#!/usr/bin/env python3
"""Fix remaining 8 unverified mentors by properly QP-decoding mailhog bodies."""

import json, re, quopri, urllib.request, urllib.error

BASE = "http://localhost:8080/api"
MAILHOG = "http://localhost:8025/api/v2"

FAILED = [
    {"firstName": "Ayşe",    "lastName": "Şahin",   "email": "mentor_ayse@seed.test",    "password": "Seed1234!",
     "profile": {"bio": "Data scientist with expertise in statistical modelling and SQL optimization.", "field": "Data Science", "expertise": "Data Analysis", "affiliation": "Insider", "interests": ["Python", "SQL", "Data"], "maxMenteeCapacity": 3, "preferredMenteeSkills": ["Python", "SQL", "Pandas"], "preferredMenteeMajor": "Statistics", "mentoringGoals": "Teach practical data analysis and storytelling with data.", "mentorshipDuration": 3}},
    {"firstName": "Barış",   "lastName": "Güneş",   "email": "mentor_baris@seed.test",   "password": "Seed1234!",
     "profile": {"bio": "Computer vision engineer with 6 years building perception systems for autonomous vehicles.", "field": "Computer Vision", "expertise": "OpenCV / PyTorch", "affiliation": "Ford Otosan", "interests": ["Computer Vision", "Python", "AI/ML"], "maxMenteeCapacity": 2, "preferredMenteeSkills": ["Python", "Linear Algebra"], "preferredMenteeMajor": "Computer Engineering", "mentoringGoals": "Help mentees understand and implement vision algorithms.", "mentorshipDuration": 6}},
    {"firstName": "Pınar",   "lastName": "Acar",    "email": "mentor_pinar@seed.test",   "password": "Seed1234!",
     "profile": {"bio": "Full-stack engineer specialising in Node.js microservices and React frontends.", "field": "Software Engineering", "expertise": "Full Stack / Node.js", "affiliation": "Shopify", "interests": ["Node.js", "React", "TypeScript"], "maxMenteeCapacity": 3, "preferredMenteeSkills": ["JavaScript", "Node.js"], "preferredMenteeMajor": "Computer Science", "mentoringGoals": "Grow well-rounded full-stack engineers.", "mentorshipDuration": 3}},
    {"firstName": "Ömer",    "lastName": "Tuna",    "email": "mentor_omer@seed.test",    "password": "Seed1234!",
     "profile": {"bio": "Senior UX designer with expertise in user research and Figma-based design systems.", "field": "UX/UI Design", "expertise": "Figma / User Research", "affiliation": "Booking.com", "interests": ["UX", "Design", "Figma"], "maxMenteeCapacity": 2, "preferredMenteeSkills": ["Figma", "Design Thinking"], "preferredMenteeMajor": "Design", "mentoringGoals": "Teach user-centred design and portfolio building.", "mentorshipDuration": 3}},
    {"firstName": "İbrahim", "lastName": "Yücel",   "email": "mentor_ibrahim@seed.test", "password": "Seed1234!",
     "profile": {"bio": "Systems programmer writing Rust for safety-critical aerospace software.", "field": "Systems Programming", "expertise": "Rust / C++", "affiliation": "Roketsan", "interests": ["Rust", "C++", "Systems"], "maxMenteeCapacity": 2, "preferredMenteeSkills": ["C", "Memory Management"], "preferredMenteeMajor": "Computer Engineering", "mentoringGoals": "Introduce devs to low-level programming and Rust ownership.", "mentorshipDuration": 6}},
    {"firstName": "Nazlı",   "lastName": "Arslan",  "email": "mentor_nazli@seed.test",   "password": "Seed1234!",
     "profile": {"bio": "API design expert who has built and documented REST and GraphQL APIs for 200+ engineers.", "field": "Software Engineering", "expertise": "API Design / GraphQL", "affiliation": "İyzico", "interests": ["REST", "GraphQL", "API"], "maxMenteeCapacity": 3, "preferredMenteeSkills": ["HTTP", "JSON"], "preferredMenteeMajor": "Computer Science", "mentoringGoals": "Teach developers to design developer-friendly APIs.", "mentorshipDuration": 3}},
    {"firstName": "Aslı",    "lastName": "Karahan", "email": "mentor_asli@seed.test",    "password": "Seed1234!",
     "profile": {"bio": "Ruby on Rails consultant with 9 years delivering projects for startups across Europe.", "field": "Web Development", "expertise": "Ruby on Rails", "affiliation": "Independent", "interests": ["Ruby", "Rails", "Backend"], "maxMenteeCapacity": 3, "preferredMenteeSkills": ["Ruby", "HTML/CSS"], "preferredMenteeMajor": "Computer Science", "mentoringGoals": "Show mentees how to ship fast with Rails conventions.", "mentorshipDuration": 3}},
    {"firstName": "Tarık",   "lastName": "Özer",    "email": "mentor_tarik@seed.test",   "password": "Seed1234!",
     "profile": {"bio": "Quantum computing researcher at IBM Quantum, working on error correction codes.", "field": "Quantum Computing", "expertise": "Qiskit / Quantum Algorithms", "affiliation": "IBM Research", "interests": ["Quantum", "Physics", "Mathematics"], "maxMenteeCapacity": 2, "preferredMenteeSkills": ["Linear Algebra", "Python"], "preferredMenteeMajor": "Physics", "mentoringGoals": "Introduce curious engineers to quantum computing fundamentals.", "mentorshipDuration": 6}},
]


def http_post(url, data, token=None):
    body = json.dumps(data).encode()
    headers = {"Content-Type": "application/json"}
    if token: headers["Authorization"] = f"Bearer {token}"
    req = urllib.request.Request(url, data=body, headers=headers, method="POST")
    try:
        with urllib.request.urlopen(req) as r: return r.status, json.loads(r.read())
    except urllib.error.HTTPError as e: return e.code, json.loads(e.read())

def http_get(url, token=None):
    headers = {}
    if token: headers["Authorization"] = f"Bearer {token}"
    req = urllib.request.Request(url, headers=headers)
    try:
        with urllib.request.urlopen(req) as r: return r.status, json.loads(r.read())
    except urllib.error.HTTPError as e: return e.code, json.loads(e.read())

def http_patch(url, data, token):
    body = json.dumps(data).encode()
    headers = {"Content-Type": "application/json", "Authorization": f"Bearer {token}"}
    req = urllib.request.Request(url, data=body, headers=headers, method="PATCH")
    try:
        with urllib.request.urlopen(req) as r: return r.status, json.loads(r.read())
    except urllib.error.HTTPError as e: return e.code, json.loads(e.read())


def get_token_for(email):
    _, data = http_get(f"{MAILHOG}/messages?limit=500")
    for msg in data.get("items", []):
        to = msg["Content"]["Headers"].get("To", [""])[0]
        if email.lower() not in to.lower():
            continue
        raw_body = msg["Content"]["Body"]
        # QP-decode to get clean text
        try:
            decoded = quopri.decodestring(raw_body.encode()).decode("utf-8", errors="replace")
        except Exception:
            decoded = raw_body
        match = re.search(r"token=([A-Za-z0-9\-]{30,})", decoded)
        if match:
            return match.group(1)
        # fallback: look in raw for token= patterns and strip soft line breaks
        cleaned = re.sub(r"=\r?\n", "", raw_body)
        match = re.search(r"token=([A-Za-z0-9\-]{30,})", cleaned)
        if match:
            return match.group(1)
    return None


ok = 0
for i, m in enumerate(FAILED):
    email = m["email"]
    print(f"[{i+1}/{len(FAILED)}] {m['firstName']}", end=" ... ", flush=True)

    # Resend verification
    http_post(f"{BASE}/auth/resend-verification", {"email": email})

    import time; time.sleep(1)

    token = get_token_for(email)
    if not token:
        print("NO TOKEN FOUND"); continue

    print(f"token={token[:8]}...", end=" ", flush=True)
    status, resp = http_get(f"{BASE}/auth/verify-email?token={token}")
    if status != 200:
        print(f"VERIFY FAILED ({status}): {resp}"); continue

    status, resp = http_post(f"{BASE}/auth/login", {"email": email, "password": m["password"]})
    if status != 200:
        print(f"LOGIN FAILED ({status}): {resp}"); continue
    jwt = resp["sessionToken"]

    status, resp = http_patch(f"{BASE}/users/me", m["profile"], jwt)
    if status != 200:
        print(f"PROFILE FAILED ({status}): {resp}"); continue

    print(f"OK (id={resp.get('id')})")
    ok += 1

print(f"\nDone: {ok}/{len(FAILED)} fixed.")
