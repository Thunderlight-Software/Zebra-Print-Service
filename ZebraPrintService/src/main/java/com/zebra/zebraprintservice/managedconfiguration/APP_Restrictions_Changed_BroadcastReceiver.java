package com.zebra.zebraprintservice.managedconfiguration;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.RestrictionsManager;
import android.os.Bundle;
import android.os.Parcelable;
import android.util.Log;

public class APP_Restrictions_Changed_BroadcastReceiver extends BroadcastReceiver {

    private final static String TAG = "ZebraPrintService";

    @Override
    public void onReceive(Context context, Intent intent) {
        RestrictionsManager myRestrictionsMgr =
                (RestrictionsManager) context.getApplicationContext().getSystemService(Context.RESTRICTIONS_SERVICE);
        Bundle appRestrictions = myRestrictionsMgr.getApplicationRestrictions();
        Log.d(TAG, "Managed config" + appRestrictions.toString());

        Parcelable[] printers_lists = appRestrictions.getParcelableArray("printers_list");
        if(printers_lists != null && printers_lists.length > 0)
        {
            for(int i = 0; i < printers_lists.length; i++)
            {
                Bundle printer = (Bundle)printers_lists[i];
                Log.d(TAG, "********************************************************************");
                Log.d(TAG, "Managed config: Printer description (" + i + "/" + printers_lists.length  +")");
                Log.d(TAG, "Managed config, name: " + appRestrictions.getString("name"));
                Log.d(TAG, "Managed config, address: " + appRestrictions.getString("address"));
                Log.d(TAG, "Managed config, port: " + appRestrictions.getString("port"));
                Log.d(TAG, "Managed config, connexion: " + appRestrictions.getString("connexion"));
                Log.d(TAG, "Managed config, type: " + appRestrictions.getString("type"));
            }
        }
    }
}
