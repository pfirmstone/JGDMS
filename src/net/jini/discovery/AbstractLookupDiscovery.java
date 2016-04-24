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

package net.jini.discovery;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.MulticastSocket;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.rmi.RemoteException;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.security.PrivilegedActionException;
import java.security.PrivilegedExceptionAction;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.BlockingDeque;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import net.jini.config.Configuration;
import net.jini.config.ConfigurationException;
import net.jini.config.EmptyConfiguration;
import net.jini.config.NoSuchEntryException;
import net.jini.constraint.BasicMethodConstraints;
import net.jini.core.constraint.InvocationConstraints;
import net.jini.core.constraint.MethodConstraints;
import net.jini.core.discovery.LookupLocator;
import net.jini.core.lookup.ServiceID;
import net.jini.core.lookup.ServiceRegistrar;
import net.jini.io.UnsupportedConstraintException;
import net.jini.security.BasicProxyPreparer;
import net.jini.security.ProxyPreparer;
import net.jini.security.Security;
import net.jini.security.SecurityContext;
import org.apache.river.config.Config;
import org.apache.river.config.LocalHostLookup;
import org.apache.river.discovery.Discovery;
import org.apache.river.discovery.DiscoveryConstraints;
import org.apache.river.discovery.DiscoveryProtocolException;
import org.apache.river.discovery.EncodeIterator;
import org.apache.river.discovery.MulticastAnnouncement;
import org.apache.river.discovery.MulticastRequest;
import org.apache.river.discovery.UnicastResponse;
import org.apache.river.discovery.internal.MultiIPDiscovery;
import org.apache.river.logging.Levels;
import org.apache.river.logging.LogUtil;
import org.apache.river.thread.NamedThreadFactory;
import org.apache.river.thread.WakeupManager;
import org.apache.river.thread.WakeupManager.Ticket;


/**
 * This package private superclass of LocatorDiscovery exists for 
 * safe construction of LocatorDiscovery's state, while retaining 
 * backward compatibility of the API.
 * 
 * See www.securecoding.cert.org TSM01-J and OBJ11-J
 * 
 */
abstract class AbstractLookupDiscovery implements DiscoveryManagement,
                                                  DiscoveryGroupManagement {
     /* Name of this component; used in config entry retrieval and the logger.*/
    private static final String COMPONENT_NAME
                                        = "net.jini.discovery.LookupDiscovery";
    /* Logger used by this utility. */
    private static final Logger logger = Logger.getLogger(COMPONENT_NAME);

    /** Maximum number of concurrent tasks that can be run in any executor
     * created by this class.
     */
    private static final int MAX_N_TASKS = 5;
    /** Default maximum size of multicast packets to send and receive. */
    private static final int DEFAULT_MAX_PACKET_SIZE = 512;
    /** Default time to live value to use for sending multicast packets. */
    private static final int DEFAULT_MULTICAST_TTL = 15;
    /** Default timeout to set on sockets used for unicast discovery. */
    private static final int DEFAULT_SOCKET_TIMEOUT = 1*60*1000;

    /** Flag indicating whether or not this class is still functional. */
    private volatile boolean terminated = false;
    /** Set of listeners to be sent discovered/discarded/changed events.  Access sync on registrars */
    private final List<DiscoveryListener> listeners = new CopyOnWriteArrayList<DiscoveryListener>();
    /** The groups to discover. Empty set -- NO_GROUPS, access synchronised on registrars */
    private final Set<String> groups;
    /** If groups passed to constructor are null -- ALL_GROUPS, writes synchronised on registrars */
    private volatile boolean all_groups;
    /** Map from ServiceID to UnicastResponse. */
    private final Map<ServiceID,UnicastResponse> registrars = new ConcurrentHashMap<ServiceID,UnicastResponse>(11);
    /** 
     * Set that takes one of the following:
     * <p><ul>
     * <li> Socket (discovery from multicast request/response exchange)
     * <li> LookupLocator (discovery from multicast announcement)
     * <li> CheckGroupsMarker (discarded/changed from announcement)
     * <li> CheckReachabilityMarker (announcements stopped, tests reachability)
     * </ul><p>
     * Each element of this set represents a potential (or pending) discovered,
     * discarded, or changed event. Instances of UnicastDiscoveryTask retrieve
     * the next available element from this set and, based on the object type
     * of the element, determines the processing to perform and what event
     * type to send to the registered listeners.
     */
    private final Set pendingDiscoveries = Collections.newSetFromMap(new ConcurrentHashMap());
    /** Thread that handles pending notifications. */
    private final Notifier notifierThread;
    /** Notifications to be sent to listeners.  Synchronised access with lock notify */
    private final BlockingDeque<NotifyTask> pendingNotifies = new LinkedBlockingDeque<NotifyTask>();
    /** ExecutorService for running UnicastDiscoveryTasks and
     *  DecodeAnnouncementTasks.
     */
    private final ExecutorService executor;
    
    /* WakeupManager to delay tasks. */
    private final WakeupManager discoveryWakeupMgr;
    private final boolean isDefaultWakeupMgr;
    /* Outstanding tickets - Access synchronized on 
     * UnicastDiscoveryTask instance, then on tickets.
     * Access only synchronized on tickets from nukeThreads 
     */
    private final List<Ticket> tickets;
    /** Thread that handles incoming multicast announcements. */
    private final AnnouncementListener announceeThread;
    /** Collection that contains instances of the Requestor Thread class,
     *  each of which participate in multicast discovery by periodically
     *  sending out multicast discovery requests for a finite period of time.
     * 
     * Access synchronised.
     */
    private final Collection<Thread> requestors = new LinkedList<Thread>();
    /** Thread that manages incoming multicast responses. Runs only when
     *  there are Requestor threads running.
     * 
     * Writes synchronised on requestors.
     */
    private volatile ResponseListener respondeeThread;
    /** Security context that contains the access control context to restore
     * for callbacks, etc.
     */
    private final SecurityContext securityContext = Security.getContext();
    /** Map from ServiceID to multicast announcement time stamps; used by the
     *  process that monitors multicast announcements from already-discovered
     *  lookup services, and determines when those announcements have stopped.
     * 
     * Access synchronised on registrars.
     */
    private final ConcurrentMap<ServiceID,AnnouncementInfo> regInfo = new ConcurrentHashMap<ServiceID,AnnouncementInfo>(11);
    /** Thread that monitors multicast announcements from already-discovered
     *  lookup services and, upon determining that those announcements have
     *  stopped, queues a reachability test with the UnicastDiscoveryTask
     *  which will ultimately result in the lookup service being discarded
     *  if the reachability test indicates that the lookup service is
     *  actually down.
     */
    private final AnnouncementTimerThread announcementTimerThread;
    /* Preparer for the proxies to the lookup services that are discovered
     * and used by this utility.
     */
    private final ProxyPreparer registrarPreparer;
    /* Utility for participating in version 2 of discovery protocols. */
    private final Discovery protocol2 = Discovery.getProtocol2(null);
    /* Maximum number multicast requests to send when this utility is started
     * for the first time, and whenever the groups to discover are changed.
     */
    private final int multicastRequestMax;
    /* With respect to when this utility is started, as well as when the set
     * of groups to discover is changed, the value of this field represents
     * the number of milliseconds to wait after sending the n-th multicast
     * request, and before sending the (n+1)-st request, where n is less than
     * the value of <code)multicastRequestMax</code>.
     */
    private final long multicastRequestInterval;
    /* With respect to when this utility is started, as well as when the set
     * of groups to discover is changed, the value of this field represents
     * the number of milliseconds to wait after sending the n-th multicast
     * request, where n is equal to the value of 
     * <code)multicastRequestMax</code>.
     */
    private final long finalMulticastRequestInterval;
    /* Name of requesting host to include in multicast request if
     * participating in version 2 of multicast request protocol.
     */
    private final String multicastRequestHost;
    /* Constraints specified for outgoing multicast requests. */
    private final DiscoveryConstraints multicastRequestConstraints;
    /* The network interfaces (NICs) through which multicast packets will
     * be sent.
     * 
     * Effectively immutable array.
     */
    private final NetworkInterface[] nics;
    /* NICs that initially failed are retried after this number of millisecs.*/
    private final int nicRetryInterval;
    /* Controls how often (in milliseconds) this utility examines the
     * multicast announcements from previously discovered lookup services
     * for "liveness".
     */
    private final long multicastAnnouncementInterval;
    /* 
     * Controls how long to wait before responding to multicast
     * announcements
     */
    private final long unicastDelayRange;
    /* Controls how long to wait before sending out multicast requests */
    private final long initialMulticastRequestDelayRange;
    /* 
     * Flag which indicates that initial multicast request thread has been
     * started.
     * 
     * Access synchronized on requestors.
     */
    private boolean initialRequestorStarted = false;
    /* Constraints specified for incoming multicast announcements. */
    private final DiscoveryConstraints multicastAnnouncementConstraints;
    /* Unprocessed constraints specified for unicast discovery. */
    private final InvocationConstraints rawUnicastDiscoveryConstraints;

    /** Constants used to tell the notifierThread the type of event to send */
    private static final int DISCOVERED = 0;
    private static final int DISCARDED  = 1;
    private static final int CHANGED    = 2;

    /** Constants used to indicate the set of network interfaces being used */
    private static final int NICS_USE_ALL  = 0;//use all NICs in the system
    private static final int NICS_USE_SYS  = 1;//use NIC assigned by the system
    private static final int NICS_USE_LIST = 2;//use list of NICs from config
    private static final int NICS_USE_NONE = 3;//multicast disabled
    /** Flag that indicates how the set of network interfaces was configured */
    private final int nicsToUse;
    
    private final Exception thrown;

    /** Data structure containing task data processed by the Notifier Thread */
    private static class NotifyTask {
	/** The set of listeners to notify */
	public final List<DiscoveryListener> listeners;
	/** Map of discovered registrars-to-groups in which each is a member */
	public final Map<ServiceRegistrar,String[]> groupsMap;
	/** The type of event to send: DISCOVERED, DISCARDED, CHANGED */
	public final int eventType;
	public NotifyTask(List<DiscoveryListener> listeners, Map<ServiceRegistrar,String[]> groupsMap, int eventType) {
	    this.listeners = listeners;
	    this.groupsMap = groupsMap;
	    this.eventType = eventType;
	}
    }//end class NotifyTask

    /** Thread that retrieves data structures of type NotifyTask from a
     *  queue and, based on the contents of the data structure, sends the
     *  appropriate event (discovered/discarded/changed) to each registered
     *  listener.
     *  <p>
     *  Only 1 instance of this thread is run.
     */
    private class Notifier extends Thread {
	
	public Notifier() {
	    super("event listener notification");
	    setDaemon(false);
	}//end constructor

	public void run() {
            logger.finest("LookupDiscovery - Notifier thread started");
	    while (!interrupted()) {
		final NotifyTask task;
                try {
                    task = pendingNotifies.takeFirst();
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt(); // restore
                    break;
                }
		    
                /* The call to notify() on the registered listeners is
                 * performed inside a doPrivileged block that restores the
                 * access control context that was in place when this utility
                 * was created.
                 * 
                 * This is done because the notify() method called below 
                 * executes in the client. But the listener object that
                 * defines that notify() method may have been obtained by
                 * the client from some (possibly untrusted) 3rd party. With
                 * respect to 3rd party code executing in the client, it is
                 * not desirable to allow such code to execute with more
                 * priviledges than the client that created this utility.
                 * Therefore, before executing notify() on any of the 
                 * registered listeners, the client's Subject should be
                 * restored, and the listner code should be restricted to
                 * doing nothing more than the client itself is allowed to do.
                 */
		AccessController.doPrivileged
                (securityContext.wrap(new PrivilegedAction() {
		    public Object run() {
                        boolean firstListener = true;
			for (Iterator iter = task.listeners.iterator();
			     iter.hasNext(); )
			{
			    DiscoveryListener l =
                                             (DiscoveryListener)iter.next();
                            /* Always send discovered and discarded events */
                            if(     (task.eventType == CHANGED)
                                && !(l instanceof DiscoveryChangeListener) )
                            {
                                continue;
                            }//endif
			    DiscoveryEvent e =
				new DiscoveryEvent
                                        ( AbstractLookupDiscovery.this,
                                          deepCopy(task.groupsMap) );
                            /* Log the event info about the lookup(s) */
                            if(     firstListener
                                && (logger.isLoggable(Level.FINEST)) )
                            {
                                String eType =
                                       new String[]{"discovered",
                                                    "discarded",
                                                    "changed"}[task.eventType];
                                ServiceRegistrar[] regs = e.getRegistrars();
                                logger.log(Level.FINEST, "{0} event  -- {1} lookup(s)", new Object[]{eType, regs.length});
                                Map groupsMap = e.getGroups();
                                for(int i=0;i<regs.length;i++) {
                                    LookupLocator loc = null;
                                    try {
                                        loc = regs[i].getLocator();
                                    } catch (Throwable ex) { /* ignore */ }
                                    String[] groups = 
                                             (String[])groupsMap.get(regs[i]);
                                    logger.log(Level.FINEST, "    {0} locator  = {1}", new Object[]{eType, loc});
                                    if(groups.length == 0) {
                                        logger.log(Level.FINEST,"    {0}"+" group    "
                                                            +"= NO_GROUPS", eType);
                                    } else {
                                        for(int j=0;j<groups.length;j++) {
                                            logger.log(Level.FINEST, "    {0} group[{1}] = {2}", new Object[]{eType, j, groups[j]});
                                        }//end loop
                                    }//endif(groups.length)
                                }//end loop
                            }//endif(firstListener && isLoggable(Level.FINEST)
                            try {
                        	switch(task.eventType) {
                                    case DISCOVERED:
                                	l.discovered(e);
                                	break;
                                    case DISCARDED:
                                	l.discarded(e);
                                	break;
                                    case CHANGED:
                                	((DiscoveryChangeListener)l).changed(e);
                                	break;
                        	}//end switch(eventType)
                            } catch (Throwable t) {
                                logger.log(Levels.HANDLED, "a discovery listener failed to process a " +
                                	(task.eventType == DISCARDED ? "discard" : task.eventType == DISCOVERED ? "discover" : "changed") + " event", t);
                            }
                            firstListener = false;
			}//end loop
			return null;
		    }//end run
		}),//end PrivilegedAction and wrap
                securityContext.getAccessControlContext());//end doPrivileged
	    }//end loop
	}//end run
    }//end class Notifier

    /** Thread that listens for multicast announcements from lookup services.
     *  <p>
     *  If the announcements are from a lookup service that has not already
     *  been discovered, and if it is determined that the lookup service 
     *  belongs to at least one group of interest, a "pendingDiscovery" is
     *  queued for the UnicastDiscoveryTask to process asynchronously,
     *  completing the discovery process by performing unicast discovery.
     *  <p>
     *  If the announcements are from a lookup service that has already 
     *  been discovered, the lookup service's member groups - as indicated
     *  in the announcements - are analyzed for changes that may result
     *  in either the lookup service being discarded, or in a changed event
     *  being sent.
     *  <p>
     *  Only 1 instance of this thread is run.
     */
    private class AnnouncementListener extends Thread {
	/** Multicast socket for receiving packets */
	private final MulticastSocket sock;
        /* Set of interfaces whose elements also belong to the nics[] array,
         * which encountered failure when setting the interface or joining
         * the desired multicast group, and which will be retried periodically.
         */
        private ArrayList<NetworkInterface> retryNics = null;

	public AnnouncementListener() throws IOException {
	    super("multicast discovery announcement listener");
	    setDaemon(false);
	    sock = new MulticastSocket(Constants.discoveryPort);
            switch(nicsToUse) {
                case NICS_USE_ALL:
                    /* Using all interfaces. Skip (but report) any interfaces
                     * that are "bad" or not configured for multicast.
                     */
                    for(int i=0;i<nics.length;i++) {
                        try {
                            sock.setNetworkInterface(nics[i]);
                            sock.joinGroup(Constants.getAnnouncementAddress());
                        } catch(IOException e) {
                            if(retryNics == null) {
                                retryNics = new ArrayList<NetworkInterface>(nics.length);
                            }//endif
                            retryNics.add(nics[i]);
                            if( logger.isLoggable(Levels.HANDLED) ) {
                                LogRecord logRec = 
                                  new LogRecord(Levels.HANDLED,
						"network interface "
                                                +"is bad or not configured "
                                                +"for multicast: {0}");
                                logRec.setParameters(new Object[]{nics[i]});
                                logRec.setThrown(e);
                                logger.log(logRec);
                            }//endif
                        }
                    }//end loop
                    break;
                case NICS_USE_LIST:
                    /* Using a configured list of specific interfaces. Skip
                     * (but report) any interfaces that are "bad" or not
                     * configured for multicast.
                     */
                    for(int i=0;i<nics.length;i++) {
                        try {
                            sock.setNetworkInterface(nics[i]);
                            sock.joinGroup(Constants.getAnnouncementAddress());
                        } catch(IOException e) {
                            if(retryNics == null) {
                                retryNics = new ArrayList<NetworkInterface>(nics.length);
                            }//endif
                            retryNics.add(nics[i]);
                            if( logger.isLoggable(Level.SEVERE) ) {
                                LogRecord logRec = 
                                  new LogRecord(Level.SEVERE,
                                                "network interface is bad or "
                                                +"not configured for "
                                                +"multicast: {0}");
                                logRec.setParameters(new Object[]{nics[i]});
                                logRec.setThrown(e);
                                logger.log(logRec);
                            }//endif
                        }
                    }//end loop
                    break;
                case NICS_USE_SYS:
                    /* Using the system-dependent default interface. Don't
                     * need to specifically set the interface. If that
                     * interface is "bad" or not configured for multicast,
                     * log it and try again later.
                     */
                    try {
                        sock.joinGroup(Constants.getAnnouncementAddress());
                    } catch(IOException e) {
                        retryNics = new ArrayList<NetworkInterface>(0);
                        if( logger.isLoggable(Level.SEVERE) ) {
                            logger.log(Level.SEVERE, "system default network "
                                       +"interface is bad or not configured "
                                       +"for multicast", e);
                        }//endif
                    }
                    break;
                case NICS_USE_NONE:
                    break;//multicast disabled, do nothing
                default:
                    throw new AssertionError("nicsToUse flag out of range "
                                             +"(0-3): "+nicsToUse);
            }//end switch(nicsToUse)
	}//end constructor

	/** True if thread has been interrupted */
	private volatile boolean interrupted = false;

	/* This is a workaround for Thread.interrupt not working on
	 * MulticastSocket.receive on all platforms.
	 */
	public void interrupt() {
	    interrupted = true;
	    sock.close();
	}//end interrupt

        /** Accessor method that returns the <code>interrupted</code> flag. */
	public boolean isInterrupted() {
	    return interrupted;
	}//end isInterrupted

        /** Convenience method that retries any previously failed interfaces.*/
	private void retryBadNics() {
            if(retryNics == null) return;//no failed NICs to retry
            if( !retryNics.isEmpty() ) {
                String recoveredStr = "network interface has recovered "
                                      +"from previous failure: {0}";
                @SuppressWarnings("unchecked")
                ArrayList<NetworkInterface> tmpList = (ArrayList<NetworkInterface>) retryNics.clone();
                retryNics.clear();
                for(int i=0; i<tmpList.size(); i++) {
                    NetworkInterface nic =tmpList.get(i);
                    try {
                        sock.setNetworkInterface(nic);
                        sock.joinGroup(Constants.getAnnouncementAddress());
                        if(nicsToUse == NICS_USE_LIST) {
                            logger.log(Level.INFO, recoveredStr, nic);
                        } else {
                            logger.log(Level.FINE, recoveredStr, nic);
                        }//endif
                    } catch(IOException e1) {
                        retryNics.add(nic);//put back for another retry later
                    }
                }//end loop
                if(retryNics.isEmpty()) retryNics = null;//future retries off
            } else {//(retryNics.size() == 0) ==> sys default interface
                try {
                    sock.joinGroup(Constants.getAnnouncementAddress());
                    retryNics = null;
                    logger.log(Level.INFO, "system default network "
                                           +"interface has recovered from "
                                           +"previous failure");
                } catch(IOException e1) { }
            }//endif(!retryNics.isEmpty())
	}//end retryBadNics

	public void run() {
            logger.finest("LookupDiscovery - AnnouncementListener thread "
                          +"started");
	    byte[] buf = new byte[
		multicastAnnouncementConstraints.getMulticastMaxPacketSize(
		    DEFAULT_MAX_PACKET_SIZE)];
	    DatagramPacket pkt = new DatagramPacket(buf, buf.length);
            long endTime = System.currentTimeMillis() + nicRetryInterval;
	    while (!isInterrupted()) {
		try {
                    int delta_t = 0;
                    if(retryNics != null) {//bad NICs, retry when time's up
                        delta_t = (int)(endTime - System.currentTimeMillis());
                        if( delta_t <= 0) {
                            retryBadNics();
                            if(retryNics != null) {//still bad, reset timer
                                delta_t = nicRetryInterval;
                                endTime = System.currentTimeMillis() + delta_t;
                            } else {//all NICs recovered, turn off timer
                                delta_t = 0;
                            }//endif
                        }//endif
                    }//endif
                    sock.setSoTimeout(delta_t);
		    pkt.setLength(buf.length);
		    try {
			sock.receive(pkt);
		    } catch (NullPointerException e) {
			break; // workaround for bug 4190513
		    }
		    restoreContextAddTask(new DecodeAnnouncementTask(pkt));

		    buf = new byte[buf.length];
		    pkt = new DatagramPacket(buf, buf.length);

		} catch (SocketTimeoutException e) {//continue/retry bad NICs
		} catch (InterruptedIOException e) {
		    break;
		} catch (Exception e) {//ignore
                    if( isInterrupted() )  break;
                    logger.log(Level.INFO,
                               "exception while listening for multicast "
                               +"announcements",
                               e);
		}
	    }//end loop(!interrupted)
	    sock.close();
            logger.finest("LookupDiscovery - AnnouncementListener thread "
                          +"completed");
	}//end run
    }//end class AnnouncementListener

    /** Thread that listens for multicast responses to the multicast requests
     *  sent out by the Requestor Thread class. Upon receiving a multicast
     *  response, the socket that accepted the connection request associated
     *  with the the multicast response is added to the set of
     *  pendingDiscoveries so that it (the socket) will be used by the
     *  UnicastDiscoveryTask to complete the discovery process asynchronously.
     *  <p>
     *  Only 1 instance of this thread is run.
     */
    private class ResponseListener extends Thread {
	/** Server socket for accepting connections */
	public final ServerSocket serv;
	
	public ResponseListener() throws IOException {
	    super("multicast discovery response listener");
	    setDaemon(false);
	    serv = new ServerSocket(0);
	}//end constructor

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
		(new Socket(LocalHostLookup.getLocalHost(), getPort())).close();
	    } catch (IOException e) { /* ignore */ }
	}//end interrupt

        /** Accessor method that returns the <code>interrupted</code> flag. */
	public boolean isInterrupted() {
	    return interrupted;
	}//end isInterrupt

	public void run() {
            logger.finest("LookupDiscovery - ResponseListener thread started");
	    while (!isInterrupted()) {
		try {
		    Socket sock = serv.accept();
		    if (isInterrupted()) {
			try {
			    sock.close();
			} catch (IOException e) { }
			break;
		    }//end if
                    pendingDiscoveries.add(sock);
                    restoreContextAddTask(new UnicastDiscoveryTask(sock));
		} catch (InterruptedIOException e) {
		    break;
		} catch (Exception e) {//ignore
                    logger.log(Level.INFO,
                               "exception while listening for multicast "
                               +"response",
                               e);
		}
	    }//end loop(!isInterrupted)
	    try {
		serv.close();
	    } catch (IOException e) {//ignore
                logger.log(Levels.HANDLED,
                           "IOException while attempting a socket close",
                           e);
	    }
            logger.finest("LookupDiscovery - ResponseListener thread "
                          +"completed");
	}//end run

	/** Return the local port of the socket */
	public int getPort() {
	    return serv.getLocalPort();
	}//end getPort
    }//end class ResponseListener

    /** Thread that periodically sends out multicast requests for a limited
     *  period of time, and then exits.
     *  <p>
     *  An instance of this thread is run at startup, and each time the
     *  set of groups to discover is changed.
     */
    private class Requestor extends Thread {
	/** Multicast socket for sending packets */
	private final MulticastSocket sock;
	/** Unicast response port */
	private final int responsePort;
	/** Groups to request */
	private final String[] groups;
	private final boolean delayFlag;

	public Requestor(String[] groups, int port, boolean delayFlag)
	    throws IOException
	{
	    super("multicast discovery request");
	    setDaemon(false);
	    sock = new MulticastSocket(Constants.discoveryPort);
	    sock.setTimeToLive(
		multicastRequestConstraints.getMulticastTimeToLive(
		    DEFAULT_MULTICAST_TTL));
	    responsePort = port;
	    this.groups = groups == null ? new String[0] : groups;
	    this.delayFlag = delayFlag;
	}//end constructor

	/** This method sends out N (for small N) multicast requests. Until
         *  the last request is sent out, this method sleeps for 5 seconds
         *  after each request is sent. After the last request is sent,
         *  this method sleeps for 2 minutes to allow the ResponseListener
         *  time to receive and process any multicast responses sent in
         *  reply to the multicast requests. Before sending a request, a
         *  new multicast request is constructed so that updates
         *  can be made to the set of service IDs of the lookup services
         *  discovered due to previous requests. 
         *  <p>
	 *  After all requests have been sent, and the ResponseListener
         *  has been given the appropriate time to receive and process
         *  any multicast responses, if there are no more active instances
         *  of this thread, this method terminates (interrupts) the
         *  ResponseListener. Note that although it is more desirable to
         *  have the ResponseListener set a timeout on the server socket
         *  (using setSoTimeout) and then simply exit after a period of 
         *  time in which both the ResponseListener has been idle, and
         *  there have been no active Requestor threads, using setSoTimeout
         *  in this way can cause random hangs on the Solaris(TM) operating
         *  system.
	 */
	public void run() {
            logger.finest("LookupDiscovery - Requestor thread started");
	    int count; // bug 4084783/4187594
	    try {
		if (delayFlag
		    && (initialMulticastRequestDelayRange > 0)
		    && (multicastRequestMax >= 0))
		{
		    Thread.sleep((long) (Math.random() *
					 initialMulticastRequestDelayRange));
		}
		for (count = multicastRequestMax;
                                          --count >= 0 && !isInterrupted(); )
                {
                    DatagramPacket[] reqs = encodeMulticastRequest
			(new MulticastRequest(multicastRequestHost,
					      responsePort,
					      groups,
					      getServiceIDs()));
                    sendPacketByNIC(sock, reqs);
		    Thread.sleep(count > 0 ?
                      multicastRequestInterval:finalMulticastRequestInterval);
		}//end loop
	    } catch (InterruptedException e) {//terminate gracefully
	    } catch (InterruptedIOException e) {//terminate gracefully
            } catch (Exception e) {
                logger.log(Level.INFO,"exception while marshalling outgoing "
                                      +"multicast request", e);
	    } finally {
		synchronized (requestors) {
		    requestors.remove(Thread.currentThread());
		    if (respondeeThread != null && requestors.isEmpty()) {
			respondeeThread.interrupt();
			respondeeThread = null;
		    }
		}//end sync
		sock.close();
                logger.finest("LookupDiscovery - Requestor thread completed");
	    }//end try/catch/finally
	}//end run
    }//end class Requestor

    /**
     * This thread monitors the multicast announcements sent from the
     * lookup service(s) that have already been discovered by this class,
     * looking for indications that those announcements have terminated.
     * 
     * The data structure used to map the discovered lookup services to
     * the time of arrival of the most recent multicast announcement from
     * each such lookup service is examined at regular intervals; dependent
     * on the system property <code>net.jini.discovery.announce</code>.
     *
     * If the difference between the current time and the last time of
     * arrival for any announcement exceeds a predetermined threshold, the
     * corresponding lookup is polled for its current set of member groups.
     * If that lookup service is unreachable, or if it is reachable but its
     * member groups have been replaced, the lookup service is discarded.
     */
    private class AnnouncementTimerThread extends Thread {
        /* Number of interval to exceed for declaring announcements stopped */
        private static final long N_INTERVALS = 3;
        
        public AnnouncementTimerThread() {
            super("multicast announcement timer");
            setDaemon(false);
        }
        public void run() {
            long timeThreshold = N_INTERVALS*multicastAnnouncementInterval;
            try {
                while(!isInterrupted()) {
                    sleep(multicastAnnouncementInterval);
                    long curTime = System.currentTimeMillis();
                    /* Previously regInfo was cloned to avoid synchronizing
                     * this was changed to a simple synchronized block during code
                     * auditing as it appeared like an unnecessary 
                     * performance optimisation that risked atomicity.
                     */
                    Set<Map.Entry<ServiceID,AnnouncementInfo>> eSet = regInfo.entrySet();
                    for(Iterator<Map.Entry<ServiceID,AnnouncementInfo>> itr = eSet.iterator(); itr.hasNext(); ) {
                        Map.Entry<ServiceID,AnnouncementInfo> pair   = itr.next();
                        ServiceID srvcID = pair.getKey();
                        long tStamp = pair.getValue().gettStamp();
                        long deltaT = curTime - tStamp;
                        if(deltaT > timeThreshold) {
                            /* announcements stopped, queue reachability
                             * test and potential discarded event
                             */
                            UnicastResponse resp = registrars.get(srvcID);
                            Object req = new CheckReachabilityMarker(resp);
                            if(pendingDiscoveries.add(req)) {
                                restoreContextAddTask(
                                    new UnicastDiscoveryTask(req));
                            }//endif
                        }//end if
                    }//end loop (itr)
                }//end loop (!isInterrupted)
            } catch (InterruptedException e) { }
        }//end run
    }//end class AnnouncementTimerThread

    /**
     * Marker object placed in pendingDiscoveries set to indicate to
     * UnicastDiscoveryTask that the groups of the lookup service which sent
     * the contained announcement need to be verified.
     */
    private static class CheckGroupsMarker {
	/** Announcement sent by lookup service to check groups of */
	final MulticastAnnouncement announcement;

	CheckGroupsMarker(MulticastAnnouncement announcement) {
	    this.announcement = announcement;
	}

	public int hashCode() {
	    return announcement.getServiceID().hashCode();
	}

	public boolean equals(Object obj) {
	    return obj instanceof CheckGroupsMarker &&
		   announcement.getServiceID().equals(
		      ((CheckGroupsMarker) obj).announcement.getServiceID());
	}
    }

    /**
     * Marker object placed in pendingDiscoveries set to indicate to
     * UnicastDiscoveryTask that reachability of the lookup service which sent
     * the contained unicast response needs to be verified.
     */
    private static class CheckReachabilityMarker {
	/** Response sent by lookup service to check reachability of */
	final UnicastResponse response;

	CheckReachabilityMarker(UnicastResponse response) {
	    this.response = response;
	}

	public int hashCode() {
	    return response.getRegistrar().hashCode();
	}

	public boolean equals(Object obj) {
	    return obj instanceof CheckReachabilityMarker &&
		   response.getRegistrar().equals(
		      ((CheckReachabilityMarker) obj).response.getRegistrar());
	}
    }

    /**
     * Task which decodes received multicast announcement packets.  This is
     * separated into a task to allow the AnnouncementListener thread to
     * quickly loop and receive new announcement packets; the act of decoding
     * packets may involve relatively slow cryptographic operations such as
     * signature verification, and would impede the packet receiving loop if it
     * were performed inline.
     */
    private class DecodeAnnouncementTask implements Runnable {

	private final DatagramPacket datagram;

	/**
	 * Creates a task for decoding the given multicast announcement packet.
	 */
	public DecodeAnnouncementTask(DatagramPacket datagram) {
	    this.datagram = datagram;
	}

	/**
	 * Restore the privileged context and run
	 */
	public void run() {
	    Security.doPrivileged(new PrivilegedAction() {
		public Object run() {
		    doRun();
		    return null;
		}//end run
	    });//end doPrivileged
	}
	/**
	 * Decodes this task's multicast announcement packet.  If the
	 * constraints for decoding multicast announcements are satisfied and
	 * the announcement merits further processing, an appropriate object is
	 * added to the pendingDiscoveries set, and control is transferred to a
	 * UnicastDiscoveryTask.
	 */
	private void doRun() {
	    MulticastAnnouncement ann;
	    try {
		ann = decodeMulticastAnnouncement(datagram);
	    } catch (Exception e) {
		if (!(e instanceof InterruptedIOException)) {
		    logger.log(Levels.HANDLED,
			       "exception decoding multicast announcement", e);
		}
		return;
	    }

	    /* If the registrars map contains the service ID of the registrar
	     * that sent the current announcement then that registrar has
	     * already been discovered.
	     * 
	     * Determine if the member groups of the already-discovered
	     * registrar have been replaced by a set containing none of the
	     * desired groups. If yes, then discard the registrar.
	     * 
	     * If the registrar that sent the current announcement has not
	     * already been discovered, then check to see if any of the
	     * group(s) in which the registrar is a member are in the set of
	     * desired groups to discover. If yes, then queue the registrar for
	     * unicast discovery.
	     */
	    Object pending = null;
	    ServiceID srvcID = ann.getServiceID();
            UnicastResponse resp = registrars.get(srvcID);
            if (resp != null) {
                // already in discovered set, timestamp announcement
                AnnouncementInfo aInfo = regInfo.get(srvcID);
                if (aInfo == null){
                    // Possible interleaved putIfAbsent
                    aInfo = new AnnouncementInfo( System.currentTimeMillis(), -1);
                    AnnouncementInfo existed = regInfo.putIfAbsent(srvcID, aInfo);
                    if (existed != null) {
                        // The time will be almost identical so reuse existing.
                        aInfo = existed;
                    }
                } else {
                    AnnouncementInfo newAInfo = new AnnouncementInfo( System.currentTimeMillis(), aInfo.getSeqNum());
                    if (regInfo.replace(srvcID, aInfo, newAInfo)){ 
                        aInfo = newAInfo;
                    } else {
                        // May have been removed or replaced already
                        AnnouncementInfo existed = regInfo.putIfAbsent(srvcID, newAInfo);
                        if (existed != null){
                            aInfo = existed;
                        } else {
                            // Was absent
                            aInfo = newAInfo;
                        }
                    }
                }
                long currNum = ann.getSequenceNumber();
                if ((newSeqNum(currNum, aInfo.getSeqNum())) &&
                    (!groupSetsEqual(resp.getGroups(), ann.getGroups()))) {
                    /* Check if the groups have changed. In the case of
                     * split announcement messages, eventually, group difference
                     * will be seen for the given sequence number. This
                     * check ignores other differences, such as port numbers,
                     * but for the purposes of LookupDiscovery, this is not
                     * important.
                     */			
                    pending = new CheckGroupsMarker(ann);
                }
            } else if (groupsOverlap(ann.getGroups())) {
                // newly discovered
                pending = new LookupLocator(ann.getHost(), ann.getPort());
            }
	    if (pending != null) {
		try {
		    checkAnnouncementConstraints(ann);
		} catch (Exception e) {
		    if (!(e instanceof InterruptedIOException)) {
			logger.log(Levels.HANDLED,
			       "exception decoding multicast announcement", e);
		    }
		    return;
		}
		if (pending instanceof CheckGroupsMarker) {
                    // Since this is a valid announcement, update the
                    // sequence number.
                    AnnouncementInfo aInfo = regInfo.get(srvcID);
                    if (aInfo != null && !regInfo.replace // Avoid null pointer exception
                            (   srvcID, 
                                aInfo, 
                                new AnnouncementInfo( 
                                    aInfo.gettStamp(), 
                                    ann.getSequenceNumber()
                                ) 
                            )
                        )
                    {
                        logger.fine("aInfo changed and was not replaced");
                    }
		}
		// enqueue and handle pending action, if not already enqueued
		if (pendingDiscoveries.add(pending)) {
		    if (unicastDelayRange <= 0) {
			new UnicastDiscoveryTask(pending).run();
		    } else {
			final UnicastDiscoveryTask ud =
			    new UnicastDiscoveryTask(pending, true);
			final Ticket t = restoreContextScheduleRunnable(ud);
			synchronized (ud) {
			    ud.ticket = t;
			    ud.delayRun = false;
			    synchronized (tickets) {
				tickets.add(t);
			    }
			    ud.notifyAll();
			}
		    }
		}
	    }
	}

	/** Returns <code>true</code> if currentNum is a new sequence number 
	 * that needs to be inspected. A -1 occurs if the announcement had no
	 * sequence number (for e.g. DiscoveryV1) or the service had been
	 * discovered through unicast discovery. REMIND: Ideally the
	 * message should have a flag which indicates no sequence number instead
	 * of overloading the -1 value
	 */
	private boolean newSeqNum(long currentNum, long oldNum) {
	    if (oldNum == -1) {
		// No sequence number information, so we guess that this is
		// a new announcement of interest.
		return true;
	    } else if (currentNum > oldNum) {
		return true;
	    } else {
		return false;
	    }
	}
    }

    /** Task which retrieves elements from the set of pendingDiscoveries and
     *  performs the appropriate processing based on the object type of
     *  the element.
     *  <p>
     *  Each element of the set of pendingDiscoveries is one of the following
     *  object types: Socket, LookupLocator, CheckGroupsMarker,
     *  or CheckReachabilityMarker.
     *  <p>
     *  When the element to process is a Socket, the element was a result
     *  of a multicast request/response exchange (see the Requestor and
     *  ResponseListener Thread classes). In this case, this task completes
     *  the discovery of the associated lookup service by performing the
     *  final stage of unicast discovery, ultimately resulting in a discovered
     *  event being sent to all registered listeners.
     *  <p>
     *  When the element to process is a LookupLocator, the element was a
     *  result of a multicast announcement received from a lookup service -
     *  belonging to at least one group of interest - which has not already
     *  been discovered. In this case, this task also completes the discovery
     *  of the lookup service referenced in the announcement by performing the
     *  final stage of unicast discovery, ultimately resulting in a discovered
     *  event being sent to all registered listeners.
     *  <p>
     *  When the element to process is a CheckGroupsMarker, the
     *  element was a result of a multicast announcement received from an
     *  already-discovered lookup service whose member groups have changed
     *  in some way. In this case, this task determines how those member
     *  groups have changed and, based on how they have changed, whether 
     *  (or not) to send a discarded event or a changed event to the
     *  appropriate registered listeners.
     *  <p>
     *  When the element to process is a CheckReachabilityMarker, the
     *  element was a result of a determination that the multicast 
     *  announcements from an already-discovered lookup service have
     *  stopped being received (see the AnnouncementTimerThread class).
     *  In this case, this task determines if the affected lookup service
     *  is still available ("reachable"). If this task cannot communicate
     *  with the lookup service, a discarded event is queued to be sent
     *  to all registered listeners.
     *  <p>
     *  Rather than performing unicast discovery synchronously, after multicast
     *  discovery has occurred in either the AnnouncementListener thread 
     *  (the multicast announcement protocol) or the ResponseListener thread
     *  (the multicast request protocol), the unicast discovery processing 
     *  that is required to complete the discovery process is queued for
     *  asynchronous execution in this task. Unicast discovery is performed
     *  asynchronously because unicast discovery can take quite a while to
     *  fail if a lookup service "disappears" (because the network or the
     *  lookup service itself has crashed) between the time a multicast
     *  announcement or response indicates the existence of a lookup service
     *  eligible for unicast discovery, and the time unicast discovery 
     *  actually starts. If unicast discovery is performed synchronously
     *  in the threads that implement the multicast announcement and multicast
     *  request protocols, other multicast announcements (as well as other
     *  unicast discoveries) will be missed whenever a lookup service
     *  disappears prior to the commencement of the unicast discovery stage.
     */
    private class UnicastDiscoveryTask implements Runnable {
	private final Object req;
	private Ticket ticket = null;
	private boolean delayRun = false;
	UnicastDiscoveryTask(Object req) {
	    this(req, false);
	}
	UnicastDiscoveryTask(Object req, boolean delayRun) {
	    this.req = req;
	    this.delayRun = delayRun;
	}
	/**
	 * Restore the privileged context and run
	 */
	public void run() {
	    Security.doPrivileged(new PrivilegedAction() {
		public Object run() {
		    doRun();
		    return null;
		}//end run
	    });//end doPrivileged
	}
	private void doRun() {
            logger.finest("LookupDiscovery - UnicastDiscoveryTask started");
	    try {
		synchronized (this) {
		    while (delayRun) {
			this.wait();
		    }
		    synchronized (tickets) {
			// If this was run by a WakeupManager, remove its
			// ticket from the list of outstanding tickets.
			if (ticket != null) {
			    tickets.remove(ticket);
			}
		    }
		}
		Socket sock = null;
		MulticastAnnouncement announcement = null;
		UnicastResponse response = null;
		if (req instanceof Socket) {
		    // Perform unicast discovery on the connected socket.
		    DiscoveryConstraints unicastDiscoveryConstraints =
			DiscoveryConstraints.process(
			    rawUnicastDiscoveryConstraints);
		    sock = (Socket)req;
		    UnicastResponse resp;
		    try {
			prepareSocket(sock, unicastDiscoveryConstraints);
			resp = doUnicastDiscovery(sock,
						  unicastDiscoveryConstraints);
		    } finally {
			try {
			    sock.close();
			} catch (IOException e) { /* ignore */ }
		    }
		    maybeAddNewRegistrar(resp);
		} else if(req instanceof LookupLocator) {
		    // Perform unicast discovery using the LookupLocator
		    // host and port.
		    LookupLocator loc = (LookupLocator)req;
		    UnicastResponse resp = new MultiIPDiscovery() {
			protected UnicastResponse performDiscovery(
							Discovery disco,
							DiscoveryConstraints dc,
							Socket s)
			    throws IOException, ClassNotFoundException
			{
			    return doUnicastDiscovery(s, dc, disco);
			}
			protected void singleResponseException(Exception e,
							       InetAddress addr,
							       int port)
			{
                                logger.log(
				Levels.HANDLED,
				"Exception occured during unicast discovery " +
				addr + ":" + port, e);
                            } 
		    }.getResponse(loc.scheme(), loc.getHost(),
				      loc.getPort(),
				      rawUnicastDiscoveryConstraints);
		    maybeAddNewRegistrar(resp);
		} else if(req instanceof CheckGroupsMarker) {
		    // handle group changes
		    announcement = ((CheckGroupsMarker)req).announcement;
		    ServiceID srvcID = announcement.getServiceID();
		    UnicastResponse resp = registrars.get(srvcID);
		    if(resp != null) {
			maybeSendEvent(resp, announcement.getGroups());
		    }//endif
		} else if(req instanceof CheckReachabilityMarker) { 
		    // test reachability
		    response = ((CheckReachabilityMarker)req).response;
		    maybeSendEvent(response, null);
		}//endif
	    } catch (InterruptedIOException e) {
		logger.log(Levels.HANDLED,
			   "exception occurred during unicast discovery",
			   e);
	    } catch (Throwable e) {
		if (((req instanceof Socket) ||
		    (req instanceof LookupLocator)) &&
		    logger.isLoggable(Level.INFO)) {
		    String logmsg =
			"exception occurred during unicast discovery to " +
			"{0}:{1,number,#} with constraints {2}";
		    String methodName = "run";
		    if (req instanceof Socket) {
			Socket sock = (Socket) req;
			LogUtil.logThrow(logger, 
					 Level.INFO,
					 this.getClass(),
					 methodName,
					 logmsg,
					 new Object[] {
					    sock.getInetAddress().getHostName(),
					    Integer.valueOf(sock.getPort()),
					    rawUnicastDiscoveryConstraints
					 },
					 e);
		    } else {
			LookupLocator loc = (LookupLocator) req;
			LogUtil.logThrow(logger, 
					 Level.INFO,
					 this.getClass(),
					 methodName,
					 logmsg,
					 new Object[] {
					    loc.getHost(),
					    Integer.valueOf(loc.getPort()),
					    rawUnicastDiscoveryConstraints
					 },
					 e);
		    }
		} else {
		    logger.log(Level.INFO,
			   "exception occurred during unicast discovery",
			   e);
		}
	    } finally {
		// Done with the request. Remove it regardless of
		// if we succeeded or failed.
                pendingDiscoveries.remove(req);
	    }//end try/catch
            logger.finest("LookupDiscovery - UnicastDiscoveryTask completed");
	}//end run

    }//end class UnicastDiscoveryTask

    /**
     * Construct a new lookup discovery object, set to discover the
     * given set of groups.  The set is represented as an array of
     * strings.  This array may be empty, which is taken as the empty
     * set, and discovery is not performed.  The reference passed in
     * may be null, which is taken as no set, and in which case
     * discovery of all reachable lookup services is performed.
     * Otherwise, the array contains the names of groups to discover.
     * The caller must have DiscoveryPermission for each group (or
     * for all groups, if the array is null).
     *
     * @param groups the set of group names to discover (null for no
     *               set, empty for no discovery)
     *
     * @throws java.lang.NullPointerException input array contains at least
     *         one null element
     *
     * @throws java.io.IOException an exception occurred in starting discovery
     *
     * @see #NO_GROUPS
     * @see #ALL_GROUPS
     * @see #setGroups
     * @see DiscoveryPermission
     */
    AbstractLookupDiscovery(String[] groups) throws IOException {
        this(verifyGroups(groups), initEmptyConf());
    }//end constructor
    
        /**
     * Constructs a new lookup discovery object, set to discover the
     * given set of groups, and having the given <code>Configuration</code>.
     * <p>
     * The set of groups to discover is represented as an array of
     * strings.  This array may be empty, which is taken as the empty
     * set, and discovery is not performed.  The reference passed in
     * may be <code>null</code>, which is taken as no set, and in which
     * case discovery of all reachable lookup services is performed.
     * Otherwise, the array contains the names of groups to discover.
     * The caller must have <code>DiscoveryPermission</code> for each
     * group (or for all groups, if the array is <code>null</code>).
     *
     * @param groups the set of group names to discover (null for no
     *               set, empty for no discovery)
     *
     * @param config an instance of <code>Configuration</code>, used to
     *               obtain the objects needed to configure the current
     *               instance of this class
     *
     * @throws java.lang.NullPointerException input array contains at least
     *         one <code>null</code> element or <code>null</code> is input
     *         for the configuration
     *
     * @throws java.io.IOException an exception occurred in starting discovery
     *
     * @throws net.jini.config.ConfigurationException indicates an exception
     *         occurred while retrieving an item from the given
     *         <code>Configuration</code>
     *
     * @see #NO_GROUPS
     * @see #ALL_GROUPS
     * @see #setGroups
     * @see DiscoveryPermission
     * @see net.jini.config.Configuration
     * @see net.jini.config.ConfigurationException
     */
    AbstractLookupDiscovery(String[] groups, Configuration config) 
            throws IOException, ConfigurationException {
        this(verifyGroups(groups), init(config));
    }// end constructor
    
    static String [] verifyGroups(String [] groups) 
            throws NullPointerException, SecurityException {
        testArrayForNullElement(groups);
        checkGroups(groups);
        return groups;
    }
    
    static Initializer initEmptyConf() throws IOException {
        try {
            return new Initializer(EmptyConfiguration.INSTANCE);
        } catch (ConfigurationException ex) {
            throw new IOException("EmptyConfiguration threw exception", ex);
        }
    }
    
    static Initializer init(Configuration config) throws IOException, ConfigurationException {
        return new Initializer(config);
    }
    
    private static class Initializer {
        ProxyPreparer registrarPreparer;
        DiscoveryConstraints multicastRequestConstraints;
        DiscoveryConstraints multicastAnnouncementConstraints;
        InvocationConstraints rawUnicastDiscoveryConstraints;
        ExecutorService executor;
        int multicastRequestMax;
        long multicastRequestInterval;
        long finalMulticastRequestInterval;
        String multicastRequestHost;
        NetworkInterface [] nics;
        int nicsToUse;
        int nicRetryInterval;
        long multicastAnnouncementInterval;
        long unicastDelayRange;
        List<Ticket> tickets;
        WakeupManager discoveryWakeupMgr;
        boolean isDefaultWakeupMgr;
        long initialMulticastRequestDelayRange;
        
        private Initializer(Configuration config) throws ConfigurationException,
                UnsupportedConstraintException, UnknownHostException, SocketException
        {
            /* Read Configuration */

            if(config == null)  throw new NullPointerException("config is null");
            /* Lookup service proxy preparer */
            registrarPreparer = (ProxyPreparer)config.getEntry
                                                        (COMPONENT_NAME,
                                                         "registrarPreparer",
                                                         ProxyPreparer.class,
                                                         new BasicProxyPreparer());
            /* constraints */
            MethodConstraints constraints = (MethodConstraints)config.getEntry
                                                        (COMPONENT_NAME,
                                                         "discoveryConstraints",
                                                         MethodConstraints.class,
                                                         null);
            if (constraints == null) {
                constraints = 
                    new BasicMethodConstraints(InvocationConstraints.EMPTY);
            }
            multicastRequestConstraints = DiscoveryConstraints.process(
                constraints.getConstraints(
                    DiscoveryConstraints.multicastRequestMethod));
            multicastAnnouncementConstraints = DiscoveryConstraints.process(
                constraints.getConstraints(
                    DiscoveryConstraints.multicastAnnouncementMethod));
            rawUnicastDiscoveryConstraints = 
                constraints.getConstraints(
                    DiscoveryConstraints.unicastDiscoveryMethod);

            /* ExecutorService */
            ExecutorService executorServ;
            try {
                executorServ = (ExecutorService) config.getEntry(COMPONENT_NAME,
                        "executorService", ExecutorService.class);
            } catch (NoSuchEntryException e) { /* use default */
                executorServ =
                    new ThreadPoolExecutor(
                        MAX_N_TASKS, 
                        MAX_N_TASKS, /* Ignored */
                        15L, TimeUnit.SECONDS,
                        new LinkedBlockingQueue<Runnable>(), /* Unbounded Queue */
                        new NamedThreadFactory("LookupDiscovery", false)
                    );
            }
            this.executor = executorServ;

            /* Multicast request-related configuration items */
            multicastRequestMax
             = ( config.getEntry
                                 (COMPONENT_NAME,
                                  "multicastRequestMax",
                                  int.class,
                                  Integer.valueOf(7) ) ).intValue();
            multicastRequestInterval
             = ( config.getEntry
                                (COMPONENT_NAME,
                                "multicastRequestInterval",
                                long.class,
                                Long.valueOf(5000L) ) ).longValue();
            finalMulticastRequestInterval
             = ( config.getEntry
                          (COMPONENT_NAME,
                           "finalMulticastRequestInterval",
                           long.class,
                           Long.valueOf(2*60*1000L) ) ).longValue();
            String multicastRequestHost;
            try {
                multicastRequestHost
                 = Config.getNonNullEntry(config,
                                                   COMPONENT_NAME,
                                                   "multicastRequestHost",
                                                   String.class);
            } catch (NoSuchEntryException nse) {
                multicastRequestHost = getLocalHost();
            }
            this.multicastRequestHost = multicastRequestHost;
            /* Configuration items related to the network interface(s) */
            NetworkInterface[] nics = null;
            int nicsToUse = NICS_USE_ALL;
            try {
                nics = (NetworkInterface[])config.getEntry
                                                       (COMPONENT_NAME,
                                                        "multicastInterfaces",
                                                        NetworkInterface[].class);
                if(nics == null) {
                    nicsToUse = NICS_USE_SYS;
                    logger.config("LookupDiscovery - using system default network "
                                  +"interface for multicast");
                } else {//(nics != null)
                    if( nics.length == 0 ) {
                        nicsToUse = NICS_USE_NONE;
                        logger.config("LookupDiscovery - MULTICAST DISABLED");
                    } else {//(nics.length > 0), use the given specific list
                        nicsToUse = NICS_USE_LIST;
                        if( logger.isLoggable(Level.CONFIG) ) {
                            logger.log(Level.CONFIG,
                                   "LookupDiscovery - multicast network "
                                   +"interface(s): {0}", Arrays.asList(nics) );
                        }//endif
                    }//endif
                }//endif
            } catch(NoSuchEntryException e) {// no config item, use default - all
                Enumeration en = NetworkInterface.getNetworkInterfaces();
                List nicList = (en != null) ?
                    Collections.list(en) : Collections.EMPTY_LIST;
                nics = (NetworkInterface[])(nicList.toArray
                                         (new NetworkInterface[nicList.size()]) );
                nicsToUse = NICS_USE_ALL;
                if( logger.isLoggable(Level.CONFIG) ) {
                    logger.log(Level.CONFIG,"LookupDiscovery - multicast network "
                                            +"interface(s): {0}", nicList);
                }//endif
            }
            this.nics = nics;
            this.nicsToUse = nicsToUse;

            nicRetryInterval
             = ( config.getEntry
                                    (COMPONENT_NAME,
                                     "multicastInterfaceRetryInterval",
                                     int.class,
                                     Integer.valueOf(5*60*1000) ) ).intValue();
            /* Multicast announcement-related configuration items */
            multicastAnnouncementInterval
             = ( config.getEntry
                          (COMPONENT_NAME,
                           "multicastAnnouncementInterval",
                           long.class,
                           Long.valueOf(2*60*1000L) ) ).longValue();

            unicastDelayRange = Config.getLongEntry(config,
                                        COMPONENT_NAME,
                                        "unicastDelayRange",
                                        0,
                                        0,
                                        Long.MAX_VALUE);
            tickets = new ArrayList();

            /* Wakeup manager */
            WakeupManager discoveryWakeupMgr = null;
            boolean isDefaultWakeupMgr = false;
            if (unicastDelayRange > 0) {
                try {
                    discoveryWakeupMgr =
                            (WakeupManager)config.getEntry(COMPONENT_NAME,
                                                           "wakeupManager",
                                                           WakeupManager.class);
                } catch(NoSuchEntryException e) { /* use default */
                    discoveryWakeupMgr = new WakeupManager(
                                         new WakeupManager.ThreadDesc(null, true));
                    isDefaultWakeupMgr = true;
                }
            }
            this.discoveryWakeupMgr = discoveryWakeupMgr;
            this.isDefaultWakeupMgr = isDefaultWakeupMgr;

            initialMulticastRequestDelayRange = Config.getLongEntry(config,
                                COMPONENT_NAME,
                                "initialMulticastRequestDelayRange",
                                0,
                                0,
                                Long.MAX_VALUE);

            /* End read configuration */
        }
    }

    private AbstractLookupDiscovery(String[] groups,  Initializer init)
    {
	this.groups = new ConcurrentSkipListSet();
        if (groups != null) {
	    for (int i = 0; i < groups.length; i++) {
		this.groups.add(groups[i]);
	    }//end loop
            all_groups = false;
	} else {
            all_groups = true;
        }
        
        /* Init */
        registrarPreparer = init.registrarPreparer;
        multicastRequestConstraints = init.multicastRequestConstraints;
        multicastAnnouncementConstraints = init.multicastAnnouncementConstraints;
        rawUnicastDiscoveryConstraints = init.rawUnicastDiscoveryConstraints;
        executor = init.executor;
        multicastRequestMax = init.multicastRequestMax;
        multicastRequestInterval = init.multicastRequestInterval;
        finalMulticastRequestInterval = init.finalMulticastRequestInterval;
        multicastRequestHost = init.multicastRequestHost;
        nics = init.nics;
        nicsToUse = init.nicsToUse;
        nicRetryInterval = init.nicRetryInterval;
        multicastAnnouncementInterval = init.multicastAnnouncementInterval;
        unicastDelayRange = init.unicastDelayRange;
        tickets = init.tickets;
        discoveryWakeupMgr = init.discoveryWakeupMgr;
        isDefaultWakeupMgr = init.isDefaultWakeupMgr;
        initialMulticastRequestDelayRange = init.initialMulticastRequestDelayRange;
        /* end Init */
        
        if(nicsToUse ==  NICS_USE_NONE) { //disable discovery
            announcementTimerThread = null;
            announceeThread = null;
            notifierThread = null;
            thrown = null;
            return;
        } 
        
        /* threads */
        AnnouncementTimerThread announcementTimerThread = null;
        AnnouncementListener announceeThread = null;
        Notifier notifierThread = null;
        Exception thrown = null;
	try {
            announcementTimerThread  = Security.doPrivileged(new PrivilegedExceptionAction<AnnouncementTimerThread>() {
		public AnnouncementTimerThread run() throws Exception {
                    return new AnnouncementTimerThread();
		}//end run
	    });//end doPrivileged
	
            announceeThread = Security.doPrivileged(new PrivilegedExceptionAction<AnnouncementListener>() {
		public AnnouncementListener run() throws Exception {
                    return new AnnouncementListener();
		}//end run
	    });//end doPrivileged
	
            notifierThread = Security.doPrivileged(new PrivilegedExceptionAction<Notifier>() {
		public Notifier run() throws Exception {
                    return new Notifier();
		}//end run
	    });//end doPrivileged
	} catch (PrivilegedActionException e) {
	    thrown = e.getException();
	}
        this.announcementTimerThread = announcementTimerThread;
        this.announceeThread = announceeThread;
        this.notifierThread = notifierThread;
        this.thrown = thrown;
        
    }//end constructor
    
    /* sync on this */
    private boolean started = false;
    
    /**
     * In previous releases threads were started in the constructor, however
     * this violates safe construction according to the JMM.
     * <p>
     * Threads will be started automatically when a <code>DiscoveryListener</code>
     * is added, if start hasn't been called.
     * 
     * @since 2.2.1
     */
    void start() throws IOException {
        synchronized (this){
            if (started) return;
            if (thrown != null) throw (IOException) thrown;
            if (!all_groups || !this.groups.isEmpty()) {
                requestGroups(this.groups);
            }//end if
            if (nicsToUse != NICS_USE_NONE) {
                announceeThread.start();
                announcementTimerThread.start();
                notifierThread.start();
            }
            started = true;
        }
    }

    /**
     * Register a listener as interested in receiving DiscoveryEvent
     * notifications.
     *
     * @param l the listener to register
     *
     * @throws java.lang.NullPointerException this exception occurs when
     *         <code>null</code> is input to the listener parameter
     *         <code>l</code>.
     *
     * @throws java.lang.IllegalStateException this exception occurs when
     *         this method is called after the <code>terminate</code>
     *         method has been called.
     * 
     * @see DiscoveryEvent
     * @see #removeDiscoveryListener
     */
    public void addDiscoveryListener(DiscoveryListener l) {
        if(l == null) {
            throw new NullPointerException("can't add null listener");
        }
        if (terminated) {
            throw new IllegalStateException("discovery terminated");
        }
        if (listeners.indexOf(l) >= 0) return; //already have this listener
        listeners.add(l);
        if (registrars.isEmpty()) return;//nothing to send the new listener
        Map<ServiceRegistrar,String[]> groupsMap = new HashMap(registrars.size());
        Iterator<UnicastResponse> iter = registrars.values().iterator();
        while (iter.hasNext()) {
            UnicastResponse resp = iter.next();
            groupsMap.put(resp.getRegistrar(),resp.getGroups());
        }
        List<DiscoveryListener> list = new ArrayList<DiscoveryListener>(1);
        list.add(l);
        addNotify(list, groupsMap, DISCOVERED);
    }//end addDiscoveryListener

    /**
     * Indicate that a listener is no longer interested in receiving
     * DiscoveryEvent notifications.
     *
     * @param l the listener to unregister
     *
     * @throws java.lang.IllegalStateException this exception occurs when
     *         this method is called after the <code>terminate</code>
     *         method has been called.
     * 
     * @see #addDiscoveryListener
     */
    public void removeDiscoveryListener(DiscoveryListener l) {
        if (terminated) {
            throw new IllegalStateException("discovery terminated");
        }
        listeners.remove(l);
    }//end removeDiscoveryListener

    /**
     * Returns an array of instances of <code>ServiceRegistrar</code>, each
     * corresponding to a proxy to one of the currently discovered lookup
     * services. For each invocation of this method, a new array is returned.
     *
     * @return array of instances of <code>ServiceRegistrar</code>, each
     *         corresponding to a proxy to one of the currently discovered
     *         lookup services
     *
     * @throws java.lang.IllegalStateException this exception occurs when
     *         this method is called after the <code>terminate</code>
     *         method has been called.
     * 
     * @see net.jini.core.lookup.ServiceRegistrar
     * @see net.jini.discovery.DiscoveryManagement#removeDiscoveryListener
     */
    public ServiceRegistrar[] getRegistrars() {
        if (terminated) {
            throw new IllegalStateException("discovery terminated");
        }
        if (registrars.isEmpty()) {
            return new ServiceRegistrar[0];
        }
        Iterator iter = registrars.values().iterator();
        ServiceRegistrar[] regs = new ServiceRegistrar[registrars.size()];
        for (int i=0;iter.hasNext();i++) {
            regs[i] = ((UnicastResponse)iter.next()).getRegistrar();
        }
        return regs;
    }//end getRegistrars

    /**
     * Discard a registrar from the set of registrars already
     * discovered.  This does not prevent that registrar from being
     * rediscovered; it is intended to be used to clear unreachable
     * entries from the set. <p>
     *
     * If the registrar has been discovered using this LookupDiscovery
     * object, each listener registered with this object will have its
     * discarded method called with the given registrar as parameter.
     *
     * @param reg the registrar to discard
     *
     * @throws java.lang.IllegalStateException this exception occurs when
     *         this method is called after the <code>terminate</code>
     *         method has been called.
     * 
     * @see DiscoveryListener#discarded
     */
    public void discard(ServiceRegistrar reg) {
        if (terminated) {
            throw new IllegalStateException("discovery terminated");
        }
        if(reg == null) return;
        sendDiscarded(reg,null);
    }//end discard

    /** Terminate the discovery process. */
    public void terminate() {
        if (terminated)  return;
        terminated = true;
	nukeThreads();
    }//end terminate

    /**
     * Return the set of group names this LookupDiscovery instance is
     * trying to discover.  If this method returns the empty array,
     * that value is guaranteed to be referentially equal to
     * LookupDiscovery.NO_GROUPS.
     *
     * @return the set of groups to be discovered (null for all, empty
     *         for no discovery)
     *
     * @throws java.lang.IllegalStateException this exception occurs when
     *         this method is called after the <code>terminate</code>
     *         method has been called.
     * 
     * @see #NO_GROUPS
     * @see #ALL_GROUPS
     * @see #setGroups
     */
    public String[] getGroups() {
        if (terminated) {
            throw new IllegalStateException("discovery terminated");
        }
        if (all_groups)
            return ALL_GROUPS;
        if (groups.isEmpty())
            return NO_GROUPS;
        return collectionToStrings(groups);
    }//end getGroups

    /**
     * Add a set of groups to the set to be discovered.
     * The caller must have DiscoveryPermission for each group.
     *
     * @param newGroups the groups to add
     *
     * @throws java.io.IOException the multicast request protocol failed
     *         to start
     *
     * @throws java.lang.IllegalStateException this exception occurs when
     *         this method is called after the <code>terminate</code>
     *         method has been called.
     * 
     * @throws java.lang.UnsupportedOperationException there is no set of
     *         groups to add to
     *
     * @see DiscoveryPermission
     */
    public void addGroups(String[] newGroups) throws IOException {
        testArrayForNullElement(newGroups);
	checkGroups(newGroups);
        if (terminated)
            throw new IllegalStateException("discovery terminated");
        if (all_groups)
            throw new UnsupportedOperationException(
                                      "can't add to \"any groups\"");
        Collection req = new ArrayList(newGroups.length);
        for (int i = 0; i < newGroups.length; i++) {
            if (groups.add(newGroups[i]))
                req.add(newGroups[i]);
        }
        if (!req.isEmpty())
            requestGroups(req);
    }//end addGroups

    /**
     * Change the set of groups to be discovered to correspond to the
     * given set.  The set is represented as an array of strings.
     * This array may be empty, which is taken as the empty set, and
     * discovery is not performed.  The reference passed in may be
     * null, which is taken as no set, and in which case discovery of
     * all reachable lookup services is performed.  Otherwise, the
     * array contains the names of groups to discover.
     * The caller must have DiscoveryPermission for each group (or
     * for all groups, if the array is null).
     *
     * @param newGroups the new set of groups to discover (null for
     *                  all, empty array for no discovery)
     *
     * @throws java.io.IOException an exception occurred when starting
     *         multicast discovery
     *
     * @throws java.lang.IllegalStateException this exception occurs when
     *         this method is called after the <code>terminate</code>
     *         method has been called.
     * 
     * @see LookupDiscovery
     * @see #ALL_GROUPS
     * @see #NO_GROUPS
     * @see DiscoveryPermission
     * @see #getGroups
     */
    @Override
    public void setGroups(String[] newGroups) throws IOException {
        testArrayForNullElement(newGroups);
	checkGroups(newGroups);
	boolean maybeDiscard = false;
	Set newGrps = null;
	if (newGroups != null) {	
	    newGrps = new HashSet(newGroups.length * 2);
	    for (int i = 0; i < newGroups.length; i++) {
		newGrps.add(newGroups[i]);
	    }
	}
        if (terminated)
            throw new IllegalStateException("discovery terminated");
        if (newGroups == null) {
            if (!all_groups) {
                all_groups = true;
                groups.clear();
                requestGroups(null);
            }
            return;
        }
        if (all_groups == true) {
            all_groups = false;
            groups.clear();
            maybeDiscard = true;
        }
        Set toAdd = new HashSet(newGrps);
        toAdd.removeAll(groups);
        // Figure out which groups to get rid of.  We start off
        // with the full set for which we are already listening,
        // and eliminate any that are in both the new set and the
        // current set.
        Collection toRemove = new HashSet(groups);
        toRemove.removeAll(newGrps);
        // Add new groups before we remove any old groups, because
        // removeGroups will start a new round of multicast requests
        // if the set of groups becomes empty, and we don't want it
        // to do so without reason.
        groups.addAll(toAdd);
        if (!toRemove.isEmpty())
            maybeDiscard |= removeGroupsInt(collectionToStrings(toRemove));
        if (!toAdd.isEmpty())
            requestGroups(toAdd);
	if (maybeDiscard)
	    maybeDiscardRegistrars();
    }//end setGroups

    /**
     * Remove a set of groups from the set to be discovered.
     *
     * @param oldGroups groups to remove
     *
     * @throws java.lang.IllegalStateException this exception occurs when
     *         this method is called after the <code>terminate</code>
     *         method has been called.
     * 
     * @throws java.lang.UnsupportedOperationException there is no set of
     *         groups from which to remove
     */
    public void removeGroups(String[] oldGroups) {
        testArrayForNullElement(oldGroups);
	boolean maybeDiscard;
        if (terminated)
            throw new IllegalStateException("discovery terminated");
        if (all_groups)
            throw new UnsupportedOperationException(
                                       "can't remove from \"any groups\"");
        maybeDiscard = removeGroupsInt(oldGroups);
	if (maybeDiscard)
	    maybeDiscardRegistrars();
    }//end removeGroups

    /**
     * Sends the given packet data on the given <code>MulticastSocket</code>
     * through each of the network interfaces corresponding to elements of
     * the array configured when this utility was constructed.
     *
     * @param mcSocket   the <code>MulticastSocket</code> on which the data
     *                   will be sent
     * @param packet     <code>DatagramPacket</code> array whose elements are
     *                   the data to send 
     *
     * @throws java.io.InterruptedIOException
     */
    private void sendPacketByNIC(MulticastSocket mcSocket,
                                 DatagramPacket[] packet)
                                                 throws InterruptedIOException
    {
        switch(nicsToUse) {
            case NICS_USE_ALL:
                /* Using all interfaces. Skip (but report) any interfaces
                 * that are "bad" or not configured for multicast.
                 */
                for(int i=0;i<nics.length;i++) {
                    try {
                        mcSocket.setNetworkInterface(nics[i]);
                        sendPacket(mcSocket,packet);
                    } catch(InterruptedIOException e) {
                        throw e;//to signal a graceful exit
                    } catch(IOException e) {
                        if( logger.isLoggable(Levels.HANDLED) ) {
                            LogRecord logRec = 
                              new LogRecord(Levels.HANDLED,
					    "network interface is "
                                            +"bad or not configured for "
                                            +"multicast: {0}");
                            logRec.setParameters(new Object[]{nics[i]});
                            logRec.setThrown(e);
                            logger.log(logRec);
                        }//endif
                    } catch(Exception e) {
                        if( logger.isLoggable(Levels.HANDLED) ) {
                            LogRecord logRec = 
                              new LogRecord(Levels.HANDLED, "exception while "
                                            +"sending packet through network "
                                            +"interface: {0}");
                            logRec.setParameters(new Object[]{nics[i]});
                            logRec.setThrown(e);
                            logger.log(logRec);
                        }//endif
                    }
                }//end loop
                break;
            case NICS_USE_LIST:
                /* Using a configured list of specific interfaces. Skip (but
                 * always report) any interfaces that are "bad" or not
                 * configured for multicast.
                 */
                for(int i=0;i<nics.length;i++) {
                    try {
                        mcSocket.setNetworkInterface(nics[i]);
                        sendPacket(mcSocket,packet);
                    } catch(InterruptedIOException e) {
                        throw e;//to signal a graceful exit
                    } catch(IOException e) {
                        if( logger.isLoggable(Level.SEVERE) ) {
                            LogRecord logRec = 
                              new LogRecord(Level.SEVERE,"network interface "
                                            +"is bad or not configured for "
                                            +"multicast: {0}");
                            logRec.setParameters(new Object[]{nics[i]});
                            logRec.setThrown(e);
                            logger.log(logRec);
                        }//endif
                    } catch(Exception e) {
                        if( logger.isLoggable(Level.SEVERE) ) {
                            LogRecord logRec = 
                              new LogRecord(Level.SEVERE,"exception while "
                                            +"sending packet through network "
                                            +"interface: {0}");
                            logRec.setParameters(new Object[]{nics[i]});
                            logRec.setThrown(e);
                            logger.log(logRec);
                        }//endif
                    }
                }//end loop
                break;
            case NICS_USE_SYS:
                /* Using the system-dependent default interface. Don't need
                 * to specifically set the interface. If that interface is
                 * "bad" or not configured for multicast, always report it.
                 */
                try {
                    sendPacket(mcSocket,packet);
                } catch(InterruptedIOException e) {
                    throw e;//to signal a graceful exit
                } catch(IOException e) {
                    if( logger.isLoggable(Level.SEVERE) ) {
                        logger.log(Level.SEVERE, "system default network "
                                   +"interface is bad or not configured "
                                   +"for multicast", e);
                    }//endif
                } catch(Exception e) {
                    if( logger.isLoggable(Level.SEVERE) ) {
                        logger.log(Level.SEVERE, "exception while sending "
                                   +"packet through system default network "
                                   +"interface", e);
                    }//endif
                }
                break;
            case NICS_USE_NONE:
                break;//multicast disabled, do nothing
            default:
                throw new AssertionError("nicsToUse flag out of range (0-3): "
                                         +nicsToUse);
        }//end switch(nicsToUse)
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
     * @throws java.io.IOException
     */
    private static void sendPacket(MulticastSocket mcSocket,
                                   DatagramPacket[] packet) throws IOException
    {
        for(int i=0;i<packet.length;i++) {
            mcSocket.send(packet[i]);
        }//end loop
    }//end sendPacket

    /** Returns the local host name. */
    private static String getLocalHost() throws UnknownHostException {
	try {
	    return ((InetAddress) Security.doPrivileged(
		new PrivilegedExceptionAction() {
		    public Object run() throws UnknownHostException {
			return LocalHostLookup.getLocalHost();
		    }
		})).getHostAddress();
	} catch (PrivilegedActionException e) {
	    // Remove host information if caller does not have privileges
	    // to see it.
	    try {
		LocalHostLookup.getLocalHost();
	    } catch (UnknownHostException uhe) {
		throw uhe;
	    }
	    logger.log(Levels.FAILED, "Unknown host exception", e.getCause());
	    throw new UnknownHostException("Host name cleared due to " +
					   "insufficient caller permissions");
	}
    }

    /** Determines if the caller has discovery permission for each group. */
    private static void checkGroups(String[] groups) {
	SecurityManager sm = System.getSecurityManager();
	if (sm == null)  return;
	if (groups != null) {
	    for (int i = 0; i < groups.length; i++) {
		sm.checkPermission(new DiscoveryPermission(groups[i]));
	    }//end loop
	} else {
	    sm.checkPermission(new DiscoveryPermission("*"));
	}//endif
    }//end checkGroups

    /** Converts a collection to an array of strings. */
    private static final String[] collectionToStrings(Collection c) {
	return c == null ? null : (String[]) c.toArray(new String[c.size()]);
    }//end collectionToStrings

    /** Determines if two sets of registrar member groups have identical
     *  contents. Assumes there are no duplicates, and the sets can never
     *  be null.
     *
     *  @param groupSet0    <code>String</code> array containing the group
     *                      names from the first set used in the comparison
     *  @param groupSet1    <code>String</code> array containing the group
     *                      names from the second set used in the comparison
     * 
     *  @return <code>true</code> if the contents of each set is identical;
     *          <code>false</code> otherwise
     */
    private static boolean groupSetsEqual(String[] groupSet0,
                                          String[] groupSet1)
    {
        if(groupSet0.length != groupSet1.length) return false;
        /* is every element of one set contained in the other set? */
        iLoop:
        for(int i=0;i<groupSet0.length;i++) {
            for(int j=0;j<groupSet1.length;j++) {
                if( groupSet0[i].equals(groupSet1[j]) ) {
                    continue iLoop;
                }
            }//end loop(j)
            return false;
        }//end loop(i)
        return true;
    }//end groupSetsEqual

    /** Returns true if the registrars contained in the given (possibly null)
     *  UnicastResponse instances are equals() to one another.
     */
    private static boolean registrarsEqual(UnicastResponse resp1,
					   UnicastResponse resp2)
    {
	return resp1 != null && resp2 != null &&
	       resp2.getRegistrar().equals(resp1.getRegistrar());
    }//end registrarsEqual

    /**
     * Remove the specified groups from the set of groups to discover, and
     * return true if any were actually removed.  Must be synchronised on registrars.
     */
    private boolean removeGroupsInt(String[] oldGroups) {
	boolean removed = false;
	for (int i = 0; i < oldGroups.length; i++) {
	    removed |= groups.remove(oldGroups[i]);
	}
	return removed;
    }//end removeGroupsInt

    /** Returns the service IDs of the lookup service(s) discovered to date. */
    private ServiceID[] getServiceIDs() {
        return (ServiceID[])
            registrars.keySet().toArray(new ServiceID[registrars.size()]);
    }//end getServiceIDs

    /**
     * Indicate whether any of the group names in the given array match
     * any of the groups of interest.
     *
     * @param possibilities the set of group names to compare to the set
     *                      of groups to discover (must not be null)
     */
    private boolean groupsOverlap(String[] possibilities) {
	/* Match if we're interested in any group, or if we're
	 * interested in none and there are no possibilities.
         */
	if (all_groups)  return true;
	for (int i = 0; i < possibilities.length; i++) {
	    if (groups.contains(possibilities[i]))  return true;
	}//end loop
	return false;
    }//end groupsOverlap

    /** Called at startup and whenever the set of groups to discover is
     *  changed. This method executes the multicast request protocol by
     *  starting the ResponseListener thread to listen for multicast 
     *  responses; and starting a Requestor thread to send out multicast
     *  requests for the set of groups contained in the given Collection.
     */
    private void requestGroups(final Collection req) throws IOException {
	try {
            Security.doPrivileged(new PrivilegedExceptionAction() {
		public Object run() throws Exception {
		    Thread t;
		    synchronized (requestors) {
			if (respondeeThread == null) {
			    respondeeThread = new ResponseListener();
			    respondeeThread.start();
			}//endif
			boolean delayFlag = false;
			if (!initialRequestorStarted) {
			    // only delay the first time
			    delayFlag = true;
			    initialRequestorStarted = true;
			}
			t = new Requestor(collectionToStrings(req),
					  respondeeThread.getPort(),
					  delayFlag);
			requestors.add(t);
		    }//end sync
		    t.start();
		    return null;
		}//run
	    });//end doPrivileged
	} catch (PrivilegedActionException e) {
	    throw (IOException)e.getException();
	}//end try/catch
    }//end requestGroups
    
    private static void prepareSocket(Socket s, DiscoveryConstraints dc)
	throws SocketException
    {
	try {
	    s.setTcpNoDelay(true);
	} catch (SocketException e) {
	    // ignore possible failures and proceed anyway
	}
	try {
	    s.setKeepAlive(true);
	} catch (SocketException e) {
	    // ignore possible failures and proceed anyway
	}
	s.setSoTimeout(dc.getUnicastSocketTimeout(DEFAULT_SOCKET_TIMEOUT));
    }
    
    /**
     * If the lookup service associated with the given UnicastResponse
     * is not in the set of already-discovered lookup services, this method
     * adds it to that set, and each registered listener is notified.
     *
     * @param resp the UnicastResponse associated with the lookup
     *             service to add
     */
    private void maybeAddNewRegistrar(UnicastResponse resp) {
        /* If the group names contained in the given incoming unicast response
         * don't match any of the groups of interest, then don't waste time 
         * performing an unnecessary proxy preparation; simply return.
         */
        if( !groupsOverlap(resp.getGroups()) )  return;

        /* Proxy preparation - 
         *
         * The given incoming unicast response contains a proxy to a lookup
         * service that belongs to at least one of the groups of interest.
         * Before adding that proxy to the managed set of discovered lookup
         * services, and before notifying any of the registered listeners,
         * that proxy should be prepared. This is necessary in this utility
         * because that lookup service may be tested for reachability at
         * some point. Since that test involves a remote call (to getGroups())
         * through the proxy, the proxy should be prepared.
         *
         * The preparation of that proxy is performed inside a doPrivileged
         * block that restores the access control context that was in place
         * when this utility was created. In this way, any code that is
         * executed as a part of preparing the proxy will be executed with
         * no additional permissions beyond the permissions that were granted
         * to the client that created this utility. This is done because the
         * proxy preparer executed below is provided by the deployer and thus
         * can be viewed as an artifact of the client. Therefore, before
         * executing the preparer's code in this utility, the client's
         * Subject should be restored, and the preparer code should be
         * restricted to doing nothing more than the client itself is
         * allowed to do.
         *
         * Note that it's okay to modify the state of the given incoming
         * unicast response here because, prior to modification and storage
         * in the managed set of registrars in this method, it is assumed that
         * that object is not accessed by any other thread.
         */
        try {
	    final ServiceRegistrar srcReg = resp.getRegistrar();
            ServiceRegistrar prepReg
		= (ServiceRegistrar)AccessController.doPrivileged
		    ( securityContext.wrap( new PrivilegedExceptionAction() {
                        public Object run() throws RemoteException {
                            Object proxy = registrarPreparer.prepareProxy
                                                            (srcReg);
                            logger.log(Level.FINEST, "LookupDiscovery - "
                                       +"prepared lookup service proxy: {0}",
                                       proxy);
                            return proxy;
                        }//end run
                    }),//end PrivilegedExceptionAction and wrap
                  securityContext.getAccessControlContext());//end doPrivileged
	    if (prepReg != srcReg) {
		resp = new UnicastResponse(resp.getHost(),
					   resp.getPort(),
					   resp.getGroups(),
					   prepReg);
	    }
	} catch (Exception e) {
            Exception e1 = ( (e instanceof PrivilegedActionException) ? 
                            ((PrivilegedActionException)e).getException() : e);
            logger.log(Level.INFO,
                       "exception while preparing lookup service proxy",
                       e1);
	    return;
	}
        /* Add any newly discovered registrars to the managed set and notify
         * all listeners.
         */
        if(groupsOverlap(resp.getGroups()) &&
           !registrarsEqual(resp,
                            (UnicastResponse) registrars.put
                               (resp.getRegistrar().getServiceID(), resp)))
        {
            /* Time stamp the service ID and store its current sequence 
             * number. The first time stamp associated
             * with the current service ID occurs here. All other time
             * stamps for that service ID will occur when multicast
             * announcements for that service ID arrive (in the
             * AnnouncementListener thread).
             *
             * Note that if the time stamp for the service ID were
             * initialized upon the arrival of the first announcement,
             * rather than here when it is first discovered, the
             * AnnouncementTimerThread would not be able to detect the
             * termination of announcements for the case where the 
             * termination happens to occur between the time the lookup 
             * is first discovered here, and the time the first
             * announcement was supposed to have arrived. This can
             * happen because a multicast request from the client can
             * cause the lookup to be discovered before the first
             * announcement arrives.
             */
            AnnouncementInfo aInfo = new AnnouncementInfo(System.currentTimeMillis(), -1);
            AnnouncementInfo existed = regInfo.putIfAbsent(resp.getRegistrar().getServiceID(),
                     aInfo);
            if (existed != null){
                logger.log(Level.FINE, 
                    "AnnouncementInfo already existed in regInfo map:\n{0}should be:\n{1}",
                    new Object[]{existed, aInfo});
            }
            if(!listeners.isEmpty()) {
                addNotify( listeners,
                        mapRegToGroups(resp.getRegistrar(),resp.getGroups()),
                          DISCOVERED);
            }//endif
        }//endif
    }//end maybeAddNewRegistrar

    /** Determine if any of the already-discovered registrars are no longer
     *  members of any of the groups to discover, and discard those registrars
     *  that are no longer members of any of those groups.
     */
    @SuppressWarnings("unchecked")
    private void maybeDiscardRegistrars() {
        Map<ServiceRegistrar,String[]> groupsMap = new HashMap<ServiceRegistrar,String[]>(registrars.size());
        for(Iterator<UnicastResponse> iter=registrars.values().iterator();iter.hasNext(); ){
            UnicastResponse ent = iter.next();
            if(!groupsOverlap(ent.getGroups())) { // not interested anymore
                groupsMap.put(ent.getRegistrar(),ent.getGroups());
                regInfo.remove(ent.getRegistrar().getServiceID());
                iter.remove(); // remove (srvcID,response) mapping
            }//endif
        }//end loop
        if( !groupsMap.isEmpty() && !listeners.isEmpty() ) {
            addNotify(listeners, groupsMap, DISCARDED);
        }//endif
    }//end maybeDiscardRegistrars

    /**
     * Add a notification task to the pending queue, and wake up the Notifier.
     */
    private void addNotify(List<DiscoveryListener> notifies,
            Map<ServiceRegistrar,String[]> groupsMap, int eventType) 
    {
        pendingNotifies.addLast(new NotifyTask(notifies,
                                               groupsMap,
                                               eventType));
    }//end addNotify

    /** Terminates (interrupts) all currently-running threads. */
    private void nukeThreads() {
        Security.doPrivileged(new PrivilegedAction() {
	    public Object run() {
                if(announcementTimerThread != null) {
                    announcementTimerThread.interrupt();
                }//endif
		synchronized (requestors) {
		    for (Iterator iter = requestors.iterator();
			 iter.hasNext(); )
		    {
			Thread t = (Thread) iter.next();
			t.interrupt();
		    }
		    if (respondeeThread != null)
			respondeeThread.interrupt();
		}
                if(announceeThread != null) {
                    announceeThread.interrupt();
                }//endif
		synchronized (tickets) {
                    terminateTaskMgr();
		    Iterator i = tickets.iterator();
		    while (i.hasNext()) {
			Ticket t = (Ticket) i.next();
			i.remove();
			discoveryWakeupMgr.cancel(t);
		    }
		    if (isDefaultWakeupMgr) {
			// cancelAll should be a no-op in this case,
			// but just be sure.
			discoveryWakeupMgr.cancelAll();
			discoveryWakeupMgr.stop();
		    }
		}//end sync
                if (notifierThread != null) notifierThread.interrupt();
		return null;
	    }//end run
	});//end doPrivileged
    }//end nukeThreads

    /** This method removes all pending and active tasks from the TaskManager
     *  for this instance. It also clears the set of pendingDiscoveries, and
     *  closes all associated sockets.
     */ 
    private void terminateTaskMgr() {
        executor.shutdownNow();
        try {
            executor.awaitTermination(10L, TimeUnit.SECONDS);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
        /* Clear pendingDiscoveries and close all associated sockets */
            for(Iterator iter = pendingDiscoveries.iterator();
                iter.hasNext();)
            {
                Object req = iter.next();
                iter.remove();
                if (req instanceof Socket) {
                    try {
                        ((Socket)req).close();
                    } catch (IOException e) { /* ignore */ }
                }//endif
            }//end loop
            pendingNotifies.clear();
    }//end terminateTaskMgr

    /** After a possible change in the member groups of the 
     *  <code>ServiceRegistrar</code> corresponding to the given 
     *  <code>UnicastResponse</code> parameter, this method
     *  determines whether or not the registrar's member groups have
     *  changed in such a way that either a changed event or a discarded
     *  event is warranted.
     *  <p>
     *  Note that even if the contents of the new set of groups initially 
     *  indicate that the corresponding registrar is a candidate for
     *  a discarded or a changed event, further analysis must be performed.
     *  This is because there is no guarantee that the new set of member
     *  groups have not been "split" across the multicast announcements
     *  sent by the lookup service; and so there is no guarantee that the
     *  contents of the new group set actually reflect a change that warrants
     *  an event. To guarantee that the new group set accurately reflects
     *  the registrar's member groups, this method makes a remote call to
     *  the registrar to retrieve its actual member groups.
     *  <p>
     *  There is one situation where it is not necessary to query the
     *  registrar for its current member groups. That situation is 
     *  when the set of groups input to the <code>newGroups</code> parameter
     *  is equivalent to NO_GROUPS. If that new group set is equivalent
     *  to NO_GROUPS, it is guaranteed that the registrar's member groups
     *  have not been split across the multicast announcements.
     *
     * @param response  instance of <code>UnicastResponse</code> 
     *                  corresponding to the registrar whose current and
     *                  previous member groups are to be compared
     * @param newGroups <code>String</code> array containing the new
     *                  member groups of the registrar corresponding to the 
     *                  <code>response</code> parameter (just after a
     *                  possible change)
     */
    private void maybeSendEvent(UnicastResponse response, String[] newGroups) {
        ServiceRegistrar reg = response.getRegistrar();
        boolean getActual    = true;
        if(newGroups == null) { // newGroups null means get actual groups now
            newGroups = getActualGroups(reg);
            if(newGroups == null) return; // if null, then it was discarded
            getActual = false;
        }//endif

        if(groupSetsEqual(response.getGroups(),newGroups)) return;

        String[] actualGroups = newGroups;
        if( getActual && (newGroups.length > 0) ) {
            actualGroups = getActualGroups(reg);
            if(actualGroups == null) return; // null ==> was already discarded
        }//endif
	
        // Other events may have occured to registrars while we were
        // making our remote call.
        UnicastResponse resp =
            registrars.get(reg.getServiceID());
        if (resp == null) {
            // The registrar was discarded in the meantime. Oh well.
            return;
        }
        notifyOnGroupChange(reg, resp.getGroups(), actualGroups);
    }//end maybeSendEvent

    /** After a possible change in the member groups of the given
     *  <code>ServiceRegistrar</code> parameter, this method compares
     *  the registrar's original set of member groups to its new set
     *  of member groups.
     *  <p>
     *  If the criteria shown below is satisfied, either a discarded event
     *  or a changed event will be sent to any registered listeners. The
     *  criteria is based on whether the old and new groups are equal,
     *  and whether one or more elements of the new group set also belong
     *  to the set of groups to discover (the new groups are "still of
     *  interest"). The criteria is as follows:
     *  <p>
     *  if (old groups and new groups)
     *  <p><ul>
     *       <li> (not equal but stillInterested) --&gt; send a changed event
     *       <li> (!stillInterested)              --&gt; send a discarded event
     *    </ul>
     *  <p>
     *
     * @param reg       instance of <code>ServiceRegistrar</code> 
     *                  corresponding to the registrar whose current and
     *                  previous member groups are to be compared; and 
     *                  whose corresponding service ID is used as the key
     *                  into the various data structures that contain
     *                  pertinent information about that registrar
     * @param oldGroups <code>String</code> array containing the member
     *                  groups of the <code>reg</code> parameter prior to
     *                  being changed
     * @param newGroups <code>String</code> array containing the current
     *                  member groups of the <code>reg</code> parameter
     *                  (just after a possible change)
     */
    private void notifyOnGroupChange(ServiceRegistrar reg,
                                     String[]         oldGroups,
                                     String[]         newGroups)
    {
        boolean equal           = groupSetsEqual(oldGroups,newGroups);
        boolean stillInterested = groupsOverlap(newGroups);
        if(!equal && stillInterested) {
            sendChanged(reg,newGroups);
        } else if(!stillInterested) {
            sendDiscarded(reg,newGroups);
        }//endif
    }//end notifyOnGroupChange

    /** Convenience method that sends a discarded event containing only
     *  one registrar to all registered listeners. This method must be
     *  called from within a block that is synchronized on the registrars
     *  map.
     * 
     *  @param reg       instance of <code>ServiceRegistrar</code> 
     *                   corresponding to the registrar to include in the
     *                   event
     *  @param curGroups <code>String</code> array containing the current
     *                   member groups of the registrar referenced by the 
     *                   <code>reg</code> parameter
     */
    private void sendDiscarded(ServiceRegistrar reg, String[] curGroups) {
        ServiceID srvcID = reg.getServiceID();
        if(curGroups == null) { // discard request is from external source
            UnicastResponse resp = (UnicastResponse)registrars.get(srvcID);
            if(resp == null) return;
            curGroups = resp.getGroups();
        }//endif
        if( registrars.remove(srvcID) != null ) { 
            regInfo.remove(srvcID);
            if( !listeners.isEmpty() ) {
                addNotify( listeners, mapRegToGroups(reg,curGroups), DISCARDED);
            }//endif
        }//endif
    }//end sendDiscarded

    /** Convenience method that sends a changed event containing only
     *  one registrar to all registered listeners that are interested in
     *  such events. This method must be called from within a block that
     *  is synchronized on the registrars map.
     * 
     *  @param reg       instance of <code>ServiceRegistrar</code> 
     *                   corresponding to the registrar to include in the
     *                   event
     *  @param curGroups <code>String</code> array containing the current
     *                   member groups of the registrar referenced by the 
     *                   <code>reg</code> parameter
     */
    private void sendChanged(ServiceRegistrar reg, String[] curGroups) {
        /* replace old groups with new; prevents repeated changed events */
        UnicastResponse resp = registrars.get(reg.getServiceID());
	registrars.put(reg.getServiceID(),
		       new UnicastResponse(resp.getHost(),
					   resp.getPort(),
					   curGroups,
					   resp.getRegistrar()));
        if( !listeners.isEmpty() ) {
            addNotify(listeners, mapRegToGroups(reg,curGroups), CHANGED);
        }//endif
    }//end sendChanged

    /** Creates and returns a deep copy of the input parameter. This method
     *  assumes the input map is a HashMap of the registrar-to-groups mapping;
     *  and returns a clone not only of the map, but of each key-value pair
     *  contained in the mapping.
     *
     * @param groupsMap mapping from a set of registrars to the member groups
     *                  of each registrar 
     * 
     *  @return clone of the input map, and of each key-value pair contained
     *          in the input map
     */
    private Map<ServiceRegistrar, String[]> deepCopy(Map<ServiceRegistrar,String[]> groupsMap) {
        /* clone the input HashMap */
        Map<ServiceRegistrar,String[]> newMap = new HashMap<ServiceRegistrar,String[]>(groupsMap);
        /* clone the values of each mapping in place */
        Set<Map.Entry<ServiceRegistrar,String[]>> eSet = newMap.entrySet();
        for(Iterator<Map.Entry<ServiceRegistrar,String[]>> itr = eSet.iterator(); itr.hasNext(); ) {
            Map.Entry<ServiceRegistrar,String[]> pair = itr.next();
            /* only need to clone the value of the order pair */
            pair.setValue( pair.getValue().clone() );
        }
        return newMap;
    }//end deepCopy

    /** This method retrieves from the given <code>ServiceRegistrar</code>,
     *  the current groups in which that registrar is a member. If the
     *  registrar is un-reachable, then this method will discard the
     *  registrar.
     *
     * @param reg instance of <code>ServiceRegistrar</code> referencing the
     *            registrar whose member groups are to be retrieved and returned
     * 
     *  @return <code>String</code> array containing the current member groups
     *          of the registrar referenced by the <code>reg</code> parameter
     */
    private String[] getActualGroups(final ServiceRegistrar reg) {
        /* The retrieval of the member groups of the given ServiceRegistrar
         * is performed inside a doPrivileged block that restores the access
         * control context that was in place when this utility was created.
         * 
         * This is done because the call to getGroups() below is executed
         * on a proxy to the given ServiceRegistrar; which may be downloaded
         * code supplied by a 3rd party. With respect to downloaded, 3rd party
         * code, it is not desirable to allow such code to execute with more
         * priviledges than the client that created this utility. Therefore,
         * before executing getGroups() on the given proxy, the client's
         * Subject should be restored, and the proxy code should be
         * restricted to doing nothing more than the client itself is
         * allowed to do.
         */
        try {
            return (String[])AccessController.doPrivileged
                     ( securityContext.wrap(new PrivilegedExceptionAction()
                           { public Object run() throws RemoteException {
                                   return reg.getGroups();
                               }//end run
                           }),//end PrivilegedExceptionAction and wrap
                       securityContext.getAccessControlContext());//end doPriv
        } catch(Throwable e) {
            /* A RemoteException, wrapped in a PriviligedActionException,
             * occurred. This means that the reg is unreachable; discard it.
             */
            discard(reg);
            return null;
        }//end try

    }//end getActualGroups

    /** Convenience method that creates and returns a mapping of a single
     *  <code>ServiceRegistrar</code> instance to a set of groups.
     * 
     *  @param reg       instance of <code>ServiceRegistrar</code> 
     *                   corresponding to the registrar to use as the key
     *                   to the mapping
     *  @param curGroups <code>String</code> array containing the current
     *                   member groups of the registrar referenced by the 
     *                   <code>reg</code> parameter; and which is used
     *                   as the value of the mapping
     *
     *   @return <code>Map</code> instance containing a single mapping from
     *           a given registrar to its current member groups
     */
    private Map<ServiceRegistrar,String[]> mapRegToGroups(ServiceRegistrar reg, String[] curGroups) {
        Map<ServiceRegistrar,String[]> groupsMap = new HashMap<ServiceRegistrar,String[]>(1);
        groupsMap.put(reg,curGroups);
        return groupsMap;
    }//end mapRegToGroups

    /**
     * This method is used by the public methods of this class that are
     * specified to throw a <code>NullPointerException</code> when the given
     * array of group names contains one or more <code>null</code> elements;
     * in which case, this method throws a <code>NullPointerException</code>
     * which should be allowed to propagate outward.
     * 
     * @throws java.lang.NullPointerException this exception occurs when
     *         one or more of the elements of the <code>groupArray</code>
     *         parameter is <code>null</code>.
     */
    private static void testArrayForNullElement(String[] groupArray) {
        if(groupArray == null) return;
        for(int i=0;i<groupArray.length;i++) {
            if(groupArray[i] == null) {
                throw new NullPointerException("null element in group array");
            }//endif
        }//end loop
    }//end testArrayForNullElement

    /**
     * Decodes received multicast announcement packet. Constraint checking is
     * delayed.
     */
    private MulticastAnnouncement decodeMulticastAnnouncement(
						    final DatagramPacket pkt)
	throws IOException
    {
	// REMIND: cache recently received announcements to skip re-decoding?
	int pv;
	try {
	    pv = ByteBuffer.wrap(
		pkt.getData(), pkt.getOffset(), pkt.getLength()).getInt();
	} catch (BufferUnderflowException e) {
	    throw new DiscoveryProtocolException(null, e);
	}
	multicastAnnouncementConstraints.checkProtocolVersion(pv);
	final Discovery disco = getDiscovery(pv);

	try {
	    return (MulticastAnnouncement) AccessController.doPrivileged(
		securityContext.wrap(new PrivilegedExceptionAction() {
		    public Object run() throws IOException {
			return disco.decodeMulticastAnnouncement(
			    pkt,
			    multicastAnnouncementConstraints.
				getUnfulfilledConstraints(),
				true);
		    }
		}), securityContext.getAccessControlContext());
	} catch (PrivilegedActionException e) {
	    throw (IOException) e.getCause();
	}
    }

    /*
     * Restore the original context while checking constraints.
     */
    private void checkAnnouncementConstraints(final MulticastAnnouncement ann)
	throws IOException
    {
	try {
	    AccessController.doPrivileged(
		securityContext.wrap(new PrivilegedExceptionAction() {
		    public Object run() throws IOException {
			ann.checkConstraints();
			return null;
		    }
	    }), securityContext.getAccessControlContext());
	} catch (PrivilegedActionException e) {
	    throw (IOException) e.getCause();
	}
    }
    
    /**
     * Encodes outgoing multicast requests based on protocol in use, applying
     * configured security constraints (if any).
     */
    private DatagramPacket[] encodeMulticastRequest(final MulticastRequest req)
	throws IOException
    {
	// REMIND: cache latest request to skip re-encoding
	final Discovery disco = getDiscovery(
	    multicastRequestConstraints.chooseProtocolVersion());
	final List packets = new ArrayList();
	AccessController.doPrivileged(
	    securityContext.wrap(new PrivilegedAction() {
		public Object run() {
		    EncodeIterator ei = disco.encodeMulticastRequest(
			req, 
			multicastRequestConstraints.getMulticastMaxPacketSize(
			    DEFAULT_MAX_PACKET_SIZE),
			multicastRequestConstraints.getUnfulfilledConstraints()
		    );
		    while (ei.hasNext()) {
			try {
			    packets.addAll(Arrays.asList(ei.next()));
			} catch (Exception e) {
			    logger.log(
				(e instanceof UnsupportedConstraintException) ?
				    Levels.HANDLED : Level.INFO,
				"exception encoding multicast request", e);
			}
		    }
		    return null;
		}
	    }), securityContext.getAccessControlContext());

	if (packets.isEmpty()) {
	    throw new DiscoveryProtocolException("no encoded requests");
	}
	return (DatagramPacket[]) packets.toArray(
	    new DatagramPacket[packets.size()]);
    }
    
    private void restoreContextAddTask(final Runnable t) {
	AccessController.doPrivileged(
	    securityContext.wrap(new PrivilegedAction() {
		public Object run() {
                    executor.execute(t);
		    return null;
		    }
		}),
	    securityContext.getAccessControlContext());
    }

    private Ticket restoreContextScheduleRunnable(final UnicastDiscoveryTask t)
    {
	return (Ticket) AccessController.doPrivileged(
	    securityContext.wrap(new PrivilegedAction() {
		public Object run() {
		    return discoveryWakeupMgr.schedule(
			    System.currentTimeMillis() +
			    (long) (Math.random() * unicastDelayRange),
			    new Runnable() {
				public void run() {
                                    executor.execute(t);
				}
			    }
			);
		    }
		}),
	    securityContext.getAccessControlContext());
    }
    /**
     * Performs unicast discovery over given socket based on protocol in use,
     * applying configured security constraints (if any).
     */
    private UnicastResponse
	doUnicastDiscovery(
	    final Socket socket,
	    final DiscoveryConstraints unicastDiscoveryConstraints,
	    final Discovery disco)
	throws IOException, ClassNotFoundException
    {
	try {
	    return AccessController.doPrivileged(
		securityContext.wrap(new PrivilegedExceptionAction<UnicastResponse>() {
		    public UnicastResponse run() throws Exception {
			return disco.doUnicastDiscovery(
			    socket, 
			    unicastDiscoveryConstraints.getUnfulfilledConstraints(),
			    null,
			    null,
			    null);
		    }
		}), securityContext.getAccessControlContext());
	} catch (PrivilegedActionException e) {
	    Throwable t = e.getCause();
	    if (t instanceof IOException) {
		throw (IOException) t;
	    } else if (t instanceof ClassNotFoundException) {
		throw (ClassNotFoundException) t;
	    } else {
		throw new AssertionError(t);
	    }
	}
    }
    
    private UnicastResponse doUnicastDiscovery(
	    final Socket socket,
	    final DiscoveryConstraints unicastDiscoveryConstraints)
	throws IOException, ClassNotFoundException
    {
	Discovery disco =
	    getDiscovery(unicastDiscoveryConstraints.chooseProtocolVersion());
	return doUnicastDiscovery(socket, unicastDiscoveryConstraints, disco);
    }

    /**
     * Returns Discovery instance for the given version, or throws
     * DiscoveryProtocolException if the version is unsupported.
     */
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
    
    /**
     * Holder class for the time and sequence number of the last
     * received announcement. The regInfo map contains instances of this
     * class as values.
     */
    private static class AnnouncementInfo {
	private final long tStamp;
	private final long seqNum;
	private AnnouncementInfo(long tStamp, long seqNum) {
	    this.tStamp = tStamp;
	    this.seqNum = seqNum;
	}

        /**
         * @return the tStamp
         */
        long gettStamp() {
            return tStamp;
        }

        /**
         * @return the seqNum
         */
        long getSeqNum() {
            return seqNum;
        }
        
        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append("Timestamp: ");
            sb.append(tStamp);
            sb.append('\n');
            sb.append("Sequence Number: ");
            sb.append(seqNum);
            sb.append('\n');
            return sb.toString();
        }
    }
}
