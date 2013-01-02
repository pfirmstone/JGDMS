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
import java.util.*;

import com.sun.jini.qa.harness.QAConfig;
import com.sun.jini.qa.harness.QATestEnvironment;
import net.jini.config.ConfigurationException;
import com.sun.jini.start.ServiceStarter;
import com.sun.jini.start.SharedGroup;
import com.sun.jini.qa.harness.OverrideProvider;
import com.sun.jini.qa.harness.TestException;
import com.sun.jini.qa.harness.QAConfig;
import com.sun.jini.qa.harness.Test;

import java.io.*;
import java.rmi.*;

import net.jini.admin.Administrable;
import net.jini.core.transaction.server.TransactionManager;

/**
 * Verifies that proxies for the same shared group service 
 * are equal and that proxies for different shared groups 
 * are not equal
 */
 
public class MahaloImplReadyStateTest extends TxnMgrTestBase {

    // Appears to be an unnecessary override.
//    public Test construct(QAConfig sysConfig) throws Exception {
//	return super.construct(sysConfig);
//    }

    public void run() throws Exception {
        
        /*
         * Assumptions. Test description has:
         * - exported service on fixed port so that the service reference
         *   is still valid before and after restarting
         * - has created an "exporter" object that delays
         *   the service's initialization enough to cause the getAdmin call
         *   to occur before initialization has completed.
         */
	logger.log(Level.INFO, "run()");
	final String serviceName = "net.jini.core.transaction.server.TransactionManageR";

        TransactionManager tm = getTransactionManager();
        
        // Kill transaction manager service.
        shutdown(0);
        
	try { Thread.sleep(10000); } catch (Exception e) {;}

        // Invoke remote method
        Object admin = ((Administrable)tm).getAdmin();
        
        if (admin == null) {
            throw new TestException("Successfully called getAdmin before init.");
        }
    }

}
	
