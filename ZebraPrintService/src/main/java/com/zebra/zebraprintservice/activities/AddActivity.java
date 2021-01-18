package com.zebra.zebraprintservice.activities;

import android.annotation.SuppressLint;
import android.app.ActionBar;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothClass;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import android.net.ConnectivityManager;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ListView;

import androidx.annotation.NonNull;

import com.zebra.zebraprintservice.BuildConfig;
import com.zebra.zebraprintservice.NetworkDevice;
import com.zebra.zebraprintservice.PrinterAdapter;
import com.zebra.zebraprintservice.R;
import com.zebra.zebraprintservice.database.PrinterDatabase;

import org.parceler.Parcels;

import java.net.DatagramPacket;
import java.net.InetSocketAddress;
import java.net.MulticastSocket;
import java.net.SocketAddress;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class AddActivity extends Activity
{
    private static final String TAG = AddActivity.class.getSimpleName();
    private static final boolean DEBUG = BuildConfig.DEBUG & true;
    private static final String MULTICAST_ADDRESS = "224.0.1.55";
    private static final int MULTICAST_PORT = 4201;
    private static final int MULTICAST_RECV = 65534;
    private static final int MULTICAST_TIMEOUT = 5000;
    private static final int MAX_DATAGRAM_SIZE = 65507;
    private static final int NET_TIMEOUT = 30000;
    private static final int BT_TIMEOUT = 30000;
    private byte[] DEVICE_DISCOVERY_REQUEST_PACKET = { 46, 44, 58, 1, 0, 0 };
    private HashMap<PrinterDatabase.Printer,Long> mPrinterTimeouts = new HashMap<>();
    private List<PrinterDatabase.Printer> mStoredList = null;
    private BluetoothAdapter mBluetoothAdapter = null;
    private BluetoothManager mBluetoothManager;
    private WifiManager mWifiManager = null;
    private UsbManager mUsbManager = null;
    private Thread mNetworkThread = null;
    private Thread mExpireThread = null;
    private boolean bQuit = false;
    private boolean bNetQuit = false;
    private Object mLock = new Object();
    private ListView mListView;
    private PrinterDatabase mDb;
    private List<PrinterDatabase.Printer> results;
    private PrinterAdapter mPrinterAdapter;
    private Context mCtx;

    /***********************************************************************************************/
    @Override
    @SuppressLint("all")
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_add);
        overridePendingTransition(0,0);
        ActionBar actionBar = getActionBar();
        actionBar.setDisplayHomeAsUpEnabled(true);
        mDb = new PrinterDatabase(this);
        mListView = findViewById(R.id.printerList);
        results = new ArrayList<>();
        mPrinterAdapter = new PrinterAdapter(this,results);
        mListView.setAdapter(mPrinterAdapter);
        mListView.setOnItemClickListener(listClick);
        mStoredList = mDb.getAllPrinters();
        mCtx = this;

        mUsbManager = (UsbManager) getSystemService(Context.USB_SERVICE);
        mBluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        mWifiManager = (WifiManager) getSystemService(Context.WIFI_SERVICE);
        if (mBluetoothManager != null) mBluetoothAdapter = mBluetoothManager.getAdapter();

        //Find USB Printers
        findUsbPrinters();

        //Add any Network Printers
        findNetPrinter();

        //Find already paired devices.
        findPairedBluetooth();

        //Register USB Receivers
        registerReceiver(mUsbReceiver,new IntentFilter(UsbManager.ACTION_USB_DEVICE_ATTACHED));
        registerReceiver(mUsbReceiver,new IntentFilter(UsbManager.ACTION_USB_DEVICE_DETACHED));

        //Register Network Receiver
        registerReceiver(mNetReceiver,new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION));

        mExpireThread = new Thread(ExpireThread);
        mExpireThread.start();
    }

    /***********************************************************************************************/
    @Override
    protected void onResume()
    {
        super.onResume();
        //Register BT Receivers
        registerReceiver(mBTReceiver, new IntentFilter(BluetoothDevice.ACTION_FOUND));
        registerReceiver(mBTReceiver, new IntentFilter(BluetoothAdapter.ACTION_DISCOVERY_FINISHED));
        registerReceiver(mBTReceiver, new IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED));
        if (mBluetoothAdapter != null && !mBluetoothAdapter.isDiscovering()) mBluetoothAdapter.startDiscovery();
        mStoredList = mDb.getAllPrinters();
        findPairedBluetooth();
    }

    /***********************************************************************************************/
    @Override
    protected void onPause()
    {
        super.onPause();
        unregisterReceiver(mBTReceiver);
        if (mBluetoothAdapter != null && mBluetoothAdapter.isDiscovering()) mBluetoothAdapter.cancelDiscovery();
    }

    /***********************************************************************************************/
    @Override
    protected void onDestroy()
    {
        try
        {
            if (DEBUG) Log.d(TAG, "onDestroy()");
            bQuit = true;
            bNetQuit = true;
            unregisterReceiver(mUsbReceiver);
            unregisterReceiver(mNetReceiver);
            mNetworkThread.join();
            mExpireThread.join();
        }catch (Exception e) {};
        super.onDestroy();
        mDb.close();
    }
    /***********************************************************************************************/
    @Override
    public void onBackPressed()
    {
        super.onBackPressed();
        finish();
        overridePendingTransition(0,0);
    }

    /***********************************************************************************************/
    @Override
    public boolean onMenuItemSelected(int featureId, @NonNull MenuItem item)
    {
        switch (item.getItemId())
        {
            case android.R.id.home:
                finish();
                overridePendingTransition(0, 0);
                return true;
        }
        return super.onMenuItemSelected(featureId, item);
    }

    /***********************************************************************************************/
    private AdapterView.OnItemClickListener listClick = new AdapterView.OnItemClickListener()
    {
        @Override
        public void onItemClick(AdapterView<?> parent, View view, int position, long id)
        {
            Intent i = new Intent(mCtx,PrinterInfoActivity.class);
            i.putExtra("printer", Parcels.wrap(results.get(position)));
            startActivityForResult(i,0);
        }
    };

    /***********************************************************************************************/
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data)
    {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != Activity.RESULT_OK ) return;

        PrinterDatabase.Printer printer = Parcels.unwrap(data.getParcelableExtra("printer"));
        if (printer == null) { return; }
        mStoredList.add(printer);
        for (int i=0;i<mPrinterAdapter.getCount(); ++i)
        {
            if (mPrinterAdapter.getItem(i).mAddress.equals(printer.mAddress))
            {
                mPrinterAdapter.remove(mPrinterAdapter.getItem(i)); break;
            }
        }
        mPrinterAdapter.notifyDataSetChanged();
    }

    /***********************************************************************************************/
    private boolean alreadySelected(PrinterDatabase.Printer printer)
    {
        for (PrinterDatabase.Printer p : mStoredList)
        {
            if (p.mAddress.equals(printer.mAddress)) return true;
        }
        return false;
    }
    /***********************************************************************************************/
    private void findUsbPrinters()
    {
        if (mUsbManager != null)
        {
            HashMap<String, UsbDevice> deviceList = mUsbManager.getDeviceList();
            for (String deviceName : deviceList.keySet())
            {
                UsbDevice device = deviceList.get(deviceName);
                if (device.getInterfaceCount() > 0)
                {
                    if (device.getInterface(0).getInterfaceClass() == 0x07)
                    {
                        if (DEBUG) Log.v(TAG, "Found USB Printer :" + device.getProductName() + " VID:" + device.getVendorId() + " PID:" + device.getProductId() + " -> " + deviceName);
                        PrinterDatabase.Printer printer = new PrinterDatabase.Printer();
                        printer.mName = device.getProductName();
                        printer.mPrinter = device.getProductName();
                        printer.mAddress = device.getDeviceName();
                        printer.mType = "usb";
                        printer.mDPI = 203;
                        printer.mPrinterId = "usb:"+device.getDeviceName();
                        printer.mDescription = getString(R.string.usb);
                        if (alreadySelected(printer)) return;
                        mPrinterAdapter.add(printer);
                        mPrinterAdapter.notifyDataSetChanged();
                    }
                }
            }
        }
    }
    /**********************************************************************************************/
    private void findNetPrinter()
    {
         try
        {
            //Get Connection State
            if (mWifiManager.isWifiEnabled() && mWifiManager.getConnectionInfo().getBSSID() != null)
            {
                if (mNetworkThread != null) return;
                bNetQuit = false;
                mNetworkThread = new Thread(mNetworkSearch);
                mNetworkThread.start();
            } else
            {
                bNetQuit = true;
                if (mNetworkThread != null) mNetworkThread.join();
                mNetworkThread = null;
            }
        }catch (Exception e) {}
    }

    /**********************************************************************************************/
    private void findPairedBluetooth()
    {
        if (mBluetoothAdapter == null) return;
        Set<BluetoothDevice> pairedDevices = mBluetoothAdapter.getBondedDevices();
        for (BluetoothDevice device : pairedDevices)
        {
            BluetoothClass mBtClass = device.getBluetoothClass();
            if (DEBUG) Log.i(TAG, "Bonded : " + device.getAddress() + " COD: " + mBtClass.getDeviceClass() + " -> " + device.getName());
            if (!DEBUG)
            {
                if (mBtClass.getMajorDeviceClass() != BluetoothClass.Device.Major.IMAGING) continue;
                if (mBtClass.getDeviceClass() != 1664) continue;
            }

            PrinterDatabase.Printer printer = new PrinterDatabase.Printer();
            printer.mName = device.getName();
            printer.mDPI = 203;
            printer.mPrinter = device.getName();
            printer.mAddress = device.getAddress();
            printer.mType = "bt";
            printer.mPrinterId = "bt:"+device.getAddress();
            printer.mDescription = device.getAddress() + " - "+ getString(R.string.bluetooth);
            if (alreadySelected(printer)) continue;
            boolean bAdd = true;
            for (int i=0;i<mPrinterAdapter.getCount(); ++i)
            {
                if (mPrinterAdapter.getItem(i).mAddress.equals(printer.mAddress)) bAdd = false;
            }
            if (bAdd == true) mPrinterAdapter.add(printer);
        }
        mPrinterAdapter.notifyDataSetChanged();
    }

    /**********************************************************************************************/
    private final BroadcastReceiver mBTReceiver = new BroadcastReceiver()
    {
        public void onReceive(Context context, Intent intent)
        {
            String action = intent.getAction();

            // When discovery finds a device
            if (BluetoothDevice.ACTION_FOUND.equals(action))
            {
                // Get the BluetoothDevice object from the Intent
                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                BluetoothClass mBtClass = intent.getParcelableExtra(BluetoothDevice.EXTRA_CLASS);
                if (device.getName() == null) return;
                if (DEBUG) Log.i(TAG, device.getAddress() + " COD: " + mBtClass.getDeviceClass() + " -> " + device.getName());
                if (!DEBUG)
                {
                    if (mBtClass.getMajorDeviceClass() != BluetoothClass.Device.Major.IMAGING) return;
                    if (mBtClass.getDeviceClass() != 1664) return;
                }

                PrinterDatabase.Printer printer = new PrinterDatabase.Printer();
                printer.mName = device.getName();
                printer.mDPI = 203;
                printer.mPrinter = device.getName();
                printer.mAddress = device.getAddress();
                printer.mType = "bt";
                printer.mPrinterId = "bt:"+device.getAddress();
                printer.mDescription = device.getAddress() + " - "+ getString(R.string.bluetooth);
                if (alreadySelected(printer)) return;

                for (int i=0;i<mPrinterAdapter.getCount(); ++i)
                {
                    if (mPrinterAdapter.getItem(i).mAddress.equals(printer.mAddress))
                    {
                        mPrinterTimeouts.put(mPrinterAdapter.getItem(i), System.currentTimeMillis() + NET_TIMEOUT);
                        return;
                    }
                }
                mPrinterTimeouts.put(printer, System.currentTimeMillis() + BT_TIMEOUT);
                mPrinterAdapter.add(printer);
                mPrinterAdapter.notifyDataSetChanged();
                return;
            }

            if (BluetoothAdapter.ACTION_DISCOVERY_FINISHED.equals(action))
            {
                if (DEBUG) Log.i(TAG, "Restart Bluetooth Scan");
                if (mBluetoothAdapter != null) mBluetoothAdapter.startDiscovery();
            }

            if (BluetoothAdapter.ACTION_STATE_CHANGED.equals(action))
            {
                if (DEBUG) Log.i(TAG, "Bluetooth State Change");
                if(intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, -1) == BluetoothAdapter.STATE_OFF) return;
                if (mBluetoothAdapter != null) mBluetoothAdapter.startDiscovery();
            }
        }
    };
    /**********************************************************************************************/
    private final BroadcastReceiver mUsbReceiver = new BroadcastReceiver()
    {
        public void onReceive(Context context, Intent intent)
        {
            String action = intent.getAction();
            UsbDevice device = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
            if (UsbManager.ACTION_USB_DEVICE_DETACHED.equals(action))
            {
                if (DEBUG) Log.i(TAG,"Usb Device Detached : " + device.getDeviceName());
                for (int i=0;i<mPrinterAdapter.getCount(); ++i)
                {
                    if (mPrinterAdapter.getItem(i).mAddress.equals(device.getDeviceName()))
                    {
                        mPrinterAdapter.remove(mPrinterAdapter.getItem(i));
                        mPrinterAdapter.notifyDataSetChanged();
                        return;
                    }
                }
                return;
            }

            if (UsbManager.ACTION_USB_DEVICE_ATTACHED.equals(action))
            {
                if (DEBUG) Log.i(TAG,"Usb Device Attached : " + device.getDeviceName());
                findUsbPrinters();
                return;
            }
        }
    };

    /**********************************************************************************************/
    public BroadcastReceiver mNetReceiver = new BroadcastReceiver()
    {
        @Override
        public void onReceive(Context context, Intent intent)
        {
            findNetPrinter();
        }
    };
    /**********************************************************************************************/
    private final Runnable mNetworkSearch = new Runnable()
    {
        private SocketAddress mMulticastGroup = null;
        private MulticastSocket mMulticastSocket = null;

        @Override
        public void run()
        {
            if (DEBUG) Log.i(TAG,"Starting Network Search");
            try
            {
                mMulticastGroup = new InetSocketAddress(MULTICAST_ADDRESS, MULTICAST_PORT);
                mMulticastSocket = new MulticastSocket(MULTICAST_RECV);
                mMulticastSocket.setReuseAddress(true);

                //Loop Searching for Printers
                while (!bNetQuit)
                {
                    try
                    {
                        //Send Discovery Packet
                        if (DEBUG) Log.i(TAG,"Sending Network Search Request");
                        DatagramPacket mRequest = new DatagramPacket(DEVICE_DISCOVERY_REQUEST_PACKET, DEVICE_DISCOVERY_REQUEST_PACKET.length,mMulticastGroup);
                        mMulticastSocket.send(mRequest);

                        //Listen for replies.
                        DatagramPacket packet = new DatagramPacket(new byte[MAX_DATAGRAM_SIZE], MAX_DATAGRAM_SIZE);
                        mMulticastSocket.setSoTimeout(MULTICAST_TIMEOUT);

                        //Get Responses
                        long timerStarted = System.currentTimeMillis();
                        while (System.currentTimeMillis() - timerStarted < (MULTICAST_TIMEOUT))
                        {
                            mMulticastSocket.receive(packet);
                            NetworkDevice netDevice = new NetworkDevice(packet.getData());
                            if (DEBUG) Log.i(TAG,"Found Network Device:" + netDevice.getName() +" -> " + netDevice.getAddress() + ":" + netDevice.getPort());

                            final PrinterDatabase.Printer printer = new PrinterDatabase.Printer();
                            printer.mName = netDevice.getName();
                            printer.mDPI = 203;
                            printer.mPrinter = netDevice.getName();
                            printer.mAddress = netDevice.getAddress();
                            printer.mPort = netDevice.getPort();
                            printer.mType = "network";
                            printer.mPrinterId = "tcp:"+netDevice.getAddress();
                            printer.mDescription = netDevice.getAddress() + " - " + getString(R.string.network);
                            runOnUiThread(new Runnable()
                                {
                                    @Override
                                    public void run()
                                    {
                                        if (alreadySelected(printer)) return;
                                        for (int i = 0; i < mPrinterAdapter.getCount(); ++i)
                                        {
                                            if (mPrinterAdapter.getItem(i).mAddress.equals(printer.mAddress))
                                            {
                                                mPrinterTimeouts.put(mPrinterAdapter.getItem(i), System.currentTimeMillis() + NET_TIMEOUT);
                                                return;
                                            }
                                        }
                                        mPrinterTimeouts.put(printer, System.currentTimeMillis() + NET_TIMEOUT);
                                        mPrinterAdapter.add(printer);
                                        mPrinterAdapter.notifyDataSetChanged();
                                    }
                                });
                            }
                    } catch (SocketTimeoutException e) {}
                }
            }catch (Exception e)
            {
                e.printStackTrace();
            }
            if (mMulticastSocket != null) mMulticastSocket.close();
            if (DEBUG) Log.i(TAG,"Stopped Network Search");
        }
    };
    /**********************************************************************************************/
    private final Runnable ExpireThread = new Runnable()
    {
        @Override
        public void run()
        {
            if (DEBUG) Log.i(TAG,"Started Expiry Thread");
            while (!bQuit)
            {
                try
                {
                    Thread.sleep(1000);

                    //Remove any old devices
                    Iterator<Map.Entry<PrinterDatabase.Printer, Long>> printers = mPrinterTimeouts.entrySet().iterator();
                    while (printers.hasNext())
                    {
                        Map.Entry<PrinterDatabase.Printer, Long> entry = printers.next();
                        if (entry.getValue() < System.currentTimeMillis())
                        {
                            if (DEBUG) Log.i(TAG,"Printer Expiry :" + entry.getKey());
                            final PrinterDatabase.Printer p = entry.getKey();
                            runOnUiThread(new Runnable()
                            {
                                @Override
                                public void run()
                                {
                                    mPrinterAdapter.remove(p);
                                    mPrinterAdapter.notifyDataSetChanged();
                                }
                            });
                            printers.remove();
                        }
                    }
                }catch (Exception e) {}
            }
            if (DEBUG) Log.i(TAG,"Stopped Expiry Thread");
        }
    };

}
