import java.io.ByteArrayOutputStream;
import java.net.StandardProtocolFamily;
import java.net.UnixDomainSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;

/**
 * Mock SPIRE agent for QA: serves a single X.509 SVID over the SPIFFE Workload
 * API (minimal HTTP/2 + gRPC) on a Unix domain socket, matching DirtyChai's
 * SpireWorkloadApiClient/SpireConnection (static-HPACK-only, no flow control,
 * single stream). Args: socketPath certChainPem keyPkcs8Pem bundlePem|"" spiffeId
 */
public final class MockSpireAgent {

    static final int F_DATA = 0x0, F_HEADERS = 0x1, F_SETTINGS = 0x4;
    static final int FLAG_END_STREAM = 0x1, FLAG_END_HEADERS = 0x4, FLAG_ACK = 0x1;
    static final byte[] PREFACE = "PRI * HTTP/2.0\r\n\r\nSM\r\n\r\n".getBytes(StandardCharsets.US_ASCII);

    private final Path socketPath;
    private final byte[] svidResponse; // pre-encoded X509SVIDResponse protobuf

    MockSpireAgent(Path socketPath, byte[] svidResponse) {
        this.socketPath = socketPath;
        this.svidResponse = svidResponse;
    }

    public static void main(String[] args) throws Exception {
        Path socket = Path.of(args[0]);
        byte[] certChainDer = pemBlocksToDer(Path.of(args[1]), "CERTIFICATE");
        byte[] keyDer = pemBlocksToDer(Path.of(args[2]), "PRIVATE KEY");
        byte[] bundlePem = (args.length > 4 && !args[3].isEmpty())
                ? Files.readAllBytes(Path.of(args[3])) : null;
        String spiffeId = args[args.length - 1];
        byte[] resp = encodeX509SVIDResponse(certChainDer, keyDer, bundlePem, spiffeId);
        Files.deleteIfExists(socket);
        new MockSpireAgent(socket, resp).serve();
    }

    void serve() throws Exception {
        ServerSocketChannel ssc = ServerSocketChannel.open(StandardProtocolFamily.UNIX);
        ssc.bind(UnixDomainSocketAddress.of(socketPath));
        System.err.println("MockSpireAgent: listening on " + socketPath
                + " (svidResponse=" + svidResponse.length + "B)");
        ExecutorService pool = Executors.newCachedThreadPool();
        for (;;) {
            SocketChannel ch = ssc.accept();
            pool.submit(() -> { try { handle(ch); } catch (Exception e) {
                System.err.println("MockSpireAgent conn ended: " + e); } });
        }
    }

    /** One client connection: HTTP/2 handshake, then answer each FetchX509SVID stream. */
    void handle(SocketChannel ch) throws Exception {
        readFully(ch, ByteBuffer.allocate(PREFACE.length));   // client preface
        readFrame(ch);                                        // client SETTINGS
        writeFrame(ch, F_SETTINGS, 0, 0, new byte[0]);        // server SETTINGS
        readFrame(ch);                                        // client SETTINGS ACK
        System.err.println("MockSpireAgent: handshake complete");
        for (;;) {
            Frame f = readFrame(ch);                          // HEADERS for a FetchX509SVID stream
            if (f == null) return;
            if (f.type != F_HEADERS) continue;
            int sid = f.streamId;
            readFrame(ch);                                    // empty request DATA (END_STREAM)
            writeFrame(ch, F_HEADERS, FLAG_END_HEADERS, sid, new byte[]{(byte) 0x88}); // :status 200
            writeFrame(ch, F_DATA, FLAG_END_STREAM, sid, grpcMessage(svidResponse));   // the SVID
            // gRPC trailers: DirtyChai's client reads one more frame after an END_STREAM
            // DATA (SpireConnection readX509SVIDResponse line 200) and ignores it.
            writeFrame(ch, F_HEADERS, FLAG_END_STREAM | FLAG_END_HEADERS, sid, new byte[0]);
            System.err.println("MockSpireAgent: served SVID on stream " + sid);
        }
    }

    // ---- HTTP/2 framing ----
    static final class Frame { int type, flags, streamId; byte[] payload; }

    Frame readFrame(SocketChannel ch) throws Exception {
        ByteBuffer h = ByteBuffer.allocate(9);
        if (!readFully(ch, h)) return null;
        h.flip();
        int len = ((h.get() & 0xFF) << 16) | ((h.get() & 0xFF) << 8) | (h.get() & 0xFF);
        Frame f = new Frame();
        f.type = h.get() & 0xFF;
        f.flags = h.get() & 0xFF;
        f.streamId = h.getInt() & 0x7FFFFFFF;
        ByteBuffer p = ByteBuffer.allocate(len);
        if (len > 0 && !readFully(ch, p)) return null;
        f.payload = p.array();
        return f;
    }

    void writeFrame(SocketChannel ch, int type, int flags, int streamId, byte[] payload) throws Exception {
        ByteBuffer b = ByteBuffer.allocate(9 + payload.length);
        b.put((byte) (payload.length >>> 16));
        b.put((byte) (payload.length >>> 8));
        b.put((byte) payload.length);
        b.put((byte) type);
        b.put((byte) flags);
        b.putInt(streamId & 0x7FFFFFFF);
        b.put(payload);
        b.flip();
        while (b.hasRemaining()) ch.write(b);
    }

    boolean readFully(SocketChannel ch, ByteBuffer b) throws Exception {
        while (b.hasRemaining()) { if (ch.read(b) < 0) return false; }
        return true;
    }

    /** gRPC length-prefixed message: 1 compression byte + 4 big-endian length + protobuf. */
    static byte[] grpcMessage(byte[] proto) {
        byte[] r = new byte[5 + proto.length];
        r[1] = (byte) (proto.length >>> 24);
        r[2] = (byte) (proto.length >>> 16);
        r[3] = (byte) (proto.length >>> 8);
        r[4] = (byte) proto.length;
        System.arraycopy(proto, 0, r, 5, proto.length);
        return r;
    }

    // ---- protobuf (DirtyChai SpireProtobuf field order: cert=1,key=2,bundle=3,spiffeId=4) ----
    static byte[] encodeX509SVIDResponse(byte[] certDer, byte[] keyDer, byte[] bundle, String spiffeId)
            throws Exception {
        ByteArrayOutputStream svid = new ByteArrayOutputStream();
        writeLenField(svid, 1, certDer);                                   // x509_svid (DER chain)
        writeLenField(svid, 2, keyDer);                                    // x509_svid_key (PKCS8 DER)
        if (bundle != null) writeLenField(svid, 3, bundle);               // bundle (PEM)
        if (spiffeId != null) writeLenField(svid, 4, spiffeId.getBytes(StandardCharsets.UTF_8));
        ByteArrayOutputStream resp = new ByteArrayOutputStream();
        writeLenField(resp, 1, svid.toByteArray());                        // repeated X509SVID svids
        return resp.toByteArray();
    }

    static void writeLenField(ByteArrayOutputStream out, int fieldNum, byte[] value) throws Exception {
        writeVarint(out, (fieldNum << 3) | 2);  // wire type 2 = length-delimited
        writeVarint(out, value.length);
        out.write(value);
    }

    static void writeVarint(ByteArrayOutputStream out, int v) {
        while ((v & ~0x7F) != 0) { out.write((v & 0x7F) | 0x80); v >>>= 7; }
        out.write(v);
    }

    // ---- PEM helpers ----
    /** Concatenate the DER of every PEM block of the given type (e.g. all CERTIFICATEs in a chain). */
    static byte[] pemBlocksToDer(Path pem, String type) throws Exception {
        String text = Files.readString(pem);
        String begin = "-----BEGIN " + type + "-----";
        String end = "-----END " + type + "-----";
        ByteArrayOutputStream der = new ByteArrayOutputStream();
        int i = 0;
        for (;;) {
            int b = text.indexOf(begin, i);
            if (b < 0) break;
            int e = text.indexOf(end, b);
            String body = text.substring(b + begin.length(), e).replaceAll("\\s", "");
            der.write(Base64.getDecoder().decode(body));
            i = e + end.length();
        }
        if (der.size() == 0) throw new IllegalArgumentException("no " + type + " block in " + pem);
        return der.toByteArray();
    }
}
