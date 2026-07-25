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
package org.apache.river.outrigger.proxy;

import net.jini.core.constraint.MarshallingFormat;
import net.jini.core.entry.Entry;
import net.jini.core.entry.UnusableEntryException;
import net.jini.id.Uuid;
import net.jini.id.UuidFactory;
import net.jini.space.JavaSpace;
import org.apache.river.api.io.AtomicSerial;
import org.apache.river.api.io.AtomicSerial.GetArg;
import org.apache.river.api.io.AtomicSerial.PutArg;
import org.apache.river.api.io.AtomicSerial.SerialForm;
import org.apache.river.api.io.EntryV2Codec;
import org.apache.river.landlord.LeasedResource;
import org.apache.river.logging.Levels;
import org.apache.river.proxy.CodebaseProvider;
import org.apache.river.proxy.MarshalledWrapper;

import java.io.*;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.MalformedURLException;
import java.rmi.MarshalException;
import java.rmi.UnmarshalException;
import java.security.DigestOutputStream;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.ServiceLoader;
import java.util.WeakHashMap;
import java.util.logging.Logger;
import net.jini.core.entry.EntryWireField;
import net.jini.core.entry.GetEntryArg;
import net.jini.core.entry.SerialEntry;

/**
 * An <code>EntryRep</code> object contains a packaged
 * <code>Entry</code> object for communication between the client and a
 * <code>JavaSpace</code>.
 *
 * <h2>EntryRep-v2 (JGDMS-STD-006 EntryRep-v2 amendment)</h2>
 * <p>The v1 {@code MarshalledInstance[] values} array is replaced by a single canonical
 * DER {@code EntryRepV2Body} ({@link #body}). Per-field <em>slice</em> bytes
 * ({@link #sliceBytes}) are cached for matching/indexing: a field's slice bytes are a pure
 * function of the field value alone (context-free), so positional slice-byte comparison in
 * {@link #matches(EntryRep)} preserves v1 template-matching semantics. The DER work is done
 * behind the release-8 {@link EntryV2Codec} SPI, resolved via {@link ServiceLoader} on a
 * DER-capable JVM (the flag-day requirement -- a missing provider fails LOUDLY).
 *
 * @author Sun Microsystems, Inc.
 *
 * @see JavaSpace
 * @see Entry
 */
@AtomicSerial
public class EntryRep implements StorableResource<EntryRep>, LeasedResource {

    /**
     * The canonical {@code EntryRepV2Body} DER bytes (wire + persistence form). Replaces the
     * v1 {@code MarshalledInstance[] values}.
     */
    private volatile byte[] body;

    /**
     * Per-field canonical slice bytes (the byte-equality match unit), derived from
     * {@link #body}. Transient: recomputed by decoding {@link #body} on unmarshal/restore.
     */
    private volatile transient byte[][] sliceBytes;

    /** Per-field wildcard/null marker (true = {@code absent [0]} slice). Transient (derived). */
    private volatile transient boolean[] absent;

    /** The 32-byte {@code entrySchemaDigest} (routing/identity; EXCLUDED from matching). */
    private volatile byte[] entrySchemaDigest;

    private volatile String[]	superclasses;	// class names of the superclasses
    private volatile long[]	hashes;		// superclass hashes
    private volatile long	hash;		// hash for the entry class
    private volatile String	className;	// the class ID of the entry
    private volatile String	codebase;	// the codebase for this entry class
    private volatile Uuid	id;		// space-relative storage id
    private volatile transient long	expires;// expiration time

    /**
     * <code>true</code> if the last time this object was unmarshalled
     * integrity was being enforced, <code>false</code> otherwise.
     */
    private volatile transient boolean integrity;

    /** Comparator for sorting fields */
    private static final FieldComparator comparator = new FieldComparator();

    /**
     * This object represents the passing of a <code>null</code>
     * parameter as a template, which is designed to match any entry.
     * When a <code>null</code> is passed, it is replaced with this
     * rep, which is then handled specially in a few relevant places.
     */
    private static final EntryRep matchAnyRep;

    static {
        classHashes = new WeakHashMap<Class,Long>();
	matchAnyRep = makeMatchAny();
    }

    /**
     * Builds the ``match any'' stand-in for a null template. It is never marshalled on
     * the wire, so it is given a schema-less v2 form (empty body/slices/digest) built
     * WITHOUT the {@link EntryV2Codec} SPI -- a null template must work even on a JVM
     * that lacks a DER provider, and it MUST NOT be fed to the DER decode/guard path.
     */
    private static EntryRep makeMatchAny() {
	try {
	    final Entry anon = new Entry() {
		// keeps tests happy
		static final long serialVersionUID = -4244768995726274609L;
	    };
	    EntryRep r = new EntryRep();
	    r.realClass = anon.getClass();
	    r.className = r.realClass.getName();
	    r.codebase = CodebaseProvider.getClassAnnotation(r.realClass);
	    r.body = new byte[0];
	    r.sliceBytes = new byte[0][];
	    r.absent = new boolean[0];
	    r.entrySchemaDigest = new byte[0];
	    r.hash = findHash(r.realClass, true).longValue();
	    r.superclasses = new String[0];
	    r.hashes = new long[0];
	    return r;
	} catch (MarshalException | UnusableEntryException e) {
	    throw new AssertionError(e);
	}
    }

    /**
     * The realClass object is transient because we neither need nor want
     * it reconstituted on the other side.  All we want is to be able to
     * recreate it on the receiving client side.  If it were not transient,
     * not only would an unnecessary object creation occur, but it might
     * force the download of the actual class to the server.
     */
    private volatile transient Class realClass;	// real class of the contained object

    /**
     * Logger for logging information about operations carried out in
     * the client. Note, we hard code "org.apache.river.outrigger" so
     * we don't drag in OutriggerServerImpl to outrigger-dl.jar.
     */
    private static final Logger logger =
	Logger.getLogger("org.apache.river.outrigger.proxy");

    // -------------------------------------------------------------------------
    // EntryRep-v2 codec SPI (flag-day: a DER-capable JVM is required)
    // -------------------------------------------------------------------------

    /** Lazily-resolved EntryV2Codec provider. */
    private static volatile EntryV2Codec CODEC;

    /**
     * Resolves the {@link EntryV2Codec} provider (mirrors {@code MarshalledInstance}'s
     * {@code MarshalFactoryProvider} ServiceLoader dispatch). Fails LOUDLY when no provider
     * is on the classpath -- a v2-born space requires a DER-capable JVM (flag-day), and a
     * silent fallback is exactly the state the flag-day forbids.
     */
    private static EntryV2Codec codec() {
	EntryV2Codec c = CODEC;
	if (c == null) {
	    for (EntryV2Codec candidate : ServiceLoader.load(
		    EntryV2Codec.class, EntryRep.class.getClassLoader())) {
		c = candidate;
		break;
	    }
	    if (c == null) {
		throw new IllegalStateException(
		    "EntryRep-v2 requires a DER-capable JVM: no "
		    + EntryV2Codec.class.getName() + " provider found on the classpath"
		    + " (jgdms-der). This space is born v2/ATOMIC-DER (flag-day).");
	    }
	    CODEC = c;
	}
	return c;
    }

    /**
     * Set this entry's generic data to be shared with the <code>other</code>
     * object.  Those fields that are object references that will be the same
     * for all objects of the same type are shared this way.
     * <p>
     * Note that <code>codebase</code> is <em>not</em> shared.  If it were,
     * then the failure of one codebase could make all entries inaccessible.
     * Each entry is usable insofar as the codebase under which it was
     * written is usable.
     * @param other object to share this entry's generic data with.
     */
    public synchronized void shareWith(EntryRep other) {
	className = other.className;
	superclasses = other.superclasses;
	hashes = other.hashes;
	hash = other.hash;
    }

    /**
     * Get the entry fields associated with the passed class and put
     * them in a canonical order. The fields are sorted so that fields
     * belonging to a superclasses are before fields belonging to
     * subclasses and within a class fields are ordered
     * lexicographically by their name.
     */
    static private Field[] getFields(Class cl) {
	final Field[] fields = cl.getFields();
	Arrays.sort(fields, comparator);
	return fields;
    }

    /**
     * Cached hash values for all classes we encounter. Weak hash used
     * in case the class is GC'ed from the client's VM.
     */
    static final private WeakHashMap<Class,Long> classHashes;

    /**
     * Lookup the hash value for the given class. If it is not
     * found in the cache, generate the hash for the class and
     * save it.
     */
    static synchronized private Long findHash(Class clazz,
					      boolean marshaling)
	throws MarshalException, UnusableEntryException
    {

	Long hash = classHashes.get(clazz);

	// If hash not cached, calculate it for this class and,
	// recursively, all superclasses
	//
	if (hash == null) {
	    if (clazz.isAnnotationPresent(SerialEntry.class)) {
		hash = computeSerialEntryHash(clazz, marshaling);
	    } else {
		try {
		    Field[] fields = getFields(clazz);
		    MessageDigest md = MessageDigest.getInstance("SHA");
		    DataOutputStream out =
			new DataOutputStream(
			    new DigestOutputStream(new ByteArrayOutputStream(127),
						   md));
		    Class c = clazz.getSuperclass();
		    if (c != Object.class)
			// recursive call
			out.writeLong(findHash(c, marshaling).longValue());

		    // Hash only usable fields, this means that we do not
		    // detect changes in non-usable fields. This should be ok
		    // since those fields do not move between space and client.
		    //
		    for (int i = 0; i < fields.length; i++) {
			if (!usableField(fields[i]))
			    continue;
			out.writeUTF(fields[i].getName());
			out.writeUTF(fields[i].getType().getName());
		    }
		    out.flush();
		    byte[] digest = md.digest();
		    long h = 0;
		    for (int i = Math.min(8, digest.length); --i >= 0; ) {
			h += ((long)(digest[i] & 0xFF)) << (i * 8);
		    }
		    hash = Long.valueOf(h);
		} catch (Exception e) {
		    if (marshaling)
			throw throwNewMarshalException(
			   "Exception calculating entry class hash for " +
			   clazz, e);
		    else
			throw throwNewUnusableEntryException(
			   "Exception calculating entry class hash for " +
			   clazz, e);
		}
	    }
	    classHashes.put(clazz, hash);
	}
	return hash;
    }

    /**
     * Computes the SHA-256 hash for a {@code @SerialEntry} class by invoking
     * its {@code entryForm()} method and hashing the wire field names and
     * types.  SHA-256 is used in preference to SHA-1 for forward compatibility.
     */
    static private Long computeSerialEntryHash(Class clazz, boolean marshaling)
	throws MarshalException, UnusableEntryException
    {
	try {
	    Method entryFormMethod = clazz.getMethod("entryForm");
	    EntryWireField[] wireFields = (EntryWireField[]) entryFormMethod.invoke(null);
	    if (wireFields == null || wireFields.length == 0) {
		if (marshaling)
		    throw throwNewMarshalException(
			clazz.getName() + ".entryForm() returned null or empty array", null);
		else
		    throw throwNewUnusableEntryException(
			clazz.getName() + ".entryForm() returned null or empty array", null);
	    }
	    MessageDigest md = MessageDigest.getInstance("SHA-256");
	    DataOutputStream out =
		new DataOutputStream(
		    new DigestOutputStream(new ByteArrayOutputStream(127), md));
	    Class superclass = clazz.getSuperclass();
	    if (superclass != null && superclass != Object.class) {
		out.writeLong(findHash(superclass, marshaling).longValue());
	    }
	    out.writeUTF(clazz.getName());
	    for (EntryWireField wf : wireFields) {
		out.writeUTF(wf.getName());
		out.writeUTF(wf.getType().getName());
	    }
	    out.flush();
	    byte[] digest = md.digest();
	    long h = 0;
	    for (int i = Math.min(8, digest.length); --i >= 0; ) {
		h += ((long)(digest[i] & 0xFF)) << (i * 8);
	    }
	    return Long.valueOf(h);
	} catch (MarshalException | UnusableEntryException e) {
	    throw e;
	} catch (Exception e) {
	    if (marshaling)
		throw throwNewMarshalException(
		    "Exception calculating @SerialEntry hash for " + clazz, e);
	    else
		throw throwNewUnusableEntryException(
		    "Exception calculating @SerialEntry hash for " + clazz, e);
	}
    }

    /**
     * Create a serialized form of the entry.  If <code>validate</code> is
     * <code>true</code>, basic sanity checks are done on the class to
     * ensure that it meets the requirements to be an <code>Entry</code>.
     * <code>validate</code> is <code>false</code> only when creating the
     * stand-in object for "match any", which is never actually marshalled
     * on the wire and so which doesn't need to be "proper".
     * <p>
     * EntryRep-v2: the entry is encoded to a single canonical DER
     * {@code EntryRepV2Body} via the {@link EntryV2Codec} SPI (always ATOMIC-DER
     * under the born-immutable flag-day). The {@code format} argument is retained
     * for source compatibility; v2 always encodes ATOMIC-DER regardless (the
     * space's born-format is enforced by {@code SpaceProxy2.repFor}).
     */
    private EntryRep(Entry entry, boolean validate, MarshallingFormat format)
	    throws MarshalException {
	realClass = entry.getClass();
	if (validate)
	    ensureValidClass(realClass);
	className = realClass.getName();
	codebase = CodebaseProvider.getClassAnnotation(realClass);

	try {
	    final EntryV2Codec.Encoded enc;
	    if (realClass.isAnnotationPresent(SerialEntry.class)) {
		enc = encodeSerialEntry(realClass, entry);
	    } else {
		// Reflective path: the SPI reads the usable fields in the single
		// FieldComparator order shared with the server/index.
		enc = codec().encodeReflective(realClass, entry);
	    }
	    installBody(enc.body, enc.sliceBytes, enc.entrySchemaDigest);
	} catch (IOException | RuntimeException e) {
	    /* A marshalling failure isn't guaranteed to surface as IOException
	     * (e.g. a non-DER-encodable field value, or a proxy-typed field --
	     * amendment F1); wrap so this constructor honours its documented
	     * "throws only MarshalException" contract. */
	    throw throwNewMarshalException(
		"Can't marshal entry of type " + className, e);
	}

	try {
	    hash = findHash(realClass, true).longValue();
	} catch (UnusableEntryException e) {
	    // Will never happen when we pass true to findHash
	    throw new AssertionError(e);
	}

	// Loop through the supertypes, making a list of all superclasses.
	ArrayList<String> sclasses = new ArrayList<String>();
	ArrayList<Long> shashes = new ArrayList<Long>();
	for (Class c = realClass.getSuperclass();
	     c != Object.class;
	     c = c.getSuperclass())
	{
	    try {
		sclasses.add(c.getName());
		shashes.add(findHash(c, true));
	    } catch (ClassCastException cce) {
		break;	// not Serializable
	    } catch (UnusableEntryException e) {
		// Will never happen when we pass true to findHash
		throw new AssertionError(e);
	    }
	}
	superclasses = sclasses.toArray(new String[sclasses.size()]); // safe publication.
	long [] hashes = new long[shashes.size()];
	for (int i=0; i < hashes.length; i++) {
	    hashes[i] = (shashes.get(i)).longValue();
	}
        this.hashes = hashes; // safe publication.
    }

    /**
     * Encodes a {@code @SerialEntry} instance via its static
     * {@code serialize(PutEntryArg, T)} method (positional field values) and its
     * {@code entryForm()} wire metadata, through the {@link EntryV2Codec} SPI.
     */
    private static EntryV2Codec.Encoded encodeSerialEntry(Class realClass, Entry entry)
	    throws MarshalException {
	try {
	    Method entryFormMethod = realClass.getMethod("entryForm");
	    EntryWireField[] wireFields = (EntryWireField[]) entryFormMethod.invoke(null);
	    OutriggerPutEntryArgImpl putArg = new OutriggerPutEntryArgImpl(wireFields);
	    Method serializeMethod = realClass.getMethod("serialize",
		net.jini.core.entry.PutEntryArg.class, realClass);
	    serializeMethod.invoke(null, putArg, entry);
	    Object[] rawValues = putArg.getResult();

	    String[] wireNames = new String[wireFields.length];
	    Class<?>[] wireTypes = new Class<?>[wireFields.length];
	    for (int i = 0; i < wireFields.length; i++) {
		wireNames[i] = wireFields[i].getName();
		wireTypes[i] = wireFields[i].getType();
	    }
	    // superclass names (routing only)
	    ArrayList<String> supers = new ArrayList<String>();
	    for (Class c = realClass.getSuperclass(); c != null && c != Object.class;
		 c = c.getSuperclass()) {
		supers.add(c.getName());
	    }
	    return codec().encodeSerialEntry(realClass.getName(),
		supers.toArray(new String[0]), wireNames, wireTypes, rawValues);
	} catch (InvocationTargetException e) {
	    Throwable cause = e.getCause();
	    if (cause instanceof IOException)
		throw throwNewMarshalException(
		    "IOException during " + realClass.getName() + ".serialize()",
		    (IOException) cause);
	    throw throwNewMarshalException(
		"Exception during " + realClass.getName() + ".serialize()", e);
	} catch (IOException e) {
	    throw throwNewMarshalException(
		"Cannot marshal @SerialEntry " + realClass.getName(), e);
	} catch (Exception e) {
	    throw throwNewMarshalException(
		"Cannot marshal @SerialEntry " + realClass.getName(), e);
	}
    }

    /** Installs the v2 body + derived slice/absent arrays (safe publication). */
    private void installBody(byte[] body, byte[][] slices, byte[] entrySchemaDigest) {
	final boolean[] a = new boolean[slices.length];
	for (int i = 0; i < slices.length; i++) {
	    a[i] = isAbsentSlice(slices[i]);
	}
	this.sliceBytes = slices;
	this.absent = a;
	this.body = body;
	this.entrySchemaDigest = entrySchemaDigest;
    }

    /**
     * A slice is the {@code absent [0]} marker iff its first byte is the context-tag
     * {@code [0]} primitive (0x80). A {@code value [1]} slice starts with 0xA1.
     */
    private static boolean isAbsentSlice(byte[] slice) {
	return slice != null && slice.length > 0 && (slice[0] & 0xFF) == 0x80;
    }

    /**
     * Create a serialized form of the entry with our object's
     * relevant fields set. Retained for source/binary compatibility; callers that
     * must honor a space's configured (born-immutable) marshalling format should use
     * {@link #EntryRep(Entry, MarshallingFormat)} instead -- see {@code SpaceProxy2.repFor}.
     */
    public EntryRep(Entry entry) throws MarshalException {
	this(entry, true, MarshallingFormat.ATOMIC_DER);
    }

    /**
     * Create a serialized form of the entry with our object's relevant
     * fields set. EntryRep-v2 always encodes the space's single born ATOMIC-DER
     * format; the {@code format} argument is retained for source compatibility.
     * @param entry the entry to marshal.
     * @param format the marshalling format (retained for compatibility; v2 = ATOMIC-DER).
     * @throws NullPointerException if <code>format</code> is <code>null</code>.
     */
    public EntryRep(Entry entry, MarshallingFormat format) throws MarshalException {
	this(entry, true, notNull(format));
    }

    private static MarshallingFormat notNull(MarshallingFormat format) {
	if (format == null)
	    throw new NullPointerException("format cannot be null");
	return format;
    }

    private static boolean checkIntegrity(GetArg arg) throws IOException, ClassNotFoundException {
	byte[] body = (byte[]) arg.get("body", null);
	if (body == null) throw new InvalidObjectException("null body (EntryRep-v2)");
	byte[] entrySchemaDigest = (byte[]) arg.get("entrySchemaDigest", null);
	if (entrySchemaDigest == null) throw new InvalidObjectException("null entrySchemaDigest");
	String[] superclasses = (String[]) arg.get("superclasses", null);
	if (superclasses == null) throw new InvalidObjectException("null superclasses");
	long[]	hashes = (long[]) arg.get("hashes", null);
	if (hashes == null) throw new InvalidObjectException("null hashes");
	if (hashes.length != superclasses.length)
	    throw new InvalidObjectException("hashes.length (" +
                hashes.length + ") does not equal  superclasses.length (" +
	        superclasses.length + ")");
	arg.get("hash", 0L);
	String	className = (String) arg.get("className", null);
	if (className == null) throw new InvalidObjectException("null className");
	Object	codebase = arg.get("codebase", null);
	if (codebase != null && !((codebase instanceof String))) throw
		new InvalidObjectException("codebase must be an instance of string");
	Object	id = arg.get("id", null);
	if (id != null && !((id instanceof Uuid))) throw
		new InvalidObjectException("id must be an instance of Uuid");
	return MarshalledWrapper.integrityEnforced(arg);
    }

    public static SerialForm[] serialForm() {
        return new SerialForm[] {
            new SerialForm("body", byte[].class),
            new SerialForm("entrySchemaDigest", byte[].class),
            new SerialForm("superclasses", String[].class),
            new SerialForm("hashes", long[].class),
            new SerialForm("hash", long.class),
            new SerialForm("className", String.class),
            new SerialForm("codebase", String.class),
            new SerialForm("id", Uuid.class)
        };
    }

    public static void serialize(PutArg arg, EntryRep o) throws IOException {
        arg.put("body", o.body);
        arg.put("entrySchemaDigest", o.entrySchemaDigest);
        arg.put("superclasses", o.superclasses);
        arg.put("hashes", o.hashes);
        arg.put("hash", o.hash);
        arg.put("className", o.className);
        arg.put("codebase", o.codebase);
        arg.put("id", o.id);
        arg.writeArgs();
    }

    private EntryRep(GetArg arg, boolean integrity) throws IOException, ClassNotFoundException {
	body = (byte[]) arg.get("body", null);
	entrySchemaDigest = (byte[]) arg.get("entrySchemaDigest", null);
	superclasses = (String[]) arg.get("superclasses", null);
	hashes = (long[]) arg.get("hashes", null);
	hash = arg.get("hash", 0L);
	className = (String) arg.get("className", null);
	codebase = (String) arg.get("codebase", null);
	id = (Uuid) arg.get("id", null);
	this.integrity = integrity;
	// Decode the body to populate the per-field slice/absent arrays used for matching
	// and indexing. A non-v2 body is refused LOUDLY here (the codec rejects version != 2)
	// -- the @AtomicSerial skew guard (an old-proxy body with no "body" field already
	// failed above in checkIntegrity/get).
	if (body != null) {
	    EntryV2Codec.Decoded dec = codec().decode(body);
	    this.sliceBytes = dec.sliceBytes;
	    this.absent = dec.absent;
	}
    }

    EntryRep(GetArg arg) throws IOException, ClassNotFoundException {
	this(arg, checkIntegrity(arg));
    }


    /** Used in recovery */
    public EntryRep() { }



    /** Used to look up no-arg constructors.  */
    private final static Class[] noArg = new Class[0];

    /**
     * Ensure that the entry class is valid, that is, that it has appropriate
     * access.  If not, throw <code>IllegalArgumentException</code>.
     * <p>
     * For {@link SerialEntry @SerialEntry} classes, a public
     * {@code (GetEntryArg)} constructor is required instead of a no-arg
     * constructor.
     */
    private static void ensureValidClass(Class c) {
	boolean ctorOK = false;
	try {
	    if (!Modifier.isPublic(c.getModifiers())) {
		throw throwRuntime(new IllegalArgumentException(
		    "entry class " + c.getName() + " not public"));
	    }
	    if (c.isAnnotationPresent(SerialEntry.class)) {
		Constructor ctor = c.getConstructor(GetEntryArg.class);
		ctorOK = Modifier.isPublic(ctor.getModifiers());
	    } else {
		Constructor ctor = c.getConstructor(noArg);
		ctorOK = Modifier.isPublic(ctor.getModifiers());
	    }
	} catch (NoSuchMethodException e) {
	    ctorOK = false;
	} catch (SecurityException e) {
	    ctorOK = false;
	}
	if (!ctorOK) {
	    String msg = c.isAnnotationPresent(SerialEntry.class)
		? "entry class " + c.getName() + " needs public (GetEntryArg) constructor"
		: "entry class " + c.getName() + " needs public no-arg constructor";
	    throw throwRuntime(new IllegalArgumentException(msg));
	}
    }

    /**
     * The <code>EntryRep</code> that marks a ``match any'' request.
     * This is used to represent a <code>null</code> template.
     * @return null object template entry.
     */
    public static EntryRep matchAnyEntryRep() {
	return matchAnyRep;
    }

    /**
     * Return <code>true</code> if the given rep is that ``match any''
     * <code>EntryRep</code>.
     */
    private static boolean isMatchAny(EntryRep rep) {
	return matchAnyRep.equals(rep);
    }

    /**
     * @return class name that is used by the ``match any'' EntryRep
     */
    public static String matchAnyClassName() {
	return matchAnyRep.classFor();
    }

    /**
     *
     * @param tmpl
     * @return
     */
    public boolean primeEntryClass(Entry tmpl){
	if (tmpl !=null && className != null && className.equals(tmpl.getClass().getCanonicalName())){
	    realClass = tmpl.getClass();
	    return true;
	}
	return false;
    }

    /**
     * @return An <code>Entry</code> object built out of this
     * <code>EntryRep</code> This is used by the client-side proxy to
     * convert the <code>EntryRep</code> it gets from the space server
     * into the actual <code>Entry</code> object it represents.
     * @throws UnusableEntryException
     *		    One or more fields in the entry cannot be
     *		    deserialized, or the class for the entry type
     *		    itself cannot be deserialized.
     */
    public Entry entry() throws UnusableEntryException {
        String className = ""; // set before any exception can be thrown.
	try {
	    ArrayList badFields = null;
	    ArrayList except = null;
            final Entry entryObj;

            synchronized (this){
                className = this.className;
		if (realClass == null){
		    realClass = CodebaseProvider.loadClass(codebase, className,
                                                   null, integrity, null);
		}
                if (findHash(realClass, false).longValue() != hash)
                    throw throwNewUnusableEntryException(
                        new IncompatibleClassChangeError(realClass + " changed"));

		if (realClass.isAnnotationPresent(SerialEntry.class)) {
		    return entryViaSerialEntry(realClass);
		}

		try {
		    entryObj = (Entry) realClass.getDeclaredConstructor().newInstance();
		} catch (NoSuchMethodException e) {
		    throw throwNewUnusableEntryException(
			    new IncompatibleClassChangeError(realClass + " changed: " + e.getMessage()));
		} catch (InvocationTargetException e) {
		    throw throwNewUnusableEntryException(
			    new IncompatibleClassChangeError(realClass + " changed: " + e.getMessage()));
		}

                final EntryV2Codec.Decoded dec = codec().decode(body);
                Field[] fields = getFields(realClass);

                int fLength = fields.length;
                int nvals = 0;                 // index into the slice array
                int slicesLength = dec.sliceBytes.length;
                for (int i = 0; i < fLength; i++) {
                    Throwable nested = null;
                    try {
                        if (!usableField(fields[i]))
                            continue;

                        byte[] slice = dec.sliceBytes[nvals++];
                        Object value = codec().decodeFieldValue(
                                slice, fields[i].getType(), dec);
                        fields[i].set(entryObj, value);
                    } catch (Throwable e) {
                        nested = e;
                    }

                    if (nested != null) {	// some problem occurred
                        if (badFields == null) {
                            badFields = new ArrayList(fLength);
                            except = new ArrayList(fLength);
                        }
                        badFields.add(fields[i].getName());
                        except.add(nested);
                    }
                }

                /* See if any fields have vanished from the class. */
                if (nvals < slicesLength) {
                    throw throwNewUnusableEntryException(
                            entryObj,
                            null,
                            new Throwable[] {
                                new IncompatibleClassChangeError(
                                        "A usable field has been removed from " +
                                        entryObj.getClass().getName() +
                                        " since this EntryRep was created")
                            });
                }
            }

	    // if there were any bad fields, throw the exception
	    if (badFields != null) {
		String[] bf =
		    (String[]) badFields.toArray(
			new String[badFields.size()]);
		Throwable[] ex =
		    (Throwable[]) except.toArray(new Throwable[bf.length]);
		throw throwNewUnusableEntryException(entryObj, bf, ex);
	    }

	    // everything fine, return the entry
	    return entryObj;
	} catch (InstantiationException e) {
	    throw throwNewUnusableEntryException(e);
	} catch (ClassNotFoundException e) {
	    throw throwNewUnusableEntryException("Encountered a " +
		"ClassNotFoundException while unmarshalling " + className, e);
	} catch (IllegalAccessException e) {
	    throw throwNewUnusableEntryException(e);
	} catch (RuntimeException e) {
	    throw throwNewUnusableEntryException("Encountered a " +
		"RuntimeException while unmarshalling " + className, e);
	} catch (IOException e) {
	    // Covers MarshalException (findHash), MalformedURLException (loadClass), and a
	    // bad/undecodable v2 body (codec) -- all surface as an UnusableEntryException.
	    throw throwNewUnusableEntryException("Encountered an " +
		"IOException while unmarshalling " + className, e);
	}
    }

    /**
     * Constructs a {@link SerialEntry @SerialEntry} instance using its
     * {@code (GetEntryArg)} constructor, unmarshalling the stored field slices first.
     */
    private Entry entryViaSerialEntry(Class realClass)
	    throws UnusableEntryException {
	try {
	    Method entryFormMethod = realClass.getMethod("entryForm");
	    EntryWireField[] wireFields = (EntryWireField[]) entryFormMethod.invoke(null);
	    final EntryV2Codec.Decoded dec = codec().decode(body);
	    Object[] rawValues = new Object[wireFields.length];
	    for (int i = 0; i < wireFields.length && i < dec.sliceBytes.length; i++) {
		try {
		    rawValues[i] = codec().decodeFieldValue(
			dec.sliceBytes[i], wireFields[i].getType(), dec);
		} catch (Throwable e) {
		    rawValues[i] = null;
		}
	    }
	    GetEntryArg getArg = new OutriggerGetEntryArgImpl(wireFields, rawValues);
	    Constructor ctor = realClass.getConstructor(GetEntryArg.class);
	    return (Entry) ctor.newInstance(getArg);
	} catch (Exception e) {
	    throw throwNewUnusableEntryException(
		"Exception constructing @SerialEntry " + realClass.getName(), e);
	}
    }

    // inherit doc comment
    @Override
    public int hashCode() {
	return className.hashCode();
    }

    /**
     * To be equal, the other object must by an <code>EntryRep</code> for
     * an object of the same class with the same values for each field.
     * This is <em>not</em> a template match -- see <code>matches</code>.
     *
     * @see #matches
     */
    @Override
    public boolean equals(Object o) {
	if (o == null)
	    return false;
	if (this == o)
	    return true;
	if (!(o instanceof EntryRep))
	    return false;

	EntryRep other = (EntryRep) o;

        synchronized (this){
            // If we're not the same class then we can't be equal
            if (hash != other.hash)
                return false;

            final byte[][] mine = sliceBytes;
            final byte[][] theirs = other.sliceBytes;
            if (mine == null || theirs == null)
                return mine == theirs;
            if (mine.length != theirs.length)
                return false;
            for (int i = 0; i < mine.length; i++) {
                if (!Arrays.equals(mine[i], theirs[i]))
                    return false;
            }
        }

	return true;
    }

    /**
     * Return <code>true</code> if the field is to be used for the
     * entry.  That is, return <code>true</code> if the field isn't
     * <code>transient</code>, <code>static</code>, or <code>final</code>.
     * @throws IllegalArgumentException
     *			The field is not <code>transient</code>,
     *			<code>static</code>, or <code>final</code>, but
     *			is primitive and hence not a proper field for
     *			an <code>Entry</code>.
     */
    static private boolean usableField(Field field) {
	// ignore anything that isn't a public non-static mutable field
	final int ignoreMods =
	    (Modifier.TRANSIENT | Modifier.STATIC | Modifier.FINAL);

	if ((field.getModifiers() & ignoreMods) != 0)
	    return false;

	// if it isn't ignorable, it has to be an object of some kind
	if (field.getType().isPrimitive()) {
	    throw throwRuntime(new IllegalArgumentException(
		"primitive field, " + field + ", not allowed in an Entry"));
	}

	return true;
    }

    /**
     * @return the ID.
     */
    public Uuid id() {
	return id;
    }

    /**
     * Pick a random <code>Uuid</code> and set our id field to it.
     * @throws IllegalStateException if this method has already
     *         been called.
     */
    public void pickID() {
        synchronized (this){
            if (id != null)
                throw new IllegalStateException("pickID called more than once");
            id = UuidFactory.generate();
        }
    }

    /**
     * Return the packed quick-reject hash contribution for the given field:
     * {@code Arrays.hashCode} of the field's canonical slice bytes, or {@code 0} for a
     * wildcard/null (absent) field. Replaces the v1 {@code value(field).hashCode()}; the
     * slice bytes are the byte-equality match unit, so a v1-matching pair still buckets
     * identically under v2 indexes.
     * @param field the field position.
     * @return the field's slice hash (0 for absent).
     */
    public int sliceHash(int field) {
	final byte[][] s = sliceBytes;
	if (s == null || field >= s.length || absent[field])
	    return 0;
	return Arrays.hashCode(s[field]);
    }

    /**
     * @param field the field position.
     * @return the raw canonical slice bytes for the given field (the byte-equality match
     * unit), or {@code null} if out of range.
     */
    public byte[] sliceBytes(int field) {
	final byte[][] s = sliceBytes;
	if (s == null || field >= s.length)
	    return null;
	return s[field];
    }

    /**
     * @param field the field position.
     * @return {@code true} if the given field is a wildcard (template) / null (stored)
     * -- the {@code absent} marker.
     */
    public boolean isWildcard(int field) {
	final boolean[] a = absent;
	return a == null || field >= a.length || a[field];
    }

    /**
     * @return the number of fields in this kind of entry.
     */
    public int numFields() {
        synchronized (this){
            if (sliceBytes != null) return sliceBytes.length;
        }
	return 0;
    }

    /**
     * @return the class name for this entry.
     */
    public String classFor() {
	return className;
    }

    /**
     * @return the array names of superclasses of this entry type.
     */
    public String[] superclasses() {
	return superclasses != null ? superclasses.clone() : new String [0];
    }

    /**
     * @return the hash of this entry type.
     */
    public long getHash() {
	return hash;
    }

    /**
     * @return the array of superclass hashes of this entry type.
     */
    public long[] getHashes() {
	return hashes != null ? hashes.clone() : new long[0];
    }

    /**
     * See if the other object matches the template object this
     * represents.  (Note that even though "this" is a template, it may
     * have no wildcards -- a template can have all values.)
     *
     * <p>EntryRep-v2: matching is positional byte-equality over the per-field
     * <em>slice</em> bytes (amendment &sect;A.4). A wildcard (template {@code absent}) field
     * is skipped; every non-wildcard template slice MUST byte-equal the corresponding
     * stored slice. The {@code entrySchemaDigest} and schema table are OUTSIDE the compared
     * region (&sect;A.4.6) -- exactly as v1 compared {@code MarshalledInstance} payload bytes
     * while excluding schema/annotation.
     * @param other object to check if it matches this objects template.
     * @return true if matches the template object this EntryRep represents.
     */
    public boolean matches(EntryRep other) {
        synchronized (this){
            if (EntryRep.isMatchAny(this)) return true;

            final byte[][] mine = sliceBytes;
            final boolean[] wild = absent;
            final byte[][] theirs = other.sliceBytes;
            for (int f = 0; f < mine.length; f++) {
                if (wild[f]) {		// skip wildcards
                    continue;
                }
                if (!Arrays.equals(mine[f], theirs[f])) {
                    return false;
                }
            }
        }
	return true;	     // no mismatches, so must be OK
    }

    @Override
    public String toString() {
	return ("EntryRep[" + className + "]");
    }

    /**
     * @param otherClass class name of class or interface this is the same or
     * superclass of the object that this EntryRep represents.
     * @return <code>true</code> if this entry represents an object that
     * is at least the type of the <code>otherClass</code>.
     */
    public boolean isAtLeastA(String otherClass) {
        if (otherClass.equals(matchAnyClassName()))
	    // The other is a null template, all entries are at least entry.
	    return true;
        synchronized (this){
            if (className.equals(otherClass))
                return true;
            for (int i = 0; i < superclasses.length; i++)
                if (superclasses[i].equals(otherClass))
                    return true;
            return false;
        }
    }

    /** Comparator for sorting fields. Cribbed from Reggie */
    private static class FieldComparator implements Comparator {
	public FieldComparator() {}

	/** Super before subclass, alphabetical within a given class */
	public int compare(Object o1, Object o2) {
	    Field f1 = (Field)o1;
	    Field f2 = (Field)o2;
	    if (f1 == f2)
		return 0;
	    if (f1.getDeclaringClass() == f2.getDeclaringClass())
		return f1.getName().compareTo(f2.getName());
	    if (f1.getDeclaringClass().isAssignableFrom(
						     f2.getDeclaringClass()))
		return -1;
	    return 1;
	}
    }

    // -------------------------------------------------------
    // Methods required by LeasedResource and StorableResource
    // -------------------------------------------------------

    // inherit doc comment from LeasedResource
    public void setExpiration(long newExpiration) {
	expires = newExpiration;
    }

    // inherit doc comment from LeasedResource
    public long getExpiration() {
	return expires;
    }

    // inherit doc comment from LeasedResource
    // We use the Rep ID as the cookie
    public Uuid getCookie() {
	return id;
    }

    // -------------------------------------
    //  Methods required by StorableResource
    // -------------------------------------

    // inherit doc comment
    public synchronized void store(ObjectOutputStream out) throws IOException {
	final long bits0;
	final long bits1;
	if (id == null) {
	    bits0 = 0;
	    bits1 = 0;
	} else {
	    bits0 = id.getMostSignificantBits();
	    bits1 = id.getLeastSignificantBits();
	}
	out.writeLong(bits0);
	out.writeLong(bits1);
	out.writeLong(expires);
	out.writeObject(codebase);
	out.writeObject(className);
	out.writeObject(superclasses);
	out.writeObject(body);              // EntryRep-v2 body (was: values)
	out.writeObject(entrySchemaDigest);
	out.writeLong(hash);
	out.writeObject(hashes);
    }

    // inherit doc comment
    public synchronized EntryRep restore(ObjectInputStream in)
	throws IOException, ClassNotFoundException
    {
	final long bits0 = in.readLong();
	final long bits1 = in.readLong();
	if (bits0 == 0 && bits1 == 0) {
	    id = null;
	} else {
	    id = UuidFactory.create(bits0, bits1);
	}

	expires      = in.readLong();
	codebase     = (String)in.readObject();
	className    = (String)in.readObject();
	superclasses = (String [])in.readObject();
	body         = (byte[])in.readObject();
	entrySchemaDigest = (byte[])in.readObject();
	hash	     = in.readLong();
	hashes       = (long[])in.readObject();
	// Flag-day recovery guard: a non-v2 body is refused LOUDLY here (the codec rejects
	// version != 2) -- recovery never silently skips a bad snapshot record.
	if (body != null) {
	    EntryV2Codec.Decoded dec = codec().decode(body);
	    this.sliceBytes = dec.sliceBytes;
	    this.absent = dec.absent;
	}
        return this;
    }

    // Utility methods for throwing and logging exceptions
    /** Log and throw a runtime exception */
    private static RuntimeException throwRuntime(RuntimeException e) {
	if (logger.isLoggable(Levels.FAILED)) {
	    logger.log(Levels.FAILED, e.getMessage(), e);
	}

	throw e;
    }

    /** Construct, log, and throw a new MarshalException */
    private static MarshalException throwNewMarshalException(
	    String msg, Exception nested)
	throws MarshalException
    {
	final MarshalException me = new MarshalException(msg, nested);
	if (logger.isLoggable(Levels.FAILED)) {
	    logger.log(Levels.FAILED, msg, me);
	}

	throw me;
    }

    /**
     * Construct, log, and throw a new UnusableEntryException
     */
    private UnusableEntryException throwNewUnusableEntryException(
	    Entry partial, String[] badFields, Throwable[] exceptions)
	throws UnusableEntryException
    {
	final UnusableEntryException uee =
	    new UnusableEntryException(partial, badFields, exceptions);

	if (logger.isLoggable(Levels.FAILED)) {
	    logger.log(Levels.FAILED,
		       "failure constructing entry of type " + className, uee);
	}

	throw uee;
    }

    /**
     * Construct, log, and throw a new UnusableEntryException, that
     * wraps a given exception.
     */
    private static UnusableEntryException throwNewUnusableEntryException(
            Throwable nested)
	throws UnusableEntryException
    {
	final UnusableEntryException uee = new UnusableEntryException(nested);

	if (logger.isLoggable(Levels.FAILED)) {
	    logger.log(Levels.FAILED, nested.getMessage(), uee);
	}

	throw uee;
    }

    /**
     * Construct, log, and throw a new UnusableEntryException, that
     * will rap a newly constructed UnmarshalException (that optional
     * wraps a given exception).
     */
    private static UnusableEntryException throwNewUnusableEntryException(
            String msg, Exception nested)
	throws UnusableEntryException
    {
	final UnmarshalException ue = new UnmarshalException(msg, nested);
	final UnusableEntryException uee = new UnusableEntryException(ue);

	if (logger.isLoggable(Levels.FAILED)) {
	    logger.log(Levels.FAILED, msg, uee);
	}

	throw uee;
    }
}
