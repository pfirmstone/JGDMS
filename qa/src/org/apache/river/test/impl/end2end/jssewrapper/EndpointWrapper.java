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

import java.rmi.MarshalledObject;
import net.jini.core.constraint.InvocationConstraints;
import net.jini.jeri.Endpoint;
import net.jini.jeri.OutboundRequest;
import net.jini.jeri.OutboundRequestIterator;
import net.jini.jeri.connection.OutboundRequestHandle;
import net.jini.jeri.connection.Connection;

import net.jini.security.proxytrust.TrustEquivalence;

import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.io.IOException;

import java.util.Collection;
import java.util.Iterator;
import java.util.ArrayList;
import java.util.List;


/**
 * A wrapper for the Endpoint object.
 */
public class EndpointWrapper implements Endpoint, Serializable, TrustEquivalence {

    Endpoint endpoint;
    String className;
    private static Throwable lastException = null;

    EndpointWrapper(Endpoint endpoint) {
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
        if (!(object instanceof EndpointWrapper)) {
            return false;
        }
        return endpoint.equals(((EndpointWrapper)object).endpoint);
    }

    /* inherit javadoc */
    public String toString() {
        Statistics.increment(className, "toString");
        return "EndpointWrapper: " + endpoint.toString();
    }

    /**
     * Method to return the last exception encountered in the wrapped iterator
     * @return last exception thrown by the wrapped iterator
     */
     public static Throwable getLastEndpointException() {
         return lastException;
     }

    /**
     * Wrapper delegate for the <code>Endpoint.newRequest</code>
     * method. The call is forwarded to the wrapped endpoint, and
     * the returned <code>OutboundRequest</code> and the
     * given context are used to call the <code>WriteCallback</code>
     * object obtained from the bridge, if any. Either the
     * returned <code>ReadCallback</code> object or null are wrapped
     * in a <code>MarshalledObject</code> and written to the output
     * stream returned by the <code>OutboundRequest</code> object.
     *
     * @param constraints The <code>InvocationConstraints</code> intended 
     *      for the wrapped endpoint
     * @return the <code>OutboundRequestIterator</code> obtained from the
     *      wrapped endpoint
     */
    public OutboundRequestIterator newRequest(InvocationConstraints constraints)
    {
        Util.log(Util.PARAMS, "parameter constraints: " + constraints);
        Statistics.increment(className, "newRequest");
        return
            new WrapperIterator(endpoint.newRequest(constraints),constraints);
    }

    public boolean checkTrustEquivalence(Object obj) {
        if (obj == this) {
            return true;
        } else if (!(obj instanceof EndpointWrapper)) {
            return false;
        }
        EndpointWrapper ew = (EndpointWrapper) obj;
        return ((TrustEquivalence)endpoint).checkTrustEquivalence(ew.endpoint);
    }

    public static class WrapperIterator implements OutboundRequestIterator {
        private int index = 0;
        private int end = 0;
        private final InvocationConstraints constraints;
        private final List requests = new ArrayList();

        public WrapperIterator(OutboundRequestIterator it,
            InvocationConstraints constraints ) {
            while (it.hasNext()) {
                try {
                    requests.add(it.next());
                } catch (IOException e) {
                    lastException = e;
                }
            }
            end = requests.size() -1;
            this.constraints = constraints;
        }

        public boolean hasNext() {
            return !(index>end);
        }

        public OutboundRequest next() throws IOException {
            Util.log(Util.CALLS,"Enter "
              + "Endpoint.newRequest(connectionEndpoint,context)");
            Util.log(Util.STACK);
            ReadCallback rcb = null;
            WriteCallback wcb = (WriteCallback) Bridge.writeCallbackLocal.get();
            OutboundRequest or = (OutboundRequest) requests.get(index);
            if (wcb != null) {
                rcb = wcb.writeCallback(or, constraints);
            }
            ObjectOutputStream oos =
                new ObjectOutputStream(or.getRequestOutputStream());
            oos.writeObject(new MarshalledObject(rcb));
            oos.flush();
            Util.log(Util.RETURN, "returned OutboundRequest: " + or);
            Util.log(Util.CALLS, "Leaving Endpoint.newRequest");
            index++;
            return or;
        }
    }
}
