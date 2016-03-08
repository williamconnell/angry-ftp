package com.haegroup.net;

import org.apache.commons.lang3.ArrayUtils;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.KeyStore;

/**
 * Created by William Connell on 29/01/2016.
 */
public class FTPClientConnection
{
    private final Socket originalSocket;
    private Socket socket;
    private FTPDataConnection dataConnection;

    private InputStream commandInputStream;
    private OutputStream commandOutputStream;

    private String username;

    private String rootPath;
    private String currentPath;

    private char transferCode;

    public FTPClientConnection(Socket socket, String anonymousDirectory) throws IOException
    {
        this.originalSocket = socket;
        this.socket = this.originalSocket;

        this.commandInputStream = socket.getInputStream();
        this.commandOutputStream = socket.getOutputStream();

        // Store the paths.
        this.rootPath = anonymousDirectory;// new File(".").getCanonicalPath().replace('\\', '/');
        this.currentPath = "/";

        this.dataConnection = null;
    }

    public void handle(Object object) throws Exception
    {
        writeLine("220 Service ready for new user.");

        try
        {
            String line;
            while (true)
            {
                // Read the line.
                line = readLine();
                System.out.println(line);

                // Parse the message from the client.
                String response;
                String[] commandParts = line.split(" ", 2);

                // Retrieve the command and the arguments.
                String command = commandParts[0].toUpperCase().trim();
                String arguments = commandParts.length > 1 ? line.substring(command.length() + 1) : null;

                if (arguments != null)
                {
                    arguments = arguments.trim();

                    if (arguments.isEmpty())
                    {
                        arguments = null;
                    }
                }

                // Handle the command.
                switch (command)
                {
                    case "AUTH":
                        response = auth(arguments);
                        break;

                    case "USER":
                        response = user(arguments);
                        break;

                    case "PASS":
                        response = password(arguments);
                        break;

                    case "CWD":
                        response = changeWorkingDirectory(arguments);
                        break;

                    case "CDUP":
                        response = changeWorkingDirectory("..");
                        break;

                    case "PWD":
                        response = String.format("257 \"%s\" is current directory.", currentPath);
                        break;

                    case "QUIT":
                        response = "221 Service closing control connection.";
                        break;

                    case "TYPE":
                        if (arguments != null)
                        {
                            String[] args = arguments.split(" ", 2);
                            response = type(args[0], args.length > 1 ? args[1] : null);
                        }
                        else
                        {
                            response = "502 Command not implemented.";
                        }

                        break;

                    case "DELE":
                        response = delete(arguments);
                        break;

                    case "PORT":
                        response = port(arguments);
                        break;

                    case "PASV":
                        response = passive(arguments);
                        break;

                    case "EPSV":
                        response = extendedPassive(arguments);
                        break;

                    case "EPRT":
                        response = extendedPort(arguments);
                        break;

                    case "LIST":
                        response = list(arguments);
                        break;

                    case "RETR":
                        response = retrieve(arguments);
                        break;

                    case "STOR":
                        response = store(arguments);
                        break;

                    default:
                        response = "502 Command not implemented.";
                        break;
                }

                if (socket == null || !socket.isConnected())
                {
                    break;
                }
                else
                {
                    writeLine(response);

                    // Quit message.
                    if (response.startsWith("221"))
                    {
                        break;
                    }

                    if (command.equals("AUTH"))
                    {
                        InputStream stream = null;
                        try
                        {
                            stream = FTPClientConnection.class.getResource("/server.pfx").openStream();

                            final SSLContext sslContext = SSLContext.getInstance("TLS");

                            final KeyStore keyStore = KeyStore.getInstance("PKCS12");
                            keyStore.load(stream, "password".toCharArray());

                            final KeyManagerFactory keyManagerFactory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
                            keyManagerFactory.init(keyStore, "password".toCharArray());

                            sslContext.init(keyManagerFactory.getKeyManagers(), null, null);

                            SSLSocketFactory sslSocketFactory = sslContext.getSocketFactory();

                            // Override the socket.
                            SSLSocket sslSocket = (SSLSocket) sslSocketFactory.createSocket(
                                    originalSocket,
                                    null,
                                    originalSocket.getPort(),
                                    false);
                            sslSocket.setUseClientMode(false);

                            socket = sslSocket;

                            // Store the new streams.
                            commandInputStream = socket.getInputStream();
                            commandOutputStream = socket.getOutputStream();
                        }
                        finally
                        {
                            if (stream != null)
                            {
                                stream.close();
                            }
                        }
                    }
                }
            }
        }
        catch (Exception e)
        {
            e.printStackTrace();
            throw e;
        }
    }

    private String auth(String mode)
    {
        switch (mode)
        {
            case "TLS":
                return "234 Enabling TLS Connection.";

            default:
                return "504 Unrecognised AUTH mode.";
        }
    }

    private String delete(String filename) throws IOException
    {
        final String absoluteFilename = rootPath + changeDirectory(currentPath, filename);

        if (!Files.exists(Paths.get(absoluteFilename)))
        {
            return "505 File does not exist.";
        }

        Files.delete(Paths.get(absoluteFilename));

        return "250 Requested file action okay, completed.";
    }

    private String store(String filename) throws IOException
    {
        final String absoluteFilename = rootPath + changeDirectory(currentPath, filename);
        final String absoluteFilepath = absoluteFilename.substring(0, absoluteFilename.lastIndexOf('/'));

        if (!Files.exists(Paths.get(absoluteFilepath)))
        {
            return "505 Path does not exist.";
        }

        final File file = new File(absoluteFilename);
        if (!dataConnection.store(file, param -> {
            try
            {
                writeLine("226 Closing data connection, file transfer successful.");
            }
            catch (IOException e)
            {
                return false;
            }
            return true;
        }))
        {
            return "505 File not found.";
        }

        return "150 File status okay; about to open data connection.";
    }

    private String retrieve(String filename) throws IOException
    {
        if (filename == null)
        {
            return "505 File not found.";
        }

        // Sanitise the filename
        filename = filename.replace('\\', '/');
        Path path = Paths.get(rootPath, currentPath, filename);

        if (dataConnection.retrieve(path, param -> {
            try
            {
                writeLine("226 Closing data connection, file transfer successful.");
            }
            catch (IOException e)
            {
                return false;
            }
            return true;
        }, transferCode))
        {
            return String.format("150 Opening %s mode data transfer for RETR", dataConnection.isPassive() ? "PASSIVE" : "ACTIVE");
        }

        return "505 File not found.";
    }

    private String readLine() throws IOException
    {
        return readLine(commandInputStream);
    }

    private String readLine(InputStream inputStream) throws IOException
    {
        StringBuilder builder = new StringBuilder();

        do
        {
            // Read the next byte.
            byte character = (byte) inputStream.read();
            builder.append((char) character);
        } while (!builder.toString().endsWith(FTPServer.LINE_END));

        return builder.toString().trim();
    }

    private void writeLine(String content) throws IOException
    {
        writeLine(commandOutputStream, content);
    }

    private void writeLine(OutputStream outputStream, String content) throws IOException
    {
        outputStream.write((content + FTPServer.LINE_END).getBytes("ASCII"));
        outputStream.flush();
    }

    private String user(String username)
    {
        this.username = username;

        return "331 User name okay, need password.";
    }

    private String password(String password)
    {
        return "230 User logged in, proceed. Logged out if appropriate.";
    }

    private String changeWorkingDirectory(String path)
    {
        String newPath = changeDirectory(currentPath, path);

        // Verify the path is valid.
        if (!Files.isDirectory(Paths.get(rootPath, newPath)))
        {
            return "550 Requested action not taken. File unavailable (e.g., file not found, no access).";
        }

        currentPath = newPath;

        return "250 Requested file action okay, completed.";
    }

    private String type(String typeCode, String formatControl)
    {
        String response;

        switch (typeCode)
        {
            case "A":
            case "I":
                transferCode = typeCode.charAt(0);
                response = "200 OK";
                break;

            case "E":
            case "L":
            default:
                response = "504 Command not implemented for that parameter.";
                break;
        }

        if (formatControl != null)
        {
            switch (formatControl)
            {
                case "N":
                    response = "200 OK";
                    break;

                case "T":
                case "C":
                default:
                    response = "504 Command not implemented for that parameter.";
                    break;
            }
        }

        return response;
    }

    private String port(String arguments) throws IOException
    {
        if (arguments == null)
        {
            return "501 Syntax error in parameters or arguments.";
        }

        String[] args = arguments.split(",");

        // Skip 0 as that will be empty.
        String address = String.format("%s.%s.%s.%s", args);

        int high = Integer.parseInt(args[4]);
        int low = Integer.parseInt(args[5]);

        // Wrap the ByteBuffer.
        ByteBuffer bb = ByteBuffer.wrap(new byte[] {(byte)(high & 0xFF), (byte)(low & 0xFF)});
        bb.order(ByteOrder.BIG_ENDIAN);

        int port = bb.getShort() & 0xFFFF;
        System.out.println("PORT: " + port);

        return extendedPort(String.format("|%d|%s|%d|", 1, address, port));
    }

    private String passive(String arguments) throws IOException
    {
        String result = startPassive(0);

        String[] split = result.split("\\|", 2);

        String address = split[0].replace('.', ',');
        int port = Integer.parseInt(split[1]);

        ByteBuffer bb = ByteBuffer.allocate(2);
        bb.order(ByteOrder.BIG_ENDIAN);
        bb.putShort((short) port);
        bb.position(0);

        byte[] portBytes = new byte[2];
        bb.get(portBytes);

        return String.format("227 Entering Passive Mode (%s,%d,%d)", address, (short) portBytes[0] & 0xFF, (short) portBytes[1] & 0xFF);
    }

    private String list(String pathname) throws IOException
    {
        if (pathname == null)
        {
            pathname = "/";
        }

        // Write the list via the data connection.
        boolean result = dataConnection.list(rootPath, currentPath, pathname, param -> {
            try
            {
                writeLine("266 Transfer complete.");
            }
            catch (IOException e)
            {
                return false;
            }

            return true;
        });

        if (result)
        {
            return String.format("150 Opening %s mode data transfer for LIST.", dataConnection.isPassive() ? "PASSIVE" : "ACTIVE");
        }
        else
        {
            return "450 Requested file action not taken.";
        }
    }

    private String extendedPort(String arguments) throws IOException
    {
        if (arguments == null)
        {
            return "501 Syntax error in parameters or arguments, no arguments provided.";
        }

        String[] args = arguments.split("\\|");

        // Skip 0 as that will be empty.
        int protocol = Integer.parseInt(args[1]);
        String address = args[2];
        int port = Integer.parseInt(args[3]);

        if (protocol != 1 && protocol != 2)
        {
            return "522 Network protocol not supported, use (1, 2).";
        }
        else if (port < 0 || port > Short.MAX_VALUE)
        {
            return "501 Syntax error in parameters or arguments, port is out of valid range (0 .. " + Short.MAX_VALUE + ").";
        }

        dataConnection = new FTPDataConnection(address, port);

        return "200 OK.";
    }

    private String extendedPassive(String arguments) throws IOException
    {
        int port = 0;
        if (arguments != null)
        {
            String[] args = arguments.split("\\|");

            // Skip 0 as that will be empty.
            port = Integer.parseInt(args[3]);

            if (port < 0 || port > Short.MAX_VALUE)
            {
                return "501 Syntax error in parameters or arguments, port is out of valid range (0 .. " + Short.MAX_VALUE + ").";
            }
        }

        String result = startPassive(port);
        String[] split = result.split("\\|", 2);

        return String.format("229 Entering Extended Passive Mode (||||%s|)", split[1]);
    }

    private String startPassive(int port) throws IOException
    {
        ServerSocket passiveListener = null;

        int localPort = -1;
        while (localPort < 1 || localPort > 32767)
        {
            if (passiveListener != null)
            {
                passiveListener.close();
            }

            passiveListener = new ServerSocket(port, 16, socket.getLocalAddress());
            localPort = passiveListener.getLocalPort();
        }

        dataConnection = new FTPDataConnection(passiveListener);

        final InetAddress localAddress = passiveListener.getInetAddress();
        return localAddress.getHostAddress() + '|' + localPort;
    }

    private static String changeDirectory(String currentPath, String path)
    {
        String[] parts = path.split("/");

        if (!path.startsWith("/"))
        {
            String[] oldParts = currentPath.split("/");
            parts = ArrayUtils.addAll(oldParts, parts);
        }

        String newPath = "";

        for (int i = parts.length - 1; i >= 0; i--)
        {
            String part = parts[i];
            if (part.equals(".."))
            {
                // Skip 1 more part.
                i--;
                continue;
            }

            // Skip empty parts.
            if (part.isEmpty())
            {
                continue;
            }

            newPath = part + "/" + newPath;
        }

        if (newPath.endsWith("/"))
        {
            newPath = newPath.substring(0, newPath.length() - 1);
        }

        return "/" + newPath;
    }
}
