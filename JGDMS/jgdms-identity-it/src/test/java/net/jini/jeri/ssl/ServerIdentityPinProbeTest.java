/*
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

package net.jini.jeri.ssl;

import java.io.IOException;
import java.security.Principal;
import javax.security.auth.Subject;
import javax.security.auth.x500.X500Principal;
import net.jini.constraint.BasicMethodConstraints;
import net.jini.core.constraint.ClientAuthentication;
import net.jini.core.constraint.ClientMinPrincipal;
import net.jini.core.constraint.Confidentiality;
import net.jini.core.constraint.Integrity;
import net.jini.core.constraint.InvocationConstraint;
import net.jini.core.constraint.InvocationConstraints;
import net.jini.core.constraint.RemoteMethodControl;
import net.jini.core.constraint.ServerAuthentication;
import net.jini.core.constraint.ServerMinPrincipal;
import net.jini.export.CodebaseAccessor;
import net.jini.io.UnsupportedConstraintException;
import net.jini.loader.pref.ServerIdentityPinAccess;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 * The codebase provider's {@code ServerIdentityPin}: the probe of a bootstrap
 * proxy's own connection, and the pin, over real SSL and TCP JERI with
 * {@code AtomicILFactory} exports (the AtomicInvocationHandler).  The client's
 * identity is the ambient SPIFFE worker, as deployed ({@link AmbientIdentityFixture});
 * the servers run in their own JVM.
 */
public class ServerIdentityPinProbeTest {

    private static CodebaseAccessor accessor, otherAccessor, tcpAccessor;

    @BeforeClass
    public static void setUp() throws Exception {
        AmbientIdentityFixture.start();
        accessor = (CodebaseAccessor) AmbientIdentityFixture.proxy("atomicReggie");
        otherAccessor = (CodebaseAccessor) AmbientIdentityFixture.proxy("atomicOther");
        tcpAccessor = (CodebaseAccessor) AmbientIdentityFixture.proxy("atomicTcp");
    }

    @Before
    public void noSubjectInScope() {
        assertNull("the workload identity is ambient: no Subject is in scope", Subject.current());
    }

    private static boolean spiffeAvailable() {
        try {
            Class.forName("au.net.zeus.jgdms.spiffe.SpiffePrincipal", false, ClassLoader.getSystemClassLoader());
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    @Test
    public void thePinProbeReturnsTheAuthenticatedIdentity() throws Exception {
        Principal p = ServerIdentityPinAccess.probe(accessor);
        if (spiffeAvailable()) {
            assertEquals("spiffe://example.org/reggie", p.getName());
        } else {
            assertEquals("without the SPIFFE class the identity is the X.500 name",
                new X500Principal("CN=Reggie"), p);
        }
    }

    @Test
    public void thePinProbeRefusesAConstraintTheClientCannotMeetInsteadOfTreatingItAsAnonymous() throws Exception {
        CodebaseAccessor demanding = (CodebaseAccessor) ((RemoteMethodControl) accessor).setConstraints(
            new BasicMethodConstraints(new InvocationConstraints(
                new InvocationConstraint[]{ClientAuthentication.YES, ServerAuthentication.YES,
                    Integrity.YES, Confidentiality.YES,
                    new ClientMinPrincipal(new X500Principal("CN=Nobody"))}, null)));
        try {
            ServerIdentityPinAccess.probe(demanding);
            fail("an authenticating transport whose handshake cannot be made must refuse");
        } catch (UnsupportedConstraintException expected) {
        }
    }

    @Test
    public void thePinProbeGivesNoIdentityForATransportThatCannotAuthenticate() throws Exception {
        assertNull(ServerIdentityPinAccess.probe(tcpAccessor));
    }

    @Test
    public void aPinnedProxyStillWorksAndAnotherServerCannotAnswerForIt() throws Exception {
        Principal p = ServerIdentityPinAccess.probe(accessor);
        CodebaseAccessor pinned = ServerIdentityPinAccess.pin(accessor, p);
        assertEquals(IdentityServers.PROBE_CODEBASE, pinned.getClassAnnotation());
        assertEquals("without the pin the other server answers (control)", "other",
            otherAccessor.getCertFactoryType());
        CodebaseAccessor wrong = ServerIdentityPinAccess.pin(otherAccessor, p);
        try {
            wrong.getClassAnnotation();
            fail("a server other than the pinned one must not answer");
        } catch (IOException expected) {
        }
    }

    @Test
    public void aProxyThatAlreadyRequiresTheServerPrincipalCanBeProbedAndPinned() throws Exception {
        CodebaseAccessor constrained = (CodebaseAccessor) ((RemoteMethodControl) accessor).setConstraints(
            new BasicMethodConstraints(new InvocationConstraints(
                new InvocationConstraint[]{ClientAuthentication.YES, ServerAuthentication.YES,
                    Integrity.YES, Confidentiality.YES, new ServerMinPrincipal(new X500Principal("CN=Reggie"))},
                null)));
        Principal p = ServerIdentityPinAccess.probe(constrained);
        CodebaseAccessor pinned = ServerIdentityPinAccess.pin(constrained, p);
        // With the SPIFFE class present the pin is the SPIFFE ID and replaces the X.500
        // requirement: one connection cannot satisfy two ServerMinPrincipal requirements.
        assertEquals(IdentityServers.PROBE_CODEBASE, pinned.getClassAnnotation());
    }
}
