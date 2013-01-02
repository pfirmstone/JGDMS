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
package com.sun.jini.test.spec.jeri.basicinvocationhandler;

import java.util.logging.Level;

import com.sun.jini.qa.harness.TestException;

import com.sun.jini.test.spec.jeri.util.FakeArgument;
import com.sun.jini.test.spec.jeri.util.Util;

import net.jini.io.MarshalInputStream;

import java.lang.reflect.Proxy;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.logging.Level;

/**
 * <pre>
 * Purpose
 *   This test verifies the behavior of the BasicInvocationHandler.invoke
 *   method during a normal, non-exceptional method call.
 *
 * Test Cases
 *   Test cases defined by the Actions section below.
 *
 * Infrastructure
 *   This test requires the following infrastructure:
 *     1) FakeInterface
 *          -an interface that declares these methods:
 *             void     voidReturn()
 *             void     voidReturn(int i, Object o)
 *             void     voidReturn(int i)
 *             Object   objectReturn()
 *             Object   objectReturn(Object o, int i)
 *             Object   objectReturn(int i)
 *             int      intReturn()
 *             int      intReturn(int i, Object o)
 *             int      intReturn(int i)
 *             int[]    intArrayReturn(int[] i)
 *             Object[] objectArrayReturn(Object[] o)
 *             byte     byteReturn(byte b)
 *             long     longReturn(long l)
 *             double   doubleReturn(double d)
 *             boolean  booleanReturn(boolean b)
 *             char     charReturn(char c)
 *             short    shortReturn(short s)
 *             float    floatReturn(float f)
 *     2) FakeObjectEndpoint
 *          -newCall returns OutboundRequestIterator passed to constructor
 *          -executeCall method returns null
 *     3) FakeOutboundRequestIterator
 *          -hasNext method returns true on first call and false after that
 *          -next method returns OutboundRequest passed to constructor
 *           and throws NoSuchElementException if called more than once
 *     4) FakeOutboundRequest
 *          -abort method does nothing
 *          -getDeliveryStatus method returns true
 *          -getRequestOutputStream method returns a ByteArrayOutputStream
 *          -getResponseInputStream method returns passed in 
 *           ByteArrayInputStream
 *          -getUnfulfilledConstraints method return InvocationConstraints.EMPTY
 *          -populateContext method does nothing
 *
 * Actions
 *   For each test case the test performs the following steps:
 *     1) construct a FakeOutboundRequest
 *     2) construct a FakeOutboundRequestIterator,passing in FakeOutboundRequest
 *     3) construct a FakeObjectEndpoint, passing in FakeOutboundRequestIterator
 *     4) construct a FakeBasicInvocationHandler, passing in FakeObjectEndpoint
 *        and null MethodConstraints
 *     5) create a dynamic proxy for the FakeInterface using the
 *        BasicInvocationHandler
 *     6) for each method in FakeInterface do the following:
 *          a) initialize FakeOutboundRequestIterator
 *          b) initialize FakeOutboundRequest with values to return
 *          c) invoke each method on dynamic proxy with methodArgs
 *          d) assert the return value is returned
 *          e) assert request stream contains correct bytes and objects
 * </pre>
 */
public class Invoke_NormalReturnTest extends AbstractInvokeTest {

    interface FakeInterface {
        public void     voidReturn() throws IOException;
        public void     voidReturn(int i, Object o) throws IOException;
        public void     voidReturn(int i) throws IOException;
        public Object   objectReturn() throws IOException;
        public Object   objectReturn(Object o, int i) throws IOException;
        public Object   objectReturn(int i) throws IOException;
        public int      intReturn() throws IOException;
        public int      intReturn(int i, Object o) throws IOException;
        public int      intReturn(int i) throws IOException;
        public int[]    intArrayReturn(int[] i) throws IOException;
        public Object[] objectArrayReturn(Object[] o) throws IOException;
        public byte     byteReturn(byte b) throws IOException;
        public long     longReturn(long l) throws IOException;
        public double   doubleReturn(double d) throws IOException;
        public boolean  booleanReturn(boolean b) throws IOException;
        public char     charReturn(char c) throws IOException;
        public short    shortReturn(short s) throws IOException;
        public float    floatReturn(float f) throws IOException;
    }

    // inherit javadoc
    public void run() throws Exception {
        // construct infrastructure needed by test
        request.setDeliveryStatusReturn(true);
        FakeInterface fi = (FakeInterface) Proxy.newProxyInstance(
            FakeInterface.class.getClassLoader(),
            new Class[] { FakeInterface.class },
            handler);

        // i: int method arg
        // o: Object method arg
        // params: method args in an Object array
        // rObj: return Object
        // rInt: return int
        int i; Object o; Object[] params; Object rObj; int rInt;

        FakeArgument fa = new FakeArgument(null,null);

        logger.log(Level.FINE,"=================================");
        logger.log(Level.FINE,"test case 1: voidReturn()");
        logger.log(Level.FINE,"");
        initCall(null);
        fi.voidReturn();
        checkCall(null);

        logger.log(Level.FINE,"=================================");
        logger.log(Level.FINE,"test case 2: voidReturn(int,Object)");
        logger.log(Level.FINE,"");
        i = Integer.MAX_VALUE; o = fa; 
        params = new Object[] {new Integer(i),o};
        initCall(null);
        fi.voidReturn(i,o);
        checkCall(params);

        logger.log(Level.FINE,"=================================");
        logger.log(Level.FINE,"test case 3: voidReturn(int)");
        logger.log(Level.FINE,"");
        i = 0; params = new Object[] {new Integer(i)};
        initCall(null);
        fi.voidReturn(i);
        checkCall(params);

        logger.log(Level.FINE,"=================================");
        logger.log(Level.FINE,"test case 4: objectReturn()");
        logger.log(Level.FINE,"");
        rObj = fa;
        initCall(rObj);
        assertion(fi.objectReturn().equals(rObj));
        checkCall(null);

        logger.log(Level.FINE,"=================================");
        logger.log(Level.FINE,"test case 5: objectReturn(Object,int)");
        logger.log(Level.FINE,"");
        i = Integer.MIN_VALUE; o = fa; 
        params = new Object[] {o,new Integer(i)}; rObj = fa;
        initCall(rObj);
        assertion(fi.objectReturn(o,i).equals(rObj));
        checkCall(params);

        logger.log(Level.FINE,"=================================");
        logger.log(Level.FINE,"test case 6: objectReturn(int)");
        logger.log(Level.FINE,"");
        i = -1; params = new Object[] {new Integer(i)}; rObj = null;
        initCall(rObj);
        assertion(fi.objectReturn(i) == rObj);
        checkCall(params);

        logger.log(Level.FINE,"=================================");
        logger.log(Level.FINE,"test case 7: intReturn()");
        logger.log(Level.FINE,"");
        rInt = 0; rObj = new Integer(rInt);
        initCall(rObj);
        assertion(fi.intReturn() == rInt);
        checkCall(null);

        logger.log(Level.FINE,"=================================");
        logger.log(Level.FINE,"test case 8: intReturn(int,Object)");
        logger.log(Level.FINE,"");
        i = 23; o = null; params = new Object[] {new Integer(i),o}; 
        rInt = Integer.MIN_VALUE; rObj = new Integer(rInt);
        initCall(rObj);
        assertion(fi.intReturn(i,o) == rInt);
        checkCall(params);

        logger.log(Level.FINE,"=================================");
        logger.log(Level.FINE,"test case 9: intReturn(int)");
        logger.log(Level.FINE,"");
        i = 1; params = new Object[] {new Integer(i)}; 
        rInt = Integer.MAX_VALUE; rObj = new Integer(rInt);
        initCall(rObj);
        assertion(fi.intReturn(i) == rInt);
        checkCall(params);

        //////////////////////////////////////////////////////////

        logger.log(Level.FINE,"=================================");
        logger.log(Level.FINE,"test case 10: intArrayReturn(int[])");
        logger.log(Level.FINE,"");
        int[] intArray = new int[] {Integer.MIN_VALUE,0,Integer.MAX_VALUE};
        params = new Object[] {intArray};
        initCall(intArray);
        assertion(Arrays.equals(fi.intArrayReturn(intArray),intArray));
        checkCall(params);

        logger.log(Level.FINE,"=================================");
        logger.log(Level.FINE,"test case 11: objectArrayReturn(Object[])");
        logger.log(Level.FINE,"");
        Object[] objectArray = new Object[] {fa};
        params = new Object[] {objectArray};
        initCall(objectArray);
        assertion(
            Arrays.equals(fi.objectArrayReturn(objectArray),objectArray));
        checkCall(params);

        logger.log(Level.FINE,"=================================");
        logger.log(Level.FINE,"test case 12: byteReturn(byte)");
        logger.log(Level.FINE,"");
        Byte b = new Byte(Byte.MAX_VALUE);
        Byte rByte = new Byte(Byte.MIN_VALUE);
        params = new Object[] {b};
        initCall(rByte);
        assertion(fi.byteReturn(b.byteValue()) == rByte.byteValue());
        checkCall(params);

        logger.log(Level.FINE,"=================================");
        logger.log(Level.FINE,"test case 13: longReturn(long)");
        logger.log(Level.FINE,"");
        Long l = new Long(Long.MAX_VALUE);
        Long rLong = new Long(Long.MIN_VALUE);
        params = new Object[] {l};
        initCall(rLong);
        assertion(fi.longReturn(l.longValue()) == rLong.longValue());
        checkCall(params);

        logger.log(Level.FINE,"=================================");
        logger.log(Level.FINE,"test case 14: doubleReturn(double)");
        logger.log(Level.FINE,"");
        Double d = new Double(Double.MAX_VALUE);
        Double rDouble = new Double(Double.MIN_VALUE);
        params = new Object[] {d};
        initCall(rDouble);
        assertion(fi.doubleReturn(d.doubleValue()) ==rDouble.doubleValue());
        checkCall(params);

        logger.log(Level.FINE,"=================================");
        logger.log(Level.FINE,"test case 15: booleanReturn(boolean)");
        logger.log(Level.FINE,"");
        Boolean bo = new Boolean(true);
        Boolean rBoolean = new Boolean(false);
        params = new Object[] {bo};
        initCall(rBoolean);
        assertion(fi.booleanReturn(bo.booleanValue()) == 
               rBoolean.booleanValue());
        checkCall(params);

        logger.log(Level.FINE,"=================================");
        logger.log(Level.FINE,"test case 16: charReturn(char)");
        logger.log(Level.FINE,"");
        Character c = new Character('a');
        Character rCharacter = new Character('A');
        params = new Object[] {c};
        initCall(rCharacter);
        assertion(fi.charReturn(c.charValue()) == rCharacter.charValue());
        checkCall(params);

        logger.log(Level.FINE,"=================================");
        logger.log(Level.FINE,"test case 17: shortReturn(short)");
        logger.log(Level.FINE,"");
        Short s = new Short(Short.MAX_VALUE);
        Short rShort = new Short(Short.MIN_VALUE);
        params = new Object[] {s};
        initCall(rShort);
        assertion(fi.shortReturn(s.shortValue()) == rShort.shortValue());
        checkCall(params);

        logger.log(Level.FINE,"=================================");
        logger.log(Level.FINE,"test case 18: floatReturn(float)");
        logger.log(Level.FINE,"");
        Float f = new Float(Float.MAX_VALUE);
        Float rFloat = new Float(Float.MIN_VALUE);
        params = new Object[] {f};
        initCall(rFloat);
        assertion(fi.floatReturn(f.floatValue()) == rFloat.floatValue());
        checkCall(params);
    }

    /**
     * Initialized test infrastructure for each test case.
     */
    private void initCall(Object rObj) throws IOException {
        iterator.init();
        request.setResponseInputStream(0x01,rObj);
    }

    /**
     * Check request stream for correct bytes and objects once a
     * call has completed.
     */
    private void checkCall(Object[] params) 
        throws IOException, ClassNotFoundException, TestException 
    {
        ByteArrayInputStream requestStream = request.getRequestStream();
        assertion(requestStream.read() == 0x00);  // verify protocol
        assertion(requestStream.read() == 0x00);  // verify integrity
        MarshalInputStream mis = new MarshalInputStream(
            requestStream,null,false,null,new ArrayList());
        mis.readLong();  // read but don't verify the method hash

        // read and verify method args
        if (params != null) {
            for (int i = 0; i < params.length; i++) {
                Object expected = params[i];
                if (expected == null) {
                    assertion(mis.readObject() == null);
                } else {
                    Object actual =Util.unmarshalValue(expected.getClass(),mis);
                    if (expected.getClass().isArray()) {
                        //contents unchecked
                    } else {
                        assertion(expected.equals(actual));
                    }
                }
            }
        }

    }

}

