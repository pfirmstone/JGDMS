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

import java.util.logging.Level;

import com.sun.jini.mahalo.*;
import net.jini.core.transaction.*;
import net.jini.core.transaction.server.*;

import net.jini.config.Configuration;
import net.jini.config.ConfigurationException;
import net.jini.security.ProxyPreparer;

import java.io.*;
import java.rmi.*;

import com.sun.jini.qa.harness.QAConfig;
import com.sun.jini.qa.harness.Test;
import com.sun.jini.qa.harness.TestException;

/**
 */
public abstract class TxnManagerTest extends TestBase
    implements TransactionConstants, TxnManagerTestOpcodes, Test
{
    protected static final boolean DEBUG = true;

    TransactionManager[] mgrs = new TransactionManager[1];

    public TransactionManager manager() throws RemoteException {
	return (TransactionManager) mgrs[0];
    }

    protected void startTxnMgr() throws TestException {
	specifyServices(new Class[] {TransactionManager.class}); 
	mgrs[0]= (TransactionManager)services[0]; // prepared by specifyServices
    }

    public Test construct(QAConfig config) throws Exception {
        super.construct(config);
        super.parse();
        return this;
    }

    /**
     * This method aggressively frees evrything by used Runtime.gc().
     */
    final static public synchronized void fullGC() {
	Runtime rt  = Runtime.getRuntime();
	long isFree = rt.freeMemory();
	long wasFree;
	do {
	    wasFree = isFree;
	    rt.runFinalization();
	    rt.gc();
	    isFree = rt.freeMemory();
	} while (isFree > wasFree);
    }
}
