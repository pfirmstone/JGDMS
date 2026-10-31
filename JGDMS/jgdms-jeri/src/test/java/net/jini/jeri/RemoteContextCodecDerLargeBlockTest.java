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
import java.io.ObjectOutput;
import java.security.ProtectionDomain;
import javax.security.auth.Subject;
import org.junit.Assert;
import org.junit.Test;

/**
 * Invariant: the reducing-context side-band block is read by the DER codec whatever its size, up
 * to the ceilings {@link RemoteContextCodec} itself applies (4096 domains). The JOSS reader
 * refused any top-level primitive run over 255 bytes; DER has no block framing, so it must not
 * share that limit. This sends blocks from a few hundred bytes up to the domain ceiling through
 * the production {@link DerReducingContextCodec} so the size at which {@code DerInputLimits}
 * would refuse a legitimate context, if there is one, shows up as a failure here.
 */
public class RemoteContextCodecDerLargeBlockTest {

    // Kind discriminator mirrors RemoteContextCodec (as in the other RemoteContextCodec tests).
    private static final byte KIND_URL = 2;

    private static String url(int i) {
        StringBuilder sb = new StringBuilder("http://host").append(i).append(".example.org/");
        while (sb.length() < 196) sb.append('a');
        return sb.append(i).append(".jar").toString();
    }

    private static void roundTrips(int domains) throws Exception {
        DerReducingContextCodec codec = new DerReducingContextCodec();
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ObjectOutput out = codec.createMarshalOutputStream(baos, java.util.Collections.emptyList(), null);
        out.writeInt(domains);
        for (int i = 0; i < domains; i++) {
            out.writeByte(KIND_URL);
            out.writeUTF(url(i));
            out.writeInt(0); // no certs
        }
        out.flush();
        byte[] wire = baos.toByteArray();

        ObjectInput in = codec.createMarshalInputStream(new ByteArrayInputStream(wire), null, false, null);
        ProtectionDomain[] back = RemoteContextCodec.unmarshal(in, new Subject());

        Assert.assertEquals(domains + " domains (" + wire.length + " wire bytes)", domains, back.length);
        Assert.assertEquals(url(domains - 1),
                back[domains - 1].getCodeSource().getLocation().toExternalForm());
    }

    @Test public void smallContext()     throws Exception { roundTrips(3); }

    @Test public void overJossBlockSize() throws Exception { roundTrips(10); }    // about 2 KB

    @Test public void hundredDomains()   throws Exception { roundTrips(100); }    // about 21 KB

    @Test public void thousandDomains()  throws Exception { roundTrips(1000); }   // about 210 KB

    @Test public void atTheDomainCeiling() throws Exception { roundTrips(4096); } // about 860 KB
}
