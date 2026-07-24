/*
 * Copyright 2026 peter.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package net.jini.space;

import java.rmi.Remote;
import java.rmi.RemoteException;
import net.jini.export.Exporter;
import net.jini.jeri.AtomicDerILFactory;
import net.jini.jeri.BasicJeriExporter;
import net.jini.jeri.tcp.TcpServerEndpoint;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

/**
 * End-to-end DER fault round-trip for {@link InternalSpaceException} over a
 * REAL exported {@code AtomicDerILFactory} endpoint (U1b finding 8a,
 * fix/der-throwable-marshalling).
 *
 * <p>{@code InternalSpaceException} is the JOSS-fence poster child: its
 * {@code writeObject}/{@code readObject} throw {@code NotSerializableException}
 * in any java.io graph, and it deliberately carries no {@code @AtomicSerial}
 * annotation. The {@code DerThrowableForm} carrier never java.io-serializes the
 * original -- capture reads only getters -- so the fence must not interfere,
 * and the {@code (String, Throwable)} constructor-matching rebuild must restore
 * the public {@link InternalSpaceException#nestedException} field (set by that
 * constructor) along with the cause chain.
 */
public class InternalSpaceExceptionDerFaultRoundTripTest {

    /** Remote contract throwing the fenced exception. */
    public interface SpaceLike extends Remote {
        void poke() throws RemoteException;
    }

    public static final class SpaceLikeImpl implements SpaceLike {
        @Override
        public void poke() {
            throw new InternalSpaceException("space internal failure",
                    new IllegalStateException("log corrupt"));
        }
    }

    private Exporter exporter;
    /** STRONG ref: the export table holds the impl weakly (DGC disabled). */
    private SpaceLikeImpl impl;
    private SpaceLike proxy;

    @Before
    public void setUp() throws Exception {
        exporter = new BasicJeriExporter(
                TcpServerEndpoint.getInstance(0),
                new AtomicDerILFactory(null, null,
                        InternalSpaceExceptionDerFaultRoundTripTest.class.getClassLoader()),
                false, true);
        impl = new SpaceLikeImpl();
        proxy = (SpaceLike) exporter.export(impl);
    }

    @After
    public void tearDown() {
        if (exporter != null) exporter.unexport(true);
    }

    @Test
    public void internalSpaceExceptionSurfacesTypedWithNestedException() throws Exception {
        try {
            proxy.poke();
            Assert.fail("expected InternalSpaceException");
        } catch (InternalSpaceException e) {
            Assert.assertEquals("space internal failure", e.getMessage());
            Assert.assertNotNull("nestedException must be restored by the"
                    + " (String, Throwable) constructor match", e.nestedException);
            Assert.assertTrue("nestedException type must survive, got "
                    + e.nestedException,
                    e.nestedException instanceof IllegalStateException);
            Assert.assertEquals("log corrupt", e.nestedException.getMessage());
            Assert.assertSame("getCause() and nestedException are set by the same"
                    + " constructor", e.nestedException, e.getCause());
        } catch (RemoteException e) {
            Assert.fail("the fault degraded to a generic remote failure -- the"
                    + " pre-carrier abort behaviour: " + e);
        }
    }
}
