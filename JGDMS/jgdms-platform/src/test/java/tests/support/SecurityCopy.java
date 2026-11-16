/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package tests.support;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.security.CodeSource;
import org.apache.river.api.security.PermissionGrantBuilder;
import org.junit.Assume;

/**
 * Which copy of the shared {@code org.apache.river.api.security} classes
 * (those DirtyChai bundles in {@code java.base}) the tests run against, found
 * through public API only, so that tests can skip what the loaded copy does
 * not have.
 * <p>
 * A JDK that bundles the classes loads its own, and this module's copies on
 * the class path are not used; any other JDK loads this module's. The tests do
 * not patch anything into {@code java.base}, so they cannot reach the
 * non-public members of the classes of the JDK's copy.
 */
public final class SecurityCopy {

    private SecurityCopy() {}

    /**
     * @return true if the shared classes are this module's copies, loaded
     * from the class path; false if they are the JDK's, loaded by the boot
     * loader. This module's copies are not used on a JDK that bundles its own.
     */
    public static boolean isJgdmsCopy() {
        return PermissionGrantBuilder.class.getClassLoader() != null;
    }

    /**
     * @return true if the loaded copy has {@code PermissionGrantBuilder.DIGEST_DOMAIN}
     * and {@code PermissionGrantBuilder.codeSources(CodeSource...)}: it is not
     * older than the pinned DirtyChai trunk, whose fixes the tests assume.
     */
    public static boolean hasDigestDomain() {
        try {
            Field f = PermissionGrantBuilder.class.getField("DIGEST_DOMAIN");
            PermissionGrantBuilder.class.getMethod("codeSources", CodeSource[].class);
            return f.getType() == int.class && Modifier.isStatic(f.getModifiers());
        } catch (NoSuchFieldException | NoSuchMethodException e) {
            return false;
        }
    }

    /**
     * The value of {@code PermissionGrantBuilder.DIGEST_DOMAIN} of the loaded
     * copy. The constant is read by reflection because a compiler inlines it,
     * and the JDK's copy may number its contexts differently.
     * 
     * @return the context
     * @throws AssertionError if the loaded copy has no such constant
     */
    public static int digestDomain() {
        try {
            return PermissionGrantBuilder.class.getField("DIGEST_DOMAIN").getInt(null);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("the loaded copy has no PermissionGrantBuilder.DIGEST_DOMAIN", e);
        }
    }

    /**
     * Skips the test unless the shared classes are this module's copies, for a
     * test of behaviour that only they have and that is going away.
     * 
     * @param behaviour what the test needs, for the message
     */
    public static void assumeJgdmsCopy(String behaviour) {
        Assume.assumeTrue("Skipped: " + behaviour + " exists only in this module's copy of "
                + "org.apache.river.api.security, and the JDK's copy is loaded", isJgdmsCopy());
    }

    /**
     * Skips the test if the loaded copy is older than the one with
     * {@code DIGEST_DOMAIN}, for a test of a behaviour that an older bundled
     * copy does not have.
     * 
     * @param behaviour what the test needs, for the message
     */
    public static void assumeCurrentCopy(String behaviour) {
        Assume.assumeTrue("Skipped: " + behaviour + " needs a copy of org.apache.river.api.security "
                + "with PermissionGrantBuilder.DIGEST_DOMAIN, and the loaded copy is older", hasDigestDomain());
    }
}
