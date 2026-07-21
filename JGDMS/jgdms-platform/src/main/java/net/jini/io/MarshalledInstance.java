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
package net.jini.io;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InvalidObjectException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.ObjectStreamClass;
import java.io.ObjectStreamException;
import java.io.ObjectStreamField;
import java.io.OutputStream;
import java.io.Serializable;
import java.security.Guard;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.ServiceLoader;
import net.jini.core.constraint.InvocationConstraint;
import net.jini.core.constraint.InvocationConstraints;
import net.jini.core.constraint.MarshallingFormat;
import net.jini.io.context.IntegrityEnforcement;
import org.apache.river.api.io.AtomicObjectInput;
import org.apache.river.api.io.AtomicSerial;
import org.apache.river.api.io.AtomicSerial.GetArg;
import org.apache.river.api.io.DeSerializationPermission;
import org.apache.river.api.io.AtomicSerial.SerialForm;
import org.apache.river.api.io.Valid;

/*
 * Implementation note: This class uses the helper class
 * MarshalledObject that is in this package. To avoid confusion
 * with java.rmi.MarshalledObject the fully qualified class names
 * are used for both classes.
 */

/**
 * A <code>MarshalledInstance</code> contains an object in serialized
 * form. The contained object can be deserialized on demand when
 * explicitly requested. This allows an object to be sent from one VM
 * to another in a way that allows the receiver to control when and if
 * the object is deserialized.
 * <p>
 * The contained object is specified at construction time and can
 * either be provided in unserialized or serialized form. If provided
 * in unserialized form it will be serialized during construction
 * with the serialization semantics defined by
 * <code>MarshalOutputStream</code>. In particular, classes are annotated
 * with a codebase URL from which the class can be loaded (if available).
 * <p>
 * If the <code>MarshalledInstance</code> needs to deserialize the
 * contained object then the contained object will be deserialized with the
 * deserialization semantics defined by <code>MarshalInputStream</code>.
 * In particular, the codebase annotations associated with the contained
 * object may be used to load classes referenced by the contained object.
 * <p>
 * <code>MarshalledInstance</code> provides functionality similar to
 * <code>java.rmi.MarshalledObject</code>, but additionally provides
 * for the verification of codebase integrity. Unlike
 * <code>java.rmi.MarshalledObject</code>, it does not perform remote
 * object-to-stub replacement.
 *
 * @author Sun Microsystems, Inc.
 * @since 2.0
 */
@AtomicSerial
public class MarshalledInstance implements Serializable, net.jini.activation.arg.MarshalledObject {
    
    private static final Guard UNMARSHAL = new DeSerializationPermission("MARSHALL");

    /**
     * Reserved {@code payloadFormat} identifier for the legacy Java-Object-Serialization
     * codec (the built-in default; not discovered through {@link MarshalFactoryProvider}).
     */
    public static final String FORMAT_JOSS = "JOSS";

    private static final byte[] EMPTY = new byte[0];

    private static final String PAYLOAD_BYTES = "payloadBytes";
    private static final String CODEBASE_ANNOTATION = "codebaseAnnotation";
    private static final String SCHEMA_BYTES = "schemaBytes";
    private static final String SCHEMA_DIGEST = "schemaDigest";
    private static final String PAYLOAD_FORMAT = "payloadFormat";
    private static final String HASH = "hash";

    /**
     * Lazily-loaded {@link MarshalFactoryProvider}s keyed by {@code payloadFormat}
     * (JGDMS-STD-008 sec.13.2). The JOSS default is NOT in this map.
     */
    private static volatile Map<String,MarshalFactoryProvider> providers;

    // serialPersistentFields is INDEPENDENT of serialForm() (dual-path JOSS keep, STD-008 sec9.1)
    private static final ObjectStreamField[] serialPersistentFields = {
        new ObjectStreamField(PAYLOAD_BYTES, byte[].class),
        new ObjectStreamField(CODEBASE_ANNOTATION, byte[].class),
        new ObjectStreamField(SCHEMA_BYTES, byte[].class),
        new ObjectStreamField(SCHEMA_DIGEST, byte[].class),
        new ObjectStreamField(PAYLOAD_FORMAT, String.class),
        new ObjectStreamField(HASH, int.class)
    };

    public static SerialForm [] serialForm(){
        return new SerialForm [] {
            new SerialForm(PAYLOAD_BYTES, byte[].class),
            new SerialForm(CODEBASE_ANNOTATION, byte[].class),
            new SerialForm(SCHEMA_BYTES, byte[].class),
            new SerialForm(SCHEMA_DIGEST, byte[].class),
            new SerialForm(PAYLOAD_FORMAT, String.class),
            new SerialForm(HASH, Integer.TYPE)
        };
    }

    public static void serialize(AtomicSerial.PutArg args, MarshalledInstance obj) throws IOException {
        putArgs(args, obj);
        args.writeArgs();
    }

    // DUAL-PATH: PutArg overload for the neutral @AtomicSerial serialize() path
    private static void putArgs(AtomicSerial.PutArg pf, MarshalledInstance obj) {
        pf.put(PAYLOAD_BYTES, obj.payloadBytes);
        pf.put(CODEBASE_ANNOTATION, obj.codebaseAnnotation);
        pf.put(SCHEMA_BYTES, obj.schemaBytes);
        pf.put(SCHEMA_DIGEST, obj.schemaDigest);
        pf.put(PAYLOAD_FORMAT, obj.payloadFormat);
        pf.put(HASH, obj.hash);
    }

    // DUAL-PATH: PutField overload for the JOSS writeObject() path
    private static void putArgs(ObjectOutputStream.PutField pf, MarshalledInstance obj) {
        pf.put(PAYLOAD_BYTES, obj.payloadBytes);
        pf.put(CODEBASE_ANNOTATION, obj.codebaseAnnotation);
        pf.put(SCHEMA_BYTES, obj.schemaBytes);
        pf.put(SCHEMA_DIGEST, obj.schemaDigest);
        pf.put(PAYLOAD_FORMAT, obj.payloadFormat);
        pf.put(HASH, obj.hash);
    }

    /**
     * @serial Bytes of the encoded contained object (STD-008 sec.13.1; formerly
     * {@code objBytes}). If <code>payloadBytes</code> is <code>null</code> then the
     * object marshalled was a <code>null</code> reference.
     */
    private final byte[] payloadBytes;

    /**
     * @serial Bytes of the OPTIONAL codebase annotation (STD-006 sec.8 backup URL;
     * formerly {@code locBytes}), which are ignored by <code>equals</code>. If
     * <code>null</code>, there were no non-<code>null</code> annotations during
     * marshalling.
     */
    private final byte[] codebaseAnnotation;

    /**
     * @serial Embedded schema describing {@code payloadBytes} (STD-006 sec.7.8),
     * promoted to first-class state (STD-008 sec.13.1). Never {@code null}; empty for
     * schema-less formats such as {@link #FORMAT_JOSS}. Ignored by {@code equals}.
     */
    private final byte[] schemaBytes;

    /**
     * @serial 32-byte SHA-256 digest of the leaf schema record, exposed for the
     * sec.12.4 fast-path. Never {@code null}; empty for schema-less formats. Ignored
     * by {@code equals}.
     */
    private final byte[] schemaDigest;

    /**
     * @serial Self-describing payload-format identifier (STD-008 sec.13.1) selecting
     * the decoding codec via {@link MarshalFactoryProvider}. Never {@code null};
     * {@link #FORMAT_JOSS} for the legacy path. Ignored by {@code equals}.
     */
    private final String payloadFormat;

    /**
     * @serial Stored hash code of contained object.
     *
     * @see #hashCode
     */
    private final int hash;

    static final long serialVersionUID = -5187033771082433496L;

    /**
     * Hash of the marshalled representation, matching {@code java.rmi.MarshalledObject}
     * so the hashcode is comparable across VMs and across the MarshalledObject conversion.
     */
    private static int computeHash(byte[] payloadBytes){
	int h = 0;
	for (int i = 0; i < payloadBytes.length; i++) {
	    h = 31 * h + payloadBytes[i];
	}
	return h;
    }

    private static boolean check(GetArg arg) throws IOException, ClassNotFoundException{
	byte [] payloadBytes = arg.get(PAYLOAD_BYTES, null, byte[].class);
	byte [] codebaseAnnotation = arg.get(CODEBASE_ANNOTATION, null, byte[].class);
	int hash = arg.get(HASH, 0);
	if ((payloadBytes == null) && ((hash != 13) || (codebaseAnnotation != null)))
	    throw new InvalidObjectException("Bad hash or annotation");
	int h = (payloadBytes == null) ? 13 : computeHash(payloadBytes);
	if (h != hash) throw new InvalidObjectException("Bad hash or annotation");
	UNMARSHAL.checkGuard(null);
	return true;
    }

    public MarshalledInstance(GetArg arg) throws IOException, ClassNotFoundException{
	this(check(arg), arg);
    }

    private MarshalledInstance( boolean check, GetArg arg) throws IOException, ClassNotFoundException {
	payloadBytes = Valid.copy(arg.get(PAYLOAD_BYTES, null, byte[].class));
	codebaseAnnotation = Valid.copy(arg.get(CODEBASE_ANNOTATION, null, byte[].class));
	byte[] sb = Valid.copy(arg.get(SCHEMA_BYTES, null, byte[].class));
	schemaBytes = (sb == null) ? EMPTY : sb;
	byte[] sd = Valid.copy(arg.get(SCHEMA_DIGEST, null, byte[].class));
	schemaDigest = (sd == null) ? EMPTY : sd;
	String fmt = arg.get(PAYLOAD_FORMAT, FORMAT_JOSS, String.class);
	payloadFormat = (fmt == null) ? FORMAT_JOSS : fmt;
	hash = arg.get(HASH, 0);
    }
    
    /*
     * TODO: ServiceProvider for MarshalFactory, to allow clients to use
     * any Serialization framework for handback objects used in Jini api's,
     * such as events.  It isn't appropriate to use subclasses where
     * remote service nodes use a prior version, or don't support dynamic
     * class loading.
     *
     * Service proxy's wishing to utilise different Serialization Frameworks
     * can subclass MarshalledInstance directly to provide their own implementation.
     */
    
    /**
     * Creates a new <code>MarshalledInstance</code> that contains the
     * marshalled representation of the current state of the supplied
     * object. The object is serialized with the semantics defined by
     * <code>MarshalOutput</code>.
     * 
     * This is designed to allow MarshalledInstance to be extensible 
     * and utilize other Serialization Frameworks.
     * 
     * This constructor calls a chain of internal constructors and static
     * methods that prevent finalizer attacks.
     * 
     * It is advisable for overriding classes to be stateless.
     *
     * @param obj The Object to be contained in the new 
     *          <code>MarshalledInstance</code>
     * @param context the collection of context information objects or
     *        <code>null</code>
     * @param marshalFactory MarshalFactory used to create underlying MarshalInstanceInput
     * and MarshalInstanceOutput.
     * @throws IOException if the object cannot be serialized
     * @throws NullPointerException if context or marshalFactory is null.
     */
    protected MarshalledInstance(Object obj, Collection context, MarshalFactory marshalFactory) throws IOException{
	this(marshal(obj, context, marshalFactory));
    }

    private MarshalledInstance(Marshalled m){
	this.payloadBytes = m.payloadBytes;
	this.codebaseAnnotation = m.codebaseAnnotation;
	this.schemaBytes = m.schemaBytes;
	this.schemaDigest = m.schemaDigest;
	this.payloadFormat = m.payloadFormat;
	this.hash = m.hash;
    }

    /**
     * Encodes {@code obj} via the factory and captures the resulting first-class state
     * (STD-008 sec.13). All work happens in this static method, before any field of the
     * new instance is assigned, preserving the finalizer-attack protection of the
     * original constructor chain.
     *
     * <p>The schema, digest and format are reported by the
     * {@link MarshalInstanceOutput} after writing (default JOSS values for schema-less
     * codecs). The hash matches {@code java.rmi.MarshalledObject} so it is comparable
     * across VMs and across the MarshalledObject conversion. A {@code null} object
     * yields {@code payloadBytes == null} and the MarshalledObject null-hash (13).
     */
    private static Marshalled marshal(Object obj, Collection context, MarshalFactory factory) throws IOException {
	if (context == null) throw new NullPointerException();
	if (factory == null) throw new NullPointerException();
	if (obj == null){
	    return new Marshalled(null, null, EMPTY, EMPTY, FORMAT_JOSS, 13);
	}
	ByteArrayOutputStream bout = new ByteArrayOutputStream();
	ByteArrayOutputStream lout = new ByteArrayOutputStream();
	MarshalInstanceOutput out = null;
	try {
	    out = factory.createMarshalOutput(bout, lout, context);
	    out.writeObject(obj);
	    out.flush();
	    byte[] payload = bout.toByteArray();
	    // codebaseAnnotation is null if no annotations
	    byte[] annotation = out.hadAnnotations() ? lout.toByteArray() : null;
	    byte[] schema = out.getSchemaBytes();
	    byte[] digest = out.getSchemaDigest();
	    String format = out.getPayloadFormat();
	    return new Marshalled(
		    payload,
		    annotation,
		    schema == null ? EMPTY : schema,
		    digest == null ? EMPTY : digest,
		    format == null ? FORMAT_JOSS : format,
		    computeHash(payload));
	} finally {
	    try {
		if (out != null) out.close();
	    } catch (IOException e){} // Ignore
	}
    }

    /**
     * Immutable carrier of the first-class marshalled state, returned by
     * {@link #marshal} so a single private constructor can assign all final fields.
     */
    private static final class Marshalled {
	final byte[] payloadBytes;
	final byte[] codebaseAnnotation;
	final byte[] schemaBytes;
	final byte[] schemaDigest;
	final String payloadFormat;
	final int hash;

	Marshalled(byte[] payloadBytes, byte[] codebaseAnnotation, byte[] schemaBytes,
		byte[] schemaDigest, String payloadFormat, int hash){
	    this.payloadBytes = payloadBytes;
	    this.codebaseAnnotation = codebaseAnnotation;
	    this.schemaBytes = schemaBytes;
	    this.schemaDigest = schemaDigest;
	    this.payloadFormat = payloadFormat;
	    this.hash = hash;
	}
    }
    
    /**
     * Creates a new <code>MarshalledInstance</code> that contains the
     * marshalled representation of the current state of the supplied
     * object. The object is serialized with the semantics defined by
     * <code>MarshalOutputStream</code>. The output stream used to marshal the
     * object implements {@link ObjectStreamContext} and returns an empty
     * collection from its {@link ObjectStreamContext#getObjectStreamContext
     * getObjectStreamContext} method.
     * <p>
     * Subclasses may override {@link #getMarshalFactory() } to customize the
     * semantics of serialization.
     *
     * @param obj The Object to be contained in the new 
     *          <code>MarshalledInstance</code>
     * @throws IOException if the object cannot be serialized
     */
    public MarshalledInstance(Object obj) throws IOException {
	this(obj, Collections.EMPTY_SET);
    }

    /**
     * Creates a new <code>MarshalledInstance</code> that contains the
     * marshalled representation of the current state of the supplied
     * object. The object is serialized with the semantics defined by
     * <code>MarshalOutputStream</code>. The output stream used to marshal the
     * object implements {@link ObjectStreamContext} and returns the given
     * collection from its {@link ObjectStreamContext#getObjectStreamContext
     * getObjectStreamContext} method.
     *
     * @param obj The Object to be contained in the new 
     *          <code>MarshalledInstance</code>
     * @param context the collection of context information objects
     * @throws IOException if the object cannot be serialized
     * @throws NullPointerException if <code>context</code> is <code>null</code>
     */
    public MarshalledInstance(Object obj, final Collection context)
	throws IOException
    {
	this(obj, context, new MarshalFactoryInstance());
    }

    /**
     * Creates a new <code>MarshalledInstance</code> whose contained object is encoded
     * using the wire format required by {@code constraints} (JGDMS-STD-008 sec.13). If
     * the constraints require a {@link MarshallingFormat}, that format's codec is used
     * (e.g. {@link MarshallingFormat#ATOMIC_DER} selects the JGDMS-STD-006/ATOMIC-DER codec); with no
     * format constraint the default Java-Object-Serialization codec is used. The
     * resulting instance is self-describing: its {@code payloadFormat} lets any receiver
     * decode it via {@link MarshalFactoryProvider} without a subclass.
     *
     * @param obj the Object to be contained, or {@code null}.
     * @param context the collection of context information objects.
     * @param constraints the invocation constraints, or {@code null} for none.
     * @throws IOException if the object cannot be serialized.
     * @throws UnsupportedConstraintException if a required {@link MarshallingFormat}
     *         cannot be satisfied on this node (no codec for it, or conflicting formats).
     * @throws NullPointerException if {@code context} is {@code null}.
     */
    public MarshalledInstance(Object obj, Collection context, InvocationConstraints constraints)
	throws IOException
    {
	this(obj, context, chooseMarshalFactory(constraints));
    }

    /**
     * Creates a new <code>MarshalledInstance</code> from an
     * existing <code>MarshalledObject</code>. An object equivalent
     * to the object contained in the passed <code>MarshalledObject</code>
     * will be contained in the new <code>MarshalledInstance</code>.
     * <p>
     * The object contained in the passed <code>MarshalledObject</code>
     * will not be unmarshalled as part of this call.
     *
     * @param mo The <code>MarshalledObject</code> that contains
     *        the object the new <code>MarshalledInstance</code> should
     *        contain
     * @throws NullPointerException if <code>mo</code> is <code>null</code>
     */
    public MarshalledInstance(java.rmi.MarshalledObject mo) {

	if (mo == null)
	    throw new NullPointerException();

	// To extract the java.rmi.MarshalledObject's fields we
	// convert the mo into a net.jini.io.MarshalledObject.
	// (See resolveClass() in FromMOInputStream) The private
	// version of MarshalledObject allows access to the needed
	// fields.
	//
	net.jini.io.MarshalledObject privateMO = null;
	try {
	    ByteArrayOutputStream baos = new ByteArrayOutputStream();
	    ObjectOutputStream oos = new ObjectOutputStream(baos);
	    oos.writeObject(mo);
	    oos.flush();
	    byte[] bytes = baos.toByteArray();
	    ByteArrayInputStream bais = new ByteArrayInputStream(bytes);
	    ObjectInputStream ois = new FromMOInputStream(bais);
	    privateMO =
		(net.jini.io.MarshalledObject)ois.readObject();
	} catch (IOException  ioe) {
	    throw new AssertionError(ioe);
	} catch (ClassNotFoundException ioe){
	    throw new AssertionError(ioe);
	}
	// A java.rmi.MarshalledObject is always Java-Object-Serialization.
	payloadBytes = privateMO.objBytes;
	codebaseAnnotation = privateMO.locBytes;
	schemaBytes = EMPTY;
	schemaDigest = EMPTY;
	payloadFormat = FORMAT_JOSS;
	hash = privateMO.hash;
    }
    
    /**
     * Creates a new <code>MarshalledObject</code> that will
     * contain an object equivalent to the object contained
     * in this <code>MarshalledInstance</code> object.
     * <p>
     * The object contained in this <code>MarshalledInstance</code>
     * object will not be unmarshalled as part of this call.
     * <p>
     * Some Objects in the Jini api include MarshalledObject as part of their
     * serial form.
     * @return A new <code>MarshalledObject</code> which
     *        contains an object equivalent to the object
     *        contained in this <code>MarshalledInstance</code>
     */
    public java.rmi.MarshalledObject convertToMarshalledObject() {
	// To create a java.rmi.MarshalledObject with previously
	// serialized data we first create a private
	// net.jini.io.MarshalledObject with the
	// data and then convert it to the final object by changing
	// the class during readObject(). (See resolveClass() in
	// ToMOInputStream)
	//
	if (!FORMAT_JOSS.equals(payloadFormat))
	    throw new IllegalStateException(
		"Cannot convert a non-JOSS MarshalledInstance (payloadFormat="
		+ payloadFormat + ") to java.rmi.MarshalledObject");

	net.jini.io.MarshalledObject privateMO =
		new net.jini.io.MarshalledObject();

	privateMO.objBytes = payloadBytes;
	privateMO.locBytes = codebaseAnnotation;
	privateMO.hash = hash;

	java.rmi.MarshalledObject mo = null;
	try {
	    ByteArrayOutputStream baos = new ByteArrayOutputStream();
	    ObjectOutputStream oos = new ObjectOutputStream(baos);
	    oos.writeObject(privateMO);
	    oos.flush();
	    byte[] bytes = baos.toByteArray();
	    ByteArrayInputStream bais = new ByteArrayInputStream(bytes);
	    ObjectInputStream ois = new ToMOInputStream(bais);
	    mo = (java.rmi.MarshalledObject)ois.readObject();
	} catch (IOException ioe) {
	    throw new AssertionError(ioe);
	} catch (ClassNotFoundException ioe){
	    throw new AssertionError(ioe);
	}
	return mo;
    }
    
    /**
     * Returns this instance's self-describing payload-format identifier
     * (JGDMS-STD-008 sec.13.1): the token naming the codec that encoded
     * the contained object and that {@link #get()} will use to decode it
     * -- {@link #FORMAT_JOSS} for the legacy Java-Object-Serialization
     * codec, {@link net.jini.core.constraint.MarshallingFormat#ATOMIC_DER}'s
     * token for the JGDMS-STD-006 canonical DER codec, or any other
     * registered {@link MarshalFactoryProvider} format. Never
     * {@code null}. Note that a {@code MarshalledInstance} containing
     * {@code null} always carries the {@link #FORMAT_JOSS} token (it has
     * no payload bytes to decode); callers gating on format should treat
     * {@link #isNull()} instances as format-neutral.
     *
     * <p>This accessor lets format-sensitive receivers (e.g. a DER-only
     * service refusing JOSS payloads at its trust boundary) check what a
     * {@code MarshalledInstance} contains <em>before</em> any call to
     * {@link #get()} can reach the named codec.
     *
     * <p>This accessor is {@code final} for the same reason {@link #isNull()},
     * {@link #equals(Object)} and {@link #hashCode()} are: it is a
     * trust-boundary input (format-gating receivers act on its answer), and a
     * subclass must not be able to report a format different from the
     * {@code payloadFormat} the codec machinery recorded and that
     * {@link #get()} will actually dispatch on. Subclasses using alternative
     * marshalling frameworks already surface their format through this field
     * via {@code MarshalInstanceOutput.getPayloadFormat()}, so there is no
     * legitimate override.
     *
     * @return the payload format identifier, never {@code null}
     * @since 4.0.0
     */
    public final String getPayloadFormat(){
	return payloadFormat;
    }

    /**
     * Sub classes implement this method to use alternative Serialization
     * frameworks to unmarshall data.
     * @return a new MarshalFactory instance.
     */
    protected MarshalFactory getMarshalFactory(){
	return factoryForFormat(payloadFormat);
    }

    /**
     * Resolves the {@link MarshalFactory} for a {@code payloadFormat} (STD-008 sec.13.2):
     * the built-in JOSS factory for {@link #FORMAT_JOSS} (or {@code null}), otherwise the
     * {@link MarshalFactoryProvider} discovered by {@link ServiceLoader} for that format.
     * Subclasses (e.g. {@code AtomicMarshalledInstance}) that override
     * {@link #getMarshalFactory()} bypass this lookup entirely.
     */
    private static MarshalFactory factoryForFormat(String format){
	if (format == null || FORMAT_JOSS.equals(format)){
	    return new MarshalFactoryInstance();
	}
	MarshalFactoryProvider p = providers().get(format);
	if (p == null){
	    throw new IllegalStateException(
		"No MarshalFactoryProvider registered for payloadFormat: " + format
		+ " (is the codec module on the classpath?)");
	}
	return p.marshalFactory();
    }

    private static Map<String,MarshalFactoryProvider> providers(){
	Map<String,MarshalFactoryProvider> m = providers;
	if (m == null){
	    m = loadProviders();
	    providers = m;
	}
	return m;
    }

    private static Map<String,MarshalFactoryProvider> loadProviders(){
	Map<String,MarshalFactoryProvider> m = new HashMap<String,MarshalFactoryProvider>();
	for (MarshalFactoryProvider p : ServiceLoader.load(MarshalFactoryProvider.class)){
	    String fmt = p.payloadFormat();
	    if (fmt != null && !FORMAT_JOSS.equals(fmt) && !m.containsKey(fmt)){
		m.put(fmt, p);
	    }
	}
	return m;
    }

    /**
     * Selects the {@link MarshalFactory} required by the given invocation constraints
     * (JGDMS-STD-008 sec.13) -- the shared enforcement primitive for the
     * {@link MarshallingFormat} constraint. A required {@code MarshallingFormat}
     * determines the format: {@link #FORMAT_JOSS} (or {@code null} constraints / no
     * format constraint) yields the built-in JOSS factory; any other format is resolved
     * to its {@link MarshalFactoryProvider} via {@link ServiceLoader}. If a constraint
     * only <em>prefers</em> a format, it is honoured when resolvable on this node,
     * otherwise the default is used.
     *
     * @param constraints the invocation constraints, or {@code null} for none.
     * @return the MarshalFactory to use; never {@code null}.
     * @throws UnsupportedConstraintException if a required format has no registered
     *         provider on this node, or two different formats are required at once.
     */
    public static MarshalFactory chooseMarshalFactory(InvocationConstraints constraints)
	throws UnsupportedConstraintException
    {
	String format = requiredFormat(constraints);
	if (format == null || FORMAT_JOSS.equals(format)){
	    return new MarshalFactoryInstance();
	}
	MarshalFactoryProvider p = providers().get(format);
	if (p == null){
	    throw new UnsupportedConstraintException(
		"No MarshalFactoryProvider for required MarshallingFormat: " + format
		+ " (is the codec module on the classpath?)");
	}
	return p.marshalFactory();
    }

    /**
     * The format identifier required by the constraints, or -- failing a hard
     * requirement -- the first resolvable preferred format, or {@code null} for the
     * default. Conflicting required {@code MarshallingFormat}s are unsatisfiable.
     */
    private static String requiredFormat(InvocationConstraints constraints)
	throws UnsupportedConstraintException
    {
	if (constraints == null) return null;
	String required = null;
	for (InvocationConstraint c : constraints.requirements()){
	    if (c instanceof MarshallingFormat){
		String f = ((MarshallingFormat) c).getFormat();
		if (required == null) required = f;
		else if (!required.equals(f))
		    throw new UnsupportedConstraintException(
			"Conflicting required MarshallingFormat constraints: "
			+ required + " and " + f);
	    }
	}
	if (required != null) return required;
	for (InvocationConstraint c : constraints.preferences()){
	    if (c instanceof MarshallingFormat){
		String f = ((MarshallingFormat) c).getFormat();
		if (FORMAT_JOSS.equals(f) || providers().containsKey(f)) return f;
	    }
	}
	return null;
    }
    /**
     * Returns a new copy of the contained object.
     * @return a new Object instance of the marshalled bytes contained within.
     * @throws java.io.IOException
     * @throws java.lang.ClassNotFoundException
     */
    @Override
    public Object get() throws IOException, ClassNotFoundException 
    {
        return this.get(false);
    }

    /**
     * Returns a new copy of the contained object. Deserialization is
     * performed with the semantics defined by <code>MarshalInputStream</code>.
     * The input stream used to unmarshal the object implements {@link
     * ObjectStreamContext} and returns a collection from its {@link
     * ObjectStreamContext#getObjectStreamContext getObjectStreamContext}
     * method which contains a single element of type {@link
     * IntegrityEnforcement}; the {@link IntegrityEnforcement#integrityEnforced
     * integrityEnforced} method of this element returns the specified
     * <code>verifyCodebaseIntegrity</code> value.
     * <p><code>MarshalledInstance</code> implements this method by calling
     * <code>{@link #get(ClassLoader, boolean, ClassLoader, Collection)
     * get}(null, verifyCodebaseIntegrity, null, null)</code>.
     *
     * @param verifyCodebaseIntegrity if <code>true</code> then
     *        codebase integrity is verified, otherwise code base
     *        integrity is not verified
     * @return a new copy of the contained object
     * @throws IOException if an 
     *         <code>IOException</code> occurs while deserializing the
     *         object from its internal representation
     * @throws ClassNotFoundException if any classes necessary
     *         for reconstructing the contained object can not
     *         be found or if <code>verifyCodebaseIntegrity</code>
     *         is <code>true</code> and the integrity of the
     *         contained object's codebase cannot be confirmed
     */
    @Override
    public Object get(final boolean verifyCodebaseIntegrity) 
	throws IOException, ClassNotFoundException 
    {
	return get(null, verifyCodebaseIntegrity, null, null);
    }
    
    /**
     * Returns a new copy of the contained object.Deserialization is
     * performed with the semantics defined by <code>MarshalInputStream</code>.
     * The input stream used to unmarshal the object implements
     * {@link ObjectStreamContext} and returns a collection from its
     * {@link ObjectStreamContext#getObjectStreamContext getObjectStreamContext} 
     * method which contains a single element of type 
     * {@link IntegrityEnforcement};
     * the {@link IntegrityEnforcement#integrityEnforced integrityEnforced}
     * method of this element returns the specified 
     * <code>verifyCodebaseIntegrity</code> value.
     * <p><code>MarshalledInstance</code> implements this method by calling
     * <code>{@link #get(ClassLoader, boolean, ClassLoader, Collection)
     * get}(null, verifyCodebaseIntegrity, null, null)</code>.
     *
     * @param <T> Instance class type.
     * @param verifyCodebaseIntegrity if <code>true</code> then
     *        codebase integrity is verified, otherwise code base
     *        integrity is not verified
     * @param type - class of object to be read from bytes.
     * @return a new copy of the contained object
     * @throws IOException if an 
     *         <code>IOException</code> occurs while deserializing the
     *         object from its internal representation
     * @throws ClassNotFoundException if any classes necessary
     *         for reconstructing the contained object can not
     *         be found or if <code>verifyCodebaseIntegrity</code>
     *         is <code>true</code> and the integrity of the
     *         contained object's codebase cannot be confirmed
     */
    @Override
    public <T> T get(final boolean verifyCodebaseIntegrity, Class<T> type) 
	throws IOException, ClassNotFoundException 
    {
	return get(null, verifyCodebaseIntegrity, null, null, type);
    }

    /**
     * Returns a new copy of the contained object. Deserialization is
     * performed with the semantics defined by <code>MarshalInputStream</code>.
     * If <code>context</code> is not <code>null</code>
     * the input stream used to unmarshal the object implements {@link
     * ObjectStreamContext} and returns the given collection from its {@link
     * ObjectStreamContext#getObjectStreamContext getObjectStreamContext}
     * method.
     * <p>If <code>context</code> is <code>null</code>
     * the input stream used to unmarshal the object implements {@link
     * ObjectStreamContext} and returns a collection from its {@link
     * ObjectStreamContext#getObjectStreamContext getObjectStreamContext}
     * method which contains a single element of type {@link
     * IntegrityEnforcement}; the {@link IntegrityEnforcement#integrityEnforced
     * integrityEnforced} method of this element returns the specified
     * <code>verifyCodebaseIntegrity</code> value.
     *
     * @param defaultLoader the class loader value (possibly
     *	      <code>null</code>) to pass as the <code>defaultLoader</code>
     *        argument to <code>RMIClassLoader</code> methods
     * @param verifyCodebaseIntegrity if <code>true</code> then
     *        codebase integrity is verified, otherwise code base
     *        integrity is not verified
     * @param verifierLoader the class loader value (possibly
     *        <code>null</code>) to pass to {@link
     *        net.jini.security.Security#verifyCodebaseIntegrity
     *        Security.verifyCodebaseIntegrity}, if
     *        <code>verifyCodebaseIntegrity</code> is <code>true</code>
     * @param context the collection of context information objects or
     *        <code>null</code>
     * @return a new copy of the contained object
     * @throws IOException if an 
     *         <code>IOException</code> occurs while deserializing the
     *         object from its internal representation
     * @throws ClassNotFoundException if any classes necessary
     *         for reconstructing the contained object can not
     *         be found or if <code>verifyCodebaseIntegrity</code>
     *         is <code>true</code> and the integrity of the
     *         contained object's codebase cannot be confirmed
     */
    @Override
    public Object get(final ClassLoader defaultLoader,
		      final boolean verifyCodebaseIntegrity,
		      final ClassLoader verifierLoader,
		      final Collection context)
	throws IOException, ClassNotFoundException 
    {
	return get(defaultLoader,
                verifyCodebaseIntegrity,
                verifierLoader,
                context,
                Object.class);
    }
    
    /**
     * Returns a new copy of the contained object.Deserialization is
     * performed with the semantics defined by <code>MarshalInputStream</code>.
     * If <code>context</code> is not <code>null</code>
     * the input stream used to unmarshal the object implements {@link
     * ObjectStreamContext} and returns the given collection from its {@link
     * ObjectStreamContext#getObjectStreamContext getObjectStreamContext}
     * method.
     * <p>If <code>context</code> is <code>null</code>
     * the input stream used to unmarshal the object implements {@link
     * ObjectStreamContext} and returns a collection from its {@link
     * ObjectStreamContext#getObjectStreamContext getObjectStreamContext}
     * method which contains a single element of type {@link
     * IntegrityEnforcement}; the {@link IntegrityEnforcement#integrityEnforced
     * integrityEnforced} method of this element returns the specified
     * <code>verifyCodebaseIntegrity</code> value.
     *
     * @param defaultLoader the class loader value (possibly
     *	      <code>null</code>) to pass as the <code>defaultLoader</code>
     *        argument to <code>RMIClassLoader</code> methods
     * @param verifyCodebaseIntegrity if <code>true</code> then
     *        codebase integrity is verified, otherwise code base
     *        integrity is not verified
     * @param verifierLoader the class loader value (possibly
     *        <code>null</code>) to pass to {@link
     *        net.jini.security.Security#verifyCodebaseIntegrity
     *        Security.verifyCodebaseIntegrity}, if
     *        <code>verifyCodebaseIntegrity</code> is <code>true</code>
     * @param context the collection of context information objects or
     *        <code>null</code>
     * @param type the class type of the object returned.
     * @return a new copy of the contained object
     * @throws IOException if an 
     *         <code>IOException</code> occurs while deserializing the
     *         object from its internal representation
     * @throws ClassNotFoundException if any classes necessary
     *         for reconstructing the contained object can not
     *         be found or if <code>verifyCodebaseIntegrity</code>
     *         is <code>true</code> and the integrity of the
     *         contained object's codebase cannot be confirmed
     */
    @Override
    public <T> T get(final ClassLoader defaultLoader,
		      final boolean verifyCodebaseIntegrity,
		      final ClassLoader verifierLoader,
		      final Collection context,
                      final Class<T> type)
	throws IOException, ClassNotFoundException 
    {
	if (payloadBytes == null)   // must have been a null object
	    return null;
	final Collection ctext;
	if (context == null) {
	    ctext = Collections.singleton( new IntegrityEnforcement(){

		@Override
		public boolean integrityEnforced() {
		    return verifyCodebaseIntegrity;
		}

	    });
	} else {
	    ctext = context;
	}
	final ByteArrayInputStream bin = new ByteArrayInputStream(payloadBytes);
	// codebaseAnnotation is null if no annotations
	final ByteArrayInputStream lin =
	    (codebaseAnnotation == null ? null : new ByteArrayInputStream(codebaseAnnotation));

	MarshalInstanceInput in = null;
	try {
	    in = getMarshalFactory().createMarshalInput(
		bin, lin, schemaBytes, schemaDigest, payloadFormat,
		defaultLoader, verifyCodebaseIntegrity,
		verifierLoader, ctext);
	    in.useCodebaseAnnotations();
            if (in instanceof AtomicObjectInput){
                return ((AtomicObjectInput) in).readObject(type);
            } else {
                Object obj = in.readObject();
                if (type.isInstance(obj)) return (T) obj;
                StringBuilder sb = new StringBuilder();
                sb.append("Object not of type expected: ")
                    .append(type.toString())
                    .append("found instead: ")
                    .append(obj.getClass().toString());
                throw new ClassCastException(sb.toString());
            }
	} finally {
	    try {
		if (in != null) in.close();
	    } catch (IOException e){} // Ignore
	}
    }

    /**
     * Compares this <code>MarshalledInstance</code> to another
     * object. Returns true if and only if the argument refers to an instance
     * of <code>MarshalledInstance</code> that contains exactly the same
     * serialized form for its contained object as this object does and
     * has the same class codebase annotations.
     *
     * @param obj the object to compare with this
     *            <code>MarshalledInstance</code>
     * @return <code>true</code> if the argument contains an object
     *         with an equivalent serialized form and codebase;
     *	       otherwise returns <code>false</code>
     */
    public boolean fullyEquals(Object obj) {
	if (equals(obj)) {
	    MarshalledInstance other = (MarshalledInstance)obj;
	    return Arrays.equals(codebaseAnnotation, other.codebaseAnnotation);
	}
	return false;
    }

    /**
     * Compares this <code>MarshalledInstance</code> to another
     * object. Returns true if and only if the argument refers to an instance
     * of <code>MarshalledInstance</code> that contains exactly the same
     * serialized form for its contained object as this object does. The
     * comparison ignores any class codebase annotations, so that
     * two objects can be equivalent if they have the same serialized
     * representation, except for the codebase of each class in the
     * serialized representation.
     * <p>
     * Subclasses should not override this method.
     * 
     * @since 3.1.0 MarshalledInstance is extensible and since it is used for hand-back
     * objects over remote connections and because it's possible for
     * MarshalledInstance to be converted to MarshalledObject and back again to 
     * MarshalledInstance, the resulting conversion may loose type compatibility
     * with the original instance which may be a subclass of MarshalledInstance.  
     * This equals method allows a caller to determine if the hand-back
     * instance is equal to the original, based solely on the serial form 
     * of the contained object.
     * 
     * @param obj the object to compare with this
     *            <code>MarshalledInstance</code>
     * @return <code>true</code> if the argument contains an object
     *         with an equivalent serialized form; otherwise returns
     *         <code>false</code>
     */
    @Override
    public final boolean equals(Object obj) {
	if (obj == this)
	    return true;

	if (obj instanceof MarshalledInstance) {
	    MarshalledInstance other = (MarshalledInstance)obj;
	    if (hash != other.hash)
		return false;
	    return Arrays.equals(payloadBytes, other.payloadBytes);
	}
	return false;
    }

    /**
     * Returns the hash code for this <code>MarshalledInstance</code>.
     * The hash code is calculated only from the serialized form
     * of the contained object.
     * <p>
     * Subclasses should not override this method.
     * 
     * @return The hash code for this object
     */
    @Override
    public final int hashCode() {
	return hash;
    }
    
    /**
     * Null reference check for marshaled instance.
     * @return true if MarshalledInstance contains a null reference.
     */
    public final boolean isNull() {
	return payloadBytes == null;
    }

    private void writeObject(ObjectOutputStream out) throws IOException {
//	out.defaultWriteObject();
        putArgs(out.putFields(), this);
        out.writeFields();
    }

    /**
     * Verify the case of null contained object.
     * @param in ObjectInputStream
     * @throws ClassNotFoundException if class not found.
     * @throws IOException if a problem occurs during de-serialization.
     */
    private void readObject(ObjectInputStream in)
	throws IOException, ClassNotFoundException
    {
	in.defaultReadObject();

	// If contained object is null, then hash and codebaseAnnotation must be
	// proper
	//
	if ((payloadBytes == null) && ((hash != 13) || (codebaseAnnotation != null)))
	    throw new InvalidObjectException("Bad hash or annotation");
    }

    /**
     * Protect against missing superclass.
     * @throws ObjectStreamException an instance of InvalidObjectException if invoked.
     */
    private void readObjectNoData() throws ObjectStreamException {
	throw new InvalidObjectException("Bad class hierarchy");
    }

    static class MarshalFactoryInstance implements MarshalFactory {

	@Override
	public MarshalInstanceInput createMarshalInput(InputStream objIn,
		InputStream locIn, 
		ClassLoader defaultLoader,
		boolean verifyCodebaseIntegrity,
		ClassLoader verifierLoader,
		Collection context) throws IOException 
	{
	    return new MarshalledInstanceInputStream(
		    objIn,
		    locIn,
		    defaultLoader,
		    verifyCodebaseIntegrity,
		    verifierLoader,
		    context
	    );
	}

	@Override
	public MarshalInstanceOutput createMarshalOutput(OutputStream objOut,
		OutputStream locOut, Collection context) throws IOException {
	    return new MarshalledInstanceOutputStream(objOut, locOut, context);
	}
    }
    
    /**
     * This class is used to marshal objects for
     * <code>MarshalledInstance</code>.  It places the location annotations
     * to one side so that two <code>MarshalledInstance</code>s can be
     * compared for equality if they differ only in location
     * annotations.  Objects written using this stream should be read back
     * from a <code>MarshalledInstanceInputStream</code>.
     *   
     * @see MarshalledInstanceInputStream
     */  
    private static class MarshalledInstanceOutputStream
        extends MarshalOutputStream implements MarshalInstanceOutput
    {
	/** The stream on which location objects are written. */
	private final ObjectOutputStream locOut;
 
	/** <code>true</code> if non-<code>null</code> annotations are
	 *  written.
	 */
	private boolean hadAnnotations;

	/**
	 * Creates a new <code>MarshalledObjectOutputStream</code> whose
	 * non-location bytes will be written to <code>objOut</code> and whose
	 * location annotations (if any) will be written to
	 * <code>locOut</code>.
	 */
	public MarshalledInstanceOutputStream(OutputStream objOut,
					      OutputStream locOut,
					      Collection context)
	    throws IOException
	{
	    super(objOut, context);
	    this.locOut = new ObjectOutputStream(locOut);
	    hadAnnotations = false;
	}
 
	/**
	 * Returns <code>true</code> if any non-<code>null</code> location
	 * annotations have been written to this stream.
	 */
	@Override
	public boolean hadAnnotations() {
	    return hadAnnotations;
	}
 
	/**
	 * Overrides <code>MarshalOutputStream.writeAnnotation</code>
	 * implementation to write annotations to the location stream.
	 */
	@Override
	public void writeAnnotation(String loc) throws IOException {
	    hadAnnotations |= (loc != null);
	    locOut.writeObject(loc);
	}

	@Override
	public void flush() throws IOException {
	    super.flush();
	    locOut.flush();
	}
    }

    /**
     * The counterpart to <code>MarshalledInstanceOutputStream</code>.
     *   
     * @see MarshalledInstanceOutputStream
     */  
    private static class MarshalledInstanceInputStream
        extends MarshalInputStream implements MarshalInstanceInput
    {
	/**
	 * The stream from which annotations will be read.  If this is
	 * <code>null</code>, then all annotations were <code>null</code>.
	 */
	private final ObjectInputStream locIn;
 
	/**
	 * Creates a new <code>MarshalledObjectInputStream</code> that
	 * reads its objects from <code>objIn</code> and annotations
	 * from <code>locIn</code>.  If <code>locIn</code> is
	 * <code>null</code>, then all annotations will be
	 * <code>null</code>.
	 */
	MarshalledInstanceInputStream(InputStream objIn,
				      InputStream locIn,
				      ClassLoader defaultLoader,
				      boolean verifyCodebaseIntegrity,
				      ClassLoader verifierLoader,
				      Collection context)
	    throws IOException
	{
	    super(objIn,
		  defaultLoader,
		  verifyCodebaseIntegrity,
		  verifierLoader,
		  context);
	    this.locIn = (locIn == null ? null : new ObjectInputStream(locIn));
	}
 
	/**
	 * Overrides <code>MarshalInputStream.readAnnotation</code> to
	 * return locations from the stream we were given, or <code>null</code>
	 * if we were given a <code>null</code> location stream.
	 */
	@Override
	protected String readAnnotation()
	    throws IOException, ClassNotFoundException
	{
	    return (locIn == null ? null : (String)locIn.readObject());
	}
    }    

    /**
     * Input stream to convert <code>java.rmi.MarshalledObject</code>
     * into <code>net.jini.io.MarshalledObject</code>.
     */
    private static class FromMOInputStream extends ObjectInputStream {

	FromMOInputStream(InputStream in) throws IOException {
	    super(in);
        }

	/**
	 * Overrides <code>ObjectInputStream.resolveClass</code> to change
	 * an occurence of class <code>java.rmi.MarshalledObject</code> to
	 * class <code>net.jini.io.MarshalledObject</code>.
	 */
        @Override
	protected Class resolveClass(ObjectStreamClass desc)
	    throws IOException, ClassNotFoundException
	{
	    if (desc.getName().equals("java.rmi.MarshalledObject")) {
		return net.jini.io.MarshalledObject.class;
	    }
	    return super.resolveClass(desc);
	}
    }

    /**
     * Input stream to convert
     * <code>net.jini.io.MarshalledObject</code> into
     * <code>java.rmi.MarshalledObject</code>.
     */
    private static class ToMOInputStream extends ObjectInputStream {

	ToMOInputStream(InputStream in) throws IOException {
	    super(in);
        }

	/**
	 * Overrides <code>ObjectInputStream.resolveClass</code>
	 * to change an occurence of class
	 * <code>net.jini.io.MarshalledObject</code>
	 * to class <code>java.rmi.MarshalledObject</code>.
	 */
        @Override
	protected Class resolveClass(ObjectStreamClass desc)
	    throws IOException, ClassNotFoundException
	{
	    if (desc.getName().equals("net.jini.io.MarshalledObject")) {
		return java.rmi.MarshalledObject.class;
	    }
	    return super.resolveClass(desc);
	}
    }
}
