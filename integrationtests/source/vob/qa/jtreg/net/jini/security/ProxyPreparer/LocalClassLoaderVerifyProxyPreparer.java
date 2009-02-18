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
import java.rmi.RemoteException;
import java.security.Permission;
import java.util.Collections;
import net.jini.core.constraint.MethodConstraints;
import net.jini.security.BasicProxyPreparer;
import net.jini.security.Security;

/**
 * A basic proxy preparer that uses this class's class loader when verifying
 * trust in proxies.
 */
public class LocalClassLoaderVerifyProxyPreparer extends BasicProxyPreparer {

    public LocalClassLoaderVerifyProxyPreparer(boolean verify,
					      Permission[] permissions)
    {
	super(verify, permissions);
    }

    public LocalClassLoaderVerifyProxyPreparer(
	boolean verify, 
	MethodConstraints methodConstraints,
	Permission[] permissions)
    {
	super(verify, methodConstraints, permissions);
    }

    protected void verify(Object proxy) throws RemoteException {
	if (proxy == null) {
	    throw new NullPointerException("Proxy cannot be null");
	} else if (verify) {
	    MethodConstraints mc = getMethodConstraints(proxy);
	    Security.verifyObjectTrust(
		proxy, getClass().getClassLoader(),
		mc == null ? Collections.EMPTY_SET : Collections.singleton(mc));
	}
    }
}
