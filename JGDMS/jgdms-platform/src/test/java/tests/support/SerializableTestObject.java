/*
 * Copyright 2021 The Apache Software Foundation.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package tests.support;

import java.io.IOException;
import java.io.ObjectOutputStream;
import java.io.ObjectOutputStream.PutField;
import java.io.ObjectStreamField;
import java.io.Serializable;
import java.util.Arrays;
import java.util.Objects;
import org.apache.river.api.io.AtomicSerial;
import org.apache.river.api.io.AtomicSerial.GetArg;
import org.apache.river.api.io.AtomicSerial.PutArg;
import org.apache.river.api.io.AtomicSerial.SerialForm;

/**
 *
 * @author peter
 */
@AtomicSerial
public class SerializableTestObject implements Serializable {
    
    private static final long serialVersionUID = 1L;

    /**
     * Names of serial fields / arguments. Note how these names are unrelated to field
     * names. If we refactor field names, and rename them, the Strings
     * representing serial fields don't change and the serial form of the class
     * is not broken.
     */
    private static final String TEST_STR = "testString";
    private static final String TEST_ARRY = "testArray";
    private static final String TEST_INT = "testInt";
    private static final String TEST_BYTE = "testByte";
    private static final String TEST_BOOLEAN = "testBoolean";
    private static final String TEST_CHAR = "testChar";
    private static final String TEST_SHORT = "testShort";
    private static final String TEST_LONG = "testLong";
    private static final String TEST_FLOAT = "testFloat";
    private static final String TEST_DOUBLE = "testDouble";

    /**
     * serialPersistentFields.
     *
     * This method will be used by serialization frameworks to get names and
     * types of serial arguments. These will ensure type checking occurs during
     * de-serialization, fields will be de-serialized and created prior to the
     * instantiation of the parent object.
     *
     * @return array of SerialForm
     * @see ObjectStreamField
     */
    public static SerialForm[] serialForm() {
        return new SerialForm[]{
            new SerialForm(TEST_STR, String.class),
            new SerialForm(TEST_ARRY, long[].class),
            new SerialForm(TEST_INT, int.class),
            new SerialForm(TEST_BYTE, byte.class),
            new SerialForm(TEST_BOOLEAN, boolean.class),
            new SerialForm(TEST_CHAR, char.class),
            new SerialForm(TEST_SHORT, short.class),
            new SerialForm(TEST_LONG, long.class),
            new SerialForm(TEST_FLOAT, float.class),
            new SerialForm(TEST_DOUBLE, double.class)
        };
    }
    
    private static final ObjectStreamField [] serialPersistentFields = serialForm();

    public static void serialize(PutArg args, SerializableTestObject obj) throws IOException {
        System.out.println("writing fields to stream");
        putArgs(args, obj);
        args.writeArgs();
    }
    
    public static void putArgs(PutField args, SerializableTestObject obj) throws IOException {
        args.put(TEST_STR, obj.str);
        args.put(TEST_ARRY, obj.longs);
        args.put(TEST_INT, obj.integer);
        args.put(TEST_BYTE, obj.tbyte);
        args.put(TEST_BOOLEAN, obj.bool);
        args.put(TEST_CHAR, obj.tchar);
        args.put(TEST_SHORT, obj.tshort);
        args.put(TEST_LONG, obj.tlong);
        args.put(TEST_FLOAT, obj.tfloat);
        args.put(TEST_DOUBLE, obj.tdouble);
    }
    
    private void writeObject(ObjectOutputStream out) throws IOException {
        putArgs(out.putFields(), this);
        out.writeFields();
    }

    /**
     * Invariant validation
     *
     * @param args
     * @return
     * @throws IOException
     * @throws ClassNotFoundException
     */
    private static GetArg check(GetArg args) throws IOException, ClassNotFoundException {
        args.get(TEST_STR, null, String.class); // check String class type.
        args.get(TEST_ARRY, null, long[].class); // check array class type.
        // don't need to test int class type, but if there are other invariants
        // we check them here.
        return args;
    }

    private final String str;
    private final long[] longs;
    private final int integer;
    private final boolean bool;
    private final byte tbyte;
    private final char tchar;
    private final short tshort;
    private final long tlong;
    private final float tfloat;
    private final double tdouble;

    /**
     * AtomicSerial constructor.
     *
     * @param args
     * @throws IOException
     * @throws ClassNotFoundException
     */
    public SerializableTestObject(GetArg args) throws IOException, ClassNotFoundException {
        this(check(args).get(TEST_STR, "default", String.class),
                args.get(TEST_ARRY, new long[0], long[].class),
                args.get(TEST_INT, 0),
                args.get(TEST_BOOLEAN, false),
                args.get(TEST_BYTE, Byte.MAX_VALUE),
                args.get(TEST_CHAR, Character.MAX_VALUE),
                args.get(TEST_SHORT, Short.MIN_VALUE),
                args.get(TEST_LONG, 0L),
                args.get(TEST_FLOAT, 0.0F),
                args.get(TEST_DOUBLE, 0.0)
        );
    }

    public SerializableTestObject(
            String str,
            long[] longs,
            int integer,
            boolean bool,
            byte tbyte,
            char tchar,
            short tshort,
            long tlong,
            float tfloat,
            double tdouble) 
    {
        this.str = str;
        this.longs = longs.clone();
        this.integer = integer;
        this.bool = bool;
        this.tbyte = tbyte;
        this.tchar = tchar;
        this.tshort = tshort;
        this.tlong = tlong;
        this.tfloat = tfloat;
        this.tdouble = tdouble;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        final SerializableTestObject other = (SerializableTestObject) obj;
        if (this.bool != other.bool) {
            return false;
        }
        if (this.tbyte != other.tbyte) {
            return false;
        }
        if (this.tchar != other.tchar) {
            return false;
        }
        if (this.tshort != other.tshort) {
            return false;
        }
        if (this.tlong != other.tlong) {
            return false;
        }
        if (Float.floatToIntBits(this.tfloat) != Float.floatToIntBits(other.tfloat)) {
            return false;
        }
        if (Double.doubleToLongBits(this.tdouble) != Double.doubleToLongBits(other.tdouble)) {
            return false;
        }
        if (this.integer != other.integer) {
            return false;
        }
        if (!Objects.equals(this.str, other.str)) {
            return false;
        }
        return Arrays.equals(this.longs, other.longs);
    }

    @Override
    public int hashCode() {
        int hash = 3;
        hash = 71 * hash + (this.bool ? 1 : 0);
        hash = 71 * hash + this.tbyte;
        hash = 71 * hash + this.tchar;
        hash = 71 * hash + this.tshort;
        hash = 71 * hash + (int) (this.tlong ^ (this.tlong >>> 32));
        hash = 71 * hash + Float.floatToIntBits(this.tfloat);
        hash = 71 * hash + (int) (Double.doubleToLongBits(this.tdouble) ^ (Double.doubleToLongBits(this.tdouble) >>> 32));
        hash = 71 * hash + Objects.hashCode(this.str);
        hash = 71 * hash + Arrays.hashCode(this.longs);
        hash = 71 * hash + this.integer;
        return hash;
    }

    @Override
    public String toString() {
        String ln = "\n";
        String colon = ": ";
        String indent = "    ";
        StringBuilder buffer = new StringBuilder();
        buffer.append(getClass().getCanonicalName()).append(ln);
        buffer.append(indent).append(TEST_STR).append(colon).append(str).append(ln);
        buffer.append(indent).append(TEST_ARRY).append(colon).append(Arrays.toString(longs)).append(ln);
        buffer.append(indent).append(TEST_INT).append(colon).append(integer).append(ln);
        buffer.append(indent).append(TEST_BOOLEAN).append(colon).append(bool).append(ln);
        buffer.append(indent).append(TEST_BYTE).append(colon).append(tbyte).append(ln);
        buffer.append(indent).append(TEST_CHAR).append(colon).append(tchar).append(ln);
        buffer.append(indent).append(TEST_SHORT).append(colon).append(tshort).append(ln);
        buffer.append(indent).append(TEST_LONG).append(colon).append(tlong).append(ln);
        buffer.append(indent).append(TEST_FLOAT).append(colon).append(tfloat).append(ln);
        buffer.append(indent).append(TEST_DOUBLE).append(colon).append(tdouble).append(ln);
        return buffer.toString();
    }
}
