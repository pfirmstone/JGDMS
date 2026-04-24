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

package net.jini.lookup;

import java.io.DataInputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Default {@link ProxyBytecodeAnalyzer} implementation that scans a class's
 * constant pool for references to methods or fields considered dangerous.
 *
 * <p>The analysis is purely structural: it reads the constant-pool entries
 * (tags {@code CONSTANT_Methodref}, {@code CONSTANT_InterfaceMethodref}, and
 * {@code CONSTANT_Fieldref}) and checks whether the referenced class and
 * name-and-type combination matches any entry in the configured blacklist.
 * No class loading or bytecode execution is performed.</p>
 *
 * <p>The default blacklist contains the following method references:</p>
 * <ul>
 *   <li>{@code java/lang/Runtime.exec}</li>
 *   <li>{@code java/lang/ProcessBuilder.start}</li>
 *   <li>{@code java/io/FileOutputStream.<init>}</li>
 *   <li>{@code java/io/FileWriter.<init>}</li>
 *   <li>{@code java/nio/file/Files.write}</li>
 *   <li>{@code java/nio/file/Files.newOutputStream}</li>
 *   <li>{@code java/nio/file/Files.newByteChannel}</li>
 *   <li>{@code java/lang/reflect/Method.invoke}</li>
 *   <li>{@code java/lang/Class.forName}</li>
 *   <li>{@code java/lang/ClassLoader.loadClass}</li>
 * </ul>
 *
 * <p>Each blacklist entry is expressed as
 * {@code "internalClassName.methodOrFieldName"}, for example
 * {@code "java/lang/Runtime.exec"}.  Custom blacklists are passed to the
 * constructor and <em>replace</em> the default set (they are not merged with
 * it).</p>
 *
 * <p>Instances of this class are thread-safe.</p>
 *
 * @see ProxyBytecodeAnalyzer
 * @see ProxyIsolationFilter
 * @since 3.1
 */
public class StandardProxyBytecodeAnalyzer extends ProxyBytecodeAnalyzer {

    private static final Logger logger =
            Logger.getLogger(StandardProxyBytecodeAnalyzer.class.getName());

    /** Default set of dangerous method/field references (internal form). */
    private static final Set<String> DEFAULT_BLACKLIST =
            Collections.unmodifiableSet(new HashSet<String>(Arrays.asList(
                    "java/lang/Runtime.exec",
                    "java/lang/ProcessBuilder.start",
                    "java/io/FileOutputStream.<init>",
                    "java/io/FileWriter.<init>",
                    "java/nio/file/Files.write",
                    "java/nio/file/Files.newOutputStream",
                    "java/nio/file/Files.newByteChannel",
                    "java/lang/reflect/Method.invoke",
                    "java/lang/Class.forName",
                    "java/lang/ClassLoader.loadClass"
            )));

    // Java class-file constant pool tags (JVMS Table 4.4-B)
    private static final int CONSTANT_UTF8                =  1;
    private static final int CONSTANT_INTEGER             =  3;
    private static final int CONSTANT_FLOAT               =  4;
    private static final int CONSTANT_LONG                =  5;
    private static final int CONSTANT_DOUBLE              =  6;
    private static final int CONSTANT_CLASS               =  7;
    private static final int CONSTANT_STRING              =  8;
    private static final int CONSTANT_FIELDREF            =  9;
    private static final int CONSTANT_METHODREF           = 10;
    private static final int CONSTANT_INTERFACE_METHODREF = 11;
    private static final int CONSTANT_NAME_AND_TYPE       = 12;
    private static final int CONSTANT_METHOD_HANDLE       = 15;
    private static final int CONSTANT_METHOD_TYPE         = 16;
    private static final int CONSTANT_INVOKE_DYNAMIC      = 18;

    private final Set<String> blacklist;

    /**
     * Creates an analyzer using the default blacklist.
     */
    public StandardProxyBytecodeAnalyzer() {
        this.blacklist = DEFAULT_BLACKLIST;
    }

    /**
     * Creates an analyzer using the supplied blacklist entries.
     *
     * <p>Each entry must be of the form
     * {@code "internalClassName.memberName"}, using the JVM internal class
     * name format (slash-separated), for example
     * {@code "java/lang/Runtime.exec"}.</p>
     *
     * @param blacklistEntries the set of dangerous member references; must
     *        not be {@code null} and must not contain {@code null} elements.
     */
    public StandardProxyBytecodeAnalyzer(Collection<String> blacklistEntries) {
        if (blacklistEntries == null) throw new NullPointerException("blacklistEntries");
        this.blacklist = Collections.unmodifiableSet(
                new HashSet<String>(blacklistEntries));
    }

    /**
     * {@inheritDoc}
     *
     * <p>Scans the constant pool of {@code classBytes} for references that
     * match the configured blacklist.  Returns {@link AnalysisVerdict#UNSAFE}
     * at the first match, {@link AnalysisVerdict#SAFE} if no match is found,
     * or {@link AnalysisVerdict#INCONCLUSIVE} if the bytes cannot be
     * parsed.</p>
     */
    @Override
    public AnalysisVerdict analyze(String className,
                                   byte[] classBytes,
                                   String codebase) {
        if (className == null)  throw new NullPointerException("className");
        if (classBytes == null) throw new NullPointerException("classBytes");

        try {
            return scanConstantPool(className, classBytes);
        } catch (IOException e) {
            logger.log(Level.WARNING,
                    "Could not parse class bytes for {0}: {1}",
                    new Object[]{className, e.getMessage()});
            return AnalysisVerdict.INCONCLUSIVE;
        }
    }

    /**
     * Parses the constant pool of the class file and checks every
     * {@code Methodref}, {@code InterfaceMethodref} and {@code Fieldref}
     * entry against the blacklist.
     */
    private AnalysisVerdict scanConstantPool(String className, byte[] classBytes)
            throws IOException {
        DataInputStream dis =
                new DataInputStream(new java.io.ByteArrayInputStream(classBytes));

        // magic
        int magic = dis.readInt();
        if (magic != 0xCAFEBABE) {
            logger.log(Level.WARNING,
                    "Class bytes for {0} do not start with 0xCAFEBABE", className);
            return AnalysisVerdict.INCONCLUSIVE;
        }

        dis.readUnsignedShort(); // minor_version
        dis.readUnsignedShort(); // major_version

        int cpCount = dis.readUnsignedShort(); // constant_pool_count

        // Arrays indexed 1 .. cpCount-1
        String[] utf8Pool      = new String[cpCount];
        int[]    classNameIdx  = new int[cpCount];   // class_index for Class entries
        int[]    natClass      = new int[cpCount];   // class_index for Ref entries
        int[]    natNameType   = new int[cpCount];   // name_and_type_index for Ref
        int[]    natName       = new int[cpCount];   // name_index of NameAndType
        boolean[] isRef        = new boolean[cpCount]; // Methodref / InterfaceMethodref / Fieldref

        for (int i = 1; i < cpCount; i++) {
            int tag = dis.readUnsignedByte();
            switch (tag) {
                case CONSTANT_UTF8:
                    utf8Pool[i] = dis.readUTF();
                    break;
                case CONSTANT_INTEGER:
                case CONSTANT_FLOAT:
                    dis.readInt();
                    break;
                case CONSTANT_LONG:
                case CONSTANT_DOUBLE:
                    dis.readLong();
                    i++; // takes two slots
                    break;
                case CONSTANT_CLASS:
                    classNameIdx[i] = dis.readUnsignedShort();
                    break;
                case CONSTANT_STRING:
                    dis.readUnsignedShort();
                    break;
                case CONSTANT_FIELDREF:
                case CONSTANT_METHODREF:
                case CONSTANT_INTERFACE_METHODREF:
                    natClass[i]    = dis.readUnsignedShort();
                    natNameType[i] = dis.readUnsignedShort();
                    isRef[i] = true;
                    break;
                case CONSTANT_NAME_AND_TYPE:
                    natName[i] = dis.readUnsignedShort();
                    dis.readUnsignedShort(); // descriptor_index (ignored)
                    break;
                case CONSTANT_METHOD_HANDLE:
                    dis.readUnsignedByte();
                    dis.readUnsignedShort();
                    break;
                case CONSTANT_METHOD_TYPE:
                    dis.readUnsignedShort();
                    break;
                case CONSTANT_INVOKE_DYNAMIC:
                    dis.readUnsignedShort();
                    dis.readUnsignedShort();
                    break;
                default:
                    // Unknown tag — return INCONCLUSIVE (be conservative)
                    logger.log(Level.WARNING,
                            "Unknown constant pool tag {0} in class {1}",
                            new Object[]{tag, className});
                    return AnalysisVerdict.INCONCLUSIVE;
            }
        }

        // Now resolve the ref entries we collected
        for (int i = 1; i < cpCount; i++) {
            if (!isRef[i]) continue;

            int classRefIdx = natClass[i];
            int nameTypeIdx = natNameType[i];
            if (classRefIdx == 0 || nameTypeIdx == 0) continue;

            int classUtf8Idx = classNameIdx[classRefIdx];
            int nameIdx      = natName[nameTypeIdx];
            if (classUtf8Idx == 0 || nameIdx == 0) continue;

            String refClass = utf8Pool[classUtf8Idx];
            String refName  = utf8Pool[nameIdx];
            if (refClass == null || refName == null) continue;

            String candidate = refClass + "." + refName;
            if (blacklist.contains(candidate)) {
                logger.log(Level.INFO,
                        "Proxy class {0} references blacklisted member: {1}",
                        new Object[]{className, candidate});
                return AnalysisVerdict.UNSAFE;
            }
        }

        return AnalysisVerdict.SAFE;
    }
}
