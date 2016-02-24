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

import org.apache.river.tool.classdepend.ClassDepend;
import org.apache.river.tool.classdepend.ClassDependParameters.CDPBuilder;
import org.apache.river.tool.classdepend.ClassDependencyRelationship;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 *
 * @author peter
 */
public class GenTrustedClasses {
    
    public static void main(String[] args) {
	String classpath = null;
	List<String> providers = new ArrayList<String>();
	ClassDepend classDepend;
	CDPBuilder cdpBuilder = new CDPBuilder();
	List<String> classes = new ArrayList<String>();
	for (int i = 0, l = args.length; i < l; i++){
	    if ("-cp".equals(args[i])){
		i++;
		classpath = args[i];
	    } else if ("-prov".equals(args[i])){
		i++;
		providers.add(args[i]);
	    } else if ("-in".equals(args[i])){
		i++;
		cdpBuilder.addInsidePackage(args[i]);
	    } else {
		classes.add(args[i]);
	    }
	}
	try {
	    classDepend = ClassDepend.newInstance(classpath, null, true);
	} catch (IOException ex) {
	    ex.printStackTrace(System.err);
	    return;
	}
	Map classDependencyRelationMap = null;
        try{
            classDependencyRelationMap = classDepend.getDependencyRelationshipMap(classes, true); // get the reference to Collection<Class>
        } catch (ClassNotFoundException e){
            e.printStackTrace(System.err);
        } catch (IOException e) {
            e.printStackTrace(System.err);
        }
//	ClassDependParameters cdp = cdpBuilder.build();
//	Set result = classDepend.filterClassDependencyRelationShipMap(classDependencyRelationMap, cdp);
	List classDependencyRelations = new ArrayList(providers.size());
	Iterator it = providers.iterator();
	while (it.hasNext()){
	    classDependencyRelations.add(classDependencyRelationMap.get(it.next()));
	}
	Set<String> classNames = new TreeSet<String>();
	it = classDependencyRelations.iterator();
	while (it.hasNext()){
	    ClassDependencyRelationship cdrs = (ClassDependencyRelationship) it.next();
	    Set dependants = cdrs.getDependants();
	    Iterator i = dependants.iterator();
	    while (i.hasNext()){
		classNames.add(i.next().toString());
	    }
	}
	it = classNames.iterator();
	while (it.hasNext()){
	    System.out.println(it.next());
	}
    }
    
}
