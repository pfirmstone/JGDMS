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

package net.jini.loader.pref;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.security.CodeSource;
import java.security.Permission;
import java.security.PermissionCollection;
import java.security.Permissions;
import java.security.Policy;
import java.security.Principal;
import java.security.ProtectionDomain;
import java.security.cert.Certificate;
import java.util.Arrays;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import net.jini.security.policy.DynamicPolicyProvider;
import org.apache.river.api.security.PermissionGrant;
import org.apache.river.api.security.PermissionGrantBuilder;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 * T4 with a real {@link PreferredClassLoader}: a dynamic grant to a class of a
 * multi jar codebase applies to each jar's domain, including a jar whose 
 * first class is loaded after the grant, and to no other domain.
 */
public class PreferredClassLoaderCodebaseGrantTest {

    private static final Principal[] NONE = new Principal[0];
    private static final Permission PERM = new RuntimePermission("preferred.codebase.grant");
    private static final String A = "codebasetest.ProxyA";
    private static final String B = "codebasetest.ProxyB";

    private File dir;
    private URL jarA;
    private URL jarB;

    @Before
    public void setUp() throws Exception {
        dir = Files.createTempDirectory("pcl-codebase").toFile();
        jarA = jar("a.jar", A);
        jarB = jar("b.jar", B);
    }

    @After
    public void tearDown() {
        for (File f : dir.listFiles()) f.delete();
        dir.delete();
    }

    /** A jar containing one trivial, empty class, named by name. */
    private URL jar(String file, String name) throws Exception {
        File f = new File(dir, file);
        try (JarOutputStream out = new JarOutputStream(new FileOutputStream(f))) {
            out.putNextEntry(new JarEntry(name.replace('.', '/') + ".class"));
            out.write(emptyClass(name));
            out.closeEntry();
        }
        return f.toURI().toURL();
    }

    // Class file for: public class <name> {}  (no constructor, version 52).
    private static byte[] emptyClass(String name) throws Exception {
        java.io.ByteArrayOutputStream bo = new java.io.ByteArrayOutputStream();
        java.io.DataOutputStream o = new java.io.DataOutputStream(bo);
        String internal = name.replace('.', '/');
        o.writeInt(0xCAFEBABE);
        o.writeShort(0);
        o.writeShort(52);
        o.writeShort(5);                  // constant pool count
        o.writeByte(1); o.writeUTF(internal);          // #1 Utf8
        o.writeByte(7); o.writeShort(1);               // #2 Class
        o.writeByte(1); o.writeUTF("java/lang/Object"); // #3 Utf8
        o.writeByte(7); o.writeShort(3);               // #4 Class
        o.writeShort(0x0021);             // public super
        o.writeShort(2);                  // this
        o.writeShort(4);                  // super
        o.writeShort(0);                  // interfaces
        o.writeShort(0);                  // fields
        o.writeShort(0);                  // methods
        o.writeShort(0);                  // attributes
        return bo.toByteArray();
    }

    private PreferredClassLoader newLoader() {
        return new PreferredClassLoader(new URL[]{jarA, jarB}, 
                ClassLoader.getPlatformClassLoader(), null, false);
    }

    private static PermissionGrant grantTo(Class<?> c) {
        return PermissionGrantBuilder.newBuilder()
                .principals(NONE).permissions(new Permission[]{PERM})
                .context(PermissionGrantBuilder.CODEBASE_DOMAIN)
                .clazz(c).build();
    }

    @Test
    public void everyJarOfTheCodebaseIsCovered() throws Exception {
        PreferredClassLoader l = newLoader();
        Class<?> a = l.loadClass(A);
        PermissionGrant g = grantTo(a);
        assertTrue(g.implies(a.getProtectionDomain()));

        Class<?> b = l.loadClass(B); // after the grant
        assertNotSame(a.getProtectionDomain(), b.getProtectionDomain());
        assertTrue("jar B, loaded after the grant", g.implies(b.getProtectionDomain()));

        ProtectionDomain outside = new ProtectionDomain(
                new CodeSource(new File(dir, "outside.jar").toURI().toURL(), (Certificate[]) null), 
                null, l, NONE);
        assertFalse("location outside the codebase", g.implies(outside));

        PreferredClassLoader other = newLoader();
        assertFalse("second loader over identical jars", 
                g.implies(other.loadClass(A).getProtectionDomain()));
        assertFalse(g.implies(other.loadClass(B).getProtectionDomain()));
    }

    @Test
    public void dynamicPolicyProviderGrantReachesEveryJar() throws Exception {
        PreferredClassLoader l = newLoader();
        Class<?> a = l.loadClass(A);
        DynamicPolicyProvider policy = new DynamicPolicyProvider(new Policy() {
            @Override public PermissionCollection getPermissions(CodeSource cs) { return new Permissions(); }
            @Override public PermissionCollection getPermissions(ProtectionDomain d) { return new Permissions(); }
            @Override public boolean implies(ProtectionDomain d, Permission p) { return false; }
            @Override public void refresh() { }
        });
        policy.grant(a, NONE, new Permission[]{PERM});
        Class<?> b = l.loadClass(B);
        assertTrue(Arrays.asList(policy.getGrants(a, NONE)).contains(PERM));
        assertTrue(Arrays.asList(policy.getGrants(b, NONE)).contains(PERM));
        assertTrue(policy.implies(b.getProtectionDomain(), PERM));
    }
}
