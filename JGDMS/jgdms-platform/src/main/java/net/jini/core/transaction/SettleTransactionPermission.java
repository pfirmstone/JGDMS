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
package net.jini.core.transaction;

import java.security.Permission;
import net.jini.security.AccessPermission;

/**
 * Represents the permission required for a Subject to participate in settling
 * (committing or aborting) a distributed transaction. This permission is
 * checked against <em>every</em> Subject participating in the transaction —
 * both the transport Subject and each user Subject propagated via the
 * multi-Subject wire protocol — by
 * {@code TxnManagerImpl.checkAllParticipantsPermission}.  Subjects from each
 * remote Endpoint that joined the transaction are all verified independently.
 *
 * <p>An instance contains a target name but no actions list; you either have
 * the named permission or you don't. Wildcard matching is supported using the
 * syntax specified by {@link AccessPermission}, with the following target
 * names defined for use with a Mahalo transaction manager server:
 *
 * <table border="1" cellpadding="5">
 * <caption>Target names</caption>
 * <tr>
 *   <th>Name</th><th>Matches</th>
 * </tr>
 * <tr>
 *   <td>{@code "commit"}</td>
 *   <td>permission to commit a transaction</td>
 * </tr>
 * <tr>
 *   <td>{@code "abort"}</td>
 *   <td>permission to abort a transaction</td>
 * </tr>
 * <tr>
 *   <td>{@code "settle"}</td>
 *   <td>implies both {@code "commit"} and {@code "abort"}</td>
 * </tr>
 * <tr>
 *   <td>{@code "*"}</td>
 *   <td>all transaction settlement operations</td>
 * </tr>
 * </table>
 *
 * <p>A policy grant of {@code SettleTransactionPermission("settle")} is
 * equivalent to granting both {@code SettleTransactionPermission("commit")}
 * and {@code SettleTransactionPermission("abort")}.
 *
 * @since 3.1
 */
public class SettleTransactionPermission extends AccessPermission {

    private static final long serialVersionUID = 1L;

    /**
     * Creates an instance with the specified target name.
     *
     * @param name the target name ({@code "commit"}, {@code "abort"},
     *             {@code "settle"}, or {@code "*"})
     * @throws NullPointerException if the target name is {@code null}
     * @throws IllegalArgumentException if the target name does not match the
     *         syntax specified in the comments at the beginning of the
     *         {@link AccessPermission} class
     */
    public SettleTransactionPermission(String name) {
        super(name);
    }

    /**
     * Returns {@code true} if this permission implies the specified
     * permission.
     *
     * <p>In addition to the standard {@link AccessPermission} wildcard rules,
     * a target name of {@code "settle"} implies both {@code "commit"} and
     * {@code "abort"}.
     *
     * @param perm the permission to check
     * @return {@code true} if this permission implies {@code perm}
     */
    @Override
    public boolean implies(Permission perm) {
        if (perm == null || perm.getClass() != getClass()) {
            return false;
        }
        if ("settle".equals(getName())) {
            String target = perm.getName();
            return "commit".equals(target) || "abort".equals(target)
                    || "settle".equals(target);
        }
        return super.implies(perm);
    }
}
