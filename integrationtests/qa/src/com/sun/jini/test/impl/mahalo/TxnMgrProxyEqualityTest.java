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

import java.util.logging.Level;

import java.rmi.MarshalledObject;
import java.rmi.NoSuchObjectException;
import java.rmi.RemoteException;
import java.rmi.ServerException;

import com.sun.jini.qa.harness.QAConfig;
import com.sun.jini.qa.harness.TestException;

import com.sun.jini.constants.TimeConstants;

import net.jini.core.lease.Lease;
import net.jini.core.transaction.server.TransactionManager;
import net.jini.core.transaction.server.TransactionManager.Created;

public class TxnMgrProxyEqualityTest extends TxnMgrTestBase 
    implements TimeConstants 
{

    //
    // This should be long enough to sensibly run the test.
    // If the service doesn't grant long enough leases, then
    // we might have to resort to using something like the
    // LeaseRenewalManager to keep our leases current.
    //
    private final long DURATION = 3*HOURS;

    public void run() throws Exception {
	logger.log(Level.INFO, "Starting up " + this.getClass().toString()); 

        // Get Mailbox references
	TransactionManager[] mbs = getTransactionManagers(2);
	TransactionManager txnmgr1 = mbs[0];
	TransactionManager txnmgr2 = mbs[1];
	TransactionManager txnmgr1_dup =  null;

        // Get Mailbox admin references
	Object admin1 = getTransactionManagerAdmin(txnmgr1);
	Object admin2 = getTransactionManagerAdmin(txnmgr2);
	Object admin1_dup = null;


	Created txn1 = getCreated(txnmgr1, DURATION);
	Created txn2 = getCreated(txnmgr2, DURATION);

        // Get txn lease references
	Lease txnl1 = getTransactionManagerLease(txn1);
	Lease txnl2 = getTransactionManagerLease(txn2);
	Lease txnl1_dup = null;

        // Get Duplicate references
	MarshalledObject marshObj01 = new MarshalledObject(txnmgr1);
	txnmgr1_dup = (TransactionManager)marshObj01.get();
	marshObj01 = new MarshalledObject(admin1);
	admin1_dup = marshObj01.get();
	marshObj01 = new MarshalledObject(txnl1);
	txnl1_dup = (Lease)marshObj01.get();

        // check top-level proxies
        if (!proxiesEqual(txnmgr1, txnmgr1_dup)) {
            throw new TestException( 
                "Duplicate proxies were not equal");
        }
        logger.log(Level.INFO, "Duplicate service proxies were equal");

        if (proxiesEqual(txnmgr1, txnmgr2)) {
            throw new TestException( 
                "Different proxies were equal");
        }
        logger.log(Level.INFO, "Different service proxies were not equal");

        // check admin proxies
        if (!proxiesEqual(admin1, admin1_dup)) {
            throw new TestException( 
                "Duplicate admin proxies were not equal");
        }
        logger.log(Level.INFO, "Duplicate admin proxies were equal");

        if (proxiesEqual(admin1, admin2)) {
            throw new TestException( 
                "Different admin proxies were equal");
        }
        logger.log(Level.INFO, "Different admin proxies were not equal");

        // check leases
        if (!proxiesEqual(txnl1, txnl1_dup)) {
            throw new TestException( 
                "Duplicate transaction leases were not equal");
        }
        logger.log(Level.INFO, "Duplicate transaction leases were equal");

        if (proxiesEqual(txnl1, txnl2)) {
            throw new TestException( 
                "Different transaction leases were equal");
        }
        logger.log(Level.INFO, "Different transaction leases were not equal");
    }

    /**
     * Invoke parent's setup and parser
     * @exception QATestException will usually indicate an "unresolved"
     *  condition because at this point the test has not yet begun.
     */
    public void setup(QAConfig sysConfig) throws Exception {
	super.setup(sysConfig);
	parse();
    }

    private static boolean proxiesEqual(Object a, Object b) {
        //Check straight equality
        if (!a.equals(b))
            return false;
	//System.out.println("A equals B");
        //Check symmetrical equality
        if (!b.equals(a))
            return false;
	//System.out.println("B equals A");
        //Check reflexive equality
        if (!a.equals(a))
            return false;
	//System.out.println("A equals A");
        if (!b.equals(b))
            return false;
	//System.out.println("B equals B");
        //Check consistency
        if (a.equals(null) || b.equals(null))
            return false;
	//System.out.println("B !equals null && A !equals null");

        return true;
    }


}
