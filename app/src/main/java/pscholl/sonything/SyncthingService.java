package pscholl.sonything;

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
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

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

    @Override
    public IBinder onBind(Intent i) {
      return null;
    }

    /**
     * run in seperate thread to avoid ANR. Also check if a syncthing instance
     * is already running, if so do nothing.
     */
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
      System.err.println("started");
      new Thread(bootSyncthing).start();
      return START_STICKY;
    }

    protected Runnable bootSyncthing = new Runnable() {
      @Override
      public void run() {
         try {
            File home = setupHome(getApplicationContext());
            mProcess = startSyncthing(home);

            Intent apiKeyIntent = new Intent();
            apiKeyIntent.setAction(ACTION_APIKEY);
            apiKeyIntent.putExtra(EXTRA_APIKEY, getApiKey(home));
            sendBroadcast(apiKeyIntent);
        } catch (Exception e) {
            e.printStackTrace();
        }
      }
    };

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

        pb.environment().put("STTRACE", "all");
        pb.environment().put("GOMAXPROCS", "1");

        System.err.println("executing " + pb.command().toString());
        return pb.start();
    }

    /**
     * loop-mount an ext2 filesystem (stored as a file on the sd-card) to an
     * app-accesible directory. This give syncthing enouhg storage space to work,
     * and allows for filenames that are not limited by the vfat filesystem.
     */
    protected File setupHome(Context c) throws Exception {
        //
        // TODO install libsyncthing.so on loop-mounted home. There is not
        // enough space on the internal memory for the android installation
        // method (needs double the amount), so put the executable into the
        // res/ folder and unpack to loop-mounted...
        //

        // TODO make sure everything is owned by the current user
        //

        //
        // get package dir
        //
        PackageManager m = c.getPackageManager();
        String s = c.getPackageName();
        PackageInfo pi = m.getPackageInfo(s, 0);
        s = pi.applicationInfo.dataDir;

        //
        // Do create a home. Only works if there is an /sdcard mount and there
        // is a sthing.ext2 file that contains an ext2 filesystem. Loop-mount
        // this as an ext2 filesystem with the root-hack from libtweak.so. Then
        // unpack the default config.
        //
        // TODO add mkfs.ext2 to the tools and create file if it's not there.
        //
        File android = new File("/android/"),
             ext2 = new File("/mnt/sdcard/STHING.EX2"),
             home = new File(new File(s).getParentFile().getParentFile(), "sthing");

        if (!isSdPresent())
            throw new Exception("external storage not available");

        if (!ext2.exists())
            throw new Exception(ext2.getCanonicalPath() + " not found.");

        System.err.println("setting up home " + home.toString());

        home.mkdir();
        Shell.exec("mount -o loop -t ext2 " +
            new File(android, ext2.getAbsolutePath()).getAbsolutePath() + " " +
            new File(android, home.getAbsolutePath()).getAbsolutePath());
        Shell.exec("chmod 777 " +
            new File(android, home.getAbsolutePath()).getAbsolutePath());

        System.err.println("setup done");

        //
        // if we get there, everything is setup so copy over the default config
        //
        InputStream input = c.getAssets().open("config");
        File outPath = new File(home, "config.xml");
        //Runtime.getRuntime().exec("chmod 777 " + outPath);

        if (!outPath.exists()) {
            OutputStream output = new FileOutputStream(outPath);

            byte[] buffer = new byte[1024];
            int length;
            while ((length = input.read(buffer)) > 0)
                output.write(buffer, 0, length);

            output.close();
            input.close();
        }
        System.err.println("copy done");

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
