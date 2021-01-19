package com.zebra.zebraprintservice.connection;

import com.zebra.zebraprintservice.database.PrinterDatabase;

import java.io.IOException;
import java.io.InputStream;

public abstract class PrinterConnection
{
    public interface ConnectionCallback
    {
        void onConnected();
        void onConnectFailed();
    }

    public interface WriteDataCallback
    {
        void onWriteData(int iOffset, int iLength);
    }

    public abstract boolean isAvailable();
    public abstract void connect(ConnectionCallback callback);
    public abstract void disconnect();
    public abstract void destroy();
    public abstract String getName();
    public abstract String getDescription();
    public abstract InputStream getInputStream();
    public abstract void writeData(byte[] bData) throws IOException;
    public abstract void writeData(byte[] bData, WriteDataCallback callback) throws IOException;
    public abstract PrinterDatabase.Printer getPrinter();

}
