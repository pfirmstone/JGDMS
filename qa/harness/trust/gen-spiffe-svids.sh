#!/bin/bash
# ============================================================================
# gen-spiffe-svids.sh
#
# Generates a local test CA and per-service SPIFFE SVIDs (X.509 certificates
# with SPIFFE URI SubjectAlternativeNames) for use in the JGDMS QA harness
# "spiffe" configuration set.
#
# Each SVID has:
#   - Subject DN:  CN=<Service>  (title-case, matching existing jsse identities)
#   - URI SAN:     spiffe://test.jgdms.local/svc/<service>  (service roles)
#                  spiffe://test.jgdms.local/client/test     (client role)
#   - Key type:    EC P-256
#   - Validity:    10 years (only for test/CI use — not for production)
#
# Service roles generated:
#   reggie fiddler mahalo norm mercury outrigger tester phoenix group
#
# Client role generated (SPIFFE ID follows /client/ path per context_8.md §14):
#   client  →  spiffe://test.jgdms.local/client/test
#
# Outputs (relative to OUTPUT_DIR):
#   ca/ca.pem                   — test CA certificate (PEM)
#   ca/ca_key.pem               — test CA private key (PEM, PKCS#8)
#   <role>/svid.pem             — SVID certificate PEM (leaf + CA chain)
#   <role>/svid_key.pem         — SVID private key PEM (PKCS#8)
#   truststore.jks              — JKS trust store containing test CA
#
# Prerequisites:
#   openssl >= 1.1.1  (tested with 3.x)
#   keytool (from JDK)
#
# Usage:
#   cd qa/harness/trust
#   bash gen-spiffe-svids.sh
# ============================================================================

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
OUTPUT_DIR="${SCRIPT_DIR}/spiffe"

TRUST_DOMAIN="test.jgdms.local"
VALIDITY_DAYS=3650       # WARNING: 10-year validity — FOR CI/TEST USE ONLY; never use in production
CA_KEY_BITS=""           # unused for EC
CA_KEY_CURVE="P-256"
TRUSTSTORE_PASSWORD="spiffetrustpw"
TRUSTSTORE_FILE="${SCRIPT_DIR}/spiffetrust.jks"

# Service roles using /svc/ SPIFFE path
ROLES="reggie fiddler mahalo norm mercury outrigger tester phoenix group"

# Client role uses /client/ SPIFFE path (see SPIFFE ID scheme in context_8.md §14)
CLIENT_ROLES="client"

# All roles (service + client) — for directory creation
ALL_ROLES="${ROLES} ${CLIENT_ROLES}"

# Title-case mapping: role → CN value used in Subject DN
declare -A CN_MAP=(
    [reggie]="Reggie"
    [fiddler]="Fiddler"
    [mahalo]="Mahalo"
    [norm]="Norm"
    [mercury]="Mercury"
    [outrigger]="Outrigger"
    [tester]="Tester"
    [phoenix]="Phoenix"
    [group]="Group"
    [client]="Test Client"
)

echo "==> Creating output directory: ${OUTPUT_DIR}"
mkdir -p "${OUTPUT_DIR}/ca"
for role in ${ALL_ROLES}; do
    mkdir -p "${OUTPUT_DIR}/${role}"
done

# ----------------------------------------------------------------------------
# 1. Generate test CA
# ----------------------------------------------------------------------------
echo "==> Generating test CA key and self-signed certificate"
CA_KEY="${OUTPUT_DIR}/ca/ca_key.pem"
CA_CERT="${OUTPUT_DIR}/ca/ca.pem"
CA_SERIAL="${OUTPUT_DIR}/ca/serial.txt"

if [ ! -f "${CA_SERIAL}" ]; then
    echo "01" > "${CA_SERIAL}"
fi

openssl genpkey \
    -algorithm EC \
    -pkeyopt ec_paramgen_curve:P-256 \
    -out "${CA_KEY}" \
    2>/dev/null

openssl req \
    -new -x509 \
    -key "${CA_KEY}" \
    -out "${CA_CERT}" \
    -days ${VALIDITY_DAYS} \
    -subj "/O=JGDMS Test/CN=JGDMS Test CA" \
    -extensions v3_ca \
    -addext "basicConstraints=critical,CA:TRUE" \
    -addext "keyUsage=critical,keyCertSign,cRLSign" \
    2>/dev/null

echo "    CA certificate: ${CA_CERT}"

# ----------------------------------------------------------------------------
# 2. Generate per-service SVIDs (using /svc/ SPIFFE path)
# ----------------------------------------------------------------------------
for role in ${ROLES}; do
    cn="${CN_MAP[$role]}"
    spiffe_id="spiffe://${TRUST_DOMAIN}/svc/${role}"
    svid_key="${OUTPUT_DIR}/${role}/svid_key.pem"
    svid_csr="${OUTPUT_DIR}/${role}/svid.csr"
    svid_cert="${OUTPUT_DIR}/${role}/svid.pem"
    ext_file="${OUTPUT_DIR}/${role}/san.cnf"

    echo "==> Generating SVID for role '${role}' (CN=${cn}, SPIFFE ID=${spiffe_id})"

    # Generate SVID private key (EC P-256, PKCS#8 format)
    openssl genpkey \
        -algorithm EC \
        -pkeyopt ec_paramgen_curve:P-256 \
        -out "${svid_key}" \
        2>/dev/null

    # Create a temporary OpenSSL config with the SPIFFE URI SAN
    cat > "${ext_file}" <<EOF
[req]
distinguished_name = dn
req_extensions     = svid_ext
prompt             = no

[dn]
CN = ${cn}

[svid_ext]
subjectAltName = @alt_names

[alt_names]
URI.1 = ${spiffe_id}

[svid_sign_ext]
subjectAltName     = @alt_names
basicConstraints   = critical,CA:FALSE
keyUsage           = critical,digitalSignature,keyEncipherment
extendedKeyUsage   = serverAuth,clientAuth
EOF

    # Generate CSR
    openssl req \
        -new \
        -key "${svid_key}" \
        -out "${svid_csr}" \
        -config "${ext_file}" \
        2>/dev/null

    # Sign with test CA, injecting the SPIFFE URI SAN
    openssl x509 \
        -req \
        -in "${svid_csr}" \
        -CA "${CA_CERT}" \
        -CAkey "${CA_KEY}" \
        -CAserial "${CA_SERIAL}" \
        -out "${svid_cert}" \
        -days ${VALIDITY_DAYS} \
        -extfile "${ext_file}" \
        -extensions svid_sign_ext \
        2>/dev/null

    # Append the CA certificate to the svid.pem so the chain is complete
    cat "${CA_CERT}" >> "${svid_cert}"

    # Clean up CSR and temp config
    rm -f "${svid_csr}" "${ext_file}"

    echo "    SVID:  ${svid_cert}"
    echo "    Key:   ${svid_key}"
done

# ----------------------------------------------------------------------------
# 3. Generate client SVID (using /client/ SPIFFE path per context_8.md §14)
# ----------------------------------------------------------------------------
for role in ${CLIENT_ROLES}; do
    cn="${CN_MAP[$role]}"
    # Client IDs follow the /client/<name> path (not /svc/)
    spiffe_id="spiffe://${TRUST_DOMAIN}/client/test"
    svid_key="${OUTPUT_DIR}/${role}/svid_key.pem"
    svid_csr="${OUTPUT_DIR}/${role}/svid.csr"
    svid_cert="${OUTPUT_DIR}/${role}/svid.pem"
    ext_file="${OUTPUT_DIR}/${role}/san.cnf"

    echo "==> Generating client SVID for role '${role}' (CN=${cn}, SPIFFE ID=${spiffe_id})"

    # Generate SVID private key (EC P-256, PKCS#8 format)
    openssl genpkey \
        -algorithm EC \
        -pkeyopt ec_paramgen_curve:P-256 \
        -out "${svid_key}" \
        2>/dev/null

    # Create a temporary OpenSSL config with the SPIFFE URI SAN
    cat > "${ext_file}" <<EOF
[req]
distinguished_name = dn
req_extensions     = svid_ext
prompt             = no

[dn]
CN = ${cn}

[svid_ext]
subjectAltName = @alt_names

[alt_names]
URI.1 = ${spiffe_id}

[svid_sign_ext]
subjectAltName     = @alt_names
basicConstraints   = critical,CA:FALSE
keyUsage           = critical,digitalSignature,keyEncipherment
extendedKeyUsage   = serverAuth,clientAuth
EOF

    # Generate CSR
    openssl req \
        -new \
        -key "${svid_key}" \
        -out "${svid_csr}" \
        -config "${ext_file}" \
        2>/dev/null

    # Sign with test CA, injecting the SPIFFE URI SAN
    openssl x509 \
        -req \
        -in "${svid_csr}" \
        -CA "${CA_CERT}" \
        -CAkey "${CA_KEY}" \
        -CAserial "${CA_SERIAL}" \
        -out "${svid_cert}" \
        -days ${VALIDITY_DAYS} \
        -extfile "${ext_file}" \
        -extensions svid_sign_ext \
        2>/dev/null

    # Append the CA certificate to the svid.pem so the chain is complete
    cat "${CA_CERT}" >> "${svid_cert}"

    # Clean up CSR and temp config
    rm -f "${svid_csr}" "${ext_file}"

    echo "    SVID:  ${svid_cert}"
    echo "    Key:   ${svid_key}"
done

# ----------------------------------------------------------------------------
# 4. Import test CA into JKS trust store for use with javax.net.ssl.trustStore
# ----------------------------------------------------------------------------
echo "==> Creating JKS trust store: ${TRUSTSTORE_FILE}"
rm -f "${TRUSTSTORE_FILE}"
keytool -importcert \
    -noprompt \
    -alias spiffe-test-ca \
    -file "${CA_CERT}" \
    -keystore "${TRUSTSTORE_FILE}" \
    -storetype JKS \
    -storepass "${TRUSTSTORE_PASSWORD}" \
    2>/dev/null

echo "    Trust store:  ${TRUSTSTORE_FILE}"
echo "    Password:     ${TRUSTSTORE_PASSWORD}"

# ----------------------------------------------------------------------------
# 5. Summary
# ----------------------------------------------------------------------------
echo ""
echo "==> Done.  SPIFFE test credentials written to: ${OUTPUT_DIR}"
echo ""
echo "    Service roles (spiffe://${TRUST_DOMAIN}/svc/<role>):"
for role in ${ROLES}; do
    echo "      ${OUTPUT_DIR}/${role}/"
done
echo "    Client role (spiffe://${TRUST_DOMAIN}/client/test):"
echo "      ${OUTPUT_DIR}/client/"
echo ""
echo "    To use these credentials, add the following JVM args:"
echo "    -Djava.security.auth.login.config=<path>/spiffelogins"
echo "    -Dnet.jini.jeri.ssl.spiffe.dir=${OUTPUT_DIR}"
echo "    -Djavax.net.ssl.trustStore=${TRUSTSTORE_FILE}"
echo "    -Djavax.net.ssl.trustStorePassword=${TRUSTSTORE_PASSWORD}"
echo "    -Djavax.net.ssl.trustStoreType=JKS"
echo ""
