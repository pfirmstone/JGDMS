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
import com.sun.jini.qa.harness.Test;

import com.sun.jini.test.spec.io.util.FakeMarshalInputStream;
import com.sun.jini.test.spec.io.util.FakeMarshalOutputStream;

import net.jini.io.MarshalOutputStream;
import net.jini.io.MarshalInputStream;

import java.io.File;
import java.io.ByteArrayOutputStream;
import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.logging.Level;
import java.lang.reflect.UndeclaredThrowableException;
import java.io.IOException;
import java.rmi.UnknownHostException;
import java.rmi.ConnectException;
import java.rmi.RemoteException;
import java.rmi.MarshalException;
import java.rmi.UnmarshalException;
import java.rmi.ConnectIOException;
//import java.net.UnknownHostException;
//import java.net.ConnectException;

/**
 * <pre>
 * Purpose
 *   This test verifies the behavior or the MarshalInputStream.resolveClass
 *   and MarshalInputStream.resolveProxyClass protected methods when
 *   the call to the readAnnotation method throws an exception.
 * 
 * Test Cases
 *   This test iterates over a set of exceptions.  Each exception
 *   denotes one test case and is defined by the variable:
 *      Throwable readAnnotationException
 *   where readAnnotationException is restricted to instances of:
 *      IOException
 *      ClassNotFoundException
 *      RuntimeException
 *      Error
 * 
 * Infrastructure
 *   This test requires the following infrastructure:
 *     1) FakeMarshalInputStream
 *          -extends MarshalInputStream
 *          -readAnnotation method throws exception passed to constructor
 * 
 * Actions
 *   For each test case the test performs the following steps:
 *     1) construct a MarshalOutputStream, passing in a ByteArrayOutputStream
 *        and an empty Collection
 *     2) write a File object to the MarshalOutputStream
 *     3) construct a ByteArrayInputStream from ByteArrayOutputStream byte array
 *     4) construct a FakeMarshalInputStream, passing in ByteArrayInputStream
 *        and readAnnotationException
 *     5) attempt to read from the FakeMarshalInputStream
 *     6) assert readAnnotationException is thrown directly
 * </pre>
 */
public class Resolve_ReadAnnotationExceptionTest extends QATestEnvironment implements Test {

    Throwable[] cases = {
        new ClassNotFoundException(),
        new IOException(),
        new java.net.UnknownHostException(),    //IOException subclass
        new java.net.ConnectException(),        //IOException subclass
        new RemoteException(),                  //IOException subclass
        new java.rmi.UnknownHostException(""),  //RemoteException subclass 
        new java.rmi.ConnectException(""),      //RemoteException subclass
        new MarshalException(""),               //RemoteException subclass
        new UnmarshalException(""),             //RemoteException subclass
        new ConnectIOException(""),             //RemoteException subclass
        new SecurityException(),                //RuntimeException subclass
        new ArrayIndexOutOfBoundsException(),   //RuntimeException subclass
        new UndeclaredThrowableException(null), //RuntimeException subclass
        new NullPointerException(),             //RuntimeException subclass
        new LinkageError(),                     //Error subclass
        new AssertionError()                    //Error subclass
    };

    public Test construct(QAConfig sysConfig) throws Exception {
        return this;
    }

    public void run() throws Exception {
        for (int i = 0; i < cases.length; i++) {
            logger.log(Level.FINE,"=================================");
            Throwable readAnnotationException = cases[i];
            logger.log(Level.FINE,"test case " + (i+1) + ": "
                + "readAnnotationException:" + readAnnotationException);
            logger.log(Level.FINE,"");

            // Write a File object to MarshalOutputStream

            ArrayList context = new ArrayList();
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            MarshalOutputStream output = new FakeMarshalOutputStream(
                baos,context,"http://foo.bar");
            output.writeObject(new File("test case " + (i+1)));
            output.close();

            // Attempt to read from MarshalInputStream

            ByteArrayInputStream bios = new ByteArrayInputStream(
                baos.toByteArray());
            MarshalInputStream input = new FakeMarshalInputStream(
                bios,readAnnotationException,null);

            // verify result

            try {
                System.out.println("readObject: " + input.readObject());
                throw new AssertionError("readObject() call should fail");
            } catch (Throwable caught) {
                assertion(readAnnotationException.equals(caught),
                    caught.toString());
            }
        }
    }

    public void tearDown() {
    }

}

