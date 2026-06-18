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

/**
 * Bytecode facts gathered about a single {@code static} field of a class
 * (SOW &sect;4). Instance fields are not recorded.
 */
public final class StaticFieldInfo {

    private final String  name;
    /** The field's type descriptor (e.g. {@code Ljava/util/Map;} or {@code [B}). */
    private final String  descriptor;
    /** The field's element/object internal name, or {@code null} for primitives. */
    private final String  internalType;
    private final boolean isFinal;
    private boolean       writtenOutsideClinit;
    private boolean       mutatedOutsideClinit;

    StaticFieldInfo(String name, String descriptor, String internalType,
                    boolean isFinal) {
        this.name         = name;
        this.descriptor   = descriptor;
        this.internalType = internalType;
        this.isFinal      = isFinal;
    }

    public String getName() {
        return name;
    }

    public String getDescriptor() {
        return descriptor;
    }

    /**
     * The field's object type as an internal name (e.g.
     * {@code java/util/concurrent/Executor}), or {@code null} when the field is
     * a primitive.  For array fields this is the (object) element type, or
     * {@code null} for primitive-element arrays.
     */
    public String getInternalType() {
        return internalType;
    }

    public boolean isFinal() {
        return isFinal;
    }

    /** True if the field is an array type. */
    public boolean isArray() {
        return descriptor.charAt(0) == '[';
    }

    /**
     * True if this field receives a {@code PUTSTATIC} from a method other than
     * {@code <clinit>} &mdash; i.e. it is mutated (reassigned) at runtime rather
     * than only being initialised once in the static initializer.  A
     * {@code static final} container that is only assigned in {@code <clinit>}
     * but whose <em>contents</em> change is detected separately by the scanner
     * (see {@link ClassSignals}).
     */
    public boolean isWrittenOutsideClinit() {
        return writtenOutsideClinit;
    }

    void setWrittenOutsideClinit(boolean v) {
        this.writtenOutsideClinit = v;
    }

    /**
     * True if a mutating method ({@code add}, {@code put}, {@code remove},
     * {@code clear}, {@code set}, &hellip;) is invoked on this field's value
     * outside {@code <clinit>} &mdash; evidence that a {@code static final}
     * container accumulates per-deployment state at runtime (a registry) rather
     * than being an immutable clinit-only lookup table.
     */
    public boolean isMutatedOutsideClinit() {
        return mutatedOutsideClinit;
    }

    void setMutatedOutsideClinit(boolean v) {
        this.mutatedOutsideClinit = v;
    }

    @Override
    public String toString() {
        return (isFinal ? "static final " : "static ") + descriptor + " " + name;
    }
}
