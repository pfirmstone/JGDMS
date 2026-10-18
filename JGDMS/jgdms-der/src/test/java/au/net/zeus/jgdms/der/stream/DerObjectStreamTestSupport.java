package au.net.zeus.jgdms.der.stream;

import au.net.zeus.jgdms.der.DerWriter;
import au.net.zeus.jgdms.der.Tag;

import java.io.IOException;

/** Test-only bridge to the package-private {@link DerObjectStreamCodec}. */
public final class DerObjectStreamTestSupport {
    private DerObjectStreamTestSupport() {}

    /** Reads one {@code [3]} String item whose content is exactly {@code content}. */
    public static Object readStringItem(byte[] content) throws IOException, ClassNotFoundException {
        byte[] item = DerWriter.writeTlv(new Tag(Tag.CLASS_CONTEXT, false, 3), content);
        byte[] stream = new byte[3 + item.length];
        System.arraycopy(StreamSchemaDedup.versionTlv(), 0, stream, 0, 3);
        System.arraycopy(item, 0, stream, 3, item.length);
        DerObjectStreamCodec in = new DerObjectStreamCodec();
        in.initReader(stream);
        return in.readObject();
    }

    public static void writeString(String s) throws IOException {
        new DerObjectStreamCodec().writeObject(s);
    }

    public static void writeObject(Object o) throws IOException {
        new DerObjectStreamCodec().writeObject(o);
    }
}
