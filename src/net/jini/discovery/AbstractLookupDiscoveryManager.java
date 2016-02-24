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

import org.apache.river.logging.Levels;
import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Logger;
import net.jini.core.discovery.LookupLocator;
import net.jini.core.lookup.ServiceRegistrar;

/**
 * This package private superclass of LookupDiscoverManager exists for 
 * safe construction of LookupDiscoveryManager's state, while retaining
 * backward compatibility of the API.
 * 
 * See www.securecoding.cert.org TSM01-J and OBJ11-J
 * 
 */
abstract class AbstractLookupDiscoveryManager implements DiscoveryManagement,
                                       DiscoveryGroupManagement,
                                       DiscoveryLocatorManagement {
    /* Name of this component; used in config entry retrieval and the logger.*/
    private static final String COMPONENT_NAME
                                        = "net.jini.discovery.LookupDiscoveryManager";
    /* Logger used by this utility. */
    private static final Logger logger = Logger.getLogger(COMPONENT_NAME);
    /** Constant that indicates the discovery mechanism is group discovery */
    public static final int FROM_GROUP = 1;
    /** Constant that indicates the discovery mechanism is locator discovery */
    public static final int FROM_LOCATOR = 2;

    /** Constants used to indicate the type of event to send */
    private static final int DISCOVERED = 0;
    private static final int DISCARDED  = 1;
    private static final int CHANGED    = 2;

    /** Contains instances of the <code>ProxyReg</code> wrapper class. This
     *  set acts as what is referred to as the "managed set of registrars"
     *  or simply, the "managed set". When a registrar is discovered, that
     *  registrar and its associated "discovered state" is wrapped in an
     *  instance of <code>ProxyReg</code>, which is then placed in this set,
     *  which is managed by this <code>LookupDiscoveryManager</code>. As
     *  registrars are discovered and discarded, and as the discovered state
     *  of those registrars is modified, the contents of this set are modified
     *  appropriately.
     *
     *  Note that this set is shared across threads; therefore, when
     *  accessing or modifying the contents of this set, the appropriate
     *  synchronization must be applied.
     * 
     *  A list is used instead of a Set, because hashCode and equals are 
     *  remote calls.  However we need to avoid duplicates and since this 
     *  requires list traversal using remote method calls during add, 
     *  we need a lock to prevent concurrent additions.  Removal while an
     *  add operation is in progress would be acceptable, since the worst
     *  case scenario is the iterator snapshot would contain the removed
     *  ProxyReg.
     */
    private final List<ProxyReg> discoveredSet = new CopyOnWriteArrayList<ProxyReg>(); // sync(discoveredSet)
    private final Lock discoveredSetAddLock = new ReentrantLock();
    /** Contains the instances of <code>DiscoveryListener</code> that clients
     *  register with the <code>LookupDiscoveryManager</code>. The elements
     *  of this set receive discovered events, discarded events and, when
     *  appropriate, changed events.
     */
    private final Set<DiscoveryListener> listeners = Collections.newSetFromMap(new ConcurrentHashMap<DiscoveryListener,Boolean>());
    /** The <code>LookupDiscovery</code> utility used to manage the group
     *  discovery mechanism. Note that this object cannot be accessed outside
     *  of this <code>LookupDiscoveryManager</code>.
     */
    private final LookupDiscovery lookupDisc; // Inconsistent sync(discoveredSet) OR sync ProxyReg object OR no sync, set in beginDiscovery
    /** The listener that receives discovered, discarded, and changed events
     *  from the <code>LookupDiscovery</code> utility that manages group
     *  discovery on behalf of this <code>LookupDiscoveryManager</code>.
     */
    private final DiscoveryListener groupListener = new GroupDiscoveryListener();
    /** The <code>LookupLocatorDiscovery</code> utility used to manage the
     *  locator discovery mechanism. Note that this object cannot be accessed
     *  outside of this <code>LookupDiscoveryManager</code>.
     */
    private final LookupLocatorDiscovery locatorDisc; // no sync
    /** The listener that receives discovered and discarded events from the
     *  <code>LookupLocatorDiscovery</code> utility that manages locator
     *  discovery on behalf of this <code>LookupDiscoveryManager</code>.
     */
    private final DiscoveryListener locatorListener = new LocatorDiscoveryListener();

    /** Wrapper class in which each instance corresponds to a lookup service
     *  that has been discovered via either group discovery, locator discovery,
     *  or both.
     *  
     *  Note that each element of the set of discovered registrars managed by
     *  this <code>LookupDiscoveryManager</code> (referred to as the "managed
     *  set of registrars" or simply, the "managed set"), is actually an 
     *  instance of this class.
     */
    final class ProxyReg  {
        /** The discovered registrar to be managed */
	public final ServiceRegistrar proxy; //no sync final
        /** The groups to which the discovered registrar belongs */
        public String[] memberGroups; //sync(this)
        /** Special-purpose flag used in the discard process. This flag is
         *  only relevant to <code>LocatorDiscoveryListener.discarded</code>.
         *  This flag is set to <code>true</code> only when the method
         *  <code>LookupDiscoveryManager.discard</code> is called by the
         *  client. 
         *  
         *  A <code>true</code> value for this flag indicates to 
         *  <code>LocatorDiscoveryListener.discarded</code> that the registrar
         *  referenced by a given discarded event was discarded because the
         *  registrar was determined to be unreachable (a "communication
         *  discard"). A <code>false</code> value for this flag indicates to 
         *  <code>LocatorDiscoveryListener.discarded</code> that the registrar
         *  was discarded due to lost interest in the registrar's locator
         *  (a "no-interest discard"). 
         * 
         *  This flag is necessary because the locators of the discovered
         *  registrars are not retrieved and stored with the registrars and
         *  their member groups. The locators are not retrieved because to
         *  do so would require a remote call which is viewed as undesirable
         *  here since such a call cannot be guaranteed to complete in a timely
         *  fashion. Without the locator of the discarded registrar, the
         *  <code>LocatorDiscoveryListener.discarded</code> method cannot
         *  determine if the discarded event received from the 
         *  <code>LookupLocatorDiscovery</code> is a communication discard 
         *  or a no-interest discard. It is important for that method to
         *  be able to make such a determination because the action taken
         *  by that <code>discarded</code> method is dependent on that
         *  determination. For more information, refer to the API
         *  documentation (generated by the <code>javadoc</code> tool) of
         *  the <code>LocatorDiscoveryListener.discarded</code> method.
         *  
         *  Note that the mechanism that uses this flag assumes that
         *  neither the <code>LookupDiscovery</code> utility nor the 
         *  <code>LookupLocatorDiscovery</code> utility employed by this
         *  <code>LookupDiscoveryManager</code> are accessible outside of
         *  this <code>LookupDiscoveryManager</code>. If either were 
         *  accessible, then there would be no way to guarantee that a 
         *  value of <code>true</code> in this flag is equivalent to a
         *  communication discard.
         */
        public boolean commDiscard = false; // sync(this)
        /** Integer restricted to the values 0, 1, 2, and 3. Each value 
         *  represents a bit (or set of bits) that, when set, indicates the
         *  mechanism (group discovery, locator discovery, or both) through
         *  which the registrar referenced by the current instance of
         *  this class (proxy) has currently been (or not been) discovered.
         *  That is, if
         *  from = 0 (no bits set)  ==&gt; discovered by neither group nor locator
         *       = 1 (bit 0 set)    ==&gt; discovered by only group discovery
         *       = 2 (bit 1 set)    ==&gt; discovered by only locator discovery
         *       = 3 (bits 1&amp;2 set) ==&gt; discovered by both group and locator
         */
	private int from; // sync(this)
        /** Indicates whether the registrar referenced by this class is
         *  currently in the process of being discarded. This flag is used to
         *  prevent access, or inadvertent modifications to, the discovered
         *  state of registrars that are in the process of being discarded,
         *  but which have not yet been removed from the managed set.
         */
	private boolean bDiscarded = false; //sync(this)
        /** Constructs an instance of this wrapper class.
         *
         *  @param proxy        reference to a registrar that has been newly
         *                      discovered or re-discovered
         *  @param memberGroups the groups to which the discovered registrar
         *                      belongs
         *  @param from         indicates the mechanism by which the registrar
         *                      was discovered (group or locator discovery).
         *                      The only values which are valid for this
         *                      parameter are FROM_GROUP or FROM_LOCATOR.
         */
        public ProxyReg(ServiceRegistrar proxy,
                        String[] memberGroups,
                        int from)
        {
            if(proxy == null) {
	        throw new IllegalArgumentException("proxy cannot be null");
            }//endif
            if( (from != FROM_GROUP) && (from != FROM_LOCATOR) ) {
	        throw new IllegalArgumentException
                                ("invalid discovery mechanism: must be either "
                                 +"FROM_GROUP or FROM_LOCATOR");
            }//endif
	    this.proxy        = proxy;
	    this.memberGroups = memberGroups;
	    this.from         = from;
	}//end constructor	    

	public boolean equals(Object obj) {
	    if (obj instanceof ProxyReg){
		return proxy.equals(((ProxyReg)obj).proxy);
	    } else return false;
	}//end equals

	public int hashCode() {
	    return proxy.hashCode();
	}//end hashCode
	
        /** Sets the appropriate bit in the 'from' variable to indicate the
         *  mechanism or mechanisms through which the registrar referenced by
         *  this class was discovered (group discovery, locator discovery, or
         *  both). 
         *  
         *  This method is typically called during the discovery process; and
         *  the value input should always be either FROM_GROUP or FROM_LOCATOR.
         */
	public synchronized void addFrom(int from) {
	    this.from |= from;
	}//end addFrom

        /** Un-sets the appropriate bit in the 'from' variable to remove
         *  the indication that the registrar referenced by this class 
         *  was discovered via the mechanism identified by the input
         *  parameter. If, after un-setting the appropriate bit, the
         *  'from' variable is equal to 0 (indicating the registrar was
         *  previously discovered by neither group nor locator discovery),
         *  this method returns <code>true</code>; otherwise it returns
         *  <code>false</code>, which indicates that the registrar was 
         *  still discovered by the "opposite" mechanism from that identified
         *  by the input parameter.
         *  
         *  This method is typically called during the discard process; and
         *  the value input should always be either FROM_GROUP or FROM_LOCATOR.
         */
	public synchronized boolean removeFrom(int from) {
	    if( from == 0 ) {
	        throw new RuntimeException
                                   ("call to removeFrom with bad argument");
            }//endif
	    this.from &= ~from;
	    return (this.from == 0);
	}//end removeFrom

        /** Accessor method that returns the value of the 'from' variable.
         *  The value returned indicates the mechanism or mechanisms through
         *  which the registrar referenced by this class was previously
         *  discovered (group discovery, locator discovery, or both).
         *  
         *  @return <code>int</code> representing either group discovery (1),
         *          or locator discovery (2), or both (3).
         */
	public synchronized int getFrom() {
	    return this.from;
	}//end getFrom

        /** Discards the registrar referenced in this class from either the
         *  <code>LookupDiscovery</code> or <code>LookupLocatorDiscovery</code>
         *  utility employed by this <code>LookupDiscoveryManager</code>.
         * 
         *  The utility from which that registrar is discarded is dependent on
         *  whether the registrar was previously discovered via group or both
         *  group and locator discovery, or via locator discovery alone.
         *
         *  This method enables a mechanism for sequentially "chaining" the
         *  discard process of the <code>LookupDiscovery</code> utility with
         *  the discard process of the <code>LookupLocatorDiscovery</code>.
         *  That is, rather than discarding the registrar from both utilities
         *  at the same time, by invoking this method, the registrar will be
         *  discarded from only one of those utilities. Then when the discarded
         *  event is received by the listener registered with the utility from
         *  which the registrar was discarded, the listener - based on the
         *  current discovered state of the registrar - determines whether to: 
         *  discard the registrar from the other utility, send a discarded
         *  event out to the client's listener, or simply update state and
         *  do nothing more.
         *
         *  This chaining mechanism helps to present a single event
         *  source to the client listeners. That is, even though the
         *  two discovery utilities used internally by this 
         *  <code>LookupDiscoveryManager</code> operate independently,
         *  sending discarded events to the group discovery listener and/or
         *  the locator discovery listener, this chaining mechanism enables
         *  the coordination of those separate events so that only one 
         *  event is sent to the client listeners, even though multiple 
         *  events may have been received here.
         *
         *  Although this chaining mechanism is helpful in coordinating the
         *  discarded events received from the <code>LookupDiscovery</code>
         *  and <code>LookupLocatorDiscovery</code> utilities, the
         *  actual reason it must be used is due to the fact that 
         *  <code>LookupDiscovery</code> can send a certain type of
         *  discarded event - referred to as "passive communication
         *  discard"; whereas <code>LookupLocatorDiscovery</code> cannot.
         *  Recall that <code>LookupDiscovery</code> monitors the
         *  registrars it has discovered for reachability, whereas
         *  <code>LookupLocatorDiscovery</code> does not. When the
         *  <code>LookupDiscovery</code> sends a passive communication
         *  discard because it has determined that one of its registrars
         *  has become unreachable, if that registrar was also discovered
         *  via locator discovery, it is necessary to discard the registrar
         *  from the <code>LookupLocatorDiscovery</code> as well. In
         *  that case, this method is called to create a discard chain
         *  which ultimately will result in the registrar being discarded
         *  from the <code>LookupLocatorDiscovery</code>.
         */
	public void discard() {
            LookupDiscovery lookD = null;
            LookupLocatorDiscovery locateD = null;
            ServiceRegistrar regProxy = null;
	    synchronized(this) {
		bDiscarded = true;
                regProxy = proxy;
                if((from & FROM_GROUP) != 0) {
                    lookD = lookupDisc;
                } 
                if((from & FROM_LOCATOR) != 0) {
                    locateD = locatorDisc;
                }//endif
	    }//end sync
            /* Give group discovery priority to avoid race condition. When
             * appropriate, locator discard will eventually occur as a result
             * of the first group discard.
             */
            if (lookD != null) lookD.discard(regProxy);
            if (locateD != null) locateD.discard(regProxy);
	}//end discard

        /** Accessor method that returns the value of the <code>boolean</code>
         *  variable <code>bDiscarded</code>.
         */
	public synchronized boolean isDiscarded() {
	    return bDiscarded;
	}

        /** Accessor method that returns the <code>String</code> array
         *  containing the names of the groups to which the registrar
         *  referenced in this class belongs.
         */
        public synchronized  String[] getMemberGroups() {
            return memberGroups;
        }//end getMemberGroups

        /** Modifier method that changes the set of member groups - stored in
         *  this class and associated with the registrar referenced in this
         *  class - to the given set of member groups.
         */
        public synchronized  void setMemberGroups(String[] newMemberGroups) {
            memberGroups = newMemberGroups;
        }//end setMemberGroups
    }//end class ProxyReg

    /** Class that defines the listener that is registered with the
     *  <code>LookupLocatorDiscovery</code> that performs locator discovery
     *  on behalf of this <code>LookupDiscoveryManager</code>.
     */
    class LocatorDiscoveryListener implements DiscoveryListener {
        /** Called by the <code>LookupLocatorDiscovery</code> to
         *  send a discovered event to this listener when that
         *  <code>LookupLocatorDiscovery</code> has discovered at least
         *  one registrar (reg) having a locator equal one of the locators
         *  desired by this <code>LookupDiscoveryManager</code>.
         *  The reg(s) referenced in the given discovered event may,
         *  or may not, have also been previously discovered through group
         *  discovery.
         *  
         *  If this <code>LookupDiscoveryManager</code> has no prior knowledge
         *  of a particular reg referenced in the given discovered event when
         *  the <code>LookupLocatorDiscovery</code> invokes this method, then
         *  this method will send to the listeners registered with this
         *  <code>LookupDiscoveryManager</code>, a discovered event
         *  referencing that reg; otherwise, it will simply update state
         *  and send no event at all.
         */
	public void discovered(DiscoveryEvent e) {
            ServiceRegistrar[] proxys = (ServiceRegistrar[])e.getRegistrars();
            Map<ServiceRegistrar, String[]> groupsMap = e.getGroups();
            int len = proxys.length;
            Map<ServiceRegistrar, String[]> discoveredGroupsMap 
                    = new HashMap<ServiceRegistrar, String[]>(len);
            discoveredSetAddLock.lock();
            try {
                for(int i=0; i<len; i++) {
                        ProxyReg reg = findReg(proxys[i]);
                        if(reg == null) {//newly discovered, send event
                            reg = new ProxyReg(proxys[i], 
                                               groupsMap.get(proxys[i]),
                                               FROM_LOCATOR);
                            discoveredSet.add(reg);
                            discoveredGroupsMap.put(proxys[i],
                                                    groupsMap.get(proxys[i]));
                        } else {//previously discovered, update bit, send no event
                            reg.addFrom(FROM_LOCATOR);
                        }//endif
                }//end loop
            } finally {
                discoveredSetAddLock.unlock();
            }
            /* Will send notification only if map is non-empty from above */
            notifyListener(discoveredGroupsMap, DISCOVERED);
	}//end discovered

        /** Called by <code>LookupLocatorDiscovery</code> to send a discarded
         *  event to this listener when one of the following events occurs,
         *  affecting at least one registrar (reg) that was previously
         *  discovered through either locator discovery alone, or through
         *  both group and locator discovery:
         *  <p><ul>  
         *   <li> The method <code>LookupLocatorDiscovery.discard</code>
         *        was called because the reg was determined to be unreachable
         *        (this is referred to as a "communication discard").
         *   <li> Either the method <code>setLocators</code> or the method
         *        <code>removeLocators</code> was called on the
         *        <code>LookupLocatorDiscovery</code>, and the resulting new
         *        set of desired locators no longer contains an element
         *        equal to the locator of the discarded reg (this is referred
         *        to as a "no-interest discard").
         *  </ul><p>
         *  Depending on the type of discarded event received from the
         *  <code>LookupLocatorDiscovery</code> as described above, and
         *  depending on whether the discarded reg was previously discovered
         *  by locator discovery alone, or by both group and locator discovery,
         *  this method will determine whether or not to pass a discarded
         *  event on to the client's listener. (Note that because the discarded
         *  event was sent by the <code>LookupLocatorDiscovery</code>,
         *  the discarded reg could not have been previously discovered by
         *  group discovery alone.)
         *
         *  The following describes the logic used by this method to determine
         *  whether or not to send a discarded event to the client listeners
         *  registered with this <code>LookupDiscoveryManager</code>. Note
         *  that since the <code>LookupLocatorDiscovery</code> is accessible
         *  only from within this <code>LookupDiscoveryManager</code>, the
         *  method <code>LookupLocatorDiscovery.discard</code> is never called
         *  from "outside" of this <code>LookupDiscoveryManager</code>.
         *  This fact is exploited to communicate to this method that a
         *  communication discard has occurred. That is, when
         *  <code>LookupDiscoveryManager.discard</code> is called, a boolean
         *  associated with the discarded reg is set to <code>true</code> to
         *  indicate that a communication discard has occurred.
         *
         *  Thus, for a particular reg referenced in the given discarded event,
         *  <p><ul>  
         *   <li> if the discarded event is a result of a call to 
         *        <code>LookupDiscoveryManager.discard</code>, then this
         *        method will conclude that the discarded event must be a 
         *        communication discard (indicated by the value of the
         *        boolean set by <code>LookupDiscoveryManager.discard</code>
         *        and associated with the discarded reg). When the discard is
         *        a communication discard, if the reg was previously
         *        discovered by only locator discovery, this method sends a
         *        discarded event. But if the reg was previously discovered
         *        by group discovery as well as locator discovery,
         *        then rather than sending a discarded event here,
         *        <code>LookupDiscovery.discard</code> is called (through
         *        a call to <code>reg.discard</code>), which will send a
         *        discarded event to <code>GroupDiscoveryListener</code>, which
         *        then ultimately sends a discarded event to the registered
         *        listeners of this <code>LookupDiscoveryManager</code>.
         *   <li> if the discarded event is NOT a result of a call to
         *        <code>LookupDiscoveryManager.discard</code> (is not a
         *        communication discard), then this method will conclude
         *        that the discarded event must be a no-interest discard.
         *        When the discard is a no-interest discard, if the discarded
         *        reg was previously discovered by ONLY locator discovery,
         *        then a discarded event is sent; otherwise, no event is sent
         *        (see section DU.2.5.1 of the "Jini Discovery Utilities
         *        Specification").
         *  </ul><p>
         *  The logic described above can be collapsed into the following
         *  decision process:
         *  <p><pre>
         *      if(discovered by locator discovery alone) {
         *          send discarded event
         *      } else {//discovered by both group and locator discovery
         *          if(communication discard) {
         *              call discard on LookupDiscovery
         *          }
         *      }
         *  </pre>
         */
	public void discarded(DiscoveryEvent e) {
	    ServiceRegistrar[] proxys = e.getRegistrars();
            Map<ServiceRegistrar, String[]> groupsMap = e.getGroups();
            int len = proxys.length;
            Map<ServiceRegistrar, String[]> discardedGroupsMap 
                    = new HashMap<ServiceRegistrar, String[]>(len);
	    for(int i=0; i<len; i++) {
                    ProxyReg reg = findReg(proxys[i]);
                    if(reg != null) {
                        String[] newGroups = groupsMap.get(reg.proxy);
                        if ( removeDiscoveredSet(reg,FROM_LOCATOR) ) {
                            /* Locator discovery only, always send discarded */
                            discardedGroupsMap.put(reg.proxy,newGroups);
                        } else {//group and loc discovery
                            synchronized (reg){
                                if( reg.commDiscard ) {//unreachable, send later
                                    reg.discard();//discard from LookupDiscovery
                                }//endif
                            }
                        }//endif
                    }//endif(reg != null)
            }//end loop
            /* Will send notification only if map is non-empty from above */
            notifyListener(discardedGroupsMap, DISCARDED);
	}//end discarded

        /* Convenience method that first attempts to unset the bit in the
         * discovery mechanism flag of the given ProxyReg based on the value
         * of the given discovery mechanism parameter (from). Depending on
         * the value of that parameter, the flag value that results will show
         * either no group discovery or no locator discovery (or neither
         * mechanism).
         *
         * After unsetting the appropriate bit, if the value of the flag
         * shows neither group discovery nor locator discovery, then the 
         * given ProxyReg is removed from the managed set of registrars, and
         * <code>true</code> is returned; otherwise <code>false</code> is
         * returned.
         */
	boolean removeDiscoveredSet(ProxyReg reg, int from) {
	    boolean bret = reg.removeFrom(from);
	    if (bret) discoveredSet.remove(reg);
	    return bret;
	}//end removeDiscoveredSet
    }//end class LocatorDiscoveryListener

    /** Class that defines the listener that is registered with the
     *  <code>LookupDiscovery</code> that performs group discovery
     *  on behalf of this <code>LookupDiscoveryManager</code>.
     */
    private final class GroupDiscoveryListener
                                           extends LocatorDiscoveryListener
                                           implements DiscoveryChangeListener
    {
        /** Called by <code>LookupDiscovery</code> to send a discovered event
         *  to this listener when that <code>LookupDiscovery</code> has
         *  discovered at least one registrar (reg) belonging to at least one
         *  of the groups desired by this <code>LookupDiscoveryManager</code>.
         *  The reg(s) referenced in the given discovered event may,
         *  or may not, have also been previously discovered through locator
         *  discovery.
         *  
         *  If this <code>LookupDiscoveryManager</code> has no prior knowledge
         *  of a particular reg referenced in the given discovered event when
         *  the <code>LookupDiscovery</code> invokes this method, then this
         *  method will send to the listeners registered with this
         *  <code>LookupDiscoveryManager</code>, a discovered event
         *  referencing that reg; otherwise, depending on whether or not
         *  the set of member groups referenced in the given discovered
         *  event equal the member groups currently associated with the
         *  reg from that event, this method may send a changed event, or
         *  may simply update state and send no event at all.
         */
	public void discovered(DiscoveryEvent e) {
            ServiceRegistrar[] proxys = (ServiceRegistrar[])e.getRegistrars();
            Map<ServiceRegistrar, String[]> groupsMap = e.getGroups();
            int len = proxys.length;
            Map<ServiceRegistrar, String[]> discoveredGroupsMap 
                    = new HashMap<ServiceRegistrar, String[]>(len);
            Map<ServiceRegistrar, String[]> changedGroupsMap    
                    = new HashMap<ServiceRegistrar, String[]>(len);
            discoveredSetAddLock.lock();
            try {
                for(int i=0; i<len; i++) {
                    ProxyReg reg = findReg(proxys[i]);
                    if(reg == null) {//newly discovered, send discovered event
                        reg = new ProxyReg(proxys[i],
                                           groupsMap.get(proxys[i]),
                                           FROM_GROUP);
                        discoveredSet.add(reg);
                        discoveredGroupsMap.put(proxys[i],
                                                groupsMap.get(proxys[i]));
                    } else {//previously discovered by group, by loc or by both
                        String[] oldGroups = reg.getMemberGroups();
                        String[] newGroups = groupsMap.get(reg.proxy);
                        if( groupSetsEqual(oldGroups,newGroups) ) {//by loc
                            reg.addFrom(FROM_GROUP);//send no event
                        } else {//by group or by both, send changed event
                            reg.setMemberGroups(newGroups);//update groups
                            changedGroupsMap.put(reg.proxy,newGroups);
                        }//endif
                    }//endif
                }//end loop
            } finally {
                discoveredSetAddLock.unlock();
            }
            /* Will send notification only if map is non-empty from above */
            notifyListener(discoveredGroupsMap, DISCOVERED);
            notifyListener(changedGroupsMap, CHANGED);
	}//end discovered

        /** Called by <code>LookupDiscovery</code> to send a discarded event
         *  to this listener when one of the following events occurs  
         *  affecting at least one registrar (reg) that was previously
         *  discovered through either group discovery alone, or through
         *  both group and locator discovery:
         *  <p><ul>  
         *   <li> The method <code>LookupDiscovery.discard</code> was
         *        called because the reg was determined to be unreachable.
         *        In this case, the discarded event received from the
         *        <code>LookupDiscovery</code> is referred to as an
         *        "active communication discard".
         *   <li> The <code>LookupDiscovery</code> has determined that the
         *        reg has stopped sending multicast announcements.
         *        In this case, the discarded event received from the
         *        <code>LookupDiscovery</code> is referred to as a
         *        "passive communication discard".
         *   <li> Either the method <code>setGroups</code> or the method
         *        <code>removeGroups</code> has been called on the
         *        <code>LookupDiscovery</code>, and the resulting new set
         *        of desired groups no longer intersects the set of member
         *        groups of the discarded reg. In this case, the discarded
         *        event received from the <code>LookupDiscovery</code> is
         *        referred to as an "active no-interest discard".
         *   <li> The member groups of the discarded reg have been changed
         *        in such a way that the set of member groups associated
         *        with the discarded reg no longer intersects the set of
         *        desired groups to discover. In this case, the discarded
         *        event received from <code>LookupDiscovery</code> is
         *        referred to as a "passive no-interest discard".
         *  </ul><p>
         *  Depending on the type of discarded event received from the
         *  <code>LookupDiscovery</code> as described above, and depending
         *  on whether the discarded reg was previously discovered by group
         *  discovery alone, or by both group and locator discovery, this
         *  method will determine whether to send a discarded event, a changed
         *  event, or no event at all. Note that because the discarded event
         *  was sent by the <code>LookupDiscovery</code>, the discarded reg
         *  could not have been previously discovered by locator discovery
         *  alone. Note also that even though a discarded event is received
         *  by this method, there are conditions in which a changed event
         *  is ultimately sent to the listeners registered with this
         *  <code>LookupDiscoveryManager</code> (see below).
         *
         *  The following describes the logic used by this method to determine
         *  whether or not to send an event to the registered listeners of
         *  this <code>LookupDiscoveryManager</code>, as well as what type
         *  of event to send. Note that this method concludes that the
         *  current discarded event must be a communication discard when the
         *  the set of desired groups still intersects the discarded reg's
         *  current set of member groups. This is because when interest in
         *  the reg still exists, <code>LookupDiscovery</code> will discard 
         *  the reg only when it has determined that the reg is unreachable.
         *  
         *  Thus, for a particular reg referenced in the given discarded event,
         *  <p><ul>  
         *   <li> if the member groups contained in the discarded event from
         *        the <code>LookupDiscovery</code> are identical to the
         *        previously discovered member groups of that reg, and if
         *        if those member groups still intersect the current set of
         *        desired groups (still interested in the reg), then this
         *        method will conclude that the discarded event must be either
         *        an active or a passive communication discard. When the 
         *        discard is a communication discard (either active or
         *        passive), if the reg was previously discovered by only group
         *        discovery, this method sends a discarded event. But if the
         *        reg was previously discovered by locator discovery as well
         *        as group discovery, then rather than sending a discarded
         *        event here, <code>LookupLocatorDiscovery.discard</code>
         *        is called (through a call to <code>reg.discard</code>),
         *        which will send a discarded event to
         *        <code>LocatorDiscoveryListener</code>, which then
         *        ultimately sends a discarded event to the registered
         *        listeners of this <code>LookupDiscoveryManager</code>.
         *   <li> if the member groups contained in the discarded event from
         *        the <code>LookupDiscovery</code> are identical to the
         *        previously discovered member groups of that reg, but those
         *        member groups NO LONGER intersect the current set of desired
         *        groups, then this method will conclude that the discarded
         *        event must be an active no-interest discard. When the 
         *        discard is an active no-interest discard, if the discarded
         *        reg was previously discovered by ONLY group discovery, then
         *        a discarded event is sent; otherwise, no event is sent
         *        (see section DU.2.4.1 of the "Jini Discovery Utilities
         *        Specification").
         *   <li> if the member groups contained in the discarded event from
         *        the <code>LookupDiscovery</code> neither intersect the
         *        current set of desired groups (indicating no interest), nor
         *        are they identical to the previously discovered member
         *        groups of that reg (the member groups have changed), this
         *        method will conclude that the discarded event must be a
         *        passive no-interest discard. When the discard is a passive
         *        no-interest discard, if the discarded reg was previously
         *        discovered by locator discovery as well as group discovery,
         *        a changed event is is sent to the appropriate listeners;
         *        otherwise a discarded event is sent. A changed event is
         *        sent when the reg was previously discovered by both group
         *        and locator discovery because the fact that the reg was
         *        discovered via locator discovery indicates continued
         *        interest in the reg, even though the reg was discarded with
         *        respect to group discovery. In this case, since the member
         *        groups have actually changed (changed in such a way that the
         *        <code>LookupDiscovery</code> sent a discarded event), and 
         *        since there is still interest in that reg, it is more
         *        appropriate to send a changed event here rather than a
         *        discarded event.
         *  </ul><p>
         *  The logic described above can be collapsed into the following
         *  decision process:
         *  <p><pre>
         *      if(discovered by group discovery alone) {
         *          send discarded event
         *      } else {//discovered by both group and locator discovery
         *          if(NO LONGER interested in member groups) {
         *              if(member group have changed) {
         *                  send changed event
         *              }
         *          } else {//still interested in member groups
         *              call discard on LookupLocatorDiscovery
         *          }
         *      }
         *  </pre>
         */
	public void discarded(DiscoveryEvent e) {
	    ServiceRegistrar[] proxys = (ServiceRegistrar[])e.getRegistrars();
            Map<ServiceRegistrar, String[]> groupsMap = e.getGroups();
            int len = proxys.length;
            Map<ServiceRegistrar, String[]> discardedGroupsMap 
                    = new HashMap<ServiceRegistrar, String[]>(len);
            Map<ServiceRegistrar, String[]> changedGroupsMap   
                    = new HashMap<ServiceRegistrar, String[]>(len);
	    for(int i=0; i<len; i++) {
                    ProxyReg reg = findReg(proxys[i]);
                    if(reg != null) {
                        String[] newGroups = groupsMap.get(reg.proxy);
                        if( removeDiscoveredSet(reg,FROM_GROUP) ) {
                            /* group discovery only, always send discarded */
                            discardedGroupsMap.put(reg.proxy,newGroups);
                        } else {//discovered by group and locator
                            String[] desiredGroups = lookupDisc.getGroups();
                            if( !stillInterested(newGroups,desiredGroups) ) {
                                String[] oldGroups = reg.getMemberGroups();
                                if( !groupSetsEqual(oldGroups,newGroups) ) {
                                    reg.setMemberGroups(newGroups);
                                    changedGroupsMap.put(reg.proxy,newGroups);
                                }//endif(!groupSetsEqual ==> groups changed)
                            } else {//stillInterested ==> communication discard
                                reg.discard();//from LookupLocatorDiscovery
                            }//endif(stillInterested or not)
                        }//endif(removeDiscoveredSet ==> group or group & loc)
                    }//endif(reg != null)
            }//end loop
            /* Will send notification only if map is non-empty from above */
            notifyListener(discardedGroupsMap, DISCARDED);
            notifyListener(changedGroupsMap, CHANGED);
	}//end discarded

        /** Called by <code>LookupDiscovery</code> to send a changed event
         *  to this listener when that <code>LookupDiscovery</code> has 
         *  determined that the member groups of a previously discovered
         *  registrar (reg) have changed. This method extracts and records
         *  the appropriate information from the given changed event, and
         *  then sends a changed event to the appropriate listeners registered
         *  with this <code>LookupDiscoveryManager</code>.
         */
        public void changed(DiscoveryEvent e) {
            /* update the groups of each changed registrar */
            ServiceRegistrar[] proxys = (ServiceRegistrar[])e.getRegistrars();
            Map<ServiceRegistrar, String[]> groupsMap = e.getGroups();
            int len = proxys.length;
            Map<ServiceRegistrar, String[]> changedGroupsMap 
                    = new HashMap<ServiceRegistrar, String[]>(len);
	    for(int i=0; i<len; i++) {
                ProxyReg reg = findReg(proxys[i]);
                if(reg != null) {//previously discovered
                    String[] newGroups = groupsMap.get(reg.proxy);
                    reg.setMemberGroups(newGroups);
                    changedGroupsMap.put(reg.proxy,newGroups);
                }//endif
            }//end loop
            /* Will send notification only if map is non-empty from above */
            notifyListener(changedGroupsMap, CHANGED);
        }//end changed
    }//end class GroupDiscoveryListener

    /** 
     * Constructs an instance of this class
     */
    AbstractLookupDiscoveryManager(  DiscoveryListener listener,
                                  LookupDiscovery lookup,
                                  LookupLocatorDiscovery locator)
    {
        /* Prepare the discovery process */
        if(listener != null) listeners.add(listener);
        /* Configure for group discovery */
        lookupDisc = lookup;
        /* Configure for locator discovery */
        locatorDisc = locator;
    }//end constructor

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
     * @see net.jini.discovery.DiscoveryLocatorManagement#getLocators
     * @see #setLocators
     */
    public LookupLocator[] getLocators(){
	return locatorDisc.getLocators();
    }

    /**
     * Adds a set of locators to the managed set of locators. Elements in the
     * input set that duplicate (using the <code>LookupLocator.equals</code>
     * method) elements already in the managed set will be ignored. If the
     * empty array is input, the managed set of locators will not change.
     *
     * @param locators <code>LookupLocator</code> array consisting of the
     *                 locators to add to the managed set.
     * 
     * @throws java.lang.UnsupportedOperationException this exception 
     *         occurs when there is no managed set of locators to augment.
     *         That is, the current managed set of locators is
     *         <code>null</code>.
     *
     * @throws java.lang.NullPointerException this exception occurs when
     *         either <code>null</code> is input to the <code>locators</code>
     *         parameter, or one or more of the elements of the
     *         <code>locators</code> parameter is <code>null</code>.
     *
     * @see net.jini.discovery.DiscoveryLocatorManagement#addLocators
     * @see #removeLocators
     */
    public void addLocators(LookupLocator[] locators){
	locatorDisc.addLocators(locators);
    }

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
     * @throws java.lang.UnsupportedOperationException this exception 
     *         occurs when there is no managed set of locators from which
     *         remove elements.
     *
     * @throws java.lang.NullPointerException this exception occurs when
     *         <code>null</code> is input to the <code>locators</code>
     *         parameter.
     *
     * @see net.jini.discovery.DiscoveryLocatorManagement#removeLocators
     * @see #addLocators
     */
    public void removeLocators(LookupLocator[] locators) {
	locatorDisc.removeLocators(locators);
    }

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
     *         <code>null</code> is input to the <code>locators</code>
     *         parameter.
     *
     * @see net.jini.discovery.DiscoveryLocatorManagement#setLocators
     * @see #getLocators
     */
    public void setLocators(LookupLocator[] locators) {
	locatorDisc.setLocators(locators);
    }
    
    /** 
     * Returns an array consisting of the elements of the managed set
     * of groups; that is, the names of the groups whose members are the
     * lookup services to discover. If the managed set of groups is empty,
     * this method will return the empty array. If there is no managed set
     * of groups, then null is returned; indicating that all groups are to
     * be discovered. This method returns a new array upon each invocation.
     *
     * @return <code>String</code> array consisting of the elements of the
     *         managed set of groups
     *
     * @see net.jini.discovery.DiscoveryGroupManagement#getGroups
     * @see #setGroups
     */
    public String[] getGroups() {
	return lookupDisc.getGroups();
    }

    /**   
     * Adds a set of group names to the managed set of groups. Elements in
     * the input set that duplicate elements already in the managed set
     * will be ignored. Once a new name is added to the managed set,
     * attempts will be made to discover all (as yet) undiscovered lookup
     * services that are members of the group having that name. If the empty
     * array (<code>DiscoveryGroupManagement.NO_GROUPS</code>) is input, the
     * managed set of groups will not change.
     *
     * Note that any entity that invokes this method must have
     * <code>DiscoveryPermission</code> on each of the groups in the
     * new set, otherwise a <code>SecurityException</code> will be
     * propagated through this method.
     *
     * @param groups <code>String</code> array consisting of the group names
     *               to add to the managed set.
     *
     * @throws java.io.IOException because an invocation of this method may
     *         result in the re-initiation of the discovery process, which can
     *         throw an <code>IOException</code> when socket allocation occurs.
     * 
     * @throws java.lang.UnsupportedOperationException this exception 
     *         occurs when there is no managed set of groups to augment.
     *         That is, the current managed set of groups is <code>null</code>.
     *         If the managed set of groups is <code>null</code>, all groups
     *         are being discovered; thus, requesting that a set of groups be
     *         added to the set of all groups makes no sense.
     *
     * @throws java.lang.NullPointerException this exception occurs when
     *         either <code>null</code> is input to the <code>groups</code>
     *         parameter, or one or more of the elements of the
     *         <code>groups</code> parameter is <code>null</code>. If a
     *         <code>null</code> is input, then the entity is effectively
     *         requesting that "all groups" be added to the current managed
     *         set of groups; which is not allowed. (Note that if the entity
     *         wishes to change the managed set of groups from a finite set
     *         of names to "all groups", the <code>setGroups</code> method
     *         should be invoked with <code>null</code> input.)
     *
     * @see net.jini.discovery.DiscoveryGroupManagement#addGroups
     * @see #removeGroups
     */
    public void addGroups(String[] groups) throws IOException {
	lookupDisc.addGroups(groups);
    }

    /**   
     * Deletes a set of group names from the managed set of groups. If the
     * empty array (<code>DiscoveryGroupManagement.NO_GROUPS</code>) is input,
     * this method takes no action.
     *
     * @param groups <code>String</code> array consisting of the group names
     *               that will be removed from the managed set.
     *
     * @throws java.lang.UnsupportedOperationException this exception 
     *         occurs when there is no managed set of groups from which to
     *         remove elements.
     *
     * @throws java.lang.NullPointerException this exception occurs when
     *         <code>null</code> is input to the <code>groups</code>
     *         parameter.
     *
     * @see net.jini.discovery.DiscoveryGroupManagement#removeGroups
     * @see #addGroups
     */
    public void removeGroups(String[] groups) {
        if (groups == null) {
            throw new NullPointerException
                                ("can't remove null from groups to discover");
        }
        String[] curGroupsToDiscover = lookupDisc.getGroups();
        if (curGroupsToDiscover == null) {
            throw new UnsupportedOperationException
                                        ("can't remove from \"any groups\"");
        }
        if (curGroupsToDiscover.length == 0) return;

        HashSet curGroups = new HashSet(curGroupsToDiscover.length);
        for(int i=0;i<curGroupsToDiscover.length;i++) {
            curGroups.add(curGroupsToDiscover[i]);
        }
        boolean removed = false;
        for(int i=0;i<groups.length;i++) {
            removed |= curGroups.remove(groups[i]);
        }
        if(!removed) return; // nothing removed

	lookupDisc.removeGroups(groups); //generates discards that get ignored
    }

    /**   
     * Replaces all of the group names in the managed set with names from
     * a new set. Once a new group name has been placed in the managed
     * set, if there are lookup services belonging to that group that have
     * already been discovered, no event will be sent to the entity's
     * listener for those particular lookup services. Attempts to discover
     * all (as yet) undiscovered lookup services belonging to that group
     * will continue to be made.
     * <p>
     * If null (<code>DiscoveryGroupManagement.ALL_GROUPS</code>) is input
     * to this method, then attempts will be made to discover all (as yet)
     * undiscovered lookup services that are within range, and which
     * are members of any group. If the empty array
     * (<code>DiscoveryGroupManagement.NO_GROUPS</code>) is input, then
     * group discovery will cease until this method is invoked with an
     * input parameter that is non-<code>null</code> and non-empty.
     *
     * Note that any entity that invokes this method must have
     * <code>DiscoveryPermission</code> on each of the groups in the
     * new set, otherwise a <code>SecurityException</code> will be
     * propagated through this method.
     *
     * @param groups <code>String</code> array consisting of the group
     *               names that will replace the current names in the
     *               managed set.
     *
     * @throws java.io.IOException because an invocation of this method may
     *         result in the re-initiation of the discovery process, which can
     *         throw an <code>IOException</code> when socket allocation occurs.
     *
     * @see net.jini.discovery.DiscoveryGroupManagement#setGroups
     * @see #getGroups
     */
    public void setGroups(String[] groups) throws IOException {
        lookupDisc.setGroups(groups); //generates discards that get ignored
    }

    /**
     * Adds an instance of <code>DiscoveryListener</code> to the set of
     * objects listening for discovery events. Once the listener is
     * registered, it will be notified of all lookup services discovered
     * to date, and will then be notified as new lookup services are
     * discovered or existing lookup services are discarded.
     * <p>
     * The listener methods may throw Error or RuntimeException subclasses.
     * They will normally be reported only through the log. If the discovered
     * method throws Throwable T during the initial discovery of existing
     * services then this method will also throw T.
     * <p>
     * If <code>null</code> is input, this method takes no action. If the
     * listener input to this method duplicates (using the <code>equals</code>
     * method) another element in the current set of listeners, no action
     * is taken.
     *
     * @param listener an instance of <code>DiscoveryListener</code>
     *                 corresponding to the listener to add to the set of
     *                 listeners.
     *
     * @throws java.lang.NullPointerException this exception occurs when
     *         <code>null</code> is input to the  <code>listener</code>
     *          parameter.
     *
     * @see net.jini.discovery.DiscoveryListener
     * @see net.jini.discovery.DiscoveryManagement#addDiscoveryListener
     * @see #removeDiscoveryListener
     */
    public void addDiscoveryListener(DiscoveryListener listener) {
        if(listener == null) {
            throw new NullPointerException("can't add null listener");
        }
        
        listeners.add(listener);
        if (discoveredSet.isEmpty()) return;
        Map<ServiceRegistrar, String[]> groupsMap = new HashMap<ServiceRegistrar, String[]>(discoveredSet.size());
        Iterator<ProxyReg> it = discoveredSet.iterator();
        while (it.hasNext()){
            ProxyReg reg = it.next();
            groupsMap.put(reg.proxy,reg.getMemberGroups());
        } 
        notifyListener(listener, groupsMap, DISCOVERED);
    }

    /**
     * Removes a listener from the set of objects listening for discovery
     * events. If the listener object input to this method does not exist
     * in the set of listeners, then this method will take no action.
     *
     * @param listener an instance of <code>DiscoveryListener</code>
     *                 corresponding to the listener to remove from the set
     *                 of listeners.
     *
     * @see net.jini.discovery.DiscoveryListener
     * @see net.jini.discovery.DiscoveryManagement#removeDiscoveryListener
     * @see #addDiscoveryListener
     */
    public void removeDiscoveryListener(DiscoveryListener listener) {
        listeners.remove(listener);
    }

    /**
     * Returns an array of instances of <code>ServiceRegistrar</code>, each
     * corresponding to a proxy to one of the currently discovered lookup
     * services. For each invocation of this method, a new array is returned.
     *
     * @return array of instances of <code>ServiceRegistrar</code>, each
     *         corresponding to a proxy to one of the currently discovered
     *         lookup services
     *
     * @see net.jini.core.lookup.ServiceRegistrar
     * @see net.jini.discovery.DiscoveryManagement#removeDiscoveryListener
     */
    public ServiceRegistrar[] getRegistrars() {
	List<ServiceRegistrar> proxySet = new LinkedList<ServiceRegistrar>();
        Iterator<ProxyReg> iter = discoveredSet.iterator();
        while(iter.hasNext()) {
            ProxyReg reg = iter.next();
            if(!reg.isDiscarded())
                proxySet.add(reg.proxy);
        }
	return proxySet.toArray(new ServiceRegistrar[proxySet.size()]);
    }

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
     * @see net.jini.core.lookup.ServiceRegistrar
     * @see net.jini.discovery.DiscoveryManagement#discard
     */
    public void discard(ServiceRegistrar proxy) {
        if(proxy == null) return;
	ProxyReg reg = findReg(proxy);
	if(reg != null) {
            synchronized (reg){
                reg.discard();
                reg.commDiscard = true;
            }
        }//endif
    }//end discard

    /**
     * Terminates all threads, ending all discovery processing being
     * performed by the current instance of this class.
     * <p>
     * After this method has been invoked, no new lookup services will
     * be discovered, and the effect of any new operations performed
     * on the current instance of this class are undefined.
     *
     * @see net.jini.discovery.DiscoveryManagement#terminate
     */
    public void terminate() {
        listeners.clear();
	lookupDisc.terminate();
	locatorDisc.terminate();
    }
    
    /** Return where the proxy came from. 
     * <p>
     *  An int restricted to the values 0, 1, 2, and 3. Each value 
     *  represents a bit (or set of bits) that indicates the
     *  mechanism (group discovery, locator discovery, or both) through
     *  which the registrar referenced by the current instance of
     *  this class (proxy) has currently been (or not been) discovered.
     * </p>
     * <table border=0><caption>That is, if from</caption>
     * <tr><td>= 0 (no bits set)</td><td>==&gt; discovered by neither group nor locator</td></tr>
     * <tr><td>= 1 (bit 0 set)</td><td>==&gt; discovered by only group discovery</td></tr>
     * <tr><td>= 2 (bit 1 set)</td><td>==&gt; discovered by only locator discovery</td></tr>
     * <tr><td>= 3 (bits 1&amp;2 set)</td><td>==&gt; discovered by both group and locator</td></tr>
     * </table>
     * 
     * @param proxy  a ServiceRegistrar object
     * @return an <code>int</code> from, indicating whether the proxy   
     *         was obtained through group or locator discovery. 
     */
    public int getFrom(ServiceRegistrar proxy) {
	AbstractLookupDiscoveryManager.ProxyReg reg = findReg(proxy);
	if(reg != null)
	    return reg.getFrom();
	return 0;
    }


    private ProxyReg findReg(ServiceRegistrar proxy) {
        Iterator iter = discoveredSet.iterator();
        while(iter.hasNext()) {
            ProxyReg reg =(ProxyReg)iter.next();
            if(reg.proxy.equals(proxy))
                return reg;
        }
	return null;
    }

    /**
     * Notify all listeners for a discovery event. If a listener's method
     * completes abruptly due to a Throwable, it is logged and processing 
     * continues.
     * @param groupsMap mapping from the elements of the registrars of this
     *               event to the member groups in which each registrar is
     *               a member.
     * @param eventType The type of event.
     */
    private void notifyListener(Map<ServiceRegistrar, String[]> groupsMap, int eventType) {
	if(groupsMap.isEmpty()) return;
	Iterator<DiscoveryListener> iter = listeners.iterator();
	while(iter.hasNext()) {
	    DiscoveryListener l = iter.next();
	    try {
                notifyListener(l, groupsMap, eventType);
	    } catch (Throwable t) {
                logger.log(Levels.HANDLED, "a discovery listener failed to process a " +
                	(eventType == DISCARDED ? "discard" : eventType == DISCOVERED ? "discover" : "changed") + " event", t);
	    }
	}
    }//end notifyListener
    
    /**
     * Notify a specific listener for a discovery event. If the listener's
     * method throws a Throwable T, this method will also throw T. 
     * @param l The listener to notify.
     * @param groupsMap mapping from the elements of the registrars of this
     *               event to the member groups in which each registrar is
     *               a member.
     * @param eventType The type of the event.
     */
    private void notifyListener(DiscoveryListener l,
                                Map<ServiceRegistrar, String[]> groupsMap,
                                int eventType)
    {
        /* always send discovered and discarded events, not always changed */
        if((eventType == CHANGED) && !(l instanceof DiscoveryChangeListener)){
            return;
        }
        DiscoveryEvent evt = new DiscoveryEvent(AbstractLookupDiscoveryManager.this,
                                                deepCopy(groupsMap) );
        switch(eventType) {
            case DISCOVERED:
                l.discovered(evt);
                break;
            case DISCARDED:
                l.discarded(evt);
                break;
            case CHANGED:
                ((DiscoveryChangeListener)l).changed(evt);
                break;
        }//end switch
    }//end notifyListener

    /** Determines if two sets of registrar member groups have identical
     *  contents. Assumes there are no duplicates, and the sets can never
     *  be null.
     *
     * @param groupSet0    <code>String</code> array containing the group
     *                     names from the first set used in the comparison
     * @param groupSet1    <code>String</code> array containing the group
     *                     names from the second set used in the comparison
     * 
     *  @return <code>true</code> if the contents of each set is identical;
     *          <code>false</code> otherwise
     */
    private static boolean groupSetsEqual(String[] groupSet0,
                                          String[] groupSet1)
    {
        if(groupSet0.length != groupSet1.length) return false;
        /* every element of one set contained in the other set? */
        iLoop:
        for(int i=0;i<groupSet0.length;i++) {
            for(int j=0;j<groupSet1.length;j++) {
                if( groupSet0[i].equals(groupSet1[j]) ) {
                    continue iLoop;
                }
            }
            return false;
        }
        return true;
    }//end groupSetsEqual

    /** Determines if at least one member group of a given registrar is
     *  contained in the given set of desired groups.
     *
     * @param regGroups     <code>String</code> array containing the member
     *                      groups of a given registrar (will never be null)
     * @param desiredGroups <code>String</code> array containing the groups
     *                      to discover (can be null - ALL_GROUPS)
     * 
     *  @return <code>true</code> if at least one element of regGroups is
     *          contained in desiredGroups; <code>false</code> otherwise
     */
    private boolean stillInterested(String[] regGroups,String[] desiredGroups){
        if(desiredGroups == DiscoveryGroupManagement.ALL_GROUPS) return true;
        if(desiredGroups.length == 0) return false;
	for(int i=0;i<regGroups.length;i++) {
	    for (int j=0;j<desiredGroups.length;j++) {
                if(regGroups[i].equals(desiredGroups[j])) return true;
            }
        }
	return false;
    }//end stillInterested

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
        for(Iterator<Map.Entry<ServiceRegistrar, String[]>> itr = eSet.iterator(); itr.hasNext(); ) {
            Map.Entry<ServiceRegistrar, String[]> pair = itr.next();
            /* only need to clone the value of the order pair */
            pair.setValue( pair.getValue().clone() );
        }
        return newMap;
    }//end deepCopy

    /**
     * Initiates the discovery process for
     * the given set of groups and the given set of locators. Whenever a
     * lookup service is discovered, discarded, or changed, the appropriate
     * notification will be sent to the appropriate listener.
     */
    void beginDiscovery()    
    {
	lookupDisc.addDiscoveryListener(groupListener); // Needs to be delayed until after construction, this reference escapes.
	locatorDisc.addDiscoveryListener(locatorListener); // Needs to be delayed until after construction, this reference escapes.
    }//end beginDiscovery

}
