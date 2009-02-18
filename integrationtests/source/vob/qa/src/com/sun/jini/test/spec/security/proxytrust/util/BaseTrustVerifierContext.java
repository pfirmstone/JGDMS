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
package com.sun.jini.test.spec.security.proxytrust.util;

import java.util.logging.Level;

// java
import java.util.ArrayList;
import java.util.Collection;
import java.rmi.RemoteException;
import java.lang.reflect.Method;

// net.jini
import net.jini.core.constraint.MethodConstraints;
import net.jini.security.TrustVerifier;
import net.jini.security.proxytrust.ProxyTrust;


/**
 * Base class for all TrustVerifierContext classes implementing
 * TrustVerifier.Context interface having constructor with 1 parameter:
 * Object[]. Collection of those objects will be returned by getCallerContext
 * method.
 */
public abstract class BaseTrustVerifierContext extends BaseIsTrustedObjectClass
        implements TrustVerifier.Context {

    /** Array of context objects for use by trust verifiers. */
    protected Object[] ctxObjs;

    /**
     * List of object which were specified as parameters to 'isTrustedObject'
     * method calls.
     */
    protected ArrayList objList;

    /** ProxyTrust.getProxyVerifier method */
    private static Method gpvMethod;

    static {
        try {
            gpvMethod =
                ProxyTrust.class.getMethod("getProxyVerifier", new Class[0]);
        } catch (Exception e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    /**
     * Constructor saving array of context objects.
     */
    public BaseTrustVerifierContext(Object[] objs) {
        ctxObjs = objs;

        if (ctxObjs == null) {
            ctxObjs = new Object[0];
        }
        objList = new ArrayList();
    }

    /**
     * Returns context objects passed to constructor.
     *
     * @return context objects passed to constructor
     */
    public Object[] getCtxObjs() {
        return ctxObjs;
    }

    /**
     * Returns a collection of context objects for use by trust verifiers.
     *
     * @return a collection of context objects for use by trust verifiers
     */
    public Collection getCallerContext() {
        Collection coll = new ArrayList();

        for (int i = 0; i < ctxObjs.length; ++i) {
            coll.add(ctxObjs[i]);
        }
        return coll;
    }

    /**
     * Method from TrustVerifier.Context interface. Does nothing.
     *
     * @returns null
     */
    public ClassLoader getClassLoader() {
        return null;
    }

    /**
     * Returns list of object which were specified as parameters to
     * 'isTrustedObject' method calls.
     *
     * @return list of object which were specified as parameters to
     *         'isTrustedObject' method calls
     */
    public ArrayList getObjsList() {
        return objList;
    }

    /**
     * Returns name of checked method.
     *
     * @return 'checked method' name
     */
    public String getMethodName() {
        return "isTrustedObject";
    }

    /**
     * Checks if this Context contains MethodConstraints for
     * 'getProxyTrustIterator' method of ProxyTrust. Return true if it contains
     * required constraints and false otherwise.
     *
     * @return true if this Context contains MethodConstraints for
     *         'getProxyTrustIterator' method of ProxyTrust and false otherwise
     */
    public boolean containsValidMC() {
        if (getValidMC() == null) {
            return false;
        }
        return true;
    }

    /**
     * Returns first valid MethodConstraints for 'getProxyTrustIterator' method
     * of ProxyTrust or null otherwise.
     *
     * @return first valid MethodConstraints for 'getProxyTrustIterator' method
     *         of ProxyTrust or null otherwise.
     */
    public MethodConstraints getValidMC() {
        for (int i = 0; i < ctxObjs.length; ++i) {
            if ((ctxObjs[i] instanceof MethodConstraints)
                    && !((MethodConstraints) ctxObjs[i]).getConstraints(
                            gpvMethod).isEmpty()) {
                return (MethodConstraints) ctxObjs[i];
            }
        }
        return null;
    }

    /**
     * Returns a string representation of this object.
     *
     * @return a string representation of this object
     */
    public String toString() {
        String str = " ";

        for (int i = 0; i < ctxObjs.length; ++i) {
            str += ctxObjs[i] + " ";
        }
        return str;
    }
}
