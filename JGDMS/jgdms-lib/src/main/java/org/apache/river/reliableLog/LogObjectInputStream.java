/*
 * Copyright 2018 peter.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.river.reliableLog;

import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectStreamClass;
import java.lang.ref.WeakReference;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Used for control of Class visibility during deserialization for modular environments.
 * 
 * @author peter
 */
class LogObjectInputStream extends ObjectInputStream {
    
    private static final Map<String,Class> specialClasses = new HashMap<String,Class>(9);
    static {
	specialClasses.put("boolean", boolean.class);
	specialClasses.put("byte", byte.class);
	specialClasses.put("char", char.class);
	specialClasses.put("short", short.class);
	specialClasses.put("int", int.class);
	specialClasses.put("long", long.class);
	specialClasses.put("float", float.class);
	specialClasses.put("double", double.class);
	specialClasses.put("void", void.class);
    }
    
    private final WeakReference<ClassLoader> defaultLoader;
    
    LogObjectInputStream(InputStream in, ClassLoader defaultLoader) throws IOException {
	super(in);
	this.defaultLoader = new WeakReference<ClassLoader>(defaultLoader);
    }
    
    @Override
    protected Class<?> resolveClass(ObjectStreamClass desc)
        throws IOException, ClassNotFoundException
    {
	String name = desc.getName();
	Class c = (Class) specialClasses.get(name);
	if (c != null) return c;
	ClassLoader loader = defaultLoader.get();
	if (loader == null) return super.resolveClass(desc);
	return Class.forName(name, false, loader);
    }
    
    @Override
    protected Class<?> resolveProxyClass(String[] interfaces)
        throws IOException, ClassNotFoundException
    {
	ClassLoader loader = defaultLoader.get();
	if (loader == null) return super.resolveProxyClass(interfaces);
	int length = interfaces.length;
	List<Class> interfaceClasses = new ArrayList<Class>(length);
	for (int i = 0; i < length; i++){
	    Class c;
	    try {
		c = Class.forName(interfaces[i], false, loader);
	    } catch (ClassNotFoundException e){
		// Ignore for now, better to have a proxy with less
		// functionality, than be unable to restore altogether.
		// Need to consider logging.
		continue;
	    }
	    interfaceClasses.add(c);
	}
	return Proxy.getProxyClass(
		loader, 
		interfaceClasses.toArray(new Class[interfaceClasses.size()])
	);
    }
}
