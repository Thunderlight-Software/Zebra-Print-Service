package com.zebra.zebraprintservice.activities;

import androidx.annotation.NonNull;

import android.app.ActionBar;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import com.zebra.zebraprintservice.database.PrinterDatabase;
import com.zebra.zebraprintservice.R;
import com.zebra.zebraprintservice.service.ZebraPrintService;

import java.util.ArrayList;
import java.util.List;

public class SettingsActivity extends Activity
{
    private ZebraPrintService printService;
    private TextView mVersion;
    private TextView mServiceVersion;
    private TextView mPermissionTxt;
    private TextView mPermissionTitle;
    private final static int REQUEST_CODE_ASK_MULTIPLE_PERMISSIONS = 1;
    private final static int REQUEST_CODE_EXPORT = 2;
    private final static int REQUEST_CODE_IMPORT = 3;
    private PrinterDatabase mDb;

    /***********************************************************************************************/
    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        mDb = new PrinterDatabase(this);

        //Import Data if we need too.
        if (getIntent().getData() != null)
        {
            mDb.importData(getIntent().getData());
            mDb.close();
            finish();
            return;
        }

        printService = new ZebraPrintService();
        setContentView(R.layout.activity_settings);
        overridePendingTransition(0,0);
        ActionBar actionBar = getActionBar();
        actionBar.setDisplayHomeAsUpEnabled(true);

        mVersion = findViewById(R.id.version);
        mPermissionTitle = findViewById(R.id.permissionState);
        mPermissionTxt = findViewById(R.id.permissions);
        mServiceVersion = findViewById(R.id.serviceversion);
        mVersion.setText("V"+printService.getUtilsVersion());

        try {
            PackageInfo pInfo = getPackageManager().getPackageInfo(getPackageName(), 0);
            mServiceVersion.setText("V"+pInfo.versionName);
        } catch (PackageManager.NameNotFoundException ignored) {}

    }
    /***********************************************************************************************/
    @Override
    protected void onDestroy()
    {
        super.onDestroy();
        mDb.close();
    }

    /***********************************************************************************************/
    @Override
    public void onBackPressed()
    {
        super.onBackPressed();
        finish();
        overridePendingTransition(0,0);
    }

    /***********************************************************************************************/
    @Override
    public boolean onCreateOptionsMenu(Menu menu)
    {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.settings_menu, menu);
        return true;
    }

    /***********************************************************************************************/
    @Override
    public boolean onMenuItemSelected(int featureId, @NonNull MenuItem item)
    {
        switch (item.getItemId())
        {
            case android.R.id.home:
                finish();
                overridePendingTransition(0,0);
                return true;

            case R.id.exportPrinters:
            {
                Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
                intent.setType("text/xml");
                startActivityForResult(intent, REQUEST_CODE_EXPORT);
                overridePendingTransition(0, 0);
                return true;
            }

            case R.id.importPrinters:
            {
                Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                intent.setType("text/xml");
                startActivityForResult(intent, REQUEST_CODE_IMPORT);
                overridePendingTransition(0, 0);
                return true;
            }

            case R.id.removePrinters:
                mDb.deleteAll();
                return true;

        }
        return super.onOptionsItemSelected(item);
    }

    /***********************************************************************************************/
    @Override
    protected void onResume()
    {
        super.onResume();
        if(checkPermissions())
        {
            mPermissionTxt.setText(getString(R.string.granted));
            mPermissionTitle.setTextColor(getColor(R.color.text));
        }else{
            mPermissionTxt.setText(getString(R.string.enable_permissions));
            mPermissionTitle.setTextColor(getColor(R.color.alert));
        }
    }

    /***********************************************************************************************/
    private boolean checkPermissions()
    {
        try
        {
            PackageInfo packageInfo = getPackageManager().getPackageInfo(getPackageName(), PackageManager.GET_PERMISSIONS);
            String[] requestedPermissions = packageInfo.requestedPermissions;
            List<String> neededPermissions = new ArrayList<String>();
            if(requestedPermissions != null)
            {
                for (int i = 0; i < requestedPermissions.length; i++)
                {
                    if (checkSelfPermission(requestedPermissions[i]) == PackageManager.PERMISSION_DENIED) return false;
                }
            }

        }catch (Exception e) {}
        return true;
    }

    /***********************************************************************************************/
    public void onPermission(View v)
    {
        try
        {
            PackageInfo packageInfo = getPackageManager().getPackageInfo(getPackageName(), PackageManager.GET_PERMISSIONS);

            //Get Permissions
            String[] requestedPermissions = packageInfo.requestedPermissions;
            List<String> neededPermissions = new ArrayList<String>();

            if(requestedPermissions != null)
            {
                for (int i = 0; i < requestedPermissions.length; i++)
                {
                    if (checkSelfPermission(requestedPermissions[i]) == PackageManager.PERMISSION_DENIED) neededPermissions.add(requestedPermissions[i]);
                }
                if (neededPermissions.size() == 0 ) return;
                requestPermissions(neededPermissions.toArray(new String[neededPermissions.size()]), REQUEST_CODE_ASK_MULTIPLE_PERMISSIONS);
            }
        } catch (PackageManager.NameNotFoundException e) {}
    }

    /***********************************************************************************************/
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data)
    {
        super.onActivityResult(requestCode, resultCode, data);

        switch (requestCode)
        {
            case REQUEST_CODE_ASK_MULTIPLE_PERMISSIONS:
                break;

            case REQUEST_CODE_EXPORT:
                if (data != null)
                {
                    if (mDb.exportData(data.getData()))
                    {
                        Toast.makeText(this, R.string.export_ok,Toast.LENGTH_LONG).show();
                    }else{
                        Toast.makeText(this, R.string.export_failed,Toast.LENGTH_LONG).show();
                    }
                }
                break;

            case REQUEST_CODE_IMPORT:
                if (data != null)
                {
                    if (mDb.importData(data.getData()))
                    {
                        Toast.makeText(this, R.string.import_ok,Toast.LENGTH_LONG).show();
                    }else{
                        Toast.makeText(this, R.string.import_failed,Toast.LENGTH_LONG).show();
                    }
                }
                break;

        }
    }
}
