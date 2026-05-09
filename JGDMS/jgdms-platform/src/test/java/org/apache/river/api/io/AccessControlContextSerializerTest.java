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

package org.apache.river.api.io;

import java.net.URL;
import java.net.URLStreamHandler;
import java.security.AccessControlContext;
import java.security.CodeSource;
import java.security.ProtectionDomain;
import javax.security.auth.Subject;
import javax.security.auth.x500.X500Principal;
import org.junit.Assert;
import org.junit.Test;

public class AccessControlContextSerializerTest {

    @Test
    public void testMarshalUnmarshalFiltersAndUsesAuthenticatedSubject() throws Exception {
        String oldHandlers = System.getProperty("java.protocol.handler.pkgs");
        System.setProperty("java.protocol.handler.pkgs", "net.jini.url");
        try {
            URL httpmd = new URL(null,
                    "httpmd://repo.example.org/client-stub.jar;sha-256=0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
                    new PassThroughHandler());
            URL plain = new URL("http://repo.example.org/client-stub.jar");
            ProtectionDomain httpmdPd = new ProtectionDomain(
                    new CodeSource(httpmd, (java.security.cert.Certificate[]) null),
                    null, null, new java.security.Principal[0]);
            ProtectionDomain plainPd = new ProtectionDomain(
                    new CodeSource(plain, (java.security.cert.Certificate[]) null),
                    null, null, new java.security.Principal[0]);
            AccessControlContext acc = new AccessControlContext(new ProtectionDomain[]{httpmdPd, plainPd});

            byte[] encoded = AccessControlContextSerializer.marshalForTransport(acc);
            Assert.assertTrue(encoded.length > 0);
            Assert.assertEquals(1, readInt(encoded));

            Subject authenticated = new Subject();
            authenticated.getPrincipals().add(new X500Principal("CN=worker"));
            AccessControlContext reconstructed =
                    AccessControlContextSerializer.unmarshalForTransport(encoded, authenticated);
            byte[] reencoded = AccessControlContextSerializer.marshalForTransport(reconstructed);
            Assert.assertEquals(1, readInt(reencoded));
        } finally {
            if (oldHandlers == null) {
                System.clearProperty("java.protocol.handler.pkgs");
            } else {
                System.setProperty("java.protocol.handler.pkgs", oldHandlers);
            }
        }
    }

    @Test
    public void testAtomicMarshalRoundTripForAccessControlContext() throws Exception {
        String oldHandlers = System.getProperty("java.protocol.handler.pkgs");
        System.setProperty("java.protocol.handler.pkgs", "net.jini.url");
        try {
            URL httpmd = new URL(null,
                    "httpmd://repo.example.org/client-stub.jar;sha-256=0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
                    new PassThroughHandler());
            ProtectionDomain pd = new ProtectionDomain(
                    new CodeSource(httpmd, (java.security.cert.Certificate[]) null),
                    null, null, new java.security.Principal[0]);
            AccessControlContext original = new AccessControlContext(new ProtectionDomain[]{pd});

            java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
            java.io.ObjectOutputStream out = new AtomicMarshalOutputStream(baos, null);
            out.writeObject(original);
            out.flush();

            java.io.ByteArrayInputStream bais = new java.io.ByteArrayInputStream(baos.toByteArray());
            java.io.ObjectInputStream in = AtomicMarshalInputStream.create(
                    bais, null, false, null, null, false);
            Object result = in.readObject();
            Assert.assertTrue(result instanceof AccessControlContext);
        } finally {
            if (oldHandlers == null) {
                System.clearProperty("java.protocol.handler.pkgs");
            } else {
                System.setProperty("java.protocol.handler.pkgs", oldHandlers);
            }
        }
    }
    
    @Test
    public void testDomainIdentityRejectsStandardSerialization() throws Exception {
        URL httpmd = new URL(null,
                "httpmd://repo.example.org/client-stub.jar;sha-256=0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
                new PassThroughHandler());
        AccessControlContextSerializer.DomainIdentity domain = new AccessControlContextSerializer.DomainIdentity(
                new CodeSource(httpmd, (java.security.cert.Certificate[]) null),
                new java.security.Principal[0]);
        java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
        java.io.ObjectOutputStream out = new java.io.ObjectOutputStream(baos);
        try {
            out.writeObject(domain);
            Assert.fail("Expected NotSerializableException");
        } catch (java.io.NotSerializableException expected) {
            // expected
        } finally {
            out.close();
        }
    }

    private static int readInt(byte[] bytes) {
        return ((bytes[0] & 0xFF) << 24)
                | ((bytes[1] & 0xFF) << 16)
                | ((bytes[2] & 0xFF) << 8)
                | (bytes[3] & 0xFF);
    }

    private static final class PassThroughHandler extends URLStreamHandler {
        @Override
        protected java.net.URLConnection openConnection(URL u) {
            throw new UnsupportedOperationException();
        }
    }
}
