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

import net.jini.core.lookup.ServiceItem;

/**
 * The <code>ServiceDiscoveryEvent</code> class encapsulates the
 * service discovery information made available by the event mechanism
 * of the {@link net.jini.lookup.LookupCache LookupCache}.  All listeners
 * that an entity has registered with the cache's event mechanism will
 * receive an event of type <code>ServiceDiscoveryEvent</code> upon
 * the discovery, removal, or modification of one of the cache's services.
 * This class is used by 
 * {@link net.jini.lookup.ServiceDiscoveryManager ServiceDiscoveryManager}.
 * 
 * @author Sun Microsystems, Inc.
 *
 * @see ServiceDiscoveryManager
 */
public class ServiceDiscoveryEvent extends java.util.EventObject {
    private static final long serialVersionUID = -4654412297235019084L;

    /** Represents the state of the service prior to the event.
     *
     *  @serial
     */
    private final ServiceItem preEventItem;

    /** Represents the state of the service after the event.
     *
     *  @serial
     */
    private final ServiceItem postEventItem;

    /**
     * The constructor of <code>ServiceDiscoveryEvent</code> takes
     * three arguments:
     * 
     * <li>An instance of <code>Object</code> corresponding to the
     * instance of <code>LookupCache</code> from which the given event
     * originated</li>
     * <p>
     * <li>A <code>ServiceItem</code> reference representing the state
     * of the service (associated with the given event) prior to the
     * occurrence of the event</li>
     * <p>
     * <li>A <code>ServiceItem</code> reference representing the state
     * of the service after the occurrence of the event</li>
     * 
     * <p>
     * If <code>null</code> is passed as the source parameter for the
     * constructor, a <code>NullPointerException</code> will be thrown.
     * <p>
     * Depending on the nature of the discovery event, a null reference
     * may be passed as one or the other of the remaining parameters, but
     * never both. If <code>null</code> is passed as both the
     * <code>preEventItem </code>and the <code>postEventItem</code>
     * parameters, a <code>NullPointerException</code> will be thrown.
     * <p>
     * Note that the constructor will not modify the contents of either
     * <code>ServiceItem</code> argument. Doing so can result in
     * unpredictable and undesirable effects on future processing by the
     * <code>ServiceDiscoveryManager</code>. That is why the effects of any
     * such modification to the contents of either input parameter are
     * undefined.
     *
     * @param source an instance of <code>Object</code> corresponding 
     * 			to the instance of <code>LookupCache</code> from 
			which the given event originated.
     *
     * @param preEventItem a <code>ServiceItem</code> reference
     * 			representing the state of the service (associated 
     * 			with the given event) prior to the occurrence of 
     * 			the event. 
     *
     * @param postEventItem a <code>ServiceItem</code> reference 
     *  		representing the state of the service after the 
     * 			occurrence of the event.
     *
     * @throws <code>NullPointerException</code> if <code>null</code> is 
     *			passed as the source parameter for the constructor, 
     * 			or if <code>null</code> is passed as both the 
     * 			<code>preEventItem </code>and the 
     * 			<code>postEventItem</code> parameters.
     */
    public ServiceDiscoveryEvent(
            Object source, 
            ServiceItem preEventItem,
            ServiceItem postEventItem) 
    {
	this(source, preEventItem, postEventItem, 
                nullCheck(source, preEventItem, postEventItem));
    }
    
    private static boolean nullCheck( //Prevent finalizer attack.
            Object source, 
            ServiceItem preEventItem,
            ServiceItem postEventItem) throws NullPointerException 
    {
        if((preEventItem == null && postEventItem == null)|| source == null)
	    throw new NullPointerException();
        return true;
    }
    
    private ServiceDiscoveryEvent(
            Object source,
            ServiceItem preEventItem,
            ServiceItem postEventItem,
            boolean check)
    {
        super(source);
	if(preEventItem != null)
	    this.preEventItem = new ServiceItem(preEventItem.serviceID,
					    preEventItem.service,
					    preEventItem.attributeSets);
        else this.preEventItem = null;
	if(postEventItem != null)
	    this.postEventItem = new ServiceItem(postEventItem.serviceID,
					     postEventItem.service,
					     postEventItem.attributeSets);
        else this.postEventItem = null;
    }

    /**
     * Returns an instance of a <code>ServiceItem</code> containing the
     * service reference corresponding to the given event. The service
     * state reflected in the returned service item is the state of the
     * service prior to the occurrence of the event.
     * <p>
     * If the event is a discovery event (as opposed to a removal or
     * modification event), then this method will return <code>null</code> 
     * because the discovered service had no state in the cache prior to 
     * its discovery.
     * <p>
     * Because making a copy can be a very expensive process, this
     * method does not return a copy of the service reference associated
     * with the given event. Rather, it returns the appropriate service
     * reference from the cache itself. Due to this cost, listeners that
     * receive a <code>ServiceDiscoveryEvent</code> must not modify the
     * contents of the object returned by this method; doing so could
     * cause the state of the cache to become corrupted or inconsistent
     * because the objects returned by this method are also members of
     * the cache. This potential for corruption or inconsistency is why
     * the effects of modifying the object returned by this accessor
     * method are undefined.
     *
     * @return ServiceItem containing the service reference corresponding 
     *  		to the given event.
     */
    public ServiceItem getPreEventServiceItem() 
    {
	return preEventItem;
    }

    /**
     * Returns an instance of a <code>ServiceItem</code> containing the
     * service reference corresponding to the given event. The service
     * state reflected in the returned service item is the state of the
     * service after the occurrence of the event.  
     * <p> 
     * If the event is a removal event, then this method will return 
     * <code>null</code> because the discovered service has no state in  
     * the cache after it is removed from the cache.
     * <p>
     * Because making a copy can be a very expensive process, this
     * method does not return a copy of the service reference associated
     * with the given event. Rather, it returns the appropriate service
     * reference from the cache itself. Due to this cost, listeners that
     * receive a <code>ServiceDiscoveryEvent</code> must not modify the
     * contents of the object returned by this method; doing so could
     * cause the state of the cache to become corrupted or inconsistent
     * because the objects returned by this method are also members of
     * the cache. This potential for corruption or inconsistency is why
     * the effects of modifying the object returned by this accessor
     * method are undefined.
     *
     * @return ServiceItem containing the service reference corresponding 
     *  		to the given event.
     */
    public ServiceItem getPostEventServiceItem() 
    {
	return postEventItem;
    }
	
}
