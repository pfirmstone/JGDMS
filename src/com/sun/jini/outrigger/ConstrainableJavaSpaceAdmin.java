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

import java.rmi.RemoteException;
import net.jini.core.entry.Entry;
import net.jini.core.transaction.Transaction;
import net.jini.core.transaction.TransactionException;
import net.jini.core.constraint.MethodConstraints;
import net.jini.space.JavaSpace05;

/**
 * Sub-interface of <code>JavaSpaceAdmin</code> that
 * adds a method that allows iterators to be created with
 * a given set of constraints.<p>
 *
 * @deprecated The {@link JavaSpace05#contents JavaSpace05.contents}
 *             method can be used to view the space's contents.
 *
 * @author Sun Microsystems, Inc.
 * @since 2.0
 */
public interface ConstrainableJavaSpaceAdmin extends JavaSpaceAdmin {
    /**
     * Return an <code>AdminIterator</code> that will iterate over all
     * the entries in the space that match the given template and are
     * visible under the given transaction.
     * <p>
     * The interactions between other operations on the space and
     * the returned iterator are undefined
     * <p>
     * Note, because this is a convenience method for
     * <code>contents(Entry, Transaction, int,
     * MethodConstraints)</code> the constraints associated with
     * <code>contents(Entry, Transaction, int,
     * MethodConstraints)</code> are used for any calls though this
     * method, not the constraints associated with this method.
     *
     * @param tmpl The iterator should return only entries that match
     *             tmpl
     * @param txn  The iterator should return only entries that match
     *             this transaction
     * @throws RemoteException if communications with the
     *         server is necessary and it can not be completed.
     * @throws TransactionException if there is a problem with
     *         <code>txn</code>.
     * @throws SecurityException If the space is performing 
     *         access control and it can not be confirmed
     *         that the subject making this call has permission
     *         to create an <code>AdminIterator</code> with
     *         the specified template and transaction.
     */
    AdminIterator contents(Entry tmpl, Transaction txn)
	throws TransactionException, RemoteException;

    /**
     * Return an <code>AdminIterator</code> that will iterate over all
     * the entries in the space that match the given template and are
     * visible under the given transaction.
     * <p>
     * The interactions between other operations on the space and
     * the returned iterator are undefined
     * <p>
     * Note, because this is a convenience method for
     * <code>contents(Entry, Transaction, int,
     * MethodConstraints)</code> the constraints associated with
     * <code>contents(Entry, Transaction, int,
     * MethodConstraints)</code> are used for any calls though this
     * method, not the constraints associated with this method.
     *
     * @param tmpl The iterator should return only entries that match
     *             tmpl
     * @param txn  The iterator should return only entries that match
     *             this transaction
     * @param fetchSize advice on how many entries to fetch when the iterator
     *             has to go to the server for more entries.
     * @throws RemoteException if communications with the
     *         server is necessary and it can not be completed.
     * @throws TransactionException if there is a problem with
     *         <code>txn</code>.
     * @throws SecurityException If the space is performing 
     *         access control and it can not be confirmed
     *         that the subject making this call has permission
     *         to create an <code>AdminIterator</code> with
     *         the specified template and transaction.
     * @throws IllegalArgumentException if fetchSize is 
     *         not postive, or <code>USE_DEFUALT</code>.
     */
    AdminIterator contents(Entry tmpl, Transaction txn, int fetchSize)
	throws TransactionException, RemoteException;

    /**
     * Return an <code>AdminIterator</code> that will iterate over all
     * the entries in the space that match the given template and are
     * visible under the given transaction. The returned iterator
     * will support proxy trust verification and will enforce
     * the specified <code>MethodConstraints</code>.
     * <p>
     * The interactions between other operations on the space and
     * the returned iterator are undefined
     * <p>
     * @param tmpl The iterator should return only entries that match
     *             tmpl
     * @param txn  The iterator should return only entries that match
     *             this transaction
     * @param fetchSize advice on how many entries to fetch when the 
     *             iterator has to go to the server for more entries.
     * @param constrains the <code>MethodConstraints</code> the
     *             returned proxy should enforce.
     * @return An object that can be used to iterate over entries
     *         in the space.
     * @throws RemoteException if communications with the
     *         server is necessary and it can not be completed.
     * @throws TransactionException if there is a problem with
     *         <code>txn</code>.
     * @throws SecurityException If the space is performing 
     *         access control and it can not be confirmed
     *         that the subject making this call has permission
     *         to create an <code>AdminIterator</code> with
     *         the specified template and transaction.
     * @throws IllegalArgumentException if fetchSize is 
     *         not postive, or <code>USE_DEFUALT</code>.
     */
    AdminIterator contents(Entry tmpl, Transaction txn, int fetchSize,
			   MethodConstraints constrains)
	throws TransactionException, RemoteException;
}
