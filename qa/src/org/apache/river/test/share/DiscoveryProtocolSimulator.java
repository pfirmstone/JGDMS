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

package org.apache.river.test.share;

import org.apache.river.qa.harness.QAConfig;
import org.apache.river.qa.harness.AdminManager;
import org.apache.river.qa.harness.TestException;

import org.apache.river.start.HTTPDStatus;
import org.apache.river.start.ServiceStarter;

import org.apache.river.thread.TaskManager;

import org.apache.river.discovery.ClientSubjectChecker;
import org.apache.river.discovery.Discovery;
import org.apache.river.discovery.DiscoveryConstraints;
import org.apache.river.discovery.DiscoveryProtocolException;
import org.apache.river.discovery.EncodeIterator;
import org.apache.river.discovery.MulticastAnnouncement;
import org.apache.river.discovery.MulticastRequest;
import org.apache.river.discovery.UnicastResponse;

import net.jini.discovery.Constants;
import net.jini.discovery.IncomingMulticastRequest;
import net.jini.discovery.IncomingUnicastRequest;
import net.jini.discovery.OutgoingMulticastAnnouncement;
import net.jini.discovery.OutgoingUnicastResponse;

import net.jini.core.constraint.MethodConstraints;
import net.jini.core.discovery.LookupLocator;
import net.jini.core.event.EventRegistration;
import net.jini.core.event.RemoteEventListener;
import net.jini.core.lookup.ServiceID;
import net.jini.core.lookup.ServiceItem;
import net.jini.core.lookup.ServiceMatches;
import net.jini.core.lookup.ServiceRegistrar;
import net.jini.core.lookup.ServiceRegistration;
import net.jini.core.lookup.ServiceTemplate;

import org.apache.river.test.services.lookupsimulator.LookupSimulatorProxyInterface;

import net.jini.constraint.BasicMethodConstraints;
import net.jini.io.UnsupportedConstraintException;
import net.jini.config.Configuration;
import net.jini.config.NoSuchEntryException;
import net.jini.config.ConfigurationException;
import net.jini.core.constraint.InvocationConstraint;
import net.jini.core.constraint.InvocationConstraints;
import org.apache.river.config.Config;
import net.jini.security.Security;
import net.jini.security.AuthenticationPermission;

import java.lang.reflect.Array;

import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.MulticastSocket;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.net.UnknownHostException;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InterruptedIOException;

import java.nio.ByteBuffer;
import java.nio.BufferUnderflowException;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;

import java.rmi.activation.ActivationGroupDesc;
import java.rmi.activation.ActivationGroupDesc.CommandEnvironment;
import java.rmi.activation.ActivationGroup;
import java.rmi.activation.ActivationGroupID;
import java.rmi.activation.ActivationDesc;
import java.rmi.activation.Activatable;
import java.rmi.activation.ActivationException;
import java.rmi.MarshalledObject;
import java.rmi.RemoteException;

import java.security.Permission;
import java.security.Principal;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Properties;
import java.util.Random;
import java.util.StringTokenizer;

import java.util.logging.Logger;
import java.util.logging.Level;

import javax.security.auth.login.LoginContext;
import javax.security.auth.Subject;
import javax.security.auth.login.LoginException;
import java.security.PrivilegedExceptionAction;
import java.security.PrivilegedActionException;


/**
 * Instances of this class provide general-purpose functions related to the
 * discovery protocol. This utility class is intended to be useful to all
 * categories of tests that wish to simulate the multicast and unicast
 * message exchange between a client or service and a lookup service -- 
 * from the point of view of the lookup service. (For provide the
 * analogous functionality from the point of view of the client,
 * use the <code>net.jini.discovery.LookupDiscovery</code> and the
 * <code>net.jini.discovery.LookupLocatorDiscovery</code> utilities.
 *
 * @see net.jini.core.discovery.LookupLocator
 *
 * @see net.jini.discovery.IncomingMulticastAnnouncement
 * @see net.jini.discovery.IncomingMulticastRequest
 * @see net.jini.discovery.IncomingUnicastRequest
 * @see net.jini.discovery.IncomingUnicastResponse
 *
 * @see net.jini.discovery.OutgoingMulticastAnnouncement
 * @see net.jini.discovery.OutgoingMulticastRequest
 * @see net.jini.discovery.OutgoingUnicastRequest
 * @see net.jini.discovery.OutgoingUnicastResponse
 */
public class DiscoveryProtocolSimulator {

    /** Maximum minMax lease duration for both services and events */
    private static final long MAX_LEASE = 1000L * 60 * 60 * 24 * 365 * 1000;
    /** Maximum minimum renewal interval */
    private static final long MAX_RENEW = 1000L * 60 * 60 * 24 * 365;
    /** Default maximum size of multicast packets to send and receive */
    private static final int DEFAULT_MAX_PACKET_SIZE = 512;
    /** Default time to live value to use for sending multicast packets */
    private static int DEFAULT_MULTICAST_TTL;
    /** Default timeout to set on sockets used for unicast discovery */
    private static final int DEFAULT_SOCKET_TIMEOUT = 1*60*1000;

    private static Logger logger = Logger.getLogger("org.apache.river.qa.harness");

    /** Socket timeout for multicast request receive() */
    private static final int SOCKET_TIMEOUT = 5*60*1000;

    /** Only allow connections from this address */
    private InetAddress thisInetAddress = null;
    /** Current number of multicast announcements sent by this class */
    private int nAnnouncements = 0;
    /** Internet Protocol (IP) addresses of the network interfaces (NICs)
     *  through which multicast packets will be sent.
     */
    private InetAddress[] nicAddresses;

    /** Port for unicast discovery */
    private int unicastPort = 0;
    /** The locator to send */
    private volatile LookupLocator lookupLocator = null;
    /** The member groups to send */
    private String[] memberGroups = {};

    /** Thread to receive/process multicast requests from client or service */
    volatile MulticastThread multicastRequestThread;
    /** Thread to receive/process unicast requests from client or service */
    volatile UnicastThread unicastRequestThread;
    /** Thread to send multicast announcements to from client or service */
    volatile AnnounceThread multicastAnnouncementThread;

    /** Task manager for sending events and discovery responses */
    private final TaskManager taskMgr = new TaskManager(10, 1000 * 15, 1.0f);

    /** Proxy for the "fake" lookup service that is sent */
    private volatile LookupSimulatorProxyInterface lookupProxy;
    /** The service ID to assign to the lookup service that is sent */
    private volatile ServiceID lookupServiceID = null;

    /** Socket timeout for unicast discovery request processing */
    private int unicastTimeout =
	Integer.getInteger("org.apache.river.reggie.unicastTimeout",
			   1000 * 60).intValue();
    /* For synchronization, instead of ReadersWriter locks used by reggie */
    private final Object lockNAnnouncements = new Object();
    private final Object lockLookupLocator  = new Object();
    private final Object lockMemberGroups   = new Object();

    /* new fields taken from the davis reggie */

    /** Network interfaces to use for multicast discovery */
    private volatile NetworkInterface[] multicastInterfaces;
    /** Flag indicating whether network interfaces were explicitly specified */
    private volatile boolean multicastInterfacesSpecified;
    private volatile Discovery protocol2;
    /** Constraints specified for incoming multicast requests */
    private volatile DiscoveryConstraints multicastRequestConstraints;
    /** Constraints specified for outgoing multicast announcements */
    private volatile DiscoveryConstraints multicastAnnouncementConstraints;
    /** Constraints specified for handling unicast discovery */
    private volatile DiscoveryConstraints unicastDiscoveryConstraints;
    /** Client subject checker to apply to incoming multicast requests */
    private volatile ClientSubjectChecker multicastRequestSubjectChecker;
    /** Client subject checker to apply to unicast discovery attempts */
    private volatile ClientSubjectChecker unicastDiscoverySubjectChecker;
    /** Interval to wait in between sending multicast announcements */
    private volatile long multicastAnnouncementInterval = 1000 * 60 * 2;

    
    public DiscoveryProtocolSimulator(QAConfig config, String[] memberGroups, int unicastPort, LookupSimulatorProxyInterface proxy)
                                       throws ActivationException, IOException, TestException
    {
        this.memberGroups = memberGroups;
        this.unicastPort  = unicastPort;
        // start LUS before switching identity to reggie
        lookupProxy = proxy;
	LoginContext context = null;
	Configuration c = config.getConfiguration();
	try {
	    context = (LoginContext) c.getEntry("test",
						"reggieLoginContext", 
						LoginContext.class,
						null);
	} catch (ConfigurationException e) {
	    throw new RuntimeException("Bad configuration", e);
	}
	if (context == null) {
		init(config);
        } else {
	    try {
		Principal reggie = 
		    (Principal) c.getEntry("principal", "reggie", Principal.class);
		// allow the simulator, running as reggie, to authenticate as reggie
		// XXX Should the permission be obtained from the configuration???
		Security.grant(lookupProxy.getClass(), 
			       new Principal[]{reggie}, 
			       new Permission[] {
				   new AuthenticationPermission(
						 Collections.singleton(reggie),
						 Collections.singleton(reggie),
						 "connect")});
		context.login();
	    } catch (LoginException e) {
		throw new RuntimeException("LoginFailed", e);
	    } catch (ConfigurationException e) {
		throw new RuntimeException("Configuration error", e);
	    }
	    try {
		final QAConfig finalConfig = config;
		Subject.doAsPrivileged(context.getSubject(),
			     	new PrivilegedExceptionAction() {
				    public Object run() throws ActivationException, IOException {
					init(finalConfig);
				 	return null;
				    }
				},
				null);
	    } catch (PrivilegedActionException e) {
		Throwable t = e.getException();
		if (t instanceof ActivationException) {
		    throw (ActivationException) t;
		}
		if (t instanceof IOException) {
		    throw (IOException) t;
		}
	    }
	}
    }//end constructor

    public void stopAnnouncements() {
        stopAnnouncements(null);
    }//end stopAnnouncements
    public void stopAnnouncements(String testname) {
        logger.log(Level.FINE, "   stopAnnouncements entered");
        /* terminate all daemons */
        unicastRequestThread.interrupt();
	multicastRequestThread.interrupt();
	multicastAnnouncementThread.interrupt();
        logger.log(Level.FINE, "     interrupted all threads");
	taskMgr.terminate();
        logger.log(Level.FINE, "     terminated task manager");
	try {
            logger.log(Level.FINE, "     unicastRequestThread.join()");
            unicastRequestThread.join();
            logger.log(Level.FINE, "     multicastRequestThread.join()");
            multicastRequestThread.join();
            logger.log(Level.FINE, 
                              "     multicastAnnouncementThread.join()");
            multicastAnnouncementThread.join();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        logger.log(Level.FINE, "     close all request sockets");
        closeRequestSockets(taskMgr.getPending());
        logger.log(Level.FINE, "   stopAnnouncements exited");
    }//end stopAnnouncements

//    public void destroyLookup() throws RemoteException {
//        lookupProxy.destroy();
//    }//end destroyLookup

    public int getNAnnouncementsSent() {
        synchronized(lockNAnnouncements) {
            return nAnnouncements;
        }//end sync
    }//end getNAnnouncementsSent

    public ServiceRegistrar getLookupProxy() {
        return lookupProxy;
    }//end getLookupProxy

    public LookupLocator getLookupLocator() {
        synchronized(lockLookupLocator) {
            return lookupLocator;
        }//end sync
    }//end getLookupLocator

    public String[] getMemberGroups() {
        synchronized(lockMemberGroups) {
            return memberGroups; // don't clone, never modified once created
        }//end sync
    }//end getMemberGroups

    public void addMemberGroups(String[] groups) throws RemoteException {
        String[] tmpArray = null;
        /* Change the member groups locally, then replace them remotely */
        synchronized(lockMemberGroups) {
            for (int i=0;i<groups.length;i++) {
                if (indexOf(memberGroups, groups[i]) < 0)
                    memberGroups = (String[])arrayAdd(memberGroups,groups[i]);
            }
            tmpArray = new String[memberGroups.length];
            System.arraycopy(memberGroups,0,tmpArray,0,memberGroups.length);
        }//end sync
        /* Replace the groups remotely - no remote calls in sync block*/
        lookupProxy.setMemberGroups(tmpArray);
        synchronized (multicastAnnouncementThread) {
            multicastAnnouncementThread.notify();
        }//end sync
    }//end addMemberGroups

    public void removeMemberGroups(String[] groups) throws RemoteException {
        String[] tmpArray = null;
        /* Change the member groups locally, then replace them remotely */
        synchronized(lockMemberGroups) {
            for (int i=0;i<groups.length;i++) {
                int j = indexOf(memberGroups, groups[i]);
                if (j >= 0) memberGroups = (String[])arrayDel(memberGroups,j);
            }
            tmpArray = new String[memberGroups.length];
            System.arraycopy(memberGroups,0,tmpArray,0,memberGroups.length);
        }//end sync
        /* Replace the groups remotely - no remote calls in sync block*/
        lookupProxy.setMemberGroups(tmpArray);
        synchronized (multicastAnnouncementThread) {
            multicastAnnouncementThread.notify();
        }//end sync
    }//end removeMemberGroups

    public void setMemberGroups(String[] groups) throws RemoteException {
        String[] tmpArray = null;
        /* Change the member groups locally, then replace them remotely */
        synchronized(lockMemberGroups) {
            memberGroups = (String[])removeDups(groups);
            tmpArray = new String[memberGroups.length];
            System.arraycopy(memberGroups,0,tmpArray,0,memberGroups.length);
        }//end sync
        /* Replace the groups remotely - no remote calls in sync block*/
        lookupProxy.setMemberGroups(memberGroups);
        synchronized (multicastAnnouncementThread) {
            multicastAnnouncementThread.notify();
        }//end sync
    }//end setMemberGroups

    public int getUnicastPort() {
        synchronized(lockLookupLocator) {
            return unicastPort;
        }//end sync
    }//end getUnicastPort

    public void setUnicastPort(int port) throws IOException {
	if (port == unicastPort) return;
        LookupLocator tmpLocator = null;
        synchronized(lockLookupLocator) {
            if(    (    (port == 0)
                     && (unicastRequestThread.port == Constants.discoveryPort))
                || (port == unicastRequestThread.port) )
            {
                unicastPort = port;
                return;
            }
            /* create a UnicastThread that listens on the new port */
            UnicastThread newUnicastRequestThread = new UnicastThread(port);
            /* terminate the current UnicastThread listening on the old port */
            unicastRequestThread.interrupt();
            try {
                unicastRequestThread.join();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            /* start the UnicastThread listening on the new port */
            unicastRequestThread = newUnicastRequestThread;
            unicastRequestThread.start();
            unicastPort = port;
            lookupLocator = QAConfig.getConstrainedLocator(lookupLocator.getHost(),
                                                           unicastRequestThread.port);
            /* Equality (same port&host ==> lookupLocator.equals(tmpLocator) */
            tmpLocator = QAConfig.getConstrainedLocator(lookupLocator.getHost(),
                                                        unicastRequestThread.port);
        }//end sync
        /* Set unicast port remotely - no remote calls in sync block*/
        lookupProxy.setLocator(tmpLocator); // also sets unicastPort
        synchronized (multicastAnnouncementThread) {
            multicastAnnouncementThread.notify();
        }//end sync
    }//end setUnicastPort

    /** Multicast discovery request thread code. */
    private class MulticastThread extends Thread {
	/** Multicast socket to receive packets */
	private final MulticastSocket socket;

	/**
	 * Create a high priority daemon thread.  Set up the socket now
	 * rather than in run, so that we get any exception up front.
	 */
	public MulticastThread() throws IOException {
	    super("multicast request");
	    setDaemon(true);
	    if (multicastInterfaces != null && multicastInterfaces.length == 0)
	    {
		socket = null;
		return;
	    }
	    InetAddress requestAddr = Constants.getRequestAddress();
	    socket = new MulticastSocket(Constants.discoveryPort);
	    socket.setTimeToLive(
		multicastAnnouncementConstraints.getMulticastTimeToLive(
		    DEFAULT_MULTICAST_TTL));
	    if (multicastInterfaces != null) {
		Level failureLogLevel =
		    multicastInterfacesSpecified ? Level.WARNING : Level.FINE;
		for (int i = 0; i < multicastInterfaces.length; i++) {
		    try {
			socket.setNetworkInterface(multicastInterfaces[i]);
			socket.joinGroup(requestAddr);
		    } catch (IOException e) {
			logger.log(
			    failureLogLevel,
			    "exception configuring multicast interface", e);
		    }
		}
	    } else {
		// REMIND: tolerate failure here?
		socket.joinGroup(requestAddr);
	    }
	}

	/** True if thread has been interrupted */
	private volatile boolean interrupted = false;

	/* This is a workaround for Thread.interrupt not working on
	 * MulticastSocket.receive on all platforms.
	 */
	public void interrupt() {
	    interrupted = true;
	    socket.close();
	}

	public boolean isInterrupted() {
	    return interrupted;
	}

	public void run() {
	    if (multicastInterfaces != null && multicastInterfaces.length == 0)
	    {
		return;
	    }
	    byte[] buf = new byte[
		multicastRequestConstraints.getMulticastMaxPacketSize(
		    DEFAULT_MAX_PACKET_SIZE)];
	    DatagramPacket dgram = new DatagramPacket(buf, buf.length);
	    while (!isInterrupted()) {
		try {
		    dgram.setLength(buf.length);
		    try {
			socket.receive(dgram);
		    } catch (NullPointerException e) {
			break; // workaround for bug 4190513
		    }
		    int pv;
		    try {
			pv = ByteBuffer.wrap(dgram.getData(),
					     dgram.getOffset(),
					     dgram.getLength()).getInt();
		    } catch (BufferUnderflowException e) {
			throw new DiscoveryProtocolException(null, e);
		    }
		    multicastRequestConstraints.checkProtocolVersion(pv);

		    MulticastRequest req = 
			getDiscovery(pv).decodeMulticastRequest(
			    dgram,
			    multicastRequestConstraints.
				getUnfulfilledConstraints(),
			    multicastRequestSubjectChecker);
                    synchronized (lockMemberGroups){
                        if ((req.getGroups().length != 0 &&
                             !overlap(memberGroups, req.getGroups())) ||
                            indexOf(req.getServiceIDs(), lookupServiceID) >= 0)
                            continue;
                    }
		    logger.log(Level.FINE, "Received valid multicast for " + lookupLocator);
		    taskMgr.addIfNew(
			new AddressTask(InetAddress.getByName(req.getHost()),
					req.getPort()));
		} catch (InterruptedIOException ignore) {
		    break;
		} catch (DiscoveryProtocolException ignore) {
		    break;
		} catch (Exception e) {
		    if (isInterrupted()) {
			break;
		    }
		    logger.log(Level.FINE,
			       "exception receiving multicast request", e);
		}
	    }
	    socket.close();
	}
    }

    /** Unicast discovery request thread code. */
    private class UnicastThread extends Thread {
	/** Server socket to accepts connections on. */
	private final ServerSocket listen;
	/** Listen port */
	public final int port;

	/**
	 * Create a daemon thread.  Set up the socket now rather than in run,
	 * so that we get any exception up front.
	 */
	public UnicastThread(int port) throws IOException {
	    super("unicast request");
	    setDaemon(true);
            ServerSocket listen = null;
	    if (port == 0) {
		try {
		    listen = new ServerSocket(Constants.discoveryPort);
		} catch (IOException e) {
		    logger.log(Level.FINE,
			       "failed to bind to default port", e);
		}
	    }
	    if (listen == null) {
		listen = new ServerSocket(port);
	    }
	    this.port = listen.getLocalPort();
            this.listen = listen;
	}

	/** True if thread has been interrupted */
	private volatile boolean interrupted = false;

	/* This is a workaround for Thread.interrupt not working on
	 * ServerSocket.accept on all platforms.  ServerSocket.close
	 * can't be used as a workaround, because it also doesn't work
	 * on all platforms.
	 */
	public void interrupt() {
	    interrupted = true;
	    try {
		(new Socket(InetAddress.getLocalHost(), port)).close();
	    } catch (IOException e) {
	    }
	}

	public boolean isInterrupted() {
	    return interrupted;
	}

	public void run() {
	    while (!isInterrupted()) {
		try {
		    Socket socket = listen.accept();
		    if (isInterrupted()) {
			try {
			    socket.close();
			} catch (IOException e) {
			    logger.log(Level.FINE,
				       "exception closing socket", e);
			}
			break;
		    }
		    logger.log(Level.FINE, "Adding socket task for " + lookupLocator);
		    taskMgr.add(new SocketTask(socket));
		} catch (InterruptedIOException e) {
		    break;
		} catch (Exception e) {
		    logger.log(Level.FINE, "exception listening on socket", e);
		}
		/* if we fail in any way, just forget about it */
	    }
	    try {
		listen.close();
	    } catch (IOException e) {
	    }
	}
    }

    /** Multicast discovery announcement thread code. */
    private class AnnounceThread extends Thread {
	/** Multicast socket to send packets on */
	private final MulticastSocket socket;

	private volatile boolean interrupted = false;

	/* This is a workaround for Thread.interrupt not working due
	 * to the logging system sometimes throwing away InterruptedIOException
	 */
	public void interrupt() {
	    interrupted = true;
	    super.interrupt();
	}

	public boolean isInterrupted() {
	    return interrupted;
	}

	/**
	 * Create a daemon thread.  Set up the socket now rather than in run,
	 * so that we get any exception up front.
	 */
	public AnnounceThread() throws IOException {
	    super("discovery announcement");
	    setDaemon(true);
	    if (multicastInterfaces == null || multicastInterfaces.length > 0)
	    {
		socket = new MulticastSocket();
		socket.setTimeToLive(
		    multicastAnnouncementConstraints.getMulticastTimeToLive(
			DEFAULT_MULTICAST_TTL));
	    } else {
		socket = null;
	    }
	}

	public void run() {
	    if (multicastInterfaces != null && multicastInterfaces.length == 0)
	    {
		return;
	    }
	    try {
		while (!isInterrupted()) {
                    synchronized (lockMemberGroups){
                        if (!announce(memberGroups)) break;
                    }
                    synchronized (this){
                        wait(multicastAnnouncementInterval);
                    }
		}
	    } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
	    }
// disable this to allow simulation of disappearance of multicast announcements
//	    if (memberGroups.length > 0) 
//		announce(new String[0]);//send NO_GROUPS just before shutdown
	    socket.close();
	}

	/**
	 * Announce membership in the specified groups, and return false if
	 * interrupted, otherwise return true.
	 */
	private boolean announce(String[] groups) {
	    // REMIND: cache latest announcement to skip re-encoding
	    List packets = new ArrayList();
	    Discovery disco;
	    try {
		disco = getDiscovery(
		    multicastAnnouncementConstraints.chooseProtocolVersion());
	    } catch (DiscoveryProtocolException e) {
		throw new AssertionError(e);
	    }
	    EncodeIterator ei = disco.encodeMulticastAnnouncement(
		new MulticastAnnouncement(System.currentTimeMillis(),
					  lookupLocator.getHost(),
					  lookupLocator.getPort(),
					  groups,
					  lookupServiceID),
		multicastAnnouncementConstraints.getMulticastMaxPacketSize(
					  DEFAULT_MAX_PACKET_SIZE),
		multicastAnnouncementConstraints.getUnfulfilledConstraints());
	    while (ei.hasNext()) {
		try {
		    packets.addAll(Arrays.asList(ei.next()));
		} catch (Exception e) {
		// UnsupportedConstraintException is expected and normal
                    if (! (e instanceof UnsupportedConstraintException)) {
		        logger.log(Level.INFO,
			           "exception encoding multicast announcement", e);
                    }
		}
	    }
	    try {
	        logger.log(Level.FINE, "Sending packets from " + lookupLocator);
		send((DatagramPacket[])
		    packets.toArray(new DatagramPacket[packets.size()]));
		    synchronized(lockNAnnouncements) {
                        nAnnouncements++;
                    }
	    } catch (InterruptedIOException e) {
		return false;
	    } catch (IOException e) {
		logger.log(Level.INFO,
			   "exception sending multicast announcement", e);
	    }
	    return true;
	}

	/**
	 * Attempts to multicast the given packets on each of the configured
	 * network interfaces.
	 */
	private void send(DatagramPacket[] packets)
	    throws InterruptedIOException
	{
	    if (multicastInterfaces != null) {
		Level failureLogLevel =
		    multicastInterfacesSpecified ? Level.WARNING : Level.FINE;
		for (int i = 0; i < multicastInterfaces.length; i++) {
		    try {
			socket.setNetworkInterface(multicastInterfaces[i]);
			send(packets, failureLogLevel);
		    } catch (SocketException e) {
			logger.log(failureLogLevel,
				   "exception setting interface", e);
		    }
		}
	    } else {
		send(packets, Level.WARNING);
	    }
	}

	/**
	 * Attempts to multicast the given packets on the currently set network
	 * interface, logging failures at the specified logging level.
	 */
	private void send(DatagramPacket[] packets, Level failureLogLevel)
	    throws InterruptedIOException
	{
	    for (int i = 0; i < packets.length; i++) {
		try {
		    socket.send(packets[i]);
		} catch (InterruptedIOException e) {
		    throw e;
		} catch (IOException e) {
		    logger.log(failureLogLevel, "exception sending packet", e);
		}
	    }
	}
    }

    /** Returns Discovery instance implementing the given protocol version */
    private Discovery getDiscovery(int version)
	throws DiscoveryProtocolException
    {
	switch (version) {
	    case Discovery.PROTOCOL_VERSION_1:
		return Discovery.getProtocol1();
	    case Discovery.PROTOCOL_VERSION_2:
		return protocol2;
	    default:
		throw new DiscoveryProtocolException(
		    "unsupported protocol version: " + version);
	}
    }

    private final class AddressTask implements TaskManager.Task {

	/** The address */
	public final InetAddress addr;
	/** The port */
	public final int port;

	/** Simple constructor */
	public AddressTask(InetAddress addr, int port) {
	    this.addr = addr;
	    this.port = port;
	}

	public int hashCode() {
	    return addr.hashCode();
	}

	/** Two tasks are equal if they have the same address and port */
	public boolean equals(Object obj) {
	    if (!(obj instanceof AddressTask))
		return false;
	    AddressTask ua = (AddressTask)obj;
	    return addr.equals(ua.addr) && port == ua.port;
	}

	/** Connect and then process a unicast discovery request */
	public void run() {
	    try {
                logger.log(Level.FINE, "Responding to multicast with unicast from " + lookupLocator);
		respond(new Socket(addr, port));
	    } catch (IOException e) {
	    } catch (SecurityException e) {
	    }
	}

	/** No ordering */
	public boolean runAfter(List tasks, int size) {
	    return false;
	}
    }//end class AddressTask

    /** Socket for unicast discovery response. */
    private final class SocketTask implements TaskManager.Task {

	/** The socket */
	public final Socket socket;

	/** Simple constructor */
	public SocketTask(Socket socket) {
	    this.socket = socket;
	}

	/** Process a unicast discovery request */
	public void run() {
	    respond(socket);
	}

	/** No ordering */
	public boolean runAfter(List tasks, int size) {
	    return false;
	}
    }//end class SocketTask

    /** Process a unicast discovery request, and respond. */
    private void respond(Socket socket) {
	try {
	    socket.setSoTimeout(
		unicastDiscoveryConstraints.getUnicastSocketTimeout(
		    DEFAULT_SOCKET_TIMEOUT));

	    int pv = new DataInputStream(socket.getInputStream()).readInt();
	    unicastDiscoveryConstraints.checkProtocolVersion(pv);
	    getDiscovery(pv).handleUnicastDiscovery(
		new UnicastResponse(lookupLocator.getHost(),
				    lookupLocator.getPort(),
				    memberGroups,
				    lookupProxy),
		socket,
		unicastDiscoveryConstraints.getUnfulfilledConstraints(),
		unicastDiscoverySubjectChecker,
		Collections.EMPTY_LIST);
	    logger.log(Level.FINE, "Responded to unicast request for " + lookupLocator);

	} catch (Exception e) {
	    try {
		if (InetAddress.getLocalHost().equals(socket.getInetAddress())) {
		    logger.log(Level.FINE, 
			       "exception handling unicast discovery from "
			       + socket.getInetAddress() + ":" 
			       + socket.getPort(),
			       e);
		} else {
		    logger.log(Level.FINE, 
			       "Ignoring spurious request packet from " 
			       + socket.getInetAddress());
		}
	    } catch (UnknownHostException ue) {
		logger.log(Level.SEVERE, "Unknown host!", ue);
	    }
	} finally {
	    try {
		socket.close();
	    } catch (IOException e) {
		logger.log(Level.FINE, "exception closing socket", e);
	    }
	}
    }

    /** Close any sockets that were sitting in the task queue. */
    private void closeRequestSockets(ArrayList tasks) {
	for (int i = tasks.size(); --i >= 0; ) {
	    Object obj = tasks.get(i);
	    if (obj instanceof SocketTask) {
		try {
		    ((SocketTask)obj).socket.close();
		} catch (IOException e) {
		}
	    }
	}
    }//end closeRequestSockets

    /** Return a new array containing the elements of the given array
     *  plus the given element added to the end.
     */
    private static Object[] arrayAdd(Object[] array, Object elt) {
	int len = array.length;
	Object[] narray =
	    (Object[])Array.newInstance(array.getClass().getComponentType(),
					len + 1);
	System.arraycopy(array, 0, narray, 0, len);
	narray[len] = elt;
	return narray;
    }//end arrayAdd

    /** Return a new array containing all the elements of the given array
     *  except the one at the specified index.
     */
    private static Object[] arrayDel(Object[] array, int i) {
	int len = array.length - 1;
	Object[] narray =
	    (Object[])Array.newInstance(array.getClass().getComponentType(),
					len);
	System.arraycopy(array, 0, narray, 0, i);
	System.arraycopy(array, i + 1, narray, i, len - i);
	return narray;
    }//end ArrayDel

    /** Returns the first index of elt in the array, else -1. */
    private static int indexOf(Object[] array, Object elt) {
	return indexOf(array, array.length, elt);
    }//end indexOf

    /** Returns the first index of elt in the array if < len, else -1. */
    private static int indexOf(Object[] array, int len, Object elt) {
	for (int i = 0; i < len; i++) {
	    if (elt.equals(array[i]))
		return i;
	}
	return -1;
    }//end indexOf

    /** Return true if some object is an element of both arrays */
    private static boolean overlap(Object[] arr1, Object[] arr2) {
	for (int i = arr1.length; --i >= 0; ) {
	    if (indexOf(arr2, arr1[i]) >= 0)
		return true;
	}
	return false;
    }//end overlap

    /** Weed out duplicates. */
    private static Object[] removeDups(Object[] arr) {
	for (int i = arr.length; --i >= 0; ) {
	    if (indexOf(arr, i, arr[i]) >= 0)
		arr = arrayDel(arr, i);
	}
	return arr;
    }//end removeDups

    /**
     * Sends the given packet data on the given <code>MulticastSocket</code>
     * through each of the network interfaces corresponding to elements of
     * the given array of IP addresses. If the given array of IP addresses
     * is <code>null</code> or empty, then the data will be sent through only
     * the default network interface.
     *
     * @param mcSocket   the <code>MulticastSocket</code> on which the data
     *                   will be sent
     * @param packet     <code>DatagramPacket</code> array whose elements are
     *                   the data to send 
     * @param addresses  <code>InetAddress</code> array whose elements
     *                   represent the Internet Protocol (IP) addresses
     *                   corresponding to the network interfaces (NICs)
     *                   through which the multicast data should be sent
     *
     * @throws java.io.InterruptedIOException
     */
    private static void sendPacketByNIC(MulticastSocket mcSocket,
                                        DatagramPacket[] packet,
                                        InetAddress[] addresses)
                                                 throws InterruptedIOException
    {
        if( (addresses != null) && (addresses.length > 0) ) {
            for(int i=0;i<addresses.length;i++) {
                try {
                    mcSocket.setInterface(addresses[i]);
                } catch(SocketException e) {
                    continue;//skip to next interface address
                }
                sendPacket(mcSocket,packet);
            }//end loop
        } else {//use default interface
            sendPacket(mcSocket,packet);
        }//endif
    }//end sendPacketByNIC

    /**
     * Sends the given packet data on the given <code>MulticastSocket</code>
     * through the network interface that is currently set.
     *
     * @param mcSocket the <code>MulticastSocket</code> on which the data
     *                 will be sent
     * @param packet   <code>DatagramPacket</code> array whose elements are 
     *                 the data to send 
     *
     * @throws java.io.InterruptedIOException
     */
    private static void sendPacket(MulticastSocket mcSocket,
                                   DatagramPacket[] packet)
                                                throws InterruptedIOException
    {
        for(int i=0;i<packet.length;i++) {
            try {
                mcSocket.send(packet[i]);
            } catch(InterruptedIOException e) {
                throw e;
            } catch(IOException e) {
            }
        }//end loop
    }//end sendPacket

    /**
     * Retrieves and parses the <code>-Dnet.jini.discovery.interface</code>
     * system property, converting each parsed value to an instance of
     * <code>InetAddress</code>, and returning the results of each conversion
     * in an array.
     *
     * @return <code>InetAddress</code> array in which each element represents
     *         the Internet Protocol (IP) address corresponding to a network
     *         interface.
     *
     * @throws java.net.UnknownHostException
     */
    private static InetAddress[] getNICAddresses() throws UnknownHostException
    {
        String str = null;
	try {
	    str = System.getProperty("net.jini.discovery.interface");
	} catch (SecurityException e) { /* ignore */ }
        if (str == null) return null;
        InetAddress[] addrs = null;
        String delimiter = ",";
        StringTokenizer st = null;
        st = new StringTokenizer(str,delimiter);
        int n = st.countTokens();
        if (n > 0) {
            addrs = new InetAddress[n];
            for(int i=0;((st.hasMoreTokens())&&(i<n));i++) {
                addrs[i] = InetAddress.getByName(st.nextToken());
            }
            return addrs;
        } else {
            return addrs;
        }
    }//end getNICAddresses

    /* Note that the QAConfig is named qaConfig here to avoid cut/paste
     * screwups pulling davis configuration entries into the code, which
     * use the name 'config' for the Configuration object.
     */
    private void init(QAConfig qaConfig)
                                       throws ActivationException, IOException
    {
        String host = System.getProperty("java.rmi.server.hostname");
        if (host == null) {
            host = InetAddress.getLocalHost().getHostName();
        }
        thisInetAddress = InetAddress.getByName(host);
        unicastRequestThread = new UnicastThread(unicastPort);
        synchronized (lockLookupLocator){
            lookupLocator = QAConfig.getConstrainedLocator(host, unicastRequestThread.port);
        }
        /* start an activatable lookup service simulation */
        if (lookupServiceID == null) {
            lookupServiceID = lookupProxy.getServiceID();
        }
        if( (lookupProxy == null) || (lookupServiceID == null) ) {
            throw new ActivationException("failure creating lookup service");
        }
	// the code block was a noop for unicastPort > 0, because 
	// setUnicastPort does nothing if the argument is unicastPort
//          if(unicastPort > 0) {
//              /* Change the locator port for this lookup service. */
//              setUnicastPort(unicastPort);
//          } else {
//              /* Port is already set (randomly). need to set only the locator. */
//              lookupProxy.setLocator(lookupLocator);
//          }//endif
	// change to set unconditionally
        lookupProxy.setLocator(lookupLocator);

	/* add new configration entries from the davis reggie implementation */
	Configuration config = qaConfig.getConfiguration();
	MethodConstraints discoveryConstraints = null;
	try {
	    try {
	        multicastInterfaces = (NetworkInterface[]) config.getEntry(
	    	    "test", "multicastInterfaces", NetworkInterface[].class);
	        multicastInterfacesSpecified = true;
	    } catch (NoSuchEntryException e) {
	        List l = Collections.list(NetworkInterface.getNetworkInterfaces());
	        multicastInterfaces = (NetworkInterface[])
		    l.toArray(new NetworkInterface[l.size()]);
	        multicastInterfacesSpecified = false;
	    }
//	    multicastAnnouncementInterval = Config.getLongEntry(
//	        config, "test", "multicastAnnouncementInterval",
//	        multicastAnnouncementInterval, 1, Long.MAX_VALUE);
	    multicastAnnouncementInterval = 
	        qaConfig.getLongConfigVal("net.jini.discovery.announce", 120000);
	    discoveryConstraints = 
	        (MethodConstraints) config.getEntry(
		    "test", "discoveryConstraints",
		    MethodConstraints.class, null);
	    if (discoveryConstraints == null) {
	        discoveryConstraints = 
		    new BasicMethodConstraints(InvocationConstraints.EMPTY);
	    }
	    try {
	        multicastRequestSubjectChecker =
		    (ClientSubjectChecker) Config.getNonNullEntry(
		        config, "test", "multicastRequestSubjectChecker",
		        ClientSubjectChecker.class);
	    } catch (NoSuchEntryException e) {
	        // leave null
	    }
	    try {
	        unicastDiscoverySubjectChecker =
		    (ClientSubjectChecker) Config.getNonNullEntry(
		        config, "test", "unicastDiscoverySubjectChecker",
		        ClientSubjectChecker.class);
	    } catch (NoSuchEntryException e) {
	        // leave null
	    }
	} catch (ConfigurationException ce) {
	    throw new RuntimeException("Configuration error", ce);
	}
	protocol2 = Discovery.getProtocol2(null);
	multicastRequestConstraints = DiscoveryConstraints.process(
	    discoveryConstraints.getConstraints(
		DiscoveryConstraints.multicastRequestMethod));
	multicastAnnouncementConstraints = DiscoveryConstraints.process(
	    discoveryConstraints.getConstraints(
		DiscoveryConstraints.multicastAnnouncementMethod));
	unicastDiscoveryConstraints = DiscoveryConstraints.process(
	    discoveryConstraints.getConstraints(
		DiscoveryConstraints.unicastDiscoveryMethod));

	try {
            DEFAULT_MULTICAST_TTL = Config.getIntEntry(
	        config, "multicast", "ttl", 1, 0, 15);
	} catch (ConfigurationException ce) {
            DEFAULT_MULTICAST_TTL = 1;
	}

        /* create the discovery-related threads */
        multicastRequestThread = new MulticastThread();
        multicastAnnouncementThread = new AnnounceThread();
        
    }//end init
    
    public void start(){
        /* start the threads */
        unicastRequestThread.start();
        multicastRequestThread.start();
        multicastAnnouncementThread.start();
    }

}//end class DiscoveryProtocolSimulator


