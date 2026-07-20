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
package org.apache.river.outrigger.snaplogstore;

import java.io.File;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.util.HashMap;
import net.jini.config.AbstractConfiguration;
import net.jini.config.Configuration;
import net.jini.config.ConfigurationException;
import net.jini.config.NoSuchEntryException;
import net.jini.id.Uuid;
import net.jini.id.UuidFactory;
import org.apache.river.outrigger.LogOps;
import org.apache.river.outrigger.OutriggerServerImpl;
import org.apache.river.outrigger.Recover;
import org.apache.river.outrigger.Store;
import org.apache.river.outrigger.StoredObject;
import org.apache.river.outrigger.StoredResource;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.*;

/**
 * Tests for the born-immutable-format guards (JGDMS-STD-006 sec.3 item 5,
 * guard i+ii) added to {@link BackEnd}/{@link LogStore}: a snapshot's
 * recovered marshalling format is compared against the recovering
 * instance's own configuration, and a mismatch is refused (fail-closed)
 * before any other state is recovered. An empty store (no snapshot yet)
 * fires no guard -- any format is a legal birth.
 *
 * <p>Drives the real {@link LogStore}/{@link BackEnd} persistence path (a
 * minimal in-memory {@link Configuration} stands in for a real deployment
 * configuration) so the guard is exercised exactly as
 * {@code OutriggerServerImpl} would exercise it, not a reimplementation of
 * the snapshot format in the test.
 */
public class BornFormatGuardTest {

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    /** Minimal {@code Configuration} providing only what {@code LogStore} needs. */
    private static class DirConfiguration extends AbstractConfiguration {
        private final String dir;
        DirConfiguration(String dir) { this.dir = dir; }

        @Override
        protected <T> Object getEntryInternal(String component, String name,
                Class<T> type, Object data) throws ConfigurationException {
            if (OutriggerServerImpl.PERSISTENCE_DIR_CONFIG_ENTRY.equals(name)) {
                return dir;
            }
            throw new NoSuchEntryException(name);
        }
    }

    /** Records what was recovered; enforces the same comparison OutriggerServerImpl does. */
    private static class RecoverStub implements Recover {
        final String configuredFormat;
        volatile boolean entryFormatDispatched;

        RecoverStub(String configuredFormat) { this.configuredFormat = configuredFormat; }

        public void recoverSessionId(long sessionId) { }
        public void recoverJoinState(StoredObject state) throws Exception { }
        public void recoverWrite(StoredResource entry, Long txnId) throws Exception { }
        public void recoverTake(Uuid cookie, Long txnId) throws Exception { }
        public void recoverRegister(StoredResource registration, String type,
                StoredObject[] templates) throws Exception { }
        public void recoverTransaction(Long txnId, StoredObject transaction) throws Exception { }
        public void recoverUuid(Uuid uuid) { }

        public void recoverEntryFormat(String format) {
            entryFormatDispatched = true;
            if (!configuredFormat.equals(format)) {
                throw new IllegalStateException(
                    "Refusing to start: store born with " + format
                    + " but configured for " + configuredFormat);
            }
        }
    }

    private LogStore openStore(String dir) throws ConfigurationException {
        return new LogStore(new DirConfiguration(dir));
    }

    /** First boot of a brand-new (empty) store: no snapshot exists yet. */
    @Test
    public void emptyStoreFiresNoGuard() throws Exception {
        String dir = tmp.newFolder("empty").getAbsolutePath();
        LogStore store = openStore(dir);
        RecoverStub recover = new RecoverStub("JOSS");
        LogOps log = store.setupStore(recover, "JOSS");
        assertNotNull(log);
        assertFalse("no snapshot exists yet -- recoverEntryFormat must not be called",
            recover.entryFormatDispatched);
        store.close();
    }

    /** Boot a store, force a snapshot to be written, then reopen with the same format. */
    @Test
    public void sameFormatOnRecoveryFiresNoException() throws Exception {
        String dir = tmp.newFolder("same").getAbsolutePath();
        writeOneSnapshot(dir, "JOSS");

        LogStore store2 = openStore(dir);
        RecoverStub recover2 = new RecoverStub("JOSS");
        store2.setupStore(recover2, "JOSS");
        assertTrue("a populated store's snapshot must dispatch recoverEntryFormat",
            recover2.entryFormatDispatched);
        store2.close();
    }

    /** JOSS-configured instance recovering a DER-written snapshot must refuse to start. */
    @Test
    public void derSnapshotRefusedByJossConfiguredInstance() throws Exception {
        String dir = tmp.newFolder("der-then-joss").getAbsolutePath();
        writeOneSnapshot(dir, "JGDMS-STD-006/ATOMIC-DER");

        LogStore store2 = openStore(dir);
        RecoverStub recover2 = new RecoverStub("JOSS");
        try {
            store2.setupStore(recover2, "JOSS");
            fail("recovering a DER-born snapshot as a JOSS-configured instance must throw");
        } catch (IllegalStateException expected) {
            // expected: fail-closed guard fired -- LogStore never finishes
            // setupStore (the guard throws before the consumer thread
            // starts or the front-end log file is opened), so there is
            // nothing for this test to close.
        }
    }

    /** JOSS-configured instance recovering a DER-written snapshot (and vice versa) refuses startup. */
    @Test
    public void jossSnapshotRefusedByDerConfiguredInstance() throws Exception {
        String dir = tmp.newFolder("joss-then-der").getAbsolutePath();
        writeOneSnapshot(dir, "JOSS");

        LogStore store2 = openStore(dir);
        RecoverStub recover2 = new RecoverStub("JGDMS-STD-006/ATOMIC-DER");
        try {
            store2.setupStore(recover2, "JGDMS-STD-006/ATOMIC-DER");
            fail("recovering a JOSS-born snapshot as a DER-configured instance must throw");
        } catch (IllegalStateException expected) {
            // expected: fail-closed guard fired -- see note above.
        }
    }

    /**
     * Boots a fresh store at {@code dir} twice: boot 1 logs one operation
     * (a {@code uuidOp}, forced synchronously to disk) and closes without
     * ever writing a snapshot (nothing has rolled/consumed the log file
     * yet); boot 2's own {@code setupStore} (mirroring
     * {@code OutriggerServerImpl}'s startup, {@code BackEnd
     * .setupStore}'s {@code consumeLogs(true)}) consumes that log file and
     * writes the store's first snapshot, carrying {@code format}. Neither
     * boot dispatches {@code recoverEntryFormat} (no snapshot exists when
     * either one starts), leaving a real on-disk snapshot for a
     * subsequent (third) boot to recover.
     */
    private void writeOneSnapshot(String dir, String format) throws Exception {
        LogStore store1 = openStore(dir);
        RecoverStub recover1 = new RecoverStub(format);
        LogOps log1 = store1.setupStore(recover1, format);
        assertFalse("boot 1 is an empty store", recover1.entryFormatDispatched);
        log1.uuidOp(UuidFactory.generate());
        store1.close();

        LogStore store2 = openStore(dir);
        RecoverStub recover2 = new RecoverStub(format);
        store2.setupStore(recover2, format);
        assertFalse("boot 2 still has no prior snapshot to recover",
            recover2.entryFormatDispatched);
        store2.close();

        // Confirm a snapshot file now exists before the recovery test proceeds.
        File[] children = new File(dir).listFiles();
        assertNotNull(children);
        boolean sawSnapshot = false;
        for (File f : children) {
            if (f.getName().startsWith("Snapshot.")) sawSnapshot = true;
        }
        assertTrue("expected a Snapshot.* file after two boots", sawSnapshot);
    }

    /**
     * Writes a snapshot file in the PRE-A1 on-disk shape directly (bypassing
     * {@link BackEnd}/{@link LogStore}, which only ever write the current
     * shape): {@code LogFile.LOG_VERSION} as the version int, with NO
     * entryFormat marker following it -- exactly what every pre-A1 release
     * persisted. Used to prove the board-review fix to
     * {@link BackEnd#recoverSnapshot()}: a new-code instance must still be
     * able to load this shape (it predates the born-immutable-format
     * concept entirely, so it is unconditionally legacy JOSS -- STD-006
     * sec.11.8 graceful degradation), dispatched through the very same
     * {@code recoverEntryFormat} guard as a current-shape snapshot.
     */
    private void writeLegacyPreA1Snapshot(String dir) throws IOException {
        File[] snapshot = new File[1];
        // Constructing SnapshotFile against an empty directory writes a
        // dummy Snapshot.1 placeholder (mirrors BackEnd's own first use);
        // the real, legacy-shaped content is written below and commit()
        // replaces the placeholder with it.
        SnapshotFile sf = new SnapshotFile(
            new File(dir, "Snapshot.").getAbsolutePath(), snapshot);

        ObjectOutputStream out = sf.next();
        out.writeInt(LogFile.LOG_VERSION); // pre-A1 version int -- no format token follows
        out.writeObject(Long.valueOf(0L));       // sessionId
        out.writeObject(null);                   // joinState
        out.writeObject(new HashMap());          // entries
        out.writeObject(new HashMap());          // registrations
        out.writeObject(new HashMap());          // pendingTxns
        out.writeObject(null);                   // topUuid
        out.writeObject(null);                   // lastLog
        sf.commit();
    }

    /**
     * Fix 1 regression (blocking): a JOSS-configured instance recovering a
     * PRE-A1-shaped legacy snapshot (no format marker at all) must recover
     * normally -- not be refused by a blunt version-int mismatch. This is
     * exactly the JOSS-stays-JOSS in-place upgrade the pre-fix code would
     * have refused outright (data loss), since the pre-fix version check
     * threw before ever reaching the format-comparison guard.
     */
    @Test
    public void preA1LegacySnapshotRecoveredByJossConfiguredInstance() throws Exception {
        String dir = tmp.newFolder("pre-a1-joss").getAbsolutePath();
        writeLegacyPreA1Snapshot(dir);

        LogStore store = openStore(dir);
        RecoverStub recover = new RecoverStub("JOSS");
        store.setupStore(recover, "JOSS");
        assertTrue("a legacy pre-A1 snapshot must still dispatch "
            + "recoverEntryFormat (as implicit JOSS), not be refused outright",
            recover.entryFormatDispatched);
        store.close();
    }

    /**
     * Fix 1 regression (blocking), other half: a DER-configured instance
     * pointed at the same pre-A1-shaped legacy snapshot must still be
     * correctly refused -- via the existing {@code recoverEntryFormat}
     * guard (a genuine format contradiction), not via the blunt version
     * check this fix removes.
     */
    @Test
    public void preA1LegacySnapshotRefusedByDerConfiguredInstance() throws Exception {
        String dir = tmp.newFolder("pre-a1-der").getAbsolutePath();
        writeLegacyPreA1Snapshot(dir);

        LogStore store = openStore(dir);
        RecoverStub recover = new RecoverStub("JGDMS-STD-006/ATOMIC-DER");
        try {
            store.setupStore(recover, "JGDMS-STD-006/ATOMIC-DER");
            fail("a DER-configured instance recovering a legacy (implicit "
                + "JOSS) pre-A1 snapshot must still be refused");
        } catch (IllegalStateException expected) {
            // expected: fail-closed guard fired.
        }
        assertTrue("the guard must have actually been consulted",
            recover.entryFormatDispatched);
    }

    /**
     * A version int that is neither the pre-A1 legacy value nor the
     * current {@code SNAPSHOT_VERSION} remains a hard reject -- genuine
     * corruption, not a recognized shape.
     */
    @Test
    public void unknownVersionIsStillHardRejected() throws Exception {
        String dir = tmp.newFolder("unknown-version").getAbsolutePath();
        File[] snapshot = new File[1];
        SnapshotFile sf = new SnapshotFile(
            new File(dir, "Snapshot.").getAbsolutePath(), snapshot);
        ObjectOutputStream out = sf.next();
        out.writeInt(LogFile.LOG_VERSION + 99); // not a recognized version
        sf.commit();

        LogStore store = openStore(dir);
        RecoverStub recover = new RecoverStub("JOSS");
        try {
            store.setupStore(recover, "JOSS");
            fail("an unrecognized snapshot version must be a hard reject");
        } catch (net.jini.space.InternalSpaceException expected) {
            // expected: BackEnd.logAndThrowRecoveryException wraps "Wrong
            // file version" in an InternalSpaceException.
        }
    }
}
