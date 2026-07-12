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
import java.util.logging.Level;
import java.util.logging.Logger;
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

    private static final Logger logger =
            Logger.getLogger(ClinitBlockingVisitor.class.getName());

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
     * <p><strong>Fail-secure contract.</strong> {@code visit()} — ASM's first
     * callback — sets {@code clinitOwner[0]} before any method body has been
     * parsed.  A class crafted to parse its header cleanly and then throw
     * mid-method-body (e.g. a {@link StackOverflowError} from ASM's unbounded
     * annotation-element-value recursion) must not leave the caller believing
     * the class was fully indexed with {@code clinitOwner[0]} non-null: doing
     * so would let a BFS run over a partial, truncated call graph whose
     * blocking-sink edges were never recorded, yielding a false {@code CLEAN}
     * verdict.  So on <em>any</em> {@link Throwable} thrown while parsing:
     * <ul>
     *   <li>{@code clinitOwner[0]} is reset to {@code null}, undoing whatever
     *       {@code visit()} set, so the caller's existing
     *       {@code clinitOwner[0] == null} check correctly detects the
     *       failure;</li>
     *   <li>any {@code callGraph}/{@code isNativeMap} entries added for this
     *       class before the throw are purged, so no half-recorded state
     *       survives into the BFS phase;</li>
     *   <li>this method returns {@code false} (in addition to the
     *       {@code clinitOwner[0] == null} signal) so a failure can never be
     *       silently missed by a caller that only checks the return value.</li>
     * </ul>
     * The caller ({@link JarAnalyzer}) is expected to route both signals
     * through its existing parse-failure path (fail-secure verdict), exactly
     * as it already does for a class ASM cannot parse at all.
     *
     * <p>{@link StackOverflowError} is caught and swallowed (the stack is
     * fully unwound at this catch frame, so it is safe to continue analysing
     * the rest of the JAR).  Other {@link VirtualMachineError}s (e.g.
     * {@link OutOfMemoryError}) are <em>not</em> swallowed — they are more
     * likely genuine infra pressure than crafted input, and continuing
     * analysis after one is unreliable, so they propagate to the caller.
     * Every swallowed {@link Throwable} is logged at {@code WARNING} so
     * operators retain the signal that a class failed to fully analyze, even
     * though the overall verdict is correctly fail-secure.
     *
     * @param classBytes raw bytes of a single {@code .class} entry
     * @param callGraph  shared call-graph map to populate;
     *                   key = {@code "owner/methodName/descriptor"},
     *                   value = set of call sites invoked from that method
     * @param isNativeMap shared map of native methods;
     *                    key = {@code "owner/methodName/descriptor"},
     *                    value = {@code true}
     * @param clinitOwner output single-element array; set to the internal
     *                    class name of the parsed class on success, or reset
     *                    to {@code null} if parsing failed partway through
     * @return {@code true} if the class was fully indexed; {@code false} if
     *         any {@link Throwable} was caught while parsing (a fail-secure
     *         signal — the caller must treat this the same as a parse
     *         failure)
     */
    static boolean indexClass(byte[] classBytes,
                           Map<String, Set<String>> callGraph,
                           Map<String, Boolean> isNativeMap,
                           String[] clinitOwner) {
        ClinitIndexVisitor visitor = null;
        try {
            ClassReader cr = new ClassReader(classBytes);
            visitor = new ClinitIndexVisitor(callGraph, isNativeMap, clinitOwner);
            cr.accept(visitor, ClassReader.SKIP_FRAMES | ClassReader.SKIP_DEBUG);
            return true;
        } catch (StackOverflowError soe) {
            // Stack is fully unwound at this frame — safe to continue with
            // the rest of the JAR.  Still fail-secure for THIS class.
            purgePartialState(visitor, callGraph, isNativeMap, clinitOwner);
            logger.log(Level.WARNING,
                    "StackOverflowError parsing class bytecode (possible "
                    + "crafted deep-recursion input) — treating as parse "
                    + "failure (fail-secure)", soe);
            return false;
        } catch (VirtualMachineError vme) {
            // Not the stack-safe case above (e.g. OutOfMemoryError): more
            // likely genuine infra pressure than crafted input, and
            // continuing analysis after this is unreliable — propagate.
            throw vme;
        } catch (Throwable t) {
            // Malformed class (ASM throws a variety of unchecked exceptions
            // for bad bytecode) — caller handles by treating as no-clinit /
            // parse failure.
            purgePartialState(visitor, callGraph, isNativeMap, clinitOwner);
            logger.log(Level.WARNING,
                    "Failed to parse class bytecode — treating as parse "
                    + "failure (fail-secure)", t);
            return false;
        }
    }

    /**
     * Undoes any partial state a failed {@link #indexClass} attempt may have
     * recorded: resets {@code clinitOwner[0]} to {@code null} (it may have
     * been set by {@code visit()} before the failure) and removes every
     * {@code callGraph}/{@code isNativeMap} entry the (possibly {@code null})
     * {@code visitor} added for this class before the throw.
     */
    private static void purgePartialState(ClinitIndexVisitor visitor,
                                          Map<String, Set<String>> callGraph,
                                          Map<String, Boolean> isNativeMap,
                                          String[] clinitOwner) {
        if (clinitOwner != null && clinitOwner.length > 0) {
            clinitOwner[0] = null;
        }
        if (visitor != null) {
            for (String key : visitor.addedMethodKeys) {
                callGraph.remove(key);
                isNativeMap.remove(key);
            }
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

        // BFS: each node is a method key.  Rather than every queued node
        // carrying a full copy of the path-so-far (O(V·pathlen) memory), we
        // record each node's parent exactly once, at first-visit time, and
        // reconstruct the full path only once — by walking backward from a
        // found sink to the root — the single time a sink is actually found.
        Set<String> visited          = new HashSet<String>();
        Map<String, String> parent   = new HashMap<String, String>();
        Deque<BfsNode> queue         = new ArrayDeque<BfsNode>();

        queue.add(new BfsNode(clinitKey, 0));
        visited.add(clinitKey);

        while (!queue.isEmpty()) {
            BfsNode current = queue.poll();
            if (current.depth > maxDepth) continue;

            Set<String> callees = callGraph.get(current.key);
            if (callees == null) continue;

            for (String callee : callees) {
                if (visited.contains(callee)) continue;
                visited.add(callee);
                // Record the parent at first-visit time — even for callees
                // that are never enqueued below (depth boundary reached, or
                // no callGraph entry of their own).  A sink can be such a
                // leaf, and it must still have its parent recorded so the
                // path can be reconstructed if it turns out to be the hit.
                parent.put(callee, current.key);

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

                // Check blocking sink — determine if the path to this blocker
                // is defended by a permission guard (BLOCKING_GUARDED) or not
                // (BLOCKING).
                if (BlockingSinkRegistry.isBlocking(owner, name, descriptor)) {
                    List<String> path = reconstructPath(callee, clinitKey, parent);
                    ClinitVerdict v = hasPermissionGuardOnPath(path, callGraph)
                            ? ClinitVerdict.BLOCKING_GUARDED
                            : ClinitVerdict.BLOCKING;
                    return new ClinitAnalysisResult(v, path);
                }

                // Check unregistered native
                Boolean isNative = isNativeMap.get(callee);
                if (Boolean.TRUE.equals(isNative)
                        && !BlockingSinkRegistry.isSafeNative(owner, name, descriptor)) {
                    List<String> path = reconstructPath(callee, clinitKey, parent);
                    return new ClinitAnalysisResult(ClinitVerdict.NATIVE_OPACITY, path);
                }

                // Continue BFS if callee has its own call graph entry.  The
                // depth boundary is preserved exactly as before: a node AT
                // maxDepth still has its callees checked for sink/native
                // status above, it is just not enqueued for further
                // expansion beyond that.
                if (callGraph.containsKey(callee) && current.depth < maxDepth) {
                    queue.add(new BfsNode(callee, current.depth + 1));
                }
            }
        }

        return new ClinitAnalysisResult(ClinitVerdict.CLEAN,
                Collections.<String>emptyList());
    }

    /**
     * Reconstructs the root-to-{@code sink} path by walking the
     * {@code parent} pointers backward from {@code sink} and reversing the
     * result.  Produces exactly the same sequence the old full-path-copy BFS
     * built: {@code [clinitKey, ..., sink]}.
     *
     * <p>Termination is explicit — the walk stops the moment it reaches
     * {@code clinitKey} (the seeded root, which never has a {@code parent}
     * entry of its own) rather than relying on {@code parent.get(...)}
     * incidentally returning {@code null}, so the loop cannot spin forever
     * even if the map were ever mutated unexpectedly.
     *
     * @param sink      the node the path must end at (a blocking sink or
     *                  native-opaque callee)
     * @param clinitKey the root node the path must start at
     * @param parent    child → parent map, populated once per node at
     *                  first-visit time by the BFS above
     * @return the path from {@code clinitKey} to {@code sink}, inclusive
     */
    private static List<String> reconstructPath(String sink, String clinitKey,
                                                 Map<String, String> parent) {
        List<String> path = new ArrayList<String>();
        String cur = sink;
        while (cur != null) {
            path.add(cur);
            if (cur.equals(clinitKey)) {
                break; // reached the root — stop explicitly
            }
            cur = parent.get(cur);
        }
        Collections.reverse(path);
        return path;
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
        final String key;
        final int    depth;

        BfsNode(String key, int depth) {
            this.key   = key;
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

        /**
         * Method keys this visitor has added to {@code callGraph} (and
         * possibly {@code isNativeMap}) so far.  If parsing this class fails
         * partway through, {@link #purgePartialState} removes exactly these
         * keys so no half-recorded state for this class survives.
         */
        private final Set<String> addedMethodKeys = new HashSet<String>();

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
            addedMethodKeys.add(methodKey);

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

        /**
         * Models the class-initialization edge created by a static-field
         * reference.  A {@code GETSTATIC} or {@code PUTSTATIC} instruction
         * triggers the owner class's {@code <clinit>} (JVMS §5.5), so we add a
         * synthetic callee edge to {@code owner + "/<clinit>/()V"}.  This lets
         * the BFS reachability scan and the cycle detector follow a blocking
         * {@code <clinit>} reached purely through a static-field reference chain
         * into another JAR class.
         *
         * <p>Instance-field instructions ({@code GETFIELD}/{@code PUTFIELD}) do
         * not trigger initialization on their own and are intentionally left
         * unmodeled.
         */
        @Override
        public void visitFieldInsn(int opcode, String owner, String name,
                                   String descriptor) {
            if (opcode == Opcodes.GETSTATIC || opcode == Opcodes.PUTSTATIC) {
                callees.add(owner + "/<clinit>/()V");
            }
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
