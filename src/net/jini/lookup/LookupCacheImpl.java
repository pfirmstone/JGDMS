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

package net.jini.lookup;

import java.io.IOException;
import java.rmi.RemoteException;
import java.rmi.server.ExportException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RunnableFuture;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.jini.config.ConfigurationException;
import net.jini.core.entry.Entry;
import net.jini.core.event.RemoteEvent;
import net.jini.core.event.RemoteEventListener;
import net.jini.core.event.UnknownEventException;
import net.jini.core.lookup.ServiceEvent;
import net.jini.core.lookup.ServiceID;
import net.jini.core.lookup.ServiceItem;
import net.jini.core.lookup.ServiceMatches;
import net.jini.core.lookup.ServiceRegistrar;
import net.jini.core.lookup.ServiceTemplate;
import net.jini.export.Exporter;
import net.jini.export.ServiceAttributesAccessor;
import net.jini.export.ServiceIDAccessor;
import net.jini.export.ServiceProxyAccessor;
import net.jini.io.MarshalledInstance;
import net.jini.jeri.BasicILFactory;
import net.jini.jeri.BasicJeriExporter;
import net.jini.jeri.tcp.TcpServerEndpoint;
import net.jini.security.TrustVerifier;
import net.jini.security.proxytrust.ServerProxyTrust;
import org.apache.river.concurrent.RC;
import org.apache.river.concurrent.Ref;
import org.apache.river.concurrent.Referrer;
import org.apache.river.lookup.entry.LookupAttributes;
import org.apache.river.proxy.BasicProxyTrustVerifier;
import org.apache.river.thread.DependencyLinker;
import org.apache.river.thread.ExtensibleExecutorService;
import org.apache.river.thread.FutureObserver;
import org.apache.river.thread.NamedThreadFactory;
import org.apache.river.thread.ObservableFutureTask;

/**
 * Internal implementation of the LookupCache interface. Instances of this class
 * are used in the blocking versions of ServiceDiscoveryManager.lookup() and are returned by
 * createLookupCache.
 */
final class LookupCacheImpl implements LookupCache {

    private static final int ITEM_ADDED = 0;
    private static final int ITEM_REMOVED = 2;
    private static final int ITEM_CHANGED = 3;
    /* The listener that receives remote events from the lookup services */
    private final LookupListener lookupListener;
    /* Exporter for the remote event listener (lookupListener) */
    private volatile Exporter lookupListenerExporter;
    /* Proxy to the listener that receives remote events from lookups */
    private volatile RemoteEventListener lookupListenerProxy;
    /**
     * Task manager for the various tasks executed by this LookupCache
     */
    private volatile ExecutorService cacheTaskMgr;
    private volatile CacheTaskDependencyManager cacheTaskDepMgr;
    /* Flag that indicates if the LookupCache has been terminated. */
    private volatile boolean bCacheTerminated = false;
    /* Contains the ServiceDiscoveryListener's that receive local events */
    private final Collection<ServiceDiscoveryListener> sItemListeners;
    /* Map from ServiceID to ServiceItemReg */
    private final ConcurrentMap<ServiceID, ServiceItemReg> serviceIdMap;
    /* Map from ProxyReg to EventReg: (proxyReg, {source,id,seqNo,lease})*/
    private final ConcurrentMap<ProxyReg, EventReg> eventRegMap;
    /* Template current cache instance should use for primary matching */
    private final ServiceTemplate tmpl;
    /* Filter current cache instance should use for secondary matching */
    private final ServiceItemFilter filter;
    /* Desired lease duration to request from lookups' event mechanisms */
    private final long leaseDuration;
    /* Log the time when the cache gets created. This value is used to
     * calculate the time when the cache should expire.
     */
    private final long startTime;
    /**
     * For tasks waiting on verification events after service discard
     */
    private volatile ExecutorService serviceDiscardTimerTaskMgr;
    private final ConcurrentMap<ServiceID, Future> serviceDiscardFutures;
    /**
     * Whenever a ServiceIdTask is created in this cache, it is assigned a
     * unique sequence number to allow such tasks associated with the same
     * ServiceID to be executed in the order in which they were queued in the
     * TaskManager. This field contains the value of the sequence number
     * assigned to the most recently created ServiceIdTask.
     */
    private final AtomicLong taskSeqN;
    private final ServiceDiscoveryManager sdm;

    LookupCacheImpl(ServiceTemplate tmpl, ServiceItemFilter filter, ServiceDiscoveryListener sListener, long leaseDuration, ServiceDiscoveryManager sdm) throws RemoteException {
	this.taskSeqN = new AtomicLong();
	this.startTime = System.currentTimeMillis();
	this.eventRegMap = new ConcurrentHashMap<ProxyReg, EventReg>();
	this.serviceIdMap = new ConcurrentHashMap<ServiceID, ServiceItemReg>();
	this.sItemListeners = new HashSet<ServiceDiscoveryListener>();
	this.serviceDiscardFutures = RC.concurrentMap(new ConcurrentHashMap<Referrer<ServiceID>, Referrer<Future>>(), Ref.WEAK_IDENTITY, Ref.STRONG, 60000, 60000);
	this.tmpl = tmpl.clone();
	this.leaseDuration = leaseDuration;
	this.filter = filter;
	lookupListener = new LookupListener();
	if (sListener != null) {
	    sItemListeners.add(sListener);
	}
	this.sdm = sdm;
    } //end constructor

    private ExecutorService eventNotificationExecutor;

    /* RemoteEventListener class that is registered with the proxy to
     * receive notifications from lookup services when any ServiceItem
     * changes (NOMATCH_MATCH, MATCH_NOMATCH, MATCH_MATCH)
     */
    private final class LookupListener implements RemoteEventListener,
	    ServerProxyTrust {

	RemoteEventListener export() throws ExportException {
	    return (RemoteEventListener) lookupListenerExporter.export(this);
	}

	@Override
	public void notify(RemoteEvent evt) throws UnknownEventException,
		java.rmi.RemoteException {
	    if (!(evt instanceof ServiceEvent)) {
		throw new UnknownEventException("ServiceEvent required,not: " + evt.toString());
	    }
	    ServiceEvent theEvent = (ServiceEvent) evt;
	    if (sdm.useInsecureLookup){
		notifyServiceMap(
		    theEvent.getSource(),
		    theEvent.getID(),
		    theEvent.getSequenceNumber(),
		    theEvent.getServiceID(),
		    theEvent.getServiceItem(),
		    theEvent.getTransition()
		);
	    } else {
		ServiceItem item = null;
		ServiceID id;
		//REMIND: Consider using the actual service proxy?
		Object proxy = theEvent.getBootstrapProxy();
		if (proxy != null){
		    Entry [] attributes;
		    proxy = sdm.bootstrapProxyPreparer.prepareProxy(proxy);
		    try {
			id = ((ServiceIDAccessor)proxy).serviceID();
			attributes = ((ServiceAttributesAccessor)proxy).getServiceAttributes();
			item = new ServiceItem(id, proxy, attributes);
		    } catch (IOException ex) {
			sdm.logger.log(Level.FINE, 
			    "exception thrown while attempting to establish contact via a bootstrap proxy"
			    , ex
			);
			// Item will be null.
		    }
		}
		notifyServiceMap(
		    theEvent.getSource(),
		    theEvent.getID(),
		    theEvent.getSequenceNumber(),
		    theEvent.getServiceID(),
		    item,
		    theEvent.getTransition()
		);
	    }
	}

	/**
	 * Returns a <code>TrustVerifier</code> which can be used to verify that
	 * a given proxy to this listener can be trusted.
	 */
	@Override
	public TrustVerifier getProxyVerifier() {
	    return new BasicProxyTrustVerifier(lookupListenerProxy);
	} //end getProxyVerifier
    } //end class LookupCacheImpl.LookupListener

    /**
     * This task class, when executed, first registers to receive ServiceEvents
     * from the given ServiceRegistrar. If the registration process succeeds (no
     * RemoteExceptions), it then performs a lookup to query the given
     * ServiceRegistrar for a "snapshot" of its current state with respect to
     * services that match the given template.
     *
     * Note that the order of execution is important. That is, the lookup must
     * be executed only after registration for events has completed. This is
     * because when an entity registers with the event mechanism of a
     * ServiceRegistrar, the entity will only receive notification of events
     * that occur "in the future", after the registration is made. The entity
     * will not receive events about changes to the state of the
     * ServiceRegistrar that may have occurred before or during the registration
     * process.
     *
     * Thus, if the order of these tasks were reversed and lookup were to be
     * performed prior to the RegisterListenerTask, then the possibility exists
     * for the occurrence of a change in the ServiceRegistrar's state between
     * the time lookup retrieves a snapshot of that state, and the time the
     * event registration process has completed, resulting in an incorrect view
     * of the current state of the ServiceRegistrar.
     */
    private static final class RegisterListenerTask extends
	    CacheTask {

	final LookupCacheImpl cache;

	public RegisterListenerTask(ProxyReg reg,
		long seqN, LookupCacheImpl cache) {
	    super(reg, seqN);
	    this.cache = cache;
	}

	@Override
	public boolean hasDeps() {
	    return true;
	}

	@Override
	public boolean dependsOn(CacheTask t) {
	    if (t instanceof ProxyRegDropTask) {
		ProxyReg r = getProxyReg();
		if (r != null && r.equals(t.getProxyReg())) {
		    if (t.getSeqN() < getSeqN()) {
			return true;
		    }
		}
	    }
	    return false;
	}

	@Override
	public void run() {
	    ServiceDiscoveryManager.logger.finest(
		    "ServiceDiscoveryManager - RegisterListenerTask started");
	    long duration = cache.getLeaseDuration();
	    if (duration < 0) {
		return;
	    }
	    try {
		EventReg eventReg
			= cache.sdm.registerListener(
				reg.getProxy(),
				cache.tmpl,
				cache.lookupListenerProxy,
				duration
			);
		// eventReg is a new object not visible to other threads yet.
		// It will be safely published using a ConcurrentMap, so
		// we don't need to synchronize here.
		/* Cancel the lease if the cache has been terminated */
		if (cache.bCacheTerminated
			|| Thread.currentThread().isInterrupted()) {
		    // eventReg.lease is final and is already safely published
		    cache.sdm.cancelLease(eventReg.lease);
		    return;
		} else {
		    EventReg existed
			    = cache.eventRegMap.putIfAbsent(reg, eventReg);
		    while (existed != null) {
			// Cancel existing lease.
			cache.sdm.cancelLease(existed.lease);
			boolean replaced
				= cache.eventRegMap.replace(reg, existed, eventReg);
			if (replaced) {
			    break;
			} else {
			    existed = cache.eventRegMap.putIfAbsent(reg, eventReg);
			}
		    }
		    /* Execute the lookup only if there were no problems */
		    synchronized (eventReg) {
			cache.lookup(reg, eventReg);
		    }
		} //endif
	    } catch (Exception e) {
		cache.sdm.fail(e,
			reg.getProxy(),
			this.getClass().getName(),
			"run",
			"Exception occurred while attempting to register with the lookup service event mechanism",
			cache.bCacheTerminated
		);
	    }
	    ServiceDiscoveryManager.logger.finest(
		    "ServiceDiscoveryManager - RegisterListenerTask completed");
	} //end run
    } //end class LookupCacheImpl.RegisterListenerTask

    /**
     * When the given registrar is discarded, this Task class is used to remove
     * the registrar from the various maps maintained by this cache.
     */
    private static final class ProxyRegDropTask extends CacheTask {

	final LookupCacheImpl cache;
	final EventReg eReg;

	public ProxyRegDropTask(ProxyReg reg,
		EventReg eReg,
		long seqN,
		LookupCacheImpl cache) {
	    super(reg, seqN);
	    this.cache = cache;
	    this.eReg = eReg;
	}

	@Override
	public void run() {
	    ServiceDiscoveryManager.logger.finest(
		    "ServiceDiscoveryManager - ProxyRegDropTask started");
	    //lease has already been cancelled by removeProxyReg
	    cache.eventRegMap.remove(reg, eReg);
	    /* For each itemReg in the serviceIdMap, disassociate the
	     * lookup service referenced here from the itemReg; and
	     * if the itemReg then has no more lookup services associated
	     * with it, remove the itemReg from the map and send a
	     * service removed event.
	     */
	    Iterator<Map.Entry<ServiceID, ServiceItemReg>> iter = cache.serviceIdMap.entrySet().iterator();
	    while (iter.hasNext()) {
		Map.Entry<ServiceID, ServiceItemReg> e = iter.next();
		ServiceID srvcID = e.getKey();
		ServiceItemReg itemReg = e.getValue();
		/* mutating the map would not have been allowed while
		 * iterating when using a standard map.
		 */
		ServiceRegistrar proxy = null;
		ServiceItem item;
		synchronized (itemReg) {
		    item = itemReg.removeProxy(reg.getProxy()); //disassociate the LUS
		    if (item != null) {
			// new LUS chosen to track changes
			proxy = itemReg.getProxy();
			cache.itemMatchMatchChange(srvcID, itemReg, proxy, item, false);
		    } else if (itemReg.hasNoProxys()) {
			//no more LUSs, remove from map
			item = itemReg.getFilteredItem();
			iter.remove();
			if (!itemReg.isDiscarded()) {
			    cache.removeServiceNotify(item);
			}
		    } //endif
		}
	    } //end loop
	    ServiceDiscoveryManager.logger.finest("ServiceDiscoveryManager - ProxyRegDropTask " + "completed");
	} //end run

	@Override
	public boolean hasDeps() {
	    return true;
	}

	@Override
	public boolean dependsOn(CacheTask t) {
	    if (t instanceof RegisterListenerTask || t instanceof ProxyRegDropTask) {
		ProxyReg r = getProxyReg();
		if (r != null && r.equals(t.getProxyReg())) {
		    if (t.getSeqN() < getSeqN()) {
			return true;
		    }
		}
	    }
	    return false;
	}
    } //end class LookupCacheImpl.ProxyRegDropTask

    /**
     * Task class used to determine whether or not to "commit" a service discard
     * request, increasing the chances that the service will eventually be
     * re-discovered. This task is also used to attempt a filter retry on an
     * item in which the cache's filter initially returned indefinite.
     */
    private static final class ServiceDiscardTimerTask implements Runnable {

	private final ServiceID serviceID;
	private final long endTime;
	private final LookupCacheImpl cache;

	public ServiceDiscardTimerTask(LookupCacheImpl cache, ServiceID serviceID) {
	    this.serviceID = serviceID;
	    this.cache = cache;
	    this.endTime = cache.sdm.discardWait + System.currentTimeMillis();
	} //end constructor

	@Override
	public void run() {
	    ServiceDiscoveryManager.logger.finest("ServiceDiscoveryManager - " + "ServiceDiscardTimerTask started");
	    /* Exit if this cache has already been terminated. */
	    if (cache.bCacheTerminated) {
		return;
	    }
	    /* Simply return if a MATCH_NOMATCH event arrived for this
	     * item prior to this task running and as a result, the item
	     * was removed from the map.
	     */
	    if (!cache.serviceIdMap.containsKey(serviceID)) {
		return;
	    }
	    long curDur = endTime - System.currentTimeMillis();
	    synchronized (this) {
		/* Wait until interrupted or time expires */
		while (curDur > 0) {
		    try {
			this.wait(curDur);
		    } catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			return; // Task cancelled
		    }
		    /* Exit if this cache was terminated while waiting. */
		    if (cache.bCacheTerminated) {
			return;
		    }
		    /* Either the wait period has completed or has been
		     * interrupted. If the service ID is no longer in
		     * in the serviceIdMap, then it's assumed that a
		     * MATCH_NOMATCH event must have arrived which could be
		     * viewed as an indication that the service's lease
		     * expired, which then could be interpreted as meaning
		     * the service is actually down, and will be
		     * re-discovered when it comes back on line. In that
		     * case, exit the thread.
		     */
		    if (!cache.serviceIdMap.containsKey(serviceID)) {
			return;
		    }
		    curDur = endTime - System.currentTimeMillis();
		} //end loop
	    } //end sync
	    /* The thread was not interrupted, time expired.
	     *
	     * If the service ID is still contained in the serviceIdMap,
	     * then a MATCH_NOMATCH event did not arrive, which is
	     * interpreted here to mean that the service is still up.
	     * The service ID will still be in the map if one (or both)
	     * of the following is true:
	     *  - the client discarded an unreachable service that never
	     *    actually went down (so it's lease never expired, and
	     *    a MATCH_NOMATCH event was never received)
	     *  - upon applying the cache's filter to the service, the
	     *    filter returned indefinite, and this task was queued
	     *    to request that filtering be retried at a later time
	     *
	     * For the first case above, the service is "un-discarded" so
	     * the service will be available to the client again. For the
	     * second case, the filter is retried. If the service passes
	     * the filter, the service is "un-discarded"; otherwise, it is
	     * 'quietly' removed from the map (because a service removed
	     * event was already sent when the service was originally
	     * discarded.
	     */
	    ServiceItemReg itemReg = cache.serviceIdMap.get(serviceID);
	    if (itemReg != null) {
		//Discarded status hasn't changed
		ServiceItem itemToSend;
		synchronized (itemReg) {
		    if (!itemReg.unDiscard()) {
			return; // "un-discards"
		    }
		    ServiceItem item;
		    ServiceItem filteredItem = null;
		    itemToSend = itemReg.getFilteredItem();
		    if (itemToSend == null) {
			item = itemReg.getItem().clone();
			filteredItem = item.clone();
			//retry the filter
			if (cache.sdm.useInsecureLookup){
			    if (cache.sdm.filterPassed(filteredItem, cache.filter)) {
				itemToSend = cache.addFilteredItemToMap(item, filteredItem, itemReg);
				if (itemToSend == null) return;
			    } else {
				//'quietly' remove the item
				cache.removeServiceIdMapSendNoEvent(serviceID, itemReg);
				return;
			    } //endif
			} else {
			    // We're dealing with a bootstrap proxy.
			    // The filter may not be expecting a bootstrap proxy.
			    try {
				if(cache.sdm.filterPassed(filteredItem, cache.filter))
				    itemToSend = cache.addFilteredItemToMap(item, filteredItem, itemReg);
				if (itemToSend == null) return;
			    } catch (SecurityException | ClassCastException ex){
				ServiceDiscoveryManager.logger.log(Level.FINE, 
				    "Exception caught, while attempting to filter a bootstrap proxy", ex);
				try {
				    filteredItem.service = ((ServiceProxyAccessor) filteredItem.service).getServiceProxy();
				    if(cache.sdm.filterPassed(filteredItem, cache.filter))
					itemToSend = cache.addFilteredItemToMap(item, filteredItem, itemReg);
				    if (itemToSend == null) return;
				} catch (RemoteException ex1) {
				    ServiceDiscoveryManager.logger.log(Level.FINE, 
					"Exception thrown while attempting to obtain service proxy from bootstrap",
					ex1);
				    return;
				}
			    }
			}
		    } //endif
		    /* Either the filter was retried and passed, in which case,
		     * the filtered itemCopy was placed in the map and
		     * "un-discarded"; or the
		     * filter wasn't applied above (a non-null filteredItem
		     * field in the itemReg in the map means that the filter
		     * was applied at some previous time). In the latter case, the
		     * service can now be "un-discarded", and a notification
		     * that the service is now available can be sent for either case.
		     */
		    cache.addServiceNotify(itemToSend);
		}
	    }
	    ServiceDiscoveryManager.logger.finest("ServiceDiscoveryManager - " + "ServiceDiscardTimerTask completed");
	} //end run
    }

    // This method's javadoc is inherited from an interface of this class
    @Override
    public void terminate() {
	synchronized (this) {
	    if (bCacheTerminated) {
		return; //allow for multiple terminations
	    }
	    bCacheTerminated = true;
	} //end sync
	sdm.removeLookupCache(this);
	/* Terminate all tasks: first, terminate this cache's Executors*/
	cacheTaskMgr.shutdownNow();
	/* Terminate ServiceDiscardTimerTasks running for this cache */
	serviceDiscardTimerTaskMgr.shutdownNow();
	eventNotificationExecutor.shutdownNow();
	/* Cancel all event registration leases held by this cache. */
	Set set = eventRegMap.entrySet();
	Iterator iter = set.iterator();
	while (iter.hasNext()) {
	    Map.Entry e = (Map.Entry) iter.next();
	    EventReg eReg = (EventReg) e.getValue();
	    sdm.cancelLease(eReg.lease);
	} //end loop
	/* Un-export the remote listener for events from lookups. */
	try {
	    lookupListenerExporter.unexport(true);
	} catch (IllegalStateException e) {
	    ServiceDiscoveryManager.logger.log(Level.FINEST, "IllegalStateException occurred while unexporting " + "the cache's remote event listener", e);
	}
	ServiceDiscoveryManager.logger.finest("ServiceDiscoveryManager - LookupCache terminated");
    } //end LookupCacheImpl.terminate

    // This method's javadoc is inherited from an interface of this class
    @Override
    public ServiceItem lookup(ServiceItemFilter myFilter) {
	checkCacheTerminated();
	ServiceItem[] ret = getServiceItems(myFilter);
	if (ret.length == 0) {
	    return null;
	}
	// Maths.abs(Integer.MIN_VALUE) = -ve, so to avoid random
	// hard to debug bugs, this has been changed.
	int rand = sdm.random.nextInt(ret.length);
	return ret[rand];
    } //end LookupCacheImpl.lookup

    // This method's javadoc is inherited from an interface of this class
    @Override
    public ServiceItem[] lookup(ServiceItemFilter myFilter, int maxMatches) {
	checkCacheTerminated();
	if (maxMatches < 1) {
	    throw new IllegalArgumentException("maxMatches must be > 0");
	}
	List<ServiceItem> items = new LinkedList<ServiceItem>();
	ServiceItem[] sa = getServiceItems(myFilter);
	int len = sa.length;
	if (len == 0) {
	    return new ServiceItem[0];
	}
	int rand = sdm.random.nextInt(Integer.MAX_VALUE) % len;
	for (int i = 0; i < len; i++) {
	    items.add(sa[(i + rand) % len]);
	    if (items.size() == maxMatches) {
		break;
	    }
	} //end loop
	ServiceItem[] ret = new ServiceItem[items.size()];
	items.toArray(ret);
	return ret;
    } //end LookupCacheImpl.lookup

    // This method's javadoc is inherited from an interface of this class
    @Override
    public void discard(Object serviceReference) {
	checkCacheTerminated();
	/* Loop through the serviceIdMap, looking for the itemReg that
	 * corresponds to given serviceReference. If such an itemReg
	 * exists, and it's not already discarded, then queue a task
	 * to discard the given serviceReference.
	 */
	Iterator<Map.Entry<ServiceID, ServiceItemReg>> iter = getServiceIdMapEntrySetIterator();
	ServiceItem filteredItem;
	ServiceID sid;
	while (iter.hasNext()) {
	    Map.Entry<ServiceID, ServiceItemReg> e = iter.next();
	    ServiceItemReg itmReg = e.getValue();
	    synchronized (itmReg) {
		filteredItem = itmReg.getFilteredItem();
		if (filteredItem != null && (filteredItem.service).equals(serviceReference)) {
		    sid = e.getKey();
		    if (itmReg.discard()) {
			Future f = serviceDiscardTimerTaskMgr.submit(new ServiceDiscardTimerTask(this, sid));
			serviceDiscardFutures.put(sid, f);
			removeServiceNotify(filteredItem);
		    }
		    return;
		} //endifeg)
	    } //end sync
	} //end loop
    } //end LookupCacheImpl.discard

    /* Returns the iterator of entry set in the copy of the ServiceIdMap */
    private Iterator<Map.Entry<ServiceID, ServiceItemReg>> getServiceIdMapEntrySetIterator() {
	return serviceIdMap.entrySet().iterator();
    } //end LookupCacheImpl.getServiceIdMapEntrySetIterator

    /**
     * This method returns a <code>ServiceItem</code> array containing elements
     * that satisfy the following conditions: - is referenced by one of the
     * <code>itemReg</code> elements contained in the <code>serviceIdMap</code>
     * - is not currently discarded - satisfies the given
     * <code>ServiceItemFilter</code>
     *
     * Note that the <code>filter</code> parameter is a "2nd stage" filter. That
     * is, for each <code>itemReg</code> element in the
     * <code>serviceIdMap</code>, the "1st stage" filter corresponding to the
     * current instance of <code>LookupCache</code> has already been applied to
     * the <code>ServiceItem</code> referenced in that <code>itemReg</code>. The
     * <code>ServiceItemFilter</code> applied here is supplied by the entity
     * interacting with the cache, and provides a second filtering process.
     * Thus, this method applies the given <code>filter</code> parameter to the
     * <code>filteredItem</code> field (not the <code>item</code> field) of each
     * non-discarded <code>itemReg</code> element in the
     * <code>serviceIdMap</code>.
     *
     * This method returns all the instances of <code>ServiceItem</code> that
     * pass the given <code>filter</code>; and it discards all the items that
     * produce an indefinite result when that <code>filter</code> is applied.
     */
    private ServiceItem[] getServiceItems(ServiceItemFilter filter2) {
	List<ServiceItem> items = new LinkedList<ServiceItem>();
	Iterator<Map.Entry<ServiceID, ServiceItemReg>> iter = getServiceIdMapEntrySetIterator();
	while (iter.hasNext()) {
	    Map.Entry<ServiceID, ServiceItemReg> e = iter.next();
	    ServiceItemReg itemReg = e.getValue();
	    Object serviceToDiscard;
	    ServiceItem itemToFilter;
	    synchronized (itemReg) {
		if ((itemReg.isDiscarded()) || (itemReg.getFilteredItem() == null)) {
		    continue;
		}
		/* Make a copy because the filter may change it to null */
		itemToFilter = itemReg.getFilteredItem().clone();
		serviceToDiscard = itemReg.getItem().service;
	    }
	    /* Apply the filter */
	    boolean pass = (filter2 == null) || (filter2.check(itemToFilter));
	    /* Handle filter fail - skip to next item */
	    if (!pass) {
		continue;
	    }
	    /* Handle filter pass - add item to return set */
	    if (itemToFilter.service != null) {
		items.add(itemToFilter);
		continue;
	    } //endif(pass)
	    /* Handle filter indefinite - discard the item */
	    discard(serviceToDiscard);
	} //end loop
	ServiceItem[] ret = new ServiceItem[items.size()];
	items.toArray(ret);
	return ret;
    } //end LookupCacheImpl.getServiceItems

    ServiceItem [] processBootStrapProxys(Object [] proxys){
	int length = proxys.length;
	Collection<ServiceItem> result = new ArrayList<ServiceItem>(length);
	for (int i = 0; i < length; i++){
	    Object bootstrap;
	    Entry [] attributes;
	    ServiceID id;
	    try {
		bootstrap = sdm.bootstrapProxyPreparer.prepareProxy(proxys[i]);
		attributes = ((ServiceAttributesAccessor) bootstrap).getServiceAttributes();
		id = ((ServiceIDAccessor) bootstrap).serviceID();
		result.add(new ServiceItem(id, bootstrap, attributes));
	    } catch (IOException ex) { } //ignore
	}
	return result.toArray(new ServiceItem[result.size()]);
    }
    
    // This method's javadoc is inherited from an interface of this class
    @Override
    public void addListener(ServiceDiscoveryListener listener) {
	checkCacheTerminated();
	if (listener == null) {
	    throw new NullPointerException("can't add null listener");
	}
	//No action is taken if not added according to LookupCache
	ServiceItem[] items = getServiceItems(null);
	synchronized (sItemListeners) {
	    if (sItemListeners.add(listener)) {
		for (int i = 0, l = items.length; i < l; i++) {
		    addServiceNotify(items[i], listener);
		} //end loop
	    }
	}
    } //end LookupCacheImpl.addListener

    // This method's javadoc is inherited from an interface of this class
    @Override
    public void removeListener(ServiceDiscoveryListener listener) {
	checkCacheTerminated();
	if (listener == null) {
	    return;
	}
	synchronized (sItemListeners) {
	    sItemListeners.remove(listener);
	}
    } //end LookupCacheImpl.removeListener

    /**
     * Add a new ProxyReg to the lookupCache. Called by the constructor and the
     * DiscMgrListener's discovered() method.
     *
     * @param reg a ProxyReg to add.
     */
    public void addProxyReg(ProxyReg reg) {
	RegisterListenerTask treg = new RegisterListenerTask(reg, taskSeqN.getAndIncrement(), this);
	cacheTaskDepMgr.submit(treg);
    } //end LookupCacheImpl.addProxyReg

    /**
     * Remove a ProxyReg from the lookupCache. Called by DiscMgrListener's
     * discarded() method.
     *
     * @param reg a ProxyReg to remove.
     */
    public void removeProxyReg(ProxyReg reg) {
	ProxyRegDropTask t;
	//let the ProxyRegDropTask do the eventRegMap.remove
	EventReg eReg = eventRegMap.get(reg);
	if (eReg != null) {
	    try {
		sdm.leaseRenewalMgr.remove(eReg.lease);
	    } catch (Exception e) {
		ServiceDiscoveryManager.logger.log(Level.FINER, "exception occurred while removing an " + "event registration lease", e);
	    }
	} //endif
	t = new ProxyRegDropTask(reg, eReg, taskSeqN.getAndIncrement(), this);
	cacheTaskDepMgr.removeUselessTask(reg);
	cacheTaskDepMgr.submit(t);
    } //end LookupCacheImpl.removeProxyReg

    /* Throws IllegalStateException if this lookup cache has been
     * terminated
     */
    private void checkCacheTerminated() {
	sdm.checkTerminated();
	if (bCacheTerminated) {
	    throw new IllegalStateException("this lookup cache was terminated");
	} //endif
    } //end LookupCacheImpl.checkCacheTerminated

    /**
     * Called by the lookupListener's notify() method. Checks the event sequence
     * number and, based on whether or not a "gap" is found in in the event
     * sequence, performs lookup (if a gap was found) or processes the event if
     * no gap was found.
     *
     * Recall that the Event specification states that if the sequence numbers
     * of two successive events differ by only 1, then one can be assured that
     * no events were missed. On the other hand, if the difference is greater
     * than 1 (the sequence contains a "gap"), then one or more events may -- or
     * may not -- have been missed. Thus, if a gap is found in the events,
     * although it's possible that no events were missed, this method takes the
     * conservative approach by assuming events were missed. When this method
     * determines that an event may have been missed, it requests a current
     * "snapshot" of the given ServiceRegistrar's state by performing lookup.
     * Since this method can safely assume that no events have been missed if it
     * finds no gaps in the event sequence, it can safely process the event
     * maintaining equivalent state to the registrar, that is finding and
     * filtering new services and filtering updated or changed existing
     * services.
     *
     * Note that when a lookup service is discovered, this utility registers
     * with that lookup service's event mechanism for service events related to
     * the services of interest. Upon registering with the event mechanism, a
     * data structure (of type EventReg) containing information about that
     * registration is placed in a Map for later processing when events do
     * arrive. If the timing is right, it is possible that a service event may
     * arrive between the time the registration is made and the time the
     * EventReg is stored in the map. Thus, this method may find that the
     * eventRegMap does not contain an element corresponding to the event this
     * method is currently processing. In that case, this method will do
     * nothing. It will simply return so that the service referenced in the
     * event can be discovered using the snapshot returned by the lookup method
     * that is ultimately c by the RegisterListenerTask (whose listener
     * registration caused this method to be invoked in the first place).
     */
    private void notifyServiceMap(Object eventSource, 
				    long eventID, 
				    long seqNo,
				    ServiceID sid,
				    ServiceItem item,
				    int transition) 
    {
	if (eventSource == null) {
	    return;
	}
	/* Search eventRegMap for ProxyReg corresponding to event. */
	ProxyReg reg = null;
	EventReg eReg = null;
	Set<Map.Entry<ProxyReg, EventReg>> set = eventRegMap.entrySet();
	Iterator<Map.Entry<ProxyReg, EventReg>> iter = set.iterator();
	while (iter.hasNext()) {
	    Map.Entry<ProxyReg, EventReg> e = iter.next();
	    eReg = e.getValue();
	    if (eventID == eReg.eventID && eventSource.equals(eReg.source)) {
		reg = e.getKey();
		break;
	    } //endif
	} //end loop
	if (reg == null || eReg == null) {
	    return; //event arrived before eventReg in map
	}
	/* Next, look for any gaps in the event sequence. */
	synchronized (eReg) {
	    long delta = eReg.updateSeqNo(seqNo);
	    CacheTask t;
	    if (delta == 1) {
		//no gap, handle current event
		/* Fix for Bug ID 4378751. The conditions described by that
		 * bug involve a ServiceItem (corresponding to a previously
		 * discovered service ID) having a null service field. A
		 * null service field is due to an UnmarshalException caused
		 * by a SecurityException that results from the lack of a
		 * connection permission for the lookup service codebase
		 * to the service's remote codebase. Skip this ServiceItem,
		 * otherwise an un-expected serviceRemoved event will result
		 * because the primary if-block will be unintentionally
		 * entered due to the null service field in the ServiceItem.
		 */
		if ((item != null) && (item.service == null)) {
		    return;
		}
		/* Handle the event by the transition type, and by whether
		 * the associated ServiceItem is an old, previously discovered
		 * item, or a newly discovered item.
		 */
		if (transition == ServiceRegistrar.TRANSITION_MATCH_NOMATCH) {
		    handleMatchNoMatch(reg.getProxy(), sid);
		} else if (transition == ServiceRegistrar.TRANSITION_NOMATCH_MATCH || transition == ServiceRegistrar.TRANSITION_MATCH_MATCH) {
		    newOldService(reg, item, transition == ServiceRegistrar.TRANSITION_MATCH_MATCH);
		} //endif(transition)
	    } else if (delta == 0) {
	    } else if (delta < 0) {
	    } else {
		//gap in event sequence, request snapshot
		lookup(reg, eReg);
	    } //endif
	} //end sync(eReg)
    } //end LookupCacheImpl.notifyServiceMap

    /**
     * Requests a "snapshot" of the given registrar's state.
     */
    private void lookup(ProxyReg reg, EventReg eReg) {
	assert Thread.holdsLock(eReg);
	ServiceRegistrar proxy = reg.getProxy();
	ServiceItem[] items;
	/* For the given lookup, get all services matching the tmpl */
	try {
	    // Don't like the fact that we're calling foreign code while
	    // holding an object lock, however holding this lock doesn't
	    // provide an opportunity for DOS as the lock only relates to a specific
	    // ServiceRegistrar and doesn't interact with client code.
	    if (sdm.useInsecureLookup){
		ServiceMatches matches = proxy.lookup(tmpl, Integer.MAX_VALUE);
		items = matches.items;
	    } else {
		Object [] matches = proxy.lookUp(tmpl, Integer.MAX_VALUE);
		items = processBootStrapProxys(matches);
	    }
	} catch (Exception e) {
	    // ReRegisterGoodEquals test failure becomes more predictable
	    // when fail is only called if decrement is successful.
	    sdm.fail(e, proxy, this.getClass().getName(), "run", "Exception occurred during call to lookup", bCacheTerminated);
	    return;
	}
	if (items == null) {
	    throw new AssertionError("spec violation in queried lookup service: ServicesMatches instance returned by call to lookup() method contains null 'items' field");
	}
	/* 1. Cleanup "orphaned" itemReg's. */
	Iterator<Map.Entry<ServiceID, ServiceItemReg>> iter = serviceIdMap.entrySet().iterator();
	while (iter.hasNext()) {
	    Map.Entry<ServiceID, ServiceItemReg> e = iter.next();
	    ServiceID srvcID = e.getKey();
	    ServiceItem itemInSnapshot = findItem(srvcID, items);
	    if (itemInSnapshot != null) {
		continue; //not an orphan
	    }
	    if (Thread.currentThread().isInterrupted()) {
		continue; // skip
	    }
	    /* removal is performed whilst holding a lock on itemReg.*/
	    ServiceItemReg itemReg = e.getValue();
	    ServiceRegistrar prxy;
	    ServiceItem item;
	    synchronized (itemReg) {
		item = itemReg.removeProxy(reg.getProxy()); //disassociate the LUS
		if (item != null) {
		    // new LUS chosen to track changes
		    prxy = itemReg.getProxy();
		    itemMatchMatchChange(srvcID, itemReg, prxy, item, false);
		} else if (itemReg.hasNoProxys()) {
		    //no more LUSs, remove from map
		    item = itemReg.getFilteredItem();
		    iter.remove();
		    if (!itemReg.isDiscarded()) {
			removeServiceNotify(item);
		    }
		} //endif
	    }
	} //end loop
	/* 2. Handle "new" and "old" items from the given lookup */
	for (int i = 0, l = items.length; i < l; i++) {
	    /* Skip items with null service field (Bug 4378751) */
	    if (items[i].service == null) {
		continue;
	    }
	    newOldService(reg, items[i], false);
	} //end loop
    }

    /**
     * Method used to process the service state ("snapshot"), matching this
     * cache's template, retrieved from the given lookup service.
     *
     * After retrieving the snapshot S, the lookup method calls this method for
     * each service referenced in S. This method determines if the given service
     * is an already-discovered service (is currently in this cache's
     * serviceIdMap), or is a new service. This method handles the service
     * differently, depending on whether the service is a new or old.
     *
     * a. if the item is old, then this method will: - compare the given item
     * from the snapshot to the UN-filtered item in given itemReg if(same
     * version but attributes have changed) send changed event else if( version
     * has changed ) send removed event followed by added event else do nothing
     * - apply the filter to the given item if(filter fails) send removed event
     * else if(filter passes) set the filtered item in the itemReg in the map
     * else if (filter is indefinite) discard item send removed event queue
     * another filter attempt for later b. if the given item is newly
     * discovered, then this task will: - create a new ServiceItemReg containing
     * the given item - place the new itemReg in the serviceIdMap - apply the
     * filter to the given item if(filter fails) remove the item from the map
     * but send NO removed event else if(filter passes) send added event for the
     * FILTERED item else if (filter is indefinite) discard item queue another
     * filter attempt for later but send NO removed event
     */
    private void newOldService(ProxyReg reg, ServiceItem item, boolean matchMatchEvent) {
	boolean previouslyDiscovered = false;
	ServiceItemReg itemReg;
	ServiceID thisTaskSid = item.serviceID;
	itemReg = serviceIdMap.get(thisTaskSid);
	if (itemReg == null) {
	    if (!eventRegMap.containsKey(reg)) {
		/* reg must have been discarded, simply return */
		return;
	    } //endif
	    // else
	    itemReg = new ServiceItemReg(reg.getProxy(), item);
	    ServiceItemReg existed = serviceIdMap.putIfAbsent(thisTaskSid, itemReg);
	    if (existed != null) {
		itemReg = existed;
		previouslyDiscovered = true;
	    }
	} else if (itemReg.isDiscarded()) {
	    return;
	} else {
	    previouslyDiscovered = true;
	}
	if (previouslyDiscovered) {
	    //a. old, previously discovered item
	    itemMatchMatchChange(thisTaskSid, itemReg, reg.getProxy(), item, matchMatchEvent);
	} else {
	    //b. newly discovered item
	    ServiceItem newFilteredItem;
	    synchronized (itemReg) {
		newFilteredItem = filterMaybeDiscard(thisTaskSid, itemReg, item, false);
	    }
	    if (newFilteredItem != null) {
		addServiceNotify(newFilteredItem);
	    } //endif
	} //endif
    }

    /**
     * Removes an entry in the serviceIdMap, but sends no notification.
     */
    private boolean removeServiceIdMapSendNoEvent(ServiceID sid, ServiceItemReg itemReg) {
	return serviceIdMap.remove(sid, itemReg);
    } //end LookupCacheImpl.removeServiceIdMapSendNoEvent

    /**
     * Returns the element in the given items array having the given ServiceID.
     */
    private ServiceItem findItem(ServiceID sid, ServiceItem[] items) {
	if (items != null) {
	    for (int i = 0, length = items.length; i < length; i++) {
		if (items[i].serviceID.equals(sid)) {
		    return items[i];
		}
	    } //end loop
	} //endif
	return null;
    } //end LookupCacheImpl.findItem

    /**
     * With respect to a given service (referenced by the parameter newItem), if
     * either an event has been received from the given lookup service
     * (referenced by the proxy parameter), or a snapshot of the given lookup
     * service's state has been retrieved, this method determines whether the
     * service's attributes have changed, or whether a new version of the
     * service has been registered. After the appropriate determination has been
     * made, this method applies the filter associated with the current cache
     * and sends the appropriate local ServiceDiscoveryEvent(s).
     *
     * This method is called under the following conditions: - when a new lookup
     * service is discovered, this method will be called for each previously
     * discovered service - when a gap in the events from a previously
     * discovered lookup service is discovered, this method will be called for
     * each previously discovered service - when a MATCH_MATCH event is
     * received, this method will be called for each previously discovered
     * service - when a NOMATCH_MATCH event is received, this method will be
     * called for each previously discovered service Note that this method is
     * never called when a MATCH_NOMATCH event is received; such an event is
     * always handled by the handleMatchNoMatch method.
     *
     * When this method is called, it may send one of the following events or
     * combination of events: - a service changed event - a service removed
     * event followed by a service added event - a service removed event
     *
     * A service removed event is sent when the service either fails the filter,
     * or the filter produces an indefinite result; in which case, the service
     * is also discarded.
     *
     * A service changed event is sent when the service passes the filter, and
     * it is determined that the service's attributes have changed. In this
     * case, the old and new service proxies are treated as the same if one of
     * the following conditions is met: - this method was called because of the
     * receipt of a MATCH_MATCH event - the old and new service proxies are
     * byte-wise fully equal (Note that the lookup service specification
     * guarantees that the proxies are the same when a MATCH_MATCH event is
     * received.)
     *
     * A service removed event followed by a service added event is sent when
     * the service passes the filter, and the conditions for which a service
     * changed event would be considered are not met; that is, this method was
     * not called because of the receipt of a MATCH_MATCH event; or the old and
     * new service proxies are not byte-wise fully equal.
     *
     * The if-else-block contained in this method implements the logic just
     * described. The parameter matchMatchEvent reflects the pertinent event
     * state that causes this method to be called. That is, either a MATCH_MATCH
     * event was received, or it wasn't, (and if it wasn't, then a full
     * byte-wise comparison is performed to determine whether the proxies are
     * still the same).
     *
     * To understand when the 'else' part of the if-else-block is executed,
     * consider the following conditions: - there is more than one lookup
     * service with which the service registers (ex. LUS-0 and LUS-1) - after
     * the service registers with LUS-0, a NOMATCH_MATCH event is received and
     * handled (so the service is now known to the cache) - before the service
     * registers with LUS-1, the service is replaced with a new version - the
     * NOMATCH_MATCH event resulting from the service's registration with LUS-1
     * is received BEFORE receiving the MATCH_NOMATCH/NOMATCH_MATCH event
     * sequence that will ultimately result from the re-registration of that new
     * version with LUS-0 When the above conditions occur, the NOMATCH_MATCH
     * event that resulted from the service's registration with LUS-1 will cause
     * this method to be invoked and the proxies to be fully compared (because
     * the event was not a MATCH_MATCH event); and since the old service proxy
     * and the new service proxy will not be fully equal, the else part of the
     * if-else-block will be executed.
     *
     * This method applies the filter only after the above comparisons and
     * determinations have been completed.
     */
    private void itemMatchMatchChange(ServiceID srvcID, ServiceItemReg itemReg, ServiceRegistrar proxy, ServiceItem newItem, boolean matchMatchEvent) {
	/* Save the pre-event state. Update the post-event state after
	 * applying the filter.
	 */
	ServiceItem oldItem;
	ServiceItem oldFilteredItem;
	boolean notifyServiceRemoved;
	boolean attrsChanged = false;
	boolean versionChanged = false;
	ServiceRegistrar proxyChanged = null;
	ServiceItem newFilteredItem;
	synchronized (itemReg) {
	    if (itemReg != serviceIdMap.get(srvcID)) {
		return;
	    }
	    notifyServiceRemoved = !itemReg.isDiscarded();
	    oldItem = itemReg.getItem();
	    oldFilteredItem = itemReg.getFilteredItem();
	    if (itemReg.proxyNotUsedToTrackChange(proxy, newItem)) {
		// not tracking
		if (matchMatchEvent) {
		    return;
		}
		if (notifyServiceRemoved) {
		    return;
		}
		proxyChanged = proxy; // start tracking instead
	    } //endif
	    if (!notifyServiceRemoved) {
		itemReg.replaceProxyUsedToTrackChange(proxyChanged, newItem);
		itemReg.setFilteredItem(null);
		itemReg.discard();
		if (matchMatchEvent) {
		    return;
		}
	    } //endif
	    /* For an explanation of the logic of the following if-else-block,
	     * refer to the method description above.
	     */
	    if (matchMatchEvent || sameVersion(newItem, oldItem)) {
		if (!notifyServiceRemoved) {
		    return;
		}
		/* Same version, determine if the attributes have changed.
		 * But first, replace the new service proxy with the old
		 * service proxy so the client always uses the old proxy
		 * (at least, until the version is changed).
		 */
		//                newItem.service = oldItem.service; //Data race
		/* Now compare attributes */
		attrsChanged = !LookupAttributes.equal(newItem.attributeSets, oldItem.attributeSets);
		if (!attrsChanged) {
		    return; //no change, no need to filter
		}
	    } else {
		//(!matchMatchEvent && !same version) ==> re-registration
		versionChanged = true;
	    } //endif
	    /* Now apply the filter, and send events if appropriate */
	    newFilteredItem = filterMaybeDiscard(srvcID, itemReg, newItem, notifyServiceRemoved);
	}
	if (newFilteredItem != null) {
	    /* Passed the filter, okay to send event(s). */
	    if (attrsChanged) {
		changeServiceNotify(newFilteredItem, oldFilteredItem);
	    }
	    if (versionChanged) {
		if (notifyServiceRemoved) {
		    removeServiceNotify(oldFilteredItem);
		} //endif
		addServiceNotify(newFilteredItem);
	    } //endif
	} //endif
    }

    /**
     * Convenience method that performs a byte-wise comparison, including
     * codebases, of the services referenced by the given service items, and
     * returns the result. If the services cannot be compared, it is assumed
     * that the versions are not the same, and <code>false</code> is returned.
     */
    private boolean sameVersion(ServiceItem item0, ServiceItem item1) {
	boolean fullyEqual = false;
	try {
	    MarshalledInstance mi0 = new MarshalledInstance(item0.service);
	    MarshalledInstance mi1 = new MarshalledInstance(item1.service);
	    fullyEqual = mi0.fullyEquals(mi1);
	} catch (IOException e) {
	    ServiceDiscoveryManager.logger.log(Level.INFO, "failure marshalling old and new " + "services for equality check", e);
	}
	return fullyEqual;
    } //end LookupCacheImpl.sameVersion

    /**
     * Gets the remaining time left on the current cache's "lifespan".
     */
    public long getLeaseDuration() {
	if (leaseDuration == Long.MAX_VALUE) {
	    return Long.MAX_VALUE;
	}
	return leaseDuration + startTime - System.currentTimeMillis();
    } //end LookupCacheImpl.getLeaseDuration

    /**
     * Sends a notification to all listeners when a ServiceItem has been added.
     */
    private void addServiceNotify(ServiceItem item) {
	serviceNotifyDo(null, item, ITEM_ADDED);
    } //end LookupCacheImpl.addServiceNotify

    /**
     * Sends a notification to the given listener when a ServiceItem has been
     * added.
     */
    private void addServiceNotify(ServiceItem item, ServiceDiscoveryListener srvcListener) {
	eventNotificationExecutor.execute(new ServiceNotifyDo(null, item, ITEM_ADDED, srvcListener, this));
	if (ServiceDiscoveryManager.logger.isLoggable(Level.FINEST)) {
	    try {
		throw new Exception("Back Trace");
	    } catch (Exception ex) {
		ex.fillInStackTrace();
		ServiceDiscoveryManager.logger.log(Level.FINEST, "Log back trace", ex);
	    }
	}
    } //end LookupCacheImpl.addServiceNotify

    /**
     * Sends a notification when a ServiceItem has been removed.
     */
    private void removeServiceNotify(ServiceItem item) {
	serviceNotifyDo(item, null, ITEM_REMOVED);
    } //end LookupCacheImpl.removeServiceNotify

    /**
     * Sends a notification when a ServiceItem has been changed, but still
     * matches.
     */
    private void changeServiceNotify(ServiceItem newItem, ServiceItem oldItem) {
	serviceNotifyDo(oldItem, newItem, ITEM_CHANGED);
    } //end LookupCacheImpl.changeServiceNotify

    /**
     * Common code for performing service notification to all listeners.
     */
    private void serviceNotifyDo(ServiceItem oldItem, ServiceItem item, int action) {
	synchronized (sItemListeners) {
	    if (sItemListeners.isEmpty()) {
		return;
	    }
	    Iterator<ServiceDiscoveryListener> iter = sItemListeners.iterator();
	    while (iter.hasNext()) {
		ServiceDiscoveryListener sl = iter.next();
		eventNotificationExecutor.execute(new ServiceNotifyDo(oldItem, item, action, sl, this));
		if (ServiceDiscoveryManager.logger.isLoggable(Level.FINEST)) {
		    try {
			throw new Exception("Back Trace");
		    } catch (Exception ex) {
			ex.fillInStackTrace();
			ServiceDiscoveryManager.logger.log(Level.FINEST, "Log back trace", ex);
		    }
		}
	    } //end loop
	}
    } //end LookupCacheImpl.serviceNotifyDo

    /**
     * Common code for performing service notification to one listener in an
     * executor task thread.
     */
    private static class ServiceNotifyDo implements Runnable {

	final ServiceItem oldItem;
	final ServiceItem item;
	final int action;
	final ServiceDiscoveryListener sl;
	final Object lookupCache;

	ServiceNotifyDo(ServiceItem oldItem, ServiceItem item, int action, ServiceDiscoveryListener sl, LookupCache lookupCache) {
	    this.oldItem = oldItem;
	    this.item = item;
	    this.action = action;
	    this.sl = sl;
	    this.lookupCache = lookupCache;
	}

	@Override
	public void run() {
	    ServiceDiscoveryEvent event;
	    try {
		event = new ServiceDiscoveryEvent(lookupCache, oldItem, item);
	    } catch (NullPointerException e) {
		boolean lookupCacheNull = lookupCache == null;
		boolean oldItemNull = oldItem == null;
		boolean itemNull = item == null;
		ServiceDiscoveryManager.logger.log(Level.INFO, "ServiceDiscoveryEvent constructor threw NullPointerException, lookupCache null? {0} oldItem null? {1} item null? {2}", new Object[]{lookupCacheNull, oldItemNull, itemNull});
		return;
	    }
	    switch (action) {
		case ITEM_ADDED:
		    sl.serviceAdded(event);
		    break;
		case ITEM_REMOVED:
		    sl.serviceRemoved(event);
		    break;
		case ITEM_CHANGED:
		    sl.serviceChanged(event);
		    break;
		default:
		    throw new IllegalArgumentException("case must be one of the following: ITEM_ADDED, ITEM_REMOVED or ITEM_CHANGED");
	    } //end switch(action)
	}
    }

    void initCache() throws RemoteException {
	/* Get the exporter for the remote event listener from the
	 * configuration.
	 */
	try {
	    Exporter defaultExporter = new BasicJeriExporter(TcpServerEndpoint.getInstance(0), new BasicILFactory(), false, false);
	    lookupListenerExporter = sdm.thisConfig.getEntry(ServiceDiscoveryManager.COMPONENT_NAME, "eventListenerExporter", Exporter.class, defaultExporter);
	} catch (ConfigurationException e) {
	    // exception, use default
	    ExportException e1 = new ExportException("Configuration exception while " + "retrieving exporter for " + "cache's remote event listener", e);
	    throw e1;
	}
	/*
	 * Executor dedicated to event notification.
	 */
	try {
	    eventNotificationExecutor = sdm.thisConfig.getEntry(ServiceDiscoveryManager.COMPONENT_NAME, "eventNotificationExecutor", ExecutorService.class);
	} catch (ConfigurationException e) {
	    /* use default */
	    eventNotificationExecutor = new ThreadPoolExecutor(2, 30, 15, TimeUnit.SECONDS, new ArrayBlockingQueue<Runnable>(20), new NamedThreadFactory("SDM event notifier", false), new ThreadPoolExecutor.CallerRunsPolicy());
	}
	/* Get a general-purpose task manager for this cache from the
	 * configuration. This task manager will be used to manage the
	 * various tasks executed by this instance of the lookup cache.
	 */
	try {
	    cacheTaskMgr = sdm.thisConfig.getEntry(ServiceDiscoveryManager.COMPONENT_NAME, "cacheExecutorService", ExecutorService.class);
	} catch (ConfigurationException e) {
	    /* use default */
	    cacheTaskMgr = new ThreadPoolExecutor(10, 10, 15, TimeUnit.SECONDS, new LinkedBlockingQueue<Runnable>(), new NamedThreadFactory("SDM lookup cache", false));
	}
	cacheTaskMgr = new ExtensibleExecutorService(cacheTaskMgr, new ExtensibleExecutorService.RunnableFutureFactory() {
	    @Override
	    public <T> RunnableFuture<T> newTaskFor(Runnable r, T value) {
		if (r instanceof ObservableFutureTask) {
		    return (RunnableFuture<T>) r;
		}
		return new CacheTaskWrapper<T>(r, value);
	    }

	    @Override
	    public <T> RunnableFuture<T> newTaskFor(Callable<T> c) {
		if (c instanceof ObservableFutureTask) {
		    return (RunnableFuture<T>) c;
		}
		return new CacheTaskWrapper<T>(c);
	    }
	});
	cacheTaskDepMgr = new CacheTaskDependencyManager(cacheTaskMgr);
	/* Get a special-purpose task manager for this cache from the
	 * configuration. That task manager will be used to manage the
	 * various instances of the special-purpose task, executed by
	 * this instance of the lookup cache, that waits on verification
	 * events after a previousy discovered service has been discarded.
	 */
	try {
	    serviceDiscardTimerTaskMgr = sdm.thisConfig.getEntry(ServiceDiscoveryManager.COMPONENT_NAME, "discardExecutorService", ExecutorService.class);
	} catch (ConfigurationException e) {
	    /* use default */
	    serviceDiscardTimerTaskMgr = new ThreadPoolExecutor(10, 10, 15, TimeUnit.SECONDS, new LinkedBlockingQueue<Runnable>(), new NamedThreadFactory("SDM discard timer", false));
	}
	// Moved here from constructor to avoid publishing this reference
	lookupListenerProxy = lookupListener.export();
	Iterator<ProxyReg> it = sdm.proxyRegSet.iterator();
	while (it.hasNext()) {
	    addProxyReg(it.next());
	}
    } //end LookupCacheImpl.initCache

    /**
     * Applies the first-stage <code>filter</code> associated with the current
     * instance of <code>LookupCache</code> to the given <code>item</code> and
     * returns the resulting filtered item if the <code>filter</code> is passed
     * (or is <code>null</code>); otherwise, returns <code>null</code> and sends
     * a service removed event if the <code>sendEvent</code> parameter is
     * <code>true</code>.
     * <p>
     * This method is called only when the <code>item</code> to be filtered
     * corresponds to an element that currently exists in the
     * <code>serviceIdMap</code>.
     * <p>
     * As described in the <code>ServiceItemFilter</code> specification, when
     * the <code>item</code> passes the <code>filter</code>, the
     * <code>service</code> field of the <code>item</code> is replaced with the
     * filtered form of the object previously contained in that field. In this
     * case, the <code>filteredItem</code> field of the corresponding
     * <code>ServiceItemReg</code> element of the <code>serviceIdMap</code> is
     * set to this new filtered item.
     * <p>
     * If the <code>filter</code> returns <code>indefinite</code>, then that
     * specification states that the <code>service</code> field is replaced with
     * <code>null</code>. In this case, the <code>filteredItem</code> field of
     * the corresponding <code>ServiceItemReg</code> element of the
     * <code>serviceIdMap</code> is left unchanged.
     */
    private ServiceItem filterMaybeDiscard(ServiceID srvcID, ServiceItemReg itemReg, ServiceItem item, boolean sendEvent) {
	if ((item == null) || (item.service == null)) {
	    return null;
	}
	if (filter == null) {
	    ServiceItem filteredItem = item.clone();
	    if (sdm.useInsecureLookup){
		return addFilteredItemToMap(item, filteredItem, itemReg);
	    } else {
		try {
		    filteredItem.service =
			    ((ServiceProxyAccessor) filteredItem.service).getServiceProxy();
		    return addFilteredItemToMap(item, filteredItem, itemReg);
		} catch (RemoteException ex) {
		    ServiceDiscoveryManager.logger.log(Level.FINE,
			    "Exception thrown while trying to download service proxy",
			    ex);
		    discardRetryLater(item, sendEvent, itemReg);
		    return null;
		}
	    }
	} //endif
	/* Make a copy to filter because the filter may modify it. */
	ServiceItem filteredItem = item.clone();
	boolean pass;
	if (sdm.useInsecureLookup){
	    pass = filter.check(filteredItem);
	} else {
	    try {
		pass = filter.check(filteredItem);
	    } catch (ClassCastException | SecurityException ex){ 
		try {
		    // Filter didn't expect bootstrap proxy
		    filteredItem.service = ((ServiceProxyAccessor) filteredItem.service).getServiceProxy();
		    pass = filter.check(filteredItem);
		} catch (RemoteException ex1) {
		    ServiceDiscoveryManager.logger.log(Level.SEVERE, 
			"Exception thrown while trying to download service proxy",
			ex1);
		    discardRetryLater(item, sendEvent, itemReg);
		    return null;
		}
	    }
	}
	/* Handle filter fail */
	if (!pass) {
	    boolean notify = false;
	    ServiceItem oldFilteredItem = null;
	    if (itemReg != null) {
		if (sendEvent) {
		    oldFilteredItem = itemReg.getFilteredItem();
		    notify = removeServiceIdMapSendNoEvent(srvcID, itemReg);
		} else {
		    removeServiceIdMapSendNoEvent(srvcID, itemReg);
		} //endif
		if (notify) {
		    removeServiceNotify(oldFilteredItem);
		}
	    } //endif
	    return null;
	} //endif(fail)
	/* Handle filter pass */
	if (filteredItem.service != null) {
	    return addFilteredItemToMap(item, filteredItem, itemReg);
	} //endif(pass)
	/* Handle filter indefinite */
	discardRetryLater(item, sendEvent, itemReg);
	return null;
    } //end LookupCacheImpl.filterMaybeDiscard

    /**
     * Convenience method called by <code>filterMaybeDiscard</code> that finds
     * the <code>ServiceItemReg</code> element in the <code>serviceIdMap</code>
     * that corresponds to the given <code>ServiceItem</code> and, if such an
     * element is found, replaces the <code>item</code> field of that element
     * with the given <code>item</code> parameter; and sets the
     * <code>filteredItem</code> field of that element to the value contained in
     * the <code>filteredItem</code> parameter.
     */
    private ServiceItem addFilteredItemToMap(ServiceItem item, ServiceItem filteredItem, ServiceItemReg itemReg) {
	ServiceID id = item.serviceID;
	assert Thread.holdsLock(itemReg);
	if (itemReg == serviceIdMap.get(id)) {
	    cancelDiscardTask(id);
	    itemReg.replaceProxyUsedToTrackChange(null, item);
	    itemReg.setFilteredItem(filteredItem);
	    return filteredItem;
	}
	return null;
    } //end LookupCacheImpl.addFilteredItemToMap

    /**
     * Convenience method called by <code>filterMaybeDiscard</code> that finds
     * in the <code>serviceIdMap</code>, the <code>ServiceItemReg</code> element
     * corresponding to the given <code>ServiceItem</code>, sets a service
     * removed event, and queues a <code>ServiceDiscardTimerTask</code> to retry
     * the filter at a later time. If the <code>serviceIdMap</code> does not
     * contain a <code>ServiceItemReg</code> corresponding to the given
     * <code>ServiceItem</code>, then this method simply returns.
     */
    private void discardRetryLater(ServiceItem item, boolean sendEvent, ServiceItemReg itemReg) {
	ServiceItem oldFilteredItem;
	oldFilteredItem = itemReg.getFilteredItem();
	/* If there's been any change in what is being discarded for
	 * filter retry, then update the item field in the map to
	 * capture that change; and set the filteredItem field to
	 * to null to guarantee that the filter is re-applied to
	 * that changed item.
	 */
	if (itemReg.discard()) {
	    itemReg.replaceProxyUsedToTrackChange(null, item);
	    itemReg.setFilteredItem(null);
	    Future f = serviceDiscardTimerTaskMgr.submit(new ServiceDiscardTimerTask(this, item.serviceID));
	    serviceDiscardFutures.put(item.serviceID, f);
	    if (sendEvent) {
		removeServiceNotify(oldFilteredItem); // Maybe send anyway?
	    }
	}
    } //end LookupCacheImpl.discardRetryLater

    /**
     * Convenience method called (only when a TRANSITION_MATCH_NOMATCH event is
     * received) that removes the given <code>item</code> from the
     * <code>serviceIdMap</code> and wakes up the
     * <code>ServiceDiscardTimerTask</code> if the given <code>item</code> is
     * discarded; otherwise, sends a removed event.
     */
    private void handleMatchNoMatch(ServiceRegistrar proxy, ServiceID srvcID) {
	ServiceItemReg itemReg = serviceIdMap.get(srvcID);
	if (itemReg != null) {
	    ServiceItem newItem;
	    ServiceItem filteredItem;
	    boolean notify = false;
	    ServiceRegistrar itemRegProxy = null;
	    synchronized (itemReg) {
		newItem = itemReg.removeProxy(proxy);
		filteredItem = itemReg.getFilteredItem();
		if (newItem != null) {
		    itemRegProxy = itemReg.getProxy();
		} else if (itemReg.hasNoProxys()) {
		    if (itemReg.isDiscarded()) {
			/* Remove item from map and wake up the discard task */
			removeServiceIdMapSendNoEvent(srvcID, itemReg);
			cancelDiscardTask(srvcID);
		    } else {
			//remove item from map and send removed event
			notify = removeServiceIdMapSendNoEvent(srvcID, itemReg);
		    } //endif
		} //endif
	    }
	    if (itemRegProxy != null) {
		itemMatchMatchChange(srvcID, itemReg, itemRegProxy, newItem, false);
	    } else if (notify) {
		removeServiceNotify(filteredItem);
	    }
	} //endif
    } //end LookupCacheImpl.handleMatchNoMatch

    /**
     * Wake up service discard task if running, else remove from mgr.
     */
    private void cancelDiscardTask(ServiceID sid) {
	// Might need to record future's and cancel from there.
	Future task = serviceDiscardFutures.get(sid);
	if (task != null) {
	    task.cancel(true);
	}
    } //end LookupCacheImpl.cancelDiscardTask

    /**
     * Used in the LookupCache. For each LookupCache, there is a HashMap that
     * maps ServiceId to a ServiceItemReg. The ServiceItemReg class helps track
     * where the ServiceItem comes from.
     */
    final static class ServiceItemReg {
	/* Maps ServiceRegistrars to their latest registered item */

	private final Map<ServiceRegistrar, ServiceItem> items;
	/* The ServiceRegistrar currently being used to track changes */
	private ServiceRegistrar proxy;
	/* Flag that indicates that the ServiceItem has been discarded. */
	private boolean bDiscarded;
	/* The discovered service, prior to filtering. */
	private ServiceItem item;
	/* The discovered service, after filtering. */
	private ServiceItem filteredItem;
	/* Creates an instance of this class, and associates it with the given
	 * lookup service proxy.
	 */

	public ServiceItemReg(ServiceRegistrar proxy, ServiceItem item) {
	    this.bDiscarded = false;
	    items = new HashMap<ServiceRegistrar, ServiceItem>();
	    this.proxy = proxy;
	    items.put(proxy, item);
	    this.item = item;
	    filteredItem = null;
	}

	/* Adds the given proxy to the 'proxy-to-item' map. This method is
	 * called by the newOldService method.  Returns false if the proxy is being used
	 * to track changes, true otherwise.
	 */
	public boolean proxyNotUsedToTrackChange(ServiceRegistrar proxy, ServiceItem item) {
	    synchronized (this) {
		items.put(proxy, item);
		return !proxy.equals(this.proxy);
	    }
	}

	/**
	 * Replaces the proxy used to track change if the proxy passed in is non
	 * null, also replaces the ServiceItem.
	 *
	 * @param proxy replacement proxy
	 * @param item replacement item.
	 */
	public void replaceProxyUsedToTrackChange(ServiceRegistrar proxy, ServiceItem item) {
	    synchronized (this) {
		if (proxy != null) {
		    this.proxy = proxy;
		}
		this.item = item;
	    }
	}
	/* Removes the given proxy from the 'proxy-to-item' map. This method
	 * is called from the lookup, handleMatchNoMatch methods and 
	 * ProxyRegDropTask.  If this proxy was being used to track changes, 
	 * then pick a new one and return its current item, else return null.
	 */

	public ServiceItem removeProxy(ServiceRegistrar proxy) {
	    synchronized (this) {
		items.remove(proxy);
		if (proxy.equals(this.proxy)) {
		    if (items.isEmpty()) {
			this.proxy = null;
		    } else {
			Map.Entry ent = (Map.Entry) items.entrySet().iterator().next();
			this.proxy = (ServiceRegistrar) ent.getKey();
			return (ServiceItem) ent.getValue();
		    }//endif
		}//endif
	    }
	    return null;
	}
	/* Determines if the 'proxy-to-item' map contains any mappings.
	 */

	public boolean hasNoProxys() {
	    synchronized (this) {
		return items.isEmpty();
	    }
	}
	/* Returns the flag indicating whether the ServiceItem is discarded. */

	public boolean isDiscarded() {
	    synchronized (this) {
		return bDiscarded;
	    }
	}

	/* Discards if not discarded and returns true if successful */
	public boolean discard() {
	    synchronized (this) {
		if (!bDiscarded) {
		    bDiscarded = true;
		    return true;
		}
		return false;
	    }
	}

	/* Undiscards if discarded and returns true if successful */
	public boolean unDiscard() {
	    synchronized (this) {
		if (bDiscarded) {
		    bDiscarded = false;
		    return true;
		}
		return false;
	    }
	}

	/**
	 * @return the proxy
	 */
	public ServiceRegistrar getProxy() {
	    synchronized (this) {
		return proxy;
	    }
	}

	/**
	 * @return the filteredItem
	 */
	public ServiceItem getFilteredItem() {
	    synchronized (this) {
		return filteredItem;
	    }
	}

	/**
	 * @param filteredItem the filteredItem to set
	 */
	public void setFilteredItem(ServiceItem filteredItem) {
	    synchronized (this) {
		this.filteredItem = filteredItem;
	    }
	}

	/**
	 * @return the item
	 */
	public ServiceItem getItem() {
	    synchronized (this) {
		return item;
	    }
	}
    }

    /**
     *      */
    final static class CacheTaskDependencyManager implements FutureObserver {

	// CacheTasks pending completion.

	private final ConcurrentLinkedQueue<CacheTaskWrapper> pending;
	private final ExecutorService executor;

	CacheTaskDependencyManager(ExecutorService e) {
	    this.pending = new ConcurrentLinkedQueue<CacheTaskWrapper>();
	    executor = e;
	}

	CacheTaskWrapper submit(Runnable t) {
	    CacheTaskWrapper future = new CacheTaskWrapper(t, null);
	    pending.offer(future);
	    future.addObserver(this);
	    if (t instanceof CacheTask && ((CacheTask) t).hasDeps()) {
		List<FutureObserver.ObservableFuture> deps = new LinkedList<FutureObserver.ObservableFuture>();
		Iterator<CacheTaskWrapper> it = pending.iterator();
		while (it.hasNext()) {
		    CacheTaskWrapper w = it.next();
		    Object c = w.getTask();
		    if (c instanceof CacheTask && ((CacheTask) t).dependsOn((CacheTask) c)) {
			deps.add(w);
		    }
		}
		if (deps.isEmpty()) {
		    executor.submit(future);
		    ServiceDiscoveryManager.logger.log(Level.FINEST, "ServiceDiscoveryManager {0} submitted to executor task queue", t.toString());
		} else {
		    DependencyLinker linker = new DependencyLinker(executor, deps, future);
		    linker.register();
		    ServiceDiscoveryManager.logger.log(Level.FINEST, "ServiceDiscoveryManager {0} registered dependencies", t.toString());
		}
	    } else {
		executor.submit(future);
		ServiceDiscoveryManager.logger.log(Level.FINEST, "ServiceDiscoveryManager {0} submitted to executor task queue", t.toString());
	    }
	    return future;
	}

	@Override
	public void futureCompleted(Future e) {
	    pending.remove(e);
	    Object t;
	    if (e instanceof CacheTaskWrapper) {
		t = ((CacheTaskWrapper) e).getTask();
	    } else {
		t = e;
	    }
	    ServiceDiscoveryManager.logger.log(Level.FINEST, "ServiceDiscoveryManager {0} completed execution", t.toString());
	}

	/**
	 * Removes from the cache's task queue, all pending tasks associated
	 * with the given ProxyReg. This method is called when the given
	 * ProxyReg has been discarded.
	 */
	void removeUselessTask(ProxyReg reg) {
	    Iterator<CacheTaskWrapper> it = pending.iterator();
	    while (it.hasNext()) {
		CacheTaskWrapper w = it.next();
		Object t = w.getTask();
		if (t instanceof CacheTask && ((CacheTask) t).isFromProxy(reg)) {
		    w.cancel(true); // Also causes task to be removed
		    ServiceDiscoveryManager.logger.log(Level.FINEST, "ServiceDiscoveryManager {0} cancelled", t.toString());
		}
	    } //end loop
	} //end LookupCacheImpl.removeUselessTask

    }

    /**
     * ObservableFuture wrapper class for CacheTask's.
     *
     * @param <T>
     */
    final static class CacheTaskWrapper<T> extends ObservableFutureTask<T> {

	private final Object task;

	CacheTaskWrapper(Runnable r, T result) {
	    super(r, result);
	    task = r;
	}

	CacheTaskWrapper(Callable<T> c) {
	    super(c);
	    task = c;
	}

	Object getTask() {
	    return task;
	}

    }

    /**
     * Class for implementing register/lookup/notify/dropProxy/discard tasks
     */
    abstract static class CacheTask implements Runnable {

	protected final ProxyReg reg;
	protected volatile long thisTaskSeqN;

	protected CacheTask(ProxyReg reg, long seqN) {
	    this.reg = reg;
	    this.thisTaskSeqN = seqN;
	    ServiceDiscoveryManager.logger.log(Level.FINEST, "ServiceDiscoveryManager {0} constructed", this.toString());
	} //end constructor
	/* check if the task is on a specific ProxyReg, return true if it is */

	public boolean isFromProxy(ProxyReg reg) {
	    if (this.reg == null) {
		return false;
	    }
	    return (this.reg).equals(reg);
	} //end isFromProxy

	/**
	 * Returns the ProxyReg associated with this task (if any).
	 */
	public ProxyReg getProxyReg() {
	    return reg;
	} //end ProxyReg

	/**
	 * Returns the unique sequence number of this task.
	 */
	public long getSeqN() {
	    return thisTaskSeqN;
	} //end getSeqN

	public abstract boolean hasDeps();

	public boolean dependsOn(CacheTask task) {
	    return false;
	}
    } //end class CacheTask
}
