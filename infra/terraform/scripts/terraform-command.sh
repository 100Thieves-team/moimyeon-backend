#!/usr/bin/env bash

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../.." && pwd)"
TERRAFORM_ROOT="${ROOT_DIR}/infra/terraform"
TERRAFORM_BIN="${TERRAFORM_BIN:-terraform}"

command_name="${1:-}"
environment="${2:-}"

usage() {
  cat >&2 <<'EOF'
Usage:
  terraform-command.sh fmt
  terraform-command.sh init <shared|dev|live>
  terraform-command.sh validate <shared|dev|live>
  terraform-command.sh plan <shared|dev|live> <plan-output-path>
  terraform-command.sh state-list <shared|dev|live>
  terraform-command.sh output-raw <shared|dev|live> <output-name>
  terraform-command.sh output-json <shared|dev|live> <output-name>

Official commands always use the committed <environment>.tfvars file and never
accept local overrides. Run ad-hoc Terraform directly for local experiments.
EOF
  exit 2
}

if [ "${command_name}" = "fmt" ]; then
  [ "$#" -eq 1 ] || usage
  exec "${TERRAFORM_BIN}" fmt -check -recursive "${TERRAFORM_ROOT}"
fi

case "${environment}" in shared|dev|live) ;; *) usage ;; esac

while IFS= read -r environment_key; do
  case "${environment_key}" in
    TF_VAR_*|TF_CLI_ARGS|TF_CLI_ARGS_*|TF_WORKSPACE|TF_DATA_DIR)
      echo "Official Terraform commands refuse implicit input: ${environment_key}." >&2
      exit 1
      ;;
  esac
done < <(env | sed 's/=.*//' | sort)

environment_dir="${TERRAFORM_ROOT}/envs/${environment}"
variable_file="${environment_dir}/${environment}.tfvars"
backend_source="${environment_dir}/backend.tf.example"
backend_file="${environment_dir}/backend.tf"

[ -f "${variable_file}" ] || {
  echo "Missing committed variable source: ${variable_file}." >&2
  exit 1
}
[ -f "${backend_source}" ] || {
  echo "Missing committed backend source: ${backend_source}." >&2
  exit 1
}

for forbidden in \
  "${environment_dir}/terraform.tfvars" \
  "${environment_dir}/terraform.tfvars.json"; do
  if [ -e "${forbidden}" ] || [ -L "${forbidden}" ]; then
    echo "Official Terraform commands refuse auto-loaded local file: ${forbidden}." >&2
    exit 1
  fi
done

while IFS= read -r auto_file; do
  [ -z "${auto_file}" ] && continue
  echo "Official Terraform commands refuse auto-loaded local file: ${auto_file}." >&2
  exit 1
done < <(find "${environment_dir}" -maxdepth 1 \( -type f -o -type l \) \( -name '*.auto.tfvars' -o -name '*.auto.tfvars.json' \) -print)

if [ -e "${backend_file}" ] || [ -L "${backend_file}" ]; then
  if ! cmp -s "${backend_source}" "${backend_file}"; then
    echo "Generated backend.tf differs from the committed backend source: ${environment}." >&2
    exit 1
  fi
else
  cp "${backend_source}" "${backend_file}"
fi

case "${command_name}" in
  init)
    [ "$#" -eq 2 ] || usage
    "${TERRAFORM_BIN}" -chdir="${environment_dir}" init -input=false -no-color
    "${TERRAFORM_BIN}" -chdir="${environment_dir}" workspace select default >/dev/null
    [ "$("${TERRAFORM_BIN}" -chdir="${environment_dir}" workspace show)" = "default" ] || {
      echo "Official Terraform commands require the default workspace." >&2
      exit 1
    }
    ;;
  validate)
    [ "$#" -eq 2 ] || usage
    validate_data_dir="${environment_dir}/.terraform/validate"
    TF_DATA_DIR="${validate_data_dir}" \
      "${TERRAFORM_BIN}" -chdir="${environment_dir}" init -backend=false -input=false -no-color
    exec env TF_DATA_DIR="${validate_data_dir}" \
      "${TERRAFORM_BIN}" -chdir="${environment_dir}" validate -no-color
    ;;
  plan)
    [ "$#" -eq 3 ] || usage
    plan_output="$3"
    case "${plan_output}" in
      /*) ;;
      *) plan_output="${ROOT_DIR}/${plan_output}" ;;
    esac
    "${TERRAFORM_BIN}" -chdir="${environment_dir}" init -input=false -no-color
    "${TERRAFORM_BIN}" -chdir="${environment_dir}" workspace select default >/dev/null
    [ "$("${TERRAFORM_BIN}" -chdir="${environment_dir}" workspace show)" = "default" ] || {
      echo "Official Terraform commands require the default workspace." >&2
      exit 1
    }
    exec "${TERRAFORM_BIN}" -chdir="${environment_dir}" plan \
      -input=false \
      -lock-timeout=5m \
      -no-color \
      -var-file="${environment}.tfvars" \
      -out="${plan_output}"
    ;;
  state-list)
    [ "$#" -eq 2 ] || usage
    "${TERRAFORM_BIN}" -chdir="${environment_dir}" init -input=false -no-color >/dev/null
    "${TERRAFORM_BIN}" -chdir="${environment_dir}" workspace select default >/dev/null
    [ "$("${TERRAFORM_BIN}" -chdir="${environment_dir}" workspace show)" = "default" ] || {
      echo "Official Terraform commands require the default workspace." >&2
      exit 1
    }
    exec "${TERRAFORM_BIN}" -chdir="${environment_dir}" state list
    ;;
  output-raw|output-json)
    [ "$#" -eq 3 ] || usage
    output_name="$3"
    case "${output_name}" in
      ''|*[!A-Za-z0-9_]*)
        echo "Invalid Terraform output name: ${output_name}." >&2
        exit 1
        ;;
    esac
    "${TERRAFORM_BIN}" -chdir="${environment_dir}" init -input=false -no-color >/dev/null
    "${TERRAFORM_BIN}" -chdir="${environment_dir}" workspace select default >/dev/null
    [ "$("${TERRAFORM_BIN}" -chdir="${environment_dir}" workspace show)" = "default" ] || {
      echo "Official Terraform commands require the default workspace." >&2
      exit 1
    }
    if [ "${command_name}" = "output-raw" ]; then
      exec "${TERRAFORM_BIN}" -chdir="${environment_dir}" output -raw "${output_name}"
    fi
    exec "${TERRAFORM_BIN}" -chdir="${environment_dir}" output -json "${output_name}"
    ;;
  *)
    usage
    ;;
esac
