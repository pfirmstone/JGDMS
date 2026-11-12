/*
 * CoreMathInverseTrigTables: Java port of the constant tables of CORE-MATH's binary64 atan, asin, acos and atan2,
 * src/binary64/atan/atan.c, src/binary64/asin/asin.c, src/binary64/acos/acos.c and src/binary64/atan2/atan2.c, and of the
 * constants of src/binary64/atan2/tint.h, at commit 018fa97cbdacf59924b899a9dfa422d532cb2bb7.
 *
 * DERIVATION: this file is a Java port, that is a modified and derived work, of
 * the constant tables of src/binary64/atan/atan.c, src/binary64/asin/asin.c (repeated in src/binary64/acos/acos.c),
 * src/binary64/acos/acos.c, src/binary64/atan2/atan2.c and src/binary64/atan2/tint.h of the CORE-MATH project at commit 018fa97cbdacf59924b899a9dfa422d532cb2bb7.
 * It is not part of CORE-MATH and is not endorsed by its authors. The copyright and permission notice below are the upstream's, reproduced without change.
 *
 * Copyright (c) 2023-2026 Alexei Sibidanov <sibid@uvic.ca>.
 * Copyright (c) 2022-2026 Alexei Sibidanov <sibid@uvic.ca>.
 * Copyright (c) 2024-2025 Alexei Sibidanov.
 * Copyright (c) 2024-2025 Paul Zimmermann and Alexei Sibidanov
 * Copyright (c) 2023-2024 Paul Zimmermann and Alexei Sibidanov
 *
 * This file is part of the CORE-MATH project
 * (https://core-math.gitlabpages.inria.fr/).
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package au.net.zeus.jgdms.cel.math;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The constant tables of CORE-MATH's binary64 {@code atan}, {@code asin}, {@code acos} and
 * {@code atan2} (and the four constants of {@code tint.h}), in one class so that one digest covers them.
 * The {@code cc}, {@code s}, {@code c} and {@code ct} tables of {@code asin.c} are text-identical to those
 * of {@code acos.c} (the md5 of each line range was compared on 2026-11-12), so one definition serves both
 * ports, as {@link CoreMathTables} does for sine and cosine.
 * <p>
 * <b>Bit-exact.</b> Every value is a literal copy of the C source's: a {@code double} as the same
 * hexadecimal floating-point literal, a 64-bit word as the same hexadecimal digits. A rounded decimal
 * would change a table entry and, silently, a result. Two checks guard them. {@code CoreMathInverseTrigConstantsTest}
 * re-reads the C files and compares every entry here to them, bit for bit; it needs the CORE-MATH clone, so it is
 * skipped without it (and FAILS if the full tier is requested with {@code -Dcore.math.full=true}).
 * {@code CoreMathInverseTrigTablesDigestTest} needs nothing: it hashes a canonical rendering of {@link #snapshot()}
 * (SHA-256) and compares the digest, committed as a test resource, so a changed bit fails on every machine.
 * <p>
 * <b>Layout.</b> A C two-dimensional table is flattened row-major (see each field). The arrays are private:
 * they are Java arrays, so a package-visible array could be written by any class in this package and every
 * later result would change silently. The element accessors are what the ports call (small static methods
 * the JIT inlines); {@link #snapshot()} gives the tests a copy that cannot write back.
 */
final class CoreMathInverseTrigTables {

    private CoreMathInverseTrigTables() {}

    /** C: atan.c:77-143 {@code A[][2]}, flattened row-major: {@code A[2*j]} approximates tan(pi/256*j), {@code A[2*j+1]} approximates atan(A[2*j]) - pi/256*j; 129 rows, the last (j = 128) is {0,0}. */
    private static final double[] ATAN_A = {
        0x0p+0, 0x0p+0, 0x1.9224e047e368ep-7, 0x1.a3ca6c727c59dp-62,
        0x1.92346247a91fp-6, 0x1.138b0ef96a186p-64, 0x1.2dbaae9a05dbp-5, 0x1.36e7f8a3f5e42p-59,
        0x1.927278a3b1162p-5, -0x1.ac986efb92662p-64, 0x1.f7495ea3f3783p-5, 0x1.06ec8011ee816p-59,
        0x1.2e239ccff3831p-4, -0x1.858437d431332p-58, 0x1.60b9f7597fdecp-4, -0x1.cebd13eb7c513p-60,
        0x1.936bb8c5b2da2p-4, -0x1.840cac0d81db5p-58, 0x1.c63ce377fc802p-4, 0x1.400b0fdaa109ep-58,
        0x1.f93183a8db9e9p-4, 0x1.0e04e06c86e72p-59, 0x1.1626d85a91e7p-3, 0x1.f7ad829163ca7p-59,
        0x1.2fcac73a6064p-3, -0x1.2680735ce2cd8p-58, 0x1.4986a74cf4e57p-3, -0x1.90559690b42e4p-57,
        0x1.635c990ce0d36p-3, 0x1.91d29110b41aap-58, 0x1.7d4ec54fb5968p-3, -0x1.ea90e2718278p-59,
        0x1.975f5e0553158p-3, -0x1.dc82ac14e3e1cp-61, 0x1.b1909efd8b762p-3, -0x1.73a10fd13daafp-58,
        0x1.cbe4ceb4b4cf2p-3, -0x1.3a7ffbeabda0bp-57, 0x1.e65e3f27c9f2ap-3, -0x1.db6627a24d523p-57,
        0x1.007fa758626aep-2, -0x1.45f97dd3099f6p-57, 0x1.0de53475f3b3cp-2, -0x1.6293f68741816p-57,
        0x1.1b6103d3597e9p-2, -0x1.ab240d40633e9p-57, 0x1.28f459ecad74dp-2, -0x1.de34d14e832ep-61,
        0x1.36a08355c63dcp-2, 0x1.af540d9fb4926p-57, 0x1.4466d542bac92p-2, 0x1.da60fdbc82ac4p-57,
        0x1.5248ae1701b17p-2, -0x1.92a601170138ap-56, 0x1.604775fbb27dfp-2, -0x1.7f1fca1d5d15bp-57,
        0x1.6e649f7d78649p-2, -0x1.4e223ea716c7bp-57, 0x1.7ca1a832d0f84p-2, 0x1.b24c824ac51fcp-56,
        0x1.8b00196b3d022p-2, 0x1.4314cd132ba43p-57, 0x1.998188e816bfp-2, -0x1.11f1e0817879ap-56,
        0x1.a827999fcef32p-2, -0x1.c3dea4dbad538p-57, 0x1.b6f3fc8c61e5bp-2, 0x1.60d1b780ee3ebp-57,
        0x1.c5e87185e67b6p-2, -0x1.ab5edb7dfa545p-59, 0x1.d506c82a2c8p-2, -0x1.8e1437048b5bdp-57,
        0x1.e450e0d273e7ap-2, -0x1.06951c97b050fp-56, 0x1.f3c8ad985d9eep-2, -0x1.14af9522ab518p-59,
        0x1.01b819b5a7cf7p-1, -0x1.aba0d7d97d1f2p-56, 0x1.09a4c59bd0d4dp-1, 0x1.095bc4ebc2c42p-59,
        0x1.11ab7190834ecp-1, 0x1.798826fa27774p-55, 0x1.19cd3fe8e405dp-1, 0x1.008f6258fc98fp-55,
        0x1.220b5ef047825p-1, -0x1.462af7ceb7de6p-58, 0x1.2a6709a74f289p-1, -0x1.1184dfd78b472p-56,
        0x1.32e1889047ffdp-1, 0x1.9141876dc40c5p-56, 0x1.3b7c3289ed6f3p-1, 0x1.481c20189726cp-55,
        0x1.44386db9ce5dbp-1, 0x1.2e851bd025441p-55, 0x1.4d17b087b265dp-1, 0x1.13ada9b8bc419p-56,
        0x1.561b82ab7f99p-1, -0x1.05b4c3c4cbee8p-55, 0x1.5f457e4f4812ep-1, -0x1.5619249bd96f1p-55,
        0x1.6897514751db6p-1, -0x1.b0a0fbcafc671p-57, 0x1.7212be621be6dp-1, -0x1.19ff2dc66da45p-55,
        0x1.7bb99ed2990cfp-1, 0x1.1320449592d92p-55, 0x1.858de3b716571p-1, -0x1.1fddcd2f3da8ep-55,
        0x1.8f9197bf85eebp-1, 0x1.d44a42e35cc97p-57, 0x1.99c6e0f634394p-1, -0x1.585a178b4a18dp-56,
        0x1.a43002ae4285p-1, 0x1.f95a531b3a97p-57, 0x1.aecf5f9ba35a6p-1, -0x1.96c2d43ca3392p-60,
        0x1.b9a77c18c1af2p-1, -0x1.a5bed94b05defp-57, 0x1.c4bb009e77983p-1, 0x1.54509d2bff511p-59,
        0x1.d00cbc7384d2ep-1, -0x1.b4c867cef300cp-57, 0x1.db9fa89953fcfp-1, -0x1.ddfac663d6bc6p-62,
        0x1.e776eafc91706p-1, -0x1.a510683ff7cb6p-56, 0x1.f395d9f0e3c92p-1, 0x1.4fdcd8e4e871p-59,
        0x1p+0, 0x0p+0, 0x1.065c900aaf2d8p+0, -0x1.deec7fc9042adp-55,
        0x1.0ce29d0883c99p+0, -0x1.395ae45e0657dp-55, 0x1.139447e6a86eep+0, 0x1.332cf301a97f3p-55,
        0x1.1a73d55278c4bp+0, -0x1.6cc8c4b78213bp-55, 0x1.2183b0c4573ffp+0, 0x1.70a90841da57ap-55,
        0x1.28c66fdaf8f09p+0, -0x1.ba39bad450eep-57, 0x1.303ed61109e2p+0, -0x1.8692946d9f93cp-55,
        0x1.37efd8d87607ep+0, 0x1.3b711bf765b58p-57, 0x1.3fdca42847507p+0, 0x1.c21387985b081p-56,
        0x1.48089f8bf42ccp+0, -0x1.7ddb19d3d0efcp-55, 0x1.507773c537eadp+0, -0x1.f5e354cf971f3p-56,
        0x1.592d11142fa55p+0, -0x1.00f0ad675330dp-56, 0x1.622db63c8ecc2p+0, -0x1.2c93f50ab2c0ep-55,
        0x1.6b7df862652p+0, 0x1.bec391adc37d5p-56, 0x1.7522cbdd428a8p+0, -0x1.9686ddc9ffcf5p-57,
        0x1.7f218e25a7461p+0, -0x1.8d16529514246p-56, 0x1.89801106cc709p+0, -0x1.092f51e9c2803p-55,
        0x1.9444a7462122ap+0, -0x1.07c06755404c4p-55, 0x1.9f7632fa9e871p+0, 0x1.02e0d43abc92bp-55,
        0x1.ab1c35d8a74eap+0, 0x1.d0184e48af6f7p-58, 0x1.b73ee3c3ef16ap+0, 0x1.73be957380bc2p-56,
        0x1.c3e738086bc0fp+0, -0x1.02b6e26c84462p-56, 0x1.d11f0dae40609p+0, 0x1.25c4f3ffa6e1fp-58,
        0x1.def13b73c1406p+0, -0x1.e302db3c6823fp-58, 0x1.ed69b4153a45dp+0, 0x1.3207830326c0ep-56,
        0x1.fc95abad6cf4ap+0, -0x1.6308cee7927bfp-57, 0x1.0641e192ceab3p+1, -0x1.0147ebf0df4c5p-56,
        0x1.0ea21d716fbf7p+1, -0x1.168533cc41d8bp-56, 0x1.17749711a6679p+1, -0x1.52a0b0333e9c5p-57,
        0x1.20c36c6a7f38ep+1, 0x1.8659eece35395p-57, 0x1.2a99f50fd4f4fp+1, 0x1.20fcad18cb36fp-55,
        0x1.3504f333f9de6p+1, -0x1.52afdbd5a8c74p-56, 0x1.4012ce2586a17p+1, -0x1.9747a792907d7p-56,
        0x1.4bd3d87fe065p+1, 0x1.90c59393b52c8p-56, 0x1.585aa4e1530fap+1, 0x1.af6934f13a3a8p-56,
        0x1.65bc6cc825147p+1, -0x1.8534dcab5ad3ep-59, 0x1.74118e4b6a7c8p+1, -0x1.555aa8bfca9a1p-56,
        0x1.837626d70fdb8p+1, -0x1.56b3fee9ca72bp-58, 0x1.940ad30abc792p+1, 0x1.4b3fdd4fdc06cp-58,
        0x1.a5f59e90600ddp+1, 0x1.285d367c55ddcp-57, 0x1.b9633283b6d14p+1, -0x1.8712976f17a16p-59,
        0x1.ce885653127e7p+1, -0x1.abe8ab65d49fcp-60, 0x1.e5a3de972a377p+1, 0x1.cd9be81ad764bp-58,
        0x1.ff01305ecd8dcp+1, 0x1.742c2922656fap-59, 0x1.0d7dc7cff4c9ep+2, -0x1.7c842978bee09p-56,
        0x1.1d0143e71565fp+2, 0x1.7bc7dea7c3c03p-57, 0x1.2e4ff1626b949p+2, 0x1.aefbe25b404e9p-59,
        0x1.41bfee2424771p+2, -0x1.4bcfaaa95cb2cp-60, 0x1.57be4eaa5e11bp+2, 0x1.0fe741e4ec679p-58,
        0x1.70d751908c1b1p+2, 0x1.fe74a5b0ec709p-58, 0x1.8dc25c117782bp+2, 0x1.0ca1c19f710efp-58,
        0x1.af73f4ca3310fp+2, 0x1.2867b40ba77d6p-58, 0x1.d7398d15e70dbp+2, 0x1.0fd4e0d4b1547p-57,
        0x1.0372fb36b87e2p+3, 0x1.c16c9ecc1621dp-58, 0x1.208dbdae055efp+3, 0x1.6b81a36e75e8cp-58,
        0x1.44e6c595afdccp+3, -0x1.7c22045771848p-58, 0x1.7398c57f3f1adp+3, 0x1.970503be105cp-58,
        0x1.b1d03c03d2f7fp+3, -0x1.f299d010aead2p-60, 0x1.046e9fe60a77ep+4, 0x1.d2b61deff33ecp-58,
        0x1.45affed201b55p+4, 0x1.0e84d9567203ap-64, 0x1.b267195b1ffaep+4, -0x1.ad44b44b92653p-64,
        0x1.45e2455e4aaa7p+5, -0x1.296d577b5e21dp-60, 0x1.45eed6854ce99p+6, 0x1.2db53886013cap-63,
        0,0
    };

    /** C: atan.c:147-155 {@code uint16_t c[31][3]}, flattened row-major: {@code c[3*i + k]}. */
    private static final int[] ATAN_C = {
        419, 81, 0, 500, 81, 0, 582, 163, 0, 745, 163, 0, 908, 326, 0, 1234, 326, 0,
        1559, 651, 0, 2210, 650, 1, 2860, 1299, 3, 4156, 1293, 4, 5444, 2569, 24,
        7989, 2520, 32, 10476, 4917, 168, 15224, 4576, 200, 19601, 8341, 838,
        27105, 6648, 731, 33036, 10210, 1998, 41266, 6292, 1117, 46469, 7926, 2048,
        52375, 4038, 849, 55587, 4591, 1291, 58906, 2172, 479, 60612, 2390, 688,
        62325, 1107, 247, 63192, 1207, 349, 64056, 556, 124, 64491, 605, 175,
        64923, 278, 62, 65141, 303, 88, 65358, 139, 31, 65467, 151, 44
    };

    /** C: atan.c:232 {@code ch[4]} of {@code cr_atan}. */
    private static final double[] ATAN_CH = {
        0x1p+0, -0x1.555555555552bp-2, 0x1.9999999069c2p-3, -0x1.248d2c8444ac6p-3
    };

    /** C: atan.c:239-240 {@code ch2[4]} of {@code cr_atan}. */
    private static final double[] ATAN_CH2 = {
        -0x1.5555555555555p-2, 0x1.99999999998c1p-3, -0x1.249249176aecp-3, 0x1.c711fd121ae8p-4
    };

    /** C: atan.c:161-163 {@code ch[][2]} of {@code as_atan_refine2}, flattened: {@code ch[2*i + k]}. */
    private static final double[] ATAN_R2_CH = {
        -0x1.5555555555555p-2, -0x1.5555555555555p-56, 0x1.999999999999ap-3, -0x1.999999999bcb8p-57,
        -0x1.2492492492492p-3, -0x1.249242093c016p-57
    };

    /** C: atan.c:164-165 {@code cl[4]} of {@code as_atan_refine2}. */
    private static final double[] ATAN_R2_CL = {
        0x1.c71c71c71c71cp-4, -0x1.745d1745d1265p-4, 0x1.3b13b115bcbc4p-4, -0x1.1107c41ad3253p-4
    };

    /** C: atan.c:203-216 {@code db[][3]} of {@code as_atan_refine2}, flattened row-major (|x|, rounded result, correction); 12 rows. */
    private static final double[] ATAN_DB = {
        0x1.0dc89a3b5501p-7, 0x1.0dc70ac228717p-7, 0x1p-61,
        0x1.e3fb41d2d226p-8, 0x1.e3f9013a852f8p-8, 0x1p-62,
        0x1.7ba49f739829fp-1, 0x1.46ac372243536p-1, 0x1p-109,
        0x1.a933fe176b375p-3, 0x1.a33f32ac5ceb5p-3, -0x1p-112,
        0x1.bb04a79820063p-8, 0x1.bb02ed5c5e956p-8, -0x1p-115,
        0x1.cd30a9499618bp-8, 0x1.cd2eb65f92a46p-8, -0x1p-112,
        0x1.f44aa37b8e66bp-7, 0x1.f440b04187c87p-7, -0x1p-112,
        0x1.fd2ac95e57ef9p-8, 0x1.fd2829febc03ap-8, -0x1p-112,
        0x1.6419079bbf601p-6, 0x1.640aade8f5427p-6, 0x1p-114,
        0x1.7ba49f739829fp-1, 0x1.46ac372243536p-1, 0x1p-110,
        0x1.d768804487b07p-3, 0x1.cf5676f373ec1p-3, -0x1p-110,
        0x1.bb04a79820063p-8, 0x1.bb02ed5c5e956p-8, -0x1p-115
    };

    /** C: asin.c:120-187 and acos.c:144-211 {@code cc[33][8]} (identical text in both files), flattened row-major: {@code cc[8*j + k]}. */
    private static final double[] ASINACOS_CC = {
        1,                      0, 0x1.5555555555555p-3, 0x1.33333333333e4p-4,
        0x1.6db6db6d31f82p-5, 0x1.f1c71f6889397p-6, 0x1.6e874b7045b46p-6, 0x1.1f753132271e2p-6,
        0x1.0055a27e0d033p+0, -0x1.d9ba10494c062p-54, 0x1.57c00cb5d6c4dp-3, 0x1.37881f5649a75p-4,
        0x1.759af49d494ddp-5, 0x1.002e1864dda2ep-5, 0x1.7c2d5d468cdd9p-6, 0x1.292834c025357p-6,
        0x1.00abe0c129e1ep+0,  0x1.7ceb0ee49d42ap-57, 0x1.5a3385d5c7ba5p-3, 0x1.3bf51056f6637p-4,
        0x1.7dba76b124b37p-5, 0x1.07be4b02e94c4p-5, 0x1.8a6bb92513f01p-6, 0x1.36afd4c615aecp-6,
        0x1.0102bcffd6acdp+0, -0x1.c2294c65d2e86p-55, 0x1.5caff17351901p-3, 0x1.407abbc04feb3p-4,
        0x1.86179b8005949p-5, 0x1.0f97520bd4e72p-5, 0x1.9950c5c89f3dfp-6, 0x1.44f2344e7b664p-6,
        0x1.015a397cf0f1cp+0, -0x1.eebd6ccfe3ee3p-55, 0x1.5f3581be7b08bp-3, 0x1.4519ddf1ae531p-4,
        0x1.8eb4b6ee35e92p-5, 0x1.17bc85414cd46p-5, 0x1.a8e5895e3fcf9p-6, 0x1.53fafdc629400p-6,
        0x1.01b2588811eebp+0,  0x1.7193e5d0a915fp-59, 0x1.61c46a67205d2p-3, 0x1.49d33a6eeae0bp-4,
        0x1.979438563c014p-5, 0x1.20316ae977f05p-5, 0x1.b9339afb53aa4p-6, 0x1.63d6b02c42d0ap-6,
        0x1.020b1c7df0575p+0, -0x1.dd547e329c1e5p-55, 0x1.645ce0ab901bbp-3, 0x1.4ea79c34fc7a6p-4,
        0x1.a0b8ac08940ecp-5, 0x1.28f9babd0629bp-5, 0x1.ca452cf90a55ep-6, 0x1.7492b016730efp-6,
        0x1.026487c8c5d71p+0, -0x1.5fd9b68dc3b6ep-54, 0x1.66ff1b67d5d70p-3, 0x1.5397d613373ebp-4,
        0x1.aa24bce3aeb4ap-5, 0x1.3219610b150acp-5, 0x1.dc251825103f1p-6, 0x1.863d5a3932532p-6,
        0x1.02be9ce0b87cdp+0,  0x1.e5d09da2e0f04p-56, 0x1.69ab5325bc359p-3, 0x1.58a4c3097aab3p-4,
        0x1.b3db3605f46f2p-5, 0x1.3b94821742cabp-5, 0x1.eedee7da72a15p-6, 0x1.98e6179a3e9a0p-6,
        0x1.03195e4c483f1p+0, -0x1.5db10ad66eacbp-54, 0x1.6c61c22d908f0p-3, 0x1.5dcf46ab9f2cbp-4,
        0x1.bddf049bb1f4dp-5, 0x1.456f7db6ac768p-5, 0x1.013f738bd7bb3p-5, 0x1.ac9d739783d21p-6,
        0x1.0374cea0c0c9fp+0, -0x1.917bff5241c76p-54, 0x1.6f22a497b2ec0p-3, 0x1.63184d8a79db5p-4,
        0x1.c83339caf946ep-5, 0x1.4faef331019d4p-5, 0x1.0b8917547d678p-5, 0x1.c17533f147e1cp-6,
        0x1.03d0f082afcc8p+0, -0x1.018bbcddb49ebp-54, 0x1.71ee385efdf06p-3, 0x1.6880cda2d3885p-4,
        0x1.d2db0cbfae54dp-5, 0x1.5a57c56b50c5ep-5, 0x1.16535a40098b2p-5, 0x1.d780730b8ebb8p-6,
        0x1.042dc6a65ffbfp+0, -0x1.c7ea28dce95d1p-55, 0x1.74c4bd7412f9ep-3, 0x1.6e09c6d2b72bcp-4,
        0x1.ddd9dcda253dep-5, 0x1.656f1f62b5001p-5, 0x1.21a5ae2ac77eep-5, 0x1.eed3bca067f0ep-6,
        0x1.048b53d05907bp+0,  0x1.634fffed6e2a6p-54, 0x1.77a675d1978bep-3, 0x1.73b4435583415p-4,
        0x1.e9333402ebbf3p-5, 0x1.70fa78fd9f73fp-5, 0x1.2d8804d934fe1p-5, 0x1.03c29691a281cp-5,
        0x1.04e99ad5e4bcdp+0, -0x1.e97a72fe827e0p-54, 0x1.7a93a5917200cp-3, 0x1.7981584731c05p-4,
        0x1.f4eac9268fae2p-5, 0x1.7cff9c3b19721p-5, 0x1.3a02d9c1e0145p-5, 0x1.10d64a0e56953p-5,
        0x1.05489e9d99995p+0,  0x1.d177637ec6a2bp-55, 0x1.7d8c930314681p-3, 0x1.7f72262f532e4p-4,
        0x1.0082416e39013p-4, 0x1.8984aac80ddf4p-5, 0x1.471f3caf18eb8p-5, 0x1.1eb1cce6dd570p-5,
        0x1.05a8621feb16bp+0, -0x1.e5b33b1407c5fp-56, 0x1.809186c2e57ddp-3, 0x1.8587d99442dc8p-4,
        0x1.06c23d1dfcb7fp-4, 0x1.969024036dd22p-5, 0x1.54e6dd4d2af33p-5, 0x1.2d62f439f2a31p-5,
        0x1.0608e867bff30p+0,  0x1.cbef5d8580027p-55, 0x1.83a2cbd2d8ba2p-3, 0x1.8bc3ab9724c6ep-4,
        0x1.0d377ef1e0c39p-4, 0x1.a428eb7addf84p-5, 0x1.636417bc01ff2p-5, 0x1.3cf8acc7eb2a0p-5,
        0x1.066a34930ec8dp+0, -0x1.480f445fedad1p-54, 0x1.86c0afb447a74p-3, 0x1.9226e29948d9cp-4,
        0x1.13e44a9b5a3a6p-4, 0x1.b2564fea8b3fep-5, 0x1.72a2023d92458p-5, 0x1.4d8313cec3485p-5,
        0x1.06cc49d38146cp+0, -0x1.b55394f4fc07bp-55, 0x1.89eb82831feedp-3, 0x1.98b2d2eb9bb23p-4,
        0x1.1acb01e9ab414p-4, 0x1.c12012cbd00c6p-5, 0x1.82ac7c1d15c38p-5, 0x1.5f13925c6edcap-5,
        0x1.072f2b6f1e601p+0, -0x1.2dcbb05419970p-54, 0x1.8d2397127aebbp-3, 0x1.9f68df88da51dp-4,
        0x1.21ee26a4f62a1p-4, 0x1.d08e707f7ae6fp-5, 0x1.93903dee3feb0p-5, 0x1.71bcfb5c57b59p-5,
        0x1.0792dcc0fbd20p+0, -0x1.5bf23ee4f9d54p-56, 0x1.9069430ab508ap-3, 0x1.a64a7adb4cd85p-4,
        0x1.29505c8b48349p-4, 0x1.e0aa2921cfa60p-5, 0x1.a55aeb46f4322p-5, 0x1.8593acad3becep-5,
        0x1.07f76139f761dp+0,  0x1.fa1046481bb82p-54, 0x1.93bcdf091cca6p-3, 0x1.ad59278edc42fp-4,
        0x1.30f46b7261652p-4, 0x1.f17c8a17c843ep-5, 0x1.b81b2619e15b5p-5, 0x1.9aadb395f5ae4p-5,
        0x1.085cbc61783c1p+0,  0x1.0a6e9efa20176p-54, 0x1.971ec6c1531e4p-3, 0x1.b496797068912p-4,
        0x1.38dd419140184p-4, 0x1.0187bc3357bbbp-4, 0x1.cbe0a3dcafe26p-5, 0x1.b122f4fa499d0p-5,
        0x1.08c2f1d638e4cp+0,  0x1.b47c159534a3dp-56, 0x1.9a8f592078624p-3, 0x1.bc04165b57ab2p-4,
        0x1.410df5f4bed1dp-4, 0x1.0ab6bdf478c71p-4, 0x1.e0bc44a945c64p-5, 0x1.c90d59bcd5701p-5,
        0x1.092a054f1a2fcp+0, -0x1.2f657224e9830p-54, 0x1.9e0ef87243a2cp-3, 0x1.c3a3b7366a278p-4,
        0x1.4989cb22e2175p-4, 0x1.1450e5ba7ad39p-4, 0x1.f6c02c8f0ef93p-5, 0x1.e288ffc8d182cp-5,
        0x1.0991fa9bffbf4p+0, -0x1.ca1140a1abbf4p-58, 0x1.a19e0a8823b80p-3, 0x1.cb772900f9c24p-4,
        0x1.525431f0cbb2ep-4, 0x1.1e5c2d06804e1p-4, 0x1.06ffefa7aa6b8p-4, 0x1.fdb4704dca347p-5,
        0x1.09fad5a6b68f9p+0,  0x1.aa1f06e92964ep-56, 0x1.a53cf8e28c50ep-3, 0x1.d3804df1de350p-4,
        0x1.5b70cc8fa98dcp-4, 0x1.28def298c979bp-4, 0x1.13482f6347eebp-4, 0x1.0d586de48358cp-4,
        0x1.0a649a73e61f2p+0,  0x1.74ac0d817e9c7p-55, 0x1.a8ec30dc93891p-3, 0x1.dbc11ea950625p-4,
        0x1.64e371d5616d3p-4, 0x1.33e0023936249p-4, 0x1.204426263066ap-4, 0x1.1cd12e4629723p-4,
        0x1.0acf4d240ccc4p+0,  0x1.da890f3b40bd3p-54, 0x1.acac23da07797p-3, 0x1.e43bab7741a98p-4,
        0x1.6eb030c631819p-4, 0x1.3f669d2eb516ep-4, 0x1.2e0006ae505aep-4, 0x1.2d58204457c82p-4,
        0x1.0b3af1f4880bbp+0,  0x1.f450fb78d32bap-56, 0x1.b07d4778263afp-3, 0x1.ecf21db7be0efp-4,
        0x1.78db5465013e4p-4, 0x1.4b7a8376f0996p-4, 0x1.3c88f9f2ef221p-4, 0x1.3f02ad9eb9753p-4,
        0x1.0ba78d40a9260p+0, -0x1.57b07a441e242p-54, 0x1.b46015c126262p-3, 0x1.f5e6b94713f3dp-4,
        0x1.836967d0afecfp-4, 0x1.5823fdd1707b9p-4, 0x1.4bed355269dc2p-4, 0x1.51e83065121cfp-4,
        0x1.0c152382d7366p+0, -0x1.ee6913347c2a6p-54, 0x1.b8550d62bfb6ep-3, 0x1.ff1bde0fa3cadp-4,
        0x1.8e5f3ab550989p-4, 0x1.656be8b38ebafp-4, 0x1.5c3c13008a099p-4, 0x1.662225a1b4f77p-4,
    };

    /** C: asin.c:273-291 and acos.c:318-336 {@code s[33][2]} (identical text), flattened row-major: {@code s[2*j + k]}: sin(pi/64*j) as (low, high). */
    private static final double[] ASINACOS_S = {
        0x0p+0, 0x0p+0, -0x1.912bd0d569a9p-61, 0x1.91f65f10dd814p-5,
        -0x1.e2718d26ed688p-60, 0x1.917a6bc29b42cp-4, 0x1.13000a89a11ep-58, 0x1.2c8106e8e613ap-3,
        -0x1.26d19b9ff8d82p-57, 0x1.8f8b83c69a60bp-3, -0x1.42deef11da2c4p-57, 0x1.f19f97b215f1bp-3,
        -0x1.5d28da2c4612dp-56, 0x1.294062ed59f06p-2, -0x1.efdc0d58cf62p-62, 0x1.58f9a75ab1fddp-2,
        -0x1.72cedd3d5a61p-57, 0x1.87de2a6aea963p-2, 0x1.5b362cb974183p-57, 0x1.b5d1009e15ccp-2,
        0x1.e0d891d3c6841p-58, 0x1.e2b5d3806f63bp-2, -0x1.a5a014347406cp-55, 0x1.073879922ffeep-1,
        0x1.b25dd267f66p-55, 0x1.1c73b39ae68c8p-1, -0x1.efcc626f74a6fp-57, 0x1.30ff7fce17035p-1,
        0x1.8076a2cfdc6b3p-57, 0x1.44cf325091dd6p-1, -0x1.75720992bfbb2p-55, 0x1.57d69348cecap-1,
        -0x1.bdd3413b26456p-55, 0x1.6a09e667f3bcdp-1, -0x1.0f537acdf0ad7p-56, 0x1.7b5df226aafafp-1,
        -0x1.2c5e12ed1336dp-55, 0x1.8bc806b151741p-1, -0x1.30ee286712474p-55, 0x1.9b3e047f38741p-1,
        0x1.9f630e8b6dac8p-60, 0x1.a9b66290ea1a3p-1, -0x1.bc69f324e6d61p-55, 0x1.b728345196e3ep-1,
        -0x1.6e0b1757c8d07p-56, 0x1.c38b2f180bdb1p-1, -0x1.e7b6bb5ab58aep-58, 0x1.ced7af43cc773p-1,
        0x1.457e610231ac2p-56, 0x1.d906bcf328d46p-1, -0x1.014c76c126527p-55, 0x1.e212104f686e5p-1,
        0x1.760b1e2e3f81ep-55, 0x1.e9f4156c62ddap-1, 0x1.52c7adc6b4989p-56, 0x1.f0a7efb9230d7p-1,
        0x1.562172a361fd3p-56, 0x1.f6297cff75cbp-1, -0x1.7a0a8ca13571fp-55, 0x1.fa7557f08a517p-1,
        -0x1.87df6378811c7p-55, 0x1.fd88da3d12526p-1, -0x1.c57bc2e24aa15p-57, 0x1.ff621e3796d7ep-1,
        0x0p+0, 0x1p+0
    };

    /** C: asin.c:311-314 and acos.c:385-388 {@code c[5][2]} (identical text), flattened: {@code c[2*i + k]}. */
    private static final double[] ASINACOS_C = {
        0x1p+0, -0x1.fc2c76456515bp-108, 0x1.5555555555555p-3, 0x1.5555555623513p-57,
        0x1.3333333333333p-4, 0x1.9997e3427441bp-59, 0x1.6db6db6db6db7p-5, -0x1.cb95ff08658e6p-62,
        0x1.f1c71c71c6d5bp-6, 0x1.b125bccdcc89ep-60
    };

    /** C: asin.c:315 and acos.c:389 {@code ct[3]} (identical text). */
    private static final double[] ASINACOS_CT = {
        0x1.6e8ba2ec8cb69p-6, 0x1.1c4ea7a15c997p-6, 0x1.ca8355d39bb67p-7
    };

    /** C: asin.c:195-197 {@code off[2][2]}, flattened: {@code off[2*k + c]} (the C declares it over three lines; the first row is +pi/2, the second -pi/2). */
    private static final double[] ASIN_OFF = {
        0x1.921fb54442d18p+0, 0x1.1a62633145c07p-54, -0x1.921fb54442d18p+0, -0x1.1a62633145c07p-54
    };

    /** C: asin.c:351-381 {@code db[][3]} of {@code as_asin_database}, flattened row-major (|x|, rounded result, correction); 29 rows sorted by |x|. */
    private static final double[] ASIN_DB = {
        0x1.7137449123ef6p-26, 0x1.7137449123ef7p-26, -0x1.fffffffffffffp-80,
        0x1.d12ed0af1a27ep-26, 0x1.d12ed0af1a27fp-26, -0x1.63676aa7969aap-134,
        0x1.51c4b960778f5p-23, 0x1.51c4b9607790dp-23, 0x1.fffffffffffffp-77,
        0x1.3cfc2a006a414p-22, 0x1.3cfc2a006a465p-22, -0x1.1e68bc303367cp-131,
        0x1.cbaa95dadb559p-22, 0x1.cbaa95dadb65p-22, 0x1.742593ef4c3d3p-128,
        0x1.acd69f89ad8f1p-20, 0x1.acd69f89ae57ap-20, 0x1.b0ddfe3e29b3cp-126,
        0x1.2bffffffc233bp-16, 0x1.2c00000006dddp-16, -0x1.1c26f2054c23p-122,
        0x1.ff0f3022b2e9dp-16, 0x1.ff0f3024065e7p-16, -0x1.ffffffffffffep-70,
        0x1.3217783d70d1dp-14, 0x1.32177841ffbefp-14, -0x1.fffffffffffffp-68,
        0x1.c373ff4aad79bp-14, 0x1.c373ff594d65bp-14, -0x1.fffffffffffffp-68,
        0x1.b3f28593cad2fp-9, 0x1.b3f2ba40dbc66p-9, -0x1.27ca20c684481p-116,
        0x1.e17b3f6bb5e6ep-7, 0x1.e17faefac7797p-7, -0x1.15a1c95184d1bp-112,
        0x1.41d60a76a82edp-6, 0x1.41db571d96126p-6, -0x1.df34bcdacf353p-114,
        0x1.921c0a0486537p-6, 0x1.9226605233224p-6, -0x1.38e672f1696d5p-108,
        0x1.9c360a8dd681ap-6, 0x1.9c412d62c144p-6, 0x1.1edd261167bafp-110,
        0x1.d6315f7ee7e01p-6, 0x1.d641e6d5e769ap-6, -0x1.af7dec023f5b9p-109,
        0x1.ea6fdc56fc61ap-6, 0x1.ea829e3e988e5p-6, -0x1.f358e90326f9ap-109,
        0x1.2749dc4d19c6dp-5, 0x1.275a3d78c01ecp-5, 0x1.49ab71c17bd11p-110,
        0x1.69768dc89bbp-5, 0x1.69949b3d51fb1p-5, -0x1.14b91318650e8p-113,
        0x1.a4816b2066707p-5, 0x1.a4b0bfb0454d5p-5, 0x1.fffffffffffffp-59,
        0x1.d77b117f230d6p-5, 0x1.d7bdcd778049fp-5, 0x1.aafc31a59e093p-114,
        0x1.fc7a07b2549aap-5, 0x1.fccdc252cad1fp-5, -0x1.dc02fdac5159ap-109,
        0x1.2df0542154f1bp-4, 0x1.2e36813a9874p-4, -0x1.63abaa885a4dap-108,
        0x1.51cf5db1b1956p-4, 0x1.5231b416ba885p-4, -0x1.e1e3f45d0bec3p-108,
        0x1.d0ef799001ba9p-3, 0x1.d5064e6fe82c5p-3, -0x1.84b9ecb11e241p-109,
        0x1.4a8e1a96e38e3p-2, 0x1.50954b7bbf87bp-2, -0x1.b9f88cfb1ef42p-108,
        0x1.ceee68154d1c9p-2, 0x1.e05b0e0a809bcp-2, 0x1.117d6aecd2594p-107,
        0x1.da4e0e6c717a5p-2, 0x1.ed25c5eb8c916p-2, -0x1.89e120e5744a5p-109,
        0x1.e9950730c4696p-2, 0x1.fe767739d0f6dp-2, 0x1.16376667a73f3p-120,
    };

    /** C: acos.c:217 {@code off[2][2]}, flattened: {@code off[2*k + c]}. */
    private static final double[] ACOS_OFF = {
        0,0, 0x1.921fb54442d18p+1, 0x1.1a62633145c07p-53
    };

    /** C: atan2.c:463-471 {@code T2[65]}. */
    private static final double[] ATAN2_T2 = {
        0x0p+0, 0x1p-6, 0x1p-5, 0x1.8p-5, 0x1p-4, 0x1.4p-4, 0x1.8p-4, 0x1.cp-4,
        0x1p-3, 0x1.2p-3, 0x1.4p-3, 0x1.6p-3, 0x1.8p-3, 0x1.ap-3, 0x1.cp-3, 0x1.ep-3,
        0x1p-2, 0x1.1p-2, 0x1.2p-2, 0x1.3p-2, 0x1.4p-2, 0x1.5p-2, 0x1.6p-2, 0x1.7p-2,
        0x1.8p-2, 0x1.9p-2, 0x1.ap-2, 0x1.bp-2, 0x1.cp-2, 0x1.dp-2, 0x1.ep-2, 0x1.fp-2,
        0x1p-1, 0x1.08p-1, 0x1.1p-1, 0x1.18p-1, 0x1.2p-1, 0x1.28p-1, 0x1.3p-1, 0x1.38p-1,
        0x1.4p-1, 0x1.48p-1, 0x1.5p-1, 0x1.58p-1, 0x1.6p-1, 0x1.68p-1, 0x1.7p-1, 0x1.78p-1,
        0x1.8p-1, 0x1.88p-1, 0x1.9p-1, 0x1.98p-1, 0x1.ap-1, 0x1.a8p-1, 0x1.bp-1, 0x1.b8p-1,
        0x1.cp-1, 0x1.c8p-1, 0x1.dp-1, 0x1.d8p-1, 0x1.ep-1, 0x1.e8p-1, 0x1.fp-1, 0x1.f8p-1, 0x1p+0
    };

    /** C: atan2.c:472-505 {@code f2[65][2]}, flattened row-major: {@code f2[2*jt + k]}. */
    private static final double[] ATAN2_F2 = {
        0x0p+0, 0x0p+0, -0x1.95220c39d4dffp-53, 0x1.fff555bbb73p-7,
        0x1.2542779d776dep-53, 0x1.ffd55bba976p-6, -0x1.6061bbe3de53cp-53, 0x1.7fb818430da4p-5,
        -0x1.639269b0da47ep-53, 0x1.ff55bb72cfep-5, -0x1.4a7663af440f7p-55, 0x1.3f59f0e7c55ap-4,
        0x1.d1824d59f9e13p-53, 0x1.7ee182602f1p-4, 0x1.bef71e5340b31p-55, 0x1.be39ebe6f07cp-4,
        0x1.b8cb225e627dp-53, 0x1.fd5ba9aac2f6p-4, 0x1.b92de9bac94c2p-53, 0x1.1e1fafb04372p-3,
        -0x1.d3cb89e62dafdp-54, 0x1.3d6eee8c6627p-3, -0x1.882a55960087ap-53, 0x1.5c9811e3ec27p-3,
        0x1.1347b0b4f881dp-54, 0x1.7b97b4bce5bp-3, 0x1.873d8079ed0d2p-53, 0x1.9a6a8e96c862p-3,
        0x1.022f621a5c1cbp-54, 0x1.b90d7529260ap-3, 0x1.9c648d1534598p-53, 0x1.d77d5df20573p-3,
        -0x1.4ea9238610a08p-54, 0x1.f5b75f92c80ep-3, 0x1.2c5c8e721970dp-53, 0x1.09dc597d8636p-2,
        0x1.30ca4748b1bf9p-57, 0x1.18bf5a30bf178p-2, -0x1.20ef9ba6dbf9p-53, 0x1.278372057ef48p-2,
        0x1.e69c5abb498d2p-53, 0x1.362773707ebc8p-2, 0x1.a8a86f0ea9311p-54, 0x1.44aa436c2af08p-2,
        0x1.db5336feef7fp-54, 0x1.530ad9951cd48p-2, 0x1.9636a3aa3b84p-54, 0x1.614840309cfep-2,
        0x1.1ce2a8c848b74p-55, 0x1.6f61941e4defp-2, -0x1.4b1bbd1ea6db3p-55, 0x1.7d5604b63b3f8p-2,
        -0x1.4925e8b916e0bp-53, 0x1.8b24d394a1b28p-2, 0x1.9e6c988fd0a77p-56, 0x1.98cd5454d6b18p-2,
        -0x1.a49bd836a17p-53, 0x1.a64eec3cc24p-2, -0x1.ca3cf09c6b5f8p-53, 0x1.b3a911da65c7p-2,
        -0x1.cc1ce70934c34p-56, 0x1.c0db4c94ec9fp-2, 0x1.2e982ddf3872ap-55, 0x1.cde53432c135p-2,
        -0x1.2ea406ee84d0fp-55, 0x1.dac670561bb5p-2, -0x1.de35847c81979p-53, 0x1.e77eb7f175a38p-2,
        -0x1.a3992dc382a23p-57, 0x1.f40dd0b541418p-2, -0x1.b32c949c9d593p-55, 0x1.0039c73c1a40cp-1,
        -0x1.d5b495f6349e6p-56, 0x1.0657e94db30dp-1, -0x1.f34582f6255fep-53, 0x1.0c6145b5b43dcp-1,
        0x1.ed42511e3f11dp-54, 0x1.1255d9bfbd2a8p-1, -0x1.1cef189ff9e7fp-54, 0x1.1835a88be7c14p-1,
        -0x1.928df287a668fp-58, 0x1.1e00babdefeb4p-1, -0x1.e3bde360c7ddbp-53, 0x1.23b71e2cc9e6cp-1,
        0x1.bd86313ce4fdep-54, 0x1.2958e59308e3p-1, -0x1.8e8a85803cc1dp-53, 0x1.2ee628406cbccp-1,
        -0x1.77ef7641c777fp-54, 0x1.345f01cce37bcp-1, 0x1.b73ef3389d02fp-53, 0x1.39c391cd41718p-1,
        0x1.ecf8b492644fp-56, 0x1.3f13fb89e96f4p-1, 0x1.c1125fd3810c7p-53, 0x1.445065b795b54p-1,
        0x1.2483350fe548bp-53, 0x1.4978fa3269eep-1, 0x1.4a33dbeb3796cp-55, 0x1.4e8de5bb6ec04p-1,
        -0x1.46edd2af69483p-53, 0x1.538f57b89062p-1, -0x1.2bcb93b18b52ap-53, 0x1.587d81f732fbcp-1,
        0x1.0028e4bc5e7cap-57, 0x1.5d58987169b18p-1, 0x1.ed487acaf1174p-53, 0x1.6220d115d7b8cp-1,
        -0x1.2dd4dfd7d1777p-53, 0x1.66d663923e088p-1, 0x1.2bfe3cf3b9d79p-54, 0x1.6b798920b3d98p-1,
        -0x1.8c34d25aadef6p-56, 0x1.700a7c5784634p-1, -0x1.f426acf4d3bdbp-54, 0x1.748978fba8e1p-1,
        -0x1.afe57dd9ff23p-53, 0x1.78f6bbd5d316p-1, -0x1.54fbef0e862abp-54, 0x1.7d528289fa094p-1,
        0x1.90227758b11bap-54, 0x1.819d0b7158a4cp-1, 0x1.16b66e7fc8b8cp-53, 0x1.85d69576cc2c4p-1,
        -0x1.55b9a5e177a1bp-55, 0x1.89ff5ff57f1f8p-1, 0x1.c27cfaa9f7a14p-53, 0x1.8e17aa99cc05cp-1,
        0x1.1a62633145c07p-55, 0x1.921fb54442d18p-1
    };

    /** C: atan2.c:506-510 {@code O[8][2]}, flattened row-major: {@code O[2*kw + k]}. */
    private static final double[] ATAN2_O = {
        0,0, 0x1.921fb54442d18p+0,0x1.1a62633145c07p-54,
        0,0, -0x1.921fb54442d18p+0,-0x1.1a62633145c07p-54,
        0x1.921fb54442d18p+1,0x1.1a62633145c07p-53, 0x1.921fb54442d18p+0,0x1.1a62633145c07p-54,
        -0x1.921fb54442d18p+1,-0x1.1a62633145c07p-53, -0x1.921fb54442d18p+0,-0x1.1a62633145c07p-54
    };

    /** C: atan2.c:540 {@code b[3]}. */
    private static final double[] ATAN2_B = {
        -0x1.55555555554d2p-2, 0x1.999999860e1cap-3, -0x1.248ad469844a1p-3
    };

    /** C: atan2.c:573-574 {@code b2[4]}. */
    private static final double[] ATAN2_B2 = {
        -0x1.5555555555555p-2, 0x1.999999999755ep-3, -0x1.24924883596f8p-3, 0x1.c6f7d73531bc2p-4
    };

    /** C: atan2.c:421 {@code finf[2][2]}, flattened: {@code finf[2*k + c]}. */
    private static final double[] ATAN2_FINF = {
        0x1p-55, 0x1.921fb54442d18p-1, 0x1p-54, 0x1.2d97c7f3321d2p+1
    };

    /** C: atan2.c:133-164 {@code tint_t P[30]}, five longs per row: {@code h, m, l, ex, sgn}; 30 rows (numerator of the rational approximation of atan on [0,1]). */
    private static final long[] ATAN2_P = {
        0x82703f8b53112ecaL, 0x71ce6279d467aa45L, 0x19c9b28466b6fc0L, -10L, 0L,
        0xb3cf74b427d53e03L, 0xee590b839e751890L, 0xf2570b12bcb2381aL, -8L, 0L,
        0xb08498b17b88c39aL, 0x11e37821d784d8faL, 0x2795b14b84b9d78cL, -6L, 0L,
        0xfd4baa7bb52b0d83L, 0xb25425beb0a04dc3L, 0x92008d9fb34b68ffL, -5L, 0L,
        0x96541948b85fb386L, 0xe0b815ace425a859L, 0xbeca17c69bb4f7a0L, -3L, 0L,
        0x96aaa5d4baffc590L, 0xf23db783b6e4ce55L, 0x2c54e054d3b69f7eL, -2L, 0L,
        0x83cd41b5fa862b11L, 0xad8f9d6e3eb9d833L, 0x4ef470d801fa03caL, -1L, 0L,
        0xcbbc7752197cc106L, 0xbc66cfb3edefa6e3L, 0x568b2f9e4feb8910L, -1L, 0L,
        0x8cf8f7c585767042L, 0xcd92a5b9ac13d8c8L, 0x43bd4a023dbe04dL, 0L, 0L,
        0xafdc36d6b36139bfL, 0x11df63474e3859d4L, 0x42b30b2c089a2b6cL, 0L, 0L,
        0xc6f8fb1463822519L, 0x2f9f04cf55c10377L, 0x1c44675c6d483606L, 0L, 0L,
        0xcce79f10f754e315L, 0x91ce47edcbc8a4eeL, 0xb2a6f721ac656b42L, 0L, 0L,
        0xc090384dbd77132eL, 0x1dfb4d9a1d595de7L, 0xe506ca899a394f70L, 0L, 0L,
        0xa556ac9eee25e867L, 0xd84dafa25141d203L, 0xf1c7c2a5156b3ec3L, 0L, 0L,
        0x81bd2677ddfcbc38L, 0x1df33f27dbb60939L, 0xcb4c2c73286a6c29L, 0L, 0L,
        0xb9ed12169d72e8d9L, 0xc81d95dcef13c227L, 0x974b95796b93c332L, -1L, 0L,
        0xf2df6da875b666faL, 0xc29dd3a69c274c65L, 0x83b9260976c8f0c5L, -2L, 0L,
        0x902d34d4db910ed8L, 0x6f3ecddfd6af0a9fL, 0x8e950d7e70a8e9b2L, -2L, 0L,
        0x9aefa41961ce1207L, 0xacd0317666753751L, 0x3eaf42a53fcc6534L, -3L, 0L,
        0x95db9977c5e6e263L, 0x85367a63fab8a720L, 0xfc2b63bd115998d8L, -4L, 0L,
        0x817cb6d8236d5f4dL, 0x70c7815f28909d9bL, 0xc54e97155d12158L, -5L, 0L,
        0xc5eec4228c352098L, 0x789e56be1dee9b67L, 0xba25bda81292ccc1L, -7L, 0L,
        0x840c38ccfcb5dc64L, 0xb55a44095bf7f312L, 0x6668b8792d4ecd08L, -8L, 0L,
        0x97074af7b286b19eL, 0xc20aac21f3130f1dL, 0x369e9fdbdd48528aL, -10L, 0L,
        0x9054011c32d4b7a5L, 0xc68d3c5d525f4af4L, 0xeeb3c98615dee188L, -12L, 0L,
        0xdddb4a2eaab2bb15L, 0x753761a8f0e9aa8dL, 0x3f20620cd3e075bbL, -15L, 0L,
        0x80ef9a1b3e59abe5L, 0x18e6afe0651fdf49L, 0xd1c0329e6a702d9dL, -17L, 0L,
        0xca16dd49cd30854cL, 0xbad8ca379401027cL, 0x1fc7f47985486f16L, -21L, 0L,
        0xa0ba01b8742ba801L, 0xe6dc14a9516d7f04L, 0x1f5303d878905d2fL, -25L, 0L,
        0x9c0440896ce6515cL, 0xcf3470fa7f10ac3L, 0xa9dc80682c111a9dL, -59L, 1L,
    };

    /** C: atan2.c:166-197 {@code tint_t Q[30]}, five longs per row: {@code h, m, l, ex, sgn}; 30 rows (denominator). */
    private static final long[] ATAN2_Q = {
        0x82703f8b53112ecaL, 0x71ce6279d467aa45L, 0x19c9b28466b6fc0L, -10L, 0L,
        0xb3cf74b427d53e03L, 0xee590b839e751890L, 0xf2570b12bcb238cfL, -8L, 0L,
        0xb33c44af0d43c9e8L, 0xf4426f84614701dbL, 0x979e49dc5b858442L, -6L, 0L,
        0x8264d256f0bf4d61L, 0xee21431cc0f29764L, 0xce0cc1b593ac9952L, -4L, 0L,
        0x9d97c4947687b2eeL, 0xef4087c49dde5229L, 0x742bc6e02bfb6df8L, -3L, 0L,
        0xa0f888dc7aab949eL, 0xf623f19ac413db23L, 0x854744a164f70ecdL, -2L, 0L,
        0x8fd9c7172ab0828eL, 0x1a9f72fadafc3dfbL, 0xaf96a180ea297178L, -1L, 0L,
        0xe38168b78a51ee15L, 0x604a47815c8091ebL, 0x9faaa42312e8eb30L, -1L, 0L,
        0xa164ccdbb65ee133L, 0xe623433c5e086402L, 0xcb228afd335a245fL, 0L, 0L,
        0xced0c0cbe44fac2fL, 0xd7c2adfc7c5dda9eL, 0xa22b4a311cf5a5b1L, 0L, 0L,
        0xf0e6660438c90371L, 0x3dca1a0f58f0d943L, 0x8a92b9de68c4488cL, 0L, 0L,
        0x8000000000000000L, 0x0L, 0x0L, 1L, 0L,
        0xf8ea68f9ad849103L, 0xb257e4bfc606967fL, 0x818fa2816928c4aaL, 0L, 0L,
        0xddc64eeee3506234L, 0x47a33ea40a2b22e4L, 0xa6a2c129ace2470aL, 0L, 0L,
        0xb52ddfba2d37c8e2L, 0x24551a9b19fa064aL, 0x3d78c68b54bc3850L, 0L, 0L,
        0x87ac113d26b683a1L, 0x4e97756c38785b0bL, 0xf75f0a4506931ba8L, 0L, 0L,
        0xba065070b900f3d5L, 0xe49d8f9f238f1eebL, 0x6805d48d67f1dcd9L, -1L, 0L,
        0xe902d2a4bfc22441L, 0x945cfe6c83a5b828L, 0x519ebb457f8ccca0L, -2L, 0L,
        0x84e405c915f7769bL, 0xfaaf34c3c51dd39L, 0xa97fa0ae99382c93L, -2L, 0L,
        0x896da46c7e6cebb3L, 0xe0afc0b6db3dbbf5L, 0x61ccff6e78efff4L, -3L, 0L,
        0x801a0c1a0dfefc92L, 0xf8e0a97902e08d47L, 0x81898e0b7d8ef0bcL, -4L, 0L,
        0xd59d088930df07dbL, 0xa8cb00021849fbafL, 0xf80cc91aa0bddf89L, -6L, 0L,
        0x9db56a6984b77231L, 0xcf7c2074e0480959L, 0x3316ecaa6aa4604eL, -7L, 0L,
        0xcb7450d9d62d2e68L, 0x9787b4265977d004L, 0xba615cee00a841d7L, -9L, 0L,
        0xe12a52e02b64b23eL, 0x97c9139a8ced716cL, 0x70a176ad2b5f16d2L, -11L, 0L,
        0xd057b6d0900ed30dL, 0x5e04bb17eb879a5eL, 0x23ed135b60bea070L, -13L, 0L,
        0x9b1f1ca22fef7ae5L, 0x19a687f0a4073eeeL, 0x5a9ccbc8c14578bL, -15L, 0L,
        0xaeba710f053aac3bL, 0x30ea5d6fb116f775L, 0x8a9bcfef5698f8f1L, -18L, 0L,
        0x84b9ada076cd007dL, 0x13e55006073f76b1L, 0x4cf4259968228f3eL, -21L, 0L,
        0xcca4b9046786937dL, 0x233c1e691c127c1fL, 0xf493e0b7a3de459aL, -26L, 0L,
    };

    /** C: tint.h:62-63 {@code ZERO}: h, m, l, ex, sgn. */
    private static final long[] TINT_ZERO = {0L, 0L, 0L, -1076L, 0L};

    /** C: tint.h:65-66 {@code ONE}. */
    private static final long[] TINT_ONE = {0x8000000000000000L, 0L, 0L, 1L, 0L};

    /** C: tint.h:69-71 {@code PI}, error < 2^-196.96. */
    private static final long[] TINT_PI = {0xc90fdaa22168c234L, 0xc4c6628b80dc1cd1L, 0x29024e088a67cc74L, 2L, 0L};

    /** C: tint.h:74-76 {@code PI2} (pi/2), error < 2^-197.96. */
    private static final long[] TINT_PI2 = {0xc90fdaa22168c234L, 0xc4c6628b80dc1cd1L, 0x29024e088a67cc74L, 1L, 0L};

    // ------------------------------------------------------------------------------------------
    // Read access
    // ------------------------------------------------------------------------------------------

    /** {@code A[2*j + k]} of {@code atan.c}. */
    static double atanA(int flatIndex) {
        return ATAN_A[flatIndex];
    }

    /** {@code c[3*i + k]} of {@code atan.c}. */
    static int atanC(int flatIndex) {
        return ATAN_C[flatIndex];
    }

    /** {@code ch[i]} of {@code cr_atan}. */
    static double atanCh(int i) {
        return ATAN_CH[i];
    }

    /** {@code ch2[i]} of {@code cr_atan}. */
    static double atanCh2(int i) {
        return ATAN_CH2[i];
    }

    /** {@code ch[2*i + k]} of {@code as_atan_refine2}. */
    static double atanR2Ch(int flatIndex) {
        return ATAN_R2_CH[flatIndex];
    }

    /** {@code cl[i]} of {@code as_atan_refine2}. */
    static double atanR2Cl(int i) {
        return ATAN_R2_CL[i];
    }

    /** {@code db[3*j + k]} of {@code as_atan_refine2}. */
    static double atanDb(int flatIndex) {
        return ATAN_DB[flatIndex];
    }

    /** Number of rows of {@code db} in {@code as_atan_refine2} (C: {@code sizeof(db)/sizeof(db[0])}). */
    static int atanDbRows() {
        return ATAN_DB.length / 3;
    }

    /** {@code cc[8*j + k]} of {@code asin.c} and {@code acos.c}. */
    static double asinAcosCc(int flatIndex) {
        return ASINACOS_CC[flatIndex];
    }

    /** {@code s[2*j + k]} of {@code asin.c} and {@code acos.c}: k = 0 the low word, k = 1 the high word. */
    static double asinAcosS(int flatIndex) {
        return ASINACOS_S[flatIndex];
    }

    /** {@code c[2*i + k]} of {@code asin.c} and {@code acos.c}. */
    static double asinAcosC(int flatIndex) {
        return ASINACOS_C[flatIndex];
    }

    /** {@code ct[i]} of {@code asin.c} and {@code acos.c}. */
    static double asinAcosCt(int i) {
        return ASINACOS_CT[i];
    }

    /** {@code off[2*k + c]} of {@code asin.c}. */
    static double asinOff(int flatIndex) {
        return ASIN_OFF[flatIndex];
    }

    /** {@code db[3*j + k]} of {@code as_asin_database}. */
    static double asinDb(int flatIndex) {
        return ASIN_DB[flatIndex];
    }

    /** Number of rows of {@code db} in {@code as_asin_database} (C: {@code sizeof(db)/sizeof(db[0])}). */
    static int asinDbRows() {
        return ASIN_DB.length / 3;
    }

    /** {@code off[2*k + c]} of {@code acos.c}. */
    static double acosOff(int flatIndex) {
        return ACOS_OFF[flatIndex];
    }

    /** {@code T2[i]} of {@code atan2.c}. */
    static double atan2T2(int i) {
        return ATAN2_T2[i];
    }

    /** {@code f2[2*jt + k]} of {@code atan2.c}. */
    static double atan2F2(int flatIndex) {
        return ATAN2_F2[flatIndex];
    }

    /** {@code O[2*kw + k]} of {@code atan2.c}. */
    static double atan2O(int flatIndex) {
        return ATAN2_O[flatIndex];
    }

    /** {@code b[i]} of {@code cr_atan2}. */
    static double atan2B(int i) {
        return ATAN2_B[i];
    }

    /** {@code b2[i]} of {@code cr_atan2}. */
    static double atan2B2(int i) {
        return ATAN2_B2[i];
    }

    /** {@code finf[2*k + c]} of {@code as_atan2_special}. */
    static double atan2Finf(int flatIndex) {
        return ATAN2_FINF[flatIndex];
    }

    /** Loads row {@code row} (0..29) of {@code P} into {@code t}. */
    static void atan2P(int row, CoreMathTint.Tint t) {
        load(ATAN2_P, row, t);
    }

    /** Loads row {@code row} (0..29) of {@code Q} into {@code t}. */
    static void atan2Q(int row, CoreMathTint.Tint t) {
        load(ATAN2_Q, row, t);
    }

    private static void load(long[] table, int row, CoreMathTint.Tint t) {
        int k = 5 * row;
        t.h = table[k];
        t.m = table[k + 1];
        t.l = table[k + 2];
        t.ex = table[k + 3];
        t.sgn = table[k + 4];
    }

    /** A fresh copy of {@code ZERO} as {@code h, m, l, ex, sgn}. */
    static long[] tintZero() {
        return TINT_ZERO.clone();
    }

    /** A fresh copy of {@code ONE}. */
    static long[] tintOne() {
        return TINT_ONE.clone();
    }

    /** A fresh copy of {@code PI}. */
    static long[] tintPi() {
        return TINT_PI.clone();
    }

    /** A fresh copy of {@code PI2}. */
    static long[] tintPi2() {
        return TINT_PI2.clone();
    }

    private static long[] bits(double[] d) {
        long[] out = new long[d.length];
        for (int i = 0; i < d.length; i++) {
            out[i] = Double.doubleToRawLongBits(d[i]);
        }
        return out;
    }

    private static long[] widen(int[] v) {
        long[] out = new long[v.length];
        for (int i = 0; i < v.length; i++) {
            out[i] = v[i];
        }
        return out;
    }

    /**
     * Every table as a name and a COPY of its values: a {@code double} as its raw bits, an {@code int} or a
     * {@code long} as it is. The iteration order is fixed (the order of the declarations above). Used by the digest
     * test and by the test that compares the tables with the C source; nothing in the ports calls it.
     */
    static Map<String, long[]> snapshot() {
        Map<String, long[]> m = new LinkedHashMap<>();
        m.put("ATAN_A", bits(ATAN_A));
        m.put("ATAN_C", widen(ATAN_C));
        m.put("ATAN_CH", bits(ATAN_CH));
        m.put("ATAN_CH2", bits(ATAN_CH2));
        m.put("ATAN_R2_CH", bits(ATAN_R2_CH));
        m.put("ATAN_R2_CL", bits(ATAN_R2_CL));
        m.put("ATAN_DB", bits(ATAN_DB));
        m.put("ASINACOS_CC", bits(ASINACOS_CC));
        m.put("ASINACOS_S", bits(ASINACOS_S));
        m.put("ASINACOS_C", bits(ASINACOS_C));
        m.put("ASINACOS_CT", bits(ASINACOS_CT));
        m.put("ASIN_OFF", bits(ASIN_OFF));
        m.put("ASIN_DB", bits(ASIN_DB));
        m.put("ACOS_OFF", bits(ACOS_OFF));
        m.put("ATAN2_T2", bits(ATAN2_T2));
        m.put("ATAN2_F2", bits(ATAN2_F2));
        m.put("ATAN2_O", bits(ATAN2_O));
        m.put("ATAN2_B", bits(ATAN2_B));
        m.put("ATAN2_B2", bits(ATAN2_B2));
        m.put("ATAN2_FINF", bits(ATAN2_FINF));
        m.put("ATAN2_P", ATAN2_P.clone());
        m.put("ATAN2_Q", ATAN2_Q.clone());
        m.put("TINT_ZERO", TINT_ZERO.clone());
        m.put("TINT_ONE", TINT_ONE.clone());
        m.put("TINT_PI", TINT_PI.clone());
        m.put("TINT_PI2", TINT_PI2.clone());
        return m;
    }
}
