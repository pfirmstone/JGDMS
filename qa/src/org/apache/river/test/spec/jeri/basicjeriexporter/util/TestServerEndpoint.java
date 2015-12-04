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
package org.apache.river.test.spec.jeri.basicjeriexporter.util;

//jeri imports
import net.jini.core.constraint.InvocationConstraints;
import net.jini.io.UnsupportedConstraintException;
import net.jini.jeri.Endpoint;
import net.jini.jeri.ServerEndpoint;
import net.jini.jeri.OutboundRequest;
import net.jini.jeri.OutboundRequestIterator;
import net.jini.jeri.RequestDispatcher;

//java.io
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.Serializable;

//java.lang.reflect
import java.lang.reflect.Method;

//java.util
import java.util.Random;
import java.util.Date;


public class TestServerEndpoint implements ServerEndpoint{

    private static String request;
    private static String response;
    private static String dir;
    private static Random random = new Random(new Date().getTime());

    public TestServerEndpoint() {
        dir = System.getProperty("java.io.tmpdir") + File.separator +
            "jeriTest";
        request = dir + File.separator + "request" + random.nextInt();
        response = dir + File.separator + "response" + random.nextInt();
        try {
            if (new File(dir).mkdirs()){
                System.out.println("The test transport successfully created"
                    + " " + dir);
            } else {
                System.out.println("The test transport was unable to create"
                    + " " + dir);
            }
            if (new File(request).createNewFile()) {
                System.out.println("Test transport created file " + request);
            } else {
                System.out.println("Test transport failed to create "
                    + request);
            }
            if (new File(response).createNewFile()){
                System.out.println("Test transport created file " + response);
            } else {
               System.out.println("Test transport failed to create "
                    + response);
            }

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public Endpoint enumerateListenEndpoints(ServerEndpoint.ListenContext ctx)
        throws IOException {
        Endpoint e = null;
        TestListenEndpoint tle = new TestListenEndpoint(request, response);
        ListenCookie cookie = ctx.addListenEndpoint(tle);
        if (cookie.equals(tle.getListenHandle().getCookie())) {
            e = new TestEndpoint(request, response);
        }
        return e;
    }

    public InvocationConstraints checkConstraints(InvocationConstraints ics)
        throws UnsupportedConstraintException {
        if (!ics.isEmpty()){
            throw new UnsupportedConstraintException("This endpoint does not"
                + " support setting constraints on a remote call");
        }
        return InvocationConstraints.EMPTY;
    }

    private static class TestListenEndpoint
        implements ServerEndpoint.ListenEndpoint {

        private TestListenHandle handle;
        private String request;
        private String response;

        public TestListenEndpoint(String request, String response) {
            this.request = request;
            this.response = response;
        }

        public void checkPermissions() {

        }

        public synchronized ServerEndpoint.ListenHandle listen(
            RequestDispatcher dispatcher) throws IOException {
            ListenOperation listen = new ListenOperation(request, response,
                dispatcher);
            handle = new TestListenHandle(listen);
            Thread t = new Thread(listen);
            t.setDaemon(true);
            t.start();
            return handle;
        }

        public synchronized TestListenHandle getListenHandle() {
            return handle;
        }

    }

    private static class TestListenCookie
        implements ServerEndpoint.ListenCookie {
        private Integer id = null;

        public TestListenCookie(Integer id) {
            this.id = id;
        }

        public Integer getId() {
            return id;
        }

        public boolean equals(Object o) {
            if (!(o instanceof TestListenCookie)) {
                return false;
            }
            TestListenCookie tlc = (TestListenCookie) o;
            return this.id.equals(tlc.id);
        }
    }

    private static class TestListenHandle
        implements ServerEndpoint.ListenHandle {
        ListenOperation listen = null;
        TestListenCookie cookie = null;

        public TestListenHandle(ListenOperation listen) {
            this.listen = listen;
            cookie = new TestListenCookie(new Integer(listen.hashCode()));
        }

        public void close() {
            listen.close();
            new File(request).delete();
            new File(response).delete();
            new File(dir).delete();
            System.out.println("The test transport deleted " + request);
            System.out.println("The test transport deleted " + response);
            System.out.println("The test transport deleted " + dir);
            //Instrumentation
            TransportListener listener = BJETransportListener.getListener();
            try {
                Method m = this.getClass().getMethod("close",
                    new Class[] {});
                listener.called(m,this,new Object[] {});
            } catch (NoSuchMethodException e){
                e.printStackTrace();
            }
        }

        public ServerEndpoint.ListenCookie getCookie() {
            return cookie;
        }
    }
}
