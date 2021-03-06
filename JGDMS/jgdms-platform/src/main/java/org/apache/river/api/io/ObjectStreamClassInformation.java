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
import java.io.InputStream;
import java.io.InvalidClassException;
import java.io.ObjectInput;
import java.io.ObjectInputStream;
import java.io.ObjectOutput;
import java.io.ObjectOutputStream;
import java.io.ObjectStreamClass;
import java.io.ObjectStreamConstants;
import java.io.ObjectStreamField;
import java.io.OutputStream;
import java.io.StreamCorruptedException;
import java.lang.reflect.Field;
import org.apache.river.impl.Messages;

/**
 * This class is a container for ObjectStreamClass information contained in an
 * ObjectInputStream,
 *
 */
class ObjectStreamClassInformation {

    /**
     * The resulting class descriptor is not fully functional; it can only be
     * used as input to the ObjectInputStream.resolveClass() and
     * ObjectStreamClass.initNonProxy() methods.
     */
    static ObjectStreamClass convert(ObjectStreamClassInformation o) 
	    throws IOException, ClassNotFoundException {
	ByteArrayOutputStream bao = new ByteArrayOutputStream();
	ObjectOutputStream dao = new ObjectOutputStream(bao);
	o.write(dao);
	dao.flush();
	byte[] bytes = bao.toByteArray();
	ClassDescriptorConversionObjectInputStream pois 
		= new ClassDescriptorConversionObjectInputStream(
			new ByteArrayInputStream(bytes)
		);
	return pois.readClassDescriptor();
    }

    static ObjectStreamClassInformation convert(ObjectStreamClass o) 
	    throws IOException, ClassNotFoundException {
	ByteArrayOutputStream bao = new ByteArrayOutputStream();
	ClassDescriptorConversionObjectOutputStream coos 
		= new ClassDescriptorConversionObjectOutputStream(bao);
	coos.writeClassDescriptor(o);
	coos.flush();
	byte[] bytes = bao.toByteArray();
	ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(bytes));
	ObjectStreamClassInformation result = new ObjectStreamClassInformation();
	result.read(in);
	result.readFields(in);
	return result;
    }
    
    String fullyQualifiedClassName;
    long serialVer;
    boolean externalizable;
    boolean serializable;
    boolean hasWriteObjectData;
    boolean hasBlockExternalData;
    boolean isEnum;
    ObjectStreamField[] fields;
    private int primDataSize;
    int numObjFields;

    @Override
    public String toString() {
	String endLine = "\n";
	StringBuilder b = new StringBuilder(512);
	b.append("Name: ").append(fullyQualifiedClassName).append(endLine).append("Externalizable? ").append(externalizable).append(endLine).append("Serializable? ").append(serializable).append(endLine).append("Has writeObject() data? ").append(hasWriteObjectData).append(endLine).append("Has block external data? ").append(hasBlockExternalData).append(endLine).append("Is Enum? ").append(isEnum).append(endLine);
	if (fields != null) {
	    for (int i = 0, l = fields.length; i < l; i++) {
		if (fields[i] != null) {
		    b.append("Field name: ").append(fields[i].getName()).append(endLine).append("Field Type Code: ").append(fields[i].getTypeCode()).append(endLine).append("Field offset: ").append(fields[i].getOffset()).append(endLine);
		}
	    }
	}
	b.append("Primitive data size: ").append(primDataSize).append(endLine).append("Number of Object fields: ").append(numObjFields).append(endLine);
	return b.toString();
    }

    /**
     * Writes non-proxy class descriptor information to given DataOutputStream.
     */
    void write(ObjectOutput out) throws IOException {
//        System.out.println(fullyQualifiedClassName);
//        System.out.println(serialVer);
	out.writeUTF(fullyQualifiedClassName);
	out.writeLong(serialVer);
	byte flags = 0;
	if (externalizable) {
	    flags |= ObjectStreamConstants.SC_EXTERNALIZABLE;
	    flags |= ObjectStreamConstants.SC_BLOCK_DATA; // Stream protocol version 1 isn't supported.
	} else if (serializable) {
//            System.out.println("Serializable");
	    flags |= ObjectStreamConstants.SC_SERIALIZABLE;
	}
	if (hasWriteObjectData) {
//            System.out.println("hasWriteObjectData");
	    flags |= ObjectStreamConstants.SC_WRITE_METHOD;
	}
	if (isEnum) {
//            System.out.println("isEnum");
	    flags |= ObjectStreamConstants.SC_ENUM;
	}
	out.writeByte(flags);
        int length = fields != null ? fields.length : 0;
	out.writeShort(length);
	for (int i = 0; i < length; i++) {
	    ObjectStreamField f = fields[i];
//            System.out.println(f);
	    out.writeByte(f.getTypeCode());
	    out.writeUTF(f.getName());
	    if (!f.isPrimitive()) {
		String typeString = f.getTypeString();
		if (typeString == null) {
		    out.writeByte(ObjectStreamConstants.TC_NULL);
		} else {
                    if (out instanceof ObjOutputStream){
                        ((ObjOutputStream)out).fieldTypeStringWritten(typeString);
                    }
		    out.writeByte(ObjectStreamConstants.TC_STRING);
		    out.writeUTF(typeString);
		}
	    }
	}
    }

    /**
     * Reads non-proxy class descriptor information from given DataInputStream.
     * 
     * Note this doesn't read the field descriptors.
     */
    void read(ObjectInput in) throws IOException {
	//    	System.out.println("read in class descriptor");
	fullyQualifiedClassName = in.readUTF();
	if (fullyQualifiedClassName.length() == 0) {
	    // luni.07 = The stream is corrupted
	    throw new IOException(Messages.getString("luni.07")); //$NON-NLS-1$
	}
	serialVer = in.readLong();
	byte flags = in.readByte();
	hasWriteObjectData = ((flags & ObjectStreamConstants.SC_WRITE_METHOD) != 0);
	hasBlockExternalData = ((flags & ObjectStreamConstants.SC_BLOCK_DATA) != 0);
	externalizable = ((flags & ObjectStreamConstants.SC_EXTERNALIZABLE) != 0);
	boolean sflag = (flags & ObjectStreamConstants.SC_SERIALIZABLE) != 0;
	if (externalizable && sflag) {
	    throw new InvalidClassException(fullyQualifiedClassName, "serializable and externalizable flags conflict");
	}
	serializable = externalizable || sflag;
	isEnum = ((flags & ObjectStreamConstants.SC_ENUM) != 0);
	if (isEnum && serialVer != 0L) {
	    throw new InvalidClassException(fullyQualifiedClassName, "enum descriptor has non-zero serialVersionUID: " + serialVer);
	}
    }

    /**
     * Reads in field descriptors.
     * 
     * @param in
     * @throws IOException
     * @throws ClassNotFoundException 
     */
    void readFields(ObjectInput in) throws IOException, ClassNotFoundException {
	//    	System.out.println("readFields");
	int numFields = in.readShort();
	if (isEnum && numFields != 0) {
	    throw new InvalidClassException(fullyQualifiedClassName, "enum descriptor has non-zero field count: " + numFields);
	}
	fields = ((numFields > 0) ? new ObjectField[numFields] : ObjectStreamClass.NO_FIELDS);
	for (int i = 0; i < numFields; i++) {
	    char tcode = (char) in.readByte();
	    String fname = in.readUTF();
	    String signature = null;
	    if ((tcode == 'L') || (tcode == '[')) {
		byte streamConstant = (in instanceof AtomicMarshalInputStream) ? ((AtomicMarshalInputStream) in).nextTC() : in.readByte();
		switch (streamConstant) {
		    case ObjectStreamConstants.TC_NULL:
			//    			System.out.println("TC_NULL");
			break;
		    case ObjectStreamConstants.TC_REFERENCE:
			//    			System.out.println("TC_REFERENCE");
			if (in instanceof AtomicMarshalInputStream) {
			    signature = (String) ((AtomicMarshalInputStream) in).readCyclicReference();
			} else {
			    throw new StreamCorruptedException("TC_REFERENCE is not supported for this stream");
			}
			break;
		    case ObjectStreamConstants.TC_STRING:
			//    			System.out.println("TC_STRING");
			if (in instanceof AtomicMarshalInputStream) {
			    signature = (String) ((AtomicMarshalInputStream) in).readNewString(isEnum);
			} else {
			    signature = in.readUTF();
			}
			break;
		    case ObjectStreamConstants.TC_LONGSTRING:
			//    			System.out.println("TC_LONGSTRING");
			if (in instanceof AtomicMarshalInputStream) {
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
		IOException ex = new InvalidClassException(fullyQualifiedClassName, "invalid descriptor for field " + fname);
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
	if (firstObjIndex != -1 && firstObjIndex + numObjFields != fields.length) {
	    throw new InvalidClassException(fullyQualifiedClassName, "illegal field order");
	}
    }

    /**
     * @return the fullyQualifiedClassName
     */
    public String getFullyQualifiedClassName() {
	return fullyQualifiedClassName;
    }

    /**
     * @return the serialVer
     */
    public long getSerialVer() {
	return serialVer;
    }

    /**
     * @return the externalizable
     */
    public boolean isExternalizable() {
	return externalizable;
    }

    boolean isSerializable() {
       return serializable;
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
	    if (signature == null) {
		throw new IllegalArgumentException("illegal signature, cannot be null");
	    }
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
    }

    static class ClassDescriptorConversionObjectInputStream extends ObjectInputStream {

	ClassDescriptorConversionObjectInputStream(InputStream input) throws IOException {
	    super(input);
	}

	@Override
	public ObjectStreamClass readClassDescriptor() throws IOException, ClassNotFoundException {
	    return super.readClassDescriptor();
	}
    }

    static class ClassDescriptorConversionObjectOutputStream extends ObjectOutputStream {

	ClassDescriptorConversionObjectOutputStream(OutputStream output) throws IOException {
	    super(output);
	}

	@Override
	public void writeClassDescriptor(ObjectStreamClass o) throws IOException {
	    super.writeClassDescriptor(o);
	}
    }

}
