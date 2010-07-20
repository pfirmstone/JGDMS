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

import net.jini.lookup.StreamServiceRegistrarFacade;
import net.jini.lookup.ServiceRegistrarFacade;
import java.util.ArrayList;
import java.util.EventObject;
import java.util.Map;
import net.jini.core.lookup.PortableServiceRegistrar;
import net.jini.core.lookup.ServiceRegistrar;
import net.jini.core.lookup.StreamServiceRegistrar;

/**
 * Event object passed (via either the <code>DiscoveryListener</code>
 * interface or the <code>DiscoveryChangeListener</code>) to indicate to
 * interested parties that one or more <code>ServiceRegistrar</code>
 * objects have been discovered or discarded during the discovery process.
 *
 * @author Sun Microsystems, Inc.
 *
 * @see LookupDiscovery
 * @see LookupLocatorDiscovery
 * @see LookupDiscoveryManager
 * @see DiscoveryListener
 * @see DiscoveryChangeListener
 * @see net.jini.core.lookup.ServiceRegistrar
 */
public class DiscoveryEvent extends EventObject {
    private static final long serialVersionUID = 5280303374696501479L;

    /**
     * The registrars with which this event is associated.
     *
     * @serial
     */
    private PortableServiceRegistrar[] regs;

    /**
     * Map from the registrars of this event to the groups in which each
     * is a member.
     *
     * @serial
     */
    private Map groups;

    /**
     * Construct a new <code>DiscoveryEvent</code> object, with the given
     * source and set of registrars.  The set of registrars should not be
     * empty. This constructor has been left in place to maintain binary
     * compatibility.
     *
     * @param source the source of this event
     * @param regs   the registrars to which this event applies
     * @deprecated 
     */
    @Deprecated
    public DiscoveryEvent(Object source, ServiceRegistrar[] regs) {
	super(source);
	this.regs = regs;
	this.groups = null;
    }
    
    /**
     * Construct a new <code>DiscoveryEvent</code> object, with the given
     * source and set of registrars.  The set of registrars should not be
     * empty.
     *
     * @param source the source of this event
     * @param regs   the registrars to which this event applies
     */
    public DiscoveryEvent(Object source, PortableServiceRegistrar[] regs) {
	super(source);
	this.regs = regs;
	this.groups = null;
    }

    /**
     * Construct a new <code>DiscoveryEvent</code> object, with the given
     * source and registrars-to-groups mapping. The mapping should not be
     * empty.
     *
     * @param source the source of this event
     * @param groups mapping from the elements of the registrars of this
     *               event to the member groups in which each registrar is
     *               a member
     */
    public DiscoveryEvent(Object source, Map groups) {
	super(source);
	this.groups = groups;
        this.regs   = (PortableServiceRegistrar[])(groups.keySet()).toArray
                                       (new PortableServiceRegistrar[groups.size()]);
    }

    /**
     * Return the set of registrars to which this event applies.
     * The same array is returned on every call; a copy is not made.
     * 
     * I'm going to experiment with returning a copy to see if anything
     * breaks.
     * 
     * @return the set of registrars to which this event applies.
     */
    public ServiceRegistrar[] getRegistrars() {
        int l = regs.length;
        ArrayList<ServiceRegistrar> sr = new ArrayList<ServiceRegistrar>(l);
        for ( int i = 0; i < l; i++ ){
            if (regs[i] instanceof ServiceRegistrar) {
                sr.add( (ServiceRegistrar) regs[i]);
            } else {
                sr.add( new ServiceRegistrarFacade(regs[i]));
            }
        }
        ServiceRegistrar[] sra = new ServiceRegistrar[sr.size()];
        return sr.toArray(sra);
    }
    
    /**
     * Return the set of registrars to which this event applies.
     * The same array is returned on every call; a copy is not made.
     * 
     * I'm going to experiment with returning a copy to see if anything
     * breaks.
     * 
     * @return the set of registrars to which this event applies.
     */
    public StreamServiceRegistrar[] getStreamRegistrars() {
        int l = regs.length;
        ArrayList<StreamServiceRegistrar> sr = new ArrayList<StreamServiceRegistrar>(l);
        for ( int i = 0; i < l; i++ ){
            if (regs[i] instanceof StreamServiceRegistrar) {
                sr.add( (StreamServiceRegistrar) regs[i]);
            } else {
                sr.add( new StreamServiceRegistrarFacade(regs[i]));
            }
        }
        StreamServiceRegistrar[] sra = new StreamServiceRegistrar[sr.size()];
        return sr.toArray(sra);
    }
    
    /**
     * Return the set of registrars to which this event applies.
     * The same array is returned on every call; a copy is not made.
     * 
     * I'm going to experiment with returning a copy to see if anything
     * breaks.
     * 
     * @return the set of registrars to which this event applies.
     */
    public PortableServiceRegistrar[] getPRegistrars() {
        return regs.clone();      
    }
    
    /**
     * Returns a set that maps to each registrar referenced by this event,
     * the current set of groups in which each registrar is a member.
     * <p>
     * To retrieve the set of member groups corresponding to any element
     * of the array returned by the <code>getRegistrars</code> method,
     * simply use the desired element from that array as the key to the
     * <code>get</code> method of the <code>Map</code> object returned
     * by this method and cast to <code>String</code>[].
     * <p>
     * Note that the same <code>Map</code> object is returned on every
     * call to this method; that is, a copy is not made.
     *
     *  @return <code>Map</code> in which the keys are the elements of the
     *          array returned by the <code>getRegistrars</code> method
     *          of this class; and the values are <code>String</code>[]
     *          arrays containing the member groups corresponding to each
     *          registrar.
     */
    public Map getGroups() {
        return groups;
    }
}
