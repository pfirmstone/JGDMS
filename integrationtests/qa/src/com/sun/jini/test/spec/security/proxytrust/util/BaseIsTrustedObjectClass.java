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


/**
 * Base class for auxiliary classes for testing 'isTrustedObject' method of
 * ProxyTrustVerifier class.
 */
public abstract class BaseIsTrustedObjectClass {

    /** List of classes whose checked methods were invoked. */
    protected static ArrayList srcArray = new ArrayList();

    /**
     * Inits srcArray's value.
     */
    public static void initClassesArray() {
        srcArray = new ArrayList();
    }

    /**
     * Returns a list of classes whose checked methods were invoked.
     *
     * @return a list of classes whose checked methods were invoked
     */
    public static BaseIsTrustedObjectClass[] getClassesArray() {
        return (BaseIsTrustedObjectClass []) srcArray.toArray(
                new BaseIsTrustedObjectClass[srcArray.size()]);
    }

    /**
     * Returns name of checked method.
     *
     * @return 'checked method' name
     */
    public abstract String getMethodName();
}
