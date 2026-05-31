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

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.StreamTokenizer;
import java.io.StringReader;
import java.net.URISyntaxException;
import java.security.KeyStore;
import java.security.Permission;
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
 * {@code net.jini.security.jwt.JwtPrincipal} entry in the policy whose
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
     * string so that {@code jgdms-platform} need not be on the classpath
     * (though in practice it always is).
     */
    private static final String JWT_PRINCIPAL_CLASS =
            "net.jini.security.jwt.JwtPrincipal";

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

	Set<String> roleClaims = parseRoleClaims();
	File parseTarget = policy;
	File tempFile = null;
	if (roleClaims != null) {
	    tempFile = preprocessJwtFilter(policy, roleClaims);
	    parseTarget = tempFile;
	}

	try {
	    PolicyParser parser = new DefaultPolicyParser();
	    Collection<PermissionGrant> grantsCol = parser.parse(parseTarget.toURI().toURL(), System.getProperties());
	    PermissionGrant[] grants = grantsCol.toArray(new PermissionGrant[0]);
	    int length = grants.length;
	    List<PermissionGrant> condensed = new ArrayList<PermissionGrant>(length);
	    for (int i = 0; i < length; i++) {
		if (grants[i] == null) continue;
		PermissionGrantBuilder builder = grants[i].getBuilderTemplate();
		Collection<Permission> permissions = new TreeSet<Permission>(new PermissionComparator());
		permissions.addAll(grants[i].getPermissions());
		for (int j = 0; j < length; j++) {
		    if (i == j || grants[j] == null) continue;
		    if (grants[i].impliesEquivalent(grants[j])) {
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
	    try (PrintWriter pw = new PrintWriter(new BufferedWriter(new FileWriter(condensedPolicy, false)))) {
		for (PermissionGrant grant : condensed) {
		    pw.print("grant ");
		    pw.print(grant.toString());
		}
	    }
	} finally {
	    if (tempFile != null) tempFile.delete();
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
     * Reads the given policy file and writes a filtered copy to a temporary
     * file, removing any {@code JwtPrincipal} entries whose claim name is not
     * in {@code roleClaims}.  Grant blocks left with no principals after
     * filtering are dropped entirely.
     *
     * <p>The returned temporary file is owned by the caller and must be
     * deleted after use.
     *
     * @param input      the source policy file to filter
     * @param roleClaims the set of JWT claim names to retain
     * @return a temporary file containing the filtered policy text
     * @throws IOException if reading or writing fails
     */
    private static File preprocessJwtFilter(File input, Set<String> roleClaims) throws IOException {
	String text = readFileText(input);
	String filtered = filterJwtFromText(text, roleClaims);
	File tmp = File.createTempFile("policyfilter-", ".policy");
	tmp.deleteOnExit();
	try (PrintWriter pw = new PrintWriter(new BufferedWriter(new FileWriter(tmp, false)))) {
	    pw.print(filtered);
	}
	return tmp;
    }

    /**
     * Reads an entire file as a UTF-8 string.
     */
    private static String readFileText(File f) throws IOException {
	StringBuilder sb = new StringBuilder((int) Math.min(f.length(), Integer.MAX_VALUE));
	try (BufferedReader br = new BufferedReader(
		new InputStreamReader(new FileInputStream(f), "UTF-8"))) {
	    char[] buf = new char[4096];
	    int n;
	    while ((n = br.read(buf)) != -1) {
		sb.append(buf, 0, n);
	    }
	}
	return sb.toString();
    }

    /**
     * Filters JWT principal entries from a policy text string.
     *
     * <p>Uses {@link StreamTokenizer} with the same settings as
     * {@code DefaultPolicyScanner} to correctly handle quoted strings and
     * {@code //} / {@code /* * /} comments.  The resulting text is syntactically
     * valid and can be fed directly to {@code DefaultPolicyParser}.
     *
     * @param text       the raw policy file text
     * @param roleClaims the JWT claim names to retain
     * @return filtered policy text
     */
    static String filterJwtFromText(String text, Set<String> roleClaims) throws IOException {
	StreamTokenizer st = new StreamTokenizer(new StringReader(text));
	st.slashSlashComments(true);
	st.slashStarComments(true);
	st.wordChars('_', '_');
	st.wordChars('$', '$');

	StringBuilder out = new StringBuilder(text.length());
	int tok;
	while ((tok = st.nextToken()) != StreamTokenizer.TT_EOF) {
	    if (tok == StreamTokenizer.TT_WORD) {
		String kw = st.sval;
		if ("keystore".equalsIgnoreCase(kw)) {
		    appendKeystoreEntry(st, out);
		} else if ("grant".equalsIgnoreCase(kw)) {
		    appendFilteredGrant(st, out, roleClaims);
		}
		// other top-level words (unrecognised) are silently skipped
	    }
	    // ';' and other top-level tokens are silently skipped
	}
	return out.toString();
    }

    /**
     * Reads and re-emits one {@code keystore} clause from {@code st} into
     * {@code out}.
     */
    private static void appendKeystoreEntry(StreamTokenizer st, StringBuilder out) throws IOException {
	out.append("keystore");
	if (st.nextToken() == '"') {
	    out.append(" \"").append(escapePolicy(st.sval)).append("\"");
	    int tok = st.nextToken();
	    if (tok == ',') {
		tok = st.nextToken();
		if (tok == '"') {
		    out.append(", \"").append(escapePolicy(st.sval)).append("\"");
		} else {
		    st.pushBack();
		}
	    } else {
		st.pushBack();
	    }
	} else {
	    st.pushBack();
	}
	out.append(";\n");
    }

    /**
     * Reads one complete {@code grant} block from {@code st}, applies the JWT
     * role filter to the principal list, and appends the (possibly filtered)
     * grant to {@code out}.  If filtering leaves a grant that originally had
     * principals with an empty principal list, the entire grant is dropped.
     */
    private static void appendFilteredGrant(StreamTokenizer st, StringBuilder out,
	    Set<String> roleClaims) throws IOException {
	String signers = null, codebase = null, digest = null;
	// Each element: { className, name } where either may be null for wildcards
	List<String[]> principals = new ArrayList<String[]>();

	// --- read grant header (everything before '{') ---
	int tok;
	headerLoop:
	while (true) {
	    tok = st.nextToken();
	    switch (tok) {
		case StreamTokenizer.TT_EOF:
		    return; // malformed input
		case '{':
		    break headerLoop;
		case ',':
		    break; // clause delimiter — ignore
		case StreamTokenizer.TT_WORD:
		    String kw = st.sval;
		    if ("signedby".equalsIgnoreCase(kw)) {
			if (st.nextToken() == '"') signers = st.sval;
		    } else if ("codebase".equalsIgnoreCase(kw)) {
			if (st.nextToken() == '"') codebase = st.sval;
		    } else if ("digest".equalsIgnoreCase(kw)) {
			if (st.nextToken() == '"') digest = st.sval;
		    } else if ("principal".equalsIgnoreCase(kw)) {
			String className = null, name = null;
			tok = st.nextToken();
			if (tok == StreamTokenizer.TT_WORD) {
			    className = st.sval;
			    tok = st.nextToken();
			}
			if (tok == '"') {
			    name = st.sval;
			} else if (tok == (int) '*') {
			    name = "*";
			} else {
			    st.pushBack();
			}
			principals.add(new String[]{className, name});
		    }
		    break;
		default:
		    break; // ignore other tokens in the header
	    }
	}

	// --- read permission entries inside '{' ... '}' ---
	// Each element: { permClass, target, actions, signer } (nulls for absent fields)
	List<String[]> permissions = new ArrayList<String[]>();
	permLoop:
	while (true) {
	    tok = st.nextToken();
	    switch (tok) {
		case StreamTokenizer.TT_EOF:
		case '}':
		    break permLoop;
		case StreamTokenizer.TT_WORD:
		    if ("permission".equalsIgnoreCase(st.sval)) {
			String[] perm = readPermissionEntry(st);
			if (perm != null) permissions.add(perm);
		    }
		    break;
		default:
		    break;
	    }
	}
	// Consume optional ';' after the closing '}'
	tok = st.nextToken();
	if (tok != ';') st.pushBack();

	// --- apply JWT principal filter ---
	List<String[]> kept = new ArrayList<String[]>(principals.size());
	boolean hadJwtPrincipal = false;
	for (String[] p : principals) {
	    if (JWT_PRINCIPAL_CLASS.equals(p[0])) {
		hadJwtPrincipal = true;
		if (!isNonRoleJwtByName(p[1], roleClaims)) {
		    kept.add(p);
		}
	    } else {
		kept.add(p);
	    }
	}

	// Drop the grant if it had JWT principals but all were filtered out
	if (hadJwtPrincipal && kept.isEmpty()) return;

	// --- emit the (possibly filtered) grant ---
	out.append("grant");
	if (signers != null) out.append(" signedby \"").append(escapePolicy(signers)).append("\"");
	if (codebase != null) out.append(" codebase \"").append(escapePolicy(codebase)).append("\"");
	if (digest != null) out.append(" digest \"").append(escapePolicy(digest)).append("\"");
	for (String[] p : kept) {
	    out.append(" principal");
	    if (p[0] != null) out.append(" ").append(p[0]);
	    if (p[1] != null) {
		if ("*".equals(p[1])) {
		    out.append(" *");
		} else {
		    out.append(" \"").append(escapePolicy(p[1])).append("\"");
		}
	    }
	}
	out.append(" {\n");
	for (String[] perm : permissions) {
	    out.append("    permission ").append(perm[0]);
	    if (perm[1] != null) out.append(" \"").append(escapePolicy(perm[1])).append("\"");
	    if (perm[2] != null) out.append(", \"").append(escapePolicy(perm[2])).append("\"");
	    if (perm[3] != null) out.append(", signedby \"").append(escapePolicy(perm[3])).append("\"");
	    out.append(";\n");
	}
	out.append("};\n");
    }

    /**
     * Reads one {@code permission} entry from {@code st} after the
     * {@code "permission"} keyword has already been consumed.
     *
     * @return a four-element array {@code {className, target, actions, signedBy}},
     *         with {@code null} for absent fields; or {@code null} if the
     *         entry could not be parsed
     */
    private static String[] readPermissionEntry(StreamTokenizer st) throws IOException {
	if (st.nextToken() != StreamTokenizer.TT_WORD) {
	    st.pushBack();
	    return null;
	}
	String pClass = st.sval;
	String pTarget = null, pActions = null, pSigner = null;

	int tok = st.nextToken();
	if (tok == '"') {
	    pTarget = st.sval;
	    tok = st.nextToken();
	}
	if (tok == ',') tok = st.nextToken();
	if (tok == '"') {
	    pActions = st.sval;
	    tok = st.nextToken();
	}
	if (tok == ',') tok = st.nextToken();
	if (tok == StreamTokenizer.TT_WORD && "signedby".equalsIgnoreCase(st.sval)) {
	    if (st.nextToken() == '"') pSigner = st.sval;
	} else {
	    st.pushBack();
	}
	return new String[]{pClass, pTarget, pActions, pSigner};
    }

    /**
     * Returns {@code true} when {@code name} belongs to a JWT claim whose name
     * (the part before the first {@code ':'}) is <em>not</em> in
     * {@code roleClaims}.  Returns {@code false} for {@code null} or
     * malformed names so that such principals are left untouched.
     */
    private static boolean isNonRoleJwtByName(String name, Set<String> roleClaims) {
	if (name == null || name.isEmpty()) return false;
	int colon = name.indexOf(':');
	if (colon < 0) return false; // malformed — leave untouched
	String claimName = name.substring(0, colon);
	return !roleClaims.contains(claimName);
    }

    /**
     * Escapes {@code "} and {@code \} characters in {@code s} for safe
     * embedding between double-quotes in a policy file.
     */
    private static String escapePolicy(String s) {
	return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

}
