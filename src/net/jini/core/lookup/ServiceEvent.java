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
package net.jini.core.lookup;

/**
 * This class is used for remote events sent by the lookup service.  It
 * extends RemoteEvent with methods to obtain the service ID of the matched
 * item, the transition that triggered the event, and the new state of
 * the item.
 * <p>
 * Sequence numbers for a given event ID are strictly increasing.  If there
 * is no gap between two sequence numbers, no events have been missed; if
 * there is a gap, events might (but might not) have been missed.  For
 * example, a gap might occur if the lookup service crashes, even if no
 * events are lost due to the crash.
 *
 * @author Sun Microsystems, Inc.
 *
 * @since 1.0
 */
public abstract class ServiceEvent extends net.jini.core.event.RemoteEvent {

    private static final long serialVersionUID = 1304997274096842701L;

    /**
     * ServiceID of the item that triggered the event.
     *
     * @serial
     */
    protected ServiceID serviceID;
    /**
     * One of ServiceRegistrar.TRANSITION_*MATCH_*MATCH.
     *
     * @serial
     */
    protected int transition;

    /**
     * Simple constructor.
     *
     * @param source the source of this ServiceEvent
     * @param eventID the registration eventID
     * @param seqNo the sequence number of this event
     * @param handback the client handback
     * @param serviceID the serviceID of the item that triggered the event
     * @param transition the transition that triggered the event
     */
    public ServiceEvent(Object source,
			long eventID,
			long seqNo,
			java.rmi.MarshalledObject handback,
			ServiceID serviceID,
			int transition)
    {
	super(source, eventID, seqNo, handback);
	this.serviceID = serviceID;
	this.transition = transition;
    }

    /** Returns the serviceID of the item that triggered the event. 
     *
     * @return a <tt>ServiceID</tt> object representing the service ID value
     */
    public ServiceID getServiceID() {
	return serviceID;
    }

    /** Returns the singleton transition that triggered the event.
     *
     * @return an <tt>int</tt> representing the transition value
     */

    public int getTransition() {
	return transition;
    }
    
    /** 
     * Returns a <code>String</code> representation of this 
     * <code>ServiceEvent</code>.
     * @return a <code>String</code> representation of this object
     */
    public String toString() {
	StringBuffer sBuffer = new StringBuffer();
	sBuffer.append(getClass().getName()).append(
	       "[serviceID=").append(getServiceID()).append(
	       ", transition=");
	switch (getTransition()) {
	    case ServiceRegistrar.TRANSITION_MATCH_MATCH:
		sBuffer.append("TRANSITION_MATCH_MATCH");
		break;
	    case ServiceRegistrar.TRANSITION_MATCH_NOMATCH:
		sBuffer.append("TRANSITION_MATCH_NOMATCH");
		break;
	    case ServiceRegistrar.TRANSITION_NOMATCH_MATCH:
		sBuffer.append("TRANSITION_NOMATCH_MATCH");
		break;
	    default:
	        sBuffer.append("UNKNOWN_TRANSITION:").append(
	            transition);
	}
	sBuffer.append(", source=").append(source).append(
	    ", eventID=").append(getID()).append(
	    ", seqNum=").append(getSequenceNumber()).append(
	    ", handback=").append(getRegistrationObject());
	return sBuffer.append("]").toString();
    }

    /**
     * Returns the new state of the item, or null if the item was deleted
     * from the lookup service.
     *
     * @return a <tt>ServiceItem</tt> object representing the service item value
     */
    public abstract ServiceItem getServiceItem();
}

