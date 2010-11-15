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
import java.util.BitSet;

/**
 * This class parses a command line that uses multi-character options,
 * such as <code>-verbose</code> or <code>-help</code>.
 * <p>
 * To use <code>MultiCommandLine</code>, create a <code>MultiCommandLine</code>
 * object with the array of strings you wish to parse (typically the
 * array passed to the utility's <code>main</code> method), and then
 * consume options from it, providing default values in case the option
 * is not specified by the user.  When you have consumed all the
 * options, you invoke the <code>MultiCommandLine</code> object's
 * <code>getOperands</code> method to return the remaining operands on
 * the command line.  If ``<code>--</code>'' is specified it is neither an
 * option nor an operand, just a separator between the two lists.  The
 * <code>CommandLine.BadInvocationException</code> is used to signal
 * errors in the construction of the strings, that is, a user error,
 * such as specifying a option that takes an argument but forgetting to
 * provide that argument.  See the documentation for
 * <code>MultiCommandLine.main</code> for an example.
 * <p>
 * You must call <code>getOperands</code> for proper behavior, even if
 * you do not use any operands in your command.  <code>getOperands</code> 
 * checks for several user errors, including unknown options.  If you
 * do not expect to use operands, you should check the return value of
 * <code>getOperands</code> and complain if any are specified.
 * <p>
 * No options
 * can be consumed after <code>getOperands</code> is invoked.  Each
 * option may be used only once.  Failure to follow these
 * rule is a programmer error that will result in a
 * <code>CommandLine.ProgrammingException</code>.
 * <p>
 * <code>MultiCommandLine</code> provides you several methods to get input
 * streams from the command line.  If these do not suffice for your
 * particular needs, you can get the argument as a <code>String</code>
 * and do your own processing.
 *
 * @author Sun Microsystems, Inc.
 *
 * @see java.util.StringTokenizer
 */
public class MultiCommandLine extends CommandLine {
    /** The args provided. */
    private String[] args;

    /** which ones have been used? */
    private BitSet used;

    /** Have all the options been consumed? */
    private boolean allUsed;

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
    private String opt;		// which String was found

    /**
     * Create a new <code>MultiCommandLine</code> object that will
     * return specified options, arguments, and operands.
     */
    public MultiCommandLine(String[] args) {
	this(null, args);
    }

    /**
     * Create a new <code>MultiCommandLine</code> object that will
     * return specified options, arguments, and operands.  The
     * <code>prog</code> parameter is the program name.
     */
    public MultiCommandLine(String prog, String[] args) {
	this.prog = prog;
	this.args = args;
	used = new BitSet(args.length);
	options = new ArrayList();
    }

    /**
     * Used to store known option types so we can generate a usage message.
     */
    private static class Opt {
	/** The option. */
	String	opt;

	/** The argument type. */
	String	argType;

	/** Option can be specified more than once. */
	boolean multi;

	Opt(String opt, String argType) {
	    this.opt = opt;
	    this.argType = argType;
	}
    }

    /**
     * Return <code>true</code> if the given option is specified on the
     * command line.
     */
    public synchronized boolean getBoolean(String opt) {
	addOpt(opt, null);
	boolean retval = false;
	while (findOpt(opt))
	    retval = true;
	return retval;
    }

    /**
     * Return the argument for the given option.  This is a workhorse
     * routine shared by all the methods that get options with arguments.
     */
    private String getArgument(String opt)
	throws BadInvocationException
    {
	if (findOpt(opt))
	    return optArg();
	return null;
    }

    /**
     * Return the argument of the given string option from the command
     * line.  If the option is not specified, return
     * <code>defaultValue</code>.
     */
    public synchronized String getString(String opt, String defaultValue)
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
    public synchronized int getInt(String opt, int defaultValue)
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
    public synchronized long getLong(String opt, long defaultValue)
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
    public synchronized double getDouble(String opt, double defaultValue)
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
    public synchronized Writer getWriter(String opt, Writer defaultValue)
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
    public synchronized Writer getWriter(String opt, String path)
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
    public synchronized Reader getReader(String opt, Reader defaultValue)
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
    public synchronized Reader getReader(String opt, String path)
	throws IOException, BadInvocationException
    {
	addOpt(opt, "file");
	return parseReader(getArgument(opt), path);
    }

    /**
     * Return a <code>OutputStream</code> that is the result of creating a new
     * <code>FileOutputStream</code> object for the file named by the given
     * option.
     * If the option is not specified, return <code>defaultValue</code>.
     *
     * @see CommandLine#parseOutputStream(java.lang.String,java.io.OutputStream)
     */
    public synchronized OutputStream
	getOutputStream(String opt, OutputStream defaultValue)
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
    public synchronized OutputStream getOutputStream(String opt, String path)
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
	getInputStream(String opt, InputStream defaultValue)
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
    public synchronized InputStream getInputStream(String opt, String path)
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
	getRandomAccessFile(String opt, RandomAccessFile defaultValue,
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
	getRandomAccessFile(String opt, String path, String mode)
	throws IOException, BadInvocationException
    {
	addOpt(opt, "file");
	return parseRandomAccessFile(getArgument(opt), path, mode);
    }

    /**
     * Find the given option somewhere in the command line.  If the
     * option is not found, return <code>false</code>.  Otherwise set
     * <code>str</code>, <code>pos</code>, and <code>opt</code> fields,
     * mark the option character as <code>USED</code>, and then return
       <code>true</code>.
     */
    private boolean findOpt(String opt) {
	if (allUsed)
	    return false;
	boolean seenUnused = false;
	for (int i = 0; i < args.length; i++) {
	    if (used.get(i))			// already consumed
		continue;
	    if (!args[i].startsWith("-"))	// not an option
		continue;
	    if (args[i].equals("--"))		// "--" ends the list
		break;
	    seenUnused = true;
	    if (args[i].length() - 1 == opt.length() &&
	        args[i].regionMatches(1, opt, 0, opt.length()))
	    {
		str = i;
		this.opt = opt;
		used.set(i);
		return true;
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
	if (str >= args.length)
	    throw new BadInvocationException(opt);
	used.set(str + 1);
	return args[str + 1];
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
	if (getBoolean("?") || getBoolean("help")) {
	    usage();
	    throw new HelpOnlyException();
	}

	StringBuffer unknown = new StringBuffer();
	int a;
	for (a = 0; a < args.length; a++) {
	    if (used.get(a))			// skip used parameters
		continue;
	    if (!args[a].startsWith("-"))	// first non-option argument
		break;
	    if (args[a].equals("--")) {		// "--" ends things
		a++;				// skip the "--"
		break;
	    }
	    unknown.append(' ').append(args[a]);
	}
	if (unknown.length() != 0) {
	    String ustr = unknown.toString();
	    throw new BadInvocationException("unknown option" +
		(ustr.indexOf(' ') > 0 ? "s" : "") + ":" + ustr);
	}

	String[] remains = new String[args.length - a];
	System.arraycopy(args, a, remains, 0, remains.length);
	usedUp = true;
	return remains;
    }

    /**
     * Add the given option of the given type to the list of known options.
     * "-?" and "-help" are handled separately in <code>getOperands</code>.
     *
     * @see #getOperands
     * @see #usage
     */
    private void addOpt(String opt, String optType) {
	// ensure this is a new, not a redundant, option.
	Iterator it = options.iterator();
	while (it.hasNext()) {
	    Opt o = (Opt) it.next();
	    if (o.opt.equals(opt)) {
		o.multi = true;
		return;		// already known
	    }
	}

	if (!isHelp(opt))
	    options.add(new Opt(opt, optType));
    }

    private static boolean isHelp(String opt) {
	return (opt.equals("?") || opt.equals("help"));
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

	// print out options take arguments
	Iterator it = options.iterator();
	while (it.hasNext()) {
	    Opt o = (Opt) it.next();
	    System.out.print(" [-");
	    System.out.print(o.opt);
	    if (o.argType != null) {
		System.out.print(' ');
		System.out.print(o.argType);
	    }
	    System.out.print("]");
	    if (o.multi)
		System.out.print("...");
	}

	// assume other arguments
	System.out.println(" ...");
    }
}
