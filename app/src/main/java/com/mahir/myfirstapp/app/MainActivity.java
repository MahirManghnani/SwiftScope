package com.mahir.myfirstapp.app;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.hardware.usb.UsbConstants;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbEndpoint;
import android.hardware.usb.UsbInterface;
import android.hardware.usb.UsbManager;
import android.os.Handler;
import android.os.Bundle;

import android.util.Log;
import android.view.View;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.SeekBar;
import android.widget.Switch;

import com.mahir.adctest.app.R;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.IntBuffer;

import com.jjoe64.graphview.GraphView;
import com.jjoe64.graphview.GraphViewSeries;
import com.jjoe64.graphview.LineGraphView;


public class MainActivity extends Activity {
    // Tag for log
    private static final String TAG = MainActivity.class.getName();

    // Bytes for commands
    public static final byte TRIGGER_ON = 0x01;
    public static final byte TRIGGER_OFF = 0x10;
    public static final byte TRIGGER_SET = 0x03;
    public static final byte SAMP_RATE_SET = 0x04;
    public static final byte REQUEST_DATA = 0x05;

    // Largest value recieved
    public static final double MAX_Y = 3475;

    // Starting values
    double currMaxY = MAX_Y;
    double currMinY = 0;

    // USB variables
    private UsbManager mUsbManager;
    private UsbDevice mDevice;
    private UsbDeviceConnection mConnection;
    private UsbEndpoint mEndpointBulkIn, mEndpointBulkOut;

    // Views
    SeekBar triggerSeek;
    RadioGroup rg;
    RadioGroup vrg;
    GraphView graphView;
    GraphViewSeries gSeries;
    GraphViewSeries tSeries;
    ImageButton TBleft;
    ImageButton TBright;
    ImageButton VBleft;
    ImageButton VBright;
    ImageButton up;
    ImageButton down;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Disable the trigger seekbar and set the listener
        triggerSeek = (SeekBar) findViewById(R.id.trigger_seek);
        triggerSeek.setMax((int) MAX_Y);
        triggerSeek.setEnabled(false);
        triggerSeek.setOnSeekBarChangeListener(new BarListener());

        // Set the listeners for both radio groups
        rg = (RadioGroup) findViewById(R.id.rgroup);
        rg.check(R.id.r6);
        rg.setOnCheckedChangeListener(new rgListener());

        vrg = (RadioGroup) findViewById(R.id.vrgroup);
        vrg.setOnCheckedChangeListener(new vrgListener());

        // Assign image buttons
        TBleft = (ImageButton) findViewById(R.id.TBleft);
        TBright = (ImageButton) findViewById(R.id.TBright);

        VBleft = (ImageButton) findViewById(R.id.VBleft);
        VBright = (ImageButton) findViewById(R.id.VBright);

        up = (ImageButton) findViewById(R.id.up);
        down = (ImageButton) findViewById(R.id.down);

        // Create series for when app starts
        gSeries = new GraphViewSeries(new GraphView.GraphViewData[] {
                new GraphView.GraphViewData((double) 0, MAX_Y/2d),
                new GraphView.GraphViewData((double) 1500, MAX_Y/2d)
        });

        // Apply settings for GraphView
        graphView = new LineGraphView(this, "");
        graphView.addSeries(gSeries);
        graphView.setManualYAxisBounds(currMaxY, currMinY);
        graphView.setViewPort((double) 0, (double) 1000);
        graphView.setScrollable(true);
        graphView.getGraphViewStyle().setNumHorizontalLabels(11);
        graphView.getGraphViewStyle().setNumVerticalLabels(9);
        graphView.getGraphViewStyle().setTextSize(0);

        vrg.check((R.id.v8));


        LinearLayout layout = (LinearLayout) findViewById(R.id.graph1);
        layout.addView(graphView);

        // Receive intent with UsbManager information
        mUsbManager = (UsbManager)getSystemService(Context.USB_SERVICE);

        // Set up BroadcastReceiver to check for USB detached
        BroadcastReceiver mUsbReceiver = new BroadcastReceiver() {
            public void onReceive(Context context, Intent intent) {
                String action = intent.getAction();

                if (UsbManager.ACTION_USB_DEVICE_DETACHED.equals(action)) {
                    UsbDevice device = (UsbDevice)intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
                    if (device != null) {
                        Intent i = new Intent(MainActivity.this, splash.class);
                        finish();
                        startActivity(i);
                    }
                }
            }
        };

        IntentFilter filter = new IntentFilter();
        filter.addAction(UsbManager.ACTION_USB_DEVICE_DETACHED);
        registerReceiver(mUsbReceiver, filter);
    }

    @Override
    public void onPause() {
        super.onPause();
    }

    @Override
    public void onResume() {
        super.onResume();

        // Get the intent
        Intent intent = getIntent();
        Log.d(TAG, "intent: " + intent);
        String action = intent.getAction();

        // Check the action from the UsbManager
        // If a USB device has been attached call setDevice
        UsbDevice device = (UsbDevice)intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
        if (UsbManager.ACTION_USB_DEVICE_ATTACHED.equals(action)) {
            setDevice(device);
            //checkDeviceEPs(device);
        }
        else if (UsbManager.ACTION_USB_DEVICE_DETACHED.equals(action)) {
            if (mDevice != null && mDevice.equals(device)) {
                setDevice(null);
                Intent i = new Intent(this, splash.class);
                this.startActivity(i);
            }
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
    }

    // Function to check the end-points of the device (for de-bugging)
    private void checkDeviceEPs(UsbDevice device) {
        Log.d(TAG, "checkDevice: " + device);
        int interfaceCount = device.getInterfaceCount();
        Log.d(TAG, "Number of interfaces = " + interfaceCount);
        for(int i=0; i<interfaceCount; i++) {
            UsbInterface intf = device.getInterface(i);
            int endpointCount = intf.getEndpointCount();
            Log.d(TAG, "    Interface " + i + ": no. of endpoints = " + endpointCount);

            for(int j=0; j<endpointCount; j++) {
                UsbEndpoint ep = intf.getEndpoint(j);
                Log.d(TAG, "        endpoint " + j + " is " + ep.getType() + " " + ep.getDirection());
            }

        }
    }

    // Function to check that the connected device is correct
    // and assign the endpoints for use
    private void setDevice(UsbDevice device) {
        // the device should have 1 interface
        Log.d(TAG, "setDevice " + device);
        if (device.getInterfaceCount() != 1) {
            Log.e(TAG, "could not find interface");
            return;
        }
        UsbInterface intf = device.getInterface(0);

        // device should have two endpoints
        if (intf.getEndpointCount() != 2) {
            Log.e(TAG, "could not find endpoint");
            return;
        }

        // temp endpoints
        UsbEndpoint ep0 = intf.getEndpoint(0);
        UsbEndpoint ep1 = intf.getEndpoint(1);

        // endpoint 0 should be of type bulk and direction in
        if (ep0.getType() != UsbConstants.USB_ENDPOINT_XFER_BULK
                && ep0.getDirection() != UsbConstants.USB_DIR_IN) {
            Log.e(TAG, "endpoint 0 is not bulk in");
            return;
        }

        // endpoint 1 should be of type bulk and direction out
        if (ep1.getType() != UsbConstants.USB_ENDPOINT_XFER_BULK
                && ep1.getDirection() != UsbConstants.USB_DIR_OUT) {
            Log.e(TAG, "endpoint 1 is not bulk in");
            return;
        }

        // assign device and endpoints
        mDevice = device;
        mEndpointBulkIn = ep0;
        mEndpointBulkOut = ep1;

        if (device != null) {
            UsbDeviceConnection connection = mUsbManager.openDevice(device);
            if (connection != null && connection.claimInterface(intf, true)) {
                Log.d(TAG, "open SUCCESS");
                mConnection = connection;
                RequestData();
            } else {
                Log.d(TAG, "open FAIL");
                mConnection = null;
            }
        }
    }


    // Function that requests data from the microcontroller
    // then starts a thread to receive the data
    public void RequestData() {
        if(mConnection != null) {
            mConnection.bulkTransfer(mEndpointBulkOut, (new byte[] {REQUEST_DATA, 0, 0}), 3, 0);
            Handler handler = new Handler();
            ReadThread thread = new ReadThread(handler);
            thread.start();
        }
    }

    // Function that checks the status of the trigger switch and sends commands
    // to switch on and off to the microcontroller
    public void sendTrigger(View view) {
        if(mConnection != null) {
            boolean on = ((Switch) view).isChecked();
            if(on) {
                triggerSeek.setEnabled(true);
                mConnection.bulkTransfer(mEndpointBulkOut, (new byte[] {TRIGGER_ON, 0, 0}), 3, 0);
            }
            else {
                mConnection.bulkTransfer(mEndpointBulkOut, (new byte[] {TRIGGER_OFF, 0, 0}), 3, 0);
                triggerSeek.setProgress(0);
                triggerSeek.setEnabled(false);
            }
        }
    }

    // Function which adjusts the vertical position of the display from
    // user input buttons
    public void vPos(View view) {
        int i = view.getId();
        if (i == up.getId()) {
            currMaxY = currMaxY - (currMaxY-currMinY)/100d;
            currMinY = currMinY - (currMaxY-currMinY)/100d;
        }
        else if (i == down.getId()) {
            currMaxY = currMaxY + (currMaxY-currMinY)/100d;
            currMinY = currMinY + (currMaxY-currMinY)/100d;
        }
        graphView.setManualYAxisBounds(currMaxY, currMinY);
    }


    // Function that checks buttons for the bases (time and voltage) and
    // makes changes to the relevant radio buttons
    public void shiftBase(View view) {
        String bTAG = (view.getTag()).toString();
        int rID;
        if(view.getId() == R.id.TBleft || view.getId() == R.id.TBright) {
            rID = rg.getCheckedRadioButtonId();
        }
        else {
            rID = vrg.getCheckedRadioButtonId();
        }
        if (bTAG.equals("l")) {
            ((RadioButton) findViewById(rID - 1)).setChecked(true);
        } else if (bTAG.equals("r")) {
            ((RadioButton) findViewById(rID + 1)).setChecked(true);
        }
    }

    // Sends sample rate code to the microcontroller
    public void sendSamRate(int tag) {
        if(mConnection != null) {
            byte[] samp_code = new byte[2];

            switch (tag) {
                case 2:
                case 5:
                case 7:
                case 10:
                case 13:
                case 16:
                case 19:
                    samp_code[0] = 1;
                    break;
                case 1:
                case 4:
                case 6:
                case 9:
                case 12:
                case 15:
                case 18:
                    samp_code[0] = 2;
                    break;
                case 3:
                case 8:
                case 11:
                case 14:
                case 17:
                    samp_code[0] = 5;
                    break;
            }

            switch (tag) {
                case 4:
                case 5:
                    samp_code[1] = 6;
                    break;
                case 6:
                case 7:
                    samp_code[1] = 5;
                    break;
                case 8:
                case 9:
                case 10:
                    samp_code[1] = 4;
                    break;
                case 11:
                case 12:
                case 13:
                    samp_code[1] = 3;
                    break;
                case 14:
                case 15:
                case 16:
                    samp_code[1] = 2;
                    break;
                case 17:
                case 18:
                case 19:
                    samp_code[1] = 1;
                    break;
            }

            mConnection.bulkTransfer(mEndpointBulkOut, (new byte[]{SAMP_RATE_SET, samp_code[0], samp_code[1]}), 3, 0);
        }
    }

    // Updates the GraphView and then requests more data
    private void plot(GraphView.GraphViewData[] dataArray) {
        gSeries.resetData(dataArray);
        RequestData();
    }

    // Thread to receive new data and convert it for the GraphView
    class ReadThread extends Thread {
        private final Handler mHandler;
        ReadThread(Handler handler) {
            mHandler = handler;
        }
        @Override
        public void run() {
            if (mConnection != null) {
                final byte[] output = new byte[6000];
                if (mConnection.bulkTransfer(mEndpointBulkIn, output, output.length, 0) > 0) {
                    GraphDataBuffer gdb = new GraphDataBuffer(ByteBuffer.wrap(output)
                            .order(ByteOrder.LITTLE_ENDIAN).asIntBuffer());
                    gdb.makeArray();
                    final GraphView.GraphViewData[] g = gdb.dataArr;
                    mHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            plot(g);
                        }
                    });
                }
            }
        }
    }

    // Listener for different stages of changing value of trigger seek bar
    public class BarListener implements SeekBar.OnSeekBarChangeListener {

        // If the progress has changed update the trigger line
        @Override
        public void onProgressChanged(SeekBar bar,  int TRIGGER_LEVEL, boolean fromUser) {
            tSeries.resetData(new GraphView.GraphViewData[] {
                    new GraphView.GraphViewData(0, (double) TRIGGER_LEVEL),
                    new GraphView.GraphViewData(1500, (double) TRIGGER_LEVEL)
            });
        }

        // When first touched add line to GraphView
        @Override
        public void onStartTrackingTouch(SeekBar seekBar) {
            tSeries = new GraphViewSeries("", new GraphViewSeries.GraphViewSeriesStyle(Color.RED,3) ,
                    new GraphView.GraphViewData[] {
                        new GraphView.GraphViewData(0, seekBar.getProgress()),
                        new GraphView.GraphViewData(1500, seekBar.getProgress())
                    });
            graphView.addSeries(tSeries);

        }

        // When touch stops remove line and data to microcontroller
        @Override
        public void onStopTrackingTouch(SeekBar seekBar) {
            graphView.removeSeries(tSeries);
            short x = (short) seekBar.getProgress();
            byte[] y = {(byte)(x & 0xff), (byte)((x >> 8) & 0xff)};
            mConnection.bulkTransfer(mEndpointBulkOut, (new byte[] {TRIGGER_SET, y[0], y[1]}), 3, 0);
        }
    }


    // Listener to see if the RadioButton checks have changed
    // Disables buttons if necessary
    public class rgListener implements RadioGroup.OnCheckedChangeListener {

        @Override
        public void onCheckedChanged(RadioGroup group, int checkedId) {
            RadioButton rb = (RadioButton) findViewById(checkedId);
            String rbTAG = rb.getTag().toString();
            if(rbTAG.equals("1")) {
                TBleft.setEnabled(false);
            }
            else {
                TBleft.setEnabled(true);
            }
            if(rbTAG.equals("19")) {
                TBright.setEnabled(false);
            }
            else {
                TBright.setEnabled(true);
            }
            sendSamRate(Integer.parseInt(rbTAG));
        }
    }

    // Same but for the voltage base
    public class vrgListener implements RadioGroup.OnCheckedChangeListener {

        @Override
        public void onCheckedChanged(RadioGroup group, int checkedId) {
            RadioButton rb = (RadioButton) findViewById(checkedId);
            double vDIV = Double.parseDouble(rb.getTag().toString());
            if(vDIV==0.005) {
                VBleft.setEnabled(false);
            }
            else {
                VBleft.setEnabled(true);
            }
            if(vDIV==20) {
                VBright.setEnabled(false);
            }
            else {
                VBright.setEnabled(true);
            }
            double diff = (MAX_Y/20d)  * vDIV;
            double middle = (currMaxY + currMinY)/2d;
            currMaxY = middle + diff;
            currMinY = middle - diff;
            graphView.setManualYAxisBounds(currMaxY, currMinY);

        }
    }

    // Custon class to get IntBuffer and convert into GraphViewData array
    public class GraphDataBuffer {

        IntBuffer buff;
        GraphView.GraphViewData[] dataArr;

        public GraphDataBuffer(IntBuffer buffer) {
            buff = buffer;
            dataArr = new GraphView.GraphViewData[buff.capacity()];
        }

        void makeArray() {
            for(int i=0; i<buff.capacity(); i++) {
                dataArr[i] = new GraphView.GraphViewData((double) buff.position(), (double) buff.get());
            }
        }
    }

}
