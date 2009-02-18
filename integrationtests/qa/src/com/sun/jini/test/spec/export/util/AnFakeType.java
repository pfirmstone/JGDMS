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
package com.sun.jini.test.spec.export.util;


/**
 * An auxiliary class used in the tests for
 * {@link net.jini.export.ServerContext} as a server context element.
 */
public class AnFakeType {

    /**
     * The inner value.
     */
    private Integer innerValue = null;

    /**
     * Constructor. Creates an instance of
     * {@link com.sun.jini.test.spec.export.util.AnFakeType} object from the
     * specified initial value.
     *
     * @param initValue the initial value
     */
    public AnFakeType(int initValue) {
        innerValue = new Integer(initValue);
    }

    /**
     * Get the inner value of the
     * {@link com.sun.jini.test.spec.export.util.AnFakeType} object.
     *
     * @return the inner value
     */
    public Integer getValue() {
        return innerValue;
    }

    /**
     * Converts the {@link com.sun.jini.test.spec.export.util.AnFakeType} object
     * to a String.
     *
     * @return the string representation of the
     *         {@link com.sun.jini.test.spec.export.util.AnFakeType} object.
     */
    public String toString() {
        return this.getClass().getName() + "[" + innerValue.toString() + "]";
    }

    /**
     * Indicates whether some other
     * {@link com.sun.jini.test.spec.export.util.AnFakeType} object is "equal to"
     * this one.
     *
     * @param obj the reference object with which to compare
     * @return true if this object is the same as the obj argument;
     *         false otherwise.
     */
    public boolean equals(AnFakeType obj) {
        return innerValue.equals(obj.getValue());
    }
}
