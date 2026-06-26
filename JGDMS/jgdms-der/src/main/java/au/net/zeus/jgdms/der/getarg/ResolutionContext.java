/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package au.net.zeus.jgdms.der.getarg;

import net.jini.loader.ClassLoading;

import java.net.MalformedURLException;

/**
 * The <em>endpoint-assigned</em> class-resolution context for a single DER decode: the loader
 * the receiving endpoint chose to resolve classes against, plus the codebase-integrity settings.
 *
 * <p>Class resolution is a deterministic function of the endpoint-chosen {@code defaultLoader},
 * <strong>never</strong> the thread-context class loader or {@code latestUserDefinedLoader} — the
 * ambient mechanisms that Michael Warres (<em>Class Loading Issues in Java RMI and Jini</em>,
 * TR-2006-149) shows fail across codebases (undesired local resolution, same-name type conflicts,
 * annotation loss) and break outright under OSGi (no meaningful "latest" loader; the TCCL cannot
 * see another bundle). The endpoint hands the loader in; resolution does not guess.
 *
 * <p>DER carries <strong>no</strong> codebase annotation on the wire (STD-006 sec.8): the schema
 * travels, not codebase URLs. So names resolve with {@code codebase == null} against
 * {@code defaultLoader} through {@link ClassLoading} (so the preferred-class / OSGi-aware
 * {@code PreferredClassProvider} SPI applies, and the {@code defaultLoader} anchors the parent of
 * any preferred loader). Because {@code codebase} is {@code null}, the
 * {@code verifyCodebaseIntegrity}/{@code verifierLoader} settings are carried for fidelity but do
 * not gate resolution here (integrity is enforced where a codebase IS conveyed — the downloadable
 * proxy path via {@code ProxyCodebaseSpi}).
 *
 * <p>{@link #NONE} (a null {@code defaultLoader}) is the standalone/in-VM decode with no endpoint:
 * {@code ClassLoading} then resolves through the platform default (the RMI/context fallback), which
 * is acceptable precisely because there is no federation boundary to get wrong.
 */
public record ResolutionContext(ClassLoader defaultLoader,
                                boolean verifyCodebaseIntegrity,
                                ClassLoader verifierLoader) {

    /** No endpoint loader assigned (standalone / in-VM decode); resolution falls back to the platform default. */
    public static final ResolutionContext NONE = new ResolutionContext(null, false, null);

    /** A context resolving against {@code defaultLoader} with no codebase-integrity verification. */
    public static ResolutionContext of(ClassLoader defaultLoader) {
        return defaultLoader == null ? NONE : new ResolutionContext(defaultLoader, false, null);
    }

    /**
     * Resolves a class by name against the endpoint loader (DER carries no codebase, so
     * {@code codebase == null}). Goes through {@link ClassLoading}, never the thread-context loader.
     */
    public Class<?> loadClass(String name) throws ClassNotFoundException {
        try {
            return ClassLoading.loadClass(null, name, defaultLoader, verifyCodebaseIntegrity, verifierLoader);
        } catch (MalformedURLException e) {
            // codebase is null -> never thrown; treat defensively as a resolution failure.
            throw new ClassNotFoundException(name, e);
        }
    }

    /**
     * Resolves a {@code java.lang.reflect.Proxy} class for the named interfaces against the endpoint
     * loader (the JGDMS-correct counterpart to {@code Proxy.getProxyClass} -- preferred-loader and
     * OSGi aware, with {@code defaultLoader} anchoring the parent).
     */
    public Class<?> loadProxyClass(String[] interfaceNames) throws ClassNotFoundException {
        try {
            return ClassLoading.loadProxyClass(null, interfaceNames, defaultLoader,
                    verifyCodebaseIntegrity, verifierLoader);
        } catch (MalformedURLException e) {
            throw new ClassNotFoundException("proxy" + java.util.Arrays.toString(interfaceNames), e);
        }
    }
}
