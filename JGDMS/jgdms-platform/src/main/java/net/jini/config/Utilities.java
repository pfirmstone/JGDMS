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

package net.jini.config;

import java.net.URL;
import java.net.URLClassLoader;

/**
 * Provides utility methods for use in this package.
 *
 * @author Sun Microsystems, Inc.
 * @since 2.0
 */
class Utilities {

    /** This class should not be instantiated. */
    private Utilities() { }

    /**
     * Class loader whose parent is the bootstrap class loader, and provides
     * no resources of its own, for finding resources in the bootstrap class
     * loader.
     */
    static final ClassLoader bootstrapResourceLoader =
	URLClassLoader.newInstance(new URL[0], null);

    /**
     * Returns the primitive type associated with a wrapper type or null if the
     * argument is not a wrapper type.
     *
     * @param type the wrapper type
     * @return the associated primitive type or null
     */
    static Class getPrimitiveType(Class type) {
	if (type == Boolean.class) {
	    return Boolean.TYPE;
	} else if (type == Byte.class) {
	    return Byte.TYPE;
	} else if (type == Character.class) {
	    return Character.TYPE;
	} else if (type == Short.class) {
	    return Short.TYPE;
	} else if (type == Integer.class) {
	    return Integer.TYPE;
	} else if (type == Long.class) {
	    return Long.TYPE;
	} else if (type == Float.class) {
	    return Float.TYPE;
	} else if (type == Double.class) {
	    return Double.TYPE;
	} else {
	    return null;
	}
    }

    /**
     * Returns a String describing the type.
     *
     * @param type the type
     * @return a String describing the type
     */
    static String typeString(Class type) {
	if (type == null) {
	    return "null";
	} else if (!type.isArray()) {
	    return type.getName();
	} else {
	    StringBuffer sb = new StringBuffer();
	    Class c;
	    int dimensions = 0;
	    for (c = type; c.isArray(); c = c.getComponentType()) {
		dimensions++;
	    }
	    sb.append(c.getName());
	    while (dimensions-- > 0) {
		sb.append("[]");
	    }
	    return sb.toString();
	}
    }
}
