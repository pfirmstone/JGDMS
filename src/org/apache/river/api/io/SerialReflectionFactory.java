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
import java.io.Serializable;
import java.io.StreamCorruptedException;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.security.Guard;

/**
 * Distributed form, required for reflective calls to instantiate objects remotely, 
 * using a constructor, static method or an Object method.
 * 
 * This object must be Thread confined, it is not thread safe.
 * 
 * Internal state is guarded, arrays are not defensively coped.
 * 
 * This is compatible with Version 2 of the Java Serialization Protocol.
 * 
 * @author Peter Firmstone.
 * @see Distributed
 * @see DistributePermission
 * @see Serializable
 * @see Externalizable
 */
public final class SerialReflectionFactory implements Externalizable {
    private static final long serialVersionUID = 1L;
    /* Guard private state */
    private static final Guard distributable = new DistributePermission();
    
    /* Minimal protocol to write primitives directly to stream, only for parameters.
     * Strings are written as Objects, since they are a special case, identical
     * strings are sent by reference to the first and not duplicated.
     * Class is also a special case, it too is not duplicated.
     * 
     * Primitives are written separately to objects, the first value written 
     * to ObjectOutput is the length of the parameter array, each parameter is
     * proceeded by a byte header indicating its type.  For null values, only the
     * byte header is written to stream.
     * 
     * This protocol is fixed and cannot be changed after release.
     */
    private static final byte BOOLEAN = 0;
    private static final byte BYTE = 1;
    private static final byte CHAR = 2;
    private static final byte SHORT = 3;
    private static final byte INT = 4;
    private static final byte LONG = 5;
    private static final byte FLOAT = 6;
    private static final byte DOUBLE = 7;
    private static final byte OBJECT = 8;
    private static final byte NULL = 9;
    
    private Object classOrObject;
    private String method;
    private Class [] parameterTypes;
    private Object [] parameters;
    private final boolean constructed; // default value is false.
    
    /**
     * Public method provided for java serialization framework.
     */
    public SerialReflectionFactory(){
        constructed = false;
    }
    
    /**
     * Reflection is used at the remote end, with information provided to
     * SerialReflectionFactory, to call a constructor, static method
     * or an object method after de-serialization by DistributedObjectInputStream.
     * <p>
     * Information given to SerialReflectionFactory is guarded by DistributePermission.
     * <p>
     * Instantiation of a Distributed object at a remote endpoint proceeds as follows:
     * <ul><li>
     * If factoryClassOrObject is a Class and methodName is null, a constructor
     * of that Class is called reflectively with parameterTypes and parameters.
     * </li><li>
     * If factoryClassOrObject is a Class and methodName is defined, a static
     * method with that name is called reflectively on that Class with
     * parameterTypes and parameters.
     * </li><li>
     * If factoryClassOrObject is an Object and methodName is defined, a method
     * with that name is called reflectively on that Object with
     * parameterTypes and parameters.
     * </li></ul>
     * <p>
     * Tip: Object versions of primitive values and String parameters 
     * are relatively fast as are primitive arrays.
     * Object versions of primitive parameters are writen to DataOutput
     * as primitives.
     * <p>
     * Constructor parameters must either be Serializable, Externalizable or
     * Distributed objects.
     * <p>
     * 
     * @param factoryClassOrObject will be used for constructor, factory static method,
     * or builder Object.
     * @param methodName name of static factory method, null if using a constructor.
     * @param parameterTypes Type signature of method or constructor, or null.
     * @param parameters array of Objects to be passed to constructor, or null.
     * @see DistributePermission
     */
    public SerialReflectionFactory(Object factoryClassOrObject, String methodName, Class[] parameterTypes, Object [] parameters){
        classOrObject = factoryClassOrObject;
        method = methodName;
        this.parameterTypes = parameterTypes;
        this.parameters = parameters;
        constructed = true;
        if ( (parameterTypes != null && parameterTypes.length != parameters.length) || (parameters != null && parameters.length > 127)) 
            throw new IllegalArgumentException("Array lengths don't match, or arrays are too long,"
                    + " parameter array limit 127, "
                    + "you need to see a shrink if you need this many parameters");
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
    
    // Inherit documentation
    public void writeExternal(ObjectOutput out) throws IOException {
        distributable.checkGuard(null);
        out.writeObject(classOrObject);
        out.writeObject(method);
        /* don't clone arrays for defensive copies, it's up to constructing 
         * object to do so if needs to.
         */
        out.writeObject(parameterTypes);
        int l = parameters != null ? parameters.length : 0;
        // Write length to stream.
        out.writeByte(l);
        for (int i = 0; i < l; i++){
            writeObject(parameters[i], out);
        }
    }
    
    /**
     * Object primitive values parameters are sent as their values to avoid
     * Serialization overhead, this is only performed because primitives aren't
     * Objects so can't be used directly.  Primitive arrays are Objects so 
     * they can be used, therefore there's no need to handle them here.
     */
    private void writeObject(Object o, ObjectOutput out ) throws IOException{
        if (o == null) {
            out.writeByte(NULL);
            return;
        }
        if (o instanceof Boolean){
            out.writeByte(BOOLEAN);
            out.writeBoolean(((Boolean) o).booleanValue());
            return;
        }
        if (o instanceof Byte){
            out.writeByte(BYTE);
            out.writeByte(((Byte)o).byteValue());
            return;
        }
        if (o instanceof Character){
            out.writeByte(CHAR);
            out.writeChar(((Character)o).charValue());
            return;
        }
        if (o instanceof Short){
            out.writeByte(SHORT);
            out.writeShort(((Short)o).shortValue());
            return;
        }
        if (o instanceof Integer){
            out.writeByte(INT);
            out.writeInt(((Integer)o).intValue());
            return;
        }
        if (o instanceof Long){
            out.writeByte(LONG);
            out.writeLong(((Long)o).longValue());
            return;
        }
        if (o instanceof Float){
            out.writeByte(FLOAT);
            out.writeFloat(((Float)o).floatValue());
            return;
        }
        if (o instanceof Double){
            out.writeByte(DOUBLE);
            out.writeDouble(((Double)o).doubleValue());
            return;
        }
        // Arrays are treated as Objects, java serialization is relatively
        // efficient with primitive arrays.
        out.writeByte(OBJECT);
        out.writeObject(o);
    }
    
    private Object readObject(ObjectInput in) throws IOException, ClassNotFoundException{
        byte b = in.readByte();
        switch(b){
            case BOOLEAN:   boolean bool = in.readBoolean();
                            return Boolean.valueOf(bool);
                
            case BYTE:      byte bite = in.readByte();
                            return Byte.valueOf(bite);
                
            case CHAR:      char ch = in.readChar();
                            return Character.valueOf(ch);
                
            case SHORT:     short sh = in.readShort();
                            return Short.valueOf(sh);
                
            case INT:       int i = in.readInt();
                            return Integer.valueOf(i);
                
            case LONG:      long l = in.readLong();
                            return Long.valueOf(l);
                
            case FLOAT:     float f = in.readFloat();
                            return Float.valueOf(f);
                
            case DOUBLE:    double d = in.readDouble();
                            return Double.valueOf(d);
                
            case OBJECT:    return in.readObject();
                
            case NULL:      return null;
                
            default:        throw new StreamCorruptedException("out of range byte read from stream");
        }
        
    }
    
    // Inherit documentation.
    public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
        /* If created during deserialisation, we needn't be defensive, this
         * will never be shared with other threads and will be replaced by
         * a fully constructed thread safe immutable object. */
        if (constructed) throw new IllegalStateException("Object already constructed");
        /* Don't defensively copy arrays, the object is used immediately after
         * deserialization to construct the Distributed Object, the fields are
         * not accessed again, it is up to creator methods themselves to 
         * preserve invariants.
         */
        classOrObject = in.readObject();
        method = (String) in.readObject();
        parameterTypes = (Class[]) in.readObject();
        byte len = in.readByte();
        parameters = len == 0 ? null : new Object[len];
        for (int i = 0; i < len; i++){
            parameters[i] = readObject(in);
        }
    }
}
