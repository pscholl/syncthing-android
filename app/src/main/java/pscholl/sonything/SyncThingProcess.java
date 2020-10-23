package pscholl.sonything;

import com.github.ma1co.openmemories.tweak.*;
import android.content.Context;
import android.util.Log;
import android.content.pm.PackageManager;
import android.content.pm.PackageInfo;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
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

import android.os.Environment;

public class SyncThingProcess {

    protected Thread mSyncthingThread = null;

    public static boolean isSdPresent() {
      return android.os.Environment.getExternalStorageState().equals(android.os.Environment.MEDIA_MOUNTED);
    }

    public SyncThingProcess(final Context c) throws Exception
    {
        /**
         * get location of libsyncthing.so
         */
        PackageManager m = c.getPackageManager();
        String s = c.getPackageName();
        PackageInfo pi = m.getPackageInfo(s, 0);
        s = pi.applicationInfo.dataDir;

        /**
         * TODO install libsyncthing.so on loop-mounted home. There is not
         * enough space on the internal memory for the android installation
         * method (needs double the amount), so put the executable into the
         * res/ folder and unpack to loop-mounted...
         */

        /** TODO
         * make sure everything is owned by the current user
         */


        /**
         * Do create a home. Only works if there is an /sdcard mount and there
         * is a sthing.ext2 file that contains an ext2 filesystem. Loop-mount
         * this as an ext2 filesystem with the root-hack from libtweak.so. Then
         * unpack the default config.
         * TODO add mkfs.ext2 to the tools and create file if it's not there.
         */
        File android = new File("/android/");
        File ext2 = new File("/mnt/sdcard/STHING.EX2");
        final File home = new File(new File(s).getParentFile().getParentFile(), "sthing");
        Runtime.getRuntime().exec("chmod 777 " + home.getAbsolutePath());
        home.mkdir();

        if (!ext2.exists())
          throw new Exception(ext2.getCanonicalPath() + " not found.");

        try {
        Shell.exec("mount -o loop -t ext2 " +
          new File(android, ext2.getAbsolutePath()).getAbsolutePath() + " " +
          new File(android, home.getAbsolutePath()).getAbsolutePath());
        Shell.exec("chmod 777 " +
          new File(android, home.getAbsolutePath()).getAbsolutePath());
        } catch (Exception e) {
        }


        InputStream input = c.getAssets().open("config");
        File outPath = new File(home, "config.xml");
        Runtime.getRuntime().exec("chmod 777 " + outPath);

        if (!outPath.exists()) {
            OutputStream output = new FileOutputStream(outPath);

            byte[] buffer = new byte[1024];
            int length;
            while ((length = input.read(buffer)) > 0)
                output.write(buffer, 0, length);

            output.close();
            input.close();
        }

        if (mSyncthingThread != null)
          return;

        /**
         * just keep on restarting syncthing forever
         */
        mSyncthingThread = new Thread() { public void run() {
          while (true) {
            /**
             * execute libsyncthing.so in the home-directory
             */
            ProcessBuilder pb = new ProcessBuilder(
                new File(home, "libsyncthing.so").toString(),
                "-no-browser",
                "-no-restart",
                "-verbose",
                "-logfile", "default",
                "-home", home.toString());

            System.err.println("executing " + pb.command().toString());

            try {
              pb.start().waitFor();
            }
            catch(Exception e) {
              e.printStackTrace();
            }
          }
        }};

        mSyncthingThread.start();
    }

    public int terminate() throws InterruptedException {
        /* weird, this seems to kill the jvm */
        //p.destroy();
        //return p.waitFor();
        return 0;
    }

    protected File getExecPath(Context c, String s) {
        File dir = c.getFilesDir().getParentFile();
        return new File(new File(dir, "lib"), s);
    }
}
