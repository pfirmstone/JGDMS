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
package com.sun.jini.qa.harness;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.io.FileOutputStream;
import java.io.FileNotFoundException;
import java.net.ServerSocket;
import java.net.URL;
import java.net.URLConnection;
import java.net.MalformedURLException;
import java.util.ArrayList;
import java.util.Date;
import java.text.MessageFormat;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.MissingResourceException;
import java.util.Properties;
import java.util.ResourceBundle;
import java.util.StringTokenizer;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.jar.JarFile;
import java.util.zip.ZipEntry;
import java.lang.reflect.Field;
import java.net.BindException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketAddress;

//Should there be an 'AbortTestRequest' ?

/**
 * An implementation of the master component of a distributed test harness.
 * Provides the command line interface for starting a test run, execs and
 * manages the VM which contains the master test, and coordinates the
 * activities of all participating slave harness.
 * <p>
 * Command line options accessed by this class:
 * <ul>
 * <li><code>-tests testlist</code> where <code>testlist</code> is a 
 *     comma-separated list of test descriptor names to run. If omitted, all 
 *     tests matching other filters (such as categories) are run.
 *
 * <li> <code>-xtests testlist</code> where <code>testlist</code> is the
 *      comma separated list of tests to exclude from testing
 *
 * <li> <code>-xcategories catlist</code> where <code>catlist</code> is
 *      the comma set of categories to exclude from testing. Any test
 *      which is a member of any category in this list is excluded from
 *      testing.
 * <li> <code>-env envfile</code> where <code>envfile</code> is the name
 *      of a properties file containing environment definitions for
 *      running tests (i.e. which jdk/jsk versions to use when running tests).
 *      If the file is specified as a relative path, it is resolved relative
 *      to the current working directory.
 * <li> <code>-categories categorylist</code> is the comma-separated
 *      set of categories to test
 * <li> <code>-help</code> prints a usage message
 * </ul>
 * <p>
 * Property values accessed by this class:
 * <ul>
 * <li><code>com.sun.jini.qa.harness.verifier</code> is the name of the
 *     configuration verifier class to load. Before a test is run,
 *     this class is loaded (if defined) and its <code>canRun</code>
 *     method called to determine whether to run the test.
 * <li><code>com.sun.jini.qa.harness.runCommandAfterEachTest</code> is a
 *     platform dependent command line to be executed at the conclusion of
 *     each test.
 * </ul>
 * <p>
 * Note that there is no actual distinction between command-line options
 * and property values. That is, a property file could contain the entry
 * 'xtests=foo,bar' to exclude tests foo and bar. Likewise, the command line
 * could include the option "-com.sun.jini.qa.harness.runCommandAfterEachTest ls"
 * which would cause the Unix 'ls' command to be run after each test.
 * <p>
 * Tests are always run in a separate VM spawned by this class. The
 * <code>QAConfig</code> object is serialized and written to the
 * test VM's input stream.
 * <p>
 * Test status information must be passed back from the test VM 
 * to the harness VM. To do this, the wrapper writes a specially formatted
 * string as the last line of output to <code>System.err</code>.
 * <code>MasterHarness</code> tracks the last line written to this stream,
 * and uses the information in it to update the test results summary tables.
 * Because the <code>message</code> portion of the status could be
 * a multi-line string, the wrapper converts any '\n' or '\r' characters
 * in the message to the tokens '<<n>>' or '<<r>>' respectively. 
 * <code>MasterHarness</code> restores these line terminator characters
 * as part of reconstructing the test results.
 *</table> <p>
 */
class MasterHarness {

    /**
     * token which identifies the beginning of a test result message being
     * sent from a test running in another VM
     */
    private static final String STATUS_TOKEN = "!STATUS!";

    /** the keep-alive port number */
    public final static int KEEPALIVE_PORT=10004;

    /** the output stream for this VM's output */
    private PrintStream outStream = System.out;

    /** the pipe for collecting stderr output from the test vm */
    private Pipe errPipe; 

    /** the pipe for collecting stdout output from the test vm */
    private Pipe outPipe;

    /** the max recursion depth when parsing test sets */
    private final static int MAXTESTCOUNT = 100;

    /** the command line arguments */
    private String[] args;

    /** the list of test categories to run. If null, all categories are run. */
    private List categories;

    /** depth counter to detect infinite recursion in <code>addTest</code> */
    private int testTries = 0;

    /** 
     * the object which forwards input to a test subprocess. This object
     * is reused for all test subprocesses.
     */
    private InputForwarder forwarder;

    /** the set of tests to run */
    private TestList testList;

    /** the list of categories to exclude from testing */
    private List xCategories;

    /** the list of tests to exclude from testing */
    private List xTestNames;

    /** the configuration object for this test run */
    private QAConfig config = null;

    /**
     * boolean controlling whether to read from stdin
     */
    boolean doInputBind;

    /** a reference to the keep-alive thread */
    private Thread keepaliveThread;

    /** the category map, cached if non-null */
    private HashMap categoryMap = null;

    /** the test name to test description properties map */
    private HashMap testMap = null;

    /**
     * Construct the <code>MasterHarness</code>. Creates an instance
     * of <code>QAConfig</code>, starts the keep-alive thread, connects
     * to any participating slave harnesses, and builds the test list.
     *
     * @param args      <code>String</code> array containing the command line
     *                  arguments
     */
    MasterHarness(String[] args) throws TestException {
        this.args = args;
	if (args.length == 0 || args[0].equals("-h") || args[0].equals("-help"))
	{
	    usage();
	    System.exit(1);
	}
	File f = new File(args[0]);
	// args[0] must be absolute, or it will be resolved against 
	// the kit installation directory (which is undefined)
	if (! f.isAbsolute()) {
	    f = f.getAbsoluteFile();
	    args[0] = f.toString();
	}
	if (! f.exists()) {
	    usage();
	    System.exit(1);
	}
	config = new QAConfig(args);
	keepaliveThread = new Thread(new KeepAlivePort(), "Keepalive");
	keepaliveThread.setDaemon(true);
	keepaliveThread.start();
	try {
	    Thread.sleep(1000);
	} catch (InterruptedException ignore) {
	}
	SlaveHarness.connect();
	// check for installed provider. Failure here should kill slaves.
	try {
	    Class  policyClass = 
		Class.forName("com.sun.jini.qa.harness.MergedPolicyProvider");
	    if (policyClass.getClassLoader().getParent() != null) {
		outStream.println("MergedPolicyprovider must be "
				+ "installed in an extensions directory");
		System.exit(1);
	    }
	} catch (Exception e) {
	    outStream.println("failed to find MergedPolicyProvider");
	    System.exit(1);
	}
	outStream.println("");
        outStream.println("-----------------------------------------");
	outStream.println("CONFIGURATION FILE:");
	outStream.println("");
	outStream.println("   " + args[0]);
	testList = new TestList(config, System.currentTimeMillis());
	loadTestDescriptions();
	buildTestList();
        displayConfigInfo(); // seems like this should be in QAConfig
    }

    /** 
     * A <code>Runnable</code> which opens the keep-alive server socket
     * and accepts connections for the life of this VM.
     */
    private class KeepAlivePort implements Runnable {

	public void run() {
	    ArrayList<Socket> socketList = new ArrayList<Socket>(); // keep references
            SocketAddress add = new InetSocketAddress(KEEPALIVE_PORT);
	    try {
                
		ServerSocket socket = new ServerSocket();
                socket.bind(add);
		while (true) {
		    socketList.add(socket.accept());
		}
	    } catch (BindException e){
                try {
                        Thread.sleep(240000); // Wait 4 minutes for TCP 2MSL TIME_WAIT
                        ServerSocket socket = new ServerSocket();
                        socket.bind(add);
                        while (true) {
                            socketList.add(socket.accept());
                        }
                } catch (InterruptedException ex){
                    outStream.println("Interruped while opening ServerSocket with KEEPALIVE_PORT:" + KEEPALIVE_PORT );
                    outStream.println("Unexpected exception after waiting 4 minutes for port to become available:\n");
                    ex.printStackTrace(outStream);
                    outStream.println("Initial attempt failed:\n");
                    e.printStackTrace(outStream);
                    System.exit(1);
                }catch (Exception ex){
                    outStream.println("Error occurred while attempting to open ServerSocket with KEEPALIVE_PORT:" + KEEPALIVE_PORT );
                    outStream.println("Unexpected exception after waiting 4 minutes for port to become available:\n");
                    ex.printStackTrace(outStream);
                    outStream.println("Initial attempt failed:\n");
                    e.printStackTrace(outStream);
                    System.exit(1);
                }
            }catch (Exception e) {
		outStream.println("Problem with KEEPALIVE_PORT:" + KEEPALIVE_PORT );
		outStream.println("Unexpected exception:");
		e.printStackTrace(outStream);
		System.exit(1);
	    }
	}
    }

    /**
     * Delete files which have been registered for deletion. Registered
     * files are deleted on the master host, and a request to delete
     * registered files is sent to all slave hosts.
     *
     * @throws TestException if an error occurs when communicating
     *                       with a slave
     */
    private void deleteRegisteredFiles() throws TestException {
	config.deleteRegisteredFiles();
	SlaveHarness.broadcastRequest(new DeleteRegisteredFilesRequest());
    }

    /**
     * Build the list of tests. 
     */
    private void buildTestList() throws TestException {
	String testString = config.getStringConfigVal("tests", null);
	categories = getCategories();
	xTestNames = buildList(config.getStringConfigVal("xtests", null));
	addFromURL(xTestNames, config.getStringConfigVal("excludeList", null)); 
	xCategories = buildList(config.getStringConfigVal("xcategories", null));
	outStream.println("");
        outStream.println("-----------------------------------------");
	outStream.println("SETTING UP THE TEST LIST:");
	outStream.println("");
	// If there are no arguments assume the user wants all the tests.
	if (testString == null) {
	    addByCategory();
	} else {
	    addTests(testString);
	}
    }

    /**
     * Attempt to add the test specified by <code>testName</code> to the current
     * set of tests. A test is obtained by calling the configurations
     * <code>getTestDescription</code> factory method. The test is added if:
     * <ul>
     * <li>the named test can be found and it's <code>TestDescription</code>
     *     is valid
     * <li>the test is not in the excluded test list
     * <li>none of the test categories are in the excluded category list
     * <li>the test has a category which is included in the requested categories
     *     (if any)
     * <li>the test passes test suite specific validity checks
     * <li>the test is not a duplicate
     * </ul>
     * @param testName the name of the test to add
     */
    private void addTest(String testName) throws TestException {
	String reason = null;
        TestDescription td = null;
	Properties p = (Properties) testMap.get(testName);
	// for td's not in the jar, or if -categories was not included
	if (!testName.endsWith(".td")) {
	    testName += ".td";
	}
	if (p == null) {
	    p = config.loadProperties(testName);
	}
	if (p == null) {
	    throw new TestException("no properties for " + testName);
	}
	try {
	    td = config.getTestDescription(testName, p);
	} catch (TestException e) {
	    e.printStackTrace();
	    reason = e.getMessage();
	}

	/* perform built-in checks in the test description */
	if (reason == null) {
	    reason = td.checkValidity();
	}

	/* if ok, perform command line checks */
	if (reason == null) {
	    reason = checkValidity(td);
	}

	/* complain and bail if anything went wrong */
	if (reason != null) {
	    outStream.println("   Skipping test: " + testName);
	    outStream.println("      Reason: " + reason);
	    return;
	}
        outStream.println("   Adding test: " + testName);
	String[] configTags = config.getConfigTags();
	for (int i = 0; i < configTags.length; i++) {
	    testList.add(new TestRun(td, configTags[i]));
	}
    }

    /**
     * Run all of the tests in the test list and displays their results.
     *
     * @return true if all started tests complete and pass; false if either
     *         one or more of the started tests fail or does not complete
     */
    boolean runTests() throws TestException {
	deleteRegisteredFiles();
	doInputBind = config.getBooleanConfigVal(
	    "com.sun.jini.qa.harness.bindInput", true);
	boolean genHtml = config.getBooleanConfigVal(
	    "com.sun.jini.qa.harness.generateHtml", false);
        outStream.println("-----------------------------------------");
        outStream.println("STARTING TO RUN THE TESTS");
        outStream.println("");
        outStream.println("");

	// main test loop
	while (testList.hasMore()) {
	    long testRunStartTime = System.currentTimeMillis();
	    TestResult testResult = null;
	    File logFile = null;
	    TestRun testRun = testList.next();
	    TestDescription td = testRun.td;
	    String configTag = testRun.configTag;
	    config.doConfigurationSetup(configTag, td);
	    String verifierNames = config.getStringConfigVal(
		"com.sun.jini.qa.harness.verifier", null);
	    if (!checkVerifiers(td, verifierNames)) {
		testResult = new TestResult(
		    testRun, false, Test.SKIP, 
		    "verifiers are: " + verifierNames);
		testList.add(testRun, testResult);
		outStream.println(testResult.toString());
		outStream.println("");
                outStream.println("-----------------------------------------");
		outStream.println("");
		continue;
	    }
	    if (genHtml) {
		logFile = getLogFile(td);
		setOutputStream(logFile);
	    }
	    String running = ((testRun.isRerun) ? "RE-RUNNING " : "Running ");
	    outStream.println(running + td.getName());
	    outStream.println("Time is " + new Date());
	    TestRunner runner = new TestRunner(testRun);
	    int interval = getTimeout(); // ask the subclass
	    if (interval > 0) {
		Thread testThread = new Thread(runner, "TestRunner");
		Timeout.TimeoutHandler handler =
		    new Timeout.ThreadTimeoutHandler(testThread);
		Timeout timeout = new Timeout(handler, interval);
		testThread.start();
		timeout.start();
		try {
		    testThread.join();
		} catch (InterruptedException e) { //shouldn't happen
		    outStream.println("testThread.join() interrupted..." +
		    "should not happen");
		}
		if(testThread.interrupted()) {
		    outStream.println("Test was interrupted");
		}
		if (timeout.timedOut()) {
		    outStream.println("Timed out");
		    SlaveTest.broadcast(new TeardownRequest());
		} else {
		    timeout.cancel();
		}
	    } else {
		runner.run();
	    }
	    deleteRegisteredFiles(); // do here in case test vm died
	    SlaveTest.waitForSlaveDeath(60); // give them a minute
	    testResult = runner.getTestResult();
	    testResult.setLogFile(logFile);
	    testResult.setElapsedTime(
		System.currentTimeMillis() - testRunStartTime);
	    testList.add(testRun, testResult);
	    outStream.println(testResult.toString());
	    runCommandAfterEachTest();
	    if (genHtml) {
		outStream.close(); // close log file
		setOutputStream(System.out); // return to System.out
	    } else {
		outStream.println("");
                outStream.println("-----------------------------------------");
		outStream.println("");
	    }
	} // end main while loop
        outStream.println("SUMMARY =================================");
        outStream.println("");
	TestList.TestResultIterator iter = testList.createTestResultIterator();
        boolean removePassResults = config.getBooleanConfigVal(
            "com.sun.jini.qa.harness.generateHtml.removePassResults",false);
        while (iter.hasMore()) {
            TestResult[] results = iter.next();
	    for (int i = 0; i < results.length; i++) {
		if (i == 0) {
	    	    outStream.println(results[i].toString());
		} else {
	            outStream.println("RE-RUN " + i);
	            outStream.println(results[i].toString());
		}
		// remove passed test result is required
                if (removePassResults && results[i].state) {
		    if (results[i].logFile != null &&
                        ! results[i].logFile.delete()) 
                    {
                        outStream.println("Error: could not remove " 
			    + results[i].logFile);
       		    }
		    // set logFile to null so that further report generation
		    // won't try to create links to the log files
		    results[i].logFile = null;
       		}
	    }
            outStream.println("-----------------------------------------");
        }
        outStream.println("");
        outStream.println("# of tests started   = " + 
	    testList.getNumStarted());
        outStream.println("# of tests completed = " 
	    + testList.getNumCompleted());
	if (testList.getNumSkipped() > 0) {
	    outStream.println("# of tests skipped   = " + 
		testList.getNumSkipped());
	}
        outStream.println("# of tests passed    = " + testList.getNumPassed());
        outStream.println("# of tests failed    = " + testList.getNumFailed());
	if (testList.getNumRerun() > 0) {
            outStream.println("# of tests rerun     = " + 
		testList.getNumRerun());
	}
        outStream.println("");
        outStream.println("-----------------------------------------");
        outStream.println("");
	testList.setFinishTime(System.currentTimeMillis());
        outStream.println("   Date finished:");
        outStream.println("      " 
	    + (new Date(testList.getFinishTime())).toString());
        outStream.println("   Time elapsed:");
        outStream.println("      " +
            ((testList.getDurationTime())/1000) + " seconds");
        outStream.println("");

	if (genHtml) {
	    try {
	        HtmlReport htmlReport = new HtmlReport(config, testList);
	        htmlReport.generate();
	    } catch (IOException ioe) {
		outStream.println("Exception trying to generate html:");
		ioe.printStackTrace(outStream);
	    }
	    setOutputStream(System.out); // return to System.out
	}

	boolean genXml = config.getBooleanConfigVal(
	    "com.sun.jini.qa.harness.generateXml", false);
	if (genXml) {
	    try {
	        XmlReport xmlReport = new XmlReport(config, testList);
	        xmlReport.generate();
	    } catch (Exception e) {
		outStream.println("Exception trying to generate xml:");
		e.printStackTrace(outStream);
	    }
	}
        return (testList.getNumFailed() == 0 && 
		testList.getNumStarted() == testList.getNumCompleted());
    } //end runTests

    /**
     * Set the output stream used by the harness VM to
     * the given stream.
     */
    private void setOutputStream(PrintStream out) {
	if (outStream != null) {
	    outStream.flush();
	}
	outStream = out;
    }

    /**
     * Set the output stream used by the harness VM to
     * a FileOutputStream writing to the given file.
     * If the FileOutputStream cannot be created,
     * the VM will exit.
     */
    private void setOutputStream(File logFile) {
	try {
	    setOutputStream(new PrintStream(new FileOutputStream(logFile)));
	} catch (FileNotFoundException fnfe) {
	    outStream.println("Cannot create " + logFile + ".  Exiting...");
	    System.exit(2);
	}
    }

    /**
     * Establish a file object given a test description.  The
     * file name is the test name with '/' replaced with '_'.
     * The file path is the same file path as that given in the
     * <code>com.sun.jini.qa.harness.generateHtml.resultLog</code> property.
     */
    private File getLogFile(TestDescription td) {
	String resultLog = config.getStringConfigVal(
	    "com.sun.jini.qa.harness.generateHtml.resultLog","index.html");
	File resultDir = (new File(resultLog)).getParentFile();
	if (resultDir != null && !resultDir.exists()) {
            resultDir.mkdirs();
	}
	String testName = td.getName().replace('/','_').replace('\\','_');
	File logFile = new File(resultDir, testName + ".txt");
	int counter = 1;
	while (logFile.exists()) {
	    logFile = new File(resultDir, testName + "-" + counter + ".txt");
	    counter++;
	}
	return logFile;
    }

    /**
     * A <code>Runnable</code> used to execute a test. 
     */
    private class TestRunner implements Runnable {

	private TestRun testRun;
	private TestResult testResult;

	/** 
	 * Construct the <code>TestRunner</code>.
	 *
	 * @param testRun the test to run
	 */
	public TestRunner(TestRun testRun) {
	    this.testRun = testRun;
	}

	/**
	 * Run the test in another VM.
	 * The <code>TestResult</code> is saved for later retrieval.
	 */
	public void run() {
	    testResult = runTestOtherVM(testRun);
	}

	/**
	 * Return the <code>TestResult</code> returned when the test
	 * was executed by this <code>TestRunner</code>.
	 *
	 * @return the test result
	 */
	public TestResult getTestResult() {
	    return testResult;
	}
    }

    /**
     * Returns the categories that were input for the current test run either
     * on the command line, or in the configuration file.
     *
     * @return <code>String</code> array containing the categories that were
     *         input for the current test run either on the command line, or
     *         in the configuration file.
     */
    private String[] getRequestedCategories() {
	if (categories == null) {
	    return null;
	}
        return ((String[])categories.toArray(new String[categories.size()]));
    }

    /**
     * Convert a string of comma-separated tokens to lower case and return
     * the tokens as a <code>List</code>.
     *
     * @param flatString the string of comma-separated tokens
     *
     * @return a <code>List</code> of tokens. If <code>flatString</code> is
     *         <code>null</code> or contains no tokens, a zero length
     *         <code>List</code> is returned.
     */
    private List buildList(String flatString) {
	ArrayList list = new ArrayList();
        if (flatString != null) {
	    StringTokenizer st = new StringTokenizer(flatString, ",");
	    while (st.hasMoreTokens()) {
		list.add(st.nextToken().toLowerCase());
	    }
	}
	return list;
    }

    private void addFromURL(List list, String urlString) throws TestException {
	if (urlString == null) {
	    return;
	}
	URL url = null;
	try {
	    url = new URL(urlString);
	} catch (MalformedURLException e1) {
	    try {
		url = new URL("file:" + urlString);
	    } catch (MalformedURLException e2) {
		throw new TestException("neither " + urlString 
					+ " nor file:" + urlString 
					+ " are value URLs");
	    }
	}
	try {
	    URLConnection conn = url.openConnection();
	    BufferedReader reader =
		new BufferedReader(new InputStreamReader(conn.getInputStream()));
	    String line;
	    while ((line = reader.readLine()) != null) {
		if (line.trim().length() == 0) {
		    continue;
		}
		list.add(line.trim().toLowerCase());
	    }
	} catch (IOException e) {
	    throw new TestException("problem reading exclusion list at " + url, e);
	}
    }

    /**
     * Add all of the tests identified in <code>testSet</code> to the
     * set of tests to run. The list is a comma-separated set of
     * test names or test set names. A test set is preceded by the
     * prefix "set:". The config is searched for a match to the test
     * set name, and if a match is found, the value of the match is
     * included in the list of tests. This evaluation is performed recursively,
     * so that sets may refer to other sets. Tests are not added to the
     * run set unless they meet the criteria defined in <@link addTest>.
     *
     * @param testSet the comma-separated set of tests to run
     */
    private void addTests(String testSet) throws TestException {
        // to make sure we are not in some kind of property loop
        if (testTries > MAXTESTCOUNT) {
            return;
        }
        testTries++;
	StringTokenizer st = new StringTokenizer(testSet, ",");
	while (st.hasMoreTokens()) {
	    String nextTest = st.nextToken();

	    /* if it starts with "set:" it's a subset */
	    if (nextTest.substring(0,4).equalsIgnoreCase("set:")) {
		String subset = nextTest.substring(4);
		String nextList = config.getStringConfigVal(subset, null);
		if (nextList != null) {
		    addTests(nextList);
		}
	    } else {
		addTest(nextTest);
            }
        }
	testTries--;
    }

    /**
     * Perform first-level validity checks on a test. This method returns
     * a 'reason' string for the failures it detects. In order for a
     * test to be considered valid, it:
     * <ul>
     * <li>must not have been excluded by name
     * <li>must not have been excluded by category
     * <li>must have a category which matches the requested categories 
     *     to run (if any)
     * <li>must not be a duplicate
     * </ul>
     * If a specialization of this class overrides this method, a 
     * call to <code>super.checkValidity(td)</code> should be 
     * performed.
     * 
     * @param td the test description of the test to validate
     *
     * @return <code>null</code> if validation passes, or a 'reason'
     *         string describing the failure
     */
    private String checkValidity(TestDescription td) {

	/* Determine if the test has been excluded by name */
	String testName = td.getName();
	if (xTestNames.contains(testName.toLowerCase())) {
            return "excluded by name";
	}

	/* Determine if the test belongs to any excluded category */
	String[] cats = td.getCategories();
	for (int i = 0; i < cats.length; i++) {
	    if (xCategories.contains(cats[i].toLowerCase())) {
		return "excluded by category";
	    }
	}

	/* Determine if test's categories are valid */
        if (categories != null) { // if null, categories are ignored
	    boolean good = false;
	    for (int i = 0; i < cats.length; i++) {
		if (categories.contains(cats[i])) {
		    good = true;
		    break;
		}
	    }
	    if (!good) {
		StringBuffer buf = new StringBuffer();
		buf.append("category mismatch");
		buf.append("      Categories selected for this run: ");
		for (int j = 0; j < categories.size(); j++) {
		    buf.append(categories.get(j) + " ");
		}
		buf.append("\n");
		buf.append("      Categories this test applies to: ");
		String[] testCategories = td.getCategories();
		for (int j = 0; j < testCategories.length; j++) {
		    buf.append(testCategories[j] + " ");
		}
		buf.append("\n");
		return buf.toString();
	    }
	}

	/* check for undesired duplicates */
	if (testList.contains(td)) {
	    return "duplicate test";
	}
	return null;
    }

    /**
     * Method called after each test to run a command in a separate process.
     * As an example, this method is useful to execute a diagnostic
     * command script to determine system resources after each test.
     * <p>
     * The command to run is specified by the property
     * <code>com.sun.jini.qa.harness.runCommandAfterEachTest.</code>  If this
     * property is null or not specified, then no command is run.
     * <p>
     * The method returns once the command finishes.  Any exception thrown
     * by trying to run the command is ignored.
     */
    private void runCommandAfterEachTest() {
        String command = config.getStringConfigVal(
	    "com.sun.jini.qa.harness.runCommandAfterEachTest",
          null);
        try {
            if (command != null) {
                Process process = Runtime.getRuntime().exec(command);
                process.waitFor();
            }
        } catch (Exception ignore) {}
    }

    /**
     * Run a test in its own VM. All slaves are sent a request to start
     * their slave tests. The command line to execute locally is obtained
     * from the <code>TestDescription</code> and passed to
     * <code>Runtime.exec</code>. The <code>QAConfig</code> instance
     * is written to the processes <code>System.in</code> stream. Pipes
     * are created to pass any output from the process to the output stream.
     * When the process exits, the test status info is extracted from
     * the last line of the process <code>System.err</code> stream
     * and returned.
     *
     * @param testRun the test to run
     *
     * @return the <code>TestResult</code> returned by the tests 
     * <code>run</code> method
     */
    private TestResult runTestOtherVM(TestRun testRun) {
	boolean discardOKOutput = 
	   config.getBooleanConfigVal("com.sun.jini.qa.harness.discardOKOutput",
				      false);
	TestResult testResult = null;
	Process proc = null;
	Throwable unexpectedException = null;
	PrintStream printStream = outStream;
	ByteArrayOutputStream stream = null;
        try {
	    // slaves should be ready to accept requests on return
	    SlaveHarness.broadcastRequest(new SlaveTestRequest(config));
	    String[] cmdArray = 
//		testRun.td.getCommandLine(config.getSystemProps("master"));
		testRun.td.getCommandLine(null);
	    StringBuffer sb = new StringBuffer();
	    for (int i = 0; i < cmdArray.length; i++) {
		if (cmdArray[i].indexOf(' ') >= 0) {
		    sb.append("'").append(cmdArray[i]).append("' ");
	        } else {
		    sb.append(cmdArray[i]).append(" ");
	        }
	    }
	    printStream.println(
		"Starting test in separate process with command:");
	    printStream.println(sb.toString());
	    File workingDir = testRun.td.getWorkingDir();
            proc = Runtime.getRuntime().exec(cmdArray, null, workingDir);
	    printStream = outStream;
	    if (discardOKOutput) {
		stream = new ByteArrayOutputStream();
		printStream = new PrintStream(stream);
	    }
	    SlaveHarness.setLogStreams(printStream);
	    TestResultFilter filter = bindOutput(proc, printStream);
	    config.setTestTotal(testList.getTestTotal());
	    config.setTestIndex(testList.getTestNumber());
	    ObjectOutputStream os = 
		new ObjectOutputStream(proc.getOutputStream());
	    os.writeObject(config);
	    os.flush();
//  	    bindInput(proc);
	    proc.waitFor();
//  	    bindInput(null);
	    outPipe.waitTillEmpty(5000);//XXX do I need to detect timeout?
	    errPipe.waitTillEmpty(5000);
	    testResult = filter.getTestResult(testRun);
	    if (testResult == null) {
		testResult = new TestResult(testRun,
				    false, 
				    Test.TEST,
				    "Test VM terminated without "
				  + "returning test status");
	    }
	} catch (InterruptedException e) {
	    testResult = new TestResult(testRun,
				false,
				Test.TEST,
				"test process was interrupted");
	    try {
		Class procClass = proc.getClass();
		Field field = procClass.getDeclaredField("pid");
		field.setAccessible(true);
		int pid = field.getInt(proc);
		printStream.println();
		printStream.println("Attempting to dump threads of " +
		    "test VM process " + pid);
		Process p = Runtime.getRuntime().exec(
		    "/usr/bin/kill -QUIT " + pid);
		p.waitFor();
		Thread.sleep(5000); //allow time for the thread dump to happen
		
		SlaveTest.broadcast(new SlaveThreadDumpRequest());
		ObjectOutputStream os = 
		    new ObjectOutputStream(proc.getOutputStream());
		os.writeObject(new MasterThreadDumpRequest());
		os.flush();
		Thread.sleep(5000);
//  	        outPipe.waitTillEmpty(10000);
//  	        errPipe.waitTillEmpty(10000);
	    } catch (Exception e2) {
		printStream.println();
		printStream.println("Attempt to dump threads of test VM failed");
	    }
        } catch (Throwable e) {
	    unexpectedException = e;
            testResult = new TestResult(testRun,
				false,
				Test.TEST,
				"runTestOtherVM failed: " + e);
        } finally {
	    if (discardOKOutput && !testResult.state) {
		printStream.flush(); // don't know if this is necessary
		outStream.print(stream.toString());
	    }
	    SlaveHarness.setLogStreams(outStream);
	    if (unexpectedException != null) {
		outStream.println("Unexpected exception:");
		unexpectedException.printStackTrace(outStream);
	    }
	    //output time stamp
	    outStream.println();
	    StringBuffer buf = new StringBuffer();
       	    (new MessageFormat("{0,time}")).
		format(new Object[] {new Date()}, buf, null);
	    outStream.println("TIME: " + buf);
	    outStream.println();
	    if (proc != null) {
	        try {
		    proc.destroy(); // just in case, silently ignore errors
		    Thread.sleep(5000); // give it time to die
		    int exitValue = proc.exitValue();
		    outStream.println("Test process was destroyed "
			+ "and returned code " + exitValue);
		} catch (IllegalThreadStateException itse) {
			outStream.println("Test process was destroyed "
			    + "but has not yet terminated");
			itse.printStackTrace();
	        } catch (Exception e) {
	        }
	    }
	} 
	return testResult;
    }

    /**
     * Attach <code>System.in</code> to the processes output stream.
     * This is an optional feature which can be turned on if a test
     * requires keyboard input.
     *
     * @param proc the process to attach <code>stdin</code> to. 
     *             <code>proc</code> may be <code>null</code>, in which case
     *             any input received by <code>System.in</code> is discarded.
     *        
     */
    private void bindInput(Process proc) {
	if (doInputBind) {
	    if (forwarder == null) {
		forwarder = new InputForwarder();
		Thread forwarderThread = 
		    new Thread(forwarder, "Forwarder");
		forwarderThread.start();
	    }
	    if (proc != null) {
		forwarder.setOutputStream(proc.getOutputStream());
	    } else {
		forwarder.setOutputStream(null);
	    }
	}
    }

    /**
     * Utility class to forward bytes from <code>System.in</code> to 
     * an output stream. An instance of this class is designed to be reused for 
     * different output streams.
     */
    private class InputForwarder implements Runnable {

        OutputStream out;
	Object outLock = new Object();

	/**
	 * Set the output stream to forward bytes to.
	 *
	 * @param out the output stream, which may be <code>null</code>
	 */
	public void setOutputStream(OutputStream out) {
	    synchronized (outLock) {
		this.out = out;
	    }
	}

	/**
	 * Forward characters from <code>System.in</code> to the processes input
	 * stream. Since the read may not be interruptible, there is no reliable
	 * way to terminate this thread when a test VM exits. Therefore the
	 * output stream will change over time via calls to
	 * <code>setOutputStream</code>.
	 */
	public void run() {
	    boolean doit = true;
	    while (doit) {
		try {
		    int charval = System.in.read();
		    synchronized (outLock) {
			if (charval == -1) {
			    doit = false;
			} else if (out != null) {
			    out.write(charval);
			    out.flush();
			}
		    }
		} catch (IOException e) {
		    outStream.println("I/O exception in forwarder:");
		    e.printStackTrace(outStream);
		}
	    }
	}
    }

    /**
     * Attach stdout and stderr to the subprocess. 
     * The <code>TestResultFilter</code> used to filter the
     * <code>System.err</code> stream is returned so that
     * the status encoded in the last line can be retrieved.
     *
     * @param proc the process to attach the IO streams to
     *
     * @return the <code>TestResultFilter</code> used to scan
     *         <code>System.err</code> 
     * @throws IOException if the process I/O streams cannot be obtained
     */
    private TestResultFilter bindOutput(Process proc, 
				    PrintStream stream) throws IOException 
    {
	TestResultFilter f = new TestResultFilter();
        outPipe = 
	    new Pipe("test-out", proc.getInputStream(), stream, null, null);
	errPipe = 
	    new Pipe("test-err", proc.getErrorStream(), stream, f, null);
	return f;
    }

    /**
     * A <code>Pipe.Filter</code> which examines the input stream for
     * the token string <code>STATUS_TOKEN</code>. Once this token is
     * detected, the input stream is absorbed by this filter and used
     * to construct a <code>TestResult</code> object.
     */
    private class TestResultFilter implements Pipe.Filter {

	byte[] inputBuffer = new byte[STATUS_TOKEN.length()];
	byte[] statusBytes = STATUS_TOKEN.getBytes();
	StringBuffer statusBuffer = new StringBuffer();
	int count;
	boolean searching = true;

	/**
	 * Search for the identifying token. The token characters, and any
	 * following characters, are not returned by this filter.
	 * 
	 * @param b the input byte to filter
	 * @return a byte array containing the filter results
	 */
	public byte[] filterInput(byte b) {
	    byte[] returnBuffer = new byte[0];
	    if (searching) {
		inputBuffer[count++] = b;
		for (int i = 0; i < count; i++) {
		    if (inputBuffer[i] != statusBytes[i]) {
			returnBuffer = new byte[count];
			System.arraycopy(inputBuffer, 
					 0, 
					 returnBuffer,
					 0, 
					 count);
			count = 0;
			break;
		    }
		}
		if (count == statusBytes.length) {
		    searching = false;
		}
	    } else {
		String s = new String(new byte[]{b});
		statusBuffer.append(s);
	    }
	    return returnBuffer;
	}

	/**
	 * Get the <code>TestResult</code> encoded in last line of the output stream
	 * by the test wrapper. The format of the data is:
	 * <ul>
	 * <li>the identifying token: STATUS_TOKEN
	 * <li>a character: 'P' if the test passed, 'F' if the test failed
	 * <li>a character representation of the integer failure type
	 * <li>a string containing the status message
	 * </ul>
	 * 
	 * @param testRun the test run
	 * @return the <code>TestResult</code> object corresponding to the encoded
	 *         info.  <code>null</code> is returned if the identifying token
	 *         was never detected, or if the encoded test result info is missing.
	 */
	private TestResult getTestResult(TestRun testRun) {
	    String s = statusBuffer.toString();
	    if (s.length() < 2) {
		return null;
	    }
	    boolean passed = (s.charAt(0) == 'P');
	    int type = s.charAt(1) - '0';
	    return new TestResult(testRun, passed, type, s.substring(2));
	}
    }

    /**
     * Generate the test result string to be sent from the test VM
     * to the harness VM.
     *
     * @param state boolean indicating whether the test passed
     * @param type the failure type
     * @param message the test summary message
     */
    static String genMessage(boolean state, int type, String message) {
	String s = STATUS_TOKEN + (state ? "P" : "F")
	                    + Integer.toString(type)
	                    + message;
	return s;
    }

    /**
     * Call all registered <code>ConfigurationVerifiers</code> for
     * the given test. If any verifier returns <code>false</code>, this
     * method returns false. Otherwise this method returns true.
     *
     * @param td the <code>TestDescription</code> of the current test
     * @param verifierNames the list of verifier class names
     * @throws TestException which wraps any exception thrown while
     *                       instantiating or calling the verifiers
     */
    private boolean checkVerifiers(TestDescription td, String verifierNames) 
	throws TestException
    {
	String[] verifiers = config.parseString(verifierNames, ", \t");
	if (verifiers != null) {
	    for (int i = 0; i < verifiers.length; i++) {
		try {
		    Class c = Class.forName(verifiers[i], 
					    true,
					    config.getTestLoader());
		    ConfigurationVerifier v = 
			(ConfigurationVerifier) c.newInstance();
		    if (!v.canRun(td, config)) {
			return false;
		    }
		} catch (Exception e) {
		    throw new TestException("Exception invoking "
					  + "configuration filter",
					    e);
		}
	    }
	}
	return true;
    }

    /**
     * Add categories of tests. Test full test/category list in
     * <code>${com.sun.jini.qa.home}/lib/testlist.txt</code> is
     * read and filtered by the set of category names supplied on
     * the command line. The input file is assumed to be sorted
     * by category, then by test name. Duplicate test names which
     * appear in more than one requested category are discarded.
     * Test names in testlist.txt are expressed using dot notation,
     * and are converted to the local file system path syntax.
     */
    private void addByCategory() throws TestException {
        String[] rqstdCats = getRequestedCategories();
	if (rqstdCats == null || rqstdCats.length == 0) {
	    outStream.println("No tests or categories specified");
	    System.exit(1);
	}
	// used to avoid dups due to multiple category membership
        HashSet testsToRun = new HashSet();
	for (int i = 0; i < rqstdCats.length; i++) {
	    ArrayList l = (ArrayList) categoryMap.get(rqstdCats[i]);
	    if (l == null) {
		outStream.println("no tests in category " + rqstdCats[i]);
		continue;
	    }
	    for (int j = 0; j < l.size(); j++) {
		String testName = (String) l.get(j);
		if (testsToRun.contains(testName)) {
		    continue;
		}
		testsToRun.add(testName);
		addTest(testName);
	    }
	}
    }

    private void loadTestDescriptions() throws TestException {
	if (categoryMap != null) {
	    return;
	}
	categoryMap = new HashMap();
	testMap = new HashMap();
	// inhibit category processing if none requested
	if (config.getStringConfigVal("categories", null) == null
	    && config.getStringConfigVal("category", null) == null)
	{
	    return;
	}
	String testJar = config.getStringConfigVal("testJar", null);
	if (testJar == null) {
	    throw new TestException("testjar is not defined");
	}
	JarFile jarFile;
	try {
	    jarFile = new JarFile(testJar);
	} catch (IOException e) {
	    throw new TestException("cannot access test jar file " + testJar,
				    e);
	}
	Enumeration en = jarFile.entries();
	while (en.hasMoreElements()) {
	    ZipEntry entry = (ZipEntry) en.nextElement();
	    if (entry.getName().endsWith(".td")) {
		Properties p = config.loadProperties(entry.getName());
		if (p.getProperty("testClass") == null) {
		    outStream.println(
			    "Test description file has no testClass property: "
			    + entry.getName());
		    continue;
		}
		testMap.put(entry.getName(), p);
		String cats = p.getProperty("testCategories");
		if (cats != null) {
		    String[] categories = config.parseString(cats, ", \t");
		    for (int i = 0; i < categories.length; i++) {
			ArrayList l = 
			    (ArrayList) categoryMap.get(categories[i]);
			if (l == null) {
			    l = new ArrayList();
			    categoryMap.put(categories[i], l);
			}
			l.add(entry.getName());
		    }
		}
	    }
	}
    }

    /**
     * This method prints to standard output, a usage message for the QA test
     * harness.
     */
    private void usage() {
        outStream.print("Usage: QARunner configFilename ");
        outStream.print("[-categories cat1,cat2]");
        outStream.println("[-tests test1,test2]");
        outStream.print("[-xcategories] cat1,cat2]");
        outStream.println("[-xtests test1,test2]");
        outStream.println("Descriptions:");
        outStream.println("configFilename");
        outStream.println("\tThe name of the properties file");
        outStream.println("\tfor this run of tests");
        outStream.println("-categories");
        outStream.println("\tA comma separated list ");
        outStream.println("\tof the categories this product is being ");
        outStream.println("\ttested in. ");
        outStream.println("-tests");
        outStream.println("\tA comma separated list of the individual");
        outStream.println("\ttests to run. These tests are only run if");
        outStream.println("\tthey are appropriate for the categories");
        outStream.println("\tthe product is in");
        outStream.println("-xcategories (optional)");
        outStream.println("\tA comma separated list of the categories");
        outStream.println("\tto exclude from the current test run. Any");
        outStream.println("\ttest belonging to one or more of these");
        outStream.println("\tcategories will not be run.");
        outStream.println("-tests (optional)");
        outStream.println("\tA comma separated list of the tests");
        outStream.println("\tto exclude from the current test run");
        outStream.println("One of -tests or -categories must be specified");
    }//end usage

    /**
     * Prints to standard output configuration information for this
     * test run. This method is somewhat out-of-date.
     */
    private void displayConfigInfo() {
        String installDir = "com.sun.jini.qa.home";//XXX note 'qa'
        String jskHome = "com.sun.jini.jsk.home";

        String[] categories = getRequestedCategories();
        StringBuffer categoryString = new StringBuffer("No Categories");
	if (categories != null) {
	    categoryString = new StringBuffer();
	    for (int i = 0; i < categories.length; i++) {
		categoryString.append(categories[i] + " ");
	    }
	}
        outStream.println("");
        outStream.println("-----------------------------------------");
        outStream.println("GENERAL HARNESS CONFIGURATION INFORMATION:");
        outStream.println("");
        outStream.println("   Date started:");
        outStream.println("      " + (new Date()).toString());
        outStream.println("   Installation directory of the JSK:");
        outStream.println("      " + jskHome + "="
		   + config.getStringConfigVal(jskHome, null));
        outStream.println("   Installation directory of the harness:");
        outStream.println("      " + installDir + "="
		   + config.getStringConfigVal(installDir, null));
	outStream.println("   Categories being tested:");
        outStream.println("      categories=" + categoryString);
        Properties properties = System.getProperties();
        outStream.println("-----------------------------------------");
        outStream.println("ENVIRONMENT PROPERTIES:");
        outStream.println("");
        outStream.println("   JVM information:");
        outStream.println("      " + properties.getProperty("java.vm.name","unknown")
                     + ", " + properties.getProperty("java.vm.version")
                     + ", " + properties.getProperty("sun.arch.data.model","32")
		     + " bit VM mode");
        outStream.println("      " + properties.getProperty("java.vm.vendor",""));
        outStream.println("   OS information:");
        outStream.println("      " + properties.getProperty("os.name","unknown")
                           + ", " + properties.getProperty("os.version")
                           + ", " + properties.getProperty("os.arch"));
        outStream.println("");

    }

    /**
     * Get the categories to run. If no categories are specified,
     * then <code>null</code> is returned to signify that all categories
     * are to be run.
     *
     * @return the list of categories of tests to run, or <code>null</code>
     *         for all categories.
     */
    private List getCategories() {
	ArrayList categories = null;
	String cats = config.getStringConfigVal("categories", null);
	if (cats == null)
	    cats = config.getStringConfigVal("category", null);
	if (cats != null) {
	    categories = new ArrayList();
	    StringTokenizer st = new StringTokenizer(cats, ",");
	    while (st.hasMoreTokens()) {
		categories.add(st.nextToken().toLowerCase());
	    }
	}
	return categories;
    }   

    /**
     * Return the timeout value to use. The value is specified by
     * the parameter named <code>com.sun.jini.qa.harness.timeout</code>, 
       and is iterpreted
     * as a value in seconds. If undefined, a default value of
     * zero (no timeout) is returned.
     *
     * @return the timeout value for a test
     */
    private int getTimeout() {
	int seconds = config.getIntConfigVal("com.sun.jini.qa.harness.timeout",
					     0);
	return seconds * 1000; // convert to milliseconds
    }

    private static class SlaveThreadDumpRequest implements SlaveRequest {

	public Object doSlaveRequest(SlaveTest slaveTest) throws Exception {
	    AdminManager manager = slaveTest.getAdminManager();
	    Iterator it = manager.iterator();
	    while (it.hasNext()) {
		Object admin = it.next();
		if (admin instanceof NonActivatableGroupAdmin) {
		    if (((NonActivatableGroupAdmin) admin).forceThreadDump()) {
			try {
			    Thread.sleep(5000); // give it time to flush
			} catch (InterruptedException e) {
			}
		    }
		}
	    }
	    return null;
	}
    }

    private static class MasterThreadDumpRequest
	implements MasterTest.MasterTestRequest 
    {

	public void doRequest(Test test) throws Exception {
	    if (test instanceof QATest) {
		((QATest) test).forceThreadDump(); // delay done by test
	    }
	}
    }
}
