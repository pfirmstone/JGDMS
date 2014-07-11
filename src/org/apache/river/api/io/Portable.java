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
 * Portable is an extension to the Java Serialization Framework.
 * 
 * Portable objects are suitable for use as immutable value objects with 
 * final fields that may be freely replicated, alternatively they are also 
 * suited as safely published thread safe mutable objects used 
 * to store service snapshots in a {@link net.jini.io.MarshalledInstance} for 
 * fail over replication to other nodes, or to upgrade a service.
 * <p>
 * Portable objects are not serialized, instead they are only created using an 
 * accessible constructor, public static factory method or builder object making them
 * more suitable for security; validating class invariants and concurrent code
 * that relies on immutability and safe publication of fields using final or
 * volatile.
 * <p>
 * Portable Objects are created remotely with an AccessControlContext
 * containing one ProtectionDomain with a CodeSource that has a null location 
 * and null Certificates.  
 * Only minimal permissions granted to any location by the administrator will apply.  
 * Minimal privilege is required to prevent remote instantiation 
 * of ClassLoader, Policy, SecurityManager, or any other type of object with 
 * security checks performed during construction.  The developer is free to
 * use privileged access, including login context from within constructors and
 * methods.
 * <p>
 * The serial form of a Portable object is managed by PortableFactory
 * and is solely dependant on the classes, parameters and signatures of methods
 * or constructors.  
 * <p>
 * Portable Objects with equal PortableFactory's shall be identical 
 * in Object form after un-marshaling within an identical jvm and one 
 * may be substituted for the other to reduce network traffic.
 * <p>
 * Portable Objects that are equal in Object form are not guaranteed to be equal
 * in serial form.  The implementor may enforce serial form equality by ensuring
 * identical methods of creation are used for equal objects and document it in 
 * Javadoc. Portable objects equal in Object form at one node should also be
 * equal after un-marshaling to a second remote node even when serial form differs. 
 * <p>
 * Portable Objects (boomerangs) that are duplicated across 
 * nodes may not be equal when returning to a local node after construction and 
 * redistribution on different nodes.  Later versions of code may elect to 
 * use different classes, constructors or method signatures that result in
 * inequality.
 * <p>
 * Portable objects while free to evolve and possibly having completely different 
 * classes or being completely unequal after distribution to separate nodes, 
 * must always share a common public interface or superclass for referential
 * purposes, this may of course be Object, however if it is, it should be stated
 * clearly in Javadoc to avoid ClassCastException's upon un-marshaling.
 * <p>
 * Portable objects have no version, instead PortableFactory contains all 
 * information required to recreate any Portable Object.  
 * For this reason, Portable objects cannot be used as Entry
 * objects, as they are dependant on published serial form.  It may be possible
 * in a later release to use Portable objects as fields in Entry objects, this
 * is not supported presently.
 * <p>
 * Portable objects are highly recommended for use as value objects in domain 
 * driven design, they may also be used for value objects.  PortableFactory can
 * be used to create the root entity in an aggregate.
 <p>
 * Although final is not enforced, all fields should be final or volatile, safe
 * construction must be honored 'this' must not be allowed to 
 * escape during construction, Portable objects will be exposed to multiple
 * threads on multiple nodes, without external synchronization.
 * <p>
 * Portable objects are thread safe.
 * <p>
 * Do not use Portable if you don't intend to honor this contract, use
 * Serializable instead.
 * <p>
 * Caveat:<br>
 * Portable Objects cannot be stored directly in a 
 * {@link java.rmi.MarshalledObject}, a {@link net.jini.io.MarshalledInstance}
 * must first be created and converted, also a Portable Object will be
 * returned as a {@link PortableFactory} when {@link java.rmi.MarshalledObject}
 * is un-marshaled, a {@link java.rmi.MarshalledObject} must first be
 * converted to {@link net.jini.io.MarshalledInstance} before un-marshaling.
 * <p>
 * @author Peter Firmstone.
 * @since 3.0.0
 */
public interface Portable {
    
    /**
     * Prepare for transport in a PortableObjectOutputStream. 
     * ObjectInput uses PortableFactory to create the Portable Object at the 
     * remote end using a constructor, static method or object method.
     * 
     * @return A PortableFactory, PortableObjectInputStream uses PortableFactory
     * to create a Portable Object at the remote end using a constructor,
     * static method or an object method.
     */
    PortableFactory factory();
}
