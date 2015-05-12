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
package org.apache.river.test.spec.activation.activatableinvocationhandler;

import java.util.logging.Level;
import net.jini.activation.ActivatableInvocationHandler;
import net.jini.jeri.BasicInvocationHandler;
import net.jini.jeri.ObjectEndpoint;
import net.jini.jeri.OutboundRequest;
import org.apache.river.qa.harness.TestException;
import org.apache.river.qa.harness.QATestEnvironment;
import org.apache.river.qa.harness.Test;
import java.lang.reflect.Proxy;
import java.lang.reflect.InvocationHandler;
import java.util.Arrays;
import java.util.logging.Logger;
import java.util.logging.Level;
import org.apache.river.test.spec.activation.util.FakeActivationID;
import org.apache.river.test.spec.activation.util.RemoteMethodSetInterface;
import org.apache.river.test.spec.activation.util.MethodSetProxy;


/**
 * <pre>
 * Purpose
 *   This test verifies the behavior of the ActivatableInvocationHandler.invoke
 *   method during a non-exceptional method invocation (except equals,
 *   hashCode, or toString) in case when underlying proxy is valid and no
 *   activation is performed.
 *
 * Infrastructure
 *   This test requires the following infrastructure:
 *     1) RemoteMethodSetInterface
 *     5) MethodSetProxy
 *
 * Actions
 *   The test performs the following steps:
 *     1) construct a MethodSetProxy
 *     2) construct a ActivatableInvocationHandler, passing in the
 *        MethodSetProxy
 *     3) for each method in RemoteMethodSetInterface do the following:
 *        a) pass MethodSetProxy the parameter if needed
 *        c) invoke each method on dynamic proxy with methodArgs
 *        d) assert the return value is returned
 * </pre>
 */
public class Invoke_MethodsNoActivationTest extends QATestEnvironment implements Test {

    /**
     * This method performs all actions mentioned in class description.
     */
    public void run() throws Exception {
        int i;
        Object o;

        FakeActivationID aid = new FakeActivationID(null);
        MethodSetProxy fup = new MethodSetProxy(logger);
        InvocationHandler handler =
                new ActivatableInvocationHandler(aid, fup);
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
