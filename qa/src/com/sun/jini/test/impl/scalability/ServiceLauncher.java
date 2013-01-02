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
package com.sun.jini.test.impl.scalability;

import java.util.logging.Level;

// Test harness specific classes
import com.sun.jini.qa.harness.QAConfig;
import com.sun.jini.qa.harness.QATestEnvironment;
import com.sun.jini.qa.harness.Test;
import com.sun.jini.qa.harness.TestException;

import java.rmi.NoSuchObjectException;
import java.util.StringTokenizer;
import java.util.HashMap;


/** 
 * Tests that an Lease Renweal Service properly grants, renews,
 * cancels, and/or expires leases.  Tests for availably by adding
 * leases to the set.
 */
public class ServiceLauncher extends QATestEnvironment implements Test {

    private static HashMap nameMap = new HashMap();
    static {
	nameMap.put("fiddler", "net.jini.discovery.LookupDiscoveryService");
	nameMap.put("mahalo", 
		    "net.jini.core.transaction.server.TransactionManager");
	nameMap.put("mercury", "net.jini.event.EventMailbox");
	nameMap.put("norm", "net.jini.lease.LeaseRenewalService");
	nameMap.put("outrigger", "net.jini.space.JavaSpace");
	nameMap.put("reggie", "net.jini.core.lookup.ServiceRegistrar");
    }

    public Test construct(QAConfig config) throws Exception {
	super.construct(config);
	String serviceList = 
	    config.getStringConfigVal("com.sun.jini.qa.harness.scalability.serviceList", "");
        StringTokenizer tok = new StringTokenizer(serviceList);
	while (tok.hasMoreTokens()) {
	    String service = tok.nextToken();
	    if (nameMap.containsKey(service)) {
		service = (String) nameMap.get(service);
	    }
	    try {
		getManager().startService(service);
	    } catch (Exception e) {
		throw new TestException("Failed to start " + service, e);
	    }
	}
        return this;
    }

    public void run() throws Exception {
	getConfig().suspendRun("service launcher suspended" 
			  + "<popup>resume to teardown and terminate</popup>");
    }
}
