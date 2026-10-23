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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.Externalizable;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.ObjectInput;
import java.io.ObjectInputStream;
import java.io.ObjectOutput;
import java.io.ObjectOutputStream;
import java.lang.ref.WeakReference;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.security.CodeSource;
import java.security.Permission;
import java.security.PermissionCollection;
import java.security.Permissions;
import java.security.Policy;
import java.security.Principal;
import java.security.ProtectionDomain;
import java.security.PublicKey;
import java.security.cert.Certificate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import net.jini.security.Security;
import net.jini.security.policy.DynamicPolicyProvider;
import org.apache.river.api.security.codebase.IfaceA;
import org.apache.river.api.security.codebase.Outside;
import org.apache.river.api.security.codebase.ProxyA;
import org.apache.river.api.security.codebase.ProxyB;
import org.junit.After;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;
import tests.support.DigestCodeSources;
import tests.support.MyPrincipal;
import tests.support.SecurityCopy;

/**
 * {@link PermissionGrantBuilder#DIGEST_DOMAIN} grants apply to the domains of
 * the code that one ClassLoader loaded, identified by its
 * {@code DigestCodeSource}s, whether or not the domain exists yet, and to no
 * other; the grant lasts as long as the ClassLoader.
 * <p>
 * Needs {@code java.security.DigestCodeSource} (DirtyChai), which is reached 
 * by reflection; on a JDK that does not have the constructors needed the 
 * positive cases are skipped and the fail-closed cases run.
 * <p>
 * The grant classes tested are the ones the JDK loads: a JDK that bundles its
 * own in {@code java.base} uses those, any other uses this module's copies.
 * The test uses their public API only, and the package-private
 * {@code DigestDomainGrant} is identified by its class name. What the loaded
 * copy does not have is skipped (an older bundled copy has no
 * {@code DIGEST_DOMAIN}); so are the tests of behaviour that only this
 * module's copy has, and that is going away: the text of {@code toString},
 * the non-public constructor of {@code DigestDomainGrant} and serialization. 
 * Those run only when the JDK loads this module's copy.  A CLASSLOADER grant
 * without a class is void in both copies, and is tested on either.
 */
public class DigestDomainGrantTest {

    private static final Principal[] NONE = new Principal[0];
    private static final Permission PERM = new RuntimePermission("digest.domain.grant");
    private static final byte[] SHA = {1, 2, 3};

    private File dir;
    private URL jarA;
    private URL jarB;
    private URL jarOutside;
    private Loader loader;

    @Before
    public void setUp() throws Exception {
        dir = Files.createTempDirectory("digest-domain").toFile();
        jarA = jar("a.jar", ProxyA.class);
        jarB = jar("b.jar", ProxyB.class);
        jarOutside = jar("outside.jar", Outside.class);
        loader = new Loader(new URL[]{jarA, jarB});
    }

    @After
    public void tearDown() throws Exception {
        loader.close();
        for (File f : dir.listFiles()) f.delete();
        dir.delete();
    }

    private URL jar(String name, Class<?> c) throws Exception {
        File f = new File(dir, name);
        String res = c.getName().replace('.', '/') + ".class";
        try (JarOutputStream out = new JarOutputStream(new FileOutputStream(f));
             InputStream in = DigestDomainGrantTest.class.getClassLoader()
                     .getResourceAsStream(res)) {
            out.putNextEntry(new JarEntry(res));
            byte[] buf = new byte[4096];
            for (int n; (n = in.read(buf)) > 0;) out.write(buf, 0, n);
            out.closeEntry();
        }
        return f.toURI().toURL();
    }

    // ---- DigestCodeSource, by reflection -----------------------------------

    /**
     * Skips the test unless the loaded copy has DIGEST_DOMAIN and the
     * DigestCodeSource has what the test needs.
     */
    private static void assumeDigest() {
        SecurityCopy.assumeCurrentCopy("a DIGEST_DOMAIN grant");
        Assume.assumeNotNull(DigestCodeSources.usable());
    }

    /**
     * A digest computed from the jar: what the loader makes.
     * 
     * @throws SecurityException as DigestCodeSource does, when the jar is not
     * what it was when its digest was first computed.
     */
    private static CodeSource computed(URL jar, Certificate... certs) throws Exception {
        return DigestCodeSources.computed(jar.toString(), certs.length == 0 ? null : certs, "SHA-256");
    }

    /** An expected identity, or a forgery: the digest is asserted, not computed. */
    private static CodeSource asserted(URL jar, byte[] digest, String alg, Certificate... certs) throws Exception {
        return DigestCodeSources.asserted(jar.toString(), certs.length == 0 ? null : certs, alg, digest);
    }

    private static byte[] digestOf(CodeSource cs) throws Exception {
        return DigestCodeSources.digestOf(cs);
    }

    /** The identity the verifier established for a jar: the supplied digest. */
    private static CodeSource expected(URL jar, Certificate... certs) throws Exception {
        return asserted(jar, digestOf(computed(jar, certs)), "SHA-256", certs);
    }

    /**
     * DigestDomainGrant is package-private, and in java.base on a JDK that
     * bundles it, so it is identified by its name.
     */
    private static boolean isDigestDomainGrant(PermissionGrant g) {
        return g != null && "org.apache.river.api.security.DigestDomainGrant".equals(g.getClass().getName());
    }

    private PermissionGrant grantTo(Class<?> c, CodeSource[] codebase, Principal... pals) {
        return grantTo(PERM, c, codebase, pals);
    }

    private PermissionGrant grantTo(Permission perm, Class<?> c, CodeSource[] codebase, Principal... pals) {
        return PermissionGrantBuilder.newBuilder()
                .principals(pals)
                .permissions(new Permission[]{perm})
                .codeSources(codebase)
                .context(SecurityCopy.digestDomain())
                .clazz(c)
                .build();
    }

    private ProtectionDomain domain(CodeSource cs, ClassLoader l, Principal... pals) {
        return new ProtectionDomain(cs, null, l, pals);
    }

    // ---- the grant ---------------------------------------------------------

    @Test
    public void verifiedJarsAreImpliedIncludingOnesLoadedLater() throws Exception {
        assumeDigest();
        Class<?> a = loader.define(ProxyA.class, computed(jarA));
        PermissionGrant g = grantTo(a, new CodeSource[]{expected(jarA), expected(jarB)});
        assertTrue(isDigestDomainGrant(g));
        assertTrue("A", g.implies(a.getProtectionDomain()));
        // B has no domain yet when the grant is made.
        Class<?> b = loader.define(ProxyB.class, computed(jarB));
        assertTrue("B, defined after the grant", g.implies(b.getProtectionDomain()));
        // A domain rebuilt by a combiner shares the CodeSource.
        assertTrue(g.implies(domain(b.getProtectionDomain().getCodeSource(), loader, new MyPrincipal("p"))));
    }

    @Test
    public void unexpectedCodeIsNotImplied() throws Exception {
        assumeDigest();
        Class<?> a = loader.define(ProxyA.class, computed(jarA));
        PermissionGrant g = grantTo(a, new CodeSource[]{expected(jarA)});
        // A jar added to the loader later (URLClassLoader.addURL).
        loader.addUrl(jarB);
        assertFalse("jar added later", g.implies(domain(computed(jarB), loader)));
        // Same location as A, different content.
        File f = new File(dir, "a.jar");
        Files.write(f.toPath(), Files.readAllBytes(new File(dir, "outside.jar").toPath()));
        try {
            assertFalse("changed content", g.implies(domain(computed(jarA), loader)));
        } catch (SecurityException refused) {
            // DigestCodeSource remembers the digest first computed for a
            // location and refuses to compute another: the changed jar never
            // becomes a code source to be implied, which is closed too. The
            // grant is not affected by the change, A's domain is still implied.
            assertTrue("A, as it was loaded", g.implies(a.getProtectionDomain()));
        }
        // Whichever way, the digest of the changed content, asserted for A's 
        // location, is not what the verifier established.
        assertFalse("changed content, digest asserted", g.implies(
                domain(asserted(jarA, digestOf(computed(jarOutside)), "SHA-256"), loader)));
        // Right location and digest, other signers than the verifier saw.
        assertFalse("other signers", g.implies(domain(computed(jarOutside, new FakeCertificate("x")), loader)));
    }

    @Test
    public void aDigestAssertedByACallerIsNotIdentity() throws Exception {
        assumeDigest();
        Class<?> a = loader.define(ProxyA.class, computed(jarA));
        PermissionGrant g = grantTo(a, new CodeSource[]{expected(jarA)});
        ProtectionDomain forged = domain(asserted(jarA, digestOf(computed(jarA)), "SHA-256"), loader);
        assertFalse("forged DigestCodeSource carrying A's digest", g.implies(forged));
        assertFalse("plain CodeSource at A", g.implies(domain(new CodeSource(jarA, (Certificate[]) null), loader)));
        assertTrue("control: the computed one", g.implies(domain(computed(jarA), loader)));
    }

    @Test
    public void mixedSignersMatchOnlyTheSuppliedSet() throws Exception {
        assumeDigest();
        Certificate signer = new FakeCertificate("signer");
        Class<?> a = loader.define(ProxyA.class, computed(jarA));
        PermissionGrant unsigned = grantTo(a, new CodeSource[]{expected(jarA)});
        PermissionGrant signed = grantTo(a, new CodeSource[]{expected(jarA, signer)});
        ProtectionDomain bySigner = domain(computed(jarA, signer), loader);
        ProtectionDomain byNone = domain(computed(jarA), loader);
        assertFalse(unsigned.implies(bySigner));
        assertTrue(unsigned.implies(byNone));
        assertTrue(signed.implies(bySigner));
        assertFalse(signed.implies(byNone));
    }

    @Test
    public void anotherLoaderOverIdenticalJarsIsNotImplied() throws Exception {
        assumeDigest();
        Class<?> a = loader.define(ProxyA.class, computed(jarA));
        PermissionGrant g = grantTo(a, new CodeSource[]{expected(jarA)});
        try (Loader other = new Loader(new URL[]{jarA})) {
            assertFalse(g.implies(domain(computed(jarA), other)));
        }
    }

    @Test
    public void aProxyClassUsesTheLoaderOfTheClass() throws Exception {
        assumeDigest();
        URL jarI = jar("iface.jar", IfaceA.class);
        try (Loader l = new Loader(new URL[]{jarA, jarI})) {
            Class<?> iface = l.loadClass(IfaceA.class.getName());
            Object proxy = Proxy.newProxyInstance(l, new Class<?>[]{iface}, new InvocationHandler() {
                public Object invoke(Object p, java.lang.reflect.Method m, Object[] args) {
                    return null;
                }
            });
            Class<?> proxyClass = proxy.getClass();
            assertNull(proxyClass.getProtectionDomain().getClassLoader());
            PermissionGrant g = grantTo(proxyClass, new CodeSource[]{expected(jarA)});
            assertTrue(g.implies(domain(computed(jarA), l)));
            assertFalse("Proxy classes' shared domain has no loader to match",
                    g.implies(domain(computed(jarA), null)));
        }
    }

    @Test
    public void principalsAreHonoured() throws Exception {
        assumeDigest();
        Class<?> a = loader.define(ProxyA.class, computed(jarA));
        Principal p = new MyPrincipal("required");
        PermissionGrant g = grantTo(a, new CodeSource[]{expected(jarA)}, p);
        assertFalse(g.implies(domain(computed(jarA), loader)));
        assertTrue(g.implies(domain(computed(jarA), loader, p)));
    }

    @Test
    public void classLoaderAndCodeSourceAreNotImplied() throws Exception {
        assumeDigest();
        Class<?> a = loader.define(ProxyA.class, computed(jarA));
        PermissionGrant g = grantTo(a, new CodeSource[]{expected(jarA)});
        assertFalse(g.implies(loader, NONE));
        assertFalse(g.implies(computed(jarA), NONE));
    }

    /**
     * D1: a CLASSLOADER grant is tied to a ClassLoader and is dynamic only, so
     * it never implies a CodeSource, whatever the CodeSource is (null, plain,
     * the one of the granted class), whatever the Principals, and whether or
     * not the grant has a class. DirtyChai's copy before dafbac90a inherited
     * {@code PrincipalGrant.implies(CodeSource, Principal[])}, which was true
     * for every CodeSource when the Principals matched, and for a grant
     * without Principals, always.
     */
    @Test
    public void classLoaderGrantNeverImpliesACodeSource() throws Exception {
        SecurityCopy.assumeCurrentCopy("a CLASSLOADER grant that implies no CodeSource");
        if (!SecurityCopy.isJgdmsCopy()) {
            // The JDK's own copy: one built before DirtyChai dafbac90a still has D1, and so
            // has a JDK image older than DIGEST_DOMAIN (skipped above). That is a known gap in
            // the JDK under test, not in JGDMS: see NON-CONFORMANCES D1.
            PermissionGrant voidGrant = PermissionGrantBuilder.newBuilder()
                    .permissions(new Permission[]{PERM})
                    .context(PermissionGrantBuilder.CLASSLOADER)
                    .build();
            Assume.assumeFalse("this JDK's bundled copy predates DirtyChai dafbac90a (D1): a CLASSLOADER"
                    + " grant implies a null CodeSource", voidGrant.implies((CodeSource) null, NONE));
        }
        Class<?> a = loader.define(ProxyA.class, new CodeSource(jarA, (Certificate[]) null));
        Principal p = new MyPrincipal("p");
        Principal q = new MyPrincipal("q");
        CodeSource plain = new CodeSource(jarB, (Certificate[]) null);
        CodeSource ofA = a.getProtectionDomain().getCodeSource();
        for (boolean withClass : new boolean[]{false, true}) {
            for (Principal[] grantee : new Principal[][]{NONE, {p}}) {
                PermissionGrantBuilder b = PermissionGrantBuilder.newBuilder()
                        .principals(grantee)
                        .permissions(new Permission[]{PERM})
                        .context(PermissionGrantBuilder.CLASSLOADER);
                if (withClass) b.clazz(a);
                PermissionGrant g = b.build();
                String what = (withClass ? "with class" : "without class") + ", grantee " + Arrays.asList(grantee);
                assertEquals(what + ": void without a class", !withClass, g.isVoid());
                for (CodeSource cs : new CodeSource[]{null, plain, ofA}) {
                    for (Principal[] pals : new Principal[][]{NONE, {p}, {q}, {p, q}}) {
                        assertFalse(what + ": implies(" + cs + ", " + Arrays.asList(pals) + ")",
                                g.implies(cs, pals));
                    }
                }
                try (Loader other = new Loader(new URL[]{jarA})) {
                    assertFalse(what + ": a domain of the CodeSource but another loader",
                            g.implies(domain(ofA, other, p)));
                }
                // The grant itself is live when it has a class: the loader is implied.
                assertEquals(what + ": the loader of the class", withClass,
                        g.implies(domain(ofA, loader, p)));
            }
        }
    }

    /**
     * A Permission that can be serialized and read back.  DirtyChai's 
     * Permission is not Serializable, nor are RuntimePermission and the 
     * other JDK permissions, so a builder holding one cannot be written, 
     * and a Serializable subclass cannot be read, Permission having no 
     * constructor without arguments.  An Externalizable one can.
     */
    public static final class SerialPermission extends Permission implements Externalizable {
        public SerialPermission() {
            super("digest.domain.grant.serial");
        }
        @Override public boolean implies(Permission p) { return p instanceof SerialPermission; }
        @Override public boolean equals(Object o) { return o instanceof SerialPermission; }
        @Override public int hashCode() { return 17; }
        @Override public String getActions() { return ""; }
        @Override public void writeExternal(ObjectOutput out) { }
        @Override public void readExternal(ObjectInput in) { }
    }

    @Test
    public void voidAfterSerialization() throws Exception {
        SecurityCopy.assumeJgdmsCopy("the void grant that a digest domain grant becomes when serialized");
        assumeDigest();
        Class<?> a = loader.define(ProxyA.class, computed(jarA));
        PermissionGrant g = grantTo(new SerialPermission(), a, new CodeSource[]{expected(jarA)});
        assertFalse("control: not void before", g.isVoid());
        assertTrue("control: implies before", g.implies(a.getProtectionDomain()));
        ByteArrayOutputStream bo = new ByteArrayOutputStream();
        try (ObjectOutputStream oo = new ObjectOutputStream(bo)) {
            oo.writeObject(g);
        }
        PermissionGrant back = (PermissionGrant) new ObjectInputStream(
                new ByteArrayInputStream(bo.toByteArray())).readObject();
        assertTrue(back.isVoid());
        assertFalse(back.implies(domain(computed(jarA), loader)));
    }

    // ---- the builder never widens ------------------------------------------

    @Test
    public void builderRefusesWhatWouldBeWiderThanSupplied() throws Exception {
        SecurityCopy.assumeCurrentCopy("a DIGEST_DOMAIN builder");
        Class<?> a = loader.define(ProxyA.class, new CodeSource(jarA, (Certificate[]) null));
        PermissionGrantBuilder b = PermissionGrantBuilder.newBuilder()
                .principals(NONE).permissions(new Permission[]{PERM})
                .context(SecurityCopy.digestDomain());
        try {
            b.clazz(a).build();
            fail("no code sources");
        } catch (IllegalArgumentException expected) { }
        try {
            b.codeSources(new CodeSource[0]).build();
            fail("empty code sources");
        } catch (IllegalArgumentException expected) { }
        try {
            b.codeSources(new CodeSource(jarA, (Certificate[]) null)).build();
            fail("a code source without a digest");
        } catch (IllegalArgumentException expected) { }
        try {
            PermissionGrantBuilder.newBuilder().principals(NONE)
                    .permissions(new Permission[]{PERM})
                    .codeSources(new CodeSource(jarA, (Certificate[]) null))
                    .context(SecurityCopy.digestDomain()).build();
            fail("no class");
        } catch (IllegalArgumentException expected) { }
        if (DigestCodeSources.usable() != null) {
            try {
                b.codeSources(asserted(jarA, SHA, "SHA-512")).build();
                fail("digest algorithm other than SHA-256");
            } catch (IllegalArgumentException expected) { }
            // An expected identity may be a digest supplied by the verifier.
            assertNotNull(b.codeSources(asserted(jarA, SHA, "SHA-256")).build());
        }
    }

    @Test
    public void providerAndSecurityOverloadsNeverGrantUniversally() throws Exception {
        SecurityCopy.assumeCurrentCopy("a DIGEST_DOMAIN builder");
        Class<?> a = loader.define(ProxyA.class, new CodeSource(jarA, (Certificate[]) null));
        DynamicPolicyProvider policy = new DynamicPolicyProvider(new EmptyPolicy());
        for (CodeSource[] bad : new CodeSource[][]{null, new CodeSource[0], 
                {new CodeSource(jarA, (Certificate[]) null)}}) {
            try {
                policy.grant(a, bad, NONE, new Permission[]{PERM});
                fail("refused");
            } catch (IllegalArgumentException expected) { }
        }
        try {
            policy.grant(null, new CodeSource[]{new CodeSource(jarA, (Certificate[]) null)}, 
                    NONE, new Permission[]{PERM});
            fail("no class");
        } catch (IllegalArgumentException expected) { }
        assertEquals(0, policy.getGrants(a, NONE).length);
    }

    @Test
    public void securityOverloadRefusesAnInconclusiveLoader() throws Exception {
        Policy original = Policy.getPolicy();
        try {
            Policy.setPolicy(new DynamicPolicyProvider(new EmptyPolicy()));
            Class<?> a = loader.define(ProxyA.class, new CodeSource(jarA, (Certificate[]) null));
            Security.markInconclusiveProxyClassLoader(loader);
            try {
                Security.grant(a, new CodeSource[]{new CodeSource(jarA, (Certificate[]) null)}, 
                        NONE, new Permission[]{PERM});
                fail("inconclusive is not verified");
            } catch (IllegalStateException expected) { }
        } finally {
            Security.invalidateInconclusiveProxyLoaderGrants();
            Policy.setPolicy(original);
        }
    }

    @Test
    public void digestDomainDeliversPermissionThroughThePolicy() throws Exception {
        assumeDigest();
        Class<?> a = loader.define(ProxyA.class, computed(jarA));
        DynamicPolicyProvider policy = new DynamicPolicyProvider(new EmptyPolicy());
        policy.grant(a, new CodeSource[]{expected(jarA), expected(jarB)}, NONE, new Permission[]{PERM});
        Class<?> b = loader.define(ProxyB.class, computed(jarB));
        assertTrue(policy.implies(a.getProtectionDomain(), PERM));
        assertTrue(policy.implies(b.getProtectionDomain(), PERM));
        assertFalse(policy.implies(domain(computed(jarOutside), loader), PERM));
    }

    // ---- controls: the other paths, so the cases above distinguish the semantics

    @Test
    public void controlClassLoaderGrantCoversEverythingTheLoaderDefines() throws Exception {
        Class<?> a = loader.define(ProxyA.class, new CodeSource(jarA, (Certificate[]) null));
        PermissionGrant g = PermissionGrantBuilder.newBuilder().principals(NONE)
                .permissions(new Permission[]{PERM})
                .context(PermissionGrantBuilder.CLASSLOADER).clazz(a).build();
        loader.addUrl(jarOutside);
        assertTrue("added later", g.implies(domain(new CodeSource(jarOutside, (Certificate[]) null), loader)));
        assertTrue("forged", g.implies(domain(new CodeSource(jarOutside, 
                new Certificate[]{new FakeCertificate("x")}), loader)));
        try (Loader other = new Loader(new URL[]{jarA})) {
            assertFalse(g.implies(domain(new CodeSource(jarA, (Certificate[]) null), other)));
        }
    }

    @Test
    public void classLoaderGrantToAProxyClassUsesTheClassLoader() throws Exception {
        SecurityCopy.assumeCurrentCopy("a CLASSLOADER grant to a proxy class that applies to the loader of its interface");
        URL jarI = jar("iface.jar", IfaceA.class);
        try (Loader l = new Loader(new URL[]{jarA, jarI})) {
            Class<?> iface = l.loadClass(IfaceA.class.getName());
            Object proxy = Proxy.newProxyInstance(l, new Class<?>[]{iface}, new InvocationHandler() {
                public Object invoke(Object p, java.lang.reflect.Method m, Object[] args) {
                    return null;
                }
            });
            PermissionGrant g = PermissionGrantBuilder.newBuilder().principals(NONE)
                    .permissions(new Permission[]{PERM})
                    .context(PermissionGrantBuilder.CLASSLOADER).clazz(proxy.getClass()).build();
            assertTrue(g.implies(domain(new CodeSource(jarA, (Certificate[]) null), l)));
            assertFalse(g.implies(domain(new CodeSource(jarA, (Certificate[]) null), loader)));
            DynamicPolicyProvider policy = new DynamicPolicyProvider(new EmptyPolicy());
            policy.grant(proxy.getClass(), NONE, new Permission[]{PERM});
            assertTrue(Arrays.asList(policy.getGrants(proxy.getClass(), NONE)).contains(PERM));
            Class<?> a = l.loadClass(IfaceA.class.getName());
            assertTrue(Arrays.asList(policy.getGrants(a, NONE)).contains(PERM));
        }
    }

    @Test
    public void controlProtectionDomainGrantDoesNotCoverJarB() throws Exception {
        Class<?> a = loader.define(ProxyA.class, new CodeSource(jarA, (Certificate[]) null));
        PermissionGrant g = PermissionGrantBuilder.newBuilder().principals(NONE)
                .permissions(new Permission[]{PERM})
                .context(PermissionGrantBuilder.PROTECTIONDOMAIN).clazz(a).build();
        assertTrue(g.implies(a.getProtectionDomain()));
        assertFalse(g.implies(domain(new CodeSource(jarB, (Certificate[]) null), loader)));
    }

    /**
     * A CLASSLOADER grant is tied to the ClassLoader of a class and goes with
     * it. One that was not given a class is in the state of a grant whose
     * loader is gone: it is void and implies nothing. It never applies to all
     * domains, and building it does not throw. DirtyChai does the same.
     */
    @Test
    public void classLoaderGrantWithoutAClassIsVoid() throws Exception {
        SecurityCopy.assumeCurrentCopy("a CLASSLOADER grant without a class that is void");
        Principal p = new MyPrincipal("p");
        Class<?> a = loader.define(ProxyA.class, new CodeSource(jarA, (Certificate[]) null));
        // Control: the grant of a class is not void and implies the class's domain.
        PermissionGrant ofA = PermissionGrantBuilder.newBuilder().principals(NONE)
                .permissions(new Permission[]{PERM})
                .context(PermissionGrantBuilder.CLASSLOADER).clazz(a).build();
        assertFalse(ofA.isVoid());
        assertTrue(ofA.implies(a.getProtectionDomain()));
        assertTrue(ofA.implies(loader, NONE));

        PermissionGrant open = PermissionGrantBuilder.newBuilder().principals(NONE)
                .permissions(new Permission[]{PERM})
                .context(PermissionGrantBuilder.CLASSLOADER).build();
        assertTrue(open.isDyanamic());
        assertTrue("no class: void", open.isVoid());
        CodeSource cs = new CodeSource(jarA, (Certificate[]) null);
        assertFalse(open.implies(domain(cs, loader)));
        assertFalse(open.implies(a.getProtectionDomain()));
        assertFalse("a domain without a ClassLoader", open.implies(domain(cs, null)));
        assertFalse("no domain", open.implies((ProtectionDomain) null));
        try (Loader other = new Loader(new URL[]{jarB})) {
            assertFalse(open.implies(domain(cs, other)));
            assertFalse(open.implies(other, NONE));
        }
        assertFalse(open.implies(loader, NONE));
        assertFalse(open.implies((ClassLoader) null, NONE));
        // The same with principals: the grant never applies to all domains.
        PermissionGrant forP = PermissionGrantBuilder.newBuilder().principals(new Principal[]{p})
                .permissions(new Permission[]{PERM})
                .context(PermissionGrantBuilder.CLASSLOADER).build();
        assertTrue(forP.isVoid());
        assertFalse(forP.implies(domain(cs, loader, p)));
        assertFalse(forP.implies(loader, new Principal[]{p}));
        assertFalse(forP.implies(domain(cs, loader)));
        assertFalse(forP.implies(loader, NONE));
        // A class without a ClassLoader (a bootstrap class) is also void.
        PermissionGrant boot = PermissionGrantBuilder.newBuilder().principals(NONE)
                .permissions(new Permission[]{PERM})
                .context(PermissionGrantBuilder.CLASSLOADER).clazz(String.class).build();
        assertTrue(boot.isVoid());
        assertFalse(boot.implies(domain(cs, null)));
        assertFalse(boot.implies((ClassLoader) null, NONE));
    }

    /**
     * DynamicPolicyProvider.grant(null, ...) used to grant to all domains. A
     * grant without a class is void, so nothing is granted, and no exception
     * is thrown (the same checks as for any grant still apply).
     */
    @Test
    public void dynamicPolicyGrantWithoutAClassGrantsNothing() throws Exception {
        Class<?> a = loader.define(ProxyA.class, new CodeSource(jarA, (Certificate[]) null));
        DynamicPolicyProvider policy = new DynamicPolicyProvider(new EmptyPolicy());
        policy.grant(a, NONE, new Permission[]{PERM}); // control
        assertTrue(policy.implies(a.getProtectionDomain(), PERM));
        Permission other = new RuntimePermission("digest.domain.grant.other");
        policy.grant(null, NONE, new Permission[]{other});
        policy.grant(null, null, new Permission[]{other});
        assertFalse(policy.implies(a.getProtectionDomain(), other));
        assertFalse(policy.implies(domain(new CodeSource(jarOutside, (Certificate[]) null), null), other));
        assertFalse(policy.implies(null, other));
        assertEquals(0, policy.getGrants(null, NONE).length);
        assertFalse(Arrays.asList(policy.getGrants(a, NONE)).contains(other));
        assertTrue(Arrays.asList(policy.getGrants(a, NONE)).contains(PERM));
    }

    /** The grant of a class is dynamic and is not void while its loader is reachable. */
    @Test
    public void classLoaderGrantToAClassIsDynamicAndNotVoid() throws Exception {
        Class<?> a = loader.define(ProxyA.class, new CodeSource(jarA, (Certificate[]) null));
        PermissionGrantBuilder b = PermissionGrantBuilder.newBuilder().principals(NONE)
                .permissions(new Permission[]{PERM})
                .context(PermissionGrantBuilder.CLASSLOADER).clazz(a);
        PermissionGrant g = b.build();
        assertTrue(g.isDyanamic());
        assertFalse(g.isVoid());
        assertEquals("equal to a grant of the same builder state", g, b.build());
        assertEquals(g.hashCode(), b.build().hashCode());
        assertTrue(g.impliesEquivalent(b.build()));
        assertTrue(g.toString().contains("ClassLoader grant."));
        assertFalse("the loader is not the domain's", g.equals(
                PermissionGrantBuilder.newBuilder().principals(NONE)
                .permissions(new Permission[]{PERM})
                .context(PermissionGrantBuilder.PROTECTIONDOMAIN).clazz(a).build()));
    }

    /**
     * What a grant without a class and a grant to a class become when
     * serialized: both are void, and a deserialized grant never applies to all
     * domains. (Serialization is going away, see the non-conformance list J7.)
     */
    @Test
    public void classLoaderGrantSerializesThroughItsBuilder() throws Exception {
        SecurityCopy.assumeJgdmsCopy("serialization of a CLASSLOADER grant");
        Class<?> a = loader.define(ProxyA.class, new CodeSource(jarA, (Certificate[]) null));
        PermissionGrant open = PermissionGrantBuilder.newBuilder().principals(NONE)
                .permissions(new Permission[]{new SerialPermission()})
                .context(PermissionGrantBuilder.CLASSLOADER).build();
        assertTrue("control: void before", open.isVoid());
        PermissionGrant back = roundTrip(open);
        assertTrue("void after", back.isVoid());
        assertTrue(back.isDyanamic());
        assertFalse("does not become a grant to all domains", back.implies(domain(
                new CodeSource(jarOutside, (Certificate[]) null), loader)));
        assertFalse(back.implies(a.getProtectionDomain()));
        PermissionGrant ofA = PermissionGrantBuilder.newBuilder().principals(NONE)
                .permissions(new Permission[]{new SerialPermission()})
                .context(PermissionGrantBuilder.CLASSLOADER).clazz(a).build();
        assertTrue("control: implied before", ofA.implies(a.getProtectionDomain()));
        PermissionGrant gone = roundTrip(ofA);
        assertTrue("a ClassLoader is not serialized", gone.isVoid());
        assertFalse(gone.implies(a.getProtectionDomain()));
    }

    private static PermissionGrant roundTrip(PermissionGrant g) throws Exception {
        ByteArrayOutputStream bo = new ByteArrayOutputStream();
        try (ObjectOutputStream oo = new ObjectOutputStream(bo)) {
            oo.writeObject(g);
        }
        return (PermissionGrant) new ObjectInputStream(
                new ByteArrayInputStream(bo.toByteArray())).readObject();
    }

    @Test
    public void digestDomainGrantIsDynamicAndEqualToAnotherOfTheSameState() throws Exception {
        assumeDigest();
        Class<?> a = loader.define(ProxyA.class, computed(jarA));
        PermissionGrantBuilder b = PermissionGrantBuilder.newBuilder()
                .principals(NONE)
                .permissions(new Permission[]{PERM})
                .codeSources(expected(jarA))
                .context(SecurityCopy.digestDomain())
                .clazz(a);
        PermissionGrant g = b.build();
        assertTrue(g.isDyanamic());
        assertFalse(g.isVoid());
        assertEquals(g, b.build());
        assertEquals(g.hashCode(), b.build().hashCode());
        assertTrue(g.impliesEquivalent(b.build()));
        PermissionGrant other = grantTo(a, new CodeSource[]{expected(jarB)});
        assertFalse("other identities", g.equals(other));
        assertFalse("other identities", g.impliesEquivalent(other));
        assertFalse(g.equals(PermissionGrantBuilder.newBuilder().principals(NONE)
                .permissions(new Permission[]{PERM})
                .context(PermissionGrantBuilder.CLASSLOADER).clazz(a).build()));
    }

    /** The text of toString is that of this module's copy only. */
    @Test
    public void digestDomainGrantToStringSaysWhatItIs() throws Exception {
        SecurityCopy.assumeJgdmsCopy("the text 'Digest domain grant:' of DigestDomainGrant.toString");
        assumeDigest();
        Class<?> a = loader.define(ProxyA.class, computed(jarA));
        assertTrue(grantTo(a, new CodeSource[]{expected(jarA)}).toString().contains("Digest domain grant:"));
    }

    /**
     * The builder never passes what the constructor refuses, so it is
     * called directly, by reflection: the class is package-private, so this
     * is possible only when it is this module's copy, in the runtime package
     * of the test.
     */
    @Test
    public void digestDomainGrantRefusesWhatCannotBeVerified() throws Exception {
        SecurityCopy.assumeJgdmsCopy("the non-public constructor of DigestDomainGrant");
        java.lang.reflect.Constructor<?> k = null;
        for (java.lang.reflect.Constructor<?> c : Class.forName(
                "org.apache.river.api.security.DigestDomainGrant").getDeclaredConstructors()) {
            Class<?>[] p = c.getParameterTypes();
            if (p.length == 4 && p[0] == WeakReference.class && p[1] == List.class) {
                k = c;
            }
        }
        Assume.assumeNotNull(k);
        k.setAccessible(true);
        WeakReference<ClassLoader> ref = new WeakReference<ClassLoader>(loader);
        Permission[] perms = {PERM};
        List<CodeSource> plain = new ArrayList<CodeSource>(
                Arrays.asList(new CodeSource(jarA, (Certificate[]) null)));
        assertRefused(NullPointerException.class, "loader", k, null, plain, perms);
        assertRefused(IllegalArgumentException.class, "no digest code sources", k, ref,
                new ArrayList<CodeSource>(), perms);
        assertRefused(IllegalArgumentException.class, "no digest code sources", k, ref, null, perms);
        try {
            k.newInstance(ref, plain, NONE, perms);
            fail("a CodeSource that is not a DigestCodeSource");
        } catch (java.lang.reflect.InvocationTargetException e) {
            assertEquals(IllegalArgumentException.class, e.getCause().getClass());
        }
    }

    private static void assertRefused(Class<? extends Exception> expected, String message,
            java.lang.reflect.Constructor<?> k, WeakReference<ClassLoader> loader,
            List<CodeSource> sources, Permission[] perms) throws Exception {
        try {
            k.newInstance(loader, sources, NONE, perms);
            fail("expected " + expected.getName());
        } catch (java.lang.reflect.InvocationTargetException e) {
            assertEquals(expected, e.getCause().getClass());
            assertEquals(message, e.getCause().getMessage());
        }
    }

    // ---- helpers -----------------------------------------------------------

    /** Isolated from the test classes; can define classes with any CodeSource. */
    private static class Loader extends URLClassLoader {
        Loader(URL[] urls) {
            super(urls, ClassLoader.getPlatformClassLoader());
        }

        void addUrl(URL u) {
            addURL(u);
        }

        Class<?> define(Class<?> c, CodeSource cs) throws Exception {
            byte[] b;
            try (InputStream in = DigestDomainGrantTest.class.getClassLoader().getResourceAsStream(
                    c.getName().replace('.', '/') + ".class")) {
                b = in.readAllBytes();
            }
            return defineClass(c.getName(), b, 0, b.length, 
                    new ProtectionDomain(cs, new Permissions(), this, null));
        }
    }

    private static final class FakeCertificate extends Certificate {
        private static final long serialVersionUID = 1L;
        private final byte[] encoded;
        FakeCertificate(String id) {
            super("fake");
            encoded = id.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        }
        @Override public byte[] getEncoded() { return encoded.clone(); }
        @Override public void verify(PublicKey key) { }
        @Override public void verify(PublicKey key, String sigProvider) { }
        @Override public String toString() { return "FakeCertificate"; }
        @Override public PublicKey getPublicKey() { return null; }
    }

    private static final class EmptyPolicy extends Policy {
        @Override public PermissionCollection getPermissions(CodeSource cs) { return new Permissions(); }
        @Override public PermissionCollection getPermissions(ProtectionDomain d) { return new Permissions(); }
        @Override public boolean implies(ProtectionDomain d, Permission p) { return false; }
        @Override public void refresh() { }
    }
}
