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

package au.net.zeus.jgdms.cel.math;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

/**
 * The licence headers of the package (phase B2 fix pass; operator rulings 2026-11-12 "Keep the license MIT", "Yes to all four": the MIT notice on {@code U128.java} and the CERN / Inria
 * lines on {@code CoreMathDint.java}; fix pass 2: the {@code log/dint.h} line removed, "Remove it (Recommended)"). Hermetic: reads the sources of this module under {@code src/main/java} and
 * {@code src/test/java}, and the {@code LICENSE} and {@code NOTICE} files of the directory above the module. It FAILS, it does not skip, when it cannot find them (re-check review Fable L-6:
 * a skipped licence check reads as green). Runtime tier: FAST.
 *
 * <h2>SEALED PREDICTION (B2F-L1, written before the first run; phase B2 fix pass 1; SUPERSEDED in part by B2F2-L1 below)</h2>
 * The 14 ported files ({@code CoreMathAcos, Asin, Atan, Atan2, Cos, Dint, InverseTrigTables, Sin, Support, Tables, Tan, TanTables, Tint} and {@code U128}) each carry the MIT permission
 * sentence and a derivation statement and not the word "Apache"; {@code CoreMathDint} carries the three CERN / Inria lines verbatim beside tan.c's; {@code U128} carries the sin.c and cos.c
 * lines; the six own files ({@code CoreMath, CorrectlyRoundedMath, CrMath, MathProvider, MathProviders, StrictMathPlaceholder}) carry the ASF header and not the MIT sentence:
 * 14 + 6 of 20, 0 violations. ELSE: a header was lost or an own file was given a notice it does not need. (RESULT at the first run: HELD. The "three CERN / Inria lines" clause is superseded:
 * the operator's decision of 2026-11-12 removed the {@code log/dint.h} line, so there are two.)
 *
 * <h2>SEALED PREDICTION (B2F2-L1, fix pass 2, 2026-11-12; written before the first run of this version)</h2>
 * Of the 20 files of {@code src/main/java/au/net/zeus/jgdms/cel/math} each has exactly one header kind, 14 MIT (the names above) and 6 ASF (the six above), none with neither (a header-less 21st file
 * would fail); of the files of {@code src/test/java/au/net/zeus/jgdms/cel/math} exactly one carries the MIT notice in its header, {@code CoreMathTintTestCopies.java}; the {@code JGDMS/LICENSE} entry
 * names exactly those 14 + 1 files as carrying the notice and exactly the 6 as the project's own; the lines "Copyright (c) 2022, 2023 CERN and Inria" and "Copyright (c) 2022-2025 CERN and
 * Inria" are in the LICENSE, the NOTICE and the header of {@code CoreMathDint}, and "Copyright (c) 2022 CERN." (the {@code log/dint.h} line) is in none of the three; the sin.c, cos.c and tint.h
 * lines are in {@code U128} and {@code CoreMathTintTestCopies}; the test runs (is not skipped) from the module directory. ELSE: a header was lost, an own file was given a notice, the LICENSE
 * list drifted from the files, or the removed line came back.
 */
class CoreMathLicenceHeadersTest {

    private static final String[] MIT = {
        "CoreMathAcos", "CoreMathAsin", "CoreMathAtan", "CoreMathAtan2", "CoreMathCos", "CoreMathDint", "CoreMathInverseTrigTables", "CoreMathSin", "CoreMathSupport", "CoreMathTables",
        "CoreMathTan", "CoreMathTanTables", "CoreMathTint", "U128",
    };
    private static final String[] APACHE = {"CoreMath", "CorrectlyRoundedMath", "CrMath", "MathProvider", "MathProviders", "StrictMathPlaceholder"};
    private static final String PERMISSION = "Permission is hereby granted, free of charge";
    private static final String ASF = "Licensed to the Apache Software Foundation";
    private static final String PKG = "au/net/zeus/jgdms/cel/math";

    /** The leading comment of a source file (up to the first end of comment). */
    private static String header(String text) {
        int i = text.indexOf("*/");
        return i < 0 ? text : text.substring(0, i + 2);
    }

    private static List<Path> javaFiles(Path dir) throws IOException {
        try (Stream<Path> s = Files.list(dir)) {
            return s.filter(p -> p.getFileName().toString().endsWith(".java")).sorted().collect(Collectors.toList());
        }
    }

    private static String stem(Path p) {
        String n = p.getFileName().toString();
        return n.substring(0, n.length() - ".java".length());
    }

    @Test
    void everyFileOfThePackageHasExactlyOneHeaderKindAndThePortedFilesAreTheFourteen() throws IOException {
        Path dir = CoreMathTestSupport.moduleDir().resolve("src/main/java").resolve(PKG);
        TreeSet<String> mit = new TreeSet<>();
        TreeSet<String> asf = new TreeSet<>();
        for (Path f : javaFiles(dir)) {
            String s = CoreMathTestSupport.readText(f);
            String h = header(s);
            boolean isMit = h.contains(PERMISSION);
            boolean isAsf = h.contains(ASF);
            if (isMit == isAsf) {
                fail(CoreMathTestSupport.msg(f.getFileName(), ": the header must be exactly one of MIT and ASF (MIT ", isMit, ", ASF ", isAsf, ")"));
            }
            if (isMit) {
                assertTrue(s.contains("DERIVATION"), CoreMathTestSupport.msg(f.getFileName(), ": the derivation statement is missing"));
                assertFalse(s.contains("Apache"), CoreMathTestSupport.msg(f.getFileName(), ": a ported file must not carry an Apache header"));
                mit.add(stem(f));
            } else {
                asf.add(stem(f));
            }
        }
        assertEquals(new TreeSet<>(Arrays.asList(MIT)), mit, "the files with the MIT notice");
        assertEquals(new TreeSet<>(Arrays.asList(APACHE)), asf, "the files with the ASF header");
        assertEquals(20, mit.size() + asf.size());
    }

    @Test
    void inTheTestTreeOnlyTheCopyOfAPortedFileCarriesTheMitNotice() throws IOException {
        TreeSet<String> mit = new TreeSet<>();
        for (Path f : javaFiles(CoreMathTestSupport.moduleDir().resolve("src/test/java").resolve(PKG))) {
            if (header(CoreMathTestSupport.readText(f)).contains(PERMISSION)) {
                mit.add(stem(f));
            }
        }
        assertEquals(new TreeSet<>(Arrays.asList("CoreMathTintTestCopies")), mit, "the test files with the MIT notice");
    }

    @Test
    void theLicenceEntryListsExactlyTheFilesThatCarryTheNoticeAndTheOnesThatDoNot() throws IOException {
        String lic = CoreMathTestSupport.readText(CoreMathTestSupport.moduleDir().getParent().resolve("LICENSE")).replaceAll("\\s+", " ");
        int a = lic.indexOf("The ported files are in jgdms-cel");
        int b = lic.indexOf("The other files of the package");
        int c = lic.indexOf("are this project's own code", b);
        assertTrue(a >= 0 && b > a && c > b, "the CORE-MATH entry of LICENSE has the expected sentences");
        TreeSet<String> listed = names(lic.substring(a, b));
        TreeSet<String> own = names(lic.substring(b, c));
        TreeSet<String> expectedListed = new TreeSet<>();
        for (String m : MIT) {
            expectedListed.add(CoreMathTestSupport.msg(m, ".java"));
        }
        expectedListed.add("CoreMathTintTestCopies.java");
        TreeSet<String> expectedOwn = new TreeSet<>();
        for (String o : APACHE) {
            expectedOwn.add(CoreMathTestSupport.msg(o, ".java"));
        }
        assertEquals(expectedListed, listed, "the files the LICENSE entry says carry the notice");
        assertEquals(expectedOwn, own, "the files the LICENSE entry says are the project's own");
        assertTrue(lic.contains("This list therefore names main and test files"), "the LICENSE entry says that it lists test files too");
    }

    private static TreeSet<String> names(String text) {
        TreeSet<String> out = new TreeSet<>();
        Matcher m = Pattern.compile("[A-Za-z0-9]+\\.java").matcher(text);
        while (m.find()) {
            out.add(m.group());
        }
        return out;
    }

    @Test
    void theCopyrightLinesAreVerbatimWhereTheyAreCarriedAndTheRemovedLineIsGone() throws IOException {
        Path root = CoreMathTestSupport.moduleDir().getParent();
        String lic = CoreMathTestSupport.readText(root.resolve("LICENSE"));
        String notice = CoreMathTestSupport.readText(root.resolve("NOTICE"));
        Path main = CoreMathTestSupport.moduleDir().resolve("src/main/java").resolve(PKG);
        String dint = CoreMathTestSupport.readText(main.resolve("CoreMathDint.java"));
        String u128 = CoreMathTestSupport.readText(main.resolve("U128.java"));
        String tintCopy = CoreMathTestSupport.readText(CoreMathTestSupport.moduleDir().resolve("src/test/java").resolve(PKG).resolve("CoreMathTintTestCopies.java"));
        String powDint = "Copyright (c) 2022, 2023 CERN and Inria";
        String powPow = "Copyright (c) 2022-2025 CERN and Inria";
        String logDint = "Copyright (c) 2022 CERN.";
        for (String text : new String[] {lic, notice, dint}) {
            assertTrue(text.contains(powDint), "the pow/dint.h line");
            assertTrue(text.contains(powPow), "the pow/pow.h, pow.c line");
            assertFalse(text.contains(logDint), "the log/dint.h line was removed on the operator's decision of 2026-11-12");
        }
        assertTrue(dint.contains(" * Copyright (c) 2022-2025 Paul Zimmermann and Tom Hubrecht\n"), "tan.c's line in CoreMathDint");
        assertTrue(u128.contains(" * Copyright (c) 2026 Paul Zimmermann and Alexei Sibidanov\n"), "sin.c's line in U128");
        assertTrue(u128.contains(" * Copyright (c) 2026 Paul Zimmermann\n"), "cos.c's line in U128");
        assertTrue(tintCopy.contains(" * Copyright (c) 2023-2024 Paul Zimmermann and Alexei Sibidanov\n"), "tint.h's line in CoreMathTintTestCopies");
        assertTrue(lic.contains("(CoreMathTintTestCopies.java: ") || lic.replaceAll("\\s+", " ").contains("CoreMathTintTestCopies.java: atan2/tint.h"), "the LICENSE entry names the notice of the test copy");
    }
}
