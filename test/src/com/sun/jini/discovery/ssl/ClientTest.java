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

package com.sun.jini.discovery.ssl;

import com.sun.jini.discovery.UnicastResponse;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collection;
import net.jini.core.constraint.ClientAuthentication;
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
 * 
 */
public class ClientTest {

    public ClientTest() {
    }
    Client instance;
    
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
        Client instance = new Client();
        String expResult = "net.jini.discovery.ssl";
        String result = instance.getFormatName();
        assertEquals(expResult, result);
        // TODO review the generated test code and remove the default call to fail.
    }

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
        instance.checkUnicastDiscoveryConstraints(constraints);
        // TODO review the generated test code and remove the default call to fail.
    }

    @Test
    public void checkConstraintFailClientAuth() throws Exception {
        System.out.println("checkConstraintFailClientAuth");
        Collection<InvocationConstraint> required = new ArrayList<InvocationConstraint>(4);
        InvocationConstraint clientAuth = ClientAuthentication.YES;
        required.add(clientAuth);
        Collection preferred = null;
        InvocationConstraints constraints = new InvocationConstraints(required, preferred);
        UnsupportedConstraintException ex = null;
        try {
            instance.checkUnicastDiscoveryConstraints(constraints);
        } catch (UnsupportedConstraintException e) {
            ex = e;
        }
        assertTrue( ex != null);
    }
}