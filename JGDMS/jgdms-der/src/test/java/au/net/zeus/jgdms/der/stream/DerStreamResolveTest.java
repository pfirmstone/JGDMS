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
package au.net.zeus.jgdms.der.stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectStreamException;
import java.util.ArrayList;
import java.util.List;
import net.jini.core.constraint.ClientAuthentication;
import net.jini.core.constraint.Confidentiality;
import net.jini.core.constraint.Delegation;
import net.jini.core.constraint.Integrity;
import net.jini.core.constraint.ServerAuthentication;
import org.apache.river.api.io.AtomicSerial;
import org.apache.river.api.io.AtomicSerial.GetArg;
import org.apache.river.api.io.AtomicSerial.PutArg;
import org.apache.river.api.io.AtomicSerial.SerialForm;
import org.apache.river.api.io.Resolve;
import org.junit.jupiter.api.Test;

/**
 * A value that implements {@link Resolve} is replaced by its {@code readResolve()} when it is the object a
 * stream read returns, as it already is when it is nested in another value and as Java serialization
 * does. Before, only the nested case resolved, so a singleton read directly from a DER stream (a
 * constraint such as {@code Integrity.YES}) was a second instance that was not equal to the first.
 */
class DerStreamResolveTest {

    /** A value whose readResolve() answers one of two canonical instances. */
    @AtomicSerial
    public static final class Flag implements Resolve {
        public static final Flag ON = new Flag(true);
        public static final Flag OFF = new Flag(false);
        public static SerialForm[] serialForm() {
            return new SerialForm[] {new SerialForm("on", Boolean.TYPE)};
        }
        public static void serialize(PutArg arg, Flag f) throws IOException {
            arg.put("on", f.on);
            arg.writeArgs();
        }
        final boolean on;
        private Flag(boolean on) { this.on = on; }
        public Flag(GetArg arg) throws IOException, ClassNotFoundException { this(arg.get("on", false)); }
        @Override public Object readResolve() throws ObjectStreamException { return on ? ON : OFF; }
    }

    private static List<Object> roundTrip(Object... objs) throws Exception {
        DerObjectStreamCodec out = new DerObjectStreamCodec();
        for (Object o : objs) {
            out.writeObject(o);
        }
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        out.drainTo(bos);
        DerObjectStreamCodec in = new DerObjectStreamCodec();
        in.initReader(bos.toByteArray());
        List<Object> back = new ArrayList<>();
        for (int i = 0; i < objs.length; i++) {
            back.add(in.readObject());
        }
        return back;
    }

    @Test
    void aResolveValueReadDirectlyIsItsCanonicalInstance() throws Exception {
        List<Object> back = roundTrip(Flag.ON, Flag.OFF, Flag.ON);
        assertSame(Flag.ON, back.get(0));
        assertSame(Flag.OFF, back.get(1));
        assertSame(Flag.ON, back.get(2));
    }

    @Test
    void theSingletonConstraintsReadDirectlyAreTheCanonicalInstances() throws Exception {
        Object[] all = {
            Integrity.YES, Integrity.NO, Confidentiality.YES, Confidentiality.NO,
            ClientAuthentication.YES, ClientAuthentication.NO, ServerAuthentication.YES, ServerAuthentication.NO,
            Delegation.YES, Delegation.NO,
        };
        List<Object> back = roundTrip(all);
        for (int i = 0; i < all.length; i++) {
            assertSame(all[i], back.get(i), "item " + i + " (" + all[i] + ")");
        }
    }

    @Test
    void aValueThatIsNotResolveIsUnchanged() throws Exception {
        DerStreamSchemaDedupTest.BytesHolder h = new DerStreamSchemaDedupTest.BytesHolder(new byte[] {1, 2, 3});
        Object back = roundTrip(h).get(0);
        assertNotSame(h, back);
        assertEquals(DerStreamSchemaDedupTest.BytesHolder.class, back.getClass());
    }
}
