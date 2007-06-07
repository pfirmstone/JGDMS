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

package com.sun.jini.discovery.internal;

import com.sun.jini.discovery.DatagramBufferFactory;
import com.sun.jini.discovery.DelayedMulticastAnnouncementDecoder;
import com.sun.jini.discovery.DiscoveryProtocolException;
import com.sun.jini.discovery.MulticastAnnouncement;
import com.sun.jini.discovery.MulticastRequest;
import com.sun.jini.discovery.MulticastRequestEncoder;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.logging.Level;
import java.security.cert.Certificate;
import javax.security.auth.x500.X500Principal;
import javax.security.auth.x500.X500PrivateCredential;
import net.jini.core.constraint.InvocationConstraints;
import net.jini.io.UnsupportedConstraintException;

/**
 * Superclass for client-side providers for the net.jini.discovery.x500.*
 * discovery formats.
 */
public class X500Client
    extends X500Provider
    implements MulticastRequestEncoder, DelayedMulticastAnnouncementDecoder
{
    /**
     * Creates an instance with the given attributes.
     */
    protected X500Client(String formatName,
			 String signatureAlgorithm,
			 int maxSignatureLength,
			 String keyAlgorithm,
			 String keyAlgorithmOID)
    {
	super(formatName,
	      signatureAlgorithm,
	      maxSignatureLength,
	      keyAlgorithm,
	      keyAlgorithmOID);
    }

    // documentation inherited from MulticastRequestEncoder
    public void encodeMulticastRequest(MulticastRequest request,
				       DatagramBufferFactory bufs,
				       InvocationConstraints constraints)
	throws IOException
    {
	if (request == null || bufs == null) {
	    throw new NullPointerException();
	}
	try {
	    X500Constraints cons = X500Constraints.process(constraints, true);

	    // REMIND: instead iterate through constraint-designated principals
	    X500PrivateCredential[] creds = getPrivateCredentials();
	    X500PrivateCredential chosen = null;
	    int best = -1;
	    SecurityException se = null;
	    for (int i = 0; i < creds.length; i++) {
		X500PrivateCredential c = creds[i];
		X500Principal p = c.getCertificate().getSubjectX500Principal();
		int score = cons.checkClientPrincipal(p);
		if (score < 0) {
		    if (logger.isLoggable(Level.FINEST)) {
			logger.log(Level.FINEST,
				   "skipping disallowed principal {0}",
				   new Object[]{ p });
		    }
		    continue;
		}
		try {
		    checkAuthenticationPermission(p, "connect");
		} catch (SecurityException e) {
		    se = e;
		    if (logger.isLoggable(Level.FINE)) {
			logger.log(Level.FINE,
				   "not authorized to use principal {0}",
				   new Object[]{ p });
		    }
		    continue;
		}
		if (score > best) {
		    chosen = c;
		    best = score;
		}
	    }
	    if (chosen == null) {
		UnsupportedConstraintException uce = 
		    new UnsupportedConstraintException(
			"unsupported constraints: " + constraints);
		if (se != null) {
		    // At least one principal was rejected due to lack
		    // of authentication permissions
		    secureThrow(se, uce);		    
		}
		throw uce;
	    }
	    if (logger.isLoggable(Level.FINEST)) {
		logger.log(Level.FINEST, "using principal {0}",
			   new Object[]{ chosen });
	    }
	    SigningBufferFactory sbf = new SigningBufferFactory(bufs, chosen);
	    Plaintext.encodeMulticastRequest(request, sbf);
	    sbf.sign();

	} catch (IOException e) {
	    throw e;
	} catch (SecurityException e) {
	    throw e;
	} catch (Exception e) {
	    throw new DiscoveryProtocolException(null, e);
	}
    }

    // documentation inherited from MulticastAnnouncementDecoder
    public MulticastAnnouncement decodeMulticastAnnouncement(
					    ByteBuffer buf,
					    InvocationConstraints constraints,
                                            boolean delayConstraintCheck)
	throws IOException
    {
	try {
	    int len = buf.getInt();
	    ByteBuffer data = buf.duplicate();
	    data.limit(data.position() + len);
	    buf.position(data.limit());

	    X500Principal p = new X500Principal(Plaintext.getUtf(buf));
	    ByteBuffer signed = (ByteBuffer) data.duplicate().position(0);
	    MulticastAnnouncement ma =
		Plaintext.decodeMulticastAnnouncement(data);

            ma = new X500MulticastAnnouncement(ma, constraints, p, 
					       buf.duplicate(), signed);
            if (!delayConstraintCheck) {
                ma.checkConstraints();
            }
            return ma;
        } catch (IOException e) {
            throw e;
        } catch (SecurityException e) {
            throw e;
        } catch (Exception e) {
            throw new DiscoveryProtocolException(null, e);
        }
    }
    
    public MulticastAnnouncement decodeMulticastAnnouncement(ByteBuffer buf,
                                            InvocationConstraints constraints)
        throws IOException
    {
        return decodeMulticastAnnouncement(buf, constraints, false);
    }
    
    private class X500MulticastAnnouncement extends MulticastAnnouncement {
	private final InvocationConstraints constraints;
	private final X500Principal p;
	private final ByteBuffer signature;
	private final ByteBuffer signed;
	private X500MulticastAnnouncement(MulticastAnnouncement plainMA,
					  InvocationConstraints constraints,
					  X500Principal p,
					  ByteBuffer signature,
					  ByteBuffer signed)
	{
	    super(plainMA.getSequenceNumber(),  plainMA.getHost(), 
		    plainMA.getPort(), plainMA.getGroups(),
		    plainMA.getServiceID());
	    this.constraints = constraints;
	    this.p = p;
	    this.signature = signature;
	    this.signed = signed;
	}

	public void checkConstraints() throws IOException {
	    try {
		X500Constraints cons = X500Constraints.process(constraints,
							       false);
		if (cons.checkServerPrincipal(p) < 0) {
		    throw new UnsupportedConstraintException(
			"principal not allowed: " + p);
		}
		Certificate cert = getCertificate(p);
		if (cert == null) {
		    throw new DiscoveryProtocolException(
			"unknown principal: " + p);
		}
		if (logger.isLoggable(Level.FINEST)) {
		    logger.log(Level.FINEST, "mapped principal {0} to {1}",
			       new Object[]{ p, cert });
		}
		if (!verify(signed.duplicate(), signature.duplicate(), 
			    cert.getPublicKey()))
		{
		    throw new DiscoveryProtocolException(
			"signature verification failed: " + p);
		}
	    } catch (IOException e) {
		throw e;
	    } catch (SecurityException e) {
		throw e;
	    } catch (Exception e) {
		throw new DiscoveryProtocolException(null, e);
	    }       
	}
    }
}
