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
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Principal;
import java.security.Signature;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import javax.security.auth.Subject;
import javax.security.auth.x500.X500Principal;
import net.jini.security.jwt.DefaultJwtVerifier;
import net.jini.security.jwt.JwtRawToken;
import net.jini.security.jwt.JwtVerificationException;
import net.jini.security.jwt.VerifiedJwt;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 * The user-Subject JWT path of {@link BasicInvocationDispatcher} must fail
 * closed.  Invariants defended: a token is never accepted unless the verifier
 * installed at that moment accepted it; replacing the verifier discards what
 * the old one accepted; the cache is bounded in size and age; a malformed
 * length or a throwing verifier is a refusal, not a crash or an acceptance.
 */
public class JwtDispatcherFailClosedTest {

    private static final String ISS = "https://issuer.test";
    private static KeyPair rsa;

    @BeforeClass
    public static void keys() throws Exception {
        KeyPairGenerator g = KeyPairGenerator.getInstance("RSA");
        g.initialize(2048);
        rsa = g.generateKeyPair();
    }

    @Before
    @After
    public void reset() {
        BasicInvocationDispatcher.setJwtVerifier(null);
    }

    // ---- helpers ----------------------------------------------------------

    private static String b64(byte[] b) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(b);
    }

    private static String b64(String s) {
        return b64(s.getBytes(StandardCharsets.UTF_8));
    }

    private static long now() { return System.currentTimeMillis() / 1000L; }

    /** A token whose signature is junk: what an attacker with no key can write. */
    private static String forged(String sub, String expClaim) {
        return b64("{\"alg\":\"RS256\",\"kid\":\"k1\"}") + "."
                + b64("{\"sub\":\"" + sub + "\",\"iss\":\"" + ISS + "\",\"aud\":\"svc-a\"" + expClaim + "}")
                + ".Zm9yZ2Vk";
    }

    private static String exp(long secondsFromNow) {
        return ",\"exp\":" + (now() + secondsFromNow);
    }

    private static String signed(String sub, String expClaim) throws Exception {
        String si = b64("{\"alg\":\"RS256\",\"kid\":\"k1\"}") + "."
                + b64("{\"sub\":\"" + sub + "\",\"iss\":\"" + ISS + "\",\"aud\":\"svc-a\"" + expClaim + "}");
        Signature s = Signature.getInstance("SHA256withRSA");
        s.initSign(rsa.getPrivate());
        s.update(si.getBytes(StandardCharsets.US_ASCII));
        return si + "." + b64(s.sign());
    }

    private static byte[] wire(String jwt) throws IOException {
        Set<Object> creds = jwt == null ? Collections.emptySet()
                : Collections.singleton((Object) new JwtRawToken(jwt));
        // No wire principals: these tests are about the token path.  Principal
        // binding is covered by JwtPrincipalBindingTest.
        Subject s = new Subject(true, Collections.<Principal>emptySet(),
                creds, Collections.emptySet());
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        BasicInvocationHandler.writeUserSubjects(baos, new Subject[] {s});
        return baos.toByteArray();
    }

    @SuppressWarnings("unchecked")
    private static List<Subject> read(byte[] bytes) throws Throwable {
        Method m = BasicInvocationDispatcher.class
                .getDeclaredMethod("readUserSubjects", java.io.InputStream.class, Subject.class);
        m.setAccessible(true);
        try {
            return (List<Subject>) m.invoke(null, new ByteArrayInputStream(bytes), null);
        } catch (InvocationTargetException e) {
            throw e.getCause();
        }
    }

    /** Asserts the read is refused with an IOException (never accepted, never another type). */
    private static IOException refused(byte[] bytes, String why) throws Throwable {
        try {
            read(bytes);
        } catch (IOException expected) {
            return expected;
        } catch (Throwable wrong) {
            Assert.fail(why + ": threw " + wrong + " instead of IOException");
        }
        Assert.fail(why + ": was accepted");
        return null;
    }

    /**
     * A verifier that trusts the token: returns the claims it carries, whatever the
     * signature.  For tests of the dispatcher's own behaviour; exp may be absent.
     */
    static VerifiedJwt trusting(String jwt) {
        String json = new String(Base64.getUrlDecoder().decode(jwt.split("\\.")[1]),
                StandardCharsets.UTF_8);
        java.util.regex.Matcher e = java.util.regex.Pattern.compile("\"exp\":(\\d+)").matcher(json);
        java.util.regex.Matcher u = java.util.regex.Pattern.compile("\"sub\":\"([^\"]*)\"").matcher(json);
        return new VerifiedJwt(u.find() ? u.group(1) : null, ISS, null,
                e.find() ? Instant.ofEpochSecond(Long.parseLong(e.group(1))) : null, null, null);
    }

    @SuppressWarnings("unchecked")
    private static ConcurrentHashMap<String, Object> cache() throws Exception {
        Field sf = BasicInvocationDispatcher.class.getDeclaredField("jwtState");
        sf.setAccessible(true);
        Object state = sf.get(null);
        Field cf = state.getClass().getDeclaredField("cache");
        cf.setAccessible(true);
        return (ConcurrentHashMap<String, Object>) cf.get(state);
    }

    // ---- fail closed with no verifier -------------------------------------

    @Test
    public void tokenWithNoVerifierInstalledIsRefused() throws Throwable {
        IOException e = refused(wire(forged("mallory", exp(3600))), "no verifier");
        Assert.assertTrue(e.getMessage(), e.getMessage().contains("no JwtVerifier"));
    }

    @Test
    public void callWithNoJwtIsUnaffectedByAbsenceOfVerifier() throws Throwable {
        List<Subject> subjects = read(wire(null));
        Assert.assertEquals(1, subjects.size());
    }

    @Test
    public void forgedTokenRefusedBeforeVerifierStaysRefusedAfter() throws Throwable {
        String token = forged("mallory", exp(36000));
        refused(wire(token), "before any verifier");
        BasicInvocationDispatcher.setJwtVerifier(new DefaultJwtVerifier(
                ISS, "svc-a", "RS256", Map.of("k1", rsa.getPublic())));
        refused(wire(token), "after the real verifier is installed");
    }

    @Test
    public void tokenIsNotServedFromCacheOnceVerifierInstalled() throws Throwable {
        // The attack: pre-install acceptance on structure alone, cached to the forger's own exp.
        String token = forged("mallory", exp(36000));
        refused(wire(token), "no verifier");
        AtomicInteger calls = new AtomicInteger();
        BasicInvocationDispatcher.setJwtVerifier(jwt -> {
            calls.incrementAndGet();
            throw new JwtVerificationException("bad signature");
        });
        refused(wire(token), "rejecting verifier");
        Assert.assertEquals("the new verifier must have been asked", 1, calls.get());
        Assert.assertEquals("a refused token is never cached", 0, cache().size());
    }

    @Test
    public void tokenAcceptedUnderVerifierAIsRefusedAfterVerifierBReplacesIt() throws Throwable {
        String token = forged("bob", exp(3600));
        BasicInvocationDispatcher.setJwtVerifier(JwtDispatcherFailClosedTest::trusting);
        read(wire(token));
        AtomicInteger calls = new AtomicInteger();
        BasicInvocationDispatcher.setJwtVerifier(jwt -> {
            calls.incrementAndGet();
            throw new JwtVerificationException("B rejects");
        });
        refused(wire(token), "after replacement");
        Assert.assertEquals(1, calls.get());
    }

    @Test
    public void realVerifierAcceptsSignedAndRefusesTampered() throws Throwable {
        BasicInvocationDispatcher.setJwtVerifier(new DefaultJwtVerifier(
                ISS, "svc-a", "RS256", Map.of("k1", rsa.getPublic())));
        String good = signed("alice", exp(600));
        Assert.assertEquals(1, read(wire(good)).size());
        String[] p = good.split("\\.");
        String tampered = p[0] + "." + b64("{\"sub\":\"admin\",\"iss\":\"" + ISS + "\""
                + exp(600) + "}") + "." + p[2];
        refused(wire(tampered), "tampered payload, old signature");
    }

    // ---- other refusals ----------------------------------------------------

    @Test
    public void throwingVerifierIsARefusal() throws Throwable {
        BasicInvocationDispatcher.setJwtVerifier(jwt -> { throw new IllegalStateException("boom"); });
        refused(wire(forged("x", exp(60))), "verifier throws runtime exception");
        Assert.assertEquals(0, cache().size());
    }

    @Test
    public void emptyTokenIsRefusedEvenByPermissiveVerifier() throws Throwable {
        BasicInvocationDispatcher.setJwtVerifier(JwtDispatcherFailClosedTest::trusting);
        refused(rawJwtWire(new byte[0], 0), "empty token");
    }

    @Test
    public void negativeLengthIsAnIOExceptionNotACrash() throws Throwable {
        BasicInvocationDispatcher.setJwtVerifier(JwtDispatcherFailClosedTest::trusting);
        refused(rawJwtWire(new byte[0], -1), "length 0xFFFFFFFF");
        refused(rawJwtWire(new byte[0], Integer.MIN_VALUE), "length 0x80000000");
    }

    @Test
    public void oversizedLengthIsRefusedBeforeAllocation() throws Throwable {
        BasicInvocationDispatcher.setJwtVerifier(JwtDispatcherFailClosedTest::trusting);
        refused(rawJwtWire(new byte[0], 65_537), "length 65537");
        refused(rawJwtWire(new byte[0], Integer.MAX_VALUE), "length Integer.MAX_VALUE");
    }

    /** One Subject, no principals, one JWT whose declared length is {@code declared}. */
    private static byte[] rawJwtWire(byte[] body, int declared) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(baos);
        out.writeShort(1);
        out.writeShort(0);
        out.writeByte(1);
        out.writeInt(declared);
        out.write(body);
        return baos.toByteArray();
    }

    // ---- cache bounds ------------------------------------------------------

    @Test
    public void cacheEntryLifetimeIsCappedWhateverTheTokenClaims() throws Throwable {
        BasicInvocationDispatcher.setJwtVerifier(JwtDispatcherFailClosedTest::trusting);
        read(wire(forged("farfuture", ",\"exp\":" + (now() + 10L * 365 * 86400))));
        Assert.assertEquals(1, cache().size());
        Object entry = cache().values().iterator().next();
        Field uf = entry.getClass().getDeclaredField("until");
        uf.setAccessible(true);
        Instant until = (Instant) uf.get(entry);
        Assert.assertTrue("entry must not outlive the 300 s cap, was " + until,
                until.isBefore(Instant.now().plusSeconds(301)));
    }

    @Test
    public void cacheNeverExceedsItsSizeLimit() throws Throwable {
        BasicInvocationDispatcher.setJwtVerifier(JwtDispatcherFailClosedTest::trusting);
        for (int i = 0; i < 1500; i++) {
            read(wire(forged("u" + i, exp(3600))));
            Assert.assertTrue("size " + cache().size(), cache().size() <= 1024);
        }
    }

    @Test
    public void tokenWithoutExpOrPastExpIsNeverCached() throws Throwable {
        AtomicInteger calls = new AtomicInteger();
        BasicInvocationDispatcher.setJwtVerifier(jwt -> { calls.incrementAndGet(); return trusting(jwt); });
        byte[] noExp = wire(forged("noexp", ""));
        read(noExp);
        read(noExp);
        byte[] past = wire(forged("past", exp(-3600)));
        read(past);
        read(past);
        Assert.assertEquals("each presentation re-verified", 4, calls.get());
        Assert.assertEquals(0, cache().size());
    }

    @Test
    public void verifiedTokenIsCachedAndCacheDoesNotRetainTheToken() throws Throwable {
        AtomicInteger calls = new AtomicInteger();
        BasicInvocationDispatcher.setJwtVerifier(jwt -> { calls.incrementAndGet(); return trusting(jwt); });
        String token = forged("cached", exp(3600));
        byte[] w = wire(token);
        read(w);
        read(w);
        Assert.assertEquals(1, calls.get());
        for (String k : cache().keySet()) {
            Assert.assertFalse("cache must not hold the raw token", token.contains(k) || k.contains(token));
        }
    }

    // ---- logging -----------------------------------------------------------

    @Test
    public void refusalDoesNotLogOrReportTheToken() throws Throwable {
        final List<LogRecord> seen = new ArrayList<>();
        Handler h = new Handler() {
            public void publish(LogRecord r) { seen.add(r); }
            public void flush() { }
            public void close() { }
        };
        Logger root = Logger.getLogger("");
        java.util.logging.Level old = root.getLevel();
        root.setLevel(java.util.logging.Level.ALL);
        root.addHandler(h);
        try {
            String token = forged("leaky", exp(3600));
            String sigSegment = token.substring(token.lastIndexOf('.') + 1);
            BasicInvocationDispatcher.setJwtVerifier(new DefaultJwtVerifier(
                    ISS, "svc-a", "RS256", Map.of("k1", rsa.getPublic())));
            List<String> texts = new ArrayList<>();
            IOException e = refused(wire(token), "forged");
            texts.add(String.valueOf(e.getMessage()));
            for (Throwable c = e.getCause(); c != null; c = c.getCause()) {
                texts.add(String.valueOf(c.getMessage()));
            }
            for (LogRecord r : seen) {
                texts.add(String.valueOf(r.getMessage()));
                if (r.getParameters() != null) {
                    for (Object o : r.getParameters()) texts.add(String.valueOf(o));
                }
            }
            for (String t : texts) {
                Assert.assertFalse("leaks token: " + t, t.contains(token) || t.contains(sigSegment));
            }
        } finally {
            root.removeHandler(h);
            root.setLevel(old);
        }
    }
}
