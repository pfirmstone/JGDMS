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
package org.apache.river.outrigger;

import java.io.IOException;

/**
 * Checked refusal thrown when a persistent store recovered at startup is
 * incompatible with this Outrigger implementation -- most importantly when
 * the store's persisted marshalling format is not
 * {@link net.jini.core.constraint.MarshallingFormat#ATOMIC_DER} (Outrigger
 * is DER-only in JGDMS 4.0.0; see
 * {@code SOW-Outrigger-DER-Only-JOSS-Rejection.md}).
 *
 * <p>This is deliberately a <em>checked</em> {@link IOException} subtype,
 * declared by {@link Recover#recoverEntryFormat} and propagated through
 * {@link Store#setupStore}: the refusal must travel the
 * {@code PrivilegedExceptionAction} path in
 * {@code OutriggerServerImpl.start()} so the
 * {@code catch (PrivilegedActionException)} cleanup block runs -- an
 * unchecked throw would bypass that block and leave a live exported
 * endpoint and running non-daemon threads behind a refused store
 * (fail-loud must also be fail-clean).
 *
 * @since 4.0.0
 */
public class IncompatibleStoreException extends IOException {
    private static final long serialVersionUID = 1L;

    public IncompatibleStoreException(String message) {
        super(message);
    }

    public IncompatibleStoreException(String message, Throwable cause) {
        super(message, cause);
    }
}
