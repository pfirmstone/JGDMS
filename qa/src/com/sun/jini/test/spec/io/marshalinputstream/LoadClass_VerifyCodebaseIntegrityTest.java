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
package com.sun.jini.test.spec.io.marshalinputstream;

import java.util.logging.Level;

import com.sun.jini.qa.harness.QATestEnvironment;
import com.sun.jini.qa.harness.QAConfig;
import com.sun.jini.qa.harness.QAConfig;
import com.sun.jini.qa.harness.AdminManager;
import com.sun.jini.qa.harness.Test;

import net.jini.loader.ClassLoading;

import com.sun.jini.test.spec.io.util.FakeClassLoader;

import java.rmi.server.RMIClassLoader;
import java.util.logging.Level;
import java.lang.reflect.Proxy;

/**
 * <pre>
 * Purpose
 *   This test verifies the behavior of the ClassLoading.loadClass and
 *   ClassLoading.loadProxyClass static methods in the cases where
 *   Security.verifyCodebaseIntegrity calls do and do not throw an exception.
 *
 * Test Cases
 *   This test iterates over a 3-tuple.  Each 3-tuple denotes one test case
 *   and is defined by the cross-product of these variables:
 *      Class   loadClass
 *      boolean providesIntegrity
 *      String  codebase
 *   where loadClass is one of:
 *      String.class
 *      FakeArgument.class
 *      Proxy class for FakeInterface
 *   and providesIntegrity is one of:
 *      true
 *      false
 *   and codebase is one of:
 *      null
 *      actual codebase for FakeArgument jar
 *
 * Infrastructure
 *   This test requires the following infrastructure:
 *     1) FakeIntegrityVerifier
 *          -overrides the providesIntegrity method so it returns a value
 *           based on a system property setting
 *     2) FakeArgument
 *          -serializable object contained in a JAR file separate from the test
 *     3) FakeInterface
 *          -an interface which declares no methods
 *     4) FakeClassLoader
 *          -delegates to system class loader
 *
 * Actions
 *   For each test case the test performs the following steps:
 *     1) set a system property to providesIntegrity
 *     2) construct FakeClassLoader instances for defaultLoader
 *        and verifierLoader
 *     3) call ClassLoading.loadClass or ClassLoading.loadProxyClass
 *        (determined by type of loadClass) with these args:
 *        codebase,loadClass name,defaultLoader,true,verifierLoader
 *     4) if loadClass is not String.class
 *        and (providesIntegrity is false or codebase is null)
 *           assert ClassNotFoundException is thrown
 *        else
 *           assert loadClass equals the returned class
 * </pre>
 */
public class LoadClass_VerifyCodebaseIntegrityTest extends QATestEnvironment implements Test {

    QAConfig config;
    Object[][] cases;
    String interfaceName = "com.sun.jini.test.spec.io.util.FakeInterface";
    AdminManager manager;


    // inherit javadoc
    public Test construct(QAConfig sysConfig) throws Exception {
        this.config = (QAConfig) sysConfig;
        config.setDynamicParameter(
                "qaClassServer.port",
                config.getStringConfigVal("com.sun.jini.test.port", "8082"));
	manager = new AdminManager(sysConfig);
        manager.startService("testClassServer");

        String codebase = config.getStringConfigVal(
            "com.sun.jini.test.spec.io.util.fakeArgumentJar","Error");

        // loadClass values
        Class fakeArg = RMIClassLoader.loadClass(codebase,
            "com.sun.jini.test.spec.io.util.FakeArgument");
        Class proxy = Proxy.getProxyClass(
            RMIClassLoader.getClassLoader(codebase),
            new Class[] {RMIClassLoader.loadClass(codebase,interfaceName)});

        // providesIntegrity
        Boolean f = Boolean.FALSE;
        Boolean t = Boolean.TRUE;

        // test cases
        cases = new Object[][] {
            // loadClass, providesIntegrity, codebase, shouldThrowException
            {String.class, f, null,     f},
            {String.class, t, null,     f},
            {String.class, f, codebase, f},
            {String.class, t, codebase, f},
            {fakeArg,      f, null,     t},
            {fakeArg,      t, null,     t},
            {fakeArg,      f, codebase, t},
            {fakeArg,      t, codebase, f},
            {proxy,        f, null,     t},
            {proxy,        t, null,     t},
            {proxy,        f, codebase, t},
            {proxy,        t, codebase, f}
        };
        return this;
    }

    // inherit javadoc
    public void run() throws Exception {
        for (int i = 0; i < cases.length; i++) {
            logger.log(Level.FINE,"=================================");
            Class loadClass = (Class)cases[i][0];
            boolean providesIntegrity =
                ((Boolean)cases[i][1]).booleanValue();
            String codebase = (String)cases[i][2];
            boolean shouldThrowException =
                ((Boolean)cases[i][3]).booleanValue();
            logger.log(Level.FINE,"test case " + (i+1) + ": "
                + "loadClass:" + loadClass
                + ", providesIntegrity:" + providesIntegrity
                + ", codebase:" + codebase);
            logger.log(Level.FINE,"");

            // Verifier shouldn't be called (so throw an exception if 
            // it is) under the following conditions:
            boolean condition = (loadClass == String.class) ||
                                 codebase == null;
            System.setProperty(
                "com.sun.jini.test.spec.io.util.FakeIntegrityVerifier."
                + "throwException","" + condition);

            // set return value for verifier's providesIntegrity method
            System.setProperty(
                "com.sun.jini.test.spec.io.util.FakeIntegrityVerifier."
                + "providesIntegrity","" + providesIntegrity);

            FakeClassLoader verifierLoader = new FakeClassLoader();
            FakeClassLoader defaultLoader = new FakeClassLoader();

            // verify result

            if (Proxy.isProxyClass(loadClass)) {
                try {
                    Class result = ClassLoading.loadProxyClass(
                        codebase,new String[] {interfaceName},
                        defaultLoader,true,verifierLoader);
                    assertion(! shouldThrowException);
                    // can't .equals loadClass and result Proxy classes 
                    // since they are defined in different class loaders
                    assertion(Proxy.isProxyClass(result));
                    Class[] loadClasses = loadClass.getInterfaces();
                    Class[] resultClasses = result.getInterfaces();
                    assertion(resultClasses.length == loadClasses.length);
                    for (int j = 0; j < resultClasses.length; j++) {
                        assertion(resultClasses[j].getName().equals(
                            loadClasses[j].getName()));
                    }
                } catch (ClassNotFoundException cnfe) { 
                    assertion(shouldThrowException,cnfe.toString());
                }
            } else {
                try {
                    Class result = ClassLoading.loadClass(
                        codebase,loadClass.getName(),
                        defaultLoader,true,verifierLoader);
                    assertion(! shouldThrowException);
                    assertion(loadClass.equals(result));
                } catch (ClassNotFoundException cnfe) { 
                    assertion(shouldThrowException,cnfe.toString());
                }
            }
        }
    }

    // inherit javadoc
    public void tearDown() {
    }

}

