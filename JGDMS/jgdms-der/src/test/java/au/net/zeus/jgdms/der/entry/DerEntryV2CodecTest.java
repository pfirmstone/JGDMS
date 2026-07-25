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

import org.apache.river.api.io.EntryV2Codec;
import org.junit.jupiter.api.Test;

import java.util.ServiceLoader;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The EntryV2Codec SPI seam end-to-end: ServiceLoader discovery + reflective round-trip. */
public class DerEntryV2CodecTest {

    public static class Reading {
        public Double temperature;
        public String station;
        public Reading() {}
        public Reading(Double t, String s) { this.temperature = t; this.station = s; }
    }

    @Test
    public void serviceLoaderDiscoversProvider() {
        EntryV2Codec codec = ServiceLoader.load(EntryV2Codec.class,
                getClass().getClassLoader()).iterator().next();
        assertNotNull(codec);
        assertTrue(codec instanceof DerEntryV2Codec);
    }

    @Test
    public void reflectiveRoundTripThroughSpi() throws Exception {
        EntryV2Codec codec = new DerEntryV2Codec();
        EntryV2Codec.Encoded enc = codec.encodeReflective(Reading.class, new Reading(21.5, "North"));
        EntryV2Codec.Decoded dec = codec.decode(enc.body);

        // slice bytes preserved through the SPI round-trip
        assertEquals(enc.sliceBytes.length, dec.sliceBytes.length);
        for (int i = 0; i < enc.sliceBytes.length; i++) {
            assertArrayEquals(enc.sliceBytes[i], dec.sliceBytes[i]);
        }
        // field order: alpha -> [station, temperature]
        Object station = codec.decodeFieldValue(dec.sliceBytes[0], String.class, dec);
        Object temp = codec.decodeFieldValue(dec.sliceBytes[1], Double.class, dec);
        assertEquals("North", station);
        assertEquals(21.5, temp);
    }

    @Test
    public void serialEntrySingleRecordRoundTrip() throws Exception {
        EntryV2Codec codec = new DerEntryV2Codec();
        EntryV2Codec.Encoded enc = codec.encodeSerialEntry(
                "com.example.Foo", new String[0],
                new String[]{ "a", "b" },
                new Class<?>[]{ String.class, Integer.class },
                new Object[]{ "hello", 7 });
        EntryV2Codec.Decoded dec = codec.decode(enc.body);
        assertEquals(2, dec.sliceBytes.length);
        assertEquals("hello", codec.decodeFieldValue(dec.sliceBytes[0], String.class, dec));
        assertEquals(7, codec.decodeFieldValue(dec.sliceBytes[1], Integer.class, dec));
    }
}
