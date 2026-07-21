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
package org.apache.river.outrigger;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.io.IOException;
import net.jini.config.ConfigurationException;
import net.jini.core.constraint.InvocationConstraints;
import net.jini.export.Exporter;
import net.jini.jeri.AtomicILFactory;
import net.jini.jeri.BasicJeriExporter;
import net.jini.jeri.Endpoint;
import net.jini.jeri.ServerEndpoint;
import org.apache.river.outrigger.proxy.OutriggerServer;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.*;

/**
 * DER-only configuration rejections
 * ({@code SOW-Outrigger-DER-Only-JOSS-Rejection.md} sec.3 item 1 +
 * sec.9.1): the retained {@code useDerForEntries} entry fails loudly at
 * startup if set to anything but {@code true} (never silently ignored),
 * and the best-effort deployer {@code serverExporter} override check
 * refuses an introspectable {@code BasicJeriExporter} whose invocation
 * layer factory is not an {@code AtomicDerILFactory}.
 */
public class DerOnlyConfigRejectTest {

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    /**
     * A ServerEndpoint stand-in: never listened on -- the exporter check
     * refuses at init(), long before export. (TcpServerEndpoint cannot be
     * used here: its class initialization requires an SM-capable JVM.)
     */
    public static final class FakeServerEndpoint implements ServerEndpoint {
        public InvocationConstraints checkConstraints(InvocationConstraints c) {
            return InvocationConstraints.EMPTY;
        }
        public Endpoint enumerateListenEndpoints(ListenContext ctx)
                throws IOException {
            throw new UnsupportedOperationException("never exported");
        }
    }

    /** Called from the configuration file: a stale JOSS-IL exporter. */
    public static Exporter jossIlExporter() {
        return new BasicJeriExporter(new FakeServerEndpoint(),
            new AtomicILFactory(null, null,
                OutriggerServer.class.getClassLoader()),
            false, true);
    }

    private String writeConfig(String body) throws Exception {
        File f = tmp.newFile("outrigger-config-reject.config");
        Files.write(f.toPath(), body.getBytes(StandardCharsets.UTF_8));
        return f.getAbsolutePath();
    }

    @Test
    public void useDerForEntriesFalseIsRefusedLoudly() throws Exception {
        String config = writeConfig(
            "org.apache.river.outrigger {\n"
            + "    useDerForEntries = false;\n"
            + "    initialLookupGroups = new String[]{};\n"
            + "}\n");
        TransientOutriggerImpl wrapper =
            new TransientOutriggerImpl(new String[]{config}, null);
        try {
            wrapper.start();
            fail("useDerForEntries=false must be refused, never silently "
                + "ignored");
        } catch (ConfigurationException expected) {
            assertTrue("refusal must be actionable and name the entry",
                expected.getMessage().contains("useDerForEntries"));
            assertTrue("refusal must name the DER-only regime",
                expected.getMessage().contains("ATOMIC_DER"));
        }
    }

    @Test
    public void nonDerBasicJeriExporterIsRefusedLoudly() throws Exception {
        String config = writeConfig(
            "import org.apache.river.outrigger.DerOnlyConfigRejectTest;\n"
            + "import net.jini.export.Exporter;\n"
            + "org.apache.river.outrigger {\n"
            + "    serverExporter = DerOnlyConfigRejectTest.jossIlExporter();\n"
            + "    initialLookupGroups = new String[]{};\n"
            + "}\n");
        TransientOutriggerImpl wrapper =
            new TransientOutriggerImpl(new String[]{config}, null);
        try {
            wrapper.start();
            fail("a deployer serverExporter with a non-DER invocation "
                + "layer must be refused where introspectable");
        } catch (ConfigurationException expected) {
            assertTrue("refusal must name the required factory",
                expected.getMessage().contains("AtomicDerILFactory"));
        }
    }
}
