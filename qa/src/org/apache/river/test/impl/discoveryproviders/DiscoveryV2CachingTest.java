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

package org.apache.river.test.impl.discoveryproviders;
//harness imports
import org.apache.river.qa.harness.QAConfig;
import org.apache.river.qa.harness.LegacyTest;

import java.lang.ref.Reference;
import java.lang.ref.WeakReference;
import java.lang.ref.SoftReference;
import java.net.URLClassLoader;
import java.net.URL;
import java.util.List;
import java.util.LinkedList;
import org.apache.river.discovery.Discovery;
import org.apache.river.qa.harness.Test;

// Need to refactor LegacyTest methods to an abstract base class if more tests are
// created in this category.
public class DiscoveryV2CachingTest implements LegacyTest {
    protected static QAConfig sysConfig;

    //inherit javadoc
    public Test construct(QAConfig config) {
        sysConfig = config;
        return this;
    }

    //inherit javadoc
    public void tearDown() {
    }

    private static ClassLoader getDummyClassLoader() 
	throws java.net.MalformedURLException 
    {
	return URLClassLoader.newInstance(
	    new URL[] { new URL("file:///discofoo.jar") });

    }

    // Checks to see that the class loader gets GC'd
    private static void checkClassLoaderGC() 
	    throws java.net.MalformedURLException 
    {
	ClassLoader cl = getDummyClassLoader();
	Reference wcr = new WeakReference(cl);
	Discovery d = Discovery.getProtocol2((ClassLoader) cl);
	cl = null;
	System.gc();
	if (wcr.get() != null) {
	    throw new AssertionError("Class loader not freed");
	}
    }

    private static void clearRefs() {
	List l = new LinkedList();
	while (true) {
	    try {
		l.add(new byte[65536]);
	    } catch (OutOfMemoryError e) {
		break;
	    }
	}
    }

    // Force clears all references and makes sure that the Discovery
    // instance is freed. Next, another getProtocol2 call is made to make sure 
    // that a non-null instance is returned for the given class loader.
    private static void checkDiscoveryGC() 
		    throws java.net.MalformedURLException 
    {
	ClassLoader cl = getDummyClassLoader();
	Discovery d = Discovery.getProtocol2((ClassLoader) cl);
	SoftReference sdr = new SoftReference(d);
	d = null;
	clearRefs();
	if (sdr.get() != null) {
	    throw new AssertionError("Discovery instance not freed");
	}
	d = Discovery.getProtocol2(cl);
	if (d == null) {
	    throw new 
	    AssertionError("Discovery instance not allocated");
	}
    }

    /*
     * Performs two tests.
     * 1. It ensures that class loaders used as keys for discovery instance
     * mappings are not pinned unnecessarily.
     * 2. It ensures that discovery instances are garbage collected when
     * memory runs low.
     * LegacyTest 1,2 throws java.lang.AssertionError on failure.
     *
     */
    public void run() throws Exception {
	checkClassLoaderGC();
	checkDiscoveryGC();
    }

}
