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
package com.sun.jini.tool.envcheck;

import java.util.HashSet;
import java.util.ResourceBundle;
import java.util.StringTokenizer;

/**
 * A class which provides a standard way to report the results of 
 * a test. 
 */
public abstract class Reporter {

    /** the level at which information messages are generated */
    public final static int INFO = 0;

    /** the level at which warning messages are generated */
    public final static int WARNING = 1;

    /** the level at which error messages are generated */
    public final static int ERROR = 2;

    /** the header label for the source record */
    private static String sourceString;

    /** the header labels for the test result records */
    private static String[] msgHeaders = new String[3];

    /** flag controlling display of explanation text */
    private static boolean explain = false;

    /** the display reportingLevel */
    private static int reportingLevel = WARNING;

    /** the warning count */
    private static int warningCount = 0;

    /** the error count */
    private static int errorCount = 0;

    /** flag controlling printing stack traces */
    private static boolean traces = false;

    /** table of explanations which have been output,  to inhibit duplicates */
    private static HashSet explanationsGiven = new HashSet();

    /** initialize the localized header labels */
    static {
	ResourceBundle bundle = Util.getResourceBundle(EnvCheck.class);
	msgHeaders[0] = Util.getString("reporter.info", bundle);
	msgHeaders[1] = Util.getString("reporter.warning", bundle);
	msgHeaders[2] = Util.getString("reporter.error", bundle);
	sourceString = Util.getString("reporter.source", bundle);
    }

    // inhibit instantiation
    private Reporter(){
    }

    /** 
     * Set the reporting level. No validity checks are done.
     *
     * @param level the max level to generate output
     */
    public static void setLevel(int level) {
        reportingLevel = level;
    }

    /**
     * Set the explanation generation flag;
     *
     * @param explain the value to set the flag to
     */
    public static void setExplanation(boolean explain) {
	Reporter.explain = explain;
    }

    /**
     * Set the flag controlling printing of stack traces.
     *
     * @param traces if <code>true</code>, print stack traces
     */
    public static void setPrintTraces(boolean traces) {
	Reporter.traces = traces;
    }

    /**
     * Generate the output for <code>message</code> . This method is silent
     * if the message level is less than <code>reportLevel</code>.
     * If <code>explain</code> is <code>true</code>, the explanation text 
     * will be output only the first time it is encountered.  
     *
     * @param message the <code>Message</code> to print
     */
    public static void print(Message message) {
	print(message, null);
    }
    
    /**
     * Generate the output for <code>message.</code> This method is silent if
     * the message level is less than <code>Reporter.level</code>.  If
     * <code>Reporter.explain</code> is <code>true</code>, the explanation text
     * will be output only the first time it is encountered. If
     * <code>source</code> is <code>null</code>, the source record is not
     * output. If the message level is error or warning, the appropriate counter
     * is updated.
     *
     * @param message the <code>Message</code> to print
     * @param source additional information identifying the component under test
     */
    public static void print(Message message, String source) {
	int messageLevel = message.getLevel();
	if (reportingLevel > messageLevel) {
	    return;
	}
	if (messageLevel == WARNING) {
	    warningCount++;
	} else if (messageLevel == ERROR) {
	    errorCount++;
	}
	System.out.println();
	System.out.println(msgHeaders[messageLevel] 
			   + " " 
			   + message.getMessage());
	if (source != null) {
	    System.out.println(sourceString + " " + source);
	}
	if (explain) {
	    printExplanation(message);
	}
	if (traces) {
	    message.printStackTrace(); // noop if no exception
	}
    }
    
    /**
     * Output the explanation text supplied by <code>message.</code> If there is
     * no explanation text, or if the text has been output previously, this
     * method returns. Otherwise the text is output. Formatting is applied if
     * <code>message.formatExplanation()</code> returns <code>true</code>.
     *
     * @param message the <code>Message</code> to explain
     */
    private static void printExplanation(Message message) {
	String exp = message.getExplanation();
	if (exp == null) {
	    return;
	}
	if (explanationsGiven.contains(exp)) {
	    return;
	}
	explanationsGiven.add(exp);
	if (message.formatExplanation()) {
	    String indent = "    ";
	    int lineMax = 70;
	    StringBuffer buf = new StringBuffer();
	    StringTokenizer tok = new StringTokenizer(exp);
	    int lineStart = 0;
	    while (tok.hasMoreTokens()) {
		String nextWord = tok.nextToken();
		int lineLength = 
		    buf.length() + nextWord.length() + 1 - lineStart; 
		if (lineLength > lineMax) {
		    buf.append("\n");
		    lineStart = buf.length();
		}
		if (lineStart == buf.length()) {
		    buf.append(indent);
		} else {
		    buf.append(" ");
		}
		buf.append(nextWord);
	    }
	    if (buf.length() > lineStart) {
		buf.append("\n");
	    }
	    exp = buf.toString();
	    if (exp.length() > 0) {
		System.out.println();
	    }
	}
	System.out.println(exp);
    }

    /**
     * Get the total number of warning records that were generated.
     *
     * @return the warning count
     */
    public static int getWarningCount() {
	return warningCount;
    }

    /**
     * Get the total number of error records that were generated.
     *
     * @return the error count
     */
    public static int getErrorCount() {
	return errorCount;
    }

    /**
     * A container of message information to be processed by
     * <code>Reporter</code>.
     */
    public static class Message {

	/** optional exception associated with the message */
	private Throwable thrownException = null;

	/** the brief message text */
	private String message = null;

	/** the full explanation text */
	private String explanation = null;

	/** the message level, initialized to an illegal value */
	private int level = -1;

	/** flag to format the explanation */
	private boolean formatExplanation = true;

	/**
	 * Construct a <code>Message</code> having the given level
	 * and text. 
	 *
	 * @param level the message level
	 * @param message the short message text
	 * @param explanation the explanation text
	 */
	public Message(int level, String message, String explanation) {
	    this.level = level;
	    this.message = message;
	    this.explanation = explanation;
	}

	/**
	 * Construct a <code>Message</code> having the given level,
	 * text, and exception data.
	 *
	 * @param level the message level
	 * @param message the short message text
	 * @param thrownException the exception associated with the message
	 * @param explanation the explanation text
	 */
	public Message(int level,
		       String message, 
		       Throwable thrownException,
		       String explanation) 
	{
	    this.level = level;
	    this.message = message;
	    this.thrownException = thrownException;
	    this.explanation = explanation;
	}

	/**
	 * Returns the message level.
	 * 
	 * @return the level
	 */
	protected int getLevel() {
	    if (level == -1) {
		throw new IllegalStateException("Bad Level");
	    }
	    return level;
	}

	/**
	 * Returns the short message text. If a non-<code>null</code> exception
	 * is associated with this message, the exception message is
	 * appended to the short message.
	 *
	 * @return the short message
	 */
	protected String getMessage() {
	    String buf = "";
	    if (message != null) {
		buf =  message;
		if (thrownException != null) {
		    buf += ": ";
		}
	    }
	    if (thrownException != null) {
		buf += thrownException.getMessage();
	    } 
	    if (buf.length() == 0) {
		throw new IllegalStateException("No Message");
	    }
	    return buf;
	}

	/**
	 * Returns the explanation text.
	 *
	 * @return the explanation text
	 */
	protected String getExplanation() {
	    return explanation;
	}

	/**
	 * Prints a stack trace for the exception associated with this
	 * message. If there is no such exception this method returns silently.
	 */
	protected void printStackTrace() {
	    if (thrownException != null) {
		thrownException.printStackTrace();
	    }
	}

	/**
	 * Set the flag indicating whether to format the explanation text.
	 *
	 * @param b if <code>true</code>, format the text
	 */
	protected void setFormatExplanation(boolean b) {
	    formatExplanation = b;
	}

	/**
	 * Returns the flag indicating whether to format the explanation text.
	 *
	 * @return <code>true</code> if the text is to be formatted.
	 */
	protected boolean formatExplanation() {
	    return formatExplanation;
	}
    }
}
