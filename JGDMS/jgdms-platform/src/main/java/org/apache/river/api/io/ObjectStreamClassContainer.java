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

import java.io.IOException;
import java.io.InvalidObjectException;
import java.io.ObjectInputStream;
import java.io.ObjectStreamClass;
import java.io.ObjectStreamException;
import java.io.ObjectStreamField;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.security.AccessControlContext;
import java.security.AccessControlException;
import java.security.AccessController;
import java.security.Permission;
import java.security.PrivilegedAction;
import java.security.PrivilegedActionException;
import java.security.PrivilegedExceptionAction;
import java.security.ProtectionDomain;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.NavigableSet;
import java.util.Objects;
import java.util.Set;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author peter
 */
class ObjectStreamClassContainer {
    static final ConcurrentMap<Class<?>, ObjectStreamClassContainer> lookup = new ConcurrentHashMap<Class<?>, ObjectStreamClassContainer>();
    private final ObjectStreamField[] empty;
    private ObjectStreamClass localClass;
    private ObjectStreamClass deserializedClass;
    private ObjectStreamClassInformation osci;
    private ObjectStreamClassContainer superClass;
    private int handle;
    private boolean isProxy;
    private Class<?> resolvedClass;
    private Method readObjectMethod;
    private Method readResolveMethod;
    private Method readObjectNoDataMethod;
    private Constructor constructor;
    private Object[] constructorParams;
    private AccessControlContext context;
    private ClassNotFoundException deserializedClassNotFound;

    ObjectStreamClassContainer() {
	empty = new ObjectStreamField[0];
    }

    ObjectStreamClassContainer(final ObjectStreamClass localClass, ObjectStreamClass deserializedClass, ObjectStreamClassInformation osci, int handle, boolean isProxy) {
	this();
	this.localClass = localClass;
	this.deserializedClass = deserializedClass;
	this.osci = osci;
	this.handle = handle;
	this.isProxy = isProxy;
    }

    protected void deSerializationPermitted(Permission perm) {
        SecurityManager sm = System.getSecurityManager();
        if (sm == null) return;
	if (context != null && perm != null) {
	    sm.checkPermission(perm, context);
	}
	if (!hasReadObjectNoData() && !hasReadObject()) {
	    //Ok if there's no data.
	    // Check all classes in heirarchy for absence of data (stateless object)
	    // Not worried about primitive fields, might as well be stateless.
	    ObjectStreamClassInformation osc = osci;
	    ObjectStreamClassContainer superClass = this.superClass;
	    CHECK_SAFE:
	    while (osc != null && osc.hasWriteObjectData == false && osc.hasBlockExternalData == false && osc.numObjFields == 0) {
		// Double check all fields are primitives.
		ObjectStreamField[] fields = osc.fields;
		if (fields != null) {
		    for (int i = 0, l = fields.length; i < l; i++) {
			if (!fields[i].isPrimitive()) {
			    break CHECK_SAFE;
			}
		    }
		}
		if (superClass != null) {
		    if (superClass.hasReadObjectNoData() || superClass.hasReadObject()) {
			break CHECK_SAFE;
		    }
		    osc = superClass.osci;
		    superClass = superClass.superClass;
		} else {
		    return; // If there's no data and no object fields therefore safe.
		}
	    }
	}
	if (perm == null) {
	    throw new AccessControlException("DeSerialization is not permitted: " + osci);
	}
	if (context == null) {
	    context = AccessController.doPrivileged(new PrivilegedAction<AccessControlContext>() {
		@Override
		public AccessControlContext run() {
		    List<ProtectionDomain> domains = new ArrayList<ProtectionDomain>();
		    Class clazz = null;
		    try {
			clazz = forClass();
		    } catch (ClassNotFoundException ex) {
			Logger.getLogger(ObjectStreamClassContainer.class.getName()).log(Level.SEVERE, null, ex);
		    }
		    while (clazz != null) {
			domains.add(clazz.getProtectionDomain());
			clazz = clazz.getSuperclass();
		    }
		    return new AccessControlContext(domains.toArray(new ProtectionDomain[domains.size()]));
		}
	    });
	    sm.checkPermission(perm, context);
	}
    }

    @Override
    public String toString() {
	String clas = "unresolved";
	if (osci != null && osci.getFullyQualifiedClassName() != null) {
	    clas = osci.getFullyQualifiedClassName();
	}
	if (localClass != null) {
	    clas = localClass.toString();
	}
	return super.toString() + " Class: " + clas;
    }

    /**
     *
     * @param clas
     * @return
     * @throws IOException
     */
    Object newParamInstance(Class<?> clas, boolean collectionsClass) throws IOException {
	// Special cases, all others must be null or we
	// can affect object equality with nasty unexpected bugs.
	if (clas == Integer.TYPE) {
	    return 0;
	}
	if (clas == Long.TYPE) {
	    return (long) 0;
	}
	if (clas == Boolean.TYPE) {
	    return false;
	}
	if (clas == Byte.TYPE) {
	    return (byte) 0;
	}
	if (clas == Character.TYPE) {
	    return (char) 0;
	}
	if (clas == Short.TYPE) {
	    return (short) 0;
	}
	if (clas == Double.TYPE) {
	    return (double) 0.0;
	}
	if (clas == Float.TYPE) {
	    return (float) 0.0;
	}
	if (collectionsClass) {
	    // Collections classes don't allow null parameters.
	    if (clas == Object[].class) {
		return new Object[0];
	    }
	    if (clas == Collection.class || clas == List.class) {
		return Collections.emptyList();
	    }
	    if (clas == Set.class || clas == SortedSet.class || clas == NavigableSet.class) {
		return Collections.emptyNavigableSet();
	    }
	    if (clas == Map.class || clas == SortedMap.class || clas == NavigableMap.class) {
		return Collections.emptyNavigableMap();
	    }
	}
	return null;
    }

    Object newInstance() throws IOException {
	if (constructor == null) {
	    boolean isCollections = false;
	    String classname = resolvedClass.getName();
	    if (classname.equals("java.util.Arrays$ArrayList")) {
		isCollections = true;
	    }
	    if (classname.startsWith("java.util.Collections")) {
		isCollections = true;
	    }
//	    System.out.println("Finding constructor for class " + resolvedClass);
	    Constructor[] ctors = getConstructors(resolvedClass);
	    for (int i = 0, l = ctors.length; i < l; i++) {
		int count;
		count = ctors[i].getParameterCount();
		Class[] ptypes = ctors[i].getParameterTypes();
		try {
		    Object[] prams = new Object[count];
		    for (int j = 0; j < count; j++) {
			// we could try harder, but this will do for now.
			prams[j] = newParamInstance(ptypes[j], isCollections);
		    }
		    //			ctors[i].setAccessible(true);
		    Object result = ctors[i].newInstance(prams);
		    //			System.out.println("Successfully created instance " + result);
		    // Now we know it works, record it.
		    constructor = ctors[i];
		    constructorParams = prams;
		    return result;
		} catch (InstantiationException ex) {
		    //			Logger.getLogger(AtomicMarshalInputStream.class.getName()).log(Level.SEVERE, resolvedClass.getCanonicalName(), ex);
		} catch (IllegalAccessException ex) {
		    //			Logger.getLogger(AtomicMarshalInputStream.class.getName()).log(Level.SEVERE, resolvedClass.getCanonicalName(), ex);
		} catch (IllegalArgumentException ex) {
		    //			Logger.getLogger(AtomicMarshalInputStream.class.getName()).log(Level.SEVERE, resolvedClass.getCanonicalName(), ex);
		} catch (InvocationTargetException ex) {
		    //			Logger.getLogger(AtomicMarshalInputStream.class.getName()).log(Level.SEVERE, resolvedClass.getCanonicalName(), ex);
		} catch (Exception ex) {
		    //			Logger.getLogger(AtomicMarshalInputStream.class.getName()).log(Level.SEVERE, resolvedClass.getCanonicalName(), ex);
		}
	    }
	}
	try {
	    if (constructor == null) {
		throw new InvalidObjectException("constructor is null: " + resolvedClass.getCanonicalName());
	    }
	    return constructor.newInstance(constructorParams);
	} catch (InstantiationException ex) {
	    throw new IOException("Unable to crate", ex);
	} catch (IllegalAccessException ex) {
	    throw new IOException(ex);
	} catch (IllegalArgumentException ex) {
	    throw new IOException(ex);
	} catch (InvocationTargetException ex) {
	    throw new IOException(ex);
	} catch (NullPointerException ex) {
	    //		System.out.println("Unable to find a suitable constructor for class " + resolvedClass);
	    InvalidObjectException e = new InvalidObjectException("Cannot create instance of " + resolvedClass);
	    e.initCause(ex);
	    throw e;
	}
    }

    Constructor[] getConstructors(final Class clas) {
	return AccessController.doPrivileged(new PrivilegedAction<Constructor[]>() {
	    @Override
	    public Constructor[] run() {
		try {
		    Constructor[] ctors = clas.getDeclaredConstructors();
		    for (int i = 0, l = ctors.length; i < l; i++) {
			ctors[i].setAccessible(true);
		    }
		    return ctors;
		} catch (SecurityException ex) {
		    Logger.getLogger(ObjectStreamClassInformation.class.getName()).log(Level.SEVERE, null, ex);
		}
		return null;
	    }
	});
    }

    boolean hasWriteObjectData() {
	if (osci != null) {
	    return osci.hasWriteObjectData;
	}
	return false;
    }

    boolean hasReadObject() {
	return readObjectMethod != null;
    }

    void invokeReadObject(Object o, ObjectInputStream in) throws IOException, ClassNotFoundException {
	Object[] params = {in};
	try {
	    readObjectMethod.invoke(o, params);
	} catch (IllegalAccessException ex) {
	    Logger.getLogger(ObjectStreamClassInformation.class.getName()).log(Level.SEVERE, null, ex);
	} catch (IllegalArgumentException ex) {
	    Logger.getLogger(ObjectStreamClassInformation.class.getName()).log(Level.SEVERE, null, ex);
	} catch (InvocationTargetException ex) {
	    Throwable t = ex.getTargetException();
	    if (t instanceof IOException) {
		throw (IOException) t;
	    }
	    if (t instanceof ClassNotFoundException) {
		throw (ClassNotFoundException) t;
	    }
	    Logger.getLogger(ObjectStreamClassInformation.class.getName()).log(Level.SEVERE, null, ex);
	}
    }

    Object invokeReadResolve(Object o) throws ObjectStreamException, IOException, ClassNotFoundException {
	if (o == null) return null;
	try {
//	    if (readResolveMethod == null) {
//		readResolveMethod = getReadResolveMethod(o.getClass());
//		if (readResolveMethod == null) {
//		    return o;
//		}
//	    }
	    return readResolveMethod.invoke(o, (Object[]) null);
	} catch (IllegalAccessException ex) {
	    Logger.getLogger(ObjectStreamClassInformation.class.getName()).log(Level.SEVERE, null, ex);
	} catch (IllegalArgumentException ex) {
	    Logger.getLogger(ObjectStreamClassInformation.class.getName()).log(Level.SEVERE, null, ex);
	} catch (InvocationTargetException ex) {
	    Throwable target = ex.getTargetException();
	    if (target instanceof ObjectStreamException) {
		throw (ObjectStreamException) target;
	    } else if (target instanceof IOException){
		throw (IOException) target;
	    } else if (target instanceof ClassNotFoundException){
		throw (ClassNotFoundException) target;
	    } else if (target instanceof Error) {
		throw (Error) target;
	    } else if (target instanceof RuntimeException){
		throw (RuntimeException) target;
	    } else {
		throw new IOException("Exception thrown while invoking readResolve", target);
	    }
	}
	return null;
    }

    void invokeReadObjectNoData(Object o) throws InvalidObjectException {
	try {
	    readObjectMethod.invoke(o, (Object[]) null);
	} catch (IllegalAccessException ex) {
	    Logger.getLogger(ObjectStreamClassInformation.class.getName()).log(Level.SEVERE, null, ex);
	} catch (IllegalArgumentException ex) {
	    Logger.getLogger(ObjectStreamClassInformation.class.getName()).log(Level.SEVERE, null, ex);
	} catch (InvocationTargetException ex) {
	    Throwable t = ex.getTargetException();
	    if (t instanceof InvalidObjectException) {
		throw (InvalidObjectException) t;
	    }
	    Logger.getLogger(ObjectStreamClassInformation.class.getName()).log(Level.SEVERE, null, ex);
	}
    }

    boolean hasMethodReadResolve() {
	return readResolveMethod != null;
    }

    Class<?> getReadResolveReturnType(){
	return readResolveMethod.getReturnType();
    }
    
    boolean hasReadObjectNoData() {
	return readObjectNoDataMethod != null;
    }

    Method getPrivateInstanceMethod(final Class<?> c, final String methodName, final Class<?>[] parameters, final Class<?> returnType) {
	return AccessController.doPrivileged(new PrivilegedAction<Method>() {
	    @Override
	    public Method run() {
		try {
		    Method m = c.getDeclaredMethod(methodName, parameters);
		    int modifiers = m.getModifiers();
		    if (Modifier.isPrivate(modifiers) && !Modifier.isStatic(modifiers) && returnType.equals(m.getReturnType())) {
			m.setAccessible(true);
			return m;
		    }
		} catch (NoSuchMethodException e) {
		    // TODO: Log
		} catch (SecurityException e) {
		} // TODO: Log
		return null;
	    }
	});
    }

    void setClass(final Class<?> c) throws ClassNotFoundException {
	if (c == null) throw new NullPointerException("class cannot be null");
	resolvedClass = c;
	readResolveMethod = getReadResolveMethod(c);
	Class[] params = {ObjectInputStream.class};
	readObjectMethod = getPrivateInstanceMethod(c, "readObject", params, Object.class);
	readObjectNoDataMethod = getPrivateInstanceMethod(c, "readObjectNoData", null, Void.TYPE);
	putInMap();
    }

    Method getReadResolveMethod(final Class<?> c) {
	return AccessController.doPrivileged(new PrivilegedAction<Method>() {
	    @Override
	    public Method run() {
		String name = "readResolve";
		Method m = null;
		int modifiers = 0;
		Class cm = c;
		int count = 0;
		do {
		    try {
			m = cm.getDeclaredMethod(name, (Class[]) null);
			modifiers = m.getModifiers();
			m.setAccessible(true);
			if (Modifier.isStatic(count) 
				// Allow covariant return values.
			    || !Object.class.isAssignableFrom(m.getReturnType())
				) {
			    cm = cm.getSuperclass();
			    count++;
			    continue;
			}
			break;
		    } catch (NoSuchMethodException ex) {
			cm = cm.getSuperclass();
			count++;
		    }
		} while (cm != null);
		if (m == null) {
		    return null;
		}
		boolean privt = Modifier.isPrivate(modifiers);
		boolean prted = Modifier.isProtected(modifiers);
		boolean pub = Modifier.isPublic(modifiers);
		if (count == 0) {
		    return m;
		} else {
		    if (pub || prted) {
			return m;
		    }
		    if (!privt && !prted && !pub) {
			// Check package access.
			if (Objects.equals(c.getPackage(),cm.getPackage()) && c.getClassLoader() == cm.getClassLoader()) {
			    return m;
			}
		    }
		}
		return null;
	    }
	});
    }

    @Override
    public int hashCode() {
	int hash = 3;
	hash = 11 * hash + handle;
	return hash;
    }

    @Override
    public boolean equals(Object o) {
	if (!(o instanceof ObjectStreamClassContainer)) {
	    return false;
	}
	ObjectStreamClassContainer that = (ObjectStreamClassContainer) o;
	return this.handle == that.handle;
    }

    void putInMap() throws ClassNotFoundException {
	lookup.putIfAbsent(forClass(), this);
    }

    void setLocalClassDescriptor(ObjectStreamClass descriptor) throws ClassNotFoundException {
	localClass = descriptor;
	if (isProxy) {
	    try {
		constructor = AccessController.doPrivileged(new PrivilegedExceptionAction<Constructor>() {
		    @Override
		    public Constructor run() throws ClassNotFoundException {
			try {
			    Class[] params = {InvocationHandler.class};
			    Constructor constructor = getLocalClass().forClass().getDeclaredConstructor(params);
			    constructor.setAccessible(true);
			    return constructor;
			} catch (NoSuchMethodException ex) {
			    Logger.getLogger(ObjectStreamClassInformation.class.getName()).log(Level.SEVERE, null, ex);
			} catch (SecurityException ex) {
			    Logger.getLogger(ObjectStreamClassInformation.class.getName()).log(Level.SEVERE, null, ex);
			}
			return null;
		    }
		});
	    } catch (PrivilegedActionException ex) {
		Exception e = ex.getException();
		if (e instanceof ClassNotFoundException) throw (ClassNotFoundException)e;
		if (e instanceof RuntimeException) throw (RuntimeException)e;
		throw new RuntimeException("Unexpected exception", ex);
	    }
	    constructorParams = new Object[1];
	    constructorParams[0] = new InvocationHandler() {
		@Override
		public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
		    Method m = Object.class.getMethod("toString", (Class[]) null);
		    if (m.equals(method)) {
			return "java.lang.reflect.Proxy";
		    }
		    throw new UnsupportedOperationException("Serializable field hasn't been set");
		}
	    };
	}
    }

    ObjectStreamClassContainer getSuperDesc() {
	return superClass;
    }

    Class<?> forClass() throws ClassNotFoundException {
	if (deserializedClassNotFound != null 
//		&& localClass == null 
//		&& resolvedClass == null 
//		&& deserializedClass == null
		) throw deserializedClassNotFound;
	if (resolvedClass != null) {
	    return resolvedClass;
	}
	if (deserializedClass != null) {
	    Class<?> clz = deserializedClass.forClass();
	    if (clz != null) {
		return clz;
	    }
	}
	return localClass != null ? localClass.forClass() : null;
    }

    String getName() throws ClassNotFoundException {
	if (osci != null) {
	    return osci.getFullyQualifiedClassName();
	}
	if (localClass != null) {
	    return localClass.forClass().getName();
	}
	return forClass().getName();
    }

    boolean wasSerializable() {
	if (osci != null) {
	    return osci.serializable;
	}
	return false;
    }

    boolean wasExternalizable() {
	if (osci != null) {
	    return osci.isExternalizable();
	}
	return false;
    }

    boolean hasBlockData() {
	if (osci != null) {
	    return osci.hasBlockExternalData;
	}
	return false;
    }

    boolean isProxy() {
	return isProxy;
    }

    long getSerialVersionUID() {
	if (osci != null) {
	    return osci.getSerialVer();
	}
	return -1L;
    }

    ObjectStreamField[] getFields() {
	if (deserializedClass != null) return deserializedClass.getFields();
	if (osci != null) return osci.fields;
	if (localClass != null) return localClass.getFields();
	return empty;
    }

    void setSuperclass(ObjectStreamClassContainer readClassDesc) {
	superClass = readClassDesc;
    }

    /**
     * @return the localClass
     */
    public ObjectStreamClass getLocalClass() throws ClassNotFoundException {
	if (deserializedClassNotFound != null && localClass == null) throw deserializedClassNotFound;
	return localClass;
    }

    /**
     * @return the deserializedClass
     */
    public ObjectStreamClass getDeserializedClass() throws ClassNotFoundException {
	if (deserializedClassNotFound != null && deserializedClass == null) throw deserializedClassNotFound;
	return deserializedClass;
    }

    /**
     * @param deserializedClass the deserializedClass to set
     */
    public void setDeserializedClass(ObjectStreamClass deserializedClass){
	this.deserializedClass = deserializedClass;
    }
    
    public void setDeserializedClassNotFound(ClassNotFoundException ex){
	this.deserializedClassNotFound = ex;
    }

    /**
     * @return the osci
     */
    public ObjectStreamClassInformation getOsci() {
	return osci;
    }

    /**
     * @return the constructor
     */
    public Constructor getConstructor() {
	return constructor;
    }

}
