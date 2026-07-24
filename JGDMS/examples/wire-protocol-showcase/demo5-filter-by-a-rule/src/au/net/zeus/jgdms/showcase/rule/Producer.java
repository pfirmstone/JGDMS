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

package au.net.zeus.jgdms.showcase.rule;

import au.net.zeus.jgdms.der.object.ObjectCodec;
import au.net.zeus.jgdms.der.schema.AtomicSerialSchemaRecord;
import au.net.zeus.jgdms.der.schema.SchemaChain;
import au.net.zeus.jgdms.der.schema.SchemaGenerator;
import au.net.zeus.jgdms.showcase.rule.records.SurveyObservation;
import au.net.zeus.jgdms.showcase.rule.records.WeatherReading;

import java.io.DataOutputStream;
import java.io.FileOutputStream;
import java.util.List;

/**
 * The producer side. It HAS the record classes on its classpath. It turns several
 * weather readings and one survey observation into their on-the-wire form — each field
 * reduced to canonical bytes, alongside the small shape description ("schema") that
 * travels with them — and writes them to a file for the reader to pick up.
 *
 * <p>This is exactly what a field station does before it sends a reading to a shared
 * space: it reduces the reading to bytes on its own side; the reader only ever sees
 * bytes plus the travelling shape.
 */
public final class Producer {

    /** The weather readings the reader will filter. */
    static final WeatherReading[] WEATHER = {
        new WeatherReading("North-Ridge", 25.4, 60.0, 1),  // warm, North  -> should match
        new WeatherReading("North-Vale",  14.2, 82.0, 2),  // cool, North  -> should NOT match (too cold)
        new WeatherReading("South-Bay",   31.0, 45.0, 3),  // warm, South  -> should NOT match (wrong name)
        new WeatherReading("Northgate",   22.5, 55.0, 4),  // warm, North  -> should match
        new WeatherReading("East-Field",  19.9, 70.0, 5),  // borderline   -> should NOT match (not above 20)
    };

    /** The survey observation the reader will transform into a local direction vector. */
    static final SurveyObservation SURVEY =
        new SurveyObservation("Control-Point-A", 30.0, 10.0, 100.0);

    public static void main(String[] args) throws Exception {
        if (args.length != 1) {
            System.err.println("usage: Producer <output-file>");
            System.exit(2);
        }

        try (DataOutputStream out = new DataOutputStream(new FileOutputStream(args[0]))) {
            out.writeInt(WEATHER.length);
            for (WeatherReading w : WEATHER) {
                writeRecord(out, w, WeatherReading.class);
            }
            writeRecord(out, SURVEY, SurveyObservation.class);
        }

        System.out.println("Producer (this program HAS the record classes on its classpath):");
        System.out.println("  weather record class .... " + WeatherReading.class.getName());
        System.out.println("  survey record class ..... " + SurveyObservation.class.getName());
        System.out.println();
        System.out.println("  wrote " + WEATHER.length + " weather readings:");
        for (WeatherReading w : WEATHER) {
            System.out.println("      " + w);
        }
        System.out.println("  wrote 1 survey observation:");
        System.out.println("      " + SURVEY);
        System.out.println();
        System.out.println("  each field is now canonical bytes; the shape description travels alongside.");
        System.out.println("  wrote everything to: " + args[0]);
    }

    /** Writes {@code instance} as: [schema-chain block(s)] then [payload block]. Both class-free to read back. */
    private static void writeRecord(DataOutputStream out, Object instance, Class<?> clazz) throws Exception {
        SchemaChain.Result chain = SchemaGenerator.generateChain(clazz);
        byte[] payload = ObjectCodec.encodeHierarchy(instance, chain);

        // The shape description ("schema") that travels in the message: one block per class in the chain.
        List<AtomicSerialSchemaRecord> leafFirst = chain.chain();
        out.writeInt(leafFirst.size());
        for (AtomicSerialSchemaRecord record : leafFirst) {
            Blocks.writeBlock(out, record.encode());
        }
        Blocks.writeBlock(out, payload);
    }
}
