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
package org.apache.river.test.spec.io.marshaloutputstream;

import java.util.logging.Level;

import org.apache.river.qa.harness.QATestEnvironment;
import org.apache.river.qa.harness.QAConfig;
import org.apache.river.qa.harness.Test;

import org.apache.river.test.spec.io.util.FakeSecurityManager;

import net.jini.io.MarshalOutputStream;

import java.security.Permission;
import java.io.OutputStream;
import java.io.ByteArrayOutputStream;
import java.io.PipedOutputStream;
import java.io.SerializablePermission;
import java.io.IOException;
import java.util.Collection;
import java.util.ArrayList;
import java.util.logging.Level;

/**
 * <pre>
 * Purpose
 *   This test verifies the behavior of the MarshalOutputStream
 *   during normal and exceptional constructor calls.
 * 
 *   This test verifies the behavior of the
 *   MarshalOutputStream.getObjectStreamContext method.
 * 
 * Test Cases
 *   This test contains 6 test cases:
 *     1) new MarshalOutputStream(null,null)
 *     2) new MarshalOutputStream(OutputStream,null)
 *     3) new MarshalOutputStream(null,Collection)
 *     4) new MarshalOutputStream(OutputStream,Collection)
 *     5) new MarshalOutputStream(OutputStream,Collection)
 *        that throws IOException
 *     6) new MarshalOutputStream(OutputStream,Collection)
 *        that throws SecurityException
 * 
 * Infrastructure
 *   This test requires the following infrastructure:
 *     1) FakeSecurityManager
 *          -constructor takes lists of permissions to allow and prohibit
 *          -overloads checkPermission(Permission perm) method which throws
 *           SecurityException if perm in prohibit list and returns
 *           quietly if perm in allow list
 *  
 * Actions
 *   The test performs the following steps:
 *     1) construct a MarshalOutputStream, passing in null for both arguments
 *     2) assert NullPointerException is thrown
 *     3) construct a ByteArrayOutputStream
 *     4) construct a MarshalOutputStream, passing in ByteArrayOutputStream
 *        and a null context
 *     5) assert NullPointerException is thrown
 *     6) construct an empty ArrayList
 *     7) construct a MarshalOutputStream, passing in null output stream
 *        and the ArrayList context
 *     8) assert NullPointerException is thrown
 *     9) construct a MarshalOutputStream, passing in ByteArrayOutputStream
 *        and the ArrayList context
 *    10) assert getObjectStreamContext method returns the same ArrayList
 *    11) construct an unconnected PipedOutputStream
 *    12) construct a MarshalOutputStream, passing in PipedOutputStream
 *        and the ArrayList context
 *    13) assert IOException is thrown
 *    14) construct a FakeSecurityManager, prohibiting
 *        SerializablePermission("enableSubclassImplementation") permission
 *    15) set the SecurityManager to FakeSecurityManager
 *    16) construct a MarshalOutputStream, passing in ByteArrayOutputStream
 *        and the ArrayList context
 *    17) assert SecurityException is thrown
 * </pre>
 */
public class ConstructorAccessorTest extends QATestEnvironment implements Test {

    private SecurityManager original;

    class FakeMarshalOutputStream extends MarshalOutputStream {
        public FakeMarshalOutputStream(OutputStream out, Collection context)
            throws IOException
        {
            super(out,context);
        }
        public void writeUnshared(Object obj) throws IOException {}
    }

    public Test construct(QAConfig sysConfig) throws Exception {
        original = System.getSecurityManager();
        return this;
    }

    public void run() throws Exception {
        MarshalOutputStream stream;
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ArrayList al = new ArrayList();
        PipedOutputStream pos = new PipedOutputStream();

        logger.log(Level.FINE,"=================================");
        logger.log(Level.FINE,"test case 1: "
            + "MarshalOutputStream(null,null)");
        logger.log(Level.FINE,"");

        try {
            stream = new MarshalOutputStream(null,null);
            assertion(false);
        } catch (NullPointerException ignore) {
        }

        logger.log(Level.FINE,"=================================");
        logger.log(Level.FINE,"test case 2: "
            + "MarshalOutputStream(OutputStream,null)");
        logger.log(Level.FINE,"");

        try {
            stream = new MarshalOutputStream(baos,null);
            assertion(false);
        } catch (NullPointerException ignore) {
        }

        logger.log(Level.FINE,"=================================");
        logger.log(Level.FINE,"test case 3: "
            + "MarshalOutputStream(null,Collection)");
        logger.log(Level.FINE,"");

        try {
            stream = new MarshalOutputStream(null,al);
            assertion(false);
        } catch (NullPointerException ignore) {
        }

        logger.log(Level.FINE,"=================================");
        logger.log(Level.FINE,"test case 4: "
            + "getObjectStreamContext method returns constructor arg");
        logger.log(Level.FINE,"");

        stream = new MarshalOutputStream(baos,al);
        assertion(stream.getObjectStreamContext() == al);

        logger.log(Level.FINE,"=================================");
        logger.log(Level.FINE,"test case 5: "
            + "constructor throws IOException");
        logger.log(Level.FINE,"");

        try {
            stream = new MarshalOutputStream(pos,al);
            assertion(false);
        } catch (IOException ignore) {
        }

        logger.log(Level.FINE,"=================================");
        logger.log(Level.FINE,"test case 6: "
            + "constructor throws SecurityException");
        logger.log(Level.FINE,"");

        try {
            Permission p = new SerializablePermission(
                "enableSubclassImplementation");
            System.setSecurityManager(new FakeSecurityManager(
                null, new Permission[] {p}, original));
            stream = new FakeMarshalOutputStream(baos,al);
            assertion(false);
        } catch (SecurityException ignore) { }
    }

    public void tearDown() {
        System.setSecurityManager(original);
    }

}

