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
package com.sun.jini.tool.classdepend;

import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

/**
 * A container to store class dependency related information for later analysis.
 * @author Peter Firmstone
 * @see ClassDepend
 */
public class ClassDependencyRelationship {

    private final Set dependants;   // classes that depend upon this class.
    private final Set providers;    // classes that this class depends upon.
    private final String fullyQualifiedClassName;
    private final int hash;
    private final boolean rootClass;
    
    ClassDependencyRelationship (String fullyQualifiedClassName, boolean rootClass){
        this.fullyQualifiedClassName = fullyQualifiedClassName;
        hash = 59 * 7 + (this.fullyQualifiedClassName != null ? this.fullyQualifiedClassName.hashCode() : 0);
        dependants = new HashSet();
        providers = new HashSet();
        this.rootClass = rootClass;
    }
    
    ClassDependencyRelationship (String fullyQualifiedClassName){
        this(fullyQualifiedClassName, false);    
    }
    
    // This is private since we tend to recurse the dependency tree from the
    // dependant end to the provider.
    private void addDependant(ClassDependencyRelationship dependant) {
        synchronized (dependants) {
            dependants.add(dependant);
        }
    }

    /**
     * Add a provider class to this dependant class.
     * @param provider
     */
    public void addProvider(ClassDependencyRelationship provider) {
        synchronized (providers){
            providers.add(provider);
        }
        provider.addDependant(this);
    }

    /**
     * Get the classes dependant on this class.
     * @return classes dependant on this
     */
    public Set getDependants() {
        Set deps = new HashSet();
        //defensive copy
        synchronized (dependants){
            deps.addAll(dependants);
        }
        return deps;
    }

    /**
     * Get the classes that this class needs to function.
     * @return a Set of classes
     */
    public Set getProviders() {
        Set prov = new HashSet();
        //defensive copy
        synchronized (providers){
            prov.addAll(providers);
        }
        return prov;
    }
    
    public String toString(){
        return fullyQualifiedClassName;
    }

    @Override
    public int hashCode() {
        return hash;
    }
    
    public boolean equals(Object o){
        if ( o == null ) return false;
        if (!(o instanceof ClassDependencyRelationship)) return false;
        if (fullyQualifiedClassName.equals(
                ((ClassDependencyRelationship)o).fullyQualifiedClassName))
                return true;
        return false;
    }

    /**
     * If this a root dependant, the class was used to discover dependencies.
     * @return true or false
     */
    public boolean isRootClass() {
        return rootClass;
    }
}
