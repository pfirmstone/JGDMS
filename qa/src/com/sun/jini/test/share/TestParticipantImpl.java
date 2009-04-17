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

import com.sun.jini.mahalo.*;
import net.jini.core.transaction.*;
import net.jini.core.transaction.server.*;

import com.sun.jini.qa.harness.QATest;
import com.sun.jini.qa.harness.QAConfig;

import net.jini.config.Configuration;
import net.jini.config.ConfigurationException;
import net.jini.export.Exporter;
import net.jini.export.ProxyAccessor;
import net.jini.security.TrustVerifier;
import net.jini.security.proxytrust.ServerProxyTrust;

import com.sun.jini.proxy.BasicProxyTrustVerifier;

import java.io.*;
import java.rmi.*;
import java.rmi.registry.*;
import java.rmi.server.*;
import java.util.BitSet;

/**
 *
 */
public class TestParticipantImpl
    implements TxnManagerTestOpcodes, TransactionConstants,
	       TransactionParticipant, TestParticipant, ProxyAccessor,
	       ServerProxyTrust
{
    private String name;
    private BitSet behavior; 
    private Integer lock;
    private Integer lock2;
    private long crashcount;
    private ServerTransaction str;
    private static final long TENSECONDS = 1000 * 10;
    private static final long THIRTYSECONDS = TENSECONDS *3;
    private static final boolean DEBUG = true;
    private TransactionParticipant proxy;

    public TestParticipantImpl() throws RemoteException {
	this(DEFAULT_NAME);
    }

    public TestParticipantImpl(String name) throws RemoteException {
	this.name  = name;
	lock       = new Integer(0);
	lock2      = new Integer(1);
	crashcount = System.currentTimeMillis();
	behavior   = new BitSet(OPERATION_COUNT);
	Configuration c = QAConfig.getConfig().getConfiguration();
	Exporter exporter = QAConfig.getDefaultExporter();
	if (c instanceof com.sun.jini.qa.harness.QAConfiguration) {
	    try {
		exporter = (Exporter) c.getEntry("test",
						 "transactionParticipantExporter",
						 Exporter.class);
	    } catch (ConfigurationException e) {
		throw new RemoteException("Configuration Error", e);
	    }
	}
	proxy = (TransactionParticipant)exporter.export(this);
    }

    public Object getProxy() {
	return proxy;
    }

    public TrustVerifier getProxyVerifier() {
	return new BasicProxyTrustVerifier(proxy);
    }

    private boolean checkBit(int bit) {
	boolean result = false;

	if (bit < 0) {
	    throw new IllegalArgumentException(name + ": " +
			"checkBit: bit position must be non-negative");
	}

	try {
	    result = behavior.get(bit);
	} catch (IndexOutOfBoundsException ioobe) {
	    System.out.println(name + ": checkBit: " + ioobe.getMessage());
	    ioobe.printStackTrace();
	}

	return result;
    }

    private void changeBit(int bit, boolean val) {

	if (bit < 0) {
	    throw new IllegalArgumentException(name + ": " +
			"changeBit: bit position must be non-negative");
	}

	try {
	    if (val) {
		behavior.set(bit);
	    } else {
		behavior.clear(bit);
	    }
	} catch (IndexOutOfBoundsException ioobe) {
	    System.out.println(name + ": changeBit: " + ioobe.getMessage());
	    ioobe.printStackTrace();
	}
    }

    public void behave(Transaction tr)
	throws RemoteException, TransactionException {

	str = (ServerTransaction) tr;
 
	if (checkBit(OP_JOIN)) {
	    if (DEBUG) {
		System.out.println(name + ": joining");
	    }

	    str.join(proxy, crashcount);
	    if(checkBit(OP_TIMEOUT_JOIN)) {
		if(checkBit(OP_TIMEOUT_VERYLONG)) {
		    doTimeout(THIRTYSECONDS);
		} else {
		    doTimeout(TENSECONDS);
		}
	    }
	}
  
	if (checkBit(OP_INCR_CRASHCOUNT)) {
	    synchronized(lock2) {
		crashcount++;
	    }
	}

	if (checkBit(OP_JOIN_IDEMPOTENT)) {
	    if (DEBUG) {
		System.out.println(name + ": joining again");
	    }

	    str.join(proxy, crashcount);

	    if(checkBit(OP_TIMEOUT_JOIN)) {
		if(checkBit(OP_TIMEOUT_VERYLONG)) {
		    doTimeout(THIRTYSECONDS);
		} else {
		    doTimeout(TENSECONDS);
		}
	    }
	}
    }

    public void setBehavior(int how) throws RemoteException {
	changeBit(how,true);
    }

    public void clearBehavior(int how) throws RemoteException {
	changeBit(how,false);
    }

    public boolean getBehavior(int how) throws RemoteException {
	return checkBit(how);
    }

    private void doException()
	throws UnknownTransactionException, RemoteException
    {
	if (checkBit(EXCEPTION_REMOTE))
	    throw new RemoteException(name + ": doException");

	if (checkBit(EXCEPTION_TRANSACTION))
	    throw new UnknownTransactionException(name + ":doException");
    }

    private void doTimeout(long millis) {
	try {
	    Thread.sleep(millis);
	} catch (InterruptedException ie) {
	    ;
	}
    }

    public int prepare(TransactionManager mgr, long id)
	throws UnknownTransactionException, RemoteException
    {
	if (DEBUG) {
	    System.out.println(name + ": prepare");
	}

	if (checkBit(OP_TIMEOUT_PREPARE)) {
	    if (checkBit(OP_TIMEOUT_VERYLONG)) {
		doTimeout(THIRTYSECONDS);
	    } else {
		doTimeout(TENSECONDS);
	    }
	}

	if (checkBit(OP_EXCEPTION_ON_PREPARE)) {
	    doException();
	}

	if (checkBit(OP_VOTE_NOTCHANGED)) {
	    return NOTCHANGED;
	}

	if (checkBit(OP_VOTE_PREPARED)) {
	    return PREPARED;
	}

	if (checkBit(OP_VOTE_ABORTED)) {
	    return ABORTED;
	}

	return NOTCHANGED;
    }


    public void commit(TransactionManager mgr, long id)
	throws UnknownTransactionException, RemoteException
    {
	if (DEBUG) {
	    System.out.println(name + ": commit");
	}

	if (checkBit(OP_TIMEOUT_COMMIT)) {
	    if(checkBit(OP_TIMEOUT_VERYLONG)) {
		doTimeout(THIRTYSECONDS);
	    } else {
		doTimeout(TENSECONDS);
	    }
	}

	if (checkBit(OP_EXCEPTION_ON_COMMIT)) {
	    doException();
	}
    }

    public void abort(TransactionManager mgr, long id)
	throws UnknownTransactionException, RemoteException
    {
	if (DEBUG) {
	    System.out.println(name + ": abort");
	}

	if (checkBit(OP_TIMEOUT_ABORT)) {
	    if (checkBit(OP_TIMEOUT_VERYLONG)) {
		System.out.println("OP_TIMOUT_ABORT | OP_TIMEOUT_VERYLONG");
		doTimeout(THIRTYSECONDS);
	    } else {
		doTimeout(TENSECONDS);
	    }
	}

	if (checkBit(OP_EXCEPTION_ON_ABORT)) {
	    doException();
	}
    }


    public int prepareAndCommit(TransactionManager mgr, long id)
	throws UnknownTransactionException, RemoteException
    {
	if (DEBUG) {
	    System.out.println(name + ": prepareAndCommit");
	}

	if (checkBit(OP_TIMEOUT_PREPARECOMMIT)) {
	    if (checkBit(OP_TIMEOUT_VERYLONG)) {
		doTimeout(THIRTYSECONDS);
	    } else {
		doTimeout(TENSECONDS);
	    }
	}

	if (checkBit(OP_EXCEPTION_ON_PREPARECOMMIT)) {
	    doException();
	}
 
	if (checkBit(OP_VOTE_NOTCHANGED)) {
	    return NOTCHANGED;
	}
 
	if (checkBit(OP_VOTE_PREPARED)) {
	    return PREPARED;
	}
 
	if (checkBit(OP_VOTE_ABORTED)) {
	    return ABORTED;
	}

	return NOTCHANGED;
    }

    public static void main(String[] args) throws RemoteException {
	TestParticipant part = new TestParticipantImpl();
    }
}
