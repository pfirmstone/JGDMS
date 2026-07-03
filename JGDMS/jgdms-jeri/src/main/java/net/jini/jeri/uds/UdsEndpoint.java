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
import java.io.InvalidObjectException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.io.Serializable;
import java.lang.ref.Reference;
import java.lang.ref.WeakReference;
import java.net.SocketTimeoutException;
import java.net.StandardProtocolFamily;
import java.net.UnixDomainSocketAddress;
import java.nio.channels.Channels;
import java.nio.channels.SocketChannel;
import java.util.Collection;
import java.util.Iterator;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.WeakHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.jini.core.constraint.InvocationConstraints;
import net.jini.io.UnsupportedConstraintException;
import net.jini.jeri.Endpoint;
import net.jini.jeri.OutboundRequest;
import net.jini.jeri.OutboundRequestIterator;
import net.jini.jeri.connection.Connection;
import net.jini.jeri.connection.ConnectionEndpoint;
import net.jini.jeri.connection.ConnectionManager;
import net.jini.jeri.connection.OutboundRequestHandle;
import net.jini.security.proxytrust.TrustEquivalence;
import org.apache.river.api.io.AtomicSerial;
import org.apache.river.api.io.AtomicSerial.GetArg;
import org.apache.river.api.io.AtomicSerial.PutArg;
import org.apache.river.api.io.AtomicSerial.SerialForm;
import org.apache.river.logging.Levels;
import org.apache.river.logging.LogUtil;

/**
 * A plaintext implementation of the {@link Endpoint} abstraction that uses a
 * <em>Unix domain socket</em> (a {@link SocketChannel} opened with {@link
 * StandardProtocolFamily#UNIX}) as the underlying, host-local IPC mechanism.
 *
 * <p>This class is the UDS analogue of {@code net.jini.jeri.tcp.TcpEndpoint}.
 * Where {@code TcpEndpoint} holds a {@code host}/{@code port} pair and creates
 * {@link java.net.Socket} objects, {@code UdsEndpoint} holds a single
 * filesystem <em>socket path</em> and creates {@code SocketChannel}s connected
 * to a {@link UnixDomainSocketAddress}.  Everything above the byte transport --
 * the Jini ERI multiplexing protocol via the {@link ConnectionManager}, the
 * invocation layer, {@code @AtomicSerial} marshalling -- is reused unchanged
 * (the {@link Connection} contract is stream-based with an optional {@code
 * SocketChannel}, and a UDS channel <em>is</em> a {@code SocketChannel}).
 *
 * <p><b>No TLS.</b>  This is option (iii): the transport is plaintext.  A Unix
 * domain socket is a local, kernel-mediated channel, so confidentiality and
 * integrity between the two connected processes are provided by the OS (the
 * bytes never touch a network wire) and coarse peer access control by the
 * socket file's filesystem permissions (owner / mode {@code 0600}).  There is
 * no {@code SSLSocket} and no {@code SSLEngine}.
 *
 * <h2>Identity seam (Phase&nbsp;3 item&nbsp;2 -- RESOLVED per Peter)</h2>
 * The JGDMS two-gate identity model (workload&nbsp;&ne;&nbsp;user) is carried at
 * the JERI layer, not by TLS:
 * <ul>
 * <li><b>Workload identity</b> = the peer process's SPIFFE workload SVID,
 *     presented and cryptographically verified at JERI connection setup.  The
 *     defined hooks are {@link Connection#writeRequestData writeRequestData}
 *     (client presents its SVID/JWT) and {@link
 *     net.jini.jeri.connection.ServerConnection#processRequestData
 *     ServerConnection.processRequestData} (server verifies it) -- these already
 *     exist in the connection contract and are transport-independent, so SVID
 *     verification needs no TLS.
 * <li><b>User identity</b> = JWT user subjects transmitted over this same UDS
 *     connection by the existing {@code net.jini.jeri.RemoteContextCodec}
 *     layer-2 ACC-transmission, which rides the invocation layer's injected
 *     {@code ObjectOutput}/{@code ObjectInput} (above the transport) and is
 *     therefore already transport-agnostic -- it works over UDS unchanged.
 * <li><b>Socket-file permissions</b> remain defense-in-depth, not the identity
 *     mechanism.
 * </ul>
 * This first increment does <em>not</em> present/verify an SVID (the
 * connection-setup hooks are no-ops here, as they are for plaintext TCP); it
 * deliberately leaves the seam clean rather than baking in an anonymous peer.
 * Wiring the SVID presentation/verification into {@code writeRequestData}/{@code
 * processRequestData}, and the verified worker {@code Subject} into the server
 * connection's {@code populateContext} (which {@code BasicInvocationDispatcher}
 * reads via {@code ClientSubject}), is the defined next increment.
 *
 * @see UdsServerEndpoint
 **/
@AtomicSerial
public final class UdsEndpoint
    implements Endpoint, TrustEquivalence, Serializable
{
    private static final long serialVersionUID = 5548492446838485186L;

    public static SerialForm[] serialForm(){
        return new SerialForm[]{
            new SerialForm("path", String.class)
        };
    }

    public static void serialize(PutArg arg, UdsEndpoint ep) throws IOException{
        arg.put("path", ep.path);
        arg.writeArgs();
    }

    /**
     * weak set of canonical instances; in order to use WeakHashMap,
     * maps canonical instances to weak references to themselves
     **/
    private static final Map<UdsEndpoint,WeakReference<UdsEndpoint>> internTable
            = new WeakHashMap<UdsEndpoint,WeakReference<UdsEndpoint>>();

    /** client transport logger */
    private static final Logger logger =
	Logger.getLogger("net.jini.jeri.uds.client");

    /**
     * The filesystem path of the Unix domain socket that this
     * <code>UdsEndpoint</code> connects to.
     *
     * @serial
     **/
    private final String path;

    private transient volatile ConnectionManager connectionManager;

    /**
     * Returns a <code>UdsEndpoint</code> instance for the given Unix domain
     * socket path.
     *
     * @param path the filesystem path of the socket to connect to
     *
     * @return a <code>UdsEndpoint</code> instance
     *
     * @throws NullPointerException if <code>path</code> is <code>null</code>
     * @throws IllegalArgumentException if <code>path</code> is empty
     **/
    public static UdsEndpoint getInstance(String path) {
	return intern(new UdsEndpoint(check(path)));
    }

    /**
     * Returns canonical instance equivalent to given instance.
     **/
    private static UdsEndpoint intern(UdsEndpoint endpoint) {
	synchronized (internTable) {
	    Reference<UdsEndpoint> ref = internTable.get(endpoint);
	    if (ref != null) {
		UdsEndpoint canonical = ref.get();
		if (canonical != null) {
		    return canonical;
		}
	    }
	    endpoint.connectionManager =
		new ConnectionManager(
                        new ConnectionEndpointImpl(endpoint.getPath()));
	    internTable.put(endpoint, new WeakReference<UdsEndpoint>(endpoint));
	    return endpoint;
	}
    }

    private static String checkSerial(String path) throws InvalidObjectException{
	try {
	    return check(path);
	} catch (RuntimeException e){
	    InvalidObjectException ex =
		    new InvalidObjectException("Invariants not satisfied: "
			    + e.getMessage());
	    ex.initCause(e);
	    throw ex;
	}
    }

    /** Invariant checks. */
    private static String check(String path){
	if (path == null) {
	    throw new NullPointerException("null socket path");
	}
	if (path.isEmpty()) {
	    throw new IllegalArgumentException("empty socket path");
	}
	return path;
    }

    UdsEndpoint(GetArg arg) throws IOException, ClassNotFoundException {
	this(checkSerial(arg.get("path", null, String.class)));
    }

    /** Constructs a new instance. */
    private UdsEndpoint(String path) {
	this.path = path;
    }

    /*
     * Resolves deserialized instance to equivalent canonical instance.
     */
    private Object readResolve() {
	return intern(this);
    }

    /**
     * Returns the Unix domain socket path that this <code>UdsEndpoint</code>
     * connects to.
     *
     * @return the socket path that this endpoint connects to
     **/
    public String getPath() {
	return path;
    }

    /**
     * {@inheritDoc}
     *
     * <p>Initiates an attempt to communicate the request to the remote
     * endpoint by connecting a Unix domain {@link SocketChannel} to this
     * endpoint's socket path.  If there is a security manager, its {@link
     * SecurityManager#checkPermission checkPermission} method is invoked with a
     * {@link FilePermission} for {@code "read"} on the socket path ({@code
     * "connect"} is not a valid {@code FilePermission} action -- see {@link
     * ConnectionEndpointImpl#checkConnectPermission} for the audit-derived
     * rationale, Phase&nbsp;3 item&nbsp;3).
     *
     * @throws NullPointerException {@inheritDoc}
     **/
    public OutboundRequestIterator
	newRequest(final InvocationConstraints constraints)
    {
	if (constraints == null) {
	    throw new NullPointerException();
	}

	try {
	    Constraints.Distilled distilled =
		Constraints.distill(constraints, false);
	    return connectionManager.newRequest(new Handle(distilled));

	} catch (final UnsupportedConstraintException e) {
	    return new OutboundRequestIterator() {
		private boolean nextCalled = false;
		public boolean hasNext() { return !nextCalled; }
		public OutboundRequest next() throws IOException {
		    if (!hasNext()) { throw new NoSuchElementException(); }
		    nextCalled = true;
		    e.fillInStackTrace();
		    throw e;
		}
	    };
	}
    }

    public int hashCode() {
	return path.hashCode();
    }

    public boolean equals(Object obj) {
	if (obj == this) {
	    return true;
	} else if (!(obj instanceof UdsEndpoint)) {
	    return false;
	}
	UdsEndpoint other = (UdsEndpoint) obj;
	return path.equals(other.path);
    }

    public boolean checkTrustEquivalence(Object obj) {
	if (obj == this) {
	    return true;
	} else if (!(obj instanceof UdsEndpoint)) {
	    return false;
	}
	UdsEndpoint other = (UdsEndpoint) obj;
	return path.equals(other.path);
    }

    public String toString() {
	return "UdsEndpoint[" + path + "]";
    }

    private void writeObject(ObjectOutputStream out) throws IOException {
	out.defaultWriteObject();
    }

    /**
     * @throws InvalidObjectException if the socket path is <code>null</code> or
     * empty
     **/
    private void readObject(ObjectInputStream in)
	throws IOException, ClassNotFoundException
    {
	in.defaultReadObject();
	checkSerial(path);
    }

    /**
     * OutboundRequestHandle implementation.
     **/
    private class Handle implements OutboundRequestHandle {

	private final Constraints.Distilled distilled;

	Handle(Constraints.Distilled distilled) {
	    this.distilled = distilled;
	}

	UdsEndpoint getUdsEndpoint() {
	    return UdsEndpoint.this;
	}

	Constraints.Distilled getDistilledConstraints() {
	    return distilled;
	}

	InvocationConstraints getUnfulfilledConstraints() {
	    return distilled.getUnfulfilledConstraints();
	}
    }

    /**
     * ConnectionEndpoint implementation.
     *
     * Instances of this class should never get exposed to anything
     * other than our ConnectionManager, which we trust to operate
     * correctly, so we do not bother to validate request handles and
     * connections passed in.
     **/
    private static class ConnectionEndpointImpl implements ConnectionEndpoint {
	private final String path;

	ConnectionEndpointImpl(String path) {
	    this.path = path;
	}

	/**
	 * Invoked by ConnectionManager to create a new connection.
	 **/
	public Connection connect(OutboundRequestHandle handle)
	    throws IOException
	{
	    Handle h = (Handle) handle;
	    checkConnectPermission();
	    SocketChannel channel = connect(h.getDistilledConstraints());

	    if (logger.isLoggable(Level.FINE)) {
		logger.log(Level.FINE, "connected uds channel {0}", channel);
	    }

	    return new ConnectionImpl(channel, path);
	}

	/**
	 * Returns a Unix domain socket channel connected to this endpoint's
	 * socket path.  Unlike TCP there is no name resolution and no address
	 * iteration -- a UDS path denotes exactly one local socket.
	 **/
	private SocketChannel connect(Constraints.Distilled distilled)
	    throws IOException
	{
	    if (distilled.hasConnectDeadline()) {
		long now = System.currentTimeMillis();
		long deadline = distilled.getConnectDeadline();
		if (deadline <= now) {
		    throw new SocketTimeoutException(
			"deadline past before connect attempt");
		}
	    }
	    UnixDomainSocketAddress address = UnixDomainSocketAddress.of(path);
	    SocketChannel channel =
		SocketChannel.open(StandardProtocolFamily.UNIX);
	    boolean ok = false;
	    try {
		channel.connect(address);
		ok = true;
		return channel;
	    } finally {
		if (!ok) {
		    try {
			channel.close();
		    } catch (IOException e) {
		    }
		}
	    }
	}

	/**
	 * Invoked by ConnectionManager to reuse an existing connection.
	 **/
	public Connection connect(OutboundRequestHandle handle,
				  Collection active,
				  Collection idle)
	{
	    if (active == null || idle == null) {
		throw new NullPointerException();
	    }
	    /*
	     * The transport level aspects of all constraints supported by this
	     * transport provider are always satisfied by all open connections,
	     * so we don't need to consider constraints here.  All connections
	     * from this endpoint are to the same socket path, so any existing
	     * connection is reusable provided the caller has connect permission.
	     */
	    for (Iterator i = active.iterator(); i.hasNext();) {
		ConnectionImpl c = (ConnectionImpl) i.next();
		try {
		    checkConnectPermission();
		    if (logger.isLoggable(Level.FINE)) {
			logger.log(Level.FINE, "reusing connection {0}", c);
		    }
		    return c;
		} catch (SecurityException e) {
		    logReuseDenied(c, e);
		}
	    }
	    for (Iterator i = idle.iterator(); i.hasNext();) {
		ConnectionImpl c = (ConnectionImpl) i.next();
		try {
		    checkConnectPermission();
		    if (logger.isLoggable(Level.FINE)) {
			logger.log(Level.FINE, "reusing connection {0}", c);
		    }
		    return c;
		} catch (SecurityException e) {
		    logReuseDenied(c, e);
		}
	    }
	    return null;
	}

	private void logReuseDenied(ConnectionImpl c, SecurityException e) {
	    if (logger.isLoggable(Levels.HANDLED)) {
		LogUtil.logThrow(logger, Levels.HANDLED,
		    ConnectionEndpointImpl.class, "connect",
		    "access to reuse connection {0} denied",
		    new Object[] { c }, e);
	    }
	}

	/**
	 * Permission model (Phase 3 item 3): a UDS peer is named by a filesystem
	 * path, so client connect authority is guarded by a path-scoped {@code
	 * FilePermission} on the socket file.  TCP uses {@code SocketPermission};
	 * there is no host:port here.
	 *
	 * <p>The action is {@code "read"} -- {@code "connect"} is <em>not</em> a
	 * valid {@code FilePermission} action (the only actions are
	 * {@code read,write,execute,delete,readlink}), so constructing
	 * {@code FilePermission(path,"connect")} throws {@code
	 * IllegalArgumentException} before the {@code SecurityManager} is ever
	 * consulted.  This was confirmed by a polpAudit run under an active
	 * {@code SecurityManager} (au.zeus.jdk.authorization.tool.SecurityPolicyWriter):
	 * connecting to a UDS opens the socket special file, and the coarse JDK
	 * capability gate the audit records for that is
	 * {@code java.net.NetPermission("accessUnixDomainSocket")}; the natural
	 * path-scoped filesystem-reach guard is {@code "read"} on the socket file
	 * -- the same action the accept side already uses for the same file (see
	 * {@code UdsServerEndpoint.ServerConnectionImpl.checkPermissions}), keeping
	 * the two ends of a connection symmetric.
	 */
	private void checkConnectPermission() {
	    SecurityManager sm = System.getSecurityManager();
	    if (sm != null) {
		sm.checkPermission(new FilePermission(path, "read"));
	    }
	}
    }

    /**
     * Connection implementation over a Unix domain SocketChannel.
     *
     * Instances of this class should never get exposed to anything other than
     * our ConnectionManager, which we trust to operate correctly, so we do not
     * bother to validate request handles passed in.
     **/
    private static class ConnectionImpl implements Connection {

	private final SocketChannel channel;
	private final String path;

	ConnectionImpl(SocketChannel channel, String path) {
	    assert channel.isConnected();
	    this.channel = channel;
	    this.path = path;
	}

	public InputStream getInputStream() throws IOException {
	    return Channels.newInputStream(channel);
	}

	public OutputStream getOutputStream() throws IOException {
	    return Channels.newOutputStream(channel);
	}

	/*
	 * A Unix domain SocketChannel IS a SocketChannel, so the mux layer can
	 * use it for non-blocking I/O directly -- exactly as TcpEndpoint
	 * returns socket.getChannel().  The stream-vs-channel wiring in the mux
	 * requires the channel to be in blocking mode when streams are also
	 * used; SocketChannel.open() returns a blocking channel by default, so
	 * no change is needed.  (The mux selects the channel path only when a
	 * non-null channel is returned here.)
	 */
	public SocketChannel getChannel() {
	    return channel;
	}

	public void populateContext(OutboundRequestHandle handle,
				    Collection context)
	{
	    if (context == null) {
		throw new NullPointerException();
	    }
	}

	public InvocationConstraints
	    getUnfulfilledConstraints(OutboundRequestHandle handle)
	{
	    Handle h = (Handle) handle;
	    return h.getUnfulfilledConstraints();
	}

	/**
	 * Identity seam (workload SVID presentation) -- see class javadoc.
	 * This is the client-side connection-setup hook where the process's
	 * SPIFFE workload SVID/JWT would be written for the server to verify.
	 * For this plaintext increment it is a no-op, exactly as it is for TCP.
	 */
	public void writeRequestData(OutboundRequestHandle handle,
				     OutputStream out)
	{
	    if (out == null) {
		throw new NullPointerException();
	    }
	}

	public IOException readResponseData(OutboundRequestHandle handle,
					    InputStream in)
	{
	    if (in == null) {
		throw new NullPointerException();
	    }
	    return null;
	}

	public void close() {
	    try {
		channel.close();
	    } catch (Exception e) {
	    }
	    if (logger.isLoggable(Level.FINE)) {
		logger.log(Level.FINE, "closed uds channel {0}", channel);
	    }
	}

	public String toString() {
	    return "UdsEndpoint.ConnectionImpl[" + path + "]";
	}
    }
}
