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

package com.sun.jini.test.impl.end2end.e2etest;

/* Java imports */
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import net.jini.core.constraint.ClientAuthentication;
import net.jini.core.constraint.InvocationConstraint;
import java.util.Set;
import java.util.Iterator;

import com.sun.jini.test.impl.end2end.jssewrapper.Bridge;

public class WriterObject implements Serializable,Constants {

    /**
     * the TestClient instance which invoked this call. It is
     * set immediately in writeObject, and so is valid for all
     * other methods in this object.
     */
    private transient TestClient instance;

    /**
     * the default <code>InstanceCarrier</code> to be used if the
     * wrapper is not active
     */
    private InstanceCarrier defaultCarrier;


    WriterObject(InstanceCarrier defaultCarrier) {
        this.defaultCarrier = defaultCarrier;
    }

    /**
     * writeObject for object serialization, called while the server
     * is marshalling a WriterObject return object. Attempts are made
     * to access properties for which permissions are selectively
     * granted to client or server principals. The correct security
     * handling is verified.
     *
     * @serialData The default serialized form of the object is written.
     *
     * @param stream the <code>ObjectOutputStream</code> to which this
     * object is to be written
     */
    private void writeObject(ObjectOutputStream stream)
                 throws IOException, ClassNotFoundException
    {
    stream.defaultWriteObject();
        InstanceCarrier ic = (InstanceCarrier) Bridge.readCallbackLocal.get();
    if (ic == null) {
        ic = defaultCarrier;
    }
        instance = ic.getInstance();
    try {
        System.getProperty("e2etest.clientProp");
        checkClientPropertyAccessSuccess();
    } catch (SecurityException e) {
        checkClientPropertyAccessFailure();
    }
        try {
            System.getProperty("e2etest.serverProp");
            checkServerPropertyAccessSuccess();
        } catch (SecurityException e) {
            checkServerPropertyAccessFailure();
        }

    }

    /**
     * Test whether an access failure for e2etest.clientProp is appropriate.
     */
    private void checkClientPropertyAccessFailure() {
        if (instance.getCombinedConstraints() == null) {
            instance.getLogger().log(ALWAYS,
                         "In WriterObject, combinedConstraints == null");
            return;
        }
    }

    /**
     * Test whether an access success for e2etest.clientProp is appropriate.
     * access to e2etest.clientProp should have occured in a context
     * other than the unqualified client context and failed,
     * so log a test failure.
     */
    private void checkClientPropertyAccessSuccess() {
        if (instance.getCombinedConstraints() == null) {
            instance.getLogger().log(ALWAYS,
                         "In WriterObject, combinedConstraints == null");
            return;
        }
    instance.logFailure("In WriterObject.writeObject\n"
              + "Access to e2etest.clientProp succeeded");
    }

    /**
     * Test whether an access failure for e2etest.serverProp is appropriate.
     * access to e2etest.serverProp should have occured in the
     * unqualified server subject context and succeeded, so log a test failure.
     */
    private void checkServerPropertyAccessFailure() {
        if (instance.getCombinedConstraints() == null) {
            instance.getLogger().log(ALWAYS,
                         "In WriterObject, combinedConstraints == null");
            return;
        }
    instance.logFailure("In WriterObject.writeObject\n"
              + "Access to e2etest.serverProp failed");
    }

    /**
     * Test whether an access success for e2etest.serverProp is appropriate.
     */
    private void checkServerPropertyAccessSuccess() {
        if (instance.getCombinedConstraints() == null) {
            instance.getLogger().log(ALWAYS,
                         "In WriterObject, combinedConstraints == null");
            return;
        }
    }
}
