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
package org.apache.river.test.impl.start;


import org.apache.river.qa.harness.QAConfig;
import org.apache.river.qa.harness.QATestEnvironment;
import org.apache.river.qa.harness.Test;
import org.apache.river.qa.harness.TestException;
import org.apache.river.start.ServiceStarter;
import org.apache.river.start.group.SharedGroup;

import java.io.*;
import java.rmi.*;
import java.util.logging.Level;

import net.jini.admin.Administrable;
import net.jini.event.EventMailbox;
import net.jini.io.MarshalledInstance;

/**
 * Verifies that proxies for the same shared group service 
 * are equal and that proxies for different shared groups 
 * are not equal
 */
 
public class SharedGroupProxyEqualityTest extends QATestEnvironment implements Test {

    public void run() throws Exception {
	logger.log(Level.INFO, "" + ":run()");

        /*
         * To perform all equals tests we require 4 different proxy
         * instances. Two from each service.
         */
        SharedGroup group_proxy = null;
        SharedGroup group_proxy_dup = null;
        SharedGroup bogus_group_proxy = null;
	final String serviceName = "org.apache.river.start.group.SharedGroup";
	MarshalledInstance marshObj01 = 
		new MarshalledInstance(getManager().startService(serviceName));
        MarshalledInstance marshObj02 = 
		new MarshalledInstance(getManager().startService(serviceName));

        group_proxy = (SharedGroup) marshObj01.get(false);
        group_proxy_dup = (SharedGroup) marshObj01.get(false);
        bogus_group_proxy = (SharedGroup) marshObj02.get(false);

	// Check proxies
	if (group_proxy == null ||
	    group_proxy_dup == null ||
	    bogus_group_proxy == null) {
	    throw new TestException( "Could not create group proxy");
    	}

        // Object Equality test
        if (!proxiesEqual(group_proxy, group_proxy_dup)) {
	    throw new TestException( "Proxies were not equal"); 
	}
	logger.log(Level.INFO, "Service proxies are equal");

        // Hashcode equality test
	if (group_proxy.hashCode() != group_proxy_dup.hashCode()) {
	    throw new TestException( "Equivalent service proxies " + 
		"have different hashcodes.");
	}
	logger.log(Level.INFO, "Service proxy hashcodes are equal");

        // Check bogus proxy against old references
        if ( (proxiesEqual(group_proxy, bogus_group_proxy))     ||
             (proxiesEqual(group_proxy_dup, bogus_group_proxy)) ) {
	    throw new TestException( "Service proxies equal bogus "
		+ "group proxy");
	}
	logger.log(Level.INFO, "Bogus proxy does not equal previous references");

	/*
	 * Note: hascodes can be equal even though the objects aren't, so 
	 * it's not checked. 
	 */
	return;
    }

    private static boolean proxiesEqual(Object a, Object b) {
	//Check straight equality
	if (!a.equals(b))
	    return false;
	//Check symmetrical equality
	if (!b.equals(a))
	    return false;
	//Check reflexive equality
	if (!a.equals(a))
	    return false;
	if (!b.equals(b))
	    return false;
	//Check consistency
	if (a.equals(null) || b.equals(null)) 
	    return false;

        return true;	
    }
}
	
