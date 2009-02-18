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

package com.sun.jini.test.spec.config.configurationprovider;

import java.util.logging.Level;
import com.sun.jini.qa.harness.QATest;
import com.sun.jini.qa.harness.QAConfig;
import com.sun.jini.qa.harness.TestException;
import com.sun.jini.qa.harness.TestException;
import java.util.logging.Logger;
import java.util.logging.Level;
import net.jini.config.Configuration;
import net.jini.config.ConfigurationProvider;
import net.jini.config.ConfigurationException;
import net.jini.config.ConfigurationNotFoundException;
import java.io.File;
import java.io.OutputStream;
import java.io.FileOutputStream;
import java.io.Writer;
import java.io.BufferedWriter;
import java.io.OutputStreamWriter;
import java.io.FileNotFoundException;
import java.io.UnsupportedEncodingException;
import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;


/**
 * <pre>
 * Purpose:
 *   This test verifies the behavior of the getInstance(String[]) method
 *   of the ConfigurationProvider class.
 *
 * Actions:
 *   Test performs the following steps:
 *       1) call the static method getInstance from ConfigurationProvider
 *          class passing null as a parameter;
 *          assert that the result is instance of Configuration class
 *          or ConfigurationNotFoundException is thrown;
 *       2) construct String [] options with some valid configuration;
 *          call the getInstance method from ConfigurationProvider
 *          class passing constructed configuration as a parameter;
 *          assert that the result is instance of Configuration class;
 *       3) construct String [] options with configuration
 *          that specifies invalid source location;
 *          call the getInstance method from ConfigurationProvider
 *          class passing constructed configuration as a parameter;
 *          assert that ConfigurationNotFoundException is thrown;
 *       4) construct String [] options with configuration
 *          that specifies valid source location with invalid contents;
 *          call the getInstance method from ConfigurationProvider
 *          class passing constructed configuration as a parameter;
 *          assert that ConfigurationException is thrown;
 *       5) construct String [] options with null as a first element;
 *          call the getInstance method from ConfigurationProvider
 *          class passing constructed configuration as a parameter;
 *          assert that ConfigurationException is thrown;
 *       6) create empty resource file META-INF/services/net.jini.config
 *          .Configuration in current thread class loader;
 *          construct String [] options with some valid configuration;
 *          call the getInstance method from ConfigurationProvider
 *          class passing constructed configuration as a parameter;
 *          assert that ConfigurationException is thrown;
 *       7) create resource file META-INF/services/net.jini.config
 *          .Configuration with unexistent class name for the provider;
 *          construct String [] options with some valid configuration;
 *          call the getInstance method from ConfigurationProvider
 *          class passing constructed configuration as a parameter;
 *          assert that ConfigurationException is thrown;
 *       8) create resource file META-INF/services/net.jini.config
 *          .Configuration with existent but not public class name
 *          for the provider;
 *          construct String [] options with some valid configuration;
 *          call the getInstance method from ConfigurationProvider
 *          class passing constructed configuration as a parameter;
 *          assert that ConfigurationException is thrown;
 *       9) create resource file META-INF/services/net.jini.config
 *          .Configuration with class name for the provider that does
 *          not implement Configuration;
 *          construct String [] options with some valid configuration;
 *          call the getInstance method from ConfigurationProvider
 *          class passing constructed configuration as a parameter;
 *          assert that ConfigurationException is thrown;
 *       10) create resource file META-INF/services/net.jini.config
 *          .Configuration with class name for the provider that does
 *          not have correct constructor;
 *          construct String [] options with some valid configuration;
 *          call the getInstance method from ConfigurationProvider
 *          class passing constructed configuration as a parameter;
 *          assert that ConfigurationException is thrown;
 *       11) create resource file META-INF/services/net.jini.config
 *          .Configuration with some valid class name for the provider;
 *          construct String [] options with some valid configuration;
 *          call the getInstance method from ConfigurationProvider
 *          class passing constructed configuration as a parameter;
 *          assert that getInstance method return valid class.
 *
 * </pre>
 */
public class GetInstance_Test extends GetInstance_QATest {

    /**
     * This method performs all actions mentioned in class description.
     */
    public void run() throws Exception {
        Configuration conf;

        // actions step 1
        try {
            conf = ConfigurationProvider.getInstance(null);

            if (conf == null) {
                throw new TestException(
                        "getInstance method return is equal to null");
            };
        } catch (ConfigurationNotFoundException cnfe) {
            logger.log(Level.INFO,
                    "ConfigurationNotFoundException is possible"
                    + " if options is equal to null");
        }

        // actions step 2
        createConfFile(someValidConf);
        String[] validOptions = { confFilePath };
        conf = ConfigurationProvider.getInstance(validOptions);

        if (conf == null) {
            throw new TestException(
                    "getInstance method return is equal to null");
        }

        // actions step 3
        String[] optionsWithInvalidLocation = { "<some invalid location>" };

        try {
            conf =
                    ConfigurationProvider.getInstance(
                    optionsWithInvalidLocation);
            throw new TestException(
                    "ConfigurationNotFoundException should be thrown");
        } catch (ConfigurationNotFoundException cnfe) {
            logger.log(Level.INFO,
                    "ConfigurationException in case of invalid"
                    + " configuration file name");
        }

        // actions step 4
        createConfFile("<some invalid configuration>");
        String[] invalidOptions = { confFilePath };

        try {
            conf = ConfigurationProvider.getInstance(invalidOptions);
            throw new TestException(
                    "ConfigurationException should be thrown");
        } catch (ConfigurationException ce) {
            logger.log(Level.INFO,
                    "ConfigurationException in case of invalid"
                    + " configuration file content");
        }

        // actions step 5
        String[] optionsWithNull = { null };

        try {
            conf = ConfigurationProvider.getInstance(optionsWithNull);
            throw new TestException(
                    "ConfigurationException should be thrown");
        } catch (ConfigurationException ce) {
            logger.log(Level.INFO,
                    "ConfigurationException in case of"
                    + " null containing options");
        }

        // actions step 6
        if (testResource("") == null) {
            throw new TestException(
                    "ConfigurationException should be thrown if"
                    + " resource file net.jini.config.Configuration"
                    + " is empty");
        } else {
            logger.log(Level.INFO,
                    "ConfigurationException in case of empty resource"
                    + " file net.jini.config.Configuration");
        }

        // actions step 7
        if (testResource("UnexistentClassConfiguration") == null) {
            throw new TestException(
                    "ConfigurationException should be thrown if"
                    + " resource file net.jini.config.Configuration"
                    + " contains unexist class name");
        } else {
            logger.log(Level.INFO,
                    "ConfigurationException in case of resource"
                    + " file net.jini.config.Configuration contains"
                    + " unexist class name");
        }

        // actions step 8
        if (testResource(NonPublicConfiguration.class.getName()) == null) {
            throw new TestException(
                    "ConfigurationException should be thrown if"
                    + " resource file net.jini.config.Configuration"
                    + " contains non public configuration provider"
                    + " class name");
        } else {
            logger.log(Level.INFO,
                    "ConfigurationException in case of resource"
                    + " file net.jini.config.Configuration contains"
                    + " non public configuration provider"
                    + " class name");
        }

        if (NonPublicConfiguration.wasCalled) {
            throw new TestException(
                    "ConfigurationException should be thrown if"
                    + " resource file net.jini.config.Configuration"
                    + " contains non public configuration provider"
                    + " class name");
        }

        // actions step 9
        if (testResource(NonImplConfiguration.class.getName()) == null) {
            throw new TestException(
                    "ConfigurationException should be thrown if"
                    + " resource file net.jini.config.Configuration"
                    + " contains configuration provider"
                    + " class that doesn't implement Configuration");
        } else {
            logger.log(Level.INFO,
                    "ConfigurationException in case of resource"
                    + " file net.jini.config.Configuration contains"
                    + " configuration provider"
                    + " class that doesn't implement Configuration");
        }

        if (NonImplConfiguration.wasCalled) {
            throw new TestException(
                    "ConfigurationException should be thrown if"
                    + " resource file net.jini.config.Configuration"
                    + " contains configuration provider"
                    + " class that doesn't implement Configuration");
        }

        // actions step 10
        if (testResource(BadConstructorConfiguration.class.getName())
                == null) {
            throw new TestException(
                    "ConfigurationException should be thrown if"
                    + " resource file net.jini.config.Configuration"
                    + " contains configuration provider"
                    + " class that doesn't have correct constructor");
        } else {
            logger.log(Level.INFO,
                    "ConfigurationException in case of resource"
                    + " file net.jini.config.Configuration contains"
                    + " configuration provider"
                    + " class that doesn't have correct constructor");
        }

        if (BadConstructorConfiguration.wasCalled) {
            throw new TestException(
                    "ConfigurationException should be thrown if"
                    + " resource file net.jini.config.Configuration"
                    + " contains configuration provider"
                    + " class that doesn't have correct constructor");
        }

        // actions step 11
        Exception result =
                testResource(ValidConfiguration.class.getName());

        if (result != null)  {
            result.printStackTrace();
            throw new TestException(
                    "Unexpected ConfigurationException in case of"
                    + " resource file net.jini.config.Configuration"
                    + " contains valid configuration provider");
        }
    }
}
