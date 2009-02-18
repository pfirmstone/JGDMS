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
import net.jini.jeri.InboundRequest;
import net.jini.jeri.InvocationDispatcher;

//java.rmi
import java.rmi.Remote;

//java.lang.reflect
import java.lang.reflect.Method;

//import java.security
import java.security.AccessControlContext;
import java.security.AccessController;

import java.util.Collection;


/**
 * Instrumented InvocationDispatcher that delegates to an underlying
 * InvocationDispatcher
 */
public class BJETestDispatcher implements InvocationDispatcher {

    private InvocationDispatcher dispatcher;

    public BJETestDispatcher(InvocationDispatcher dispatcher) {
        this.dispatcher = dispatcher;
    }

    public void dispatch(Remote impl,InboundRequest call, Collection ctx) {
        TransportListener listener = BJETransportListener.getListener();
        if (listener!=null){
            try {
                Method m = this.getClass().getMethod("dispatch", new Class[] {
                    Remote.class, InboundRequest.class, Collection.class});
                AccessControlContext context = AccessController.getContext();
                ClassLoader cl = Thread.currentThread().getContextClassLoader();
                listener.called(m,this,new Object[] {impl,call,context,cl});
            } catch (NoSuchMethodException e){
                BJETransportListener.getUtilLog().warning("Something is"
                    +" really wrong - a method from this class is not"
                    + "found in this class: " + e.getMessage());
                e.printStackTrace();
            }
        }
        dispatcher.dispatch(impl, call, ctx);
    }
}
