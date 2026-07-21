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

import java.io.IOException;
import java.io.InvalidObjectException;
import java.io.NotSerializableException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.ObjectStreamException;
import java.rmi.MarshalException;
import net.jini.core.constraint.MarshallingFormat;
import net.jini.core.entry.Entry;
import org.apache.river.api.io.AtomicSerial;
import org.apache.river.api.io.AtomicSerial.GetArg;
import org.apache.river.api.io.AtomicSerial.PutArg;
import org.apache.river.api.io.AtomicSerial.SerialForm;

/**
 * This class is used by the <code>snapshot</code> implementation of the
 * proxy to create a <code>snapshot</code>.  Here's what the JavaSpaces
 * specification says about <code>snapshot</code>.
 *
 * The process of serializing an entry for transmission to a JavaSpaces
 * service will be identical if the same entry is used twice. This is most
 * likely to be an issue with templates that are used repeatedly to search
 * for entries with <code>read</code> or <code>take</code>. The client-side 
 * implementations of <code>read</code> and <code>take</code> cannot
 * reasonably avoid this duplicated effort, since they have no efficient
 * way of checking whether the same template is being used without
 * intervening modification.
 *
 * The <code>snapshot</code> method gives the JavaSpaces service implementor
 * a way to reduce the impact of repeated use of the same entry. Invoking
 * <code>snapshot</code> with an <code>Entry</code> will return another
 * <code>Entry</code> object that contains a <i>snapshot</i> of the
 * original entry. Using the returned snapshot entry is equivalent to
 * using the unmodified original entry in all operations on the same
 * JavaSpaces service. Modifications to the original entry will not
 * affect the snapshot. You can <code>snapshot</code> a <code>null</code>
 * template; <code>snapshot</code> may or may not return null given a
 * <code>null</code> template.
 *
 * The entry returned from <code>snapshot</code> will be guaranteed
 * equivalent to the original unmodified object only when used with
 * the space. Using the snapshot with any other JavaSpaces service
 * will generate an <code>IllegalArgumentException</code> unless the
 * other space can use it because of knowledge about the JavaSpaces
 * service that generated the snapshot. The snapshot will be a different
 * object from the original, may or may not have the same hash code,
 * and <code>equals</code> may or may not return <code>true</code>
 * when invoked with the original object, even if the original
 * object is unmodified.
 *
 * A snapshot is guaranteed to work only within the virtual machine
 * in which it was generated. If a snapshot is passed to another 
 * virtual machine (for example, in a parameter of an RMI call),
 * using it--even with the same JavaSpaces service--may generate
 * an <code>IllegalArgumentException</code>.
 *
 * For more information, please review the appropriate specifications.
 *
 * @author Sun Microsystems, Inc.
 *
 */
// @see SpaceProxy#snapshot
@AtomicSerial
class SnapshotRep implements Entry {
    static final long serialVersionUID = 5126328162389368097L;

    private final EntryRep rep;	// the rep from the snapshot

    public static SerialForm[] serialForm() {
	return new SerialForm[] {
	    new SerialForm("rep", EntryRep.class)
	};
    }

    public static void serialize(PutArg arg, SnapshotRep o) throws IOException {
	arg.put("rep", o.rep);
	arg.writeArgs();
    }

    SnapshotRep(GetArg arg) throws IOException, ClassNotFoundException {
	this(check(arg));
    }

    private static EntryRep check(GetArg arg)
	    throws IOException, ClassNotFoundException
    {
	EntryRep rep = arg.get("rep", null, EntryRep.class);
	if (rep == null)
	    throw new InvalidObjectException("SnapshotRep requires a non-null rep");
	return rep;
    }

    /**
     * Create a new <code>SnapshotRep</code> that is a snapshot of
     * <code>e</code>, marshalled under the space's single born
     * <code>format</code> (see <code>SpaceProxy2#entryFormat</code>) so a
     * later {@code write}/{@code read}/{@code take} using this snapshot
     * (which bypasses re-marshalling, see {@code SpaceProxy2#repFor})
     * carries the same format as every other entry/template for that
     * space.
     */
    SnapshotRep(Entry e, MarshallingFormat format) throws MarshalException {
	rep = new EntryRep(e, format);
    }

    /**
     * Construct an SnapshotRep from an existing EntryRep
     */
    SnapshotRep(EntryRep e) {
	rep = e;
    }

    /**
     * Return the pre-computed <code>EntryRep</code>.
     */
    EntryRep rep() {
	return rep;
    }

    /**
     * @throws NotSerializableException always -- java.io serialization is
     * disabled; a snapshot is JVM-local per the JavaSpaces specification and,
     * if ever marshalled, travels via {@code @AtomicSerial} (PutArg).
     */
    private void writeObject(ObjectOutputStream out) throws IOException {
	throw new NotSerializableException(
	    "java.io serialization is disabled for " + getClass().getName()
	    + "; use @AtomicSerial (PutArg)");
    }

    /**
     * java.io deserialization is disabled; reconstruct via {@code @AtomicSerial}
     * (the {@link #SnapshotRep(GetArg)} constructor).
     *
     * @throws NotSerializableException always
     */
    private void readObject(ObjectInputStream in)
	throws IOException, ClassNotFoundException
    {
	throw new NotSerializableException(
	    "java.io deserialization is disabled for " + getClass().getName()
	    + "; use @AtomicSerial (GetArg)");
    }

    private void readObjectNoData() throws ObjectStreamException {
	throw new NotSerializableException(
	    "java.io deserialization is disabled for " + getClass().getName()
	    + "; use @AtomicSerial (GetArg)");
    }
}
