package com.zebra.zebraprintservice.connection;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.util.Log;

import com.zebra.zebraprintservice.BuildConfig;
import com.zebra.zebraprintservice.database.PrinterDatabase;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.UUID;

public class ZebraBluetoothPrinter extends PrinterConnection
{
    private static final String TAG = ZebraBluetoothPrinter.class.getSimpleName();
    private static final boolean DEBUG = BuildConfig.DEBUG & true;
    private static final UUID UUID_SPP = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");
    private int MAX_DATA_TO_WRITE_TO_STREAM_AT_ONCE = 1024;
    private PrinterDatabase.Printer mPrinter;
    private BluetoothAdapter mBluetoothAdapter = null;
    private BluetoothManager mBluetoothManager;
    private BluetoothDevice mDevice;
    private BluetoothSocket mSocket = null;
    private Context mCtx;
    private InputStream mInput = null;
    private OutputStream mOutput = null;

    /*********************************************************************************************/
    public ZebraBluetoothPrinter(Context ctx,PrinterDatabase.Printer printer)
    {
        mPrinter = printer;
        mCtx = ctx;
        mBluetoothManager = (BluetoothManager) mCtx.getSystemService(Context.BLUETOOTH_SERVICE);
        if (mBluetoothManager != null) mBluetoothAdapter = mBluetoothManager.getAdapter();
        if (mBluetoothAdapter != null) mDevice = mBluetoothAdapter.getRemoteDevice(printer.mAddress);
     }

    /*********************************************************************************************/
    @Override
    public String getName()
    {
        if (DEBUG) Log.d(TAG, "getName()");
        return mPrinter.mName;
    }

    /*********************************************************************************************/
    @Override
    public String getDescription()
    {
        if (DEBUG) Log.d(TAG, "getDescription()");
        return mPrinter.mDescription;
    }

    /*********************************************************************************************/
    @Override
    public InputStream getInputStream()
    {
        return mInput;
    }

    /*********************************************************************************************/
    @Override
    public void writeData(byte[] bData) throws IOException
    {
        int iSize = bData.length;
        int iOff = 0;
        while (iSize > 0)
        {
            int iLen = iSize > MAX_DATA_TO_WRITE_TO_STREAM_AT_ONCE ? MAX_DATA_TO_WRITE_TO_STREAM_AT_ONCE : iSize;
            mOutput.write(bData, iOff, iLen);
            mOutput.flush();
            try
            {
                Thread.sleep(50);
            }catch (Exception e) {}
            iOff += iLen;
            iSize -= iLen;
        }
    }

    /*********************************************************************************************/
    @Override
    public void destroy()
    {
        disconnect();
    }
    /*********************************************************************************************/
    @Override
    public PrinterDatabase.Printer getPrinter()
    {
        return mPrinter;
    }

    /*********************************************************************************************/
    @Override
    public boolean isAvailable()
    {
        return true;
    }

    /*********************************************************************************************/
    @Override
    public void connect(ConnectionCallback callback)
    {
        if (mSocket != null && mSocket.isConnected()) { callback.onConnected(); return; }
        if (DEBUG) Log.d(TAG, "connect() -> " + mDevice.getAddress());
        try
        {
            mSocket = mDevice.createRfcommSocketToServiceRecord(UUID_SPP);
            mSocket.connect();
            mInput = mSocket.getInputStream();
            mOutput = mSocket.getOutputStream();
            if (DEBUG) Log.i(TAG, "Connected");
            callback.onConnected();
        }catch (Exception e) {
            if (DEBUG) e.printStackTrace();
            callback.onConnectFailed();
        }
    }

    /*********************************************************************************************/
    @Override
    public void disconnect()
    {
        if (DEBUG) Log.d(TAG, "disconnect()");
        try
        {
            Thread.sleep(500);
        }catch (Exception e) {}
        try
        {
            if (mInput != null) mInput.close();
            if (mOutput != null) mOutput.close();
            if (mSocket != null) mSocket.close();
        }catch (Exception e) {
            if (DEBUG) e.printStackTrace();
        }
        mInput = null;
        mOutput = null;
        mSocket = null;
    }

}
