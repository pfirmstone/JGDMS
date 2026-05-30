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

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.URISyntaxException;
import java.security.KeyStore;
import java.security.Permission;
import java.security.Principal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import org.apache.river.api.security.DefaultPolicyParser;
import org.apache.river.api.security.PermissionComparator;
import org.apache.river.api.security.PermissionGrant;
import org.apache.river.api.security.PermissionGrantBuilder;
import org.apache.river.api.security.PolicyParser;
import org.apache.river.api.security.UnresolvedPrincipal;

/**
 * This PolicyCondenser can be used to consolidate and condense permission
 * grants in policy files.
 * 
 * The condenser will replace properties in grant files passed in using -Dprop=value
 * 
 * java -cp policy-condenser-3.0-SNAPSHOT.jar;%RIVER.HOME%\lib\* 
 * org.apache.river.tool.PolicyCondenser security.policy
 * 
 * <p>When the system property {@code PolicyCondenser.jwt.roleClaims} is set to
 * a comma-separated list of JWT claim names (e.g. {@code group,role}), any
 * {@code au.zeus.jgdms.security.jwt.JwtPrincipal} entry in the policy whose
 * claim name is <em>not</em> in the configured set is omitted from the
 * condensed output.  Grants whose entire principal set is removed by this
 * filter are dropped entirely (to avoid accidentally creating a grant with no
 * principal constraint).  When the property is absent all principals are
 * written verbatim (backward-compatible default).
 * 
 * @see KeyStore
 * @author Peter Firmstone
 * @since 3.0.0
 */
public class PolicyCondenser {

    /**
     * Fully-qualified class name of {@code JwtPrincipal} as it appears in
     * policy files written by the JERI dispatcher.  Compared as a plain
     * string so that {@code jgdms-security-jwt} need not be on the classpath.
     */
    private static final String JWT_PRINCIPAL_CLASS =
            "au.zeus.jgdms.security.jwt.JwtPrincipal";

    /**
     * System property that controls which JWT claim names are treated as
     * role-grain identifiers.  Only {@code JwtPrincipal} entries whose claim
     * name (the part before the first {@code ':'} in their name) appears in
     * this comma-separated list are retained in the condensed output.
     * When absent, all JWT principals are written verbatim.
     */
    private static final String ROLE_CLAIMS_PROPERTY =
            "PolicyCondenser.jwt.roleClaims";
    
    public static void main(String [] args) throws Exception{
	PolicyCondenser condenser = new PolicyCondenser();
	for (int i = 0, l = args.length; i < l; i++){
	    condenser.condense(args[i]);
	}
    }

    private PolicyCondenser() 
    {
        super();
    } 
    
    private static File policyFile(String filename) throws URISyntaxException{
       
	File policyFile = new File(filename);
	if (!policyFile.exists()){
	    try {
		policyFile.createNewFile();
	    } catch (IOException ex) {
		throw new RuntimeException("Unable to create a policy file: " + filename, ex);
	    }
	}
        return policyFile;
    }

    private void condense(String arg) throws Exception {
	File policy = policyFile(arg);
	File condensedPolicy = new File(policy.getAbsolutePath() + ".con");
	PolicyParser parser = new DefaultPolicyParser();
	Collection<PermissionGrant> grantsCol = parser.parse(policy.toURI().toURL(), System.getProperties());
	PermissionGrant [] grants = grantsCol.toArray(new PermissionGrant[0]);
	int length = grants.length;
	List<PermissionGrant> condensed = new ArrayList<PermissionGrant>(length);
	for (int i = 0; i < length; i++){
	    if (grants[i] == null) continue;
	    PermissionGrantBuilder builder = grants[i].getBuilderTemplate();
	    Collection<Permission> permissions = new TreeSet<Permission>(new PermissionComparator());
	    permissions.addAll(grants[i].getPermissions());
	    for (int j = 0; j < length; j++){
		if (i == j || grants[j] == null) continue;
		if (grants[i].impliesEquivalent(grants[j])){
		    permissions.addAll(grants[j].getPermissions());
		    grants[j] = null;
		}
	    }
	    builder.permissions(permissions.toArray(new Permission[0]));
	    PermissionGrant condensedGrant = builder.build();
	    if (!condensedGrant.isVoid()) {
		condensed.add(condensedGrant);
	    }
	    grants[i] = null;
	}
	Collections.sort(condensed, new Comparator<PermissionGrant>() {
	    @Override
	    public int compare(PermissionGrant a, PermissionGrant b) {
		return a.toString().compareTo(b.toString());
	    }
	});
	Set<String> roleClaims = parseRoleClaims();
	try (PrintWriter pw = new PrintWriter(new BufferedWriter(new FileWriter(condensedPolicy, false)))) {
	    for (PermissionGrant grant : condensed) {
		if (roleClaims != null) {
		    grant = applyJwtRoleFilter(grant, roleClaims);
		    if (grant == null) continue;
		}
		pw.print("grant ");
		pw.print(grant.toString());
	    }
	}
    }

    /**
     * Parses the {@value #ROLE_CLAIMS_PROPERTY} system property into a set of
     * claim names.  Returns {@code null} when the property is not set (meaning
     * no JWT filtering should be applied).
     */
    private static Set<String> parseRoleClaims() {
	String prop = System.getProperty(ROLE_CLAIMS_PROPERTY);
	if (prop == null || prop.isEmpty()) return null;
	Set<String> result = new HashSet<String>();
	for (String claim : prop.split(",")) {
	    String trimmed = claim.trim();
	    if (!trimmed.isEmpty()) result.add(trimmed);
	}
	return result.isEmpty() ? null : result;
    }

    /**
     * Filters JWT principals from a grant according to the configured role
     * claims set.
     *
     * <p>Any {@code JwtPrincipal} whose claim name is <em>not</em> in
     * {@code roleClaims} is removed.  If all principals are removed this way
     * the method returns {@code null} to signal that the grant should be
     * dropped (avoiding creation of an overly-broad grant with no principal
     * constraint).  Grants with no principals, and grants whose principals are
     * all non-JWT, are returned unchanged.
     *
     * @param grant      the grant to filter; must not be {@code null}
     * @param roleClaims the set of permitted claim names; must not be {@code null}
     * @return the (possibly rebuilt) grant, or {@code null} if it should be dropped
     */
    private static PermissionGrant applyJwtRoleFilter(
            PermissionGrant grant, Set<String> roleClaims) {
	Principal[] principals = grant.getPrincipals();
	if (principals.length == 0) return grant; // no principal constraint — pass through

	boolean needsFilter = false;
	for (Principal p : principals) {
	    if (isNonRoleJwtPrincipal(p, roleClaims)) {
		needsFilter = true;
		break;
	    }
	}
	if (!needsFilter) return grant;

	List<Principal> kept = new ArrayList<Principal>(principals.length);
	for (Principal p : principals) {
	    if (!isNonRoleJwtPrincipal(p, roleClaims)) {
		kept.add(p);
	    }
	}

	if (kept.isEmpty()) {
	    // All principals were non-role JWT principals; drop the grant entirely
	    // to avoid creating an unconstrained (grant-to-everyone) policy entry.
	    return null;
	}

	PermissionGrantBuilder builder = grant.getBuilderTemplate();
	builder.principals(kept.toArray(new Principal[0]));
	PermissionGrant filtered = builder.build();
	return filtered.isVoid() ? null : filtered;
    }

    /**
     * Returns {@code true} if {@code p} is a {@code JwtPrincipal} whose claim
     * name is not in {@code roleClaims}.
     *
     * <p>Comparison is performed on the {@link UnresolvedPrincipal} class name
     * so that {@code jgdms-security-jwt} need not be on the classpath at
     * condense time.
     */
    private static boolean isNonRoleJwtPrincipal(Principal p, Set<String> roleClaims) {
	if (!(p instanceof UnresolvedPrincipal)) return false;
	UnresolvedPrincipal up = (UnresolvedPrincipal) p;
	if (!JWT_PRINCIPAL_CLASS.equals(up.getClassName())) return false;
	String name = up.getName();
	if (name == null || name.isEmpty()) return false;
	int colon = name.indexOf(':');
	if (colon < 0) return false; // malformed name — leave it untouched
	String claimName = name.substring(0, colon);
	return !roleClaims.contains(claimName);
    }

}
