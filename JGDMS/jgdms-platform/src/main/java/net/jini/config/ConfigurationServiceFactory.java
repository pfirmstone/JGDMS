/*
 * Copyright 2019 The Apache Software Foundation.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package net.jini.config;

/**
 * Provides a means for obtaining {@link Configuration} instances,
 * using a configurable provider factory. The
 * configuration provider can be specified by providing a resource named
 * "META-INF/services/net.jini.config.ConfigurationServiceFactory"
 * <p>
 * This interfaces is intended for developers implementing a {@link Configuration}.
 * Users wanting to obtain a Configuration instance should use 
 * {@link ConfigurationProvider#getInstance(java.lang.String[], java.lang.ClassLoader) }
 * <p>
 * Note that a {@link ConfigurationFile} instance cannot be obtained from this Service.
 * This service is a local service (not Remote), that may be obtained through 
 * {@link java.util.ServiceLoader} or an OSGi service registry.
 * <p>
 * Implementations of this service can be made available through the OSGi
 * service registry by annotating the implementation with:
 * <pre>
 * {@literal @}RequireCapability(
 *	ns="osgi.extender",
 *	filter="(osgi.extender=osgi.serviceloader.registrar)")
 * {@literal @}ProvideCapability(
 *	ns="osgi.serviceloader",
 *	name="net.jini.config.ConfigurationServiceFactory")
 * </pre>
 * 
 * @see <a href="https://jar-download.com/javaDoc/biz.aQute.bnd/biz.aQute.bndlib/3.2.0/aQute/bnd/annotation/headers/ProvideCapability.html">
 * <code>ProvideCapability</code></a>
 * @see <a href="https://jar-download.com/javaDoc/biz.aQute.bnd/biz.aQute.bndlib/3.2.0/aQute/bnd/annotation/headers/RequireCapability.html">
 * <code>RequireCapability</code></a>
 * @see java.util.ServiceLoader
 * @author Peter Firmstone.
 * @since 3.2
 */
public interface ConfigurationServiceFactory {
    
    /**
     * Creates and returns an instance of the configuration provider, using the
     * specified options and class loader. Specifying <code>null</code> for
     * <code>options</code> uses provider-specific default options, if
     * available. Uses the specified class loader to load resources and
     * classes, or the current thread's context class loader if <code>cl</code>
     * is <code>null</code>. Supplies <code>cl</code> as the class loader to
     * the provider constructor. <p>
     *
     * Downloaded code can specify its own class loader in a call to this
     * method in order to use the configuration provider specified in the JAR
     * file from which it was downloaded.
     *
     * @param options values to use when constructing the configuration, or
     * <code>null</code> if default options should be used
     * @param cl the class loader to load resources and classes, and to pass
     * when constructing the provider. If <code>null</code>, uses the context
     * class loader.
     * @return an instance of the configuration provider constructed with the
     * specified options and class loader
     * @throws ConfigurationNotFoundException if <code>options</code> specifies
     * a source location that cannot be found, or if <code>options</code> is
     * <code>null</code> and the provider does not supply default options
     * @throws ConfigurationException if an I/O exception occurs; if there are
     * problems with the <code>options</code> argument or the format of the
     * contents of any source location it specifies; if there is a problem with
     * the contents of the resource file that names the configuration provider;
     * if the configured provider class does not exist, is not public, is
     * abstract, does not implement <code>Configuration</code>, or does not
     * have a public constructor that has <code>String[]</code> and
     * <code>ClassLoader</code> parameters; if the calling thread does not have
     * permission to obtain information from the specified source; or if
     * <code>cl</code> is <code>null</code> and the provider does not have
     * permission to access the context class loader. Any <code>Error</code>
     * thrown while creating the provider instance is propagated to the caller;
     * it is not wrapped in a <code>ConfigurationException</code>.
     */
    public Configuration getInstance(String[] options, ClassLoader cl) 
            throws ConfigurationException;
    
}
