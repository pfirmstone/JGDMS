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
import java.util.Objects;
import org.apache.river.api.io.AtomicSerial;
import org.apache.river.api.io.AtomicSerial.PutArg;
import org.apache.river.api.io.AtomicSerial.SerialForm;

/**
 *
 * @author peter
 */
@AtomicSerial
public class SerializableTestSubclassNoFields extends SerializableTestObjectNoFields {
    
    public static SerialForm [] serialForm() {
        return new SerialForm[]{
            new SerialForm("sto", SerializableTestObject.class)
        };
    }

    public static void serialize(PutArg args, SerializableTestSubclassNoFields obj) throws IOException{
        args.put("sto", obj.sto);
        args.writeArgs();
    }

    private final SerializableTestObject sto;

    public SerializableTestSubclassNoFields(AtomicSerial.GetArg args) throws IOException, ClassNotFoundException {
        super(args);
        sto = args.get("sto", null, SerializableTestObject.class);
    }
    
    public SerializableTestSubclassNoFields(String str, long[] longs, int integer, boolean bool, byte tbyte, char tchar, short tshort, long tlong, float tfloat, double tdouble, SerializableTestObject sto) {
        super(str, longs, integer, bool, tbyte, tchar, tshort, tlong, tfloat, tdouble);
        this.sto = sto;
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
        final SerializableTestSubclassNoFields other = (SerializableTestSubclassNoFields) obj;
        return Objects.equals(this.sto, other.sto);
    }

    @Override
    public int hashCode() {
        int hash = super.hashCode();
        hash = 17 * hash + Objects.hashCode(this.sto);
        return hash;
    }
    
    
    
}
