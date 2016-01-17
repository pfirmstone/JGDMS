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
import java.io.ObjectInput;
import java.io.ObjectInputStream;
import java.io.ObjectInputValidation;
import java.io.ObjectOutputStream;
import java.io.ObjectStreamClass;
import static java.io.ObjectStreamClass.NO_FIELDS;
import java.io.ObjectStreamConstants;
import static java.io.ObjectStreamConstants.TC_LONGSTRING;
import static java.io.ObjectStreamConstants.TC_NULL;
import static java.io.ObjectStreamConstants.TC_REFERENCE;
import static java.io.ObjectStreamConstants.TC_STRING;
import java.io.ObjectStreamException;
import java.io.ObjectStreamField;
import java.io.OptionalDataException;
import java.io.OutputStream;
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
import java.net.URL;
import java.rmi.MarshalledObject;
import java.rmi.activation.ActivationGroupID;
import java.security.AccessController;
import java.security.CodeSource;
import java.security.PrivilegedAction;
import java.security.PrivilegedActionException;
import java.security.PrivilegedExceptionAction;
import java.security.ProtectionDomain;
import java.security.cert.Certificate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.NavigableSet;
import java.util.Set;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.jini.io.MarshalInputStream;
import net.jini.io.ObjectStreamContext;
import org.apache.river.api.io.AtomicSerial.Factory;
import org.apache.river.api.io.AtomicSerial.GetArg;
import org.apache.river.api.io.AtomicSerial.ReadObject;
import org.apache.river.impl.Messages;


/**
 * ObjectInputStream hardened against DOS attack.
 * 
 * Not supported:
   private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException
   object graphs with circular references.
   Object construction using the first non serializable superclass zero
   arg constructor.
 
 Supported:
   Classes annotated with @AtomicSerial, that provide a single GetArg parameter 
   constructor and throw IOException.
   String's, arrays, enums, primitive values.
   Classes that have a zero arg constructor and have been granted DeSerializationPermission
   A minimial set of pre defined Java platform classes that don't conform to
   these rules but have been audited for invariant security.
   The Serialization stream protocol.

 Informational:
   Collection, List Set, SortedSet, Map and SortedMap, are replaced in
   AtomicObjectOutputStream with immutable implementations that guard
   against denial of service attacks.  These collections are not intended
   to be used in de-serialized form, other than for passing as an argument
   to create a new collection.  Collections should be type checked during
   validation before a superclass constructor is called.
   AtomicMarshalInputStream is restricted to caching 2^13 objects, the
   stream must be reset prior to exceeding this number or a 
   StreamCorruptedException will be thrown.
 *
 * @author peter
 */
public class AtomicMarshalInputStream extends MarshalInputStream {
  
    private final InputStream emptyStream = new ByteArrayInputStream(
            new byte[0]);
    
    // These two settings are to prevent DOS attacks.
    private static final long MAX_COMBINED_ARRAY_LEN = 4194304L;
    private static final int MAX_OBJECT_CACHE = 65664;
    
    private static final Class [] EMPTY_CONSTRUCTOR_PARAM_TYPES = new Class[0];
    
    static final int FIELD_IS_NOT_RESOLVED = -1;
    static final int FIELD_IS_ABSENT = -2;

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
    protected AtomicMarshalInputStream(InputStream input,
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
                return readNewArray(false);
            case TC_OBJECT:
//		System.out.println("TC_OBJECT");
                return readNewObject(false, discard);
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
    private Object readNonPrimitiveContent(boolean unshared)
            throws ClassNotFoundException, IOException {
//	System.out.println("readNonPrimitiveContent");
        checkReadPrimitiveTypes();
        if (primitiveData.available() > 0) {
	    throw new StreamCorruptedException("unexpected block data");
        }
        do {
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
                    return readNewArray(unshared);
                case TC_OBJECT:
//		    System.out.println("TC_OBJECT");
                    return readNewObject(unshared, false);
                case TC_STRING:
//		    System.out.println("TC_STRING");
                    return readNewString(unshared);
                case TC_LONGSTRING:
//		    System.out.println("TC_LONGSTRING");
                    return readNewLongString(unshared);
                case TC_ENUM:
//		    System.out.println("TC_ENUM");
                    return readEnum(unshared);
                case TC_REFERENCE:
//		    System.out.println("TC_REFERENCE");
                    if (unshared) {
                        readNewHandle();
                        throw new InvalidObjectException(Messages.getString("luni.BE")); //$NON-NLS-1$
                    }
                    return readCyclicReference();
                case TC_NULL:
//		    System.out.println("TC_NULL");
                    return null;
                case TC_EXCEPTION:
//		    System.out.println("TC_EXCEPTION");
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
        Exception exc = (Exception) readObject(false);

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
	    = new EmulatedFieldsForLoading(currentClass.deserializedClass);
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
            element.defaulted = false;
            Class<?> type = element.field.getType();
            if (type == Integer.TYPE) {
		element.intValue = input.readInt();
            } else if (type == Byte.TYPE) {
                element.byteValue = input.readByte();
            } else if (type == Character.TYPE) {
                element.charValue = input.readChar();
            } else if (type == Short.TYPE) {
                element.shortValue = input.readShort();
            } else if (type == Boolean.TYPE) {
                element.booleanValue = input.readBoolean();
            } else if (type == Long.TYPE) {
                element.longValue = input.readLong();
            } else if (type == Float.TYPE) {
                element.floatValue = input.readFloat();
            } else if (type == Double.TYPE) {
                element.doubleValue = input.readDouble();
            } else {
                // Either array or Object
                try {
                    element.fieldValue = readObject(false);
                } catch (ClassNotFoundException cnf) {
                    // WARNING- Not sure this is the right thing to do. Write
                    // test case.
                    throw new InvalidClassException(cnf.toString());
                } catch (StreamCorruptedException e){
		    StringBuilder b = new StringBuilder(200);
		    b.append("Unable to read field: ");
		    b.append(element.field.getName());
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
		    b.append(element.field.getName());
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
        ObjectStreamField[] fields = classDesc.getFields();
        fields = (null == fields ? new ObjectStreamField[0] : fields);
        final Class<?> declaringClass = classDesc.forClass();
        if (declaringClass == null && mustResolve) {
            throw new ClassNotFoundException(classDesc.getName());
        }
	

//	System.out.println("Declaring class" + declaringClass);
        for (final ObjectStreamField fieldDesc : fields) {
	    
            // get associated Field 
//            long fieldID = fieldDesc.getFieldID(accessor, declaringClass);
	    Field f = null;
	    boolean exists = false;
	    f = AccessController.doPrivileged(new PrivilegedAction<Field>(){

		@Override
		public Field run() {
		    try {
			return fieldDesc == null || declaringClass == null ?  null : 
				declaringClass.getDeclaredField(fieldDesc.getName());
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
            if (fieldDesc != null && fieldDesc.isPrimitive()) {
               IOException ex = AccessController.doPrivileged(
		       new SetPrimitiveFieldAction(
			       obj,
			       input,
			       f,
			       exists,
			       fieldDesc.getTypeCode()
		       )
	       );
	       if (ex != null) throw ex;
            } else {
                // Object type (array included).
                String fieldName = fieldDesc == null ? null :
			fieldDesc.getName();
                boolean setBack = false;
                if (mustResolve && fieldDesc == null) {
                    setBack = true;
                    mustResolve = false;
                }
                Object toSet = null;
		if (fieldDesc != null && !fieldDesc.isPrimitive()){
		    try {
			if (fieldDesc.isUnshared()) {
			    toSet = readUnshared();
			} else {
			    toSet = readObject(false);
			}
		    } catch (EOFException e){
			StringBuilder b = new StringBuilder(200);
			b.append("Exception thrown with attemting to read field: ");
			b.append(fieldDesc);
			b.append("\n");
			b.append("Within fields: ");
			b.append(Arrays.asList(fields));
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
                if (fieldDesc != null) {
                    if (toSet != null) {
                        Class<?> fieldType = getFieldClass(obj, fieldName);
                        Class<?> valueType = toSet.getClass();
                        if (fieldType != null) {
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
                AccessController.doPrivileged(new PriviAction<Object>(
                        readMethod));
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
    private Object readNewArray(boolean unshared) throws OptionalDataException,
            ClassNotFoundException, IOException {
        ObjectStreamClassContainer classDesc = readClassDesc();
//	System.out.println("readNewArray");
        if (classDesc == null) {
            throw new InvalidClassException(Messages.getString("luni.C1")); //$NON-NLS-1$
        }

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
		    objectArray[i] = readObject(false);
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
			    superResolveClass(newClassDesc.deserializedClass) 
			    :resolveClass(newClassDesc.deserializedClass);
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
	classDescriptorContainer.deserializedClass = newClassDesc;
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
	container.deserializedClass = newClassDesc;
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
    private Object readNewObject(boolean unshared, boolean discard)
            throws OptionalDataException, ClassNotFoundException, IOException {
        ObjectStreamClassContainer classDesc = readClassDesc();

        if (classDesc == null) {
            throw new InvalidClassException(Messages.getString("luni.C1")); //$NON-NLS-1$
        }
        int newHandle = nextHandle();

        // Note that these values come from the Stream, and in fact it could be
        // that the classes have been changed so that the info below now
        // conflicts with the newer class
	boolean wasExternalizable = classDesc.wasExternalizable();
	boolean wasSerializable = classDesc.wasSerializable();
	boolean atomicSerialOrDiscard = true;
	
        // Maybe we should cache the values above in classDesc ? It may be the
        // case that when reading classDesc we may need to read more stuff
        // depending on the values above
        Class<?> objectClass = classDesc.forClass();
        Object result = null, registeredResult = null;
        if (objectClass != null) {
	    if (objectClass.isAnnotationPresent(AtomicSerial.class)){
		
		registerObjectRead(null, newHandle, unshared);
		result = instantiateAtomically(classDesc, false);
		registerObjectRead(result, newHandle, unshared);
		registeredResult = result;
	    } else if (Externalizable.class.isAssignableFrom(objectClass)){
		try {
		    atomicSerialOrDiscard = false;
		    classDesc.deSerializationPermitted(); // Permission check
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
		instantiateAtomically(classDesc, discard);
		return null;
	    } else if (Serializable.class.isAssignableFrom(objectClass)) {
		// Serializable not fully supported, this retreived the 
		// first non serializable superclass zero arg constructor
//            long constructor = osci.constructor;
//            if (constructor == ObjectStreamClass.CONSTRUCTOR_IS_NOT_RESOLVED) {
//                constructor = accessor.getMethodID(resolveConstructorClass(objectClass, wasSerializable, wasExternalizable), null, new Class[0]);
//                classDesc.setConstructor(constructor);
//            }
		try {
		    result = classDesc.newInstance(); // Best effort construction using child class constructor
		    if (result instanceof Throwable) // Clear stack trace.
			((Throwable) result).setStackTrace(new StackTraceElement[0]);
		    atomicSerialOrDiscard = false;
		} catch (Exception ex){
		    Logger.getLogger(AtomicMarshalInputStream.class.getName()).log(Level.SEVERE, null, ex);
		    registerObjectRead(Reference.DISCARDED, newHandle, unshared);
		    instantiateAtomically(classDesc, true);
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
	if (!atomicSerialOrDiscard) {
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
		    if (!blockData) {
			primitiveData = input;
		    } else {
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
    
    private Object instantiateAtomically(final ObjectStreamClassContainer classDesc, boolean discard) 
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
	    GetField field = readFields(streamClass.deserializedClass);
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
		throw new ClassNotFoundException(streamClass.osci.fullyQualifiedClassName);
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
	return readObject(false);
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
	return readObject(true);
    }

    private Object readObject(boolean unshared) throws OptionalDataException,
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

            result = readNonPrimitiveContent(unshared);
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
    
    static class ObjectStreamClassContainer{
	private static final ProtectionDomain unprivilegedContext = 
	    new ProtectionDomain(new CodeSource(null,(Certificate[]) null), null);
	static final ConcurrentMap<Class<?>,ObjectStreamClassContainer> lookup = new ConcurrentHashMap<Class<?>,ObjectStreamClassContainer>();
	final ObjectStreamField [] empty;
 	ObjectStreamClass localClass;
	ObjectStreamClass deserializedClass;
	ObjectStreamClassInformation osci;
	ObjectStreamClassContainer superClass;
	int handle;
	boolean isProxy;
	Class<?> resolvedClass;
	Method readObjectMethod;
	Method readResolveMethod;
	Method readObjectNoDataMethod;
	Constructor constructor;
	Object [] constructorParams;
	
	ObjectStreamClassContainer(){
	    empty = new ObjectStreamField[0];
	}
	
	ObjectStreamClassContainer(final ObjectStreamClass localClass, ObjectStreamClass deserializedClass, ObjectStreamClassInformation osci, int handle, boolean isProxy){
	    this();
	    this.localClass = localClass;
	    this.deserializedClass = deserializedClass;
	    this.osci = osci;
	    this.handle = handle;
	    this.isProxy = isProxy;
	}
	
	protected boolean deSerializationPermitted() {
	    if (readObjectNoDataMethod == null){ //Ok if there's no data.
		// Check all classes in heirarchy for absence of data (stateless object)
		// Not worried about primitive fields, might as well be stateless.
		ObjectStreamClassInformation osc = osci;
		ObjectStreamClassContainer superClass = this.superClass;
		while ( osc != null && osc.hasWriteObjectData == false 
			&& osc.hasBlockExternalData == false 
			&& osc.numObjFields == 0){
		    if (superClass != null) {
			osc = superClass.osci;
			superClass = superClass.superClass;
		    } else {
			return true; // If there's no data and no object fields don't worry about checking.
		    }
		}
	    }
	    String name = getName();
	    if (name.startsWith("[L") && name.endsWith(";")){//object array
		int l = name.length();
		String clName = name.substring(2, l - 1);
		name = clName;
	    }
	    //TODO: consider whether to simplify DeSerializationPermission
	    // to one that is granted to a ProtectionDomain.
	    return unprivilegedContext.implies(
		new DeSerializationPermission(name));
	}
	
	@Override
	public String toString(){
	    String clas = "unresolved";
	    if (osci != null && osci.fullyQualifiedClassName != null ) clas = osci.fullyQualifiedClassName;
	    if (localClass != null) clas = localClass.toString();
	    return super.toString() + " Class: " + clas;
	}
	
	/**
	 * 
	 * @param clas
	 * @return
	 * @throws IOException 
	 */
	Object newParamInstance(Class<?> clas, boolean collectionsClass) throws IOException {
	    // Special cases, all others must be null or we 
	    // can affect object equality with nasty unexpected bugs.
	    if (clas == Integer.TYPE) return 0;
	    if (clas == Long.TYPE) return (long) 0;
	    if (clas == Boolean.TYPE) return false;
	    if (clas == Byte.TYPE) return (byte)0;
	    if (clas == Character.TYPE) return (char) 0;
	    if (clas == Short.TYPE) return (short)0;
	    if (clas == Double.TYPE) return (double) 0.0;
	    if (clas == Float.TYPE) return (float) 0.0;
	    if (clas == ActivationGroupID.class) return new ActivationGroupID(null);
	    if (collectionsClass){ // Collections classes don't allow null parameters.
		if (clas == Object[].class) return new Object[0];
		if (clas == Collection.class || clas == List.class) return Collections.emptyList();
		if (clas == Set.class || clas == SortedSet.class || clas == NavigableSet.class){
		    return Collections.emptyNavigableSet();
		}
		if (clas == Map.class || clas == SortedMap.class || clas == NavigableMap.class){
		    return Collections.emptyNavigableMap();
		}
	    }
	    return null;
	}

	Object newInstance() throws IOException{
	    // Now we ask for permission, this includes a lot of exception classes
	    deSerializationPermitted();
	    if (!isProxy){
		// Special construction cases - none at present
	    }
	    if (constructor == null){
		boolean isCollections = false;
		String classname = resolvedClass.getName();
		if (classname.equals("java.util.Arrays$ArrayList")) isCollections = true;
		if (classname.startsWith("java.util.Collections")) isCollections = true;
		System.out.println("Finding constructor for class " + resolvedClass);
		Constructor [] ctors = getConstructors(resolvedClass);
		for (int i = 0, l = ctors.length; i < l; i++){
		    int count;
		    count = ctors[i].getParameterCount();
		    Class [] ptypes = ctors[i].getParameterTypes();
		    try {
			Object [] prams = new Object [count];
			for (int j = 0; j < count; j++){
			    // we could try harder, but this will do for now.
			    prams [j] = newParamInstance(ptypes [j], isCollections);
			}
//			ctors[i].setAccessible(true);
 			Object result = ctors[i].newInstance(prams);
//			System.out.println("Successfully created instance " + result);
			// Now we know it works, record it.
			constructor = ctors[i];
			constructorParams = prams;
			return result;
		    } catch (InstantiationException ex) {
//			Logger.getLogger(AtomicMarshalInputStream.class.getName()).log(Level.SEVERE, resolvedClass.getCanonicalName(), ex);
		    } catch (IllegalAccessException ex) {
//			Logger.getLogger(AtomicMarshalInputStream.class.getName()).log(Level.SEVERE, resolvedClass.getCanonicalName(), ex);
		    } catch (IllegalArgumentException ex) {
//			Logger.getLogger(AtomicMarshalInputStream.class.getName()).log(Level.SEVERE, resolvedClass.getCanonicalName(), ex);
		    } catch (InvocationTargetException ex) {
//			Logger.getLogger(AtomicMarshalInputStream.class.getName()).log(Level.SEVERE, resolvedClass.getCanonicalName(), ex);
		    } catch (Exception ex) {
//			Logger.getLogger(AtomicMarshalInputStream.class.getName()).log(Level.SEVERE, resolvedClass.getCanonicalName(), ex);
		    }
		}
	    }
	    try {
		if (constructor == null) throw new InvalidObjectException("constructor is null: " + resolvedClass.getCanonicalName());
		return constructor.newInstance(constructorParams);
	    } catch (InstantiationException ex) {
		throw new IOException("Unable to crate",ex);
	    } catch (IllegalAccessException ex) {
		throw new IOException(ex);
	    } catch (IllegalArgumentException ex) {
		throw new IOException(ex);
	    } catch (InvocationTargetException ex) {
		throw new IOException(ex);
	    } catch (NullPointerException ex){
//		System.out.println("Unable to find a suitable constructor for class " + resolvedClass);
		InvalidObjectException e = new InvalidObjectException("Cannot create instance of " + resolvedClass);
		e.initCause(ex);
		throw e;
	    }
	    
	}
	
	Constructor [] getConstructors(final Class clas){
	    return AccessController.doPrivileged(new PrivilegedAction<Constructor []>(){
		@Override
		public Constructor [] run() {
		    try {
			Constructor [] ctors = clas.getDeclaredConstructors();
			for (int i = 0, l = ctors.length; i < l; i++){
			    ctors [i].setAccessible(true);
			}
			return ctors;
			
		    } catch (SecurityException ex) {
			Logger.getLogger(ObjectStreamClassInformation.class.getName()).log(Level.SEVERE, null, ex);
		    }
		    return null;
		}
	    });
	}
	
	boolean hasWriteObjectData(){
	    if (osci != null) return osci.hasWriteObjectData;
	    return false;
	}

	boolean hasReadObject(){
	    return readObjectMethod != null;
	}

	void invokeReadObject(Object o, ObjectInputStream in) throws IOException, ClassNotFoundException{
	    Object [] params = {in};
	    try {
		readObjectMethod.invoke(o, params);
	    } catch (IllegalAccessException ex) {
		Logger.getLogger(ObjectStreamClassInformation.class.getName()).log(Level.SEVERE, null, ex);
	    } catch (IllegalArgumentException ex) {
		Logger.getLogger(ObjectStreamClassInformation.class.getName()).log(Level.SEVERE, null, ex);
	    } catch (InvocationTargetException ex) {
		Throwable t = ex.getTargetException();
		if (t instanceof IOException) throw (IOException) t;
		if (t instanceof ClassNotFoundException) throw (ClassNotFoundException) t;
		Logger.getLogger(ObjectStreamClassInformation.class.getName()).log(Level.SEVERE, null, ex);
	    }
	}

	Object invokeReadResolve(Object o) throws ObjectStreamException{
	    try {
		if (readResolveMethod == null){		    
		    readResolveMethod = getReadResolveMethod(o.getClass());
		    if (readResolveMethod == null) return o;
		}
		return readResolveMethod.invoke(o, (Object[]) null);
	    } catch (IllegalAccessException ex) {
		Logger.getLogger(ObjectStreamClassInformation.class.getName()).log(Level.SEVERE, null, ex);
	    } catch (IllegalArgumentException ex) {
		Logger.getLogger(ObjectStreamClassInformation.class.getName()).log(Level.SEVERE, null, ex);
	    } catch (InvocationTargetException ex) {
		Throwable target = ex.getTargetException();
		if (target instanceof ObjectStreamException) {
		    throw (ObjectStreamException) target;
		} else if (target instanceof Error) {
		    throw (Error) target;
		} else {
		    throw (RuntimeException) target;
		}
	    }
	    return null;
	}

	void invokeReadObjectNoData(Object o) throws InvalidObjectException{
	    try {
		readObjectMethod.invoke(o, (Object[]) null);
	    } catch (IllegalAccessException ex) {
		Logger.getLogger(ObjectStreamClassInformation.class.getName()).log(Level.SEVERE, null, ex);
	    } catch (IllegalArgumentException ex) {
		Logger.getLogger(ObjectStreamClassInformation.class.getName()).log(Level.SEVERE, null, ex);
	    } catch (InvocationTargetException ex) {
		Throwable t = ex.getTargetException();
		if (t instanceof InvalidObjectException) throw (InvalidObjectException) t;
		Logger.getLogger(ObjectStreamClassInformation.class.getName()).log(Level.SEVERE, null, ex);
	    }
	}

	boolean hasMethodReadResolve(){
	    return readResolveMethod != null ? true : resolvedClass == URL.class;
	}

	boolean hasReadObjectNoData(){
	    return readObjectNoDataMethod != null;
	}
	
	Method getPrivateInstanceMethod(final Class<?> c, 
		final String methodName,
		final Class<?>[] parameters,
		final Class<?> returnType )
	{
	    return AccessController.doPrivileged(new PrivilegedAction<Method>(){
		@Override
		public Method run() {
		    try {
			Method m = c.getDeclaredMethod(methodName, parameters);
			int modifiers = m.getModifiers();
			if (Modifier.isPrivate(modifiers) 
				&& !Modifier.isStatic(modifiers) 
				&& returnType.equals(m.getReturnType())){
			    m.setAccessible(true);
			    return m;
			}
		    } catch (NoSuchMethodException e){ // TODO: Log
		    } catch (SecurityException e){} // TODO: Log
		    return null;
		}
	    });
	}

	void setClass(final Class<?> c){
	    resolvedClass = c;
	    readResolveMethod = getReadResolveMethod(c);
	    Class[] params = {ObjectInputStream.class};
	    readObjectMethod = getPrivateInstanceMethod(c, "readObject", params, Object.class );
	    readObjectNoDataMethod = getPrivateInstanceMethod(c, "readObjectNoData", null, Void.TYPE);
	    
	    putInMap();
	}

	Method getReadResolveMethod(final Class<?> c){
	    return AccessController.doPrivileged(new PrivilegedAction<Method>(){

		@Override
		public Method run() {
		    String name = "readResolve";
		    Method m = null;
		    int modifiers = 0;
		    Class cm = c;
		    int count = 0;
		    do {
			try {
			    m = cm.getDeclaredMethod(name, (Class[]) null);
			    modifiers = m.getModifiers();
			    m.setAccessible(true);
			    if (Modifier.isStatic(count) || !Object.class.equals(m.getReturnType())){
				cm = cm.getSuperclass();
				count ++;
				continue;
			    } 
			    break;
			} catch (NoSuchMethodException ex) {
			    cm = cm.getSuperclass();
			    count++;
			} 
		    } while (cm != null);
		    if (m == null) return null;
		    boolean privt = Modifier.isPrivate(modifiers);
		    boolean prted = Modifier.isProtected(modifiers);
		    boolean pub = Modifier.isPublic(modifiers);

		    if (count == 0){
			return m;
		    } else {
			if (pub || prted) return m;
			if (!privt && !prted && !pub){ // Check package access.
			    if (c.getPackage().equals(cm.getPackage()) 
				    && c.getClassLoader() == cm.getClassLoader()){
				return m;
			    }
			}
		    }
		    return null;
		}
	    });

	}

	@Override
	public int hashCode() {
	    int hash = 3;
	    hash = 11 * hash + handle;
	    return hash;
	}
	
	@Override
	public boolean equals(Object o){
	    if (!(o instanceof ObjectStreamClassContainer )) return false;
	    ObjectStreamClassContainer that = (ObjectStreamClassContainer) o;
	    return this.handle == that.handle;
	}
	
	void putInMap(){
	    lookup.putIfAbsent(forClass(), this);
	}
	
	void setLocalClassDescriptor(ObjectStreamClass descriptor){
	    localClass = descriptor;
	    if (isProxy){
		constructor = AccessController.doPrivileged(new PrivilegedAction<Constructor>(){
		    @Override
		    public Constructor run() {
			try {
			    Class [] params = {InvocationHandler.class};
			    Constructor constructor = localClass.forClass().getDeclaredConstructor(params);
			    constructor.setAccessible(true);
			    return constructor;
			} catch (NoSuchMethodException ex) {
			    Logger.getLogger(ObjectStreamClassInformation.class.getName()).log(Level.SEVERE, null, ex);
			} catch (SecurityException ex) {
			    Logger.getLogger(ObjectStreamClassInformation.class.getName()).log(Level.SEVERE, null, ex);
			}
			return null;
		    }
		});
		constructorParams = new Object[1];
		constructorParams [0] = new InvocationHandler(){

		    @Override
		    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
			Method m = Object.class.getMethod("toString", (Class[]) null);
			if (m.equals(method)) return "java.lang.reflect.Proxy";
			throw new UnsupportedOperationException("Serializable field hasn't been set");
		    }
		     
		};
	    }
	}
	
	ObjectStreamClassContainer getSuperDesc(){
	    return superClass;
	}
	
	
	Class<?> forClass(){
	    if (resolvedClass != null) return resolvedClass;
	    if (deserializedClass != null) {
		Class <?> clz = deserializedClass.forClass();
		if (clz != null) return clz;
	    }
	    return localClass != null ? localClass.forClass() : null;
	}
	
	String getName(){
	    if (osci != null) return osci.fullyQualifiedClassName;
	    if (localClass != null) return localClass.forClass().getName();
	    return forClass().getName();
	}
	
	boolean wasSerializable(){
	    if (osci != null) return osci.serializable;
	    return false;
	}
	
	boolean wasExternalizable(){
	    if (osci != null) return osci.externalizable;
	    return false;
	}
	
	boolean hasBlockData(){
	    if (osci != null) return osci.hasBlockExternalData;
	    return false;
	}
	
	boolean isProxy(){
	    return isProxy;
	}
	
	long getSerialVersionUID(){
	    if (osci !=null) return osci.serialVer;
	    return -1L;
	}
	
	ObjectStreamField [] getFields(){
	    if (deserializedClass != null) deserializedClass.getFields();
	    if (osci != null) return osci.fields;
	    if (localClass != null) return localClass.getFields();
	    return empty;
	}

	private void setSuperclass(ObjectStreamClassContainer readClassDesc) {
	    superClass = readClassDesc;
	}
	
    }
    
    /**
     * Dummy security manager providing access to getClassContext method.
     */
    private static class ClassContextAccess extends SecurityManager {
	/**
	 * Returns caller's caller class.
	 */
	Class caller() {
	    return getClassContext()[2];
	}
    }
    
    private static final ClassContextAccess context 
	    = AccessController.doPrivileged(
    new PrivilegedAction<ClassContextAccess>(){

	@Override
	public ClassContextAccess run() {
	    return new ClassContextAccess();
	}
	
    });
    
    private static class GetArgImpl extends AtomicSerial.GetArg {
	final Map<Class,GetField> classFields;
	final Map<Class,ReadObject> readers;
	final ObjectInput in;
	
	GetArgImpl(Map<Class,GetField> args, Map<Class,ReadObject> readers, ObjectInput in){
	    super(false); // Avoids permission check.
	    classFields = args;
	    this.readers = readers;
	    this.in = in;
	}

	@Override
	public ObjectStreamClass getObjectStreamClass() {
	    return classFields.get(context.caller()).getObjectStreamClass();
	}

	@Override
	public boolean defaulted(String name) throws IOException {
	    return classFields.get(context.caller()).defaulted(name);
	}

	@Override
	public boolean get(String name, boolean val) throws IOException {
	    return classFields.get(context.caller()).get(name, val);
	}

	@Override
	public byte get(String name, byte val) throws IOException {
	    return classFields.get(context.caller()).get(name, val);
	}

	@Override
	public char get(String name, char val) throws IOException {
	    return classFields.get(context.caller()).get(name, val);
	}

	@Override
	public short get(String name, short val) throws IOException {
	    return classFields.get(context.caller()).get(name, val);
	}

	@Override
	public int get(String name, int val) throws IOException {
	    return classFields.get(context.caller()).get(name, val);
	}

	@Override
	public long get(String name, long val) throws IOException {
	    return classFields.get(context.caller()).get(name, val);
	}

	@Override
	public float get(String name, float val) throws IOException {
	    return classFields.get(context.caller()).get(name, val);
	}

	@Override
	public double get(String name, double val) throws IOException {
	    return classFields.get(context.caller()).get(name, val);
	}

	@Override
	public Object get(String name, Object val) throws IOException {
	    return classFields.get(context.caller()).get(name, val);
	}
	
	@Override
	public <T> T get(String name, T val, Class<T> type) throws IOException {
	    // T will be replaced by Object by the compilers erasure.
	    T result = (T) classFields.get(context.caller()).get(name, val);
	    if (type.isInstance(result)) return result;
	    if (result == null) return null;
	    InvalidObjectException e = new InvalidObjectException("Input validation failed");
	    e.initCause(new ClassCastException("Attempt to assign object of incompatible type"));
	    throw e;
	}

	@Override
	public Collection getObjectStreamContext() {
	    if (in instanceof ObjectStreamContext) 
		return ((ObjectStreamContext)in).getObjectStreamContext();
	    return Collections.emptyList();
	}

	@Override
	public Class[] serialClasses() {
	    return classFields.keySet().toArray(new Class[classFields.size()]);
	}

	@Override
	public ReadObject getReader() { //TODO capture any Exceptions and rethrow here.
//	    Class c = context.caller();
//	    System.out.println("CALLER: " + c);
//	    System.out.println(readers);
	    return readers.get(context.caller());
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
    
    /**
     * This class is a container for ObjectStreamClass information
     * contained in an ObjectInputStream, 
     * 
     */
    private static class ObjectStreamClassInformation {
	/**
	 * The resulting class descriptor is not fully functional; it can only be used
	 * as input to the ObjectInputStream.resolveClass() and
	 * ObjectStreamClass.initNonProxy() methods.
	 */
	static ObjectStreamClass convert(ObjectStreamClassInformation o) 
		throws IOException, ClassNotFoundException {
	    ByteArrayOutputStream bao = new ByteArrayOutputStream();
	    ObjectOutputStream dao = new ObjectOutputStream(bao);
	    o.write(dao, true);
	    dao.flush();
	    byte [] bytes = bao.toByteArray();
	    ClassDescriptorConversionObjectInputStream pois 
		    = new ClassDescriptorConversionObjectInputStream(new ByteArrayInputStream(bytes));
	    return pois.readClassDescriptor();
	}

	static ObjectStreamClassInformation convert(ObjectStreamClass o) 
		throws IOException, ClassNotFoundException{
	    ByteArrayOutputStream bao = new ByteArrayOutputStream();
	    ClassDescriptorConversionObjectOutputStream coos = new ClassDescriptorConversionObjectOutputStream(bao);
	    coos.writeClassDescriptor(o);
	    coos.flush();
	    byte [] bytes = bao.toByteArray();
	    ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(bytes));
	    ObjectStreamClassInformation result = new ObjectStreamClassInformation();
	    result.read(in);
	    result.readFields(in);
	    return result;
	}

	/** handle value representing null */
	private static final int NULL_HANDLE = -1;

	String fullyQualifiedClassName;
	long serialVer;
	boolean externalizable;
	boolean serializable;
	boolean hasWriteObjectData;
	boolean hasBlockExternalData;
	boolean isEnum;
	ObjectStreamField[] fields;
	boolean hasHandle;
	int handle;
	private int primDataSize;
	private int numObjFields;



	@Override
	public String toString(){
	    String endLine = "\n";
	    StringBuilder b = new StringBuilder(512);
	    b.append("Name: ")
	    .append(fullyQualifiedClassName)
	    .append(endLine)
	    .append("Externalizable? ")
	    .append(externalizable)
	    .append(endLine)
	    .append("Serializable? ")
	    .append(serializable)
	    .append(endLine)
	    .append("Has writeObject() data? ")
	    .append(hasWriteObjectData)
	    .append(endLine)
	    .append("Has block external data? ")
	    .append(hasBlockExternalData)
	    .append(endLine)
	    .append("Is Enum? ")
	    .append(isEnum)
	    .append(endLine);
	    if (fields != null){
		for (int i = 0, l = fields.length; i < l; i++){
		    if (fields[i] != null){
			b.append("Field name: ")
			.append(fields[i].getName())
			.append(endLine)
			.append("Field type: ")
			.append(fields[i].getTypeCode())
			.append(endLine)
			.append("Field offset: ")
			.append(fields[i].getOffset())
			.append(endLine);
		    }
		}
	    }
	    b.append("Has Handle? ")
	    .append(hasHandle)
	    .append(endLine)
	    .append("Handle: ")
	    .append(handle)
	    .append(endLine)
	    .append("Primitive data size: ")
	    .append(primDataSize)
	    .append(endLine)
	    .append("Number of Object fields: ")
	    .append(numObjFields)
	    .append(endLine);
	    return b.toString();
	}

	/**
	 * Writes class descriptor information to given DataOutputStream.
	 */
	void write(ObjectOutputStream out, boolean replaceHandleWithObject) throws IOException {
	    out.writeUTF(fullyQualifiedClassName);
	    out.writeLong(serialVer);

	    byte flags = 0;
	    if (externalizable) {
		flags |= ObjectStreamConstants.SC_EXTERNALIZABLE;
		flags |= ObjectStreamConstants.SC_BLOCK_DATA; // Stream protocol version 1 isn't supported.
	    } else if (serializable) {
		flags |= ObjectStreamConstants.SC_SERIALIZABLE;
	    }
	    if (hasWriteObjectData) {
		flags |= ObjectStreamConstants.SC_WRITE_METHOD;
	    }
	    if (isEnum) {
		flags |= ObjectStreamConstants.SC_ENUM;
	    }
	    out.writeByte(flags);

	    out.writeShort(fields.length);
	    for (int i = 0, l = fields.length; i < l; i++) {
		ObjectStreamField f = fields[i];
		out.writeByte(f.getTypeCode());
		out.writeUTF(f.getName());
		if (!f.isPrimitive()) {
		    String typeString = f.getTypeString();
		    if (typeString == null) {
			out.writeByte(TC_NULL);
		    } else if (hasHandle && !replaceHandleWithObject) {
			out.writeByte(TC_REFERENCE);
			out.writeInt(handle);
		    } else {
			out.writeByte(TC_STRING);
			out.writeUTF(typeString);
		    }
		}
	    }
	}

	/**
	 * Reads non-proxy class descriptor information from given DataInputStream. 
	 */
	void read(ObjectInputStream in)
		throws IOException, ClassNotFoundException {
//    	System.out.println("read in class descriptor");
	    fullyQualifiedClassName = in.readUTF();
	    if (fullyQualifiedClassName.length() == 0) {
		// luni.07 = The stream is corrupted
		throw new IOException(Messages.getString("luni.07")); //$NON-NLS-1$
	    }
	    serialVer = in.readLong();
	    byte flags = in.readByte();
	    hasWriteObjectData
		    = ((flags & ObjectStreamConstants.SC_WRITE_METHOD) != 0);
	    hasBlockExternalData
		    = ((flags & ObjectStreamConstants.SC_BLOCK_DATA) != 0);
	    externalizable
		    = ((flags & ObjectStreamConstants.SC_EXTERNALIZABLE) != 0);
	    boolean sflag
		    = ((flags & ObjectStreamConstants.SC_SERIALIZABLE) != 0);
	    if (externalizable && sflag) {
		throw new InvalidClassException(
			fullyQualifiedClassName, "serializable and externalizable flags conflict");
	    }
	    serializable = externalizable || sflag;
	    isEnum = ((flags & ObjectStreamConstants.SC_ENUM) != 0);
	    if (isEnum && serialVer != 0L) {
		throw new InvalidClassException(fullyQualifiedClassName,
			"enum descriptor has non-zero serialVersionUID: " + serialVer);
	    }
	}

	/**
	 * Reads a collection of field descriptors (name, type name, etc) for the
	 * class descriptor {@code cDesc} (an {@code ObjectStreamClass})
	 * 
	 * @param cDesc
	 *            The class descriptor (an {@code ObjectStreamClass})
	 *            for which to write field information
	 * 
	 * @throws IOException
	 *             If an IO exception happened when reading the field
	 *             descriptors.
	 * @throws ClassNotFoundException
	 *             If a class for one of the field types could not be found
	 * 
	 * @see #readObject()
	 */
    //    private void readFieldDescriptors(ObjectStreamClass cDesc)
    //            throws ClassNotFoundException, IOException {
    //        short numFields = input.readShort();
    //        ObjectStreamField[] fields = new ObjectStreamField[numFields];
    //
    //        // We set it now, but each element will be inserted in the array further
    //        // down
    //        cDesc.setLoadFields(fields);
    //
    //        // Check ObjectOutputStream.writeFieldDescriptors
    //        for (short i = 0; i < numFields; i++) {
    //            char typecode = (char) input.readByte();
    //            String fieldName = input.readUTF();
    //            boolean isPrimType = isPrimitiveType(typecode);
    //            String classSig;
    //            if (isPrimType) {
    //                classSig = String.valueOf(typecode);
    //            } else {
    //                // The spec says it is a UTF, but experience shows they dump
    //                // this String using writeObject (unlike the field name, which
    //                // is saved with writeUTF).
    //                // And if resolveObject is enabled, the classSig may be modified
    //                // so that the original class descriptor cannot be read
    //                // properly, so it is disabled.
    //                boolean old = enableResolve;
    //                try {
    //                    enableResolve = false;
    //                    classSig = (String) readObject();
    //                } finally {
    //                    enableResolve = old;
    //                }
    //            }
    //            
    //            classSig = formatClassSig(classSig);
    //            ObjectStreamField f = new ObjectStreamField(classSig, fieldName);
    //            fields[i] = f;
    //        }
    //    }

	/*
	 * Format the class signature for ObjectStreamField, for example,
	 * "[L[Ljava.lang.String;;" is converted to "[Ljava.lang.String;"
	 */
    //    private static String formatClassSig(String classSig) {
    //        int start = 0;
    //        int end = classSig.length();
    //
    //        if (end <= 0) {
    //            return classSig;
    //        }
    //
    //        while (classSig.startsWith("[L", start) //$NON-NLS-1$
    //                && classSig.charAt(end - 1) == ';') {
    //            start += 2;
    //            end--;
    //        }
    //
    //        if (start > 0) {
    //            start -= 2;
    //            end++;
    //            return classSig.substring(start, end);
    //        }
    //        return classSig;
    //    }
	void readFields(ObjectInputStream in) throws IOException{
//    	System.out.println("readFields");
	    int numFields = in.readShort();
	    if (isEnum && numFields != 0) {
		throw new InvalidClassException(fullyQualifiedClassName,
			"enum descriptor has non-zero field count: " + numFields);
	    }
	    fields = ((numFields > 0)
		    ? new ObjectField[numFields] : NO_FIELDS);
	    for (int i = 0; i < numFields; i++) {
		char tcode = (char) in.readByte();
		String fname = in.readUTF();
		String signature = null;
		if ((tcode == 'L') || (tcode == '[')) {

		    byte streamConstant = (in instanceof AtomicMarshalInputStream) 
			    ? ((AtomicMarshalInputStream)in).nextTC() : in.readByte();
		    switch (streamConstant) {
			case TC_NULL:
//    			System.out.println("TC_NULL");
			    break;
			case TC_REFERENCE:
//    			System.out.println("TC_REFERENCE");
			    if (in instanceof AtomicMarshalInputStream){
				signature = (String) ((AtomicMarshalInputStream) in).readCyclicReference();
			    } else {
				throw new StreamCorruptedException("TC_REFERENCE is not supported for this stream");
			    }
			    break;
			case TC_STRING:
//    			System.out.println("TC_STRING");
			    if (in instanceof AtomicMarshalInputStream){
				signature = (String) ((AtomicMarshalInputStream) in).readNewString(isEnum);
			    } else {
				signature = in.readUTF();
			    }
			    break;
			case TC_LONGSTRING:
//    			System.out.println("TC_LONGSTRING");
			    if (in instanceof AtomicMarshalInputStream){
				signature = (String) ((AtomicMarshalInputStream) in).readNewLongString(isEnum);
			    } else {
				throw new UnsupportedOperationException("Cannot read long UTF string from this stream");
			    }
			default:
			    throw new StreamCorruptedException("Stream failed in ObjectStreamClass descriptor");
		    }
		} else {
		    signature = new String(new char[]{tcode});
		}
		try {
		    fields[i] = new ObjectField(fname, signature, false);
		} catch (RuntimeException e) {
		    IOException ex = new InvalidClassException(fullyQualifiedClassName,
			    "invalid descriptor for field " + fname);
		    ex.initCause(e);
		    throw ex;
		}
	    }
	    primDataSize = 0;
	    numObjFields = 0;
	    int firstObjIndex = -1;

	    for (int i = 0, l = fields.length; i < l; i++) {
		ObjectField f = (ObjectField) fields[i];
		switch (f.getTypeCode()) {
		    case 'Z':
		    case 'B':
			f.setOffset(primDataSize++);
			break;

		    case 'C':
		    case 'S':
			f.setOffset(primDataSize);
			primDataSize += 2;
			break;

		    case 'I':
		    case 'F':
			f.setOffset(primDataSize);
			primDataSize += 4;
			break;

		    case 'J':
		    case 'D':
			f.setOffset(primDataSize);
			primDataSize += 8;
			break;

		    case '[':
		    case 'L':
			f.setOffset(numObjFields++);
			if (firstObjIndex == -1) {
			    firstObjIndex = i;
			}
			break;

		    default:
			throw new InternalError();
		}
	    }
	    if (firstObjIndex != -1
		    && firstObjIndex + numObjFields != fields.length) {
		throw new InvalidClassException(fullyQualifiedClassName, "illegal field order");
	    }
	}

	static class ObjectField extends ObjectStreamField {

	    private final Field field;
	    private boolean unshared;

	    static String checkName(String name) {
		if (name == null) {
		    throw new NullPointerException();
		}
		return name;
	    }

	    ObjectField(String name, Class<?> type) {
		this(name, type, false);
	    }

	    ObjectField(String name, Class<?> type, boolean unshared) {
		super(checkName(name), type, unshared);
		field = null;
		this.unshared = unshared;
	    }

	    static Class<?> getType(String signature) {
		if (signature ==  null) throw new IllegalArgumentException("illegal signature, cannot be null");
		switch (signature.charAt(0)) {
		    case 'B':
			return Byte.TYPE;
		    case 'C':
			return Character.TYPE;
		    case 'D':
			return Double.TYPE;
		    case 'F':
			return Float.TYPE;
		    case 'I':
			return Integer.TYPE;
		    case 'J':
			return Long.TYPE;
		    case 'L':
		    case '[':
			return Object.class;
		    case 'S':
			return Short.TYPE;
		    case 'Z':
			return Boolean.TYPE;
		    default:
			throw new IllegalArgumentException("illegal signature: " + signature);
		}
	    }

	    /**
	     * Creates an ObjectField representing a field with the given name,
	     * signature and unshared setting.
	     */
	    ObjectField(String name, String signature, boolean unshared) {
		this(name, getType(signature), unshared);
	    }

	    static Class<?> type(Class<?> ftype, boolean showType) {
		return (showType || ftype.isPrimitive()) ? ftype : Object.class;
	    }

	    /**
	     * Creates an ObjectField representing the given field with the
	     * specified unshared setting. For compatibility with the behavior of
	     * earlier serialization implementations, a "showType" parameter is
	     * necessary to govern whether or not a getType() call on this
	     * ObjectField (if non-primitive) will return Object.class (as opposed
	     * to a more specific reference type).
	     */
	    ObjectField(Field field, boolean unshared, boolean showType) {
		this(field.getName(), type(field.getType(), showType), unshared);
	    }

	    /**
	     * Returns field represented by this ObjectStreamField, or null if
	     * ObjectStreamField is not associated with an actual field.
	     */
	    Field getField() {
		return field;
	    }

	    /**
	     * Returns boolean value indicating whether or not the serializable
	     * field represented by this ObjectStreamField instance is unshared.
	     *
	     * @return {@code true} if this field is unshared
	     *
	     * @since 1.4
	     */
	    @Override
	    public boolean isUnshared() {
		return unshared;
	    }

	    void setUnshared(boolean unshared) {
		this.unshared = unshared;
	    }

	    @Override
	    public void setOffset(int offset) {
		super.setOffset(offset);
	    }

    //	void resolve(ClassLoader loader) {
    //	    String typeString = getTypeString();
    //	    if (typeString == null && isPrimitive()){
    //		// primitive type declared in a serializable class
    //		typeString = String.valueOf(getTypeCode());
    //	    }
    //
    //	    if (typeString.length() == 1) {
    //		if (defaultResolve()) {
    //		    return;
    //		}
    //	    }
    //
    //	    String className = typeString.replace('/', '.');
    //	    if (className.charAt(0) == 'L') {
    //		// remove L and ;
    //		className = className.substring(1, className.length() - 1);
    //	    }
    //	    try {
    //		Class<?> cl = Class.forName(className, false, loader);
    //		type = (cl.getClassLoader() == null) ? cl
    //			: new WeakReference<Class<?>>(cl);
    //	    } catch (ClassNotFoundException e) {
    //		// Ignored
    //	    }
    //	}
    //	/**
    //	 * Resolves typeString into type. Returns true if the type is primitive
    //	 * and false otherwise.
    //	 */
    //	private boolean defaultResolve() {
    //	    String typeString = getTypeString();
    //	    switch (typeString.charAt(0)) {
    //		case 'I':
    //		    type = Integer.TYPE;
    //		    return true;
    //		case 'B':
    //		    type = Byte.TYPE;
    //		    return true;
    //		case 'C':
    //		    type = Character.TYPE;
    //		    return true;
    //		case 'S':
    //		    type = Short.TYPE;
    //		    return true;
    //		case 'Z':
    //		    type = Boolean.TYPE;
    //		    return true;
    //		case 'J':
    //		    type = Long.TYPE;
    //		    return true;
    //		case 'F':
    //		    type = Float.TYPE;
    //		    return true;
    //		case 'D':
    //		    type = Double.TYPE;
    //		    return true;
    //		default:
    //		    type = Object.class;
    //		    return false;
    //	    }
    //	}
	}

	static class ClassDescriptorConversionObjectInputStream extends ObjectInputStream{
	    ClassDescriptorConversionObjectInputStream(InputStream input) throws IOException{
		super(input);
	    }

	    @Override
	    public ObjectStreamClass readClassDescriptor() 
		    throws IOException, ClassNotFoundException{
		return super.readClassDescriptor();
	    }
	}

	static class ClassDescriptorConversionObjectOutputStream extends ObjectOutputStream{
	    ClassDescriptorConversionObjectOutputStream(OutputStream output) throws IOException{
		super(output);
	    }

	    @Override
	    public void writeClassDescriptor(ObjectStreamClass o) throws IOException{
		super.writeClassDescriptor(o);
	    }
	}
    }
    
    // A slot is a field plus its value
    static class ObjectSlot {

	// Field descriptor
	ObjectStreamField field;

	// Actual value this emulated field holds
	Object fieldValue;

	boolean booleanValue;
	byte byteValue;
	char charValue;
	short shortValue;
	int intValue;
	long longValue;
	float floatValue;
	double doubleValue;

	// If this field has a default value (true) or something has been
	// assigned (false)
	boolean defaulted = true;

	/**
	 * Returns the descriptor for this emulated field.
	 * 
	 * @return the field descriptor
	 */
	public ObjectStreamField getField() {
	    return field;
	}

	/**
	 * Returns the value held by this emulated field.
	 * 
	 * @return the field value
	 */
	public Object getFieldValue() {
	    return fieldValue;
	}
    }
    
    static class EmulatedFields {

	// The collection of slots the receiver represents
	private ObjectSlot[] slotsToSerialize;

	private ObjectStreamField[] declaredFields;

	/**
	 * Constructs a new instance of EmulatedFields.
	 * 
	 * @param fields
	 *            an array of ObjectStreamFields, which describe the fields to
	 *            be emulated (names, types, etc).
	 * @param declared
	 *            an array of ObjectStreamFields, which describe the declared
	 *            fields.
	 */
	public EmulatedFields(
		ObjectStreamField[] declared) {
	    super();
	    // We assume the slots are already sorted in the right shape for dumping
	    buildSlots(declared);
	    declaredFields = declared;
	}

	/**
	 * Build emulated slots that correspond to emulated fields. A slot is a
	 * field descriptor (ObjectStreamField) plus the actual value it holds.
	 * 
	 * @param fields
	 *            an array of ObjectStreamField, which describe the fields to be
	 *            emulated (names, types, etc).
	 */
	private void buildSlots(ObjectStreamField[] fields) {
	    slotsToSerialize = new ObjectSlot[fields.length];
	    for (int i = 0; i < fields.length; i++) {
		ObjectSlot s = new ObjectSlot();
		slotsToSerialize[i] = s;
		s.field = fields[i];
	    }
	    // We assume the slots are already sorted in the right shape for dumping
	}

	/**
	 * Returns {@code true} indicating the field called {@code name} has not had
	 * a value explicitly assigned and that it still holds a default value for
	 * its type, or {@code false} indicating that the field named has been
	 * assigned a value explicitly.
	 * 
	 * @param name
	 *            the name of the field to test.
	 * @return {@code true} if {@code name} still holds its default value,
	 *         {@code false} otherwise
	 * 
	 * @throws IllegalArgumentException
	 *             if {@code name} is {@code null}
	 */
	public boolean defaulted(String name) throws IllegalArgumentException {
	    ObjectSlot slot = findSlot(name, null);
	    if (slot == null) {
		throw new IllegalArgumentException();
	    }
	    return slot.defaulted;
	}

	/**
	 * Finds and returns an ObjectSlot that corresponds to a field named {@code
	 * fieldName} and type {@code fieldType}. If the field type {@code
	 * fieldType} corresponds to a primitive type, the field type has to match
	 * exactly or {@code null} is returned. If the field type {@code fieldType}
	 * corresponds to an object type, the field type has to be compatible in
	 * terms of assignment, or null is returned. If {@code fieldType} is {@code
	 * null}, no such compatibility checking is performed and the slot is
	 * returned.
	 * 
	 * @param fieldName
	 *            the name of the field to find
	 * @param fieldType
	 *            the type of the field. This will be used to test
	 *            compatibility. If {@code null}, no testing is done, the
	 *            corresponding slot is returned.
	 * @return the object slot, or {@code null} if there is no field with that
	 *         name, or no compatible field (relative to {@code fieldType})
	 */
	private ObjectSlot findSlot(String fieldName, Class<?> fieldType) {
	    boolean isPrimitive = fieldType != null && fieldType.isPrimitive();

	    for (int i = 0; i < slotsToSerialize.length; i++) {
		ObjectSlot slot = slotsToSerialize[i];
		if (slot.field.getName().equals(fieldName)) {
		    if (isPrimitive) {
			// Looking for a primitive type field. Types must match
			// *exactly*
			if (slot.field.getType() == fieldType) {
			    return slot;
			}
		    } else {
			// Looking for a non-primitive type field.
			if (fieldType == null) {
			    return slot; // Null means we take anything
			}
			// Types must be compatible (assignment)
			if (slot.field.getType().isAssignableFrom(fieldType)) {
			    return slot;
			}
		    }
		}
	    }

	    if (declaredFields != null) {
		for (int i = 0; i < declaredFields.length; i++) {
		    ObjectStreamField field = declaredFields[i];
		    if (field.getName().equals(fieldName)) {
			if (isPrimitive ? field.getType() == fieldType
				: fieldType == null
					|| field.getType().isAssignableFrom(
						fieldType)) {
			    ObjectSlot slot = new ObjectSlot();
			    slot.field = field;
			    slot.defaulted = true;
			    return slot;
			}
		    }
		}
	    }
	    return null;
	}

	/**
	 * Finds and returns the byte value of a given field named {@code name}
	 * in the receiver. If the field has not been assigned any value yet, the
	 * default value {@code defaultValue} is returned instead.
	 * 
	 * @param name
	 *            the name of the field to find.
	 * @param defaultValue
	 *            return value in case the field has not been assigned to yet.
	 * @return the value of the given field if it has been assigned, the default
	 *         value otherwise.
	 * 
	 * @throws IllegalArgumentException
	 *             if the corresponding field can not be found.
	 */
	public byte get(String name, byte defaultValue)
		throws IllegalArgumentException {
	    ObjectSlot slot = findSlot(name, Byte.TYPE);
	    // if not initialized yet, we give the default value
	    if (slot == null) {
		throw new IllegalArgumentException();
	    }
	    return slot.defaulted ? defaultValue : slot.byteValue;
	}

	/**
	 * Finds and returns the char value of a given field named {@code name} in the
	 * receiver. If the field has not been assigned any value yet, the default
	 * value {@code defaultValue} is returned instead.
	 * 
	 * @param name
	 *            the name of the field to find.
	 * @param defaultValue
	 *            return value in case the field has not been assigned to yet.
	 * @return the value of the given field if it has been assigned, the default
	 *         value otherwise.
	 * 
	 * @throws IllegalArgumentException
	 *             if the corresponding field can not be found.
	 */
	public char get(String name, char defaultValue)
		throws IllegalArgumentException {
	    ObjectSlot slot = findSlot(name, Character.TYPE);
	    // if not initialized yet, we give the default value
	    if (slot == null) {
		throw new IllegalArgumentException();
	    }
	    return slot.defaulted ? defaultValue : slot.charValue;
	}

	/**
	 * Finds and returns the double value of a given field named {@code name}
	 * in the receiver. If the field has not been assigned any value yet, the
	 * default value {@code defaultValue} is returned instead.
	 * 
	 * @param name
	 *            the name of the field to find.
	 * @param defaultValue
	 *            return value in case the field has not been assigned to yet.
	 * @return the value of the given field if it has been assigned, the default
	 *         value otherwise.
	 * 
	 * @throws IllegalArgumentException
	 *             if the corresponding field can not be found.
	 */
	public double get(String name, double defaultValue)
		throws IllegalArgumentException {
	    ObjectSlot slot = findSlot(name, Double.TYPE);
	    // if not initialized yet, we give the default value
	    if (slot == null) {
		throw new IllegalArgumentException();
	    }
	    return slot.defaulted ? defaultValue : slot.doubleValue;
	}

	/**
	 * Finds and returns the float value of a given field named {@code name} in
	 * the receiver. If the field has not been assigned any value yet, the
	 * default value {@code defaultValue} is returned instead.
	 * 
	 * @param name
	 *            the name of the field to find.
	 * @param defaultValue
	 *            return value in case the field has not been assigned to yet.
	 * @return the value of the given field if it has been assigned, the default
	 *         value otherwise.
	 * 
	 * @throws IllegalArgumentException
	 *             if the corresponding field can not be found.
	 */
	public float get(String name, float defaultValue)
		throws IllegalArgumentException {
	    ObjectSlot slot = findSlot(name, Float.TYPE);
	    // if not initialized yet, we give the default value
	    if (slot == null) {
		throw new IllegalArgumentException();
	    }
	    return slot.defaulted ? defaultValue : slot.floatValue;
	}

	/**
	 * Finds and returns the int value of a given field named {@code name} in the
	 * receiver. If the field has not been assigned any value yet, the default
	 * value {@code defaultValue} is returned instead.
	 * 
	 * @param name
	 *            the name of the field to find.
	 * @param defaultValue
	 *            return value in case the field has not been assigned to yet.
	 * @return the value of the given field if it has been assigned, the default
	 *         value otherwise.
	 * 
	 * @throws IllegalArgumentException
	 *             if the corresponding field can not be found.
	 */
	public int get(String name, int defaultValue)
		throws IllegalArgumentException {
	    ObjectSlot slot = findSlot(name, Integer.TYPE);
	    // if not initialized yet, we give the default value
	    if (slot == null) {
		throw new IllegalArgumentException();
	    }
	    return slot.defaulted ? defaultValue : slot.intValue;
	}

	/**
	 * Finds and returns the long value of a given field named {@code name} in the
	 * receiver. If the field has not been assigned any value yet, the default
	 * value {@code defaultValue} is returned instead.
	 * 
	 * @param name
	 *            the name of the field to find.
	 * @param defaultValue
	 *            return value in case the field has not been assigned to yet.
	 * @return the value of the given field if it has been assigned, the default
	 *         value otherwise.
	 * 
	 * @throws IllegalArgumentException
	 *             if the corresponding field can not be found.
	 */
	public long get(String name, long defaultValue)
		throws IllegalArgumentException {
	    ObjectSlot slot = findSlot(name, Long.TYPE);
	    // if not initialized yet, we give the default value
	    if (slot == null) {
		throw new IllegalArgumentException();
	    }
	    return slot.defaulted ? defaultValue : slot.longValue;
	}

	/**
	 * Finds and returns the Object value of a given field named {@code name} in
	 * the receiver. If the field has not been assigned any value yet, the
	 * default value {@code defaultValue} is returned instead.
	 * 
	 * @param name
	 *            the name of the field to find.
	 * @param defaultValue
	 *            return value in case the field has not been assigned to yet.
	 * @return the value of the given field if it has been assigned, the default
	 *         value otherwise.
	 * 
	 * @throws IllegalArgumentException
	 *             if the corresponding field can not be found.
	 */
	public Object get(String name, Object defaultValue)
		throws IllegalArgumentException {
	    ObjectSlot slot = findSlot(name, null);
	    // if not initialized yet, we give the default value
	    if (slot == null || slot.field.getType().isPrimitive()) {
		throw new IllegalArgumentException();
	    }
	    return slot.defaulted ? defaultValue : slot.fieldValue;
	}

	/**
	 * Finds and returns the short value of a given field named {@code name} in
	 * the receiver. If the field has not been assigned any value yet, the
	 * default value {@code defaultValue} is returned instead.
	 * 
	 * @param name
	 *            the name of the field to find.
	 * @param defaultValue
	 *            return value in case the field has not been assigned to yet.
	 * @return the value of the given field if it has been assigned, the default
	 *         value otherwise.
	 * 
	 * @throws IllegalArgumentException
	 *             if the corresponding field can not be found.
	 */
	public short get(String name, short defaultValue)
		throws IllegalArgumentException {
	    ObjectSlot slot = findSlot(name, Short.TYPE);
	    // if not initialized yet, we give the default value
	    if (slot == null) {
		throw new IllegalArgumentException();
	    }
	    return slot.defaulted ? defaultValue : slot.shortValue;
	}

	/**
	 * Finds and returns the boolean value of a given field named {@code name} in
	 * the receiver. If the field has not been assigned any value yet, the
	 * default value {@code defaultValue} is returned instead.
	 * 
	 * @param name
	 *            the name of the field to find.
	 * @param defaultValue
	 *            return value in case the field has not been assigned to yet.
	 * @return the value of the given field if it has been assigned, the default
	 *         value otherwise.
	 * 
	 * @throws IllegalArgumentException
	 *             if the corresponding field can not be found.
	 */
	public boolean get(String name, boolean defaultValue)
		throws IllegalArgumentException {
	    ObjectSlot slot = findSlot(name, Boolean.TYPE);
	    // if not initialized yet, we give the default value
	    if (slot == null) {
		throw new IllegalArgumentException();
	    }
	    return slot.defaulted ? defaultValue : slot.booleanValue;
	}

	/**
	 * Find and set the byte value of a given field named {@code name} in the
	 * receiver.
	 * 
	 * @param name
	 *            the name of the field to set.
	 * @param value
	 *            new value for the field.
	 * 
	 * @throws IllegalArgumentException
	 *             if the corresponding field can not be found.
	 */
	public void put(String name, byte value) throws IllegalArgumentException {
	    ObjectSlot slot = findSlot(name, Byte.TYPE);
	    if (slot == null) {
		throw new IllegalArgumentException();
	    }
	    slot.byteValue = value;
	    slot.defaulted = false; // No longer default value
	}

	/**
	 * Find and set the char value of a given field named {@code name} in the
	 * receiver.
	 * 
	 * @param name
	 *            the name of the field to set.
	 * @param value
	 *            new value for the field.
	 * 
	 * @throws IllegalArgumentException
	 *             if the corresponding field can not be found.
	 */
	public void put(String name, char value) throws IllegalArgumentException {
	    ObjectSlot slot = findSlot(name, Character.TYPE);
	    if (slot == null) {
		throw new IllegalArgumentException();
	    }
	    slot.charValue = value;
	    slot.defaulted = false; // No longer default value
	}

	/**
	 * Find and set the double value of a given field named {@code name} in the
	 * receiver.
	 * 
	 * @param name
	 *            the name of the field to set.
	 * @param value
	 *            new value for the field.
	 * 
	 * @throws IllegalArgumentException
	 *             if the corresponding field can not be found.
	 */
	public void put(String name, double value) throws IllegalArgumentException {
	    ObjectSlot slot = findSlot(name, Double.TYPE);
	    if (slot == null) {
		throw new IllegalArgumentException();
	    }
	    slot.doubleValue = value;
	    slot.defaulted = false; // No longer default value
	}

	/**
	 * Find and set the float value of a given field named {@code name} in the
	 * receiver.
	 * 
	 * @param name
	 *            the name of the field to set.
	 * @param value
	 *            new value for the field.
	 * 
	 * @throws IllegalArgumentException
	 *             if the corresponding field can not be found.
	 */
	public void put(String name, float value) throws IllegalArgumentException {
	    ObjectSlot slot = findSlot(name, Float.TYPE);
	    if (slot == null) {
		throw new IllegalArgumentException();
	    }
	    slot.floatValue = value;
	    slot.defaulted = false; // No longer default value
	}

	/**
	 * Find and set the int value of a given field named {@code name} in the
	 * receiver.
	 * 
	 * @param name
	 *            the name of the field to set.
	 * @param value
	 *            new value for the field.
	 * 
	 * @throws IllegalArgumentException
	 *             if the corresponding field can not be found.
	 */
	public void put(String name, int value) throws IllegalArgumentException {
	    ObjectSlot slot = findSlot(name, Integer.TYPE);
	    if (slot == null) {
		throw new IllegalArgumentException();
	    }
	    slot.intValue = value;
	    slot.defaulted = false; // No longer default value
	}

	/**
	 * Find and set the long value of a given field named {@code name} in the
	 * receiver.
	 * 
	 * @param name
	 *            the name of the field to set.
	 * @param value
	 *            new value for the field.
	 * 
	 * @throws IllegalArgumentException
	 *             if the corresponding field can not be found.
	 */
	public void put(String name, long value) throws IllegalArgumentException {
	    ObjectSlot slot = findSlot(name, Long.TYPE);
	    if (slot == null) {
		throw new IllegalArgumentException();
	    }
	    slot.longValue = value;
	    slot.defaulted = false; // No longer default value
	}

	/**
	 * Find and set the Object value of a given field named {@code name} in the
	 * receiver.
	 * 
	 * @param name
	 *            the name of the field to set.
	 * @param value
	 *            new value for the field.
	 * 
	 * @throws IllegalArgumentException
	 *             if the corresponding field can not be found.
	 */
	public void put(String name, Object value) throws IllegalArgumentException {
	    Class<?> valueClass = null;
	    if (value != null) {
		valueClass = value.getClass();
	    }
	    ObjectSlot slot = findSlot(name, valueClass);
	    if (slot == null) {
		throw new IllegalArgumentException();
	    }
	    slot.fieldValue = value;
	    slot.defaulted = false; // No longer default value
	}

	/**
	 * Find and set the short value of a given field named {@code name} in the
	 * receiver.
	 * 
	 * @param name
	 *            the name of the field to set.
	 * @param value
	 *            new value for the field.
	 * 
	 * @throws IllegalArgumentException
	 *             if the corresponding field can not be found.
	 */
	public void put(String name, short value) throws IllegalArgumentException {
	    ObjectSlot slot = findSlot(name, Short.TYPE);
	    if (slot == null) {
		throw new IllegalArgumentException();
	    }
	    slot.shortValue = value;
	    slot.defaulted = false; // No longer default value
	}

	/**
	 * Find and set the boolean value of a given field named {@code name} in the
	 * receiver.
	 * 
	 * @param name
	 *            the name of the field to set.
	 * @param value
	 *            new value for the field.
	 * 
	 * @throws IllegalArgumentException
	 *             if the corresponding field can not be found.
	 */
	public void put(String name, boolean value) throws IllegalArgumentException {
	    ObjectSlot slot = findSlot(name, Boolean.TYPE);
	    if (slot == null) {
		throw new IllegalArgumentException();
	    }
	    slot.booleanValue = value;
	    slot.defaulted = false; // No longer default value
	}

	/**
	 * Return the array of ObjectSlot the receiver represents.
	 * 
	 * @return array of ObjectSlot the receiver represents.
	 */
	public ObjectSlot[] slots() {
	    return slotsToSerialize;
	}
    }
    
    static class EmulatedFieldsForLoading extends GetField {
	// The class descriptor with the declared fields the receiver emulates
	private final ObjectStreamClass streamClass;

	// The actual representation, with a more powerful API (set&get)
	private final EmulatedFields emulatedFields;

	/**
	 * Constructs a new instance of EmulatedFieldsForLoading.
	 * 
	 * @param streamClass
	 *            an ObjectStreamClass, defining the class for which to emulate
	 *            fields.
	 */
	EmulatedFieldsForLoading(ObjectStreamClass streamClass) {
	    super();
	    this.streamClass = streamClass;
	    emulatedFields = new EmulatedFields(streamClass.getFields()); // Get Fields copies, consider not copying for efficiency?
	}

	/**
	 * Return a boolean indicating if the field named <code>name</code> has
	 * been assigned a value explicitly (false) or if it still holds a default
	 * value for the type (true) because it hasn't been assigned to yet.
	 * 
	 * @param name
	 *            A String, the name of the field to test
	 * @return <code>true</code> if the field holds it default value,
	 *         <code>false</code> otherwise.
	 * 
	 * @throws IOException
	 *             If an IO error occurs
	 * @throws IllegalArgumentException
	 *             If the corresponding field can not be found.
	 */
	@Override
	public boolean defaulted(String name) throws IOException,
		IllegalArgumentException {
	    return emulatedFields.defaulted(name);
	}

	/**
	 * Return the actual EmulatedFields instance used by the receiver. We have
	 * the actual work in a separate class so that the code can be shared. The
	 * receiver has to be of a subclass of GetField.
	 * 
	 * @return array of ObjectSlot the receiver represents.
	 */
	EmulatedFields emulatedFields() {
	    return emulatedFields;
	}

	/**
	 * Find and return the byte value of a given field named <code>name</code>
	 * in the receiver. If the field has not been assigned any value yet, the
	 * default value <code>defaultValue</code> is returned instead.
	 * 
	 * @param name
	 *            A String, the name of the field to find
	 * @param defaultValue
	 *            Return value in case the field has not been assigned to yet.
	 * @return the value of the given field if it has been assigned, or the
	 *         default value otherwise
	 * 
	 * @throws IOException
	 *             If an IO error occurs
	 * @throws IllegalArgumentException
	 *             If the corresponding field can not be found.
	 */
	@Override
	public byte get(String name, byte defaultValue) throws IOException,
		IllegalArgumentException {
	    return emulatedFields.get(name, defaultValue);
	}

	/**
	 * Find and return the char value of a given field named <code>name</code>
	 * in the receiver. If the field has not been assigned any value yet, the
	 * default value <code>defaultValue</code> is returned instead.
	 * 
	 * @param name
	 *            A String, the name of the field to find
	 * @param defaultValue
	 *            Return value in case the field has not been assigned to yet.
	 * @return the value of the given field if it has been assigned, or the
	 *         default value otherwise
	 * 
	 * @throws IOException
	 *             If an IO error occurs
	 * @throws IllegalArgumentException
	 *             If the corresponding field can not be found.
	 */
	@Override
	public char get(String name, char defaultValue) throws IOException,
		IllegalArgumentException {
	    return emulatedFields.get(name, defaultValue);
	}

	/**
	 * Find and return the double value of a given field named <code>name</code>
	 * in the receiver. If the field has not been assigned any value yet, the
	 * default value <code>defaultValue</code> is returned instead.
	 * 
	 * @param name
	 *            A String, the name of the field to find
	 * @param defaultValue
	 *            Return value in case the field has not been assigned to yet.
	 * @return the value of the given field if it has been assigned, or the
	 *         default value otherwise
	 * 
	 * @throws IOException
	 *             If an IO error occurs
	 * @throws IllegalArgumentException
	 *             If the corresponding field can not be found.
	 */
	@Override
	public double get(String name, double defaultValue) throws IOException,
		IllegalArgumentException {
	    return emulatedFields.get(name, defaultValue);
	}

	/**
	 * Find and return the float value of a given field named <code>name</code>
	 * in the receiver. If the field has not been assigned any value yet, the
	 * default value <code>defaultValue</code> is returned instead.
	 * 
	 * @param name
	 *            A String, the name of the field to find
	 * @param defaultValue
	 *            Return value in case the field has not been assigned to yet.
	 * @return the value of the given field if it has been assigned, or the
	 *         default value otherwise
	 * 
	 * @throws IOException
	 *             If an IO error occurs
	 * @throws IllegalArgumentException
	 *             If the corresponding field can not be found.
	 */
	@Override
	public float get(String name, float defaultValue) throws IOException,
		IllegalArgumentException {
	    return emulatedFields.get(name, defaultValue);
	}

	/**
	 * Find and return the int value of a given field named <code>name</code>
	 * in the receiver. If the field has not been assigned any value yet, the
	 * default value <code>defaultValue</code> is returned instead.
	 * 
	 * @param name
	 *            A String, the name of the field to find
	 * @param defaultValue
	 *            Return value in case the field has not been assigned to yet.
	 * @return the value of the given field if it has been assigned, or the
	 *         default value otherwise
	 * 
	 * @throws IOException
	 *             If an IO error occurs
	 * @throws IllegalArgumentException
	 *             If the corresponding field can not be found.
	 */
	@Override
	public int get(String name, int defaultValue) throws IOException,
		IllegalArgumentException {
	    return emulatedFields.get(name, defaultValue);
	}

	/**
	 * Find and return the long value of a given field named <code>name</code>
	 * in the receiver. If the field has not been assigned any value yet, the
	 * default value <code>defaultValue</code> is returned instead.
	 * 
	 * @param name
	 *            A String, the name of the field to find
	 * @param defaultValue
	 *            Return value in case the field has not been assigned to yet.
	 * @return the value of the given field if it has been assigned, or the
	 *         default value otherwise
	 * 
	 * @throws IOException
	 *             If an IO error occurs
	 * @throws IllegalArgumentException
	 *             If the corresponding field can not be found.
	 */
	@Override
	public long get(String name, long defaultValue) throws IOException,
		IllegalArgumentException {
	    return emulatedFields.get(name, defaultValue);
	}

	/**
	 * Find and return the Object value of a given field named <code>name</code>
	 * in the receiver. If the field has not been assigned any value yet, the
	 * default value <code>defaultValue</code> is returned instead.
	 * 
	 * @param name
	 *            A String, the name of the field to find
	 * @param defaultValue
	 *            Return value in case the field has not been assigned to yet.
	 * @return the value of the given field if it has been assigned, or the
	 *         default value otherwise
	 * 
	 * @throws IOException
	 *             If an IO error occurs
	 * @throws IllegalArgumentException
	 *             If the corresponding field can not be found.
	 */
	@Override
	public Object get(String name, Object defaultValue) throws IOException,
		IllegalArgumentException {
	    return emulatedFields.get(name, defaultValue);
	}

	/**
	 * Find and return the short value of a given field named <code>name</code>
	 * in the receiver. If the field has not been assigned any value yet, the
	 * default value <code>defaultValue</code> is returned instead.
	 * 
	 * @param name
	 *            A String, the name of the field to find
	 * @param defaultValue
	 *            Return value in case the field has not been assigned to yet.
	 * @return the value of the given field if it has been assigned, or the
	 *         default value otherwise
	 * 
	 * @throws IOException
	 *             If an IO error occurs
	 * @throws IllegalArgumentException
	 *             If the corresponding field can not be found.
	 */
	@Override
	public short get(String name, short defaultValue) throws IOException,
		IllegalArgumentException {
	    return emulatedFields.get(name, defaultValue);
	}

	/**
	 * Find and return the boolean value of a given field named
	 * <code>name</code> in the receiver. If the field has not been assigned
	 * any value yet, the default value <code>defaultValue</code> is returned
	 * instead.
	 * 
	 * @param name
	 *            A String, the name of the field to find
	 * @param defaultValue
	 *            Return value in case the field has not been assigned to yet.
	 * @return the value of the given field if it has been assigned, or the
	 *         default value otherwise
	 * 
	 * @throws IOException
	 *             If an IO error occurs
	 * @throws IllegalArgumentException
	 *             If the corresponding field can not be found.
	 */
	@Override
	public boolean get(String name, boolean defaultValue) throws IOException,
		IllegalArgumentException {
	    return emulatedFields.get(name, defaultValue);
	}

	/**
	 * Return the class descriptor for which the emulated fields are defined.
	 * 
	 * @return ObjectStreamClass The class descriptor for which the emulated
	 *         fields are defined.
	 */
	@Override
	public ObjectStreamClass getObjectStreamClass() {
	    return streamClass;
	}
    }
}
