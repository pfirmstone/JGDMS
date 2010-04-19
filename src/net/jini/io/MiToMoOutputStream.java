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
import java.lang.reflect.Constructor;
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
public class MiToMoOutputStream extends ObjectOutputStream{
    private static volatile ObjectStreamClass cachedTempDesc;
    
public MiToMoOutputStream(OutputStream out) throws IOException {
        super(out);
        useProtocolVersion(PROTOCOL_VERSION_2);
    }
    @Override
    protected void writeClassDescriptor(ObjectStreamClass desc){
        try {
            System.out.println(desc.getName());
            if (desc.getName().equals("net.jini.io.MarshalledInstance") ) {
                if (cachedTempDesc == null) {
                    //Duplicate desc so we don't posion our local cache.
                    Constructor constr = ObjectStreamClass.class.getDeclaredConstructor();
                    constr.setAccessible(true);
                    ObjectStreamClass tempDesc = (ObjectStreamClass) constr.newInstance();
                    Field[] fields = ObjectStreamClass.class.getDeclaredFields();
                    int l = fields.length;
                    for (int i = 0; i < l; i++) {
                        fields[i].setAccessible(true);
                        if (fields[i].getName().equals("name")) {
                            fields[i].set(tempDesc, "java.rmi.MarshalledObject");
                            continue;
                        }
                        if (fields[i].getName().equals("hasWriteObjectData")) {
                            fields[i].setBoolean(tempDesc, false);
                            continue;
                        }
                        if (fields[i].getName().equals("readObjectMethod")) {
                            fields[i].set(tempDesc, null);
                            continue;
                        }
                        if (fields[i].getName().equals("writeObjectMethod")) {
                            fields[i].set(tempDesc, null);
                            continue;
                        }
                        if (fields[i].getName().equals("suid")) {
                            Long moSuid = new Long(8988374069173025854L); //MarshalledObject serialVersionUID
                            fields[i].set(tempDesc, moSuid);
                            continue;
                        }
                        Object fieldValue = fields[i].get(desc);
                        try {
                            fields[i].set(tempDesc, fieldValue);
                            //If it was static the reference is the same anyway.
                            // would prefer not to set static fields.
                            // Alternative is to check and set every wanted field.
                        } catch (IllegalAccessException ex) {
                            // final field unmodifiable.
                            continue;
                        } 
                    }// End for loop
                    if (cachedTempDesc == null){ cachedTempDesc = tempDesc;}
                }//End if (cachedTempDesc == null)               
                desc = cachedTempDesc;               
            }//End if net.jini.io.MarshalledInstance
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
