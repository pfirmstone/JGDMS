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
package com.sun.jini.test.impl.mahalo;
import net.jini.core.lease.*;
import net.jini.core.transaction.*;
import net.jini.core.transaction.server.*;
import com.sun.jini.mahalo.*;
import com.sun.jini.mahalo.log.*;
import com.sun.jini.thread.*;
import java.util.*;
import java.rmi.*;
import com.sun.jini.test.share.TestParticipant;
import com.sun.jini.test.share.TxnManagerTestOpcodes;
import com.sun.jini.test.share.TxnTestUtils;
import com.sun.jini.thread.TaskManager.Task;
import java.util.concurrent.ExecutorService;


/**
 */
public abstract class RandomStressTask extends RetryTask
        implements TxnManagerTestOpcodes, Task {
    private static final boolean DEBUG = false;
    private TransactionManager mgr;
    protected Transaction.Created cr;
    protected ServerTransaction str;
    TestParticipant[] testparts;

    public RandomStressTask(ExecutorService executor, WakeupManager wakeupManager, TransactionManager mgr, int numParts) 
    {
        super(executor, wakeupManager);

        if (numParts <= 0) {
            throw new IllegalArgumentException("RandomStressTask: numParts "
                    + "must be >= 1");
        }

        if (mgr == null) {
            throw new IllegalArgumentException("RandomStressTask: mgr must "
                    + "be non-null");
        }
        this.mgr = mgr;

        try {
            cr = TransactionFactory.create(mgr, Lease.FOREVER);
            str = (ServerTransaction) cr.transaction;
            testparts = TxnTestUtils.createParticipants(numParts);
        } catch (RemoteException e) {
            throw new RuntimeException(e);
        } catch (LeaseDeniedException e) {
            throw new RuntimeException(e);
        }
    }

    /*
     * RetryTask abstract method definition
     * note: subclass needs to define tryOnce
     */
    public boolean runAfter(List list, int max) {
        return false;
    }

    public void setBehavior(int op) {
        try {
            TxnTestUtils.setBulkBehavior(op, testparts);
        } catch (RemoteException e) {
            throw new RuntimeException(e.getMessage());
        }
    }

    public void doBehavior() {
        try {
            TxnTestUtils.doBulkBehavior(str, testparts);
        } catch (RemoteException e) {
            throw new RuntimeException(e);
        } catch (TransactionException e) {
            throw new RuntimeException(e);
        }
    }

    public void commit() throws TransactionException, RemoteException {
        str.commit();
    }

    public void abort() throws TransactionException, RemoteException {
        str.abort();
    }
}
