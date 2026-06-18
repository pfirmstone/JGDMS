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
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
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
public class SerializableTestObjectNoFields implements Serializable {
    
    private static final long serialVersionUID = 1L;
    
    public static SerialForm[] serialForm() {
        return new SerialForm[]{
            new SerialForm("str", String.class),
            new SerialForm("longs", long[].class),
            new SerialForm("integer", int.class),
            new SerialForm("bool", boolean.class),
            new SerialForm("tbyte", byte.class),
            new SerialForm("tchar", char.class),
            new SerialForm("tshort", short.class),
            new SerialForm("tlong", long.class),
            new SerialForm("tfloat", float.class),
            new SerialForm("tdouble", double.class)
        };
    }

    // serialPersistentFields is INDEPENDENT of serialForm() (dual-path JOSS keep, STD-008 sec9.1);
    // it mirrors serialForm() here so a JOSS-written stream is also readable by the
    // AtomicMarshalInputStream (named-field cross-protocol round-trip).
    private static final ObjectStreamField[] serialPersistentFields = {
        new ObjectStreamField("str", String.class),
        new ObjectStreamField("longs", long[].class),
        new ObjectStreamField("integer", int.class),
        new ObjectStreamField("bool", boolean.class),
        new ObjectStreamField("tbyte", byte.class),
        new ObjectStreamField("tchar", char.class),
        new ObjectStreamField("tshort", short.class),
        new ObjectStreamField("tlong", long.class),
        new ObjectStreamField("tfloat", float.class),
        new ObjectStreamField("tdouble", double.class)
    };

    public static void serialize(PutArg args, SerializableTestObjectNoFields obj) throws IOException {
        args.put("str", obj.str);
        args.put("longs", obj.longs);
        args.put("integer", obj.integer);
        args.put("bool", obj.bool);
        args.put("tbyte", obj.tbyte);
        args.put("tchar", obj.tchar);
        args.put("tshort", obj.tshort);
        args.put("tlong", obj.tlong);
        args.put("tfloat", obj.tfloat);
        args.put("tdouble", obj.tdouble);
        args.writeArgs();
    }

    private String str;
    private long[] longs;
    private int integer;
    private boolean bool;
    private byte tbyte;
    private char tchar;
    private short tshort;
    private long tlong;
    private float tfloat;
    private double tdouble;

    /**
     * AtomicSerial constructor.
     *
     * @param args
     * @throws IOException
     * @throws ClassNotFoundException
     */
    public SerializableTestObjectNoFields(GetArg args) throws IOException, ClassNotFoundException {
        this(args.get("str", null, String.class),
            args.get("longs", new long[0], long[].class),
            args.get("integer", 0),
            args.get("bool", false),
            args.get("tbyte", (byte) 0),
            args.get("tchar", (char) 0),
            args.get("tshort", (short) 0),
            args.get("tlong", 0L),
            args.get("tfloat", 0.0F),
            args.get("tdouble", 0.0)
        );
    }

    public SerializableTestObjectNoFields(
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
        final SerializableTestObjectNoFields other = (SerializableTestObjectNoFields) obj;
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
        StringBuilder buffer = new StringBuilder();
        buffer.append(getClass().getCanonicalName()).append(ln);
        buffer.append(str).append(ln);
        buffer.append(Arrays.toString(longs)).append(ln);
        buffer.append(integer).append(ln);
        buffer.append(colon).append(bool).append(ln);
        buffer.append(tbyte).append(ln);
        buffer.append(tchar).append(ln);
        buffer.append(tshort).append(ln);
        buffer.append(tlong).append(ln);
        buffer.append(tfloat).append(ln);
        buffer.append(tdouble).append(ln);
        return buffer.toString();
    }
    
    private void readObject(ObjectInputStream input) throws IOException, ClassNotFoundException{
        input.defaultReadObject();
    }

    private void writeObject(ObjectOutputStream out) throws IOException {
        out.defaultWriteObject();
    }
}
