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
import java.util.Map;
import java.util.List;
import java.util.Iterator;
import java.util.logging.Logger;

import net.jini.core.transaction.server.TransactionManager;
import net.jini.core.transaction.server.ServerTransaction;
import net.jini.security.ProxyPreparer;

import com.sun.jini.logging.Levels;

/**
 * Keeps a mapping from {@link TransactionManager}/id pairs, to {@link Txn}
 * objects. Some <code>Txn</code>s may be <i>broken</i>, that is the
 * <code>TransactionManager</code> they are associated with can not be
 * unmarshalled and prepared, and thus can't currently be used (but may be
 * usable in the future).
 * 
 * @author Sun Microsystems, Inc.
 * @since 2.0
 */
class TxnTable {
	/**
	 * Key class for the primary map (from manager/id pairs to <code>Txn</code>
	 * s. We use a new class instead of <code>ServerTransaction</code> objects
	 * so we can make sure we don't call <code>equals</code> on unprepared
	 * managers.
	 */
	private class Key {
		/** The manager for the transaction */
		private final TransactionManager manager;

		/** The id for the transaction */
		private final long id;

		/**
		 * True if it has been asserted that <code>manager</code> has been
		 * prepared. Note, we only put Keys in the table that have this flag
		 * set.
		 */
		private final boolean prepared;

		/**
		 * Create a new key from the specified manager and id.
		 * 
		 * @param manager
		 *            the manager for the transaction
		 * @param id
		 *            the id for the transaction
		 * @param prepared
		 *            should be <code>true</code> if the manager has been
		 *            prepared and false otherwise
		 * @throws NullPointerException
		 *             if manager is <code>null</code>.
		 */
		private Key(TransactionManager manager, long id, boolean prepared) {
			if (manager == null)
				throw new NullPointerException("manager must be non-null");
			this.manager = manager;
			this.id = id;
			this.prepared = prepared;
		}

		// Inherit doc from super
		public int hashCode() {
			return (int) id ^ manager.hashCode();
		}

		// Inherit doc from super
		public boolean equals(Object other) {
			if (!(other instanceof Key))
				return false;

			final Key o = (Key) other;
			if (id != o.id)
				return false;

			/*
			 * Either this or o should have prepared == true, call
			 * manager.equals on the one who has been prepared.
			 */
			if (o.prepared)
				// Common case, usually the passed in object is in the map
				return o.manager.equals(manager);
			else if (prepared)
				return manager.equals(o.manager);
			else
				throw new AssertionError("TxnTable.Key equals call with two "
						+ "unprepared managers");
		}
	}

	/**
	 * Map of manager,id pairs (represented as <code>Keys</code>s) to non-broken
	 * <code>Txn</code>s. Only <code>Key</code>s that have their prepared flag
	 * set should go in this Map.
	 */
	final private Map txns = new java.util.HashMap();

	/**
	 * Map of transaction ids to the <code>List</code> of broken
	 * <code>Txn</code> objects that have the id. <code>null</code> if there are
	 * no broken <code>Txn</code>s.
	 */
	private Map brokenTxns = null;

	/**
	 * <code>ProxyPreparer</code> to use when unpacking transactions, may be
	 * <code>null</code>.
	 */
	private final ProxyPreparer proxyPreparer;

	/** An array of type <code>Txn</code> to pass to <code>toArray</code> */
	private static final Txn[] txnArray = new Txn[0];

	/** The logger to use */
	static private final Logger logger = Logger
			.getLogger(OutriggerServerImpl.txnLoggerName);

	/**
	 * Create a new <code>TxnTable</code>.
	 * 
	 * @param proxyPreparer
	 *            the proxy preparer to use on recovered
	 *            <code>TransactionManager</code>s.
	 */
	TxnTable(ProxyPreparer proxyPreparer) {
		this.proxyPreparer = proxyPreparer;
	}

	/**
	 * Given a <code>TransactionManager</code>, <code>manager</code>, and a
	 * transaction id, return the associated <code>Txn</code>, or
	 * <code>null</code> if there is no <code>Txn</code> for this manager/id
	 * pair. The only method this method will call on <code>manager</code> is
	 * <code>hashCode</code>. The returned <code>Txn</code> object will not be
	 * broken.
	 * 
	 * @param manager
	 *            a <code>TransactionManager</code>.
	 * @param id
	 *            a transaction id.
	 * @return the <code>Txn</code> for the specified pair or null if none can
	 *         be found.
	 * @throws IOException
	 *             if there was one or more possible matches, but they are
	 *             broken and the attempt to unpack them yielded an
	 *             <code>IOException</code>.
	 * @throws ClassNotFoundException
	 *             if there was one or more possible matches, but they are
	 *             broken and the attempt to unpack them yielded a
	 *             <code>ClassNotFoundException</code>.
	 * @throws SecurityException
	 *             if there was one or more possible matches, but they are
	 *             broken and the attempt to unpack them yielded a
	 *             <code>SecurityException</code>.
	 */
	Txn get(TransactionManager manager, long id) throws IOException,
			ClassNotFoundException {
		final Long idAsLong;
		final Txn brokenTxnsForId[];
		// Try the table of non-broken txns first
		synchronized (this) {
			final Txn r = (Txn) txns.get(new Key(manager, id, false));
			if (r != null)
				return r;

			// Check broken txns
			if (brokenTxns == null)
				// No broken Txns so txns is definitive
				return null;

			idAsLong = new Long(id);
			final List txnsForId = (List) brokenTxns.get(idAsLong);
			if (txnsForId == null)
				/*
				 * Broken Txns, but none with the right ID so txns is definitive
				 * for this manager/id pair.
				 */
				return null;

			/*
			 * If we are here there are broken Txns with the specified id, we
			 * need to try and fix each one and check to see if there is a
			 * match. Make a copy of the list so we don't need to hold a lock
			 * while making fix attempts (which in general involve remote
			 * communications).
			 */
			brokenTxnsForId = (Txn[]) txnsForId.toArray(txnArray);
		}

		/*
		 * try and fix each element of brokenTxnsForId until we find the right
		 * Txn, or until we run out of elements
		 */
		Txn match = null;
		Throwable t = null; // The first throwable we get, if any
		final List fixed = new java.util.LinkedList(); // fixed Txns
		for (int i = 0; i < brokenTxnsForId.length; i++) {
			try {
				final ServerTransaction st = brokenTxnsForId[i]
						.getTransaction(proxyPreparer);

				/*
				 * We fixed a Txn, remember so we can move it to txns.
				 */
				fixed.add(brokenTxnsForId[i]);

				/*
				 * Did this match? Pass unprepared manager to prepared one!
				 */
				if (st.mgr.equals(manager)) {
					// bingo!
					match = brokenTxnsForId[i];
					break;
				}
			} catch (Throwable tt) {
				try {
					if (logger.isLoggable(Levels.FAILED))
						logger.log(Levels.FAILED, "Encountered " + tt
								+ "while recovering/re-preparing "
								+ "transaction, will retry latter", tt);
				} catch (Throwable ttt) {
					// don't let a problem in logging kill the thread
				}

				if (t == null)
					t = tt;
			}
		}

		/*
		 * Now that all the remote operations are done, re-acquire lock so we
		 * can move anything that was fixed.
		 */
		if (!fixed.isEmpty()) {
			synchronized (this) {
				if (brokenTxns != null) {
					final List txnsForId = (List) brokenTxns.get(idAsLong);
					if (txnsForId != null) {

						// Remove from brokenTxns
						txnsForId.removeAll(fixed);

						// can we get rid of txnsForId and brokenTxns?
						if (txnsForId.isEmpty()) {
							brokenTxns.remove(idAsLong);
							if (brokenTxns.isEmpty())
								brokenTxns = null;
						}

						// Put in txns
						for (Iterator i = fixed.iterator(); i.hasNext();) {
							put((Txn) i.next());
						}
					} else {
						/*
						 * if the list for this id is gone the fixed Txns must
						 * have already been moved by someone else
						 */
					}
				} else {
					/*
					 * if brokenTxns is gone the fixed Txns must have already
					 * been moved by someone else
					 */
				}
			}
		}

		// Did we run out of candidates, or did we find a match?
		if (match != null)
			return match;

		/*
		 * Was there some Txn that may have been a match but is still broken so
		 * we don't know? [this could happen if : o the managers codebase
		 * changed so old proxies can't be unmarshalled (but new ones can), o
		 * the log is corrupted, o a bad preparer has been supplied, o etc.,
		 * etc. ]
		 */
		if (t != null) {
			/*
			 * We may have some recored of this manager/id pair, but we can't
			 * tell since one or more of the broken Txns that could be a match
			 * could not be fixed. Throw an exception to indicate an ambiguous
			 * result.
			 */
			if (t instanceof IOException)
				throw (IOException) t;
			else if (t instanceof ClassNotFoundException)
				throw (ClassNotFoundException) t;
			else if (t instanceof Error)
				throw (Error) t;
			else if (t instanceof RuntimeException)
				throw (RuntimeException) t;
			else
				throw new AssertionError(t);
		}

		/*
		 * If we are here there were one or more broken Txns with the same ID,
		 * but all them could be fixed and none of them had a manager that
		 * matched. Definitively return null, we have no record of this
		 * manager/id pair.
		 */
		return null;
	}

	/**
	 * Atomically test if there is a <code>Txn</code> for the specified
	 * <code>ServerTransaction</code> in the table, creating a new
	 * <code>Txn</code>, and place in table if there is not. If there is already
	 * a <code>Txn</code> for the specified <code>ServerTransaction</code>
	 * return the existing one, otherwise return the new one. Does not check for
	 * matches against broken txns.
	 * 
	 * @param tr
	 *            <code>ServerTransaction</code> to add to the table. The
	 *            contained manager proxy should already been prepared
	 * @return the <code>Txn</code> for <code>tr</code>.
	 */
	Txn put(ServerTransaction tr) {
		final Key k = new Key(tr.mgr, tr.id, true);
		final long internalID = OutriggerServerImpl.nextID();

		// Atomic test and set
		synchronized (this) {
			Txn r = (Txn) txns.get(k);
			if (r == null) {
				// not in table, put a new one in and return it.
				r = new Txn(tr, internalID);
				txns.put(k, r);
			}

			return r;
		}
	}

	/**
	 * Used to put a formally broken <code>Txn</code> in the main table. Only
	 * puts it in the table if it is not already their.
	 * 
	 * @param txn
	 *            the <code>Txn</code> being moved, should have been prepared
	 */
	private void put(Txn txn) {
		final Key k = new Key(txn.getManager(), txn.getTransactionId(), true);
		synchronized (this) {
			final Txn r = (Txn) txns.get(k);
			if (r == null) {
				txns.put(k, txn);
			}
		}
	}

	/**
	 * Restore a <code>Txn</code> in the table as part of log recovery. An
	 * attempt will be made unpack and prepare the transaction manager. The
	 * <code>Txn</code> may be broken. This method is not synchronized.
	 * 
	 * @param txn
	 *            the <code>Txn</code> being recovered.
	 */
	void recover(Txn txn) {
		// Try to get the transaction
		ServerTransaction st = null;
		try {
			st = txn.getTransaction(proxyPreparer);
		} catch (Throwable t) {
			// log, but otherwise ignore
			try {
				if (logger.isLoggable(Levels.FAILED))
					logger.log(Levels.FAILED, "Encountered " + t + " while "
							+ "recovering/re-preparing transaction, "
							+ "will retry latter", t);
			} catch (Throwable tt) {
				// don't let a problem in logging kill the thread
			}
		}

		if (st == null) {
			// txn is broken, put in brokenTxns
			if (brokenTxns == null)
				brokenTxns = new java.util.HashMap();

			final Long id = new Long(txn.getTransactionId());
			List txnsForId = (List) brokenTxns.get(id);
			if (txnsForId == null) {
				txnsForId = new java.util.LinkedList();
				brokenTxns.put(id, txnsForId);
			}

			txnsForId.add(txn);
		} else {
			// Txn is ok, put it in txns
			final Key k = new Key(st.mgr, st.id, true);
			txns.put(k, txn);
		}
	}

	/**
	 * Remove the mapping for the given <code>TransactionManager</code>, id
	 * pair. Will not remove broken <code>Txn</code>s.
	 * 
	 * @param manager
	 *            the <code>TransactionManager</code> for transaction being
	 *            removed.
	 * @param id the manager assigned to the transaction being removed.
	 */
	void remove(TransactionManager manager, long id) {
		final Key k = new Key(manager, id, false);
		synchronized (this) {
			txns.remove(k);
		}
	}
}
