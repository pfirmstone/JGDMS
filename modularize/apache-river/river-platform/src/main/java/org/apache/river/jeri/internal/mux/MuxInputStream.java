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
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.Deque;
import java.util.LinkedList;

/**
 * Output stream returned by OutboundRequests and InboundRequests for
 * a session of a multiplexed connection.
 */
class MuxInputStream extends InputStream {
    private final Object sessionLock;
    private final Session session;
    private final Mux mux;
    private final Deque<ByteBuffer> inBufQueue;
    private IOException sessionDown = null;
    private int inBufRemaining = 0;
    private int inBufPos = 0;
    private boolean inEOF = false;
    private boolean inClosed = false;
    private boolean sentAcknowledgment = false;

    MuxInputStream(Mux mux, Session session, Object sessionLock) {
        this.mux = mux;
        this.session = session;
        this.sessionLock = sessionLock;
        this.inBufQueue = new LinkedList<ByteBuffer>();
    }

    void down(IOException e) {
        sessionDown = e;
    }

    void appendToBufQueue(ByteBuffer data) {
        inBufQueue.addLast(data);
    }

    @Override
    public int read() throws IOException {
        synchronized (sessionLock) {
            if (inClosed) {
                throw new IOException("stream closed");
            }
            while (inBufRemaining == 0 && sessionDown == null && session.getInState() <= Session.OPEN && !inClosed) {
                if (session.getInState() == Session.IDLE) {
                    assert session.getOutState() == Session.IDLE;
                    mux.asyncSendData(Mux.Data | Mux.Data_open, session.sessionID, null);
                    session.setOutState(Session.OPEN);
                    session.setInState(Session.OPEN);
                }
                if (!session.inRationInfinite && session.getInRation() == 0) {
                    int inc = mux.initialInboundRation;
                    mux.asyncSendIncrementRation(session.sessionID, inc);
                    session.setInRation(session.getInRation() + inc);
                }
                try {
                    sessionLock.wait(5000L); // REMIND: timeout?
                } catch (InterruptedException e) {
                    String message = "request I/O interrupted";
                    session.setDown(message, e);
                    throw wrap(message, e);
                }
            }
            if (inClosed) {
                throw new IOException("stream closed");
            }
            if (inBufRemaining == 0) {
                if (inEOF) {
                    return -1;
                } else {
                    if (session.getInState() == Session.TERMINATED) {
                        throw new IOException("request aborted by remote endpoint");
                    }
                    assert sessionDown != null;
                    throw sessionDown;
                }
            }
            assert inBufQueue.size() > 0;
            int result = -1;
            while (result == -1) {
                ByteBuffer buf = (ByteBuffer) inBufQueue.getFirst();
                if (inBufPos < buf.limit()) {
                    result = (buf.get() & 0xFF);
                    inBufPos++;
                    inBufRemaining--;
                }
                if (inBufPos == buf.limit()) {
                    inBufQueue.removeFirst();
                    inBufPos = 0;
                }
            }
            if (!session.inRationInfinite) {
                checkInboundRation();
            }
            return result;
        }
    }

    private IOException wrap(String message, Exception e) {
        Throwable t;
        if (Session.traceSupression()) {
            t = e;
        } else {
            t = e.fillInStackTrace();
        }
        return new IOException(message, t);
    }

    @Override
    public int read(byte[] b, int off, int len) throws IOException {
        if (b == null) {
            throw new NullPointerException();
        } else if ((off < 0) || (off > b.length) || (len < 0) || ((off + len) > b.length) || ((off + len) < 0)) {
            throw new IndexOutOfBoundsException();
        }
        synchronized (sessionLock) {
            if (inClosed) {
                throw new IOException("stream closed");
            } else if (len == 0) {
                /*
                 * REMIND: What if
                 *     - stream is at EOF?
                 *     - session was aborted?
                 */
                return 0;
            }
            while (inBufRemaining == 0 && sessionDown == null && session.getInState() <= Session.OPEN && !inClosed) {
                if (session.getInState() == Session.IDLE) {
                    assert session.getOutState() == Session.IDLE;
                    mux.asyncSendData(Mux.Data | Mux.Data_open, session.sessionID, null);
                    session.setOutState(Session.OPEN);
                    session.setInState(Session.OPEN);
                }
                if (!session.inRationInfinite && session.getInRation() == 0) {
                    int inc = mux.initialInboundRation;
                    mux.asyncSendIncrementRation(session.sessionID, inc);
                    session.setInRation(session.getInRation() + inc);
                }
                try {
                    sessionLock.wait(5000L); // REMIND: timeout?
                } catch (InterruptedException e) {
                    String message = "request I/O interrupted";
                    session.setDown(message, e);
                    throw wrap(message, e);
                }
            }
            if (inClosed) {
                throw new IOException("stream closed");
            }
            if (inBufRemaining == 0) {
                if (inEOF) {
                    return -1;
                } else {
                    if (session.getInState() == Session.TERMINATED) {
                        throw new IOException("request aborted by remote endpoint");
                    }
                    assert sessionDown != null;
                    throw sessionDown;
                }
            }
            assert inBufQueue.size() > 0;
            int remaining = len;
            while (remaining > 0 && inBufRemaining > 0) {
                ByteBuffer buf = (ByteBuffer) inBufQueue.getFirst();
                if (inBufPos < buf.limit()) {
                    int toCopy = Math.min(buf.limit() - inBufPos, remaining);
                    buf.get(b, off, toCopy);
                    inBufPos += toCopy;
                    inBufRemaining -= toCopy;
                    off += toCopy;
                    remaining -= toCopy;
                }
                if (inBufPos == buf.limit()) {
                    inBufQueue.removeFirst();
                    inBufPos = 0;
                }
            }
            if (!session.inRationInfinite) {
                checkInboundRation();
            }
            return len - remaining;
        }
    }

    /**
     * Sends ration increment, if read drained buffers below
     * a certain mark.
     *
     * This method must NOT be invoked if the inbound ration in
     * infinite, and it must ONLY be invoked while synchronized on
     * this session's lock.
     *
     * REMIND: The implementation of this action will be a
     * significant area for performance tuning.
     */
    private void checkInboundRation() {
        assert Thread.holdsLock(sessionLock);
        assert !session.inRationInfinite;
        if (session.getInState() >= Session.FINISHED) {
            return;
        }
        int mark = mux.initialInboundRation / 2;
        int used = inBufRemaining + session.getInRation();
        if (used <= mark) {
            int inc = mux.initialInboundRation - used;
            mux.asyncSendIncrementRation(session.sessionID, inc);
            session.setInRation(session.getInRation() + inc);
        }
    }

    @Override
    public int available() throws IOException {
        synchronized (sessionLock) {
            if (inClosed) {
                throw new IOException("stream closed");
            }
            /*
             * REMIND: What if
             *     - stream is at EOF?
             *     - session was aborted?
             */
            return inBufRemaining;
        }
    }

    @Override
    public void close() {
        synchronized (sessionLock) {
            if (inClosed) {
                return;
            }
            inClosed = true;
            inBufQueue.clear(); // allow GC of unread data
            if (session.role == Session.CLIENT && !sentAcknowledgment && session.isReceivedAckRequired() && session.getOutState() < Session.TERMINATED) {
                mux.asyncSendAcknowledgment(session.sessionID);
                sentAcknowledgment = true;
                /*
                 * If removing this session from the connection's
                 * table was delayed in order to be able to send
                 * an Acknowledgment message, then take care of
                 * removing it now.
                 */
                if (session.isRemoveLater()) {
                    session.setOutState(Session.TERMINATED);
                    mux.removeSession(session.sessionID);
                    session.setRemoveLater(false);
                }
            }
            sessionLock.notifyAll();
        }
    }

    /**
     * @return the sentAcknowledgment
     */
    boolean isSentAcknowledgment() {
        return sentAcknowledgment;
    }

    /**
     * @return the inBufRemaining
     */
    int getBufRemaining() {
        return inBufRemaining;
    }

    /**
     * @return the inClosed
     */
    boolean isClosed() {
        return inClosed;
    }

    /**
     * @param inBufRemaining the inBufRemaining to set
     */
    void setBufRemaining(int inBufRemaining) {
        this.inBufRemaining = inBufRemaining;
    }

    /**
     * @param inEOF the inEOF to set
     */
    void setEOF(boolean inEOF) {
        this.inEOF = inEOF;
    }
    
}
