/*
 * Copyright 2017 The Apache Software Foundation.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.river.api.io;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import net.jini.io.MarshalledInstance;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 *
 * @author peter
 */
public class AtomicMarshalOutputStreamTest {
    
    public AtomicMarshalOutputStreamTest() {
    }

    /**
     * Test of createObjectInputStream method, of class AtomicMarshalInputStream.
     */
    @Test
    public void testObjectInputStreamCompatibility() throws Exception {
	System.out.println("testObjectInputStreamCompatiblity");
	MarshalledInstance mi = new MarshalledInstance("Test object");
	ByteArrayOutputStream baos = null;
	byte[] data;
	ObjectOutputStream oos = null;
	ByteArrayInputStream bais = null;
	ObjectInputStream ois = null;
	try {
	    baos = new ByteArrayOutputStream();
	    oos = new AtomicMarshalOutputStream(new BufferedOutputStream(baos), null, null, true);
	    oos.writeObject(mi);
	    oos.flush();
	    data = baos.toByteArray();
	    bais = new ByteArrayInputStream(data);
	    ois = new ObjectInputStream(new BufferedInputStream(bais));
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
