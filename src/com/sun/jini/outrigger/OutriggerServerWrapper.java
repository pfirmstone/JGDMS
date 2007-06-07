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

import java.util.Map;
import java.io.IOException;
import java.rmi.Remote;
import java.rmi.MarshalledObject;
import java.rmi.RemoteException;
import java.rmi.activation.ActivationID;
import java.rmi.activation.ActivationException;
import javax.security.auth.login.LoginException;

import net.jini.core.discovery.LookupLocator;
import net.jini.core.entry.Entry;
import net.jini.core.event.EventRegistration;
import net.jini.core.event.RemoteEventListener;
import net.jini.core.transaction.Transaction;
import net.jini.core.transaction.TransactionException;
import net.jini.core.transaction.UnknownTransactionException;
import net.jini.core.transaction.server.TransactionManager;
import net.jini.core.lease.Lease;
import net.jini.core.lease.LeaseDeniedException;
import net.jini.core.lease.UnknownLeaseException;
import net.jini.export.ProxyAccessor;
import net.jini.space.JavaSpace;
import net.jini.space.InternalSpaceException;

import net.jini.security.TrustVerifier;
import net.jini.security.proxytrust.ServerProxyTrust;
import net.jini.config.ConfigurationException;
import net.jini.id.Uuid;

import com.sun.jini.start.LifeCycle;

/**
 * For various reasons there is code that we would like
 * to run before every incoming remote call. To accomplish
 * this we wrap the server in an object that will run
 * the common code and then delegate to the server to
 * do the actual work. This is a base class for these
 * wrappers.
 *
 * @author Sun Microsystems, Inc.
 * @since 2.0
 */
class OutriggerServerWrapper 
    implements OutriggerServer, ServerProxyTrust, ProxyAccessor
{
    /** The object being delegated to */     
    private final OutriggerServerImpl delegate;

    /** 
     * If <code>false</code>, hold calls until it becomes
     * <code>true</code> 
     */
    private boolean allowCalls;

    /** 
     * If non-null cause incoming calls to immediately throw this
     * exception. Takes presidents over <code>holdCalls</code>. This
     * field is only set to an <code>Error</code>,
     * <code>RemoteException</code>, or <code>RuntimeException</code>
     * and thus can be thrown by an of <code>OutriggerServer</code>'s
     * methods.  
     */
    private Throwable failCallsWith;

    /**
     * Create an <code>OutriggerServerWrapper</code> that
     * will delegate to a non-activatable <code>OutriggerServerImpl</code>
     * created with the specified configuration and wrapped by
     * <code>this</code>.
     * @param configArgs set of strings to be used to obtain a
     *                   <code>Configuration</code>.
     * @param lifeCycle the object to notify when this
     *                  service is destroyed.
     * @param persistent If <code>true</code> will throw an 
     *                   <code>ConfigurationException</code>
     *                   if there is no persistence directory or
     *                   store specified in the configuration.
     * @throws IOException if there is problem exporting the server.
     * @throws ConfigurationException if the <code>Configuration</code> is 
     *         malformed.
     * @throws LoginException if the <code>loginContext</code> specified
     *         in the configuration is non-null and throws 
     *         an exception when login is attempted.
     */
    OutriggerServerWrapper(String[] configArgs, LifeCycle lifeCycle, 
			   boolean persistent) 
	throws IOException, ConfigurationException, LoginException
    {
	try {
	    delegate = new OutriggerServerImpl(null, lifeCycle, configArgs,
					       persistent, this);
	} catch (ActivationException e) {
	    throw new AssertionError(e);
	}
    }

    /**
     * Create an <code>OutriggerServerWrapper</code> that
     * will delegate to an <code>OutriggerServerImpl</code>
     * created with the specified argument and wrapped by <code>this</code>.
     * @param activationID of the server, must not be <code>null</code>.
     * @param configArgs set of strings to be used to obtain a
     *                   <code>Configuration</code>.
     * @throws IOException if there is problem recovering data
     *         from disk, exporting the server, or unpacking 
     *         <code>data</code>.
     * @throws ConfigurationException if the <code>Configuration</code> is 
     *         malformed.
     * @throws ActivationException if activatable and there
     *         is a problem getting a reference to the activation system.
     * @throws LoginException if the <code>loginContext</code> specified
     *         in the configuration is non-null and throws 
     *         an exception when login is attempted.
     * @throws NullPointerException if <code>activationID</code>
     *         is <code>null</code>.
     */
    OutriggerServerWrapper(ActivationID activationID, String[] configArgs) 
	throws IOException, ConfigurationException, LoginException,
	       ActivationException
    {
	if (activationID == null)
	    throw new NullPointerException("activationID must be non-null");
	delegate = new OutriggerServerImpl(activationID, null, configArgs,
					   true, this);
    }

    /**
     * Cause incoming calls to block until further notice.
     */
    synchronized void holdCalls() {
	failCallsWith = null;
	allowCalls = false;
	notifyAll();
    }

    /**
     * Cause in new or blocked calls to fail with
     * the specified exception.
     * @throws IllegalArgumentException if <code>t</code>
     * is not an <code>Error</code>, <code>RemoteException</code>,
     * or <code>RuntimeException</code>.
     * @throws NullPointerException if <code>t</code> is <code>null</code>.
     */
    synchronized void rejectCalls(Throwable t) {
	if (t == null)
	    throw new NullPointerException("Throwable must not be null");

	if (!((t instanceof Error) || 
	      (t instanceof RuntimeException) ||
	      (t instanceof RemoteException)))
	    throw new IllegalArgumentException("t must be an exception " +
		"that can be thrown from any of OutriggerServer's methods");

	failCallsWith = t;
	allowCalls = true;
	notifyAll();
    }

    /**
     * Allow incoming calls.
     */
    synchronized void allowCalls() {
	failCallsWith = null;
	allowCalls = true;
	notifyAll();
    }
    
    /**
     * Block until calls are allowed, or until calls
     * are to be rejected.
     * @throws RemoteException If calls are being rejected
     *         with <code>RemoteException</code>s.
     * @throws RuntimeException If calls are being rejected
     *         with <code>RuntimeException</code>s.
     * @throws Error If calls are being rejected
     *         with <code>Error</code>s.
     */
    private synchronized void gate() throws RemoteException {
	while (!allowCalls || failCallsWith != null) {
	    if (failCallsWith != null) {
		if (failCallsWith instanceof RemoteException)
		    throw (RemoteException)failCallsWith;
		else if (failCallsWith instanceof Error)
		    throw (Error)failCallsWith;
		else if (failCallsWith instanceof RuntimeException) 
		    throw (RuntimeException)failCallsWith;
		else
		    throw new AssertionError("Wrapper trying to " +
					     "throw " + failCallsWith);
	    }

	    if (!allowCalls) {
		try {
		    wait();
		} catch (InterruptedException e) {
		    throw new 
			InternalSpaceException("gate method interrupted");
		}
	    }
	}
    }
	        

    public long[] write(EntryRep entry, Transaction txn, long lease)
	throws TransactionException, RemoteException 
    {
	gate();
	return delegate.write(entry, txn, lease);
    }

    public Object read(EntryRep tmpl, Transaction txn, long timeout,
		       QueryCookie cookie)
	throws TransactionException, RemoteException, InterruptedException
    {
	gate();
	return delegate.read(tmpl, txn, timeout, cookie);
    }

    public Object readIfExists(EntryRep tmpl, Transaction txn, long timeout,
			       QueryCookie cookie)
	throws TransactionException, RemoteException, InterruptedException
    {
	gate();
	return delegate.readIfExists(tmpl, txn, timeout, cookie);
    }

    public Object take(EntryRep tmpl, Transaction txn, long timeout,
		       QueryCookie cookie)
	throws TransactionException, RemoteException, InterruptedException
    {
	gate();
	return delegate.take(tmpl, txn, timeout, cookie);
    }

    public Object takeIfExists(EntryRep tmpl, Transaction txn, long timeout,
			       QueryCookie cookie)
	throws TransactionException, RemoteException, InterruptedException
    {
	gate();
	return delegate.takeIfExists(tmpl, txn, timeout, cookie);
    }

    public EventRegistration
	notify(EntryRep tmpl, Transaction txn, RemoteEventListener listener,
	       long lease, MarshalledObject handback)
	throws TransactionException, RemoteException 
    {
	gate();
	return delegate.notify(tmpl, txn, listener, lease, handback);
    }

    public EventRegistration registerForAvailabilityEvent(EntryRep[] tmpls,
	    Transaction txn, boolean visibilityOnly, RemoteEventListener listener,
  	    long leaseTime, MarshalledObject handback)
        throws TransactionException, RemoteException
    {
	gate();
	return delegate.registerForAvailabilityEvent(tmpls, txn, visibilityOnly,
	    listener, leaseTime, handback);
    }

    public long[] write(EntryRep[] entries, Transaction txn, long[] leaseTimes)
        throws TransactionException, RemoteException
    {
	gate();
	return delegate.write(entries, txn, leaseTimes);
    }

    public Object take(EntryRep[] tmpls, Transaction tr, long timeout,
		       int limit, QueryCookie cookie)
	throws TransactionException, RemoteException
    {
	gate();
	return delegate.take(tmpls, tr, timeout, limit, cookie);
    }

    public MatchSetData contents(EntryRep[] tmpls, Transaction tr,
				 long leaseTime, long limit)
        throws TransactionException, RemoteException
    {
	gate();
	return delegate.contents(tmpls, tr, leaseTime, limit);
    }

    public EntryRep[] nextBatch(Uuid contentsQueryUuid, Uuid entryUuid)
        throws RemoteException
    {
	gate();
	return delegate.nextBatch(contentsQueryUuid, entryUuid);
    }

    public long renew(Uuid cookie, long extension)
	throws LeaseDeniedException, UnknownLeaseException, RemoteException
    {
	gate();
	return delegate.renew(cookie, extension);
    }

    public void cancel(Uuid cookie)
	throws UnknownLeaseException, RemoteException
    {
	gate();
	delegate.cancel(cookie);
    }

    public Object getAdmin() throws RemoteException {
	gate();
	return delegate.getAdmin();
    }

    public int prepare(TransactionManager mgr, long id)
	throws UnknownTransactionException, RemoteException 
    {
	gate();
	return delegate.prepare(mgr, id);
    }

    public void commit(TransactionManager mgr, long id)
	throws UnknownTransactionException, RemoteException 
    {
	gate();
	delegate.commit(mgr, id);
    }

    public void abort(TransactionManager mgr, long id)
	throws UnknownTransactionException, RemoteException 
    {
	gate();
	delegate.abort(mgr, id);
    }

    public int prepareAndCommit(TransactionManager mgr, long id)
	throws UnknownTransactionException, RemoteException 
    {
	gate();
	return delegate.prepareAndCommit(mgr, id);
    }
    
    public RenewResults renewAll(Uuid[] cookies, long[] durations)
	throws RemoteException
    {
	gate();
	return delegate.renewAll(cookies, durations);
    }

    public Map cancelAll(Uuid[] cookies) throws RemoteException {
	gate();
	return delegate.cancelAll(cookies);
    }

    public Object getServiceProxy() throws RemoteException {
	gate();
	return delegate.getServiceProxy();
    }

    public JavaSpace space() throws RemoteException {
	gate();
	return delegate.space();
    }

    public Uuid contents(EntryRep tmpl, Transaction txn)
        throws TransactionException, RemoteException
    {
	gate();
	return delegate.contents(tmpl, txn);
    }

    public EntryRep[] nextReps(Uuid iterationUuid, int max, 
			       Uuid entryUuid) 
	throws RemoteException
    {
	gate();
	return delegate.nextReps(iterationUuid, max, entryUuid);
    }

    public void delete(Uuid iterationUuid, Uuid entryUuid) 
	throws RemoteException
    {
	gate();
	delegate.delete(iterationUuid, entryUuid);
    }

    public void close(Uuid iterationUuid) throws RemoteException {
	gate();
	delegate.close(iterationUuid);
    }

    public void destroy() throws RemoteException {
	gate();
	delegate.destroy();
    }

    public Entry[] getLookupAttributes() throws RemoteException {
	gate();
	return delegate.getLookupAttributes();
    }

    public void addLookupAttributes(Entry[] attrSets) 
	throws RemoteException 
    {
	gate();
	delegate.addLookupAttributes(attrSets);
    }

    public void modifyLookupAttributes(Entry[] attrSetTemplates,
				       Entry[] attrSets)
       throws RemoteException
    {
	gate();
	delegate.modifyLookupAttributes(attrSetTemplates, attrSets);
    }

    public String[] getLookupGroups() throws RemoteException {
	gate();
	return delegate.getLookupGroups();
    }

    public void addLookupGroups(String[] groups) throws RemoteException {
	gate();
	delegate.addLookupGroups(groups);
    }

    public void removeLookupGroups(String[] groups) throws RemoteException {
	gate();
	delegate.removeLookupGroups(groups);
    }

    public void setLookupGroups(String[] groups) throws RemoteException {
	gate();
	delegate.setLookupGroups(groups);
    }

    public LookupLocator[] getLookupLocators() throws RemoteException {
	gate();
	return delegate.getLookupLocators();
    }

    public void addLookupLocators(LookupLocator[] locators) 
	throws RemoteException 
    {
	gate();
	delegate.addLookupLocators(locators);
    }

    public void removeLookupLocators(LookupLocator[] locators) 
	throws RemoteException 
    {
	gate();
	delegate.removeLookupLocators(locators);
    }

    public void setLookupLocators(LookupLocator[] locators) 
	throws RemoteException 
    {
	gate();
	delegate.setLookupLocators(locators);
    }

    public Object getProxy() {
	// don't need to block or die on this one.
	return delegate.getProxy();
    }

    public TrustVerifier getProxyVerifier() throws RemoteException {
	gate();
	return delegate.getProxyVerifier();
    }
}
