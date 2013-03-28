/*
 *  Licensed to the Apache Software Foundation (ASF) under one or more
 *  contributor license agreements.  See the NOTICE file distributed with
 *  this work for additional information regarding copyright ownership.
 *  The ASF licenses this file to You under the Apache License, Version 2.0
 *  (the "License"); you may not use this file except in compliance with
 *  the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package org.apache.river.api.net;

import java.io.File;
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.Map;
import java.util.StringTokenizer;
import org.apache.river.impl.Messages;

/**
 *
 * @author peter
 */
final class UriParser {
    
    private static final char [] latin = new char[256];
    private static final String [] latinEsc = new String[256];
    private final static Map<String, Character> unreserved = new HashMap<String, Character>(66); // To be unescaped during normalisation.
    
    /* 2.1.  Percent-Encoding
     * 
     * A percent-encoding mechanism is used to represent a data octet in a
     * component when that octet's corresponding character is outside the
     * allowed set or is being used as a delimiter of, or within, the
     * component.  A percent-encoded octet is encoded as a character
     * triplet, consisting of the percent character "%" followed by the two
     * hexadecimal digits representing that octet's numeric value.  For
     * example, "%20" is the percent-encoding for the binary octet
     * "00100000" (ABNF: %x20), which in US-ASCII corresponds to the space
     * character (SP).  Section 2.4 describes when percent-encoding and
     * decoding is applied.
     * 
     *    pct-encoded = "%" HEXDIG HEXDIG
     * 
     * The uppercase hexadecimal digits 'A' through 'F' are equivalent to
     * the lowercase digits 'a' through 'f', respectively.  If two URIs
     * differ only in the case of hexadecimal digits used in percent-encoded
     * octets, they are equivalent.  For consistency, URI producers and
     * normalizers should use uppercase hexadecimal digits for all percent-
     * encodings.
     */
    // Any character that is not part of the reserved and unreserved sets must
    // be encoded.
    // Section 2.1 Percent encoding must be converted to upper case during normalisation.
    private static final char escape = '%';
     /* RFC3986 obsoletes RFC2396 and RFC2732
     * 
     * reserved    = gen-delims / sub-delims
     * 
     * gen-delims  = ":" / "/" / "?" / "#" / "[" / "]" / "@"
     * 
     * sub-delims  = "!" / "$" / "&" / "'" / "(" / ")"
     *               / "*" / "+" / "," / ";" / "="
     */
    // Section 2.2 Reserved set is protected from normalisation.
    private static final char [] gen_delims = {':', '/', '?', '#', '[', ']', '@'};
    private static final char [] sub_delims = {'!', '$', '&', '\'', '(', ')', '*',
                                                            '+', ',', ';', '='};
    
    /*
     * For consistency, percent-encoded octets in the ranges of ALPHA
     * (%41-%5A and %61-%7A), DIGIT (%30-%39), hyphen (%2D), period (%2E),
     * underscore (%5F), or tilde (%7E) should not be created by URI
     * producers and, when found in a URI, should be decoded to their
     * corresponding unreserved characters by URI normalizers.
     */
    // Section 2.3 Unreserved characters (Allowed) must be decoded during normalisation if % encoded.
    private static final char [] lowalpha = "abcdefghijklmnopqrstuvwxyz".toCharArray();
    private static final char [] upalpha = "ABCDEFGHIJKLMNOPQRSTUVWXYZ".toCharArray();
    private static final char [] numeric = "0123456789".toCharArray();
    private static final char [] unres_punct =  {'-' , '.' , '_' , '~'};
    private static final char [] schemeEx = "+-.".toCharArray(); // + ALPHA and numeric.
     
    static {
        processLatin();
        processUnreserved();
    }
    
    private static void processUnreserved(){
        int l = lowalpha.length;
        for (int i = 0, n = 97; i < l; i++, n++){
            unreserved.put(String.valueOf(latinEsc[n]), Character.valueOf(lowalpha[i]));
        }
        l = upalpha.length;
        for (int i = 0, n = 65; i < l; i++, n++){
            unreserved.put(String.valueOf(latinEsc[n]), Character.valueOf(upalpha[i]));
        }
        l = numeric.length;
        for (int i = 0, n = 48; i < l; i++, n++){
            unreserved.put(String.valueOf(latinEsc[n]), Character.valueOf(numeric[i]));
        }
        l = unres_punct.length;
        for (int i = 0; i < l; i++){
            int n = index(latin, unres_punct[i]);
            unreserved.put(String.valueOf(latinEsc[n]), Character.valueOf(unres_punct[i]));
        }
        l = schemeEx.length;
        for (int i = 0; i < l; i++){
            int n = index(latin, schemeEx[i]);
            unreserved.put(String.valueOf(latinEsc[n]), Character.valueOf(schemeEx[i]));
        }
    } 
    
    /**
     * Finds a character in an array and returns the index at which it exists,
     * or returns -1 if it doesn't
     * 
     * @param array
     * @param character
     * @return 
     */
    public static int index(char [] array, char character){
        int l = array.length;
        for (int i = 0; i < l; i++){
            if (array[i] == character) return i;
        }
        return -1;
    }
    
    /**
     * Encodes illegal characters according to RFC3986, "%" characters are not 
     * encoded, since they are legal and in case the string already
     * contains escaped characters.  The percent character must be encoded
     * manually prior to calling this method.
     * <p>
     * No normalisation or platform specific changes are performed.
     * 
     * @param str
     * @return
     * @throws URISyntaxException  
     */
    public static String escapeIllegalCharacters(String str) throws URISyntaxException {
        if (str == null) return null;
        char [] chars = str.toCharArray();
        int len = chars.length;
        StringBuilder sb = new StringBuilder(len + 12);
        boolean esc = false;
        for (int i = 0; i < len; i++){
            if (chars[i] == escape){
                /*  Section 2.4
                 * Because the percent ("%") character serves as the indicator for
                 * percent-encoded octets, it must be percent-encoded as "%25" for that
                 * octet to be used as data within a URI.  Implementations must not
                 * percent-encode or decode the same string more than once, as decoding
                 * an already decoded string might lead to misinterpreting a percent
                 * data octet as the beginning of a percent-encoding, or vice versa in
                 * the case of percent-encoding an already percent-encoded string.
                 */
                sb.append(chars[i]);
            }else if ( index(gen_delims, chars[i]) != -1 
                    || index(sub_delims, chars[i]) != -1
                    || index(lowalpha, chars[i]) != -1
                    || index(upalpha, chars[i]) != -1
                    || index(numeric, chars[i]) != -1
                    || index(unres_punct, chars[i]) != -1){
                sb.append(chars[i]);
            }else {
                int n = index(latin, chars[i]);
                if (n < 0) throw new URISyntaxException(str, "String contains unescapable character");
                sb.append(latinEsc[n]);
                esc = true;
            }
        }
        if (!esc) return str;
        return sb.toString();
    }
    
    /** Fixes windows file URI string by converting back slashes to forward
     * slashes and inserting a forward slash before the drive letter if it is
     * missing.  No normalisation or modification of case is performed.
     */
    public static String fixWindowsURI(String uri) {
        if (uri == null) return null;
        if ( uri.startsWith("file:") || uri.startsWith("FILE:")){
            char [] u = uri.toCharArray();
            int l = u.length; 
            StringBuilder sb = new StringBuilder();
            for (int i=0; i<l; i++){
                // Ensure we use forward slashes
                if (u[i] == File.separatorChar) {
                    sb.append('/');
                    continue;
                }
                if (i == 5 && uri.startsWith(":", 6 )) {
                    // Windows drive letter without leading slashes doesn't comply
                    // with URI spec, fix it here
                    sb.append("/");
                }
                sb.append(u[i]);
            }
            return sb.toString();
        }
        return uri;
    }
    
    private static void processLatin(){
        /*  Complete list of Unicode Latin possible to represent with percentage encoding.*/
        //          Basic Latin 
        //            Position Decimal Name Appearance 
        //            0x0000 0 <control>: NULL 
        latin[0] = '\u0000';
        latinEsc[0] = "%00";
        //            0x0001 1 <control>: START OF HEADING  
        latin[1] = '\u0001';
        latinEsc[1] = "%01";
        //            0x0002 2 <control>: START OF TEXT 
        latin[2] = '\u0002';
        latinEsc[2] = "%02";
        //            0x0003 3 <control>: END OF TEXT  
        latin[3] = '\u0003';
        latinEsc[3] = "%03";
        //            0x0004 4 <control>: END OF TRANSMISSION 
        latin[4] = '\u0004';
        latinEsc[4] = "%04";
        //            0x0005 5 <control>: ENQUIRY  
        latin[5] = '\u0005';
        latinEsc[5] = "%05";
        //            0x0006 6 <control>: ACKNOWLEDGE  
        latin[6] = '\u0006';
        latinEsc[6] = "%06";
        //            0x0007 7 <control>: BELL  
        latin[7] = '\u0007';
        latinEsc[7] = "%07";
        //            0x0008 8 <control>: BACKSPACE 
        latin[8] = '\u0008';
        latinEsc[8] = "%08";
        //            0x0009 9 <control>: HORIZONTAL TABULATION  
        latin[9] = '\u0009';
        latinEsc[9] = "%09";
        //            0x000A 10 <control>: LINE FEED  
        latin[10] = '\n';
        latinEsc[10] = "%0A";
        //            0x000B 11 <control>: VERTICAL TABULATION 
        latin[11] = '\u000B';
        latinEsc[11] = "%0B";
        //            0x000C 12 <control>: FORM FEED  
        latin[12] = '\u000C';
        latinEsc[12] = "%0C";
        //            0x000D 13 <control>: CARRIAGE RETURN 
        latin[13] = '\r';
        latinEsc[13] = "%0D";
        //            0x000E 14 <control>: SHIFT OUT 
        latin[14] = '\u000E';
        latinEsc[14] = "%0E";
        //            0x000F 15 <control>: SHIFT IN  
        latin[15] = '\u000F';
        latinEsc[15] = "%0F";
        //            0x0010 16 <control>: DATA LINK ESCAPE 
        latin[16] = '\u0010';
        latinEsc[16] = "%10";
        //            0x0011 17 <control>: DEVICE CONTROL ONE 
        latin[17] = '\u0011';
        latinEsc[17] = "%11";
        //            0x0012 18 <control>: DEVICE CONTROL TWO 
        latin[18] = '\u0012';
        latinEsc[18] = "%12";
        //            0x0013 19 <control>: DEVICE CONTROL THREE 
        latin[19] = '\u0013';
        latinEsc[19] = "%13";
        //            0x0014 20 <control>: DEVICE CONTROL FOUR 
        latin[20] = '\u0014';
        latinEsc[20] = "%14";
        //            0x0015 21 <control>: NEGATIVE ACKNOWLEDGE 
        latin[21] = '\u0015';
        latinEsc[21] = "%15";
        //            0x0016 22 <control>: SYNCHRONOUS IDLE 
        latin[22] = '\u0016';
        latinEsc[22] = "%16"; 
        //            0x0017 23 <control>: END OF TRANSMISSION BLOCK  
        latin[23] = '\u0017';
        latinEsc[23] = "%17";
        //            0x0018 24 <control>: CANCEL  
        latin[24] = '\u0018';
        latinEsc[24] = "%18";
        //            0x0019 25 <control>: END OF MEDIUM  
        latin[25] = '\u0019';
        latinEsc[25] = "%19";
        //            0x001A 26 <control>: SUBSTITUTE  
        latin[26] = '\u001A';
        latinEsc[26] = "%1A";
        //            0x001B 27 <control>: ESCAPE  
        latin[27] = '\u001B';
        latinEsc[27] = "%1B";
        //            0x001C 28 <control>: FILE SEPARATOR  
        latin[28] = '\u001C';
        latinEsc[28] = "%1C";
        //            0x001D 29 <control>: GROUP SEPARATOR  
        latin[29] = '\u001D';
        latinEsc[29] = "%1D";
        //            0x001E 30 <control>: RECORD SEPARATOR  
        latin[30] = '\u001E';
        latinEsc[30] = "%1E";
        //            0x001F 31 <control>: UNIT SEPARATOR  
        latin[31] = '\u001F';
        latinEsc[31] = "%1F";
        //            0x0020 32 SPACE  
        latin[32] = '\u0020';
        latinEsc[32] = "%20";
        //            0x0021 33 EXCLAMATION MARK ! 
        latin[33] = '\u0021';
        latinEsc[33] = "%21";
        //            0x0022 34 QUOTATION MARK " 
        latin[34] = '\u0022';
        latinEsc[34] = "%22";
        //            0x0023 35 NUMBER SIGN # 
        latin[35] = '\u0023';
        latinEsc[35] = "%23";
        //            0x0024 36 DOLLAR SIGN $ 
        latin[36] = '\u0024';
        latinEsc[36] = "%24";
        //            0x0025 37 PERCENT SIGN % 
        latin[37] = '\u0025';
        latinEsc[37] = "%25";
        //            0x0026 38 AMPERSAND & 
        latin[38] = '\u0026';
        latinEsc[38] = "%26";
        //            0x0027 39 APOSTROPHE ' 
        latin[39] = '\'';
        latinEsc[39] = "%27";
        //            0x0028 40 LEFT PARENTHESIS ( 
        latin[40] = '\u0028';
        latinEsc[40] = "%28";
        //            0x0029 41 RIGHT PARENTHESIS ) 
        latin[41] = '\u0029';
        latinEsc[41] = "%29";
        //            0x002A 42 ASTERISK * 
        latin[42] = '\u002A';
        latinEsc[42] = "%2A";
        //            0x002B 43 PLUS SIGN + 
        latin[43] = '\u002B';
        latinEsc[43] = "%2B";
        //            0x002C 44 COMMA , 
        latin[44] = '\u002C';
        latinEsc[44] = "%2C";
        //            0x002D 45 HYPHEN-MINUS - 
        latin[45] = '\u002D';
        latinEsc[0] = "%2D";
        //            0x002E 46 FULL STOP . 
        latin[46] = '\u002E';
        latinEsc[46] = "%2E";
        //            0x002F 47 SOLIDUS / 
        latin[47] = '\u002F';
        latinEsc[47] = "%2F";
        //            0x0030 48 DIGIT ZERO 0 
        latin[48] = '\u0030';
        latinEsc[48] = "%30";
        //            0x0031 49 DIGIT ONE 1 
        latin[49] = '\u0031';
        latinEsc[49] = "%31";
        //            0x0032 50 DIGIT TWO 2 
        latin[50] = '\u0032';
        latinEsc[50] = "%32";
        //            0x0033 51 DIGIT THREE 3 
        latin[51] = '\u0033';
        latinEsc[51] = "%33";
        //            0x0034 52 DIGIT FOUR 4 
        latin[52] = '\u0034';
        latinEsc[52] = "%34";
        //            0x0035 53 DIGIT FIVE 5 
        latin[53] = '\u0035';
        latinEsc[53] = "%35";
        //            0x0036 54 DIGIT SIX 6 
        latin[54] = '\u0036';
        latinEsc[54] = "%36";
        //            0x0037 55 DIGIT SEVEN 7 
        latin[55] = '\u0037';
        latinEsc[55] = "%37";
        //            0x0038 56 DIGIT EIGHT 8 
        latin[56] = '\u0038';
        latinEsc[56] = "%38";
        //            0x0039 57 DIGIT NINE 9 
        latin[57] = '\u0039';
        latinEsc[57] = "%39";
        //            0x003A 58 COLON : 
        latin[58] = '\u003A';
        latinEsc[58] = "%3A";
        //            0x003B 59 SEMICOLON ; 
        latin[59] = '\u003B';
        latinEsc[59] = "%3B";
        //            0x003C 60 LESS-THAN SIGN < 
        latin[60] = '\u003C';
        latinEsc[60] = "%3C";
        //            0x003D 61 EQUALS SIGN = 
        latin[61] = '\u003D';
        latinEsc[61] = "%3D";
        //            0x003E 62 GREATER-THAN SIGN > 
        latin[62] = '\u003E';
        latinEsc[62] = "%3E";
        //            0x003F 63 QUESTION MARK ? 
        latin[63] = '\u003F';
        latinEsc[63] = "%3F";
        //            0x0040 64 COMMERCIAL AT @ 
        latin[64] = '\u0040';
        latinEsc[64] = "%40";
        //            0x0041 65 LATIN CAPITAL LETTER A A 
        latin[65] = '\u0041';
        latinEsc[65] = "%41";
        //            0x0042 66 LATIN CAPITAL LETTER B B 
        latin[66] = '\u0042';
        latinEsc[66] = "%42";
        //            0x0043 67 LATIN CAPITAL LETTER C C 
        latin[67] = '\u0043';
        latinEsc[67] = "%43";
        //            0x0044 68 LATIN CAPITAL LETTER D D 
        latin[68] = '\u0044';
        latinEsc[68] = "%44";
        //            0x0045 69 LATIN CAPITAL LETTER E E 
        latin[69] = '\u0045';
        latinEsc[69] = "%45";
        //            0x0046 70 LATIN CAPITAL LETTER F F 
        latin[70] = '\u0046';
        latinEsc[70] = "%46";
        //            0x0047 71 LATIN CAPITAL LETTER G G 
        latin[71] = '\u0047';
        latinEsc[71] = "%47";
        //            0x0048 72 LATIN CAPITAL LETTER H H 
        latin[72] = '\u0048';
        latinEsc[72] = "%48";
        //            0x0049 73 LATIN CAPITAL LETTER I I 
        latin[73] = '\u0049';
        latinEsc[73] = "%49";
        //            0x004A 74 LATIN CAPITAL LETTER J J 
        latin[74] = '\u004A';
        latinEsc[74] = "%4A";
        //            0x004B 75 LATIN CAPITAL LETTER K K 
        latin[75] = '\u004B';
        latinEsc[75] = "%4B";
        //            0x004C 76 LATIN CAPITAL LETTER L L 
        latin[76] = '\u004C';
        latinEsc[76] = "%4C";
        //            0x004D 77 LATIN CAPITAL LETTER M M 
        latin[77] = '\u004D';
        latinEsc[77] = "%4D";
        //            0x004E 78 LATIN CAPITAL LETTER N N 
        latin[78] = '\u004E';
        latinEsc[78] = "%4E";
        //            0x004F 79 LATIN CAPITAL LETTER O O 
        latin[79] = '\u004F';
        latinEsc[79] = "%4F";
        //            0x0050 80 LATIN CAPITAL LETTER P P 
        latin[80] = '\u0050';
        latinEsc[80] = "%50";
        //            0x0051 81 LATIN CAPITAL LETTER Q Q 
        latin[81] = '\u0051';
        latinEsc[81] = "%51";
        //            0x0052 82 LATIN CAPITAL LETTER R R 
        latin[82] = '\u0052';
        latinEsc[82] = "%52";
        //            0x0053 83 LATIN CAPITAL LETTER S S 
        latin[83] = '\u0053';
        latinEsc[83] = "%53";
        //            0x0054 84 LATIN CAPITAL LETTER T T 
        latin[84] = '\u0054';
        latinEsc[84] = "%54";
        //            0x0055 85 LATIN CAPITAL LETTER U U 
        latin[85] = '\u0055';
        latinEsc[85] = "%55";
        //            0x0056 86 LATIN CAPITAL LETTER V V 
        latin[86] = '\u0056';
        latinEsc[86] = "%56";
        //            0x0057 87 LATIN CAPITAL LETTER W W 
        latin[87] = '\u0057';
        latinEsc[87] = "%57";
        //            0x0058 88 LATIN CAPITAL LETTER X X 
        latin[88] = '\u0058';
        latinEsc[88] = "%58";
        //            0x0059 89 LATIN CAPITAL LETTER Y Y 
        latin[89] = '\u0059';
        latinEsc[89] = "%59";
        //            0x005A 90 LATIN CAPITAL LETTER Z Z 
        latin[90] = '\u005A';
        latinEsc[90] = "%5A";
        //            0x005B 91 LEFT SQUARE BRACKET [ 
        latin[91] = '\u005B';
        latinEsc[91] = "%5B";
        //            0x005C 92 REVERSE SOLIDUS \ 
        latin[92] = '\\';
        latinEsc[92] = "%5C";
        //            0x005D 93 RIGHT SQUARE BRACKET ] 
        latin[93] = '\u005D';
        latinEsc[93] = "%5D";
        //            0x005E 94 CIRCUMFLEX ACCENT ^ 
        latin[94] = '\u005E';
        latinEsc[94] = "%5E";
        //            0x005F 95 LOW LINE _ 
        latin[95] = '\u005F';
        latinEsc[95] = "%5F";
        //            0x0060 96 GRAVE ACCENT ` 
        latin[96] = '\u0060';
        latinEsc[96] = "%60";
        //            0x0061 97 LATIN SMALL LETTER A a 
        latin[97] = '\u0061';
        latinEsc[97] = "%61";
        //            0x0062 98 LATIN SMALL LETTER B b 
        latin[98] = '\u0062';
        latinEsc[98] = "%62";
        //            0x0063 99 LATIN SMALL LETTER C c 
        latin[99] = '\u0063';
        latinEsc[99] = "%63";
        //            0x0064 100 LATIN SMALL LETTER D d 
        latin[100] = '\u0064';
        latinEsc[100] = "%64";
        //            0x0065 101 LATIN SMALL LETTER E e 
        latin[101] = '\u0065';
        latinEsc[101] = "%65";
        //            0x0066 102 LATIN SMALL LETTER F f 
        latin[102] = '\u0066';
        latinEsc[102] = "%66";
        //            0x0067 103 LATIN SMALL LETTER G g 
        latin[103] = '\u0067';
        latinEsc[103] = "%67";
        //            0x0068 104 LATIN SMALL LETTER H h 
        latin[104] = '\u0068';
        latinEsc[104] = "%68";
        //            0x0069 105 LATIN SMALL LETTER I i 
        latin[105] = '\u0069';
        latinEsc[105] = "%69";
        //            0x006A 106 LATIN SMALL LETTER J j 
        latin[106] = '\u006A';
        latinEsc[106] = "%6A";
        //            0x006B 107 LATIN SMALL LETTER K k 
        latin[107] = '\u006B';
        latinEsc[107] = "%6B";
        //            0x006C 108 LATIN SMALL LETTER L l 
        latin[108] = '\u006C';
        latinEsc[108] = "%6C";
        //            0x006D 109 LATIN SMALL LETTER M m 
        latin[109] = '\u006D';
        latinEsc[109] = "%6D";
        //            0x006E 110 LATIN SMALL LETTER N n 
        latin[110] = '\u006E';
        latinEsc[110] = "%6E";
        //            0x006F 111 LATIN SMALL LETTER O o 
        latin[111] = '\u006F';
        latinEsc[111] = "%6F";
        //            0x0070 112 LATIN SMALL LETTER P p 
        latin[112] = '\u0070';
        latinEsc[112] = "%70";
        //            0x0071 113 LATIN SMALL LETTER Q q 
        latin[113] = '\u0071';
        latinEsc[113] = "%71";
        //            0x0072 114 LATIN SMALL LETTER R r 
        latin[114] = '\u0072';
        latinEsc[114] = "%72";
        //            0x0073 115 LATIN SMALL LETTER S s 
        latin[115] = '\u0073';
        latinEsc[115] = "%73";
        //            0x0074 116 LATIN SMALL LETTER T t 
        latin[116] = '\u0074';
        latinEsc[116] = "%74";
        //            0x0075 117 LATIN SMALL LETTER U u 
        latin[117] = '\u0075';
        latinEsc[117] = "%75";
        //            0x0076 118 LATIN SMALL LETTER V v 
        latin[118] = '\u0076';
        latinEsc[118] = "%76";
        //            0x0077 119 LATIN SMALL LETTER W w 
        latin[119] = '\u0077';
        latinEsc[119] = "%77";
        //            0x0078 120 LATIN SMALL LETTER X x 
        latin[120] = '\u0078';
        latinEsc[120] = "%78";
        //            0x0079 121 LATIN SMALL LETTER Y y 
        latin[121] = '\u0079';
        latinEsc[121] = "%79";
        //            0x007A 122 LATIN SMALL LETTER Z z 
        latin[122] = '\u007A';
        latinEsc[122] = "%7A";
        //            0x007B 123 LEFT CURLY BRACKET { 
        latin[123] = '\u007B';
        latinEsc[123] = "%7B";
        //            0x007C 124 VERTICAL LINE | 
        latin[124] = '\u007C';
        latinEsc[124] = "%7C";
        //            0x007D 125 RIGHT CURLY BRACKET } 
        latin[125] = '\u007D';
        latinEsc[125] = "%7D";
        //            0x007E 126 TILDE ~ 
        latin[126] = '\u007E';
        latinEsc[126] = "%7E";
        //            0x007F 127 <control>: DELETE  
        latin[127] = '\u007F';
        latinEsc[127] = "%7F";
        
        //            Latin-1 Supplement 
        //            Position Decimal Name Appearance 
        //            0x0080 128 <control>:  
        latin[128] = '\u0080';
        latinEsc[128] = "%80";
        //            0x0081 129 <control>:  ? 
        latin[129] = '\u0081';
        latinEsc[129] = "%81";
        //            0x0082 130 <control>: BREAK PERMITTED HERE 
        latin[130] = '\u0082';
        latinEsc[130] = "%82";
        //            0x0083 131 <control>: NO BREAK HERE 
        latin[131] = '\u0083';
        latinEsc[131] = "%83";
        //            0x0084 132 <control>:  
        latin[132] = '\u0084';
        latinEsc[132] = "%84";
        //            0x0085 133 <control>: NEXT LINE 
        latin[133] = '\u0085';
        latinEsc[133] = "%85";
        //            0x0086 134 <control>: START OF SELECTED AREA 
        latin[134] = '\u0086';
        latinEsc[134] = "%86";
        //            0x0087 135 <control>: END OF SELECTED AREA 
        latin[135] = '\u0087';
        latinEsc[135] = "%87";
        //            0x0088 136 <control>: CHARACTER TABULATION SET 
        latin[136] = '\u0088';
        latinEsc[136] = "%88";
        //            0x0089 137 <control>: CHARACTER TABULATION WITH JUSTIFICATION 
        latin[137] = '\u0089';
        latinEsc[137] = "%89";
        //            0x008A 138 <control>: LINE TABULATION SET 
        latin[138] = '\u008A';
        latinEsc[138] = "%8A";
        //            0x008B 139 <control>: PARTIAL LINE DOWN 
        latin[139] = '\u008B';
        latinEsc[139] = "%8B";
        //            0x008C 140 <control>: PARTIAL LINE UP 
        latin[140] = '\u008C';
        latinEsc[140] = "%8C";
        //            0x008D 141 <control>: REVERSE LINE FEED 
        latin[141] = '\u008D';
        latinEsc[141] = "%8D";
        //            0x008E 142 <control>: SINGLE SHIFT TWO 
        latin[142] = '\u008E';
        latinEsc[142] = "%8E";
        //            0x008F 143 <control>: SINGLE SHIFT THREE 
        latin[143] = '\u008F';
        latinEsc[143] = "%8F";
        //            0x0090 144 <control>: DEVICE CONTROL STRING 
        latin[144] = '\u0090';
        latinEsc[144] = "%90";
        //            0x0091 145 <control>: PRIVATE USE ONE 
        latin[145] = '\u0091';
        latinEsc[145] = "%91";
        //            0x0092 146 <control>: PRIVATE USE TWO 
        latin[146] = '\u0092';
        latinEsc[146] = "%92";
        //            0x0093 147 <control>: SET TRANSMIT STATE 
        latin[147] = '\u0093';
        latinEsc[147] = "%93";
        //            0x0094 148 <control>: CANCEL CHARACTER 
        latin[148] = '\u0094';
        latinEsc[148] = "%94";
        //            0x0095 149 <control>: MESSAGE WAITING 
        latin[149] = '\u0095';
        latinEsc[149] = "%95";
        //            0x0096 150 <control>: START OF GUARDED AREA 
        latin[150] = '\u0096';
        latinEsc[150] = "%96";
        //            0x0097 151 <control>: END OF GUARDED AREA 
        latin[151] = '\u0097';
        latinEsc[151] = "%97";
        //            0x0098 152 <control>: START OF STRING 
        latin[152] = '\u0098';
        latinEsc[152] = "%98";
        //            0x0099 153 <control>:  
        latin[153] = '\u0099';
        latinEsc[153] = "%99";
        //            0x009A 154 <control>: SINGLE CHARACTER INTRODUCER 
        latin[154] = '\u009A';
        latinEsc[154] = "%9A";
        //            0x009B 155 <control>: CONTROL SEQUENCE INTRODUCER 
        latin[155] = '\u009B';
        latinEsc[155] = "%9B";
        //            0x009C 156 <control>: STRING TERMINATOR 
        latin[156] = '\u009C';
        latinEsc[156] = "%9C";
        //            0x009D 157 <control>: OPERATING SYSTEM COMMAND 
        latin[157] = '\u009D';
        latinEsc[157] = "%9D";
        //            0x009E 158 <control>: PRIVACY MESSAGE 
        latin[158] = '\u009E';
        latinEsc[158] = "%9E";
        //            0x009F 159 <control>: APPLICATION PROGRAM COMMAND 
        latin[159] = '\u009F';
        latinEsc[159] = "%9F";
        //            0x00A0 160 NO-BREAK SPACE   
        latin[160] = '\u00A0';
        latinEsc[160] = "%A0";
        //            0x00A1 161 INVERTED EXCLAMATION MARK 
        latin[161] = '\u00A1';
        latinEsc[161] = "%A1";
        //            0x00A2 162 CENT SIGN 
        latin[162] = '\u00A2';
        latinEsc[162] = "%A2";
        //            0x00A3 163 POUND SIGN 
        latin[163] = '\u00A3';
        latinEsc[163] = "%A3";
        //            0x00A4 164 CURRENCY SIGN 
        latin[164] = '\u00A4';
        latinEsc[164] = "%A4";
        //            0x00A5 165 YEN SIGN 
        latin[165] = '\u00A5';
        latinEsc[165] = "%A5";
        //            0x00A6 166 BROKEN BAR 
        latin[166] = '\u00A6';
        latinEsc[166] = "%A6";
        //            0x00A7 167 SECTION SIGN 
        latin[167] = '\u00A7';
        latinEsc[167] = "%A7";
        //            0x00A8 168 DIAERESIS 
        latin[168] = '\u00A8';
        latinEsc[168] = "%A8";
        //            0x00A9 169 COPYRIGHT SIGN 
        latin[169] = '\u00A9';
        latinEsc[169] = "%A9";
        //            0x00AA 170 FEMININE ORDINAL INDICATOR 
        latin[170] = '\u00AA';
        latinEsc[170] = "%AA";
        //            0x00AB 171 LEFT-POINTING DOUBLE ANGLE QUOTATION MARK 
        latin[171] = '\u00AB';
        latinEsc[171] = "%AB";
        //            0x00AC 172 NOT SIGN 
        latin[172] = '\u00AC';
        latinEsc[172] = "%AC";
        //            0x00AD 173 SOFT HYPHEN 
        latin[173] = '\u00AD';
        latinEsc[173] = "%AD";
        //            0x00AE 174 REGISTERED SIGN 
        latin[174] = '\u00AE';
        latinEsc[174] = "%AE";
        //            0x00AF 175 MACRON 
        latin[175] = '\u00AF';
        latinEsc[175] = "%AF";
        //            0x00B0 176 DEGREE SIGN 
        latin[176] = '\u00B0';
        latinEsc[176] = "%B0";
        //            0x00B1 177 PLUS-MINUS SIGN 
        latin[177] = '\u00B1';
        latinEsc[177] = "%B1";
        //            0x00B2 178 SUPERSCRIPT TWO 
        latin[178] = '\u00B2';
        latinEsc[178] = "%B2";
        //            0x00B3 179 SUPERSCRIPT THREE 
        latin[179] = '\u00B3';
        latinEsc[179] = "%B3";
        //            0x00B4 180 ACUTE ACCENT 
        latin[180] = '\u00B4';
        latinEsc[180] = "%B4";
        //            0x00B5 181 MICRO SIGN 
        latin[181] = '\u00B5';
        latinEsc[181] = "%B5";
        //            0x00B6 182 PILCROW SIGN 
        latin[182] = '\u00B6';
        latinEsc[182] = "%B6";
        //            0x00B7 183 MIDDLE DOT 
        latin[183] = '\u00B7';
        latinEsc[183] = "%B7";
        //            0x00B8 184 CEDILLA 
        latin[184] = '\u00B8';
        latinEsc[184] = "%B8";
        //            0x00B9 185 SUPERSCRIPT ONE 
        latin[185] = '\u00B9';
        latinEsc[185] = "%B9";
        //            0x00BA 186 MASCULINE ORDINAL INDICATOR 
        latin[186] = '\u00BA';
        latinEsc[186] = "%BA";
        //            0x00BB 187 RIGHT-POINTING DOUBLE ANGLE QUOTATION MARK 
        latin[187] = '\u00BB';
        latinEsc[187] = "%BB";
        //            0x00BC 188 VULGAR FRACTION ONE QUARTER 
        latin[188] = '\u00BC';
        latinEsc[188] = "%BC";
        //            0x00BD 189 VULGAR FRACTION ONE HALF 
        latin[189] = '\u00BD';
        latinEsc[189] = "%BD";
        //            0x00BE 190 VULGAR FRACTION THREE QUARTERS 
        latin[190] = '\u00BE';
        latinEsc[190] = "%BE";
        //            0x00BF 191 INVERTED QUESTION MARK 
        latin[191] = '\u00BF';
        latinEsc[191] = "%BF";
        //            0x00C0 192 LATIN CAPITAL LETTER A WITH GRAVE 
        latin[192] = '\u00C0';
        latinEsc[192] = "%C0";
        //            0x00C1 193 LATIN CAPITAL LETTER A WITH ACUTE 
        latin[193] = '\u00C1';
        latinEsc[193] = "%C1";
        //            0x00C2 194 LATIN CAPITAL LETTER A WITH CIRCUMFLEX 
        latin[194] = '\u00C2';
        latinEsc[194] = "%C2";
        //            0x00C3 195 LATIN CAPITAL LETTER A WITH TILDE 
        latin[195] = '\u00C3';
        latinEsc[195] = "%C3";
        //            0x00C4 196 LATIN CAPITAL LETTER A WITH DIAERESIS 
        latin[196] = '\u00C4';
        latinEsc[196] = "%C4";
        //            0x00C5 197 LATIN CAPITAL LETTER A WITH RING ABOVE 
        latin[197] = '\u00C5';
        latinEsc[197] = "%C5";
        //            0x00C6 198 LATIN CAPITAL LETTER AE 
        latin[198] = '\u00C6';
        latinEsc[198] = "%C6";
        //            0x00C7 199 LATIN CAPITAL LETTER C WITH CEDILLA 
        latin[199] = '\u00C7';
        latinEsc[199] = "%C7";
        //            0x00C8 200 LATIN CAPITAL LETTER E WITH GRAVE 
        latin[200] = '\u00C8';
        latinEsc[200] = "%C8";
        //            0x00C9 201 LATIN CAPITAL LETTER E WITH ACUTE 
        latin[201] = '\u00C9';
        latinEsc[201] = "%C9";
        //            0x00CA 202 LATIN CAPITAL LETTER E WITH CIRCUMFLEX 
        latin[202] = '\u00CA';
        latinEsc[202] = "%CA";
        //            0x00CB 203 LATIN CAPITAL LETTER E WITH DIAERESIS 
        latin[203] = '\u00CB';
        latinEsc[203] = "%CB";
        //            0x00CC 204 LATIN CAPITAL LETTER I WITH GRAVE 
        latin[204] = '\u00CC';
        latinEsc[204] = "%CC";
        //            0x00CD 205 LATIN CAPITAL LETTER I WITH ACUTE 
        latin[205] = '\u00CD';
        latinEsc[205] = "%CD";
        //            0x00CE 206 LATIN CAPITAL LETTER I WITH CIRCUMFLEX 
        latin[206] = '\u00CE';
        latinEsc[206] = "%CE";
        //            0x00CF 207 LATIN CAPITAL LETTER I WITH DIAERESIS 
        latin[207] = '\u00CF';
        latinEsc[207] = "%CF";
        //            0x00D0 208 LATIN CAPITAL LETTER ETH 
        latin[208] = '\u00D0';
        latinEsc[208] = "%D0";
        //            0x00D1 209 LATIN CAPITAL LETTER N WITH TILDE 
        latin[209] = '\u00D1';
        latinEsc[209] = "%D1";
        //            0x00D2 210 LATIN CAPITAL LETTER O WITH GRAVE 
        latin[210] = '\u00D2';
        latinEsc[210] = "%D2";
        //            0x00D3 211 LATIN CAPITAL LETTER O WITH ACUTE 
        latin[211] = '\u00D3';
        latinEsc[211] = "%D3";
        //            0x00D4 212 LATIN CAPITAL LETTER O WITH CIRCUMFLEX 
        latin[212] = '\u00D4';
        latinEsc[212] = "%D4";
        //            0x00D5 213 LATIN CAPITAL LETTER O WITH TILDE 
        latin[213] = '\u00D5';
        latinEsc[213] = "%D5";
        //            0x00D6 214 LATIN CAPITAL LETTER O WITH DIAERESIS 
        latin[214] = '\u00D6';
        latinEsc[214] = "%D6";
        //            0x00D7 215 MULTIPLICATION SIGN 
        latin[215] = '\u00D7';
        latinEsc[215] = "%D7";
        //            0x00D8 216 LATIN CAPITAL LETTER O WITH STROKE 
        latin[216] = '\u00D8';
        latinEsc[216] = "%D8";
        //            0x00D9 217 LATIN CAPITAL LETTER U WITH GRAVE 
        latin[217] = '\u00D9';
        latinEsc[217] = "%D9";
        //            0x00DA 218 LATIN CAPITAL LETTER U WITH ACUTE 
        latin[218] = '\u00DA';
        latinEsc[218] = "%DA";
        //            0x00DB 219 LATIN CAPITAL LETTER U WITH CIRCUMFLEX 
        latin[219] = '\u00DB';
        latinEsc[219] = "%DB";
        //            0x00DC 220 LATIN CAPITAL LETTER U WITH DIAERESIS 
        latin[220] = '\u00DC';
        latinEsc[220] = "%DC";
        //            0x00DD 221 LATIN CAPITAL LETTER Y WITH ACUTE 
        latin[221] = '\u00DD';
        latinEsc[221] = "%DD";
        //            0x00DE 222 LATIN CAPITAL LETTER THORN 
        latin[222] = '\u00DE';
        latinEsc[222] = "%DE";
        //            0x00DF 223 LATIN SMALL LETTER SHARP S 
        latin[223] = '\u00DF';
        latinEsc[223] = "%DF";
        //            0x00E0 224 LATIN SMALL LETTER A WITH GRAVE 
        latin[224] = '\u00E0';
        latinEsc[224] = "%E0";
        //            0x00E1 225 LATIN SMALL LETTER A WITH ACUTE 
        latin[225] = '\u00E1';
        latinEsc[225] = "%E1";
        //            0x00E2 226 LATIN SMALL LETTER A WITH CIRCUMFLEX 
        latin[226] = '\u00E2';
        latinEsc[226] = "%E2";
        //            0x00E3 227 LATIN SMALL LETTER A WITH TILDE 
        latin[227] = '\u00E3';
        latinEsc[227] = "%E3";
        //            0x00E4 228 LATIN SMALL LETTER A WITH DIAERESIS 
        latin[228] = '\u00E4';
        latinEsc[228] = "%E4";
        //            0x00E5 229 LATIN SMALL LETTER A WITH RING ABOVE 
        latin[229] = '\u00E5';
        latinEsc[229] = "%E5";
        //            0x00E6 230 LATIN SMALL LETTER AE 
        latin[230] = '\u00E6';
        latinEsc[230] = "%E6";
        //            0x00E7 231 LATIN SMALL LETTER C WITH CEDILLA 
        latin[231] = '\u00E7';
        latinEsc[231] = "%E7";
        //            0x00E8 232 LATIN SMALL LETTER E WITH GRAVE 
        latin[232] = '\u00E8';
        latinEsc[232] = "%E8";
        //            0x00E9 233 LATIN SMALL LETTER E WITH ACUTE 
        latin[233] = '\u00E9';
        latinEsc[233] = "%E9";
        //            0x00EA 234 LATIN SMALL LETTER E WITH CIRCUMFLEX 
        latin[234] = '\u00EA';
        latinEsc[234] = "%EA";
        //            0x00EB 235 LATIN SMALL LETTER E WITH DIAERESIS 
        latin[235] = '\u00EB';
        latinEsc[235] = "%EB";
        //            0x00EC 236 LATIN SMALL LETTER I WITH GRAVE 
        latin[236] = '\u00EC';
        latinEsc[236] = "%EC";
        //            0x00ED 237 LATIN SMALL LETTER I WITH ACUTE 
        latin[237] = '\u00ED';
        latinEsc[237] = "%ED";
        //            0x00EE 238 LATIN SMALL LETTER I WITH CIRCUMFLEX 
        latin[238] = '\u00EE';
        latinEsc[238] = "%EE";
        //            0x00EF 239 LATIN SMALL LETTER I WITH DIAERESIS 
        latin[239] = '\u00EF';
        latinEsc[239] = "%EF";
        //            0x00F0 240 LATIN SMALL LETTER ETH 
        latin[240] = '\u00F0';
        latinEsc[240] = "%F0";
        //            0x00F1 241 LATIN SMALL LETTER N WITH TILDE 
        latin[241] = '\u00F1';
        latinEsc[241] = "%F1";
        //            0x00F2 242 LATIN SMALL LETTER O WITH GRAVE 
        latin[242] = '\u00F2';
        latinEsc[242] = "%F2";
        //            0x00F3 243 LATIN SMALL LETTER O WITH ACUTE 
        latin[243] = '\u00F3';
        latinEsc[243] = "%F3";
        //            0x00F4 244 LATIN SMALL LETTER O WITH CIRCUMFLEX 
        latin[244] = '\u00F4';
        latinEsc[244] = "%F4";
        //            0x00F5 245 LATIN SMALL LETTER O WITH TILDE 
        latin[245] = '\u00F5';
        latinEsc[245] = "%F5";
        //            0x00F6 246 LATIN SMALL LETTER O WITH DIAERESIS 
        latin[246] = '\u00F6';
        latinEsc[246] = "%F6";
        //            0x00F7 247 DIVISION SIGN 
        latin[247] = '\u00F7';
        latinEsc[247] = "%F7";
        //            0x00F8 248 LATIN SMALL LETTER O WITH STROKE 
        latin[248] = '\u00F8';
        latinEsc[248] = "%F8";
        //            0x00F9 249 LATIN SMALL LETTER U WITH GRAVE 
        latin[249] = '\u00F9';
        latinEsc[249] = "%F9";
        //            0x00FA 250 LATIN SMALL LETTER U WITH ACUTE 
        latin[250] = '\u00FA';
        latinEsc[250] = "%FA";
        //            0x00FB 251 LATIN SMALL LETTER U WITH CIRCUMFLEX 
        latin[251] = '\u00FB';
        latinEsc[251] = "%FB";
        //            0x00FC 252 LATIN SMALL LETTER U WITH DIAERESIS 
        latin[252] = '\u00FC';
        latinEsc[252] = "%FC";
        //            0x00FD 253 LATIN SMALL LETTER Y WITH ACUTE 
        latin[253] = '\u00FD';
        latinEsc[253] = "%FD";
        //            0x00FE 254 LATIN SMALL LETTER THORN 
        latin[254] = '\u00FE';
        latinEsc[254] = "%FE";
        //            0x00FF 255 LATIN SMALL LETTER Y WITH DIAERESIS 
        latin[255] = '\u00FF';
        latinEsc[255] = "%FF";
    }
    
    String string;
    String scheme;
    String schemespecificpart;
    String authority;
    String userinfo;
    String host;
    int port = -1;
    String path;
    String query;
    String fragment;
    boolean opaque;
    boolean absolute;
    boolean serverAuthority = false;
    int hash = -1;

    void parseURI(String uri, boolean forceServer) throws URISyntaxException {
        char fSlash = '/';
        StringBuilder temp = new StringBuilder(uri);
        // assign uri string to the input value per spec
        string = uri;
        int index;
        int index1;
        int index2;
        int index3;
        // parse into Fragment, Scheme, and SchemeSpecificPart
        // then parse SchemeSpecificPart if necessary
        // Fragment
        index = temp.indexOf("#");
        if (index != -1) {
            // remove the fragment from the end
            fragment = temp.substring(index + 1);
            validateFragment(uri, fragment, index + 1);
            //                temp = temp.substring(0, index);
            temp.delete(index, temp.length());
        }
        // Scheme and SchemeSpecificPart
        index = index1 = temp.indexOf(":");
        index2 = temp.indexOf("/");
        index3 = temp.indexOf("?");
        // if a '/' or '?' occurs before the first ':' the uri has no
        // specified scheme, and is therefore not absolute
        if (index != -1 && (index2 >= index || index2 == -1) && (index3 >= index || index3 == -1)) {
            // the characters up to the first ':' comprise the scheme
            absolute = true;
            scheme = temp.substring(0, index);
            if (scheme.length() == 0) {
                throw new URISyntaxException(uri, Messages.getString("luni.83"), index);
            }
            validateScheme(uri, scheme, 0);
            schemespecificpart = temp.substring(index + 1);
            if (schemespecificpart.length() == 0) {
                throw new URISyntaxException(uri, Messages.getString("luni.84"), index + 1);
            }
        } else {
            absolute = false;
            schemespecificpart = temp.toString();
        }
        if (scheme == null || schemespecificpart.length() > 0 && schemespecificpart.charAt(0) == fSlash) {
            opaque = false;
            // the URI is hierarchical
            // Query
            temp.delete(0, temp.length());
            temp.append(schemespecificpart);
            index = temp.indexOf("?");
            if (index != -1) {
                query = temp.substring(index + 1);
                temp.delete(index, temp.length());
                validateQuery(uri, query, index2 + 1 + index);
            }
            // Authority and Path
            if (temp.length() >= 2 && temp.charAt(0) == fSlash && temp.charAt(1) == fSlash) {
                //$NON-NLS-1$
                index = temp.indexOf("/", 2);
                if (index != -1) {
                    authority = temp.substring(2, index);
                    path = temp.substring(index);
                } else {
                    authority = temp.substring(2);
                    if (authority.length() == 0 && query == null && fragment == null) {
                        throw new URISyntaxException(uri, Messages.getString("luni.9F"), uri.length()); //$NON-NLS-1$
                    }
                    path = ""; //$NON-NLS-1$
                    // nothing left, so path is empty (not null, path should
                    // never be null)
                }
                if (authority.length() == 0) {
                    authority = null;
                } else {
                    validateAuthority(uri, authority, index1 + 3);
                }
            } else {
                // no authority specified
                path = temp.toString();
            }
            int pathIndex = 0;
            if (index2 > -1) {
                pathIndex += index2;
            }
            if (index > -1) {
                pathIndex += index;
            }
            validatePath(uri, path, pathIndex);
        } else {
            // if not hierarchical, URI is opaque
            opaque = true;
            validateSsp(uri, schemespecificpart, index2 + 2 + index);
        }
        parseAuthority(forceServer);
    }

    private void validateScheme(String uri, String scheme, int index) throws URISyntaxException {
        // first char needs to be an alpha char
        char ch = scheme.charAt(0);
        if (!((ch >= 'a' && ch <= 'z') || (ch >= 'A' && ch <= 'Z'))) {
            throw new URISyntaxException(uri, Messages.getString("luni.85"), 0); //$NON-NLS-1$
        }
        try {
            URIEncoderDecoder.validateSimple(scheme, "+-."); //$NON-NLS-1$
        } catch (URISyntaxException e) {
            throw new URISyntaxException(uri, Messages.getString("luni.85"), index + e.getIndex());
        }
    }

    private void validateSsp(String uri, String ssp, int index) throws URISyntaxException {
        try {
            URIEncoderDecoder.validate(ssp, Uri.allLegal);
        } catch (URISyntaxException e) {
            throw new URISyntaxException(uri, Messages.getString("luni.86", e.getReason()), index + e.getIndex());
        }
    }

    private void validateAuthority(String uri, String authority, int index) throws URISyntaxException {
        try {
            URIEncoderDecoder.validate(authority, "@[]" + Uri.someLegal); //$NON-NLS-1$
        } catch (URISyntaxException e) {
            throw new URISyntaxException(uri, Messages.getString("luni.87", e.getReason()), index + e.getIndex());
        }
    }

    private void validatePath(String uri, String path, int index) throws URISyntaxException {
        try {
            URIEncoderDecoder.validate(path, "/@" + Uri.someLegal); //$NON-NLS-1$
        } catch (URISyntaxException e) {
            throw new URISyntaxException(uri, Messages.getString("luni.88", e.getReason()), index + e.getIndex());
        }
    }

    private void validateQuery(String uri, String query, int index) throws URISyntaxException {
        try {
            URIEncoderDecoder.validate(query, Uri.queryLegal);
        } catch (URISyntaxException e) {
            throw new URISyntaxException(uri, Messages.getString("luni.89", e.getReason()), index + e.getIndex());
        }
    }

    private void validateFragment(String uri, String fragment, int index) throws URISyntaxException {
        try {
            URIEncoderDecoder.validate(fragment, Uri.allLegal);
        } catch (URISyntaxException e) {
            throw new URISyntaxException(uri, Messages.getString("luni.8A", e.getReason()), index + e.getIndex());
        }
    }

    /**
     * determine the host, port and userinfo if the authority parses
     * successfully to a server based authority
     *
     * behaviour in error cases: if forceServer is true, throw
     * URISyntaxException with the proper diagnostic messages. if
     * forceServer is false assume this is a registry based uri, and just
     * return leaving the host, port and userinfo fields undefined.
     *
     * and there are some error cases where URISyntaxException is thrown
     * regardless of the forceServer parameter e.g. malformed ipv6 address
     */
    void parseAuthority(boolean forceServer) throws URISyntaxException {
        if (authority == null) {
            return;
        }
        String temp;
        String tempUserinfo = null;
        String tempHost = null;
        int index;
        int hostindex = 0;
        int tempPort = -1;
        temp = authority;
        index = temp.indexOf('@');
        if (index != -1) {
            // remove user info
            tempUserinfo = temp.substring(0, index);
            validateUserinfo(authority, tempUserinfo, 0);
            temp = temp.substring(index + 1); // host[:port] is left
            hostindex = index + 1;
        }
        index = temp.lastIndexOf(':');
        int endindex = temp.indexOf(']');
        if (index != -1 && endindex < index) {
            // determine port and host
            tempHost = temp.substring(0, index);
            if (index < (temp.length() - 1)) {
                // port part is not empty
                try {
                    tempPort = Integer.parseInt(temp.substring(index + 1));
                    if (tempPort < 0) {
                        if (forceServer) {
                            throw new URISyntaxException(authority, Messages.getString("luni.8B"), hostindex + index + 1); //$NON-NLS-1$
                        }
                        return;
                    }
                } catch (NumberFormatException e) {
                    if (forceServer) {
                        throw new URISyntaxException(authority, Messages.getString("luni.8B"), hostindex + index + 1); //$NON-NLS-1$
                    }
                    return;
                }
            }
        } else {
            tempHost = temp;
        }
        if (tempHost.equals("")) {
            //$NON-NLS-1$
            if (forceServer) {
                throw new URISyntaxException(authority, Messages.getString("luni.A0"), hostindex); //$NON-NLS-1$
            }
            return;
        }
        if (!isValidHost(forceServer, tempHost)) {
            return;
        }
        // this is a server based uri,
        // fill in the userinfo, host and port fields
        userinfo = tempUserinfo;
        host = tempHost;
        port = tempPort;
        serverAuthority = true;
    }

    private void validateUserinfo(String uri, String userinfo, int index) throws URISyntaxException {
        for (int i = 0; i < userinfo.length(); i++) {
            char ch = userinfo.charAt(i);
            if (ch == ']' || ch == '[') {
                throw new URISyntaxException(uri, Messages.getString("luni.8C"), index + i);
            }
        }
    }

    /**
     * distinguish between IPv4, IPv6, domain name and validate it based on
     * its type
     */
    private boolean isValidHost(boolean forceServer, String host) throws URISyntaxException {
        if (host.charAt(0) == '[') {
            // ipv6 address
            if (host.charAt(host.length() - 1) != ']') {
                throw new URISyntaxException(host, Messages.getString("luni.8D"), 0); //$NON-NLS-1$
            }
            if (!isValidIP6Address(host)) {
                throw new URISyntaxException(host, Messages.getString("luni.8E")); //$NON-NLS-1$
            }
            return true;
        }
        // '[' and ']' can only be the first char and last char
        // of the host name
        if (host.indexOf('[') != -1 || host.indexOf(']') != -1) {
            throw new URISyntaxException(host, Messages.getString("luni.8F"), 0); //$NON-NLS-1$
        }
        int index = host.lastIndexOf('.');
        if (index < 0 || index == host.length() - 1 || !Character.isDigit(host.charAt(index + 1))) {
            // domain name
            if (isValidDomainName(host)) {
                return true;
            }
            if (forceServer) {
                throw new URISyntaxException(host, Messages.getString("luni.8F"), 0); //$NON-NLS-1$
            }
            return false;
        }
        // IPv4 address
        if (isValidIPv4Address(host)) {
            return true;
        }
        if (forceServer) {
            throw new URISyntaxException(host, Messages.getString("luni.90"), 0); //$NON-NLS-1$
        }
        return false;
    }

    private boolean isValidDomainName(String host) {
        try {
            URIEncoderDecoder.validateSimple(host, "-."); //$NON-NLS-1$
        } catch (URISyntaxException e) {
            return false;
        }
        String label = null;
        StringTokenizer st = new StringTokenizer(host, "."); //$NON-NLS-1$
        while (st.hasMoreTokens()) {
            label = st.nextToken();
            if (label.startsWith("-") || label.endsWith("-")) {
                //$NON-NLS-1$ //$NON-NLS-2$
                return false;
            }
        }
        if (!label.equals(host)) {
            char ch = label.charAt(0);
            if (ch >= '0' && ch <= '9') {
                return false;
            }
        }
        return true;
    }

    private boolean isValidIPv4Address(String host) {
        int index;
        int index2;
        try {
            int num;
            index = host.indexOf('.');
            num = Integer.parseInt(host.substring(0, index));
            if (num < 0 || num > 255) {
                return false;
            }
            index2 = host.indexOf('.', index + 1);
            num = Integer.parseInt(host.substring(index + 1, index2));
            if (num < 0 || num > 255) {
                return false;
            }
            index = host.indexOf('.', index2 + 1);
            num = Integer.parseInt(host.substring(index2 + 1, index));
            if (num < 0 || num > 255) {
                return false;
            }
            num = Integer.parseInt(host.substring(index + 1));
            if (num < 0 || num > 255) {
                return false;
            }
        } catch (Exception e) {
            return false;
        }
        return true;
    }

    private boolean isValidIP6Address(String ipAddress) {
        int length = ipAddress.length();
        boolean doubleColon = false;
        int numberOfColons = 0;
        int numberOfPeriods = 0;
        String word = ""; //$NON-NLS-1$
        char c = 0;
        char prevChar = 0;
        int offset = 0; // offset for [] ip addresses
        if (length < 2) {
            return false;
        }
        for (int i = 0; i < length; i++) {
            prevChar = c;
            c = ipAddress.charAt(i);
            switch (c) {
            // case for an open bracket [x:x:x:...x]
                case '[':
                    if (i != 0) {
                        return false; // must be first character
                    }
                    if (ipAddress.charAt(length - 1) != ']') {
                        return false; // must have a close ]
                    }
                    if ((ipAddress.charAt(1) == ':') && (ipAddress.charAt(2) != ':')) {
                        return false;
                    }
                    offset = 1;
                    if (length < 4) {
                        return false;
                    }
                    break;
            // case for a closed bracket at end of IP [x:x:x:...x]
                case ']':
                    if (i != length - 1) {
                        return false; // must be last character
                    }
                    if (ipAddress.charAt(0) != '[') {
                        return false; // must have a open [
                    }
                    break;
            // case for the last 32-bits represented as IPv4
            // x:x:x:x:x:x:d.d.d.d
                case '.':
                    numberOfPeriods++;
                    if (numberOfPeriods > 3) {
                        return false;
                    }
                    if (!isValidIP4Word(word)) {
                        return false;
                    }
                    if (numberOfColons != 6 && !doubleColon) {
                        return false;
                    }
                    // a special case ::1:2:3:4:5:d.d.d.d allows 7 colons
                    // with
                    // an IPv4 ending, otherwise 7 :'s is bad
                    if (numberOfColons == 7 && ipAddress.charAt(0 + offset) != ':' && ipAddress.charAt(1 + offset) != ':') {
                        return false;
                    }
                    word = ""; //$NON-NLS-1$
                    break;
                case ':':
                    numberOfColons++;
                    if (numberOfColons > 7) {
                        return false;
                    }
                    if (numberOfPeriods > 0) {
                        return false;
                    }
                    if (prevChar == ':') {
                        if (doubleColon) {
                            return false;
                        }
                        doubleColon = true;
                    }
                    word = ""; //$NON-NLS-1$
                    break;
                default:
                    if (word.length() > 3) {
                        return false;
                    }
                    if (!isValidHexChar(c)) {
                        return false;
                    }
                    word += c;
            }
        }
        // Check if we have an IPv4 ending
        if (numberOfPeriods > 0) {
            if (numberOfPeriods != 3 || !isValidIP4Word(word)) {
                return false;
            }
        } else {
            // If we're at then end and we haven't had 7 colons then there
            // is a problem unless we encountered a doubleColon
            if (numberOfColons != 7 && !doubleColon) {
                return false;
            }
            // If we have an empty word at the end, it means we ended in
            // either a : or a .
            // If we did not end in :: then this is invalid
            if (word == "" && ipAddress.charAt(length - 1 - offset) != ':' && ipAddress.charAt(length - 2 - offset) != ':') {
                return false;
            }
        }
        return true;
    }

    private boolean isValidIP4Word(String word) {
        char c;
        if (word.length() < 1 || word.length() > 3) {
            return false;
        }
        for (int i = 0; i < word.length(); i++) {
            c = word.charAt(i);
            if (!(c >= '0' && c <= '9')) {
                return false;
            }
        }
        if (Integer.parseInt(word) > 255) {
            return false;
        }
        return true;
    }

    private boolean isValidHexChar(char c) {
        return (c >= '0' && c <= '9') || (c >= 'A' && c <= 'F') || (c >= 'a' && c <= 'f');
    }
    
}
