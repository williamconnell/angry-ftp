package com.haegroup;

import com.haegroup.net.FTPServer;

import java.io.IOException;

/**
 * Created by William Connell on 29/01/2016.
 */
public class AngryFTP
{
    public static void main(String[] args)
    {
        FTPServer server = new FTPServer("D:/watch");

        try
        {
            server.start();
        }
        catch (IOException e)
        {
            e.printStackTrace();
        }
    }
}
