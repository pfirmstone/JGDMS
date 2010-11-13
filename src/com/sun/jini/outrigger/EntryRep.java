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
package com.sun.jini.outrigger;

import com.sun.jini.landlord.LeasedResource;
import com.sun.jini.logging.Levels;
import com.sun.jini.proxy.MarshalledWrapper;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.InvalidObjectException;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.net.MalformedURLException;
import java.rmi.MarshalException;
import java.rmi.UnmarshalException;
import java.rmi.server.RMIClassLoader;
import java.security.DigestOutputStream;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.WeakHashMap;
import java.util.logging.Logger;

import net.jini.core.entry.Entry;
import net.jini.core.entry.UnusableEntryException;
import net.jini.id.Uuid;
import net.jini.id.UuidFactory;
import net.jini.io.MarshalledInstance;
import net.jini.loader.ClassLoading;
import net.jini.space.JavaSpace;

/**
 * An <code>EntryRep</code> object contains a packaged
 * <code>Entry</code> object for communication between the client and a
 * <code>JavaSpace</code>.
 *
 * @author Sun Microsystems, Inc.
 *
 * @see JavaSpace
 * @see Entry
 */
class EntryRep implements StorableResource, LeasedResource, Serializable {
    static final long serialVersionUID = 3L;

    /**
     * The fields of the entry in marshalled form. Use <code>null</code>
     * for <code>null</code> fields.
     */
    private MarshalledInstance[] values;

    private String[]	superclasses;	// class names of the superclasses
    private long[]	hashes;		// superclass hashes
    private long	hash;		// hash for the entry class
    private String	className;	// the class ID of the entry
    private String	codebase;	// the codebase for this entry class
    private Uuid	id;		// space-relative storage id
    private transient long	expires;// expiration time

    /** 
     * <code>true</code> if the last time this object was unmarshalled 
     * integrity was being enforced, <code>false</code> otherwise.
     */
    private transient boolean integrity;

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
	try {
	    matchAnyRep = new EntryRep(new Entry() {
		// keeps tests happy
		static final long serialVersionUID = -4244768995726274609L;
	    }, false);
	} catch (MarshalException e) {
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
    private transient Class realClass;	// real class of the contained object

    /** 
     * Logger for logging information about operations carried out in
     * the client. Note, we hard code "com.sun.jini.outrigger" so
     * we don't drag in OutriggerServerImpl to outrigger-dl.jar.
     */
    private static final Logger logger = 
	Logger.getLogger("com.sun.jini.outrigger.proxy");

    /**
     * Set this entry's generic data to be shared with the <code>other</code>
     * object.  Those fields that are object references that will be the same
     * for all objects of the same type are shared this way.
     * <p>
     * Note that <code>codebase</code> is <em>not</em> shared.  If it were,
     * then the failure of one codebase could make all entries inaccessible.
     * Each entry is usable insofar as the codebase under which it was
     * written is usable.
     */
    void shareWith(EntryRep other) {
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
    static private WeakHashMap	classHashes;

    /**
     * Lookup the hash value for the given class. If it is not
     * found in the cache, generate the hash for the class and
     * save it.
     */
    static synchronized private Long findHash(Class clazz, 
					      boolean marshaling) 
	throws MarshalException, UnusableEntryException
    {
	if (classHashes == null)
	    classHashes = new WeakHashMap();

	Long hash = (Long)classHashes.get(clazz);

	// If hash not cached, calculate it for this class and,
	// recursively, all superclasses
	//
	if (hash == null) {
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
		hash = new Long(h);
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
	    classHashes.put(clazz, hash);
	}
	return hash;
    }

    /**
     * Create a serialized form of the entry.  If <code>validate</code> is
     * <code>true</code>, basic sanity checks are done on the class to
     * ensure that it meets the requirements to be an <code>Entry</code>.
     * <code>validate</code> is <code>false</code> only when creating the
     * stand-in object for "match any", which is never actually marshalled
     * on the wire and so which doesn't need to be "proper".
     */
    private EntryRep(Entry entry, boolean validate) throws MarshalException {
	realClass = entry.getClass();
	if (validate)
	    ensureValidClass(realClass);
	className = realClass.getName();
	codebase = RMIClassLoader.getClassAnnotation(realClass);

	/*
	 * Build up the per-field and superclass information through
	 * the reflection API.
	 */
	final Field[] fields = getFields(realClass);
	int numFields = fields.length;

	// collect the usable field values in vals[0..nvals-1]
	MarshalledInstance[] vals = new MarshalledInstance[numFields];
	int nvals = 0;

	for (int fnum = 0; fnum < fields.length; fnum++) {
	    final Field field = fields[fnum];
	    if (!usableField(field))
		continue;
		
	    final Object fieldValue;
	    try {
		fieldValue = field.get(entry);
	    } catch (IllegalAccessException e) {
		/* In general between using getFields() and 
		 * ensureValidClass this should never happen, however
		 * there appear to be a few screw cases and
		 * IllegalArgumentException seems appropriate.
		 */
		final IllegalArgumentException iae =
		    new IllegalArgumentException("Couldn't access field " + 
						 field);
		iae.initCause(e);
		throw throwRuntime(iae);
	    }

	    if (fieldValue == null) {
		vals[nvals] = null;
	    } else {
		try {
		    vals[nvals] = new MarshalledInstance(fieldValue);
		} catch (IOException e) {
		    throw throwNewMarshalException(
		        "Can't marshal field " + field + " with value " +
			fieldValue, e);
		}
	    }

	    nvals++;
	}

	// copy the vals with the correct length
	this.values = new MarshalledInstance[nvals];
	System.arraycopy(vals, 0, this.values, 0, nvals);

	try {
	    hash = findHash(realClass, true).longValue();
	} catch (UnusableEntryException e) {
	    // Will never happen when we pass true to findHash
	    throw new AssertionError(e);
	}

	// Loop through the supertypes, making a list of all superclasses.
	ArrayList sclasses = new ArrayList();
	ArrayList shashes = new ArrayList();
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
	superclasses = (String[])sclasses.toArray(new String[sclasses.size()]);
	hashes = new long[shashes.size()];
	for (int i=0; i < hashes.length; i++) {
	    hashes[i] = ((Long)shashes.get(i)).longValue();
	}
    }

    /**
     * Create a serialized form of the entry with our object's
     * relevant fields set.
     */
    public EntryRep(Entry entry) throws MarshalException {
	this(entry, true);
    }

    /** Used in recovery */
    EntryRep() { }

    /** Used to look up no-arg constructors.  */
    private static Class[] noArg = new Class[0];

    /**
     * Ensure that the entry class is valid, that is, that it has appropriate
     * access.  If not, throw <code>IllegalArgumentException</code>.
     */
    private static void ensureValidClass(Class c) {
	boolean ctorOK = false;
	try {
	    if (!Modifier.isPublic(c.getModifiers())) {
		throw throwRuntime(new IllegalArgumentException(
		    "entry class " + c.getName() + " not public"));
	    }
	    Constructor ctor = c.getConstructor(noArg);
	    ctorOK = Modifier.isPublic(ctor.getModifiers());
	} catch (NoSuchMethodException e) {
	    ctorOK = false;
	} catch (SecurityException e) {
	    ctorOK = false;
	}
	if (!ctorOK) {
	    throw throwRuntime(new IllegalArgumentException("entry class " +
		c.getName() +" needs public no-arg constructor"));
	}
    }

    /**
     * The <code>EntryRep</code> that marks a ``match any'' request.
     * This is used to represent a <code>null</code> template.
     */
    static EntryRep matchAnyEntryRep() {
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
     * Return the class name that is used by the ``match any'' EntryRep
     */
    static String matchAnyClassName() {
	return matchAnyRep.classFor();
    }

    /**
     * Return an <code>Entry</code> object built out of this
     * <code>EntryRep</code> This is used by the client-side proxy to
     * convert the <code>EntryRep</code> it gets from the space server
     * into the actual <code>Entry</code> object it represents.
     *
     * @throws UnusableEntryException
     *		    One or more fields in the entry cannot be
     *		    deserialized, or the class for the entry type
     *		    itself cannot be deserialized.
     */
    Entry entry() throws UnusableEntryException {
	ObjectInputStream objIn = null;
	try {
	    ArrayList badFields = null;
	    ArrayList except = null;

	    realClass = ClassLoading.loadClass(codebase, className,
					       null, integrity, null);

	    if (findHash(realClass, false).longValue() != hash)
		throw throwNewUnusableEntryException(
		    new IncompatibleClassChangeError(realClass + " changed"));

	    Entry entryObj = (Entry) realClass.newInstance();

	    Field[] fields = getFields(realClass);

	    /*
	     * Loop through the fields, ensuring no primitives and
	     * checking for wildcards.
	     */
	    int nvals = 0;		// index into this.values[]
	    for (int i = 0; i < fields.length; i++) {
		Throwable nested = null;
		try {
		    if (!usableField(fields[i]))
			continue;
		    
		    final MarshalledInstance val = values[nvals++];
		    Object value = (val == null ? null : val.get(integrity));
		    fields[i].set(entryObj, value);
		} catch (Throwable e) {
		    nested = e;
		}

		if (nested != null) {	// some problem occurred
		    if (badFields == null) {
			badFields = new ArrayList(fields.length);
			except = new ArrayList(fields.length);
		    }
		    badFields.add(fields[i].getName());
		    except.add(nested);
		}
	    }

	    /* See if any fields have vanished from the class, 
	     * because of the hashing this should never happen but
	     * throwing an exception that provides more info
	     * (instead of AssertionError) seems harmless.
	     */
	    if (nvals < values.length) {
		throw throwNewUnusableEntryException(
			entryObj,		// should this be null?
			null,			// array of bad-field names
			new Throwable[] {	// array of exceptions
			    new IncompatibleClassChangeError(
				    "A usable field has been removed from " +
				    entryObj.getClass().getName() +
				    " since this EntryRep was created")
			});
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
	    /*
	     * If this happens outside a per-field deserialization then
	     * this is a complete failure  The per-field ones are caught
	     * inside the per-field loop.
	     */
	    throw throwNewUnusableEntryException(e);
	} catch (ClassNotFoundException e) {
	    // see above
	    throw throwNewUnusableEntryException("Encountered a " +
		"ClassNotFoundException while unmarshalling " + className, e);
	} catch (IllegalAccessException e) {
	    // see above
	    throw throwNewUnusableEntryException(e);
	} catch (RuntimeException e) {
	    // see above
	    throw throwNewUnusableEntryException("Encountered a " +
		"RuntimeException while unmarshalling " + className, e);
	} catch (MalformedURLException e) {
	    // see above
	    throw throwNewUnusableEntryException("Malformed URL " +
		"associated with entry of type " + className, e);
	} catch (MarshalException e) {
	    // because we call findHash() w/ false, should never happen
	    throw new AssertionError(e);
	}
    }

    // inherit doc comment
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
    public boolean equals(Object o) {
	// The other passed in was null--obviously not equal
	if (o == null)
	    return false;

	// The other passed in was ME--obviously I'm the same as me...
	if (this == o)
	    return true;

	if (!(o instanceof EntryRep))
	    return false;

	EntryRep other = (EntryRep) o;

	// If we're not the same class then we can't be equal
	if (hash != other.hash)
	    return false;

	/* Paranoid check just to make sure we can't get an
	 * IndexOutOfBoundsException. Should never happen.
	 */
	if (values.length != other.values.length)
	    return false;

	/* OPTIMIZATION:
	 * If we have a case where one element is null and the corresponding
	 * element within the object we're comparing ourselves with is
	 * non-null (or vice-versa), we can stop right here and declare the
	 * two objects to be unequal. This is slightly faster than checking 
	 * the bytes themselves.
	 * LOGIC: They've both got to be null or both have got to be
	 *        non-null or we're out-of-here...
	 */
	for (int i = 0; i < values.length; i++) {
	    if ((values[i] == null) && (other.values[i] != null))
		return false;
	    if ((values[i] != null) && (other.values[i] == null))
		return false;
	}

	/* The most expensive tests we save for last.
	 * Because we've made the null/non-null check above, we can
	 * simplify our comparison here: if our element is non-null,
	 * we know the other value is non-null, too.
	 * If any equals() calls from these element comparisons come
	 * back false then return false. If they all succeed, we fall
	 * through and return true (they were equal).
	 */
	for (int i = 0; i < values.length; i++) {
	    // Short-circuit evaluation if null, compare otherwise.
	    if (values[i] != null && !values[i].equals(other.values[i]))
		return false;
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
     * Return the ID.
     */
    Uuid id() {
	return id;
    }

    /**
     * Pick a random <code>Uuid</code> and set our id field to it.
     * @throws IllegalStateException if this method has already
     *         been called.
     */
    void pickID() {
	if (id != null)
	    throw new IllegalStateException("pickID called more than once");
	id = UuidFactory.generate();
    }

    /**
     * Return the <code>MarshalledObject</code> for the given field.
     */
    public MarshalledInstance value(int fieldNum) {
	return values[fieldNum];
    }

    /**
     * Return the number of fields in this kind of entry.
     */
    public int numFields() {
	if (values != null) {
	    return values.length;
	} else {
	    return 0;
	}
    }

    /**
     * Return the class name for this entry.
     */
    public String classFor() {
	return className;
    }

    /**
     * Return the array names of superclasses of this entry type.
     */
    public String[] superclasses() {
	return superclasses;
    }

    /**
     * Return the hash of this entry type.
     */
    long getHash() {
	return hash;
    }

    /**
     * Return the array of superclass hashes of this entry type.
     */
    long[] getHashes() {
	return hashes;
    }

    /**
     * See if the other object matches the template object this
     * represents.  (Note that even though "this" is a template, it may
     * have no wildcards -- a template can have all values.)
     */
    boolean matches(EntryRep other) {
	/*
	 * We use the fact that this is the template in several ways in
	 * the method implementation.  For instance, in this next loop,
	 * we know that the real object must be at least my type, which
	 * means (a) the field types already match, and (b) it has at
	 * least as many fields as the this does.
	 */

	//Note: If this object is the MatchAny template then 
	//      return true (all entries match MatchAny)
	if (EntryRep.isMatchAny(this))
	    return true;

	for (int f = 0; f < values.length; f++) {
	    if (values[f] == null) {		// skip wildcards
		continue;
	    }
	    if (!values[f].equals(other.values[f])) {
		return false;
	    }
	}
	return true;	     // no mismatches, so must be OK
    }

    public String toString() {
	return ("EntryRep[" + className + "]");
    }

    /**
     * Return <code>true</code> if this entry represents an object that
     * is at least the type of the <code>otherClass</code>.
     */
    boolean isAtLeastA(String otherClass) {
        if (otherClass.equals(matchAnyClassName()))
	    // The other is a null template, all entries are at least entry.
	    return true;

	if (className.equals(otherClass))
	    return true;
	for (int i = 0; i < superclasses.length; i++)
	    if (superclasses[i].equals(otherClass))
		return true;
	return false;
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

    /**
     * Use <code>readObject</code> method to capture whether or
     * not integrity was being enforced when this object was
     * unmarshalled, and to perform basic integrity checks.
     */
    private void readObject(ObjectInputStream in)
	throws IOException, ClassNotFoundException
    {
	in.defaultReadObject();
	if (className == null)
	    throw new InvalidObjectException("null className");

	if (values == null)
	    throw new InvalidObjectException("null values");

	if (superclasses == null)
	    throw new InvalidObjectException("null superclasses");

	if (hashes == null) 
	    throw new InvalidObjectException("null hashes");

	if (hashes.length != superclasses.length)
	    throw new InvalidObjectException("hashes.length (" +
                hashes.length + ") does not equal  superclasses.length (" +
	        superclasses.length + ")");

	// get value for integrity flag
	integrity = MarshalledWrapper.integrityEnforced(in);
    }

    /** 
     * We should always have data in the stream, if this method
     * gets called there is something wrong.
     */
    private void readObjectNoData() throws InvalidObjectException {
	throw new 
	    InvalidObjectException("SpaceProxy should always have data");
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
    public void store(ObjectOutputStream out) throws IOException {
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
	out.writeObject(values);
	out.writeLong(hash);
	out.writeObject(hashes);
    }

    // inherit doc comment
    public void restore(ObjectInputStream in) 
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
	values       = (MarshalledInstance [])in.readObject();
	hash	     = in.readLong();
	hashes       = (long[])in.readObject();
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
     * raps a given exception.
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
