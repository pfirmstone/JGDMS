/*
 *  Licensed to the Apache Software Foundation (ASF) under one or more
 *  contributor license agreements.  See the NOTICE file distributed with
 *  this work for additional information regarding copyright ownership.
 *  The ASF licenses this file to You under the Apache License, Version 2.0
 *  (the "License"); you may not use this file except in compliance with
 *  the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package org.apache.river.api.io;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import org.junit.Test;
import static org.junit.Assert.*;
import tests.support.DistributedObject;

/**
 *
 * @author peter
 */
public class DistributedObjectOutputStreamTest {
    
    public DistributedObjectOutputStreamTest() {
    }

    /**
     * Test of create method, of class DistributedObjectOutputStream.
     */
    @Test
    public void testCreate() throws Exception {
        System.out.println("create: test constructor, static method and object method");
        DistributedObject expResult = new DistributedObject("Testing");
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ObjectOutputStream outst = DistributedObjectOutputStream.create(out);
        outst.writeObject(expResult);
        ObjectInputStream in = DistributedObjectInputStream.create(new ByteArrayInputStream(out.toByteArray()));
        Object result = in.readObject();
        assertEquals(expResult.toString(), result.toString());
        out = new ByteArrayOutputStream();
        outst = DistributedObjectOutputStream.create(out);
        expResult = new DistributedObject("Testing", 1);
        outst.writeObject(expResult);
        in = DistributedObjectInputStream.create(new ByteArrayInputStream(out.toByteArray()));
        result = in.readObject();
        assertEquals(expResult.toString(), result.toString());
        expResult = new DistributedObject("Testing", 2);
        outst.writeObject(expResult);
        in = DistributedObjectInputStream.create(new ByteArrayInputStream(out.toByteArray()));
        result = in.readObject();
        assertEquals(expResult.toString(), result.toString());
    }
    
    @Test
    public void testPrimitives() throws Exception {
        System.out.println("create: test constructor, static method and object method");
        DistributedObject expResult = new DistributedObject(Boolean.TRUE);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ObjectOutputStream outst = DistributedObjectOutputStream.create(out);
        outst.writeObject(expResult);
        ObjectInputStream in = DistributedObjectInputStream.create(new ByteArrayInputStream(out.toByteArray()));
        Object result = in.readObject();
        assertEquals(expResult.toString(), result.toString());
        out = new ByteArrayOutputStream();
        outst = DistributedObjectOutputStream.create(out);
        expResult = new DistributedObject(true);
        outst.writeObject(expResult);
        in = DistributedObjectInputStream.create(new ByteArrayInputStream(out.toByteArray()));
        result = in.readObject();
        assertEquals(expResult.toString(), result.toString());
        
    }
}
