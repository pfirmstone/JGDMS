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
import net.jini.jeri.OutboundRequest;
import net.jini.jeri.OutboundRequestIterator;

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

public class TestEndpoint implements Endpoint, Serializable {
    private String request;
    private String response;

    public TestEndpoint(String request, String response) {
        this.request = request;
        this.response = response;
    }

    public OutboundRequestIterator newRequest(InvocationConstraints ics) {
        return new TestOutboundRequestIterator(request, response, ics);
    }

    private static class TestOutboundRequestIterator
        implements OutboundRequestIterator {

        private String request;
        private String response;
        private boolean hasNext = true;
        private InvocationConstraints ics;

        public TestOutboundRequestIterator(String request, String response,
            InvocationConstraints ics){
            this.request = request;
            this.response = response;
            this.ics = ics;
            if (!ics.isEmpty()){
                hasNext = false;
            }
        }

        public synchronized boolean hasNext() {
            boolean temp = hasNext;
            hasNext = false;
            return temp;
        }

        public synchronized OutboundRequest next() throws IOException {
            if (!ics.isEmpty()) {
                throw new UnsupportedConstraintException("TestServerEndpoint"
                    + " does not support settting constraints on a call");
            }
            if (request!=null) {
                String temp = request;
                request = null;
                return new TestOutboundRequest(temp, response);
            } else {
                throw new IOException("There are no more OutboundRequests"
                    + " avaliable from this iterator");
            }
        }

    }

    private static class TestOutboundRequest implements OutboundRequest {
        private String request;
        private String response;
        private InputStream is;
        private OutputStream os;

        public TestOutboundRequest(String request, String response) {
            this.request = request;
            this.response = response;
            try {
                os = new FileOutputStream(request);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        synchronized public void abort(){
            new File(response).delete();
            new File(request).delete();
        }

        public boolean getDeliveryStatus() {
            return true;
        }

        synchronized public OutputStream getRequestOutputStream(){
            return os;
        }

        synchronized public InputStream getResponseInputStream() {
            try {
                while (new File(response).length()<=0) {
                    Thread.currentThread().sleep(1000);
                }
                if (is==null) {
                    is = new FileInputStream(response);
                }
            } catch (InterruptedException ie) {
                ie.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            }
            return is;
        }

        synchronized public InvocationConstraints getUnfulfilledConstraints() {
            return InvocationConstraints.EMPTY;
        }

        public void populateContext(Collection context) {

        }
    }
}
