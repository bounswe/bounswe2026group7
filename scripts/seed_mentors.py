#!/usr/bin/env python3
"""Seed 30 diverse mentor profiles into the backend."""

import json
import re
import time
import urllib.request
import urllib.error

BASE = "http://localhost:8080/api"
MAILHOG = "http://localhost:8025/api/v2"

MENTORS = [
    {
        "firstName": "Ahmet", "lastName": "Yılmaz", "email": "mentor_ahmet@seed.test", "password": "Seed1234!",
        "profile": {
            "bio": "10+ years building distributed backend systems at scale. Love helping devs level up.",
            "field": "Computer Science", "expertise": "Backend Development",
            "affiliation": "Trendyol", "interests": ["Microservices", "Java", "Performance"],
            "maxMenteeCapacity": 3, "preferredMenteeSkills": ["Java", "Spring Boot"],
            "preferredMenteeMajor": "Computer Engineering", "mentoringGoals": "Help juniors build production-ready backend skills.",
            "mentorshipDuration": 3,
        }
    },
    {
        "firstName": "Selin", "lastName": "Kaya", "email": "mentor_selin@seed.test", "password": "Seed1234!",
        "profile": {
            "bio": "Frontend lead with deep expertise in React and design systems.",
            "field": "Software Engineering", "expertise": "Frontend Development",
            "affiliation": "Getir", "interests": ["React", "TypeScript", "UX"],
            "maxMenteeCapacity": 2, "preferredMenteeSkills": ["JavaScript", "React"],
            "preferredMenteeMajor": "Computer Science", "mentoringGoals": "Guide mentees to write clean, accessible UI code.",
            "mentorshipDuration": 3,
        }
    },
    {
        "firstName": "Burak", "lastName": "Arslan", "email": "mentor_burak@seed.test", "password": "Seed1234!",
        "profile": {
            "bio": "Senior iOS developer at Apple, 8 years shipping consumer apps.",
            "field": "Mobile Development", "expertise": "iOS / Swift",
            "affiliation": "Apple", "interests": ["Swift", "SwiftUI", "Mobile"],
            "maxMenteeCapacity": 2, "preferredMenteeSkills": ["Swift", "Xcode"],
            "preferredMenteeMajor": "Computer Science", "mentoringGoals": "Help mentees publish their first App Store app.",
            "mentorshipDuration": 6,
        }
    },
    {
        "firstName": "Zeynep", "lastName": "Demir", "email": "mentor_zeynep@seed.test", "password": "Seed1234!",
        "profile": {
            "bio": "Android engineer passionate about Kotlin coroutines and clean architecture.",
            "field": "Mobile Development", "expertise": "Android / Kotlin",
            "affiliation": "Hepsiburada", "interests": ["Kotlin", "Android", "Clean Architecture"],
            "maxMenteeCapacity": 3, "preferredMenteeSkills": ["Kotlin", "Java"],
            "preferredMenteeMajor": "Computer Engineering", "mentoringGoals": "Build strong Android fundamentals and architecture skills.",
            "mentorshipDuration": 3,
        }
    },
    {
        "firstName": "Emre", "lastName": "Çelik", "email": "mentor_emre@seed.test", "password": "Seed1234!",
        "profile": {
            "bio": "ML engineer at Google with focus on recommendation systems.",
            "field": "Artificial Intelligence", "expertise": "Machine Learning",
            "affiliation": "Google", "interests": ["Python", "AI/ML", "TensorFlow"],
            "maxMenteeCapacity": 2, "preferredMenteeSkills": ["Python", "NumPy"],
            "preferredMenteeMajor": "Data Science", "mentoringGoals": "Bridge the gap between ML theory and production systems.",
            "mentorshipDuration": 6,
        }
    },
    {
        "firstName": "Ayşe", "lastName": "Şahin", "email": "mentor_ayse@seed.test", "password": "Seed1234!",
        "profile": {
            "bio": "Data scientist with expertise in statistical modelling and SQL optimization.",
            "field": "Data Science", "expertise": "Data Analysis",
            "affiliation": "Insider", "interests": ["Python", "SQL", "Data"],
            "maxMenteeCapacity": 3, "preferredMenteeSkills": ["Python", "SQL", "Pandas"],
            "preferredMenteeMajor": "Statistics", "mentoringGoals": "Teach practical data analysis and storytelling with data.",
            "mentorshipDuration": 3,
        }
    },
    {
        "firstName": "Kemal", "lastName": "Yıldız", "email": "mentor_kemal@seed.test", "password": "Seed1234!",
        "profile": {
            "bio": "DevOps architect with 7 years of Kubernetes and CI/CD experience.",
            "field": "DevOps", "expertise": "Kubernetes / Docker",
            "affiliation": "N11", "interests": ["Kubernetes", "Docker", "DevOps"],
            "maxMenteeCapacity": 2, "preferredMenteeSkills": ["Linux", "Docker"],
            "preferredMenteeMajor": "Computer Engineering", "mentoringGoals": "Help devs become comfortable with infra and automation.",
            "mentorshipDuration": 3,
        }
    },
    {
        "firstName": "Fatma", "lastName": "Öztürk", "email": "mentor_fatma@seed.test", "password": "Seed1234!",
        "profile": {
            "bio": "Cloud solutions architect at AWS, certified in 5 specialisations.",
            "field": "Cloud Computing", "expertise": "AWS / Terraform",
            "affiliation": "Amazon", "interests": ["AWS", "Cloud", "Terraform"],
            "maxMenteeCapacity": 2, "preferredMenteeSkills": ["AWS", "Python"],
            "preferredMenteeMajor": "Computer Science", "mentoringGoals": "Guide architects to design scalable cloud infrastructure.",
            "mentorshipDuration": 6,
        }
    },
    {
        "firstName": "Ozan", "lastName": "Kurt", "email": "mentor_ozan@seed.test", "password": "Seed1234!",
        "profile": {
            "bio": "Cybersecurity specialist — pentesting, threat modelling, and secure SDLC.",
            "field": "Cybersecurity", "expertise": "Penetration Testing",
            "affiliation": "TUBITAK", "interests": ["Security", "Linux", "Networking"],
            "maxMenteeCapacity": 2, "preferredMenteeSkills": ["Linux", "Networking"],
            "preferredMenteeMajor": "Cybersecurity", "mentoringGoals": "Help beginners break into security responsibly.",
            "mentorshipDuration": 6,
        }
    },
    {
        "firstName": "Deniz", "lastName": "Aydın", "email": "mentor_deniz@seed.test", "password": "Seed1234!",
        "profile": {
            "bio": "Blockchain developer building DeFi protocols on Ethereum since 2017.",
            "field": "Blockchain", "expertise": "Solidity / Web3",
            "affiliation": "Independent", "interests": ["Blockchain", "Solidity", "Web3"],
            "maxMenteeCapacity": 2, "preferredMenteeSkills": ["JavaScript", "Solidity"],
            "preferredMenteeMajor": "Computer Science", "mentoringGoals": "Onboard developers into the Web3 ecosystem.",
            "mentorshipDuration": 3,
        }
    },
    {
        "firstName": "Tolga", "lastName": "Eren", "email": "mentor_tolga@seed.test", "password": "Seed1234!",
        "profile": {
            "bio": "Game developer at Zynga with expertise in Unity and real-time multiplayer.",
            "field": "Game Development", "expertise": "Unity / C#",
            "affiliation": "Zynga", "interests": ["Unity", "C#", "Game Dev"],
            "maxMenteeCapacity": 3, "preferredMenteeSkills": ["C#", "Mathematics"],
            "preferredMenteeMajor": "Computer Science", "mentoringGoals": "Help aspiring game devs ship their first game.",
            "mentorshipDuration": 3,
        }
    },
    {
        "firstName": "Gizem", "lastName": "Koç", "email": "mentor_gizem@seed.test", "password": "Seed1234!",
        "profile": {
            "bio": "Embedded systems engineer working on IoT firmware for industrial sensors.",
            "field": "Embedded Systems", "expertise": "C / IoT",
            "affiliation": "Arçelik", "interests": ["C", "IoT", "Embedded"],
            "maxMenteeCapacity": 2, "preferredMenteeSkills": ["C", "Electronics"],
            "preferredMenteeMajor": "Electrical Engineering", "mentoringGoals": "Help bridge the gap between hardware and software.",
            "mentorshipDuration": 6,
        }
    },
    {
        "firstName": "Serkan", "lastName": "Polat", "email": "mentor_serkan@seed.test", "password": "Seed1234!",
        "profile": {
            "bio": "Data engineer building petabyte-scale pipelines with Spark and Kafka.",
            "field": "Data Engineering", "expertise": "Spark / Kafka",
            "affiliation": "Yemeksepeti", "interests": ["Spark", "Kafka", "Data"],
            "maxMenteeCapacity": 2, "preferredMenteeSkills": ["Python", "SQL"],
            "preferredMenteeMajor": "Computer Science", "mentoringGoals": "Teach scalable data pipeline design and best practices.",
            "mentorshipDuration": 3,
        }
    },
    {
        "firstName": "Merve", "lastName": "Doğan", "email": "mentor_merve@seed.test", "password": "Seed1234!",
        "profile": {
            "bio": "NLP researcher at Hugging Face working on multilingual transformers.",
            "field": "Natural Language Processing", "expertise": "Transformers / Python",
            "affiliation": "Hugging Face", "interests": ["NLP", "Python", "AI/ML"],
            "maxMenteeCapacity": 2, "preferredMenteeSkills": ["Python", "PyTorch"],
            "preferredMenteeMajor": "Data Science", "mentoringGoals": "Guide NLP beginners to fine-tune and deploy language models.",
            "mentorshipDuration": 6,
        }
    },
    {
        "firstName": "Barış", "lastName": "Güneş", "email": "mentor_baris@seed.test", "password": "Seed1234!",
        "profile": {
            "bio": "Computer vision engineer with 6 years building perception systems for autonomous vehicles.",
            "field": "Computer Vision", "expertise": "OpenCV / PyTorch",
            "affiliation": "Ford Otosan", "interests": ["Computer Vision", "Python", "AI/ML"],
            "maxMenteeCapacity": 2, "preferredMenteeSkills": ["Python", "Linear Algebra"],
            "preferredMenteeMajor": "Computer Engineering", "mentoringGoals": "Help mentees understand and implement vision algorithms.",
            "mentorshipDuration": 6,
        }
    },
    {
        "firstName": "Pınar", "lastName": "Acar", "email": "mentor_pinar@seed.test", "password": "Seed1234!",
        "profile": {
            "bio": "Full-stack engineer specialising in Node.js microservices and React frontends.",
            "field": "Software Engineering", "expertise": "Full Stack / Node.js",
            "affiliation": "Shopify", "interests": ["Node.js", "React", "TypeScript"],
            "maxMenteeCapacity": 3, "preferredMenteeSkills": ["JavaScript", "Node.js"],
            "preferredMenteeMajor": "Computer Science", "mentoringGoals": "Grow well-rounded full-stack engineers.",
            "mentorshipDuration": 3,
        }
    },
    {
        "firstName": "Mert", "lastName": "Kaplan", "email": "mentor_mert@seed.test", "password": "Seed1234!",
        "profile": {
            "bio": "Go engineer building high-throughput financial services at a fintech unicorn.",
            "field": "Software Engineering", "expertise": "Go / Microservices",
            "affiliation": "Papara", "interests": ["Go", "Microservices", "Backend"],
            "maxMenteeCapacity": 2, "preferredMenteeSkills": ["Go", "Concurrency"],
            "preferredMenteeMajor": "Computer Engineering", "mentoringGoals": "Introduce developers to systems programming with Go.",
            "mentorshipDuration": 3,
        }
    },
    {
        "firstName": "Hande", "lastName": "Uçar", "email": "mentor_hande@seed.test", "password": "Seed1234!",
        "profile": {
            "bio": "Database architect with deep knowledge of PostgreSQL tuning and schema design.",
            "field": "Database Engineering", "expertise": "PostgreSQL / MongoDB",
            "affiliation": "Boğaziçi University", "interests": ["SQL", "PostgreSQL", "Database"],
            "maxMenteeCapacity": 3, "preferredMenteeSkills": ["SQL", "Data Modelling"],
            "preferredMenteeMajor": "Computer Science", "mentoringGoals": "Teach solid database design and query optimisation.",
            "mentorshipDuration": 3,
        }
    },
    {
        "firstName": "Cem", "lastName": "Erdoğan", "email": "mentor_cem@seed.test", "password": "Seed1234!",
        "profile": {
            "bio": "Software architect with 15 years applying SOLID and domain-driven design in enterprise systems.",
            "field": "Software Architecture", "expertise": "Design Patterns / DDD",
            "affiliation": "Turkcell", "interests": ["Architecture", "Java", "DDD"],
            "maxMenteeCapacity": 2, "preferredMenteeSkills": ["OOP", "Java"],
            "preferredMenteeMajor": "Computer Engineering", "mentoringGoals": "Help devs think in systems and write maintainable code.",
            "mentorshipDuration": 6,
        }
    },
    {
        "firstName": "Elif", "lastName": "Soylu", "email": "mentor_elif@seed.test", "password": "Seed1234!",
        "profile": {
            "bio": "Product manager at Atlassian with a background in software engineering.",
            "field": "Product Management", "expertise": "Agile / Scrum",
            "affiliation": "Atlassian", "interests": ["Product", "Agile", "Leadership"],
            "maxMenteeCapacity": 3, "preferredMenteeSkills": ["Communication", "Analytics"],
            "preferredMenteeMajor": "Business", "mentoringGoals": "Help engineers transition into product management.",
            "mentorshipDuration": 3,
        }
    },
    {
        "firstName": "Ömer", "lastName": "Tuna", "email": "mentor_omer@seed.test", "password": "Seed1234!",
        "profile": {
            "bio": "Senior UX designer with expertise in user research and Figma-based design systems.",
            "field": "UX/UI Design", "expertise": "Figma / User Research",
            "affiliation": "Booking.com", "interests": ["UX", "Design", "Figma"],
            "maxMenteeCapacity": 2, "preferredMenteeSkills": ["Figma", "Design Thinking"],
            "preferredMenteeMajor": "Design", "mentoringGoals": "Teach user-centred design and portfolio building.",
            "mentorshipDuration": 3,
        }
    },
    {
        "firstName": "Canan", "lastName": "Bulut", "email": "mentor_canan@seed.test", "password": "Seed1234!",
        "profile": {
            "bio": "SRE at Spotify — observability, SLOs, and incident response are my bread and butter.",
            "field": "Site Reliability Engineering", "expertise": "SRE / Observability",
            "affiliation": "Spotify", "interests": ["SRE", "DevOps", "Monitoring"],
            "maxMenteeCapacity": 2, "preferredMenteeSkills": ["Linux", "Python"],
            "preferredMenteeMajor": "Computer Engineering", "mentoringGoals": "Grow engineers who care about reliability and on-call culture.",
            "mentorshipDuration": 3,
        }
    },
    {
        "firstName": "İbrahim", "lastName": "Yücel", "email": "mentor_ibrahim@seed.test", "password": "Seed1234!",
        "profile": {
            "bio": "Systems programmer writing Rust for safety-critical aerospace software.",
            "field": "Systems Programming", "expertise": "Rust / C++",
            "affiliation": "Roketsan", "interests": ["Rust", "C++", "Systems"],
            "maxMenteeCapacity": 2, "preferredMenteeSkills": ["C", "Memory Management"],
            "preferredMenteeMajor": "Computer Engineering", "mentoringGoals": "Introduce devs to low-level programming and Rust ownership.",
            "mentorshipDuration": 6,
        }
    },
    {
        "firstName": "Nazlı", "lastName": "Arslan", "email": "mentor_nazli@seed.test", "password": "Seed1234!",
        "profile": {
            "bio": "API design expert who has built and documented REST and GraphQL APIs for 200+ engineers.",
            "field": "Software Engineering", "expertise": "API Design / GraphQL",
            "affiliation": "İyzico", "interests": ["REST", "GraphQL", "API"],
            "maxMenteeCapacity": 3, "preferredMenteeSkills": ["HTTP", "JSON"],
            "preferredMenteeMajor": "Computer Science", "mentoringGoals": "Teach developers to design developer-friendly APIs.",
            "mentorshipDuration": 3,
        }
    },
    {
        "firstName": "Alp", "lastName": "Kılıç", "email": "mentor_alp@seed.test", "password": "Seed1234!",
        "profile": {
            "bio": "Open source maintainer with top 1% GitHub contributions, core contributor to several popular libraries.",
            "field": "Open Source", "expertise": "OSS Contribution / GitHub",
            "affiliation": "Independent", "interests": ["Open Source", "Git", "Community"],
            "maxMenteeCapacity": 4, "preferredMenteeSkills": ["Git", "Any Language"],
            "preferredMenteeMajor": "Computer Science", "mentoringGoals": "Help newcomers make their first open source contributions.",
            "mentorshipDuration": 1,
        }
    },
    {
        "firstName": "Sibel", "lastName": "Çetin", "email": "mentor_sibel@seed.test", "password": "Seed1234!",
        "profile": {
            "bio": "Serial entrepreneur, 3 successful exits. Currently building AI startup number 4.",
            "field": "Entrepreneurship", "expertise": "Startups / Product",
            "affiliation": "YCombinator Alumna", "interests": ["Startup", "Product", "AI/ML"],
            "maxMenteeCapacity": 2, "preferredMenteeSkills": ["Problem Solving", "Communication"],
            "preferredMenteeMajor": "Business", "mentoringGoals": "Help technical founders validate ideas and build MVPs fast.",
            "mentorshipDuration": 6,
        }
    },
    {
        "firstName": "Yusuf", "lastName": "Doğru", "email": "mentor_yusuf@seed.test", "password": "Seed1234!",
        "profile": {
            "bio": "PhD in Computer Science, specialising in distributed systems and consensus algorithms.",
            "field": "Academic Research", "expertise": "Distributed Systems / Research",
            "affiliation": "METU", "interests": ["Research", "Distributed Systems", "Academia"],
            "maxMenteeCapacity": 2, "preferredMenteeSkills": ["Mathematics", "Algorithms"],
            "preferredMenteeMajor": "Computer Science", "mentoringGoals": "Guide students through research methodology and academic writing.",
            "mentorshipDuration": 6,
        }
    },
    {
        "firstName": "Aslı", "lastName": "Karahan", "email": "mentor_asli@seed.test", "password": "Seed1234!",
        "profile": {
            "bio": "Ruby on Rails consultant with 9 years delivering projects for startups across Europe.",
            "field": "Web Development", "expertise": "Ruby on Rails",
            "affiliation": "Independent", "interests": ["Ruby", "Rails", "Backend"],
            "maxMenteeCapacity": 3, "preferredMenteeSkills": ["Ruby", "HTML/CSS"],
            "preferredMenteeMajor": "Computer Science", "mentoringGoals": "Show mentees how to ship fast with Rails conventions.",
            "mentorshipDuration": 3,
        }
    },
    {
        "firstName": "Tarık", "lastName": "Özer", "email": "mentor_tarik@seed.test", "password": "Seed1234!",
        "profile": {
            "bio": "Quantum computing researcher at IBM Quantum, working on error correction codes.",
            "field": "Quantum Computing", "expertise": "Qiskit / Quantum Algorithms",
            "affiliation": "IBM Research", "interests": ["Quantum", "Physics", "Mathematics"],
            "maxMenteeCapacity": 2, "preferredMenteeSkills": ["Linear Algebra", "Python"],
            "preferredMenteeMajor": "Physics", "mentoringGoals": "Introduce curious engineers to quantum computing fundamentals.",
            "mentorshipDuration": 6,
        }
    },
    {
        "firstName": "Lale", "lastName": "Işık", "email": "mentor_lale@seed.test", "password": "Seed1234!",
        "profile": {
            "bio": "Engineering manager at Microsoft leading a team of 12 backend engineers.",
            "field": "Engineering Management", "expertise": "Leadership / Team Building",
            "affiliation": "Microsoft", "interests": ["Leadership", "Backend", "Career"],
            "maxMenteeCapacity": 3, "preferredMenteeSkills": ["Communication", "Problem Solving"],
            "preferredMenteeMajor": "Computer Engineering", "mentoringGoals": "Help senior ICs navigate the path to engineering leadership.",
            "mentorshipDuration": 6,
        }
    },
]


def http_post(url, data, token=None):
    body = json.dumps(data).encode()
    headers = {"Content-Type": "application/json"}
    if token:
        headers["Authorization"] = f"Bearer {token}"
    req = urllib.request.Request(url, data=body, headers=headers, method="POST")
    try:
        with urllib.request.urlopen(req) as r:
            return r.status, json.loads(r.read())
    except urllib.error.HTTPError as e:
        return e.code, json.loads(e.read())


def http_get(url, token=None):
    headers = {}
    if token:
        headers["Authorization"] = f"Bearer {token}"
    req = urllib.request.Request(url, headers=headers)
    try:
        with urllib.request.urlopen(req) as r:
            return r.status, json.loads(r.read())
    except urllib.error.HTTPError as e:
        return e.code, json.loads(e.read())


def http_patch(url, data, token):
    body = json.dumps(data).encode()
    headers = {"Content-Type": "application/json", "Authorization": f"Bearer {token}"}
    req = urllib.request.Request(url, data=body, headers=headers, method="PATCH")
    try:
        with urllib.request.urlopen(req) as r:
            return r.status, json.loads(r.read())
    except urllib.error.HTTPError as e:
        return e.code, json.loads(e.read())


def get_verification_token(email):
    for _ in range(5):
        _, data = http_get(f"{MAILHOG}/messages?limit=50")
        for msg in data.get("items", []):
            to = msg["Content"]["Headers"].get("To", [""])[0]
            if email.lower() in to.lower():
                body = msg["Content"]["Body"]
                match = re.search(r"token=([A-Za-z0-9\-]+)", body)
                if match:
                    return match.group(1)
        time.sleep(0.5)
    return None


def process_mentor(m, index):
    email = m["email"]
    print(f"[{index+1:2}/30] {m['firstName']} {m['lastName']} ({email})", end=" ... ", flush=True)

    # Register
    status, resp = http_post(f"{BASE}/auth/register", {
        "firstName": m["firstName"], "lastName": m["lastName"],
        "email": email, "password": m["password"], "isMentor": True
    })
    if status not in (200, 201):
        if "already" in str(resp).lower() or status == 409:
            print("already exists, skipping registration")
        else:
            print(f"REGISTER FAILED ({status}): {resp}")
            return False

    # Verify email
    token = get_verification_token(email)
    if token:
        http_get(f"{BASE}/auth/verify-email?token={token}")
    else:
        print("no verification email found, trying login anyway")

    # Login
    status, resp = http_post(f"{BASE}/auth/login", {"email": email, "password": m["password"]})
    if status != 200 or "sessionToken" not in resp:
        print(f"LOGIN FAILED ({status}): {resp}")
        return False
    jwt = resp["sessionToken"]

    # Update profile
    status, resp = http_patch(f"{BASE}/users/me", m["profile"], jwt)
    if status != 200:
        print(f"PROFILE UPDATE FAILED ({status}): {resp}")
        return False

    print(f"OK (id={resp.get('id')})")
    return True


ok = 0
for i, mentor in enumerate(MENTORS):
    if process_mentor(mentor, i):
        ok += 1

print(f"\nDone: {ok}/{len(MENTORS)} mentors seeded.")
