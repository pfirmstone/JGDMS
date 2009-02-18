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
import com.sun.jini.test.spec.jeri.mux.util.IncrementRationMessage;
import com.sun.jini.test.spec.jeri.mux.util.PingAckMessage;
import com.sun.jini.test.spec.jeri.mux.util.PingMessage;
import com.sun.jini.test.spec.jeri.mux.util.ProtocolException;
import com.sun.jini.test.spec.jeri.mux.util.Redirector;
import com.sun.jini.test.spec.jeri.mux.util.ServerConnectionHeader;
import com.sun.jini.test.spec.jeri.mux.util.TestServerEndpoint;
import com.sun.jini.test.spec.jeri.mux.util.TestService;
import com.sun.jini.test.spec.jeri.mux.util.TestServiceImpl;
import com.sun.jini.test.spec.jeri.mux.util.TimedReceiveTask;
import com.sun.jini.test.spec.jeri.mux.util.Util;

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
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;

//java.util
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;

/**
 * Purpose: The purpose of this test is to exercise the Jini ERI
 * Multiplexing Protocol flow control mechanism.  The test verifies that
 * a protocol server sends data to the client according to the inbound ration
 * specified on the client side.
 *
 * Test:
 * 1. Establish a Jini ERI Multiplexing Protocol connection using an
 * instrumented client.
 * 2. In the client, specify the inbound ration as 256 bytes.
 * 3. From the server, write 800 bytes to the mux connection.
 * 4. Verify that the data is broken up into messages, each under 256 bytes.
 * 5. Verify that all data messages are only sent after
 * the client sends an increment ration message.
 */
public class ServerFlowControlTest extends AbstractMuxTest {

    public void run() throws Exception {
        //Setup server side mux connection
        ServerEndpoint se = getServerEndpoint();
        Redirector rd = new Redirector(InetAddress.getLocalHost()
            .getHostAddress(), getPort(), getPort() + 1);
        Thread t = new Thread(rd);
        t.start();
        ((TestServerEndpoint) se).redirect(getPort() + 1);
        TestService service = new TestServiceImpl();
        BasicJeriExporter exporter = new BasicJeriExporter(se,
            new BasicILFactory());
        TestService stub = (TestService) exporter.export(service);
        //Obtain a message to send for the test
        byte[] ball = new byte[800];
        Arrays.fill(ball, (byte)0x88);
        stub.bounce(ball);
        rd.stop();
        Thread.currentThread().sleep(1000 * 30);
        byte[] data = extractDataMessage(rd.getClientConversation());
        byte[] serverData = extractDataMessage(rd.getServerConversation());
        int size = (serverData[2]<<8) + serverData[3];
        //Establish a connection to the mux server
        Socket s = new Socket(InetAddress.getLocalHost(),getPort());
        InputStream is = s.getInputStream();
        OutputStream os = s.getOutputStream();
        //Set the client ration to 256 bytes
        ClientConnectionHeader cHeader = new ClientConnectionHeader()
            .setRation((short)0x0001);
        cHeader.send(os);
        //receive the ServerConnection header
        ServerConnectionHeader sHeader = new ServerConnectionHeader();
        sHeader.receive(is,getTimeout());
        //Play back the request obtained before
        os.write(data);
        os.flush();
        int received = 0;
        int sessionId = 0;
        while (received < size) {
            //Receive message fragment
            DataMessage dm = new DataMessage().suppressFormatCheck();
            dm.receive(is,getTimeout());
            int dataSize = dm.getSize();
            if (dataSize > 256) {
                throw new TestException("Message fragment size"
                    + " over 256 bytes: " + dm);
            } else if (dataSize > 0) {
                received += dm.getSize();
                sessionId = dm.getsessionId();
                //Send an increment ration of 256 bytes
                IncrementRationMessage ir = new IncrementRationMessage()
                    .setIncrement( (byte) 0, (short) 0x0100)
                    .setSessionID( (byte) sessionId);
                ir.send(os);
            }
        }
    }

    /**
     * Extracts a Jini ERI mux protocol data message from an array of bytes.
     *
     * @param conversation Array from which to extract data message.
     * @return An array of bytes that that represents a single data message.
     */
    private byte[] extractDataMessage(ArrayList conversation) {
        Iterator it = conversation.iterator();
        byte[] data = new byte[conversation.size()-8];
        int index = 0;
        while (it.hasNext()) {
            byte[] bytes = (byte[]) it.next();
            //skip the client connection header
            if (index>7) {
                data[index-8] = bytes[0];
            }
            index++;
        }
        return data;
    }
}
