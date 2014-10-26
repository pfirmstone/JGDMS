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

import com.sun.jini.qa.harness.QAConfig;
import com.sun.jini.qa.harness.QATestEnvironment;
import net.jini.config.ConfigurationException;
import com.sun.jini.start.ServiceStarter;
import com.sun.jini.start.SharedGroup;
import com.sun.jini.qa.harness.OverrideProvider;
import com.sun.jini.qa.harness.TestException;
import com.sun.jini.qa.harness.QAConfig;
import com.sun.jini.qa.harness.Test;
import com.sun.jini.qa.harness.VMKiller;

import java.io.*;
import java.rmi.*;
import java.util.*;

import net.jini.core.transaction.server.TransactionManager;

/**
 * Verifies that proxies for the same shared group service 
 * are equal and that proxies for different shared groups 
 * are not equal
 */
 
public class TxnMgrImplNullRecoveredLocators extends QATestEnvironment implements Test {

    private static class OverrideGenerator implements OverrideProvider {

	public String[] getOverrides(QAConfig config, 
				     String servicePrefix, 
				     int index) throws TestException 
	{
	    String[] ret = new String[0];
	    if (servicePrefix == null) { // check for test override
		return ret;
	    }
	    String override = 
		config.getServiceStringProperty(servicePrefix,
						     "override",
						     index);
            logger.log(Level.INFO, 
		       "getOverrides for " + servicePrefix + "." + index);
	    if (override != null) {
		StringTokenizer st = new StringTokenizer(override, "|");
		String token;
		ArrayList pairs = new ArrayList();
		while(st.hasMoreTokens()) {
		    token = st.nextToken(); 
		    int eq = token.indexOf('=');
		    if (eq == -1) {
		        throw new IllegalArgumentException("override missing "
							 + "'=' character: " +
							   token);
		    }
		    pairs.add(token.substring(0, eq));
		    pairs.add(token.substring(eq + 1));
		}
                logger.log(Level.INFO, "getOverrides returning " + pairs);
		ret = (String[])pairs.toArray(new String[pairs.size()]);
	    }
	    return ret;
	}
    }

    public Test construct(QAConfig sysConfig) throws Exception {
	super.construct(sysConfig);
        sysConfig.addOverrideProvider(new OverrideGenerator());
        return this;
    }

    public void run() throws Exception {
	logger.log(Level.INFO, "run()");

        TransactionManager txn_mgr_proxy = null;
	final String serviceName = 
	    "net.jini.core.transaction.server.TransactionManager";
	try {
	    txn_mgr_proxy = 
		(TransactionManager)getManager().startService(serviceName); 
	    if (!getManager().killVM(txn_mgr_proxy)) {
		logger.log(Level.INFO, "Could not kill " + serviceName);
	    }

	    // get delay in seconds
	    int killDelay = getConfig().getIntConfigVal(
		    "com.sun.jini.qa.harness.killvm.delay", 15);

	    if (killDelay < 0) {
	        killDelay = 15;
	    }
	    
	    // Allow service time to auto-restart, which should fail
	    try {
		Thread.sleep(killDelay * 1000);
	    } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
		logger.log(Level.INFO, "Sleep was interrupted");
		//ignore
            }
	} catch (Exception e) { 
	    e.printStackTrace();
	    throw new TestException("Caught unexpected exception: " + e);
	}
	try {
	    /*
	     * Should recover locators upon startup and try to 
	     * use null recovered locator preparer.
		 */
	    txn_mgr_proxy.create(1000);
	    throw new TestException("Restarted service with "
				  + "invalid configuration");
	} catch (Throwable e) {
	    e.printStackTrace();
	    if (!verifyConfigurationException(e)) {
		throw new TestException("Service failed due to "
				      + "non-configuration related exception.");
	    }
	    logger.log(Level.INFO, "Caught expected exception");
	}
	return;
    }

    private static boolean verifyConfigurationException(Throwable e) {
	Throwable cause = e;
	while (cause.getCause() != null) {
	   cause = cause.getCause(); 
	}
	return (cause instanceof ConfigurationException);
    }
}
	
