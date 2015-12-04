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

import java.rmi.server.RMIClassLoader;
import net.jini.loader.pref.PreferredClassProvider;

/**
 * Provides a class annotation string for classes defined by a class
 * loader that is an instance of this interface.
 *
 * <p>This interface allows a {@link ClassLoader} subclass to
 * customize the behavior of {@link RMIClassLoader#getClassAnnotation
 * RMIClassLoader.getClassAnnotation} (and thus RMI marshalling
 * semantics) for classes defined by its instances.
 *
 * <p>Note that this interface is only effective if the current {@link
 * RMIClassLoader} provider supports it; not all
 * <code>RMIClassLoader</code> providers support this interface.  In
 * particular, the default provider (see {@link
 * RMIClassLoader#getDefaultProviderInstance
 * RMIClassLoader.getDefaultProviderInstance}) does <i>not</i> support
 * this interface, and so when the default provider is used, this
 * interface will be ignored by
 * <code>RMIClassLoader.getClassAnnotation</code>.  {@link
 * PreferredClassProvider} and its subclasses do support this
 * interface.
 *
 * @author Sun Microsystems, Inc.
 * @since 2.0
 **/
public interface ClassAnnotation {

    /**
     * Returns the class annotation string for classes defined by this
     * class loader.  The returned value may be <code>null</code>.
     *
     * @return the class annotation string for classes defined by this
     * loader, or <code>null</code>
     **/
    String getClassAnnotation();
}
