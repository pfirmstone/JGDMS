#!/bin/bash

function test_tar_release {
	TMP_DIR=tar_release_test

	mkdir $TMP_DIR
	cp dist/apache-river-$VERSION-src.tar.gz $TMP_DIR
	cd $TMP_DIR

	tar xf apache-river-$VERSION-src.tar.gz
	cd apache-river-2.2.0
	
	ant build

	echo
	echo "Release built from TAR correctly"

	cd ../..

	rm -rf $TMP_DIR
}

function test_zip_release {
	TMP_DIR=zip_release_test

	mkdir $TMP_DIR
	cp dist/apache-river-$VERSION-src.zip $TMP_DIR
	cd $TMP_DIR

	unzip apache-river-$VERSION-src.zip
	cd apache-river-$VERSION

	ant build

	echo
	echo "Release built from ZIP correctly"

	cd ../..

	rm -rf $TMP_DIR
}

function sign_all {
	echo
	echo "Signing release artifacts"

	cd dist
	for f in $( ls ); do
		gpg --armor --output $f.asc --detach-sign $f
		gpg --print-md SHA512 $f > $f.sha
	done
	cd ..
}

function rat_report {
	echo
	echo "Running RAT report"

	if [ "${RAT_HOME+x}" = "x" ]; then
		echo "Using RAT_HOME=${RAT_HOME}"
	else
		echo "Please set RAT_HOME before continuing"
		exit 1
	fi

	java -jar $RAT_HOME/apache-rat-0.7.jar -d src > RAT_REPORT_src.txt
	java -jar $RAT_HOME/apache-rat-0.7.jar -d examples -e *.mf > RAT_REPORT_examples.txt
	java -jar $RAT_HOME/apache-rat-0.7.jar -d qa/jtreg -e *.mf > RAT_REPORT_qa_jtreg.txt

	mv RAT_REPORT* dist/
}

function upload_all {
	UPLOAD_DEST=$1

	echo
	echo "Uploading artifacts to $UPLOAD_DEST"

	cd dist
	scp * $UPLOAD_DEST
	cd ..
}

function confirm_continue {
	QUIT_MSG=$1

	echo
	echo "Continue? [Yy]ess [Nn]o"
	read CONTINUE

	[[ 'n' = $CONTINUE || 'N' = $CONTINUE ]] && echo $QUIT_MSG
}

VERSION=3.0.0

echo "Rolling River Release $VERSION"

echo
echo "Checking repo status"

svn st

confirm_continue "Please Update repo state and then re-run this script."

ant release

test_tar_release
test_zip_release

sign_all
rat_report
upload_all thobbs@people.apache.org:~/public_html/river/
