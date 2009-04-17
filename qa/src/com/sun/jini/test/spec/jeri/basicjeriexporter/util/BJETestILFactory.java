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
package com.sun.jini.test.spec.jeri.basicjeriexporter.util;

//overture imports
import net.jini.jeri.BasicILFactory;
import net.jini.jeri.InvocationDispatcher;
import net.jini.jeri.InvocationLayerFactory;
import net.jini.jeri.ObjectEndpoint;
import net.jini.jeri.ServerCapabilities;


//java.lang.reflect
import java.lang.reflect.InvocationHandler;

//java.rmi
import java.rmi.Remote;

//java.rmi.server
import java.rmi.server.ExportException;

//java.lang.reflect
import java.lang.reflect.Method;

//import java.security
import java.security.AccessControlContext;
import java.security.AccessController;

// import java.util
import java.util.Collection;

/**
 * Instrumented InvocationLayerFactory
 */
public class BJETestILFactory extends BasicILFactory {


    public InvocationHandler createInvocationHandler(Class[] interfaces,
        Remote impl, ObjectEndpoint oe) throws ExportException {
        TransportListener listener = BJETransportListener.getListener();
        if (listener!=null){
            try {
                Method m = this.getClass().getMethod("createInvocationHandler",
                    new Class[] {Class[].class, Remote.class,
                        ObjectEndpoint.class});
                listener.called(m,this,new Object[] {impl,oe});
            } catch (NoSuchMethodException e){
                BJETransportListener.getUtilLog().warning("Something is"
                    +" really wrong - a method from this class is not"
                    + "found in this class: " + e.getMessage());
                    e.printStackTrace();
            }
        }
       return super.createInvocationHandler(interfaces,impl,oe);
    }

    public InvocationDispatcher createInvocationDispatcher(
        Collection interfaces, Remote impl, ServerCapabilities caps)
        throws ExportException {
        TransportListener listener = BJETransportListener.getListener();
        if (listener!=null){
            try {
                Method m = this.getClass().getMethod(
                    "createInvocationDispatcher",
                    new Class[] {Collection.class, Remote.class,
                        ServerCapabilities.class});
                AccessControlContext ctx = AccessController.getContext();
                ClassLoader cl = Thread.currentThread().getContextClassLoader();
                listener.called(m,this,new Object[] {interfaces,impl,ctx,cl});
            } catch (NoSuchMethodException e){
                BJETransportListener.getUtilLog().warning("Something is"
                    +" really wrong - a method from this class is not"
                    + "found in this class: " + e.getMessage());
                e.printStackTrace();
            }
        }
        InvocationDispatcher dispatcher = super.createInvocationDispatcher(
            interfaces,impl,caps);
        return new BJETestDispatcher(dispatcher);
    }
}
