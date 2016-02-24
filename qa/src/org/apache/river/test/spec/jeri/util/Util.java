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
package org.apache.river.test.spec.jeri.util;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.ObjectOutput;
import java.lang.reflect.Method;
import java.security.DigestOutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import net.jini.io.MarshalInputStream;
import net.jini.io.MarshalOutputStream;

public class Util {

    /**
     * Computes the "method hash" of a remote method, <code>m</code>.  The
     * method hash is a <code>long</code> containing the first 64 bits of the
     * SHA digest from the UTF encoded string of the method name followed by
     * its "method descriptor".  See section 4.3.3 of The Java(TM) Virtual
     * Machine Specification for the definition of a "method descriptor".
     *
     * @param	m remote method
     * @return	the method hash
     */
    public static long computeMethodHash(Method m) {
	long hash = 0;
	ByteArrayOutputStream sink = new ByteArrayOutputStream(127);
	try {
	    MessageDigest md = MessageDigest.getInstance("SHA");
	    DataOutputStream out = new DataOutputStream(
		new DigestOutputStream(sink, md));

	    String s = getMethodNameAndDescriptor(m);
	    out.writeUTF(s);

	    // use only the first 64 bits of the digest for the hash
	    out.flush();
	    byte hasharray[] = md.digest();
	    for (int i = 0; i < Math.min(8, hasharray.length); i++) {
		hash += ((long) (hasharray[i] & 0xFF)) << (i * 8);
	    }
	} catch (IOException ignore) {
	    /* can't happen, but be deterministic anyway. */
	    hash = -1;
	} catch (NoSuchAlgorithmException complain) {
	    throw new SecurityException(complain.getMessage());
	}
	return hash;
    }

    /**
     * Returns a string consisting of the given method's name followed by
     * its "method descriptor", as appropriate for use in the computation
     * of the "method hash".
     *
     * See section 4.3.3 of The Java(TM) Virtual Machine Specification for
     * the definition of a "method descriptor".
     */
    private static String getMethodNameAndDescriptor(Method m) {
	StringBuffer desc = new StringBuffer(m.getName());
	desc.append('(');
	Class[] paramTypes = m.getParameterTypes();
	for (int i = 0; i < paramTypes.length; i++) {
	    desc.append(getTypeDescriptor(paramTypes[i]));
	}
	desc.append(')');
	Class returnType = m.getReturnType();
	if (returnType == void.class) {	// optimization: handle void here
	    desc.append('V');
	} else {
	    desc.append(getTypeDescriptor(returnType));
	}
	return desc.toString();
    }

    /**
     * Returns the descriptor of a particular type, as appropriate for either
     * a parameter type or return type in a method descriptor.
     */
    private static String getTypeDescriptor(Class type) {
	if (type.isPrimitive()) {
	    if (type == int.class) {
		return "I";
	    } else if (type == boolean.class) {
		return "Z";
	    } else if (type == byte.class) {
		return "B";
	    } else if (type == char.class) {
		return "C";
	    } else if (type == short.class) {
		return "S";
	    } else if (type == long.class) {
		return "J";
	    } else if (type == float.class) {
		return "F";
	    } else if (type == double.class) {
		return "D";
	    } else if (type == void.class) {
		return "V";
	    } else {
		throw new Error("unrecognized primitive type: " + type);
	    }
	} else if (type.isArray()) {
	    /*
	     * According to JLS 20.3.2, the getName() method on Class does
	     * return the virtual machine type descriptor format for array
	     * classes (only); using that should be quicker than the otherwise
	     * obvious code:
	     *
	     *     return "[" + getTypeDescriptor(type.getComponentType());
	     */
	    return type.getName().replace('.', '/');
	} else {
	    return "L" + type.getName().replace('.', '/') + ";";
	}
    }

    /**
     * Unmarshals an object of type <code>type</code> from
     * <code>in</code>.
     *
     * @return an object of type <code>type</code>
     */
    public static Object unmarshalValue(Class type, MarshalInputStream in)
        throws IOException, ClassNotFoundException
    {
        if (type == void.class) {
            return null;
        } else if (type == Integer.class) {
            return new Integer(in.readInt());
        } else if (type == Boolean.class) {
            return new Boolean(in.readBoolean());
        } else if (type == Byte.class) {
            return new Byte(in.readByte());
        } else if (type == Character.class) {
            return new Character(in.readChar());
        } else if (type == Short.class) {
            return new Short(in.readShort());
        } else if (type == Long.class) {
            return new Long(in.readLong());
        } else if (type == Float.class) {
            return new Float(in.readFloat());
        } else if (type == Double.class) {
            return new Double(in.readDouble());
        } else {
            return in.readObject();
        }
    }

    /**
     * Marshals <code>value</code> to <code>out</code>.  If
     * <code>value</code> is a wrapper object for a primitive type,
     * then the primitive is extracted from the object and
     * written to the stream using the appropriate primitive
     * write method.
     */
    public static void marshalValue(Object value, ObjectOutput out)
        throws IOException
    {
        if (value == null) {
            out.writeObject(value);
            return;
        }

        Class type = value.getClass();
        if (type == Integer.class) {
            out.writeInt(((Integer) value).intValue());
        } else if (type == Boolean.class) {
            out.writeBoolean(((Boolean) value).booleanValue());
        } else if (type == Byte.class) {
            out.writeByte(((Byte) value).byteValue());
        } else if (type == Character.class) {
            out.writeChar(((Character) value).charValue());
        } else if (type == Short.class) {
            out.writeShort(((Short) value).shortValue());
        } else if (type == Long.class) {
            out.writeLong(((Long) value).longValue());
        } else if (type == Float.class) {
            out.writeFloat(((Float) value).floatValue());
        } else if (type == Double.class) {
            out.writeDouble(((Double) value).doubleValue());
        } else {
            out.writeObject(value);
        }
    }

}
