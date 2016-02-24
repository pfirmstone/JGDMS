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

package org.apache.river.tool;

import org.apache.river.system.FileObserver;
import org.apache.river.system.FileWalker;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;

import java.text.MessageFormat;

import java.util.ArrayList;
import java.util.MissingResourceException;
import java.util.ResourceBundle;
import java.util.StringTokenizer;

/**
 * This class is an abstract class that acts as the base class for 
 * classes wishing to search a directory tree and generate a list of files
 * or fully-qualified package.classnames.
 * <p>
 * Concrete classes that extend this class are required to provide an
 * implementation for the single abstract method that defines additional
 * selection policies to apply when constructing the desired list from the
 * list resulting from the search.
 * <p>
 * This class allows a number of arguments to be input on the command
 * line that can be used to dynamically control how the methods of this
 * class interpret and handle the data that is manipulated. Those
 * command line arguments are:
 * </p>
 *  <ul>
 *    <li> '-drive &lt;drive&gt;'     - the drive letter to prepend to the
 *                                directory tree that is searched
 *    <li> '-root &lt;rootDir>&gt;'    - the root directory to prepend to the relative
 *                                directory used as the start point of the
 *                                search
 *    <li> '-start &lt;startDir&gt;'  - the directory (absolute or relative) from
 *                                which to start the search
 *    <li> '-suffix &lt;.fileExt>&gt;' - will search for only files having this suffix
 *                                as extension (ex. .java, .class, .c, etc)
 *    <li> '-out &lt;outputFile&gt;'  - the file to which to write the generated list
 *    <li> '-noRecurse'         - will search for the desired files only in the
 *                                given start directory; that is, it will not
 *                                "walk the tree"
 *    <li> '-verbose'           - will display status information as 
 *                                processing proceeds
 *    <li>
 *    <li>
 *  </ul>
 */
abstract public class AbstractTreeWalker {

    /** PrintWriter to which the fully-qualified package and test names will
     *  be written. Defaults to standard output.
     */
    PrintWriter outPW = new PrintWriter(System.out,true);

    private static ResourceBundle resources;
    private static boolean resinit = false;

    private String drive = "c";
    private String rootDir = null;

    /** The absolute path of the directory from which to start the search for
     *  test source files.
     */
    private String startDir = ".";
    private String suffix   = ".java";
    private String outFilename;
    private boolean recurse = true;
    private boolean verbose = false;

    /* Constructs instance of this class initialized with command line args */
    public AbstractTreeWalker(String[] args) {
        setup(args);
    }//end constructor

    /** Creates the list of fully-qualified package.testnames to be executed */
    abstract void buildPackageList();

    /** Walks the directory tree populating the list with the source files to
     *  analyze
     */
    ArrayList getFilesList() {
        File startFD = new File(startDir);
        if (!startFD.exists()) {
            print("treewalker.not.exist",startDir);
            System.exit(0);
        }
        if (!startFD.isDirectory()) {
            print("treewalker.not.directory",startDir);
            System.exit(0);
        }
        FileWalker fileWalker = new FileWalker();
        FileObserver fileObserver = new FileObserver(suffix);
        fileWalker.addObserver(fileObserver);
        fileWalker.walk(startFD,recurse);
        return fileObserver.getFileList();
    }//end getFilesList

    /** Converts the elements of the given ArrayList from directory format
     *  to package format. If the rootDir is not null, strips off the 
     *  directories contained in the rootDir; otherwise, it converts the
     *  whole directory path to packages. Strips off the suffix.
     */
    ArrayList getPkgList(ArrayList dirList) {
        ArrayList pkgList = new ArrayList();
        String rootPkg = ( (rootDir == null) ? null : pathToPkg(rootDir) );
        for(int i=0;i<dirList.size();i++) {
            String pkg = pathToPkg((String)dirList.get(i));
            if(pkg.endsWith(suffix)) { //strip off the suffix
                int indx = pkg.lastIndexOf(suffix);
                pkg = pkg.substring(0,indx);
            }
            if(rootPkg != null) { //strip off the rootDir
                if(pkg.startsWith(rootPkg+".")) {
                    int indx = 1+rootPkg.length();
                    pkg = pkg.substring(indx);
                }
            }//end if
            pkgList.add(pkg);
        }//end loop(i)
        return pkgList;
    }//end getPkgList

    /** Print out string according to resourceBundle format. */
    static void print(String key, Object val) {
        String fmt = getString(key);
        if (fmt == null)  fmt = "no text found: \"" + key + "\" {0}";
        System.err.println(MessageFormat.format(fmt, new Object[]{val}));
    }//end print

    /** Print out string according to resourceBundle format. */
    static void print(String key, Object val1, Object val2) {
        String fmt = getString(key);
        if (fmt == null)  fmt = "no text found: \"" + key + "\" {0} {1}";
        System.err.println(MessageFormat.format(fmt,new Object[]{val1, val2}));
    }//end print

    /** Get the strings from this class' resource localization bundle. */
    private static String getString(String key) {
        if (!resinit) {
            try {
                resources = ResourceBundle.getBundle
                                   ("org.apache.river.tool.resources.treewalker");
                resinit = true;
            } catch (MissingResourceException e) {
            }
        }//endif
        try {
            return resources.getString(key);
        } catch (MissingResourceException e) {
            return null;
        }
    }//end getString

    /** Convenience method for handling the command line input to this utility.
     *  This method parses the command line and from the values retrieved
     *  from the command line, sets the fields of this class with the
     *  appropriate data.
     */
    private void setup(String[] args) {
        for (int i = 0; i < args.length ; i++ ) {
            String arg = args[i];
            if(arg.equals("-drive")) {
                i++;
                setDrive(args[i]);
            } else if(arg.equals("-root")) {
                i++;
                setRootDir(args[i]);
            } else if(arg.equals("-start")) {
                i++;
                startDir = args[i];
            } else if(arg.equals("-suffix")) {
                i++;
                suffix = args[i];
            } else if(arg.equals("-out")) {
                i++;
                setOutPrintWriter(args[i]);
            } else if(arg.equals("-noRecurse")) {
                recurse = false;
            } else if(arg.equals("-verbose")) {
                verbose = true;
            } else if(arg.startsWith("-")) {
                usage();
                System.exit(0);
            }//endif
        }//end loop
        if(!isAbsolute(startDir)) {
            startDir = relativeToAbsolutePath(rootDir,startDir);
        }//endif
        if(verbose) {
            if(dosFileSystem()) print("treewalker.print.drive",drive);
            if(rootDir != null) print("treewalker.print.root",rootDir);
            print("treewalker.print.start",startDir);
            print("treewalker.print.suffix",suffix);
            print("treewalker.print.recurse", recurse);
        }//endif
    }//end setup

    /** Print out the usage for this utility. */
    private static void usage() {
	print("treewalker.usage", null);
    }//end usage

    /** Sets the drive letter for the root directory */
    private void setDrive(String drv) {
        char[] charArray = drv.toCharArray();
        if((drv.length() > 1) || ((charArray[0] < 'A')||(charArray[0] > 'z'))){
            print("treewalker.warning", "illegal drive ("+drv+") -- "
                                    +"must be single letter a-z");
            return;
        }
        drive = drv;
    }//end setDrive

    /** Sets the root directory to prepend to the relative start directory */
    private void setRootDir(String dir) {
        if(!isAbsolute(dir)) {
            print("treewalker.absolute","input root directory -- "+dir);
            System.exit(0);
        }
        rootDir = dir;
    }//end setRootDir

    /** Gets the root directory to prepend to the relative start directory
     * @return  */
    protected String getRootDir() {
        return rootDir;
    }//end getRootDir

    /** Based on the input directory, retrieves and sets the absolute path of
     *  the directory from which to start the search for test source files.
     */
    private void setStartDir(String dir) {
        File startDirFD = new File(dir);
        startDir = startDirFD.getAbsolutePath();
    }//end setStartDir

    /** Based on the input filename, creates and sets a PrintWriter to which
     *  the fully-qualified package and test names will be written.
     */
    private void setOutPrintWriter(String filename) {
        try {
            FileWriter outFW = new FileWriter(filename);
            outPW = new PrintWriter(outFW,true);
        } catch(IOException e) {
            print("treewalker.open.failure",filename);
            System.exit(0);
        }
    }//end setOutPrintWriter

    /** Converts components of the given directory path to package format. */
    private String pathToPkg(String path) {
        String dirComponents[] = getDirs(path);
        StringBuffer strBuf = new StringBuffer(dirComponents[0]);
        for(int i=1;i<dirComponents.length;i++) {
            strBuf = strBuf.append(".").append(dirComponents[i]);
        }//end loop(i)
        return strBuf.toString();
    }//end pathToPkg

    /** Determines whether the <code>path</code> parameter is absolute or
     *  relative. 
     * 
     *  This method applies the following criteria in making the 
     *  determination: if the <code>path</code> parameter contains a drive
     *  designation, the <code>path</code> parameter is always considered
     *  absolute, regardless of the current file system. Additionally,
     *  although the windows operating system considers a path that 
     *  begins with two separators ('\\') absolute, and a path that begins
     *  with only a single separator ('\') relative, this method considers
     *  both relative. 
     *
     *  @param path <code>String</code> containing the path/filename to analyze
     *
     *  @return <code>true</code> if the <code>path</code> parameter is 
     *          absolute; <code>false</code> otherwise.
     */
    private boolean isAbsolute(String path) {
        /* A drive always means absolute no matter what file system. Also,
         * On windows: drive or double separator ==> absolute,
         *             but single separator ==> relative.
         * This method considers only a drive letter absolute.
         */
        if(hasDrive(path)) return true;
        if(!dosFileSystem()) {
            File fd = new File(path);
            return fd.isAbsolute();
        }
        return false;
    }//end isAbsolute

    /** Determines whether the <code>path</code> parameter contains a 
     *  drive designator.
     *
     *  @param path <code>String</code> containing the path/filename to analyze
     *
     *  @return <code>true</code> if the <code>path</code> parameter contains a
     *          drive designator; <code>false</code> otherwise.
     */
    private boolean hasDrive(String path) {
        char[] charArray = path.toCharArray();
        return ((charArray[0] >= 'A') && (charArray[0] <= 'z'))
            && (    (charArray[1] == ':') 
		&& ((charArray[2] == '\\') || (charArray[2] == '/')) );
    }//end hasDrive

    /** Determines whether or not the current file system is a DOS file system.
     *
     *  @return <code>true</code> if the current file system is a DOS file
     *          system; <code>false</code> otherwise.
     */
    private boolean dosFileSystem() {
        String sep = System.getProperty("file.separator");
        String dosSep  = "\\";
        boolean isDos = ( (dosSep.compareTo(sep) == 0));
        return isDos;
    }//end dosFileSystem

    /** Returns a <code>String</code> containing the absolute path associated
     *  with the <code>path</code> parameter. When "computing" the absolute
     *  path of the <code>path</code> parameter, this method considers
     *  the current file system and whether or not the <code>path</code>
     *  parameter contains a drive designation: if the file system does
     *  expect a drive designation, but the <code>path</code> parameter
     *  contains such a drive, this method will strip off the drive designation
     *  and return a path/filename made up with the given components
     *  and path separators appropriate for the current file system. 
     *
     *  @param path <code>String</code> containing the path/filename to 
     *              process. 
     *
     *  @return <code>String</code> containing the absolute path/filename
     *          for the given <code>path</code> parameter.
     */
    private String getAbsolutePath(String path) {
        if( !dosFileSystem() && hasDrive(path) ) {
            /* if on unix & has a drive: strip off the drive */
            String[] components = getDirs(path);
            String sep = System.getProperty("file.separator");
            StringBuffer strBuf = new StringBuffer(sep+components[1]);
            for(int i=2;i<components.length;i++) {
                strBuf = strBuf.append(sep).append(components[i]);
            }
            return strBuf.toString();
        }
        return (new File(path)).getAbsolutePath();
    }//end getAbsolutePath

    /** Returns a <code>String</code> array whose elements correspond to the
     *  drive/directory/file components making up the <code>path</code> 
     *  parameter.
     *
     *  @param path <code>String</code> containing the path/filename to parse. 
     *
     *  @return <code>String</code> array containing the drive/directory/file
     *          components making up the <code>path</code> parameter.
     */
    private String[] getDirs(String path) {
        String[] strArray = new String[0];
        String delimiter = "/\\"; // handle mixed path separators
        StringTokenizer st = new StringTokenizer(path,delimiter);
        int n = st.countTokens();
        if (n > 0) {
            strArray = new String[n];
            for(int i=0;((st.hasMoreTokens())&&(i<n));i++) {
                strArray[i] = st.nextToken();
            }
            return strArray;
        } else {
            return strArray;
        }
    }//end getDirs

    /** Returns the drive separator associated with the current file system.
     *  For example, on DOS files systems, ":" would be returned, whereas on
     *  unix file systems, "" would be returned.
     *
     * @return <code>String</code> containing the drive separator associated
     *         with the current file system.
     */
    private String getDriveSeparator() {
        String driveSep = ( dosFileSystem() ? ":" : "" );
        return driveSep;
    }//end getDriveSeparator

    /** Returns the drive letter to use depending on the file system.
     *
     *  @return <code>String</code> containing the drive letter to use.
     */
    private String getFileSystemDrive() {
        String drv = ( dosFileSystem() ? drive : "" );
        return drv;
    }//end getFileSystemDrive

    /**
     * This method constructs a well-formed, absolute path/filename from the
     * given <code>installDir</code> and <code>path</code> parameters. This
     * method is intended to be sensitive to file system dependencies.
     * For example, if the property is written in DOS file format
     * (ex. C:\aaa\bbb\ccc\), but this method is executed on a system with
     * a unix file system, this method will build a String in the unix file
     * system format from the contents of the given String (/aaa/bbb/ccc).
     *
     * In addition to being sensitive to file system differences, this method
     * is also designed to handle both absolute and relative paths in a 
     * well-defined manner. If the path/filename corresponding to the given
     * <code>path</code> parameter is absolute, that path/filename will be
     * converted to the appropriate file system format with no other
     * modifications or additions applied. On the other hand, if the
     * <code>path</code> parameter is relative, then the value contained in
     * <code>installDir</code> parameter will be prepended to the 
     * <code>path</code> parameter, and the result will be returned in the
     * appropriate file system format.
     *
     * Whether a given path/filename is viewed as relative or absolute is
     * based on the following criteria:
     *
     *  non-DOS file systems  abs/rel    DOS file systems  abs/rel
     *  --------------------  --------   ----------------  --------
     *     /aaa/bbb/ccc       absolute    c:\aaa\bbb\ccc   absolute
     *     aaa/bbb/ccc        relative      \aaa\bbb\ccc   relative
     *                                      \\aaa\bbb\ccc  relative
     *                                        aaa\bbb\ccc  relative
     * 
     * Note that this method also attempts to handle the case where the 
     * format of the given <code>path</code> parameter is different than
     * the current file system's format. For example,
     *
     * file sys installDir      path      abs/rel         output 
     * -------- ---------- -------------- --------  --------------------
     *   DOS     c:\files  c:\aaa\bbb\ccc absolute  c:\aaa\bbb\ccc
     *   unix    c:\files  c:\aaa\bbb\ccc absolute    /aaa/bbb/ccc
     *   DOS     c:\files     aaa\bbb\ccc relative  c:\files\aaa\bbb\ccc
     *   unix    c:\files     aaa\bbb\ccc relative    files/aaa/bbb/ccc
     *
     * @param path       <code>String</code> containing the name of the
     *                   path/filename to modify (if necessary)
     * @param installDir <code>String</code> containing the name of the
     *                   directory to prepend to the <code>path</code>
     *                   parameter
     *
     * @return a <code>String</code> containing a path/filename constructed
     *         from the value of the input parameters in a format that is
     *         appropriate to the current file system.
     */
    private String relativeToAbsolutePath(String installDir, String path) {
        if(path == null) return path;
        String sep = System.getProperty("file.separator");
        if(isAbsolute(path)) {/* Absolute path, don't prepend installDir */
            /* re-build the path with "clean" components */
            StringBuffer pBuf = new StringBuffer();
            String pDir = getAbsolutePath(path);
            String pDirComponents[] = getDirs(pDir);
            int pStart = 0;
            if(hasDrive(pDir)) {
                pBuf = pBuf.append(pDirComponents[0]);
                pStart = 1;
            }
            for(int i=pStart;i<pDirComponents.length;i++) {
                pBuf = pBuf.append(sep).append(pDirComponents[i]);
            }
            return pBuf.toString();
        } else { /* Relative path, prepend installDir */
            String driveSep = getDriveSeparator();
            String iDir = null;
            if(installDir != null) {
                /* re-build the install directory with "clean" components */
                StringBuffer iBuf = new StringBuffer();
                iDir = getAbsolutePath(installDir);
                String iDirComponents[] = getDirs(iDir);
                int iStart = 0;
                if(hasDrive(iDir)) {
                    iBuf = iBuf.append(iDirComponents[0]);
                    iStart = 1;
                } else if(dosFileSystem()) { // no drive but windows: get drive
                    String iDrive = getFileSystemDrive();
                    iBuf = iBuf.append(iDrive).append(driveSep);
                }
                for(int i=iStart;i<iDirComponents.length;i++) {
                    iBuf = iBuf.append(sep).append(iDirComponents[i]);
                }
                iDir = iBuf.toString();
            }
            /* construct the new path by pre-pending the installDir */
            String pDir = path;
            String pDirComponents[] = getDirs(pDir);
            int pStart = 0;
            StringBuffer pBuf = new StringBuffer();
            /* if no install directory, use OS-dependent absolute path */
            if( (iDir == null) || (iDir.length() == 0) ) { // no installDir
                pDir = getAbsolutePath(path);
                pDirComponents = getDirs(pDir);
                if(hasDrive(pDir)) {
                    pBuf = pBuf.append(pDirComponents[0]);
                    pStart = 1;
                } else if(dosFileSystem()) {
                    String pDrive = getFileSystemDrive();
                    pBuf = pBuf.append(pDrive).append(driveSep);
                }
            } else { // prepend the install directory
                pBuf = pBuf.append(iDir);
            }
            /* re-build path with install directory and "clean" components */
            for(int i=pStart;i<pDirComponents.length;i++) {
                pBuf = pBuf.append(sep).append(pDirComponents[i]);
            }
            return pBuf.toString();
        }
    }//end relativeToAbsolutePath

}//end class AbstractTreeWalker

