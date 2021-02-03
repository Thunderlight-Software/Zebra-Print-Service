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
        ManagedConfigurationHelper.readConfigFromManagedConfiguration(context);
    }
}
