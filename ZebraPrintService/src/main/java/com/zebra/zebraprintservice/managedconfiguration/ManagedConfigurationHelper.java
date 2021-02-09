package com.zebra.zebraprintservice.managedconfiguration;

import android.content.Context;
import android.content.RestrictionsManager;
import android.os.Bundle;
import android.os.Looper;
import android.os.Parcelable;
import android.util.Log;

import com.zebra.zebraprintservice.database.PrinterDatabase;
import com.zebra.zebraprintservice.service.ZebraPrintService;

import java.util.Collection;
import java.util.Date;
import java.util.HashSet;

import androidx.annotation.Nullable;
import androidx.enterprise.feedback.KeyedAppState;
import androidx.enterprise.feedback.KeyedAppStatesCallback;
import androidx.enterprise.feedback.KeyedAppStatesReporter;

public class ManagedConfigurationHelper {

    private static final String TAG = ZebraPrintService.class.getSimpleName();

    /**********************************************************************************************/
    public static void readConfigFromManagedConfiguration(Context context) {
        Log.d(TAG, "Managed config");
        // Retrieve the restriction manager that contains the Managed Configuration data
        RestrictionsManager myRestrictionsMgr =
                (RestrictionsManager) context.getSystemService(Context.RESTRICTIONS_SERVICE);
        // Get the application restrictions bundle
        Bundle appRestrictions = myRestrictionsMgr.getApplicationRestrictions();
        Log.d(TAG, "Managed config" + appRestrictions.toString());

        if(appRestrictions != null)
        {
            Log.d(TAG, "Managed configuration bundle found.");
            // Get the Application Feedback Channel reporter
            KeyedAppStatesReporter reporter = KeyedAppStatesReporter.create(context.getApplicationContext());
            // Create the states collection that will be sent to feedback channel
            Collection states = new HashSet<>();

            // Try to retrieve the printer list from managed configuration bundle
            Parcelable[] printers_lists = appRestrictions.getParcelableArray("printers_list");
            if(printers_lists != null && printers_lists.length > 0)
            {
                Log.d(TAG, "Managed configuration bundle contains " + printers_lists.length + " printers descriptions.");

                // The managed config contains a printer list
                // Get the database object
                PrinterDatabase mDb = new PrinterDatabase(context);

                // Remove all printers from database
                mDb.deleteAll();

                // Store printer information to provide data inside the feedback channel message
                String fcDataPrinterInfo = "";

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

                    // Add information to feedback channel data message.
                    fcDataPrinterInfo += "(Printer " + (i+1) + "/" + printers_lists.length + "||"
                            + "||Name:" + dbPrinter.mName
                            + "||Description:" + dbPrinter.mDescription
                            + "||Type:" + dbPrinter.mType
                            + "||Address:" + dbPrinter.mAddress
                            + ") ";
                }
                mDb.close();
                // Report success to feedback channel
                states.add(KeyedAppState.builder()
                        .setKey("ADDPRINTER")
                        .setSeverity(KeyedAppState.SEVERITY_INFO)
                        .setMessage(printers_lists.length + " printer(s) added successfully to the database")
                        .setData(fcDataPrinterInfo)
                        .build());
            }
            else
            {
                // No printers found in the list
                states.add(KeyedAppState.builder()
                        .setKey("ADDPRINTER")
                        .setSeverity(KeyedAppState.SEVERITY_ERROR)
                        .setMessage("Unable to retrieve the printers definition from the managed configuration data. Check logcat for more information.")
                        .build());
            }
            // Send states to feedback channel
            if(states.size() > 0)
            {
                Log.d(TAG, "Sending state to feedback channel");
                reporter.setStates(states, new KeyedAppStatesCallback() {
                    @Override
                    public void onResult(int state, @Nullable Throwable throwable) {
                        Log.d(TAG, "reporter.setStates result: " + state);
                        if(throwable != null)
                        {
                            Log.d(TAG, "Error: " + throwable.getMessage());
                        }
                    }
                });
            }
        }
        else
        {
            // No app restriction bundle found
            // Do nothing
            Log.d(TAG, "No managed configuration bundle found.");
        }

    }
}
