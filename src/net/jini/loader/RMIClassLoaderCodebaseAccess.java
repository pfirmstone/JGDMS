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
package net.jini.loader;

import java.io.IOException;
import java.net.URL;
import java.rmi.server.RMIClassLoader;
import java.security.AccessControlContext;
import java.security.AccessController;
import java.security.PrivilegedAction;
import net.jini.loader.pref.PreferredClassLoader;

/**
 *
 * @author Gregg Wonderly
 */
public class RMIClassLoaderCodebaseAccess implements CodebaseClassAccess {
	public Class loadClass( String codebase,
				  String name,
				  ClassLoader defaultLoader ) throws IOException, ClassNotFoundException {
		return RMIClassLoader.loadClass( codebase, name, defaultLoader );
	}

	public Class loadProxyClass(String codebase, String[] interfaceNames, ClassLoader defaultLoader) throws IOException, ClassNotFoundException {
		return RMIClassLoader.loadProxyClass( codebase, interfaceNames, defaultLoader );
	}

	public String getClassAnnotation( Class cls ) {
		return RMIClassLoader.getClassAnnotation( cls );
	}

	public ClassLoader getClassLoader(String codebase) throws IOException {
		return RMIClassLoader.getClassLoader( codebase );
	}

	public Class loadClass(String codebase, String name) throws IOException, ClassNotFoundException {
		return RMIClassLoader.loadClass( codebase, name );
	}

	public ClassLoader createClassLoader( final URL[] urls, final ClassLoader parent,
			final boolean requireDlPerm, final AccessControlContext ctx) {
		return AccessController.doPrivileged(new PrivilegedAction<ClassLoader>() {
				public ClassLoader run() {
					return new PreferredClassLoader(urls, parent, null, requireDlPerm);
				}
			}, ctx );
	}
	public ClassLoader getParentContextClassLoader() {
		/*
		 * The RMI supporting, default implementation simply uses the current thread's
		 * context class loader.
		 */
            return AccessController.doPrivileged(new PrivilegedAction<ClassLoader>() {
                public ClassLoader run() {
                    return Thread.currentThread().getContextClassLoader();
                }
            });
	}
	public ClassLoader getSystemContextClassLoader( ClassLoader defaultLoader ) {
            if( defaultLoader == null ) {
                return Thread.currentThread().getContextClassLoader();
            }
            return defaultLoader.getClass().getClassLoader( );
	}
}

