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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Effectively Immutable parameter class for ClassDepend.  
 * When River transitions to Java 5, this will
 * allow easy concurrent programming using the new concurrent utils packages.
 * 
 * This class cannot be instantiated directly, you must use a CDPBuilder to
 * return a ClassDependParamters object instance.
 * 
 * @author Peter Firmstone
 * @see ClassDepend
 * @see CDPBuilder
 */
public class ClassDependParameters {
    /* outsidePackagesOrClasses excluded from search ,excludes the names of classes,
     * or package patterns, that should be excluded from the computation */

    private final String[] outsidePackagesOrClasses;
    private final String[] insidePackages; // package scope to search for dependencies in.
    private final String[] showPackages; //Show only the dependencies found in these Packages.
    private final String[] hidePackages; //Hide these packages from output, the dependencies are still calculated.
    private final boolean ignoreOuterParentClass; // For internal classes to ignore their parent class.
    private final boolean excludePlatformClasses;
    private final boolean edges;

    private ClassDependParameters(CDPBuilder builder) {

        outsidePackagesOrClasses = (String[]) builder.outsidePackagesOrClasses.toArray(
                new String[builder.outsidePackagesOrClasses.size()]);
        insidePackages = (String[]) builder.insidePackages.toArray(
                new String[builder.insidePackages.size()]);
        showPackages = (String[]) builder.showPackages.toArray(
                new String[builder.showPackages.size()]);
        hidePackages = (String[]) builder.hidePackages.toArray(
                new String[builder.hidePackages.size()]);
        ignoreOuterParentClass = builder.ignoreOuterParentClass;
        excludePlatformClasses = builder.excludePlatformClasses;
        edges = builder.edges;

    }

    private List cloneArraytoList(String[] array) {
        /* We can get away with cloning the Array since Strings are immutable.
         * the copy, a cloned array, has identical object references to String
         * objects contained in the original.  The retrieved ArrayList can be modified
         * without affecting the original array.
         */
        String[] ac = (String[]) array.clone();
        return Arrays.asList(ac);

    }

    /**
     * outsidePackagesOrClasses - excluded from search ,excludes the names 
     * of classes, or package patterns, that should be excluded from the 
     * dependency computation 
     * @see ClassDepend
     * @return outsidePackagesOrClasses
     */
    public List outsidePackagesOrClasses() {
        return cloneArraytoList(outsidePackagesOrClasses);
    }

    public List insidePackages() {
        return cloneArraytoList(insidePackages);
    }
    
    public List showPackages() {
        return cloneArraytoList(showPackages);
    }
    
    public List hidePackages() {
        return cloneArraytoList(hidePackages);
    }
    
    public boolean ignoreOuterParentClass() {
        return ignoreOuterParentClass;
    }

    public boolean excludePlatformClasses() {
        return excludePlatformClasses;
    }

    public boolean edges() {
        return edges;
    }

    /**
     * CDPBuilder - to build an immutable ClassDependParameters object, much
     * like the StringBuilder and String class relationship.
     * 
     * CDP Builder is not threadsafe.
     * 
     * Optional Parameters are set by methods that can be chained on the
     * Builder object, which has a no argument constructor.  
     * The <code>build()</code> method returns the new ClassDependParameters
     * object, the builder can be used to build as many ClassDependParameter
     * objects as desired.
     */
    public static class CDPBuilder {
        /* Lists are good for building, they're dynamically resizable
         * this builder is not threadsafe.
         */

        private List outsidePackagesOrClasses = new ArrayList();
        private List insidePackages = new ArrayList();
        private List showPackages = new ArrayList();
        private List hidePackages = new ArrayList();
        private boolean ignoreOuterParentClass = false;
        private boolean excludePlatformClasses = false;
        private boolean edges = false;

        public CDPBuilder() {
        }

        /**
         * The package patterns or class names to be excluded from the dependency
         * search results.
         * @param outsidePackageOrClass Package pattern or Class to be excluded from
         * dependency checking.
         * A package pattern ending in .* excludes the packages in the package
         * root directory, to decend recursively into and exclude subpackages, 
         * the package pattern should end in .**
         * 
         * @see ClassDepend
         * @see ClassDependParameters
         * @return CDPBuilder so named optional parameters can be chained
         */
        public CDPBuilder addOutsidePackageOrClass(String outsidePackageOrClass) {
            outsidePackagesOrClasses.add(outsidePackageOrClass);
            return this;
        }

        public CDPBuilder addOutsidePackagesOrClasses(String[] outsidePackagesOrClasses) {
            int l = outsidePackagesOrClasses.length;
            for (int i = 0; i < l; i++) {
                this.outsidePackagesOrClasses.add(outsidePackagesOrClasses[i]);
            }
            return this;
        }

        public CDPBuilder addOutsidePackagesOrClasses(List excludes) {
            outsidePackagesOrClasses.addAll(excludes);
            return this;
        }



        /**
         * Inside packages limit the scope of the dependency search to
         * Classes within these packages.
         * @param insidePackage A String pattern including the fully qualified 
         *                      package name, followed by .* to capture classes
         *                      in the packages root directory or by .** to
         *                      include subpackages recursively as well.
         * @return CDPBuilder - enables optional parameter method chaining.
         */
        public CDPBuilder addInsidePackage(String insidePackage) {
            insidePackages.add(insidePackage);
            return this;
        }
        
        /**
         * Inside packages limit the scope of the dependency search to
         * Classes within these packages.
         * @param insidePackages
         * @return CDPBuilder - enables optional parameter method chaining.
         */
        public CDPBuilder addInsidePackages(String[] insidePackages) {
            for (int i = 0, l = insidePackages.length; i < l; i++) {
                this.insidePackages.add(insidePackages[i]);
            }
            return this;
        }

        public CDPBuilder addInsidePackages(List inside) {
            insidePackages.addAll(inside);
            return this;
        }
        
        public CDPBuilder addShowPackages(String [] showPackages){
            for (int i = 0, l = showPackages.length; i < l; i++){
                this.showPackages.add(showPackages[i]);
            }
            return this;
        }
        
        public CDPBuilder addShowPackages(List showPackages){
            this.showPackages.addAll(showPackages);
            return this;
        }
        
        public CDPBuilder addShowPackage(String showPackage){
            this.showPackages.add(showPackage);
            return this;
        }
        
        public CDPBuilder addHidePackages(String [] hidePackages){
            for (int i = 0, l = hidePackages.length; i < l; i++){
                this.hidePackages.add(hidePackages[i]);
            }
            return this;
        }
        
        public CDPBuilder addHidePackages(List hidePackages){
            this.hidePackages.addAll(hidePackages);
            return this;
        }
        
        public CDPBuilder addHidePackage(String hidePackage){
            this.hidePackages.add(hidePackage);
            return this;
        }
        
        public CDPBuilder ignoreOuterParentClass(boolean b) {
            ignoreOuterParentClass = b;
            return this;
        }

        /**
         * This optional parameter if true, excludes Java platform classes
         * from the dependency search.
         * If false the platform classes returned will depend on the Java
         * platform and version the test is executing on.
         * 
         * @see ClassDepend
         * @see ClassDependParameters
         * @param b
         * @return CDPBuilder - enables optional parameter method chaining.
         */
        public CDPBuilder excludePlatformClasses(boolean b) {
            excludePlatformClasses = b;
            return this;
        }

        public CDPBuilder edges(boolean e){
            edges = e;
            return this;
        }

        /**
         * Builds ClassDependParameters immutable object from optional
         * parameters, execute this method last, after setting all optional
         * parameters.
         * @see ClassDependParameters
         * @return ClassDependParameter object
         */
        public ClassDependParameters build() {
            return new ClassDependParameters(this);
        }
    }
}
