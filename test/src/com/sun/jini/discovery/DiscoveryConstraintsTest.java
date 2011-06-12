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

package com.sun.jini.discovery;

import java.util.ArrayList;
import java.util.Collection;
import java.util.logging.Level;
import java.util.logging.Logger;
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
public class DiscoveryConstraintsTest {

    public DiscoveryConstraintsTest() {
    }
    
    DiscoveryConstraints instance;
    InvocationConstraints constraints;
    @BeforeClass
    public static void setUpClass() throws Exception {
    }

    @AfterClass
    public static void tearDownClass() throws Exception {
    }

    @Before
    public void setUp() {
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
        constraints = new InvocationConstraints(required, preferred);
        instance = null;
    }

//    /**
//     * Test of multicastRequest method, of class DiscoveryConstraints.
//     */
//    @Test
//    public void multicastRequest() {
//        System.out.println("multicastRequest");
//        DiscoveryConstraints.multicastRequest();
//        // TODO review the generated test code and remove the default call to fail.
//        fail("The test case is a prototype.");
//    }
//
//    /**
//     * Test of multicastAnnouncement method, of class DiscoveryConstraints.
//     */
//    @Test
//    public void multicastAnnouncement() {
//        System.out.println("multicastAnnouncement");
//        DiscoveryConstraints.multicastAnnouncement();
//        // TODO review the generated test code and remove the default call to fail.
//        fail("The test case is a prototype.");
//    }
//
//    /**
//     * Test of unicastDiscovery method, of class DiscoveryConstraints.
//     */
//    @Test
//    public void unicastDiscovery() {
//        System.out.println("unicastDiscovery");
//        DiscoveryConstraints.unicastDiscovery();
//        // TODO review the generated test code and remove the default call to fail.
//        fail("The test case is a prototype.");
//    }
//    
    /**
     * Test of process() method, of class DiscoveryConstraints.
     */
    @Test
    public void process() throws Exception {
        System.out.println("process");
        instance = DiscoveryConstraints.process(constraints);
    }

    /**
     * Test of chooseProtocolVersion method, of class DiscoveryConstraints.
     */
    @Test
    public void chooseProtocolVersion() throws Exception {
        System.out.println("chooseProtocolVersion");
        instance = DiscoveryConstraints.process(constraints);
        int expResult = 2;
        int result = instance.chooseProtocolVersion();
        assertEquals(expResult, result);
        // TODO review the generated test code and remove the default call to fail.
    }

    /**
     * Test of checkProtocolVersion method, of class DiscoveryConstraints.
     */
    @Test
    public void checkProtocolVersion() throws Exception {
        System.out.println("checkProtocolVersion");
        int version = 2;
        instance = DiscoveryConstraints.process(constraints);
        instance.checkProtocolVersion(version);
        // TODO review the generated test code and remove the default call to fail.
    }

    /**
     * Test of getConnectionDeadline method, of class DiscoveryConstraints.
     */
    @Test
    public void getConnectionDeadline() throws Exception {
        System.out.println("getConnectionDeadline");
        long defaultValue = 0L;
        instance = DiscoveryConstraints.process(constraints);
        long expResult = 0L;
        long result = instance.getConnectionDeadline(defaultValue);
        assertEquals(expResult, result);
        // TODO review the generated test code and remove the default call to fail.
    }

    /**
     * Test of getMulticastMaxPacketSize method, of class DiscoveryConstraints.
     */
    @Test
    public void getMulticastMaxPacketSize() throws Exception {
        System.out.println("getMulticastMaxPacketSize");
        int defaultValue = 0;
        instance = DiscoveryConstraints.process(constraints);
        int expResult = 0;
        int result = instance.getMulticastMaxPacketSize(defaultValue);
        assertEquals(expResult, result);
        // TODO review the generated test code and remove the default call to fail.
    }

    /**
     * Test of getMulticastTimeToLive method, of class DiscoveryConstraints.
     */
    @Test
    public void getMulticastTimeToLive() throws Exception {
        System.out.println("getMulticastTimeToLive");
        int defaultValue = 0;
        instance = DiscoveryConstraints.process(constraints);
        int expResult = 0;
        int result = instance.getMulticastTimeToLive(defaultValue);
        assertEquals(expResult, result);
    }

    /**
     * Test of getUnicastSocketTimeout method, of class DiscoveryConstraints.
     */
    @Test
    public void getUnicastSocketTimeout() throws Exception {
        System.out.println("getUnicastSocketTimeout");
        int defaultValue = 0;
        instance = DiscoveryConstraints.process(constraints);
        int expResult = 0;
        int result = instance.getUnicastSocketTimeout(defaultValue);
        assertEquals(expResult, result);
    }

    /**
     * Test of getUnfulfilledConstraints method, of class DiscoveryConstraints.
     */
    @Test
    public void getUnfulfilledConstraints() throws Exception {
        System.out.println("getUnfulfilledConstraints");
        instance = DiscoveryConstraints.process(constraints);
        InvocationConstraints expResult = constraints;
        InvocationConstraints result = instance.getUnfulfilledConstraints();
        assertEquals(expResult, result);
    }
    
    /**
     * Test of getUnfulfilledConstraints method, of class DiscoveryConstraints.
     */
    @Test
    public void testConstraints() throws Exception {
        System.out.println("testConstraints");
        Collection<InvocationConstraint> discoveryConstraints 
                = new ArrayList<InvocationConstraint>();
        InvocationConstraint usoctimout = new UnicastSocketTimeout(10);
        InvocationConstraint multicastMaxpackSize = new MulticastMaxPacketSize(512);
        InvocationConstraint discoveryProtVer = DiscoveryProtocolVersion.TWO;
        InvocationConstraint multicastTTL = new MulticastTimeToLive(100);
        discoveryConstraints.add(usoctimout);
        discoveryConstraints.add(multicastMaxpackSize);
        discoveryConstraints.add(discoveryProtVer);
        discoveryConstraints.add(multicastTTL);
        
        Collection<InvocationConstraint> requiredAdditionalConstraints = new ArrayList<InvocationConstraint>(4);
        InvocationConstraint integrity = Integrity.YES;
        InvocationConstraint confidential = Confidentiality.YES;
        InvocationConstraint serverAuth = ServerAuthentication.YES;
        InvocationConstraint strength = ConfidentialityStrength.STRONG;
        requiredAdditionalConstraints.add(integrity);
        requiredAdditionalConstraints.add(confidential);
        requiredAdditionalConstraints.add(serverAuth);
        requiredAdditionalConstraints.add(strength);
        
        Collection<InvocationConstraint> combined = new ArrayList<InvocationConstraint>();
        combined.addAll(discoveryConstraints);
        combined.addAll(requiredAdditionalConstraints);
        
        Collection preferred = null;
        
        constraints = new InvocationConstraints(combined, preferred);
       
        instance = DiscoveryConstraints.process(constraints);
        InvocationConstraints expResult 
                = new InvocationConstraints(requiredAdditionalConstraints, preferred);
        InvocationConstraints result = instance.getUnfulfilledConstraints();
        assertEquals(expResult, result);
    }

}