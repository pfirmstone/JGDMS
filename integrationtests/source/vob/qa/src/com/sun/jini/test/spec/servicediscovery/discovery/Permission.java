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
package com.sun.jini.test.spec.servicediscovery.discovery;

import java.util.logging.Level;

import com.sun.jini.qa.harness.QAConfig;
import com.sun.jini.qa.harness.QATest;
import net.jini.discovery.DiscoveryPermission;
import java.security.PermissionCollection;

public class Permission extends QATest {

    public void run() throws Exception {
        check("foo", "foo", true);
        check("sun.com", "sun.com", true);
        check("*", "foo", true);
        check("*", "*.sun.com", true);
        check("*.sun.com", "foo.sun.com", true);
        check("*.sun.com", "*.foo.sun.com", true);
        check("foo", "bar", false);
        check("foo", "Foo", false);
        check("foo", "*", false);
        check("foo.sun.com", "*.sun.com", false);
        check("*.sun.com", "*", false);
        check("*.foo.sun.com", "*.sun.com", false);
        check("*.sun.com", "sun.com", false);
    }

    private static void check(String src, String dst, boolean res) {
        DiscoveryPermission psrc = new DiscoveryPermission(src);
        DiscoveryPermission pdst = new DiscoveryPermission(dst);

        if (psrc.implies(pdst) != res) {
            throw new SecurityException(src + (res ? " does not imply " :
                    " implies ") + dst);
        }
        PermissionCollection col = psrc.newPermissionCollection();
        col.add(psrc);

        if (col.implies(pdst) != res) {
            throw new SecurityException(src + " in collection " + (res ?
                    " does not imply " : " implies ") + dst);
        }
	col.setReadOnly();
	try {
	    col.add(pdst);
	    throw new SecurityException("read-only collection allowed add");
	} catch (SecurityException e) {
	}
    }

}
