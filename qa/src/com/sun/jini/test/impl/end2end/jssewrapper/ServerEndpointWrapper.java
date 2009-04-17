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

package com.sun.jini.test.impl.end2end.jssewrapper;

/* JAAS imports */
import javax.security.auth.Subject;

/* Overture imports */
import net.jini.io.UnsupportedConstraintException;
import net.jini.jeri.RequestDispatcher;
import net.jini.jeri.Endpoint;
import net.jini.jeri.ServerEndpoint;
import net.jini.core.constraint.InvocationConstraints;

import java.io.IOException;



/**
 * A wrapper for the ServerEndpoint object. All calls
 * are forwarded to the underlying object. No additional functionality
 * is provided other than incrementing statistics counters
 */
public final class ServerEndpointWrapper implements ServerEndpoint {

    private ServerEndpoint endpoint;
    private String className;

    /** Creates a wrapper for a ServerEndpoint. */
    public ServerEndpointWrapper(ServerEndpoint endpoint) {
        this.endpoint = endpoint;
        className = Util.getClassName(endpoint);
    }

    /* inherit javadoc */
    public int hashCode() {
        Statistics.increment(className, "hashCode");
        return endpoint.hashCode();
    }

    /* inherit javadoc */
    public boolean equals(Object object) {
        Statistics.increment(className, "equals");
        if (!(object instanceof ServerEndpointWrapper)) {
            return false;
        }
        return endpoint.equals(((ServerEndpointWrapper)object).endpoint);
    }

    /* inherit javadoc */
    public String toString() {
        Statistics.increment(className, "toString");
        return "ServerEndpointWrapper: " + endpoint.toString();
    }

    public Endpoint enumerateListenEndpoints(ServerEndpoint.ListenContext ctx)
        throws IOException {
        EndpointWrapper eWrapper = new EndpointWrapper(endpoint
            .enumerateListenEndpoints(new ContextWrapper(ctx)));
        return eWrapper;
    }

    /* inherit javadoc */
    public InvocationConstraints checkConstraints(
        InvocationConstraints constraints)
        throws UnsupportedConstraintException {
        Util.log(Util.CALLS,
            "Entering ServerEndpoint.checkConstraints(constraints)");
        Util.log(Util.STACK);
        Util.log(Util.PARAMS, "Parameter constraints: " + constraints);
        Statistics.increment(className, "supportsConstraints");
        InvocationConstraints b = endpoint.checkConstraints(constraints);
        Util.log(Util.RETURN, "Returning constraints: " + b);
        Util.log(Util.CALLS, "Leaving ServerEndpoint.checkConstraints");
        return b;
    }

    public static class ContextWrapper implements ServerEndpoint.ListenContext {

        private ServerEndpoint.ListenContext context;

        public ContextWrapper(ServerEndpoint.ListenContext context) {
            this.context = context;
        }

        public ServerEndpoint.ListenCookie addListenEndpoint(
            ServerEndpoint.ListenEndpoint endpoint) throws IOException {
            return context.addListenEndpoint(new LEWrapper(endpoint));
        }
    }

    public static class LEWrapper implements ServerEndpoint.ListenEndpoint {

        private ServerEndpoint.ListenEndpoint endpoint;

        public LEWrapper(ServerEndpoint.ListenEndpoint endpoint) {
            this.endpoint = endpoint;
        }

        /* inherit javadoc */
        public void checkPermissions() {
            Util.log(Util.CALLS, "Entering ServerEndpoint.checkPermissions()");
            Util.log(Util.STACK);
            Statistics.increment(Util.getClassName(endpoint),
                "checkPermissions");
            endpoint.checkPermissions();
            Util.log(Util.CALLS, "Leaving ServerEndpoint.listen");
        }

        /* inherit javadoc */
        public ServerEndpoint.ListenHandle listen(RequestDispatcher handler)
                                        throws IOException
        {
            Util.log(Util.CALLS, "Entering ListenEndpoint.listen()");
            Util.log(Util.STACK);
            Statistics.increment(Util.getClassName(endpoint), "listen");
            RequestDispatcher handlerWrapper =
                new SecureRequestHandlerWrapper(handler);
            ServerEndpoint.ListenHandle listener =
                endpoint.listen(handlerWrapper);
            Util.log(Util.RETURN, "Returning ListenHandle: "
                             + listener);
            Util.log(Util.CALLS, "Leaving ListenEndpoint.listen");
            return listener;
        }
    }
}
