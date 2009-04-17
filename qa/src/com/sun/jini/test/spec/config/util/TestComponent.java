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

package com.sun.jini.test.spec.config.util;


/**
 * A fake implementation of some component in configuration.
 */
public class TestComponent extends AbstractTestComponent {

    /**
     * Some static entry.
     */
    public static Object data = null;

    /**
     * Some static entry.
     */
    public static TestComponent staticEntry = new TestComponent();

    /**
     * A fake implementation of some internal component in configuration.
     */
    public static class InternalTestComponent {

        /**
         * Do nothing. Described to be public.
         */
        public InternalTestComponent() {}
    }

    /**
     * Do nothing. Described to be public.
     */
    public TestComponent() {
        staticEntry = null;
    }

    /**
     * Stores argument into data field.
     */
    public TestComponent(Object data) {
        this.data = data;
    }

    /**
     * Some static method to the call from test.
     */
    public static TestComponent staticMethod() {
        return staticEntry;
    };

    /**
     * Some static method to the call from test.
     */
    public static TestComponent staticMethod(int a) {
        return staticEntry;
    };

    /**
     * Some static method to the call from test.
     */
    public static TestComponent staticMethod(Object o) {
        return staticEntry;
    };

    /**
     * Some static method to the call from test.
     */
    public static TestComponent staticMethod(int a, int b, int c) {
        return staticEntry;
    };

    /**
     * Some static method returning InterfaceTestComponent type object
     */
    public static InterfaceTestComponent getInterfaceTestComponent() {
        return (InterfaceTestComponent) staticEntry;
    };

    /**
     * Some static method returning AbstractTestComponent type object
     */
    public static AbstractTestComponent getAbstractTestComponent() {
        return (AbstractTestComponent) staticEntry;
    };

    /**
     * Returns boolean.
     */
    public static boolean getBoolean(Object a) {
        return ((Boolean)a).booleanValue();
    };

    /**
     * Returns byte.
     */
    public static byte getByte(Object a) {
        return ((Byte)a).byteValue();
    };

    /**
     * Returns char.
     */
    public static char getChar(Object a) {
        return ((Character)a).charValue();
    };

    /**
     * Returns short.
     */
    public static short getShort(Object a) {
        return ((Short)a).shortValue();
    };

    /**
     * Returns int.
     */
    public static int getInt(Object a) {
        return ((Integer)a).intValue();
    };

    /**
     * Returns long.
     */
    public static long getLong(Object a) {
        return ((Long)a).longValue();
    };

    /**
     * Returns float.
     */
    public static float getFloat(Object a) {
        return ((Float)a).floatValue();
    };

    /**
     * Returns double.
     */
    public static double getDouble(Object a) {
        return ((Double)a).doubleValue();
    };

    /**
     * Method that throws exception.
     */
    public static TestComponent throwException(Object a) {
        throw new NullPointerException();
    };
}
