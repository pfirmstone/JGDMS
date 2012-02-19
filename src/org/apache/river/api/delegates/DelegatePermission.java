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

package org.apache.river.api.delegates;

import java.io.IOException;
import java.io.InvalidObjectException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.security.Permission;
import java.security.PermissionCollection;
import java.security.Permissions;
import java.util.Collections;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ConcurrentSkipListMap;
import org.apache.river.api.security.PermissionComparator;
import org.apache.river.api.security.CachingSecurityManager;
import org.apache.river.impl.util.RC;
import org.apache.river.impl.util.Ref;
import org.apache.river.impl.util.Referrer;

/**
 * A DelegatePermission represents any another Permission, called the candidate.
 * 
 * A DelegatePermission is used by a Security Delegate in checkPermission
 * calls.
 * 
 * A Security Delegate does not have an interface that identifies it as a Delegate,
 * it is a wrapper class that has an identical interface, where 
 * practical, to the object it encapsulates, to disguise it from clients.
 * 
 * Security Delegates enable sensitive objects to be used by code that isn't
 * fully trusted you may want to monitor, such as a 
 * file write that is limited by the number of bytes written, or a Permission
 * to write a file, that we might decide to retract or revoke if a user
 * does something we don't like, such as exceed a pre set limit or behave
 * in a manner we would like to avoid, such as hogging network bandwidth.
 * 
 * A DelegatePermission never implies it's candidate, however if a 
 * ProtectionDomain has the Permission the delegate represents, then the 
 * CachingSecurityManager, which a Security Delegate must utilise,
 * must ensure that it is also checked.
 * 
 * If the SecurityManager installed doesn't implement CachingSecurityManager,
 * the DelegatePermission Guard's will be disabled.  This allows delegate's
 * to be included in code, the decision to utilise delegate functionality may
 * delayed until runtime or deployment.
 * 
 * The DelegatePermissionCollection returned by newPermissionCollection() is not
 * synchronized, this decision was made because PermissionCollection's are 
 * usually accessed from within a heterogenous PermissionCollection like 
 * Permissions that synchronizes anyway.  The decision made for the
 * PermissionCollection contract to be synchronized has been broken deliberately
 * in this case, existing PermissionCollection implementatations don't cleanly
 * protect their internal state with synchronization, since the Enumeration
 * returned by elements() will throw a ConcurrentModificationException if in a 
 * loop when Permission's are being added to a PermissionCollection.  In this
 * case external synchronization must be used.
 * 
 * PermissionCollection's are used mostly read only.
 * 
 * Serialization has been implemented so the implementation is not
 * tied to the serialized form, instead serialization proxy's are used.
 * 
 * @author Peter Firmstone
 */
public final class DelegatePermission extends Permission{
    private static final long serialVersionUID = 1L;
    /* Object Pool ensures that equals performs very well in collections for 
     * optimum AccessControlContext result caching and minimises memory 
     * consumption.
     */
    @SuppressWarnings("unchecked")
    private static final ConcurrentMap instances 
        = RC.concurrentMap( new ConcurrentSkipListMap( 
            RC.comparator( new PermissionComparator()))
            , Ref.WEAK, Ref.WEAK ); // Value weak too, because it references key.
        
    /**
     * Factory method to obtain a DelegatePermission, this is essential to 
     * overcome broken equals contract in some jvm Permission implementations
     * like SocketPermission and to allow caching.
     * 
     * @param p Permission to be represented.
     * @return DelegatePermission
     */
    public static Permission get(Permission p){
	Permission del = (Permission) instances.get(p);
	if ( del == null ){
	    del = new DelegatePermission(p);
            @SuppressWarnings("unchecked")
	    Permission existed = (Permission) instances.putIfAbsent(p, del);
	    if ( existed != null ){
		del = existed;
	    }
	}
	return del;
    }
    
    private final Permission permission;
//    private final transient int hashCode;
    
    private DelegatePermission(Permission p){
	super(p.getName());
	permission = p;
//	int hash = 5;
//	hash = 41 * hash + (this.permission != null ? this.permission.hashCode() : 0);
//	hashCode = hash;
    }
    
    public void checkGuard(Object object) throws SecurityException {
	SecurityManager sm = System.getSecurityManager();
	if (sm instanceof CachingSecurityManager) sm.checkPermission(this);
    }

    @Override
    // This is implemented but never called.
    public boolean implies(Permission permission) {
	if (permission == null) return false;
	if (!(permission instanceof DelegatePermission)) return false;
	if ( permission.getClass() != this.getClass()) return false;
	return this.permission.implies(((DelegatePermission) permission).getPermission());
    }
    
    public Permission getPermission(){
	return permission;
    }

    // Don't override equals so all Delegates can be used in Collections
    // including those containing SocketPermission.
    @Override
    public boolean equals(Object obj) {
        return obj == this;
//	if (obj == this) return true;
//	if (obj == null) return false;
//	if ( obj.hashCode() != hashCode ) return false;
//	if (!(obj instanceof DelegatePermission)) return false;
//	if ( obj.getClass() != this.getClass() ) return false;
//	return permission.equals(((DelegatePermission) permission).getPermission());
    }

    @Override
    public int hashCode() {
//	return hashCode;
        // Not in constructor so we don't let this escape.
        return System.identityHashCode(this);
    }

    @Override
    public String getActions() {
	return permission.getActions();
    }
    
    @Override
    public PermissionCollection newPermissionCollection() {
	return new DelegatePermissionCollection();
    }
    
    /* Serialization Proxy */
    private static class SerializationProxy implements Serializable {
	private static final long serialVersionUID = 1L;
	private Permission perm;
	
	SerializationProxy(Permission p){
	    perm = p;
	}
        
        private void writeObject(ObjectOutputStream out) throws IOException{
            out.defaultWriteObject();
        }
        
        private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException{
            in.defaultReadObject();
        }
        
        private Object readResolve() {
            // perm is the field from the Serialization proxy.
            return get(perm);
        }
    }
    
    /* Serialization */
    
    private Object writeReplace() {
	return new SerializationProxy(permission);
    }
    
    private void readObject(ObjectInputStream in) throws InvalidObjectException{
	throw new InvalidObjectException("Proxy required");
    }
    
    /* PermissionCollection */
    
    private static class DelegatePermissionCollection extends PermissionCollection {
	private static final long serialVersionUID = 1L;
	private final transient PermissionCollection candidates;
	private final Set<Permission> delegates;
	
	DelegatePermissionCollection(){
	    candidates = new Permissions();
	    delegates = new HashSet<Permission>(32);
	}

	@Override
	public void add(Permission permission) {
	    if (! (permission instanceof DelegatePermission))
	    throw new IllegalArgumentException("invalid permission: "+ permission);
	if (isReadOnly())
	    throw new SecurityException("attempt to add a Permission to a " +
		    "readonly PermissionCollection");
	    delegates.add(permission);
	    candidates.add(((DelegatePermission) permission).getPermission());
	}

	@Override
	public boolean implies(Permission permission) {
	    if ( !(permission instanceof DelegatePermission)) return false;
	    if ( permission == null ) return false;
	    if (delegates.contains(permission)) return true;
	    return candidates.implies(
		    ((DelegatePermission )permission).getPermission());
	}

	@Override
	public Enumeration<Permission> elements() {
	    return Collections.enumeration(delegates);
	}
	
	/* Serialization Proxy */
	 private static class CollectionSerializationProxy implements Serializable {
	    private static final long serialVersionUID = 1L;
	    private Permission[] perms;

	    CollectionSerializationProxy(Set<Permission> p){
		perms = p.toArray(new Permission[p.size()]);
	    }
            
            private Object readResolve() {
                // perms is the field from the Serialization proxy.
                DelegatePermissionCollection dpc = new DelegatePermissionCollection();
                int l = perms.length;
                for (int i = 0; i < l; i++ ){
                    dpc.add(perms[i]);
                }
                return dpc;
            }

	}

	/* Serialization */

	private Object writeReplace() {
	    return new CollectionSerializationProxy(delegates);
	}

	private void readObject(ObjectInputStream in) throws InvalidObjectException{
	    throw new InvalidObjectException("Proxy required");
	}

	
	
    }

}
