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

import net.jini.io.MarshalledInstance;
import com.sun.jini.landlord.LeasedResource;

/**
 * This object holds an annotated reference to an
 * <code>EntryRep</code> object.  Currently there is one annotation,
 * which is a hash code for the object that can be used as a
 * quick-reject comparison when scanning through the list.  The handle
 * holds a hash code that is based on the bytes that encode the first
 * <i>N</i> fields, where <i>N</i> is the number of fields in the
 * entry up to a maximum (currently this maximum is 16 (64 bits
 * divided by 4 bits/field), so that 4 is the minimum number of bits
 * per field in the hash).
 * <p>
 * When comparing, the template's own hash is calculated, and also a
 * mask that masks out the hash codes of wildcard fields.  A template
 * will match an entry only if the entry's EntryHandle hash masked with
 * the template's wildcard mask is the same as the template's hash.
 * <p>
 * Care must be taken since the template may be a supertype of the type
 * being searched.  This is why the number of fields in the static
 * methods is passed as an argument, not simply taken from the entry in
 * question.  When a template's hash is being created, its hash value
 * is calculated as if it were of the class being searched, with the
 * subclass's field count.  Any extra fields are assumed to be
 * wildcards.  This means that the template's hash must be recalculated
 * for each subclass it is compared against, but this only happens once
 * per known subclass, and so is probably not onerous.  <p>
 *
 * There is a particular risk with the removal of entries outside of
 * transactions. Ideally marking an entry as removed and making the
 * removal durable would be atomic with respect to other
 * operations. But this would require holding a lock across disk I/O
 * which we try to avoid. In particular it would hold up the progress
 * of searches that match the entry in question, even though the very
 * next entry might be a suitable match. One alternative would be to
 * make the removal durable, then while holding the entry's lock mark
 * the entry as removed, but this would allow competing takes to both
 * get the same entry (this could be corrected by making the 2nd take
 * lose when it goes back to try and complete the removal, and then
 * continues its query, but since logging happens up in
 * OutriggerServerImpl restarting the query would be inconvenient, it
 * would probably also result in a number of unnecessary log
 * records). We could mark the entry as removed, release the entry's
 * lock, and then make the removal durable. However, this allows for
 * the possibility of a 2nd query that matches the entry coming in
 * after the entry has been removed, but before the removal has been
 * made durable, finding no matches and returning null, and then the
 * server crashing before the removal is made durable. When the server
 * came back up the entry would be available again, and if the 2nd
 * query was repeated it could then return the entry that had been
 * marked as removed. Effectively an entry would have disappeared and
 * then reappeared. <p>
 * 
 * Our solution is to introduce the <i>removePending</i> flag. When an
 * entry is to be removed outside of a transaction the removePending
 * flag is set by calling <code>provisionallyRemove</code>, the
 * removal is made durable, the entry is removed internally and the
 * removePending flag cleared (generally by calling
 * <code>remove</code> on the appropriate <code>EntryHolder</code> or
 * on the <code>EntryHolderSet</code> - either will remove the entry
 * from all the internal tables and clear the removePending flag). <p>
 *
 * Any operation that will definitively indicate that a given entry
 * has been removed must not only check to see if the entry has been
 * removed but also that removePending is not set (the
 * <code>isProvisionallyRemoved</code> method returns the state of the
 * removePending flag). If removePending is set the operation must
 * either block until removePending is cleared (this can be
 * accomplished using the <code>waitOnCompleteRemoval</code> method),
 * indicating that the removal has been made durable, or return in
 * such a way that the entry's state is left ambiguous. Note, because
 * any I/O failure while logging will result in the space crashing a
 * set removePending flag will only transition to cleared after a
 * removal has been made durable, thus an operation blocked on the
 * removePending flag should never need to go back and see if the
 * entry has become available. <p>
 *
 * Note some of the method of this class are synchronized internally,
 * while other are synchronized externally.  Methods which need to be
 * synchronized externally are called out in their comments.
 *
 * @author Sun Microsystems, Inc.  
 */
// We do not store this data on the EntryRep object itself because it
// is not really part of the client<->JavaSpaces service protocol -- 
// some implementations of EntryHolder may not choose to use this
// mechanism.  It does add an extra object per EntryRep object in
// those that *do* use it, and so we may want to re-examine this in the
// future.

class EntryHandle extends BaseHandle implements LeaseDesc, Transactable {
    /** the content hash for the rep */
    private long     hash;

    /** 
     * If this entry is locked by one or more transaction the info
     * on those transactions, otherwise <code>null</code>.
     */
    private TxnState txnState;

    /**
     * <code>true</code> if this entry has to been seen as removed,
     * but the removal has not yet been committed to disk
     */
    private boolean removePending = false;

    /**
     * Create a new handle, calculating the hash for the object.
     * If <code>mgr</code> is non-<code>null</code> start the entry
     * as write locked under the given transaction.
     * @param rep The rep of the entry this is a handle for
     * @param mgr If this entry is being written under a transaction the
     *            manager for that transaction, otherwise <code>null</code>
     * @param holder If mgr is non-<code>null</code> this must be
     *            the holder holding this handle.  Otherwise it may be
     *            <code>null</code> 
     */
    EntryHandle(EntryRep rep, TransactableMgr mgr, EntryHolder holder) {
	super(rep);
	hash = (rep != null ? hashFor(rep, rep.numFields()) : -1);
	if (mgr == null) {
	    txnState = null;
	} else {
	    if (holder == null) 
		throw new NullPointerException("EntryHandle:If mgr is " +
 	            "non-null holder must be non-null");
	    txnState = new TxnState(mgr, TransactableMgr.WRITE, holder);
	}
    }

    // inherit doc comment
    public LeasedResource getLeasedResource() {
	return rep();
    }

    /**
     * Return this handle's content hash.
     */
    long hash() {
	return hash;
    }

    /**
     * Calculate the hash for a particular entry, assuming the given number
     * of fields.
     *
     * @see #hashFor(EntryRep,int,EntryHandleHashDesc)
     */
    static long hashFor(EntryRep rep, int numFields) {
	return hashFor(rep, numFields, null);
    }

    /**
     * Calculate the hash for a particular entry, assuming the given
     * number of fields, filling in the fields of <code>desc</code>
     * with the relevant values.  <code>desc</code> may be
     * <code>null</code>.  <code>numFields</code> must be >= the number
     * of fields in the <code>rep</code> object (this is not
     * checked).
     *
     * @see #hashFor(EntryRep,int)
     * @see #descFor(EntryRep,int)
     * @see EntryHandleHashDesc
     */
    private static long
	hashFor(EntryRep rep, int numFields, EntryHandleHashDesc hashDesc)
    {
	if (rep == null || numFields == 0)
	    return 0;

	int bitsPerField = Math.max(64 / numFields, 4);	// at least 4 bits
	int fieldsInHash = 64 / bitsPerField;		// max fields used
	long mask =					// per-field bit mask
		    0xffffffffffffffffL >>> (64 - bitsPerField);
	long hash = 0;					// current hash value

	// field counts will be different if rep is a template of a superclass
	long endField = Math.min(fieldsInHash, rep.numFields());

	// set the appropriate rep of the overall hash for the field's hash
	for (int i = 0; i < endField; i++)
	    hash |= (hashForField(rep, i) & mask) << (i * bitsPerField);

	// If someone wants to remember these results, fill 'em in
	if (hashDesc != null) {
	    hashDesc.bitsPerField = bitsPerField;
	    hashDesc.fieldsInHash = fieldsInHash;
	    hashDesc.mask = mask;
	}

	return hash;
    }

    /**
     * Return the template description -- mask and hash.
     *
     * @see EntryHandleTmplDesc
     */
    static EntryHandleTmplDesc descFor(EntryRep tmpl, int numFields) {
	EntryHandleHashDesc hashDesc = new EntryHandleHashDesc();
	EntryHandleTmplDesc tmplDesc = new EntryHandleTmplDesc();

	// Get the hash and the related useful information
	tmplDesc.hash = hashFor(tmpl, numFields, hashDesc);

	// Create the mask to mask away wildcard fields
	for (int i = 0; i < hashDesc.fieldsInHash; i++) {
	    // If this field is one we have a value for, set bits in the mask
	    if (i < tmpl.numFields() && tmpl.value(i) != null)
		tmplDesc.mask |= (hashDesc.mask << (i * hashDesc.bitsPerField));
	}

	// Ensure that the non-value fields are masked out
	tmplDesc.hash &= tmplDesc.mask;

	return tmplDesc;
    }

    /**
     * Return the hash value for a given field, which is then merged in
     * as part of the overall hash for the entry.  The last 32 bytes of
     * the field value are used (or fewer if there are fewer).
     *
     * @see #hashFor(EntryRep,int,EntryHandleHashDesc)
     */
    static long hashForField(EntryRep rep, int field) {
	MarshalledInstance v = rep.value(field);
	if (v == null)	  // for templates, it's just zero
	    return 0;
	else
	    return v.hashCode();
    }

    public String toString() {
	return "0x" + Long.toHexString(hash) + " [" + rep() + "]";
    }

    /**
     * Return <code>true</code> if the operation <code>op</code> under
     * the given transaction (represented by the transaction's manager)
     * can be performed on the object represented by this handle.  The 
     * thread calling this method should own this object's lock.
     */
    // $$$ Calling this method when we don't own the lock on this
    // object seems a bit dicey, but that is exactly what we do in 
    // EntryHolder.SimpleRepEnum.nextRep().  Working it through
    // it seems to work in that particular case, but it seems fragile.
    boolean canPerform(TransactableMgr mgr, int op) {
	if (txnState == null) 
	    return true; // all operations are legal on a non-transacted entry

	return txnState.canPerform(mgr, op);
    }

    /**
     * Return <code>true</code> if the given transaction is already
     * known to the entry this handle represents.  The 
     * thread calling this method should own this object's lock.
     */
    boolean knownMgr(TransactableMgr mgr) {
	if (txnState == null) 
	    return (mgr == null); // The only mgr we know about is the null mgr

	return txnState.knownMgr(mgr);
    }

    /**
     * Return <code>true</code> if we are being managed the given
     * manager is the only one we know about.  The thread calling this
     * method should own this object's lock.
     */
    boolean onlyMgr(TransactableMgr mgr) {
	if (txnState == null) 
	    return false;

	return txnState.onlyMgr(mgr);
    }

    /**
     * Return <code>true</code> if the entry this handle represents is
     * being managed within any transaction.  The thread calling this
     * method should own this object's lock.
     */
    boolean managed() {
	return txnState != null;
    }

    /**
     * Add into the collection any transactions that are known to this
     * handle. The thread calling this method should own this object's
     * lock.
     */
    void addTxns(java.util.Collection collection) {
	if (txnState == null) 
	    return; // nothing to add

	txnState.addTxns(collection);
    }

    /**
     * Add <code>mgr</code> to the list of known managers, setting the
     * the type of lock on this entry to <code>op</code>. The thread
     * calling this method should own this object's lock.  Assumes
     * that <code>op</code> is compatible with any lock currently
     * associated with this entry.  <code>holder</code> is the the
     * <code>EntryHolder</code> holding this handle.
     */
    void add(TransactableMgr mgr, int op, EntryHolder holder) {
	if (txnState == null) {
	    txnState = new TxnState(mgr, op, holder);
	} else {
	    txnState.add(mgr, op);
	}
    }

    /**
     * It this entry is read locked promote to take locked and return
     * true, otherwise return false.  Assumes that the object is
     * locked and the take is being performed under the one
     * transaction that owns a lock on the entry.
     */
    boolean promoteToTakeIfNeeded() {
	return txnState.promoteToTakeIfNeeded();
    }

    /**
     * Returns <code>true</code> it this entry has been removed
     * outside of a transaction, but that removal has not yet been
     * committed to disk.The thread calling this method should own this
     * object's lock.
     */
    boolean isProvisionallyRemoved() {
	assert Thread.holdsLock(this);
	return removePending;
    }

    /**
     * Marks this entry as being removed outside of a transaction but
     * not yet committed to disk. The thread calling this method should
     * own this object's lock.
     */
    void provisionallyRemove() {
	assert Thread.holdsLock(this);
	assert !removePending;
	removePending = true;
    }

    /**
     * Called after the removal of a provisionally removed entry has
     * been committed to disk and the handle has been removed from its
     * holder. The thread calling this method should own this object's
     * lock.
     */
    void removalComplete() {
	assert Thread.holdsLock(this);

	if (removePending) {
	    removePending = false;
	    notifyAll();
	}
    }

    /**
     * If this entry has been marked for removal by a
     * non-transactional operation, but that operation has not be
     * yet been committed to disk, block until the operation has been
     * committed to disk, otherwise return immediately. The
     * thread calling this method should own this object's lock.
     */
    void waitOnCompleteRemoval() throws InterruptedException {
	assert Thread.holdsLock(this);
	while (removePending) {
	    wait();
	}
    }

    /**************************************************
     * Methods required by the Transactable interface 
     **************************************************/
    public synchronized int prepare(TransactableMgr mgr, 
				    OutriggerServerImpl space) 
    {
	if (txnState == null)
	    throw new IllegalStateException("Can't prepare an entry not " +
					    "involved in a transaction");
	final int rslt = txnState.prepare(mgr, space, this);
	if (txnState.empty())
	    txnState = null;

	return rslt;
    }

    public synchronized void abort(TransactableMgr mgr, 
				   OutriggerServerImpl space) 
    {
	if (txnState == null)
	    throw new IllegalStateException("Can't abort an entry not " +
					    "involved in a transaction");
	final boolean last = txnState.abort(mgr, space, this);
	if (last)
	    txnState = null;
    }

    public synchronized void commit(TransactableMgr mgr, 
				    OutriggerServerImpl space) 
    {
	if (txnState == null)
	    throw new IllegalStateException("Can't commit an entry not " +
					    "involved in a transaction");

	final boolean last = txnState.commit(mgr, space, this);
	if (last)
	    txnState = null;
    }
}
