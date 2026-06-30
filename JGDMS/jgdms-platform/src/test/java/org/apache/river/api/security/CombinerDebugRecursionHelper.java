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

import java.lang.reflect.Array;
import java.lang.reflect.Method;
import java.security.AccessController;
import java.security.Permission;
import java.security.Policy;
import java.security.Principal;
import java.security.ProtectionDomain;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.Callable;
import javax.security.auth.Subject;
import javax.security.auth.x500.X500Principal;

/**
 * Launched as a separate JVM by {@link CombinerDebugRecursionTest} with
 * {@code -Djava.security.debug=combiner} and a SecurityManager installed.
 *
 * <p>It binds a read-only Subject that carries a <em>private credential</em> and
 * performs a permission check inside {@code callAs}. That drives
 * {@code AccessController.checkPermission -> getContext -> SubjectDomainCombiner.combine}
 * with combiner debug on. On a JDK whose {@code combine()} debug prints private
 * credentials (via {@code Subject.toString()}), accessing the private credential
 * under the SecurityManager re-enters {@code checkPermission} and recurses to a
 * {@code StackOverflowError}, and the secret is written to the log. With the fix
 * ({@code toString(false)} — principals only) it prints the principal, never the
 * secret, and completes — printing {@code OK}.
 */
public final class CombinerDebugRecursionHelper {

    /** Sentinel the test asserts must NOT appear in the combiner debug output. */
    public static final String SECRET = "TOP-SECRET-PRIVATE-CRED-DO-NOT-LOG";

    public static void main(String[] args) throws Exception {
        final X500Principal principal = new X500Principal("CN=debuguser");
        final Permission probe = new RuntimePermission("combiner.debug.probe");

        // Permissive policy so the installed SecurityManager lets the helper run;
        // the check still EXECUTES (getContext -> combine -> debug), which is the
        // path under test.
        Policy.setPolicy(new Policy() {
            @Override
            public boolean implies(ProtectionDomain domain, Permission permission) {
                return true;
            }
        });
        System.setSecurityManager(new SecurityManager());

        Class<?> userSubject = Class.forName("javax.security.auth.UserSubject");
        Object subject = userSubject
                .getConstructor(boolean.class, Set.class, Set.class, Set.class)
                .newInstance(true,
                        Collections.singleton((Principal) principal),
                        Collections.emptySet(),
                        Collections.singleton(SECRET));   // a private credential

        Method callAs = Subject.class.getMethod("callAs", Callable.class,
                Array.newInstance(userSubject, 0).getClass());
        Object subjects = Array.newInstance(userSubject, 1);
        Array.set(subjects, 0, subject);

        Callable<String> action = () -> {
            AccessController.checkPermission(probe); // -> getContext -> combine -> combiner debug
            return "OK";
        };

        Object result = callAs.invoke(null, action, subjects);
        System.out.println(result);
    }
}
