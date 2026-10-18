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
        assertTrue(p.allowClasses().isEmpty());
        assertTrue(p.denyClasses().isEmpty());
        assertEquals(p.toString(), DerStreamPolicy.BUILT_IN.toString());
    }

    @Test
    void theShippedDefaultMatchesTheBuiltInWhenNoSystemPropertyIsSet() {
        // the test JVM sets none of them; a JVM that does would be testing its own configuration
        boolean set = System.getProperties().stringPropertyNames().stream().anyMatch(n -> n.startsWith(DerStreamPolicy.PROP_PREFIX));
        if (!set) {
            assertEquals(DerStreamPolicy.BUILT_IN.toString(), DerStreamPolicy.DEFAULT.toString());
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

    // ------------------------------------------------------------------ allow/deny entry grammar

    @Test
    void entriesAreExactNamesOrPackageWildcards() {
        DerStreamPolicy p = from("denyClasses", "a.b.C, a.b.C$D , x.*, y.z.**, TopLevel, ,");
        assertEquals(List.of("a.b.C", "a.b.C$D", "x.*", "y.z.**", "TopLevel"), p.denyClasses());
    }

    @Test
    void aMalformedEntryRefusesToStartRatherThanWeakenThePolicy() {
        for (String bad : new String[] {"a..b", ".a", "a.", "a b", "a.b.***", "*", "**", "a.*.b", "a.**.b", "-x", "a/b", "a;b",
                "a.b(", "1a", "a.1b", "a.b.*x"}) {
            for (String prop : new String[] {"denyClasses", "allowClasses"}) {
                IllegalStateException e = assertThrows(IllegalStateException.class, () -> from(prop, "good.Name, " + bad), bad);
                assertTrue(e.getMessage().contains("refusing to start"), e.getMessage());
            }
            assertThrows(IllegalArgumentException.class, () -> DerStreamPolicy.BUILT_IN.withDenyClasses(bad));
            assertThrows(IllegalArgumentException.class, () -> DerStreamPolicy.BUILT_IN.withAllowClasses(bad));
        }
    }

    @Test
    void aHostileEntryIsPrintedSafelyInTheMessage() {
        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> from("denyClasses", "ok.Name,evil\nINJECTED \u001b[31mred"));
        assertFalse(e.getMessage().contains("\n"), e.getMessage());
        assertFalse(e.getMessage().contains("\u001b"));
    }

    @Test
    void wildcardsMatchTheDocumentedNames() {
        DerStreamPolicy p = DerStreamPolicy.BUILT_IN.withDenyClasses("a.b.C, p.q.*, r.s.**, Bare");
        assertEquals("a.b.C", p.deniedBy("a.b.C"));
        assertNull(p.deniedBy("a.b.D"));
        assertNull(p.deniedBy("a.b.C.E"), "an exact entry is exact");
        assertNull(p.deniedBy("a.b.C$Inner"), "an exact entry does not cover nested classes");
        assertEquals("p.q.*", p.deniedBy("p.q.X"));
        assertEquals("p.q.*", p.deniedBy("p.q.X$Inner"), "a package wildcard covers nested classes of its classes");
        assertNull(p.deniedBy("p.q.sub.X"), "pkg.* is the package alone");
        assertNull(p.deniedBy("p.qq.X"));
        assertEquals("r.s.**", p.deniedBy("r.s.X"));
        assertEquals("r.s.**", p.deniedBy("r.s.t.u.X"));
        assertNull(p.deniedBy("r.sx.X"), "a tree entry matches whole package segments");
        assertNull(p.deniedBy("r.s"), "the tree root is not a class");
        assertEquals("Bare", p.deniedBy("Bare"));
        assertNull(p.deniedBy("x.Bare"));
        assertNull(DerStreamPolicy.BUILT_IN.deniedBy("anything.At.All"));
    }

    @Test
    void anEmptyAllowListMeansNoRestrictionAndANonEmptyOneRestricts() {
        assertTrue(DerStreamPolicy.BUILT_IN.allowed("any.Thing"));
        DerStreamPolicy p = DerStreamPolicy.BUILT_IN.withAllowClasses("ok.**");
        assertTrue(p.allowed("ok.A"));
        assertTrue(p.allowed("ok.sub.A"));
        assertFalse(p.allowed("no.A"));
        assertTrue(p.hasAllowList());
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
        for (String none : new String[] {"int", "java.lang.String", "list:java.lang.String", "map:{int}{java.lang.String}", "any",
                "array:int", "@AtomicSerial", "@AtomicSerial:", "enum:", "list:enum:", "x@AtomicSerial:a.B", ""}) {
            assertTrue(namesIn(none).isEmpty(), "'" + none + "' names no class: " + namesIn(none));
        }
    }

    @Test
    void aHostileTokenIsScannedInBoundedTime() {
        // 100 000 nested levels, then a denied name at the bottom: the scan is linear and recursion-free
        String token = "list:".repeat(100_000) + "enum:evil.E";
        assertTimeoutPreemptively(Duration.ofSeconds(5), () -> {
            StreamPolicyMeter m = new StreamPolicyMeter(DerStreamPolicy.BUILT_IN.withDenyClasses("evil.E"));
            DerException e = assertThrows(DerException.class, () -> m.wireType(token));
            assertTrue(e.getMessage().contains("is denied by"));
        });
        // and a long run of marker-like text without a name
        String noise = "@AtomicSerial:".repeat(50_000);
        assertTimeoutPreemptively(Duration.ofSeconds(5), () -> assertTrue(namesIn(noise).isEmpty()));
    }

    @Test
    void classNamesAreNormalisedToTheClassTheLoaderWouldBeAsked() {
        assertEquals("a.B", StreamPolicyMeter.normalise("a.B"));
        assertEquals("a.B", StreamPolicyMeter.normalise("[La.B;"));
        assertEquals("a.B", StreamPolicyMeter.normalise("[[[La.B;"));
        assertEquals("a.B$C", StreamPolicyMeter.normalise("[La.B$C;"));
        assertNull(StreamPolicyMeter.normalise("[I"));
        assertNull(StreamPolicyMeter.normalise("[[J"));
        assertNull(StreamPolicyMeter.normalise("int"));
        assertNull(StreamPolicyMeter.normalise("void"));
        assertNull(StreamPolicyMeter.normalise(""));
        assertNull(StreamPolicyMeter.normalise("[La.B"), "an unterminated array name is not a class we can name");
        assertEquals("integer", StreamPolicyMeter.normalise("integer"));
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
    void messagesAndLogsCarryOnlyPrintableTruncatedNames() {
        StreamPolicyMeter m = new StreamPolicyMeter(DerStreamPolicy.BUILT_IN.withDenyClasses("evil.**"));
        String hostile = "evil.X\n\u001b[31m" + "y".repeat(500);
        DerException e = assertThrows(DerException.class, () -> m.className(hostile));
        assertFalse(e.getMessage().contains("\n"));
        assertFalse(e.getMessage().contains("\u001b"));
        assertTrue(e.getMessage().length() < 400, "the name is truncated: " + e.getMessage().length());
        assertTrue(e.getMessage().contains("..."));
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
}
