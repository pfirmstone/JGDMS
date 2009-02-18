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
package com.sun.jini.test.impl.mercury;

import net.jini.core.event.RemoteEvent;
import java.rmi.MarshalledObject;

public class RemoteEventHandle
{
    private RemoteEvent event;
    public RemoteEventHandle(RemoteEvent event) {
	this.event = event;
    }

    public RemoteEvent getRemoteEvent() {
	return event;
    }

    //
    // equality means that the following must match:
    // - event source
    // - event ID
    // - sequence number
    //
    // Another check we perform, is making sure that the handbacks are
    // are equal too. This check, in general, isn't a valid check, but
    // since we are controlling the event generation, it's OK.
    //
    public boolean equals(Object obj) {
	//System.out.println("RemoteEventHandle: equals:");

	if (!(obj instanceof RemoteEventHandle))
	    return false;

	RemoteEvent other = ((RemoteEventHandle) obj).getRemoteEvent();

	Object tmpSrc = other.getSource();
	Object thisSrc = event.getSource();
	long tmpID = other.getID();
	long thisID = event.getID();
	long tmpSeq = other.getSequenceNumber();
	long thisSeq = event.getSequenceNumber();
	MarshalledObject tmpObj = other.getRegistrationObject();
	MarshalledObject thisObj = event.getRegistrationObject();

	if (!thisSrc.equals(tmpSrc)) {
	    //System.out.println("RemoteEventHandle: equals: " +
	    //	"event source not equal");
	    return false;
	}

	if (thisID != tmpID) {
	    //System.out.println("RemoteEventHandle: equals: " +
	    //	"event IDs not equal");
	    return false;
	}

	if (thisSeq!= tmpSeq) {
	    //System.out.println("RemoteEventHandle: equals: " +
	    //"sequence numbers not equal");
	    return false;
	}

	if (thisObj == null) {
	    return tmpObj == null;
	}
	else if (!thisObj.equals(tmpObj)) { 
	    //System.out.println("RemoteEventHandle: equals: " +
	    //    "registration objects not equal");
	    return false;
	}

	//System.out.println("RemoteEventHandle: equals: they are equal");
        return true;
    }

    public int hashCode() {
	long thisID = event.getID();
	long thisSeq = event.getSequenceNumber();
        return (int)((thisID >> 32) ^ thisID ^ (thisSeq >> 32) ^ thisSeq);
    }
}


