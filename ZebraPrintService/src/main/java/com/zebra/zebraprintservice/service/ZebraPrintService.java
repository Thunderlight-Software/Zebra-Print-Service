package com.zebra.zebraprintservice.service;

import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.RestrictionsManager;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.os.Parcelable;
import android.print.PrinterId;
import android.printservice.PrintJob;
import android.printservice.PrintService;
import android.printservice.PrinterDiscoverySession;
import android.util.Log;

import androidx.annotation.Nullable;

import com.zebra.zebraprintservice.BuildConfig;
import com.zebra.zebraprintservice.connection.PrinterConnection;
import com.zebra.zebraprintservice.connection.ZebraBluetoothPrinter;
import com.zebra.zebraprintservice.connection.ZebraFilePrinter;
import com.zebra.zebraprintservice.connection.ZebraNetworkPrinter;
import com.zebra.zebraprintservice.connection.ZebraUsbPrinter;
import com.zebra.zebraprintservice.database.PrinterDatabase;
import com.zebra.zebraprintservice.managedconfiguration.APP_Restrictions_Changed_BroadcastReceiver;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

@SuppressWarnings("JavaJniMissingFunction")
public class ZebraPrintService extends PrintService
{
    private static final String TAG = ZebraPrintService.class.getSimpleName();
    private static final boolean DEBUG = BuildConfig.DEBUG & true;
    private HashMap<PrinterId, ZebraPrinter> mPrinters = new HashMap<>();
    public static native String getUtilsVersion();
    public static native String createBitmapZPL(Bitmap bitmap);
    public static native String createBitmapCPC(Bitmap bitmap);
    private APP_Restrictions_Changed_BroadcastReceiver app_restrictions_changed_broadcastReceiver = new APP_Restrictions_Changed_BroadcastReceiver();

    static {
        System.loadLibrary("ZebraUtils");
    }

    /**********************************************************************************************/
    @Override
    public void onCreate()
    {
        if (DEBUG) Log.d(TAG, "onCreate() ");
        super.onCreate();
        readConfigFromManagedConfiguration();
    }

    /**********************************************************************************************/
    @Override
    public void onDestroy()
    {
        if (DEBUG) Log.d(TAG, "onDestroy()");
        Iterator<Map.Entry<PrinterId,ZebraPrinter>> printers = mPrinters.entrySet().iterator();
        while (printers.hasNext())
        {
            Map.Entry<PrinterId,ZebraPrinter> entry = printers.next();
            entry.getValue().destroy();
        }
        mPrinters.clear();
        super.onDestroy();
    }

    /**********************************************************************************************/
    @Override
    protected void onConnected()
    {
        if (DEBUG) Log.d(TAG, "onConnected()");
        super.onConnected();
        registerManagedConfigurationChangeBroadcastReceiver();
    }

    /**********************************************************************************************/
    @Override
    protected void onDisconnected()
    {
        if (DEBUG) Log.d(TAG, "onDisconnected()");
        super.onDisconnected();
        unregisterManagedConfigurationChangeBroadcastReceiver();
    }

    /**********************************************************************************************/
    @Nullable
    @Override
    protected PrinterDiscoverySession onCreatePrinterDiscoverySession()
    {
        if (DEBUG) Log.d(TAG, "onCreatePrinterDiscoverySession");
        return new ZebraDiscoverySession(this);
    }

    /**********************************************************************************************/
    @Override
    protected void onRequestCancelPrintJob(PrintJob printJob)
    {
        if (DEBUG) Log.d(TAG, "onRequestCancelPrintJob()");
        ZebraPrinter printer = getPrinter(printJob.getInfo().getPrinterId(),null);
        if (printer == null) { printJob.cancel(); return; }
        printer.cancelJob(printJob.getId());
    }

    /**********************************************************************************************/
    @Override
    protected void onPrintJobQueued(PrintJob printJob)
    {
        if (DEBUG) Log.d(TAG, "onPrintJobQueued()");
        ZebraPrinter printer = getPrinter(printJob.getInfo().getPrinterId(),null);
        if (printer == null) return;
        printer.addJob(printJob);
    }

    /**********************************************************************************************/
    public ZebraPrinter getPrinter(PrinterId printerId, ZebraDiscoverySession session)
    {
        ZebraPrinter result = null;
        if( mPrinters.get(printerId) != null) return  mPrinters.get(printerId);
        PrinterDatabase mDb = new PrinterDatabase(this);
        ArrayList<PrinterDatabase.Printer> printers = mDb.getAllPrinters();
        for (PrinterDatabase.Printer printer : printers)
        {
            if (printer.mPrinterId.equals(printerId.getLocalId()))
            {
                PrinterConnection connection = null;
                if (printer.mType.equals("bt")) connection = new ZebraBluetoothPrinter(this, printer);
                if (printer.mType.equals("network")) connection = new ZebraNetworkPrinter(this, printer);
                if (printer.mType.equals("usb")) connection = new ZebraUsbPrinter(this, printer);
                if (printer.mType.equals("file")) connection = new ZebraFilePrinter(this, printer);

                if (connection != null)
                {
                    if (DEBUG) Log.i(TAG,"Create Printer Resource:" + printerId.getLocalId());
                    result = new ZebraPrinter(this, session, printerId, connection);
                    mPrinters.put(printerId, result);
                }
            }
        }
        mDb.close();
        return result;
    }

    /**********************************************************************************************/
    private void readConfigFromManagedConfiguration() {
        Log.d(TAG, "Managed config");
        RestrictionsManager myRestrictionsMgr =
                (RestrictionsManager) getApplicationContext().getSystemService(Context.RESTRICTIONS_SERVICE);
        Bundle appRestrictions = myRestrictionsMgr.getApplicationRestrictions();
        Log.d(TAG, "Managed config" + appRestrictions.toString());

        Parcelable[] printers_lists = appRestrictions.getParcelableArray("printers_list");
        if(printers_lists != null && printers_lists.length > 0)
        {
            // The managed config contains a printer list
            // Get the database object
            PrinterDatabase mDb = new PrinterDatabase(this);

            // Remove all printers from database
            mDb.deleteAll();

            for(int i = 0; i < printers_lists.length; i++)
            {
                Bundle printer = (Bundle)printers_lists[i];
                // Log some data for debugging purposes
                Log.d(TAG, "********************************************************************");
                Log.d(TAG, "Managed config: Printer description (" + i + "/" + printers_lists.length  +")");
                Log.d(TAG, "Managed config, name: " + printer.getString("name"));
                Log.d(TAG, "Managed config, address: " + printer.getString("address"));
                Log.d(TAG, "Managed config, description: " + printer.getString("description"));
                Log.d(TAG, "Managed config, type: " + printer.getString("type"));
                Log.d(TAG, "Managed config, address: " + printer.getString("address"));
                Log.d(TAG, "Managed config, port: " + printer.getInt("port"));
                Log.d(TAG, "Managed config, dpi: " + printer.getInt("dpi"));
                Log.d(TAG, "Managed config, label size unit: " + printer.getString("unit"));
                Log.d(TAG, "Managed config, width: " + printer.getString("width"));
                Log.d(TAG, "Managed config, height: " + printer.getString("height"));

                // Create a Database Printer object
                PrinterDatabase.Printer dbPrinter = new PrinterDatabase.Printer();
                dbPrinter.mName = printer.getString("name");
                dbPrinter.mAddress = printer.getString("address");
                dbPrinter.mDescription = printer.getString("description");
                dbPrinter.mType = printer.getString("type");
                dbPrinter.mAddress = printer.getString("address");
                if(dbPrinter.mType.equalsIgnoreCase("network"))
                {
                    // Fill the port only for networked printers
                    dbPrinter.mPort = printer.getInt("port");
                }
                else
                {
                    // Leave to zero for non networked printers
                    dbPrinter.mPort = 0;
                }
                dbPrinter.mDPI = printer.getInt("dpi");
                String labelSizeUnit = printer.getString("unit");

                // Retrieve width and height as float values
                float fWidth = Float.parseFloat(printer.getString("width"));
                float fHeight = Float.parseFloat(printer.getString("height"));

                if(labelSizeUnit.equalsIgnoreCase("inches"))
                {
                    dbPrinter.mWidth = (int)(fWidth * dbPrinter.mDPI);
                    dbPrinter.mHeight = (int)(fHeight * dbPrinter.mDPI);
                }
                else
                {
                    dbPrinter.mWidth = (int)(fWidth * 0.393701f * dbPrinter.mDPI);
                    dbPrinter.mHeight = (int)(fHeight * 0.393701f * dbPrinter.mDPI);
                }

                switch(dbPrinter.mType)
                {
                    case "network":
                        dbPrinter.mPrinterId = "tcp:" + dbPrinter.mAddress;
                        break;
                    case "bt":
                        dbPrinter.mPrinterId = "bt:" + dbPrinter.mAddress;
                        break;
                    case "usb":
                        dbPrinter.mPrinterId = "usb:" + dbPrinter.mName;
                        break;
                }
                dbPrinter.mPrinter = dbPrinter.mName;
                dbPrinter.mTimeStamp = new Date();

                // Add the printer to the database
                mDb.insertPrinter(dbPrinter);
            }
            mDb.close();
        }
    }

    private void registerManagedConfigurationChangeBroadcastReceiver()
    {
        IntentFilter restrictionFilter = new IntentFilter(Intent.ACTION_APPLICATION_RESTRICTIONS_CHANGED);
        registerReceiver(app_restrictions_changed_broadcastReceiver, restrictionFilter);
    }

    private void unregisterManagedConfigurationChangeBroadcastReceiver()
    {
        unregisterReceiver(app_restrictions_changed_broadcastReceiver);
    }
}
