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
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.After;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;

/**
 * Tests of {@link UdsServerEndpoint#restrictPosixOwnerOnly}: the owner-only
 * mode must be applied to a bound socket file (a no-follow {@code chmod} fails
 * on a socket with ENXIO, which once made every bind fail on Linux) and the
 * checks that replace the no-follow guarantee must fail closed.
 */
public class UdsRestrictPermissionsTest {

    private Path dir;
    private final List<ServerSocketChannel> channels = new ArrayList<>();

    @Before
    public void setUp() throws IOException {
	Assume.assumeTrue("needs a POSIX file system",
	    FileSystems.getDefault().supportedFileAttributeViews()
		.contains("posix"));
	dir = Files.createTempDirectory("udsjeri-restrict");
    }

    @After
    public void tearDown() throws IOException {
	for (ServerSocketChannel c : channels) {
	    try { c.close(); } catch (IOException ignore) { }
	}
	if (dir != null) {
	    try (Stream<Path> s = Files.list(dir)) {
		for (Path p : (Iterable<Path>) s::iterator) {
		    Files.deleteIfExists(p);
		}
	    }
	    Files.deleteIfExists(dir);
	}
    }

    private Path bind(String name) throws IOException {
	Path p = dir.resolve(name);
	ServerSocketChannel c = ServerSocketChannel.open(StandardProtocolFamily.UNIX);
	channels.add(c);
	c.bind(UnixDomainSocketAddress.of(p));
	return p;
    }

    private static Set<java.nio.file.attribute.PosixFilePermission> mode(Path p)
	throws IOException
    {
	return Files.readAttributes(p,
	    java.nio.file.attribute.PosixFileAttributes.class,
	    LinkOption.NOFOLLOW_LINKS).permissions();
    }

    private static void set(Path p, String mode) throws IOException {
	Files.setPosixFilePermissions(p, PosixFilePermissions.fromString(mode));
    }

    private static final String OWNER_ONLY = "rwx------";

    @Test
    public void boundSocketBecomesOwnerOnly() throws Exception {
	Path sock = bind("s.sock");
	set(sock, "rwxrwxrwx");
	UdsServerEndpoint.restrictPosixOwnerOnly(sock, null);
	Assert.assertEquals(PosixFilePermissions.fromString(OWNER_ONLY), mode(sock));
	Assert.assertTrue(Files.readAttributes(sock,
	    java.nio.file.attribute.BasicFileAttributes.class,
	    LinkOption.NOFOLLOW_LINKS).isOther());
    }

    @Test
    public void regularFileIsRefusedAndKeepsItsMode() throws Exception {
	Path f = dir.resolve("regular");
	Files.createFile(f);
	set(f, "rw-rw-rw-");
	expectRefused(f, null);
	Assert.assertEquals(PosixFilePermissions.fromString("rw-rw-rw-"), mode(f));
    }

    @Test
    public void symlinkToSocketIsRefusedAndTargetKeepsItsMode() throws Exception {
	Path target = bind("target.sock");
	set(target, "rwxrwxrwx");
	Path link = dir.resolve("link.sock");
	Files.createSymbolicLink(link, target);
	expectRefused(link, null);
	Assert.assertEquals(PosixFilePermissions.fromString("rwxrwxrwx"), mode(target));
	Files.delete(link);
    }

    @Test
    public void symlinkToRegularFileIsRefusedAndTargetKeepsItsMode() throws Exception {
	Path target = dir.resolve("victim");
	Files.createFile(target);
	set(target, "rw-rw-rw-");
	Path link = dir.resolve("link");
	Files.createSymbolicLink(link, target);
	expectRefused(link, null);
	Assert.assertEquals(PosixFilePermissions.fromString("rw-rw-rw-"), mode(target));
	Files.delete(link);
	Files.delete(target);
    }

    @Test
    public void missingPathIsRefused() throws Exception {
	expectRefused(dir.resolve("absent"), null);
    }

    @Test
    public void inodeSwappedBetweenTheChecksIsRefused() throws Exception {
	Path sock = bind("s.sock");
	Path other = bind("other.sock");
	set(sock, "rwxrwxrwx");
	// Another socket, same type, takes the name after the first check: only
	// the identity (fileKey) comparison can notice.
	IOException e = expectRefused(sock, () -> {
	    try {
		Files.move(other, sock, StandardCopyOption.REPLACE_EXISTING);
	    } catch (IOException x) {
		throw new AssertionError(x);
	    }
	});
	Assert.assertTrue(String.valueOf(e.getCause()),
	    e.getCause().getMessage().contains("was replaced"));
    }

    @Test
    public void symlinkSwappedInBetweenTheChecksIsRefused() throws Exception {
	Path sock = bind("s.sock");
	Path decoy = dir.resolve("decoy");
	Files.createFile(decoy);
	set(decoy, "rw-rw-rw-");
	expectRefused(sock, () -> {
	    try {
		Files.delete(sock);
		Files.createSymbolicLink(sock, decoy);
	    } catch (IOException x) {
		throw new AssertionError(x);
	    }
	});
	Files.delete(sock);
    }

    private static IOException expectRefused(Path p, Runnable between) {
	try {
	    UdsServerEndpoint.restrictPosixOwnerOnly(p, between);
	} catch (IOException e) {
	    Assert.assertTrue(e.getMessage(),
		e.getMessage().startsWith("could not restrict socket file"));
	    return e;
	}
	throw new AssertionError("not refused: " + p);
    }
}
