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

import javax.security.auth.login.LoginContext;
import net.jini.activation.ActivationExporter;
import net.jini.activation.arg.ActivationID;
import net.jini.admin.Administrable;
import net.jini.admin.JoinAdmin;
import net.jini.config.Configuration;
import net.jini.config.ConfigurationException;
import net.jini.core.constraint.MethodConstraints;
import net.jini.core.discovery.LookupLocator;
import net.jini.core.entry.Entry;
import net.jini.export.Exporter;
import net.jini.jeri.BasicJeriExporter;
import net.jini.jeri.DynamicILFactory;
import net.jini.jeri.tcp.TcpServerEndpoint;
import org.apache.river.admin.DestroyAdmin;
import org.apache.river.config.Config;

/**
 * Abstract base class for Jini service parameter objects.
 *
 * <p>Subclasses read service-specific configuration entries in their own
 * constructor, so that <em>all</em> validation happens during parameter-object
 * construction and any {@link ConfigurationException} is thrown <em>before</em>
 * the service object is created.  This eliminates the pattern of swallowing
 * exceptions in the service constructor and re-throwing them later in
 * {@link AbstractJiniService#start()}.
 *
 * <h2>Common configuration entries</h2>
 * The following entries are read from the given {@code component} name:
 * <ul>
 *   <li>{@code serverExporter} ({@link Exporter}) — used to export the
 *       service; defaults to a {@link BasicJeriExporter} over TCP whose
 *       invocation-layer factory is a {@link net.jini.jeri.DynamicILFactory}
 *       carrying the Jini admin interfaces ({@link Administrable},
 *       {@link JoinAdmin}, {@link DestroyAdmin}) in its server dispatcher, so the
 *       service's admin methods dispatch over the export with no per-service code
 *       generation or config.  {@link JoinAdmin}/{@link DestroyAdmin} are declared
 *       dispatch-only, so the exported client stub is {@link Administrable} but not
 *       {@code JoinAdmin}/{@code DestroyAdmin} — those reach the client through the
 *       separate admin facet from {@link AbstractJiniService#getAdmin()} (or an
 *       {@link ActivationExporter} wrapping one when {@code activationID} is
 *       non-null)</li>
 *   <li>{@code adminConstraints} ({@link MethodConstraints}, default {@code null})
 *       — when present, applied to the admin facet returned by
 *       {@link AbstractJiniService#getAdmin()} so administration can require
 *       stronger authentication than ordinary service calls, without a second
 *       endpoint</li>
 *   <li>{@code loginContext} ({@link LoginContext}, default {@code null})
 *       — when present, {@link AbstractJiniService#start()} performs a JAAS
 *       login and runs as the resulting {@link javax.security.auth.Subject};
 *       the service logs out when destroyed</li>
 *   <li>{@code initialLookupGroups} ({@code String[]}, default {@code {""}})
 *       — lookup groups to join</li>
 *   <li>{@code initialLookupLocators} ({@link LookupLocator}[], default
 *       empty) — lookup locators to join</li>
 *   <li>{@code initialLookupAttributes} ({@link Entry}[], default empty)
 *       — attributes advertised in lookup services</li>
 *   <li>{@code Codebase_Annotation} ({@link String}, default {@code ""})
 *       — codebase annotation for the proxy class</li>
 *   <li>{@code Codebase_CertFactoryType} ({@link String}, default
 *       {@code "X.509"})</li>
 *   <li>{@code Codebase_CertPathEncoding} ({@link String}, default
 *       {@code "PkiPath"})</li>
 *   <li>{@code Codebase_Certs} ({@code byte[]}, default empty)
 *       — DER-encoded certificate path</li>
 *   <li>{@code persistenceDirectory} ({@link String}, default {@code null})
 *       — directory path for persistent state (ServiceID + service-specific
 *       state via {@link AbstractJiniService#snapshot},
 *       {@link AbstractJiniService#recover}, and
 *       {@link AbstractJiniService#applyUpdate}); when {@code null} the
 *       service is non-persistent and generates a new ServiceID on every
 *       restart</li>
 * </ul>
 *
 * @author Peter Firmstone
 * @author GitHub Copilot
 * @since 3.1.1
 */
public abstract class JiniServiceParameters {

    /**
     * The <em>cast-and-dispatch</em> interface the default exporter forces onto
     * every exported service stub: {@link Administrable} only.  It is non-{@link
     * java.rmi.Remote Remote} (its {@code getAdmin} method declares {@code throws
     * RemoteException} but the interface does not extend {@code Remote}), so a JERI
     * dynamic proxy would not carry it unless it is appended explicitly.  Supplied
     * to {@link net.jini.jeri.DynamicILFactory} as the {@code castAndDispatch} set,
     * it joins BOTH the client service stub's cast set AND the server dispatcher —
     * the canonical shape: the service proxy is {@code Administrable} and
     * {@code Administrable.getAdmin()} returns a separate admin facet.
     *
     * @see net.jini.jeri.DynamicILFactory
     */
    private static final Class[] JINI_ADMIN_CAST_AND_DISPATCH = {
        Administrable.class
    };

    /**
     * The fallback <em>dispatch-only</em> admin set used when the concrete service
     * implementation class is not available to the shared classifier (the direct
     * {@link JiniServiceParameters} subclass path): {@link JoinAdmin} and
     * {@link DestroyAdmin}, the admin interfaces {@code AbstractJiniService} itself
     * always implements.  These are registered on the server invocation dispatcher
     * (so the {@code getAdmin()} facet can invoke them over the single export) but
     * stripped from the exported client service stub's cast set, giving a thin stub
     * that is {@link Administrable} but NOT {@code JoinAdmin}/{@code DestroyAdmin}.
     *
     * <p>When the impl class IS supplied (design decision D5 — see the
     * {@code serviceImpl}-carrying constructor) the per-service derived admin set
     * from {@link AbstractJiniService#classify(Class)} is used instead, so a custom
     * non-{@code Remote} admin interface (a {@code FooAdmin}) also dispatches.
     *
     * @see net.jini.jeri.DynamicILFactory#DynamicILFactory(net.jini.core.constraint.MethodConstraints, Class, ClassLoader, Class[], Class[])
     */
    private static final Class[] JINI_ADMIN_DISPATCH_ONLY_DEFAULT = {
        JoinAdmin.class, DestroyAdmin.class
    };

    /** Exporter used to export the service over the wire. */
    final Exporter exporter;

    /**
     * JAAS login context; {@code null} when no login is required.
     * When non-null, {@link AbstractJiniService#start()} calls
     * {@link LoginContext#login()} and runs as the resulting Subject.
     */
    final LoginContext loginContext;

    /** Lookup groups that the service will join. */
    final String[] lookupGroups;

    /** Lookup locators that the service will join. */
    final LookupLocator[] lookupLocators;

    /** Attributes published to Jini lookup services. */
    final Entry[] lookupAttributes;

    /** Codebase annotation; empty string means derive from the service interface. */
    final String codebaseAnnotation;

    /** X.509 certificate factory type identifier for {@link net.jini.export.CodebaseAccessor}. */
    final String certFactoryType;

    /** Certificate-path encoding for {@link net.jini.export.CodebaseAccessor}. */
    final String certPathEncoding;

    /** DER-encoded certificate path for {@link net.jini.export.CodebaseAccessor}. */
    final byte[] encodedCerts;

    /**
     * Directory for persistent state storage, or {@code null} for
     * non-persistent operation.  When non-null,
     * {@link AbstractJiniService} uses {@link org.apache.river.reliableLog.ReliableLog}
     * to persist the ServiceID (and any subclass state) across restarts.
     */
    final String persistDir;

    /**
     * Optional method constraints applied to the admin facet returned by
     * {@link AbstractJiniService#getAdmin()}, or {@code null} to inherit the
     * server reference's constraints unchanged.
     *
     * <p>The admin facet shares the service's single JERI export; by default it
     * carries whatever constraints the exported server reference carries.  When
     * this entry is present it is applied to the admin facet via
     * {@link net.jini.core.constraint.RemoteMethodControl#setConstraints} — letting
     * administration require <em>stronger</em> authentication than ordinary service
     * calls without a second endpoint.  Read from config entry {@code adminConstraints}.
     */
    final MethodConstraints adminConstraints;

    /**
     * Reads all common Jini service configuration entries, throwing
     * {@link ConfigurationException} immediately on any error.
     *
     * <p>Backward-compatible entry point for {@link JiniServiceParameters}
     * subclasses that name only the service API interface: the default exporter's
     * {@code dispatchOnly} admin set falls back to
     * {@link #JINI_ADMIN_DISPATCH_ONLY_DEFAULT} ({@link JoinAdmin} +
     * {@link DestroyAdmin}).  A service that also declares a custom non-{@code Remote}
     * admin interface must use the {@code serviceImpl}-carrying constructor (design
     * decision D5) so that interface flows into the dispatcher.
     *
     * @param config           the configuration to read from; must be non-null
     * @param component        the configuration component name; must be non-null
     * @param activationID     the Phoenix activation ID, or {@code null} for
     *                         non-activatable deployments
     * @param serviceInterface the primary remote interface of the service,
     *                         used to build a default exporter when
     *                         {@code serverExporter} is absent from config;
     *                         must be non-null
     * @throws ConfigurationException if any mandatory entry is missing,
     *                                of the wrong type, or otherwise invalid
     */
    protected JiniServiceParameters(Configuration config,
                                    String component,
                                    ActivationID activationID,
                                    Class<?> serviceInterface)
            throws ConfigurationException {
        this(config, component, activationID, serviceInterface, null);
    }

    /**
     * Reads all common Jini service configuration entries, deriving the default
     * exporter's per-service {@code dispatchOnly} admin set from {@code serviceImpl}
     * (design decision D5).
     *
     * <p>When {@code serviceImpl} is non-{@code null} the exporter's
     * {@code dispatchOnly} set is {@link AbstractJiniService#classify(Class)
     * classify(serviceImpl).admin} — {@link JoinAdmin}, {@link DestroyAdmin}, and
     * ANY custom non-{@code Remote} admin interface the impl declares (a
     * {@code FooAdmin}) — so admin methods for the entire derived set dispatch over
     * the single shared export.  When {@code serviceImpl} is {@code null} the set
     * falls back to {@link #JINI_ADMIN_DISPATCH_ONLY_DEFAULT}.
     *
     * @param config           the configuration to read from; must be non-null
     * @param component        the configuration component name; must be non-null
     * @param activationID     the Phoenix activation ID, or {@code null} for
     *                         non-activatable deployments
     * @param serviceInterface the primary remote interface of the service, used to
     *                         build a default exporter when {@code serverExporter}
     *                         is absent from config; must be non-null
     * @param serviceImpl      the concrete service implementation class whose
     *                         derived admin set becomes the exporter's dispatch-only
     *                         set, or {@code null} to fall back to
     *                         {@code {JoinAdmin, DestroyAdmin}}
     * @throws ConfigurationException if any mandatory entry is missing,
     *                                of the wrong type, or otherwise invalid
     */
    protected JiniServiceParameters(Configuration config,
                                    String component,
                                    ActivationID activationID,
                                    Class<?> serviceInterface,
                                    Class<?> serviceImpl)
            throws ConfigurationException {

        final Class[] dispatchOnly = (serviceImpl != null)
                ? AbstractJiniService.classify(serviceImpl).admin
                : JINI_ADMIN_DISPATCH_ONLY_DEFAULT;

        final Exporter defaultExporter;
        if (activationID != null) {
            defaultExporter = new ActivationExporter(
                    activationID,
                    new BasicJeriExporter(
                            TcpServerEndpoint.getInstance(0),
                            new DynamicILFactory(
                                    null, null,
                                    serviceInterface.getClassLoader(),
                                    JINI_ADMIN_CAST_AND_DISPATCH, dispatchOnly),
                            false, true));
        } else {
            defaultExporter = new BasicJeriExporter(
                    TcpServerEndpoint.getInstance(0),
                    new DynamicILFactory(
                            null, null,
                            serviceInterface.getClassLoader(),
                            JINI_ADMIN_CAST_AND_DISPATCH, dispatchOnly),
                    false, true);
        }

        this.exporter = (activationID != null)
                ? Config.getNonNullEntry(config, component, "serverExporter",
                        Exporter.class, defaultExporter, activationID)
                : Config.getNonNullEntry(config, component, "serverExporter",
                        Exporter.class, defaultExporter);

        this.loginContext = (LoginContext) config.getEntry(
                component, "loginContext", LoginContext.class, null);

        this.lookupGroups = Config.getNonNullEntry(
                config, component, "initialLookupGroups",
                String[].class, new String[]{""}).clone();

        this.lookupLocators = Config.getNonNullEntry(
                config, component, "initialLookupLocators",
                LookupLocator[].class, new LookupLocator[0]).clone();

        this.lookupAttributes = Config.getNonNullEntry(
                config, component, "initialLookupAttributes",
                Entry[].class, new Entry[0]).clone();

        this.codebaseAnnotation = Config.getNonNullEntry(
                config, component, "Codebase_Annotation", String.class, "");

        this.certFactoryType = Config.getNonNullEntry(
                config, component, "Codebase_CertFactoryType", String.class, "X.509");

        this.certPathEncoding = Config.getNonNullEntry(
                config, component, "Codebase_CertPathEncoding", String.class, "PkiPath");

        this.encodedCerts = Config.getNonNullEntry(
                config, component, "Codebase_Certs",
                byte[].class, new byte[0]).clone();

        this.persistDir = (String) config.getEntry(
                component, "persistenceDirectory", String.class, null);

        this.adminConstraints = (MethodConstraints) config.getEntry(
                component, "adminConstraints", MethodConstraints.class, null);
    }

}
