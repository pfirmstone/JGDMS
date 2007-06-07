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

package net.jini.loader.pref;

import java.net.URL;

/**
 * Class loader to wrap the preferred class loader so that loadClass
 * checks package access. Similar to the class loaders returned by
 * URLClassLoader.newInstance.
 *
 * @author Sun Microsystems, Inc.
 **/
final class PreferredFactoryClassLoader extends PreferredClassLoader {

    PreferredFactoryClassLoader(URL[] urls, ClassLoader parent,
				String exportAnnotation,
				boolean requireDownloadPerm)
    {
	super(urls, parent, exportAnnotation, requireDownloadPerm);
    }

    public final synchronized Class loadClass(String name, boolean resolve)
	throws ClassNotFoundException
    {
	SecurityManager sm = System.getSecurityManager();
	if (sm != null) {
            String cname = name.replace('/', '.');
            if (cname.startsWith("[")) {
                int b = cname.lastIndexOf('[') + 2;
                if (b > 1 && b < cname.length()) {
                    cname = cname.substring(b);
                }
            }
	    int i = cname.lastIndexOf('.');
	    if (i != -1) {
		sm.checkPackageAccess(cname.substring(0, i));
	    }
	}
	return super.loadClass(name, resolve);
    }
}
