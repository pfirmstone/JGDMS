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

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.opentest4j.AssertionFailedError;
import org.opentest4j.TestAbortedException;

/**
 * Tests the rule that a requested tier is never silently skipped: {@link CoreMathTestSupport#requireClone}
 * ABORTS (reported as skipped) when the CORE-MATH clone is absent and the full tier was not requested, FAILS
 * when it was requested with {@code -Dcore.math.full=true}, and returns when the clone is present. Hermetic:
 * it builds a fake clone in a temporary directory. Runtime tier: FAST (default suite).
 *
 * <h2>SEALED PREDICTIONS (fix pass, written before the first run of this test)</h2>
 * <ol>
 *   <li><b>F12</b> absent clone, full tier requested: an {@code AssertionFailedError} whose message says
 *       FAILED and names {@code -Dcore.math.full=true}; absent clone, not requested: a
 *       {@code TestAbortedException} whose message says SKIPPED; present clone (a directory with
 *       {@code src/binary64/sin/sin.c} and {@code cos/cos.c}): neither, for both settings. 4 of 4 cases as stated.</li>
 * </ol>
 */
class CoreMathRequireCloneTest {

    @Test
    void absentCloneFailsLoudlyWhenTheFullTierIsRequestedAndSkipsOtherwise(@TempDir Path tmp) throws IOException {
        Path missing = tmp.resolve("no-such-clone");
        AssertionFailedError failed = assertThrows(AssertionFailedError.class, () -> CoreMathTestSupport.requireClone(missing, true, "a full-tier test"));
        assertTrue(failed.getMessage().contains("FAILED") && failed.getMessage().contains("-Dcore.math.full=true"), failed.getMessage());
        TestAbortedException aborted = assertThrows(TestAbortedException.class, () -> CoreMathTestSupport.requireClone(missing, false, "a full-tier test"));
        assertTrue(aborted.getMessage().contains("SKIPPED"), aborted.getMessage());

        Path clone = tmp.resolve("clone");
        Files.createDirectories(clone.resolve("src/binary64/sin"));
        Files.createDirectories(clone.resolve("src/binary64/cos"));
        Files.writeString(clone.resolve("src/binary64/sin/sin.c"), "/* fake */\n");
        Files.writeString(clone.resolve("src/binary64/cos/cos.c"), "/* fake */\n");
        assertDoesNotThrow(() -> CoreMathTestSupport.requireClone(clone, true, "a full-tier test"));
        assertDoesNotThrow(() -> CoreMathTestSupport.requireClone(clone, false, "a full-tier test"));
    }
}
