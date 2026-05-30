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

package org.apache.river.tool;

import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.security.Permission;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Properties;
import org.apache.river.api.security.DefaultPolicyParser;
import org.apache.river.api.security.PermissionGrant;
import org.apache.river.api.security.PolicyParser;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 * Tests for the PolicyCondenser tool.
 */
public class PolicyCondenserTest {

    private File tempDir;

    @Before
    public void setUp() throws Exception {
	tempDir = new File(
	    System.getProperty("java.io.tmpdir"),
	    "policy-condenser-test-" + Thread.currentThread().getId() + "-" + System.nanoTime()
	);
	tempDir.mkdirs();
    }

    @After
    public void tearDown() {
	if (tempDir != null && tempDir.exists()) {
	    File[] files = tempDir.listFiles();
	    if (files != null) {
		for (File f : files) {
		    f.delete();
		}
	    }
	    tempDir.delete();
	}
    }

    /**
     * Two grants for the same codebase with different permissions should be
     * merged into a single grant containing all permissions.
     */
    @Test
    public void testCondensesDuplicateCodebaseGrants() throws Exception {
	File policyFile = new File(tempDir, "duplicate.policy");
	try (PrintWriter pw = new PrintWriter(new FileWriter(policyFile))) {
	    pw.println("grant codebase \"file:/foo.jar\" {");
	    pw.println("    permission java.util.PropertyPermission \"java.home\", \"read\";");
	    pw.println("};");
	    pw.println("grant codebase \"file:/foo.jar\" {");
	    pw.println("    permission java.util.PropertyPermission \"user.dir\", \"read\";");
	    pw.println("};");
	    pw.println("grant codebase \"file:/bar.jar\" {");
	    pw.println("    permission java.util.PropertyPermission \"java.home\", \"read\";");
	    pw.println("};");
	}

	PolicyCondenser.main(new String[]{policyFile.getAbsolutePath()});

	File condensedFile = new File(tempDir, "duplicate.policy.con");
	assertTrue("Condensed file should exist", condensedFile.exists());

	PolicyParser parser = new DefaultPolicyParser();
	Collection<PermissionGrant> grants =
	    parser.parse(condensedFile.toURI().toURL(), new Properties());

	assertEquals("Two duplicate codebase grants should be merged into one", 2, grants.size());

	// Find the foo.jar grant and verify it has both permissions
	int fooPermCount = 0;
	for (PermissionGrant grant : grants) {
	    Collection<Permission> perms = grant.getPermissions();
	    if (perms.size() == 2) {
		fooPermCount++;
		List<String> names = new ArrayList<String>();
		for (Permission p : perms) {
		    names.add(p.getName());
		}
		assertTrue("Merged grant should contain java.home", names.contains("java.home"));
		assertTrue("Merged grant should contain user.dir", names.contains("user.dir"));
	    }
	}
	assertEquals("Exactly one grant should have 2 permissions (the merged foo.jar grant)", 1, fooPermCount);
    }

    /**
     * Grants with the same principals but no codebase should be merged.
     */
    @Test
    public void testCondensesDuplicatePrincipalGrants() throws Exception {
	File policyFile = new File(tempDir, "principal.policy");
	try (PrintWriter pw = new PrintWriter(new FileWriter(policyFile))) {
	    pw.println("grant principal javax.security.auth.x500.X500Principal \"CN=test\" {");
	    pw.println("    permission java.util.PropertyPermission \"java.home\", \"read\";");
	    pw.println("};");
	    pw.println("grant principal javax.security.auth.x500.X500Principal \"CN=test\" {");
	    pw.println("    permission java.util.PropertyPermission \"user.dir\", \"read\";");
	    pw.println("};");
	}

	PolicyCondenser.main(new String[]{policyFile.getAbsolutePath()});

	File condensedFile = new File(tempDir, "principal.policy.con");
	assertTrue("Condensed file should exist", condensedFile.exists());

	PolicyParser parser = new DefaultPolicyParser();
	Collection<PermissionGrant> grants =
	    parser.parse(condensedFile.toURI().toURL(), new Properties());

	assertEquals("Two duplicate principal grants should be merged into one", 1, grants.size());
	Collection<Permission> perms = grants.iterator().next().getPermissions();
	assertEquals("Merged grant should have 2 permissions", 2, perms.size());
    }

    /**
     * A policy file with no duplicate grants should be unchanged after condensation.
     */
    @Test
    public void testUniqueGrantsUnchanged() throws Exception {
	File policyFile = new File(tempDir, "unique.policy");
	try (PrintWriter pw = new PrintWriter(new FileWriter(policyFile))) {
	    pw.println("grant codebase \"file:/alpha.jar\" {");
	    pw.println("    permission java.util.PropertyPermission \"java.home\", \"read\";");
	    pw.println("};");
	    pw.println("grant codebase \"file:/beta.jar\" {");
	    pw.println("    permission java.util.PropertyPermission \"user.dir\", \"read\";");
	    pw.println("};");
	}

	PolicyCondenser.main(new String[]{policyFile.getAbsolutePath()});

	File condensedFile = new File(tempDir, "unique.policy.con");
	assertTrue("Condensed file should exist", condensedFile.exists());

	PolicyParser parser = new DefaultPolicyParser();
	Collection<PermissionGrant> grants =
	    parser.parse(condensedFile.toURI().toURL(), new Properties());

	assertEquals("Unique grants should remain as-is", 2, grants.size());
    }

    /**
     * Running the condenser twice on the same input must not duplicate grants
     * in the output file (no append-mode bug).
     */
    @Test
    public void testRunningTwiceDoesNotDuplicateGrants() throws Exception {
	File policyFile = new File(tempDir, "idempotent.policy");
	try (PrintWriter pw = new PrintWriter(new FileWriter(policyFile))) {
	    pw.println("grant codebase \"file:/foo.jar\" {");
	    pw.println("    permission java.util.PropertyPermission \"java.home\", \"read\";");
	    pw.println("};");
	}

	PolicyCondenser.main(new String[]{policyFile.getAbsolutePath()});
	PolicyCondenser.main(new String[]{policyFile.getAbsolutePath()});

	File condensedFile = new File(tempDir, "idempotent.policy.con");
	assertTrue("Condensed file should exist", condensedFile.exists());

	PolicyParser parser = new DefaultPolicyParser();
	Collection<PermissionGrant> grants =
	    parser.parse(condensedFile.toURI().toURL(), new Properties());

	assertEquals("Running condenser twice must not duplicate grants", 1, grants.size());
    }

    /**
     * Grants in the output file should be sorted in ascending order by their
     * string representation (codebase grants before principal-only grants,
     * then alphabetically within each group).
     */
    @Test
    public void testGrantsAreSorted() throws Exception {
	File policyFile = new File(tempDir, "sorted.policy");
	try (PrintWriter pw = new PrintWriter(new FileWriter(policyFile))) {
	    pw.println("grant codebase \"file:/zzz.jar\" {");
	    pw.println("    permission java.util.PropertyPermission \"java.home\", \"read\";");
	    pw.println("};");
	    pw.println("grant codebase \"file:/aaa.jar\" {");
	    pw.println("    permission java.util.PropertyPermission \"java.home\", \"read\";");
	    pw.println("};");
	}

	PolicyCondenser.main(new String[]{policyFile.getAbsolutePath()});

	File condensedFile = new File(tempDir, "sorted.policy.con");
	assertTrue("Condensed file should exist", condensedFile.exists());

	PolicyParser parser = new DefaultPolicyParser();
	Collection<PermissionGrant> grants =
	    parser.parse(condensedFile.toURI().toURL(), new Properties());

	assertEquals("Should have 2 grants", 2, grants.size());

	// Read the output file as text to verify ordering
	StringBuilder sb = new StringBuilder();
	try (java.io.BufferedReader br = new java.io.BufferedReader(new java.io.FileReader(condensedFile))) {
	    String line;
	    while ((line = br.readLine()) != null) {
		sb.append(line).append('\n');
	    }
	}
	String text = sb.toString();

	int aaaPos = text.indexOf("file:/aaa.jar");
	int zzzPos = text.indexOf("file:/zzz.jar");
	assertTrue("aaa.jar grant should appear before zzz.jar grant", aaaPos < zzzPos);
    }

    /**
     * When {@code PolicyCondenser.jwt.roleClaims=group}, a JwtPrincipal with
     * claim name {@code group} must be kept and one with claim name {@code sub}
     * must be omitted.
     */
    @Test
    public void testJwtRoleClaimKept() throws Exception {
	System.setProperty("PolicyCondenser.jwt.roleClaims", "group");
	try {
	    File policyFile = new File(tempDir, "jwt-role.policy");
	    try (PrintWriter pw = new PrintWriter(new FileWriter(policyFile))) {
		pw.println("grant principal au.zeus.jgdms.security.jwt.JwtPrincipal \"group:admins\" {");
		pw.println("    permission java.util.PropertyPermission \"java.home\", \"read\";");
		pw.println("};");
	    }

	    PolicyCondenser.main(new String[]{policyFile.getAbsolutePath()});

	    File condensedFile = new File(tempDir, "jwt-role.policy.con");
	    assertTrue("Condensed file should exist", condensedFile.exists());

	    PolicyParser parser = new DefaultPolicyParser();
	    Collection<PermissionGrant> grants =
		parser.parse(condensedFile.toURI().toURL(), new Properties());

	    assertEquals("Role-grain JwtPrincipal grant must be retained", 1, grants.size());
	} finally {
	    System.clearProperty("PolicyCondenser.jwt.roleClaims");
	}
    }

    /**
     * When {@code PolicyCondenser.jwt.roleClaims=group}, a grant whose only
     * principal is {@code JwtPrincipal "sub:alice"} must be dropped entirely
     * (to avoid creating an unconstrained policy entry).
     */
    @Test
    public void testNonRoleJwtClaimDropped() throws Exception {
	System.setProperty("PolicyCondenser.jwt.roleClaims", "group");
	try {
	    File policyFile = new File(tempDir, "jwt-nonrole.policy");
	    try (PrintWriter pw = new PrintWriter(new FileWriter(policyFile))) {
		pw.println("grant principal au.zeus.jgdms.security.jwt.JwtPrincipal \"sub:alice\" {");
		pw.println("    permission java.util.PropertyPermission \"java.home\", \"read\";");
		pw.println("};");
	    }

	    PolicyCondenser.main(new String[]{policyFile.getAbsolutePath()});

	    File condensedFile = new File(tempDir, "jwt-nonrole.policy.con");
	    assertTrue("Condensed file should exist", condensedFile.exists());

	    PolicyParser parser = new DefaultPolicyParser();
	    Collection<PermissionGrant> grants =
		parser.parse(condensedFile.toURI().toURL(), new Properties());

	    assertEquals("Grant with only non-role JWT principal must be dropped", 0, grants.size());
	} finally {
	    System.clearProperty("PolicyCondenser.jwt.roleClaims");
	}
    }

    /**
     * Non-JWT principals (e.g. X500Principal) must always be written
     * regardless of the {@code PolicyCondenser.jwt.roleClaims} setting.
     */
    @Test
    public void testNonJwtPrincipalAlwaysKept() throws Exception {
	System.setProperty("PolicyCondenser.jwt.roleClaims", "group");
	try {
	    File policyFile = new File(tempDir, "jwt-nonjwt.policy");
	    try (PrintWriter pw = new PrintWriter(new FileWriter(policyFile))) {
		pw.println("grant principal javax.security.auth.x500.X500Principal \"CN=test\" {");
		pw.println("    permission java.util.PropertyPermission \"java.home\", \"read\";");
		pw.println("};");
	    }

	    PolicyCondenser.main(new String[]{policyFile.getAbsolutePath()});

	    File condensedFile = new File(tempDir, "jwt-nonjwt.policy.con");
	    assertTrue("Condensed file should exist", condensedFile.exists());

	    PolicyParser parser = new DefaultPolicyParser();
	    Collection<PermissionGrant> grants =
		parser.parse(condensedFile.toURI().toURL(), new Properties());

	    assertEquals("Non-JWT principal grant must be retained", 1, grants.size());
	} finally {
	    System.clearProperty("PolicyCondenser.jwt.roleClaims");
	}
    }

    /**
     * When {@code PolicyCondenser.jwt.roleClaims} is absent, all JWT
     * principals are written verbatim (backward-compatible default).
     */
    @Test
    public void testPropertyAbsentWritesAllJwtPrincipals() throws Exception {
	System.clearProperty("PolicyCondenser.jwt.roleClaims"); // ensure absent
	File policyFile = new File(tempDir, "jwt-all.policy");
	try (PrintWriter pw = new PrintWriter(new FileWriter(policyFile))) {
	    pw.println("grant principal au.zeus.jgdms.security.jwt.JwtPrincipal \"sub:alice\" {");
	    pw.println("    permission java.util.PropertyPermission \"java.home\", \"read\";");
	    pw.println("};");
	    pw.println("grant principal au.zeus.jgdms.security.jwt.JwtPrincipal \"group:admins\" {");
	    pw.println("    permission java.util.PropertyPermission \"user.dir\", \"read\";");
	    pw.println("};");
	}

	PolicyCondenser.main(new String[]{policyFile.getAbsolutePath()});

	File condensedFile = new File(tempDir, "jwt-all.policy.con");
	assertTrue("Condensed file should exist", condensedFile.exists());

	PolicyParser parser = new DefaultPolicyParser();
	Collection<PermissionGrant> grants =
	    parser.parse(condensedFile.toURI().toURL(), new Properties());

	assertEquals("Without property all JWT principal grants must be written", 2, grants.size());
    }

    /**
     * A custom comma-separated claim name list must honour all specified names.
     * Grants for {@code role:editor} and {@code group:admins} are both kept
     * when {@code PolicyCondenser.jwt.roleClaims=role,group}.
     */
    @Test
    public void testMultipleClaimNamesInProperty() throws Exception {
	System.setProperty("PolicyCondenser.jwt.roleClaims", "role,group");
	try {
	    File policyFile = new File(tempDir, "jwt-multi.policy");
	    try (PrintWriter pw = new PrintWriter(new FileWriter(policyFile))) {
		pw.println("grant principal au.zeus.jgdms.security.jwt.JwtPrincipal \"role:editor\" {");
		pw.println("    permission java.util.PropertyPermission \"java.home\", \"read\";");
		pw.println("};");
		pw.println("grant principal au.zeus.jgdms.security.jwt.JwtPrincipal \"group:admins\" {");
		pw.println("    permission java.util.PropertyPermission \"user.dir\", \"read\";");
		pw.println("};");
		pw.println("grant principal au.zeus.jgdms.security.jwt.JwtPrincipal \"sub:bob\" {");
		pw.println("    permission java.util.PropertyPermission \"os.name\", \"read\";");
		pw.println("};");
	    }

	    PolicyCondenser.main(new String[]{policyFile.getAbsolutePath()});

	    File condensedFile = new File(tempDir, "jwt-multi.policy.con");
	    assertTrue("Condensed file should exist", condensedFile.exists());

	    PolicyParser parser = new DefaultPolicyParser();
	    Collection<PermissionGrant> grants =
		parser.parse(condensedFile.toURI().toURL(), new Properties());

	    assertEquals("role and group claims must be kept, sub must be dropped", 2, grants.size());
	} finally {
	    System.clearProperty("PolicyCondenser.jwt.roleClaims");
	}
    }

    /**
     * When a grant has mixed principals (one role JWT, one non-role JWT, one
     * non-JWT), only the non-role JWT principal is filtered out; the grant
     * itself is retained with the remaining principals.
     */
    @Test
    public void testMixedPrincipalsPartialFilter() throws Exception {
	System.setProperty("PolicyCondenser.jwt.roleClaims", "group");
	try {
	    File policyFile = new File(tempDir, "jwt-mixed.policy");
	    try (PrintWriter pw = new PrintWriter(new FileWriter(policyFile))) {
		pw.println("grant");
		pw.println("  principal javax.security.auth.x500.X500Principal \"CN=svc\"");
		pw.println("  principal au.zeus.jgdms.security.jwt.JwtPrincipal \"group:admins\"");
		pw.println("  principal au.zeus.jgdms.security.jwt.JwtPrincipal \"sub:alice\"");
		pw.println("  {");
		pw.println("    permission java.util.PropertyPermission \"java.home\", \"read\";");
		pw.println("};");
	    }

	    PolicyCondenser.main(new String[]{policyFile.getAbsolutePath()});

	    File condensedFile = new File(tempDir, "jwt-mixed.policy.con");
	    assertTrue("Condensed file should exist", condensedFile.exists());

	    PolicyParser parser = new DefaultPolicyParser();
	    Collection<PermissionGrant> grants =
		parser.parse(condensedFile.toURI().toURL(), new Properties());

	    assertEquals("Mixed-principal grant must be retained (not dropped)", 1, grants.size());
	    // Verify by reading the condensed file text: "group:admins" retained, "sub:alice" filtered
	    StringBuilder sb = new StringBuilder();
	    try (java.io.BufferedReader br = new java.io.BufferedReader(new java.io.FileReader(condensedFile))) {
		String line;
		while ((line = br.readLine()) != null) {
		    sb.append(line).append('\n');
		}
	    }
	    String condensedText = sb.toString();
	    assertTrue("group:admins must be kept", condensedText.contains("group:admins"));
	    assertFalse("sub:alice must be filtered out", condensedText.contains("sub:alice"));
	} finally {
	    System.clearProperty("PolicyCondenser.jwt.roleClaims");
	}
    }

}
