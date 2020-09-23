package com.zebra.zebraprintservice;

import android.util.Log;

public class NetworkDevice
{
    private static final String TAG = NetworkDevice.class.getSimpleName();
    private static final boolean DEBUG = BuildConfig.DEBUG & true;

    private String mProduct;
    private String mName;
    private String mAddress;
    private int mPort = 9100;

    public NetworkDevice(byte [] bPacket)
    {
        mProduct = parseString(bPacket,12,20);
        mAddress = parseAddress(bPacket,72,4);
        mName = parseString(bPacket,84,25);
        if ((mProduct.startsWith("QL")) ||
                (mProduct.startsWith("RW")) ||
                (mProduct.startsWith("MZ")) ||
                (mProduct.startsWith("P4T")) ||
                (mProduct.startsWith("MQ")) ||
                (mProduct.startsWith("MU"))) mPort = 9300;

        if(DEBUG) Log.i(TAG,"Found Network Printer :" + mProduct + " -> " + mAddress + ":" + mPort);
        if(DEBUG) Log.i(TAG,"System Name:" + parseString(bPacket, 84, 25));
        if(DEBUG) Log.i(TAG,"Serial Number :" + parseString(bPacket, 60, 10));
    }
    /*********************************************************************************************/
    private String parseString(byte[] paramArrayOfByte, int paramInt1, int paramInt2)
    {
        int i = 0;
        for (int j = paramInt1; (j < paramInt1 + paramInt2) && (paramArrayOfByte[j] != 0); j++) {
            i++;
        }
        return new String(paramArrayOfByte, paramInt1, i);
    }
    /*********************************************************************************************/
    private String parseAddress(byte[] paramArrayOfByte, int paramInt1, int paramInt2)
    {
        StringBuffer localStringBuffer = new StringBuffer("");
        for (int i = paramInt1; i < paramInt1 + paramInt2; i++)
        {
            localStringBuffer.append(byte2int(paramArrayOfByte[i]));
            if (i + 1 != paramInt1 + paramInt2) {
                localStringBuffer.append(".");
            }
        }
        return localStringBuffer.toString();
    }
    /*********************************************************************************************/
    private int byte2int(byte paramByte)
    {
        int i = paramByte;
        if (i < 0) i += 256;
        return i;
    }

    /*********************************************************************************************/
    public String getName()
    {
        return mName;
    }

    /*********************************************************************************************/
    public String getAddress()
    {
        return mAddress;
    }

    /*********************************************************************************************/
    public int getPort()
    {
        return mPort;
    }


}
