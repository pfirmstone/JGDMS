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
package net.jini.jeri;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import javax.security.auth.Subject;
import javax.security.auth.x500.X500Principal;
import net.jini.security.jwt.JwtRawToken;
import org.junit.Assert;
import org.junit.Test;

/**
 * Pins the Subject lock in {@link BasicInvocationHandler#writeUserSubjects}.
 *
 * <p>Invariant: a token refresh swaps a Subject's principals and its
 * {@link JwtRawToken} under the Subject's monitor, so the writer, which
 * snapshots both under the same monitor, never writes principals of one token
 * with the token of another.
 *
 * <p>The swap here stops half way, holding the monitor (the old values are
 * removed, the new ones not yet added), and the reader is started then.  With
 * the lock the reader must be BLOCKED on the monitor until the swap ends and
 * must then write the complete new pair.  Without the lock it runs at once and
 * writes the half state, which the test reports.  No timing is involved: the
 * test waits for the reader to reach BLOCKED (or to finish, which fails).
 */
public class WriteUserSubjectsLockTest {

    private static final X500Principal OLD_P = new X500Principal("CN=old-principal-marker");
    private static final X500Principal NEW_P = new X500Principal("CN=new-principal-marker");
    private static final String OLD_T = "old.token.marker";
    private static final String NEW_T = "new.token.marker";

    @Test
    public void testWriterWaitsForASwapInProgressAndWritesTheNewPair() throws Exception {
        final Subject subject = new Subject();
        subject.getPrincipals().add(OLD_P);
        subject.getPublicCredentials().add(new JwtRawToken(OLD_T));

        final CountDownLatch halfSwapped = new CountDownLatch(1);
        final CountDownLatch finishSwap = new CountDownLatch(1);
        final AtomicReference<Throwable> swapFailure = new AtomicReference<>();
        Thread swap = new Thread(() -> {
            try {
                synchronized (subject) {
                    subject.getPrincipals().remove(OLD_P);
                    subject.getPublicCredentials().removeIf(c -> c instanceof JwtRawToken);
                    halfSwapped.countDown();
                    if (!finishSwap.await(30, TimeUnit.SECONDS))
                        throw new AssertionError("swap was never released");
                    subject.getPrincipals().add(NEW_P);
                    subject.getPublicCredentials().add(new JwtRawToken(NEW_T));
                }
            } catch (Throwable t) {
                swapFailure.set(t);
            }
        }, "test-swap");
        swap.start();
        Assert.assertTrue("swap reached the half state",
                halfSwapped.await(30, TimeUnit.SECONDS));

        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        final AtomicReference<Throwable> readFailure = new AtomicReference<>();
        Thread reader = new Thread(() -> {
            try {
                BasicInvocationHandler.writeUserSubjects(out, new Subject[] {subject});
            } catch (Throwable t) {
                readFailure.set(t);
            }
        }, "test-reader");
        reader.start();

        try {
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(30);
            while (reader.getState() != Thread.State.BLOCKED
                    && reader.getState() != Thread.State.TERMINATED
                    && System.nanoTime() < deadline) {
                Thread.sleep(5);
            }
            if (reader.getState() == Thread.State.TERMINATED) {
                Assert.fail("the writer did not wait for the swap holding the Subject monitor;"
                        + " it wrote a half-swapped Subject: "
                        + new String(out.toByteArray(), StandardCharsets.ISO_8859_1)
                                .replaceAll("[^\\x20-\\x7e]", "."));
            }
            Assert.assertEquals("the writer is waiting for the monitor",
                    Thread.State.BLOCKED, reader.getState());
        } finally {
            finishSwap.countDown();
            swap.join(30000);
            reader.join(30000);
        }
        Assert.assertNull(swapFailure.get());
        Assert.assertNull(readFailure.get());
        String written = new String(out.toByteArray(), StandardCharsets.ISO_8859_1);
        Assert.assertTrue("new principal written", written.contains(NEW_P.getName()));
        Assert.assertTrue("new token written", written.contains(NEW_T));
        Assert.assertFalse("no old principal", written.contains(OLD_P.getName()));
        Assert.assertFalse("no old token", written.contains(OLD_T));
    }
}
