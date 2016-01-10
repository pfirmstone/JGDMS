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

package org.apache.river.test.impl.end2end.jssewrapper;

import javax.security.auth.Subject;

import net.jini.io.MarshalledInstance;
import net.jini.jeri.Endpoint;
import net.jini.jeri.InboundRequest;
import net.jini.jeri.RequestDispatcher;
import net.jini.jeri.ServerEndpoint;
import net.jini.jeri.connection.OutboundRequestHandle;
import net.jini.jeri.connection.Connection;
import net.jini.core.constraint.InvocationConstraints;

import java.io.ObjectInputStream;
import java.io.Serializable;
import java.io.IOException;

import java.util.Collection;
import java.util.Iterator;
import java.util.ArrayList;

/**
 * A wrapper for the RequestHandler object. The handleRequest
 * call is intercepted, and the server side of the bridge is implemented there.
 * The Endpoint which listens for requests is passed here so that
 * the server side socket can be inspected by the bridge callback.
 */
class SecureRequestHandlerWrapper implements RequestDispatcher {

    RequestDispatcher handler;
    String className;

    SecureRequestHandlerWrapper(RequestDispatcher handler)
    {
        this.handler = handler;
        className = Util.getClassName(handler);
    }

    /* inherit javadoc */
    public void dispatch(InboundRequest request) {
        Statistics.increment(className, "handleRequest");
        Util.log(Util.CALLS, "Enter RequestHandler."
            + "handleRequest(handleRequest)");
        Util.log(Util.STACK);
        Util.log(Util.PARAMS, "parameter request: " + request);
        ReadCallback cb = null;
        try {
            ClassLoader cl = Thread.currentThread().getContextClassLoader();
            Thread.currentThread().setContextClassLoader(
                                   ClassLoader.getSystemClassLoader());
            ObjectInputStream ois =
            new ObjectInputStream(request.getRequestInputStream());
            cb = (ReadCallback) ((MarshalledInstance) ois.readObject()).get(false);
            Thread.currentThread().setContextClassLoader(cl);
        }
        catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException("Failure reading ReadCallback");
        }
        Bridge.readCallbackLocal.set(cb);
        if (cb != null) {
            cb.readCallback(request);
        }
        handler.dispatch(request);
        Util.log(Util.CALLS, "Leaving RequestHandler.handleRequest");
    }
}
