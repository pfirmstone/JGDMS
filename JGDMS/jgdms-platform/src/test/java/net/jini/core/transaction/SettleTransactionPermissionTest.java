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

import java.io.FilePermission;
import java.security.Permission;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 * Unit tests for {@link SettleTransactionPermission}.
 */
public class SettleTransactionPermissionTest {

    // --- implies: same target ---

    @Test
    public void commitImpliesCommit() {
        assertTrue(new SettleTransactionPermission("commit")
                .implies(new SettleTransactionPermission("commit")));
    }

    @Test
    public void abortImpliesAbort() {
        assertTrue(new SettleTransactionPermission("abort")
                .implies(new SettleTransactionPermission("abort")));
    }

    @Test
    public void settleImpliesSettle() {
        assertTrue(new SettleTransactionPermission("settle")
                .implies(new SettleTransactionPermission("settle")));
    }

    // --- implies: "settle" covers commit and abort ---

    @Test
    public void settleImpliesCommit() {
        assertTrue(new SettleTransactionPermission("settle")
                .implies(new SettleTransactionPermission("commit")));
    }

    @Test
    public void settleImpliesAbort() {
        assertTrue(new SettleTransactionPermission("settle")
                .implies(new SettleTransactionPermission("abort")));
    }

    // --- implies: "*" covers everything ---

    @Test
    public void wildcardImpliesCommit() {
        assertTrue(new SettleTransactionPermission("*")
                .implies(new SettleTransactionPermission("commit")));
    }

    @Test
    public void wildcardImpliesAbort() {
        assertTrue(new SettleTransactionPermission("*")
                .implies(new SettleTransactionPermission("abort")));
    }

    @Test
    public void wildcardImpliesSettle() {
        assertTrue(new SettleTransactionPermission("*")
                .implies(new SettleTransactionPermission("settle")));
    }

    // --- does NOT imply across operations ---

    @Test
    public void commitDoesNotImplyAbort() {
        assertFalse(new SettleTransactionPermission("commit")
                .implies(new SettleTransactionPermission("abort")));
    }

    @Test
    public void abortDoesNotImplyCommit() {
        assertFalse(new SettleTransactionPermission("abort")
                .implies(new SettleTransactionPermission("commit")));
    }

    @Test
    public void commitDoesNotImplyWildcard() {
        assertFalse(new SettleTransactionPermission("commit")
                .implies(new SettleTransactionPermission("*")));
    }

    @Test
    public void settleDoesNotImplyWildcard() {
        assertFalse(new SettleTransactionPermission("settle")
                .implies(new SettleTransactionPermission("*")));
    }

    // --- null / different class ---

    @Test
    public void impliesNullReturnsFalse() {
        assertFalse(new SettleTransactionPermission("commit").implies(null));
    }

    @Test
    public void impliesDifferentClassReturnsFalse() {
        Permission other = new FilePermission("/tmp/foo", "read");
        assertFalse(new SettleTransactionPermission("commit").implies(other));
    }

    // --- equals / hashCode contract ---

    @Test
    public void equalsSameTarget() {
        SettleTransactionPermission a = new SettleTransactionPermission("commit");
        SettleTransactionPermission b = new SettleTransactionPermission("commit");
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
    }

    @Test
    public void notEqualDifferentTarget() {
        assertNotEquals(
                new SettleTransactionPermission("commit"),
                new SettleTransactionPermission("abort"));
    }

    // --- getName / getActions ---

    @Test
    public void getNameReturnsTarget() {
        assertEquals("commit", new SettleTransactionPermission("commit").getName());
        assertEquals("abort",  new SettleTransactionPermission("abort").getName());
        assertEquals("settle", new SettleTransactionPermission("settle").getName());
        assertEquals("*",      new SettleTransactionPermission("*").getName());
    }

    @Test
    public void getActionsReturnsEmpty() {
        // AccessPermission has no actions
        String actions = new SettleTransactionPermission("commit").getActions();
        assertTrue(actions == null || actions.isEmpty());
    }
}
