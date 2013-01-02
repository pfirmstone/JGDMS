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
package com.sun.jini.test.spec.loader.util;

// java.io
import java.io.File;

// java.net
import java.net.URL;
import java.net.MalformedURLException;
import java.net.InetAddress;

// java.util
import java.util.Random;
import java.util.StringTokenizer;

// java.lang.reflect
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;

// java.util.logging
import java.util.logging.Logger;
import java.util.logging.Level;

// com.sun.jini.qa
import com.sun.jini.qa.harness.QATestEnvironment;
import com.sun.jini.qa.harness.QAConfig;
import com.sun.jini.qa.harness.TestException;

/**
 *  Helper class to define preferred/non-preferred classes/resources.
 *  Static arrays of this class describe classes/resources names and
 *  it's preffered status according with
 *  com/sun/jini/test/spec/loader/util/classes/META-INF/PREFERRED.LIST file.
 */
public class Util {

    /** Static array of classes to be preferred or not preferred */
    public static final Item[] listClasses = {
            new Item("classes.Class01", true,  true),
            new Item("classes.Class02", false, true),
            new Item("classes.Class03", true,  true), 
            new Item("classes.Class04", false, true), 
            new Item("classes.Class05", true,  true), 
            new Item("classes.Class06", false, true), 
            new Item("classes.Class07", true,  true), 
            new Item("classes.Class08", false, true), 
            new Item("classes.Class09", true,  true), 
            new Item("classes.Class10", false, true), 
            new Item("classes.Class09$Inner1", true,  true), 
            new Item("classes.Class10$Inner1", false, true), 
            new Item("classes.Class09$Inner1$Inner2", true,  true), 
            new Item("classes.Class10$Inner1$Inner2", false, true), 
            new Item("classes.Class09$Inner1$Inner2$Inner3", true,  true), 
            new Item("classes.Class10$Inner1$Inner2$Inner3", false, true), 
            new Item("classes.dir01.Class01", true,  true),
            new Item("classes.dir01.Class02", false, true),
            new Item("classes.dir01.Class03", true,  true), 
            new Item("classes.dir02.Class01", true,  true),
            new Item("classes.dir02.Class02", true,  true),
            new Item("classes.dir02.Class03", true,  true), 
            new Item("classes.dir02.dir.Class01", true,  true),
            new Item("classes.dir02.dir.Class02", false, true),
            new Item("classes.dir02.dir.Class03", true,  true), 
    };

    /** Static array of resources to be preferred or not preferred */
    public static final Item[] listResources = {
            new Item("resources/files/file01", true,  false),
            new Item("resources/files/file02", false, false),
            new Item("resources/files/file03", true,  false), 
    };

    /**
     * Static array of classes which can be found in the executing VM's
     * classpath only
     */
    public static final Item[] listLocalClasses = {
            new Item("classes_local.Class01", false, true),
            new Item("classes_local.Class02", false, true),
            new Item("classes_local.Class03", false, true), 
    };

    /**
     * Static resources of resources which can be found in the executing VM's
     * classpath only
     */
    public static final Item[] listLocalResources = {
            new Item("resources_local/files/file01", false, false),
            new Item("resources_local/files/file02", false, false),
            new Item("resources_local/files/file03", false, false), 
    };

    /**
     * Static array of classes which cannot be found in the executing VM's
     * classpath and cannot be downloaded, but defined in the
     * PREFERRED.LIST file
     */
    public static final Item[] listNotAvailableClasses = {
            new Item("classes_not_available.Class01", true,  true),
            new Item("classes_not_available.Class02", false, true),
            new Item("classes_not_available.Class03", true,  true), 
    };

    /** Static array of interfaces to be loaded */
    public static final Item[] listInterfaces = {
            new Item("classes.Interface01", true,  true),
            new Item("classes.Interface02", true,  true),
    };

    /**
     * Returns the name which is not present
     * in the list of classes/resources.
     *
     * @param isClass true if returned random name refers to a class.
     *
     * @return random name for class/resource
     */
    public static final String getRandomName(boolean isClass) {
        Random random = new Random();
        String prefix = null;

        if (isClass) {
            prefix = Item.PACKAGE + "classes.Class";
        } else {
            prefix = Item.PATH + "resources/files/file";
        }
        return prefix + "_" + random.nextInt();
    }

    /**
     * Returns the name which is not present
     * in the list of interfaces.
     *
     * @return random name for interface
     */
    public static final String getRandomInterface() {
        Random random = new Random();
        return Item.PACKAGE + "classes.Interface" + "_" + random.nextInt();
    }

    /** the class server class */
    private static Class serverClass = null;

    /** the class server name */
    private static final String CLASSServerName =
            "com.sun.jini.tool.ClassServer";

    /** jar file in the executing VM's classpath */
    public static final String QAJarFile = "jinitests.jar";

    /** jar file with preferred list, classes and resources */
    public static final String PREFERREDJarFile = "qa1-loader-pref.jar";

    /** jar file without preferred list, but with classes and resources */
    public static final String NOPREFERREDListJarFile =
            "qa1-loader-pref-NO_PREFERRED_LIST.jar";

    /**
     * Start class sever to download preferred classes/resources
     * using httpPort.
     */
    public static Object startClassServer(QAConfig config, int httpPort)
            throws TestException {
        if (serverClass == null) {
            try {
                serverClass = Class.forName(CLASSServerName);
            } catch (ClassNotFoundException e) {
                throw new TestException("Cannot load " + CLASSServerName, e);
            }
        }
        Constructor cons = null;

        try {
            cons = serverClass.getConstructor(new Class[] {
                int.class, String.class, boolean.class, boolean.class});
        } catch (NoSuchMethodException e) {
            throw new TestException("No constructor for " + CLASSServerName);
        }
        String kitJarsDir = getJarsDir(config);

        if (kitJarsDir == null) {
            throw new TestException("com.sun.jini.qa.home not defined");
        }
        boolean trees = true;
        boolean verbose = false;
        Object classServer = null;

        try {
            classServer = cons.newInstance(new Object[] {
                new Integer(httpPort), kitJarsDir, new Boolean(trees),
                        new Boolean(verbose)});
        } catch (Exception e) {
            throw new TestException("Cannot instantiate " + CLASSServerName);
        }

        // invoke classServer.start();
        try {
            Method method = serverClass.getMethod("start", new Class[] {});
            method.invoke(classServer, new Object[] {});
        } catch (Exception e) {
            throw new TestException("Exception when invoke start method "
                    + CLASSServerName, e);
        }
        return classServer;
    }

    /**
     * Terminate the class sever.
     */
    public static void stopClassServer(Object classServer) {
        if (serverClass == null || classServer == null) {
            return;
        }

        try {
            Method method = serverClass.getMethod("terminate", new Class[] {});
            method.invoke(classServer, new Object[] {});
        } catch (Exception e) {
            Logger logger = Logger.getLogger("com.sun.jini.qa.harness.test");
            logger.log(Level.FINEST, "Exception when invoke terminate method "
                    + CLASSServerName);
        }
    }

    /**
     * Returns http or file based url address to download preferred classes.
     *
     * @param isHttp if true will return http based url address otherwise
     *         - file based.
     *
     * @param config the QAConfig object.
     *
     * @param port http port.
     *
     * @return url address.
     */
    public static String getUrlAddr(boolean isHttp, QAConfig config, int port)
            throws TestException {
        String addr = null;

        try {
            if (isHttp) {
                InetAddress inetAddress = InetAddress.getLocalHost();
                String host = inetAddress.getHostName();
                addr = "http://" + host + ":" + port + "/";
            } else {
                String path = getJarsDir(config);

                if (path == null || path.length() == 0) {
                    throw new TestException("Cannot get kit jars dir");
                }
                String sprt = File.separator;

                if (sprt.equals("\\")) {
                    StringTokenizer parser = new StringTokenizer(path, sprt);
                    StringBuffer newPath = new StringBuffer("file:/");

                    // At least one token should be in and
                    // this token is drive letter
                    newPath.append(parser.nextToken().toUpperCase());
                    newPath.append("/");

                    while (parser.hasMoreTokens()) {
                        newPath.append(parser.nextToken());
                        newPath.append("/");
                    }
                    addr = newPath.toString();
                } else {
                    addr = "file://" + path + "/";
                }
            }
        } catch (TestException qe) {
            throw qe;
        } catch (Exception e) {
            throw new TestException("Cannot get url addr: " + e.toString());
        }
        return addr;
    }

    /**
     * Returns http or file URLs for file1 and file2.
     *
     * @param isHttp if true will return http based urls otherwise - file based.
     *
     * @param file1 jar file to download classes/resources.
     *
     * @param file2 jar file to download classes/resources.
     *
     * @return array of urls that represent file1 and file2.
     */
    public static URL[] getUrls(boolean isHttp, String file1, String file2,
            QAConfig config, int port) throws TestException {
        URL[] urls = null;

        try {
            String addr = getUrlAddr(isHttp, config, port);

            if (file2 != null) {
                urls = new URL[] {
                    new URL(addr + file1), new URL(addr + file2) };
            } else {
                urls = new URL[] {
                    new URL(addr + file1) };
            }
        } catch (Exception e) {
            throw new TestException("Cannot get url for " + file1, e);
        }
        return urls;
    }

    /**
     * Converts path to array of urls.
     *
     * @param path to be converted.
     *
     * @return array of urls converted from path.
     */
    public static URL[] pathToURLs(String path) throws MalformedURLException {
        StringTokenizer tokenString = new StringTokenizer(path);
        URL[] urls = new URL[tokenString.countTokens()];
        int index = 0;

        while (tokenString.hasMoreTokens()) {
            urls[index++] = new URL(tokenString.nextToken());
        }
        return urls;
    }

    /**
     * Returns jars dir with qa1*.jar files.
     *
     * @param config the QAConfig object.
     *
     * @return jars dir with qa1*.jar files.
     */
    public static String getJarsDir(QAConfig config) {
	String home = config.getStringConfigVal("com.sun.jini.test.home", "");
        return home + File.separator + "lib";
    }

    /**
     * Returns the system class loader
     *
     * @return system class loader.
     */
    public static ClassLoader systemClassLoader() {
        return ClassLoader.getSystemClassLoader();
    }
}
