package com.zebra.zebraprintservice.connection;

import android.content.Context;
import android.util.Log;

import com.zebra.zebraprintservice.BuildConfig;
import com.zebra.zebraprintservice.database.PrinterDatabase;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;

public class ZebraNetworkPrinter extends PrinterConnection
{
    private static final String TAG = ZebraNetworkPrinter.class.getSimpleName();
    private static final boolean DEBUG = BuildConfig.DEBUG & true;
    private int MAX_DATA_TO_WRITE_TO_STREAM_AT_ONCE = 1024;
    private Socket mConnection = null;
    private PrinterDatabase.Printer mPrinter;
    private Context mCtx;
    private DataInputStream mInput = null;
    private DataOutputStream mOutput = null;

    public ZebraNetworkPrinter(Context ctx, PrinterDatabase.Printer printer)
    {
        mPrinter = printer;
        mCtx = ctx;
        printer.mPort = 9100;
        PrinterDatabase mDb = new PrinterDatabase(mCtx);
        mDb.updatePrinter(printer);
        mDb.close();
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
    public PrinterDatabase.Printer getPrinter()
    {
        return mPrinter;
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
        mOutput.write(bData);
        mOutput.flush();
    }

    @Override
    public void writeData(byte[] bData, WriteDataCallback callback) throws IOException {
        int iSize = bData.length;
        int iOff = 0;
        while (iSize > 0)
        {
            int iLen = iSize > MAX_DATA_TO_WRITE_TO_STREAM_AT_ONCE ? MAX_DATA_TO_WRITE_TO_STREAM_AT_ONCE : iSize;
            mOutput.write(bData, iOff, iLen);
            mOutput.flush();
            if(callback != null)
                callback.onWriteData(iOff, iLen);
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
    public boolean isAvailable()
    {
        return true;
    }
    /*********************************************************************************************/
    @Override
    public void connect(ConnectionCallback callback)
    {
        if (mInput != null) { callback.onConnected(); return; }
        if (DEBUG) Log.d(TAG, "connect() ->" + mPrinter.mAddress + ":" + mPrinter.mPort);
        try
        {
            mConnection = new Socket( mPrinter.mAddress, mPrinter.mPort);
            mInput = new DataInputStream(mConnection.getInputStream());
            mOutput = new DataOutputStream(mConnection.getOutputStream());
            if (DEBUG) Log.i(TAG, "Connected");
            callback.onConnected();
        }catch (Exception e)
        {
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

        //Close Data Channels
        try
        {
            if (mInput != null) mInput.close();
            if (mOutput != null) mOutput.close();
        }catch (Exception e) {
            if (DEBUG) e.printStackTrace();
        };
        mInput = null;
        mOutput = null;

        //Close Socket
        try
        {
            if (mConnection != null) mConnection.close();
        }catch (Exception e) {
            if (DEBUG) e.printStackTrace();
        }
        mConnection = null;
    }

    /*********************************************************************************************/

}
