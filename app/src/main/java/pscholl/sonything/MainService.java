package pscholl.sonything;

import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.WifiManager;
import android.os.FileObserver;
import android.os.IBinder;

//import android.support.v4.app.NotificationCompat;
//import android.app.Notification;

/** This is the job control service for synthing. It just makes sure that
 * syncthing is kept running.
 */
public class MainService extends Service {

    private static final int FOREGROUNDID = 0x007;
    private SyncThingProcess mProcess = null;

    /**
     */
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        /*
         */
        if (mProcess != null)
            return START_STICKY;

        try {
            mProcess = new SyncThingProcess(this);

            /*
             * start the service with a notification, to prevent Android from killing it
             */
            //Intent start = new Intent(this, MainActivity.class);
            //PendingIntent i = PendingIntent.getActivity(this, 1, start, PendingIntent.FLAG_UPDATE_CURRENT);

            //Notification n = new NotificationCompat.Builder(this)
            //        .setSmallIcon(R.drawable.ic_launcher_background)
            //        .setContentTitle(getString(R.string.notification_title))
            //        .setContentText(String.format(getString(R.string.notification_text), "", ""))
            //        .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            //        .setContentIntent(i)
            //        .build();

            //startForeground(FOREGROUNDID, n);
        } catch (Exception e) {
            e.printStackTrace();
        }

        return START_NOT_STICKY;
    }

    @Override
    public void onDestroy() {
        try {
            if (mProcess != null) {
                stopForeground(true);
                mProcess.terminate();
            }

            mProcess = null;
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}

