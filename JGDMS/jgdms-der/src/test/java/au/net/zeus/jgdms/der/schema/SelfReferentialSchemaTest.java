/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package au.net.zeus.jgdms.der.schema;

import au.net.zeus.jgdms.der.stream.DerMarshalInputStream;
import au.net.zeus.jgdms.der.stream.DerMarshalOutputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.Objects;
import org.apache.river.api.io.AtomicSerial;
import org.apache.river.api.io.AtomicSerial.GetArg;
import org.apache.river.api.io.AtomicSerial.PutArg;
import org.apache.river.api.io.AtomicSerial.SerialForm;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * EARLY CHECK for the DER fault-carrier work (fix/der-throwable-marshalling):
 * a SELF-REFERENTIAL {@code @AtomicSerial} type -- a serial field declared as
 * the declaring class itself (the {@code Node.next: Node} pattern) -- must be
 * schema-generatable and round-trippable before {@code DerThrowableForm} (whose
 * {@code cause} field is {@code DerThrowableForm} and whose {@code suppressed}
 * field is {@code DerThrowableForm[]}) can build on it.
 *
 * <p>Expected mechanics: {@code SchemaGenerator.toWireType} maps a field
 * declared as an {@code @AtomicSerial} class to the non-expanding
 * {@code "@AtomicSerial"} marker (the concrete class travels in each nested
 * value's own embedded schema chain), so schema generation must terminate and
 * value-tree encode/decode must recurse only as deep as the acyclic VALUE, not
 * the type graph.
 */
class SelfReferentialSchemaTest {

    /** The Node.next:Node self-referential fixture (with a self-typed array too). */
    @AtomicSerial
    public static final class Node {

        public static SerialForm[] serialForm() {
            return new SerialForm[]{
                new SerialForm("name", String.class),
                new SerialForm("next", Node.class),
                new SerialForm("branches", Node[].class),
            };
        }

        public static void serialize(PutArg arg, Node n) throws IOException {
            arg.put("name", n.name);
            arg.put("next", n.next);
            arg.put("branches", n.branches);
            arg.writeArgs();
        }

        public final String name;
        public final Node next;       // self-referential nested field
        public final Node[] branches; // self-referential array field

        public Node(String name, Node next, Node[] branches) {
            this.name = name;
            this.next = next;
            this.branches = branches;
        }

        public Node(GetArg arg) throws IOException, ClassNotFoundException {
            this(arg.get("name", null, String.class),
                 arg.get("next", null, Node.class),
                 arg.get("branches", null, Node[].class));
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof Node that)) return false;
            if (!Objects.equals(name, that.name)) return false;
            if (!Objects.equals(next, that.next)) return false;
            if (branches == null || that.branches == null) {
                return branches == that.branches;
            }
            if (branches.length != that.branches.length) return false;
            for (int i = 0; i < branches.length; i++) {
                if (!Objects.equals(branches[i], that.branches[i])) return false;
            }
            return true;
        }

        @Override
        public int hashCode() {
            return Objects.hash(name, next);
        }
    }

    /** Schema generation over a self-referential type terminates with the marker type. */
    @Test
    void schemaGenerationTerminatesForSelfReferentialType() throws Exception {
        SchemaChain.Result chain = SchemaGenerator.generateChain(Node.class);
        assertEquals(1, chain.chain().size(), "Node is Object-rooted: one record");
        AtomicSerialSchemaRecord rec = chain.chain().get(0);
        List<AtomicSerialFieldDef> fields = rec.fields();
        assertEquals(3, fields.size());
        assertEquals("java.lang.String", fields.get(0).wireType());
        assertEquals("@AtomicSerial", fields.get(1).wireType(),
                "self-typed field must map to the non-expanding @AtomicSerial marker");
        assertEquals("array:@AtomicSerial:" + Node.class.getName(),
                fields.get(2).wireType(),
                "self-typed array field must map to array:@AtomicSerial:<class>");
    }

    /** A self-referential value chain (depth 3, with a branch array) round-trips. */
    @Test
    void selfReferentialValueRoundTrips() throws Exception {
        Node leaf = new Node("leaf", null, null);
        Node mid = new Node("mid", leaf, new Node[]{new Node("branch", null, null)});
        Node root = new Node("root", mid, new Node[0]);

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (DerMarshalOutputStream out = new DerMarshalOutputStream(baos)) {
            out.writeObject(root);
            out.flush();
        }
        Object decoded;
        try (DerMarshalInputStream in =
                new DerMarshalInputStream(new ByteArrayInputStream(baos.toByteArray()))) {
            decoded = in.readObject();
        }
        assertNotNull(decoded);
        assertEquals(root, decoded, "self-referential value tree must round-trip by value");
        Node d = (Node) decoded;
        assertEquals("mid", d.next.name);
        assertEquals("leaf", d.next.next.name);
        assertNull(d.next.next.next);
        assertEquals(1, d.next.branches.length);
        assertEquals("branch", d.next.branches[0].name);
    }
}
