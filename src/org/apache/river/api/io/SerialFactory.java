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

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.security.AccessController;
import java.security.BasicPermission;
import java.security.Guard;
import java.security.PrivilegedActionException;
import java.security.PrivilegedExceptionAction;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Distributed form, required for Object reconstruction, using constructor, 
 * static factory method instantiation or Object builder instantiation.
 * 
 * This object must be Thread confined, it is not thread safe.
 * 
 * Internal state is guarded.
 * 
 * @author Peter Firmstone.
 * @see Distributed
 * @see DistributePermission
 */
public final class SerialFactory implements Externalizable {
    private static final long serialVersionUID = 1L;
    /* Guard private state */
    private static final Guard distributable = new DistributePermission();
    
    private Object classOrObject;
    private String method;
    private Class [] parameterTypes;
    private Object [] parameters;
    private final boolean constructed; // default value is false.
    
    public SerialFactory(){
        constructed = false;
    }
    
    /**
     * 
     * 
     * 
     * @param factoryClassOrObject will be used for constructor, factory static method,
     * or builder Object.
     * @param methodName name of static factory method, null if using a constructor.
     * @param parameterTypes Type signature of method or constructor
     * @param parameters Object to be passed to constructor.
     */
    public SerialFactory(Object factoryClassOrObject, String methodName, Class[] parameterTypes, Object [] parameters){
        classOrObject = factoryClassOrObject;
        method = methodName;
        this.parameterTypes = parameterTypes;
        this.parameters = parameters;
        constructed = true;
    }
    
    Object create() throws IOException {
        Method m;
        Constructor c;
        Class clazz;
        boolean object;
        if (classOrObject instanceof Class) {
            clazz = (Class) classOrObject;
            object = false;
        }
        else {
            clazz = classOrObject.getClass();
            object = true;
        }
         try {
            if (method != null){
                m = clazz.getMethod(method, parameterTypes);
                if (object) return m.invoke(classOrObject, parameters);
                return m.invoke(null, parameters);
            } else {
                c = clazz.getConstructor(parameterTypes);
                return c.newInstance(parameters);
            }
        } catch (InstantiationException ex) {
            throw new IOException(ex);
        } catch (IllegalAccessException ex) {
            throw new SecurityException(ex);
        } catch (IllegalArgumentException ex) {
            throw new IOException(ex);
        } catch (InvocationTargetException ex) {
            throw new IOException(ex);
        } catch (NoSuchMethodException ex) {
            throw new IOException(ex);
        } 
    }
    
    @Override
    public void writeExternal(ObjectOutput out) throws IOException {
        distributable.checkGuard(null);
        out.writeObject(classOrObject);
        out.writeObject(method);
        /* don't clone arrays for defensive copies, it's up to constructing 
         * object to do so if needs to.
         */
        out.writeObject(parameterTypes);
        out.writeObject(parameters);
    }

    @Override
    public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
        if (constructed) throw new IllegalStateException("Object already constructed");
        /* Don't defensively copy arrays, the object is used immediately after
         * deserialization to construct the Distributed Object, the fields are
         * not accessed again.
         * 
         * DistributedObjectOutputStream.
         */
        classOrObject = in.readObject();
        method = (String) in.readObject();
        parameterTypes = (Class[]) in.readObject();
        parameters = (Object[]) in.readObject();
        
        // All this hurts performance for little benefit.
        // Read in before changing accessibility of fields.
//        Object clas = in.readObject();
//        Object methName = in.readObject();
//        Object paramTypes = in.readObject();
//        Object param = in.readObject();
//        final String [] fieldNames = {"clazz", "method", "parameterTypes", "parameters"};
//        Field [] fields = null;
//        try {
//            fields = AccessController.doPrivileged(new Action(fieldNames));
//        } catch (PrivilegedActionException ex) {
//            Exception e = ex.getException();
//            if (e instanceof NoSuchFieldException) throw new ClassNotFoundException("No such field", e);
//            if (e instanceof SecurityException ) throw (SecurityException)e;
//            throw new IOException("Unable to instantiate fields", e);
//        }
//        // Don't worry about defensive copy arrays, the constructor or factory
//        // method will be called soon.
//        try {
//             if (clas instanceof Class) fields[0].set(this, clas);
//             if (methName instanceof String) fields[1].set(this, methName);
//             if (paramTypes instanceof Class[]) fields[2].set(this,paramTypes);
//             if (param instanceof Object[]) fields[3].set(this, param);
//        } catch (IllegalArgumentException ex) {
//            Logger.getLogger(SerialFactory.class.getName()).log(Level.SEVERE, null, ex);
//        } catch (IllegalAccessException ex) {
//            Logger.getLogger(SerialFactory.class.getName()).log(Level.SEVERE, null, ex);
//        }
//        try {
//            AccessController.doPrivileged(new RestoreProtection(fields));
//        } catch (PrivilegedActionException ex) {
//            Exception e = ex.getException();
//            if (e instanceof SecurityException ) throw (SecurityException)e;
//            throw new IOException("Unable to restore access control on final fields", e);
//        }
    }
    
//    private class Action implements PrivilegedExceptionAction<Field[]>{
//        private final String [] names;
//        
//        Action(String [] names){
//            this.names = names;
//        }
//        
//        @Override
//        public Field[] run() throws Exception {
//            int l = names.length;
//            Field [] result = new Field[l];
//            for (int i = 0; i < l; i++){
//                result [i] = SerialFactory.class.getDeclaredField(names[i]);
//                result [i].setAccessible(true);
//            }
//            return result;
//        }
//        
//    }
//    
//    private class RestoreProtection implements PrivilegedExceptionAction<Boolean>{
//        private final Field [] fields;
//        
//        RestoreProtection(Field [] f){
//            fields = f;
//        }
//        @Override
//        public Boolean run() throws Exception {
//            int l = fields.length;
//            for (int i = 0; i < l; i++){
//                fields[i].setAccessible(false);
//            }
//            return Boolean.TRUE;
//        }
//        
//    }
    
    
    
}
