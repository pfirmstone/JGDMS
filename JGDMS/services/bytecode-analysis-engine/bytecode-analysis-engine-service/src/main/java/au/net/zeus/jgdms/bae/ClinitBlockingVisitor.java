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
package au.net.zeus.jgdms.bae;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import au.net.zeus.jgdms.api.codebase.ClinitVerdict;

/**
 * ASM-based visitor that analyses a single class's {@code <clinit>} (static
 * initializer) method for blocking call paths.
 *
 * <p>The visitor is used in two phases by {@link JarAnalyzer}:
 * <ol>
 *   <li><strong>Index phase</strong> — {@link #indexClass(byte[], Map)} is
 *       called for every {@code .class} entry in the JAR.  It populates the
 *       shared call-graph map: for each class, the set of
 *       {@code "owner/name/descriptor"} triples directly called from
 *       {@code <clinit>}.</li>
 *   <li><strong>Analysis phase</strong> — {@link #analyzeClinitReachability}
 *       performs a BFS from the root class's {@code <clinit>} through the
 *       call graph, up to a configurable depth, looking for calls that reach
 *       a {@link BlockingSinkRegistry#BLOCKING_SINKS known blocking sink} or an
 *       unregistered native method.</li>
 * </ol>
 *
 * <p>Thread safety: this class is stateless; all state is passed as method
 * arguments.  Multiple threads may safely call the static methods concurrently
 * as long as they use separate {@code callGraph} maps.
 *
 * @see JarAnalyzer
 * @see BlockingSinkRegistry
 * @since 3.1.1
 * @author Peter Firmstone
 * @author GitHub Copilot
 */
final class ClinitBlockingVisitor {

    /** ASM opcode set flag. */
    private static final int ASM_API = Opcodes.ASM9;

    private ClinitBlockingVisitor() { }

    // -------------------------------------------------------------------------
    // Phase 1: index a class into the call graph
    // -------------------------------------------------------------------------

    /**
     * Parses {@code classBytes} using ASM and records, in {@code callGraph},
     * the set of call sites directly invoked from the class's {@code <clinit>}
     * method.  Each call site is stored as {@code "owner/name/descriptor"}.
     *
     * <p>Also records whether each method in the class is {@code native}
     * ({@code isNativeMap}).
     *
     * <p>This method is fail-safe: if ASM cannot parse the class, no entry is
     * added to the call graph for this class — the class will be treated as
     * having no {@code <clinit>}, and the caller should record a
     * parse-failure flag separately.
     *
     * @param classBytes raw bytes of a single {@code .class} entry
     * @param callGraph  shared call-graph map to populate;
     *                   key = {@code "owner/methodName/descriptor"},
     *                   value = set of call sites invoked from that method
     * @param isNativeMap shared map of native methods;
     *                    key = {@code "owner/methodName/descriptor"},
     *                    value = {@code true}
     * @param clinitOwner output single-element array; set to the internal
     *                    class name of the parsed class (for the caller's use)
     */
    static void indexClass(byte[] classBytes,
                           Map<String, Set<String>> callGraph,
                           Map<String, Boolean> isNativeMap,
                           String[] clinitOwner) {
        try {
            ClassReader cr = new ClassReader(classBytes);
            ClinitIndexVisitor visitor =
                    new ClinitIndexVisitor(callGraph, isNativeMap, clinitOwner);
            cr.accept(visitor, ClassReader.SKIP_FRAMES | ClassReader.SKIP_DEBUG);
        } catch (Exception ignored) {
            // Malformed class — caller handles by treating as no-clinit
        }
    }

    // -------------------------------------------------------------------------
    // Phase 2: BFS reachability analysis from <clinit>
    // -------------------------------------------------------------------------

    /**
     * Performs a BFS from {@code rootClass + "/<clinit>/()V"} through the
     * {@code callGraph} up to {@code maxDepth} hops.  Returns the
     * {@link ClinitVerdict} and the shortest path to the first blocking sink,
     * if any.
     *
     * @param rootClass  the internal binary class name to start from
     * @param callGraph  populated by {@link #indexClass} for all JAR classes
     * @param isNativeMap populated by {@link #indexClass} for all JAR classes
     * @param maxDepth   maximum BFS depth (see {@link
     *                   au.net.zeus.jgdms.api.codebase.AnalysisRequest#getMaxBfsDepth()})
     * @return a two-element array: {@code [verdict, path]}, where
     *         {@code verdict} is a {@link ClinitVerdict} and {@code path} is
     *         a {@link List}&lt;String&gt; of call-site triples leading to the
     *         blocking sink (empty for {@code CLEAN} / {@code NATIVE_OPACITY}
     *         / {@code CYCLE})
     */
    static ClinitAnalysisResult analyzeClinitReachability(
            String rootClass,
            Map<String, Set<String>> callGraph,
            Map<String, Boolean> isNativeMap,
            int maxDepth) {

        String clinitKey = rootClass + "/<clinit>/()V";

        // If there is no <clinit> at all, it's clean.
        if (!callGraph.containsKey(clinitKey)) {
            return new ClinitAnalysisResult(ClinitVerdict.CLEAN,
                    Collections.<String>emptyList());
        }

        // BFS: each node is a method key; we track the path to each node.
        Set<String> visited   = new HashSet<String>();
        Deque<BfsNode> queue  = new ArrayDeque<BfsNode>();

        List<String> initialPath = new ArrayList<String>();
        initialPath.add(clinitKey);
        queue.add(new BfsNode(clinitKey, initialPath));
        visited.add(clinitKey);

        while (!queue.isEmpty()) {
            BfsNode current = queue.poll();
            if (current.depth > maxDepth) continue;

            Set<String> callees = callGraph.get(current.key);
            if (callees == null) continue;

            for (String callee : callees) {
                if (visited.contains(callee)) continue;
                visited.add(callee);

                // Decompose the key into owner/name/descriptor
                int lastSlash = callee.lastIndexOf('/');
                int prevSlash = (lastSlash > 0)
                        ? callee.lastIndexOf('/', lastSlash - 1) : -1;
                if (prevSlash < 0) continue; // malformed key

                String descriptor = callee.substring(lastSlash + 1);
                String ownerAndName = callee.substring(0, lastSlash);
                int nameSlash = ownerAndName.lastIndexOf('/');
                if (nameSlash < 0) continue;
                String owner = ownerAndName.substring(0, nameSlash);
                String name  = ownerAndName.substring(nameSlash + 1);

                List<String> newPath = new ArrayList<String>(current.path);
                newPath.add(callee);

                // Check blocking sink — determine if the path to this blocker
                // is defended by a permission guard (BLOCKING_GUARDED) or not
                // (BLOCKING).
                if (BlockingSinkRegistry.isBlocking(owner, name, descriptor)) {
                    ClinitVerdict v = hasPermissionGuardOnPath(newPath, callGraph)
                            ? ClinitVerdict.BLOCKING_GUARDED
                            : ClinitVerdict.BLOCKING;
                    return new ClinitAnalysisResult(v, newPath);
                }

                // Check unregistered native
                Boolean isNative = isNativeMap.get(callee);
                if (Boolean.TRUE.equals(isNative)
                        && !BlockingSinkRegistry.isSafeNative(owner, name, descriptor)) {
                    return new ClinitAnalysisResult(ClinitVerdict.NATIVE_OPACITY, newPath);
                }

                // Continue BFS if callee has its own call graph entry
                if (callGraph.containsKey(callee) && current.depth < maxDepth) {
                    queue.add(new BfsNode(callee, newPath, current.depth + 1));
                }
            }
        }

        return new ClinitAnalysisResult(ClinitVerdict.CLEAN,
                Collections.<String>emptyList());
    }

    /**
     * Returns {@code true} if any method on the given call path (excluding the
     * final element, which is the blocking sink itself) has at least one
     * direct callee that is a permission guard — a {@code SecurityManager}
     * {@code check*} method or {@code AccessController.checkPermission}.
     *
     * <p>This is a conservative <em>sibling-call</em> heuristic: if method M
     * on the path calls {@code sm.checkFoo()} <em>and</em> eventually reaches
     * a blocking sink, the blocking path is considered guarded regardless of
     * whether the check appears before or after the blocking call in execution
     * order (which cannot be determined statically from a call graph alone).
     *
     * @param path      the BFS path from {@code <clinit>} to the blocking sink
     * @param callGraph the indexed call graph
     * @return {@code true} if a permission guard is present on the path
     */
    private static boolean hasPermissionGuardOnPath(
            List<String> path,
            Map<String, Set<String>> callGraph) {

        // Skip the last element (the blocking sink itself).
        int limit = path.size() - 1;
        for (int i = 0; i < limit; i++) {
            Set<String> siblings = callGraph.get(path.get(i));
            if (siblings == null) continue;
            for (String sibling : siblings) {
                if (BlockingSinkRegistry.isPermissionGuardKey(sibling)) {
                    return true;
                }
            }
        }
        return false;
    }

    // -------------------------------------------------------------------------
    // Cycle detection — separate pass
    // -------------------------------------------------------------------------

    /**
     * Detects circular {@code <clinit>} dependency cycles across all classes
     * in the JAR.  Returns the set of class names that participate in at least
     * one cycle.
     *
     * <p>Two classes A and B form a cycle when A's {@code <clinit>} (directly
     * or indirectly) calls a method on B that triggers B's {@code <clinit>},
     * and B's {@code <clinit>} similarly depends on A.
     *
     * <p>Implementation: builds a directed graph from each class's
     * {@code <clinit>} to the <em>owners</em> of all methods it calls (since
     * referencing a class's member triggers that class's {@code <clinit>}).
     * Then uses Tarjan's SCC algorithm to find SCCs of size ≥ 2.
     *
     * @param callGraph populated by repeated calls to {@link #indexClass}
     * @return set of internal class names that are members of a cycle;
     *         empty if no cycles exist
     */
    static Set<String> detectClinitCycles(Map<String, Set<String>> callGraph) {
        // Build class-level dependency graph from <clinit> entries.
        // Nodes = internal class names.
        // Edge A → B if A's <clinit> calls any method owned by B
        //   and B has a <clinit> (to avoid spurious edges to e.g. java.lang.*)
        Map<String, Set<String>> classDeps = new HashMap<String, Set<String>>();
        for (Map.Entry<String, Set<String>> entry : callGraph.entrySet()) {
            String methodKey = entry.getKey();
            if (!methodKey.endsWith("/<clinit>/()V")) continue;

            String fromClass = methodKey.substring(0,
                    methodKey.length() - "/<clinit>/()V".length());
            Set<String> deps = new HashSet<String>();
            classDeps.put(fromClass, deps);

            for (String callee : entry.getValue()) {
                // callee = "owner/name/descriptor" — extract owner
                int nameSlash = callee.lastIndexOf('/');
                if (nameSlash < 0) continue;
                String ownerAndName = callee.substring(0, nameSlash);
                int ownerSlash = ownerAndName.lastIndexOf('/');
                if (ownerSlash < 0) continue;
                String toClass = ownerAndName.substring(0, ownerSlash);
                if (!toClass.equals(fromClass)
                        && callGraph.containsKey(toClass + "/<clinit>/()V")) {
                    deps.add(toClass);
                }
            }
        }

        // Tarjan's SCC
        TarjanScc tarjan = new TarjanScc(classDeps);
        return tarjan.findCyclicNodes();
    }

    // -------------------------------------------------------------------------
    // Result holder
    // -------------------------------------------------------------------------

    /** Simple result holder returned by {@link #analyzeClinitReachability}. */
    static final class ClinitAnalysisResult {
        final ClinitVerdict verdict;
        /** Path to the blocking sink, or the single native-opaque callee. */
        final List<String>  callPath;

        ClinitAnalysisResult(ClinitVerdict verdict, List<String> callPath) {
            this.verdict  = verdict;
            this.callPath = callPath;
        }
    }

    // -------------------------------------------------------------------------
    // BFS node
    // -------------------------------------------------------------------------

    private static final class BfsNode {
        final String       key;
        final List<String> path;
        final int          depth;

        BfsNode(String key, List<String> path) {
            this(key, path, 0);
        }

        BfsNode(String key, List<String> path, int depth) {
            this.key   = key;
            this.path  = path;
            this.depth = depth;
        }
    }

    // -------------------------------------------------------------------------
    // ASM ClassVisitor: indexes a single class's call graph
    // -------------------------------------------------------------------------

    private static final class ClinitIndexVisitor extends ClassVisitor {

        private final Map<String, Set<String>> callGraph;
        private final Map<String, Boolean>     isNativeMap;
        private final String[]                 clinitOwner;
        private String                         className;

        ClinitIndexVisitor(Map<String, Set<String>> callGraph,
                           Map<String, Boolean> isNativeMap,
                           String[] clinitOwner) {
            super(ASM_API);
            this.callGraph   = callGraph;
            this.isNativeMap = isNativeMap;
            this.clinitOwner = clinitOwner;
        }

        @Override
        public void visit(int version, int access, String name, String signature,
                          String superName, String[] interfaces) {
            this.className = name;
            if (clinitOwner != null && clinitOwner.length > 0) {
                clinitOwner[0] = name;
            }
        }

        @Override
        public MethodVisitor visitMethod(int access, String name, String descriptor,
                                         String signature, String[] exceptions) {
            String methodKey = className + "/" + name + "/" + descriptor;

            // Track native methods
            if ((access & Opcodes.ACC_NATIVE) != 0) {
                isNativeMap.put(methodKey, Boolean.TRUE);
            }

            // Only index methods that have a body (i.e. not abstract/native stubs)
            // However we still need to record calls from ALL methods so that
            // BFS can follow them.  We index every method here.
            Set<String> callees = new HashSet<String>();
            callGraph.put(methodKey, callees);

            return new MethodCallIndexer(ASM_API, callees);
        }
    }

    // -------------------------------------------------------------------------
    // ASM MethodVisitor: records call sites
    // -------------------------------------------------------------------------

    private static final class MethodCallIndexer extends MethodVisitor {

        private final Set<String> callees;

        MethodCallIndexer(int api, Set<String> callees) {
            super(api);
            this.callees = callees;
        }

        @Override
        public void visitMethodInsn(int opcode, String owner, String name,
                                     String descriptor, boolean isInterface) {
            callees.add(owner + "/" + name + "/" + descriptor);
        }
    }

    // -------------------------------------------------------------------------
    // Tarjan's SCC algorithm (iterative, to avoid stack overflow)
    // -------------------------------------------------------------------------

    private static final class TarjanScc {

        private final Map<String, Set<String>> graph;
        private final Map<String, Integer>     index  = new HashMap<String, Integer>();
        private final Map<String, Integer>     lowlink = new HashMap<String, Integer>();
        private final Map<String, Boolean>     onStack = new HashMap<String, Boolean>();
        private final Deque<String>            stack   = new ArrayDeque<String>();
        private int                            nextIndex = 0;
        private final Set<String>              cyclicNodes = new LinkedHashSet<String>();

        TarjanScc(Map<String, Set<String>> graph) {
            this.graph = graph;
        }

        Set<String> findCyclicNodes() {
            for (String node : graph.keySet()) {
                if (!index.containsKey(node)) {
                    strongConnect(node);
                }
            }
            return Collections.unmodifiableSet(cyclicNodes);
        }

        private void strongConnect(String startNode) {
            // Iterative Tarjan's using an explicit call stack to avoid
            // Java stack overflow on deep graphs.
            Deque<Frame> callStack = new ArrayDeque<Frame>();
            callStack.push(new Frame(startNode, new ArrayList<String>(
                    graph.containsKey(startNode)
                    ? graph.get(startNode)
                    : Collections.<String>emptySet())));

            index.put(startNode, nextIndex);
            lowlink.put(startNode, nextIndex);
            nextIndex++;
            stack.push(startNode);
            onStack.put(startNode, Boolean.TRUE);

            while (!callStack.isEmpty()) {
                Frame frame = callStack.peek();
                String v = frame.node;

                if (frame.hasNext()) {
                    String w = frame.next();
                    if (!index.containsKey(w)) {
                        // Recurse on w
                        index.put(w, nextIndex);
                        lowlink.put(w, nextIndex);
                        nextIndex++;
                        stack.push(w);
                        onStack.put(w, Boolean.TRUE);
                        callStack.push(new Frame(w, new ArrayList<String>(
                                graph.containsKey(w)
                                ? graph.get(w)
                                : Collections.<String>emptySet())));
                    } else if (Boolean.TRUE.equals(onStack.get(w))) {
                        int wIdx = index.get(w);
                        int vLow = lowlink.get(v);
                        if (wIdx < vLow) lowlink.put(v, wIdx);
                    }
                } else {
                    // Done with v's children
                    callStack.pop();
                    if (!callStack.isEmpty()) {
                        String parent = callStack.peek().node;
                        int vLow    = lowlink.get(v);
                        int parLow  = lowlink.get(parent);
                        if (vLow < parLow) lowlink.put(parent, vLow);
                    }
                    // Check if v is root of an SCC
                    if (lowlink.get(v).equals(index.get(v))) {
                        List<String> scc = new ArrayList<String>();
                        String w;
                        do {
                            w = stack.pop();
                            onStack.put(w, Boolean.FALSE);
                            scc.add(w);
                        } while (!w.equals(v));
                        if (scc.size() >= 2) {
                            cyclicNodes.addAll(scc);
                        }
                    }
                }
            }
        }

        private static final class Frame {
            final String       node;
            final List<String> children;
            int                pos = 0;

            Frame(String node, List<String> children) {
                this.node     = node;
                this.children = children;
            }

            boolean hasNext() { return pos < children.size(); }
            String  next()    { return children.get(pos++); }
        }
    }
}
