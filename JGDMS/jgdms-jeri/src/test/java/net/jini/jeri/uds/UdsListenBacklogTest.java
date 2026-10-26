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
package net.jini.jeri.uds;

import java.io.IOException;
import java.net.UnixDomainSocketAddress;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;
import net.jini.jeri.ServerEndpoint.ListenContext;
import net.jini.jeri.ServerEndpoint.ListenCookie;
import net.jini.jeri.ServerEndpoint.ListenEndpoint;
import net.jini.jeri.ServerEndpoint.ListenHandle;
import org.apache.river.jeri.internal.ListenBacklogProbe;
import org.junit.After;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;

/**
 * The UDS server endpoint binds with {@code ListenBacklog}, not the JDK default
 * of 50.  The accept loop is held up after its first accepted connection, so
 * the kernel queue alone decides how many of a burst of connectors complete
 * (a non-blocking connect to a full Unix domain queue fails at once).
 */
public class UdsListenBacklogTest {

    private String saved;
    private Path dir;
    private ListenHandle handle;

    @Before
    public void setUp() throws IOException {
        Assume.assumeTrue("needs a POSIX file system",
            FileSystems.getDefault().supportedFileAttributeViews()
                .contains("posix"));
        saved = ListenBacklogProbe.set(null);
        dir = Files.createTempDirectory("udsjeri-backlog");
    }

    @After
    public void tearDown() throws IOException {
        if (handle != null) {
            handle.close();
        }
        ListenBacklogProbe.restore(saved);
        if (dir != null) {
            try (Stream<Path> s = Files.walk(dir)) {
                for (Path p : (Iterable<Path>) s.sorted(
                    java.util.Comparator.reverseOrder())::iterator)
                {
                    Files.deleteIfExists(p);
                }
            }
        }
    }

    /**
     * Runs a burst against a listener whose accept loop is held up on the
     * listen handle's lock after it accepts its first connection.
     */
    private int burstAgainstHeldListener(String backlog) throws Exception {
        return burstAgainstHeldListener(backlog, dir.resolve("s.sock"));
    }

    private int burstAgainstHeldListener(String backlog, final Path sock)
        throws Exception
    {
        ListenBacklogProbe.set(backlog);
        UdsServerEndpoint.getInstance(sock.toString()).enumerateListenEndpoints(
            new ListenContext() {
                public ListenCookie addListenEndpoint(ListenEndpoint e)
                    throws IOException
                {
                    handle = e.listen(new ListenBacklogProbe.NoRequests());
                    return handle.getCookie();
                }
            });
        Object held = ListenBacklogProbe.field(handle, "lock");
        synchronized (held) {
            return ListenBacklogProbe.burst(
                UnixDomainSocketAddress.of(sock), true);
        }
    }

    @Test
    public void defaultBacklogHoldsTheWholeBurst() throws Exception {
        Assume.assumeTrue("kernel caps the backlog below the burst",
            ListenBacklogProbe.kernelCap() > ListenBacklogProbe.CONNECTORS + 8);
        int completed = burstAgainstHeldListener(null);
        System.out.println("uds default backlog: " + completed + " of "
            + ListenBacklogProbe.CONNECTORS + " connects completed");
        Assert.assertEquals(ListenBacklogProbe.CONNECTORS, completed);
    }

    @Test
    public void smallBacklogLimitsTheBurst() throws Exception {
        int completed = burstAgainstHeldListener("4");
        System.out.println("uds backlog 4: " + completed + " of "
            + ListenBacklogProbe.CONNECTORS + " connects completed");
        // the queue holds backlog + 1, and one connection is already accepted
        Assert.assertTrue("completed " + completed, completed >= 1);
        Assert.assertTrue("completed " + completed, completed <= 20);
    }

    /**
     * A path too long to stage beside is bound in place, which is a second bind
     * site.
     */
    @Test
    public void smallBacklogAlsoAppliesWhenBoundInPlace() throws Exception {
        Path d = dir;
        while (d.resolve("s.sock").toString().length() < 95) {
            int room = 95 - d.resolve("s.sock").toString().length() - 1;
            d = d.resolve("x".repeat(Math.max(1, Math.min(room, 20))));
            Files.createDirectory(d);
        }
        int completed = burstAgainstHeldListener("4", d.resolve("s.sock"));
        System.out.println("uds backlog 4, bound in place: " + completed
            + " of " + ListenBacklogProbe.CONNECTORS + " connects completed");
        Assert.assertTrue("completed " + completed, completed >= 1);
        Assert.assertTrue("completed " + completed, completed <= 20);
    }
}
