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
package net.jini.jeri.ssl;

import org.junit.Assert;
import org.junit.Test;

/**
 * The bound of {@link RecordCounter}: {@link RecordCounter#FREE} records, and
 * one more for every {@link RecordCounter#BYTES_PER_RECORD} bytes sent, the
 * bytes counted up to the end of the header of the record that is being
 * counted.  The numbers here are the ones that the bound gives, written down
 * so that a change to either constant, or to the way the bytes are counted,
 * has to change them knowingly.  A record with a wire size of 256 bytes or
 * more can never be refused, whatever comes before it, as long as the
 * records before it have not used up the free ones; the smallest record
 * that a client can negotiate is 534 bytes (a maximum fragment length of 512
 * and what the cipher adds).
 */
public class RecordCounterTest {

    /** A record of the given wire size (header included) of application data. */
    private static byte[] record(int wire) {
        int body = wire - ClientHelloReader.HEADER;
        byte[] record = new byte[wire];
        record[0] = 23;
        record[1] = 3;
        record[2] = 3;
        record[3] = (byte) (body >>> 8);
        record[4] = (byte) body;
        return record;
    }

    /**
     * Counts {@code count} records of the wire size, one write each.
     *
     * @return the number of the record that was refused, or 0 if none was
     */
    private static int refusedAt(RecordCounter counter, byte[] record, int count) {
        for (int i = 1; i <= count; i++) {
            try {
                counter.count(record, 0, record.length);
            } catch (ClientHelloReader.NotTlsException refused) {
                return i;
            }
        }
        return 0;
    }

    @Test
    public void testTheBoundIsSixteenFreeAndOnePer256Bytes() {
        Assert.assertEquals(16, RecordCounter.FREE);
        Assert.assertEquals(256, RecordCounter.BYTES_PER_RECORD);
    }

    /** A record of 256 bytes or more pays for itself: never refused, however many. */
    @Test
    public void testRecordsOf256BytesAreAcceptedIndefinitely() {
        Assert.assertEquals(0, refusedAt(new RecordCounter(), record(256), 10_000));
        Assert.assertEquals(0, refusedAt(new RecordCounter(), record(257), 10_000));
    }

    /**
     * 255 bytes is one short: each record puts the count one record in
     * debt for 1/256 of a record, and the 16 free records run out at record
     * 3847.  Record k is refused when k is more than 16 plus the whole
     * number of 256s in the bytes up to the end of its own header,
     * (k - 1) * 255 + 5; at k = 3846 that is 980 480 bytes, 3830 whole
     * 256s, 3846 allowed, and at k = 3847 it is 980 735, 3830, 3846 allowed
     * for the 3847th.
     */
    @Test
    public void testRecordsOf255BytesAreRefusedAtRecord3847() {
        Assert.assertEquals(3847, refusedAt(new RecordCounter(), record(255), 10_000));
    }

    @Test
    public void testTheMessageGivesTheRecordsAndTheBytes() {
        RecordCounter counter = new RecordCounter();
        byte[] record = record(255);
        for (int i = 1; i < 3847; i++) {
            try {
                counter.count(record, 0, record.length);
            } catch (ClientHelloReader.NotTlsException e) {
                Assert.fail("refused early, at " + i);
            }
        }
        try {
            counter.count(record, 0, record.length);
            Assert.fail();
        } catch (ClientHelloReader.NotTlsException refused) {
            Assert.assertEquals("more records than a handshake needs: 3847 in 980735 bytes",
                                refused.getMessage());
        }
    }

    /** Records of 25 bytes carry one byte each: the free ones, and two more. */
    @Test
    public void testRecordsOf25BytesAreRefusedAtRecord18() {
        Assert.assertEquals(18, refusedAt(new RecordCounter(), record(25), 10_000));
    }

    /** The smallest record that a client can negotiate, 512 and what the cipher adds, is far inside. */
    @Test
    public void testRecordsOf534BytesAreAcceptedIndefinitely() {
        Assert.assertEquals(0, refusedAt(new RecordCounter(), record(534), 10_000));
    }

    /** Records of the longest size, and what the cipher adds, are far inside. */
    @Test
    public void testRecordsOf16KBytesAreAcceptedIndefinitely() {
        Assert.assertEquals(0, refusedAt(new RecordCounter(), record(16384 + 5 + 256), 10_000));
        Assert.assertEquals(0, refusedAt(new RecordCounter(), record(16406), 10_000));
    }

    /**
     * Two big records do not buy a long run of tiny ones.  The two records of
     * 16 406 bytes are 32 812 bytes, 128 whole 256s, and with the 16 free
     * records the 157th tiny record of 23 bytes (record 159 in all, at 32 812
     * + 156 * 23 + 5 = 36 405 bytes, 142 whole 256s, 158 allowed) is the first
     * that is more than the bytes allow.
     */
    @Test
    public void testTwoBigRecordsThenTinyOnesAreRefusedAtTheHundredAndFiftySeventhTinyOne() {
        RecordCounter counter = new RecordCounter();
        byte[] big = record(16406);
        byte[] tiny = record(23);
        int tinyCount = 0;
        try {
            counter.count(big, 0, big.length);
            counter.count(big, 0, big.length);
            for (;;) {
                counter.count(tiny, 0, tiny.length);
                tinyCount++;
                Assert.assertTrue("no end", tinyCount < 100_000);
            }
        } catch (ClientHelloReader.NotTlsException refused) {
            Assert.assertEquals("the 157th tiny record is refused, 156 are not",
                                156, tinyCount);
            Assert.assertTrue(refused.getMessage(),
                              refused.getMessage().startsWith(
                                  "more records than a handshake needs: 159 in "));
        }
    }

    /** The bound does not depend on where the writes end. */
    @Test
    public void testTheBoundDoesNotDependOnTheChunks() throws Exception {
        byte[] record = record(255);
        byte[] all = new byte[record.length * 5000];
        for (int i = 0; i < 5000; i++) {
            System.arraycopy(record, 0, all, i * record.length, record.length);
        }
        for (int chunk : new int[] { 1, 3, 5, 254, 255, 256, 1000, all.length }) {
            RecordCounter counter = new RecordCounter();
            try {
                for (int at = 0; at < all.length; at += chunk) {
                    counter.count(all, at, Math.min(chunk, all.length - at));
                }
                Assert.fail("chunk " + chunk);
            } catch (ClientHelloReader.NotTlsException refused) {
                Assert.assertTrue("chunk " + chunk + ": " + refused.getMessage(),
                    refused.getMessage().startsWith(
                        "more records than a handshake needs: 3847 in "));
            }
        }
    }
}
