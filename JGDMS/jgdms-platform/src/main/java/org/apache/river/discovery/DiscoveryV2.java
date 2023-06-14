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

import org.apache.river.logging.Levels;
import org.apache.river.resource.Service;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.ref.Reference;
import java.lang.ref.SoftReference;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.Socket;
import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.security.AccessController;
import java.security.MessageDigest;
import java.security.PrivilegedAction;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.jini.core.constraint.InvocationConstraints;
import net.jini.discovery.Constants;
import net.jini.io.UnsupportedConstraintException;
import org.osgi.annotation.bundle.Capability;
import org.osgi.annotation.bundle.Requirement;

/**
 * Class providing methods for implementing discovery protocol version 2.
 */
@Requirement(
	namespace="osgi.extender",
	filter="(osgi.extender=osgi.serviceloader.registrar)")
@Capability(
	namespace="osgi.serviceloader",
	name="org.apache.river.discovery.Discovery")
class DiscoveryV2 extends Discovery {

    private static final byte MULTICAST_ANNOUNCEMENT = 0;
    private static final byte MULTICAST_REQUEST      = 1;
    private static final long NULL_FORMAT_ID = 0;
    private static final int FORMAT_ID_LEN = 8;
    private static final int MULTICAST_HEADER_LEN	 = 4 + 1 + 8;
    private static final int UNICAST_REQUEST_HEADER_LEN	 = 4 + 2;
    private static final int UNICAST_RESPONSE_HEADER_LEN = 4 + 8;

    private static final int MULTICAST_REQUEST_ENCODER	    = 0;
    private static final int MULTICAST_REQUEST_DECODER	    = 1;
    private static final int MULTICAST_ANNOUNCEMENT_ENCODER = 2;
    private static final int MULTICAST_ANNOUNCEMENT_DECODER = 3;
    private static final int UNICAST_DISCOVERY_CLIENT	    = 4;
    private static final int UNICAST_DISCOVERY_SERVER	    = 5;
    private static final int NUM_PROVIDER_TYPES		    = 6;

    private static final Class[] providerTypes;
    static {
	Class[] t = new Class[NUM_PROVIDER_TYPES];
	t[MULTICAST_REQUEST_ENCODER] = MulticastRequestEncoder.class;
	t[MULTICAST_REQUEST_DECODER] = MulticastRequestDecoder.class;
	t[MULTICAST_ANNOUNCEMENT_ENCODER] = MulticastAnnouncementEncoder.class;
	t[MULTICAST_ANNOUNCEMENT_DECODER] = MulticastAnnouncementDecoder.class;
	t[UNICAST_DISCOVERY_CLIENT] = UnicastDiscoveryClient.class;
	t[UNICAST_DISCOVERY_SERVER] = UnicastDiscoveryServer.class;
	providerTypes = t;
    }

    private static final Map<ClassLoader,Reference<DiscoveryV2>> instances 
	    = new WeakHashMap<ClassLoader,Reference<DiscoveryV2>>();
    private static final Logger logger =
	Logger.getLogger(DiscoveryV2.class.getName());

    private final Map<Long,DiscoveryFormatProvider>[] formatIdMaps;

    /**
     * Returns DiscoveryV2 instance which uses providers loaded from the given
     * class loader, or the current context class loader if the given loader is
     * null.
     */
    static DiscoveryV2 getInstance(ClassLoader loader) {
	if (loader == null) loader = getContextClassLoader();
	DiscoveryV2 disco = null;
	Reference<DiscoveryV2> softDisco;
	synchronized (instances) { // Atomic
            softDisco = instances.get(loader);
	    if (softDisco != null) disco = softDisco.get();
	    if (disco == null) {
		disco = new DiscoveryV2(getProviders(loader));
		instances.put(loader, new SoftReference<DiscoveryV2>(disco));
	    }
	}
	if (logger.isLoggable(Level.FINEST)) {
	    logger.log(Level.FINEST, "returning {0} for class loader {1}",
		       new Object[]{ disco, loader });
	}
	return disco;
    }

    /**
     * Returns DiscoveryV2 instance which uses the given providers.
     */
    @SuppressWarnings("unchecked")
    static DiscoveryV2 getInstance(MulticastRequestEncoder[] mre,
				   MulticastRequestDecoder[] mrd,
				   MulticastAnnouncementEncoder[] mae,
				   MulticastAnnouncementDecoder[] mad,
				   UnicastDiscoveryClient[] udc,
				   UnicastDiscoveryServer[] uds)
    {
	List<DiscoveryFormatProvider>[] providers = new List[NUM_PROVIDER_TYPES];
	providers[MULTICAST_REQUEST_ENCODER] = asList(mre);
	providers[MULTICAST_REQUEST_DECODER] = asList(mrd);
	providers[MULTICAST_ANNOUNCEMENT_ENCODER] = asList(mae);
	providers[MULTICAST_ANNOUNCEMENT_DECODER] = asList(mad);
	providers[UNICAST_DISCOVERY_CLIENT] = asList(udc);
	providers[UNICAST_DISCOVERY_SERVER] = asList(uds);
	DiscoveryV2 disco = new DiscoveryV2(providers);
	if (logger.isLoggable(Level.FINEST)) {
	    logger.log(Level.FINEST, "returning {0}", new Object[]{ disco });
	}
	return disco;
    }

    @SuppressWarnings("unchecked")
    private DiscoveryV2(List<DiscoveryFormatProvider>[] providers) {
	formatIdMaps = new Map[NUM_PROVIDER_TYPES];
	for (int i = 0, l = formatIdMaps.length; i < l; i++) {
	    formatIdMaps[i] = makeFormatIdMap(providers[i]);
	}
    }

    @Override
    public EncodeIterator encodeMulticastRequest(
					final MulticastRequest request,
					final int maxPacketSize,
					InvocationConstraints constraints)
    {
	if (maxPacketSize < MIN_MAX_PACKET_SIZE) {
	    throw new IllegalArgumentException("maxPacketSize too small");
	}
	final InvocationConstraints absc = 
	    (constraints != null) ? constraints.makeAbsolute() : null;

	return new EncodeIterator() {

	    private final Iterator<Map.Entry<Long,DiscoveryFormatProvider>> entries = 
		formatIdMaps[MULTICAST_REQUEST_ENCODER].entrySet().iterator();

	    @Override
	    public DatagramPacket[] next() throws IOException {
		// fetch next encoder, format ID
		Map.Entry<Long,DiscoveryFormatProvider> ent = entries.next();
		long fid = ent.getKey().longValue();
		MulticastRequestEncoder mre =
		    (MulticastRequestEncoder) ent.getValue();

		// prepare buffer factory, which writes packet headers
		DatagramBuffers db = new DatagramBuffers(
		    Constants.getRequestAddress(),
		    maxPacketSize,
		    MULTICAST_REQUEST,
		    fid);

		// encode data
		mre.encodeMulticastRequest(request, db, absc);
		if (logger.isLoggable(Level.FINEST)) {
		    logger.log(Level.FINEST, "encoded {0} using {1}, {2}",
			       new Object[]{ request, mre, absc });
		}
		return db.getDatagrams();
	    }

	    @Override
	    public boolean hasNext() {
		return entries.hasNext();
	    }
	};
    }

    @Override
    public MulticastRequest decodeMulticastRequest(
					DatagramPacket packet,
					InvocationConstraints constraints,
					ClientSubjectChecker checker,
                                        boolean delayConstraintCheck)
	throws IOException
    {
	if (constraints != null) {
	    constraints = constraints.makeAbsolute();
	}
	ByteBuffer buf = ByteBuffer.wrap(
	    packet.getData(), packet.getOffset(), packet.getLength()).slice();
	if (buf.remaining() < MULTICAST_HEADER_LEN) {
	    throw new DiscoveryProtocolException("incomplete header");
	}

	// read protocol version
	int pv = buf.getInt();
	if (pv != PROTOCOL_VERSION_2) {
	    throw new DiscoveryProtocolException(
			  "wrong protocol version: " + pv);
	}

	// read packet type
	byte pt = buf.get();
	if (pt != MULTICAST_REQUEST) {
	    throw new DiscoveryProtocolException("wrong packet type: " + pt);
	}

	// read format ID
	long fid = buf.getLong();

	// lookup decoder
	MulticastRequestDecoder mrd = (MulticastRequestDecoder) 
	    formatIdMaps[MULTICAST_REQUEST_DECODER].get(Long.valueOf(fid));
	if (mrd == null) {
	    throw new DiscoveryProtocolException(
			  "unsupported format ID: " + fid);
	}

	// decode payload
	MulticastRequest req;
	boolean delayed = false;
	if (mrd instanceof DelayedMulticastRequestDecoder) {
            DelayedMulticastRequestDecoder dmrd =
                (DelayedMulticastRequestDecoder) mrd;
            req = dmrd.decodeMulticastRequest(buf, constraints, checker, 
					      delayConstraintCheck);
	    delayed = delayConstraintCheck;
	} else {
            req = mrd.decodeMulticastRequest(buf, constraints, checker);
	}
	if (logger.isLoggable(Level.FINEST)) {
	    if (delayed) {
		logger.log(Level.FINEST, "decoded {0} using {1}, {2}, {3}, " +
		           "full constraints checking has been delayed",
			   new Object[]{ req, mrd, constraints, checker });
	    }
	    else {
		logger.log(Level.FINEST, "decoded {0} using {1}, {2}, {3}",
			   new Object[]{ req, mrd, constraints, checker });
	    }
	}
	return req;
    }

    @Override
    public MulticastRequest decodeMulticastRequest(DatagramPacket packet,
                                        InvocationConstraints constraints,
                                        ClientSubjectChecker checker)
        throws IOException 
    {
	// default behavior is no delayed constraint checking.
        return decodeMulticastRequest(packet, constraints, checker, false);
    }
    
    @Override
    public EncodeIterator encodeMulticastAnnouncement(
				      final MulticastAnnouncement announcement,
				      final int maxPacketSize,
				      InvocationConstraints constraints)
    {
	if (maxPacketSize < MIN_MAX_PACKET_SIZE) {
	    throw new IllegalArgumentException("maxPacketSize too small");
	}
	final InvocationConstraints absc =
	    (constraints != null) ? constraints.makeAbsolute() : null;

	return new EncodeIterator() {

	    private final Iterator<Map.Entry<Long,DiscoveryFormatProvider>> entries = 
		formatIdMaps[
		    MULTICAST_ANNOUNCEMENT_ENCODER].entrySet().iterator();

	    @Override
	    public DatagramPacket[] next() throws IOException {
		// fetch next encoder, format ID
		Map.Entry<Long,DiscoveryFormatProvider> ent = entries.next();
		long fid = ent.getKey().longValue();
		MulticastAnnouncementEncoder mae =
		    (MulticastAnnouncementEncoder) ent.getValue();

		// prepare buffer factory, which writes packet headers
		DatagramBuffers db = new DatagramBuffers(
		    Constants.getAnnouncementAddress(),
		    maxPacketSize,
		    MULTICAST_ANNOUNCEMENT,
		    fid);

		// encode data
		mae.encodeMulticastAnnouncement(announcement, db, absc);
		if (logger.isLoggable(Level.FINEST)) {
		    logger.log(Level.FINEST, "encoded {0} using {1}, {2}",
			       new Object[]{ announcement, mae, absc });
		}
		return db.getDatagrams();
	    }

	    @Override
	    public boolean hasNext() {
		return entries.hasNext();
	    }
	};
    }

    @Override
    public MulticastAnnouncement decodeMulticastAnnouncement(
					DatagramPacket packet,
					InvocationConstraints constraints,
                                        boolean delayConstraintCheck)
	throws IOException
    {
	if (constraints != null) {
	    constraints = constraints.makeAbsolute();
	}
	ByteBuffer buf = ByteBuffer.wrap(
	    packet.getData(), packet.getOffset(), packet.getLength()).slice();
	if (buf.remaining() < MULTICAST_HEADER_LEN) {
	    throw new DiscoveryProtocolException("incomplete header");
	}

	// read protocol version
	int pv = buf.getInt();
	if (pv != PROTOCOL_VERSION_2) {
	    throw new DiscoveryProtocolException(
			  "wrong protocol version: " + pv);
	}

	// read packet type
	byte pt = buf.get();
	if (pt != MULTICAST_ANNOUNCEMENT) {
	    throw new DiscoveryProtocolException("wrong packet type: " + pt);
	}

	// read format ID
	long fid = buf.getLong();

	// lookup decoder
	MulticastAnnouncementDecoder mad = (MulticastAnnouncementDecoder) 
	    formatIdMaps[MULTICAST_ANNOUNCEMENT_DECODER].get(Long.valueOf(fid));
	if (mad == null) {
	    throw new DiscoveryProtocolException(
			  "unsupported format ID: " + fid);
	}
        MulticastAnnouncement ann;
	
	// decode payload
	boolean delayed = false;
	if (mad instanceof DelayedMulticastAnnouncementDecoder) {
            DelayedMulticastAnnouncementDecoder dmad =
		(DelayedMulticastAnnouncementDecoder) mad;
            ann = dmad.decodeMulticastAnnouncement(buf, constraints, 
						   delayConstraintCheck);
	    delayed = delayConstraintCheck;
	} else {
            ann = mad.decodeMulticastAnnouncement(buf, constraints);
        }
	if (logger.isLoggable(Level.FINEST)) {
	    if (delayed) {
		logger.log(Level.FINEST, "decoded {0} using {1}, {2}, " +
		           "full constraints checking has been delayed",
			   new Object[]{ ann, mad, constraints });
	    }
	    else {
		logger.log(Level.FINEST, "decoded {0} using {1}, {2}",
			   new Object[]{ ann, mad, constraints });
	    }
	}
	return ann;
    }

    @Override
    public MulticastAnnouncement decodeMulticastAnnouncement(
					DatagramPacket packet,
					InvocationConstraints constraints)
	throws IOException 
    {
	// default behavior is no delayed constraint checking.
	return decodeMulticastAnnouncement(packet, constraints, false);
    }
    
    @Override
    public UnicastResponse doUnicastDiscovery(
					Socket socket,
					InvocationConstraints constraints,
					ClassLoader defaultLoader,
					ClassLoader verifierLoader,
					Collection context)
	throws IOException, ClassNotFoundException
    {
	final int MAX_FORMATS = 0xFFFF;

	if (constraints != null) {
	    constraints = constraints.makeAbsolute();
	}

	// determine set of acceptable formats to propose
	Map<Long,DiscoveryFormatProvider> udcMap = formatIdMaps[UNICAST_DISCOVERY_CLIENT];
	Set<Long> fids = new LinkedHashSet<Long>();
	Exception ex = null;
	for (Iterator<Map.Entry<Long,DiscoveryFormatProvider>> i = udcMap.entrySet().iterator(); i.hasNext(); ) {
	    Map.Entry<Long,DiscoveryFormatProvider> ent = i.next();
	    UnicastDiscoveryClient udc = 
		(UnicastDiscoveryClient) ent.getValue();
	    try {
		udc.checkUnicastDiscoveryConstraints(constraints);
		fids.add(ent.getKey());
		if (fids.size() >= MAX_FORMATS) {
		    logger.log(Level.WARNING, "truncating format ID list");
		    break;
		}
	    } catch (Exception e) {
		if (e instanceof UnsupportedConstraintException ||
		    e instanceof SecurityException)
		{
		    ex = e;
		    logger.log(Levels.HANDLED,
			       "constraint check failed for " + udc, e);
		} else {
		    throw (RuntimeException) e;
		}
	    }
	}
	if (fids.isEmpty()) {
	    if (ex == null) {
		throw new DiscoveryProtocolException("no supported formats");
	    } else if (ex instanceof UnsupportedConstraintException) {
		throw (UnsupportedConstraintException) ex;
	    } else {
		throw (SecurityException) ex;
	    }
	}

	ByteBuffer outBuf = ByteBuffer.allocate(
	    UNICAST_REQUEST_HEADER_LEN + (FORMAT_ID_LEN * fids.size()));

	// write protocol version
	outBuf.putInt(PROTOCOL_VERSION_2);

	// write proposed format IDs
	outBuf.putShort((short) fids.size());
	for (Iterator<Long> i = fids.iterator(); i.hasNext(); ) {
	    outBuf.putLong( i.next().longValue());
	}

	OutputStream out = socket.getOutputStream();
	out.write(outBuf.array(), outBuf.arrayOffset(), outBuf.position());
	out.flush();

	ByteBuffer inBuf = ByteBuffer.allocate(UNICAST_RESPONSE_HEADER_LEN);
	new DataInputStream(socket.getInputStream()).readFully(
	    inBuf.array(),
	    inBuf.arrayOffset() + inBuf.position(),
	    inBuf.remaining());

	// read protocol version
	int pv = inBuf.getInt();
	if (pv != PROTOCOL_VERSION_2) {
	    throw new DiscoveryProtocolException(
			  "wrong protocol version: " + pv);
	}

	// read selected format ID
	Long fid = Long.valueOf(inBuf.getLong());
	if (fid.longValue() == NULL_FORMAT_ID) {
	    throw new DiscoveryProtocolException("format negotiation failed");
	}
	if (!fids.contains(fid)) {
	    throw new DiscoveryProtocolException(
			  "response format ID not proposed: " + fid);
	}

	// hand off to format provider to receive response data
	UnicastDiscoveryClient udc = (UnicastDiscoveryClient) udcMap.get(fid);
	UnicastResponse resp = udc.doUnicastDiscovery(
		socket, constraints, defaultLoader, verifierLoader, context,
		(ByteBuffer) ((Buffer) outBuf).flip(), (ByteBuffer) ((Buffer) inBuf).flip());
	if (logger.isLoggable(Level.FINEST)) {
	    logger.log(Level.FINEST, "received {0} using {1}, {2}",
		       new Object[]{ resp, udc, constraints });
	}
	return resp;
    }

    @Override
    public void handleUnicastDiscovery(UnicastResponse response,
				       Socket socket,
				       InvocationConstraints constraints,
				       ClientSubjectChecker checker,
				       Collection context)
	throws IOException
    {
	if (constraints != null) {
	    constraints = constraints.makeAbsolute();
	}

	// note: protocol version already consumed

	// read proposed format IDs
	DataInputStream din = new DataInputStream(socket.getInputStream());
	int nfids = din.readUnsignedShort();
	if (nfids < 0) {
	    throw new DiscoveryProtocolException(
			  "invalid format ID count: " + nfids);
	}
	ByteBuffer inBuf = ByteBuffer.allocate(
	    UNICAST_REQUEST_HEADER_LEN + (FORMAT_ID_LEN * nfids));
	inBuf.putInt(PROTOCOL_VERSION_2);
	inBuf.putShort((short) nfids);
	din.readFully(inBuf.array(),
		      inBuf.arrayOffset() + inBuf.position(),
		      inBuf.remaining());

	// select format provider
	UnicastDiscoveryServer uds = null;
	long fid = NULL_FORMAT_ID;
	Map<Long,DiscoveryFormatProvider> udsMap = formatIdMaps[UNICAST_DISCOVERY_SERVER];
	while (inBuf.hasRemaining()) { // Fully reads buffer
	    fid = inBuf.getLong();
	    UnicastDiscoveryServer s = 
		(UnicastDiscoveryServer) udsMap.get(Long.valueOf(fid));
	    if (s != null) {
		try {
		    s.checkUnicastDiscoveryConstraints(constraints);
		    uds = s;
		} catch (Exception e) {
		    logger.log(Levels.HANDLED,
			       "constraint check failed for " + s, e);
		}
	    }
	}

	ByteBuffer outBuf = ByteBuffer.allocate(UNICAST_RESPONSE_HEADER_LEN);

	// write protocol version
	outBuf.putInt(PROTOCOL_VERSION_2);

	// write selected format ID
	outBuf.putLong((uds != null) ? fid : NULL_FORMAT_ID);

	OutputStream out = socket.getOutputStream();
	out.write(outBuf.array(), outBuf.arrayOffset(), outBuf.position());
	out.flush();

	if (uds == null) {
	    throw new DiscoveryProtocolException("format negotiation failed");
	}

	if (logger.isLoggable(Level.FINEST)) {
	    logger.log(Level.FINEST, "handing off to send {0} using format provider {1}, {2}, {3}",
		       new Object[]{ response, uds, constraints, checker });
	}
	// hand off to format provider to send response data
	uds.handleUnicastDiscovery(
	    response, socket, constraints, checker, context,
	    (ByteBuffer) ((Buffer)inBuf).flip(), (ByteBuffer) ((Buffer)outBuf).flip());
	if (logger.isLoggable(Level.FINEST)) {
	    logger.log(Level.FINEST, "sent {0} using {1}, {2}, {3}",
		       new Object[]{ response, uds, constraints, checker });
	}
    }

    @Override
    public String toString() {
	// REMIND: cache string?
	Collection<DiscoveryFormatProvider> l = new LinkedList<DiscoveryFormatProvider>();
	for (int i = 0; i < NUM_PROVIDER_TYPES; i++) {
	    l.addAll(formatIdMaps[i].values());
	}
	return "DiscoveryV2" + l;
    }

    private static ClassLoader getContextClassLoader() {
	return AccessController.doPrivileged(
	    new PrivilegedAction<ClassLoader>() {
		@Override
		public ClassLoader run() {
		    return Thread.currentThread().getContextClassLoader();
		}
	    });
    }

    private static List<DiscoveryFormatProvider>[] getProviders(final ClassLoader ldr) {
	return AccessController.doPrivileged(new PrivilegedAction<List<DiscoveryFormatProvider>[]>() {
	    @Override
	    @SuppressWarnings("unchecked")
	    public List<DiscoveryFormatProvider>[] run() {
		List<DiscoveryFormatProvider>[] providers = new List[NUM_PROVIDER_TYPES];
		for (int i = 0; i < providers.length; i++) {
		    providers[i] = new ArrayList<DiscoveryFormatProvider>();
		}
		Iterator<DiscoveryFormatProvider> iter = Service.providers(
		    DiscoveryFormatProvider.class, ldr);
		while (iter.hasNext()) {
		    DiscoveryFormatProvider obj = iter.next();
		    boolean used = false;
		    for (int i = 0, l = providerTypes.length; i < l; i++) {
			if (providerTypes[i].isInstance(obj)) {
			    providers[i].add(obj);
			    used = true;
			}
		    }
		    if (!used) {
			logger.log(Level.WARNING,
				   "unusable format provider {0}",
				   new Object[]{ obj });
		    }
		}
		return providers;
	    }
	});
    }

    private static Map<Long,DiscoveryFormatProvider> makeFormatIdMap(List<DiscoveryFormatProvider> providers) {
	Map<Long,DiscoveryFormatProvider> map = new LinkedHashMap<Long,DiscoveryFormatProvider>();
	for (Iterator<DiscoveryFormatProvider> i = providers.iterator(); i.hasNext(); ) {
	    DiscoveryFormatProvider p = i.next();
	    Long fid = Long.valueOf(computeFormatID(p.getFormatName()));
	    if (map.keySet().contains(fid)) {
		logger.log(Level.WARNING,
			   "ignoring provider {0} ({1}) with " +
			   "conflicting format ID {2}",
			   new Object[]{ p, p.getFormatName(), fid });
		continue;
	    }
	    map.put(fid, p);
	}
	return map;
    }

    private static long computeFormatID(String format) {
	try {
	    MessageDigest md = MessageDigest.getInstance("SHA-1");
	    byte[] b = md.digest(format.getBytes("UTF-8"));
	    return ((b[7] & 0xFFL) << 0) +
		   ((b[6] & 0xFFL) << 8) +
		   ((b[5] & 0xFFL) << 16) +
		   ((b[4] & 0xFFL) << 24) +
		   ((b[3] & 0xFFL) << 32) +
		   ((b[2] & 0xFFL) << 40) +
		   ((b[1] & 0xFFL) << 48) +
		   ((b[0] & 0xFFL) << 56);
	} catch (Exception e) {
	    throw new AssertionError(e);
	}
    }

    @SuppressWarnings("unchecked")
    private static List<DiscoveryFormatProvider> asList(DiscoveryFormatProvider[] a) {
	return (a != null) ? Arrays.asList(a) 
		: (List<DiscoveryFormatProvider>) Collections.EMPTY_LIST;
    }

    /**
     * Buffer factory passed to multicast request and announcement encoders.
     */
    private static class DatagramBuffers implements DatagramBufferFactory {

	private static final int TRIM_THRESHOLD = 512;

	private final List<DatagramInfo> datagrams = new ArrayList<DatagramInfo>();
	private final InetAddress addr;
	private final int maxPacketSize;
	private final byte packetType;
	private final long formatId;

	DatagramBuffers(InetAddress addr,
			int maxPacketSize,
			byte packetType,
			long formatId)
	{
	    this.addr = addr;
	    this.maxPacketSize = maxPacketSize;
	    this.packetType = packetType;
	    this.formatId = formatId;
	}

	@Override
	public ByteBuffer newBuffer() {
	    DatagramInfo di = new DatagramInfo();
	    datagrams.add(di);
	    return di.getBuffer();
	}

	DatagramPacket[] getDatagrams() {
	    DatagramPacket[] dp = new DatagramPacket[datagrams.size()];
	    for (int i = 0; i < dp.length; i++) {
		dp[i] = ((DatagramInfo) datagrams.get(i)).getDatagram();
	    }
	    return dp;
	}

	private class DatagramInfo {

	    private final DatagramPacket datagram;
	    private final ByteBuffer buf;

	    DatagramInfo() {
		datagram = new DatagramPacket(new byte[maxPacketSize], 0,
					      addr, Constants.discoveryPort);
		buf = ByteBuffer.wrap(datagram.getData());

		// write packet header
		buf.putInt(PROTOCOL_VERSION_2);
		buf.put(packetType);
		buf.putLong(formatId);
	    }

	    ByteBuffer getBuffer() {
		return buf;
	    }

	    DatagramPacket getDatagram() {
		int len = buf.position();
		// trim excess buffer space if too large
		if (buf.remaining() > TRIM_THRESHOLD) {
		    byte[] b = new byte[len];
		    System.arraycopy(datagram.getData(), 0, b, 0, len);
		    datagram.setData(b);
		}
		datagram.setLength(len);
		return datagram;
	    }
	}
    }
}
