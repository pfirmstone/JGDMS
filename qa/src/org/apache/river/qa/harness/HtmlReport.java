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

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.PrintStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.Properties;
import java.util.StringTokenizer;

/**
 * Generates an html report of the test results.
 * <p>
 * This class relies on these config entries:
 * <ul>
 * <li><code>org.apache.river.qa.harness.generateHtml.resultLog</code> is
 *     the file name of the report.
 * <li><code>org.apache.river.qa.harness.generateHtml.title</code> is 
 *     the title of the report.
 * <li><code>org.apache.river.qa.harness.generateHtml.links</code> is
 *     a comma separated list of files to generate links to within the report.
 * <li><code>org.apache.river.qa.harness.generateHtml.testOrderJsp</code> is
 *     the URL for submitting <code>TestOrder</code> parameters to the
 *     ui webserver.
 * <li><code>org.apache.river.qa.harness.generateHtml.testOrderFileName</code>
 *     is the name of the file (relative) containing the persisted
 *     <code>TestOrder</code> state.
 * </ul>
 */
public class HtmlReport {

    private QAConfig config; 
    private PrintStream out; 
    private TestList testList;
    private String resultsDir = null;

    /**
     * Construct a new HtmlReport object.
     *
     * @param config the config object used to retrieve config entries
     * @param testList the data structure contains test results
     */
    HtmlReport(QAConfig config, TestList testList) throws IOException {
	this.config = config;
	this.testList = testList;
	out = getPrintStream();
    }

    /**
     * Generate the html report using the testList provided to constructor.
     */
    public void generate() throws IOException {
	printHeading();

	out.println("<UL>");
	out.println("<TABLE CELLPADDING=15 BORDER=0>");
	out.println("<TR VALIGN=TOP>");

	printLinks();
	if (config.isMaster()) {
	    printStats();
	    printTimes();
            printEnvironment();
	}

	out.println("</TR>");
	out.println("</TABLE>");
	out.println("</UL>");
	out.println();
	if (config.isMaster()) {
	    out.println("<P><P>");
	    out.println();

	    printTable();
	}

	printFooter();
    }

    /**
     * Opens stream to write the report to.  The underlying filename
     * is retrieved from the config object.
     */
    private PrintStream getPrintStream() throws IOException {
	String resultLog = config.getStringConfigVal(
	    "org.apache.river.qa.harness.generateHtml.resultLog","index.html");
	File resultDir = (new File(resultLog)).getParentFile();
	if (resultDir != null && !resultDir.exists()) {
	    resultDir.mkdirs();
	}
        return new PrintStream(new FileOutputStream(resultLog));
    }

    /**
     * Prints report header.
     */
    private void printHeading() throws IOException {
	String title = config.getStringConfigVal(
	    "org.apache.river.qa.harness.generateHtml.title","Jini Test Results");
	out.println("<HTML>");
	out.println("<HEAD>");
	out.println("  <TITLE>" + title + "</TITLE>");
	out.println("</HEAD>");
	out.println();
	out.println("<BODY>");
	out.println("  <H2 ALIGN=CENTER>" + title + "</H2>");
	out.println();
    }

    /**
     * Prints links to other test files into the report.  The links are
     * retrieved from the config object.
     */
    private void printLinks() throws IOException {
	String links = config.getStringConfigVal(
	    "org.apache.river.qa.harness.generateHtml.links", null);
	if (links != null) {
	    out.println("<TD>");
	    out.println("<UL>");
	    StringTokenizer linkTokens = new StringTokenizer(links);
	    while (linkTokens.hasMoreTokens()) {
		String link = linkTokens.nextToken();
		String name = (new File(link)).getName();
		out.println("  <LI><A HREF=\"" + link + "\">" + name + "</A>");
	    }
	    String testOrderFileName = config.getStringConfigVal(
		"org.apache.river.qa.harness.generateHtml.testOrderFileName", null);
	    File testOrderFile = null;
	    if (testOrderFileName != null) {
		testOrderFile = new File(testOrderFileName);
		if (testOrderFile.exists()) {
		    out.println("  <LI><A HREF=\"javascript:orderLink()\">" + testOrderFileName + "</A>");
		} else {
		    testOrderFile = null; // to prevent script from being generated
		}
	    }
	    out.println("</UL>");
	    out.println("</TD>");
	    out.println();
	    if (testOrderFile != null) {
		out.println(genTestOrderScript(testOrderFile));
	    }
	}
    }

    private String genTestOrderScript(File orderFile) {
	String formAction = 
	    config.getStringConfigVal(
		     "org.apache.river.qa.harness.generateHtml.testOrderJsp", null);
	String testOrderFileName = 
	    config.getStringConfigVal(
		     "org.apache.river.qa.harness.generateHtml.testOrderFileName", null);
	if (testOrderFileName == null || formAction == null) {
	    return "";
	}
	String registrarURL = 
	    config.getStringConfigVal("org.apache.river.qa.harness.generateHtml.registrarURL", null);
	StringBuffer buf = new StringBuffer();
	// generate form
	buf.append("<form name='testOrderForm' action='" + formAction + "' method='post'>\n");
	buf.append("  <input type ='hidden' name='orderURL' value=''>\n");
	if (registrarURL != null) {
	    buf.append("  <input type ='hidden' name='registrarURL' value='" + registrarURL + "'>\n");
	}
	buf.append("</form>\n");
	buf.append("\n");
	
	// generate script
	buf.append("<script>\n");
	buf.append("function orderLink() {\n");
	buf.append("  var form = document.forms['testOrderForm'];\n");
	buf.append("  form.orderURL.value = document.URL.substr(0, document.URL.lastIndexOf(\"/\") + 1) + \"" + testOrderFileName + "\";\n");
	buf.append("  form.submit();\n");
	buf.append("}\n");
	buf.append("\n");
	buf.append("function commentLink(formName) {\n");
	buf.append("  open(null, 'commentwindow', 'width=700,height=200');\n");
	buf.append("  var form = document.forms[formName];\n");
	buf.append("  form.submit();\n");
	buf.append("}\n");
	buf.append("</script>\n");
	return buf.toString();
    }

    /**
     * Prints test statistics (passes, fails, reruns, etc) to the report.
     * The number of reruns and number of skipped tests are only
     * printed if non-zero.
     */
    private void printStats() throws IOException {
	out.println("<TD>");
	out.println("<PRE># of tests started   = " 
	    + testList.getNumStarted());
	out.println("# of tests completed = " 
	    + testList.getNumCompleted());
	if (testList.getNumSkipped() > 0) {
	    out.println("# of tests skipped   = " 
	    + testList.getNumSkipped());
	}
	out.println("# of tests passed    = " 
	    + testList.getNumPassed());
	out.println("# of tests failed    = " 
	    + testList.getNumFailed());
	if (testList.getNumRerun() > 0) {
	    out.println("# of tests rerun     = " 
	    + testList.getNumRerun());
	}
	out.println("</PRE>");
	out.println("</TD>");
	out.println();
    }

    /**
     * Prints information about the test environment.
     */
    private void printEnvironment() throws IOException {
        String installDir = "org.apache.river.qa.home";//XXX note 'qa'
        String jskHome = "org.apache.river.jsk.home";
        out.println();
        out.println("<TD>");
        out.println("<PRE>Installation directory of the JSK:");
        out.println("      " + jskHome + "="
		   + config.getStringConfigVal(jskHome, null));
        out.println("Installation directory of the harness:");
        out.println("      " + installDir + "="
		   + config.getStringConfigVal(installDir, null));
	//out.println("   Categories being tested:");
        //out.println("      categories=" + categoryString);
        out.println("</PRE>");
        out.println("</TD>");

        Properties properties = System.getProperties();
        out.println("<TD>");
        out.println("<PRE>JVM information:");
        out.println("      " + properties.getProperty("java.vm.name","unknown")
                     + ", " + properties.getProperty("java.vm.version")
                     + ", " + properties.getProperty("sun.arch.data.model","32")
		     + " bit VM mode");
        out.println("      " + properties.getProperty("java.vm.vendor",""));
        out.println("OS information:");
        out.println("      " + properties.getProperty("os.name","unknown")
                           + ", " + properties.getProperty("os.version")
                           + ", " + properties.getProperty("os.arch"));
        out.println("</PRE>");
        out.println("</TD>");
        out.println();
    }

    /**
     * Prints start, finish, and elapsed times to the report.
     */
    private void printTimes() throws IOException { 
	out.println("<TD>");
	out.println("<PRE>Started:    " 
	    + (new Date(testList.getStartTime())));
	out.println("Finished:   " 
	    + (new Date(testList.getFinishTime())));
	out.println("Elapsed seconds: " 
	    + ((testList.getDurationTime())/1000));
	out.println("</PRE>");
	out.println("</TD>");
	out.println();
    }

    /**
     * Prints results table.  Failed tests that are rerun (any number of
     * times) and finally pass are printed as a 'passed' test result.
     */
    private void printTable() throws IOException {
	String filePath = getCommentPath("Result");
	out.println("<UL>");
	out.println("  <TABLE BORDER=2>");
	out.println("  <TR>");
	out.println("  <TH>"); 
	out.println("    <form name='Result'\n"
		  + "          action='http://jiniautot.east:8080/autot/GetComment.jsp'\n"
		  + "          method='post'\n"
		  + "          target='commentwindow'>\n"
		  + "      <input type='hidden' \n"
		  + "             name='commentFile'\n"
		  + "             value='" + filePath + "'>\n"
		  + "      <input type='hidden'\n"
		  + "             name='testName'\n"
		  + "             value='Result'>\n"
		  + "      <input type='hidden'\n"
		  + "             name='errorMsg'\n"
		  + "             value=''>\n"
		  + "    </form>\n"
		  + "    <a href='javascript:commentLink(\"Result\")'>");
	out.println("    Result</a></TH>");
	out.println("    <TH>Test</TH>");
	out.println("  </TR>");
        TestList.TestResultIterator iter = testList.createTestResultIterator();
        while (iter.hasMore()) {
            TestResult[] results = iter.next();
	    TestResult lastResult = results[results.length - 1];
	    // the last test result indicates the final status for the test
	    if (lastResult.type == Test.SKIP) {
		printSkippedTableRow(lastResult);
	    } else if (lastResult.state) {
		printPassedTableRow(results);
	    } else {
		printFailedTableRow(results);
	    }
	}
	out.println("  </TABLE>");
	out.println("</UL>");
	out.println();
    }

    /**
     * Prints a skipped test row to the results table in the report.
     */
    private void printSkippedTableRow(TestResult result) throws IOException {
	out.println("  <TR>");
	out.println("    <TH>");
	out.println("      Skipped");
	out.println("    </TH>");
	out.println("    <TD>");
	out.println("      " + formatTestName(result.run.td));
	out.println("    </TD>");
	out.println("  </TR>");
    }

    /**
     * Prints results for a single, passed test into a row in the 
     * results table.  The initial run and any reruns appear in the same row.
     * If a result's logFile entry is null then no link is printed.
     */
    private void printPassedTableRow(
	TestResult[] results) throws IOException 
    {
	if (results.length == 0) {
	    return; // defensive, probably can't happen
	}
	TestResult r = results[0];
	String shortName = getRelativeName(r.run.td);
	String filePath = getCommentPath(shortName);
	out.println("  <TR>");
	out.println("    <TH>");
	out.println("    <form name='" + shortName + "'\n"
		  + "          action='http://jiniautot.east:8080/autot/GetComment.jsp'\n"
		  + "          method='post'\n"
		  + "          target='commentwindow'>\n"
		  + "      <input type='hidden' \n"
		  + "             name='commentFile'\n"
		  + "             value='" + filePath + "'>\n"
		  + "      <input type='hidden'\n"
		  + "             name='testName'\n"
		  + "             value='" + shortName + "'>\n"
		  + "      <input type='hidden'\n"
		  + "             name='errorMsg'\n"
		  + "             value=''>\n"
		  + "    </form>\n"
		  + "    <a href='javascript:commentLink(\"" + shortName + "\")'>");
	out.println("      <FONT COLOR=GREEN>Passed</FONT>");
	out.println("    </a>");
	out.println("    </TH>");
	out.println("    <TD>");
	for (int i = 0; i < results.length; i++) {
	    if (i > 0) {
	        out.println("     <BR>");
	        out.println("     <B>Re-run:</B><BR>");
	    }
	    TestResult result = results[i];
	    String testName = formatTestName(result.run.td);
	    if (result.logFile == null) {
	        out.println("     " + testName);
	    } else {
	        String logName = result.logFile.getName();
	        out.println("      <A HREF=" + logName + ">" + testName + "</A>");
	    }
	    if (!result.state) {
	        out.println("<BR>");
	        out.println("      " + result.message);
	    }
	}
	out.println("    </TD>");
	out.println("  </TR>");
    }

    /**
     * Prints results for a single, failed test into a row in the 
     * results table.  The initial run and any reruns appear in the same row.
     * A failed result should always have a non-null logFile entry.
     */
    private void printFailedTableRow(
	TestResult[] results) throws IOException 
    {
	if (results.length == 0) {
	    return; // defensive, probably can't happen
	}
	TestResult r = results[0];
	String shortName = getRelativeName(r.run.td);
	String filePath = getCommentPath(shortName);
	out.println("  <TR>");
	out.println("    <TH>");
	out.println("    <form name='" + shortName + "'\n"
		  + "          action='http://jiniautot.east:8080/autot/GetComment.jsp'\n"
		  + "          method='post'\n"
		  + "          target='commentwindow'>\n"
		  + "      <input type='hidden' \n"
		  + "             name='commentFile'\n"
		  + "             value='" + filePath + "'>\n"
		  + "      <input type='hidden'\n"
		  + "             name='testName'\n"
		  + "             value='" + shortName + "'>\n"
		  + "      <input type='hidden'\n"
		  + "             name='errorMsg'\n"
		  + "             value='" + cleanup(r.message) + "'>\n"
		  + "    </form>\n"
		  + "    <a href='javascript:commentLink(\"" + shortName + "\")'>");
	out.println("      <FONT COLOR=RED>Failed</FONT>");
	out.println("    </a>");
	out.println("    </TH>");
	out.println("    <TD BGCOLOR=#FFC0C0>");
	for (int i = 0; i < results.length; i++) {
	    if (i > 0) {
	        out.println("     <BR>");
	        out.println("     <B>Re-run:</B> ");
	    }
	    TestResult result = results[i];
	    String testName = formatTestName(result.run.td);
	    String logName = result.logFile.getName();
	    out.println("      <A HREF=" + logName + ">" + testName + "</A><BR>");
	    out.println("      " + result.message);
	}
	out.println("    </TD>");
	out.println("  </TR>");
    }

    private String cleanup(String s) {
	s = s.replaceAll("\\r", "");
	s = s.replaceAll("\\n", " ");
	s = s.replaceAll("\"", "'");
        return s;
    }

    /**
     * Prints report footer.
     */
    private void printFooter() throws IOException {
	out.println("<UL><P>End of file</P></UL>");
	out.println();
	out.println("</BODY>");
	out.println("</HTML>");
    }

    /**
     * Retrieves test name from the given TestDescription and
     * replaces slashes ('/' and '\') with underscore characters.
     */
    private String formatTestName(TestDescription td) {
	return td.getName().replace('/','_').replace('\\','_');
    }

    private String getRelativeName(TestDescription td) {
	String fullName = td.getName();
	if (fullName.endsWith(".td")) {
	    fullName = fullName.substring(0, fullName.lastIndexOf(".td"));
	}
	fullName = fullName.substring(fullName.lastIndexOf('/') + 1);
	return fullName;
    }

    private String getCommentPath(String shortName) {
	if (resultsDir == null) {
	    resultsDir = config.getStringConfigVal(
		     "org.apache.river.qa.harness.generateHtml.resultsDir", "");
	}
	return resultsDir + "/" + shortName + ".comment";
    }
}
