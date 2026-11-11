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
 * A stand-in policy provider (selected through {@code PolicyVerdict.dirtyChaiClass}) that answers
 * {@code implies} like the platform provider but lists nothing from {@code getPermissions}: the
 * cross-check's questions see every grant, its listing sees none, and it must not pass.
 */
public class ImpliesButListsNothingPolicy extends Policy {

    private final Policy real;

    public ImpliesButListsNothingPolicy(URL[] urls) throws ReflectiveOperationException {
        real = (Policy) Class.forName("org.apache.river.api.security.ConcurrentPolicyFile")
                .getConstructor(URL[].class).newInstance(new Object[]{urls});
    }

    @Override
    public boolean implies(ProtectionDomain domain, Permission permission) {
        return real.implies(domain, permission);
    }

    @Override
    public PermissionCollection getPermissions(ProtectionDomain domain) {
        return new Permissions();
    }
}
