/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.river.jeri.internal.mux;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.util.logging.Level;

/**
 * Output stream returned by OutboundRequests and InboundRequests for
 * a session of a multiplexed connection.
 */
class MuxOutputStream extends OutputStream {
    private final ByteBuffer buffer;
    private final Object sessionLock;
    private final Session session;
    private final Mux mux;
    private boolean fakeOKtoWrite = false; // REMIND
    private IOException sessionDown = null;

    MuxOutputStream(Mux mux, Session session, Object sessionLock) {
        this.sessionLock = sessionLock;
        this.session = session;
        this.mux = mux;
        this.buffer = mux.directBuffersUseful() 
                ? ByteBuffer.allocateDirect(mux.maxFragmentSize) 
                : ByteBuffer.allocate(mux.maxFragmentSize);
    }

    void abort() {
        fakeOKtoWrite = false;
    }

    void handleClose() {
        fakeOKtoWrite = true;
    }

    void down(IOException e) {
        sessionDown = e;
    }

    @Override
    public void write(int b) throws IOException {
        if (!buffer.hasRemaining()) {
            writeBuffer(false);
        } else {
            synchronized (sessionLock) {
                // REMIND: necessary?
                ensureOpen();
            }
        }
        buffer.put((byte) b);
    }

    @Override
    public void write(byte[] b, int off, int len) throws IOException {
        if (b == null) {
            throw new NullPointerException();
        } else if ((off < 0) || (off > b.length) || (len < 0) || ((off + len) > b.length) || ((off + len) < 0)) {
            throw new IndexOutOfBoundsException();
        } else if (len == 0) {
            synchronized (sessionLock) {
                ensureOpen();
            }
            return;
        }
        while (len > 0) {
            int avail = buffer.remaining();
            if (len <= avail) {
                synchronized (sessionLock) {
                    ensureOpen();
                }
                buffer.put(b, off, len);
                return;
            }
            buffer.put(b, off, avail);
            off += avail;
            len -= avail;
            writeBuffer(false);
        }
    }

    /** Flush method causes deadlock */
//    @Override
//    public void flush() throws IOException {
//        synchronized (sessionLock) {
//            ensureOpen();
//        }
//        while (buffer.hasRemaining()) {
//            writeBuffer(false);
//        }
//    }

    @Override
    public void close() throws IOException {
        if (Session.logger.isLoggable(Level.FINEST)) {
            Session.logger.log(Level.FINEST, "STACK TRACE", new Throwable("STACK TRACE"));
        }
        synchronized (sessionLock) {
            ensureOpen();
        }
        while (!writeBuffer(true)) {
        }
    }

    /**
     *
     * This method must ONLY be invoked while synchronized on
     * this session's lock.
     */
    private void ensureOpen() throws IOException {
        assert Thread.holdsLock(sessionLock);
        /*
         * While we're faking that the session is still OK when it really
         * isn't (see above comments), return silently from here.
         */
        if (fakeOKtoWrite) {
            return;
        }
        int outState = session.getOutState();
        if (outState > Session.OPEN) {
            if (outState == Session.FINISHED) {
                throw new IOException("stream closed");
            } else {
                throw new IOException("session terminated");
            }
        } else if (sessionDown != null) {
            throw sessionDown;
        }
    }

    /**
     * Writes as much of the contents of this stream's output buffer
     * as is allowed by the current output ration.  Upon normal return,
     * at least one byte will have been transferred from the buffer to
     * the multiplexed connection output queue, and the buffer will have
     * been compacted, ready to be filled at the current position.
     *
     * Returns true if closeIfComplete and session was marked EOF (with
     * complete buffer written); if true, stream's output buffer should
     * no longer be accessed (because this method will not wait for
     * actual writing of the message).
     */
    private boolean writeBuffer(boolean closeIfComplete) throws IOException {
        boolean hasData;
        int origLimit;
        buffer.flip();
        origLimit = buffer.limit();
        int toSend;
        IOFuture future = null;
        boolean eofSent = false;
        synchronized (sessionLock) {
            while (buffer.remaining() > 0 
                    && !session.outRationInfinite 
                    && session.getOutRation() < 1 
                    && sessionDown == null 
                    && session.getOutState() == Session.OPEN) 
            {
                try {
                    sessionLock.wait(); // REMIND: timeout?
                } catch (InterruptedException e) {
                    String message = "request I/O interrupted";
                    session.setDown(message, e);
                    throw new IOException(message, e);
                }
            }
            ensureOpen();
            assert buffer.remaining() == 0 || session.outRationInfinite || session.getOutRation() > 0 || fakeOKtoWrite;
            /*
             * If we're just faking that the session is OK when it really
             * isn't, then we need to stop the writing from proceeding
             * past this barrier-- and if a close was requested, then
             * satisfy it right away.
             */
            if (fakeOKtoWrite) {
                assert session.role == Session.CLIENT 
                        && session.getInState() == Session.TERMINATED;
                if (closeIfComplete) fakeOKtoWrite = false;
                buffer.position(origLimit);
                buffer.compact();
                return closeIfComplete;
            }
            boolean complete;
            if (session.outRationInfinite || buffer.remaining() <= session.getOutRation()) {
                toSend = buffer.remaining();
                complete = true;
            } else {
                toSend = session.getOutRation();
                buffer.limit(toSend);
                complete = false;
            }
            if (!session.outRationInfinite) {
                session.setOutRation(session.getOutRation() - toSend);
            }
            session.setPartialDeliveryStatus(true);
            boolean open = session.getOutState() == Session.IDLE;
            boolean eof = closeIfComplete && complete;
            boolean close = session.role == Session.SERVER && eof 
                    && session.getInState() > Session.OPEN;
            boolean ackRequired = session.role == Session.SERVER 
                    && eof && session.ackListeners();
            int op = Mux.Data | (open ? Mux.Data_open : 0) 
                    | (eof ? Mux.Data_eof : 0) | (close ? Mux.Data_close : 0)
                    | (ackRequired ? Mux.Data_ackRequired : 0);
            /*
             * If we are the server-side, send even the final Data message
             * for this session synchronously with this method, so that the
             * VM will not exit before it gets delivered.  Otherwise, let
             * final Data messages (those with eof true) be sent after this
             * method completes.
             *
             * Buffers are duplicated to avoid a data race that occurred in
             * StreamConnectionIO.  IOFuture now provides the buffer's position
             * after sending.
             */
            if (!eof || session.role == Session.SERVER) {
                future = mux.futureSendData(op, session.sessionID, buffer.duplicate());
            } else {
                mux.asyncSendData(op, session.sessionID, buffer.duplicate());
            }

            if (session.getOutState() == Session.IDLE) {
                session.setOutState(Session.OPEN);
                session.setInState(Session.OPEN);
            }
            if (eof) {
                eofSent = true;
                session.setOutState(close ? Session.TERMINATED : Session.FINISHED);
                if (ackRequired) {
                    session.setSentAckRequired(true);
                }
                sessionLock.notifyAll();
            }
        }
        if (future != null) {
            /* StreamConnectionIO uses a dedicated thread for sending buffers, 
             * but synchronization is only used to get buffers for processing,
             * no locks are held while sending, for this reason the state of the
             * buffers position must be obtained from IOFuture, previously, 
             * reading buffer position depended on a data race.
             */
            hasData = waitForIO(future);
            if (hasData) {
                buffer.position(future.getPosition()).limit(origLimit);
                buffer.compact();
            } else {
                buffer.clear();
            }
        } else {
            buffer.clear();
        }
        return eofSent;
    }

    /**
     *
     * This method must NOT be invoked while synchronized on
     * this session's lock.
     */
    private boolean waitForIO(IOFuture future) throws IOException {
        assert !Thread.holdsLock(sessionLock);
        try {
            return future.waitUntilDone();
        } catch (InterruptedException e) {
            String message = "request I/O interrupted";
            session.setDown(message, e);
            throw new IOException(message, e);
        } catch (IOException e) {
            session.setDown(e.getMessage(), e.getCause());
            throw e;
        }
    }
    
}
