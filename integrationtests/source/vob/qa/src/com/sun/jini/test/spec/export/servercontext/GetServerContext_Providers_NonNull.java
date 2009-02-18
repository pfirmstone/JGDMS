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
package com.sun.jini.test.spec.export.servercontext;

import java.util.logging.Level;

// com.sun.jini.qa
import com.sun.jini.qa.harness.QATest;
import com.sun.jini.qa.harness.QAConfig;

// com.sun.jini.qa.harness
import com.sun.jini.qa.harness.QAConfig; // base class for QAConfig
import com.sun.jini.qa.harness.TestException;

// java.util
import java.util.logging.Level;
import java.util.Collection;
import java.util.HashSet;
import java.util.Enumeration;
import java.util.jar.JarFile;
import java.util.zip.ZipEntry;

// java.io
import java.io.InputStream;
import java.io.File;

// java.net
import java.net.URL;

// davis packages
import net.jini.export.ServerContext;


/**
 * <pre>
 *
 * Purpose:
 *   This test verifies the behavior of the
 *   {@link net.jini.export.ServerContext#getServerContext()} method.
 *   getServerContext() returns the server context collection for the current
 *   thread. If no server context collection has been explicitly specified
 *   via a previous call to
 *   {@link net.jini.export.ServerContext#doWithServerContext(Runnable,Collection)},
 *   then an ordered list of providers implementing the
 *   {@link net.jini.export.ServerContext.Spi} interface is consulted.
 *   {@link net.jini.export.ServerContext.Spi#getServerContext()} is called on
 *   each provider in turn; the first non-null return value is
 *   returned by this method.
 *
 * Infrastructure:
 *     - {@link GetServerContext_Providers_NonNull}
 *         performs actions; this file
 *     - src/manifest/qa1-export-servercontext-tests/non-null-context/
 *       META-INF/services/net.jini.export.ServerContext$Spi
 *         - is visible to the system class loader as a .jar file named
 *           qa1-export-servercontext-tests-non-null.jar,
 *         - contains 3 lines:
 *             com.sun.jini.test.spec.export.util.NullServerContext
 *             com.sun.jini.test.spec.export.util.NonNullServerContext
 *             com.sun.jini.test.spec.export.util.NonNullServerContext_Another
 *     - {@link com.sun.jini.test.spec.export.util.NullServerContext}
 *         server context provider that implements
 *         {@link net.jini.export.ServerContext.Spi} interface; 
 *         {@link com.sun.jini.test.spec.export.util.NullServerContext#getServerContext()}
 *         method returns null;
 *     - {@link com.sun.jini.test.spec.export.util.NonNullServerContext}
 *         server context provider that implements
 *         {@link net.jini.export.ServerContext.Spi} interface; 
 *         {@link com.sun.jini.test.spec.export.util.NonNullServerContext#getServerContext()}
 *         method returns {@link java.util.Collection} that contains only one
 *         element - {@link java.lang.String} object -
 *         "com.sun.jini.test.spec.export.util.NonNullServerContext";
 *     - {@link com.sun.jini.test.spec.export.util.NonNullServerContext_Another}
 *         server context provider that implements
 *         {@link net.jini.export.ServerContext.Spi} interface; 
 *         {@link com.sun.jini.test.spec.export.util.NonNullServerContext_Another#getServerContext()}
 *         method returns {@link java.util.Collection} that contains only one
 *         element - {@link java.lang.String} object -
 *         "com.sun.jini.test.spec.export.util.NonNullServerContext_Another";
 *
 * Actions:
 *   Test performs the following steps:
 *     - checking that there are at least 2 server context providers
 *       implementing {@link net.jini.export.ServerContext.Spi} interface whose
 *       getServerContext() method returns non-null value (NonNullServerContext
 *       and NonNullServerContext_Another) that are visible to the system class
 *       loader;
 *     - invoking ServerContext.getServerContext() method when:
 *       - no context is explicitly set for the current thread using
 *         ServerContext.doWithServerContext();
 *       - there are 2 server context providers that implement ServerContext.Spi
 *         interface and whose getServerContext() method returns non-null values:
 *           "com.sun.jini.test.spec.export.util.NonNullServerContext"
 *           "com.sun.jini.test.spec.export.util.NonNullServerContext_Another"
 *     - verifying that ServerContext.Spi.getServerContext() is called on each
 *       server context provider implementing the ServerContext.Spi interface
 *       and the first non-null return value
 *       ("com.sun.jini.test.spec.export.util.NonNullServerContext") is returned
 *       by ServerContext.getServerContext() method.
 *
 * </pre>
 */
public class GetServerContext_Providers_NonNull extends QATest {
    QAConfig config;

    /**
     * Contents of expected result.
     */
    final String expectedStr =
            "com.sun.jini.test.spec.export.util.NonNullServerContext";

    /**
     * Expected result of
     * {@link net.jini.export.ServerContext#getServerContext()} method.
     */
    Collection expectedRes = new HashSet();

    /**
     * The name of Jar file that contains resource named
     * META-INF/services/net.jini.export.ServerContext$Spi.
     */
    final String jarFileName = "qa1-export-servercontext-tests-non-null.jar";

    /**
     * The name of resource.
     */
    final String metaFileName =
            "META-INF/services/net.jini.export.ServerContext$Spi";

    /**
     * Pattern used in matches() method.
     */
    final String pat = ".*" + jarFileName + ".*"
            + "META-INF/services/net.jini.export.ServerContext\\$Spi";

    /**
     * Class object for Server Context Provider Interface
     * {@link net.jini.export.ServerContext.Spi}.
     */
    final Class srvCnxtSpiClass = ServerContext.Spi.class;

    /**
     * This method performs all preparations.
     */
    public void setup(QAConfig config) throws Exception {
        super.setup(config);
        this.config = (QAConfig) config; // or this.config = getConfig();
        // Prepare expected result
        expectedRes.add(expectedStr);

        /*
         * Get System Class Loader.
         */
        ClassLoader sysCL = ClassLoader.getSystemClassLoader();
        // logger.log(Level.INFO, "System Class Loader: " + sysCL);

        /*
         * Obtain list of all server context provider implementing
         * net.jini.export.ServerContext.Spi interface.
         * Verify that the list contains the resource named
         * qa1-export-servercontext-tests-non-null.jar.
         */
        boolean resourceIsAdded = false;
        Enumeration resources = sysCL.getSystemResources(metaFileName);

        for (; resources.hasMoreElements();) {
            URL resource = (URL) resources.nextElement();
            String resourceName = resource.toString();
            logger.log(Level.INFO, "Resource name: " + resourceName);

            if (resourceName.matches(pat)) {
                resourceIsAdded = true;
            }
        }

        if (!resourceIsAdded) {
            throw new TestException("Resource named " + jarFileName
                    + " isn't visible to the system class loader");
        }
        logger.log(Level.INFO,
                "Resource named " + jarFileName
                + " is visible to the system class loader");

        /*
         * Checking that there are 2 Server Context Providers that implement
         * net.jini.export.ServerContext.Spi interface and whose
         * getServerContext() method returns non-null value. The list of all
         * Server Context Providers is obtained from all available resources
         * and for each Server Context Provider the following is performed:
         *   - get the the name of Server Context Provider from the resource
         *     named META-INF/services/net.jini.export.ServerContext$Spi,
         *   - invoke getServerContext() method on the Server Context
         *     Provider,
         *   - verify that there are 2 Server Context Providers whose
         *     getServerContext() method returns non-null values;
         *   - verify that one of these non-null values is equal to
         *     the expected result.
         */
        resources = sysCL.getSystemResources(metaFileName);
        HashSet contexts = new HashSet();

        for (; resources.hasMoreElements();) {
            URL resource = (URL) resources.nextElement();
            String resourceFile = resource.getFile();
            logger.log(Level.INFO, "\n\nResource: " + resourceFile);

            if (resourceFile.matches(".*\\.jar.*")) {
                logger.log(Level.INFO, "The resource is a JAR file!");
                String fname = resourceFile.substring(resourceFile.indexOf(":")
                        + 1, resourceFile.indexOf("!"));
                File file = new File(fname);
                logger.log(Level.INFO, "The resource filename: " + file);
                // Create Zip Entry
                JarFile jarf = new JarFile(file);
                ZipEntry zipEntry;

                if ((zipEntry = jarf.getEntry(metaFileName)) == null) {
                    throw new TestException("There is no " + metaFileName
                            + " entry in the resource");
                }
                logger.log(Level.INFO,
                        "Zip Entry in the resource: " + zipEntry);
                // Read from the created Zip Entry
                InputStream in = jarf.getInputStream(zipEntry);
                int cc;
                StringBuffer Buf = new StringBuffer();

                while ((cc = in.read()) > - 1) {
                    Buf.append((char) cc);
                }
                logger.log(Level.INFO,
                        "Data from the Zip Entry:\n" + Buf.toString());
                logger.log(Level.INFO, "-----------------------");

                /*
                 * Invoke getServerContext() method on each Server Context
                 * Provider.
                 */
                String[] srvCnxtProviders = Buf.toString().split("\n");

                for (int i = 0; i < srvCnxtProviders.length; i++) {
                    logger.log(Level.INFO,
                            "Server Context Provider: " + srvCnxtProviders[i]);

                    try {

                        /*
                         * Does the server context provider implement
                         * net.jini.export.ServerContext.Spi interface?
                         */
                        Class cl = Class.forName(srvCnxtProviders[i]);
                        logger.log(Level.INFO,
                                "Class of the Server Context Provider: " + cl);

                        if (!srvCnxtSpiClass.isAssignableFrom(cl)) {
                            logger.log(Level.INFO,
                                    "The Server Context Provider"
                                    + " doesn't implement"
                                    + " net.jini.export.ServerContext.Spi");
                            continue;
                        }
                        logger.log(Level.INFO,
                                "The Server Context Provider does implement"
                                + " net.jini.export.ServerContext.Spi");
                        ServerContext.Spi srvCnxtProvider = (ServerContext.Spi)
                                cl.newInstance();

                        /*
                         * Invoke this getServerContext() method and store
                         * returned context if it's non-null.
                         */
                        Collection cnxt = srvCnxtProvider.getServerContext();
                        logger.log(Level.INFO,
                                "Obtained Server Context: " + cnxt);

                        if (cnxt != null) {
                            contexts.add(cnxt);
                        }
                    } catch (Exception e) {
                        logger.log(Level.FINE,
                                "Exception while invoking getServerContext()"
                                + " on the Server Context Provider: " + e);
                        continue;
                    }
                }
            } else {
                logger.log(Level.INFO, "The resource isn't a JAR file");
            }
        }
        logger.log(Level.INFO, "============================================");
        Collection[] contextsArray = (Collection[]) contexts.toArray(new
                Collection[0]);
        logger.log(Level.INFO,
                "Non-null contexts returned by getServerContext() methods"
                + " (length=" + contextsArray.length + "):");

        for (int i = 0; i < contextsArray.length; i++) {
            logger.log(Level.INFO,
                    "contextsArray[" + i + "]: " + contextsArray[i]);
        }

        /*
         * Verify that at least 2 Server Context Provider exist whose
         * getServerContext() method returns non-null
         */
        if (contextsArray.length < 2) {
            throw new TestException("Only " + contextsArray.length
                    + " Server Context Providers exist whose"
                    + " getServerContext() method returns non-null");
        }

        /*
         * Verify that one of the non-null context is equal to
         * the expected result
         */
        boolean isTestable = false;
        int count = 0;

        for (int i = 0; i < contextsArray.length; i++) {
            if (expectedRes.equals(new HashSet(contextsArray[i]))) {
                isTestable = !isTestable;
                count++;
            }
        }

        if (!isTestable) {
            throw new TestException("There is " + count + " Server Context"
                    + " Providers whose getServerContext() method returns: "
                    + expectedRes);
        }
    }

    /**
     * This method performs all actions mentioned in class description.
     */
    public void run() throws Exception {

        /* Try to get server context collection */
        logger.log(Level.FINE,
                "+++++ invoking ServerContext.getServerContext()");
        Collection retCnxt = ServerContext.getServerContext();
        logger.log(Level.FINE, "No exception has been thrown");
        logger.log(Level.FINE,
                "expected context: " + expectedRes.toString());
        logger.log(Level.FINE, "returned context: " + retCnxt.toString());

        if (!expectedRes.equals(new HashSet(retCnxt))) {
            logger.log(Level.FINE,
                    "The returned Server Context isn't equal"
                    + " to the expected one");
            throw new TestException(
                    "" + " test failed");
        }

        return;
    }
}
