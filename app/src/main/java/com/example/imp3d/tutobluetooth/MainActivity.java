package com.example.imp3d.tutobluetooth;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Handler;
import android.os.Message;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.ListView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.Set;
import java.util.UUID;

public class MainActivity extends AppCompatActivity {

    private final static String TAG_INFO = "franck";
    // #defines for identifying shared types between calling functions
    public final static int REQUEST_ENABLE_BT = 1; // used to identify adding bluetooth names
    public final static int MESSAGE_READ = 2; // used in bluetooth handler to identify message update
    public final static int CONNECTING_STATUS = 3; // used in bluetooth handler to identify message status
    private static final UUID BTMODULEUUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB"); // "random" unique identifier

    // GUI Components
    private TextView mBluetoothStatus;
    private TextView mReadBuffer;
    private TextView mMotorPos;
    private Button mScanBtn;
    private Button mOffBtn;
    private Button mListPairedDevicesBtn;
    private Button mDiscoverBtn;
    private BluetoothAdapter mBTAdapter;
    private BluetoothSocket mBTSocket = null;
    private Set<BluetoothDevice> mPairedDevices;
    private ArrayAdapter<String> mBTArrayAdapter;
    private ListView mDevicesListView;
    private CheckBox mLED1;
    private SeekBar motor;
    private StringBuilder recDataString = new StringBuilder();

    private Handler mHandler; // Our main handler that will receive callback notifications
    private ConnectedThread mConnectedThread; // bluetooth background worker thread to send and receive data

    @SuppressLint("HandlerLeak")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        //Get object corresponding to the view XML
        mLED1 = findViewById(R.id.checkboxLED1);
        mReadBuffer = findViewById(R.id.readBuffer);
        mBluetoothStatus = findViewById(R.id.bluetoothStatus);
        mScanBtn = findViewById(R.id.scan);
        mOffBtn = findViewById(R.id.off);
        mListPairedDevicesBtn = findViewById(R.id.PairedBtn);
        mDiscoverBtn = findViewById(R.id.discover);
        mDevicesListView = findViewById(R.id.devicesListView);
        motor = findViewById(R.id.seekBar);
        mMotorPos = findViewById(R.id.motorPosSend);
        mBTAdapter = BluetoothAdapter.getDefaultAdapter(); //Get default bluetooth adapter

        mBTArrayAdapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1);
        mDevicesListView.setAdapter(mBTArrayAdapter);   //Assign model to view
        mDevicesListView.setOnItemClickListener(mDeviceClickListener);

        mLED1.setEnabled(mBTAdapter.isEnabled());

        mHandler = new Handler() {
            @Override
            public void handleMessage(Message msg) {

                switch (msg.what) {
                    case CONNECTING_STATUS:
                        if (msg.arg1 == 1)
                            mBluetoothStatus.setText("Connected to device:" + msg.obj);
                        else mBluetoothStatus.setText("Connection failed!");
                        break;
                }

                if (msg.what == MESSAGE_READ) {
                    String readMessage = (String)msg.obj;
                    mReadBuffer.setText(readMessage);
                    Log.i(TAG_INFO, "MESSAGE INCOMING =>  " + readMessage);
                }
            }
        };

        if (mBTArrayAdapter == null) {
            // Device does not support Bluetooth
            mBluetoothStatus.setText("Status: Bluetooth not found");
            Toast.makeText(getApplicationContext(), "Bluetooth device not found!", Toast.LENGTH_SHORT).show();
        } else {

            listPairedDevices();

            mLED1.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {

                    Log.i(TAG_INFO, "Is Checked " + (mLED1.isChecked() ? "1" : "0"));

                    if (mConnectedThread == null) //First check to make sure thread created
                        return;

                    mConnectedThread.write(mLED1.isChecked() ? "1" : "0");
                }
            });

            motor.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {

                @Override
                public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                }

                @Override
                public void onStartTrackingTouch(SeekBar seekBar) {

                }

                @Override
                public void onStopTrackingTouch(SeekBar seekBar) {
                    Log.i(TAG_INFO, "change Value Motor " + seekBar.getProgress());

                    if (mConnectedThread == null) //First check to make sure thread created
                        return;

                    mConnectedThread.write("MA;"+seekBar.getProgress());
                    mMotorPos.setText(String.valueOf(seekBar.getProgress()));
                }
            });

            mScanBtn.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    bluetoothOn(v);
                }
            });

            mOffBtn.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    bluetoothOff(v);
                }
            });

            mListPairedDevicesBtn.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    listPairedDevices(/*v*/);
                }
            });

            mDiscoverBtn.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    discover(v);
                }
            });
        }
    }//On Create end

    private void bluetoothOn(View view) {
        if (!mBTAdapter.isEnabled()) {
            Intent enableBTIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBTIntent, REQUEST_ENABLE_BT);
        } else {
            Toast.makeText(getApplicationContext(), "Bluetooth is already on", Toast.LENGTH_SHORT).show();
        }
    }

    //Est-ce vraiment utile ? oui ! Cela sert pour savoir sur quel bouton l'utilisateur a clicker
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        //super.onActivityResult(requestCode, resultCode, data);
        // Check which request we're responding to

        if (requestCode == REQUEST_ENABLE_BT) {
            // Make sure the request was successful
            if (resultCode == RESULT_OK) {
                // The user picked a contact.
                // The Intent's data Uri identifies which contact was selected.
                mLED1.setEnabled(true);
                mBluetoothStatus.setText("Enabled");
                Toast.makeText(getApplicationContext(), "Bluetooth turned on", Toast.LENGTH_SHORT).show();
            } else {
                mBluetoothStatus.setText("Disabled");
                Toast.makeText(getApplicationContext(), "Cancled", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void bluetoothOff(View view) {
        mLED1.setEnabled(false);
        if(blReciver.isInitialStickyBroadcast())
            unregisterReceiver(blReciver);
        mBTAdapter.disable();
        mBluetoothStatus.setText("Bluetooth OFF");
        Toast.makeText(getApplicationContext(), "Bluetooth OFF", Toast.LENGTH_SHORT).show();
    }

    private void discover(View v) {

        if (mBTAdapter.isDiscovering()) {
            mBTAdapter.cancelDiscovery();
            Toast.makeText(getApplicationContext(), "Discovery stopped", Toast.LENGTH_SHORT).show();
        } else {
            if (mBTAdapter.isEnabled()) {
                mBTArrayAdapter.clear();
                IntentFilter filter = new IntentFilter();
                filter.addAction(BluetoothAdapter.ACTION_DISCOVERY_STARTED);
                filter.addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);
                filter.addAction(BluetoothDevice.ACTION_FOUND);
                registerReceiver(blReciver, filter);
                Toast.makeText(getApplicationContext(), "Discovery started", Toast.LENGTH_SHORT).show();
                mBTAdapter.startDiscovery();

            } else {
                Toast.makeText(getApplicationContext(), "Bluetooth OFF", Toast.LENGTH_SHORT).show();
            }
        }
    }

    final BroadcastReceiver blReciver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {

            if(!mBTAdapter.isEnabled())
                return;

            switch (intent.getAction()) {
                case BluetoothAdapter.ACTION_DISCOVERY_STARTED:
                    mBluetoothStatus.setText("Searching...");
                    Log.i(TAG_INFO, "Start discovering");
                    break;

                case BluetoothAdapter.ACTION_DISCOVERY_FINISHED:
                    Log.i(TAG_INFO, "Discovering finished");
                    mBluetoothStatus.setText("End");
                    break;

                case BluetoothDevice.ACTION_FOUND:
                    BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                    mBTArrayAdapter.add(device.getName() + "\n" + device.getAddress());
                    mBTArrayAdapter.notifyDataSetChanged();
                    break;
                default:
                        break;
            }
        }
    };

    private void listPairedDevices() {
        mPairedDevices = mBTAdapter.getBondedDevices();
        int cpt=0;
        if (mBTAdapter.isEnabled()) {
            for (BluetoothDevice dev : mPairedDevices) {
                mBTArrayAdapter.add(dev.getName() + "\n" + dev.getAddress());
                if(dev.getName().equals("HC-05-BLE"))
                {
                    mDevicesListView.performItemClick(mBTArrayAdapter.getView(cpt,null, null),
                            cpt,
                            mBTArrayAdapter.getItemId(cpt));
                }
                cpt++;
            }

            //Est ce utile ?
            //mBTArrayAdapter.notifyDataSetChanged();
            Toast.makeText(getApplicationContext(), "Show Paired Devices", Toast.LENGTH_SHORT).show();
        } else
            Toast.makeText(getApplicationContext(), "Bluetooth not on", Toast.LENGTH_SHORT).show();
    }

    private AdapterView.OnItemClickListener mDeviceClickListener = new AdapterView.OnItemClickListener() {
        @Override
        public void onItemClick(AdapterView<?> parent, View view, int position, long id) {

            if (!mBTAdapter.isEnabled()) {
                Toast.makeText(getBaseContext(), "Bluetooth not on", Toast.LENGTH_SHORT).show();
                return;
            }

            mBluetoothStatus.setText("Connecting...");
            String info = ((TextView) view).getText().toString();
            final String name = info.substring(0, info.length() - 17);
            final String adresse = info.substring(info.length() - 17);

            new Thread() {
                @Override
                public void run() {
                    boolean failed = false;
                    BluetoothDevice device = mBTAdapter.getRemoteDevice(adresse);

                    try {
                        mBTSocket = device.createRfcommSocketToServiceRecord(BTMODULEUUID);
                    } catch (IOException e) {
                        failed = true;
                        Toast.makeText(getBaseContext(), "Socket creation failed", Toast.LENGTH_SHORT).show();
                    }

                    try {
                        mBTSocket.connect();
                    } catch (IOException e) {
                        try {
                            failed = true;
                            mBTSocket.close();
                            mHandler.obtainMessage(CONNECTING_STATUS, -1).sendToTarget();
                        } catch (IOException e1) {
                            Toast.makeText(getBaseContext(), "Socket creation failed", Toast.LENGTH_SHORT).show();
                        }
                    }

                    if(!failed)
                    {
                        mConnectedThread = new ConnectedThread(mHandler, mBTSocket);
                        mConnectedThread.start();

                        mHandler.obtainMessage(CONNECTING_STATUS, 1,-1, name).sendToTarget();
                    }
                }
            }.start();
        }
    };
}