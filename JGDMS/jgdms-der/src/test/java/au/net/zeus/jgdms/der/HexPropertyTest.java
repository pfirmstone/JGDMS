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

package au.net.zeus.jgdms.der;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import au.net.zeus.jgdms.der.util.Hex;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;

/**
 * Sample jqwik property test (Task 0.1 acceptance: jqwik is wired and a
 * property test runs). Establishes the round-trip discipline the rest of the
 * codec suite relies on: for arbitrary bytes,
 * {@code fromHex(toHex(b)) == b}, and the encoding is exactly twice the length.
 */
class HexPropertyTest {

    @Property
    void hexRoundTripsArbitraryBytes(@ForAll byte[] bytes) {
        String hex = Hex.toHex(bytes);
        assertEquals(bytes.length * 2, hex.length());
        assertArrayEquals(bytes, Hex.fromHex(hex));
    }
}
