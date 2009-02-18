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
 * @summary Verify basic functionality of Security.getContext() method
 * @run main/othervm/policy=policy Test
 */

import java.security.*;
import net.jini.security.Security;
import net.jini.security.SecurityContext;
import net.jini.security.policy.SecurityContextSource;

class DummyContext implements SecurityContext {
    public PrivilegedAction wrap(PrivilegedAction pa) {
	return null;
    }

    public PrivilegedExceptionAction wrap(PrivilegedExceptionAction pa) {
	return null;
    }

    public AccessControlContext getAccessControlContext() {
	return null;
    }
}

class TestSecurityManager
    extends SecurityManager implements SecurityContextSource
{
    public final SecurityContext context = new DummyContext();
    public SecurityContext getContext() {
	return context;
    }
}

class TestPolicy extends Policy implements SecurityContextSource {
    public final SecurityContext context = new DummyContext();
    private final Policy policy;

    TestPolicy(Policy policy) {
	this.policy = policy;
    }

    public PermissionCollection getPermissions(CodeSource cs) {
	return policy.getPermissions(cs);
    }

    public void refresh() {
	policy.refresh();
    }

    public SecurityContext getContext() {
	return context;
    }
}

public class Test {
    public static void main(String[] args) {
	if (System.getSecurityManager() == null) {
	    System.setSecurityManager(new SecurityManager());
	}

	PrivilegedAction pa = new PrivilegedAction() {
	    public Object run() { return null; }
	};
	PrivilegedExceptionAction pea = new PrivilegedExceptionAction() {
	    public Object run() { return null; }
	};
	SecurityContext ctx = Security.getContext();
	if (ctx.wrap(pa) != pa || ctx.wrap(pea) != pea) {
	    throw new Error("default context should not wrap actions");
	}

	TestSecurityManager tsm = new TestSecurityManager();
	System.setSecurityManager(tsm);
	if (Security.getContext() != tsm.context) {
	    throw new Error("security manager context ignored");
	}

	TestPolicy tp = new TestPolicy(Policy.getPolicy());
	Policy.setPolicy(tp);
	if (Security.getContext() != tsm.context) {
	    throw new Error("security manager context precedence ignored");
	}

	System.setSecurityManager(new SecurityManager());
	if (Security.getContext() != tp.context) {
	    throw new Error("security policy context ignored");
	}
    }
}
