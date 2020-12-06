package pscholl.sonything;

import java.nio.file.Files;

import com.github.ma1co.openmemories.tweak.*;
import android.content.Context;
import android.util.Log;
import android.content.pm.PackageManager;
import android.content.pm.PackageInfo;
import android.content.Intent;
import android.app.Service;
import android.os.IBinder;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.FileInputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.io.InputStreamReader;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.zip.GZIPInputStream;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;
import org.xmlpull.v1.XmlPullParserFactory;

import android.os.Environment;

/** Job Handler for syncthing.
 *
 * Setups a working directory for syncthing on the the sd-card and start sycnthing.
 * The setup requires a loop-mount on the sd-card, hence a root hack is
 * required (for sony cams this is done with libtweak.so).
 */
public class SyncthingService extends Service {

    public final static String ACTION_APIKEY = "action_apikey";
    public final static String EXTRA_APIKEY = "extra_apikey";
    protected Process mProcess = null;
    protected Thread mThread = null;
    protected boolean mThreadRunning = true;

    @Override
    public IBinder onBind(Intent i) {
      return null;
    }

    public void onCreate() {
      mThread = new Thread(runSyncthing);
    }

    public void onDestroy() {
      mThreadRunning = false;
      try {
          mThread.interrupt();
          mThread.destroy();
      }
      catch( Exception e ) { e.printStackTrace(); }
    }

    /**
     * run in seperate thread to avoid ANR. Also check if a syncthing instance
     * is already running, if so do nothing.
     */
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
      try { mThread.start(); } // ignore if running already
      catch(Exception e) { e.printStackTrace(); }
      return START_STICKY;
    }

    protected Runnable runSyncthing = new Runnable() {
      @Override
      public void run() {
         try {
            //
            // restart syncthing as long as there are socket timeouts to
            // detect when syncthing is hanging.
            //
           while( mThreadRunning ) {
              File home = setupHome(getApplicationContext());
              mProcess = startSyncthing(home);

              Intent apiKeyIntent = new Intent();
              apiKeyIntent.setAction(ACTION_APIKEY);
              apiKeyIntent.putExtra(EXTRA_APIKEY, getApiKey(home));
              sendBroadcast(apiKeyIntent);

              waitForSocketTimeout(10000);
           }
        } catch (Exception e) {
            e.printStackTrace();
        }
      }
    };

    protected void waitForSocketTimeout(int timeoutms) throws Exception{
        while (true) try {
            Thread.sleep(1000);

            Socket s = new Socket();
            s.connect(new java.net.InetSocketAddress(
                      java.net.InetAddress.getByName("localhost"), 8384),
                      timeoutms);
            s.close();
        } catch(java.net.SocketTimeoutException e) {
            e.printStackTrace();
            return;
        } catch(java.net.ConnectException e) {
            // e.g. connection refused during startup
            e.printStackTrace();
        }
    }

    protected Process startSyncthing(File home) throws Exception {
        //
        // execute libsyncthing.so in the home-directory
        //
        System.err.println("executing ");
        ProcessBuilder pb = new ProcessBuilder(
            new File(home, "libsyncthing.so").toString(),
            "-no-browser",
            "-verbose",
            "-logfile", "default",
            "-home", home.toString());

        //pb.environment().put("STTRACE", "all");
        //pb.environment().put("GOMAXPROCS", "1");

        System.err.println("executing " + pb.command().toString());
        return pb.start();
    }

    /**
     * loop-mount an ext2 filesystem (stored as a file on the sd-card) to an
     * app-accesible directory. This give syncthing enough storage space to
     * work, and allows for filenames that are not limited by the (buggy) vfat
     * filesystem implemetation.
     */
    protected File setupHome(Context c) throws Exception {
        //
        // get package dir
        //
        PackageManager m = c.getPackageManager();
        String s = c.getPackageName();
        PackageInfo pi = m.getPackageInfo(s, 0);
        s = pi.applicationInfo.dataDir;

        //
        // Do create a home. Only works if there is an /sdcard mount, unpack the
        // sthing.ext2 if not yet on sdcard. Loop-mount this as an ext2
        // filesystem with the root-hack from libtweak.so. sthing.ext2 contains
        // a default config and the syncthing binary.
        //
        File droid = new File("/android/"),
             ext2 = new File("/mnt/sdcard/STHING.EX2"),
             home = new File(new File(s).getParentFile().getParentFile(), "sthing");

        if (!isSdPresent())
            throw new Exception("external storage not available");

        //
        // deflate from sthing.ext2.z asset if available, if not log error
        // TODO should give some user feedback
        //
        if (!ext2.exists()) {
            System.err.println("deflating ext2 " + ext2.toString());

            try {
                GZIPInputStream input = new GZIPInputStream(c.getAssets().open("sthing.ext2.z"), 1024);
                FileOutputStream output = new FileOutputStream(ext2);

                byte[] buffer = new byte[1024];
                int length;

                while ((length = input.read(buffer)) > 0)
                    output.write(buffer, 0, length);

                output.close();
                input.close();
            } catch (Exception e) {
              e.printStackTrace();
              System.err.println("unable to deflate ext2 image: is it missing?");
            }
        }

        //
        // setup the home dir:
        //  1. create dir (fails silently)
        //  1. check filesystem (will fail if already mounted)
        //  1. and mount fs (will fail if alread mounted).
        //  1. chmod the directory
        //  1. chown everything for current user
        //  1. done (finally)
        //
        System.err.println("setting up home " + home.toString());

        home.mkdir();
        int pid = android.os.Process.myPid();
        Shell.exec("e2fsck -p " +
            new File(droid, ext2.getAbsolutePath()).getAbsolutePath());
        Shell.exec("mount -o loop -t ext2 " +
            new File(droid, ext2.getAbsolutePath()).getAbsolutePath() + " " +
            new File(droid, home.getAbsolutePath()).getAbsolutePath());
        Shell.exec("chmod 777 " +
            new File(droid, home.getAbsolutePath()).getAbsolutePath());
        Shell.exec("chown -R $(ls -ln /proc/" + pid + "/cmdline | egrep -o [0-9][0-9]+) " +
            new File(droid, home.getAbsolutePath()).getAbsolutePath());

        System.err.println("setup done");

        return home;
    }

    protected static boolean isSdPresent() {
      return android.os.Environment
        .getExternalStorageState()
        .equals(android.os.Environment.MEDIA_MOUNTED);
    }

    /**
     * read the API key from config.xml
     */
    protected String getApiKey(File home) {
        XmlPullParserFactory parserFactory;
        try {
            parserFactory = XmlPullParserFactory.newInstance();
            XmlPullParser parser = parserFactory.newPullParser();
            //InputStream is = getApplicationContext().getAssets().open("config");

            File config = new File(home, "config.xml");
            InputStream is = new FileInputStream(config);

            parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false);
            parser.setInput(is, null);

            return processXMLforAPIkey(parser);

        } catch (Exception e) {
            e.printStackTrace();
        }

        return null;
    }

    private String processXMLforAPIkey(XmlPullParser parser)
            throws IOException, XmlPullParserException {
        int eventType = parser.getEventType();

        while (eventType != XmlPullParser.END_DOCUMENT) {
            String eltName = null;

            if (eventType == XmlPullParser.START_TAG &&
                "apikey".equals(parser.getName()))
                return parser.nextText();

            eventType = parser.next();
        }

        return null;
    }
 
}
