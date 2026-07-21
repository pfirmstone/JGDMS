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
import java.util.Map;
import net.jini.config.AbstractConfiguration;
import net.jini.config.Configuration;
import net.jini.config.ConfigurationException;
import net.jini.config.NoSuchEntryException;
import net.jini.core.constraint.MarshallingFormat;
import net.jini.id.Uuid;
import net.jini.id.UuidFactory;
import org.apache.river.outrigger.IncompatibleStoreException;
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
 * Tests for the recovery format guard dispatch in
 * {@link BackEnd}/{@link LogStore}. Outrigger is DER-only in JGDMS 4.0.0
 * ({@code SOW-Outrigger-DER-Only-JOSS-Rejection.md}): the guard is
 * <em>unconditional</em> -- any snapshot whose persisted format is not
 * ATOMIC_DER is refused via the checked
 * {@link org.apache.river.outrigger.IncompatibleStoreException},
 * before any other state is recovered and before {@code consumeLogs} can
 * mutate the store (a refused store stays pristine on disk for the
 * offline converter). An empty store (no snapshot yet) fires no guard --
 * a legal (DER) birth.
 *
 * <p>Drives the real {@link LogStore}/{@link BackEnd} persistence path (a
 * minimal in-memory {@link Configuration} stands in for a real deployment
 * configuration) so the guard is exercised exactly as
 * {@code OutriggerServerImpl} would exercise it, not a reimplementation of
 * the snapshot format in the test. The {@link RecoverStub} mirrors
 * {@code OutriggerServerImpl.recoverEntryFormat}'s unconditional rule.
 */
public class BornFormatGuardTest {

    private static final String DER = MarshallingFormat.ATOMIC_DER.getFormat();
    private static final String JOSS = MarshallingFormat.JOSS.getFormat();

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

    /**
     * Records what was recovered; enforces the same UNCONDITIONAL DER-only
     * rule {@code OutriggerServerImpl.recoverEntryFormat} does (JGDMS
     * 4.0.0): anything but ATOMIC_DER is refused via the checked
     * {@link IncompatibleStoreException}, regardless of configuration.
     */
    private static class RecoverStub implements Recover {
        volatile boolean entryFormatDispatched;

        public void recoverSessionId(long sessionId) { }
        public void recoverJoinState(StoredObject state) throws Exception { }
        public void recoverWrite(StoredResource entry, Long txnId) throws Exception { }
        public void recoverTake(Uuid cookie, Long txnId) throws Exception { }
        public void recoverRegister(StoredResource registration, String type,
                StoredObject[] templates) throws Exception { }
        public void recoverTransaction(Long txnId, StoredObject transaction) throws Exception { }
        public void recoverUuid(Uuid uuid) { }

        public void recoverEntryFormat(String format)
                throws IncompatibleStoreException {
            entryFormatDispatched = true;
            if (!DER.equals(format)) {
                throw new IncompatibleStoreException(
                    "Refusing to start: store born with " + format
                    + " but Outrigger is " + DER + "-only (JGDMS 4.0.0)");
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
        RecoverStub recover = new RecoverStub();
        LogOps log = store.setupStore(recover, DER);
        assertNotNull(log);
        assertFalse("no snapshot exists yet -- recoverEntryFormat must not be called",
            recover.entryFormatDispatched);
        store.close();
    }

    /** Boot a store, force a DER snapshot to be written, then reopen: recovers fine. */
    @Test
    public void derSnapshotOnRecoveryFiresNoException() throws Exception {
        String dir = tmp.newFolder("der").getAbsolutePath();
        writeOneSnapshot(dir, DER);

        LogStore store2 = openStore(dir);
        RecoverStub recover2 = new RecoverStub();
        store2.setupStore(recover2, DER);
        assertTrue("a populated store's snapshot must dispatch recoverEntryFormat",
            recover2.entryFormatDispatched);
        store2.close();
    }

    /**
     * A JOSS-born snapshot is refused UNCONDITIONALLY (DER-only, JGDMS
     * 4.0.0) -- via the checked IncompatibleStoreException so the
     * caller's cleanup path runs -- and the refused store is left
     * byte-for-byte pristine on disk (the guard fires before consumeLogs
     * can consume logs or write a fresh snapshot), preserving it for the
     * offline JOSS-to-DER converter.
     */
    @Test
    public void jossSnapshotRefusedUnconditionallyAndStorePristine() throws Exception {
        String dir = tmp.newFolder("joss-refused").getAbsolutePath();
        writeOneSnapshot(dir, JOSS);

        final Map<String, Long> before = dirState(dir);

        LogStore store2 = openStore(dir);
        RecoverStub recover2 = new RecoverStub();
        try {
            store2.setupStore(recover2, DER);
            fail("recovering a JOSS-born snapshot must throw");
        } catch (IncompatibleStoreException expected) {
            // expected: fail-closed guard fired, CHECKED -- LogStore never
            // finishes setupStore (the guard throws before the consumer
            // thread starts or the front-end log file is opened), so
            // there is nothing for this test to close.
        }
        assertTrue("the guard must have actually been consulted",
            recover2.entryFormatDispatched);
        assertEquals("a refused store must be left pristine on disk",
            before, dirState(dir));
    }

    /** Name -> length for every file under dir (recursive not needed: flat). */
    private static Map<String, Long> dirState(String dir) {
        Map<String, Long> state = new HashMap<String, Long>();
        File[] children = new File(dir).listFiles();
        assertNotNull(children);
        for (File f : children) {
            state.put(f.getName(), Long.valueOf(f.length()));
        }
        return state;
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
        RecoverStub recover1 = new RecoverStub();
        LogOps log1 = store1.setupStore(recover1, format);
        assertFalse("boot 1 is an empty store", recover1.entryFormatDispatched);
        log1.uuidOp(UuidFactory.generate());
        store1.close();

        LogStore store2 = openStore(dir);
        RecoverStub recover2 = new RecoverStub();
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
     * A PRE-A1-shaped legacy snapshot (no format marker at all --
     * unconditionally implicit JOSS) is recognized by its version int and
     * dispatched through the {@code recoverEntryFormat} guard as JOSS --
     * where the DER-only rule refuses it with the checked, actionable
     * {@link IncompatibleStoreException}, not a blunt version-mismatch
     * reject. The distinction matters for the operator message: the store
     * is a convertible pre-DER store, not corruption.
     */
    @Test
    public void preA1LegacySnapshotDispatchedAsJossAndRefused() throws Exception {
        String dir = tmp.newFolder("pre-a1").getAbsolutePath();
        writeLegacyPreA1Snapshot(dir);

        LogStore store = openStore(dir);
        RecoverStub recover = new RecoverStub();
        try {
            store.setupStore(recover, DER);
            fail("a legacy (implicit JOSS) pre-A1 snapshot must be refused");
        } catch (IncompatibleStoreException expected) {
            // expected: fail-closed guard fired, CHECKED.
        }
        assertTrue("the guard must have actually been consulted "
            + "(recognized shape dispatched as implicit JOSS, not a blunt "
            + "version reject)",
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
        RecoverStub recover = new RecoverStub();
        try {
            store.setupStore(recover, DER);
            fail("an unrecognized snapshot version must be a hard reject");
        } catch (net.jini.space.InternalSpaceException expected) {
            // expected: BackEnd.logAndThrowRecoveryException wraps "Wrong
            // file version" in an InternalSpaceException.
        }
    }
}
