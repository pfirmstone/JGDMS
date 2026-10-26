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

package org.apache.river.discovery.internal;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.security.GeneralSecurityException;
import java.security.cert.CertPath;
import java.security.cert.CertPathValidator;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.PKIXParameters;
import java.security.cert.TrustAnchor;
import java.security.cert.X509Certificate;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.security.auth.Subject;
import javax.security.auth.x500.X500Principal;
import net.jini.security.Security;

/**
 * Support routines shared by the SPIFFE discovery format providers.
 *
 * <p>These are static rather than inherited because the two things that need
 * them cannot share a superclass: the client side must extend
 * {@link X500Client} and the server side {@link X500Server}, and both of those
 * already extend {@link X500Provider}.  Holding the behaviour here keeps both
 * hierarchies intact.  It is possible only because the trust material is
 * obtained from {@link Security#spiffeTrustBundle()} at each use rather than
 * cached in an instance field.
 *
 * <p>This class is deliberately package-private and lives beside the other
 * shared format implementation rather than in a {@code spiffe.*} package, which
 * holds only the thin per-format wrappers -- the same split the {@code x500.*}
 * formats use.  {@link #validateSpiffeCert} decides which certificate a
 * signature is verified against, so keeping it unreachable from outside this
 * package avoids widening that decision into an extension point.
 *
 * @since 3.1.1
 */
final class SpiffeSupport {

    private static final Logger logger =
	Logger.getLogger("org.apache.river.discovery.spiffe");

    /**
     * {@code Subject.processWorker()}, or {@code null} on a JDK that does not
     * have it.  Resolved reflectively: the method is specific to the DirtyChai
     * JDK, and JGDMS must still build and run on a stock JDK.  This mirrors
     * {@code net.jini.jeri.ssl.Utilities.spiffeWorkerSubject()}.
     */
    private static final Method PROCESS_WORKER = findProcessWorker();

    private static Method findProcessWorker() {
	try {
	    return Subject.class.getMethod("processWorker");
	} catch (NoSuchMethodException e) {
	    // Expected and benign on a stock OpenJDK, which has no such method.
	    // Recorded at FINE only: SPIFFE discovery will simply have no
	    // process-wide workload identity to sign with on this JDK.
	    if (logger.isLoggable(Level.FINE)) {
		logger.log(Level.FINE,
		    "no Subject.processWorker() on this JDK; SPIFFE discovery "
		    + "will have no process-wide workload subject to sign with",
		    e);
	    }
	    return null;
	} catch (Exception e) {
	    // Not expected: SecurityException (e.g. a missing reflection
	    // permission checking the method itself) or some other resolution
	    // fault.  This is a real fault, not the routine "stock JDK" case,
	    // and it must be visible -- silently returning null here would
	    // present identically to a genuinely absent method, hiding the
	    // fault that needs fixing.
	    logger.log(Level.WARNING,
		"unexpected failure resolving Subject.processWorker(); "
		+ "SPIFFE discovery will have no process-wide workload "
		+ "subject to sign with", e);
	    return null;
	}
    }

    /**
     * Returns the ambient SPIFFE workload {@code Subject}, or {@code null} if
     * this JDK has no such notion or none is currently established.
     *
     * <p>The workload identity is process-wide, not thread-scoped: it is not
     * bound by {@code Subject.callAs} or {@code Subject.doAs} -- indeed those
     * reject a {@code WorkerSubject} outright, because binding is meaningless
     * for an identity that is always in scope.  It therefore cannot be reached
     * through the {@code AccessControlContext}, which carries principals only.
     *
     * <p>The result <strong>must not be cached</strong>.  SVIDs rotate
     * (typically hourly) and a retained reference goes stale; obtain a fresh one
     * at each use.  Verification tolerates rotation because trust is anchored at
     * the issuing CA, but the signer must still present its current key.
     *
     * <p><strong>Why there is no SPI that hands this Subject to the discovery
     * call path.</strong>  There used to be one --
     * {@code net.jini.discovery.DiscoveryCredentialProvider}, with a SPIFFE
     * implementation {@code net.jini.jeri.ssl.SpiffeDiscoveryCredentialProvider}
     * -- and all of it was removed as unimplementable.  That SPI's contract was
     * "return a {@code Subject} to bind": the caller passed the result to
     * {@code Subject.callAs}, which is exactly what a {@code WorkerSubject}
     * cannot be used for.  {@code callAs} and {@code doAs} reject one outright
     * with {@code IllegalArgumentException}, deliberately, because binding is
     * meaningless for an identity that is always in scope -- so a SPIFFE
     * implementation of that SPI could only ever return {@code null}, and did.
     * The conventional (X.500) implementation degenerated the other way, into a
     * constant function returning the login {@code Subject} the caller already
     * held; that case is now served by capturing the caller's identity at
     * {@code AbstractLookupDiscovery} construction.  With no possible
     * implementation on either side, the SPI was deleted rather than left as an
     * extension point that cannot be extended.
     *
     * <p>The consequence for SPIFFE is the design point this method embodies:
     * the workload identity is never handed to the call path in advance, it is
     * fetched <em>here</em>, fresh, at each signing and verification.  Do not
     * reintroduce a provider-shaped seam for it.  Full reasoning:
     * {@code docs/DESIGN-discovery-credential-resolution.md} &sect;11.6 and
     * &sect;11.8.
     *
     * @return the workload subject, or {@code null}
     */
    static Subject processWorkerSubject() {
	if (PROCESS_WORKER == null) {
	    return null;
	}
	try {
	    // processWorker() is an instance method; the receiver is unused.
	    return (Subject) PROCESS_WORKER.invoke(new Subject());
	} catch (InvocationTargetException e) {
	    // The target method itself threw -- Method.invoke wraps whatever
	    // processWorker() threw in here, so a denial (SecurityException,
	    // e.g. a missing AuthPermission) surfaces as this wrapper's cause,
	    // not directly.  This is a real fault that leaves SPIFFE discovery
	    // with no credentials, presenting exactly as "SPIFFE has nothing
	    // to sign with".  It must be visible at a level an operator sees,
	    // and it must reach any policy-authoring tool that scans the log
	    // for denied permissions -- swallowing it here would make the
	    // required grant undiscoverable by that tooling and force it to
	    // be hand-written.  Not rethrown: returning null on failure is
	    // still the correct behaviour, this only makes the failure
	    // visible.
	    Throwable cause = e.getCause();
	    if (cause instanceof SecurityException) {
		logger.log(Level.WARNING,
		    "denied access to Subject.processWorker(); SPIFFE "
		    + "discovery will have no process-wide workload subject "
		    + "to sign with -- check for a missing "
		    + "AuthPermission(\"processWorker\") or similar grant",
		    cause);
	    } else {
		logger.log(Level.WARNING,
		    "Subject.processWorker() threw; SPIFFE discovery will "
		    + "have no process-wide workload subject to sign with",
		    (cause != null) ? cause : e);
	    }
	    return null;
	} catch (SecurityException e) {
	    // Denied by the reflective call itself (e.g. access-checking the
	    // method), rather than by the method body.  Same fault class as
	    // above: must be visible, not silently swallowed.
	    logger.log(Level.WARNING,
		"denied access to Subject.processWorker(); SPIFFE discovery "
		+ "will have no process-wide workload subject to sign with -- "
		+ "check for a missing AuthPermission(\"processWorker\") or "
		+ "similar grant", e);
	    return null;
	} catch (Exception e) {
	    // Reflective invocation fault other than denial (e.g. illegal
	    // access or argument mismatch).  Also not the routine case --
	    // make it visible rather than silently disappearing.
	    logger.log(Level.WARNING,
		"unexpected failure invoking Subject.processWorker(); SPIFFE "
		+ "discovery will have no process-wide workload subject to "
		+ "sign with", e);
	    return null;
	}
    }

    /**
     * Validates a signer's leaf certificate against the SPIRE trust bundle,
     * replacing the static trust-store lookup used by the x500 formats.
     *
     * <p>SVIDs are issued dynamically and rotate, so a verifier cannot hold a
     * pre-populated store of peer certificates; the leaf travels in the packet
     * and is validated against the issuing CAs here.
     *
     * @param principal the signer's principal, as carried in the packet
     * @param leafCert the signer's leaf certificate, as carried in the packet
     * @return the validated certificate
     * @throws CertificateException if the certificate is unusable, does not
     *         match {@code principal}, or no trust bundle is available
     * @throws GeneralSecurityException if path validation fails
     */
    static Certificate validateSpiffeCert(X500Principal principal,
					  X509Certificate leafCert)
	throws CertificateException, GeneralSecurityException
    {
	if (principal == null || leafCert == null) {
	    throw new NullPointerException();
	}
	// Deployment requirement: SPIFFE IDs live in the URI SAN, and an SVID may
	// carry an empty Subject DN, but the multicast protocol identifies signers
	// by X500Principal.  Fail with something an operator can act on.
	if (principal.getName().isEmpty()) {
	    throw new CertificateException(
		"SPIFFE SVID has an empty Subject DN, which this discovery " +
		"format cannot use to identify a signer.  Configure SPIRE " +
		"registration entries to issue a non-empty Subject DN, e.g. " +
		"CN=lookup for spiffe://<domain>/host/lookup.");
	}
	if (!principal.equals(leafCert.getSubjectX500Principal())) {
	    throw new CertificateException(
		"principal name mismatch: packet claims " + principal +
		" but certificate is for " + leafCert.getSubjectX500Principal());
	}

	X509Certificate[] bundle = Security.spiffeTrustBundle();
	if (bundle == null || bundle.length == 0) {
	    throw new CertificateException(
		"no SPIRE trust bundle is available; register a " +
		"SpiffeTrustProvider via Security.registerSpiffeTrustProvider()");
	}

	Set<TrustAnchor> anchors = new HashSet<TrustAnchor>(bundle.length);
	for (int i = 0; i < bundle.length; i++) {
	    anchors.add(new TrustAnchor(bundle[i], null));
	}
	PKIXParameters params = new PKIXParameters(anchors);
	// SVIDs are short-lived; SPIRE publishes no CRL or OCSP responder.
	params.setRevocationEnabled(false);

	CertificateFactory cf = CertificateFactory.getInstance("X.509");
	CertPath certPath =
	    cf.generateCertPath(Collections.singletonList(leafCert));
	CertPathValidator.getInstance("PKIX").validate(certPath, params);
	return leafCert;
    }

    private SpiffeSupport() {
    }
}
