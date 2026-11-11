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
package net.jini.jeri;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInput;
import java.io.ObjectOutputStream;
import java.security.ProtectionDomain;
import javax.security.auth.Subject;
import org.apache.river.api.io.AtomicMarshalOutputStream;
import org.junit.Assert;
import org.junit.Test;

/**
 * Invariant: the reducing-context block a client sends (marshalling version 0x02, sent when a
 * SecurityManager is installed) is read by the PRODUCTION JOSS reader whatever its length. The
 * block's size depends on codebase URL lengths and domain order, so this test fixes them: three
 * URL domains of about 100 characters make a primitive run well over 255 bytes, which the
 * reader refused before the top-level block bound was added. The existing
 * {@code RemoteContextCodecJavaBaseExclusionTest} reads with a plain JDK stream and so cannot
 * see this.
 */
public class RemoteContextCodecProductionReaderTest {

    // Kind discriminator mirrors RemoteContextCodec (as in RemoteContextCodecJavaBaseExclusionTest).
    private static final byte KIND_URL = 2;

    private static String url(int i) {
        StringBuilder sb = new StringBuilder("http://host").append(i).append(".example.org/");
        while (sb.length() < 96) sb.append('a');
        return sb.append(i).append(".jar").toString();
    }

    @Test
    public void largeContextBlockIsReadByTheProductionReader() throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ObjectOutputStream out = new AtomicMarshalOutputStream(baos, null);
        out.writeInt(3);
        for (int i = 0; i < 3; i++) {
            out.writeByte(KIND_URL);
            out.writeUTF(url(i));
            out.writeInt(0); // no certs
        }
        out.flush();
        byte[] wire = baos.toByteArray();
        // 4 + 3 * (1 + 2 + ~100 + 4) is about 325 bytes of primitive data in one block.
        Assert.assertTrue("fixture must exceed the 255-byte short-block limit: " + wire.length,
                wire.length > 255 + 4);

        // The same factory the dispatcher uses for the side-band block.
        ObjectInput in = new AtomicReducingContextCodec().createMarshalInputStream(
                new ByteArrayInputStream(wire), null, false, null);
        ProtectionDomain[] domains = RemoteContextCodec.unmarshal(in, new Subject());

        Assert.assertEquals(3, domains.length);
        for (int i = 0; i < 3; i++) {
            Assert.assertEquals(url(i), domains[i].getCodeSource().getLocation().toExternalForm());
        }
    }
}
