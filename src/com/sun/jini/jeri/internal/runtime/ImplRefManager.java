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

package com.sun.jini.jeri.internal.runtime;

import com.sun.jini.jeri.internal.runtime.Target;
import com.sun.jini.thread.NewThreadAction;
import java.lang.ref.Reference;
import java.lang.ref.ReferenceQueue;
import java.rmi.Remote;
import java.rmi.server.Unreferenced;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.jini.security.SecurityContext;

/**
 * Manages references to remote object implementations (impls) used by
 * ObjectTable targets.  The managed reference for a particular impl
 * is represented by an instance of the inner class ImplRef.  All of
 * the targets for current exports of an impl are registered with its
 * ImplRef.
 *
 * This class supports pinning and unpinning of an impl reference by
 * its registered targets (to control whether the reference is strong
 * or weak); automatic, asynchronous invocation of an impl's
 * Unreferenced.unreferenced() method, when appropriate; and
 * asynchronous notifications of all registered targets when a (weakly
 * referenced) impl has been garbage collected.
 *
 * @author Sun Microsystems, Inc.
 **/
final class ImplRefManager {

    private static final Logger logger =
	Logger.getLogger("net.jini.jeri.BasicJeriExporter");

    /** queue notified when weak refs to impls are cleared */
    private final ReferenceQueue reapQueue = new ReferenceQueue();

    /**
     * lock guarding all mutable instance state (below).
     *
     * Note that if both this lock and the lock for an ImplRef
     * instance need to be acquired together, then this lock must be
     * acquired first.
     **/
    private final Object lock = new Object();

    /** maps WeakKey(impl) to ImplRef(WeakKey(impl)) */
    private final Map<Reference,ImplRef> weakImplTable = new HashMap<Reference,ImplRef>();

    /** thread to process garbage collected impls */
    private Thread reaper = null;

    /** true if reaper thread may be interrupted */
    private boolean interruptible = false;

    ImplRefManager() {
    }

    /**
     * Returns the ImplRef for the specified impl (creating it if
     * necessary), registering the supplied target with it.
     *
     * If the target goes away on its own accord (i.e. without the
     * impl getting garbage collected, such as by being unexported),
     * it must invoke the returned ImplRef's release(Target) method
     * passing itself (REMIND: perhaps this requirement could be
     * avoided by using weak reference notifications).
     **/
    ImplRef getImplRef(Remote impl, Target target) {
	/*
	 * Without an identity-based weak key hash table, we must look up
	 * with a key of a weak reference that matches on referent identity.
	 * If there is no matching entry, we reuse the same weak reference
	 * in the new ImplRef, so register it with our reference queue.
	 */
	Reference lookupKey = new WeakKey(impl, reapQueue);
	synchronized (lock) {
	    ImplRef implRef = weakImplTable.get(lookupKey);
	    if (implRef == null) {
		implRef = new ImplRef(lookupKey);
		weakImplTable.put(lookupKey, implRef);

		if (reaper == null) {
		    reaper = (Thread) AccessController.doPrivileged(
			new NewThreadAction(new Reaper(), "Reaper", true));
		    reaper.start();

		    /*
		     * We are now interested in special assistance from the
		     * local garbage collector for aggressively collecting
		     * unreachable remote objects, so that they do not keep
		     * the VM alive indefinitely.
		     *
		     * Without guaranteed access to something like the
		     * sun.misc.GC API, however, we currently have no
		     * practical way of getting such special assistance.
		     */
		}
	    } else {
		/*
		 * Clear the weak reference used for lookup, so that it will
		 * not generate spurious reference queue notifications later.
		 */
		lookupKey.clear();
	    }

	    implRef.addTarget(target);
	    return implRef;
	}
    }

    /**
     * A managed reference to a remote object implementation (impl).
     *
     * An ImplRef may be pinned and unpinned with respect to any
     * target that is registered with it.  While pinned for at least
     * one target, an ImplRef refers to its impl with a strong
     * reference; otherwise, it refers to the impl with only a weak
     * reference, so that the impl may be locally garbage collected.
     *
     * The getImpl() method can be used to obtain a strong reference
     * to the impl as long as it has not been garbage collected.
     *
     * An ImplRef maintains a set of all targets that are registered
     * with it-- that is, targets passed to ImplRefManager.getImplRef
     * but not passed to the release method.  If the impl is detected
     * to have been garbage collected, then all targets in the set
     * will have their collect() method invoked asynchronously.
     **/
    final class ImplRef {

	/** weak reference to impl */
	private final Reference weakRef;

	/** removed from table; guarded by "lock" */
	private boolean removed = false;

	/** targets for all exports of referenced impl; guarded by "this" */
	private final Set targets = new HashSet(1);

	/** targets that have pinned this reference; guarded by "this"  */
	private final Set pinningTargets = new HashSet(1);

	/** strong reference to impl, when pinned; guarded by "this" */
	private Remote strongRef = null;

	private ImplRef(Reference weakRef) {
	    this.weakRef = weakRef;
	}

	private synchronized void addTarget(Target target) {
	    assert !targets.contains(target);
	    targets.add(target);

	    if (logger.isLoggable(Level.FINEST)) {
		logger.log(Level.FINEST, "this={0}, target={1}",
			   new Object[] { this, target });
	    }
	}

	/**
	 * Returns the referenced impl, or null if the impl has been
	 * garbage collected.
	 **/
	Remote getImpl() {
	    return (Remote) weakRef.get();
	}

	/**
	 * Pins this reference for the specified target, so that the
	 * impl will be held with a strong reference.  This pin will
	 * remain in effect until unpin is invoked for the same
	 * target, or if the target releases this reference.
	 *
	 * This method must NOT be invoked if the referenced impl has
	 * been garbage collected (i.e. if getImpl() returns null).
	 **/
	synchronized void pin(Target target) {
	    assert target.getEnableDGC();
	    assert targets.contains(target);
	    if (pinningTargets.isEmpty()) {
		assert strongRef == null;
		strongRef = (Remote) weakRef.get();
	    }
	    assert strongRef != null;
	    assert !pinningTargets.contains(target);
	    pinningTargets.add(target);

	    if (logger.isLoggable(Level.FINEST)) {
		logger.log(Level.FINEST,
			   "this={0}, target={1}, pin count now {2}",
			   new Object[] {
			       this, target, new Integer(pinningTargets.size())
			   });
	    }
	}

	/**
	 * Unpins a previous pin of this reference.  Each invocation
	 * of this method must correspond to a previous invocation of
	 * the pin method for the same target.
	 *
	 * If this operation causes the number of pinning targets to
	 * transition to zero, the impl's unreferenced() method will
	 * be asynchronously invoked (if it implements the
	 * Unreferenced interface).
	 **/
	synchronized void unpin(Target target) {
	    assert target.getEnableDGC();
	    assert targets.contains(target);
	    assert pinningTargets.contains(target);
	    pinningTargets.remove(target);

	    if (logger.isLoggable(Level.FINEST)) {
		logger.log(Level.FINEST,
			   "this={0}, target={1}, pin count now {2}",
			   new Object[] {
			       this, target,
			       new Integer(pinningTargets.size())
			   });
	    }

	    if (pinningTargets.isEmpty()) {
		assert strongRef != null;
		invokeUnreferenced(target);
		strongRef = null;
	    }
	}

	/**
	 * Asynchronously invokes the Unreferenced.unreferenced method
	 * of the impl, if it is an instance of the Unreferenced
	 * interface.  The specified target must be registered with
	 * this ImplRef and DGC-enabled; it is used to obtain a
	 * context class loader value for the invocation.
	 **/
	private void invokeUnreferenced(final Target target) {
	    assert Thread.holdsLock(this);
	    assert strongRef != null;
	    assert target.getEnableDGC();
	    assert targets.contains(target);
	    if (strongRef instanceof Unreferenced) {
		final Unreferenced obj = (Unreferenced) strongRef;
		final Thread t = (Thread) AccessController.doPrivileged(
		    new NewThreadAction(new Runnable() {
			public void run() {
			    SecurityContext securityContext =
				target.getSecurityContext();
			    AccessController.doPrivileged(securityContext.wrap(
				new PrivilegedAction() {
				    public Object run() {
					obj.unreferenced();
					return null;
				    }
				}), securityContext.getAccessControlContext());
			}
		    }, "Unreferenced", false, true));
		AccessController.doPrivileged(new PrivilegedAction() {
		    public Object run() {
			t.setContextClassLoader(
			    target.getContextClassLoader());
			return null;
		    }
		});
		t.start();
	    }
	}

	private boolean isPinned() {
	    assert Thread.holdsLock(this);
	    return !pinningTargets.isEmpty();
	}

	/**
	 * Removes the specified target from the set of targets that
	 * are using this ImplRef, allowing the target to be garbage
	 * collected (and this ImplRef too, if there are no other
	 * targets using it).
	 *
	 * If this operation causes the number of pinning targets to
	 * transition to zero AND there are still targets representing
	 * DGC-enabled exports to the referenced impl, then the impl's
	 * unreferenced() method will be asynchronously invoked (if it
	 * implements the Unreferenced interface).
	 **/
	void release(Target target) {
	    synchronized (lock) {
		if (removed) {
		    return; // may have been removed via garbage collection
		}

		synchronized (this) {
		    assert targets.contains(target);

		    if (logger.isLoggable(Level.FINEST)) {
			logger.log(Level.FINEST, "this={0}, target={1}",
				   new Object[] { this, target });
		    }

		    targets.remove(target);
		    boolean moreTargetsLeft = !targets.isEmpty();

		    /*
		     * Effectively unpin this reference for the target
		     * if it had been pinned for it, but only invoke
		     * unreferenced if DGC-enabled targets remain.
		     */
		    if (pinningTargets.remove(target) &&
			pinningTargets.isEmpty())
		    {
			assert strongRef != null;
			if (moreTargetsLeft) { // one must be DGC-enabled, too
			    for (Iterator i = targets.iterator(); i.hasNext();)
			    {
				Target t = (Target) i.next();
				if (t.getEnableDGC()) {
				    invokeUnreferenced(t);
				    break;
				}
			    }
			}
			strongRef = null;
		    }

		    /*
		     * If there are no targets left registered with
		     * this ImplRef, remove it from the table while
		     * synchronized on "lock", to prevent a race with
		     * future ImplRefManager.getImplRef invocations.
		     */
		    if (!moreTargetsLeft) {
			remove();
		    }
		}
	    }
	}

	/**
	 * Removes this ImplRef from the table.
	 **/
	private void remove() {
	    assert Thread.holdsLock(lock);
	    assert !removed;
	    assert weakImplTable.get(weakRef) == this;
	    weakImplTable.remove(weakRef);
	    removed = true;

	    if (weakImplTable.size() == 0) {
		assert reaper != null;
		if (interruptible) {
		    AccessController.doPrivileged(new PrivilegedAction() {
			public Object run() {
			    reaper.interrupt();
			    return null;
			}
		    });
		}
		reaper = null;

		/*
		 * We are no longer interested in special assistance from the
		 * local garbage collector for aggressively collecting
		 * unreachable remote objects, if we had been getting such
		 * special assistance in the first place.
		 */
	    }
	}

	public String toString() {	// for logging
	    return "ImplRef@" + Integer.toHexString(hashCode()) +
		"[" + getImpl() + "]";
	}
    }

    /**
     * Waits for notifications that weak references in the table have
     * been cleared (and thus referenced impls have been garbage
     * collected).  When a notification is received, the targets using
     * the containing ImplRef are notified, and the corresponding
     * entry is removed from the table.
     **/
    private class Reaper implements Runnable {
	public void run() {
	    do {
		/*
		 * We must only block on the reference queue if we
		 * know that this thread will be interrupted if this
		 * reaper is to be terminated.
		 */
		synchronized (lock) {
		    if (reaper != Thread.currentThread()) {
			break;	// this reaper has been terminated
		    }
		    interruptible = true;
		}

		/*
		 * Wait for next cleared weak reference.
		 */
		Reference weakRef;
		try {
		    weakRef = reapQueue.remove();
		} catch (InterruptedException e) {
		    synchronized (lock) {
			interruptible = false;
		    }
		    break;	// pass away if interrupted
		}

		Set collectedTargets;
		synchronized (lock) {
		    /*
		     * Prevent interrupts and clear interrupted state
		     * in order to avoid unpredictable behavior below.
		     * If an interrupt occurred after reapQueue.remove
		     * returned, this thread will terminate on the
		     * next iteration.
		     */
		    interruptible = false;
		    Thread.interrupted();	// clear interrupted state

		    ImplRef implRef = (ImplRef) weakImplTable.get(weakRef);
		    if (implRef == null) {
			continue; // may have been removed via unexport
		    }
		    assert !implRef.removed;
		    synchronized (implRef) {
			assert !implRef.isPinned();
			collectedTargets = implRef.targets;
			implRef.remove();

			if (logger.isLoggable(Level.FINEST)) {
			    logger.log(Level.FINEST,
				       "implRef={0}, targets={1}",
				       new Object[] {
					   implRef, collectedTargets
				       });
			}
		    }
		}

		// notify targets without holding any locks
		for (Iterator i = collectedTargets.iterator(); i.hasNext();) {
		    ((Target) i.next()).collect();
		}

	    } while (true);
	}
    }
}
