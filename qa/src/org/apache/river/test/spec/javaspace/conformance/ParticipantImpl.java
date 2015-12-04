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
package org.apache.river.test.spec.javaspace.conformance;

// java.io
import java.io.ObjectStreamException;
import java.io.Serializable;

// java.rmi
import java.rmi.RemoteException;

// net.jini
import net.jini.core.transaction.server.TransactionParticipant;
import net.jini.core.transaction.server.TransactionManager;
import net.jini.core.transaction.server.TransactionConstants;
import net.jini.core.transaction.UnknownTransactionException;

import net.jini.config.Configuration;
import net.jini.config.ConfigurationException;
import net.jini.export.Exporter;
import net.jini.security.TrustVerifier;
import net.jini.security.proxytrust.ServerProxyTrust;

import org.apache.river.proxy.BasicProxyTrustVerifier;

import org.apache.river.qa.harness.QAConfig;
import java.rmi.server.ExportException;

/**
 * This class is needed to prevent normal committing operation.
 *
 * @author Mikhail A. Markov
 */
public class ParticipantImpl implements TransactionParticipant, 
					TransactionConstants,
					ServerProxyTrust,
					Serializable 
{


    private static Configuration configuration;
    private final Exporter exporter;
    private Object proxy;

    public static void setConfiguration(Configuration configuration) {
	ParticipantImpl.configuration = configuration;
    }

    public static Configuration getConfiguration() {
	if (ParticipantImpl.configuration == null) {
	    throw new IllegalStateException("Configuration not set");
	}
	return ParticipantImpl.configuration;
    }

    /**
     * Default Constructor requiring no arguments.
     */
    public ParticipantImpl() throws RemoteException {
	Configuration c = getConfiguration();
	Exporter exporter = QAConfig.getDefaultExporter();
	if (c instanceof org.apache.river.qa.harness.QAConfiguration) {  
	    try {
		exporter = (Exporter) c.getEntry("test",
						 "transactionParticipantExporter",
						 Exporter.class);
	    } catch (ConfigurationException e) {
		throw new RemoteException("Configuration Error", e);
	    }
	}
	this.exporter = exporter;
    }
    
    public synchronized void export() throws ExportException{
        proxy = exporter.export(this);
    }

    public synchronized Object writeReplace() throws ObjectStreamException {
	return proxy;
    }

    public synchronized TrustVerifier getProxyVerifier() {
	return new BasicProxyTrustVerifier(proxy);
    }

    /**
     * A combination of <code>prepare</code> and <code>commit</code>, which
     * can be used by the manager when there is just one participant left to
     * prepare and all other participants (if any) have responded with
     * <code>NOTCHANGED</code>.
     *
     * This method is needed to implement TransactionParticipant interface.
     * It always returns ABORTED to prevent normal committing operation.
     *
     * @param mgr the manager of the transaction
     * @param id the transaction ID
     *
     * @return an <code>int</code> representing its state
     *
     * @throws UnknownTransactionException if the transaction
     *         is unknown to the transaction manager, either
     *         because the transaction ID is incorrect or because the
     *         transaction is complete and its state has been
     *         discarded by the manager.
     * @throws RemoteException if there is a communication error
     *
     * @see #prepare
     * @see #commit
     */
    public int prepareAndCommit(TransactionManager mgr, long id)
            throws UnknownTransactionException, RemoteException {
        return ABORTED;
    }

    /**
     * Requests that the participant roll back any changes for the specified
     * transaction and unlock any resources locked by the transaction.
     * All state associated with the transaction can then be discarded
     * by the participant.
     *
     * This method is needed to implement TransactionParticipant interface.
     * It is empty.
     *
     * @param mgr the manager of the transaction
     * @param id the transaction ID
     *
     * @throws UnknownTransactionException if the transaction
     *         is unknown to the transaction manager, either
     *         because the transaction ID is incorrect or because the
     *         transaction is complete and its state has been
     *         discarded by the manager.
     * @throws RemoteException if there is a communication error
     *
     */
    public void abort(TransactionManager mgr, long id)
            throws UnknownTransactionException, RemoteException {}

    /**
     * Requests that the participant make all of its <code>PREPARED</code>
     * changes for the specified transaction visible outside of the
     * transaction and unlock any resources locked by the transaction.
     *
     * This method is needed to implement TransactionParticipant interface.
     * It is empty.
     *
     * @param mgr the manager of the transaction
     * @param id the transaction ID
     *
     * @throws UnknownTransactionException if the transaction
     *         is unknown to the transaction manager, either
     *         because the transaction ID is incorrect or because the
     *         transaction is complete and its state has been
     *         discarded by the manager.
     * @throws RemoteException if there is a communication error
     *
     */
    public void commit(TransactionManager mgr, long id)
            throws UnknownTransactionException, RemoteException {}

    /**
     * Requests that the participant prepare itself to commit the transaction,
     * and to vote on the outcome of the transaction.
     *
     * This method is needed to implement TransactionParticipant interface.
     * It always returns ABORTED to prevent normal committing operation.
     *
     * @param mgr the manager of the transaction
     * @param id the transaction ID
     *
     * @return an <code>int</code> representing this participant's state
     *
     * @throws UnknownTransactionException if the transaction
     *         is unknown to the transaction manager, either
     *         because the transaction ID is incorrect or because the
     *         transaction is complete and its state has been
     *         discarded by the manager.
     * @throws RemoteException if there is a communication error
     *
     */
    public int prepare(TransactionManager mgr, long id)
            throws UnknownTransactionException, RemoteException {
        return ABORTED;
    }
}
