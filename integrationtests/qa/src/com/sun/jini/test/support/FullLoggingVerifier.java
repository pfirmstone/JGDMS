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

import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;
import com.sun.jini.qa.harness.ConfigurationVerifier;
import com.sun.jini.qa.harness.TestDescription;
import com.sun.jini.qa.harness.TestException;
import com.sun.jini.qa.harness.QAConfig;

/**
 * A configuration verifier used by tests which should not be run
 * when full logging is turned on
 */
public class FullLoggingVerifier implements ConfigurationVerifier {

    private static Logger logger = Logger.getLogger("com.sun.jini.qa.harness");

    /**
     * Return false if full logging is turned on. Full logging is assumed
     * to be turned on if the logging properties file contains entries
     * for <code>com.sun.jini.level</code> and <code>net.jini.level</code>
     * and both levels have the value <code>FINEST.</code>
     *
     * @param td the test description for the test
     * @param config the configuration object
     *
     * @return <code>false</code> if full logging is turned on
     */
    public boolean canRun(TestDescription td, QAConfig config) {
	boolean notFull = true;
	String propFile = System.getProperty("java.util.logging.config.file");
	if (propFile != null) {
	    try {
		Properties props = config.loadProperties(propFile);
		String level = props.getProperty("com.sun.jini.level", "INFO");
		if (level.equals("FINEST")) {
		    level = props.getProperty("net.jini.level", "INFO");
		    notFull = !level.equals("FINEST");
		}
	    } catch (TestException e) {
		logger.log(Level.SEVERE, "Could not load properties", e);
	    }
	}
	return notFull;
    }
}
