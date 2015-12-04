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

package org.apache.river.test.spec.config.configurationexception;

import java.util.logging.Level;
import org.apache.river.qa.harness.QATestEnvironment;
import org.apache.river.qa.harness.QAConfig;
import org.apache.river.qa.harness.TestException;
import org.apache.river.qa.harness.TestException;
import org.apache.river.qa.harness.QAConfig;
import org.apache.river.qa.harness.Test;
import java.util.logging.Logger;
import java.util.logging.Level;
import net.jini.config.ConfigurationException;


/**
 * <pre>
 * Purpose:
 *   This test verifies the behavior of the toString method of
 *   ConfigurationException class.
 *
 * Actions:
 *   Test performs the following steps:
 *   1) construct a ConfigurationException object
 *      passing options with the some string as a parameters
 *      for message, and some exception for causing exception;
 *   2) assert the result includes the name of actual class;
 *   3) assert the result includes the string for message;
 *   4) assert the result includes result of calling toString
 *      on the causing exception;
 *   5) construct an instance of a subclass of ConfigurationException
 *      passing options with the some string as a parameters
 *      for message;
 *   6) assert the result includes the name of actual class;
 * </pre>
 */
class ConfigurationExceptionSuccessor extends ConfigurationException {
    /**
     * Creates an instance with the specified detail message.
     *
     * @param s the detail message
     */
    public ConfigurationExceptionSuccessor(String s) {
	super(s);
    }
}

public class ToString_Test extends QATestEnvironment implements Test {

    /**
     * This method performs all actions mentioned in class description.
     */
    public void run() throws Exception {
        logger.log(Level.INFO, "======================================");
        String messageString = "message string";
        String exceptionString = "exception string";
        Exception e = new Exception(exceptionString);
        ConfigurationException ce = new
                ConfigurationException(messageString, e);
        String result = ce.toString();
        logger.log(Level.INFO, "result = " + result);
        assertion(result != null, "result should not be null");
        String actualClassName = ce.getClass().getName();
        logger.log(Level.INFO, "actual class name = " + actualClassName);
        assertion(result.indexOf(actualClassName) != -1,
                "result string doesn't includes the name of actual class");
        assertion(result.indexOf(messageString) != -1,
                "result string doesn't includes the string for message");
        assertion(result.indexOf(e.toString()) != -1,
                "result string doesn't includes the result of calling"
                + " toString on the causing exception");
        ce = new ConfigurationExceptionSuccessor(messageString);
        result = ce.toString();
        actualClassName = ce.getClass().getName();
        logger.log(Level.INFO,
                "successor actual class name = " + actualClassName);
        assertion(result.indexOf(actualClassName) != -1,
                "result string doesn't includes the name of actual class");
    }
}
