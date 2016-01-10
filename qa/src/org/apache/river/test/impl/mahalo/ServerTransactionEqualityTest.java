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
package org.apache.river.test.impl.mahalo;

import org.apache.river.qa.harness.QAConfig;
import org.apache.river.qa.harness.Test;
import org.apache.river.qa.harness.TestException;

import java.rmi.MarshalledObject;

import java.util.logging.Level;
import net.jini.core.transaction.server.NestableServerTransaction;
import net.jini.core.transaction.server.NestableTransactionManager;
import net.jini.core.transaction.server.ServerTransaction;
import net.jini.core.transaction.server.TransactionManager;
import net.jini.io.MarshalledInstance;

public class ServerTransactionEqualityTest extends TxnMgrTestBase 
{

    public void run() throws Exception {
	logger.log(Level.INFO, "Starting up " + this.getClass().toString()); 

        // Get transaction manager references
	TransactionManager[] mbs = getTransactionManagers(2);
	TransactionManager txnmgr1 = mbs[0];
	TransactionManager txnmgr2 = mbs[1];
	NestableTransactionManager ntxnmgr = 
	    new NoOpNestableTransactionManager();

        ServerTransaction st1_1 = new ServerTransaction(txnmgr1, 1L);
        ServerTransaction st1_2 = new ServerTransaction(txnmgr1, 2L);
        ServerTransaction st2_1 = new ServerTransaction(txnmgr2, 1L);
        ServerTransaction st2_2 = new ServerTransaction(txnmgr2, 2L);
        ServerTransaction st1_1_dup;
        NestableServerTransaction nst1_1 = 
	    new NestableServerTransaction(ntxnmgr, 1L, null);

	
        ServerTransaction st1_5 = 
	    new ServerTransaction(ntxnmgr, 5L);
        NestableServerTransaction nst1_5 = 
	    new NestableServerTransaction(ntxnmgr, 5L, nst1_1);

        // Get duplicate references
	MarshalledInstance marshObj11 = new MarshalledInstance(st1_1);
        st1_1_dup = (ServerTransaction)marshObj11.get(false);

        // check top-level proxies
        if (!checkEquality(st1_1, st1_1)) {
            throw new TestException( 
                "Duplicate proxies were not equal");
        }
        logger.log(Level.INFO, "Identical proxies were equal");

        if (!checkEquality(st1_1, st1_1_dup)) {
            throw new TestException( 
                "Duplicate proxies were not equal");
        }
        logger.log(Level.INFO, "Duplicate proxies were equal");

        if (checkEquality(st1_1, st1_2)) {
            throw new TestException( 
                "Different proxies were equal 1");
        }
        logger.log(Level.INFO, "Different proxies were not equal 1");

        if (checkEquality(st1_1, st2_1)) {
            throw new TestException( 
                "Different proxies were equal 2");
        }
        logger.log(Level.INFO, "Different proxies were not equal 2");

        if (checkEquality(st1_1, st2_2)) {
            throw new TestException( 
                "Different proxies were equal 3");
        }
        logger.log(Level.INFO, "Different proxies were not equal 3");

        if (checkEquality(st1_1, nst1_1)) {
            throw new TestException( 
                "Different proxies were equal 4");
        }
        logger.log(Level.INFO, "Different proxies were not equal 4");

        if (!checkEquality(st1_5, nst1_5)) {
            throw new TestException( 
                "Equivalent proxies were not equal");
        }
        logger.log(Level.INFO, "Equivalent proxies were equal");

    }

    /**
     * Invoke parent's construct and parser
     * @exception QATestException will usually indicate an "unresolved"
     *  condition because at this point the test has not yet begun.
     */
    public Test construct(QAConfig sysConfig) throws Exception {
	super.construct(sysConfig);
	parse();
        return this;
    }

    private static boolean checkEquality(Object a, Object b) {
        //Check straight equality
        if (!a.equals(b))
            return false;
	System.out.println("A equals B");
        //Check symmetrical equality
        if (!b.equals(a))
            return false;
	System.out.println("B equals A");
        //Check reflexive equality
        if (!a.equals(a))
            return false;
	System.out.println("A equals A");
        if (!b.equals(b))
            return false;
	System.out.println("B equals B");
        //Check consistency
        if (a.equals(null) || b.equals(null))
            return false;
	System.out.println("B !equals null && A !equals null");

        return true;
    }


}
