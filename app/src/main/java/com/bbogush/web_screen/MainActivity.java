package com.bbogush.web_screen;

import static com.bbogush.web_screen.ConnectionService.MESSAGE_DEVICE_NAME;
import static com.bbogush.web_screen.ConnectionService.MESSAGE_READ;
import static com.bbogush.web_screen.ConnectionService.MESSAGE_STATE_CHANGE;
import static com.bbogush.web_screen.ConnectionService.MESSAGE_TOAST;
import static com.bbogush.web_screen.ConnectionService.MESSAGE_WRITE;
import static com.bbogush.web_screen.ConnectionService.STATE_CONNECTED;
import static com.bbogush.web_screen.ConnectionService.STATE_CONNECTING;
import static com.bbogush.web_screen.ConnectionService.STATE_LISTEN;
import static com.bbogush.web_screen.ConnectionService.STATE_NONE;
import static com.bbogush.web_screen.ConnectionService.TOAST;

import androidx.activity.result.ActivityResultCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContract;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.ContextCompat;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.res.ColorStateList;
import android.database.Cursor;
import android.media.projection.MediaProjectionManager;
import android.net.LinkAddress;
import android.net.Uri;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.provider.ContactsContract;
import android.provider.Settings;
import android.service.controls.Control;
import android.text.InputType;
import android.text.format.Formatter;
import android.text.method.DigitsKeyListener;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Display;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ToggleButton;

import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton;

import java.net.Inet6Address;
import java.util.List;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = MainActivity.class.getSimpleName();

    private static final int PERM_ACTION_ACCESSIBILITY_SERVICE = 100;
    private static final int PERM_MEDIA_PROJECTION_SERVICE = 101;

    private static final int HANDLER_MESSAGE_UPDATE_NETWORK = 0;

    private static final int REQUEST_ENABLE_BT = 3;

    private int httpServerPort;

    private AppService appService = null;
    private AppServiceConnection serviceConnection = null;

    private NetworkHelper networkHelper = null;
    private SettingsHelper settingsHelper = null;
    private PermissionHelper permissionHelper;
    private BluetoothAdapter bluetoothAdapter;
    private ConnectionService connectionService;

    private String contactName;
    ActivityResultLauncher<Void> pickContact = registerForActivityResult(new ActivityResultContracts.PickContact(),
            new ActivityResultCallback<Uri>() {
                @Override
                public void onActivityResult(Uri uri) {
                    // Handle the returned Uri
                    if (uri == null) {
                        return;
                    }
                    Cursor cursor = getContentResolver().query(uri, null, null, null);
                    if (cursor != null && cursor.moveToFirst()) {
                        int nameIndex = cursor.getColumnIndex(ContactsContract.Contacts.DISPLAY_NAME);
                        if (nameIndex >= 0) {
                            contactName = cursor.getString(nameIndex);
                        }
                        Log.d(TAG, "Contact Selected: " + contactName);
                    }
                    updateContactHolder();
                }
            });

    private boolean isControllee = false;

    /**
     * The Handler that gets information back from the ConnectionService
     */
    private final Handler connectionHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case MESSAGE_STATE_CHANGE:
                    switch (msg.arg1) {
                        case STATE_CONNECTED:
                            if (isControllee) {
                                WifiManager wm = getSystemService(WifiManager.class);
                                String ip = Formatter.formatIpAddress(wm.getConnectionInfo().getIpAddress());
                                connectionService.write(ip.getBytes());
                            }
                            break;
                        case STATE_CONNECTING:
                            break;
                        case STATE_LISTEN:
                        case STATE_NONE:
                            break;
                    }
                    break;
                case MESSAGE_WRITE:
                    break;
                case MESSAGE_READ:
                    byte[] readBuf = (byte[]) msg.obj;
                    // construct a string from the valid bytes in the buffer
                    String readMessage = new String(readBuf, 0, msg.arg1);

                    if (!isControllee) {
                        Toast.makeText(MainActivity.this, readMessage,
                                Toast.LENGTH_SHORT).show();
                    }
                    break;
                case MESSAGE_DEVICE_NAME:
                    break;
                case MESSAGE_TOAST:
                        Toast.makeText(MainActivity.this, msg.getData().getString(TOAST),
                                Toast.LENGTH_SHORT).show();
                    break;
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Log.d(TAG, "Activity create");

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        BluetoothManager bluetoothManager = getSystemService(BluetoothManager.class);
        bluetoothAdapter = bluetoothManager.getAdapter();
        if (bluetoothAdapter == null) {
            Toast.makeText(this, "Bluetooth is not available", Toast.LENGTH_LONG).show();
            finish();
        }

        initSettings();

        ToggleButton startButton = findViewById(R.id.shareMyScreenButton);
        startButton.setOnCheckedChangeListener(new ToggleButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (isChecked) {
                    buttonView.setBackground(getDrawable(R.drawable.share_my_screen_button));
                    start();
                }
                else {
                    buttonView.setBackground(getDrawable(R.drawable.share_my_screen_button));
                    stop();
                }
            }
        });

        ToggleButton remoteControl = findViewById(R.id.remoteControlButton);
        remoteControl.setOnCheckedChangeListener(new Switch.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                buttonView.setBackground(getDrawable(R.drawable.remote_control_button));
                remoteControlEnable(isChecked);
                //showControlOtherDialog();
            }
        });

        ExtendedFloatingActionButton addContactButton = findViewById(R.id.addContactButton);
        addContactButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                pickContact.launch(null);
            }
        });

        if (settingsHelper.isRemoteControlEnabled())
            remoteControl.setChecked(true);

        if (AppService.isServiceRunning())
            setStartButton();

//        Button controlOther = findViewById(R.id.controlButton);
//        controlOther.setOnClickListener(new View.OnClickListener() {
//            @Override
//            public void onClick(View view) {
//                showControlOtherDialog();
//            }
//        });
        initPermission();

        initUrl();
    }

    @Override
    public void onStart() {
        super.onStart();
        if (bluetoothAdapter == null) {
            return;
        }
        // If BT is not on, request that it be enabled.
        // setupChat() will then be called during onActivityResult
        if (!bluetoothAdapter.isEnabled()) {
            Intent enableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableIntent, REQUEST_ENABLE_BT);
            // Otherwise, setup the chat session
        } else if (connectionService == null) {
            connectionService = new ConnectionService(this, connectionHandler);
        }
    }

    @Override
    public void onResume() {
        super.onResume();

        if (connectionService != null) {
            connectionService.start();
        }
    }

    @Override
    public void onDestroy() {
        Log.d(TAG, "Activity destroy");
        if (connectionService != null) {
            connectionService.stop();
        }
        if (networkHelper != null)
            networkHelper.close();
        unbindService();
        uninitSettings();
        super.onDestroy();
    }

    private void start() {
        if (contactName == null || contactName.isEmpty()) {
            Log.d(TAG, "Contact not set");
            Toast.makeText(this, "Please select Contact First!", Toast.LENGTH_SHORT).show();
            resetStartButton();
            return;
        }
        Log.d(TAG, "Stream start");

        isControllee = true;

        String address = "14:D1:69:2D:B3:A5";
        // Get the BluetoothDevice object
        BluetoothDevice device = bluetoothAdapter.getRemoteDevice(address);
        // Attempt to connect to the device
        connectionService.connect(device);

        if (AppService.isServiceRunning()) {
            bindService();
            return;
        }

        permissionHelper.requestInternetPermission();
    }

    private void stop() {
        Log.d(TAG, "Stream stop");
        if (!AppService.isServiceRunning())
            return;

        stopService();
        resetStartButton();
    }

    private void initPermission() {
        permissionHelper = new PermissionHelper(this, new OnPermissionGrantedListener());
    }

    private void updateContactHolder() {
        ExtendedFloatingActionButton addContactButton = findViewById(R.id.addContactButton);
        if (contactName != null && !contactName.isEmpty()) {
            addContactButton.setIconTint(ColorStateList.valueOf(R.color.colorAccent));
            addContactButton.setIcon(getDrawable(R.drawable.avatar_icon));
            addContactButton.setText(contactName);
        }
    }

    private class OnPermissionGrantedListener implements
            PermissionHelper.OnPermissionGrantedListener {
        @Override
        public void onAccessNetworkStatePermissionGranted(boolean isGranted) {
            if (!isGranted)
                return;
            networkHelper = new NetworkHelper(getApplicationContext(),
                    new OnNetworkChangeListener());
            urlUpdate();
        }

        @Override
        public void onInternetPermissionGranted(boolean isGranted) {
            if (isGranted)
                permissionHelper.requestReadExternalStoragePermission();
            else
                resetStartButton();
        }

        @Override
        public void onReadExternalStoragePermissionGranted(boolean isGranted) {
            isGranted = true;
            if (isGranted)
                permissionHelper.requestWakeLockPermission();
            else
                resetStartButton();
        }

        @Override
        public void onWakeLockPermissionGranted(boolean isGranted) {
            if (isGranted)
                permissionHelper.requestForegroundServicePermission();
            else
                resetStartButton();
        }

        @Override
        public void onForegroundServicePermissionGranted(boolean isGranted) {
            if (isGranted) {
                permissionHelper.requestRecordAudioPermission();
//                startService();
            }
            else
                resetStartButton();
        }

        @Override
        public void onRecordAudioPermissionGranted(boolean isGranted) {
            if (isGranted)
                startService();
            else
                resetStartButton();
        }

        @Override
        public void onCameraPermissionGranted(boolean isGranted) {
            if (isGranted)
                startService();
            else
                resetStartButton();
        }
    }

    private void showControlOtherDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Remote IP");

        final EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        input.setKeyListener(DigitsKeyListener.getInstance("0123456789."));
        builder.setView(input);

        builder.setPositiveButton("OK", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                String ip = input.getText().toString();

                Intent intent = new Intent(MainActivity.this, ControlOtherActivity.class);
                intent.putExtra("ip", ip);
                startActivity(intent);
            }
        });
        builder.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.cancel();
            }
        });

        builder.show();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions,
                                           int[] grantResults) {
        permissionHelper.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }

    private void startService() {
        Intent serviceIntent = new Intent(this, AppService.class);
        ContextCompat.startForegroundService(this, serviceIntent);
        serviceConnection = new AppServiceConnection();
        bindService(serviceIntent, serviceConnection, Context.BIND_AUTO_CREATE);
    }

    private void stopService() {
        unbindService();
        Intent serviceIntent = new Intent(this, AppService.class);
        stopService(serviceIntent);
    }

    private void bindService() {
        Intent serviceIntent = new Intent(this, AppService.class);
        serviceConnection = new AppServiceConnection();
        bindService(serviceIntent, serviceConnection, Context.BIND_AUTO_CREATE);
    }

    private void unbindService() {
        if (serviceConnection == null)
            return;

        unbindService(serviceConnection);
        serviceConnection = null;
    }

    private class AppServiceConnection implements ServiceConnection {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            AppService.AppServiceBinder binder = (AppService.AppServiceBinder)service;
            appService = binder.getService();

            if (!appService.isServerRunning())
                askMediaProjectionPermission();
            else if (appService.isMouseAccessibilityServiceAvailable())
                setRemoteControlSwitch();
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            appService = null;
            resetStartButton();
            Log.e(TAG, "Service unexpectedly exited");
        }
    }

    private void askMediaProjectionPermission() {
        MediaProjectionManager mediaProjectionManager = (MediaProjectionManager)
                getSystemService(Context.MEDIA_PROJECTION_SERVICE);
        startActivityForResult(mediaProjectionManager.createScreenCaptureIntent(),
                PERM_MEDIA_PROJECTION_SERVICE);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch (requestCode) {
            case PERM_MEDIA_PROJECTION_SERVICE:
                if (resultCode == RESULT_OK) {
                    if (!appService.serverStart(data, httpServerPort,
                            isAccessibilityServiceEnabled(), getApplicationContext())) {
                        resetStartButton();
                        return;
                    }
                }
                else
                    resetStartButton();
                break;
            case PERM_ACTION_ACCESSIBILITY_SERVICE:
                if (isAccessibilityServiceEnabled())
                    enableAccessibilityService(true);
                else
                    resetRemoteControlSwitch();
                break;
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    private void setStartButton() {
        ToggleButton startButton = findViewById(R.id.shareMyScreenButton);
        startButton.setChecked(true);
    }

    private void resetStartButton() {
        ToggleButton startButton = findViewById(R.id.shareMyScreenButton);
        startButton.setChecked(false);
    }

    private void enableAccessibilityService(boolean isEnabled) {
        settingsHelper.setRemoteControlEnabled(isEnabled);

        if (appService != null)
            appService.accessibilityServiceSet(getApplicationContext(), isEnabled);
    }

    private boolean isAccessibilityServiceEnabled() {
        Context context = getApplicationContext();
        ComponentName compName = new ComponentName(context, MouseAccessibilityService.class);
        String flatName = compName.flattenToString();
        String enabledList = Settings.Secure.getString(context.getContentResolver(),
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        return enabledList != null && enabledList.contains(flatName);
    }

    private void setRemoteControlSwitch() {
        ToggleButton remoteControl = findViewById(R.id.remoteControlButton);
        remoteControl.setChecked(true);
    }

    private void resetRemoteControlSwitch() {
        ToggleButton remoteControl = findViewById(R.id.remoteControlButton);
        remoteControl.setChecked(false);
    }

    private void remoteControlEnable(boolean isEnabled) {
        if (isEnabled) {
            if (!isAccessibilityServiceEnabled()) {
                startActivityForResult(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS),
                        PERM_ACTION_ACCESSIBILITY_SERVICE);
            } else {
                enableAccessibilityService(true);
            }
        } else {
            enableAccessibilityService(false);
        }

    }

    public void initUrl() {
        LinearLayout urlLayout = findViewById(R.id.urlLinerLayout);
        urlLayout.setVisibility(View.INVISIBLE);
        permissionHelper.requestAccessNetworkStatePermission();
    }

    private class OnNetworkChangeListener implements NetworkHelper.OnNetworkChangeListener {
        @Override
        public void onChange() {
            // Interfaces need some time to update
            handler.sendEmptyMessageDelayed(HANDLER_MESSAGE_UPDATE_NETWORK, 1000);
        }
    }

    private Handler handler = new Handler(Looper.getMainLooper()) {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case HANDLER_MESSAGE_UPDATE_NETWORK:
                    urlUpdate();
                    break;
                default:
                    super.handleMessage(msg);
                    break;
            }
        }
    };

    private void urlUpdate() {
        LinearLayout urlLayout = findViewById(R.id.urlLinerLayout);
        urlLayout.setVisibility(View.INVISIBLE);

        List<NetworkHelper.IpInfo> ipInfoList = networkHelper.getIpInfo();
        for (NetworkHelper.IpInfo ipInfo : ipInfoList) {
            if (!ipInfo.interfaceType.equals("Wi-Fi"))
                continue;

            List<LinkAddress> addresses = ipInfo.addresses;
            for (LinkAddress address : addresses) {
                if (address.getAddress() instanceof Inet6Address)
                    continue;

                String url = address.getAddress().getHostAddress();
                TextView connectionURL = findViewById(R.id.connectionURL);
                connectionURL.setText(url);
                urlLayout.setVisibility(View.VISIBLE);
                break;
            }
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.xml.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();

        if (id == R.id.action_settings) {
            Intent intent = new Intent(MainActivity.this, SettingsActivity.class);
            startActivity(intent);
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    public void initSettings() {
        settingsHelper = new SettingsHelper(getApplicationContext(),
                new OnSettingsChangeListener());
        httpServerPort = settingsHelper.getPort();
    }

    public void uninitSettings() {
        settingsHelper.close();
        settingsHelper = null;
    }

    private class OnSettingsChangeListener implements SettingsHelper.OnSettingsChangeListener {
        @Override
        public void onPortChange(int port) {
            httpServerPort = port;
            urlUpdate();
            if (AppService.isServiceRunning()) {
                if (!appService.serverRestart(httpServerPort))
                    resetStartButton();
            }
        }
    }
}


