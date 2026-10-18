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
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.ObjectInputStream;
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
import net.jini.security.policy.DynamicPolicyProvider;
import org.apache.river.api.security.codebase.IfaceA;
import org.apache.river.api.security.codebase.Outside;
import org.apache.river.api.security.codebase.ProxyA;
import org.apache.river.api.security.codebase.ProxyB;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;
import tests.support.MyPrincipal;

/**
 * A dynamic grant made through {@link PermissionGrantBuilder#CODEBASE_DOMAIN}
 * applies to each ProtectionDomain of the codebase of the granted class's 
 * ClassLoader, whether or not the domain exists yet, and to no other.
 */
public class CodebaseDomainGrantTest {

    private static final Principal[] NONE = new Principal[0];
    private static final Permission PERM = new RuntimePermission("codebase.domain.grant");

    private File dir;
    private URL jarA;
    private URL jarB;
    private URL jarOutside;
    private Loader loader;

    @Before
    public void setUp() throws Exception {
        dir = Files.createTempDirectory("codebase-domain").toFile();
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
             InputStream in = CodebaseDomainGrantTest.class.getClassLoader()
                     .getResourceAsStream(res)) {
            out.putNextEntry(new JarEntry(res));
            byte[] buf = new byte[4096];
            for (int n; (n = in.read(buf)) > 0;) out.write(buf, 0, n);
            out.closeEntry();
        }
        return f.toURI().toURL();
    }

    private static PermissionGrant grantTo(Class<?> c, Principal... pals) {
        return PermissionGrantBuilder.newBuilder()
                .principals(pals)
                .permissions(new Permission[]{PERM})
                .context(PermissionGrantBuilder.CODEBASE_DOMAIN)
                .clazz(c)
                .build();
    }

    // ---- T4: multi jar codebase -------------------------------------------

    @Test
    public void multiJarCodebase() throws Exception {
        Class<?> a = loader.loadClass(ProxyA.class.getName());
        assertSame("isolated loader", loader, a.getClassLoader());
        PermissionGrant g = grantTo(a);
        assertTrue(g instanceof CodebaseDomainGrant);

        assertTrue("A", g.implies(a.getProtectionDomain()));

        // B has no domain yet when the grant is made.
        Class<?> b = loader.loadClass(ProxyB.class.getName());
        assertNotSame(a.getProtectionDomain(), b.getProtectionDomain());
        assertTrue("B, loaded after the grant", g.implies(b.getProtectionDomain()));

        // Same loader, a location outside the codebase.
        Class<?> out = loader.define(Outside.class, jarOutside, null);
        assertSame(loader, out.getClassLoader());
        assertFalse("outside the codebase", g.implies(out.getProtectionDomain()));

        // A second loader over identical jars.
        try (Loader other = new Loader(new URL[]{jarA, jarB})) {
            Class<?> a2 = other.loadClass(ProxyA.class.getName());
            Class<?> b2 = other.loadClass(ProxyB.class.getName());
            assertFalse("second loader A", g.implies(a2.getProtectionDomain()));
            assertFalse("second loader B", g.implies(b2.getProtectionDomain()));
        }

        // A domain rebuilt by a combiner, such as SubjectDomainCombiner.
        Principal p = new MyPrincipal("rebuilt");
        ProtectionDomain pa = a.getProtectionDomain();
        ProtectionDomain rebuilt = new ProtectionDomain(pa.getCodeSource(), 
                pa.getPermissions(), pa.getClassLoader(), new Principal[]{p});
        assertTrue("rebuilt A", g.implies(rebuilt));
        ProtectionDomain pb = b.getProtectionDomain();
        assertTrue("rebuilt B", g.implies(new ProtectionDomain(pb.getCodeSource(), 
                pb.getPermissions(), pb.getClassLoader(), new Principal[]{p})));
    }

    @Test
    public void domainCreatedAfterGrantIsCovered() throws Exception {
        Class<?> a = loader.loadClass(ProxyA.class.getName());
        PermissionGrant g = grantTo(a);
        // Not loaded at all, but a domain for B's location is checked.
        ProtectionDomain b = new ProtectionDomain(new CodeSource(jarB, (Certificate[]) null), 
                null, loader, NONE);
        assertTrue(g.implies(b));
    }

    @Test
    public void codeSourceIdentityIsPinned() throws Exception {
        Class<?> a = loader.loadClass(ProxyA.class.getName());
        Class<?> b = loader.loadClass(ProxyB.class.getName());
        PermissionGrant g = grantTo(a);
        assertTrue(g.implies(b.getProtectionDomain())); // pins B.
        Certificate signer = new FakeCertificate("mallory");
        ProtectionDomain signedB = new ProtectionDomain(
                new CodeSource(jarB, new Certificate[]{signer}), null, loader, NONE);
        assertFalse("same location, different signers", g.implies(signedB));
        ProtectionDomain signedA = new ProtectionDomain(
                new CodeSource(jarA, new Certificate[]{signer}), null, loader, NONE);
        assertFalse("anchor location, different signers", g.implies(signedA));
        assertTrue("B is still implied", g.implies(b.getProtectionDomain()));
    }

    @Test
    public void principalsAreHonoured() throws Exception {
        Class<?> a = loader.loadClass(ProxyA.class.getName());
        Principal p = new MyPrincipal("required");
        PermissionGrant g = grantTo(a, p);
        ProtectionDomain pa = a.getProtectionDomain();
        assertFalse(g.implies(pa)); // pa has no principals
        assertTrue(g.implies(new ProtectionDomain(pa.getCodeSource(), null, loader, 
                new Principal[]{p})));
    }

    @Test
    public void notImpliedByClassLoaderAlone() throws Exception {
        Class<?> a = loader.loadClass(ProxyA.class.getName());
        assertFalse(grantTo(a).implies(loader, NONE));
    }

    @Test
    public void otherLoaderFallsBackToAnchorDomainOnly() throws Exception {
        try (NotUrlLoader l = new NotUrlLoader()) {
            Class<?> a = l.define(ProxyA.class, jarA);
            Class<?> b = l.define(ProxyB.class, jarB);
            PermissionGrant g = grantTo(a);
            assertTrue(g.implies(a.getProtectionDomain()));
            assertFalse("same loader, another domain", g.implies(b.getProtectionDomain()));
        }
    }

    @Test
    public void voidAfterSerialization() throws Exception {
        Class<?> a = loader.loadClass(ProxyA.class.getName());
        PermissionGrant g = grantTo(a);
        ByteArrayOutputStream bo = new ByteArrayOutputStream();
        try (ObjectOutputStream oo = new ObjectOutputStream(bo)) {
            oo.writeObject(g);
        }
        Object o = new ObjectInputStream(new ByteArrayInputStream(bo.toByteArray())).readObject();
        assertTrue(((PermissionGrant) o).isVoid());
        assertFalse(((PermissionGrant) o).implies(a.getProtectionDomain()));
    }

    @Test
    public void proxyClassAnchorUsesTheLoaderOfTheClass() throws Exception {
        // BasicProxyPreparer grants to proxy.getClass(), a Proxy class has the
        // shared all permission domain, whose loader is null.
        URL jarI = jar("iface.jar", IfaceA.class);
        try (Loader l = new Loader(new URL[]{jarA, jarB, jarI})) {
            Class<?> iface = l.loadClass(IfaceA.class.getName());
            Object proxy = Proxy.newProxyInstance(l, new Class<?>[]{iface}, 
                    new InvocationHandler() {
                        public Object invoke(Object p, java.lang.reflect.Method m, Object[] a) {
                            return null;
                        }
                    });
            Class<?> proxyClass = proxy.getClass();
            assertNull("shared domain has no loader", proxyClass.getProtectionDomain().getClassLoader());
            PermissionGrant g = grantTo(proxyClass);
            Class<?> a = l.loadClass(ProxyA.class.getName());
            Class<?> b = l.loadClass(ProxyB.class.getName());
            assertTrue("jar A", g.implies(a.getProtectionDomain()));
            assertTrue("jar B", g.implies(b.getProtectionDomain()));
            assertTrue("interface jar", g.implies(iface.getProtectionDomain()));
            ProtectionDomain elsewhere = new ProtectionDomain(
                    new CodeSource(jarOutside, (Certificate[]) null), null, l, NONE);
            assertFalse(g.implies(elsewhere));
            try (Loader other = new Loader(new URL[]{jarA})) {
                assertFalse(g.implies(other.loadClass(ProxyA.class.getName()).getProtectionDomain()));
            }

            DynamicPolicyProvider policy = new DynamicPolicyProvider(new EmptyPolicy());
            policy.grant(proxyClass, NONE, new Permission[]{PERM});
            assertTrue("getGrants(proxy class)", 
                    Arrays.asList(policy.getGrants(proxyClass, NONE)).contains(PERM));
            assertTrue("propagates to a class of the codebase", 
                    Arrays.asList(policy.getGrants(a, NONE)).contains(PERM));
        }
    }

    @Test
    public void anchorWithoutLocationStillCoversItsRebuiltDomain() throws Exception {
        try (NotUrlLoader l = new NotUrlLoader()) {
            Class<?> a = l.define(ProxyA.class, null); // CodeSource without a location
            PermissionGrant g = grantTo(a);
            ProtectionDomain pa = a.getProtectionDomain();
            assertTrue(g.implies(pa));
            assertTrue("rebuilt by a combiner, shares the CodeSource", 
                    g.implies(new ProtectionDomain(pa.getCodeSource(), null, l, NONE)));
            DynamicPolicyProvider policy = new DynamicPolicyProvider(new EmptyPolicy());
            policy.grant(a, NONE, new Permission[]{PERM});
            assertTrue(Arrays.asList(policy.getGrants(a, NONE)).contains(PERM));
        }
    }

    @Test
    public void nullClassAppliesToAllDomains() throws Exception {
        PermissionGrant g = PermissionGrantBuilder.newBuilder()
                .principals(NONE).permissions(new Permission[]{PERM})
                .context(PermissionGrantBuilder.CODEBASE_DOMAIN).build();
        assertTrue(g.implies(getClass().getProtectionDomain()));
    }

    // ---- T5: propagation of signer grants ----------------------------------

    @Test
    public void getGrantsSeesSignedByGrants() throws Exception {
        Certificate signer = new FakeCertificate("signer");
        // A separate loader, package signers must agree within a loader.
        Class<?> signed = new Loader(new URL[0]).define(Outside.class, jarOutside, 
                new Certificate[]{signer});
        Class<?> unsigned = loader.loadClass(ProxyA.class.getName());
        DynamicPolicyProvider policy = new DynamicPolicyProvider(new EmptyPolicy());
        Permission signerPerm = new RuntimePermission("signed.by.grant");
        policy.grant(PermissionGrantBuilder.newBuilder()
                .certificates(new Certificate[]{signer}, new String[]{"signer"})
                .principals(NONE).permissions(new Permission[]{signerPerm})
                .context(PermissionGrantBuilder.CODESOURCE_CERTS).build());
        assertTrue("signedBy grant propagates", 
                Arrays.asList(policy.getGrants(signed, NONE)).contains(signerPerm));
        assertFalse("unsigned class does not receive it", 
                Arrays.asList(policy.getGrants(unsigned, NONE)).contains(signerPerm));
    }

    @Test
    public void getGrantsIsDomainScoped() throws Exception {
        Class<?> a = loader.loadClass(ProxyA.class.getName());
        Class<?> out = loader.define(Outside.class, jarOutside, null);
        DynamicPolicyProvider policy = new DynamicPolicyProvider(new EmptyPolicy());
        policy.grant(a, NONE, new Permission[]{PERM});
        assertTrue(Arrays.asList(policy.getGrants(a, NONE)).contains(PERM));
        assertFalse("outside the codebase", 
                Arrays.asList(policy.getGrants(out, NONE)).contains(PERM));
    }

    @Test
    public void getGrantsHonoursSuppliedPrincipals() throws Exception {
        Class<?> a = loader.loadClass(ProxyA.class.getName());
        Principal p = new MyPrincipal("required");
        DynamicPolicyProvider policy = new DynamicPolicyProvider(new EmptyPolicy());
        policy.grant(a, new Principal[]{p}, new Permission[]{PERM});
        assertTrue(Arrays.asList(policy.getGrants(a, new Principal[]{p})).contains(PERM));
        assertFalse(Arrays.asList(policy.getGrants(a, NONE)).contains(PERM));
        assertFalse(Arrays.asList(policy.getGrants(a, null)).contains(PERM));
    }

    // ---- helpers -----------------------------------------------------------

    /** A URLClassLoader, isolated from the test classes, that can define classes
     * from locations outside its URLs. */
    private static class Loader extends URLClassLoader {
        Loader(URL[] urls) {
            super(urls, ClassLoader.getPlatformClassLoader());
        }

        Class<?> define(Class<?> c, URL location, Certificate[] signers) throws Exception {
            byte[] b = bytes(c);
            CodeSource cs = new CodeSource(location, signers);
            return defineClass(c.getName(), b, 0, b.length, 
                    new ProtectionDomain(cs, new Permissions(), this, null));
        }
    }

    /** Not a URLClassLoader. */
    private static class NotUrlLoader extends ClassLoader implements AutoCloseable {
        NotUrlLoader() {
            super(ClassLoader.getPlatformClassLoader());
        }

        Class<?> define(Class<?> c, URL location) throws Exception {
            byte[] b = bytes(c);
            return defineClass(c.getName(), b, 0, b.length, 
                    new ProtectionDomain(new CodeSource(location, (Certificate[]) null), 
                            new Permissions(), this, null));
        }

        @Override
        public void close() { }
    }

    private static byte[] bytes(Class<?> c) throws Exception {
        try (InputStream in = CodebaseDomainGrantTest.class.getClassLoader().getResourceAsStream(
                c.getName().replace('.', '/') + ".class")) {
            return in.readAllBytes();
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
