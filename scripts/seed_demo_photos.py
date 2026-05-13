"""Profile photo fetcher for the MyMentorNet demo dataset seed pipeline.

Primary source: https://thispersondoesnotexist.com — returns a fresh
1024×1024 AI-generated JPEG face per request (uniform gender; we accept
this as a known limitation).

Throttles 1.5s/request to stay polite, retries on 503 with exponential
backoff, and falls back to the pre-vendored pool in
`claude_files/seed/photo_pool/*.jpg` if the network source becomes
unavailable.
"""

from __future__ import annotations

import random
import time
from pathlib import Path
from typing import Optional

import requests


REPO_ROOT = Path(__file__).resolve().parent.parent
PHOTO_POOL_DIR = REPO_ROOT / "claude_files" / "seed" / "photo_pool"
TPDNE_URL = "https://thispersondoesnotexist.com"
TPDNE_HEADERS = {
    "User-Agent": "Mozilla/5.0 (seed-demo-dataset; +https://mymentornet.org)",
    "Accept": "image/jpeg,image/*;q=0.9,*/*;q=0.5",
}
THROTTLE_SECONDS = 1.5
MAX_RETRIES = 3


def fetch_from_thispersondoesnotexist(output_path: Path,
                                       timeout: int = 15) -> bool:
    """Download one face JPEG to output_path. Returns True on success."""
    backoff = 2.0
    for attempt in range(1, MAX_RETRIES + 1):
        try:
            response = requests.get(TPDNE_URL, headers=TPDNE_HEADERS,
                                    timeout=timeout)
            if response.status_code == 200 and len(response.content) > 1000:
                output_path.write_bytes(response.content)
                return True
            print(f"  tpdne status={response.status_code} "
                  f"len={len(response.content)} attempt={attempt}")
        except requests.RequestException as exc:
            print(f"  tpdne error attempt={attempt}: {exc}")
        if attempt < MAX_RETRIES:
            time.sleep(backoff)
            backoff *= 2
    return False


def fetch_from_pool(output_path: Path, rng: random.Random) -> bool:
    """Copy a random JPEG from the vendored pool. Returns True if pool non-empty."""
    if not PHOTO_POOL_DIR.exists():
        return False
    candidates = list(PHOTO_POOL_DIR.glob("*.jpg")) + \
                 list(PHOTO_POOL_DIR.glob("*.jpeg")) + \
                 list(PHOTO_POOL_DIR.glob("*.png"))
    if not candidates:
        return False
    chosen = rng.choice(candidates)
    output_path.write_bytes(chosen.read_bytes())
    return True


def fetch_profile_photo(output_path: Path,
                        rng: Optional[random.Random] = None,
                        prefer_pool: bool = False) -> Optional[str]:
    """Try TPDNE first (or pool first if prefer_pool), fall back the other way.

    Returns the source name ('thispersondoesnotexist' | 'pool') on success,
    None on total failure.
    """
    rng = rng or random.Random()
    if prefer_pool and fetch_from_pool(output_path, rng):
        return "pool"

    if fetch_from_thispersondoesnotexist(output_path):
        time.sleep(THROTTLE_SECONDS)
        return "thispersondoesnotexist"

    if not prefer_pool and fetch_from_pool(output_path, rng):
        return "pool"

    return None


__all__ = [
    "PHOTO_POOL_DIR",
    "THROTTLE_SECONDS",
    "fetch_from_thispersondoesnotexist",
    "fetch_from_pool",
    "fetch_profile_photo",
]
