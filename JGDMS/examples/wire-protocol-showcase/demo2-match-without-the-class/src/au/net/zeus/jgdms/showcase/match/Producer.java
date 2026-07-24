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

package au.net.zeus.jgdms.showcase.match;

import au.net.zeus.jgdms.der.stream.DerMarshalOutputStream;
import au.net.zeus.jgdms.showcase.entry.SensorReadingEntry;
import net.jini.core.constraint.MarshallingFormat;
import org.apache.river.outrigger.proxy.EntryRep;

import java.io.FileOutputStream;
import java.io.OutputStream;

/**
 * The producer side of the "match without the class" demonstration. It HAS the record
 * class ({@link SensorReadingEntry}) on its classpath. It turns a stored record and two
 * templates into their on-the-wire form — where each field is already reduced to
 * canonical bytes — and writes them to a file for the matching server to pick up.
 *
 * <p>This is exactly what a real client does before it sends a record or a template to
 * a shared-space server: it marshals the fields on its own side; the server only ever
 * sees bytes.
 */
public final class Producer {

    public static void main(String[] args) throws Exception {
        if (args.length != 1) {
            System.err.println("usage: Producer <output-file>");
            System.exit(2);
        }

        // The stored record.
        SensorReadingEntry stored = new SensorReadingEntry("Station-North", "temperature", 42);

        // A template that should MATCH it: same measured quantity, "match anything" for
        // the station name and sequence number (null = wildcard).
        SensorReadingEntry matchingTemplate = new SensorReadingEntry(null, "temperature", null);

        // A template that should NOT match: a different station name.
        SensorReadingEntry nonMatchingTemplate = new SensorReadingEntry("Station-South", null, null);

        // Reduce each to its on-the-wire form using the canonical format. Field values
        // become canonical bytes; the record's class name travels as text.
        EntryRep storedRep      = new EntryRep(stored, MarshallingFormat.ATOMIC_DER);
        EntryRep matchingRep    = new EntryRep(matchingTemplate, MarshallingFormat.ATOMIC_DER);
        EntryRep nonMatchingRep = new EntryRep(nonMatchingTemplate, MarshallingFormat.ATOMIC_DER);

        try (OutputStream fileOut = new FileOutputStream(args[0]);
             DerMarshalOutputStream wire = new DerMarshalOutputStream(fileOut)) {
            wire.writeObject(storedRep);
            wire.writeObject(matchingRep);
            wire.writeObject(nonMatchingRep);
            wire.flush();
        }

        System.out.println("Producer (this program HAS the record class on its classpath):");
        System.out.println("  record class .......... " + SensorReadingEntry.class.getName());
        System.out.println("  stored record ......... " + stored);
        System.out.println("  matching template ..... " + matchingTemplate + "   (null = match anything)");
        System.out.println("  non-matching template . " + nonMatchingTemplate);
        System.out.println("  wrote three on-the-wire records to: " + args[0]);
        System.out.println("  (each field is now canonical bytes; the class name is just text)");
    }
}
