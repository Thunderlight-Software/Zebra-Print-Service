package com.zebra.zebraprintservice;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import com.zebra.zebraprintservice.database.PrinterDatabase;

import java.util.List;

public class PrinterAdapter extends ArrayAdapter<PrinterDatabase.Printer>
{
    private Context mCtx = null;
    private static class ViewHolder
    {
        TextView mBLEName;
        TextView mBLEMac;
    }

    /*********************************************************************************************************/
    public PrinterAdapter(Context context, List<PrinterDatabase.Printer> Results)
    {
        super(context,R.layout.listview_printer, Results);
        mCtx = context;
    }
    /*********************************************************************************************************/
    public View getView(int position, View convertView, ViewGroup parent)
    {
        //Get Current Item
        PrinterDatabase.Printer i = getItem(position);

        ViewHolder viewHolder;

        if (convertView == null)
        {
            viewHolder = new ViewHolder();
            LayoutInflater inflater = LayoutInflater.from(getContext());
            convertView = inflater.inflate(R.layout.listview_printer, parent, false);
            viewHolder.mBLEName = (TextView) convertView.findViewById(R.id.name);
            viewHolder.mBLEMac = (TextView) convertView.findViewById(R.id.description);
            convertView.setTag(viewHolder);
        }else{
            viewHolder = (ViewHolder) convertView.getTag();
        }

        if (i.mName != null)
        {
            viewHolder.mBLEName.setText(i.mName);
            viewHolder.mBLEMac.setText(i.mDescription);
        }else{
            viewHolder.mBLEName.setText(R.string.unknown_device);
            viewHolder.mBLEMac.setText(i.mDescription);
        }
        return convertView;
    }
}
