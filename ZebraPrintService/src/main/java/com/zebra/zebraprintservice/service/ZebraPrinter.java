package com.zebra.zebraprintservice.service;


import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.pdf.PdfDocument;
import android.graphics.pdf.PdfRenderer;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import android.os.ParcelFileDescriptor;
import android.print.PrintAttributes;
import android.print.PrintJobId;
import android.print.PrintJobInfo;
import android.print.PrinterCapabilitiesInfo;
import android.print.PrinterId;
import android.print.PrinterInfo;
import android.printservice.PrintDocument;
import android.printservice.PrintJob;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.NonNull;

import com.tom_roush.pdfbox.android.PDFBoxResourceLoader;
import com.tom_roush.pdfbox.pdmodel.PDDocument;
import com.tom_roush.pdfbox.pdmodel.PDPage;
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream;
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle;
import com.tom_roush.pdfbox.pdmodel.graphics.image.JPEGFactory;
import com.tom_roush.pdfbox.pdmodel.graphics.image.PDImageXObject;
import com.zebra.zebraprintservice.BuildConfig;
import com.zebra.zebraprintservice.R;
import com.zebra.zebraprintservice.connection.PrinterConnection;
import com.zebra.zebraprintservice.database.PrinterDatabase;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.DecimalFormat;
import java.util.ArrayList;
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
    private PrintDocument mDocument = null;
    private PdfRenderer renderer = null;
    private StringBuilder mPrintData = new StringBuilder();
    private File mTempFile;
    private int mNbCopies = 0;
    private int mCurrentCopy = 0;
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
    //TODO: do this properly
    private static boolean isPdfBoxInitialized = false;
    private boolean isVariableLengthWithContinuousModeEnabled()
    {
        // TODO: implement settings for this mode
        // Set the printer to pdf mode and set to variable length
        if(isPdfBoxInitialized == false)
        {
            try
            {
                Thread thread = new Thread(new Runnable() {

                    @Override
                    public void run() {
                        try  {
                            //Your code goes here
                            //byte bResponse[] = new byte[200];
                            ////mConnection.writeData("! U1 setvar \"apl.settings\" \"varlen\"\r\n! U1 setvar \"apl.enable\" \"pdf\"\r\n\".getBytes());".getBytes());
                            //mConnection.writeData("! U1 setvar \"apl.settings\" \"no-varlen\"\r\n! U1 setvar \"apl.enable\" \"none\"\r\n".getBytes());
                            //ZebraPrinter.this.readInput(bResponse, MAX_STATUS_RESPONSE * 10, true);
                            //Log.d(TAG, bResponse.toString());
                            PDFBoxResourceLoader.init(ZebraPrinter.this.mService.getApplicationContext());
                            isPdfBoxInitialized = true;
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                });

                thread.start();

            }catch (Exception e) {
                e.printStackTrace();
            }
        }
        return true;
    }

    /**********************************************************************************************/


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
                mDocument = mCurrent.getDocument();
                PrintJobInfo jobInfo = mCurrent.getInfo();
                if(jobInfo != null)
                    mNbCopies = jobInfo.getCopies();
                else
                    mNbCopies = 1;
                mCurrentCopy = 0;

                // Extract the document that has to be printed as a file
                mTempFile = createTempFileFromPrintJobDocument();
                if(isVariableLengthWithContinuousModeEnabled())
                {
                    // Replace the temporary file with one that has been flattened
                    mTempFile = FlattenDocumentIntoOnePage(mTempFile);
                }

                if(mIsPDFDirectPrinter)
                {
                    try
                    {
                        ////Extract byte data from the document
                        //ParcelFileDescriptor inParcel = mDocument.getData();
                        //
                        //InputStream in = new ParcelFileDescriptor.AutoCloseInputStream(inParcel);
                        //ByteArrayOutputStream buffer = new ByteArrayOutputStream();
                        //int nRead;
                        //byte[] data = new byte[16384];
                        //
                        //while ((nRead = in.read(data, 0, data.length)) != -1) {
                        //    buffer.write(data, 0, nRead);
                        //}
                        //
                        //mPdfToPrintAsByteArray = buffer.toByteArray();
                        //buffer.close();
                        //in.close();
                        mPdfToPrintAsByteArray = readFileAsByteArray(mTempFile);
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
        boolean isCPCLPrinter = mLanguage.contains("line_print");
        try
        {
            //Have we Finished ?
            if (mCurrentPage >= mPageCount)
            {
                mCurrentCopy++;
                if(mCurrentCopy >= mNbCopies)
                    finishJob();
                else
                {
                    mCurrentPage = 0;
                    setProgress((float) (mCurrentPage + mPageCount*mCurrentCopy) / (float) (mPageCount*mNbCopies));
                    mMsgHandler.obtainMessage(MSG_NEXT_PAGE).sendToTarget();
                }
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

            int iWidth;
            int iHeight;
            int iSize;
            Bitmap bitmap;
            if(isVariableLengthWithContinuousModeEnabled())
            {
                iWidth = page.getWidth();
                iHeight = page.getHeight();
                iSize = iWidth * iHeight;
                bitmap = Bitmap.createBitmap(iWidth, iHeight, Bitmap.Config.ARGB_8888);
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_PRINT);
                iWidth = (iWidth >> 3);
            }
            else
            {
                //Calculate Bitmap Size
                iWidth = mDPI * page.getWidth() / 72;
                iHeight = mDPI * page.getHeight() / 72;
                iWidth = (iWidth + 7) / 8;
                iSize = iWidth * iHeight;

                //Render the Bitmap
                if (DEBUG) Log.i(TAG,"Rendering Size :" + iWidth + "," + iHeight);
                bitmap = Bitmap.createBitmap(iWidth << 3, iHeight, Bitmap.Config.ARGB_8888);
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_PRINT);
            }


            if (isCPCLPrinter)
            {
                //Create CPC
                if (DEBUG) Log.i(TAG, "Creating CPC");
                mPrintData.append("! 0 " + mDPI + " " + mDPI + " " + mLabelHeight + " 1\r\n");
                mPrintData.append("EG " + iWidth + " " + iHeight + " 0 0 ");
                mPrintData.append(ZebraPrintService.createBitmapCPC(bitmap));
                mPrintData.append("\r\n");
                mPrintData.append("PRINT\r\n");
                if(DEBUG) Log.i(TAG, "CPCL Data: \n"+ mPrintData);
            }else {
                // By default create ZPL Data
                //Create ZPL
                String ZPLBitmap = ZebraPrintService.createBitmapZPL(bitmap);
                if (DEBUG) Log.i(TAG, "Creating ZPL");
                mPrintData.append("^XA");
                if(isVariableLengthWithContinuousModeEnabled())
                {
                    mPrintData.append("^MNN");
                    // TODO: Do something with this hardcoded margin of hell
                    mPrintData.append("^LL"+ (iHeight + 60));
                    mPrintData.append("^LH0,60");
                }
                mPrintData.append("^PW"+(iWidth*8));
                mPrintData.append("^FO,0,0^GFA," + iSize + "," + iSize + "," + iWidth + ",");
                mPrintData.append(ZPLBitmap);
                if(mPageCount == 1 && mNbCopies > 1) {
                    // Add ^PQ to jobs like printing the same label many times (single page documents)
                    mPrintData.append("^PQ" + mNbCopies);
                    // The ^PQ will print all the necessary labels for us, so we do not need to repeat
                    // the job after this
                    mCurrentCopy = mNbCopies - 1;
                }
                mPrintData.append("^XZ\r\n\r\n");
                if(DEBUG) Log.i(TAG, "ZPL Data: \n"+ mPrintData);
                bCheckPrinter = true;
            }
            bitmap.recycle();

            page.close();

            //Check Printer Status
            if (bCheckPrinter && checkStatus() == false) return;

            //Send to Printer
            Toast.makeText(mService.getApplicationContext(), "Sensing page: " + mCurrentPage + " to printer.", Toast.LENGTH_SHORT).show();
            mConnection.writeData(mPrintData.toString().getBytes());
            Toast.makeText(mService.getApplicationContext(), "Page: " + mCurrentPage + " sent to printer.", Toast.LENGTH_SHORT).show();

            //Start Next Page
            mCurrentPage++;
            if(isCPCLPrinter == false && mPageCount == 1 && mNbCopies > 1) {
                setProgress(1.0f);
            }
            else {
                setProgress((float) (mCurrentPage + mPageCount * mCurrentCopy) / (float) (mPageCount * mNbCopies));
            }
            mMsgHandler.obtainMessage(MSG_NEXT_PAGE).sendToTarget();

        }catch (Exception e)
        {
            if(DEBUG) e.printStackTrace();
            fail(R.string.interror);
        }
    }

    private byte[] readFileAsByteArray(File file)
    {
        int size = (int) file.length();
        byte[] bytes = new byte[size];
        try {
            BufferedInputStream buf = new BufferedInputStream(new FileInputStream(file));
            buf.read(bytes, 0, bytes.length);
            buf.close();
        } catch (Exception e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
            return null;
        }
        return bytes;
    }

    private File createTempFileFromPrintJobDocument()
    {
        File fileToReturn = null;
        try {
            //Create a file copy of the document
            ParcelFileDescriptor inParcel = mDocument.getData();
            fileToReturn = File.createTempFile(mDocument.getInfo().getName().replaceAll("[\\\\/:*?\"<>|]", ""), null, mService.getCacheDir());
            fileToReturn.deleteOnExit();
            InputStream in = new ParcelFileDescriptor.AutoCloseInputStream(inParcel);
            OutputStream out = new FileOutputStream(fileToReturn);
            byte[] buf = new byte[8192];
            int r;
            while ((r = in.read(buf)) > -1) out.write(buf, 0, r);
            out.close();
            in.close();
            }
            catch (IOException e) {
                e.printStackTrace();
            }
        return fileToReturn;
    }

    private File FlattenDocumentIntoOnePage(File originalTempFile)
    {
        try {

            // Extract all the pages as bitmap files
            PdfRenderer renderer = new PdfRenderer(ParcelFileDescriptor.open(originalTempFile,ParcelFileDescriptor.MODE_READ_ONLY));
            int pageCount = renderer.getPageCount();
            if(pageCount == 1)
            {
                return originalTempFile;
            }
            File newFile = File.createTempFile(originalTempFile.getName() + ".flat.pdf", null, mService.getCacheDir());
            newFile.deleteOnExit();
            PdfRenderer.Page page = null;
            ArrayList<Bitmap> trimmedPages = new ArrayList<>(renderer.getPageCount());
            for(int currentPage = 0; currentPage < renderer.getPageCount(); currentPage++)
            {
                // Let's get pages one by one
                page = renderer.openPage(currentPage);

                //We keep the original document sizes
                //int iWidth = page.getWidth();
                //int iHeight = page.getHeight();

                //Calculate Bitmap Size according to printer data
                int iWidth = mDPI * page.getWidth() / 72;
                int iHeight = mDPI * page.getHeight() / 72;
                iWidth = ((iWidth + 7) / 8)<<3;

                //Render the Bitmap
                if (DEBUG) Log.i(TAG,"Rendering Size :" + iWidth + "," + iHeight);
                Bitmap bitmap = Bitmap.createBitmap(iWidth, iHeight, Bitmap.Config.ARGB_8888);
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_PRINT);

                // Now let's trim the bitmap image
                Bitmap trimmed = imageWithMargin(bitmap, 0, 1, false);
                bitmap.recycle();

                // let's convert it to black and white
                //Bitmap bwImage = convertToBlackAndWhite(trimmed);
                //trimmed.recycle();

                // Add the trimmed page to the list
                trimmedPages.add(trimmed);

                page.close();
            }

            // Combine all the bitmaps into one
            Bitmap combined = combineImageIntoOne(trimmedPages);
            for(Bitmap toRecycle : trimmedPages)
            {
                toRecycle.recycle();
            }

            //boolean succeeded = createPDFFileFromBitmap(combined, newFile);
            boolean succeeded = createPDFFileFromBitmapPDFBox(combined, newFile);
            combined.recycle();
            return succeeded ? newFile : null;
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    private boolean createPDFFileFromBitmap(Bitmap bitmapImage, File targetFile)
    {
        PdfDocument document = new PdfDocument();
        PdfDocument.PageInfo pageInfo = new PdfDocument.PageInfo.Builder(bitmapImage.getWidth(), bitmapImage.getHeight(), 1).create();
        PdfDocument.Page page = document.startPage(pageInfo);

        Canvas canvas = page.getCanvas();

        Paint paint = new Paint();
        paint.setColor(Color.parseColor("#ffffff"));
        canvas.drawPaint(paint);

        canvas.drawBitmap(bitmapImage, 0, 0 , null);
        document.finishPage(page);


        // write the document content
        try {
            document.writeTo(new FileOutputStream(targetFile));
        } catch (IOException e) {
            e.printStackTrace();
            Toast.makeText(mService.getApplicationContext(), "Something wrong: " + e.toString(), Toast.LENGTH_LONG).show();
        }

        // close the document
        document.close();
        return targetFile.exists();
    }

    private boolean createPDFFileFromBitmapPDFBox(Bitmap bitmap, File targetFile) {
        try {
            PDDocument document = new PDDocument();
            PDPage page = new PDPage(new PDRectangle(bitmap.getWidth(), bitmap.getHeight()));
            document.addPage(page);

            // Define a content stream for adding to the PDF

            PDPageContentStream contentStream = new PDPageContentStream(document, page);

            // Here you have great control of the compression rate and DPI on your image.
            // Update 2017/11/22: The DPI param actually is useless as of current version v1.8.9.1 if you take a look into the source code. Compression rate is enough to achieve a much smaller file size.
            PDImageXObject ximage = JPEGFactory.createFromImage(document, bitmap, 0.70f);

            // You may want to call PDPage.getCropBox() in order to place your image
            // somewhere inside this page rect with (x, y) and (width, height).
            contentStream.drawImage(ximage, 0, 0);

            // Make sure that the content stream is closed:
            contentStream.close();

            document.save(targetFile);
            document.close();
            return true;
        } catch (IOException e) {
            e.printStackTrace();
        }
        return false;
    }

        private Bitmap convertToBlackAndWhite(Bitmap bitmap)
    {
        Bitmap bwBitmap = Bitmap.createBitmap( bitmap.getWidth(), bitmap.getHeight(), Bitmap.Config.RGB_565 );
        float[] hsv = new float[ 3 ];
        for( int col = 0; col < bitmap.getWidth(); col++ ) {
            for( int row = 0; row < bitmap.getHeight(); row++ ) {
                Color.colorToHSV( bitmap.getPixel( col, row ), hsv );
                if( hsv[ 2 ] > 0.5f ) {
                    bwBitmap.setPixel( col, row, 0xffffffff );
                } else {
                    bwBitmap.setPixel( col, row, 0xff000000 );
                }
            }
        }
        return bwBitmap;
    }

    private Bitmap combineImageIntoOne(ArrayList<Bitmap> bitmap) {
        int w = 0, h = 0;
        for (int i = 0; i < bitmap.size(); i++) {
            if (i < bitmap.size() - 1) {
                w = bitmap.get(i).getWidth() > bitmap.get(i + 1).getWidth() ? bitmap.get(i).getWidth() : bitmap.get(i + 1).getWidth();
            }
            h += bitmap.get(i).getHeight();
        }

        Bitmap temp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(temp);
        int top = 0;
        for (int i = 0; i < bitmap.size(); i++) {
            Log.d(TAG, "Combine: "+i+"/"+bitmap.size()+1);

            top = (i == 0 ? 0 : top+bitmap.get(i-1).getHeight());
            canvas.drawBitmap(bitmap.get(i), 0f, top, null);
        }
        return temp;
    }

    public static Bitmap imageWithMargin(Bitmap bitmap, int color, int maxMargin, boolean removeLeftRightMargin) {
        int maxTop = 0, maxBottom = 0, maxLeft = bitmap.getWidth(), maxRight = 0;
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        int[] bitmapArray = new int[width * height];
        bitmap.getPixels(bitmapArray, 0, width, 0, 0, width, height);

        // Find first non-color pixel from top of bitmap
        searchTopMargin:
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                if (bitmapArray[width * y + x] != color) {
                    maxTop = (y > maxMargin ? y - maxMargin : 0);
                    break searchTopMargin;
                }
            }
        }

        // Find first non-color pixel from bottom of bitmap
        searchBottomMargin:
        for (int y = height - 1; y >= 0; y--) {
            for (int x = width - 1; x >= 0; x--) {
                if (bitmapArray[width * y + x] != color) {
                    maxBottom = y < height - maxMargin ? y + maxMargin : height;
                    break searchBottomMargin;
                }
            }
        }

        if(removeLeftRightMargin) {
            // We scan all the image from top to bottom to get the minimal margin for left
            searchLeftMargin:
            for (int x = 0; x < width; x++) {
                for (int y = (maxTop + maxMargin); y < (maxBottom - maxMargin); y++) {
                    if (bitmapArray[width * y + x] != color) {
                        int foundMaxLeft = (x > maxMargin ? x - maxMargin : 0);
                        maxLeft = (foundMaxLeft < maxLeft) ? foundMaxLeft : maxLeft;
                    }
                }
            }

            // We scan all the image from top to bottom to get the maximal margin for right
            searchRightMargin:
            for (int x = width - 1; x >= 0; x--) {
                for (int y = (maxTop + maxMargin); y < (maxBottom - maxMargin); y++) {
                    if (bitmapArray[width * y + x] != color) {
                        int foundMaxRight = x < width - maxMargin ? x + maxMargin : width;
                        maxRight = foundMaxRight > maxRight ? foundMaxRight : maxRight;
                    }
                }
            }
        }
        else
        {
            maxLeft = 0;
            maxRight = bitmap.getWidth();
        }

        return Bitmap.createBitmap(bitmap, maxLeft, maxTop, maxRight - maxLeft, maxBottom - maxTop);
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
            // If we need more than one copy, we send them one by one
            final float dataLength = (float)mPdfToPrintAsByteArray.length;
            final float totalLength = dataLength * (float)mNbCopies;
            for(mCurrentCopy = 0; mCurrentCopy < mNbCopies; mCurrentCopy++) {
                //Send to Printer
                mConnection.writeData(mPdfToPrintAsByteArray, new PrinterConnection.WriteDataCallback() {
                    @Override
                    public void onWriteData(int iOffset, int iLength) {
                        setProgress((float) (iOffset + dataLength*(float)mCurrentCopy) / totalLength);
                    }
                });
            }
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
