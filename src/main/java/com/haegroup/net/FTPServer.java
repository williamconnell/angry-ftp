package com.haegroup.net;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Created by william.connell on 29/01/2016.
 */
public class FTPServer
{
    public static final int DEFAULT_COMMAND_PORT = 21;
    public static final int DEFAULT_DATA_PORT = 20;

    public static final String LINE_END = "\r\n";

    private ServerSocket listenSocket;
    private int commandPort;
    private int dataPort;

    private final ExecutorService executorService;

    public FTPServer()
    {
        this(DEFAULT_COMMAND_PORT);
    }

    public FTPServer(int commandPort)
    {
        this(commandPort, DEFAULT_DATA_PORT);
    }

    public FTPServer(int commandPort, int dataPort)
    {
        this.commandPort = commandPort;
        this.dataPort = dataPort;

        this.executorService = Executors.newFixedThreadPool(256);
    }

    public void start() throws IOException
    {
        listenSocket = new ServerSocket(commandPort);
        acceptClients();
    }

    public void stop()
    {
        if (listenSocket != null)
        {
            try
            {
                listenSocket.close();
            }
            catch (IOException ignored)
            {
            }
        }
    }

    private void acceptClients()
    {
        while (!listenSocket.isClosed())
        {
            Socket client = null;

            try
            {
                client = listenSocket.accept();

                // Hand off to the thread pool.
                final FTPClientConnection connection = new FTPClientConnection(client);
                executorService.submit((Runnable) () -> {
                    try
                    {
                        connection.handle(null);
                    }
                    catch (Exception e)
                    {
                        e.printStackTrace();
                    }
                });

            }
            catch (IOException e)
            {
                e.printStackTrace();
            }
        }
    }
}
