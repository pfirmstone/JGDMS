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
import java.io.InvalidObjectException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.rmi.MarshalledObject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.jini.core.event.RemoteEvent;
import net.jini.core.lookup.ServiceID;
import net.jini.core.lookup.ServiceRegistrar;
import net.jini.io.MarshalledInstance;
import org.apache.river.api.io.AtomicMarshalledInstance;
import org.apache.river.api.io.AtomicSerial;
import org.apache.river.api.io.AtomicSerial.GetArg;
import org.apache.river.proxy.MarshalledWrapper;

/**
 * Whenever the lookup discovery service discovers or discards a lookup
 * service matching the discovery/discard criteria of one or more of its 
 * registrations, the lookup discovery service sends an instance of this
 * class to the listener corresponding to each such registration.
 * <p>
 * For each registration created by the lookup discovery service, an event
 * identifier is generated. That event identifier uniquely maps the
 * registration to the listener (submitted by the client to the lookup
 * discovery service during the registration process) and to the set of
 * groups and locators the client is interested in discovering. The event
 * identifier is unique across all other active registrations with the
 * lookup discovery service, and is sent to the listener as part of the
 * event.
 * <p>
 * Because clients of the lookup discovery service need to know not only 
 * when a targeted lookup service has been discovered, but also when it 
 * has been discarded, the lookup discovery service uses an instance of
 * this class to notify a client's registration(s) when either of these
 * events occurs.
 * <p>
 * This class extends RemoteEvent, adding the following additional items
 * of abstract state: a boolean indicating whether the lookup services
 * referenced by the event have been discovered or discarded; and a set
 * consisting of proxy objects where each proxy is a marshalled instance
 * of the ServiceRegistrar interface, and each is a proxy of one of the
 * recently discovered or discarded lookup service(s). Methods are defined
 * through which this additional state may be retrieved upon receipt of an
 * instance of this class.
 * <p>
 * The sequence numbers for a given event identifier are "strictly
 * increasing". This means that when any two such successive events
 * have sequence numbers differing by only a value of 1, then it is
 * guaranteed that no events have been missed. On the other hand, when
 * viewing the set of received events in order, if the difference 
 * between the sequence numbers of two successive events is greater
 * than 1, then one or more events may or may not have been missed.
 * For example, a difference greater than 1 could occur if the lookup
 * discovery service crashes, even if no events are lost because of
 * the crash. When two successive events have sequence numbers whose
 * difference is greater than 1, there is said to be a "gap" between
 * the events.
 * <p>
 * When a gap occurs between events, the state of the locally managed
 * set of lookup services may or may not fall "out of sync" with the
 * corresponding remote state. For example, if the gap corresponds to
 * a missed event representing the (initial) discovery of a targeted
 * lookup service, the remote state will reflect this discovery whereas
 * the local state will not. When such a situation occurs, clients may
 * wish to employ the methods of the corresponding registration object
 * to query the current remote state in order to update the current
 * local state. 
 * <p>
 * Thus, clients typically use this class to determine if conditions
 * are right for a loss of synchronization (by verifying the existence
 * of a gap in the event sequence). Clients then typically use the
 * methods provided by the registration object to both determine if a
 * loss of synchronization has actually occurred, and to correct
 * such a situation when it does occur.
 *
 * @author Sun Microsystems, Inc.
 *
 * @see net.jini.core.event.RemoteEvent
 * @see net.jini.core.lookup.ServiceRegistrar
 */
@AtomicSerial
public class RemoteDiscoveryEvent extends RemoteEvent {

    private static final long serialVersionUID = -9171289945014585248L;

    /**
     * Flag indicating whether the event is a discovery event or a discard
     * event. If this variable is <code>false</code>, then the lookup services
     * referenced by this event were just discovered; if <code>true</code>,
     * then those lookup services were just discarded.
     *
     * @serial
     */
    protected final boolean discarded;

    /**
     * List consisting of marshalled proxy objects where each proxy implements
     * the <code>ServiceRegistrar</code> interface, and each is a proxy of
     * one of the recently discovered or discarded lookup service(s); the
     * lookup service(s) with which this event is associated. 
     * <p>
     * Each proxy in this list is individually marshalled in order to add
     * an additional 'layer' of serialization. Placing this serialization
     * "wrapper" around each element prevents the deserialization mechanism
     * from attempting to deserialize the individual elements in the list.
     * That is, the deserialization mechanism will only deserialize the list
     * itself. After the list itself is successfully deserialized, the client
     * (or a third party, if the client requested that events be sent to a
     * third party such as a mailbox), can then attempt to unmarshal each
     * element separately. This allows each success to be captured, and each
     * failure to be noted.
     * <p>
     * If the elements of this list were not each marshalled separately,
     * then upon encountering failure while attempting to deserialize any
     * one element of the list, the deserialization mechanism's
     * <code>readObject</code> method will throw an <code>IOException</code>;
     * resulting in the loss of all of the elements of the list, even those
     * that could be successfully deserialized.
     *
     * @serial
     */
    private final List<MarshalledObject> marshalledRegs;

    /**
     * Array containing a subset of the set of proxies to the lookup
     * service(s) with which this event is associated. The elements of this
     * array correspond to those elements of the <code>marshalledRegs</code>
     * array that were successfully unmarshalled (at least once) as a result
     * of one or more invocations of the <code>getRegistrars</code> method
     * of this event. Upon deserializing this event, this array is empty,
     * but of the same size as <code>marshalledRegs</code>; and will be
     * populated when the recipient of this event retrieves the registrars
     * corresponding to the elements of <code>marshalledRegs</code>.
     *
     * @serial
     */
    private final ServiceRegistrar[] regs;

    /**
     * <code>Map</code> from the service IDs of the registrars of this event
     * to the groups in which each registrar is a member.
     *
     * @serial
     */
    private final Map<ServiceID,String[]> groups;

    private static GetArg check(GetArg arg) throws IOException {
	RemoteEvent sup = new RemoteEvent(arg);
	if(sup.getSource() == null)
            throw new InvalidObjectException("RemoteDiscoveryEvent.readObject "
                                            +"failure - source field is null");
	try {
	    List<MarshalledObject> marshalledRegs 
		    = (List<MarshalledObject>) arg.get("marshalledRegs", null);

	    List<MarshalledObject> checked = Collections.checkedList(
		new ArrayList<MarshalledObject>(marshalledRegs.size()),
		MarshalledObject.class
	    );
	    checked.addAll(marshalledRegs);

	    // Also handles null case.
	    if (!(arg.get("regs", null) instanceof ServiceRegistrar [])) 
		throw new ClassCastException();

	    Map<ServiceID, String[]> groups 
		    = (Map<ServiceID, String[]>) arg.get("groups", null);
	    // IdentityHashMap used to avoid DOS attack with identical objects.
	    Map<ServiceID, String[]> checkedGroups = Collections.checkedMap(
		new HashMap<ServiceID, String[]>(groups.size()),
		ServiceID.class,
		String[].class
	    );
	    checkedGroups.putAll(groups);
	} catch (ClassCastException ex){
	    InvalidObjectException e = new InvalidObjectException("invariant check failed");
	    e.initCause(ex);
	    throw e;
	}
	return arg;
    }
    
    public RemoteDiscoveryEvent(GetArg arg) throws IOException {
	super(check(arg));
	discarded = arg.get("discarded", false);
	marshalledRegs = new ArrayList<MarshalledObject>(
		(List<MarshalledObject>) arg.get("marshalledRegs", null));
	regs = ((ServiceRegistrar[]) arg.get("regs", null)).clone();
	groups = new HashMap<ServiceID, String[]>(
		(Map<ServiceID, String[]>) arg.get("groups", null));
	integrity = MarshalledWrapper.integrityEnforced(arg);
	atomic = MarshalledWrapper.atomicityEnforced(arg);
    }

    /**
     * Flag related to the verification of codebase integrity. A value of
     * <code>true</code> in this field indicates that the last time this
     * event was unmarshalled, the enforcement of codebase integrity was
     * in effect.
     */
    private transient boolean integrity;
    
    private transient boolean atomic;

    /**
     * Constructs a new instance of <code>RemoteDiscoveryEvent</code>.
     *
     * @param source     reference to the lookup discovery service that
     *                   generated the event
     * @param eventID    the event identifier that maps a particular
     *                   registration to its listener and targeted groups
     *                   and locators
     * @param seqNum     the sequence number of this event
     * @param handback   the client handback (null may be input)
     * @param discarded  flag indicating whether the event being constructed
     *                   is a discovery event or a discard event
     * @param groups     mapping from the registrars of this event to the
     *                   groups in which each registrar is a member
     *
     * @throws java.io.IOException when serialization failure occurs on 
     *         every registrar of this event. That is, if at least one
     *         registrar is successfully serialized, then this exception
     *         will not be thrown.
     *
     * @throws java.lang.NullPointerException this exception occurs when
     *         either <code>null</code> is input for the map parameter, or
     *         at least one element of that map is <code>null</code>.
     *
     * @throws java.lang.IllegalArgumentException this exception occurs
     *         when an empty set of registrars is input.
     */
    @Deprecated
    public RemoteDiscoveryEvent(Object source,
                                long eventID,
                                long seqNum,
                                MarshalledObject handback,
                                boolean discarded,
                                Map<ServiceRegistrar,String[]> groups)    throws IOException
    {
	super(source, eventID, seqNum, handback);
	this.discarded = discarded;
        if(groups != null) {
            /* If the set of registrars is empty, throw exception */
            if(groups.size() == 0) {
                throw new IllegalArgumentException("empty input map");
            }
            ServiceRegistrar[] registrars =
                          (ServiceRegistrar[])(groups.keySet()).toArray
                                        (new ServiceRegistrar[groups.size()]);
            /* If any elements of the array are null, throw exception */
            for(int i=0;i<registrars.length;i++) {
                if(registrars[i] == null) {
                    throw new NullPointerException("null element ("+i
                                                   +") in input map");
                }
            }
            /* Create a new marshalled instance of each element of the
             * registrars array, and place each in the marshalledRegs
             * ArrayList of this class. Also, construct the groups map that
             * contains the mappings from the service ID of each registrar
             * to the registrar's corresponding member groups.
             *
             * Drop any element that can't be serialized.
             */
            this.groups = new HashMap<ServiceID,String[]>(groups.size());
            this.marshalledRegs = new ArrayList(groups.size());
            int l = registrars.length;
            for(int i=0;i<l;i++) {
                try {
                    marshalledRegs.add(new MarshalledInstance(registrars[i]).convertToMarshalledObject());
                    (this.groups).put((registrars[i]).getServiceID(),
                                       groups.get(registrars[i]) );
		} catch(IOException e) { /* drop if can't serialize */ }
            }
            if( !(marshalledRegs.isEmpty()) ) {
                regs = new ServiceRegistrar[marshalledRegs.size()];
            } else {
                throw new IOException("failed to serialize any of the "
                                      +registrars.length+" elements");
            }
	} else {
            throw new NullPointerException("null input map");
        }
    }//end constructor
    
    /**
     * Constructs a new instance of <code>RemoteDiscoveryEvent</code>.
     *
     * @param source     reference to the lookup discovery service that
     *                   generated the event
     * @param eventID    the event identifier that maps a particular
     *                   registration to its listener and targeted groups
     *                   and locators
     * @param seqNum     the sequence number of this event
     * @param handback   the client handback (null may be input)
     * @param discarded  flag indicating whether the event being constructed
     *                   is a discovery event or a discard event
     * @param groups     mapping from the registrars of this event to the
     *                   groups in which each registrar is a member
     *
     * @throws java.io.IOException when serialization failure occurs on 
     *         every registrar of this event. That is, if at least one
     *         registrar is successfully serialized, then this exception
     *         will not be thrown.
     *
     * @throws java.lang.NullPointerException this exception occurs when
     *         either <code>null</code> is input for the map parameter, or
     *         at least one element of that map is <code>null</code>.
     *
     * @throws java.lang.IllegalArgumentException this exception occurs
     *         when an empty set of registrars is input.
     */
    public RemoteDiscoveryEvent(Object source,
                                long eventID,
                                long seqNum,
                                MarshalledInstance handback,
                                boolean discarded,
                                Map<ServiceRegistrar,String[]> groups)    throws IOException
    {
	super(source, eventID, seqNum, handback);
	this.discarded = discarded;
        if(groups != null) {
            /* If the set of registrars is empty, throw exception */
            if(groups.size() == 0) {
                throw new IllegalArgumentException("empty input map");
            }
            ServiceRegistrar[] registrars =
                          (ServiceRegistrar[])(groups.keySet()).toArray
                                        (new ServiceRegistrar[groups.size()]);
            /* If any elements of the array are null, throw exception */
            for(int i=0;i<registrars.length;i++) {
                if(registrars[i] == null) {
                    throw new NullPointerException("null element ("+i
                                                   +") in input map");
                }
            }
            /* Create a new marshalled instance of each element of the
             * registrars array, and place each in the marshalledRegs
             * ArrayList of this class. Also, construct the groups map that
             * contains the mappings from the service ID of each registrar
             * to the registrar's corresponding member groups.
             *
             * Drop any element that can't be serialized.
             */
            this.groups = new HashMap<ServiceID,String[]>(groups.size());
            this.marshalledRegs = new ArrayList(groups.size());
            int l = registrars.length;
            for(int i=0;i<l;i++) {
                try {
                    marshalledRegs.add(new AtomicMarshalledInstance(registrars[i]).convertToMarshalledObject());
                    (this.groups).put((registrars[i]).getServiceID(),
                                       groups.get(registrars[i]) );
		} catch(IOException e) { /* drop if can't serialize */ }
            }
            if( !(marshalledRegs.isEmpty()) ) {
                regs = new ServiceRegistrar[marshalledRegs.size()];
            } else {
                throw new IOException("failed to serialize any of the "
                                      +registrars.length+" elements");
            }
	} else {
            throw new NullPointerException("null input map");
        }
    }//end constructor

    /**
     * Returns the value of the boolean flag that indicates whether this
     * event is a discovery event or a discard event.
     * 
     * @return <code>true</code> if this is a discard event, <code>false</code>
     *         if it is a discovery event.
     */
    public boolean isDiscarded() {
	return discarded;
    }//end isDiscarded

    /**
     * Returns an array consisting of instances of the ServiceRegistrar
     * interface. Each element in the returned set is a proxy to one of
     * the newly discovered or discarded lookup service(s) that caused
     * the current instance of this event class to be sent to the listener
     * of the client's registration. Note that a new array is returned
     * on every call.
     * <p>
     * When the lookup discovery service sends an instance of this event
     * class to the listener of a client's registration, the set of lookup
     * service proxies contained in the event is sent as a set of marshalled
     * instances of the ServiceRegistrar interface. Thus, in order to 
     * construct the return set, this method attempts to unmarshal each
     * element of that set of proxies. Should a failure occur while
     * attempting to unmarshal any of the elements of the set of marshalled
     * proxy objects contained in the current instance of this class, this
     * method will throw an exception of type LookupUnmarshalException. 
     * <p>
     * When a LookupUnmarshalException is thrown by this method, the
     * contents of the exception provides the client with the following
     * useful information: (1) the knowledge that a problem has occurred
     * while unmarshalling at least one of the as yet unmarshalled proxy
     * objects, (2) the set consisting of the proxy objects that were
     * successfully unmarshalled (either on the current invocation of
     * this method or on some previous invocation), (3) the set consisting
     * of the marshalled proxy objects that could not be unmarshalled
     * during the current or any previous invocation of this method, and
     * (4) the set of exceptions corresponding to each failed attempt at
     * unmarshalling during the current invocation of this method.
     * <p>
     * Typically, the type of exception that occurs when attempting to
     * unmarshal an element of the set of marshalled proxies is either an
     * IOException or a ClassNotFoundException. A ClassNotFoundException 
     * occurs whenever a remote field of the marshalled proxy cannot be
     * retrieved (usually because the codebase of one of the field's classes
     * or interfaces is currently 'down'). To address this situation, the
     * client may wish to invoke this method at some later time when the
     * 'down' codebase(s) may be accessible. Thus, the client can invoke
     * this method multiple times until all of the elements of the set of
     * marshalled proxies can be successfully unmarshalled.
     * <p>
     * Note that once an element of the set of marshalled proxy objects has
     * been successfully unmarshalled on a particular invocation of this
     * method, the resulting unmarshalled proxy is stored for return on
     * all future invocations of this method. That is, once successfully
     * unmarshalled, no attempt will be made to unmarshal that element on
     * any future invocations of this method. Thus, if this method returns
     * successfully without throwing a LookupUnmarshalException, the client
     * is guaranteed that all marshalled proxies have been successfully
     * unmarshalled; and any future invocations of this method will return
     * successfully.
     *
     * @return an array consisting of references to the discovered or discarded
     *         lookup service(s) corresponding to this event.
     * 
     * @throws net.jini.discovery.LookupUnmarshalException this exception
     *         occurs when at least one of the set of lookup service
     *         references cannot be deserialized (unmarshalled).
     *
     * @see net.jini.discovery.LookupUnmarshalException
     */
    public ServiceRegistrar[] getRegistrars() throws LookupUnmarshalException {
	synchronized (marshalledRegs) {
            if( marshalledRegs.size() > 0 ) {
                List<ServiceRegistrar> unmarshalledRegs = new ArrayList<ServiceRegistrar>();
                List exceptions = unmarshalRegistrars(marshalledRegs,
                                                           unmarshalledRegs);
                /* Add the un-marshalled elements to the end of regs */
                insertRegistrars(regs,unmarshalledRegs);
                if( exceptions.size() > 0 ) {
                    throw(new LookupUnmarshalException
                      ( clipNullsFromEnd(regs),
                        (MarshalledObject[])(marshalledRegs.toArray
                               (new MarshalledObject[marshalledRegs.size()])),
                        (Throwable[])(exceptions.toArray
                               (new Throwable[exceptions.size()])),
                        "failed to unmarshal at least one ServiceRegistrar") );
                }//endif
            }//endif
            return clipNullsFromEnd(regs);
        }//end sync(marshalledRegs)
    }//end getRegistrars

    /**
     * Returns a set that maps to the service ID of each registrar referenced
     * by this event, the current set of groups in which each registrar is a
     * member.
     * <p>
     * To retrieve the set of member groups corresponding to any element
     * of the array returned by the <code>getRegistrars</code> method,
     * simply use the service ID of the desired element from that array as
     * the key to the <code>get</code> method of the <code>Map</code> object
     * returned by this method and cast to <code>String</code>[].
     * <p>
     * Note that the same <code>Map</code> object is returned on every
     * call to this method; that is, a copy is not made.
     *
     *  @return <code>Map</code> whose key set consists of the service IDs
     *          of each lookup service with which this event is associated,
     *          and whose values are <code>String</code>[] arrays containing
     *          the names of the groups in which the lookup service having
     *          the corresponding service ID is a member.
     */
    public Map<ServiceID,String[]> getGroups() {
        return new HashMap<ServiceID,String[]>(groups);
    }//end getGroups

    /**
     * Attempts to unmarshal each element of the first input argument. When
     * an element of that argument is successfully unmarshalled, that element
     * is removed from the first set and the resulting unmarshalled proxy
     * is placed in the set referenced by the second input argument. 
     * Whenever failure occurs as a result of an attempt to unmarshal one
     * of the elements of the first set, the exception that is thrown as
     * as a result of that failure is placed in the returned set of
     * exceptions, and the element from the first set that caused the
     * failure is left in the first set to facilitate the creation of the
     * <code>LookupUnmarshalException</code> that will ultimately be thrown
     * from <code>getRegistrars</code>.
     * <p>
     * Note that there is a one-to-one correspondence between the exceptions
     * contained in the return set and the remaining elements in the first
     * set after all unmarshalling attempts have completed.
     * 
     * @param marshalledRegs   an ArrayList object consisting of marshalled
     *                         instances of ServiceRegistrar, each 
     *                         corresponding to a proxy to a lookup service.
     *
     * @param unmarshalledRegs an ArrayList object consisting of all
     *                         successfully unmarshalled proxies from
     *                         the first argument.
     *
     * @return an ArrayList consisting of the exceptions that occur as a
     *         result of attempts to unmarshal each element of the first
     *         argument to this method.
     */
    private List<Throwable> unmarshalRegistrars(List<MarshalledObject> marshalledRegs,
                                          List<ServiceRegistrar> unmarshalledRegs)
    {
        ArrayList<Throwable> exceptions = new ArrayList<Throwable>();
       /* Try to un-marshal each element in marshalledRegs; verify codebase
        * when appropriate.
        * 
        * If current element is successfully un-marshalled: 
        *    -- record the un-marshalled element
        *    -- delete the corresponding marshalled element from its set
        * 
        * If current element cannot be un-marshalled:
        *    -- place the exception in the return object
        *    -- leave the corresponding marshalled element in its set
        *    -- increment the index to the next marshalled element
        *
        * Note that index 'n' used in the loop is only a counter. That is,
        * it is intentional that the element at index 'i' is the element
        * that is unmarshalled, not the element at index 'n'. This is because
        * whenever the element is successfully unmarshalled, the element is
        * removed from the set of marshalled registrars, decreasing that set
        * by 1 element. Thus, the 'next' element to unmarshal is actually at
        * the same index as the last element that was unmarshalled.
        */
        int i = 0;
        int nMarshalledRegs = marshalledRegs.size();
        for(int n=0;n<nMarshalledRegs;n++) {
            MarshalledInstance marshalledInstance = atomic ?
		        new AtomicMarshalledInstance(marshalledRegs.get(i)):
			new MarshalledInstance(marshalledRegs.get(i));
            try {
                /* Success: record the un-marshalled element
                 *          delete the corresponding un-marshalled element
                 */
                unmarshalledRegs.add( (ServiceRegistrar) marshalledInstance.get(integrity));
                marshalledRegs.remove(i);
            } catch(Throwable e) {
                exceptions.add(e);
                i=i+1;
            }
        }//end loop
        return exceptions;
    }//end unmarshalRegistrars

    /**
     * Places the lookup service reference(s), contained in the input
     * ArrayList, into the 'empty' slots occurring at the end (indicated
     * by the first null element) of the input array.
     * 
     * @param regsArray array that will receive the new references.
     * 
     * @param regsList List containing the ServiceRegistrar references
     *        to place in regsArray input argument.
     */
    private static void insertRegistrars(ServiceRegistrar[] regsArray,
                                         List regsList)
    {
        if((regsArray != null) && (regsList != null)) {
            int lenA = regsArray.length;
            int lenB = regsList.size();
            if((lenA == 0) || (lenB == 0)) return;
            int beg = indexFirstNull(regsArray);
            int end = ( (beg+lenB) <= lenA ? (beg+lenB) : (lenA) );
            for(int i=beg, j=0; i<end; i++,j++) {
                regsArray[i] = (ServiceRegistrar)(regsList.get(j));
            }
        }
    }//end insertRegistrars

    /**
     * Convenience method that copies elements from the given array into
     * a new array, and returns the new array. This method begins with the
     * first element in the given array, and stops when it encounters the
     * first <code>null</code> element.
     * 
     * @param regsArray array from which to copy elements
     * 
     * @return array of <code>ServiceRegistrar</code> containing each element
     *         of the given array from its first element up to, but not 
     *         including, the <code>null</code> element; and all subsequent
     *         elements. If the first element of the given array is 
     *         <code>null</code>, then this method will return an empty array.
     */
    private static ServiceRegistrar[] clipNullsFromEnd
                                               (ServiceRegistrar[] regsArray)
    {
        if( regsArray == null) return new ServiceRegistrar[0];
        int clippedLen = indexFirstNull(regsArray);
        ServiceRegistrar[] clippedArray = new ServiceRegistrar[clippedLen];
        for(int i=0; i<clippedLen; i++) {
            clippedArray[i] = regsArray[i];
        }//end loop
        return clippedArray;
    }//end clipNullsFromEnd

    /**
     * Finds the index of the first element in the input array that contains
     * null.
     * <p>
     * If the array is null (or has zero length), -1 will be returned. If
     * every element of the array is non-null, this method will return
     * the length of the array. Thus, after invoking this method, it is
     * important to test for these conditions to avoid the occurrence of an 
     * IndexOutOfBoundsException when using the value returned by this
     * method.
     * 
     * @param arr Object array to examine for the first occurrence of null
     *
     * @return the index of the first element in the input array that contains
     *         null. A value of -1 is returned if the input array is null;
     *         the length of the array is returned if no element in the
     *         array is null.
     */
    private static int indexFirstNull(Object[] arr) {
        int i = -1;
        if( (arr == null) || (arr.length == 0) ) return i;
        for(i=0;i<arr.length;i++) {
            if(arr[i] == null) return i;
        }
        return i;
    }//end indexFirstNull

    private void writeObject(ObjectOutputStream out) throws IOException {
	out.defaultWriteObject();
    }

    /** 
     * When an instance of this class is deserialized, this method is
     * automatically invoked. This implementation of this method validates
     * the state of the deserialized instance, and additionally determines
     * whether or not codebase integrity verification was performed when
     * unmarshalling occurred.
     *
     * @throws InvalidObjectException if the state of the
     *         deserialized instance of this class is found to be invalid.
     */
    private void readObject(ObjectInputStream s)  
                               throws IOException, ClassNotFoundException
    {
        s.defaultReadObject();
        /* Verify source */
        if(getSource() == null) {
            throw new InvalidObjectException("RemoteDiscoveryEvent.readObject "
                                            +"failure - source field is null");
        }//endif
        /* Retrieve the value of the integrity flag */
        integrity = MarshalledWrapper.integrityEnforced(s);
	atomic = false; // Atomic streams don't call readObject.
    }//end readObject

}//end class RemoteDiscoveryEvent

