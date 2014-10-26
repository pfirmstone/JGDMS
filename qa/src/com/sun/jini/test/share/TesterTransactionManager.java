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
package com.sun.jini.test.share;

import com.sun.jini.qa.harness.QAConfig;
import java.io.ObjectStreamException;
import java.io.Serializable;
import java.rmi.RemoteException;
import java.security.PrivilegedActionException;
import java.security.PrivilegedExceptionAction;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.security.auth.Subject;
import javax.security.auth.login.LoginContext;
import net.jini.config.Configuration;
import net.jini.config.ConfigurationException;
import net.jini.core.transaction.*;
import net.jini.core.transaction.server.*;
import net.jini.export.Exporter;
import net.jini.security.TrustVerifier;
import net.jini.security.proxytrust.ProxyTrust;
import org.apache.river.api.util.Startable;

/**
 * This class provides a simple transaction manager that tests can use
 * to test a particpant.  It is not designed to be robust in any way,
 * just to allow a test to drive the participant to various parts of
 * the transaction in a controlled way.  This means that some of the
 * methods are not supported -- anything not needed by a participant
 * may have a simpler local version, since this is always used locally
 * by the test.
 */
public class TesterTransactionManager
        implements TransactionManager, TransactionConstants, Serializable, ProxyTrust, Startable {

    private static Logger logger = 
	Logger.getLogger("com.sun.jini.qa.harness");

    private static int serviceID = 100;

    /** Our transaction objects. */
    private final Map txns;

    /** The next ID to allocate. */
    private static long nextID = 1;

    final LoginContext context;
    final RemoteException exception;
    final Configuration c;

    private Object proxy;
    private Object myRef;

    public TesterTransactionManager() throws RemoteException {
        this.txns = Collections.synchronizedMap(new HashMap());
        c = QAConfig.getConfig().getConfiguration();
	LoginContext context = null;
        RemoteException exception = null;
	try {
	    context = (LoginContext) c.getEntry("test", 
						"mahaloLoginContext",
						LoginContext.class, 
						null);
	    if (context != null) {
		logger.log(Level.FINEST, "got a TesterTransactionManager login context");
	    }
	} catch (Throwable e) {
	    exception = new RemoteException("Configuration error", e);
	} finally {
            this.context = context;
            this.exception = exception;
	}	
	}

    public synchronized TrustVerifier getProxyVerifier() {
	return new TesterTransactionManagerProxyVerifier((TransactionManager) myRef);
    }

    private void doExport(Configuration c) throws RemoteException {
	Exporter exporter = QAConfig.getDefaultExporter();
	if (c instanceof com.sun.jini.qa.harness.QAConfiguration) {
	    try {
		exporter = (Exporter) c.getEntry("test",
						 "testerTransactionManagerExporter",
						 Exporter.class);
	    } catch (ConfigurationException e) {
		throw new RemoteException("Configuration error", e);
	    }
	}
	myRef = exporter.export(this);
	proxy = TesterTransactionManagerProxy.getInstance(
				 (TransactionManager) myRef, serviceID++);
    }
    
    private void doExportWithLogin(LoginContext context, final Configuration c) 
	throws RemoteException
    {
	try {
	    context.login();
	} catch (Throwable e) {
	    throw new RemoteException("Login failed", e);
	}
	try {
	    Subject.doAsPrivileged(context.getSubject(),
				   new PrivilegedExceptionAction() {
					   public Object run() throws RemoteException {
					       doExport(c);
					       return null;
					   }
				       },
				   null);
	} catch (PrivilegedActionException e) {
	    Throwable t = e.getException();
	    throw new RemoteException("doAs failed", t);
	} catch (Throwable e) {
	    throw new RemoteException("doAs failed", e);
	}
    }

    public synchronized Object writeReplace() throws ObjectStreamException {
	return proxy;
    }

    /**
     * Return a new <code>ServerTransaction</code> object.
     */
    public synchronized TesterTransaction create() {
        TesterTransaction tt = new TesterTransaction(this, nextID());
        txns.put(tt.idObj, tt);
        return tt;
    }

    /**
     * Return the next transaction id.
     */
    private static synchronized long nextID() {
        return nextID++;
    }

    /**
     * This implementation ignores the time -- it is always synchronous.
     */
    public void commit(long id, long timeout)
            throws UnknownTransactionException, CannotCommitException,
            RemoteException {
        commit(id);
    }

    /**
     * This implementation ignores the time -- it is always synchronous.
     */
    public synchronized void commit(long id)
            throws UnknownTransactionException, CannotCommitException,
            RemoteException {
        tt(id).commit();
    }

    private TesterTransaction tt(long id) throws UnknownTransactionException {
        try {
            return (TesterTransaction) txns.get(new Long(id));
        } catch (NullPointerException e) {
            throw new UnknownTransactionException("" + id);
        }
    }

    /**
     * This implementation ignores the time -- it is always synchronous.
     */
    public void abort(long id, long timeout)
            throws UnknownTransactionException, CannotAbortException,
            RemoteException {
        abort(id);
    }

    /**
     * This implementation ignores the time -- it is always synchronous.
     */
    public synchronized void abort(long id)
            throws UnknownTransactionException, CannotAbortException,
            RemoteException {
        tt(id).sendAbort();
    }

    /**
     * @throws UnsupportedOperationException
     *            Use <code>create()</code>: no leases or other mess
     *            supported or needed in this local case.
     * @see #create()
     */
    public TransactionManager.Created create(long leaseTime) {
        throw new UnsupportedOperationException("don't get fancy: use"
                + " create()");
    }

    /**
     * Return the current state of this transasction.
     */
    public synchronized int getState(long id)
            throws UnknownTransactionException, RemoteException {
        return tt(id).getState();
    }

    /**
     * Join the transaction.  Only one participant in each transaction,
     * currently.
     */
    public synchronized void join(long id, TransactionParticipant part, long crashCnt)
            throws UnknownTransactionException, CannotJoinException,
            CrashCountException {
        tt(id).join(part, crashCnt);
    }

    @Override
    public synchronized void start() throws Exception {
        if (exception != null) throw exception;
        if (context != null) {
	    doExportWithLogin(context, c);
	} else {
	    doExport(c);
}
    }
}
