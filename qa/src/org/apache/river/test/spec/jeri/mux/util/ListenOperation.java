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
package org.apache.river.test.spec.jeri.mux.util;
//jeri imports
import net.jini.jeri.connection.ServerConnection;
import net.jini.core.constraint.InvocationConstraints;
import net.jini.jeri.RequestDispatcher;
import net.jini.jeri.connection.ServerConnectionManager;
import net.jini.jeri.connection.InboundRequestHandle;

//java.io
import java.io.InputStream;
import java.io.IOException;
import java.io.OutputStream;

//java.net
import java.net.Socket;
import java.net.ServerSocket;

//java.nio
import java.nio.channels.SocketChannel;

//java.util
import java.util.Collection;

/**
 * Utility class to accept incoming connections and use a
 * <code>net.jini.jeri.connection.ServerConnectionManager</code>
 */
public class ListenOperation implements Runnable {

    private int port;
    private RequestDispatcher rd;
    private volatile boolean stop = false;
    private ServerConnectionManager manager = new ServerConnectionManager();
    private ServerSocket ss = null;
    private boolean listenStarted = false;
    private Object listenLock = new Object();

    // Constructors
    public ListenOperation(int port, RequestDispatcher rd) {
        this.port = port;
        this.rd = rd;
    }

    // Stop listening
    public synchronized void close() {
        stop = true;
        if (ss!=null) {
            try {
                ss.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    // Is the listen opertation stopped?
    public synchronized boolean getStop() {
        return stop;
    }

    public boolean waitForListen() {
        int retries = 0;
        while ((!listenStarted)&&(retries<6)) {
            synchronized(listenLock) {
                try {
                    listenLock.wait(500);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                } finally {
                    retries++;
                }
            }
        }
        return listenStarted;
    }

    // Accept loop for this class
    public void run() {
        try {
            ss = new ServerSocket(port);
            while (!getStop()) {
                if (!listenStarted) {
                    synchronized(listenLock) {
                        listenStarted = true;
                        listenLock.notify();
                    }
                }
                ServerConnection connection =
                    new TestServerConnection(ss.accept());
                manager.handleConnection(connection, rd);
            }
        } catch (IOException e) {
            if (!getStop()) {
                e.printStackTrace();
            }
        }
    }

    // Utility class to interact with server connection manager
    private class TestServerConnection
        implements ServerConnection, InboundRequestHandle {

        private Socket s;
        private InputStream is;
        private OutputStream os;

        // Constructor
        public TestServerConnection(Socket s) {
            this.s = s;
            try {
                is = s.getInputStream();
                os = s.getOutputStream();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        //inherit javadoc
        public InvocationConstraints checkConstraints(
            InboundRequestHandle handle, InvocationConstraints constraints) {
            return InvocationConstraints.EMPTY;
        }

        //inherit javadoc
        public void checkPermissions(InboundRequestHandle handle) {
        }

        //inherit javadoc
        public void close() {
            try {
                s.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        //inherit javadoc
        public SocketChannel getChannel() {
            return null;
        }

        //inherit javadoc
        public synchronized InputStream getInputStream() {
            return is;
        }

        //inherit javadoc
        public synchronized OutputStream getOutputStream() {
            return os;
        }

        //inherit javadoc
        public void populateContext(
            InboundRequestHandle handle, Collection context) {
        }

        //inherit javadoc
        public InboundRequestHandle processRequestData(
            InputStream in, OutputStream out) {
            return this;
        }
  }
}
