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
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectStreamClass;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.logging.Level;
import java.util.logging.Logger;

public class MoToMiInputStream extends ObjectInputStream {

    public MoToMiInputStream(InputStream in) throws IOException {
        super(in);
    }
    
    @Override
    protected final ObjectStreamClass readClassDescriptor() 
	throws IOException, ClassNotFoundException
    {
        ObjectStreamClass desc = null;
        try {
            Constructor constr = ObjectStreamClass.class.getDeclaredConstructor();
            constr.setAccessible(true);
            desc = (ObjectStreamClass) constr.newInstance();
            Class[] parameterTypes = new Class[1];
            parameterTypes[0] = ObjectInputStream.class;
            Method readNonProxy = ObjectStreamClass.class.getDeclaredMethod("readNonProxy", parameterTypes);
            readNonProxy.setAccessible(true);
            readNonProxy.invoke(desc, this);
            if (desc.getName().equals("java.rmi.MarshalledObject")) {
                Field[] fields = ObjectStreamClass.class.getDeclaredFields();
                int l = fields.length;
                for (int i = 0; i < l; i++) {
                    fields[i].setAccessible(true);
                    if (fields[i].getName().equals("name")) {
                        fields[i].set(desc, "net.jini.io.MarshalledInstance");
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
                        continue;
                    }
                    if (fields[i].getName().equals("suid")) {
                        Long moSuid = new Long(-5187033771082433496L); //MarshalledObject serialVersionUID
                        fields[i].set(desc, moSuid);
                        continue;
                    }
                }
            }
            return desc;
        } catch (InstantiationException ex) {
            Logger.getLogger(MoToMiInputStream.class.getName()).log(Level.SEVERE, null, ex);
        } catch (IllegalAccessException ex) {
            Logger.getLogger(MoToMiInputStream.class.getName()).log(Level.SEVERE, null, ex);
        } catch (IllegalArgumentException ex) {
            Logger.getLogger(MoToMiInputStream.class.getName()).log(Level.SEVERE, null, ex);
        } catch (InvocationTargetException ex) {
            Logger.getLogger(MoToMiInputStream.class.getName()).log(Level.SEVERE, null, ex);
        } catch (NoSuchMethodException ex) {
            Logger.getLogger(MoToMiInputStream.class.getName()).log(Level.SEVERE, null, ex);
        } catch (SecurityException ex) {
            Logger.getLogger(MoToMiInputStream.class.getName()).log(Level.SEVERE, null, ex);
        }
        return desc;
    }

}
