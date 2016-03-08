package com.haegroup.net;

/**
 * Created by william.connell on 01/02/2016.
 */
public enum FTPStatus
{
    RESTART_MARKER(110, "Restart marker replay . In this case, the text is exact and not left to the particular implementation; it must read: MARK yyyy = mmmm where yyyy is User-process data stream marker, and mmmm server's equivalent marker (note the spaces between markers and \"=\")."),
    SERVICE_READY(120, "Service ready in nnn minutes."),
    DATA_ALREADY_OPEN(125, "Data connection already open; transfer starting."),
    FILE_STATUS_OK(150, "File status okay; about to open data connection."),

    COMMAND_NOT_IMPLMENTED(202, "Command not implemented, superfluous at this site."),
    SYSTEM_STATUS(211, "System status, or system help reply."),
    DIRECTORY_STATUS(212, "Directory status."),
    FILE_STATUS(213, "File status."),
    HELP_MESSAGE(214, "Help message.On how to use the server or the meaning of a particular non-standard command. This reply is useful only to the human user."),
    NAME_SYSTEM_TYPE(215, "NAME system type. Where NAME is an official system name from the registry(http://www.iana.org/assignments/operating-system-names/operating-system-names.xhtml) kept by IANA."),
    READY_FOR_USER(220, "Service ready for new user."),
    CLOSING_CONTROL(221, "Service closing control connection."),
    DATA_OPEN(225, "Data connection open; no transfer in progress."),
    CLOSING_DATA(226, "Closing data connection. Requested file action successful (for example, file transfer or file abort)."),
    PASSIVE_MDOE(227, "Entering Passive Mode (h1,h2,h3,h4,p1,p2)."),
    PASSIVE_MODE_LONG(228, "Entering Long Passive Mode (long address, port)."),
    EX_PASSIVE_MODE(229, "Entering Extended Passive Mode (|||port|)."),
    LOGGED_IN(230, "User logged in, proceed. Logged out if appropriate."),
    LOGGED_OUT(231, "User logged out; service terminated."),
    LOGOUT_COMMAND(232, "Logout command noted, will complete when transfer done."),
    AUTH_ACCEPTED(234, "Specifies that the server accepts the authentication mechanism specified by the client, and the exchange of security data is complete. A higher level nonstandard code created by Microsoft."),
    FILE_ACTION_OK(250, "Requested file action okay, completed."),
    PATHNAME_CREATED(257, "\"PATHNAME\" created."),

    USER_OK(331, "User name okay, need password."),
    USER_NEEDED(332, "Need account for login."),
    FILE_ACTION_PENDING(350, "Requested file action pending further information."),

    SERVICE_NOT_AVAILABLE(421, "Service not available, closing control connection. This may be a reply to any command if the service knows it must shut down."),
    CANT_OPEN_DATA(425, "Can't open data connection."),
    CONNECTION_CLOSED(426, "Connection closed; transfer aborted."),
    INVLAID_USER_PASS(430, "Invalid username or password"),
    HOST_UNAVAILABLE(434, "Requested host unavailable."),
    FILE_ACTION_NOT_TAKEN(450, "Requested file action not taken."),
    ACTION_ABORTED(451, "Requested action aborted. Local error in processing."),
    ACTION_NOT_TAKEN(452, "Requested action not taken. Insufficient storage space in system.File unavailable (e.g., file busy)."),

    SYNTAX_ERROR(501, "Syntax error in parameters or arguments."),
    NOT_IMPLEMENTED(502, "Command not implemented."),
    BAD_COMMANDS(503, "Bad sequence of commands."),
    NOT_IMPLEMENTED_FOR_PARAM(504, "Command not implemented for that parameter."),
    NOT_LOGGED_IN(530, "Not logged in."),
    NEED_ACCOUNT(532, "Need account for storing files."),
    FILE_UNAVAILABLE(550, "Requested action not taken. File unavailable (e.g., file not found, no access)."),
    PAGE_TYPE_UNKNOWN(551, "Requested action aborted. Page type unknown."),
    EXCEEDED_STORAGE(552, "Requested file action aborted. Exceeded storage allocation (for current directory or dataset)."),
    NAME_NOT_ALLOWED(553, "Requested action not taken. File name not allowed."),

    INTEGRITY_PROTECTED(631, "Integrity protected reply."),
    CONFIDENTIALITY_INTEGRITY_PROTECTED(632, "Confidentiality and integrity protected reply."),
    CONFIDENTIALITY_PROTECTED(633, "Confidentiality protected reply."),

    CONNECTION_RESET(10054, "Connection reset by peer. The connection was forcibly closed by the remote host."),
    CANNOT_CONNECT_TO_REMOTE(10060, "Cannot connect to remote server."),
    CANNOT_CONNECT_TO_REMOTE_REFUSED(10061, "Cannot connect to remote server. The connection is actively refused by the server."),
    DIRECTORY_NOT_EMPTY(10066, "Directory not empty."),
    TOO_MANY_USERS(10068, "Too many users, server is full.");

    private final int code;

    private final String message;

    FTPStatus(int code, String message)
    {
        this.code = code;
        this.message = message;
    }

    public int getCode()
    {
        return code;
    }

    public String getMessage()
    {
        return message;
    }
}
