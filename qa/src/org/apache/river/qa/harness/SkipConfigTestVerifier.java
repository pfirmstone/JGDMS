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
package org.apache.river.qa.harness;

import java.util.logging.Logger;
import java.util.logging.Level;

/**
 * A configuration verifier used by tests which should not be run
 * in a given configuration. Used to temporarily disable a test.
 * <p>
 * This verifier uses the 
 * <code>org.apache.river.qa.harness.skipConfigs</code> property to
 * determine what configurations to skip.  This property should
 * contain a comma separated list of configurations.
 */
public class SkipConfigTestVerifier implements ConfigurationVerifier {

    private static Logger logger = 
	Logger.getLogger("org.apache.river.qa.harness");

    /**
     * Return false if running in any configuration specified by the
     * <code>org.apache.river.qa.harness.skipConfigs</code> property.
     * <p>
     *
     * @param td the test description for the test
     * @param config the configuration object
     *
     * @return <code>false</code> if running in any configuration
     *         specified by the 
     *         <code>org.apache.river.qa.harness.skipConfigs</code> property
     */
    public boolean canRun(TestDescription td, QAConfig config) {
	String[] configList = config.parseString(
	    config.getStringConfigVal("org.apache.river.qa.harness.skipConfigs",""),
	    " ,");
	String currentConfig = config.getConfigurationTag();
	for (int i = 0; i < configList.length; i++) {
	    if (currentConfig.equals(configList[i])) {
	        logger.log(Level.INFO, 
                       "ATTENTION: SkipConfigTestVerifier configured to skip " 
                       + td.getName());
	        return false;
            }
	}
        return true;
    }
}
