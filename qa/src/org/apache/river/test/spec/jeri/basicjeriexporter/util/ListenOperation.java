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
import net.jini.jeri.InboundRequest;
import net.jini.jeri.RequestDispatcher;

//java.io
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.Serializable;


//java.util
import java.util.Collection;
import java.util.ArrayList;

public class ListenOperation implements Runnable {

    private String request;
    private String response;
    private RequestDispatcher rd = null;
    private volatile boolean stop = false;

    public ListenOperation(String request, String response,
        RequestDispatcher rd) {
        this.request = request;
        this.response = response;
        this.rd = rd;
    }

    public synchronized void close() {
        stop = true;
    }

    public synchronized boolean getStop() {
        return stop;
    }

    public void run() {
        boolean fileFound = false;
        while (!fileFound) {
            if (new File(request).length()>0) {
                Thread t = new Thread(new Processor(request, response));
                t.setDaemon(true);
                t.start();
                fileFound = true;
            }
            try {
                Thread.currentThread().sleep(1000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    private class Processor implements Runnable {

        private String request;
        private String response;

        public Processor(String request, String response) {
            this.request = request;
            this.response = response;
        }

        public void run() {
            rd.dispatch(new TestInboundRequest(request, response));
        }
    }

    private static class TestInboundRequest implements InboundRequest {

        private String request;
        private String response;
        private InputStream is;
        private OutputStream os;

        public TestInboundRequest(String request, String response) {
            this.request = request;
            this.response = response;
            try {
                is = new FileInputStream(request);
                os = new FileOutputStream(response);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        public void abort() {
            new File(response).delete();
        }

        public InvocationConstraints
            checkConstraints(InvocationConstraints constraints)
                throws UnsupportedConstraintException {
            return InvocationConstraints.EMPTY;
        }

        public void checkPermissions() {
        }

        public InputStream getRequestInputStream() {
            return is;
        }

        public OutputStream getResponseOutputStream() {
            return os;
        }

        public void populateContext(Collection context) {

        }
    }
}
