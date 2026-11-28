# Download-module class sort, part 2: jgdms-lib-dl sorted files 60 to 117

Author: claude-sonnet-5-5 sub-agent of the proxy-board review session, 2026-11-28. Read-only sort; every row is the sub-agent's reading and is RELAYED until someone re-opens it.

Bins: D data; X exception; M interface or other declaration only; P pure forwarder or proxy-trust plumbing; L work done in the client; U uncertain.

Written to disk by the coordinating session (Claude Fable 5.2), with the operator's permission, from the sub-agent's report: the sub-agent's own write was refused by the permission system. Rows are copied from that report; only the path prefix was restored. The sub-agent read every file in full with comment lines and blank lines filtered out, so comment text was not read. Line numbers are file line numbers.

## Judgement calls (the sub-agent's)

- UIDescriptor is L because getUIFactory unmarshals the factory.
- Locales is L because getFirstSupportedLocale computes a first match. It is borderline D.
- The lease classes are binned by their own code. AbstractLease and LandlordLease are L because they track expiration and build lease maps. ConstrainableLandlordLease is P with a borderline flag.
- LookupUtilMarshalDelegate is D as serialization codec plumbing. It is borderline L, since create() builds objects from the wire form.

## Table

| # | path | kind | bin | evidence | notes |
|---|---|---|---|---|---|
| 1 | jgdms-lib-dl/src/main/java/net/jini/lookup/entry/StatusBean.java | abstract class, implements EntryBean, Serializable | D | L72 "return assoc.severity;" | Getter/setter wrapper over a Status entry (makeLink L55, followLink L62). |
| 2 | jgdms-lib-dl/src/main/java/net/jini/lookup/entry/StatusType.java | class, implements Serializable, Resolve; @AtomicSerial | D | L113 "private static int check(GetArg arg)" | Typesafe-enum value with serialForm, serialize, check and readResolve (L161). No logic beyond validation. |
| 3 | jgdms-lib-dl/src/main/java/net/jini/lookup/entry/UIDescriptor.java | class, extends AbstractEntry; @SerialEntry | L | L219 "new MarshalledInstance(factory).get(false)" | Wraps no remote interface. Client work: getUIFactory (L204-225) swaps the thread context class loader (L216-222) and unmarshals the factory (L219). Wraps attributes in a ConsistentSet (L73, L165). Calls MarshalledInstance.get at L219. No Class.forName, loadClass, readObject or thread creation. |
| 4 | jgdms-lib-dl/src/main/java/net/jini/lookup/entry/UIDescriptorBean.java | class, implements EntryBean, Serializable | D | L80 "return assoc.role;" | Bean accessors delegating to the UIDescriptor field. |
| 5 | jgdms-lib-dl/src/main/java/net/jini/lookup/entry/jmx/JMXProperty.java | class, extends AbstractEntry; @SerialEntry | D | L60 "serialize(PutEntryArg arg, JMXProperty obj)" | Public fields name and value. |
| 6 | jgdms-lib-dl/src/main/java/net/jini/lookup/entry/jmx/JMXProtocolType.java | class, extends AbstractEntry; @SerialEntry | D | L73 "protocolType.toLowerCase()" | Constants and one field. The constructor only lowercases. |
| 7 | jgdms-lib-dl/src/main/java/net/jini/lookup/ui/AboutUI.java | interface | M | L69 "String ROLE = ..." | Role constant only. |
| 8 | jgdms-lib-dl/src/main/java/net/jini/lookup/ui/AdminUI.java | interface | M | L68 "String ROLE = ..." | Role constant only. |
| 9 | jgdms-lib-dl/src/main/java/net/jini/lookup/ui/MainUI.java | interface | M | L69 "String ROLE = ..." | Role constant only. |
| 10 | jgdms-lib-dl/src/main/java/net/jini/lookup/ui/attribute/AccessibleUI.java | class, implements Serializable | D | L78 "return getClass().getName().hashCode();" | Stateless marker attribute with class-name equals and hashCode. |
| 11 | jgdms-lib-dl/src/main/java/net/jini/lookup/ui/attribute/Locales.java | class, implements Serializable | L | L127 "getFirstSupportedLocale(Locale[] locales)" | Borderline D. It holds a Set of Locale in a ConsistentSet. Local work: getFirstSupportedLocale over an array (L127-140) and a list (L154-174). No remote wrap. No class loading, unmarshalling or threads. |
| 12 | jgdms-lib-dl/src/main/java/net/jini/lookup/ui/attribute/RequiredPackages.java | class, implements Serializable | D | L195 "getVersion(String packageName)" | Validating constructor (L123-154), then map lookup, equals and hashCode. |
| 13 | jgdms-lib-dl/src/main/java/net/jini/lookup/ui/attribute/UIFactoryTypes.java | class, implements Serializable | D | L129 "isAssignableTo(Class classObj)" | Validating constructor. isAssignableTo only does typeNames.contains(classObj.getName()) (L135). |
| 14 | jgdms-lib-dl/src/main/java/net/jini/space/AvailabilityEvent.java | abstract class, extends RemoteEvent; @AtomicSerial | D | L73 "private static boolean check(GetArg arg)" | Event data with a boolean flag. check validates the source (L84). java.io readObject/writeObject are stubbed to throw (L156-181). Abstract getEntry and getSnapshot at L197 and L225. |
| 15 | jgdms-lib-dl/src/main/java/net/jini/space/FilterRejectedException.java | class, extends Exception | X | L56 "extends Exception" | Nested enum Reason (L66) is M. |
| 16 | jgdms-lib-dl/src/main/java/net/jini/space/FilteredTupleSpace.java | interface | M | L75 "public interface FilteredTupleSpace" | Declarations only. |
| 17 | jgdms-lib-dl/src/main/java/net/jini/space/InternalSpaceException.java | class, extends RuntimeException | X | L55 "extends RuntimeException" |  |
| 18 | jgdms-lib-dl/src/main/java/net/jini/space/JavaSpace.java | interface | M | L39 "public interface JavaSpace" | Declarations plus constant NO_WAIT. |
| 19 | jgdms-lib-dl/src/main/java/net/jini/space/JavaSpace05.java | interface, extends JavaSpace | M | L81 "interface JavaSpace05 extends JavaSpace" |  |
| 20 | jgdms-lib-dl/src/main/java/net/jini/space/MatchSet.java | interface | M | L113 "public interface MatchSet" |  |
| 21 | jgdms-lib-dl/src/main/java/net/jini/space/TupleSpace.java | interface, extends JavaSpace05 | M | L33 "interface TupleSpace extends JavaSpace05" |  |
| 22 | jgdms-lib-dl/src/main/java/org/apache/river/activation/ActivationAdmin.java | interface, extends Remote | M | L35 "interface ActivationAdmin extends Remote" |  |
| 23 | jgdms-lib-dl/src/main/java/org/apache/river/admin/AdminIterator.java | interface | M | L44 "public interface AdminIterator" |  |
| 24 | jgdms-lib-dl/src/main/java/org/apache/river/admin/DestroyAdmin.java | interface | M | L29 "public interface DestroyAdmin" |  |
| 25 | jgdms-lib-dl/src/main/java/org/apache/river/admin/FiddlerAdmin.java | interface, extends JoinAdmin, DestroyAdmin | M | L119 "interface FiddlerAdmin" |  |
| 26 | jgdms-lib-dl/src/main/java/org/apache/river/admin/JavaSpaceAdmin.java | interface, extends JoinAdmin, DestroyAdmin | M | L43 "interface JavaSpaceAdmin" | Constant USE_DEFAULT. |
| 27 | jgdms-lib-dl/src/main/java/org/apache/river/admin/StorageLocationAdmin.java | interface | M | L33 "public interface StorageLocationAdmin" |  |
| 28 | jgdms-lib-dl/src/main/java/org/apache/river/landlord/AbstractLeasedResource.java | abstract class, implements LeasedResource, Cloneable | L | L43 "renew(long expiration)" | No remote wrap. Local work: clone (L28), and renew copies the resource and calls setExpiration on the copy (L44-46). None of the flagged calls. |
| 29 | jgdms-lib-dl/src/main/java/org/apache/river/landlord/ConstrainableLandlordLease.java | final class, extends LandlordLease, implements RemoteMethodControl; @AtomicSerial | P | L285 "setConstraints(MethodConstraints constraints)" | Borderline. Wraps the Landlord server reference. Constraint plumbing: constrainServer (L271-282), setConstraints (L285), the check and readObject consistency checks (L234, L297), and Method mapping tables via type.getMethod (L78-82). Its own createLeaseMap (L329) and canBatch (L312) add batching helpers. It inherits the L logic of LandlordLease and AbstractLease. No Class.forName or loadClass. |
| 30 | jgdms-lib-dl/src/main/java/org/apache/river/landlord/ConstrainableLandlordLeaseMap.java | final class, extends LandlordLeaseMap | L | L72 "canContainKey(Object key)" | Adds only a constraint-equivalence batching predicate (L72-91) and a RemoteMethodControl check (L63). Wraps the Landlord. Inherits the batching logic of LandlordLeaseMap. None of the flagged calls. |
| 31 | jgdms-lib-dl/src/main/java/org/apache/river/landlord/FixedLeasePeriodPolicy.java | class, implements LeasePeriodPolicy | L | L98 "return Math.min(requestedDuration, maximum);" | Pure local computation, no remote wrap. calculateDuration (L86), calcExpiration (L120), grant (L134) and renew (L145). None of the flagged calls. |
| 32 | jgdms-lib-dl/src/main/java/org/apache/river/landlord/Landlord.java | interface, extends Remote | M | L45 "public interface Landlord extends Remote" | Nested static class RenewResults (L130, @AtomicSerial) is D. |
| 33 | jgdms-lib-dl/src/main/java/org/apache/river/landlord/LandlordLease.java | class, extends AbstractLease, implements ReferentUuid, ID<Uuid>, ProxyAccessor; @AtomicSerial | L | L216 "return new LandlordLeaseMap(landlord, ...)" | Wraps the Landlord. cancel (L153) and doRenew (L158) forward with the same arguments. Client work: negative-duration argument check (L161-166), canBatch (L187), createLeaseMap (L216), and expiration setter (L211). It inherits expiration arithmetic from AbstractLease. None of the flagged calls. |
| 34 | jgdms-lib-dl/src/main/java/org/apache/river/landlord/LandlordLeaseMap.java | class, extends AbstractIDLeaseMap | L | L191 "landlord.renewAll(cookiesA, extensionsA)" | Wraps the Landlord. cancelAll (L116) builds a cookie list and calls landlord.cancelAll (L130). It maps results back to leases (L149-157) and builds a LeaseMapException (L162). renewAll (L169) builds arrays (L173-189), computes expirations (L197-199) and calls landlord.renewAll (L191). It removes denied leases (L206). None of the flagged calls. |
| 35 | jgdms-lib-dl/src/main/java/org/apache/river/landlord/LandlordProxyVerifier.java | final class, implements Serializable, TrustVerifier; @AtomicSerial | P | L202 "return constrainedServer.checkTrustEquivalence(inputProxyServer);" | Proxy-trust support. Wraps the Landlord as RemoteMethodControl and TrustEquivalence. isTrustedObject (L153) compares the landlordUuid and calls setConstraints (L195). |
| 36 | jgdms-lib-dl/src/main/java/org/apache/river/landlord/LandlordUtil.java | class | L | L55 "renewAll(LocalLandlord landlord, Uuid[] cookies, long[] durations)" | Static utilities. renewAll (L55-80) loops over LocalLandlord.renew and collects granted times and exceptions. cancelAll (L95-110) loops over LocalLandlord.cancel. It calls a LocalLandlord, not a remote ref. None of the flagged calls. |
| 37 | jgdms-lib-dl/src/main/java/org/apache/river/landlord/LeaseFactory.java | class | L | L80 "if (landlord instanceof RemoteMethodControl)" | Factory. newLease (L79-83) picks the Constrainable or plain lease. getVerifier (L95) builds a LandlordProxyVerifier. It wraps the Landlord. None of the flagged calls. |
| 38 | jgdms-lib-dl/src/main/java/org/apache/river/landlord/LeasePeriodPolicy.java | interface | M | L29 "public interface LeasePeriodPolicy" | Nested class Result (L34, final fields) is D. |
| 39 | jgdms-lib-dl/src/main/java/org/apache/river/landlord/LeasedResource.java | interface | M | L31 "public interface LeasedResource" |  |
| 40 | jgdms-lib-dl/src/main/java/org/apache/river/landlord/LocalLandlord.java | interface | M | L44 "public interface LocalLandlord" |  |
| 41 | jgdms-lib-dl/src/main/java/org/apache/river/lease/AbstractIDLeaseMap.java | abstract class, extends AbstractMap, implements LeaseMap | L | L79 "RC.concurrentMap(... Ref.WEAK, Ref.STRONG ...)" | Local map logic with no remote wrap. Concurrent weak-referenced maps (L79-85), identity keying (L175), put/remove with entry bookkeeping (L156-173). Nested LeaseEntry (L188) has compareTo (L262). None of the flagged calls. |
| 42 | jgdms-lib-dl/src/main/java/org/apache/river/lease/AbstractLease.java | abstract class, implements Lease; @AtomicSerial | L | L157 "long exp = doRenew(duration) + System.currentTimeMillis();" | Holds mutable expiration (L85). renew (L154-162) converts a duration to an absolute time and clamps overflow. adjustedVal (L65) and checkExpiration (L93) convert DURATION and ABSOLUTE forms. Abstract doRenew is the only remote hook. None of the flagged calls. |
| 43 | jgdms-lib-dl/src/main/java/org/apache/river/lease/AbstractLeaseMap.java | abstract class, implements LeaseMap | L | L72 "if (map instanceof ConcurrentMap)" | Local map wrapper with no remote wrap. Synchronized-map choice (L72-78), checkKey/checkValue, putAll (L146), nested EntrySet, EntryIterator and Entry. None of the flagged calls. |
| 44 | jgdms-lib-dl/src/main/java/org/apache/river/lease/BasicRenewalFailureEvent.java | class, extends RenewalFailureEvent; @AtomicSerial | L | L183 "lease = (Lease) marshalledLease.get(verifyCodebaseIntegrity);" | Event holding MarshalledInstance fields. It lazily unmarshals and caches the lease (L181-186) and the throwable (L211-222). Calls MarshalledInstance.get at L183 and L218. It uses MarshalledWrapper.integrityEnforced at L93 and L233. It defines a private readObject (L229) that calls in.defaultReadObject, but it does not call readObject on a stream. No Class.forName, loadClass or threads. |
| 45 | jgdms-lib-dl/src/main/java/org/apache/river/lease/ID.java | interface | M | L26 "public interface ID<T>" |  |
| 46 | jgdms-lib-dl/src/main/java/org/apache/river/lookup/entry/BasicServiceType.java | class, extends ServiceType | L | L107 "bundle = ResourceBundle.getBundle(name);" | Lazy ResourceBundle load and cache in init (L101-112). getIcon (L62), getDisplayName (L78) and getShortDescription (L90) read from the bundle. No remote wrap. No Class.forName, loadClass, MarshalledInstance.get, readObject or threads. |
| 47 | jgdms-lib-dl/src/main/java/org/apache/river/lookup/entry/LookupAttributes.java | class (final-style, private constructor) | L | L394 "getDeclaredConstructor().newInstance()" | Utility algorithms with no remote wrap. add (L111) and modify (L174) with dedup and matching. Reflective field-wise equal (L223) and matches (L261). update (L388) copies fields reflectively. updateSerialEntry (L427-460) invokes entryForm, serialize and the GetEntryArg constructor reflectively. check (L294) inspects constructors and fields. equal(Object,Object) at L483 builds MarshalledInstance values to compare them. Nested SimpleSerialPutArg (L642) and SimpleSerialGetArg (L669). No Class.forName, loadClass, MarshalledInstance.get, readObject or threads. It uses Method.invoke at L430, L435 and L440, and Constructor.newInstance at L451. |
| 48 | jgdms-lib-dl/src/main/java/org/apache/river/lookup/util/ConsistentMap.java | class, extends AbstractMap, implements Serializable; @AtomicSerial | D | L136 "Collections.unmodifiableSet(entrySet)" | Immutable map copy. Constructor copies entries into a ConsistentSet (L104-121). |
| 49 | jgdms-lib-dl/src/main/java/org/apache/river/lookup/util/ConsistentMapEntry.java | final class, implements Map.Entry, Serializable; @AtomicSerial | D | L135 "throw new UnsupportedOperationException();" | Immutable key/value pair with equals and hashCode. |
| 50 | jgdms-lib-dl/src/main/java/org/apache/river/lookup/util/ConsistentSet.java | class, extends AbstractSet, implements Serializable; @AtomicSerial | D | L110 "public static <T> T[] elements(Collection<T> init)" | Immutable array-backed set. The iterator is read-only (L139-160). |
| 51 | jgdms-lib-dl/src/main/java/org/apache/river/lookup/util/LookupUtilMarshalDelegate.java | final class, implements MarshalDelegate | D | L66 "create(Class<?> c, GetArg arg)" | Borderline L. Serialization dispatch for ConsistentMapEntry only: servedClasses, serialForm, serialize, and create returning new ConsistentMapEntry(arg) (L67). No loading or reflection. |
| 52 | jgdms-lib-dl/src/main/java/org/apache/river/proxy/BasicProxyTrustVerifier.java | final class, implements TrustVerifier, Serializable; @AtomicSerial | P | L121 "return trustedProxy.checkTrustEquivalence(unverifiedProxy);" | Proxy-trust plumbing. Wraps a RemoteMethodControl and TrustEquivalence proxy. isTrustedObject (L111) applies setConstraints (L119-120). |
| 53 | jgdms-lib-dl/src/main/java/org/apache/river/proxy/Bootstrap.java | final class | L | L108 "Proxy.newProxyInstance(" | Wraps a service proxy and returns a new bootstrap Proxy with the same InvocationHandler (L106-112). create (L68-115) unwraps ProxyAccessor (L70-71). It round-trips Serializable proxies through ObjectOutputStream and ObjectInputStream (L80-85). That is readObject at L85. It gets the proxy class loader in doPrivileged (L122). No Class.forName, loadClass, MarshalledInstance.get or threads. |
| 54 | jgdms-lib-dl/src/main/java/org/apache/river/proxy/CodebaseProvider.java | final class | L | L53 "ClassLoading.loadClass(codebase, name, ..." | Class-loading utility. getClassAnnotation (L36), loadClass (L45-58) and loadProxyClass (L61-74). It delegates to net.jini.loader.ClassLoading and falls back to RMIClassLoader on NoSuchMethodError (L58, L74). No literal Class.forName. It contains loadClass at L45 and L53, and RMIClassLoader.loadClass at L58. No MarshalledInstance.get, readObject or threads. |
| 55 | jgdms-lib-dl/src/main/java/org/apache/river/proxy/ConstrainableProxyUtil.java | class | L | L65 "translateConstraints(" | Static algorithms over Method[] mappings. translateConstraints (L65), reverseTranslateConstraints (L105), equivalentConstraints (L154) and verifyConsistentConstraints (L220). It builds BasicMethodConstraints. No remote wrap. None of the flagged calls. |
| 56 | jgdms-lib-dl/src/main/java/org/apache/river/proxy/MarshalledWrapper.java | class, implements Serializable; @AtomicSerial | L | L205 "return instance.get(integrity);" | Wraps a MarshalledInstance. Unmarshals via get() (L204), get(ClassLoader, ClassLoader) (L235) and get with context (L270). It inspects the ObjectStreamContext for integrity and atomicity (integrityEnforced at L91 and L103, atomicityEnforced at L126). Calls MarshalledInstance.get at L205, L239 and L275. It defines a private readObject (L332) that calls in.defaultReadObject. No Class.forName, loadClass directly, or threads. |
| 57 | jgdms-lib-dl/src/main/java/org/apache/river/proxy/ThrowThis.java | class, extends AtomicException (abstract class extends Exception, verified in jgdms-platform); @AtomicSerial | X | L55 "extends AtomicException" | Exception carrying a RemoteException (L62, throwRemoteException L123). |
| 58 | jgdms-lib-dl/src/main/java/org/apache/river/start/group/SharedGroup.java | interface, extends Remote | M | L35 "interface SharedGroup extends Remote" |  |

## Totals (58 files, the sub-agent's corrected count)

D 13, X 3, M 20, P 3, L 19, U 0.

- D rows: 1, 2, 4, 5, 6, 10, 12, 13, 14, 48, 49, 50, 51.
- X rows: 15, 17, 57.
- M rows: 7, 8, 9, 16, 18, 19, 20, 21, 22, 23, 24, 25, 26, 27, 32, 38, 39, 40, 45, 58.
- P rows: 29, 35, 52.
- L rows: 3, 11, 28, 30, 31, 33, 34, 36, 37, 41, 42, 43, 44, 46, 47, 53, 54, 55, 56.

## Watch list (L rows)

- MarshalledInstance.get: UIDescriptor L219, BasicRenewalFailureEvent L183 and L218, MarshalledWrapper L205, L239 and L275.
- ClassLoading.loadClass / loadProxyClass and RMIClassLoader: CodebaseProvider L53, L58, L69 and L74.
- ObjectInputStream.readObject call: Bootstrap L85.
- Reflection in LookupAttributes (Method.invoke, Constructor.newInstance, Field.get/set): L394, L398, L403, L430, L435, L440 and L451.
- No row calls Class.forName or creates threads.
- Rows 3, 44, 53, 54, 56 are the L rows with the strongest dynamic-code character (class loading, unmarshalling, proxy construction).

## Not opened or not completely read

None. All 58 files were read in full. The ServiceType superclass of BasicServiceType and AtomicException were checked only by a grep of the class declaration.
