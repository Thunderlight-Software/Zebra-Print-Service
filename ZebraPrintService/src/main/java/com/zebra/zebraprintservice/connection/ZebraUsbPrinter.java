package com.zebra.zebraprintservice.connection;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.usb.UsbConstants;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbEndpoint;
import android.hardware.usb.UsbInterface;
import android.hardware.usb.UsbManager;
import android.os.Handler;
import android.util.Log;

import com.zebra.zebraprintservice.BuildConfig;
import com.zebra.zebraprintservice.database.PrinterDatabase;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.HashMap;

public class ZebraUsbPrinter extends PrinterConnection
{
    private static final String TAG = ZebraUsbPrinter.class.getSimpleName();
    private static final boolean DEBUG = BuildConfig.DEBUG & true;
    private static final String ACTION_USB_PERMISSION = "com.zebra.USB_PERMISSION.Printer";
    private static final int USB_TIMEOUT = 1000;
    private int MAX_DATA_TO_WRITE_TO_STREAM_AT_ONCE = 1024;
    private UsbManager mUsbManager;
    private UsbInterface mInterface = null;
    private UsbDeviceConnection mUsbConnection = null;
    private UsbDevice mDevice;
    private PrinterDatabase.Printer mPrinter;
    private PendingIntent mPermissionIntent;
    private ConnectionCallback mCurrentCallback = null;
    private Context mCtx;
    private Handler mHandler;
    private InputStream mInput = null;
    private OutputStream mOutput = null;

    /*********************************************************************************************/
    public ZebraUsbPrinter(Context ctx, PrinterDatabase.Printer printer)
    {
        mCtx = ctx;
        mPrinter = printer;
        mUsbManager =  (UsbManager) mCtx.getSystemService(Context.USB_SERVICE);
        mPermissionIntent = PendingIntent.getBroadcast(mCtx, 0, new Intent(ACTION_USB_PERMISSION), 0);
        mHandler = new Handler(mCtx.getMainLooper());

        PrinterDatabase mDb = new PrinterDatabase(mCtx);
        mDb.updatePrinter(printer);
        mDb.close();

        mCtx.registerReceiver(UsbReciever,new IntentFilter(ACTION_USB_PERMISSION));

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
                        if (printer.mAddress.equals(device.getDeviceName())) mDevice = device;
                    }
                }
            }
        }
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
        mCtx.unregisterReceiver(UsbReciever);
    }
    /*********************************************************************************************/
    @Override
    public boolean isAvailable()
    {
        return mDevice != null;
    }
    /*********************************************************************************************/
    @Override
    public void connect(final ConnectionCallback callback)
    {
        mCurrentCallback = callback;
        if (mDevice == null) { callback.onConnectFailed(); return; }
        if (mInput != null) { callback.onConnected(); return; }

        if (DEBUG) Log.d(TAG, "connect()");
        if (!mUsbManager.hasPermission(mDevice))
        {
            mHandler.post(new Runnable()
            {
                @Override
                public void run()
                {
                    mUsbManager.requestPermission(mDevice, mPermissionIntent);
                }
            });
        return;
        }

        if (DEBUG) Log.i(TAG,"Setting up USB");
        UsbEndpoint mEndpointBulkOut = null;
        UsbEndpoint mEndpointBulkIn = null;
        mInterface = mDevice.getInterface(0);
        for (int i = 0; i < mInterface.getEndpointCount(); i++)
        {
            UsbEndpoint ep = mInterface.getEndpoint(i);
            if (ep.getType() == UsbConstants.USB_ENDPOINT_XFER_BULK)
            {
                if (ep.getDirection() == UsbConstants.USB_DIR_OUT) mEndpointBulkOut = ep;
                if (ep.getDirection() == UsbConstants.USB_DIR_IN) mEndpointBulkIn = ep;
            }
        }

        if (mEndpointBulkOut == null || mEndpointBulkIn == null ) { disconnect(); callback.onConnectFailed(); return; }
        mUsbConnection = mUsbManager.openDevice(mDevice);
        mUsbConnection.claimInterface(mInterface,true);

        mOutput = new USBOutputStream(mUsbConnection, mEndpointBulkOut);
        mInput = new USBInputStream(mUsbConnection,mEndpointBulkIn);
        callback.onConnected();
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
            if (mUsbConnection != null) { mUsbConnection.releaseInterface(mInterface) ; mUsbConnection.close(); }
        }catch (Exception e) {
            if (DEBUG) e.printStackTrace();
        };

        mUsbConnection = null;
        mInterface = null;
        mInput = null;
        mOutput = null;
    }

    /*********************************************************************************************/
    private final BroadcastReceiver UsbReciever = new BroadcastReceiver()
    {
        @Override
        public void onReceive(Context context, Intent intent)
        {
            String action = intent.getAction();

            //Permission Request
            if (ACTION_USB_PERMISSION.equals(action))
            {
                if (DEBUG) Log.i(TAG,"Permission Response");
                if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false))
                {
                    connect(mCurrentCallback);
                }else{
                    disconnect();
                }
            }
        }
    };

    /*********************************************************************************************/
    private class USBInputStream extends InputStream
    {
        private static final int BUFF_LEN = 4096;
        private byte[] inBuffer = new byte[BUFF_LEN];
        private UsbEndpoint mInPipe;
        private UsbDeviceConnection mConnection;
        private int mBuffCount = 0;
        private int mInPtr = 0;
        private int mOutPtr = 0;

        public USBInputStream(UsbDeviceConnection connection, UsbEndpoint inPipe)
        {
            mConnection = connection;
            mInPipe = inPipe;
        }

        // -1 = End of Stream
        @Override
        public synchronized int read()
        {
            int iRes = -1;
            available();
            if (mBuffCount > 0)
            {
                iRes = inBuffer[mInPtr++] & 0xff;
                if (mInPtr == BUFF_LEN) mInPtr = 0;
                mBuffCount--;
            }
            return iRes;
        }

        @Override
        public int read(byte[] b)
        {
            return read(b,0,b.length);
        }

        @Override
        public synchronized int available()
        {
            if (mBuffCount != 0) return mBuffCount;
            byte[] tBuffer = new byte[4096];
            int iRes = read(tBuffer);
            for (int j=0; j <iRes; j++)
            {
                inBuffer[mOutPtr++] = tBuffer[j];
                if (mOutPtr == BUFF_LEN) mOutPtr = 0;
                mBuffCount++;
            }
            return mBuffCount;
        }

        @Override
        public synchronized int read(byte[] b, int off, int len)
        {
            if (mBuffCount > 0)
            {
                int bLen = mBuffCount < len ? mBuffCount : len;
                for (int iCnt = 0; iCnt < bLen; iCnt++)
                {
                    b[off + iCnt] = inBuffer[mInPtr++];
                    if (mInPtr == BUFF_LEN) mInPtr = 0;
                    mBuffCount--;
                }
                return bLen;
            }

            //Get some more data
            if (mInPipe == null) return -1;
            byte[] recvBuff = new byte[len];
            int iRes = mConnection.bulkTransfer(mInPipe, recvBuff, len, USB_TIMEOUT);
            if (iRes == -1) return -1;
            for (int iCnt = 0; iCnt < iRes; iCnt++) b[off + iCnt] = recvBuff[iCnt];
            return iRes;
        }
    }

    /*********************************************************************************************/
    private class USBOutputStream extends OutputStream
    {
        private static final int BUFF_LEN = 64;
        private UsbEndpoint mOutPipe;
        private UsbDeviceConnection mConnection;
        private byte[] outBuffer = new byte[BUFF_LEN];
        private int pIn = 0;

        public USBOutputStream(UsbDeviceConnection connection, UsbEndpoint outputPipe)
        {
            mConnection = connection;
            mOutPipe = outputPipe;
        }

        @Override
        public synchronized void write(int b)
        {
            outBuffer[pIn++] = (byte) b;
            if (pIn == BUFF_LEN) flush();
        }

        @Override
        public synchronized void flush()
        {
            if (pIn == 0) return;
            mConnection.bulkTransfer(mOutPipe,outBuffer,pIn,USB_TIMEOUT);
            pIn = 0;
        }

        @Override
        public void close()
        {
            flush();
        }
    }
}
