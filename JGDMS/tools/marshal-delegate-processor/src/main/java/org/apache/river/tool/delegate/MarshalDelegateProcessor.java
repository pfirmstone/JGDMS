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

package org.apache.river.tool.delegate;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.Filer;
import javax.annotation.processing.Messager;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.AnnotationMirror;
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
import javax.tools.Diagnostic.Kind;
import javax.tools.FileObject;
import javax.tools.JavaFileObject;
import javax.tools.StandardLocation;

/**
 * Annotation processor that generates a per-package {@code MarshalDelegate} for
 * the {@code @AtomicSerial} classes that need in-package dispatch (a non-public
 * class, or a non-public single-argument {@code (GetArg)} constructor), and
 * <em>validates</em> the {@code @AtomicSerial} contract by emitting warnings for
 * classes that cannot be marshalled or delegated (incomplete classes, private
 * {@code (GetArg)} constructors).  It does not modify the annotated classes.
 *
 * <p>One {@code GeneratedMarshalDelegate} is emitted per package (skipped when a
 * hand-written {@code MarshalDelegate} already exists there), serving only the
 * classes that need it; fully-public classes use the engine's reflective path.
 * All generated delegates of a module are listed in
 * {@code META-INF/services/org.apache.river.api.io.MarshalDelegate}.
 */
@SupportedAnnotationTypes("org.apache.river.api.io.AtomicSerial")
public final class MarshalDelegateProcessor extends AbstractProcessor {

    private static final String ATOMIC_SERIAL = "org.apache.river.api.io.AtomicSerial";
    private static final String STATELESS = "org.apache.river.api.io.AtomicSerial.Stateless";
    private static final String GET_ARG = "org.apache.river.api.io.AtomicSerial.GetArg";
    private static final String MARSHAL_DELEGATE = "org.apache.river.api.io.MarshalDelegate";
    private static final String GEN = "GeneratedMarshalDelegate";
    private static final String SERVICE = "META-INF/services/" + MARSHAL_DELEGATE;
    private static final String SF = "org.apache.river.api.io.AtomicSerial.SerialForm";
    private static final String PA = "org.apache.river.api.io.AtomicSerial.PutArg";
    private static final String GA = "org.apache.river.api.io.AtomicSerial.GetArg";

    private Elements elements;
    private Messager messager;
    private Filer filer;
    private final Set<String> generated = new LinkedHashSet<>();

    @Override
    public SourceVersion getSupportedSourceVersion() {
        return SourceVersion.latestSupported();
    }

    @Override
    public synchronized void init(ProcessingEnvironment env) {
        super.init(env);
        elements = env.getElementUtils();
        messager = env.getMessager();
        filer = env.getFiler();
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment round) {
        TypeElement as = elements.getTypeElement(ATOMIC_SERIAL);
        if (as != null) {
            Map<String, List<TypeElement>> byPkg = new TreeMap<>();
            for (Element e : round.getElementsAnnotatedWith(as)) {
                if (e.getKind() != ElementKind.CLASS) {
                    continue;
                }
                TypeElement t = (TypeElement) e;
                String pkg = elements.getPackageOf(t).getQualifiedName().toString();
                byPkg.computeIfAbsent(pkg, k -> new ArrayList<>()).add(t);
            }
            for (Map.Entry<String, List<TypeElement>> en : byPkg.entrySet()) {
                try {
                    generatePackage(en.getKey(), en.getValue());
                } catch (IOException ex) {
                    messager.printMessage(Kind.ERROR, "MarshalDelegateProcessor: " + ex);
                }
            }
        }
        if (round.processingOver() && !generated.isEmpty()) {
            writeServiceFile();
        }
        return false; // do not claim the annotation; allow other processors
    }

    // ---- per-class analysis ----

    private static final class Info {
        TypeElement element;
        String relName;       // name relative to the package (Foo or Outer.Inner)
        boolean isPublic, isAbstract, isStateless;
        boolean hasGetArgCtor, ctorPrivate, ctorPublic;
        boolean hasSerialForm, hasSerialize;
        boolean needsDelegate;
    }

    private Info analyze(TypeElement t, String pkg) {
        Info i = new Info();
        i.element = t;
        String canonical = t.getQualifiedName().toString();
        i.relName = canonical.startsWith(pkg + ".") ? canonical.substring(pkg.length() + 1) : canonical;
        Set<Modifier> mods = t.getModifiers();
        i.isPublic = mods.contains(Modifier.PUBLIC);
        i.isAbstract = mods.contains(Modifier.ABSTRACT);
        i.isStateless = hasAnnotation(t, STATELESS);
        for (Element e : t.getEnclosedElements()) {
            if (e.getKind() == ElementKind.CONSTRUCTOR) {
                ExecutableElement c = (ExecutableElement) e;
                List<? extends VariableElement> ps = c.getParameters();
                if (ps.size() == 1 && isType(ps.get(0).asType(), GET_ARG)) {
                    i.hasGetArgCtor = true;
                    i.ctorPrivate = c.getModifiers().contains(Modifier.PRIVATE);
                    i.ctorPublic = c.getModifiers().contains(Modifier.PUBLIC);
                }
            } else if (e.getKind() == ElementKind.METHOD && e.getModifiers().contains(Modifier.STATIC)) {
                ExecutableElement m = (ExecutableElement) e;
                String n = m.getSimpleName().toString();
                if (n.equals("serialForm") && m.getParameters().isEmpty()) {
                    i.hasSerialForm = true;
                } else if (n.equals("serialize") && m.getParameters().size() == 2) {
                    i.hasSerialize = true;
                }
            }
        }
        i.needsDelegate = !i.isPublic || (i.hasGetArgCtor && !i.ctorPublic);
        return i;
    }

    /** Emits contract warnings; never modifies the class. */
    private void validate(Info i) {
        TypeElement t = i.element;
        if (i.hasGetArgCtor && i.ctorPrivate) {
            messager.printMessage(Kind.WARNING,
                "@AtomicSerial: PRIVATE (GetArg) constructor cannot be called by a MarshalDelegate; "
                + "make it package-private to allow removing the setAccessible fallback (4.0.0).", t);
        }
        if (!i.isAbstract && !i.hasGetArgCtor) {
            messager.printMessage(Kind.WARNING,
                "@AtomicSerial: no single-argument (GetArg) constructor; class cannot be deserialized.", t);
        }
        if (!i.isStateless && !i.isAbstract && (!i.hasSerialForm || !i.hasSerialize)) {
            messager.printMessage(Kind.WARNING,
                "@AtomicSerial: incomplete contract -- missing"
                + (!i.hasSerialForm ? " serialForm()" : "")
                + (!i.hasSerialize ? " serialize(PutArg,T)" : "")
                + " and not @Stateless; class cannot be marshalled. Bring it up to spec.", t);
        }
    }

    /** A served class must be fully dispatchable by the generated code. */
    private boolean dispatchable(Info i) {
        if (!i.needsDelegate) {
            return false;
        }
        if (i.ctorPrivate) {
            return false;
        }
        if (i.isAbstract) {
            return i.hasSerialForm && i.hasSerialize; // serialForm/serialize only, no create
        }
        if (!i.hasGetArgCtor) {
            return false;
        }
        if (i.isStateless) {
            return true; // empty serialForm, no-op serialize, create
        }
        return i.hasSerialForm && i.hasSerialize;
    }

    private void generatePackage(String pkg, List<TypeElement> classes) throws IOException {
        if (handWrittenDelegateExists(pkg)) {
            return;
        }
        List<Info> served = new ArrayList<>();
        for (TypeElement t : classes) {
            Info i = analyze(t, pkg);
            validate(i);
            if (dispatchable(i)) {
                served.add(i);
            }
        }
        if (!served.isEmpty()) {
            writeDelegate(pkg, served);
        }
    }

    private void writeDelegate(String pkg, List<Info> served) throws IOException {
        String fqn = pkg + "." + GEN;
        StringBuilder b = new StringBuilder();
        b.append("// Generated by ").append(getClass().getName()).append(" -- do not edit.\n");
        b.append("package ").append(pkg).append(";\n\n");
        b.append("public final class ").append(GEN)
         .append(" implements ").append(MARSHAL_DELEGATE).append(" {\n\n");
        b.append("    private static final ").append(SF).append("[] EMPTY = new ").append(SF).append("[0];\n\n");
        b.append("    public ").append(GEN).append("() { }\n\n");
        b.append("    private static final Class<?>[] SERVED = {\n");
        for (Info i : served) {
            b.append("        ").append(i.relName).append(".class,\n");
        }
        b.append("    };\n\n");
        b.append("    @Override public Class<?>[] servedClasses() { return SERVED.clone(); }\n\n");

        b.append("    @Override\n    @SuppressWarnings({\"unchecked\",\"rawtypes\"})\n");
        b.append("    public ").append(SF).append("[] serialForm(Class<?> c) {\n");
        for (Info i : served) {
            if (i.isStateless) {
                b.append("        if (c == ").append(i.relName).append(".class) return EMPTY;\n");
            } else {
                b.append("        if (c == ").append(i.relName).append(".class) return ").append(i.relName).append(".serialForm();\n");
            }
        }
        b.append("        throw new IllegalArgumentException(unhandled(c));\n    }\n\n");

        b.append("    @Override\n    @SuppressWarnings({\"unchecked\",\"rawtypes\"})\n");
        b.append("    public void serialize(Class<?> c, ").append(PA).append(" arg, Object o) throws java.io.IOException {\n");
        for (Info i : served) {
            if (i.isStateless) {
                b.append("        if (c == ").append(i.relName).append(".class) return;\n");
            } else {
                b.append("        if (c == ").append(i.relName).append(".class) { ").append(i.relName)
                 .append(".serialize(arg, (").append(i.relName).append(") o); return; }\n");
            }
        }
        b.append("        throw new IllegalArgumentException(unhandled(c));\n    }\n\n");

        b.append("    @Override\n    @SuppressWarnings({\"unchecked\",\"rawtypes\"})\n");
        b.append("    public Object create(Class<?> c, ").append(GA).append(" arg) throws java.io.IOException, ClassNotFoundException {\n");
        for (Info i : served) {
            if (!i.isAbstract) {
                b.append("        if (c == ").append(i.relName).append(".class) return new ").append(i.relName).append("(arg);\n");
            }
        }
        b.append("        throw new IllegalArgumentException(unhandled(c));\n    }\n\n");

        b.append("    private static String unhandled(Class<?> c) {\n");
        b.append("        return \"").append(GEN).append(" (").append(pkg).append(") does not serve \" + c.getName();\n");
        b.append("    }\n}\n");

        JavaFileObject src = filer.createSourceFile(fqn, served.get(0).element);
        try (PrintWriter pw = new PrintWriter(src.openWriter())) {
            pw.print(b);
        }
        generated.add(fqn);
    }

    private void writeServiceFile() {
        try {
            FileObject fo = filer.createResource(StandardLocation.CLASS_OUTPUT, "", SERVICE);
            try (PrintWriter pw = new PrintWriter(fo.openWriter())) {
                for (String f : generated) {
                    pw.println(f);
                }
            }
        } catch (IOException ex) {
            messager.printMessage(Kind.ERROR, "MarshalDelegateProcessor: cannot write " + SERVICE + ": " + ex);
        }
    }

    // ---- helpers ----

    private boolean handWrittenDelegateExists(String pkg) {
        PackageElement pe = elements.getPackageElement(pkg);
        if (pe == null) {
            return false;
        }
        for (Element e : pe.getEnclosedElements()) {
            if (e.getKind() == ElementKind.CLASS
                    && !e.getSimpleName().contentEquals(GEN)
                    && implementsMarshalDelegate((TypeElement) e)) {
                return true;
            }
        }
        return false;
    }

    private boolean implementsMarshalDelegate(TypeElement t) {
        for (TypeMirror itf : t.getInterfaces()) {
            if (isType(itf, MARSHAL_DELEGATE)) {
                return true;
            }
        }
        return false;
    }

    private boolean hasAnnotation(Element e, String fqn) {
        for (AnnotationMirror am : e.getAnnotationMirrors()) {
            if (isType(am.getAnnotationType(), fqn)) {
                return true;
            }
        }
        return false;
    }

    private boolean isType(TypeMirror tm, String fqn) {
        if (tm.getKind() != TypeKind.DECLARED) {
            return false;
        }
        Element el = ((DeclaredType) tm).asElement();
        return el instanceof TypeElement && ((TypeElement) el).getQualifiedName().contentEquals(fqn);
    }
}
