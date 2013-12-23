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
package com.sun.jini.test.spec.jeri.mux;

import java.util.logging.Level;

//harness imports
import com.sun.jini.qa.harness.TestException;

//utility classes
import com.sun.jini.test.spec.jeri.mux.util.AbortMessage;
import com.sun.jini.test.spec.jeri.mux.util.AbstractMuxTest;
import com.sun.jini.test.spec.jeri.mux.util.AcknowledgementMessage;
import com.sun.jini.test.spec.jeri.mux.util.ClientConnectionHeader;
import com.sun.jini.test.spec.jeri.mux.util.CloseMessage;
import com.sun.jini.test.spec.jeri.mux.util.DataMessage;
import com.sun.jini.test.spec.jeri.mux.util.IncrementRationMessage;
import com.sun.jini.test.spec.jeri.mux.util.PingAckMessage;
import com.sun.jini.test.spec.jeri.mux.util.PingMessage;
import com.sun.jini.test.spec.jeri.mux.util.ProtocolException;
import com.sun.jini.test.spec.jeri.mux.util.ServerConnectionHeader;
import com.sun.jini.test.spec.jeri.mux.util.Util;

//jeri imports
import net.jini.core.constraint.InvocationConstraints;
import net.jini.jeri.Endpoint;
import net.jini.jeri.OutboundRequest;
import net.jini.jeri.OutboundRequestIterator;

//java.io
import java.io.InputStream;
import java.io.IOException;
import java.io.OutputStream;

//java.net
import java.net.ServerSocket;
import java.net.Socket;

//java.util
import java.util.Arrays;

/**
 * Purpose: This test verifies that a protocol client stops sending data
 * to a sever after the server sends a Close message.  It also verifies
 * that the client sends an Abort message after a Close message from the
 * server is received if the client is in an active session.
 *
 * Test:
 * 1. Establish a Jini ERI Multiplexing Protocol connection using an
 * instrumented server.
 * 2. In the server, specify the inbound ration as 256 bytes.
 * 3. From the client, write 800 bytes to the mux connection.
 * 4. After the first message from the client is received, send a Close
 * message from the server.
 * 5. Verify that an Abort message is sent by the client.
 */
public class CloseTest extends AbstractMuxTest {

    private Socket s = null;
    private OutboundRequest or;
    private final byte[] received = new byte[2];
    private Object lock = new Object();
    private int expectedBytes = 800;

    public void run() throws Exception {
        //Start a listen opertation
        ServerSocket ss = new ServerSocket(getPort());
        acceptOneConnection(ss);
        Endpoint ep = getEndpoint();
        openClientConnection(ep);
        Socket connection = getConnection();
        if (connection==null) {
            throw new TestException("Unable to establish"
                + "client/server connection");
        }
        InputStream is = connection.getInputStream();
        OutputStream os = connection.getOutputStream();
        //Verify that a properly formatted ClientConnectionHeader is
        //received
        ClientConnectionHeader cHeader =
            new ClientConnectionHeader();
        try {
            cHeader.receive(is,getTimeout());
        } catch (ProtocolException e) {
            e.printStackTrace();
            throw new TestException(e.getMessage(),e);
        }
        //Send a ServerConnectionHeader with a server inbound ration
        //of 256 bytes
        ServerConnectionHeader sHeader = new ServerConnectionHeader();
        sHeader.setRation((short)0x0001).send(os);
        int receivedBytes = 0;
        long stopTime = System.currentTimeMillis() + getTimeout()*5;
        DataMessage dm = new DataMessage().suppressFormatCheck();
        dm.receive(is,getTimeout());
        receivedBytes += dm.getSize();
        //Verify that no more that 256 bytes are received
        if (dm.getSize()>256) {
            throw new TestException("More than 256 bytes"
                + " were sent");
        }
        //Send an Close message
        int sessionId = dm.getsessionId();
        DataMessage eof = new DataMessage().setEof()
            .setSessionID((byte)sessionId);
        eof.send(os);
        CloseMessage cm = new CloseMessage();
        cm.send(os);
        if (!abortReceived(is)) {
            throw new TestException("The client did not"
                + " send an Abort message");
        }
    }

    /**
     * Asynchronously listens for a single incoming connection.  When an
     * incoming connection is received, accepts the connection and exits.
     */
    private void acceptOneConnection(final ServerSocket ss) {
        Thread t = new Thread(new Runnable() {
            public void run() {
                try {
                    Socket temp = ss.accept();
                    synchronized(lock) {
                        s = temp;
                        lock.notify();
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        });
        t.start();
    }

    /**
     * Blocks until a connection is accepted or until a timeout expires and
     * returns a connected socket is a connection was received.
     *
     * @return A socket representing an accepted connection or null if no
     * connection was accepted
     */
    private Socket getConnection() throws InterruptedException {
        if (s==null) {
            synchronized (lock) {
                long duration = 2 * getTimeout();
                long finish = System.currentTimeMillis() + duration;
                do {
                    lock.wait(duration);
                    duration = finish - System.currentTimeMillis();
                } while ( s == null && duration > 0);
            }
        }
        Socket temp = s;
        s=null;
        return temp;
    }

    /**
     * Exercises the client side of the mux connection.  Causes the client
     * mux code to connect to the test mux server and transmit data.
     *
     * @param ep An endpoint that uses
     * <code>net.jini.jeri.connection.ConnectionManager</code>
     */
    private void openClientConnection(final Endpoint ep) {
        Thread t = new Thread(new Runnable() {
            public void run() {
                try {
                    OutboundRequestIterator it = ep.newRequest(
                        InvocationConstraints.EMPTY);
                    OutboundRequest or = it.next();
                    final InputStream is = or.getResponseInputStream();
                    Thread input = new Thread(new Runnable() {
                        public void run() {
                            try {
                                byte[] bytes = new byte[2];
                                is.read(bytes);
                                received[0] = bytes[0];
                                received[1] = bytes[1];
                                is.close();
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                        }
                    });
                    input.start();
                    OutputStream os = or.getRequestOutputStream();
                    byte[] bytes = new byte[expectedBytes];
                    Arrays.fill(bytes,(byte)0x88);
                    os.write(bytes);
                    os.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        });
        t.start();
    }

    /**
     * Blocks until a Jini ERI mux protocol Abort message is received or until
     * a timeout expires.
     *
     * @param is Input stream from which to read Jini ERI mux messages
     * @return true if an Abort message is received, false otherwise
     */
    private boolean abortReceived(InputStream is)
        throws IOException, InterruptedException {
        long stopTime = System.currentTimeMillis() + (getTimeout());
        boolean abortReceived = false;
        while (System.currentTimeMillis() < stopTime) {
            if (!waitForInput(is,1,stopTime)) {
                return false;
            }
            byte[] msb = new byte[1];
            is.read(msb);
            if ((msb[0]|(byte)0x22)==0x22) {
                abortReceived = true;
                break;
            } else if ((msb[0]&(byte)0x80)==0x80){
                byte[] header = new byte[3];
                if (!waitForInput(is,3,stopTime)) {
                    return false;
                }
                is.read(header);
                int size =  (header[1]<<8) + header[2];
                if (!waitForInput(is,size,stopTime)) {
                    return false;
                }
                byte[] data = new byte[size];
                is.read(data);
            }
        }
        return abortReceived;
    }

    /**
     * Blocks until a given number of bytes are available from the input
     * stream or until a timeout expires.
     *
     * @param is The input stream to read bytes from
     * @param bytes The number of bytes to wait for
     * @param stop The number of ms to wait for the bytes to be available
     */
    private boolean waitForInput(InputStream is, int bytes, long stop)
        throws IOException, InterruptedException {
        while((is.available()<bytes)&&(System.currentTimeMillis() < stop)){
            Thread.sleep(100);
        }
        return (is.available()>=bytes);
    }
}
