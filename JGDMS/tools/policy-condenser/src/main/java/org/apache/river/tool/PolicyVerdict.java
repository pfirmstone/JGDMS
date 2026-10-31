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
package org.apache.river.tool;

import java.io.File;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URL;
import java.security.AllPermission;
import java.security.CodeSource;
import java.security.Permission;
import java.security.Policy;
import java.security.Principal;
import java.security.ProtectionDomain;
import java.security.cert.Certificate;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import javax.security.auth.x500.X500Principal;

/**
 * Asks a production policy provider, not a text scan, whether a policy file gives
 * {@code java.security.AllPermission} to code from a stranger's codebase.
 *
 * <p>Provider choice.  On the DirtyChai JDK the provider the security manager installs is
 * {@code au.zeus.jdk.authorization.policy.ConcurrentPolicyFile}; its package is not exported, so
 * it is reachable only with {@code --add-exports java.base/au.zeus.jdk.authorization.policy=
 * ALL-UNNAMED}.  Without it, or on a stock JDK, the tool falls back to
 * {@code org.apache.river.api.security.ConcurrentPolicyFile} of jgdms-platform, which is the same
 * source and was measured to give the same answers on the review fixtures
 * ({@code PolicyLintTest.providersAgreeOnFixtures}).  The stock
 * {@code sun.security.provider.PolicyFile} is never used: it disagrees with both on seven of the
 * review fixtures.  The provider class is reported in the output.
 *
 * <p>Both providers expand {@code ${property}} from {@link System#getProperties()}, so the caller's
 * definitions are set as system properties for the duration of one question and restored.
 */
final class PolicyVerdict {

    static final String DIRTYCHAI = "au.zeus.jdk.authorization.policy.ConcurrentPolicyFile";
    static final String PLATFORM = "org.apache.river.api.security.ConcurrentPolicyFile";

    /** Test seam: the class tried first for {@code auto} and {@code dirtychai}. */
    static volatile String dirtyChaiClass = DIRTYCHAI;

    /** The answers for one policy file. */
    static final class Answer {
        /** Null if the real parser loaded the file, else why it did not. */
        String loadError;
        /** Code from http://evil.example.com/evil.jar, no principal. */
        boolean stranger;
        /** The same code run by an arbitrary authenticated X500Principal. */
        boolean strangerPrincipal;
        boolean fileCode;
        boolean platformModule;
        /** Null CodeSource: not implied by design; reported, never a finding on its own. */
        boolean nullCodeSource;
    }

    final String providerClass;
    final String note;
    private final Constructor<?> constructor;
    private final Object parser;
    private final Method parse;

    private PolicyVerdict(String providerClass, String note, Constructor<?> constructor,
            Object parser, Method parse) {
        this.providerClass = providerClass;
        this.note = note;
        this.constructor = constructor;
        this.parser = parser;
        this.parse = parse;
    }

    /**
     * Opens a provider.  {@code mode} is {@code auto}, {@code dirtychai} or {@code platform}.
     *
     * @throws IllegalStateException if no provider of the requested kind can be used
     */
    static PolicyVerdict open(String mode) {
        String note = null;
        if (!"platform".equals(mode)) {
            try {
                return open(dirtyChaiClass, "production provider of the DirtyChai JDK");
            } catch (Throwable t) {
                if ("dirtychai".equals(mode)) {
                    throw new IllegalStateException(dirtyChaiClass + " cannot be used: " + why(t)
                            + " (needs --add-exports java.base/au.zeus.jdk.authorization.policy="
                            + "ALL-UNNAMED on the DirtyChai JDK)");
                }
                note = "fell back from " + dirtyChaiClass + ": " + why(t);
            }
        }
        try {
            return open(PLATFORM, note == null ? "provider of jgdms-platform" : note);
        } catch (Throwable t) {
            throw new IllegalStateException("no policy provider can be used: " + why(t));
        }
    }

    private static PolicyVerdict open(String className, String note) throws Exception {
        Class<?> c = Class.forName(className, true, PolicyVerdict.class.getClassLoader());
        if (!Policy.class.isAssignableFrom(c)) {
            throw new ClassCastException(className + " is not a java.security.Policy");
        }
        Constructor<?> k = c.getConstructor(URL[].class);
        String pkg = className.substring(0, className.lastIndexOf('.'));
        Class<?> pc = Class.forName(pkg + ".DefaultPolicyParser", true, c.getClassLoader());
        Object parser = pc.getConstructor().newInstance();
        Method parse = pc.getMethod("parse", URL.class, Properties.class);
        // Construct once on an empty list: reflection access errors surface here, not per file.
        k.newInstance(new Object[]{new URL[0]});
        return new PolicyVerdict(className, note, k, parser, parse);
    }

    private static String why(Throwable t) {
        Throwable c = t;
        while (c instanceof InvocationTargetException && c.getCause() != null) c = c.getCause();
        return c.getClass().getSimpleName() + (c.getMessage() == null ? "" : ": " + c.getMessage());
    }

    /**
     * Loads {@code policy} with the provider and asks the questions.  {@code defines} are visible
     * to property expansion during the call and removed after it.
     */
    synchronized Answer ask(File policy, Map<String, String> defines) {
        Answer a = new Answer();
        Properties sys = System.getProperties();
        Map<String, String> saved = new HashMap<String, String>();
        Map<String, String> set = new HashMap<String, String>(defines);
        set.put("/", File.separator);
        for (Map.Entry<String, String> e : set.entrySet()) {
            saved.put(e.getKey(), sys.getProperty(e.getKey()));
            sys.setProperty(e.getKey(), e.getValue());
        }
        try {
            URL url = policy.toURI().toURL();
            try {
                parse.invoke(parser, url, sys);
            } catch (InvocationTargetException e) {
                a.loadError = why(e);
                return a;
            }
            Policy p;
            try {
                p = (Policy) constructor.newInstance(new Object[]{new URL[]{url}});
            } catch (InvocationTargetException e) {
                a.loadError = why(e);
                return a;
            }
            Permission all = new AllPermission();
            Principal[] none = new Principal[0];
            Principal[] some = {new X500Principal("CN=lint-stranger")};
            CodeSource http = source("http://evil.example.com/evil.jar");
            a.stranger = p.implies(new ProtectionDomain(http, null, null, none), all);
            a.strangerPrincipal = p.implies(new ProtectionDomain(http, null, null, some), all);
            a.fileCode = p.implies(new ProtectionDomain(source("file:/lint-stranger/evil.jar"),
                    null, null, none), all);
            CodeSource jrt = source("jrt:/lint.stranger.module");
            a.platformModule = jrt != null
                    && p.implies(new ProtectionDomain(jrt, null, null, none), all);
            a.nullCodeSource = p.implies(new ProtectionDomain(null, null, null, none), all);
        } catch (Exception e) {
            a.loadError = why(e);
        } finally {
            for (Map.Entry<String, String> e : saved.entrySet()) {
                if (e.getValue() == null) sys.remove(e.getKey());
                else sys.setProperty(e.getKey(), e.getValue());
            }
        }
        return a;
    }

    private static CodeSource source(String url) {
        try {
            return new CodeSource(new URL(url), (Certificate[]) null);
        } catch (MalformedURLException e) {
            return null; // e.g. no jrt: handler on this JDK; the question is then not asked
        }
    }
}
