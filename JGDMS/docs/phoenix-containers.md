# Why Phoenix Should Not Be Run in a Container

## Overview

Phoenix (`jgdms-activation`) is the JGDMS implementation of the Java RMI Activation
daemon.  It works well on bare-metal or VM hosts where it can own the machine lifecycle.
It is fundamentally incompatible with container runtimes (Docker, containerd, Podman) and
with container orchestrators (Kubernetes, Nomad) because of three deep structural mismatches
described below.

The correct alternative for containerised deployments is
`NonActivatableServiceDescriptor`, which is described in the final section.

---

## 1. Phoenix Spawns Child JVM Processes

Phoenix's core job is to launch and supervise one or more **activation groups**, where
each group is a separate JVM process forked by Phoenix at the request of a client
(`ActivationSystem.activateGroup()`).  The relevant code path is in
`org.apache.river.phoenix.Activation.GroupEntry.getInstantiator()`:

```java
ProcessBuilder pb = activation.buildGroupProcess(desc);
child = pb.start();                      // forks a new OS process
watchdog = new Watchdog();
watchdog.start();
```

`buildGroupProcess()` assembles a full `java` command line from the
`ActivationGroupDesc` and calls `ProcessBuilder.start()`.  Each activation group
therefore becomes an **independent OS process** inside the container's PID namespace.

### What this means for containers

| Container assumption | Phoenix reality |
|---|---|
| PID 1 is the workload | Phoenix is PID 1; the real service JVMs are PID 2, 3, … |
| cgroups limits apply to PID 1 | Child JVM heap, threads, and file descriptors are invisible to the pod's resource limits |
| Liveness / readiness probes target the container process | Probes target Phoenix; a crashed service JVM is invisible — Phoenix stays up |
| `SIGTERM` to PID 1 terminates the workload | `SIGTERM` to Phoenix may not propagate cleanly to child JVMs; orphans are possible |
| Horizontal Pod Autoscaler scales replicas | Phoenix inside a pod cannot scale individual activation groups independently |

---

## 2. Phoenix Requires a Persistent Filesystem Log

Phoenix uses `ReliableLog` to persist its activation state (group descriptors, object
registrations, incarnation numbers) to a **local directory** specified by the
`persistenceDirectory` configuration entry:

```java
logName = (String) config.getEntry(PHOENIX, "persistenceDirectory", String.class);
ReliableLog log = new ReliableLog(logName, handler);
log.recover(Activation.class.getClassLoader());
```

On startup, Phoenix always replays this log to reconstruct the activation database.  If
the directory does not exist or is empty, Phoenix takes an initial snapshot and starts
fresh.

### What this means for containers

* Container filesystems are **ephemeral by default**.  A pod restart wipes the log
  directory, destroying the activation registration database.  Every restart would be a
  "first incarnation" and all previously registered activatable services would be lost.
* Mounting a `PersistentVolume` to preserve the log works around the data-loss problem
  but introduces a **single-node binding** (`ReadWriteOnce`) that prevents the pod from
  being rescheduled freely across nodes.  This defeats most of the value of a container
  orchestrator.
* Kubernetes `StatefulSet` semantics are designed for exactly this pattern, but
  combining a `StatefulSet` with Phoenix's child-process spawning model still does not
  solve problems 1 and 3.

---

## 3. Phoenix Is the Lifecycle Manager — Kubernetes Also Wants to Be

Phoenix provides:
* on-demand activation of services when first called
* automatic restart of crashed service JVMs (Watchdog thread)
* `ActivationSystem` RMI interface for registering and unregistering services at runtime
* a `GroupOutputHandler` callback for capturing service stdout/stderr

Kubernetes provides:
* liveness and readiness probes with automatic pod restart
* rolling updates and canary deployments
* horizontal pod autoscaling
* structured log collection via container stdout/stderr

These two lifecycle managers **conflict**:

* Phoenix restarts a crashed service JVM internally without Kubernetes knowing.
  Kubernetes metrics (restart count, CrashLoopBackOff) become meaningless.
* Phoenix's `ActivationSystem` registration model requires services to be registered
  before they can be activated.  In an immutable-image / GitOps workflow there is no
  natural point to run the registration step.
* Phoenix's `shutdown()` method calls `System.exit(0)`, which terminates the container
  and triggers a Kubernetes restart — potentially in an infinite loop if the log is
  missing.
* Kubernetes `SIGTERM` → `SIGKILL` grace period applies to Phoenix (PID 1).  Phoenix
  may not propagate `SIGTERM` to child JVMs within the grace period, leaving orphaned
  processes that the container runtime will `SIGKILL` uncleanly.

---

## The Correct Alternative: `NonActivatableServiceDescriptor`

Every JGDMS service that has an activatable constructor
(`MyServiceImpl(ActivationID, MarshalledObject)`) also has a **non-activatable
constructor** (`MyServiceImpl(String[] args, LifeCycle lc)`).
`NonActivatableServiceDescriptor` uses the non-activatable constructor to start the
service in-process inside the `ServiceStarter` JVM:

```java
// NonActivatableServiceDescriptor.create() — in-process, no fork
Constructor<?> ctor = implClass.getDeclaredConstructor(String[].class, LifeCycle.class);
Object impl = ctor.newInstance(new Object[]{args, lifeCycle});
```

This means:

| Concern | Phoenix | NonActivatableServiceDescriptor |
|---|---|---|
| Number of JVM processes per container | 1 + N child JVMs | Exactly 1 (PID 1 = service JVM) |
| cgroups resource accounting | Partial (children invisible) | Complete |
| Kubernetes liveness / readiness | Monitors Phoenix, not services | Monitors the service directly |
| Persistent log required | Yes (`persistenceDirectory`) | No |
| Lifecycle manager conflict | Yes | No — Kubernetes is the sole lifecycle manager |
| Rolling updates | Complex | Standard Kubernetes rolling update |
| Horizontal scaling | Not supported | Standard Kubernetes HPA |

### Container entrypoint pattern

```
ENTRYPOINT ["java",
  "-Djava.security.manager=org.apache.river.api.security.ConcurrentPolicyFile",
  "-Djava.security.policy=/opt/jgdms/policy/service.policy",
  "-jar", "/opt/jgdms/lib/service-starter.jar",
  "/opt/jgdms/config/service.config"]
```

The `service.config` file uses `NonActivatableServiceDescriptor` — not a
`SharedActivatableServiceDescriptor` or `SharedGroupDescriptor`:

```groovy
import org.apache.river.start.NonActivatableServiceDescriptor;
import com.sun.jini.start.ServiceDescriptor;

com.sun.jini.start.serviceDescriptors = new ServiceDescriptor[] {
    new NonActivatableServiceDescriptor(
        "http://${codebase.host}:${codebase.port}/my-service-dl.jar",
        "${policy.file}",
        "/opt/jgdms/lib/my-service.jar",
        "com.example.MyServiceImpl",
        new String[]{ "/opt/jgdms/config/my-service.config" }
    )
};
```

With this pattern:
* PID 1 is the service JVM — Kubernetes liveness probes, `SIGTERM` handling, and
  resource limits all work correctly.
* No `persistenceDirectory` is needed — state is either held in-memory or in an
  external store (database, Cassandra, etc.).
* The container image is fully immutable — no runtime activation registration step is
  required.
* Each service role gets its own pod and its own Kubernetes `Deployment` (or
  `StatefulSet` if the service itself needs persistent storage), managed independently
  by the orchestrator.

---

## Summary

Phoenix is designed for a world where one long-running daemon manages many services on
a single host.  Containers invert this model: each container runs one service, and the
orchestrator manages many containers.  The structural mismatches — multi-process spawning,
local persistent log, and dual lifecycle management — make Phoenix unsuitable for
container deployments.  Use `NonActivatableServiceDescriptor` with `ServiceStarter` as
PID 1 instead.
