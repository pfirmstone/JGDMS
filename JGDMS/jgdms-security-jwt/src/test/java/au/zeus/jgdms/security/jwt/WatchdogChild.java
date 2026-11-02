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

import java.io.ByteArrayInputStream;
import java.util.HashMap;
import java.util.Map;
import javax.security.auth.Subject;
import javax.security.auth.login.LoginException;

/**
 * Main class of the child JVMs of {@link WatchdogSecurityManagerTest}.  It plays
 * the application code that calls the JWT module: the policy gives its code
 * source no thread permission.
 */
public final class WatchdogChild {

    private WatchdogChild() {}

    public static void main(String[] args) throws Exception {
        if (System.getSecurityManager() == null) throw new AssertionError("no SecurityManager is installed");
        System.out.println("CHILD-SM " + System.getSecurityManager().getClass().getName());
        switch (args[0]) {
            case "read":
                for (int i = 0; i < 2; i++) {
                    byte[] b = BoundedBody.read(new ByteArrayInputStream(new byte[5]), 10, 1000);
                    System.out.println("CHILD-READ-OK " + b.length);
                }
                break;
            case "expire":
                // A deadline that passes: the closer thread is created, again with no help from this caller.
                final java.util.concurrent.CountDownLatch closed = new java.util.concurrent.CountDownLatch(1);
                try {
                    BoundedBody.read(new java.io.InputStream() {
                        @Override
                        public int read() throws java.io.IOException {
                            try {
                                closed.await();
                            } catch (InterruptedException e) {
                                throw new java.io.IOException(e);
                            }
                            return -1;
                        }

                        @Override
                        public void close() {
                            closed.countDown();
                        }
                    }, 10, 100);
                    throw new AssertionError("no timeout");
                } catch (java.net.SocketTimeoutException expected) {
                    System.out.println("CHILD-EXPIRE-OK");
                }
                break;
            case "login":
                Map<String, String> opts = new HashMap<>();
                opts.put("jwksUri", args[1]);
                opts.put("issuer", args[2]);
                opts.put("token", args[3]);
                JwtLoginModule m = new JwtLoginModule();
                Subject s = new Subject();
                m.initialize(s, null, new HashMap<>(), opts);
                try {
                    if (!m.login() || !m.commit()) throw new AssertionError("login returned false");
                    System.out.println("CHILD-LOGIN-OK principals=" + s.getPrincipals().size());
                } catch (LoginException e) {
                    System.out.println("CHILD-LOGIN-REFUSED " + e.getMessage());
                }
                break;
            case "refresh":
                refresh(args);
                break;
            default:
                throw new IllegalArgumentException(args[0]);
        }
    }

    /**
     * {@code args}: mode, jwksUri, issuer, token, tokenUri.  Logs in with a refresh endpoint, lets the
     * refresh thread run once, logs out.
     */
    private static void refresh(String[] args) throws Exception {
        Map<String, String> opts = new HashMap<>();
        opts.put("jwksUri", args[1]);
        opts.put("issuer", args[2]);
        opts.put("token", args[3]);
        opts.put("refreshTokenUri", args[4]);
        opts.put("refreshToken", "dummy-refresh-token");
        opts.put("clientId", "test-client");
        opts.put("clientSecret", "test-secret");
        JwtLoginModule m = new JwtLoginModule();
        Subject s = new Subject();
        m.initialize(s, null, new HashMap<>(), opts);
        if (!m.login()) throw new AssertionError("login returned false");
        try {
            if (!m.commit()) throw new AssertionError("commit returned false");
        } catch (LoginException e) {
            System.out.println("CHILD-COMMIT-REFUSED " + e.getMessage());
            return;
        }
        java.lang.reflect.Field f = JwtLoginModule.class.getDeclaredField("refreshThread");
        f.setAccessible(true);
        Object task = f.get(m);
        Thread t;
        if (task instanceof Thread) {
            t = (Thread) task;
        } else {
            java.lang.reflect.Field tf = task.getClass().getDeclaredField("thread");
            tf.setAccessible(true);
            t = (Thread) tf.get(task);
        }
        System.out.println("CHILD-COMMIT-OK thread=" + t.getName() + " daemon=" + t.isDaemon()
                + " ccl=" + (t.getContextClassLoader() == ClassLoader.getSystemClassLoader() ? "system" : "other"));
        long end = System.currentTimeMillis() + 20_000;
        while (m.lastRefreshed == null && System.currentTimeMillis() < end) Thread.sleep(100);
        System.out.println(m.lastRefreshed != null ? "CHILD-REFRESHED"
                : "CHILD-NO-REFRESH " + m.lastRefreshFailure);
        m.logout();
        t.join(5000);
        System.out.println("CHILD-LOGOUT alive=" + t.isAlive() + " principals=" + s.getPrincipals().size()
                + " credentials=" + s.getPublicCredentials().size());
    }
}
