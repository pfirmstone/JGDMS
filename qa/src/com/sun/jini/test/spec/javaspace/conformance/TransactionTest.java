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
package com.sun.jini.test.spec.javaspace.conformance;

import java.util.logging.Level;

// java.util
import java.util.ArrayList;

// java.rmi
import java.rmi.RemoteException;
import java.rmi.RMISecurityManager;

// net.jini
import net.jini.core.lease.Lease;
import net.jini.core.entry.Entry;
import net.jini.core.event.EventRegistration;
import net.jini.core.transaction.Transaction;
import net.jini.core.transaction.TransactionFactory;
import net.jini.core.transaction.server.TransactionManager;
import net.jini.space.JavaSpace;

// com.sun.jini
import com.sun.jini.qa.harness.QATestEnvironment;
import com.sun.jini.qa.harness.QAConfig;
import com.sun.jini.qa.harness.Test;
import com.sun.jini.qa.harness.TestException;

import net.jini.security.ProxyPreparer;
import net.jini.config.Configuration;
import net.jini.config.ConfigurationException;

/**
 * Abstract Test base for all javaspace conformance tests.
 *
 * @author Mikhail A. Markov
 */
public abstract class TransactionTest extends JavaSpaceTest {

    /**
     * Sets up the testing environment.
     *
     * @param config QAConfig from the runner for construct.
     */
    public Test construct(QAConfig config) throws Exception {
        // mandatory call to parent
        super.construct(config);
        // get an instance of Transaction Manager
        mgr = getTxnManager();
        return this;
    }

    
}
