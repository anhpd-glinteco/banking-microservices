#!/usr/bin/env python3
"""Exercise deployment and recovery against a dedicated TH8 Compose project."""

import argparse
import json
import re
import runpy
import time
import urllib.parse
from pathlib import Path

AcceptanceRun = runpy.run_path(Path(__file__).with_name("th7-acceptance.py"))["AcceptanceRun"]
HEALTH = {
    "discovery-service": 8761,
    "api-gateway": 8080,
    "account-service-a": 8081,
    "account-service-b": 8081,
    "transfer-service": 8082,
    "transaction-history-service": 8084,
    "notification-service": 8083,
}


class DeploymentRun(AcceptanceRun):
    def __init__(self, args):
        # Reuse the HTTP, SQL, and trace assertions from the TH7 baseline.
        super().__init__(args, run_id_key="TH8_RUN_ID", history_port_key="GATEWAY_HOST_PORT")
        self.frontend = f'http://127.0.0.1:{self.environment["FRONTEND_HOST_PORT"]}'
        self.mailpit = f'http://127.0.0.1:{self.environment["MAILPIT_HOST_PORT"]}'
        self.history_timeout = 120
        self.mail_checks = 0
        self.transaction_id = None

    def _request(self, *args, **kwargs):
        response = super()._request(*args, **kwargs)
        entry = self.requests[-1]
        entry["path"] = re.sub(r"(/api/history/number/)[^/]+", r"\1<account>", entry["path"])
        return response

    def _otp_for(self, email):
        otp = super()._otp_for(email)
        deadline = time.monotonic() + 45
        while time.monotonic() < deadline:
            inbox = self._request("GET", "/api/v1/messages", base=self.mailpit)
            messages = inbox.body.get("messages", []) if isinstance(inbox.body, dict) else []
            for message in messages:
                if any(recipient["Address"] == email for recipient in message["To"]):
                    response = self._request("GET", f'/api/v1/message/{message["ID"]}', base=self.mailpit)
                    if isinstance(response.body, dict) and otp in response.body.get("HTML", ""):
                        self.mail_checks += 1
                        return otp
            time.sleep(1)
        raise AssertionError("The Notification listener did not deliver the OTP to the local test mailbox.")

    def _health(self, service):
        url = f"http://127.0.0.1:{HEALTH[service]}/actuator/health"
        output = self._compose("exec", "-T", service, "curl", "-sS", "-w", "\n%{http_code}", url)
        body, status = output.rsplit("\n", 1)
        return int(status), json.loads(body)

    def _startup(self):
        info = self._tc01()
        self._healthy()
        self._require(self._request("GET", "/", base=self.frontend).status == 200, "The frontend did not serve its index.")
        self._capture_ps("compose-ps.txt")
        return info

    def _healthy(self):
        results = {service: self._health(service) for service in HEALTH}
        self._require(all(value == (200, {"status": "UP"}) for value in results.values()),
                      "Expected health HTTP 200 with status UP and no details for every JVM.")
        return {name: {"code": code, **body} for name, (code, body) in results.items()}

    def _notification_and_health(self):
        self.accounts["alice"] = self._account_info("alice")
        self.accounts["bob"] = self._account_info("bob")
        self._require(self._history(self.accounts["alice"], token=self.accounts["bob"]["token"]).status == 403,
                      "History must reject a different owner.")
        logs = self._compose("logs", "--no-color", "--tail", "500", "account-service-a", "account-service-b", "notification-service")
        self._require("OTP email sent." in logs and "OTP event published." in logs, "Expected safe OTP application logs.")
        self._require("th7_" not in logs and '"otp":' not in logs, "Found account fixture identifiers or OTP data in logs.")
        return {"health": self._healthy(), "mailbox_deliveries": self.mail_checks, "safe_log_messages": True}

    def _transfer(self, amount, *, frontend=False):
        alice = self.accounts["alice"]
        before = int(self._mysql("SELECT COUNT(*) FROM transactions;", "bank_transfer"))
        base = self.frontend if frontend else self.gateway
        response = self._request("POST", "/api/transfers", base=base, token=alice["token"], body={
            "fromAccountNumber": alice["number"], "toAccountNumber": self.accounts["bob"]["number"],
            "amount": amount, "description": "TH8 synthetic deployment acceptance",
        })
        self._require(response.status == 200 and isinstance(response.body, dict), "Transfer submission failed.")
        transaction_id = response.body["id"]
        deadline = time.monotonic() + 60
        while time.monotonic() < deadline:
            status = self._request("GET", f"/api/transfers/{transaction_id}/status", base=base, token=alice["token"])
            self._require(status.status == 200 and isinstance(status.body, dict), "Transfer status request failed.")
            state = status.body.get("status")
            self._require(state not in {"FAILED", "COMPENSATED"}, f"Transfer exited with status {state}.")
            if state == "SUCCESS":
                history = self._history(alice)
                if history.status == 200 and any(row["transactionId"] == transaction_id for row in history.body):
                    after = int(self._mysql("SELECT COUNT(*) FROM transactions;", "bank_transfer"))
                    self._require(after == before + 1, "Expected exactly one new transfer row.")
                    return transaction_id
            time.sleep(1)
        raise AssertionError("Transfer SUCCESS and corresponding history were not observed within 60 seconds.")

    def _instance_failover(self):
        initial = self._tc03()
        self._compose("stop", "account-service-a")
        try:
            self._wait_registered(1)
            traces = self._wait_for_instance(self.accounts["alice"], "account-service-b", 5, timeout=120)
            transaction = self._transfer(500)
        finally:
            self._compose("start", "account-service-a")
        recovered = self._tc06()
        return {"initial": initial, "last_five_trace_ids": traces[-5:], "survivor": "account-service-b",
                "successful_history_transaction": transaction, "recovered": recovered}

    def _no_provider(self):
        self._compose("stop", "account-service-a", "account-service-b")
        self._wait_registered(0)
        alice = self.accounts["alice"]
        count = self._mysql("SELECT COUNT(*) FROM transactions;", "bank_transfer")
        offset = self._kafka_offset()
        history = self._history(alice)
        response = self._request("POST", "/api/transfers", token=alice["token"], body={
            "fromAccountNumber": alice["number"], "toAccountNumber": self.accounts["bob"]["number"],
            "amount": 1001, "description": "TH8 provider unavailable",
        })
        self._require((history.status, response.status) == (503, 503), "Expected safe 503 from both dependent operations.")
        self._require(count == self._mysql("SELECT COUNT(*) FROM transactions;", "bank_transfer"), "Transfer count changed.")
        self._require(offset == self._kafka_offset(), "Kafka offset changed.")
        return {"history_http": 503, "transfer_http": 503, "transfer_count_unchanged": True, "kafka_offset_unchanged": True}

    def _recover(self):
        recovery = self._tc06()
        self.transaction_id = self._transfer(1000)
        return {**recovery, "successful_history_transaction": self.transaction_id}

    def _snapshot(self):
        alice = self.accounts["alice"]
        balance = self._mysql(f'SELECT balance FROM accounts WHERE id={alice["id"]};', "bank_account")
        history = self._history(alice)
        self._require(history.status == 200 and isinstance(history.body, list), "Cannot read persisted history.")
        ids = sorted({row["transactionId"] for row in history.body if row["transactionId"]})
        self._require(self.transaction_id in ids and bool(balance), "The reference transfer or balance is missing.")
        return {"alice_balance": balance, "transaction_ids": ids}

    def _db_recovery(self):
        initial = self._snapshot()
        self._compose("stop", "mysql")
        try:
            code, health = self._health("account-service-b")
            self._require(code == 503 and health.get("status") == "DOWN", "Account health did not reflect loss of MySQL.")
            raw_log = self._compose("logs", "--no-color", "--tail", "300", "account-service-b")
            signature = next((text for text in ("Communications link failure", "JDBCConnectionException",
                                               "Connection is not available", "Connection refused")
                              if text in raw_log), None)
            self._require(bool(signature), "Missing recognizable database failure in Account logs.")
        finally:
            self._compose("start", "mysql")
            self._compose("up", "-d", "--wait", "--wait-timeout", "240")
        self._wait_registered(2)
        after = self._snapshot()
        self._require(initial == after, "Data changed when MySQL was restarted.")
        return {"account_http_while_db_down": code, "log_signature": signature, "before": initial, "after": after}

    def _stack_recovery(self):
        before = self._snapshot()
        self._compose("down")
        self._compose("up", "-d", "--wait", "--wait-timeout", "300")
        self._wait_registered(2)
        after = self._snapshot()
        self._require(before == after, "Data changed when the whole stack was recreated.")
        self._capture_ps("compose-ps-after-recreate.txt")
        transaction = self._transfer(501, frontend=True)
        return {"before": before, "after": after, "successful_history_transaction_via_nginx": transaction}

    def _capture_ps(self, name):
        (self.output_directory / name).write_text(self._compose("ps") + "\n", encoding="utf-8")

    def run(self):
        cases = (
            ("DEP01", "Clean deployment and Eureka memberships", self._startup),
            ("DEP02", "Actuator, log safety, and local mail delivery", self._notification_and_health),
            ("BANK01", "Account failover while a transfer completes", self._instance_failover),
            ("DEP03", "No provider, no write", self._no_provider),
            ("DEP04", "Provider recovery", self._recover),
            ("DEP05", "Database outage and persistence", self._db_recovery),
            ("DEP06", "Stack recreation preserving data", self._stack_recovery),
        )
        try:
            for test_id, title, action in cases:
                self._case(test_id, title, action)
        except Exception:
            return 1
        finally:
            path = self.output_directory / "e2e-summary.json"
            summary = json.loads(path.read_text(encoding="utf-8"))
            summary["result"] = "PASS" if all(case["result"] == "PASS" for case in self.cases) and len(self.cases) == len(cases) else "FAIL"
            summary["not_run"] = [test_id for test_id, _, _ in cases[len(self.cases):]]
            path.write_text(json.dumps(summary, indent=2) + "\n", encoding="utf-8")
        return 0


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--env-file", required=True)
    parser.add_argument("--compose-file", required=True)
    parser.add_argument("--project-name", required=True)
    parser.add_argument("--output-directory", required=True)
    args = parser.parse_args()
    if Path(args.compose_file).name != "compose.th8.yml" or not re.fullmatch(r"bank-th8-[a-z0-9][a-z0-9-]{0,39}", args.project_name):
        parser.error("Use an isolated TH8 Compose project.")
    run = DeploymentRun(args)
    if args.project_name != f"bank-th8-{run.run_id}":
        parser.error("Project name does not match the initialized TH8 run.")
    return run.run()


if __name__ == "__main__":
    raise SystemExit(main())
