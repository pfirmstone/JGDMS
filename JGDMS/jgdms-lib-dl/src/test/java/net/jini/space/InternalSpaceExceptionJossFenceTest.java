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
package net.jini.space;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.NotSerializableException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.List;
import org.apache.river.api.io.AtomicMarshalInputStream;
import org.apache.river.api.io.AtomicMarshalOutputStream;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * SOW-Outrigger-DER-Only-JOSS-Rejection sec.3 item 6: java.io (JOSS)
 * serialization of {@link InternalSpaceException} is fenced off -- in any
 * graph, including as a nested cause -- while the atomic marshalling wire
 * form (the {@code ThrowableSerializer} substitution) is byte-compatibly
 * preserved because the class deliberately carries no {@code @AtomicSerial}
 * annotation (the annotation would take dispatch precedence over the
 * serializer substitution and change the wire form -- the sec.2.2 board
 * constraint).
 */
public class InternalSpaceExceptionJossFenceTest {

    // ---------------------------------------------------------------
    // JOSS fence: direct
    // ---------------------------------------------------------------

    @Test
    public void jossWriteIsFenced() throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ObjectOutputStream oos = new ObjectOutputStream(baos);
        try {
            oos.writeObject(new InternalSpaceException("fenced"));
            fail("java.io serialization of InternalSpaceException must throw"
                 + " NotSerializableException");
        } catch (NotSerializableException expected) {
            assertTrue(expected.getMessage()
                    .contains(InternalSpaceException.class.getName()));
        }
    }

    // ---------------------------------------------------------------
    // JOSS fence: any graph -- nested cause and container element
    // ---------------------------------------------------------------

    @Test
    public void jossWriteIsFencedAsNestedCause() throws Exception {
        Exception outer = new IllegalStateException("outer",
                new InternalSpaceException("inner", new IOException("root")));
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ObjectOutputStream oos = new ObjectOutputStream(baos);
        try {
            oos.writeObject(outer);
            fail("java.io serialization of a cause chain containing"
                 + " InternalSpaceException must throw NotSerializableException");
        } catch (NotSerializableException expected) {
            assertTrue(expected.getMessage()
                    .contains(InternalSpaceException.class.getName()));
        }
    }

    @Test
    public void jossWriteIsFencedInsideAContainerGraph() throws Exception {
        List<Object> graph = new ArrayList<Object>();
        graph.add("benign");
        graph.add(new InternalSpaceException("in a list"));
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ObjectOutputStream oos = new ObjectOutputStream(baos);
        try {
            oos.writeObject(graph);
            fail("java.io serialization of any graph containing"
                 + " InternalSpaceException must throw NotSerializableException");
        } catch (NotSerializableException expected) {
            assertTrue(expected.getMessage()
                    .contains(InternalSpaceException.class.getName()));
        }
    }

    // ---------------------------------------------------------------
    // Atomic marshalling transit preserved (ThrowableSerializer path)
    // ---------------------------------------------------------------

    /**
     * The exception must still cross the atomic marshalling layer exactly as
     * before the fence: {@code AtomicMarshalOutputStream.defaultReplaceObject}
     * substitutes {@code ThrowableSerializer} BEFORE the object is serialized
     * (the fence methods are never invoked), and reconstruction happens via
     * constructor matching ({@code InternalSpaceException(String, Throwable)}),
     * repopulating the public {@code nestedException} field.
     */
    @Test
    public void atomicMarshallingRoundTripStillWorks() throws Exception {
        InternalSpaceException original =
                new InternalSpaceException("space broke", new IOException("root"));
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ObjectOutputStream oos = new AtomicMarshalOutputStream(baos, null);
        oos.writeObject(original);
        oos.flush();
        ByteArrayInputStream bais = new ByteArrayInputStream(baos.toByteArray());
        ObjectInputStream in =
                AtomicMarshalInputStream.create(bais, null, false, null, null, false);
        Object decoded = in.readObject();
        assertTrue("atomic decode must reconstruct the original exception type,"
                + " got " + decoded.getClass(),
                decoded instanceof InternalSpaceException);
        InternalSpaceException result = (InternalSpaceException) decoded;
        assertEquals("space broke", result.getMessage());
        assertNotNull("nestedException must be repopulated by the"
                + " (String, Throwable) constructor", result.nestedException);
        assertTrue(result.nestedException instanceof IOException);
        assertEquals("root", result.nestedException.getMessage());
    }
}
