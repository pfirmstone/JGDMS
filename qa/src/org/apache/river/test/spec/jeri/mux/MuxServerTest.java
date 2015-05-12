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
import org.apache.river.test.spec.jeri.mux.util.PingAckMessage;
import org.apache.river.test.spec.jeri.mux.util.PingMessage;
import org.apache.river.test.spec.jeri.mux.util.ProtocolException;
import org.apache.river.test.spec.jeri.mux.util.Redirector;
import org.apache.river.test.spec.jeri.mux.util.ServerConnectionHeader;
import org.apache.river.test.spec.jeri.mux.util.TestServerEndpoint;
import org.apache.river.test.spec.jeri.mux.util.TestService;
import org.apache.river.test.spec.jeri.mux.util.TestServiceImpl;
import org.apache.river.test.spec.jeri.mux.util.TimedReceiveTask;
import org.apache.river.test.spec.jeri.mux.util.Util;

//jeri imports
import net.jini.core.constraint.InvocationConstraints;
import net.jini.jeri.BasicILFactory;
import net.jini.jeri.BasicJeriExporter;
import net.jini.jeri.Endpoint;
import net.jini.jeri.OutboundRequest;
import net.jini.jeri.OutboundRequestIterator;
import net.jini.jeri.ServerEndpoint;

//java.io
import java.io.InputStream;
import java.io.IOException;
import java.io.OutputStream;

//java.net
import java.net.ServerSocket;
import java.net.Socket;

//java.util
import java.util.ArrayList;
import java.util.Iterator;

/**
 * Purpose: The purpose of this test is to exercise server-side implementations
 * of the Jini ERI multiplexing protocol.
 *
 * Use Case: Communicating with a Jini ERI multiplexing protocol server.
 *
 * This test verifies that:
 *     1. Messages that pertain to the connection as a whole begin with a
 *     byte in the range 0x00-0x0F
 *     2. Messages that pertain to a particular session begin with a byte
 *     in the range 0x10-0xFF
 *     3. Session identifiers fall between 0 and 127 (inclusive)
 *     4. The server responds to ClientConnectionHeaders with a
 *     ServerConnectionHeader
 *     5. The ServerConnectionHeader, PingAck, and Data
 *     messages sent by the server have the correct format.
 *     6. Reserved bits of the messages sent by the server are not used.
 * Test:
 *     1. Obtain an instance of <code>ServerEndpoint</code> that uses
 *     <code>net.jini.jeri.connection.ServerConnectionManager</code>.
 *     2. Export an object using the endpoint obtained in step 1.
 *     3. Connect to the server endpoint and send a
 *     <code>ClientConnectionHeader</code>.
 *     4. Verify that a <code>ServerConnectionHeader</code> header is sent from
 *     the server side of the connection.
 *     5. Verify that the server connection header matches its specified format.
 *     6. Send a <code>Ping</code> message to the server.
 *     7. Verify that the server responds with a <code>PingAck</code> message
 *     containing the cookie sent by the client.
 *     8. Make a remote call on the object.
 *     9. Intercept the messages flowing between the client and server sides
 *     of the connection and verify that the messages have the correct format
 *     and session identifiers.
 */
public class MuxServerTest extends AbstractMuxTest {

    public void run() throws Exception {
        //Setup server side mux connection
        ServerEndpoint se = getServerEndpoint();
        TestService service = new TestServiceImpl();
        int redirectPort = getPort() + 1;
        Redirector rd = new Redirector(getHost(),getPort(),redirectPort);
        ((TestServerEndpoint) se).redirect(redirectPort);
        Thread t = new Thread(rd);
        t.start();
        BasicJeriExporter exporter = new BasicJeriExporter(se,
            new BasicILFactory());
        TestService stub = (TestService) exporter.export(service);
        //Connect to the mux server
        Socket s = new Socket(getHost(),getPort());
        InputStream is = s.getInputStream();
        OutputStream os = s.getOutputStream();
        //Send client connection header
        ClientConnectionHeader cHeader = new ClientConnectionHeader();
        cHeader.send(os);
        //Receive ServerConnection header and verify format
        ServerConnectionHeader sHeader = new ServerConnectionHeader();
        try {
            sHeader.receive(is,getTimeout());
        } catch (ProtocolException e) {
            e.printStackTrace();
            throw new TestException(e.getMessage(),e);
        }
        //Make a remote call that returns something
        stub.doSomething();
        exporter.unexport(true);
        rd.stop();
        //Extract and analyze the messages sent by the mux server
        try {
            analyzeServerDataBytes(rd.getServerConversation());
        } catch (ProtocolException e) {
            e.printStackTrace();
            throw new TestException(e.getMessage(),e);
        }
                //Ping the server
        PingMessage pm = new PingMessage().setCookie((short)0x5544);
        pm.send(os);
        //Receive a ping ack from server and verify that the correct cookie
        //is included
        se = getServerEndpoint();
        service = new TestServiceImpl();
        exporter = new BasicJeriExporter(se,
            new BasicILFactory());
        stub = (TestService) exporter.export(service);
        //Connect to the mux server
        s = new Socket(getHost(),getPort());
        is = s.getInputStream();
        os = s.getOutputStream();
        PingAckMessage pam = new PingAckMessage().setCookie((short)0x5544);
        try {
            pam.receive(is,getTimeout());
        } catch (ProtocolException e) {
            e.printStackTrace();
            throw new TestException(e.getMessage(),e);
        }
    }

    /**
     * Verifies that the bytes received from the server are what the test
     * expects.
     *
     * @param serverConversation The bytes received from the server.
     */
    private void analyzeServerDataBytes(ArrayList serverConversation)
        throws ProtocolException {
        byte[] serverBytes = new byte[serverConversation.size()];
        Iterator it = serverConversation.iterator();
        int i=0;
        while (it.hasNext()) {
            byte[] singleByte = (byte[]) it.next();
            serverBytes[i] = singleByte[0];
            i++;
        }
        //extract the data message
        byte[] dataMessage = new byte[serverBytes.length-8];
        System.arraycopy(serverBytes,8,dataMessage,0,dataMessage.length);
        //Verify that the received message is a data message
        if ((dataMessage[0]&0x80)!=0x80) {
            throw new ProtocolException("Message received: "
                + Util.convert(dataMessage) + " is not a data message");
        }
        //Verify the length of the message
        int size = (dataMessage[2]<<8) + dataMessage[3];
        if (size!=dataMessage.length-4) {
            throw new ProtocolException("Data message received reported"
                + " incorrect size");
        }

    }
}
