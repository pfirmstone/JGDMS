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

package org.apache.river.test.spec.config.configurationprovider;

import java.util.logging.Level;
import org.apache.river.qa.harness.QATestEnvironment;
import org.apache.river.qa.harness.QAConfig;
import org.apache.river.qa.harness.Test;
import org.apache.river.qa.harness.TestException;
import java.util.logging.Logger;
import java.util.logging.Level;
import net.jini.config.Configuration;
import net.jini.config.ConfigurationProvider;
import net.jini.config.ConfigurationFile;
import net.jini.config.ConfigurationException;
import net.jini.config.ConfigurationNotFoundException;
import java.io.File;
import java.io.OutputStream;
import java.io.FileOutputStream;
import java.io.Writer;
import java.io.BufferedWriter;
import java.io.OutputStreamWriter;
import java.io.FileNotFoundException;
import java.io.UnsupportedEncodingException;
import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.security.AccessControlException;


/**
 * This class collects together common parts for all getInstance
 * method tests infrastructure.
 */
public abstract class GetInstance_QATest extends QATestEnvironment implements Test {

    /**
     * Name of file with configuration for tests.
     */
    final protected String confFileName = "config.prop";

    /**
     * System temporary dir name.
     */
    final protected String tmpDirName = System.getProperty("java.io.tmpdir");

    /**
     * Configuration file.
     */
    final protected File confFile = new File(tmpDirName, confFileName);

    /**
     * Configuration file path.
     */
    final protected String confFilePath = confFile.getPath();

    /**
     * Some valid configuration.
     */
    final protected String someValidConf =
            "import net.jini.security.BasicProxyPreparer;\n"
            + "org.apache.river.start {\n"
            + "    activationSystemPreparer = new BasicProxyPreparer();\n"
            + "}\n";

    /**
     * Writes information to the file.
     *
     * @param file in this file content will be written
     * @param content desired content of configuration file
     */
    protected void createFile(File file, String content)
            throws FileNotFoundException, UnsupportedEncodingException,
            IOException {
        final OutputStream cfos = new FileOutputStream(file);
        final OutputStreamWriter cfosw = new OutputStreamWriter(cfos, "UTF-8");
        final Writer cfw = new BufferedWriter(cfosw);
        cfw.write(content);
        cfw.close();
        cfosw.close();
        cfos.close();
    }

    /**
     * Writes information to the file with name confFileName.
     *
     * @param conf desired content of configuration file
     */
    protected void createConfFile(String conf)
            throws FileNotFoundException, UnsupportedEncodingException,
            IOException {
        createFile(confFile, conf);
    }

    /**
     * Creates array of URLs in the specified subdirectory
     * of the temporary directory with the specified
     * META-INF/services/net.jini.config.Configuration
     * resource content.
     *
     * @param base subdirectory name
     * @param content desired content of the resource file
     * @return created class loader
     */
    protected URL[] createUrls(String base, String content)
            throws IOException {
        File classDir = new File(tmpDirName, base);
        File metaDir = new File(classDir, "META-INF");
        File servicesDir = new File(metaDir, "services");
        servicesDir.mkdirs();
        File confResource = new File(servicesDir.getPath(),
                "net.jini.config.Configuration");
        createFile(confResource, content);
        URL classURL = classDir.toURI().toURL();
        URL[] urls = { classURL };
        return urls;
    }

    /**
     * Creates URL class loder in the specified subdirectory
     * of the temporary directory with the specified
     * META-INF/services/net.jini.config.Configuration
     * resource content.
     *
     * @param base subdirectory name
     * @param content desired content of the resource file
     * @return created class loader
     */
    protected URLClassLoader createClassLoader(String base, String content)
            throws IOException {
        return new URLClassLoader(createUrls(base, content));
    }

    /**
     * Removes directory structure created by createClassLoader method
     *
     * @param base subdirectory name
     */
    protected void removeResources(String base) throws IOException {
        File classDir = new File(tmpDirName, base);
        File metaDir = new File(classDir, "META-INF");
        File servicesDir = new File(metaDir, "services");
        File confResource = new File(servicesDir.getPath(),
                "net.jini.config.Configuration");
        confResource.delete();
        servicesDir.delete();
        metaDir.delete();
        classDir.delete();
    }

    /**
     * Cofiguring resource and testing actions.
     *
     * Create class directory "jini.test.spec.config" in temporary directory.
     * Create resource META-INF/services/net.jini.config.Configuration
     * with specified "content" in the class directory.
     * Create URLClassLoader that points to class directory.
     * Call ConfigurationProvider.getInstance with some valid option and
     * created class loder.
     * Assert that valid class was returned.
     * Cleanup used temporary directory.
     *
     * @param content desired content of resource file
     * @return if ConfigurationException was thrown returns it, null otherwise
     */
    protected Exception testResource(String content) throws IOException {
        createConfFile(someValidConf);
        String[] validOptions = { confFilePath };
        return testResource(validOptions, content);
    }

    /**
     * Cofiguring resource and testing actions.
     *
     * Create class directory "jini.test.spec.config" in temporary directory.
     * Create resource META-INF/services/net.jini.config.Configuration
     * with specified content in the class directory.
     * Create URLClassLoader that points to class directory.
     * Call ConfigurationProvider.getInstance with specified options and
     * created class loder.
     * Assert that valid class was returned.
     * Cleanup used temporary directory.
     *
     * @param options desired content of options array
     * @param content desired content of resource file
     * @return if ConfigurationException was thrown returns it, null otherwise
     */
    protected Exception testResource(String[] options, String content)
            throws IOException {
        String resourceBase = "jini.test.spec.config";
        ClassLoader cl = createClassLoader(resourceBase, content);
        Exception result = testResource(options, content, cl);
        removeResources(resourceBase);
        return result;
    }

    /**
     * Cofiguring resource and testing actions.
     *
     * Call ConfigurationProvider.getInstance with specified options and
     * class loder.
     * Assert that valid class was returned.
     * Cleanup used temporary directory.
     *
     * @param options desired content of options array
     * @param content class name that should be returned
     * @param cl class loader for using as parameter
     * @return if ConfigurationException was thrown returns it, null otherwise
     */
    protected Exception testResource(String[] options, String content,
            ClassLoader cl) throws IOException {
        Exception result = null;

        try {
            Configuration conf = ConfigurationProvider.getInstance(options, cl);

            if (!conf.getClass().getName().equals(content)) {
                throw new AssertionError(
                        "ConfigurationProvider.getInstance method"
                        + " returns invalid class");
            }
        } catch (ConfigurationException ce) {
            result = ce;
        }
        return result;
    }
}
