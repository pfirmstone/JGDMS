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

package org.apache.river.discovery.internal;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.security.AccessController;
import java.security.Permission;
import java.security.PrivilegedAction;
import java.security.cert.CertPath;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.NoSuchElementException;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.net.SocketFactory;
import net.jini.core.constraint.InvocationConstraints;
import net.jini.io.UnsupportedConstraintException;
import net.jini.io.context.AtomicValidationEnforcement;
import net.jini.jeri.Endpoint;
import net.jini.jeri.OutboundRequest;
import net.jini.jeri.OutboundRequestIterator;
import net.jini.jeri.connection.Connection;
import net.jini.jeri.connection.ConnectionEndpoint;
import net.jini.jeri.connection.OutboundRequestHandle;
import net.jini.loader.DownloadPermission;
import net.jini.security.Security;
import org.apache.river.api.io.DeSerializationPermission;
import org.apache.river.api.security.PermissionGrant;
import org.apache.river.api.security.PermissionGrantBuilder;
import org.apache.river.discovery.UnicastDiscoveryClient;
import org.apache.river.discovery.UnicastResponse;
import org.apache.river.discovery.ssl.sha224.Client;
import org.apache.river.jeri.internal.connection.ConnManager;
import org.apache.river.jeri.internal.connection.ConnManagerFactory;

/**
 * Provides an abstract endpoint-based UnicastDiscoveryClient implementation,
 * which serves as a superclass for client-side providers for the
 * net.jini.discovery.ssl and net.jini.discovery.kerberos unicast discovery
 * formats.
 */
public abstract class EndpointBasedClient
    extends EndpointBasedProvider implements UnicastDiscoveryClient
{
    /**
     * Constructs instance with the given format name and object providing
     * access to non-public endpoint operations.
     */
    protected EndpointBasedClient(String formatName,
				  EndpointInternals endpointInternals)
    {
	super(formatName, endpointInternals);
    }

    // documentation inherited from UnicastDiscoveryClient
    public void checkUnicastDiscoveryConstraints(
					InvocationConstraints constraints)
	throws UnsupportedConstraintException
    {
	if (constraints == null) {
	    constraints = InvocationConstraints.EMPTY;
	}
	ConnectionInfo ci = getConnectionInfo(null, constraints);
	InvocationConstraints unfulfilled = 
		endpointInternals.getUnfulfilledConstraints(ci.handle);
	checkIntegrity(unfulfilled);
	checkAtomicity(unfulfilled);
    }

    // documentation inherited from UnicastDiscoveryClient
    public UnicastResponse doUnicastDiscovery(
					Socket socket,
					InvocationConstraints constraints,
					ClassLoader defaultLoader,
					ClassLoader verifierLoader,
					Collection context,
					ByteBuffer sent,
					ByteBuffer received)
	throws IOException, ClassNotFoundException
    {
	if (socket == null || sent == null || received == null) {
	    throw new NullPointerException();
	}
	if (constraints == null) {
	    constraints = InvocationConstraints.EMPTY;
	}
	ConnectionInfo ci = getConnectionInfo(socket, constraints);
	Connection conn = ci.endpoint.connect(ci.handle);
	try {
	    InvocationConstraints unfulfilled = conn.getUnfulfilledConstraints(ci.handle);
	    boolean integrity = checkIntegrity(unfulfilled);
	    boolean atomicity = checkAtomicity(unfulfilled);
	    if (atomicity){
		AtomicValidationEnforcement enforceAtomic = 
		    new AtomicValidationEnforcement() {

			@Override
			public boolean enforced() {
			    return true;
			}
		    };
		if (context == null || context.isEmpty()){
		    context = new ArrayList();
		    context.add(enforceAtomic);
		    context = Collections.unmodifiableCollection(context);
		} else {
		    context = new ArrayList(context);
		    context.add(enforceAtomic);
		    context = Collections.unmodifiableCollection(context);
		}
	    }
	    OutputStream out =
		new BufferedOutputStream(conn.getOutputStream());
	    conn.writeRequestData(ci.handle, out);
	    out.write(calcHandshakeHash(sent, received));
	    out.flush();

	    InputStream in = new BufferedInputStream(conn.getInputStream());
	    IOException e = conn.readResponseData(ci.handle, in);
	    if (e != null) {
		throw e;
	    }
	    return readUnicastResponse(
		in, defaultLoader, integrity, verifierLoader, context);
	} finally {
	    conn.close();
	}
    }
    
    /**
     * Client providers can override to utilize a different format.
     * @param in
     * @param defaultLoader
     * @param verifyCodebaseIntegrity
     * @param verifierLoader
     * @param context
     * @return
     * @throws IOException
     * @throws ClassNotFoundException 
     */
    protected UnicastResponse readUnicastResponse(
					    InputStream in,
					    ClassLoader defaultLoader,
					    boolean verifyCodebaseIntegrity,
					    ClassLoader verifierLoader,
					    Collection context)
	throws IOException, ClassNotFoundException
    {
	return Plaintext.readUnicastResponse(
	    in, defaultLoader, verifyCodebaseIntegrity, verifierLoader, context);
    }
    
    protected boolean readAnnotationCertsGrantPerm(InputStream in, 
					    boolean verifyCodebaseIntegrity)
	    throws IOException
    {
	DataInputStream din = new DataInputStream(in);
	String classAnnotation = din.readUTF();
	String certFactoryType = din.readUTF();
	String certPathEncoding = din.readUTF();
	short length = din.readShort();
	byte [] encodedCerts = new byte[length];
	din.readFully(encodedCerts);
	PermissionGrantBuilder pgb = PermissionGrantBuilder.newBuilder();
        pgb.context(PermissionGrantBuilder.URI);
	pgb.permissions(
	    new Permission []{
		new DownloadPermission(),
		new DeSerializationPermission("ATOMIC")
	    }
	);
	PermissionGrant grant = null;
	if (length > 0){ // Make grant to CodeSource with URI, with Certificate signers.
	    try {
		CertificateFactory factory = 
		    CertificateFactory.getInstance(certFactoryType);
		CertPath certPath = factory.generateCertPath(
		    new ByteArrayInputStream(encodedCerts), certPathEncoding);
		verifyCodebaseIntegrity = false; // signed jar file will ensure codebase integrity.

		Collection<? extends Certificate> certs = certPath.getCertificates();
		pgb.certificates(certs.toArray(new Certificate[certs.size()]));
                if (!classAnnotation.isEmpty()) { // Make a URI grant.
                    String [] classAnnotations = classAnnotation.split(" ");
                    for (int i = 0, l = classAnnotations.length; i < l ; i++){
                        pgb.uri(formatName);
                    }
                    grant = pgb.build();
                } 
	    } catch (CertificateException ex) {
		Logger.getLogger(Client.class.getName()).log(Level.INFO, "unable to build certficate", ex);
	    }
	} 
	if (grant != null){
	    final PermissionGrant g = grant;
	    AccessController.doPrivileged(new PrivilegedAction(){

		@Override
		public Object run() {
		    Security.grant(g);
		    return null;
		}

	    });
	}
	return verifyCodebaseIntegrity;
    }

    /**
     * Returns an endpoint which uses the given socket factory, if non-null,
     * and may incorporate information (such as the expected server principal)
     * from the given set of constraints, if non-null.  Other parameters of the
     * endpoint, such as the target host and port, are irrelevant from the
     * standpoint of this class and can be chosen arbitrarily.  Throws an
     * UnsupportedConstraintException if the given constraints lack information
     * needed to construct the endpoint.
     */
    protected abstract Endpoint getEndpoint(SocketFactory factory,
					    InvocationConstraints constraints)
	throws UnsupportedConstraintException;

    /**
     * Returns components needed to initiate a connection over the given socket
     * in accordance with the specified constraints.
     */
    private ConnectionInfo getConnectionInfo(Socket socket,
					     InvocationConstraints constraints)
	throws UnsupportedConstraintException
    {
	Endpoint ep = 
	    getEndpoint(new PrearrangedSocketFactory(socket), constraints);
	ConnManagerFactoryImpl factory = new ConnManagerFactoryImpl();
	endpointInternals.setConnManagerFactory(ep, factory);
	endpointInternals.disableSocketConnect(ep);

	OutboundRequestIterator iter = ep.newRequest(constraints);
	ConnectionInfo ci = factory.getConnectionInfo();
	if (ci != null) {
	    return ci;
	}

	/*
	 * Connection manager was never consulted, so constraints must not be
	 * supported.  The resulting endpoint-fabricated outbound request
	 * iterator should throw UnsupportedConstraintException from its next
	 * method.
	 */
	if (iter.hasNext()) {
	    try {
		iter.next();
	    } catch (UnsupportedConstraintException e) {
		throw e;
	    } catch (IOException e) {
	    }
	}
	throw new AssertionError("expected UnsupportedConstraintException");
    }

    /**
     * Components for initiating a connection.
     */
    private static class ConnectionInfo {

	final ConnectionEndpoint endpoint;
	final OutboundRequestHandle handle;

	ConnectionInfo(ConnectionEndpoint endpoint,
		       OutboundRequestHandle handle)
	{
	    this.endpoint = endpoint;
	    this.handle = handle;
	}
    }

    /**
     * Factory which produces connection managers that capture the
     * OutboundRequestHandle and ConnectionEndpoint instances used to initiate
     * connections, and then return empty OutboundRequestIterators.
     */
    private static class ConnManagerFactoryImpl implements ConnManagerFactory {

	private ConnectionInfo connInfo = null;

	ConnManagerFactoryImpl() {
	}

	public ConnManager create(final ConnectionEndpoint endpoint) {
	    if (endpoint == null) {
		throw new NullPointerException();
	    }
	    return new ConnManager() {
		public OutboundRequestIterator newRequest(
						  OutboundRequestHandle handle)
		{
		    if (handle == null) {
			throw new NullPointerException();
		    }
		    connInfo = new ConnectionInfo(endpoint, handle);

		    return new OutboundRequestIterator() {
			public boolean hasNext() {
			    return false;
			}
			public OutboundRequest next() throws IOException {
			    throw new NoSuchElementException();
			}
		    };
		}
	    };
	}

	ConnectionInfo getConnectionInfo() {
	    return connInfo;
	}
    }

    /**
     * Socket factory which returns a prearranged socket.
     */
    private static class PrearrangedSocketFactory extends SocketFactory {

	private final Socket socket;

	PrearrangedSocketFactory(Socket socket) {
	    this.socket = socket;
	}

	public Socket createSocket() throws IOException {
	    return socket;
	}

	public Socket createSocket(String host, int port) throws IOException {
	    return socket;
	}

	public Socket createSocket(InetAddress host, int port)
	    throws IOException
	{
	    return socket;
	}

	public Socket createSocket(String host,
				   int port,
				   InetAddress localHost,
				   int localPort)
	    throws IOException
	{
	    return socket;
	}

	public Socket createSocket(InetAddress host,
				   int port,
				   InetAddress localHost,
				   int localPort)
	    throws IOException
	{
	    return socket;
	}

	public String toString() {
	    return "PrearrangedSocketFactory[" + socket + "]";
	}
    }
}
