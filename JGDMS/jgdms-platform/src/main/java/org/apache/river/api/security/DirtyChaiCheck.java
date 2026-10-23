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

package org.apache.river.api.security;

import java.util.ArrayList;
import java.util.List;

/**
 * Fails fast, with a message that says what is wrong, when this JVM does not bundle the
 * {@code org.apache.river.api.security} classes that JGDMS is compiled against.
 * <p>
 * Those classes are in {@code java.base} of a DirtyChai JDK.  On any other JDK they are
 * missing, and only the signature-only stubs of {@code jgdms-dirtychai-stubs} (compile-time,
 * never to be shipped) can be on the class path, whose methods throw
 * {@code UnsupportedOperationException}.  The boot class loader is asked, because it
 * is the one that cannot find a stub: a class that java.base lacks (an older DirtyChai)
 * is reported as well, instead of being loaded from a stub that is on the class path.
 */
public final class DirtyChaiCheck {

    /** The classes that DirtyChai bundles, see tools/stubgen/classes.txt. */
    private static final String[] BUNDLED = {
        "CertificateGrant", "ClassLoaderGrant", "DigestDomainGrant", "DigestGrant",
        "PermissionDelegate", "PermissionGrant", "PermissionGrantBuilder",
        "PermissionGrantBuilderImp", "PrincipalGrant", "ProtectionDomainGrant",
        "ScalableNestedPolicy", "URIGrant", "UnresolvedPrincipal"
    };

    private DirtyChaiCheck() {}

    /** The bundled classes that the boot class loader cannot find. */
    public static List<String> missing() {
        List<String> missing = new ArrayList<String>();
        for (String name : BUNDLED) {
            try {
                Class.forName("org.apache.river.api.security." + name, false, null);
            } catch (ClassNotFoundException e) {
                missing.add(name);
            }
        }
        return missing;
    }

    /**
     * @throws IllegalStateException if this JVM is not a DirtyChai one, or an older one
     */
    public static void verify() {
        List<String> missing = missing();
        if (!missing.isEmpty()) {
            throw new IllegalStateException("JGDMS needs a DirtyChai JDK: java.base lacks "
                    + missing.size() + " of the 13 org.apache.river.api.security classes ("
                    + missing + ").  A stock JDK is only for compiling, with jgdms-dirtychai-stubs.");
        }
    }
}
