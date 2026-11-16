/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package au.net.zeus.jgdms.der.inspect;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * One recognised endpoint record found by {@link EndpointLocator}.
 *
 * <p>All values are class-free: strings, boxed scalars, {@code byte[]},
 * {@link au.net.zeus.jgdms.der.getarg.EnumName} and
 * {@link au.net.zeus.jgdms.der.getarg.ClassName}. No class named by the data was loaded to
 * produce them.
 *
 * @param path          where the endpoint sits, from the root ({@code $}): field names,
 *                      {@code [i]} element indexes, {@code [key i]} / {@code [value i]} map
 *                      entries, {@code <handler>} for a proxy's invocation handler and
 *                      {@code <payload>} for the content of a {@code MarshalledInstance}
 * @param className     the endpoint record's leaf class name, as named by its schema
 * @param classChain    the record's schema class names, leaf first
 * @param fields        every scalar field of every class in the chain, by wire name (a name
 *                      declared by more than one class is qualified as {@code Class#name});
 *                      nested-object, array-of-object and collection fields are omitted
 * @param host          the value of the configured host field, or {@code null} if the class
 *                      has none configured or the value is null
 * @param port          the value of the configured port field, or {@code null} if the class
 *                      has none configured
 * @param socketFactory the socket factory, or {@code null} if the field is null or none is
 *                      configured
 * @param location      where the endpoint's bytes are, for a rewriter
 */
public record EndpointFinding(String path,
                              String className,
                              List<String> classChain,
                              Map<String, Object> fields,
                              String host,
                              Integer port,
                              SocketFactory socketFactory,
                              Location location) {

    /**
     * Creates a finding; lists and maps are copied unmodifiable.
     */
    public EndpointFinding {
        Objects.requireNonNull(path, "path");
        Objects.requireNonNull(className, "className");
        classChain = List.copyOf(classChain);
        fields = java.util.Collections.unmodifiableMap(new java.util.LinkedHashMap<>(fields));
        Objects.requireNonNull(location, "location");
    }

    /**
     * What a socket factory slot holds, without any class loaded.
     *
     * @param kind       the wire form of the slot's value
     * @param classNames for {@link Kind#RECORD} the factory record's schema class names, leaf
     *                   first; for {@link Kind#PROXY} the proxy's interface names; for
     *                   {@link Kind#ENUM} the enum's class name
     * @param constant   the enum constant name for {@link Kind#ENUM}, else {@code null}
     */
    public record SocketFactory(Kind kind, List<String> classNames, String constant) {

        /** Wire forms a non-null polymorphic slot can take. */
        public enum Kind {
            /** A nested {@code @AtomicSerial} record. */
            RECORD,
            /** A {@code [8]} dynamic proxy. */
            PROXY,
            /** A {@code [7]} enum constant. */
            ENUM
        }

        /**
         * Creates a description; the list is copied unmodifiable.
         */
        public SocketFactory {
            Objects.requireNonNull(kind, "kind");
            classNames = List.copyOf(classNames);
        }
    }

    /**
     * How the endpoint's byte range is to be read.
     */
    public enum Form {
        /**
         * The range is a nested-record TLV, {@code SEQUENCE { OCTET STRING schemaChain,
         * OCTET STRING payload }}; it carries its own schema and can be replaced as a unit.
         */
        NESTED_RECORD,
        /**
         * The range is a {@code [1]} object-stream item whose content is a
         * {@code MarshalledInstanceRecord}; it carries its own schema.
         */
        STREAM_ITEM,
        /**
         * The range is a bare hierarchy payload whose schema travels separately: in the
         * {@code schemaBytes} of the top-level record, or in the {@code schemaBytes} field of the
         * {@code MarshalledInstance} whose payload it is (the innermost enclosing
         * {@link Kind#MARSHALLED_INSTANCE_PAYLOAD}). Replacing it means replacing that schema
         * (and schema digest) too.
         */
        PAYLOAD
    }

    /**
     * The roles an enclosing TLV can play.
     */
    public enum Kind {
        /** A nested-record {@code SEQUENCE { schemaChain, payload }}. */
        NESTED_RECORD,
        /** The payload {@code OCTET STRING} of a nested record. */
        RECORD_PAYLOAD,
        /** A hierarchy payload: the outer {@code SEQUENCE} of per-class {@code SEQUENCE}s. */
        HIERARCHY,
        /** One class's private field {@code SEQUENCE}. */
        CLASS_FIELDS,
        /** A {@code [8]} proxy (field form or object-stream form). */
        PROXY,
        /** The element {@code SEQUENCE} of an {@code array:@AtomicSerial:} field. */
        ARRAY,
        /** A collection or map body ({@code SET OF} or {@code SEQUENCE OF}). */
        COLLECTION,
        /** One map entry {@code SEQUENCE { key, value }}. */
        MAP_ENTRY,
        /** An explicit {@code Any} wrapper ({@code [20]}, {@code [30]} or {@code [31]}). */
        ANY,
        /**
         * The {@code payloadBytes OCTET STRING} field of a {@code net.jini.io.MarshalledInstance}
         * record; see {@link Enclosing#marshalledInstanceHash()}.
         */
        MARSHALLED_INSTANCE_PAYLOAD,
        /** A {@code [1]} object-stream item. */
        STREAM_ATOMIC,
        /** The {@code MarshalledInstanceRecord SEQUENCE} inside a {@code [1]} item. */
        STREAM_RECORD,
        /** The payload {@code OCTET STRING} of that record. */
        STREAM_RECORD_PAYLOAD
    }

    /**
     * Byte location of an endpoint within the input given to {@link EndpointLocator}.
     *
     * <p>Every offset is an absolute index into that input (for
     * {@link EndpointLocator#locate(au.net.zeus.jgdms.der.marshal.MarshalledInstanceRecord)},
     * into the record's {@code payloadBytes}). Nested payloads are carried in definite-length
     * {@code OCTET STRING}s and are therefore physically contiguous inside their parents at every
     * level, including the payload of a nested {@code MarshalledInstance}; a single absolute
     * range is enough.
     *
     * <p>To replace the endpoint, a rewriter replaces {@code [offset, offset + length)} and then
     * rewrites the length octets of every entry of {@link #enclosing()}, innermost first (a length
     * change can change the size of the length octets themselves, which shifts every later
     * offset). For each {@link Kind#MARSHALLED_INSTANCE_PAYLOAD} entry it must also recompute that
     * {@code MarshalledInstance}'s {@code hash} over the new payload content and rewrite the hash
     * TLV. For each entry whose {@link Enclosing#reorderOnChange()} is true it must re-sort that
     * {@code SET OF} by element (or map key) encoding.
     *
     * @param form      how the range is to be read
     * @param offset    absolute offset of the endpoint's first byte
     * @param length    length of the endpoint's range
     * @param enclosing every TLV that contains the range, outermost first
     */
    public record Location(Form form, int offset, int length, List<Enclosing> enclosing) {

        /**
         * Creates a location; the list is copied unmodifiable.
         */
        public Location {
            Objects.requireNonNull(form, "form");
            enclosing = List.copyOf(enclosing);
        }
    }

    /**
     * One TLV that contains an endpoint's range.
     *
     * @param kind                   the role of this TLV
     * @param path                   the path of the value this TLV belongs to
     * @param offset                 absolute offset of the TLV's first (tag) byte
     * @param headerLength           number of tag and length octets
     * @param contentLength          number of content octets
     * @param reorderOnChange        true when this is a canonical {@code SET OF} and the endpoint is
     *                               inside an element (or a map key), so changing the endpoint can
     *                               change the required element order
     * @param marshalledInstanceHash for {@link Kind#MARSHALLED_INSTANCE_PAYLOAD}, the location of
     *                               that {@code MarshalledInstance}'s {@code hash} field; otherwise
     *                               {@code null}
     */
    public record Enclosing(Kind kind, String path, int offset, int headerLength, int contentLength,
                            boolean reorderOnChange, MarshalledInstanceHash marshalledInstanceHash) {

        /**
         * Creates an entry.
         */
        public Enclosing {
            Objects.requireNonNull(kind, "kind");
            Objects.requireNonNull(path, "path");
        }

        /**
         * Absolute offset of the first content octet.
         *
         * @return {@code offset + headerLength}
         */
        public int contentOffset() {
            return offset + headerLength;
        }

        /**
         * Absolute offset one past the TLV's last octet.
         *
         * @return {@code offset + headerLength + contentLength}
         */
        public int end() {
            return offset + headerLength + contentLength;
        }
    }

    /**
     * Where a {@code MarshalledInstance}'s {@code hash} field is. The hash TLV is in the same
     * class {@code SEQUENCE} as the {@code payloadBytes} field and after it, so its enclosing TLVs
     * are the entries that precede the {@link Kind#MARSHALLED_INSTANCE_PAYLOAD} entry; its offset
     * moves by however much the payload field's size changes.
     *
     * @param offset       absolute offset of the hash {@code INTEGER} TLV
     * @param length       length of that TLV
     * @param value        the hash value carried, which the locator checked against the payload
     */
    public record MarshalledInstanceHash(int offset, int length, int value) {
    }
}
