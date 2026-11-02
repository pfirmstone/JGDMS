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
package au.zeus.jgdms.security.jwt;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

/**
 * Invariant: a module thread is a daemon named with the JGDMS prefix whose context class
 * loader is the system class loader, whatever the creating thread's loader is.
 */
public class NewDaemonThreadTest {

    /** Runs {@code body} with a context class loader that is not the system loader. */
    private static void withForeignLoader(Runnable body) {
        ClassLoader foreign = new ClassLoader(null) { };
        assertNotSame(ClassLoader.getSystemClassLoader(), foreign);
        ClassLoader saved = Thread.currentThread().getContextClassLoader();
        Thread.currentThread().setContextClassLoader(foreign);
        try {
            body.run();
        } finally {
            Thread.currentThread().setContextClassLoader(saved);
        }
    }

    @Test
    public void unstartedThreadHasPrefixDaemonAndSystemLoader() {
        Thread[] t = new Thread[1];
        withForeignLoader(() -> t[0] = new NewDaemonThread(() -> { }, "y", false).run());
        assertEquals("(JSK) y", t[0].getName());
        assertTrue(t[0].isDaemon());
        assertSame(ClassLoader.getSystemClassLoader(), t[0].getContextClassLoader());
        assertEquals(Thread.State.NEW, t[0].getState());
    }

    @Test
    public void startedThreadRunsAndHasPrefixDaemonAndSystemLoader() throws Exception {
        CountDownLatch ran = new CountDownLatch(1);
        Thread[] t = new Thread[1];
        withForeignLoader(() -> t[0] = BoundedBody.startThread(ran::countDown, "x"));
        assertTrue(ran.await(10, TimeUnit.SECONDS));
        assertEquals("(JSK) x", t[0].getName());
        assertTrue(t[0].isDaemon());
        assertSame(ClassLoader.getSystemClassLoader(), t[0].getContextClassLoader());
    }
}
