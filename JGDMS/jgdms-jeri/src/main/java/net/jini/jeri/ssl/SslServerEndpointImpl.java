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

import org.apache.river.action.GetLongAction;
import org.apache.river.jeri.internal.connection.BasicServerConnManager;
import org.apache.river.jeri.internal.connection.ServerConnManager;
import org.apache.river.jeri.internal.runtime.Util;
import org.apache.river.logging.Levels;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.nio.channels.SocketChannel;
import java.security.AccessControlContext;
import java.lang.reflect.Constructor;
import java.security.AccessController;
import java.security.GeneralSecurityException;
import java.security.Permission;
import java.security.Principal;
import java.security.PrivilegedAction;
import java.security.cert.CertPath;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.net.ServerSocketFactory;
import javax.net.SocketFactory;
import javax.net.ssl.SSLPeerUnverifiedException;
import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import javax.security.auth.Subject;
import javax.security.auth.x500.X500Principal;
import javax.security.auth.x500.X500PrivateCredential;
import net.jini.core.constraint.AtomicInputValidation;
import net.jini.core.constraint.Integrity;
import net.jini.core.constraint.InvocationConstraint;
import net.jini.core.constraint.InvocationConstraints;
import net.jini.io.UnsupportedConstraintException;
import net.jini.jeri.Endpoint;
import net.jini.jeri.InboundRequest;
import net.jini.jeri.RequestDispatcher;
import net.jini.jeri.ServerEndpoint.ListenContext;
import net.jini.jeri.ServerEndpoint.ListenCookie;
import net.jini.jeri.ServerEndpoint.ListenEndpoint;
import net.jini.jeri.ServerEndpoint.ListenHandle;
import net.jini.jeri.ServerEndpoint;
import net.jini.jeri.connection.InboundRequestHandle;
import net.jini.jeri.connection.ServerConnection;
import net.jini.security.AuthenticationPermission;
import net.jini.security.Security;
import net.jini.security.SecurityContext;
import org.apache.river.jeri.internal.runtime.LocalHost;

/**
 * Provides the implementation of SslServerEndpoint so that the implementation
 * can be inherited by HttpsServerEndpoint without revealing the inheritance in
 * the public API.
 *
 * 
 */
class SslServerEndpointImpl extends Utilities {

    /* -- Fields -- */

    /** Server logger */
    static final Logger logger = SERVER_LOGGER;

	/**
	 * Builder for subject-neutral virtual infrastructure threads.
	 *
	 * <p>In DirtyChai, {@code Thread} captures {@code Subject.current()} at
	 * construction time ({@code scopedSubject}) and {@code Thread.runWith}
	 * re-establishes it as a ScopedValue scope around the entire thread body.
	 * Infrastructure threads (accept loops, SSL context rebuild) must be
	 * subject-neutral: they are long-lived, block on I/O, and must not run
	 * under a caller's subject (confused-deputy risk).
	 *
	 * <p>Each call to {@code .start()} or {@code .unstarted()} on this builder
	 * is wrapped in {@code Subject.callAs(null, ...)} at the call site,
	 * ensuring each constructed thread captures {@code null} as its
	 * {@code scopedSubject}.  {@code Thread.runWith} then takes the no-scope
	 * branch and no ScopedValue subject scope wraps the thread body.
	 */
	static final Thread.Builder.OfVirtual acceptThreadBuilder = Thread.ofVirtual();

    /** The default server connection manager. */
    private static final ServerConnManager defaultServerConnectionManager =
	new BasicServerConnManager();
    
    private static final LocalHost LOCAL_HOST = new LocalHost(logger, SslServerEndpointImpl.class);

    /**
     * Reflective constructor for {@code javax.security.auth.RemoteSubject(X509Certificate[])}
     * -- DirtyChai's read-only, authenticated-remote-peer {@code WorkerSubject} subclass.
     * It derives the X.500 DN and SPIFFE URI principals from the verified leaf certificate,
     * carries the peer {@code CertPath} as its sole (public) credential, and holds no
     * private credentials.
     *
     * <p>jgdms-jeri compiles on stock OpenJDK (where this class is absent) but runs only on
     * DirtyChai (where it is always present), so the type is bound reflectively once here.
     * A {@code null} value -- a non-DirtyChai JVM, which JGDMS does not support at runtime --
     * makes {@code getClientSubject} fall back to a plain read-only X.500 {@code Subject}
     * (defensive only; logged at HANDLED).
     */
    private static final Constructor<?> REMOTE_SUBJECT_CTOR =
        AccessController.doPrivileged((PrivilegedAction<Constructor<?>>) () -> {
            try {
                return Class.forName("javax.security.auth.RemoteSubject")
                            .getConstructor(X509Certificate[].class);
            } catch (ClassNotFoundException | NoSuchMethodException e) {
                return null;
            }
        });

    /**
     * Mints the authenticated-remote-peer {@code RemoteSubject} from the verified mTLS
     * peer certificate chain, or returns {@code null} when the DirtyChai type is absent
     * (non-DirtyChai JVM) or construction fails -- in which case the caller uses the
     * defensive X.500-only fallback.
     */
    private static Subject mintRemoteSubject(Certificate[] chain) {
        if (REMOTE_SUBJECT_CTOR == null) return null;
        try {
            X509Certificate[] x509Chain = new X509Certificate[chain.length];
            for (int i = 0; i < chain.length; i++) {
                x509Chain[i] = (X509Certificate) chain[i];
            }
            return (Subject) REMOTE_SUBJECT_CTOR.newInstance((Object) x509Chain);
        } catch (ReflectiveOperationException | RuntimeException e) {
            if (logger.isLoggable(Levels.HANDLED)) {
                logger.log(Levels.HANDLED, "RemoteSubject construction failed", e);
            }
            return null;
        }
    }

    /** The associated server endpoint. */
    private final ServerEndpoint serverEndpoint;

    /** The listen endpoint. */
    private final SslListenEndpoint listenEndpoint;

    /** Creates an instance of this class. */
    SslServerEndpointImpl(ServerEndpoint serverEndpoint,
                            SslListenEndpoint listenEndpoint)
    {
		this.serverEndpoint = serverEndpoint;
		this.listenEndpoint = listenEndpoint;
    }
    
    /* -- Methods -- */

    /** Computes the principals in the subject available for authentication */
    private static Set computePrincipals(Subject subject) {
		if (subject == null) return null;
		/* Get principals from the subject */
		X500PrivateCredential[] credentials =
			(X500PrivateCredential[]) AccessController.doPrivileged(
				new SubjectCredentials.GetAllPrivateCredentialsAction(subject));
		Set result = SubjectCredentials.getPrincipals(subject, ANY_KEY_ALGORITHM, credentials);
		/* Remove ones we don't have authentication listen permission for. */
		SecurityManager sm = System.getSecurityManager();
		if (sm != null) {
			for (Iterator iter = result.iterator(); iter.hasNext(); ) {
				Principal p = (Principal) iter.next();
				try {
					sm.checkPermission(
					new AuthenticationPermission(
						Collections.singleton(p), null, "listen"));
				} catch (SecurityException e) {
					logger.log(Levels.HANDLED,
						   "compute principals for server endpoint " +
						   "caught exception",
						   e);
					iter.remove();
				}
			}
		}
		return result.isEmpty() ? null : result;
    }

    /**
     * Checks that principals is not empty and contains no nulls, and returns
     * it as a set.  Returns null if no principals are specified.
     */
    private static Set<X500Principal> checkPrincipals(X500Principal[] principals) {
		if (principals.length == 0) {
			return null;
		}
		Set<X500Principal> result = new HashSet<X500Principal>(principals.length);
		for (int i = principals.length; --i >= 0; ) {
			X500Principal p = principals[i];
			if (p == null) throw new NullPointerException("Server principal cannot be null");
			result.add(p);
		}
		return result;
    }

    

	/** Returns the SSLSocketFactory, calling sslInit if needed. */
    final SSLSocketFactory getSSLSocketFactory() {
		return listenEndpoint.getSSLSocketFactory();
    }

    /** Returns the ServerAuthManager, calling sslInit if needed. */
    final ServerAuthManager getAuthManager() {
		return listenEndpoint.getAuthManager();
    }

    /** Returns a hash code value for this object. */
    public int hashCode() {
		return getClass().hashCode()
			^ System.identityHashCode(listenEndpoint.serverSubject)
			^ (getServerPrincipals() == null ? 0 : getServerPrincipals().hashCode())
			^ (getServerHost() == null ? 0 : getServerHost().hashCode())
			^ getPort()
			^ (getSocketFactory() != null ? getSocketFactory().hashCode() : 0)
			^ (getServerSocketFactory() != null
			   ? getServerSocketFactory().hashCode() : 0);
    }

    /**
     * Two instances of this class are equal if they have the same actual
     * class; have server subjects that compare equal using ==; have server
     * principals that are either both null or are equal when compared as the
     * elements of a Set; have the same server host and port; have socket
     * factories that are either both null, or have the same actual class and
     * are equal; and have server socket factories that are either both null,
     * or have the same actual class and are equal.
     */
    public boolean equals(Object object) {
		if (object == null || object.getClass() != getClass()) return false;
		
		SslServerEndpointImpl other = (SslServerEndpointImpl) object;
		return listenEndpoint.serverSubject == other.listenEndpoint.serverSubject
			&& safeEquals(getServerPrincipals(), other.getServerPrincipals())
			&& safeEquals(getServerHost(), other.getServerHost())
			&& getPort() == other.getPort()
			&& Util.sameClassAndEquals(getSocketFactory(), other.getSocketFactory())
			&& Util.sameClassAndEquals(getServerSocketFactory(), other.getServerSocketFactory());
    }

    /** Returns a string representation of this object. */
    @Override
    public String toString() {
		return getClassName(this) + fieldsToString();
    }

    /** Returns a string representation of the fields of this object. */
    final String fieldsToString() {
        StringBuilder sb = new StringBuilder();
        Set<X500Principal> principals = getServerPrincipals();
        String serverHost = getServerHost();
        ServerSocketFactory ssf = getServerSocketFactory();
        SocketFactory sf = getSocketFactory();
        sb.append("[");
        if (principals != null) sb.append(principals).append(", ");
        if (serverHost != null) sb.append(serverHost).append(":");
        sb.append(getPort());
        if (ssf != null) sb.append(", ").append(ssf);
        if (sf != null) sb.append(", ").append(sf);
        sb.append("]");
		return sb.toString();
    }

    /* -- Implement ServerCapabilities -- */

	final InvocationConstraints checkConstraints(InvocationConstraints constraints)
												throws UnsupportedConstraintException
	{
		// Resolve the server subject lazily if needed (may have been deferred
		// from construction time to ensure the login Subject is in the ACC).
		listenEndpoint.resolveSubjectIfNeeded();
		try {
			listenEndpoint.checkListenPermissions(false);
		} catch (SecurityException e) {
			if (logger.isLoggable(Levels.FAILED)) {
				logThrow(logger, Levels.FAILED,
					 SslServerEndpoint.class, "checkConstraints",
					 "check constraints for {0}\nwith {1}\nthrows",
					 new Object[] { this, constraints },
					 e);
			}
			throw e;
		}
		Set clientPrincipals = getClientPrincipals(constraints);
		if (clientPrincipals == null) {
			clientPrincipals = Collections.singleton(UNKNOWN_PRINCIPAL);
		}
		Map serverKeyTypes = new HashMap();
		List certPaths =
			SubjectCredentials.getCertificateChains(listenEndpoint.serverSubject);
		if (certPaths != null) {
			for (int i = certPaths.size(); --i >= 0; ) {
				CertPath chain = (CertPath) certPaths.get(i);
				X509Certificate cert = SubjectCredentials.firstX509Cert(chain);
				X500Principal principal = SubjectCredentials.getPrincipal(listenEndpoint.serverSubject, cert);
				if (principal != null) {
					Collection keyTypes =
					(Collection) serverKeyTypes.get(principal);
					if (keyTypes == null) {
						keyTypes = new ArrayList(1);
						serverKeyTypes.put(principal, keyTypes);
					}
					keyTypes.add(Utilities.normalizeKeyAlgorithm(cert.getPublicKey().getAlgorithm()));
				}
			}
		} else {
			throw new UnsupportedConstraintException("Server credentials unsuitable for TLS");
		}
		String[] suites = getSupportedCipherSuites();
		for (int suiteIndex = suites.length; --suiteIndex >= 0; ) {
			String suite = suites[suiteIndex];
			String suiteKeyType = getKeyAlgorithm(suite);
			Iterator sIter =
			(getServerPrincipals() == null
			 ? Collections.EMPTY_SET : getServerPrincipals()).iterator();
			X500Principal server;
			do {
				if (sIter.hasNext()) {
					server = (X500Principal) sIter.next();
					assert server != null;
					Collection keyTypes =
					(Collection) serverKeyTypes.get(server);
					if (keyTypes == null || !keyTypes.contains(suiteKeyType)) {
					continue;
					}
				} else {
					server = null;
				}
				Iterator cIter = clientPrincipals.iterator();
				Principal client;
				do {
					if (cIter.hasNext()) {
						client = (Principal) cIter.next();
						assert client != null;
					} else {
						client = null;
					}
					InvocationConstraints unfulfilledConstraints =
					getUnfulfilledIntegrityOrAtomicityConstraints(
						suite, client, server, constraints);
					if (unfulfilledConstraints != null) {
						if (logger.isLoggable(Level.FINE)) {
							logger.log(Level.FINE,
								   "check constraints for {0}\n" +
								   "with {1}\nreturns {2}",
								   new Object[] {
								   serverEndpoint, constraints,
								   unfulfilledConstraints
								   });
						}
						return unfulfilledConstraints;
					}
				} while (client != null);
			} while (server != null);
		}
		UnsupportedConstraintException uce =
			new UnsupportedConstraintException(
			"Constraints are not supported: " + constraints);
		if (logger.isLoggable(Levels.FAILED)) {
			logThrow(
			logger, Levels.FAILED,
			SslServerEndpoint.class, "checkConstraints",
			"check constraints for {0}\nwith {1}\nthrows",
			new Object[] { serverEndpoint, constraints },
			uce);
		}
		throw uce;
    }

    /**
     * Returns null if the constraints are not supported, else any integrity
     * constraints required or preferred by the arguments.
     */
    static InvocationConstraints getUnfulfilledConstraints(
	String cipherSuite,
	Principal client,
	Principal server,
	InvocationConstraints constraints)
    {
		boolean supported = false;
		for (int i = 2; --i >= 0; ) {
			boolean integrity = i == 0;
			ConnectionContext context = ConnectionContext.getInstance(cipherSuite, client, server, integrity,
			false /* clientSide */, constraints);
			if (context != null) {
				if (context.getIntegrityRequired()) {
					return INTEGRITY_REQUIRED;
				} else if (context.getIntegrityPreferred()) {
					return INTEGRITY_PREFERRED;
				} else {
					supported = true;
				}
			}
		}
		return supported ? InvocationConstraints.EMPTY : null;
    }
    
    /**
     * Returns null if the constraints are not supported, else any integrity
     * constraints required or preferred by the arguments.
     */
    static InvocationConstraints getUnfulfilledIntegrityOrAtomicityConstraints(
	String cipherSuite,
	Principal client,
	Principal server,
	InvocationConstraints constraints)
    {
		List<InvocationConstraint> requiredConstraints = new ArrayList<InvocationConstraint>(2);
		List<InvocationConstraint> preferredConstraints = new ArrayList<InvocationConstraint>(2);
		boolean supported = false;
		for (int i = 2; --i >= 0; ) {
			boolean upperLayerConstraints = i == 1;
			ConnectionContext context = 
				ConnectionContext.getInstance(cipherSuite,
					client,
					server,
					upperLayerConstraints,
					false /* clientSide */,
					constraints);
			if (context != null) {
				if (context.getIntegrityRequired()) {
					requiredConstraints.add(Integrity.YES);
				} else if (context.getIntegrityPreferred()) {
					preferredConstraints.add(Integrity.YES);
				} else if (context.getAtomicityRequired()){
					requiredConstraints.add(AtomicInputValidation.YES);
				} else if (context.getAtomicityPreferred()){
					preferredConstraints.add(AtomicInputValidation.YES);
				} else {
					supported = true;
				}
			}
		}
		if (!requiredConstraints.isEmpty() || !preferredConstraints.isEmpty()){
			return new InvocationConstraints(
				requiredConstraints,
				preferredConstraints
			);
		}
		return supported ? InvocationConstraints.EMPTY : null;
    }

    /* -- Implement ServerEndpoint -- */

    final Endpoint enumerateListenEndpoints(ListenContext listenContext)
	throws IOException
    {
        String resolvedHost = LOCAL_HOST.check(this.getServerHost(), this);

        Endpoint result = createEndpoint(
            resolvedHost,
            checkCookie(listenContext.addListenEndpoint(listenEndpoint)));
        if (logger.isLoggable(Level.FINE)) {
            logger.log(Level.FINE,
                       "enumerate listen endpoints for {0}\nreturns {1}",
                       new Object[] { this, result });
        }
        return result;
    }

    /**
     * Creates an endpoint for this server endpoint corresponding to the
     * specified server host and listen cookie.
     */
    Endpoint createEndpoint(String serverHost, SslListenCookie cookie) {
		return SslEndpoint.getInstance(serverHost, cookie.getPort(), getSocketFactory());
    }

    /**
     * Checks that the argument is a valid listen cookie for this server
     * endpoint.
     */
    private SslListenCookie checkCookie(ListenCookie cookie) {
		if (!(cookie instanceof SslListenCookie)) {
			throw new IllegalArgumentException(
			"Cookie must be of type SslListenCookie: " + cookie);
		}
		SslListenCookie sslListenCookie = ((SslListenCookie) cookie);
		ListenEndpoint cookieListenEndpoint
			= sslListenCookie.getListenEndpoint();

		if (!listenEndpoint.equals(cookieListenEndpoint)) {
			throw new IllegalArgumentException(
			"Cookie has wrong listen endpoint: found " +
			cookieListenEndpoint + ", expected " + listenEndpoint);
		}
		return sslListenCookie;
    }

    /**
     * @return the serverPrincipals
     */
    Set<X500Principal> getServerPrincipals() {
        return listenEndpoint.serverPrincipals;
    }

    /**
     * @return the serverHost
     */
    String getServerHost() {
        return listenEndpoint.serverHost;
    }

    /**
     * @return the port
     */
    int getPort() {
        return listenEndpoint.port;
    }

    /**
     * @return the socketFactory
     */
    SocketFactory getSocketFactory() {
        return listenEndpoint.socketFactory;
    }

    /**
     * @return the serverSocketFactory
     */
    ServerSocketFactory getServerSocketFactory() {
        return listenEndpoint.serverSocketFactory;
    }
    
    void setServerConnectionManager(ServerConnManager connectionManager){
        listenEndpoint.setServerConnectionManager(connectionManager);
    }

    /** Implements ListenEndpoint */
    static class SslListenEndpoint extends Utilities implements ListenEndpoint {
        
        /**
         * The maximum time a session should be used before expiring -- non-final
         * to facilitate testing.  Use 24 hours to allow the client, which uses
         * 23.5 hours, to renegotiate a new session before the server timeout.
         */
        private final long maxServerSessionDuration =
            ((Long) Security.doPrivileged(
                new GetLongAction("org.apache.river.jeri.ssl.maxServerSessionDuration",
                                  24L * 60L * 60L * 1000L))).longValue();
        
		/**
		 * Whether to resolve the server subject lazily from the ambient
		 * security context (SPIFFE, Subject.current(), or ACC) at the time
		 * of first use, rather than at construction time.  Set when the
		 * caller passed {@code null} as the serverSubject.
		 */
		private final boolean useCurrentSubject;

		/** The server subject, or null if the server is anonymous. */
		private volatile Subject serverSubject;

		/**
		 * The principals to use for authentication, or null if the server is
		 * anonymous.  Lazily computed when useCurrentSubject is true.
		 */
		private volatile Set<X500Principal> serverPrincipals;

        /**
         * The host name that clients should use to connect to this server, or null
         * if enumerateListenEndpoints should compute the default.
         */
        final String serverHost;

        /** The server port */
        final int port;

        /** The socket factory for use in the associated Endpoint. */
        final SocketFactory socketFactory;

        /** The server socket factory. */
        final ServerSocketFactory serverSocketFactory;

        private ServerConnManager serverConnectionManager;
        
        /**
         * The permissions needed to authenticate when listening on this endpoint,
         * or null if the server is anonymous.  Effectively immutable array.
         */
        private final Permission[] listenPermissions;
        
        
		/** The factory for creating JSSE sockets -- set by sslInit */
		private volatile SSLSocketFactory sslSocketFactory;

		/**
		 * The authentication manager for the SSLContext for this endpoint -- set
		 * by sslInit.
		 */
		private volatile ServerAuthManager authManager;

		/**
		 * True while a background SSL-context rebuild is scheduled or running
		 * after a SPIFFE subject rotation.  Prevents duplicate background tasks
		 * from being submitted on every call during the rebuild window.
		 */
		private boolean refreshScheduled = false;
        
		SslListenEndpoint(Subject serverSubject,
							X500Principal[] serverPrincipals,
							String serverHost,
							int port,
							SocketFactory socketFactory,
							ServerSocketFactory serverSocketFactory)
		{
			this.serverConnectionManager = defaultServerConnectionManager;
			this.serverHost = serverHost;
			this.port = port;
			this.socketFactory = socketFactory;
			this.serverSocketFactory = serverSocketFactory;
			this.useCurrentSubject = (serverSubject == null && serverPrincipals == null);
			if (this.useCurrentSubject) {
				// Subject and principals resolved lazily at first use
				// (checkConstraints / sslInit / listen) so Subject.doAsPrivileged
				// has already established the login Subject in the ACC by then.
				this.serverSubject = null;
				this.serverPrincipals = null;
				this.listenPermissions = null;
			} else {
				this.serverSubject = serverSubject;
				this.serverPrincipals = (serverPrincipals == null)
					? computePrincipals(serverSubject)
					: checkPrincipals(serverPrincipals);
				Permission[] listenPerms;
				if (this.serverPrincipals == null) {
					listenPerms = null;
				} else {
					listenPerms =
						new AuthenticationPermission[this.serverPrincipals.size()];
					int i = 0;
					for (Iterator iter = this.serverPrincipals.iterator(); iter.hasNext(); i++)
					{
						Principal p = (Principal) iter.next();
						listenPerms[i] = new AuthenticationPermission(Collections.singleton(p), null, "listen");
					}
				}
				if (this.serverPrincipals == null ||
					!hasListenPermissions(listenPerms, port))
				{
					this.serverSubject = null;
					listenPerms = null;
				}
				this.listenPermissions = listenPerms;
			}
		}

		/* inherit javadoc */
			@Override
		public void checkPermissions() {
			// When the subject is resolved lazily (useCurrentSubject=true) we
			// must resolve it HERE — while the service Subject is still active
			// on the calling thread via Subject.callAs — so that hashCode() and
			// equals() already see the resolved Subject when BasicExportTable
			// does the listenPool lookup in getBinding().  Without this, every
			// port-0 endpoint with useCurrentSubject=true hashes and compares
			// identically, causing different services (e.g. SharedGroup and
			// Mahalo) in the same group JVM to share the same SslListenHandle.
			if (useCurrentSubject) resolveSubjectIfNeeded();
			checkListenPermissions(true, listenPermissions, port);
		}
        
        /**
         * Returns true if the caller has AuthenticationPermission for listen on
         * this endpoint.
         */
        private static boolean hasListenPermissions(Permission[] listenPerms, int port) {
            try {
                checkListenPermissions(false, listenPerms, port);
                return true;
            } catch (SecurityException e) {
                logger.log(Levels.HANDLED,
                           "check listen permissions for server endpoint " +
                           "caught exception",
                           e);
                return false;
            }
        }
        
        /**
         * Check for permission to listen on this endpoint, but only checking
         * socket permissions if checkSocket is true.
         */
        final void checkListenPermissions(boolean checkSocket) {
            checkListenPermissions(checkSocket, listenPermissions, port);
        }
        
        /**
         * Check for permission to listen on this endpoint, but only checking
         * socket permissions if checkSocket is true.
         */
        static void checkListenPermissions(boolean checkSocket, Permission[] listenPerms, int port) {
            SecurityManager sm = System.getSecurityManager();
            if (sm != null) {
                if (checkSocket) {
                    sm.checkListen(port);
                }
                if (listenPerms != null) {
                    for (int i = listenPerms.length; --i >= 0; ) {
                        sm.checkPermission(listenPerms[i]);
                    }
                }
            }
        }
        
		/**
		 * Resolves the server Subject from the ambient security context,
		 * called at the start of every operation that reads
		 * {@link #serverSubject} or {@link #serverPrincipals}.
		 *
		 * <p>When {@link #useCurrentSubject} is {@code false} the Subject
		 * was supplied explicitly at construction time and is never changed.</p>
		 *
		 * <p>When {@link #useCurrentSubject} is {@code true} the resolution
		 * order is:</p>
		 * <ol>
		 *   <li><b>SPIFFE</b> — always re-fetched on every call because the
		 *       process-wide SPIFFE SVID rotates (typically hourly).  If the
		 *       Subject reference changes the cached {@code sslSocketFactory}
		 *       and {@code authManager} are invalidated so that
		 *       {@link #sslInit()} recreates them with the fresh credentials.</li>
		 *   <li><b>{@code Subject.current()}</b> — thread-scoped Subject set
		 *       by {@code Subject.callAs()} or {@code Subject.doAsPrivileged()}.
		 *       Only consulted when SPIFFE returns {@code null} and no subject
		 *       has been cached yet.</li>
		 * </ol>
		 */
		synchronized void resolveSubjectIfNeeded() {
            if (!useCurrentSubject) return; // Subject was supplied explicitly — never re-resolve
            if (logger.isLoggable(Level.FINE)) {
                logger.log(Level.FINE,
                    "resolveSubjectIfNeeded: serverSubject={0}, thread={1}",
                     new Object[]{ serverSubject, Thread.currentThread().getName() });
            }

            // Priority 1: Process-wide SPIFFE Subject.
            // Always re-fetch: the SPIFFE SVID rotates (typically hourly) and
            // Utilities.spiffeWorkerSubject() returns the current live Subject.
            Subject spiffe = Utilities.spiffeWorkerSubject();
            if (spiffe != null) {
                if (spiffe != serverSubject) {
                    // Subject has been refreshed — update subject state and
                    // schedule a background SSL context rebuild so the hot path
                    // (getSSLSocketFactory / getAuthManager) is never blocked.
                    // The old factories remain in service until the rebuild
                    // completes and the volatile write swaps them in.
                    serverSubject = spiffe;
                    serverPrincipals = computePrincipals(spiffe);
                    scheduleSSLContextRebuild();
                }
                return;
            }

            // Priority 2: Thread-scoped Subject.current() (set by Subject.callAs).
            // Re-resolved on every call — the ScopedValue changes per scope so
            // caching is incorrect (e.g. harness group Tester scope vs service
            // Mahalo scope).
            Subject current = Subject.current();
            if (logger.isLoggable(Level.FINE)) {
                logger.log(Level.FINE,
                   "resolveSubjectIfNeeded: Subject.current()={0}", current);
            }
            if (current != null && hasTlsIdentity(current)) {
                serverSubject = current;
                serverPrincipals = computePrincipals(current);
            } else if (logger.isLoggable(Level.FINE)) {
                logger.log(Level.FINE,
                    "resolveSubjectIfNeeded: no TLS-capable subject found on thread {0}",
                Thread.currentThread().getName());
            }
		}

		/**
		 * Schedules a background task to rebuild the SSL context after a SPIFFE
		 * subject rotation.  The old {@code sslSocketFactory} and
		 * {@code authManager} remain live until the rebuild completes and the
		 * volatile fields are swapped in.  At most one rebuild task is queued at
		 * a time; subsequent rotation detections during the window are no-ops.
		 *
		 * <p>Must be called while holding {@code this} monitor.</p>
		 */
		private void scheduleSSLContextRebuild() {
			assert Thread.holdsLock(this);
			if (refreshScheduled) return; // a rebuild is already in flight			
			refreshScheduled = true;
			final SslListenEndpoint self = this;
			Subject.callAs(null, () ->
				acceptThreadBuilder.name("SSL context rebuild after SPIFFE rotation").start(
					new Runnable() {
						@Override
						public void run() {
							synchronized (self) {
								try {
									// Re-read the latest subject inside the lock in case
									// it rotated again between scheduling and execution.
									Subject latest = Utilities.spiffeWorkerSubject();
									if (latest != null && latest != serverSubject) {
										serverSubject = latest;
										serverPrincipals = computePrincipals(latest);
									}
									SSLContextInfo info = getServerSSLContextInfo(
											serverSubject, serverPrincipals);
									// Volatile writes — immediately visible to all threads.
									sslSocketFactory = info.sslContext.getSocketFactory();
									authManager = (ServerAuthManager) info.authManager;
								} finally {
									refreshScheduled = false;
								}
							}
						}
					}
				)
			);
		}

		/**
		 * Eagerly initialises the SSL context if not already done.
		 * Called from {@link SslServerEndpointImpl#listen} while the service
		 * subject is active on the calling thread, ensuring
		 * {@code resolveSubjectIfNeeded()} finds a TLS-capable subject.
		 * Must not be called from a subject-neutral infrastructure thread.
		 */
		synchronized void initIfNeeded() {
		    if (sslSocketFactory == null) sslInit();
		}

		/**
		 * Initializes the sslSocketFactory and authManager fields.  Wait to do
		 * this until needed, because creating the SSLContext requires initializing
		 * the secure random number generator, which can be time consuming.
		 * Only called for the initial setup; SPIFFE rotation rebuilds are handled
		 * by the background task in {@link #scheduleSSLContextRebuild()}.
		 */
		private void sslInit() {
			assert Thread.holdsLock(this);
			resolveSubjectIfNeeded();
			SSLContextInfo info = getServerSSLContextInfo(
				serverSubject, serverPrincipals);
			sslSocketFactory = info.sslContext.getSocketFactory();
			authManager = (ServerAuthManager) info.authManager;
		}

		/** Returns the SSLSocketFactory, calling sslInit if needed. */
		final SSLSocketFactory getSSLSocketFactory() {
			if (sslSocketFactory == null) {
				synchronized (this) {
					if (sslSocketFactory == null) sslInit();
				}
			}
			return sslSocketFactory;
		}

		/** Returns the ServerAuthManager, calling sslInit if needed. */
		final ServerAuthManager getAuthManager() {
			if (authManager == null) {
				synchronized (this) {
					if (authManager == null) sslInit();
				}
			}
			return authManager;
		}

		/* inherit javadoc */
		public ListenHandle listen(RequestDispatcher requestDispatcher)
			throws IOException
		{
			if (requestDispatcher == null) {
				throw new NullPointerException("Request dispatcher cannot be null");
			}
			checkCredentials();
			// Eagerly initialise the SSL context while the service subject is
			// active on this thread (via Subject.callAs in the export sequence).
			// This prevents the lazy double-checked initialisation in
			// getSSLSocketFactory() / getAuthManager() from firing later on a
			// subject-neutral infrastructure thread (accept loop, mux reader)
			// where Subject.current() returns null and resolveSubjectIfNeeded()
			// would find no TLS-capable subject, leaving serverSubject null and
			// producing an anonymous SSL context.
			initIfNeeded();
			ServerSocket serverSocket = serverSocketFactory != null
			? serverSocketFactory.createServerSocket(port)
			: new ServerSocket(port);
			return createListenHandle(requestDispatcher, serverSocket);
		}

		/**
		 * Check that the subject has credentials for the principals specified
		 * when the server endpoint was created.
		 */
		private void checkCredentials() throws UnsupportedConstraintException {
			resolveSubjectIfNeeded();
			if (serverSubject == null) return;
		
			checkListenPermissions(false, listenPermissions, port);
			Set principals = serverSubject.getPrincipals();
			/* Keep track of progress; remove entry when check is done */
				boolean nullServerPrincipals = serverPrincipals == null;
			Map<X500Principal,Object> progress 
				= new HashMap<X500Principal,Object>(
					nullServerPrincipals ? 0 : serverPrincipals.size());
				if (!nullServerPrincipals){
					for (X500Principal p : serverPrincipals) {
						if (!principals.contains(p)) {
							throw new UnsupportedConstraintException(
								"Missing principal: " + p);
						}
						progress.put(p, X500Principal.class);
					}
				}
			X500PrivateCredential[] privateCredentials =
				(X500PrivateCredential[]) AccessController.doPrivileged(
					new SubjectCredentials.GetAllPrivateCredentialsAction(serverSubject));
			List certPaths =
			SubjectCredentials.getCertificateChains(serverSubject);
			if (certPaths != null) {
				for (int i = certPaths.size(); --i >= 0; ) {
					CertPath chain = (CertPath) certPaths.get(i);
					X509Certificate firstCert = firstX509Cert(chain);
					X500Principal p = firstCert.getSubjectX500Principal();
					if (progress.containsKey(p)) {
						try {
							checkValidity(chain, null);
						} catch (CertificateException e) {
							progress.put(p, e);
							continue;
						}
						progress.put(p, CertPath.class);
						for (int j = privateCredentials.length; --j >= 0; ) {
							X509Certificate cert =
							privateCredentials[j].getCertificate();
							if (firstCert.equals(cert)) {
								progress.remove(p);
								break;
							}
						}
					}
				}
			}
			if (!progress.isEmpty()) {
				Map.Entry<X500Principal,Object> entry 
					= progress.entrySet().iterator().next();
				X500Principal p = entry.getKey();
				Object result = entry.getValue();
				if (result == X500Principal.class) {
					throw new UnsupportedConstraintException("Missing public credentials: " + p);
				} else if (result == CertPath.class) {
					throw new UnsupportedConstraintException("Missing private credentials: " + p);
				} else {
					throw new UnsupportedConstraintException(
						"Problem with certificates: " + p + "\n" + result,
						(CertificateException) result);
				}
			}
		}

		/**
		 * Creates a listen handle for the specified dispatcher and server
		 * socket.
		 */
		ListenHandle createListenHandle(RequestDispatcher requestDispatcher,
										ServerSocket serverSocket)
															throws IOException
		{
			SslListenHandle listenHandle = new SslListenHandle(requestDispatcher, serverSocket, this);
			logger.log(Level.FINE, "created {0}", listenHandle);
			// Create and start the accept thread after full construction to
			// prevent 'this' escaping before SslListenHandle is initialised.
			listenHandle.acceptThread = listenHandle.createAcceptThread();
			listenHandle.acceptThread.start();
			return listenHandle;
		}

		/** Returns a hash code value for this object. */
		public int hashCode() {
			if (useCurrentSubject) {
				// If the subject has been resolved (by checkPermissions() or
				// listen()), fold its identity into the hash so that two
				// endpoints resolved to DIFFERENT subjects produce different
				// hash codes and therefore land in different listenPool buckets.
				// If the subject is still null (not yet resolved) fall back to
				// port-only hashing — identical to the pre-resolution bucket.
				Subject sub = serverSubject; // volatile read
				return getClass().hashCode()
					^ (sub != null ? System.identityHashCode(sub) : 0)
					^ port
					^ (serverSocketFactory != null
					   ? serverSocketFactory.hashCode() : 0);
			}
			return getClass().hashCode()
				^ System.identityHashCode(serverSubject)
				^ (serverPrincipals == null ? 0 : serverPrincipals.hashCode())
				^ port
				^ (serverSocketFactory != null
				   ? serverSocketFactory.hashCode() : 0);
		}

		/**
		 * Two instances of this class are equal if they have the same actual
		 * class; have server subjects that compare equal using
		 * <code>==</code>; have server principals that are either both
		 * <code>null</code> or compare equal using <code>equals</code>; have
		 * the same port; and have server socket factories that are both null,
		 * or have the same actual class and are equal. Note that the server
		 * host and socket factory are ignored.
		 * <p>
		 * When {@code useCurrentSubject} is {@code true} the subject is resolved
		 * lazily from the ambient security context, so {@code serverSubject} may be
		 * {@code null} on one instance and non-null on another that represents the
		 * same logical endpoint.  In that case only the port and server socket
		 * factory are compared so that {@code BasicExportTable} can correctly
		 * reuse an already-listening socket rather than attempting to bind the
		 * port a second time.
		 */
		public boolean equals(Object object) {
			if (this == object) {
				return true;
			} else if (object == null || getClass() != object.getClass()) {
				return false;
			}
			SslListenEndpoint other = (SslListenEndpoint) object;
			if (useCurrentSubject || other.useCurrentSubject) {
				// When either endpoint uses lazy subject resolution, compare
				// by resolved subjects (if available) rather than ignoring them.
				// checkPermissions() resolves the subject before BasicExportTable
				// calls getBinding(), so both subjects are typically non-null here.
				// If one is still unresolved (null) fall back to port-only comparison
				// to preserve the pre-existing same-service-reuse behaviour.
				Subject thisSub  = serverSubject;        // volatile reads
				Subject otherSub = other.serverSubject;
				if (thisSub != null && otherSub != null) {
					// Both resolved: require same subject identity, same port,
					// same socket factory.  This prevents two services running in
					// the same group JVM (e.g. SharedGroup CN=Phoenix and Mahalo
					// CN=Mahalo) from sharing the same SslListenHandle.
					return thisSub == otherSub
						&& port == other.port
						&& Util.sameClassAndEquals(serverSocketFactory,
									   other.serverSocketFactory);
				}
				// At least one unresolved — fall back to port + factory only
				// (legacy behaviour, avoids double-binding port 0 for the same
				// service exported twice before subjects are resolved).
				return port == other.port
					&& Util.sameClassAndEquals(serverSocketFactory,
								   other.serverSocketFactory);
			}
			return serverSubject == other.serverSubject
				&& safeEquals(serverPrincipals, other.serverPrincipals)
				&& port == other.port
				&& Util.sameClassAndEquals(serverSocketFactory,
							   other.serverSocketFactory);
		}

		/**
		 * @return the serverConnectionManager
		 */
		synchronized ServerConnManager getServerConnectionManager() {
			return serverConnectionManager;
		}

		/**
		 * @param serverConnectionManager the serverConnectionManager to set
		 */
		synchronized void setServerConnectionManager(ServerConnManager serverConnectionManager) {
			this.serverConnectionManager = serverConnectionManager;
		}

	}// end SslListenEndpoint

    /** Implements ListenHandle */
    static class SslListenHandle extends Utilities implements ListenHandle {
        
        private final SslListenEndpoint listenEndpoint;

		/** The request handler */
		private final RequestDispatcher requestDispatcher;

		/** The server socket used to accept connections */
		final ServerSocket serverSocket;

		/** The security context at the time of the listen. */
		private final SecurityContext securityContext;

		/** Whether the listen handle has been closed. */
		private boolean closed = false;

		/** Set of connections created by this listen handle */
		private final Set connections = new HashSet();

		/** Used to throttle accept failures */
		private final Object failureLock = new Object();
		private long acceptFailureTime = 0;
		private int acceptFailureCount = 0;

		private Thread acceptThread;

		/** Creates a listen handle */
		SslListenHandle(RequestDispatcher requestDispatcher,
							ServerSocket serverSocket,
							SslListenEndpoint listenEndpoint)
			throws IOException
		{
			this.requestDispatcher = requestDispatcher;
			this.serverSocket = serverSocket;
				this.listenEndpoint = listenEndpoint;
			securityContext = Security.getContext();
			// acceptThread is initialised after construction via initAcceptThread()
			// to prevent 'this' escaping before the constructor completes.
			this.acceptThread = null;
		}

		/**
		 * Creates the accept thread.  Must be called after the constructor
		 * returns so that {@code this} is fully constructed before the method
		 * reference {@code this::acceptLoop} is captured.
		 */
		Thread createAcceptThread() {
			return AccessController.doPrivileged((PrivilegedAction<Thread>) () ->
				Subject.callAs(null, () ->
					acceptThreadBuilder.name(toString()).unstarted(this::acceptLoop)
				)
			);
		}

		/** Handles new socket connections. */
		final void acceptLoop() {
			while (true) {
				Socket socket = null;
				Throwable exception;
				SslServerConnection connection = null;
				try {
					socket = serverSocket.accept();

					/* Send data without delay */
					try {
						socket.setTcpNoDelay(true);
					} catch (SocketException e) {
						/* Ignore and continue; the connection will still work, just with worse latency. */
					}
					/*
					 * Send periodic pings so we can tell if the connection
					 * dies.
					 */
					try {
						socket.setKeepAlive(true);
					} catch (SocketException e) {
						/* Ignore and continue; the connection will still work, just without keep-alive. */
					}

					connection = serverConnection(socket);
					synchronized (this) {
						if (closed) {
							try {
								connection.closeInternal(false /* removeFromListener */);
							} catch (IOException e) {
								/* Ignore and continue; we're already closed. */
							}
							break;
						}
						connections.add(connection);
					}
					final SslServerConnection finalConnection = connection;
					AccessController.doPrivileged(
					securityContext.wrap(
						new PrivilegedAction() {
							public Object run() {
								handleConnection(
								finalConnection, requestDispatcher);
								return null;
							}
						}), securityContext.getAccessControlContext());
					continue;
				} catch (Exception e) {
					exception = e;
				} catch (Error e) {
					exception = e;
				}
				/* Problem occurred */
				boolean closedSync;
				synchronized (this) {
					closedSync = closed;
				}
				if (!closedSync && logger.isLoggable(Level.INFO)) {
					String msg = "handling connection {0} throws";
					Object arg = connection;
					if (connection == null) {
						msg = "accepting connection on {0} throws";
						arg = this;
					}
					logThrow(logger, Level.INFO,
						 SslListenHandle.class, "acceptLoop",
						 msg, new Object[] { arg }, exception);
				}
				if (socket != null) {
					try {
						socket.close();
					} catch (IOException e) {
						/* Ignore and continue; we're already handling an exception. */
						if (logger.isLoggable(Level.FINE)) {
							logger.log(Level.FINE,
								   "closing socket after accept failure throws",
								   e);
						}
					}
				}
				if (closedSync) {
					break;
				}
				boolean knownFailure =
					(exception instanceof Exception ||
					 exception instanceof OutOfMemoryError ||
					 exception instanceof NoClassDefFoundError);
				if (!(knownFailure && continueAfterAcceptFailure(exception))) {
					try {
						serverSocket.close();
					} catch (IOException e) {
						/* Ignore and continue; we're already handling an exception. */
						if (logger.isLoggable(Level.FINE)) {
							logger.log(Level.FINE,
								   "closing server socket after accept failure throws",
								   e);
						}
					}
					if (!knownFailure) {
						throw (Error) exception;
					} else {
						return;
					}
				}
			}
		}

		/**
		 * Throttles the accept loop after ServerSocket.accept throws
		 * an exception, and decides whether to continue at all.  The
		 * current code is borrowed from the JRMP implementation; it
		 * always continues, but it delays the loop after bursts of
		 * failed accepts.
		 */
		private boolean continueAfterAcceptFailure(Throwable t) {
			/*
			 * If we get a burst of NFAIL failures in NMSEC milliseconds,
			 * then wait for ten seconds.  This is to ensure that individual
			 * failures don't cause hiccups, but sustained failures don't
			 * hog the CPU in futile accept-fail-retry looping.
			 */
			final int NFAIL = 10;
			final int NMSEC = 5000;
			long now = System.currentTimeMillis();
			boolean fail = false;
			synchronized (failureLock){
				if (acceptFailureTime == 0L ||
					(now - acceptFailureTime) > NMSEC)
				{
					// failure time is very old, or this is first failure
					acceptFailureTime = now;
					acceptFailureCount = 0;
				} else {
					// failure window was started recently
					acceptFailureCount++;
					if (acceptFailureCount >= NFAIL) {
						fail = true;
					}
				}
			}
			if (fail) {
				try {
					Thread.sleep(10000);
				} catch (InterruptedException ignore) {
					/* Why are we ignoring the interrupt and not 
					 * restoring the interrupted status?
					 */
					Thread.currentThread().interrupt();
				}
				// no need to reset counter/timer
			}
	    	return true;
		}

		/** Returns a string representation of this object. */
		public String toString() {
			return getClassName(this) + "[" +
			listenEndpoint.serverHost + ":" + getPort() + "]";
		}

		/** Returns a connection for the specified socket. */
		SslServerConnection serverConnection(Socket socket)
			throws IOException
		{
			return new SslServerConnection(this, socket);
		}

		/** Handles a newly accepted server connection.
		 *
		 * <p>Wraps the connection-manager call in
		 * {@code Subject.callAs(listenEndpoint.serverSubject, ...)} so that
		 * {@code Subject.current()} equals the service subject at MuxServer
		 * construction time.  This allows dispatch threads to restore the
		 * service subject via {@code Subject.callAs(serverSubject, ...)} and
		 * make authenticated outbound calls (e.g. codebase lookup) during
		 * argument unmarshal.
		 */
		void handleConnection(SslServerConnection connection,
							  RequestDispatcher requestDispatcher)
		{
			final Subject resolved = listenEndpoint.serverSubject;
			try {
				if (Utilities.isWorkerSubject(resolved)) {
					// The WorkerSubject (the process SPIFFE identity) must NOT be passed
					// to Subject.callAs/doAs (it throws) nor captured by
					// Subject.current().  Its principals are carried in the
					// AccessControlContext transmitted via JERI and used for the
					// AuthenticationPermission check directly; outbound calls made during
					// dispatch read the worker fresh via Subject.processWorker().  So
					// handle the connection without binding a thread-scoped subject.
					listenEndpoint.getServerConnectionManager().handleConnection(
						connection, requestDispatcher);
				} else {
					// An explicitly-supplied (non-WorkerSubject) service Subject: bind it
					// via callAs so its principals are Subject.current() at MuxServer
					// construction, letting dispatch restore it for outbound calls.
					Subject.callAs(resolved, (Callable<Void>) () -> {
						listenEndpoint.getServerConnectionManager().handleConnection(
							connection, requestDispatcher);
						return null;
					});
				}
			} catch (RuntimeException e) {
				throw e;
			} catch (Exception e) {
				throw new RuntimeException(e);
			}
		}

		/** Returns the port on which this handle is listening. */
		private int getPort() {
			return serverSocket.getLocalPort();
		}

		/* inherit javadoc */
		public void close() {
			List<SslServerConnection> toClose;
			synchronized (this) {
			if (closed) {
				return;
			}
			logger.log(Level.FINE, "closing {0}", this);
			closed = true;
			toClose = new ArrayList<SslServerConnection>(connections);
			connections.clear();
			}
			try {
				serverSocket.close();
			} catch (IOException e) {
				/* Ignore and continue; we're already closed. */
				if (logger.isLoggable(Level.FINE)) {
					logger.log(Level.FINE, "closing server socket throws", e);
				}
			}
			for (SslServerConnection connection : toClose) {
				try {
					/*
					 * Call closeInternal because close would call back to
					 * remove the connection; we've already cleared connections.
					 */
					connection.closeInternal(
					false /* removeFromListener */);
				} catch (IOException e) {
					/* Ignore and continue; we're already closed. */
					if (logger.isLoggable(Level.FINE)) {
						logger.log(Level.FINE,"closing connection "+ connection + " throws", e);
					}
				}
			}
		}

		/**
		 * Called when a connection is closed without a call to close on this
		 * listener.
		 */
		synchronized void noteConnectionClosed(
			SslServerConnection connection)
		{
			connections.remove(connection);
		}

		/* inherit javadoc */
		public ListenCookie getCookie() {
			return new SslListenCookie(getPort(), listenEndpoint);
		}
	} // end SslListenHandle

	/** Implements ListenCookie */
	static final class SslListenCookie implements ListenCookie {
		private final int port;
		private final ListenEndpoint listenEndpoint;

		SslListenCookie(int port, ListenEndpoint listenEndpoint) {
			this.port = port;
				this.listenEndpoint = listenEndpoint;
		}

		/** Returns the port on which the associated handle is listening. */
		final int getPort() {
			return port;
		}

		/**
		 * Returns the listen endpoint associated with this listen cookie.
		 */
		final ListenEndpoint getListenEndpoint() {
			return listenEndpoint;
		}
	}// end SslListenCookie

	/** Implements ServerConnection */
	static class SslServerConnection extends Utilities implements ServerConnection {

		/** The listen handle that accepted this connection */
		private final SslListenHandle listenHandle;

		/** The JSSE socket used for communication */
		final SSLSocket sslSocket;
        
		/** The inbound request handle for this connection. */
		private final InboundRequestHandle requestHandle =
			new InboundRequestHandle() { };

		/**
		 * The session for this connection's socket, or null if not retrieved
		 * yet.  Check that the current session matches to prevent new
		 * handshakes.
		 */
		private final SSLSession session;

		/**
		 * The client subject -- depends on session being set.  This instance
		 * is read-only. 
		 */
		private final Subject clientSubject;

		/** The client principal -- depends on session being set. */
		private final X500Principal clientPrincipal;

		/** The server principal -- depends on session being set. */
		private final X500Principal serverPrincipal;

		/**
		 * The authentication permission required for this connection, or null
		 * if the server is anonymous -- depends on session being set.
		 */
		private final AuthenticationPermission authPermission;

		/** The cipher suite -- depends on session being set. */
		private final String cipherSuite;
        
		/** True if the connection has been closed. */
		volatile boolean closed;

		/** Creates a server connection */
		SslServerConnection(SslListenHandle listenHandle, Socket socket)
			throws IOException
		{
			this.listenHandle = listenHandle;
			sslSocket = (SSLSocket) listenHandle.listenEndpoint.getSSLSocketFactory().createSocket(
				socket, socket.getInetAddress().getHostName(), socket.getPort(), true /* autoClose */);
			sslSocket.setEnabledCipherSuites(getSupportedCipherSuites());

			/* Need to put in server mode before requesting client auth. */
			sslSocket.setUseClientMode(false);
			sslSocket.setNeedClientAuth(true);
		    //sslSocket.setWantClientAuth(true);
			try {
				session = sslSocket.getSession();
				sslSocket.setEnableSessionCreation(false);
				cipherSuite = session.getCipherSuite();
				if ("NULL".equals(getKeyExchangeAlgorithm(cipherSuite))) {
					throw new SecurityException("Handshake failed: " + cipherSuite);
				}
				clientSubject = getClientSubject(sslSocket);
				clientPrincipal = clientSubject != null
					? ((X500Principal)
					   clientSubject.getPrincipals().iterator().next())
					: null;
				X509Certificate serverCert =
					listenHandle.listenEndpoint.getAuthManager().getServerCertificate(session);
				if (serverCert == null){
					IOException e = new IOException("Server Certificate cannot be null");
					e.fillInStackTrace();
					throw e;
				}
				serverPrincipal = serverCert.getSubjectX500Principal();
				if (serverPrincipal != null) {
					authPermission = new AuthenticationPermission(
						Collections.singleton(serverPrincipal),
						(clientPrincipal != null
						 ? Collections.singleton(clientPrincipal) : null),
						"accept");
				} else {
					authPermission = null;
				}
			} catch (SecurityException e){
				throw new IOException("Unable to create session", e);
			}
			logger.log(Level.FINE, "created {0}", toString());
		}

		/* inherit javadoc */
		@Override
		public final String toString() {
			StringBuilder sb = new StringBuilder();
			sb.append(getClassName(this)).append("[");
			if (session != null) sb.append(session).append(", ");
			sb.append(listenHandle.listenEndpoint.serverHost).append(":").append(sslSocket.getLocalPort())
				.append("<=").append(sslSocket.getInetAddress().getHostName())
				.append(":").append(sslSocket.getPort()).append("]");
			return sb.toString();
		}

		/* -- Implement ServerConnection -- */

		/* inherit javadoc */
		@Override
		public InputStream getInputStream() throws IOException {
			return sslSocket.getInputStream();
		}

		/* inherit javadoc */
		@Override
		public OutputStream getOutputStream() throws IOException {
			return sslSocket.getOutputStream();
		}

		/* inherit javadoc */
		@Override
		public SocketChannel getChannel() {
			return null;
		}

		/* inherit javadoc */
		@Override
		public InboundRequestHandle processRequestData(InputStream in,
													   OutputStream out)
		{
			if (in == null || out == null) {
				throw new NullPointerException("Arguments cannot be null");
			}
			SecurityException exception;
			try {
				long now = System.currentTimeMillis();
				decacheSession();
				long create = session.getCreationTime();
				long expiration = create + listenHandle.listenEndpoint.maxServerSessionDuration;
				/* Check for rollover */
				if (expiration < create) {
					expiration = Long.MAX_VALUE;
				}
				if (expiration < now) {
					/*
					 * The session has expired.  Invalidate the session so that
					 * it is not reused, and throw an exception.
					 */
					session.invalidate();
					throw new SecurityException("Session has expired");
				}
				if (serverPrincipal != null) {
					listenHandle.listenEndpoint.getAuthManager().checkCredentials(session, clientSubject);
				}
				return requestHandle;
			} catch (SecurityException e) {
				exception = e;
			} catch (GeneralSecurityException e) {
				exception = new SecurityException(e.getMessage());
				exception.initCause(e);
			}
			try {
				out.close();
			} catch (IOException e2) {
			}
			if (logger.isLoggable(Levels.FAILED)) {
				logThrow(logger, Levels.FAILED,
					 SslServerConnection.class, "processRequestData",
					 "process request data for session {0}\nclient {1}\n" +
					 "throws",
					 new Object[] { session, subjectString(clientSubject) },
					 exception);
			}
			throw exception;
		}

		/**
		 * Make sure the cached session is up to date, and set session-related
		 * fields if needed.
		 */
		private void decacheSession() {
			SSLSession socketSession = sslSocket.getSession();
			if (session == socketSession) {
				return;
			} else if ( !session.isValid()){
				throw new SecurityException("Session invalid");
			} else {
				/*
				 * We disable session creation as soon as we notice the
				 * first session, but it is possible that a second
				 * handshake could have started by then, so check that we
				 * have the same session.  -tjb[31.Jan.2003]
				 */
				throw new SecurityException("New handshake occurred on socket");
			}
		}

		/**
		 * Returns the read-only <code>Subject</code> associated with the
		 * client host connected to the other end of the connection on the
		 * specified <code>SSLSocket</code>.  Returns null if the client is
		 * anonymous.
		 */
		private Subject getClientSubject(SSLSocket socket) {
			SSLSession session = socket.getSession();
			try {
				Certificate[] certificateChain = session.getPeerCertificates();
				if (certificateChain != null
					&& certificateChain.length > 0
					&& certificateChain[0] instanceof X509Certificate)
				{
					/*
					 * The client subject is the authenticated *remote peer* worker
					 * identity, minted from the verified mTLS peer certificate chain as a
					 * read-only RemoteSubject (a WorkerSubject subclass): X.500 DN + SPIFFE
					 * URI principals derived from the leaf, the CertPath credential, and no
					 * private credentials.  Both identity halves come from the cert (never
					 * the wire), and a WorkerSubject can never be callAs/doAs'd -- so the
					 * SPIFFE principal is an additive, authenticated identity used for
					 * Gate-1 ACC stamping (BasicInvocationDispatcher), not transmitted.
					 */
					Subject remote = mintRemoteSubject(certificateChain);
					if (remote != null) {
						return remote;
					}
					/*
					 * Defensive fallback only (JGDMS runs on DirtyChai, where RemoteSubject
					 * is always present): a plain read-only X.500 Subject, no SPIFFE principal.
					 */
					X509Certificate cert = (X509Certificate) certificateChain[0];
					return new Subject(
						true,
						Collections.singleton(cert.getSubjectX500Principal()),
						Collections.singleton(getCertFactory().generateCertPath(Arrays.asList(certificateChain))),
						Collections.EMPTY_SET
					);
				}
			} catch (SSLPeerUnverifiedException e) {
			} catch (CertificateException e) {
				logger.log(Levels.HANDLED,
	       				   "get client subject caught exception", e);
			}
			return null;
		}

		/* inherit javadoc */
		public void checkPermissions(InboundRequestHandle requestHandle) {
			check(requestHandle);
			SecurityManager sm = System.getSecurityManager();
			if (sm != null) {
				try {
					sm.checkAccept(sslSocket.getInetAddress().getHostAddress(), sslSocket.getPort());
					if (authPermission != null) sm.checkPermission(authPermission);
				} catch (SecurityException e) {
					if (logger.isLoggable(Levels.FAILED)) {
						logThrow(logger, Levels.FAILED,
							 SslServerConnection.class, "checkPermissions",
							 "check permissions for {0} throws",
							 new Object[] { this },
							 e);
					}
					throw e;
				}
			}
		}

		/**
		 * Checks that the argument is the request handle for this connection.
		 */
		private void check(InboundRequestHandle requestHandle) {
			if (requestHandle == null) {
				throw new NullPointerException("Request handle cannot be null");
			} else if (requestHandle != this.requestHandle) {
				throw new IllegalArgumentException(
					"Wrong request handle: found " + requestHandle +
					", expected " + this.requestHandle);
			}
		}

		/* inherit javadoc */
		public InvocationConstraints checkConstraints(InboundRequestHandle requestHandle,
													  InvocationConstraints constraints)
			throws UnsupportedConstraintException
		{
			check(requestHandle);
			if (constraints == null) {
				throw new NullPointerException("Constraints cannot be null");
			}
			InvocationConstraints result = getUnfulfilledIntegrityOrAtomicityConstraints(
			cipherSuite, clientPrincipal, serverPrincipal, constraints);
			if (result == null) {
				UnsupportedConstraintException uce = 
	 				new UnsupportedConstraintException(
					"Constraints are not supported: " + constraints);
				if (logger.isLoggable(Levels.FAILED)) {
					logThrow(logger, Levels.FAILED,
						 SslServerConnection.class, "checkConstraints",
						 "check constraints for {0}\nwith {1}\n" +
						 "throws",
						 new Object[] {SslServerConnection.this, constraints},
						 uce);
				}
				throw uce;
			}
			if (logger.isLoggable(Level.FINE)) {
				logger.log(Level.FINE,
				   "check constraints for {0}\nwith {1}\nreturns {2}",
				   new Object[] {
					   SslServerConnection.this, constraints,
					   result
				   });
			}
			return result;
		}

		/* inherit javadoc */
		public void populateContext(InboundRequestHandle requestHandle,
						Collection context)
		{
			check(requestHandle);
			Util.populateContext(context, sslSocket.getInetAddress());
			Util.populateContext(context, clientSubject);
		}

		/* inherit javadoc */
		public void close() throws IOException {
			closeInternal(true);
		}

		/**
		 * Like close, but does not call noteConnectionClosed unless
		 * removeFromListener is true.
		 */
		void closeInternal(boolean removeFromListener)
			throws IOException
		{
			synchronized (this) {
				if (closed) return;
				logger.log(Level.FINE, "closing {0}", this);
				closed = true;
			}
			sslSocket.close();
			if (removeFromListener) listenHandle.noteConnectionClosed(this);
		}
	}
}// end SslServerEndpointImpl
