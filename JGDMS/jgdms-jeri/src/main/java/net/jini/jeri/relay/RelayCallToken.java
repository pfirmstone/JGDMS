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

package net.jini.jeri.relay;

import java.util.Objects;
import java.util.concurrent.Callable;

/**
 * Binds an opaque relay call token to the current thread for the duration of
 * an action.
 *
 * <p>The trusted host registers a caller's subjects with the relay and
 * receives a token for them.  While {@link #callWith} runs its action, every
 * {@link RelayEndpoint#newRequest} on that thread puts the token in the
 * request header, so the relay can attach those subjects to the call.  The
 * token is captured when {@code newRequest} is called.
 *
 * <p>The binding is not inherited by threads started inside the action.
 * There is no public way to read a bound token.
 *
 * @since 4.0
 */
public final class RelayCallToken {

    /** Maximum token length in bytes. */
    static final int MAX_LENGTH = 64;

    private static final RuntimePermission SET_TOKEN_PERMISSION =
        new RuntimePermission("setRelayCallToken");

    private static final ThreadLocal<byte[]> CURRENT = new ThreadLocal<byte[]>();

    private RelayCallToken() {
        throw new AssertionError();
    }

    /**
     * Runs {@code action} with {@code token} bound to the current thread,
     * then restores the binding that was in place before, whether the action
     * returns or throws.  The token is copied.
     *
     * @param <T> the action's result type
     * @param token the token, 1 to 64 bytes
     * @param action the action to run
     * @return the action's result
     * @throws NullPointerException if {@code token} or {@code action} is
     *         {@code null}
     * @throws IllegalArgumentException if the token length is not 1 to 64
     * @throws SecurityException if a security manager is present and
     *         {@code RuntimePermission("setRelayCallToken")} is not granted
     * @throws Exception whatever the action throws
     */
    public static <T> T callWith(byte[] token, Callable<T> action)
        throws Exception
    {
        Objects.requireNonNull(token, "token");
        Objects.requireNonNull(action, "action");
        byte[] copy = token.clone();
        if (copy.length < 1 || copy.length > MAX_LENGTH) {
            throw new IllegalArgumentException(
                "token length must be 1 to " + MAX_LENGTH + ": " + copy.length);
        }
        SecurityManager sm = System.getSecurityManager();
        if (sm != null) {
            sm.checkPermission(SET_TOKEN_PERMISSION);
        }
        byte[] previous = CURRENT.get();
        CURRENT.set(copy);
        try {
            return action.call();
        } finally {
            if (previous == null) {
                CURRENT.remove();
            } else {
                CURRENT.set(previous);
            }
        }
    }

    /** Returns a copy of the token bound to the current thread, or null. */
    static byte[] current() {
        byte[] t = CURRENT.get();
        return t == null ? null : t.clone();
    }
}
