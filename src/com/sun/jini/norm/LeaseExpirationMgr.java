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
package com.sun.jini.norm;

import java.lang.ref.WeakReference;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.sun.jini.collection.WeakTable;
import com.sun.jini.landlord.LeasedResource;
import com.sun.jini.thread.InterruptedStatusThread;
import com.sun.jini.thread.WakeupManager;

/**
 * Lease manager that aggressively expires leases as their expiration times
 * occur.  Also schedules and manages expiration warning events.
 * <p>
 * Note, unlike Mahalo's <code>LeaseExpirationManager</code> (which this
 * was seeded from), we make no attempt to make it generic because of
 * the need to schedule expiration warning events.
 *
 * @author Sun Microsystems, Inc.
 */
class LeaseExpirationMgr implements WeakTable.KeyGCHandler {
    /** Logger for logging messages for this class */
    static final Logger logger = Logger.getLogger("com.sun.jini.norm");

    /** 
     * Map of sets to task tickets.
     * <p>
     * A Note on Synchronization
     * <p>
     * Whenever we operate on the <code>ticketMap</code> we hold
     * the lock on the key being used.  This is necessary because
     * expiration and warning sender tasks need to remove tickets from
     * the map but at the same time a renewal may be updating the map
     * to associate the set with a new ticket.  If we don't synchronize
     * there is a small window where a task could remove the ticket
     * for its replacement.
     */
    private WeakTable		ticketMap = new WeakTable(this); 

    /** Ref to the main server object has all the top level methods */
    private NormServerBaseImpl	server;

    /** Queue of tasks, ordered by time */
    private WakeupManager	runQueue = new WakeupManager();

    /** Queue of tasks to expire sets */
    final List expireQueue = new LinkedList();

    /** Thread to expire sets */
    private final Thread expireThread = new ExpirationThread();

    /**
     * Create a <code>LeaseExpirationMgr</code> to aggressively expire
     * the leases of the passed <code>NormServerBaseImpl</code>
     */
    LeaseExpirationMgr(NormServerBaseImpl server) {
	this.server = server;
	expireThread.start();
    }

    /**
     * Terminate the <code>LeaseExpirationMgr</code>, killing any 
     * threads it has started
     */
    void terminate() {
	runQueue.stop();
	runQueue.cancelAll();
	expireThread.interrupt();
    }
    
    /**
     * Notifies the manager of a new lease being created.
     *
     * @param resource the resource associated with the new lease
     */
    void register(LeasedResource resource) {
	// Need to synchronize because schedule manipulates 
	// ticketMap.
	synchronized (resource) {
	    schedule(resource);
	}
    }

    /**
     * Notifies the manager of a lease being renewed. <p>
     *
     * This method assumes the lock on <code>set</code> is owned by the
     * current thread.
     *
     * @param resource the set for which tasks have to be rescheduled
     */
    void reschedule(LeasedResource resource) {
	/*
	 * Remove the old event.  This method is only called
	 * (indirectly) from NormServerBaseImpl.renew() so we know that
	 * we own the lock on resource.
	 */
	WakeupManager.Ticket ticket =
	    (WakeupManager.Ticket) ticketMap.remove(resource);
	if (ticket != null) {
	    runQueue.cancel(ticket);
	}
	// Schedule the new event
	schedule(resource);	
    }

    /**
     * Schedule a leased resource to be reaped in the future. Called
     * when a resource gets a lease, a lease is renewed, and during log
     * recovery.
     * <p>
     * This method assumes the lock on <code>resource</code> is owned by
     * the current thread.
     */
    void schedule(LeasedResource resource) {
	WakeupManager.Ticket ticket;
	final LeaseSet set = (LeaseSet) resource;
	MgrTask task;

	if (set.haveWarningRegistration()) {
	    task = new SendWarning(set);	    
	    ticket = runQueue.schedule(set.getWarningTime(), task);	    
	} else {
	    task = new QueueExpiration(set);
	    ticket = runQueue.schedule(set.getExpiration(), task);
	}
	
	/*
	 * No window here because the tasks only use the ticket after
	 * they acquire the lock on their set, but we still own the lock
	 * on the set.
	 */
	task.setTicket(ticket);
	ticketMap.getOrAdd(set, ticket);
    }

    // purposefully inherit doc comment from supertype
    // Called when LeaseResource we are tracking is garbage collected
    public void keyGC(Object value) {
	final WakeupManager.Ticket ticket = (WakeupManager.Ticket) value;
	runQueue.cancel(ticket);
    }

    /**
     * Expires sets queued for expiration.  Perform the expiration in a
     * separate thread because the operation will block if a snapshot is going
     * on.  It's OK for an expiration to block other expirations, which need
     * not be timely, but using the separate thread avoids blocking renewal
     * warnings, which should be timely.
     */
    private class ExpirationThread extends InterruptedStatusThread {

	ExpirationThread() {
	    super("expire lease sets thread");
	    setDaemon(true);
	}

	public void run() {
	    while (!hasBeenInterrupted()) {
		try {
		    Runnable task;
		    synchronized (expireQueue) {
			if (expireQueue.isEmpty()) {
			    expireQueue.wait();
			    continue;
			}
			task = (Runnable) expireQueue.remove(0);
		    }
		    task.run();
		} catch (InterruptedException e) {
		    return;
		} catch (Throwable t) {
		    logger.log(Level.INFO,
			       "Exception in lease set expiration thread -- " +
			       "attempting to continue",
			       t);
		}
	    }
	}
    }

    /**
     * Utility base class for our tasks, mainly provides the the proper
     * locking for manipulating the ticketMap.
     */
    private abstract class MgrTask implements Runnable {
	/** Resource this task is to operate on */
	protected final WeakReference resourceRef;
	
	/** Ticket for this task */
	private WakeupManager.Ticket ticket;

	/** 
	 * Simple constructor.
	 *
	 * @param set the set this task is to operate on
	 */
	protected MgrTask(LeaseSet set) {
	    resourceRef = new WeakReference(set);
	}

	/** Set the ticket associated with this task. */
	private void setTicket(WakeupManager.Ticket ticket) {
	    this.ticket = ticket;
	}

	/**
	 * Removes this task's ticket from the ticket map iff this
	 * task's ticket is in the map.  Returns the
	 * <code>LeaseSet</code> this task is to operate on or
	 * <code>null</code> if this task should stop.
	 */
	protected LeaseSet removeOurTicket() {
	    final LeaseSet set = (LeaseSet) resourceRef.get();
	    if (set != null) {
		synchronized (set) {
		    final WakeupManager.Ticket currentTicket = 
			(WakeupManager.Ticket) ticketMap.get(set);
		    if (ticket.equals(currentTicket)) {
			ticketMap.remove(set);
		    } else {
			/*
			 * Someone removed us after we were committed to
			 * run -- we should stop.
			 */
			return null;
		    }
		}
	    }

	    return set;
	}

	// purposefully inherit doc comment from supertype
	public abstract void run();
    }

    /** Task that queues a task to expire a lease set. */
    private class QueueExpiration extends MgrTask {
	QueueExpiration(LeaseSet set) {
	    super(set);
	}

	public void run() {
	    LeaseSet set = removeOurTicket();
	    if (set != null) {
		synchronized (expireQueue) {
		    expireQueue.add(new Expiration(set));
		    expireQueue.notifyAll();
		}
	    }
	}
    }

    /**
     * Objects that do the actual expiration of the set in question,
     * stuck in <code>expireQueue</code>.
     */
    private class Expiration implements Runnable {

	private LeaseSet set;
	/**
	 * Create a <code>Expiration</code> task for the passed resource.
	 *
	 * @param set the set this task is to operate on
	 */
	private Expiration(LeaseSet set) {
	    this.set = set;
	}

	// purposefully inherit doc comment from supertype
	public void run() {
	    server.expireIfTime(set);
	    /*
	     * Note we don't care if it's actually time or not, if it
	     * is not the task will be rescheduled by the renewal.
	     */
	}
    }

    /**
     * Objects that do the schedule the warning events, also schedules
     * an expiration task.
     */
    private class SendWarning extends MgrTask {
	/**
	 * Create a <code>SendWarning</code> task for the passed resource.
	 *
	 * @param set the set this task is to operate on
	 */
	private SendWarning(LeaseSet set) {
	    super(set);
	}

	// purposefully inherit doc comment from supertype
	public void run() {
	    final LeaseSet s = (LeaseSet) resourceRef.get();
	    if (s == null) {
		// set is gone, no work to do
		return;
	    }

	    /*
	     * By holding this lock we prevent other threads from
	     * scheduling new tasks for this set...if we have been
	     * replaced we will return before scheduling a new task, if
	     * we have not been we will schedule the new task and it can
	     * be cleanly removed by any renew that is happening at the
	     * same time.
	     */
	    synchronized (s) {
		final LeaseSet set = removeOurTicket();
		if (set == null) {
		    // set is gone, or our task was replaced, no work to do
		    return;
		}

		// Send event
		server.sendWarningEvent(set);

		// Schedule expiration task
		final MgrTask task = new QueueExpiration(set);
		final WakeupManager.Ticket newTicket =
		    runQueue.schedule(set.getExpiration(), task);
		task.setTicket(newTicket);
		ticketMap.getOrAdd(set, newTicket);
	    }
	}
    }
}
