package com.plugdio.mqttsysconsole;

import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.drawable.ColorDrawable;
import android.os.AsyncTask;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.provider.Settings;
import android.support.design.widget.TabLayout;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.view.ViewPager;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import org.eclipse.paho.android.service.MqttAndroidClient;
import org.eclipse.paho.client.mqttv3.IMqttActionListener;
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.IMqttToken;
import org.eclipse.paho.client.mqttv3.MqttCallback;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;

import java.util.Iterator;
import java.util.Properties;
import java.util.Set;

public class MainActivity extends AppCompatActivity {


    public static int LOGLEVEL = 1;
    public static boolean ERROR = LOGLEVEL > 0;
    public static boolean WARN = LOGLEVEL > 1;
    public static boolean INFO = LOGLEVEL > 2;
    public static boolean DEBUG = LOGLEVEL > 3;
    public static boolean VERBOSE = LOGLEVEL > 4;

    private int backButtonCount = 0;

    private String LOG_TAG = "Main_Activity";
    private SmartFragmentStatePagerAdapter adapterViewPager;

    private MqttAndroidClient client;

    private String mqttHost;
    private String mqttPort;
    private Boolean mqqtAuthEnabled;
    private String mqttUser;
    private String mqttPass;

    private static Properties mySys = new Properties();
    private ProgressDialog progress;

    // constants used to define App and MQTT connection status
    private enum AppStatus {
        INITIAL,                            // initial status
        NO_CONFIG,                         // missing configuration
        CONNECTING,                         // attempting to connect
        CONNECTED,                          // connected
        NOTCONNECTED_UNKNOWNREASON          // failed to connect for some reason
    }

    private AppStatus status = AppStatus.INITIAL;
    /**
     * The {@link ViewPager} that will host the section contents.
     */
    private static ViewPager mViewPager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        final TextView mqttStatusTextView = (TextView) findViewById(R.id.mqtt_status);
        mqttStatusTextView.setText("Not connected");

//        progress = ProgressDialog.show(this, "MQTT $SYS Console", "Connecting");
        progress = new ProgressDialog(this);
        progress.show();

        progress.setCancelable(false);
        progress.getWindow().setBackgroundDrawable(new ColorDrawable(android.graphics.Color.TRANSPARENT));
        progress.setContentView(R.layout.progressdialog);

        SharedPreferences sharedPrefs =
                PreferenceManager.getDefaultSharedPreferences(this);
        mqttHost = sharedPrefs.getString("mqtt_broker_address", "");
        mqttPort = sharedPrefs.getString("mqtt_broker_port", "");
        mqqtAuthEnabled = sharedPrefs.getBoolean("mqtt_authentication_switch", false);
        mqttUser = sharedPrefs.getString("mqtt_username", "");
        mqttPass = sharedPrefs.getString("mqtt_password", "");

        if (
                mqttHost.equals("") ||
                        (mqqtAuthEnabled && mqttUser.equals("")) ||
                        (mqqtAuthEnabled && mqttPass.equals(""))
                ) {
            if (DEBUG) Log.d(LOG_TAG, "MQTT configuration is missing");
            mqttStatusTextView.setText("MQTT configuration is missing");
            status = AppStatus.NO_CONFIG;
            progress.dismiss();
            return;
        }

        String clientId = Settings.Secure.getString(getContentResolver(),
                Settings.Secure.ANDROID_ID);

        if (DEBUG) Log.d(LOG_TAG, "clientId: " + clientId);

        if (clientId.length() > 23) {
            clientId = clientId.substring(0, 23);
        }

        client = new MqttAndroidClient(this, "tcp://" + mqttHost + ":" + mqttPort, clientId);

        MqttConnectOptions options = new MqttConnectOptions();
        options.setMqttVersion(MqttConnectOptions.MQTT_VERSION_3_1);
        if (mqqtAuthEnabled) {
            options.setUserName(mqttUser);
            options.setPassword(mqttPass.toCharArray());
        }

        try {
            IMqttToken token = client.connect(options);

            token.setActionCallback(new IMqttActionListener() {
                @Override
                public void onSuccess(IMqttToken asyncActionToken) {
                    if (DEBUG) Log.d(LOG_TAG, "MQTT connection successful, let's subscribe");

                    status = AppStatus.CONNECTED;

                    String topic = "$SYS/#";
                    int qos = 1;
                    try {
                        IMqttToken subToken = client.subscribe(topic, qos);
                        subToken.setActionCallback(new IMqttActionListener() {
                            @Override
                            public void onSuccess(IMqttToken asyncActionToken) {
                                if (DEBUG) Log.d(LOG_TAG, "MQTT subscribe successful");
                                mqttStatusTextView.setText("Connected");
                                status = AppStatus.CONNECTED;
                            }

                            @Override
                            public void onFailure(IMqttToken asyncActionToken,
                                                  Throwable exception) {
                                if (ERROR)
                                    Log.e(LOG_TAG, "MQTT subscribe failed: " + exception.getMessage());
                                mqttStatusTextView.setText("Couldn't subscripbe to the $SYS topic");
                                status = AppStatus.NOTCONNECTED_UNKNOWNREASON;
                            }
                        });
                    } catch (MqttException e) {
                        if (ERROR) Log.e(LOG_TAG, "MqttException #2: " + e.getMessage());
                        mqttStatusTextView.setText("Connection failed");
                    } catch (Exception e) {
                        if (ERROR) Log.e(LOG_TAG, "Exception #2: " + e.getMessage());
                        mqttStatusTextView.setText("Connection failed");
                        return;
                    }
                }

                @Override
                public void onFailure(IMqttToken asyncActionToken, Throwable exception) {
                    if (ERROR) Log.e(LOG_TAG, "MQTT connection failed: " + exception.getMessage());
                    mqttStatusTextView.setText("Connection failed");
                    status = AppStatus.NOTCONNECTED_UNKNOWNREASON;

                }
            });

        } catch (MqttException e) {
            if (ERROR) Log.e(LOG_TAG, "MqttException #1: " + e.getMessage());
            mqttStatusTextView.setText("Connection failed");
        } catch (Exception e) {
            if (ERROR) Log.e(LOG_TAG, "Exception #1: " + e.getMessage());
            mqttStatusTextView.setText("Connection failed");
            return;
        }
        client.setCallback(new MqttCallback() {
            @Override
            public void connectionLost(Throwable cause) {
                if (ERROR) Log.e(LOG_TAG, "connectionLost: " + cause);
            }

            @Override
            public void messageArrived(String topic, MqttMessage message) throws Exception {
                if (VERBOSE)
                    Log.v(LOG_TAG, "messageArrived: " + topic + " / " + message.toString());

                String tvTopic = topic.replaceAll("/", "_");

                mySys.setProperty(tvTopic, message.toString());

//                TextView tv = (TextView) findViewById(getResources().getIdentifier(tvTopic, "id", getPackageName()));
                View currentView = adapterViewPager.getRegisteredFragment(mViewPager.getCurrentItem()).getView();
                TextView tv = (TextView) currentView.findViewById(getResources().getIdentifier(tvTopic, "id", currentView.getContext().getPackageName()));
                if (tv != null) {
                    if (VERBOSE) Log.v(LOG_TAG, "tv#1 is not null: " + tvTopic);
                } else {
                    if (VERBOSE) Log.v(LOG_TAG, "tv#1 is null: " + tvTopic);
                }


                tv.setText(message.toString());

//                adapterViewPager.notifyDataSetChanged();

            }

            @Override
            public void deliveryComplete(IMqttDeliveryToken token) {
                if (VERBOSE) Log.v(LOG_TAG, "deliveryComplete");
            }
        });

        new LongOperation().execute("start");
    }

    @Override
    protected void onStart() {
        super.onStart();

        SharedPreferences sharedPrefs =
                PreferenceManager.getDefaultSharedPreferences(this);

        if (
                !sharedPrefs.getString("mqtt_broker_address", "").equals(mqttHost) ||
                        !sharedPrefs.getString("mqtt_broker_port", "").equals(mqttPort) ||
                        sharedPrefs.getBoolean("mqtt_authentication_switch", false) != mqqtAuthEnabled ||
                        !sharedPrefs.getString("mqtt_username", "").equals(mqttUser) ||
                        !sharedPrefs.getString("mqtt_password", "").equals(mqttPass)
                ) {
            if (DEBUG) Log.d(LOG_TAG, "MQTT configuration has changed. Recreate");
            recreate();
        }


    }

    @Override
    public void onBackPressed() {

        if (backButtonCount >= 1) {
            Intent intent = new Intent(Intent.ACTION_MAIN);
            intent.addCategory(Intent.CATEGORY_HOME);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
        } else {
            Toast.makeText(this, "Press the back button once again to close the application.", Toast.LENGTH_SHORT).show();
            backButtonCount++;
        }
    }

    @Override
    public void onDestroy() {
        if (DEBUG) Log.d(LOG_TAG, "ondestroy");

        if (client != null) {
            try {
                IMqttToken disconToken = client.disconnect();
                disconToken.setActionCallback(new IMqttActionListener() {
                    @Override
                    public void onSuccess(IMqttToken asyncActionToken) {
                        if (DEBUG) Log.d(LOG_TAG, "MQTT disconnect successful");

                    }

                    @Override
                    public void onFailure(IMqttToken asyncActionToken,
                                          Throwable exception) {
                        if (ERROR)
                            Log.e(LOG_TAG, "MQTT disconnect failed: " + exception.getMessage());
                    }
                });
            } catch (MqttException e) {
                if (ERROR) Log.e(LOG_TAG, "MqttException: " + e.getMessage());
            } catch (NullPointerException e) {
                if (ERROR) Log.e(LOG_TAG, "Looks like we were not connected: " + e.getMessage());
            }
        }
        super.onDestroy();

    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            startActivity(new Intent(this, SettingsActivity.class));
            return true;
        } else if (id == R.id.action_about) {
            startActivity(new Intent(this, AboutActivity.class));
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    private class LongOperation extends AsyncTask<String, Void, String> {

        private String LOG_TAG = "Main_LongOperation";

        protected String doInBackground(String... params) {

            if (status == AppStatus.NO_CONFIG || status == AppStatus.NOTCONNECTED_UNKNOWNREASON) {
                return null;
            }

            int i = 0;
            while ((i < 10) && status == AppStatus.INITIAL) {
                i++;
                if (DEBUG) Log.d(LOG_TAG, i + ". ready?: " + status);
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    if (ERROR) Log.e(LOG_TAG, "InterruptedException: " + e.getMessage());
                }
            }

            if (status != AppStatus.CONNECTED) {
                return null;
            }

            i = 0;
            while ((i < 10) && (mySys.size() < 10)) {
                i++;
                if (DEBUG) Log.d(LOG_TAG, i + ". mySys size: " + mySys.size());
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    if (ERROR) Log.e(LOG_TAG, "InterruptedException: " + e.getMessage());
                }
            }
            return null;
        }

        protected void onProgressUpdate(Integer... progress) {
        }

        @Override
        protected void onPostExecute(String result) {
            if (DEBUG) Log.d(LOG_TAG, "onPostExecute: " + mySys.size());
            progress.dismiss();

            ImageView alertImage = (ImageView) findViewById(R.id.alert);

            if (status == AppStatus.CONNECTED) {
                alertImage.setVisibility(View.INVISIBLE);

                adapterViewPager = new MyPagerAdapter(getSupportFragmentManager());

                mViewPager = (ViewPager) findViewById(R.id.container);
                mViewPager.setAdapter(adapterViewPager);
                mViewPager.setOffscreenPageLimit(1);

                TabLayout tabLayout = (TabLayout) findViewById(R.id.tabs);
                tabLayout.setupWithViewPager(mViewPager);
            } else {
                alertImage.setVisibility(View.VISIBLE);
            }
        }
    }

    /**
     * *********************************************************************************************************
     */
    public static class PlaceholderFragment extends Fragment {
        /**
         * The fragment argument representing the section number for this
         * fragment.
         */
        private static final String ARG_SECTION_NUMBER = "section_number";
        private String LOG_TAG = "Main_PHFragement";

        public PlaceholderFragment() {
        }

        /**
         * Returns a new instance of this fragment for the given section
         * number.
         */
        public static PlaceholderFragment newInstance(int sectionNumber) {

            PlaceholderFragment fragment = new PlaceholderFragment();
            Bundle args = new Bundle();
            args.putInt(ARG_SECTION_NUMBER, sectionNumber);
            fragment.setArguments(args);
            return fragment;
        }

        @Override
        public View onCreateView(LayoutInflater inflater, ViewGroup container,
                                 Bundle savedInstanceState) {
            View rootView = null; // = inflater.inflate(R.layout.fragment_highlights, container, false);
//            TextView textView = (TextView) rootView.findViewById(R.id.section_label);
//            textView.setText(getString(R.string.section_format, getArguments().getInt(ARG_SECTION_NUMBER)));

            if (DEBUG) Log.d(LOG_TAG, "Tab selected: " + getArguments().getInt(ARG_SECTION_NUMBER));

            switch (getArguments().getInt(ARG_SECTION_NUMBER)) {
                case 1:
                    if (DEBUG) Log.d(LOG_TAG, "Highlights");
                    rootView = inflater.inflate(R.layout.fragment_highlights, container, false);

//                    TextView tv2 = (TextView) rootView.findViewById(R.id.$SYS_broker_uptime);
//                    tv2.setText(mySys.getProperty("SYS_broker_uptime"));

                    break;
                case 2:
                    if (DEBUG) Log.d(LOG_TAG, "System");
                    rootView = inflater.inflate(R.layout.fragment_system, container, false);
                    break;
                case 3:
                    if (DEBUG) Log.d(LOG_TAG, "Clients");
                    rootView = inflater.inflate(R.layout.fragment_clients, container, false);
                    break;
                case 4:
                    if (DEBUG) Log.d(LOG_TAG, "Messages & Data");
                    rootView = inflater.inflate(R.layout.fragment_messages, container, false);
                    break;
                case 5:
                    if (DEBUG) Log.d(LOG_TAG, "Stats");
                    rootView = inflater.inflate(R.layout.fragment_stats, container, false);
                    break;
            }

            Set props = mySys.keySet();   // get set-view of keys
            Iterator itr = props.iterator();

            TextView tv;

            while (itr.hasNext()) {

                String key = (String) itr.next();
                tv = (TextView) rootView.findViewById(getResources().getIdentifier(key, "id", rootView.getContext().getPackageName()));
                if (tv != null) {
                    if (DEBUG)
                        Log.d(LOG_TAG, "tv#2 is not null: " + key + " value: " + mySys.getProperty(key));
                    tv.setText(mySys.getProperty(key));
                } else {
                    if (DEBUG) Log.d(LOG_TAG, "tv#2 is null: " + key);
                }

            }

            return rootView;
        }

        @Override
        public void onActivityCreated(Bundle savedInstanceState) {
            super.onActivityCreated(savedInstanceState);
            if (DEBUG)
                Log.d(LOG_TAG, "fragment onActivityCreated - " + getArguments().getInt(ARG_SECTION_NUMBER));
        }

        @Override
        public void onStart() {
            super.onStart();
            if (DEBUG)
                Log.d(LOG_TAG, "fragment onStart - " + getArguments().getInt(ARG_SECTION_NUMBER));
        }

        @Override
        public void onAttach(Context context) {
            super.onAttach(context);
            if (DEBUG)
                Log.d(LOG_TAG, "fragment onAttach - " + getArguments().getInt(ARG_SECTION_NUMBER));
/*
            if (context instanceof OnItemSelectedListener) {
                listener = (OnItemSelectedListener) context;
            } else {
                throw new ClassCastException(context.toString()
                        + " must implemenet MyListFragment.OnItemSelectedListener");
            }
*/
        }

        @Override
        public void onDetach() {
            super.onDetach();
            if (DEBUG) Log.d(LOG_TAG, "fragment onDetach");
        }

        @Override
        public void onResume() {
            super.onResume();
            if (DEBUG)
                Log.d(LOG_TAG, "fragment onResume - " + getArguments().getInt(ARG_SECTION_NUMBER));
        }


        @Override
        public void setUserVisibleHint(boolean isVisibleToUser) {
            super.setUserVisibleHint(isVisibleToUser);
            if (isVisibleToUser) {
                if (DEBUG)
                    Log.d(LOG_TAG, "showing fragment: " + getArguments().getInt(ARG_SECTION_NUMBER));
//                FragmentTransaction ft = getFragmentManager().beginTransaction();
//                ft.detach(this).attach(this).commit();
            } else {
                // fragment is no longer visible
            }
        }

    }



    // idea from: https://guides.codepath.com/android/ViewPager-with-FragmentPagerAdapter
    // Extend from SmartFragmentStatePagerAdapter now instead for more dynamic ViewPager items
    public static class MyPagerAdapter extends SmartFragmentStatePagerAdapter {
        private static int NUM_ITEMS = 5;
        private String LOG_TAG = "Main_MyPagerAdapter";

        public MyPagerAdapter(FragmentManager fragmentManager) {
            super(fragmentManager);
        }

        // Returns total number of pages
        @Override
        public int getCount() {
            return NUM_ITEMS;
        }

        // Returns the fragment to display for that page
        @Override
        public Fragment getItem(int position) {
            return PlaceholderFragment.newInstance(position + 1);
        }

        // Returns the page title for the top indicator
        @Override
        public CharSequence getPageTitle(int position) {

            if (VERBOSE) Log.v(LOG_TAG, "getPageTitle: " + position);

            switch (position) {
                case 0:
                    return "Highlights";
                case 1:
                    return "System";
                case 2:
                    return "Clients";
                case 3:
                    return "Messages & Data";
                case 4:
                    return "Stats";
            }
            return null;
        }

        @Override
        public int getItemPosition(Object object) {
            if (DEBUG) Log.d(LOG_TAG, "reload");
            return POSITION_NONE;
        }

    }

}
