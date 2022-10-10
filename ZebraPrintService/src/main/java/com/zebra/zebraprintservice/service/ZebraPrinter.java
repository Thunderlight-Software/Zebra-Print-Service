package com.zebra.zebraprintservice.service;


import android.graphics.Bitmap;
import android.graphics.pdf.PdfRenderer;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import android.os.ParcelFileDescriptor;
import android.print.PrintAttributes;
import android.print.PrintJobId;
import android.print.PrinterCapabilitiesInfo;
import android.print.PrinterId;
import android.print.PrinterInfo;
import android.printservice.PrintDocument;
import android.printservice.PrintJob;
import android.util.Log;

import androidx.annotation.NonNull;

import com.zebra.zebraprintservice.BuildConfig;
import com.zebra.zebraprintservice.R;
import com.zebra.zebraprintservice.connection.PrinterConnection;
import com.zebra.zebraprintservice.database.PrinterDatabase;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.DecimalFormat;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class ZebraPrinter implements Handler.Callback
{
    private static final String TAG = ZebraPrinter.class.getSimpleName();
    private static final boolean DEBUG = BuildConfig.DEBUG & true;
    private static final int MSG_START_JOB              = 1;
    private static final int MSG_NEXT_PAGE              = 2;
    private static final int MSG_GET_DETAILS            = 3;
    private static final int MSG_GET_JOB                = 4;
    private static final int MSG_QUIT                   = 5;
    private static final int MSG_SEND_PDF_DATA          = 6;
    private static final int MAX_STATUS_RESPONSE        = 500;
    private int mPrinterStatus = PrinterInfo.STATUS_IDLE;
    private final List<PrintJob> mJobs = new CopyOnWriteArrayList<>();
    private PrintJob mCurrent = null;
    protected Handler mHandler;
    private Handler mMsgHandler;
    private HandlerThread mMsgThread;
    private ZebraDiscoverySession mDiscoverySession;
    private ZebraPrintService mService;
    private PrinterConnection mConnection;
    private PrinterId mPrinterId = null;
    private PdfRenderer renderer = null;
    private StringBuilder mPrintData = new StringBuilder();
    private File mTempFile;
    private int mPageCount = 0;
    private int mCurrentPage = 0;
    private boolean bCancelled = false;
    private int mDPI = 0;
    private int mLabelWidth = 0;
    private int mLabelHeight = 0;
    private String mLanguage = "";
    private boolean mIsPDFDirectPrinter = false;
    private byte[] mPdfToPrintAsByteArray = null;

    public interface ConnectionCallback
    {
        void onConnected();
        void onConnectFailed();
    }

    /*********************************************************************************************/
    public ZebraPrinter(ZebraPrintService service,ZebraDiscoverySession discoverySession, PrinterId printerId, PrinterConnection connection)
    {
        if (DEBUG) Log.d(TAG, "Create Printer() -> " + printerId.getLocalId());
        mService = service;
        mDiscoverySession = discoverySession;
        mPrinterId = printerId;
        mConnection = connection;
        PrinterDatabase.Printer printer = mConnection.getPrinter();
        mDPI = printer.mDPI;
        mLabelHeight = printer.mHeight;
        mLabelWidth = printer.mWidth;
        mHandler = new Handler(mService.getMainLooper());
        mMsgThread = new HandlerThread("PrinterMsgHandler");
        mMsgThread.start();
        mMsgHandler = new Handler(mMsgThread.getLooper(),this);
    }

    /*********************************************************************************************/
    private void updatePrinter(boolean bUpdateDB)
    {
        if (DEBUG) Log.d(TAG, "updatePrinter() -> " + mPrinterId.getLocalId());

        if (bUpdateDB)
        {
            PrinterDatabase mDb = new PrinterDatabase(mService);
            PrinterDatabase.Printer printer = mConnection.getPrinter();
            printer.mDPI = mDPI;
            printer.mHeight = mLabelHeight;
            printer.mWidth = mLabelWidth;
            printer.mLanguage = mLanguage;
            mDb.updatePrinter(printer);
            mDb.close();
        }

        if (mDiscoverySession == null) return;

        mHandler.post(new Runnable()
        {
            @Override
            public void run()
            {
                mDiscoverySession.addPrinters(Collections.singletonList(getPrinterInfo()));
            }
        });
    }

    /*********************************************************************************************/
    public void startTracking()
    {
        if (DEBUG) Log.d(TAG, "startTracking() -> " + mPrinterId.getLocalId());
        mMsgHandler.obtainMessage(MSG_GET_DETAILS).sendToTarget();
    }

    /*********************************************************************************************/
    public void stopTracking()
    {
        if (DEBUG) Log.d(TAG, "stopTracking() -> " + mPrinterId.getLocalId());
    }
    /*********************************************************************************************/
    public boolean isAvailable()
    {
        return mConnection.isAvailable();
    }

    /*********************************************************************************************/
    public void destroy()
    {
        try
        {
            if (mMsgThread == null) return;
            mConnection.destroy();
            mMsgHandler.removeCallbacksAndMessages(null);
            mMsgHandler.obtainMessage(MSG_QUIT).sendToTarget();
            mMsgThread.join();
            mMsgThread = null;
        }catch (Exception e) {
            if(DEBUG) e.printStackTrace();
        }
    }
    /*********************************************************************************************/
    public PrinterInfo getPrinterInfo()
    {
        if (DEBUG) Log.d(TAG, "getPrinterInfo()");
        PrinterInfo.Builder builder = new PrinterInfo.Builder(mPrinterId, mConnection.getName(),mPrinterStatus)
                .setIconResourceId(R.drawable.ic_printer)
                .setDescription(mConnection.getDescription());

        //If we have Capabilities
        if (!mLanguage.isEmpty())
        {
            float fWidth = (float)mLabelWidth / (float)mDPI;
            float fHeight = (float)mLabelHeight / (float)mDPI;
            DecimalFormat df = new DecimalFormat("0.00");
            String labelSize = df.format(fWidth) + "\" x " + df.format(fHeight) + "\"";
            PrinterCapabilitiesInfo.Builder cap = new PrinterCapabilitiesInfo.Builder(mPrinterId);
            cap.addMediaSize(new PrintAttributes.MediaSize(labelSize, labelSize, (int)(fWidth * 1000), (int)(fHeight * 1000)), true);
            cap.addResolution(new PrintAttributes.Resolution(String.valueOf(mDPI), String.valueOf(mDPI), mDPI, mDPI), true);
            cap.setColorModes(PrintAttributes.COLOR_MODE_MONOCHROME, PrintAttributes.COLOR_MODE_MONOCHROME);
            cap.setDuplexModes(PrintAttributes.DUPLEX_MODE_NONE, PrintAttributes.DUPLEX_MODE_NONE);
            cap.setMinMargins(new PrintAttributes.Margins(0, 0, 0, 0));
            builder.setCapabilities(cap.build());
        }

        return builder.build();
    }

    /**********************************************************************************************/
    public void addJob(PrintJob job)
    {
        if (DEBUG) Log.d(TAG, "addJob()");
        mJobs.add(job);
        job.start();
        job.block(mService.getResources().getString(R.string.waiting));
        if (mCurrent == null) mMsgHandler.obtainMessage(MSG_START_JOB).sendToTarget();
    }

    /**********************************************************************************************/
    public void cancelJob(PrintJobId id)
    {
        if (DEBUG) Log.d(TAG, "cancelJob()");
        for (PrintJob job : mJobs)
        {
            if (job.getId().equals(id))
            {
                mJobs.remove(job);
                job.cancel();
                return;
            }
        }

        if (mCurrent != null && mCurrent.getId().equals(id))  bCancelled = true;
    }
    /**********************************************************************************************/
    @Override
    public boolean handleMessage(@NonNull Message msg)
    {
        switch (msg.what)
        {
            //Start the Print Job
            case MSG_START_JOB:
                if (DEBUG) Log.d(TAG, "Start Print Job");
                MsgStartJob();
                return true;

            case MSG_GET_JOB:
                MsgGetJob();
                return true;

            case MSG_NEXT_PAGE:
                if (DEBUG) Log.d(TAG, "Render Page");
                MsgNextPage();
                return true;

            case MSG_GET_DETAILS:
                MsgGetDetails(true);
                return true;

            case MSG_QUIT:
                if (DEBUG) Log.i(TAG,"Quit Msg Loop");
                mMsgThread.quit();
                break;

            case MSG_SEND_PDF_DATA:
                if (DEBUG) Log.i(TAG,"Send PDF Data to printer.");
                MsgSendPDFData();
                return true;

        }
        return false;
    }

    /**********************************************************************************************/
    private void checkFlushed()
    {
        try
        {
            byte bResponse[] = new byte[200];
            mConnection.writeData("! U1 getvar \"device.languages\"\r\n".getBytes());
            readInput(bResponse, MAX_STATUS_RESPONSE * 10, true);
        }catch (Exception e) {}
    }

    /**********************************************************************************************/
    private void MsgStartJob()
    {
        bCancelled = false;
        if (mJobs.isEmpty()) { mCurrent = null; mConnection.disconnect(); return; }

        //Need to obtain settings from printer if we don't have them
        MsgGetDetails(false);

        mCurrent = mJobs.remove(0);
        mConnection.connect(new PrinterConnection.ConnectionCallback()
        {
            @Override
            public void onConnected()
            {
                mMsgHandler.obtainMessage(MSG_GET_JOB).sendToTarget();
            }

            @Override
            public void onConnectFailed()
            {
                fail(R.string.interror);
            }
        });

    }
    /**********************************************************************************************/
    private void MsgGetJob()
    {
        mHandler.post(new Runnable()
        {
            @Override
            public void run()
            {
                mCurrent.start();
                PrintDocument doc = mCurrent.getDocument();
                if(mIsPDFDirectPrinter)
                {
                    try
                    {
                        //Extract byte data from the document
                        ParcelFileDescriptor inParcel = doc.getData();

                        InputStream in = new ParcelFileDescriptor.AutoCloseInputStream(inParcel);
                        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
                        int nRead;
                        byte[] data = new byte[16384];

                        while ((nRead = in.read(data, 0, data.length)) != -1) {
                            buffer.write(data, 0, nRead);
                        }

                        mPdfToPrintAsByteArray = buffer.toByteArray();

                        buffer.close();
                        in.close();

                        mMsgHandler.obtainMessage(MSG_SEND_PDF_DATA).sendToTarget();

                    }catch (Exception e)
                    {
                        mPdfToPrintAsByteArray = null;
                        if(DEBUG) e.printStackTrace();
                        fail(R.string.interror);
                    }

                }
                else
                {
                    try
                    {
                        //Create a file copy of the document
                        ParcelFileDescriptor inParcel = doc.getData();
                        mTempFile = File.createTempFile(doc.getInfo().getName().replaceAll("[\\\\/:*?\"<>|]", ""), null, mService.getCacheDir());
                        mTempFile.deleteOnExit();

                        InputStream in = new ParcelFileDescriptor.AutoCloseInputStream(inParcel);
                        OutputStream out = new FileOutputStream(mTempFile);
                        byte[] buf = new byte[8192];
                        int r;
                        while ((r = in.read(buf)) > -1) out.write(buf, 0, r);
                        out.close();
                        in.close();

                        renderer = new PdfRenderer(ParcelFileDescriptor.open(mTempFile,ParcelFileDescriptor.MODE_READ_ONLY));
                        mPageCount = renderer.getPageCount();
                        mCurrentPage = 0;
                        mMsgHandler.obtainMessage(MSG_NEXT_PAGE).sendToTarget();

                    }catch (Exception e)
                    {
                        if(DEBUG) e.printStackTrace();
                        fail(R.string.interror);
                    }
                }
            }
        });
    }

    /**********************************************************************************************/
    private void MsgNextPage()
    {
        boolean bCheckPrinter = false;
        try
        {
            //Have we Finished ?
            if (mCurrentPage == mPageCount)
            {
                finishJob();
                return;
            }

            // Request to cancel print ?
            if (bCancelled == true)
            {
                cancelJob();
                return;
            }

            //Get Next Page
            if (DEBUG) Log.d(TAG, "Rendering Page :" + (mCurrentPage + 1) + "/" + mPageCount);
            PdfRenderer.Page page = renderer.openPage(mCurrentPage);
            mPrintData.setLength(0);

            //Calculate Bitmap Size
            int iWidth = mDPI * page.getWidth() / 72;
            int iHeight = mDPI * page.getHeight() / 72;
            iWidth = (iWidth + 7) / 8;
            int iSize = iWidth * iHeight;

            //Render the Bitmap
            if (DEBUG) Log.i(TAG,"Rendering Size :" + iWidth + "," + iHeight);
            Bitmap bitmap = Bitmap.createBitmap(iWidth << 3, iHeight, Bitmap.Config.ARGB_8888);
            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_PRINT);

            if (mLanguage.contains("zpl"))
            {
                //Create ZPL
                if (DEBUG) Log.i(TAG, "Creating ZPL");
                mPrintData.append("^XA");
                mPrintData.append("^FO,0,0^GFA," + iSize + "," + iSize + "," + iWidth + ",");
                mPrintData.append(ZebraPrintService.createBitmapZPL(bitmap));
                mPrintData.append("^XZ\r\n\r\n");
                bCheckPrinter = true;
            }else {
                //Create CPC
                if (DEBUG) Log.i(TAG, "Creating CPC");
                mPrintData.append("! 0 " + mDPI + " " + mDPI + " " + mLabelHeight + " 1\r\n");
                mPrintData.append("EG " + iWidth + " " + iHeight + " 0 0 ");
                mPrintData.append(ZebraPrintService.createBitmapCPC(bitmap));
                mPrintData.append("\r\n");
                mPrintData.append("PRINT\r\n");
            }
            bitmap.recycle();

            page.close();

            //Check Printer Status
            if (bCheckPrinter && checkStatus() == false) return;

            //Send to Printer
            mConnection.writeData(mPrintData.toString().getBytes());

            //Start Next Page
            mCurrentPage++;
            setProgress((float) mCurrentPage / (float) mPageCount);
            mMsgHandler.obtainMessage(MSG_NEXT_PAGE).sendToTarget();

        }catch (Exception e)
        {
            if(DEBUG) e.printStackTrace();
            fail(R.string.interror);
        }
    }

    /**********************************************************************************************/
    private void MsgSendPDFData()
    {
        // Request to cancel print ?
        if (bCancelled == true)
        {
            cancelJob();
            return;
        }
        //Check Printer Status
        try {
            if (checkStatus() == false) return;

            //Send to Printer
            mConnection.writeData(mPdfToPrintAsByteArray, new PrinterConnection.WriteDataCallback() {
                @Override
                public void onWriteData(int iOffset, int iLength) {
                    setProgress((float) iOffset / (float) iLength);
                }
            });


        } catch (IOException e) {
            e.printStackTrace();
            fail(R.string.interror);
        }
        // Finish the job.
        finishJob();
    }

    /**********************************************************************************************/
    private void MsgGetDetails(final boolean bDisconnect)
    {
        mConnection.connect(new PrinterConnection.ConnectionCallback()
        {
            @Override
            public void onConnected()
            {
                byte bResponse[] = new byte[200];
                try
                {
                    //Get Device Language
                    mLanguage = "";
                    if (DEBUG) Log.i(TAG,"Getting Device Settings");
                    mConnection.writeData("! U1 getvar \"device.languages\"\r\n".getBytes());
                    int respLen = readInput(bResponse,MAX_STATUS_RESPONSE,true);
                    String resp = new String(bResponse,0,respLen).toString().replace("\"","");
                    if (DEBUG) Log.i(TAG,"Reported Language:" + resp);
                    mLanguage = resp;

                    //Get DPI if we can
                    try {
                        mConnection.writeData("! U1 getvar \"head.resolution.in_dpi\"\r\n".getBytes());
                        respLen = readInput(bResponse,MAX_STATUS_RESPONSE,true);
                        if (respLen != 0)
                        {
                            resp = new String(bResponse, 0, respLen).toString().replace("\"", "");
                            mDPI = Integer.parseInt(resp);
                        }
                    }catch (Exception e) {}

                    // Get apl status to check if we are dealing with a pdf direct printer
                    try {
                        mConnection.writeData("! U1 getvar \"apl.enable\"\r\n".getBytes());
                        respLen = readInput(bResponse,MAX_STATUS_RESPONSE,true);
                        if (respLen != 0)
                        {
                            resp = new String(bResponse, 0, respLen).toString().replace("\"", "");
                            mIsPDFDirectPrinter = resp.equalsIgnoreCase("pdf");
                        }
                    }catch (Exception e) {}

                    updatePrinter(true);
                    if (bDisconnect) mConnection.disconnect();
                }catch (Exception e) {
                    if(DEBUG) e.printStackTrace();
                    mPrinterStatus = PrinterInfo.STATUS_UNAVAILABLE;
                    updatePrinter(false);
                    mConnection.disconnect();
                }
            }

            @Override
            public void onConnectFailed()
            {
                if (DEBUG) Log.i(TAG,"Connection Failed");
               mPrinterStatus = PrinterInfo.STATUS_UNAVAILABLE;
               updatePrinter(false);
            }
        });
    }

    /**********************************************************************************************/
    private void setProgress(final float progress)
    {
        if (DEBUG) Log.d(TAG, "setProgress() -> :" + progress);
        mHandler.post(new Runnable()
        {
            @Override
            public void run()
            {
                mCurrent.setProgress(progress);
            }
        });
    }
    /**********************************************************************************************/
    private void finishJob()
    {
        if (DEBUG) Log.d(TAG, "finishJob()");
        mHandler.post(new Runnable()
        {
            @Override
            public void run()
            {
                if(mIsPDFDirectPrinter)
                {
                    // Set mPdfToPrintAsByteArray to null for better garbage collection
                    mPdfToPrintAsByteArray = null;
                }
                else
                {
                    checkFlushed();
                    renderer.close();
                    mTempFile.delete();
                }
                mCurrent.complete();
                mMsgHandler.obtainMessage(MSG_START_JOB).sendToTarget();
            }
        });
    }

    /**********************************************************************************************/
    private void cancelJob()
    {
        if (DEBUG) Log.d(TAG, "cancelJob()");
        mHandler.post(new Runnable()
        {
            @Override
            public void run()
            {
                checkFlushed();
                renderer.close();
                mTempFile.delete();
                mMsgHandler.obtainMessage(MSG_START_JOB).sendToTarget();
            }
        });
    }

    /**********************************************************************************************/
    private void fail(final int stringId)
    {
        Log.e(TAG, "fail() -> " + mService.getResources().getString(stringId));
        mHandler.post(new Runnable()
        {
            @Override
            public void run()
            {
                mCurrent.fail(mService.getResources().getString(stringId));
                mCurrent = null;
            }
        });
    }
    /**********************************************************************************************/
    private int readInput(byte[] b,int timeout,boolean bAnyLen) throws IOException
    {
        int bufferOffset = 0;
        long maxTimeMillis = System.currentTimeMillis() + timeout;
        while (System.currentTimeMillis() < maxTimeMillis && bufferOffset < b.length)
        {
            int readLength = Math.min( mConnection.getInputStream().available(),b.length-bufferOffset);
            int readResult = mConnection.getInputStream().read(b, bufferOffset, readLength);
            if (readResult == -1) break;
            bufferOffset += readResult;
            if (bufferOffset != 0 && bAnyLen == true ) return bufferOffset;
        }
        return bufferOffset;
    }

    /**********************************************************************************************/
    // Field 1 = Paper Out
    // Field 2 = Paused
    // Field 14 = Head up
    // Field 10 = Too Cold *
    // Field 11 = Too Hot  *
    // Field 15 = Ribbon Out *
    // * = Not Implemented
    private boolean checkStatus() throws IOException
    {
            mConnection.writeData("~HS\n".getBytes());
            byte[] bResponse = new byte[256];
            int ResLen = readInput(bResponse,MAX_STATUS_RESPONSE,true);
            if (ResLen == 0) { fail(R.string.no_response); return false; }                                        // No Response
            if (bResponse[0] != 0x02) { fail(R.string.no_response); return false; }                                // Invalid Response

            //Create string from Response
            StringBuilder Response = new StringBuilder();
            for (int i=0; i<ResLen; i++)
            {
                if (bResponse[i] > ' ') Response.append((char)bResponse[i]);
                if (bResponse[i] == 0x0d) Response.append(",");
            }
            String[] Fields = Response.toString().split(",");

            //Check Paper Out
            if (Fields[1].equals("1"))
            {
                if(DEBUG) Log.i(TAG, "Printer Out of Paper");
                fail(R.string.out_of_paper);
                return false;
            }

            //Check Paused
            if (Fields[2].equals("1"))
            {
                if(DEBUG) Log.i(TAG, "Printer Paused");
                fail(R.string.paused);
                return false;
            }

            //Check Head up
            if (Fields[14].equals("1"))
            {
                if(DEBUG) Log.i(TAG, "Printer Head Up");
                fail(R.string.head_up);
                return false;
            }
        return true;
    }

}
