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
package com.sun.jini.test.spec.activation.activatableinvocationhandler;

import java.util.logging.Level;
import net.jini.activation.ActivatableInvocationHandler;
import net.jini.jeri.BasicInvocationHandler;
import net.jini.jeri.ObjectEndpoint;
import net.jini.jeri.OutboundRequest;
import com.sun.jini.qa.harness.TestException;
import com.sun.jini.qa.harness.QATest;
import java.rmi.Remote;
import java.rmi.activation.ActivationID;
import java.lang.reflect.Proxy;
import java.lang.reflect.InvocationHandler;
import java.util.Arrays;
import java.util.logging.Logger;
import java.util.logging.Level;
import com.sun.jini.test.spec.activation.util.RemoteMethodSetInterface;
import com.sun.jini.test.spec.activation.util.FakeActivationID;
import com.sun.jini.test.spec.activation.util.MethodSetProxy;


/**
 * <pre>
 * Purpose
 *   This test verifies the behavior of the ActivatableInvocationHandler
 *   class invoke method during a non-exceptional method
 *   invocation (except equals, hashCode, or toString).
 *
 * Infrastructure
 *   This test requires the following infrastructure:
 *     1) RemoteMethodSetInterface
 *     5) MethodSetProxy
 *
 * Actions
 *   The test performs the following steps:
 *     1) construct a FakeActivationID and MethodSetProxy
 *     2) construct a ActivatableInvocationHandler, passing in the
 *        FakeActivationID and MethodSetProxy
 *     3) construct a proxy with interface RemoteMethodSetInterface
 *        and this handler
 *     4) construct a FakeActivationID which will return this proxy
 *     5) construct a ActivatableInvocationHandler, passing in the
 *        last FakeActivationID and null
 *     6) for each method in RemoteMethodSetInterface do the following:
 *        a) pass MethodSetProxy the parameter if needed
 *        c) invoke each method on dynamic proxy with methodArgs
 *        d) assert the valid value is returned
 * </pre>
 */
public class Invoke_MethodsWithActivationTest extends QATest {

    /**
     * This method performs all actions mentioned in class description.
     */
    public void run() throws Exception {
        int i;
        Object o;

        ActivationID aid = new FakeActivationID(logger);
        MethodSetProxy fup = new MethodSetProxy(logger);
        InvocationHandler handler2 = new ActivatableInvocationHandler(aid,
                fup);
        RemoteMethodSetInterface fakeProxy = (RemoteMethodSetInterface)
                Proxy.newProxyInstance(
                        RemoteMethodSetInterface.class.getClassLoader(),
                        new Class[] {RemoteMethodSetInterface.class},
                        handler2);
        ActivationID aid2 = new FakeActivationID(logger, fakeProxy, true);
        InvocationHandler handler = new ActivatableInvocationHandler(aid2,
                null);
        RemoteMethodSetInterface fi = (RemoteMethodSetInterface)
                Proxy.newProxyInstance(
                        RemoteMethodSetInterface.class.getClassLoader(),
                        new Class[] {RemoteMethodSetInterface.class},
                        handler);

        fup.setInt(100);
        fi.voidReturn();
        assertion(fi.intReturn() == 110);

        fup.setInt(200);
        i = 1000;
        o = new Object();
        fi.voidReturn(i, o);
        assertion(fi.intReturn() == 1220);

        fup.setInt(300);
        i = 2000;
        fi.voidReturn(i);
        assertion(fi.intReturn() == 2330);

        o = new Object();
        fup.setObject(o);
        assertion(fi.objectReturn().equals(o));

        i = Integer.MIN_VALUE;
        fup.setInt(i);
        o = new Object();
        fup.setObject(o);
        assertion(fi.objectReturn(o, i).equals(o));

        i = i + 1;
        assertion(fi.objectReturn(i).equals(new Integer(i)));

        i = i + 1;
        fup.setInt(i);
        assertion(fi.intReturn() == i);

        i = i + 1;
        o = new Object();
        assertion(fi.intReturn(i, o) == i);

        i = i + 1;
        assertion(fi.intReturn(i) == i);
        Object[] objectArray = new Object[] {
            o, fup };
        assertion(Arrays.equals(fi.objectArrayReturn(objectArray),
                objectArray));

        byte b = Byte.MAX_VALUE;
        assertion(fi.byteReturn(b) == b);

        long l = Long.MAX_VALUE;
        assertion(fi.longReturn(l) == l);

        double d = Double.MAX_VALUE;
        assertion(fi.doubleReturn(d) == d);

        boolean bo = true;
        assertion(fi.booleanReturn(bo) == bo);

        char c = 'a';
        assertion(fi.charReturn(c) == c);

        short s = Short.MAX_VALUE;
        assertion(fi.shortReturn(s) == s);

        float f = Float.MAX_VALUE;
        assertion(fi.floatReturn(f) == f);
    }
}
