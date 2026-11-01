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
package au.zeus.jgdms.security.jwt;

import java.security.Principal;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * The result of a successful JWT validation, returned by {@link JwtValidator#validate}.
 *
 * @param subject    the {@code sub} claim value, or null if absent
 * @param email      the {@code email} claim value, or null if absent
 * @param groups     the values from the {@code groups} claim array (never null; may be empty)
 * @param allClaims  the well-known and requested top-level claims, as text
 * @param principals the principals the server derives from the same claims
 *                   ({@code sub:}, {@code email:} only if {@code email_verified}
 *                   is JSON true, {@code group:}); at most the server's cap
 *
 * @since 3.1.1
 */
record ParsedJwt(
        String subject,
        String email,
        List<String> groups,
        Map<String, String> allClaims,
        Set<Principal> principals) {

    ParsedJwt {
        Objects.requireNonNull(groups, "groups");
        Objects.requireNonNull(allClaims, "allClaims");
        groups = Collections.unmodifiableList(groups);
        allClaims = Collections.unmodifiableMap(allClaims);
        principals = Collections.unmodifiableSet(
                new LinkedHashSet<>(Objects.requireNonNull(principals, "principals")));
    }
}
