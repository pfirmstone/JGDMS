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

package com.sun.jini.start;

import java.io.IOException;
import net.jini.config.Configuration;
import net.jini.security.ProxyPreparer;
import org.apache.river.api.io.AtomicSerial;
import org.apache.river.start.LifeCycle;

/**
 * This class is not serial form compatible with Jini 2.1.  This is provided
 * for api compatibility with Jini 2.1.  Please upgrade at your earliest
 * convenience.
 */
@Deprecated
@AtomicSerial
public class NonActivatableServiceDescriptor extends org.apache.river.start.NonActivatableServiceDescriptor {

    public NonActivatableServiceDescriptor(String exportCodebase, String policy, String importCodebase, String implClassName, String[] serverConfigArgs, LifeCycle lifeCycle, ProxyPreparer preparer) {
	super(exportCodebase, policy, importCodebase, implClassName, serverConfigArgs, lifeCycle, preparer);
    }
    public NonActivatableServiceDescriptor(AtomicSerial.GetArg arg) throws IOException {
	super(arg);
    }

    public NonActivatableServiceDescriptor(String exportCodebase, String policy, String importCodebase, String implClassName, Configuration config, LifeCycle lifeCycle, ProxyPreparer preparer) {
	super(exportCodebase, policy, importCodebase, implClassName, config, lifeCycle, preparer);
    }

    public NonActivatableServiceDescriptor(String exportCodebase, String policy, String importCodebase, String implClassName, String[] serverConfigArgs, LifeCycle lifeCycle) {
	super(exportCodebase, policy, importCodebase, implClassName, serverConfigArgs, lifeCycle);
    }
    
    public NonActivatableServiceDescriptor(String exportCodebase, String policy, String importCodebase, String implClassName, String[] serverConfigArgs) {
	super(exportCodebase, policy, importCodebase, implClassName, serverConfigArgs);
    }

    public NonActivatableServiceDescriptor(String exportCodebase, String policy, String importCodebase, String implClassName, String[] serverConfigArgs, ProxyPreparer preparer) {
	super(exportCodebase, policy, importCodebase, implClassName, serverConfigArgs, preparer);
    }
    
    @Override
    public Object create(Configuration config) throws Exception {
	Object created = super.create(config);
	if (created instanceof org.apache.river.start.NonActivatableServiceDescriptor.Created){
	    org.apache.river.start.NonActivatableServiceDescriptor.Created crt =
		(org.apache.river.start.NonActivatableServiceDescriptor.Created) created;
	    return new Created(crt.impl, crt.proxy);
	}
	return created;
    }
    
    public static class Created extends org.apache.river.start.NonActivatableServiceDescriptor.Created {

	public Created(Object impl, Object proxy) {
	    super(impl, proxy);
	}
	
    }
    
}
