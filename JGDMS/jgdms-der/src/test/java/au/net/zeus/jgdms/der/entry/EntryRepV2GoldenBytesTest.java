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

package au.net.zeus.jgdms.der.entry;

import au.net.zeus.jgdms.der.object.fixtures.Alpha;
import au.net.zeus.jgdms.der.object.fixtures.Beta;
import org.apache.river.api.io.EntryV2Codec;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Frozen-bytes oracle for the Entry wire form (EntryRepV2): the body and every per-field slice are the
 * match contract, so they must stay byte-identical across any change to how they are composed.
 * Fixtures were generated from the byte[]-composition encoder before the DerEncoder migration
 * (A2); regenerate only for a deliberate wire change with {@code -Dgolden.generate=true}.
 */
class EntryRepV2GoldenBytesTest {

    private static final boolean GENERATE = Boolean.getBoolean("golden.generate");
    private static final Path DIR = Paths.get("src/test/resources/golden-entry");

    public static class TwoFields {
        public Alpha first;
        public Beta second;
        public TwoFields() {}
    }

    public static class SameTwice {
        public Alpha first;
        public Alpha second;
        public SameTwice() {}
    }

    private static final EntryV2Codec CODEC = new DerEntryV2Codec();

    private static EntryV2Codec.Encoded reflective(Class<?> c, Object o) {
        try {
            return CODEC.encodeReflective(c, o);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static Map<String, Supplier<EntryV2Codec.Encoded>> corpus() {
        Map<String, Supplier<EntryV2Codec.Encoded>> c = new LinkedHashMap<>();
        c.put("reading", () -> reflective(DerEntryV2CodecTest.Reading.class,
                new DerEntryV2CodecTest.Reading(21.5, "North")));
        c.put("reading-nulls", () -> reflective(DerEntryV2CodecTest.Reading.class,
                new DerEntryV2CodecTest.Reading(null, null)));
        c.put("two-different-atomic-fields", () -> {
            TwoFields t = new TwoFields();
            t.first = new Alpha(1, "alpha");
            t.second = new Beta(1, "alpha", 2, "beta");
            return reflective(TwoFields.class, t);
        });
        c.put("same-atomic-class-twice", () -> {
            SameTwice t = new SameTwice();
            t.first = new Alpha(1, "alpha");
            t.second = new Alpha(2, "other");
            return reflective(SameTwice.class, t);
        });
        c.put("serial-entry", () -> {
            try {
                return CODEC.encodeSerialEntry("com.example.Foo", new String[0],
                        new String[]{"a", "b"}, new Class<?>[]{String.class, Integer.class},
                        new Object[]{"hello", 7});
            } catch (IOException e) {
                throw new IllegalStateException(e);
            }
        });
        return c;
    }

    private static String hex(byte[] b) {
        StringBuilder sb = new StringBuilder(b.length * 2);
        for (byte x : b) sb.append(String.format("%02x", x & 0xFF));
        return sb.toString();
    }

    private static String render(EntryV2Codec.Encoded e) {
        StringBuilder sb = new StringBuilder("body=").append(hex(e.body)).append('\n');
        for (int i = 0; i < e.sliceBytes.length; i++) {
            sb.append("slice").append(i).append('=').append(hex(e.sliceBytes[i])).append('\n');
        }
        return sb.toString();
    }

    @TestFactory
    Stream<DynamicTest> entryBytesAreFrozen() {
        return corpus().entrySet().stream().map(e -> DynamicTest.dynamicTest(e.getKey(), () -> {
            String now = render(e.getValue().get());
            assertEquals(now, render(e.getValue().get()), "non-deterministic");
            Path f = DIR.resolve(e.getKey() + ".hex");
            if (GENERATE) {
                Files.createDirectories(DIR);
                Files.write(f, now.getBytes(StandardCharsets.US_ASCII));
                return;
            }
            assertTrue(Files.exists(f), "missing golden fixture " + f);
            // The fixture is text: a checkout that converts line endings (Windows core.autocrlf) must not change what is compared.
            String frozen = new String(Files.readAllBytes(f), StandardCharsets.US_ASCII).replace("\r\n", "\n");
            assertEquals(frozen, now, e.getKey() + ": Entry bytes changed");
        }));
    }
}
