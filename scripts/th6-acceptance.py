#!/usr/bin/env python3

"""Run the TH6 fixed-address integration scenarios against one disposable Compose fixture."""

from __future__ import annotations

import argparse
import json
import os
import re
import subprocess
import time
import urllib.error
import urllib.parse
import urllib.request
import uuid
from dataclasses import dataclass
from datetime import datetime, timezone
from decimal import Decimal
from pathlib import Path


@dataclass
class HttpResult:
    status: int
    body: object
    headers: object
    elapsed_ms: int


class AcceptanceRun:
    def __init__(self, args: argparse.Namespace) -> None:
        self.env_file = Path(args.env_file)
        self.output_directory = Path(args.output_directory)
        self.project_name = args.project_name
        self.compose_file = args.compose_file
        self.environment = self._read_environment(self.env_file)
        self.run_id = self.environment["TH6_RUN_ID"]
        self.account = f'http://127.0.0.1:{self.environment["ACCOUNT_HOST_PORT"]}'
        self.history = f'http://127.0.0.1:{self.environment["HISTORY_HOST_PORT"]}'
        self.cases = []
        self.requests = []

    @staticmethod
    def _read_environment(path: Path) -> dict[str, str]:
        values = {}
        for line in path.read_text(encoding="utf-8").splitlines():
            if line and not line.startswith("#"):
                name, value = line.split("=", maxsplit=1)
                values[name] = value
        return values

    def _compose(
        self, *arguments: str, input_text: str | None = None, environment: dict[str, str] | None = None,
    ) -> str:
        command = [
            "docker", "compose", "--env-file", str(self.env_file), "--project-name", self.project_name,
            "-f", self.compose_file, *arguments,
        ]
        result = subprocess.run(command, input=input_text, capture_output=True, text=True, check=False, env=environment)
        if result.returncode != 0:
            message = "The isolated Compose operation failed. Inspect this run's retained container logs."
            raise RuntimeError(message)
        return result.stdout.strip()

    def _mysql(self, statement: str, database: str) -> str:
        environment = os.environ.copy()
        environment["MYSQL_PWD"] = self.environment["MYSQL_ROOT_PASSWORD"]
        return self._compose(
            "exec", "-T", "--env", "MYSQL_PWD", "mysql", "mysql",
            "--protocol=TCP", "--host=127.0.0.1", "--user=root", f"--database={database}",
            "--batch", "--skip-column-names",
            input_text=statement,
            environment=environment,
        )

    def _request(
        self,
        method: str,
        path: str,
        *,
        base: str | None = None,
        token: str | None = None,
        body: object | None = None,
    ) -> HttpResult:
        headers = {"Accept": "application/json"}
        if token:
            headers["Authorization"] = f"Bearer {token}"
        data = None
        if body is not None:
            headers["Content-Type"] = "application/json"
            data = json.dumps(body).encode("utf-8")
        request = urllib.request.Request((base or self.account) + path, data=data, method=method, headers=headers)
        started = time.monotonic()
        try:
            with urllib.request.urlopen(request, timeout=12) as response:
                payload = response.read()
                result = HttpResult(response.status, self._json(payload), response.headers, int((time.monotonic() - started) * 1000))
        except urllib.error.HTTPError as error:
            result = HttpResult(error.code, self._json(error.read()), error.headers, int((time.monotonic() - started) * 1000))
        except (urllib.error.URLError, TimeoutError, OSError):
            result = HttpResult(0, None, {}, int((time.monotonic() - started) * 1000))
        self.requests.append({
            "run_id": self.run_id,
            "method": method,
            "path": path.split("?", maxsplit=1)[0],
            "status": result.status,
            "elapsed_ms": result.elapsed_ms,
        })
        return result

    @staticmethod
    def _json(payload):
        try:
            return json.loads(payload)
        except ValueError:
            return None

    @staticmethod
    def _require(condition, message):
        if not condition:
            raise AssertionError(message)

    def _account_info(self, role: str) -> dict[str, object]:
        username = "th6_" + uuid.uuid4().hex[:16]
        email = f"{username}@example.invalid"
        password = uuid.uuid4().hex + "B7!"
        otp_request = self._request("POST", "/api/accounts/otp/generate?" + urllib.parse.urlencode({"email": email}))
        self._require(otp_request.status == 200, "OTP request failed.")
        otp = self._compose("exec", "-T", "redis", "redis-cli", "GET", f"otp:{email}")
        self._require(bool(re.fullmatch(r"\d{6}", otp)), "The synthetic OTP was not created in the fixture Redis instance.")
        registration = self._request("POST", "/api/accounts/users/register-with-otp", body={
            "fullName": f"TH6 {role.title()}", "username": username, "password": password,
            "email": email, "otp": otp,
        })
        self._require(registration.status == 200 and isinstance(registration.body, dict), "Synthetic user registration failed.")
        user_id = registration.body["id"]
        rows = self._mysql(
            "SELECT id, account_number FROM accounts WHERE user_id=" + str(int(user_id)) + " ORDER BY id LIMIT 1;",
            "bank_account",
        )
        account_id, account_number = rows.split("\t", maxsplit=1)
        if role == "alice":
            self._mysql(f"UPDATE accounts SET balance=50000000.00 WHERE id={int(account_id)};", "bank_account")
        return {"id": int(account_id), "number": account_number, "username": username, "password": password}


    def _snapshot(self):
        return {
            "transfer_rows": int(self._mysql("SELECT COUNT(*) FROM transactions;", "bank_transfer")),
            "kafka_offsets": self._compose(
                "exec", "-T", "--env", "KAFKA_HEAP_OPTS=-Xmx96m",
                "--env", "KAFKA_JVM_PERFORMANCE_OPTS=-XX:+UseSerialGC",
                "kafka", "/opt/kafka/bin/kafka-get-offsets.sh",
                "--bootstrap-server", "kafka:9092", "--topic", "banking.transaction", "--time", "-1",
            ).splitlines(),
        }

    def _balance(self, account):
        return Decimal(self._mysql(
            f"SELECT balance FROM accounts WHERE id={account['id']};", "bank_account"
        ))

    def _newman(self, folder, values):
        private_dir = self.env_file.parent
        environment = private_dir / "postman-env.json"
        raw = private_dir / "newman-private.json"
        environment.write_text(json.dumps({"name": "TH6 isolated", "values": [
            {"key": key, "value": str(value), "enabled": True} for key, value in values.items()
        ]}), encoding="utf-8")
        raw.unlink(missing_ok=True)
        integration = Path(__file__).resolve().parents[1] / "integration/postman"
        try:
            result = subprocess.run([
                "docker", "run", "--rm", "--network", "host",
                "-v", f"{integration}:/etc/newman:ro", "-v", f"{private_dir}:/th6",
                "postman/newman@sha256:d9b5e780ead0026bbb37f3759cd7b10fc8a88cd4030c8266a7c5e343d75a5198", "run", "integration-tests.json",
                "-e", "/th6/postman-env.json", "--folder", folder, "--delay-request", "500",
                "--timeout", "175000", "--timeout-request", "30000", "--reporters", "json",
                "--reporter-json-export", "/th6/newman-private.json", "--export-environment", "/th6/postman-env.json",
            ], capture_output=True, check=False)
            report = json.loads(raw.read_text(encoding="utf-8"))["run"] if raw.exists() else {}
            checks = [
                {"request": entry["item"]["name"], "http": (entry.get("response") or {}).get("code"),
                 "assertions": [{"name": a["assertion"], "pass": not a.get("error")} for a in entry.get("assertions", [])]}
                for entry in report.get("executions", [])
            ]
            self.cases.append({"folder": folder, "newman_exit": result.returncode, "requests": checks})
            if result.returncode == 0:
                values.update({v["key"]: v["value"] for v in json.loads(environment.read_text())["values"]})
            self._require(result.returncode == 0 and checks, f"Newman failed in {folder}. See sanitized request and assertion results.")
        finally:
            raw.unlink(missing_ok=True)
            environment.unlink(missing_ok=True)
        return self.cases[-1]

    def _save_evidence(self, result, error=None):
        evidence = {"run_id": self.run_id, "utc": datetime.now(timezone.utc).isoformat(),
                    "result": result, "error": error, "cases": self.cases, "fixture_setup_http": self.requests}
        (self.output_directory / "e2e-summary.json").write_text(
            json.dumps(evidence, ensure_ascii=False, indent=2) + "\n", encoding="utf-8"
        )

    def run(self):
        result = "FAIL"
        error = None
        try:
            alice, bob = self._account_info("alice"), self._account_info("bob")
            values = {
                "accountUrl": self.account,
                "transferUrl": f'http://127.0.0.1:{self.environment["TRANSFER_HOST_PORT"]}',
                "historyUrl": self.history,
                "username": alice["username"], "password": alice["password"],
                "sender": alice["number"], "recipient": bob["number"], "missing": str(uuid.uuid4()),
            }
            before = [self._balance(alice), self._balance(bob)]
            count = int(self._mysql("SELECT COUNT(*) FROM transactions;", "bank_transfer"))
            success = self._newman("01 Success", values)
            after = [self._balance(alice), self._balance(bob)]
            snapshot = self._snapshot()
            self._require(after == [before[0] - 1000, before[1] + 1000], "Unexpected account balances.")
            self._require(snapshot["transfer_rows"] == count + 1 and snapshot["kafka_offsets"], "Expected one transfer and readable Kafka offsets.")
            success.update(result="PASS", transaction_id=values["transactionId"],
                           balances_before=list(map(str, before)), balances_after=list(map(str, after)))
            self._save_evidence("IN_PROGRESS")
            missing = self._newman("02 Missing account", values)
            self._require(self._snapshot() == snapshot, "404 changed transfer storage or Kafka offsets.")
            missing.update(result="PASS", side_effects=snapshot)
            self._save_evidence("IN_PROGRESS")
            self._compose("stop", "account-service")
            down = self._newman("03 Provider unavailable", values)
            self._require(self._snapshot() == snapshot, "503 changed transfer storage or Kafka offsets.")
            down.update(result="PASS", side_effects=snapshot)
            result = "PASS"
        except (RuntimeError, AssertionError) as exc:
            error = str(exc)
        finally:
            self._save_evidence(result, error)
        print(f"TH6 E2E {result}" + (f": {error}" if error else ""))
        return 0 if result == "PASS" else 1


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    for name in ("env-file", "output-directory", "project-name", "compose-file"):
        parser.add_argument(f"--{name}", required=True)
    raise SystemExit(AcceptanceRun(parser.parse_args()).run())
