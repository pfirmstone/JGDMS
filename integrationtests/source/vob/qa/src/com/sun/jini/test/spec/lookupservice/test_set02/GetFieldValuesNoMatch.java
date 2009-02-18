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

import java.util.logging.Level;
import com.sun.jini.qa.harness.TestException;

import com.sun.jini.test.spec.lookupservice.QATestRegistrar;
import com.sun.jini.test.spec.lookupservice.attribute.Attr12;
import net.jini.core.lookup.ServiceTemplate;
import net.jini.core.entry.Entry;
import java.rmi.RemoteException;

/**
 * This class is used to verify that doing a getFieldValues with a template
 * that matches nothing results in a null return value.
 */
public class GetFieldValuesNoMatch extends QATestRegistrar {

    public void run() throws Exception {
	Class[] types = {GetFieldValuesNoMatch.class};
	Entry[] ents = {new Attr12()};
	Object[] vals = getProxy().getFieldValues(
				 new ServiceTemplate(null, types, ents),
				 0, "i0_12");
	if (vals != null) {
	    throw new TestException("getFieldValues did not return null");
	}
    }
}
