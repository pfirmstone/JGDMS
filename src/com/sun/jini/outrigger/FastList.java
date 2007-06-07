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

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * This class provides a low-synchronization list of objects.  It is
 * designed to allow proper traversal of the list while objects are
 * being added and/or removed, and to allow simultaneous adds and
 * removals.  Traversals must be done in a single thread; no fair handing
 * off a <code>Node</code> pointer to another thread to continue the
 * traversal!  A single thread cannot perform two or more simultaneous
 * traversals, even if traversing different lists.
 * <p>
 * All this means that users of the list will occasionally see a
 * removed node in the list, because it may be marked removed after it
 * has been reached while traversing the list.  This will be rare, but
 * it is up to the user of the list to skip over nodes that have been
 * removed if necessary, and to handle or avoid attempted re-removal of
 * a node.  This is why <code>remove</code> returns a boolean -- if the
 * node has already been removed by the time the attempt to remove it
 * happens, <code>remove</code> will return <code>false</code>.  This
 * is done synchronizing on the node itself.  A typical piece of code
 * to remove would look like this:
 *
 * <pre>
 *  public synchronized Value findMatch(Value matchFor) {
 *      for (Value val = list.head(); val != null; val = val.next()) {
 *          if (matchFor.matches(val)) {
 *              if (!list.remove(n))   // oh, well -- someone got to it first
 *                  continue;          // keep looking
 *              return val;            // it's ours to take!
 *          }
 *      }
 *      return null;
 *  }
 * </pre>
 *
 * This code assumes that getting a removed node will be rare enough
 * that there is no point in testing for it until a match is found.
 * If testing for a match was expensive, one might
 * <code>synchronize</code> on the node object and test it for being
 * removed and then try for matching, thus preventing any other
 * thread from attempting a match at the same time.  In any case, the
 * <code>remove</code> test is itself atomic -- either it succeeds, or
 * it fails because someone got there first.
 *
 * <p>
 * <i>The Java(TM) Language Specification<i> details a memory model based on
 * local and global memories, apparently intended as a model for
 * multiprocessor caches.  The specified memory model should be
 * supplemented by William Pugh's {@link
 * http://www.cs.umd.edu/~pugh/java/memoryModel/ work} on the Java
 * programming language memory model and JSR 133.
 * <p>
 * In particular, the <code>volatile</code> keyword offers no practical
 * guarantees.  The lock and unlock operations underlying the
 * <code>synchronized</code> language construct are the primary mechanisms
 * for communicating between threads.  A lock operation forces stale values
 * to be flushed from the locking thread's cache: any values subsequently
 * read by the thread are either read from main memory or are cached values
 * no older than the time the lock was acquired.  An unlock operation forces
 * dirty cache entries to be written to main memory: any values written by
 * the thread up to then must be force-written to main memory before the
 * unlock can complete.
 * <p>
 * In every other respect, access to shared memory by threads is
 * arbitrary.  Updates can happen at any time and in any order.  In
 * particular, if one thread initializes a new Node and adds it to the end
 * of the list, it's possible for a different thread to see the pointer to
 * the new Node but read uninitialized (stale) values for the fields of the
 * new Node (including, ominously, the instance's <code>vptr</code>),
 * because writes can be seen in a different order by another thread.  Even
 * on a uniprocessor, compiler optimizations can cause similar surprises.
 * <p>
 * The removal of a node is decoupled from the unlinking of that node from
 * the list.  The <code>next</code> method unlinks some nodes that have been
 * removed; with reaping, the number of nodes which remain in a list for a
 * long time will be less than the number of threads in the VM.
 * <p>
 * No node can be a member of more than one list.  Nodes which have been
 * added to a list can never be added to another list, even if they are
 * removed from the first list.
 * <p>
 * Never synchronize on an instance of this class - it could lead to
 * deadlock. (See the paragraph on locking order below for explanation.)
 * <p>
 * You can synchronize on an instance of a <code>Node</code> subclass
 * provided that the synchronizing thread does not already hold a lock on
 * another <code>Node</code> instance taken from the same
 * <code>FastList</code>. Note, many of the methods on <code>FastList</code>
 * synchronize on <code>Node</code>s in the
 * list, thus you should not call one of these methods if you
 * already hold a lock on a <code>Node</code> in the list. Similarly
 * for methods on <code>Node</code>.
 * <p>
 * The remainder of this comment discusses the internal structure
 * and assumptions of this class.
 * <p>
 * The design is to have a list of <code>FastList.Node</code> nodes,
 * each of which has a <code>next</code> reference, a <code>removed</code>
 * flag, and a <code>guardSet</code> (indicating which traversals
 * depend on a future synchronization on that node).
 * <p>
 * There is a special sublist which is important to assuring the integrity
 * of this data structure.  The chain of nodes starting at <code>head</code>
 * and continuing to the <code>tail</code> via <code>Node.next</code>
 * references is called the <dfn>main sequence</dfn>.  The fundamental
 * constraints on the main sequence are: <ul>
 * <li> The <code>tail</code> is always reachable from the <code>head</code>.
 * <li> Every <code>Node</code> for which <code>removed==false</code> is
 * an element of the main sequence.
 * <li> Every <code>Node</code> for which
 * <code>guardSet.isEmpty()==false</code> is an element of the main sequence
 * (regardless of <code>removed</code>).
 * <li> <code>tail.next==null</code> whenever <code>tail != null</code>.
 * <li> <code>tail==null</code> if and only if <code>head==null</code>.
 * </ul>
 * <p>
 * Note that removed non-guard nodes can still be in the main sequence:
 * they do not have to be unlinked immediately.
 * <p>
 * We say <q><var>Y</var> is <dfn>reachable</dfn> from <var>X</var></q>
 * when <var>X</var> equals <var>Y</var> or when <var>Y</var> is
 * reachable from <code><var>X</var>.next</code>.
 * <p>
 * New nodes are added at the tail, requiring synchronization on the list.
 * A node is said to be <dfn>removed<dfn> if its <code>removed</code>
 * field is <code>true</code>.  Such a node may still exist and be
 * part of the main sequence.  Removed nodes will eventually be unlinked
 * from their neighbors, if the <code>reap</code> method is called
 * occasionally.
 * <p>
 * Any element can become removed (by the <code>remove</code> method),
 * but once that happens, that element can never revert to being unremoved.
 * This means that the <code>removed</code> field can be read without
 * synchronization: a <code>true</code> value indicates that the node has
 * been removed; a <code>false</code> value is inconclusive.
 * This in turn means that a removed element cannot be added to a
 * <code>FastList</code>, because some other threads may read a stale cached
 * value for its <code>removed</code> field.  (It may be possible, one day,
 * to liberate removed nodes from this restriction, given the guarantees of
 * guarded traversal.)
 * <p>
 * The list can be traversed without much synchronization.
 * When a traversal begins, a node near the end of the main sequence is
 * specially marked (<i>guarded</i>) in order that the traversal will
 * stop and synchronize when it reaches that node.  When the guard node is
 * reached, and there are more nodes in the list to traverse, the
 * procedure is repeated: the (new) last node in the list
 * is marked, and traversal continues.  This procedure ends when the
 * traversal reaches its guard node but finds out that it is still the
 * last node in the list.  In this way, much of the list can be traversed
 * without synchronization.
 * <p>
 * It is possible for a traversal to find itself on an orphaned branch
 * (one from which the <code>tail</code> is not reachable).  This can
 * happen when the <code>restart</code> method is called by a different
 * thread.  Even a node on an orphaned branch is still useful for
 * traversal: every unremoved node which was inserted after the orphaned
 * node but before the traversal originally began is still reachable
 * from the orphaned node.
 * <p>
 * Because every traversal has a guard, a list with at least
 * one node can not become empty by traversal alone, even if the node
 * is removed.  The <code>reap</code> method can remove such nodes.
 * <p>
 * Main-sequence nodes are stored in FIFO order.
 * <p>
 * Synchronized locks are acquired in accordance with the following
 * partial order, in order to avoid deadlocks.
 * <ul><li>Earlier nodes precede later nodes (i.e. where a thread holds
 * the lock on a node <var>X</var>, it must not wait for a lock on any
 * node <var>Y</var> unless <var>Y</var> is reachable from
 * <var>X</var>).</li>
 * <li>Every node precedes the list (i.e. where a thread holds the lock on
 * the <code>FastList</code> itself, it must not wait for a lock on any node
 * which has been added to that list).</li></ul>
 * <p>
 * Each node contain a field (<code>guardSet</code>) which
 * indicates how many different threads are using the node as a guard node.
 * If the field's value is <code>null</code>, the number is zero and the node
 * is not a guard node.  If the field's value is nonnull, the value is a
 * <code>WeakHashMap</code> whose <code>keySet</code> is the set of threads
 * using the node as a guard node.  If that <code>keySet</code> is empty,
 * the node is not a guard node.
 * <p>
 * There is a global table (actually a ThreadLocal) called
 * <code>DynamicGuard</code> which is used to
 * retrieve the guard node for the current thread.  The number of entries
 * in a guard node's <code>guardSet</code> generally corresponds to the
 * number of times that node appears in the global table (the
 * <code>guardSet</code> size temporarily becomes greater
 * than the number of table entries, but it can never be less).
 * The list need not be synchronized while the global table is being accessed.
 * <p>
 * Unsynchronized access may be made to the <code>guardSet</code>,
 * <code>removed</code>, and <code>next</code> fields.  In the case of
 * <code>next</code>, the field may be modified so as to unlink any removed
 * non-guard nodes.  This action does not invalidate the main-sequence
 * invariant, and can only be done by a thread which is holding a lock on
 * the node being unlinked.
 * <p>
 * In the case of <code>guardSet</code> and <code>removed</code>, the fields
 * are read as an optimization.  If the values read from the fields might
 * be stale, then synchronization is done and the old values are not
 * relied on.  The value of <code>guardSet</code> is read without
 * synchronization to see if it's null or not, as part of a check to detect
 * the traversing thread's guard node.  The unsynchronized value of
 * <code>removed</code> is untrusted when it's <code>false</code>; however,
 * a <code>true</code> value is trustworthy, since such a value could only
 * be read after the node is irrevocably marked removed.
 *
 * @author Sun Microsystems, Inc.
 *
 */

class FastList {

    /**
     * A single node in a <code>FastList</code>.
     */
    static abstract class Node {
	/** The next youngest Node in the list.
	 * If this node is the last in the list, this field is null.
	 * Otherwise, it's a pointer to the next Node that was added
	 * to the list (and not unlinked in the meantime).
	 * <p>
	 * This field may be changed only while synchronized on
	 * <code>this</code>, or when unlinking a node from the middle
	 * of the list.  This field may be read without
	 * synchronization if the reading thread has a guard node which
	 * is not <code>this</code>.
	 */
	private volatile Node next;

	/** True only if this node has been removed.
	 * Every node starts out unremoved.  Once this field becomes
	 * true, it can never become false again.  You must be
	 * synchronized on this before you can modify this field.
	 * You can read this field without synchronization if you don't
	 * mind reading an old <code>false</code> instead of a more
	 * recent <code>true</code>.
	 */
	private boolean	removed;

	/** The list containing this node.
	 * Every node in a list has the same value for this field.
	 * This field must not be changed after the node is added to
	 * a list.  The field can be read without synchronization
	 * provided that the reading thread has first synchronized on
	 * something since <code>this</code> node was added to its list
	 * (that is, during a traversal).
	 */
	private FastList list;

	/** Set of threads for which this is a guard.
	 * This is a weak map from Thread to nulls, and the key set is
	 * the important set of threads.  The values are unimportant.
	 * You must be synchronized on this node to access the map.
	 * This field may be <code>null</code>, which is semantically
	 * equivalent to the empty set.
	 * <p>
	 * This field is read without synchronization in <code>next</code>.
	 * That method is searching for a particular node where the value
	 * of this field is known to be continuously nonnull since the last
	 * synchronization by the reading thread, so it is safe.
	 * <p>
	 * Note to maintainer: it may be better to use a reference count
	 * and rely on the user to use a finally-clause to effect the
	 * necessary decrement.  In that case, head() should verify that
	 * any previous traversal has been properly terminated.
	 */
	private transient volatile WeakHashMap guardSet;

	/** Default constructor.  This is called by subclasses. */
	protected Node() {
	    this.removed = false;
	    this.guardSet = null;
	    this.list = null;	// will be filled in by FastList.add()
	    this.next = null;
	}

	/**
	 * Traverse to the next node.  Note that between the time that the
	 * next node is returned and the time you look at it someone may
	 * have removed it.  Returns <code>null</code> if there are no more
	 * nodes.
	 * <p>
	 * Although this method attempts to skip removed nodes, callers
	 * <em>must not</em> assume that removed nodes have been skipped.
	 * Use the {@link #removed() removed} method to tell when a node
	 * returned by this method is in fact removed, and
	 * <code>synchronize</code> on the node if you want to prevent
	 * anyone else from removing the node while you are in the
	 * synchronized block.
	 * <p>
	 * Do not call this method twice on the same node unless each call is
	 * part of a separate traversal; a traversal starts with a call to
	 * <code>List.head</code>).
	 * <p>
	 * Note this method should not be called by threads that
	 * hold locks on <code>Node</code>s that appear in the list
	 * after this <code>Node</code>.	 	 
	 */
	public Node next() {

	    this.list.checkPanic();	// abort if malfunctioned

            // Ensure the list has a guard and it is for THIS list
	    Node traversalGuard = (Node)FastList.DynamicGuard.get();
	    if (traversalGuard == null)
		this.list.panic("Unguarded FastList traversal - 345");

	    if (traversalGuard.list != this.list)
		this.list.panic("Illegal traversal of 2 FastLists");

	    /* When this thread last synchronized, Node.guardSet on
	     * the thread's guard node was nonempty because the
	     * traversing thread was added to guardSet by newGuardFor.
	     * Since then, it cannot have been emptied, and therefore
	     * cannot have been replaced with null.  Therefore the
	     * guard node must appear to have a nonnull Node.guardSet
	     * field, even if accessed without locking.  This means we
	     * can safely conclude, without locking, that a Node whose
	     * guardSet field is null is not the guard node for the
	     * current thread.  
	     */
	    if (this.guardSet != null) {
		/* This may or may not be the guard node; lock it
		 * so we can be sure that reading `next' is safe:
		 */
		synchronized (this) {
		    if (this.guardSet != null && traversalGuard == this) {
			/* This is the guard node for the current
			 * thread; drop it and return null.
			 * (We don't need to find nodes added
			 *  after the traversal was started)
			 */
			FastList.DynamicGuard.set(null);
			traversalGuard.unguard(Thread.currentThread());
			return null; 

			/* If we did want nodes that were added
			 * since the traversal started we 
			 * want to try and get a new guard node
			 * with code like this :
			 *
			 * if (this.next != null) {
			 *    if (!this.list.newGuardFor(this)) {
			 *	  // If it returned false, newGuardFor()
			 *	  // has already cleaned up--we just return
			 *  	  return null;	// traversal is over
			 *    } else { 
			 *	// We can extend the traversal : proceed!
			 *    } 
			 * } else { 
			 *    // We're at the end of the list. Clean up.
			 *    FastList.DynamicGuard.set(null); 
			 *    traversalGuard.unguard(
                         *        Thread.currentThread()); 
			 *    return null; 
			 * } 
                         */
		    }
		}
	    }

	    // At this point, there is a guard node reachable from this.next.

	    /* This loop repeatedly looks at this.next to see if there's a
	     * possibility of unlinking the next node.  It stops at the
	     * first sign of trouble (a guard node, an unremoved node, or
	     * a concurrency conflict):
	     */
	    Node n = this.next;
	    while (true) {
		if (n == null) {
		    // We're on an orphaned branch.  Remove our guard.
		    traversalGuard = (Node)FastList.DynamicGuard.get();
		    if (traversalGuard != null) {
			FastList.DynamicGuard.set(null);
			traversalGuard.unguard(Thread.currentThread());
		    }
		    return null;
		}

		// We can only unlink a removed node which is not a guard:
		if (n.guardSet != null || !n.removed)
		    break;

		/* Lock n and see if we can find an excuse to unlink it.
		 * While n is locked, no other thread can change this.next.
		 */
		synchronized (n) {
		    // Break if this.next changed.
		    if (this.next != n)
			break;
		    // Verify that n is not a guard:
		    if (n.guardSet != null && !n.guardSet.isEmpty()) {
			break;
		    }
                    // reduce empty set to null
		    n.guardSet = null;
		}
		/* n is removed and not a guard, so it's a candidate
		 * for removal.  If we got this far, then we have a
		 * guard node.  Therefore, n is not the last node in
		 * the main sequence (although it may be the last node
		 * on an orphaned branch).
		 */
		this.next = n = n.next;
	    }

	    return n;
	}

	/** 
	 * Resume a traversal abandoned by another thread.
	 * This is used by remote iterators.  When a traversal was
	 * started (and abandoned) by thread A, and a thread B wants
	 * to continue the traversal from where A left off, then B
	 * must call this method on the most recent <code>Node</code>
	 * traversed by thread A.  After this method returns normally,
	 * B can call the <code>next</code> method on the same
	 * <code>Node</code> to continue the traversal.
	 * <p>
	 * Note this method should not be called by threads that
	 * hold locks on <code>Node</code>s that appear in the list
	 * after this <code>Node</code>.	 	 
	 */
	public synchronized void restart() {

	    this.list.checkPanic();	// abort if malfunctioned.

	    /* If the current thread still has a guard from an earlier
	     * abandoned traversal, then clean up that guard.
	     */
	    Node oldguard = (Node)FastList.DynamicGuard.get();
	    if (oldguard != null) {
		FastList.DynamicGuard.set(null);
		oldguard.unguard(Thread.currentThread());
	    }

	    /* There must be a synchronized block here before calling
	     * newGuardFor, because newGuardFor reads FastList.tail
	     * without synchronization.  That's why this whole method
	     * is synchronized.
	     */

	    /* Attempt to get a guard.  If newGuardFor returns false, no
	     * guard has been allocated, but that isn't a problem because
	     * it also means that this node is on an orphaned branch where
	     * a guard node is not necessary.
	     */
	    this.list.newGuardFor(null);
	}

	/**
	 * Return <code>true</code> if the object has been removed
	 * from the list.  If called while synchronized on
	 * <code>this</code>, this will return true if the object has
	 * been removed and false otherwise.  
	 */
	public boolean removed() {

	    this.list.checkPanic();	// abort if malfunctioned.
	    return removed;
	}

	/**
	 * Remove the node from the list.  This will be unsuccessful if the
	 * node was removed by someone else after you got your hands on
	 * it.  This must be synchronized because the test to check if it
	 * is already removed and the following decision to remove it if it
	 * hasn't been removed must be unitary.
	 */
	private boolean remove() {

	    synchronized (this) {
		if (removed)
		    return false;		// beaten to it

		removed = true;		// mark first, unlink later
	    }

	    this.list.reapable();

	    return true;
	}

	/** Remove a thread from guardSet.  The indicated thread is
	 * removed from the guardSet of the node, and the guardSet is
	 * reduced to <code>null</code> if it's empty.
	 * @param traverser the Thread to use as a key
	 */
	private synchronized void unguard(Thread traverser) {
	    if (this.guardSet == null)
		return;

	    this.guardSet.remove(traverser);
	    if (this.guardSet.isEmpty())
		this.guardSet = null;
	}

	final Node dump(java.io.PrintWriter out) { //DEBUG
	  if (this.removed) //DEBUG
	      out.print('!'); //DEBUG
	  out.print(toString()); //DEBUG
	  Map g = this.guardSet; //DEBUG
	  if (g != null) //DEBUG
	      out.print("[g=" + g.size() + "]"); //DEBUG
	  return this.next; //DEBUG
	} //DEBUG
    } // end class Node

    /** The guard node for the calling thread's traversal, if any.
     * The value is <tt>null</tt> for a thread which is not traversing
     * anything, and is a <code>Node</code> for other threads.  No
     * thread can be involved in two concurrent traversals.  This field
     * can be accessed without synchronization since it's thread-local.
     */
    private static final ThreadLocal DynamicGuard = new ThreadLocal();

    /** Flag to indicate that removed elements exist in the list and
     * that reaping is not necessarily a waste of time.  This is set
     * in <code>remove</code> and cleared in <code>reap</code>.
     */
    private boolean reapable;

    /** The pointer to the last element in the list. */
    private Node tail; // synchronize on list to read or write

    /** The pointer to the first element in the list. */
    private Node head; // synchronize on list to read or write

    /** An Error that indicated a fundamental malfunction in the list. */
    private transient Error firstPanic = null;

    /**
     * Create an new empty list.
     */
    public FastList() {
	this.reapable = false;
	this.tail = null;
	this.head = null;
	this.firstPanic = null;
    }

    /**
     * Add a given node to the end of the list. Note, this method
     * generally attempts to lock <code>Node</code>s in the list and
     * should not be called from a thread that holds a lock on a node
     * already in the list - it is ok if the calling thread holds the
     * lock on <code>node</code>.
     */
    public void add(Node node) {

	this.checkPanic();	// abort if malfunctioned.
	if (node.removed) {
	    throw new IllegalArgumentException(
		    "FastList.add cannot accept a removed node");
	}
	if (node.list != null) {
	    throw new IllegalArgumentException(
		"FastList.add cannot accept a node from another FastList");
	}

	node.list = this;

	while (true) {
	    Node t = this.tail;
	    if (t == null) {
		// List appears to be empty - lock list and insert first node
		synchronized (this) {
		    if (this.tail != null) {
			//System.err.print('$'); //DBG
			continue;	// try again
		    }
		    this.tail = node;
		    this.head = node;
		}
	    } else {
		/* List is not empty - lock tail & list and insert the
		 * node at the end.
		 */
		synchronized (t) {
		    if (t != this.tail) { // optimization
			//System.err.print('#'); //DBG
			continue;	// try again
		    }
		    synchronized (this) {
			if (t != this.tail) {
			    //System.err.print('&'); //DBG
			    continue;	// try again
			}
			t.next = node;
			this.tail = node;
		    }
		}
	    }
	    return;
	}
    }

    /** 
     * Allocate a new guard node for a traversing thread.
     * This method selects a guard node on the main sequence, as close as
     * practically possible to the tail.  Returns true iff a guard was
     * allocated, and false if the list appeared to contain no elements
     * after the oldGuard.  This routine must acquire a new guard at
     * the same time as or before releasing the old one, in order to
     * guarantee that the new guard will be reachable from the old.
     * <p>
     * Because this method reads <code>FastList.tail</code> without
     * synchronization, the guard could be stale.  It's important that
     * it not be too stale, or else nodes added before the traversal
     * began might not appear in the traversal.  For this reason, the
     * caller must have recently acquired a lock (i.e. synchronized on
     * any object).  The guard allocated by this method will have been the
     * tail at least once since that lock was acquired.
     * <p>
     * Note: the oldGuard will always be dropped as the guard node for
     * this thread. Further, should this method return <code>false</code>
     * the DynamicGuard will be set to <code>null</code>.
     */
    private boolean newGuardFor(Node oldGuard) {

	/* tGood becomes true when it's determined that a given node
	 * is good enough to be a guard node.  "Good enough" means
	 * that it's reachable from the head, and that it was the tail
	 * at least once since this method was entered.  
	 */

	Thread current = Thread.currentThread();

	while (true) {
	    boolean tGood = false;		// is tGood a useable guard?
	    boolean noMoreElements = false;	// is oldGuard the tail node?
	    Node t = this.tail;

	    /* A new guard is not necessary for an empty list.
	     * Further, an empty list has no guard nodes (or indeed
	     * any nodes) so there is no need to call unguard() on
	     * anything -- even the parameter "oldGuard".  
	     */
	    if (t == null)
		return false;

	    /* Optimistically presume that t will be good enough, and add
	     * to its guardSet.  If the optimism isn't justified, then the
	     * addition will be undone later.  If t is on the main sequence
	     * and after oldGuard, then it's OK for use as a new guard.
	     * This is because traversals must be guaranteed to hit the
	     * guard node, and the easiest way to do that is to ensure
	     * that the guard node is on the main sequence.
	     */
	    synchronized (t) {
		if (  (t.guardSet != null && !t.guardSet.isEmpty())
		   || !t.removed())
		{
		    if (t != oldGuard)		// can't use same guard again
			tGood = true;
		}
		// Make sure t can't be unlinked after we release the lock.
		if (t.guardSet == null)
		    t.guardSet = new WeakHashMap();
		t.guardSet.put(current, null);
	    }

	    /* If t isn't good enough, then we have to try the real tail.  If
	     * the tail isn't good enough, then we're at the end of the list,
	     * and no guard is required.
	     */
	    if (!tGood) {
		synchronized (this) {
		    if (this.tail == t) {
			if (t != oldGuard) {  // t is reachable from oldGuard
			    tGood = true;
			} else {	      // oldGuard == this.tail
			    noMoreElements = true;
			}
		    }
		}
	    }

	    if (tGood) {
		DynamicGuard.set(t);
		if (oldGuard != null)
		    oldGuard.unguard(current);
		return true;
	    }

	    //System.err.print('*'); //DBG

	    // Undo the optimistic work if it turns out t isn't good enough.
	    t.unguard(current);

	    if (noMoreElements) {
		DynamicGuard.set(null);
		if (oldGuard != null && oldGuard != t)
		    oldGuard.unguard(current);
		return false;
	    }
	}
    }

    /**
     * Return the start of the list. Returns null if the list is
     * empty.  Note, this method generally attempts to lock
     * <code>Node</code>s in the list and should not be called from a
     * thread that holds a lock on a node already in the list.
     */
    public Node head() {

	this.checkPanic();	// Abort if malfunctioned.
	/* If the current thread still has a guard from an earlier abandoned
	 * traversal, then clean up that guard.
	 */
	Node oldguard = (Node)DynamicGuard.get();
	if (oldguard != null) {
	    DynamicGuard.set(null);
	    oldguard.unguard(Thread.currentThread());
	}

	Node h;
	Node t;

	synchronized (this) {
	    h = this.head;
	    t = this.tail;
	    if (h == null)
		return null;		// nothing in list
	}

	// There must be a synchronized block here before calling
	// newGuardFor, because newGuardFor reads FastList.tail
	// without synchronization.

	// Every traversal needs a guard.
	if (!newGuardFor(null))
	    return null;		// nothing in list, suddenly

	// Make an attempt to unlink removed non-guard nodes from the head
	while (h.removed()) {
	    synchronized (h) {
                /* 
                 * We stop if we find any node that is a guard node. Of 
                 * course, eventually we'll reach the node which is OUR 
                 * guard node and at that point we'll break from this loop.
                 */
		if (h.guardSet != null && !h.guardSet.isEmpty())
		    break;		// can't unlink guard nodes

		synchronized (this) {
		    if (h != this.head)
			return this.head;	// someone else just did it
		    this.head = h = h.next;
		}
	    }
	}

	return h;
    }

    /**
     * Remove the given node from this list.  
     */
    public boolean remove(Node node) {

	this.checkPanic();	// Abort if malfunctioned.
	return node.remove();
    }

    /** Remember that there's something for the reaper to do.
     */
    private void reapable() {
	this.reapable = true;
    }

    /** 
     * Perform a normal traversal to help clear out some removed
     * nodes.  It also moves the tail. Note, this method generally
     * attempts to lock <code>Node</code>s in the list and should not
     * be called from a thread that holds a lock on a node already in
     * the list.
     */
    public void reap() {

	if (!this.reapable)
	    return;

	this.reapable = false;
	// reapable could be concurrently set to true, which is OK.

	reapCnt++;

	// Normal traversal (unlinks some nodes as a side effect).
	Node last = null;
	Node secondLast = null;
	for (Node n = this.head(); n != null; n = n.next()) {
	    secondLast = last;
	    last = n;
	}

	// Clear out the last node only if it's removed and not a guard
	if (last == null || !last.removed || last.guardSet != null)
	    return;
	
	if (secondLast == null) {
	    // probably the only one in the list
	    synchronized (last) {
		if (last.next != null)
		    return;	// changed while waiting
		if (last.guardSet != null && !last.guardSet.isEmpty())
		    return;	// last became a guard while we waited
		synchronized (this) {
		    if (last != this.tail || last != this.head)
			return;	// a node was added while we waited
		    // remove the only node from the list
		    this.head = this.tail = null;
		}
	    }
	} else {
	    /* Move the tail to secondLast.  We can only do this if
	     * secondLast is on the main sequence and last is both
	     * removed and not a guard.
	     */
	    if (secondLast.removed && secondLast.guardSet == null)
		return;		// not sure secondLast is on main sequence
	    synchronized (secondLast) {
		if (secondLast.removed) {
		    if (secondLast.guardSet == null)
			return;	// not sure secondLast is on main sequence
		    if (secondLast.guardSet.isEmpty()) {
			secondLast.guardSet = null;
			return;	// ditto
		    }
		}
		// We're sure now that secondLast is on the main sequence.
		synchronized (last) {
		    if (secondLast.next != last)
			return;	// last has already been unlinked
		    if (last.next != null)
			return;	// a node was added while we waited
		    if (last.guardSet != null && !last.guardSet.isEmpty())
			return;	// last is a guard
		    // We tested last.removed and it must still be true now.
		    synchronized (this) {
			if (last != this.tail)
			    return; // shouldn't happen
			// Unlink last (secondLast is the new tail):
			secondLast.next = null;
			this.tail = secondLast;
		    }
		}
	    }
	}
    } // end reap

    int reapCnt = 0; //DEBUG
    public final int reapCnt() { return reapCnt; } //DEBUG


    public void dump(java.io.PrintWriter out) { //DEBUG
	Node n = this.head; //DEBUG
	while (n != null) { //DEBUG
	    if (this.tail == n) //DEBUG
		out.print("[tail]"); //DEBUG
	    n = n.dump(out); //DEBUG
	    out.print(' '); //DEBUG
	} //DEBUG
	out.print("null"); //DEBUG
    } //DEBUG

    /** Shut down the list due to an error.  The argument is used to
     * construct an Error which is thrown.  The FastList then becomes
     * unusable because every public method calls checkPanic which
     * then throws another exception.
     *
     * @param condition identifying string which will help debugging
     */
    private void panic(String condition)
	throws Error
    {
	InternalError err = new InternalError(condition);

	// Record only the first panic so that subsequent ones don't confuse:
	if (firstPanic == null) {
	    synchronized (this) {
		if (firstPanic == null)
		    firstPanic = err;
	    }
	}

	throw err;
    }

    /** Abort if the list has been shut down.  This does nothing in
     * normal operation.  If the list has undergone a panic, then this
     * throws an exception which says so.
     */
    private void checkPanic()
	throws Error
    {
	if (firstPanic == null)
	    return;
	throw new InternalError("FastList panicked; original error was:" +
		firstPanic);
    }

}
