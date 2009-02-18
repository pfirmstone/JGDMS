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


/**
 * <pre>
 * Purpose:
 *   This test verifies the behavior of the
 *   getInstance(String[] options, ClassLoader cl)
 *   method of the {@link ConfigurationProvider} class.
 *
 * Actions:
 *   Test checks assertions and performs the following steps:
 *       1) Available provider specific default options.
 *              Steps:
 *          create resource file META-INF/services/net.jini.config
 *          .Configuration with some valid class name that supports
 *          default options (ValidConfiguration class);
 *          construct URL class loader with this resource;
 *          call the static method getInstance from ConfigurationProvider
 *          class passing null and constructed class loader
 *          as parameters;
 *          assert that the result is instance of specified ValidConfiguration
 *          class.
 *       2) Unavailable provider specific default options.
 *              Steps:
 *          create resource file META-INF/services/net.jini.config
 *          .Configuration with some valid class name that doesn't supports
 *          default options (ValidConfigurationWithoutDefaults class) for
 *          the provider;
 *          construct URL class loader with this resource;
 *          call the static method getInstance from ConfigurationProvider
 *          class passing null and constructed class loader
 *          as parameters;
 *          assert that ConfigurationNotFoundException is thrown.
 *       3) Constructing an instance of the configuration provider
 *          with the specified options and class loader.
 *              Steps:
 *          create resource file META-INF/services/net.jini.config
 *          .Configuration with some valid class name
 *          (ValidConfiguration class);
 *          construct URL class loader with this resource;
 *          construct String [] options with some valid values;
 *          call the getInstance method from ConfigurationProvider
 *          class passing constructed options and class loader as parameters;
 *          assert that the result is instance of specified ValidConfiguration
 *          class;
 *       4) Invalid source location in options.
 *              Steps:
 *          create resource file META-INF/services/net.jini.config
 *          .Configuration with some valid class name
 *          (ConfigurationFile class);
 *          construct URL class loader with this resource;
 *          construct String [] options with values that specifies
 *          invalid source location;
 *          call the getInstance method from ConfigurationProvider
 *          class passing constructed options and class loader as parameters;
 *          assert that ConfigurationNotFoundException is thrown;
 *       5) Invalid content source location.
 *              Steps:
 *          create resource file META-INF/services/net.jini.config
 *          .Configuration with some valid class name
 *          (ConfigurationFile class);
 *          construct URL class loader with this resource;
 *          construct String [] options with values that specifies
 *          valid source location with invalid contents;
 *          call the getInstance method from ConfigurationProvider
 *          class passing constructed options and class loader as parameters;
 *          assert that ConfigurationException is thrown;
 *       6) Problem with the contents of the resource file that names
 *          the configuration provider.
 *              Steps:
 *          create empty resource file META-INF/services/net.jini.config
 *          .Configuration;
 *          construct URL class loader with this resource;
 *          construct String [] options with some valid values;
 *          call the getInstance method from ConfigurationProvider
 *          class passing constructed options and class loader as parameters;
 *          assert that ConfigurationException is thrown;
 *       7) Configured provider class does not exist.
 *              Steps:
 *          create resource file META-INF/services/net.jini.config
 *          .Configuration with unexistent class name for the provider;
 *          construct URL class loader with this resource;
 *          construct String [] options with some valid values;
 *          call the getInstance method from ConfigurationProvider
 *          class passing constructed options and class loader as parameters;
 *          assert that ConfigurationException is thrown;
 *       8) Configured provider class is not public.
 *              Steps:
 *          create resource file META-INF/services/net.jini.config
 *          .Configuration with existent but not public class name
 *          for the provider (NonPublicConfiguration class);
 *          construct URL class loader with this resource;
 *          construct String [] options with some valid values;
 *          call the getInstance method from ConfigurationProvider
 *          class passing constructed options and class loader as parameters;
 *          assert that ConfigurationException is thrown;
 *       9) Configured provider class does not implement Configuration.
 *              Steps:
 *          create resource file META-INF/services/net.jini.config
 *          .Configuration with class name for the provider that does
 *          not implement Configuration;
 *          construct URL class loader with this resource;
 *          construct String [] options with some valid values;
 *          call the getInstance method from ConfigurationProvider
 *          class passing constructed options and class loader as parameters;
 *          assert that ConfigurationException is thrown;
 *       10) Configured provider class does have valid constructor.
 *              Steps:
 *          create resource file META-INF/services/net.jini.config
 *          .Configuration with class name for the provider that does
 *          not have correct constructor;
 *          construct URL class loader with this resource;
 *          construct String [] options with some valid values;
 *          call the getInstance method from ConfigurationProvider
 *          class passing constructed options and class loader as parameters;
 *          assert that ConfigurationException is thrown;
 *       11) Resources available in the class loader of the caller,
 *          and in the context class loader, are not used for locating
 *          providers if a class loader is specified that doesn't
 *          inherit from those loaders.
 *              Steps:
 *          create resource file META-INF/services/net.jini.config
 *          .Configuration with some valid class name
 *          (ValidConfiguration class) for the provider;
 *          construct URL class loader with this resource;
 *          create second resource file META-INF/services/net.jini.config
 *          .Configuration with some other class name
 *          (NonPublicConfiguration class) for the provider;
 *          construct second URL class loader with this resource;
 *          set this second URL class loader as the context class loader;
 *          construct String [] options with some valid values;
 *          call the getInstance method from ConfigurationProvider
 *          class passing constructed options and class loader as parameters;
 *          assert that getInstance method return valid class
 *          (ValidConfiguration class).
 *       12) If multiple provider resources are available, only the last
 *          one is used.
 *              Steps:
 *          create resource file META-INF/services/net.jini.config
 *          .Configuration with some invalid class name
 *          (NonPublicConfiguration class) for the provider;
 *          construct URL class loader with this resource;
 *          create second resource file META-INF/services/net.jini.config
 *          .Configuration with some valid class name
 *          (ValidConfiguration class) for the provider;
 *          construct second URL class loader with this resource passing
 *          first class loader as parent;
 *          construct String [] options with some valid values;
 *          call the getInstance method from ConfigurationProvider
 *          class passing constructed options and class loader as parameters;
 *          assert that getInstance method return valid class
 *          (ValidConfiguration class).
 *       13) Comments in resource files are ignored.
 *              Steps:
 *          create resource file META-INF/services/net.jini.config
 *          .Configuration with some valid class name
 *          (ValidConfiguration class) for the provider and
 *          comments before, at the end of class name string and
 *          after that string, include also some new lines, space and tab
 *          characters;
 *          construct URL class loader with this resource;
 *          construct String [] options with some valid values;
 *          call the getInstance method from ConfigurationProvider
 *          class passing constructed options and class loader as parameters;
 *          assert that getInstance method return valid class
 *          (ValidConfiguration class).
 *       14) Using a resource file containing multiple entries
 *          results in a ConfigurationException.
 *              Steps:
 *          create resource file META-INF/services/net.jini.config
 *          .Configuration with two valid class names
 *          (ValidConfiguration  and ValidConfiguration2 classes)
 *          for the provider;
 *          construct URL class loader with this resource;
 *          construct String [] options with some valid values;
 *          call the getInstance method from ConfigurationProvider
 *          class passing constructed options and class loader as parameters;
 *          assert that ConfigurationException is thrown.
 *       15) Specifying null for the class loader argument uses
 *          the context class loader.
 *              Steps:
 *          create resource file META-INF/services/net.jini.config
 *          .Configuration with some valid class name
 *          (ValidConfiguration class) for the provider;
 *          construct URL class loader with this resource;
 *          set this class loader as the context class loader;
 *          construct String [] options with some valid values;
 *          call the getInstance method from ConfigurationProvider
 *          class passing constructed options and null for class loader
 *          as parameters;
 *          assert that getInstance method return valid class
 *          (ValidConfiguration class).
 *       16) Specifying null for the class loader argument when
 *          the context class loader is not set.
 *              Steps:
 *          set null as the context class loader;
 *          construct String [] options with some valid values;
 *          call the getInstance method from ConfigurationProvider
 *          class passing constructed options and null for class loader
 *          as parameters;
 *          assert that ConfigurationException is thrown.
 *       17) Specifying null for the class loader argument results
 *          in null being passed to the provider's constructor --
 *          the context class loader should not be passed.
 *              Steps:
 *          create resource file META-INF/services/net.jini.config
 *          .Configuration with ValidConfiguration class name
 *          for the provider;
 *          construct URL class loader with this resource;
 *          set this class loader as the context class loader;
 *          construct String [] options with some valid values;
 *          call the getInstance method from ConfigurationProvider
 *          class passing constructed options and null for class loader
 *          as parameters;
 *          assert that ValidConfiguration has null as class loader
 *          argument.
 *       18) Specifying a non-null value for the class loader argument
 *          results in that value being passed to the provider's
 *          constructor.
 *              Steps:
 *          create resource file META-INF/services/net.jini.config
 *          .Configuration with ValidConfiguration class name
 *          for the provider;
 *          construct URL class loader with this resource;
 *          construct String [] options with some valid values;
 *          call the getInstance method from ConfigurationProvider
 *          class passing constructed options and constructed class
 *          loader as parameters;
 *          assert that ValidConfiguration has this constructed class
 *          loader as class loader argument.
 *       19) ConfigurationException is thrown if the provider class
 *          has correct constructor, but it is non-public.
 *              Steps:
 *          create resource file META-INF/services/net.jini.config
 *          .Configuration with NonPublicConfiguration class name
 *          for the provider;
 *          construct URL class loader with this resource;
 *          construct String [] options with some valid values;
 *          call the getInstance method from ConfigurationProvider
 *          class passing constructed options and constructed class
 *          loader as parameters;
 *          assert that ConfigurationException is thrown.
 *       20) ConfigurationException is thrown if the provider class
 *          is abstract.
 *              Steps:
 *          create resource file META-INF/services/net.jini.config
 *          .Configuration with AbstractConfiguration class name
 *          for the provider;
 *          construct URL class loader with this resource;
 *          construct String [] options with some valid values;
 *          call the getInstance method from ConfigurationProvider
 *          class passing constructed options and constructed class
 *          loader as parameters;
 *          assert that ConfigurationException is thrown.
 *       21) If no resource is available that ConfigurationFile is used.
 *              Steps:
 *          assert that system class loader doesn't contain resource file
 *          META-INF/services/net.jini.config.Configuration;
 *          construct String [] options with some valid values;
 *          call the getInstance method from ConfigurationProvider
 *          class passing constructed options and system class loader
 *          as parameters;
 *          assert that instance of ConfigurationFile is returned.
 * </pre>
 */
public class GetInstance_ClassLoaderTest extends GetInstance_QATest {

    /**
     * This method performs all actions mentioned in class description.
     */
    public void run() throws Exception {
        logger.log(Level.INFO, "---------------------------");
        ClassLoader defaultCL =
                Thread.currentThread().getContextClassLoader();
        Configuration conf;

        // 1
        Exception result = testResource(null,
                ValidConfiguration.class.getName());

        if (result != null) {
            result.printStackTrace();
            throw new TestException(
                    "Unexpected ConfigurationException in case of"
                    + " resource file net.jini.config.Configuration"
                    + " contains valid configuration provider"
                    + " that supports default options");
        }

        // 2
        if (testResource(null,
                ValidConfigurationWithoutDefaults.class.getName()) == null)
        {
            throw new TestException(
                    "ConfigurationException should be thrown if"
                    + " proider doesn't supplies default options");
        } else {
            logger.log(Level.INFO,
                    "ConfigurationException in case of null options"
                    + " and the proider doesn't supplies default options");
        }

        // 3
        result = testResource(ValidConfiguration.class.getName());
        if (result != null) {
            result.printStackTrace();
            throw new TestException(
                    "Unexpected ConfigurationException in case of"
                    + " resource file net.jini.config.Configuration"
                    + " contains valid configuration provider");
        }

        // 4
        String[] optionsWithInvalidLocation = { "<some invalid location>" };
        if (testResource(optionsWithInvalidLocation,
                ConfigurationFile.class.getName()) == null) {
            throw new TestException(
                    "ConfigurationException should be thrown if options"
                    + " contain invalid configuration file name");
        } else {
            logger.log(Level.INFO,
                    "ConfigurationException in case of invalid"
                    + " configuration file name");
        }

        // 5
        createConfFile("<some invalid configuration>");
        String[] invalidOptions = { confFilePath };
        if (testResource(invalidOptions,
                ConfigurationFile.class.getName()) == null) {
            throw new TestException(
                    "ConfigurationException should be thrown if options"
                    + " contain invalid configuration file content");
        } else {
            logger.log(Level.INFO,
                    "ConfigurationException in case of invalid"
                    + " configuration file content");
        }

        // 6
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

        // 7
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

        // 8
        NonPublicConfiguration.wasCalled = false;
        if ((testResource(NonPublicConfiguration.class.getName()) == null)
                || NonPublicConfiguration.wasCalled) {
            throw new TestException(
                    "ConfigurationException should be thrown if"
                    + " resource file net.jini.config.Configuration"
                    + " contains non public configuration provider"
                    + " class name");
        } else {
            logger.log(Level.INFO,
                    "ConfigurationException in case of resource"
                    + " file net.jini.config.Configuration contains"
                    + " non public configuration provider class name");
        }

        // 9
        NonImplConfiguration.wasCalled = false;
        if ((testResource(NonImplConfiguration.class.getName()) == null)
                || NonImplConfiguration.wasCalled) {
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

        // 10
        BadConstructorConfiguration.wasCalled = false;
        if ((testResource(BadConstructorConfiguration.class.getName()) 
                == null) || BadConstructorConfiguration.wasCalled) {
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

        // 11
        String resourceBase2 = "jini.test.spec.config.2";
        ClassLoader myCL = createClassLoader(resourceBase2,
                NonPublicConfiguration.class.getName());
        Thread.currentThread().setContextClassLoader(myCL);
        result = testResource(ValidConfiguration.class.getName());
        if (result != null) {
            result.printStackTrace();
            throw new TestException(
                    "Unexpected ConfigurationException in case of"
                    + " resource file net.jini.config.Configuration"
                    + " contains valid configuration provider");
        }
        Thread.currentThread().setContextClassLoader(defaultCL);
        removeResources(resourceBase2);

        // 12
        resourceBase2 = "jini.test.spec.config.2";
        myCL = createClassLoader(resourceBase2,
                NonPublicConfiguration.class.getName());
        String resourceBase3 = "jini.test.spec.config.3";
        URL[] urls = createClassLoader(resourceBase3,
                ValidConfiguration.class.getName()).getURLs();
        myCL = new URLClassLoader(urls, myCL);
        createConfFile(someValidConf);
        String[] validOptions = { confFilePath };
        result = testResource(validOptions,
                ValidConfiguration.class.getName(), myCL);
        if (result != null) {
            result.printStackTrace();
            throw new TestException(
                    "Unexpected ConfigurationException in case of"
                    + " multiple provider resources are available");
        }
        removeResources(resourceBase2);
        removeResources(resourceBase3);

        // 13
        String resourceBase = "jini.test.spec.config";
        myCL = createClassLoader(resourceBase,
            "\n"
            + "# comment 1 \n"
            + " # comment 2 \n"
            + "\t# comment 3 \n"
            + "\t\n"
            + " \t"
            + ValidConfiguration.class.getName()
            + " \t# comment 4 \n"
            + "# comment 5 \n"
            + " ");
        result = testResource(validOptions,
                ValidConfiguration.class.getName(), myCL);
        if (result != null) {
            result.printStackTrace();
            throw new TestException(
                    "Unexpected ConfigurationException in case of"
                    + " resource file net.jini.config.Configuration"
                    + " contains some comments");
        }
        removeResources(resourceBase);

        // 14
        ValidConfiguration.wasCalled = false;
        String resourceContent =
                ValidConfiguration.class.getName()
                + "\n" 
                + ValidConfiguration.class.getName();
        if ((testResource(resourceContent) == null)
                || ValidConfiguration.wasCalled) {
            throw new TestException(
                    "ConfigurationException should be thrown if"
                    + " resource file net.jini.config.Configuration"
                    + " contains two class names");
        } else {
            logger.log(Level.INFO,
                    "ConfigurationException in case of resource"
                    + " file net.jini.config.Configuration contains"
                    + " two class names");
        }

        // 15
        resourceBase = "jini.test.spec.config";
        myCL = createClassLoader(resourceBase,
            ValidConfiguration.class.getName());
        Thread.currentThread().setContextClassLoader(myCL);
        result = testResource(validOptions,
                ValidConfiguration.class.getName(), null);
        if (result != null) {
            result.printStackTrace();
            throw new TestException(
                    "Unexpected ConfigurationException in case of"
                    + " null for the class loder argument");
        }
        Thread.currentThread().setContextClassLoader(defaultCL);
        removeResources(resourceBase);

        // 16
        ValidConfiguration.wasCalled = false;
        Thread.currentThread().setContextClassLoader(null);
        if ((testResource(validOptions,
                ValidConfiguration.class.getName(), null) == null)
                || ValidConfiguration.wasCalled) {
            throw new TestException(
                    "ConfigurationException should be thrown if"
                    + " context class loader is not set and"
                    + " null for the class loder argument");
        } else {
            logger.log(Level.INFO,
                    "ConfigurationException in case of"
                    + " context class loader is not set and"
                    + " null for the class loder argument");
        }
        Thread.currentThread().setContextClassLoader(defaultCL);

        // 17
        resourceBase = "jini.test.spec.config";
        myCL = createClassLoader(resourceBase,
            ValidConfiguration.class.getName());
        Thread.currentThread().setContextClassLoader(myCL);
        ValidConfiguration.obtainedCl = myCL;
        result = testResource(validOptions,
                ValidConfiguration.class.getName(), null);
        if (ValidConfiguration.obtainedCl != null) {
            throw new TestException(
                    "Provider constructor should obtain"
                    + " null as the class loder argument");
        }
        Thread.currentThread().setContextClassLoader(defaultCL);
        removeResources(resourceBase);

        // 18
        resourceBase = "jini.test.spec.config";
        myCL = createClassLoader(resourceBase,
            ValidConfiguration.class.getName());
        ValidConfiguration.obtainedCl = null;
        result = testResource(validOptions,
                ValidConfiguration.class.getName(), myCL);
        if (ValidConfiguration.obtainedCl != myCL) {
            throw new TestException(
                    "Provider constructor should obtain same class"
                    + " loder as in getInstance argument");
        }
        removeResources(resourceBase);

        // 19
        NonPublicConfiguration.wasCalled = false;
        if ((testResource(NonPublicConfiguration.class.getName()) == null)
                || NonPublicConfiguration.wasCalled) {
            throw new TestException(
                    "ConfigurationException should be thrown if"
                    + " provider class has correct constructor,"
                    + " but it is non-public");
        } else {
            logger.log(Level.INFO,
                    "ConfigurationException in case of"
                    + " provider class has correct constructor,"
                    + " but it is non-public");
        }

        // 20
        AbstractConfiguration.wasCalled = false;
        if ((testResource(AbstractConfiguration.class.getName()) == null)
                || AbstractConfiguration.wasCalled) {
            throw new TestException(
                    "ConfigurationException should be thrown if"
                    + " provider class is abstact");
        } else {
            logger.log(Level.INFO,
                    "ConfigurationException in case of"
                    + " provider class is abstact");
        }

        // 21
        ClassLoader sysCL = ClassLoader.getSystemClassLoader();
        String resourceName = "META-INF/services/"
                + Configuration.class.getName();
        URL resURL = sysCL.getResource(resourceName);
        if (resURL != null) {
            throw new TestException(
                    "Resource file shouldn't be available");
        }
        result = testResource(validOptions,
                ConfigurationFile.class.getName(), sysCL);
        if (result != null) {
            result.printStackTrace();
            throw new TestException(
                    "Unexpected ConfigurationException in case of"
                    + " no resource is available");
        }

        // clearing temporary file system
        confFile.delete();
    }
}
