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
package com.sun.jini.test.impl.mercury;

import java.util.logging.Level;

// Test harness specific classes
import java.io.IOException;

import java.rmi.RemoteException;
import java.util.List;
import java.util.Arrays;

import com.sun.jini.admin.DestroyAdmin;
import com.sun.jini.admin.StorageLocationAdmin;

import net.jini.admin.Administrable;
import net.jini.admin.JoinAdmin;
import net.jini.core.entry.Entry;
import net.jini.core.discovery.LookupLocator;
import net.jini.discovery.LookupDiscovery;
import net.jini.event.EventMailbox;
import net.jini.lookup.entry.Name;


public abstract class AdminIFBase extends MailboxTestBase {

    protected boolean assertLocators(LookupLocator[] got, LookupLocator[] want)
    {
	
	if (got == null || want == null) { // check for null
	    return got == want;
	} else if (want.length == 0) {  // check for empty
	    return (got.length == 0);
	} else { 
	    java.util.List gotList = java.util.Arrays.asList(got);
	    java.util.List wantList = java.util.Arrays.asList(want);
	    return gotList.containsAll(wantList);
	}
    }

    protected boolean assertLookupGroups(String[] got, String[] want) {

        if (got == null || want == null) {
            return got == want;
	} else if (want.length == 0) {
	    return got.length == 0;
	} else {
	    java.util.List gotList = java.util.Arrays.asList(got);
	    java.util.List wantList = java.util.Arrays.asList(want);
	    return gotList.containsAll(wantList);
	}
    }

    protected void dumpGroups(String[] got) {
        if (got == null) 
	    logger.log(Level.INFO, "\t<ALL_GROUPS>");
	else if (got.length == 0) 
	    logger.log(Level.INFO, "\t<NO_GROUPS>");
	else 
	    for (int i = 0; i < got.length; i++) {
	        logger.log(Level.INFO, "\t" + got[i]);
	    }
    }

    protected boolean assertContainsName(Entry[] entries, String name) {
	for (int i = 0; i < entries.length; i++) {
	    if (entries[i] instanceof Name) {
		if(name.equals(((Name)entries[i]).name)) {
		    return true; 
		} 
	    }
	}
        return false;
    }

    protected void waitOnInput() {
        logger.log(Level.INFO, "Hit return to continue test");
        try {
            System.in.read();
        } catch (IOException e) {
            logger.log(Level.INFO, 
		       "IOException while reading before exit " + e);
        }
    }
}
