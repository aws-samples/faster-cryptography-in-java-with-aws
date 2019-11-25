#!/bin/bash
set -euf -o pipefail  # Makes bash behave sanely, see https://sipb.mit.edu/doc/safe-shell/

txtblk='\e[0;30m' # Black - Regular
txtred='\e[0;31m' # Red
txtgrn='\e[0;32m' # Green
txtylw='\e[0;33m' # Yellow
txtrst='\e[0m'    # Text Reset

function msg {
    printf "$*$txtrst\n" 1>&2
}

docker_files="-f docker-compose.yml -f docker-compose.desktop.yml"
while :; do
    case ${1-} in
        -debug)
            docker_files="$docker_files -f docker-compose.desktop-debug.yml"
            msg "Desktop debug mode enabled"
            shift
            break
            ;;
        -?*)
            msg "Unknown option: $1"
            msg "$usage"
            exit 1
            ;;
        *)
            break
    esac

    shift
done

gradle clean
gradle docker
docker-compose $docker_files up --build

