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
package com.sun.jini.test.spec.io.marshalledinstance;

import java.util.logging.Level;

import com.sun.jini.qa.harness.QATestEnvironment;
import com.sun.jini.qa.harness.QAConfig;
import com.sun.jini.qa.harness.AdminManager;
import com.sun.jini.qa.harness.Test;

import net.jini.io.MarshalledInstance;

import com.sun.jini.test.spec.io.util.FakeObject;

import java.rmi.server.RMIClassLoader;
import java.util.logging.Level;
import java.io.IOException;
import java.rmi.ConnectException;
import java.rmi.RemoteException;
import java.rmi.MarshalException;
import java.rmi.UnmarshalException;
import java.lang.reflect.UndeclaredThrowableException;
import net.jini.loader.ClassLoading;

/**
 * <pre>
 * Purpose
 *   This test verifies the behavior of MarshalledInstance
 *   get method in cases where it should throw an exception.
 * 
 * Test Cases
 *   This test iterates over a set of exceptions.  Each exception
 *   denotes one test case and is defined by the variable:
 *      Throwable readObjectException
 *   Additional test cases are defined in the Actions section below.
 * 
 * Infrastructure
 *   This test requires the following infrastructure:
 *     1) FakeObject
 *          -implements Serializable
 *          -custom readObject method throws exception passed to constructor
 *     2) FakeArgument
 *          -implements Serializable
 *          -class file contained in a JAR file separate from the test
 *     3) FakeIntegrityVerifier
 *          -overrides the providesIntegrity method so it returns a value
 *           based on a system property setting
 * 
 * Actions
 *   For each test case the test performs the following steps:
 *     1) construct a FakeObject, passing in readObjectException
 *     2) construct a MarshalledInstance with the FakeObject
 *     3) call the MarshalledInstance get methods
 *     4) assert readObjectException is thrown directly
 *   Additionally, perform the following steps:
 *     5) construct FakeIntegrityVerifier return values by setting system properties
 *     6) construct a FakeArgument instance by calling RMIClassLoader.loadClass
 *     7) construct a MarshalledInstance with the FakeArgument
 *     8) call the MarshalledInstance get methods, passing in true
 *     9) assert a ClassNotFoundException is thrown
 * </pre>
 */
public class Get_ExceptionTest extends QATestEnvironment implements Test {

    QAConfig config;
    AdminManager manager;

    Throwable[] cases = {
        new IOException(),
        new RemoteException(),                  //IOException subclass
        new MarshalException(""),               //RemoteException subclass
        new UnmarshalException(""),             //RemoteException subclass
        new SecurityException(),                //RuntimeException subclass
        new ArrayIndexOutOfBoundsException(),   //RuntimeException subclass
        new UndeclaredThrowableException(null), //RuntimeException subclass
        new NullPointerException(),             //RuntimeException subclass
        new LinkageError(),                     //Error subclass
        new AssertionError(),                   //Error subclass
        new ClassNotFoundException()
    };

    public Test construct(QAConfig sysConfig) throws Exception {
        this.config = (QAConfig) sysConfig;
        config.setDynamicParameter(
                "qaClassServer.port",
                config.getStringConfigVal("com.sun.jini.test.port", "8082"));
        manager = new AdminManager(sysConfig);
        manager.startService("testClassServer");
        return this;
    }

    public void run() throws Exception {
        int counter = 1;
        MarshalledInstance mi;
        for (int i = 0; i < cases.length; i++) {
            logger.log(Level.FINE,"=================================");
            Throwable readObjectException = cases[i];
            logger.log(Level.FINE,"test case " + (counter++) + ": "
                + "readObjectException:" + readObjectException);
            logger.log(Level.FINE,"");

            FakeObject fo = new FakeObject(readObjectException);
            mi = new MarshalledInstance(fo);
            try {
                mi.get(true);
                assertion(false);
            } catch (Throwable caught) {
                assertion(
                    caught.getClass() == readObjectException.getClass(),
                    caught.toString());
            }

            try {
                mi.get(null,true,null,null);
                assertion(false);
            } catch (Throwable caught) {
                assertion(
                    caught.getClass() == readObjectException.getClass(),
                    caught.toString());
            }

        }

        logger.log(Level.FINE,"=================================");
        logger.log(Level.FINE,"test case " + (counter++) + ": "
            + "Codebase unverified");
        logger.log(Level.FINE,"");

        // set return value for verifier's providesIntegrity method
        System.setProperty(
            "com.sun.jini.test.spec.io.util.FakeIntegrityVerifier."
            + "throwException","false");
        System.setProperty(
            "com.sun.jini.test.spec.io.util.FakeIntegrityVerifier."
            + "providesIntegrity","false");

        String codebase = config.getStringConfigVal(
            "com.sun.jini.test.spec.io.util.fakeArgumentJar","Error");
        Object fakeArg = ClassLoading.loadClass(codebase,
            "com.sun.jini.test.spec.io.util.FakeArgument", null, false, null).newInstance();
        mi = new MarshalledInstance(fakeArg);
        try {
            mi.get(true);
            assertion(false);
        } catch (ClassNotFoundException ignore) {
        }

        try {
            mi.get(null,true,null,null);
            assertion(false);
        } catch (ClassNotFoundException ignore) {
        }
    }

    public void tearDown() {
    }

}

