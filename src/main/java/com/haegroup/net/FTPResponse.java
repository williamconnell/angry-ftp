package com.haegroup.net;

/**
 * Created by William Connell on 01/02/2016.
 */
public class FTPResponse
{
    private final int statusCode;
    private final String message;

    public FTPResponse(FTPStatus status)
    {
        this(status, status.getMessage());
    }

    public FTPResponse(FTPStatus status, String message)
    {
        this(status.getCode(), message);
    }

    public FTPResponse(int statusCode, String message)
    {
        this.statusCode = statusCode;
        this.message = message;
    }

    public int getStatusCode()
    {
        return statusCode;
    }

    public String getMessage()
    {
        return message;
    }
}
