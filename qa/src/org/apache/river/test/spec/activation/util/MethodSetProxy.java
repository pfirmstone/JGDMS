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
package org.apache.river.test.spec.activation.util;
import java.lang.IllegalArgumentException;
import java.lang.reflect.InvocationHandler;
import java.util.logging.Logger;
import java.util.logging.Level;
import java.rmi.Remote;
import org.apache.river.test.spec.activation.util.RemoteMethodSetInterface;


/**
 * An implementation of the <code>RemoteMethodSetInterface</code>
 * class with access control to each method.
 */
public class MethodSetProxy implements RemoteMethodSetInterface {
    private Object o;
    private int i;
    private Logger logger;

    /**
     * Stores the logger for debug purposes
     */
    public MethodSetProxy(Logger logger) {
        this.logger = logger;
    }

    /**
     * Stores object that will be returned by demand
     */
    public void setObject(Object o) {
        this.o = o;
    };

    /**
     * Stores int value that will be returned by demand
     */
    public void setInt(int i) {
        this.i = i;
    };

    /**
     * Increases stored int to 10
     */
    public void voidReturn() {
        i += 10;
        logger.log(Level.FINEST, "MethodSetProxy.voidReturn()");
    };

    /**
     * Increases stored int to 20 and to i
     */
    public void voidReturn(int i, Object o) {
        this.i += 20 + i;
        logger.log(Level.FINEST, "MethodSetProxy.voidReturn(int i, Object o)");
    };

    /**
     * Increases stored int to 30 and to i
     */
    public void voidReturn(int i) {
        this.i += 30 + i;
        logger.log(Level.FINEST, "MethodSetProxy.voidReturn(int i)");
    };

    /**
     * Returns stored object
     */
    public Object objectReturn() {
        logger.log(Level.FINEST, "MethodSetProxy.objectReturn()");
        return o;
    };

    /**
     * Returns stored object
     */
    public Object objectReturn(Object o, int i) {
        logger.log(Level.FINEST,
                "MethodSetProxy.objectReturn(Object o, int i)");
        return o;
    };

    /**
     * Returns Integer constructed from i
     */
    public Object objectReturn(int i) {
        logger.log(Level.FINEST, "MethodSetProxy.objectReturn(int i)");
        return new Integer(i);
    };

    /**
     * Returns stored int
     */
    public int intReturn() {
        logger.log(Level.FINEST, "MethodSetProxy.intReturn()");
        return i;
    };

    /**
     * Returns stored int
     */
    public int intReturn(int i, Object o) {
        logger.log(Level.FINEST, "MethodSetProxy.intReturn(int i, Object o)");
        return i;
    };

    /**
     * Returns passed i
     */
    public int intReturn(int i) {
        logger.log(Level.FINEST, "MethodSetProxy.intReturn(int i)");
        return i;
    };

    /**
     * Returns passed ia
     */
    public int[] intArrayReturn(int[] ia) {
        logger.log(Level.FINEST, "MethodSetProxy.intArrayReturn(int[] ia)");
        return ia;
    };

    /**
     * Returns passed oa
     */
    public Object[] objectArrayReturn(Object[] oa) {
        logger.log(Level.FINEST,
                "MethodSetProxy.objectArrayReturn(Object[] oa)");
        return oa;
    };

    /**
     * Returns passed b
     */
    public byte byteReturn(byte b) {
        logger.log(Level.FINEST, "MethodSetProxy.byteReturn(byte b)");
        return b;
    };

    /**
     * Returns passed l
     */
    public long longReturn(long l) {
        logger.log(Level.FINEST, "MethodSetProxy.longReturn(long l)");
        return l;
    };

    /**
     * Returns passed d
     */
    public double doubleReturn(double d) {
        logger.log(Level.FINEST, "MethodSetProxy.doubleReturn(double d)");
        return d;
    };

    /**
     * Returns passed b
     */
    public boolean booleanReturn(boolean b) {
        logger.log(Level.FINEST, "MethodSetProxy.booleanReturn(boolean b)");
        return b;
    };

    /**
     * Returns passed c
     */
    public char charReturn(char c) {
        logger.log(Level.FINEST, "MethodSetProxy.charReturn(char c)");
        return c;
    };

    /**
     * Returns passed s
     */
    public short shortReturn(short s) {
        logger.log(Level.FINEST, "MethodSetProxy.shortReturn(short s)");
        return s;
    };

    /**
     * Returns passed f
     */
    public float floatReturn(float f) {
        logger.log(Level.FINEST, "MethodSetProxy.floatReturn(float f)");
        return f;
    };
}
