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
import java.util.Arrays;
import java.util.logging.Level;
import java.lang.reflect.Proxy;
import net.jini.activation.ActivatableInvocationHandler;
import com.sun.jini.qa.harness.TestException;
import com.sun.jini.qa.harness.QATestEnvironment;
import com.sun.jini.qa.harness.Test;
import com.sun.jini.test.spec.activation.util.FakeActivationID;


/**
 * <pre>
 * Purpose
 *   This test verifies the behavior of the ActivatableInvocationHandler.invoke
 *   method when equals, hashCode, or toString methods are passed to the
 *   invoke method.
 *
 *   This test verifies the behavior of the ActivatableInvocationHandler
 *   method when equals, hashCode, or toString methods are called on
 *   an instance of ActivatableInvocationHandler.
 *
 * Test Cases
 *   This test iterates over a 3-tuple.  Each 3-tuple
 *   denotes one test case and is defined by the variables:
 *     Class[] proxy1Interfaces
 *     Class[] proxy2Interfaces
 *     boolean equalInvocationHandlers
 *
 * Infrastructure
 *   This test requires the following infrastructure:
 *     1) FakeInterface1 and FakeInterface2
 *          -two top-level interfaces, each declaring one method
 *     2) FakeSubInterface1
 *          -a sub-interface of FakeInterface1 which overloads it's method
 *     3) FakeActivationID
 *
 * Actions
 *   For each test case the test performs the following steps:
 *     1) construct two FakeActivationID
 *     2) constructs two ActivatableInvocationHandlers, passing in a different
 *        FakeActivationID to each
 *     3) create a dynamic proxy that implements the proxy1Interfaces and
 *        uses the first ActivatableInvocationHandler
 *     4) create a dynamic proxy that implements the proxy2Interfaces and
 *        uses the second ActivatableInvocationHandler
 *
 *     5) if   the proxies implement the same interfaces in the same order and
 *             equalInvocationHandlers is true
 *        then verify that both proxies are .equals to each other
 *             (symmetric) and .equals to themselves (reflexive)
 *        else verify that neither proxy is .equals to the other
 *     6) if   the proxies are .equals
 *        then verify they return the same hash code
 *     7) verify toString methods of the proxies return non-null String objects
 *
 *     8) if   equalInvocationHandlers is true
 *        then verify that both invocation handlers are .equals to each
 *             other (symmetric) and .equals to themselves (reflexive)
 *        else verify that neither invocation handler is .equals to the other
 *     9) if   the invocation handlers are .equals
 *        then verify they return the same hash code
 *    10) verify toString methods of the invocation handlers return
 *        non-null String objects
 *
 *   Note: steps 5 to 7 are for ActivatableInvocationHandler.invoke
 *         steps 8 to 10 are for ActivatableInvocationHandler
 * </pre>
 */
public class Invoke_ObjectMethodsTest extends QATestEnvironment implements Test {


    interface FakeInterface1 {

        public Object fake1Method()
                throws Throwable;
    }


    interface FakeSubInterface1 extends FakeInterface1 {

        public Object fake1Method()
                throws Throwable;
    }


    interface FakeInterface2 {

        public Object fake2Method()
                throws Throwable;
    }
    Class fi1 = FakeInterface1.class;
    Class fsi1 = FakeSubInterface1.class;
    Class fi2 = FakeInterface2.class;
    Boolean t = Boolean.TRUE;
    Boolean f = Boolean.FALSE;

    Object[][] cases = {
        // same proxy interfaces
            { new Class[] { }, 
              new Class[] { }, 
              t }, 
            { new Class[] { fi1 }, 
              new Class[] { fi1 }, 
              t }, 
            { new Class[] { fsi1 }, 
              new Class[] { fsi1 }, 
              t }, 
            { new Class[] { fi2 }, 
              new Class[] { fi2 }, 
              t }, 
            { new Class[] { fi1, fi2 }, 
              new Class[] { fi1, fi2 }, 
              t }, 
            { new Class[] { fi2, fsi1 }, 
              new Class[] { fi2, fsi1 }, 
              t }
              ,
        // different proxy interfaces
            { new Class[] { }, 
              new Class[] { fi1 }, 
              f }, 
            { new Class[] { fi1 }, 
              new Class[] { fi2 }, 
              f }, 
            { new Class[] { fsi1 }, 
              new Class[] { fi1 }, 
              f }, 
            { new Class[] { fi1, fi2 }, 
              new Class[] { fi2, fi1 }, 
              f }, 
            { new Class[] { fsi1, fi2 }, 
              new Class[] { fi2 }, 
              f }, 
            { new Class[] { fsi1, fi2 }, 
              new Class[] { fi1, fi2 }, 
              f }
    };

    /**
     * This method performs all actions mentioned in class description.
     */
    public void run() throws Exception {
        for (int i = 0; i < cases.length; i++) {
            logger.log(Level.FINEST, "test case: " + i);
            Class[] proxy1Interfaces = (Class[]) cases[i][0];
            Class[] proxy2Interfaces = (Class[]) cases[i][1];
            boolean equalInvocationHandlers =
                    ((Boolean) cases[i][2]).booleanValue();

            // create a dynamic proxy for proxy1Interfaces
            FakeActivationID aid = new FakeActivationID(null);
            ActivatableInvocationHandler handler1 = new
                    ActivatableInvocationHandler(aid, null);
            Object proxy1 = Proxy.newProxyInstance(
                    this.getClass().getClassLoader(),
                    proxy1Interfaces,
                    handler1);

            // create a dynamic proxy for proxy2Interfaces
            FakeActivationID aid2 = aid;

            if (!equalInvocationHandlers) {
                aid2 = new FakeActivationID(null);
            }
            ActivatableInvocationHandler handler2 = new
                    ActivatableInvocationHandler(aid2, null);
            Object proxy2 = Proxy.newProxyInstance(
                    this.getClass().getClassLoader(),
                    proxy2Interfaces,
                    handler2);

            // verify ActivatableInvocationHandler.invoke equals, hashCode,
            // and toString methods
            if (equalInvocationHandlers
                    && Arrays.equals(proxy1Interfaces, proxy2Interfaces)) {
                assertion(proxy1.equals(proxy2));
                assertion(proxy2.equals(proxy1));
                assertion(proxy1.equals(proxy1));
                assertion(proxy2.equals(proxy2));
                assertion(proxy1.hashCode() == proxy2.hashCode());
            } else {
                assertion(!proxy1.equals(proxy2));
                assertion(!proxy2.equals(proxy1));
            }
            assertion(proxy1.toString() != null);
            assertion(proxy2.toString() != null);

            // verify ActivatableInvocationHandler equals, hashCode,
            // and toString methods
            if (equalInvocationHandlers) {
                assertion(handler1.equals(handler2));
                assertion(handler2.equals(handler1));
                assertion(handler1.equals(handler1));
                assertion(handler2.equals(handler2));
                assertion(handler1.hashCode() == handler2.hashCode());
            } else {
                assertion(!handler1.equals(handler2));
                assertion(!handler2.equals(handler1));
                assertion(!handler1.equals(new Object()));
            }
            assertion(handler1.toString() != null);
            assertion(handler2.toString() != null);
        }
    }
}
