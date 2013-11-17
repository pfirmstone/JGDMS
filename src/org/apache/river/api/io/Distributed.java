/*
 *  Licensed to the Apache Software Foundation (ASF) under one or more
 *  contributor license agreements.  See the NOTICE file distributed with
 *  this work for additional information regarding copyright ownership.
 *  The ASF licenses this file to You under the Apache License, Version 2.0
 *  (the "License"); you may not use this file except in compliance with
 *  the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package org.apache.river.api.io;

/**
 * Distributed objects are suitable for use as immutable value objects with 
 * final fields that may be freely replicated, alternatively they are also 
 * suited as safely published thread safe mutable objects used 
 * to store service snapshots in a {@link net.jini.io.MarshalledInstance} for 
 * fail over replication to other nodes, or to upgrade a service.
 * <p>
 * Distributed objects are not serialized, instead they are only created using an 
 * accessible constructor, public static factory method or builder object making them
 * more suitable for security; validating class invariants and concurrent code
 * that relies on immutability and safe publication of fields using final or
 * volatile.
 * <p>
 * Distributed Objects are created remotely with an AccessControlContext
 * containing one ProtectionDomain with a CodeSource that has a null location 
 * and null Certificates.  
 * Only minimal permissions granted to any location by the administrator will apply.  
 * Minimal privilege is required to prevent remote instantiation 
 * of ClassLoader, Policy, SecurityManager, or any other type of object with 
 * security checks performed during construction.  The developer is free to
 * use privileged access, including login context from within constructors and
 * methods.
 * <p>
 * The serial form of a Distributed object is managed by SerialReflectionFactory
 * and is solely dependant on the classes, parameters and signatures of methods
 * or constructors.  
 * <p>
 * Distributed Objects with equal SerialReflectionFactory's shall be identical 
 * in Object form after un-marshaling within an identical jvm and one 
 * may be substituted for the other to reduce network traffic.
 * <p>
 * Distributed Objects that are equal in Object form are not guaranteed to be equal
 * in serial form.  The implementor may enforce serial form equality by ensuring
 * identical methods of creation are used for equal objects and document it in 
 * javadoc. Distributed objects equal in Object form at one node should also be
 * equal after un-marshaling to a second remote node even when serial form differs. 
 * <p>
 * Distributed Objects (boomerangs) that are duplicated across 
 * nodes may not be equal when returning to a local node after construction and 
 * redistribution on different nodes.  Later versions of code may elect to 
 * use different classes, constructors or method signatures that result in
 * inequality.
 * <p>
 * Distributed objects while free to evolve and possibly having completely different 
 * classes or being completely unequal after distribution to separate nodes, 
 * must always share a common public interface or superclass for referential
 * purposes, this may of course be Object, however if it is, it should be stated
 * clearly in Javadoc to avoid ClassCastException's upon un-marshaling.
 * <p>
 * Distributed objects have no version, instead SerialReflectionFactory contains all 
 * information required to distribute and recreate any Distributed Object using
 * reflection.  For this reason, Distributed objects cannot be used as Entry
 * objects, as they are dependant on published serial form.  It may be possible
 * in a later release to use Distributed objects as fields in Entry objects, this
 * is not supported presently.
 * <p>
 * Distributed objects are recommended for use as value objects in domain 
 * driven design.
 * <p>
 * Although final is not enforced, all fields should be final or volatile, safe
 * construction must be honored 'this' must not be allowed to 
 * escape during construction, distributed objects will be exposed to multiple
 * threads on multiple nodes, without external synchronization.
 * <p>
 * Distributed objects are thread safe.
 * <p>
 * Do not use Distributed if you don't intend to honor this contract, use
 * Serializable instead.
 * <p>
 * Caveat:<br>
 * Distributed Objects cannot be stored directly in a 
 * {@link java.rmi.MarshalledObject}, a {@link net.jini.io.MarshalledInstance}
 * must first be created and converted, also a Distributed Object will
 * returned as a {@link SerialReflectionFactory} when {@link java.rmi.MarshalledObject}
 * is un-marshaled, the {@link java.rmi.MarshalledObject} must first be
 * converted to {@link net.jini.io.MarshalledInstance} before un-marshaling.
 * <p>
 * @author Peter Firmstone.
 * @since 3.0.0
 */
public interface Distributed {
    
    /**
     * Substitutes an Object in an ObjectOutput with a SerialReflectionFactory,
     * ObjectInput uses SerialReflectionFactory to reconstruct the Object at the 
     * remote end using reflection to call a constructor, static method or
     * object method.
     * 
     * @return SerialReflectionFactory for object remote instantiation using
     * reflection to call a constructor, static method or object method.
     */
    SerialReflectionFactory substitute();
}
