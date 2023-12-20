#!/bin/sh

echo Setting version to $1
mvn org.eclipse.tycho:tycho-versions-plugin:set-version -DnewVersion=$1-SNAPSHOT