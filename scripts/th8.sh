#!/usr/bin/env bash
set -euo pipefail
repo=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)
action=${1:-}
id=${2:-}
[[ $id =~ ^[a-z0-9][a-z0-9-]{0,39}$ ]] || { echo "Usage: bash scripts/th8.sh {init|up|status|down|test|verify} <run-id>" >&2; exit 2; }
state=${TH8_STATE_ROOT:-${XDG_STATE_HOME:-$HOME/.local/state}/bank-th8}
run=$(realpath -m -- "$state/$id")
project="bank-th8-$id"

if [[ $action == init ]]; then
    umask 077
    mkdir -p -- "$state"
    mkdir -- "$run" || { echo 'Run already exists. Use its existing configuration or choose a new ID.' >&2; exit 2; }
    openssl genpkey -quiet -algorithm RSA -pkeyopt rsa_keygen_bits:2048 -out "$run/private.pem"
    openssl pkey -in "$run/private.pem" -pubout -out "$run/public.pem"
    # The run directory is private on the host. The application UID needs to read its mounted file.
    chmod 444 "$run/"{private,public}.pem
    {
        printf 'TH8_RUN_ID=%s\n' "$id"
        printf "TH8_KEY_DIR='%s'\n" "$run"
        printf 'FRONTEND_HOST_PORT=25173\nGATEWAY_HOST_PORT=28080\nEUREKA_HOST_PORT=28761\nMAILPIT_HOST_PORT=28025\n'
        for name in MYSQL_ROOT_PASSWORD ACCOUNT_DB_PASSWORD TRANSFER_DB_PASSWORD HISTORY_DB_PASSWORD; do
            printf '%s=%s\n' "$name" "$(openssl rand -hex 24)"
        done
    } > "$run/.env"
    printf 'Run %s initialized. Config path: %s/.env\n' "$project" "$run"
    exit 0
fi

[[ -s "$run/.env" ]] || { echo 'Initialize the run first.' >&2; exit 2; }
cd "$repo"
# Compose gives inherited variables precedence over the selected env file.
unset TH8_RUN_ID TH8_KEY_DIR COMPOSE_PROJECT_NAME FRONTEND_HOST_PORT GATEWAY_HOST_PORT EUREKA_HOST_PORT MAILPIT_HOST_PORT
unset MYSQL_ROOT_PASSWORD ACCOUNT_DB_PASSWORD TRANSFER_DB_PASSWORD HISTORY_DB_PASSWORD
compose=(docker compose -p "$project" --env-file "$run/.env" -f "$repo/compose.th8.yml")
case "$action" in
    up) "${compose[@]}" up -d --build --wait --wait-timeout 300 ;;
    status) "${compose[@]}" ps -a ;;
    down) "${compose[@]}" down ;;
    verify)
        output=${TH8_OUTPUT_ROOT:-$repo/output/th8}
        logs="$output/$id/maven-$(date -u +%Y%m%dT%H%M%SZ)"
        mkdir -p "$logs"
        for module in bank-common account-service transfer-service transaction-history-service notification-service api-gateway discovery-service; do
            goal=verify
            if [[ $module == bank-common ]]; then goal=install; fi
            if mvn -B -ntp "-Dmaven.repo.local=$run/maven-repository" -f "$module/pom.xml" "$goal" >"$logs/$module.log" 2>&1; then
                printf 'PASS %s\n' "$module"
            else
                printf 'FAIL %s — %s/%s.log\n' "$module" "$logs" "$module" >&2
                exit 1
            fi
        done
        ;;
    test)
        output=${TH8_OUTPUT_ROOT:-$repo/output/th8}
        attempt="$output/$id/$(date -u +%Y%m%dT%H%M%SZ)"
        mkdir -p "$output/$id"
        mkdir "$attempt"
        python3 "$repo/scripts/th8-acceptance.py" --env-file "$run/.env" --project-name "$project" \
            --compose-file "$repo/compose.th8.yml" --output-directory "$attempt"
        ;;
    *) echo 'Unknown action.' >&2; exit 2 ;;
esac
