#!/bin/sh

set -e
set -x

# reset version code/name to current date
versionCodeDate=`date +%s`
versionNameDate=`date +%Y-%m-%d_%H.%M.%S`
projectname=`sed -n 's,.*name="app_name">\(.*\)<.*,\1,p' res/values/strings.xml`

sed -i \
    -e "s,android:versionCode=\"[0-9][0-9]*\",android:versionCode=\"$versionCodeDate\"," \
    -e "s,android:versionName=\"\([^\"][^\"]*\)\",android:versionName=\"\1.$versionNameDate\"," \
    AndroidManifest.xml

. ~/.android/bashrc
android update project --path . --name $projectname
