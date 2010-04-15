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

import net.jini.loader.CodebaseAccessClassLoader;
import net.jini.loader.DownloadPermission;

/**
 * An <code>CodebaseAccessClassLoader</code> provider that supports preferred
 * classes and enforces {@link DownloadPermission}.
 *
 * <p>See the {@link CodebaseAccessClassLoader} specification for information
 * about how to install and configure the <code>CodebaseAccessClassLoader</code>
 * service provider.
 *
 * @author Sun Microsystems, Inc.
 * @since 2.0
 **/
public class RequireDlPermProvider extends PreferredClassProvider {

    /**
     * Creates a new <code>RequireDlPermProvider</code>.
     *
     * <p>This constructor is used by the {@link CodebaseAccessClassLoader}
     * service provider location mechanism when
     * <code>RequireDlPermProvider</code> is configured as the
     * <code>CodebaseAccessClassLoader</code> provider class.
     *
     * <p>This constructor passes <code>true</code> to the superclass
     * constructor that has a <code>boolean</code> parameter.
     **/
    public RequireDlPermProvider() {
	super(true);
    }
}
