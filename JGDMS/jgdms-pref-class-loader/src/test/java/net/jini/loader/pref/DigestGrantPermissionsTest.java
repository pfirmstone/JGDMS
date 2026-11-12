/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
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

import java.net.URL;
import java.security.Permission;
import java.security.Permissions;
import org.junit.Assume;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 * Invariant: a per-JAR digest grant lets that JAR's code define classes on
 * DirtyChai, so it must imply DirtyChai's LoadClassPermission.  The grant
 * named a class that does not exist ({@code net.jini.loader.LoadClassPermission}),
 * which could never resolve.  Runs on DirtyChai only; elsewhere the class is
 * absent and the test is skipped.
 */
public class DigestGrantPermissionsTest {

    private static Permission dirtyChaiLoadClassPermission() throws Exception {
        Class<?> c;
        try {
            c = Class.forName(PreferredProxyCodebaseProvider.LOAD_CLASS_PERMISSION);
        } catch (ClassNotFoundException e) {
            Assume.assumeNoException("not DirtyChai", e);
            return null;
        }
        return (Permission) c.getConstructor().newInstance();
    }

    @Test
    public void aDigestGrantImpliesDirtyChaisLoadClassPermission() throws Exception {
        Permission loadClass = dirtyChaiLoadClassPermission();
        Permissions granted = new Permissions();
        for (Permission p : PreferredProxyCodebaseProvider.digestGrantPermissions(
                new URL("https://codebase.example/svc-dl.jar"))) {
            granted.add(p);
        }
        assertTrue("the digest grant must let the JAR define classes: " + granted, granted.implies(loadClass));
    }
}
