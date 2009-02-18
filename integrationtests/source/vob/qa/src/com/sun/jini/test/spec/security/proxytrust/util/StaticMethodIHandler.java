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
package com.sun.jini.test.spec.security.proxytrust.util;

import java.util.logging.Level;

// java
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;

// net.jini
import net.jini.security.proxytrust.ProxyTrustIterator;


/**
 * Invocation handler having static method
 * 'ProxyTrustIterator getProxyTrustIterator()'.
 */
public class StaticMethodIHandler extends NonProxyStaticMethodObject
        implements InvocationHandler {

    /** impl for this invocation handler */
    protected Object impl;

    /**
     * Creates an instance with the specified implementation.
     *
     * @param impl implementation
     */
    public StaticMethodIHandler(Object impl) {
        super();
        this.impl = impl;
    }

    /**
     * Executes the specified method with the specified arguments on the
     * specified proxy, and returns the return value, if any.
     * Specially treat only Object.toString method.
     *
     * @param proxy the proxy object
     * @param method the method being invoked
     * @param args the arguments to the specified method
     * @return the value returned by executing the specified method on
     *         the specified proxy with the specified arguments, or null
     *        if the method has void return type
     * @throws Throwable the exception thrown by executing the specified
     *         method
     */
    public Object invoke(Object proxy, Method m, Object[] args)
            throws Throwable {
        Class decl = m.getDeclaringClass();

        if (decl == Object.class) {
            String name = m.getName();

            if (name.equals("toString")) {
                return "Proxy[ " + toString() + " ]";
            }
        }

        try {
            return m.invoke(impl, args);
        } catch (InvocationTargetException ite) {
            throw ite.getTargetException();
        } catch (IllegalAccessException iae) {
            throw new IllegalArgumentException().initCause(iae);
        }
    }
}
