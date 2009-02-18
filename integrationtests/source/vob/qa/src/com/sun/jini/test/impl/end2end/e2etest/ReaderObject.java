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

/* JAAS imports */
import javax.security.auth.Subject;

/* Java imports */
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.Serializable;

import net.jini.core.constraint.ClientAuthentication;
import net.jini.core.constraint.InvocationConstraint;
import net.jini.core.constraint.InvocationConstraints;

import java.security.AccessControlContext;
import java.security.AccessController;

import java.util.Iterator;
import java.util.Set;

import com.sun.jini.test.impl.end2end.jssewrapper.Bridge;

/*
 * Instances of this class are used as call parameters in some
 * remote method calls. This class provides an implementation of
 * the <code>readObject</code> method which performs operations
 * requiring FilePermissions. This is used to verify that execution
 * in client and server contexts occurs when expected.
 */

public class ReaderObject implements Serializable,Constants {

    /**
     * the default <code>InstanceCarrier</code> to be used if the
     * wrapper is not active
     */
    private InstanceCarrier defaultCarrier;

    /**
     * the <code>TestClient</code> instance which invoked this call. It is
     * set immediately in <code>readObject</code>, and so is valid for all
     * other methods in this class.
     */
    private transient TestClient instance;

    /**
     * the <code>Logger</code> associated with the <code>TestClient</code>
     * instance which invoked this call. It is set immediately in
     * <code>readObject,</code> and so is valid for all other methods
     * in this class.
     */
    private transient Logger logger;

    /**
     * Combined constraints for this call. These should be computed
     * using the constraints imposed by the client and derived from
     * the method name on the server.
     */
    private InvocationConstraints constraints;

    /**
     * Construct a ReaderObject which will be deserialized in the context
     * of the given constraints.
     *
     * @param c the combined constraints which will be in force when this
     *          object is unmarshalled in the server
     */
    ReaderObject(InvocationConstraints c, InstanceCarrier defaultCarrier) {
    this.constraints = c;
    this.defaultCarrier = defaultCarrier;
    }

    /**
     * The readObject method called during deserialization in the server.
     * This method attempts to access property <code>e2etest.clientProp</code>
     * which is only allowed in the client context, and
     * <code>e2etest.serverProp</code> which is only allowed in the
     * server context.
     *
     * @throws IOException if an I/O error occurs
     * @throws ClassNotFoundException if the class cannot be found
     */
    private void readObject(ObjectInputStream stream)
        throws IOException, ClassNotFoundException
    {
    stream.defaultReadObject();
        InstanceCarrier ic = (InstanceCarrier) Bridge.readCallbackLocal.get();
    if (ic == null) {
        ic = defaultCarrier;
    }
    instance = ic.getInstance();
    logger = instance.getLogger();

    /* access property only available to client subject */
    try {
        System.getProperty("e2etest.clientProp");
        checkClientPropertyAccessSuccess();
    } catch (SecurityException e) {
        checkClientPropertyAccessFailure();
        if (e instanceof SecurityException) checkClientPropertyAccessFailure();
    }

    /* access property only available to server subject */
    try {
        System.getProperty("e2etest.serverProp");
        checkServerPropertyAccessSuccess();
    } catch (SecurityException e) {
        checkServerPropertyAccessFailure();
    }
    }

    /**
     * Test whether an access failure for e2etest.clientProp is appropriate.<p>
     */
    private void checkClientPropertyAccessFailure() {
        AccessControlContext acc = AccessController.getContext();
        logger.log(DEBUG,"Current Context: " + acc);
        logger.log(DEBUG,"Current Subject: " + Subject.getSubject(acc));
        logger.log(DEBUG,"Current Constraints: " + constraints);
    logger.log(DEBUG,"e2etest.clientProp access failed");
    }

    /**
     * Test whether an access success for <code>e2etest.clientProp</code>
     * is appropriate. If <code>ClientAuthentication.NO</code> exists in
     * the constraints object, or if the proxy was exported with a
     * marshalControl value other than <code>CLIENT_PRIV</code>,
     * then the access to <code>e2etest.clientProp</code> should have
     * occured in a context other than the unqualified client subject context
     * and failed. In this case, log a test failure to the logger.
     */
    private void checkClientPropertyAccessSuccess() {
        AccessControlContext acc = AccessController.getContext();
        logger.log(DEBUG,"Current Context: " + acc);
        logger.log(DEBUG,"Current Subject: " + Subject.getSubject(acc));
        logger.log(DEBUG,"Current Constraints: " + constraints);
    instance.logFailure("In ReaderObject.readObject\n"
                + "Access to e2etest.clientProp succeeded");
    }

    /**
     * Test whether an access failure for e2etest.serverProp is appropriate.
     */
    private void checkServerPropertyAccessFailure() {
        AccessControlContext acc = AccessController.getContext();
        logger.log(DEBUG,"Current Context: " + acc);
        logger.log(DEBUG,"Current Subject: " + Subject.getSubject(acc));
        logger.log(DEBUG,"Current Constraints: " + constraints);
    instance.logFailure("In ReaderObject.readObject\n"
                + "Access to e2etest.serverProp failed");
    }

    /**
     * Test whether an access success for e2etest.serverProp is appropriate.
     * If the proxy was exported with a marshalControl value other than AS_IS
     * then the access to e2etest.serverProp should have occured in a context
     * other than the unqualified server context and failed. In this case,
     * log a test failure to the logger.
     */
    private void checkServerPropertyAccessSuccess() {
        AccessControlContext acc = AccessController.getContext();
        logger.log(DEBUG,"Current Context: " + acc);
        logger.log(DEBUG,"Current Subject: " + Subject.getSubject(acc));
        logger.log(DEBUG,"Current Constraints: " + constraints);
    logger.log(DEBUG,"e2etest.serverProp access succeeded");
    }
}
