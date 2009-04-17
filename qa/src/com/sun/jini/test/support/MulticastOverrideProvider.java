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

package com.sun.jini.test.support;

import com.sun.jini.qa.harness.OverrideProvider;
import com.sun.jini.qa.harness.QAConfig;
import com.sun.jini.qa.harness.TestException;
import java.util.ArrayList;

/**
 * An <code>OverrideProvider</code> which generates an override string
 * for the entries:
 * <ul>
 * <li><code>net.jini.discovery.LookupDiscovery.multicastAnnouncementInterval</code>
 * <li><code>com.sun.jini.reggie.multicastAnnouncementInterval</code> 
 * if the service name is <code>net.jini.core.lookup.ServiceRegistrar</code>
 * <li><code>multicast.ttl</code> is the test run is non-distributed
 * </ul>
 */
public class MulticastOverrideProvider implements OverrideProvider {

    /**
     * If the test property <code>"net.jini.discovery.announce"</code> is
     * defined, use its value to generate the override strings. For all values
     * of <code>serviceName</code> including <code>null</code>, an override for
     * <code>net.jini.discovery.LookupDiscovery.multicastAnnouncementInterval</code>
     * is generated. If <code>serviceName</code> is
     * <code>net.jini.core.lookup.ServiceRegistrar</code> then an additional
     * override is generated for
     * <code>com.sun.jini.reggie.multicastAnnouncementInterval.</code>
     * If the test run is NOT distributed then an override is generated
     * for <code>multicast.ttl</code> to set it to zero.
     * 
     * @param config the test config
     * @param serviceName the name of the service to generate overrides for
     * @param index the service instance count
     * @return an array of override strings, or a zero-length array if
     *         there are no overrides.
     * @throws TestException if the value of the announce test property
     *                       is non-numeric
     */
    public String[] getOverrides(QAConfig config, String serviceName, int index)
	throws TestException
    {
	ArrayList list = new ArrayList();
	String announce = 
	    config.getStringConfigVal("net.jini.discovery.announce", null);

	if (announce != null) {
	    try {
		Long.parseLong(announce); 
		String annString = "multicastAnnouncementInterval";
		String lusName = "net.jini.core.lookup.ServiceRegistrar";
		list.add("net.jini.discovery.LookupDiscovery." + annString);
		list.add(announce);
		if (lusName.equals(serviceName)) {
		    list.add("com.sun.jini.reggie." + annString);
		    list.add(announce);
		}
	    } catch (NumberFormatException e) {
		throw new TestException("net.jini.discovery.announce"
					+ " must be a number", 
					e);
	    }
	}

	if (! QAConfig.isDistributed()) {
	    list.add("multicast.ttl");
	    list.add("0");
	}

	return (String[]) list.toArray(new String[list.size()]);
    }
}
