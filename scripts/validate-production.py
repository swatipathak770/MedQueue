#!/usr/bin/env python3
"""Offline production Compose input checks. Values are never printed."""

import ipaddress
import re
import sys
from pathlib import Path, PurePosixPath


REQUIRED = (
    "DB_URL",
    "DB_USERNAME",
    "DB_PASSWORD",
    "JWT_SECRET",
    "CORS_ALLOWED_ORIGINS",
    "RDS_TRUSTSTORE_PATH",
    "RDS_TRUSTSTORE_PASSWORD",
    "MEDQUEUE_BACKEND_IMAGE",
    "MEDQUEUE_FRONTEND_IMAGE",
    "WEB_BIND_ADDRESS",
)
PRIVATE_V4 = tuple(
    ipaddress.ip_network(value)
    for value in ("10.0.0.0/8", "172.16.0.0/12", "192.168.0.0/16")
)


def load_env(path: Path) -> dict[str, str]:
    values: dict[str, str] = {}
    for line_number, line in enumerate(path.read_text(encoding="utf-8").splitlines(), 1):
        stripped = line.strip()
        if not stripped or stripped.startswith("#"):
            continue
        if "=" not in stripped:
            raise ValueError(f"Invalid environment entry on line {line_number}")
        key, value = stripped.split("=", 1)
        value = value.strip()
        if len(value) >= 2 and value[0] == value[-1] and value[0] in "\"'":
            value = value[1:-1]
        values[key.strip()] = value
    return values


def validate(values: dict[str, str]) -> list[str]:
    errors = [f"{key} is missing or still a placeholder" for key in REQUIRED
              if not values.get(key, "").strip() or "REPLACE_" in values[key]]

    for key in ("MEDQUEUE_BACKEND_IMAGE", "MEDQUEUE_FRONTEND_IMAGE"):
        image = values.get(key, "")
        if image and not re.fullmatch(r"[^\s@]+@sha256:[0-9a-fA-F]{64}", image):
            errors.append(f"{key} must be pinned to a 64-character SHA-256 digest")
        if image and re.search(r":latest(?:@|$)", image):
            errors.append(f"{key} must not use the latest tag")

    address = values.get("WEB_BIND_ADDRESS", "")
    try:
        parsed = ipaddress.ip_address(address)
        if parsed.version != 4 or not any(parsed in network for network in PRIVATE_V4):
            errors.append("WEB_BIND_ADDRESS must be an RFC1918 private IPv4 address")
        if parsed.is_loopback or parsed.is_unspecified or parsed.is_link_local or parsed.is_multicast:
            errors.append("WEB_BIND_ADDRESS must be a usable private interface address")
    except ValueError:
        errors.append("WEB_BIND_ADDRESS must be a valid IPv4 address")

    database_url = values.get("DB_URL", "")
    match = re.match(r"^jdbc:mysql://([^/:?#]+)(?::\d+)?/([^?]+)", database_url)
    if not match:
        errors.append("DB_URL must be a JDBC URL to the RDS database endpoint")
    else:
        host = match.group(1).lower()
        if host in {"mysql", "localhost", "127.0.0.1", "::1"}:
            errors.append("DB_URL must not target a local or Docker MySQL host")
        if not (host.endswith(".rds.amazonaws.com") or host.endswith(".rds.amazonaws.com.cn")):
            errors.append("DB_URL host must be an Amazon RDS endpoint")

    trust_path = values.get("RDS_TRUSTSTORE_PATH", "")
    if trust_path and not (Path(trust_path).is_absolute() or PurePosixPath(trust_path).is_absolute()):
        errors.append("RDS_TRUSTSTORE_PATH must be an absolute host path")

    if values.get("SPRING_PROFILES_ACTIVE", "prod") != "prod":
        errors.append("SPRING_PROFILES_ACTIVE must be prod")
    return errors


def main() -> int:
    if len(sys.argv) != 2:
        print("Usage: python scripts/validate-production.py <production-env-file>")
        return 2
    try:
        values = load_env(Path(sys.argv[1]))
        errors = validate(values)
    except (OSError, UnicodeError, ValueError) as error:
        print(f"Validation could not read the environment file ({type(error).__name__}).")
        return 2
    if errors:
        for error in errors:
            print(f"FAIL: {error}")
        return 1
    print("PASS: required production inputs, digest-pinned images, RDS endpoint, and private bind address validated.")
    print("NOTE: the Spring prod profile enforces TLS certificate and hostname verification using the configured PKCS12 truststore.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
