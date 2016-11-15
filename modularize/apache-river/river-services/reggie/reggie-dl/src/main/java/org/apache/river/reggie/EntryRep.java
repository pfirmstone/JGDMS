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
package org.apache.river.reggie;

import org.apache.river.proxy.MarshalledWrapper;
import org.apache.river.reggie.ClassMapper.EntryField;
import java.io.IOException;
import java.io.InvalidObjectException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.ObjectStreamField;
import java.io.Serializable;
import java.lang.reflect.Field;
import java.rmi.MarshalException;
import java.rmi.RemoteException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import net.jini.core.entry.Entry;
import org.apache.river.api.io.AtomicSerial;
import org.apache.river.api.io.AtomicSerial.GetArg;
import org.apache.river.api.io.Valid;

/**
 * An EntryRep contains the fields of an Entry packaged up for
 * transmission between client-side proxies and the registrar server.
 * Instances are never visible to clients, they are private to the
 * communication between the proxies and the server.
 * <p>
 * This class only has a bare minimum of methods, to minimize
 * the amount of code downloaded into clients.
 *
 * @author Sun Microsystems, Inc.
 *
 */
@AtomicSerial
final class EntryRep implements Serializable, Cloneable {

    private static final long serialVersionUID = 2L;
    private static final ObjectStreamField[] serialPersistentFields = 
    { 
        /** @serialField The Class of the Entry converted to EntryClass. */
        new ObjectStreamField("eclass", EntryClass.class),
        /** @serialField The codebase of the entry class. */
        new ObjectStreamField("codebase", String.class),
	/** @serialField The public fields of the Entry, each converted as necessary to
	 * a MarshalledWrapper (or left as is if of known java.lang immutable
	 * type).  The fields are in super- to subclass order. */
        new ObjectStreamField("fields", Object[].class)
    };

    /**
     * The Class of the Entry converted to EntryClass.
     *
     * @serial
     */
    final EntryClass eclass;
    /**
     * The codebase of the entry class.
     * 
     * @serial
     */
    final String codebase;
    /**
     * The public fields of the Entry, each converted as necessary to
     * a MarshalledWrapper (or left as is if of known java.lang immutable
     * type).  The fields are in super- to subclass order.
     *
     * @serial
     */
    private final Object[] fields; // Individual fields were mutated by RegistrarImpl

    transient List flds; // Reggie now uses flds to access fields.
    
    
    private static boolean check(GetArg arg) throws IOException{
	EntryClass eclass = Valid.notNull(
	    arg.get("eclass", null, EntryClass.class), 
	    "eclass cannot be null"
	); 
	String codebase = arg.get("codebase", null, String.class); 
	Object [] fields = Valid.notNull(
	    arg.get("fields", null, Object[].class), 
	    "fields array cannot be null"
	); 
	return true;
    }
    
    EntryRep(GetArg arg) throws IOException{
	this(arg, check(arg));
    }
    
    private EntryRep(GetArg arg, boolean check) throws IOException{
	eclass = arg.get("eclass", null, EntryClass.class);
	codebase = arg.get("codebase", null, String.class);
	fields = Valid.copy(arg.get("fields", null, Object[].class));
	flds = Collections.synchronizedList(Arrays.asList(fields != null ? fields : new Object[0]));
    }
    
    private void writeObject(ObjectOutputStream out) throws IOException{
	synchronized (fields){
	    out.defaultWriteObject();
	}
    }
    
    /**
     * For clone & Reggie
     */
    EntryRep(EntryRep copy, boolean replaceEntryClass){
	eclass = replaceEntryClass ? copy.eclass.getReplacement(): copy.eclass;
	codebase = copy.codebase;
	synchronized (copy.fields){
	    fields = copy.fields.clone();
	}
	flds = Collections.synchronizedList(Arrays.asList(fields != null ? fields : new Object[0]));
    }
    
    /**
     * Converts an Entry to an EntryRep.  Any exception that results
     * is bundled up into a MarshalException.
     */
    private EntryRep(Entry entry, boolean needCodebase) throws RemoteException {
	EntryClassBase ecb = ClassMapper.toEntryClassBase(entry.getClass());
	eclass = ecb.eclass;
	codebase = needCodebase ? ecb.codebase : null;
	try {
	    EntryField[] efields = ClassMapper.getFields(entry.getClass());
	    fields = new Object[efields.length];
	    for (int i = efields.length; --i >= 0; ) {
		EntryField f = efields[i];
		Object val = f.field.get(entry);
		if (f.marshal && val != null)
		    val = new MarshalledWrapper(val);
		fields[i] = val;
	    }
	} catch (IOException e) {
	    throw new MarshalException("error marshalling arguments", e);
	} catch (IllegalAccessException e) {
	    throw new MarshalException("error marshalling arguments", e);
	}
	flds = Collections.synchronizedList(Arrays.asList(fields != null ? fields : new Object[0]));
    }

    /**
     * Convert back to an Entry.  If the Entry cannot be constructed,
     * null is returned.  If a field cannot be unmarshalled, it is set
     * to null.
     */
    public Entry get() {
	try {
	    Class clazz = eclass.toClass(codebase);
	    EntryField[] efields = ClassMapper.getFields(clazz);
	    Entry entry = (Entry)clazz.newInstance();
	    for (int i = efields.length; --i >= 0; ) {
		Object val = flds.get(i);
		EntryField f = efields[i];
		Field rf = f.field;
		try {
		    if (f.marshal && val != null)
			val = ((MarshalledWrapper) val).get();
		    rf.set(entry, val);
		} catch (Throwable e) {
		    if (e instanceof IllegalArgumentException) {
			// fix 4872566: work around empty exception message
			String msg = "unable to assign " +
			    ((val != null) ?
				"value of type " + val.getClass().getName() :
				"null") +
			    " to field " + rf.getDeclaringClass().getName() +
			    "." + rf.getName() + " of type " +
			    rf.getType().getName();
			e = new ClassCastException(msg).initCause(e);
		    }
		    RegistrarProxy.handleException(e);
		}
	    }
	    return entry;
	} catch (Throwable e) {
	    RegistrarProxy.handleException(e);
	}
	return null;
    }

    /**
     * We don't need this in the client or the server, but since we
     * redefine equals we provide a minimal hashCode that works.
     */
    public int hashCode() {
	return eclass.hashCode();
    }

    /**
     * EntryReps are equal if they have the same class and the fields
     * are pairwise equal.  This is really only needed in the server,
     * but it's very convenient to have here.
     */
    public boolean equals(Object obj) {
	if (obj instanceof EntryRep) {
	    EntryRep entry = (EntryRep)obj;
	    if (!eclass.equals(entry.eclass) ||
		flds.size() != entry.flds.size())
		return false;
	    for (int i = flds.size(); --i >= 0; ) {
		if ((flds.get(i) == null && entry.flds.get(i) != null) ||
		    (flds.get(i) != null && !flds.get(i).equals(entry.flds.get(i))))
		    return false;
	    }	    
	    return true;
	}
	return false;
    }

    /**
     * Test if an entry matches a template.  
     */
    boolean matchEntry(EntryRep tmpl) {
	if (!tmpl.eclass.isAssignableFrom(eclass) ||
	    tmpl.flds.size() > flds.size())
	    return false;
	for (int i = tmpl.flds.size(); --i >= 0; ) {
	    if (tmpl.flds.get(i) != null &&
		!tmpl.flds.get(i).equals(flds.get(i)))
		return false;
	}
	return true;
    }

    /**
     * Deep clone (which just means cloning the fields array too).
     * This is really only needed in the server, but it's very
     * convenient to have here.
     */
    @Override
    public Object clone() {
	return new EntryRep(this, false);
	}

    /**
     * Converts an array of Entry to an array of EntryRep.  If needCodebase
     * is false, then the codebase of every EntryRep will be null.
     */
    public static EntryRep[] toEntryRep(Entry[] entries, boolean needCodebase)
	throws RemoteException
    {
	EntryRep[] reps = null;
	if (entries != null) {
	    reps = new EntryRep[entries.length];
	    for (int i = entries.length; --i >= 0; ) {
		if (entries[i] != null) {
		    reps[i] = new EntryRep(entries[i], needCodebase);
		}
	    }
	}
	return reps;
    }

    /** Converts an array of EntryRep to an array of Entry. */
    public static Entry[] toEntry(EntryRep[] reps) {
	Entry[] entries = null;
	if (reps != null) {
	    entries = new Entry[reps.length];
	    for (int i = reps.length; --i >= 0; ) {
		entries[i] = reps[i].get();
	    }
	}
	return entries;
    }
    
     private void readObject(ObjectInputStream in)
	throws IOException, ClassNotFoundException
    {
	in.defaultReadObject();
	flds = Collections.synchronizedList(Arrays.asList(fields != null ? fields : new Object[0]));
}
}
