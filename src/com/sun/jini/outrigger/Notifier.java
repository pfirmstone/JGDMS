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
package com.sun.jini.outrigger;

import java.io.IOException;
import java.rmi.RemoteException;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.jini.core.event.UnknownEventException;
import net.jini.config.Configuration;
import net.jini.config.ConfigurationException;
import net.jini.security.ProxyPreparer;
import net.jini.space.JavaSpace;

import com.sun.jini.constants.ThrowableConstants;
import com.sun.jini.config.Config;
import com.sun.jini.logging.Levels;
import com.sun.jini.thread.TaskManager;
import com.sun.jini.thread.RetryTask;
import com.sun.jini.thread.WakeupManager;

/**
 * The notifier thread.  This thread is responsible for notifying
 * objects for which interest has been registered.  It operates in
 * transient space as much as possible.  Pending notifications will be
 * lost when the server goes down, but registrations of interest
 * survive across server crashes for persistent servers.
 *
 * @author Sun Microsystems, Inc.
 *
 * @see JavaSpace#notify
 * @see OutriggerServerImpl#notify
 */
// @see NotifyChit
class Notifier implements com.sun.jini.constants.TimeConstants {
    /** 
     * The object to use for the <code>source</code> when creating
     * events.
     */
    private final JavaSpace source;

    /** Proxy preparer to use on recovered listeners */
    private final ProxyPreparer recoveredListenerPreparer;
    
    /** wakeup manager for <code>NotifyTask</code> */
    private final WakeupManager wakeupMgr = 
	new WakeupManager(new WakeupManager.ThreadDesc(null, true));
    
    /** pending notifications tasks */
    private final TaskManager pending;

    private final static int	MAX_ATTEMPTS = 10;	// max times to retry

    /** Logger for logging event related information */
    private static final Logger logger = 
	Logger.getLogger(OutriggerServerImpl.eventLoggerName);

    /**
     * Create a notifier connected to the given <code>space</code>.
     * @param source the value to use for the <code>source</code> in
     *               remote event objects.
     * @param recoveredListenerPreparer <code>ProxyPreparer</code> to
     *               apply to recovered listeners.
     * @param config a source of configuration data.a
     * @throws ConfigurationException if there is a problem
     *         with the passed configuration.
     * @throws NullPointerException if <code>source</code> or 
     *         <code>config</code> arguments are <code>null</code>.
     */
    Notifier(JavaSpace source, ProxyPreparer recoveredListenerPreparer, 
	     Configuration config) 
	throws ConfigurationException 
    {
	if (source == null)
	    throw new NullPointerException("source must be non-null");
	this.source = source;

	this.recoveredListenerPreparer = recoveredListenerPreparer;

	pending = (TaskManager)Config.getNonNullEntry(config,
	    OutriggerServerImpl.COMPONENT_NAME, "notificationsTaskManager", 
	    TaskManager.class, new TaskManager());
    }

    /**
     * Terminate the notifier, shutting down any threads
     * it has running. This method can assume that
     * the constructor completed.
     */
    void terminate() {
	pending.terminate();
	wakeupMgr.stop();	
	wakeupMgr.cancelAll();	
    }

    /**
     * Queue up an event for delivery.
     * @param sender An object that on request will
     *               attempt to deliver its event
     *               to the associated listener.
     * @throws NullPointerException if <code>sender</code> is
     * <code>null</code>
     */
    void enqueueDelivery(EventSender sender) {
	pending.add(new NotifyTask(sender));
    }

    /*
     * Static stuff for Pending (can't put it in the class, unfortunately).
     */
				// 1 day =hrs  mins secs milliseconds
    private static final long	MAX_TIME = 1 * DAYS;
    private static final long	delays[] = {
				    1 * SECONDS, 5 * SECONDS,
				    10 * SECONDS, 60 * SECONDS, 60 * SECONDS
				};

    static {
	/*
	 * Make the delays the amount of time since the start -- it
	 * is easier to declare the intervals, but the elapsed time is
	 * more <i>useful</i>.
	 */
	for (int i = 1; i < delays.length; i++)
	    delays[i] += delays[i - 1];
    }

    /**
     * A task that represent a notification of matching a particular
     * template under a given transaction.
     */
    private class NotifyTask extends RetryTask {
	/** Who and what to send a event to. */
	private final EventSender sender;	

	/**
	 * Create an object to represent this list of chits needing
	 * notification.
	 * @param sender An object that on request will
	 *               attempt to deliver its event
	 *               to the associated listener.
	 * @throws NullPointerException if <code>sender</code> is
	 * <code>null</code>
	 */
	NotifyTask(EventSender sender) {
	    super(Notifier.this.pending, Notifier.this.wakeupMgr);
	    if (sender == null)
		throw new NullPointerException("sender must be non-null");
	    this.sender = sender;
	}

	/**
	 * Try to notify the target.  Return <code>true</code> if the
	 * notification was successful.
	 * <p>
	 * We know that we are the only one dealing with the given chit
	 * because <code>runAfter</code> makes sure of it.
	 */
	public boolean tryOnce() {
	    long curTime = System.currentTimeMillis();
	    if (curTime - startTime() > MAX_TIME) {
		if (logger.isLoggable(Levels.FAILED)) {
		    logger.log(Levels.FAILED, 
			"giving up on delivering event, keeping registration");
		}

		return true;	// just stop here, we are declaring "success"
	    }

	    boolean successful = true;	// notification successful?
	    try {
		sender.sendEvent(source, curTime, recoveredListenerPreparer);
	    } catch (UnknownEventException e) {
		// they didn't want to know about this, so stop them getting
		// future notifications, too.
		logFailure("UnknownEventException", Level.FINER, true, e);
		sender.cancelRegistration();
		// this is still "successful" -- we know to stop sending this
	    } catch (RemoteException e) {
		final int cat = ThrowableConstants.retryable(e);

		if (cat == ThrowableConstants.BAD_INVOCATION ||
		    cat == ThrowableConstants.BAD_OBJECT)
		{
		    // Listener probably bad, retry likely to fail.
		    logFailure("definite exception", Level.INFO, true, e);
		    sender.cancelRegistration();
		} else if (cat == ThrowableConstants.INDEFINITE) {
		    // try, try, again
		    logFailure("indefinite exception", Levels.FAILED,
			       false, e);
		    successful = false;	
		} else if (cat == ThrowableConstants.UNCATEGORIZED) {
		    // Same as above but log differently.
		    logFailure("uncategorized exception", Level.INFO, false, 
			       e);
		    successful = false;	
		} else {
		    logger.log(Level.WARNING, "ThrowableConstants.retryable " +
			"returned out of range value, " + cat,
			new AssertionError(e));
		    successful = false;			    
		}
	    } catch (IOException e) {
		// corrupted listener? unlikely to get better, cancel
		logFailure("IOException", Level.INFO, true, e);
		sender.cancelRegistration();
	    } catch (ClassNotFoundException e) {
		// probably a codebase problem, retry
		logFailure("ClassNotFoundException", Levels.FAILED, false, e);
		successful = false;			
	    } catch (RuntimeException e) {
		/* bad listener, or preparer, either way unlikely to
		 * get better
		 */
		logFailure("RuntimeException", Level.INFO, true, e);
		sender.cancelRegistration();
	    }

	    if (!successful && attempt() > MAX_ATTEMPTS) {
		if (logger.isLoggable(Levels.FAILED)) {
		    logger.log(Levels.FAILED, 
			"giving up on delivering event, keeping registration");
		}
		return true;		// as successful as we're going to be
	    }

	    return successful;
	}

	public boolean runAfter(java.util.List list, int max) {
	    for (int i = 0; i < max; i++) {
		Object task = list.get(i);
		if (task instanceof NotifyTask) {
		    NotifyTask nt = (NotifyTask)task;
		    if (sender.runAfter(nt.sender))
			return true;
		}
	    }
	    return false;
	}

	/** Log a failed delivery attempt */
	private void logFailure(String exceptionDescription, Level level,
				boolean terminal, Throwable t) 
	{
	    if (logger.isLoggable(level)) {
		logger.log(level, "Encountered " + exceptionDescription +
		     "while preparing to send/sending event, " +
		     (terminal?"dropping":"keeping") +  " registration", t);
	    }
	}
    }
}
