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
/*
 */
package com.sun.jini.test.spec.io.marshalinputstream;

import java.util.logging.Level;

import com.sun.jini.qa.harness.QATestEnvironment;
import com.sun.jini.qa.harness.QAConfig;
import com.sun.jini.qa.harness.TestException;
import com.sun.jini.qa.harness.AdminManager;
import com.sun.jini.qa.harness.Test;

import com.sun.jini.test.spec.io.util.FakeMarshalInputStream;
import com.sun.jini.test.spec.io.util.FakeObjectEndpoint;
import com.sun.jini.test.spec.io.util.FakeRMIClassLoaderSpi;

import net.jini.io.MarshalOutputStream;
import net.jini.io.MarshalInputStream;
import net.jini.jeri.BasicInvocationHandler;

import java.security.Permission;
import java.rmi.server.RMIClassLoader;
import java.io.ByteArrayOutputStream;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.util.ArrayList;
import java.util.logging.Level;
import java.lang.reflect.Proxy;
import java.net.MalformedURLException;

/**
 * <pre>
 * Purpose
 *   This test verifies the behavior of the MarshalInputStream.resolveClass
 *   and MarshalInputStream.resolveProxyClass protected methods when
 *   a call to the RMIClassLoader.loadClass method throws an exception.
 * 
 * Test Cases
 *   This test iterates over a 4-tuple.  Each 4-tuple denotes one test case
 *   and is defined by the cross-product of these variables:
 *      String  loadClassExceptionName
 *      Object  transferObject
 *      boolean callUseCodebaseAnnotations
 *      String  readAnnotationReturnVal
 *   where loadClassExceptionName is restricted to class names and
 *   subclass names of:
 *      MalformedURLException
 *      ClassNotFoundException
 *      RuntimeException
 *      Error
 *   transferObject is one of:
 *      a File object
 *      a dynamic proxy for FakeInterface
 *   callUseCodebaseAnnotations is one of:
 *      true
 *      false
 *   and readAnnotationReturnVal is one of:
 *      null
 *      "fakeCodebase"
 * 
 * Infrastructure
 *   This test requires the following infrastructure:
 *     1) FakeRMIClassLoader
 *          -extends RMIClassLoaderSpi
 *          -loadClass method checks arguments and then
 *           throws loadClassExceptionName
 *     2) FakeMarshalInputStream
 *          -extends MarshalInputStream
 *          -readAnnotation method returns value passed to constructor
 *     3) FakeInterface
 *          -an interface which declares no methods
 * 
 * Actions
 *   For each test case the test performs the following steps:
 *     1) construct a MarshalOutputStream, passing in a ByteArrayOutputStream
 *        and an empty Collection
 *     2) write transferObject to the MarshalOutputStream
 *     3) construct a ByteArrayInputStream from ByteArrayOutputStream byte array
 *     4) construct a FakeMarshalInputStream, passing in ByteArrayInputStream
 *        and readAnnotationReturnVal
 *     5) if callUseCodebaseAnnotations is true, call
 *        MarshalInputStream.useCodebaseAnnotations method
 *     6) communicate expected arguments and loadClassExceptionName to
 *        FakeRMIClassLoader using static variables in FakeRMIClassLoader
 *     7) attempt to read from the FakeMarshalInputStream
 *     8) assert that arguments passed to FakeRMIClassLoader are correct
 *     9) assert the exception denoted by loadClassExceptionName
 *        is thrown directly
 * </pre>
 */
public class Resolve_LoadClassExceptionTest extends QATestEnvironment implements Test {

    QAConfig config;
    Object[][] cases;
    String interfaceName = "com.sun.jini.test.spec.io.util.FakeInterface";
    AdminManager manager;

    public Test construct(QAConfig sysConfig) throws Exception {
        this.config = (QAConfig) sysConfig;
        config.setDynamicParameter(
                "qaClassServer.port",
                config.getStringConfigVal("com.sun.jini.test.port", "8082"));
        manager = new AdminManager(sysConfig);
        manager.startService("testClassServer");

        // readAnnotationReturnVal values
        String codebase = config.getStringConfigVal(
            "com.sun.jini.test.spec.io.util.fakeArgumentJar","Error");

        // transferObject field values
        File file = new File("test case");
        Object proxy = Proxy.newProxyInstance(
                    RMIClassLoader.getClassLoader(codebase),
                    new Class[] {RMIClassLoader.loadClass(
                        codebase,interfaceName)},
                    new BasicInvocationHandler(new FakeObjectEndpoint(),null));

        // callUseCodebaseAnnotations
        Boolean f = Boolean.FALSE;
        Boolean t = Boolean.TRUE;

        // loadClassException
        Throwable cnfe = new ClassNotFoundException();
        Throwable murle = new MalformedURLException();
        Throwable npe = new NullPointerException();
        Throwable ae = new AssertionError();

        // test cases
        cases = new Object[][] {
            // loadClassException, transferObject, 
            //              callUseCodebaseAnnotations, readAnnotationReturnVal
            {cnfe,   file,  t, codebase},
            {murle,  file,  t, codebase},
            {npe,    file,  t, null},
            {ae,     file,  t, null},
            {cnfe,   file,  f, null},
            {murle,  file,  f, null},
            {npe,    proxy, f, null},
            {ae,     proxy, f, null},
            {cnfe,   proxy, t, null},
            {murle,  proxy, t, null},
            {npe,    proxy, t, codebase},
            {ae,     proxy, t, codebase}
        };
        return this;
    }

    /**
     * This method performs all actions mentioned in class description.
     */
    public void run() throws Exception {
        for (int i = 0; i < cases.length; i++) {
            logger.log(Level.FINE,"=================================");
            Throwable loadClassException = (Throwable) cases[i][0];
            Object transferObject = cases[i][1];
            boolean callUseCodebaseAnnotations =
                ((Boolean)cases[i][2]).booleanValue();
            String readAnnotationReturnVal = (String)cases[i][3];
            logger.log(Level.FINE,"test case " + (i+1) + ": "
                + "loadClassException:" + loadClassException
                + ", transferObject:" + transferObject
                + ", callUseCodebaseAnnotations:"+callUseCodebaseAnnotations
                + ", readAnnotationReturnVal:" + readAnnotationReturnVal);
            logger.log(Level.FINE,"");

            // Write transferObject to MarshalOutputStream
            ArrayList context = new ArrayList();
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            MarshalOutputStream output = 
                new MarshalOutputStream(baos,context);
            output.writeObject(transferObject);
            output.close();

            // Read transferObject from MarshalInputStream
            ByteArrayInputStream bios = 
                new ByteArrayInputStream(baos.toByteArray());
            MarshalInputStream input = new FakeMarshalInputStream(
                bios,null,readAnnotationReturnVal,false);
            if (callUseCodebaseAnnotations) {
                input.useCodebaseAnnotations();
            }

            // Setup FakeRMIClassLoaderSpi static fields
            if (transferObject instanceof Proxy) {
                FakeRMIClassLoaderSpi.initLoadProxyClass(
                    loadClassException,
                    (callUseCodebaseAnnotations ?
                        readAnnotationReturnVal : null),
                    new String[] {interfaceName},
                    null);
            } else {
                FakeRMIClassLoaderSpi.initLoadClass(
                    loadClassException,
                    (callUseCodebaseAnnotations ?
                        readAnnotationReturnVal : null),
                    transferObject.getClass().getName(),
                    null);
            }

            // verify result
            try {
                input.readObject();
                throw new TestException("should have never reached here");
            } catch (Throwable caught) {
                if (loadClassException instanceof 
                    ClassNotFoundException) 
                {
caught.printStackTrace();
                    assertion(caught instanceof ClassNotFoundException,
                        caught.toString());
                } else {
                    assertion(caught == loadClassException,caught.toString());
                }
            }

        }
    }

    public void tearDown() {
    }

}

