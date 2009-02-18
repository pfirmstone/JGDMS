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
import java.lang.reflect.Proxy;
import java.lang.reflect.InvocationHandler;
import java.util.logging.Logger;
import java.util.logging.Level;
import java.rmi.Remote;
import java.rmi.activation.ActivationID;
import com.sun.jini.qa.harness.QATest;
import net.jini.activation.ActivatableInvocationHandler;
import com.sun.jini.test.spec.activation.util.FakeActivationID;
import com.sun.jini.test.spec.activation.util.RemoteMethodSetInterface;
import com.sun.jini.test.spec.activation.util.MethodSetProxy;


/**
 * <pre>
 * Purpose:
 *   This test verifies the behavior of the
 *   {@link ActivatableInvocationHandler} class getCurrentProxy method
 *   during non exceptional call.
 *
 * Infrastructure:
 *   This test requires the following infrastructure:
 *     1) FakeActivationID
 *     2) RemoteMethodSetInterface
 *     3) MethodSetProxy
 *
 * Actions:
 *   Test performs the following steps:
 *       1) construct a FakeActivationID
 *       2) construct an ActivatableInvocationHandler object
 *          passing FakeActivationID and null as parameters
 *       3) call getCurrentProxy method from the created object
 *       4) assert the returned value is null
 *       5) construct a ActivatableInvocationHandler object
 *          passing FakeActivationID and FakeCurrentProxy as a parameters
 *       6) call getCurrentProxy method from the created object
 *       7) assert the returned value is the same as used in constructor
 *       8) construct a MethodSetProxy
 *       9) construct a FakeActivationID 
 *       10) construct an ActivatableInvocationHandler object
 *          passing FakeActivationID and MethodSetProxy as parameters
 *       11) construct fakeProxy with this handler
 *       12) construct a FakeActivationID with fakeProxy as parameter
 *       13) construct an ActivatableInvocationHandler object
 *          passing FakeActivationID and null as parameters
 *       14) construct proxy with this handler
 *       15) call some method from that proxy to activate the handler
 *          and update the underlying Proxy
 *       16) call getCurrentProxy method from the created object
 *       17) assert the returned value is equal to MethodSetProxy object
 * </pre>
 */
public class GetCurrentProxy_CallTest extends QATest {

    /**
     * This method performs all actions mentioned in class description.
     */
    public void run() throws Exception {
        ActivatableInvocationHandler handler;
        ActivationID aid = new FakeActivationID(logger);
        handler = new ActivatableInvocationHandler(aid, null);
        Object cp = handler.getCurrentProxy();
        assertion(cp == null);

        aid = new FakeActivationID(logger);
        MethodSetProxy fup = new MethodSetProxy(logger);
        handler = new ActivatableInvocationHandler(aid, fup);
        cp = handler.getCurrentProxy();
        assertion(fup == cp);

        fup = new MethodSetProxy(logger);
        aid = new FakeActivationID(logger);
        ActivatableInvocationHandler handler2 = new
                ActivatableInvocationHandler(aid, fup);
        RemoteMethodSetInterface fakeProxy = (RemoteMethodSetInterface)
                Proxy.newProxyInstance(
                        RemoteMethodSetInterface.class.getClassLoader(),
                        new Class[] {RemoteMethodSetInterface.class},
                        handler2);
        ActivationID aid2 = new FakeActivationID(logger, fakeProxy, true);
        // handler does not have an underlying proxy - activation will
        // be performed
        handler = new ActivatableInvocationHandler(aid2, null);
        RemoteMethodSetInterface fi = (RemoteMethodSetInterface)
                Proxy.newProxyInstance(
                        RemoteMethodSetInterface.class.getClassLoader(),
                        new Class[] {RemoteMethodSetInterface.class},
                        handler);
        fi.voidReturn();
        cp = handler.getCurrentProxy();
        assertion(cp.equals(fup));
    }
}
