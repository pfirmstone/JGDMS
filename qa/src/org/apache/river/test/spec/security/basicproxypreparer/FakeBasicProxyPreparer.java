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

package org.apache.river.test.spec.security.basicproxypreparer;
import java.rmi.RemoteException;
import java.security.Permission;
import net.jini.security.BasicProxyPreparer;
import net.jini.core.constraint.MethodConstraints;


/**
 * class gives possibility to access protected methods and fields
 * of tested BasicProxyPreparer class.
 */
public class FakeBasicProxyPreparer extends BasicProxyPreparer {

    RuntimeException getPermissionsTrap = null;
    RuntimeException verifyTrap = null;
    RuntimeException grantTrap = null;
    RuntimeException setConstraintsTrap = null;

    boolean skipVerify = false;
    boolean remoteExceptionInVerify = false;
    
    public Object storedProxy = null;

    /**
     * Gateway to protected BasicProxyPreparer.verify field
     */
    public boolean getVerifyField() {
	return verify;
    }

    /**
     * Gateway to protected BasicProxyPreparer.methodConstraintsSpecified field
     */
    public boolean getMethodConstraintsSpecifiedField() {
	return methodConstraintsSpecified;
    }

    /**
     * Gateway to protected BasicProxyPreparer.methodConstraints field
     */
    public MethodConstraints getMethodConstraintsField() {
	return methodConstraints;
    }

    /**
     * Gateway to protected BasicProxyPreparer.permissions field
     */
    public Permission[] getPermissionsField() {
	return super.getPermissions(null);
    }

    /**
     * Calls the same BasicProxyPreparer constructor
     */
    public FakeBasicProxyPreparer() {
	super();
    }

    /**
     * Calls the same BasicProxyPreparer constructor
     */
    public FakeBasicProxyPreparer(boolean verify, Permission[] permissions) {
	super(verify, permissions);
    }

    /**
     * Calls the same BasicProxyPreparer constructor
     */
    public FakeBasicProxyPreparer(boolean verify,
			      MethodConstraints methodConstraints,
			      Permission[] permissions)
    {
	super(verify, methodConstraints, permissions);
    }

    /**
     * Gateway to protected BasicProxyPreparer.getMethodConstraints method
     */
    public MethodConstraints getMethodConstraints(Object proxy) {
	return super.getMethodConstraints(proxy);
    }

    /**
     * Gateway to protected BasicProxyPreparer.getPermissions method
     */
    public Permission[] getPermissions(Object proxy) {
        storedProxy = proxy;
        if (getPermissionsTrap != null) {
            throw getPermissionsTrap;
        }
	return super.getPermissions(proxy);
    }

    /**
     * Gateway to protected BasicProxyPreparer.verify method
     */
    public void verify(Object proxy) throws RemoteException {
        storedProxy = proxy;
        if (verifyTrap != null) {
            throw verifyTrap;
        }
        if (remoteExceptionInVerify) {
            throw new RemoteException();
        }
        if (skipVerify) return;
	super.verify(proxy);
    }

    /**
     * Gateway to protected BasicProxyPreparer.grant method
     */
    public void grant(Object proxy) {
        storedProxy = proxy;
        if (grantTrap != null) {
            throw grantTrap;
        }
	super.grant(proxy);
    }

    /**
     * Gateway to protected BasicProxyPreparer.setConstraints method
     */
    public Object setConstraints(Object proxy) {
        storedProxy = proxy;
        if (setConstraintsTrap != null) {
            throw setConstraintsTrap;
        }
	return storedProxy = super.setConstraints(proxy);
    }

    /**
     * Exception e will be thrown in next call of getPermissions
     */
    public void catchGetPermissions(RuntimeException e) {
	getPermissionsTrap = e;
    }

    /**
     * Exception e will be thrown in next call of verify
     */
    public void catchVerify(RuntimeException e) {
	verifyTrap = e;
    }

    /**
     * Exception e will be thrown in next call of verify
     */
    public void skipVerify() {
	skipVerify = true;
    }

    /**
     * Exception e will be thrown in next call of verify
     */
    public void remoteExceptionInVerify() {
	remoteExceptionInVerify = true;
    }

    /**
     * Exception e will be thrown in next call of grant
     */
    public void catchGrant(RuntimeException e) {
	grantTrap = e;
    }
    
    /**
     * Exception e will be thrown in next call of setConstraints
     */
    public void catchSetConstraints(RuntimeException e) {
	setConstraintsTrap = e;
    }
    
}
