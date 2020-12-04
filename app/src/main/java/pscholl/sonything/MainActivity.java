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

public class MainActivity extends Activity {

    protected boolean mUpdateUI = false;
    protected RequestQueue mQueue = null;
    protected Map<String, String> mHeaders = null;
    protected Map<String, String> mParams = null;

    /**
     * delay between request for status updates to syncthing. Do not make much
     * faster, seem syncthing cannot handle that.
     */
    protected final long UPDATEDELAY = 10000;

    /**
     * timeout after a restart due to error
     */
    protected final long RESTARTDELAY = 20000;

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
    }

    @Override
    protected void onResume() {
        mUpdateUI = true;

        //
        // this (re-)starts syncthing
        //
        startService(new Intent(this, SyncthingService.class));

        super.onResume();
    }

    @Override
    protected void onPause() {
        mUpdateUI = false;
        super.onPause();
    }

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
     * handles single syncthingEvents comming in from the REST api
     */
    protected void onSyncThingEvent(JSONObject obj) {
        System.err.println(obj.toString());
    }

    /**
     * get a status update through the REST api and update the UI.
     *
     * see https://docs.syncthing.net/rest/events-get.html
     *
     * TODO remove UPDATEDELAY and schedule via the blocking of the rest API,
     *      i.e. just keep on requesting, but add since parameter to request
     *
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
                    scheduleRequest(createEventRequest(), UPDATEDELAY);
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

                // will be activated through onApiKey
                //if (mUpdateUI)
                //    scheduleRequest(onSyncthingEvents, RESTARTDELAY);

                //
                // reset the events we want to get
                //
                mParams.put( "since", Integer.toString(0) );

                //
                // restart syncthing on error, will call here again via
                // the onApiKey() callback and schedule the next request
                //
                handler.postDelayed(new Runnable() {
                    @Override
                    public void run() { startService(new Intent(MainActivity.this, SyncthingService.class));  }
                }, RESTARTDELAY);
            }
        }) {
        public Map<String, String> getHeaders() { return mHeaders; }
    };
    }
}
