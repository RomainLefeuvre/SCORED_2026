#!/bin/bash
CURRENT_VERSION="da1a9c73df41abeb2b06e0b0e4f986af6f2fb18e"
git clone https://gitlab.softwareheritage.org/swh/devel/swh-graph.git
cd swh-graph

git checkout -f da1a9c73df41abeb2b06e0b0e4f986af6f2fb18e
cd java 

sed -i "s/\${git.closest.tag.name}/$CURRENT_VERSION/" pom.xml
mvn clean install -DskipTests
