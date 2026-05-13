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
package org.apache.river.reggie.proxy;

import org.apache.river.proxy.MarshalledWrapper;
import org.apache.river.reggie.proxy.ClassMapper.EntryField;
import java.io.IOException;
import java.io.InvalidObjectException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.ObjectStreamField;
import java.io.Serializable;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.rmi.MarshalException;
import java.rmi.RemoteException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import net.jini.core.entry.Entry;
import net.jini.core.entry.EntryWireField;
import net.jini.core.entry.GetEntryArg;
import net.jini.core.entry.SerialEntry;
import org.apache.river.api.io.AtomicMarshalledInstance;
import org.apache.river.api.io.AtomicSerial;
import org.apache.river.api.io.AtomicSerial.GetArg;
import org.apache.river.api.io.AtomicSerial.PutArg;
import org.apache.river.api.io.AtomicSerial.SerialForm;
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
public final class EntryRep implements Serializable, Cloneable {

    private static final long serialVersionUID = 2L;
    private static final ObjectStreamField[] serialPersistentFields = 
        serialForm();
    
    public static SerialForm[] serialForm(){
        return new SerialForm[]{
            /** @serialField The Class of the Entry converted to EntryClass. */
            new SerialForm("eclass", EntryClass.class),
            /** @serialField The codebase of the entry class. */
            new SerialForm("codebase", String.class),
            /** @serialField The public fields of the Entry, each converted as necessary to
             * a MarshalledWrapper (or left as is if of known java.lang immutable
             * type).  The fields are in super- to subclass order. */
            new SerialForm("fields", Object[].class)
        };
    }
    
    public static void serialize(PutArg arg, EntryRep er) throws IOException{
        arg.put("eclass", er.eclass);
        arg.put("codebase", er.codebase);
        synchronized (er.fields){
            arg.put("fields", er.fields.clone());
        }
        arg.writeArgs();
    }

    /**
     * The Class of the Entry converted to EntryClass.
     *
     * @serial
     */
    public final EntryClass eclass;
    /**
     * The codebase of the entry class.
     * 
     * @serial
     */
    public final String codebase;
    /**
     * The public fields of the Entry, each converted as necessary to
     * a MarshalledWrapper (or left as is if of known java.lang immutable
     * type).  The fields are in super- to subclass order.
     *
     * @serial
     */
    private final Object[] fields; // Individual fields were mutated by RegistrarImpl

    transient List flds; // Reggie now uses flds to access fields.
   
    public List fields(){
	return flds;
    }
    
    private static boolean check(GetArg arg) 
	    throws IOException, ClassNotFoundException{
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
    
    EntryRep(GetArg arg) throws IOException, ClassNotFoundException{
	this(arg, check(arg));
    }
    
    private EntryRep(GetArg arg, boolean check) 
	    throws IOException, ClassNotFoundException{
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
     * For clone and Reggie
     */
    public EntryRep(EntryRep copy, boolean replaceEntryClass){
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
	    fields = fields(entry);
	} catch (IOException e) {
	    throw new MarshalException("error marshalling arguments", e);
	} catch (IllegalAccessException e) {
	    throw new MarshalException("error marshalling arguments", e);
	}
	flds = Collections.synchronizedList(Arrays.asList(fields != null ? fields : new Object[0]));
    }
    
    private static Object[] fields(Entry entry) 
            throws IOException, IllegalArgumentException, IllegalAccessException {
        Class<?> cls = entry.getClass();
        if (cls.isAnnotationPresent(SerialEntry.class)) {
            return fieldsViaSerialEntry(cls, entry);
        }
        EntryField[] efields = ClassMapper.getFields(cls);
        Object[] fields = new Object[efields.length];
        for (int i = efields.length; --i >= 0; ) {
            EntryField f = efields[i];
            Object val = f.field.get(entry);
            if (f.marshal && val != null)
                val = new MarshalledWrapper(new AtomicMarshalledInstance(val));
            fields[i] = val;
        }
        return fields;
    }

    /**
     * Serialises an {@code @SerialEntry} instance by invoking its static
     * {@code serialize(PutEntryArg, T)} method and collecting the results.
     */
    private static Object[] fieldsViaSerialEntry(Class<?> cls, Entry entry)
            throws IOException {
        try {
            Method entryFormMethod = cls.getMethod("entryForm");
            EntryWireField[] wireFields = (EntryWireField[]) entryFormMethod.invoke(null);
            PutEntryArgImpl putArg = new PutEntryArgImpl(wireFields);
            Method serializeMethod = cls.getMethod("serialize",
                    net.jini.core.entry.PutEntryArg.class, cls);
            serializeMethod.invoke(null, putArg, entry);
            Object[] rawValues = putArg.getResult();
            // Wrap values that need marshalling (not simple immutable types)
            Object[] fields = new Object[wireFields.length];
            for (int i = 0; i < wireFields.length; i++) {
                Object val = rawValues[i];
                if (val != null && needsMarshal(wireFields[i].getType())) {
                    val = new MarshalledWrapper(new AtomicMarshalledInstance(val));
                }
                fields[i] = val;
            }
            return fields;
        } catch (NoSuchMethodException e) {
            throw new MarshalException(
                cls.getName() + " is @SerialEntry but missing required static method", e);
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause();
            if (cause instanceof IOException) throw (IOException) cause;
            throw new MarshalException(
                "Exception during " + cls.getName() + ".serialize()", e);
        } catch (IllegalAccessException e) {
            throw new MarshalException(
                "Cannot access serialize() on " + cls.getName(), e);
        }
    }

    /** Types whose instances are known-immutable and do not need MarshalledWrapper. */
    private static final java.util.Set<Class<?>> IMMUTABLE_TYPES;
    static {
        java.util.Set<Class<?>> s = new java.util.HashSet<>();
        s.add(String.class);
        s.add(Integer.class);
        s.add(Boolean.class);
        s.add(Character.class);
        s.add(Long.class);
        s.add(Float.class);
        s.add(Double.class);
        s.add(Byte.class);
        s.add(Short.class);
        IMMUTABLE_TYPES = java.util.Collections.unmodifiableSet(s);
    }

    /** Returns {@code true} if values of the given type need MarshalledWrapper wrapping. */
    private static boolean needsMarshal(Class<?> type) {
        return !IMMUTABLE_TYPES.contains(type);
    }

    /**
     * Convert back to an Entry.  If the Entry cannot be constructed,
     * null is returned.  If a field cannot be unmarshalled, it is set
     * to null.
     * @return The Entry this EntryRep represents.
     */
    public Entry get() {
	try {
	    Class clazz = eclass.toClass(codebase);
	    if (clazz.isAnnotationPresent(SerialEntry.class)) {
		return getViaSerialEntry(clazz);
	    }
	    EntryField[] efields = ClassMapper.getFields(clazz);
	    Entry entry = (Entry)clazz.getDeclaredConstructor().newInstance();
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
     * Deserialises a {@code @SerialEntry} entry via its {@code (GetEntryArg)}
     * constructor, first unmarshalling any {@link MarshalledWrapper} values
     * back to their original objects.
     */
    private Entry getViaSerialEntry(Class clazz) {
	try {
	    Method entryFormMethod = clazz.getMethod("entryForm");
	    EntryWireField[] wireFields = (EntryWireField[]) entryFormMethod.invoke(null);
	    // Unmarshal wrapped values back to their original types
	    Object[] rawValues = new Object[wireFields.length];
	    for (int i = 0; i < wireFields.length && i < flds.size(); i++) {
		Object val = flds.get(i);
		if (val instanceof MarshalledWrapper) {
		    try {
			val = ((MarshalledWrapper) val).get();
		    } catch (Throwable e) {
			RegistrarProxy.handleException(e);
			val = null;
		    }
		}
		rawValues[i] = val;
	    }
	    GetEntryArg getArg = new GetEntryArgImpl(wireFields, rawValues);
	    Constructor ctor = clazz.getConstructor(GetEntryArg.class);
	    return (Entry) ctor.newInstance(getArg);
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
    public boolean matchEntry(EntryRep tmpl) {
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
