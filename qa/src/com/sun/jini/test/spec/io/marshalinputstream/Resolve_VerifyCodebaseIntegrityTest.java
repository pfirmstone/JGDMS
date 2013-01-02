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
import com.sun.jini.qa.harness.AdminManager;
import com.sun.jini.qa.harness.Test;

import com.sun.jini.test.spec.io.util.FakeMarshalOutputStream;
import com.sun.jini.test.spec.io.util.FakeObjectEndpoint;
import com.sun.jini.test.spec.io.util.FakeMethodConstraints;
import com.sun.jini.test.spec.io.util.FakeClassLoader;

import net.jini.io.MarshalOutputStream;
import net.jini.io.MarshalInputStream;
import net.jini.jeri.BasicInvocationHandler;

import java.security.Permission;
import java.rmi.server.RMIClassLoader;
import java.io.ByteArrayOutputStream;
import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.logging.Level;
import java.lang.reflect.Proxy;

/**
 * <pre>
 * Purpose
 *   This test verifies the behavior of the MarshalInputStream.resolveClass and
 *   MarshalInputStream.resolveProxyClass static methods in the cases where
 *   Security.verifyCodebaseIntegrity calls do and do not throw an exception.
 *
 * Test Cases
 *   This test iterates over a 3-tuple.  Each 3-tuple denotes one test case
 *   and is defined by the cross-product of these variables:
 *      Object  transferObject
 *      boolean providesIntegrity
 *      String  writeAnnotationReturnVal
 *   transferObject is one of:
 *      a String instance
 *      a FakeArgument instance created by call to RMIClassLoader.loadClass
 *      a dynamic proxy for FakeInterface
 *   providesIntegrity is one of:
 *      true
 *      false
 *   and writeAnnotationReturnVal is one of:
 *      null
 *      actual codebase for FakeArgument jar
 *
 * Infrastructure
 *   This test requires the following infrastructure:
 *     1) FakeIntegrityVerifier
 *          -overrides the providesIntegrity method so it returns a value
 *           based on a system property setting
 *     2) FakeMarshalOutputStream
 *          -overrides the writeAnnotation method so it writes the one passed in
 *           during construction
 *     3) FakeArgument
 *          -serializable object contained in a JAR file separate from the test
 *     4) FakeInterface
 *          -an interface which declares no methods
 *     5) FakeClassLoader
 *          -delegates to system class loader
 *
 * Actions
 *   For each test case the test performs the following steps:
 *     1) construct a FakeMarshalOutputStream, passing in a
 *        ByteArrayOutputStream an empty Collection, and
 *        writeAnnotationReturnVal
 *     2) write transferObject to the MarshalOutputStream
 *     3) construct a ByteArrayInputStream from ByteArrayOutputStream byte array
 *     4) construct a MarshalInputStream, passing in ByteArrayInputStream
 *        and FakeClassLoader for verifierLoader
 *     5) call MarshalInputStream.useCodebaseAnnotations method
 *     6) set a system property to providesIntegrity
 *     7) call MarshalInputStream.readObject
 *     8) if transferObject is not an instance of String
 *        and (providesIntegrity is false or writeAnnotationReturnVal is null)
 *           assert ClassNotFoundException is thrown
 *        else
 *           assert transferObject equals the read object
 * </pre>
 */
public class Resolve_VerifyCodebaseIntegrityTest extends QATestEnvironment implements Test {

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

        // writeAnnotationReturnVal values
        String codebase = config.getStringConfigVal(
            "com.sun.jini.test.spec.io.util.fakeArgumentJar","Error");

        // transferObject field values
        String string = "transfer string";
        Object fakeArg = RMIClassLoader.loadClass(codebase,
            "com.sun.jini.test.spec.io.util.FakeArgument").newInstance();
        Object proxy = Proxy.newProxyInstance(
                    RMIClassLoader.getClassLoader(codebase),
                    new Class[] {RMIClassLoader.loadClass(
                        codebase,interfaceName)},
                    new BasicInvocationHandler(new FakeObjectEndpoint(),
                        new FakeMethodConstraints(null)));

        // providesIntegrity
        Boolean f = Boolean.FALSE;
        Boolean t = Boolean.TRUE;

        // test cases
        cases = new Object[][] {
            // transferObject, providesIntegrity, 
            //                   writeAnnotationReturnVal, shouldThrowException
            {string,  f, null,     f},
            {string,  t, null,     f},
            {string,  f, codebase, f},
            {string,  t, codebase, f},
            {fakeArg, f, null,     t},
            {fakeArg, t, null,     t},
            {fakeArg, f, codebase, t},
            {fakeArg, t, codebase, f},
            {proxy,   f, null,     t},
            {proxy,   t, null,     t},
            {proxy,   f, codebase, t},
            {proxy,   t, codebase, f}
        };
        return this;
    }

    // inherit javadoc
    public void run() throws Exception {
        for (int i = 0; i < cases.length; i++) {
            logger.log(Level.FINE,"=================================");
            Object transferObject = cases[i][0];
            boolean providesIntegrity =
                ((Boolean)cases[i][1]).booleanValue();
            String writeAnnotationReturnVal = (String)cases[i][2];
            boolean shouldThrowException =
                ((Boolean)cases[i][3]).booleanValue();
            logger.log(Level.FINE,"test case " + (i+1) + ": "
                + "transferObject:" + transferObject
                + ", providesIntegrity:" + providesIntegrity
                + ", writeAnnotationReturnVal:" + writeAnnotationReturnVal);
            logger.log(Level.FINE,"");

            // Write transferObject to MarshalOutputStream

            ArrayList context = new ArrayList();
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            MarshalOutputStream output = new FakeMarshalOutputStream(
                baos,context,writeAnnotationReturnVal);
            output.writeObject(transferObject);
            output.close();

            // Read transferObject from MarshalInputStream

            // Verifier shouldn't be called (so throw an exception if 
            // it is) under the following conditions:
            boolean condition = (transferObject instanceof String) ||
                                 writeAnnotationReturnVal == null;
            System.setProperty(
                "com.sun.jini.test.spec.io.util.FakeIntegrityVerifier."
                + "throwException","" + condition);

            // set return value for verifier's providesIntegrity method
            System.setProperty(
                "com.sun.jini.test.spec.io.util.FakeIntegrityVerifier."
                + "providesIntegrity","" + providesIntegrity);

            ByteArrayInputStream bios = 
                new ByteArrayInputStream(baos.toByteArray());
            MarshalInputStream input = new MarshalInputStream(
                bios,null,true,new FakeClassLoader(),context);
            input.useCodebaseAnnotations();

            // verify result

            if (shouldThrowException) {
                try {
                    input.readObject();
                    assertion(false);
                } catch (ClassNotFoundException ignore) { }
            } else {
                if (transferObject instanceof Proxy) {
                    // can't .equals written and read Proxy classes 
                    // since they are defined in different class loaders
                    Object received = input.readObject();
                    assertion(received instanceof Proxy);
                    Class[] rClasses = 
                        received.getClass().getInterfaces();
                    Class[] tClasses = 
                        transferObject.getClass().getInterfaces();
                    assertion(rClasses.length == tClasses.length);
                    for (int j = 0; j < rClasses.length; j++) {
                        assertion(rClasses[j].getName().equals(
                            tClasses[j].getName()));
                    }
                } else {
                    assertion(transferObject.equals(input.readObject()));
                }
            }
        }
    }

    // inherit javadoc
    public void tearDown() {
    }

}

