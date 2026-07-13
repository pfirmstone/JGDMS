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

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import javax.annotation.processing.Messager;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;

/**
 * The distilled model of a single {@code @JiniService} <em>implementation</em>
 * class: the resolved public API interface (from {@code api()}, or inferred from
 * the impl's implemented interfaces minus the JGDMS infrastructure set), that
 * interface's abstract (public) methods, the resolved internal wire
 * ({@code protocol}) interface, the requested proxy shape ({@code proxy} /
 * {@code codebase}), and whether the developer has already hand-written the
 * backend interface or the proxy class (in which case the processor validates but
 * does not generate them).
 *
 * <p>The {@code proxy} ({@link ProxyType}) and {@code codebase} elements are read
 * off the annotation and stored.  Generation is shape-dispatched (JGDMS-STD-009
 * §6):
 * <ul>
 *   <li>the BACKEND (wire) interface is generated only when the smart proxy
 *       <em>translates</em> the API into a distinct internal protocol
 *       ({@link #protocolIsApi() protocol != api}); a do-nothing proxy needs
 *       none, since the API interface is already the wire interface;</li>
 *   <li>the constrainable PROXY <em>class</em> is generated only when
 *       {@code proxyType == }{@link ProxyType#SMART} (§6, shape 3): a
 *       {@code DYNAMIC} service is a runtime {@code java.lang.reflect.Proxy} and
 *       generates no proxy class;</li>
 *   <li>a {@code DYNAMIC} service generates no server-side ILFactory either: the
 *       non-{@code Remote} admin interfaces are appended to the exported stub's
 *       interface and dispatch sets by the reusable framework factory
 *       {@code net.jini.jeri.DynamicILFactory}, which the JGDMS service support
 *       installs by default (§14 [RESOLVED]).  So a {@code DYNAMIC} service with
 *       {@code protocol == api} generates nothing at all.</li>
 * </ul>
 * The {@code codebase} axis of the §6 shape dispatch ({@code -dl} packaging) is a
 * follow-on task; {@code codebase} is stored but not yet acted on.
 *
 * <p>{@code proxyType} is mostly <em>inferred</em>, not read verbatim off the
 * annotation: a {@code smartProxy()} reference or a translating (distinct)
 * {@code protocol()} each independently imply {@link ProxyType#SMART} (an explicit
 * {@code proxy = DYNAMIC} contradicting either is a fail-closed compile error) —
 * {@code DYNAMIC} has no codegen able to bridge {@code api() != protocol()} or
 * forward through a delegate, so leaving either signal un-acted-on would silently
 * generate a backend interface (or nothing at all) with no proxy class to use it.
 * {@code proxy()} is only load-bearing, on its own, for the thin case
 * ({@code protocol() == api()}, no {@code smartProxy()}): {@code DYNAMIC} vs. a
 * thin {@code SMART} proxy (no translation, no delegate — the shape
 * Reggie/Fiddler/Mahalo/Mercury's hand-written proxies use) is a genuine choice no
 * other element can distinguish.
 *
 * <p>All resolution is name- and mirror-based; nothing here loads a live
 * {@code Class}, so the processor depends only on {@code java.compiler}.
 */
final class ServiceModel {

    /** {@code java.rmi.Remote}, the marker that partitions client vs. admin interfaces. */
    static final String REMOTE_NAME = "java.rmi.Remote";

    /**
     * The four {@link java.rmi.Remote} bootstrap-accessor interfaces subtracted from
     * the inferred service API — the ONLY {@code Remote}-extending interfaces that are
     * NOT service API.  This is the allowlist that lets the processor's
     * {@link #resolveApi} converge <em>exactly</em> with the runtime
     * {@code AbstractJiniService.classify}: api = {implemented interfaces extending
     * {@code Remote}} − {these four accessors} − {@code Remote} itself.  Because a
     * service API must extend {@code Remote} to be exported, the non-{@code Remote}
     * admin/{@code RemoteMethodControl} entries drop out automatically under the
     * {@code Remote} test, so both sides key off the same rule (JGDMS-STD-009 §6).
     */
    static final Set<String> BOOTSTRAP_ACCESSORS = new java.util.LinkedHashSet<>(java.util.Arrays.asList(
            "net.jini.lookup.ServiceProxyAccessor",
            "net.jini.lookup.ServiceIDAccessor",
            "net.jini.lookup.ServiceAttributesAccessor",
            "net.jini.export.CodebaseAccessor"));

    /** The annotated service <em>implementation</em> class (carries @JiniService). */
    final TypeElement impl;

    /**
     * The resolved <em>primary</em> public API (remote) interface — the first of
     * {@link #apiInterfaces}.  Used for naming (the {@code <Api>Backend} /
     * {@code Constrainable<Api>Proxy} conventions) and for diagnostics; the (SMART)
     * proxy class and backend interface are generated against it, while the full set
     * the proxy implements and forwards is {@link #apiInterfaces}.
     */
    final TypeElement api;

    /**
     * ALL resolved service API (remote) interfaces (design decision D2 —
     * multi-interface registration): every explicitly-declared {@code api()} element,
     * or — when {@code api()} is empty — every implemented {@code Remote} interface
     * that is not a bootstrap accessor (see {@link #resolveApi}).  Mirrors the runtime
     * {@code AbstractJiniService.getServiceInterfaces()} full set; {@link #api} is the
     * first element.  An empty {@code api()} that yields several interfaces is NOT an
     * error (it was, before multi-interface convergence).  JGDMS-STD-009 §3.1/§4: the
     * generated shell and backend cover the method set across <em>all</em> of these.
     */
    final List<TypeElement> apiInterfaces = new ArrayList<>();

    /**
     * The public (abstract) methods declared across <em>all</em>
     * {@link #apiInterfaces}, deduplicated by erased signature (an override common
     * to two api interfaces yields a single forwarding method).
     */
    final List<ExecutableElement> apiMethods = new ArrayList<>();

    /**
     * The resolved internal wire ({@code protocol}) interface(s).  When
     * {@code protocol()} is empty (or names exactly the {@link #apiInterfaces api}
     * set) this holds the api interface types (non-translating); when a distinct
     * translating protocol is declared it holds those wire interface types.  A wire
     * set of more than one element has no single Java type able to name it, so the
     * generated aggregate {@link #backendSimpleName <Api>Backend} is used as the
     * combined wire type in that case (see {@code ServiceProxyProcessor}).
     */
    final List<TypeMirror> protocol = new ArrayList<>();

    /**
     * True when the internal wire {@code protocol()} is the public API itself —
     * {@code protocol()} left empty, or naming exactly the {@link #apiInterfaces
     * api} set (an erased-type set comparison).  A do-nothing DYNAMIC proxy has
     * {@code protocol == api} and needs NO generated backend interface: the API
     * interface is already the wire interface (JGDMS-STD-009 §6, shapes 1 &amp; 2).
     * A generated backend interface is emitted for the translating case
     * ({@code protocol != api}) and, so the generated SMART proxy has a single type
     * to name a multi-element wire set, when the wire set has &gt; 1 interface.
     */
    boolean protocolIsApi = true;

    /** {@code component} config name for the generated wrapper (may be empty). */
    String component = "";

    /**
     * The declared proxy type ({@code proxy} element), mirroring
     * {@code au.net.zeus.jgdms.service.annotation.ProxyType}.  Gates constrainable
     * proxy-class generation: the class is emitted only when this is
     * {@link ProxyType#SMART} (JGDMS-STD-009 §6, shape 3); a {@link ProxyType#DYNAMIC}
     * service generates no proxy class.
     */
    ProxyType proxyType = ProxyType.DYNAMIC;

    /**
     * The declared {@code codebase} flag: whether the service ships a downloadable
     * {@code -dl} jar.  Stored only; not yet acted on by codegen.
     */
    boolean codebase;

    /** Conventional simple name of the backend interface: {@code <Api>Backend}. */
    String backendSimpleName;

    /** True when a backend interface with the conventional name already exists. */
    boolean backendHandWritten;

    /** The hand-written backend interface element, if present (for validation). */
    TypeElement proxyHandWrittenBackend;

    /** The hand-written proxy class, if present (for validation), else null. */
    TypeElement proxyHandWritten;

    /**
     * The {@code @SmartProxy} client-side logic class named by
     * {@link au.net.zeus.jgdms.service.annotation.JiniService#smartProxy()}, or
     * {@code null} when none (or {@code Void.class}) was declared.  When present the
     * generated SMART shell forwards to it (P3); the processor validates it is a
     * {@code @SmartProxy} marker that implements every api interface and declares a
     * {@code (serverType[, @State types])} constructor.
     */
    TypeElement smartProxyElement;

    /** Set by validation when a fatal error was reported (suppresses generation). */
    boolean hadError;

    private ServiceModel(TypeElement impl, TypeElement api) {
        this.impl = impl;
        this.api = api;
    }

    /** @return the declared proxy type ({@code proxy} element); {@link ProxyType#DYNAMIC} by default. */
    ProxyType proxyType() {
        return proxyType;
    }

    /** @return the declared {@code codebase} flag ({@code false} by default). */
    boolean codebase() {
        return codebase;
    }

    /**
     * @return {@code true} when the internal wire {@code protocol} is the public
     *         API itself ({@code protocol == api}); {@code false} when the
     *         developer set a distinct translating {@code protocol}.  When
     *         {@code true}, no backend interface is generated (§6 shapes 1 &amp; 2).
     */
    boolean protocolIsApi() {
        return protocolIsApi;
    }

    /**
     * Builds the model from the annotated <em>implementation</em> class.  Reads
     * {@code @JiniService} off {@code impl}, resolves ALL API interfaces from
     * {@code api()} (or infers them via the shared allowlist — interfaces extending
     * {@link #REMOTE_NAME Remote} minus the {@link #BOOTSTRAP_ACCESSORS accessors}, see
     * {@link #resolveApi}), keeps the first as the primary {@link #api}, and collects
     * that interface's methods.
     *
     * @return the model, or {@code null} if the API interface cannot be resolved
     *         (a diagnostic has then already been emitted against {@code impl})
     */
    static ServiceModel of(TypeElement impl, Elements elements, Types types, Messager messager) {
        // Read @JiniService members off the implementation class.
        AnnotationMirror ann = annotation(impl, ServiceProxyProcessor.JINI_SERVICE);
        List<TypeMirror> protocolTypes = new ArrayList<>();
        String component = "";
        ProxyType proxyType = ProxyType.DYNAMIC;
        boolean proxyExplicit = false;
        boolean codebase = false;
        List<TypeMirror> apiTypes = new ArrayList<>();
        TypeMirror smartProxyType = null;
        if (ann != null) {
            for (var en : ann.getElementValues().entrySet()) {
                String name = en.getKey().getSimpleName().toString();
                AnnotationValue av = en.getValue();
                switch (name) {
                    case "api":
                        collectClassArray(av, apiTypes);
                        break;
                    case "protocol":
                        // protocol() is now Class<?>[] (default {}); collectClassArray
                        // handles both an array literal and a single value.  Void.class
                        // is filtered out so a stale/explicit Void collapses to "empty".
                        collectClassArray(av, protocolTypes);
                        protocolTypes.removeIf(ServiceModel::isVoid);
                        break;
                    case "component":
                        component = String.valueOf(av.getValue());
                        break;
                    case "proxy":
                        proxyType = ProxyType.from(enumConstant(av));
                        proxyExplicit = true;
                        break;
                    case "codebase":
                        if (av.getValue() instanceof Boolean) {
                            codebase = (Boolean) av.getValue();
                        }
                        break;
                    case "smartProxy":
                        // A single Class<?>; Void.class means "no smart-proxy delegate".
                        if (av.getValue() instanceof TypeMirror
                                && !isVoid((TypeMirror) av.getValue())) {
                            smartProxyType = (TypeMirror) av.getValue();
                        }
                        break;
                    default:
                        break;
                }
            }
        }

        // Proxy-type resolution (ratified): a smartProxy() reference IMPLIES SMART,
        // so proxy=SMART need not be stated.  A smartProxy() with an explicit
        // proxy=DYNAMIC is a contradiction -> fail closed.  declaredProxyType is the
        // RAW, un-inferred value read off the annotation, captured before either
        // inference rule below mutates proxyType -- both conflict checks compare
        // against this snapshot, not the progressively-inferred proxyType, so that
        // (e.g.) a smartProxy()-driven inference happening first doesn't mask a
        // genuine explicit-DYNAMIC contradiction with a translating protocol()
        // checked second.
        ProxyType declaredProxyType = proxyType;
        boolean smartProxyConflict = smartProxyType != null
                && proxyExplicit && declaredProxyType == ProxyType.DYNAMIC;
        if (smartProxyType != null) {
            proxyType = ProxyType.SMART;
        }

        // A declared, translating protocol() (non-empty and distinct from api())
        // ALSO implies SMART, for the same reason smartProxy() does: DYNAMIC has no
        // codegen to bridge api() != protocol() -- its exported
        // java.lang.reflect.Proxy only ever carries the interfaces the impl
        // actually implements, so a DYNAMIC service with a translating protocol
        // would otherwise silently generate a backend interface (gated on
        // protocol != api alone, independent of proxyType) but no proxy class and
        // no ILFactory, handing clients a stub that does not even implement api().
        // Compares the RAW annotation-declared apiTypes, not the resolved/possibly
        // -inferred apis list (not available this early): if api() is left empty
        // while a translating protocol() is declared, that already differs from
        // the (empty) api set, so this still infers SMART -- which then correctly
        // routes into resolveApi's existing "a SMART/translating service must
        // declare api() explicitly" diagnostic below, rather than silently
        // inferring api() from the wire interface the way DYNAMIC would have.
        boolean translatingProtocol = !protocolTypes.isEmpty()
                && !sameErasedTypeMirrorSet(protocolTypes, apiTypes, types);
        boolean protocolConflict = translatingProtocol
                && proxyExplicit && declaredProxyType == ProxyType.DYNAMIC;
        if (translatingProtocol) {
            proxyType = ProxyType.SMART;
        }

        // Resolve ALL API interfaces: every api() element, or -- when api() is empty
        // -- inferred from the impl's implemented interfaces via the shared allowlist
        // (symmetric with the runtime rule in AbstractJiniService.classify, which
        // likewise returns ALL of them and does not treat multiple as ambiguous).
        // The first is the primary the proxy/backend is generated for; the rest are
        // registered on the model (multi-interface registration, D2).
        if (smartProxyConflict || protocolConflict) {
            String cause = smartProxyConflict && protocolConflict
                    ? "smartProxy() and a translating protocol()"
                    : smartProxyConflict ? "smartProxy()" : "a translating protocol()";
            messager.printMessage(javax.tools.Diagnostic.Kind.ERROR,
                "@JiniService on " + impl.getQualifiedName() + ": " + cause
                + " implies a SMART proxy; remove the explicit proxy=DYNAMIC (or the"
                + " smartProxy()/protocol() declaration that implies SMART).", impl);
            return null;
        }
        List<TypeElement> apis = resolveApi(impl, apiTypes, proxyType, types, messager);
        if (apis.isEmpty()) {
            return null; // diagnostic already emitted
        }
        TypeElement api = apis.get(0);

        ServiceModel m = new ServiceModel(impl, api);
        // Resolve the @SmartProxy delegate class named by smartProxy() (if any).
        if (smartProxyType != null && smartProxyType.getKind() == TypeKind.DECLARED) {
            Element el = ((DeclaredType) smartProxyType).asElement();
            if (el instanceof TypeElement) {
                m.smartProxyElement = (TypeElement) el;
            }
        }
        m.apiInterfaces.addAll(apis);
        String simple = api.getSimpleName().toString();
        m.backendSimpleName = simple + "Backend";

        // Collect the public abstract API methods across ALL api interfaces
        // (JGDMS-STD-009 §3.1/§4: the generated shell must cover every api
        // interface's method set, not just the primary's).  Skip static/default
        // and java.lang.Object methods; deduplicate by erased signature so a method
        // common to two api interfaces yields a single forwarding method (emitting
        // it twice would fail to compile).
        java.util.Set<String> seen = new java.util.LinkedHashSet<>();
        for (TypeElement iface : apis) {
            for (Element e : elements.getAllMembers(iface)) {
                if (e.getKind() != ElementKind.METHOD) {
                    continue;
                }
                ExecutableElement me = (ExecutableElement) e;
                if (me.getModifiers().contains(Modifier.STATIC)
                        || me.getModifiers().contains(Modifier.DEFAULT)) {
                    continue;
                }
                Element enclosing = me.getEnclosingElement();
                if (enclosing instanceof TypeElement
                        && "java.lang.Object".contentEquals(
                            ((TypeElement) enclosing).getQualifiedName())) {
                    continue;
                }
                if (seen.add(methodKey(me, types))) {
                    m.apiMethods.add(me);
                }
            }
        }
        // protocol default (empty {}) means "same as the annotated API".  A
        // distinct protocol set is the translating-smart-proxy signal that gates
        // backend-interface generation (protocol != api); the do-nothing default
        // leaves protocolIsApi true so no backend is generated (§6 shapes 1 & 2).
        // protocolIsApi is a SET comparison: the protocol[] is empty, or it names
        // exactly the api[] set (erased).  The wire list (m.protocol) then holds the
        // api interface types (non-translating) or the declared protocol types.
        if (protocolTypes.isEmpty() || sameErasedSet(protocolTypes, apis, types)) {
            for (TypeElement a : apis) {
                m.protocol.add(a.asType());
            }
            m.protocolIsApi = true;
        } else {
            m.protocol.addAll(protocolTypes);
            m.protocolIsApi = false;
        }
        m.component = component;
        // proxy gates constrainable proxy-class generation (SMART only, §6 shape
        // 3); codebase is read and stored but not yet acted on for -dl packaging
        // (a follow-on task).
        m.proxyType = proxyType;
        m.codebase = codebase;

        // Detect a hand-written backend interface with the conventional name in
        // the API's package.
        PackageElement pkg = elements.getPackageOf(api);
        String backendFqn = pkg.isUnnamed()
                ? m.backendSimpleName
                : pkg.getQualifiedName() + "." + m.backendSimpleName;
        TypeElement existingBackend = elements.getTypeElement(backendFqn);
        if (existingBackend != null && existingBackend.getKind() == ElementKind.INTERFACE) {
            m.backendHandWritten = true;
            m.proxyHandWrittenBackend = existingBackend;
        }

        // Detect a hand-written proxy class by convention, so validate-only mode
        // can check an already-written proxy's constrainability and forwarding
        // coverage.  Look for the constrainable name first, then the base name,
        // in both the API's package and a sibling ".proxy" package.
        String base = pkg.isUnnamed() ? "" : pkg.getQualifiedName().toString();
        String[] candidates = {
            qualify(base, "Constrainable" + simple + "Proxy"),
            qualify(base, simple + "Proxy"),
            qualify(siblingProxyPackage(base), "Constrainable" + simple + "Proxy"),
            qualify(siblingProxyPackage(base), simple + "Proxy")
        };
        for (String cand : candidates) {
            TypeElement pe = elements.getTypeElement(cand);
            if (pe != null && pe.getKind() == ElementKind.CLASS) {
                m.proxyHandWritten = pe;
                break;
            }
        }

        return m;
    }

    /**
     * Reads a {@code Class<?>[]}-valued annotation member into {@code out} as a
     * list of {@code TypeMirror} (survives the {@code MirroredTypesException} that
     * reading live {@code Class} values would throw).
     */
    private static void collectClassArray(AnnotationValue av, List<TypeMirror> out) {
        Object v = av.getValue();
        if (v instanceof List<?>) {
            for (Object o : (List<?>) v) {
                if (o instanceof AnnotationValue) {
                    Object inner = ((AnnotationValue) o).getValue();
                    if (inner instanceof TypeMirror) {
                        out.add((TypeMirror) inner);
                    }
                }
            }
        } else if (v instanceof TypeMirror) {
            out.add((TypeMirror) v);
        }
    }

    /**
     * Resolves ALL API interfaces for the annotated impl (design decision D2 —
     * multi-interface registration): every {@code api()} element when present, else
     * <em>every</em> interface inferred from the impl's interface closure via the
     * shared allowlist — api = {interfaces extending {@link #REMOTE_NAME Remote}} −
     * {the four {@link #BOOTSTRAP_ACCESSORS accessors}} − {@code Remote} itself
     * (symmetric with {@code AbstractJiniService.classify}).  The first element is the
     * primary the proxy/backend is generated for; the generated shell forwards the
     * method set across all of them ({@link #apiMethods}).
     *
     * <p>Multi-interface convergence: an empty {@code api()} that resolves to several
     * interfaces is NO LONGER an error (previously "ambiguous"); all are registered,
     * exactly as the runtime {@code getServiceInterfaces()} infers and registers them.
     * Returns an empty list (after emitting a fail-closed diagnostic) only if an
     * explicit {@code api()} element is not an interface, or inference yields nothing.
     */
    private static List<TypeElement> resolveApi(TypeElement impl, List<TypeMirror> apiTypes,
                                                ProxyType proxyType,
                                                Types types, Messager messager) {
        if (!apiTypes.isEmpty()) {
            // Explicit api(): register EVERY declared interface (multi-interface).
            List<TypeElement> explicit = new ArrayList<>();
            for (TypeMirror tm : apiTypes) {
                TypeElement te = asInterfaceElement(tm, types);
                if (te == null) {
                    messager.printMessage(javax.tools.Diagnostic.Kind.ERROR,
                        "@JiniService api() must name remote interface(s).", impl);
                    return java.util.Collections.emptyList();
                }
                explicit.add(te);
            }
            return explicit;
        }
        // api() inference is a DYNAMIC-only convenience (ratified): a SMART proxy
        // (including any smartProxy()/translating service) must declare api()
        // explicitly.  A translating SMART impl implements the WIRE/protocol
        // interface, not the public api, so inferring api() from its interfaces
        // would advertise the wire interface -- the exact footgun the runtime guard
        // AbstractJiniService.resolveServiceInterfaces catches.  Fail closed here at
        // compile time.
        if (proxyType == ProxyType.SMART) {
            messager.printMessage(javax.tools.Diagnostic.Kind.ERROR,
                "@JiniService on " + impl.getQualifiedName() + " is a SMART proxy with an"
                + " empty api(); a SMART/translating service must declare api() explicitly"
                + " (only a DYNAMIC proxy can infer it from the implementation, which"
                + " implements the wire/protocol interface, not the public api).", impl);
            return java.util.Collections.emptyList();
        }
        // Infer from the impl's FULL interface closure via the shared allowlist:
        // every interface extending Remote, minus the four bootstrap accessors and
        // Remote itself.  Non-Remote admin/RMC interfaces drop out under the Remote
        // test (they are the administrative contract, not the client API).
        Set<TypeElement> closure = new java.util.LinkedHashSet<>();
        collectAllInterfaces(impl.asType(), types, closure);
        List<TypeElement> inferred = new ArrayList<>();
        for (TypeElement te : closure) {
            String name = te.getQualifiedName().toString();
            if (REMOTE_NAME.equals(name) || BOOTSTRAP_ACCESSORS.contains(name)) {
                continue;
            }
            if (extendsRemote(te, types)) {
                inferred.add(te);
            }
        }
        if (inferred.isEmpty()) {
            messager.printMessage(javax.tools.Diagnostic.Kind.ERROR,
                "@JiniService on " + impl.getQualifiedName() + " has an empty api()"
                + " and no service interface could be inferred (the class implements"
                + " only infrastructure interfaces); declare api() explicitly.", impl);
            return java.util.Collections.emptyList();
        }
        return inferred;
    }

    /**
     * Collects every interface {@link TypeElement} in {@code t}'s supertype closure
     * (its own super-interfaces and those reached through superclasses) into
     * {@code out}, each once.  Mirrors {@code AbstractJiniService.collectAllInterfaces}
     * <em>including its order</em>: the directly-declared interfaces (and their
     * closures) are visited BEFORE the superclass subtree, so the inferred primary
     * ({@code apiInterfaces.get(0)}) matches the runtime {@code getServiceInterfaces()[0]}
     * even when a service's {@code Remote} interfaces are declared at different levels
     * of the class hierarchy.  {@link javax.lang.model.util.Types#directSupertypes}
     * returns the superclass first, so it is deferred here rather than walked in place.
     */
    private static void collectAllInterfaces(TypeMirror t, Types types, Set<TypeElement> out) {
        TypeMirror classSuper = null;
        for (TypeMirror sup : types.directSupertypes(t)) {
            if (sup.getKind() != TypeKind.DECLARED) {
                continue;
            }
            Element el = ((DeclaredType) sup).asElement();
            if (el instanceof TypeElement && el.getKind() == ElementKind.INTERFACE) {
                // Interface: recurse only the first time it is seen (avoids
                // re-walking a diamond).
                if (out.add((TypeElement) el)) {
                    collectAllInterfaces(sup, types, out);
                }
            } else {
                // Superclass (or Object): defer so this level's own interfaces are
                // collected first, matching the runtime traversal order.
                classSuper = sup;
            }
        }
        if (classSuper != null) {
            collectAllInterfaces(classSuper, types, out);
        }
    }

    /** True iff interface {@code te} is, or transitively extends, {@code java.rmi.Remote}. */
    private static boolean extendsRemote(TypeElement te, Types types) {
        return isOrExtends(te.asType(), REMOTE_NAME, types);
    }

    private static boolean isOrExtends(TypeMirror t, String fqn, Types types) {
        if (t.getKind() == TypeKind.DECLARED) {
            Element el = ((DeclaredType) t).asElement();
            if (el instanceof TypeElement
                    && ((TypeElement) el).getQualifiedName().contentEquals(fqn)) {
                return true;
            }
        }
        for (TypeMirror sup : types.directSupertypes(t)) {
            if (isOrExtends(sup, fqn, types)) {
                return true;
            }
        }
        return false;
    }

    /**
     * True iff the declared {@code protocol[]} names exactly the {@code api[]} set,
     * compared by erased qualified type name (order-independent).  Used to fold a
     * redundant {@code protocol()} that merely restates the api into the
     * non-translating (thin) case, so {@code protocolIsApi} stays a set comparison
     * rather than a positional one.
     */
    private static boolean sameErasedSet(List<TypeMirror> protocolTypes,
                                         List<TypeElement> apis, Types types) {
        Set<String> a = new java.util.LinkedHashSet<>();
        for (TypeElement e : apis) {
            a.add(types.erasure(e.asType()).toString());
        }
        Set<String> p = new java.util.LinkedHashSet<>();
        for (TypeMirror tm : protocolTypes) {
            p.add(types.erasure(tm).toString());
        }
        return a.equals(p);
    }

    /**
     * True iff two raw {@code Class<?>[]}-valued annotation members ({@code protocol()}
     * and {@code api()}, before {@code api()} inference has run) name the same set of
     * types, compared by erased qualified name (order-independent). Unlike
     * {@link #sameErasedSet}, both sides are still {@code TypeMirror}s -- used by the
     * proxy-type inference in {@link #of} to detect a translating {@code protocol()}
     * before the resolved {@code apis} list exists (an explicitly empty {@code api()}
     * compared against a non-empty {@code protocol()} correctly reads as "differs").
     */
    private static boolean sameErasedTypeMirrorSet(List<TypeMirror> protocolTypes,
                                                   List<TypeMirror> apiTypes, Types types) {
        Set<String> p = new java.util.LinkedHashSet<>();
        for (TypeMirror tm : protocolTypes) {
            p.add(types.erasure(tm).toString());
        }
        Set<String> a = new java.util.LinkedHashSet<>();
        for (TypeMirror tm : apiTypes) {
            a.add(types.erasure(tm).toString());
        }
        return a.equals(p);
    }

    /** A dedup key for a method: simple name + erased parameter type names. */
    private static String methodKey(ExecutableElement m, Types types) {
        StringBuilder sb = new StringBuilder(m.getSimpleName().toString()).append('(');
        boolean first = true;
        for (VariableElement p : m.getParameters()) {
            if (!first) {
                sb.append(',');
            }
            first = false;
            sb.append(types.erasure(p.asType()).toString());
        }
        return sb.append(')').toString();
    }

    /** The {@link TypeElement} of a declared interface type, or {@code null}. */
    private static TypeElement asInterfaceElement(TypeMirror tm, Types types) {
        if (tm == null || tm.getKind() != TypeKind.DECLARED) {
            return null;
        }
        Element el = ((DeclaredType) tm).asElement();
        if (el instanceof TypeElement && el.getKind() == ElementKind.INTERFACE) {
            return (TypeElement) el;
        }
        return null;
    }

    private static String qualify(String pkg, String simple) {
        return pkg.isEmpty() ? simple : pkg + "." + simple;
    }

    /** The conventional sibling downloadable proxy package: {@code ....api.x} -> {@code ....x.proxy}
     *  is service-specific, so we simply append {@code .proxy} to a trimmed base. */
    private static String siblingProxyPackage(String base) {
        if (base.isEmpty()) {
            return "proxy";
        }
        return base + ".proxy";
    }

    private static boolean isVoid(TypeMirror t) {
        if (t.getKind() == TypeKind.VOID) {
            return true;
        }
        if (t.getKind() == TypeKind.DECLARED) {
            Element el = ((DeclaredType) t).asElement();
            return el instanceof TypeElement
                    && "java.lang.Void".contentEquals(((TypeElement) el).getQualifiedName());
        }
        return false;
    }

    private static AnnotationMirror annotation(Element e, String fqn) {
        for (AnnotationMirror am : e.getAnnotationMirrors()) {
            TypeMirror t = am.getAnnotationType();
            if (t.getKind() == TypeKind.DECLARED) {
                Element el = ((DeclaredType) t).asElement();
                if (el instanceof TypeElement
                        && ((TypeElement) el).getQualifiedName().contentEquals(fqn)) {
                    return am;
                }
            }
        }
        return null;
    }

    /** Reads a single enum-valued annotation member as its constant name, or null. */
    private static String enumConstant(AnnotationValue av) {
        Object v = av.getValue();
        if (v instanceof VariableElement) {
            return ((VariableElement) v).getSimpleName().toString();
        }
        return null;
    }

    /**
     * The processor-side mirror of
     * {@code au.net.zeus.jgdms.service.annotation.ProxyType}.  The processor has
     * no compile dependency on the annotations module (it reads everything by
     * name via {@code javax.lang.model}), so the enum is restated here; its
     * constant names MUST track the annotation's.
     */
    enum ProxyType {
        /** A {@code java.lang.reflect.Proxy} invoked remotely (default). */
        DYNAMIC,
        /** A downloaded smart proxy whose behaviour runs locally. */
        SMART;

        /** Maps a constant name (from the annotation) to a value; defaults to {@link #DYNAMIC}. */
        static ProxyType from(String name) {
            if (name != null) {
                for (ProxyType t : values()) {
                    if (t.name().equals(name)) {
                        return t;
                    }
                }
            }
            return DYNAMIC;
        }
    }
}
