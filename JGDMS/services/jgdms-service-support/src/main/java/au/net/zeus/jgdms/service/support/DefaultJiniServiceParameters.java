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
package au.net.zeus.jgdms.service.support;

import net.jini.activation.arg.ActivationID;
import net.jini.config.Configuration;
import net.jini.config.ConfigurationException;

/**
 * Concrete, no-extra-config parameter object for
 * {@link AbstractJiniService} subclasses that need no service-specific
 * configuration entries beyond those already defined by
 * {@link JiniServiceParameters}.
 *
 * <h2>Background</h2>
 * {@link JiniServiceParameters} is abstract so that richer services can
 * declare a subclass that reads and validates service-specific entries
 * <em>before</em> the service object is constructed.  However, simple
 * services (like a Hello World or a basic adapter service) have no
 * additional configuration entries and were previously forced to write a
 * trivial one-liner inner class:
 * <pre>
 * public static final class MyServiceParameters extends JiniServiceParameters {
 *     public MyServiceParameters(Configuration config, ActivationID id)
 *             throws ConfigurationException {
 *         super(config, MY_COMPONENT, id, MyService.class);
 *     }
 * }
 * </pre>
 *
 * <p>{@code DefaultJiniServiceParameters} eliminates this inner class.
 * Services with no extra configuration entries can pass a
 * {@code DefaultJiniServiceParameters} instance directly to the
 * {@link AbstractJiniService#AbstractJiniService(JiniServiceParameters,
 * org.apache.river.start.lifecycle.LifeCycle) AbstractJiniService} constructor,
 * or rely on the convenience constructors that
 * {@link AbstractJiniService} provides since 3.1.1.
 *
 * <h2>Typical usage</h2>
 * <pre>
 * // Activatable constructor
 * public MyServiceImpl(ActivationID activationID, String[] data)
 *         throws Exception {
 *     this(new DefaultJiniServiceParameters(
 *              ConfigurationProvider.getInstance(
 *                      data, MyServiceImpl.class.getClassLoader()),
 *              COMPONENT, activationID, MyService.class),
 *          null);
 * }
 *
 * // Or use the convenience super-constructors added in AbstractJiniService:
 * public MyServiceImpl(String[] configArgs, LifeCycle lifeCycle)
 *         throws Exception {
 *     super(configArgs, lifeCycle, COMPONENT, MyService.class);
 * }
 * </pre>
 *
 * @see JiniServiceParameters
 * @see AbstractJiniService
 * @since 3.1.1
 * @author Peter Firmstone
 * @author GitHub Copilot
 */
public final class DefaultJiniServiceParameters extends JiniServiceParameters {

    /**
     * Reads all common Jini service configuration entries and stores them
     * for use by {@link AbstractJiniService}.
     *
     * <p>No service-specific entries are read.  Any additional entries
     * required by the service must be read by a custom
     * {@link JiniServiceParameters} subclass instead.
     *
     * @param config           the Jini configuration; must be non-null
     * @param component        the configuration component name
     *                         (e.g. {@code "com.example.myservice"}); must
     *                         be non-null
     * @param activationID     the Phoenix activation ID, or {@code null} for
     *                         non-activatable deployments
     * @param serviceInterface the primary remote interface of the service,
     *                         used to determine the default exporter's
     *                         {@link ClassLoader} and codebase fallback;
     *                         must be non-null
     * @throws ConfigurationException if any mandatory configuration entry is
     *                                missing, of the wrong type, or otherwise
     *                                invalid
     */
    public DefaultJiniServiceParameters(Configuration config,
                                        String component,
                                        ActivationID activationID,
                                        Class<?> serviceInterface)
            throws ConfigurationException {
        super(config, component, activationID, serviceInterface);
    }

    /**
     * Reads all common Jini service configuration entries and derives the default
     * exporter's per-service dispatch-only admin set from {@code serviceImpl}
     * (design decision D5).
     *
     * @param config           the Jini configuration; must be non-null
     * @param component        the configuration component name; must be non-null
     * @param activationID     the Phoenix activation ID, or {@code null} for
     *                         non-activatable deployments
     * @param serviceInterface the primary remote interface of the service; must be
     *                         non-null
     * @param serviceImpl      the concrete service implementation class whose derived
     *                         admin set becomes the exporter's dispatch-only set, or
     *                         {@code null} to fall back to {@code {JoinAdmin,
     *                         DestroyAdmin}}
     * @throws ConfigurationException if any mandatory configuration entry is missing,
     *                                of the wrong type, or otherwise invalid
     */
    public DefaultJiniServiceParameters(Configuration config,
                                        String component,
                                        ActivationID activationID,
                                        Class<?> serviceInterface,
                                        Class<?> serviceImpl)
            throws ConfigurationException {
        super(config, component, activationID, serviceInterface, serviceImpl);
    }
}
