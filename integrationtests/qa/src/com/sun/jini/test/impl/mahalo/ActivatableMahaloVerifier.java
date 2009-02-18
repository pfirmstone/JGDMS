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

import com.sun.jini.qa.harness.ConfigurationVerifier;
import com.sun.jini.qa.harness.QAConfig;
import com.sun.jini.qa.harness.TestDescription;

import java.util.logging.Logger;
import java.util.logging.Level;

/**
 * A configuration verifier used by tests which should not be run
 * if mahalo is not if type activatable
 */
public class ActivatableMahaloVerifier implements ConfigurationVerifier {

    private static Logger logger = 
	Logger.getLogger("com.sun.jini.qa.harness.test");

    /**
     * Return false if running a non-activatable mahalo.
     *
     * @param td the test description for the test
     * @param config the configuration object
     *
     * @return <code>false</code> if testing non-activatable mahalo
     */
    public boolean canRun(TestDescription td, QAConfig config) {
        String c = 
	    config.getStringConfigVal("com.sun.jini.qa.harness.serviceMode",
				      null);
	if (c != null) {
	    if (c.equals("activatable")) {
		return true;
	    } else {
		logger.log(Level.INFO,
			   "\nActivatableMahaloVerifier configured to skip "
			   + td.getName() + "\n");
		return false;
	    }
	}
	String mahalo = "net.jini.core.transaction.server.TransactionManager";
	c = config.getStringConfigVal(mahalo + ".type", "activatable");
	if (!c.equals("activatable")) {
	    logger.log(Level.INFO, 
                       "\nActivatableMahaloVerifier configured to skip " 
                       + td.getName() + "\n");
	    return false;
        }
        return true;
    }
}
