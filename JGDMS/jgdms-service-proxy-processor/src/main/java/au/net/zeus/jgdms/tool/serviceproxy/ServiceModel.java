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
 * <p>All resolution is name- and mirror-based; nothing here loads a live
 * {@code Class}, so the processor depends only on {@code java.compiler}.
 */
final class ServiceModel {

    /**
     * The JGDMS infrastructure interfaces excluded when {@code api()} is empty and
     * the service API is inferred from the impl's implemented interfaces (mirrors
     * the runtime rule in {@code AbstractJiniService.resolveServiceInterfaces}).
     */
    static final Set<String> INFRA_INTERFACES = new java.util.LinkedHashSet<>(java.util.Arrays.asList(
            "net.jini.admin.Administrable",
            "net.jini.admin.JoinAdmin",
            "org.apache.river.admin.DestroyAdmin",
            "net.jini.lookup.ServiceProxyAccessor",
            "net.jini.lookup.ServiceIDAccessor",
            "net.jini.lookup.ServiceAttributesAccessor",
            "net.jini.export.CodebaseAccessor",
            "net.jini.core.constraint.RemoteMethodControl",
            // The bare Remote marker is never itself a service API (mirrors the
            // runtime AbstractJiniService.INFRA_INTERFACES).
            "java.rmi.Remote"));

    /** The annotated service <em>implementation</em> class (carries @JiniService). */
    final TypeElement impl;

    /**
     * The resolved <em>primary</em> public API (remote) interface: the first
     * {@code api()} element, or (when {@code api()} is empty) the first inferred
     * non-infrastructure interface.  Used for naming (the {@code <Api>Backend} /
     * {@code Constrainable<Api>Proxy} conventions) and for diagnostics.  The full
     * set the proxy implements and forwards is {@link #apiInterfaces}.
     */
    final TypeElement api;

    /**
     * The full, ordered set of public API (remote) interfaces the generated proxy
     * implements and forwards — every {@code api()} element, or (when {@code api()}
     * is empty) every inferred non-infrastructure interface.  {@link #api} is the
     * first (primary) of these.  JGDMS-STD-009 §3.1/§4: the generated shell and
     * backend must cover the method set across <em>all</em> api interfaces,
     * symmetric with the runtime
     * {@code AbstractJiniService.resolveServiceInterfaces}.
     */
    final List<TypeElement> apiInterfaces = new ArrayList<>();

    /**
     * The public (abstract) methods declared across <em>all</em>
     * {@link #apiInterfaces}, deduplicated by erased signature (an override common
     * to two api interfaces yields a single forwarding method).
     */
    final List<ExecutableElement> apiMethods = new ArrayList<>();

    /** Resolved internal wire interface ({@code protocol}); defaults to {@link #api}. */
    TypeMirror protocol;

    /**
     * True when {@code protocol()} was left at its default ({@code Void.class}),
     * i.e. the internal wire interface is the public API itself ({@code protocol
     * == api}).  A do-nothing DYNAMIC proxy has {@code protocol == api} and needs
     * NO generated backend interface: the API interface is already the wire
     * interface (JGDMS-STD-009 §6, shapes 1 &amp; 2).  A generated backend interface
     * is emitted only for the translating case {@code protocol != api}.
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
     * {@code @JiniService} off {@code impl}, resolves the primary API interface
     * from {@code api()} (or infers it from the impl's implemented interfaces minus
     * {@link #INFRA_INTERFACES}), and collects that interface's methods.
     *
     * @return the model, or {@code null} if the API interface cannot be resolved
     *         (a diagnostic has then already been emitted against {@code impl})
     */
    static ServiceModel of(TypeElement impl, Elements elements, Types types, Messager messager) {
        // Read @JiniService members off the implementation class.
        AnnotationMirror ann = annotation(impl, ServiceProxyProcessor.JINI_SERVICE);
        TypeMirror protocol = null;
        String component = "";
        ProxyType proxyType = ProxyType.DYNAMIC;
        boolean codebase = false;
        List<TypeMirror> apiTypes = new ArrayList<>();
        if (ann != null) {
            for (var en : ann.getElementValues().entrySet()) {
                String name = en.getKey().getSimpleName().toString();
                AnnotationValue av = en.getValue();
                switch (name) {
                    case "api":
                        collectClassArray(av, apiTypes);
                        break;
                    case "protocol":
                        if (av.getValue() instanceof TypeMirror) {
                            protocol = (TypeMirror) av.getValue();
                        }
                        break;
                    case "component":
                        component = String.valueOf(av.getValue());
                        break;
                    case "proxy":
                        proxyType = ProxyType.from(enumConstant(av));
                        break;
                    case "codebase":
                        if (av.getValue() instanceof Boolean) {
                            codebase = (Boolean) av.getValue();
                        }
                        break;
                    default:
                        break;
                }
            }
        }

        // Resolve the FULL set of API interfaces: every api() element, or -- when
        // api() is empty -- every interface inferred from the impl's implemented
        // interfaces minus the JGDMS infrastructure set (symmetric with the runtime
        // rule in AbstractJiniService.resolveServiceInterfaces, which likewise
        // returns ALL of them and does not treat multiple as ambiguous).
        List<TypeElement> apiList = resolveApiInterfaces(impl, apiTypes, types, messager);
        if (apiList.isEmpty()) {
            return null; // diagnostic already emitted
        }
        TypeElement api = apiList.get(0);

        ServiceModel m = new ServiceModel(impl, api);
        m.apiInterfaces.addAll(apiList);
        String simple = api.getSimpleName().toString();
        m.backendSimpleName = simple + "Backend";

        // Collect the public abstract API methods across ALL api interfaces
        // (JGDMS-STD-009 §3.1/§4: the generated shell must cover every api
        // interface's method set, not just the primary's).  Skip static/default
        // and java.lang.Object methods; deduplicate by erased signature so a method
        // common to two api interfaces yields a single forwarding method (emitting
        // it twice would fail to compile).
        java.util.Set<String> seen = new java.util.LinkedHashSet<>();
        for (TypeElement iface : apiList) {
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
        // protocol default (Void.class) means "same as the annotated API".  A
        // distinct protocol is the translating-smart-proxy signal that gates
        // backend-interface generation (protocol != api); the do-nothing default
        // leaves protocolIsApi true so no backend is generated (§6 shapes 1 & 2).
        if (protocol == null || isVoid(protocol)) {
            m.protocol = api.asType();
            m.protocolIsApi = true;
        } else {
            m.protocol = protocol;
            m.protocolIsApi = types.isSameType(types.erasure(protocol),
                                               types.erasure(api.asType()));
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
     * Resolves the FULL, ordered set of API interfaces for the annotated impl:
     * every {@code api()} element when {@code api()} is non-empty, else every
     * interface inferred from the impl's implemented interfaces (walking the
     * superclass chain down to, but not including, {@code AbstractJiniService})
     * minus {@link #INFRA_INTERFACES} — symmetric with the runtime
     * {@code AbstractJiniService.resolveServiceInterfaces}, which returns all of
     * them (multiple interfaces are the multi-interface service case, not an
     * ambiguity).  Returns an empty list (after emitting a fail-closed diagnostic)
     * if a declared element is not an interface, or if inference yields nothing.
     */
    private static List<TypeElement> resolveApiInterfaces(TypeElement impl,
            List<TypeMirror> apiTypes, Types types, Messager messager) {
        List<TypeElement> result = new ArrayList<>();
        if (!apiTypes.isEmpty()) {
            for (TypeMirror t : apiTypes) {
                TypeElement api = asInterfaceElement(t, types);
                if (api == null) {
                    messager.printMessage(javax.tools.Diagnostic.Kind.ERROR,
                        "@JiniService api() must name remote interface(s).", impl);
                    return java.util.Collections.emptyList();
                }
                result.add(api);
            }
            return result;
        }
        // Infer from the impl's implemented interfaces (walking supers down to
        // AbstractJiniService), minus infrastructure — symmetric with the runtime.
        java.util.Set<TypeElement> inferred = new java.util.LinkedHashSet<>();
        for (TypeElement c = impl; c != null; c = superTypeElement(c)) {
            String cn = c.getQualifiedName().toString();
            if ("au.net.zeus.jgdms.service.support.AbstractJiniService".equals(cn)
                    || "java.lang.Object".equals(cn)) {
                break;
            }
            for (TypeMirror iface : c.getInterfaces()) {
                TypeElement te = asInterfaceElement(iface, types);
                if (te != null
                        && !INFRA_INTERFACES.contains(te.getQualifiedName().toString())) {
                    inferred.add(te);
                }
            }
        }
        if (inferred.isEmpty()) {
            messager.printMessage(javax.tools.Diagnostic.Kind.ERROR,
                "@JiniService on " + impl.getQualifiedName() + " has an empty api()"
                + " and no service interface could be inferred (the class implements"
                + " only infrastructure interfaces); declare api() explicitly.", impl);
            return java.util.Collections.emptyList();
        }
        result.addAll(inferred);
        return result;
    }

    /** The superclass of {@code t} as a {@link TypeElement}, or {@code null} at
     *  the top of the chain (or when the superclass is not a declared type). */
    private static TypeElement superTypeElement(TypeElement t) {
        TypeMirror sup = t.getSuperclass();
        if (sup == null || sup.getKind() != TypeKind.DECLARED) {
            return null;
        }
        Element el = ((DeclaredType) sup).asElement();
        return (el instanceof TypeElement) ? (TypeElement) el : null;
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
