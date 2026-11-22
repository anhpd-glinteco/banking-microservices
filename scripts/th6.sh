#!/usr/bin/env bash
set -Eeuo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
compose_file="$repo_root/compose.th6-test.yml"
work_root="${TH6_WORK_ROOT:-/mnt/d/Codex/work/bank-th6-runs}"
output_root="${TH6_OUTPUT_ROOT:-/mnt/d/Codex/outputs/bank/th6}"
action="${1:-run}"
requested_id="${2:-}"

if [[ ! -d /mnt/d ]]; then
    work_root="${TH6_WORK_ROOT:-${XDG_STATE_HOME:-$HOME/.local/state}/bank-th6-runs}"
    output_root="${TH6_OUTPUT_ROOT:-$repo_root/output/th6}"
fi

if [[ "$action" != run && "$action" != start && "$action" != test && "$action" != status && "$action" != stop ]]; then
    printf 'Usage: %s [run|start|test|status|stop] [run-id]\n' "$0" >&2
    exit 2
fi

new_run() {
    local run_id="$requested_id"
    if [[ -z "$run_id" ]]; then
        run_id="th6-$(date -u +%Y%m%dT%H%M%SZ | tr '[:upper:]' '[:lower:]')-$(openssl rand -hex 4)"
    fi
    if [[ ! "$run_id" =~ ^[a-z][a-z0-9-]{2,50}$ ]]; then
        printf 'Invalid fixture run ID.\n' >&2
        exit 2
    fi

    local run_dir="$work_root/$run_id"
    local output_dir="$output_root/$run_id"
    if [[ -e "$run_dir" || -e "$output_dir" ]]; then
        printf 'Fixture output already exists. Choose a new run ID.\n' >&2
        exit 1
    fi

    mkdir -p "$run_dir/keys" "$run_dir/maven-repository" "$output_dir/build"
    chmod 700 "$run_dir" "$run_dir/keys" "$output_dir" "$output_dir/build"
    openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 \
        -out "$run_dir/keys/bank-private.pem" 2>/dev/null
    openssl pkey -in "$run_dir/keys/bank-private.pem" -pubout \
        -out "$run_dir/keys/bank-public.pem" 2>/dev/null
    chmod 600 "$run_dir/keys/bank-private.pem" "$run_dir/keys/bank-public.pem"

    cat > "$run_dir/fixture.env" <<EOF
TH6_RUN_ID=$run_id
TH6_KEY_DIR=$run_dir/keys
MYSQL_ROOT_PASSWORD=$(openssl rand -hex 24)
ACCOUNT_DB_PASSWORD=$(openssl rand -hex 24)
TRANSFER_DB_PASSWORD=$(openssl rand -hex 24)
HISTORY_DB_PASSWORD=$(openssl rand -hex 24)
MYSQL_HOST_PORT=${TH6_MYSQL_PORT:-33306}
KAFKA_HOST_PORT=${TH6_KAFKA_PORT:-59092}
REDIS_HOST_PORT=${TH6_REDIS_PORT:-36379}
ACCOUNT_HOST_PORT=${TH6_ACCOUNT_PORT:-28081}
TRANSFER_HOST_PORT=${TH6_TRANSFER_PORT:-28082}
HISTORY_HOST_PORT=${TH6_HISTORY_PORT:-28084}
EOF
    chmod 600 "$run_dir/fixture.env"
    TH6_RUN_ID="$run_id"
    requested_id="$run_id"
    printf 'TH6_RUN_ID=%s\nTH6_RUN_DIR=%s\nTH6_OUTPUT_DIR=%s\n' "$run_id" "$run_dir" "$output_dir" \
        > "$output_dir/run-info.txt"
    printf 'Created isolated TH6 run %s. Private fixture credentials and JWT keys are in the work directory.\n' "$run_id"
}

load_run() {
    local run_id="$requested_id"
    if [[ -z "$run_id" ]]; then
        printf 'Supply the TH6_RUN_ID printed by the previous command.\n' >&2
        exit 2
    fi
    if [[ ! "$run_id" =~ ^[a-z][a-z0-9-]{2,50}$ ]]; then
        printf 'Invalid fixture run ID.\n' >&2
        exit 2
    fi
    local run_dir="$work_root/$run_id"
    if [[ ! -f "$run_dir/fixture.env" || ! -d "$run_dir/keys" ]]; then
        printf 'Private TH6 fixture credentials are missing for %s.\n' "$run_id" >&2
        exit 1
    fi
    TH6_RUN_ID="$run_id"
    TH6_RUN_DIR="$run_dir"
    TH6_OUTPUT_DIR="$output_root/$run_id"
    export TH6_RUN_ID TH6_RUN_DIR TH6_OUTPUT_DIR
    export MAVEN_REPO_LOCAL="${MAVEN_REPO_LOCAL:-$run_dir/maven-repository}"
}

compose() {
    docker compose --parallel 1 --env-file "$TH6_RUN_DIR/fixture.env" --project-name "bank-$TH6_RUN_ID" -f "$compose_file" "$@"
}

verify_source() {
    local module
    local -a modules=(bank-common account-service transfer-service transaction-history-service)
    for module in "${modules[@]}"; do
        local log="$TH6_OUTPUT_DIR/build/$module-verify.log"
        local -a command=(mvn -B -ntp "-Dmaven.repo.local=$MAVEN_REPO_LOCAL" -f "$module/pom.xml")
        if [[ "$module" == bank-common ]]; then
            command+=(install)
        else
            command+=(verify)
        fi
        if (cd "$repo_root" && "${command[@]}" > "$log" 2>&1); then
            printf 'PASS %s\n' "$module"
        else
            tail -n 35 "$log" >&2
            printf 'FAIL %s. Inspect the retained build log.\n' "$module" >&2
            return 1
        fi
    done
}

provision_databases() {
    local sql="$TH6_RUN_DIR/provision.sql"
    cat > "$sql" <<EOF
CREATE DATABASE bank_account CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE bank_transfer CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE bank_history CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE USER 'account_app'@'%' IDENTIFIED BY '$(grep '^ACCOUNT_DB_PASSWORD=' "$TH6_RUN_DIR/fixture.env" | cut -d= -f2-)';
CREATE USER 'transfer_app'@'%' IDENTIFIED BY '$(grep '^TRANSFER_DB_PASSWORD=' "$TH6_RUN_DIR/fixture.env" | cut -d= -f2-)';
CREATE USER 'history_app'@'%' IDENTIFIED BY '$(grep '^HISTORY_DB_PASSWORD=' "$TH6_RUN_DIR/fixture.env" | cut -d= -f2-)';
GRANT SELECT, INSERT, UPDATE, DELETE, CREATE, ALTER, INDEX, REFERENCES ON bank_account.* TO 'account_app'@'%';
GRANT SELECT, INSERT, UPDATE, DELETE, CREATE, ALTER, INDEX, REFERENCES ON bank_transfer.* TO 'transfer_app'@'%';
GRANT SELECT, INSERT, UPDATE, DELETE, CREATE, ALTER, INDEX, REFERENCES ON bank_history.* TO 'history_app'@'%';
EOF
    if ! compose exec -T mysql sh -c 'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysql --protocol=TCP --host=127.0.0.1 --user=root --batch' < "$sql" \
            > "$TH6_RUN_DIR/database-provision.log" 2>&1; then
        printf 'Database fixture provisioning failed. Inspect the sanitized command result, then preserve this run.\n' >&2
        return 1
    fi
}

start_services() {
    compose config --quiet
    verify_source
    compose up -d --wait --wait-timeout 240 mysql kafka redis
    provision_databases
    local service
    for service in account-service transfer-service transaction-history-service; do
        compose build "$service"
        compose up -d --wait --wait-timeout 240 "$service"
    done
    printf 'TH6 services are healthy.\n'
    cat "$TH6_OUTPUT_DIR/run-info.txt"
}

case "$action" in
    run|start)
        new_run
        load_run "$TH6_RUN_ID"
        start_services
        if [[ "$action" == run ]]; then
            python3 "$repo_root/scripts/th6-acceptance.py" \
                --compose-file "$compose_file" --env-file "$TH6_RUN_DIR/fixture.env" \
                --project-name "bank-$TH6_RUN_ID" --output-directory "$TH6_OUTPUT_DIR"
        fi
        printf 'Fixture remains isolated and running. Stop it without deleting data with: %s stop %s\n' "$0" "$TH6_RUN_ID"
        ;;
    test)
        load_run "$requested_id"
        compose config --quiet
        compose up -d --wait --wait-timeout 240 account-service
        python3 "$repo_root/scripts/th6-acceptance.py" \
            --compose-file "$compose_file" --env-file "$TH6_RUN_DIR/fixture.env" \
            --project-name "bank-$TH6_RUN_ID" --output-directory "$TH6_OUTPUT_DIR"
        ;;
    status)
        load_run "$requested_id"
        compose ps
        ;;
    stop)
        load_run "$requested_id"
        compose down
        printf 'Stopped %s. Database, Kafka and Redis volumes remain in place.\n' "$TH6_RUN_ID"
        ;;
esac
