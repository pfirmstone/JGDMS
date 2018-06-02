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

package org.apache.river.tool;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.URISyntaxException;
import java.security.KeyStore;
import java.security.Permission;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.TreeSet;
import org.apache.river.api.security.DefaultPolicyParser;
import org.apache.river.api.security.PermissionComparator;
import org.apache.river.api.security.PermissionGrant;
import org.apache.river.api.security.PermissionGrantBuilder;
import org.apache.river.api.security.PolicyParser;

/**
 * This PolicyCondenser can be used to consolidate and condense permission
 * grants in policy files.
 * 
 * The condenser will replace properties in grant files passed in using -Dprop=value
 * 
 * java -cp policy-condenser-3.0-SNAPSHOT.jar;%RIVER.HOME%\lib\* 
 * org.apache.river.tool.PolicyCondenser security.policy
 * 
 * @see KeyStore
 * @author Peter Firmstone
 * @since 3.0.0
 */
public class PolicyCondenser {
    
    public static void main(String [] args) throws Exception{
	PolicyCondenser condenser = new PolicyCondenser();
	for (int i = 0, l = args.length; i < l; i++){
	    condenser.condense(args[i]);
	}
    }

    private PolicyCondenser() 
    {
        super();
    } 
    
    private static File policyFile(String filename) throws URISyntaxException{
       
	File policyFile = new File(filename);
	if (!policyFile.exists()){
	    try {
		policyFile.createNewFile();
	    } catch (IOException ex) {
		throw new RuntimeException("Unable to create a policy file: " + filename, ex);
	    }
	}
        return policyFile;
    }

    private void condense(String arg) throws Exception {
	File policy = policyFile(arg);
	File condensedPolicy = policyFile(arg + ".con");
	PolicyParser parser = new DefaultPolicyParser();
	Collection<PermissionGrant> grantsCol = parser.parse(policy.toURI().toURL(), System.getProperties());
	PermissionGrant [] grants = grantsCol.toArray(new PermissionGrant[grantsCol.size()]);
	int length = grants.length;
	Collection<PermissionGrantBuilder> builders = new ArrayList<PermissionGrantBuilder>(length);
	for (int i = 0; i < length; i++){
	    if (grants[i] == null) continue;
	    PermissionGrantBuilder builder = grants[i].getBuilderTemplate();
	    Collection<Permission> permissions = new TreeSet<Permission>(new PermissionComparator());
	    permissions.addAll(grants[i].getPermissions());
	    for (int j = 0; j < length; j++){
		if (i == j || grants[j] == null) continue;
		if (grants[i].impliesEquivalent(grants[j])){
		    permissions.addAll(grants[j].getPermissions());
		    grants[j] = null;
		}
	    }
	    builder.permissions(permissions.toArray(new Permission[permissions.size()]));
	    builders.add(builder);
	    grants[i] = null;
	}
	PrintWriter pw = new PrintWriter(new BufferedWriter(new FileWriter(condensedPolicy, true)));
	Iterator<PermissionGrantBuilder> builderIt = builders.iterator();
	while(builderIt.hasNext()){
	    pw.print("grant ");
	    pw.print(builderIt.next().build().toString());
	}
	pw.flush();
	pw.close();
    }

}
