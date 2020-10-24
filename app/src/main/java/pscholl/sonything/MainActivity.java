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

public class MainActivity extends Activity {

    protected RequestQueue mQueue = null;
    protected Map<String, String> mHeaders = null;

    @Override
    protected void onDestroy() {
        unregisterReceiver(onApiKey);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);


        //
        // make sure that we receive updates from the syncthing service
        //
        IntentFilter filter = new IntentFilter();
        filter.addAction(SyncthingService.ACTION_APIKEY);
        registerReceiver(onApiKey, filter);

        //
        // this (re-)starts syncthing
        //
        startService(new Intent(this, SyncthingService.class));


        mQueue = Volley.newRequestQueue(this);

        //
        // getting the API key is buggy, the only surefire way would
        // be to wait until mSyncthing has setup the home, and then
        // read the config, i.e. find the api key
        //
        //mHeaders = new HashMap<String, String>();
        //mHeaders.put( "X-API-KEY", Syncthing.getApiKey(this) );

        ////
        //// start syncthing if not running
        ////
        //JsonObjectRequest ping = new JsonObjectRequest(
        //    Request.Method.GET,
        //    "http://localhost:8384/rest/system/ping",
        //    null,
        //    new Response.Listener<JSONObject>() {
        //        @Override
        //        public void onResponse(JSONObject obj) {}
        //    },
        //    new Response.ErrorListener() {
        //        @Override
        //        public void onErrorResponse(VolleyError err) {
        //            mSyncthing.start();
        //            System.err.println(err.toString());
        //        }
        //    }) {
        //    public Map<String, String> getHeaders() { return mHeaders; }
        //};

        //mQueue.add(ping);
    }

    @Override
    protected void onResume() {
        super.onResume();

        // TODO connect to REST api and display some information
        // with volley https://developer.android.com/training/volley
    }

    @Override
    protected void onPause() {
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
     * receives the apikey from the syncthing service. Once this is there we
     * know that syncthing is running and we can get updates from the REST api.
     */
    protected BroadcastReceiver onApiKey = new BroadcastReceiver() {
        @Override
        public void onReceive(Context c, Intent i) {
            System.err.println("rx'ed an intent");
        }
    };
}
