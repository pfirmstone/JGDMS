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

// net.jini
import net.jini.security.proxytrust.ProxyTrustIterator;


/**
 * Class having constructor with 1 parameter: Object[] and public method
 * 'ProxyTrustIterator getProxyTrustIterator()' returning TestTrustIterator
 * with objects specified in constructor's parameter.
 */
public class ValidNonProxyObject extends BaseIsTrustedObjectClass {

    /** Objects specified in constructor. */
    protected Object[] objs;

    /** TrustIterator produced from objects. */
    protected ProxyTrustIterator pti;

    /**
     * Store given array of objects and creates TrustIterator.
     *
     * @param objs array of objects
     */
    public ValidNonProxyObject(Object[] objs) {
        this.objs = objs;
        pti = new TestTrustIterator(objs);
    }

    /**
     * Returns ProxyTrustIterator produced from stored objects array.
     *
     * @return stored ProxyTrustITerator
     */
    public ProxyTrustIterator getProxyTrustIterator() {
        srcArray.add(this);
        return pti;
    }

    /**
     * Returns array of objects which were specified in constructor.
     *
     * @return array of objects which were specified in constructor
     */
    public Object[] getObjArray() {
        return objs;
    }

    /**
     * Returns name of checked method.
     *
     * @return 'checked method' name
     */
    public String getMethodName() {
        return "getProxyTrustIterator";
    }

    /**
     * Returns a string representation of this object.
     *
     * @return a string representation of this object
     */
    public String toString() {
        String str = "ValidNonProxyObject[ ";

        for (int i = 0; i < objs.length; ++i) {
            str += objs[i].getClass().getName() + " ";
        }
        str += "]";
        return str;
    }
}
