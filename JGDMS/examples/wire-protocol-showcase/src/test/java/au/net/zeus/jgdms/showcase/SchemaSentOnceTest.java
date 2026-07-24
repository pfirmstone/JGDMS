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

package au.net.zeus.jgdms.showcase;

import au.net.zeus.jgdms.der.stream.DerMarshalInputStream;
import au.net.zeus.jgdms.showcase.demo.SchemaSentOnceDemo;
import au.net.zeus.jgdms.showcase.demo.ShowcaseSupport;
import au.net.zeus.jgdms.showcase.model.CalibratedReading;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Automated checks for the "shape sent once" claim. These measure REAL bytes (never a
 * drawn line) and confirm the stream round-trips.
 */
class SchemaSentOnceTest {

    @Test
    void shapeDescriptionIsAMeaningfulFractionOfEachRecord() throws Exception {
        int shape = ShowcaseSupport.shapeDescriptionBytes(CalibratedReading.class);
        int record = ShowcaseSupport.selfDescribingRecord(
                SchemaSentOnceDemo.makeReadings(1).get(0)).length;
        // Honesty guard: the win only holds because the shape is a real fraction.
        assertTrue(shape > record * 0.4,
                "fixture must have a shape description that is a real fraction of each record; "
                + "shape=" + shape + " record=" + record);
    }

    @Test
    void addingAnObjectAfterTheFirstCostsFarLessThanRepeatingTheShape() throws Exception {
        List<CalibratedReading> r = SchemaSentOnceDemo.makeReadings(5);
        int perRecordWithShape = ShowcaseSupport.selfDescribingRecord(r.get(0)).length;

        int s1 = SchemaSentOnceDemo.streamOf(r.subList(0, 1)).length;
        int s2 = SchemaSentOnceDemo.streamOf(r.subList(0, 2)).length;
        int s3 = SchemaSentOnceDemo.streamOf(r.subList(0, 3)).length;

        int costOfSecond = s2 - s1;
        int costOfThird  = s3 - s2;
        // Each additional object must cost much less than a full self-describing record,
        // because the shape description is not repeated.
        assertTrue(costOfSecond < perRecordWithShape * 0.6,
                "second object should skip the shape; cost=" + costOfSecond + " record=" + perRecordWithShape);
        assertTrue(costOfThird < perRecordWithShape * 0.6,
                "third object should skip the shape; cost=" + costOfThird + " record=" + perRecordWithShape);
    }

    @Test
    void shapeSentOnce_isSmallerThanRepeatingIt_atScale() throws Exception {
        List<CalibratedReading> r = SchemaSentOnceDemo.makeReadings(100);
        long once   = SchemaSentOnceDemo.streamOf(r).length;
        long repeat = SchemaSentOnceDemo.repeatedShapeSize(r);
        long json   = SchemaSentOnceDemo.jsonSize(r);
        assertTrue(once < repeat, "shape-once must beat shape-repeated: once=" + once + " repeat=" + repeat);
        assertTrue(once < json, "shape-once must beat JSON text at scale: once=" + once + " json=" + json);
        // A conservative floor on the real saving vs repeating the shape.
        assertTrue((repeat - once) > repeat * 0.5,
                "expected at least a 50% saving vs repeating the shape; once=" + once + " repeat=" + repeat);
    }

    @Test
    void everyObjectRoundTripsFromTheStream() throws Exception {
        List<CalibratedReading> r = SchemaSentOnceDemo.makeReadings(100);
        byte[] stream = SchemaSentOnceDemo.streamOf(r);
        DerMarshalInputStream in = new DerMarshalInputStream(new ByteArrayInputStream(stream));
        for (int i = 0; i < r.size(); i++) {
            Object back = in.readObject();
            assertEquals(r.get(i), back, "object " + i + " must round-trip from the stream");
        }
    }

    @Test
    void demoMainRunsGreen() throws Exception {
        SchemaSentOnceDemo.main(new String[0]);
    }
}
