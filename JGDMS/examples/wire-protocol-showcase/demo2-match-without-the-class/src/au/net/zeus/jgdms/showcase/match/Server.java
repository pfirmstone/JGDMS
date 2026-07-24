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

import au.net.zeus.jgdms.der.stream.DerMarshalInputStream;
import org.apache.river.outrigger.proxy.EntryRep;

import java.io.FileInputStream;
import java.io.InputStream;

/**
 * The matching server of the "match without the class" demonstration. It is launched
 * with the record class ({@code SensorReadingEntry}) DELIBERATELY LEFT OFF its
 * classpath. It reads the on-the-wire records the producer wrote, and matches a template
 * against the stored record — by straight byte comparison, never reconstructing the
 * record object, and never needing the record's class.
 *
 * <p>The program checks every claim itself and exits non-zero if any of them fails, so
 * a person watching or a build server can trust the result.
 */
public final class Server {

    private static final String RECORD_CLASS = "au.net.zeus.jgdms.showcase.entry.SensorReadingEntry";

    public static void main(String[] args) throws Exception {
        if (args.length != 1) {
            System.err.println("usage: Server <input-file>");
            System.exit(2);
        }

        System.out.println("Server (this program does NOT have the record class on its classpath):");
        System.out.println();

        // 1. Prove the record class really is absent here.
        boolean classAbsent;
        try {
            Class.forName(RECORD_CLASS);
            classAbsent = false;
        } catch (ClassNotFoundException expected) {
            classAbsent = true;
        }
        System.out.println("  Can this server load " + RECORD_CLASS + "?");
        System.out.println("      " + (classAbsent ? "NO - ClassNotFoundException (the class is not here)"
                                                   : "yes (unexpected - the class IS on the classpath)"));
        System.out.println();

        // 2. Read the three on-the-wire records. This does NOT need the record class:
        //    each field stays as opaque canonical bytes; the class name is just text.
        EntryRep storedRep, matchingRep, nonMatchingRep;
        try (InputStream fileIn = new FileInputStream(args[0]);
             DerMarshalInputStream wire = new DerMarshalInputStream(fileIn)) {
            storedRep      = (EntryRep) wire.readObject();
            matchingRep    = (EntryRep) wire.readObject();
            nonMatchingRep = (EntryRep) wire.readObject();
        }
        System.out.println("  Read three on-the-wire records without loading the record class.");
        System.out.println("  The server can see the class NAME as plain text: \""
                + storedRep.classFor() + "\"");
        System.out.println("  ...but it never turns that name into a loaded class to match.");
        System.out.println();

        // 3. Match by byte comparison. matches() compares the marshalled field bytes;
        //    it never calls readObject and never loads the record class.
        boolean shouldMatch    = matchingRep.matches(storedRep);
        boolean shouldNotMatch = nonMatchingRep.matches(storedRep);
        System.out.println("  matching template  vs stored record -> matches? " + shouldMatch    + "   (expected true)");
        System.out.println("  non-matching template vs stored record -> matches? " + shouldNotMatch + "   (expected false)");
        System.out.println();

        // 4. Show that turning the bytes back INTO the object really would need the class,
        //    which is exactly what matching avoided.
        String reconstructOutcome;
        boolean reconstructNeedsClass;
        try {
            storedRep.entry();
            reconstructOutcome = "unexpectedly succeeded";
            reconstructNeedsClass = false;
        } catch (Throwable t) {
            reconstructNeedsClass = isMissingClass(t);
            reconstructOutcome = t.getClass().getSimpleName()
                    + (t.getMessage() == null ? "" : ": " + t.getMessage())
                    + describeMissingClass(t);
        }
        System.out.println("  If the server tried to rebuild the record OBJECT from the bytes:");
        System.out.println("      " + reconstructOutcome);
        System.out.println("  -> Rebuilding the object needs the class. MATCHING did not.");
        System.out.println();

        boolean allHeld = classAbsent && shouldMatch && !shouldNotMatch && reconstructNeedsClass;
        System.out.println(allHeld
                ? "ALL CLAIMS HELD: the server matched templates by comparing bytes, with no"
                  + " record class present, so there is no way for a hostile record to run code"
                  + " on the server during a match."
                : "A CLAIM DID NOT HOLD - see above.");
        if (!allHeld) System.exit(1);
    }

    /** True if the throwable (its causes, or a shared-space unusable-entry reason) is a missing class. */
    private static boolean isMissingClass(Throwable t) {
        for (Throwable c = t; c != null; c = c.getCause()) {
            if (isClassAbsence(c)) return true;
        }
        // A shared-space unusable-entry failure carries its real reasons in a side array.
        if (t instanceof net.jini.core.entry.UnusableEntryException ue && ue.nestedExceptions != null) {
            for (Throwable n : ue.nestedExceptions) {
                for (Throwable c = n; c != null; c = c.getCause()) {
                    if (isClassAbsence(c)) return true;
                }
            }
        }
        return false;
    }

    private static boolean isClassAbsence(Throwable c) {
        if (c instanceof ClassNotFoundException || c instanceof NoClassDefFoundError) return true;
        String m = c.getMessage();
        return m != null && m.contains(RECORD_CLASS);
    }

    /** A short " (because ...)" note naming the underlying missing-class reason, if any. */
    private static String describeMissingClass(Throwable t) {
        if (t instanceof net.jini.core.entry.UnusableEntryException ue && ue.nestedExceptions != null) {
            for (Throwable n : ue.nestedExceptions) {
                for (Throwable c = n; c != null; c = c.getCause()) {
                    if (isClassAbsence(c)) {
                        return " (because " + c.getClass().getSimpleName()
                                + (c.getMessage() == null ? "" : ": " + c.getMessage()) + ")";
                    }
                }
            }
        }
        return "";
    }
}
