/*
 * Copyright 2026 peter.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
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
import java.net.StandardProtocolFamily;
import java.net.UnixDomainSocketAddress;
import java.nio.channels.ServerSocketChannel;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFileAttributes;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;
import net.jini.export.Exporter;
import net.jini.jeri.AtomicILFactory;
import net.jini.jeri.BasicJeriExporter;
import org.junit.After;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;

/**
 * The listener binds in a private staging directory and links the socket into
 * place, so the public path never shows a mode wider than {@code rwx------}
 * (a bind creates the file as {@code 0777 & ~umask}; a connect in the gap
 * before a later chmod is queued and accepted after it).
 */
public class UdsStagedBindTest {

    private static final Set<PosixFilePermission> OWNER_ONLY =
	PosixFilePermissions.fromString("rwx------");

    private Path dir;

    @Before
    public void setUp() throws IOException {
	Assume.assumeTrue("needs a POSIX file system",
	    FileSystems.getDefault().supportedFileAttributeViews()
		.contains("posix"));
	dir = Files.createTempDirectory("udsjeri-staged");
    }

    @After
    public void tearDown() throws IOException {
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

    private static Exporter exporter(Path socket) {
	return new BasicJeriExporter(
	    UdsServerEndpoint.getInstance(socket.toString()),
	    new AtomicILFactory(null, null,
		UdsStagedBindTest.class.getClassLoader()),
	    false, true);
    }

    private static List<String> entries(Path d) throws IOException {
	List<String> names = new ArrayList<>();
	try (Stream<Path> s = Files.list(d)) {
	    s.forEach(p -> names.add(p.getFileName().toString()));
	}
	return names;
    }

    @Test
    public void successfulListenLeavesOnlyTheSocket() throws Exception {
	Path sock = dir.resolve("s.sock");
	Exporter e = exporter(sock);
	e.export(new UdsEndpointRoundTripTest.EchoImpl());
	try {
	    Assert.assertEquals(List.of("s.sock"), entries(dir));
	    Assert.assertEquals(OWNER_ONLY, Files.readAttributes(sock,
		PosixFileAttributes.class, LinkOption.NOFOLLOW_LINKS)
		.permissions());
	} finally {
	    e.unexport(true);
	}
	Assert.assertEquals(List.of(), entries(dir));
    }

    @Test
    public void refusedTakeoverLeavesNoStagingDirectory() throws Exception {
	Path sock = dir.resolve("s.sock");
	try (ServerSocketChannel incumbent =
		 ServerSocketChannel.open(StandardProtocolFamily.UNIX))
	{
	    incumbent.bind(UnixDomainSocketAddress.of(sock));
	    Exporter e = exporter(sock);
	    try {
		e.export(new UdsEndpointRoundTripTest.EchoImpl());
		Assert.fail("a live incumbent must not be taken over");
	    } catch (IOException expected) {
		// refused
	    } finally {
		try { e.unexport(true); } catch (Exception ignore) { }
	    }
	    Assert.assertEquals(List.of("s.sock"), entries(dir));
	}
    }

    @Test
    public void pathTooLongToStageIsBoundInPlace() throws Exception {
	// Target fits in sun_path but target + "/.uds-<hex>/s" does not.
	Path d = dir;
	while (d.resolve("s.sock").toString().length() < 95) {
	    int room = 95 - d.resolve("s.sock").toString().length() - 1;
	    d = d.resolve("x".repeat(Math.max(1, Math.min(room, 20))));
	    Files.createDirectory(d);
	}
	Path sock = d.resolve("s.sock");
	Assert.assertTrue(sock.toString(), sock.toString().length() <= 100);
	Exporter e = exporter(sock);
	e.export(new UdsEndpointRoundTripTest.EchoImpl());
	try {
	    Assert.assertEquals(List.of("s.sock"), entries(d));
	    Assert.assertEquals(OWNER_ONLY, Files.readAttributes(sock,
		PosixFileAttributes.class, LinkOption.NOFOLLOW_LINKS)
		.permissions());
	} finally {
	    e.unexport(true);
	}
    }

    @Test
    public void publicPathNeverShowsAWiderMode() throws Exception {
	final Path sock = dir.resolve("s.sock");
	final AtomicBoolean stop = new AtomicBoolean();
	final List<String> wide = new ArrayList<>();
	Thread watcher = new Thread(() -> {
	    while (!stop.get()) {
		try {
		    Set<PosixFilePermission> m = Files.readAttributes(sock,
			PosixFileAttributes.class, LinkOption.NOFOLLOW_LINKS)
			.permissions();
		    if (!OWNER_ONLY.equals(m)) {
			synchronized (wide) {
			    wide.add(PosixFilePermissions.toString(m));
			}
		    }
		} catch (IOException absent) {
		    // not there yet / already gone
		}
		try (Stream<Path> st = Files.list(dir)) {
		    for (Path p : (Iterable<Path>) st::iterator) {
			if (p.getFileName().toString().startsWith(".uds-")) {
			    Set<PosixFilePermission> m = Files.readAttributes(p,
				PosixFileAttributes.class,
				LinkOption.NOFOLLOW_LINKS).permissions();
			    if (!OWNER_ONLY.equals(m)) {
				synchronized (wide) {
				    wide.add("staging " + PosixFilePermissions.toString(m));
				}
			    }
			}
		    }
		} catch (IOException absent) {
		    // raced with removal
		}
	    }
	}, "mode-watcher");
	watcher.start();
	try {
	    for (int i = 0; i < 300; i++) {
		Exporter e = exporter(sock);
		e.export(new UdsEndpointRoundTripTest.EchoImpl());
		e.unexport(true);
	    }
	} finally {
	    stop.set(true);
	    watcher.join();
	}
	synchronized (wide) {
	    Assert.assertTrue("modes seen at the public path: " + wide,
		wide.isEmpty());
	}
    }

    @Test
    public void socketReplacedAfterLinkingIsRefusedAndLeftAlone() throws Exception {
	final Path sock = dir.resolve("s.sock");
	final Path other = dir.resolve("other.sock");
	try (ServerSocketChannel replacement =
		 ServerSocketChannel.open(StandardProtocolFamily.UNIX))
	{
	    replacement.bind(UnixDomainSocketAddress.of(other));
	    UdsServerEndpoint.restrictPermissionsFaultInjector = p -> {
		try {
		    Files.move(other, p,
			java.nio.file.StandardCopyOption.REPLACE_EXISTING);
		} catch (IOException x) {
		    throw new AssertionError(x);
		}
	    };
	    Exporter e = exporter(sock);
	    try {
		e.export(new UdsEndpointRoundTripTest.EchoImpl());
		Assert.fail("a socket path replaced while linking must be refused");
	    } catch (IOException expected) {
		// refused
	    } finally {
		UdsServerEndpoint.restrictPermissionsFaultInjector = null;
		try { e.unexport(true); } catch (Exception ignore) { }
	    }
	    Assert.assertTrue("the replacement is not ours to delete",
		Files.exists(sock, LinkOption.NOFOLLOW_LINKS));
	    Assert.assertEquals(List.of("s.sock"), entries(dir));
	}
    }

    private boolean bindsIn(String mode, boolean sticky) throws Exception {
	Path d = Files.createTempDirectory(dir, "parent");
	Files.setPosixFilePermissions(d, PosixFilePermissions.fromString(mode));
	if (sticky) {
	    int m = (Integer) Files.getAttribute(d, "unix:mode");
	    Files.setAttribute(d, "unix:mode", m | 01000);
	}
	Path sock = d.resolve("s.sock");
	Exporter e = exporter(sock);
	try {
	    e.export(new UdsEndpointRoundTripTest.EchoImpl());
	    return true;
	} catch (IOException refused) {
	    return false;
	} finally {
	    try { e.unexport(true); } catch (Exception ignore) { }
	}
    }

    @Test
    public void groupWritableNonStickyParentIsRefused() throws Exception {
	Assert.assertFalse(bindsIn("rwxrwx---", false));
	Assert.assertFalse(bindsIn("rwxr-xrwx", false));
    }

    @Test
    public void groupWritableStickyParentIsAccepted() throws Exception {
	Assert.assertTrue(bindsIn("rwxrwx---", true));
    }

    @Test
    public void privateAndReadOnlyForOthersParentsAreAccepted() throws Exception {
	Assert.assertTrue(bindsIn("rwx------", false));
	Assert.assertTrue(bindsIn("rwxr-xr-x", false));
    }
}
