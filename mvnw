#!/bin/sh
set -eu

base_dir=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
distribution_url=$(sed -n 's/^distributionUrl=//p' "$base_dir/.mvn/wrapper/maven-wrapper.properties")
archive_name=${distribution_url##*/}
version=${archive_name#apache-maven-}
version=${version%-bin.zip}
maven_home=${MAVEN_USER_HOME:-"$HOME/.m2"}/wrapper/dists/apache-maven-$version-bin/octopus/apache-maven-$version

if [ ! -x "$maven_home/bin/mvn" ]; then
  archive_path=${TMPDIR:-/tmp}/$archive_name
  mkdir -p "$(dirname "$maven_home")"
  if command -v curl >/dev/null 2>&1; then
    curl --fail --location --output "$archive_path" "$distribution_url"
  else
    wget --output-document="$archive_path" "$distribution_url"
  fi
  unzip -q -o "$archive_path" -d "$(dirname "$maven_home")"
fi

exec "$maven_home/bin/mvn" "$@"
