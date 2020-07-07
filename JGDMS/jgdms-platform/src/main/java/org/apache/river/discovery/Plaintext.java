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

package org.apache.river.discovery;

import java.io.ByteArrayOutputStream;
import java.io.DataInput;
import java.io.DataInputStream;
import java.io.DataOutput;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.io.UTFDataFormatException;
import java.nio.Buffer;
import java.nio.BufferOverflowException;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CoderResult;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Set;
import net.jini.core.constraint.AtomicInputValidation;
import net.jini.core.constraint.ClientAuthentication;
import net.jini.core.constraint.ClientMaxPrincipal;
import net.jini.core.constraint.ClientMaxPrincipalType;
import net.jini.core.constraint.ClientMinPrincipal;
import net.jini.core.constraint.ClientMinPrincipalType;
import net.jini.core.constraint.Confidentiality;
import net.jini.core.constraint.ConstraintAlternatives;
import net.jini.core.constraint.Delegation;
import net.jini.core.constraint.DelegationAbsoluteTime;
import net.jini.core.constraint.DelegationRelativeTime;
import net.jini.core.constraint.Integrity;
import net.jini.core.constraint.InvocationConstraint;
import net.jini.core.constraint.InvocationConstraints;
import net.jini.core.constraint.ServerAuthentication;
import net.jini.core.constraint.ServerMinPrincipal;
import net.jini.core.lookup.ServiceID;
import net.jini.core.lookup.ServiceRegistrar;
import net.jini.io.MarshalledInstance;
import net.jini.io.MarshalInputStream;
import net.jini.io.MarshalOutputStream;
import net.jini.io.UnsupportedConstraintException;
import net.jini.io.context.AtomicValidationEnforcement;
import org.apache.river.api.io.AtomicMarshalInputStream;
import org.apache.river.api.io.AtomicMarshalOutputStream;

/**
 * Provides utility methods for plaintext data operations.
 */
public class Plaintext {

    private static final int MAX_USHORT = 0xFFFF;
    private static final int SHORT_LEN = 2;
    private static final int SERVICE_ID_LEN = 16;

    private static final Charset utf = Charset.forName("UTF-8");
    private static final Set supportedConstraints = new HashSet();
    static {
	supportedConstraints.add(Integrity.NO);
	supportedConstraints.add(Confidentiality.NO);
	supportedConstraints.add(ClientAuthentication.NO);
	supportedConstraints.add(ServerAuthentication.NO);
	supportedConstraints.add(Delegation.NO);
	supportedConstraints.add(AtomicInputValidation.YES);
	supportedConstraints.add(ClientMaxPrincipal.class);
	supportedConstraints.add(ClientMaxPrincipalType.class);
	supportedConstraints.add(ClientMinPrincipal.class);
	supportedConstraints.add(ClientMinPrincipalType.class);
	supportedConstraints.add(ServerMinPrincipal.class);
	supportedConstraints.add(DelegationAbsoluteTime.class);
	supportedConstraints.add(DelegationRelativeTime.class);
    }

    /* Uninstantiable */
    private Plaintext() {
    }

    /**
     * Returns the given integer value as an unsigned short, throwing an
     * IllegalArgumentException if the value is negative or too large.
     */
    public static short intToUshort(int i) {
	if (i < 0 || i > MAX_USHORT) {
	    throw new IllegalArgumentException("invalid value: " + i);
	}
	return (short) i;
    }

    /**
     * Returns an integer with the unsigned value of the given short.
     */
    public static int ushortToInt(short s) {
	return s & MAX_USHORT;
    }

    /**
     * Returns a byte array containing the UTF encoding of the given string.
     */
    public static byte[] toUtf(String s) throws UTFDataFormatException {
	try {
	    ByteArrayOutputStream bout = new ByteArrayOutputStream(s.length());
	    DataOutput dout = new DataOutputStream(bout);
	    dout.writeUTF(s);
	    return bout.toByteArray();
	} catch (UTFDataFormatException e) {
	    throw e;
	} catch (IOException e) {
	    throw new AssertionError(e);
	}
    }

    /**
     * Writes the given string to the provided buffer in UTF format, starting
     * at the buffer's current position and not exceeding its limit.  If the
     * encoding of the given string exceeds the buffer's limit, then a
     * BufferOverflowException is thrown and the position of the buffer is left
     * unchanged from its initial value.
     */
    public static void putUtf(ByteBuffer buf, String s)
	throws UTFDataFormatException
    {
	ByteBuffer dup = buf.duplicate();
	dup.putShort((short) 0);
	int start = dup.position();
	CoderResult cr = 
	    utf.newEncoder().encode(CharBuffer.wrap(s), dup, true);
	if (cr.isUnderflow()) {
	    buf.putShort(intToUshort(dup.position() - start));
	    ((Buffer)buf).position(dup.position());
	} else if (cr.isOverflow()) {
	    throw new BufferOverflowException();
	} else {
	    throw new UTFDataFormatException(cr.toString());
	}
    }

    /**
     * Returns string read from the given buffer in UTF format , starting at
     * the buffer's current position and not exceeding its limit.  If the
     * string's encoding extends past the buffer's limit, then a
     * BufferUnderflowException is thrown and the position of the buffer is
     * left unchanged from its initial value.
     */
    public static String getUtf(ByteBuffer buf) throws UTFDataFormatException {
	ByteBuffer dup = buf.duplicate();
	int len = ushortToInt(dup.getShort());
	if (len > dup.remaining()) {
	    throw new BufferUnderflowException();
	}
	((Buffer)dup).limit(dup.position() + len);
	try {
	    String s = utf.newDecoder().decode(dup).toString();
	    ((Buffer)buf).position(dup.position());
	    return s;
	} catch (CharacterCodingException e) {
	    throw (UTFDataFormatException)
		new UTFDataFormatException().initCause(e);
	}
    }

    /**
     * Returns normally if the given constraints can be satisfied by a
     * plaintext-based format/protocol (such as net.jini.discovery.plaintext,
     * or version 1 of the discovery protocols); otherwise, throws an
     * UnsupportedConstraintException .  Null constraints are considered
     * equivalent to empty constraints.
     */
    public static void checkConstraints(InvocationConstraints constraints)
	throws UnsupportedConstraintException
    {
	if (constraints == null) {
	    return;
	}
	for (Iterator i = constraints.requirements().iterator(); i.hasNext(); )
	{
	    InvocationConstraint c = (InvocationConstraint) i.next();
	    if (!supported(c)) {
		throw new UnsupportedConstraintException(
			      "unsupported constraint: " + c);
	    }
	}
    }

    /**
     * Encodes multicast request according to the net.jini.discovery.plaintext
     * format.
     */
    public static void encodeMulticastRequest(MulticastRequest request,
					      DatagramBufferFactory bufs)
	throws IOException
    {
	try {
	    LinkedList groups = new LinkedList();
	    groups.addAll(Arrays.asList(request.getGroups()));
	    do {
		ByteBuffer buf = bufs.newBuffer();

		// write client host
		putUtf(buf, request.getHost());

		// write client port
		buf.putShort(intToUshort(request.getPort()));

		// write first lookup group
		int ngroups = 0;
		int ngroupsPos = buf.position();
		buf.putShort((short) 0);
		if (!groups.isEmpty()) {
		    putUtf(buf, (String) groups.removeFirst());
		    ngroups++;
		}

		// calculate size of known service ID list
		ServiceID[] ids = request.getServiceIDs();
		int maxIds = Math.min(ids.length, MAX_USHORT);
		int maxIdsLen = SHORT_LEN + maxIds * SERVICE_ID_LEN;

		// write additional lookup groups, as space allows
		if (buf.remaining() > maxIdsLen && !groups.isEmpty()) {
		    int slim = buf.limit();
		    ((Buffer)buf).limit(slim - maxIdsLen);
		    try {
			do {
			    putUtf(buf, (String) groups.getFirst());
			    groups.removeFirst();
			    ngroups++;
			} while (!groups.isEmpty() && ngroups < MAX_USHORT);
		    } catch (BufferOverflowException e) {
		    }
		    ((Buffer)buf).limit(slim);
		}
		buf.putShort(ngroupsPos, intToUshort(ngroups));

		// write known service IDs
		int nids = Math.min(
		    maxIds,
		    (buf.remaining() - SHORT_LEN) / SERVICE_ID_LEN);
		buf.putShort(intToUshort(nids));
		for (int i = 0; i < nids; i++) {
		    ServiceID id = ids[i];
		    buf.putLong(id.getMostSignificantBits());
		    buf.putLong(id.getLeastSignificantBits());
		}
	    } while (!groups.isEmpty());

	} catch (RuntimeException e) {
	    throw new DiscoveryProtocolException(null, e);
	}
    }

    /**
     * Decodes multicast request according to the net.jini.discovery.plaintext
     * format.
     */
    public static MulticastRequest decodeMulticastRequest(ByteBuffer buf)
	throws IOException
    {
	try {
	    // read client host
	    String host = getUtf(buf);

	    // read client port
	    int port = ushortToInt(buf.getShort());

	    // read lookup groups
	    String[] groups = new String[ushortToInt(buf.getShort())];
	    for (int i = 0; i < groups.length; i++) {
		groups[i] = getUtf(buf);
	    }

	    // read known service IDs
	    ServiceID[] ids = new ServiceID[ushortToInt(buf.getShort())];
	    for (int i = 0; i < ids.length; i++) {
		long hi = buf.getLong();
		long lo = buf.getLong();
		ids[i] = new ServiceID(hi, lo);
	    }

	    return new MulticastRequest(host, port, groups, ids);

	} catch (RuntimeException e) {
	    throw new DiscoveryProtocolException(null, e);
	}
    }
    
    /**
     * Encodes multicast announcement according to the
     * net.jini.discovery.plaintext format.
     */
    public static void encodeMulticastAnnouncement(
					    MulticastAnnouncement announcement,
					    DatagramBufferFactory bufs)
	throws IOException
    {
	try {
	    LinkedList groups = new LinkedList();
	    groups.addAll(Arrays.asList(announcement.getGroups()));
	    do {
		ByteBuffer buf = bufs.newBuffer();
		int slim = buf.limit();
		((Buffer)buf).limit(slim - SERVICE_ID_LEN);

		// write sequence number
		buf.putLong(announcement.getSequenceNumber());

		// write LUS host
		putUtf(buf, announcement.getHost());

		// write LUS port
		buf.putShort(intToUshort(announcement.getPort()));

		// write LUS member groups, as space allows
		int ngroups = 0;
		int ngroupsPos = buf.position();
		buf.putShort((short) 0);
		try {
		    while (!groups.isEmpty() && ngroups < MAX_USHORT) {
			putUtf(buf, (String) groups.getFirst());
			groups.removeFirst();
			ngroups++;
		    }
		} catch (BufferOverflowException e) {
		    if (ngroups == 0) {
			throw e;
		    }
		}
		buf.putShort(ngroupsPos, intToUshort(ngroups));

		// write LUS service ID
		ServiceID id = announcement.getServiceID();
		((Buffer)buf).limit(slim);
		buf.putLong(id.getMostSignificantBits());
		buf.putLong(id.getLeastSignificantBits());

	    } while (!groups.isEmpty());

	} catch (RuntimeException e) {
	    throw new DiscoveryProtocolException(null, e);
	}
    }
    
    /**
     * Decodes multicast announcement according to the
     * net.jini.discovery.plaintext format.
     */
    public static MulticastAnnouncement decodeMulticastAnnouncement(
							    ByteBuffer buf)
	throws IOException
    {
	try {
	    // read sequence number
	    long seq = buf.getLong();

	    // read LUS host
	    String host = getUtf(buf);

	    // read LUS port
	    int port = ushortToInt(buf.getShort());

	    // read LUS member groups
	    String[] groups = new String[ushortToInt(buf.getShort())];
	    for (int i = 0; i < groups.length; i++) {
		groups[i] = getUtf(buf);
	    }

	    // read LUS service ID
	    long idhi = buf.getLong();
	    long idlo = buf.getLong();
	    
	    return new MulticastAnnouncement(
		seq, host, port, groups, new ServiceID(idhi, idlo));

	} catch (RuntimeException e) {
	    throw new DiscoveryProtocolException(null, e);
	}
    }
    
    /**
     * Writes unicast response according to the net.jini.discovery.plaintext
     * format.
     */
    public static void writeUnicastResponse(OutputStream out,
					    UnicastResponse response,
					    Collection context)
	throws IOException
    {
	try {
	    DataOutput dout = new DataOutputStream(out);

	    // write LUS host
	    dout.writeUTF(response.getHost());

	    // write LUS port
	    dout.writeShort(intToUshort(response.getPort()));

	    // write LUS member groups
	    String[] groups = response.getGroups();
	    dout.writeInt(groups.length);
	    for (int i = 0; i < groups.length; i++) {
		dout.writeUTF(groups[i]);
	    }

	    // write LUS proxy
	    // Note this instance is compatible with ObjectOutputStream,
	    // it doesn't write annotations, it isn't compatible with
	    // MarshalOutputStream
	    MarshalledInstance mi = null;
	    Object registrar = response.getRegistrar();
	    if (context != null){
		for (Object o : context){
		    if (o instanceof AtomicValidationEnforcement &&
			    ((AtomicValidationEnforcement)o).enforced())
		    {
			throw new UnsupportedConstraintException(
				"Constraint not supported: "
				+ AtomicInputValidation.YES
			);
		    }
		}
	    } else { // Avoid NPE.
		context = Collections.EMPTY_SET;
	    }
	    mi = new MarshalledInstance(registrar, context);
	    new ObjectOutputStream(out).writeObject(mi);
	} catch (RuntimeException e) {
	    throw new DiscoveryProtocolException(null, e);
	}
    }
    
     /**
     * Writes unicast response according to the net.jini.discovery.plaintext
     * format, with the exception that the registrar proxy is written using
     * AtomicMarshalOutputStream, compatible with MarshalOutputStream
     * format (codebase annotations are written to the stream).
     * 
     * This implementation doesn't transmit a MarshalledInstance, instead
     * it serializes the proxy directly.
     */
    public static void writeV2UnicastResponse(OutputStream out,
					    UnicastResponse response,
					    Collection context)
	throws IOException
    {
	try {
	    DataOutput dout = new DataOutputStream(out);

	    // write LUS host
	    dout.writeUTF(response.getHost());

	    // write LUS port
	    dout.writeShort(intToUshort(response.getPort()));

	    // write LUS member groups
	    String[] groups = response.getGroups();
	    dout.writeInt(groups.length);
	    for (int i = 0; i < groups.length; i++) {
		dout.writeUTF(groups[i]);
	    }

	    // write LUS proxy
	    // Note this instance is compatible with MarshalOutputStream,
	    // it writes codebase annotations, it isn't compatible with
	    // ObjectOutputStream
	    Object registrar = response.getRegistrar();
	    new AtomicMarshalOutputStream(out, getLoader(registrar), context, true).writeObject(registrar);
	} catch (RuntimeException e) {
	    throw new DiscoveryProtocolException(null, e);
	}
    }
    
    static ClassLoader getLoader(final Object o){
	if (o == null) return null;
	return AccessController.doPrivileged(new PrivilegedAction<ClassLoader>(){

	    public ClassLoader run() {
		return o.getClass().getClassLoader();
	    }
	    
	});
    }

    /**
     * Reads unicast response according to the net.jini.discovery.plaintext
     * format.
     */
    public static UnicastResponse readUnicastResponse(
					    InputStream in,
					    ClassLoader defaultLoader,
					    boolean verifyCodebaseIntegrity,
					    ClassLoader verifierLoader,
					    Collection context)
	throws IOException, ClassNotFoundException
    {
	try {
	    
	    if (context != null){
		for (Object o : context){
		    if (o instanceof AtomicValidationEnforcement &&
			    ((AtomicValidationEnforcement)o).enforced())
		    {
			throw new UnsupportedConstraintException(
				"Unsupported constraint: " 
				+ AtomicInputValidation.YES
			);
		    }
		}
	    }
	    
	    DataInput din = new DataInputStream(in);

	    // read LUS host
	    String host = din.readUTF();

	    // read LUS port
	    int port = din.readUnsignedShort();

	    // read LUS member groups
	    String[] groups = new String[din.readInt()];
	    for (int i = 0; i < groups.length; i++) {
		groups[i] = din.readUTF();
	    }

	    // read LUS proxy
	    MarshalledInstance mi = (MarshalledInstance) 
		new ObjectInputStream(in).readObject();
	    
	    ServiceRegistrar reg = (ServiceRegistrar) mi.get(
		defaultLoader,
		verifyCodebaseIntegrity,
		verifierLoader,
		context);

	    return new UnicastResponse(host, port, groups, reg);

	} catch (RuntimeException e) {
	    throw new DiscoveryProtocolException(null, e);
	}
    }
    
    /**
     * Reads unicast response similar to the net.jini.discovery.plaintext
     * format, but with the exception of not using a MarshalledInstance of
     * the Proxy, but an AtomicMarshalInputStream to directly receive it instead.
     */
    public static UnicastResponse readV2UnicastResponse(
					    InputStream in,
					    ClassLoader defaultLoader,
					    boolean verifyCodebaseIntegrity,
					    ClassLoader verifierLoader,
					    Collection context)
	throws IOException, ClassNotFoundException
    {
	try {
	    DataInput din = new DataInputStream(in);

	    // read LUS host
	    String host = din.readUTF();

	    // read LUS port
	    int port = din.readUnsignedShort();

	    // read LUS member groups
	    String[] groups = new String[din.readInt()];
	    for (int i = 0; i < groups.length; i++) {
		groups[i] = din.readUTF();
	    }

	    // read LUS proxy, defensively checking type.
	    ServiceRegistrar reg = ((AtomicMarshalInputStream)
		    AtomicMarshalInputStream.create(in,
			    defaultLoader,
			    verifyCodebaseIntegrity,
			    verifierLoader,
			    context,
			    false
		    )).readObject(ServiceRegistrar.class);
	    
	    return new UnicastResponse(host, port, groups, reg);

	} catch (RuntimeException e) {
	    throw new DiscoveryProtocolException(null, e);
	}
    }

    private static boolean supported(InvocationConstraint ic) {
	if (ic instanceof ConstraintAlternatives) {
	    ConstraintAlternatives ca = (ConstraintAlternatives) ic;
	    for (Iterator i = ca.elements().iterator(); i.hasNext(); ) {
		if (supported((InvocationConstraint) i.next())) {
		    return true;
		}
	    }
	    return false;
	}
	return supportedConstraints.contains(ic) ||
	       supportedConstraints.contains(ic.getClass());
    }
}
