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
package com.sun.jini.test.impl.mercury;

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
import net.jini.event.EventMailbox;

/**
 * Verifies that proxies for the same shared group service 
 * are equal and that proxies for different shared groups 
 * are not equal
 */
 
public class MailboxImplReadyStateTest extends MailboxTestBase {

    public Test construct(QAConfig sysConfig) throws Exception {
	super.construct(sysConfig);
        return this;
    }

    public void run() throws Exception {
        
        /*
         * Assumptions. Test description has:
         * - exported mailbox on fixed port so that the mailbox reference
         *   is still valid before and after service restart
         * - has created an "exporter" object that exports the service
         *   so it's reachable, but delays the export() call enough to allow
         *   the getAdmin() call to arrive before export() returns.
         * The admin proxy is set after the service is exported, so if we
         * get a null value, then we called getAdmin() before the service was
         * fully initialized.
         *   
         */
	logger.log(Level.INFO, "run()");
	final String serviceName = "net.jini.event.EventMailbox";

        EventMailbox mb = getConfiguredMailbox();
        
        // Kill mailbox service.
        shutdown(0);
        
	try { Thread.sleep(10000); } catch (Exception e) {;}

        // Invoke remote method
        Object admin = ((Administrable)mb).getAdmin();
        
        if (admin == null) {
            throw new TestException("Successfully called getAdmin before init.");
        }
	logger.log(Level.INFO, "Got admin proxy: {0}", admin);
    }

}
	
