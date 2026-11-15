/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package au.net.zeus.jgdms.bench.cel;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.zip.CRC32C;

/**
 * INPUT FIXTURE for the STD-011 stage-1 benchmarks: a de-paging, CRC-verifying reader of a {@code .rawscan} processing
 * file that keeps only the three columns the benchmark expressions read (range, azimuth, elevation), as float32 arrays.
 * It is not a production step and builds no answer; it exists because the JGDMS repository has no rawscan reader (the
 * format owner's reader lives in the Observations_to_Salsa repository, which this repository cannot depend on). The
 * record layout and page rule below are INTENT copied from that repository's {@code RawScanFormat} and
 * {@code E57PageWriter} as READ on 2026-11-11, and are NOT a specification of this code: the file itself is the check
 * (every page CRC must verify, every stored angle must lie in its bound).
 *
 * <h2>What the file layout turned out to be (MEASURED on SdeDatabase_Station2_Scan2.rawscan, 2026-11-11)</h2>
 * <ul>
 *   <li>The 48-byte header carries NO record count. The count is {@code points/@recordCount} in the E57 XML section the
 *       header points at ({@code xmlPhysicalOffset}, {@code xmlLogicalLength}), itself CRC-paged. The brief said the
 *       header held the count; it does not.</li>
 *   <li>The page CRC is the CRC32C of the 1020 payload bytes stored BIG-endian at bytes 1020-1023. The javadoc of
 *       {@code E57PageWriter} (and the brief) say little-endian; {@code E57PageWriter.flushPage} writes
 *       {@code Integer.reverseBytes} and the file verifies big-endian on all 7 XML pages and every record page. The code
 *       and the file outrank the javadoc.</li>
 *   <li>Records start at physical page 1 and run for {@code recordCount x 56} logical bytes; appended sections (angular
 *       index, selection) follow on later pages, so the file length must not be used as a count.</li>
 * </ul>
 *
 * <h2>Working set</h2>
 * Three float arrays of {@code recordCount} entries (12 bytes per record, 99,132,360 bytes for 8,261,030 records) plus
 * one {@value #PAGES_PER_READ}-page read buffer and its de-paged copy (about 2 MiB). Nothing else is retained.
 */
public final class RawScanColumns {

    public static final int PAGE_BYTES = 1024;
    public static final int PAYLOAD_BYTES = 1020;
    public static final int RECORD_BYTES = 56;
    public static final int OFFSET_RANGE = 0;
    public static final int OFFSET_AZIMUTH = 4;
    public static final int OFFSET_ELEVATION = 8;

    /** Largest stored azimuth: the float32 nearest pi, which is ABOVE Math.PI (RawScanFormat contract). */
    public static final float AZIMUTH_LIMIT = (float) Math.PI;
    /** Largest stored elevation magnitude: the float32 nearest pi/2. */
    public static final float ELEVATION_LIMIT = (float) (Math.PI / 2);

    private static final int PAGES_PER_READ = 1024;

    /**
     * What the load found. Every count names its noun and denominator in {@link #describe()}.
     *
     * @param recordCount            {@code points/@recordCount} from the XML section
     * @param geoConversionPoints    {@code geo:conversionPointsWritten} from the same XML (a second statement of the count)
     * @param pagesInFile            {@code fileLength / 1024}
     * @param recordPages            pages that hold the records, {@code ceil(recordCount x 56 / 1020)}
     * @param pagesVerified          pages whose CRC was checked: page 0, the record pages, and every later page
     * @param crcFailuresPage0       page 0 CRC failures (0 or 1)
     * @param crcFailuresRecords     record-page CRC failures
     * @param crcFailuresOther       CRC failures on the pages after the records (appended sections and the XML)
     * @param azimuthOutOfBounds     records whose stored azimuth is NaN or outside [-AZIMUTH_LIMIT, AZIMUTH_LIMIT]
     * @param elevationOutOfBounds   records whose stored elevation is NaN or outside [-ELEVATION_LIMIT, ELEVATION_LIMIT]
     * @param rangeNegativeOrNaN     records whose stored range is negative or NaN (the writer's convention refusal)
     * @param rangeZero              records with range == 0 (no return)
     * @param xmlCartesianBounds     {xMin, xMax, yMin, yMax, zMin, zMax} from the XML {@code cartesianBounds}
     * @param sx12ReturnsDelivered   {@code geo:sx12ReturnsDelivered} from the XML, or -1 when the file has none (an SX12 element:
     *                               the converter's own count of returns it wrote, so a record that is not one is a blank)
     * @param slackBytes             bytes between the last record and the end of the last record page's payload
     * @param slackNonZeroBytes      how many of those are not zero (the converter pads with zeros: a reader that starts
     *                               late, or ends early, finds records' bytes here or leaves them out of its count)
     */
    public record Census(long recordCount, long geoConversionPoints, long pagesInFile, long recordPages,
                         long pagesVerified, long crcFailuresPage0, long crcFailuresRecords, long crcFailuresOther,
                         long azimuthOutOfBounds, long elevationOutOfBounds, long rangeNegativeOrNaN, long rangeZero,
                         double[] xmlCartesianBounds, long sx12ReturnsDelivered, long slackBytes, long slackNonZeroBytes) {

        /** One report line per count, each with its denominator. */
        public String describe() {
            StringBuilder sb = new StringBuilder(1024);
            sb.append("records=").append(recordCount).append(" (points/@recordCount; geo:conversionPointsWritten=")
                    .append(geoConversionPoints).append(")\n");
            sb.append("pages in file=").append(pagesInFile).append(", record pages=").append(recordPages)
                    .append(", pages CRC-verified=").append(pagesVerified).append("\n");
            sb.append("CRC failures: page0=").append(crcFailuresPage0).append(" of 1; record pages=")
                    .append(crcFailuresRecords).append(" of ").append(recordPages).append("; later pages=")
                    .append(crcFailuresOther).append(" of ").append(pagesInFile - 1 - recordPages).append("\n");
            sb.append("azimuth outside stored-float32 bound: ").append(azimuthOutOfBounds).append(" of ").append(recordCount)
                    .append(" records\n");
            sb.append("elevation outside stored-float32 bound: ").append(elevationOutOfBounds).append(" of ")
                    .append(recordCount).append(" records\n");
            sb.append("range negative or NaN: ").append(rangeNegativeOrNaN).append(" of ").append(recordCount)
                    .append(" records\n");
            sb.append("range == 0 (no return): ").append(rangeZero).append(" of ").append(recordCount).append(" records\n");
            sb.append("geo:sx12ReturnsDelivered=").append(sx12ReturnsDelivered).append(" (-1 = absent); records minus returns delivered=")
                    .append(sx12ReturnsDelivered < 0 ? "n/a" : Long.toString(recordCount - sx12ReturnsDelivered)).append('\n');
            sb.append("slack after the last record: ").append(slackBytes).append(" bytes, ").append(slackNonZeroBytes)
                    .append(" of them non-zero\n");
            return sb.toString();
        }
    }

    public final float[] range;
    public final float[] azimuth;
    public final float[] elevation;
    public final Census census;

    private RawScanColumns(float[] range, float[] azimuth, float[] elevation, Census census) {
        this.range = range;
        this.azimuth = azimuth;
        this.elevation = elevation;
        this.census = census;
    }

    public int count() {
        return range.length;
    }

    /**
     * Reads {@code file} read-only, CRC-verifies every page, and returns the three columns.
     *
     * @throws IOException on a read failure or a header/XML that does not fit the layout this fixture was written for
     *                     (a CRC mismatch is COUNTED in the census, not thrown, so the caller sees how many)
     */
    public static RawScanColumns load(Path file) throws IOException {
        try (FileChannel ch = FileChannel.open(file, StandardOpenOption.READ)) {
            long fileLength = ch.size();
            if (fileLength % PAGE_BYTES != 0) {
                throw new IOException("file length is not a whole number of 1024-byte pages");
            }
            ByteBuffer page0 = ByteBuffer.allocate(PAGE_BYTES).order(ByteOrder.LITTLE_ENDIAN);
            readFully(ch, page0, 0L);
            byte[] sig = Arrays.copyOf(page0.array(), 8);
            if (!"RAWSCAN1".equals(new String(sig, StandardCharsets.US_ASCII))) {
                throw new IOException("signature is not RAWSCAN1");
            }
            int versionMajor = page0.getInt(8);
            long headerFileLength = page0.getLong(16);
            long xmlPhysicalOffset = page0.getLong(24);
            long xmlLogicalLength = page0.getLong(32);
            long pageSize = page0.getLong(40);
            if (versionMajor != 2 || pageSize != PAGE_BYTES || headerFileLength != fileLength) {
                throw new IOException(new StringBuilder("unexpected header: major=").append(versionMajor)
                        .append(" pageSize=").append(pageSize).append(" headerFileLength=").append(headerFileLength)
                        .append(" actual=").append(fileLength).toString());
            }
            long crcFailuresPage0 = pageCrcOk(page0.array(), 0) ? 0L : 1L;

            byte[] xml = readXml(ch, xmlPhysicalOffset, xmlLogicalLength);
            long[] counts = new long[3];
            double[] bounds = new double[6];
            long fileOffset = parseXml(xml, counts, bounds);
            long recordCount = counts[0];
            if (fileOffset != PAGE_BYTES) {
                throw new IOException(new StringBuilder("records do not start at page 1: fileOffset=").append(fileOffset)
                        .toString());
            }
            if (recordCount <= 0 || recordCount > Integer.MAX_VALUE) {
                throw new IOException(new StringBuilder("record count out of range: ").append(recordCount).toString());
            }
            int n = (int) recordCount;
            long recordBytes = recordCount * RECORD_BYTES;
            long recordPages = (recordBytes + PAYLOAD_BYTES - 1) / PAYLOAD_BYTES;
            long pagesInFile = fileLength / PAGE_BYTES;
            if (1L + recordPages > pagesInFile) {
                throw new IOException("the records would run past the end of the file");
            }

            float[] range = new float[n];
            float[] azimuth = new float[n];
            float[] elevation = new float[n];
            long crcFailuresRecords = 0;
            long azBad = 0;
            long elBad = 0;
            long rngBad = 0;
            long rngZero = 0;
            long slackBytes = 0;
            long slackNonZero = 0;

            ByteBuffer raw = ByteBuffer.allocate(PAGES_PER_READ * PAGE_BYTES);
            byte[] logical = new byte[PAGES_PER_READ * PAYLOAD_BYTES + RECORD_BYTES];
            ByteBuffer lb = ByteBuffer.wrap(logical).order(ByteOrder.LITTLE_ENDIAN);
            int carry = 0;
            int decoded = 0;
            long page = 1;
            long pagesLeft = recordPages;
            while (pagesLeft > 0) {
                int pages = (int) Math.min(PAGES_PER_READ, pagesLeft);
                raw.clear();
                raw.limit(pages * PAGE_BYTES);
                readFully(ch, raw, page * PAGE_BYTES);
                byte[] rb = raw.array();
                for (int p = 0; p < pages; p++) {
                    if (!pageCrcOk(rb, p * PAGE_BYTES)) {
                        crcFailuresRecords++;
                    }
                    System.arraycopy(rb, p * PAGE_BYTES, logical, carry + p * PAYLOAD_BYTES, PAYLOAD_BYTES);
                }
                int total = carry + pages * PAYLOAD_BYTES;
                int whole = Math.min(total / RECORD_BYTES, n - decoded);
                for (int r = 0; r < whole; r++) {
                    int base = r * RECORD_BYTES;
                    float rng = lb.getFloat(base + OFFSET_RANGE);
                    float az = lb.getFloat(base + OFFSET_AZIMUTH);
                    float el = lb.getFloat(base + OFFSET_ELEVATION);
                    int i = decoded + r;
                    range[i] = rng;
                    azimuth[i] = az;
                    elevation[i] = el;
                    if (!(az >= -AZIMUTH_LIMIT && az <= AZIMUTH_LIMIT)) {
                        azBad++;
                    }
                    if (!(el >= -ELEVATION_LIMIT && el <= ELEVATION_LIMIT)) {
                        elBad++;
                    }
                    if (!(rng >= 0f)) {
                        rngBad++;
                    } else if (rng == 0f) {
                        rngZero++;
                    }
                }
                decoded += whole;
                carry = total - whole * RECORD_BYTES;
                if (decoded == n) {
                    // Review F3 (Opus, 2026-11-11): a reader that begins k records late still decodes n records and passes
                    // every CRC and bound; its last k records come from this zero padding. Counting the padding makes that
                    // visible (it is also asserted through rangeZero against the converter's own returns-delivered count).
                    slackBytes = carry;
                    for (int s = 0; s < carry; s++) {
                        if (logical[whole * RECORD_BYTES + s] != 0) {
                            slackNonZero++;
                        }
                    }
                }
                if (carry > 0 && decoded < n) {
                    System.arraycopy(logical, whole * RECORD_BYTES, logical, 0, carry);
                } else {
                    carry = 0;
                }
                page += pages;
                pagesLeft -= pages;
            }
            if (decoded != n) {
                throw new IOException(new StringBuilder("decoded ").append(decoded).append(" of ").append(n)
                        .append(" records").toString());
            }

            long crcFailuresOther = 0;
            long laterPages = pagesInFile - 1 - recordPages;
            ByteBuffer one = ByteBuffer.allocate(PAGE_BYTES);
            for (long q = 1 + recordPages; q < pagesInFile; q++) {
                one.clear();
                readFully(ch, one, q * PAGE_BYTES);
                if (!pageCrcOk(one.array(), 0)) {
                    crcFailuresOther++;
                }
            }
            Census census = new Census(recordCount, counts[1], pagesInFile, recordPages,
                    1L + recordPages + laterPages, crcFailuresPage0, crcFailuresRecords, crcFailuresOther,
                    azBad, elBad, rngBad, rngZero, bounds, counts[2], slackBytes, slackNonZero);
            return new RawScanColumns(range, azimuth, elevation, census);
        }
    }

    /** CRC32C over the 1020 payload bytes against the big-endian uint32 in the last four bytes of the page. */
    private static boolean pageCrcOk(byte[] buf, int pageStart) {
        CRC32C crc = new CRC32C();
        crc.update(buf, pageStart, PAYLOAD_BYTES);
        int s = pageStart + PAYLOAD_BYTES;
        long stored = ((buf[s] & 0xFFL) << 24) | ((buf[s + 1] & 0xFFL) << 16) | ((buf[s + 2] & 0xFFL) << 8)
                | (buf[s + 3] & 0xFFL);
        return crc.getValue() == stored;
    }

    private static void readFully(FileChannel ch, ByteBuffer b, long position) throws IOException {
        long at = position;
        while (b.hasRemaining()) {
            int n = ch.read(b, at);
            if (n < 0) {
                throw new IOException(new StringBuilder("unexpected end of file at ").append(at).toString());
            }
            at += n;
        }
    }

    /** The XML section's logical bytes; a CRC mismatch on its pages is an IOException (nothing downstream is trustworthy). */
    private static byte[] readXml(FileChannel ch, long physicalOffset, long logicalLength) throws IOException {
        if (logicalLength <= 0 || logicalLength > (1 << 24)) {
            throw new IOException("implausible XML length");
        }
        int len = (int) logicalLength;
        byte[] out = new byte[len];
        int pages = (len + PAYLOAD_BYTES - 1) / PAYLOAD_BYTES;
        ByteBuffer one = ByteBuffer.allocate(PAGE_BYTES);
        for (int p = 0; p < pages; p++) {
            one.clear();
            readFully(ch, one, physicalOffset + (long) p * PAGE_BYTES);
            if (!pageCrcOk(one.array(), 0)) {
                throw new IOException(new StringBuilder("XML section page ").append(p).append(" fails its CRC").toString());
            }
            System.arraycopy(one.array(), 0, out, p * PAYLOAD_BYTES, Math.min(PAYLOAD_BYTES, len - p * PAYLOAD_BYTES));
        }
        return out;
    }

    /**
     * Fills counts[0] = points/@recordCount, counts[1] = geo:conversionPointsWritten, counts[2] = geo:sx12ReturnsDelivered
     * (-1 when absent) and bounds; returns points/@fileOffset.
     */
    private static long parseXml(byte[] xml, long[] counts, double[] bounds) throws IOException {
        try {
            DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
            f.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            f.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            DocumentBuilder b = f.newDocumentBuilder();
            Document d = b.parse(new ByteArrayInputStream(xml));
            NodeList points = d.getElementsByTagName("points");
            if (points.getLength() != 1) {
                throw new IOException(new StringBuilder("expected exactly one points element, found ")
                        .append(points.getLength()).toString());
            }
            Element p = (Element) points.item(0);
            counts[0] = Long.parseLong(p.getAttribute("recordCount"));
            long fileOffset = Long.parseLong(p.getAttribute("fileOffset"));
            counts[1] = Long.parseLong(text(d, "geo:conversionPointsWritten"));
            NodeList delivered = d.getElementsByTagName("geo:sx12ReturnsDelivered");
            counts[2] = delivered.getLength() == 1 ? Long.parseLong(delivered.item(0).getTextContent().trim()) : -1L;
            String[] names = {"xMinimum", "xMaximum", "yMinimum", "yMaximum", "zMinimum", "zMaximum"};
            for (int i = 0; i < names.length; i++) {
                bounds[i] = Double.parseDouble(text(d, names[i]));
            }
            return fileOffset;
        } catch (IOException e) {
            throw e;
        } catch (Exception e) {
            throw new IOException("XML section could not be read", e);
        }
    }

    private static String text(Document d, String tag) throws IOException {
        NodeList l = d.getElementsByTagName(tag);
        if (l.getLength() != 1) {
            throw new IOException(new StringBuilder("expected one ").append(tag).append(", found ").append(l.getLength())
                    .toString());
        }
        return l.item(0).getTextContent().trim();
    }
}
