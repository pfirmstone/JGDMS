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
package org.apache.river.tool.preferred;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * The raw bytecode facts the {@link ClassSignalScanner} gathers for a single
 * class.  These are interpreted by the {@link DecisionEngine} (which applies
 * the configurable type sets and the &sect;2 decision logic) to produce a
 * {@link ClassDecision}.
 *
 * <p>All names are JVM internal names (using {@code /} separators).
 */
public final class ClassSignals {

    private final String   internalName;
    private final boolean  isPublic;
    private final boolean  isInterface;
    private final String   superName;
    private final String[] interfaces;

    private boolean implementsSerializableDirectly;
    private boolean atomicSerial;
    /** Internal name of the immediately enclosing class, or {@code null}. */
    private String  enclosingClass;

    private final List<StaticFieldInfo> staticFields = new ArrayList<StaticFieldInfo>();

    /** Method "name+descriptor" entries that are {@code static synchronized}. */
    private final Set<String> staticSynchronizedMethods = new LinkedHashSet<String>();

    /** Names of this class's static fields used as a {@code synchronized(...)} monitor. */
    private final Set<String> synchronizedOnStaticFields = new LinkedHashSet<String>();

    /** Human-readable "method &rarr; blocking call" evidence under a static lock. */
    private final List<String> blockingUnderStaticLock = new ArrayList<String>();

    /**
     * Static executor fields whose {@code <clinit>} initializer is a
     * virtual-thread factory ({@code Executors.newVirtualThreadPerTaskExecutor()}):
     * these have no thread-starvation coupling and are not a contention hazard
     * (SOW &sect;9.2).
     */
    private final Set<String> virtualThreadExecutorFields = new LinkedHashSet<String>();

    ClassSignals(String internalName, boolean isPublic, boolean isInterface,
                 String superName, String[] interfaces) {
        this.internalName = internalName;
        this.isPublic     = isPublic;
        this.isInterface  = isInterface;
        this.superName    = superName;
        this.interfaces   = (interfaces == null) ? new String[0] : interfaces.clone();
    }

    public String getInternalName() {
        return internalName;
    }

    public boolean isPublic() {
        return isPublic;
    }

    public boolean isInterface() {
        return isInterface;
    }

    public String getSuperName() {
        return superName;
    }

    /** Directly declared interfaces (internal names). Never null. */
    public String[] getInterfaces() {
        return interfaces.clone();
    }

    /** True if this class directly declares {@code implements java.io.Serializable}. */
    public boolean implementsSerializableDirectly() {
        return implementsSerializableDirectly;
    }

    void setImplementsSerializableDirectly(boolean v) {
        this.implementsSerializableDirectly = v;
    }

    /** True if the class carries {@code @org.apache.river.api.io.AtomicSerial}. */
    public boolean isAtomicSerial() {
        return atomicSerial;
    }

    void setAtomicSerial(boolean v) {
        this.atomicSerial = v;
    }

    public String getEnclosingClass() {
        return enclosingClass;
    }

    void setEnclosingClass(String v) {
        this.enclosingClass = v;
    }

    /** The package of this class as an internal name (e.g. {@code net/jini/id}). */
    public String getPackageName() {
        int slash = internalName.lastIndexOf('/');
        return (slash < 0) ? "" : internalName.substring(0, slash);
    }

    public List<StaticFieldInfo> getStaticFields() {
        return staticFields;
    }

    void addStaticField(StaticFieldInfo f) {
        staticFields.add(f);
    }

    StaticFieldInfo findStaticField(String name) {
        for (StaticFieldInfo f : staticFields) {
            if (f.getName().equals(name)) return f;
        }
        return null;
    }

    public Set<String> getStaticSynchronizedMethods() {
        return staticSynchronizedMethods;
    }

    void addStaticSynchronizedMethod(String nameAndDesc) {
        staticSynchronizedMethods.add(nameAndDesc);
    }

    public Set<String> getSynchronizedOnStaticFields() {
        return synchronizedOnStaticFields;
    }

    void addSynchronizedOnStaticField(String fieldName) {
        synchronizedOnStaticFields.add(fieldName);
    }

    public List<String> getBlockingUnderStaticLock() {
        return blockingUnderStaticLock;
    }

    void addBlockingUnderStaticLock(String evidence) {
        blockingUnderStaticLock.add(evidence);
    }

    public Set<String> getVirtualThreadExecutorFields() {
        return virtualThreadExecutorFields;
    }

    void addVirtualThreadExecutorField(String fieldName) {
        virtualThreadExecutorFields.add(fieldName);
    }

    /** Unmodifiable empty signals object for a class that failed to parse. */
    static ClassSignals unparseable(String internalName) {
        return new ClassSignals(internalName, false, false, null,
                new String[0]);
    }

    @Override
    public String toString() {
        return "ClassSignals[" + internalName
                + ", staticFields=" + staticFields.size()
                + ", staticSync=" + staticSynchronizedMethods.size()
                + ", syncOnStatic=" + synchronizedOnStaticFields.size()
                + ", serializable=" + implementsSerializableDirectly
                + ", atomicSerial=" + atomicSerial + "]";
    }

    /** Returns an immutable snapshot copy of a list (helper for callers). */
    static <T> List<T> immutable(List<T> in) {
        return Collections.unmodifiableList(new ArrayList<T>(in));
    }
}
