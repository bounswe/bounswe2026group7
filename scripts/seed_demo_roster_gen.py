#!/usr/bin/env python3
"""Generate `claude_files/seed/roster.yaml` for the demo seed pipeline.

One-shot deterministic generator: gender-tagged Turkish names,
random 1st interest from `seed_data` interest sets, manually-curated
2nd / 3rd interests (drawn from a related-interest map below), city
from `seed_data.LOCATIONS`, mentor capacity uniform[1..4], availability
days, profile-photo flag, and post-count bell-curve roll.

Run once before authoring the markdown corpus:
    python scripts/seed_demo_roster_gen.py

Re-running overwrites `claude_files/seed/roster.yaml` deterministically
(same MASTER_SEED ⇒ same roster).
"""

from __future__ import annotations

import random
import sys
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(REPO_ROOT / "scripts"))

import seed_demo_random as R
from seed_data import (
    MENTEE_INTEREST_SETS,
    MENTOR_INTEREST_SETS,
)


# Curated locations across Turkey (deliberately spread, not Istanbul-heavy).
# Weight roughly mirrors population but trimmed so demos don't feel
# concentrated. ~50% Istanbul districts, ~50% spread across Anatolia / Aegean
# / Mediterranean / Black Sea / Marmara.
LOCATIONS = [
    # Istanbul districts (15)
    "Kadikoy, Istanbul", "Sisli, Istanbul", "Besiktas, Istanbul",
    "Uskudar, Istanbul", "Bakirkoy, Istanbul", "Maltepe, Istanbul",
    "Beyoglu, Istanbul", "Atasehir, Istanbul", "Sariyer, Istanbul",
    "Pendik, Istanbul", "Umraniye, Istanbul", "Kartal, Istanbul",
    "Esenyurt, Istanbul", "Avcilar, Istanbul", "Beylikduzu, Istanbul",
    # Ankara (5)
    "Cankaya, Ankara", "Kecioren, Ankara", "Yenimahalle, Ankara",
    "Etimesgut, Ankara", "Mamak, Ankara",
    # Izmir (4)
    "Konak, Izmir", "Karsiyaka, Izmir", "Bornova, Izmir", "Buca, Izmir",
    # Bursa (2)
    "Nilufer, Bursa", "Osmangazi, Bursa",
    # Antalya (2)
    "Muratpasa, Antalya", "Kepez, Antalya",
    # Aegean (3)
    "Manisa", "Aydin", "Denizli",
    # Mediterranean (3)
    "Adana", "Mersin", "Hatay",
    # Marmara (3)
    "Kocaeli", "Sakarya", "Tekirdag",
    # Black Sea (3)
    "Trabzon", "Samsun", "Ordu",
    # Central Anatolia (3)
    "Konya", "Kayseri", "Eskisehir",
    # Southeast (2)
    "Gaziantep", "Diyarbakir",
    # East (2)
    "Erzurum", "Van",
    # College towns (2)
    "Edirne", "Canakkale",
]

OUTPUT_PATH = REPO_ROOT / "claude_files" / "seed" / "roster.yaml"
NUM_MENTORS = 12
NUM_MENTEES = 48


# ── gendered Turkish name pool (manually curated) ──────────────────────────

FIRST_NAMES_MALE = [
    "Ahmet", "Ali", "Alper", "Arda", "Aras", "Atilla", "Aydin", "Aytac",
    "Baran", "Baris", "Berat", "Berke", "Bora", "Burak", "Burhan", "Can",
    "Caner", "Cem", "Cihan", "Cinar", "Dogan", "Egemen", "Emir", "Emre",
    "Eren", "Erkan", "Ersin", "Ertan", "Fikret", "Furkan", "Gokhan", "Hakan",
    "Hakki", "Halil", "Halis", "Haluk", "Hasan", "Huseyin", "Ibrahim", "Ilker",
    "Ismail", "Kaan", "Kemal", "Kerem", "Korhan", "Kubilay", "Levent", "Lutfi",
    "Mahmut", "Mehmet", "Mert", "Metin", "Mustafa", "Murat", "Nihat", "Oguz",
    "Okan", "Onur", "Orhan", "Osman", "Polat", "Ramazan", "Recep", "Riza",
    "Sadik", "Selim", "Selcuk", "Serdar", "Serkan", "Sinan", "Suleyman", "Tarik",
    "Tayfun", "Taylan", "Tolga", "Tunc", "Ufuk", "Umut", "Volkan", "Yagiz",
    "Yusuf", "Zafer",
]

FIRST_NAMES_FEMALE = [
    "Asli", "Asuman", "Aybike", "Ayca", "Ayse", "Aysu", "Banu", "Basak",
    "Begum", "Berna", "Berrin", "Beste", "Bilge", "Buse", "Burcu", "Canan",
    "Cansu", "Ceren", "Cigdem", "Damla", "Defne", "Derya", "Didem", "Dilara",
    "Duygu", "Ece", "Ela", "Elif", "Elvan", "Emine", "Eren", "Esra",
    "Esma", "Ezgi", "Fatma", "Feride", "Filiz", "Funda", "Gamze", "Gizem",
    "Gokce", "Gonca", "Gul", "Gulay", "Gulsah", "Hale", "Hande", "Hatice",
    "Hilal", "Ipek", "Irem", "Jale", "Kader", "Kubra", "Lale", "Leyla",
    "Mehtap", "Melda", "Melek", "Melike", "Melis", "Melisa", "Merve", "Meryem",
    "Mina", "Naz", "Nazli", "Neslihan", "Nesrin", "Nil", "Nur", "Nuray",
    "Oya", "Ozge", "Pelin", "Pinar", "Reyhan", "Sabiha", "Selin", "Seda",
    "Sema", "Sevgi", "Sevim", "Sezen", "Simge", "Songul", "Sude", "Sule",
    "Tugce", "Tulay", "Umran", "Yagmur", "Yasemin", "Zehra", "Zerrin", "Zeynep",
]


LAST_NAMES = [
    "Acar", "Akdeniz", "Aksoy", "Aksu", "Akyol", "Altinok", "Aral", "Arikan",
    "Arslan", "Asik", "Aslan", "Aslanli", "Atalay", "Atay", "Aydin", "Aykac",
    "Bakir", "Balci", "Basaran", "Bayar", "Bayer", "Bayraktar", "Bicakci",
    "Bilgin", "Bozkurt", "Caglar", "Cakir", "Can", "Cetin", "Cevik", "Cinar",
    "Colak", "Demir", "Demirci", "Demiroz", "Dilek", "Dinc", "Dogan", "Durak",
    "Eken", "Ekinci", "Elmas", "Erdem", "Eren", "Ergin", "Ergun", "Erkmen",
    "Ersoy", "Genc", "Gok", "Gulhan", "Gultekin", "Gunes", "Guney", "Gungor",
    "Hekim", "Inan", "Ipekci", "Isik", "Kahraman", "Kaplan", "Kara", "Karaca",
    "Karagoz", "Kavak", "Kaya", "Kayan", "Kilic", "Kilicoglu", "Kiraz", "Koc",
    "Korkmaz", "Kurt", "Mert", "Mutlu", "Nalbant", "Onal", "Onder", "Oz",
    "Ozcan", "Ozdemir", "Ozkan", "Ozturk", "Pak", "Polat", "Sahin", "Sari",
    "Sezen", "Sezer", "Sonmez", "Soylu", "Subasi", "Surmeli", "Tan", "Tasdemir",
    "Tekin", "Toprak", "Tuncer", "Turan", "Tufekci", "Ural", "Uysal", "Yalcin",
    "Yaman", "Yavuz", "Yenidogan", "Yildiz", "Yilmaz",
]


# ── related-interest map (curated; "kendin seç" choices from seed_data clusters) ─

# For each cluster the 3 items are intra-related; we additionally hand-pick
# 2 "neighbour" clusters so the 3rd interest can come from an adjacent
# domain when the 20% roll fires.

MENTOR_NEIGHBOURS = {
    0: [1, 21],     # Frontend Architecture → Backend Eng, Technical Writing
    1: [0, 9],      # Backend Engineering → Frontend, Cloud Arch
    2: [17, 27],    # Machine Learning → MLOps, AI Governance
    3: [28, 29],    # Digital Marketing → Customer Success, Sales Eng
    4: [10, 12],    # Career Dev → Eng Mgmt, Career Transition
    5: [0, 21],     # UI/UX Strategy → Frontend, Tech Writing
    6: [0, 1],      # iOS Eng → Frontend, Backend
    7: [11, 19],    # Entrepreneurship → Angel Investing, Startup Founding
    8: [4, 10],     # Research Leadership → Career Dev, Eng Mgmt
    9: [1, 18],     # Cloud Architecture → Backend, Platform Eng
    10: [4, 16],    # Eng Mgmt → Career Dev, Agile Leadership
    11: [7, 19],    # Angel Investing → Entrepreneurship, Startup Founding
    12: [4, 30],    # Career Transition → Career Dev, Inclusive Hiring
    13: [11, 7],    # Retirement → Angel Investing, Entrepreneurship
    14: [9, 1],     # Security Arch → Cloud Arch, Backend
    15: [2, 17],    # Data Platform → ML, MLOps
    16: [10, 21],   # Agile Leadership → Eng Mgmt, Tech Writing
    17: [2, 15],    # MLOps → ML, Data Platform
    18: [9, 14],    # Platform Eng → Cloud Arch, Security
    19: [7, 11],    # Startup Founding → Entrepreneurship, Angel Invest
    20: [21, 5],    # Tech Writing → was mis-numbered; cap at 20
    21: [0, 20],    # Tech Writing → Frontend, Open Source
    22: [4, 30],    # Open Source → Career Dev, Inclusive Hiring
    23: [22, 7],    # FinTech → Open Source, Entrepreneurship
    24: [22, 7],    # HealthTech → Open Source, Entrepreneurship
    25: [22, 7],    # EdTech → Open Source, Entrepreneurship
    26: [22, 5],    # Game Industry → Open Source, UI/UX
    27: [2, 17],    # Embedded Sys → ML, MLOps
    28: [29, 3],    # AI Gov → Sales Eng, Marketing
    29: [3, 4],     # Customer Success → Marketing, Career Dev
    30: [28, 4],    # Mentoring First-Gen → AI Gov, Career Dev
}


MENTEE_NEIGHBOURS = {
    0: [4, 17],      # Frontend Dev → Bootcamp Grad, Cloud Eng
    1: [4, 16],      # Backend Dev → Bootcamp Grad, Data Eng
    2: [15, 23],     # ML → Data Eng, Computer Vision
    3: [21, 24],     # Digital Marketing → Tech Blogging, FinTech
    4: [9, 19],      # Career Switch → Product Mgmt, Public Speaking
    5: [0, 28],      # UI/UX → Frontend, Digital Product Thinking
    6: [0, 17],      # iOS → Frontend, Mobile App
    7: [10, 14],     # Entrepreneurship → Cybersec, QA
    8: [4, 19],      # Research → Career Switch, Public Speaking
    9: [4, 19],      # Product Mgmt → Career Switch, Public Speaking
    10: [1, 13],     # Cybersec → Backend, DevOps
    11: [5, 25],     # Game Dev → UI/UX, Game Design
    12: [1, 16],     # Blockchain → Backend, Data Eng
    13: [1, 16],     # DevOps → Backend, Cloud Eng
    14: [10, 1],     # QA → Cybersec, Backend
    15: [2, 16],     # Data Eng → ML, Cloud Eng
    16: [13, 15],    # Cloud Eng → DevOps, Data Eng
    17: [0, 6],      # Mobile App → Frontend, iOS
    18: [22, 30],    # Algorithms → ML Ops, Bootcamp Grad
    19: [20, 4],     # Public Speaking → Time Mgmt, Career Switch
    20: [19, 4],     # Time Mgmt → Public Speaking, Career Switch
    21: [3, 19],     # English Writing → Marketing, Public Speaking
    22: [2, 16],     # ML Ops → ML, Cloud Eng
    23: [2, 22],     # Computer Vision → ML, MLOps
    24: [3, 25],     # Full Stack → Marketing, Mobile App
    25: [11, 26],    # FinTech → Game Dev, Game Design
    26: [11, 5],     # Game Design → Game Dev, UI/UX
    27: [2, 22],     # AI Ethics → ML, MLOps
    28: [9, 5],      # Digital Product Thinking → Product Mgmt, UI/UX
    29: [8, 19],     # Study Abroad → Research, Public Speaking
    30: [4, 0],      # Bootcamp Grad → Career Switch, Frontend
}


def flat_skills(cluster_sets: list[list[str]]) -> list[tuple[int, str]]:
    """Flatten (cluster_index, skill_label) for uniform 1st-interest pick."""
    return [(i, s) for i, cluster in enumerate(cluster_sets) for s in cluster]


def pick_related_interests(role: str, primary_cluster: int,
                           primary_skill: str,
                           rng_2nd: random.Random,
                           rng_3rd: random.Random) -> list[str]:
    """80% chance of 2nd interest (from same cluster); 20% chance of 3rd
    (from a neighbour cluster). 2nd is picked from same cluster's other 2
    skills; 3rd is picked from a neighbour cluster's skills."""
    cluster_sets = MENTOR_INTEREST_SETS if role == "MENTOR" else MENTEE_INTEREST_SETS
    neighbours = MENTOR_NEIGHBOURS if role == "MENTOR" else MENTEE_NEIGHBOURS
    out: list[str] = [primary_skill]

    if rng_2nd.random() < 0.80:
        same_cluster = [s for s in cluster_sets[primary_cluster] if s != primary_skill]
        if same_cluster:
            out.append(rng_2nd.choice(same_cluster))

    if rng_3rd.random() < 0.20:
        nbrs = neighbours.get(primary_cluster, [])
        if nbrs:
            nbr_cluster_idx = rng_3rd.choice(nbrs)
            if 0 <= nbr_cluster_idx < len(cluster_sets):
                pool = [s for s in cluster_sets[nbr_cluster_idx] if s not in out]
                if pool:
                    out.append(rng_3rd.choice(pool))

    return out


def cluster_to_field(cluster_index: int, role: str) -> str:
    """Map cluster index → a 'field' label suitable for Mentor.field."""
    cluster_sets = MENTOR_INTEREST_SETS if role == "MENTOR" else MENTEE_INTEREST_SETS
    return cluster_sets[cluster_index][0]


# ── generator ─────────────────────────────────────────────────────────────

def gen_email(first_name: str, last_name: str, roster_id: int) -> str:
    return f"{first_name.lower()}.{last_name.lower()}.{roster_id}@seed.test"


def gen_user(roster_id: int, role: str, *,
             rng_names: random.Random,
             rng_loc: random.Random,
             rng_interest_primary: random.Random,
             rng_interest_2nd: random.Random,
             rng_interest_3rd: random.Random,
             rng_dem: random.Random) -> dict:
    gender = "male" if rng_names.random() < 0.5 else "female"
    pool = FIRST_NAMES_MALE if gender == "male" else FIRST_NAMES_FEMALE
    first_name = rng_names.choice(pool)
    last_name = rng_names.choice(LAST_NAMES)
    city = rng_loc.choice(LOCATIONS)

    cluster_sets = MENTOR_INTEREST_SETS if role == "MENTOR" else MENTEE_INTEREST_SETS
    flat = flat_skills(cluster_sets)
    cluster_idx, primary_skill = rng_interest_primary.choice(flat)
    interests = pick_related_interests(
        role, cluster_idx, primary_skill, rng_interest_2nd, rng_interest_3rd,
    )

    has_photo = rng_dem.random() < R.PROB_PROFILE_PHOTO

    user = {
        "id": roster_id,
        "gender": gender,
        "role": role,
        "first_name": first_name,
        "last_name": last_name,
        "email": gen_email(first_name, last_name, roster_id),
        "password": "Seed1234!",
        "city": city,
        "has_photo": has_photo,
        "interests": [{"label": skill, "uri": None} for skill in interests],
    }

    if role == "MENTOR":
        user["field"] = cluster_to_field(cluster_idx, "MENTOR")
        user["expertise"] = primary_skill
        user["affiliation"] = ""
        user["max_capacity"] = rng_dem.randint(1, 4)
        user["mentoring_goals"] = ""
        user["mentorship_duration"] = rng_dem.choice([1, 3, 6])
        # mentor preferred skills: pull 1-2 from the cluster (same as interests)
        user["preferred_mentee_skills"] = [
            {"label": s, "uri": None} for s in interests[:2]
        ]
        user["availability_days"] = R.availability_days(rng_dem)
        # Mentor post count is bell-curve rolled
        user["post_count"] = R.post_count(rng_dem)
    elif role == "MENTEE":
        user["major"] = ""
        user["career_interest"] = primary_skill
        user["affiliation"] = ""
        user["goals"] = ""
        user["background_info"] = ""
        user["skills"] = [{"label": s, "uri": None} for s in interests]
        user["post_count"] = R.post_count(rng_dem)
    else:
        # ADMIN
        user["post_count"] = 0

    return user


def main() -> None:
    rng_names = random.Random(R.MASTER_SEED + 200)
    rng_loc = random.Random(R.MASTER_SEED + 201)
    rng_p = random.Random(R.MASTER_SEED + 202)
    rng_2 = random.Random(R.MASTER_SEED + 203)
    rng_3 = random.Random(R.MASTER_SEED + 204)
    rng_dem = random.Random(R.MASTER_SEED + 205)

    roster: list[dict] = []

    # Admin (id 1)
    roster.append(gen_user(
        1, "ADMIN",
        rng_names=rng_names, rng_loc=rng_loc, rng_interest_primary=rng_p,
        rng_interest_2nd=rng_2, rng_interest_3rd=rng_3, rng_dem=rng_dem,
    ))
    # Mentors: ids 2..(NUM_MENTORS+1)
    for rid in range(2, 2 + NUM_MENTORS):
        roster.append(gen_user(
            rid, "MENTOR",
            rng_names=rng_names, rng_loc=rng_loc, rng_interest_primary=rng_p,
            rng_interest_2nd=rng_2, rng_interest_3rd=rng_3, rng_dem=rng_dem,
        ))
    # Mentees: ids (NUM_MENTORS+2)..(NUM_MENTORS+NUM_MENTEES+1)
    mentee_start = 2 + NUM_MENTORS
    for rid in range(mentee_start, mentee_start + NUM_MENTEES):
        roster.append(gen_user(
            rid, "MENTEE",
            rng_names=rng_names, rng_loc=rng_loc, rng_interest_primary=rng_p,
            rng_interest_2nd=rng_2, rng_interest_3rd=rng_3, rng_dem=rng_dem,
        ))

    OUTPUT_PATH.parent.mkdir(parents=True, exist_ok=True)
    import yaml
    OUTPUT_PATH.write_text(
        yaml.safe_dump(roster, sort_keys=False, allow_unicode=True, width=120),
        encoding="utf-8",
    )

    n_mentor = sum(1 for u in roster if u["role"] == "MENTOR")
    n_mentee = sum(1 for u in roster if u["role"] == "MENTEE")
    n_admin = sum(1 for u in roster if u["role"] == "ADMIN")
    print(f"Wrote {len(roster)} users to {OUTPUT_PATH}")
    print(f"  {n_admin} admin, {n_mentor} mentors, {n_mentee} mentees")
    photos = sum(1 for u in roster if u.get("has_photo"))
    print(f"  {photos} users marked has_photo (≈ {photos/len(roster)*100:.0f}%)")
    post_total = sum(u.get("post_count", 0) for u in roster)
    print(f"  total post_count rolled: {post_total}")


if __name__ == "__main__":
    main()
