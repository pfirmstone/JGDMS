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
package org.apache.river.test.spec.servicediscovery.discovery;

import java.util.logging.Level;

// java packages
import java.io.IOException;
import java.net.InetAddress;
import java.rmi.RemoteException;

// Test harness packages
import org.apache.river.qa.harness.TestException;
import org.apache.river.qa.harness.QAConfig;
import org.apache.river.qa.harness.QATestEnvironment;
import org.apache.river.qa.harness.QAConfig;

// net.jini
import net.jini.discovery.Constants;
import net.jini.core.discovery.LookupLocator;
import net.jini.lookup.DiscoveryAdmin;
import net.jini.core.lookup.ServiceRegistrar;


public class Locator extends Discovery {

    public void run() throws Exception {
        // Ensure that the parsing code is sane.
        testLocator("localhost", -Constants.discoveryPort, null, false);
        testLocator("localhost", Constants.discoveryPort, null, true);
        testLocator(null, Constants.discoveryPort, "http://hostname/", false);
        testLocator(null, Constants.discoveryPort, "hostname", false);
        testLocator(null, Constants.discoveryPort, "jini:hostname", false);
        testLocator("hostname", Constants.discoveryPort,
                "jini://hostname:", true);
        testLocator("hostname", Constants.discoveryPort,
                "jini://hostname/", true);
        testLocator("hostname", Constants.discoveryPort,
                "jini://hostname", true);
        testLocator("hostname", 40, "jini://hostname:40/", true);
        testLocator("hostname", 70000, "jini://hostname:70000/", false);
        testLocator("hostname", -300, "jini://hostname:-300/", false);

        // Ensure that the locator actually works.
        DiscoveryAdmin admin = null;

	ServiceRegistrar reg = createLookup();
	admin = getAdmin(reg);
	String[] actualGroups = new String[] {
	    "disc_test" };
	admin.setMemberGroups(actualGroups);
	LookupLocator locator =
	    QAConfig.getConstrainedLocator(reg.getLocator());
	ServiceRegistrar reg1 = locator.getRegistrar();
	
	if (reg1.equals(reg) == false) {
	    throw new TestException("registrars not the same");
	}
    }

    protected void testLocator(String host, int port, String url,
            boolean shouldSucceed) throws Exception {
        LookupLocator loc = null;
        boolean excepted = false;

        try {
            if (url != null) {
                loc = QAConfig.getConstrainedLocator(url);
            } else {
                loc = QAConfig.getConstrainedLocator(host, port);
            }
        } catch (Exception e) {
            excepted = true;

            if (shouldSucceed) {
                e.printStackTrace();

                if (url == null) {
                    throw new TestException(
                            "constructor for {" + host + "," + port
                            + "} threw an exception");
                } else {
                    throw new TestException(
                            "constructor for {" + url + "} threw an exception");
                }
            }
        }

        if (excepted == false) {
            if (shouldSucceed == false) {
                if (url == null) {
                    throw new TestException(
                            "constructor for {" + host + "," + port
                            + "} should have thrown an exception");
                } else {
                    throw new TestException(
                            "constructor for {" + url
                            + "} should have thrown an exception");
                }
            }
	    String canonicalHost = host;
	    try {
		canonicalHost = InetAddress.getByName(host).getCanonicalHostName();
	    } catch (Exception ignore){
	    }
            if (!loc.getHost().equals(canonicalHost)) {
                throw new TestException(
                        "getHost doesn't match host for " + canonicalHost);
            }

            if (loc.getPort() != port) {
                throw new TestException(
                        "getPort doesn't match port for " + port);
            }
        }
    }
}
