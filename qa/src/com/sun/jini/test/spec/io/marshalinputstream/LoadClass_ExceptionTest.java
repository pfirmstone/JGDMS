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

import com.sun.jini.qa.harness.QATest;
import com.sun.jini.qa.harness.TestException;
import com.sun.jini.qa.harness.QAConfig;

import net.jini.loader.ClassLoading;

import com.sun.jini.test.spec.io.util.FakeRMIClassLoaderSpi;

import java.util.ArrayList;
import java.util.logging.Level;
import java.lang.reflect.UndeclaredThrowableException;
import java.net.MalformedURLException;

/**
 * <pre>
 * Purpose
 *   This test verifies the behavior of the ClassLoading.loadClass
 *   and ClassLoading.loadProxyClass static methods when
 *   a call to the RMIClassLoader.loadClass method throws an exception.
 *
 * Test Cases
 *   This test iterates over a set of exceptions.  Each exception
 *   denotes one test case and is defined by the variable:
 *      Throwable loadClassException
 *   where loadClassException is restricted to instances of:
 *      MalformedURLException
 *      ClassNotFoundException
 *      RuntimeException
 *      Error
 *
 * Infrastructure
 *   This test requires the following infrastructure:
 *     1) FakeRMIClassLoader
 *          -extends RMIClassLoaderSpi
 *          -loadClass method checks arguments and then
 *           throws loadClassException
 *
 * Actions
 *   For each test case the test performs the following steps:
 *     1) communicate expected arguments and loadClassException to
 *        FakeRMIClassLoader using static variables in FakeRMIClassLoader
 *     2) call ClassLoading.loadClass
 *     3) assert that arguments passed to FakeRMIClassLoader are correct
 *     4) assert loadClassException is thrown directly
 *     5) call ClassLoading.loadProxyClass
 *     6) assert that arguments passed to FakeRMIClassLoader are correct
 *     7) assert loadClassException is thrown directly
 * </pre>
 */
public class LoadClass_ExceptionTest extends QATest {

    // test cases
    Throwable[] cases = {
        new ClassNotFoundException(),
        new MalformedURLException(),
        new SecurityException(),                //RuntimeException subclass
        new ArrayIndexOutOfBoundsException(),   //RuntimeException subclass
        new UndeclaredThrowableException(null), //RuntimeException subclass
        new NullPointerException(),             //RuntimeException subclass
        new LinkageError(),                     //Error subclass
        new AssertionError()                    //Error subclass
    };

    // inherit javadoc
    public void setup(QAConfig sysConfig) throws Exception {
    }

    // inherit javadoc
    public void run() throws Exception {
        for (int i = 0; i < cases.length; i++) {
            logger.log(Level.FINE,"=================================");
            Throwable loadClassException = (Throwable) cases[i];
            logger.log(Level.FINE,"test case " + (i+1) + ": "
                + "loadClassException:" + loadClassException);
            logger.log(Level.FINE,"");

            // Setup FakeRMIClassLoaderSpi static fields
            FakeRMIClassLoaderSpi.initLoadClass(
                loadClassException, null, "foo", null);

            // verify loadClass result
            try {
                ClassLoading.loadClass(null,"foo",null,true,null);
                assertion(false);
            } catch (Throwable caught) {
		if (caught != loadClassException) {
		    caught.printStackTrace();
		}
                assertion(caught == loadClassException,caught.toString());
            }

            // Setup FakeRMIClassLoaderSpi static fields
            FakeRMIClassLoaderSpi.initLoadProxyClass(
                loadClassException, null, new String[] {"foo"}, null);

            // verify loadProxyClass result
            try {
                ClassLoading.loadProxyClass(
                    null,new String[] {"foo"},null,true,null);
                assertion(false);
            } catch (Throwable caught) {
                assertion(caught == loadClassException,caught.toString());
            }

        }
    }

    // inherit javadoc
    public void tearDown() {
    }

}

