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
import com.sun.jini.test.spec.jeri.mux.util.AbstractMuxTest;
import com.sun.jini.test.spec.jeri.mux.util.AcknowledgementMessage;
import com.sun.jini.test.spec.jeri.mux.util.ClientConnectionHeader;
import com.sun.jini.test.spec.jeri.mux.util.DataMessage;
import com.sun.jini.test.spec.jeri.mux.util.PingAckMessage;
import com.sun.jini.test.spec.jeri.mux.util.PingMessage;
import com.sun.jini.test.spec.jeri.mux.util.ProtocolException;
import com.sun.jini.test.spec.jeri.mux.util.ServerConnectionHeader;

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

/**
 * Purpose: The purpose of this test is to exercise client-side implementations
 * of the Jini ERI multiplexing protocol.
 *
 * Use Case: Communicating with a Jini ERI multiplexing protocol client.
 *
 * This test verifies that:
 *     1. Messages that pertain to the connection as a whole begin with a
 *     byte in the range 0x00-0x0F
 *     2. Messages that pertain to a particular session begin with a byte
 *     in the range 0x10-0xFF
 *     3. Session identifiers fall between 0 and 127 (inclusive)
 *     4. The client initiates a session by sending a Data message with the
 *     open flag set
 *     5. The ClientConnectionHeader, PingAck, Acknowledgement, and Data
 *     messages sent by the client have the correct format.
 *     6. Reserved bits of the messages sent by the client are not used.
 *     7. The client sends a Data message with the EOF flag set when all the
 *     data is sent.
 *
 * Test:
 *     1. Obtain an instance of an endpoint that uses
 *     <code>net.jini.jeri.connection.ConnectionManager</code> to manage its
 *     connections.
 *     2. Obtain an <code>OutboundRequest</code> instance from the endpoint and
 *     write a sequence of bytes to the <code>OutputStream</code> obtained from
 *     the outbound request.
 *     3. Call close on the <code>OutputStream</code>.
 *     4. Verify that a <code>ClientConnectionHeader</code> header is sent to
 *     the server side of the connection.
 *     5. Verify that the client connection header matches its specified format.
 *     6. Send a <code>ServerConnectionHeader</code> to the client side of the
 *     connection.
 *     7. Verify that the client sends a <code>Data</code> message that
 *     matches the specified format.
 *     8. Verify that the open flag is set on the data message.
 *     9. Verify that a session identifier not in use is sent by the client.
 *     10. Verify that the bytes sent from the client match the bytes received
 *     by the server.
 *     11. Respond with a data message that requires acknowledgement.
 *     12. Verify that the client responds with an <code>Acknowledgement</code>
 *     message that matches the specified format.
 *     13. Verify that the acknowledgement message contains the correct session
 *     id.
 *     14. Send a <code>Ping</code> message to the client.
 *     15. Verify that the client responds with a <code>PingAck</code> message
 *     containing the cookie sent by the server.
 */
public class MuxClientTest extends AbstractMuxTest {

    private Socket s = null;
    private OutboundRequest or;
    private final byte[] received = new byte[2];
    private Object lock = new Object();

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
        //Send a ServerConnectionHeader
        ServerConnectionHeader sHeader = new ServerConnectionHeader();
        sHeader.send(os);
        //Verify that a properly formatted Data message with 100
        //data bytes is received
        DataMessage dm = new DataMessage();
        try {
            //verify that the open and eof flags are set on the data
            //message
            dm.setOpen().setEof().receive(is,getTimeout());
        } catch (ProtocolException e) {
            e.printStackTrace();
            throw new TestException(e.getMessage(),e);
        }
        //verify that 600 bytes are received
        byte[] payload = (byte[])dm.getPayload();
        if (payload.length!=600) {
            //try once again - it is possible the message was split
            try {
               dm.receive(is,getTimeout());
            } catch (ProtocolException e) {
                e.printStackTrace();
                throw new TestException(e.getMessage(),e);
            }
        }
        if (dm.getSize()!=600) {
            throw new TestException("Test did not receive"
                + " the expected number of bytes");
        }
        //Respond with a Data message that requires acknowledgement
        dm = new DataMessage();
        dm.setEof().setSessionID((byte)0x00).setPayload(
            new byte[] {(byte)0x16,(byte)0x16}).setSize((short)2)
            .setSessionID((byte) 0x00).setEof().setClose().setAckRequired();
        dm.send(os);
        //Verify that a properly formatted acknowledgement message is
        //received
        AcknowledgementMessage am = new AcknowledgementMessage();
        try {
            am.receive(is,getTimeout());
        } catch (ProtocolException e) {
            e.printStackTrace();
            throw new TestException(e.getMessage(),e);
        }
        //Verify that the contents of the server message were received
        if ((received[0]!=0x16)||(received[1]!=0x16)){
            throw new TestException("Message from the server"
                + " was not received properly");
        }
        //Verify ping
        short cookie = (short) 0x8080;
        PingMessage pm = new PingMessage();
        pm.setCookie(cookie).send(os);
        PingAckMessage pam = new PingAckMessage();
        try {
            pam.setCookie(cookie).receive(is,getTimeout());
        } catch (ProtocolException e) {
            e.printStackTrace();
            throw new TestException(e.getMessage(),e);
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
                    for (int i=0; i<600; i++) {
                        os.write(0x88);
                    }
                    os.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        });
        t.start();
    }
}
