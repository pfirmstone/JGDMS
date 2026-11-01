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
package net.jini.security.jwt;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Principal;
import java.security.Signature;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 * Claims handling of {@link DefaultJwtVerifier} in signature mode.  Invariants
 * defended: only top-level members are read; {@code aud} is matched as whole
 * elements; the audience and issuer are mandatory; a token cannot live longer
 * than the configured maximum; {@code typ}, when present, is JWT; and the
 * principals of an accepted token are exactly the ones its signed claims
 * name, with none for a verifier that checked no signature.  The tokens are
 * validly signed, so a refusal is due to the claim under test.
 */
public class DefaultJwtVerifierClaimsTest {

    private static final String ISS = "https://issuer.test";
    private static final String AUD = "svc-a";
    private static KeyPair rsa;
    private static DefaultJwtVerifier v;

    @BeforeClass
    public static void keys() throws Exception {
        KeyPairGenerator g = KeyPairGenerator.getInstance("RSA");
        g.initialize(2048);
        rsa = g.generateKeyPair();
        v = new DefaultJwtVerifier(ISS, AUD, "RS256", Map.of("k1", rsa.getPublic()));
    }

    private static String b64(String s) {
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(s.getBytes(StandardCharsets.UTF_8));
    }

    private static long now() { return System.currentTimeMillis() / 1000L; }

    private static String token(String header, String payload) throws Exception {
        String si = b64(header) + "." + b64(payload);
        Signature s = Signature.getInstance("SHA256withRSA");
        s.initSign(rsa.getPrivate());
        s.update(si.getBytes(StandardCharsets.US_ASCII));
        return si + "." + Base64.getUrlEncoder().withoutPadding().encodeToString(s.sign());
    }

    private static String token(String payload) throws Exception {
        return token("{\"alg\":\"RS256\",\"kid\":\"k1\"}", payload);
    }

    private static String payload(String aud, String exp, String extra) {
        return "{\"sub\":\"alice\",\"iss\":\"" + ISS + "\",\"aud\":" + aud
                + ",\"exp\":" + exp + (extra.isEmpty() ? "" : "," + extra) + "}";
    }

    private static void rejected(DefaultJwtVerifier ver, String token, String why) {
        try {
            ver.verify(token);
        } catch (JwtVerificationException expected) {
            return;
        }
        Assert.fail(why + ": token was accepted");
    }

    // ---- nested members are not lifted -------------------------------------

    @Test
    public void nestedAudienceIsNotLiftedToTheTopLevel() throws Exception {
        // top-level aud is for another service; a nested object carries ours
        String p = "{\"sub\":\"a\",\"iss\":\"" + ISS + "\",\"aud\":\"svc-b\",\"exp\":" + (now() + 600)
                + ",\"profile\":{\"n\":1,\"aud\":\"svc-a\"}}";
        rejected(v, token(p), "nested aud");
    }

    @Test
    public void nestedExpiryDoesNotOverrideAnExpiredTopLevelExpiry() throws Exception {
        String p = "{\"sub\":\"a\",\"iss\":\"" + ISS + "\",\"aud\":\"svc-a\",\"exp\":" + (now() - 3600)
                + ",\"profile\":{\"n\":1,\"exp\":" + (now() + 3600) + "}}";
        rejected(v, token(p), "nested exp");
    }

    @Test
    public void nestedSubjectAndGroupsAreNotDerivedAsPrincipals() throws Exception {
        String p = payload("\"svc-a\"", String.valueOf(now() + 600),
                "\"profile\":{\"sub\":\"mallory\",\"groups\":[\"admins\"]}");
        VerifiedJwt j = v.verify(token(p));
        Assert.assertEquals("alice", j.getSubject());
        Assert.assertEquals(Set.<Principal>of(new JwtPrincipal("sub:alice")), j.getPrincipals());
    }

    @Test
    public void duplicateTopLevelMemberIsRejected() throws Exception {
        String p = "{\"sub\":\"a\",\"iss\":\"" + ISS + "\",\"aud\":\"svc-b\",\"aud\":\"svc-a\",\"exp\":"
                + (now() + 600) + "}";
        rejected(v, token(p), "duplicate aud");
    }

    // ---- audience -----------------------------------------------------------

    @Test
    public void commaJoinedAudienceDoesNotMatchAnElement() throws Exception {
        rejected(v, token(payload("\"evil,svc-a\"", String.valueOf(now() + 600), "")),
                "aud 'evil,svc-a'");
        rejected(v, token(payload("\"evil, svc-a\"", String.valueOf(now() + 600), "")),
                "aud 'evil, svc-a'");
    }

    @Test
    public void audienceArrayMatchesByWholeElement() throws Exception {
        String exp = String.valueOf(now() + 600);
        Assert.assertEquals(List.of("other", "svc-a"),
                v.verify(token(payload("[\"other\",\"svc-a\"]", exp, ""))).getAudience());
        rejected(v, token(payload("[\"other\",\"svc-ab\"]", exp, "")), "array without our element");
        rejected(v, token(payload("[\"other,svc-a\"]", exp, "")), "array element with a comma");
        rejected(v, token(payload("[\"svc-a\",5]", exp, "")), "non-string element");
    }

    @Test
    public void scalarAudienceIsAListOfOne() throws Exception {
        Assert.assertEquals(List.of("svc-a"),
                v.verify(token(payload("\"svc-a\"", String.valueOf(now() + 600), ""))).getAudience());
    }

    // ---- construction -------------------------------------------------------

    @Test(expected = IllegalArgumentException.class)
    public void signatureModeRefusesNullAudience() {
        new DefaultJwtVerifier(ISS, null, "RS256", Map.of("k1", rsa.getPublic()));
    }

    @Test(expected = IllegalArgumentException.class)
    public void signatureModeRefusesEmptyAudience() {
        new DefaultJwtVerifier(ISS, "", "RS256", Map.of("k1", rsa.getPublic()));
    }

    @Test(expected = IllegalArgumentException.class)
    public void signatureModeRefusesNullIssuer() {
        new DefaultJwtVerifier(null, AUD, "RS256", Map.of("k1", rsa.getPublic()));
    }

    @Test(expected = IllegalArgumentException.class)
    public void nonPositiveMaximumLifetimeIsRefused() {
        new DefaultJwtVerifier(ISS, AUD, "RS256", Map.of("k1", rsa.getPublic()), Duration.ZERO);
    }

    // ---- lifetime -----------------------------------------------------------

    @Test
    public void expiryBeyondTheMaximumLifetimeIsRefused() throws Exception {
        // operator ruling 2026-11-02: the default maximum lifetime is 1 hour.
        rejected(v, token(payload("\"svc-a\"", String.valueOf(now() + 2 * 3600), "")),
                "exp 2 h away, default 1 h");
        rejected(v, token(payload("\"svc-a\"", String.valueOf(now() + 3600 + 120), "")),
                "exp 1 h 2 min away, default 1 h");
        rejected(v, token(payload("\"svc-a\"", "253402300799", "")), "exp in year 9999");
        v.verify(token(payload("\"svc-a\"", String.valueOf(now() + 3500), "")));
    }

    @Test
    public void maximumLifetimeIsConfigurable() throws Exception {
        DefaultJwtVerifier short1 = new DefaultJwtVerifier(ISS, AUD, "RS256",
                Map.of("k1", rsa.getPublic()), Duration.ofMinutes(10));
        rejected(short1, token(payload("\"svc-a\"", String.valueOf(now() + 3600), "")), "1 h > 10 min");
        short1.verify(token(payload("\"svc-a\"", String.valueOf(now() + 300), "")));
    }

    @Test
    public void expGivenAsAStringIsRefused() throws Exception {
        rejected(v, token(payload("\"svc-a\"", "\"" + (now() + 600) + "\"", "")), "string exp");
    }

    @Test
    public void absurdExponentExpiryIsRefusedNotAllocated() throws Exception {
        rejected(v, token(payload("\"svc-a\"", "1e999999999", "")), "1e999999999");
    }

    // ---- typ ----------------------------------------------------------------

    @Test
    public void typHeaderMustBeJwtWhenPresent() throws Exception {
        String p = payload("\"svc-a\"", String.valueOf(now() + 600), "");
        rejected(v, token("{\"alg\":\"RS256\",\"kid\":\"k1\",\"typ\":\"refresh+jwt\"}", p), "typ refresh+jwt");
        rejected(v, token("{\"alg\":\"RS256\",\"kid\":\"k1\",\"typ\":5}", p), "typ number");
        v.verify(token("{\"alg\":\"RS256\",\"kid\":\"k1\",\"typ\":\"jwt\"}", p));
        v.verify(token("{\"alg\":\"RS256\",\"kid\":\"k1\",\"typ\":\"JWT\"}", p));
        v.verify(token("{\"alg\":\"RS256\",\"kid\":\"k1\"}", p));
    }

    // ---- derived principals ------------------------------------------------

    @Test
    public void derivedPrincipalsAreExactlyTheSignedSubjectEmailAndGroups() throws Exception {
        String p = payload("\"svc-a\"", String.valueOf(now() + 600),
                "\"email\":\"alice@example.org\",\"email_verified\":true,\"groups\":[\"admins\",\"users\"]");
        VerifiedJwt j = v.verify(token(p));
        Assert.assertEquals(Set.<Principal>of(
                new JwtPrincipal("sub:alice"),
                new JwtPrincipal("email:alice@example.org"),
                new JwtPrincipal("group:admins"),
                new JwtPrincipal("group:users")), j.getPrincipals());
        Assert.assertEquals(ISS, j.getIssuer());
        Assert.assertEquals(List.of("admins", "users"), j.getGroups());
        Assert.assertNotNull(j.getExpiry());
    }

    @Test
    public void claimsOnlyVerifierYieldsNoPrincipals() throws Exception {
        DefaultJwtVerifier claimsOnly = new DefaultJwtVerifier(ISS, AUD);
        VerifiedJwt j = claimsOnly.verify(token(payload("\"svc-a\"", String.valueOf(now() + 600),
                "\"groups\":[\"admins\"]")));
        Assert.assertEquals("alice", j.getSubject());
        Assert.assertTrue("a token nobody authenticated gives no identity", j.getPrincipals().isEmpty());
    }

    // ---- email needs email_verified == JSON true ---------------------------

    private static Set<Principal> principalsWith(String extra) throws Exception {
        return v.verify(token(payload("\"svc-a\"", String.valueOf(now() + 600), extra))).getPrincipals();
    }

    @Test
    public void emailPrincipalOnlyWhenEmailVerifiedIsJsonTrue() throws Exception {
        String e = "\"email\":\"ceo@corp.example\"";
        JwtPrincipal email = new JwtPrincipal("email:ceo@corp.example");
        Assert.assertTrue(principalsWith(e + ",\"email_verified\":true").contains(email));
        Assert.assertFalse(principalsWith(e + ",\"email_verified\":false").contains(email));
        Assert.assertFalse(principalsWith(e).contains(email));
        Assert.assertFalse(principalsWith(e + ",\"email_verified\":\"true\"").contains(email));
        Assert.assertFalse(principalsWith(e + ",\"email_verified\":1").contains(email));
        Assert.assertFalse(principalsWith(e + ",\"email_verified\":null").contains(email));
        // the token is still accepted, with the sub principal and the unverified email on the claims
        VerifiedJwt j = v.verify(token(payload("\"svc-a\"", String.valueOf(now() + 600),
                e + ",\"email_verified\":false")));
        Assert.assertEquals("ceo@corp.example", j.getEmail());
        Assert.assertEquals(Set.<Principal>of(new JwtPrincipal("sub:alice")), j.getPrincipals());
    }

    // ---- empty names, principal cap ---------------------------------------

    @Test
    public void emptySubAndEmptyGroupAreRefused() throws Exception {
        long exp = now() + 600;
        rejected(v, token("{\"sub\":\"\",\"iss\":\"" + ISS + "\",\"aud\":\"svc-a\",\"exp\":" + exp + "}"), "empty sub");
        rejected(v, token(payload("\"svc-a\"", String.valueOf(exp), "\"groups\":[\"ok\",\"\"]")), "empty group");
        rejected(v, token(payload("\"svc-a\"", String.valueOf(exp),
                "\"email\":\"\",\"email_verified\":true")), "empty email");
        // controls: the same tokens with non-empty values are accepted
        Assert.assertEquals(2, principalsWith("\"groups\":[\"ok\"]").size());
    }

    private static String groups(int n) {
        StringBuilder g = new StringBuilder("\"groups\":[");
        for (int i = 0; i < n; i++) g.append(i == 0 ? "" : ",").append("\"g").append(i).append('"');
        return g.append(']').toString();
    }

    @Test
    public void defaultCapOf64PrincipalsIsEnforced() throws Exception {
        // sub + 63 groups = 64: accepted; sub + 64 groups = 65: refused
        Assert.assertEquals(64, principalsWith(groups(63)).size());
        long exp = now() + 600;
        rejected(v, token(payload("\"svc-a\"", String.valueOf(exp), groups(64))), "65 principals");
        rejected(v, token(payload("\"svc-a\"", String.valueOf(exp), groups(4000))), "4001 principals");
    }

    // ---- strict JSON (RFC 8259) -------------------------------------------

    private static String withExp(String expLiteral) {
        return "{\"sub\":\"alice\",\"iss\":\"" + ISS + "\",\"aud\":\"svc-a\",\"exp\":" + expLiteral + "}";
    }

    @Test
    public void numbersFollowTheJsonGrammar() throws Exception {
        long e = now() + 600;
        v.verify(token(withExp(String.valueOf(e))));
        v.verify(token(withExp(e + ".0")));
        v.verify(token(withExp(e + "e0")));
        v.verify(token(withExp(e + "E+0")));
        rejected(v, token(withExp("+" + e)), "+exp");
        rejected(v, token(withExp("0" + e)), "leading zero");
        rejected(v, token(withExp("00")), "00");
        rejected(v, token(withExp(e + ".")), "trailing dot");
        rejected(v, token(withExp("." + e)), "leading dot");
        rejected(v, token(withExp(e + "e")), "empty exponent");
        rejected(v, token(withExp(e + "e+")), "sign-only exponent");
        rejected(v, token(withExp("--" + e)), "double minus");
        rejected(v, token(withExp("0x10")), "hex");
    }

    private static String withSub(String rawSubLiteral) {
        return "{\"sub\":" + rawSubLiteral + ",\"iss\":\"" + ISS + "\",\"aud\":\"svc-a\",\"exp\":"
                + (now() + 600) + "}";
    }

    @Test
    public void unicodeEscapesAreFourHexDigitsAndSurrogatesPair() throws Exception {
        Assert.assertEquals("A", v.verify(token(withSub("\"\\u0041\""))).getSubject());
        Assert.assertEquals("\ud83d\ude00", v.verify(token(withSub("\"\\ud83d\\ude00\""))).getSubject());
        rejected(v, token(withSub("\"\\u+041\"")), "plus sign in \\u escape");
        rejected(v, token(withSub("\"\\u-041\"")), "minus sign in \\u escape");
        rejected(v, token(withSub("\"\\u00g1\"")), "non-hex in \\u escape");
        rejected(v, token(withSub("\"\\u00\"")), "short \\u escape");
        rejected(v, token(withSub("\"\\ud800\"")), "lone high surrogate");
        rejected(v, token(withSub("\"\\udc00\"")), "lone low surrogate");
        rejected(v, token(withSub("\"\\ud800x\"")), "high surrogate then ordinary char");
        rejected(v, token(withSub("\"\\ud800\\u0041\"")), "high surrogate then non-low escape");
        rejected(v, token(withSub("\"\\ude00\\ud83d\"")), "reversed pair");
    }

    @Test
    public void nullCritHeaderIsRefusedLikeAnyCrit() throws Exception {
        String p = payload("\"svc-a\"", String.valueOf(now() + 600), "");
        rejected(v, token("{\"alg\":\"RS256\",\"kid\":\"k1\",\"crit\":null}", p), "crit:null");
        rejected(v, token("{\"alg\":\"RS256\",\"kid\":\"k1\",\"crit\":[\"exp\"]}", p), "crit array");
        v.verify(token("{\"alg\":\"RS256\",\"kid\":\"k1\"}", p));
    }

    @Test(expected = UnsupportedOperationException.class)
    public void derivedPrincipalSetIsImmutable() throws Exception {
        v.verify(token(payload("\"svc-a\"", String.valueOf(now() + 600), "")))
                .getPrincipals().add(new JwtPrincipal("group:admins"));
    }
}
