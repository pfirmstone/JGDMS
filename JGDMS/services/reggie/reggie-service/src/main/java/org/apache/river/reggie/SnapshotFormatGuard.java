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

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import net.jini.core.constraint.MarshallingFormat;

/**
 * Guard mirroring the born-immutable-format persistence guard added to
 * Outrigger's snaplogstore {@code BackEnd} (SOW-Entry-ATOMIC-DER-Migration.md
 * sec.3 item 5, task A1): a registrar's marshalling format is fixed at
 * instantiation and immutable for the life of a populated persistent store.
 * This class writes/reads the format marker that {@link RegistrarImpl}'s
 * {@code takeSnapshot}/{@code recoverSnapshot} persist alongside the
 * snapshot's log-format version, and enforces the recovery-side half of the
 * guard: a snapshot recovered whose format contradicts the instance's own
 * configuration is refused.
 *
 * <p>Extracted as a small, standalone, directly unit-testable class --
 * {@code RegistrarImpl} has no pre-existing test infrastructure and is a
 * large, monolithic class not otherwise amenable to unit testing without
 * a full {@code Configuration}/export/persistence harness -- rather than
 * inlining the version/marker logic, mirroring the shape of Outrigger's
 * guard (a small helper around the on-disk version int) without requiring
 * a full {@code RegistrarImpl} to exercise it.
 *
 * <p><b>Recovery-guard airtightness (2026-07-20, per the SOW's A3 row):</b>
 * unlike Outrigger, for Reggie the "absent format marker means implicit
 * JOSS" rule is airtight, not merely a graceful-degradation default: DER is
 * unreleased, so no deployed Reggie snapshot can contain anything other
 * than legacy JOSS-encoded state. A version other than the two recognized
 * here ({@link #PRE_A3_LOG_VERSION}, {@link #LOG_VERSION}) is therefore
 * unconditionally a hard reject (genuine corruption or an unsupported
 * future version), never a third graceful-degradation case.
 */
final class SnapshotFormatGuard {

    /**
     * Pre-A3 on-disk shape: the log-format version every released JGDMS
     * build wrote, with no format marker following it -- unconditionally
     * legacy JOSS (STD-006 sec.11.8 graceful degradation: an absent
     * old-format marker means implicit legacy JOSS, never a reject).
     */
    static final int PRE_A3_LOG_VERSION = 3;

    /**
     * Current on-disk shape: {@link #PRE_A3_LOG_VERSION} + 1. The format
     * marker (a {@code String}, {@link MarshallingFormat#getFormat()})
     * follows immediately after this version int.
     */
    static final int LOG_VERSION = PRE_A3_LOG_VERSION + 1;

    private SnapshotFormatGuard() {}

    /**
     * Writes the format marker. Called immediately after the version int
     * ({@link #LOG_VERSION}) in {@code RegistrarImpl.takeSnapshot}.
     *
     * @param out the snapshot's object stream
     * @param format this instance's configured format token
     *        ({@code entryFormat().getFormat()})
     */
    static void writeFormatMarker(ObjectOutputStream out, String format) throws IOException {
	out.writeObject(format);
    }

    /**
     * Resolves the marshalling format token recovered from a snapshot,
     * given the log-format version actually read from the stream.
     *
     * @param in the snapshot's object input stream, positioned immediately
     *        after the version int was read
     * @param logVersion the version int just read from {@code in}
     * @return the recovered format token: the marker just written by
     *         {@link #writeFormatMarker} for {@link #LOG_VERSION}, or
     *         {@link MarshallingFormat#JOSS}'s token unconditionally for
     *         {@link #PRE_A3_LOG_VERSION} (which predates the
     *         born-immutable-format concept and therefore never wrote a
     *         marker)
     * @throws IOException if {@code logVersion} is neither recognized
     *         version (a hard reject: genuine corruption or an unsupported
     *         version)
     */
    static String resolveRecoveredFormat(ObjectInputStream in, int logVersion)
	    throws IOException, ClassNotFoundException
    {
	if (logVersion == LOG_VERSION) {
	    return (String) in.readObject();
	}
	if (logVersion == PRE_A3_LOG_VERSION) {
	    return MarshallingFormat.JOSS.getFormat();
	}
	throw new IOException("wrong log format version: " + logVersion);
    }

    /**
     * Guard: fail closed if {@code recoveredFormat} contradicts
     * {@code configured}. Not called for an empty store (no prior
     * snapshot exists) -- any format is a legal birth for an empty store.
     *
     * @param recoveredFormat the format token recovered from an existing
     *        snapshot (see {@link #resolveRecoveredFormat})
     * @param configured this instance's own configured format
     * @throws IOException if {@code recoveredFormat} does not match
     *         {@code configured}
     */
    static void checkFormat(String recoveredFormat, MarshallingFormat configured)
	    throws IOException
    {
	String configuredToken = configured.getFormat();
	if (!configuredToken.equals(recoveredFormat)) {
	    throw new IOException(
		"Refusing to recover: this store was born with marshalling "
		+ "format " + recoveredFormat + " but this instance is "
		+ "configured for " + configuredToken + ". A registrar's "
		+ "marshalling format is fixed at instantiation and "
		+ "immutable for the life of the store (JGDMS-STD-006 sec.3 "
		+ "item 5); flipping useDerForEntries on a populated store "
		+ "is not a supported migration -- redeploy a new instance "
		+ "instead.");
	}
    }
}
