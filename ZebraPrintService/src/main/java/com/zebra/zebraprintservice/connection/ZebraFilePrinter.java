package com.zebra.zebraprintservice.connection;

import android.content.Context;
import android.util.Log;

import com.zebra.zebraprintservice.BuildConfig;
import com.zebra.zebraprintservice.database.PrinterDatabase;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

public class ZebraFilePrinter extends PrinterConnection
{
    private static final String TAG = ZebraNetworkPrinter.class.getSimpleName();
    private static final boolean DEBUG = BuildConfig.DEBUG & true;
    private int MAX_DATA_TO_WRITE_TO_STREAM_AT_ONCE = 1024;
    private PrinterDatabase.Printer mPrinter;
    private FileOutputStream mOutput = null;
    private ZebraInputStream mInput = null;
    private Context mCtx;

    /*********************************************************************************************/
    public ZebraFilePrinter(Context ctx, PrinterDatabase.Printer printer)
    {
        if (DEBUG) Log.i(TAG,"Creating File Printer -> " + printer.mWidth + "x" + printer.mHeight + " DPI:" + printer.mDPI);
        mPrinter = printer;
        mCtx = ctx;
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
        try
        {
            if (mOutput != null ) { callback.onConnected(); return; }
            File file = new File(mCtx.getExternalCacheDir(), mPrinter.mAddress);
            if (!file.exists()) file.createNewFile();
            file.setReadable(true, false);
            if(DEBUG) Log.i(TAG,"Creating Output File:" + file.getAbsolutePath());
            mOutput = new FileOutputStream(file);
            mInput = new ZebraInputStream();
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
        try
        {
            if (mOutput != null) mOutput.close();
            if (mInput != null) mInput.close();
            mInput = null;
            mOutput = null;
        }catch (Exception e) {
            if (DEBUG) e.printStackTrace();
        }
        mOutput = null;
    }

    /*********************************************************************************************/
    @Override
    public void destroy()
    {
        disconnect();
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
         //Check if we need to respond to command
        String sData = new String(bData,0,bData.length < 40 ? bData.length : 40);
        if (sData.contains("device.languages")) { mInput.send(String.valueOf(mPrinter.mLanguage).getBytes()); return; }
        if (sData.contains("ezpl.print_width")) { mInput.send(String.valueOf(mPrinter.mWidth).getBytes()); return; }
        if (sData.contains("zpl.label_length")) { mInput.send(String.valueOf(mPrinter.mHeight).getBytes()); return; }
        if (sData.contains("head.resolution.in_dpi")) { mInput.send(String.valueOf(mPrinter.mDPI).getBytes()); return; }
        if (sData.startsWith("~HS")) { mInput.send(new byte[] { 0x02 }); mInput.send("0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0".getBytes()); return; };

        mOutput.write(bData);
        mOutput.flush();
    }

    @Override
    public void writeData(byte[] bData, WriteDataCallback callback) throws IOException {

        //Check if we need to respond to command
        String sData = new String(bData,0,bData.length < 40 ? bData.length : 40);
        if (sData.contains("device.languages")) { mInput.send(String.valueOf(mPrinter.mLanguage).getBytes()); return; }
        if (sData.contains("ezpl.print_width")) { mInput.send(String.valueOf(mPrinter.mWidth).getBytes()); return; }
        if (sData.contains("zpl.label_length")) { mInput.send(String.valueOf(mPrinter.mHeight).getBytes()); return; }
        if (sData.contains("head.resolution.in_dpi")) { mInput.send(String.valueOf(mPrinter.mDPI).getBytes()); return; }
        if (sData.startsWith("~HS")) { mInput.send(new byte[] { 0x02 }); mInput.send("0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0".getBytes()); return; };


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
    private class ZebraInputStream extends InputStream
    {
        private static final int BUFF_LEN = 4096;
        private byte[] inBuffer = new byte[BUFF_LEN];
        private int mBuffCount = 0;
        private int mInPtr = 0;
        private int mOutPtr = 0;

        // -1 = End of Stream
        @Override
        public synchronized int read()
        {
            int iRes = -1;
            if (mBuffCount > 0)
            {
                iRes = inBuffer[mInPtr++] & 0xff;
                if (mInPtr == BUFF_LEN) mInPtr = 0;
                mBuffCount--;
            }
            return iRes;
        }

        public synchronized void send(byte[] b)
        {
            for (int j=0; j <b.length; j++)
            {
                inBuffer[mOutPtr++] = b[j];
                if (mOutPtr == BUFF_LEN) mOutPtr = 0;
                mBuffCount++;
            }
        }

        @Override
        public synchronized int available()
        {
            return mBuffCount;
        }
    }

}
