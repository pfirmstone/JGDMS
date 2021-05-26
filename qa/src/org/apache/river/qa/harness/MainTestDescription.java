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
package org.apache.river.qa.harness;

// java.util
import java.util.Properties;
import java.util.StringTokenizer;
import java.util.ArrayList;

// java.util.logging
import java.util.logging.Logger;
import java.util.logging.Level;

// java.io
import java.io.BufferedReader;
import java.io.File;
import java.io.Reader;
import java.io.Writer;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;

/**
 * A <code>TestDescription</code> which represents tests that are
 * implemented as source code which is compiled automatically and
 * executed by calling the main method of the compiled class. 
 * Tags
 * must be provided in the first comment block of the test source.
 * The supported syntax is quite rigid:
 * <ul>
 * <li>The tags must reside in the first comment block in the code
 * <li>The comment block must comprise the first non-blank text
 *     in the code
 * <li>The comment block must begin with the '\/\*' sequence and
 *     any tags on this initial line will be ignored
 * <li>The tag '@test' must be the first tag in the comment block
 * <li>Tags may be of the form:
 *   <table>
 *     <tr>
 *       <td>@key
 *       <td>key value is implicitly <code>true</code>
 *     <tr>
 *       <td>@key value1 value2 ...
 *       <td>values are separated by white space and may not 
 *           contain white space
 *     <tr>
 *       <td>@key=value
 *       <td>value may contain any character, including '='. White
 *           space in value is retained
 *   </table>
 * <li>The @library and @build tags may occur multiple times. 
 *     The tag values are
 *     appended in the order read
 * <li>If the <code>run</code> tag is encountered, the <code>run</code> 
 *     options are processed. There is no action implied by the run
 *     tag. The test class returned by <code>getTestClassName</code>
 *     is always run as the test, regardless of the existance or content
 *     of the <code>run</code> tag.
 * <li>The comment block must be terminated with the '\*\/' sequence
 * </ul>
 * The following tags are defined:
 * <table>
 * <tr>
 * <td>@test
 * <td>A mandatory tag which identifies this as a valid test description
 * <tr>
 * <td>@library
 * <td>identifies libraries of source files which may be accessed by the
 * test. The library directory must be specified relative to the location of the
 * test source file containing the @library tag. Multiple entries per line, and
 * multiple occurances of the @library tag, are allowed.
 * <tr>
 * <td>@build
 * <td>identifies the names of source files to compile (the .java extension is
 * omitted. All library files to be compiled must be named in @build tags. It is
 * legal, but unnecessary, to include the name of the test source file to
 * compile. However, if other source files in the test directory must also be
 * compiled, those names should also be specified in the build tag. Multiple
 * entries per line, and multiple occurances of the @build tag, are allowed.
 * <tr>
 * <td>@run main/policy=policyfile vmarg1 vmarg2 ... 
 * <td>This tag defines the execution environment of the test. The main token
 * defines this as a main-style test, and may be omitted. If /policy= is
 * defined, the named policy file is resolved relative to the test source
 * directory. Any remaining tokens which begin with a '-' character are included
 * in the VM options when the test VM is invoked.
 * </table>
 * Any other tags are interpreted as configuration value definitions.
 */
public class MainTestDescription extends TestDescription {

    /** the config object */
    private transient volatile QAConfig config;

    /** The scratch directory */
    private File scratchDir = null;

    /** the directory containing the test source */
    private File testSourceDir = null;

    /** the directory containing the test classes */
    private File testClassDir = null;

    /** the policy file specified by the /policy= modifier */
    //XXX this field must not be explicitly set. It is set by
    // a method called by the parent class constructor. If it
    // is set here, the value set by the parent class constructor
    // is overwritten. This is very bad. Rework this.
    private String policyTag;

    /** the logger */
    private static final Logger logger = 
	Logger.getLogger("org.apache.river.qa.harness");

    /**
     * Construct a test description for a test with the given <code>name</code>.
     * The name is assumed to represent the name of a source file relative
     * to the installation directory of the test harness.
     * The internal properties object is populated by parsing a set of
     * tags provided at the beginning of the test source file.
     *
     * @param name the name of the test
     * @param config the config object for this test
     *
     * @throws TestException if mandatory tags are missing or well-known
     *                       tags are malformed
     */
    public MainTestDescription(String name, Properties p, QAConfig config) 
	throws TestException
    {
	super(name, p, config);
	this.config = config;
    }
    
    void setConfig(QAConfig config){
        super.setConfig(config);
        this.config = config;
    }

    /**
     * Overridden method which causes the constructor to initialize the
     * internal properties object from tags in the source file.
     *
     * @throws TestException if mandatory tags are missing or well-known
     *                       tags are malformed
     */
    protected void initProperties() throws TestException {
	parseTags(getName());
    }	

    /**
     * Indicates whether some other object is equal to this one.
     * <code>obj</code> is considered equal to this object if it
     * is an instance of <code>TestDescription</code> and has a
     * name which is identical to this name.
     *
     * @param obj the object to test for equality
     * @return true if <code>obj</code> is equal to this object
     */
    public boolean equals(Object obj) {
	if (!(obj instanceof MainTestDescription)) {
	    return false;
	}
	return ((MainTestDescription) obj).getName().equals(getName());
    }

    /**
     * Return a hashcode value for the object.
     *
     * @return the hashcode
     */
    public int hashCode() {
	return getName().hashCode();
    }

    /**
     * Return a string representation of this object.
     *
     * @return a string representing this object
     */
    public String toString() {
	return "MainTestDescription[" + getName() + "]";
    }


    /**
     * Returns the classpath for compilation and test execution.
     * The classpath consists of the JAR file containing 
     * <code>MainWrapper</code> followed by the directory containing the
     * test classes, followed by the directory containing the
     * test sources.
     *
     * @return the classpath string
     */
    public String getClasspath() {
	if (scratchDir == null) {
	    throw new IllegalStateException("must call initDirectories first");
	}
	String classpath = config.getKitHomeDir() 
	                 + File.separator
	                 + "lib"
	                 + File.separator 
	                 + "qa1-mainwrapper.jar";
	classpath += File.pathSeparator;
	classpath += testClassDir.getAbsolutePath();
	classpath += File.pathSeparator;
	classpath += testSourceDir.getAbsolutePath();
	return classpath;
    }

    /**
     * Augments the arguments returned by the superclass call with
     * property definition strings for the properties:
     * <ul>
     * <li><code>test.src</code>
     * <li><code>test.classes</code>
     * </ul>
     *
     * @return the array of property definitions
     */
    public String[] getJVMArgs() {
	if (scratchDir == null) {
	    throw new IllegalStateException("must call initDirectories first");
	}
	ArrayList l = new ArrayList(10);
	String[] superStrings = super.getJVMArgs(); // typically null
	if (superStrings != null) {
	    for (int i = 0; i < superStrings.length; i++) {
		l.add(superStrings[i]);
	    }
	}
	l.add("-Dtest.src=" + testSourceDir.getAbsolutePath());
	l.add("-Dtest.classes=" + testClassDir.getAbsolutePath());
	if (getPolicyFile() != null) {
//	    l.add("-Djava.security.manager=org.apache.river.tool.SecurityPolicyWriter"); // Seems to be ok here for jsse
//	    l.add("-Djava.security.manager=default");
//	    l.add("-Dpolicy.provider=org.apache.river.tool.DebugDynamicPolicyProvider");
//	    l.add("-Dorg.apache.river.tool.DebugDynamicPolicyProvider.grantAll=true");
//	    l.add("-Dpolicy.provider=net.jini.security.policy.DynamicPolicyProvider");
	}
	l.add("-Dorg.apache.river.qa.home=" + config.getKitHomeDir());
	return (String[]) l.toArray(new String[l.size()]);
    }


    /**
     * Get the policy file for the test VM. If the policy is not
     * specified by the configuration, then <code>null</code> is
     * returned to indicate no policy is to be used
     *
     * @return the policy file to use, or <code>null</code>
     */
    public String getPolicyFile() {
        String policyFile = config.getStringConfigVal("testPolicyfile", null);
	if (policyFile != null) {
	    String installDir = config.getKitHomeDir();
	    policyFile = config.relativeToAbsolutePath(installDir, policyFile);
	}
	return policyFile;
    }

    /**
     * Returns a default category name if no categories have been
     * defined for the test. This is special behavior for main tests
     * to retain compatibility with jtreg tagged source files.
     *
     * @return a single category, <code>default</code>, if no categories
     *         are defined for the test
     */
      public String[] getCategories() {
	  String[] cats = super.getCategories();
	  if (cats.length == 0) {
	      cats = new String[]{"default"};
	  }
	  return cats;
      }

    /**
     * Get the command line for the test VM. Before the command
     * line is constructed, any required test directories are
     * created and source files are compiled.
     *
     * @throws TestException if a failure occurs setting up the
     *         test environment or compiling the sources.
     */
    public String[] getCommandLine() throws TestException {
	buildTest();
	return super.getCommandLine(null);
    }

    /**
     * Creates the working directories for the test, and sets the values
     * for:
     * <table>
     * <tr><td>scratchDir<td>a scratch directory for misc test files
     * <tr><td>testClassDir<td>the directory where the test classes are placed
     * <tr><td>testSourceDir<td>the directory where the test sources are located
     * </table>
     * In addition, if <code>policyTag</code> is non-null, then a new security
     * policy is generated in the scratch directory and the 
     * <code>testPolicyfile</code> configuration value is set to point to it.
     *
     * @throws TestException if the source directory is missing, if any
     *                       of the target directories cannot be created,
     *                       or if an I/OO error occurs creating the policy file
     */
    private void initDirectories() throws TestException {
	testSourceDir = 
	    new File(config.getKitHomeDir(), getName()).getParentFile();
	if (!testSourceDir.exists()) {
	    throw new TestException("source directory " 
				    + testSourceDir 
				    + " missing");
	}
	File baseDir = new File("maintests");
	if (!baseDir.exists()) {
	    if (!baseDir.mkdir()) {
		throw new TestException("Can't make maintests directory");
	    }
	}
	File classesRoot = new File(baseDir, "classes");
	if (!classesRoot.exists()) {
	    if (!classesRoot.mkdir()) {
		throw new TestException("Can't make classes directory");
	    }
	}
	testClassDir = new File(classesRoot, getName()).getParentFile();
	if (!testClassDir.exists()) {
	    if (!testClassDir.mkdirs()) {
		throw new TestException("Can't make classes directory "
					+ testClassDir);
	    }
	}
	scratchDir = new File(baseDir, "scratch");
	if (!scratchDir.exists()) {
	    if (!scratchDir.mkdir()) {
		throw new TestException("Can't make scratch directory");
	    }
	}
	if (policyTag != null) {
	    File origPolicy = new File(testSourceDir, policyTag);
	    File newPolicy = null;
	    FileReader reader = null;
	    FileWriter writer = null;
	    try {
		newPolicy = new File(scratchDir, policyTag + "_new");
		String sep = System.getProperty("line.separator");
		reader = new FileReader(origPolicy);
		writer = new FileWriter(newPolicy);
		writer.write("// grant added by harness" + sep);
		writer.write("grant codebase \"file:${org.apache.river.qa.home}${/}lib${/}qa1-mainwrapper.jar\" {" + sep);
		writer.write("         permission java.security.AllPermission;" + sep);
		writer.write("};" + sep);
		writer.write(sep);
		writer.write("grant {" + sep);
		writer.write("         permission java.io.FilePermission \"" 
			     + classesRoot.getAbsolutePath() 
			     + "${/}-\", \"read\";" + sep);
		writer.write("};" + sep);
		writer.write(sep);
		writer.write("// original policy file:" + sep);
		writer.write("// " + origPolicy + sep);
		int c;
		while ((c = reader.read()) >= 0) {
		    writer.write(c);
		}
	    } catch (IOException e) {
		e.printStackTrace();
		throw new TestException("Error creating policy file" 
				      + newPolicy, e);
	    } finally {
		try {
		    reader.close();
		    writer.close();
		} catch (Exception ignore) {
		}
	    }
 	    config.setDynamicParameter("testPolicyfile", 
				       newPolicy.getAbsolutePath());
	    logger.log(Level.FINEST, 
		       "policy found, set to " + newPolicy.getAbsolutePath());
	}
    }

    /**
     * Retrieve a value from the configuration by searching
     * for the given <code>key</code> and return the value
     * parsed into a <code>String</code> array. The tokens 
     * in the value may be separated by space, tab, or ','.
     * If the configuration value does not exist or contains
     * no tokens, an empty string is returned.
     *
     * @param  key the name of the configuration value
     *
     * @return a string array containing the set of tokens
     *         parsed from the configuration value, or an
     *         empty string
     */
    private String[] getConfigStrings(String key) {
	String[] stringArray = new String[0];
	String s = config.getStringConfigVal(key, null);
	if (s != null) {
	    stringArray = config.parseString(s, ", \t");
	}
	return stringArray;
    }

    /**
     * Returns the codebase for the test. The codebase may be tagged
     * by the parameter name <code>testCodebase</code>. 
     * If this value is not defined, <code>null</code>
     * is returned (inhibiting any codebase annotation). Tests run
     * by  <code>MainWrapper</code> will typically not set a
     * codebase annotation.
     *
     * @return the codebase for the test
     */
    public String getCodebase() {
        String codebase = config.getStringConfigVal("testCodebase", null);
	logger.log(Level.FINEST, "using codebase: " + codebase);
	return codebase;
    }

    /**
     * Return the wrapper class name for executing a test in another VM.
     * The value returned is that obtained by searching the configuration
     * for the key <code>testWrapper</code>. If this key is not found,
     * the default value <code>org.apache.river.qa.harness.MainWrapper</code> is
     * returned. One special value is recognized:
     * <table>
     * <tr>
     *   <td>mainwrapper
     *   <td>if the search for <code>testWrapper</code> returns this
     *       value (case insensitive), then this method will return 
     *       <code>org.apache.river.qa.harness.MainWrapper</code>
     * </table>
     * If the search for <code>testWrapper</code> returns any other
     * value, then that value is returned by this method. Note that
     * this test descriptor performs command-line setup specific
     * to <code>MainWrapper</code>; it is unlikely that specifying
     * a different wrapper would result in correct behavior.
     *
     * @return the class name of the test wrapper
     */
    public String getWrapperClassName() {
	return config.getStringConfigVal("testWrapper",
					 "org.apache.river.qa.harness.MainWrapper");
    }

    /**
     * Parse the configuration tags contained in a java source file.
     * The supported syntax is quite rigid:
     * <ul>
     * <li>The tags must reside in the first comment block in the code
     * <li>The comment block must comprise the first non-blank text
     *     in the code
     * <li>The comment block must begin with the '\/\*' sequence and
     *     any tags on this initial line will be ignored
     * <li>The tag '@test' must be the first tag in the comment block
     * <li>Tags may be of the form:
     *   <table>
     *     <tr>
     *       <td>@key
     *       <td>key value is implicitly <code>true</code>
     *     <tr>
     *       <td>@key value1 value2 ...
     *       <td>values are separated by white space and may not 
     *           contain white space
     *     <tr>
     *       <td>@key=value
     *       <td>value may contain any character, including '='. White
     *           space in value is retained
     *   </table>
     * <li>The @library and @build tags may occur multiple times. 
     *     The tag values are
     *     appended in the order read
     * <li>If the <code>run</code> tag is encountered, the <code>run</code> 
     *     options are processed. There is no action implied by the run
     *     tag. The test class returned by <code>getTestClassName</code>
     *     is always run as the test, regardless of the existance or content
     *     of the <code>run</code> tag.
     * <li>The comment block must be terminated with the '\*\/' sequence
     * </ul>
     * The internal properties object is updated to reflect tag values.
     * Also, values for <code>testClass</code> and <code>testWrapper</code>
     * are generated on the assumption that the test name is also the test
     * class to be executed and is to be executed using the 
     * <code>MainWrapper</code>
     *
     * @param source the name of the java source file, which may be 
     *               relative to the kit installation directory
     *
     * @throws TestException if the <code>@test</code> tag is missing or the
     *                       tag comment block is malformed
     */
    private void parseTags(String source) throws TestException {
	logger.log(Level.FINEST, "parseTags source: " + source);
        String installDir = config.getKitHomeDir();
	String absName = config.relativeToAbsolutePath(installDir, source);
	String baseName = source.substring(0, source.lastIndexOf(".java"));
	baseName = baseName.replace('\\', '/');
	baseName = baseName.substring(baseName.lastIndexOf('/') + 1);
	setProperty("testClass", baseName);
	setProperty("testWrapper", "org.apache.river.qa.harness.MainWrapper");
	try {
	    String line;
	    boolean gotTestTag = false;
	    BufferedReader r = new BufferedReader(new FileReader(absName));
	    while ((line = r.readLine()) != null) {
		StringTokenizer tok = new StringTokenizer(line);
		if (! tok.hasMoreTokens()) {
		    continue; // skip blanks lines
		}
		String token = tok.nextToken();
		if (! token.startsWith("/*")) {
		    throw new TestException("Tags comment block required");
		}
		if (line.indexOf("@test") >= 0) {
		    gotTestTag = true;
		}
		break; // leading comment delimiter found
	    }
	    if (line == null) {
		return; //no comments in file
	    }
	    if (!gotTestTag) {
		/* find @test in a comment */
		while ((line = r.readLine()) != null) {
		    if (line.indexOf("*/") >= 0) {
			throw new TestException("Missing @test tag");
		    }
		    if (line.indexOf("@test") >= 0) {
			break; // found @test
		    }
		}
	    }
	    if (line == null) {
		throw new TestException("EOF before end of tag block");
	    }
	    boolean doingRun = false;
	    while ((line = r.readLine()) != null) {
		if (line.indexOf('@') >= 0) {
		    String rest = line.substring(line.indexOf('@') + 1);
		    if (rest.startsWith(" ")) {
			throw new TestException("white space follows '@' in tag");
		    }
		    String key = null;
		    String value = ""; // last else relies on this init
		    int firstEq = rest.indexOf("=");
		    int firstSp = rest.indexOf(" ");
		    if (firstEq >= 0 && (firstSp < 0 || (firstEq < firstSp))) {
			key = rest.substring(0,rest.indexOf('='));
			value = rest.substring(rest.indexOf('=') +1);
		    } else {
			StringTokenizer tok = new StringTokenizer(rest);
			if (tok.countTokens() <= 0) {
			    continue; // ignore solo '@'
			} else if (tok.countTokens() == 1) {
			    key = tok.nextToken();
			    value = "true";
			} else {
			    key = tok.nextToken();
			    value = tok.nextToken();
			    while (tok.hasMoreTokens()) {
				value += " " + tok.nextToken();
			    }
			}
		    }
		    if (key.equals("run")) {
			doingRun = true;
			processRunOptions(value, source);
		    } else {
			doingRun = false;
		    }
		    if (key.equals("library") || key.equals("build")) {
			String oldValue = getProperty(key);
			if (oldValue != null) {
			    value = oldValue + " " + value;
			}
		    }
		    setProperty(key, value);
		} else if (doingRun) {
		    while (line.startsWith(" ")
			   || line.startsWith("\t")
			   || line.startsWith("* ")
			   || line.startsWith("*\t")) {
			line = line.substring(1);
		    }
		    if (line.startsWith("-")) {
			processRunOptions("main " + line, source); //XXX ugly
		    }
		} //XXX assumes main, assumes run is last
		if (line.indexOf("*/") >= 0) {
		    break; // end of comment block
		}
	    }
	    if (line == null) {
		throw new TestException("EOF before end of tag block");
	    }
	    // always add the implied build target
	    String buildList = getProperty("build");
	    if (buildList == null) {
		buildList = baseName;
	    } else {
		if (buildList.indexOf(baseName) < 0) {
		    buildList += " " + baseName;
		}
	    }
	    setProperty("build", buildList);
	    return;
	} catch (IOException e) {
	    throw new TestException("problem parsing tags", e);
	}
    }

    /**
     * Parse the options following the <code>@run</code> tag. The
     * only option recognized is the <code>/policy=</code> option,
     * which must refer to a security policy file in the test source
     * directory. In addition, any VM options specified in the tag
     * will be added to the set current set of VM options. A VM option
     * is any token with a leading '-'. A valid
     * tag might look like:
     * <pre>
     * @run main/policy=policy.file/othervm -Dfoo=bar -Da=b testname
     * <pre>
     * In this example, the security policy used for the test VM will
     * be <code>policy.file</code> located in the test source directory.
     * The tokens <code>main</code> and <code>othervm</code> are
     * ignored but legal. The property definitions <code>-Dfoo=bar</code>
     * and -Da=b will be applied to the test VM. The final token,
     * <code>testname</code> is ignored. 
     *
     * @throw TestException if the <code>main</code> action flag is missing
     */
    private void processRunOptions(String options, String testName) 
	throws TestException
    {
	StringTokenizer optionsTok = new StringTokenizer(options);
	if (optionsTok.hasMoreTokens()) {
	    String actions = optionsTok.nextToken();
	    StringTokenizer actionsTok = new StringTokenizer(actions, "/");
	    String actionString = actionsTok.nextToken();
	    if (!actionString.equals("main")) {
		throw new TestException("run tag requires main action");
	    }
	    while (actionsTok.hasMoreTokens()) {
		String actOption = actionsTok.nextToken();
		logger.log(Level.FINEST, "action option: " + actOption);
		if (actOption.startsWith("policy=")) {
		    policyTag = actOption.substring("policy=".length());
		}
	    }
	    String newOptions = null;
	    while (optionsTok.hasMoreTokens()) {
		String vmOption = optionsTok.nextToken();
		if (!vmOption.startsWith("-")) { // stop on first non-option
		    break;
		}
		if (newOptions == null) {
		    newOptions = vmOption;
		} else {
		    newOptions += " " + vmOption;
		}
	    }
	    if (newOptions != null) {
		String optionArgs = getProperty("testjvmargs");
		if (optionArgs == null) {
		    optionArgs = newOptions;
		} else {
		    optionArgs += " " + newOptions;
		}
		setProperty("testjvmargs", optionArgs);
	    }
	}
    }

    /**
     * Builds all of the classes identified by the build tag. Places
     * class files in the class directories relative to where the
     * corresponding source files are found in the source file tree.
     *
     * @throws TestException if expected files/directories do not exist
     */
    private void buildTest() throws TestException {
	initDirectories();
	String cp = getClasspath();
	String sp = getSourcepath();
	String[] buildList = getConfigStrings("build");
	for (int i = 0; i < buildList.length; i++) {
  	    File sourceFile = getSourceFile(buildList[i]);
 	    if (compileNeeded(buildList[i], sourceFile)) {
		File sourceDir = getCompilationDirectory(buildList[i]);
		String sourceName = buildList[i].replace('.', '/');
		logger.log(Level.FINEST, "compilation working directory is " 
			   + sourceDir);
		String cmdLine = "javac"
		    + " -classpath " + cp
		    + " -sourcepath " + sp
		    + " -d " + testClassDir.getAbsolutePath() 
		    + " " + sourceName + ".java";
		logger.log(Level.FINEST, "compile cmdline: " + cmdLine);
		try {
		    Process p = Runtime.getRuntime().exec(cmdLine,
							  null,
							  sourceDir);
		    logger.log(Level.FINEST, "compile started");
		    p.waitFor();
		    logger.log(Level.FINEST, "compile finished");
		} catch (InterruptedException ignore) {
                    Thread.currentThread().interrupt();
		} catch (IOException e) {
		    throw new TestException("Exception running compiler", e);
		}
		File testClassFile = new File(testClassDir, 
					      sourceName + ".class");
		if (!testClassFile.exists()) {
		    throw new TestException("Failed to generate "
					    + " class file from"
					    + buildList[i]);
		}
	    }
	}
    }

    /**
     * Returns the directory path containing the source file
     * identified by <code>targetName</code>. The returned name is
     * expressed as a path relative to the test source
     * directory. The possible set of return values includes
     * the components of the <code>@library</code> tag, or ".", 
     * signifying the test source directory.
     *
     * @param targetName the name token identifying the compilation
     *                   target (no <code>.java</code> or <code>.class</code>
     *                   extension is included).
     * @return the source directory path for the target
     * @throws TestException if expected files/directories do not exist
     */
    private File getSourceFile(String targetName) throws TestException {
	if (scratchDir == null) {
	    throw new IllegalStateException("must call initDirectories first");
	}
	targetName = targetName.replace('.','/');
	File target = new File(testSourceDir, targetName + ".java");
	if (target.exists()) {
	    return target;
	}
	String[] libStrings = getConfigStrings("library");
	for (int i = 0; i < libStrings.length; i++) {
	    File dir = new File(testSourceDir, libStrings[i]);
	    if (!dir.exists()) {
		throw new TestException("library directory " 
					+ dir 
					+ " does not exist");
	    }
	    target = new File(dir, targetName + ".java");
	    if (target.exists()) {
		return target;
	    }
	}
	throw new TestException("could not find source file " 
				+ targetName + ".java");
    }

    /**
     * Get the compilation directory for a source file. Converts any 
     * package identifiers to path separators.
     *
     * @param targetName the compilation target specified in package
     *                   notation
     * @return the directory in which the target source file is expected
     *         to reside.
     *
     * @throws TestException if the source file could not be found
     */
    private File getCompilationDirectory(String targetName) 
	throws TestException 
    {
	if (scratchDir == null) {
	    throw new IllegalStateException("must call initDirectories first");
	}
	targetName = targetName.replace('.','/');
	File target = new File(testSourceDir, targetName + ".java");
	if (target.exists()) {
	    return testSourceDir;
	}
	String[] libStrings = getConfigStrings("library");
	for (int i = 0; i < libStrings.length; i++) {
	    File dir = new File(testSourceDir, libStrings[i]);
	    if (!dir.exists()) {
		throw new TestException("library directory " 
					+ dir 
					+ " does not exist");
	    }
	    target = new File(dir, targetName + ".java");
	    if (target.exists()) {
		return dir;
	    }
	}
	throw new TestException("could not find source file " 
				+ targetName + ".java");
    }	

    /**
     * Determines whether a source file must be compiled. Compares the
     * timestamp of a source file with it's correspondingly named class
     * file, and returns <code>true</code> if the source is newer. All
     * class files are assumed to be located in the directory referenced
     * by the <code>testClassDir</code> attribute.
     * 
     * @param targetName the name of the compilation target expressed
     *                   as a simple name token (that is <code>Foo</code>
     *                   rather than <code>Foo.java</code> or
     *                   <code>Foo.class</code>
     * @param sourceFile the source file, which is assumed to exist
     *
     * @return <code>true</code> if the target class file does not exist or
     *         is older than the source file
     *
     * @throws TestException if the directory names cannot be determined
     *                       from the system properties, or if the source 
     *                       file cannot be found
     */
    private boolean compileNeeded(String targetName, File sourceFile)
 	throws TestException
    {
	if (scratchDir == null) {
	    throw new IllegalStateException("must call initDirectories first");
	}
	targetName = targetName.replace('.', '/');
	File target = new File(testClassDir, targetName + ".class");
	if (!target.exists()) {
	    logger.log(Level.FINEST, "Target class " + target + " not found");
	    return true;
	}
	boolean needsUpdate = target.lastModified() < sourceFile.lastModified();
	if (needsUpdate) {
	    logger.log(Level.FINEST, "Target class " + target + " out of date");
	} else {
	    logger.log(Level.FINEST, "Target class " + target + " is current");
	}
	return needsUpdate;
    }

    /**
     * Returns the sourcepath for compilation.
     * The sourcepath consists of the the directory containing the
     * test sources,  followed by 
     * components of the library tag resolved
     * relative to the test source directory. 
     *
     * @return the sourcepath string
     */
    private String getSourcepath() {
	String srcpath = testSourceDir.toString();
	String[] libString = getConfigStrings("library");
	for (int i = 0; i < libString.length; i++) {
	    srcpath += File.pathSeparator;
	    File f = new File(testSourceDir, libString[i]);
	    srcpath += f.getAbsolutePath();
	}
	return srcpath;
    }

    /**
     * Get the test arguments. This method returns a test argument
     * list formated for <code>MainWrapper</code>, which requires
     * the name of the test, the name of the test class, and the
     * set of test arguments retrieved from the configuration bound
     * to the key <code>testArgs</code>.
     *
     * @return the complete VM argument list, which is never null
     */
    public String[] getTestArgs() {
        String argStrings = config.getStringConfigVal("testArgs", "");
        String[] testArgs = config.parseString(argStrings);
	if (testArgs == null) {
	    testArgs = new String[0];
	}
	String[] args = new String[testArgs.length + 2];
	args[0] = getName();
	args[1] = getTestClassName();
	for (int i = 0; i < testArgs.length; i++) {
	    args[i + 2] = testArgs[i];
	}
	return args;
    }

    /**
     * Return the working directory for test execution. For MainWrapper
     * tests, the working directory is the scratch directory. If the
     * scratch directory has not been created, an 
     * <code>IllegalStateException</code> is thrown.
     *
     * @return the scratch directory
     */
    public File getWorkingDir() {
	if (scratchDir == null) {
	    throw new IllegalStateException("must call initDirectories first");
	}
	return scratchDir;
    }
}
