/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package org.apache.river.api.io;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectInputValidation;
import java.io.ObjectOutputStream;
import java.rmi.UnmarshalException;
import java.util.Collection;
import net.jini.io.MarshalInputStream;
import net.jini.io.MarshalledInstance;
import static org.junit.Assert.assertEquals;

import org.junit.Test;
import tests.support.Have;


/**
 *
 * @author peter
 */
public class AtomicMarshalInputStreamTest {
  
    @Test
    public void testReadObject() throws Exception {
	System.out.println("available");
	ByteArrayOutputStream baos = new ByteArrayOutputStream();
	ObjectOutputStream oos = new AtomicMarshalOutputStream(baos, null);
	Have expResult = Have.SERVICE;
	oos.writeObject(expResult);
	oos.flush();
	byte [] bits = baos.toByteArray();
	ByteArrayInputStream bais = new ByteArrayInputStream(bits);
	ObjectInputStream instance = AtomicMarshalInputStream.create(bais, null, false, null, null, false);
	Have result = (Have) instance.readObject();
	assertEquals(expResult, result);
    }
    
    @Test
    public void testException() throws Exception {
	System.out.println("Exception test");
	ByteArrayOutputStream baos = new ByteArrayOutputStream();
	ObjectOutputStream oos = new AtomicMarshalOutputStream(baos, null);
	UnmarshalException ue = new UnmarshalException("Synthetic");
	oos.writeObject(ue);
	oos.flush();
	byte [] bits = baos.toByteArray();
	ByteArrayInputStream bais = new ByteArrayInputStream(bits);
	ObjectInputStream in = AtomicMarshalInputStream.create(bais, null, false, null, null, false);
	UnmarshalException ue2 = (UnmarshalException) in.readObject();
	assertEquals(ue.getMessage(), ue2.getMessage());
    }

    /**
     * Test of create method, of class AtomicMarshalInputStream.
     */
    @Test
    public void testObjectOutputStreamCompatibility() throws Exception {
	System.out.println("test ObjectOutputStream compatibility");
	MarshalledInstance mi = new MarshalledInstance("Test object");
	ByteArrayOutputStream baos = null;
	byte[] data;
	ObjectOutputStream oos = null;
	ByteArrayInputStream bais = null;
	ObjectInputStream ois = null;
	try {
	    baos = new ByteArrayOutputStream();
	    oos = new ObjectOutputStream(new BufferedOutputStream(baos));
	    oos.writeObject(mi);
	    oos.flush();
	    data = baos.toByteArray();
	    bais = new ByteArrayInputStream(data);
	    ois = AtomicMarshalInputStream.create(new BufferedInputStream(bais),
		    null,
		    false,
		    null,
		    null, false
	    );
	    MarshalledInstance result = (MarshalledInstance) ois.readObject();
	    assertEquals(mi, result);
	} finally {
	    try {
		if (oos != null) oos.close();
		else if (baos != null) baos.close();
		if (ois != null) ois.close();
		else if (bais != null) bais.close();
	    } catch (IOException e){}
	}
    }

    /**
     * Test of create method, of class AtomicMarshalInputStream.
     */
    @Test
    public void testCreate() throws Exception {
	System.out.println("createObjectInputStream");
	MarshalledInstance mi = new MarshalledInstance("Test object");
	ByteArrayOutputStream baos = null;
	byte[] data;
	ObjectOutputStream oos = null;
	ByteArrayInputStream bais = null;
	ObjectInputStream ois = null;
	try {
	    baos = new ByteArrayOutputStream();
	    oos = new AtomicMarshalOutputStream(new BufferedOutputStream(baos), null, null, false);
	    oos.writeObject(mi);
	    oos.flush();
	    data = baos.toByteArray();
	    bais = new ByteArrayInputStream(data);
	    ois = AtomicMarshalInputStream.create(new BufferedInputStream(bais),
		    null,
		    false,
		    null,
		    null, false
	    );
	    MarshalledInstance result = (MarshalledInstance) ois.readObject();
	    assertEquals(mi, result);
	} finally {
	    try {
		if (oos != null) oos.close();
		else if (baos != null) baos.close();
		if (ois != null) ois.close();
		else if (bais != null) bais.close();
	    } catch (IOException e){}
	}
    } 
}
