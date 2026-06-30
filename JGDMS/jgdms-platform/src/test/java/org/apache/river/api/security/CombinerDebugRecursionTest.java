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

package org.apache.river.api.security;

import java.io.File;
import java.lang.reflect.Array;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import javax.security.auth.Subject;
import org.junit.Assume;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 * Regression guard for the {@code SubjectDomainCombiner.combine()} debug fix.
 *
 * <p>With {@code AccessController.checkPermission} routed through
 * {@code getContext()} (the unification), a combiner debug that prints private
 * credentials would re-enter the permission check and stack-overflow, and would
 * leak the secret to the log. This test forks a JVM with combiner debug enabled
 * and a SecurityManager installed (so the recursion path is live) and asserts
 * the run completes without {@code StackOverflowError}, never prints the private
 * credential, and still logs the principal.
 *
 * <p>Reflective + {@link Assume}-gated so it compiles on a stock JDK and
 * self-skips there (the forked child is the same JDK, so it only runs on a
 * DirtyChai JDK that has the multi-Subject {@code callAs}).
 */
public class CombinerDebugRecursionTest {

    private static boolean dirtyChai() {
        try {
            Class<?> us = Class.forName("javax.security.auth.UserSubject");
            Subject.class.getMethod("callAs", Callable.class, Array.newInstance(us, 0).getClass());
            return true;
        } catch (ClassNotFoundException | NoSuchMethodException notDirtyChai) {
            return false;
        }
    }

    @Test
    public void combinerDebugDoesNotRecurseOrLeakPrivateCredentials() throws Exception {
        Assume.assumeTrue("requires the DirtyChai multi-Subject API", dirtyChai());

        String javaBin = System.getProperty("java.home")
                + File.separator + "bin" + File.separator + "java";
        ProcessBuilder pb = new ProcessBuilder(
                javaBin,
                "-cp", System.getProperty("java.class.path"),
                "-Djava.security.manager=allow",
                "-Djava.security.debug=combiner",
                CombinerDebugRecursionHelper.class.getName());
        pb.redirectErrorStream(true);

        Process proc = pb.start();
        String output = new String(proc.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        boolean finished = proc.waitFor(90, TimeUnit.SECONDS);
        if (!finished) {
            proc.destroyForcibly();
            fail("combiner-debug helper did not terminate within 90s (runaway recursion?)");
        }

        assertEquals("helper should exit cleanly\n--- child output ---\n" + output,
                0, proc.exitValue());
        assertTrue("callAs + checkPermission must complete under combiner debug\n--- child output ---\n" + output,
                output.contains("OK"));
        assertFalse("combiner debug must not recurse to StackOverflowError\n--- child output ---\n" + output,
                output.contains("StackOverflowError"));
        assertFalse("private credential MUST NOT be written to the debug log\n--- child output ---\n" + output,
                output.contains(CombinerDebugRecursionHelper.SECRET));
        assertTrue("combiner debug should still log the principal\n--- child output ---\n" + output,
                output.contains("CN=debuguser"));
    }
}
