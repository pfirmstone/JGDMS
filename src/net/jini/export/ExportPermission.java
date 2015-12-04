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

package net.jini.export;

import java.security.BasicPermission;

/**
 * Defines runtime permissions for <code>Exporter</code> implementations.
 * An instance contains a name (also referred to as a "target
 * name") but no actions list; you either have the named permission or you
 * don't. An asterisk may appear at the end of the name, following a ".",
 * or by itself, to signify a wildcard match.
 *
 * <p>
 * The possible target names are:
 * <table summary "Describes permission target names and what they allow"
 * border=1 cellpadding=5 width="100%">
 * <tr>
 * <th>Permission Target Name</th>
 * <th>What the Permission Allows</th>
 * <th>Risks of Allowing this Permission</th>
 * </tr>
 * <tr>
 * <td>exportRemoteInterface.<i>interfaceName</i></td>
 * <td>obtaining {@link java.lang.reflect.Method} objects that have their
 * accessibility flags set to suppress language access checks, for methods of
 * the indicated non-public remote interface <i>interfaceName</i> (given as a
 * fully qualified class name)</td>
 * <td>The caller can invoke methods of a non-public remote interface.</td>
 * </tr>
 * </table>
 *
 * @author Sun Microsystems, Inc.
 * 
 * @since 2.0
 */
public final class ExportPermission extends BasicPermission {
    private static final long serialVersionUID = 9221509074492981772L;

    /**
     * Creates an instance with the specified name.
     *
     * @param name the target name
     */
    public ExportPermission(String name) {
	super(name);
    }

    /**
     * Creates an instance with the specified name. The actions parameter is
     * ignored.
     *
     * @param name the target name
     * @param actions ignored
     */
    public ExportPermission(String name, String actions) {
	super(name, actions);
    }
}
