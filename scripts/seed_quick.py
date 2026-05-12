#!/usr/bin/env python3
"""Quick seed: creates a small set of test users you can log in with.

Set SEED_HOST env var to target a different backend; defaults to localhost.
Examples:
    python3 scripts/seed_quick.py                      # → localhost:8080
    SEED_HOST=167.71.44.71 python3 scripts/seed_quick.py
"""

import json
import os
import re
import time
import urllib.error
import urllib.request

HOST = os.environ.get("SEED_HOST", "localhost")
BASE = f"http://{HOST}:8080/api"
TEST_SUPPORT = f"http://{HOST}:8080/api/test"
MAILHOG = f"http://{HOST}:8025/api/v2"
PASSWORD = "Seed1234!"

USERS = [
    {"first_name": "Can",   "last_name": "Mentee",  "email": "can.mentee@seed.test",   "is_mentor": False},
    {"first_name": "Ece",   "last_name": "Mentee",  "email": "ece.mentee@seed.test",   "is_mentor": False},
    {"first_name": "Ahmet", "last_name": "Mentor",  "email": "ahmet.mentor@seed.test", "is_mentor": True},
    {"first_name": "Zeynep","last_name": "Mentor",  "email": "zeynep.mentor@seed.test","is_mentor": True},
]


def request(method, url, data=None, token=None):
    body = json.dumps(data).encode() if data else None
    headers = {"Content-Type": "application/json"}
    if token:
        headers["Authorization"] = f"Bearer {token}"
    req = urllib.request.Request(url, data=body, headers=headers, method=method)
    try:
        with urllib.request.urlopen(req) as r:
            text = r.read().decode()
            return r.status, json.loads(text) if text else {}
    except urllib.error.HTTPError as e:
        text = e.read().decode()
        return e.code, json.loads(text) if text else {}


def get_verification_token(email):
    # Try test endpoint first
    status, data = request("GET", f"{TEST_SUPPORT}/verification-token?email={email}")
    if status == 200 and data.get("token"):
        return data["token"]

    # Fall back to MailHog
    for _ in range(10):
        status, data = request("GET", f"{MAILHOG}/messages?limit=200")
        for msg in (data.get("items") or []):
            to_header = msg.get("Content", {}).get("Headers", {}).get("To", [""])
            if email.lower() not in to_header[0].lower():
                continue
            body = msg.get("Content", {}).get("Body", "")
            match = re.search(r"token=([A-Za-z0-9\-]+)", body)
            if match:
                return match.group(1)
        time.sleep(1)
    return None


def seed_admin(user):
    status, data = request("POST", f"{TEST_SUPPORT}/admin")
    if status == 200:
        print(f"  Created admin via test endpoint (email={data.get('email')})")
        return
    # Fall back: register normally and note it
    print(f"  Admin endpoint not available ({status}), skipping admin creation")


def seed_regular(user):
    email = user["email"]

    # Try login first
    status, resp = request("POST", f"{BASE}/auth/login", {"email": email, "password": PASSWORD})
    if status == 200 and "sessionToken" in resp:
        print(f"  Already exists and verified (userId={resp.get('userId')})")
        return

    # Always attempt registration; 409 means the user already exists (unverified).
    reg_status, reg_resp = request("POST", f"{BASE}/auth/register", {
        "firstName": user["first_name"],
        "lastName":  user["last_name"],
        "email":     email,
        "password":  PASSWORD,
        "isMentor":  user["is_mentor"],
    })
    if reg_status not in (200, 201, 409):
        print(f"  Register failed ({reg_status}): {reg_resp}")
        return

    # Get verification token (from test endpoint or MailHog)
    token = get_verification_token(email)
    if not token:
        print(f"  Could not get verification token for {email}")
        return

    request("GET", f"{BASE}/auth/verify-email?token={token}")

    # Login
    status, resp = request("POST", f"{BASE}/auth/login", {"email": email, "password": PASSWORD})
    if status == 200:
        print(f"  Verified & logged in OK (userId={resp.get('userId')})")
    else:
        print(f"  Login after verify failed ({status}): {resp}")


def main():
    print("=" * 50)
    print("Quick seed — creating test users")
    print("=" * 50)
    print(f"Password for all: {PASSWORD}\n")

    for user in USERS:
        role = "ADMIN" if user.get("admin") else ("MENTOR" if user["is_mentor"] else "MENTEE")
        email = user["email"]
        print(f"[{role}] {user['first_name']} {user['last_name']} <{email}>")

        if user.get("admin"):
            seed_admin(user)
        else:
            seed_regular(user)

    print("\nDone! Login credentials (use these in the mobile app):")
    print(f"  can.mentee@seed.test    /  {PASSWORD}  (MENTEE)")
    print(f"  ece.mentee@seed.test    /  {PASSWORD}  (MENTEE)")
    print(f"  ahmet.mentor@seed.test  /  {PASSWORD}  (MENTOR)")
    print(f"  zeynep.mentor@seed.test /  {PASSWORD}  (MENTOR)")


if __name__ == "__main__":
    main()
