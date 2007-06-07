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
package com.sun.jini.reggie;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.Comparator;
import java.util.WeakHashMap;
import java.lang.ref.SoftReference;
import java.rmi.MarshalException;

/**
 * Maps Class to ServiceType/Base, Class to EntryClass/Base, and Class to
 * Field[], with caching for efficiency.
 *
 * @author Sun Microsystems, Inc.
 *
 */
class ClassMapper {

    /** Weak Map from Class to SoftReference(ServiceTypeBase) */
    private static final WeakHashMap serviceMap = new WeakHashMap(23);
    /** Weak Map from Class to SoftReference(EntryClassBase) */
    private static final WeakHashMap entryMap = new WeakHashMap(17);
    /** Weak Map from Class to SoftReference(sorted Field[]) */
    private static final WeakHashMap fieldMap = new WeakHashMap(17);
    /** Comparator for sorting fields */
    private static final FieldComparator comparator = new FieldComparator();
    private static final ServiceType[] empty = {};
    private static final Class[] noArg = new Class[0];

    private ClassMapper() {}

    /** Returns a ServiceTypeBase descriptor for a class. */
    public static ServiceTypeBase toServiceTypeBase(Class cls) 
        throws MarshalException 
    {
	synchronized (serviceMap) {
	    return toServiceTypeBase(cls, true);
	}
    }

    /**
     * Returns a ServiceTypeBase descriptor for a class.  If needCodebase
     * is false, the returned descriptor's codebase may be null.
     */
    private static ServiceTypeBase toServiceTypeBase(Class cls,
						     boolean needCodebase)
	throws MarshalException 
    {
	if (cls == null)
	    return null;
	SoftReference cref = (SoftReference)serviceMap.get(cls);
	ServiceTypeBase stype = null;
	if (cref != null)
	    stype = (ServiceTypeBase)cref.get();
	if (stype == null) {
	    stype = new ServiceTypeBase(
			   new ServiceType(cls,
					   toServiceType(cls.getSuperclass()),
					   toServiceType(cls.getInterfaces())),
			   null);
	    serviceMap.put(cls, new SoftReference(stype));
	}
	if (needCodebase && stype.codebase == null)
	    stype.setCodebase(cls);
	return stype;
    }

    /** Returns a ServiceType descriptor for a class. */
    private static ServiceType toServiceType(Class cls) 
	throws MarshalException 
    {
	if (cls != null)
	    return toServiceTypeBase(cls, false).type;
	return null;
    }

    /** Converts an array of Class to an array of ServiceType. */
    public static ServiceType[] toServiceType(Class[] classes) 
	throws MarshalException 
    {
	if (classes == null)
	    return null;
	if (classes.length == 0)
	    return empty;
	ServiceType[] stypes = new ServiceType[classes.length];
	synchronized (serviceMap) {
	    for (int i = classes.length; --i >= 0; ) {
		stypes[i] = toServiceType(classes[i]);
	    }
	}
	return stypes;
    }

    /** Returns a EntryClassBase descriptor for a class. */
    public static EntryClassBase toEntryClassBase(Class cls) 
	throws MarshalException 
    {
	synchronized (entryMap) {
	    return toEntryClassBase(cls, true);
	}
    }

    /**
     * Returns a EntryClassBase descriptor for a class.  If base is false,
     * the returned descriptor's codebase may be null, and the class need
     * not be public and need not have a no-arg constructor.
     */
    private static EntryClassBase toEntryClassBase(Class cls, boolean base) 
        throws MarshalException 
    {
	if (cls == null)
	    return null;
	SoftReference cref = (SoftReference)entryMap.get(cls);
	EntryClassBase eclass = null;
	if (cref != null)
	    eclass = (EntryClassBase)cref.get();
	if (eclass == null) {
	    if (base) {
		if (!Modifier.isPublic(cls.getModifiers()))
		    throw new IllegalArgumentException("entry class " +
						       cls.getName() +
						       " is not public");
		try {
		    cls.getConstructor(noArg);
		} catch (NoSuchMethodException e) {
		    throw new IllegalArgumentException("entry class " +
						       cls.getName() +
			        " does not have a public no-arg constructor");
		}
	    }
	    eclass = new EntryClassBase(
			     new EntryClass(cls,
					    toEntryClass(cls.getSuperclass())),
			     null);
	    entryMap.put(cls, new SoftReference(eclass));
	}
	if (base && eclass.codebase == null)
	    eclass.setCodebase(cls);
	return eclass;
    }

    /** Returns an EntryClass descriptor for a class. */
    private static EntryClass toEntryClass(Class cls) throws MarshalException {
	if (cls != null)
	    return toEntryClassBase(cls, false).eclass;
	return null;
    }

    /** Field of an Entry class, with marshalling information */
    static class EntryField {
	/** Field for the field */
	public final Field field;
	/**
	 * True if instances of the field need to be converted
	 * to MarshalledWrapper.  False if the type of the field
	 * is String, Integer, Boolean, Character, Long, Float,
	 * Double, Byte, or Short.
	 */
	public final boolean marshal;

	/**
	 * Basic constructor.
	 */
	public EntryField(Field field) {
	    this.field = field;
	    Class c = field.getType();
	    marshal = !(c == String.class ||
			c == Integer.class ||
			c == Boolean.class ||
			c == Character.class ||
			c == Long.class ||
			c == Float.class ||
			c == Double.class ||
			c == Byte.class ||
			c == Short.class);
	}
    }

    /**
     * Returns public fields, in super to subclass order, sorted
     * alphabetically within a given class.
     */
    public static EntryField[] getFields(Class cls) {
	synchronized (fieldMap) {
	    SoftReference cref = (SoftReference)fieldMap.get(cls);
	    EntryField[] efields = null;
	    if (cref != null)
		efields = (EntryField[])cref.get();
	    if (efields == null) {
		Field[] fields = cls.getFields();
		Arrays.sort(fields, comparator);
		int len = 0;
		for (int i = 0; i < fields.length; i++) {
		    if ((fields[i].getModifiers() &
			 (Modifier.STATIC|Modifier.FINAL|Modifier.TRANSIENT))
			== 0)
		    {
			if (fields[i].getType().isPrimitive())
			    throw new IllegalArgumentException("entry class " +
							       cls.getName() +
						  " has a primitive field");
			fields[len++] = fields[i];
		    }
		}
		efields = new EntryField[len];
		while (--len >= 0) {
		    efields[len] = new EntryField(fields[len]);
		}
		fieldMap.put(cls, new SoftReference(efields));
	    }
	    return efields;
	}
    }

    /** Comparator for sorting fields. */
    private static class FieldComparator implements Comparator {
	public FieldComparator() {}

	/** Super before subclass, alphabetical within a given class */
	public int compare(Object o1, Object o2) {
	    Field f1 = (Field)o1;
	    Field f2 = (Field)o2;
	    if (f1 == f2)
		return 0;
	    if (f1.getDeclaringClass() == f2.getDeclaringClass())
		return f1.getName().compareTo(f2.getName());
	    if (f1.getDeclaringClass().isAssignableFrom(
						     f2.getDeclaringClass()))
		return -1;
	    return 1;
	}
    }
}
