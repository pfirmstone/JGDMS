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

import org.apache.river.api.io.AtomicSerial.SerialForm;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.Externalizable;
import java.io.IOException;
import java.io.InvalidClassException;
import java.io.NotActiveException;
import java.io.NotSerializableException;
import java.io.ObjectInputStream;
import java.io.ObjectOutput;
import java.io.ObjectStreamClass;
import java.io.ObjectStreamConstants;
import java.io.ObjectStreamField;
import java.io.OutputStream;
import java.io.Serializable;
import java.io.StreamCorruptedException;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Proxy;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.jini.io.ObjectStreamContext;
import org.apache.river.api.io.AtomicSerial.PutArg;
import org.apache.river.api.io.AtomicSerial.Stateless;

//import org.apache.harmony.misc.accessors.ObjectAccessor;
//import org.apache.harmony.misc.accessors.AccessorFactory;

import org.apache.river.impl.Messages;

/**
 * A specialized {@link OutputStream} that is able to write (serialize) Java
 * objects as well as primitive data types (int, byte, char etc.). The data can
 * later be loaded using an ObjectInputStream.
 * 
 * @see ObjectInputStream
 * @see ObjectOutput
 * @see Serializable
 * @see Externalizable
 */
class ObjOutputStream extends OutputStream implements ObjectOutput,
        ObjectStreamConstants, ObjectStreamContext {

    /*
     * Mask to zero SC_BLOC_DATA bit.
     */
//    private static final byte NOT_SC_BLOCK_DATA = (byte) (SC_BLOCK_DATA ^ 0xFF);

    /*
     * How many nested levels to writeObject. We may not need this.
     */
    private int nestedLevels;

    /*
     * Where we write to
     */
    private DataOutputStream output;

    /*
     * If object replacement is enabled or not
     */
    private boolean enableReplace;

    /*
     * Where we write primitive types to
     */
    private DataOutputStream primitiveTypes;

    /*
     * Where the write primitive types are actually written to
     */
    private ByteArrayOutputStream primitiveTypesBuffer;

    /*
     * Table mapping Object -> Integer (handle)
     */
    private Map<Object, Integer> objectsWritten;

    /*
     * All objects are assigned an ID (integer handle)
     */
    private int currentHandle;

    /*
     * Used by defaultWriteObject
     */
    private Object currentObject;

    /*
     * Used by defaultWriteObject
     */
    private ObjectStreamClass currentClass;
    
    /*
     * Either ObjectStreamConstants.PROTOCOL_VERSION_1 or
     * ObjectStreamConstants.PROTOCOL_VERSION_2
     */
    private int protocolVersion;

    /*
     * Used to detect nested exception when saving an exception due to an error
     */
    private StreamCorruptedException nestedException;

    /*
     * Used to keep track of the PutField object for the class/object being
     * written
     */
    private EmulatedFieldsForDumping currentPutField;

    /*
     * Allows the receiver to decide if it needs to call writeObjectOverride
     */
    private final boolean subclassOverridingImplementation;
    

//    private ObjectAccessor accessor = AccessorFactory.getObjectAccessor();

    /*
     * Descriptor for java.lang.reflect.Proxy
     */
    private final ObjectStreamClass proxyClassDesc = ObjectStreamClass.lookup(Proxy.class); 
    private Collection context;

    /**
     * Constructs a new {@code ObjectOutputStream}. This default constructor can
     * be used by subclasses that do not want to use the public constructor if
     * it allocates unneeded data.
     * 
     * @throws IOException
     *             if an error occurs when creating this stream.
     * @throws SecurityException
     *             if a security manager is installed and it denies subclassing
     *             this class.
     * @see SecurityManager#checkPermission(java.security.Permission)
     */
    protected ObjOutputStream() throws IOException, SecurityException {
        super();
        SecurityManager currentManager = System.getSecurityManager();
        if (currentManager != null) {
            currentManager.checkPermission(SUBCLASS_IMPLEMENTATION_PERMISSION);
        }
        /*
         * WARNING - we should throw IOException if not called from a subclass
         * according to the JavaDoc. Add the test.
         */
        this.subclassOverridingImplementation = true;
    }
    
    /**
     * Constructs a new ObjectOutputStream that writes to the OutputStream
     * {@code output}.
     * 
     * @param output
     *            the non-null OutputStream to filter writes on.
     * 
     * @throws IOException
     *             if an error occurs while writing the object stream
     *             header
     * @throws SecurityException
     *             if a security manager is installed and it denies subclassing
     *             this class.
     */
    public ObjOutputStream(OutputStream output) throws IOException {
        this(output, null);
    }

    /**
     * Constructs a new ObjectOutputStream that writes to the OutputStream
     * {@code output}.
     * 
     * @param output
     *            the non-null OutputStream to filter writes on.
     * 
     * @throws IOException
     *             if an error occurs while writing the object stream
     *             header
     * @throws SecurityException
     *             if a security manager is installed and it denies subclassing
     *             this class.
     */
    public ObjOutputStream(OutputStream output, Collection context) throws IOException {
//        Class<?> implementationClass = getClass();
//        Class<?> thisClass = ObjectOutputStream.class;
//        if (implementationClass != thisClass) {
//            boolean mustCheck = false;
//            try {
//                Method method = implementationClass.getMethod("putFields", //$NON-NLS-1$
//                        ObjectStreamClass.EMPTY_CONSTRUCTOR_PARAM_TYPES);
//                mustCheck = method.getDeclaringClass() != thisClass;
//            } catch (NoSuchMethodException e) {
//            }
//            if (!mustCheck) {
//                try {
//                    Method method = implementationClass.getMethod(
//                            "writeUnshared", //$NON-NLS-1$
//                            ObjectStreamClass.UNSHARED_PARAM_TYPES);
//                    mustCheck = method.getDeclaringClass() != thisClass;
//                } catch (NoSuchMethodException e) {
//                }
//            }
//            if (mustCheck) {
//                SecurityManager sm = System.getSecurityManager();
//                if (sm != null) {
//                    sm
//                            .checkPermission(ObjectStreamConstants.SUBCLASS_IMPLEMENTATION_PERMISSION);
//                }
//            }
//        }
        this.context = Collections.unmodifiableCollection(context != null ? context : Collections.emptyList());
        this.output = (output instanceof DataOutputStream) ? (DataOutputStream) output
                : new DataOutputStream(output);
        this.enableReplace = false;
        this.protocolVersion = PROTOCOL_VERSION_2;
        this.subclassOverridingImplementation = false;

        resetState();
        this.nestedException = new StreamCorruptedException();
        // So write...() methods can be used by
        // subclasses during writeStreamHeader()
        primitiveTypes = this.output;
        // Has to be done here according to the specification
        writeStreamHeader();
        primitiveTypes = null;
    }
    
    @Override
    public String toString(){
        String cr = "\n";
        String obj = "Object at wire handle index: ";
        String is = " is: ";
        StringBuilder s = new StringBuilder();
        s.append("ObjOutputStream dump: \n");
//        s.append("Nested levels: ").append(nestedLevels).append(cr);
//        s.append("Enable replace: ").append(enableReplace).append(cr);
//        s.append("Current handle: ").append(currentHandle).append(cr);
//        s.append("Current class: ").append(currentClass).append(cr);
//        s.append("Current object: ").append(currentObject).append(cr);
        s.append("Objects written:\n");
        TreeMap<Integer,Object> streamHandles = new TreeMap<Integer,Object>();
        Set<Entry<Object,Integer>> streamHandleSet = objectsWritten.entrySet();
        Iterator<Entry<Object,Integer>> streamHandleIterator = streamHandleSet.iterator();
        while (streamHandleIterator.hasNext()){
            Entry<Object,Integer> entry = streamHandleIterator.next();
            streamHandles.put(entry.getValue(), entry.getKey());
        }
        Set<Entry<Integer,Object>> handleSet = streamHandles.entrySet();
        Iterator<Entry<Integer,Object>> handleIterator = handleSet.iterator();
        while (handleIterator.hasNext()){
            Entry<Integer,Object> entry = handleIterator.next();
            s.append(obj).append(entry.getKey()).append(is).append(entry.getValue()).append(cr);
        }
//        s.append(objectsWritten);
        return s.toString();
    }

    /**
     * Writes optional information for class {@code aClass} to the output
     * stream. This optional data can be read when deserializing the class
     * descriptor (ObjectStreamClass) for this class from an input stream. By
     * default, no extra data is saved.
     * 
     * @param aClass
     *            the class to annotate.
     * @throws IOException
     *             if an error occurs while writing to the target stream.
     * @see ObjectInputStream#resolveClass(ObjectStreamClass)
     */
    protected void annotateClass(Class<?> aClass) throws IOException {
        // By default no extra info is saved. Subclasses can override
    }

    /**
     * Writes optional information for a proxy class to the target stream. This
     * optional data can be read when deserializing the proxy class from an
     * input stream. By default, no extra data is saved.
     * 
     * @param aClass
     *            the proxy class to annotate.
     * @throws IOException
     *             if an error occurs while writing to the target stream.
     * @see ObjectInputStream#resolveProxyClass(String[])
     */
    protected void annotateProxyClass(Class<?> aClass) throws IOException {
        // By default no extra info is saved. Subclasses can override
    }

    /**
     * Do the necessary work to see if the receiver can be used to write
     * primitive types like int, char, etc.
     */
    private void checkWritePrimitiveTypes() {
        if (primitiveTypes == null) {
            // If we got here we have no Stream previously created
            // WARNING - if the stream does not grow, this code is wrong
            primitiveTypesBuffer = new ByteArrayOutputStream(128);
            primitiveTypes = new DataOutputStream(primitiveTypesBuffer);
        }
    }

    /**
     * Closes this stream. Any buffered data is flushed. This implementation
     * closes the target stream.
     * 
     * @throws IOException
     *             if an error occurs while closing this stream.
     */
    @Override
    public void close() throws IOException {
        // First flush what is needed (primitive data, etc)
        flush();
        output.close();
    }

    /**
     * Computes the collection of emulated fields that users can manipulate to
     * store a representation different than the one declared by the class of
     * the object being dumped.
     * 
     * @see #writeFields
     * @see #writeFieldValues(EmulatedFieldsForDumping)
     */
    private void computePutField() throws IOException {
//        currentPutField = new EmulatedFieldsForDumping(this, currentClass.getFields());
        currentPutField = new EmulatedFieldsForDumping(this, fields(currentClass.forClass()));
    }
    
    private ObjectStreamField [] fields(Class clz) throws IOException{
        if (Proxy.class.equals(clz)){
            return new SerialForm[]{
                new SerialForm("h", InvocationHandler.class)
            };
        }
        try {
            Method m = clz.getMethod("serialForm", new Class [0]);
            int modifiers = m.getModifiers();
            if (Modifier.isStatic(modifiers) && Modifier.isPublic(modifiers) && m.getReturnType() == SerialForm [].class){
//                System.out.println("Invoking serialForm method on " + clz);
                ObjectStreamField [] fields = (ObjectStreamField[]) m.invoke(null, (Object []) null);
                Arrays.sort(fields);
                return fields;
            }
        } catch (NoSuchMethodException ex) {
            Logger.getLogger(AtomicMarshalInputStream.class.getName()).log(Level.INFO, "@AtomicSerial class is missing public static method serialForm", ex);
        } catch (Exception ex) {
            throw new IOException("Unable to access serialForm method" , ex);
        } 
        return new ObjectStreamField [0];
    }

    /**
     * Default method to write objects to this stream. Serializable fields
     * defined in the object's class and superclasses are written to the output
     * stream.
     * 
     * @throws IOException
     *             if an error occurs while writing to the target stream.
     * @throws NotActiveException
     *             if this method is not called from {@code writeObject()}.
     * @see ObjectInputStream#defaultReadObject
     */
    public void defaultWriteObject() throws IOException {
        // We can't be called from just anywhere. There are rules.
        if (currentObject == null) {
            throw new NotActiveException();
        }
        writeFieldValues(currentObject, currentClass);
    }

    /**
     * Writes buffered data to the target stream. This is similar to {@code
     * flush} but the flush is not propagated to the target stream.
     * 
     * @throws IOException
     *             if an error occurs while writing to the target stream.
     */
    protected void drain() throws IOException {
        if (primitiveTypes == null || primitiveTypesBuffer == null) {
            return;
        }

        // If we got here we have a Stream previously created
        int offset = 0;
        byte[] written = primitiveTypesBuffer.toByteArray();
        // Normalize the primitive data
        while (offset < written.length) {
            int toWrite = written.length - offset > 1024 ? 1024
                    : written.length - offset;
            if (toWrite < 256) {
                output.writeByte(TC_BLOCKDATA);
                output.writeByte((byte) toWrite);
            } else {
                output.writeByte(TC_BLOCKDATALONG);
                output.writeInt(toWrite);
            }

            // write primitive types we had and the marker of end-of-buffer
            output.write(written, offset, toWrite);
            offset += toWrite;
        }

        // and now we're clean to a state where we can write an object
        primitiveTypes = null;
        primitiveTypesBuffer = null;
    }

    /**
     * Dumps the parameter {@code obj} only if it is {@code null}
     * or an object that has already been dumped previously.
     * 
     * @param obj
     *            Object to check if an instance previously dumped by this
     *            stream.
     * @return null if it is an instance which has not been dumped yet (and this
     *         method does nothing). Integer, if {@code obj} is an
     *         instance which has been dumped already. In this case this method
     *         saves the cyclic reference.
     * 
     * @throws IOException
     *             If an error occurs attempting to save {@code null} or
     *             a cyclic reference.
     */
    private Integer dumpCycle(Object obj) throws IOException {
        // If the object has been saved already, save its handle only
        Integer handle = objectsWritten.get(obj);
        if (handle != null) {
            writeCyclicReference(handle);
            return handle;
        }
        return null;
    }

    /**
     * Enables object replacement for this stream. By default this is not
     * enabled. Only trusted subclasses (loaded with system class loader) are
     * allowed to change this status.
     * 
     * @param enable
     *            {@code true} to enable object replacement; {@code false} to
     *            disable it.
     * @return the previous setting.
     * @throws SecurityException
     *             if a security manager is installed and it denies enabling
     *             object replacement for this stream.
     * @see #replaceObject
     * @see ObjectInputStream#enableResolveObject
     */
    protected boolean enableReplaceObject(boolean enable)
            throws SecurityException {
        if (enable) {
            // The Stream has to be trusted for this feature to be enabled.
            // trusted means the stream's classloader has to be null
            SecurityManager currentManager = System.getSecurityManager();
            if (currentManager != null) {
                currentManager.checkPermission(SUBSTITUTION_PERMISSION);
            }
        }
        boolean originalValue = enableReplace;
        enableReplace = enable;
        return originalValue;
    }

    /**
     * Writes buffered data to the target stream and calls the {@code flush}
     * method of the target stream.
     * 
     * @throws IOException
     *             if an error occurs while writing to or flushing the output
     *             stream.
     */
    @Override
    public void flush() throws IOException {
        drain();
        output.flush();
    }


    /**
     * Return the next <code>Integer</code> handle to be used to indicate cyclic
     * references being saved to the stream.
     * 
     * @return the next handle to represent the next cyclic reference
     */
    private Integer nextHandle() {
        Integer handle = Integer.valueOf(this.currentHandle++);
//        Throwable backTrace = new Throwable("Who requested the handle.");
//        System.out.println("New reference handle created: " + handle);
//        backTrace.printStackTrace(System.out);
        return handle;
    }
    
    void fieldTypeStringWritten(String str){
        Integer handle = nextHandle();
//        System.out.println("Assigned reference handle " + handle + " to " + currentObject);
        objectsWritten.put(str, handle);
    }

    /**
     * Gets this stream's {@code PutField} object. This object provides access
     * to the persistent fields that are eventually written to the output
     * stream. It is used to transfer the values from the fields of the object
     * that is currently being written to the persistent fields.
     * 
     * @return the PutField object from which persistent fields can be accessed
     *         by name.
     * @throws IOException
     *             if an I/O error occurs.
     * @throws NotActiveException
     *             if this method is not called from {@code writeObject()}.
     * @see ObjectInputStream#defaultReadObject
     */
    public PutArg putFields() throws IOException {
        // We can't be called from just anywhere. There are rules.
        if (currentObject == null) {
            throw new NotActiveException();
        }
        if (currentPutField == null) {
            computePutField();
        }
        return currentPutField;
    }

    /**
     * Assume object {@code obj} has not been dumped yet, and assign a
     * handle to it
     *
     * @param obj
     *            Non-null object being dumped.
     * @return the handle that this object is being assigned.
     * 
     * @see #nextHandle
     */
//    private Integer registerObjectWritten(Object obj) {
//        Integer handle = nextHandle();
//        objectsWritten.put(obj, handle);
//        return handle;
//    }

    /**
     * Remove the unshared object from the table, and restore any previous
     * handle.
     * 
     * @param obj
     *            Non-null object being dumped.
     * @param previousHandle
     *            The handle of the previous identical object dumped
     */
    private void removeUnsharedReference(Object obj, Integer previousHandle) {
        if (previousHandle != null) {
            objectsWritten.put(obj, previousHandle);
        } else {
            objectsWritten.remove(obj);
        }
    }

    /**
     * Allows trusted subclasses to substitute the specified original {@code
     * object} with a new object. Object substitution has to be activated first
     * with calling {@code enableReplaceObject(true)}. This implementation just
     * returns {@code object}.
     *
     * @param object
     *            the original object for which a replacement may be defined.
     * @return the replacement object for {@code object}.
     * @throws IOException
     *             if any I/O error occurs while creating the replacement
     *             object.
     * @see #enableReplaceObject
     * @see ObjectInputStream#enableResolveObject
     * @see ObjectInputStream#resolveObject
     */
    protected Object replaceObject(Object object) throws IOException {
        // By default no object replacement. Subclasses can override
        return object;
    }

    /**
     * Resets the state of this stream. A marker is written to the stream, so
     * that the corresponding input stream will also perform a reset at the same
     * point. Objects previously written are no longer remembered, so they will
     * be written again (instead of a cyclical reference) if found in the object
     * graph.
     * 
     * @throws IOException
     *             if {@code reset()} is called during the serialization of an
     *             object.
     */
    public void reset() throws IOException {
        // First we flush what we have
        drain();
        /*
         * And dump a reset marker, so that the ObjectInputStream can reset
         * itself at the same point
         */
        output.writeByte(TC_RESET);
        // Now we reset ourselves
        resetState();
    }

    /**
     * Reset the collection of objects already dumped by the receiver. If the
     * objects are found again in the object graph, the receiver will dump them
     * again, instead of a handle (cyclic reference).
     * 
     */
    private void resetSeenObjects() {
        objectsWritten = new IdentityHashMap<Object, Integer>();
        currentHandle = baseWireHandle;
    }

    /**
     * Reset the receiver. The collection of objects already dumped by the
     * receiver is reset, and internal structures are also reset so that the
     * receiver knows it is in a fresh clean state.
     * 
     */
    private void resetState() {
        resetSeenObjects();
        nestedLevels = 0;
    }

    /**
     * Sets the specified protocol version to be used by this stream.
     * 
     * @param version
     *            the protocol version to be used. Use a {@code
     *            PROTOCOL_VERSION_x} constant from {@code
     *            java.io.ObjectStreamConstants}.
     * @throws IllegalArgumentException
     *             if an invalid {@code version} is specified.
     * @throws IOException
     *             if an I/O error occurs.
     * @see ObjectStreamConstants#PROTOCOL_VERSION_1
     * @see ObjectStreamConstants#PROTOCOL_VERSION_2
     */
    public void useProtocolVersion(int version) throws IOException {
        if (!objectsWritten.isEmpty()) {
            // luni.C8=Cannot set protocol version when stream in use
            throw new IllegalStateException(Messages.getString("luni.C8")); //$NON-NLS-1$
        }
        if (version != ObjectStreamConstants.PROTOCOL_VERSION_1
                && version != ObjectStreamConstants.PROTOCOL_VERSION_2) {
            // luni.9C=Unknown protocol\: {0}
            throw new IllegalArgumentException(Messages.getString("luni.9C", version)); //$NON-NLS-1$
        }
        protocolVersion = version;
    }

    /**
     * Writes the entire contents of the byte array {@code buffer} to the output
     * stream. Blocks until all bytes are written.
     * 
     * @param buffer
     *            the buffer to write.
     * @throws IOException
     *             if an error occurs while writing to the target stream.
     */
    @Override
    public void write(byte[] buffer) throws IOException {
        checkWritePrimitiveTypes();
        primitiveTypes.write(buffer);
    }

    /**
     * Writes {@code count} bytes from the byte array {@code buffer} starting at
     * offset {@code index} to the target stream. Blocks until all bytes are
     * written.
     * 
     * @param buffer
     *            the buffer to write.
     * @param offset
     *            the index of the first byte in {@code buffer} to write.
     * @param length
     *            the number of bytes from {@code buffer} to write to the output
     *            stream.
     * @throws IOException
     *             if an error occurs while writing to the target stream.
     */
    @Override
    public void write(byte[] buffer, int offset, int length) throws IOException {
        checkWritePrimitiveTypes();
        primitiveTypes.write(buffer, offset, length);
    }

    /**
     * Writes a single byte to the target stream. Only the least significant
     * byte of the integer {@code value} is written to the stream. Blocks until
     * the byte is actually written.
     * 
     * @param value
     *            the byte to write.
     * @throws IOException
     *             if an error occurs while writing to the target stream.
     */
    @Override
    public void write(int value) throws IOException {
        checkWritePrimitiveTypes();
        primitiveTypes.write(value);
    }

    /**
     * Writes a boolean to the target stream.
     * 
     * @param value
     *            the boolean value to write to the target stream.
     * @throws IOException
     *             if an error occurs while writing to the target stream.
     */
    @Override
    public void writeBoolean(boolean value) throws IOException {
        checkWritePrimitiveTypes();
        primitiveTypes.writeBoolean(value);
    }

    /**
     * Writes a byte (8 bit) to the target stream.
     * 
     * @param value
     *            the byte to write to the target stream.
     * @throws IOException
     *             if an error occurs while writing to the target stream.
     */
    @Override
    public void writeByte(int value) throws IOException {
        checkWritePrimitiveTypes();
        primitiveTypes.writeByte(value);
    }

    /**
     * Writes the string {@code value} as a sequence of bytes to the target
     * stream. Only the least significant byte of each character in the string
     * is written.
     * 
     * @param value
     *            the string to write to the target stream.
     * @throws IOException
     *             if an error occurs while writing to the target stream.
     */
    @Override
    public void writeBytes(String value) throws IOException {
        checkWritePrimitiveTypes();
        primitiveTypes.writeBytes(value);
    }

    /**
     * Writes a character (16 bit) to the target stream.
     * 
     * @param value
     *            the character to write to the target stream.
     * @throws IOException
     *             if an error occurs while writing to the target stream.
     */
    @Override
    public void writeChar(int value) throws IOException {
        checkWritePrimitiveTypes();
        primitiveTypes.writeChar(value);
    }

    /**
     * Writes the string {@code value} as a sequence of characters to the target
     * stream.
     * 
     * @param value
     *            the string to write to the target stream.
     * @throws IOException
     *             if an error occurs while writing to the target stream.
     */
    @Override
    public void writeChars(String value) throws IOException {
        checkWritePrimitiveTypes();
        primitiveTypes.writeChars(value);
    }

    /**
     * Write a class descriptor {@code classDesc} (an
     * {@code ObjectStreamClass}) to the stream.
     * 
     * @param classDesc
     *            The class descriptor (an {@code ObjectStreamClass}) to
     *            be dumped
     * @param unshared
     *            Write the object unshared
     * @return the handle assigned to the class descriptor
     * 
     * @throws IOException
     *             If an IO exception happened when writing the class
     *             descriptor.
     */
    private Integer writeClassDesc(ObjectStreamClass classDesc, boolean unshared, Class objClass)
            throws IOException {
        if (classDesc == null) {
            writeNull();
            return null;
        }
        Integer handle = null;
        if (!unshared) {
            handle = dumpCycle(classDesc);
        }
        if (handle == null) {
            Class<?> classToWrite = objClass;
            Integer previousHandle = null;
            if (unshared) {
                previousHandle = objectsWritten.get(classDesc);
            }
            // If we got here, it is a new (non-null) classDesc that will have
            // to be registered as well
            handle = nextHandle();
//            System.out.println("Assigned reference handle " + handle + " to " + objClass);
            objectsWritten.put(classDesc, handle);

            if (!Proxy.class.equals(objClass) && Proxy.isProxyClass(objClass) ) {
                output.writeByte(TC_PROXYCLASSDESC);
//                System.out.println("Writing proxy to stream: " + objClass.toString());
                Class<?>[] interfaces = classToWrite.getInterfaces();
                output.writeInt(interfaces.length);
                for (int i = 0; i < interfaces.length; i++) {
                    output.writeUTF(interfaces[i].getName());
                }
                annotateProxyClass(classToWrite);
                output.writeByte(TC_ENDBLOCKDATA);
                writeClassDesc(proxyClassDesc, false, Proxy.class);
                if (unshared) {
                    // remove reference to unshared object
                    removeUnsharedReference(classDesc, previousHandle);
                }
                return handle;
            }

            output.writeByte(TC_CLASSDESC);
            if (protocolVersion == PROTOCOL_VERSION_1) {
                writeNewClassDesc(classDesc, objClass);
            } else {
                // So write...() methods can be used by
                // subclasses during writeClassDescriptor()
                primitiveTypes = output;
                writeClassDescriptor(classDesc);
                primitiveTypes = null;
            }
            // Extra class info (optional)
            annotateClass(classToWrite);
            drain(); // flush primitive types in the annotation
            output.writeByte(TC_ENDBLOCKDATA);
            if (!Enum.class.isAssignableFrom(classToWrite)){
                Class superClass = classToWrite.getSuperclass();
                ObjectStreamClass superStreamClass = null;
                if (superClass != null && superClass != Object.class){
                    superStreamClass = ObjectStreamClass.lookupAny(superClass);
                }
                writeClassDesc(superStreamClass, unshared, superClass);
            }
            if (unshared) {
                // remove reference to unshared object
                removeUnsharedReference(classDesc, previousHandle);
            }
        }
        return handle;
    }

    /**
     * Writes a handle representing a cyclic reference (object previously
     * dumped).
     * 
     * @param handle
     *            The Integer handle that represents an object previously seen
     * 
     * @throws IOException
     *             If an IO exception happened when writing the cyclic
     *             reference.
     */
    private void writeCyclicReference(Integer handle) throws IOException {
        output.writeByte(TC_REFERENCE);
        output.writeInt(handle.intValue());
    }

    /**
     * Writes a double (64 bit) to the target stream.
     * 
     * @param value
     *            the double to write to the target stream.
     * @throws IOException
     *             if an error occurs while writing to the target stream.
     */
    @Override
    public void writeDouble(double value) throws IOException {
        checkWritePrimitiveTypes();
        primitiveTypes.writeDouble(value);
    }

    /**
     * Writes a collection of field descriptors (name, type name, etc) for the
     * class descriptor {@code classDesc} (an
     * {@code ObjectStreamClass})
     * 
     * @param classDesc
     *            The class descriptor (an {@code ObjectStreamClass})
     *            for which to write field information
     * @param externalizable
     *            true if the descriptors are externalizable
     * 
     * @throws IOException
     *             If an IO exception happened when writing the field
     *             descriptors.
     * 
     * @see #writeObject(Object)
     */
//    private void writeFieldDescriptors(ObjectStreamClass classDesc,
//            boolean externalizable) throws IOException {
//        Class<?> loadedClass = classDesc.forClass();
//        ObjectStreamField[] fields = null;
//        int fieldCount = 0;
//
//        // The fields of String are not needed since Strings are treated as
//        // primitive types
//        if (!externalizable && loadedClass != ObjectStreamClass.STRINGCLASS) {
//            fields = classDesc.fields();
//            fieldCount = fields.length;
//        }
//
//        // Field count
//        output.writeShort(fieldCount);
//        // Field names
//        for (int i = 0; i < fieldCount; i++) {
//            ObjectStreamField f = fields[i];
//            output.writeByte(f.getTypeCode());
//            output.writeUTF(f.getName());
//            if (!f.isPrimitive()) {
//                writeObject(f.getTypeString());
//            }
//        }
//    }

    /**
     * Writes the fields of the object currently being written to the target
     * stream. The field values are buffered in the currently active {@code
     * PutField} object, which can be accessed by calling {@code putFields()}.
     * 
     * @throws IOException
     *             if an error occurs while writing to the target stream.
     * @throws NotActiveException
     *             if there are no fields to write to the target stream.
     * @see #putFields
     */
    public void writeFields() throws IOException {
        // Has to have fields to write
        if (currentPutField == null) {
            throw new NotActiveException();
        }
        writeFieldValues(currentPutField);
    }

    /**
     * Writes a collection of field values for the emulated fields
     * {@code emulatedFields}
     * 
     * @param emulatedFields
     *            an {@code EmulatedFieldsForDumping}, concrete subclass
     *            of {@code PutField}
     * 
     * @throws IOException
     *             If an IO exception happened when writing the field values.
     * 
     * @see #writeFields
     * @see #writeObject(Object)
     */
    private void writeFieldValues(EmulatedFieldsForDumping emulatedFields)
            throws IOException {
        EmulatedFields accessibleSimulatedFields = emulatedFields
                .emulatedFields(); // Access internal fields which we can
//        System.out.println(accessibleSimulatedFields);
        // set/get. Users can't do this.
        ObjectSlot[] slots = accessibleSimulatedFields.slots();
        for (int i = 0, l = slots.length; i < l; i++) {
            ObjectSlot slot = slots[i];
            Class<?> type = slot.getField().getType();
            if (type == Integer.TYPE) {
//                System.out.println("Writing field int to stream: " + slot.getIntValue());
                output.writeInt(slot.getIntValue());
            } else if (type == Byte.TYPE) {
//                System.out.println("Writing field byte to stream: " + slot.getByteValue());
                output.writeByte(slot.getByteValue());
            } else if (type == Character.TYPE) {
//                System.out.println("Writing field char to stream: " + slot.getCharValue());
                output.writeChar(slot.getCharValue());
            } else if (type == Short.TYPE) {
//                System.out.println("Writing field short to stream: " + slot.getShortValue());
                output.writeShort(slot.getShortValue());
            } else if (type == Boolean.TYPE) {
//                System.out.println("Writing field boolean to stream: " + slot.isBooleanValue());
                output.writeBoolean(slot.isBooleanValue());
            } else if (type == Long.TYPE) {
//                System.out.println("Writing field long to stream: " + slot.getLongValue());
                output.writeLong(slot.getLongValue());
            } else if (type == Float.TYPE) {
//                System.out.println("Writing field float to stream: " + slot.getFloatValue());
                output.writeFloat(slot.getFloatValue());
            } else if (type == Double.TYPE) {
//                System.out.println("Writing field double to stream: " + slot.getDoubleValue());
                output.writeDouble(slot.getDoubleValue());
            } else {
                Object fieldValue;
                try {
                    fieldValue = slot.getFieldValue();
                } catch (ClassNotFoundException ex) {
                    throw new AssertionError("Impossible ", ex);
                }
//                System.out.println("Writing field Object to stream: " + fieldValue);
                // Either array or Object
                writeObject(fieldValue);
            }
        }
    }


    /**
     * Writes a collection of field values for the fields described by class
     * descriptor {@code classDesc} (an {@code ObjectStreamClass}).
     * This is the default mechanism, when emulated fields (an
     * {@code PutField}) are not used. Actual values to dump are fetched
     * directly from object {@code obj}.
     * 
     * @param obj
     *            Instance from which to fetch field values to dump.
     * @param classDesc
     *            A class descriptor (an {@code ObjectStreamClass})
     *            defining which fields should be dumped.
     * 
     * @throws IOException
     *             If an IO exception happened when writing the field values.
     * 
     * @see #writeObject(Object)
     */
    private void writeFieldValues(Object obj, ObjectStreamClass classDesc)
            throws IOException {
        ObjectStreamField[] fields = classDesc.getFields();
        Class<?> declaringClass = classDesc.forClass();
        for(ObjectStreamField fieldDesc : fields) {
            try {
                
                // get associated Field 
//                long fieldID = fieldDesc.getFieldID(accessor, declaringClass);
                Field f = null;
                f = AccessController.doPrivileged(new PrivilegedAction<Field>(){

                    @Override
                    public Field run() {
                        try {
                            return fieldDesc == null || declaringClass == null ?  null : 
                                    declaringClass.getDeclaredField(fieldDesc.getName());
                        } catch (NoSuchFieldException ex) {
                            return null;
                        } catch (SecurityException ex) {
                            Logger.getLogger(AtomicMarshalOutputStream.class.getName()).log(Level.INFO, null, ex);
                            return null;
                        }
                    }

                });

                // Code duplication starts, just because Java is typed
                if (fieldDesc != null && fieldDesc.isPrimitive()) {
                    switch (fieldDesc.getTypeCode()) {
                        case 'B':
                            output.writeByte(f.getByte(obj));
                            break;
                        case 'C':
                            output.writeChar(f.getChar(obj));
                            break;
                        case 'D':
                            output.writeDouble(f.getDouble(obj));
                            break;
                        case 'F':
                            output.writeFloat(f.getFloat(obj));
                            break;
                        case 'I':
                            output.writeInt(f.getInt(obj));
                            break;
                        case 'J':
                            output.writeLong(f.getLong(obj));
                            break;
                        case 'S':
                            output.writeShort(f.getShort(obj));
                            break;
                        case 'Z':
                            output.writeBoolean(f.getBoolean(obj));
                            break;
                        default:
                            throw new IOException(
                                    Messages.getString(
                                            "luni.BF", fieldDesc.getTypeCode())); //$NON-NLS-1$
                    }
                } else {
                    // Object type (array included).
                    Object objField = f.get(obj);
                    if (fieldDesc != null && fieldDesc.isUnshared()) {
                        writeUnshared(objField);
                    } else {
                        writeObject(objField);
                    }
                }
            } catch (NoSuchFieldError nsf) {
                // The user defined a serialForm but did not provide
                // the glue to transfer values,
                // (in their serialize method) so we end up using the default mechanism and
                // fail to set the emulated field
                InvalidClassException e = new InvalidClassException(classDesc.getName());
                e.addSuppressed(nsf);
                throw e;
            } catch (IllegalArgumentException ex) {
                Logger.getLogger(ObjOutputStream.class.getName()).log(Level.SEVERE, null, ex);
            } catch (IllegalAccessException ex) {
                Logger.getLogger(ObjOutputStream.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
    }

    /**
     * Writes a float (32 bit) to the target stream.
     * 
     * @param value
     *            the float to write to the target stream.
     * @throws IOException
     *             if an error occurs while writing to the target stream.
     */
    @Override
    public void writeFloat(float value) throws IOException {
        checkWritePrimitiveTypes();
        primitiveTypes.writeFloat(value);
    }

    /**
     * Walks the hierarchy of classes described by class descriptor
     * {@code classDesc} and writes the field values corresponding to
     * fields declared by the corresponding class descriptor. The instance to
     * fetch field values from is {@code object}. If the class
     * (corresponding to class descriptor {@code classDesc}) defines
     * private instance method {@code writeObject} it will be used to
     * dump field values.
     * 
     * @param object
     *            Instance from which to fetch field values to dump.
     * @param classDesc
     *            A class descriptor (an {@code ObjectStreamClass})
     *            defining which fields should be dumped.
     * 
     * @throws IOException
     *             If an IO exception happened when writing the field values in
     *             the hierarchy.
     * @throws NotActiveException
     *             If the given object is not active
     * 
     * @see #defaultWriteObject
     * @see #writeObject(Object)
     */
    private void writeHierarchy(Object object, ObjectStreamClass classDesc, Class theClass)
            throws IOException, NotActiveException {
        // We can't be called from just anywhere. There are rules.
        if (object == null) {
            throw new NotActiveException();
        }
        
        // Fields are written from class closest to Object to leaf class
        // (down the chain)
        Class superClass = theClass.getSuperclass();
        if (superClass != null) {
            // first
            writeHierarchy(object, ObjectStreamClass.lookupAny(superClass), superClass);
        }

        // Have to do this before calling defaultWriteObject or anything
        // that calls defaultWriteObject
        currentObject = object;
        currentClass = classDesc;

        // See if the object has a writeObject method. If so, run it
        boolean executed = false;
        if (Proxy.class.equals(theClass)){ // We have to do it for java.lang.reflect.Proxy.
            PutArg args = putFields();
            InvocationHandler h = Proxy.getInvocationHandler(object);
            args.put("h", h);
            args.writeArgs();
            executed = true;
        }
        try { 
            if (theClass.isAnnotationPresent(AtomicSerial.class)
                    && !theClass.isAnnotationPresent(Stateless.class))
            {
            try {
                Method m = theClass.getMethod("serialize", new Class []{PutArg.class, theClass});
                int mods = m.getModifiers();
                if (Modifier.isStatic(mods) && Modifier.isPublic(mods)){
                    PutArg args = putFields();
//                    System.out.println("Invoking serialize method on " + theClass);
                    m.invoke(null, new Object [] {args, object});
                    if (((EmulatedFieldsForDumping)args).fields != 0) {
                        StringBuilder sb = new StringBuilder();
                        sb.append(theClass)
                          .append(" puts ")
                          .append(((EmulatedFieldsForDumping)args).fields)
                          .append(" arguments but doesn't call PutArg::writeArgs in serialize method");
                        throw new IOException(sb.toString());
                    }
                    executed = true;
                } else {
                    throw new IOException("serialize method is not static and public");
                }
            
//            if (classDesc.hasMethodWriteObject()){
//                final Method method = classDesc.getMethodWriteObject();
//                try {
//                    method.invoke(object, new Object[] { this });
//                    executed = true;
//                } catch (InvocationTargetException e) {
//                    Throwable ex = e.getTargetException();
//                    if (ex instanceof RuntimeException) {
//                        throw (RuntimeException) ex;
//                    } else if (ex instanceof Error) {
//                        throw (Error) ex;
//                    }
//                    throw (IOException) ex;
//                } catch (IllegalAccessException e) {
//                    throw new RuntimeException(e.toString());
//                }
//            }
                } catch (NoSuchMethodException ex) {
                    throw new IOException("@AtomicSerial class " 
                            + theClass.getCanonicalName()+ 
                            " doesn't define serialize method", ex);
                } catch (IllegalAccessException e) {
                    throw new RuntimeException(e.toString());
                } catch (IllegalArgumentException ex) {
                    throw new IOException("@AtomicSerial class " 
                            + theClass.getCanonicalName()+ 
                            " doesn't define serialize method correctly", ex);
                } catch (InvocationTargetException e) {
                    Throwable ex = e.getTargetException();
                    if (ex instanceof RuntimeException) {
                        throw (RuntimeException) ex;
                    } else if (ex instanceof Error) {
                        throw (Error) ex;
                    }
                    throw (IOException) ex;
                }
            }

            if (executed) {
                drain();
                output.writeByte(TC_ENDBLOCKDATA);
            } else {
                // If the object did not have a writeMethod, call
                // defaultWriteObject
                defaultWriteObject();
            }
            
        } finally {
            // Cleanup, needs to run always so that we can later detect
            // invalid calls to defaultWriteObject
            currentObject = null;
            currentClass = null;
            currentPutField = null;
        }
    }

    /**
     * Writes an integer (32 bit) to the target stream.
     * 
     * @param value
     *            the integer to write to the target stream.
     * @throws IOException
     *             if an error occurs while writing to the target stream.
     */
    @Override
    public void writeInt(int value) throws IOException {
        checkWritePrimitiveTypes();
        primitiveTypes.writeInt(value);
    }

    /**
     * Writes a long (64 bit) to the target stream.
     * 
     * @param value
     *            the long to write to the target stream.
     * @throws IOException
     *             if an error occurs while writing to the target stream.
     */
    @Override
    public void writeLong(long value) throws IOException {
        checkWritePrimitiveTypes();
        primitiveTypes.writeLong(value);
    }

    /**
     * Write array {@code array} of class {@code arrayClass} with
     * component type {@code componentType} into the receiver. It is
     * assumed the array has not been dumped yet. Return an {@code Integer}
     * that represents the handle for this object (array) which is dumped here.
     * 
     * @param array
     *            The array object to dump
     * @param arrayClass
     *            A {@code java.lang.Class} representing the class of the
     *            array
     * @param componentType
     *            A {@code java.lang.Class} representing the array
     *            component type
     * @return the handle assigned to the array
     * 
     * @throws IOException
     *             If an IO exception happened when writing the array.
     */
    private Integer writeNewArray(Object array, Class<?> arrayClass, ObjectStreamClass arrayClDesc,
            Class<?> componentType, boolean unshared) throws IOException {
        output.writeByte(TC_ARRAY);
        writeClassDesc(arrayClDesc, false, arrayClass);

        Integer handle = nextHandle();

        if (!unshared) {
//            System.out.println("Assigned reference handle " + handle + " to " + array);
            objectsWritten.put(array, handle);
        }

        // Now we have code duplication just because Java is typed. We have to
        // write N elements and assign to array positions, but we must typecast
        // the array first, and also call different methods depending on the
        // elements.

        if (componentType.isPrimitive()) {
            if (componentType == Integer.TYPE) {
                int[] intArray = (int[]) array;
                output.writeInt(intArray.length);
                for (int i = 0; i < intArray.length; i++) {
                    output.writeInt(intArray[i]);
                }
            } else if (componentType == Byte.TYPE) {
                byte[] byteArray = (byte[]) array;
                output.writeInt(byteArray.length);
                output.write(byteArray, 0, byteArray.length);
            } else if (componentType == Character.TYPE) {
                char[] charArray = (char[]) array;
                output.writeInt(charArray.length);
                for (int i = 0; i < charArray.length; i++) {
                    output.writeChar(charArray[i]);
                }
            } else if (componentType == Short.TYPE) {
                short[] shortArray = (short[]) array;
                output.writeInt(shortArray.length);
                for (int i = 0; i < shortArray.length; i++) {
                    output.writeShort(shortArray[i]);
                }
            } else if (componentType == Boolean.TYPE) {
                boolean[] booleanArray = (boolean[]) array;
                output.writeInt(booleanArray.length);
                for (int i = 0; i < booleanArray.length; i++) {
                    output.writeBoolean(booleanArray[i]);
                }
            } else if (componentType == Long.TYPE) {
                long[] longArray = (long[]) array;
                output.writeInt(longArray.length);
                for (int i = 0; i < longArray.length; i++) {
                    output.writeLong(longArray[i]);
                }
            } else if (componentType == Float.TYPE) {
                float[] floatArray = (float[]) array;
                output.writeInt(floatArray.length);
                for (int i = 0; i < floatArray.length; i++) {
                    output.writeFloat(floatArray[i]);
                }
            } else if (componentType == Double.TYPE) {
                double[] doubleArray = (double[]) array;
                output.writeInt(doubleArray.length);
                for (int i = 0; i < doubleArray.length; i++) {
                    output.writeDouble(doubleArray[i]);
                }
            } else {
                throw new InvalidClassException(
                        Messages.getString(
                                "luni.C2", arrayClass.getName())); //$NON-NLS-1$
            }
        } else {
            // Array of Objects
            Object[] objectArray = (Object[]) array;
            output.writeInt(objectArray.length);
            for (int i = 0, l = objectArray.length; i < l; i++) {
                // TODO: This place is the opportunity for enhancement
                //      We can implement writing elements through fast-path,
                //      without setting up the context (see writeObject()) for 
                //      each element with public API
                writeObject(objectArray[i]);
            }
        }
        return handle;
    }

    /**
     * Write class {@code object} into the receiver. It is assumed the
     * class has not been dumped yet. Classes are not really dumped, but a class
     * descriptor ({@code ObjectStreamClass}) that corresponds to them.
     * Return an {@code Integer} that represents the handle for this
     * object (class) which is dumped here.
     * 
     * @param clazz
     *            The {@code java.lang.Class} object to dump
     * @return the handle assigned to the class being dumped
     * 
     * @throws IOException
     *             If an IO exception happened when writing the class.
     */
    private Integer writeNewClass(Class<?> clazz, boolean unshared)
            throws IOException {
        output.writeByte(TC_CLASS);

        // Instances of java.lang.Class are always Serializable, even if their
        // instances aren't (e.g. java.lang.Object.class).
        // We cannot call lookup because it returns null if the parameter
        // represents instances that cannot be serialized, and that is not what
        // we want.
        ObjectStreamClass clDesc = ObjectStreamClass.lookupAny(clazz);
        
        // The handle for the classDesc is NOT the handle for the class object
        // being dumped. We must allocate a new handle and return it.
        if (clazz.isEnum()) {
            writeEnumDesc(clazz, clDesc, unshared);
        } else {
            writeClassDesc(clDesc, unshared, clazz);
        }
        Integer handle = nextHandle();

        if (!unshared) {
//            System.out.println("Assigned reference handle " + handle + " to " + clazz);
            objectsWritten.put(clazz, handle);
        }

        return handle;
    }

    /**
     * Write class descriptor {@code classDesc} into the receiver. It is
     * assumed the class descriptor has not been dumped yet. The class
     * descriptors for the superclass chain will be dumped as well. Return an
     * {@code Integer} that represents the handle for this object (class
     * descriptor) which is dumped here.
     * 
     * @param classDesc
     *            The {@code ObjectStreamClass} object to dump
     * 
     * @throws IOException
     *             If an IO exception happened when writing the class
     *             descriptor.
     */
    private void writeNewClassDesc(ObjectStreamClass classDesc, Class theClass)
            throws IOException {
        ObjectStreamClassInformation classInfo;
        classInfo = new ObjectStreamClassInformation();
        classInfo.fullyQualifiedClassName = classDesc.getName();
        classInfo.serialVer = classDesc.getSerialVersionUID();
        if (theClass == null){
            theClass = classDesc.forClass();
        }
        boolean proxy = Proxy.class.equals(theClass);
        if (theClass.isAnnotationPresent(AtomicSerial.class) || proxy){
            classInfo.serializable = true;
            if (!theClass.isAnnotationPresent(Stateless.class) || proxy){
                classInfo.hasWriteObjectData = true;
                classInfo.fields = fields(classDesc.forClass());
            }
        } else {
            classInfo.fields = new ObjectStreamField [0];
        }
        if (theClass.isAnnotationPresent(AtomicExternal.class)){
            classInfo.externalizable = true;
        }
        if (theClass.isEnum())
            classInfo.isEnum = true;
//        System.out.println("Writing class descriptor to stream: " + theClass);
        classInfo.write(this);
    }

    /**
     * Writes a class descriptor to the target stream.
     * 
     * @param classDesc
     *            the class descriptor to write to the target stream.
     * @throws IOException
     *             if an error occurs while writing to the target stream.
     */
    protected void writeClassDescriptor(ObjectStreamClass classDesc)
            throws IOException {
        writeNewClassDesc(classDesc, null);
    }

    /**
     * Write exception {@code ex} into the receiver. It is assumed the
     * exception has not been dumped yet. Return an {@code Integer} that
     * represents the handle for this object (exception) which is dumped here.
     * This is used to dump the exception instance that happened (if any) when
     * dumping the original object graph. The set of seen objects will be reset
     * just before and just after dumping this exception object.
     * 
     * When exceptions are found normally in the object graph, they are dumped
     * as a regular object, and not by this method. In that case, the set of
     * "known objects" is not reset.
     * 
     * @param ex
     *            Exception object to dump
     * 
     * @throws IOException
     *             If an IO exception happened when writing the exception
     *             object.
     */
    private void writeNewException(Exception ex) throws IOException {
        output.writeByte(TC_EXCEPTION);
        resetSeenObjects();
        if (ex.getClass().isAnnotationPresent(AtomicSerial.class)){
            writeObjectInternal(ex, false, false, false); // No replacements
        } else {
            writeObjectInternal(new ThrowableSerializer(ex), false, false, false);
        }
        resetSeenObjects();
    }

    /**
     * Write object {@code object} of class {@code theClass} into
     * the receiver. It is assumed the object has not been dumped yet. Return an
     * {@code Integer} that represents the handle for this object which
     * is dumped here.
     * 
     * If the object implements {@code Externalizable} its
     * {@code writeExternal} is called. Otherwise, all fields described
     * by the class hierarchy is dumped. Each class can define how its declared
     * instance fields are dumped by defining a private method
     * {@code writeObject}
     * 
     * @param object
     *            The object to dump
     * @param theClass
     *            A {@code java.lang.Class} representing the class of the
     *            object
     * @param unshared
     *            Write the object unshared
     * @return the handle assigned to the object
     * 
     * @throws IOException
     *             If an IO exception happened when writing the object.
     */
    private Integer writeNewObject(Object object, Class<?> theClass, ObjectStreamClass clDesc, 
            boolean unshared) throws IOException {
        // Not String, not null, not array, not cyclic reference

        EmulatedFieldsForDumping originalCurrentPutField = currentPutField; // save
        currentPutField = null; // null it, to make sure one will be computed if
        // needed

        boolean externalizable = Externalizable.class.isAssignableFrom(theClass);
        
        boolean serializable = false;
        if (theClass != null){
            serializable = 
                    theClass.isAnnotationPresent(AtomicSerial.class) ||
                    Proxy.isProxyClass(theClass);
        }
        if (!externalizable && !serializable) {
            // Object is neither externalizable nor serializable. Error
            throw new NotSerializableException(theClass.getName());
        }
        
        // Either serializable or externalizable, now we can save info
        output.writeByte(TC_OBJECT);
        writeClassDesc(clDesc, false, theClass);
        Integer previousHandle = null;
        if (unshared) {
            previousHandle = objectsWritten.get(object);
        }
        Integer handle = nextHandle();
//        System.out.println("Assigned reference handle " + handle + " to " + object);
        objectsWritten.put(object, handle);
        // This is how we know what to do in defaultWriteObject. And it is also
        // used by defaultWriteObject to check if it was called from an invalid
        // place.
        // It allows writeExternal to call defaultWriteObject and have it work.
        currentObject = object;
        currentClass = clDesc;
        try {
            if (externalizable) {
                boolean noBlockData = protocolVersion == PROTOCOL_VERSION_1;
                if (noBlockData) {
                    primitiveTypes = output;
                }
                // Object is externalizable, just call its own method
                ((Externalizable) object).writeExternal(this);
                if (noBlockData) {
                    primitiveTypes = null;
                } else {
                    // Similar to the code in writeHierarchy when object
                    // implements writeObject.
                    // Any primitive data has to be flushed and a tag must be
                    // written
                    drain();
                    output.writeByte(TC_ENDBLOCKDATA);
                }
            } else { // If it got here, it has to be Serializable
                // Object is serializable. Walk the class chain writing the
                // fields
                writeHierarchy(object, clDesc, theClass);
            }
        } finally {         
            // Cleanup, needs to run always so that we can later detect invalid
            // calls to defaultWriteObject
            if (unshared) {
                // remove reference to unshared object
                removeUnsharedReference(object, previousHandle);
            }
            currentObject = null;
            currentClass = null;
            currentPutField = originalCurrentPutField;
        }

        return handle;
    }
    
    /**
     * Write String {@code object} into the receiver. It is assumed the
     * String has not been dumped yet. Return an {@code Integer} that
     * represents the handle for this object (String) which is dumped here.
     * Strings are saved encoded with {@link DataInput modified UTF-8}.
     * 
     * @param object
     *            the string to dump.
     * @return the handle assigned to the String being dumped
     * 
     * @throws IOException
     *             If an IO exception happened when writing the String.
     */
    private Integer writeNewString(String object, boolean unshared)
            throws IOException {
        long count = countUTFBytes(object);
        byte[] buffer;
        int offset = 0;
        if (count <= 0xffff) {
            buffer = new byte[(int)count+3];
            buffer[offset++] = TC_STRING;
            offset = writeShortToBuffer((short) count, buffer, offset);
        } else {
            buffer = new byte[(int)count+9];
            buffer[offset++] = TC_LONGSTRING;
            offset = writeLongToBuffer(count, buffer, offset);
        }
        offset = writeUTFBytesToBuffer(object, count, buffer, offset);
        output.write(buffer, 0, offset);

        Integer handle = nextHandle();

        if (!unshared) {
//            System.out.println("Assigned reference handle " + handle + " to " + object);
            objectsWritten.put(object, handle);
        }
        
        return handle;
    }

    /**
     * Write a special tag that indicates the value {@code null} into the
     * receiver.
     * 
     * @throws IOException
     *             If an IO exception happened when writing the tag for
     *             {@code null}.
     */
    private void writeNull() throws IOException {
        output.writeByte(TC_NULL);
    }

    /**
     * Writes an object to the target stream.
     * 
     * @param object
     *            the object to write to the target stream.
     * @throws IOException
     *             if an error occurs while writing to the target stream.
     * @see ObjectInputStream#readObject()
     */
    @Override
    public final void writeObject(Object object) throws IOException {
        writeObject(object, false);
    }

    /**
     * Writes an unshared object to the target stream. This method is identical
     * to {@code writeObject}, except that it always writes a new object to the
     * stream versus the use of back-referencing for identical objects by
     * {@code writeObject}.
     * 
     * @param object
     *            the object to write to the target stream.
     * @throws IOException
     *             if an error occurs while writing to the target stream.
     * @see ObjectInputStream#readUnshared()
     */
    public void writeUnshared(Object object) throws IOException {
        writeObject(object, true);
    }

    private void writeObject(Object object, boolean unshared)
            throws IOException {
        boolean setOutput = (primitiveTypes == output);
        if (setOutput) {
            primitiveTypes = null;
        }
        // This is the spec'ed behavior in JDK 1.2. Very bizarre way to allow
        // behavior overriding.
        if (subclassOverridingImplementation && !unshared) {
            writeObjectOverride(object);
        } else {

            try {
                // First we need to flush primitive types if they were written
                drain();
                // Actual work, and class-based replacement should be computed
                // if needed.
                writeObjectInternal(object, unshared, true, true);
                if (setOutput) {
                    primitiveTypes = output;
                }
            } catch (IOException ioEx1) {
                // This will make it pass through until the top caller. It also
                // lets it pass through the nested exception.
                if (nestedLevels == 0 && ioEx1 != nestedException) {
                    try {
                        writeNewException(ioEx1);
                    } catch (IOException ioEx2) {
                        nestedException.fillInStackTrace();
                        nestedException.addSuppressed(ioEx2);
                        nestedException.addSuppressed(ioEx1);
                        throw nestedException;
                    }
                }
                throw ioEx1; // and then we propagate the original exception
            }
        }
    }

    /**
     * Write object {@code object} into the receiver's underlying stream.
     * 
     * @param object
     *            The object to write
     * @param unshared
     *            Write the object unshared
     * @param computeClassBasedReplacement
     *            A boolean indicating if class-based replacement should be
     *            computed (if supported) for the object.
     * @param computeStreamReplacement
     *            A boolean indicating if stream-based replacement should be
     *            computed (if supported) for the object.
     * @return the handle assigned to the final object being dumped
     * 
     * @throws IOException
     *             If an IO exception happened when writing the object
     * 
     * @see ObjectInputStream#readObject()
     */
    private Integer writeObjectInternal(Object object, boolean unshared,
            boolean computeClassBasedReplacement,
            boolean computeStreamReplacement) throws IOException {

        if (object == null) {
            writeNull();
            return null;
        }
        Integer handle;
        if (!unshared) {
            handle = dumpCycle(object);
            if (handle != null) {
                return handle; // cyclic reference
            }
        }

        // Non-null object, first time seen...
        Class<?> objClass = object.getClass();
        ObjectStreamClass clDesc = ObjectStreamClass.lookupAny(objClass);    
        nestedLevels++;
        try {

            if (!(enableReplace && computeStreamReplacement)) {
                // Is it a Class ?
                if (Class.class.equals(objClass)) {
                    return writeNewClass((Class<?>) object, unshared);
                }
                // Is it an ObjectStreamClass ?
                if (ObjectStreamClass.class.isAssignableFrom(objClass)) {
                    return writeClassDesc((ObjectStreamClass) object, unshared, objClass);
                }
            }

            if ( objClass.isAnnotationPresent(AtomicSerial.class)
                    && computeClassBasedReplacement) {
                
                if(object instanceof Replace){
//                    Method methodWriteReplace = clDesc.getMethodWriteReplace();
                    Object replObj = ((Replace)object).writeReplace(); 
//                    try {
//                        replObj = methodWriteReplace.invoke(object, (Object[]) null);
//                    } catch (IllegalAccessException iae) {
//                        replObj = object;
//                    } catch (InvocationTargetException ite) {
//                        // WARNING - Not sure this is the right thing to do
//                        // if we can't run the method
//                        Throwable target = ite.getTargetException();
//                        if (target instanceof ObjectStreamException) {
//                            throw (ObjectStreamException) target;
//                        } else if (target instanceof Error) {
//                            throw (Error) target;
//                        } else {
//                            throw (RuntimeException) target;
//                        }
//                    }
                    if (replObj != object) {
                        // All over, class-based replacement off this time.
                        Integer replacementHandle = writeObjectInternal(
                                replObj, false, false,
                                computeStreamReplacement);
                        // Make the original object also map to the same
                        // handle.
                        if (replacementHandle != null) {
                            objectsWritten.put(object, replacementHandle);
                        }
                        return replacementHandle;
                    }
                }

            }

            // We get here either if class-based replacement was not needed or
            // if it was needed but produced the same object or if it could not
            // be computed.
            if (enableReplace && computeStreamReplacement) {
                // Now we compute the stream-defined replacement.
                Object streamReplacement = replaceObject(object);
                if (streamReplacement != object) {
                    // All over, class-based replacement off this time.
                    Integer replacementHandle = writeObjectInternal(
                            streamReplacement, false,
                            computeClassBasedReplacement, false);
                    // Make the original object also map to the same handle.
                    if (replacementHandle != null) {
                        objectsWritten.put(object, replacementHandle);
                    }
                    return replacementHandle;
                }
            }

            // We get here if stream-based replacement produced the same object

            // Is it a Class ?
            if (Class.class.equals(objClass)) {
                return writeNewClass((Class<?>) object, unshared);
            }

            // Is it an ObjectStreamClass ?
            if (ObjectStreamClass.class.isAssignableFrom(objClass)) {
                return writeClassDesc((ObjectStreamClass) object, unshared, objClass);
            }

            // Is it a String ? (instanceof, but == is faster)
            if (String.class.equals(objClass)) {
                return writeNewString((String) object, unshared);
            }

            // Is it an Array ?
            if (objClass.isArray()) {
                return writeNewArray(object, objClass, clDesc, objClass
                        .getComponentType(), unshared);
            }

            if (object instanceof Enum) {
                return writeNewEnum((Enum) object, objClass, unshared);
            }

            // Not a String or Class or Array. Default procedure.
            return writeNewObject(object, objClass, clDesc, unshared);
        } finally {
            nestedLevels--;
        }
    }
    
     // write for Enum Class Desc only, which is different from other classes
    private ObjectStreamClass writeEnumDesc(Class<?> theClass, ObjectStreamClass classDesc, boolean unshared)
            throws IOException {
        // write classDesc, classDesc for enum is different
        Integer previousHandle = null;
        if (unshared) {
            previousHandle = objectsWritten.get(classDesc);
        }
        Integer handle = null;
        if (!unshared) {
            handle = dumpCycle(classDesc);
        }
        if (handle == null) {
            // If we got here, it is a new (non-null) classDesc that will have
            // to be registered as well
            Integer handl =  nextHandle();
//            System.out.println("Assigned reference handle " + handl + " to " + classDesc);
            objectsWritten.put(classDesc, handl);

            output.writeByte(TC_CLASSDESC);
            if (protocolVersion == PROTOCOL_VERSION_1) {
                writeNewClassDesc(classDesc, theClass);
            } else {
                // So write...() methods can be used by
                // subclasses during writeClassDescriptor()
                primitiveTypes = output;
                writeClassDescriptor(classDesc);
                primitiveTypes = null;
            }
            // Extra class info (optional)
            annotateClass(theClass);
            drain(); // flush primitive types in the annotation
            output.writeByte(TC_ENDBLOCKDATA);
            // write super class
            Class superClass = theClass.getSuperclass();
            ObjectStreamClass superClassDesc = ObjectStreamClass.lookup(superClass);
            if (null != superClassDesc) {
                // super class is also enum
                writeEnumDesc(superClass, superClassDesc, unshared);
            } else {
                output.writeByte(TC_NULL);
            }
            if (unshared) {
                // remove reference to unshared object
                removeUnsharedReference(classDesc, previousHandle);
            }
        }
        return classDesc;
    }

    private Integer writeNewEnum(Enum object, Class<?> theClass,
            boolean unshared) throws IOException {
        // write new Enum
        EmulatedFieldsForDumping originalCurrentPutField = currentPutField; // save
        // null it, to make sure one will be computed if needed
        currentPutField = null;

        output.writeByte(TC_ENUM);
        while (theClass != null && !theClass.isEnum()) {
            // write enum only
            theClass = theClass.getSuperclass();
        }
        ObjectStreamClass classDesc = ObjectStreamClass.lookup(theClass);
        writeEnumDesc(theClass, classDesc, unshared);
       

        Integer previousHandle = null;
        if (unshared) {
            previousHandle = objectsWritten.get(object);
        }
        Integer handle = nextHandle();
//        System.out.println("Assigned reference handle " + handle + " to " + object);
        objectsWritten.put(object, handle);
        
        
        // Only write field "name" for enum class
        String str = object.name();
        writeNewString(str, false);

        if (unshared) {
            // remove reference to unshared object
            removeUnsharedReference(object, previousHandle);
        }
        currentPutField = originalCurrentPutField;
        return handle;
    }

    /**
     * Method to be overridden by subclasses to write {@code object} to the
     * target stream.
     * 
     * @param object
     *            the object to write to the target stream.
     * @throws IOException
     *             if an error occurs while writing to the target stream.
     */
    protected void writeObjectOverride(Object object) throws IOException {
        if (!subclassOverridingImplementation) {
            // Subclasses must override.
            throw new IOException();
        }
    }

    /**
     * Writes a short (16 bit) to the target stream.
     * 
     * @param value
     *            the short to write to the target stream.
     * @throws IOException
     *             if an error occurs while writing to the target stream.
     */
    @Override
    public void writeShort(int value) throws IOException {
        checkWritePrimitiveTypes();
        primitiveTypes.writeShort(value);
    }

    /**
     * Writes the {@link ObjectOutputStream} header to the target stream.
     * 
     * @throws IOException
     *             if an error occurs while writing to the target stream.
     */
    protected void writeStreamHeader() throws IOException {
        output.writeShort(STREAM_MAGIC);
        output.writeShort(STREAM_VERSION);
    }

    /**
     * Writes a string encoded with {@link DataInput modified UTF-8} to the
     * target stream.
     * 
     * @param value
     *            the string to write to the target stream.
     * @throws IOException
     *             if an error occurs while writing to the target stream.
     */
    @Override
    public void writeUTF(String value) throws IOException {
        checkWritePrimitiveTypes();
        primitiveTypes.writeUTF(value);
    }
    
    long countUTFBytes(String str) {
        int utfCount = 0, length = str.length();
        for (int i = 0; i < length; i++) {
            int charValue = str.charAt(i);
            if (charValue > 0 && charValue <= 127) {
                utfCount++;
            } else if (charValue <= 2047) {
                utfCount += 2;
            } else {
                utfCount += 3;
            }
        }
        return utfCount;
    }
    
    int writeShortToBuffer(int val,
                           byte[] buffer, int offset) throws IOException {
        buffer[offset++] = (byte) (val >> 8);
        buffer[offset++] = (byte) val;
        return offset;
    }
    
    int writeLongToBuffer(long val,
                          byte[] buffer, int offset) throws IOException {
        buffer[offset++] = (byte) (val >> 56);
        buffer[offset++] = (byte) (val >> 48);
        buffer[offset++] = (byte) (val >> 40);
        buffer[offset++] = (byte) (val >> 32);
        buffer[offset++] = (byte) (val >> 24);
        buffer[offset++] = (byte) (val >> 16);
        buffer[offset++] = (byte) (val >> 8);
        buffer[offset++] = (byte) val;
        return offset;
    }
    
    int writeUTFBytesToBuffer(String str, long count,
                              byte[] buffer, int offset) throws IOException {
        int length = str.length();
        for (int i = 0; i < length; i++) {
            int charValue = str.charAt(i);
            if (charValue > 0 && charValue <= 127) {
                buffer[offset++] = (byte) charValue;
            } else if (charValue <= 2047) {
                buffer[offset++] = (byte) (0xc0 | (0x1f & (charValue >> 6)));
                buffer[offset++] = (byte) (0x80 | (0x3f & charValue));
            } else {
                buffer[offset++] = (byte) (0xe0 | (0x0f & (charValue >> 12)));
                buffer[offset++] = (byte) (0x80 | (0x3f & (charValue >> 6)));
                buffer[offset++] = (byte) (0x80 | (0x3f & charValue));
             }
        }
        return offset;
    }

    @Override
    public Collection getObjectStreamContext() {
        return context;
    }

}
