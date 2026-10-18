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

package au.net.zeus.jgdms.der.stream;

import au.net.zeus.jgdms.der.DerException;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * B5: the policy object itself: property parsing and validation (a mistake can never switch a limit off), the allow/deny entry
 * grammar and matching, and the meter's name extraction. The stream-level behaviour is {@link DerStreamPolicyAdmissionTest}.
 */
class DerStreamPolicyTest {

    private static DerStreamPolicy from(String... kv) {
        Map<String, String> m = new HashMap<>();
        for (int i = 0; i < kv.length; i += 2) m.put(DerStreamPolicy.PROP_PREFIX + kv[i], kv[i + 1]);
        return DerStreamPolicy.fromProperties(m::get);
    }

    // ------------------------------------------------------------------ defaults and budgets

    @Test
    void withNothingSetThePolicyIsTheBuiltInDefaults() {
        DerStreamPolicy p = from();
        assertEquals(DerStreamPolicy.DEFAULT_MAX_OBJECTS, p.maxObjects());
        assertEquals(DerStreamPolicy.DEFAULT_MAX_ELEMENTS, p.maxElements());
        assertEquals(DerStreamPolicy.DEFAULT_MAX_DISTINCT_CLASSES, p.maxDistinctClasses());
        assertTrue(p.rejectRebinding());
        assertEquals(p.toString(), DerStreamPolicy.BUILT_IN.toString());
    }

    @Test
    void theDocumentedDefaultsAreTheShippedOnes() {
        // these numbers are in the Javadoc, the SOW and release notes: a change here is a change to what streams are accepted
        assertEquals(262_144, DerStreamPolicy.DEFAULT_MAX_OBJECTS);
        assertEquals(4_194_304, DerStreamPolicy.DEFAULT_MAX_ELEMENTS);
        assertEquals(1_024, DerStreamPolicy.DEFAULT_MAX_DISTINCT_CLASSES);
        assertTrue(DerStreamPolicy.BUILT_IN.rejectRebinding());
    }

    @Test
    void theShippedDefaultMatchesTheBuiltInWhenNoSystemPropertyIsSet() {
        // the test JVM sets none of them; a JVM that does would be testing its own configuration
        boolean set = System.getProperties().stringPropertyNames().stream().anyMatch(n -> n.startsWith(DerStreamPolicy.PROP_PREFIX));
        if (!set) {
            assertEquals(DerStreamPolicy.BUILT_IN.toString(), DerStreamPolicy.defaults().toString());
            assertSame(DerStreamPolicy.defaults(), DerStreamPolicy.defaults(), "read once");
        }
    }

    @Test
    void aValidBudgetIsTakenAndAnythingElseIsTheDefault() {
        assertEquals(5, from("maxObjects", "5").maxObjects());
        assertEquals(7, from("maxObjects", "  7 ").maxObjects(), "whitespace is trimmed");
        assertEquals(Integer.MAX_VALUE, from("maxElements", String.valueOf(Integer.MAX_VALUE)).maxElements());
        for (String bad : new String[] {"", "  ", "abc", "0", "-1", "-2147483648", "2147483648", "1e3", "0x10", "1.5", "5 5", "٣"}) {
            assertEquals(DerStreamPolicy.DEFAULT_MAX_OBJECTS, from("maxObjects", bad).maxObjects(),
                    "'" + bad + "' must fall back to the default, never to 'no limit'");
        }
    }

    @Test
    void everyBudgetIsReadFromItsOwnProperty() {
        DerStreamPolicy p = from("maxObjects", "11", "maxElements", "22", "maxDistinctClasses", "33");
        assertEquals(11, p.maxObjects());
        assertEquals(22, p.maxElements());
        assertEquals(33, p.maxDistinctClasses());
    }

    @Test
    void theRebindingSwitchTakesOnlyTrueOrFalse() {
        assertFalse(from("rejectRebinding", "false").rejectRebinding());
        assertFalse(from("rejectRebinding", " FALSE ").rejectRebinding());
        assertTrue(from("rejectRebinding", "true").rejectRebinding());
        for (String bad : new String[] {"", "no", "0", "off", "yes", "1", "f"}) {
            assertTrue(from("rejectRebinding", bad).rejectRebinding(), "'" + bad + "' keeps the safe default");
        }
    }

    @Test
    void theProgrammaticWithersRefuseANonPositiveBudget() {
        DerStreamPolicy p = DerStreamPolicy.BUILT_IN;
        assertThrows(IllegalArgumentException.class, () -> p.withMaxObjects(0));
        assertThrows(IllegalArgumentException.class, () -> p.withMaxElements(-1));
        assertThrows(IllegalArgumentException.class, () -> p.withMaxDistinctClasses(0));
        assertEquals(1, p.withMaxObjects(1).maxObjects());
    }

    // ------------------------------------------------------------------ the meter's name extraction

    private static java.util.Set<String> namesIn(String token) throws DerException {
        StreamPolicyMeter m = new StreamPolicyMeter(DerStreamPolicy.BUILT_IN);
        m.wireType(token);
        return m.classNames();
    }

    @Test
    void wireTypeTokensYieldTheirClassNamesAtAnyDepth() throws Exception {
        assertEquals(java.util.Set.of("a.B"), namesIn("@AtomicSerial:a.B"));
        assertEquals(java.util.Set.of("a.B"), namesIn("array:@AtomicSerial:a.B"));
        assertEquals(java.util.Set.of("a.E"), namesIn("enum:a.E"));
        assertEquals(java.util.Set.of("a.E"), namesIn("list:enum:a.E"));
        assertEquals(java.util.Set.of("a.E"), namesIn("array:set:list:orderedset:enum:a.E"));
        assertEquals(java.util.Set.of("k.K", "v.V"), namesIn("map:{@AtomicSerial:k.K}{enum:v.V}"));
        assertEquals(java.util.Set.of("k.K", "v.V", "w.W"), namesIn("orderedmap:{list:@AtomicSerial:k.K}{map:{enum:v.V}{@AtomicSerial:w.W}}"));
        assertEquals(java.util.Set.of("a.B$C"), namesIn("@AtomicSerial:a.B$C"));
        // the loader receives everything up to the end of the part, so an array-class name is a name: it loads the element class
        assertEquals(java.util.Set.of("a.E"), namesIn("enum:[La.E;"));
        assertEquals(java.util.Set.of("a.E"), namesIn("array:enum:[[La.E;"));
        assertEquals(java.util.Set.of("a.B"), namesIn("array:@AtomicSerial:[La.B;"));
        assertEquals(java.util.Set.of("k.K", "v.V"), namesIn("map:{@AtomicSerial:[Lk.K;}{enum:[Lv.V;}"));
        assertEquals(java.util.Set.of("a.E:foo"), namesIn("enum:a.E:foo"), "whatever follows the marker is the name");
        assertEquals(java.util.Set.of("a.E x"), namesIn("enum:a.E x"));
        for (String none : new String[] {"int", "java.lang.String", "list:java.lang.String", "map:{int}{java.lang.String}", "any",
                "array:int", "@AtomicSerial", "@AtomicSerial:", "enum:", "list:enum:", "x@AtomicSerial:a.B", ""}) {
            assertTrue(namesIn(none).isEmpty(), "'" + none + "' names no class: " + namesIn(none));
        }
    }

    @Test
    void aHostileTokenIsScannedInBoundedTime() {
        // 100 000 nested levels, then a name at the bottom: the scan is linear and recursion-free
        String token = "list:".repeat(100_000) + "enum:evil.E";
        assertTimeoutPreemptively(Duration.ofSeconds(5), () -> assertEquals(java.util.Set.of("evil.E"), namesIn(token)));
        // and a long run of markers: the loader would be handed one 700 000-character name, which no class can have
        String noise = "@AtomicSerial:".repeat(50_000);
        assertTimeoutPreemptively(Duration.ofSeconds(5), () -> {
            DerException e = assertThrows(DerException.class, () -> namesIn(noise));
            assertTrue(e.getMessage().contains("longer than any class can have"));
        });
        // many short markers, each with its own short name: linear
        String many = "map:" + "{@AtomicSerial:a.B}".repeat(30_000);
        assertTimeoutPreemptively(Duration.ofSeconds(5), () -> assertEquals(java.util.Set.of("a.B"), namesIn(many)));
    }

    @Test
    void classNamesAreNormalisedToTheClassTheLoaderWouldBeAsked() {
        for (boolean values : new boolean[] {false, true}) {
            assertEquals("a.B", StreamPolicyMeter.normalise("a.B", values));
            assertEquals("a.B", StreamPolicyMeter.normalise("[La.B;", values));
            assertEquals("a.B", StreamPolicyMeter.normalise("[[[La.B;", values));
            assertEquals("a.B$C", StreamPolicyMeter.normalise("[La.B$C;", values));
            assertNull(StreamPolicyMeter.normalise("[I", values), "a primitive array loads no class of ours");
            assertNull(StreamPolicyMeter.normalise("[[J", values));
            assertNull(StreamPolicyMeter.normalise("[Z", values));
            assertNull(StreamPolicyMeter.normalise("", values));
            assertEquals("integer", StreamPolicyMeter.normalise("integer", values));
            // a malformed array name is judged as written: the loader receives that string, so a list must too
            assertEquals("[La.B", StreamPolicyMeter.normalise("[La.B", values));
            assertEquals("[La.B;x", StreamPolicyMeter.normalise("[La.B;x", values));
            assertEquals("[L;", StreamPolicyMeter.normalise("[L;", values));
            assertEquals("[Q", StreamPolicyMeter.normalise("[Q", values));
        }
        // a primitive NAME is no class to load only where delivery maps it to the primitive itself (a Class-valued field)
        for (String prim : new String[] {"boolean", "byte", "char", "short", "int", "long", "float", "double", "void"}) {
            assertEquals(prim, StreamPolicyMeter.normalise(prim, false), "a record, enum or interface called " + prim + " is a load");
            assertNull(StreamPolicyMeter.normalise(prim, true));
        }
    }

    @Test
    void aPrimitiveNameIsCountedAsAClassExceptWhereItIsAClassValue() throws Exception {
        StreamPolicyMeter m = new StreamPolicyMeter(DerStreamPolicy.BUILT_IN);
        m.classValue("int");
        m.classValue("void");
        assertEquals(0, m.distinctClasses());
        m.className("int");
        assertEquals(1, m.distinctClasses());
    }

    @Test
    void anOverlongNameIsRefusedAndNeverStored() {
        StreamPolicyMeter m = new StreamPolicyMeter(DerStreamPolicy.BUILT_IN);
        DerException e = assertThrows(DerException.class, () -> m.className("a".repeat(DerStreamPolicy.MAX_CLASS_NAME_LENGTH + 1)));
        assertTrue(e.getMessage().contains("longer than any class can have"));
        assertEquals(0, m.distinctClasses());
        assertDoesNotThrow(() -> m.className("a".repeat(DerStreamPolicy.MAX_CLASS_NAME_LENGTH)));
    }

    @Test
    void messagesCarryOnlyPrintableTruncatedNames() {
        StreamPolicyMeter m = new StreamPolicyMeter(DerStreamPolicy.BUILT_IN.withMaxDistinctClasses(1));
        assertDoesNotThrow(() -> m.className("first.One"));
        String hostile = "evil.X\n\u001b[31m" + "y".repeat(500);
        // a rejection that quotes a name (rebinding) must not carry control characters or an unbounded string
        String shown = DerStreamPolicy.printable(hostile);
        assertFalse(shown.contains("\n"));
        assertFalse(shown.contains("\u001b"));
        assertTrue(shown.length() < 130 && shown.endsWith("..."), shown);
    }

    @Test
    void theCountersAreInclusiveAtTheirLimits() throws Exception {
        StreamPolicyMeter m = new StreamPolicyMeter(DerStreamPolicy.BUILT_IN.withMaxObjects(2).withMaxElements(3).withMaxDistinctClasses(2));
        m.object();
        m.object();
        assertThrows(DerException.class, m::object);
        m.elements(2);
        m.elements(1);
        assertThrows(DerException.class, () -> m.elements(1));
        m.className("a.A");
        m.className("a.A");
        m.className("b.B");
        assertEquals(2, m.distinctClasses());
        assertThrows(DerException.class, () -> m.className("c.C"));
    }

    // ------------------------------------------------------------------ defaults(): lazy, repeatable failure

    @Test
    void aFloodOfRejectionsIsLoggedAtAFixedRateButEveryOneStillThrows() {
        java.util.concurrent.atomic.AtomicInteger logged = new java.util.concurrent.atomic.AtomicInteger();
        java.util.logging.Logger log = java.util.logging.Logger.getLogger("au.net.zeus.jgdms.der.stream.policy");
        java.util.logging.Handler h = new java.util.logging.Handler() {
            @Override public void publish(java.util.logging.LogRecord r) { logged.incrementAndGet(); }
            @Override public void flush() { }
            @Override public void close() { }
        };
        log.addHandler(h);
        StreamPolicyMeter.resetLogLimiterForTest();
        try {
            int thrown = 0;
            long start = System.nanoTime();
            for (int i = 0; i < 500; i++) {
                StreamPolicyMeter m = new StreamPolicyMeter(DerStreamPolicy.BUILT_IN.withMaxDistinctClasses(1));
                try {
                    m.className("a.A");
                    m.className("b.B" + i);
                } catch (DerException e) {
                    thrown++;
                }
            }
            long seconds = 1 + (System.nanoTime() - start) / 1_000_000_000L;
            assertEquals(500, thrown, "every rejection throws");
            assertTrue(logged.get() <= 5 * seconds + 5, "at most five lines per second were logged: " + logged.get());
            assertTrue(logged.get() >= 1);
        } finally {
            log.removeHandler(h);
        }
    }
}
