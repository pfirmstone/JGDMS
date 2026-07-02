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
 * The distilled model of a single {@code @JiniService} API interface: its
 * abstract (public) methods, the resolved internal wire ({@code protocol})
 * interface, the requested {@code generate} set, and whether the developer has
 * already hand-written the backend interface or the proxy class (in which case
 * the processor validates but does not generate them).
 *
 * <p>All resolution is name- and mirror-based; nothing here loads a live
 * {@code Class}, so the processor depends only on {@code java.compiler}.
 */
final class ServiceModel {

    final TypeElement api;

    /** The public (abstract) methods declared by the API interface hierarchy. */
    final List<ExecutableElement> apiMethods = new ArrayList<>();

    /** Resolved internal wire interface ({@code protocol}); defaults to {@link #api}. */
    TypeMirror protocol;

    /** {@code component} config name for the generated wrapper (may be empty). */
    String component = "";

    boolean generateBackend;
    boolean generateProxy;
    boolean generateWrapper;

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

    private ServiceModel(TypeElement api) {
        this.api = api;
    }

    static ServiceModel of(TypeElement api, Elements elements, Types types, Messager messager) {
        ServiceModel m = new ServiceModel(api);
        String simple = api.getSimpleName().toString();
        m.backendSimpleName = simple + "Backend";

        // Collect the public abstract API methods (skip static/default methods
        // and Object methods; the API is an interface).
        for (Element e : elements.getAllMembers(api)) {
            if (e.getKind() != ElementKind.METHOD) {
                continue;
            }
            ExecutableElement me = (ExecutableElement) e;
            if (me.getModifiers().contains(Modifier.STATIC)
                    || me.getModifiers().contains(Modifier.DEFAULT)) {
                continue;
            }
            if (me.getEnclosingElement() != null
                    && "java.lang.Object".contentEquals(
                        ((TypeElement) me.getEnclosingElement()).getQualifiedName())) {
                continue;
            }
            m.apiMethods.add(me);
        }

        // Read @JiniService members.
        AnnotationMirror ann = annotation(api, ServiceProxyProcessor.JINI_SERVICE);
        TypeMirror protocol = null;
        List<String> generate = null;
        String component = "";
        if (ann != null) {
            for (var en : ann.getElementValues().entrySet()) {
                String name = en.getKey().getSimpleName().toString();
                AnnotationValue av = en.getValue();
                switch (name) {
                    case "protocol":
                        if (av.getValue() instanceof TypeMirror) {
                            protocol = (TypeMirror) av.getValue();
                        }
                        break;
                    case "component":
                        component = String.valueOf(av.getValue());
                        break;
                    case "generate":
                        generate = enumConstants(av);
                        break;
                    default:
                        break;
                }
            }
        }
        // protocol default (Void.class) means "same as the annotated API".
        if (protocol == null || isVoid(protocol)) {
            m.protocol = api.asType();
        } else {
            m.protocol = protocol;
        }
        m.component = component;
        // generate default is all three.
        if (generate == null) {
            m.generateBackend = m.generateProxy = m.generateWrapper = true;
        } else {
            m.generateBackend = generate.contains("BACKEND");
            m.generateProxy = generate.contains("PROXY");
            m.generateWrapper = generate.contains("WRAPPER");
        }

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

    /** Reads an array-of-enum annotation value into a list of constant names. */
    @SuppressWarnings("unchecked")
    private static List<String> enumConstants(AnnotationValue av) {
        List<String> out = new ArrayList<>();
        Object v = av.getValue();
        if (v instanceof List<?>) {
            for (Object o : (List<?>) v) {
                if (o instanceof AnnotationValue) {
                    Object ev = ((AnnotationValue) o).getValue();
                    if (ev instanceof VariableElement) {
                        out.add(((VariableElement) ev).getSimpleName().toString());
                    }
                }
            }
        }
        return out;
    }
}
