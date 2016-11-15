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

import org.apache.river.config.Config;
import org.apache.river.discovery.Discovery;
import org.apache.river.discovery.DiscoveryConstraints;
import org.apache.river.discovery.UnicastResponse;
import org.apache.river.discovery.internal.MultiIPDiscovery;
import org.apache.river.logging.Levels;
import org.apache.river.logging.LogUtil;
import org.apache.river.thread.RetryTask;
import org.apache.river.thread.WakeupManager;
import java.io.IOException;
import java.lang.reflect.Method;
import java.net.InetAddress;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.BlockingDeque;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RunnableFuture;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.jini.config.Configuration;
import net.jini.config.ConfigurationException;
import net.jini.config.EmptyConfiguration;
import net.jini.config.NoSuchEntryException;
import net.jini.core.constraint.InvocationConstraints;
import net.jini.core.constraint.MethodConstraints;
import net.jini.core.constraint.RemoteMethodControl;
import net.jini.core.discovery.LookupLocator;
import net.jini.core.lookup.ServiceRegistrar;
import net.jini.security.BasicProxyPreparer;
import net.jini.security.ProxyPreparer;
import org.apache.river.thread.ExtensibleExecutorService;
import org.apache.river.thread.ExtensibleExecutorService.RunnableFutureFactory;
import org.apache.river.thread.NamedThreadFactory;

/**
 * This package private superclass of LookupLocatorDiscovery exists for 
 * safe construction of LookupLocatorDiscovery's state, while retaining 
 * backward compatibility of the API.
 * 
 * See www.securecoding.cert.org TSM01-J and OBJ11-J
 * 
 */
abstract class AbstractLookupLocatorDiscovery implements DiscoveryManagement,
                                               DiscoveryLocatorManagement {
    
    /* Name of this component; used in config entry retrieval and the logger.*/
    private static final String COMPONENT_NAME
                                 = "net.jini.discovery.LookupLocatorDiscovery";
    /* Logger used by this utility. */
    private static final Logger logger = Logger.getLogger(COMPONENT_NAME);
    /** Maximum number of concurrent tasks that can be run in any task
     *  manager created by this class.
     */
    private static final int MAX_N_TASKS = 15;
    /** Default timeout to set on sockets used for unicast discovery. */
    private static final int DEFAULT_SOCKET_TIMEOUT = 1*60*1000;
    /** LookupLocator.getRegistrar method, used for looking up client
     *  constraints of contained lookup locators.
     */
    private static final Method getRegistrarMethod;
    static {
	try {
	    getRegistrarMethod = LookupLocator.class.getDeclaredMethod(
				     "getRegistrar", new Class[0]);
	} catch (NoSuchMethodException e) {
	    throw new AssertionError(e);
	}
    }

    /** ExecutorService for the discovery tasks. On the first attempt to
     *  discover each locator, the tasks used to perform those discoveries
     *  are managed by this <code>ExecutorService</code> so that the number of
     *  concurrent threads can be bounded. If one or more of those attempts
     *  fails, a <code>WakeupManager</code> is used (through the use of a
     *  <code>RetryTask</code>) to schedule - at a later time (employing a
     *  "backoff strategy") - the re-execution of each failed task in this
     *  <code>ExecutorService</code>.
     */
    private final ExecutorService discoveryExecutor;
    /** Wakeup manager for the discovery tasks. For any locator, after
     *  an initial failure to discover the locator, the task used to
     *  perform all future discovery attempts is managed by this
     *  <code>WakeupManager</code>; which schedules the re-execution of
     *  the failed task - in the task manager - at various times in the
     *  future until the locator is successfully discovered. This wakeup
     *  manager is supplied to the <code>RetryTask</code>) that performs
     *  the actual discovery attempt(s) so that when termination of this
     *  lookup locator discovery utility is requested, all tasks scheduled
     *  for retry by this wakeup manager can be cancelled.
     */
    private final WakeupManager discoveryWakeupMgr;
    /** Stores LookupLocators that have not been discovered yet. */ 
    private final Set<LocatorReg> undiscoveredLocators = Collections.newSetFromMap(new ConcurrentHashMap<LocatorReg,Boolean>());
    /** Stores LookupLocators that have been discovered */
    private final List<LocatorReg> discoveredLocators = new CopyOnWriteArrayList<LocatorReg>();
    /** Thread that handles pending notifications. */
    private final Notifier notifierThread = new Notifier();
    /** Notifications to be sent to listeners. */
    private final BlockingDeque<NotifyTask> pendingNotifies = new LinkedBlockingDeque<NotifyTask>();
    /** Stores DiscoveryListeners **/
    private final List<DiscoveryListener> listeners = new CopyOnWriteArrayList<DiscoveryListener>(); // sync(this) only for add and remove to avoid duplicates.
    /** Flag indicating whether or not this class is still functional. */
    private volatile boolean terminated = false; // sync(this)
    /* Preparer for the proxies to the lookup services that are discovered
     * and used by this utility.
     */
    private final ProxyPreparer registrarPreparer;
    /* Utility for participating in version 2 of the unicast discovery
     * protocol.
     * TODO: investigate why this field isn't used.
     */
    private final Discovery protocol2 = Discovery.getProtocol2(null);
    /* 
     * Controls how long to wait before attempting unicast discovery, on
     * startup.
     */
    private final long initialUnicastDelayRange;
    /*
     * Flag which indicates if discoverLocators was called during
     * initialUnicastDelayRange delay.
     */
    private volatile boolean discoverLocatorsCalled = false;
    /** Wrapper class in which each instance corresponds to a lookup service
     *  to discover via unicast discovery.
     */
    private class LocatorReg {
        private ServiceRegistrar proxy = null; // sync(this)
        public final LookupLocator l;
        private String[] memberGroups = null; // sync(this)
	private boolean discarded = false; // sync(this)

	private static final long MIN_RETRY = 15000;
        private final long[] sleepTime = { 5*1000, 10*1000, 20*1000,
	                                  30*1000, 60*1000, 
                                          2*60*1000, 4*60*1000,
                                          8*60*1000, 15*60*1000};
        private int tryIndx = 0; // sync(this)
        private long nextTryTime; // sync(this)

	public LocatorReg(LookupLocator l) {
	    this.l = l;
            nextTryTime = System.currentTimeMillis();
	}//end constructor

        /** Accessor that returns the absolute time at which the next
         *  discovery attempt should be made after the previous attempt
         *  has failed to find the desired lookup service.
         */
        public synchronized long getNextTryTime() {
            return nextTryTime;
        }//end getNextTryTime

        /** Computes the time (in milliseconds) used to determine when it
         *  is allowable -- after a previous failure -- to again attempt
         *  unicast discovery of the lookup service referenced in this class.
         *
         *  Since this method is called multiple times for a particular lookup
         *  service only when there is difficulty discovering that lookup
         *  service, the value computed by this method increases in a
         *  graduated manner - increasing the amount of time to wait before
         *  the next discovery attempt should be made - upon each invocation,
         *  eventually reaching a maximum time interval over which discovery
         *  is re-tried. In this way, the network is not flooded with unicast
         *  discovery requests referencing a lookup service that may not be
         *  available for quite some time (if ever).
	 */
	public void calcNextTryTime() {
            synchronized (this){
                nextTryTime = System.currentTimeMillis() + sleepTime[tryIndx];
                if(tryIndx < sleepTime.length-1)  tryIndx++;
            }
	}//end calcNextTryTime

        /** This method gets called only from the public discard() method.
         *  The purpose of this method is to delay the next discovery attempt.
	 */
	public void delayNextTryTime()  {
            synchronized (this){
                discarded = true;
                tryIndx = 2;
            }
	}

        /** Initiates unicast discovery of the lookup service referenced 
         *  in this class.
         */
	public boolean tryGetProxy() {
            if (getProxy() != null ) {
                throw new IllegalArgumentException
                                 ("LookupLocator has been discovered already");
            }
	    InvocationConstraints ic = InvocationConstraints.EMPTY;
	    if (l instanceof RemoteMethodControl) {
		MethodConstraints mc =
		    ((RemoteMethodControl) l).getConstraints();
		if (mc != null) {
		    ic = mc.getConstraints(getRegistrarMethod);
		}
	    }
	    try {
                doUnicastDiscovery(l, ic);
		return true;
	    } catch (Throwable e) {
                if( logger.isLoggable(Level.INFO) ) {
                    try {
                        LogUtil.logThrow(logger, 
                            Level.INFO,
                            this.getClass(),
                            "tryGetProxy",
                            "exception occured during unicast discovery to "
                            + "{0}:{1,number,#} with constraints {2}",
                             new Object[] {
                                l.getHost(),
                                Integer.valueOf(l.getPort()),
                                ic
                             },
                             e);
                    } catch (Throwable t) {
                        // Ignore
                    }
                }//endif
		calcNextTryTime();//discovery failed; try again even later
		return false;
	    }
	}//end tryGetProxy

       /** This method employs the unicast discovery protocol to discover
        *  the registrar having <code>LookupLocator</code> equal to the value
        *  contained in the <code>locator</code> parameter of this class.
	*/
        private void doUnicastDiscovery(LookupLocator locator,
					InvocationConstraints ic)
	    throws IOException, ClassNotFoundException
        {
	    UnicastResponse resp = new MultiIPDiscovery() {
                @Override
		protected UnicastResponse performDiscovery(
							Discovery disco,
							DiscoveryConstraints dc,
							Socket s)
		throws IOException, ClassNotFoundException
		{
		    return disco.doUnicastDiscovery(
					    s,
					    dc.getUnfulfilledConstraints(),
					    null,
					    null,
					    null);
		    
		}
		
                @Override
		protected void socketCloseException(IOException e) {
		   logger.log(Level.FINEST,
		       "IOException on socket close upon "
		       + "completion of unicast discovery",
		       e);	
		}
		
                @Override
		protected void singleResponseException(Exception e,
						       InetAddress addr,
						       int port)
		{
		    logger.log(
			Levels.HANDLED,
			"Exception occured during unicast discovery " +
			addr + ":" + port, e);
		}
		
	    }.getResponse(locator.scheme(), locator.getHost(), locator.getPort(), ic);
	    	    
	    /* Proxy preparation */
                ServiceRegistrar proxy = (ServiceRegistrar)registrarPreparer.prepareProxy
                                                            (resp.getRegistrar());
                String [] memberGroups = resp.getGroups();
            
                logger.log(Level.FINEST, "LookupLocatorDiscovery - prepared "
                           +"lookup service proxy: {0}", proxy);
                synchronized (this){
                    this.proxy = proxy;
                    this.memberGroups = memberGroups;
                }
               
            
        }//end doUnicastDiscovery

	private void queueDiscoveryTask() {
            boolean discard = false;
            synchronized (this){
                if (discarded) {
                    discarded = false;
                    discard = true;
                }
            }
	    if (discard) {
		// We need to delay this discovery
		discoveryWakeupMgr.schedule(
			System.currentTimeMillis() + MIN_RETRY,
		    new Runnable() {
			public void run() {
                            DiscoveryTask task = new DiscoveryTask(LocatorReg.this,
				discoveryExecutor, discoveryWakeupMgr);
			    discoveryExecutor.submit(task);
			}
		    }
		);
	    } else {
                DiscoveryTask task = new DiscoveryTask(this,
			discoveryExecutor, discoveryWakeupMgr);
		discoveryExecutor.submit(task);
	    }
	}
	
        /** Returns true if the locators are equal. */
	public boolean equals(Object obj) {
            if( !(obj instanceof LocatorReg) ) return false;
	    return l.equals(((LocatorReg)obj).l);
	}//end equals

        /** Returns the hash code of the locator referenced in this class. */
	public int hashCode() {
	    return l.hashCode();
	}//end hashCode

        /**
         * @return the proxy
         */
        private synchronized ServiceRegistrar getProxy() {
            return proxy;
        }

        /**
         * @return the memberGroups
         */
        private synchronized String[] getMemberGroups() {
            return memberGroups;
        }
    }//end class LocatorReg

    /** Data structure containing task data processed by the Notifier Thread */
    private static class NotifyTask {
	/** The listeners to notify */
	public final List<DiscoveryListener> listeners;
	/** Map of discovered registrars to groups in which each is a member */
	public final Map<ServiceRegistrar, String[]> groupsMap;
	/** True if discarded, else discovered */
	public final boolean discard;
	public NotifyTask(List<DiscoveryListener> listeners,
                          Map<ServiceRegistrar, String[]> groupsMap,
			  boolean discard)
	{
	    this.listeners = listeners;
	    this.groupsMap = groupsMap;
	    this.discard   = discard;
	}
    }//end class NotifyTask

    /** Thread that retrieves data structures of type NotifyTask from a
     *  queue and, based on the contents of the data structure, sends the
     *  appropriate event (discovered/discarded) to each registered listener.
     *  <p>
     *  Only 1 instance of this thread is run.
     */
    private class Notifier extends Thread {
        // In case client code catches and resets interrupt.
        private volatile boolean interrupted = false;
        
	public Notifier() {
	    super("event notifier");
	    setDaemon(false);
	}//end constructor
        
        public void interrupt(){
            super.interrupt();
            interrupted = true;
        }

	public void run() {
            logger.finest("LookupLocatorDiscovery - Notifier thread started");
	    while (!interrupted) {
		NotifyTask task;
		try {
                    task = pendingNotifies.takeFirst(); // Blocks waiting for food.
                } catch (InterruptedException ex) {
                    return;
                }
                boolean firstListener = true;
		for(Iterator<DiscoveryListener> iter = task.listeners.iterator();iter.hasNext();){
		    DiscoveryListener l = iter.next();
		    DiscoveryEvent e =
                        new DiscoveryEvent(AbstractLookupLocatorDiscovery.this,
                                           deepCopy(task.groupsMap) );
                    /* Log the event info about the lookup(s) */
                    if(firstListener && (logger.isLoggable(Level.FINEST)) ) {
                        String eType = (task.discard ? 
                                                    "discarded":"discovered");
                        ServiceRegistrar[] regs = e.getRegistrars();
                        int len = regs.length;
                        logger.log(Level.FINEST, "{0} event  -- {1} lookup(s)",
                                new Object[]{eType, len});
                        Map groupsMap = e.getGroups();
                        for(int i=0;i<len;i++) {
                            LookupLocator loc = null;
                            try {
                                loc = regs[i].getLocator();
                            } catch (Throwable ex) { /* ignore */ }
                            String[] groups = (String[])groupsMap.get(regs[i]);
                            logger.log(Level.FINEST, "    {0} locator  = {1}", 
                                    new Object[]{eType, loc});
                            if(groups.length == 0) {
                                logger.log(Level.FINEST,
                                        "    {0} group    = NO_GROUPS", eType);
                            } else {
                                for(int j=0;j<groups.length;j++) {
                                    logger.log(Level.FINEST,
                                            "    {0} group[{1}] = {2}",
                                            new Object[]{eType, j, groups[j]});
                                }//end loop
                            }//endif(groups.length)
                        }//end loop
                    }//endif(firstListener && isLoggable(Level.FINEST)
                    try {
			if (task.discard) {
			    l.discarded(e);
			} else {
			    l.discovered(e);
                	}//endif
                    } catch (Throwable t) {
                	logger.log(Levels.HANDLED, "a discovery listener failed to process a " +
                		(task.discard ? "discard" : "discover") + " event", t);
                    }
		}//end loop
	    }//end loop
	}//end run
    }//end class Notifier

    /** Task which retrieves elements from the set of undiscoveredLocators
     *  and attempts, through the unicast discovery protocol, to discover
     *  the lookup service having the LookupLocator referenced by the element.
     *  If a particular instance of this class fails to find the lookup
     *  service that it references, this task will be rescheduled to be
     *  executed again at a later time, using a "backoff strategy" as defined
     *  by the method <code>org.apache.river.thread.RetryTask.retryTime</code>.
     *
     *  @see org.apache.river.thread.RetryTask
     *  @see org.apache.river.thread.WakeupManager
     */
    private class DiscoveryTask extends RetryTask {
        private final LocatorReg reg;
        public DiscoveryTask(LocatorReg reg,
                             ExecutorService executor,
                             WakeupManager wakeupMgr)
        {
            super(executor,wakeupMgr);
            this.reg = reg;
	}//end constructor

        /** Executes the current instance of this task once, attempting to
         *  discover - through unicast discovery - the lookup service
         *  referenced in that instance. Upon successfully discovering the
         *  indicated lookup service, this method returns <code>true</code>
         *  and the current instance of this task is not executed again.
         *  For each unsuccessful discovery attempt made by this method
         *  for the current instance of this task, this method returns
         *  <code>false</code>, which causes the task to be scheduled by
         *  the <code>WakeupManager</code> to be executed again at a later
         *  time as indicated by the value returned by <code>retryTime</code>.
         */
        public boolean tryOnce() {
            logger.finest("LookupLocatorDiscovery - DiscoveryTask started");
            if (terminated) return true;
            /* Locators may have been removed (ex. removeLocators or
             * setLocators) between the time they were added to the map,
             * and the time this task is finally executed. Determine if
             * this task should continue.
             */
            if( undiscoveredLocators.isEmpty() ) {
                logger.finest("LookupLocatorDiscovery - DiscoveryTask "
                              +"completed");
                return true;//true ==> done. Don't queue retry.
            }//endif
            if(!undiscoveredLocators.contains(reg)) {
                logger.finest("LookupLocatorDiscovery - DiscoveryTask "
                              +"completed");
                return true;//already removed, true ==> don't queue retry
            }
            /* Use the unicast discovery protocol to perform the actual
             * discovery. Note that since this process involves remote,
             * interprocess (socket) communication, it is important that
             * this processing be performed outside of the sync block.
             */
            boolean noRetry = regTryGetProxy(reg);//t -> done, f -> queue retry
            if (terminated) return true;
            
            if(noRetry) {
                logger.finest("LookupLocatorDiscovery - DiscoveryTask "
                              +"completed");
            } else {
                logger.finest("LookupLocatorDiscovery - DiscoveryTask "
                              +"failed, will retry later");
            }//endif
            return noRetry;

	}//end tryOnce

        /** Returns the next absolute time (in milliseconds) at which another
         *  attempt to discover the lookup service referenced in this class
         *  should be made.
         *  
         *  Overrides the version of this method in the parent class.
         */
        public long retryTime() {
            return reg.getNextTryTime();
        }//end retryTime

    }//end class DiscoveryTask

    /**
     * Creates an instance of this class
     */
    AbstractLookupLocatorDiscovery() {
        this(initEmptyConfig());
    }//end constructor

    /**
     * Constructs a new lookup locator discovery object, having the given
     * <code>Configuration</code>.
     * <p>
     * For each managed <code>LookupLocator</code>, unicast discovery is
     * performed to obtain a <code>ServiceRegistrar</code> proxy for that
     * lookup service.
     * 
     * @param config   an instance of <code>Configuration</code>, used to
     *                 obtain the objects needed to configure the current
     *                 instance of this class
     *
     * @throws net.jini.config.ConfigurationException indicates an exception
     *         occurred while retrieving an item from the given
     *         <code>Configuration</code>
     * 
     * @throws java.lang.NullPointerException <code>null</code> is input
     *         for the configuration
     */
    AbstractLookupLocatorDiscovery(Configuration config)
                                                throws ConfigurationException
    {
        this(init(config));
    }//end constructor
    
    private AbstractLookupLocatorDiscovery(Initializer init){
        registrarPreparer = init.registrarPreparer;
        discoveryExecutor = new ExtensibleExecutorService(
                init.discoveryTaskMgr,
                new FutureFactory()
        );
        discoveryWakeupMgr = init.discoveryWakeupMgr;
        initialUnicastDelayRange = init.initialUnicastDelayRange.longValue();
    }
    
    private static class FutureFactory implements RunnableFutureFactory {

        @Override
        public <T> RunnableFuture<T> newTaskFor(Runnable r, T value) {
            if (r instanceof RunnableFuture) return (RunnableFuture<T>) r;
            return new FutureTask<T>(r, value);
        }

        @Override
        public <T> RunnableFuture<T> newTaskFor(Callable<T> c) {
            return new FutureTask<T>(c); 
        }
        
    }

    /**
     * Add a DiscoveryListener to the listener set. The listener's
     * discovered method gets called right way with an array of 
     * ServiceRegistrars that have already been discovered, and will
     * be called in the future whenever additional lookup services
     * are discovered.
     *
     * @param l the new DiscoveryListener to add
     *
     * @throws java.lang.NullPointerException this exception occurs when
     *         <code>null</code> is input to the listener parameter
     *         <code>l</code>.
     *
     * @throws java.lang.IllegalStateException this exception occurs when
     *         this method is called after the <code>terminate</code>
     *         method has been called.
     * 
     * @see #removeDiscoveryListener
     */
    public void addDiscoveryListener(DiscoveryListener l) {
        if(l == null) {
            throw new NullPointerException("can't add null listener");
        }
	
        if (terminated) throw new IllegalStateException("discovery terminated");
        synchronized (this){ // sync to make atomic with remove
            if(listeners.contains(l)) return; //already have this listener
            listeners.add(l); // Small window where it's possible to add duplicates.
        }
        Map<ServiceRegistrar, String[]> groupsMap 
                = new HashMap<ServiceRegistrar, String[]>(discoveredLocators.size());
        Iterator<LocatorReg> iter = discoveredLocators.iterator();
        while (iter.hasNext()) {
            LocatorReg reg = iter.next();
            groupsMap.put(reg.getProxy(), reg.getMemberGroups());
        }//end loop
        List<DiscoveryListener> list = new ArrayList<DiscoveryListener>(1);
        list.add(l);
        if (!groupsMap.isEmpty()) addNotify(list, groupsMap, false);
    }//end addDiscoveryListener

    /**
     * Remove a DiscoveryListener from the listener set. It does
     * nothing if the DiscoveryListener does not exist in the 
     * the listener set.
     *
     * @param l the existing DiscoveryListener to remove
     *
     * @throws java.lang.IllegalStateException this exception occurs when
     *         this method is called after the <code>terminate</code>
     *         method has been called.
     * 
     * @see #addDiscoveryListener
     */
    public void removeDiscoveryListener(DiscoveryListener l) {
        if (terminated) throw new IllegalStateException("discovery terminated");
        synchronized (this){ // sync to avoid duplicates, makes add atomic
            listeners.remove(l);
        }
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
        if (terminated) throw new IllegalStateException("discovery terminated");
        if(discoveredLocators.isEmpty()) return new ServiceRegistrar[0];
        return buildServiceRegistrar();
    }//end getRegistrars

    /**
     * Removes an instance of <code>ServiceRegistrar</code> from the
     * managed set of lookup services, making the corresponding lookup
     * service eligible for re-discovery. This method takes no action if
     * the parameter input to this method is <code>null</code>, or if it
     * does not match (using <code>equals</code>) any of the elements in
     * the managed set.
     *
     * @param proxy the instance of <code>ServiceRegistrar</code> to discard
     *              from the managed set of lookup services
     *
     * @throws java.lang.IllegalStateException this exception occurs when
     *         this method is called after the <code>terminate</code>
     *         method has been called.
     * 
     * @see net.jini.core.lookup.ServiceRegistrar
     * @see net.jini.discovery.DiscoveryManagement#discard
     */
    public void discard(ServiceRegistrar proxy) {
        if (terminated) {
            throw new IllegalStateException("discovery terminated");
        }
        if(proxy == null) return;
        LookupLocator lct = findRegFromProxy(proxy);
        if(lct == null) return;
        /* Remove locator from the set of already-discovered locators */
        LocatorReg reg = removeDiscoveredLocator(lct);
        if (reg != null){
            /* Prepare the information for the discarded event */
            Map groupsMap = new HashMap(1);
            groupsMap.put(reg.getProxy(), reg.getMemberGroups());
            /* Prepare the discarded locatorReg for re-discovery */
            synchronized (reg){
                reg.proxy = null;
                reg.memberGroups = null;
                reg.delayNextTryTime();
            }
            addAndQueueDiscoveryTaskIfAbsent(reg);//put discarded reg back in the not-discovered map
            /* Send a discarded event to all registered listeners */
            if(!listeners.isEmpty()) {
                addNotify(listeners, groupsMap, true);
            }//endif
        }
    }//end discard

    /**
     * Terminates all threads, ending all locator discovery processing being
     * performed by the current instance of this class.
     * <p>
     * After this method has been invoked, no new lookup services will
     * be discovered, and the effect of any new operations performed
     * on the current instance of this class are undefined.
     *
     * @see net.jini.discovery.DiscoveryManagement#terminate
     */
    public void terminate() {
        synchronized (this){
            if(terminated) return;
            terminated = true;
        }
        terminateTaskMgr();
        boolean interrupted = false;
        // Don't leave DiscoveryListener's hanging.
        while (!pendingNotifies.isEmpty()) {
            try {
                Thread.sleep(1000L);
            } catch (InterruptedException ex) {
                interrupted = true;
            }
        }
        notifierThread.interrupt();
        if (interrupted) Thread.currentThread().interrupt();
    }//end terminate

    /**
     * Returns an array consisting of the elements of the managed set
     * of locators; that is, instances of <code>LookupLocator</code> in
     * which each instance corresponds to a specific lookup service to
     * discover. The returned set will include both the set of 
     * <code>LookupLocator</code>s corresponding to lookup services 
     * that have already been discovered as well as the set of those
     * that have not yet been discovered. If the managed set of locators
     * is empty, this method will return the empty array. This method
     * returns a new array upon each invocation.
     *
     * @return <code>LookupLocator</code> array consisting of the elements
     *         of the managed set of locators
     *
     * @throws java.lang.IllegalStateException this exception occurs when
     *         this method is called after the <code>terminate</code>
     *         method has been called.
     * 
     * @see net.jini.discovery.DiscoveryLocatorManagement#getLocators
     * @see #setLocators
     */
    public LookupLocator[] getLocators() {
        if (terminated) throw new IllegalStateException("discovery terminated");
        /* Includes the set of already-discovered lookup services and
         * the set of not-yet-discovered lookup services.
         */
        List<LookupLocator> locators = new LinkedList<LookupLocator>();
        /* Retrieve the locators of the already-discovered lookup services */
	Iterator iter = discoveredLocators.iterator();
	while(iter.hasNext()) {
	    locators.add(((LocatorReg)iter.next()).l);
        }//end loop
        /* Append the locators of the not-yet-discovered lookup services */
        iter = undiscoveredLocators.iterator();
	while(iter.hasNext()) {
	    locators.add(((LocatorReg)iter.next()).l);
        }//end loop
	return locators.toArray(new LookupLocator[locators.size()]);
    }//end getLocators

    /**
     * Adds a set of locators to the managed set of locators. Elements in the
     * input set that duplicate (using the <code>LookupLocator.equals</code>
     * method) elements already in the managed set will be ignored. If the
     * empty array is input, the managed set of locators will not change.
     *
     * @param locators <code>LookupLocator</code> array consisting of the
     *                 locators to add to the managed set.
     * 
     * @throws java.lang.NullPointerException this exception occurs when
     *         either <code>null</code> is input to the <code>locators</code>
     *         parameter, or one or more of the elements of the
     *         <code>locators</code> parameter is <code>null</code>.
     *
     * @throws java.lang.IllegalStateException this exception occurs when
     *         this method is called after the <code>terminate</code>
     *         method has been called.
     * 
     * @see net.jini.discovery.DiscoveryLocatorManagement#addLocators
     * @see #removeLocators
     */
    public void addLocators(LookupLocator[] locators) {
        testSetForNull(locators);
        if (terminated) {
            throw new IllegalStateException("discovery terminated");
        }
	discoverLocators(locators);
    }//end addLocators

     /**
     * Replaces all of the locators in the managed set with locators from
     * a new set, and discards any already-discovered lookup service that
     * corresponds to a locator that is removed from the managed set
     * as a result of an invocation of this method. For any such lookup
     * service that is discarded, a discard notification is sent; and that
     * lookup service will not be eligible for re-discovery (assuming it is
     * not currently eligible for discovery through other means, such as
     * group discovery).
     * <p>
     * If the empty array is input, locator discovery will cease until this
     * method is invoked with an input parameter that is non-<code>null</code>
     * and non-empty.
     *
     * @param locators <code>LookupLocator</code> array consisting of the 
     *                 locators that will replace the current locators in the
     *                 managed set.
     *
     * @throws java.lang.NullPointerException this exception occurs when
     *         either <code>null</code> is input to the <code>locators</code>
     *         parameter, or one or more of the elements of the
     *         <code>locators</code> parameter is <code>null</code>.
     *
     * @throws java.lang.IllegalStateException this exception occurs when
     *         this method is called after the <code>terminate</code>
     *         method has been called.
     * 
     * @see net.jini.discovery.DiscoveryLocatorManagement#setLocators
     * @see #getLocators
     */
    public void setLocators(LookupLocator[] locators) {
        testSetForNull(locators);
        if (terminated) throw new IllegalStateException("discovery terminated");

        Map groupsMap = new HashMap(1);
        /* From the set of already-discovered locators, remove each 
         * element that is NOT in the input set of locators.
         */
        Iterator<LocatorReg> iter = discoveredLocators.iterator();
        List<LocatorReg> remove = new LinkedList<LocatorReg>();
        while(iter.hasNext()) {
            LocatorReg reg = iter.next();
            if(!isArrayContains(locators, reg.l)) {
                remove.add(reg);
                groupsMap.put(reg.getProxy(), reg.getMemberGroups());
            }//endif
        }//end loop
        discoveredLocators.removeAll(remove);
        /* From the set of yet-to-be-discovered locators, remove each 
         * element that is NOT in the input set of replacement locators.
         * 
         * Note that if the discovery task is currently attempting to
         * discover a locator from this set, and if that locator is not
         * contained in the given input set of replacement locators (that
         * is, it is no longer desired that that locator be discovered),
         * then the discovery task, when it completes (either successfully
         * or un-successfully) the attempt to discover that locator, will
         * end all discovery processing with respect to the affected
         * locator.
         *
         * To inform the discovery task -- upon its return from the
         * unicast discovery process -- of the desire to terminate all
         * discovery processing for that particular locator, the element
         * in the set of undiscoveredLocators that corresponds to that
         * locator is removed. This means that if the discovery attempt
         * failed, the locator will no longer be considered one of the
         * yet-to-be-discovered locators; and if the attempt succeeded,
         * prevents the locator from being placed in the set of 
         * already-discovered locators. It also prevents any discarded
         * or discovered events from being sent.
         */
        iter = undiscoveredLocators.iterator();
        while(iter.hasNext()) {
            LocatorReg reg = (LocatorReg)iter.next();
            if(!isArrayContains(locators, reg.l))  {
                iter.remove();
            }//endif
        }//end loop
        /* Initiate discovery process for any new, un-discovered locators*/
        discoverLocators(locators);
        /* Send a discarded event to all registered listeners for any
         * locators that were removed by this method.
         */
        if(!groupsMap.isEmpty() && !listeners.isEmpty()) {
            addNotify( listeners, groupsMap, true);
        }//endif
    }//end setLocators

    /**
     * Deletes a set of locators from the managed set of locators, and discards
     * any already-discovered lookup service that corresponds to a deleted
     * locator. For any lookup service that is discarded as a result of an
     * invocation of this method, a discard notification is sent; and that
     * lookup service will not be eligible for re-discovery (assuming it is
     * not currently eligible for discovery through other means, such as
     * group discovery).
     * <p>
     * If the empty array is input, this method takes no action.
     *
     * @param locators <code>LookupLocator</code> array consisting of the
     *                 locators that will be removed from the managed set.
     * 
     * @throws java.lang.NullPointerException this exception occurs when
     *         either <code>null</code> is input to the <code>locators</code>
     *         parameter, or one or more of the elements of the
     *         <code>locators</code> parameter is <code>null</code>.
     *
     * @throws java.lang.IllegalStateException this exception occurs when
     *         this method is called after the <code>terminate</code>
     *         method has been called.
     * 
     * @see net.jini.discovery.DiscoveryLocatorManagement#removeLocators
     * @see #addLocators
     */
    public void removeLocators(LookupLocator[] locators) {
        testSetForNull(locators);
        if (terminated)  throw new IllegalStateException("discovery terminated");
        Map groupsMap = new HashMap(1);
        for(int i=0; i<locators.length; i++) {
            LocatorReg reg = removeDiscoveredLocator(locators[i]);
            if(reg != null) {//removing an already-discovered reg
                groupsMap.put(reg.getProxy(), reg.getMemberGroups());
                continue;
            }//endif
            reg = findReg(locators[i]);
            if(reg != null) {//reg not yet discovered, stop discovery of it
                undiscoveredLocators.remove(reg);
            }//endif
        }//end loop
        /* Send a discarded event to all registered listeners for any
         * locators that were removed by this method.
         */
        if(!groupsMap.isEmpty() && !listeners.isEmpty()) {
            addNotify(listeners, groupsMap, true);
        }//endif
    }//end removeLocators

    /**
     * Returns the set of <code>LookupLocator</code> objects representing the
     * desired lookup services that are currently discovered. If no lookup
     * services are currently discovered, this method returns the empty array.
     * This method returns a new array upon each invocation.
     *
     * @return <code>LookupLocator</code> array consisting of the elements
     *         from the managed set of locators that correspond to lookup
     *         services that have already been discovered.
     *
     * @throws java.lang.IllegalStateException this exception occurs when
     *         this method is called after the <code>terminate</code>
     *         method has been called.
     */
    public LookupLocator[] getDiscoveredLocators() {
        if (terminated) throw new IllegalStateException("discovery terminated");
	List<LookupLocator> locators = new LinkedList<LookupLocator>();
        /* Retrieve the locators of the already-discovered lookup services */
	Iterator<LocatorReg> iter = discoveredLocators.iterator();
	while(iter.hasNext()) { 
            locators.add(iter.next().l);
        }//end loop
	return locators.toArray(new LookupLocator[locators.size()]);
    }//end getDiscoveredLocators

    /**
     * Returns the set of <code>LookupLocator</code> objects representing the
     * desired lookup services that have not yet been discovered. If all
     * of the desired lookup services are currently discovered, this method
     * returns the empty array. This method returns a new array upon each
     * invocation.
     *
     * @return <code>LookupLocator</code> array consisting of the elements
     *         from the managed set of locators that correspond to lookup
     *         services that have not yet been discovered.
     *
     * @throws java.lang.IllegalStateException this exception occurs when
     *         this method is called after the <code>terminate</code>
     *         method has been called.
     */
    public LookupLocator[] getUndiscoveredLocators() {
        if (terminated) throw new IllegalStateException("discovery terminated");
        List<LookupLocator> locators = new LinkedList<LookupLocator>();
        Iterator<LocatorReg> iter = undiscoveredLocators.iterator();
        for(int i=0;iter.hasNext();i++) {
            locators.add(iter.next().l);
        }//end loop
        return locators.toArray(new LookupLocator[locators.size()]);
    }//end getUndiscoveredLocators

    /** Initiates the discovery process for the lookup services having the
     *  given locators.
     */
    private void discoverLocators(LookupLocator[] lcts) {
	discoverLocatorsCalled = true;
	if (lcts == null)  return;
	LookupLocator lct;
	for(int i=0; i<lcts.length; i++) {
	    if(isDiscovered(lcts[i]))  continue;
	    LocatorReg reg = findReg(lcts[i]);//in not-yet-discovered map?
	    if(reg == null) {
		reg = new LocatorReg(lcts[i]);
		addAndQueueDiscoveryTaskIfAbsent(reg);
	    }//endif
	}//end loop
    }//end discoverLocators

    /** From the sets of elements corresponding to yet-to-be-discovered
     *  locators, this methods finds and returns the instance of LocatorReg
     *  corresponding to the given LookupLocator. This method searches 
     *  the set of undiscoveredLocators, and upon finding a matching
     *  LocatorReg object, that object is returned; otherwise, null is
     *  returned.
     */
    private LocatorReg findReg(LookupLocator lct) {
        Iterator iter = undiscoveredLocators.iterator();
        while(iter.hasNext()) {
            LocatorReg reg = (LocatorReg)iter.next();
            if (reg.l.equals(lct))  return reg;
        }//end loop
        return null;
    }//end findReg

    /** This method searches the set of discovered LocatorReg objects
     *  for the element that contains the given ServiceRegistrar object.
     *  Upon finding such an element, the corresponding LookupLocator is
     *  returned; otherwise, null is returned. 
     */
    private LookupLocator findRegFromProxy(ServiceRegistrar proxy) {
	Iterator iter = discoveredLocators.iterator();
	while(iter.hasNext()) {
	    LocatorReg reg = (LocatorReg)iter.next();
	    if((reg.getProxy()).equals(proxy ))  return reg.l;
	}//end loop
	return null;    
    }//end findRegFromProxy

    /** Convenience method called from within the DiscoveryTask. Employing
     *  unicast discovery, this method attempts to discover the lookup service
     *  associated with the given LocatorReg. After successfully discovering
     *  the desired lookup service, this method queues the appropriate
     *  event for dissemination to the registered listeners, and then returns
     *  <code>true</code>; otherwise <code>false</code> is returned.
     */
    private boolean regTryGetProxy(LocatorReg reg) {
        /* The following call performs the actual unicast discovery attempt,
         * and should not be made within a synchronization block.
         */
	boolean b = reg.tryGetProxy();
        /* While the discovery attempt was being made above, the locator
         * corresponding to the given LocatorReg may have been removed from
         * the managed set of locators (by a call to set/removeLocators). If
         * it did happen to be removed, then there is no need to continue
         * with the discovery process of that locator, whether its proxy was
         * successfully retrieved or not. Thus, if it was removed  from the
         * set of undiscoveredLocators while unicast discovery was being
         * performed, then return true to stop any queuing of a retry of
         * the task used in the discovery attempt of the given locator.
         * 
         * If it wasn't removed, but its proxy could not be successfully
         * discovered (as indicated by a false return value), then leave
         * it in the set of undiscoveredLocators and schedule - at a later
         * time - a retry of the task which performs the discovery attempt.
         *
         * Finally, if the proxy of the locator was successfully discovered,
         * then remove the locator from the set of undiscoveredLocators, add
         * it to the set of discoveredLocators, notify all registered
         * listeners that the locator has been discovered, and return true
         * to prevent retries from being queued.
         */
        if(!undiscoveredLocators.contains(reg))  return true;//already removed, true ==> don't queue retry
        /* Discovery un-successful, leave in set, try new wakeup task */
        if(!b) return false;//this causes a retry to be queued
        /* Discovery was successful, move reg from undiscoveredLocators
         * to discoveredLocators, and notify listeners
         */
        if (undiscoveredLocators.remove(reg)){ //Atomic
            discoveredLocators.add(reg);
            if(!listeners.isEmpty()) {
                addNotify(listeners,
                          mapRegToGroups(reg.getProxy(), reg.getMemberGroups()),
                          false);
            }//endif
            //done; don't queue any retries return true
        } //else already removed, true ==> don't queue retry
        return true; 
    }//end regTryGetProxy

    /** From each element of the set of LocatorReg objects that correspond
     *  to lookup services that have been discovered, this method extracts
     *  the ServiceRegistrar reference and returns all of the references
     *  in an array of ServiceRegistrar.
     */
    private ServiceRegistrar[] buildServiceRegistrar() {
	List<ServiceRegistrar> regs = new LinkedList<ServiceRegistrar>();
	Iterator<LocatorReg> iter = discoveredLocators.iterator();
	while(iter.hasNext()) {
	    LocatorReg reg = iter.next();
	    regs.add(reg.getProxy());
	}//end loop
	return regs.toArray(new ServiceRegistrar[regs.size()]);
    }//end buildServiceRegistrar

    /** 
     *  Adds the given LocatorReg object to the set containing the objects
     *  corresponding to the locators of desired lookup services that have
     *  not yet been discovered, and queues a DiscoveryTask to attempt,
     *  through unicast discovery, to discover the associated lookup service.
     */
    private void addAndQueueDiscoveryTaskIfAbsent(LocatorReg reg) {
        if (undiscoveredLocators.add(reg)) reg.queueDiscoveryTask();
    }//end addToMap

    /** Determines whether or not the lookup service associated with the
     *  given LookupLocator has already been discovered.
     */
    private boolean isDiscovered(LookupLocator lct) {
	Iterator iter = discoveredLocators.iterator();
	while(iter.hasNext()) {
 	    LocatorReg reg = (LocatorReg)iter.next();
	    if(reg.l.equals(lct))  return true;
	}//end loop
	return false;
    }//end isDiscovered

    /** Add a notification task to the pending queue, and start an instance of
     *  the Notifier thread if one isn't already running.
     */
    private void addNotify(List<DiscoveryListener> notifies,
                           Map<ServiceRegistrar, String[]> groupsMap,
			   boolean discard)
    {
        pendingNotifies.addLast(new NotifyTask(notifies,
                                               groupsMap,
                                               discard));
    }//end addNotify

    /** Convenience method used to remove the LocatorReg - corresponding to
     *  the given LookupLocator - from the set of LocatorReg objects that
     *  correspond to lookup services that have already been discovered.
     */
    private LocatorReg removeDiscoveredLocator(LookupLocator lct) {
	Iterator iter = discoveredLocators.iterator();
	while(iter.hasNext()) {
	    LocatorReg reg = (LocatorReg)iter.next();
	    if(reg.l.equals(lct)) {
		discoveredLocators.remove(reg);
		return reg;
	    }//endif
	}//end loop
	return null;
    }//end removeDiscoveredLocator

    /** Convenience method that removes all pending and active tasks from the
     *  TaskManager, and removes all pending tasks from the WakeupManager.
     */ 
    private void terminateTaskMgr() {
        /* Cancel all tasks scheduled for future retry by the wakeup manager */
        if(discoveryWakeupMgr != null) {
            discoveryWakeupMgr.stop();//stop execution of the wakeup manager
            discoveryWakeupMgr.cancelAll();//cancel all tickets
        }//endif
        /* Cancel/remove pending tasks from the task manager and terminate */
        if(discoveryExecutor != null) {
            List<Runnable> pendingTasks = discoveryExecutor.shutdownNow();
            Iterator<Runnable> ir = pendingTasks.iterator();
            while (ir.hasNext()){
                Runnable pendingTask = ir.next();
                if (pendingTask instanceof Future) {
                    ((Future) pendingTask).cancel(false);
                }
            }
        }//endif
    }//end terminateTaskMgr

    /** Determines if the given Object is an element of the given array. */
    private boolean isArrayContains(Object[] a, Object obj) {
	for(int i=0; i<a.length; i++ ) {
	    if(a[i].equals(obj))  return true;
	}//end loop
	return false;
    }//end isArrayContains
    
    /* Convenience method useful for debugging. */
    public String toString() {
        StringBuilder sb = new StringBuilder(300);
        String message = "Undiscovered Locators reg:";
        char lineReturn = '\n';
        Iterator iter = undiscoveredLocators.iterator();
        while(iter.hasNext()) {
            LocatorReg reg = (LocatorReg)iter.next();	    
            sb.append(message);
            sb.append(reg.l);
            sb.append(lineReturn);
        }//end loop
        return sb.toString();
    }//end printMap

    /**
     * This method is used by the public methods of this class that are
     * specified to throw a <code>NullPointerException</code> when the
     * set of locators is either <code>null</code> or contains one or
     * more <code>null</code> elements; in either case, this method 
     * throws a <code>NullPointerException</code> which should be allowed
     * to propagate outward.
     * 
     * @throws java.lang.NullPointerException this exception occurs when
     *         either <code>null</code> is input to the <code>locatorSet</code>
     *         parameter, or one or more of the elements of the
     *         <code>locatorSet</code> parameter is <code>null</code>.
     */
    private void testSetForNull(LookupLocator[] locatorSet) {
        if(locatorSet == null) {
            throw new NullPointerException("null locator array");
        }//endif
        for(int i=0;i<locatorSet.length;i++) {
            if(locatorSet[i] == null) {
                throw new NullPointerException
                                           ("null element in locator array");
            }//endif
        }//end loop
    }//end testSetForNull

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
    private Map<ServiceRegistrar, String[]> deepCopy(Map<ServiceRegistrar, String[]> groupsMap) {
        /* clone the input HashMap */
        Map<ServiceRegistrar, String[]> newMap 
                = new HashMap<ServiceRegistrar, String[]>(groupsMap);
        /* clone the values of each mapping in place */
        Set<Map.Entry<ServiceRegistrar, String[]>> eSet = newMap.entrySet();
        for(Iterator<Map.Entry<ServiceRegistrar, String[]>> itr = eSet.iterator();
                itr.hasNext(); ) 
        {
            Map.Entry<ServiceRegistrar, String[]> pair = itr.next();
            /* only need to clone the value of the order pair */
            pair.setValue( pair.getValue().clone() );
        }//end loop
        return newMap;
    }//end deepCopy

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
    private Map mapRegToGroups(ServiceRegistrar reg, String[] curGroups) {
        HashMap groupsMap = new HashMap(1);
        groupsMap.put(reg,curGroups);
        return groupsMap;
    }//end mapRegToGroups

    /**
     * Using the given <code>Configuration</code>, initializes the current
     * instance of this utility, and initiates the discovery process for
     * the given set of locators.
     *
     * @param locators the set of locators to discover
     * @throws java.lang.NullPointerException input array contains at least
     *         one <code>null</code> element or <code>null</code> is input
     *         for the configuration
     */
    void beginDiscovery(final LookupLocator[] locators)
    {
        notifierThread.start();
        if (locators == null) {
            return;
        }
        testSetForNull(locators);
        if (initialUnicastDelayRange > 0) {
            discoveryWakeupMgr.schedule(
                System.currentTimeMillis() +
                    (long) (Math.random() * initialUnicastDelayRange),
                new Runnable() {
                    public void run() {
                        synchronized (AbstractLookupLocatorDiscovery.this) {
                            if (terminated || discoverLocatorsCalled) {
                                // discoverLocatorsCalled will be true
                                // if there has been an intervening
                                // addLocators or setLocators call.
                                return;
                            }
                        }
                        discoverLocators(locators);
                    }
                }
            );
        } else {
            discoverLocators(locators);
        }
    }
    
    private static class Initializer{
        ProxyPreparer registrarPreparer;
        ExecutorService discoveryTaskMgr;
        WakeupManager discoveryWakeupMgr;
        Long initialUnicastDelayRange;
    }
    
    private static Initializer initEmptyConfig(){
        try {
            return init(EmptyConfiguration.INSTANCE);
        } catch (ConfigurationException ex) {
            throw new IllegalStateException("EmptyConfiguration threw execption", ex);
        }
    }

    /* Convenience method that encapsulates the retrieval of the configurable
     * items from the given <code>Configuration</code> object.
     */
    private static Initializer init(Configuration config) throws ConfigurationException {
        Initializer i = new Initializer();
        if(config == null)  throw new NullPointerException("config is null");
        /* Lookup service proxy preparer */
        i.registrarPreparer = (ProxyPreparer)config.getEntry
                                                   (COMPONENT_NAME,
                                                    "registrarPreparer",
                                                    ProxyPreparer.class,
                                                    new BasicProxyPreparer());
        /* Task manager */
        try {
            i.discoveryTaskMgr = (ExecutorService)config.getEntry(COMPONENT_NAME,
                                                            "executorService",
                                                            ExecutorService.class);
        } catch(NoSuchEntryException e) { /* use default */
            i.discoveryTaskMgr = 
            new ThreadPoolExecutor(
                    MAX_N_TASKS, 
                    MAX_N_TASKS, /* Ignored */
                    15L,
                    TimeUnit.SECONDS,
                    new LinkedBlockingQueue<Runnable>(), /* Unbounded */
                    new NamedThreadFactory("LookupLocatorDiscovery", false)
            );
        }
        /* Wakeup manager */
        try {
            i.discoveryWakeupMgr = (WakeupManager)config.getEntry
                                                        (COMPONENT_NAME,
                                                         "wakeupManager",
                                                         WakeupManager.class);
        } catch(NoSuchEntryException e) { /* use default */
            i.discoveryWakeupMgr = new WakeupManager
                                    (new WakeupManager.ThreadDesc(null, true));
        }
	
	i.initialUnicastDelayRange = Config.getLongEntry(config,
			    COMPONENT_NAME,
			    "initialUnicastDelayRange",
			    0,
			    0,
			    Long.MAX_VALUE);
        return i;
    }//end init

    
}
