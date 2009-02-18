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
package com.sun.jini.test.spec.security.util;

// java
import java.util.ArrayList;

// net.jini
import net.jini.security.TrustVerifier;


/**
 * Base class for all TrustVerifier-s implementing TrustVerifier interface.
 */
public abstract class BaseTrustVerifier implements TrustVerifier {

    /** Classes whose 'isTrustedObject' method was called. */
    protected static ArrayList classes = new ArrayList();

    /** Objects passed to 'isTrustedObject' method. */
    protected static ArrayList objs = new ArrayList();

    /** Contexts passed to 'isTrustedObject' method. */
    protected static ArrayList ctxs = new ArrayList();

    /**
     * Returns classes whose 'isTrustedObject' method was called.
     *
     * @return classes whose 'isTrustedObject' method was called
     */
    public static Class[] getClasses() {
        return (Class []) classes.toArray(new Class[classes.size()]);
    }

    /**
     * Returns objects passed to 'isTrustedObject' method.
     *
     * @return objects passed to 'isTrustedObject' method
     */
    public static Object[] getObjs() {
        return objs.toArray();
    }

    /**
     * Returns contexts passed to 'isTrustedObject' method.
     *
     * @return contexts passed to 'isTrustedObject' method
     */
    public static Object[] getCtxs() {
        return ctxs.toArray();
    }

    /**
     * Resets obj and ctx list.
     */
    public static void initLists() {
        classes = new ArrayList();
        objs = new ArrayList();
        ctxs = new ArrayList();
    }
}
