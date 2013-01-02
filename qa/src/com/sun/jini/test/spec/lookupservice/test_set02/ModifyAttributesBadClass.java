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
package com.sun.jini.test.spec.lookupservice.test_set02;
import com.sun.jini.qa.harness.QAConfig;
import com.sun.jini.qa.harness.Test;

import java.util.logging.Level;
import com.sun.jini.qa.harness.TestException;

import com.sun.jini.test.spec.lookupservice.QATestRegistrar;
import com.sun.jini.test.spec.lookupservice.attribute.Attr08;
import com.sun.jini.test.spec.lookupservice.attribute.Attr12;
import net.jini.core.lookup.ServiceItem;
import net.jini.core.lookup.ServiceRegistration;
import net.jini.core.lease.UnknownLeaseException;
import net.jini.core.entry.Entry;
import java.rmi.RemoteException;

/**
 * This class is used to verify that doing a modifyAttributes with an
 * entry template that is not the same class as, nor a superclass of,
 * the corresponding update entry, results in an IllegalArgumentException.
 */
public class ModifyAttributesBadClass extends QATestRegistrar {

    private ServiceRegistration reg;

    public Test construct(QAConfig sysConfig) throws Exception {
	super.construct(sysConfig);
	reg = registerItem(new ServiceItem(null, new Long(0), null),
			   getProxy());
        return this;
    }

    public void run() throws Exception {
	try {
	    reg.modifyAttributes(new Entry[]{new Attr08()},
				 new Entry[]{new Attr12()});
	    throw new TestException("modifyAttributes did not "
				  + "throw IllegalArgumentException");
	} catch (IllegalArgumentException e) {
	}
    }
}
