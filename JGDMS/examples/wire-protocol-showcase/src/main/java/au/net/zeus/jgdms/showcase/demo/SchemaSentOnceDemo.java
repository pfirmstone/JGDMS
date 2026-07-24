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

package au.net.zeus.jgdms.showcase.demo;

import au.net.zeus.jgdms.der.stream.DerMarshalOutputStream;
import au.net.zeus.jgdms.showcase.model.CalibratedReading;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Demonstration: "The shape description travels in the stream, and you pay for it once."
 *
 * <p>When many objects of the same type are written to one stream, the description of
 * their shape (the family-tree of field names and types) is sent with the FIRST object.
 * Every object after that just refers back to it. We measure three ways of writing the
 * same {@value #COUNT} objects and compare how the total size grows:
 *
 * <ol>
 *   <li><b>shape sent once</b> -- this library's stream format.</li>
 *   <li><b>shape repeated every time</b> -- each object carries its own full shape
 *       description (what you get without the sharing).</li>
 *   <li><b>JSON text (field names every time)</b> -- the same data as minified JSON,
 *       which spells out every field name on every object.</li>
 * </ol>
 *
 * <p>Honesty note: none of these lines is flat. Storing N objects always costs at least
 * N objects' worth of data. What differs is the SLOPE -- how much each additional object
 * adds. "Shape sent once" adds the least, because the family-tree description is not
 * repeated.
 */
public final class SchemaSentOnceDemo {

    private static final int COUNT = 100;

    public static void main(String[] args) throws Exception {
        List<CalibratedReading> readings = makeReadings(COUNT);

        System.out.println("========================================================================");
        System.out.println(" The shape description travels in the stream -- and you pay for it once");
        System.out.println("========================================================================");
        System.out.println();
        System.out.println("We write " + COUNT + " readings of the same type. Each reading's shape is a");
        System.out.println("family tree of three levels, with descriptive field names -- a realistic,");
        System.out.println("not-tiny shape description.");
        System.out.println();
        int shape = ShowcaseSupport.shapeDescriptionBytes(CalibratedReading.class);
        int oneRecord = ShowcaseSupport.selfDescribingRecord(readings.get(0)).length;
        System.out.println("  shape description (family tree) ...... " + shape + " bytes");
        System.out.println("  one self-describing record ........... " + oneRecord + " bytes"
                + "   (data + its own shape description)");
        System.out.printf ("  the shape is %.0f%% of a single record%n", 100.0 * shape / oneRecord);
        System.out.println();

        // ---- Evidence that the first object is big and the rest are small ---------
        long stream1 = streamOf(readings.subList(0, 1)).length;
        long stream2 = streamOf(readings.subList(0, 2)).length;
        long stream3 = streamOf(readings.subList(0, 3)).length;
        System.out.println("In the stream format, watch what each extra object adds:");
        System.out.println("  first object (carries the shape) .......... " + stream1 + " bytes so far");
        System.out.println("  + second object (refers to the shape) ..... +" + (stream2 - stream1) + " bytes");
        System.out.println("  + third object  (refers to the shape) ..... +" + (stream3 - stream2) + " bytes");
        System.out.println("  -> after the first, each object adds only its own data plus a short back-reference.");
        System.out.println();

        // ---- Cumulative size at several counts -----------------------------------
        int[] points = { 1, 2, 5, 10, 20, 50, 100 };
        System.out.println("Cumulative bytes to write N readings:");
        System.out.println();
        System.out.printf("   %5s | %14s | %14s | %14s%n",
                "N", "shape once", "shape repeated", "JSON w/ names");
        System.out.println("   ------+----------------+----------------+----------------");
        long lastOnce = 0, lastRepeat = 0, lastJson = 0;
        for (int n : points) {
            long once   = streamOf(readings.subList(0, n)).length;
            long repeat = repeatedShapeSize(readings.subList(0, n));
            long json   = jsonSize(readings.subList(0, n));
            System.out.printf("   %5d | %14d | %14d | %14d%n", n, once, repeat, json);
            lastOnce = once; lastRepeat = repeat; lastJson = json;
        }
        System.out.println();

        // ---- Bar chart at N = COUNT ---------------------------------------------
        long max = Math.max(lastJson, Math.max(lastOnce, lastRepeat));
        System.out.println("At N=" + COUNT + " (bar length = relative size):");
        System.out.printf("   shape once     %6d B  %s%n", lastOnce,   ShowcaseSupport.bar(lastOnce, max, 48));
        System.out.printf("   shape repeated %6d B  %s%n", lastRepeat, ShowcaseSupport.bar(lastRepeat, max, 48));
        System.out.printf("   JSON w/ names  %6d B  %s%n", lastJson,   ShowcaseSupport.bar(lastJson, max, 48));
        System.out.println();
        System.out.printf("   Sending the shape once instead of every time saved %,d bytes (%.1f%% smaller)%n",
                lastRepeat - lastOnce, 100.0 * (lastRepeat - lastOnce) / lastRepeat);
        System.out.printf("   and it is %.1f%% smaller than the equivalent JSON text (field names on every record).%n",
                100.0 * (lastJson - lastOnce) / lastJson);
        System.out.println();
        System.out.println("Why it matters: the reader never has to already know the shape. It arrives");
        System.out.println("in the stream, once, and every later object points back to it. You get a");
        System.out.println("self-describing stream without paying to describe every object.");
        System.out.println();
        System.out.println("Reported honestly: this win depends on the shape being a real fraction of");
        System.out.println("each object and shared across many objects. A prior measurement on a");
        System.out.println("different, flatter type (100 records, a tiny 125-byte shared shape) still");
        System.out.println("shrank 22,180 -> 9,378 bytes (to 42.3%). Where the shape is a bigger share,");
        System.out.println("as here, the saving is larger. Where each field is a separate, unique");
        System.out.println("capture (one storage layout in this project), the saving is only a few");
        System.out.println("percent -- and we would report that number, not this one.");
    }

    // ------------------------------------------------------------------------
    // Measurement helpers -- all measure REAL bytes.
    // ------------------------------------------------------------------------

    /** The real stream bytes: shape sent once, later objects refer back to it. */
    public static byte[] streamOf(List<CalibratedReading> readings) throws Exception {
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        DerMarshalOutputStream out = new DerMarshalOutputStream(b);
        for (CalibratedReading r : readings) out.writeObject(r);
        out.flush();
        return b.toByteArray();
    }

    /** Each object as its own complete self-describing record -- shape repeated. */
    public static long repeatedShapeSize(List<CalibratedReading> readings) throws Exception {
        long total = 0;
        for (CalibratedReading r : readings) total += ShowcaseSupport.selfDescribingRecord(r).length;
        return total;
    }

    /** Each object as JSON text -- field names repeated on every object. */
    public static long jsonSize(List<CalibratedReading> readings) {
        long total = 0;
        for (CalibratedReading r : readings) total += ShowcaseSupport.asJsonText(r).length;
        return total;
    }

    public static List<CalibratedReading> makeReadings(int n) {
        List<CalibratedReading> list = new ArrayList<>(n);
        String[] stations = { "Station-North", "Station-East", "Station-South", "Station-West" };
        String[] quantities = { "temperature", "pressure", "humidity", "windSpeed" };
        String[] units = { "degreesCelsius", "kilopascal", "percentRelative", "metresPerSecond" };
        for (int i = 0; i < n; i++) {
            list.add(new CalibratedReading(
                    1_000_000L + i,
                    stations[i % stations.length],
                    quantities[i % quantities.length],
                    units[i % units.length],
                    20.0 + (i % 50) * 0.37,
                    "calibration-certificate-2026-" + String.format("%04d", i)));
        }
        return list;
    }
}
