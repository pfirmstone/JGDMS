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
import java.io.DataOutputStream;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Principal;
import java.security.Signature;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import javax.security.auth.Subject;
import javax.security.auth.x500.X500Principal;
import net.jini.security.AssertPrincipalPermission;
import net.jini.security.jwt.DefaultJwtVerifier;
import net.jini.security.jwt.JwtPrincipal;
import net.jini.security.jwt.JwtRawToken;
import net.jini.security.jwt.JwtVerificationException;
import net.jini.security.jwt.JwtVerifier;
import net.jini.security.jwt.VerifiedJwt;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 * The user principals of a call must be backed by evidence the server holds.
 * Invariants defended: (1) the JwtPrincipals of a user Subject are exactly
 * those derived from its verified tokens, never the ones the sender wrote;
 * (2) any other wire principal is accepted only if the policy grants
 * {@link AssertPrincipalPermission} to the authenticated peer, by default not;
 * (3) a wire SpiffePrincipal never enters a user Subject.  Each refusal is
 * tested with the evidence that would otherwise have allowed the call (a valid
 * token of another user, a permitted peer) so a refusal cannot be caused by
 * anything but the rule under test.
 */
public class JwtPrincipalBindingTest {

    private static final String ISS = "https://issuer.test";
    private static final String AUD = "svc-a";
    private static final String X500 = "javax.security.auth.x500.X500Principal";
    private static final X500Principal ALICE_X500 = new X500Principal("CN=alice");
    private static final X500Principal BOB_X500 = new X500Principal("CN=bob");

    private static KeyPair rsa;
    private static DefaultJwtVerifier real;

    @BeforeClass
    public static void keys() throws Exception {
        KeyPairGenerator g = KeyPairGenerator.getInstance("RSA");
        g.initialize(2048);
        rsa = g.generateKeyPair();
        real = new DefaultJwtVerifier(ISS, AUD, "RS256", Map.of("k1", rsa.getPublic()));
    }

    @Before
    @After
    public void reset() {
        BasicInvocationDispatcher.setJwtVerifier(null);
    }

    // ---- helpers ------------------------------------------------------------

    private static String b64(String s) {
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(s.getBytes(StandardCharsets.UTF_8));
    }

    private static String token(String sub, String... groups) throws Exception {
        StringBuilder g = new StringBuilder();
        for (String x : groups) g.append(g.length() == 0 ? "" : ",").append('"').append(x).append('"');
        String payload = "{\"sub\":\"" + sub + "\",\"iss\":\"" + ISS + "\",\"aud\":\"" + AUD
                + "\",\"exp\":" + (System.currentTimeMillis() / 1000L + 600)
                + (groups.length > 0 ? ",\"groups\":[" + g + "]" : "") + "}";
        String si = b64("{\"alg\":\"RS256\",\"kid\":\"k1\"}") + "." + b64(payload);
        Signature s = Signature.getInstance("SHA256withRSA");
        s.initSign(rsa.getPrivate());
        s.update(si.getBytes(StandardCharsets.US_ASCII));
        return si + "." + Base64.getUrlEncoder().withoutPadding().encodeToString(s.sign());
    }

    private static Subject subject(Set<Principal> principals, String... tokens) {
        Set<Object> creds = new LinkedHashSet<>();
        for (String t : tokens) creds.add(new JwtRawToken(t));
        return new Subject(true, principals, creds, Collections.emptySet());
    }

    private static Set<Principal> set(Principal... ps) {
        Set<Principal> s = new LinkedHashSet<>();
        Collections.addAll(s, ps);
        return s;
    }

    private static byte[] wire(Subject... subjects) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        BasicInvocationHandler.writeUserSubjects(baos, subjects);
        return baos.toByteArray();
    }

    @SuppressWarnings("unchecked")
    private static List<Subject> read(byte[] bytes, Subject peer) throws Throwable {
        Method m = BasicInvocationDispatcher.class
                .getDeclaredMethod("readUserSubjects", java.io.InputStream.class, Subject.class);
        m.setAccessible(true);
        try {
            return (List<Subject>) m.invoke(null, new ByteArrayInputStream(bytes), peer);
        } catch (InvocationTargetException e) {
            throw e.getCause();
        }
    }

    private static IOException refused(byte[] bytes, Subject peer, String why) throws Throwable {
        List<Subject> got;
        try {
            got = read(bytes, peer);
        } catch (IOException expected) {
            return expected;
        } catch (Throwable wrong) {
            Assert.fail(why + ": threw " + wrong + " instead of IOException");
            return null;
        }
        Assert.fail(why + ": was accepted with principals " + got.get(0).getPrincipals());
        return null;
    }

    private static Set<Principal> principalsOf(List<Subject> subjects) {
        Assert.assertEquals(1, subjects.size());
        return subjects.get(0).getPrincipals();
    }

    private static AssertPrincipalPermission allow(String cls, String name) {
        return new AssertPrincipalPermission(cls, name);
    }

    /** The wire bytes of one Subject carrying one principal of an arbitrary class name and no token. */
    private static byte[] rawPrincipalWire(String className, String name) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(baos);
        out.writeShort(1);
        out.writeShort(1);
        out.writeUTF(className);
        out.writeUTF(name);
        out.writeByte(0);
        return baos.toByteArray();
    }

    // ---- principals are bound to the token ---------------------------------

    @Test
    public void bobsValidTokenCannotCarryAlicesPrincipals() throws Throwable {
        BasicInvocationDispatcher.setJwtVerifier(real);
        Subject forged = subject(set(new JwtPrincipal("sub:alice@example.org"),
                new JwtPrincipal("group:administrators")), token("bob@example.org"));
        // The control: bob's token alone is accepted, and gives bob only.
        Set<Principal> bob = principalsOf(read(
                wire(subject(set(), token("bob@example.org"))), null));
        Assert.assertEquals(set(new JwtPrincipal("sub:bob@example.org")), bob);
        refused(wire(forged), null, "bob's token, alice's principals");
        // Not even a peer allowed to assert everything may assert a JwtPrincipal.
        try (PeerPolicyFixture f = new PeerPolicyFixture(allow("*", "*"))) {
            refused(wire(forged), PeerPolicyFixture.peer(), "bob's token, alice's principals, wildcard peer");
        }
    }

    @Test
    public void wireJwtPrincipalOutsideTheDerivedSetRefusesEvenIfOthersAreInIt() throws Throwable {
        BasicInvocationDispatcher.setJwtVerifier(real);
        Subject s = subject(set(new JwtPrincipal("sub:bob@example.org"),
                new JwtPrincipal("group:administrators")), token("bob@example.org", "users"));
        refused(wire(s), null, "bob + group he does not have");
    }

    @Test
    public void principalsFromNoTokenAreRefusedWhenThePeerLacksThePermission() throws Throwable {
        BasicInvocationDispatcher.setJwtVerifier(real);
        byte[] w = wire(subject(set(ALICE_X500)));
        try (PeerPolicyFixture f = new PeerPolicyFixture()) {
            refused(w, PeerPolicyFixture.peer(), "no token, X500 principal, peer without the permission");
            refused(wire(subject(set(new JwtPrincipal("group:administrators")))), PeerPolicyFixture.peer(),
                    "no token, JwtPrincipal");
        }
    }

    @Test
    public void principalsWithNoTokenAndNoVerifierAreRefused() throws Throwable {
        // no verifier installed; no peer
        refused(wire(subject(set(ALICE_X500))), null, "no peer");
        refused(wire(subject(set(new JwtPrincipal("sub:alice")))), null, "no peer, JwtPrincipal");
        // no verifier installed; a peer allowed everything: X500 passes, JwtPrincipal still does not
        try (PeerPolicyFixture f = new PeerPolicyFixture(allow("*", "*"))) {
            Assert.assertEquals(set(ALICE_X500),
                    principalsOf(read(wire(subject(set(ALICE_X500))), PeerPolicyFixture.peer())));
            refused(wire(subject(set(new JwtPrincipal("sub:alice")))), PeerPolicyFixture.peer(),
                    "no verifier, no token, JwtPrincipal, wildcard peer");
        }
    }

    // ---- the permission -----------------------------------------------------

    @Test
    public void peerWithThePermissionMayAssertTheGrantedPrincipalAndNotAnotherOne() throws Throwable {
        try (PeerPolicyFixture f = new PeerPolicyFixture(allow(X500, "CN=alice"))) {
            Subject peer = PeerPolicyFixture.peer();
            Assert.assertEquals(set(ALICE_X500), principalsOf(read(wire(subject(set(ALICE_X500))), peer)));
            refused(wire(subject(set(BOB_X500))), peer, "ungranted X500 principal");
            refused(wire(subject(set(ALICE_X500, BOB_X500))), peer, "granted and ungranted together");
            // the grant is for X500Principal; the same name as another class is not covered
            refused(rawPrincipalWire("javax.security.auth.kerberos.KerberosPrincipal", "alice@EXAMPLE.ORG"),
                    peer, "other principal class");
        }
    }

    @Test
    public void permissionIsScopedByNamePattern() throws Throwable {
        try (PeerPolicyFixture f = new PeerPolicyFixture(allow(X500, "CN=staff-*"))) {
            Subject peer = PeerPolicyFixture.peer();
            X500Principal in = new X500Principal("CN=staff-7");
            Assert.assertEquals(set(in), principalsOf(read(wire(subject(set(in))), peer)));
            refused(wire(subject(set(new X500Principal("CN=customer-7")))), peer, "outside the pattern");
        }
    }

    @Test
    public void onlyTheAuthenticatedPeerCountsNotTheUserNorAnUnknownPeer() throws Throwable {
        try (PeerPolicyFixture f = new PeerPolicyFixture(allow("*", "*"))) {
            // a peer whose principals the policy does not know
            Subject stranger = new Subject(true, Collections.<Principal>singleton(new X500Principal("CN=stranger")),
                    Collections.emptySet(), Collections.emptySet());
            refused(wire(subject(set(ALICE_X500))), stranger, "peer without a grant");
            // no authenticated peer at all
            refused(wire(subject(set(ALICE_X500))), null, "null peer");
            // a peer with no principals
            Subject anon = new Subject(true, Collections.<Principal>emptySet(),
                    Collections.emptySet(), Collections.emptySet());
            refused(wire(subject(set(ALICE_X500))), anon, "peer with no principals");
        }
    }

    @Test
    public void wireSpiffePrincipalInAUserSubjectIsRefusedEvenWithAWildcardGrant() throws Throwable {
        String spiffe = "au.net.zeus.jgdms.spiffe.SpiffePrincipal";
        try (PeerPolicyFixture f = new PeerPolicyFixture(allow("*", "*"), allow(spiffe, "*"))) {
            IOException e = refused(rawPrincipalWire(spiffe, "spiffe://example.org/ns/prod/sa/payments"),
                    PeerPolicyFixture.peer(), "SpiffePrincipal with wildcard grants");
            Assert.assertTrue(e.getMessage(), e.getMessage().contains("SpiffePrincipal"));
            // control: the same peer and grant do admit a non-workload principal
            Assert.assertEquals(set(ALICE_X500),
                    principalsOf(read(wire(subject(set(ALICE_X500))), PeerPolicyFixture.peer())));
        }
    }

    @Test
    public void permittedWirePrincipalsAndDerivedPrincipalsCombine() throws Throwable {
        BasicInvocationDispatcher.setJwtVerifier(real);
        try (PeerPolicyFixture f = new PeerPolicyFixture(allow(X500, "CN=alice"))) {
            Subject s = subject(set(ALICE_X500, new JwtPrincipal("sub:bob@example.org")),
                    token("bob@example.org"));
            Assert.assertEquals(set(new JwtPrincipal("sub:bob@example.org"), ALICE_X500),
                    new LinkedHashSet<>(principalsOf(read(wire(s), PeerPolicyFixture.peer()))));
        }
    }

    // ---- derived set, cache, verifier swap ----------------------------------

    @Test
    public void derivedPrincipalsEqualTheTokensSubjectAndGroupsWhateverTheSenderWrote() throws Throwable {
        BasicInvocationDispatcher.setJwtVerifier(real);
        Set<Principal> expected = set(new JwtPrincipal("sub:carol"),
                new JwtPrincipal("group:ops"), new JwtPrincipal("group:dev"));
        String t = token("carol", "ops", "dev");
        // the client sends nothing, a subset, or all: the result is the derived set
        Assert.assertEquals(expected, principalsOf(read(wire(subject(set(), t)), null)));
        Assert.assertEquals(expected, principalsOf(read(
                wire(subject(set(new JwtPrincipal("group:ops")), t)), null)));
        Assert.assertEquals(expected, principalsOf(read(
                wire(subject(new LinkedHashSet<>(expected), t)), null)));
    }

    @Test
    public void cacheHitReturnsTheDerivedPrincipalsAndNeverTheWireOnes() throws Throwable {
        AtomicInteger calls = new AtomicInteger();
        BasicInvocationDispatcher.setJwtVerifier(raw -> {
            calls.incrementAndGet();
            return real.verify(raw);
        });
        String t = token("dana", "ops");
        Set<Principal> expected = set(new JwtPrincipal("sub:dana"), new JwtPrincipal("group:ops"));
        Assert.assertEquals(expected, principalsOf(read(wire(subject(set(), t)), null)));
        Assert.assertEquals(expected, principalsOf(read(wire(subject(set(), t)), null)));
        Assert.assertEquals("second presentation served from the cache", 1, calls.get());
        // a cache hit must not let a forged wire principal ride along
        refused(wire(subject(set(new JwtPrincipal("group:administrators")), t)), null,
                "cache hit with a forged extra principal");
        Assert.assertEquals(1, calls.get());
    }

    @Test
    public void swappingTheVerifierDropsTheCacheAndTheDerivedSet() throws Throwable {
        String t = token("erin");
        BasicInvocationDispatcher.setJwtVerifier(real);
        Assert.assertEquals(set(new JwtPrincipal("sub:erin")), principalsOf(read(wire(subject(set(), t)), null)));
        // B accepts the same string but says it is someone else
        BasicInvocationDispatcher.setJwtVerifier(raw ->
                new VerifiedJwt("frank", ISS, null, real.verify(raw).getExpiry(), null, null));
        Assert.assertEquals(set(new JwtPrincipal("sub:frank")), principalsOf(read(wire(subject(set(), t)), null)));
        refused(wire(subject(set(new JwtPrincipal("sub:erin")), t)), null,
                "erin's principal after the verifier that derived it was replaced");
        // C rejects it: the entry accepted under B must not be served
        BasicInvocationDispatcher.setJwtVerifier(raw -> { throw new JwtVerificationException("no"); });
        refused(wire(subject(set(), t)), null, "after C rejects");
    }

    @Test
    public void aVerifierThatReturnsNothingIsARefusal() throws Throwable {
        BasicInvocationDispatcher.setJwtVerifier(raw -> null);
        refused(wire(subject(set(), token("gail"))), null, "null result");
    }

    @Test
    public void aClaimsOnlyVerifierGivesNoJwtPrincipalAtAll() throws Throwable {
        BasicInvocationDispatcher.setJwtVerifier(new DefaultJwtVerifier(ISS, AUD));
        String t = token("hank");
        // the token is accepted as a token, but it confers no identity
        Assert.assertTrue(principalsOf(read(wire(subject(set(), t)), null)).isEmpty());
        refused(wire(subject(set(new JwtPrincipal("sub:hank")), t)), null, "claims-only verifier");
    }

    @Test
    public void twoTokensOfOneSubjectContributeTheirUnion() throws Throwable {
        BasicInvocationDispatcher.setJwtVerifier(real);
        Set<Principal> got = new LinkedHashSet<>(principalsOf(read(
                wire(subject(set(), token("ivy"), token("ivy", "ops"))), null)));
        Assert.assertEquals(set(new JwtPrincipal("sub:ivy"), new JwtPrincipal("group:ops")), got);
        // tokens of other Subjects do not leak across Subjects
        List<Subject> two = read(wire(subject(set(), token("ivy")), subject(set(), token("jon"))), null);
        Assert.assertEquals(set(new JwtPrincipal("sub:ivy")), two.get(0).getPrincipals());
        Assert.assertEquals(set(new JwtPrincipal("sub:jon")), two.get(1).getPrincipals());
        refused(wire(subject(set(), token("ivy")), subject(set(new JwtPrincipal("sub:ivy")), token("jon"))), null,
                "second Subject borrowing the first Subject's identity");
    }
}
