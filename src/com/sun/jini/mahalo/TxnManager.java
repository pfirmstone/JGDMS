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
package com.sun.jini.mahalo;

import net.jini.admin.Administrable;
import net.jini.admin.JoinAdmin;
import com.sun.jini.admin.DestroyAdmin;
import com.sun.jini.landlord.Landlord;
import com.sun.jini.start.ServiceProxyAccessor;
import java.rmi.Remote;
import java.rmi.RemoteException;
import net.jini.core.transaction.Transaction;
import net.jini.core.transaction.UnknownTransactionException;
import net.jini.core.transaction.server.TransactionManager;

/**
 * Encapsulates the interface of an implementation of
 * a <code>TransactionManager</code>.
 *
 * @author Sun Microsystems, Inc.
 *
 */
public interface TxnManager extends Remote, Landlord, DestroyAdmin,
    Administrable, JoinAdmin, ServiceProxyAccessor, TransactionManager
{
    /** Logger and configuration component name for Norm */
    public static final String MAHALO = "com.sun.jini.mahalo";

    /**
     * Returns a reference to the <code>TransactionManager</code>
     * interface.
     */
    public TransactionManager manager() throws RemoteException;

    /**
     * Retrieves a <code>Transaction</code> given the
     * transaction's ID.
     *
     * @param id the id
     */
    public Transaction getTransaction(long id)
	throws RemoteException, UnknownTransactionException;
}
