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
/**
 * File-based transport implementation capable of handling a single
 * remote call per export
 */
package org.apache.river.test.spec.jeri.connection.util;

//jeri imports
import net.jini.core.constraint.InvocationConstraints;
import net.jini.io.UnsupportedConstraintException;
import net.jini.jeri.Endpoint;
import net.jini.jeri.ServerEndpoint;
import net.jini.jeri.OutboundRequest;
import net.jini.jeri.OutboundRequestIterator;
import net.jini.jeri.RequestDispatcher;

//java.io
import java.io.IOException;
import java.io.Serializable;

//java.lang.reflect
import java.lang.reflect.Method;

//java.net
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.UnknownHostException;

//java.util
import java.util.Random;

/**
 * Utility class that implements an instrumented ServerEndpoint
 */
public class TestServerEndpoint implements ServerEndpoint{

    private static String host;
    private int port = 0;
    private TestEndpoint te = null;

    static {
        try {
            host = InetAddress.getLocalHost().getHostAddress();
        } catch (UnknownHostException e) {
            e.printStackTrace();
        }
    }

    public TestServerEndpoint(int port) {
        this.port = port;
    }

    /**
     * Returns an instance of TestEndpoint
     *
     * @return TestEndpoint
     */
    public TestEndpoint getTestEndpoint() {
        return te;
    }

    //inherit javadoc
    public Endpoint enumerateListenEndpoints(ServerEndpoint.ListenContext ctx)
        throws IOException {
        Endpoint e = null;
        TestListenEndpoint tle = new TestListenEndpoint();
        ListenCookie cookie = ctx.addListenEndpoint(tle);
        if (cookie.equals(tle.getListenHandle().getCookie())) {
            e = new TestEndpoint(port, host);
        }
        te = (TestEndpoint) e;
        return e;
    }

    //inherit javadoc
    public InvocationConstraints checkConstraints(InvocationConstraints ics)
        throws UnsupportedConstraintException {
        if (!ics.isEmpty()){
            throw new UnsupportedConstraintException("This endpoint does not"
                + " support setting constraints on a remote call");
        }
        return InvocationConstraints.EMPTY;
    }

    /**
     * Implementation of ServerEndpoint.ListenEndpoint
     */
    private class TestListenEndpoint
        implements ServerEndpoint.ListenEndpoint {

        private TestListenHandle handle;

        //inherit javadoc
        public void checkPermissions() {

        }

        //inherit javadoc
        public synchronized ServerEndpoint.ListenHandle listen(
            RequestDispatcher dispatcher) throws IOException {
            ListenOperation listen = new ListenOperation(port,
                new TestDispatcher(dispatcher));
            handle = new TestListenHandle(listen);
            Thread t = new Thread(listen);
            t.setDaemon(true);
            t.start();
            if (!listen.waitForListen()) {
                System.out.println("Listen operation has not started");
            }
            return handle;
        }

        //inherit javadoc
        public synchronized TestListenHandle getListenHandle() {
            return handle;
        }

    }

    /**
     * Implementation of ServerEndpoint.ListenCookie
     */
    private static class TestListenCookie
        implements ServerEndpoint.ListenCookie {
        private Integer id = null;

        public TestListenCookie(Integer id) {
            this.id = id;
        }

        /**
         * Returns an id that identifies this cookie.
         *
         * @return Integer value identifying this cookie
         */
        public Integer getId() {
            return id;
        }

        /**
         * Compares TestListenCookie instances
         *
         * @param o instance of TestListenCookie
         * @return true if o is an instance of TestListenCookie and o's id
         * mathches the id of this object.
         */
        public boolean equals(Object o) {
            if (!(o instanceof TestListenCookie)) {
                return false;
            }
            TestListenCookie tlc = (TestListenCookie) o;
            return this.id.equals(tlc.id);
        }
    }

    /**
     * Implementation of ServerEndpoint.ListenHandle
     */
    private class TestListenHandle
        implements ServerEndpoint.ListenHandle {
        ListenOperation listen = null;
        TestListenCookie cookie = null;

        public TestListenHandle(ListenOperation listen) {
            this.listen = listen;
            cookie = new TestListenCookie(new Integer(listen.hashCode()));
        }

        //inherit javadoc
        public void close() {
            listen.close();
        }

        //inherit javadoc
        public ServerEndpoint.ListenCookie getCookie() {
            return cookie;
        }
    }

}
