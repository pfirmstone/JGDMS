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

/*
 * 
 */

import java.io.Serializable;
import java.net.URL;
import java.rmi.*;
import net.jini.activation.*;
import net.jini.activation.arg.*;
import java.rmi.server.*;
import net.jini.jeri.*;

public class ActivatableImpl implements MyRMI, Serializable {
    
    private ActivationLibrary.ExportHelper helper;
    private boolean classLoaderOk = false;
    
    public ActivatableImpl(ActivationID id, MarshalledObject mobj)
	throws RemoteException, ActivationException
    {
	helper = new ActivationLibrary.ExportHelper(mobj, this, id);
	helper.export();

	ClassLoader thisLoader = ActivatableImpl.class.getClassLoader();
	ClassLoader ccl = Thread.currentThread().getContextClassLoader();
	
	System.err.println("implLoader: " + thisLoader);	
	System.err.println("ccl: " + ccl);

	/*
	 * the context class loader is the ccl from when this object
	 * was exported.  If the bug has been fixed, the ccl will be
	 * the same as the class loader of this class.
	 */
	classLoaderOk = (thisLoader == ccl);
    }

    private Object writeReplace() {
	return helper.getStub();
    }

    public boolean classLoaderOk() throws RemoteException {
	return classLoaderOk;
    }
    
    public void shutdown() throws Exception {
	helper.deactivate();
    }
}
