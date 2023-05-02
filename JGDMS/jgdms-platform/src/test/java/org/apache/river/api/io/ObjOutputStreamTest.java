/*
 * Copyright 2021 The Apache Software Foundation.
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
import java.io.ObjectOutput;
import java.io.ObjectOutputStream;
import java.io.ObjectStreamClass;
import net.jini.io.MarshalledInstance;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import static org.junit.Assert.*;
import tests.support.Have;
import tests.support.SerializableTestObject;
import tests.support.SerializableTestObjectNoFields;
import tests.support.SerializableTestSubclass;

/**
 *
 * @author peter
 */
public class ObjOutputStreamTest {
    
    public ObjOutputStreamTest() {
    }
    
    @BeforeClass
    public static void setUpClass() {
    }
    
    @AfterClass
    public static void tearDownClass() {
    }
    
    @Before
    public void setUp() {
    }
    
    @After
    public void tearDown() {
    }

    /**
     * Test of annotateClass method, of class ObjOutputStream.
     */
//    @Test
//    public void testAnnotateClass() throws Exception {
//        System.out.println("annotateClass");
//        Class aClass = null;
//        ObjOutputStream instance = new ObjOutputStream();
//        instance.annotateClass(aClass);
//        // TODO review the generated test code and remove the default call to fail.
//        fail("The test case is a prototype.");
//    }

    /**
     * Test of annotateProxyClass method, of class ObjOutputStream.
     */
//    @Test
//    public void testAnnotateProxyClass() throws Exception {
//        System.out.println("annotateProxyClass");
//        Class aClass = null;
//        ObjOutputStream instance = new ObjOutputStream();
//        instance.annotateProxyClass(aClass);
//        // TODO review the generated test code and remove the default call to fail.
//        fail("The test case is a prototype.");
//    }

    /**
     * Test of close method, of class ObjOutputStream.
     */
//    @Test
//    public void testClose() throws Exception {
//        System.out.println("close");
//        ObjOutputStream instance = new ObjOutputStream();
//        instance.close();
//        // TODO review the generated test code and remove the default call to fail.
//        fail("The test case is a prototype.");
//    }

    /**
     * Test of defaultWriteObject method, of class ObjOutputStream.
     */
//    @Test
//    public void testDefaultWriteObject() throws Exception {
//        System.out.println("defaultWriteObject");
//        ObjOutputStream instance = new ObjOutputStream();
//        instance.defaultWriteObject();
//        // TODO review the generated test code and remove the default call to fail.
//        fail("The test case is a prototype.");
//    }

    /**
     * Test of drain method, of class ObjOutputStream.
     */
//    @Test
//    public void testDrain() throws Exception {
//        System.out.println("drain");
//        ObjOutputStream instance = new ObjOutputStream();
//        instance.drain();
//        // TODO review the generated test code and remove the default call to fail.
//        fail("The test case is a prototype.");
//    }

    /**
     * Test of enableReplaceObject method, of class ObjOutputStream.
     */
//    @Test
//    public void testEnableReplaceObject() throws IOException {
//        System.out.println("enableReplaceObject");
//        boolean enable = false;
//        ObjOutputStream instance = new ObjOutputStream();
//        boolean expResult = false;
//        boolean result = instance.enableReplaceObject(enable);
//        assertEquals(expResult, result);
//        // TODO review the generated test code and remove the default call to fail.
//        fail("The test case is a prototype.");
//    }

    /**
     * Test of flush method, of class ObjOutputStream.
     */
//    @Test
//    public void testFlush() throws Exception {
//        System.out.println("flush");
//        ObjOutputStream instance = new ObjOutputStream();
//        instance.flush();
//        // TODO review the generated test code and remove the default call to fail.
//        fail("The test case is a prototype.");
//    }

    /**
     * Test of putFields method, of class ObjOutputStream.
     */
//    @Test
//    public void testPutFields() throws Exception {
//        System.out.println("putFields");
//        ObjOutputStream instance = new ObjOutputStream();
//        ObjectOutputStream.PutField expResult = null;
//        ObjectOutputStream.PutField result = instance.putFields();
//        assertEquals(expResult, result);
//        // TODO review the generated test code and remove the default call to fail.
//        fail("The test case is a prototype.");
//    }

    /**
     * Test of replaceObject method, of class ObjOutputStream.
     */
//    @Test
//    public void testReplaceObject() throws Exception {
//        System.out.println("replaceObject");
//        Object object = null;
//        ObjOutputStream instance = new ObjOutputStream();
//        Object expResult = null;
//        Object result = instance.replaceObject(object);
//        assertEquals(expResult, result);
//        // TODO review the generated test code and remove the default call to fail.
//        fail("The test case is a prototype.");
//    }

    /**
     * Test of reset method, of class ObjOutputStream.
     */
//    @Test
//    public void testReset() throws Exception {
//        System.out.println("reset");
//        ObjOutputStream instance = new ObjOutputStream();
//        instance.reset();
//        // TODO review the generated test code and remove the default call to fail.
//        fail("The test case is a prototype.");
//    }

    /**
     * Test of useProtocolVersion method, of class ObjOutputStream.
     */
//    @Test
//    public void testUseProtocolVersion() throws Exception {
//        System.out.println("useProtocolVersion");
//        int version = 0;
//        ObjOutputStream instance = new ObjOutputStream();
//        instance.useProtocolVersion(version);
//        // TODO review the generated test code and remove the default call to fail.
//        fail("The test case is a prototype.");
//    }

    /**
     * Test of write method, of class ObjOutputStream.
     */
//    @Test
//    public void testWrite_byteArr() throws Exception {
//        System.out.println("write");
//        byte[] buffer = null;
//        ObjOutputStream instance = new ObjOutputStream();
//        instance.write(buffer);
//        // TODO review the generated test code and remove the default call to fail.
//        fail("The test case is a prototype.");
//    }

    /**
     * Test of write method, of class ObjOutputStream.
     */
//    @Test
//    public void testWrite_3args() throws Exception {
//        System.out.println("write");
//        byte[] buffer = null;
//        int offset = 0;
//        int length = 0;
//        ObjOutputStream instance = new ObjOutputStream();
//        instance.write(buffer, offset, length);
//        // TODO review the generated test code and remove the default call to fail.
//        fail("The test case is a prototype.");
//    }
    /**
     * Test of write method, of class ObjOutputStream.
     */
    @Test
    public void testWrite_int() throws Exception {
        System.out.println("writeInt");
        int value = 0;
        System.out.println("testAtomicInputStreamCompatiblity");
	ByteArrayOutputStream baos = null;
	byte[] data;
	ObjOutputStream oos = null;
	ByteArrayInputStream bais = null;
	AtomicMarshalInputStream ois = null;
	try {
	    baos = new ByteArrayOutputStream();
	    oos = new ObjOutputStream(new BufferedOutputStream(baos));
	    oos.writeInt(value);
	    oos.flush();
	    data = baos.toByteArray();
	    bais = new ByteArrayInputStream(data);
	    ois = new AtomicMarshalInputStream(new BufferedInputStream(bais), null, false, null, null);
	    int result = ois.readInt();
	    assertEquals(value, result);
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
     * Test of write method, of class ObjOutputStream.
     */
    @Test
    public void testWrite_string() throws Exception {
        System.out.println("writeString");
        System.out.println("testObjectInputStreamCompatiblity");
	String string = "Test object";
	ByteArrayOutputStream baos = null;
	byte[] data;
	ObjOutputStream oos = null;
	ByteArrayInputStream bais = null;
	AtomicMarshalInputStream ois = null;
	try {
	    baos = new ByteArrayOutputStream();
	    oos = new ObjOutputStream(new BufferedOutputStream(baos));
	    oos.writeObject(string);
	    oos.flush();
	    data = baos.toByteArray();
	    bais = new ByteArrayInputStream(data);
	    ois = new AtomicMarshalInputStream(new BufferedInputStream(bais), null, false, null, null);
	    String result = ois.readObject(String.class);
	    assertEquals(string, result);
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
     * Test of writeBoolean method, of class ObjOutputStream.
     */
    @Test
    public void testWriteBoolean() throws Exception {
        System.out.println("writeBoolean");
        boolean value = false;System.out.println("writeInt");
        System.out.println("testAtomicInputStreamCompatiblity");
	ByteArrayOutputStream baos = null;
	byte[] data;
	ObjOutputStream oos = null;
	ByteArrayInputStream bais = null;
	AtomicMarshalInputStream ois = null;
	try {
	    baos = new ByteArrayOutputStream();
	    oos = new ObjOutputStream(new BufferedOutputStream(baos));
	    oos.writeBoolean(value);
	    oos.flush();
	    data = baos.toByteArray();
	    bais = new ByteArrayInputStream(data);
	    ois = new AtomicMarshalInputStream(new BufferedInputStream(bais), null, false, null, null);
	    boolean result = ois.readBoolean();
	    assertEquals(value, result);
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
     * Test of writeByte method, of class ObjOutputStream.
     */
    @Test
    public void testWriteByte() throws Exception {
        System.out.println("writeByte");
        byte value = 0;
        System.out.println("testAtomicInputStreamCompatiblity");
	ByteArrayOutputStream baos = null;
	byte[] data;
	ObjOutputStream oos = null;
	ByteArrayInputStream bais = null;
	AtomicMarshalInputStream ois = null;
	try {
	    baos = new ByteArrayOutputStream();
	    oos = new ObjOutputStream(new BufferedOutputStream(baos));
	    oos.writeByte(value);
	    oos.flush();
	    data = baos.toByteArray();
	    bais = new ByteArrayInputStream(data);
	    ois = new AtomicMarshalInputStream(new BufferedInputStream(bais), null, false, null, null);
	    byte result = ois.readByte();
	    assertEquals(value, result);
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
     * Test of writeChar method, of class ObjOutputStream.
     */
    @Test
    public void testWriteChar() throws Exception {
        System.out.println("writeChar");
        char value = 'w';
        System.out.println("testAtomicInputStreamCompatiblity");
	ByteArrayOutputStream baos = null;
	byte[] data;
	ObjOutputStream oos = null;
	ByteArrayInputStream bais = null;
	AtomicMarshalInputStream ois = null;
	try {
	    baos = new ByteArrayOutputStream();
	    oos = new ObjOutputStream(new BufferedOutputStream(baos));
	    oos.writeChar(value);
	    oos.flush();
	    data = baos.toByteArray();
	    bais = new ByteArrayInputStream(data);
	    ois = new AtomicMarshalInputStream(new BufferedInputStream(bais), null, false, null, null);
	    char result = ois.readChar();
	    assertEquals(value, result);
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
     * Test of writeDouble method, of class ObjOutputStream.
     */
    @Test
    public void testWriteDouble() throws Exception {
        System.out.println("writeDouble");
        double value = 0.0;
        System.out.println("testAtomicInputStreamCompatiblity");
	ByteArrayOutputStream baos = null;
	byte[] data;
	ObjOutputStream oos = null;
	ByteArrayInputStream bais = null;
	AtomicMarshalInputStream ois = null;
	try {
	    baos = new ByteArrayOutputStream();
	    oos = new ObjOutputStream(new BufferedOutputStream(baos));
	    oos.writeDouble(value);
	    oos.flush();
	    data = baos.toByteArray();
	    bais = new ByteArrayInputStream(data);
	    ois = new AtomicMarshalInputStream(new BufferedInputStream(bais), null, false, null, null);
	    double result = ois.readDouble();
	    assertEquals(value, result, 0.0);
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
     * Test of writeFloat method, of class ObjOutputStream.
     */
    @Test
    public void testWriteFloat() throws Exception {
        System.out.println("writeFloat");
        float value = 0.0F;
        System.out.println("testAtomicInputStreamCompatiblity");
	ByteArrayOutputStream baos = null;
	byte[] data;
	ObjOutputStream oos = null;
	ByteArrayInputStream bais = null;
	AtomicMarshalInputStream ois = null;
	try {
	    baos = new ByteArrayOutputStream();
	    oos = new ObjOutputStream(new BufferedOutputStream(baos));
	    oos.writeFloat(value);
	    oos.flush();
	    data = baos.toByteArray();
	    bais = new ByteArrayInputStream(data);
	    ois = new AtomicMarshalInputStream(new BufferedInputStream(bais), null, false, null, null);
	    float result = ois.readFloat();
	    assertEquals(value, result, 0.0F);
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
     * Test of writeInt method, of class ObjOutputStream.
     */
    @Test
    public void testWriteInt() throws Exception {
        System.out.println("writeInt");
        int value = 0;
        System.out.println("testAtomicInputStreamCompatiblity");
	ByteArrayOutputStream baos = null;
	byte[] data;
	ObjOutputStream oos = null;
	ByteArrayInputStream bais = null;
	AtomicMarshalInputStream ois = null;
	try {
	    baos = new ByteArrayOutputStream();
	    oos = new ObjOutputStream(new BufferedOutputStream(baos));
	    oos.writeInt(value);
	    oos.flush();
	    data = baos.toByteArray();
	    bais = new ByteArrayInputStream(data);
	    ois = new AtomicMarshalInputStream(new BufferedInputStream(bais), null, false, null, null);
	    int result = ois.readInt();
	    assertEquals(value, result);
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
     * Test of writeLong method, of class ObjOutputStream.
     */
    @Test
    public void testWriteLong() throws Exception {
        System.out.println("writeLong");
        long value = 0L;
        System.out.println("testAtomicInputStreamCompatiblity");
	ByteArrayOutputStream baos = null;
	byte[] data;
	ObjOutputStream oos = null;
	ByteArrayInputStream bais = null;
	AtomicMarshalInputStream ois = null;
	try {
	    baos = new ByteArrayOutputStream();
	    oos = new ObjOutputStream(new BufferedOutputStream(baos));
	    oos.writeLong(value);
	    oos.flush();
	    data = baos.toByteArray();
	    bais = new ByteArrayInputStream(data);
	    ois = new AtomicMarshalInputStream(new BufferedInputStream(bais), null, false, null, null);
	    long result = ois.readLong();
	    assertEquals(value, result);
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
     * Test of writeLong method, of class ObjOutputStream.
     */
    @Test
    public void testWriteArray() throws Exception {
        System.out.println("writeArray");
        long [] value = new long [] {0L, 1024L, 5L};
        System.out.println("testAtomicInputStreamCompatiblity");
	ByteArrayOutputStream baos = null;
	byte[] data;
	ObjOutputStream oos = null;
	ByteArrayInputStream bais = null;
	AtomicMarshalInputStream ois = null;
	try {
	    baos = new ByteArrayOutputStream();
	    oos = new ObjOutputStream(new BufferedOutputStream(baos));
	    oos.writeObject(value);
	    oos.flush();
	    data = baos.toByteArray();
	    bais = new ByteArrayInputStream(data);
	    ois = new AtomicMarshalInputStream(new BufferedInputStream(bais), null, false, null, null);
	    long [] result = ois.readObject(long[].class);
	    assertArrayEquals(value, result);
        } catch (Exception e){
            e.printStackTrace(System.out);
            throw e;
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
     * Test of writeObject method, of class ObjOutputStream.
     */
    @Test
    public void testWriteObject() throws Exception {
        System.out.println("writeObject");
        Object object = new SerializableTestObject(
                "lucky",
                new long []{1L, 3L},
                10,
                true,
                (byte)1,
                (char)10,
                (short)10,
                80L,
                5.5F,
                2.1);
        System.out.println("testAtomicInputStreamCompatiblity");
	ByteArrayOutputStream baos = null;
	byte[] data;
	ObjOutputStream oos = null;
	ByteArrayInputStream bais = null;
	AtomicMarshalInputStream ois = null;
	try {
	    baos = new ByteArrayOutputStream();
	    oos = new ObjOutputStream(new BufferedOutputStream(baos));
	    oos.writeObject(object);
            oos.writeBoolean(true);
	    oos.flush();
	    data = baos.toByteArray();
	    bais = new ByteArrayInputStream(data);
	    ois = new AtomicMarshalInputStream(new BufferedInputStream(bais), null, false, null, null);
	    SerializableTestObject result = ois.readObject(SerializableTestObject.class);
            assertTrue(ois.readBoolean());
	    assertEquals(object, result);
        } catch (Exception e){
            e.printStackTrace(System.out);
            throw e;
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
     * Test of writeObject method, of class ObjOutputStream.
     */
    @Test
    public void testWriteObjectOOS() throws Exception {
        System.out.println("writeObject OOS compat check");
        Object object = new SerializableTestObject(
                "lucky",
                new long []{1L, 3L},
                10,
                true,
                (byte)1,
                (char)10,
                (short)10,
                80L,
                5.5F,
                2.1);
        System.out.println("testAtomicInputStreamCompatiblity");
	ByteArrayOutputStream baos = null;
	byte[] data;
	ObjOutputStream oos = null;
	ByteArrayInputStream bais = null;
	AtomicMarshalInputStream ois = null;
	try {
	    baos = new ByteArrayOutputStream();
	    oos = new ObjOutputStream(new BufferedOutputStream(baos));
	    oos.writeObject(object);
            oos.writeBoolean(true);
	    oos.flush();
	    data = baos.toByteArray();
	    bais = new ByteArrayInputStream(data);
	    ois = new AtomicMarshalInputStream(new BufferedInputStream(bais), null, false, null, null);
	    SerializableTestObject result = ois.readObject(SerializableTestObject.class);
            assertTrue(ois.readBoolean());
	    assertEquals(object, result);
        } catch (Exception e){
            e.printStackTrace(System.out);
            throw e;
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
     * Test of writeObject method, of class ObjOutputStream.
     */
    @Test
    public void testWriteObjectOOSNOFields() throws Exception {
        System.out.println("writeObjectOutputStream No Fields");
        Object object = new SerializableTestObjectNoFields(
                "lucky",
                new long []{1L, 3L},
                10,
                true,
                (byte)1,
                (char)10,
                (short)10,
                80L,
                5.5F,
                2.1);
        System.out.println("testAtomicInputStreamCompatiblity");
	ByteArrayOutputStream baos = null;
	byte[] data;
	ObjectOutputStream oos = null;
	ByteArrayInputStream bais = null;
	AtomicMarshalInputStream ois = null;
	try {
	    baos = new ByteArrayOutputStream();
	    oos = new ObjectOutputStream(new BufferedOutputStream(baos));
	    oos.writeObject(object);
            oos.writeBoolean(true);
	    oos.flush();
	    data = baos.toByteArray();
	    bais = new ByteArrayInputStream(data);
	    ois = new AtomicMarshalInputStream(new BufferedInputStream(bais), null, false, null, null);
	    SerializableTestObjectNoFields result = ois.readObject(SerializableTestObjectNoFields.class);
            assertTrue(ois.readBoolean());
	    assertEquals(object, result);
        } catch (Exception e){
            e.printStackTrace(System.out);
            throw e;
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
     * Test of writeObject method, of class ObjOutputStream.
     */
    @Test
    public void testWriteObjectBlockData() throws Exception {
        System.out.println("writeObjectBlockData");
        Object object = new SerializableTestObjectNoFields(
                "lucky",
                new long []{1L, 3L},
                10,
                true,
                (byte)1,
                (char)10,
                (short)10,
                80L,
                5.5F,
                2.1);
        System.out.println("testAtomicInputStreamCompatiblity");
	ByteArrayOutputStream baos = null;
	byte[] data;
	ObjOutputStream oos = null;
	ByteArrayInputStream bais = null;
	AtomicMarshalInputStream ois = null;
	try {
	    baos = new ByteArrayOutputStream();
	    oos = new ObjOutputStream(new BufferedOutputStream(baos));
	    oos.writeObject(object);
            oos.writeBoolean(true);
	    oos.flush();
	    data = baos.toByteArray();
	    bais = new ByteArrayInputStream(data);
	    ois = new AtomicMarshalInputStream(new BufferedInputStream(bais), null, false, null, null);
	    SerializableTestObjectNoFields result = ois.readObject(SerializableTestObjectNoFields.class);
            assertTrue(ois.readBoolean());
	    assertEquals(object, result);
        } catch (Exception e){
            e.printStackTrace(System.out);
            throw e;
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
     * Test of writeObject method, of class ObjOutputStream.
     */
    @Test
    public void testWriteObjectSubclass() throws Exception {
        System.out.println("writeObjectSubclass");
        SerializableTestObject object = new SerializableTestObject(
                "lucky",
                new long []{1L, 3L},
                10,
                false,
                (byte)0,
                (char)'a',
                (short)0,
                0L,
                0.0F,
                0.0);
        object = new SerializableTestSubclass("lucky",
                new long []{1L, 3L},
                10,
                false,
                (byte)0,
                (char)'a',
                (short)0,
                0L,
                0.0F,
                0.0,
                object);
        System.out.println("testAtomicInputStreamCompatiblity");
	ByteArrayOutputStream baos = null;
	byte[] data;
	ObjectOutput oos = null;
	ByteArrayInputStream bais = null;
	AtomicMarshalInputStream ois = null;
	try {
	    baos = new ByteArrayOutputStream();
	    oos = new ObjOutputStream(new BufferedOutputStream(baos));
	    oos.writeObject(object);
	    oos.flush();
	    data = baos.toByteArray();
	    bais = new ByteArrayInputStream(data);
	    ois = new AtomicMarshalInputStream(new BufferedInputStream(bais), null, false, null, null);
	    SerializableTestSubclass result = ois.readObject(SerializableTestSubclass.class);
	    assertEquals(object, result);
        } catch (Exception e){
            e.printStackTrace(System.out);
            throw e;
	} finally {
	    try {
		if (oos != null) {
                    System.out.println("*************");
                    System.out.println("Objects written to stream: \n" +oos);
                    System.out.println("*************");
                    oos.close();
                }
		else if (baos != null) baos.close();
		if (ois != null) {
                    System.out.println("*************");
                    System.out.println("Objects read from stream: \n" +ois);
                    System.out.println("*************");
                    ois.close();
                }
		else if (bais != null) bais.close();
	    } catch (IOException e){}
	}
    }
    
    /**
     * Test of writeShort method, of class ObjOutputStream.
     */
    @Test
    public void testWriteEnum() throws Exception {
        System.out.println("writeEnum");
        Have value = Have.HOUSE;
        System.out.println("testAtomicInputStreamCompatiblity");
	ByteArrayOutputStream baos = null;
	byte[] data;
	ObjOutputStream oos = null;
	ByteArrayInputStream bais = null;
	AtomicMarshalInputStream ois = null;
	try {
	    baos = new ByteArrayOutputStream();
	    oos = new ObjOutputStream(new BufferedOutputStream(baos));
	    oos.writeObject(value);
	    oos.flush();
	    data = baos.toByteArray();
	    bais = new ByteArrayInputStream(data);
	    ois = new AtomicMarshalInputStream(new BufferedInputStream(bais), null, false, null, null);
	    Have result = ois.readObject(Have.class);
	    assertEquals(value, result);
        } catch (Exception e){
            e.printStackTrace(System.out);
            throw e;
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
     * Test of writeShort method, of class ObjOutputStream.
     */
    @Test
    public void testWriteShort() throws Exception {
        System.out.println("writeShort");
        short value = 16;
        System.out.println("testAtomicInputStreamCompatiblity");
	ByteArrayOutputStream baos = null;
	byte[] data;
	ObjOutputStream oos = null;
	ByteArrayInputStream bais = null;
	AtomicMarshalInputStream ois = null;
	try {
	    baos = new ByteArrayOutputStream();
	    oos = new ObjOutputStream(new BufferedOutputStream(baos));
	    oos.writeShort(value);
	    oos.flush();
	    data = baos.toByteArray();
	    bais = new ByteArrayInputStream(data);
	    ois = new AtomicMarshalInputStream(new BufferedInputStream(bais), null, false, null, null);
	    short result = ois.readShort();
	    assertEquals(value, result);
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
     * Test of writeUTF method, of class ObjOutputStream.
     */
    @Test
    public void testWriteUTF() throws Exception {
        System.out.println("writeUTF");
        String value = "UTF String";
        System.out.println("testAtomicInputStreamCompatiblity");
	ByteArrayOutputStream baos = null;
	byte[] data;
	ObjOutputStream oos = null;
	ByteArrayInputStream bais = null;
	AtomicMarshalInputStream ois = null;
	try {
	    baos = new ByteArrayOutputStream();
	    oos = new ObjOutputStream(new BufferedOutputStream(baos));
	    oos.writeUTF(value);
	    oos.flush();
	    data = baos.toByteArray();
	    bais = new ByteArrayInputStream(data);
	    ois = new AtomicMarshalInputStream(new BufferedInputStream(bais), null, false, null, null);
	    String result = ois.readUTF();
	    assertEquals(value, result);
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
