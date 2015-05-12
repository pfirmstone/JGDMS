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

package org.apache.river.test.impl.end2end.e2etest;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.Date;
import java.util.Set;
import java.util.HashSet;
import java.util.StringTokenizer;

/**
 * A class which supports the logging requirements of the test. Logging
 * is controlled by a set of flags provided by the <code>end2end.logArgs</code>
 * property. This set of flags is defined in the <code>Constants</code>
 * interface. Multiple log records are buffered and then written atomically
 * by one of the <code>writeLog</code> methods, so that related records
 * are kept together for multithreaded tests
 */
class Logger implements Constants {

    /** the desired width of a header */
    private static final int HEADERWIDTH = 80;

    /** the flags obtained from the <code>end2end.logArgs</code> property. */
    private static Set logFlagsSet;

    /** flag whether to buffer log results */
    private static boolean bufferLog;

    /** the buffer containing text to be logged */
    private StringBuffer buffer = new StringBuffer();

    /** header string for boundaries */
    private String header;

    /** footer string for boundaries */
    private String footer;

    /**
      * get the flags and store them in logFlagsSet. Also, set System.err
      * to System.out so that redirecting stdout redirects everything
     */
    static {
	logFlagsSet = new HashSet();
	String props = System.getProperty("end2end.logArgs");
        if (props == null) {
            props = ALWAYS;
        }
        else {
            props =  ALWAYS + "," + props;
        }
	StringTokenizer st = new StringTokenizer(props,",");
	while (st.hasMoreTokens()) {
            String flag = st.nextToken();
	    boolean matched = false;
	    for (int i=legalLogFlags.length; --i>=0; ) {
		if (legalLogFlags[i].equals(flag)) {
		    matched = true;
		    break;
		}
	    }
	    if (!matched) {
		throw new TestException("log flag "
                                      +  flag + " "
                                      + "is not recognized",
                                         null);
	    }
	    logFlagsSet.add(flag);
	}
        System.setErr(System.out);
    }

    /**
     * Control whether logging is to be buffered. Access to the bufferLog
     * flag is not synchronized since it is a readonly attribute, set
     * just once during initialization in End2EndTest.main.
     *
     * @param buffered if <code>true</code>, log records are buffered
     *                 between <code>writeLog</code> calls
     */
    static void setBufferedLog(boolean buffered) {
	bufferLog = buffered;
    }

    /**
     * Create the header string, and print it if output
     * is unbuffered
     *
     * @param message the message for the boundary header
     */
     void startBoundary(String message) {
        if (loggable(BOUNDARIES)) {
	    if (message.length() < (HEADERWIDTH - 4)) {
		StringBuffer msg = new StringBuffer();
		int leaderCount = (HEADERWIDTH - message.length() - 2)/2;
		int trailerCount = leaderCount;
		if ((message.length() % 2) == 1) leaderCount++;
		while (--leaderCount >= 0) {
		    msg.append("*");
		}
		msg.append(" ");
		msg.append(message);
		msg.append(" ");
		while (--trailerCount >= 0) {
		    msg.append("*");
		}
		message = msg.toString();
	    }
	    header = message;
	    StringBuffer fb = new StringBuffer();
	    for (int i = message.length(); --i >= 0; ) {
		fb.append("*");
	    }
	    footer = fb.toString();
	    if (!bufferLog) {
                System.out.println();
		System.out.println(header);
	    }
	}
    }

    /**
     * Create the footer string, and print it if output
     * is unbuffered
     */
     void endBoundary() {
        if (loggable(BOUNDARIES)) {
	    if (header != null) {
		StringBuffer fb = new StringBuffer();
		for (int i = header.length(); --i >= 0; ) {
		    fb.append("*");
		}
		footer = fb.toString();
		if (!bufferLog) {
		    System.out.println(footer);
		}
	    }
	}
    }

    /**
     * check whether the given String was included in the set of log
     * flags obtained from <code>end2end.logArgs</code>.
     *
     * @param logFlag the String to test
     *
     * @return <code>true</code> if the <code>logFlag</code> matches a flag
     * 				 passed in the <code>end2end.logArgs</code>,
     *                           or if the user specified the ALL flag.
     */

    boolean loggable(String logFlag) {
	return (logFlagsSet.contains(logFlag) || logFlagsSet.contains(ALL));
    }

    /**
     * Append a log record to the log buffer
     *
     * @param logFlag flag identifying the type of this message
     * @param message the record to add to the buffer
     */
    void log(String logFlag, String message) {
        String timeStampedMessage = new Date().toString() + " " + message;
	if (loggable(logFlag)) {
            if (bufferLog) {
		buffer.append(timeStampedMessage)
		      .append("\n");
	    } else {
		System.out.println(timeStampedMessage);
	    }
        }
    }

    /**
     * Append an exception stack trace to the log buffer
     *
     * @param logFlag flag identifying the type of this message
     * @param t the throwable whose trace is to be added to the buffer
     */
    void log(String logFlag, Throwable t) {
	if (loggable(logFlag)) {
	    if (bufferLog) {
		PrintStream origStream = System.err;
		ByteArrayOutputStream s = new ByteArrayOutputStream();
		PrintStream ps = new PrintStream(s);
		System.setErr(ps);
		t.printStackTrace();
		ps.flush();
		buffer.append(s.toString());
		System.setErr(origStream);
	    }
	    else {
		t.printStackTrace();
	    }
	}
    }

    /**
     * Append an stack trace for the current thread to the log buffer
     *
     * @param logFlag flag identifying the type of this message
     */
    void dump(String logFlag) {
	if (loggable(logFlag)) {
	    if (bufferLog) {
		PrintStream origStream = System.err;
		ByteArrayOutputStream s = new ByteArrayOutputStream();
		PrintStream ps = new PrintStream(s);
	 	System.setErr(ps);
		Thread.dumpStack();
		ps.flush();
		buffer.append(s.toString());
		System.setErr(origStream);
	    } else {
		Thread.dumpStack();
	    }

	}
    }

    /**
     * Reset the buffer.
     */
    private void reset() {
	if (buffer.length() > 0) {
	    buffer = new StringBuffer();
	}
	header = null;
	footer = null;
    }

    /**
     * Write the log buffer atomically to System.out. The buffer is
     * reset to the empty state. If the log flags provided by the user
     * includes the BOUNDARIES flag, then then log buffer is wrapped
     * with beginning and ending lines to visually delimit the buffer.
     */
    void writeLog() {
	String s = getLogBuffer();
	if (s != null) System.out.println(s);
	reset();
    }

    /**
     * get the current log buffer. If the log flags provided by the user
     * includes the BOUNDARIES flag, then then log buffer is wrapped
     * with beginning and ending lines to visually delimit the buffer.
     * If the buffering is not being done, or the log buffer is empty,
     * null is returned.
     *
     * @return A string containing the contents of the log buffer, optionally
     *         wrapped by boundary strings. Null is returned if the buffer
     *         is empty or buffering is turned off
     */
    String getLogBuffer() {
	String s = null;
	if (bufferLog) {
	    if (buffer.length() > 0) {
		StringBuffer b = new StringBuffer();
		if (header != null) {
		    b.append("\n");
                    b.append(header);
		    b.append("\n");
		}
		b.append(buffer);
		if (footer != null) {
		    b.append(footer);
		    b.append("\n");
		}
		s = b.toString();
	    }
        }
	return s;
    }
}
