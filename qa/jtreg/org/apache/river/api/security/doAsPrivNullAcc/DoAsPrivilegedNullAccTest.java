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
/* @test
 * @summary Regression: Subject.doAsPrivileged(subject, action, null) under an
 *          installed SecurityManager must not throw ArrayIndexOutOfBoundsException.
 *          A null acc builds an empty assigned context (NULL_PD_ARRAY); a
 *          permission check inside the action (Subject.current()) routes through
 *          getContext -> AccessControlContext.optimize, which previously indexed
 *          acc.context[0] on the empty array when the truncated stack had a single
 *          domain. Exercised standalone and nested inside callAs (the case that
 *          crashed). DirtyChai-only (UserSubject); the null-acc idiom is what every
 *          JGDMS doAsPrivileged call site uses.
 * @run main/othervm/policy=null.policy -Djava.security.manager=default DoAsPrivilegedNullAccTest
 */
import java.security.PrivilegedAction;
import java.util.Set;
import java.util.concurrent.Callable;
import javax.security.auth.Subject;
import javax.security.auth.UserSubject;
import javax.security.auth.x500.X500Principal;

public class DoAsPrivilegedNullAccTest {

    static final X500Principal USER  = new X500Principal("CN=enduser");
    static final X500Principal OTHER = new X500Principal("CN=otheruser");

    public static void main(String[] args) throws Exception {
        if (System.getSecurityManager() == null) throw new AssertionError("expected an installed SecurityManager");

        // 1. standalone null acc: the action triggers a permission check (current()).
        Object r1 = Subject.doAsPrivileged(plain(OTHER), (PrivilegedAction<Object>) Subject::current, null);
        check("standalone doAsPrivileged(.,.,null): no AIOOBE, current() is OTHER",
                r1 instanceof Subject && ((Subject) r1).getPrincipals().contains(OTHER));

        // 2. nested in callAs(USER): the exact path that previously crashed in
        //    AccessControlContext.optimize.
        Subject.callAs((Callable<Void>) () -> {
            Object r2 = Subject.doAsPrivileged(plain(OTHER), (PrivilegedAction<Object>) Subject::current, null);
            check("callAs(USER)->doAsPrivileged(.,.,null): no AIOOBE, current() is OTHER (user replaced)",
                    r2 instanceof Subject
                            && ((Subject) r2).getPrincipals().contains(OTHER)
                            && !((Subject) r2).getPrincipals().contains(USER));
            return null;
        }, user(USER));

        System.out.println("PASSED");
    }

    static UserSubject user(X500Principal p) { return new UserSubject(true, Set.of(p), Set.of(), Set.of()); }
    static Subject     plain(X500Principal p) { return new Subject(true, Set.of(p), Set.of(), Set.of()); }

    static void check(String what, boolean ok) {
        if (!ok) throw new AssertionError("FAIL: " + what);
        System.out.println("ok: " + what);
    }
}
