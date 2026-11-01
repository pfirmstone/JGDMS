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
package org.apache.river.tool;

import java.security.Permission;

/**
 * A permission whose {@code implies} throws, so that what it grants cannot be told
 * ({@code PolicyLintGateTest.impliesThatThrowsIsCannotTell}).
 */
public final class ThrowingPermission extends Permission {

    private static final long serialVersionUID = 1L;

    public ThrowingPermission(String name) {
        super(name);
    }

    @Override
    public boolean implies(Permission p) {
        throw new IllegalStateException("implies cannot be evaluated");
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof ThrowingPermission && ((ThrowingPermission) o).getName().equals(getName());
    }

    @Override
    public int hashCode() {
        return getName().hashCode();
    }

    @Override
    public String getActions() {
        return "";
    }
}
