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

package org.apache.river.test.impl.end2end.e2etest ;

/* Davis imports */
import net.jini.security.AccessPermission;

/* Java imports */
import java.security.Permission;

/**
 * A permission used to express the access control policy for the
 * <code>SecureServer</code> class. The <code>name</code> specifies the names of
 * the method which you have permission to call. An asterisk means all methods.
 */
public class SecureServerPermission extends AccessPermission
                                    implements Constants
{
    /**
     * Construct a <code>SecureServerPermission</code> for the given
     * target <code>name</code>. This constructor <b>must</b> be
     * defined or the remote call will fail
     */
    public SecureServerPermission(String name) {
        super(name);
    }

    /**
     * Test whether this permission implies the given permission.
     *
     * @param p the permission to test
     * @return true if this permission implies <code>p</code>
     */
    public boolean implies(Permission p) {
        boolean result = super.implies(p) ;
        return result;
    }
}
