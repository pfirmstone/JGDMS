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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

/**
 * The licence headers of the package (phase B2 fix pass; operator rulings 2026-11-12 "Keep the license MIT", "Yes to all four": the MIT notice on {@code U128.java} and the CERN / Inria
 * lines on {@code CoreMathDint.java}). Hermetic: reads the sources of this module under {@code src/main/java} (skipped if the working directory is not the module). Runtime tier: FAST.
 *
 * <h2>SEALED PREDICTION (B2F-L1, written before the first run)</h2>
 * The 14 ported files ({@code CoreMathAcos, Asin, Atan, Atan2, Cos, Dint, InverseTrigTables, Sin, Support, Tables, Tan, TanTables, Tint} and {@code U128}) each carry the MIT permission
 * sentence and a derivation statement and not the word "Apache"; {@code CoreMathDint} carries the three CERN / Inria lines verbatim beside tan.c's; {@code U128} carries the sin.c and cos.c
 * lines; the six own files ({@code CoreMath, CorrectlyRoundedMath, CrMath, MathProvider, MathProviders, StrictMathPlaceholder}) carry the ASF header and not the MIT sentence:
 * 14 + 6 of 20, 0 violations. ELSE: a header was lost or an own file was given a notice it does not need.
 */
class CoreMathLicenceHeadersTest {

    private static final String[] MIT = {
        "CoreMathAcos", "CoreMathAsin", "CoreMathAtan", "CoreMathAtan2", "CoreMathCos", "CoreMathDint", "CoreMathInverseTrigTables", "CoreMathSin", "CoreMathSupport", "CoreMathTables",
        "CoreMathTan", "CoreMathTanTables", "CoreMathTint", "U128",
    };
    private static final String[] APACHE = {"CoreMath", "CorrectlyRoundedMath", "CrMath", "MathProvider", "MathProviders", "StrictMathPlaceholder"};

    private static String source(String name) throws IOException {
        Path dir = Path.of(System.getProperty("user.dir")).resolve("src/main/java/au/net/zeus/jgdms/cel/math");
        Path file = dir.resolve(new StringBuilder(40).append(name).append(".java").toString());
        Assumptions.assumeTrue(Files.isReadable(file), "the module sources are not at " + dir);
        return Files.readString(file, StandardCharsets.UTF_8).replace("\r\n", "\n");
    }

    @Test
    void thePortedFilesCarryTheMitNoticeAndTheOwnFilesTheApacheHeader() throws IOException {
        int checked = 0;
        for (String n : MIT) {
            String s = source(n);
            assertTrue(s.contains("Permission is hereby granted, free of charge"), n + ": the MIT permission sentence is missing");
            assertTrue(s.contains("DERIVATION"), n + ": the derivation statement is missing");
            assertFalse(s.contains("Apache"), n + ": a ported file must not carry an Apache header");
            checked++;
        }
        for (String n : APACHE) {
            String s = source(n);
            assertTrue(s.contains("Licensed to the Apache Software Foundation"), n + ": the ASF header is missing");
            assertFalse(s.contains("Permission is hereby granted"), n + ": an own file must not carry the MIT notice");
            checked++;
        }
        assertTrue(checked == 20);
        String dint = source("CoreMathDint");
        assertTrue(dint.contains(" * Copyright (c) 2022-2025 Paul Zimmermann and Tom Hubrecht\n"));
        assertTrue(dint.contains(" * Copyright (c) 2022 CERN.\n"));
        assertTrue(dint.contains(" * Copyright (c) 2022, 2023 CERN and Inria\n"));
        assertTrue(dint.contains(" * Copyright (c) 2022-2025 CERN and Inria\n"));
        String u128 = source("U128");
        assertTrue(u128.contains(" * Copyright (c) 2026 Paul Zimmermann and Alexei Sibidanov\n"));
        assertTrue(u128.contains(" * Copyright (c) 2026 Paul Zimmermann\n"));
    }
}
