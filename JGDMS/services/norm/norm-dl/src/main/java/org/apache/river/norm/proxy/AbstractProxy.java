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

package org.apache.river.norm.proxy;

import java.io.IOException;
import java.io.InvalidObjectException;
import java.io.ObjectInputStream;
import java.io.Serializable;
import net.jini.export.ProxyAccessor;
import net.jini.id.ReferentUuid;
import net.jini.id.ReferentUuids;
import net.jini.id.Uuid;
import org.apache.river.api.io.AtomicSerial;
import org.apache.river.api.io.AtomicSerial.GetArg;

/**
 * Defines an abstract class that supplies basic referent UUID and
 * serialization behavior for Norm proxies.
 *
 * @author Sun Microsystems, Inc.
 * @since 2.0
 */
@AtomicSerial
abstract class AbstractProxy implements ReferentUuid, Serializable, ProxyAccessor {
    private static final long serialVersionUID = 1;

    /**
     * The server.
     *
     * @serial
     */
    final NormServer server;

    /**
     * The unique identifier for this proxy.
     *
     * @serial
     */
    final Uuid uuid;

    /** Creates an instance of this class. */
    AbstractProxy(NormServer server, Uuid uuid) {
	this(server, uuid, check(server, uuid));
    }
    
    private AbstractProxy(NormServer server, Uuid uuid, boolean check){
	this.server = server;
	this.uuid = uuid;
    }
    
    AbstractProxy(GetArg arg) throws IOException {
	this((NormServer) arg.get("server", null),
	    (Uuid) arg.get("uuid", null),
	    check(arg));
    }
    
    private static boolean check(NormServer server, Uuid uuid) {
	if (server == null) {
	    throw new NullPointerException("server cannot be null");
	} else if (uuid == null) {
	    throw new NullPointerException("uuid cannot be null");
	}
	return true;
    }

    private static boolean check(GetArg arg) throws IOException {
	NormServer server = (NormServer) arg.get("server", null);
	Uuid uuid = (Uuid) arg.get("uuid", null);
	if (server == null || uuid == null) {
	    throw new InvalidObjectException(
		"server and uuid must be non-null");
	}
	return true;
    }

    /** Require fields to be non-null. */
    private void readObjectNoData() throws InvalidObjectException {
	throw new InvalidObjectException(
	    "server and uuid must be non-null");
    }

    /** Require fields to be non-null. */
    private void readObject(ObjectInputStream in)
	throws IOException, ClassNotFoundException
    {
	in.defaultReadObject();
	if (server == null || uuid == null) {
	    throw new InvalidObjectException(
		"server and uuid must be non-null");
	}
    }

    /** Returns true if the object has the same UUID as this instance. */
    public boolean equals(Object object) {
	return ReferentUuids.compare(this, object);
    }

    /** Returns a hash code for this object. */
    public int hashCode() {
	return uuid.hashCode();
    }

    /* -- Implement ReferentUuid -- */

    /* inherit javadoc */
    public Uuid getReferentUuid() {
	return uuid;
    }
    
    @Override
    public Object getProxy() {
	return server;
    }
}
