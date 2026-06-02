# Getting Started with JGDMS

JGDMS (*Jini Global Distributed Micro Services*) is a security-hardened fork of
[Apache River](https://river.apache.org/) that provides dynamically discoverable,
capability-based microservices over IPv6 networks.  This guide walks you from a
fresh clone to a running Hello World service in a few steps.

---

## Table of Contents

1. [Prerequisites](#1-prerequisites)
2. [Build from source](#2-build-from-source)
3. [Project structure](#3-project-structure)
4. [Run the Hello World example](#4-run-the-hello-world-example)
5. [Create your own service](#5-create-your-own-service)
6. [Key concepts](#6-key-concepts)
7. [Next steps](#7-next-steps)

---

## 1. Prerequisites

| Requirement | Notes |
|---|---|
| **DirtyChai JDK** | A Java 21-compatible fork of OpenJDK that retains `SecurityManager` / `AccessController` support required by JGDMS. [Get it here](https://github.com/pfirmstone/jdk-with-authorization). **Java 24+ is not supported** (authorization APIs were removed in Java 24). |
| **Maven 3.8+** | Standard Maven build tool. |
| **IPv6 networking** | Multicast discovery uses IPv6 by default. On Linux, make sure `net.ipv6.conf.all.disable_ipv6=0`. On macOS/Windows the loopback interface supports IPv6 out of the box. |

> **Why DirtyChai?**  JGDMS relies on `SecurityManager`, `AccessController`, and
> `ProtectionDomain` APIs that were deprecated in Java 17 and deleted in Java 24.
> DirtyChai restores and extends these APIs on a modern JVM, including support for
> virtual threads.

---

## 2. Build from source

Clone the repository and build all modules:

```bash
git clone https://github.com/pfirmstone/JGDMS.git
cd JGDMS
mvn -f JGDMS/pom.xml package
```

To run the unit tests as well:

```bash
mvn -f JGDMS/pom.xml test
```

The build produces one JAR per module under each module's `target/` directory.
The key runtime JARs end up under `JGDMS/dist/` once the `dist` module runs.

---

## 3. Project structure

```
JGDMS/                          ← repository root
├── JGDMS/                      ← Maven multi-module root (pom.xml here)
│   ├── jgdms-platform/         ← Core platform APIs (@AtomicSerial, Security, Uri…)
│   ├── jgdms-jeri/             ← Jini Extensible Remote Invocation (JERI) stack
│   ├── jgdms-lib/              ← Client-side libraries (ServiceDiscoveryManager…)
│   ├── jgdms-lib-dl/           ← Download-side helpers (ServiceDiscoveryHelper…)
│   ├── jgdms-pref-class-loader/← Preferred-class loader + codebase verification
│   ├── service-starter/        ← ServiceStarter launcher
│   ├── services/
│   │   ├── reggie/             ← Jini Lookup Service
│   │   ├── mahalo/             ← Distributed transaction manager
│   │   ├── hello-world/        ← Hello World example (start here)
│   │   └── jgdms-service-support/ ← AbstractJiniService, DefaultJiniServiceParameters
│   ├── jgdms-service-archetype/← Maven archetype for new services
│   └── dist/                   ← Assembled runtime distribution
├── deploy/                     ← Kubernetes / Docker / Helm deployment artefacts
└── docs/                       ← Design documents and blog series
```

---

## 4. Run the Hello World example

The Hello World example under `JGDMS/services/hello-world/` demonstrates the
complete JGDMS service lifecycle: service implementation, smart proxy, and a
client that discovers the service via multicast.

### 4.1 Build Hello World

```bash
mvn -f JGDMS/pom.xml package -pl services/hello-world -am
```

### 4.2 Start the Lookup Service (Reggie)

Every JGDMS deployment requires at least one running Jini Lookup Service so that
services can register and clients can discover them.

Create a minimal starter config `reggie-start.config`:

```
import org.apache.river.start.*;
import net.jini.core.discovery.LookupLocator;

com.sun.jini.start {
    serviceDescriptors = new ServiceDescriptor[] {
        new NonActivatableServiceDescriptor(
            "",                                      /* no codebase for server JVM */
            "reggie.policy",
            "JGDMS/services/reggie/reggie-service/target/reggie-service-3.1.1-SNAPSHOT.jar",
            "com.sun.jini.reggie.TransientRegistrarImpl",
            new String[]{ "reggie.config" }
        )
    };
}
```

Then launch it:

```bash
java -Djava.security.policy=reggie.policy \
     -cp JGDMS/service-starter/target/service-starter-3.1.1-SNAPSHOT.jar \
     org.apache.river.start.ServiceStarter \
     reggie-start.config
```

### 4.3 Start the Hello World service

```bash
java -Djava.security.policy=hello-service.policy \
     -cp <classpath-including-service-jars> \
     org.apache.river.start.ServiceStarter \
     JGDMS/services/hello-world/hello-world-service/src/main/resources/hello-world-service.config
```

The service reads its configuration from `hello-world-service.config`.
By default it uses plain TCP (no TLS) and joins the public multicast lookup group.

### 4.4 Run the Hello World client

```bash
java -Djava.security.policy=hello-client.policy \
     -cp <classpath-including-client-jars> \
     au.net.zeus.jgdms.hello.client.HelloWorldClient \
     JGDMS/services/hello-world/hello-world-client/src/main/resources/hello-world-client.config
```

Expected output:

```
Service replied: Hello, World!
```

The client uses `ServiceDiscoveryHelper.fromConfig(config, component)` to handle
all discovery boilerplate in a single try-with-resources block.

---

## 5. Create your own service

A Maven archetype generates the standard four-module layout automatically.

### 5.1 Generate a new project

```bash
mvn archetype:generate \
  -DarchetypeGroupId=au.net.zeus.jgdms \
  -DarchetypeArtifactId=jgdms-service-archetype \
  -DarchetypeVersion=3.1.1-SNAPSHOT \
  -DgroupId=com.example \
  -DartifactId=my-service \
  -DserviceName=MyService \
  -Dcomponent=com.example.myservice
```

This produces the following layout:

```
my-service/
├── api/        ← Service interface (MyServiceService.java)
├── dl/         ← Client-side smart proxy (MyServiceServiceProxy.java)
├── service/    ← Server implementation + config + policy
└── client/     ← Discovery client
```

### 5.2 Module responsibilities

| Module | What lives here |
|---|---|
| **api** | The remote service interface your clients and service both depend on |
| **dl** | The `@AtomicSerial` smart proxy downloaded to client JVMs |
| **service** | Server-side implementation extending `AbstractJiniService`; `.config` and `.policy` files |
| **client** | A main class using `ServiceDiscoveryHelper` to discover and call your service |

### 5.3 Minimal service implementation

`AbstractJiniService` handles all Jini boilerplate (export, JoinManager, lease
renewal).  Your implementation only needs two constructors and two template
methods:

```java
public class MyServiceServiceImpl
        extends AbstractJiniService
        implements MyServiceService {

    static final String COMPONENT = "com.example.myservice";

    // Non-activatable constructor (used with ServiceStarter)
    public MyServiceServiceImpl(String[] configArgs, LifeCycle lifeCycle)
            throws Exception {
        super(configArgs, lifeCycle, COMPONENT, MyServiceService.class);
    }

    // Activatable constructor (used with Phoenix)
    public MyServiceServiceImpl(ActivationID activationID, String[] data)
            throws Exception {
        super(activationID, data, COMPONENT, MyServiceService.class);
    }

    @Override
    protected Object createProxy(Object stub, Uuid serviceUuid) {
        return MyServiceServiceProxy.create((MyServiceService) stub, serviceUuid);
    }

    @Override
    protected Class<?>[] getServiceInterfaces() {
        return new Class<?>[]{ MyServiceService.class };
    }

    // Implement your service methods here…
}
```

### 5.4 Minimal client

```java
try (ServiceDiscoveryHelper discovery =
        ServiceDiscoveryHelper.fromConfig(config, COMPONENT)) {

    MyServiceService svc =
        discovery.lookup(MyServiceService.class, timeoutMs);
    // Call service methods…
}
```

---

## 6. Key concepts

### Service discovery model

JGDMS uses *capability-based* discovery: clients search for a service by its
Java interface type, not by a fixed URL.  A Jini Lookup Service (Reggie) acts
as the rendezvous point.  Registrations are *lease-based* — a service that
crashes cleans itself out of the registry automatically when its lease expires.

### Smart proxies and `@AtomicSerial`

The *smart proxy* (the `-dl` module) is the object that the client JVM actually
uses.  It is downloaded from the server's codebase and deserialized on the
client.  All wire-serializable types must use
[`@AtomicSerial`](JGDMS/jgdms-platform/src/main/java/org/apache/river/api/io/AtomicSerial.java)
and provide a `(GetArg)` constructor for validated atomic deserialization.

### Configuration files (`.config`)

JGDMS uses Jini's `ConfigurationProvider` rather than property files or YAML.
Configuration entries are type-safe Java expressions scoped to a *component*
name (typically the fully-qualified class name of the service).  See the Hello
World configs in
`JGDMS/services/hello-world/*/src/main/resources/` for annotated examples.

### Security policy files (`.policy`)

JGDMS enforces Java security policy using DirtyChai's `ConcurrentPolicyFile`
provider.  Policy files follow standard Java policy syntax.  Least-privilege
grants are keyed by codebase URL.  See `hello-service.policy` and
`hello-client.policy` for examples.

### Transport options

| Transport | Class | Notes |
|---|---|---|
| Plain TCP (dev) | `TcpServerEndpoint` | No authentication; use only on trusted networks |
| TLS (production) | `SslServerEndpoint` | Requires keystore/truststore; supports `Integrity.YES`, `ClientAuthentication.YES`, `ServerAuthentication.YES` |

---

## 7. Next steps

| Topic | Where to look |
|---|---|
| Architecture overview | [`ARCHITECTURE.md`](ARCHITECTURE.md) |
| Service & proxy life cycles | [`SERVICE_AND_PROXY_LIFE_CYCLES.md`](SERVICE_AND_PROXY_LIFE_CYCLES.md) |
| Proxy isolation model | [`PROXY_ISOLATION.md`](PROXY_ISOLATION.md) |
| Design philosophy | [`JGDMS/docs/philosophy.md`](JGDMS/docs/philosophy.md) |
| Production Kubernetes deployment (SCAP) | [`deploy/README.md`](deploy/README.md) |
| Security architecture | [`JGDMS/docs/Big picture security architecture/`](JGDMS/docs/Big%20picture%20security%20architecture/) |
| Blog series (six parts) | [`JGDMS/docs/blog-post-1-why-fork.md`](JGDMS/docs/blog-post-1-why-fork.md) |
| DirtyChai JDK | [github.com/pfirmstone/jdk-with-authorization](https://github.com/pfirmstone/jdk-with-authorization) |
| Community discussion | [GitHub Discussions](https://github.com/pfirmstone/JGDMS/discussions) |
