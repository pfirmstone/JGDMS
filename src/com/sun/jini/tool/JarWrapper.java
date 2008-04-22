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

package com.sun.jini.tool;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.net.URI;
import java.net.URISyntaxException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.MissingResourceException;
import java.util.ResourceBundle;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.jar.Attributes;
import java.util.jar.Attributes.Name;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import java.util.logging.ConsoleHandler;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A tool for generating "wrapper" JAR files.  A wrapper JAR file contains a
 * <code>Class-Path</code> manifest attribute listing a group of JAR files to
 * be loaded from a common codebase.  It may also, depending on applicability
 * and selected options, contain a JAR index file, a preferred class list
 * and/or a <code>Main-Class</code> manifest entry for the grouped JAR files.
 * <p>
 * The following items are discussed below:
 * <ul>
 *   <li> <a href="#applicability">Applicability</a>
 *   <li> <a href="#running">Using the Tool</a>
 *   <li> <a href="#logging">Logging</a>
 *   <li> {@linkplain #main Processing Options}
 * </ul>
 * <p>
 * <a name="applicability"><h3>Applicability</h3></a>
 * <p>
 * The <code>JarWrapper</code> tool is applicable in the following deployment
 * situations, which may overlap:
 * <ul>
 *   <li> If a codebase contains multiple JAR files which declare preferred
 *	  resources, <code>JarWrapper</code> can be used to produce a wrapper
 *	  JAR file with a combined preferred list.  Preferred resources are
 *	  described in the documentation for the {@link net.jini.loader.pref}
 *	  package.
 *        <p>
 *   <li> If a codebase contains multiple JAR files and requires integrity
 *	  protection, <code>JarWrapper</code> can be used to produce a wrapper
 *	  JAR file with a <code>Class-Path</code> attribute that uses HTTPMD
 *	  URLs.  HTTPMD URLs are described in the documentation for the
 *	  {@link net.jini.url.httpmd} package.
 *        <p>
 *   <li> If an application or service packaged as an executable JAR file
 *	  refers to classes specified at deployment time (e.g., via a
 *	  {@link net.jini.config.Configuration Configuration}) which are not
 *	  present in the JAR file or its <code>Class-Path</code>,
 *	  <code>JarWrapper</code> can be used to produce a wrapper JAR file
 *	  which includes the extra classes in its <code>Class-Path</code> while
 *	  retaining the original <code>Main-Class</code> declaration; the
 *	  wrapper JAR file can then be executed in place of the original JAR
 *	  file.
 * </ul>
 * <p>
 * <a name="running"><h3>Using the Tool</h3></a>
 * <code>JarWrapper</code> can be run directly from the
 * {@linkplain #main command line} or can be invoked programmatically using the
 * {@link #wrap wrap} method.
 * <p>
 * To run the tool on UNIX platforms:
 * <blockquote><pre>
 * java -jar <var><b>install_dir</b></var>/lib/jarwrapper.jar <var><b>processing_options</b></var>
 * </pre></blockquote>
 * To run the tool on Microsoft Windows platforms:
 * <blockquote><pre>
 * java -jar <var><b>install_dir</b></var>\lib\jarwrapper.jar <var><b>processing_options</b></var>
 * </pre></blockquote>
 * <p>
 * A more specific example with options for running directly from a Unix command
 * line might be:
 * <blockquote><pre>
 * % java -jar <var><b>install_dir</b></var>/lib/jarwrapper.jar \
 *        -httpmd=SHA-1 wrapper.jar base_dir src1.jar src2.jar
 * </pre></blockquote>
 * where <var><b>install_dir</b></var> is the directory where the Apache
 * River release is installed. This command line would result in the creation
 * of a wrapper JAR file, <code>wrapper.jar</code>, in the current working
 * directory, whose contents would be based on the source JAR files
 * <code>src1.jar</code> and <code>src2.jar</code> (as well as any other JAR
 * files referenced transitively through their <code>Class-Path</code>
 * attributes or JAR indexes).  The paths for <code>src1.jar</code> and
 * <code>src2.jar</code>, as well as any transitively referenced JAR files,
 * would be resolved relative to the <code>base_dir</code> directory.  The
 * <code>Class-Path</code> attribute of <code>wrapper.jar</code> would use
 * HTTPMD URLs with SHA-1 digests.  If any of the HTTPMD URLs encountered is
 * found to be invalid and can not be resolved, the <code>JarWrapper</code>
 * operation will fail.
 * <p>
 * The equivalent programmatic invocation of <code>JarWrapper</code> would be:
 * <blockquote><pre>
 * JarWrapper.wrap("wrapper.jar", "base_dir", new String[]{ "src1.jar", "src2.jar" }, "SHA-1", true, "manifest.mf" );
 * </pre></blockquote>
 *
 * <p>
 * <a name="logging"><h3>Logging</h3></a>
 * <p>
 * <code>JarWrapper</code> uses the {@link Logger} named
 * <code>com.sun.jini.tool.JarWrapper</code> to log information at the
 * following logging levels:
 * <p>
 * <table border="1" cellpadding="5"
 * 	  summary="Describes logging performed by JarWrapper at different
 *		   logging levels">
 * <caption halign="center" valign="top"><b><code>
 *    com.sun.jini.tool.JarWrapper</code></b></caption>
 *
 *   <tr> <th scope="col"> Level   <th scope="col"> Description </tr>
 *   <tr>
 *     <td> {@link Level#WARNING WARNING}
 *     <td> Generated JAR index entries that do not end in <code>".jar"</code>
 *   </tr>
 *   <tr>
 *     <td> {@link Level#FINE FINE}
 *     <td> Names of processed source JAR files and output wrapper JAR file
 *   </tr>
 *   <tr>
 *     <td> {@link Level#FINER FINER}
 *     <td> Processing of <code>Main-Class</code> and <code>Class-Path</code>
 *          attributes, and presence of preferred lists and JAR indexes
 *   </tr>
 *   <tr>
 *     <td> {@link Level#FINEST FINEST}
 *     <td> Processing and compilation of preferred lists and JAR indexes
 *   </tr>
 * </table>
 *
 * @author Sun Microsystems, Inc.
 * @since 2.0
 */
public class JarWrapper {

    private static ResourceBundle resources;
    private static final Object resourcesLock = new Object();
    private static final Logger logger =
	Logger.getLogger(JarWrapper.class.getName());

    private final File destJar;
    /**
     * Base directory, <code>null</code> in case the JAR files are specified as
     * absolute files, the so called flatten classpath option.
     */
    private final File baseDir;
    private final SourceJarURL[] srcJars;
    private final Manifest manifest;
    private final MessageDigest digest;
    private final JarIndexWriter indexWriter;
    private final PreferredListWriter prefWriter;
    private final StringBuffer classPath = new StringBuffer();
    private String mainClass = null;
    private final Set seenJars = new HashSet();

    static private final String DEFAULT_HTTPMD_ALGORITHM = "SHA-1";

    /**
     * Initializes JarWrapper based on the given values.
     */
    private JarWrapper(String destJar,
		       String baseDir,
		       String[] srcJars,
		       String httpmdAlg,
		       boolean index,
		       Manifest mf,
		       List apiClasses)
    {
	this.destJar = new File(destJar);
	if (this.destJar.exists()) {
	    throw new LocalizedIllegalArgumentException(
		"jarwrapper.fileexists", destJar);
	}
	if (baseDir != null) {
	this.baseDir = new File(baseDir);
	if (!this.baseDir.isDirectory()) {
	    throw new LocalizedIllegalArgumentException(
		"jarwrapper.invalidbasedir", baseDir);
	}
	}
	else {
	    this.baseDir = null;
	}
	this.srcJars = new SourceJarURL[srcJars.length];
	for (int i = 0; i < srcJars.length; i++) {
	    try {
		SourceJarURL url;
		if (baseDir == null) {
		    File file = new File(srcJars[i]);
		    url = new SourceJarURL(file.getName(),
			file.getParentFile());
		}
		else {
		    url = new SourceJarURL(srcJars[i]);
		}
		if (url.algorithm != null) {
		    throw new LocalizedIllegalArgumentException(
			"jarwrapper.urlhasdigest", url);
		}
		this.srcJars[i] = url;
	    } catch (LocalizedIOException e) {
		throw new LocalizedIllegalArgumentException(e);
	    } catch (IOException e) {
		throw (IllegalArgumentException)
		    new IllegalArgumentException(e.getMessage()).initCause(e);
	    }
	}
	if (httpmdAlg != null) {
	    try {
		digest = MessageDigest.getInstance(httpmdAlg);
	    } catch (NoSuchAlgorithmException e) {
		throw (IllegalArgumentException)
		    new LocalizedIllegalArgumentException(
			"jarwrapper.invalidhttpmdalg", httpmdAlg).initCause(e);
	    }
	} else {
	    digest = null;
	}
        manifest = mf != null ? new Manifest(mf) : new Manifest();
	indexWriter = index ? new JarIndexWriter() : null;
	List classes = new ArrayList();
	if (apiClasses != null) {
	    for (Iterator classNames = apiClasses.iterator();
		    classNames.hasNext();) {
		String className = (String) classNames.next();
		if (className != null) {
		    classes.add(className.replace('.', '/') + ".class");
		}
	    }
	}
	prefWriter = new PreferredListWriter(classes);
    }

    /**
     * Generates a wrapper JAR file for the specified JAR files.  The command
     * line arguments are:
     * <pre>
     * [ <var>options</var> ] <var>dest-jar</var> <var>base-dir</var> <var>src-jar</var> [ <var>src-jar</var> ...]
     * </pre>
     * The <var>dest-jar</var> argument specifies the name of the wrapper JAR
     * file to generate.  The <var>base-dir</var> argument specifies the base
     * directory from which to locate source JAR files to wrap.  The
     * <var>src-jar</var> arguments are non-absolute URLs to "top-level" source
     * JAR files relative to <var>base-dir</var>; they also constitute the
     * basis of the <code>Class-Path</code> attribute included in the generated
     * wrapper JAR file.  JAR files not present in the command line but
     * indirectly referenced via JAR index or <code>Class-Path</code> entries
     * in source JAR files will themselves be used as source JAR files, and
     * will appear alongside the top-level source JAR files in the
     * <code>Class-Path</code> attribute of the wrapper JAR file in depth-first
     * order, with JAR index references appearing before
     * <code>Class-Path</code> references.  This utility does not modify any
     * source JAR files.
     * <p>
     * If any of the top-level source JAR files contain preferred resources (as
     * indicated by a preferred list in the JAR file), then a preferred list
     * describing resource preferences across all source JAR files will be
     * included in the wrapper JAR file.  The preferred list of a top-level
     * source JAR file is interpreted as applying to that JAR file along with
     * all JAR files transitively referenced by it through JAR index or
     * <code>Class-Path</code> entries, excluding JAR files that have already
     * been encountered in the processing of preceding top-level JAR files.  If
     * a given top-level source JAR file does not contain a preferred list,
     * then all resources contained in it and its transitively referenced JAR
     * files (again, excluding those previously encountered) are considered not
     * preferred.  Preferred lists are described further in the documentation
     * for {@link net.jini.loader.pref.PreferredClassLoader
     * PreferredClassLoader}.
     * <p>
     * If any of the top-level source JAR files declare a
     * <code>Main-Class</code> manifest entry, then the wrapper JAR file will
     * include a <code>Main-Class</code> manifest entry whose value is that of
     * the first top-level source JAR file listed on the command line which
     * defines a <code>Main-Class</code> entry.
     * <p>
     * Note that attribute values generated by this utility, such as those for
     * the <code>Class-Path</code> and <code>Main-Class</code> attributes
     * described above, do not take precedence over values for the same
     * attributes contained in a manifest file explicitly specified using the
     * <code>-manifest</code> option (described below).
     * <p>
     * Supported options for this tool include:
     * <p>
     * <dl>
     *   <dt> <code>-verbose</code>
     *   <dd> Sets the level of the <code>com.sun.jini.tool.JarWrapper</code>
     * 	      logger to <code>Level.FINER</code>.
     *	      <p>
     *   <dt> <code>-httpmd[=algorithm]</code>
     *   <dd> Use (relative) HTTPMD URLs in the <code>Class-Path</code>
     *        attribute of the generated wrapper JAR file.  The default is to
     *        use HTTP URLs.  Digests for HTTPMD URLs are calculated using the
     *        given algorithm, or SHA-1 if none is specified.
     *	      <p>
     *   <dt> <code>-noindex</code>
     *   <dd> Do not include a JAR index in the generated wrapper JAR file.  The
     *	      default is to compile an index based on the contents of the
     *	      source JAR files.
     *	      <p>
     *   <dt> <code>-manifest=<I>file</I></code>
     *   <dd> Specifies a manifest file containing attribute values to include
     *        in the manifest file inside the generated wrapper JAR file.
     * 	      This allows enables users to  add additional metadata or 
     *        override JarWrapper's generated  values to customize the resulting 
     *        manifest.  The values contained in this optional file take 
     *        precedence over the generated content. This flag is conceptually 
     *        similar to the jar utilities <code>m</code> flag. In the current
     *        version there are four possible attributes that can be overridden
     *        in the target Manifest.  These are
     *        <code>Name.MANIFEST_VERSION</code>,
     *        <code>Name("Created-By")</code>, <code>Name.CLASS_PATH</code> and
     *        <code>Name.MAIN_CLASS</code>.  Any additonal attributes beyond
     *        these four will be appended to the manifest attribute list and
     *        will appear in the resultant <code>MANIFEST.MF</code> file.
     * </dl>
     */
    public static void main(String[] args) {
	String destJar;
	String baseDir;
	String[] srcJars;
	String httpmdAlg = null;
	boolean index = true;
        Manifest mf = null;

	int i = 0;
	while (i < args.length && args[i].startsWith("-")) {
	    String s = args[i++];
	    if (s.equals("-help")) {
		System.err.println(localize("jarwrapper.usage"));
		System.exit(0);
	    } else if (s.equals("-verbose")) {
		setLoggingLevel(Level.FINER);
	    } else if (s.equals("-debug")) {
		setLoggingLevel(Level.ALL);
	    } else if (s.equals("-httpmd") || s.startsWith("-httpmd=")) {
		if (httpmdAlg != null) {
		    System.err.println(localize("jarwrapper.multiplehttpmd"));
		    System.err.println(localize("jarwrapper.usage"));
		    System.exit(1);
		}
		int split = s.indexOf('=');
		httpmdAlg = (split != -1) ? 
                    s.substring(split + 1) : DEFAULT_HTTPMD_ALGORITHM;
            } else if (s.startsWith("-manifest=")) {
                int split = s.indexOf('=');
                String fileName = s.substring(split + 1);
                try {
                    mf = retrieveManifest(fileName);
                } catch (IOException ioe) {
		    System.err.println(localize("jarwrapper.badmanifest", s));
	            System.exit(1);
                }
	    } else if (s.equals("-noindex")) {
		index = false;
	    } else {
		System.err.println(localize("jarwrapper.badoption", s));
		System.err.println(localize("jarwrapper.usage"));
		System.exit(1);
	    }
	}
	if (args.length - i < 3) {
	    System.err.println(localize("jarwrapper.insufficientargs"));
	    System.err.println(localize("jarwrapper.usage"));
	    System.exit(1);
	}
	destJar = args[i++];
	baseDir = args[i++];
	srcJars = new String[args.length - i];
	System.arraycopy(args, i, srcJars, 0, srcJars.length);

	try {
	    wrap(destJar, baseDir, srcJars, httpmdAlg, index, mf);
	} catch (Throwable t) {
	    if (t instanceof LocalizedIllegalArgumentException ||
		t instanceof LocalizedIOException)
	    {
		System.err.println(t.getMessage());
	    } else {
		System.err.println(localize("jarwrapper.fatalexception"));
		t.printStackTrace();
	    }
	    System.exit(1);
	}
    }

    /**
     * Invokes {@link #wrap(String, String, String[], String, boolean, Manifest)
     * wrap} with the provided values and a <code>null</code> manifest.
     *
     * @param destJar name of the wrapper JAR file to generate
     * @param baseDir base directory from which to locate source JAR
     * files to wrap
     * @param srcJars list of top-level source JAR files to process
     * @param httpmdAlg name of algorithm to use for generating HTTPMD URLs, or
     * <code>null</code> if plain HTTP URLs should be used
     * @param index if <code>true</code>, generate a JAR index; if
     * <code>false</code>, do not generate one
     * @throws IOException if an I/O error occurs while processing source JAR
     * files or generating the wrapper JAR file
     * @throws IllegalArgumentException if the provided values are invalid
     * @throws NullPointerException if <code>destJar</code>,
     * <code>baseDir</code>, <code>srcJars</code>, or any element of
     * <code>srcJars</code> is <code>null</code>
     */
    public static void wrap(String destJar,
			    String baseDir,
			    String[] srcJars,
			    String httpmdAlg,
			    boolean index)
	throws IOException
    {
	wrap(destJar, baseDir, srcJars, httpmdAlg, index, null);
    }

    /**
     * Generates a wrapper JAR file based on the provided values in the same
     * manner as described in the documentation for {@link #main}.  The only
     * difference between this method and <code>main</code> is that it receives
     * its values as explicit arguments instead of in a command line, and
     * indicates failure by throwing an exception.
     *
     * @param destJar name of the wrapper JAR file to generate
     * @param baseDir base directory from which to locate source JAR
     * files to wrap
     * @param srcJars list of top-level source JAR files to process
     * @param httpmdAlg name of algorithm to use for generating HTTPMD URLs, or
     * <code>null</code> if plain HTTP URLs should be used
     * @param index if <code>true</code>, generate a JAR index; if
     * <code>false</code>, do not generate one
     * @param mf manifest containing values to include in the manifest file 
     * of the generated wrapper JAR file
     * 
     * @throws IOException if an I/O error occurs while processing source JAR
     * files or generating the wrapper JAR file
     * @throws IllegalArgumentException if the provided values are invalid
     * @throws NullPointerException if <code>destJar</code>,
     * <code>baseDir</code>, <code>srcJars</code>, or any element of
     * <code>srcJars</code> is <code>null</code>
     * @since 2.1
     */
    public static void wrap(String destJar,
                            String baseDir,
                            String[] srcJars,
                            String httpmdAlg,
                            boolean index, 
                            Manifest mf)
        throws IOException
    {
	wrap(destJar, baseDir, srcJars, httpmdAlg, index, mf, null);
    }

    /**
     * Generates a wrapper JAR file based on the provided values in the same
     * manner as described in the documentation for {@link #main}.  The only
     * difference between this method and <code>main</code> is that it receives
     * its values as explicit arguments instead of in a command line, and
     * indicates failure by throwing an exception.
     *
     * @param destJar name of the wrapper JAR file to generate
     * @param baseDir base directory from which to locate source JAR
     * files to wrap
     * @param srcJars list of top-level source JAR files to process
     * @param httpmdAlg name of algorithm to use for generating HTTPMD URLs, or
     * <code>null</code> if plain HTTP URLs should be used
     * @param index if <code>true</code>, generate a JAR index; if
     * <code>false</code>, do not generate one
     * @param mf manifest containing values to include in the manifest file
     * of the generated wrapper JAR file
     * @param apiClasses list of binary class names (type <code>String</code>)
     * that must be considered API classes in case a preferences conflict
     * arises during wrapping of the JAR files, or <code>null</code> in case
     * no such list is available
     *
     * @throws IOException if an I/O error occurs while processing source JAR
     * files or generating the wrapper JAR file
     * @throws IllegalArgumentException if the provided values are invalid
     * @throws NullPointerException if <code>destJar</code>,
     * <code>baseDir</code>, <code>srcJars</code>, or any element of
     * <code>srcJars</code> is <code>null</code>
     */
    public static void wrap(String destJar,
                            String baseDir,
                            String[] srcJars,
                            String httpmdAlg,
                            boolean index,
                            Manifest mf,
                            List apiClasses)
        throws IOException
    {
        new JarWrapper(destJar, baseDir, srcJars, httpmdAlg, index, mf,
	    apiClasses).wrap();
    }

    /**
     * Generates a wrapper JAR file based on the provided values in the same
     * manner as described in the documentation for {@link #main}.
     * <p>
     * The difference between this method and the 6 and 7-arg <code>wrap</code>
     * method is that the source JAR files must be specified by an absolute path
     * and that for processing the classpath will be flattened, i.e. each source
     * JAR file will be considered as relative to its parent directory (that
     * will serve as a virtual base directory) for the assembly of the
     * <code>Class-Path</code> entry.
     *
     * @param destJar name of the wrapper JAR file to generate
     * @param srcJars list of top-level source JAR files to process, must be
     * absolute paths
     * @param httpmdAlg name of algorithm to use for generating HTTPMD URLs, or
     * <code>null</code> if plain HTTP URLs should be used
     * @param index if <code>true</code>, generate a JAR index; if
     * <code>false</code>, do not generate one
     * @param mf manifest containing values to include in the manifest file
     * of the generated wrapper JAR file
     * @param apiClasses list of binary class names (type <code>String</code>)
     * that must be considered API classes in case a preferences conflict
     * arises during wrapping of the JAR files, or <code>null</code> in case
     * no such list is available
     *
     * @throws IOException if an I/O error occurs while processing source JAR
     * files or generating the wrapper JAR file
     * @throws IllegalArgumentException if the provided values are invalid
     * @throws NullPointerException if <code>destJar</code>,
     * <code>srcJars</code>, or any element of <code>srcJars</code> is
     * <code>null</code>
     */
    public static void wrap(String destJar,
                            String[] srcJars,
                            String httpmdAlg,
                            boolean index,
                            Manifest mf,
                            List apiClasses)
        throws IOException
    {
        new JarWrapper(destJar, null, srcJars, httpmdAlg, index, mf,
	    apiClasses).wrap();
    }

    /**
     * Processes source JAR files and outputs wrapper JAR file.
     */
    private void wrap() throws IOException {
	for (int i = 0; i < srcJars.length; i++) {
	    process(srcJars[i], null);
	}
	outputWrapperJar();
    }

    /**
     * Processes source JAR file indicated by the given URL, determining
     * preferred resources using the provided preferred list reader.  If the
     * preferred list reader is null, then the URL is for a top-level source
     * JAR file, in which case the preferred list of the JAR file should be
     * read, if the JAR file has not already been processed.
     */
    private void process(SourceJarURL url, PreferredListReader prefReader)
	throws IOException
    {
	File file = baseDir == null ? url.toFile() : url.toFile(baseDir);
	boolean seen = seenJars.contains(file);
	boolean checkMainClass = mainClass == null && prefReader == null;
	if (seen && !checkMainClass) {
	    return;
	}

	if (logger.isLoggable(Level.FINE)) {
	    logger.log(Level.FINE, "processing {0}", new Object[]{ file });
	}
	if (!file.exists()) {
	    throw new LocalizedIOException("jarwrapper.filenotfound", file);
	}
	JarFile jar = new JarFile(file, false);

	if (checkMainClass) {
	    mainClass = getMainClass(jar);
	}
	if (!seen) {
	    seenJars.add(file);

	    if (digest != null) {
		url = new SourceJarURL(
		    url.path,
		    digest.getAlgorithm(),
		    getDigestString(digest, file),
		    null);
	    }
	    if (classPath.length() > 0) {
		classPath.append(' ');
	    }
	    classPath.append(url);

	    if (indexWriter != null) {
		indexWriter.addEntries(jar, url);
	    }
	    if (prefReader == null) {
		prefReader = new PreferredListReader(jar);
	    }
	    prefWriter.addEntries(jar, prefReader);

	    List l = new ArrayList();
	    l.addAll(new JarIndexReader(jar).getJars());
	    l.addAll(getClassPath(jar));
	    for (Iterator i = l.iterator(); i.hasNext(); ) {
		SourceJarURL u = (SourceJarURL) i.next();
		u = url.resolve(new SourceJarURL(u.path, null, null, null));
		process(u, prefReader);
	    }
	}
    }

    /**
     * Returns URLs contained in the Class-Path attribute (if any) of the given
     * JAR file, as a list of SourceJarURL instances.
     */
    private List getClassPath(JarFile jar) throws IOException {
	Manifest mf = jar.getManifest();
	if (mf == null) {
	    return Collections.EMPTY_LIST;
	}
	Attributes atts = mf.getMainAttributes();
	String cp = atts.getValue(Name.CLASS_PATH);
	if (cp == null) {
	    return Collections.EMPTY_LIST;
	}
	if (logger.isLoggable(Level.FINER)) {
	    logger.log(Level.FINER, "Class-Path: {0}", new Object[]{ cp });
	}
	List l = new ArrayList();
	for (StringTokenizer tok = new StringTokenizer(cp, " ");
	     tok.hasMoreTokens(); )
	{
	    SourceJarURL url = new SourceJarURL(tok.nextToken());
	    if (digest != null && url.algorithm == null) {
		throw new LocalizedIOException("jarwrapper.nonhttpmdurl", url);
	    }
	    l.add(url);
	}
	return l;
    }


    /**
     * Writes wrapper JAR file based on information from processed source JARs.
     */
    private void outputWrapperJar() throws IOException {
	if (logger.isLoggable(Level.FINE)) {
	    logger.log(Level.FINE, "writing {0}", new Object[]{ destJar });
	}
	Attributes atts = manifest.getMainAttributes();
        if (atts.get(Name.MANIFEST_VERSION) == null)
	    atts.put(Name.MANIFEST_VERSION, "1.0");
        Name creatorName = new Name("Created-By");
        if (atts.get(creatorName) == null )
	    atts.put(creatorName, JarWrapper.class.getName());
        if (atts.get(Name.CLASS_PATH) == null)
	    atts.put(Name.CLASS_PATH, classPath.toString());
        if ((atts.get(Name.MAIN_CLASS) == null) && (mainClass != null)) {
	    atts.put(Name.MAIN_CLASS, mainClass);
	}

	boolean completed = false;
	try {
	    JarOutputStream jout =
		new JarOutputStream(new FileOutputStream(destJar), manifest);
	    if (indexWriter != null) {
		indexWriter.write(jout);
	    }
	    prefWriter.write(jout);
	    jout.close();
	    completed = true;
	} finally {
	    if (!completed) {
		deleteWrapperJar();
	    }
	}
    }

    /**
     * Attempts to delete wrapper JAR file.
     */
    private void deleteWrapperJar() {
	try {
	    if (!destJar.delete() && logger.isLoggable(Level.WARNING)) {
		logger.log(
		    Level.WARNING,
		    "failed to delete {0}",
		    new Object[]{ destJar });
	    }
	} catch (Throwable t) {
	    logger.log(
		Level.WARNING, "exception deleting wrapper JAR file", t);
	}
    }

    /**
     * Returns the value of the Main-Class attribute of the given JAR file, or
     * null if none is present.
     */
    private static String getMainClass(JarFile jar) throws IOException {
	Manifest mf = jar.getManifest();
	if (mf == null) {
	    return null;
	}
	Attributes atts = mf.getMainAttributes();
	String mc = atts.getValue(Name.MAIN_CLASS);
	if (mc != null && logger.isLoggable(Level.FINER)) {
	    logger.log(Level.FINER, "Main-Class: {0}", new Object[]{ mc });
	}
	return mc;
    }

    /**
     * Returns a string representation of the message digest of the given file.
     */
    private static String getDigestString(MessageDigest digest, File file)
	throws IOException
    {
	FileInputStream fin = new FileInputStream(file);
	byte[] buf = new byte[2048];
	int n;
	while ((n = fin.read(buf)) >= 0) {
	    digest.update(buf, 0, n);
	}
	buf = digest.digest();
	fin.close();

	StringBuffer sb = new StringBuffer(buf.length * 2);
	for (int i = 0; i < buf.length; i++) {
	    byte b = buf[i];
	    sb.append(Character.forDigit((b >> 4) & 0xf, 16));
	    sb.append(Character.forDigit(b & 0xf, 16));
	}
	return sb.toString();
    }

    /**
     * Sets logging and console handler output level.
     */
    private static void setLoggingLevel(Level level) {
	logger.setLevel(level);
	for (Logger l = logger; l != null; l = l.getParent()) {
	    Handler[] handlers = l.getHandlers();
	    for (int i = 0; i < handlers.length; i++) {
		if (handlers[i] instanceof ConsoleHandler) {
		    handlers[i].setLevel(level);
		}
	    }
	    if (!l.getUseParentHandlers()) {
		break;
	    }
	}
    }

    /**
     * Returns localized message text corresponding to the given key string.
     */
    static String localize(String key) {
	return localize(key, new Object[0]);
    }

    /**
     * Returns localized message text corresponding to the given key string,
     * passing the provided value as an argument when formatting the message.
     */
    static String localize(String key, Object val) {
	return localize(key, new Object[] { val });
    }

    /**
     * Returns localized message text corresponding to the given key string,
     * passing the provided values as an argument when formatting the message.
     */
    static String localize(String key, Object[] vals) {
	String fmt = getResourceString(key);
	if (fmt == null) {
	    return "error: no text found in resource bundle for key: " + key;
	}
	return MessageFormat.format(fmt, vals);
    }

    /**
     * Returns localized format string, obtained from the resource bundle for
     * JarWrapper, that corresponds to the given key, or null if the resource
     * bundle does not contain a corresponding string.
     */
    private static String getResourceString(String key) {
	synchronized (resourcesLock) {
	    if (resources == null) {
		resources = ResourceBundle.getBundle(
		    "com.sun.jini.tool.resources.jarwrapper");
	    }
	}
	try {
	    return resources.getString(key);
	} catch (MissingResourceException e) {
	    return null;
	}
    }

    /**
     * Returns the Manifest object derived from a manifest file as specified 
     * on the command line. 
     */
    private static Manifest retrieveManifest(String fileName) 
        throws IOException 
    {
        FileInputStream fis = new FileInputStream(fileName);
        Manifest mf = new Manifest(fis);
        fis.close();
        return mf;
    }

    /**
     * IllegalArgumentException with a localized detail message.
     */
    private static class LocalizedIllegalArgumentException
	extends IllegalArgumentException
    {
	private static final long serialVersionUID = 0L;

	/**
	 * Creates exception with localized message text corresponding to the
	 * given key string, passing the provided value as an argument when
	 * formatting the message.
	 */
	LocalizedIllegalArgumentException(String key, Object val) {
	    super(localize(key, val));
	}

	/**
	 * Creates exception with the localized message text of the given
	 * cause.
	 */
	LocalizedIllegalArgumentException(LocalizedIOException cause) {
	    super(cause.getMessage());
	    initCause(cause);
	}
    }

    /**
     * IOException with a localized detail message.
     */
    private static class LocalizedIOException extends IOException {

	private static final long serialVersionUID = 0L;

	/**
	 * Creates exception with localized message text corresponding to the
	 * given key string, passing the provided value as an argument when
	 * formatting the message.
	 */
	LocalizedIOException(String key, Object val) {
	    super(localize(key, val));
	}

	/**
	 * Creates exception with localized message text corresponding to the
	 * given key string, passing the provided values as an argument when
	 * formatting the message.
	 */
	LocalizedIOException(String key, Object[] vals) {
	    super(localize(key, vals));
	}
    }

    /**
     * Represents URL to a source JAR file.  Source JAR URLs must be relative,
     * and may contain HTTPMD digests.
     */
    private static class SourceJarURL {

	private static final Pattern httpmdPattern =
	    Pattern.compile("(.*);(.+?)=(.+?)(?:,(.*))?$");

	/** raw URL string, including HTTPMD information (if any) */
	final String raw;
	/** URL path component, excluding any HTTPMD information */
	final String path;
	/** HTTPMD digest algorithm, or null if non-HTTPMD URL */
	final String algorithm;
	/** HTTPMD digest value, or null if non-HTTPMD URL */
	final String digest;
	/** HTTPMD digest comment, or null if non-HTTPMD URL */
	final String comment;
	/**
	 * Base directory associated with relative path for JAR file, only
	 * set in case the flatten classpath option is used.
	 */
	private File baseDir;

	/**
	 * Creates SourceJarURL based on given raw URL string.
	 */
	SourceJarURL(String raw) throws IOException {
	    try {
		this.raw = raw;
		Matcher m = httpmdPattern.matcher(raw);
		if (m.matches()) {
		    path = m.group(1);
		    algorithm = m.group(2);
		    digest = m.group(3);
		    comment = m.group(4);
		} else {
		    path = raw;
		    algorithm = null;
		    digest = null;
		    comment = null;
		}

		URI uri = new URI(path);
		if (uri.getScheme() != null) {
		    throw new LocalizedIOException(
			"jarwrapper.urlhasscheme", raw);
		} else if (uri.getAuthority() != null) {
		    throw new LocalizedIOException(
			"jarwrapper.urlhasauthority", raw);
		}
		String p = uri.getPath();
		if (p == null || p.length() == 0) {
		    throw new LocalizedIOException(
			"jarwrapper.urlemptypath", raw);
		} else if (p.startsWith("/")) {
		    throw new LocalizedIOException(
			"jarwrapper.urlabsolute", raw);
		}
	    } catch (URISyntaxException e) {
		throw (IOException) new LocalizedIOException(
		    "jarwrapper.invalidurlsyntax", raw).initCause(e);
	    }
	}

	/**
	 * Creates SourceJarURL based on given raw URL string that has an
	 * individual associated base directory.
	 */
	SourceJarURL(String raw, File baseDir) throws IOException {
	    this(raw);

	    this.baseDir = baseDir;
	}

	/**
	 * Creates SourceJarURL based on given components.
	 */
	SourceJarURL(String path, 
		     String algorithm, 
		     String digest,
		     String comment) 
	{
	    if (algorithm != null) {
		raw = path + ';' + algorithm + '=' + digest +
		      ((comment != null) ? ',' + comment : "");
	    } else {
		raw = path;
	    }
	    this.path = path;
	    this.algorithm = algorithm;
	    this.digest = digest;
	    this.comment = comment;
	}

	/**
	 * Resolves given URL relative to this URL.
	 */
	SourceJarURL resolve(SourceJarURL other) {
	    try {
		// hack around URI bug 4548698 by temporarily prepending slash
		URI uri = new URI('/' + path);
		String p = uri.resolve(other.path).getPath().substring(1);
		return new SourceJarURL(
		    p, other.algorithm, other.digest, other.comment);
	    } catch (URISyntaxException e) {
		throw new AssertionError(e);
	    }
	}

	/**
	 * Returns file represented by this URL.
	 */
	File toFile() {
	    return toFile(baseDir);
	}

	/**
	 * Returns file represented by this URL.
	 */
	File toFile(File base) {
	    try {
		String p = new URI(path).getPath();	// decode path
		return new File(base, p.replace('/', File.separatorChar));
	    } catch (URISyntaxException e) {
		throw (Error) new InternalError().initCause(e);
	    }
	}

	public boolean equals(Object obj) {
	    return obj instanceof SourceJarURL && 
		   raw.equals(((SourceJarURL) obj).raw);
	}

	public int hashCode() {
	    return raw.hashCode();
	}

	public String toString() {
	    return raw;
	}
    }

    /**
     * Parses JAR indexes.
     */
    private static class JarIndexReader {

	private static final Pattern headerPattern = 
	    Pattern.compile("^JarIndex-Version:\\s*(.*?)$");
	private static final Pattern versionPattern =
	    Pattern.compile("^1(\\.\\d+)*$");

	private final List jars;

	/**
	 * Parses the given JAR file's JAR index, if any.
	 */
	JarIndexReader(JarFile jar) throws IOException {
	    List l = new ArrayList();
	    jars = Collections.unmodifiableList(l);
	    JarEntry ent = jar.getJarEntry("META-INF/INDEX.LIST");
	    if (ent == null) {
		return;
	    }
	    logger.finer("reading JAR index");
	    BufferedReader r = new BufferedReader(
		new InputStreamReader(jar.getInputStream(ent), "UTF8"));

	    String s = r.readLine();
	    if (s == null) {
		throw new IOException("missing JAR index header");
	    }
	    s = s.trim();
	    Matcher m = headerPattern.matcher(s);
	    if (!m.matches()) {
		throw new IOException("illegal JAR index header: " + s);
	    }
	    s = m.group(1);
	    if (!versionPattern.matcher(s).matches()) {
		throw new IOException("unsupported JAR index version: " + s);
	    }

	    s = r.readLine();
	    if (s == null) {
		throw new IOException("truncated JAR index");
	    }
	    s = s.trim();
	    if (s.length() > 0) {
		throw new IOException(
		    "non-empty line after JAR index header: " + s);
	    }

	    while ((s = r.readLine()) != null) {
		SourceJarURL url = new SourceJarURL(s.trim());
		if (logger.isLoggable(Level.FINEST)) {
		    logger.log(
			Level.FINEST,
			"JAR index references {0}",
			new Object[]{ url });
		}
		l.add(url);
		do {
		    s = r.readLine();
		} while (s != null && s.trim().length() > 0);
	    }
	    if (l.isEmpty()) {
		throw new IOException("empty JAR index");
	    }
	}

	/**
	 * Returns list of SourceJarURLs representing the JAR files referenced
	 * in the JAR index.
	 */
	List getJars() {
	    return jars;
	}
    }

    /**
     * Assembles and writes JAR indexes.
     */
    private static class JarIndexWriter {

	private final List urls = new ArrayList();
	private final Map contentMap = new HashMap();

	JarIndexWriter() {
	}

	/**
	 * Tabulates contents of the given JAR file, associating them with the
	 * provided URL for the JAR file.
	 */
	void addEntries(JarFile jar, SourceJarURL url) {
	    Set contents = new HashSet();
	    for (Enumeration e = jar.entries(); e.hasMoreElements();) {
		String name = ((JarEntry) e.nextElement()).getName();
		if (!(name.startsWith("META-INF") || name.endsWith("/"))) {
		    int pos = name.lastIndexOf("/");
		    contents.add((pos != -1) ? name.substring(0, pos) : name);
		}
	    }
	    if (!contents.isEmpty()) {
		urls.add(url);
		contentMap.put(url, contents);
	    }
	}

	/**
	 * Writes JAR index to the given output stream.
	 */
	void write(JarOutputStream jout) throws IOException {
	    if (contentMap.isEmpty()) {
		logger.finer("omitting empty JAR index");
		return;
	    }
	    logger.finer("writing JAR index");
	    jout.putNextEntry(new JarEntry("META-INF/INDEX.LIST"));
	    Writer w = 
		new BufferedWriter(new OutputStreamWriter(jout, "UTF8"));
	    w.write("JarIndex-Version: 1.0\n\n");

	    // preserve original insertion order
	    for (Iterator i = urls.iterator(); i.hasNext();) {
		SourceJarURL url = (SourceJarURL) i.next();
		Set contents = (Set) contentMap.get(url);

		if (!url.raw.endsWith(".jar")) {
		    if (url.algorithm != null) {
			url = new SourceJarURL(
			    url.path, url.algorithm, url.digest, ".jar");
		    } else if (logger.isLoggable(Level.WARNING)) {
			logger.log(
			    Level.WARNING,
			    "JAR index entry {0} does not end in .jar",
			    new Object[]{ url });
		    }
		}
		if (logger.isLoggable(Level.FINEST)) {
		    logger.log(
			Level.FINEST,
			"writing JAR index entry {0}: {1}",
			new Object[]{ url, contents });
		}
		w.write(url + "\n");
		for (Iterator j = contents.iterator(); j.hasNext(); ) {
		    w.write(j.next() + "\n");
		}
		w.write("\n");
	    }
	    w.flush();
	    jout.closeEntry();
	}
    }

    /**
     * Parses preferred lists.
     */
    private static class PreferredListReader {

	private static final Pattern headerPattern = 
	    Pattern.compile("^PreferredResources-Version:\\s*(.*?)$");
	private static final Pattern versionPattern =
	    Pattern.compile("^1\\.\\d+$");
	private static final Pattern namePattern =
	    Pattern.compile("^Name:\\s*(.*)$");
	private static final Pattern preferredPattern =
	    Pattern.compile("^Preferred:\\s*(.*)$");

	private final boolean defaultPref;
	private final Map namePrefs = new HashMap();
	private final Map packagePrefs = new HashMap();
	private final Map subtreePrefs = new HashMap();

	/**
	 * Parses the given JAR file's preferred list, if any.
	 */
	PreferredListReader(JarFile jar) throws IOException {
	    JarEntry ent = jar.getJarEntry("META-INF/PREFERRED.LIST");
	    if (ent == null) {
		defaultPref = false;
		return;
	    }
	    logger.finer("reading preferred list");
	    BufferedReader r = new BufferedReader(
		new InputStreamReader(jar.getInputStream(ent), "UTF8"));

	    String s = r.readLine();
	    if (s == null) {
		throw new IOException("missing preferred list header");
	    }
	    s = s.trim();
	    Matcher m = headerPattern.matcher(s);
	    if (!m.matches()) {
		throw new IOException("illegal preferred list header: " + s);
	    }
	    s = m.group(1);
	    if (!versionPattern.matcher(s).matches()) {
		throw new IOException(
		    "unsupported preferred list version: " + s);
	    }

	    s = nextNonBlankLine(r);
	    if (s == null) {
		throw new IOException("empty preferred list");
	    }
	    if ((m = preferredPattern.matcher(s)).matches()) {
		defaultPref = Boolean.valueOf(m.group(1)).booleanValue();
		s = nextNonBlankLine(r);
	    } else {
		defaultPref = false;
	    }

	    while (s != null) {
		if (!(m = namePattern.matcher(s)).matches()) {
		    throw new IOException(
			"expected preferred entry name: " + s);
		}
		String name = m.group(1);

		s = nextNonBlankLine(r);
		if (s == null) {
		    throw new IOException("EOF before preferred entry");
		}
		if (!(m = preferredPattern.matcher(s)).matches()) {
		    throw new IOException("expected preferred entry: " + s);
		}
		Boolean pref = Boolean.valueOf(m.group(1));

		String key;
		Map map;
		if (name.endsWith("/*")) {
		    key = name.substring(0, name.length() - 2);
		    map = packagePrefs;
		} else if (name.endsWith("/")) {
		    key = name.substring(0, name.length() - 1);
		    map = packagePrefs;
		} else if (name.endsWith("/-")) {
		    key = name.substring(0, name.length() - 2);
		    map = subtreePrefs;
		} else {
		    key = name;
		    map = namePrefs;
		}
		if (key.length() == 0) {
		    throw new IOException(
			"invalid preferred entry name: " + name);
		}
		map.put(key, pref);
		if (logger.isLoggable(Level.FINEST)) {
		    logger.log(
			Level.FINEST,
			"read preferred list entry {0}: {1}",
			new Object[]{ name, pref });
		}

		s = nextNonBlankLine(r);
	    }
	}

	/**
	 * Returns true if list prefers given entry, or false otherwise.
	 */
	 boolean isPreferred(String entry) {
	     Boolean b = (Boolean) namePrefs.get(entry);
	     if (b != null) {
		 return b.booleanValue();
	     }

	     if (entry.endsWith(".class")) {
		 int i = entry.lastIndexOf('$');
		 while (i >= 0) {
		     String outer = entry.substring(0, i) + ".class";
		     if ((b = (Boolean) namePrefs.get(outer)) != null) {
			 return b.booleanValue();
		     }
		     i = entry.lastIndexOf('$', i - 1);
		 }
	     }

	     int i = entry.lastIndexOf('/');
	     if (i >= 0) {
		 String base = entry.substring(0, i);
		 if ((b = (Boolean) packagePrefs.get(base)) != null) {
		     return b.booleanValue();
		 }

		 for (;;) {
		     if ((b = (Boolean) subtreePrefs.get(base)) != null) {
			 return b.booleanValue();
		     }
		     if ((i = base.lastIndexOf('/')) < 0) {
			 break;
		     }
		     base = base.substring(0, i);
		 }
	     }

	     return defaultPref;
	 }

	/**
	 * Returns next non-blank, non-comment line, or null if end of file has
	 * been reached.
	 */
	private static String nextNonBlankLine(BufferedReader reader)
	    throws IOException
	{
	    String s;
	    while ((s = reader.readLine()) != null) {
		s = s.trim();
		if (s.length() > 0 && s.charAt(0) != '#') {
		    return s;
		}
	    }
	    return null;
	}
    }

    /**
     * Compiles and writes combined preferred lists.
     */
    private static class PreferredListWriter {

	private static final int NAME_LEN      = "Name: ".length();
	private static final int PREFERRED_LEN = "Preferred: ".length();
	private static final int TRUE_LEN      = "true".length();
	private static final int FALSE_LEN     = "false".length();
	private static final int NEWLINE_LEN   = "\n".length();

	private final HashMap pathMap = new HashMap();
	private final DirNode rootNode = new DirNode("");
	private int numPrefs = 0;
	private final List apiClasses;


	/**
	 * Constructs a <code>PreferredListWriter</code>.
	 *
	 * @param apiClasses list of URI paths representing classes that must be
	 * considered API classes in case a preferences conflict arrises during
	 * wrapping of JAR files
	 */
	PreferredListWriter(List apiClasses) {
	    this.apiClasses = apiClasses;
	    pathMap.put("", rootNode);
	}

	/**
	 * Records preferred status of each file entry in the given JAR file,
	 * determined using the provided preferred list reader.
	 */
	void addEntries(JarFile jar, PreferredListReader prefReader)
	    throws IOException
	{
	    for (Enumeration e = jar.entries(); e.hasMoreElements(); ) {
		String path = ((JarEntry) e.nextElement()).getName();
		if (!(path.startsWith("META-INF") || path.endsWith("/"))) {
		    boolean pref = prefReader.isPreferred(path);
		    if (logger.isLoggable(Level.FINEST)) {
			logger.log(
			    Level.FINEST,
			    pref ? "preferred: {0}" : "not preferred: {0}",
			    new Object[]{ path });
		    }
		    addFile(path, jar.getName(), pref);
		}
	    }
	}

	/**
	 * Writes minimal combined preferred list to given output stream.
	 */
	void write(JarOutputStream jout) throws IOException {
	    if (numPrefs == 0) {
		logger.finer("omitting empty preferred list");
		return;
	    }
	    logger.finer("writing preferred list");

	    jout.putNextEntry(new JarEntry("META-INF/PREFERRED.LIST"));
	    Writer w = 
		new BufferedWriter(new OutputStreamWriter(jout, "UTF8"));
	    w.write("PreferredResources-Version: 1.0\n");

	    rootNode.compileList();
	    rootNode.writeList(w);

	    w.flush();
	    jout.closeEntry();
	}

	/**
	 * Records the preferred setting of the given file entry.
	 */
	private void addFile(String path, String jarFileName, boolean preferred)
	    throws IOException
	{
	    FileNode fn = (FileNode) pathMap.get(path);
	    if (fn != null) {
		if (fn.preferred != preferred) {
		    // in case it is part of what are considered API classes
		    // we correct the preferred value if required and correct
		    // the total number of preferred classes encountered
		    if (apiClasses.contains(path)) {
			if (fn.preferred) {
			    fn.preferred = false;
			    numPrefs--;
			}
		    }
		    else {
			throw new LocalizedIOException(
			    "jarwrapper.prefconflict",
			    new Object[] { path, jarFileName, fn.jarFileName });
		    }
		}
		return;
	    }

	    fn = new FileNode(path, jarFileName, preferred);
	    pathMap.put(path, fn);
	    if (preferred) {
		numPrefs++;
	    }

	    path = parentPath(path);
	    DirNode dn = (DirNode) pathMap.get(path);
	    if (dn != null) {
		dn.files.add(fn);
		return;
	    }
	    dn = new DirNode(path);
	    pathMap.put(path, dn);
	    dn.files.add(fn);

	    for (path = parentPath(path); ; path = parentPath(path)) {
		DirNode pn = (DirNode) pathMap.get(path);
		if (pn != null) {
		    pn.subdirs.add(dn);
		    return;
		}
		pn = new DirNode(path);
		pathMap.put(path, pn);
		pn.subdirs.add(dn);
		dn = pn;
	    }
	}

	/**
	 * Returns path of the parent directory of the indicated JAR entry.
	 */
	private static String parentPath(String path) {
	    if (path.endsWith("/")) {
		path = path.substring(0, path.length() - 1);
	    }
	    int i = path.lastIndexOf('/');
	    return (i >= 0) ? path.substring(0, i + 1) : "";
	}

	static int min(int i1, int i2, int i3) {
	    return Math.min(i1, Math.min(i2, i3));
	}

	/**
	 * Returns the number of characters needed to write a preferred list
	 * entry with the given name and preferred setting.  If the given name
	 * is null, then the length of a "default" preferred list entry (i.e.,
	 * an entry without a name) is returned.
	 */
	static int calcEntryLength(String name, boolean pref) {
	    int len = NEWLINE_LEN;
	    if (name != null) {
		len += NAME_LEN + name.length() + NEWLINE_LEN;
	    }
	    len += PREFERRED_LEN + (pref ? TRUE_LEN : FALSE_LEN) + NEWLINE_LEN;
	    return len;
	}

	/**
	 * Writes preferred list entry with the given name and preferred
	 * setting.  If the given name is null, then a "default" preferred list
	 * entry is written.
	 */
	static void writeEntry(Writer w, String name, boolean pref)
	    throws IOException
	{
	    if (logger.isLoggable(Level.FINEST)) {
		logger.log(
		    Level.FINEST,
		    "writing preferred list entry {0}: {1}",
		    new Object[]{
			(name != null) ? name : "<default>",
			Boolean.valueOf(pref) });
	    }
	    w.write("\n");
	    if (name != null) {
		w.write("Name: " + name + "\n");
	    }
	    w.write("Preferred: " + pref + "\n");
	}

	/**
	 * Stores file preference state.
	 */
	private static class FileNode {

	    /* action constants */
	    static final int NONE    = 0;
	    static final int SKIP    = 1;
	    static final int INCLUDE = 2;

	    final String path;
	    final String jarFileName;
	    boolean preferred;
	    int action;

	    FileNode(String path, String jarFileName, boolean preferred) {
		this.path = path;
		this.preferred = preferred;
		this.jarFileName = jarFileName;
	    }
	}

	/**
	 * Represents JAR-internal directory.
	 */
	private class DirNode {

	    final String path;
	    final List subdirs = new ArrayList();
	    final List files = new ArrayList();

	    /*
	     * The length, in characters, of the preferred list covering this
	     * directory subtree if the default preferred setting for the
	     * entire subtree is true.
	     */
	    int prefSubtreeLen;
	    /*
	     * The length, in characters, of the preferred list covering this
	     * directory subtree if the default preferred setting for the
	     * immediate directory is true, but the default preferred setting
	     * for the subtree as a whole is false.
	     */
	    int prefPackageLen;
	    /*
	     * The length, in characters, of the preferred list covering this
	     * directory subtree if the default preferred setting for the
	     * entire subtree is false.
	     */
	    int unprefSubtreeLen;
	    /*
	     * The length, in characters, of the preferred list covering this
	     * directory subtree if the default preferred setting for the
	     * immediate directory is false, but the default preferred setting
	     * for the subtree as a whole is true.
	     */
	    int unprefPackageLen;

	    DirNode(String path) {
		this.path = path;
	    }

	    /**
	     * Computes minimal list length using dynamic programming.
	     */
	    void compileList() {
		int prefLen = 0, unprefLen = 0;
		for (Iterator i = files.iterator(); i.hasNext(); ) {
		    FileNode fn = (FileNode) i.next();

		    for (int j = fn.path.lastIndexOf('$'); 
			 j != -1;
			 j = fn.path.lastIndexOf('$', j - 1))
		    {
			FileNode fn2 = (FileNode) pathMap.get(
			    fn.path.substring(0, j) + ".class");
			if (fn2 != null) {
			    fn.action = (fn.preferred == fn2.preferred) ?
				FileNode.SKIP : FileNode.INCLUDE;
			    break;
			}
		    }

		    int entryLen = calcEntryLength(fn.path, fn.preferred);
		    if (fn.action == FileNode.SKIP) {
			// won't list, so don't increment length counts
		    } else if (fn.action == FileNode.INCLUDE) {
			prefLen += entryLen;
			unprefLen += entryLen;
		    } else if (fn.preferred) {
			unprefLen += entryLen;
		    } else {
			prefLen += entryLen;
		    }
		}
		prefSubtreeLen = prefLen;
		prefPackageLen = prefLen;
		unprefSubtreeLen = unprefLen;
		unprefPackageLen = unprefLen;

		for (Iterator i = subdirs.iterator(); i.hasNext();) {
		    DirNode dn = (DirNode) i.next();
		    dn.compileList();
		    String subtreePath = dn.path + "-";

		    prefSubtreeLen += min(
			dn.prefSubtreeLen,
			dn.unprefSubtreeLen +
			    calcEntryLength(subtreePath, false),
			dn.unprefPackageLen + calcEntryLength(dn.path, false));
		    prefPackageLen += min(
			dn.prefSubtreeLen + calcEntryLength(subtreePath, true),
			dn.prefPackageLen + calcEntryLength(dn.path, true),
			dn.unprefSubtreeLen);
		    unprefSubtreeLen += min(
			dn.prefSubtreeLen + calcEntryLength(subtreePath, true),
			dn.prefPackageLen + calcEntryLength(dn.path, true),
			dn.unprefSubtreeLen);
		    unprefPackageLen += min(
			dn.prefSubtreeLen,
			dn.unprefSubtreeLen +
			    calcEntryLength(subtreePath, false),
			dn.unprefPackageLen + calcEntryLength(dn.path, false));
		}
	    }

	    /**
	     * Writes preferred list.  This method is only called on the 
	     * root node.
	     */
	    void writeList(Writer w) throws IOException {
		int totalPrefSubtreeLen =
		    prefSubtreeLen + calcEntryLength(null, true);
		boolean defaultPref = totalPrefSubtreeLen < unprefSubtreeLen;
		if (defaultPref) {
		    writeEntry(w, null, true);
		}
		writeFiles(w, defaultPref);
		for (Iterator i = subdirs.iterator(); i.hasNext();) {
		    ((DirNode) i.next()).writeDir(w, defaultPref);
		}
	    }

	    /**
	     * Writes preferred list entries (if any) for this directory, which
	     * inherits the given preferred value as its default.
	     */
	    void writeDir(Writer w, boolean contextPref) throws IOException {
		boolean dirPref;
		boolean subdirPref;
		String subtreePath = path + "-";
		if (contextPref) {
		    int totalUnprefPackageLen =
			unprefPackageLen + calcEntryLength(path, false);
		    int totalUnprefSubtreeLen =
			unprefSubtreeLen + calcEntryLength(subtreePath, false);
		    int best = min(
			prefSubtreeLen,
			totalUnprefPackageLen,
			totalUnprefSubtreeLen);
		    if (best == prefSubtreeLen) {
			dirPref = true;
			subdirPref = true;
		    } else if (best == totalUnprefPackageLen) {
			writeEntry(w, path, false);
			dirPref = false;
			subdirPref = true;
		    } else {
			writeEntry(w, subtreePath, false);
			dirPref = false;
			subdirPref = false;
		    }
		} else {
		    int totalPrefPackageLen =
			prefPackageLen + calcEntryLength(path, true);
		    int totalPrefSubtreeLen =
			prefSubtreeLen + calcEntryLength(subtreePath, true);
		    int best = min(
			unprefSubtreeLen,
			totalPrefPackageLen,
			totalPrefSubtreeLen);
		    if (best == unprefSubtreeLen) {
			dirPref = false;
			subdirPref = false;
		    } else if (best == totalPrefPackageLen) {
			writeEntry(w, path, true);
			dirPref = true;
			subdirPref = false;
		    } else {
			writeEntry(w, subtreePath, true);
			dirPref = true;
			subdirPref = true;
		    }
		}
		writeFiles(w, dirPref);
		for (Iterator i = subdirs.iterator(); i.hasNext(); ) {
		    ((DirNode) i.next()).writeDir(w, subdirPref);
		}
	    }

	    /**
	     * Writes preferred list entries (if any) for files in this
	     * directory, which has the given preferred value as its default.
	     */
	    void writeFiles(Writer w, boolean contextPref) throws IOException {
		for (Iterator i = files.iterator(); i.hasNext(); ) {
		    FileNode fn = (FileNode) i.next();
		    if (fn.action != FileNode.SKIP &&
			(fn.action == FileNode.INCLUDE ||
			 fn.preferred != contextPref))
		    {
			writeEntry(w, fn.path, fn.preferred);
		    }
		}
	    }
	}
    }
}
