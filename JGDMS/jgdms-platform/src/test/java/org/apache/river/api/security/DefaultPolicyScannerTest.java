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

package org.apache.river.api.security;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import org.apache.river.api.security.DefaultPolicyScanner.GrantEntry;
import org.apache.river.api.security.DefaultPolicyScanner.InvalidFormatException;
import org.apache.river.api.security.DefaultPolicyScanner.KeystoreEntry;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 * Tests for {@link DefaultPolicyScanner}, covering the digest clause parsing
 * that was added to support DigestGrant-based policy entries.
 */
public class DefaultPolicyScannerTest {

    private final DefaultPolicyScanner scanner = new DefaultPolicyScanner();

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private List<GrantEntry> scan(String policy) throws Exception {
        List<GrantEntry> grants = new ArrayList<>();
        List<KeystoreEntry> keystores = new ArrayList<>();
        scanner.scanStream(new StringReader(policy), grants, keystores);
        return grants;
    }

    // -----------------------------------------------------------------------
    // Plain grant (no digest) — existing behaviour must be preserved
    // -----------------------------------------------------------------------

    @Test
    public void testScanPlainGrantHasNullDigest() throws Exception {
        System.out.println("testScanPlainGrantHasNullDigest");
        String policy = "grant codebase \"file:/foo/bar.jar\" {"
                + "  permission java.security.SecurityPermission \"test\";"
                + "};";
        List<GrantEntry> grants = scan(policy);
        assertEquals(1, grants.size());
        assertNull("Plain grant must have null digest", grants.get(0).getDigest());
        assertEquals("file:/foo/bar.jar", grants.get(0).getCodebase(null));
    }

    // -----------------------------------------------------------------------
    // grant with digest clause
    // -----------------------------------------------------------------------

    @Test
    public void testScanGrantWithDigestClause() throws Exception {
        System.out.println("testScanGrantWithDigestClause");
        String policy = "grant digest \"SHA-256:deadbeef\" {"
                + "  permission java.security.AllPermission;"
                + "};";
        List<GrantEntry> grants = scan(policy);
        assertEquals(1, grants.size());
        assertEquals("SHA-256:deadbeef", grants.get(0).getDigest());
        assertNull(grants.get(0).getCodebase(null));
    }

    @Test
    public void testScanGrantWithDigestAndCodebase() throws Exception {
        System.out.println("testScanGrantWithDigestAndCodebase");
        String policy = "grant codebase \"file:/app/lib.jar\", digest \"SHA-1:aabbcc\" {"
                + "  permission java.io.FilePermission \"/tmp\", \"read\";"
                + "};";
        List<GrantEntry> grants = scan(policy);
        assertEquals(1, grants.size());
        assertEquals("SHA-1:aabbcc", grants.get(0).getDigest());
        assertEquals("file:/app/lib.jar", grants.get(0).getCodebase(null));
    }

    @Test
    public void testScanGrantDigestIsCaseInsensitiveKeyword() throws Exception {
        System.out.println("testScanGrantDigestIsCaseInsensitiveKeyword");
        // The keyword 'digest' must be recognised regardless of letter case.
        String policy = "grant DIGEST \"SHA-256:cafe\" {"
                + "  permission java.security.SecurityPermission \"x\";"
                + "};";
        List<GrantEntry> grants = scan(policy);
        assertEquals(1, grants.size());
        assertEquals("SHA-256:cafe", grants.get(0).getDigest());
    }

    @Test
    public void testScanGrantDigestKeywordMixedCase() throws Exception {
        System.out.println("testScanGrantDigestKeywordMixedCase");
        String policy = "grant Digest \"MD5:0011aabb\" {"
                + "  permission java.security.SecurityPermission \"y\";"
                + "};";
        List<GrantEntry> grants = scan(policy);
        assertEquals(1, grants.size());
        assertEquals("MD5:0011aabb", grants.get(0).getDigest());
    }

    // -----------------------------------------------------------------------
    // Multiple grants — only the one with digest should have it set
    // -----------------------------------------------------------------------

    @Test
    public void testScanMultipleGrantsOnlyOneWithDigest() throws Exception {
        System.out.println("testScanMultipleGrantsOnlyOneWithDigest");
        String policy =
            "grant codebase \"file:/plain.jar\" {"
          + "  permission java.security.SecurityPermission \"a\";"
          + "};"
          + "grant digest \"SHA-256:ff00\" {"
          + "  permission java.security.SecurityPermission \"b\";"
          + "};";
        List<GrantEntry> grants = scan(policy);
        assertEquals(2, grants.size());
        // order is guaranteed by the scanner (first-in, first-out)
        assertNull(grants.get(0).getDigest());
        assertEquals("SHA-256:ff00", grants.get(1).getDigest());
    }

    // -----------------------------------------------------------------------
    // GrantEntry.toString() includes the digest
    // -----------------------------------------------------------------------

    @Test
    public void testGrantEntryToStringIncludesDigest() throws Exception {
        System.out.println("testGrantEntryToStringIncludesDigest");
        String policy = "grant digest \"SHA-256:aabb\" {"
                + "  permission java.security.SecurityPermission \"z\";"
                + "};";
        List<GrantEntry> grants = scan(policy);
        String s = grants.get(0).toString();
        assertTrue("toString() must mention the digest value",
                s.contains("SHA-256:aabb"));
    }

    // -----------------------------------------------------------------------
    // Invalid syntax: digest keyword not followed by a quoted string
    // -----------------------------------------------------------------------

    @Test
    public void testScanDigestMissingQuotedValueThrows() {
        System.out.println("testScanDigestMissingQuotedValueThrows");
        // "digest" followed by an unquoted word must cause InvalidFormatException
        String policy = "grant digest unquoted { permission java.security.AllPermission; };";
        try {
            scan(policy);
            fail("Expected InvalidFormatException for unquoted digest value");
        } catch (InvalidFormatException e) {
            // expected
        } catch (Exception e) {
            fail("Expected InvalidFormatException but got " + e);
        }
    }
}
