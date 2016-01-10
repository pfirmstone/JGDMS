/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package org.apache.river.api.io;

import tests.support.Have;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import static org.junit.Assert.*;
import org.junit.Test;

/**
 *
 * @author peter
 */
public class AtomicMarshalInputStreamTest {
  
    /**
     * Test of available method, of class AtomicMarshalInputStream.
     */
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
	ObjectInputStream instance = AtomicMarshalInputStream.create(bais, null, false, null, null);
	Have result = (Have) instance.readObject();
	assertEquals(expResult, result);
    }
    

}
