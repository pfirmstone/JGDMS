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
package com.sun.jini.test.spec.security.proxytrust.proxytrustinvocationhandler;

import java.util.logging.Level;

// java.lang
import java.lang.reflect.Method;

// net.jini
import net.jini.core.constraint.RemoteMethodControl;
import net.jini.security.proxytrust.ProxyTrust;
import net.jini.security.proxytrust.ProxyTrustInvocationHandler;

// com.sun.jini
import com.sun.jini.qa.harness.TestException;
import com.sun.jini.test.spec.security.proxytrust.util.AbstractTestBase;
import com.sun.jini.test.spec.security.proxytrust.util.ProxyTrustUtil;
import com.sun.jini.test.spec.security.proxytrust.util.Interface12Impl;
import com.sun.jini.test.spec.security.proxytrust.util.TestClassLoader;


/**
 * <pre>
 * Purpose
 *   This test verifies the following:
 *     If the specified method is Object.hashCode, invoke method of
 *     ProxyTrustInvocationHandler returns a hash code for the specified proxy
 *     object.
 *
 * Infrastructure
 *   This test requires the following infrastructure:
 *     Interface12Proxy - normal dynamic proxy class implementing TestInterface1
 *             and TestInterface2 interfaces
 *     ValidMainProxy - normal main proxy
 *     ValidBootProxy - normal boot proxy
 *     TestClassLoader - normal class loader
 *
 * Action
 *   The test performs the following steps:
 *     1) construct ProxyTrustInvocationHandler with ValidMainProxy and
 *        ValidBootProxy as parameters
 *     2) construct Interface12Proxy with constructed
 *        ProxyTrustInvocationHandler
 *     3) construct an instance of Interface12Proxy with the same
 *        ProxyTrustInvocationHandler and TestClassLoader (Interface12Proxy1)
 *     4) invoke 'invoke' method of constructed ProxyTrustInvocationHandler with
 *        Interface12Proxy and 'hashCode' method
 *     5) invoke 'invoke' method of constructed ProxyTrustInvocationHandler with
 *        Interface12Proxy1 and 'hashCode' method
 *     6) assert that values returned by 'invoke' methods will be equal
 * </pre>
 */
public class Invoke_hashCodeTest extends AbstractTestBase {

    /**
     * This method performs all actions mentioned in class description.
     *
     */
    public void run() throws Exception {
        RemoteMethodControl main = createValidMainProxy();
        ProxyTrust boot = createValidBootProxy();
        ProxyTrustInvocationHandler ptih = createPTIH(main, boot);
        Object proxy = ProxyTrustUtil.newProxyInstance(
                new Interface12Impl(), ptih);
        Method m = Object.class.getDeclaredMethod("hashCode", new Class[0]);
        TestClassLoader cl = new TestClassLoader();
        Object proxy1 = ProxyTrustUtil.newProxyInstance(
                new Interface12Impl(), ptih, cl);
        Object res = ptihInvoke(ptih, proxy, m, null);
        Object res1 = ptihInvoke(ptih, proxy, m, null);

        if (res != null && res1 != null && !res.equals(res1)) {
            // FAIL
            throw new TestException(
                    "'hashCode' method of constructed "
                    + "ProxyTrustInvocationHandler for " + proxy
                    + " returned " + res + " result, and for " + proxy1
                    + " - " + res1 + ", while equal non-null results "
                    + "were expected.");
        }

        // PASS
        logger.fine("'hashCode' method of constructed "
                + "ProxyTrustInvocationHandler returned equal hash codes "
                + "for equal proxies as expected.");
    }
}
