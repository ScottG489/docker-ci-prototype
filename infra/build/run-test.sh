#!/bin/bash
set -ex

source /opt/build/build_functions.sh

trap cleanup EXIT
cleanup() {
  cd "$(git rev-parse --show-toplevel)/infra/tf/test-env"
  terraform destroy --auto-approve
}

tf_apply "infra/tf/test-env"

set +x
setup_application_configuration "$1"
set -x

ansible_deploy "infra/tf/test-env"

run_tests "infra/tf/test-env"
