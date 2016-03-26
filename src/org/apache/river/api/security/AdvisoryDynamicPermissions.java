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
package org.apache.river.api.security;

import java.security.Permission;
import net.jini.security.ProxyPreparer;
import org.apache.river.api.common.Beta;

/**
 * Jar files that include a META-INF/PERMISSIONS.LIST file,
 * allow a {@link ProxyPreparer} to grant these permissions dynamically.
 * <p>
 * The PERMISSIONS.LIST file must be UTF-8 encoded, the format of the file 
 * is line based, length is not limited, however lines must be readable by 
 * {@link java.io.BufferedReader#readLine() }.
 * <p>
 * <h2>Syntax:</h2>
 * Comments begin with # or //.
 * <p>
 * A permission is represented by three whitespace delimited strings:
 * <ul>
 * <li>type - The fully qualified class name of the permission. The class must be a subclass
 *        of <code>java.security.Permission</code> and must define a
 *        2-argument constructor that takes a <i>name</i> string and an
 *        <i>actions</i> string.
 * 
 * <li>name - The permission name that will be passed as the first argument
 *        to the constructor of the <code>Permission</code> class identified
 *        by <code>type</code>.
 * 
 * <li>actions - The permission actions that will be passed as the second
 *        argument to the constructor of the <code>Permission</code> class
 *        identified by <code>type</code>.
 * </ul>
 * <p>
 * The encoded format is:
 *
 * <pre>
 * (type)
 * </pre>
 *
 * or
 *
 * <pre>
 * (type &quot;name&quot;)
 * </pre>
 *
 * or
 *
 * <pre>
 * (type &quot;name&quot; &quot;actions&quot;)
 * </pre>
 *
 * where <i>name</i> and <i>actions</i> are strings that must be encoded for
 * proper parsing. Specifically, the <code>&quot;</code>,<code>\</code>,
 * carriage return, and line feed characters must be escaped using
 * <code>\&quot;</code>, <code>\\</code>,<code>\r</code>, and
 * <code>\n</code>, respectively.
 *
 * <p>
 * The encoded string contains no leading or trailing whitespace characters.
 * A single space character is used between <i>type</i> and
 * &quot;<i>name</i>&quot; and between &quot;<i>name</i>&quot; and
 * &quot;<i>actions</i>&quot;.
 * 
 * @author peter
 * @since 3.0.0
 */
@Beta
public interface AdvisoryDynamicPermissions {
    
    public static final Permission [] DEFAULT_PERMISSIONS = new Permission [0];
    
    /**
     * Advisory permissions for smart proxy's, the client may or may not grant them.
     * This method is typically called from a 
     * {@link net.jini.security.ProxyPreparer} implementation.  If the client
     * chooses to grant these permissions, it may do so to a specific proxy 
     * with Principals belonging to a specific ClassLoader after authenticating
     * the proxy's principal.
     * 
     * @return unshared array containing only advisory permissions and no null references.
     * 
     */
    public Permission [] getPermissions();
}
