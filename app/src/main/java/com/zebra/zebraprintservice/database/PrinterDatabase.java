package com.zebra.zebraprintservice.database;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.net.Uri;
import android.util.Xml;

import org.parceler.Parcel;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserFactory;
import org.xmlpull.v1.XmlSerializer;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.Date;

public class PrinterDatabase
{
    private static final String DB_NAME             = "Printers.db";
    private static final int DB_VERSION             = 5;
    private static final String DB_TABLE            = "Printers";
    private static final String KEY_PRINTERID       = "PrinterId";
    private static final String KEY_NAME            = "Name";
    private static final String KEY_PRINTER         = "Printer";
    private static final String KEY_DESCRIPTION     = "Description";
    private static final String KEY_TYPE            = "Type";
    private static final String KEY_ADDRESS         = "Address";
    private static final String KEY_DPI             = "DPI";
    private static final String KEY_WIDTH           = "Width";
    private static final String KEY_HEIGHT          = "Height";
    private static final String KEY_PORT            = "Port";
    private static final String KEY_MODIFIED        = "Modified";
    private static final String KEY_LANGUAGE        = "Language";

    private DatabaseHelper mDbHelper = null;
    private Context mCtx = null;

    // Printer Entry
    @Parcel
    public static class Printer
    {
        public String mPrinterId = "";
        public String mName = "";
        public String mPrinter = "";
        public String mDescription = "";
        public String mType = "";
        public String mAddress = "";
        public String mLanguage = "";
        public int mPort = 0;
        public int mDPI = 0;
        public int mWidth = 0;
        public int mHeight = 0;
        public Date mTimeStamp = new Date();
    }

    /**********************************************************************************************/
    public PrinterDatabase(Context ctx)
    {
        mCtx = ctx;
        mDbHelper = new DatabaseHelper(mCtx.getApplicationContext());
    }

    /**********************************************************************************************/
    public void close()
    {
        mDbHelper.close();
    }

    /**********************************************************************************************/
    private Printer getRecord(Cursor c)
    {
        if (c == null) return null;
        Printer mItem = new Printer();
        mItem.mPrinterId = c.getString(c.getColumnIndex(KEY_PRINTERID));
        mItem.mName = c.getString(c.getColumnIndex(KEY_NAME));
        mItem.mPrinter = c.getString(c.getColumnIndex(KEY_PRINTER));
        mItem.mDescription = c.getString(c.getColumnIndex(KEY_DESCRIPTION));
        mItem.mType = c.getString(c.getColumnIndex(KEY_TYPE));
        mItem.mAddress = c.getString(c.getColumnIndex(KEY_ADDRESS));
        mItem.mPort = c.getInt(c.getColumnIndex(KEY_PORT));
        mItem.mDPI = c.getInt(c.getColumnIndex(KEY_DPI));
        mItem.mWidth = c.getInt(c.getColumnIndex(KEY_WIDTH));
        mItem.mHeight = c.getInt(c.getColumnIndex(KEY_HEIGHT));
        mItem.mTimeStamp = new Date(c.getLong(c.getColumnIndex(KEY_MODIFIED)));
        mItem.mLanguage = c.getString(c.getColumnIndex(KEY_LANGUAGE));
        return mItem;
    }

    /**********************************************************************************************/
    private ContentValues populateValues(Printer item)
    {
        ContentValues Values = new ContentValues();
        Values.put(KEY_PRINTERID, item.mPrinterId);
        Values.put(KEY_NAME, item.mName);
        Values.put(KEY_PRINTER, item.mPrinter);
        Values.put(KEY_DESCRIPTION, item.mDescription);
        Values.put(KEY_TYPE, item.mType);
        Values.put(KEY_ADDRESS, item.mAddress);
        Values.put(KEY_PORT, item.mPort);
        Values.put(KEY_DPI, item.mDPI);
        Values.put(KEY_WIDTH, item.mWidth);
        Values.put(KEY_HEIGHT, item.mHeight);
        Values.put(KEY_MODIFIED,item.mTimeStamp.getTime());
        Values.put(KEY_LANGUAGE,item.mLanguage);
        return Values;
    }

    /**********************************************************************************************/
    private ArrayList<Printer> getItems(Cursor c)
    {
        ArrayList<Printer> mItems = new ArrayList<Printer>();
        if (c == null ) return mItems;
        if (c.getCount() == 0) { c.close(); return mItems; }
        c.moveToFirst();
        do {
            mItems.add(getRecord(c));
        }while (c.moveToNext());
        c.close();
        return mItems;
    }

    /**********************************************************************************************/
    public ArrayList<Printer> getAllPrinters()
    {
        Cursor c = mDbHelper.getReadableDatabase().query(DB_TABLE, null, null, null, null, null, null);
        return getItems(c);
    }
    /**********************************************************************************************/
    public void insertPrinter(Printer item)
    {
        ContentValues Values = populateValues(item);
        mDbHelper.getWritableDatabase().insert(DB_TABLE, null, Values);
    }
    /**********************************************************************************************/
    public void updatePrinter(Printer item)
    {
        ContentValues Values = populateValues(item);
        mDbHelper.getWritableDatabase().update(DB_TABLE,Values,KEY_PRINTERID + "=?",new String[] {String.valueOf(item.mPrinterId)} );
    }
    /**********************************************************************************************/
    public void replacePrinter(Printer item)
    {
        ContentValues Values = populateValues(item);
        mDbHelper.getWritableDatabase().replace(DB_TABLE,null,Values);
    }

    /**********************************************************************************************/
    public Printer getPrinter(String printerId)
    {
        Cursor c = mDbHelper.getReadableDatabase().query(DB_TABLE, null, KEY_PRINTERID + "=?",new String[] {printerId}, null, null, null);
        if (c == null ) return null;
        if (c.getCount() == 0) { c.close(); return null; }
        c.moveToFirst();
        Printer p = getRecord(c);
        c.close();
        return p;
    }

    /**********************************************************************************************/
    public void deletePrinter(Printer item)
    {
        ContentValues Values = populateValues(item);
        mDbHelper.getWritableDatabase().delete(DB_TABLE,KEY_PRINTERID + "=?",new String[] {String.valueOf(item.mPrinterId)} );
    }
    /**********************************************************************************************/
    public void deleteAll()
    {
         mDbHelper.getWritableDatabase().delete(DB_TABLE,null,null);
    }


    /**********************************************************************************************/
    public boolean exportData(Uri uri)
    {
        try
        {
            Cursor c = mDbHelper.getReadableDatabase().query(DB_TABLE, null, null, null, null, null, null);
            OutputStream os = mCtx.getContentResolver().openOutputStream(uri);
            OutputStreamWriter out = new OutputStreamWriter(os);
            XmlSerializer xml = Xml.newSerializer();
            xml.setFeature("http://xmlpull.org/v1/doc/features.html#indent-output", true);
            xml.setOutput(out);
            xml.startDocument("UTF-8",true);
            xml.startTag("", "database");
            if (c != null && c.getCount() != 0)
            {
                c.moveToFirst();
                do {
                    xml.startTag("","record");
                    for (int i=0; i < c.getColumnCount(); i++)
                    {
                        xml.startTag("",c.getColumnName(i));
                        xml.text(c.getString(i));
                        xml.endTag("",c.getColumnName(i));
                    }
                    xml.endTag("","record");
                }while (c.moveToNext());
                c.close();
            }
            xml.endTag("","database");
            xml.endDocument();
            out.flush();
            out.close();
            os.close();
        }catch (Exception e)
        {
            e.printStackTrace();
            return false;
        }
        return true;
    }
    /**********************************************************************************************/
    public boolean importData(Uri uri)
    {
        SQLiteDatabase db = mDbHelper.getWritableDatabase();
        try
        {
            InputStream is = mCtx.getContentResolver().openInputStream(uri);
            InputStreamReader in = new InputStreamReader(is);
            XmlPullParser xml = XmlPullParserFactory.newInstance().newPullParser();
            xml.setInput(in);
            int eventType = xml.getEventType();
            ContentValues Values = new ContentValues();
            int iRecCnt = 0;
            String Tag="";
            String Text="";
            db.beginTransaction();
            deleteAll();
            while (eventType != XmlPullParser.END_DOCUMENT)
            {
                //Start of a Tag
                if (eventType == XmlPullParser.START_TAG)
                {
                    Tag=xml.getName();
                    if (Tag.equals("record")) iRecCnt++;
                }

                //Capture the Text
                if (eventType == XmlPullParser.TEXT) Text = xml.getText();

                //Process on End TAg
                if (eventType == XmlPullParser.END_TAG)
                {
                    Tag=xml.getName();
                    if (Tag.equals("record")) iRecCnt--;
                    if (iRecCnt == 0)
                    {
                        if (Values.size() != 0) db.replace(DB_TABLE, null, Values);
                        Values.clear();
                    }else{
                        Values.put(Tag,Text);
                    }
                }
                eventType = xml.next();
            }
            db.setTransactionSuccessful();
            in.close();
        }catch (Exception e)
        {
            if (db.inTransaction()) db.endTransaction();
            return false;
        }
        db.endTransaction();
        return true;
    }
    /**********************************************************************************************/
    private static class DatabaseHelper extends SQLiteOpenHelper
    {

        public DatabaseHelper(Context ctx)
        {
            super(ctx,DB_NAME,null,DB_VERSION);
        }

        @Override
        public void onCreate(SQLiteDatabase db) {
            db.execSQL("CREATE TABLE " + DB_TABLE + " ( " +
                    KEY_PRINTERID + " TEXT NOT NULL PRIMARY KEY, " +
                    KEY_PRINTER + " TEXT," +
                    KEY_LANGUAGE + " TEXT DEFAULT 'zpl'," +
                    KEY_NAME + " TEXT NOT NULL," +
                    KEY_DESCRIPTION + " TEXT NOT NULL," +
                    KEY_TYPE + " TEXT NOT NULL," +
                    KEY_ADDRESS + " TEXT NOT NULL, " +
                    KEY_PORT + " INT, " +
                    KEY_DPI + " INT, " +
                    KEY_WIDTH + " INT, " +
                    KEY_HEIGHT + " INT, " +
                    KEY_MODIFIED + " DATETIME DEFAULT CURRENT_TIMESTAMP" +
                    " )");
        }

        @Override
        public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
            db.execSQL("DROP TABLE IF EXISTS " + DB_TABLE);
            onCreate(db);
        }
    }
}
