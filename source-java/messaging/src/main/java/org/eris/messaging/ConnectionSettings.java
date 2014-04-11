package org.eris.messaging;

public abstract class ConnectionSettings
{
    protected String _scheme = "amqp";
    
    protected String _host = "localhost";

    protected int _port = 5672;

    protected String _user = "";

    protected String _pass = "";
    
    protected boolean _tcpNodelay = false;

    protected int _readBufferSize = 65535;

    protected int _writeBufferSize = 65535;

    protected int _connectTimeout = 60000;

    protected long _idleTimeout = 60000;

    public String getHost()
    {
        return _host;
    }

    public int getPort()
    {
        return _port;
    }

    public boolean isTcpNodelay()
    {
        return _tcpNodelay;
    }

    public int getReadBufferSize()
    {
        return _readBufferSize;
    }

    public int getWriteBufferSize()
    {
        return _writeBufferSize;
    }

    public int getConnectTimeout()
    {
        return _connectTimeout;
    }

    public long getIdleTimeout()
    {
        return _idleTimeout;
    }
}
