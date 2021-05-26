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

import org.apache.river.admin.DestroyAdmin;
import java.io.IOException;
import java.io.InvalidObjectException;
import java.io.ObjectInputStream;
import java.io.ObjectStreamException;
import java.io.Serializable;
import java.rmi.RemoteException;
import net.jini.admin.JoinAdmin;
import net.jini.core.constraint.MethodConstraints;
import net.jini.core.constraint.RemoteMethodControl;
import net.jini.core.discovery.LookupLocator;
import net.jini.core.entry.Entry;
import net.jini.export.ProxyAccessor;
import net.jini.id.ReferentUuid;
import net.jini.id.ReferentUuids;
import net.jini.id.Uuid;
import net.jini.security.proxytrust.ProxyTrustIterator;
import net.jini.security.proxytrust.SingletonProxyTrustIterator;
import org.apache.river.api.io.AtomicSerial;
import org.apache.river.api.io.AtomicSerial.GetArg;

/**
 * A <tt>TxnMgrAdminProxy</tt> is a client-side proxy for a mailbox service. 
 * This interface provides access to the administrative functions 
 * of the mailbox service as defined by the <tt>TxnMgrAdmin</tt> interface.
 *
 * @author Sun Microsystems, Inc.
 *
 * @since 1.1
 */
@AtomicSerial
public class TxnMgrAdminProxy implements DestroyAdmin, JoinAdmin, 
    Serializable, ReferentUuid, ProxyAccessor 
{

    private static final long serialVersionUID = 2L;

    /**
     * The backend server reference
     *
     * @serial
     */
    final TxnManager server;

    /**
     * The backend's service ID
     *
     * @serial
     */
    final Uuid proxyID;

    /**
     * Creates an administrable transaction manager proxy, 
     * returning an instance
     * that implements RemoteMethodControl if the server does too.
     *
     * @param txnMgr the server proxy
     * @param id the ID of the server
     */
    public static TxnMgrAdminProxy create(TxnManager txnMgr, Uuid id) {
        if (txnMgr instanceof RemoteMethodControl) {
            return new ConstrainableTxnMgrAdminProxy(txnMgr, id, null);
        } else {
            return new TxnMgrAdminProxy(txnMgr, id);
        }
    }

    /** Simple constructor. */
    private TxnMgrAdminProxy(TxnManager server, Uuid serviceProxyID) {
	this.server = server;
	this.proxyID = serviceProxyID;
    }

    TxnMgrAdminProxy(GetArg arg) throws IOException, ClassNotFoundException{
	this(check(arg), arg.get("proxyID", null, Uuid.class));
    }
    
    private static TxnManager check(GetArg arg) 
	    throws IOException, ClassNotFoundException{
	TxnManager server = arg.get("server", null, TxnManager.class);
	Uuid proxyID = (Uuid) arg.get("proxyID", null);
    
	/* Verify server */
        if(server == null) {
            throw new InvalidObjectException("TxnMgrAdminProxy.readObject "
                                             +"failure - server "
                                             +"field is null");
        }//endif//endif
        /* Verify proxyID */
        if(proxyID == null) {
            throw new InvalidObjectException("TxnMgrAdminProxy.proxyID "
                                             +"failure - proxyID "
                                             +"field is null");
	}
	return server;
    }

    // This method's javadoc is inherited from an interface of this class
    public Entry[] getLookupAttributes() throws RemoteException {
	return server.getLookupAttributes();
    }

    // This method's javadoc is inherited from an interface of this class
    public void addLookupAttributes(Entry[] attrSets) throws RemoteException {
	server.addLookupAttributes(attrSets);
    }

    // This method's javadoc is inherited from an interface of this class
    public void modifyLookupAttributes(Entry[] attrSetTemplates,
				       Entry[] attrSets)
	throws RemoteException
    {
	server.modifyLookupAttributes(attrSetTemplates, attrSets);
    }

    // This method's javadoc is inherited from an interface of this class
    public String[] getLookupGroups() throws RemoteException {
	return server.getLookupGroups();
    }

    // This method's javadoc is inherited from an interface of this class
    public void addLookupGroups(String[] groups) throws RemoteException {
	server.addLookupGroups(groups);
    }

    // This method's javadoc is inherited from an interface of this class
    public void removeLookupGroups(String[] groups) throws RemoteException {
	server.removeLookupGroups(groups);
    }

    // This method's javadoc is inherited from an interface of this class
    public void setLookupGroups(String[] groups) throws RemoteException {
	server.setLookupGroups(groups);
    }

    // This method's javadoc is inherited from an interface of this class
    public LookupLocator[] getLookupLocators() throws RemoteException {
	return server.getLookupLocators();
    }

    // This method's javadoc is inherited from an interface of this class
    public void addLookupLocators(LookupLocator[] locators)
	throws RemoteException
    {
	server.addLookupLocators(locators);
    }

    // This method's javadoc is inherited from an interface of this class
    public void removeLookupLocators(LookupLocator[] locators)
	throws RemoteException
    {
	server.removeLookupLocators(locators);
    }

    // This method's javadoc is inherited from an interface of this class
    public void setLookupLocators(LookupLocator[] locators)
	throws RemoteException
    {
	server.setLookupLocators(locators);
    }

    // This method's javadoc is inherited from an interface of this class
    public void destroy() throws RemoteException {
	server.destroy();
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

    // documentation inherited from supertype
    public int hashCode() {
	return proxyID.hashCode();
    }

    /** Proxies for servers with the same ID are considered equal. */
    public boolean equals(Object o) {
        return ReferentUuids.compare(this,o);
    }
    
    public Object getProxy() {
	return server;
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
        if(server == null) {
            throw new InvalidObjectException("TxnMgrAdminProxy.readObject "
                                             +"failure - server "
                                             +"field is null");
        }//endif
        /* Verify proxyID */
        if(proxyID == null) {
            throw new InvalidObjectException("TxnMgrAdminProxy.proxyID "
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
                                         +"deserialize MailboxProxy instance");
    }//end readObjectNoData

    @AtomicSerial
    static final class ConstrainableTxnMgrAdminProxy extends TxnMgrAdminProxy
                                               implements RemoteMethodControl
    {
        private static final long serialVersionUID = 2L;

        /** Constructs a new <code>ConstrainableTxnMgrAdminProxy</code> 
	 *  instance.
         *  <p>
         *  For a description of all but the <code>methodConstraints</code>
         *  argument (provided below), refer to the description for the
         *  constructor of this class' super class.
         *
         *  @param methodConstraints the client method constraints to place on
         *                           this proxy (may be <code>null</code>).
         */
        private ConstrainableTxnMgrAdminProxy(TxnManager server, 
                                   Uuid proxyID,
                                   MethodConstraints methodConstraints)
        {
            super( constrainServer(server, methodConstraints), proxyID);
        }//end constructor

	ConstrainableTxnMgrAdminProxy(GetArg arg) 
		throws IOException, ClassNotFoundException {
	    super(check(arg));
	}
	
	private static GetArg check(GetArg arg) 
		throws IOException, ClassNotFoundException{
	    TxnMgrAdminProxy p = new TxnMgrAdminProxy(arg);
	    // Verify that the server implements RemoteMethodControl
            if( !(p.server instanceof RemoteMethodControl) ) {
                throw new InvalidObjectException
                              ("TxnMgrAdminProxy.readObject failure - server "
                               +"does not implement RemoteMethodControl");
	    }
	    return arg;
	}

        /** Returns a copy of the given server proxy having the client method
         *  constraints that result after the specified method mapping is
         *  applied to the given client method constraints.
         */
        private static TxnManager constrainServer(TxnManager server,
                                                MethodConstraints constraints)
        {
            RemoteMethodControl constrainedServer = 
                ((RemoteMethodControl)server).setConstraints(constraints);

            return ((TxnManager)constrainedServer);
        }//end constrainServer

        /** Returns a new copy of this proxy class 
         *  (<code>ConstrainableTxnMgrAdminProxy</code>) with its client
         *  constraints set to the specified constraints. A <code>null</code>
         *  value is interpreted as mapping all methods to empty constraints.
         */
        public RemoteMethodControl setConstraints
                                              (MethodConstraints constraints)
        {
            return (new ConstrainableTxnMgrAdminProxy(server, 
						       proxyID, constraints));
        }//end setConstraints

        /** Returns the client constraints placed on the current instance
         *  of this proxy class (<code>ConstrainableTxnMgrAdminProxy</code>).
         *  The value returned by this method can be <code>null</code>,
         *  which is interpreted as mapping all methods to empty constraints.
         */
        public MethodConstraints getConstraints() {
            return ( ((RemoteMethodControl)server).getConstraints() );
        }//end getConstraints

        /* Note that the superclass's hashCode method is OK as is. */
        /* Note that the superclass's equals method is OK as is. */

        /** Returns a proxy trust iterator that is used in 
         *  <code>ProxyTrustVerifier</code> to retrieve this object's
         *  trust verifier.
         */
        private ProxyTrustIterator getProxyTrustIterator() {
	    return new SingletonProxyTrustIterator(server);
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
            if( !(server instanceof RemoteMethodControl) ) {
                throw new InvalidObjectException
                              ("TxnMgrAdminProxy.readObject failure - server "
                               +"does not implement RemoteMethodControl");
            }//endif
        }//end readObject  

    }//end class ConstrainableTxnMgrAdminProxy
}

