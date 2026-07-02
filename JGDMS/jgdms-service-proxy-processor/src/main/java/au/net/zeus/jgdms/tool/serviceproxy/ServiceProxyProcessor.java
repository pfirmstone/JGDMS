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
 * fully-qualified name (the annotations are {@code SOURCE}-retained and consumed
 * through {@code javax.lang.model}), so it depends only on the JDK's
 * {@code java.compiler} module.
 *
 * <h2>Modes</h2>
 * <ul>
 *   <li><b>validate-only</b> ({@code -Aserviceproxy.validateOnly=true}) — emits
 *       the contract diagnostics of the design note's validation mode and
 *       generates nothing.  Independently useful: it would have caught the
 *       original {@code AbstractSmartProxy} round-trip flaws before generation
 *       existed.</li>
 *   <li><b>generate</b> (default) — additionally emits the backend interface
 *       (this phase), the constrainable proxy, and optionally the wrapper.</li>
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
        TypeElement jini = elements.getTypeElement(JINI_SERVICE);
        if (jini != null) {
            for (Element e : round.getElementsAnnotatedWith(jini)) {
                if (e.getKind() != ElementKind.INTERFACE) {
                    messager.printMessage(Kind.ERROR,
                        "@JiniService must annotate the public API interface, not a "
                        + e.getKind().toString().toLowerCase() + ".", e);
                    continue;
                }
                try {
                    processJiniService((TypeElement) e);
                } catch (IOException ex) {
                    messager.printMessage(Kind.ERROR, "ServiceProxyProcessor: " + ex, e);
                }
            }
        }
        TypeElement smart = elements.getTypeElement(SMART_PROXY);
        if (smart != null) {
            for (Element e : round.getElementsAnnotatedWith(smart)) {
                if (e.getKind() != ElementKind.CLASS) {
                    messager.printMessage(Kind.ERROR,
                        "@SmartProxy must annotate a client-side logic delegate class.", e);
                    continue;
                }
                validateSmartProxy((TypeElement) e);
                // Generation of the smart-proxy shell is a later phase (P3); for
                // now @SmartProxy is validate-only regardless of mode.
            }
        }
        return false; // do not claim the annotations; allow other processors
    }

    // ---------------------------------------------------------------- @JiniService

    private void processJiniService(TypeElement api) throws IOException {
        ServiceModel model = ServiceModel.of(api, elements, types, messager);
        validateJiniService(model);
        if (validateOnly || model.hadError) {
            return;
        }
        if (model.generateBackend && !model.backendHandWritten) {
            writeBackend(model);
        }
        if (model.generateProxy && model.proxyHandWritten == null) {
            writeProxy(model);
        }
        // Wrapper (P4) and smart-proxy shell (P3) generation land in later phases.
    }

    // ------------------------------------------------------------------ validation

    /**
     * Emits the design note's validation-mode diagnostics for a {@code @JiniService}
     * API interface; never modifies the class.
     */
    private void validateJiniService(ServiceModel m) {
        // Every API method must declare RemoteException (a Remote interface
        // whose methods cannot signal transport failure is malformed).
        if (!isRemote(m.api.asType())) {
            messager.printMessage(Kind.ERROR,
                "@JiniService interface " + m.api.getQualifiedName()
                + " must extend java.rmi.Remote.", m.api);
            m.hadError = true;
        }
        for (ExecutableElement method : m.apiMethods) {
            if (!throwsRemoteException(method)) {
                messager.printMessage(Kind.ERROR,
                    "@JiniService API method " + m.api.getSimpleName() + "."
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
        if (!implementsType(backend.asType(), m.api.getQualifiedName().toString())) {
            messager.printMessage(Kind.ERROR,
                "Backend interface " + backend.getQualifiedName()
                + " must aggregate the public API interface " + m.api.getQualifiedName()
                + ".", backend);
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
     * Validates a {@code @SmartProxy} delegate: it must implement its declared
     * {@code api}, and its {@code @Stateless}/state declarations must be
     * consistent.
     */
    private void validateSmartProxy(TypeElement delegate) {
        TypeMirror apiType = annotationClassValue(delegate, SMART_PROXY, "api");
        if (apiType != null && !implementsType(delegate.asType(), typeName(apiType))) {
            messager.printMessage(Kind.ERROR,
                "@SmartProxy delegate " + delegate.getQualifiedName()
                + " must implement its declared api " + typeName(apiType) + ".", delegate);
        }
        // A @SmartProxy delegate should not itself be @AtomicSerial (the
        // generated shell is the wire type, not the delegate) -- flagged only as
        // a warning since a developer may legitimately mark unrelated behaviour.
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
         .append(" * annotation processor.  It aggregates the public API contract with the fixed\n")
         .append(" * set of JGDMS infrastructure interfaces every exported service stub must\n")
         .append(" * expose, so a JERI stub for this interface transitively carries them all.\n")
         .append(" *\n")
         .append(" * @see ").append(m.api.getQualifiedName()).append('\n')
         .append(" */\n");
        b.append("public interface ").append(simple).append("\n")
         .append("        extends ").append(REMOTE).append(",\n")
         .append("                ").append(m.api.getQualifiedName());
        for (String infra : BACKEND_INFRA) {
            b.append(",\n                ").append(infra);
        }
        b.append(" {\n}\n");

        JavaFileObject src = filer.createSourceFile(fqn, m.api);
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
    private void writeProxy(ServiceModel m) throws IOException {
        String pkg = elements.getPackageOf(m.api).getQualifiedName().toString();
        String api = m.api.getQualifiedName().toString();
        String apiSimple = m.api.getSimpleName().toString();
        String protocol = typeName(m.protocol);
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
         .append(" *\n")
         .append(" * @see ").append(api).append('\n')
         .append(" * @see au.net.zeus.jgdms.proxy.AbstractSmartProxy\n")
         .append(" */\n");
        b.append("@org.apache.river.api.io.AtomicSerial\n");
        b.append("@org.apache.river.api.io.AtomicSerial.Stateless\n");
        b.append("public final class ").append(simple).append("\n")
         .append("        extends ").append(ABSTRACT_SMART_PROXY).append(".ConstrainableSmartProxy\n")
         .append("        implements ").append(api).append(" {\n\n");
        b.append("    private static final long serialVersionUID = 1L;\n\n");

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
         .append(" create(").append(api).append(" server, net.jini.id.Uuid proxyID) {\n");
        b.append("        if (!(server instanceof ").append(REMOTE_METHOD_CONTROL).append(")) {\n");
        b.append("            throw new IllegalArgumentException(\n");
        b.append("                \"service must be exported with a constrainable endpoint: \"\n");
        b.append("                + \"server does not implement RemoteMethodControl\");\n");
        b.append("        }\n");
        b.append("        net.jini.core.constraint.MethodConstraints serverConstraints =\n");
        b.append("                ((").append(REMOTE_METHOD_CONTROL).append(") server).getConstraints();\n");
        b.append("        return new ").append(simple)
         .append("(server, proxyID, serverConstraints);\n");
        b.append("    }\n\n");

        // Public (server, proxyID, constraints) ctor.
        b.append("    /**\n")
         .append("     * Creates a constrainable proxy applying {@code constraints} to the stub.\n")
         .append("     *\n")
         .append("     * @param server      the remote server stub\n")
         .append("     * @param proxyID     the service's stable unique identifier\n")
         .append("     * @param constraints per-method constraints, or {@code null}\n")
         .append("     */\n");
        b.append("    public ").append(simple).append("(").append(api)
         .append(" server, net.jini.id.Uuid proxyID,\n")
         .append("            net.jini.core.constraint.MethodConstraints constraints) {\n");
        b.append("        super(server, proxyID, constraints);\n");
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
        b.append("    }\n\n");

        // setConstraints returning a re-constrained copy via getReferentUuid().
        b.append("    @Override\n");
        b.append("    public ").append(REMOTE_METHOD_CONTROL)
         .append(" setConstraints(net.jini.core.constraint.MethodConstraints constraints) {\n");
        b.append("        return new ").append(simple).append("(\n");
        b.append("                (").append(protocol).append(") server, getReferentUuid(), constraints);\n");
        b.append("    }\n");

        // Forwarding methods -- every public API method delegated to (protocol) server.
        for (ExecutableElement method : m.apiMethods) {
            b.append('\n');
            b.append("    @Override\n");
            b.append("    ").append(renderMethodSignature(method)).append(" {\n");
            b.append("        ").append(renderForwardingBody(method, protocol)).append('\n');
            b.append("    }\n");
        }

        b.append("}\n");

        JavaFileObject src = filer.createSourceFile(fqn, m.api);
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

    /** The forwarding body: {@code return ((Protocol) server).name(args);} (or no return for void). */
    private String renderForwardingBody(ExecutableElement method, String protocol) {
        StringBuilder sb = new StringBuilder();
        boolean isVoid = method.getReturnType().getKind() == TypeKind.VOID;
        if (!isVoid) {
            sb.append("return ");
        }
        sb.append("((").append(protocol).append(") server).")
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

    /** Reads a {@code Class<?>}-valued annotation member as a TypeMirror (survives
     *  the {@code MirroredTypeException} that reading a live {@code Class} would throw). */
    private TypeMirror annotationClassValue(Element e, String annFqn, String member) {
        for (javax.lang.model.element.AnnotationMirror am : e.getAnnotationMirrors()) {
            if (!isType(am.getAnnotationType(), annFqn)) {
                continue;
            }
            for (var en : am.getElementValues().entrySet()) {
                if (en.getKey().getSimpleName().contentEquals(member)) {
                    Object v = en.getValue().getValue();
                    if (v instanceof TypeMirror) {
                        return (TypeMirror) v;
                    }
                }
            }
        }
        return null;
    }
}
