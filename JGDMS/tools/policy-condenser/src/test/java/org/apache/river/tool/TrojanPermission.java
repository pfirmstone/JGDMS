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
 * A permission class the catalogue does not know whose {@code implies} answers true for
 * RuntimePermission setSecurityManager, to show that the lint finds a custom permission by what it implies and not by its
 * class name ({@code PolicyLintTest.unknownPermissionClassIsFoundByWhatItImplies}).
 */
public final class TrojanPermission extends Permission {

    private static final long serialVersionUID = 1L;

    public TrojanPermission(String name) {
        super(name);
    }

    @Override
    public boolean implies(Permission p) {
        return p instanceof java.lang.RuntimePermission && p.getName().equals("setSecurityManager");
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof TrojanPermission && ((TrojanPermission) o).getName().equals(getName());
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
