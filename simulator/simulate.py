#!/usr/bin/env python3
"""
SecureScope 로그 시뮬레이터
각 시나리오로 다양한 공격 패턴을 재현한다.

Usage:
    python simulate.py --scenario all
    python simulate.py --scenario brute --target http://localhost:8080
    python simulate.py --scenario portscan --attacker-ip 10.0.0.99
    python simulate.py --scenario normal --count 20
"""

import argparse
import json
import random
import time
from datetime import datetime, timezone, timedelta

import urllib.request
import urllib.error

# ─── 상수 ──────────────────────────────────────────────────────────────────────

NORMAL_IPS   = ["192.168.1.10", "192.168.1.11", "192.168.1.50", "10.0.0.5"]
ATTACKER_IPS = ["203.0.113.42", "198.51.100.7", "172.16.254.1"]
WHITELIST_MACS = [
    "AA:BB:CC:DD:EE:01",
    "AA:BB:CC:DD:EE:02",
    "AA:BB:CC:DD:EE:03",
]
USERS = ["admin", "user1", "user2", "deploy", "guest"]

# ─── HTTP 헬퍼 ─────────────────────────────────────────────────────────────────

def post_event(base_url: str, payload: dict, verbose: bool = False) -> bool:
    url  = f"{base_url}/api/events"
    body = json.dumps(payload).encode("utf-8")
    req  = urllib.request.Request(
        url, data=body,
        headers={"Content-Type": "application/json"},
        method="POST",
    )
    try:
        with urllib.request.urlopen(req, timeout=5) as resp:
            if verbose:
                result = json.loads(resp.read())
                print(f"  ✓ [{payload['eventType']}] {payload['sourceIp']} → id={result['data']['id']}")
            return True
    except urllib.error.HTTPError as e:
        print(f"  ✗ HTTP {e.code}: {e.read().decode()}")
        return False
    except Exception as e:
        print(f"  ✗ Error: {e}")
        return False


def now_iso() -> str:
    return datetime.now(timezone.utc).isoformat()


def after_hours_iso() -> str:
    """허용 시간대(09-18) 밖인 새벽 2시 타임스탬프 반환"""
    base = datetime.now(timezone(timedelta(hours=9)))  # KST
    return base.replace(hour=2, minute=random.randint(0, 59)).astimezone(timezone.utc).isoformat()


# ─── 시나리오 ───────────────────────────────────────────────────────────────────

def scenario_normal(base_url: str, count: int, verbose: bool) -> None:
    """정상 트래픽: 화이트리스트 MAC, 등록된 IP, 업무 시간"""
    print(f"\n[NORMAL] {count}개의 정상 이벤트를 전송합니다...")
    for _ in range(count):
        payload = {
            "eventType": "LOGIN_SUCCESS",
            "sourceIp": random.choice(NORMAL_IPS),
            "macAddress": random.choice(WHITELIST_MACS),
            "userId": random.choice(USERS),
            "occurredAt": now_iso(),
        }
        post_event(base_url, payload, verbose)
        time.sleep(0.1)


def scenario_brute_force(base_url: str, attacker_ip: str, verbose: bool) -> None:
    """브루트포스: 동일 IP에서 LOGIN_FAIL 7회 연속 (임계값 5회 초과)"""
    print(f"\n[BRUTE FORCE] {attacker_ip} 에서 로그인 실패 7회 전송...")
    for i in range(7):
        payload = {
            "eventType": "LOGIN_FAIL",
            "sourceIp": attacker_ip,
            "userId": "admin",
            "occurredAt": now_iso(),
        }
        post_event(base_url, payload, verbose)
        print(f"  → 시도 {i + 1}/7")
        time.sleep(0.3)


def scenario_port_scan(base_url: str, attacker_ip: str, verbose: bool) -> None:
    """포트 스캔: 동일 IP에서 12개 서로 다른 포트 접근 (임계값 10개 초과)"""
    ports = random.sample(range(1, 65535), 12)
    print(f"\n[PORT SCAN] {attacker_ip} → 포트 {ports[:5]}... 등 12개 접근")
    for port in ports:
        payload = {
            "eventType": "PORT_SCAN",
            "sourceIp": attacker_ip,
            "targetPort": port,
            "occurredAt": now_iso(),
        }
        post_event(base_url, payload, verbose)
        time.sleep(0.1)


def scenario_unauthorized_mac(base_url: str, attacker_ip: str, verbose: bool) -> None:
    """비인가 MAC: 화이트리스트에 없는 MAC 주소로 접근"""
    bad_macs = ["DE:AD:BE:EF:00:01", "CA:FE:BA:BE:00:02", "FA:CE:B0:0C:00:03"]
    print(f"\n[UNAUTHORIZED MAC] {len(bad_macs)}개의 미등록 MAC 주소로 접근...")
    for mac in bad_macs:
        payload = {
            "eventType": "UNAUTHORIZED_ACCESS",
            "sourceIp": attacker_ip,
            "macAddress": mac,
            "userId": random.choice(USERS),
            "occurredAt": now_iso(),
        }
        post_event(base_url, payload, verbose)
        time.sleep(0.2)


def scenario_after_hours(base_url: str, attacker_ip: str, verbose: bool) -> None:
    """시간 외 접근: 새벽 2시(KST) 타임스탬프로 이벤트 전송"""
    print(f"\n[AFTER HOURS] 새벽 2시 KST 타임스탬프로 접근 이벤트 전송...")
    for _ in range(3):
        payload = {
            "eventType": "LOGIN_SUCCESS",
            "sourceIp": attacker_ip,
            "macAddress": random.choice(WHITELIST_MACS),
            "userId": "admin",
            "occurredAt": after_hours_iso(),
        }
        post_event(base_url, payload, verbose)
        time.sleep(0.2)


# ─── 메인 ───────────────────────────────────────────────────────────────────────

def main() -> None:
    parser = argparse.ArgumentParser(description="SecureScope 이벤트 시뮬레이터")
    parser.add_argument("--target",       default="http://localhost:8080", help="API 서버 URL")
    parser.add_argument("--scenario",     default="all",
                        choices=["all", "normal", "brute", "portscan", "mac", "afterhours"],
                        help="실행할 시나리오")
    parser.add_argument("--attacker-ip",  default=ATTACKER_IPS[0], help="공격자 IP")
    parser.add_argument("--count",        type=int, default=10, help="normal 시나리오 이벤트 수")
    parser.add_argument("--verbose",      action="store_true", help="응답 출력")
    args = parser.parse_args()

    print(f"🛡️  SecureScope Simulator")
    print(f"   Target  : {args.target}")
    print(f"   Scenario: {args.scenario}")
    print(f"   Attacker: {args.attacker_ip}")
    print("─" * 50)

    if args.scenario in ("all", "normal"):
        scenario_normal(args.target, args.count, args.verbose)

    if args.scenario in ("all", "brute"):
        scenario_brute_force(args.target, args.attacker_ip, args.verbose)

    if args.scenario in ("all", "portscan"):
        scenario_port_scan(args.target, args.attacker_ip, args.verbose)

    if args.scenario in ("all", "mac"):
        scenario_unauthorized_mac(args.target, args.attacker_ip, args.verbose)

    if args.scenario in ("all", "afterhours"):
        scenario_after_hours(args.target, args.attacker_ip, args.verbose)

    print("\n✅  시뮬레이션 완료")


if __name__ == "__main__":
    main()
