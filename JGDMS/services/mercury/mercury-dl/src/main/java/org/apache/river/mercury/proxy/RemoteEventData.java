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
package org.apache.river.mercury.proxy;

import org.apache.river.proxy.MarshalledWrapper;
import java.io.IOException;
import java.io.InvalidObjectException;
import net.jini.core.event.RemoteEvent;
import net.jini.io.MarshalledInstance;
import org.apache.river.api.io.AtomicSerial;
import org.apache.river.api.io.AtomicSerial.GetArg;
import org.apache.river.api.io.AtomicSerial.PutArg;
import org.apache.river.api.io.AtomicSerial.SerialForm;

/**
 * Simple struct to hold a <code>RemoteEvent</code> and its associated
 * <code>Object</code> (cookie) obtained from an <code>EventLog</code>.
 */
@AtomicSerial
public class RemoteEventData {

    /**
     * <code>MarshalledObject</code> that holds desired
     * <code>RemoteEvent</code>. Wrapping the remote event
     * permits deserialization to occur on demand on the
     * client-side.
     */
    private final MarshalledInstance mi;

    /** Cookie associated with the <code>RemoteEvent</code> */
    private final Object cookie;

    /**
     * <code>true</code> if the last time this object was unmarshalled
     * integrity was being enforced, <code>false</code> otherwise.
     */
    private final boolean integrity;

    public static SerialForm[] serialForm() {
        return new SerialForm[] {
            new SerialForm("mi", MarshalledInstance.class),
            new SerialForm("cookie", Object.class)
        };
    }

    public static void serialize(PutArg arg, RemoteEventData o) throws IOException {
        arg.put("mi", o.mi);
        arg.put("cookie", o.cookie);
        arg.writeArgs();
    }

    /**
     * Creates a new RemoteEventData instance.
     * @param re value of <code>re</code> field.
     * @param cookie value of <code>cookie</code> field.
     */
    public RemoteEventData(RemoteEvent re, Object cookie) {
        this(convert(re), cookie, false);
    }

    RemoteEventData(GetArg arg) throws IOException, ClassNotFoundException {
	// check(arg) performs the invariant validation (null cookie) that was
	// previously in readObject, and the integrity flag is captured from the
	// stream context here so it can be assigned to the final field.
	this(check(arg), arg.get("cookie", null, Object.class),
	     MarshalledWrapper.integrityEnforced(arg));
    }

    private RemoteEventData(MarshalledInstance mi, Object cookie, boolean integrity){
	this.mi = mi;
	this.cookie = cookie;
	this.integrity = integrity;
    }
    
    private static MarshalledInstance convert(RemoteEvent re){
	MarshalledInstance mi;
        try {
            mi = (re==null)?null:new MarshalledInstance(re);
        } catch (IOException ioe) {
            mi = null;
        }
	return mi;
    }
    
    private static MarshalledInstance check(GetArg arg) throws IOException, ClassNotFoundException {
	MarshalledInstance mi = arg.get("mi", null, MarshalledInstance.class);
	Object cookie = arg.get("cookie", null, Object.class);
	if (cookie == null) 
	    throw new InvalidObjectException("null cookie");
	return mi;
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

}
