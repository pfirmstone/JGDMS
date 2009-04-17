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
package com.sun.jini.test.spec.jeri.connection.util;

//jeri imports
import net.jini.jeri.connection.Connection;
import net.jini.jeri.connection.ConnectionEndpoint;
import net.jini.jeri.connection.ConnectionManager;
import net.jini.jeri.connection.OutboundRequestHandle;
import net.jini.core.constraint.InvocationConstraints;
import net.jini.io.UnsupportedConstraintException;
import net.jini.jeri.Endpoint;
import net.jini.jeri.OutboundRequest;
import net.jini.jeri.OutboundRequestIterator;

//java.io
import java.io.InputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.Serializable;

//java.nio
import java.nio.channels.SocketChannel;

//java.net
import java.net.Socket;

//java.util
import java.util.Collection;
import java.util.Iterator;

public class TestEndpoint implements Endpoint, Serializable, ConnectionEndpoint
{
    private String host;
    private int port;
    private TestConnection testConnection = new TestConnection();
    private ConnectionManager manager = null;
    private String exceptionMethod = null;

    public TestEndpoint(int port, String host) {
        this.port = port;
        this.host = host;
        manager = new ConnectionManager(this);

    }

    /**
     * Set a method in which to throw an exception
     *
     * @param methodName The name of the method in which to throw the
     * exception
     */
    public void setException(String methodName) {
        exceptionMethod = methodName;
    }

    //inherit javadoc
    public OutboundRequestIterator newRequest(InvocationConstraints ics) {
        return manager.newRequest(testConnection);
    }

    //inherit javadoc
    public Connection connect(OutboundRequestHandle handle) {
        ConnectionTransportListener.getListener().called("connect1");
        return (Connection)handle;
    }

    //inherit javadoc
    public Connection connect(OutboundRequestHandle handle, Collection active,
        Collection idle) {
        ConnectionTransportListener.getListener().called("connect2");
        Connection connection = null;
        Iterator it = active.iterator();
        connection = it.hasNext()?(Connection)it.next():null;
        if (connection==null) {
            it = idle.iterator();
            connection = it.hasNext()?(Connection)it.next():null;
        }
        if ((exceptionMethod!=null)&&(exceptionMethod.equals("connect"))) {
            throw new RuntimeException("Bogus Exception");
        }
        return connection;
    }


    //Implementation of Connection interface
    private class TestConnection implements Connection, OutboundRequestHandle {

        private InputStream is = null;
        private OutputStream os = null;
        private Socket s = null;

        //inherit javadoc
        public void close(){
            if (s!=null) {
                try {
                    s.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }

        //inherit javadoc
        public synchronized SocketChannel getChannel() {
            return null;
        }

        //inherit javadoc
        public synchronized InputStream getInputStream() throws IOException {
            if (s==null) {
                s = new Socket(host,port);
                is = s.getInputStream();
                os = s.getOutputStream();
            }
            return is;
        }

        //inherit javadoc
        public synchronized OutputStream getOutputStream() throws IOException {
            if (s==null) {
                s = new Socket(host,port);
                is = s.getInputStream();
                os = s.getOutputStream();
            }
            return os;
        }

        //inherit javadoc
        public InvocationConstraints getUnfulfilledConstraints(
            OutboundRequestHandle handle) {
            ConnectionTransportListener.getListener()
                .called("getUnfulfilledConstraints");
            return InvocationConstraints.EMPTY;
        }

        //inherit javadoc
        public void populateContext(OutboundRequestHandle handle,
            Collection context) {
            ConnectionTransportListener.getListener()
                .called("populateContext");
        }

        //inherit javadoc
        public IOException readResponseData(OutboundRequestHandle handle,
            InputStream stream) {
            ConnectionTransportListener.getListener()
                .called("readResponseData");
            return null;
        }

        //inherit javadoc
        public void writeRequestData(OutboundRequestHandle handle,
            OutputStream stream) {
            if ((exceptionMethod!=null)
                &&(exceptionMethod.equals("writeRequestData"))) {
                throw new RuntimeException("Bogus Exception");
            }
            ConnectionTransportListener.getListener()
                .called("writeRequestData");
        }
    }
}
