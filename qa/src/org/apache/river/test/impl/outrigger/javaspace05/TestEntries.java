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
package org.apache.river.test.impl.outrigger.javaspace05;

import java.util.Collection;
import java.util.Iterator;
import java.util.Random;
import net.jini.id.UuidFactory;

/**
 * Utilities for creating and working with test entries.
 */
class TestEntries {
    static final private TestEntry[] prototypes = new TestEntry[] {
	new A0(), 
	new A00(), new A01(), 
	new A000(), new A001(), new A010(), new A011(),
	new B0(),
	new B00(), new B01(),
	new C0()
    };
    static final private String colors[] = new String[]{"red","blue"};

    static private Random random = new Random();

    static private TestEntry pickProto() {
	final int i = random.nextInt(prototypes.length);
	return prototypes[i].dup();
    }

    static TestEntry newEntry() {
	final TestEntry rslt = pickProto();
	rslt.setUuid(UuidFactory.generate());
	rslt.setColor(colors[random.nextInt(colors.length)]);
	return rslt;
    }

    static Collection newTemplates() {
	final Collection rslt = new java.util.LinkedList();
	final int disc = random.nextInt(11);
	if (disc == 0) {
	    rslt.add(null);
	    return rslt;
	}

	final int limit;
	if (disc < 6) {
	    limit = 3;
	} else {
	    limit = prototypes.length * 3;
	}

	final int tmplCount = random.nextInt(limit-1) + 1;
	for (int i=0; i<tmplCount; i++) {
	    final TestEntry t = pickProto();
	    final int r = random.nextInt(colors.length+1);
	    if (r < colors.length)
		t.setColor(colors[r]);
	    // else leave null

	    rslt.add(t);
	}

	return rslt;
    }

    static boolean isMatch(TestEntry tmpl, TestEntry target) {
	if (tmpl == null)
	    return true;

	if (!tmpl.getClass().isInstance(target))
	    return false;
	if (tmpl.getColor() == null)
	    return true;
	return tmpl.getColor().equals(target.getColor());
    }


    static boolean isMatch(Collection tmpls, TestEntry target) {
	for (Iterator k=tmpls.iterator(); k.hasNext();) {
	    final TestEntry tmpl = (TestEntry)k.next();
	    if (isMatch(tmpl, target)) {
		return true;
	    }
	}

	return false;
    }

    static String toString(TestEntry e) {
	if (e == null)
	    return "null";

	final String className = e.getClass().getName();
	return className.substring(className.lastIndexOf(".")+1) +
	    ":" + e.getColor();
    }

    static String toString(Collection c) {
	final StringBuffer buf = new StringBuffer();
	for (Iterator i=c.iterator(); i.hasNext(); ) {
	    final TestEntry e = (TestEntry)i.next();
	    buf.append(toString(e));
	    buf.append(" ");
	}
	return buf.toString();
    }
}
