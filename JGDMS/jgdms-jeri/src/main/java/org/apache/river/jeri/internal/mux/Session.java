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

import org.apache.river.thread.Executor;
import org.apache.river.thread.GetThreadPoolAction;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.ArrayList;
import java.util.Collection;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.jini.core.constraint.InvocationConstraints;
import net.jini.io.UnsupportedConstraintException;
import net.jini.io.context.AcknowledgmentSource;
import net.jini.jeri.InboundRequest;
import net.jini.jeri.OutboundRequest;

/**
 * A Session represents a single session of a multiplexed connection,
 * for either client-side and server-side perspective.  The particular
 * role (CLIENT or SERVER) is indicated at construction time.
 *
 * @author Sun Microsystems, Inc.
 **/
final class Session {

    static final int CLIENT = 0;
    static final int SERVER = 1;

    static final int IDLE	= 0;
    static final int OPEN	= 1;
    static final int FINISHED	= 2;
    static final int TERMINATED	= 3;
    private static final String[] stateNames = {
	"idle", "open", "finished", "terminated"
    };
   
    /** 
     * This method prevents a SecurityException from being thrown for
     * a client proxy that doesn't have permission to read the property.
     * When this is the case, the secure trace supression option
     * is chosen.
     * This is not optimised, because exception conditions
     * are exceptional.
     */
    static boolean traceSupression(){
        try {
            return AccessController.doPrivileged(
                new PrivilegedAction<Boolean>() 
                {
                    @Override
                    public Boolean run() {
                        return Boolean.getBoolean("org.apache.river.jeri.server.suppressStackTraces");
                    }
                }
            );
        } catch (SecurityException e) {
            return true;
        }
    }

    /**
     * pool of threads for executing tasks in system thread group: used for
     * I/O (reader and writer) threads and other asynchronous tasks
     **/
    private static final Executor systemThreadPool =
	(Executor) AccessController.doPrivileged(
	    new GetThreadPoolAction(false));

    /** mux logger */
    static final Logger logger =
	Logger.getLogger("net.jini.jeri.connection.mux");

    private final Mux mux;
    final int sessionID;
    final int role;

    private final MuxOutputStream out;
    private final MuxInputStream in;

    /** lock guarding all mutable instance state (below) */
    private final Object sessionLock;
    private boolean sessionDown;

    private int outState;
    private int outRation;
    final boolean outRationInfinite;
    private boolean partialDeliveryStatus;

    private int inState;
    private int inRation;
    final boolean inRationInfinite;
		
    private boolean removeLater;		// REMIND

    private boolean receivedAckRequired;

    private final Collection<AcknowledgmentSource.Listener> ackListeners;
    private boolean sentAckRequired;
    private boolean receivedAcknowledgment;

    /**
     *
     */
    Session(Mux mux, int sessionID, int role) {
        this.receivedAcknowledgment = false;
        this.sentAckRequired = false;
        this.receivedAckRequired = false;
        this.removeLater = false;
        this.partialDeliveryStatus = false;
        this.sessionDown = false;
        this.ackListeners = new ArrayList<AcknowledgmentSource.Listener>(3);
        this.sessionLock = new Object();
	this.mux = mux;
	this.sessionID = sessionID;
	this.role = role;
        
	outState = (role == CLIENT ? IDLE : OPEN);
	outRation = mux.initialOutboundRation;
	outRationInfinite = (outRation == 0);

	inState = (role == CLIENT ? IDLE : OPEN);
	inRation = mux.initialInboundRation;
	inRationInfinite = (inRation == 0);
        out = new MuxOutputStream(mux, this, sessionLock);
	in = new MuxInputStream(mux, this, sessionLock);
    }

    /**
     *
     */
    OutboundRequest getOutboundRequest() {
	assert role == CLIENT;
	return new OutboundRequest() {
            @Override
	    public void populateContext(Collection context) {
		((MuxClient) mux).populateContext(context);
	    }
            @Override
	    public InvocationConstraints getUnfulfilledConstraints() {
		/*
		 * NYI: We currently have no request-specific hook
		 * back to the transport implementation, so we must
		 * depend on OutboundRequest wrapping for this method.
		 */
		throw new AssertionError();
	    }
            @Override
	    public OutputStream getRequestOutputStream() { return out; }
            @Override
	    public InputStream getResponseInputStream() { return in; }
            @Override
	    public boolean getDeliveryStatus() {
		synchronized (sessionLock) {
		    return partialDeliveryStatus;
		}
	    }
            @Override
	    public void abort() { Session.this.abort(); }
	};
    }

    /**
     *
     */
    InboundRequest getInboundRequest() {
	assert role == SERVER;
	return new InboundRequest() {
            @Override
	    public void checkPermissions() {
		((MuxServer) mux).checkPermissions();
	    }
            @Override
	    public InvocationConstraints
		checkConstraints(InvocationConstraints constraints)
		throws UnsupportedConstraintException
	    {
		return ((MuxServer) mux).checkConstraints(constraints);
	    }
            @Override
	    public void populateContext(Collection context) {
		context.add(new AcknowledgmentSource() {
                    @Override
		    public boolean addAcknowledgmentListener(
			AcknowledgmentSource.Listener listener)
		    {
			if (listener == null) {
			    throw new NullPointerException();
			}
			synchronized (sessionLock) {
			    if (getOutState() < FINISHED) {
				ackListeners.add(listener);
				return true;
			    } else {
				return false;
			    }
			}
		    }
		});
		((MuxServer) mux).populateContext(context);
	    }
            @Override
	    public InputStream getRequestInputStream() { return in; }
            @Override
	    public OutputStream getResponseOutputStream() { return out; }
            @Override
	    public void abort() { Session.this.abort(); }
	};
    }

    /**
     *
     */
    void abort() {
	synchronized (sessionLock) {
	    if (!sessionDown) {
		if (logger.isLoggable(Level.FINEST)) {
		    logger.log(Level.FINEST,
                            "outState={0},inState={1},role={2}",
                            new Object[]{stateNames[getOutState()],
                                stateNames[inState],
                                role == CLIENT ? "CLIENT" : "SERVER"});
		}

		if (getOutState() == IDLE) {
		    mux.removeSession(sessionID);
		} else if (getOutState() < TERMINATED) {
		    if (role == SERVER && getOutState() == FINISHED) {
			/*
			 * In this case, send Close rather than Abort, so that
			 * a client that still hasn't finished writing will not
			 * get an unnecessary failure and will be able to read
			 * the complete response as intended (still permitting
			 * server-side defensive abort() invocation).
			 */
			mux.asyncSendClose(sessionID);
		    } else {
			mux.asyncSendAbort(Mux.Abort | (role == SERVER ?
							Mux.Abort_partial : 0),
					   sessionID, null);
		    }
		    setOutState(TERMINATED);
		}

		setDown("request aborted", null);
	    }
	    /*
	     * After the application has invoked abort() on the request, we
	     * must no longer try to "fake" an OK session.
	     */
            out.abort();

	    /*
	     * If removing this session from the connection's table
	     * was delayed in order to be able to send an
	     * Acknowledgment message, then we remove it on local
	     * abort in order to clean up resources.  Also make sure
	     * that our state is considered terminated so that no
	     * future Acknowledgment message will be sent.
	     */
	    if (removeLater) {
		if (getOutState() < TERMINATED) {
		    setOutState(TERMINATED);
		}
		mux.removeSession(sessionID);
		removeLater = false;
	    }
	}
    }

    /**
     *
     */
    void setDown(String message, Throwable cause) {
	synchronized (sessionLock) {
	    if (!sessionDown) {
		sessionDown = true;
                IOException ex = new IOException(message, cause);
                out.down(ex);
                in.down(ex);
		sessionLock.notifyAll();
	    }
	}
    }

    /**
     *
     */
    void handleIncrementRation(int increment) throws ProtocolException {
	synchronized (sessionLock) {
	    if (inState == IDLE || inState == TERMINATED) {
		throw new ProtocolException("IncrementRation on " +
		    stateNames[inState] + " session: " + sessionID);
	    }
	    if (!outRationInfinite) {
		if (outRation + increment < outRation) {
		    throw new ProtocolException("ration overflow");
		}
		if (getOutState() == OPEN) {
		    if (increment > 0) {
			if (outRation == 0) {
			    sessionLock.notifyAll();
			}
			outRation += increment;
		    }
		}
	    } // ignore message if outbound ration is infinite
	}
    }

    /**
     *
     */
    void handleAbort(boolean partial) throws ProtocolException {
	synchronized (sessionLock) {
	    if (inState == IDLE || inState == TERMINATED) {
		throw new ProtocolException("Abort on " +
		    stateNames[inState] + " session: " + sessionID);
	    }

	    setInState(TERMINATED);
	    partialDeliveryStatus = partial;

	    /*
	     * Respond with an abort of this side of the session, if it's
	     * still open.
	     */
	    /*
	     * REMIND: Technically, the client should not have to send
	     * an Abort message here if it is already in the finished
	     * state, although the spec would seem to suggest that it
	     * should do so regardless.  A particular reason that we
	     * send it here in that case, though, is that it should be
	     * a cheap way to avoid 4827402 for that case-- to ensure
	     * that no late Acknowledgment message gets sent after the
	     * session has been removed.
	     */
	    if (getOutState() < TERMINATED) {
		mux.asyncSendAbort(Mux.Abort | (role == SERVER ?
						Mux.Abort_partial : 0),
				   sessionID, null);
		setOutState(TERMINATED);
	    }

	    setDown("request aborted by remote endpoint", null);

	    if (sentAckRequired && !receivedAcknowledgment) {
		notifyAcknowledgmentListeners(false);
	    }	// REMIND: what about other dangling acknowledgments?

	    mux.removeSession(sessionID);
	}
    }

    /**
     *
     */
    void handleClose() throws ProtocolException {
	if (role != CLIENT) {
	    throw new ProtocolException("Close sent by client");
	}

	synchronized (sessionLock) {
	    if (inState != FINISHED) {
		throw new ProtocolException("Close on " +
		    stateNames[inState] + " session: " + sessionID);
	    }
	    if (getOutState() < FINISHED) {
		/*
		 * From a protocol perspective, we need to terminate the
		 * session at this point (because we're not finished, but
		 * we don't want to hold on to it unnecessarily).  But we
		 * also don't want the session to appear failed while the
		 * client is still writing-- instead, we want the client
		 * to be able to successfully read the complete response
		 * that was received-- so this flag is set to
		 * (temporarily) fake that the session is still in OK
		 * shape (but not send any more data for it).
		 */
                out.handleClose();
		mux.asyncSendAbort(Mux.Abort, sessionID, null);
		setOutState(TERMINATED);
		/*
		 * REMIND: This approach causes a premature negative
		 * acknowledgment to the server.  It seems that
		 * ideally, if receivedAckRequired is true, we should
		 * delay sending the Abort message until the response
		 * input stream is closed and an Acknowledgment has
		 * been sent-- although that would be somewhat at odds
		 * with the "timely fashion" prescription of the Close
		 * message specification.
		 */
	    }

	    setInState(TERMINATED);

	    setDown("request closed by server", null);

	    /*
	     * If we still (might) need to send an Acknowledgment,
	     * then we must delay removing this session from the
	     * connection's table now, to prevent the sessionID being
	     * reused before the Acknowledgment message is sent.
	     */
	    if (getOutState() == TERMINATED ||
		!receivedAckRequired || in.isSentAcknowledgment())
	    {
		mux.removeSession(sessionID);
	    } else {
		removeLater = true;
	    }
	}
    }

    /**
     *
     */
    void handleAcknowledgment() throws ProtocolException {
	if (role != SERVER) {
	    throw new ProtocolException("Acknowledgment sent by server");
	}

	synchronized (sessionLock) {
	    if (inState == IDLE || inState == TERMINATED) {
		throw new ProtocolException("Acknowledgment on " +
		    stateNames[inState] + " session: " + sessionID);
	    }
	    if (getOutState() < FINISHED) {
		throw new ProtocolException(
		    "acknowledgment received before EOF sent");
	    }
	    if (!sentAckRequired) {
		throw new ProtocolException("acknowledgment not requested");
	    }
	    if (receivedAcknowledgment) {
		throw new ProtocolException("duplicate acknowledgment");
	    }
	    receivedAcknowledgment = true;

	    notifyAcknowledgmentListeners(true);
	}
    }

    /**
     *
     */
    void handleData(ByteBuffer data,
		    boolean eof, boolean close, boolean ackRequired)
	throws ProtocolException
    {
	assert eof || (!close && !ackRequired);

	if (ackRequired && role != CLIENT) {
	    throw new ProtocolException("Data/ackRequired sent by client");
	}

	synchronized (sessionLock) {
	    boolean notified = close;	// close always causes notification

	    if (inState != OPEN) {
		throw new ProtocolException("Data on " +
		    stateNames[inState] + " session: " + sessionID);
	    }
	    int length = data.remaining();
	    if (!inRationInfinite && length > inRation) {
		throw new ProtocolException("input ration exceeded");
	    }
	    if (!in.isClosed() && getOutState() < TERMINATED) {
		if (length > 0) {
		    if (in.getBufRemaining() == 0) {
			sessionLock.notifyAll();
			notified = true;
		    }
		    in.appendToBufQueue(data);
		    in.setBufRemaining(in.getBufRemaining() + length);
		    if (!inRationInfinite) {
			inRation -= length;
		    }
		}
	    }

	    if (eof) {
		in.setEOF(true);
		setInState(FINISHED);
		if (!notified) {
		    sessionLock.notifyAll();
		}

		if (ackRequired) {
		    receivedAckRequired = true;
		    // send acknowledgment if input stream already closed?
		}

		if (close) {
		    handleClose();
		}
		// REMIND: send Close if appropriate?
	    }
	}
    }

    /**
     *
     */
    void handleOpen() throws ProtocolException {
	assert role == SERVER;
	synchronized (sessionLock) {
	    if (inState < FINISHED || getOutState() < TERMINATED) {
		throw new ProtocolException(
                    inState < FINISHED ?
		    ("Data/open on " +
		     stateNames[inState] + " session: " + sessionID) :
		    ("Data/open before previous session terminated"));
	    }

	    setInState(TERMINATED);
	    // REMIND: process dangling acknowledgments here?

	    setDown("old request", null);	// extraneous?
	    sessionLock.notifyAll();

	    mux.removeSession(sessionID);
	}
    }

    /**
     *
     */
    void setOutState(int newState) {
        assert Thread.holdsLock(sessionLock);
	assert newState > outState;
	outState = newState;
    }

    /**
     *
     */
    void setInState(int newState) {
        assert Thread.holdsLock(sessionLock);
	assert newState > inState;
	inState = newState;
    }
    
    boolean ackListeners(){
        assert Thread.holdsLock(sessionLock);
        return !ackListeners.isEmpty();
    }

    private void notifyAcknowledgmentListeners(final boolean received) {
	if (!ackListeners.isEmpty()) {
	    systemThreadPool.execute(
                    new NotifyAcknowledgementListeners(ackListeners, received),
                    "Mux ack notifier");
	}
    }
    
    private static class NotifyAcknowledgementListeners implements Runnable {
        final Collection<AcknowledgmentSource.Listener> ackListeners;
        final boolean received;
        
        NotifyAcknowledgementListeners(
                Collection<AcknowledgmentSource.Listener> ackListeners,
                boolean received)
        {
            this.ackListeners = new ArrayList<AcknowledgmentSource.Listener>(ackListeners);
            this.received = received;
        }
        
        @Override
        public void run() {
            for (AcknowledgmentSource.Listener listener : ackListeners) {
                listener.acknowledgmentReceived(received);
            }
        }
    }

    /**
     * @return the outState
     */
    int getOutState() {
        assert Thread.holdsLock(sessionLock);
        return outState;
    }

    /**
     * @return the outRation
     */
    int getOutRation() {
        assert Thread.holdsLock(sessionLock);
        return outRation;
    }

    /**
     * @param outRation the outRation to set
     */
    void setOutRation(int outRation) {
        assert Thread.holdsLock(sessionLock);
        this.outRation = outRation;
    }

    /**
     * @return the inState
     */
    int getInState() {
        assert Thread.holdsLock(sessionLock);
        return inState;
    }

    /**
     * @param partialDeliveryStatus the partialDeliveryStatus to set
     */
    void setPartialDeliveryStatus(boolean partialDeliveryStatus) {
        assert Thread.holdsLock(sessionLock);
        this.partialDeliveryStatus = partialDeliveryStatus;
    }

    /**
     * @return the sentAckRequired
     */
    boolean isSentAckRequired() {
        assert Thread.holdsLock(sessionLock);
        return sentAckRequired;
    }

    /**
     * @param sentAckRequired the sentAckRequired to set
     */
    void setSentAckRequired(boolean sentAckRequired) {
        assert Thread.holdsLock(sessionLock);
        this.sentAckRequired = sentAckRequired;
    }

    /**
     * @return the inRation
     */
    int getInRation() {
        assert Thread.holdsLock(sessionLock);
        return inRation;
    }

    /**
     * @param inRation the inRation to set
     */
    void setInRation(int inRation) {
        assert Thread.holdsLock(sessionLock);
        this.inRation = inRation;
    }

    /**
     * @return the removeLater
     */
    boolean isRemoveLater() {
        assert Thread.holdsLock(sessionLock);
        return removeLater;
    }

    /**
     * @param removeLater the removeLater to set
     */
    void setRemoveLater(boolean removeLater) {
        assert Thread.holdsLock(sessionLock);
        this.removeLater = removeLater;
    }

    /**
     * @return the receivedAckRequired
     */
    boolean isReceivedAckRequired() {
        assert Thread.holdsLock(sessionLock);
        return receivedAckRequired;
    }
}
