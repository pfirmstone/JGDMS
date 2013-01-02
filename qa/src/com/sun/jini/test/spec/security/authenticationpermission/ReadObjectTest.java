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
package com.sun.jini.test.spec.security.authenticationpermission;

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
import java.security.PermissionCollection;
import java.security.Permission;
import java.util.Enumeration;
import java.util.List;

// net.jini
import net.jini.security.AuthenticationPermission;

// com.sun.jini
import com.sun.jini.qa.harness.TestException;
import com.sun.jini.qa.harness.QATestEnvironment;
import com.sun.jini.qa.harness.Test;


/**
 * <pre>
 * Purpose
 *   This test verifies the following:
 *     'readObject' method of AuthenticationPermission verifies the syntax of
 *     the target name and recreates any transient state. It throws
 *     InvalidObjectException if the target name or actions
 *     string is null, or if the target name or actions string
 *     does not match the syntax specified in the comments at the beginning
 *     of this class.
 *
 * Infrastructure
 *     FakeAuthenticationPermission - serialization-equivalent of
 *             AuthenticationPermission
 *     FakeOutputStream - stream to serialize FakeAuthenticationPermission as
 *             AuthenticationPermission: it overrides writeClassDescriptor
 *             method in the following way: it writes desc of
 *             AuthenticationPermission class instead of
 *             FakeAuthenticationPermission one
 *
 * Action
 *   The test performs the following steps:
 *     1) construct FakeAuthenticationPermission with different incorrect (from
 *        AuthenticationPermission's point of view) target names:
 *        null,
 *        "",
 *        abc abc,
 *        abc "def,
 *        abc "def" peer abc "def,
 *        abc peer def "def",
 *        abc "abc" peer def,
 *        * "abc",
 *        *,
 *        abc "def" peer *,
 *        abc "def" peer abc "*",
 *        abc "def" peer * "*",
 *        abc "def" abc "def,
 *        abc "def" peer abc "def" abc "def,
 *        abc "def" abc peer def "def",
 *        abc "abc" peer def "def" def,
 *        abc "abc" * "abc",
 *        abc "def" peer abc "def" *
 *     2) construct FakeOutputStream
 *     3) write constructed FakeAuthenticationPermission to FakeOutputStream
 *     4) read from InputStream created from FakeOutputStream content
 *     5) assert that InvalidObjectException will be thrown
 *     6) construct FakeAuthenticationPermission with different incorrect (from
 *        AuthenticationPermission's point of view) actions:
 *        null, "", "*", "lisSten", "accept, lisSten", "accept listen"
 *     7) construct FakeOutputStream1
 *     8) write constructed FakeAuthenticationPermission to FakeOutputStream1
 *     9) read from InputStream created from FakeOutputStream1 content
 *     10) assert that InvalidObjectException will be thrown
 *     11) construct FakeAuthenticationPermission1 with valid target name and
 *         actions
 *     12) construct FakeOutputStream2
 *     13) write constructed FakeAuthenticationPermission1 to FakeOutputStream2
 *     14) read from InputStream created from FakeOutputStream2 content
 *     15) assert that object will be got without exceptions
 * </pre>
 */
public class ReadObjectTest extends QATestEnvironment implements Test {

    /**
     * This method performs all actions mentioned in class description.
     *
     */
    public void run() throws Exception {
        String[] invalidNames = new String[] {
            null,
            "\"\"",
            "abc abc",
            "abc \"def",
            "abc \"def\" peer abc \"def",
            "abc peer def \"def\"",
            "abc \"abc\" peer def",
            "* \"abc\"",
            "*",
            "abc \"def\" peer *",
            "abc \"def\" peer abc \"*\"",
            "abc \"def\" peer * \"*\"",
            "abc \"def\" abc \"def",
            "abc \"def\" peer abc \"def\" abc \"def",
            "abc \"def\" abc peer def \"def\"",
            "abc \"abc\" peer def \"def\" def",
            "abc \"abc\" * \"abc\"",
            "abc \"def\" peer abc \"def\" *" };
        String[] invalidActions = new String[] {
            null, "", "*", "lisSten", "accept, lisSten", "accept listen" };
        ByteArrayOutputStream baos;
        ObjectOutputStream oout;
        ObjectInputStream oin;

        for (int i = 0; i < invalidNames.length; ++i) {
            baos = new ByteArrayOutputStream();
            oout = new FakeOutputStream(baos);
            oout.writeObject(createFakeAP(invalidNames[i], "connect"));
            oout.close();
            oin = new ObjectInputStream(
                    new ByteArrayInputStream(baos.toByteArray()));

            try {
                oin.readObject();

                // FAIL
                throw new TestException(
                        "AuthenticationPermission has been read "
                        + "successfully while "
                        + "InvalidObjectException was expected to "
                        + "be thrown.");
            } catch (InvalidObjectException ioe) {
                // PASS
                logger.fine(ioe.toString() + " has been thrown "
                        + "as expected.");
            }
        }

        for (int i = 0; i < invalidActions.length; ++i) {
            baos = new ByteArrayOutputStream();
            oout = new FakeOutputStream(baos);
            oout.writeObject(createFakeAP("abc \"abc\"",
                    invalidActions[i]));
            oout.close();
            oin = new ObjectInputStream(
                    new ByteArrayInputStream(baos.toByteArray()));

            try {
                oin.readObject();

                // FAIL
                throw new TestException(
                        "AuthenticationPermission has been read "
                        + "successfully while "
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
        oout.writeObject(createFakeAP("abc \"abc\"", "connect"));
        oout.close();
        oin = new ObjectInputStream(
                new ByteArrayInputStream(baos.toByteArray()));
        oin.readObject();

        // PASS
        logger.fine("AuthenticationPermission has been read "
                + "successfully as expected.");
    }

    /**
     * Logs parameters specified and creates FakeAuthenticationPermission with
     * them.
     *
     * @param name 1-st parameter for FakeAuthenticationPermission constructor
     * @param actions 2-nd parameter for FakeAuthenticationPermission
     *         constructor
     * @return created FakeAuthenticationPermission instance
     */
    public FakeAuthenticationPermission createFakeAP(String name,
            String actions) {
        logger.fine("Creating FakeAuthenticationPermission with '" + name
                + "' target name and '" + actions + "' actions.");
        return new FakeAuthenticationPermission(name, actions);
    }


    /**
     * Serialization-equivalent of AuthenticationPermission.
     */
    public static class FakeAuthenticationPermission extends Permission {
        private String actions;

        public FakeAuthenticationPermission(String name, String actions) {
            super(name);
            this.actions = actions;
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
     * Serialization-equivalent of AuthenticationPermissionCollection.
     */
    public static class FakeAuthenticationPermissionCollection
            extends PermissionCollection {
        private List permissions;

        public FakeAuthenticationPermissionCollection(List permissions) {
            this.permissions = permissions;
        }

        public void add(Permission perm) {
        }

        public boolean implies(Permission perm) {
            return false;
        }

        public Enumeration elements() {
            return null;
        }
    }


    /**
     * Stream to serialize FakeAuthenticationPermission as
     * AuthenticationPermission.
     */
    public static class FakeOutputStream extends ObjectOutputStream {

        public FakeOutputStream(OutputStream out) throws IOException {
            super(out);
        }

        protected void writeClassDescriptor(ObjectStreamClass desc)
                throws IOException {
            if (desc.forClass() == FakeAuthenticationPermission.class) {
                desc = ObjectStreamClass.lookup(AuthenticationPermission.class);
            } else if (desc.forClass() ==
                    FakeAuthenticationPermissionCollection.class) {
                try {
                    desc = ObjectStreamClass.lookup(
                        Class.forName("net.jini.security.AuthenticationPermission$AuthenticationPermissionCollection"));
                } catch (ClassNotFoundException cnfe) {
                    cnfe.printStackTrace();
                    throw new IOException(cnfe.getMessage());
                }
            }
            super.writeClassDescriptor(desc);
        }
    }
}
