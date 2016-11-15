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
import java.io.DataInput;
import java.io.DataInputStream;
import java.io.EOFException;
import java.io.Externalizable;
import java.io.IOException;
import java.io.InputStream;
import java.io.InvalidClassException;
import java.io.InvalidObjectException;
import java.io.NotActiveException;
import java.io.NotSerializableException;
import java.io.ObjectInputStream;
import java.io.ObjectInputValidation;
import java.io.ObjectOutputStream;
import java.io.ObjectStreamClass;
import java.io.ObjectStreamConstants;
import static java.io.ObjectStreamConstants.TC_LONGSTRING;
import static java.io.ObjectStreamConstants.TC_NULL;
import static java.io.ObjectStreamConstants.TC_REFERENCE;
import static java.io.ObjectStreamConstants.TC_STRING;
import java.io.ObjectStreamException;
import java.io.ObjectStreamField;
import java.io.OptionalDataException;
import java.io.PushbackInputStream;
import java.io.Serializable;
import java.io.SerializablePermission;
import java.io.StreamCorruptedException;
import java.io.UTFDataFormatException;
import java.io.WriteAbortedException;
import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Proxy;
import java.security.AccessController;
import java.security.Permission;
import java.security.PrivilegedAction;
import java.security.PrivilegedActionException;
import java.security.PrivilegedExceptionAction;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.jini.io.MarshalInputStream;
import org.apache.river.api.io.AtomicSerial.Factory;
import org.apache.river.api.io.AtomicSerial.GetArg;
import org.apache.river.api.io.AtomicSerial.ReadObject;
import org.apache.river.impl.Messages;


/**
 * <h1>ObjectInputStream hardened against DOS attack.</h1>
 * 
 *    <h2>De-serialization Not supported:</h2>
 * <p>
 * <ul>
 *      <li><code>private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException</code></li>
 *      <li>Object graphs with circular references.</li>
 *      <li>Object construction using the first non serializable superclass zero
 *      arg constructor.</li>
 * </ul>
 *</p>
 *    <h2>De-serialization Supported:</h2>
 * <p>
 * <ul>
 *      <li>Classes annotated with @AtomicSerial, that provide a single GetArg parameter 
 *      constructor and throw IOException.</li>
 *      <li>Classes annotated with @AtomicExternal, that provide a single ObjectInput parameter
 *      constructor and throw IOException.</li>
 *      <li>Classes that implement {@link Externalizable}
 *      <li>String's, arrays, enums, primitive values.</li>
 *      <li>{@link Serializable} object's with a public zero arg constructor,
 *      with serial forms that contain only primitive fields, any object fields must
 *      be marked transient.</li>
 *      <li>{@link net.jini.core.entry.Entry}, stream data will be checked against
 *      each field type.</li>
 * </ul>
 * </p><p>
 *      Any of the above classes that have the appropriate {@link DeSerializationPermission},
 *      {@link Serializable} object's that have only primitive serial form, don't
 *      require {@link DeSerializationPermission}.
 *      The Serialization stream protocol.
 *</p>
 *    <h2>Informational:</h2>
 * <p>
 *      Collection, List Set, SortedSet, Map and SortedMap, are replaced in
 *      AtomicObjectOutputStream with immutable implementations that guard
 *      against denial of service attacks.  These collections are not intended
 *      to be used in de-serialized form, other than for passing as an argument
 *      to create a new collection.  Collections should be type checked during
 *      validation before a superclass constructor is called.
 * </p><p>
 *      AtomicMarshalInputStream is restricted to caching 2^16 objects, and a total combined
 *      array length of Integer.MAX_VALUE - 8, for all arrays, the
 *      stream must be reset prior to exceeding these limits or a 
 *      StreamCorruptedException will be thrown and control will return to the caller.
 * </p><p>
 *	JVM arguments should be adjusted to ensure that an OOME will not be thrown
 *	if these limits are reached.
 *</p>
 * @author peter
 */
public class AtomicMarshalInputStream extends MarshalInputStream {
  
    private final InputStream emptyStream = new ByteArrayInputStream(
            new byte[0]);
    
    // These two settings are to prevent DOS attacks.
    private static final long MAX_COMBINED_ARRAY_LEN = Integer.MAX_VALUE - 8;
    private static final int MAX_OBJECT_CACHE = 65664;
    
    private static final Class [] EMPTY_CONSTRUCTOR_PARAM_TYPES = new Class[0];
    
    static final int FIELD_IS_NOT_RESOLVED = -1;
    static final int FIELD_IS_ABSENT = -2;
    
    private static final Permission EXTERNALIZABLE = new DeSerializationPermission("EXTERNALIZABLE");
    private static final Permission ATOMIC = new DeSerializationPermission("ATOMIC");
    private static final Permission ENTRY = new DeSerializationPermission("ENTRY");
    private static final Permission PROXY = new DeSerializationPermission("PROXY");

    // If the receiver has already read & not consumed a TC code
    private boolean hasPushbackTC;

    // How many nested levels to readObject. When we reach 0 we have to validate
    // the graph then reset it
    private int nestedLevels;
    
    // Prevents DOS attacks.
    private long arrayLenAllowedRemain;

    // All objects are assigned an ID (integer handle)
    private int currentHandle;
    
    // Where we push back a byte
    private final PushbackInputStream pushbackInputStream;

    // Where we read from
    private final DataInputStream input;

    // Where we read primitive types from
    private final DataInputStream primitiveTypes;

    // Where we keep primitive type data
    private InputStream primitiveData = emptyStream;
    
    // Resolve object is a mechanism for replacement
    private volatile boolean enableResolve;

    // Table mapping Integer (handle) -> Object
//    private Map<Integer, Object> objectsRead;
    
    // Table of Objects, synchronized access to prevent unsafe publication of shared Objects.
    private final Object [] objectsRead = new Object[MAX_OBJECT_CACHE];

    // Used by defaultReadObject
    private volatile Object currentObject;

    // Used by defaultReadObject
    private volatile ObjectStreamClassContainer currentClass;
    
    // All validations to be executed when the complete graph is read. See inner
    // type below.
    private InputValidationDesc[] validations;


    // Original caller's class loader, used to perform class lookups
//    private ClassLoader callerClassLoader;

    // false when reading missing fields
    private boolean mustResolve = true;

    // Handle for the current class descriptor
    private int descriptorHandle;
    
    // Compatible with ObjectInputStream - no annotations.
    private final boolean objectInputStreamCompat;

    private static final HashMap<String, Class<?>> PRIMITIVE_CLASSES =
        new HashMap<String, Class<?>>();
    
    private final SerializationConstructor sc;

    static {
        PRIMITIVE_CLASSES.put("byte", byte.class); //$NON-NLS-1$
        PRIMITIVE_CLASSES.put("short", short.class); //$NON-NLS-1$
        PRIMITIVE_CLASSES.put("int", int.class); //$NON-NLS-1$
        PRIMITIVE_CLASSES.put("long", long.class); //$NON-NLS-1$
        PRIMITIVE_CLASSES.put("boolean", boolean.class); //$NON-NLS-1$
        PRIMITIVE_CLASSES.put("char", char.class); //$NON-NLS-1$
        PRIMITIVE_CLASSES.put("float", float.class); //$NON-NLS-1$
        PRIMITIVE_CLASSES.put("double", double.class); //$NON-NLS-1$
        PRIMITIVE_CLASSES.put("void", void.class); //$NON-NLS-1$
    }

    // Internal type used to keep track of validators & corresponding priority
    static class InputValidationDesc {
        ObjectInputValidation validator;

        int priority;
    }
    
    /**
     * Static factory method to obtain an instance without checking for
     * SerializablePermission("enableSubclassImplementation")
     * 
     * @param in
     * @param defaultLoader
     * @param verifyCodebaseIntegrity
     * @param verifierLoader
     * @param context
     * @return
     * @throws IOException 
     */
    public static MarshalInputStream create(final InputStream in,
				    final ClassLoader defaultLoader,
				    final boolean verifyCodebaseIntegrity,
				    final ClassLoader verifierLoader,
				    final Collection context) throws IOException{
	try {
	    return AccessController.doPrivileged(new PrivilegedExceptionAction<MarshalInputStream>(){

		@Override
		public MarshalInputStream run() throws Exception {
		    return new AtomicMarshalInputStream(
			    in,
			    defaultLoader,
			    verifyCodebaseIntegrity,
			    verifierLoader,
			    context,
			    false, false
		    ); 		}
		
	    });
	} catch (PrivilegedActionException ex) {
	    Exception e = ex.getException();
	    if (e instanceof IOException) throw (IOException)e;
	    if (e instanceof RuntimeException) throw (RuntimeException)e;
	    throw new IOException("Exception thrown during construction", ex);
	}
        
    }
    
    /**
     * The instance returned can de-serialize data written by 
     * {@link java.io.ObjectOutputStream}, however it is not compatible
     * with {@link net.jini.io.MarshalOutputStream}.
     * 
     * @param in
     * @param defaultLoader
     * @param verifyCodebaseIntegrity
     * @param verifierLoader
     * @param context
     * @return
     * @throws IOException 
     */
    public static ObjectInputStream createObjectInputStream(final InputStream in,
				    final ClassLoader defaultLoader,
				    final boolean verifyCodebaseIntegrity,
				    final ClassLoader verifierLoader,
				    final Collection context) throws IOException{
	try {
	    return AccessController.doPrivileged(new PrivilegedExceptionAction<ObjectInputStream>(){

		@Override
		public ObjectInputStream run() throws Exception {
		    return new AtomicMarshalInputStream(
			    in,
			    defaultLoader,
			    verifyCodebaseIntegrity,
			    verifierLoader,
			    context,
			    false,
			    true
		    ); 		}
		
	    });
	} catch (PrivilegedActionException ex) {
	    Exception e = ex.getException();
	    if (e instanceof IOException) throw (IOException)e;
	    if (e instanceof RuntimeException) throw (RuntimeException)e;
	    throw new IOException("Exception thrown during construction", ex);
	}
        
    }
    
    private static InputStream check(InputStream in, final boolean subclassCheck){
	if (in == null) throw new NullPointerException();
	// SecurityException's propagate, cause them to be thrown prior to
	// superclass instantiation.
	if (subclassCheck){
	    AccessController.doPrivileged(new PrivilegedAction<Object>() {
		@Override
		public Object run() {
		    new SerializablePermission("enableSubclassImplementation").checkGuard(null);
		    new SerializablePermission("enableSubstitution").checkGuard(null);
		    return null;
		}
	    });
	}
	return in;
    }
    
    /**
     * Constructs a new ObjectInputStream that reads from the InputStream
     * {@code input}.
     * 
     * @param input
     *            the non-null source InputStream to filter reads on.
     * @param defaultLoader
     * @param verifyCodebaseIntegrity
     * @param verifierLoader
     * @param context
     * @throws IOException
     *             if an error occurs while reading the stream header.
     * @throws StreamCorruptedException
     *             if the source stream does not contain serialized objects that
     *             can be read.
     * @throws SecurityException
     *             if a security manager is installed and it denies subclassing
     *             this class.
     */
    public AtomicMarshalInputStream(InputStream input,
			      ClassLoader defaultLoader,
			      boolean verifyCodebaseIntegrity,
			      ClassLoader verifierLoader,
			      Collection context)
	throws IOException
    {
	this(input, defaultLoader, verifyCodebaseIntegrity, verifierLoader, context, true, false);
    }
    
    private AtomicMarshalInputStream(InputStream input,
	    ClassLoader defaultLoader,
	    boolean verifyCodebaseIntegrity,
	    ClassLoader verifierLoader,
	    Collection context,
	    boolean subclassCheck,
	    boolean objectInputStreamCompatible) 
	    throws IOException
    {
	this(true,
	    check(input, subclassCheck),
	    defaultLoader,
	    verifyCodebaseIntegrity,
	    verifierLoader,
	    context, false);
    }
    
    private AtomicMarshalInputStream(
	    boolean checked,
	    InputStream input,
	    ClassLoader defaultLoader,
	    boolean verifyCodebaseIntegrity,
	    ClassLoader verifierLoader,
	    Collection context,
	    boolean objectInputStreamCompat) throws IOException
    {
        super(defaultLoader, verifyCodebaseIntegrity, verifierLoader, context);
	this.sc = new SerializationConstructor();
//	this.objectsRead = new NonBlockingHashMap<Integer,Object>(); // Assists with object publication consumes minimal memory
	pushbackInputStream = new PushbackInputStream(input);
        this.input = new DataInputStream(pushbackInputStream);
        primitiveTypes = new DataInputStream(this);
        enableResolve = false;
        resetState();
        // So read...() methods can be used by
        // subclasses during readStreamHeader()
        primitiveData = this.input;
        // Stream header has to be read in here according to the specification
	readStreamHeader();
        primitiveData = emptyStream;
	this.objectInputStreamCompat = objectInputStreamCompat;
    }
    
   
   

    /**
     * Returns the number of bytes of primitive data that can be read from this
     * stream without blocking. This method should not be used at any arbitrary
     * position; just when reading primitive data types (int, char etc).
     * 
     * @return the number of available primitive data bytes.
     * @throws IOException
     *             if any I/O problem occurs while computing the available
     *             bytes.
     */
    @Override
    public int available() throws IOException {
        // returns 0 if tc data is an object, or N if reading primitive types
	checkReadPrimitiveTypes();
	return primitiveData.available();
    }

    /**
     * Checks to if it is ok to read primitive types from this stream at
     * this point. One is not supposed to read primitive types when about to
     * read an object, for example, so an exception has to be thrown.
     * 
     * @throws IOException
     *             If any IO problem occurred when trying to read primitive type
     *             or if it is illegal to read primitive types
     */
    private void checkReadPrimitiveTypes() throws IOException {
        // If we still have primitive data, it is ok to read primitive data
        if (primitiveData == input || primitiveData.available() > 0) {
            return;
        }

        // If we got here either we had no Stream previously created or
        // we no longer have data in that one, so get more bytes
        do {
            byte tc = nextTCNoEOFEx();
            switch (tc) {
                case TC_BLOCKDATA:
//		    System.out.println("TC_BLOCKDATA");
                    primitiveData = new ByteArrayInputStream(readBlockData());
                    return;
                case TC_BLOCKDATALONG:
//		    System.out.println("TC_BLOCKDATALONG");
                    primitiveData = new ByteArrayInputStream(
                            readBlockDataLong());
                    return;
                case TC_RESET:
//		    System.out.println("TC_RESET");
                    resetState();
                    break;
                default:
                    if (tc != -1) {
                        pushbackTC(tc);
                    }
                    return;
            }
            // Only TC_RESET falls through
        } while (true);
    }
    
    private void readyPrimitiveData() throws IOException{
	byte tc = nextTC();
	switch (tc) {
	    case TC_BLOCKDATA:
//				System.out.println("TC_BLOCKDATA");
		primitiveData = new ByteArrayInputStream(readBlockData());
		break;
	    case TC_BLOCKDATALONG:
//				System.out.println("TC_BLOCKDATALONG");
		primitiveData = new ByteArrayInputStream(
			readBlockDataLong());
		break;
	    case TC_RESET:
//				System.out.println("TC_RESET");
		resetState();
		break;
	    default:
		if (tc >= 0 && (tc < TC_BASE || tc > TC_MAX)) {
		    throw new StreamCorruptedException("invalid type code: " + tc);
		}
	}
    }

    /**
     * Closes this stream. This implementation closes the source stream.
     * 
     * @throws IOException
     *             if an error occurs while closing this stream.
     */
    @Override
    public void close() throws IOException {
	// Don't sync
	input.close();
    }

    /**
     * Default method to read objects from this stream. Serializable fields
     * defined in the object's class and superclasses are read from the source
     * stream.
     * 
     * @throws ClassNotFoundException
     *             if the object's class cannot be found.
     * @throws IOException
     *             if an I/O error occurs while reading the object data.
     * @throws NotActiveException
     *             if this method is not called from {@code readObject()}.
     * @see ObjectOutputStream#defaultWriteObject
     */
    @Override
    public void defaultReadObject() throws IOException, ClassNotFoundException,
            NotActiveException {
        // We can't be called from just anywhere. There are rules.
//	Throwable t = new Throwable("debugging");
//	System.out.println("defaultReadObject called");
//	t.printStackTrace(System.out);
	if (currentObject != null || !mustResolve) {
//	    System.out.println("defaultReadObject, currentObject: " + currentObject);
	    readFieldValues(currentObject, currentClass);
	} else {
	    throw new NotActiveException();
	}
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
     * @see #resolveObject
     * @see ObjectOutputStream#enableReplaceObject
     */
    @Override
    protected boolean enableResolveObject(boolean enable)
            throws SecurityException {
        if (enable) {
            // The Stream has to be trusted for this feature to be enabled.
            // trusted means the stream's classloader has to be null
            SecurityManager currentManager = System.getSecurityManager();
            if (currentManager != null) {
                currentManager.checkPermission(SUBSTITUTION_PERMISSION);
            }
        }
        boolean originalValue = enableResolve;
        enableResolve = enable;
        return originalValue;
    }

    /**
     * Checks if two classes belong to the same package.
     * 
     * @param c1
     *            one of the classes to test.
     * @param c2
     *            the other class to test.
     * @return {@code true} if the two classes belong to the same package,
     *         {@code false} otherwise.
     */
    private boolean inSamePackage(Class<?> c1, Class<?> c2) {
        String nameC1 = c1.getName();
        String nameC2 = c2.getName();
        int indexDotC1 = nameC1.lastIndexOf('.');
        int indexDotC2 = nameC2.lastIndexOf('.');
        if (indexDotC1 != indexDotC2) {
            return false; // cannot be in the same package if indices are not
        }
        // the same
        if (indexDotC1 < 0) {
            return true; // both of them are in default package
        }
        return nameC1.substring(0, indexDotC1).equals(
                nameC2.substring(0, indexDotC2));
    }

    /**
     * Return the tc {@code int} handle to be used to indicate cyclic
     * references being loaded from the stream.
     * 
     * @return the tc handle to represent the tc cyclic reference
     */
    int nextHandle() {
        return currentHandle++;
    }

    /**
     * Return the tc token code (TC) from the receiver, which indicates what
 kind of object follows
     * 
     * @return the tc TC from the receiver
     * 
     * @throws IOException
     *             If an IO error occurs
     * 
     * @see ObjectStreamConstants
     */
    byte nextTC() throws IOException {
        byte tc = nextTCNoEOFEx();
	if (tc < 0) throw new EOFException();
	return tc;
    }
    
    private byte nextTCNoEOFEx() throws IOException {
	hasPushbackTC = false;
	return (byte) input.read();
    }

    /**
     * Pushes back the last TC code read
     * 
     * @throws IOException if pushed back again before nextTC() is called.
     */
    private void pushbackTC(byte tc) throws IOException {
	pushbackInputStream.unread(tc);
	hasPushbackTC = true;
    }

    /**
     * Reads a single byte from the source stream and returns it as an integer
     * in the range from 0 to 255. Returns -1 if the end of the source stream
     * has been reached. Blocks if no input is available.
     * 
     * @return the byte read or -1 if the end of the source stream has been
     *         reached.
     * @throws IOException
     *             if an error occurs while reading from this stream.
     */
    @Override
    public int read() throws IOException {
	checkReadPrimitiveTypes();
	return primitiveData.read();
    }

    /**
     * Reads at most {@code length} bytes from the source stream and stores them
     * in byte array {@code buffer} starting at offset {@code count}. Blocks
     * until {@code count} bytes have been read, the end of the source stream is
     * detected or an exception is thrown.
     * 
     * @param buffer
     *            the array in which to store the bytes read.
     * @param offset
     *            the initial position in {@code buffer} to store the bytes
     *            read from the source stream.
     * @param length
     *            the maximum number of bytes to store in {@code buffer}.
     * @return the number of bytes read or -1 if the end of the source input
     *         stream has been reached.
     * @throws IndexOutOfBoundsException
     *             if {@code offset < 0} or {@code length < 0}, or if
     *             {@code offset + length} is greater than the length of
     *             {@code buffer}.
     * @throws IOException
     *             if an error occurs while reading from this stream.
     * @throws NullPointerException
     *             if {@code buffer} is {@code null}.
     */
    @Override
    public int read(byte[] buffer, int offset, int length) throws IOException {
	// Force buffer null check first!
	if (offset > buffer.length || offset < 0) {
	    // luni.12=Offset out of bounds \: {0}
	    throw new ArrayIndexOutOfBoundsException(Messages.getString("luni.12", offset)); //$NON-NLS-1$
	}
	if (length < 0 || length > buffer.length - offset) {
	    // luni.18=Length out of bounds \: {0}
	    throw new ArrayIndexOutOfBoundsException(Messages.getString("luni.18", length)); //$NON-NLS-1$
	}
	if (length == 0) {
	    return 0;
	}
	checkReadPrimitiveTypes();
	return primitiveData.read(buffer, offset, length);
    }

    /**
     * Reads and returns an array of raw bytes with primitive data. The array
     * will have up to 255 bytes. The primitive data will be in the format
     * described by {@code DataOutputStream}.
     * 
     * @return The primitive data read, as raw bytes
     * 
     * @throws IOException
     *             If an IO exception happened when reading the primitive data.
     */
    private byte[] readBlockData() throws IOException {
        byte[] result = new byte[input.readByte() & 0xff];
        input.readFully(result);
        return result;
    }

    /**
     * Reads and returns an array of raw bytes with primitive data. The array
     * will have more than 255 bytes. The primitive data will be in the format
     * described by {@code DataOutputStream}.
     * 
     * @return The primitive data read, as raw bytes
     * 
     * @throws IOException
     *             If an IO exception happened when reading the primitive data.
     */
    private byte[] readBlockDataLong() throws IOException {
	int length = input.readInt();
	if (length > arrayLenAllowedRemain){
	    try {
		close();
	    } catch (IOException e){} // Ignore
	    throw new IOException("Attempt to excessively long array of raw bytes");
	}
        byte[] result = new byte[length];
	arrayLenAllowedRemain = arrayLenAllowedRemain - length;
        input.readFully(result);
        return result;
    }

    /**
     * Reads a boolean from the source stream.
     * 
     * @return the boolean value read from the source stream.
     * @throws EOFException
     *             if the end of the input is reached before the read
     *             request can be satisfied.
     * @throws IOException
     *             if an error occurs while reading from the source stream.
     */
    @Override
    public boolean readBoolean() throws IOException {
	return primitiveTypes.readBoolean();
    }

    /**
     * Reads a byte (8 bit) from the source stream.
     * 
     * @return the byte value read from the source stream.
     * @throws EOFException
     *             if the end of the input is reached before the read
     *             request can be satisfied.
     * @throws IOException
     *             if an error occurs while reading from the source stream.
     */
    @Override
    public byte readByte() throws IOException {
	return primitiveTypes.readByte();
    }

    /**
     * Reads a character (16 bit) from the source stream.
     * 
     * @return the char value read from the source stream.
     * @throws EOFException
     *             if the end of the input is reached before the read
     *             request can be satisfied.
     * @throws IOException
     *             if an error occurs while reading from the source stream.
     */
    @Override
    public char readChar() throws IOException {
	return primitiveTypes.readChar();
    }

    /**
     * Reads and discards block data and objects until TC_ENDBLOCKDATA is found.
     * 
     * @throws IOException
     *             If an IO exception happened when reading the optional class
     *             annotation.
     * @throws ClassNotFoundException
     *             If the class corresponding to the class descriptor could not
     *             be found.
     */
    private void discardData() throws ClassNotFoundException, IOException {
//	System.out.println("discardData");
        primitiveData = emptyStream;
        boolean resolve = mustResolve;
        mustResolve = false;
        do {
            byte tc = nextTC();
            if (tc == TC_ENDBLOCKDATA) {
//		System.out.println("TC_ENDBLOCKDATA");
                mustResolve = resolve;
		return;
            } 
            readContent(tc, true);
        } while (true);
    }

    /**
     * Reads a class descriptor (an {@code ObjectStreamClass}) from the
     * stream.
     * 
     * @return the class descriptor read from the stream
     * 
     * @throws IOException
     *             If an IO exception happened when reading the class
     *             descriptor.
     * @throws ClassNotFoundException
     *             If the class corresponding to the class descriptor could not
     *             be found.
     */
    private ObjectStreamClassContainer readClassDesc() throws ClassNotFoundException,
            IOException {
//	System.out.println("Reading class descriptor");
        byte tc = nextTC();
        switch (tc) {
            case TC_CLASSDESC:
//		System.out.println("TC_CLASSDESC");
                return readNewClassDesc(false);
            case TC_PROXYCLASSDESC:
//		System.out.println( "TC_PROXYCLASSDESC");
                return readNewProxyClassDesc();
            case TC_REFERENCE:
//		System.out.println( "TC_REFERENCE");
                return  (ObjectStreamClassContainer) readCyclicReference();
            case TC_NULL:
//		System.out.println( "TC_NULL");
                return null;
            default:
                throw new StreamCorruptedException(Messages.getString(
                        "luni.BC", Integer.toHexString(tc & 0xff))); //$NON-NLS-1$
        }
    }

    /**
     * Reads the content of the receiver based on the previously read token
     * {@code tc}.
     * 
     * @param tc
     *            The token code for the tc item in the stream
     * @return the object read from the stream
     * 
     * @throws IOException
     *             If an IO exception happened when reading the class
     *             descriptor.
     * @throws ClassNotFoundException
     *             If the class corresponding to the object being read could not
     *             be found.
     */
    private Object readContent(byte tc, boolean discard) throws ClassNotFoundException,
            IOException {
        switch (tc) {
            case TC_BLOCKDATA:
//		System.out.println("TC_BLOCKDATA");
                return readBlockData();
            case TC_BLOCKDATALONG:
//		System.out.println("TC_BLOCKDATALONG");
                return readBlockDataLong();
            case TC_CLASS:
//		System.out.println("TC_CLASS");
                return readNewClass(false);
            case TC_CLASSDESC:
//		System.out.println("TC_CLASSDESC");
                return readNewClassDesc(false);
            case TC_ARRAY:
//		System.out.println("TC_ARRAY");
                return readNewArray(false, null);
            case TC_OBJECT:
//		System.out.println("TC_OBJECT");
                return readNewObject(false, discard, null);
            case TC_STRING:
//		System.out.println("TC_STRING");
                return readNewString(false);
            case TC_LONGSTRING:
//		System.out.println("TC_LONGSTRING");
                return readNewLongString(false);
            case TC_REFERENCE:
//		System.out.println("TC_REFERENCE");
                return readCyclicReference();
            case TC_NULL:
//		System.out.println("TC_NULL");
                return null;
            case TC_EXCEPTION:
//		System.out.println("TC_EXCEPTION");
                Exception exc = readException();
                throw new WriteAbortedException(Messages.getString("luni.BD"), exc); //$NON-NLS-1$
            case TC_RESET:
//		System.out.println("TC_RESET");
                resetState();
                return null;
            default:
                throw new StreamCorruptedException(Messages.getString(
                        "luni.BC", Integer.toHexString(tc & 0xff))); //$NON-NLS-1$
        }
    }

    /**
     * Reads the content of the receiver based on the previously read token
     * {@code tc}. Primitive data content is considered an error.
     * 
     * @param unshared
     *            read the object unshared
     * @return the object read from the stream
     * 
     * @throws IOException
     *             If an IO exception happened when reading the class
     *             descriptor.
     * @throws ClassNotFoundException
     *             If the class corresponding to the object being read could not
     *             be found.
     */
    private Object readNonPrimitiveContent(boolean unshared, Class type)
            throws ClassNotFoundException, IOException {
//	System.out.println("readNonPrimitiveContent");
        checkReadPrimitiveTypes();
        if (primitiveData.available() > 0) {
	    throw new StreamCorruptedException("unexpected block data");
        }
        do {
	    Object result;
            byte tc = nextTC();
            switch (tc) {
                case TC_CLASS:
//		    System.out.println("TC_CLASS");
		    return readNewClass(unshared);
                case TC_CLASSDESC:
//		    System.out.println("TC_CLASSDESC");
                    return readNewClassDesc(unshared);
                case TC_ARRAY:
//		    System.out.println("TC_ARRAY");
                    return readNewArray(unshared, type);
                case TC_OBJECT:
//		    System.out.println("TC_OBJECT");
                    return readNewObject(unshared, false, type);
                case TC_STRING:
		    if (type != null && !type.equals(String.class)){
			throw new InvalidObjectException("Was expecting " + type + " but got a string");
		    }
//		    System.out.println("TC_STRING");
                    return readNewString(unshared);
                case TC_LONGSTRING:
		    if (type != null && !type.equals(String.class)){
			throw new InvalidObjectException("Was expecting " + type + " but got a long string");
		    }
//		    System.out.println("TC_LONGSTRING");
                    return readNewLongString(unshared);
                case TC_ENUM:
//		    System.out.println("TC_ENUM");
                    result = readEnum(unshared);
		    if (type != null && !type.isInstance(result)) throw new InvalidObjectException("Was expecting " + type + " but got: "+  result );
		    return result;
                case TC_REFERENCE:
//		    System.out.println("TC_REFERENCE");
                    if (unshared) {
                        readNewHandle();
                        throw new InvalidObjectException(Messages.getString("luni.BE")); //$NON-NLS-1$
                    }
                    result = readCyclicReference();
		    if (type != null && type.isInstance(result));
		    return result;
                case TC_NULL:
//		    System.out.println("TC_NULL");
                    return null;
                case TC_EXCEPTION:
//		    System.out.println("TC_EXCEPTION");
		    if (type != null && type.isAssignableFrom(Exception.class))
			throw new InvalidObjectException("Was expecting " + type 
				+ " but got an Exception");
                    Exception exc = readException();
                    throw new WriteAbortedException(Messages.getString("luni.BD"), exc); //$NON-NLS-1$
                case TC_RESET:
//		    System.out.println("TC_RESET");
                    resetState();
                    break;
                case TC_ENDBLOCKDATA: // Can occur reading class annotation
//		    System.out.println("TC_ENDBLOCKDATA");
                    pushbackTC(tc);
                    throw new StreamCorruptedException("unexpected end of block data");
                default:
		    System.out.println("default");
                    throw new StreamCorruptedException(Messages.getString(
                            "luni.BC", Integer.toHexString(tc & 0xff))); //$NON-NLS-1$
            }
            // Only TC_RESET falls through
        } while (true);
    }

    /**
     * Reads the tc item from the stream assuming it is a cyclic reference to
 an object previously read. Return the actual object previously read.
     * 
     * @return the object previously read from the stream
     * 
     * @throws IOException
     *             If an IO exception happened when reading the class
     *             descriptor.
     * @throws InvalidObjectException
     *             If the cyclic reference is not valid.
     */
    Object readCyclicReference() throws InvalidObjectException,
            IOException {
        return registeredObjectRead(readNewHandle()); // Integers too big for integer cache.
    }

    /**
     * Reads a double (64 bit) from the source stream.
     * 
     * @return the double value read from the source stream.
     * @throws EOFException
     *             if the end of the input is reached before the read
     *             request can be satisfied.
     * @throws IOException
     *             if an error occurs while reading from the source stream.
     */
    @Override
    public double readDouble() throws IOException {
	return primitiveTypes.readDouble();
    }

    /**
     * Read the tc item assuming it is an exception. The exception is not a
     * regular instance in the object graph, but the exception instance that
     * happened (if any) when dumping the original object graph. The set of seen
     * objects will be reset just before and just after loading this exception
     * object.
     * <p>
     * When exceptions are found normally in the object graph, they are loaded
     * as a regular object, and not by this method. In that case, the set of
     * "known objects" is not reset.
     * 
     * @return the exception read
     * 
     * @throws IOException
     *             If an IO exception happened when reading the exception
     *             object.
     * @throws ClassNotFoundException
     *             If a class could not be found when reading the object graph
     *             for the exception
     * @throws OptionalDataException
     *             If optional data could not be found when reading the
     *             exception graph
     * @throws WriteAbortedException
     *             If another exception was caused when dumping this exception
     */
    private Exception readException() throws WriteAbortedException,
            OptionalDataException, ClassNotFoundException, IOException {

        resetSeenObjects();

        // Now we read the Throwable object that was saved
        // WARNING - the grammar says it is a Throwable, but the
        // WriteAbortedException constructor takes an Exception. So, we read an
        // Exception from the stream
        Exception exc = (Exception) readObject(false, Exception.class);

        // We reset the receiver's state (the grammar has "reset" in normal
        // font)
        resetSeenObjects();
        return exc;
    }



    /**
     * Reads the persistent fields of the object that is currently being read
     * from the source stream. The values read are stored in a GetField object
     * that provides access to the persistent fields. This GetField object is
     * then returned.
     * 
     * @return the GetField object from which persistent fields can be accessed
     *         by name.
     * @throws ClassNotFoundException
     *             if the class of an object being deserialized can not be
     *             found.
     * @throws IOException
     *             if an error occurs while reading from this stream.
     * @throws NotActiveException
     *             if this stream is currently not reading an object.
     */
    @Override
    public GetField readFields() throws IOException, ClassNotFoundException,
            NotActiveException {
	// We can't be called from just anywhere. There are rules.
	if (currentObject == null) {
	    throw new NotActiveException();
	}
//	System.out.println("readFields called");
	EmulatedFieldsForLoading result 
	    = new EmulatedFieldsForLoading(currentClass.getDeserializedClass());
	readFieldValues(result);
	return result;
    }
    
    private GetField readFields(ObjectStreamClass deserializedClassDescriptor)
	    throws IOException, ClassNotFoundException 
    {
	EmulatedFieldsForLoading result 
		= new EmulatedFieldsForLoading(deserializedClassDescriptor);
	readFieldValues(result);
	return result;
    }

    /**
     * Reads a collection of field values for the emulated fields
     * {@code emulatedFields}
     * 
     * @param emulatedFields
     *            an {@code EmulatedFieldsForLoading}, concrete subclass
     *            of {@code GetField}
     * 
     * @throws IOException
     *             If an IO exception happened when reading the field values.
     * @throws InvalidClassException
     *             If an incompatible type is being assigned to an emulated
     *             field.
     * @throws OptionalDataException
     *             If optional data could not be found when reading the
     *             exception graph
     * 
     * @see #readFields
     * @see #readObject()
     */
    private void readFieldValues(EmulatedFieldsForLoading emulatedFields)
            throws OptionalDataException, InvalidClassException, IOException {
        ObjectSlot[] slots = emulatedFields.emulatedFields()
                .slots();
        for (ObjectSlot element : slots) {
            element.setDefaulted(false);
            Class<?> type = element.getField().getType();
            if (type == Integer.TYPE) {
		element.setIntValue(input.readInt());
            } else if (type == Byte.TYPE) {
                element.setByteValue(input.readByte());
            } else if (type == Character.TYPE) {
                element.setCharValue(input.readChar());
            } else if (type == Short.TYPE) {
                element.setShortValue(input.readShort());
            } else if (type == Boolean.TYPE) {
                element.setBooleanValue(input.readBoolean());
            } else if (type == Long.TYPE) {
                element.setLongValue(input.readLong());
            } else if (type == Float.TYPE) {
                element.setFloatValue(input.readFloat());
            } else if (type == Double.TYPE) {
                element.setDoubleValue(input.readDouble());
            } else {
                // Either array or Object
                try {
                    element.setFieldValue(readObject(false, null));
                } catch (ClassNotFoundException cnf) {
                    // WARNING- Not sure this is the right thing to do. Write
                    // test case.
                    throw new InvalidClassException(cnf.toString());
                } catch (StreamCorruptedException e){
		    StringBuilder b = new StringBuilder(200);
		    b.append("Unable to read field: ");
		    b.append(element.getField().getName());
		    b.append(" of type: ");
		    b.append(type);
		    b.append("\n");
		    b.append("while deserializing class: ");
		    b.append(emulatedFields.getObjectStreamClass().getName());
		    StreamCorruptedException ex = new StreamCorruptedException(b.toString());
		    ex.initCause(e);
		    throw ex;
		} catch (EOFException e){
		    StringBuilder b = new StringBuilder(200);
		    b.append("Unable to read field: ");
		    b.append(element.getField().getName());
		    b.append(" of type: ");
		    b.append(type);
		    b.append("\n");
		    b.append("while deserializing class: ");
		    b.append(emulatedFields.getObjectStreamClass().getName());
		    EOFException ex = new EOFException(b.toString());
		    ex.initCause(e);
		    throw ex;
		}
            }
        }
    }

    /**
     * Reads a collection of field values for the class descriptor
     * {@code classDesc} (an {@code ObjectStreamClass}). The
     * values will be used to set instance fields in object {@code obj}.
     * This is the default mechanism, when emulated fields (an
     * {@code GetField}) are not used. Actual values to load are stored
     * directly into the object {@code obj}.
     * 
     * @param obj
     *            Instance in which the fields will be set.
     * @param classDesc
     *            A class descriptor (an {@code ObjectStreamClass})
     *            defining which fields should be loaded.
     * 
     * @throws IOException
     *             If an IO exception happened when reading the field values.
     * @throws InvalidClassException
     *             If an incompatible type is being assigned to an emulated
     *             field.
     * @throws OptionalDataException
     *             If optional data could not be found when reading the
     *             exception graph
     * @throws ClassNotFoundException
     *             If a class of an object being de-serialized can not be found
     * 
     * @see #readFields
     * @see #readObject()
     */
    private void readFieldValues(Object obj, ObjectStreamClassContainer classDesc)
            throws OptionalDataException, ClassNotFoundException, IOException {
        // Now we must read all fields and assign them to the receiver
//	System.out.println("readFieldValues called");
	ObjectStreamClass lclass = ObjectStreamClass.lookup(classDesc.forClass());
        ObjectStreamField[] dfields = classDesc.getFields();
        dfields = (null == dfields ? new ObjectStreamField[0] : dfields);
        final Class<?> declaringClass = classDesc.forClass();
        if (declaringClass == null && mustResolve) {
            throw new ClassNotFoundException(classDesc.getName());
        }
	

//	System.out.println("Declaring class" + declaringClass);
        for (int i = 0, l = dfields.length; i < l; i++) {
	    final ObjectStreamField dfield = dfields[i];
            // get associated Field 
//            long fieldID = fieldDesc.getFieldID(accessor, declaringClass);
	    Field f = null;
	    boolean exists = false;
	    f = AccessController.doPrivileged(new PrivilegedAction<Field>(){

		@Override
		public Field run() {
		    try {
			return dfield == null || declaringClass == null ?  null : 
				declaringClass.getDeclaredField(dfield.getName());
		    } catch (NoSuchFieldException ex) {
			return null;
		    } catch (SecurityException ex) {
			Logger.getLogger(AtomicMarshalInputStream.class.getName()).log(Level.SEVERE, null, ex);
			return null;
		    }
		}
		
	    });
	    if (f != null) exists = true;
//	    if (fieldDesc != null) System.out.println("Field: " + fieldDesc + " is primitive " + fieldDesc.isPrimitive());
            // Code duplication starts, just because Java is typed
            if (dfield != null && dfield.isPrimitive()) {
               IOException ex = AccessController.doPrivileged(
		       new SetPrimitiveFieldAction(
			       obj,
			       input,
			       f,
			       exists,
			       dfields[i].getTypeCode()
		       )
	       );
	       if (ex != null) throw ex;
            } else {
                // Object type (array included).
                String fieldName = dfield == null ? null :
			dfield.getName();
                boolean setBack = false;
                if (mustResolve && dfield == null) {
                    setBack = true;
                    mustResolve = false;
                }
                Object toSet = null;
		if (dfield != null && !dfield.isPrimitive()){
		    try {
			// Local field descriptor.
			ObjectStreamField streamField = lclass.getField(fieldName);
			// Class type is Object.class for deserialized fields
			// therefore we must use the local descriptor.
			Class fieldType = streamField == null ? null : streamField.getType();
			// However if the local field isn't defined we can't check it.
			if (dfield.isUnshared()) {
			    toSet = readObject(true, fieldType);
			} else {
			    toSet = readObject(false, fieldType);
			}
		    } catch (EOFException e){
			StringBuilder b = new StringBuilder(200);
			b.append("Exception thrown with attemting to read field: ");
			b.append(dfield);
			b.append("\n");
			b.append("Within fields: ");
			b.append(Arrays.asList(dfields));
			b.append("\n");
			b.append("Class name: ");
			b.append(classDesc.getName());
			b.append("\n");
			EOFException ex = new EOFException(b.toString());
			ex.initCause(e);
			throw ex;
		    }
		}
                if (setBack) {
                    mustResolve = true;
                }
                if (dfield != null) {
                    if (toSet != null) {
                        Class<?> fieldType = getFieldClass(obj, fieldName);
                        Class<?> valueType = toSet.getClass();
                        if (fieldType != null) {
			    // Redundant check, but harmless.
                            if (!fieldType.isAssignableFrom(valueType)) {
                                throw new ClassCastException(Messages.getString(
                                    "luni.C0", new String[] { //$NON-NLS-1$
                                    fieldType.toString(), valueType.toString(),
                                            classDesc.getName() + "." //$NON-NLS-1$
                                                    + fieldName }));
                            }
                            try {
				final Field fld = f;
				final Object o = obj;
				final Object toS = toSet;
				if (exists) {
				    AccessController.doPrivileged(new PrivilegedExceptionAction(){
					@Override
					public Object run() throws Exception {
					    fld.setAccessible(true);
					    fld.set(o, toS);
					    return null;
					}
				    });
				}
			    } catch (PrivilegedActionException ex) {
				Logger.getLogger(AtomicMarshalInputStream.class.getName()).log(Level.SEVERE, null, ex);
			    }
                        }
                    }
                }
            }
        }
    }
    
    private static class SetPrimitiveFieldAction implements PrivilegedAction<IOException> {
	private final Object obj;
	private final DataInputStream input;
	private final Field f;
	private final boolean exists;
	private final char typeCode;
	
	SetPrimitiveFieldAction(Object obj, DataInputStream in, Field f, boolean exists, char typeCode){
	    this.input = in;
	    this.f = f;
	    this.exists = exists;
	    this.typeCode = typeCode;
	    this.obj = obj;
	}

	@Override
	public IOException run() {
	    try {
		f.setAccessible(true);
                    switch (typeCode) {
                        case 'B':
                            byte srcByte = input.readByte();
			    if (exists) f.setByte(obj, srcByte);
                            break;
                        case 'C':
                            char srcChar = input.readChar();
                            if (exists) f.setChar(obj, srcChar);
                            break;
                        case 'D':
                            double srcDouble = input.readDouble();
			    if (exists) f.setDouble(obj, srcDouble);
                            break;
                        case 'F':
                            float srcFloat = input.readFloat();
                            if (exists) f.setFloat(obj, srcFloat);
                            break;
                        case 'I':
                            int srcInt = input.readInt();
                            if (exists) f.setInt(obj, srcInt);
                            break;
                        case 'J':
                            long srcLong = input.readLong();
                            if (exists) f.setLong(obj, srcLong);
                            break;
                        case 'S':
                            short srcShort = input.readShort();
                            if (exists) f.setShort(obj, srcShort);
                            break;
                        case 'Z':
                            boolean srcBoolean = input.readBoolean();
                            if (exists) f.setBoolean(obj, srcBoolean);
                            break;
                        default:
                            throw new StreamCorruptedException(Messages.getString(
                                    "luni.BF", typeCode)); //$NON-NLS-1$
                    }
                } catch (NoSuchFieldError err) {
		    Logger.getLogger(AtomicMarshalInputStream.class.getName()).log(Level.SEVERE, null, err);
                } catch (IllegalArgumentException ex) {
		    Logger.getLogger(AtomicMarshalInputStream.class.getName()).log(Level.SEVERE, null, ex);
		} catch (IllegalAccessException ex) {
		    Logger.getLogger(AtomicMarshalInputStream.class.getName()).log(Level.SEVERE, null, ex);
		} catch (IOException ex) {
		    return ex;
	    }
	    return null;
	}
	
    }

    private static Class<?> getFieldClass(final Object obj,
                                          final String fieldName) {
        return AccessController.doPrivileged(new PrivilegedAction<Class<?>>() {
		@Override
                public Class<?> run() {
                    Class<?> objClass = obj.getClass();
                    while (objClass != null) {
                        try {
                            Class<?> fc =
                                objClass.getDeclaredField(fieldName).getType();
                            return fc;
                        } catch (NoSuchFieldException e) {
                            // Ignored
                        }
                        objClass = objClass.getSuperclass();
                    }
                    return null;
                }
            });
    }

    /**
     * Reads a float (32 bit) from the source stream.
     * 
     * @return the float value read from the source stream.
     * @throws EOFException
     *             if the end of the input is reached before the read
     *             request can be satisfied.
     * @throws IOException
     *             if an error occurs while reading from the source stream.
     */
    @Override
    public float readFloat() throws IOException {
	return primitiveTypes.readFloat();
    }

    /**
     * Reads bytes from the source stream into the byte array {@code buffer}.
     * This method will block until {@code buffer.length} bytes have been read.
     * 
     * @param buffer
     *            the array in which to store the bytes read.
     * @throws EOFException
     *             if the end of the input is reached before the read
     *             request can be satisfied.
     * @throws IOException
     *             if an error occurs while reading from the source stream.
     */
    @Override
    public void readFully(byte[] buffer) throws IOException {
	primitiveTypes.readFully(buffer);
    }

    /**
     * Reads bytes from the source stream into the byte array {@code buffer}.
     * This method will block until {@code length} number of bytes have been
     * read.
     * 
     * @param buffer
     *            the byte array in which to store the bytes read.
     * @param offset
     *            the initial position in {@code buffer} to store the bytes
     *            read from the source stream.
     * @param length
     *            the maximum number of bytes to store in {@code buffer}.
     * @throws EOFException
     *             if the end of the input is reached before the read
     *             request can be satisfied.
     * @throws IOException
     *             if an error occurs while reading from the source stream.
     */
    @Override
    public void readFully(byte[] buffer, int offset, int length)
            throws IOException {
	primitiveTypes.readFully(buffer, offset, length);
    }

    /**
     * Walks the hierarchy of classes described by class descriptor
     * {@code classDesc} and reads the field values corresponding to
     * fields declared by the corresponding class descriptor. The instance to
     * store field values into is {@code object}. If the class
     * (corresponding to class descriptor {@code classDesc}) defines
     * private instance method {@code readObject} it will be used to load
     * field values.
     * 
     * @param object
     *            Instance into which stored field values loaded.
     * @param classDesc
     *            A class descriptor (an {@code ObjectStreamClass})
     *            defining which fields should be loaded.
     * 
     * @throws IOException
     *             If an IO exception happened when reading the field values in
     *             the hierarchy.
     * @throws ClassNotFoundException
     *             If a class for one of the field types could not be found
     * @throws NotActiveException
     *             If {@code defaultReadObject} is called from the wrong
     *             context.
     * 
     * @see #defaultReadObject
     * @see #readObject()
     */
    private void readHierarchy(Object object, ObjectStreamClassContainer classDesc)
            throws IOException, ClassNotFoundException, NotActiveException {
        // We can't be called from just anywhere. There are rules.
        if (object == null && mustResolve) {
            throw new NotActiveException();
        }

        ArrayList<ObjectStreamClassContainer> streamClassList 
		= new ArrayList<ObjectStreamClassContainer>(12);
        ObjectStreamClassContainer nextStreamClass = classDesc;
        while (nextStreamClass != null) {
            streamClassList.add(0, nextStreamClass);
	    nextStreamClass = nextStreamClass.getSuperDesc();
//	    if (nextStreamClass == null){
//		nextStreamClass = ObjectStreamClass.lookup(superc); // Only lookup Serializable
//            nextStreamClass = nextStreamClass.getSuperclass();
//	    }
        }
        if (object == null) {
            Iterator<ObjectStreamClassContainer> streamIt = streamClassList.iterator();
            while (streamIt.hasNext()) {
                ObjectStreamClassContainer streamClass = streamIt.next();
                readObjectForClass(null, streamClass);
            }
        } else {
            ArrayList<Class<?>> classList = new ArrayList<Class<?>>(32);
            Class<?> nextClass = object.getClass();
            while (nextClass != null) {
                Class<?> testClass = nextClass.getSuperclass();
                if (testClass != null) {
                    classList.add(0, nextClass);
                }
                nextClass = testClass;
            }
//	    System.out.println("Class list: " + classList);
//	    System.out.println("Stream class list: " + streamClassList);
            int lastIndex = 0;
            for (int i = 0; i < classList.size(); i++) {
                Class<?> superclass = classList.get(i);
                int index = findStreamSuperclass(superclass, streamClassList,
                        lastIndex);
//		System.out.println("super class index  " + index);
		
                if (index == -1) {
                    readObjectNoData(object, superclass, ObjectStreamClass.lookupAny(superclass));
                } else {
                    for (int j = lastIndex; j <= index; j++) {
                        readObjectForClass(object, streamClassList.get(j));
                    }
                    lastIndex = index + 1;
                }
            }
        }
    }

    private int findStreamSuperclass(Class<?> cl,
            List<ObjectStreamClassContainer> classList, int lastIndex) {
        ObjectStreamClassContainer objCl;
        String forName;

        for (int i = lastIndex; i < classList.size(); i++) {
            objCl = classList.get(i);
            forName = objCl.forClass().getName();

            if (objCl.getName().equals(forName)) {
                if (cl.getName().equals(objCl.getName())) {
                    return i;
                }
            } else {
                // there was a class replacement
                if (cl.getName().equals(forName)) {
                    return i;
                }
            }
        }
        return -1;
    }

    private void readObjectNoData(Object object, Class<?> cl, ObjectStreamClass classDesc)
            throws ObjectStreamException {
        if (!Serializable.class.isAssignableFrom(cl)) {
            return;
        }
	
//        if (classDesc.hasMethodReadObjectNoData()){
	final Method readMethod 
		= getMethod(cl, "readObjectNoData", (Class []) null);
//		    = classDesc.getMethodReadObjectNoData();
	if (readMethod != null){
            try {
                readMethod.invoke(object, new Object[0]);
            } catch (InvocationTargetException e) {
                Throwable ex = e.getTargetException();
                if (ex instanceof RuntimeException) {
                    throw (RuntimeException) ex;
                } else if (ex instanceof Error) {
                    throw (Error) ex;
                }
                throw (ObjectStreamException) ex;
            } catch (IllegalAccessException e) {
                throw new RuntimeException(e.toString());
            }
        }

    }
    
    private Method getMethod(final Class type, final String name, final Class[] paramTypes){
	if (type == null) return null;
	return AccessController.doPrivileged(new PrivilegedAction<Method>(){
	    @Override
	    public Method run() {
		Method m = null;
		try {
		    m = type.getDeclaredMethod(name, paramTypes);
		    m.setAccessible(true);
		} catch (NoSuchMethodException ex) {
		    Logger.getLogger(AtomicMarshalInputStream.class.getName()).log(Level.FINEST, null, ex);
		} catch (SecurityException ex) {
		    Logger.getLogger(AtomicMarshalInputStream.class.getName()).log(Level.FINEST, null, ex);
		}
		return m;
	    }
	});
    }

    private void readObjectForClass(Object object, ObjectStreamClassContainer classDesc)
            throws IOException, ClassNotFoundException, NotActiveException {
        // Have to do this before calling defaultReadObject or anything that
        // calls defaultReadObject
//	System.out.println("Object: " + object);
//	System.out.println("Class: " + classDesc.forClass());
        currentObject = object;
        currentClass = classDesc;
	Class<?> targetClass = classDesc.forClass();
	Class [] paramTypes = {ObjectOutputStream.class};
        final Method readMethod;
        if (targetClass == null || !mustResolve) {
            readMethod = null;
        } else {
	    paramTypes[0] = ObjectInputStream.class;
            readMethod = getMethod(targetClass, "readObject", paramTypes);
//		    = classDesc.getMethodReadObject();
        }
        try {
            if (readMethod != null) {
                // We have to be able to fetch its value, even if it is private
//                AccessController.doPrivileged(new PriviAction<Object>(
//                        readMethod));
                try {
//		    System.out.println("Invoking readObject");
                    readMethod.invoke(object, new Object[] { this });
//		    System.out.println("Object read: " + object);
                } catch (InvocationTargetException e) {
                    Throwable ex = e.getTargetException();
                    if (ex instanceof ClassNotFoundException) {
                        throw (ClassNotFoundException) ex;
                    } else if (ex instanceof RuntimeException) {
                        throw (RuntimeException) ex;
                    } else if (ex instanceof Error) {
                        throw (Error) ex;
                    }
                    throw (IOException) ex;
                } catch (IllegalAccessException e) {
                    throw new RuntimeException(e.toString());
                }
            } else {
		//defaultReadObject()
                readFieldValues(object, classDesc);
            }
	    // Normally we only discard data for object's with writeObject
	    // methods, however we
            if (classDesc.hasWriteObjectData()) {
                discardData();
            }
        } finally {
            // Cleanup, needs to run always so that we can later detect invalid
            // calls to defaultReadObject
            currentObject = null; // We did not set this, so we do not need to
            // clean it
            currentClass = null;
        }
    }

    /**
     * Reads an integer (32 bit) from the source stream.
     * 
     * @return the integer value read from the source stream.
     * @throws EOFException
     *             if the end of the input is reached before the read
     *             request can be satisfied.
     * @throws IOException
     *             if an error occurs while reading from the source stream.
     */
    @Override
    public int readInt() throws IOException {
	return primitiveTypes.readInt();
    }

    /**
     * Reads the tc line from the source stream. Lines are terminated by
     * {@code '\r'}, {@code '\n'}, {@code "\r\n"} or an {@code EOF}.
     * 
     * @return the string read from the source stream.
     * @throws IOException
     *             if an error occurs while reading from the source stream.
     * @deprecated Use {@link BufferedReader}
     */
    @Deprecated
    @Override
    public String readLine() throws IOException {
	return primitiveTypes.readLine();
    }

    /**
     * Reads a long (64 bit) from the source stream.
     * 
     * @return the long value read from the source stream.
     * @throws EOFException
     *             if the end of the input is reached before the read
     *             request can be satisfied.
     * @throws IOException
     *             if an error occurs while reading from the source stream.
     */
    @Override
    public long readLong() throws IOException {
	return primitiveTypes.readLong();
    }
    
    static int getInt(byte[] b, int off) {
        return ((b[off + 3] & 0xFF)      ) +
               ((b[off + 2] & 0xFF) <<  8) +
               ((b[off + 1] & 0xFF) << 16) +
               ((b[off    ]       ) << 24);
    }
    
    /**
     * Read a new array from the receiver. It is assumed the array has not been
     * read yet (not a cyclic reference). Return the array read.
     * 
     * @param unshared
     *            read the object unshared
     * @return the array read
     * 
     * @throws IOException
     *             If an IO exception happened when reading the array.
     * @throws ClassNotFoundException
     *             If a class for one of the objects could not be found
     * @throws OptionalDataException
     *             If optional data could not be found when reading the array.
     */
    private Object readNewArray(boolean unshared, Class type) throws OptionalDataException,
            ClassNotFoundException, IOException {
        ObjectStreamClassContainer classDesc = readClassDesc();
//	System.out.println("readNewArray");
        if (classDesc == null) {
            throw new InvalidClassException(Messages.getString("luni.C1")); //$NON-NLS-1$
        }
	if (type != null && !type.isAssignableFrom(classDesc.forClass())) 
	    throw new InvalidObjectException(
		    "expecting " + type + "in stream, but got " 
			    + classDesc.forClass()
	    );

        int newHandle = nextHandle();
	// Array size
	int size = input.readInt();
//	System.out.println("array size: " + size);
	
	if (size > arrayLenAllowedRemain) {
	    try {
		close();
	    } catch (IOException e){} // Ignore
	    throw new IOException("Attempt to deserialize an array with length exceeding 65535, length requested: " + size);
	}
	arrayLenAllowedRemain = arrayLenAllowedRemain - size;
        Class<?> arrayClass = classDesc.forClass();
        Class<?> componentType = arrayClass.getComponentType();
        Object result = Array.newInstance(componentType, size);

        registerObjectRead(result, newHandle, unshared);

        // Now we have code duplication just because Java is typed. We have to
        // read N elements and assign to array positions, but we must typecast
        // the array first, and also call different methods depending on the
        // elements.
        if (componentType.isPrimitive()) {
            if (componentType == Integer.TYPE) {
                int[] intArray = (int[]) result;
                for (int i = 0; i < size; i++) {
                    intArray[i] = input.readInt();
                }
            } else if (componentType == Byte.TYPE) {
                byte[] byteArray = (byte[]) result;
                input.readFully(byteArray, 0, size);
            } else if (componentType == Character.TYPE) {
                char[] charArray = (char[]) result;
                for (int i = 0; i < size; i++) {
                    charArray[i] = input.readChar();
                }
            } else if (componentType == Short.TYPE) {
                short[] shortArray = (short[]) result;
                for (int i = 0; i < size; i++) {
                    shortArray[i] = input.readShort();
                }
            } else if (componentType == Boolean.TYPE) {
                boolean[] booleanArray = (boolean[]) result;
                for (int i = 0; i < size; i++) {
                    booleanArray[i] = input.readBoolean();
                }
            } else if (componentType == Long.TYPE) {
                long[] longArray = (long[]) result;
                for (int i = 0; i < size; i++) {
                    longArray[i] = input.readLong();
                }
            } else if (componentType == Float.TYPE) {
                float[] floatArray = (float[]) result;
                for (int i = 0; i < size; i++) {
                    floatArray[i] = input.readFloat();
                }
            } else if (componentType == Double.TYPE) {
                double[] doubleArray = (double[]) result;
                for (int i = 0; i < size; i++) {
                    doubleArray[i] = input.readDouble();
                }
            } else {
                throw new ClassNotFoundException(Messages.getString(
                        "luni.C2", classDesc.getName())); //$NON-NLS-1$
            }
        } else {
	    try {
		// Array of Objects
		Object[] objectArray = (Object[]) result;
		for (int i = 0; i < size; i++) {
		    // TODO: This place is the opportunity for enhancement
		    //      We can implement writing elements through fast-path,
		    //      without setting up the context (see readObject()) for 
		    //      each element with public API
		    objectArray[i] = readObject(false, null);
		}
	    } catch (EOFException e){
		EOFException ex = new EOFException(
			"Unable to deserialize an instanceof " 
				+componentType + " as an array element");
		ex.initCause(e);
		throw ex;
	    }
        }
        if (enableResolve) {
            result = resolveObject(result);
            registerObjectRead(result, newHandle, false);
        }
        return result;
    }

    /**
     * Reads a new class from the receiver. It is assumed the class has not been
     * read yet (not a cyclic reference). Return the class read.
     * 
     * @param unshared
     *            read the object unshared
     * @return The {@code java.lang.Class} read from the stream.
     * 
     * @throws IOException
     *             If an IO exception happened when reading the class.
     * @throws ClassNotFoundException
     *             If a class for one of the objects could not be found
     */
    private Class<?> readNewClass(boolean unshared)
            throws ClassNotFoundException, IOException {
        ObjectStreamClassContainer classDesc = readClassDesc();
        if (classDesc != null) {
            Class<?> localClass = classDesc.forClass();
            if (localClass != null) {
                registerObjectRead(localClass, nextHandle(), unshared);
            }
            return localClass;
        }
        throw new InvalidClassException(Messages.getString("luni.C1")); //$NON-NLS-1$
    }

    /*
     * read class type for Enum, note there's difference between enum and normal
     * classes
     */
    private ObjectStreamClassContainer readEnumDesc() throws IOException,
            ClassNotFoundException {
        byte tc = nextTC();
        switch (tc) {
            case TC_CLASSDESC:
//		System.out.println("TC_CLASSDESC");
                return readEnumDescInternal();
            case TC_REFERENCE:
//		System.out.println("TC_REFERENCE");
                return (ObjectStreamClassContainer) readCyclicReference();
            case TC_NULL:
//		System.out.println("TC_NULL");
                return null;
            default:
                throw new StreamCorruptedException(Messages.getString(
                        "luni.BC", Integer.toHexString(tc & 0xff))); //$NON-NLS-1$
        }
    }

    private ObjectStreamClassContainer readEnumDescInternal() throws IOException,
            ClassNotFoundException {
        ObjectStreamClass classDesc;
        primitiveData = input;
        int oldHandle = descriptorHandle;
        descriptorHandle = nextHandle();
        classDesc = readClassDescriptor();
	ObjectStreamClassContainer classDescContainer = (ObjectStreamClassContainer) registeredObjectRead(descriptorHandle);
	registerObjectRead(classDescContainer, descriptorHandle, false); // re read in case overridden.
        descriptorHandle = oldHandle;
        primitiveData = emptyStream;
	Class<?> c = objectInputStreamCompat ? superResolveClass(classDesc) : resolveClass(classDesc);
	c = replaceClass(c);
	ObjectStreamClass localClass = ObjectStreamClass.lookup(c);
	classDescContainer.setClass(c);
	classDescContainer.setLocalClassDescriptor(localClass);
//        classDesc.setClass(resolveClass(classDesc));
        // Consume unread class annotation data and TC_ENDBLOCKDATA
        discardData();
        ObjectStreamClassContainer superClass = readClassDesc();
        checkedSetSuperClassDesc(classDescContainer, superClass);
        // Check SUIDs, note all SUID for Enum is 0L
        if (0L != classDesc.getSerialVersionUID()
                || 0L != superClass.getSerialVersionUID()) {
            throw new InvalidClassException(superClass.getName(), Messages
                    .getString("luni.C3", superClass, //$NON-NLS-1$
                            superClass));
        }
//        byte tc = nextTC();
//        // discard TC_ENDBLOCKDATA after classDesc if any
//        if (tc == TC_ENDBLOCKDATA) {
//	    System.out.println("TC_ENDBLOCKDATA");
//            // read tc parent class. For enum, it may be null
//	    readClassDesc();
//            superClass.setSuperclass(readClassDesc());
//        } else {
//            // not TC_ENDBLOCKDATA, push back for tc read
//            pushbackTC(tc);
//        }
        return classDescContainer;
    }

    @SuppressWarnings("unchecked")// For the Enum.valueOf call
    private Object readEnum(boolean unshared) throws OptionalDataException,
            ClassNotFoundException, IOException {
        // read classdesc for Enum first
        ObjectStreamClassContainer classDesc = readEnumDesc();
        int newHandle = nextHandle();
        // read name after class desc
        String name;
        byte tc = nextTC();
        switch (tc) {
            case TC_REFERENCE:
//		System.out.println("TC_REFERENCE");
                if (unshared) {
                    readNewHandle();
                    throw new InvalidObjectException(Messages.getString("luni.BE")); //$NON-NLS-1$
                }
                name = (String) readCyclicReference();
                break;
            case TC_STRING:
//		System.out.println("TC_STRING");
                name = (String) readNewString(unshared);
                break;
            default:
                throw new StreamCorruptedException(Messages.getString("luni.BC"));//$NON-NLS-1$
        }

        Enum<?> result = Enum.valueOf((Class) classDesc.forClass(), name);
        registerObjectRead(result, newHandle, unshared);

        return result;
    }

    /**
     * Reads a new class descriptor from the receiver. It is assumed the class
     * descriptor has not been read yet (not a cyclic reference). Return the
     * class descriptor read.
     * 
     * @param unshared
     *            read the object unshared
     * @return The {@code ObjectStreamClass} read from the stream.
     * 
     * @throws IOException
     *             If an IO exception happened when reading the class
     *             descriptor.
     * @throws ClassNotFoundException
     *             If a class for one of the objects could not be found
     */
    private ObjectStreamClassContainer readNewClassDesc(boolean unshared)
            throws ClassNotFoundException, IOException {
        // So read...() methods can be used by
        // subclasses during readClassDescriptor()
        primitiveData = input;
//        Integer oldHandle = descriptorHandle;
//        descriptorHandle = nextHandle();
        ObjectStreamClassContainer newClassDesc = readStreamClassDescriptor(unshared);
//	newClassDesc.handle=descriptorHandle;
//	registerObjectRead(newClassDesc, descriptorHandle, unshared);
//        descriptorHandle = oldHandle;
        primitiveData = emptyStream;

        // We need to map classDesc to class.
        try {
//            newClassDesc.setClass(resolveClass(newClassDesc));
	    Class<?> c = objectInputStreamCompat ? 
			    superResolveClass(newClassDesc.getDeserializedClass()) 
			    :resolveClass(newClassDesc.getDeserializedClass());
	    c = replaceClass(c);
            // Check SUIDs & base name of the class
            verifyAndInit(newClassDesc,c);
	    
        } catch (ClassNotFoundException e) {
            if (mustResolve) {
                throw e;
                // Just continue, the class may not be required
            }
        }
	// Consume unread class annotation data and TC_ENDBLOCKDATA
        discardData();
        checkedSetSuperClassDesc(newClassDesc, readClassDesc());
      
        return newClassDesc;
    }
    
    

    /**
     * Reads a new proxy class descriptor from the receiver. It is assumed the
     * proxy class descriptor has not been read yet (not a cyclic reference).
     * Return the proxy class descriptor read.
     * 
     * @return The {@code Class} read from the stream.
     * 
     * @throws IOException
     *             If an IO exception happened when reading the class
     *             descriptor.
     * @throws ClassNotFoundException
     *             If a class for one of the objects could not be found
     */
    private ObjectStreamClassContainer readNewProxyClassDesc() throws ClassNotFoundException,
            IOException {
	int handle = nextHandle();
	ObjectStreamClassContainer streamClassContainer 
		= new ObjectStreamClassContainer(null, null, null, handle, true );
	registerObjectRead(streamClassContainer, handle, false);
        int count = input.readInt();
	if (count > Byte.MAX_VALUE) throw new ClassNotFoundException(
	    "Smells like a denial of service attack, requesting to create a proxy with many interfaces: "
	+ count);
        String[] interfaceNames = new String[count];
        for (int i = 0; i < count; i++) {
            interfaceNames[i] = input.readUTF();
        }
        
	Class<?> proxyClass = objectInputStreamCompat ? 
		superResolveProxyClass(interfaceNames) 
		: resolveProxyClass(interfaceNames);
        ObjectStreamClass streamClass = ObjectStreamClass.lookup(proxyClass);
	streamClassContainer.setLocalClassDescriptor(streamClass);
        // Consume unread class annotation data and TC_ENDBLOCKDATA
        discardData();
	checkedSetSuperClassDesc(streamClassContainer, readClassDesc());
	return streamClassContainer;
    }
    
    private ObjectStreamClassContainer readStreamClassDescriptor(boolean unshared) throws IOException,
	    ClassNotFoundException {
	ObjectStreamClassInformation info = new ObjectStreamClassInformation();
	info.read(this);
        ObjectStreamClass newClassDesc;
        

        /*
         * We must register the class descriptor before reading field
         * descriptors. If called outside of readObject, the descriptorHandle
         * might be null.
         */
//        descriptorHandle = (null == descriptorHandle ? nextHandle() : descriptorHandle);
	int handle = nextHandle();
	ObjectStreamClassContainer classDescriptorContainer = new ObjectStreamClassContainer(null, null, info, handle, false );
        registerObjectRead(classDescriptorContainer, handle, unshared);
	info.readFields(this);
	newClassDesc = ObjectStreamClassInformation.convert(info);
	classDescriptorContainer.setDeserializedClass(newClassDesc);
        return classDescriptorContainer;
    }

    /**
     * Reads a class descriptor from the source stream.
     * 
     * @return the class descriptor read from the source stream.
     * @throws ClassNotFoundException
     *             if a class for one of the objects cannot be found.
     * @throws IOException
     *             if an error occurs while reading from the source stream.
     */
    @Override
    protected final ObjectStreamClass readClassDescriptor() throws IOException,
            ClassNotFoundException {
	ObjectStreamClassInformation info = new ObjectStreamClassInformation();
	info.read(this);
        ObjectStreamClass newClassDesc;
        

        /*
         * We must register the class descriptor before reading field
         * descriptors. If called outside of readObject, the descriptorHandle
         * might be null.
         */
////        descriptorHandle = (null == descriptorHandle ? nextHandle() : descriptorHandle);
	ObjectStreamClassContainer container = new ObjectStreamClassContainer(null, null, info, descriptorHandle, false);
        registerObjectRead(container, descriptorHandle, false);
	info.readFields(this);
	newClassDesc = ObjectStreamClassInformation.convert(info);
	container.setDeserializedClass(newClassDesc);
        return newClassDesc;
    }
    
    protected Class replaceClass(Class c){
	return c;
    }
    
    protected Object replaceObject(Object o) throws IOException, ClassNotFoundException{
	return o;
    }
    
    /**
     * Write a new handle describing a cyclic reference from the stream.
     * 
     * @return the handle read
     * 
     * @throws IOException
     *             If an IO exception happened when reading the handle
     */
    int readNewHandle() throws IOException {
        return input.readInt();
    }

    private Class<?> resolveConstructorClass(Class<?> objectClass, boolean wasSerializable, boolean wasExternalizable)
        throws OptionalDataException, ClassNotFoundException, IOException {

            // The class of the instance may not be the same as the class of the
            // constructor to run
            // This is the constructor to run if Externalizable
            Class<?> constructorClass = objectClass;

            // WARNING - What if the object is serializable and externalizable ?
            // Is that possible ?
            if (wasSerializable) {
                // Now we must run the constructor of the class just above the
                // one that implements Serializable so that slots that were not
                // dumped can be initialized properly
                while (constructorClass != null
			&& Serializable.class.isAssignableFrom(constructorClass)
			) {
                    constructorClass = constructorClass.getSuperclass();
                }
            }

            // Fetch the empty constructor, or null if none.
            Constructor<?> constructor = null;
            if (constructorClass != null) {
                try {
                    constructor = constructorClass
                            .getDeclaredConstructor(EMPTY_CONSTRUCTOR_PARAM_TYPES);
                } catch (NoSuchMethodException nsmEx) {
                    // Ignored
                }
            }

            // Has to have an empty constructor
            if (constructor == null) {
                throw new InvalidClassException(constructorClass.getName(), Messages
                        .getString("luni.C4")); //$NON-NLS-1$
            }

            int constructorModifiers = constructor.getModifiers();

            // Now we must check if the empty constructor is visible to the
            // instantiation class
            if (Modifier.isPrivate(constructorModifiers)
                    || (wasExternalizable && !Modifier
                            .isPublic(constructorModifiers))) {
                throw new InvalidClassException(constructorClass.getName(), Messages
                        .getString("luni.C4")); //$NON-NLS-1$
            }

            // We know we are testing from a subclass, so the only other case
            // where the visibility is not allowed is when the constructor has
            // default visibility and the instantiation class is in a different
            // package than the constructor class
            if (!Modifier.isPublic(constructorModifiers)
                    && !Modifier.isProtected(constructorModifiers)) {
                // Not public, not private and not protected...means default
                // visibility. Check if same package
                if (!inSamePackage(constructorClass, objectClass)) {
                    throw new InvalidClassException(constructorClass.getName(),
                            Messages.getString("luni.C4")); //$NON-NLS-1$
                }
            }

            return constructorClass;
    }

    /**
     * Read a new object from the stream. It is assumed the object has not been
     * loaded yet (not a cyclic reference). Return the object read.
     * 
     * If the object implements <code>Externalizable</code> its
     * <code>readExternal</code> is called. Otherwise, all fields described by
     * the class hierarchy are loaded. Each class can define how its declared
     * instance fields are loaded by defining a private method
     * <code>readObject</code>
     * 
     * @param unshared
     *            read the object unshared
     * @param discard 
     *		  discard the object unless it is an instance of AtomicSerial
     *		  or Externalizable.
     * @return the object read
     * 
     * @throws IOException
     *             If an IO exception happened when reading the object.
     * @throws OptionalDataException
     *             If optional data could not be found when reading the object
     *             graph
     * @throws ClassNotFoundException
     *             If a class for one of the objects could not be found
     */
    private Object readNewObject(boolean unshared, boolean discard, Class type)
            throws OptionalDataException, ClassNotFoundException, IOException {
        ObjectStreamClassContainer classDesc = readClassDesc();
	
        if (classDesc == null) {
            throw new InvalidClassException(Messages.getString("luni.C1")); //$NON-NLS-1$
        }
	if (type != null){
	    Class c = classDesc.forClass();
	    if (!type.isAssignableFrom(c)){
		if (classDesc.hasMethodReadResolve() && 
		    c.isAnnotationPresent(Serializer.class)) 
		{ 
		    if(!type.isAssignableFrom(
			    ((Serializer)c.getAnnotation(
				    Serializer.class)).replaceObType()))
		    {
			throw new InvalidObjectException(
			    "expecting " + type + " in stream, but got " 
					+ classDesc.forClass()
			);
		    }
		} else {
		    throw new InvalidObjectException(
			"expecting " + type + " in stream, but got " 
				    + classDesc.forClass()
		    );
		}
	    }
	}
        int newHandle = nextHandle();

        // Note that these values come from the Stream, and in fact it could be
        // that the classes have been changed so that the info below now
        // conflicts with the newer class
	boolean wasExternalizable = classDesc.wasExternalizable();
	boolean wasSerializable = classDesc.wasSerializable();
	boolean atomicOrDiscard = true;
	
        // Maybe we should cache the values above in classDesc ? It may be the
        // case that when reading classDesc we may need to read more stuff
        // depending on the values above
        Class<?> objectClass = classDesc.forClass();
        Object result = null, registeredResult = null;
        if (objectClass != null) {
	    if (objectClass.isAnnotationPresent(AtomicSerial.class)){
		classDesc.deSerializationPermitted(ATOMIC);
		registerObjectRead(null, newHandle, unshared);
		result = instantiateAtomicSerialOrDiscard(classDesc, false);
		registerObjectRead(result, newHandle, unshared);
		registeredResult = result;
	    } else if (objectClass.isAnnotationPresent(AtomicExternal.class)){
		classDesc.deSerializationPermitted(ATOMIC);
		registerObjectRead(null, newHandle, unshared);
		result = instantiateAtomicExternal(classDesc);
		registerObjectRead(result, newHandle, unshared);
		registeredResult = result;
	    } else if (Proxy.isProxyClass(objectClass)){ // Dynamically Generated Proxy
		classDesc.deSerializationPermitted(PROXY);
		registerObjectRead(null, newHandle, unshared);
		result = instantiateProxy(classDesc);
		registerObjectRead(result, newHandle, unshared);
		registeredResult = result;
	    } else if (Externalizable.class.isAssignableFrom(objectClass)){
		try {
		    atomicOrDiscard = false;
		    classDesc.deSerializationPermitted(EXTERNALIZABLE); // Permission check
		    result = objectClass.newInstance();
		    registerObjectRead(null, newHandle, unshared);
		    registeredResult = result;
		} catch (InstantiationException ex) {
		    Logger.getLogger(AtomicMarshalInputStream.class.getName()).log(Level.SEVERE, null, ex);
		} catch (IllegalAccessException ex) {
		    Logger.getLogger(AtomicMarshalInputStream.class.getName()).log(Level.SEVERE, null, ex);
		}
	    } else if (discard){
		registerObjectRead(Reference.DISCARDED, newHandle, unshared);
		instantiateAtomicSerialOrDiscard(classDesc, discard);
		return null;
	    } else if (Serializable.class.isAssignableFrom(objectClass)) {
		// Serializable not fully supported, this retreived the 
		// first non serializable superclass zero arg constructor
//            long constructor = osci.constructor;
//            if (constructor == ObjectStreamClass.CONSTRUCTOR_IS_NOT_RESOLVED) {
//                constructor = accessor.getMethodID(resolveConstructorClass(objectClass, wasSerializable, wasExternalizable), null, new Class[0]);
//                classDesc.setConstructor(constructor);
//            }
		if (net.jini.core.entry.Entry.class.isAssignableFrom(objectClass)){
		    classDesc.deSerializationPermitted(ENTRY);
		} else {
		    classDesc.deSerializationPermitted(null);
		}
		try {
		    result = classDesc.newInstance(); // Best effort construction using child class constructor
//		    if (result instanceof Throwable) // Clear stack trace.
//			((Throwable) result).setStackTrace(new StackTraceElement[0]);
		    atomicOrDiscard = false;
		} catch (Exception ex){
		    Logger.getLogger(AtomicMarshalInputStream.class.getName()).log(Level.SEVERE, null, ex);
		    registerObjectRead(Reference.DISCARDED, newHandle, unshared);
		    instantiateAtomicSerialOrDiscard(classDesc, true);
		    return null;
		}
		// Circular links may occur here
		registerObjectRead(null, newHandle, unshared);
		registeredResult = result;
	    } else {
		throw new NotSerializableException(objectClass.getName());
	    }
	    
        } else {
            result = null;
        }
	if (!atomicOrDiscard) {
	    try {
		// This is how we know what to do in defaultReadObject. And it is
		// also used by defaultReadObject to check if it was called from an
		// invalid place. It also allows readExternal to call
		// defaultReadObject and have it work.
		currentObject = result;
		currentClass = classDesc;

		// If Externalizable, just let the object read itself
		if (wasExternalizable) {
//		    System.out.println("Externalizable");
		    boolean blockData = classDesc.hasBlockData();
		    if (blockData) {
			readyPrimitiveData();
		    } else {
			primitiveData = input;
		    }
		    if (mustResolve) {
			Externalizable extern = (Externalizable) result;
			extern.readExternal(this);
		    }
		    if (blockData) {
			// Similar to readHierarchy. Anything not read by
			// readExternal has to be consumed here
			discardData();
		    } 
		    else {
			primitiveData = emptyStream;
		    }
		} else {
		    // If we got here, it is Serializable but not Externalizable.
		    // Walk the hierarchy reading each class' slots
		    readHierarchy(result, classDesc);
		}
	    } finally {
		// Cleanup, needs to run always so that we can later detect invalid
		// calls to defaultReadObject
		currentObject = null;
		currentClass = null;
	    }
	}

        if (objectClass != null && !(result instanceof Reference) && classDesc.hasMethodReadResolve()) {
	    result = classDesc.invokeReadResolve(result);
        }
        // We get here either if class-based replacement was not needed or if it
        // was needed but produced the same object or if it could not be
        // computed.

        // The object to return is the one we instantiated or a replacement for
        // it
        if (result != null) {
            if (enableResolve) result = resolveObject(result);
	    result = replaceObject(result);
        }
	
        // Re register anyway for publication
	registerObjectRead(result, newHandle, unshared);
	if (result instanceof Reference) result = null;
        return result;
    }
    
    
    
    private Object instantiateAtomicExternal(ObjectStreamClassContainer classDesc) throws IOException, ClassNotFoundException{
	Externalizable extern = null;
	boolean blockData = classDesc.hasBlockData();
	if (blockData) {
	    readyPrimitiveData();
	} else {
	    primitiveData = input;
	}
	if (mustResolve) {
	    extern = (Externalizable) 
		AtomicExternal.Factory.instantiate(classDesc.forClass(), this);
	}
	if (blockData) {
	    // Similar to readHierarchy. Anything not read by
	    // readExternal has to be consumed here
	    discardData();
	} else {
	    primitiveData = emptyStream;
	}
	return extern;
    }
    
    private Object instantiateProxy(final ObjectStreamClassContainer classDesc) throws IOException, ClassNotFoundException{
	List<ObjectStreamClassContainer> streamClassList 
		= new ArrayList<ObjectStreamClassContainer>();
        ObjectStreamClassContainer nextStreamClass = classDesc;
        while (nextStreamClass != null) {
            streamClassList.add(0, nextStreamClass);
	    nextStreamClass = nextStreamClass.getSuperDesc();
        }
	int size = streamClassList.size();
	Map<Class,GetField> fields = new HashMap<Class,GetField>(size);
	for (ObjectStreamClassContainer streamClass : streamClassList) {
	    ObjectStreamClass cls = streamClass.getDeserializedClass() != null ? 
		    streamClass.getDeserializedClass() : 
		    streamClass.getLocalClass();
	    GetField field = readFields(cls);
	    // We don't support direct stream access
	    // so we have to discard this data.
	    if (streamClass.hasWriteObjectData()) discardData();
	    Class c = streamClass.forClass();
	    if (c != null) {
		fields.put(c, field);
	    } else {
		throw new ClassNotFoundException(streamClass.getOsci().getFullyQualifiedClassName());
	    }
	}
	GetField proxyFields = fields.get(Proxy.class);
	Object handler = proxyFields.get("h", null); //All dynamically generated proxy's utilise this field.
	if (handler instanceof InvocationHandler){
	    try {
		return classDesc.getConstructor().newInstance(new Object[]{handler});
	    } catch (InstantiationException ex) {
		throw new IOException("unable to instantiate", ex);
	    } catch (IllegalAccessException ex) {
		throw new SecurityException("instantiation denied", ex);
	    } catch (IllegalArgumentException ex) {
		InvalidObjectException e = new InvalidObjectException("invalid argument");
		e.initCause(ex);
		throw e;
	    } catch (InvocationTargetException ex) {
		throw new IOException("Unable to construct", ex.getTargetException());
	    }
	} else {
	    throw new InvalidObjectException("InvocationHander for proxy must be an instance of InvocationHander and non null");
	}
    }
    
    private Object instantiateAtomicSerialOrDiscard(final ObjectStreamClassContainer classDesc, boolean discard) 
	    throws IOException, ClassNotFoundException, InvalidClassException, InvalidObjectException 
    {
//	System.out.println("Instantiate Atomicly");
	// Order descending from superclass
//	if (discard) System.out.println("Warning, discarding object unable to deserialize: " + classDesc);
        List<ObjectStreamClassContainer> streamClassList 
		= new ArrayList<ObjectStreamClassContainer>();
        ObjectStreamClassContainer nextStreamClass = classDesc;
        while (nextStreamClass != null) {
            streamClassList.add(0, nextStreamClass);
	    nextStreamClass = nextStreamClass.getSuperDesc();
        }
	int size = streamClassList.size();
	Map<Class,GetField> fields = new HashMap<Class,GetField>(size);
	Map<Class,ReadObject> readers = new HashMap<Class,ReadObject>(size);
	for (ObjectStreamClassContainer streamClass : streamClassList) {
	    GetField field = readFields(streamClass.getDeserializedClass());
	    // AtomicSerial doesn't support direct stream access
	    // so we have to discard this data.
	    // Discarded data may be referenced elsewhere in the stream
	    // so we do our best to retrieve it.
	    
	    Class c = streamClass.forClass();
	    if (c != null) {
		ReadObject reader = AtomicSerial.Factory.streamReader(c);
		if (reader != null){
		    reader.read(this);
		    readers.put(c, reader);
		} 
		if (streamClass.hasWriteObjectData()) discardData();
		fields.put(c, field);
	    } else {
		if (streamClass.hasWriteObjectData()) discardData();
		throw new ClassNotFoundException(streamClass.getOsci().getFullyQualifiedClassName());
	    }
	}
	GetArg arg = new GetArgImpl(fields, readers, this);
	Object result = discard ? 
		Reference.DISCARDED : 
		Factory.instantiate(classDesc.forClass(),
		    arg
		);
	return result;
    }

    /**
     * Read a string encoded in {@link DataInput modified UTF-8} from the
     * receiver. Return the string read.
     * 
     * @param unshared
     *            read the object unshared
     * @return the string just read.
     * @throws IOException
     *             If an IO exception happened when reading the String.
     */
    Object readNewString(boolean unshared) throws IOException {
        Object result = input.readUTF();
        if (enableResolve) {
            result = resolveObject(result);
        }
	registerObjectRead(result, nextHandle(), unshared);
        return result;
    }

    /**
     * Read a new String in UTF format from the receiver. Return the string
     * read.
     * 
     * @param unshared
     *            read the object unshared
     * @return the string just read.
     * 
     * @throws IOException
     *             If an IO exception happened when reading the String.
     */
    Object readNewLongString(boolean unshared) throws IOException {
        long length = input.readLong();
	if (length > arrayLenAllowedRemain) {
	    try {
		close();
	    } catch (IOException e){} // Ignore
	    throw new IOException("Combined length of arrays too long to allow read of long UTF string");
	}
	arrayLenAllowedRemain = arrayLenAllowedRemain - length;
        Object result 
		= decodeUTF((int) length, input);
        if (enableResolve) {
            result = resolveObject(result);
        }
        registerObjectRead(result, nextHandle(), unshared);
        return result;
    }
    
    private static String decodeUTF(int len, DataInput in) throws IOException {
        byte[] buf = new byte[len];
        char[] out = new char[len];
        in.readFully(buf, 0, len);

        return convertUTF8WithBuf(buf, out, 0, len);
    }
    
    
    private static String convertUTF8WithBuf(byte[] buf, char[] out, int offset,
		    int utfSize) throws UTFDataFormatException {
	    int count = 0, s = 0, a;
	    while (count < utfSize) {
		    if ((out[s] = (char) buf[offset + count++]) < '\u0080')
			    s++;
		    else if (((a = out[s]) & 0xe0) == 0xc0) {
			    if (count >= utfSize)
				    throw new UTFDataFormatException(Messages.getString("luni.D7",
						    count));
			    int b = buf[count++];
			    if ((b & 0xC0) != 0x80)
				    throw new UTFDataFormatException(Messages.getString("luni.D7",
						    (count - 1)));
			    out[s++] = (char) (((a & 0x1F) << 6) | (b & 0x3F));
		    } else if ((a & 0xf0) == 0xe0) {
			    if (count + 1 >= utfSize)
				    throw new UTFDataFormatException(Messages.getString("luni.D8",
						    (count + 1)));
			    int b = buf[count++];
			    int c = buf[count++];
			    if (((b & 0xC0) != 0x80) || ((c & 0xC0) != 0x80))
				    throw new UTFDataFormatException(Messages.getString("luni.D9",
						    (count - 2)));
			    out[s++] = (char) (((a & 0x0F) << 12) | ((b & 0x3F) << 6) | (c & 0x3F));
		    } else {
			    throw new UTFDataFormatException(Messages.getString("luni.DA",
					    (count - 1)));
		    }
	    }
	    return new String(out, 0, s);
    }

    /**
     * Reads the tc object from the source stream.
     * 
     * @return the object read from the source stream.
     * @throws ClassNotFoundException
     *             if the class of one of the objects in the object graph cannot
     *             be found.
     * @throws IOException
     *             if an error occurs while reading from the source stream.
     * @throws OptionalDataException
     *             if primitive data types were found instead of an object.
     * @see ObjectOutputStream#writeObject(Object)
     */
    @Override
    protected final Object readObjectOverride() throws OptionalDataException,
            ClassNotFoundException, IOException {
	return readObject(false, null);
    }
    
    /**
     * <p>
     * Reads the tc object from the source stream. In this case,
     * the Object will only be read from the stream if the type matches.
     * </p><p>
     * If the stream type doesn't match, AtomicMarshalInputStream will check
     * if the class has a readResolve method and check its annotated with @Serializer 
     * with a declared return type.
     * If neither match the expected type, an InvalidObjectException
     * will be thrown.
     * </p><p>
     * If no exception is thrown, then AtomicMarshalInputStream will proceed
     * and deserialize the object.
     * </p>
     * @param <T>
     * @param type
     * @return the new object read.
     * @throws ClassNotFoundException
     *             if the class of one of the objects in the object graph cannot
     *             be found.
     * @throws IOException
     *             if an error occurs while reading from the source stream.
     * @see ObjectOutputStream#writeUnshared
     */
    public <T> T readObject(Class<T> type) throws IOException, ClassNotFoundException {
	return (T) readObject(false, type);
    }

    /**
     * Reads the tc unshared object from the source stream.
     * 
     * @return the new object read.
     * @throws ClassNotFoundException
     *             if the class of one of the objects in the object graph cannot
     *             be found.
     * @throws IOException
     *             if an error occurs while reading from the source stream.
     * @see ObjectOutputStream#writeUnshared
     */
    @Override
    public Object readUnshared() throws IOException, ClassNotFoundException {
	return readObject(true, null);
    }
    
    /**
     * Reads the tc unshared object from the source stream.  In this case,
     * the Object will only be read from the stream if the type matches.
     * 
     * @param type the Class of the object to be read.
     * @return the new object read.
     * @throws ClassNotFoundException
     *             if the class of one of the objects in the object graph cannot
     *             be found.
     * @throws IOException
     *             if an error occurs while reading from the source stream.
     * @see ObjectOutputStream#writeUnshared
     */
    public <T> T readUnshared(Class<T> type) throws IOException, ClassNotFoundException {
	return (T) readObject(true, type);
    }

    private Object readObject(boolean unshared, Class type) throws OptionalDataException,
            ClassNotFoundException, IOException {
        boolean restoreInput = (primitiveData == input);
        if (restoreInput) {
            primitiveData = emptyStream;
        }

        // This is the spec'ed behavior in JDK 1.2. Very bizarre way to allow
        // behavior overriding.
//        if (subclassOverridingImplementation && !unshared) {
//            return readObjectOverride();
//        }

        // If we still had primitive types to read, should we discard them
        // (reset the primitiveTypes stream) or leave as is, so that attempts to
        // read primitive types won't read 'past data' ???
        Object result;
        try {
            // We need this so we can tell when we are returning to the
            // original/outside caller
            if (++nestedLevels == 1) {
		arrayLenAllowedRemain = MAX_COMBINED_ARRAY_LEN;
                // Remember the caller's class loader
    //                callerClassLoader = AccessController.doPrivileged(
    //		    new PrivilegedAction<ClassLoader>(){
    //
    //			@Override
    //			public ClassLoader run() {
    //			    return context.caller().getClassLoader();
    //			}
    //
    //		    }
    //		);
            }

            result = readNonPrimitiveContent(unshared, type);
            if (restoreInput) {
                primitiveData = input;
            }
        } finally {
            // We need this so we can tell when we are returning to the
            // original/outside caller
            if (--nestedLevels == 0) {
		arrayLenAllowedRemain = 0;
                // We are going to return to the original caller, perform
                // cleanups.
                // No more need to remember the caller's class loader
//                callerClassLoader = null;
            }
        }

        // Done reading this object. Is it time to return to the original
        // caller? If so we need to perform validations first.
        if (nestedLevels == 0 && validations != null) {
            // We are going to return to the original caller. If validation is
            // enabled we need to run them now and then cleanup the validation
            // collection
            try {
                for (InputValidationDesc element : validations) {
                    element.validator.validateObject();
                }
            } finally {
                // Validations have to be renewed, since they are only called
                // from readObject
                validations = null;
            }
        }
        return result;
    }

    /**
     * Reads a short (16 bit) from the source stream.
     * 
     * @return the short value read from the source stream.
     * @throws IOException
     *             if an error occurs while reading from the source stream.
     */
    @Override
    public short readShort() throws IOException {
	return primitiveTypes.readShort();
    }

    /**
     * Does nothing.
     * 
     * @throws IOException
     *             if an error occurs while reading from the source stream.
     * @throws StreamCorruptedException
     *             if the source stream does not contain readable serialized
     *             objects.
     */
    @Override
    protected final void readStreamHeader() throws IOException,
            StreamCorruptedException {
        if (input.readShort() == STREAM_MAGIC
                && input.readShort() == STREAM_VERSION) {
            return;
        }
        throw new StreamCorruptedException();
    }

    /**
     * Reads an unsigned byte (8 bit) from the source stream.
     * 
     * @return the unsigned byte value read from the source stream packaged in
     *         an integer.
     * @throws EOFException
     *             if the end of the input is reached before the read
     *             request can be satisfied.
     * @throws IOException
     *             if an error occurs while reading from the source stream.
     */
    @Override
    public int readUnsignedByte() throws IOException {
	return primitiveTypes.readUnsignedByte();
    }

    /**
     * Reads an unsigned short (16 bit) from the source stream.
     * 
     * @return the unsigned short value read from the source stream packaged in
     *         an integer.
     * @throws EOFException
     *             if the end of the input is reached before the read
     *             request can be satisfied.
     * @throws IOException
     *             if an error occurs while reading from the source stream.
     */
    @Override
    public int readUnsignedShort() throws IOException {
	return primitiveTypes.readUnsignedShort();
    }

    /**
     * Reads a string encoded in {@link DataInput modified UTF-8} from the
     * source stream.
     * 
     * @return the string encoded in {@link DataInput modified UTF-8} read from
     *         the source stream.
     * @throws EOFException
     *             if the end of the input is reached before the read
     *             request can be satisfied.
     * @throws IOException
     *             if an error occurs while reading from the source stream.
     */
    @Override
    public String readUTF() throws IOException {
	return primitiveTypes.readUTF();
    }

    /**
     * Return the object previously read tagged with handle {@code handle}.
     * 
     * @param handle
     *            The handle that this object was assigned when it was read.
     * @return the object previously read.
     * 
     * @throws InvalidObjectException
     *             If there is no previously read object with this handle
     */
    
    Object registeredObjectRead(int handle)
            throws InvalidObjectException, StreamCorruptedException {
//        Object res = objectsRead.get(handle);
	Object res = null;
	int pos = handle - baseWireHandle;
	synchronized (objectsRead){
	    if (pos < MAX_OBJECT_CACHE){
		res = objectsRead[pos];
	    } else {
		throw new StreamCorruptedException(
			"AtomicObjectInputStream is prohibited from caching more than " 
				+ MAX_OBJECT_CACHE + " Objects");
	    }
	}
//	System.out.println("Reference handle: " + handle + " Object: " + res);
//	if (res == null) {
//	    System.out.println(objectsRead);
//	    throw new StreamCorruptedException("Reference handle out of bounds");
//	}
//	if (res == null) System.out.println("reading null referent");
//	System.out.println("Objects read" + objectsRead);
        if (res == Reference.UNSHARED) {
            throw new InvalidObjectException(Messages.getString("luni.C5")); //$NON-NLS-1$
        } else if (res == Reference.DISCARDED){
	    return null;
	}

        return res;
    }

    /**
     * Assume object {@code obj} has been read, and assign a handle to
     * it, {@code handle}.
     * 
     * @param obj
     *            Non-null object being loaded.
     * @param handle
     *            An Integer, the handle to this object
     * @param unshared
     *            Boolean, indicates that caller is reading in unshared mode
     * 
     * @see #nextHandle
     */
    void registerObjectRead(Object obj, int handle, boolean unshared) throws StreamCorruptedException {
//	System.out.println("Registering Object read: " + obj + " handle: " + handle + " unshared: " + unshared);
//        objectsRead.put(handle, unshared ? Reference.UNSHARED : obj);
	int pos = handle - baseWireHandle;
	synchronized(objectsRead){
	    if (pos < MAX_OBJECT_CACHE){
		objectsRead[pos] = unshared ? Reference.UNSHARED : obj;
	    } else {
		throw new StreamCorruptedException(
			"AtomicObjectInputStream is prohibited from caching more than " 
				+ MAX_OBJECT_CACHE + " Objects");
	    }
	}
    }

    /**
     * Registers a callback for post-deserialization validation of objects. It
     * allows to perform additional consistency checks before the {@code
     * readObject()} method of this class returns its result to the caller. This
     * method can only be called from within the {@code readObject()} method of
     * a class that implements "special" deserialization rules. It can be called
     * multiple times. Validation callbacks are then done in order of decreasing
     * priority, defined by {@code priority}.
     * 
     * @param object
     *            an object that can validate itself by receiving a callback.
     * @param priority
     *            the validator's priority.
     * @throws InvalidObjectException
     *             if {@code object} is {@code null}.
     * @throws NotActiveException
     *             if this stream is currently not reading objects. In that
     *             case, calling this method is not allowed.
     * @see ObjectInputValidation#validateObject()
     */
    @Override
    public void registerValidation(ObjectInputValidation object,
            int priority) throws NotActiveException, InvalidObjectException {
	// Validation can only be registered when inside readObject calls
	Object instanceBeingRead = this.currentObject;

	// We can't be called from just anywhere. There are rules.
	if (instanceBeingRead == null && nestedLevels == 0) {
	    throw new NotActiveException();
	}
	if (object == null) {
	    throw new InvalidObjectException(Messages.getString("luni.C6")); //$NON-NLS-1$
	}
	// From now on it is just insertion in a SortedCollection. Since
	// the Java class libraries don't provide that, we have to
	// implement it from scratch here.
	InputValidationDesc desc = new InputValidationDesc();
	desc.validator = object;
	desc.priority = priority;
	// No need for this, validateObject does not take a parameter
	// desc.toValidate = instanceBeingRead;
	if (validations == null) {
	    validations = new InputValidationDesc[1];
	    validations[0] = desc;
	} else {
	    int i = 0;
	    for (; i < validations.length; i++) {
		InputValidationDesc validation = validations[i];
		// Sorted, higher priority first.
		if (priority >= validation.priority) {
		    break; // Found the index where to insert
		}
	    }
	    InputValidationDesc[] oldValidations = validations;
	    int currentSize = oldValidations.length;
	    validations = new InputValidationDesc[currentSize + 1];
	    System.arraycopy(oldValidations, 0, validations, 0, i);
	    System.arraycopy(oldValidations, i, validations, i + 1, currentSize
		    - i);
	    validations[i] = desc;
	}
    }

    /**
     * Reset the collection of objects already loaded by the receiver.
     */
    private void resetSeenObjects() {
//        objectsRead.clear();
	synchronized (objectsRead){
	    for (int i = 0; i < MAX_OBJECT_CACHE; i++){
		objectsRead[i] = null;
	    }
	}
        currentHandle = baseWireHandle;
        primitiveData = emptyStream;
    }
    
    /**
     * Loads the Java class corresponding to the class descriptor {@code
     * osClass} that has just been read from the source stream.
     * 
     * @param osClass
     *            an ObjectStreamClass read from the source stream.
     * @return a Class corresponding to the descriptor {@code osClass}.
     * @throws ClassNotFoundException
     *             if the class for an object cannot be found.
     * @throws IOException
     *             if an I/O error occurs while creating the class.
     * @see ObjectOutputStream#annotateClass(Class)
     */
//    @Override
//    protected Class<?> resolveClass(ObjectStreamClass osClass)
//            throws IOException, ClassNotFoundException {
//	synchronized (lock){
//	    // fastpath: obtain cached value
//	    Class<?> cls = osClass.forClass();
//	    if (null == cls) {
//		// slowpath: resolve the class
//		String className = osClass.getName();
//
//		// if it is primitive class, for example, long.class
//		cls = PRIMITIVE_CLASSES.get(className);
//
//		if (null == cls) {
//		    // not primitive class
//		    // Use the first non-null ClassLoader on the stack. If null, use
//		    // the system class loader
//		    cls = LoadClass.forName(className, true, callerClassLoader);
//		}
//	    }
//	    return cls;
//	}
//    }

    /**
     * Reset the receiver. The collection of objects already read by the
     * receiver is reset, and internal structures are also reset so that the
     * receiver knows it is in a fresh clean state.
     */
    private void resetState() throws IOException {
	if (nestedLevels != 0) throw new StreamCorruptedException("unexpected recursion depth: " + nestedLevels);
        resetSeenObjects();
        if (hasPushbackTC) nextTC();
	nestedLevels = 0;
    }

    /**
     * Skips {@code length} bytes on the source stream. This method should not
     * be used to skip bytes at any arbitrary position, just when reading
     * primitive data types (int, char etc).
     *
     * @param length
     *            the number of bytes to skip.
     * @return the number of bytes actually skipped.
     * @throws IOException
     *             if an error occurs while skipping bytes on the source stream.
     * @throws NullPointerException
     *             if the source stream is {@code null}.
     */
    @Override
    public int skipBytes(int length) throws IOException {
        // To be used with available. Ok to call if reading primitive buffer
	if (input == null) {
	    throw new NullPointerException();
	}

	int offset = 0;
	while (offset < length) {
	    checkReadPrimitiveTypes();
	    long skipped = primitiveData.skip(length - offset);
	    if (skipped == 0) {
		return offset;
	    }
	    offset += (int) skipped;
	}
	return length;
    }

    /**
     * Verify if the SUID & the base name for descriptor
     * <code>loadedStreamClass</code>matches
     * the SUID & the base name of the corresponding loaded class and
     * init private fields.
     * 
     * @param loadedStreamClass
     *            An ObjectStreamClass that was loaded from the stream.
     * 
     * @throws InvalidClassException
     *             If the SUID of the stream class does not match the VM class
     */
    private void verifyAndInit(ObjectStreamClassContainer loadedStreamClass, Class<?> localClass)
            throws InvalidClassException {
	
        ObjectStreamClass localStreamClass = ObjectStreamClass
                .lookupAny(localClass);

        if (loadedStreamClass.getSerialVersionUID() != localStreamClass
                .getSerialVersionUID()) {
            throw new InvalidClassException(loadedStreamClass.getName(), Messages
                    .getString("luni.C3", loadedStreamClass, //$NON-NLS-1$
                            localStreamClass));
        }
	
	// REMIND: suspect that name check is not desired for full compatibility,
	// as it prevents class substitution.
//        String loadedClassBaseName = getBaseName(loadedStreamClass.getName());
//        String localClassBaseName = getBaseName(localStreamClass.getName());
//
//        if (!loadedClassBaseName.equals(localClassBaseName)) {
//	    if (!loadedClassBaseName.equals("URL")){
//		throw new InvalidClassException(loadedStreamClass.getName(), Messages
//                    .getString("luni.C7", loadedClassBaseName, //$NON-NLS-1$
//                            localClassBaseName));
//	    } // Else Ignore
//	}
	loadedStreamClass.setClass(localClass);
    }

//    private static String getBaseName(String fullName) {
//        int k = fullName.lastIndexOf('.');
//
//        if (k == -1 || k == (fullName.length() - 1)) {
//            return fullName;
//        }
//        return fullName.substring(k + 1);
//    }

    // Avoid recursive defining.
    private static void checkedSetSuperClassDesc(ObjectStreamClassContainer desc,
            ObjectStreamClassContainer superDesc) throws StreamCorruptedException {
        if (desc.equals(superDesc)) {
            throw new StreamCorruptedException();
        }
        desc.setSuperclass(superDesc); // superclass set already
    }
    
     /**
     * Sorts the fields for dumping. Primitive types come first, then regular
     * types.
     * 
     * @param fields
     *            ObjectStreamField[] fields to be sorted
     */
    static void sortFields(ObjectStreamField[] fields) {
        // Sort if necessary
        if (fields.length > 1) {
            Comparator<ObjectStreamField> fieldDescComparator = new Comparator<ObjectStreamField>() {
		@Override
                public int compare(ObjectStreamField f1, ObjectStreamField f2) {
                    return f1.compareTo(f2);
                }
            };
            Arrays.sort(fields, fieldDescComparator);
        }
    }
    
    
    
    
    
    public static enum Reference { CIRCULAR, UNSHARED, DISCARDED }
    
    /**
     * Return true if the type code
     * <code>typecode<code> describes a primitive type
     *
     * @param typecode a char describing the typecode
     * @return {@code true} if the typecode represents a primitive type
     * {@code false} if the typecode represents an Object type (including arrays)
     *
     * @see Object#hashCode
     */
    static boolean isPrimitiveType(char typecode) {
        return !(typecode == '[' || typecode == 'L');
    }
    
    
    
    
}
