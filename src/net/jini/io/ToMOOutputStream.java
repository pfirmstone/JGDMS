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

package net.jini.io;

import java.io.IOException;
import java.io.ObjectOutputStream;
import java.io.ObjectStreamClass;
import java.io.OutputStream;
import java.lang.reflect.Field;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * ToMOOutputStream enables writing a jini MarshalledObject into
 * an RMI MarshalledObject Serialized form.
 * 
 * This is a nasty horrible hack to write the output stream as a 
 * java.rmi.MarshalledObject, so the CDC PersonalProfile version 1.12
 * can share this object.
 * 
 * Since this class accesses the private state of ObjectStreamClass, it is
 * tied to it's internal implementation, however since this class also
 * implements Serializable, its internal state has been publicly published
 * so must remain compatible.
 * 
 * @author Peter Firmstone.
 */
public class ToMOOutputStream extends ObjectOutputStream{
public ToMOOutputStream(OutputStream out) throws IOException {
        super(out);
        useProtocolVersion(PROTOCOL_VERSION_2);
    }
    @Override
    protected void writeClassDescriptor(ObjectStreamClass desc){
        try {
            System.out.println(desc.getName());
            if (desc.getName().equals("net.jini.io.MarshalledObject")) {
                Field[] fields = ObjectStreamClass.class.getDeclaredFields();
                int l = fields.length;
                for (int i = 0; i < l; i++) {
                    fields[i].setAccessible(true);
                    if (fields[i].getName().equals("name")) {
                        fields[i].set(desc, "java.rmi.MarshalledObject");
                        continue;
                    }
                    if (fields[i].getName().equals("hasWriteObjectData")) {
                        fields[i].setBoolean(desc, false);
                        continue;
                    }
                    if (fields[i].getName().equals("readObjectMethod")) {
                        fields[i].set(desc, null);
                        continue;
                    }
                    if (fields[i].getName().equals("writeObjectMethod")) {
                        fields[i].set(desc, null);
                    }
                }
            }
            super.writeClassDescriptor(desc);
        } catch (IllegalArgumentException ex) {
            ex.printStackTrace();
            //Logger.getLogger(ToMOOutputStream.class.getName()).log(Level.SEVERE, null, ex);
        } catch (IllegalAccessException ex) {
            ex.printStackTrace();
            //Logger.getLogger(ToMOOutputStream.class.getName()).log(Level.SEVERE, null, ex);
        } catch (IOException ex) {
            ex.printStackTrace();
            //Logger.getLogger(ToMOOutputStream.class.getName()).log(Level.SEVERE, null, ex);
        } catch (Exception ex){
            ex.printStackTrace();
            //Logger.getLogger(ToMOOutputStream.class.getName()).log(Level.SEVERE, null, ex);
        }
    }   
}
