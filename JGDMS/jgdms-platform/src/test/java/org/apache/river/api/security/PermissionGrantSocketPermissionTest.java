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

import java.net.SocketPermission;
import java.security.AllPermission;
import java.security.Permission;
import java.security.Principal;
import java.security.cert.Certificate;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 * Tests that {@link PermissionGrant} accepts {@link SocketPermission}
 * instances without throwing, exercising the reflective
 * {@code SocketPermission.init()} call that is a no-op on standard JDK.
 */
public class PermissionGrantSocketPermissionTest {

    // -----------------------------------------------------------------------
    // SocketPermission can be stored in a URI grant without error
    // -----------------------------------------------------------------------

    @Test
    public void testSocketPermissionAddedToURIGrantDoesNotThrow() {
        System.out.println("testSocketPermissionAddedToURIGrantDoesNotThrow");
        Permission sp = new SocketPermission("localhost:1099", "connect");
        PermissionGrant g = PermissionGrantBuilder.newBuilder()
                .uri("file:/app.jar")
                .permissions(new Permission[]{ sp })
                .principals(new Principal[0])
                .certificates(new Certificate[0], new String[0])
                .context(PermissionGrantBuilder.URI)
                .build();
        assertNotNull(g);
        assertTrue(g.getPermissions().contains(sp));
    }

    @Test
    public void testSocketPermissionAddedToPrincipalGrantDoesNotThrow() {
        System.out.println("testSocketPermissionAddedToPrincipalGrantDoesNotThrow");
        Permission sp = new SocketPermission("192.168.1.1:80", "connect,accept");
        PermissionGrant g = PermissionGrantBuilder.newBuilder()
                .permissions(new Permission[]{ sp })
                .principals(new Principal[0])
                .context(PermissionGrantBuilder.PRINCIPAL)
                .build();
        assertNotNull(g);
        assertTrue(g.getPermissions().contains(sp));
    }

    /** Multiple SocketPermissions in the same grant — all should be processed. */
    @Test
    public void testMultipleSocketPermissionsDoNotThrow() {
        System.out.println("testMultipleSocketPermissionsDoNotThrow");
        Permission sp1 = new SocketPermission("host1:80", "connect");
        Permission sp2 = new SocketPermission("host2:443", "connect");
        Permission sp3 = new SocketPermission("host3:8080", "accept");
        PermissionGrant g = PermissionGrantBuilder.newBuilder()
                .uri("file:/multi.jar")
                .permissions(new Permission[]{ sp1, sp2, sp3 })
                .principals(new Principal[0])
                .certificates(new Certificate[0], new String[0])
                .context(PermissionGrantBuilder.URI)
                .build();
        assertNotNull(g);
        assertEquals(3, g.getPermissions().size());
    }

    /** Mix of SocketPermission and non-SocketPermission in the same grant. */
    @Test
    public void testMixedPermissionsIncludingSocketPermission() {
        System.out.println("testMixedPermissionsIncludingSocketPermission");
        Permission sp   = new SocketPermission("example.com:443", "connect");
        Permission all  = new AllPermission();
        Permission rt   = new RuntimePermission("exitVM");
        PermissionGrant g = PermissionGrantBuilder.newBuilder()
                .uri("file:/mixed.jar")
                .permissions(new Permission[]{ sp, all, rt })
                .principals(new Principal[0])
                .certificates(new Certificate[0], new String[0])
                .context(PermissionGrantBuilder.URI)
                .build();
        assertNotNull(g);
        // AllPermission makes the grant privileged
        assertTrue(g.isPrivileged());
        assertTrue(g.getPermissions().contains(sp));
    }

    /** SocketPermission with a wildcard host should not throw. */
    @Test
    public void testSocketPermissionWildcardHostDoesNotThrow() {
        System.out.println("testSocketPermissionWildcardHostDoesNotThrow");
        Permission sp = new SocketPermission("*.example.com", "resolve");
        PermissionGrant g = PermissionGrantBuilder.newBuilder()
                .uri("file:/wildcard.jar")
                .permissions(new Permission[]{ sp })
                .principals(new Principal[0])
                .certificates(new Certificate[0], new String[0])
                .context(PermissionGrantBuilder.URI)
                .build();
        assertNotNull(g);
        assertTrue(g.getPermissions().contains(sp));
    }

    /** No permissions in grant — must still succeed. */
    @Test
    public void testEmptyPermissionsArrayDoesNotThrow() {
        System.out.println("testEmptyPermissionsArrayDoesNotThrow");
        PermissionGrant g = PermissionGrantBuilder.newBuilder()
                .uri("file:/empty.jar")
                .permissions(new Permission[0])
                .principals(new Principal[0])
                .certificates(new Certificate[0], new String[0])
                .context(PermissionGrantBuilder.URI)
                .build();
        assertNotNull(g);
        assertTrue(g.getPermissions().isEmpty());
    }
}
