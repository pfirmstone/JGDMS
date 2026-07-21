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
package org.apache.river.reggie;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import net.jini.core.constraint.MarshallingFormat;

/**
 * Reggie's persistence half of the born-immutable format guards
 * (JGDMS-STD-006 sec.3 item 5; SOW-Entry-ATOMIC-DER-Migration.md sec.4 A3,
 * guard (v)): a registrar's marshalling format is fixed at instantiation and
 * immutable for the life of the store, so snapshot recovery must refuse a
 * snapshot whose born format contradicts the recovering instance's own
 * configuration -- fail-closed, before any snapshot state is consumed.
 * Mirrors outrigger-snaplogstore's {@code BackEnd} guard (A1,
 * board-reviewed, commit {@code 5899a7f9e}), adapted to Reggie's single
 * {@code RegistrarImpl.takeSnapshot}/{@code recoverSnapshot} pair.
 *
 * <p>On-disk shape: {@link RegistrarImpl#takeSnapshot} writes
 * {@link #LOG_VERSION} followed immediately by a format-marker string
 * ({@link #writeFormatMarker}); {@link RegistrarImpl#recoverSnapshot} reads
 * the version int and hands it to {@link #resolveRecoveredFormat}, which
 * either reads the marker (current shape) or resolves the pre-A3 shape
 * ({@link #PRE_A3_LOG_VERSION}, no marker ever written) as implicit legacy
 * JOSS. The absent-marker-means-implicit-JOSS rule (STD-006 sec.11.8
 * graceful degradation) is airtight for Reggie because DER is unreleased:
 * no deployed store contains DER-encoded entries, so an unmarked pre-A3
 * snapshot is provably pure JOSS. {@link #checkFormat} then makes the
 * accept/refuse decision against the instance's configured format -- a
 * JOSS-configured instance recovers a pre-A3 snapshot fine; a
 * DER-configured instance pointed at legacy JOSS data is refused via the
 * guard (a real config/data contradiction), not a blunt version mismatch.
 */
final class SnapshotFormatGuard {

    /**
     * Current log format version: one more than {@link #PRE_A3_LOG_VERSION}
     * because the snapshot's own on-disk shape changed (the format marker
     * written immediately after this version int), not because the
     * log-record shape changed.
     */
    static final int LOG_VERSION = 4;

    /**
     * The on-disk shape written by every pre-A3 version of this class:
     * version 3, with no format marker following the version int. A
     * new-code instance MUST still be able to recover this shape -- it
     * predates the born-immutable-format concept entirely, so it is
     * unconditionally legacy JOSS (see class javadoc). Any version other
     * than this one and {@link #LOG_VERSION} remains a hard reject
     * (genuine corruption / unsupported version).
     */
    static final int PRE_A3_LOG_VERSION = 3;

    private SnapshotFormatGuard() { throw new AssertionError(); }

    /**
     * Writes the born-format marker, called by
     * {@link RegistrarImpl#takeSnapshot} immediately after the
     * {@link #LOG_VERSION} int.
     *
     * @param stream the snapshot stream
     * @param format the instance's born format token
     *        ({@code MarshallingFormat.getFormat()})
     * @throws IOException from the stream
     * @throws NullPointerException if {@code format} is null -- an
     *         unconfigured format must never be silently persisted
     */
    static void writeFormatMarker(DataOutput stream, String format)
	    throws IOException
    {
	if (format == null)
	    throw new NullPointerException(
		"cannot persist a null born-format marker");
	stream.writeUTF(format);
    }

    /**
     * Resolves the format a snapshot was born with, from the version int
     * just read by {@link RegistrarImpl#recoverSnapshot}: the marker
     * string for a current-shape snapshot, implicit legacy JOSS for the
     * pre-A3 shape (no marker was ever written -- do not read one), and a
     * hard reject for anything else.
     *
     * @param stream the snapshot stream, positioned immediately after the
     *        version int
     * @param logVersion the version int read from the snapshot
     * @return the born format token this snapshot was written under
     * @throws IOException on an unsupported version (fail-closed) or from
     *         the stream
     */
    static String resolveRecoveredFormat(DataInput stream, int logVersion)
	    throws IOException
    {
	if (logVersion == LOG_VERSION) {
	    return stream.readUTF();
	}
	if (logVersion == PRE_A3_LOG_VERSION) {
	    // Legacy pre-A3 shape: no format marker was ever written; this
	    // predates the born-immutable-format concept, so it is
	    // unconditionally legacy JOSS (STD-006 sec.11.8: an absent
	    // old-format marker means implicit legacy JOSS, not a reject).
	    return MarshallingFormat.JOSS.getFormat();
	}
	throw new IOException("wrong log format version: " + logVersion);
    }

    /**
     * The accept/refuse decision (guard (ii) of JGDMS-STD-006 sec.3
     * item 5): refuses, fail-closed, a snapshot whose born format is
     * unrecognized or contradicts this instance's configured format --
     * before any snapshot state is consumed.
     *
     * @param recoveredFormat the token {@link #resolveRecoveredFormat}
     *        returned
     * @param configured this instance's own born format
     * @throws IOException if the recovered format is unrecognized, or
     *         contradicts {@code configured}
     */
    static void checkFormat(String recoveredFormat, MarshallingFormat configured)
	    throws IOException
    {
	if (!MarshallingFormat.JOSS.getFormat().equals(recoveredFormat)
	    && !MarshallingFormat.ATOMIC_DER.getFormat().equals(recoveredFormat))
	{
	    // Fail-closed on ambiguity: an unrecognized token is corruption
	    // (or a future format this version does not know), never
	    // something to guess a meaning for.
	    throw new IOException(
		"snapshot carries an unrecognized born-format marker: "
		+ recoveredFormat);
	}
	if (!configured.getFormat().equals(recoveredFormat)) {
	    throw new IOException(
		"snapshot was born " + recoveredFormat
		+ " but this instance is configured " + configured.getFormat()
		+ ": a store's marshalling format is fixed at instantiation"
		+ " and immutable for its life (JGDMS-STD-006 sec.3 item 5)"
		+ " -- an in-place format change on a populated store is"
		+ " refused; redeploy a new born-" + configured.getFormat()
		+ " instance instead");
	}
    }
}
