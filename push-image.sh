#!/bin/bash
set -euf -o pipefail  # Makes bash behave sanely, see https://sipb.mit.edu/doc/safe-shell/

# This script can be used to manually push a container into a registry.

DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" >/dev/null 2>&1 && pwd )"
txtblk='\e[0;30m' # Black - Regular
txtred='\e[0;31m' # Red
txtgrn='\e[0;32m' # Green
txtylw='\e[0;33m' # Yellow
txtrst='\e[0m'    # Text Reset

function msg {
    printf "$*$txtrst\n" 1>&2
}

usage="$0 [stage]"
if [[ $# -ne 1 ]]; then
    msg "Invalid arguments, usage: $usage"
    exit 1
fi

stage="$1"
image_name="com.amazonaws.fcj/faster-cryptography-in-java"
repo_name="com.amazonaws.fcj/faster-cryptography-in-java-$stage"

$(aws ecr get-login --no-include-email)

pushd "$DIR" > /dev/null 2>&1
./gradlew clean docker
popd > /dev/null 2>&1

tag="latest"

repo_uri="$(aws ecr describe-repositories --repository-names "$repo_name"| jq -r '.repositories[0]["repositoryUri"]')"
repo_uri_with_tag="$repo_uri:$tag"
docker tag "$image_name":"$tag" "$repo_uri_with_tag"

docker push "$repo_uri_with_tag"
