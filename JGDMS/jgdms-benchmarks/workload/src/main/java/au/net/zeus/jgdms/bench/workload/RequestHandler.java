/*
 * Copyright (C) 2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package au.net.zeus.jgdms.bench.workload;

import java.util.HashMap;
import java.util.Map;
import java.util.zip.CRC32;

/**
 * A small, self-contained request handler: the code whose cost is measured
 * with and without a SecurityManager.
 *
 * <p>Its only guarded operation is {@link System#getProperty(String)}, which
 * under a SecurityManager performs one {@code PropertyPermission} check per
 * call and otherwise does almost no work. That makes it a good probe for the
 * cost of a permission check itself; {@link #handle} wraps a few such reads
 * in ordinary request-handling work so the check can be seen as a fraction
 * of a request.
 *
 * <p>This class lives in its own jar, which the benchmark policy grants only
 * {@code PropertyPermission "jgdms.bench.*", "read"}. Its ProtectionDomain is
 * therefore evaluated against the policy on every check, like application
 * code, rather than passing on AllPermission.
 *
 * <p>Written with AI assistance (Claude); see the README.
 */
public final class RequestHandler {

    /** Prefix of every property the workload is permitted to read. */
    public static final String PROPERTY_PREFIX = "jgdms.bench.";

    private final String[] guardedProperties;

    /**
     * @param guardedProperties names of the properties to read; each must
     *        start with {@link #PROPERTY_PREFIX} so the policy permits it
     */
    public RequestHandler(String[] guardedProperties) {
        if (guardedProperties.length == 0) {
            throw new IllegalArgumentException("need at least one property");
        }
        for (String p : guardedProperties) {
            if (!p.startsWith(PROPERTY_PREFIX)) {
                throw new IllegalArgumentException("not a benchmark property: " + p);
            }
        }
        this.guardedProperties = guardedProperties.clone();
    }

    /**
     * One guarded operation: a single property read, which is a single
     * {@code PropertyPermission} check when a SecurityManager is installed.
     *
     * @param index any int; reduced modulo the number of properties
     * @return the property value
     */
    public String readGuarded(int index) {
        return System.getProperty(
                guardedProperties[Math.floorMod(index, guardedProperties.length)]);
    }

    /**
     * Reads an arbitrary property from this domain. Used only by the
     * harness's set-up checks, to confirm that a property the policy does not
     * grant is refused, i.e. that this domain really is restricted.
     *
     * @param name property name
     * @return the property value
     */
    public static String readProperty(String name) {
        return System.getProperty(name);
    }

    /**
     * The access control context as seen from this domain: what a permission
     * check made here would evaluate. Used only by the diagnostic
     * {@code CheckBreakdown} benchmarks, to time the context capture on its
     * own and to see whether repeated captures return the cached instance.
     *
     * @return {@code AccessController.getContext()} called from this class
     */
    @SuppressWarnings("removal")
    public static java.security.AccessControlContext context() {
        return java.security.AccessController.getContext();
    }

    /**
     * Handles one request: parses {@code query} as {@code k=v&k=v...},
     * checksums {@code body}, performs {@code guardedReads} guarded property
     * reads, and formats a response.
     *
     * @param query        URL-style query string
     * @param body         request body
     * @param guardedReads number of permission-checked operations to perform
     * @return a value derived from the whole response, so none of the work
     *         can be eliminated as dead code
     */
    public long handle(String query, byte[] body, int guardedReads) {
        Map<String, String> params = new HashMap<>();
        int start = 0;
        int length = query.length();
        while (start < length) {
            int amp = query.indexOf('&', start);
            if (amp < 0) {
                amp = length;
            }
            int eq = query.indexOf('=', start);
            if (eq < 0 || eq > amp) {
                params.put(query.substring(start, amp), "");
            } else {
                params.put(query.substring(start, eq), query.substring(eq + 1, amp));
            }
            start = amp + 1;
        }

        CRC32 crc = new CRC32();
        crc.update(body, 0, body.length);
        long checksum = crc.getValue();

        StringBuilder response = new StringBuilder(256);
        for (int i = 0; i < guardedReads; i++) {
            response.append(readGuarded(i)).append(';');
        }
        response.append(params.size()).append(':').append(checksum).append('\n');
        for (Map.Entry<String, String> e : params.entrySet()) {
            response.append(e.getKey()).append('=').append(e.getValue()).append('\n');
        }
        return response.length() ^ checksum ^ response.charAt(response.length() / 2);
    }
}
