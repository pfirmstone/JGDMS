/*
 * Copyright (C) 2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package au.net.zeus.jgdms.bench.serial;

import au.net.zeus.jgdms.bench.serial.Payloads.Batch;
import au.net.zeus.jgdms.bench.serial.Payloads.Blob;
import au.net.zeus.jgdms.bench.serial.Payloads.Point;
import au.net.zeus.jgdms.bench.serial.Payloads.Record;
import java.rmi.Remote;
import java.rmi.RemoteException;

/**
 * The remote service the JERI tier calls: one typed echo method per payload,
 * so each call marshals the payload as an argument and again as the result,
 * exactly as a real remote method with a value argument and a value result.
 *
 * <p>Methods are typed rather than {@code Object echo(Object)} because that is
 * how JGDMS services declare remote methods, and because the DER layer
 * resolves the declared parameter and result types.
 */
public interface Echo extends Remote {

    Point point(Point p) throws RemoteException;

    Record record(Record r) throws RemoteException;

    Batch batch(Batch b) throws RemoteException;

    Blob blob(Blob b) throws RemoteException;

    /** No arguments, no result: the floor cost of a JERI call in each format. */
    void ping() throws RemoteException;

    /** The server-side implementation; returns its argument unchanged. */
    final class Impl implements Echo {
        @Override public Point point(Point p) { return p; }
        @Override public Record record(Record r) { return r; }
        @Override public Batch batch(Batch b) { return b; }
        @Override public Blob blob(Blob b) { return b; }
        @Override public void ping() {}
    }
}
