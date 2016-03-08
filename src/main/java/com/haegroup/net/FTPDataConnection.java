package com.haegroup.net;

import com.sun.xml.internal.ws.policy.privateutil.PolicyUtils;
import javafx.util.Callback;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Set;

/**
 * Created by William Connell on 01/02/2016.
 */
public class FTPDataConnection
{
    private static final DateTimeFormatter DATE_TIME_FORMATTER_YEAR = DateTimeFormatter.ofPattern("MMM dd  yyyy");
    private static final DateTimeFormatter DATE_TIME_FORMATTER_TIME = DateTimeFormatter.ofPattern("MMM dd HH:mm");

    private final ServerSocket listenSocket;

    private final String dataAddress;
    private final int dataPort;

    private final boolean isPassive;

    public FTPDataConnection(ServerSocket listenSocket)
    {
        this.listenSocket = listenSocket;

        this.dataAddress = null;
        this.dataPort = -1;

        this.isPassive = true;
    }

    public FTPDataConnection(String dataAddress, int dataPort)
    {
        this.listenSocket = null;

        this.dataAddress = dataAddress;
        this.dataPort = dataPort;

        this.isPassive = false;
    }

    @Override
    protected void finalize() throws Throwable
    {
        super.finalize();

        if (this.listenSocket != null)
        {
            this.listenSocket.close();
        }
    }

    /**
     * Sends the path listing via the connection asynchronously.
     *
     * @param rootPath Root part of the path.
     * @param path     Relative part of the path.
     * @return True if path was valid, return false otherwise.
     */
    public boolean list(String rootPath, String currentPath, String path, Callback<Boolean, Boolean> completed) throws IOException
    {
        // If no path is provided set to root.
        if (path == null)
        {
            path = "/";
        }

        // Convert to UNIX slashes.
        path = path.replace('\\', '/');
        final Path fullpath = Paths.get(rootPath, currentPath, path);

        // Verify that the path exists and that it's a directory.
        if (!Files.exists(fullpath) || !Files.isDirectory(fullpath))
        {
            return false;
        }

        // Connect to the socket.
        final Socket socket = openConnection();
        if (socket == null)
        {
            return false;
        }

        new Thread(() ->
        {
            try
            {
                final OutputStream dataOutputStream = socket.getOutputStream();

                // Retrieve a list of files.
                final File[] files = fullpath.toFile().listFiles();

                // Check that files were found.
                if (files != null)
                {
                    for (File file : files)
                    {
                        final char[] flags = new char[10];
                        Arrays.fill(flags, '-');
                        flags[0] = file.isDirectory() ? 'd' : '-';

                        try
                        {
                            final Set<PosixFilePermission> permissionSet = Files.getPosixFilePermissions(fullpath);
                            String permissions = PosixFilePermissions.toString(permissionSet);

                            for (int i = 1; i < flags.length; i++)
                            {
                                flags[i] = permissions.charAt(i - 1);
                            }
                        }
                        catch (UnsupportedOperationException e)
                        {
                            if (file.canRead())
                            {
                                flags[1] = flags[4] = flags[7] = 'r';
                            }

                            if (file.canWrite())
                            {
                                flags[2] = flags[5] = flags[8] = 'w';
                            }

                            if (file.canExecute())
                            {
                                flags[3] = flags[6] = flags[9] = 'x';
                            }
                        }

                        final String date = formatDate(
                                LocalDateTime.ofEpochSecond(file.lastModified(), 0, ZoneOffset.UTC));

                        final String line = String.format(
                                "%s   1 %-10s %-10s %10d %s %s",
                                new String(flags),
                                "temp",
                                "temp",
                                file.length(),
                                date,
                                file.getName());

                        writeLine(dataOutputStream, line);
                    }

                    dataOutputStream.close();

                    completed.call(true);
                }
            }
            catch (IOException e)
            {
                e.printStackTrace();
            }
        }, "TEMP THREAD").start();

        return true;
    }

    public boolean retrieve(Path path, Callback<Boolean, Boolean> completed, char transferCode) throws IOException
    {
        final File file = path.toFile();

        // Confirm the file exists.
        if (!file.exists() || !file.isFile())
        {
            return false;
        }

        // Connect to the socket.
        final Socket socket = openConnection();
        if (socket == null)
        {
            return false;
        }

        new Thread(() ->
        {
            try
            {
                final OutputStream dataOutputStream = socket.getOutputStream();

                InputStream inputStream = new FileInputStream(file);

                writeData(dataOutputStream, inputStream, transferCode);

                dataOutputStream.close();
                socket.close();

                completed.call(true);
            }
            catch (IOException e)
            {
                e.printStackTrace();
            }
        }, "TEMP THREAD").start();

        return true;
    }

    private int writeData(OutputStream destination, InputStream source, char transferCode) throws IOException
    {
        return writeDataBinary(destination, source);
    }

    private int writeDataBinary(OutputStream destination, InputStream source) throws IOException
    {
        byte[] buffer = new byte[4096];
        int count;
        int total = 0;

        while ((count = source.read(buffer)) > 0)
        {
            destination.write(buffer, 0, count);
            total += count;
        }

        return total;
    }

    private Socket openConnection() throws IOException
    {
        if (isPassive && listenSocket != null)
        {
            return listenSocket.accept();
        }
        else if (dataAddress != null && dataPort > 0)
        {
            return new Socket(dataAddress, dataPort);
        }

        return null;
    }

    private String formatDate(LocalDateTime dateTime)
    {
        if (dateTime.compareTo(LocalDateTime.now().minusDays(180)) < 0)
        {
            return dateTime.format(DATE_TIME_FORMATTER_YEAR);
        }
        else
        {
            return dateTime.format(DATE_TIME_FORMATTER_TIME);
        }
    }

    private void writeLine(OutputStream outputStream, String content) throws IOException
    {
        outputStream.write((content + FTPServer.LINE_END).getBytes("ASCII"));
        outputStream.flush();
    }

    public boolean isPassive()
    {
        return isPassive;
    }

    public boolean store(File file, Callback<Boolean, Boolean> completed) throws IOException
    {
        // Connect to the socket.
        final Socket socket = openConnection();
        if (socket == null)
        {
            return false;
        }

        new Thread(() ->
        {
            try
            {
                final InputStream dataInputStream = socket.getInputStream();

                OutputStream outputStream = new FileOutputStream(file);

                writeData(outputStream, dataInputStream, 'I');

                outputStream.close();
                dataInputStream.close();

                socket.close();

                completed.call(true);
            }
            catch (IOException e)
            {
                e.printStackTrace();
            }
        }, "TEMP THREAD").start();

        return true;
    }
}
