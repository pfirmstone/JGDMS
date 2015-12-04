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
package org.apache.river.test.spec.jeri.mux;

import java.util.logging.Level;

//harness imports
import org.apache.river.qa.harness.TestException;

//utility classes
import org.apache.river.test.spec.jeri.mux.util.AbstractMuxTest;
import org.apache.river.test.spec.jeri.mux.util.AcknowledgementMessage;
import org.apache.river.test.spec.jeri.mux.util.ClientConnectionHeader;
import org.apache.river.test.spec.jeri.mux.util.DataMessage;
import org.apache.river.test.spec.jeri.mux.util.IncrementRationMessage;
import org.apache.river.test.spec.jeri.mux.util.PingAckMessage;
import org.apache.river.test.spec.jeri.mux.util.PingMessage;
import org.apache.river.test.spec.jeri.mux.util.ProtocolException;
import org.apache.river.test.spec.jeri.mux.util.ServerConnectionHeader;

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
 * Purpose: The purpose of this test is to exercise the Jini ERI
 * Multiplexing Protocol flow control mechanism.  The test verifies that
 * a protocol client sends data to the server according to the inbound ration
 * specified on the server side.
 *
 * Test:
 * 1. Establish a Jini ERI Multiplexing Protocol connection using an
 * instrumented server.
 * 2. In the server, specify the inbound ration as 256 bytes.
 * 3. From the client, write 800 bytes to the mux connection.
 * 4. Verify that the data is broken up into messages, each under 256 bytes.
 * 5. Verify that all data messages except the first are only sent after
 * the server sends an increment ration message.
 */
public class ClientFlowControlTest extends AbstractMuxTest {

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
        long messageTime = getTimeout();
        do {
            DataMessage dm = new DataMessage().suppressFormatCheck();
            //new TimedReceiveTask(dm,is,getTimeout());
            dm.receive(is,messageTime);
            receivedBytes += dm.getSize();
            if (receivedBytes==expectedBytes) {
                break;
            }
            //Verify that no more that 256 bytes are received
            if (dm.getSize()>256) {
                throw new TestException("More than 256 bytes"
                    + " were sent");
            }
            int session = dm.getsessionId();
            //Verify that no data is sent before an increment ration
            //message is sent;
            DataMessage dummy = new DataMessage().suppressFormatCheck();
            boolean dataSent = true;
            dummy.receive(is,messageTime);
            if (dummy.getSize()>0) {
                throw new TestException("Data was sent before"
                    + " the server sent an increment ration message");
            }
            //request that an additional 256 bytes be sent
            IncrementRationMessage increment = new IncrementRationMessage();
            increment.setIncrement((byte)0,(short)0x0100)
                .setSessionID((byte)session).send(os);
        } while ((receivedBytes < expectedBytes)
            &&(System.currentTimeMillis()<stopTime));
        if (receivedBytes!=expectedBytes) {
            throw new TestException("Unexpected number of bytes"
                + " were received");
        }
    }

    /**
     * Asynchronously listens for a single incoming connection.  When an
     * incoming connection is received, accepts the connection and exits.
     *
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
}
