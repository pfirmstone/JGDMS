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

import java.security.CodeSource;
import java.security.Principal;
import java.security.ProtectionDomain;

/**
 * Decorator that allows a {@link PermissionGrant} to be externally voided.
 */
public final class ExternallyVoidablePermissionGrant extends PermissionGrant {

    private volatile boolean externallyVoided;

    public ExternallyVoidablePermissionGrant(PermissionGrant decorated) {
        super(decorated);
        externallyVoided = false;
    }

    /**
     * Marks this grant as void from an external event.
     */
    public void voidGrant() {
        externallyVoided = true;
    }

    @Override
    public boolean implies(ProtectionDomain pd) {
        return !externallyVoided && decorated().implies(pd);
    }

    @Override
    public boolean implies(ClassLoader cl, Principal[] pal) {
        return !externallyVoided && decorated().implies(cl, pal);
    }

    @Override
    public boolean implies(CodeSource codeSource, Principal[] pal) {
        return !externallyVoided && decorated().implies(codeSource, pal);
    }

    @Override
    public boolean impliesEquivalent(PermissionGrant grant) {
        return decorated().impliesEquivalent(grant);
    }

    @Override
    public boolean isDyanamic() {
        return decorated().isDyanamic();
    }

    @Override
    public boolean isVoid() {
        return externallyVoided || decorated().isVoid();
    }

    @Override
    public PermissionGrantBuilder getBuilderTemplate() {
        return decorated().getBuilderTemplate();
    }
}
