package com.zebra.zebraprintservice.activities;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputFilter;
import android.text.Spanned;
import android.text.TextWatcher;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;

import com.zebra.zebraprintservice.BuildConfig;
import com.zebra.zebraprintservice.R;
import com.zebra.zebraprintservice.database.PrinterDatabase;

import org.parceler.Parcels;

public class PrinterInfoActivity extends Activity
{
    private static final String TAG = PrinterInfoActivity.class.getSimpleName();
    private static final boolean DEBUG = BuildConfig.DEBUG & true;
    private PrinterDatabase mDb;
    private PrinterDatabase.Printer mSelected = null;
    private EditText mName = null;
    private EditText mWidth = null;
    private EditText mHeight = null;
    private Button mSubmit = null;
    private Button mCancel = null;
    private double MAX_SIZE = 9.99;
    private double MIN_SIZE = .50;

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_printer_info);
        overridePendingTransition(0,0);
        mName = findViewById(R.id.name);
        mWidth = findViewById(R.id.width);
        mHeight = findViewById(R.id.height);
        mCancel = findViewById(R.id.cancel_button);
        mSubmit = findViewById(R.id.submit_button);
        mDb = new PrinterDatabase(this);

        //Get Printer Details
        mSelected = Parcels.unwrap(getIntent().getParcelableExtra("printer"));
        if (mSelected == null) { finish(); return; }

        //Found printer to display details.
        if (DEBUG) Log.i(TAG,"Printer Info : " + mSelected.mPrinter);

        //Submit Button Handler
        mSubmit.setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View v)
            {
                mSelected.mName = mName.getText().toString();
                mSelected.mWidth = (int)(Double.parseDouble(mWidth.getText().toString()) * (double)mSelected.mDPI);
                mSelected.mHeight = (int)(Double.parseDouble(mHeight.getText().toString()) * (double)mSelected.mDPI);
                if (mSelected.mWidth == 0 || mSelected.mHeight == 0) return;

                mDb.replacePrinter(mSelected);
                Intent i = new Intent();
                i.putExtra("printer", Parcels.wrap(mSelected));
                setResult(Activity.RESULT_OK,i);
                finish();
            }
        });

        // Cancel Button Handler
        mCancel.setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View v)
            {
                setResult(Activity.RESULT_CANCELED);
                finish();
            }
        });

        // Name Filters
        mName.setFilters(new InputFilter[]{new InputFilter.LengthFilter(32) });
        mName.addTextChangedListener(mWatcher);

        mWidth.setFilters(new InputFilter[] { new InputFilterMinMax(0,MAX_SIZE)});
        mHeight.setFilters(new InputFilter[] { new InputFilterMinMax(0,MAX_SIZE)});

        mWidth.setText(String.format("%.2f",((double)mSelected.mWidth / (double)mSelected.mDPI)));
        mHeight.setText(String.format("%.2f",((double)mSelected.mHeight / (double)mSelected.mDPI)));
        mName.setText(""); mName.append(mSelected.mName);
    }

    @Override
    protected void onDestroy()
    {
        super.onDestroy();
        overridePendingTransition(0,0);
        mDb.close();
    }

    /***********************************************************************************************/
    public class InputFilterMinMax implements InputFilter
    {
        private double min, max;

        public InputFilterMinMax(double min, double max)
        {
            this.min = Math.min(min, max);
            this.max = Math.max(min, max);
        }

        @Override
        public CharSequence filter(CharSequence source, int i, int i2, Spanned spanned, int i3, int i4)
        {
            try
            {
                int dotPos = -1;
                int len = spanned.length();
                for (int decimalsI = 0; decimalsI < len; decimalsI++)
                {
                    char c = spanned.charAt(decimalsI);
                    if (c == '.' || c == ',')
                    {
                        dotPos = decimalsI;
                        break;
                    }
                }

                if (dotPos >= 0)
                {
                    // protects against many dots
                    if (source.equals(".") || source.equals(",")) return "";
                    if (i4 > dotPos && len - dotPos > 2) return "";
                }

                //Not a valid number
                double input = Double.parseDouble(spanned.toString() + source.toString());
                if (isInRange(min, max, input)) return null;

            } catch (NumberFormatException e) { }
            return "";
        }

        private boolean isInRange(double min, double max, double value)
        {
            return value >= min && value <= max;
        }
    }

    /***********************************************************************************************/
    private TextWatcher mWatcher = new TextWatcher()
    {
        @Override
        public void beforeTextChanged(CharSequence s, int start, int count, int after)
        {
        }

        @Override
        public void onTextChanged(CharSequence s, int start, int before, int count)
        {
        }

        @Override
        public void afterTextChanged(Editable s)
        {
            //Name Validation
            if(mName.getText().toString().trim().length()==0)
            {
                mSubmit.setEnabled(false);
                return;
            }
            mSubmit.setEnabled(true);
        }
    };

}
