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

import com.sun.jini.tool.classdepend.ClassDependParameters.CDPBuilder;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.regex.Pattern;

/**
 * Provides a utility for computing which classes are depended on by a set of
 * classes.  This class is not thread safe.
 */
public class ClassDepend {

    /** The system classpath. */
    private static final String systemClasspath =
	System.getProperty("java.class.path");

    /**
     * The class loader used to load classes being checked for dependencies.
     */
    private final ClassLoader loader;

    /**
     * The class loader for classes that should be excluded because they are
     * considered part of the platform.
     */
    private final ClassLoader platformLoader;

    /**
     * Used to compute the classes available in the classpath for a package.
     */
    private final PackageClasses packageClasses;
        
    private volatile boolean printClassesWithFileSeparator = false;
    
    /**
     * Public Factory method for creating a new instance of ClassDepend.
     * 
     * The <code>classpath</code> argument
     * specifies the classpath that will be used to look up the class bytecode
     * for classes whose dependencies are being computed.  If the value
     * specified is <code>null</code>, then the system class loader will be
     * used.  Otherwise, a {@link URLClassLoader} will be constructed using the
     * URLs specified by <code>classpath</code> and with a parent class loader
     * that is the parent of the system class loader.  The
     * <code>platform</code> argument specifies the classpath that will be used
     * to find classes that should be considered part of the platform and
     * should be excluded from consideration when computing dependencies.  If
     * the value specified is <code>null</code>, then the parent of the system
     * class loader will be used.  Otherwise, a <code>URLClassLoader</code>
     * will be constructed using the URLs specified by <code>platform</code>
     * and with a parent class loader that is the parent of the system class
     * loader.
     * 
     * @param classpath the classpath for finding classes, or
     *		<code>null</code>
     * @param platform the classpath for finding platform classes, or
     *		<code>null</code>
     * @param warn print warnings instead of throwing an exception when a Class
     *          can't be found or when ClassLoading fails.
     * @return ClassDepend
     * @throws java.net.MalformedURLException
     * @throws java.io.IOException
     */
    public static ClassDepend newInstance(String classpath, String platform, boolean warn)
            throws MalformedURLException, IOException{       
            /* Explanation for us mere mortals.
             * Ternary conditional operator:
             * Object ob = expression ? this if true : else this;
             * ClassDepend classdepend = if warn not true, then new ClassDepend(), else
             * new anonymous class that extends ClassDepend
             * with noteClassNotFound and
             * noteClassLoadingFailed method overrides.
             *
             * This prevents exceptions from being thrown and prints warnings
             * instead on the System err
             * 
             * Using a Factory method to return a new instance allows
             * us to return different versions of ClassDepend, as we have 
             * here by overriding the default methods for debugging.
             */
            ClassDepend classDepend = !warn 
                    ? new ClassDepend(classpath, platform) 
                    : new ClassDepend(classpath, platform) {
                        protected void noteClassNotFound(String name) {
                            System.err.println("Warning: Class not found: " + name);
                        }
                        protected void noteClassLoadingFailed(String name, IOException e) {
                            System.err.println("Warning: Problem loading class " 
                                    + name + ": " + e.getMessage());
                        }
            };
        return classDepend;
    }

    public static void main(String[] args) {
	try {
            CDPBuilder cdpb = new CDPBuilder();
	    String classpath = null;
	    String platform = null;
            Set rootClasses = new HashSet();
            boolean recurse = true;
	    boolean warn = false; //supress exceptions, print to error, warn instead
	    boolean files = false; //print class with file path separator
            boolean graph = false; //print dependency relation ships between classes.
	    for (int i = 0; i < args.length; i++) {
		String arg = args[i];
		if (arg.equals("-cp")) {
		    classpath = args[++i];
		} else if (arg.equals("-platform")) {
		    platform = args[++i];
		} else if (arg.equals("-exclude")) {
		    cdpb.addOutsidePackageOrClass(args[++i]);
		} else if (arg.equals("-norecurse")) {
		    recurse = false;
		} else if (arg.equals("-warn")) {
		    warn = true;
		} else if (arg.equals("-files")) {
		    files = true;
                } else if (arg.equals("-graph")) {
                    graph = true;
                } else if (arg.equals("-excljava")) {
                    cdpb.excludePlatformClasses(true);
		} else if (arg.startsWith("-")) {
		    throw new IllegalArgumentException("Bad option: " + arg);
		} else {
		    rootClasses.add(arg);
		}
	    }
            ClassDependParameters cdp = cdpb.build();          
	    ClassDepend classDepend = ClassDepend.newInstance(classpath, platform, warn);
            Set result = classDepend
                    .filterClassDependencyRelationShipMap(
                    classDepend.getDependencyRelationshipMap(rootClasses, recurse),
                    cdp);
            Iterator results = result.iterator();
            while (results.hasNext()){
                Object rezult = results.next();
                if ( !(rezult instanceof ClassDependencyRelationship )) continue;
                ClassDependencyRelationship cl = (ClassDependencyRelationship) rezult;
                String str = cl.toString();
		if (files) {
		    str = str.replace('.', File.separatorChar).concat(".class");
                    System.out.println(str);
		}
		if (graph) {
                    Set deps = cl.getProviders();
                    Iterator itr = deps.iterator();
                    while (itr.hasNext()){
                        Object dep = itr.next();
                        if ( result.contains(dep)) {
                            System.out.println("\"" + cl + "\""+ " -> " + 
                                "\"" + dep + "\"" + ";");
                        }
                    }
                }
	    }
	} catch (Throwable e) {
	    e.printStackTrace();
	    System.exit(1);
	}
    }
	
    /**
     * Creates an instance of this class.  The <code>classpath</code> argument
     * specifies the classpath that will be used to look up the class bytecode
     * for classes whose dependencies are being computed.  If the value
     * specified is <code>null</code>, then the system class loader will be
     * used.  Otherwise, a {@link URLClassLoader} will be constructed using the
     * URLs specified by <code>classpath</code> and with a parent class loader
     * that is the parent of the system class loader.  The
     * <code>platform</code> argument specifies the classpath that will be used
     * to find classes that should be considered part of the platform and
     * should be excluded from consideration when computing dependencies.  If
     * the value specified is <code>null</code>, then the parent of the system
     * class loader will be used.  Otherwise, a <code>URLClassLoader</code>
     * will be constructed using the URLs specified by <code>platform</code>
     * and with a parent class loader that is the parent of the system class
     * loader.
     *
     * @param	classpath the classpath for finding classes, or
     *		<code>null</code>
     * @param	platform the classpath for finding platform classes, or
     *		<code>null</code>
     * @throws	MalformedURLException if the URLs specified in the arguments
     *		are malformed
     * @throws	IOException if an I/O error occurs while accessing files in the
     *		classpath 
     */
    ClassDepend(String classpath, String platform) 
            throws MalformedURLException, IOException {
	if (classpath == null) {
	    classpath = systemClasspath;
	}
	ClassLoader system = ClassLoader.getSystemClassLoader();
	ClassLoader parent = system.getParent();
	loader = (systemClasspath.equals(classpath))
	    ? system
	    : new URLClassLoader(getClasspathURLs(classpath), parent);
	packageClasses = new PackageClasses(classpath);
	platformLoader = (platform == null)
	    ? parent
	    : new URLClassLoader(getClasspathURLs(platform), parent);
        //System.out.println(platformLoader.toString());
    }

    /**
     * This method builds the entire DependencyRelationShipMap that can be
     * used to analyse class dependencies.
     * 
     * Computes information about class dependencies.  The computation of
     * dependencies starts with the classes that match the names in
     * <code>rootClasses</code>.  Classes are found in a URL class loader by the
     * <code>classpath</code> specified in the constructor. 
     *
     * @param rootClasses 
     * @param recurse If true, this option causes ClassDepend to inspect class 
     * files for dependencies.
     * If false, ClassDepend doesn't inspect class files, it simply
     * gathers the names of class files from within Package directories and 
     * JAR files.
     * @return result
     * @throws java.io.IOException
     * @throws java.lang.ClassNotFoundException
     * @see ClassDependencyRelationship
     */
    public Map getDependencyRelationshipMap(Collection rootClasses, boolean recurse) 
            throws IOException, ClassNotFoundException {
        Map result = new HashMap(); // May be changed to ConcurrentHashMap for Java 5
        Set seen = new HashSet();
        Set compute = computeClasses(rootClasses);      
	while (!compute.isEmpty()) {
            Set computeNext = new HashSet(); //built from discovered dependencies
            Iterator computeIterator = compute.iterator();            
	    while (computeIterator.hasNext()) {
                String name = (String) computeIterator.next();
                if ( !seen.contains(name)){
                    seen.add(name);
                    if (rootClasses.contains(name)){
                        // Put all root classes into ClassDependencyRelationship containers
                        ClassDependencyRelationship rootClass = new ClassDependencyRelationship(name, true);
                        result.put(name, rootClass);
                    }
                    Set providerClassNames = new HashSet();
		    String resource = getResourceName(name);
                    if (recurse) {
			InputStream in = loader.getResourceAsStream(resource);
			if (in == null) {
			    noteClassNotFound(name);
			} else {
			    try {
                                // Discover the referenced classes by loading classfile and inspecting
                                providerClassNames = ReferencedClasses.compute(
                                        new BufferedInputStream(in));
                                computeNext.addAll(providerClassNames);
			    } catch (IOException e) {
				noteClassLoadingFailed(name, e);
			    } finally {
				try {
				    in.close();
				} catch (IOException e) {
				}
			    }
			}
		    } else if (loader.getResource(resource) == null) {
			noteClassNotFound(name);
		    }
                    /* Now we add all the provider classes to the dependant
                     * this is useful for edges or classes of interest where 
                     * we my want to pick points to recurse through dependents 
                     * instead of providers.
                     */
                   Iterator iter = providerClassNames.iterator();
                    while (iter.hasNext()){
                        String provider = (String) iter.next();
                        ClassDependencyRelationship providerClass;
                        if (!result.containsKey(provider)){
                            providerClass = new ClassDependencyRelationship(provider);
                            result.put(provider, providerClass);
                        }else{
                            providerClass = (ClassDependencyRelationship) result.get(provider);
		}
                        ((ClassDependencyRelationship) result.get(name)).addProvider(providerClass);
	    }
                }
            }
            /* The old list is exhausted, lets iterate through our newly
             * discovered collection.
             */
	    compute = computeNext;
	}
	return result;
    }

    /**
     * This method applies optional filters to provide methods to support the
     * original API of ClassDep.
     * @param dependencyRelationShipMap The initial map before filtering.
     * @param cdp The parameters for filtration.
     * @see ClassDependParameters
     * @see ClassDependencyRelationship
     * @return Set<ClassDependencyRelationShip> result The result after filtration.
     */
    public Set filterClassDependencyRelationShipMap(Map dependencyRelationShipMap, ClassDependParameters cdp){
        Set result = new HashSet(); // final result
        Set preliminaryResult = new HashSet();
        
        Pattern excludePattern = createPattern(cdp.outsidePackagesOrClasses());     
        Pattern includePattern = createPattern(cdp.insidePackages());
        Pattern hidePattern = createPattern(cdp.hidePackages());
        Pattern showPattern = createPattern(cdp.showPackages());      
        Collection classRelations = dependencyRelationShipMap.values();
        // get the root class set.
        Set rootClasses = new HashSet();
        {
            Iterator itr = classRelations.iterator();
            while (itr.hasNext()){
                ClassDependencyRelationship cdr = (ClassDependencyRelationship) itr.next();
                if (cdr.isRootClass()){
                    rootClasses.add(cdr);
                }
            }
        }
        // traverse the tree from root dependant to providers
        while ( !rootClasses.isEmpty() ){
            Set computeNext = new HashSet();
            Iterator computeIterator = rootClasses.iterator();
            while (computeIterator.hasNext()){
                ClassDependencyRelationship cdr = (ClassDependencyRelationship) computeIterator.next();
                String name = cdr.toString();
                    // filter out the classes as requested
                    if ( !preliminaryResult.contains(cdr) && 
                        ( !cdp.excludePlatformClasses() || !classPresent(name, platformLoader) ) &&
                        !matches(name, excludePattern) && 
                        ( cdp.insidePackages().size() == 0 || matches(name, includePattern)))
                    {
                        // TODO remove the outer parent class if requested
                        preliminaryResult.add(cdr);
                        computeNext.addAll(cdr.getProviders());
                    }
            }
            rootClasses = computeNext;
        }
        // populate the result with the edge classes if requested
        if (cdp.edges()){
            Iterator itr = preliminaryResult.iterator();
            while (itr.hasNext()) {
                ClassDependencyRelationship cdr = (ClassDependencyRelationship) itr.next();
                result.addAll(cdr.getProviders());
            }
            /* edge classes aren't in the filtered preliminary result set ;), 
             * so remove it just in case some classes from the preliminary
             * result set snuck back in via the provider Sets;
             */
            result.removeAll(preliminaryResult); 
        }else{
            result = preliminaryResult;
        }
        
        /* If we have shows or hides, we want to remove these from
         * the result Collection, under certain conditions.
         */
        Set remove = new HashSet();
        Iterator itr = result.iterator();
        while (itr.hasNext()){
            ClassDependencyRelationship cdr = (ClassDependencyRelationship) itr.next();
            String name = cdr.toString();
            if(( matches(name,hidePattern)|| ( showPattern != null && !matches(name, showPattern))))
            {
                remove.add(cdr);
            }
        }
        result.removeAll(remove);
        return result;
    }

    /**
     * Called when the specified class is not found. <p>
     *
     * This implementation throws a <code>ClassNotFoundException</code>.
     *
     * @param	name the class name
     * @throws	ClassNotFoundException to signal that processing should
     *		terminate and the exception should be thrown to the caller
     */
    protected void noteClassNotFound(String name)
	throws ClassNotFoundException
    {
	throw new ClassNotFoundException("Class not found: " + name);
    }

    /**
     * Called when attempts to load the bytecodes for the specified class fail.
     *
     * @param	name the class name
     * @param	e the exception caused by the failure
     * @throws	IOException to signal that processing should terminate and the
     *		exception should be thrown to the caller
     */
    protected void noteClassLoadingFailed(String name, IOException e)
	throws IOException
    {
	throw e;
    }

    /**
     * Returns the classes in the classpath that match the specified names by
     * expanding package wildcards.
     */
    private Set computeClasses(Collection names)
	throws IOException
    {
	Set result = new HashSet();
        Iterator namesIterator = names.iterator();
	while (namesIterator.hasNext()) {
            String name = (String) namesIterator.next();
	    if (name.endsWith(".*")) {
		name = name.substring(0, name.length() - 2);
		result.addAll(packageClasses.compute(false, name));
	    } else if (name.endsWith(".**")) {
		name = name.substring(0, name.length() - 3);
		result.addAll(packageClasses.compute(true, name));
	    } else {
		result.add(name);
	    }
	}
	return result;
    }

    /** Returns the URLs associated with a classpath. */
    private URL[] getClasspathURLs(String classpath)
	throws MalformedURLException
    {
	StringTokenizer tokens =
	    new StringTokenizer(classpath, File.pathSeparator);
	URL[] urls = new URL[tokens.countTokens()];
	for (int i = 0; tokens.hasMoreTokens(); i++) {
	    String file = tokens.nextToken();
	    try {
		urls[i] = new File(file).toURI().toURL();
	    } catch (MalformedURLException e) {
		urls[i] = new URL(file);
	    }
	}
	return urls;
    }

    /** Checks if the class is present in the given loader. */
    private boolean classPresent(String name, ClassLoader loader) {
	return loader.getResource(getResourceName(name)) != null;
    }

    /** Returns the name of the resource associated with a class name. */
    private String getResourceName(String classname) {
	return classname.replace('.', '/').concat(".class");
    }

    /**
     * Creates a pattern that matches class names for any of the names in the
     * argument.  Returns null if the argument is empty.  xNames that end in
     * '.*' match classes in the package, names that end in '.**' match classes
     * in the package and it's subpackage.  Other names match the class.
     * @param names
     * @return Pattern
     */
    public Pattern createPattern(Collection names) {
	if (names.isEmpty()) {
	    return null;
	}
	StringBuffer sb = new StringBuffer();
	boolean first = true;
        Iterator namesItr = names.iterator();
	while (namesItr.hasNext()) {
            String name = (String) namesItr.next();
	    if (!first) {
		sb.append('|');
	    } else {
		first = false;
	    }
	    if (name.endsWith(".*")) {
		sb.append(
		    quote( name.substring(0, name.length() - 1)) +
		    "[^.]+");
	    } else if (name.endsWith(".**")) {
		sb.append(
		    quote(name.substring(0, name.length() - 2)) +
		    ".+");
	    } else {
		sb.append(quote(name));
	    }
	}
	return Pattern.compile(sb.toString());
    }

    /**
     * Checks if the string matches the pattern, returning false if the pattern
     * is null.
     * @param string
     * @param pattern
     * @return True if the Pattern Matches
     */
    public boolean matches(String string, Pattern pattern) {
	return pattern != null && pattern.matcher(string).matches();
    }
    
    /**
     * Returns a literal pattern String for the specified String.
     * Added to backport Java 1.5 sources to 1.4.  adds the functionality
     * of java.util.regex.Pattern.quote() method missing from Java 1.4 version
     *
     * This method produces a String that can be used to create a 
     * Pattern that would match the string s as if it were a literal pattern.
     * Metacharacters or escape sequences in the input sequence 
     * will be given no special meaning.
     * @param s - The String to be literalised
     * @return A literal string replacement
     */
    
    private String quote(String s) {
        StringBuffer sb =  new StringBuffer(s.length() * 2).append("\\Q");
        int previousEndQuotationIndex = 0;
        int endQuotationIndex = 0;
        while ((endQuotationIndex = s.indexOf("\\E", previousEndQuotationIndex)) >= 0) {
            sb.append(s.substring(previousEndQuotationIndex, endQuotationIndex));
            sb.append("\\E\\\\E\\Q");
            previousEndQuotationIndex = endQuotationIndex + 2;
        }
        sb.append(s.substring(previousEndQuotationIndex));
        sb.append("\\E");
        String literalPattern = sb.toString();
        return literalPattern;
    }

    public boolean printClassesWithFileSeparator() {
        return printClassesWithFileSeparator;
    }

    public void setPrintClassesWithFileSeparator(boolean printClassesWithFileSeparator) {
        this.printClassesWithFileSeparator = printClassesWithFileSeparator;
    }
}
