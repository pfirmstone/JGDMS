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

package org.apache.river.example.hello;

import java.io.IOException;
import java.net.MalformedURLException;
import net.jini.loader.pref.PreferredClassProvider;
import net.jini.url.httpmd.HttpmdUtil;

/**
 * A preferred class provider that computes HTTPMD URLs to use as the class
 * annotation for classes in the application and bootstrap classpath. The
 * following system properties control the HTTPMD URLs created:
 *
 * o export.codebase -- a space-separated list of the HTTPMD URLs for use as
 *   the codebase. The digest values specified in the URLs will be ignored.
 *   The path portion of the URLs, without the message digest parameters, will
 *   be used to specify the source files, relative to the source directory, to
 *   use for computing message digests.
 *
 * o export.codebase.source -- the name of the directory containing the source
 *   files corresponding to the URLs in the codebase
 *
 * @author Sun Microsystems, Inc.
 * 
 */
public class MdClassAnnotationProvider
    extends PreferredClassProvider
{
    private final String codebase;

    public MdClassAnnotationProvider()
	throws IOException, MalformedURLException
    {
	super(false);
        String codebaseApp = null;
        try {
	    codebaseApp = HttpmdUtil.computeDigestCodebase(
	        System.getProperty("export.codebase.source.app"),
	        System.getProperty("export.codebase.app"));
        } catch(NullPointerException e) { /* properties not set */ }

        String codebaseJsk = null;
        try {
	    codebaseJsk = HttpmdUtil.computeDigestCodebase(
	        System.getProperty("export.codebase.source.jsk"),
	        System.getProperty("export.codebase.jsk"));
        } catch(NullPointerException e) { /* properties not set */ }

        if( (codebaseApp != null) && (codebaseJsk != null) ) {
            codebase = codebaseApp+" "+codebaseJsk;
        } else if(codebaseApp != null) {
            codebase = codebaseApp;
        } else if(codebaseJsk != null) {
            codebase = codebaseJsk;
        } else {
            throw new NullPointerException("no codebase properties defined");
        }
    }

    protected String getClassAnnotation(ClassLoader loader) {
	return codebase;
    }
}
