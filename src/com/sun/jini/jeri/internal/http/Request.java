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

package com.sun.jini.jeri.internal.http;

import com.sun.jini.thread.Executor;
import com.sun.jini.thread.GetThreadPoolAction;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import net.jini.io.context.AcknowledgmentSource;

/**
 * Superclass for OutboundRequest, InboundRequest implementation classes.
 *
 * @author Sun Microsystems, Inc.
 * 
 */
abstract class Request {

    private static final Executor systemThreadPool = (Executor)
	java.security.AccessController.doPrivileged(
	    new GetThreadPoolAction(false));

    /* stream states */
    private static final int UNUSED   = 0;
    private static final int OPEN     = 1;
    private static final int EOF      = 2;
    private static final int CLOSED   = 3;
    private static final int INVALID  = 4;
    
    private final Object stateLock = new Object();
    private final Object outLock = new Object();
    private final Object inLock = new Object();
    private final ContentOutputStream out;
    private final ContentInputStream in;

    private boolean aborted = false;
    private int outState = UNUSED;
    private int inState = UNUSED;
    private IOException outException;
    private IOException inException;

    /**
     * Creates new Request, initializes content input/output streams.
     */
    Request() {
	out = new ContentOutputStream();
	in = new ContentInputStream();
    }

    /**
     * Returns true if any data sent.
     */
    public boolean getDeliveryStatus() {
	synchronized (stateLock) {
	    return outState > UNUSED;
	}
    }

    /**
     * Terminates request.
     */
    public void abort() {
	synchronized (stateLock) {
	    if (aborted || (outState >= CLOSED && inState >= EOF)) {
		return;
	    }
	    aborted = true;
	}
	Runnable reaper = new Runnable() { public void run() { finish(); } };
	systemThreadPool.execute(reaper, "Request reaper");
    }
    
    /**
     * Finishes request, if not finished or aborted already.  Returns once
     * request is finished.
     */
    void finish() {
	try { out.close(false); } catch (Throwable th) {}
	try { in.close(false); } catch (Throwable th) {}
    }
    
    /**
     * Returns OutputStream used for writing outbound request/response data.
     */
    OutputStream getOutputStream() {
	return out;
    }
    
    /**
     * Returns InputStream used for reading inbound request/response data.
     */
    InputStream getInputStream() {
	return in;
    }

    /**
     * Method called internally before any outbound data is written.
     */
    abstract void startOutput() throws IOException;
    
    /**
     * Method called internally to write outbound request/response data.
     */
    abstract void write(byte[] b, int off, int len) throws IOException;
    
    /**
     * Method called internally to signal the end of outbound data.
     */
    abstract void endOutput() throws IOException;
    
    /**
     * Method called internally before any inbound data is read.  Returns true
     * if inbound data is valid, false otherwise (e.g., error message content).
     */
    abstract boolean startInput() throws IOException;
    
    /**
     * Method called internally to read inbound request/response data.
     */
    abstract int read(byte[] b, int off, int len) throws IOException;
    
    /**
     * Method called internally to gauge available inbound data.
     */
    abstract int available() throws IOException;
    
    /**
     * Method called internally when finished reading inbound data.
     */
    abstract void endInput() throws IOException;
    
    /**
     * Method called internally to register acknowledgment listener.
     */
    abstract void addAckListener(AcknowledgmentSource.Listener listener);
    
    /**
     * Method called internally when request is finished.  If corrupt is true,
     * the underlying transport channel has been left in an unknown state.
     */
    abstract void done(boolean corrupt);
    
    /**
     * Method called internally to implement AcknowledgmentSource;
     * checks state and delegates to addAckListener method.
     */
    final boolean addAcknowledgmentListener(AcknowledgmentSource.Listener l) {
	synchronized (outLock) {
	    synchronized (stateLock) {
		if (aborted || outState >= CLOSED) {
		    return false;
		}
	    }
	    addAckListener(l);
	}
	return true;
    }
    
    /**
     * Stream for writing outbound request/response data.
     */
    private class ContentOutputStream extends OutputStream {

	public void write(int b) throws IOException {
	    write(new byte[] { (byte) b }, 0, 1);
	}
	
	public void write(byte[] b, int off, int len) throws IOException {
	    synchronized (outLock) {
		checkOpen();
		try {
		    Request.this.write(b, off, len);
		} catch (Throwable th) {
		    invalidate(th);
		}
	    }
	}
	
	public void flush() throws IOException {
	    /*
	     * REMIND: Upper layers will tend to invoke flush even though there
	     * is no need for it in RMI's protocol, so sending data in that
	     * event seems undesirable; also, we have no need to indicate a
	     * "push" to the remote endpoint.  For now, we just ignore flush()
	     * invocations.
	     */
	    synchronized (outLock) {
		checkOpen();
	    }
	}
	
	public void close() throws IOException {
	    close(true);
	}
	
	/**
	 * Checks to make sure stream is open and startOutput has been called.
	 * Assumes caller already possesses outLock monitor.
	 */
	private void checkOpen() throws IOException {
	    synchronized (stateLock) {
		if (aborted) {
		    throw new IOException("request aborted");
		} 
		switch (outState) {
		    case OPEN:
			return;
		    case CLOSED:
			throw new IOException("stream closed");
		    case INVALID:
			throw outException;
		}
		outState = OPEN;
	    }
	    try {
		startOutput();
	    } catch (Throwable th) {
		invalidate(th);
	    }
	}
	
	/**
	 * Attempts to close stream.  If checkAbort is true and the request has
	 * been aborted, close will fail with an IOException.
	 */
	private void close(boolean checkAbort) throws IOException {
	    synchronized (outLock) {
		synchronized (stateLock) {
		    if (checkAbort && aborted) {
			throw new IOException("request aborted");
		    } else if (outState == CLOSED) {
			return;
		    } else if (outState == INVALID) {
			throw outException;
		    }
		}
		try {
		    if (outState == UNUSED) {
			startOutput();
		    }
		    endOutput();
		} catch (Throwable th) {
		    invalidate(th);
		}
		synchronized (stateLock) {
		    outState = CLOSED;
		    if (inState >= EOF) {
			done(inState == INVALID);
		    }
		}
	    }
	}
	
	/**
	 * Invalidates stream, saving cause.  If other stream is done, finishes
	 * request.  Assumes caller already possesses outLock monitor.
	 */
	private void invalidate(Throwable cause) throws IOException {
	    synchronized (stateLock) {
		if (outState != INVALID) {
		    if (outState <= OPEN && inState >= EOF) {
			done(true);
		    }
		    outState = INVALID;
		    outException = new IOException("stream invalid");
		    outException.initCause(cause);
		}
		throw outException;
	    }
	}
    }

    /**
     * Stream for reading inbound request/response data.
     */
    private class ContentInputStream extends InputStream {
	
	public int read() throws IOException {
	    byte[] b = new byte[1];
	    return (read(b, 0, 1) != -1) ? b[0] & 0xFF : -1;
	}
	
	public int read(byte[] b, int off, int len) throws IOException {
	    synchronized (inLock) {
		if (!checkOpen()) {
		    return -1;
		}
		try {
		    int n = Request.this.read(b, off, len);
		    if (n == -1) {
			endInput();
			synchronized (stateLock) {
			    inState = EOF;
			    if (outState >= CLOSED) {
				done(outState == INVALID);
			    }
			}
		    }
		    return n;
		} catch (Throwable th) {
		    invalidate(th);
		    throw new InternalError();		// unreached
		}
	    }
	}
	
	public int available() throws IOException {
	    synchronized (inLock) {
		if (!checkOpen()) {
		    return 0;
		}
		try {
		    return Request.this.available();
		} catch (Throwable th) {
		    invalidate(th);
		    throw new InternalError();		// unreached
		}
	    }
	}
	
	public void close() throws IOException {
	    close(true);
	}
	
	/**
	 * Checks to make sure stream is open and startInput has been called.
	 * Returns false if EOF has been reached, true otherwise.  Assumes
	 * caller already possesses inLock monitor.
	 */
	private boolean checkOpen() throws IOException {
	    synchronized (stateLock) {
		if (aborted) {
		    throw new IOException("request aborted");
		} 
		switch (inState) {
		    case OPEN:
			return true;
		    case EOF:
			return false;
		    case CLOSED:
			throw new IOException("stream closed");
		    case INVALID:
			throw inException;
		}
		inState = OPEN;
	    }
	    try {
		if (startInput()) {
		    return true;
		} else {
		    endInput();
		    synchronized (stateLock) {
			inState = EOF;
			if (outState >= CLOSED) {
			    done(outState == INVALID);
			}
		    }
		    return false;
		}
	    } catch (Throwable th) {
		invalidate(th);
		throw new InternalError();		// unreached
	    }
	}
	
	/**
	 * Attempts to close stream.  If checkAbort is true and the request has
	 * been aborted, close will fail with an IOException.
	 */
	private void close(boolean checkAbort) throws IOException {
	    synchronized (inLock) {
		synchronized (stateLock) {
		    if (checkAbort && aborted) {
			throw new IOException("request aborted");
		    }
		    switch (inState) {
			case EOF:
			    inState = CLOSED;
			case CLOSED:
			    return;
			case INVALID:
			    throw inException;
		    }
		}
		try {
		    if (inState == UNUSED) {
			startInput();
		    }
		    endInput();
		} catch (Throwable th) {
		    invalidate(th);
		}
		synchronized (stateLock) {
		    inState = CLOSED;
		    if (outState >= CLOSED) {
			done(outState == INVALID);
		    }
		}
	    }
	}
	
	/**
	 * Invalidates stream, saving cause.  If other stream is done, finishes
	 * request.  Assumes caller already possesses inLock monitor.
	 */
	private void invalidate(Throwable cause) throws IOException {
	    synchronized (stateLock) {
		if (inState != INVALID) {
		    if (inState <= OPEN && outState >= CLOSED) {
			done(true);
		    }
		    inState = INVALID;
		    inException = new IOException("stream invalid");
		    inException.initCause(cause);
		}
		throw inException;
	    }
	}
    }
}
