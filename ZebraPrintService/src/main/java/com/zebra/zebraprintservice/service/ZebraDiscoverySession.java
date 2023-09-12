package com.zebra.zebraprintservice.service;

import android.app.PendingIntent;
import android.content.Intent;
import android.print.PrinterId;
import android.print.PrinterInfo;
import android.printservice.PrinterDiscoverySession;
import android.util.Log;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import androidx.annotation.NonNull;

import com.zebra.zebraprintservice.BuildConfig;
import com.zebra.zebraprintservice.R;
import com.zebra.zebraprintservice.activities.PrinterInfoActivity;
import com.zebra.zebraprintservice.database.PrinterDatabase;

import org.parceler.Parcels;

public class ZebraDiscoverySession extends PrinterDiscoverySession
{
    private static final String TAG = ZebraDiscoverySession.class.getSimpleName();
    private static final boolean DEBUG = BuildConfig.DEBUG & true;
    private ZebraPrintService mService;

    /**********************************************************************************************/
    public ZebraDiscoverySession(ZebraPrintService service)
    {
        mService = service;
    }

    /**********************************************************************************************/
    @Override
    public void onStartPrinterDiscovery(@NonNull List<PrinterId> priorityList)
    {
        if (DEBUG) Log.d(TAG, "onStartPrinterDiscovery() " + priorityList);
        PrinterDatabase mDb = new PrinterDatabase(mService);
        ArrayList<PrinterDatabase.Printer> printers = mDb.getAllPrinters();

        //Remove Any Printers not in Database
        List<PrinterInfo> mCurrentList = getPrinters();


        if (DEBUG) Log.d(TAG, "Printers in stystem:" + mCurrentList.size());
        for (PrinterInfo printer : mCurrentList)
        {
            if (DEBUG) Log.d(TAG, "------------------------------------------");
            if (DEBUG) Log.d(TAG, "Printers ID:" + printer.getId());
            boolean bFound = false;
            for (PrinterDatabase.Printer stored : printers)
            {
                if (DEBUG) Log.d(TAG, "Stored ID:" + stored.mPrinterId);
                if (stored.mPrinterId.equals(printer.getId()))
                {
                    bFound = true;
                    if (DEBUG) Log.d(TAG, "Found ID:" + stored.mPrinterId);
                    mDb.replacePrinter(printer, stored);
                }
            }
            if (DEBUG) Log.d(TAG, "------------------------------------------");
            if (bFound == false)
                removePrinters(Collections.singletonList(printer.getId()));
        }

        // Remove all existing printers
        // TODO: check why actual printers get a pb when re discovered
        //List<PrinterInfo> printersList = getPrinters();
        //ArrayList<PrinterId> printersIds = new ArrayList<>(printersList.size());
        //for(PrinterInfo printInfo : printersList)
        //    printersIds.add(printInfo.getId());
        //removePrinters(printersIds);

        //Add Printers from database
        int iReqCode =1;
        for (PrinterDatabase.Printer printer : printers)
        {
            PrinterId printerId= mService.generatePrinterId(printer.mPrinterId);
            ZebraPrinter print = mService.getPrinter(printerId,this);
            if (print != null)
            {
                Intent i = new Intent(mService,PrinterInfoActivity.class);
                i.putExtra("printer", Parcels.wrap(printer));
                PendingIntent pi = PendingIntent.getActivity(mService,iReqCode,i,PendingIntent.FLAG_CANCEL_CURRENT | PendingIntent.FLAG_MUTABLE);
                if (DEBUG) Log.i(TAG,"Adding Printer:" + printerId.getLocalId());
                PrinterInfo.Builder builder = new PrinterInfo.Builder(printerId, printer.mName,print.isAvailable() ? PrinterInfo.STATUS_IDLE : PrinterInfo.STATUS_UNAVAILABLE)
                        .setIconResourceId(R.drawable.ic_printer)
                        .setDescription(printer.mDescription)
                        .setInfoIntent(pi);
                addPrinters(Collections.singletonList(builder.build()));
                iReqCode++;
            }
        }
        mDb.close();
    }

    /**********************************************************************************************/
    @Override
    public void onStopPrinterDiscovery()
    {
        if (DEBUG) Log.d(TAG, "onStopPrinterDiscovery()");
    }

    /**********************************************************************************************/
    @Override
    public void onValidatePrinters(@NonNull List<PrinterId> printerIds)
    {
        if (DEBUG) Log.d(TAG, "onValidatePrinters() " + printerIds);
    }

    /**********************************************************************************************/
    @Override
    public void onStartPrinterStateTracking(@NonNull PrinterId printerId)
    {
        if (DEBUG) Log.d(TAG, "onStartPrinterStateTracking() " + printerId.getLocalId());
        ZebraPrinter localPrinter = mService.getPrinter(printerId,this);
        if (localPrinter == null) return;
        localPrinter.startTracking();
    }

    /**********************************************************************************************/
    @Override
    public void onStopPrinterStateTracking(@NonNull PrinterId printerId)
    {
        if (DEBUG) Log.d(TAG, "onStopPrinterStateTracking() " + printerId.getLocalId());
        ZebraPrinter localPrinter = mService.getPrinter(printerId,this);
        if (localPrinter != null) localPrinter.stopTracking();
    }

    /**********************************************************************************************/
    @Override
    public void onDestroy()
    {
       if (DEBUG) Log.d(TAG, "onDestroy()");
    }

}
