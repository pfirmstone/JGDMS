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
package au.net.zeus.jgdms.hello;

import java.rmi.RemoteException;
import net.jini.activation.arg.ActivationID;
import net.jini.config.ConfigurationException;
import net.jini.config.ConfigurationProvider;
import net.jini.id.Uuid;
import au.net.zeus.jgdms.api.hello.HelloService;
import au.net.zeus.jgdms.hello.proxy.HelloServiceProxy;
import au.net.zeus.jgdms.service.support.AbstractJiniService;
import au.net.zeus.jgdms.service.support.JiniServiceParameters;
import org.apache.river.start.lifecycle.LifeCycle;

/**
 * Activatable, Jini-aware wrapper around {@link HelloServiceImpl}.
 *
 * <p>This class wires the core business logic ({@link HelloServiceImpl}) into
 * the full JGDMS service infrastructure:
 * <ul>
 *   <li>Reads all configuration from a Jini {@link net.jini.config.Configuration}</li>
 *   <li>Exports itself via a configurable {@link net.jini.export.Exporter}</li>
 *   <li>Builds a {@link HelloServiceProxy} for clients</li>
 *   <li>Registers with Jini lookup services via a
 *       {@link net.jini.lookup.JoinManager}</li>
 * </ul>
 *
 * <p>All infrastructure boilerplate is inherited from
 * {@link AbstractJiniService}; this subclass only supplies service-specific
 * logic.
 *
 * <h2>Configuration component</h2>
 * All Jini configuration entries are read from component
 * {@value #COMPONENT}.  The common infrastructure entries are documented in
 * {@link JiniServiceParameters}.  No service-specific mandatory entries exist.
 *
 * <h2>Activatable constructor</h2>
 * The {@code (ActivationID, String[])} constructor satisfies the Phoenix
 * activation-group contract.
 *
 * <h2>Non-activatable constructor</h2>
 * The {@code (String[], LifeCycle)} constructor supports the
 * {@code NonActivatableServiceDescriptor} / {@code ServiceStarter} framework.
 *
 * <h2>Running the service</h2>
 * <pre>
 * java -Djava.security.policy=hello-service.policy \
 *      -cp &lt;classpath&gt; \
 *      org.apache.river.start.ServiceStarter \
 *      hello-world-start.config
 * </pre>
 *
 * See {@code hello-world-service.config} in the resources directory for the
 * full Jini configuration, including SSL transport setup and method
 * constraints.
 *
 * @see HelloServiceImpl
 * @see HelloService
 * @see AbstractJiniService
 * @since 3.1.1
 * @author Peter Firmstone
 * @author GitHub Copilot
 */
public class HelloWorldServiceImpl
        extends AbstractJiniService
        implements HelloService {

    /** Configuration component name for this service. */
    static final String COMPONENT = "au.net.zeus.jgdms.hello";

    /** The core implementation to which all calls are delegated. */
    private final HelloServiceImpl impl;

    // -------------------------------------------------------------------------
    // Public constructors
    // -------------------------------------------------------------------------

    /**
     * Activatable constructor.  Required by Phoenix.
     *
     * @param activationID the activation ID assigned by the activation system
     * @param data         configuration arguments for
     *                     {@link ConfigurationProvider#getInstance}
     * @throws ConfigurationException if a mandatory configuration entry is
     *                                missing or invalid
     * @throws Exception              if construction otherwise fails
     */
    public HelloWorldServiceImpl(ActivationID activationID,
                                 String[] data)
            throws Exception {
        this(new HelloServiceParameters(
                     ConfigurationProvider.getInstance(
                             data,
                             HelloWorldServiceImpl.class.getClassLoader()),
                     activationID),
             null);
    }

    /**
     * Non-activatable constructor for use with
     * {@code NonActivatableServiceDescriptor} / {@code ServiceStarter}.
     *
     * @param configArgs configuration arguments for
     *                   {@link ConfigurationProvider#getInstance}
     * @param lifeCycle  lifecycle callback; may be {@code null}
     * @throws ConfigurationException if a mandatory configuration entry is
     *                                missing or invalid
     * @throws Exception              if construction otherwise fails
     */
    public HelloWorldServiceImpl(String[] configArgs,
                                 LifeCycle lifeCycle)
            throws Exception {
        this(new HelloServiceParameters(
                     ConfigurationProvider.getInstance(
                             configArgs,
                             HelloWorldServiceImpl.class.getClassLoader()),
                     null),
             lifeCycle);
    }

    /**
     * Internal constructor: receives pre-validated parameters.
     */
    private HelloWorldServiceImpl(HelloServiceParameters params,
                                   LifeCycle lifeCycle)
            throws java.io.IOException {
        super(params, lifeCycle);
        this.impl = new HelloServiceImpl();
    }

    // -------------------------------------------------------------------------
    // AbstractJiniService template methods
    // -------------------------------------------------------------------------

    @Override
    protected Object createProxy(Object stub, Uuid serviceUuid) {
        return HelloServiceProxy.create((HelloService) stub, serviceUuid);
    }

    @Override
    protected Class<?>[] getServiceInterfaces() {
        return new Class<?>[]{ HelloService.class };
    }

    // -------------------------------------------------------------------------
    // HelloService — delegate all calls to the core implementation
    // -------------------------------------------------------------------------

    @Override
    public String sayHello(String name) throws RemoteException {
        getReadyState().check();
        return impl.sayHello(name);
    }

    // -------------------------------------------------------------------------
    // Parameter object
    // -------------------------------------------------------------------------

    /**
     * Parameter object for {@link HelloWorldServiceImpl}.
     *
     * <p>Reads and validates all Jini service configuration entries before the
     * service object is constructed.  There are no service-specific mandatory
     * entries beyond the common infrastructure entries documented in
     * {@link JiniServiceParameters}.
     */
    public static final class HelloServiceParameters extends JiniServiceParameters {

        /**
         * Reads Hello World Service configuration.
         *
         * @param config       the Jini configuration; must be non-null
         * @param activationID the Phoenix activation ID, or {@code null} for
         *                     non-activatable deployments
         * @throws ConfigurationException if any mandatory entry is missing or
         *                                invalid
         */
        public HelloServiceParameters(net.jini.config.Configuration config,
                                      ActivationID activationID)
                throws ConfigurationException {
            super(config, COMPONENT, activationID, HelloService.class);
        }
    }
}
