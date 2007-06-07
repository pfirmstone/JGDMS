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
package com.sun.jini.mercury;

import com.sun.jini.proxy.MarshalledWrapper;

import java.io.InvalidObjectException;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.Serializable;
import java.util.Collection;

import net.jini.core.event.RemoteEvent;
import net.jini.id.Uuid;
import net.jini.io.MarshalledInstance;

/**
 * Simple struct to hold a <code>RemoteEvent</code> and its associated 
 * <code>Object</code> (cookie) obtained from an <code>EventLog</code>.
 */
class RemoteEventData implements Serializable {
    private static final long serialVersionUID = 1L;

    /**
     * <code>MarshalledObject</code> that holds desired
     * <code>RemoteEvent</code>. Wrapping the remote event
     * permits deserialization to occur on demand on the 
     * client-side.
     */
    private MarshalledInstance mi;

    /** Cookie associated with the <code>RemoteEvent</code> */
    private final Object cookie;
    
    /** 
     * <code>true</code> if the last time this object was unmarshalled 
     * integrity was being enforced, <code>false</code> otherwise.
     */
    private transient boolean integrity;

    /**
     * Creates a new RemoteEventData instance.
     * @param re value of <code>re</code> field.
     * @param cookie value of <code>cookie</code> field.
     */
    RemoteEventData(RemoteEvent re, Object cookie) {
        try {
            mi = (re==null)?null:new MarshalledInstance(re);
        } catch (IOException ioe) {
            mi = null;
        }
	this.cookie = cookie;
    }
    
    public RemoteEvent getRemoteEvent() throws ClassNotFoundException {
        if (mi == null) 
            throw new ClassNotFoundException(
                "Failed to create server-side remote event");
        RemoteEvent re = null;
        try {
            re = (RemoteEvent)mi.get(integrity);
        } catch (IOException ioe) {
            throw new ClassNotFoundException(
                "Failed to create client-side remote event", ioe);
        }
        return re;
    }
    
    public Object getCookie() {
        return cookie;
    }
    
    /**
     * Use <code>readObject</code> method to capture whether or
     * not integrity was being enforced when this object was
     * unmarshalled, and to perform basic integrity checks.
     */
    private void readObject(ObjectInputStream in)
	throws IOException, ClassNotFoundException
    {
	in.defaultReadObject();

	if (cookie == null) 
	    throw new InvalidObjectException("null cookie");

	// get value for integrity flag
	integrity = MarshalledWrapper.integrityEnforced(in);
    }
    
    /** 
     * We should always have data in the stream, if this method
     * gets called there is something wrong.
     */
    private void readObjectNoData() throws InvalidObjectException {
	throw new 
	    InvalidObjectException("RemoteEventData should always have data");
    }

}
