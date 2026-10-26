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
package au.net.zeus.jgdms.der.evolution;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import au.net.zeus.jgdms.der.evolution.Variants.Codec;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/**
 * Class evolution with {@code @AtomicSerial}: the class hierarchy that wrote a stream and the one that reads
 * it need not be the same. Each class reads its fields through its own {@code GetArg}, which exposes only
 * the fields of that class's own record in the stream, so a class never depends on, or sees, how another
 * class's fields are laid out. The stream's schema (for DER the schema chain it declares) is the one
 * schema; there is no second schema to reconcile with the local class hierarchy, and a hierarchy that differs from the
 * writer's is not an error.
 *
 * <p>Two releases of the same class names are compiled into separate class loaders and a stream is written
 * by one and read by the other, in both codecs:
 * <ul>
 *   <li>v1: {@code Sub extends Base}; {@code Base} has {@code a}, {@code Sub} has {@code b};</li>
 *   <li>v2a: {@code Sub} no longer extends {@code Base} and drops {@code a};</li>
 *   <li>v2b: {@code Sub} no longer extends {@code Base} and declares {@code a} itself;</li>
 *   <li>v2c: composition: {@code Sub} holds a {@code Base} built with {@code new Base(arg)}, and declares {@code a}
 *       and {@code b} itself.</li>
 * </ul>
 */
class ClassEvolutionTest {

    private static final String HDR = "package evo;\nimport java.io.IOException;\n"
            + "import org.apache.river.api.io.AtomicSerial;\nimport org.apache.river.api.io.AtomicSerial.*;\n";

    private static final String BASE = HDR + """
        @AtomicSerial public class Base {
          public static SerialForm[] serialForm() { return new SerialForm[]{ new SerialForm("a", int.class) }; }
          public static void serialize(PutArg arg, Base o) throws IOException { arg.put("a", o.a); arg.writeArgs(); }
          public final int a;
          public Base(int a) { this.a = a; }
          public Base(GetArg arg) throws IOException, ClassNotFoundException { this(arg.get("a", -1)); }
        }
        """;

    private static final String SUB_V1 = HDR + """
        @AtomicSerial public class Sub extends Base {
          public static SerialForm[] serialForm() { return new SerialForm[]{ new SerialForm("b", String.class) }; }
          public static void serialize(PutArg arg, Sub o) throws IOException { arg.put("b", o.b); arg.writeArgs(); }
          public final String b;
          public Sub(int a, String b) { super(a); this.b = b; }
          public Sub(GetArg arg) throws IOException, ClassNotFoundException { super(arg); this.b = arg.get("b", "none", String.class); }
          public int a() { return a; }
          public String b() { return b; }
        }
        """;

    private static final String SUB_V2A = HDR + """
        @AtomicSerial public class Sub {
          public static SerialForm[] serialForm() { return new SerialForm[]{ new SerialForm("b", String.class) }; }
          public static void serialize(PutArg arg, Sub o) throws IOException { arg.put("b", o.b); arg.writeArgs(); }
          public final String b;
          public Sub(int a, String b) { this.b = b; }
          public Sub(GetArg arg) throws IOException, ClassNotFoundException { this.b = arg.get("b", "none", String.class); }
          public int a() { return -2; }
          public String b() { return b; }
        }
        """;

    private static final String SUB_V2B = HDR + """
        @AtomicSerial public class Sub {
          public static SerialForm[] serialForm() { return new SerialForm[]{ new SerialForm("a", int.class), new SerialForm("b", String.class) }; }
          public static void serialize(PutArg arg, Sub o) throws IOException { arg.put("a", o.a); arg.put("b", o.b); arg.writeArgs(); }
          public final int a; public final String b;
          public Sub(int a, String b) { this.a = a; this.b = b; }
          public Sub(GetArg arg) throws IOException, ClassNotFoundException { this(arg.get("a", -1), arg.get("b", "none", String.class)); }
          public int a() { return a; }
          public String b() { return b; }
        }
        """;

    private static final String SUB_V2C = HDR + """
        @AtomicSerial public class Sub {
          public static SerialForm[] serialForm() { return new SerialForm[]{ new SerialForm("a", int.class), new SerialForm("b", String.class) }; }
          public static void serialize(PutArg arg, Sub o) throws IOException { arg.put("a", o.base.a); arg.put("b", o.b); arg.writeArgs(); }
          public final Base base; public final String b;
          public Sub(int a, String b) { this.base = new Base(a); this.b = b; }
          public Sub(GetArg arg) throws IOException, ClassNotFoundException { this.base = new Base(arg); this.b = arg.get("b", "none", String.class); }
          public int a() { return base.a; }
          public String b() { return b; }
        }
        """;

    private static ClassLoader v1;
    private static ClassLoader v2a;
    private static ClassLoader v2b;
    private static ClassLoader v2c;

    @BeforeAll
    static void compile() throws Exception {
        v1 = Variants.load(Map.of("evo/Base.java", BASE, "evo/Sub.java", SUB_V1));
        v2a = Variants.load(Map.of("evo/Base.java", BASE, "evo/Sub.java", SUB_V2A));
        v2b = Variants.load(Map.of("evo/Base.java", BASE, "evo/Sub.java", SUB_V2B));
        v2c = Variants.load(Map.of("evo/Base.java", BASE, "evo/Sub.java", SUB_V2C));
    }

    private static Object sub(ClassLoader l, int a, String b) throws Exception {
        return l.loadClass("evo.Sub").getConstructor(int.class, String.class).newInstance(a, b);
    }

    private static int a(Object o) throws Exception {
        return (Integer) o.getClass().getMethod("a").invoke(o);
    }

    private static String b(Object o) throws Exception {
        return (String) o.getClass().getMethod("b").invoke(o);
    }

    /** Writes a Sub(7, "x") with {@code from} and reads it with {@code to}; the result is a class of {@code to}. */
    private static Object evolve(Codec c, ClassLoader from, ClassLoader to) throws Exception {
        Object back = c.read(c.write(sub(from, 7, "x"), from), to);
        assertSame(to, back.getClass().getClassLoader(), "the object must be built from the reader's class");
        return back;
    }

    @ParameterizedTest
    @EnumSource(Codec.class)
    void sameVersionRoundTrips(Codec c) throws Exception {
        for (ClassLoader l : new ClassLoader[] {v1, v2a, v2b, v2c}) {
            Object back = evolve(c, l, l);
            assertEquals("x", b(back));
        }
        assertEquals(7, a(evolve(c, v1, v1)));
        assertEquals(7, a(evolve(c, v2b, v2b)));
        assertEquals(7, a(evolve(c, v2c, v2c)));
    }

    @ParameterizedTest
    @EnumSource(Codec.class)
    void aSubclassThatNoLongerHasItsSuperclassStillReadsItsOwnFields(Codec c) throws Exception {
        // v1 wrote Sub{b} over Base{a}; v2a is a Sub with no superclass: it reads b, and does not see Base's record
        Object back = evolve(c, v1, v2a);
        assertEquals("x", b(back));
    }

    @ParameterizedTest
    @EnumSource(Codec.class)
    void aFieldMovedToAnotherClassReadsAsAbsentNotAsAnError(Codec c) throws Exception {
        // v2b declares a itself, but the v1 stream carries a in Base's record, which Sub's GetArg cannot see:
        // a is absent for Sub, so its default applies; b is intact; nothing is rejected
        Object back = evolve(c, v1, v2b);
        assertEquals(-1, a(back));
        assertEquals("x", b(back));
    }

    @ParameterizedTest
    @EnumSource(Codec.class)
    void compositionReadsAnOldInheritanceStreamWithoutLoss(Codec c) throws Exception {
        // v2c is not a Base, it holds one built with new Base(arg): the stack names Base, so Base's own record is read
        Object back = evolve(c, v1, v2c);
        assertEquals(7, a(back));
        assertEquals("x", b(back));
    }

    @ParameterizedTest
    @EnumSource(Codec.class)
    void anOldReaderOfACompositionStreamReadsWhatItsClassesDeclare(Codec c) throws Exception {
        // the v2c stream has one class record, Sub{a,b}: v1's Sub reads b; v1's Base finds no Base record of its own,
        // so under DER its a defaults (JOSS, see the last test, finds a by name in the flattened record)
        Object back = evolve(c, v2c, v1);
        assertEquals("x", b(back));
        assertEquals(c == Codec.JOSS ? 7 : -1, a(back));
    }

    @ParameterizedTest
    @EnumSource(Codec.class)
    void aFlattenedWriterIsReadByTheOldHierarchyAndByTheSplitOne(Codec c) throws Exception {
        assertEquals("x", b(evolve(c, v2b, v1)));
        assertEquals("x", b(evolve(c, v2b, v2a)));
        assertEquals(7, a(evolve(c, v2b, v2c)));        // composition: new Base(arg) resolves to Sub, the one registered class
        assertEquals(7, a(evolve(c, v2c, v2b)));
    }

    @ParameterizedTest
    @EnumSource(Codec.class)
    void aClassThatDroppedAFieldIgnoresItInOldStreams(Codec c) throws Exception {
        Object back = evolve(c, v2b, v2a);
        assertEquals("x", b(back));
        assertEquals(-2, a(back));
    }

    @ParameterizedTest
    @EnumSource(Codec.class)
    void aFlattenedWriterReadByTheOldSplitHierarchy(Codec c) throws Exception {
        // the one place the codecs differ today: JOSS resolves Base's a from the flattened Sub record by name,
        // DER keeps each class to its own record
        int expectedA = c == Codec.JOSS ? 7 : -1;
        assertEquals(expectedA, a(evolve(c, v2b, v1)));
    }
}
