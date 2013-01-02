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
import com.sun.jini.qa.harness.Test;

import java.util.logging.Level;
import com.sun.jini.qa.harness.TestException;

import com.sun.jini.test.spec.lookupservice.QATestRegistrar;
import com.sun.jini.test.spec.lookupservice.attribute.Attr07;
import com.sun.jini.test.spec.lookupservice.attribute.Attr08;
import net.jini.core.lookup.ServiceItem;
import net.jini.core.lookup.ServiceMatches;
import net.jini.core.lookup.ServiceTemplate;
import net.jini.core.lookup.ServiceRegistration;
import net.jini.core.lease.UnknownLeaseException;
import net.jini.core.entry.Entry;
import java.rmi.RemoteException;

/**
 * This class is used to verify that doing a modifyAttributes always
 * matches on the "pre" state, not the "post" or intermediate state,
 * and that if the intermediate state (after applying just one update)
 * causes the entry to be a duplicate of another "pre" entry state,
 * that it does not get deleted.
 */
public class ModifyAttributesSwap extends QATestRegistrar {

    private ServiceRegistration reg;
    private Attr07 attr0;
    private Attr07 attr1;
    private Attr08 attr2;
    private Attr08 attr3;
    private Entry[] attrs;

    public Test construct(QAConfig sysConfig) throws Exception {
	super.construct(sysConfig);

	attr0 = new Attr07();
	attr0.setDefaults();
	attr1 = new Attr07();
	attr1.setDefaults();
	attr1.i0_07 = new Integer(0);

	attr2 = new Attr08();
	attr2.setDefaults();
	attr3 = new Attr08();
	attr3.setDefaults();
	attr3.i0_08 = new Integer(0);

	attrs = new Entry[]{attr0, attr1, attr2, attr3};

	reg = registerItem(new ServiceItem(null, new Long(0), attrs),
			   getProxy());
        return this;
    }

    public void run() throws Exception {
	Attr07 tmpl0 = new Attr07();
	tmpl0.i0_07 = attr0.i0_07;
	Attr07 mod0 = new Attr07();
	mod0.i0_07 = new Integer(600);
	Attr07 tmpl1 = new Attr07();
	tmpl1.i0_07 = mod0.i0_07;
	Attr07 mod1 = new Attr07();
	mod1.i0_07 = attr0.i0_07;

	attr0.i0_07 = mod0.i0_07;

	Attr07 tmpl2 = new Attr07();
	tmpl2.i0_07 = new Integer(1);
	Attr07 mod2 = new Attr07();
	mod2.i0_07 = attr1.i0_07;
	Attr07 tmpl3 = new Attr07();
	tmpl3.i0_07 = attr1.i0_07;
	Attr07 mod3 = new Attr07();
	mod3.i0_07 = tmpl2.i0_07;

	attr1.i0_07 = tmpl2.i0_07;

	Attr08 tmpl4 = new Attr08();
	tmpl4.i0_08 = attr2.i0_08;
	Attr08 mod4 = new Attr08();
	mod4.i0_08 = attr3.i0_08;
	Attr08 tmpl5 = new Attr08();
	tmpl5.i0_08 = attr3.i0_08;
	Attr08 mod5 = new Attr08();
	mod5.i0_08 = attr2.i0_08;

	reg.modifyAttributes(new Entry[]{tmpl0, tmpl1,
					 tmpl2, tmpl3,
					 tmpl4, tmpl5},
			     new Entry[]{mod0, mod1,
					 mod2, mod3,
					 mod4, mod5});
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
