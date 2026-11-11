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
package au.net.zeus.jgdms.der.object;

import au.net.zeus.jgdms.der.getarg.ResolutionContext;
import au.net.zeus.jgdms.der.marshal.DerMarshalledInstance;
import au.net.zeus.jgdms.der.stream.DerMarshalInputStream;
import au.net.zeus.jgdms.der.stream.DerMarshalOutputStream;
import net.jini.core.constraint.MarshallingFormat;
import net.jini.io.MarshalledInstance;
import net.jini.io.UnsupportedConstraintException;
import org.apache.river.api.io.AtomicSerial;
import org.apache.river.api.io.AtomicSerial.GetArg;
import org.apache.river.api.io.AtomicSerial.PutArg;
import org.apache.river.api.io.AtomicSerial.SerialForm;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Invariant: when the receiving stream requires {@code ATOMIC_DER}, a {@link MarshalledInstance}
 * that names another payload format is refused when it is DECODED, not when it is later read.
 * Call sites such as {@code mi.get(false)} carry no context, so a check that lived only in
 * {@code get} could never see the requirement; these tests build the holder first and read
 * the field afterwards, exactly as a lazily unmarshalling proxy does.
 */
public class DerLazyMarshalledInstanceFormatTest {

    private static final String DER = MarshallingFormat.ATOMIC_DER.getFormat();

    /** An object that keeps a {@link MarshalledInstance} to unmarshal later, as service proxies do. */
    @AtomicSerial
    public static final class Holder {
        public static SerialForm[] serialForm() {
            return new SerialForm[]{ new SerialForm("mi", MarshalledInstance.class) };
        }

        public static void serialize(PutArg arg, Holder h) throws IOException {
            arg.put("mi", h.mi);
            arg.writeArgs();
        }

        public final MarshalledInstance mi;

        public Holder(MarshalledInstance mi) { this.mi = mi; }

        public Holder(GetArg arg) throws IOException, ClassNotFoundException {
            this.mi = arg.get("mi", null, MarshalledInstance.class);
        }
    }

    private static byte[] encode(MarshalledInstance mi) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        ClassLoader cl = DerLazyMarshalledInstanceFormatTest.class.getClassLoader();
        DerMarshalOutputStream out = new DerMarshalOutputStream(bos, Collections.emptyList(), cl);
        out.writeObject(new Holder(mi));
        out.flush();
        return bos.toByteArray();
    }

    private static Holder decode(byte[] bytes, String requiredFormat) throws Exception {
        ClassLoader cl = DerLazyMarshalledInstanceFormatTest.class.getClassLoader();
        ResolutionContext rc = new ResolutionContext(cl, false, cl).withRequiredPayloadFormat(requiredFormat);
        return (Holder) new DerMarshalInputStream(new ByteArrayInputStream(bytes), rc).readObject();
    }

    private static Throwable find(Throwable t, Class<? extends Throwable> type) {
        while (t != null && !type.isInstance(t)) t = t.getCause();
        return t;
    }

    @Test
    public void jossPayloadIsRefusedAtDecodeWhenDerRequired() throws Exception {
        byte[] bytes = encode(new MarshalledInstance("joss-payload", Collections.emptyList()));
        Exception e = assertThrows(Exception.class, () -> decode(bytes, DER));
        assertTrue(find(e, UnsupportedConstraintException.class) != null,
                "expected UnsupportedConstraintException in the chain of " + e);
    }

    @Test
    public void jossPayloadDecodesWhenNothingRequired() throws Exception {
        byte[] bytes = encode(new MarshalledInstance("joss-payload", Collections.emptyList()));
        Holder h = decode(bytes, null);
        assertEquals("joss-payload", h.mi.get(false));
    }

    @Test
    public void derPayloadIsAcceptedWhenDerRequired() throws Exception {
        byte[] bytes = encode(new DerMarshalledInstance("der-payload", Collections.emptyList(), false));
        Holder h = decode(bytes, DER);
        assertEquals("der-payload", h.mi.get(false));
    }
}
