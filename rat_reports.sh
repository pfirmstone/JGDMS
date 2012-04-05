#!/bin/bash

if [ "${RAT_HOME+x}" = "x" ]; then
	echo "Using RAT_HOME=${RAT_HOME}"
else
	echo "Please set RAT_HOME before continuing"
	exit 1
fi

java -jar $RAT_HOME/apache-rat-0.8.jar -d src > RAT_REPORT_src.txt
java -jar $RAT_HOME/apache-rat-0.8.jar -d examples -e *.mf > RAT_REPORT_examples.txt
java -jar $RAT_HOME/apache-rat-0.8.jar -d qa/jtreg -e *.mf > RAT_REPORT_qa_jtreg.txt
