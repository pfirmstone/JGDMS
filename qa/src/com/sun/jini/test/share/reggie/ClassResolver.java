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
package com.sun.jini.test.share.reggie;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.lang.ref.WeakReference;
import java.lang.ref.ReferenceQueue;

/**
 * Maps EntryClass and ServiceType instances to canonical instances, so
 * that equals and isAssignableFrom methods work correctly.  Two instances
 * are considered equivalent if they have the same class name.
 *
 * 
 *
 */
class ClassResolver {

    /** True if recovering log, false otherwise */
    private boolean inRecovery = false;
    /** Map from String (classname) to WeakRep(ServiceType) */
    private final Map serviceMap = new HashMap();
    /** Reference queue for WeakReps of serviceMap */
    private final ReferenceQueue serviceRefQueue = new ReferenceQueue();
    /** Map from String (classname) to WeakRep(EntryClass) */
    private final Map entryMap = new HashMap();
    /** Reference queue for WeakReps of entryMap */
    private final ReferenceQueue entryRefQueue = new ReferenceQueue();

    /** Simple constructor */
    public ClassResolver() {}

    /** Set whether or not we are recovering log */
    public void setInRecovery(boolean inRecovery) {
	this.inRecovery = inRecovery;
    }

    /** Returns canonical instance of a ServiceType. */
    public ServiceType resolve(ServiceType stype) {
	if (stype == null)
	    return null;
	synchronized (serviceMap) {
	    /* remove any queued refs */
	    while (true) {
		WeakRep ref = (WeakRep)serviceRefQueue.poll();
		if (ref == null)
		    break;
		serviceMap.remove(ref.name);
	    }
	    WeakRep ref = (WeakRep)serviceMap.get(stype.getName());
	    if (ref != null) {
		ServiceType rstype = (ServiceType)ref.get();
		// XXX should worry about type evolution
		if (rstype != null)
		    return rstype;
	    }
	    resolve(stype.getInterfaces());
	    stype.canonical(resolve(stype.getSuperclass()));
	    serviceMap.put(stype.getName(), new WeakRep(stype.getName(),
							stype,
							serviceRefQueue));
	    return stype;
	}
    }

    /**
     * Replaces the elements of the ServiceType array with their
     * canonical instances.
     */
    public void resolve(ServiceType[] stypes) {
	if (stypes != null) {
	    for (int i = stypes.length; --i >= 0; ) {
		stypes[i] = resolve(stypes[i]);
	    }
	}
    }

    /** Returns canonical instance of an EntryClass. */
    public EntryClass resolve(EntryClass eclass) {
	if (eclass == null)
	    return null;
	synchronized (entryMap) {
	    /* remove any queued refs */
	    while (true) {
		WeakRep ref = (WeakRep)entryRefQueue.poll();
		if (ref == null)
		    break;
		entryMap.remove(ref.name);
	    }
	    WeakRep ref = (WeakRep)entryMap.get(eclass.getName());
	    if (ref != null) {
		EntryClass reclass = (EntryClass)ref.get();
		if (reclass != null) {
		    if (reclass.getNumFields() == eclass.getNumFields() &&
			(reclass.getFieldsHash() == eclass.getFieldsHash() ||
			 reclass.getFieldsHash() == 0 ||
			 eclass.getFieldsHash() == 0))
		    {
			if (reclass.getFieldsHash() == 0)
			    reclass.setFieldsHash(eclass.getFieldsHash());
			return reclass;
		    } else if (anyInUse(reclass)) {
			throw new IncompatibleClassChangeError(
					eclass.getName() +
					": different public fields");
		    }
		}
	    }
	    eclass.canonical(resolve(eclass.getSuperclass()));
	    entryMap.put(eclass.getName(), new WeakRep(eclass.getName(),
						       eclass,
						       entryRefQueue));
	    return eclass;
	}
    }

    /**
     * Returns true if the log is not being recovered and there are any
     * instances or templates of the given entry class or any instances or
     * templates of its known subclasses. If the log is being recovered,
     * or there are no instances or templates, purge the entry class and
     * any subclasses.
     */
    private boolean anyInUse(EntryClass eclass) {
	if (!inRecovery && (eclass.getNumInstances() > 0 ||
			    eclass.getNumTemplates() > 0))
	    return true;
	List purges = null;
	for (Iterator iter = entryMap.values().iterator(); iter.hasNext(); ) {
	    WeakRep ref = (WeakRep) iter.next();
	    if (ref != null) {
		EntryClass reclass = (EntryClass)ref.get();
		if (reclass != null && eclass.isAssignableFrom(reclass)) {
		    if (!inRecovery && (reclass.getNumInstances() > 0 ||
					reclass.getNumTemplates() > 0))
			return true;
		    if (purges == null)
			purges = new ArrayList(1);
		    purges.add(reclass.getName());
		}
	    }
	}
	if (purges != null)
	    entryMap.keySet().removeAll(purges);
	return false;
    }

    /**
     * Replace all embedded descriptors in an Item with their
     * canonical instances.
     */
    public void resolve(Item item) {
	item.serviceType = resolve(item.serviceType);
	resolve(item.attributeSets);
    }

    /**
     * Replace all embedded descriptors in a Template with their
     * canonical instances.
     */
    public void resolve(Template tmpl) {
	resolve(tmpl.serviceTypes);
	resolve(tmpl.attributeSetTemplates);
    }

    /**
     * Replace all embedded descriptors in an EntryRep array with their
     * canonical instances.
     */
    public void resolve(EntryRep[] attrSets) {
	if (attrSets != null) {
	    for (int i = attrSets.length; --i >= 0; ) {
		attrSets[i].eclass = resolve(attrSets[i].eclass);
	    }
	}
    }

    /**
     * Replace all embedded descriptors in an EntryRep array with their
     * canonical instances, allowing null EntryReps.
     */
    public void resolveWithNulls(EntryRep[] attrSets) {
	if (attrSets != null) {
	    for (int i = attrSets.length; --i >= 0; ) {
		if (attrSets[i] != null)
		    attrSets[i].eclass = resolve(attrSets[i].eclass);
	    }
	}
    }

    /**
     * A weak ref extended with the key for the map.
     */
    private static class WeakRep extends WeakReference {
	/** The key for the map */
	public String name;

	/** Always create a queued ref. */
	public WeakRep(String name, Object referent, ReferenceQueue refQueue)
	{
	    super(referent, refQueue);
	    this.name = name;
	}
    }
}
