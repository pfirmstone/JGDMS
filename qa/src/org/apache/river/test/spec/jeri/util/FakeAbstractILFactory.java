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
package org.apache.river.test.spec.jeri.util;

import net.jini.jeri.AbstractILFactory;
import net.jini.jeri.InvocationDispatcher;
import net.jini.jeri.ObjectEndpoint;
import net.jini.jeri.ServerCapabilities;

import java.lang.reflect.InvocationHandler;
import java.util.Collection;
import java.rmi.Remote;
import java.rmi.server.ExportException;

/**
 * A concrete impl of AbstractILFactory used to
 * access protected methods of AbstractILFactory.
 * Implementations of abstract methods throw AssertionError.
 * <p>
 * Used by:
 * <ul>
 *   <li>org.apache.river.test.spec.jeri.abstractilfactory.AccessorTest
 *   <li>org.apache.river.test.spec.jeri.basicilfactory.ObjectMethodsTest
 * </ul>
 */
public class FakeAbstractILFactory extends AbstractILFactory {

    protected InvocationDispatcher createInvocationDispatcher(
        Collection methods, Remote impl, ServerCapabilities caps)
        throws ExportException
    {
        throw new AssertionError();
    }

    protected InvocationHandler createInvocationHandler(
        Class[] interfaces, Remote impl, ObjectEndpoint oe)
        throws ExportException
    {
        throw new AssertionError();
    }

    public Class[] getExtraProxyInterfaces(Remote impl)
        throws ExportException
    {
        return super.getExtraProxyInterfaces(impl);
    }

    public Collection getInvocationDispatcherMethods(Remote impl)
        throws ExportException
    {
        return super.getInvocationDispatcherMethods(impl);
    }

    public Class[] getProxyInterfaces(Remote impl)
        throws ExportException
    {
        return super.getProxyInterfaces(impl);
    }

    public Class[] getRemoteInterfaces(Remote impl)
        throws ExportException
    {
        return super.getRemoteInterfaces(impl);
    }

}
