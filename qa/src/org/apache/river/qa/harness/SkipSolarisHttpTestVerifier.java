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
 * on a Solaris operating system in the http test configuration.
 */
public class SkipSolarisHttpTestVerifier implements ConfigurationVerifier {

    private static Logger logger = 
	Logger.getLogger("org.apache.river.qa.harness");

    /**
     * Return false if running on a Solaris operating system, which is the case
     * if the system property "os.name" has the value "Solaris", and the
     * configuration is http.
     *
     * @param td the test description for the test
     * @param config the configuration object
     *
     * @return <code>false</code> if running on Solaris under http
     */
    public boolean canRun(TestDescription td, QAConfig config) {
	String currentConfig = config.getConfigurationTag();
	String os = System.getProperty("os.name","");
	if (os.equalsIgnoreCase("sunos") && currentConfig.equalsIgnoreCase("http")) {
	    logger.log(Level.INFO, 
                       "ATTENTION: SkipSolarisHttpTestVerifier configured to skip " 
                       + td.getName());
	    return false;
        }
        return true;
    }
}
