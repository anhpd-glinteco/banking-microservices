#!/usr/bin/env python3
"""Run the TH7 service-discovery scenarios against one disposable Compose fixture."""

from __future__ import annotations

import argparse
import csv
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
from pathlib import Path


@dataclass
class HttpResult:
    status: int
    body: object
    headers: object
    elapsed_ms: int


class AcceptanceRun:
    def __init__(self, args: argparse.Namespace, *, run_id_key="TH7_RUN_ID", history_port_key="HISTORY_HOST_PORT") -> None:
        self.env_file = Path(args.env_file)
        self.output_directory = Path(args.output_directory)
        self.project_name = args.project_name
        self.compose_file = args.compose_file
        self.environment = self._read_environment(self.env_file)
        self.run_id = self.environment[run_id_key]
        self.gateway = f'http://127.0.0.1:{self.environment["GATEWAY_HOST_PORT"]}'
        self.history = f'http://127.0.0.1:{self.environment[history_port_key]}'
        self.history_timeout = 180
        self.cases: list[dict[str, object]] = []
        self.requests: list[dict[str, object]] = []
        self.tokens: dict[str, str] = {}
        self.accounts: dict[str, dict[str, object]] = {}

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
        safe_error: bool = False,
    ) -> str:
        command = [
            "docker", "compose", "--env-file", str(self.env_file), "--project-name", self.project_name,
            "-f", self.compose_file, *arguments,
        ]
        result = subprocess.run(command, input=input_text, capture_output=True, text=True, check=False, env=environment)
        if result.returncode != 0:
            message = "The isolated Compose operation failed. Inspect this run's retained container logs."
            if safe_error:
                mysql_error = next((line for line in result.stderr.splitlines() if line.startswith("ERROR ")), "")
                if mysql_error:
                    message = f"Fixture MySQL command failed: {mysql_error[:200]}"
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
            safe_error=True,
        )

    def _request(
        self,
        method: str,
        path: str,
        *,
        base: str | None = None,
        token: str | None = None,
        body: object | None = None,
        request_id: str | None = None,
    ) -> HttpResult:
        headers = {"Accept": "application/json"}
        if token:
            headers["Authorization"] = f"Bearer {token}"
        if request_id:
            headers["X-TH7-Request-Id"] = request_id
        data = None
        if body is not None:
            headers["Content-Type"] = "application/json"
            data = json.dumps(body).encode("utf-8")
        request = urllib.request.Request((base or self.gateway) + path, data=data, method=method, headers=headers)
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
            "request_id": request_id or "",
            "status": result.status,
            "elapsed_ms": result.elapsed_ms,
        })
        return result

    @staticmethod
    def _json(payload: bytes) -> object:
        if not payload:
            return None
        try:
            return json.loads(payload)
        except (UnicodeDecodeError, json.JSONDecodeError):
            return None

    def _require(self, condition: bool, message: str) -> None:
        if not condition:
            raise AssertionError(message)

    def _case(self, test_id: str, title: str, action) -> None:
        started = datetime.now(timezone.utc)
        try:
            details = action()
            self.cases.append({"id": test_id, "title": title, "result": "PASS", "details": details or {}})
            print(f"PASS {test_id} {title}")
        except Exception as error:
            message = str(error).splitlines()[0][:240]
            self.cases.append({"id": test_id, "title": title, "result": "FAIL", "details": {"reason": message}})
            print(f"FAIL {test_id} {title}: {message}")
            raise
        finally:
            self.cases[-1].setdefault("started_at", started.isoformat())
            self._save_evidence()

    @staticmethod
    def _instances(apps: object) -> dict[str, list[str]]:
        applications = apps["applications"]["application"]
        if isinstance(applications, dict):
            applications = [applications]
        found = {}
        for application in applications:
            instances = application.get("instance", [])
            if isinstance(instances, dict):
                instances = [instances]
            found[application["name"].lower()] = [
                instance.get("instanceId", "") for instance in instances
                if instance.get("status") == "UP"
            ]
        return found

    def _registry(self) -> dict[str, list[str]]:
        result = self._request("GET", "/eureka/apps", base=f'http://127.0.0.1:{self.environment["EUREKA_HOST_PORT"]}')
        self._require(result.status == 200 and isinstance(result.body, dict), "Eureka applications endpoint did not return JSON.")
        return self._instances(result.body)

    def _wait_registered(self, expected_account_count: int, timeout: int | None = None) -> dict[str, list[str]]:
        deadline = time.monotonic() + (timeout or self.history_timeout)
        last = {}
        required = {"transfer-service", "transaction-history-service", "notification-service", "api-gateway"}
        while time.monotonic() < deadline:
            try:
                last = self._registry()
                if required.issubset(last) and len(last.get("account-service", [])) == expected_account_count:
                    return last
            except (AssertionError, KeyError, TypeError):
                pass
            time.sleep(2)
        raise AssertionError(f"Eureka did not reach the expected registration count of {expected_account_count}.")

    def _account_info(self, role: str) -> dict[str, object]:
        username = "th7_" + uuid.uuid4().hex[:16]
        email = f"{username}@example.invalid"
        password = uuid.uuid4().hex + "B7!"
        otp_request = self._request("POST", "/api/accounts/otp/generate?" + urllib.parse.urlencode({"email": email}))
        self._require(otp_request.status == 200, "OTP request through the API Gateway failed.")
        otp = self._otp_for(email)
        registration = self._request("POST", "/api/accounts/users/register-with-otp", body={
            "fullName": f"TH7 {role.title()}", "username": username, "password": password,
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
        login = self._request("POST", "/api/accounts/login", body={"username": username, "password": password})
        self._require(login.status == 200 and login.body.get("accessToken"), "Fixture user login did not return a JWT.")
        return {"id": int(account_id), "number": account_number, "token": login.body["accessToken"]}

    def _otp_for(self, email: str) -> str:
        otp = self._compose("exec", "-T", "redis", "redis-cli", "GET", f"otp:{email}")
        self._require(bool(re.fullmatch(r"\d{6}", otp)), "The synthetic OTP was not created in the fixture Redis instance.")
        return otp

    def _history(self, account: dict[str, object], *, token: str | None = None, trace: str | None = None) -> HttpResult:
        return self._request(
            "GET", "/api/history/number/" + urllib.parse.quote(str(account["number"]), safe=""),
            base=self.history, token=token or str(account["token"]), request_id=trace,
        )

    def _account_trace_log(self, request_ids: list[str]) -> dict[str, tuple[str, int]]:
        log = self._compose(
            "logs", "--no-color", "--tail", "2000", "account-service-a", "account-service-b"
        )
        wanted = set(request_ids)
        found = {}
        pattern = re.compile(r"TH7 account request instance=([^\s]+) request=([0-9a-f-]+) status=(\d+)")
        for match in pattern.finditer(log):
            instance_id, request_id, status = match.groups()
            if request_id in wanted:
                found[request_id] = (instance_id, int(status))
        return found

    def _record_traced_read(self, account: dict[str, object]) -> tuple[HttpResult, str]:
        request_id = str(uuid.uuid4())
        result = self._history(account, trace=request_id)
        return result, request_id

    def _wait_for_instance(self, account: dict[str, object], expected: str, count: int, timeout: int = 180) -> list[str]:
        deadline = time.monotonic() + timeout
        request_ids: list[str] = []
        consecutive = 0
        while time.monotonic() < deadline:
            result, request_id = self._record_traced_read(account)
            request_ids.append(request_id)
            observed = self._account_trace_log([request_id]).get(request_id)
            if result.status == 200 and observed == (expected, 200):
                consecutive += 1
            else:
                consecutive = 0
            if consecutive >= count:
                return request_ids
            time.sleep(2)
        raise AssertionError(f"Service discovery did not settle on {expected} within {timeout} seconds.")

    def _tc01(self) -> dict[str, object]:
        applications = self._wait_registered(2)
        account_ids = sorted(applications["account-service"])
        self._require(len(set(account_ids)) == 2, "Account instances did not register with distinct IDs.")
        return {"registered_services": sorted(applications), "account_instance_ids": account_ids}

    def _tc02(self) -> dict[str, object]:
        self.accounts["alice"] = self._account_info("alice")
        self.accounts["bob"] = self._account_info("bob")
        alice = self.accounts["alice"]
        bob = self.accounts["bob"]

        self._require(self._history(alice).status == 200, "Owner could not read history through History Service discovery.")
        self._require(self._history(alice, token=str(bob["token"])).status == 403,
                      "History lookup did not preserve owner authorization.")
        legacy = self._request(
            "GET", f'/api/history/{alice["id"]}', base=self.history, token=str(alice["token"]),
        )
        self._require(legacy.status == 200 and isinstance(legacy.body, list), "Legacy numeric account lookup changed.")

        initial = int(self._mysql("SELECT COUNT(*) FROM transactions;", "bank_transfer"))
        transfer = self._request("POST", "/api/transfers", token=str(alice["token"]), body={
            "fromAccountNumber": alice["number"], "toAccountNumber": bob["number"],
            "amount": 1000, "description": "TH7 synthetic discovery check",
        })
        self._require(transfer.status == 200 and transfer.body.get("status") == "PROCESSING",
                      "Transfer Service did not accept the synthetic transfer.")
        transaction_id = transfer.body["id"]
        deadline = time.monotonic() + 60
        status = None
        while time.monotonic() < deadline:
            response = self._request("GET", f"/api/transfers/{transaction_id}/status", token=str(alice["token"]))
            status = response.body.get("status") if isinstance(response.body, dict) else None
            if response.status == 200 and status == "SUCCESS":
                break
            if response.status != 200 or status in {"FAILED", "COMPENSATED"}:
                raise AssertionError("The synthetic Kafka transfer did not complete successfully.")
            time.sleep(1)
        self._require(status == "SUCCESS", "The synthetic transfer did not reach SUCCESS within 60 seconds.")

        deadline = time.monotonic() + 60
        owner_history = []
        while time.monotonic() < deadline:
            response = self._history(alice)
            owner_history = response.body if isinstance(response.body, list) else []
            if response.status == 200 and any(row.get("transactionId") == transaction_id for row in owner_history):
                break
            time.sleep(1)
        self._require(any(row.get("transactionId") == transaction_id for row in owner_history),
                      "Kafka history did not contain the completed synthetic transfer.")
        self._require(int(self._mysql("SELECT COUNT(*) FROM transactions;", "bank_transfer")) == initial + 1,
                      "The fixture did not persist exactly one transfer.")
        return {"transfer_status": status, "history_rows_for_owner": len(owner_history), "authorization": "401/403 policy retained"}

    def _tc03(self) -> dict[str, object]:
        results = []
        request_ids = []
        for _ in range(20):
            result, request_id = self._record_traced_read(self.accounts["alice"])
            request_ids.append(request_id)
            self._require(result.status == 200 and isinstance(result.body, list), "A History discovery request failed.")
            results.append(result)
        traced = self._account_trace_log(request_ids)
        instances = {traced[request_id][0] for request_id in request_ids if request_id in traced}
        self._require(len(traced) == 20, "Account logs did not confirm all 20 trace IDs.")
        self._require(all(status == 200 for _, status in traced.values()), "An Account lookup returned a non-200 status.")
        self._require(len(instances) == 2, "Both Account instances did not handle traced requests.")
        return {"requests": 20, "unique_instances": sorted(instances), "status_codes": [200] * len(results)}

    def _tc04(self) -> dict[str, object]:
        self._compose("kill", "--signal", "SIGKILL", "account-service-a")
        applications = self._wait_registered(1)
        self._require(len(applications["account-service"]) == 1, "The stopped instance was still registered after Eureka convergence.")
        request_ids = self._wait_for_instance(self.accounts["alice"], "account-service-b", 5)
        observed = self._account_trace_log(request_ids)
        served_by_b = [request_id for request_id in request_ids if observed.get(request_id) == ("account-service-b", 200)]
        self._require(len(served_by_b) >= 5, "The surviving Account instance did not handle five consecutive calls.")
        account = self.accounts["alice"]
        transfer = None
        deadline = time.monotonic() + 60
        while time.monotonic() < deadline:
            transfer = self._request("POST", "/api/transfers", token=str(account["token"]), body={
                "fromAccountNumber": account["number"], "toAccountNumber": self.accounts["bob"]["number"],
                "amount": 500, "description": "TH7 surviving-provider fixture check",
            })
            if transfer.status == 200 and isinstance(transfer.body, dict):
                break
            if transfer.status not in {0, 503}:
                raise AssertionError("Transfer failed unexpectedly while one Account provider was available.")
            time.sleep(2)
        self._require(transfer is not None and transfer.status == 200,
                      "Transfer could not discover the surviving Account instance.")
        transaction_id = transfer.body["id"]
        deadline = time.monotonic() + 60
        status = None
        while time.monotonic() < deadline:
            response = self._request("GET", f"/api/transfers/{transaction_id}/status", token=str(account["token"]))
            status = response.body.get("status") if isinstance(response.body, dict) else None
            if response.status == 200 and status == "SUCCESS":
                break
            if response.status != 200 or status in {"FAILED", "COMPENSATED"}:
                raise AssertionError("The failover transfer did not complete successfully.")
            time.sleep(1)
        self._require(status == "SUCCESS", "The failover transfer did not reach SUCCESS within 60 seconds.")
        return {"surviving_instance": "account-service-b", "consecutive_history_reads": 5,
                "transfer_status": status}

    def _tc05(self) -> dict[str, object]:
        self._compose("kill", "--signal", "SIGKILL", "account-service-b")
        deadline = time.monotonic() + self.history_timeout
        while time.monotonic() < deadline:
            try:
                if not self._registry().get("account-service"):
                    break
            except (AssertionError, KeyError, TypeError):
                pass
            time.sleep(2)
        else:
            raise AssertionError("Eureka did not remove the unavailable Account instances.")

        account = self.accounts["alice"]
        transaction_count = int(self._mysql("SELECT COUNT(*) FROM transactions;", "bank_transfer"))
        kafka_offset = self._kafka_offset()
        deadline = time.monotonic() + self.history_timeout
        history = HttpResult(0, None, {}, 0)
        while time.monotonic() < deadline:
            history = self._history(account)
            if history.status == 503:
                break
            time.sleep(2)
        self._require(history.status == 503, "History did not return HTTP 503 without an Account provider.")

        transfer = self._request("POST", "/api/transfers", token=str(account["token"]), body={
            "fromAccountNumber": account["number"], "toAccountNumber": self.accounts["bob"]["number"],
            "amount": 1001, "description": "TH7 provider-unavailable fixture check",
        })
        message = transfer.body.get("message", "") if isinstance(transfer.body, dict) else ""
        self._require(transfer.status == 503,
                      f"Transfer returned HTTP {transfer.status} without an Account provider: {message}")
        self._require(int(self._mysql("SELECT COUNT(*) FROM transactions;", "bank_transfer")) == transaction_count,
                      "A failed provider preflight changed the transaction row count.")
        self._require(self._kafka_offset() == kafka_offset,
                      "A failed provider preflight published a Kafka event.")
        return {"history_status": history.status, "transfer_status": transfer.status,
                "transaction_rows_unchanged": True, "kafka_offset_unchanged": True}

    def _kafka_offset(self) -> str:
        result = self._compose(
            "exec", "-T", "kafka", "/opt/kafka/bin/kafka-get-offsets.sh",
            "--bootstrap-server", "kafka:9092", "--topic", "banking.transaction", "--time", "-1",
        )
        self._require(bool(result), "The Kafka transaction topic has no readable offset.")
        return result

    def _tc06(self) -> dict[str, object]:
        self._compose("start", "account-service-a", "account-service-b")
        self._compose("up", "-d", "--wait", "--wait-timeout", "180", "account-service-a", "account-service-b")
        registrations = self._wait_registered(2)
        self._require(len(registrations["account-service"]) == 2, "Restarted Account instances did not re-register together.")
        request_ids: list[str] = []
        seen: set[str] = set()
        deadline = time.monotonic() + 60
        while time.monotonic() < deadline and len(seen) < 2:
            response, request_id = self._record_traced_read(self.accounts["alice"])
            request_ids.append(request_id)
            instance = self._account_trace_log([request_id]).get(request_id)
            if response.status == 200 and instance and instance[1] == 200:
                seen.add(instance[0])
            else:
                time.sleep(2)
        self._require(len(seen) == 2, "Both re-registered instances did not resume serving requests.")
        return {"registered_instance_ids": sorted(registrations["account-service"]),
                "serving_instances_after_restart": sorted(seen)}

    def _tc07(self) -> dict[str, object]:
        self._compose("stop", "discovery-service")
        observed, request_id = self._record_traced_read(self.accounts["alice"])
        self._require(observed.status == 200 and isinstance(observed.body, list),
                      "History could not use its cached Account registration while Eureka was unavailable.")
        self._compose("start", "discovery-service")
        self._compose("up", "-d", "--wait", "--wait-timeout", "180", "discovery-service")
        registrations = self._wait_registered(2)
        recovered, recovery_request_id = self._record_traced_read(self.accounts["alice"])
        self._require(recovered.status == 200, "Service discovery did not recover after Eureka restarted.")
        return {"history_status_while_registry_stopped": observed.status,
                "request_id": request_id, "history_status_after_registry_recovery": recovered.status,
                "recovery_request_id": recovery_request_id,
                "account_instances_after_recovery": registrations["account-service"]}

    def _save_evidence(self) -> None:
        self.output_directory.mkdir(parents=True, exist_ok=True)
        request_path = self.output_directory / "request-results.csv"
        if self.requests:
            with request_path.open("w", encoding="utf-8", newline="") as output:
                writer = csv.DictWriter(output, fieldnames=self.requests[0].keys())
                writer.writeheader()
                writer.writerows(self.requests)
        summary = {
            "run_id": self.run_id,
            "started_at": datetime.now(timezone.utc).isoformat(),
            "result": "FAIL" if any(case["result"] == "FAIL" for case in self.cases) else "IN_PROGRESS",
            "cases": self.cases,
            "request_count": len(self.requests),
        }
        (self.output_directory / "e2e-summary.json").write_text(
            json.dumps(summary, indent=2) + "\n", encoding="utf-8"
        )

    def run(self) -> int:
        self._save_evidence()
        cases = [
            ("TC01", "Eureka registers each service and both Account instances", self._tc01),
            ("TC02", "Transfer and History call Account through discovery with JWT authorization", self._tc02),
            ("TC03", "Twenty History calls reach both Account instances", self._tc03),
            ("TC04", "The surviving Account instance serves requests after its peer stops", self._tc04),
            ("TC05", "Unavailable Account returns safe 503 errors without writing a transfer", self._tc05),
            ("TC06", "Restarted Account instances register and serve requests again", self._tc06),
            ("TC07", "Eureka restarts and service discovery recovers", self._tc07),
        ]
        result = "PASS"
        try:
            for test_id, title, action in cases:
                self._case(test_id, title, action)
        except Exception:
            result = "FAIL"
            raise
        finally:
            summary = {
                "run_id": self.run_id,
                "started_at": datetime.now(timezone.utc).isoformat(),
                "result": result,
                "cases": self.cases,
                "request_count": len(self.requests),
                "not_run": [test_id for test_id, _, _ in cases[len(self.cases):]],
            }
            self.output_directory.mkdir(parents=True, exist_ok=True)
            (self.output_directory / "e2e-summary.json").write_text(
                json.dumps(summary, indent=2) + "\n", encoding="utf-8"
            )
            if self.requests:
                with (self.output_directory / "request-results.csv").open("w", encoding="utf-8", newline="") as output:
                    writer = csv.DictWriter(output, fieldnames=self.requests[0].keys())
                    writer.writeheader()
                    writer.writerows(self.requests)
        print(f"TH7 {result}: {self.output_directory}")
        return 0 if result == "PASS" else 1


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--compose-file", required=True)
    parser.add_argument("--env-file", required=True)
    parser.add_argument("--project-name", required=True)
    parser.add_argument("--output-directory", required=True)
    args = parser.parse_args()
    return AcceptanceRun(args).run()


if __name__ == "__main__":
    raise SystemExit(main())
