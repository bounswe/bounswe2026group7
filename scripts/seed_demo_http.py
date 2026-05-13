"""HTTP helpers for the MyMentorNet demo dataset seed pipeline.

Authenticates seed users, caches JWTs to disk, and provides multipart upload
plus mentorship request/accept POSTs. Used for code paths where the real
backend behaviour (magic-byte validation, follow-graph sync, notification
fanout, lazy conversation creation) needs to fire authentically.
"""

from __future__ import annotations

import json
import mimetypes
import time
import urllib.error
import urllib.request
import uuid
from pathlib import Path
from typing import Optional

import requests


API_BASE = "http://localhost:8080/api"
TOKEN_CACHE_PATH = Path(__file__).resolve().parent / ".seed_tokens.json"
LOGIN_BACKOFF_SECONDS = 0.0  # bumped on 429


# ── token cache ────────────────────────────────────────────────────────────

class TokenCache:
    """Simple email→sessionToken cache backed by .seed_tokens.json."""

    def __init__(self, path: Path = TOKEN_CACHE_PATH) -> None:
        self.path = path
        self._tokens: dict[str, str] = {}
        if path.exists():
            try:
                self._tokens = json.loads(path.read_text())
            except json.JSONDecodeError:
                self._tokens = {}

    def get(self, email: str) -> Optional[str]:
        return self._tokens.get(email)

    def put(self, email: str, token: str) -> None:
        self._tokens[email] = token
        self.path.write_text(json.dumps(self._tokens, indent=2))

    def clear(self) -> None:
        self._tokens = {}
        if self.path.exists():
            self.path.unlink()


# ── auth ───────────────────────────────────────────────────────────────────

def login(email: str, password: str,
          cache: Optional[TokenCache] = None) -> str:
    """Returns sessionToken, using cache when present."""
    if cache is not None:
        cached = cache.get(email)
        if cached:
            return cached

    response = requests.post(
        f"{API_BASE}/auth/login",
        json={"email": email, "password": password},
        timeout=10,
    )
    if response.status_code == 429:
        # rate-limited despite APP_RATELIMIT_ENABLED=false — back off
        time.sleep(5)
        return login(email, password, cache)
    response.raise_for_status()
    token = response.json()["sessionToken"]
    if cache is not None:
        cache.put(email, token)
    return token


def auth_headers(token: str) -> dict[str, str]:
    return {"Authorization": f"Bearer {token}"}


# ── profile photo upload ───────────────────────────────────────────────────

def upload_profile_photo(token: str, file_path: Path,
                         content_type: str = "image/jpeg") -> dict:
    """POST /api/users/me/photo (multipart)."""
    with open(file_path, "rb") as fh:
        response = requests.post(
            f"{API_BASE}/users/me/photo",
            headers=auth_headers(token),
            files={"file": (file_path.name, fh, content_type)},
            timeout=30,
        )
    response.raise_for_status()
    return response.json()


# ── attachment upload (for post images) ────────────────────────────────────

def upload_attachment(token: str, file_path: Path,
                      content_type: Optional[str] = None) -> uuid.UUID:
    """POST /api/messages/attachments — returns the new attachment UUID."""
    if content_type is None:
        content_type = (mimetypes.guess_type(str(file_path))[0]
                        or "application/octet-stream")
    with open(file_path, "rb") as fh:
        response = requests.post(
            f"{API_BASE}/messages/attachments",
            headers=auth_headers(token),
            files={"file": (file_path.name, fh, content_type)},
            timeout=30,
        )
    response.raise_for_status()
    payload = response.json()
    return uuid.UUID(payload["id"])


# ── mentorship request / accept ────────────────────────────────────────────

def create_mentorship_request(mentee_token: str, *, mentor_id: int,
                              message: str) -> dict:
    response = requests.post(
        f"{API_BASE}/mentorship-requests",
        headers={**auth_headers(mentee_token),
                 "Content-Type": "application/json"},
        json={"mentorId": mentor_id, "message": message},
        timeout=15,
    )
    response.raise_for_status()
    return response.json()


def accept_mentorship_request(mentor_token: str, *, request_id: int,
                              duration: int) -> dict:
    """duration must be one of {1, 3, 6} — enforced by backend."""
    response = requests.put(
        f"{API_BASE}/mentorship-requests/{request_id}/accept",
        headers={**auth_headers(mentor_token),
                 "Content-Type": "application/json"},
        json={"duration": duration},
        timeout=15,
    )
    response.raise_for_status()
    return response.json()


def health_check() -> bool:
    try:
        response = requests.get(f"{API_BASE.replace('/api', '')}/actuator/health",
                                timeout=5)
        return response.status_code == 200 and response.json().get("status") == "UP"
    except (requests.RequestException, ValueError):
        return False


__all__ = [
    "API_BASE",
    "TOKEN_CACHE_PATH",
    "TokenCache",
    "login",
    "auth_headers",
    "upload_profile_photo",
    "upload_attachment",
    "create_mentorship_request",
    "accept_mentorship_request",
    "health_check",
]
