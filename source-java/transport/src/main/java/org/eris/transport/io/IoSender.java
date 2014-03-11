/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.eris.transport.io;

import java.io.IOException;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import org.eris.logging.Logger;
import org.eris.threading.Threading;
import org.eris.transport.Sender;
import org.eris.transport.SenderClosedException;
import org.eris.transport.SenderException;
import org.eris.transport.TransportException;


public final class IoSender implements Runnable, Sender<ByteBuffer>
{

    private static final Logger log = Logger.get(IoSender.class);

    // by starting here, we ensure that we always test the wraparound
    // case, we should probably make this configurable somehow so that
    // we can test other cases as well
    private final static int START = Integer.MAX_VALUE - 10;

    private final long timeout;
    private final Socket socket;
    private final OutputStream out;

    private final byte[] buffer;
    private volatile int head = START;
    private volatile int tail = START;
    private volatile boolean idle = true;
    private final Object notFull = new Object();
    private final Object notEmpty = new Object();
    private final AtomicBoolean closed = new AtomicBoolean(false);
    private final Thread senderThread;

    private volatile Throwable exception = null;

    public IoSender(Socket socket, int bufferSize, long timeout) throws TransportException
    {
        this.socket = socket;
        this.buffer = new byte[pof2(bufferSize)]; // buffer size must be a power of 2
        this.timeout = timeout;

        try
        {
            out = socket.getOutputStream();
        }
        catch (IOException e)
        {
            throw new TransportException("Error getting output stream for socket", e);
        }

        try
        {
            //Create but deliberately don't start the thread.
            senderThread = Threading.getThreadFactory().createThread(this);
        }
        catch(Exception e)
        {
            throw new Error("Error creating IOSender thread",e);
        }

        senderThread.setDaemon(true);
        senderThread.setName(String.format("IoSender - %s", socket.getRemoteSocketAddress()));
    }

    public void initiate()
    {
        senderThread.start();
    }

    private static final int pof2(int n)
    {
        int result = 1;
        while (result < n)
        {
            result *= 2;
        }
        return result;
    }

    public void send(ByteBuffer buf) throws TransportException
    {
        if (closed.get())
        {
            throw new SenderClosedException("sender is closed", exception);
        }
        if(!senderThread.isAlive())
        {
            throw new SenderException("sender thread not alive");
        }

        final int size = buffer.length;
        int remaining = buf.remaining();

        while (remaining > 0)
        {
            final int hd = head;
            final int tl = tail;

            if (hd - tl >= size)
            {
                flush();
                synchronized (notFull)
                {
                    long start = System.currentTimeMillis();
                    long elapsed = 0;
                    while (!closed.get() && head - tail >= size && elapsed < timeout)
                    {
                        try
                        {
                            notFull.wait(timeout - elapsed);
                        }
                        catch (InterruptedException e)
                        {
                            // pass
                        }
                        elapsed += System.currentTimeMillis() - start;
                    }

                    if (closed.get())
                    {
                        throw new SenderClosedException("sender is closed", exception);
                    }

                    if (head - tail >= size)
                    {
                        throw new SenderException(String.format("write timed out: %s, %s", head, tail));
                    }
                }
                continue;
            }

            final int hd_idx = mod(hd, size);
            final int tl_idx = mod(tl, size);
            final int length;

            if (tl_idx > hd_idx)
            {
                length = Math.min(tl_idx - hd_idx, remaining);
            }
            else
            {
                length = Math.min(size - hd_idx, remaining);
            }

            buf.get(buffer, hd_idx, length);
            head += length;
            remaining -= length;
        }
    }

    public void flush()
    {
        if (idle)
        {
            synchronized (notEmpty)
            {
                notEmpty.notify();
            }
        }
    }

    public void close() throws TransportException
    {
        close(true);
    }

    void close(boolean reportException) throws TransportException
    {
        if (!closed.getAndSet(true))
        {
            synchronized (notFull)
            {
                notFull.notify();
            }

            synchronized (notEmpty)
            {
                notEmpty.notify();
            }

            try
            {
                if (Thread.currentThread() != senderThread)
                {
                    senderThread.join(timeout);
                    if (senderThread.isAlive())
                    {
                        log.error("join timed out");
                        throw new SenderException("join timed out");
                    }
                }
            }
            catch (InterruptedException e)
            {
                log.error("interrupted whilst waiting for sender thread to stop");
                throw new SenderException("interrupted whilst waiting for sender thread to stop",e);
            }
            if (reportException && exception != null)
            {
                throw new SenderException("Exception during close",exception);
            }
        }
    }

    public void run()
    {
        final int size = buffer.length;
        while (true)
        {
            final int hd = head;
            final int tl = tail;

            if (hd == tl)
            {
                if (closed.get())
                {
                    break;
                }

                idle = true;

                synchronized (notEmpty)
                {
                    while (head == tail && !closed.get())
                    {
                        try
                        {
                            notEmpty.wait();
                        }
                        catch (InterruptedException e)
                        {
                            // pass
                        }
                    }
                }

                idle = false;

                continue;
            }

            final int hd_idx = mod(hd, size);
            final int tl_idx = mod(tl, size);

            final int length;
            if (tl_idx < hd_idx)
            {
                length = hd_idx - tl_idx;
            }
            else
            {
                length = size - tl_idx;
            }

            try
            {
                out.write(buffer, tl_idx, length);
            }
            catch (IOException e)
            {
                log.error(e, "error in write thread");
                exception = e;
                try
                {
                    close(false);
                }
                catch (Exception ex)
                {
                    log.error(ex, "Error while closing");
                }
                break;
            }
            tail += length;
            if (head - tl >= size)
            {
                synchronized (notFull)
                {
                    notFull.notify();
                }
            }
        }
    }

    public void setIdleTimeout(int i) throws TransportException
    {
        try
        {
            socket.setSoTimeout(i);
        }
        catch (Exception e)
        {
            throw new TransportException("Error setting idle timeout",e);
        }
    }

    private int mod(int n, int m)
    {
        int r = n % m;
        return r < 0 ? m + r : r;
    }
}
