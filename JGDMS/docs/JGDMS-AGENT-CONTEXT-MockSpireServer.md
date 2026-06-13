# JGDMS Agent Context: Mock SPIRE Workload API Server

**Task:** Implement `MockSpireServer` — a lightweight mock SPIRE Workload API server
for use in the JGDMS QA test harness. Enables full integration testing of
`SpiffeCredentialManager` including SVID rotation, reconnection, and watcher lifecycle.

**Phase:** Phase 4 of the SPIFFE Discovery Provider implementation
(see `JGDMS-AGENT-CONTEXT-SpiffeDiscoveryProvider-v1.3.md`).

**Location:** QA test harness infrastructure. Does NOT need to be bootstrap-safe.
Modern Java features (lambdas, streams, var) are permitted.

---

## 1. Why This Exists

`SpiffeCredentialManager.FileSvidSource` covers the static credential case but
cannot test:

- SVID rotation triggering `SvidRotationListener` callbacks
- `SvidWatcher` reconnection after server restart
- `UnavailableClient` retry path when SPIRE is initially unreachable
- The full SPIRE protocol path (grpc framing, response streaming, error handling)
- Time-based SVID expiry behaviour

The mock server enables all of these without requiring a real SPIRE deployment
in the QA environment.

---

## 2. SPIRE Workload API Protocol

### 2.1 Transport

- **Socket**: Unix domain socket (configurable path; use a temp file in QA)
- **Security**: None — h2c (HTTP/2 cleartext). The socket IS the auth boundary.
- **No TLS** on the Workload API socket.

### 2.2 HTTP/2 h2c Framing

The connection follows standard HTTP/2 (RFC 7540):

```
Client                              Server
  |                                    |
  |-- Connection Preface ------------->|  "PRI * HTTP/2.0\r\n\r\nSM\r\n\r\n"
  |-- SETTINGS (empty) -------------->|
  |<-- SETTINGS (server config) ------|
  |<-- SETTINGS ACK ------------------|
  |-- SETTINGS ACK ------------------>|
  |                                    |
  |-- HEADERS (method call) --------->|  stream_id=1
  |-- DATA (grpc request body) ------>|  stream_id=1, empty for FetchX509SVID
  |                                    |
  |<-- HEADERS (200 OK) --------------|  stream_id=1
  |<-- DATA (grpc response) ----------|  stream_id=1, one per SVID update
  |<-- HEADERS (trailers, grpc-status)|  stream_id=1 (unary only)
```

**Frame format** (RFC 7540 §4.1):
```
+-----------------------------------------------+
|                 Length (24)                   |
+---------------+---------------+---------------+
|   Type (8)    |   Flags (8)   |
+-+-------------+---------------+-------------------------------+
|R|                 Stream Identifier (31)                      |
+=+=============================================================+
|                   Frame Payload (0...)                      ...
+---------------------------------------------------------------+
```

Frame types needed:
- `0x0` — DATA
- `0x1` — HEADERS
- `0x4` — SETTINGS
- `0x5` — PUSH_PROMISE (not used)
- `0x8` — WINDOW_UPDATE (send to unblock client)
- `0x3` — RST_STREAM (for error handling)

### 2.3 gRPC Framing

Each DATA frame payload contains zero or more length-prefixed gRPC messages:

```
+------------------------------------------+
|  Compressed-Flag (1 byte, always 0x00)   |
+------------------------------------------+
|  Message-Length (4 bytes, big-endian)    |
+------------------------------------------+
|  Message (protobuf bytes, Message-Length)|
+------------------------------------------+
```

### 2.4 gRPC Methods

| Method | Type | Path |
|---|---|---|
| `FetchX509SVID` | Unary (server-streaming with one response then close) | `/spiffe.workload.SpiffeWorkloadAPI/FetchX509SVID` |
| `WatchX509SVIDs` | Server-streaming | `/spiffe.workload.SpiffeWorkloadAPI/WatchX509SVIDs` |

Looking at `SpireWorkloadApiClient`:
- `fetchSVID()` calls `conn.fetchX509SVID()` — one response then stream closed
- `startWatching()` calls `conn.startFetchX509SVIDStream()` then reads in a loop

Both likely use the same underlying gRPC streaming method; `fetchSVID` just reads
the first response. Confirm against `SpireConnection.java`.

**[OPEN]** Verify the exact method path strings from `SpireConnection.java` — they
are the literal strings used in the HTTP/2 `:path` header.

### 2.5 gRPC Request

The `FetchX509SVIDRequest` / `WatchX509SVIDsRequest` protobuf is empty (no fields).
The client sends a zero-length message body:
```
0x00                -- Compressed-Flag: not compressed
0x00 0x00 0x00 0x00 -- Message-Length: 0
                    -- (no message bytes)
```

### 2.6 gRPC Response Protobuf

`X509SVIDResponse` (field 1 = repeated X509SVID):

```
Field 1 (SVID, length-delimited, repeated):
  Field 1 (certChain, bytes):   DER-encoded certificate chain
  Field 2 (privateKey, bytes):  PKCS#8 encoded private key
  Field 3 (bundle, bytes):      DER-encoded CA certificates
  Field 4 (spiffeId, string):   "spiffe://trust-domain/path"
```

This matches `SpireProtobuf.X509SVID` exactly.

---

## 3. SpireProtobuf Encode Path Extension

`SpireProtobuf.java` currently has only a decode path. Add the encode path:

```java
// Add to SpireProtobuf.java

/**
 * Encodes an X509SVIDResponse for transmission over the mock server.
 *
 * @param response the response to encode
 * @return protobuf-encoded bytes
 * @throws IOException if encoding fails
 */
static byte[] encodeX509SVIDResponse(X509SVIDResponse response) throws IOException {
    java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
    for (X509SVID svid : response.svids) {
        byte[] svidBytes = encodeX509SVID(svid);
        // Field 1 (FIELD_RESPONSE_SVIDS), wire type 2 (length-delimited)
        writeVarint(out, (FIELD_RESPONSE_SVIDS << 3) | WIRE_TYPE_LENGTH_DELIMITED);
        writeVarint(out, svidBytes.length);
        out.write(svidBytes);
    }
    return out.toByteArray();
}

static byte[] encodeX509SVID(X509SVID svid) throws IOException {
    java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
    writeField(out, FIELD_X509_SVID_CERT,     svid.certChain);
    writeField(out, FIELD_X509_SVID_KEY,      svid.privateKey);
    if (svid.bundle != null && svid.bundle.length > 0) {
        writeField(out, FIELD_X509_SVID_BUNDLE, svid.bundle);
    }
    if (svid.spiffeId != null) {
        writeField(out, FIELD_X509_SVID_SPIFFE_ID,
            svid.spiffeId.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }
    return out.toByteArray();
}

private static void writeField(java.io.ByteArrayOutputStream out,
                                int fieldNumber, byte[] value) throws IOException {
    writeVarint(out, (fieldNumber << 3) | WIRE_TYPE_LENGTH_DELIMITED);
    writeVarint(out, value.length);
    out.write(value);
}

private static void writeVarint(java.io.ByteArrayOutputStream out,
                                 int value) throws IOException {
    while ((value & ~0x7F) != 0) {
        out.write((value & 0x7F) | 0x80);
        value >>>= 7;
    }
    out.write(value);
}
```

**Note**: The encode path does NOT need to be bootstrap-safe (it lives in the mock
server, not in production code). Modern Java features may be used in the surrounding
mock server code, but keep `SpireProtobuf.java` itself bootstrap-safe since it is
shared with production.

---

## 4. MockSpireServer Architecture

### 4.1 Public API

```java
package org.apache.river.test.spiffe;

/**
 * Mock SPIRE Workload API server for QA testing.
 * Serves X.509 SVIDs over a Unix domain socket using h2c + gRPC.
 *
 * Usage:
 *   Path socket = Files.createTempFile("spire-test", ".sock");
 *   Files.delete(socket); // socket must not exist before bind
 *   try (MockSpireServer server = new MockSpireServer(socket)) {
 *       server.start(initialSvid);
 *       // SpiffeCredentialManager connects here
 *       server.rotateSvid(newSvid);  // triggers SvidRotationListener
 *   }
 */
public final class MockSpireServer implements AutoCloseable {

    /**
     * Creates a server that will listen on the given Unix socket path.
     * Does not bind or start yet.
     *
     * @param socketPath path for the Unix domain socket; must not exist
     */
    public MockSpireServer(Path socketPath) { ... }

    /**
     * Binds the socket and starts the server thread.
     * Blocks until the socket is ready to accept connections.
     *
     * @param initialSvid the SVID to serve immediately to connecting clients
     */
    public void start(SpireProtobuf.X509SVID initialSvid)
        throws IOException { ... }

    /**
     * Pushes a new SVID to all active watch streams.
     * Triggers SvidRotationListener callbacks in any connected
     * SpiffeCredentialManager instances.
     *
     * @param newSvid the updated SVID
     */
    public void rotateSvid(SpireProtobuf.X509SVID newSvid) { ... }

    /**
     * Simulates a SPIRE agent restart: closes all active connections,
     * then re-opens the socket. Tests the SpiffeCredentialManager
     * reconnection / UnavailableClient retry path.
     */
    public void restart() throws IOException { ... }

    /**
     * Closes the server and deletes the socket file.
     */
    @Override
    public void close() { ... }
}
```

### 4.2 Internal Structure

```
MockSpireServer
  ├── ServerSocketChannel (Unix domain socket, NIO)
  ├── currentSvid: volatile SpireProtobuf.X509SVID
  ├── activeConnections: CopyOnWriteArrayList<ClientConnection>
  └── serverThread: Thread (daemon)
        └── accept loop → ClientConnection per accepted socket

ClientConnection
  ├── SocketChannel (the accepted client socket)
  ├── Http2Connection (wraps SocketChannel)
  │     ├── connection preface handling
  │     ├── SETTINGS exchange
  │     ├── HEADERS frame parsing (extract :path, :method)
  │     └── DATA frame read/write
  └── activeStreams: Map<Integer, GrpcStream>

GrpcStream
  ├── streamId: int
  ├── method: String (from :path header)
  └── streaming: boolean (false for fetch, true for watch)
```

### 4.3 Request Handling Flow

```
accept() → new ClientConnection
  → handle connection preface
  → SETTINGS exchange
  → read HEADERS frame → parse :path → determine method
  → read DATA frame → parse gRPC request (empty)
  → send HEADERS (200 OK, content-type: application/grpc+proto)
  → send DATA (gRPC response: currentSvid)
  → if streaming: park thread / register in activeConnections
  → if unary:     send HEADERS trailers (grpc-status: 0), close stream
```

On `rotateSvid(newSvid)`:
```
  → atomically update currentSvid
  → for each active streaming ClientConnection:
      → encode newSvid as gRPC DATA frame
      → write to SocketChannel
```

### 4.4 HTTP/2 Implementation Notes

**Connection preface**: The client sends the 24-byte magic string followed by a
SETTINGS frame. The server must:
1. Read and validate the 24-byte magic: `PRI * HTTP/2.0\r\n\r\nSM\r\n\r\n`
2. Send its own SETTINGS frame (can be empty: length=0, type=0x4, flags=0x0, stream=0)
3. Send a SETTINGS ACK (length=0, type=0x4, flags=0x1, stream=0)
4. Read the client's SETTINGS ACK

**HEADERS frame**: Contains HPACK-encoded headers. The minimal implementation reads
the raw bytes and extracts `:path` and `:method` using a simple HPACK literal decoder
or pre-computed byte patterns for the known method paths.

**[OPEN]** HPACK is non-trivial to implement from scratch. Options:
a. Use a minimal HPACK library in the test harness (test-only dependency)
b. Use `java.net.http` internals (fragile, not public API)
c. Implement only the subset of HPACK needed: static table lookup + literal encoding
   — this is feasible since the client sends a small fixed set of headers

**Recommendation**: Option (c) for zero test dependencies. The mock only needs to
decode `:path`, `:method`, and `content-type` from the client, and encode `:status 200`
and `content-type: application/grpc+proto` in the response. That's 4 well-known
static table entries (indices 3, 4, 1, 31 in the HPACK static table).

**WINDOW_UPDATE**: HTTP/2 uses flow control. The mock server should send a
WINDOW_UPDATE frame on both the connection (stream 0) and each new stream to grant
the client sufficient window before sending large DATA frames.

**DATA frame padding**: Not needed for the mock — send DATA frames without padding
(flags=0x0 for streaming, flags=0x1 for END_STREAM on unary response).

### 4.5 Unix Domain Socket (Java 16+)

```java
// Java 16+ UnixDomainSocketAddress
ServerSocketChannel server = ServerSocketChannel.open(
    StandardProtocolFamily.UNIX);
server.bind(UnixDomainSocketAddress.of(socketPath));
server.configureBlocking(false);
// or blocking — simpler for the mock server thread

// Accept:
SocketChannel client = server.accept();
```

---

## 5. Test SVIDs

The mock server serves `SpireProtobuf.X509SVID` instances. For QA, these are loaded
from the same PEM files used by `SpiffeCredentialManager.FileSvidSource`. A helper
method converts PEM files to `SpireProtobuf.X509SVID`:

```java
public static SpireProtobuf.X509SVID loadFromPem(Path svidPem, Path keyPem,
                                                   Path bundlePem)
    throws IOException, GeneralSecurityException { ... }
```

This reuses the same PEM parsing logic already in
`SpiffeCredentialManager.FileSvidSource`, ensuring test credentials are identical
to those the `FileSvidSource` path would produce.

---

## 6. Test Scenarios Enabled

| Scenario | How the mock supports it |
|---|---|
| Basic SVID fetch | Client connects → mock serves initial SVID |
| SVID rotation | `rotateSvid()` → mock pushes new SVID to watch stream → `SvidRotationListener.onSvidRotation()` fires |
| SPIRE unavailable at startup | Start `SpiffeCredentialManager` before calling `start()` → `UnavailableClient` created → then `start()` → reconnection path exercised |
| SPIRE agent restart | `restart()` → all connections closed → `SpiffeCredentialManager` watcher errors → reconnect loop triggered |
| Multiple watchers | Two `SpiffeCredentialManager` instances connected → `rotateSvid()` updates both |
| Trust bundle update | Include updated bundle in `rotateSvid()` payload → `getTrustBundle()` returns new CA |
| Malformed response | Add `sendMalformedResponse()` to mock → verify `SpiffeConnectionException` thrown and retry occurs |

---

## 7. Key Files to Read Before Starting

| File | Why |
|---|---|
| `SpireConnection.java` | The client's HTTP/2 + gRPC implementation — the mock server mirrors this |
| `SpireWorkloadApiClient.java` | How the client uses `SpireConnection`; identifies the exact method paths |
| `SpireProtobuf.java` | The protobuf decoder to extend with the encode path |
| `SpiffeCredentialManager.java` | The component under test; understand `reconnectWatcher()`, `UnavailableClient`, and `SvidRotationListener` notification |

---

## 8. Open Questions

1. **Method path strings** — confirm the exact `:path` header values used by
   `SpireConnection` for both `fetchX509SVID()` and the watch stream. These
   are the routing keys in the mock server.

2. **HPACK approach** — settle option (a) library or (c) hand-rolled minimal
   HPACK before starting implementation. Recommendation: (c) — the static table
   entries needed are small and fixed.

3. **Java version floor** — confirm `java.net` Unix domain socket support
   (`StandardProtocolFamily.UNIX`, Java 16+) is available in the QA harness JVM.
   If not, use a third-party Unix socket library or adapt to a TCP loopback socket
   for cross-platform testing.

4. **`WatchX509SVIDs` vs `FetchX509SVID`** — confirm from `SpireConnection.java`
   whether the watcher uses a separate streaming RPC or the same `FetchX509SVID`
   with a streaming response.

---

## 9. Acceptance Criteria

- [ ] `MockSpireServer.start(svid)` serves the SVID within 500 ms of client connect.
- [ ] `SpiffeCredentialManager` connecting to the mock socket produces a non-null
  `SpiffeSubject` with `SpiffePrincipal` in its principal set.
- [ ] `MockSpireServer.rotateSvid(newSvid)` triggers `onSvidRotation()` within 1 s
  on all connected `SpiffeCredentialManager` instances.
- [ ] `MockSpireServer.restart()` followed by reconnect produces a fresh `SpiffeSubject`.
- [ ] `MockSpireServer` cleans up its socket file on `close()`.
- [ ] All Phase 1–3 discovery format tests pass with the mock server replacing
  `FileSvidSource`.
