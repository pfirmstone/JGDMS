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
import java.security.Principal;
import java.security.ProtectionDomain;
import org.junit.Test;
import static org.junit.Assert.*;

public class ExternallyVoidablePermissionGrantTest {

    @Test
    public void voidGrant_marksGrantVoidAndStopsImplication() {
        PermissionGrant base = PermissionGrantBuilder.newBuilder()
                .permissions(new Permission[]{new RuntimePermission("testVoidableGrant")})
                .principals(new Principal[0])
                .context(PermissionGrantBuilder.PRINCIPAL)
                .build();
        ExternallyVoidablePermissionGrant wrapped =
                new ExternallyVoidablePermissionGrant(base);

        assertFalse(wrapped.isVoid());
        assertTrue(wrapped.implies((ProtectionDomain) null));

        wrapped.voidGrant();
        assertTrue(wrapped.isVoid());
        assertFalse(wrapped.implies((ProtectionDomain) null));
    }
}
