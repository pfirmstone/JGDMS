#!/bin/bash

# Licensed to the Apache Software Foundation (ASF) under one
# or more contributor license agreements.  See the NOTICE file
# distributed with this work for additional information
# regarding copyright ownership. The ASF licenses this file
# to you under the Apache License, Version 2.0 (the
# "License"); you may not use this file except in compliance
# with the License. You may obtain a copy of the License at
#
#      http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#

if [ "${RAT_HOME+x}" = "x" ]; then
	echo "Using RAT_HOME=${RAT_HOME}"
else
	echo "Please set RAT_HOME before continuing"
	exit 1
fi

java -jar $RAT_HOME/apache-rat-0.8.jar -d src > RAT_REPORT_src.txt
java -jar $RAT_HOME/apache-rat-0.8.jar -d examples -e *.mf > RAT_REPORT_examples.txt
java -jar $RAT_HOME/apache-rat-0.8.jar -d qa/jtreg -e *.mf > RAT_REPORT_qa_jtreg.txt
