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
package org.apache.river.test.impl.mercury;

import org.apache.river.qa.harness.ConfigurationVerifier;
import org.apache.river.qa.harness.QAConfig;
import org.apache.river.qa.harness.TestDescription;

import java.util.logging.Logger;
import java.util.logging.Level;

/**
 * A configuration verifier used by tests which should not be run
 * if mercury is not if type activatable
 */
public class ActivatableMercuryVerifier implements ConfigurationVerifier {

    private static Logger logger = 
	Logger.getLogger("org.apache.river.qa.harness.test");

    /**
     * Return false if running a non-activatable mercury.
     *
     * @param td the test description for the test
     * @param config the configuration object
     *
     * @return <code>false</code> if using jsse configurations
     */
    public boolean canRun(TestDescription td, QAConfig config) {
        String c =
           config.getStringConfigVal("org.apache.river.qa.harness.serviceMode",
                                     null);
        if (c != null) {
	    if (c.equals("activatable")) {
		return true;
	    } else {
		logger.log(Level.INFO,
			   "ActivatableMercuryVerifier configured to skip "
			   + td.getName());
		return false;
	    }
	}
	String mercury = "net.jini.event.EventMailbox";
	c = config.getStringConfigVal(mercury + ".type", "activatable");
	if (!c.equals("activatable")) {
	    logger.log(Level.INFO, 
                       "ActivatableMercuryVerifier configured to skip " 
                       + td.getName());
	    return false;
        }
        return true;
    }
}
