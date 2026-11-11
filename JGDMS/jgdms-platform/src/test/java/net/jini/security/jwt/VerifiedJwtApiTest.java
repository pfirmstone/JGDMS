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
import org.junit.Test;

/**
 * The parts of the verified-token API that exist only in this change: the
 * {@code emailVerified} flag and the configurable principal cap.  Kept apart
 * from {@link DefaultJwtVerifierClaimsTest} so that class compiles against the
 * code before the change (fail-first evidence).
 */
public class VerifiedJwtApiTest {

    private static final String ISS = "https://issuer.test";
    private static final String AUD = "svc-a";

    @Test
    public void emailPrincipalNeedsTheVerifiedFlag() {
        VerifiedJwt j = new VerifiedJwt("a", "i", null, null, "x@y", null);
        Assert.assertEquals(Set.<Principal>of(new JwtPrincipal("sub:a")), j.getPrincipals());
        Assert.assertFalse(j.isEmailVerified());
        VerifiedJwt no = new VerifiedJwt("a", "i", null, null, "x@y", false, null);
        Assert.assertEquals(Set.<Principal>of(new JwtPrincipal("sub:a")), no.getPrincipals());
        VerifiedJwt k = new VerifiedJwt("a", "i", null, null, "x@y", true, null);
        Assert.assertTrue(k.isEmailVerified());
        Assert.assertTrue(k.getPrincipals().contains(new JwtPrincipal("email:x@y")));
    }

    @Test
    public void emptyNamesAreRefusedByDerivePrincipals() {
        try {
            new VerifiedJwt("", "i", null, null, null, null);
            Assert.fail("empty sub");
        } catch (IllegalArgumentException expected) { /* refused */ }
        try {
            new VerifiedJwt("a", "i", null, null, null, List.of(""));
            Assert.fail("empty group");
        } catch (IllegalArgumentException expected) { /* refused */ }
    }

    private static String b64(String s) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(s.getBytes(StandardCharsets.UTF_8));
    }

    private static String token(KeyPair kp, int groups) throws Exception {
        StringBuilder g = new StringBuilder();
        for (int i = 0; i < groups; i++) g.append(i == 0 ? "" : ",").append("\"g").append(i).append('"');
        String payload = "{\"sub\":\"alice\",\"iss\":\"" + ISS + "\",\"aud\":\"" + AUD + "\",\"exp\":"
                + (System.currentTimeMillis() / 1000L + 600) + ",\"groups\":[" + g + "]}";
        String si = b64("{\"alg\":\"RS256\",\"kid\":\"k1\"}") + "." + b64(payload);
        Signature s = Signature.getInstance("SHA256withRSA");
        s.initSign(kp.getPrivate());
        s.update(si.getBytes(StandardCharsets.US_ASCII));
        return si + "." + Base64.getUrlEncoder().withoutPadding().encodeToString(s.sign());
    }

    @Test
    public void principalCapIsConfigurable() throws Exception {
        KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
        gen.initialize(2048);
        KeyPair kp = gen.generateKeyPair();
        Map<String, java.security.PublicKey> keys = Map.of("k1", kp.getPublic());
        DefaultJwtVerifier small = new DefaultJwtVerifier(ISS, AUD, "RS256", keys,
                DefaultJwtVerifier.DEFAULT_MAX_LIFETIME, 3);
        Assert.assertEquals(3, small.verify(token(kp, 2)).getPrincipals().size());   // sub + 2
        try {
            small.verify(token(kp, 3));                                               // sub + 3 = 4
            Assert.fail("4 principals accepted with a cap of 3");
        } catch (JwtVerificationException expected) { /* refused */ }
        DefaultJwtVerifier big = new DefaultJwtVerifier(ISS, AUD, "RS256", keys,
                Duration.ofHours(1), 200);
        Assert.assertEquals(101, big.verify(token(kp, 100)).getPrincipals().size());
        try {
            new DefaultJwtVerifier(ISS, AUD, "RS256", keys, Duration.ofHours(1), 0);
            Assert.fail("cap 0");
        } catch (IllegalArgumentException expected) { /* refused */ }
    }
}
