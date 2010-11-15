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

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.io.Reader;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Iterator;

/**
 * This class parses a command line using POSIX 1003.2-1992 rules.
 * These are:
 * <ul>
 * <li>Each option name is a single alphanumeric character.
 * <li>All options should be preceded by the "<code>-</code>" character
 * <li>Options that take no arguments can be grouped together behind
 *     a single <code>-</code> (e.g., <code>-nek</code>)
 * <li>Each option that takes an argument should be specified separately.
 * <li>Arguments to options are not optional; an option either always
 *     takes an argument or it doesn't.
 * <li>If multiple arguments are given to the same option, they should
 *     be separated by commas (e.g., <code>-f path1,path2,path3</code>).
 * <li>All options should precede operands on the command line.  ("Operands"
 *     are the arguments that are not options; in <code>cat -u x y</code>,
 *     <code>x</code> and <code>y</code> are operands.)
 * <li>The argument <code>--</code> signals the end of options.  All
 *     remaining words on the command line are operands
 * <li>The order of options relative to one another should not matter.
 * <li>For utilities that use operands to specify files to be opened,
 *     the "<code>-</code>" should mean standard input or output.
 * </ul>
 * <code>POSIXCommandLine</code> does not enforce the alphanumeric property
 * of option characters, nor that options with arguments must be alone
 * on a line (<code>-nekfpath</code> <em>vs.</em> <code>-nek
 * -fpath</code>).  <code>POSIXCommandLine</code> also recognizes the common
 * style of using a <code>-?</code> option to get command usage;
 * nothing prevents you from adding your own option for this purpose.
 * <code>POSIXCommandLine</code> does not aid in splitting up multiple
 * arguments to one option; <code>java.util.StringTokenizer</code> does
 * this quite well enough.
 * <p>
 * To use <code>POSIXCommandLine</code>, create a <code>POSIXCommandLine</code>
 * object with the array of strings you wish to parse (typically the
 * array passed to the utility's <code>main</code> method), and then
 * consume options from it, providing default values in case the option
 * is not specified by the user.  When you have consumed all the
 * options, you invoke the <code>POSIXCommandLine</code> object's
 * <code>getOperands</code> method to return the remaining operands on
 * the command line.  If <code>--</code> is specified it is neither an
 * option nor an operand, just a separator between the two lists.  The
 * <code>CommandLine.BadInvocationException</code> is used to signal
 * errors in the construction of the strings, that is, a user error,
 * such as specifying a option that takes an argument but forgetting to
 * provide that argument.  See the documentation for
 * <code>POSIXCommandLine.main</code> for an example.
 * <p>
 * You must call <code>getOperands</code> for proper behavior, even if
 * you do not use any operands in your command.  <code>getOperands</code> 
 * checks for several user errors, including unknown options.  If you
 * do not expect to use operands, you should check the return value of
 * <code>getOperands</code> and complain if any are specified.
 * <p>
 * You must consume (check for) all options that take arguments before
 * you consume any boolean (no-argument) option.  Further, no options
 * can be consumed after <code>getOperands</code> is invoked.  Each
 * option character may be used only once.  Failure to follow these
 * rule is a programmer error that will result in a
 * <code>CommandLine.ProgrammingException</code>.
 * <p>
 * <code>POSIXCommandLine</code> provides you several methods to get input
 * streams from the command line.  If these do not suffice for your
 * particular needs, you can get the argument as a <code>String</code>
 * and do your own processing.
 *
 * @author Sun Microsystems, Inc.
 *
 * @see java.util.StringTokenizer
 */
public class POSIXCommandLine extends CommandLine {
    /** The original words provided. */
    private String[] orig;

    /** The words blown up into an array of array of characters. */
    private char[][] args;

    /** Have all the options been consumed? */
    private boolean allUsed;

    /** Have we been asked for any single-character (boolean) options? */
    private boolean singles;

    /** Has the whole command line been eaten (via <code>getOperands</code>)? */
    private boolean usedUp;

    /** The list of known options for the usage message. */
    private ArrayList options;

    /** The program name (if specified). */
    private String prog;

    // I wouldn't do this stateful stuff if I could return more than one
    // value from a method -- it didn't seem worth creating a new object
    // to hold the necessary values on each call to findOpt().  So I've
    // ensured that only one parsing method can be executing at a time
    // and "returned" values via this side effect.  YUCK!!!!
    private int str;		// found in which string
    private int pos;		// found at what position
    private char opt;		// which char was found

    /** Marks a character as being used up in the options. */
    private static final char	USED = '\u0000';

    /**
     * Create a new <code>CommandLine</code> object that will return
     * specified options, arguments, and operands.
     */
    public POSIXCommandLine(String[] args) {
	this(null, args);
    }

    /**
     * Create a new <code>CommandLine</code> object that will return
     * specified options, arguments, and operands.  The <code>prog</code>
     * parameter is the program name.
     */
    public POSIXCommandLine(String prog, String[] args) {
	orig = args;
	this.args = new char[args.length][];
	this.prog = prog;
	for (int i = 0; i < args.length; i++)
	    this.args[i] = args[i].toCharArray();
	options = new ArrayList();
    }

    /**
     * Used to store known option types so we can generate a usage message.
     */
    private static class Opt {
	/** The option. */
	char	opt;

	/** The argument type. */
	String	argType;

	/** Can be specified multiple times. */
	boolean multi;

	Opt(char opt, String argType) {
	    this.opt = opt;
	    this.argType = argType;
	}
    }

    /**
     * Return <code>true</code> if the given option is specified on the
     * command line.
     */
    public synchronized boolean getBoolean(char opt) {
	addOpt(opt, null);
	singles = true;
	boolean retval = false;
	while (findOpt(opt))
	    retval = true;
	return retval;
    }

    /**
     * Return the argument for the given option.  This is a workhorse
     * routine shared by all the methods that get options with arguments.
     */
    private String getArgument(char opt)
	throws BadInvocationException
    {
	assertNoSingles();
	if (findOpt(opt))
	    return optArg();
	return null;
    }

    /**
     * Return the argument of the given string option from the command
     * line.  If the option is not specified, return
     * <code>defaultValue</code>.
     */
    public synchronized String getString(char opt, String defaultValue)
	throws BadInvocationException
    {
	addOpt(opt, "str");
	return parseString(getArgument(opt), defaultValue);
    }

    /**
     * Return the argument of the given <code>int</code> option from
     * the command line.  If the option is not specified, return
     * <code>defaultValue</code>.
     *
     * @see CommandLine#parseInt
     */
    public synchronized int getInt(char opt, int defaultValue)
	throws BadInvocationException, NumberFormatException
    {
	addOpt(opt, "int");
	return parseInt(getArgument(opt), defaultValue);
    }

    /**
     * Return the argument of the given <code>long</code> option from
     * the command line.  If the option is not specified, return
     * <code>defaultValue</code>.
     *
     * @see CommandLine#parseLong
     */
    public synchronized long getLong(char opt, long defaultValue)
	throws BadInvocationException, NumberFormatException
    {
	addOpt(opt, "long");
	return parseLong(getArgument(opt), defaultValue);
    }

    /**
     * Return the value of the given <code>double</code> from the command line.
     * If the option is not specified, return <code>defaultValue</code>.
     *
     * @see CommandLine#parseDouble
     */
    public synchronized double getDouble(char opt, double defaultValue)
	throws BadInvocationException, NumberFormatException
    {
	addOpt(opt, "val");
	return parseDouble(getArgument(opt), defaultValue);
    }

    /**
     * Return a <code>Writer</code> that is the result of creating a new
     * <code>FileWriter</code> object for the file named by the given
     * option.
     * If the option is not specified, return <code>defaultValue</code>.
     *
     * @see CommandLine#parseWriter(java.lang.String,java.io.Writer)
     */
    public synchronized Writer getWriter(char opt, Writer defaultValue)
	throws IOException, BadInvocationException
    {
	addOpt(opt, "file");
	return parseWriter(getArgument(opt), defaultValue);
    }

    /**
     * Return a <code>Writer</code> that is the result of creating a new
     * <code>FileWriter</code> object for the file named by the given
     * option.
     * If the option is not specified, the string <code>path</code> is used
     * as the file name.
     *
     * @see CommandLine#parseWriter(java.lang.String,java.lang.String)
     */
    public synchronized Writer getWriter(char opt, String path)
	throws IOException, BadInvocationException
    {
	addOpt(opt, "file");
	return parseWriter(getArgument(opt), path);
    }

    /**
     * Return a <code>Reader</code> that is the result of creating a new
     * <code>FileReader</code> object for the file named by the given
     * option.
     * If the option is not specified, return <code>defaultValue</code>.
     *
     * @see CommandLine#parseReader(java.lang.String,java.io.Reader)
     */
    public synchronized Reader getReader(char opt, Reader defaultValue)
	throws IOException, BadInvocationException
    {
	addOpt(opt, "file");
	return parseReader(getArgument(opt), defaultValue);
    }

    /**
     * Return a <code>Reader</code> that is the result of creating a new
     * <code>FileReader</code> object for the file named by the given
     * option.
     * If the option is not specified, the string <code>path</code> is used
     * as the file name.
     *
     * @see CommandLine#parseReader(java.lang.String,java.lang.String)
     */
    public synchronized Reader getReader(char opt, String path)
	throws IOException, BadInvocationException
    {
	addOpt(opt, "file");
	return parseReader(getArgument(opt), path);
    }

    /**
     * Return a <code>OutputStream</code> that is the result of creating a new
     * <code>FileOutputStream</code> object for the file named by the given
     * option.  If the argument is <code>-</code> then
     * <code>System.out</code> is returned.
     * If the option is not specified, return <code>defaultValue</code>.
     *
     * @see CommandLine#parseOutputStream(java.lang.String,java.io.OutputStream)
     */
    public synchronized OutputStream
	getOutputStream(char opt, OutputStream defaultValue)
	throws IOException, BadInvocationException
    {
	addOpt(opt, "file");
	return parseOutputStream(getArgument(opt), defaultValue);
    }

    /**
     * Return a <code>InputStream</code> that is the result of creating a new
     * <code>FileInputStream</code> object for the file named by the given
     * option.
     * If the option is not specified, the string <code>path</code> is used
     * as the file name.
     *
     * @see CommandLine#parseOutputStream(java.lang.String,java.lang.String)
     */
    public synchronized OutputStream getOutputStream(char opt, String path)
	throws IOException, BadInvocationException
    {
	addOpt(opt, "file");
	return parseOutputStream(getArgument(opt), path);
    }

    /**
     * Return a <code>InputStream</code> that is the result of creating a new
     * <code>FileInputStream</code> object for the file named by the given
     * option.
     * If the option is not specified, return <code>defaultValue</code>.
     *
     * @see CommandLine#parseInputStream(java.lang.String,java.io.InputStream)
     */
    public synchronized InputStream
	getInputStream(char opt, InputStream defaultValue)
	throws IOException, BadInvocationException
    {
	addOpt(opt, "file");
	return parseInputStream(getArgument(opt), defaultValue);
    }

    /**
     * Return a <code>InputStream</code> that is the result of creating a new
     * <code>FileInputStream</code> object for the file named by the given
     * option.
     * If the option is not specified, the string <code>path</code> is used
     * as the file name.
     *
     * @see CommandLine#parseInputStream(java.lang.String,java.lang.String)
     */
    public synchronized InputStream getInputStream(char opt, String path)
	throws IOException, BadInvocationException
    {
	addOpt(opt, "file");
	return parseInputStream(getArgument(opt), path);
    }

    /**
     * Return a <code>RandomAccessFile</code> that is the result of
     * creating a new <code>RandomAccessFile</code> object for the file
     * named by the given option, using the given <code>mode</code>.
     * If the option is not specified, return <code>defaultValue</code>.
     *
     * @see CommandLine#parseRandomAccessFile(java.lang.String,java.io.RandomAccessFile,java.lang.String)
     */
    public synchronized RandomAccessFile
	getRandomAccessFile(char opt, RandomAccessFile defaultValue,
			    String mode)
	throws IOException, BadInvocationException
    {
	addOpt(opt, "file");
	return parseRandomAccessFile(getArgument(opt), defaultValue, mode);
    }

    /**
     * Return a <code>RandomAccessFile</code> that is the result of
     * creating a new <code>RandomAccessFile</code> object for the file
     * named by the given option, using the given <code>mode</code>.
     * If the option is not specified, the string <code>path</code> is used
     * as the file name.
     *
     * @see CommandLine#parseRandomAccessFile(java.lang.String,java.lang.String,java.lang.String)
     */
    public synchronized RandomAccessFile
	getRandomAccessFile(char opt, String path, String mode)
	throws IOException, BadInvocationException
    {
	addOpt(opt, "file");
	return parseRandomAccessFile(getArgument(opt), path, mode);
    }

    /**
     * Assert that no boolean options have yet been consumed.
     */
    private void assertNoSingles() {
	assertNotUsedUp();
	if (singles)
	    throw new ProgrammingException("opts with args must come first");
    }

    /**
     * Assert that the entire command line hasn't been consumed, that is,
     * that <code>getOperands</code> hasn't yet been called.
     */
    private void assertNotUsedUp() {
	if (usedUp)
	    throw new ProgrammingException("Command line used up");
    }

    /**
     * Find the given option somewhere in the command line.  If the
     * option is not found, return <code>false</code>.  Otherwise set
     * <code>str</code>, <code>pos</code>, and <code>opt</code> fields,
     * mark the option character as <code>USED</code>, and then return
       <code>true</code>.
     */
    private boolean findOpt(char opt) {
	if (allUsed)
	    return false;
	boolean seenUnused = false;
	for (int i = 0; i < args.length; i++) {
	    if (args[i][0] != '-')
		continue;
	    if (args[i][1] == '-')	// "--" ends the list
		break;
	    for (int j = 1; j < args[i].length; j++) {
		if (args[i][j] == opt) {
		    str = i;
		    pos = j;
		    this.opt = opt;
		    args[i][j] = USED;
		    return true;
		} else if (args[i][j] != USED) {
		    seenUnused = true;
		}
	    }
	}
	if (!seenUnused)
	    allUsed = true;
	return false;
    }

    /**
     * Return the current option's argument, marking its characters
     * as <code>USED</code>.
     *
     * @exception BadInvocationException No argument is given.
     */
    private String optArg() throws BadInvocationException {
	if (pos + 1 < args[str].length) {
	    for (int i = pos + 1; i < args[str].length; i++)
		args[str][i] = USED;
	    return orig[str].substring(pos + 1);
	} else {
	    if (str >= orig.length)
		throw new BadInvocationException(new Character(opt));
	    for (int i = 0; i < args[str + 1].length; i++)
		args[str + 1][i] = USED;
	    return orig[str + 1];
	}
    }

    /**
     * Return the command line operands that come after the options.
     * This checks to make sure that all specified options have been
     * consumed -- any options remaining at this point are assumed to
     * be unknown options.  If no operands remain, an empty array is
     * returned.
     * <p>
     * This is also where <code>-?</code> is handled.  If the user
     * specifies <code>-?</code> then the method <code>usage</code> is
     * invoked and <code>HelpOnlyException</code> is thrown.  The
     * program is expected to catch this exception and simply exit
     * successfully.
     *
     * @see #usage
     */
    public String[] getOperands()
	throws BadInvocationException, HelpOnlyException
    {
	if (getBoolean('?')) {
	    usage();
	    throw new HelpOnlyException();
	}

	StringBuffer unknown = new StringBuffer();
	int a;
	for (a = 0; a < args.length; a++) {
	    if (args[a][0] == USED)	// skip used parameters
		continue;
	    if (args[a][0] != '-')	// first non-option argument
		break;
	    if (args[a][1] == '-') {	// "--" ends things
		a++;			// skip the "--"
		break;
	    }
	    for (int j = 1; j < args[a].length; j++) {
		if (args[a][j] != USED)
		    unknown.append(args[a][j]);
	    }
	}
	if (unknown.length() != 0) {
	    throw new BadInvocationException("unknown option" +
		(unknown.length() > 1 ? "s" : "") + ": " + unknown);
	}

	String[] remains = new String[args.length - a];
	for (int i = a; i < args.length; i++)
	    remains[i - a] = orig[i];
	usedUp = true;
	return remains;
    }

    /**
     * Add the given option of the given type to the list of known options.
     * '?' is handled separately in <code>getOperands</code>.
     *
     * @see #getOperands
     * @see #usage
     */
    private void addOpt(char opt, String optType) {
	// ensure this is a new, not a redundant, option.
	Iterator it = options.iterator();
	while (it.hasNext()) {
	    Opt o = (Opt) it.next();
	    if (o.opt == opt) {
		o.multi = true;
		return;		// already known
	    }
	}

	if (opt != '?')
	    options.add(new Opt(opt, optType));
    }

    /**
     * Print out a summary of the commands usage, inferred from the
     * requested options.  You can override this to provide a more
     * specific summary.  This implementation is only valid after all
     * known options have been requested and <code>getOperands</code>
     * has been (or is being) called.  Adds <code>...</code> for
     * operands.
     *
     * @see #getOperands
     */
    public void usage() {
	if (prog != null) {
	    System.out.print(prog);
	    System.out.print(' ');
	}
	System.out.print("[-?]");

	// print out boolean options
	boolean seenBool = false;
	Iterator it = options.iterator();
	while (it.hasNext()) {
	    Opt o = (Opt) it.next();
	    if (o.argType == null) {
		if (!seenBool)
		    System.out.print(" [-");
		System.out.print(o.opt);
		seenBool = true;
	    }
	}
	if (seenBool)
	    System.out.print(']');

	// print out options that take arguments
	it = options.iterator();
	while (it.hasNext()) {
	    Opt o = (Opt) it.next();
	    if (o.argType != null) {
		System.out.print(" [-");
		System.out.print(o.opt);
		System.out.print(' ');
		System.out.print(o.argType);
		System.out.print("]");
		if (o.multi)
		    System.out.print("...");
	    }
	}

	// assume other arguments
	System.out.println(" ...");
    }
}
