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
package org.apache.river.test.spec.jeri.basicinvocationdispatcher;

import java.util.logging.Level;

import org.apache.river.qa.harness.QATestEnvironment;
import org.apache.river.qa.harness.TestException;
import org.apache.river.qa.harness.QAConfig;
import org.apache.river.qa.harness.Test;

import net.jini.jeri.BasicInvocationDispatcher;
import net.jini.io.MarshalInputStream;
import net.jini.security.TrustVerifier;
import net.jini.security.proxytrust.ServerProxyTrust;
import net.jini.security.proxytrust.ProxyTrust;

import org.apache.river.test.spec.jeri.util.FakeBasicInvocationDispatcher;
import org.apache.river.test.spec.jeri.util.FakeInboundRequest;
import org.apache.river.test.spec.jeri.util.FakeServerCapabilities;
import org.apache.river.test.spec.jeri.util.FakeInvocationHandler;
import org.apache.river.test.spec.jeri.util.FakeArgument;
import org.apache.river.test.spec.jeri.util.FakeTrustVerifier;
import org.apache.river.test.spec.jeri.util.Util;

import java.util.Collection;
import java.util.ArrayList;
import java.lang.reflect.Proxy;
import java.lang.reflect.Method;
import java.rmi.Remote;
import java.rmi.RemoteException;
import java.io.IOException;
import java.io.ByteArrayInputStream;
import java.util.Arrays;
import java.util.logging.Level;

/**
 * <pre>
 * Purpose
 *   This test verifies the behavior of the BasicInvocationDispatcher.dispatch
 *   method during a normal, non-exceptional method call.
 *
 * Test Cases
 *   The test cases are defined by the Actions section below.
 *
 * Infrastructure
 *   This test requires the following infrastructure:
 *     1) FakeRemoteInterface
 *          -extends Remote, ServerProxyTrust
 *          -a class that defines these methods:
 *             void     voidReturn()                  throws RemoteException
 *             void     voidReturn(int i, Object o)   throws RemoteException
 *             void     voidReturn(int i)             throws RemoteException
 *             Object   objectReturn()                throws RemoteException
 *             Object   objectReturn(Object o, int i) throws RemoteException
 *             Object   objectReturn(int i)           throws RemoteException
 *             int      intReturn()                   throws RemoteException
 *             int      intReturn(int i, Object o)    throws RemoteException
 *             int      intReturn(int i)              throws RemoteException
 *             Object[] objectArrayReturn(Object[] o) throws RemoteException
 *             byte     byteReturn(byte b)            throws RemoteException
 *             long     longReturn(long l)            throws RemoteException
 *             double   doubleReturn(double d)        throws RemoteException
 *             boolean  booleanReturn(boolean b)      throws RemoteException
 *             char     charReturn(char c)            throws RemoteException
 *             short    shortReturn(short s)          throws RemoteException
 *             float    floatReturn(float f)          throws RemoteException
 *             TrustVerifier getProxyVerifier()       throws RemoteException
 *     2) FakeInvocationHandler
 *          -invoke method verifies the method hash and args match those passed
 *           in during construction; returns the specified object passed in
 *           during construction
 *     3) FakeInboundRequest
 *          -constructor takes four parameters (first 2 bytes, method hash,
 *           and arguments) and writes them to request stream
 *          -abort method does nothing
 *          -checkConstraints does nothing
 *          -checkPermissions does nothing
 *          -populateContext does nothing
 *          -getClientHost returns local host name
 *          -getRequestInputStream and getResponseOutputStream methods
 *           return streams created in constructor
 *     4) FakeServerCapabilities
 *          -checkConstraints method returns InvocationConstraints.EMPTY
 *     5) FakeBasicInvocationDispatcher
 *          -subclasses BasicInvocationDispatcher to give access to
 *           protected methods
 *     6) FakeArgument
 *          -extends Exception
 *          -if constructed with a read object exception (IOException,
 *           ClassNotFoundException, RuntimeException, or Error) then throw
 *           that exception in the custom readObject method
 *
 * Actions
 *   For each test case the test performs the following steps:
 *     1) construct a FakeServerCapabilities
 *     2) construct a BasicInvocationDispatcher, passing in the Collection of
 *        FakeRemoteInterface methods, FakeServerCapabilities,
 *        and null MethodConstraints and permission Class
 *     3) construct a FakeInvocationHandler, passing in the method,
 *        same array of method args and a return object
 *     4) create an implementation of FakeRemoteInterface by creating a 
 *        dynamic proxy for FakeRemoteInterface; the proxy uses 
 *        the FakeInvocationHandler
 *     5) construct a FakeInboundRequest, passing in method hash of
 *        FakeRemoteInterface's method
 *     6) call BasicInvocationDispatcher.dispatch with the FakeRemoteInterface
 *        proxy, FakeInboundRequest, and an empty context
 *     7) assert FakeInboundRequest response stream returns the byte 0x01
 *        and the proper return value
 * </pre>
 */
public class Dispatch_NormalReturnTest extends QATestEnvironment implements Test {

    protected int counter;
    protected ByteArrayInputStream response;
    protected ArrayList context;
    protected FakeInboundRequest request;
    protected long methodHash;
    protected FakeBasicInvocationDispatcher dispatcher;
    protected FakeInvocationHandler handler;
    protected Remote fi;

    public interface FakeRemoteInterface extends Remote, ServerProxyTrust {
        public void     voidReturn()                  throws RemoteException;
        public void     voidReturn(int i, Object o)   throws RemoteException;
        public void     voidReturn(int i)             throws RemoteException;
        public Object   objectReturn()                throws RemoteException;
        public Object   objectReturn(Object o, int i) throws RemoteException;
        public Object   objectReturn(int i)           throws RemoteException;
        public int      intReturn()                   throws RemoteException;
        public int      intReturn(int i, Object o)    throws RemoteException;
        public int      intReturn(int i)              throws RemoteException;
        public Object[] objectArrayReturn(Object[] o) throws RemoteException;
        public byte     byteReturn(byte b)            throws RemoteException;
        public long     longReturn(long l)            throws RemoteException;
        public double   doubleReturn(double d)        throws RemoteException;
        public boolean  booleanReturn(boolean b)      throws RemoteException;
        public char     charReturn(char c)            throws RemoteException;
        public short    shortReturn(short s)          throws RemoteException;
        public float    floatReturn(float f)          throws RemoteException;
        public TrustVerifier getProxyVerifier()       throws RemoteException;
    }

    private Collection methodCollection(Class[] c) {
        Collection ret = new ArrayList();
        for (int i = 0; i < c.length; i++) {
            Method[] m = c[i].getDeclaredMethods();
            for (int j = 0; j < m.length; j++) {
                ret.add(m[j]);
            }
        }
        return ret;
    }

    // inherit javadoc
    public Test construct(QAConfig sysConfig) throws Exception {
        // construct infrastructure needed by test
        counter = 1;
        context = new ArrayList();

        dispatcher = new FakeBasicInvocationDispatcher(
                methodCollection(new Class[] {FakeRemoteInterface.class}),
                new FakeServerCapabilities(null),           //serverCaps
                null,                                       //serverConstraints
                null,                                       //permClass
                null);                                      //classLoader

        handler = new FakeInvocationHandler();
        fi = (Remote) Proxy.newProxyInstance(
            FakeRemoteInterface.class.getClassLoader(),
            new Class[] { FakeRemoteInterface.class },
            handler);
        return this;
    }

    // inherit javadoc
    public void run() throws Exception {
        // i: int method arg
        // iclass: int.class
        // o: Object method arg
        // oclass: Object.class
        // params: method args in an Object array
        // rObj: return Object
        // rInt: return int
        // m: method
        int i; Object o; Object[] params; Object rObj; int rInt; Method m;
        Class iclass = int.class; Class oclass = Object.class;

        FakeArgument fa = new FakeArgument(null,null);
        Class fic = FakeRemoteInterface.class;

        logger.log(Level.FINE,"=================================");
        logger.log(Level.FINE,"test case 1: voidReturn()");
        logger.log(Level.FINE,"");
        m = fic.getMethod("voidReturn",null);
        initTestCase(m,null,null);
        dispatcher.dispatch(fi,request,context);
        check(request,m,null);

        logger.log(Level.FINE,"=================================");
        logger.log(Level.FINE,"test case 2: voidReturn(int,Object)");
        logger.log(Level.FINE,"");
        i = Integer.MAX_VALUE; o = fa; 
        params = new Object[] {new Integer(i),o};
        m = fic.getMethod("voidReturn",new Class[] {iclass,oclass});
        initTestCase(m,params,null);
        dispatcher.dispatch(fi,request,context);
        check(request,m,null);

        logger.log(Level.FINE,"=================================");
        logger.log(Level.FINE,"test case 3: voidReturn(int)");
        logger.log(Level.FINE,"");
        i = 0; params = new Object[] {new Integer(i)};
        m = fic.getMethod("voidReturn",new Class[] {iclass});
        initTestCase(m,params,null);
        dispatcher.dispatch(fi,request,context);
        check(request,m,null);

        logger.log(Level.FINE,"=================================");
        logger.log(Level.FINE,"test case 4: objectReturn()");
        logger.log(Level.FINE,"");
        rObj = fa;
        m = fic.getMethod("objectReturn",null);
        initTestCase(m,null,rObj);
        dispatcher.dispatch(fi,request,context);
        check(request,m,rObj);

        logger.log(Level.FINE,"=================================");
        logger.log(Level.FINE,"test case 5: objectReturn(Object,int)");
        logger.log(Level.FINE,"");
        i = Integer.MIN_VALUE; o = fa; 
        params = new Object[] {o,new Integer(i)}; rObj = fa;
        m = fic.getMethod("objectReturn",new Class[] {oclass,iclass});
        initTestCase(m,params,rObj);
        dispatcher.dispatch(fi,request,context);
        check(request,m,rObj);

        logger.log(Level.FINE,"=================================");
        logger.log(Level.FINE,"test case 6: objectReturn(int)");
        logger.log(Level.FINE,"");
        i = -1; params = new Object[] {new Integer(i)}; rObj = null;
        m = fic.getMethod("objectReturn",new Class[] {iclass});
        initTestCase(m,params,rObj);
        dispatcher.dispatch(fi,request,context);
        check(request,m,rObj);

        logger.log(Level.FINE,"=================================");
        logger.log(Level.FINE,"test case 7: intReturn()");
        logger.log(Level.FINE,"");
        rInt = 0; rObj = new Integer(rInt);
        m = fic.getMethod("intReturn",null);
        initTestCase(m,null,rObj);
        dispatcher.dispatch(fi,request,context);
        check(request,m,rObj);

        logger.log(Level.FINE,"=================================");
        logger.log(Level.FINE,"test case 8: intReturn(int,Object)");
        logger.log(Level.FINE,"");
        i = 23; o = null; params = new Object[] {new Integer(i),o}; 
        rInt = Integer.MIN_VALUE; rObj = new Integer(rInt);
        m = fic.getMethod("intReturn",new Class[] {iclass,oclass});
        initTestCase(m,params,rObj);
        dispatcher.dispatch(fi,request,context);
        check(request,m,rObj);

        logger.log(Level.FINE,"=================================");
        logger.log(Level.FINE,"test case 9: intReturn(int)");
        logger.log(Level.FINE,"");
        i = 1; params = new Object[] {new Integer(i)}; 
        rInt = Integer.MAX_VALUE; rObj = new Integer(rInt);
        m = fic.getMethod("intReturn",new Class[] {iclass});
        initTestCase(m,params,rObj);
        dispatcher.dispatch(fi,request,context);
        check(request,m,rObj);

        //////////////////////////////////////////////////////////

        logger.log(Level.FINE,"=================================");
        logger.log(Level.FINE,"test case 10: objectArrayReturn(Object[])");
        logger.log(Level.FINE,"");
        Object[] objectArray = new Object[] {fa};
        m = fic.getMethod("objectArrayReturn",
            new Class[] {objectArray.getClass()});
        initTestCase(m,new Object[] {objectArray},objectArray);
        dispatcher.dispatch(fi,request,context);
        check(request,m,objectArray);

        logger.log(Level.FINE,"=================================");
        logger.log(Level.FINE,"test case 11: byteReturn(byte)");
        logger.log(Level.FINE,"");
        Byte b = new Byte(Byte.MAX_VALUE);
        Byte rByte = new Byte(Byte.MIN_VALUE);
        m = fic.getMethod("byteReturn",new Class[] {byte.class});
        initTestCase(m,new Object[] {b},rByte);
        dispatcher.dispatch(fi,request,context);
        check(request,m,rByte);

        logger.log(Level.FINE,"=================================");
        logger.log(Level.FINE,"test case 12: longReturn(long)");
        logger.log(Level.FINE,"");
        Long l = new Long(Long.MAX_VALUE);
        Long rLong = new Long(Long.MIN_VALUE);
        m = fic.getMethod("longReturn",new Class[] {long.class});
        initTestCase(m,new Object[] {l},rLong);
        dispatcher.dispatch(fi,request,context);
        check(request,m,rLong);

        logger.log(Level.FINE,"=================================");
        logger.log(Level.FINE,"test case 13: doubleReturn(double)");
        logger.log(Level.FINE,"");
        Double d = new Double(Double.MAX_VALUE);
        Double rDouble = new Double(Double.MIN_VALUE);
        m = fic.getMethod("doubleReturn",new Class[] {double.class});
        initTestCase(m,new Object[] {d},rDouble);
        dispatcher.dispatch(fi,request,context);
        check(request,m,rDouble);

        logger.log(Level.FINE,"=================================");
        logger.log(Level.FINE,"test case 14: booleanReturn(boolean)");
        logger.log(Level.FINE,"");
        Boolean bo = new Boolean(true);
        Boolean rBoolean = new Boolean(false);
        m = fic.getMethod("booleanReturn",new Class[] {boolean.class});
        initTestCase(m,new Object[] {bo},rBoolean);
        dispatcher.dispatch(fi,request,context);
        check(request,m,rBoolean);

        logger.log(Level.FINE,"=================================");
        logger.log(Level.FINE,"test case 15: charReturn(char)");
        logger.log(Level.FINE,"");
        Character c = new Character('a');
        Character rCharacter = new Character('A');
        m = fic.getMethod("charReturn",new Class[] {char.class});
        initTestCase(m,new Object[] {c},rCharacter);
        dispatcher.dispatch(fi,request,context);
        check(request,m,rCharacter);

        logger.log(Level.FINE,"=================================");
        logger.log(Level.FINE,"test case 16: shortReturn(short)");
        logger.log(Level.FINE,"");
        Short s = new Short(Short.MAX_VALUE);
        Short rShort = new Short(Short.MIN_VALUE);
        m = fic.getMethod("shortReturn",new Class[] {short.class});
        initTestCase(m,new Object[] {s},rShort);
        dispatcher.dispatch(fi,request,context);
        check(request,m,rShort);

        logger.log(Level.FINE,"=================================");
        logger.log(Level.FINE,"test case 17: floatReturn(float)");
        logger.log(Level.FINE,"");
        Float f = new Float(Float.MAX_VALUE);
        Float rFloat = new Float(Float.MIN_VALUE);
        m = fic.getMethod("floatReturn",new Class[] {float.class});
        initTestCase(m,new Object[] {f},rFloat);
        dispatcher.dispatch(fi,request,context);
        check(request,m,rFloat);

        logger.log(Level.FINE,"=================================");
        logger.log(Level.FINE,"test case 18: getProxyVerifier()");
        logger.log(Level.FINE,"");
        TrustVerifier rVerifier = new FakeTrustVerifier();
        Method mSend = ProxyTrust.class.getMethod("getProxyVerifier",null);
        Method mReceive = fic.getMethod("getProxyVerifier",null);
        handler.init(null,mReceive,null,rVerifier);
        long methodHash = Util.computeMethodHash(mSend);
        request = new FakeInboundRequest(methodHash,null,0x00,0x00);
        dispatcher.dispatch(fi,request,context);
        check(request,mReceive,rVerifier);
    }

    // inherit javadoc
    public void tearDown() {
    }

    /**
     * Initializes FakeInvocationHandler that will be called by the dispatcher
     * and initializes FakeInboundRequest.
     *
     * @param method the method that will be invoked
     * @param args the arguments that will be passed to the method
     * @param returnObject the object that should be returned from
     *        the method call
     */
    private void initTestCase(Method method, Object[] args, 
        Object returnObject) throws IOException
    {
        handler.init(null,method,args,returnObject);
        long methodHash = Util.computeMethodHash(method);
        request = new FakeInboundRequest(methodHash,args,0x00,0x00);
    }

    /**
     * Verify that <code>request</code> received the correct response.
     * This method returns normally if the correct exception was thrown 
     * to this instance; otherwise a <code>TestException</code> is thrown.
     *
     * @param request the FakeInboundRequst receiving the response
     * @param returnObject the expected object to be returned
     *
     * @throws NullPointerException if <code>request</code> parameter is null
     * @throws TestException if the response is incorrect
     */
    private void check(FakeInboundRequest request, Method method, 
        Object returnObject) throws TestException
    {
        ByteArrayInputStream response = request.getResponseStream();

        // check first byte
        int first = response.read();
        assertion(first == 0x01, "Actual first response byte: " + first);

        // read returned object
        Object returned = null;
        try {
            MarshalInputStream mis = new MarshalInputStream(
                response, null, false, null, new ArrayList());
            returned = unmarshalValue(method.getReturnType(), mis);
            assertion(mis.read() == -1);
        } catch(Throwable t) {
            assertion(false,t.toString());
        }

        // check returned object
        if (returnObject == null) {
            assertion(returned == null,"Returned object: " + returned);
        } else if (returnObject.getClass().isArray()) {
            assertion(Arrays.equals(
                (Object[]) returnObject, (Object[]) returned) );
        } else {
            assertion(returnObject.equals(returned),
                "Expected: " + returnObject + ", received: " + returned);
        }
    }

    /**
     * Unmarshals an object of type <code>type</code> from
     * <code>in</code>.
     *
     * @return an object of type <code>type</code>
     */
    Object unmarshalValue(Class type, MarshalInputStream in)
        throws IOException, ClassNotFoundException
    {
        if (type == void.class) {
            return null;
        } else if (type == int.class) {
            return new Integer(in.readInt());
        } else if (type == boolean.class) {
            return new Boolean(in.readBoolean());
        } else if (type == byte.class) {
            return new Byte(in.readByte());
        } else if (type == char.class) {
            return new Character(in.readChar());
        } else if (type == short.class) {
            return new Short(in.readShort());
        } else if (type == long.class) {
            return new Long(in.readLong());
        } else if (type == float.class) {
            return new Float(in.readFloat());
        } else if (type == double.class) {
            return new Double(in.readDouble());
        } else {
            return in.readObject();
        }
    }

}

