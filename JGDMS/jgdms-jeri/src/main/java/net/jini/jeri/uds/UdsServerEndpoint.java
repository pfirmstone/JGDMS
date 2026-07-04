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

import java.io.FilePermission;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.BindException;
import java.net.StandardProtocolFamily;
import java.net.UnixDomainSocketAddress;
import java.nio.channels.Channels;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.AclEntry;
import java.nio.file.attribute.AclEntryPermission;
import java.nio.file.attribute.AclEntryType;
import java.nio.file.attribute.AclFileAttributeView;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.UserPrincipal;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.security.PrivilegedExceptionAction;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.jini.core.constraint.InvocationConstraints;
import net.jini.io.UnsupportedConstraintException;
import net.jini.jeri.Endpoint;
import net.jini.jeri.InboundRequest;
import net.jini.jeri.RequestDispatcher;
import net.jini.jeri.ServerEndpoint;
import net.jini.jeri.connection.InboundRequestHandle;
import net.jini.jeri.connection.ServerConnection;
import net.jini.jeri.connection.ServerConnectionManager;
import net.jini.security.Security;
import net.jini.security.SecurityContext;
import org.apache.river.logging.LogUtil;
import org.apache.river.thread.Executor;
import org.apache.river.thread.GetThreadPoolAction;

/**
 * A plaintext implementation of the {@link ServerEndpoint} abstraction that
 * listens on a <em>Unix domain socket</em> (a {@link ServerSocketChannel}
 * opened with {@link StandardProtocolFamily#UNIX}) for host-local IPC.
 *
 * <p>This class is the UDS analogue of {@code net.jini.jeri.tcp.TcpServerEndpoint}.
 * Where {@code TcpServerEndpoint} binds a TCP port and produces
 * {@code TcpEndpoint} instances carrying a host/port, {@code UdsServerEndpoint}
 * binds a filesystem <em>socket path</em> and produces {@link UdsEndpoint}
 * instances carrying that path.  The Jini ERI multiplexing protocol,
 * invocation layer and {@code @AtomicSerial} marshalling are reused unchanged.
 *
 * <p><b>No TLS</b> (option (iii)): the transport is plaintext over the kernel's
 * local socket.  See {@link UdsEndpoint} for the confidentiality/integrity
 * (kernel-provided) and identity (SPIFFE-SVID-at-connect, deferred to a later
 * increment) rationale.
 *
 * <h2>Socket-file lifecycle and access gate</h2>
 * In increment 1 (no {@code SO_PEERCRED}/SVID peer authentication yet) the
 * socket file's filesystem permissions are the <em>entire</em> peer-access
 * gate, so {@code listen} hardens them fail-closed:
 * <ul>
 * <li><b>Bind is attempted first, with no pre-delete.</b>  The OS already fails
 *     a bind over a <em>live</em> socket with {@link BindException}; the old
 *     unconditional {@code deleteIfExists}-before-bind converted that safe
 *     failure into a silent takeover of a running server.  On {@code
 *     BindException} the incumbent is probed by a short-timeout connect: a
 *     successful connect means a live server owns the path and {@code listen}
 *     fails (it does not steal it); a refused connect (stale leftover) that is
 *     also owned by the current uid is unlinked and bind is retried once.
 * <li><b>The owner-only mode is applied fail-closed.</b>  On POSIX the file is
 *     set to {@code rwx------}; a failed {@code chmod} fails the listen.  On a
 *     non-POSIX platform with an {@link AclFileAttributeView} (Windows/NTFS) an
 *     owner-only DACL is applied and verified.  If neither an owner-only POSIX
 *     mode nor an owner-only ACL can be applied, {@code listen} fails &mdash;
 *     unless the caller explicitly opted into an unprotected socket
 *     ({@code allowUnprotectedSocket=true}, default {@code false}).
 * <li><b>The containing directory is validated</b> (a world-writable,
 *     non-sticky parent is rejected on POSIX) so the owner-only file cannot be
 *     renamed/symlink-swapped out from under the server, and to close the
 *     bind&rarr;chmod TOCTOU window.
 * <li><b>Unlink-on-close is identity-checked</b>: the file is unlinked on
 *     {@code close}/unexport only if it is still the same inode this server
 *     bound (matched by {@link BasicFileAttributes#fileKey}), never a path that
 *     was replaced.
 * </ul>
 **/
public final class UdsServerEndpoint implements ServerEndpoint {

    /**
     * pool of threads for executing tasks in system thread group:
     * used for UDS accept threads
     **/
    private static final Executor systemThreadPool =
	(Executor) AccessController.doPrivileged(
	    new GetThreadPoolAction(false));

    private static final ServerConnectionManager serverConnectionManager =
	new ServerConnectionManager();

    /** server transport logger */
    private static final Logger logger =
	Logger.getLogger("net.jini.jeri.uds.server");

    /**
     * the filesystem path of the Unix domain socket that this
     * UdsServerEndpoint listens on, and that produced UdsEndpoints connect to
     **/
    private final String path;

    /**
     * If {@code true}, the caller has explicitly opted into binding a control
     * socket whose owner-only access gate could not be applied (neither an
     * owner-only POSIX mode nor an owner-only ACL).  Default {@code false}:
     * a bare unprotected control socket must never be the default (F1).
     **/
    private final boolean allowUnprotectedSocket;

    /**
     * Returns a <code>UdsServerEndpoint</code> instance that listens on the
     * given Unix domain socket path with the owner-only access gate enforced
     * fail-closed.
     *
     * @param path the filesystem path of the socket to bind to
     *
     * @return a <code>UdsServerEndpoint</code> instance
     *
     * @throws NullPointerException if <code>path</code> is <code>null</code>
     * @throws IllegalArgumentException if <code>path</code> is empty, contains
     * an embedded NUL, denotes the Linux abstract namespace (leading
     * {@code '@'}), or exceeds the AF_UNIX {@code sun_path} byte ceiling
     **/
    public static UdsServerEndpoint getInstance(String path) {
	return new UdsServerEndpoint(path, false);
    }

    /**
     * Returns a <code>UdsServerEndpoint</code> instance that listens on the
     * given Unix domain socket path, optionally permitting an
     * <em>unprotected</em> socket if the owner-only access gate cannot be
     * applied on this platform.
     *
     * <p><b>Security:</b> in increment 1 the socket file's permissions are the
     * only peer-access gate.  Passing {@code allowUnprotectedSocket=true} binds
     * a control socket that may be reachable by other local users when neither
     * an owner-only POSIX mode nor an owner-only ACL can be applied; use it only
     * when the socket lives in an already-private directory or peer access is
     * otherwise constrained.  It defaults to {@code false} (fail-closed).
     *
     * @param path the filesystem path of the socket to bind to
     * @param allowUnprotectedSocket if {@code true}, permit binding even when
     * no owner-only access gate could be applied to the socket file
     *
     * @return a <code>UdsServerEndpoint</code> instance
     *
     * @throws NullPointerException if <code>path</code> is <code>null</code>
     * @throws IllegalArgumentException if <code>path</code> is empty, contains
     * an embedded NUL, denotes the Linux abstract namespace (leading
     * {@code '@'}), or exceeds the AF_UNIX {@code sun_path} byte ceiling
     **/
    public static UdsServerEndpoint getInstance(String path,
						boolean allowUnprotectedSocket)
    {
	return new UdsServerEndpoint(path, allowUnprotectedSocket);
    }

    private UdsServerEndpoint(String path, boolean allowUnprotectedSocket) {
	// F6/LOW-1: fail-closed path validation via the single shared validator
	// (rejects null/empty, embedded NUL, Linux abstract-namespace paths, and
	// paths over the sun_path byte ceiling), shared with UdsEndpoint so the
	// client and server checks cannot drift.
	this.path = UdsPaths.validate(path);
	this.allowUnprotectedSocket = allowUnprotectedSocket;
    }

    /*
     * Stateless filesystem-identity helpers shared by the LE (listen/bind) and
     * LH (close/unlink) paths.  Declared private static on the outer class so
     * both the inner LE and the static nested LH can call them (F3).
     */

    /**
     * Returns {@code true} if the file at {@code socketPath} is owned by the
     * current process's user, so it is safe for us to remove it.  On POSIX the
     * owner is read via {@link PosixFileAttributeView} (following no links); on a
     * non-POSIX platform (e.g. Windows/NTFS) via {@link Files#getOwner}.  If
     * ownership cannot be determined or does not match, returns {@code false}
     * (fail closed -- do not remove a file we cannot prove is ours).
     **/
    private static boolean staleFileIsOurs(Path socketPath) {
	try {
	    UserPrincipal fileOwner;
	    PosixFileAttributeView posix = Files.getFileAttributeView(
		socketPath, PosixFileAttributeView.class,
		LinkOption.NOFOLLOW_LINKS);
	    if (posix != null) {
		fileOwner = posix.readAttributes().owner();
	    } else {
		// Non-POSIX (e.g. Windows): use the ACL/owner attribute.
		fileOwner = Files.getOwner(socketPath, LinkOption.NOFOLLOW_LINKS);
	    }
	    UserPrincipal me = socketPath.getFileSystem()
		.getUserPrincipalLookupService()
		.lookupPrincipalByName(System.getProperty("user.name"));
	    return fileOwner != null && fileOwner.equals(me);
	} catch (IOException | RuntimeException e) {
	    return false;
	}
    }

    /**
     * Reads a stable identity token (the filesystem {@code fileKey}) for the
     * socket inode at {@code socketPath}, or {@code null} if the platform does
     * not expose one (e.g. Windows UDS).  Used to bind-vs-close identity match
     * and to narrow the reclaim TOCTOU (F3).
     **/
    private static Object readFileKey(Path socketPath) {
	try {
	    BasicFileAttributes attrs = Files.readAttributes(
		socketPath, BasicFileAttributes.class,
		LinkOption.NOFOLLOW_LINKS);
	    return attrs.fileKey(); // may be null on some platforms
	} catch (IOException e) {
	    return null;
	}
    }

    /**
     * Returns whether the given attributes describe a Unix-domain socket special
     * file (neither a regular file, directory, nor symlink).  Part of the
     * no-fileKey close-time identity check (F3-residual); on Windows a UDS socket
     * reports {@code isOther()==true}.
     **/
    private static boolean isSocket(BasicFileAttributes attrs) {
	return attrs != null
	    && !attrs.isRegularFile()
	    && !attrs.isDirectory()
	    && !attrs.isSymbolicLink();
    }

    /**
     * TEST-ONLY fault-injection seam for the HIGH-1 regression test.
     *
     * <p>This is a package-private static hook that is <em>inert by default</em>
     * ({@code null}) and has <b>zero production behaviour</b>: it is only
     * consulted at the END of {@link LE#restrictPermissions}, <em>after</em> the
     * real owner-only POSIX mode or ACL has actually been applied and verified.
     * When a test in this package installs an injector, it converts a
     * fully-successful restriction into a thrown {@link IOException}, letting the
     * test drive the "bind succeeded, then a post-gate failure" path so the
     * {@code boundHere} socket-file cleanup in {@link LE#listen} is exercised.
     *
     * <p><b>Security invariants of this seam:</b>
     * <ul>
     * <li>It is package-private, so it is NOT reachable or settable from outside
     *     {@code net.jini.jeri.uds}; no external code can install a fault.
     * <li>It only ever <em>adds</em> a failure after the real gate ran; it can
     *     never make {@code restrictPermissions} <em>succeed</em> where it would
     *     otherwise fail, so it creates no fail-open / F1-bypass path.
     * <li>Default {@code null} means the production code path is byte-for-byte the
     *     same as without the seam (the {@code if (injector != null)} check is the
     *     only added instruction, and it is never true in production).
     * </ul>
     * The installing test must reset it to {@code null} in a {@code finally} so
     * it cannot leak between tests.
     **/
    static volatile java.util.function.Consumer<Path> restrictPermissionsFaultInjector;

    /**
     * Invokes the test-only fault injector, if one is installed, AFTER the real
     * owner-only gate has been applied.  A no-op in production (injector null).
     * Any unchecked exception the injector raises is surfaced as an {@link
     * IOException} so it flows through {@code listen}'s normal failure path.
     **/
    private static void firePostRestrictFaultIfInjected(Path socketPath)
	throws IOException
    {
	java.util.function.Consumer<Path> injector = restrictPermissionsFaultInjector;
	if (injector != null) {
	    try {
		injector.accept(socketPath);
	    } catch (RuntimeException e) {
		throw new IOException(
		    "test-only restrictPermissions fault after owner-only gate "
			+ "applied to " + socketPath, e);
	    }
	}
    }

    /**
     * Returns the Unix domain socket path that this
     * <code>UdsServerEndpoint</code> listens on.
     *
     * @return the socket path that this endpoint listens on
     **/
    public String getPath() {
	return path;
    }

    /**
     * {@inheritDoc}
     *
     * @throws NullPointerException {@inheritDoc}
     **/
    public InvocationConstraints checkConstraints(
	InvocationConstraints constraints)
	throws UnsupportedConstraintException
    {
	return Constraints.check(constraints, true);
    }

    /**
     * Passes this endpoint's {@link ListenEndpoint} to
     * <code>listenContext</code> and returns a {@link UdsEndpoint} carrying
     * this endpoint's socket path.
     *
     * @throws NullPointerException {@inheritDoc}
     **/
    public Endpoint enumerateListenEndpoints(ListenContext listenContext)
	throws IOException
    {
	if (listenContext == null) {
	    throw new NullPointerException();
	}

	LE listenEndpoint = new LE();
	ListenCookie listenCookie =
	    listenContext.addListenEndpoint(listenEndpoint);

	if (!(listenCookie instanceof LE.Cookie)) {
	    throw new IllegalArgumentException();
	}
	LE.Cookie cookie = (LE.Cookie) listenCookie;
	if (!listenEndpoint.equals(cookie.getLE())) {
	    throw new IllegalArgumentException();
	}

	return UdsEndpoint.getInstance(path);
    }

    public int hashCode() {
	return path.hashCode();
    }

    public boolean equals(Object obj) {
	if (obj == this) {
	    return true;
	} else if (!(obj instanceof UdsServerEndpoint)) {
	    return false;
	}
	UdsServerEndpoint other = (UdsServerEndpoint) obj;
	return path.equals(other.path);
    }

    public String toString() {
	return "UdsServerEndpoint[" + path + "]";
    }

    /**
     * ListenEndpoint implementation.
     **/
    private class LE implements ListenEndpoint {

	LE() { }

	/**
	 * PROVISIONAL permission model (Phase 3 item 3): a UDS listen operation
	 * creates/writes a socket file, so listen authority is guarded by a
	 * {@code FilePermission("<path>", "write,delete")}.  TCP uses
	 * {@code SecurityManager.checkListen(port)}; there is no port here.
	 * Flagged for Peter -- coded as the reasonable default.
	 */
	public void checkPermissions() {
	    SecurityManager sm = System.getSecurityManager();
	    if (sm != null) {
		sm.checkPermission(new FilePermission(path, "write,delete"));
	    }
	}

	public ListenHandle listen(RequestDispatcher requestDispatcher)
	    throws IOException
	{
	    if (requestDispatcher == null) {
		throw new NullPointerException();
	    }

	    checkPermissions();

	    Path socketPath = Paths.get(path);

	    // F5/F4: validate the containing directory BEFORE bind, so a
	    // world-writable-without-sticky parent (in which the socket file
	    // could be renamed or symlink-swapped) is rejected up front, and so
	    // the private parent covers the bind->chmod window (F4).
	    validateParentDirectory(socketPath);

	    ServerSocketChannel serverChannel =
		ServerSocketChannel.open(StandardProtocolFamily.UNIX);
	    boolean ok = false;
	    boolean boundHere = false;
	    Object fileKey = null;
	    try {
		// F3: bind FIRST, with no pre-delete.  The OS fails a bind over a
		// LIVE socket with BindException; the old unconditional
		// deleteIfExists converted that safe failure into a silent
		// takeover of a running server.
		bindReclaimingStaleOnly(serverChannel, socketPath);
		// From here on the socket file at socketPath was created by US;
		// if listen fails past this point we must remove it (HIGH-1).
		boundHere = true;

		// F1: apply the owner-only access gate fail-closed (throws if it
		// cannot be applied and the caller did not opt into an
		// unprotected socket).
		restrictPermissions(socketPath);

		// F3: capture an identity token for the inode we bound, so
		// close-time unlink only removes OUR socket, never a path that
		// was replaced.
		fileKey = readFileKey(socketPath);
		ok = true;
	    } finally {
		if (!ok) {
		    try {
			serverChannel.close();
		    } catch (IOException e) {
		    }
		    /*
		     * HIGH-1: closing a UDS ServerSocketChannel does NOT unlink
		     * the pathname socket on Linux, so a failed restrictPermissions
		     * (a throwing chmod/ACL) would otherwise leave exactly the
		     * unprotected control socket F1 exists to prevent, at the
		     * target path.  Remove the file we created -- but ONLY when we
		     * bound it here, so we never delete a live incumbent's file we
		     * did not create.
		     */
		    if (boundHere) {
			try {
			    Files.deleteIfExists(socketPath);
			} catch (IOException e) {
			    if (logger.isLoggable(Level.FINE)) {
				logger.log(Level.FINE,
				    "could not remove socket file {0} after a "
					+ "failed listen: {1}",
				    new Object[] { socketPath, e });
			    }
			}
		    }
		}
	    }

	    if (logger.isLoggable(Level.FINE)) {
		logger.log(Level.FINE, "created uds server channel {0}",
			   serverChannel);
	    }

	    Cookie cookie = new Cookie(path);
	    final LH listenHandle = new LH(requestDispatcher, serverChannel,
					   socketPath, fileKey,
					   Security.getContext(), cookie);
	    listenHandle.startAccepting();
	    return listenHandle;
	}

	/**
	 * F3: bind the server channel to {@code socketPath}, treating an
	 * "address already in use" failure safely.
	 *
	 * <p>Binds first with no pre-delete.  If bind throws {@link
	 * BindException} (the path already exists), the incumbent is probed by a
	 * short-timeout connect:
	 * <ul>
	 * <li>connect SUCCEEDS &rarr; a <em>live</em> server owns the path; a
	 *     clear {@link IOException} is propagated and the path is NOT stolen;
	 * <li>connect is REFUSED (a stale leftover socket file) AND (on POSIX)
	 *     the file is owned by the current uid &rarr; the leftover is unlinked
	 *     and bind is retried exactly once.
	 * </ul>
	 */
	private void bindReclaimingStaleOnly(ServerSocketChannel serverChannel,
					     Path socketPath)
	    throws IOException
	{
	    UnixDomainSocketAddress address = UnixDomainSocketAddress.of(path);
	    try {
		serverChannel.bind(address);
		return;
	    } catch (BindException firstBind) {
		// Path already in use -- distinguish a live incumbent from a
		// stale leftover file.  Snapshot the file's identity BEFORE the
		// probe so we can confirm it did not change between probe and
		// delete (F3-residual: narrow the probe->delete->rebind TOCTOU).
		Object keyBeforeProbe = readFileKey(socketPath);

		if (probeIncumbentIsLive(address)) {
		    IOException live = new IOException(
			"a live server already owns the socket path " + path
			    + "; refusing to take it over");
		    live.initCause(firstBind);
		    throw live;
		}
		// Refused connect => stale leftover.  Only reclaim it if it is
		// ours (POSIX/owner == current uid).  On a platform where
		// ownership cannot be determined we do NOT blindly delete
		// another principal's file: fail closed.
		if (!staleFileIsOurs(socketPath)) {
		    IOException notOurs = new IOException(
			"socket path " + path + " already exists and is not "
			    + "owned by this process; refusing to remove it");
		    notOurs.initCause(firstBind);
		    throw notOurs;
		}
		/*
		 * F3-residual: re-stat between probe and delete.  If a fileKey is
		 * available and it changed since the snapshot, the inode at the
		 * path is no longer the stale file we probed as refused -- a live
		 * server may have just bound it -- so refuse rather than delete a
		 * possibly-live socket.  (When no fileKey is exposed we cannot
		 * detect this; the owner-match above remains the guard.)
		 */
		Object keyBeforeDelete = readFileKey(socketPath);
		if (keyBeforeProbe != null && keyBeforeDelete != null
		    && !keyBeforeProbe.equals(keyBeforeDelete))
		{
		    IOException raced = new IOException(
			"socket path " + path + " changed identity between the "
			    + "liveness probe and reclaim; refusing to remove it");
		    raced.initCause(firstBind);
		    throw raced;
		}
		Files.deleteIfExists(socketPath);
		if (logger.isLoggable(Level.FINE)) {
		    logger.log(Level.FINE,
			"removed stale (refused-connect, owner-matched) socket "
			    + "file {0}; retrying bind", socketPath);
		}
		serverChannel.bind(address); // retry ONCE
	    }
	}

	/**
	 * Bound on the incumbent-liveness probe connect, in milliseconds.  A UDS
	 * connect is local and either completes or is refused promptly, so this
	 * is a safety bound against a pathological stall, not an expected wait.
	 **/
	private static final long PROBE_CONNECT_TIMEOUT_MS = 2000L;

	/**
	 * Probes whether a live server owns {@code address} by attempting a
	 * bounded, non-blocking client connect.  A successful connect (which we
	 * immediately close) means a live listener; a refused/failed connect, or
	 * a connect that does not complete within {@link
	 * #PROBE_CONNECT_TIMEOUT_MS}, means a stale leftover file with no
	 * listener.  The connect is non-blocking with a finite {@link Selector}
	 * wait so this method cannot block indefinitely (MED-1).
	 */
	private boolean probeIncumbentIsLive(UnixDomainSocketAddress address) {
	    SocketChannel probe = null;
	    Selector selector = null;
	    try {
		probe = SocketChannel.open(StandardProtocolFamily.UNIX);
		probe.configureBlocking(false);
		boolean connected = probe.connect(address);
		if (!connected) {
		    selector = Selector.open();
		    probe.register(selector, SelectionKey.OP_CONNECT);
		    long deadline =
			System.currentTimeMillis() + PROBE_CONNECT_TIMEOUT_MS;
		    while (!connected) {
			long remaining = deadline - System.currentTimeMillis();
			if (remaining <= 0L) {
			    return false; // timed out => treat as no live listener
			}
			if (selector.select(remaining) == 0) {
			    continue; // spurious wakeup / still waiting
			}
			selector.selectedKeys().clear();
			try {
			    connected = probe.finishConnect();
			} catch (IOException refused) {
			    return false; // refused => stale leftover
			}
		    }
		}
		return probe.isConnected();
	    } catch (IOException refused) {
		return false;
	    } finally {
		if (selector != null) {
		    try {
			selector.close();
		    } catch (IOException e) {
		    }
		}
		if (probe != null) {
		    try {
			probe.close();
		    } catch (IOException e) {
		    }
		}
	    }
	}

	/**
	 * F5/F4: validates the socket file's containing directory.  On POSIX a
	 * world-writable directory WITHOUT the sticky bit is rejected: in such a
	 * directory any local user can rename or replace the owner-only socket
	 * file, defeating the access gate.  The sticky bit (as on {@code /tmp})
	 * restores per-owner delete/rename protection and is accepted.  On a
	 * non-POSIX platform this is a best-effort check (the ACL gate on the
	 * file itself is the primary control there).
	 */
	private void validateParentDirectory(Path socketPath)
	    throws IOException
	{
	    Path parent = socketPath.toAbsolutePath().getParent();
	    if (parent == null) {
		return;
	    }
	    // LOW-2: NOFOLLOW so a symlinked parent is inspected as the link,
	    // not silently resolved to some other directory's permissions.
	    PosixFileAttributeView posix = Files.getFileAttributeView(
		parent, PosixFileAttributeView.class,
		LinkOption.NOFOLLOW_LINKS);
	    if (posix == null) {
		return; // non-POSIX: file-level ACL (F1) is the control here
	    }
	    Set<PosixFilePermission> perms = posix.readAttributes().permissions();
	    if (perms.contains(PosixFilePermission.OTHERS_WRITE)
		&& !hasStickyBit(parent))
	    {
		throw new IOException(
		    "refusing to bind a Unix domain socket in world-writable, "
			+ "non-sticky directory " + parent + ": the owner-only "
			+ "socket file could be renamed or replaced by another "
			+ "local user, defeating the access gate");
	    }
	}

	/**
	 * Returns whether {@code dir} has the POSIX sticky bit set.  There is no
	 * {@code PosixFilePermission} for the sticky bit, so it is read from the
	 * octal {@code unix:mode} attribute (bit {@code 01000}); if that
	 * attribute view is unavailable, conservatively reports {@code false}
	 * (no sticky bit) so a world-writable directory is rejected rather than
	 * silently accepted.
	 */
	private boolean hasStickyBit(Path dir) {
	    try {
		Object mode = Files.getAttribute(dir, "unix:mode",
		    LinkOption.NOFOLLOW_LINKS);
		if (mode instanceof Integer) {
		    return (((Integer) mode).intValue() & 01000) != 0;
		}
	    } catch (IOException | UnsupportedOperationException
		     | IllegalArgumentException e) {
		// "unix:*" view not supported; fall through to conservative false
	    }
	    return false;
	}

	/**
	 * F1: restricts the socket file to owner-only, <em>fail-closed</em>.
	 *
	 * <ol>
	 * <li>On POSIX, sets mode {@code rwx------}; a failed {@code chmod} is a
	 *     security failure, so it throws (does NOT bind a world-mode control
	 *     socket).
	 * <li>On a non-POSIX platform with an {@link AclFileAttributeView}
	 *     (Windows/NTFS), applies and verifies an owner-only DACL.
	 * <li>If neither can be applied, throws &mdash; unless the caller opted
	 *     into {@code allowUnprotectedSocket}.
	 * </ol>
	 */
	private void restrictPermissions(Path socketPath) throws IOException {
	    // LOW-2: NOFOLLOW so we operate on the socket inode itself, never a
	    // symlink target that may have been swapped in.
	    PosixFileAttributeView posix = Files.getFileAttributeView(
		socketPath, PosixFileAttributeView.class,
		LinkOption.NOFOLLOW_LINKS);
	    if (posix != null) {
		Set<PosixFilePermission> ownerOnly =
		    EnumSet.of(PosixFilePermission.OWNER_READ,
			       PosixFilePermission.OWNER_WRITE,
			       PosixFilePermission.OWNER_EXECUTE);
		try {
		    posix.setPermissions(ownerOnly);
		} catch (IOException e) {
		    // A failed chmod is a SECURITY failure, not a FINE log.
		    throw new IOException(
			"could not restrict socket file " + socketPath
			    + " to owner-only (rwx------); refusing to bind an "
			    + "unprotected control socket", e);
		}
		// The real owner-only mode is now applied; only then may the
		// test-only fault fire (see restrictPermissionsFaultInjector).
		firePostRestrictFaultIfInjected(socketPath);
		return;
	    }

	    // Non-POSIX (e.g. Windows/NTFS): try an owner-only ACL.
	    if (applyOwnerOnlyAcl(socketPath)) {
		// The real owner-only ACL is now applied; only then may the
		// test-only fault fire (see restrictPermissionsFaultInjector).
		firePostRestrictFaultIfInjected(socketPath);
		return;
	    }

	    // Neither an owner-only POSIX mode nor an owner-only ACL could be
	    // applied.  Fail closed unless the caller explicitly opted in.
	    if (allowUnprotectedSocket) {
		if (logger.isLoggable(Level.WARNING)) {
		    logger.log(Level.WARNING,
			"binding UNPROTECTED socket file {0}: no owner-only "
			    + "access gate could be applied and the caller "
			    + "opted in (allowUnprotectedSocket=true)",
			socketPath);
		}
		return;
	    }
	    throw new IOException(
		"could not apply an owner-only access gate to socket file "
		    + socketPath + " (no POSIX mode and no acceptable ACL); "
		    + "refusing to bind an unprotected control socket.  Pass "
		    + "allowUnprotectedSocket=true to override.");
	}

	/**
	 * F1: applies an owner-only DACL to {@code socketPath} via {@link
	 * AclFileAttributeView} (the Windows/NTFS path), then verifies it took.
	 * Returns {@code true} only if an owner-only ACL is in place; {@code
	 * false} if the view is unavailable or the applied ACL could not be
	 * verified owner-only.
	 */
	private boolean applyOwnerOnlyAcl(Path socketPath) {
	    // LOW-2: NOFOLLOW so the DACL is applied to the socket inode itself.
	    AclFileAttributeView aclView = Files.getFileAttributeView(
		socketPath, AclFileAttributeView.class,
		LinkOption.NOFOLLOW_LINKS);
	    if (aclView == null) {
		return false;
	    }
	    try {
		UserPrincipal owner =
		    Files.getOwner(socketPath, LinkOption.NOFOLLOW_LINKS);
		AclEntry ownerFull = AclEntry.newBuilder()
		    .setType(AclEntryType.ALLOW)
		    .setPrincipal(owner)
		    .setPermissions(EnumSet.allOf(AclEntryPermission.class))
		    .build();
		aclView.setAcl(List.of(ownerFull));

		// Verify: every ALLOW entry must name exactly the owner.
		List<AclEntry> applied = aclView.getAcl();
		for (AclEntry e : applied) {
		    if (e.type() == AclEntryType.ALLOW
			&& !owner.equals(e.principal()))
		    {
			if (logger.isLoggable(Level.FINE)) {
			    logger.log(Level.FINE,
				"owner-only ACL verification failed on {0}: "
				    + "ALLOW entry for non-owner {1}",
				new Object[] { socketPath, e.principal() });
			}
			return false;
		    }
		}
		return true;
	    } catch (IOException | RuntimeException e) {
		if (logger.isLoggable(Level.FINE)) {
		    logger.log(Level.FINE,
			"could not apply owner-only ACL to {0}: {1}",
			new Object[] { socketPath, e });
		}
		return false;
	    }
	}

	private String getPath() { return path; }

	public int hashCode() {
	    return path.hashCode();
	}

	public boolean equals(Object obj) {
	    if (obj == this) {
		return true;
	    } else if (!(obj instanceof LE)) {
		return false;
	    }
	    LE other = (LE) obj;
	    return path.equals(other.getPath());
	}

	public String toString() {
	    return "UdsServerEndpoint.LE[" + path + "]";
	}

	/**
	 * ListenCookie implementation: identifies a listen operation by the
	 * socket path the server channel is bound to.
	 **/
	private class Cookie implements ListenCookie {

	    private final String path;

	    Cookie(String path) { this.path = path; }

	    LE getLE() { return LE.this; }

	    public String toString() {
		return "UdsServerEndpoint.LE.Cookie[" + path + "]";
	    }
	}
    }

    /**
     * ListenHandle implementation: represents a listen operation.
     **/
    private static class LH implements ListenHandle {

	private final RequestDispatcher requestDispatcher;
	private final ServerSocketChannel serverChannel;
	private final Path socketPath;
	/**
	 * F3: the identity token (filesystem {@code fileKey}) of the socket
	 * inode this handle bound, captured at successful bind.  May be {@code
	 * null} where the platform exposes no fileKey.  Used at close time so we
	 * only unlink OUR socket, never a path that was replaced.
	 **/
	private final Object boundFileKey;
	private final SecurityContext securityContext;
	private final ListenCookie cookie;

	private long acceptFailureTime = 0L;	// local to accept thread
	private int acceptFailureCount;		// local to accept thread

	private final Object lock = new Object();
	private boolean closed = false;
	private final Set connections = new HashSet();

	LH(RequestDispatcher requestDispatcher,
	   ServerSocketChannel serverChannel,
	   Path socketPath,
	   Object boundFileKey,
	   SecurityContext securityContext,
	   ListenCookie cookie)
	{
	    this.requestDispatcher = requestDispatcher;
	    this.serverChannel = serverChannel;
	    this.socketPath = socketPath;
	    this.boundFileKey = boundFileKey;
	    this.securityContext = securityContext;
	    this.cookie = cookie;
	}

	/**
	 * Starts the accept loop.
	 **/
	void startAccepting() {
	    systemThreadPool.execute(new Runnable() {
		public void run() {
		    try {
			executeAcceptLoop();
		    } finally {
			/*
			 * The accept loop is only started once, so after no
			 * more channel accepts will occur, ensure that the
			 * server channel is closed and the socket file removed.
			 */
			closeServerChannel();
		    }
		}
	    }, toString() + " accept loop");
	}

	/**
	 * Executes the accept loop.
	 *
	 * The accept loop runs with the full privileges of this code;
	 * connect permissions are checked against the direct user of this
	 * endpoint later, when the ServerConnectionManager calls
	 * checkPermissions on the InboundRequest before passing it to the
	 * RequestDispatcher.
	 **/
	private void executeAcceptLoop() {
	    while (true) {
		SocketChannel channel = null;
		try {
		    channel = AccessController.doPrivileged(
			new PrivilegedExceptionAction<SocketChannel>() {
			    public SocketChannel run() throws IOException {
				return serverChannel.accept();
			    }
			});
		    if (logger.isLoggable(Level.FINE)) {
			logger.log(Level.FINE,
			    "accepted uds channel {0} from server channel {1}",
			    new Object[]{ channel, serverChannel });
		    }

		    final ServerConnection serverConnection =
			new ServerConnectionImpl(channel);

		    AccessController.doPrivileged(securityContext.wrap(
			new PrivilegedAction() {
			    public Object run() {
				serverConnectionManager.handleConnection(
				    serverConnection, requestDispatcher);
				return null;
			    }
			}), securityContext.getAccessControlContext());

		} catch (Throwable t) {
		    try {
			synchronized (lock) {
			    if (closed) {
				break;
			    }
			}

			try {
			    if (logger.isLoggable(Level.WARNING)) {
				LogUtil.logThrow(logger, Level.WARNING,
				    UdsServerEndpoint.class,
				    "executeAcceptLoop",
				    "accept loop for {0} throws",
				    new Object[] { serverChannel }, t);
			    }
			} catch (Throwable tt) {
			}
		    } finally {
			if (channel != null) {
			    try {
				channel.close();
			    } catch (IOException e) {
			    }
			}
		    }

		    boolean knownFailure =
			t instanceof Exception ||
			t instanceof OutOfMemoryError ||
			t instanceof NoClassDefFoundError;
		    if (knownFailure) {
			if (continueAfterAcceptFailure(t)) {
			    continue;
			} else {
			    return;
			}
		    }

		    throw (Error) t;
		}
	    }
	}

	/**
	 * Closes the server channel and unlinks the socket file &mdash; but only
	 * if the file at the path is STILL the inode this handle bound (F3).
	 *
	 * <p>The old code did an unconditional {@code deleteIfExists(socketPath)}
	 * by path, which would remove whatever now sits at that path even if it
	 * had been replaced by another server's socket.  Here we re-read the
	 * file's {@code fileKey} and delete only if it matches the token captured
	 * at bind; if the platform exposes no {@code fileKey} we fall back to
	 * deleting only while we still hold the (now closing) server channel that
	 * was bound to it, and never delete a path whose identity we cannot
	 * confirm is still ours.
	 **/
	private void closeServerChannel() {
	    try {
		serverChannel.close();
	    } catch (IOException e) {
	    }
	    unlinkIfStillOurs();
	    if (logger.isLoggable(Level.FINE)) {
		logger.log(Level.FINE, "closed uds server channel {0}",
			   serverChannel);
	    }
	}

	/**
	 * F3: unlink the socket file only if it is still OUR socket.
	 *
	 * <p>When both the bind-time and current {@code fileKey} are available
	 * (POSIX; Linux exposes an inode fileKey) they must match, else the file
	 * was replaced and we leave it in place.  When the platform exposes no
	 * {@code fileKey} (F3-residual: UDS sockets on Windows report a {@code
	 * null} fileKey), we do NOT fall through to an unconditional path-based
	 * delete -- that is the exact "delete a replaced socket" hazard.  Instead
	 * we require a weaker but non-trivial identity match before deleting: the
	 * file at the path must still be a Unix-domain socket (not a regular file
	 * or directory that replaced it) AND still be owned by this process.  If
	 * even that cannot be established the file is left in place (fail closed).
	 **/
	private void unlinkIfStillOurs() {
	    try {
		BasicFileAttributes attrs = Files.readAttributes(
		    socketPath, BasicFileAttributes.class,
		    LinkOption.NOFOLLOW_LINKS);
		Object nowKey = attrs.fileKey();
		if (boundFileKey != null && nowKey != null) {
		    if (boundFileKey.equals(nowKey)) {
			Files.deleteIfExists(socketPath);
		    } else {
			// A DIFFERENT inode now -- it was replaced.  Do not
			// delete another server's socket.
			if (logger.isLoggable(Level.FINE)) {
			    logger.log(Level.FINE,
				"not unlinking {0}: file identity changed since "
				    + "bind (was replaced); leaving it in place",
				socketPath);
			}
		    }
		    return;
		}
		// No fileKey available on this platform (e.g. Windows UDS): use
		// the weaker owner-match + is-socket identity check before delete.
		if (isSocket(attrs) && staleFileIsOurs(socketPath)) {
		    Files.deleteIfExists(socketPath);
		} else if (logger.isLoggable(Level.FINE)) {
		    logger.log(Level.FINE,
			"not unlinking {0}: no fileKey on this platform and the "
			    + "path is no longer a socket we own; leaving it in "
			    + "place", socketPath);
		}
	    } catch (IOException e) {
		if (logger.isLoggable(Level.FINE)) {
		    logger.log(Level.FINE,
			"could not unlink socket file {0}: {1}",
			new Object[] { socketPath, e });
		}
	    }
	}

	/**
	 * Stops this listen operation.
	 **/
	public void close() {
	    synchronized (lock) {
		if (closed) {
		    return;
		}
		closed = true;
	    }

	    closeServerChannel();

	    /*
	     * Iterating over connections without synchronization is safe at
	     * this point because no other thread will access it without
	     * verifying that closed is false in a synchronized block first.
	     */
	    for (Iterator i = connections.iterator(); i.hasNext();) {
		((ServerConnectionImpl) i.next()).close();
	    }
	}

	/**
	 * Returns a cookie to identify this listen operation.
	 **/
	public ListenCookie getCookie() {
	    return cookie;
	}

	public String toString() {
	    return "UdsServerEndpoint.LH[" + socketPath + "]";
	}

	/**
	 * Throttles the accept loop after accept throws an exception, and
	 * decides whether to continue at all.  Borrowed from TcpServerEndpoint.
	 **/
	private boolean continueAfterAcceptFailure(Throwable t) {
	    final int NFAIL = 10;
	    final int NMSEC = 5000;
	    long now = System.currentTimeMillis();
	    if (acceptFailureTime == 0L ||
		(now - acceptFailureTime) > NMSEC)
	    {
		acceptFailureTime = now;
		acceptFailureCount = 0;
	    } else {
		acceptFailureCount++;
		if (acceptFailureCount >= NFAIL) {
		    try {
			Thread.sleep(10000);
		    } catch (InterruptedException ignore) {
			Thread.currentThread().interrupt();
			return false;
		    }
		}
	    }
	    return true;
	}

	/**
	 * ServerConnection implementation over a Unix domain SocketChannel.
	 *
	 * Instances of this class should never get exposed to anything other
	 * than our ServerConnectionManager, which we trust to operate
	 * correctly, so we do not bother to validate request handles passed in.
	 **/
	private class ServerConnectionImpl implements ServerConnection {

	    private final SocketChannel channel;

	    ServerConnectionImpl(SocketChannel channel) {
		this.channel = channel;
		addToConnectionSet();
	    }

	    public InputStream getInputStream() throws IOException {
		return Channels.newInputStream(channel);
	    }

	    public OutputStream getOutputStream() throws IOException {
		return Channels.newOutputStream(channel);
	    }

	    public SocketChannel getChannel() {
		return channel;
	    }

	    /**
	     * Identity seam (workload SVID verification) -- see {@link
	     * UdsEndpoint} class javadoc.  This is the server-side
	     * connection-setup hook where the peer's SPIFFE workload SVID/JWT
	     * (written by the client's {@code writeRequestData}) would be read
	     * and cryptographically verified, establishing the worker
	     * {@code Subject}.  For this plaintext increment it is a no-op,
	     * exactly as it is for TCP.
	     */
	    public InboundRequestHandle processRequestData(InputStream in,
							   OutputStream out)
	    {
		return new InboundRequestHandle() { };
	    }

	    /**
	     * PROVISIONAL permission model (Phase 3 item 3): the server-side
	     * accept-authority check.  TCP calls
	     * {@code SecurityManager.checkAccept(host, port)}; a UDS peer has no
	     * host:port, so we guard with a {@code FilePermission("<path>",
	     * "read")} representing "may accept connections arriving on this
	     * socket file".  Flagged for Peter -- coded as the reasonable
	     * default.
	     */
	    public void checkPermissions(InboundRequestHandle handle) {
		SecurityManager sm = System.getSecurityManager();
		if (sm != null) {
		    sm.checkPermission(
			new FilePermission(socketPath.toString(), "read"));
		}
	    }

	    public InvocationConstraints checkConstraints(
		InboundRequestHandle handle,
		InvocationConstraints constraints)
		throws UnsupportedConstraintException
	    {
		/*
		 * The transport layer aspects of all constraints supported by
		 * this provider are always satisfied by all requests on the
		 * server side, so this method has the same static behavior as
		 * ServerCapabilities.checkConstraints.
		 */
		return Constraints.check(constraints, true);
	    }

	    /**
	     * Identity seam (Phase 3 item 2 -- RESOLVED per Peter): this is
	     * where the SPIFFE-verified worker {@code Subject} would be injected
	     * into the request context via {@code Util.populateContext(context,
	     * subject)}, so that {@code BasicInvocationDispatcher} reads it
	     * through {@code ClientSubject} and {@code RemoteContextCodec}
	     * stamps its principals onto the transmitted user ACC (the additive
	     * half of the two-gate model).  For this plaintext increment no SVID
	     * is verified, so no {@code ClientSubject} is populated -- the seam
	     * is left clean rather than populating an anonymous principal.
	     */
	    public void populateContext(InboundRequestHandle handle,
					Collection context)
	    {
		if (context == null) {
		    throw new NullPointerException();
		}
		// Next increment: Util.populateContext(context, verifiedWorkerSubject);
	    }

	    public void close() {
		try {
		    channel.close();	// this will bring mux down too
		} catch (IOException e) {
		}
		if (logger.isLoggable(Level.FINE)) {
		    logger.log(Level.FINE, "closed uds channel {0}", channel);
		}

		synchronized (lock) {
		    if (!closed) {	// must not mutate set after closed
			connections.remove(this);
		    }
		}
	    }

	    private void addToConnectionSet() {
		boolean needClose = false;
		synchronized (lock) {
		    if (closed) {
			needClose = true; // close after releasing lock
		    } else {
			connections.add(this);
		    }
		}
		if (needClose) {
		    close();
		}
	    }
	}
    }
}
