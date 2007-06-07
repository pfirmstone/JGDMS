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

import com.sun.jini.admin.DestroyAdmin;

import java.rmi.RemoteException;

import net.jini.admin.JoinAdmin;
import net.jini.core.entry.Entry;
import net.jini.core.transaction.Transaction;
import net.jini.core.transaction.TransactionException;
import net.jini.space.JavaSpace;
import net.jini.space.JavaSpace05;

/**
 * This interface contains all the administrative methods that Outrigger
 * provides to control its JavaSpaces<sup><font size=-2>TM</font></sup> 
 * service. <p>
 *
 * @deprecated Invoke the {@link JoinAdmin} and {@link
 *             DestroyAdmin} methods though those interfaces.  A view
 *             of the space contents can be obtained through the
 *             {@link JavaSpace05#contents JavaSpace05.contents}
 *             method.
 *
 * @author Sun Microsystems, Inc.
 */
public interface JavaSpaceAdmin extends JoinAdmin, DestroyAdmin {
    /** 
     * Can be passed to <code>contents</code> to indicate
     * no preference for the fetch size.
     */
    int USE_DEFAULT = -1;

    /**
     * Return the space that this administrative object governs.
     * @throws RemoteException if communications with the
     *         server is necessary and it can not be completed.
     */
    JavaSpace space() throws RemoteException;

    /**
     * Return an <code>AdminIterator</code> that will iterate over all
     * the entries in the space that match the given template and are
     * visible under the given transaction.
     * <p>
     * The interactions between other operations on the space and
     * the returned iterator are undefined
     * <p>
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
     *         not positive, or <code>USE_DEFAULT</code>.
     */
    AdminIterator contents(Entry tmpl, Transaction txn, int fetchSize)
	throws TransactionException, RemoteException;
}
