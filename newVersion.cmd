echo Setting version to %1
call mvn org.eclipse.tycho:tycho-versions-plugin:set-version -DnewVersion=%1-SNAPSHOT