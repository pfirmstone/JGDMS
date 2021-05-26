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
package org.apache.river.mahalo.proxy;

import java.io.IOException;
import java.io.InvalidObjectException;
import java.io.ObjectInputStream;
import java.io.ObjectStreamException;
import java.io.Serializable;
import java.rmi.RemoteException;
import net.jini.admin.Administrable;
import net.jini.core.constraint.MethodConstraints;
import net.jini.core.constraint.RemoteMethodControl;
import net.jini.core.lease.LeaseDeniedException;
import net.jini.core.transaction.CannotAbortException;
import net.jini.core.transaction.CannotCommitException;
import net.jini.core.transaction.CannotJoinException;
import net.jini.core.transaction.TimeoutExpiredException;
import net.jini.core.transaction.UnknownTransactionException;
import net.jini.core.transaction.server.CrashCountException;
import net.jini.core.transaction.server.TransactionManager;
import net.jini.core.transaction.server.TransactionManager.Created;
import net.jini.core.transaction.server.TransactionParticipant;
import net.jini.export.ProxyAccessor;
import net.jini.id.ReferentUuid;
import net.jini.id.ReferentUuids;
import net.jini.id.Uuid;
import net.jini.security.proxytrust.ProxyTrustIterator;
import net.jini.security.proxytrust.SingletonProxyTrustIterator;
import org.apache.river.api.io.AtomicSerial;
import org.apache.river.api.io.AtomicSerial.GetArg;

/**
 * A <code>TxnMgrProxy</code> is a proxy for the 
 * transaction manager service.
 * This is the object passed to clients of this service.
 * It implements the <code>TransactionManager</code> and the 
 * <code>Administrable</code> interfaces.
 *
 * @author Sun Microsystems, Inc.
 *
 * @since 1.1
 */
@AtomicSerial
public class TxnMgrProxy implements TransactionManager, Administrable, Serializable, 
    ReferentUuid, ProxyAccessor
{

    private static final long serialVersionUID = 2L;

    /**
     * The reference to the transaction manager service implementation
     *
     * @serial
     */
    final TxnManager backend;

    /**
     * The proxy's <code>Uuid</code>
     *
     * @serial
     */
    final Uuid proxyID;

    /**
     * Creates a transaction manager proxy, returning an instance
     * that implements RemoteMethodControl if the server does too.
     *
     * @param txnMgr the server proxy
     * @param id the ID of the server
     */
    public static TxnMgrProxy create(TxnManager txnMgr, Uuid id) {
        if (txnMgr instanceof RemoteMethodControl) {
            return new ConstrainableTxnMgrProxy(check(txnMgr, id), id, null);
        } else {
            return new TxnMgrProxy(check(txnMgr, id), id);
        }
    }

    /** Convenience constructor. */
    private TxnMgrProxy(TxnManager txnMgr, Uuid id) {
	this.backend = txnMgr;
	this.proxyID = id;
    }
    
    TxnMgrProxy (GetArg arg) throws IOException, ClassNotFoundException {
	this(check(arg), arg.get("proxyID", null, Uuid.class));
    }
    
    private static TxnManager check(TxnManager txnMgr, Uuid id){
        if (txnMgr == null || id == null) {
            throw new IllegalArgumentException("Cannot accept null arguments");
        }
	 return txnMgr;
    }
    
    private static TxnManager check(GetArg arg) throws IOException {
	try {
	    return check((TxnManager) arg.get("backend", null),
		    (Uuid) arg.get("proxyID", null));
	} catch (IllegalArgumentException ex){
	    InvalidObjectException e = new InvalidObjectException("Invariants unsatisfied");
	    e.initCause(ex);
	    throw e;
	}
    }
    
    public Created create(long lease) 
	throws LeaseDeniedException, RemoteException 
    {
	return backend.create(lease);
    }

    public void join(long id, TransactionParticipant part, long crashCount)
	throws UnknownTransactionException, CannotJoinException,
	       CrashCountException, RemoteException
    {
	backend.join(id, part, crashCount);
    }

    public int getState(long id) 
	throws UnknownTransactionException, RemoteException
    {
	return backend.getState(id);
    }

    public void commit(long id)
	throws UnknownTransactionException, CannotCommitException,
	       RemoteException
    {
	backend.commit(id);
    }

    public void commit(long id, long waitFor)
        throws UnknownTransactionException, CannotCommitException,
               TimeoutExpiredException, RemoteException
    {
	backend.commit(id, waitFor);
    }

    public void abort(long id)
	throws UnknownTransactionException, CannotAbortException,
	       RemoteException
    {
	backend.abort(id);
    }

    public void abort(long id, long waitFor)
	throws UnknownTransactionException, CannotAbortException,
               TimeoutExpiredException, RemoteException
    {
	backend.abort(id, waitFor);
    }

    // inherit javadoc from parent
    public Object getAdmin() throws RemoteException {
        return backend.getAdmin();
    }

    /* From net.jini.id.ReferentUuid */
    /**
     * Returns the universally unique identifier that has been assigned to the
     * resource this proxy represents.
     *
     * @return the instance of <code>Uuid</code> that is associated with the
     *         resource this proxy represents. This method will not return
     *         <code>null</code>.
     *
     * @see net.jini.id.ReferentUuid
     */
    public Uuid getReferentUuid() {
        return proxyID;
    }

    /** Proxies for servers with the same proxyID have the same hash code. */
    public int hashCode() {
	return proxyID.hashCode();
    }

    /** 
     * Proxies for servers with the same <code>proxyID</code> are 
     * considered equal. 
     */
    public boolean equals(Object o) {
	return ReferentUuids.compare(this,o);
    }
    
    /** When an instance of this class is deserialized, this method is
     *  automatically invoked. This implementation of this method validates
     *  the state of the deserialized instance.
     *
     * @throws InvalidObjectException if the state of the
     *         deserialized instance of this class is found to be invalid.
     */
    private void readObject(ObjectInputStream s)
                               throws IOException, ClassNotFoundException
    {
        s.defaultReadObject();
        /* Verify server */
        if(backend == null) {
            throw new InvalidObjectException("TxnMgrProxy.readObject "
                                             +"failure - backend "
                                             +"field is null");
        }//endif
        /* Verify proxyID */
        if(proxyID == null) {
            throw new InvalidObjectException("TxnMgrProxy.proxyID "
                                             +"failure - proxyID "
                                             +"field is null");
        }//endif
    }//end readObject

    /** During deserialization of an instance of this class, if it is found
     *  that the stream contains no data, this method is automatically
     *  invoked. Because it is expected that the stream should always
     *  contain data, this implementation of this method simply declares
     *  that something must be wrong.
     *
     * @throws InvalidObjectException to indicate that there
     *         was no data in the stream during deserialization of an
     *         instance of this class; declaring that something is wrong.
     */
    private void readObjectNoData() throws ObjectStreamException {
        throw new InvalidObjectException("no data found when attempting to "
                                         +"deserialize TxnMgrProxy instance");
    }//end readObjectNoData

    @Override
    public Object getProxy() {
	return backend;
    }
   
    /** A subclass of TxnMgrProxy that implements RemoteMethodControl. */
    @AtomicSerial
    final static class ConstrainableTxnMgrProxy extends TxnMgrProxy
        implements RemoteMethodControl
    {
        private static final long serialVersionUID = 2L;

        /** Creates an instance of this class. */
        private ConstrainableTxnMgrProxy(TxnManager txnMgr, Uuid id,
            MethodConstraints methodConstraints)
        {
            super(constrainServer(txnMgr, methodConstraints),
                  id);
        }

	ConstrainableTxnMgrProxy(GetArg arg) 
		throws IOException, ClassNotFoundException {
	    super(check(arg));
	}
	
	private static GetArg check(GetArg arg) 
		throws IOException, ClassNotFoundException {
	    TxnMgrProxy p = new TxnMgrProxy(arg);
	    // Verify that the server implements RemoteMethodControl
            if( !(p.backend instanceof RemoteMethodControl) ) {
                throw new InvalidObjectException(
		    "ConstrainableTxnMgrProxy.readObject failure - backend " +
		    "does not implement constrainable functionality ");
            }//endif
	    return arg;
	}

       /**
         * Returns a copy of the server proxy with the specified client
         * constraints and methods mapping.
         */
        private static TxnManager constrainServer(
            TxnManager txnMgr,
            MethodConstraints methodConstraints)
        {
            return (TxnManager)
                ((RemoteMethodControl)txnMgr).setConstraints(methodConstraints);
        }

        /** {@inheritDoc} */
        public RemoteMethodControl setConstraints(
            MethodConstraints constraints)
        {
            return new ConstrainableTxnMgrProxy(backend, proxyID,
                constraints);
        }

        /** {@inheritDoc} */
        public MethodConstraints getConstraints() {
            return ((RemoteMethodControl) backend).getConstraints();
        }

        /* Note that the superclass's hashCode method is OK as is. */
        /* Note that the superclass's equals method is OK as is. */

        /**
         * Returns a proxy trust iterator that is used in
         * <code>ProxyTrustVerifier</code> to retrieve this object's
         * trust verifier.
         */
        private ProxyTrustIterator getProxyTrustIterator() {
            return new SingletonProxyTrustIterator(backend);
        }//end getProxyTrustIterator
	
	/** Performs various functions related to the trust verification
         *  process for the current instance of this proxy class, as
         *  detailed in the description for this class.
         *
         * @throws <code>InvalidObjectException</code> if any of the
         *         requirements for trust verification (as detailed in the
         *         class description) are not satisfied.
         */
        private void readObject(ObjectInputStream s)
                                   throws IOException, ClassNotFoundException
        {
	    /* Note that basic validation of the fields of this class was
             * already performed in the readObject() method of this class'
             * super class.
             */
            s.defaultReadObject();
	    // Verify that the server implements RemoteMethodControl
            if( !(backend instanceof RemoteMethodControl) ) {
                throw new InvalidObjectException(
		    "ConstrainableTxnMgrProxy.readObject failure - backend " +
		    "does not implement constrainable functionality ");
            }//endif
        }//end readObject 
    }
}
