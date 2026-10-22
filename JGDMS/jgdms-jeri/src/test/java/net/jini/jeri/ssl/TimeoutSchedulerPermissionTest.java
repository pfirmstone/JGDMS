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

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.apache.river.jeri.internal.TimeoutScheduler;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Test;

/**
 * What the connection timeout scheduler needs of a security manager: its own
 * protection domain, jgdms-jeri's, must hold the permissions that its Javadoc
 * lists, and the domain of the code that starts it need hold none.  The
 * surefire profile that installs a security manager grants every permission to
 * every domain, so the scenario runs in a JVM of its own with a security
 * manager that checks only the thread under test ({@link
 * TimeoutSchedulerProbe}).
 */
public class TimeoutSchedulerPermissionTest {

    /**
     * The permissions that the domain of {@code TimeoutScheduler} needs, as
     * the Javadoc of the class lists them; the generated policy of a
     * deployment must grant them to the code base of jgdms-jeri.
     */
    private static final String[] REQUIRED = {
        "java.lang.RuntimePermission|getStackWalkerWithClassReference|",
        "javax.security.auth.AuthPermission|callAs|",
        "java.lang.RuntimePermission|createPlatformThread|",
        "java.lang.RuntimePermission|setContextClassLoader|",
    };

    private static final class Outcome {
        final String result;
        final String output;

        Outcome(String result, String output) {
            this.result = result;
            this.output = output;
        }
    }

    private static Path locationOf(Class<?> c) throws Exception {
        return Paths.get(c.getProtectionDomain().getCodeSource().getLocation().toURI());
    }

    /** Runs the scenario in a JVM of its own, with the permissions granted to jgdms-jeri's domain. */
    private static Outcome run(String... granted) throws Exception {
        List<String> command = new ArrayList<>();
        command.add(Paths.get(System.getProperty("java.home"), "bin",
            System.getProperty("os.name").startsWith("Windows") ? "java.exe" : "java")
            .toString());
        command.add("-cp");
        command.add(locationOf(TimeoutScheduler.class) + File.pathSeparator
                    + locationOf(TimeoutSchedulerProbe.class));
        command.add(TimeoutSchedulerProbe.class.getName());
        command.addAll(Arrays.asList(granted));
        Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
        StringBuilder output = new StringBuilder();
        String result = null;
        try (BufferedReader in = new BufferedReader(
                 new InputStreamReader(process.getInputStream())))
        {
            for (String line; (line = in.readLine()) != null; ) {
                output.append(line).append('\n');
                if (line.startsWith("OK") || line.startsWith("FAILED")
                    || line.startsWith("UNSUPPORTED"))
                {
                    result = line;
                }
            }
        }
        if (!process.waitFor(60, TimeUnit.SECONDS)) {
            process.destroyForcibly();
            Assert.fail("the scenario did not end: " + output);
        }
        Assert.assertNotNull("no result: " + output, result);
        Assume.assumeFalse("no security manager can be installed here: " + result,
                           result.startsWith("UNSUPPORTED"));
        return new Outcome(result, output.toString());
    }

    /**
     * With only the listed permissions in its own domain, and none in the
     * domain of the code that touches it first, the scheduler initialises and
     * its timer and its hand-off run: the lookup of the caller's class is
     * privileged, so the application's code is not asked for it.
     */
    @Test(timeout = 120000)
    public void testNeedsOnlyThePermissionsOfItsOwnDomain() throws Exception {
        Outcome outcome = run(REQUIRED);
        Assert.assertEquals(outcome.output, "OK", outcome.result);
    }

    /** Each listed permission is needed: without it the scheduler fails to start. */
    @Test(timeout = 240000)
    public void testEveryListedPermissionIsNeeded() throws Exception {
        for (int i = 0; i < REQUIRED.length; i++) {
            List<String> fewer = new ArrayList<>(Arrays.asList(REQUIRED));
            String missing = fewer.remove(i);
            Outcome outcome = run(fewer.toArray(new String[0]));
            String name = missing.split("\\|")[1];
            Assert.assertTrue("without " + missing + ": " + outcome.output,
                outcome.result.startsWith("FAILED")
                && outcome.result.contains("access denied")
                && outcome.result.contains(name));
        }
    }
}
