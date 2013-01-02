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
package com.sun.jini.test.spec.security.security;

import java.util.logging.Level;

// jav
import java.util.ArrayList;
import java.io.File;
import java.net.URL;
import java.net.MalformedURLException;

// net.jini
import net.jini.security.Security;

// com.sun.jini
import com.sun.jini.qa.harness.TestException;
import com.sun.jini.qa.harness.QATestEnvironment;
import com.sun.jini.qa.harness.Test;
import com.sun.jini.test.spec.security.util.Util;
import com.sun.jini.test.spec.security.util.BaseIntegrityVerifier;
import com.sun.jini.test.spec.security.util.TrueIntegrityVerifier;
import com.sun.jini.test.spec.security.util.FalseIntegrityVerifier;
import com.sun.jini.test.spec.security.util.FakeClassLoader;


/**
 * <pre>
 * Purpose
 *   This test verifies the following:
 *     'verifyCodebaseIntegrity' static method of Security class verifies that
 *     the URLs in the specified codebase all provide content integrity, using
 *     verifiers from the specified class loader. If a null class loader is
 *     specified, the context class loader of the current thread is used
 *     instead. An ordered list of integrity verifiers is obtained as specified
 *     below. For each URL (if any) in the specified codebase,
 *     IntegrityVerifier.providesIntegrity method of each verifier is called (in
 *     order) with the URL. If any verifier call returns true, the URL is
 *     verified (and no further verifiers are called with that URL). If all of
 *     the verifier calls return false for a URL, this method throws a
 *     SecurityException. If all of the URLs are verified, this method returns
 *     normally. The list of integrity verifiers is obtained as follows. For
 *     each resource named META-INF/services/net.jini.security.IntegrityVerifier
 *     that is visible to the specified class loader, the contents of the
 *     resource are parsed as UTF-8 text to produce a list of class names.
 *     The resource must contain a list of fully qualified class names, one per
 *     line. Space and tab characters surrounding each name, as well as blank
 *     lines, are ignored.  The comment character is '#'; all characters on each
 *     line starting with the first comment character are ignored. Each class
 *     name (that is not a duplicate of any previous class name) is loaded
 *     through the specified class loader, and the resulting class must be
 *     assignable to IntegrityVerifier and have a public no-argument
 *     constructor. The constructor is invoked to create an integrity verifier
 *     instance. This method throws MalformedURLException if the specified
 *     codebase contains an invalid URL.
 *
 * Test Cases
 *   Case 1: actions described below
 *   Case 2: Set ClassLoader for current thread to FakeClassLoader1 with empty
 *           array of URLs. During the test construct each time
 *           FakeClassLoader2, do not set it as ClassLoader for current thread,
 *           and invoke 'verifyCodebaseIntegrity' with constructed
 *           FakeClassLoader2. In this case checks will be made that tested
 *           integrity verifiers where loaded through FakeClassLoader2 and not
 *           FakeClassLoader1.
 *   Case 3: Case1 for MultiURL
 *   Case 4: Case2 for MultiURL
 *
 * Infrastructure
 *   This test requires the following infrastructure:
 *     Resource1 - META-INF/services/net.jini.security.IntegrityVerifier
 *             resource containing TrueIntegrityVerifier and
 *             FalseIntegrityVerifier in this order
 *     Resource2 - META-INF/services/net.jini.security.IntegrityVerifier
 *             resource containing FalseIntegrityVerifier
 *     Resource3 - META-INF/services/net.jini.security.IntegrityVerifier
 *             resource containing FalseIntegrityVerifier and
 *             TrueIntegrityVerifier in this order
 *     TestURL - URL string for which 'providesIntegrity' method of any
 *             integrity verifier containing in jsk-resources.jar will return
 *             false
 *     MultiURL - String containing several URL strings
 *     WrongURL - wrong URL string
 *     TrueIntegrityVerifier - TrustVerifier whose 'providesIntegrity' method
 *             always returns true
 *     FalseIntegrityVerifier - TrustVerifier whose 'providesIntegrity' method
 *             always returns false
 *     FakeClassLoader - test class loader, which is actually a URLClassLoader
 *
 * Action
 *   For each test case the test performs the following steps:
 *     1) invoke 'Security.verifyCodebaseIntegrity' method with WrongURL string
 *     2) assert that MalformedURLException will be thrown
 *     3) construct FakeClassLoader with Resource1 as a parameter and set it
 *        as ClassLoader for the current thread
 *     4) invoke 'Security.verifyCodebaseIntegrity(TestURL, null)'
 *     5) assert that TrueIntegrityVerifier will be loaded through
 *        FakeClassLoader
 *     6) assert that FalseIntegrityVerifier will be loaded through
 *        FakeClassLoader
 *     7) assert that 'providesIntegrity' method of TrueIntegrityVerifier will
 *        be invoked
 *     8) assert that 'providesIntegrity' method of FalseIntegrityVerifier will
 *        not be invoked
 *     9) assert that method will return normally
 *     10) construct FakeClassLoader with Resource2 as a parameter and set it
 *         as ClassLoader for the current thread
 *     11) invoke 'Security.verifyCodebaseIntegrity(TestURL, null)'
 *     12) assert that FalseIntegrityVerifier will be loaded through
 *         FakeClassLoader
 *     13) assert that 'providesIntegrity' method of FalseIntegrityVerifier will
 *         be invoked
 *     14) assert that method will throw SecurityException
 *     15) construct FakeClassLoader with Resource3 as a parameter and set it
 *         as ClassLoader for the current thread
 *     16) invoke 'Security.verifyCodebaseIntegrity(TestURL, null)'
 *     17) assert that FalseIntegrityVerifier will be loaded through
 *         FakeClassLoader
 *     18) assert that TrueIntegrityVerifier will be loaded through
 *         FakeClassLoader
 *     19) assert that 'providesIntegrity' method of FalseIntegrityVerifier
 *         will be invoked
 *     20) assert that 'providesIntegrity' method of TrueIntegrityVerifier
 *         will be invoked
 *     21) assert that method will return normally
 * </pre>
 */
public class VerifyCodebaseIntegrityTest extends QATestEnvironment implements Test {

    /** Resource name method */
    protected static String resName =
            "META-INF/services/net.jini.security.IntegrityVerifier";

    /**
     * Array of classes whose 'providesIntegrity' methods are expected to be
     * called.
     */
    protected Class[] expCls = new Class[0];

    /**
     * Array of urls which are expected to be parameters for 'providesIntegrity'
     * methods.
     */
    protected URL[] expUrls = new URL[0];

    /** Expected result of iteration. */
    protected Class expRes;

    /**
     * This method performs all actions mentioned in class description.
     *
     */
    public void run() throws Exception {
        File jarFile = null;
        String[] wrongUrls = new String[] {
            "?:", "file:/fake.jar ?: file:/fake1.java" };
        String[] testUrls = new String[] {
            "http://fakehost:8080/bla-bla.java",
            "http://fh/bla1.java http://fh/bla2.java http://fh/bla3.java" };
        boolean[] useNullLoader = new boolean[] { true, false };
        Class[][] clNames = new Class[][] {
            new Class[] {
                TrueIntegrityVerifier.class,
                FalseIntegrityVerifier.class },
            new Class[] { FalseIntegrityVerifier.class },
            new Class[] {
                FalseIntegrityVerifier.class,
                TrueIntegrityVerifier.class } };
        ClassLoader testCl;

        for (int i = 0; i < useNullLoader.length; ++i) {
            logger.fine("=========== Check wrong URLs ===========");

            for (int j = 0; j < wrongUrls.length; ++j) {
                jarFile = Util.createResourceJar(resName,
                        new Class[] { Class.class });

                if (useNullLoader[i]) {
                    testCl = null;
                } else {
                    testCl = new FakeClassLoader(jarFile.toURI().toURL());
                }

                try {
                    callVerifyCodebaseIntegrity(wrongUrls[j], testCl);

                    // FAIL
                    throw new TestException(
                            "Method did not throw any exception while "
                            + "MalformedURLException was expected.");
                } catch (MalformedURLException mue) {
                    // PASS
                    logger.fine("MalformedURLException was thrown "
                            + "as expected.");
                } finally {
                    jarFile.delete();
                }
            }
            logger.fine("=========== Check correct URLs ===========");

            for (int j = 0; j < clNames.length; ++j) {
                logger.fine("========== Iteration #" + (j + 1)
                        + " ==========");
                logger.fine("Test integrity verifiers are: "
                        + Util.arrayToString(clNames[j]));

                for (int k = 0; k < testUrls.length; ++k) {
                    BaseIntegrityVerifier.initLists();
                    jarFile = Util.createResourceJar(resName, clNames[j]);
                    expRes = getExpRes(clNames[j], testUrls[k]);

                    if (useNullLoader[i]) {
                        testCl = null;
                    } else {
                        testCl = new FakeClassLoader(jarFile.toURI().toURL());
                    }
                    Thread.currentThread().setContextClassLoader(
                            new FakeClassLoader(jarFile.toURI().toURL()));

                    try {
                        callVerifyCodebaseIntegrity(testUrls[k], testCl);

                        if (expRes != null) {
                            // FAIL
                            throw new TestException(
                                    "Method returned normally while "
                                    + "SecurityException was expected "
                                    + "to be thrown.");
                        }

                        // PASS
                        logger.fine("Method returned normally "
                                + "as expected.");
                    } catch (SecurityException se) {
                        if (expRes == null) {
                            // FAIL
                            throw new TestException(
                                    "Method throws " + se
                                    + " exception while "
                                    + "normal return was expected.");
                        }

                        // PASS
                        logger.fine("Method threw " + se + " as expected.");
                    } finally {
                        jarFile.delete();
                    }

                    /*
                     * check that classes where loaded through the right
                     * classloader
                     */
                    Class[] loadedCls = null;

                    if (testCl == null) {
                        loadedCls = ((FakeClassLoader)
                                Thread.currentThread()
                                    .getContextClassLoader()).getClasses();
                    } else {
                        loadedCls = ((FakeClassLoader) testCl).getClasses();
                    }
                    Class[] notLoadedCls = Util.containsClasses(
                            loadedCls, clNames[j]);

                    if (notLoadedCls != null) {
                        // FAIL
                        throw new TestException(
                                "The following classes was not loaded "
                                + " through expected class loader: "
                                + Util.arrayToString(notLoadedCls));
                    }

                    // PASS
                    logger.fine("All requested classes were loaded through "
                                + "expected class loader.");

                    // check that passed parameters where correct
                    Class[] classes = BaseIntegrityVerifier.getClasses();
                    URL[] actUrls = BaseIntegrityVerifier.getUrls();
                    String resStr = checkURLs(actUrls, classes);

                    if (resStr != null) {
                        // FAIL
                        throw new TestException(resStr);
                    }

                    // PASS
                    logger.fine("All classes got expected urls.");
                }
            }
        }
    }

    /**
     * Invokes 'Security.verifyCodebaseIntegrity' method with given argument.
     * Rethrows any exception thrown by 'verifyCodebaseIntegrity' method.
     *
     * @param codebase space-separated list of URLs for
     *        'verifyCodebaseIntegrity' method
     * @param loader ClassLoader for 'verifyCodebaseIntegrity' method
     */
    protected void callVerifyCodebaseIntegrity(String codebase,
            ClassLoader loader) throws MalformedURLException {
        logger.fine("Call 'Security.verifyCodebaseIntegrity([" + codebase
                + "], " + loader + ")'.");
        Security.verifyCodebaseIntegrity(codebase, loader);
    }

    /**
     * Return Class representing expected result - if non-null, that means
     * that SecurityException must be thrown. Fills array of
     * classes whose 'providesIntegrity' methods are expected to be called.
     *
     * @param clNames array of classes for which evaluate the result
     * @param str URLs string for which get expected results
     * @throws MalformedURLException if any of the URLs are invalid
     */
    protected Class getExpRes(Class[] clNames, String str)
            throws MalformedURLException {
        ArrayList clList = new ArrayList();
        expUrls = Util.strToUrls(str);

        for (int i = 0; i < clNames.length; ++i) {
            clList.add(clNames[i]);

            if (clNames[i] == TrueIntegrityVerifier.class) {
                expCls = (Class []) clList.toArray(new Class[clList.size()]);
                return null;
            }
        }
        expCls = (Class []) clList.toArray(new Class[clList.size()]);
        return SecurityException.class;
    }

    /**
     * Checks expected set of URLs with actual one.
     * We need to check that all expected classes got all expected URLs.
     *
     * @param actUrls actual set of URLs
     * @param actCls actual set of classes
     * @return null if sets are equal or non-null string indicating error
     */
    protected String checkURLs(URL[] actUrls, Class[] actCls) {
        if ((actUrls.length == 0 && expUrls.length != 0)
                && (actUrls.length != 0 && expUrls.length == 0)) {
            // FAIL
            return "Actual set of URLs is: [" + Util.arrayToString(actUrls)
                    + "] while [" + Util.arrayToString(expUrls)
                    + "] was expected.";
        }

        if (expRes != null) {
            /*
             * We just need to check that at least one of expected urls was
             * passed as a parameter.
             */
            for (int i = 0; i < expUrls.length; ++i) {
                if (expUrls[i].equals(actUrls[0])) {
                    Class[] cls = Util.containsClasses(actCls, expCls);

                    if (cls != null) {
                        // FAIL
                        return "'providesIntegrity' methods of the following "
                                + "classes were not called with " + expUrls[i]
                                + " as a parameter: " + Util.arrayToString(cls);
                    }

                    // PASS
                    return null;
                }
            }

            // FAIL
            return "No 'providesIntegrity' methods where called with one of "
                    + Util.arrayToString(expUrls) + " URLs.";
        }

        for (int i = 0; i < expUrls.length; ++i) {
            ArrayList clList = new ArrayList();

            // get list of classes which got expUrls[i] as a parameter
            for (int j = 0; j < actUrls.length; ++j) {
                if (expUrls[i].equals(actUrls[j])) {
                    clList.add(actCls[j]);
                }
            }

            // check what classes did not get expected url
            Class[] cls = Util.containsClasses((Class []) clList.toArray(
                    new Class[clList.size()]), expCls);

            if (cls != null) {
                // FAIL
                return "'providesIntegrity' methods of the following "
                        + "classes were not called with " + expUrls[i]
                        + " as a parameter: " + Util.arrayToString(cls);
            }
        }

        // PASS
        return null;
    }
}
