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
import java.security.BasicPermission;
import java.security.CodeSource;
import net.jini.loader.pref.PreferredClassProvider;
import net.jini.loader.pref.RequireDlPermProvider;

/**
 * Permission that must be granted to the {@link CodeSource} of a
 * downloaded class in order for the class to be defined using {@link
 * RMIClassLoader}.
 *
 * <p>A <code>DownloadPermission</code> contains a name (also referred
 * to as a "target name") but no action list; you either have the
 * named permission or you don't.  The only defined target name is
 * "permit", which allows a downloaded class with a
 * <code>CodeSource</code> that is granted the permission to be
 * defined by a class loader created by <code>RMIClassLoader</code>.
 *
 * <p>Selective granting of this permission can be used to restrict
 * the <code>CodeSource</code> values (codebase URLs and signers) from
 * which downloaded classes can be defined using
 * <code>RMIClassLoader</code>.
 *
 * <p>Note that this permission is only enforced if the current {@link
 * RMIClassLoader} provider supports it; not all
 * <code>RMIClassLoader</code> providers support this permission.  In
 * particular, the default provider (see {@link
 * RMIClassLoader#getDefaultProviderInstance
 * RMIClassLoader.getDefaultProviderInstance}) does <i>not</i> support
 * this permission, and so when the default provider is used,
 * downloaded classes do not need to be granted
 * <code>DownloadPermission</code> in order to be defined using
 * <code>RMIClassLoader</code>.  {@link PreferredClassProvider} itself
 * does not enforce this permission, but subclasses may configure it
 * to do so (see {@link RequireDlPermProvider}).
 *
 * @author Sun Microsystems, Inc.
 * @since 2.0
 **/
public final class DownloadPermission extends BasicPermission {

    private static final long serialVersionUID = 4658906595080241355L;

    /**
     * Creates a new <code>DownloadPermission</code> with the name
     * "permit".
     **/
    public DownloadPermission() {
	super("permit");
    }
}
