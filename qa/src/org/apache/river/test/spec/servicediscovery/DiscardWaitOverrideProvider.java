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

package org.apache.river.test.spec.servicediscovery;

import org.apache.river.qa.harness.OverrideProvider;
import org.apache.river.qa.harness.QAConfig;
import org.apache.river.qa.harness.TestException;

/** 
 * A test override provider which searches the test properties
 * for a non-zero value of <code>org.apache.river.sdm.discardWait</code>.
 * If found, an override is generated for 
 * <code>net.jini.lookup.ServiceDiscoveryManager.discardWait</code>
 * having that value. An override is only generated for tests
 * (serviceName == null)
 */
public class DiscardWaitOverrideProvider implements OverrideProvider {

    public String[] getOverrides(QAConfig config,
				 String serviceName,
				 int count) throws TestException 
    {
	String[] ret = new String[0];
	if (serviceName == null) {
	    long discardWait = 
		config.getLongConfigVal("org.apache.river.sdm.discardWait", 0);
	    if (discardWait != 0) {
		ret = new String[]{
		        "net.jini.lookup.ServiceDiscoveryManager.discardWait",
		        Long.toString(discardWait)
		      };
	    }
	}
	return ret;
    }
}


