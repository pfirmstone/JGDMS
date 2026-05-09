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

package org.apache.river.api.io;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InvalidObjectException;
import java.io.NotSerializableException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.ObjectStreamException;
import java.io.ObjectStreamField;
import java.io.Serializable;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.security.AccessControlContext;
import java.security.AccessController;
import java.security.CodeSource;
import java.security.DomainCombiner;
import java.security.Principal;
import java.security.PrivilegedAction;
import java.security.ProtectionDomain;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.nio.charset.StandardCharsets;
import javax.security.auth.Subject;
import net.jini.security.Security;
import org.apache.river.api.io.AtomicSerial.GetArg;
import org.apache.river.api.io.AtomicSerial.PutArg;
import org.apache.river.api.io.AtomicSerial.SerialForm;
import org.apache.river.api.net.Uri;

/**
 * Serializer for AccessControlContext.
 */
@Serializer(replaceObType = AccessControlContext.class)
@AtomicSerial
public final class AccessControlContextSerializer implements Serializable {
    private static final long serialVersionUID = 1L;
    private static final String DOMAINS = "domains";
    private static final int HTTPMD_PREFIX_LENGTH = 7;
    /** Maximum number of ProtectionDomain records accepted during unmarshal. */
    private static final int MAX_DOMAIN_COUNT = 4096;
    /** Maximum UTF-8 byte length accepted for a location URL field. */
    private static final int MAX_LOCATION_BYTES = 4096;
    /** Maximum number of principals accepted per ProtectionDomain record. */
    private static final int MAX_PRINCIPALS_PER_DOMAIN = 256;
    /** Maximum UTF-8 byte length accepted for a principal type or name field. */
    private static final int MAX_PRINCIPAL_FIELD_BYTES = 4096;
    private static final ObjectStreamField[] serialPersistentFields = serialForm();

    public static SerialForm[] serialForm() {
        return new SerialForm[]{
            new SerialForm(DOMAINS, DomainIdentityRecord[].class)
        };
    }

    public static void serialize(PutArg arg, AccessControlContextSerializer obj) throws IOException {
        arg.put(DOMAINS, obj.domains);
        arg.writeArgs();
    }

    public static byte[] marshalForTransport(AccessControlContext acc) throws IOException {
        if (acc == null) return new byte[0];
        DomainIdentityRecord[] records = recordsFromContext(acc, null);
        if (records.length == 0) return new byte[0];
        ByteArrayOutputStream baos = new ByteArrayOutputStream(512);
        writeInt(baos, records.length);
        for (int i = 0; i < records.length; i++) {
            records[i].writeTo(baos);
        }
        return baos.toByteArray();
    }

    public static AccessControlContext unmarshalForTransport(byte[] data, Subject authenticatedSubject) throws IOException {
        if (data == null || data.length == 0) {
            return null;
        }
        ByteArrayInputStream in = new ByteArrayInputStream(data);
        int count = readInt(in);
        if (count < 0 || count > MAX_DOMAIN_COUNT) {
            throw new InvalidObjectException("invalid domain count: " + count);
        }
        DomainIdentityRecord[] records = new DomainIdentityRecord[count];
        for (int i = 0; i < count; i++) {
            records[i] = DomainIdentityRecord.readFrom(in);
        }
        if (in.read() != -1) {
            throw new InvalidObjectException("unexpected trailing bytes");
        }
        ProtectionDomain[] filtered = new FilteringDomainCombiner(authenticatedSubject).combine(
            toProtectionDomains(records, authenticatedSubject),
            null
        );
        return new AccessControlContext(filtered != null ? filtered : new ProtectionDomain[0]);
    }

    private final DomainIdentityRecord[] domains;
    private final transient AccessControlContext context;

    AccessControlContextSerializer(GetArg arg) throws IOException, ClassNotFoundException {
        this(arg.get(DOMAINS, null, DomainIdentityRecord[].class));
    }

    AccessControlContextSerializer(AccessControlContext context) {
        this(recordsFromContext(context, null), context);
    }

    private AccessControlContextSerializer(DomainIdentityRecord[] domains) throws IOException {
        this(domains, new AccessControlContext(toProtectionDomains(domains, null)));
    }

    private AccessControlContextSerializer(DomainIdentityRecord[] domains, AccessControlContext context) {
        this.domains = domains != null ? domains : new DomainIdentityRecord[0];
        this.context = context;
    }

    Object readResolve() throws ObjectStreamException {
        return context;
    }

    private static DomainIdentityRecord[] recordsFromContext(AccessControlContext acc, Subject subjectOverride) {
        ProtectionDomain[] extracted = extractDomains(acc);
        if (extracted.length == 0) return new DomainIdentityRecord[0];
        ProtectionDomain[] filtered = new FilteringDomainCombiner(subjectOverride).combine(extracted, null);
        if (filtered == null || filtered.length == 0) return new DomainIdentityRecord[0];
        List<DomainIdentityRecord> out = new ArrayList<DomainIdentityRecord>(filtered.length);
        for (int i = 0; i < filtered.length; i++) {
            DomainIdentityRecord r = DomainIdentityRecord.from(filtered[i], subjectOverride);
            if (r != null) out.add(r);
        }
        return out.toArray(new DomainIdentityRecord[out.size()]);
    }

    private static ProtectionDomain[] extractDomains(final AccessControlContext acc) {
        if (acc == null) return new ProtectionDomain[0];
        final ExtractingDomainCombiner extractor = new ExtractingDomainCombiner(acc.getDomainCombiner());
        final AccessControlContext wrapped = new AccessControlContext(acc, extractor);
        AccessController.doPrivileged((PrivilegedAction<Void>) () -> {
            try {
                AccessController.checkPermission(new RuntimePermission("accessClassInPackage.java.lang"));
            } catch (SecurityException ignore) {
            }
            return null;
        }, wrapped);
        return extractor.getCaptured();
    }

    private static ProtectionDomain[] toProtectionDomains(DomainIdentityRecord[] records, Subject authenticatedSubject) {
        if (records == null || records.length == 0) return new ProtectionDomain[0];
        List<ProtectionDomain> domains = new ArrayList<ProtectionDomain>(records.length);
        for (int i = 0; i < records.length; i++) {
            try {
                ProtectionDomain pd = records[i].toProtectionDomain(authenticatedSubject);
                if (pd != null) domains.add(pd);
            } catch (IOException ex) {
                // Drop unverifiable/invalid domains during reconstruction.
            }
        }
        return domains.toArray(new ProtectionDomain[domains.size()]);
    }

    private static boolean isVerifiableHttpmd(String location) {
        if (location == null) return false;
        if (!location.regionMatches(true, 0, "httpmd:", 0, HTTPMD_PREFIX_LENGTH)) return false;
        try {
            Security.verifyCodebaseIntegrity(location, AccessControlContextSerializer.class.getClassLoader());
            return true;
        } catch (SecurityException ex) {
            return false;
        } catch (MalformedURLException ex) {
            return false;
        }
    }

    private static URL parseHttpmd(String location) throws IOException {
        try {
            Uri uri = Uri.parseAndCreate(location);
            return uri.toURL();
        } catch (URISyntaxException ex) {
            InvalidObjectException e = new InvalidObjectException("invalid httpmd URL");
            e.initCause(ex);
            throw e;
        } catch (MalformedURLException ex) {
            InvalidObjectException e = new InvalidObjectException("invalid httpmd URL");
            e.initCause(ex);
            throw e;
        }
    }

    private static Principal[] principalsFor(ProtectionDomain pd, Subject authenticatedSubject) {
        if (authenticatedSubject != null) {
            Set<Principal> principalSet = authenticatedSubject.getPrincipals();
            return principalSet.toArray(new Principal[0]);
        }
        Principal[] principals = pd.getPrincipals();
        return principals != null ? principals : new Principal[0];
    }

    private static void writeInt(ByteArrayOutputStream out, int value) {
        out.write((value >>> 24) & 0xFF);
        out.write((value >>> 16) & 0xFF);
        out.write((value >>> 8) & 0xFF);
        out.write(value & 0xFF);
    }

    private static int readInt(ByteArrayInputStream in) throws IOException {
        int b1 = in.read();
        int b2 = in.read();
        int b3 = in.read();
        int b4 = in.read();
        if ((b1 | b2 | b3 | b4) < 0) throw new InvalidObjectException("Unexpected EOF reading integer value");
        return ((b1 & 0xFF) << 24) | ((b2 & 0xFF) << 16) | ((b3 & 0xFF) << 8) | (b4 & 0xFF);
    }

    private static void writeShort(ByteArrayOutputStream out, int value) {
        out.write((value >>> 8) & 0xFF);
        out.write(value & 0xFF);
    }

    private static int readShort(ByteArrayInputStream in) throws IOException {
        int hi = in.read();
        int lo = in.read();
        if ((hi | lo) < 0) throw new InvalidObjectException("Unexpected EOF reading short value");
        return ((hi & 0xFF) << 8) | (lo & 0xFF);
    }

    private static final class ExtractingDomainCombiner implements DomainCombiner {
        private final DomainCombiner delegate;
        private volatile ProtectionDomain[] captured = new ProtectionDomain[0];

        private ExtractingDomainCombiner(DomainCombiner delegate) {
            this.delegate = delegate;
        }

        public ProtectionDomain[] combine(ProtectionDomain[] current, ProtectionDomain[] assigned) {
            List<ProtectionDomain> collected = new ArrayList<ProtectionDomain>(8);
            if (current != null) Collections.addAll(collected, current);
            if (assigned != null) Collections.addAll(collected, assigned);
            captured = collected.toArray(new ProtectionDomain[collected.size()]);
            if (delegate != null) {
                return delegate.combine(current, assigned);
            }
            return current != null ? current : assigned;
        }

        private ProtectionDomain[] getCaptured() {
            return captured;
        }
    }

    private static final class FilteringDomainCombiner implements DomainCombiner {
        private final Subject authenticatedSubject;

        private FilteringDomainCombiner(Subject authenticatedSubject) {
            this.authenticatedSubject = authenticatedSubject;
        }

        public ProtectionDomain[] combine(ProtectionDomain[] current, ProtectionDomain[] assigned) {
            List<ProtectionDomain> in = new ArrayList<ProtectionDomain>(8);
            if (current != null) Collections.addAll(in, current);
            if (assigned != null) Collections.addAll(in, assigned);
            if (in.isEmpty()) return new ProtectionDomain[0];
            List<ProtectionDomain> out = new ArrayList<ProtectionDomain>(in.size());
            for (int i = 0; i < in.size(); i++) {
                ProtectionDomain pd = in.get(i);
                if (pd == null) continue;
                CodeSource cs = pd.getCodeSource();
                URL loc = cs != null ? cs.getLocation() : null;
                String locText = loc != null ? loc.toExternalForm() : null;
                if (!isVerifiableHttpmd(locText)) continue;
                Principal[] principals = principalsFor(pd, authenticatedSubject);
                out.add(new DomainIdentity(new CodeSource(loc, cs != null ? cs.getCertificates() : null), principals));
            }
            return out.toArray(new ProtectionDomain[out.size()]);
        }
    }

    @AtomicSerial
    static final class DomainIdentityRecord implements Serializable {
        private static final long serialVersionUID = 1L;
        private static final String LOCATION = "location";
        private static final String PRINCIPAL_TYPES = "principalTypes";
        private static final String PRINCIPAL_NAMES = "principalNames";
        private static final ObjectStreamField[] serialPersistentFields = serialForm();

        static SerialForm[] serialForm() {
            return new SerialForm[]{
                new SerialForm(LOCATION, String.class),
                new SerialForm(PRINCIPAL_TYPES, String[].class),
                new SerialForm(PRINCIPAL_NAMES, String[].class)
            };
        }

        static void serialize(PutArg arg, DomainIdentityRecord obj) throws IOException {
            arg.put(LOCATION, obj.location);
            arg.put(PRINCIPAL_TYPES, obj.principalTypes);
            arg.put(PRINCIPAL_NAMES, obj.principalNames);
            arg.writeArgs();
        }

        static DomainIdentityRecord from(ProtectionDomain pd, Subject authenticatedSubject) {
            CodeSource cs = pd.getCodeSource();
            URL location = cs != null ? cs.getLocation() : null;
            String locText = location != null ? location.toExternalForm() : null;
            if (!isVerifiableHttpmd(locText)) return null;
            Principal[] principals = principalsFor(pd, authenticatedSubject);
            String[] types = new String[principals.length];
            String[] names = new String[principals.length];
            for (int i = 0; i < principals.length; i++) {
                types[i] = principals[i].getClass().getName();
                names[i] = principals[i].getName();
            }
            return new DomainIdentityRecord(locText, types, names);
        }

        static DomainIdentityRecord readFrom(ByteArrayInputStream in) throws IOException {
            int locLen = readShort(in);
            if (locLen > MAX_LOCATION_BYTES) {
                throw new InvalidObjectException("location length exceeds maximum: " + locLen);
            }
            byte[] locationBytes = new byte[locLen];
            if (in.read(locationBytes) != locLen) throw new InvalidObjectException("Unexpected EOF reading location bytes");
            String location = new String(locationBytes, StandardCharsets.UTF_8);
            int principalCount = readShort(in);
            if (principalCount > MAX_PRINCIPALS_PER_DOMAIN) {
                throw new InvalidObjectException("principal count exceeds maximum: " + principalCount);
            }
            String[] types = new String[principalCount];
            String[] names = new String[principalCount];
            for (int i = 0; i < principalCount; i++) {
                int typeLen = readShort(in);
                if (typeLen > MAX_PRINCIPAL_FIELD_BYTES) {
                    throw new InvalidObjectException("principal type length exceeds maximum: " + typeLen);
                }
                byte[] typeBytes = new byte[typeLen];
                if (in.read(typeBytes) != typeLen) throw new InvalidObjectException("Unexpected EOF reading principal type");
                types[i] = new String(typeBytes, StandardCharsets.UTF_8);
                int nameLen = readShort(in);
                if (nameLen > MAX_PRINCIPAL_FIELD_BYTES) {
                    throw new InvalidObjectException("principal name length exceeds maximum: " + nameLen);
                }
                byte[] nameBytes = new byte[nameLen];
                if (in.read(nameBytes) != nameLen) throw new InvalidObjectException("Unexpected EOF reading principal name");
                names[i] = new String(nameBytes, StandardCharsets.UTF_8);
            }
            return new DomainIdentityRecord(location, types, names);
        }

        private final String location;
        private final String[] principalTypes;
        private final String[] principalNames;

        DomainIdentityRecord(GetArg arg) throws IOException, ClassNotFoundException {
            this(
                arg.get(LOCATION, null, String.class),
                arg.get(PRINCIPAL_TYPES, null, String[].class),
                arg.get(PRINCIPAL_NAMES, null, String[].class)
            );
        }

        private DomainIdentityRecord(String location, String[] principalTypes, String[] principalNames) {
            this.location = location;
            this.principalTypes = principalTypes != null ? principalTypes : new String[0];
            this.principalNames = principalNames != null ? principalNames : new String[0];
        }

        private void writeTo(ByteArrayOutputStream out) throws IOException {
            byte[] loc = location.getBytes(StandardCharsets.UTF_8);
            if (loc.length > MAX_LOCATION_BYTES) {
                throw new InvalidObjectException("location too long to encode: " + loc.length);
            }
            writeShort(out, loc.length);
            out.write(loc);
            if (principalTypes.length != principalNames.length) {
                throw new InvalidObjectException("principal type/name length mismatch");
            }
            int count = principalTypes.length;
            if (count > MAX_PRINCIPALS_PER_DOMAIN) {
                throw new InvalidObjectException("principal count exceeds maximum: " + count);
            }
            writeShort(out, count);
            for (int i = 0; i < count; i++) {
                byte[] type = principalTypes[i].getBytes(StandardCharsets.UTF_8);
                byte[] name = principalNames[i].getBytes(StandardCharsets.UTF_8);
                if (type.length > MAX_PRINCIPAL_FIELD_BYTES) {
                    throw new InvalidObjectException("principal type too long to encode: " + type.length);
                }
                if (name.length > MAX_PRINCIPAL_FIELD_BYTES) {
                    throw new InvalidObjectException("principal name too long to encode: " + name.length);
                }
                writeShort(out, type.length);
                out.write(type);
                writeShort(out, name.length);
                out.write(name);
            }
        }

        private ProtectionDomain toProtectionDomain(Subject authenticatedSubject) throws IOException {
            if (!isVerifiableHttpmd(location)) return null;
            URL url = parseHttpmd(location);
            Principal[] principals;
            if (authenticatedSubject != null) {
                Set<Principal> principalSet = authenticatedSubject.getPrincipals();
                principals = principalSet.toArray(new Principal[0]);
            } else {
                if (principalTypes.length != principalNames.length) {
                    throw new InvalidObjectException("principal type/name length mismatch");
                }
                principals = new Principal[principalTypes.length];
                for (int i = 0; i < principals.length; i++) {
                    principals[i] = new NamedPrincipal(principalNames[i]);
                }
            }
            return new DomainIdentity(new CodeSource(url, (java.security.cert.Certificate[]) null), principals);
        }

        private void writeObject(ObjectOutputStream out) throws IOException {
            throw new NotSerializableException(
                "DomainIdentityRecord must be serialized using @AtomicSerial transport records only");
        }

        private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
            throw new NotSerializableException(
                "DomainIdentityRecord must be deserialized using @AtomicSerial transport records only");
        }
    }

    static final class DomainIdentity extends ProtectionDomain {

        DomainIdentity(CodeSource cs, Principal[] principals) {
            super(cs, null, null, principals);
        }
        
        private void writeObject(ObjectOutputStream out) throws IOException {
            throw new NotSerializableException("DomainIdentity must be serialized using @AtomicSerial transport records only");
        }
        
        private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
            throw new NotSerializableException("DomainIdentity must be deserialized using @AtomicSerial transport records only");
        }
    }

    private static final class NamedPrincipal implements Principal, Serializable {
        private static final long serialVersionUID = 1L;
        private final String name;

        private NamedPrincipal(String name) {
            this.name = name;
        }

        public String getName() {
            return name;
        }

        private void writeObject(ObjectOutputStream out) throws IOException {
            throw new NotSerializableException(
                "NamedPrincipal must not be serialized outside of AccessControlContextSerializer");
        }

        private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
            throw new NotSerializableException(
                "NamedPrincipal must not be deserialized outside of AccessControlContextSerializer");
        }
    }

    private void writeObject(ObjectOutputStream out) throws IOException {
        ObjectOutputStream.PutField pf = out.putFields();
        pf.put(DOMAINS, domains);
        out.writeFields();
    }
}
