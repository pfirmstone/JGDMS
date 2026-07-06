/*
 *  Licensed to the Apache Software Foundation (ASF) under one or more
 *  contributor license agreements.  See the NOTICE file distributed with
 *  this work for additional information regarding copyright ownership.
 *  The ASF licenses this file to You under the Apache License, Version 2.0
 *  (the "License"); you may not use this file except in compliance with
 *  the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package au.net.zeus.jgdms.tool.serviceproxy;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.LinkedHashSet;
import java.util.Set;
import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.Filer;
import javax.annotation.processing.Messager;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedOptions;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import javax.tools.Diagnostic.Kind;
import javax.tools.JavaFileObject;

/**
 * Annotation processor that generates the mechanical service-proxy boilerplate
 * for {@link au.net.zeus.jgdms.service.annotation.JiniService @JiniService}
 * (and, in later phases,
 * {@link au.net.zeus.jgdms.service.annotation.SmartProxy @SmartProxy}) annotated
 * types, and <em>validates</em> the service-proxy contract by emitting
 * warnings/errors for classes that would produce a fail-open (non-constrainable)
 * or otherwise non-compliant proxy.  It does not modify the annotated types; it
 * only emits new source files, exactly like the sibling
 * {@code MarshalDelegateProcessor}.
 *
 * <p>The processor references the JGDMS API and the service-proxy annotations by
 * fully-qualified name (consumed through {@code javax.lang.model} at compile
 * time, independent of their retention — {@code @JiniService} is now
 * {@code RUNTIME}-retained so {@code AbstractJiniService} can reflect it), so it
 * depends only on the JDK's {@code java.compiler} module.
 *
 * <h2>Modes</h2>
 * <ul>
 *   <li><b>validate-only</b> ({@code -Aserviceproxy.validateOnly=true}) — emits
 *       the contract diagnostics of the design note's validation mode and
 *       generates nothing.  Independently useful: it would have caught the
 *       original {@code AbstractSmartProxy} round-trip flaws before generation
 *       existed.</li>
 *   <li><b>generate</b> (default) — shape-dispatched emission (JGDMS-STD-009
 *       {@code §6} + {@code §14} [RESOLVED]): the <b>backend</b> (internal wire)
 *       interface only when the smart proxy <em>translates</em> the API into a
 *       distinct protocol ({@code protocol() != api()}) and unless it is
 *       hand-written -- a do-nothing proxy ({@code protocol == api}, shapes 1 &amp; 2)
 *       needs none because the API interface is already the wire interface; and the
 *       single constrainable <b>proxy class</b> only when
 *       {@code proxy() == }{@link au.net.zeus.jgdms.service.annotation.ProxyType#SMART}
 *       (JGDMS-STD-009 §6, shape 3).  A {@code DYNAMIC} service (shapes 1 &amp; 2)
 *       is exported as a runtime {@link java.lang.reflect.Proxy} the client
 *       already holds the interface for, so it generates no proxy class -- and no
 *       server-side ILFactory either: its non-{@code Remote} admin interfaces are
 *       supplied to the exported stub by the reusable framework factory
 *       {@link net.jini.jeri.DynamicILFactory}, which the JGDMS service support
 *       installs by default (mirroring {@link net.jini.jeri.ProxyTrustILFactory}'s
 *       {@code ProxyTrust} append).  A {@code DYNAMIC} service with
 *       {@code protocol == api} therefore generates NOTHING and still gets admin
 *       dispatch with neither codegen nor config.  The {@code codebase} axis of the
 *       §6 shape dispatch (the interfaces-only {@code -dl} packaging of shape 2, and
 *       the SMART {@code -dl}-vs-shared packaging) is a follow-on task:
 *       {@code model.codebase()} is read and stored but not yet acted on.</li>
 * </ul>
 *
 * @see au.net.zeus.jgdms.service.annotation.JiniService
 * @since 3.1.1
 */
@SupportedAnnotationTypes({
    ServiceProxyProcessor.JINI_SERVICE,
    ServiceProxyProcessor.SMART_PROXY
})
@SupportedOptions("serviceproxy.validateOnly")
public final class ServiceProxyProcessor extends AbstractProcessor {

    static final String JINI_SERVICE = "au.net.zeus.jgdms.service.annotation.JiniService";
    static final String SMART_PROXY  = "au.net.zeus.jgdms.service.annotation.SmartProxy";

    static final String REMOTE = "java.rmi.Remote";
    static final String REMOTE_EXCEPTION = "java.rmi.RemoteException";
    static final String REMOTE_METHOD_CONTROL = "net.jini.core.constraint.RemoteMethodControl";
    static final String ABSTRACT_SMART_PROXY = "au.net.zeus.jgdms.proxy.AbstractSmartProxy";

    /**
     * The fixed set of JGDMS infrastructure interfaces a service backend
     * aggregates (mirrors {@code HelloServiceBackend} and Reggie's
     * {@code Registrar}).  Order is the emission order in the generated
     * {@code extends} clause; {@code Remote} and the public API precede these.
     *
     * <p>The set is grounded in
     * {@code au.net.zeus.jgdms.service.support.AbstractJiniService}, whose
     * {@code implements} clause enumerates exactly the interfaces a JGDMS service
     * exposes to callers: the {@code Remote} accessors
     * ({@code ServiceProxyAccessor}, {@code ServiceAttributesAccessor},
     * {@code ServiceIDAccessor}, {@code CodebaseAccessor}) plus the non-{@code Remote}
     * admin interfaces ({@code Administrable}, {@code JoinAdmin}, {@code DestroyAdmin}).
     * ({@code ProxyAccessor} and {@code Startable} from that clause are local
     * server-lifecycle SPIs, not caller-facing, and are deliberately excluded.)
     *
     * <p>For a DYNAMIC service the non-{@code Remote} admin interfaces are supplied
     * to the exported stub not by any generated code but by the reusable framework
     * factory {@link net.jini.jeri.DynamicILFactory}, which the JGDMS service
     * support installs by default (JGDMS-STD-009 §14 [RESOLVED]).
     */
    static final String[] BACKEND_INFRA = {
        "net.jini.lookup.ServiceProxyAccessor",
        "net.jini.lookup.ServiceAttributesAccessor",
        "net.jini.lookup.ServiceIDAccessor",
        "net.jini.export.CodebaseAccessor",
        "net.jini.admin.Administrable",
        "net.jini.admin.JoinAdmin",
        "org.apache.river.admin.DestroyAdmin"
    };

    private Elements elements;
    private Types types;
    private Messager messager;
    private Filer filer;
    private boolean validateOnly;

    @Override
    public SourceVersion getSupportedSourceVersion() {
        return SourceVersion.latestSupported();
    }

    @Override
    public synchronized void init(ProcessingEnvironment env) {
        super.init(env);
        elements = env.getElementUtils();
        types = env.getTypeUtils();
        messager = env.getMessager();
        filer = env.getFiler();
        // -Aserviceproxy.validateOnly (bare) or -Aserviceproxy.validateOnly=true
        // both enable validate-only; -Aserviceproxy.validateOnly=false disables it.
        var opts = env.getOptions();
        if (opts.containsKey("serviceproxy.validateOnly")) {
            String v = opts.get("serviceproxy.validateOnly");
            validateOnly = v == null || !"false".equalsIgnoreCase(v);
        } else {
            validateOnly = false;
        }
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment round) {
        // The delegate<->service linkage is EXPLICIT (ratified): a @JiniService names
        // its @SmartProxy client-side logic class with smartProxy(); there is no
        // api-set inference/matching.  The @JiniService loop resolves and validates
        // the referenced delegate; we collect the delegates it references so an
        // unreferenced @SmartProxy marker can be flagged with a warning.
        java.util.Set<TypeElement> referencedDelegates = new java.util.LinkedHashSet<>();
        TypeElement jini = elements.getTypeElement(JINI_SERVICE);
        if (jini != null) {
            for (Element e : round.getElementsAnnotatedWith(jini)) {
                // @JiniService belongs on the service IMPLEMENTATION class, not on
                // the API interface: proxy type, codebase, and config component are
                // implementation/deployment concerns, so the interface stays a pure
                // Remote contract.  Reject interface placement fail-closed.
                if (e.getKind() == ElementKind.INTERFACE) {
                    messager.printMessage(Kind.ERROR,
                        "@JiniService belongs on the service implementation, not the "
                        + "interface " + ((TypeElement) e).getQualifiedName()
                        + "; annotate the concrete AbstractJiniService subclass and name"
                        + " the API interface(s) with api().", e);
                    continue;
                }
                if (e.getKind() != ElementKind.CLASS) {
                    messager.printMessage(Kind.ERROR,
                        "@JiniService must annotate the service implementation class, not a "
                        + e.getKind().toString().toLowerCase() + ".", e);
                    continue;
                }
                try {
                    processJiniService((TypeElement) e, referencedDelegates);
                } catch (IOException ex) {
                    messager.printMessage(Kind.ERROR, "ServiceProxyProcessor: " + ex, e);
                }
            }
        }

        // A @SmartProxy marker no @JiniService points at (via smartProxy()) generates
        // no shell -- the shell needs the service's api/protocol context.  Warn, not
        // error (a delegate may legitimately be declared for a service compiled in a
        // different round/module).
        TypeElement smart = elements.getTypeElement(SMART_PROXY);
        if (smart != null) {
            for (Element e : round.getElementsAnnotatedWith(smart)) {
                if (e.getKind() != ElementKind.CLASS) {
                    messager.printMessage(Kind.ERROR,
                        "@SmartProxy must annotate a client-side logic delegate class.", e);
                    continue;
                }
                if (!referencedDelegates.contains(e)) {
                    messager.printMessage(Kind.WARNING,
                        "@SmartProxy class " + ((TypeElement) e).getQualifiedName()
                        + " is not referenced by any @JiniService.smartProxy() in this"
                        + " round; no proxy shell was generated for it.", e);
                }
            }
        }
        return false; // do not claim the annotations; allow other processors
    }

    // ---------------------------------------------------------------- @JiniService

    private void processJiniService(TypeElement impl,
                                    java.util.Set<TypeElement> referencedDelegates) throws IOException {
        ServiceModel model = ServiceModel.of(impl, elements, types, messager);
        if (model == null) {
            return; // api()/proxy could not be resolved; error already reported
        }
        validateJiniService(model);
        // Resolve + validate the @SmartProxy delegate named by smartProxy() (if any).
        // The delegate<->service linkage is explicit (ratified); validation is
        // fail-closed and runs in validate-only mode too.
        Delegate delegate = null;
        if (model.smartProxyElement != null) {
            referencedDelegates.add(model.smartProxyElement);
            delegate = validateAndBuildDelegate(model);
            if (delegate == null) {
                model.hadError = true; // a fail-closed diagnostic was reported
            }
        }
        if (validateOnly || model.hadError) {
            return;
        }
        // Shape dispatch (JGDMS-STD-009 §6 + §14 [RESOLVED]):
        //
        // (A) BACKEND (wire) interface -- generated ONLY when the smart proxy
        //     translates the API into a distinct internal protocol
        //     (protocol() != api()), and unless it is already hand-written.  A
        //     do-nothing proxy (protocol == api, §6 shapes 1 & 2) needs NO backend
        //     interface: the API interface is already the wire interface, so
        //     aggregating it under a named Remote super-interface buys nothing.
        //
        // (B) DYNAMIC admin dispatch -- a DYNAMIC service is exported as a runtime
        //     java.lang.reflect.Proxy the client already holds the interface for,
        //     so it emits no proxy class (shape 3 only).  It ALSO generates no
        //     server-side ILFactory: its full interface set -- API + non-Remote
        //     admin (Administrable/JoinAdmin/DestroyAdmin) -- is supplied at export
        //     by the reusable library factory {@link net.jini.jeri.DynamicILFactory},
        //     which the JGDMS service framework installs by default (§14
        //     [RESOLVED]).  DynamicILFactory overrides getRemoteInterfaces to append
        //     the extra (admin) interfaces to the exported stub, feeding BOTH its
        //     cast set and the server dispatch set
        //     (AbstractILFactory.getInvocationDispatcherMethods draws only from
        //     getRemoteInterfaces); the Remote accessors are already picked up by
        //     super.getRemoteInterfaces (Remote-filtered).  So a DYNAMIC service
        //     with protocol == api generates NOTHING -- no backend, no proxy, no
        //     ILFactory -- and gets admin dispatch with neither codegen nor config.
        //
        // (C) The constrainable proxy *class* is generated ONLY for a SMART
        //     service (shape 3).
        //
        // The codebase() flag is NOT acted on here: shape-2 (DYNAMIC + codebase)
        // interfaces-only -dl packaging and the SMART -dl-vs-shared-codebase
        // packaging are a deferred design pass.  model.codebase() is read and
        // stored but does not gate generation.  Wrapper (P4) and smart-proxy
        // shell (P3) generation land in later phases.
        // A backend (aggregate wire interface) is generated:
        //   (i)  TRANSLATING -- protocol != api (existing rule, DYNAMIC or SMART);
        //        OR
        //   (ii) COMBINING -- a SMART service whose wire set has MORE THAN ONE
        //        interface (multi-api thin, or multi-protocol translating).  The
        //        generated SMART proxy's create()/ctor need a SINGLE Java type to
        //        name the whole wire set; no single interface can, so the aggregate
        //        <Api>Backend is emitted and used as that type.  A DYNAMIC service
        //        generates no proxy, so it needs no combining backend (its exported
        //        java.lang.reflect.Proxy already carries every interface).
        // Either way, a hand-written backend of the conventional name suppresses
        // generation (the processor validates it instead).
        boolean combiningBackend =
                model.proxyType() == ServiceModel.ProxyType.SMART && model.protocol.size() > 1;
        if ((!model.protocolIsApi() || combiningBackend) && !model.backendHandWritten) {
            writeBackend(model);
        }
        if (model.proxyType() == ServiceModel.ProxyType.SMART) {
            if (model.proxyHandWritten == null) {
                // When @JiniService.smartProxy() names a delegate, the generated shell
                // forwards to it; otherwise the byte-identical direct-forwarding shell
                // is emitted.  Exactly one shell either way.
                writeProxy(model, delegate);
            }
        }
        // DYNAMIC (shapes 1 & 2): no proxy class and no ILFactory are generated;
        // the framework's default net.jini.jeri.DynamicILFactory supplies the
        // admin dispatch at export.
    }

    // ------------------------------------------------------------------ validation

    /**
     * Emits the design note's validation-mode diagnostics for the {@code @JiniService}
     * implementation and its resolved API interface; never modifies either.
     */
    private void validateJiniService(ServiceModel m) {
        // Every resolved service API type must be a Remote interface (a service
        // API whose methods cannot signal transport failure is malformed).
        for (TypeElement iface : m.apiInterfaces) {
            if (!isRemote(iface.asType())) {
                messager.printMessage(Kind.ERROR,
                    "@JiniService api() interface " + iface.getQualifiedName()
                    + " must extend java.rmi.Remote.", m.impl);
                m.hadError = true;
            }
        }
        for (ExecutableElement method : m.apiMethods) {
            if (!throwsRemoteException(method)) {
                Element decl = method.getEnclosingElement();
                String declName = (decl instanceof TypeElement)
                        ? ((TypeElement) decl).getSimpleName().toString()
                        : m.api.getSimpleName().toString();
                messager.printMessage(Kind.ERROR,
                    "@JiniService API method " + declName + "."
                    + method.getSimpleName()
                    + " must declare 'throws java.rmi.RemoteException'.", method);
                m.hadError = true;
            }
        }
        // If the backend interface already exists by hand, validate that it
        // aggregates the required infrastructure interfaces (a hand-written
        // backend missing one silently breaks admin-over-wire).
        if (m.backendHandWritten) {
            validateBackendInterface(m);
        }
        // If a proxy already exists by hand, validate its constrainability and
        // its forwarding-method coverage.
        if (m.proxyHandWritten != null) {
            validateProxyClass(m);
        }
    }

    /** A hand-written backend must extend Remote and every required infra interface. */
    private void validateBackendInterface(ServiceModel m) {
        TypeElement backend = m.proxyHandWrittenBackend;
        if (backend == null) {
            return;
        }
        if (!isRemote(backend.asType())) {
            messager.printMessage(Kind.ERROR,
                "Backend interface " + backend.getQualifiedName()
                + " must extend java.rmi.Remote.", backend);
        }
        for (String infra : BACKEND_INFRA) {
            if (!implementsType(backend.asType(), infra)) {
                messager.printMessage(Kind.ERROR,
                    "Backend interface " + backend.getQualifiedName()
                    + " is missing required infrastructure interface " + infra
                    + "; omitting it silently breaks admin-over-wire.", backend);
            }
        }
        for (TypeElement iface : m.apiInterfaces) {
            if (!implementsType(backend.asType(), iface.getQualifiedName().toString())) {
                messager.printMessage(Kind.ERROR,
                    "Backend interface " + backend.getQualifiedName()
                    + " must aggregate the public API interface " + iface.getQualifiedName()
                    + ".", backend);
            }
        }
    }

    /** A hand-written proxy must be constrainable and cover every API method. */
    private void validateProxyClass(ServiceModel m) {
        TypeElement proxy = m.proxyHandWritten;
        // Constrainability: a concrete proxy must reach ConstrainableSmartProxy
        // (hence RemoteMethodControl) -- a concrete proxy extending
        // AbstractSmartProxy directly is the old fail-open plain variant.
        boolean concrete = !proxy.getModifiers().contains(Modifier.ABSTRACT);
        if (concrete && extendsAbstractSmartProxy(proxy)
                && !implementsType(proxy.asType(), REMOTE_METHOD_CONTROL)) {
            messager.printMessage(Kind.ERROR,
                "Proxy " + proxy.getQualifiedName()
                + " is a concrete non-constrainable smart proxy (does not implement "
                + REMOTE_METHOD_CONTROL + "); the client cannot impose "
                + "Integrity/ServerAuthentication/Confidentiality and the proxy cannot "
                + "participate in proxy-trust verification.  Make it extend "
                + "AbstractSmartProxy.ConstrainableSmartProxy.", proxy);
        }
        // Forwarding coverage: every API method must be present on the proxy.
        Set<String> proxyMethods = new LinkedHashSet<>();
        for (Element e : elements.getAllMembers(proxy)) {
            if (e.getKind() == ElementKind.METHOD) {
                proxyMethods.add(signature((ExecutableElement) e));
            }
        }
        for (ExecutableElement method : m.apiMethods) {
            if (!proxyMethods.contains(signature(method))) {
                messager.printMessage(Kind.WARNING,
                    "Proxy " + proxy.getSimpleName() + " is missing a forwarding method for "
                    + "API method " + m.api.getSimpleName() + "." + signature(method)
                    + "; a call would not reach the server.", proxy);
            }
        }
    }

    /**
     * Resolves and validates (fail-closed) the {@code @SmartProxy} delegate named by
     * {@code @JiniService.smartProxy()} for a SMART service, returning a
     * {@link Delegate} the shell can forward to, or {@code null} (after reporting an
     * error) when it is unusable.  Validation:
     * <ul>
     *   <li>the referenced class is annotated {@code @SmartProxy} (the marker);</li>
     *   <li>it implements <em>every</em> {@code api()} interface (the shell forwards
     *       the whole api method set to it);</li>
     *   <li>it declares a constructor {@code (serverType[, @State types...])} the
     *       generated shell can call (see {@link #validateDelegateCtor}).</li>
     * </ul>
     */
    private Delegate validateAndBuildDelegate(ServiceModel m) {
        TypeElement delegate = m.smartProxyElement;
        boolean ok = true;
        // (a) marker check.
        if (!hasAnnotation(delegate, SMART_PROXY)) {
            messager.printMessage(Kind.ERROR,
                "@JiniService.smartProxy() class " + delegate.getQualifiedName()
                + " must be annotated @" + SMART_PROXY + ".", m.impl);
            ok = false;
        }
        // (b) implements every api interface.
        for (TypeElement apiIface : m.apiInterfaces) {
            if (!implementsType(delegate.asType(), apiIface.getQualifiedName().toString())) {
                messager.printMessage(Kind.ERROR,
                    "@SmartProxy class " + delegate.getQualifiedName()
                    + " must implement the api interface " + apiIface.getQualifiedName()
                    + " named by @JiniService.api().", m.impl);
                ok = false;
            }
        }
        // (c) durable @State fields + the (serverType[, state]) ctor.
        java.util.List<StateField> states = readStates(delegate);
        if (!validateDelegateCtor(m, delegate, states)) {
            ok = false;
        }
        return ok ? new Delegate(delegate, states) : null;
    }

    // ------------------------------------------------------------------ generation

    /** Emits {@code <Api>Backend} into the API interface's package. */
    private void writeBackend(ServiceModel m) throws IOException {
        String pkg = elements.getPackageOf(m.api).getQualifiedName().toString();
        String simple = m.backendSimpleName;
        String fqn = pkg.isEmpty() ? simple : pkg + "." + simple;

        StringBuilder b = new StringBuilder();
        b.append("// Generated by ").append(getClass().getName()).append(" -- do not edit.\n");
        if (!pkg.isEmpty()) {
            b.append("package ").append(pkg).append(";\n\n");
        }
        b.append("/**\n")
         .append(" * Server-side backend (internal wire) interface for ")
         .append(m.api.getSimpleName()).append(", generated by the service-proxy\n")
         .append(" * annotation processor.  It aggregates the internal wire (protocol) contract\n")
         .append(" * -- the interface the exported server stub actually implements -- with the\n")
         .append(" * fixed set of JGDMS infrastructure interfaces every exported service stub must\n")
         .append(" * expose, so a JERI stub for this interface transitively carries them all.\n")
         .append(" *\n")
         .append(" * @see ").append(m.api.getQualifiedName()).append('\n')
         .append(" */\n");
        b.append("public interface ").append(simple).append("\n")
         .append("        extends ").append(REMOTE);
        // Aggregate the internal wire PROTOCOL set, not the public api (JGDMS-STD-009
        // §4, "Reggie Registrar vs ServiceRegistrar"): in the translating case the
        // service IMPL implements only the wire/protocol interface(s), so the
        // exported JERI stub -- and therefore this backend it carries -- exposes the
        // wire contract plus the infra; the disjoint public api is advertised only
        // via the downloaded smart proxy.  In the COMBINING case (multi-element thin
        // wire set, protocol == api) m.protocol holds the api interface types, so the
        // backend legitimately aggregates them (thin: the api IS the wire).  Either
        // way m.protocol is the resolved wire set (never empty).
        for (TypeMirror wire : m.protocol) {
            b.append(",\n                ").append(typeName(wire));
        }
        for (String infra : BACKEND_INFRA) {
            b.append(",\n                ").append(infra);
        }
        b.append(" {\n}\n");

        JavaFileObject src = filer.createSourceFile(fqn, m.impl);
        try (PrintWriter pw = new PrintWriter(src.openWriter())) {
            pw.print(b);
        }
    }

    /**
     * Emits the single, constrainable {@code Constrainable<Api>Proxy} into the
     * API interface's package (thin, one-to-one forwarding case).  The generated
     * proxy is {@code @AtomicSerial @Stateless}, extends
     * {@code AbstractSmartProxy.ConstrainableSmartProxy}, implements the public
     * API, and carries the fail-closed {@code create} factory (SOW 2.1): it
     * throws rather than degrading to a plain proxy when the {@code server}
     * reference is not a {@code RemoteMethodControl}.
     */
    private void writeProxy(ServiceModel m, Delegate delegate) throws IOException {
        String pkg = elements.getPackageOf(m.api).getQualifiedName().toString();
        String api = m.api.getQualifiedName().toString();
        String apiSimple = m.api.getSimpleName().toString();
        // P3: the delegate (if any) owns the forwarding behaviour.  A delegate with
        // @State declarations makes the shell STATEFUL: it declares its own serial
        // form for the durable fields over the @Stateless ConstrainableSmartProxy
        // base (the {server, proxyID} state lives in AbstractSmartProxy's frame; the
        // shell reads its own state fields from its own GetArg frame, exactly as
        // ConstrainableRegistrarProxy layers `constraints` over RegistrarProxy).
        String delegateFqn = delegate == null ? null
                : delegate.element.getQualifiedName().toString();
        java.util.List<StateField> states = delegate == null
                ? java.util.Collections.emptyList() : delegate.states;
        boolean stateful = !states.isEmpty();
        // Extra ctor/create parameters and pass-through arguments for the durable
        // @State fields (empty in the stateless case, keeping the direct-forwarding
        // output byte-identical).
        StringBuilder stateParams = new StringBuilder();
        StringBuilder stateArgs = new StringBuilder();
        for (StateField sf : states) {
            stateParams.append(", ").append(sf.type).append(' ').append(sf.name);
            stateArgs.append(", ").append(sf.name);
        }
        // The proxy IMPLEMENTS the public api (its client-facing forwarding
        // methods) but its create()/ctor server parameter is typed as the wire
        // set, because the deserialized/exported server stub is a wire instance
        // (the impl implements only the wire interface(s) -- JGDMS-STD-009 §4):
        //
        //   - a wire set of exactly ONE interface is named by that single interface
        //     (thin single-api -> the api; translating single-protocol -> the
        //     protocol), keeping the emitted text byte-identical to the pre-array
        //     generator;
        //   - a wire set of MORE THAN ONE interface has no single Java type able to
        //     name it, so the generated aggregate <Api>Backend (which extends every
        //     wire interface + infra) is used as the server type.
        //
        // Keying create/ctor off this server type keeps the whole chain (the
        // setConstraints `(serverType) server` cast, the forwarding casts, and the
        // @SmartProxy delegate ctor which likewise takes the wire type) type-
        // consistent for both the thin and the translating cases.
        String serverType = serverType(m);
        String simple = "Constrainable" + apiSimple + "Proxy";
        String fqn = pkg.isEmpty() ? simple : pkg + "." + simple;

        StringBuilder b = new StringBuilder();
        b.append("// Generated by ").append(getClass().getName()).append(" -- do not edit.\n");
        if (!pkg.isEmpty()) {
            b.append("package ").append(pkg).append(";\n\n");
        }
        b.append("/**\n")
         .append(" * The single, constrainable client-side proxy for ").append(apiSimple)
         .append(", generated by\n")
         .append(" * the service-proxy annotation processor.  It is the ONLY concrete wire proxy\n")
         .append(" * form: there is no non-constrainable variant, so a client that requested\n")
         .append(" * Integrity/ServerAuthentication/Confidentiality can never be silently handed a\n")
         .append(" * downgraded proxy.  Use {@link #create} rather than constructing directly.\n")
         .append(" *\n");
        if (stateful) {
            b.append(" * <p>Durable {@code @State} field(s) are serialized and passed to the delegate\n")
             .append(" * constructor UNVALIDATED by this shell: the delegate constructor is the\n")
             .append(" * validation seam -- it must throw (e.g. IllegalArgumentException, or\n")
             .append(" * InvalidObjectException for a corrupt stream) to reject a bad or hostile value.\n")
             .append(" *\n");
        }
        b.append(" * @see ").append(api).append('\n')
         .append(" * @see au.net.zeus.jgdms.proxy.AbstractSmartProxy\n")
         .append(" */\n");
        b.append("@org.apache.river.api.io.AtomicSerial\n");
        // A stateful (@State) shell declares its OWN serial form for the durable
        // fields, so it is NOT @Stateless; a delegate-less or state-less shell stays
        // @Stateless (the base owns {server, proxyID}).
        if (!stateful) {
            b.append("@org.apache.river.api.io.AtomicSerial.Stateless\n");
        }
        b.append("public final class ").append(simple).append("\n")
         .append("        extends ").append(ABSTRACT_SMART_PROXY).append(".ConstrainableSmartProxy\n")
         .append("        implements ");
        // Implement EVERY api interface (JGDMS-STD-009 §3.1/§4), not just the
        // primary -- the proxy is a stand-in for the whole service API.
        for (int i = 0; i < m.apiInterfaces.size(); i++) {
            if (i > 0) {
                b.append(", ");
            }
            b.append(m.apiInterfaces.get(i).getQualifiedName());
        }
        b.append(" {\n\n");
        b.append("    private static final long serialVersionUID = 1L;\n\n");

        // P3 delegate: client-side behaviour, NOT serialized -- rebuilt from the
        // deserialized (validated) server in every constructor.  The AtomicSerial
        // output engine marshals only the fields named by serialForm()/serialize()
        // (it does not reflect over instance fields), so `delegate` is never written
        // regardless; `transient` documents that intent and keeps it correct under a
        // JOSS-style engine that WOULD reflect.
        if (delegate != null) {
            b.append("    private transient final ").append(delegateFqn)
             .append(" delegate;\n\n");
        }
        // P3.2 durable proxy state: fields the shell serializes (declared serial
        // form over the @Stateless base) and passes to the delegate ctor after
        // server.
        if (stateful) {
            for (StateField sf : states) {
                b.append("    private final ").append(sf.type).append(' ')
                 .append(sf.name).append(";\n");
            }
            b.append('\n');
            b.append("    /** Serial form for the durable @State fields (over the "
                    + "@Stateless base's {server, proxyID}). */\n");
            b.append("    public static org.apache.river.api.io.AtomicSerial.SerialForm[] serialForm() {\n");
            b.append("        return new org.apache.river.api.io.AtomicSerial.SerialForm[]{\n");
            for (int i = 0; i < states.size(); i++) {
                StateField sf = states.get(i);
                b.append("            new org.apache.river.api.io.AtomicSerial.SerialForm(\"")
                 .append(sf.name).append("\", ").append(sf.type).append(".class)");
                b.append(i < states.size() - 1 ? ",\n" : "\n");
            }
            b.append("        };\n");
            b.append("    }\n\n");
            b.append("    /** Writes the durable @State fields declared by {@link #serialForm()}. */\n");
            b.append("    public static void serialize(org.apache.river.api.io.AtomicSerial.PutArg arg, ")
             .append(simple).append(" obj) throws java.io.IOException {\n");
            for (StateField sf : states) {
                b.append("        arg.put(\"").append(sf.name).append("\", obj.")
                 .append(sf.name).append(");\n");
            }
            b.append("        arg.writeArgs();\n");
            b.append("    }\n\n");
        }

        // Fail-closed factory (SOW 2.1).
        b.append("    /**\n")
         .append("     * Factory -- ALWAYS returns a {@link ").append(simple).append("}.\n")
         .append("     *\n")
         .append("     * <p>Fails closed: if {@code server} does not implement\n")
         .append("     * {@link ").append(REMOTE_METHOD_CONTROL).append("} the service was not exported with a\n")
         .append("     * constrainable endpoint, so no secure proxy can be produced and this method\n")
         .append("     * throws rather than silently returning a plain proxy that would drop the\n")
         .append("     * client's security constraints.  The stub's existing constraints are\n")
         .append("     * preserved (passing null would call setConstraints(null) and discard them).\n")
         .append("     *\n")
         .append("     * @param server  the remote server stub; must not be null\n")
         .append("     * @param proxyID the service's stable unique identifier; must not be null\n")
         .append("     * @return the constrainable proxy instance\n")
         .append("     * @throws IllegalArgumentException if {@code server} is not a\n")
         .append("     *         {@link ").append(REMOTE_METHOD_CONTROL).append("}\n")
         .append("     */\n");
        b.append("    public static ").append(ABSTRACT_SMART_PROXY)
         .append(" create(").append(serverType).append(" server, net.jini.id.Uuid proxyID")
         .append(stateParams).append(") {\n");
        b.append("        if (!(server instanceof ").append(REMOTE_METHOD_CONTROL).append(")) {\n");
        b.append("            throw new IllegalArgumentException(\n");
        b.append("                \"service must be exported with a constrainable endpoint: \"\n");
        b.append("                + \"server does not implement RemoteMethodControl\");\n");
        b.append("        }\n");
        b.append("        net.jini.core.constraint.MethodConstraints serverConstraints =\n");
        b.append("                ((").append(REMOTE_METHOD_CONTROL).append(") server).getConstraints();\n");
        b.append("        return new ").append(simple)
         .append("(server, proxyID, serverConstraints").append(stateArgs).append(");\n");
        b.append("    }\n\n");

        // Public (server, proxyID, constraints) ctor.
        b.append("    /**\n")
         .append("     * Creates a constrainable proxy applying {@code constraints} to the stub.\n")
         .append("     *\n")
         .append("     * @param server      the remote server stub\n")
         .append("     * @param proxyID     the service's stable unique identifier\n")
         .append("     * @param constraints per-method constraints, or {@code null}\n")
         .append("     */\n");
        b.append("    public ").append(simple).append("(").append(serverType)
         .append(" server, net.jini.id.Uuid proxyID,\n")
         .append("            net.jini.core.constraint.MethodConstraints constraints")
         .append(stateParams).append(") {\n");
        b.append("        super(server, proxyID, constraints);\n");
        for (StateField sf : states) {
            b.append("        this.").append(sf.name).append(" = ").append(sf.name).append(";\n");
        }
        if (delegate != null) {
            // Build from the POST-super `this.server` field, NOT the ctor parameter:
            // ConstrainableSmartProxy(server, proxyID, constraints) stores the
            // CONSTRAINT-TRANSFORMED stub (server.setConstraints(constraints)) in the
            // field, so the parameter still holds the un-constrained stub.  Forwarding
            // through the parameter would leak calls under the OLD constraints while
            // getConstraints() reports the new ones (silent downgrade).
            b.append("        this.delegate = new ").append(delegateFqn)
             .append("((").append(serverType).append(") this.server").append(stateArgs).append(");\n");
        }
        b.append("    }\n\n");

        // AtomicSerial (GetArg) ctor.
        b.append("    /**\n")
         .append("     * {@link org.apache.river.api.io.AtomicSerial} deserialization constructor.\n")
         .append("     *\n")
         .append("     * @param arg the deserialization argument bag\n")
         .append("     * @throws java.io.IOException            if deserialization validation fails\n")
         .append("     * @throws ClassNotFoundException if a required class cannot be found\n")
         .append("     */\n");
        b.append("    public ").append(simple)
         .append("(org.apache.river.api.io.AtomicSerial.GetArg arg)\n")
         .append("            throws java.io.IOException, ClassNotFoundException {\n");
        b.append("        super(arg);\n");
        // super(arg) has read+validated {server, proxyID} from AbstractSmartProxy's
        // own frame.  Each @State field is read from THIS class's own GetArg frame
        // (caller-class dispatch resolves it to this leaf's fields), then the
        // behaviour delegate is rebuilt from the validated inherited `server`.
        for (StateField sf : states) {
            String primDefault = primitiveDefaultLiteral(sf.type);
            if (primDefault != null) {
                // Primitive @State: the atomic engine boxes the field, so the typed
                // 3-arg get(name, null, int.class) would fail (int.class.isInstance of
                // a boxed Integer is always false) and the null default would NPE on
                // unbox.  Use the primitive-typed overload get(name, <default>) instead
                // (mirrors ParticipantHandle's `arg.get("prepstate", 0)` idiom).
                b.append("        this.").append(sf.name).append(" = arg.get(\"").append(sf.name)
                 .append("\", ").append(primDefault).append(");\n");
            } else {
                b.append("        this.").append(sf.name).append(" = arg.get(\"").append(sf.name)
                 .append("\", null, ").append(sf.type).append(".class);\n");
            }
        }
        if (delegate != null) {
            // Build from the POST-super `this.server` field, NOT the ctor parameter:
            // ConstrainableSmartProxy(server, proxyID, constraints) stores the
            // CONSTRAINT-TRANSFORMED stub (server.setConstraints(constraints)) in the
            // field, so the parameter still holds the un-constrained stub.  Forwarding
            // through the parameter would leak calls under the OLD constraints while
            // getConstraints() reports the new ones (silent downgrade).
            b.append("        this.delegate = new ").append(delegateFqn)
             .append("((").append(serverType).append(") this.server").append(stateArgs).append(");\n");
        }
        b.append("    }\n\n");

        // setConstraints returning a re-constrained copy via getReferentUuid().
        b.append("    @Override\n");
        b.append("    public ").append(REMOTE_METHOD_CONTROL)
         .append(" setConstraints(net.jini.core.constraint.MethodConstraints constraints) {\n");
        b.append("        return new ").append(simple).append("(\n");
        b.append("                (").append(serverType).append(") server, getReferentUuid(), constraints")
         .append(stateArgs).append(");\n");
        b.append("    }\n");

        // Forwarding methods -- every public API method across all api interfaces,
        // delegated to server.  For the one-to-one case (protocol == api) each
        // method is dispatched through the interface that DECLARES it, so a
        // multi-interface service forwards each interface's methods correctly
        // (casting all to a single api would not compile when a method belongs to
        // a sibling interface).  For a translating smart proxy (protocol != api)
        // every method is forwarded through the distinct internal wire protocol.
        for (ExecutableElement method : m.apiMethods) {
            b.append('\n');
            b.append("    @Override\n");
            b.append("    ").append(renderMethodSignature(method)).append(" {\n");
            if (delegate != null) {
                // The delegate implements the public api, so forwarding to it
                // compiles even when the wire method name differs (genuinely
                // disjoint translation); the delegate internally translates to the
                // protocol.
                b.append("        ").append(renderDelegateForwardingBody(method)).append('\n');
            } else {
                String castType = m.protocolIsApi() ? declaringTypeName(method) : serverType;
                b.append("        ").append(renderForwardingBody(method, castType)).append('\n');
            }
            b.append("    }\n");
        }

        b.append("}\n");

        JavaFileObject src = filer.createSourceFile(fqn, m.impl);
        try (PrintWriter pw = new PrintWriter(src.openWriter())) {
            pw.print(b);
        }
    }

    /**
     * Renders a faithful override signature for {@code method}: modifiers
     * ({@code public}), type parameters, return type, name, parameter list
     * (varargs preserved), and the {@code throws} clause -- all fully qualified,
     * so the emitted source needs no imports.  Signature fidelity is the real
     * work (SOW 9); {@code javax.lang.model} supplies it.
     */
    private String renderMethodSignature(ExecutableElement method) {
        StringBuilder sb = new StringBuilder("public ");
        // Type parameters.
        var typeParams = method.getTypeParameters();
        if (!typeParams.isEmpty()) {
            sb.append('<');
            boolean first = true;
            for (var tp : typeParams) {
                if (!first) {
                    sb.append(", ");
                }
                first = false;
                sb.append(tp.getSimpleName());
                var bounds = tp.getBounds();
                boolean nonObject = bounds.size() != 1 || !"java.lang.Object".equals(typeName(bounds.get(0)));
                if (nonObject) {
                    sb.append(" extends ");
                    boolean b1 = true;
                    for (TypeMirror bound : bounds) {
                        if (!b1) {
                            sb.append(" & ");
                        }
                        b1 = false;
                        sb.append(bound.toString());
                    }
                }
            }
            sb.append("> ");
        }
        sb.append(method.getReturnType().toString()).append(' ');
        sb.append(method.getSimpleName()).append('(');
        var params = method.getParameters();
        for (int i = 0; i < params.size(); i++) {
            if (i > 0) {
                sb.append(", ");
            }
            VariableElement p = params.get(i);
            TypeMirror pt = p.asType();
            if (method.isVarArgs() && i == params.size() - 1
                    && pt.getKind() == TypeKind.ARRAY) {
                // Render the trailing array parameter as a varargs.
                String comp = ((javax.lang.model.type.ArrayType) pt).getComponentType().toString();
                sb.append(comp).append("... ").append(p.getSimpleName());
            } else {
                sb.append(pt.toString()).append(' ').append(p.getSimpleName());
            }
        }
        sb.append(')');
        var thrown = method.getThrownTypes();
        if (!thrown.isEmpty()) {
            sb.append(" throws ");
            for (int i = 0; i < thrown.size(); i++) {
                if (i > 0) {
                    sb.append(", ");
                }
                sb.append(thrown.get(i).toString());
            }
        }
        return sb.toString();
    }

    /**
     * The single Java type that names the generated SMART proxy's {@code server}
     * (the create()/ctor parameter, the setConstraints and translating-forwarding
     * cast target):
     * <ul>
     *   <li>a wire set of exactly ONE interface is named by that interface (thin
     *       single-api → the api; translating single-protocol → the protocol),
     *       keeping the emitted proxy byte-identical to the pre-array generator;</li>
     *   <li>a wire set of MORE THAN ONE interface has no single interface able to
     *       name it, so the aggregate {@code <Api>Backend} (which extends every wire
     *       interface + infra, and which is generated or hand-written) is used.</li>
     * </ul>
     */
    private String serverType(ServiceModel m) {
        if (m.protocol.size() == 1) {
            return typeName(m.protocol.get(0));
        }
        if (m.proxyHandWrittenBackend != null) {
            return m.proxyHandWrittenBackend.getQualifiedName().toString();
        }
        String pkg = elements.getPackageOf(m.api).getQualifiedName().toString();
        return pkg.isEmpty() ? m.backendSimpleName : pkg + "." + m.backendSimpleName;
    }

    /**
     * The fully-qualified declaring interface of {@code method} (its enclosing
     * type) — the cast target used to forward one API method through the interface
     * that declares it, so a multi-interface service dispatches each method
     * correctly.  API methods always have a {@code TypeElement} enclosing type.
     */
    private String declaringTypeName(ExecutableElement method) {
        Element enclosing = method.getEnclosingElement();
        if (enclosing instanceof TypeElement) {
            return ((TypeElement) enclosing).getQualifiedName().toString();
        }
        return enclosing == null ? "java.lang.Object" : enclosing.toString();
    }

    /** The forwarding body: {@code return ((CastType) server).name(args);} (or no return for void). */
    private String renderForwardingBody(ExecutableElement method, String castType) {
        StringBuilder sb = new StringBuilder();
        boolean isVoid = method.getReturnType().getKind() == TypeKind.VOID;
        if (!isVoid) {
            sb.append("return ");
        }
        sb.append("((").append(castType).append(") server).")
          .append(method.getSimpleName()).append('(');
        var params = method.getParameters();
        for (int i = 0; i < params.size(); i++) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append(params.get(i).getSimpleName());
        }
        sb.append(");");
        return sb.toString();
    }

    /** The forwarding body through the delegate: {@code return delegate.name(args);}
     *  (or no return for void).  The delegate implements the public api, so this
     *  compiles even for a disjoint api/protocol method-name translation. */
    private String renderDelegateForwardingBody(ExecutableElement method) {
        StringBuilder sb = new StringBuilder();
        boolean isVoid = method.getReturnType().getKind() == TypeKind.VOID;
        if (!isVoid) {
            sb.append("return ");
        }
        sb.append("delegate.").append(method.getSimpleName()).append('(');
        var params = method.getParameters();
        for (int i = 0; i < params.size(); i++) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append(params.get(i).getSimpleName());
        }
        sb.append(");");
        return sb.toString();
    }

    // ------------------------------------------------- @SmartProxy delegate support

    /** A resolved {@code @SmartProxy} delegate plus its ordered {@code @State} fields. */
    private static final class Delegate {
        final TypeElement element;
        final java.util.List<StateField> states;
        Delegate(TypeElement element, java.util.List<StateField> states) {
            this.element = element;
            this.states = states;
        }
    }

    /** A durable {@code @State} field: a serialized name plus its declared type
     *  (fully-qualified so the emitted source needs no imports). */
    private static final class StateField {
        final String name;
        final String type;
        StateField(String name, String type) {
            this.name = name;
            this.type = type;
        }
    }

    /** True iff {@code e} carries the annotation named {@code annFqn}. */
    private boolean hasAnnotation(Element e, String annFqn) {
        for (javax.lang.model.element.AnnotationMirror am : e.getAnnotationMirrors()) {
            if (isType(am.getAnnotationType(), annFqn)) {
                return true;
            }
        }
        return false;
    }

    /**
     * The primitive-typed default literal for a primitive {@code @State} field type
     * name (so the generated {@code (GetArg)} ctor can call the primitive
     * {@code get(name, default)} overload), or {@code null} when the type is a
     * reference type (which uses the typed 3-arg {@code get}).
     */
    private String primitiveDefaultLiteral(String type) {
        switch (type) {
            case "boolean": return "false";
            case "byte":    return "(byte) 0";
            case "char":    return "(char) 0";
            case "short":   return "(short) 0";
            case "int":     return "0";
            case "long":    return "0L";
            case "float":   return "0.0F";
            case "double":  return "0.0D";
            default:        return null;
        }
    }

    /**
     * Reads the delegate's {@code @SmartProxy.State}/{@code @States} declarations, in
     * source (constructor-parameter) order: each contributes a durable field the
     * shell serializes and passes to the delegate ctor after {@code server}.
     */
    private java.util.List<StateField> readStates(TypeElement delegate) {
        java.util.List<StateField> out = new java.util.ArrayList<>();
        String stateFqn  = SMART_PROXY + ".State";
        String statesFqn = SMART_PROXY + ".States";
        for (javax.lang.model.element.AnnotationMirror am : delegate.getAnnotationMirrors()) {
            String at = typeName(am.getAnnotationType());
            if (statesFqn.equals(at)) {
                for (var en : am.getElementValues().entrySet()) {
                    if (!en.getKey().getSimpleName().contentEquals("value")) {
                        continue;
                    }
                    Object v = en.getValue().getValue();
                    if (v instanceof java.util.List<?>) {
                        for (Object o : (java.util.List<?>) v) {
                            if (o instanceof javax.lang.model.element.AnnotationValue) {
                                Object inner = ((javax.lang.model.element.AnnotationValue) o).getValue();
                                if (inner instanceof javax.lang.model.element.AnnotationMirror) {
                                    StateField sf = readState(
                                        (javax.lang.model.element.AnnotationMirror) inner);
                                    if (sf != null) {
                                        out.add(sf);
                                    }
                                }
                            }
                        }
                    }
                }
            } else if (stateFqn.equals(at)) {
                StateField sf = readState(am);
                if (sf != null) {
                    out.add(sf);
                }
            }
        }
        return out;
    }

    /** Reads one {@code @SmartProxy.State} mirror into a {@link StateField}. */
    private StateField readState(javax.lang.model.element.AnnotationMirror stateMirror) {
        String name = null;
        String type = null;
        for (var en : elements.getElementValuesWithDefaults(stateMirror).entrySet()) {
            String member = en.getKey().getSimpleName().toString();
            Object value = en.getValue().getValue();
            if ("name".equals(member) && value != null) {
                name = value.toString();
            } else if ("type".equals(member) && value instanceof TypeMirror) {
                type = typeName((TypeMirror) value);
            }
        }
        if (name == null || name.isEmpty() || type == null) {
            return null;
        }
        return new StateField(name, type);
    }

    /**
     * The {@code server} wire type as a {@code TypeMirror} when it is a single
     * protocol interface (the {@link #serverType(ServiceModel)} single-interface
     * case), else {@code null} (the aggregate {@code <Api>Backend}, which is
     * generated in this same round and cannot be resolved as an element here).
     */
    private TypeMirror serverTypeMirror(ServiceModel m) {
        return m.protocol.size() == 1 ? m.protocol.get(0) : null;
    }

    /**
     * Validates (fail-closed) that the delegate declares a constructor
     * {@code (serverType [, state types...])} the generated shell can call.  The
     * first parameter must accept the wire {@code serverType} (a supertype of it);
     * the remaining parameters must match the {@code @State} types in order.  When
     * the server type is the aggregate backend (not resolvable here), only the
     * parameter count and the trailing state types are checked.
     *
     * @return {@code true} if a compatible ctor exists (an error was reported and
     *         {@code false} returned otherwise)
     */
    private boolean validateDelegateCtor(ServiceModel m, TypeElement delegate,
                                         java.util.List<StateField> states) {
        int expected = 1 + states.size();
        for (Element e : delegate.getEnclosedElements()) {
            if (e.getKind() != ElementKind.CONSTRUCTOR) {
                continue;
            }
            ExecutableElement ctor = (ExecutableElement) e;
            var params = ctor.getParameters();
            if (params.size() != expected) {
                continue;
            }
            // First parameter must accept the wire server type the shell passes.
            if (!firstParamAcceptsServer(m, params.get(0).asType())) {
                continue;
            }
            // Remaining parameters must match the @State types (erased name compare).
            boolean statesMatch = true;
            for (int i = 0; i < states.size(); i++) {
                String want = states.get(i).type;
                String got = typeName(types.erasure(params.get(i + 1).asType()));
                if (!want.equals(got)) {
                    statesMatch = false;
                    break;
                }
            }
            if (statesMatch) {
                return true;
            }
        }
        StringBuilder sig = new StringBuilder(serverType(m));
        for (StateField sf : states) {
            sig.append(", ").append(sf.type);
        }
        messager.printMessage(Kind.ERROR,
            "@SmartProxy class " + delegate.getQualifiedName()
            + " must declare a constructor (" + sig + ") so the generated proxy shell"
            + " can reconstruct it from the deserialized server"
            + (states.isEmpty() ? "" : " and its @State fields") + ".",
            m.impl);
        return false;
    }

    /**
     * Whether a delegate ctor's first parameter accepts the wire {@code server} the
     * shell passes ({@code (serverType) this.server}):
     * <ul>
     *   <li>single protocol interface: the parameter must be a supertype of it
     *       ({@code isAssignable(protocol, param)});</li>
     *   <li>aggregate {@code <Api>Backend} (multi-protocol, no single resolvable
     *       type this round): the parameter names the backend by its
     *       fully-qualified name, OR is a common supertype of EVERY protocol
     *       interface (so a value of the aggregate backend, which extends them all,
     *       is assignable to it).  This closes the earlier hole where the
     *       aggregate-server first-param check was skipped.</li>
     * </ul>
     */
    private boolean firstParamAcceptsServer(ServiceModel m, TypeMirror param) {
        TypeMirror single = serverTypeMirror(m);
        if (single != null) {
            return types.isAssignable(single, param);
        }
        if (typeName(param).equals(serverType(m))) {
            return true; // names the aggregate <Api>Backend directly
        }
        for (TypeMirror protocol : m.protocol) {
            if (!types.isAssignable(protocol, param)) {
                return false;
            }
        }
        return true; // a common supertype of every wire interface
    }

    // ------------------------------------------------------------------ helpers

    private boolean throwsRemoteException(ExecutableElement m) {
        for (TypeMirror thrown : m.getThrownTypes()) {
            if (isType(thrown, REMOTE_EXCEPTION) || isSubtypeOf(thrown, REMOTE_EXCEPTION)
                    || isSupertypeOfRemoteException(thrown)) {
                return true;
            }
        }
        return false;
    }

    /** True if a declared thrown type is RemoteException or a supertype that admits it
     *  (IOException, Exception, Throwable) -- any of these satisfies the contract. */
    private boolean isSupertypeOfRemoteException(TypeMirror thrown) {
        String n = typeName(thrown);
        return "java.io.IOException".equals(n)
            || "java.lang.Exception".equals(n)
            || "java.lang.Throwable".equals(n);
    }

    private boolean isRemote(TypeMirror t) {
        return implementsType(t, REMOTE);
    }

    private boolean extendsAbstractSmartProxy(TypeElement t) {
        return implementsType(t.asType(), ABSTRACT_SMART_PROXY);
    }

    /** True if {@code t} (a type) is, extends, or implements {@code fqn}. */
    private boolean implementsType(TypeMirror t, String fqn) {
        if (isType(t, fqn)) {
            return true;
        }
        for (TypeMirror sup : types.directSupertypes(t)) {
            if (implementsType(sup, fqn)) {
                return true;
            }
        }
        return false;
    }

    private boolean isSubtypeOf(TypeMirror t, String fqn) {
        TypeElement target = elements.getTypeElement(fqn);
        if (target == null) {
            return false;
        }
        return types.isSubtype(types.erasure(t), types.erasure(target.asType()));
    }

    private boolean isType(TypeMirror tm, String fqn) {
        if (tm.getKind() != TypeKind.DECLARED) {
            return false;
        }
        Element el = ((DeclaredType) tm).asElement();
        return el instanceof TypeElement
                && ((TypeElement) el).getQualifiedName().contentEquals(fqn);
    }

    private String typeName(TypeMirror tm) {
        if (tm.getKind() == TypeKind.DECLARED) {
            Element el = ((DeclaredType) tm).asElement();
            if (el instanceof TypeElement) {
                return ((TypeElement) el).getQualifiedName().toString();
            }
        }
        return tm.toString();
    }

    /** A simple, comparable method signature: name + erased parameter type names. */
    private String signature(ExecutableElement m) {
        StringBuilder sb = new StringBuilder(m.getSimpleName().toString()).append('(');
        boolean first = true;
        for (Element p : m.getParameters()) {
            if (!first) {
                sb.append(',');
            }
            first = false;
            sb.append(typeName(types.erasure(p.asType())));
        }
        return sb.append(')').toString();
    }
}
