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
package com.sun.jini.test.spec.security.accesspermission;

import java.util.logging.Level;

// java
import java.io.OutputStream;
import java.io.ObjectOutputStream;
import java.io.ObjectInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ByteArrayInputStream;
import java.io.InvalidObjectException;
import java.io.ObjectStreamClass;
import java.io.IOException;
import java.security.Permission;

// net.jini
import net.jini.security.AccessPermission;

// com.sun.jini
import com.sun.jini.qa.harness.TestException;
import com.sun.jini.qa.harness.QATestEnvironment;
import com.sun.jini.qa.harness.Test;


/**
 * <pre>
 * Purpose
 *   This test verifies the following:
 *     'readObject' method of AccessPermission verifies the syntax of the target
 *     name and recreates any transient state. It throws InvalidObjectException
 *     if the target name is null, or if the target name does not match the
 *     syntax specified in the comments at the beginning of this class.
 *
 * Infrastructure
 *   This test requires the following infrastructure:
 *     FakeAccessPermission - serialization-equivalent of AccessPermission
 *     FakeOutputStream - stream to serialize FakeAccessPermission as
 *             AccessPermission: it overrides writeClassDescriptor method in the
 *             following way: it writes desc of AccessPermission class instead
 *             of FakeAccessPermission one
 *
 * Action
 *   The test performs the following steps:
 *     1) construct FakeAccessPermission with different incorrect (from
 *        AccessPermission's point of view) target names:
 *        null, "", "4x", "abc.4x", "abc.4x.def", "*.abc", "abc*.def",
 *        "*abc.def", "a*bc.def", "abc.*.def", "*abc*", "abc*de", "abc.*def*",
 *        "abc.d*ef", "abc..def", ".abc.def", "abc.def.", ".", " ", " abc",
 *        "abc ", "a bc", "abc. def", "abc.def ", "abc.d ef"
 *     2) construct FakeOutputStream
 *     3) write constructed FakeAccessPermission to FakeOutputStream
 *     4) read from InputStream created from FakeOutputStream content
 *     5) assert that InvalidObjectException will be thrown
 *     6) construct FakeAccessPermission1 with valid target name
 *     7) construct FakeOutputStream1
 *     8) write constructed FakeAccessPermission1 to FakeOutputStream1
 *     9) read from InputStream created from FakeOutputStream1 content
 *     10) assert that object will be got without exceptions
 * </pre>
 */
public class ReadObjectTest extends QATestEnvironment implements Test {

    /**
     * This method performs all actions mentioned in class description.
     *
     */
    public void run() throws Exception {
        String[] invalidNames = new String[] {
            null, "", "4x", "abc.4x", "abc.4x.def", "*.abc", "abc*.def",
            "*abc.def", "a*bc.def", "abc.*.def", "*abc*", "abc*de",
            "abc.*def*", "abc.d*ef", "abc..def", ".abc.def", "abc.def.",
            ".", " ", " abc", "abc ", "a bc", "abc. def", "abc.def ",
            "abc.d ef" };
        ByteArrayOutputStream baos;
        ObjectOutputStream oout;
        ObjectInputStream oin;

        for (int i = 0; i < invalidNames.length; ++i) {
            baos = new ByteArrayOutputStream();
            oout = new FakeOutputStream(baos);
            oout.writeObject(createFakeAP(invalidNames[i]));
            oout.close();
            oin = new ObjectInputStream(
                    new ByteArrayInputStream(baos.toByteArray()));

            try {
                oin.readObject();

                // FAIL
                throw new TestException(
                        "AccessPermission has been read successfully while "
                        + "InvalidObjectException was expected to "
                        + "be thrown.");
            } catch (InvalidObjectException ioe) {
                // PASS
                logger.fine(ioe.toString() + " has been thrown "
                        + "as expected.");
            }
        }
        baos = new ByteArrayOutputStream();
        oout = new FakeOutputStream(baos);
        oout.writeObject(createFakeAP("a.b.c.def"));
        oout.close();
        oin = new ObjectInputStream(
                new ByteArrayInputStream(baos.toByteArray()));
        oin.readObject();

        // PASS
        logger.fine("AccessPermission has been read successfully "
                + "as expected.");
    }

    /**
     * Logs parameter specified and creates FakeAccessPermission with this
     * parameter.
     *
     * @param name parameter for FakeAccessPermission constructor
     * @return created FakeAccessPermission instance
     */
    public FakeAccessPermission createFakeAP(String name) {
        logger.fine("Creating FakeAccessPermission with '" + name
                + "' target name.");
        return new FakeAccessPermission(name);
    }


    /**
     * Serialization-equivalent of AccessPermission.
     */
    public static class FakeAccessPermission extends Permission {

        public FakeAccessPermission(String name) {
            super(name);
        }

        public boolean implies(Permission permission) {
            return false;
        }

        public boolean equals(Object obj) {
            return false;
        }

        public int hashCode() {
            return 0;
        }

        public String getActions() {
            return null;
        }
    }


    /**
     * Stream to serialize FakeAccessPermission as AccessPermission.
     */
    public static class FakeOutputStream extends ObjectOutputStream {

        public FakeOutputStream(OutputStream out) throws IOException {
            super(out);
        }

        protected void writeClassDescriptor(ObjectStreamClass desc)
                throws IOException {
            if (desc.forClass() == FakeAccessPermission.class) {
                desc = ObjectStreamClass.lookup(AccessPermission.class);
            }
            super.writeClassDescriptor(desc);
        }
    }
}
