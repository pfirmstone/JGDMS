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

package org.apache.river.api.security;

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
import au.net.zeus.collection.RC;
import au.net.zeus.collection.Ref;
import au.net.zeus.collection.Referrer;
import java.io.StreamTokenizer;
import java.io.StringReader;
import java.util.ArrayList;
import org.apache.river.api.security.DefaultPolicyScanner.PermissionEntry;

/**
 * A DelegatePermission represents any other Permission, called a candidate
 * Permission.  A user granted a DelegatePermission does not have the privilege
 * of the candidate Permission, although a user with a candidate Permission
 * has the privilege of the DelegatePermission that represents the candidate, 
 * while the @ref DelegateSecurityManager is in force.
 * 
 * A DelegatePermission requires a security delegate to be of any
 * use or value.  A security delegates ProtectionDomain is granted the 
 * candidate permission, the security delegate allows any user granted the
 * DelegatePermission to utilise the functions that the candidate Permission
 * guards, when the user no longer has the DelegatePermission, the security
 * delegate no longer allows the user to access the functions guarded by the
 * candidate permission.  A security delegate has the responsibility to
 * prevent security sensitive objects guarded by the candidate permission from
 * escaping.  In order to do so, a security delegate utilises Li Gong's 
 * method guard pattern.
 *
 * Security Delegates enable sensitive objects to be used by code that isn't
 * fully trusted you may want to monitor, such as a 
 * file write that is limited by the number of bytes written, or a Permission
 * to write a file, that we might decide to retract or revoke if a user
 * does something we don't like, such as exceed a pre set limit or behave
 * in a manner we would like to avoid, such as hogging network bandwidth.
 * 
 * If the SecurityManager installed doesn't implement DelegateSecurityManager,
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
 * @since 3.0.0
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
            , Ref.WEAK, Ref.WEAK, 10000L, 10000L ); // Value weak too, because it references key.
        
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
    
    /**
     * Parses permission information from given GrantPermission name string.
     * Throws an IllegalArgumentException if the name string is misformatted.
     */
    private static PermissionEntry[] parsePermissions(String s) {
	try {
	    ArrayList l = new ArrayList();
	    StreamTokenizer st = createTokenizer(s);
	    char delim = '"';

	    if (st.nextToken() == StreamTokenizer.TT_WORD &&
		st.sval.equals("delim"))
	    {
		if (st.nextToken() == '=') {
		    if (st.nextToken() == StreamTokenizer.TT_WORD) {
			if (st.sval.length() > 1) {
			    throw new IllegalArgumentException(
				"excess delimiter characters");
			}
			delim = st.sval.charAt(0);
		    } else {
			delim = (char) st.ttype;
		    }
		    if (delim == ';') {
			throw new IllegalArgumentException(
			    "illegal delimiter ';'");
		    }
		} else {	// rewind
		    st = createTokenizer(s);
		}
		st.nextToken();
	    }
	    st.quoteChar(delim);

	    do {
		String type, name = null, actions = null;

		if (st.ttype != StreamTokenizer.TT_WORD) {
		    throw new IllegalArgumentException(
			"expected permission type");
		}
		type = st.sval;
		
		// REMIND: allow unquoted name/actions?
		st.nextToken();
		if (st.ttype == StreamTokenizer.TT_EOF || st.ttype == ';') {
		    l.add(new PermissionEntry(type, null, null, null));
		    continue;
		} else if (st.ttype == delim) {
		    name = st.sval;
		} else {
		    throw new IllegalArgumentException(
			"expected permission name or ';'");
		}
		
		st.nextToken();
		if (st.ttype == StreamTokenizer.TT_EOF || st.ttype == ';') {
		    l.add(new PermissionEntry(type, name, null, null));
		    continue;
		} else if (st.ttype != ',') {
		    throw new IllegalArgumentException("expected ',' or ';'");
		}

		if (st.nextToken() != delim) {
		    throw new IllegalArgumentException(
			"expected permission actions");
		}
		actions = st.sval;
		
		st.nextToken();
		if (st.ttype == StreamTokenizer.TT_EOF || st.ttype == ';') {
		    l.add(new PermissionEntry(type, name, actions, null));
		    continue;
		} else {
		    throw new IllegalArgumentException("expected ';'");
		}

	    } while (st.nextToken() != StreamTokenizer.TT_EOF);

	    return (PermissionEntry[]) l.toArray(new PermissionEntry[l.size()]);
	} catch (IOException ex) {
	    throw (Error) new InternalError().initCause(ex);
	}
    }

    /**
     * Returns tokenizer for parsing given string.  The tokenizer is configured
     * similarly to that used by sun.security.provider.PolicyParser, except
     * that comments are disabled and no quote character is set (yet).
     */
    private static StreamTokenizer createTokenizer(String s) {
	StreamTokenizer st = new StreamTokenizer(new StringReader(s));
	st.resetSyntax();
	st.wordChars('a', 'z');
	st.wordChars('A', 'Z');
	st.wordChars('.', '.');
	st.wordChars('0', '9');
	st.wordChars('_', '_');
	st.wordChars('$', '$');
	st.wordChars(128 + 32, 255);
	st.whitespaceChars(0, ' ');
	st.lowerCaseMode(false);
	st.ordinaryChar('/');
	st.slashSlashComments(false);
	st.slashStarComments(false);
	return st;
    }
    
    public void checkGuard(Object object) throws SecurityException {
	SecurityManager sm = System.getSecurityManager();
	if (sm instanceof DelegateSecurityManager) sm.checkPermission(this);
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
	return "";
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
