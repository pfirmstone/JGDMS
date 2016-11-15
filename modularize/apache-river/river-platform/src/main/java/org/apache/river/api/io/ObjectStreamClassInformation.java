/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.apache.river.api.io;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InvalidClassException;
import java.io.ObjectInputStream;
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
	o.write(dao, true);
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
    /**
     * handle value representing null
     */
    private static final int NULL_HANDLE = -1;
    private String fullyQualifiedClassName;
    private long serialVer;
    private boolean externalizable;
    boolean serializable;
    boolean hasWriteObjectData;
    boolean hasBlockExternalData;
    boolean isEnum;
    ObjectStreamField[] fields;
    boolean hasHandle;
    int handle;
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
	b.append("Has Handle? ").append(hasHandle).append(endLine).append("Handle: ").append(handle).append(endLine).append("Primitive data size: ").append(primDataSize).append(endLine).append("Number of Object fields: ").append(numObjFields).append(endLine);
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
		    out.writeByte(ObjectStreamConstants.TC_NULL);
		} else if (hasHandle && !replaceHandleWithObject) {
		    out.writeByte(ObjectStreamConstants.TC_REFERENCE);
		    out.writeInt(handle);
		} else {
		    out.writeByte(ObjectStreamConstants.TC_STRING);
		    out.writeUTF(typeString);
		}
	    }
	}
    }

    /**
     * Reads non-proxy class descriptor information from given DataInputStream.
     */
    void read(ObjectInputStream in) throws IOException, ClassNotFoundException {
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
     * Reads a collection of field descriptors (name, type name, etc) for the
     * class descriptor {@code cDesc} (an {@code ObjectStreamClass})
     *
     * @param cDesc The class descriptor (an {@code ObjectStreamClass}) for
     * which to write field information
     *
     * @throws IOException If an IO exception happened when reading the field
     * descriptors.
     * @throws ClassNotFoundException If a class for one of the field types
     * could not be found
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
    void readFields(ObjectInputStream in) throws IOException {
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
