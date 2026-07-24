#!/usr/bin/env bash
#
# Builds the showcase and runs the two library-only demonstrations, then the
# automated checks. For the third demonstration (matching without the class),
# see demo2-match-without-the-class/run.sh.
set -euo pipefail
module="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$module"

echo "Building the showcase (resolves two libraries from the local repository)..."
mvn -q package -DskipTests

cp="target/classes;target/lib/*"

echo
echo "########################################################################"
echo "# Demonstration 1: Same object, same bytes -- everywhere"
echo "########################################################################"
java -cp "$cp" au.net.zeus.jgdms.showcase.demo.SameObjectSameBytesDemo

echo
echo "(Running it a second time in a fresh process -- the checksum is identical,"
echo " because the bytes depend only on the value, not on the run.)"
java -cp "$cp" au.net.zeus.jgdms.showcase.demo.SameObjectSameBytesDemo | grep "checksum (SHA-256) of pass 1" | sed 's/^/  fresh process: /'

echo
echo "########################################################################"
echo "# Demonstration 3: The shape description travels once"
echo "########################################################################"
java -cp "$cp" au.net.zeus.jgdms.showcase.demo.SchemaSentOnceDemo

echo
echo "########################################################################"
echo "# Automated checks (a build server can run these)"
echo "########################################################################"
mvn -q test -DredirectTestOutputToFile=true
echo "All automated checks passed."
