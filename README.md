# river-internet
## This software is designed with internet security in mind, it provides:
* ObjectInput and ObjectOutput implementations for hardening deserialization in the presence of untrusted input.
* TLSv1.2 Encrypted endpoints for RPC communication over untrusted networks, using RSA and Ephemeral Diffie Hellman key exchange and AES with GCM, non-epheremal DH key exchanges are prohibited.
* IPv6 Multicast Discovery using X500 distinguished names with various integrity checking hash functions provided.
* Unicast Discovery over a TLSv1.2 connection with an SHA-224, SHA-256, SHA-384 and SHA-512 hash function to validate data sent and received at both ends prior to sending a response.
* Dynamically granting DownloadPermission and DeSerialization permission to trusted authenticated lookup services during unicast discovery.
* A lookup service to find available services.
* Lookup method that allows both ends to authenticate using a bootstrap proxy, prior service utilisation.
