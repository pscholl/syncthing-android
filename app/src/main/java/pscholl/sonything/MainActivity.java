package pscholl.sonything;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.os.Handler;

import java.util.Map;
import java.util.HashMap;
import java.util.LinkedHashMap;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import com.android.volley.toolbox.Volley;
import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.JsonArrayRequest;

import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.text.format.Formatter;

/**
 * TODO enable/disable button
 *
 * TODO integrate fs watcher and hook with start and with:
 *   https://docs.syncthing.net/rest/db-scan-post.html
 *
 * TODO show battery stats
 *  https://stackoverflow.com/questions/3291655/get-battery-level-and-state-in-android
 *
 * TODO replace json parser with something that does not use simpledateformat
 *
 */

public class MainActivity extends Activity {

    protected boolean mUpdateUI = false;
    protected RequestQueue mQueue = null;
    protected Map<String, String> mHeaders = null;
    protected Map<String, String> mParams = null;

    protected ConnectivityManager mConnectivityManager;
    protected WifiManager mWifiManager;

    @Override
    protected void onDestroy() {
        unregisterReceiver(onApiKey);
        super.onDestroy();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        //
        // make sure that we receive updates from the syncthing service
        //
        IntentFilter filter = new IntentFilter();
        filter.addAction(SyncthingService.ACTION_APIKEY);
        registerReceiver(onApiKey, filter);

        //
        // set-up the UI
        //
        setContentView(R.layout.activity_main);

        //
        // prep the volley queue
        //
        mQueue = Volley.newRequestQueue(this);

        //
        // setup wifi and event receiver
        //
        mConnectivityManager = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
        mWifiManager = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);

    }

    @Override
    protected void onResume() {
        mUpdateUI = true;

        //
        // this (re-)starts syncthing
        //
        startService(new Intent(this, SyncthingService.class));

        //
        // enable wifi and register receiver
        //
        mWifiManager.setWifiEnabled(true);

        IntentFilter f = new IntentFilter();
        f.addAction(WifiManager.WIFI_STATE_CHANGED_ACTION);
        f.addAction(WifiManager.SUPPLICANT_STATE_CHANGED_ACTION);
        f.addAction(WifiManager.NETWORK_STATE_CHANGED_ACTION);
        registerReceiver(mWifiReceiver, f);

        super.onResume();
    }

    @Override
    protected void onPause() {
        super.onPause();

        mUpdateUI = false;
        unregisterReceiver(mWifiReceiver);
    }

    protected BroadcastReceiver mWifiReceiver = new BroadcastReceiver() {
        /**
         * update the wifi status accordingly
         */
        @Override
        public void onReceive(Context context, Intent intent) {
            TextView tv = findViewById(R.id.wifi);

            switch (mWifiManager.getWifiState()) {
            case WifiManager.WIFI_STATE_ENABLED:
                NetworkInfo networkInfo = mConnectivityManager.getNetworkInfo(ConnectivityManager.TYPE_WIFI);
                WifiInfo wifiInfo = mWifiManager.getConnectionInfo();

                if (networkInfo.isConnected()) {
                    tv.setText(getString(
                          R.string.wifi_connected,
                          wifiInfo.getSSID(),
                          Formatter.formatIpAddress(wifiInfo.getIpAddress())));
                } else {
                    NetworkInfo.DetailedState state = WifiInfo.getDetailedStateOf(wifiInfo.getSupplicantState());
                    switch (state) {
                    case SCANNING:
                        tv.setText(R.string.wifi_scanning);
                    case AUTHENTICATING:
                    case CONNECTING:
                    case OBTAINING_IPADDR:
                        tv.setText(R.string.wifi_connecting);
                    default:
                        tv.setText(R.string.wifi_enabled);
                    }
                }
                break;
            case WifiManager.WIFI_STATE_ENABLING:
                tv.setText(R.string.wifi_enabling);
                break;
            default:
                tv.setText(R.string.wifi_disabled);
                break;
            }
        }
    };

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        /**
         * go back to the main screen
         */
        if (keyCode == KeyEvent.KEYCODE_MENU) {
            finish();
            startActivity(new Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_HOME)
                .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            return true;
        }

        return super.onKeyDown(keyCode, event);
    }

    /**
     * handles single syncthingEvents comming in from the REST api
     */
    protected void onSyncThingEvent(JSONObject obj) {
        try {
          String type = obj.getString("type");
          JSONObject data = obj.getJSONObject("data");
          System.err.println("got update " + type);

          switch(type) {
          case "FolderSummary":
            onFolderSummary( data );
            break;

          case "FolderScanProgress":
            onFolderScanProgress(data);
            break;

          case "RemoteDownloadProgress":
            onRemoteDownloadProgress(data);
            break;

          case "FolderCompletion":
            onFolderCompletion(data);
            break;

          default:
            System.err.println(obj.toString());
            break;
          }
        } catch(Exception e) {
          e.printStackTrace();
        }
    }


    /** 
     * {"folder":"avwck-toq6i",
     *  "needItems":1058,
     *  "globalBytes":13745786752,
     *  "globalItems":1585,
     *  "sequence":527,
     *  "device":"EVDARLQ-DHV2S6C-RSKAOFZ-V4TY42W-HCSTZSQ-ICYI5AI-EQRX35J-MNINJQY",
     *  "needBytes":9260400640,
     *  "needDeletes":0,
     *  "completion":32.63098862891483},
     */
    protected LinkedHashMap<String, LinkedHashMap<String, JSONObject>> completion 
              = new LinkedHashMap<String, LinkedHashMap<String, JSONObject>>();

    protected void onFolderCompletion(JSONObject data) throws Exception {
        //
        // did we see the folder before?
        //
        if (!completion.containsKey( data.getString("folder") )) {
            completion.put( data.getString("folder"),
                            new LinkedHashMap<String, JSONObject>() );
        }

        //
        // update the data
        //
        completion
           .get(data.getString("folder"))
           .put(data.getString("device"), data);

        //
        // update the textview
        //
        StringBuilder sb = new StringBuilder();
        for(LinkedHashMap<String, JSONObject> val : completion.values())
            for(JSONObject folder : val.values())
                sb.append(
                    getString(R.string.folder_text,
                        folder.getString("folder"),
                        folder.getInt("needItems"),
                        folder.getInt("globalItems"),
                        folder.getDouble("completion")));

        TextView tv = (TextView) findViewById(R.id.folders);
        tv.setVisibility(View.VISIBLE);
        tv.setText(sb.toString());

    }

    /**
     * {"state":{
     *    "10601118\/DSC01117.JPG":17,
     *    "10201114\/DSC00986.JPG":52},
     *    "folder":"avwck-toq6i",
     *    "device":"EVDARLQ-DHV2S6C-RSKAOFZ-V4TY42W-HCSTZSQ-ICYI5AI-EQRX35J-MNINJQY"},
     */
    protected void onRemoteDownloadProgress(JSONObject data) throws Exception {
      TextView tv = (TextView) findViewById(R.id.latest_event);
      JSONObject state = data.getJSONObject("state");

      StringBuilder sb = new StringBuilder();
      java.util.Iterator<String> x = state.keys();
      while(x.hasNext()) {
          String key = x.next();
          int val = state.getInt(key);
          sb.append(String.format( "%s - %d%%\n", key, val));
      }

      tv.setText(sb.toString());
      tv.setVisibility(View.VISIBLE);
    }

    /**
     * this looks like this:
     *  {"total":12844105729,
     *   "folder":"avwck-toq6i",
     *   "current":69664768,
     *   "rate":751814.812597032},
     */
    protected void onFolderScanProgress(JSONObject data) throws Exception {
        TextView tv = (TextView) findViewById(R.id.throughput);
        tv.setVisibility(View.VISIBLE);
        tv.setText( getString(R.string.rate_text, data.getDouble("rate")) );

        ProgressBar pb = (ProgressBar) findViewById(R.id.progressbar);
        pb.setVisibility(View.VISIBLE);
        pb.setProgress( (int) (data.getDouble("current") * 100 / data.getDouble("total")) );
    }

    /**
     * update the Ui with the FolderSummary, which looks like:
     *
     * {"summary":{"localFiles":240,
     *             "localDeleted":0,
     *             "needDirectories":0,
     *             "globalDeleted":0,
     *             "state":"scanning",
     *             "version":265,
     *             "pullErrors":0,
     *             "localTotalItems":265,
     *             "inSyncBytes":2156498048,
     *             "globalSymlinks":0,
     *             "errors":0,
     *             "invalid":"",
     *             "globalTotalItems":265,
     *             "globalBytes":2156498048,
     *             "needSymlinks":0,
     *             "stateChanged":"1970-01-01T00:58:52.174196002Z",
     *             "needBytes":0,
     *             "globalDirectories":25,
     *             "needFiles":0,
     *             "needTotalItems":0,
     *             "globalFiles":240,
     *             "ignorePatterns":false,
     *             "sequence":265,
     *             "localDirectories":25,
     *             "localSymlinks":0,
     *             "needDeletes":0,
     *             "inSyncFiles":240,
     *             "localBytes":2156498048},
     *             "folder":"avwck-toq6i"}
     */
    protected void onFolderSummary(JSONObject data) throws Exception {
        TextView tv = (TextView) findViewById(R.id.status);
        tv.setText( data.getJSONObject("summary").getString("state") );
    }

    /**
     * receives the apikey from the syncthing service. Once this is there we
     * know that syncthing is running and we can get updates from the REST api.
     */
    protected BroadcastReceiver onApiKey = new BroadcastReceiver() {
        @Override
        public void onReceive(Context c, Intent i) {
            String apikey = i.getStringExtra(SyncthingService.EXTRA_APIKEY);

            mHeaders = new HashMap<String, String>();
            mHeaders.put( "X-API-KEY", apikey );

            mParams = new HashMap<String, String>();
            mParams.put( "since", Integer.toString(0) );

            scheduleRequest(createEventRequest(), 0);
        }
    };

    /**
     * schedules a REST/Volley request with delay.
     */
    protected final Handler handler = new Handler();
    protected void scheduleRequest(final JsonArrayRequest req, long delay) {
        handler.postDelayed(new Runnable() {
            @Override
            public void run() { mQueue.add(req); }
        }, delay);
    }

    /**
     * get a status update through the REST api and update the UI.
     *
     * see https://docs.syncthing.net/rest/events-get.html
     */
    protected JsonArrayRequest createEventRequest() {
      return new JsonArrayRequest(
        Request.Method.GET,
        "http://localhost:8384/rest/events?since="+mParams.get("since"),
        null,
        new Response.Listener<JSONArray>() {
            @Override
            public void onResponse(JSONArray arr) {
                for (int i = 0; i < arr.length(); i++) try {
                  JSONObject obj = arr.getJSONObject(i);
                  onSyncThingEvent(obj);

                  //
                  // update the 'since' getParam to filter events
                  // to only those that have not been seen
                  //
                  mParams.put( "since", obj.getString( "globalID" ) );
                }
                catch (Exception e) { System.err.println(e); }

                if (mUpdateUI)
                    scheduleRequest(createEventRequest(), 0);
            }
        },
        new Response.ErrorListener() {
            @Override
            public void onErrorResponse(VolleyError err) {
                if (err instanceof com.android.volley.TimeoutError)
                  return; // ignore

                StringBuilder sb = new StringBuilder(err.toString());
                if (err.toString().length() > 160)
                  sb.delete(80, err.toString().length()-80);
                System.err.println(sb.toString());

                //
                // reset the events we want to get
                //
                mParams.put( "since", Integer.toString(0) );

                if (mUpdateUI) // re-schedule with an error delay
                    scheduleRequest(createEventRequest(), 5000);
            }
        }) {
        public Map<String, String> getHeaders() { return mHeaders; }
    };
    }
}
