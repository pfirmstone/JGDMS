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
package org.apache.river.test.spec.security.security;

import java.util.logging.Level;

// java
import java.util.Collection;
import java.util.ArrayList;
import java.io.File;
import java.rmi.RemoteException;

// net.jini
import net.jini.security.Security;

// org.apache.river
import org.apache.river.qa.harness.TestException;
import org.apache.river.qa.harness.QATestEnvironment;
import org.apache.river.qa.harness.Test;
import org.apache.river.test.spec.security.util.Util;
import org.apache.river.test.spec.security.util.TestObject;
import org.apache.river.test.spec.security.util.BaseTrustVerifier;
import org.apache.river.test.spec.security.util.TrueTrustVerifier;
import org.apache.river.test.spec.security.util.FalseTrustVerifier;
import org.apache.river.test.spec.security.util.TrustVerifierThrowingSE;
import org.apache.river.test.spec.security.util.TrustVerifierThrowingRE;
import org.apache.river.test.spec.security.util.FakeClassLoader;


/**
 * <pre>
 * Purpose
 *   This test verifies the following:
 *     'verifyObjectTrustTest' static method of Security class verifies that the
 *     specified object can be trusted to correctly implement its contract,
 *     using verifiers from the specified class loader and using the specified
 *     collection of context objects as necessary. If a null class loader is
 *     specified, the context class loader of the current thread is used
 *     instead. A TrustVerifier.Context is created, containing an ordered list
 *     of trust verifiers (obtained as specified below) and the specified class
 *     loader and collection of context objects. The
 *     TrustVerifier.Context.isTrustedObject method of that context is then
 *     called with the specified object. If that call returns true, then this
 *     method returns normally. If that call throws a RemoteException or
 *     SecurityException exception, that exception is thrown by this method.
 *     If that call returns false, a SecurityException is thrown. The list of
 *     trust verifiers is obtained as follows. For each resource named
 *     META-INF/services/net.jini.security.TrustVerifier that is visible to the
 *     specified class loader, the contents of the resource are parsed
 *     as UTF-8 text to produce a list of class names. The resource must contain
 *     a list of fully qualified class names, one per line. Space and tab
 *     characters surrounding each name, as well as blank lines, are ignored.
 *     The comment character is '#'; all characters on each line
 *     starting with the first comment character are ignored.
 *     Each class name (that is not a duplicate of any previous class name)
 *     is loaded through the specified class loader, and the resulting class
 *     must be assignable to TrustVerifier and have a public no-argument
 *     constructor. The constructor is invoked to create a trust verifier
 *     instance. This method throws NullPointerException if a collection of
 *     context objects is null.
 *
 * Test Cases
 *   Case 1: actions described below
 *   Case 2: Set ClassLoader for current thread to FakeClassLoader1 with empty
 *           array of URLs. During the test construct each time
 *           FakeClassLoader2, do not set it as ClassLoader for current thread,
 *           and invoke 'verifyObjectTrustTest' with constructed
 *           FakeClassLoader2. In this case checks will be made that  tested
 *           trust verifiers where loaded through FakeClassLoader2 and not
 *           FakeClassLoader1.
 *
 * Infrastructure
 *   This test requires the following infrastructure:
 *     Resource1 - META-INF/services/net.jini.security.TrustVerifier resource
 *             containing TrueTrustVerifier and FalseTrustVerifier in this
 *             order
 *     Resource2 - META-INF/services/net.jini.security.TrustVerifier resource
 *             containing FalseTrustVerifier
 *     Resource3 - META-INF/services/net.jini.security.TrustVerifier resource
 *             containing FalseTrustVerifier and TrueTrustVerifier in this order
 *     Resource4 - META-INF/services/net.jini.security.TrustVerifier resource
 *             containing TrustVerifierThrowingSE and FalseTrustVerifier in this
 *             order
 *     Resource5 - META-INF/services/net.jini.security.TrustVerifier resource
 *             containing TrustVerifierThrowingRE and FalseTrustVerifier in this
 *             order
 *     Resource6 - META-INF/services/net.jini.security.TrustVerifier resource
 *             containing TrustVerifierThrowingSE and TrueTrustVerifier in this
 *             order
 *     Resource7 - META-INF/services/net.jini.security.TrustVerifier resource
 *             containing TrustVerifierThrowingRE and TrueTrustVerifier in this
 *             order
 *     TestObject - object which is not trusted by any trust verifier
 *             containing in jsk-resources.jar    
 *     TrueTrustVerifier - TrustVerifier whose 'isTrustedObject' method always
 *             returns true
 *     FalseTrustVerifier - TrustVerifier whose 'isTrustedObject' method always
 *             returns false
 *     TrustVerifierThrowingSE - TrustVerifier whose 'isTrustedObject' method
 *             always throws SecurityException
 *     TrustVerifierThrowingRE - TrustVerifier whose 'isTrustedObject' method
 *             always throws RemoteException
 *     FakeContext - fake context for for 'verifyObjectTrust' method
 *     FakeClassLoader - test class loader, which is actually a URLClassLoader
 *
 * Action
 *   For each test case the test performs the following steps:
 *     1) invoke 'Security.verifyObjectTrust' method with null context
 *     2) assert that NullPointerException will be thrown
 *     3) construct FakeClassLoader with Resource1 as a parameter and set it
 *        as ClassLoader for the current thread
 *     4) invoke 'Security.verifyObjectTrust(TestObject, null, FakeContext)'
 *     5) assert that TrueTrustVerifier will be loaded through FakeClassLoader
 *     6) assert that FalseTrustVerifier will be loaded through FakeClassLoader
 *     7) assert that 'isTrustedObject' method of TrueTrustVerifier will be
 *        invoked with the same TestObject as in 'verifyObjectTrust' method
 *     8) assert that 'isTrustedObject' method of FalseTrustVerifier will not
 *        be invoked
 *     9) assert that method will return normally
 *     10) construct FakeClassLoader with Resource2 as a parameter and set it
 *         as ClassLoader for the current thread
 *     11) invoke 'Security.verifyObjectTrust(TestObject, null, FakeContext)'
 *     12) assert that FalseTrustVerifier will be loaded through FakeClassLoader
 *     13) assert that 'isTrustedObject' method of FalseTrustVerifier will be
 *         invoked with the same TestObject as in 'verifyObjectTrust' method
 *     14) assert that method will throw SecurityException
 *     15) construct FakeClassLoader with Resource3 as a parameter and set it
 *         as ClassLoader for the current thread
 *     16) invoke 'Security.verifyObjectTrust(TestObject, null, FakeContext)'
 *     17) assert that FalseTrustVerifier will be loaded through FakeClassLoader
 *     18) assert that TrueTrustVerifier will be loaded through FakeClassLoader
 *     19) assert that 'isTrustedObject' method of FalseTrustVerifier will be
 *         invoked with the same TestObject as in 'verifyObjectTrust' method
 *     20) assert that 'isTrustedObject' method of TrueTrustVerifier will be
 *         invoked with the same TestObject as in 'verifyObjectTrust' method
 *     21) assert that method will return normally
 *     22) construct FakeClassLoader with Resource4 as a parameter and set it
 *         as ClassLoader for the current thread
 *     23) invoke 'Security.verifyObjectTrust(TestObject, null, FakeContext)'
 *     24) assert that TrustVerifierThrowingSE will be loaded through
 *         FakeClassLoader
 *     25) assert that FalseTrustVerifier will be loaded through FakeClassLoader
 *     26) assert that 'isTrustedObject' method of TrustVerifierThrowingSE will
 *         be invoked with the same TestObject as in 'verifyObjectTrust' method
 *     27) assert that 'isTrustedObject' method of FalseTrustVerifier will be
 *         invoked with the same TestObject as in 'verifyObjectTrust' method
 *     28) assert that method will throw SecurityException
 *     29) construct FakeClassLoader with Resource5 as a parameter and set it
 *         as ClassLoader for the current thread
 *     30) invoke 'Security.verifyObjectTrust(TestObject, null, FakeContext)'
 *     31) assert that TrustVerifierThrowingRE will be loaded through
 *         FakeClassLoader
 *     32) assert that FalseTrustVerifier will be loaded through FakeClassLoader
 *     33) assert that 'isTrustedObject' method of TrustVerifierThrowingRE will
 *         be invoked with the same TestObject as in 'verifyObjectTrust' method
 *     34) assert that 'isTrustedObject' method of FalseTrustVerifier will be
 *         invoked with the same TestObject as in 'verifyObjectTrust' method
 *     35) assert that method will throw RemoteException
 *     36) construct FakeClassLoader with Resource6 as a parameter and set it
 *         as ClassLoader for the current thread
 *     37) invoke 'Security.verifyObjectTrust(TestObject, null, FakeContext)'
 *     38) assert that TrustVerifierThrowingSE will be loaded through
 *         FakeClassLoader
 *     39) assert that TrueTrustVerifier will be loaded through FakeClassLoader
 *     40) assert that 'isTrustedObject' method of TrustVerifierThrowingSE will
 *         be invoked with the same TestObject as in 'verifyObjectTrust' method
 *     41) assert that 'isTrustedObject' method of TrueTrustVerifier will be
 *         invoked with the same TestObject as in 'verifyObjectTrust' method
 *     41) assert that method will return normally
 *     42) construct FakeClassLoader with Resource7 as a parameter and set it
 *         as ClassLoader for the current thread
 *     43) invoke 'Security.verifyObjectTrust(TestObject, null, FakeContext)'
 *     44) assert that TrustVerifierThrowingRE will be loaded through
 *         FakeClassLoader
 *     45) assert that TrueTrustVerifier will be loaded through FakeClassLoader
 *     46) assert that 'isTrustedObject' method of TrustVerifierThrowingRE will
 *         be invoked with the same TestObject as in 'verifyObjectTrust' method
 *     47) assert that 'isTrustedObject' method of TrueTrustVerifier will be
 *         invoked with the same TestObject as in 'verifyObjectTrust' method
 *     48) assert that method will return normally
 * </pre>
 */
public class VerifyObjectTrustTest extends QATestEnvironment implements Test {

    /** Resource name method */
    protected static String resName =
            "META-INF/services/net.jini.security.TrustVerifier";

    /**
     * Array of classes whose 'isTrustedObject' methods are expected to be
     * called.
     */
    protected Class[] expCls = new Class[0];

    /**
     * This method performs all actions mentioned in class description.
     *
     */
    public void run() throws Exception {
        File jarFile = null;
        Object testObj = new TestObject();
        Collection testCtx = new ArrayList();
        testCtx.add(new Object());
        boolean[] useNullLoader = new boolean[] { true, false };
        Class[][] clNames = new Class[][] {
            new Class[] {
                TrueTrustVerifier.class,
                FalseTrustVerifier.class },
            new Class[] { FalseTrustVerifier.class },
            new Class[] {
                FalseTrustVerifier.class,
                TrueTrustVerifier.class },
            new Class[] {
                TrustVerifierThrowingSE.class,
                FalseTrustVerifier.class },
            new Class[] {
                TrustVerifierThrowingRE.class,
                FalseTrustVerifier.class },
            new Class[] {
                TrustVerifierThrowingSE.class,
                TrueTrustVerifier.class },
            new Class[] {
                TrustVerifierThrowingRE.class,
                TrueTrustVerifier.class } };
        Class expRes;
        ClassLoader testCl;

        for (int i = 0; i < useNullLoader.length; ++i) {

            // check NullPointerException
            logger.fine("Trying null context.");
            jarFile = Util.createResourceJar(resName,
                    new Class[] { Class.class });

            if (useNullLoader[i]) {
                testCl = null;
            } else {
                testCl = new FakeClassLoader(jarFile.toURI().toURL());
            }

            try {
                callVerifyObjectTrust(testObj, testCl, null);

                // FAIL
                throw new TestException(
                        "Method did not throw any exception while "
                        + "NullPointerException was expected.");
            } catch (NullPointerException npe) {
                // PASS
                logger.fine("NullPointerException was thrown as expected.");
            } finally {
                jarFile.delete();
            }

            for (int j = 0; j < clNames.length; ++j) {
                BaseTrustVerifier.initLists();
                logger.fine("========== Iteration #" + (j + 1)
                        + " ==========");
                logger.fine("Test trust verifiers are: "
                        + Util.arrayToString(clNames[j]));
                jarFile = Util.createResourceJar(resName, clNames[j]);
                expRes = getExpRes(clNames[j]);

                if (useNullLoader[i]) {
                    testCl = null;
                } else {
                    testCl = new FakeClassLoader(jarFile.toURI().toURL());
                }
                Thread.currentThread().setContextClassLoader(
                        new FakeClassLoader(jarFile.toURI().toURL()));

                try {
                    callVerifyObjectTrust(testObj, testCl, testCtx);

                    if (expRes != null) {
                        // FAIL
                        throw new TestException(
                                "Method returned normally while " + expRes
                                + " exception was expected to be thrown.");
                    }

                    // PASS
                    logger.fine("Method returned normally as expected.");
                } catch (Exception e) {
                    if (expRes == null) {
                        // FAIL
                        throw new TestException(
                                "Method throws " + e + " exception while "
                                + "normal return was expected.");
                    } else if (expRes != e.getClass()) {
                        // FAIL
                        throw new TestException(
                                "Method throws " + e + " exception while "
                                + expRes + " was expected.");
                    }

                    // PASS
                    logger.fine("Method threw " + e + " as expected.");
                } finally {
                    jarFile.delete();
                }

                /*
                 * check that all classes where loaded through the right
                 * classloader
                 */
                Class[] loadedCls = null;

                if (testCl == null) {
                    loadedCls = ((FakeClassLoader)
                            Thread.currentThread().getContextClassLoader())
                                    .getClasses();
                } else {
                    loadedCls = ((FakeClassLoader) testCl).getClasses();
                }
                Class[] notLoadedCls = Util.containsClasses(loadedCls,
                        clNames[j]);

                if (notLoadedCls != null) {
                    // FAIL
                    throw new TestException(
                            "The following classes was not loaded through "
                            + "expected class loader: "
                            + Util.arrayToString(notLoadedCls));
                }

                // PASS
                logger.fine("All requested classes were loaded through "
                        + "expected class loader.");

                // check that passed parameters where correct
                Class[] classes = BaseTrustVerifier.getClasses();
                Object[] objs = BaseTrustVerifier.getObjs();
                Object[] ctxs = BaseTrustVerifier.getCtxs();

                if (classes.length != expCls.length) {
                    // FAIL
                    throw new TestException(
                            "Expected set of classes whose "
                            + "'isTrustedObject' method had to be called "
                            + "is: " + Util.arrayToString(expCls)
                            + ", while actual set is: "
                            + Util.arrayToString(classes));
                }

                for (int k = 0; k < classes.length; ++k) {
                    if (classes[k] != expCls[k]) {
                        // FAIL
                        throw new TestException(
                                "Expected set of classes whose "
                                + "'isTrustedObject' method had to be "
                                + "called is: " + Util.arrayToString(expCls)
                                + ", while actual set is: "
                                + Util.arrayToString(classes));
                    }

                    if (objs[k] != testObj && ctxs[k] != testCtx) {
                        // FAIL
                        throw new TestException(
                                "Class " + classes[k] + " got [" + objs[k]
                                + ", " + ctxs[k] + "] parameters while ["
                                + testObj + ", " + testCtx
                                + "] was expected.");
                    }
                }

                // PASS
                logger.fine("All classes got expected parameters.");
            }
        }
    }

    /**
     * Invokes 'Security.verifyObjectTrust' method with given arguments.
     * Rethrows any exception thrown by 'verifyObjectTrust' method.
     *
     * @param obj Object for 'verifyObjectTrust' method
     * @param loader ClassLoader for 'verifyObjectTrust' method
     * @param context Collection for 'verifyObjectTrust' method
     * @throws java.rmi.RemoteException 
     */
    protected void callVerifyObjectTrust(Object obj, ClassLoader loader,
            Collection context) throws RemoteException {
        logger.fine("Call 'Security.verifyObjectTrust(" + obj + ", " + loader
                + ", " + context + ")'.");
        Security.verifyObjectTrust(obj, loader, context);
    }

    /**
     * Return Class representing expected result - if non-null, that means
     * that an exception must be thrown of returned type. Fills array of
     * classes whose 'isTrustedObject' methods are expected to be called.
     *
     * @param clNames array of classes for which evaluate the result
     */
    protected Class getExpRes(Class[] clNames) {
        Class res = null;
        ArrayList clList = new ArrayList();

        for (int i = 0; i < clNames.length; ++i) {
            clList.add(clNames[i]);

            if (clNames[i] == TrueTrustVerifier.class) {
                expCls = (Class []) clList.toArray(new Class[clList.size()]);
                return null;
            } else if (clNames[i] == TrustVerifierThrowingRE.class) {
                res = RemoteException.class;
            } else if (clNames[i] == TrustVerifierThrowingSE.class) {
                res = SecurityException.class;
            }
        }
        expCls = (Class []) clList.toArray(new Class[clList.size()]);
        return (res != null) ? res : SecurityException.class;
    }
}
