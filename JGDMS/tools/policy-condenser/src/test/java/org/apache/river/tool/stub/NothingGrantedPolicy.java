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
package org.apache.river.tool.stub;

import java.net.URL;
import java.security.Permission;
import java.security.PermissionCollection;
import java.security.Permissions;
import java.security.Policy;
import java.security.ProtectionDomain;

/**
 * A stand-in policy provider (selected through {@code PolicyVerdict.dirtyChaiClass}) that loads
 * any file and grants nothing: the broken provider the cross-check of the corpus test must
 * notice (mutant M7).  Its parser is {@link DefaultPolicyParser} in this package.
 */
public class NothingGrantedPolicy extends Policy {

    public NothingGrantedPolicy(URL[] urls) {
    }

    @Override
    public boolean implies(ProtectionDomain domain, Permission permission) {
        return false;
    }

    @Override
    public PermissionCollection getPermissions(ProtectionDomain domain) {
        return new Permissions();
    }
}
