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
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.ECGenParameterSpec;
import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 * Negative tests for {@link DefaultJwtVerifier}.  The invariant defended:
 * a token is accepted only if it carries a valid signature, by a configured
 * key, under the single pinned algorithm, and its claims are in date and for
 * this issuer and audience.  Keys are generated here; nothing is a secret.
 */
public class DefaultJwtVerifierNegativeTest {

    private static final String ISS = "https://issuer.test";
    private static final String AUD = "svc-a";

    private static KeyPair rsa, otherRsa, ec;
    private static DefaultJwtVerifier rs256;

    @BeforeClass
    public static void keys() throws Exception {
        KeyPairGenerator g = KeyPairGenerator.getInstance("RSA");
        g.initialize(2048);
        rsa = g.generateKeyPair();
        otherRsa = g.generateKeyPair();
        KeyPairGenerator e = KeyPairGenerator.getInstance("EC");
        e.initialize(new ECGenParameterSpec("secp256r1"));
        ec = e.generateKeyPair();
        rs256 = new DefaultJwtVerifier(ISS, AUD, "RS256", Map.of("k1", rsa.getPublic()));
    }

    private static String b64(String s) {
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(s.getBytes(StandardCharsets.UTF_8));
    }

    private static long now() { return System.currentTimeMillis() / 1000L; }

    private static String claims(String iss, String aud, String extra) {
        return "{\"sub\":\"alice\",\"iss\":\"" + iss + "\",\"aud\":\"" + aud + "\""
                + (extra.isEmpty() ? "" : "," + extra) + "}";
    }

    private static String goodClaims() {
        return claims(ISS, AUD, "\"exp\":" + (now() + 600));
    }

    private static String sign(String header, String payload, KeyPair kp, String jcaAlg)
            throws Exception {
        String si = b64(header) + "." + b64(payload);
        Signature s = Signature.getInstance(jcaAlg);
        s.initSign(kp.getPrivate());
        s.update(si.getBytes(StandardCharsets.US_ASCII));
        return si + "." + Base64.getUrlEncoder().withoutPadding().encodeToString(s.sign());
    }

    private static String rs(String payload) throws Exception {
        return sign("{\"alg\":\"RS256\",\"kid\":\"k1\"}", payload, rsa, "SHA256withRSA");
    }

    private static void rejected(DefaultJwtVerifier v, String token, String why) {
        try {
            v.verify(token);
        } catch (JwtVerificationException expected) {
            return;
        } catch (RuntimeException wrong) {
            Assert.fail(why + ": threw " + wrong + " instead of JwtVerificationException");
        }
        Assert.fail(why + ": token was accepted");
    }

    @Test
    public void validTokenIsAccepted() throws Exception {
        rs256.verify(rs(goodClaims()));
    }

    @Test
    public void validEs256TokenIsAccepted() throws Exception {
        DefaultJwtVerifier v = new DefaultJwtVerifier(ISS, AUD, "ES256", Map.of("e1", ec.getPublic()));
        // JCA gives DER; convert to the raw R||S that JWS carries.
        String si = b64("{\"alg\":\"ES256\",\"kid\":\"e1\"}") + "." + b64(goodClaims());
        Signature s = Signature.getInstance("SHA256withECDSA");
        s.initSign(ec.getPrivate());
        s.update(si.getBytes(StandardCharsets.US_ASCII));
        byte[] raw = derToRaw(s.sign());
        v.verify(si + "." + Base64.getUrlEncoder().withoutPadding().encodeToString(raw));
    }

    private static byte[] derToRaw(byte[] der) {
        byte[] raw = new byte[64];
        int p = 3;
        int rl = der[p++] & 0xFF;
        int ro = p; p += rl;
        p++;
        int sl = der[p++] & 0xFF;
        int so = p;
        copyRight(der, ro, rl, raw, 0);
        copyRight(der, so, sl, raw, 32);
        return raw;
    }

    private static void copyRight(byte[] src, int off, int len, byte[] dst, int dstOff) {
        while (len > 32) { off++; len--; }
        System.arraycopy(src, off, dst, dstOff + 32 - len, len);
    }

    @Test
    public void emptySignatureIsRejected() throws Exception {
        String t = rs(goodClaims());
        rejected(rs256, t.substring(0, t.lastIndexOf('.') + 1), "empty signature");
    }

    @Test
    public void garbageSignatureIsRejected() throws Exception {
        String t = rs(goodClaims());
        rejected(rs256, t.substring(0, t.lastIndexOf('.') + 1) + "AAAA", "garbage signature");
        rejected(rs256, t.substring(0, t.lastIndexOf('.') + 1) + "!!!!", "non-base64 signature");
    }

    @Test
    public void algNoneInAnyCaseIsRejected() throws Exception {
        for (String alg : new String[] {"none", "None", "NONE"}) {
            String si = b64("{\"alg\":\"" + alg + "\",\"kid\":\"k1\"}") + "." + b64(goodClaims());
            rejected(rs256, si + ".", "alg " + alg + " empty signature");
            rejected(rs256, si + ".AAAA", "alg " + alg + " with signature bytes");
        }
    }

    @Test
    public void headerAlgMustMatchEvenWhenTheSignatureIsValid() throws Exception {
        for (String alg : new String[] {"none", "HS256", "RS512"}) {
            String t = sign("{\"alg\":\"" + alg + "\",\"kid\":\"k1\"}", goodClaims(), rsa, "SHA256withRSA");
            rejected(rs256, t, "alg " + alg + " with a genuine RS256 signature");
        }
    }

    @Test
    public void hs256SignedWithRsaPublicKeyBytesIsRejected() throws Exception {
        String si = b64("{\"alg\":\"HS256\",\"kid\":\"k1\"}") + "." + b64(goodClaims());
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(rsa.getPublic().getEncoded(), "HmacSHA256"));
        String sig = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(mac.doFinal(si.getBytes(StandardCharsets.US_ASCII)));
        rejected(rs256, si + "." + sig, "algorithm confusion");
    }

    @Test
    public void tamperedPayloadWithOldSignatureIsRejected() throws Exception {
        String t = rs(goodClaims());
        String[] p = t.split("\\.");
        String forged = b64(claims(ISS, AUD, "\"exp\":" + (now() + 600)).replace("alice", "admin"));
        rejected(rs256, p[0] + "." + forged + "." + p[2], "tampered payload");
    }

    @Test
    public void signatureByAnotherKeyIsRejected() throws Exception {
        String t = sign("{\"alg\":\"RS256\",\"kid\":\"k1\"}", goodClaims(), otherRsa, "SHA256withRSA");
        rejected(rs256, t, "wrong signing key");
    }

    @Test
    public void wrongAudienceIsRejected() throws Exception {
        rejected(rs256, rs(claims(ISS, "svc-b", "\"exp\":" + (now() + 600))), "wrong audience");
    }

    @Test
    public void wrongIssuerIsRejected() throws Exception {
        rejected(rs256, rs(claims("https://evil.test", AUD, "\"exp\":" + (now() + 600))), "wrong issuer");
    }

    @Test
    public void expiredIsRejected() throws Exception {
        rejected(rs256, rs(claims(ISS, AUD, "\"exp\":" + (now() - 3600))), "expired");
    }

    @Test
    public void missingExpIsRejected() throws Exception {
        rejected(rs256, rs(claims(ISS, AUD, "")), "no exp");
    }

    @Test
    public void notYetValidIsRejected() throws Exception {
        rejected(rs256, rs(claims(ISS, AUD, "\"exp\":" + (now() + 7200) + ",\"nbf\":" + (now() + 3600))),
                "nbf in the future");
    }

    @Test
    public void absurdExpIsRejectedNotThrown() throws Exception {
        rejected(rs256, rs(claims(ISS, AUD, "\"exp\":9223372036854775807")), "exp = Long.MAX_VALUE");
    }

    @Test
    public void wrongSegmentCountIsRejected() throws Exception {
        String t = rs(goodClaims());
        String[] p = t.split("\\.");
        rejected(rs256, p[0] + "." + p[1], "two segments");
        rejected(rs256, t + ".extra", "four segments");
    }

    @Test
    public void hugeTokenIsRejected() throws Exception {
        StringBuilder sb = new StringBuilder("a.b.");
        while (sb.length() <= 65_536) sb.append('A');
        rejected(rs256, sb.toString(), "token over 65536 chars");
    }

    @Test
    public void unknownKidIsRejected() throws Exception {
        String t = sign("{\"alg\":\"RS256\",\"kid\":\"k2\"}", goodClaims(), rsa, "SHA256withRSA");
        rejected(rs256, t, "unknown kid");
    }

    @Test
    public void missingKidWithSeveralKeysIsRejected() throws Exception {
        Map<String, PublicKey> two = new HashMap<>();
        two.put("k1", rsa.getPublic());
        two.put("k2", otherRsa.getPublic());
        DefaultJwtVerifier v = new DefaultJwtVerifier(ISS, AUD, "RS256", two);
        String t = sign("{\"alg\":\"RS256\"}", goodClaims(), rsa, "SHA256withRSA");
        rejected(v, t, "no kid, two keys");
    }

    @Test
    public void critHeaderIsRejected() throws Exception {
        String t = sign("{\"alg\":\"RS256\",\"kid\":\"k1\",\"crit\":[\"x\"],\"x\":1}",
                goodClaims(), rsa, "SHA256withRSA");
        rejected(rs256, t, "crit header");
    }

    @Test
    public void claimsOnlyModeStillRequiresExp() throws Exception {
        DefaultJwtVerifier v = new DefaultJwtVerifier(ISS, AUD);
        String si = b64("{\"alg\":\"none\"}") + "." + b64(claims(ISS, AUD, "")) + ".x";
        rejected(v, si, "claims-only without exp");
    }

    @Test(expected = IllegalArgumentException.class)
    public void emptyAudienceConfigurationIsRefused() {
        new DefaultJwtVerifier(ISS, "");
    }

    @Test(expected = IllegalArgumentException.class)
    public void weakRsaKeyIsRefused() throws Exception {
        KeyPairGenerator g = KeyPairGenerator.getInstance("RSA");
        g.initialize(1024);
        new DefaultJwtVerifier(ISS, AUD, "RS256", Collections.singletonMap("w", g.generateKeyPair().getPublic()));
    }

    @Test(expected = IllegalArgumentException.class)
    public void rsaKeyForEs256IsRefused() {
        new DefaultJwtVerifier(ISS, AUD, "ES256", Map.of("k1", rsa.getPublic()));
    }
}
