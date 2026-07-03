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
import java.net.StandardProtocolFamily;
import java.net.UnixDomainSocketAddress;
import java.nio.channels.Channels;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.PosixFilePermission;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.security.PrivilegedExceptionAction;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Iterator;
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
 * <h2>Socket-file lifecycle</h2>
 * On {@code listen}, the listen socket is bound to its path after any stale
 * file at that path is removed; where the platform supports POSIX file
 * permissions the file is then restricted to owner-only ({@code rwx------}).
 * On {@code close}/unexport the socket file is unlinked.  Callers should place
 * the socket in a private, non-world-writable directory (the socket-file
 * permissions are defense-in-depth, per the SOW &sect;8).
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
     * Returns a <code>UdsServerEndpoint</code> instance that listens on the
     * given Unix domain socket path.
     *
     * @param path the filesystem path of the socket to bind to
     *
     * @return a <code>UdsServerEndpoint</code> instance
     *
     * @throws NullPointerException if <code>path</code> is <code>null</code>
     * @throws IllegalArgumentException if <code>path</code> is empty
     **/
    public static UdsServerEndpoint getInstance(String path) {
	return new UdsServerEndpoint(path);
    }

    private UdsServerEndpoint(String path) {
	if (path == null) {
	    throw new NullPointerException("null socket path");
	}
	if (path.isEmpty()) {
	    throw new IllegalArgumentException("empty socket path");
	}
	this.path = path;
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
	    ServerSocketChannel serverChannel =
		ServerSocketChannel.open(StandardProtocolFamily.UNIX);
	    boolean ok = false;
	    try {
		// Remove any stale socket file, then bind.  The caller is
		// responsible for placing this in a private directory so the
		// unlink+bind window cannot be hijacked (SOW section 8).
		Files.deleteIfExists(socketPath);
		serverChannel.bind(UnixDomainSocketAddress.of(path));
		restrictPermissions(socketPath);
		ok = true;
	    } finally {
		if (!ok) {
		    try {
			serverChannel.close();
		    } catch (IOException e) {
		    }
		}
	    }

	    if (logger.isLoggable(Level.FINE)) {
		logger.log(Level.FINE, "created uds server channel {0}",
			   serverChannel);
	    }

	    Cookie cookie = new Cookie(path);
	    final LH listenHandle = new LH(requestDispatcher, serverChannel,
					   socketPath, Security.getContext(),
					   cookie);
	    listenHandle.startAccepting();
	    return listenHandle;
	}

	/**
	 * Restricts the socket file to owner-only where the platform supports
	 * POSIX permissions; a best-effort no-op elsewhere (e.g. Windows).
	 */
	private void restrictPermissions(Path socketPath) {
	    try {
		Set<PosixFilePermission> ownerOnly =
		    EnumSet.of(PosixFilePermission.OWNER_READ,
			       PosixFilePermission.OWNER_WRITE,
			       PosixFilePermission.OWNER_EXECUTE);
		Files.setPosixFilePermissions(socketPath, ownerOnly);
	    } catch (UnsupportedOperationException | IOException e) {
		// POSIX perms unsupported (e.g. Windows) or the file view is
		// unavailable; socket-file perms are defense-in-depth, so this
		// is non-fatal.  Log at FINE.
		if (logger.isLoggable(Level.FINE)) {
		    logger.log(Level.FINE,
			"could not restrict permissions on {0}: {1}",
			new Object[] { socketPath, e });
		}
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
	   SecurityContext securityContext,
	   ListenCookie cookie)
	{
	    this.requestDispatcher = requestDispatcher;
	    this.serverChannel = serverChannel;
	    this.socketPath = socketPath;
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
	 * Closes the server channel and unlinks the socket file.
	 **/
	private void closeServerChannel() {
	    try {
		serverChannel.close();
	    } catch (IOException e) {
	    }
	    try {
		Files.deleteIfExists(socketPath);
	    } catch (IOException e) {
		if (logger.isLoggable(Level.FINE)) {
		    logger.log(Level.FINE,
			"could not unlink socket file {0}: {1}",
			new Object[] { socketPath, e });
		}
	    }
	    if (logger.isLoggable(Level.FINE)) {
		logger.log(Level.FINE, "closed uds server channel {0}",
			   serverChannel);
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
