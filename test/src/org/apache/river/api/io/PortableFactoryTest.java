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
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import junit.framework.Assert;
import static org.junit.Assert.*;
import org.junit.Test;

/**
 *
 * @author peter
 */
public class PortableFactoryTest {
    
    private final PortableFactory stringInstance;
    private final String str;
    
    public PortableFactoryTest() {
        str = "Fat Bear";
        Class[] cl = {(new char [0]).getClass()};
        Object [] chars = {str.toCharArray()};
        stringInstance = new PortableFactory(str.getClass(), null, cl , chars);
    }

    /**
     * Test of hashCode method, of class PortableFactory.
     */
    @Test
    public void testHashCode() throws IOException {
        System.out.println("hashCode");
        int result = stringInstance.create().hashCode();
        int expResult = str.hashCode();
        assertEquals(expResult, result);
        
    }

    /**
     * Test of equals method, of class PortableFactory.
     */
    @Test
    public void testEquals() throws IOException {
        System.out.println("equals");
        String expResult = str;
        Class[] cl = {(new char [0]).getClass()};
        Object [] chars = {str.toCharArray()};
        // More than one way to create a string.
        Object secondInstance = new PortableFactory(str.getClass(), null, cl, chars );
        PortableFactory thirdInstance = 
                new PortableFactory(new StringBuilder(str), "toString", null, null );
        Object result = stringInstance.create();
        Assert.assertEquals(stringInstance, secondInstance);
        // Demonstrate that equal objects can have different serial form.
        Assert.assertNotSame(stringInstance, thirdInstance);
        Assert.assertEquals(expResult, result);
        result = thirdInstance.create();
        Assert.assertEquals(expResult, result);
       
    }

    /**
     * Test of writeExternal method, of class PortableFactory.
     */
    @Test
    public void testWriteExternal() throws Exception {
        System.out.println("writeExternal");
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ObjectOutputStream outst = new ObjectOutputStream(out);
        outst.writeObject(stringInstance);
        ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(out.toByteArray()));
        Object result = in.readObject();
        assertEquals(stringInstance, result);
    }

    /**
     * Test of readExternal method, of class PortableFactory.
     */
    @Test
    public void testReadExternal() throws Exception {
        System.out.println("readExternal");
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ObjectOutputStream outst = new ObjectOutputStream(out);
        outst.writeObject(stringInstance);
        ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(out.toByteArray()));
        Object result = in.readObject();
        assertEquals(result, stringInstance);
    }
}
