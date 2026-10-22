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
package net.jini.jeri.ssl;

import java.io.InputStream;
import java.security.ProtectionDomain;
import java.util.Set;

/**
 * A class loader that defines its own copy of some classes, from the bytes
 * that the parent loads them from, in a protection domain that the test
 * chooses, and delegates every other class to the parent.  The copy is a
 * different class from the parent's, as the copy of a class in another OSGi
 * bundle revision or web application is.
 */
final class IsolatedLoader extends ClassLoader {

    private final Set<String> own;
    private final ProtectionDomain domain;

    /**
     * @param own the names of the classes to define here; their nested
     *        classes are defined here too
     * @param domain the protection domain of the copies
     */
    IsolatedLoader(ClassLoader parent, Set<String> own, ProtectionDomain domain) {
        super(parent);
        this.own = own;
        this.domain = domain;
    }

    private boolean isOwn(String name) {
        int nested = name.indexOf('$');
        return own.contains(nested < 0 ? name : name.substring(0, nested));
    }

    /** Reads the class file, with this loader's own permissions. */
    @SuppressWarnings("removal")
    private byte[] bytesOf(String name) throws ClassNotFoundException {
        String resource = name.replace('.', '/') + ".class";
        try {
            return java.security.AccessController.doPrivileged(
                (java.security.PrivilegedExceptionAction<byte[]>) () -> {
                    try (InputStream in = getParent().getResourceAsStream(resource)) {
                        if (in == null) {
                            throw new ClassNotFoundException(name);
                        }
                        return in.readAllBytes();
                    }
                });
        } catch (java.security.PrivilegedActionException e) {
            Exception cause = e.getException();
            if (cause instanceof ClassNotFoundException) {
                throw (ClassNotFoundException) cause;
            }
            throw new ClassNotFoundException(name, cause);
        }
    }

    @Override
    protected Class<?> loadClass(String name, boolean resolve)
        throws ClassNotFoundException
    {
        synchronized (getClassLoadingLock(name)) {
            if (!isOwn(name)) {
                return super.loadClass(name, resolve);
            }
            Class<?> c = findLoadedClass(name);
            if (c == null) {
                byte[] bytes = bytesOf(name);
                c = defineClass(name, bytes, 0, bytes.length, domain);
            }
            if (resolve) {
                resolveClass(c);
            }
            return c;
        }
    }
}
