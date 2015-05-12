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

package org.apache.river.test.spec.config.configurationprovider;

import java.util.logging.Level;
import org.apache.river.qa.harness.QATestEnvironment;
import org.apache.river.qa.harness.QAConfig;
import org.apache.river.qa.harness.TestException;
import org.apache.river.qa.harness.TestException;
import java.util.logging.Logger;
import java.util.logging.Level;
import net.jini.config.Configuration;
import net.jini.config.ConfigurationProvider;
import net.jini.config.ConfigurationFile;
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
import java.security.AccessControlException;


/**
 * <pre>
 * Purpose:
 *   This test verifies the behavior of the
 *   getInstance(String[] options, ClassLoader cl)
 *   method of the {@link ConfigurationProvider} class.
 *
 * Infrastructure:
 *   This test requires the special policy file that denies permission
 *   to access the context class loader
 *
 * Actions:
 *   Test checks assertions and performs the following steps:
 *          Specifying null for the class loader argument uses
 *          the context class loader to load the provider class
 *          even if the caller does not have permission to access
 *          the context class loader.
 *              Steps:
 *          create resource file META-INF/services/net.jini.config
 *          .Configuration with ValidConfiguration class name
 *          for the provider;
 *          construct URL class loader with this resource;
 *          set this class loader as the context class loader;
 *          construct String [] options with some valid values;
 *          call the getInstance method from ConfigurationProvider
 *          class passing constructed options and null as parameters;
 *          assert that instance of ValidConfiguration is returned.
 * </pre>
 */
public class GetInstance_ClassLoaderDenyTest extends GetInstance_QATest {

    /**
     * This method performs all actions mentioned in class description.
     */
    public void run() throws Exception {
        logger.log(Level.INFO, "---------------------------");
        createConfFile(someValidConf);
        String[] validOptions = { confFilePath };
        String resourceBase = "jini.test.spec.config";
        URL[] urls = createUrls(resourceBase,
                ConfigurationFile.class.getName());
        ClassLoader myCL = new FakeURLClassLoader(urls);
        Thread.currentThread().setContextClassLoader(myCL);

        try {
            ClassLoader checkPolicy =
                    Thread.currentThread().getContextClassLoader();
            throw new TestException(
                    "getClassLoader permission should not be granted");
        } catch (AccessControlException ignore) {
            logger.log(Level.INFO,
                    "getClassLoader permission is not granted");
        }
        Exception result = testResource(validOptions,
                ConfigurationFile.class.getName(), null);

        if (result != null) {
            result.printStackTrace();
            throw new TestException(
                    "Unexpected ConfigurationException in case of"
                    + " null for the class loder argument");
        }
        removeResources(resourceBase);

        // clearing temporary file system
        confFile.delete();
    }
}
