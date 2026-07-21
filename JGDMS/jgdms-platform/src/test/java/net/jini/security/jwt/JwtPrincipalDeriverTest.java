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
import java.security.Principal;
import java.util.Base64;
import java.util.Set;
import org.junit.Assert;
import org.junit.Test;

/**
 * Unit tests for {@link JwtPrincipalDeriver} and the default
 * {@link JwtVerifier#verifyAndExtractPrincipals(String)} it backs: the
 * canonical {@code sub}/{@code email}/{@code group} reconstruction the JERI
 * transport uses to avoid trusting attacker-controlled wire principals.
 */
public class JwtPrincipalDeriverTest {

    private static String jwt(String payloadJson) {
        String header = Base64.getUrlEncoder().withoutPadding()
                .encodeToString("{\"alg\":\"RS256\"}".getBytes(StandardCharsets.UTF_8));
        String payload = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(payloadJson.getBytes(StandardCharsets.UTF_8));
        return header + "." + payload + ".sig";
    }

    @Test
    public void subClaimBecomesSubPrincipal() {
        Set<Principal> p = JwtPrincipalDeriver.derivePrincipals(
                jwt("{\"sub\":\"alice\"}"));
        Assert.assertEquals(Set.of(new JwtPrincipal("sub:alice")), p);
    }

    @Test
    public void subEmailAndGroupsAllDerived() {
        Set<Principal> p = JwtPrincipalDeriver.derivePrincipals(
                jwt("{\"sub\":\"alice\",\"email\":\"a@x.org\","
                        + "\"groups\":[\"admins\",\"users\"]}"));
        Assert.assertTrue(p.contains(new JwtPrincipal("sub:alice")));
        Assert.assertTrue(p.contains(new JwtPrincipal("email:a@x.org")));
        Assert.assertTrue(p.contains(new JwtPrincipal("group:admins")));
        Assert.assertTrue(p.contains(new JwtPrincipal("group:users")));
        Assert.assertEquals(4, p.size());
    }

    @Test
    public void noRecognisedClaimsYieldsEmptySet() {
        Set<Principal> p = JwtPrincipalDeriver.derivePrincipals(
                jwt("{\"iss\":\"https://issuer\",\"exp\":9999999999}"));
        Assert.assertTrue(p.isEmpty());
    }

    @Test
    public void malformedTokenYieldsEmptySet_failClosed() {
        Assert.assertTrue(JwtPrincipalDeriver.derivePrincipals("not-a-jwt").isEmpty());
        Assert.assertTrue(JwtPrincipalDeriver.derivePrincipals("a.!!!notbase64!!!.c").isEmpty());
        Assert.assertTrue(JwtPrincipalDeriver.derivePrincipals(null).isEmpty());
    }

    @Test
    public void blankClaimValuesAreIgnored() {
        Set<Principal> p = JwtPrincipalDeriver.derivePrincipals(
                jwt("{\"sub\":\"\",\"email\":\"  \"}"));
        Assert.assertTrue(p.isEmpty());
    }

    @Test
    public void defaultSpiMethod_verifiesThenDerives() throws Exception {
        final boolean[] verified = {false};
        JwtVerifier v = raw -> { verified[0] = true; };
        Set<Principal> p = v.verifyAndExtractPrincipals(jwt("{\"sub\":\"bob\"}"));
        Assert.assertTrue("verify() must run first", verified[0]);
        Assert.assertEquals(Set.of(new JwtPrincipal("sub:bob")), p);
    }

    @Test
    public void defaultSpiMethod_failedVerifyDerivesNothing() {
        JwtVerifier v = raw -> { throw new JwtVerificationException("no"); };
        try {
            v.verifyAndExtractPrincipals(jwt("{\"sub\":\"bob\"}"));
            Assert.fail("expected JwtVerificationException");
        } catch (JwtVerificationException expected) {
            // good: no principals are produced when verification fails
        }
    }

    @Test
    public void customSpiMethod_mayOverrideMapping() throws Exception {
        // A custom verifier that authorises only a fixed principal, ignoring
        // the token claims, is honoured verbatim by the SPI contract.
        JwtVerifier v = new JwtVerifier() {
            public void verify(String raw) { }
            public Set<Principal> verifyAndExtractPrincipals(String raw) {
                return Set.of(new JwtPrincipal("sub:fixed"));
            }
        };
        Assert.assertEquals(Set.of(new JwtPrincipal("sub:fixed")),
                v.verifyAndExtractPrincipals(jwt("{\"sub\":\"whatever\"}")));
    }
}
