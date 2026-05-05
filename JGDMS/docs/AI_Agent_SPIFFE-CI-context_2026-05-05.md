# AI Agent Context: SPIFFE Unit-Test CI Infrastructure
**Date:** 2026-05-05  
**Branch merged:** `copilot/review-test-infrastructure-integration` → master  
**PR:** #229 — "SPIFFE test infrastructure: SpiffePrincipal, SpiffeLoginModule, spiffe QA config set (Phases 1–3)"

---

## 1. What was delivered in this PR

### 1.1 New SPIFFE source classes (jgdms-jeri)
All classes live in package `net.jini.jeri.ssl`.

| Class | Purpose |
|---|---|
| `SpiffePrincipal` | `java.security.Principal` wrapping a SPIFFE URI (`spiffe://trust-domain/path`). Extracted from an `X509Certificate` URI SAN. |
| `SpiffeLoginModule` | JAAS `LoginModule` that reads two PEM files (cert-chain + PKCS8 key) and populates a `Subject` with `X500PrivateCredential`, `X500Principal`, and `SpiffePrincipal`. Locates files via `svidPem`/`keyPem` JAAS options or via the system property `net.jini.jeri.ssl.spiffe.dir` + `serviceRole` option. |
| `SpiffeCredentialManager` | Lifecycle manager that wraps a `SvidSource` SPI, schedules automatic renewal (`renewalLeadSeconds` before SVID expiry), and calls `updateSubjectCredentials()` to swap in fresh `X500PrivateCredential`. Also adds/removes `X500Principal` and `SpiffePrincipal` to `Subject.getPrincipals()`, tracked in `managedPrincipals` for clean removal on rotation. |
| `SpiffeCredentialManager.SvidSource` | Public inner interface: `Svid load() throws IOException, GeneralSecurityException`. |
| `SpiffeCredentialManager.FileSvidSource` | SPI implementation reading SPIRE-written PEM files. Two constructors: `(Path svidPem, Path svidKeyPem)` and `(Path spireDir)` (uses default filenames `svid.pem` / `svid_key.pem`). |
| `SpiffeSubjectHolder` | Package-private `AtomicReference<Subject>` singleton. `set()`/`get()` called by `SpiffeCredentialManager.start()`/`close()`. `SslEndpointImpl.getCallContext()` and `SslServerEndpointImpl.SslListenEndpoint` fall back to it when `Subject.getSubject(acc) == null`. |

### 1.2 `Utilities.getPrincipals()` change
`Utilities.getPrincipals()` (line ~463) now accepts `SpiffePrincipal` alongside `X500Principal` when resolving principals from JERI constraint elements.

### 1.3 Test classes (jgdms-jeri, `src/test/java/net/jini/jeri/ssl/`)
| Class | # tests | Stage |
|---|---|---|
| `SpiffePrincipalTest` | 38 | 1 |
| `FileSvidSourceTest` | 38 | 2 |
| `SpiffeLoginModuleTest` | 22 | 3 |
| `SpiffeCredentialManagerTest` | 36 | 4 |

Test SVID PEM fixtures live in `src/test/resources/spiffe/` (two subdirs: `ca/`, `reggie/`).  
A `test.policy` file is at `src/test/resources/test.policy` for the `dirtychai` SecurityManager profile.

### 1.4 `dirtychai` Maven profile (jgdms-jeri/pom.xml, line 153+)
Activated by `-Pdirtychai` on the command line. Sets up `java.security.manager` system property and `test.policy` arg so the four SPIFFE unit tests run with SecurityManager active under DirtyChai JDK.

### 1.5 CI workflow: `.github/workflows/spiffe-unit-tests.yml`
Triggered on push/PR touching `JGDMS/jgdms-jeri/**`, `JGDMS/jgdms-platform/**`, `JGDMS/jgdms-collections/**`, `JGDMS/jgdms-activation-parameters/**`, `qa/harness/trust/spiffe/**`, `qa/harness/trust/spiffelogins`, or the workflow file itself.

Two-variant matrix (`fail-fast: false`):

| Matrix variant | `use-dirtychai` | Behaviour |
|---|---|---|
| `Temurin-17 (system)` | false | Always runs; uses GitHub-hosted Temurin 17. |
| `DirtyChai (if available)` | true | Degrades gracefully: if `gh release download dirty-chai-latest --repo pfirmstone/DirtyChai` fails, all subsequent steps are skipped (no failure). |

**Steps (both variants where applicable):**
1. `Checkout` (actions/checkout@v4)  
2. `Install DirtyChai JDK` — downloads `jdk-linux-x64.tar.gz` + sha256, verifies, installs to `/opt/dirtychai`, sets `JAVA_HOME`/`PATH`. Uses `GH_TOKEN: ${{ github.token }}`.  
3. `Check DirtyChai availability` — sets `skip=true/false` output.  
4. `Cache Maven local repository` — key: `{OS}-maven-spiffe-{hashFiles('JGDMS/**/pom.xml')}`.  
5. **`Install Pack200-ex-openjdk into local Maven repository`** — reads `<pack200.version>` from `pom.xml`, downloads jar from `pfirmstone/Pack200-ex-openjdk` GitHub Releases (not Packages), installs with `mvn install:install-file`. Uses `GH_TOKEN: ${{ secrets.GITHUB_TOKEN }}`.  
6. `Build dependencies` — `mvn install -DskipTests -pl jgdms-platform,jgdms-jeri --also-make`. `GITHUB_TOKEN` exported.  
7–10. Stages 1–4: individual test classes with optional `-Pdirtychai`.  
11. `Upload Surefire reports` (`if: always()`) — artifact name `surefire-reports-{jdk-label}`.

---

## 2. Key design decisions and constraints

### Pack200 dependency
`au.net.zeus.pack200-ex-openjdk:Pack200-ex-openjdk:1.26.1` is a **compile-scope** dependency of `jgdms-platform`. It is published as a **GitHub Release asset** (`Pack200-ex-openjdk-1.26.1.jar` on the `1.26.1` release at `pfirmstone/Pack200-ex-openjdk`), not via GitHub Packages or Maven Central. The `github-pfirmstone-pack200` repository entry in `~/.m2/settings.xml` (or `.mvn/settings.xml`) cannot resolve it — it will always 404. The workflow's "Install Pack200-ex-openjdk into local Maven repository" step handles this before the Maven build.

### DirtyChai JDK
DirtyChai is an OpenJDK fork that re-enables `SecurityManager`/`AccessController` (removed in JDK 17+). It is required for full JGDMS security testing. It is published as `dirty-chai-latest` release at `pfirmstone/DirtyChai`. The workflow gracefully skips when not available.

### SPIFFE credential rotation
`SpiffeCredentialManager` uses a `ScheduledExecutorService` (single daemon thread) to fire `renewalLeadSeconds` before SVID expiry. On renewal: old credentials are removed from `Subject.getPrivateCredentials()` and `Subject.getPrincipals()`; new ones are added atomically. `SpiffeSubjectHolder` makes the subject available process-wide when no `Subject` is bound to the current `AccessControlContext`.

### JAAS integration
`SpiffeLoginModule` can be used as a replacement for `SunX509LoginModule` in JAAS config. The `spiffelogins` file at `qa/harness/trust/spiffelogins` maps service login contexts to `SpiffeLoginModule`. The system property `net.jini.jeri.ssl.spiffe.dir` + the `serviceRole` JAAS option locate the correct PEM directory.

---

## 3. What to do next (after merge)

### 3.1 Verify CI is green
After merge the SPIFFE Unit Tests workflow should run and all four stages should pass on `Temurin-17 (system)`. Check the Actions tab.

### 3.2 Generate real SPIFFE SVIDs for QA harness
The `gen-spiffe-svids.sh` script at `qa/harness/trust/gen-spiffe-svids.sh` generates test SVIDs. These need to be regenerated periodically (SVIDs have ~1-year TTL in test fixtures, but SPIRE-issued ones are ~1h). The generated PEM files are checked in at `qa/harness/trust/spiffe/`.

### 3.3 SpiffeCredentialManagerTest — Stage 4 notes
The test at stage 4 uses a stub `SvidSource` and the real `FileSvidSource` against the checked-in test PEM fixtures. The `renewalLeadSeconds` constructor parameter controls when automatic renewal fires. Tests cover:
- Constructor NPE/IAE validation
- `start()` / `close()` lifecycle
- Subject population and principal management
- `FileSvidSource` direct paths and spireDir constructor

### 3.4 Possible next work items
- **Integration with SslEndpointImpl**: ensure `SpiffeSubjectHolder` fallback is exercised by integration tests.
- **`Utilities.getPrincipals()` constraint matching**: write tests that exercise the new `SpiffePrincipal` branch.
- **QA harness integration**: wire `SpiffeLoginModule` into the QA service login configs and run end-to-end JERI TLS tests with SPIFFE credentials.
- **`SpiffeCredentialManager` with real SPIRE agent**: integration test that uses a local SPIRE agent socket, not just file-based PEM.

---

## 4. File map

```
.github/workflows/spiffe-unit-tests.yml       CI workflow

JGDMS/jgdms-jeri/pom.xml                       (dirtychai profile at line ~153)
JGDMS/jgdms-jeri/src/main/java/net/jini/jeri/ssl/
    SpiffePrincipal.java
    SpiffeLoginModule.java
    SpiffeCredentialManager.java               (includes SvidSource, FileSvidSource inner classes)
    SpiffeSubjectHolder.java
    Utilities.java                             (getPrincipals() accepts SpiffePrincipal, line ~463)
    SslEndpointImpl.java                       (SpiffeSubjectHolder fallback, line ~296-300)
    SslServerEndpointImpl.java                 (SpiffeSubjectHolder fallback, line ~590-591)

JGDMS/jgdms-jeri/src/test/java/net/jini/jeri/ssl/
    SpiffePrincipalTest.java       (38 tests, Stage 1)
    FileSvidSourceTest.java        (38 tests, Stage 2)
    SpiffeLoginModuleTest.java     (22 tests, Stage 3)
    SpiffeCredentialManagerTest.java (36 tests, Stage 4)

JGDMS/jgdms-jeri/src/test/resources/
    spiffe/
        ca/ca.pem
        reggie/svid.pem
        reggie/svid_key.pem
    test.policy

JGDMS/jgdms-platform/pom.xml      (Pack200 compile dep, version from root <pack200.version>)
JGDMS/pom.xml                     (<pack200.version>1.26.1</pack200.version>, line ~125)

qa/harness/trust/
    spiffe/                        (QA SVID PEM fixtures, generated by gen-spiffe-svids.sh)
    spiffelogins                   (JAAS config mapping service roles to SpiffeLoginModule)
    gen-spiffe-svids.sh            (script to regenerate test SVIDs)
```

---

## 5. Build commands

```bash
# Build without tests (fast)
cd JGDMS
mvn install -DskipTests

# Run SPIFFE tests only (standard JDK)
mvn test -pl jgdms-jeri -Dtest="SpiffePrincipalTest,FileSvidSourceTest,SpiffeLoginModuleTest,SpiffeCredentialManagerTest"

# Run SPIFFE tests with DirtyChai SecurityManager profile
mvn test -pl jgdms-jeri -Pdirtychai -Dtest="SpiffePrincipalTest,FileSvidSourceTest,SpiffeLoginModuleTest,SpiffeCredentialManagerTest"

# Pre-install Pack200 if building standalone (normally handled by CI)
PACK_VER=$(grep -m1 '<pack200.version>' pom.xml | sed 's|.*<pack200.version>\(.*\)</pack200.version>.*|\1|')
gh release download "${PACK_VER}" --repo pfirmstone/Pack200-ex-openjdk \
    --pattern "Pack200-ex-openjdk-${PACK_VER}.jar" --dir /tmp/pack200-dl
mvn install:install-file \
    -Dfile="/tmp/pack200-dl/Pack200-ex-openjdk-${PACK_VER}.jar" \
    -DgroupId=au.net.zeus.pack200-ex-openjdk \
    -DartifactId=Pack200-ex-openjdk \
    -Dversion="${PACK_VER}" -Dpackaging=jar
```
