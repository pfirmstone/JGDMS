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

package net.jini.jeri.uds;

import java.nio.charset.Charset;
import java.nio.charset.IllegalCharsetNameException;
import java.nio.charset.StandardCharsets;
import java.nio.charset.UnsupportedCharsetException;

/**
 * Single source of truth for Unix-domain socket <em>path</em> validation, shared
 * by {@link UdsEndpoint} (client) and {@link UdsServerEndpoint} (server) so the
 * two checks cannot drift (LOW-1).
 *
 * <p>The checks here are SECURITY checks, not mere convenience validation: in
 * increment 1 the socket file's filesystem permissions are the entire
 * peer-access gate, so a path that escapes that gate must be rejected before an
 * endpoint is ever constructed.
 **/
final class UdsPaths {

    /**
     * Conservative platform ceiling on the {@code sun_path} byte length of an
     * {@code AF_UNIX} address.  {@code sizeof(sockaddr_un.sun_path)} is 108 on
     * Linux, 104 on macOS/BSD, and 108 on the JDK's Windows AF_UNIX shim; the
     * usable pathname is one byte less (NUL terminator).  We enforce the
     * smallest of these so a path accepted here binds/connects on every
     * supported platform, and reject over-length paths up front rather than
     * letting the JDK surface a late, opaque failure at bind/connect.
     **/
    static final int MAX_SUN_PATH_BYTES = 103; // 104 - 1 (NUL), the macOS floor

    /**
     * The charset the JDK uses to encode a pathname into {@code sun_path}: the
     * platform native encoding named by {@code sun.jnu.encoding} (MED-2).  We
     * measure the path's byte length against {@link #MAX_SUN_PATH_BYTES} in this
     * encoding, because the kernel's limit is on the encoded bytes, not on Java
     * chars.  If {@code sun.jnu.encoding} is unset or names an unknown charset,
     * we fall back to UTF-8, which is a conservative bound for the ceiling check
     * (UTF-8 is the maximal-width encoding for the code points typical in a
     * socket path, so a path that fits in UTF-8 bytes fits in most native
     * encodings; measuring in UTF-8 never under-counts for ASCII paths and
     * over-counts at worst, i.e. it fails safe by rejecting slightly early).
     **/
    private static final Charset SUN_PATH_CHARSET = resolveSunPathCharset();

    private UdsPaths() { throw new AssertionError(); }

    private static Charset resolveSunPathCharset() {
	String name = System.getProperty("sun.jnu.encoding");
	if (name != null) {
	    try {
		return Charset.forName(name);
	    } catch (IllegalCharsetNameException | UnsupportedCharsetException e) {
		// fall through to the conservative UTF-8 bound
	    }
	}
	return StandardCharsets.UTF_8;
    }

    /**
     * Validates a Unix-domain socket path fail-closed and returns it unchanged.
     *
     * @param path the candidate socket path
     * @return {@code path} (for call-site chaining)
     * @throws NullPointerException if {@code path} is {@code null}
     * @throws IllegalArgumentException if {@code path} is empty, denotes the
     * Linux abstract namespace (leading {@code '@'}), contains an embedded NUL,
     * or exceeds the {@code sun_path} byte ceiling
     **/
    static String validate(String path) {
	if (path == null) {
	    throw new NullPointerException("null socket path");
	}
	if (path.isEmpty()) {
	    throw new IllegalArgumentException("empty socket path");
	}
	/*
	 * Abstract-namespace sockets (Linux) are NOT filesystem objects and carry
	 * NO filesystem permissions, so they defeat the entire owner-only (0700)
	 * gate that is increment 1's whole access control.  They are denoted by a
	 * leading NUL byte in sun_path; the JDK's UnixDomainSocketAddress also
	 * treats a leading '@' as the abstract-namespace sigil.  Reject both.
	 */
	if (path.charAt(0) == '@') {
	    throw new IllegalArgumentException(
		"abstract-namespace socket path (leading '@') is not permitted: "
		    + "it has no filesystem permissions and defeats the "
		    + "owner-only access gate");
	}
	/*
	 * An embedded NUL both denotes the abstract namespace (leading NUL) and,
	 * anywhere in the string, truncates the C string the kernel actually
	 * binds -- a classic path-confusion / gate-bypass primitive.  Reject any
	 * NUL.
	 */
	if (path.indexOf('\0') >= 0) {
	    throw new IllegalArgumentException(
		"socket path contains an embedded NUL character");
	}
	/*
	 * Enforce the conservative AF_UNIX sun_path byte ceiling so the path is
	 * bindable/connectable on every supported platform and cannot silently
	 * bind a kernel-truncated prefix (a truncated path could resolve to a
	 * DIFFERENT, attacker-controlled inode than the one whose permissions
	 * were validated).  Measure BYTES in the native sun_path encoding (MED-2),
	 * because the kernel limit is on encoded bytes, not chars.
	 */
	int len = path.getBytes(SUN_PATH_CHARSET).length;
	if (len > MAX_SUN_PATH_BYTES) {
	    throw new IllegalArgumentException(
		"socket path is " + len + " bytes (" + SUN_PATH_CHARSET.name()
		    + "), exceeding the AF_UNIX sun_path ceiling of "
		    + MAX_SUN_PATH_BYTES + " bytes");
	}
	return path;
    }
}
