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
package com.sun.jini.qa.harness;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.StringTokenizer;

/**
 * A wrapper which provides the <code>main</code> method for running
 * the harness. If the test is to be run distributed, the system property 
 * <code>com.sun.jini.qa.harness.testhosts</code> defines the set of participants.
 */

public abstract class QARunner {

/**
 * The <code>main</code> method for the harness. If the system property 
 * <code>com.sun.jini.qa.harness.testhosts</code> isundefined, or is defined
 * and has a value which contains one or more comma or white-space separated
 * tokens, and the first token is the name of the local host, then
 * instantiate a <code>MasterHarness</code> object and call its 
 * <code>runTests</code> method. Otherwise, instantiate a
 * <code>SlaveHarness</code> object and call its <code>handleRequests</code>
 * method.
 *
 * @param args the command-line arguments
 * @throws UnknownHostException if a host list was provided by the name of
 *                              the local host cannot be determined
 * @throws TestException        if a host list was provided, but the local
 *                              host is not included in the list
 */
    public static void main(String[] args) 
	throws UnknownHostException, TestException
    {
        String hostList = AccessController.doPrivileged(
	    new PrivilegedAction<String>() {
		public String run() {
		    return 
			System.getProperty(
			    "com.sun.jini.qa.harness.testhosts");
		}
            }
        );
//	String hostList = System.getProperty("com.sun.jini.qa.harness.testhosts");
	if (isMasterHost(hostList)) {
	    boolean allPass = (new MasterHarness(args)).runTests();
	    System.exit(allPass ? 0 : 1);
	} else {
	    new SlaveHarness(args).handleRequests();
	}
    }

/**
 * Test for this host being the master host. If the system property 
 * <code>com.sun.jini.qa.harness.testhosts</code> isundefined, or is defined
 * and has a value which contains one or more comma or white-space separated
 * tokens, and the first token is the name of the local host, then
 * return true. 
 *
 * @param hostList the list of participants, or <code>null</code>
 * @throws UnknownHostException if a <code>hostList</code> is non-null and
 *                              the local host cannot be determined
 * @throws TestException        if a host list was non-null, but the local
 *                              host is not included in the list
 */
    private static boolean isMasterHost(String hostList) 
	throws UnknownHostException, TestException
    {
	if (hostList == null) {
	    return true;
	}
	InetAddress thisAddr = InetAddress.getLocalHost(); //XXX multinic??
	boolean isFirst = true;
	StringTokenizer tok = new StringTokenizer(hostList, "|");
	while (tok.hasMoreTokens()) {
	    String hostName = tok.nextToken();
	    InetAddress hostAddr = InetAddress.getByName(hostName);
	    if (hostAddr.equals(thisAddr)) {
		return isFirst;
	    }
	    isFirst = false;
	}
	throw new TestException("Local host " + thisAddr.getHostName() 
			      + " is missing from the host list: " + hostList);
    }
}
