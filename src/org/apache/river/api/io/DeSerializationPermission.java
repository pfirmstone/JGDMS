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

package org.apache.river.api.io;

import java.security.BasicPermission;

/**
 * Permission that when granted, allows de-serialization of an object.
 * Only the protection domains of each class in an object hierarchy are tested,
 * prior to construction.  Any parameter objects have already been checked.
 * <p>
 * DeSerializationPermission is different to other permissions, where other 
 * Permissions guard something, and all domains on the call stack must have
 * the Permission, DeSerializationPermission is only checked against the 
 * domains representing the class hierarchy of an object about to be 
 * de-serialized.  It is about trusting the classes of the object to check
 * all invariants while reading a stream from an untrusted source.  This also
 * allows a developer to utilize libraries, while minimizing the available
 * classes that can participate in atomic de-serialization.
 * <p>
 * There are only four types:
 * <li>ATOMIC</li>
 * <li>EXTERNALIZABLE</li>
 * <li>MARSHALLED</li>
 * <li>PROXY</li>
 *<p>
 * Permissions should only be granted to domains that are trusted to read in a
 * serial stream from an untrusted data source.
 * <p>
 * No permission is required for stateless objects that implement Serializable.
 * <p>
 * MARSHALLED - MarshalledObject allows any Serialized object to be transferred
 * over a stream, it is often used to compare the serial form of objects, 
 * however because MarshalledObject allows any Serializable class to be 
 * deserialized, it would be unsafe to unmarshal a MarshalledObject instance,
 * so this permission should only be granted for cases where MarshalledObject
 * is not unmarshalled.
 * 
 * @author peter
 * @since 3.0.0
 */
public class DeSerializationPermission extends BasicPermission {

    /**
     * The ProtectionDomain of a class that is de-serializing (reading object input)
     * from an {@link AtomicMarshalInputStream} requires permission to do so.
     * 
     * @param type one of the following 
     * <li>ATOMIC - Classes that implement {@link AtomicSerial} or {@link AtomicExternal}</li>
     * <li>EXTERNALIZABLE - Classes that implement {@link Externalizable}</li>
     * <li>MARSHALLED - {@link net.jini.io.MarshalledInstance} or {@link java.rmi.MarshalledObject}</li>
     * <li>PROXY - any class extending {@link java.lang.reflect.Proxy}, dynamically generated proxy's already have this permission.</li>
     */
    public DeSerializationPermission(String type) {
	super(type);
    }

}
