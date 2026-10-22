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
import java.util.Arrays;
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

/**
 * {@link PermissionGrantBuilder#DIGEST_DOMAIN} grants apply to the domains of
 * verified code, identified by location, signers and content digest, belonging
 * to one ClassLoader, whether or not the domain exists yet, and to no other.
 * <p>
 * Needs {@code java.security.DigestCodeSource} (DirtyChai), which is reached 
 * by reflection; on a JDK that does not have the constructors needed the 
 * positive cases are skipped and the fail-closed cases run.
 * <p>
 * The grant classes tested are those of this module, which the surefire
 * argLine patches into {@code java.base} over any copy the JDK bundles, so
 * they are not in this test's runtime package: the package-private ones, 
 * {@code DigestDomainGrant}, are reached by name, not by reference.
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

    /** Skips the test unless the DigestCodeSource has what the test needs. */
    private static void assumeDigest() {
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

    /** DigestDomainGrant is package-private, in java.base when it is patched in. */
    private static boolean isDigestDomainGrant(PermissionGrant g) throws Exception {
        return Class.forName("org.apache.river.api.security.DigestDomainGrant").isInstance(g);
    }

    private PermissionGrant grantTo(Class<?> c, CodeSource[] codebase, Principal... pals) {
        return grantTo(PERM, c, codebase, pals);
    }

    private PermissionGrant grantTo(Permission perm, Class<?> c, CodeSource[] codebase, Principal... pals) {
        return PermissionGrantBuilder.newBuilder()
                .principals(pals)
                .permissions(new Permission[]{perm})
                .codeSources(codebase)
                .context(PermissionGrantBuilder.DIGEST_DOMAIN)
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
        Class<?> a = loader.define(ProxyA.class, new CodeSource(jarA, (Certificate[]) null));
        PermissionGrantBuilder b = PermissionGrantBuilder.newBuilder()
                .principals(NONE).permissions(new Permission[]{PERM})
                .context(PermissionGrantBuilder.DIGEST_DOMAIN);
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
                    .context(PermissionGrantBuilder.DIGEST_DOMAIN).build();
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
