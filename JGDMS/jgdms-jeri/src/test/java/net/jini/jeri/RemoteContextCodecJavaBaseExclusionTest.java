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

package net.jini.jeri;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.security.ProtectionDomain;
import javax.security.auth.Subject;
import org.junit.Assert;
import org.junit.Test;

/**
 * Verifies that {@link RemoteContextCodec} refuses to reconstruct the platform
 * {@code jrt:/java.base} domain from the wire.  That domain is always fully
 * privileged ({@code AllPermission}), carries native code, and is unstamped, so
 * reconstructing it with the remote worker principals would inherit the receiver's
 * unconditional platform grant -- a privilege escalation, not a reduction.  A genuine
 * codebase-less reducer (null CodeSource) must still be preserved.
 *
 * <p>Exercises only the decoder (the security boundary against a malicious or pre-fix
 * peer), so it needs neither DirtyChai nor a SecurityManager and runs on a stock JDK.
 */
public class RemoteContextCodecJavaBaseExclusionTest {

    // Kind discriminator bytes mirror RemoteContextCodec.
    private static final byte KIND_NULL_CS = 0;
    private static final byte KIND_URL     = 2;

    @Test
    public void javaBaseDomainIsDroppedOnReceive() throws Exception {
        byte[] wire = wireOf((out) -> {
            out.writeInt(2);
            // Domain 0: the platform module -- MUST be dropped.
            out.writeByte(KIND_URL);
            out.writeUTF("jrt:/java.base");
            out.writeInt(0); // no certs
            // Domain 1: an application codebase -- MUST survive.
            out.writeByte(KIND_URL);
            out.writeUTF("http://host/app.jar");
            out.writeInt(0); // no certs
        });

        ProtectionDomain[] domains = unmarshal(wire);

        Assert.assertEquals("jrt:/java.base must be dropped, app codebase retained",
                1, domains.length);
        Assert.assertNotNull(domains[0].getCodeSource());
        Assert.assertEquals("http://host/app.jar",
                domains[0].getCodeSource().getLocation().toExternalForm());
    }

    @Test
    public void javaBaseSubmoduleClassUrlIsAlsoDropped() throws Exception {
        byte[] wire = wireOf((out) -> {
            out.writeInt(1);
            out.writeByte(KIND_URL);
            out.writeUTF("jrt:/java.base/java/lang/Object.class");
            out.writeInt(0);
        });

        Assert.assertEquals("any jrt:/java.base URL must be dropped",
                0, unmarshal(wire).length);
    }

    @Test
    public void nullCodeSourceReducerIsRetained() throws Exception {
        byte[] wire = wireOf((out) -> {
            out.writeInt(1);
            out.writeByte(KIND_NULL_CS);
        });

        ProtectionDomain[] domains = unmarshal(wire);
        Assert.assertEquals(1, domains.length);
        Assert.assertNull("a genuine null-CodeSource reducer must be preserved",
                domains[0].getCodeSource());
    }

    @Test
    public void otherJrtModuleIsRetained() throws Exception {
        byte[] wire = wireOf((out) -> {
            out.writeInt(1);
            out.writeByte(KIND_URL);
            out.writeUTF("jrt:/java.sql");
            out.writeInt(0);
        });

        ProtectionDomain[] domains = unmarshal(wire);
        Assert.assertEquals("other jrt: modules are retained", 1, domains.length);
        Assert.assertEquals("jrt:/java.sql",
                domains[0].getCodeSource().getLocation().toExternalForm());
    }

    // --- helpers ---

    private interface Writer { void write(ObjectOutputStream out) throws Exception; }

    private static byte[] wireOf(Writer w) throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ObjectOutputStream out = new ObjectOutputStream(baos);
        w.write(out);
        out.flush();
        return baos.toByteArray();
    }

    private static ProtectionDomain[] unmarshal(byte[] wire) throws Exception {
        ObjectInputStream in =
                new ObjectInputStream(new ByteArrayInputStream(wire));
        return RemoteContextCodec.unmarshal(in, new Subject());
    }
}
