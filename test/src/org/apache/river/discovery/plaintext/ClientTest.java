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

package org.apache.river.discovery.plaintext;

import org.apache.river.discovery.DatagramBufferFactory;
import org.apache.river.discovery.MulticastAnnouncement;
import org.apache.river.discovery.MulticastRequest;
import org.apache.river.discovery.UnicastResponse;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collection;
import net.jini.core.constraint.Confidentiality;
import net.jini.core.constraint.Integrity;
import net.jini.core.constraint.InvocationConstraint;
import net.jini.core.constraint.InvocationConstraints;
import net.jini.core.constraint.ServerAuthentication;
import net.jini.io.UnsupportedConstraintException;
import net.jini.jeri.ssl.ConfidentialityStrength;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 *
 * @author peter
 */
public class ClientTest {
    Client instance;
    public ClientTest() {
    }

    @BeforeClass
    public static void setUpClass() throws Exception {
    }

    @AfterClass
    public static void tearDownClass() throws Exception {
    }

    @Before
    public void setUp() {
        instance = new Client();
    }

    /**
     * Test of getFormatName method, of class Client.
     */
    @Test
    public void getFormatName() {
        System.out.println("getFormatName");
        String expResult = "net.jini.discovery.plaintext";
        String result = instance.getFormatName();
        assertEquals(expResult, result);
        // TODO review the generated test code and remove the default call to fail.
    }

//    /**
//     * Test of encodeMulticastRequest method, of class Client.
//     */
//    @Test
//    public void encodeMulticastRequest() throws Exception {
//        System.out.println("encodeMulticastRequest");
//        MulticastRequest request = null;
//        DatagramBufferFactory bufs = null;
//        InvocationConstraints constraints = null;
//        Client instance = new Client();
//        instance.encodeMulticastRequest(request, bufs, constraints);
//        // TODO review the generated test code and remove the default call to fail.
//        fail("The test case is a prototype.");
//    }
//
//    /**
//     * Test of decodeMulticastAnnouncement method, of class Client.
//     */
//    @Test
//    public void decodeMulticastAnnouncement() throws Exception {
//        System.out.println("decodeMulticastAnnouncement");
//        ByteBuffer buf = null;
//        InvocationConstraints constraints = null;
//        Client instance = new Client();
//        MulticastAnnouncement expResult = null;
//        MulticastAnnouncement result = instance.decodeMulticastAnnouncement(buf, constraints);
//        assertEquals(expResult, result);
//        // TODO review the generated test code and remove the default call to fail.
//        fail("The test case is a prototype.");
//    }

    /**
     * Test of checkUnicastDiscoveryConstraints method, of class Client.
     */
    @Test
    public void checkUnicastDiscoveryConstraints() throws Exception {
        System.out.println("checkUnicastDiscoveryConstraints");
        Collection<InvocationConstraint> required = new ArrayList<InvocationConstraint>(4);
        InvocationConstraint integrity = Integrity.YES;
        InvocationConstraint confidential = Confidentiality.YES;
        InvocationConstraint serverAuth = ServerAuthentication.YES;
        InvocationConstraint strength = ConfidentialityStrength.STRONG;
        required.add(integrity);
        required.add(confidential);
        required.add(serverAuth);
        required.add(strength);
        Collection preferred = null;
        InvocationConstraints constraints = new InvocationConstraints(required, preferred);
        Exception ex = null;
        try {
            instance.checkUnicastDiscoveryConstraints(constraints);
        }catch(UnsupportedConstraintException e){
            ex = e;
        }
        assertTrue( ex != null);
    }

//    /**
//     * Test of doUnicastDiscovery method, of class Client.
//     */
//    @Test
//    public void doUnicastDiscovery() throws Exception {
//        System.out.println("doUnicastDiscovery");
//        Socket socket = null;
//        InvocationConstraints constraints = null;
//        ClassLoader defaultLoader = null;
//        ClassLoader verifierLoader = null;
//        Collection context = null;
//        ByteBuffer sent = null;
//        ByteBuffer received = null;
//        Client instance = new Client();
//        UnicastResponse expResult = null;
//        UnicastResponse result = instance.doUnicastDiscovery(socket, constraints, defaultLoader, verifierLoader, context, sent, received);
//        assertEquals(expResult, result);
//        // TODO review the generated test code and remove the default call to fail.
//        fail("The test case is a prototype.");
//    }

}