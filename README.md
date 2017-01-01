# JGDMS - Java/Jini Global Distributed Micro Services.
## This software is forked from Apache River trunk, it is designed with internet security in mind and provides:
* ObjectInput and ObjectOutput implementations for hardening deserialization in the presence of untrusted input.
* TLSv1.2 Encrypted endpoints for RPC communication over untrusted networks, using RSA and Ephemeral Diffie Hellman key exchange and AES with GCM, non-epheremal DH key exchanges are prohibited.
* IPv6 Multicast Discovery using X500 distinguished names with various integrity checking hash functions provided.
* Unicast Discovery over a TLSv1.2 connection with an SHA-224, SHA-256, SHA-384 or SHA-512 hash function to validate data sent and received at both ends prior to sending a response.
* Dynamically granting DownloadPermission and DeSerialization permission to trusted authenticated lookup services during unicast discovery.
* Lookup Service registrar to search services available from various arbitrary third parties.
* Lookup method that allows authentication of third parties, using a bootstrap proxy, prior granting DownloadPermission and DeSerialization permission for service utilisation.
* Invocation and Method constraints.

## Discussion forum:
https://groups.google.com/forum/#!forum/river-secure-ipv6-discovery

## Notables:
* Worlds fastest, highly scalable, Java security policy provider.
* RFC3986URLClassLoader is much faster than Java's built in URLClassLoader.
* RFC3986 compliant Uri.
* Atomic Serialization outperforms standard Java.
* JERI (Jini Extensible Remote Invocation) outperforms java RMI.
* Unnecessary DNS calls have been eliminated.
* Hi performance lookup service method delays or avoids unnecessary codebase downloads.
