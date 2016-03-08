package com.haegroup.net;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Created by William Connell on 29/01/2016.
 */
public class FTPServer
{
    private static final int DEFAULT_COMMAND_PORT = 21;

    static final String LINE_END = "\r\n";

    private ServerSocket listenSocket;

    private int commandPort;

    private final ExecutorService executorService;
    private final String anonymousDirectory;

    public FTPServer(String anonymousDirectory)
    {
        this(DEFAULT_COMMAND_PORT, anonymousDirectory);
    }

    public FTPServer(int commandPort, String anonymousDirectory)
    {
        this.commandPort = commandPort;

        this.executorService = Executors.newFixedThreadPool(256);
        this.anonymousDirectory = anonymousDirectory;
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

    /**
     * Accept new client connections.
     */
    private void acceptClients()
    {
        while (!listenSocket.isClosed())
        {
            Socket client;

            try
            {
                client = listenSocket.accept();

                // Hand off to the thread pool.
                final FTPClientConnection connection = new FTPClientConnection(client, anonymousDirectory);
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
