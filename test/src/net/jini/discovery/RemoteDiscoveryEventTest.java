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

package net.jini.discovery;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.rmi.MarshalledObject;
import java.rmi.RemoteException;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.jini.core.discovery.LookupLocator;
import net.jini.core.lookup.PortableServiceRegistrar;
import net.jini.core.lookup.ServiceID;
import net.jini.core.lookup.ServiceItem;
import net.jini.core.lookup.ServiceMatches;
import net.jini.core.lookup.ServiceRegistrar;
import net.jini.core.lookup.ServiceRegistration;
import net.jini.core.lookup.ServiceTemplate;
import net.jini.io.MarshalledInstance;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/**
 *
 * @author peter
 */
public class RemoteDiscoveryEventTest {
    String s = "happy";
    Object source = "source";
    MarshalledObject<String> m = null;
    RemoteDiscoveryEvent e = null;
    MarshalledObject<String> mo = null;
    Map groups = new HashMap(1);
    String g = "grp";
    PortableServiceRegistrar psr = new PSR();
    
private static class PSR implements PortableServiceRegistrar, Serializable {
	private final ServiceID s = new ServiceID(36L, 58098L);  

	public Class[] getEntryClasses(ServiceTemplate tmpl) throws RemoteException {
	    throw new UnsupportedOperationException("Not supported yet.");
	}

	public Object[] getFieldValues(ServiceTemplate tmpl, int setIndex, String field) throws NoSuchFieldException, RemoteException {
	    throw new UnsupportedOperationException("Not supported yet.");
	}

	public String[] getGroups() throws RemoteException {
	    String[] groups = new String[1];
	    groups[0] = "grp";
	    return groups;
	}

	public LookupLocator getLocator() throws RemoteException {
	    throw new UnsupportedOperationException("Not supported yet.");
	}

	public ServiceID getServiceID() {
	    return s;    
	}

	public Class[] getServiceTypes(ServiceTemplate tmpl, String prefix) throws RemoteException {
	    throw new UnsupportedOperationException("Not supported yet.");
	}

	public Object lookup(ServiceTemplate tmpl) throws RemoteException {
	    throw new UnsupportedOperationException("Not supported yet.");
	}

	public ServiceMatches lookup(ServiceTemplate tmpl, int maxMatches) throws RemoteException {
	    throw new UnsupportedOperationException("Not supported yet.");
	}

	public ServiceRegistration register(ServiceItem item, long leaseDuration) throws RemoteException {
	    throw new UnsupportedOperationException("Not supported yet.");
	}
	
	@Override
	public boolean equals(Object o){
	    if (o == this) return true;
	    if (!(o instanceof PSR)) return false;
	    PSR that = (PSR) o;
	    if (s.equals(that.s)) return true;
	    return false;
	}

	@Override
	public int hashCode() {
	    int hash = 3;
	    hash = 79 * hash + (this.s != null ? this.s.hashCode() : 0);
	    return hash;
	}
	
    };
    
    
    
    public RemoteDiscoveryEventTest() {
    }

    @Before
    public void setUp() {
	groups.put(psr, g);
	try {
	    m = new MarshalledObject<String>(s);
	    e = new RemoteDiscoveryEvent(source, 10L, 25L, m, true, groups );
	} catch (IOException ex) {
	    Logger.getLogger(RemoteDiscoveryEventTest.class.getName()).log(Level.SEVERE, null, ex);
	}
    }

//    /**
//     * Test of isDiscarded method, of class RemoteDiscoveryEvent.
//     */
//    @Test
//    public void isDiscarded() {
//	System.out.println("isDiscarded");
//	RemoteDiscoveryEvent instance = null;
//	boolean expResult = false;
//	boolean result = instance.isDiscarded();
//	assertEquals(expResult, result);
//	// TODO review the generated test code and remove the default call to fail.
//	fail("The test case is a prototype.");
//    }
//
//    /**
//     * Test of getRegistrars method, of class RemoteDiscoveryEvent.
//     */
//    @Test
//    public void getRegistrars() throws Exception {
//	System.out.println("getRegistrars");
//	RemoteDiscoveryEvent instance = null;
//	ServiceRegistrar[] expResult = null;
//	ServiceRegistrar[] result = instance.getRegistrars();
//	assertEquals(expResult, result);
//	// TODO review the generated test code and remove the default call to fail.
//	fail("The test case is a prototype.");
//    }
//
//    /**
//     * Test of getPRegistrars method, of class RemoteDiscoveryEvent.
//     */
//    @Test
//    public void getPRegistrars() throws Exception {
//	System.out.println("getPRegistrars");
//	RemoteDiscoveryEvent instance = null;
//	PortableServiceRegistrar[] expResult = null;
//	PortableServiceRegistrar[] result = instance.getPRegistrars();
//	assertEquals(expResult, result);
//	// TODO review the generated test code and remove the default call to fail.
//	fail("The test case is a prototype.");
//    }
//
//    /**
//     * Test of getGroups method, of class RemoteDiscoveryEvent.
//     */
//    @Test
//    public void getGroups() {
//	System.out.println("getGroups");
//	RemoteDiscoveryEvent instance = null;
//	Map expResult = null;
//	Map result = instance.getGroups();
//	assertEquals(expResult, result);
//	// TODO review the generated test code and remove the default call to fail.
//	fail("The test case is a prototype.");
//    }
    
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
	    RemoteDiscoveryEvent result = (RemoteDiscoveryEvent) ois.readObject();
	    MarshalledObject moResult = result.getRegistrationObject();
	    Object srcResult = result.getSource();
	    long iDResult = result.getID();
	    long seqResult = result.getSequenceNumber();
	    assertEquals(expResult, moResult);
	    assertEquals(source, srcResult);
	    assertEquals(10L, iDResult);
	    assertEquals(25L, seqResult);
	} catch (IOException ex) {
	    Logger.getLogger(RemoteDiscoveryEventTest.class.getName()).log(Level.SEVERE, null, ex);
	} catch (ClassNotFoundException ex) {
	    Logger.getLogger(RemoteDiscoveryEventTest.class.getName()).log(Level.SEVERE, null, ex);
	} finally {
	    try {
		oos.close();
	    } catch (IOException ex) {
		Logger.getLogger(RemoteDiscoveryEventTest.class.getName()).log(Level.SEVERE, null, ex);
	    }
	}
    }
    

}