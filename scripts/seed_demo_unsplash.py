"""Unsplash API client for post-image lookup in the demo seed pipeline.

Reads UNSPLASH_ACCESS_KEY from environment (loaded via python-dotenv from
.env.seed in the orchestrator). Searches /photos/random with a free-text
query derived from each post's markdown `image_topic`. Honours Unsplash's
50 requests/hour free-tier cap by default; pass `respect_rate_limit=False`
to burn through it quickly.
"""

from __future__ import annotations

import os
import time
from pathlib import Path
from typing import Optional

import requests


UNSPLASH_BASE = "https://api.unsplash.com"
DEFAULT_QPS_SLEEP = 75.0  # 3600s / 50 req = 72s; round up to 75 for safety.
BURST_SLEEP = 0.5


class UnsplashClient:
    def __init__(self, access_key: Optional[str] = None,
                 respect_rate_limit: bool = True) -> None:
        self.access_key = access_key or os.environ.get("UNSPLASH_ACCESS_KEY")
        if not self.access_key:
            raise RuntimeError("UNSPLASH_ACCESS_KEY not set")
        self.respect_rate_limit = respect_rate_limit
        self.requests_made = 0
        self.last_request_at = 0.0

    @property
    def is_configured(self) -> bool:
        return bool(self.access_key)

    def _wait(self) -> None:
        if self.respect_rate_limit:
            elapsed = time.monotonic() - self.last_request_at
            target = DEFAULT_QPS_SLEEP
            if elapsed < target:
                time.sleep(target - elapsed)
        else:
            time.sleep(BURST_SLEEP)

    def random_photo(self, query: str,
                      orientation: str = "landscape") -> Optional[dict]:
        """Return the JSON payload of a random photo for the given query."""
        self._wait()
        response = requests.get(
            f"{UNSPLASH_BASE}/photos/random",
            headers={
                "Authorization": f"Client-ID {self.access_key}",
                "Accept-Version": "v1",
            },
            params={"query": query, "orientation": orientation, "content_filter": "high"},
            timeout=20,
        )
        self.last_request_at = time.monotonic()
        self.requests_made += 1

        if response.status_code == 403:
            print(f"  unsplash 403 (rate-limited or invalid key); aborting.")
            return None
        if response.status_code == 404:
            return None
        response.raise_for_status()
        return response.json()

    def download_image(self, photo_payload: dict, output_path: Path,
                       size: str = "regular") -> bool:
        """Download the chosen size; trigger the Unsplash download endpoint as required by ToS."""
        urls = photo_payload.get("urls", {})
        download_url = urls.get(size) or urls.get("regular") or urls.get("small")
        if not download_url:
            return False

        # Mark download per Unsplash API guidelines
        download_endpoint = photo_payload.get("links", {}).get("download_location")
        if download_endpoint:
            try:
                requests.get(download_endpoint,
                             headers={"Authorization": f"Client-ID {self.access_key}"},
                             timeout=10)
            except requests.RequestException:
                pass

        response = requests.get(download_url, timeout=30)
        if response.status_code != 200:
            return False
        output_path.write_bytes(response.content)
        return True


__all__ = [
    "UnsplashClient",
    "UNSPLASH_BASE",
    "DEFAULT_QPS_SLEEP",
]
