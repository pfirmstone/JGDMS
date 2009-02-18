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
import com.sun.jini.qa.harness.QATest;
import net.jini.config.ConfigurationException;
import com.sun.jini.start.ServiceStarter;
import com.sun.jini.start.SharedGroup;
import com.sun.jini.qa.harness.OverrideProvider;
import com.sun.jini.qa.harness.TestException;
import com.sun.jini.qa.harness.QAConfig;

import java.io.*;
import java.rmi.*;

import net.jini.core.transaction.server.TransactionManager;

/**
 * Verifies that proxies for the same shared group service 
 * are equal and that proxies for different shared groups 
 * are not equal
 */
 
public class TxnMgrImplNullConfigEntries extends QATest {

    private static class OverrideGenerator implements OverrideProvider {

	public String[] getOverrides(QAConfig config, 
				     String servicePrefix, 
				     int index) throws TestException 
	{
	    String[] ret = new String[0];
	    if (servicePrefix == null) {  // check for test override
		return ret;
	    }
	    String override = 
		config.getServiceStringProperty(servicePrefix,
						     "override",
						     index);
	    if (override != null) {
		int eq = override.indexOf('=');
		if (eq == -1) {
		    throw new IllegalArgumentException("override missing "
						     + "'=' character");
		}
		String name = override.substring(0, eq);
		String value = override.substring(eq + 1);
		ret = new String[]{name, value};
	    }
	    return ret;
	}
    }

    public void setup(QAConfig sysConfig) throws Exception {
	super.setup(sysConfig);
        sysConfig.addOverrideProvider(new OverrideGenerator());
    }

    public void run() throws Exception {
	logger.log(Level.INFO, "" + ":run()");

        TransactionManager txn_mgr_proxy = null;
	final String serviceName = 
	    "net.jini.core.transaction.server.TransactionManager";
	final int instances = 
	    getConfig().getIntConfigVal(serviceName + ".instances", -1);
	if (instances <= 0) {
	    throw new TestException( "No services to test."); 
	}
	for (int i=0; i < instances; i++) {
	    try {
                txn_mgr_proxy = 
		    (TransactionManager)manager.startService(serviceName); 
	        throw new TestException( 
		    "Started service with invalid configuration");
	    } catch (Exception e) {
		//TODO - check for Configuration exception
		logger.log(Level.INFO, "Caught expected exception");
	        e.printStackTrace();
		if (!verifyConfigurationException(e)) {
	            throw new TestException( 
		        "Service failed due to non-configuration related"
			+ "exception.");
		}
	    }
        }
	return;
    }

    private static boolean verifyConfigurationException(Exception e) {
	Throwable cause = e;
	while (cause.getCause() != null) {
	   cause = cause.getCause(); 
	}
	return (cause instanceof ConfigurationException);
    }
}
	
