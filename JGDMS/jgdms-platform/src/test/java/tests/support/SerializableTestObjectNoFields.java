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
import java.io.ObjectInput;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.ObjectStreamField;
import java.io.Serializable;
import java.util.Arrays;
import java.util.Objects;
import org.apache.river.api.io.AtomicObjectInput;
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
    
    @AtomicSerial.ReadInput
    public static AtomicSerial.ReadObject read(){
        return new RO();
    }

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
        return new SerialForm[]{};
    }
    
    private static final ObjectStreamField [] serialPersistentFields = serialForm();

    public static void serialize(PutArg args, SerializableTestObjectNoFields obj) throws IOException {
        System.out.println("writing fields directly to stream");
        args.output().writeUTF(obj.str);
        args.output().writeObject(obj.longs);
        args.output().writeInt(obj.integer);
        args.output().writeByte(obj.tbyte);
        args.output().writeBoolean(obj.bool);
        args.output().writeChar(obj.tchar);
        args.output().writeShort(obj.tshort);
        args.output().writeLong(obj.tlong);
        args.output().writeFloat(obj.tfloat);
        args.output().writeDouble(obj.tdouble);
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
        this(((RO)args.getReader()).str,
            ((RO)args.getReader()).longs,
            ((RO)args.getReader()).integer,
            ((RO)args.getReader()).bool,
            ((RO)args.getReader()).tbyte,
            ((RO)args.getReader()).tchar,
            ((RO)args.getReader()).tshort,
            ((RO)args.getReader()).tlong,
            ((RO)args.getReader()).tfloat,
            ((RO)args.getReader()).tdouble
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
        this.str = input.readUTF();
        this.longs = (long[]) input.readObject();
        this.integer = input.readInt();
        this.bool = input.readBoolean();
        this.tbyte = input.readByte();
        this.tchar = input.readChar();
        this.tshort = input.readShort();
        this.tlong = input.readLong();
        this.tfloat = input.readFloat();
        this.tdouble = input.readDouble();
    }
    
    private void writeObject(ObjectOutputStream out) throws IOException {
        out.writeUTF(str);
        out.writeObject(longs);
        out.writeInt(integer);
        out.writeByte(tbyte);
        out.writeBoolean(bool);
        out.writeChar(tchar);
        out.writeShort(tshort);
        out.writeLong(tlong);
        out.writeFloat(tfloat);
        out.writeDouble(tdouble);
    }

    private static class RO implements AtomicSerial.ReadObject {
        
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

        public RO() {
        }

        @Override
        public void read(AtomicObjectInput input) throws IOException, ClassNotFoundException {
            this.str = input.readUTF();
            this.longs = input.readObject(long[].class).clone();
            this.integer = input.readInt();
            this.bool = input.readBoolean();
            this.tbyte = input.readByte();
            this.tchar = input.readChar();
            this.tshort = input.readShort();
            this.tlong = input.readLong();
            this.tfloat = input.readFloat();
            this.tdouble = input.readDouble();
        }

        @Override
        public void read(ObjectInput input) throws IOException, ClassNotFoundException {
            throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
        }
    }
}
