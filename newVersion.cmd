echo Setting version to %1
mvn versions:set -DnewVersion=%1-SNAPSHOT
mvn org.eclipse.tycho:tycho-versions-plugin:set-version -DnewVersion=%1