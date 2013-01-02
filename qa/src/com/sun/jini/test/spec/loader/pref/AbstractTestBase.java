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
package com.sun.jini.test.spec.loader.pref;

import java.util.logging.Level;

// com.sun.jini.qa.harness
import com.sun.jini.qa.harness.QAConfig;
import com.sun.jini.qa.harness.Test;
// com.sun.jini.qa
import com.sun.jini.qa.harness.QATestEnvironment;
import com.sun.jini.qa.harness.QAConfig;
import com.sun.jini.qa.harness.Test;
import com.sun.jini.qa.harness.TestException;

// java.net
import java.net.URL;
import java.net.InetAddress;

// java.util.logging
import java.util.logging.Logger;
import java.util.logging.Level;

// java.util.Properties
import java.util.Properties;

// davis packages
import net.jini.loader.ClassAnnotation;
import net.jini.loader.pref.PreferredClassLoader;

// instrumented preferred class loader
import com.sun.jini.test.spec.loader.util.Item;
import com.sun.jini.test.spec.loader.util.Util;
import com.sun.jini.test.spec.loader.util.QATestPreferredClassLoader;
import com.sun.jini.test.spec.loader.util.QATestPreferredClassProvider;


/**
 * This class is base class for all com.sun.jini.test.spec.loader.pref tests.
 * This class sets up the testing environment and
 * has some helper methods.
 */
public abstract class AbstractTestBase extends QATestEnvironment implements Test {

    /** The name of java.rmi.server.codebase system property */
    protected static final String RMICODEBASE = "java.rmi.server.codebase";

    /** Default invalid codebase */
    protected static final String INVCODEBASE = "Invalid_codebase";

    /** The QAConfig object */
    protected volatile QAConfig config;

    /**
     * The instrumented preferred class loader
     * {@link QATestPreferredClassLoader}
     */
    protected volatile QATestPreferredClassLoader loader;

    /**
     *  Flag to define whether http or file url will be used
     *  for download preferred classes and resources
     */
    protected volatile boolean isHttp;

    /**
     * Http port to download preferred classes and resources via
     * com.sun.jini.qa.port
     */
    protected volatile int port;

    /** Auxiliary http port to download preferred classes and resources */
    protected volatile int auxPort;

    /**
     * Flag to define boolean requireDlPerm argument that will be passed to
     * {@link QATestPreferredClassLoader} constructor.
     */
    protected volatile boolean dlPerm;

    /**
     * String to define the exportAnnotation string that will be passed to
     * {@link QATestPreferredClassLoader} constructor.
     */
    protected volatile String annotation;

    /**
     *  Flag to indicate that SecurityException should be thrown.
     */
    protected volatile boolean expectSecurityException;

    /**
     *  String codebase for {@link QATestPreferredClassProvider}.
     */
    protected volatile String codebaseParam;

    /**
     * Sets up the testing environment.
     *
     * @param config QAConfig from the runner for construct.
     */
    public Test construct(QAConfig config) throws Exception {
        this.config = config;

        // Set shared vm mode to be disabled in all cases
        config.setDynamicParameter("com.sun.jini.qa.harness.shared", "false");

        // Obtain isHttp boolean parameter and if false then
        // reset com.sun.jini.qa.harness.testClassServer property to an empty
        // string to inhibit startup of test class server
        isHttp = config.getBooleanConfigVal("loader.isHttp", true);

        if (!isHttp) {
            config.setDynamicParameter("com.sun.jini.qa.harness.testClassServer", "");
        }

        // mandatory call to parent
        super.construct(config);


        /*
         * set up testing environment
         *
         * isHttp    - if true will use http based url to download
         *             classes/resources, otherwize will use file based url.
         * port      - http port for the http based url
         * dlPerm    - requireDlPerm parameter passing to PreferredClassLoader
         *             constructor.
         * annotation - string annotaiton parameter passing to
         *              PreferredClassLoader constructor.
         * annotator - if true then will use classAnnotator object passing
         *             to PreferredClassLoader constructor.
         *
         * classAnnotationName - class name of ClassAnnotation object
         *                       passing to PreferredClassLoader constructor.
         *
         * expectSecurityException  - if true then expect SecurityException
         *
         */
        port = config.getIntConfigVal("com.sun.jini.test.port", 8082);
        auxPort = config.getIntConfigVal("loader.httpPort", 8087);
        dlPerm = config.getBooleanConfigVal("loader.requireDlPerm", false);
        annotation = config.getStringConfigVal("loader.exportAnnotation", null);
        expectSecurityException =
                config.getBooleanConfigVal("loader.expectSecurityException",
                false);
        codebaseParam = config.getStringConfigVal("loader.codebase", null);
        return this;
    }

    /**
     * Create {@link QATestPreferredClassLoader} to support loader.pref's
     * testing.
     * <br>
     * Obtain all parameters passing to PreferredClassLoader constructor,
     * then create {@link QATestPreferredClassLoader}.
     * <br>
     * Parameters may be defined in the property file or may be defined
     * by the individual test.
     *
     * @param jar name of jar file to download preferred classes/resources.
     *
     * @param jar2 (optional) name of (second) jar file to download preferred
     *        classes/resources
     *
     * @throws TestException if could not create
     *         {@link QATestPreferredClassLoader}
     *
     */
    protected void createLoader(String jar, String jar2)
            throws TestException {

        /*
         * Obtain array of URL according construct patameters.
         */
        URL[] urls = Util.getUrls(isHttp, jar, jar2, config, port);
        ClassLoader prnt = Util.systemClassLoader();

        /*
         * Create instrumented PreferredClassLoader passing
         * appropriate parameters.
         *
         */
        loader = new QATestPreferredClassLoader(urls, prnt, annotation, dlPerm);
        String msg = "new QATestPreferredClassLoader(";

        for (int i = 0; i < urls.length; i++) {
            msg += urls[i].toExternalForm() + ", ";
        }
        msg += "" + annotation + ", " + dlPerm + ")";
        logger.log(Level.FINE, msg);
    }

    /**
     * Create {@link QATestPreferredClassLoader} with an url to a single jar
     * file.
     * See {@link #createLoader(String jar, String jar2)}
     *
     * @throws TestException if could not create
     *         {@link QATestPreferredClassLoader}
     */
    protected void createLoader(String jar) throws TestException {
        createLoader(jar, null);
    }

    /**
     * Returns expected string annotation for class loader
     *
     * @throws TestException if could not create
     *         {@link QATestPreferredClassLoader} object
     */
    protected String expectedAnnotationString() throws TestException {
        String expectedAnnotation = null;

        if (loader == null) {
            throw new TestException("Instrumented class loader is null");
        }

        if (annotation != null) {
            // export annotation string was passed to
            // QATestPreferredClassLoader constructor.
            expectedAnnotation = annotation;
        } else {
            // null instead of annotation string was passed to
            // QATestPreferredClassLoader constructor.
            expectedAnnotation = loader.urlsToPath();
        }
        return expectedAnnotation;
    }

    /**
     * Obtain malformed codebase from {@link #codebaseParam} that should
     * be set to malformed codebase or to null.
     * If {@link #codebaseParam} is null then reset java.rmi.server.codebase
     * to invalid codebase.
     *
     * @return malformed codebase or <code>null</code>
     *
     * @throws TestException if could not create
     *         {@link QATestPreferredClassLoader} object
     */
    protected String obtainMalformedCodebase() throws TestException {
        if (codebaseParam == null) {
            // Reset java.rmi.server.codebase to invalid codebase.
            Properties props = System.getProperties();
            props.put(RMICODEBASE, INVCODEBASE);
            System.setProperties(props);
            String prop = System.getProperty(RMICODEBASE);
            logger.log(Level.FINEST, "reset " + RMICODEBASE + ": " + prop);
        }
        return codebaseParam;
    }
}
