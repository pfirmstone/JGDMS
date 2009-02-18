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
import com.sun.jini.qa.harness.QAConfig;

import com.sun.jini.test.spec.io.util.FakeMarshalInputStream;
import com.sun.jini.test.spec.io.util.FakeRMIClassLoaderSpi;

import net.jini.io.MarshalOutputStream;
import net.jini.io.MarshalInputStream;

import java.io.ByteArrayOutputStream;
import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.logging.Level;

/**
 * <pre>
 * Purpose
 *   This test verifies the behavior of the MarshalInputStream.resolveClass
 *   protected method when a call to the RMIClassLoader.loadClass method 
 *   throws a ClassNotFoundException trying to load a primitive or void class.
 * 
 * Test Cases
 *   This test iterates over a set of Class objects.  Each Class object
 *   denotes one test case and is defined by the variable:
 *      Class  transferObject
 * 
 * Infrastructure
 *   This test requires the following infrastructure:
 *     1) FakeRMIClassLoader
 *          -extends RMIClassLoaderSpi
 *          -loadClass method checks arguments and then
 *           throws ClassNotFoundException
 *     2) FakeMarshalInputStream
 *          -extends MarshalInputStream
 *          -readAnnotation method returns null
 * 
 * Actions
 *   For each test case the test performs the following steps:
 *     1) construct a MarshalOutputStream, passing in a ByteArrayOutputStream
 *        and an empty Collection
 *     2) write transferObject to the MarshalOutputStream
 *     3) construct a ByteArrayInputStream from ByteArrayOutputStream byte array
 *     4) construct a FakeMarshalInputStream, passing in ByteArrayInputStream
 *     5) communicate expected arguments and ClassNotFoundException to
 *        FakeRMIClassLoader using static variables in FakeRMIClassLoader
 *     6) read from the FakeMarshalInputStream and assert returned value is
 *        the same as transferObject
 * </pre>
 */
public class Resolve_LoadClassTest extends QATest {

    // test cases
    Class[] cases = {
        boolean.class,
        byte.class,
        char.class,
        double.class,
        float.class,
        int.class,
        long.class,
        short.class,
        void.class,
    };

    // inherit javadoc
    public void setup(QAConfig sysConfig) throws Exception {
    }

    // inherit javadoc
    public void run() throws Exception {
        for (int i = 0; i < cases.length; i++) {
            logger.log(Level.FINE,"=================================");
            Class transferObject = cases[i];
            logger.log(Level.FINE,"test case " + (i+1) + ": "
                + "transferObject:" + transferObject);
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
                bios,null,null);

            // Setup FakeRMIClassLoaderSpi static fields
            FakeRMIClassLoaderSpi.initLoadClass(
                new ClassNotFoundException(),
                null,
                transferObject.getName(),
                null);

            // verify result
            assertion(input.readObject() == transferObject);
        }
    }

    // inherit javadoc
    public void tearDown() {
    }

}

