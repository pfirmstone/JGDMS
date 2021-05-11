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
import java.io.ObjectOutputStream;
import java.io.ObjectStreamField;
import java.util.Objects;
import org.apache.river.api.io.AtomicObjectInput;
import org.apache.river.api.io.AtomicSerial;
import org.apache.river.api.io.AtomicSerial.PutArg;
import org.apache.river.api.io.AtomicSerial.ReadInput;
import org.apache.river.api.io.AtomicSerial.ReadObject;
import org.apache.river.api.io.AtomicSerial.SerialForm;

/**
 *
 * @author peter
 */
@AtomicSerial
public class SerializableTestSubclass extends SerializableTestObject {
    
    private static final long serialVersionUID = 1L;
    
    public static SerialForm [] serialForm() {
        return new SerialForm[0];
    }
    
    private static final ObjectStreamField [] serialPersistentFields = serialForm();
    
    public static void serialize(PutArg args, SerializableTestSubclass obj) throws IOException{
        System.out.println("Writing object to stream");
        args.output().writeObject(obj.sto);
    }
    
    private void writeObject(ObjectOutputStream out) throws IOException {
        out.writeObject(sto);
    }
    
    @ReadInput
    public static ReadObject read(){
        return new RO();
    }
    private final SerializableTestObject sto;

    public SerializableTestSubclass(AtomicSerial.GetArg args) throws IOException, ClassNotFoundException {
        super(args);
        sto = ((RO)args.getReader()).sto;
    }
    
    public SerializableTestSubclass(String str, long[] longs, int integer, boolean bool, byte tbyte, char tchar, short tshort, long tlong, float tfloat, double tdouble, SerializableTestObject sto) {
        super(str, longs, integer, bool, tbyte, tchar, tshort, tlong, tfloat, tdouble);
        this.sto = sto;
    }

    private static class RO implements ReadObject {

        SerializableTestObject sto;
        
        public RO() {
        }

        @Override
        public void read(AtomicObjectInput input) throws IOException, ClassNotFoundException {
            sto = input.readObject(SerializableTestObject.class);
        }

        @Override
        public void read(ObjectInput input) throws IOException, ClassNotFoundException {
            throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
        }
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (!super.equals(obj)) return false;
        if (getClass() != obj.getClass()) {
            return false;
        }
        final SerializableTestSubclass other = (SerializableTestSubclass) obj;
        return Objects.equals(this.sto, other.sto);
    }

    @Override
    public int hashCode() {
        int hash = super.hashCode();
        hash = 17 * hash + Objects.hashCode(this.sto);
        return hash;
    }
    
    
    
}
