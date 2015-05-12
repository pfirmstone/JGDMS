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

//java.io
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;


//java.net
import java.net.ServerSocket;
import java.net.Socket;

//java.util
import java.util.ArrayList;
import java.util.Date;

/**
 * Utility class that records the conversation between a client and a server
 */
public class Redirector implements Runnable {

    private String remoteHost;
    private int remotePort;
    private int localPort;
    private ArrayList server = new ArrayList();
    private ArrayList client = new ArrayList();
    private volatile ServerSocket ss = null;
    private boolean stop = false;
    private boolean clientDone = false;
    private boolean serverDone = false;
    private Object clientLock = new Object();
    private Object serverLock = new Object();

    /**
     * Constructs a <code>Redirector</code> object.
     *
     * @param remoteHost Host for the server side of the connection being
     * redirected.
     * @param remotePort Port for the server side of the connection beign
     * redirected.
     * @param localPort Port to which the client side of the connection will
     * connect to.
     */
    public Redirector(String remoteHost, int remotePort, int localPort) {
        this.remoteHost = remoteHost;
        this.remotePort = remotePort;
        this.localPort = localPort;
    }

    /**
     * Returns the bytes sent by the server side of the connection
     */
    public ArrayList getServerConversation() throws InterruptedException {
        int retry = 0;
        while ((!serverDone)&&(retry<6)) {
            synchronized(serverLock) {
                serverLock.wait(500);
            }
            retry++;
        }
        return server;
    }

    /**
     * Returns the bytes sent by the client side of the connection
     */
    public ArrayList getClientConversation() throws InterruptedException {
        int retry = 0;
        while ((!clientDone)&&(retry<6)) {
            synchronized(clientLock) {
                clientLock.wait(500);
            }
            retry++;
        }
        return client;
    }

    /**
     * Stop redirecting the connection
     */
    public synchronized void stop() {
        stop = true;
        if (ss!=null) {
            try {
                ss.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * Is redirection stopped?
     */
    public synchronized boolean getStop() {
        return stop;
    }

    /**
     * Main loop for redirection
     */
    public void run() {
        try {
            ss = new ServerSocket(localPort);
            while(!getStop()) {
                //Accept the connection from the client
                final Socket s = ss.accept();
                //Connect to the server
                final Socket remote = new Socket(remoteHost,remotePort);
                //Send input from the client to the server
                Thread to = new Thread(new Runnable() {
                    public void run() {
                        try {
                            InputStream is = s.getInputStream();
                            OutputStream os = remote.getOutputStream();
                            while (!getStop()) {
                                if (is.available()<1) {
                                    continue;
                                }
                                byte[] bytes = new byte[1];
                                if (is.read(bytes)>0){
                                    os.write(bytes);
                                    os.flush();
                                    //record client conversation
                                    client.add(bytes);
                                }
                            }
                            is.close();
                            os.close();
                        } catch (Exception e) {
                            if (!getStop()) {
                                e.printStackTrace();
                            }
                        } finally {
                            synchronized(clientLock) {
                                clientDone = true;
                                clientLock.notify();
                            }
                        }
                    }
                });
                to.start();
                //Send input from the server to the client
                Thread from = new Thread(new Runnable() {
                    public void run() {
                        try {
                            InputStream is = remote.getInputStream();
                            OutputStream os = s.getOutputStream();
                            while (!getStop()) {
                                if (is.available()<1) {
                                    continue;
                                }
                                byte[] bytes = new byte[1];
                                if (is.read(bytes)>0){
                                    os.write(bytes);
                                    os.flush();
                                    //Record server conversation
                                    server.add(bytes);
                                }
                            }
                            is.close();
                            os.close();
                        } catch (Exception e) {
                            if (!getStop()) {
                                e.printStackTrace();
                            }
                        } finally {
                            synchronized(serverLock) {
                                serverDone = true;
                                serverLock.notify();
                            }
                        }
                    }
                });
                from.start();
            }
        } catch (Exception e) {
            if (!getStop()) {
                e.printStackTrace();
            }
        }

    }
}
