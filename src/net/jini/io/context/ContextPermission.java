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

package net.jini.io.context;

import net.jini.security.AccessPermission;

/**
 * Represents permissions used to express the access control policy for
 * elements commonly found in the context collections available from
 * {@link net.jini.export.ServerContext} and
 * {@link net.jini.io.ObjectStreamContext}.
 * <p>
 * An instance contains a name (also referred to as a "target name") but no
 * actions list; you either have the named permission or you don't. The
 * convention is that the target name is the fully qualified name of the
 * (interface) method being invoked on the context element. Wildcard matches
 * are supported using the syntax specified by {@link AccessPermission}.
 *
 * @author Sun Microsystems, Inc.
 * @since 2.0
 */
public final class ContextPermission extends AccessPermission {
    private static final long serialVersionUID = 1396656176817498282L;

    /**
     * Creates an instance with the specified name.
     *
     * @param name the target name
     */
    public ContextPermission(String name) {
	super(name);
    }
}
