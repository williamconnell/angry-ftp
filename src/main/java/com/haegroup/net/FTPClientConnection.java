package com.haegroup.net;

import org.apache.commons.lang3.ArrayUtils;

import java.io.*;
import java.net.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;

/**
 * Created by William Connell on 29/01/2016.
 */
public class FTPClientConnection
{
    private final Socket commandClient;
    private ServerSocket passiveSocket;
    private String dataClientAddress;
    private int dataClientPort;

    private boolean isPassive = false;

    private final InputStream commandInputStream;
    private final OutputStream commandOutputStream;

    private String username;

    private final String rootPath;
    private String currentPath;

    public FTPClientConnection(Socket commandClient) throws IOException
    {
        this.commandClient = commandClient;

        this.commandInputStream = commandClient.getInputStream();
        this.commandOutputStream = commandClient.getOutputStream();

        // Store the paths.
        this.rootPath = new File(".").getCanonicalPath().replace('\\', '/');
        this.currentPath = "/";
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

                    default:
                        response = "502 Command not implemented.";
                        break;
                }

                if (commandClient == null || !commandClient.isConnected())
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
                }
            }
        }
        catch (Exception e)
        {
            e.printStackTrace();
            throw e;
        }
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
        String newPath = concatDirectory(currentPath, path);

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

        // Wrap the ByteBuffer.
        ByteBuffer bb = ByteBuffer.wrap(new byte[]{Byte.parseByte(args[4]), Byte.parseByte(args[5])});
        bb.order(ByteOrder.BIG_ENDIAN);

        int port = bb.getInt();

        return extendedPort(String.format("|%d|%s|%d|", 1, address, port));
    }

    private String passive(String arguments) throws IOException
    {
        String result = startPassive(0);

        String[] split = result.split("\\|", 2);

        String address = split[0].replace('.', ',').replace("/", "");
        int port = Integer.parseInt(split[1]);

        ByteBuffer bb = ByteBuffer.allocate(2);
        bb.order(ByteOrder.BIG_ENDIAN);
        bb.putShort((short) port);
        bb.position(0);

        byte[] portBytes = new byte[2];
        bb.get(portBytes);

        return String.format("227 Entering Passive Mode (%s,%d,%d)", address, portBytes[0], portBytes[1]);
    }

    private String list(String pathname) throws IOException
    {
        final DateTimeFormatter format1 = DateTimeFormatter.ofPattern("MMM dd  yyyy");
        final DateTimeFormatter format2 = DateTimeFormatter.ofPattern("MMM dd HH:mm");

        if (pathname == null)
        {
            pathname = "/";
        }

        // Remove any windows slashes.
        pathname = pathname.replace('\\', '/');
        Path path = Paths.get(rootPath, currentPath, pathname);

        if (Files.exists(path) && Files.isDirectory(path))
        {
            final Socket dataClient;
            if (isPassive)
            {
                dataClient = passiveSocket.accept();
            }
            else
            {
                dataClient = new Socket(dataClientAddress, dataClientPort);
            }

            if (dataClient.isConnected())
            {
                new Thread(() -> {
                    try
                    {
                        final OutputStream dataOutputStream = dataClient.getOutputStream();

                        // List all of the files in the directory.
                        File[] files = path.toFile().listFiles();
                        for (File file : files)
                        {
                            String descriptor = file.isDirectory() ? "drwxr-xr-x" : "-rwxr-xr-x";

                            LocalDateTime lastModified = LocalDateTime.ofEpochSecond(file.lastModified(), 0, ZoneOffset.UTC);

                            String date = lastModified.compareTo(LocalDateTime.now().minusDays(180)) < 0 ?
                                    lastModified.format(format1) : lastModified.format(format2);

                            String line = String.format("%s   1 %-10s %-10s %10d %s %s", descriptor, "temp", "temp", file.length(), date, file.getName());
                            writeLine(dataOutputStream, line);
                        }

                        if (isPassive)
                        {
                            dataClient.close();
                        }

                        writeLine("266 Transfer complete.");
                    }
                    catch (IOException e)
                    {
                        e.printStackTrace();
                    }
                }, "TEMP THREAD").start();
            }

            return String.format("150 Opening %s mode data transfer for LIST.", isPassive ? "PASSIVE" : "ACTIVE");
        }

        return "450 Requested file action not taken.";
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

        dataClientAddress = address;
        dataClientPort = port;

        isPassive = false;

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
//        InetAddress address = commandClient.getInetAddress();

        if (passiveSocket != null)
        {
            try
            {
                passiveSocket.close();
            }
            catch (IOException ignored) { }
        }

        // Open a passive listener.
        passiveSocket = new ServerSocket(port);

        InetAddress localSocketAddress = commandClient.getInetAddress();
        int localPort = passiveSocket.getLocalPort();

        isPassive = true;

        return localSocketAddress.toString() + '|' + localPort;
    }

    private static String concatDirectory(String currentPath, String path)
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
