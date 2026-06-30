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
 * @summary The LIVE SecurityManager.checkPermission enforcement path folds the
 *          callAs-bound Subjects. The check runs inside a doPrivileged with a
 *          restricted context whose only domain is a synthetic, principal-less,
 *          permission-less probe domain (codebase file:/synthetic, NOT granted
 *          anything) — so the conjunctive (principal A AND principal B) grant is
 *          the only path to the permission, reachable only after the live path
 *          folds the bound principals onto that domain. Outside callAs, or with
 *          one Subject, it is denied. DirtyChai-only multi-Subject API.
 * @run main/othervm/policy=check.policy -Djava.security.manager=default MultiSubjectCheckPermissionTest
 */
import java.net.URL;
import java.security.AccessControlContext;
import java.security.AccessControlException;
import java.security.AccessController;
import java.security.CodeSource;
import java.security.Permission;
import java.security.PrivilegedAction;
import java.security.ProtectionDomain;
import java.security.cert.Certificate;
import java.util.Set;
import java.util.concurrent.Callable;
import javax.security.auth.Subject;
import javax.security.auth.UserSubject;
import javax.security.auth.x500.X500Principal;

public class MultiSubjectCheckPermissionTest {

    static final X500Principal ALICE = new X500Principal("CN=alice");
    static final X500Principal BOB   = new X500Principal("CN=bob");
    static final Permission BOTH = new RuntimePermission("multiSubject.bothPartiesRequired");

    /** A synthetic probe domain: codebase file:/synthetic, no principals, no permissions. */
    static final AccessControlContext PROBE = probeContext();

    public static void main(String[] args) throws Exception {
        if (System.getSecurityManager() == null) {
            throw new AssertionError("expected an installed SecurityManager");
        }
        check("no Subject bound: BOTH denied", liveCheck(BOTH), false);

        Subject.callAs((Callable<Void>) () -> {
            check("callAs(A,B): live SM.checkPermission folds both => granted", liveCheck(BOTH), true);
            return null;
        }, user(ALICE), user(BOB));

        Subject.callAs((Callable<Void>) () -> {
            check("callAs(A): conjunction unmet => denied", liveCheck(BOTH), false);
            return null;
        }, user(ALICE));

        System.out.println("PASSED");
    }

    /** Live SM check against the probe domain; bound Subjects (ScopedValue) survive doPrivileged. */
    static boolean liveCheck(Permission p) {
        return AccessController.doPrivileged((PrivilegedAction<Boolean>) () -> {
            try {
                System.getSecurityManager().checkPermission(p);
                return Boolean.TRUE;
            } catch (AccessControlException denied) {
                return Boolean.FALSE;
            }
        }, PROBE);
    }

    static AccessControlContext probeContext() {
        try {
            ProtectionDomain syn = new ProtectionDomain(
                    new CodeSource(new URL("file:/synthetic"), (Certificate[]) null), null, null, null);
            return new AccessControlContext(new ProtectionDomain[]{syn});
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    static UserSubject user(X500Principal p) { return new UserSubject(true, Set.of(p), Set.of(), Set.of()); }

    static void check(String what, boolean actual, boolean expected) {
        if (actual != expected) {
            throw new AssertionError("FAIL: " + what + " -- expected " + expected + " got " + actual);
        }
        System.out.println("ok: " + what + " = " + actual);
    }
}
