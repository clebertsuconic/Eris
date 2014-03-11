package org.eris.transport.io;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;

import org.eris.transport.NetworkConnection;
import org.eris.transport.Receiver;
import org.eris.transport.Sender;
import org.eris.transport.TransportException;

public class IoNetworkConnection implements NetworkConnection<ByteBuffer>
{
    private ConnectionSettings _settings;
    private Socket _socket;
    private Receiver<ByteBuffer> _delegate;
    private IoReceiver _receiver;
    private IoSender _sender;
    
    public IoNetworkConnection(ConnectionSettings settings) throws TransportException
    {
        _settings = settings;
        try
        {
            _socket = new Socket();
            _socket.setReuseAddress(true);
            _socket.setTcpNoDelay(_settings.isTcpNodelay());
            _socket.setSendBufferSize(_settings.getWriteBufferSize());
            _socket.setReceiveBufferSize(_settings.getReadBufferSize());

        }
        catch (SocketException e)
        {
            throw new TransportException("Error creating socket", e);
        }
    }

    @Override
    public void connect() throws TransportException
    {
        if (_delegate == null)
        {
            throw new TransportException("A receiver needs to be set (using setReceiver) before connecting");
        }
        try
        {
            InetAddress address = InetAddress.getByName(_settings.getHost());
            _socket.connect(new InetSocketAddress(address, _settings.getPort()), _settings.getConnectTimeout());
        }
        catch (UnknownHostException e)
        {
            throw new TransportException("Error connecting to given host", e);
        }
        catch (IOException e)
        {
            throw new TransportException("IO error when connecting to peer", e);
        }
        _receiver = new IoReceiver(_socket, _delegate, _settings.getReadBufferSize(), _settings.getIdleTimeout());
        _sender = new IoSender(_socket, _settings.getWriteBufferSize(), _settings.getIdleTimeout());
    }

    @Override
    public void setReceiver(Receiver<ByteBuffer> receiver)
    {
        _delegate = receiver;
    }

    @Override
    public Sender<ByteBuffer> getSender()
    {
        return _sender;
    }

    @Override
    public void close()
    {
        // TODO Auto-generated method stub
    }
}
