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

package net.jini.core.event;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.rmi.MarshalledObject;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.jini.io.MarshalledInstance;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 *
 * 
 */
public class RemoteEventTest {

    public RemoteEventTest() {
    }
    String s = "happy";
    Object source = "source";
    MarshalledObject m = null;
    RemoteEvent e = null;
    MarshalledObject mo = null;

    @Before
    @SuppressWarnings("deprecation")
    public void setUp() {
	try {
	    m = new MarshalledObject(s);
	} catch (IOException ex) {
	    Logger.getLogger(RemoteEventTest.class.getName()).log(Level.SEVERE, null, ex);
	}
	e = new RemoteEvent(source, 10L, 25L,m);
    }

    /**
     * Test of getID method, of class RemoteEvent.
     */
    @Test
    public void getID() {
	System.out.println("getID");
	RemoteEvent instance = e;
	long expResult = 10L;
	long result = instance.getID();
	assertEquals(expResult, result);
    }

    /**
     * Test of getSequenceNumber method, of class RemoteEvent.
     */
    @Test
    public void getSequenceNumber() {
	System.out.println("getSequenceNumber");
	RemoteEvent instance = e;
	long expResult = 25L;
	long result = instance.getSequenceNumber();
	assertEquals(expResult, result);
    }

    /**
     * Test of getRegistrationObject method, of class RemoteEvent.
     */
    @Test
    public void getRegistrationObject() {
	System.out.println("getRegistrationObject");
	RemoteEvent instance = e;
	MarshalledObject expResult = m;
	@SuppressWarnings("deprecation")
	MarshalledObject result = instance.getRegistrationObject();
	assertEquals(expResult, result);
    }
    
    @Test
    public void testSerialization() {
	ObjectOutputStream oos = null;
	System.out.println("test serialization");
	MarshalledObject expResult = m;
	try {
	    ByteArrayOutputStream baos = new ByteArrayOutputStream();
	    oos = new ObjectOutputStream(baos);
	    oos.writeObject(e);
	    oos.flush();
	    byte[] bytes = baos.toByteArray();
	    ByteArrayInputStream bais = new ByteArrayInputStream(bytes);
	    ObjectInputStream ois = new ObjectInputStream(bais);
	    RemoteEvent result = (RemoteEvent) ois.readObject();
	    MarshalledObject moResult = result.getRegistrationObject();
	    Object srcResult = result.getSource();
	    long iDResult = result.getID();
	    long seqResult = result.getSequenceNumber();
	    assertEquals(expResult, moResult);
	    assertEquals(source, srcResult);
	    assertEquals(10L, iDResult);
	    assertEquals(25L, seqResult);
	} catch (IOException ex) {
	    Logger.getLogger(RemoteEventTest.class.getName()).log(Level.SEVERE, null, ex);
	} catch (ClassNotFoundException ex) {
	    Logger.getLogger(RemoteEventTest.class.getName()).log(Level.SEVERE, null, ex);
	} finally {
	    try {
		oos.close();
	    } catch (IOException ex) {
		Logger.getLogger(RemoteEventTest.class.getName()).log(Level.SEVERE, null, ex);
	    }
	}
    }

}