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
package com.sun.jini.test.spec.lookupservice.test_set04;
import com.sun.jini.qa.harness.QAConfig;

import java.util.logging.Level;
import com.sun.jini.qa.harness.TestException;

import com.sun.jini.test.spec.lookupservice.QATestRegistrar;
import com.sun.jini.test.spec.lookupservice.attribute.Attr08;
import com.sun.jini.test.spec.lookupservice.attribute.Attr09;
import net.jini.core.lookup.ServiceItem;
import net.jini.core.lookup.ServiceMatches;
import net.jini.core.lookup.ServiceTemplate;
import net.jini.core.lookup.ServiceRegistration;
import net.jini.core.lease.UnknownLeaseException;
import net.jini.core.entry.Entry;
import java.rmi.RemoteException;

/**
 * This class is used to verify that doing a modifyAttributes with
 * templates and updates that modify the same entry twice results
 * in both modifications being performed, and that if the intermediate
 * state (after applying just one update) causes the entry to be a
 * duplicate of another entry, that it does not get deleted.
 *
 */
public class ModifyAttributesOverlap extends QATestRegistrar {

    private ServiceRegistration reg;
    private Attr08 attr0;
    private Attr08 attr1;
    private Attr09 attr2;
    private Attr09 attr3;
    private Entry[] attrs;

    public void setup(QAConfig sysConfig) throws Exception {
	super.setup(sysConfig);

	attr0 = new Attr08();
	attr0.setDefaults();
	attr1 = new Attr08();
	attr1.setDefaults();
	attr1.i0_08 = new Integer(0);

	attr2 = new Attr09();
	attr2.setDefaults();
	attr2.i0_09 = new Integer(0);
	attr3 = new Attr09();
	attr3.setDefaults();

	attrs = new Entry[]{attr0, attr1, attr2, attr3};

	reg = registerItem(new ServiceItem(null, new Long(0), attrs),
			   getProxy());
    }

    public void run() throws Exception {
	Attr08 tmpl0 = new Attr08();
	tmpl0.i0_08 = attr0.i0_08;
	Attr08 mod0 = new Attr08();
	mod0.i0_08 = attr1.i0_08;
	Attr08 tmpl1 = new Attr08();
	tmpl1.i0_08 = attr0.i0_08;
	Attr08 mod1 = new Attr08();
	mod1.b0_08 = new Boolean(true);

	attr0.i0_08 = mod0.i0_08;
	attr0.b0_08 = mod1.b0_08;

	Attr09 tmpl2 = new Attr09();
	tmpl2.i0_09 = attr3.i0_09;
	Attr09 mod2 = new Attr09();
	mod2.b0_09 = new Boolean(true);
	Attr09 tmpl3 = new Attr09();
	tmpl3.i0_09 = attr3.i0_09;
	Attr09 mod3 = new Attr09();
	mod3.i0_09 = attr2.i0_09;

	attr3.i0_09 = mod3.i0_09;
	attr3.b0_09 = mod2.b0_09;

	reg.modifyAttributes(new Entry[]{tmpl0, tmpl1,
					 tmpl2, tmpl3},
			     new Entry[]{mod0, mod1,
					 mod2, mod3});
	ServiceTemplate t = new ServiceTemplate(reg.getServiceID(), null, null);
	ServiceMatches matches = getProxy().lookup(t, 1);
	if (matches.items.length != 1)
	    throw new TestException("wrong number of matches from lookup");
	Entry[] newAttrs = matches.items[0].attributeSets;
	java.util.List list = java.util.Arrays.asList(newAttrs);
	for (int i = 0; i < attrs.length; i++) {
	    if (!list.contains(attrs[i]))
		throw new TestException("missing attribute " + i);
	}
	if (newAttrs.length != attrs.length)
	    throw new TestException("wrong number of attributes");
    }
}
