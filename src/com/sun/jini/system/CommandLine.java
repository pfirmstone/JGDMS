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
package com.sun.jini.system;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.RandomAccessFile;
import java.io.Reader;
import java.io.Writer;

/**
 * <code>CommandLine</code> is a base class for command line parsing
 * classes.  It provides several useful methods for the implementor
 * of the subclass and for the programmer.  It also defines exceptions
 * that can be generated during parsing commands.
 * <p>
 * <code>CommandLine</code> provides you several methods to get input
 * streams from the command line.  If these do not suffice for your
 * particular needs, you can get the argument as a <code>String</code>
 * and do your own processing.
 * <p>
 * You can use these methods to parse your own operand arguments.  For
 * example, if your operands are integers, you may want to use
 * <code>parseInt</code> to parse them to provide consistency to the
 * user.
 *
 * @author Sun Microsystems, Inc.
 *
 * @see POSIXCommandLine
 * @see MultiCommandLine
 */
public abstract class CommandLine {
    /**
     * Return the result of parsing the given string from the command
     * line.  If <code>str</code> is <code>null</code>, return
     * <code>defaultValue</code>.
     */
    public static String parseString(String str, String defaultValue) {
	return (str != null ? str : defaultValue);
    }

    /**
     * Return the result of parsing the given <code>int</code> from the
     * command line.  If <code>str</code> is <code>null</code>, return
     * <code>defaultValue</code>.  The value is parsed using to
     * understand leading <code>0x</code> or <code>#</code> to
     * introduce a hexidecimal number, leading <code>0</code> to
     * introduce an octal number, and anything else to be a decimal
     * number.
     */
    public static int parseInt(String str, int defaultValue)
	throws NumberFormatException
    {
	if (str == null)
	    return defaultValue;
	if (str.startsWith("0x"))
	    return Integer.parseInt(str.substring(2), 16);
	else if (str.startsWith("#"))
	    return Integer.parseInt(str.substring(1), 16);
	else if (str.startsWith("0"))
	    return Integer.parseInt(str.substring(1), 8);
	return Integer.parseInt(str);
    }

    /**
     * Return the result of parsing the given <code>long</code> from
     * the command line.  If <code>str</code> is <code>null</code>,
     * return <code>defaultValue</code>.  The value is parsed using to
     * understand leading <code>0x</code> or <code>#</code> to
     * introduce a hexidecimal number, leading <code>0</code> to
     * introduce an octal number, and anything else to be a decimal
     * number.
     */
    public static long parseLong(String str, long defaultValue)
	throws NumberFormatException
    {
	if (str == null)
	    return defaultValue;
	if (str.startsWith("0x"))
	    return Long.parseLong(str.substring(2), 16);
	else if (str.startsWith("#"))
	    return Long.parseLong(str.substring(1), 16);
	else if (str.startsWith("0"))
	    return Long.parseLong(str.substring(1), 8);
	return Long.parseLong(str);
    }

    /**
     * Return the result of parsing the given <code>double</code> from
     * the command line.  If <code>str</code> is <code>null</code>,
     * return <code>defaultValue</code>.  The value is parsed using
     * <code>Double.value</code>.
     */
    public static double parseDouble(String str, double defaultValue)
	throws NumberFormatException
    {
	if (str == null)
	    return defaultValue;
	return new Double(str).doubleValue();
    }

    /**
     * Return a <code>Writer</code> that is the result of creating a
     * new <code>FileWriter</code> object for the file named by the
     * given <code>path</code>.  If the argument is `<code>-</code>' then
     * an <code>OutputStreamWriter</code> for <code>System.out</code>
     * is returned.  If <code>path</code> is <code>null</code>, return
     * <code>defaultValue</code>.
     */
    public static Writer parseWriter(String path, Writer defaultValue)
	throws IOException
    {
	if (path == null)
	    return defaultValue;
	if (path.equals("-"))
	    return new OutputStreamWriter(System.out);
	return new FileWriter(path);
    }

    /**
     * Return a <code>Writer</code> that is the result of creating a
     * new <code>FileWriter</code> object for the file named by the
     * given <code>path</code>.  If the argument is `<code>-</code>' then
     * an <code>OutputStreamWriter</code> for <code>System.out</code>
     * is returned.  If <code>path</code> is <code>null</code>, the
     * string <code>path</code> is used as the file name.
     */
    public static Writer parseWriter(String path, String defaultPath)
	throws IOException
    {
	if (path == null)
	    path = defaultPath;
	if (path.equals("-"))
	    return new OutputStreamWriter(System.out);
	return new FileWriter(path);
    }

    /**
     * Return a <code>Reader</code> that is the result of creating a
     * new <code>FileReader</code> object for the file named by the
     * given <code>path</code>.  If the argument is `<code>-</code>' then
     * an <code>InputStreamReader</code> for <code>System.in</code> is
     * returned.  If <code>path</code> is <code>null</code>, return
     * <code>defaultValue</code>.
     */
    public static Reader parseReader(String path, Reader defaultValue)
	throws IOException
    {
	if (path == null)
	    return defaultValue;
	if (path.equals("-"))
	    return new InputStreamReader(System.in);
	return new FileReader(path);
    }

    /**
     * Return a <code>Reader</code> that is the result of creating a
     * new <code>FileReader</code> object for the file named by the
     * given <code>path</code>.  If the argument is `<code>-</code>' then
     * an <code>InputStreamReader</code> for <code>System.in</code> is
     * returned.  If <code>path</code> is <code>null</code>, the string
     * <code>path</code> is used as the file name.
     */
    public static Reader parseReader(String path, String defaultPath)
	throws IOException
    {
	if (path == null)
	    path = defaultPath;
	if (path.equals("-"))
	    return new InputStreamReader(System.in);
	return new FileReader(path);
    }

    /**
     * Return a <code>OutputStream</code> that is the result of
     * creating a new <code>FileOutputStream</code> object for the file
     * named by the given <code>path</code>.  If the argument is
     * `<code>-</code>' then <code>System.out</code> is returned.  If
     * <code>path</code> is <code>null</code>, return
     * <code>defaultValue</code>.
     */
    public static OutputStream
	parseOutputStream(String path, OutputStream defaultValue)
	throws IOException
    {
	if (path == null)
	    return defaultValue;
	if (path.equals("-"))
	    return System.out;
	return new FileOutputStream(path);
    }

    /**
     * Return a <code>InputStream</code> that is the result of creating
     * a new <code>FileInputStream</code> object for the file named by
     * the given <code>path</code>.  If the argument is `<code>-</code>'
     * then <code>System.in</code> is returned.  If <code>path</code>
     * is <code>null</code>, the string <code>path</code> is used as
     * the file name.
     */
    public static OutputStream
	parseOutputStream(String path, String defaultPath)
	throws IOException
    {
	if (path == null)
	    path = defaultPath;
	if (path.equals("-"))
	    return System.out;
	return new FileOutputStream(path);
    }

    /**
     * Return a <code>InputStream</code> that is the result of creating
     * a new <code>FileInputStream</code> object for the file named by
     * the given <code>path</code>.  If the argument is `<code>-</code>'
     * then <code>System.in</code> is returned.  If <code>path</code>
     * is <code>null</code>, return <code>defaultValue</code>.
     */
    public static InputStream
	parseInputStream(String path, InputStream defaultValue)
	throws IOException
    {
	if (path == null)
	    return defaultValue;
	if (path.equals("-"))
	    return System.in;
	return new FileInputStream(path);
    }

    /**
     * Return a <code>InputStream</code> that is the result of creating
     * a new <code>FileInputStream</code> object for the file named by
     * the given <code>path</code>.  If the argument is `<code>-</code>'
     * then <code>System.in</code> is returned.  If <code>path</code>
     * is <code>null</code>, the string <code>path</code> is used as
     * the file name.
     */
    public static InputStream parseInputStream(String path, String defaultPath)
	throws IOException
    {
	if (path == null)
	    path = defaultPath;
	if (path.equals("-"))
	    return System.in;
	return new FileInputStream(path);
    }

    /**
     * Return a <code>RandomAccessFile</code> that is the result of
     * creating a new <code>RandomAccessFile</code> object for the file
     * named by the given <code>path</code>, using the given
     * <code>mode</code>.  If <code>path</code> is <code>null</code>,
     * return <code>defaultValue</code>.
     */
    public static RandomAccessFile
	parseRandomAccessFile(String path, RandomAccessFile defaultValue,
			      String mode)
	throws IOException
    {
	if (path == null)
	    return defaultValue;
	return new RandomAccessFile(path, mode);
    }

    /**
     * Return a <code>RandomAccessFile</code> that is the result of
     * creating a new <code>RandomAccessFile</code> object for the file
     * named by the given <code>path</code>, using the given
     * <code>mode</code>.  If <code>path</code> is <code>null</code>,
     * the string <code>path</code> is used as the file name.
     */
    public static RandomAccessFile
	parseRandomAccessFile(String path, String defaultPath, String mode)
	throws IOException
    {
	if (path == null)
	    path = defaultPath;
	return new RandomAccessFile(path, mode);
    }

    /**
     * Signal that the programmer has invoked a method out of order.
     * The specific errors that generate this exception are defined
     * by subclasses.
     */
    public static class ProgrammingException extends RuntimeException {
	static final long serialVersionUID = 2401745757311140184L;

	/** Create an exception with the given detail string. */
	public ProgrammingException(String str) {
	    super(str);
	}
    }

    /**
     * Signal that the user specified an option in an improper way.
     * The most common errors are not providing an argument to an
     * option that needed one or specifying an unknown option.
     */
    public static class BadInvocationException extends Exception {
	static final long serialVersionUID = 4503820475450471907L;

	/** Create an "Argument required" exception for the given option. */
	public BadInvocationException(Object opt) {
	    super("Argument required for '" + opt + "'");
	}
	/** Create an exception with the given detail string. */
	public BadInvocationException(String str) {
	    super(str);
	}
    }

    /**
     * Signal that this was a "help only" invocation.  The program is
     * expected to catch this and simply exit successfully.
     */
    public static class HelpOnlyException extends Exception {
	static final long serialVersionUID = -8973201446772368025L;

	/**
	 * Create a new <code>HelpOnlyException</code> with a descriptive
	 * string.
	 */
	public HelpOnlyException() {
	    super("User only asked for help");
	}
    }
}
