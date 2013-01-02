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
import com.sun.jini.qa.harness.TestException;
import com.sun.jini.qa.harness.QAConfig;
import com.sun.jini.qa.harness.Test;

import com.sun.jini.test.spec.io.util.FakeSecurityManager;

import net.jini.io.MarshalInputStream;

import java.security.Permission;
import java.io.InputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectOutputStream;
import java.io.PipedInputStream;
import java.io.SerializablePermission;
import java.io.IOException;
import java.util.Collection;
import java.util.ArrayList;
import java.util.logging.Level;

/**
 * <pre>
 * Purpose
 *   This test verifies the behavior of the MarshalInputStream
 *   during normal and exceptional constructor calls.
 * 
 *   This test verifies the behavior of the
 *   MarshalInputStream.getObjectStreamContext method.
 * 
 * Test Cases
 *   This test contains 5 test cases: (* indicates a "don't care" value)
 *     1) new MarshalInputStream(null,*,*,*,null)
 *     2) new MarshalInputStream(InputStream,*,*,*,null)
 *     3) new MarshalInputStream(null,*,*,*,Collection)
 *     4) new MarshalInputStream(InputStream,*,*,false,Collection)
 *        that throws IOException
 *     5) new MarshalInputStream(InputStream,*,*,false,Collection)
 *        that throws SecurityException
 * 
 * Infrastructure
 *   This test requires the following infrastructure:
 *     1) FakeSecurityManager
 *          -constructor takes lists of permissions to allow and prohibit
 *          -overrides checkPermission(Permission perm) method which throws
 *           SecurityException if perm in prohibit list and returns
 *           quietly if perm in allow list
 *     2) FakeMarshalInputStream
 *          -extends MarshalInputStream
 *          -overrides readUnshared method
 * 
 * Actions
 *   The test performs the following steps:
 *     1) construct a MarshalInputStream, passing in null for input stream
 *        and context arguments
 *     2) assert NullPointerException is thrown
 *     3) construct a ByteArrayInputStream
 *     4) construct a MarshalInputStream, passing in ByteArrayInputStream
 *        and a null context
 *     5) assert NullPointerException is thrown
 *     6) construct an empty ArrayList
 *     7) construct a MarshalInputStream, passing in null input stream
 *        and the ArrayList context
 *     8) assert NullPointerException is thrown
 *     9) construct a MarshalInputStream, passing in ByteArrayInputStream
 *        and the ArrayList context
 *    10) assert getObjectStreamContext method returns the same ArrayList
 *    11) construct an unconnected PipedInputStream
 *    12) construct a MarshalInputStream, passing in PipedInputStream
 *        and the ArrayList context
 *    13) assert IOException is thrown
 *    14) construct a FakeSecurityManager, prohibiting
 *        SerializablePermission("enableSubclassImplementation") permission
 *    15) set the SecurityManager to FakeSecurityManager
 *    16) construct a FakeMarshalInputStream, passing in ByteArrayInputStream
 *        and the ArrayList context
 *    17) assert SecurityException is thrown
 * </pre>
 */
public class ConstructorAccessorTest extends QATestEnvironment implements Test {

    private SecurityManager original;

    class FakeMarshalInputStream extends MarshalInputStream {
        public FakeMarshalInputStream(InputStream in) throws IOException {
            super(in,null,false,null,new ArrayList());
        }
        public Object readUnshared() throws IOException,ClassNotFoundException {
            return null;
        }
    }

    public Test construct(QAConfig sysConfig) throws Exception {
        original = System.getSecurityManager();
        return this;
    }

    /**
     * This method performs all actions mentioned in class description.
     */
    public void run() throws Exception {
        MarshalInputStream stream;
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ObjectOutputStream oos = new ObjectOutputStream(baos);
        oos.flush();
        ByteArrayInputStream bais = 
            new ByteArrayInputStream(baos.toByteArray());
        ArrayList al = new ArrayList();
        PipedInputStream pis = new PipedInputStream();

        logger.log(Level.FINE,"=================================");
        logger.log(Level.FINE,"test case 1: "
            + "MarshalInputStream(null,*,*,*,null)");
        logger.log(Level.FINE,"");

        try {
            stream = new MarshalInputStream(null,null,false,null,null);
            assertion(false);
        } catch (NullPointerException ignore) {
        }

        logger.log(Level.FINE,"=================================");
        logger.log(Level.FINE,"test case 2: "
            + "MarshalInputStream(InputStream,*,*,*,null)");
        logger.log(Level.FINE,"");

        try {
            stream = new MarshalInputStream(bais,null,false,null,null);
            assertion(false);
        } catch (NullPointerException ignore) {
        }

        logger.log(Level.FINE,"=================================");
        logger.log(Level.FINE,"test case 3: "
            + "MarshalInputStream(null,*,*,*,Collection)");
        logger.log(Level.FINE,"");

        try {
            stream = new MarshalInputStream(null,null,false,null,al);
            assertion(false);
        } catch (NullPointerException ignore) {
        }

        logger.log(Level.FINE,"=================================");
        logger.log(Level.FINE,"test case 4: "
            + "getObjectStreamContext method returns constructor arg");
        logger.log(Level.FINE,"");

        bais.reset();
        stream = new MarshalInputStream(bais,null,false,null,al);
        assertion(stream.getObjectStreamContext() == al);

        logger.log(Level.FINE,"=================================");
        logger.log(Level.FINE,"test case 5: "
            + "constructor throws IOException");
        logger.log(Level.FINE,"");

        try {
            stream = new MarshalInputStream(pis,null,false,null,al);
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
            stream = new FakeMarshalInputStream(bais);
            assertion(false);
        } catch (SecurityException ignore) { }
    }

    public void tearDown() {
        System.setSecurityManager(original);
    }

}

