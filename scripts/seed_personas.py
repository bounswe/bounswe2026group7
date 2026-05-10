#!/usr/bin/env python3
"""Seed realistic mentorship personas into the backend and export personas.json."""

from __future__ import annotations

import json
import re
import time
from datetime import datetime, timedelta
import urllib.error
import urllib.request
from pathlib import Path
from typing import Optional


BASE = "http://localhost:8080/api"
MAILHOG = "http://localhost:8025/api/v2"
SEED_PASSWORD = "Seed1234!"
OUTPUT_PATH = Path(__file__).with_name("personas.json")


PERSONAS = [
    {
        "id": 1,
        "first_name": "Elif",
        "last_name": "Yilmaz",
        "role": "MENTEE",
        "status": "VERIFIED",
        "location": "Istanbul",
        "capacity_current": 0,
        "capacity_max": 0,
        "interests": ["Data Science", "Machine Learning"],
        "bio": "I am transitioning into data science and looking for roadmap advice, project ideas, and a realistic study plan.",
        "is_banned": False,
    },
    {
        "id": 2,
        "first_name": "Dr. Ahmet",
        "last_name": "Bulut",
        "role": "MENTOR",
        "status": "VERIFIED",
        "location": "Besiktas, Istanbul",
        "capacity_current": 2,
        "capacity_max": 3,
        "interests": ["Data Science", "Academic Career"],
        "bio": "I support students who want to build a strong foundation in data science and pursue an academic career. I am actively mentoring Elif.",
        "is_banned": False,
    },
    {
        "id": 3,
        "first_name": "Mehmet",
        "last_name": "Yilmaz",
        "role": "MENTOR",
        "status": "VERIFIED",
        "location": "Sisli, Istanbul",
        "capacity_current": 3,
        "capacity_max": 3,
        "interests": ["Finance", "Startups"],
        "bio": "I mentor founders on finance basics, startup strategy, and how to communicate early-stage ideas clearly.",
        "is_banned": False,
    },
    {
        "id": 4,
        "first_name": "Zeynep",
        "last_name": "Arslan",
        "role": "MENTOR",
        "status": "VERIFIED",
        "location": "Kadikoy, Istanbul",
        "capacity_current": 0,
        "capacity_max": 2,
        "interests": ["iOS", "Swift"],
        "bio": "I mentor aspiring iOS developers on Swift fundamentals, architecture, testing, and career entry steps.",
        "is_banned": False,
    },
    {
        "id": 5,
        "first_name": "Eren",
        "last_name": "Kilic",
        "role": "MENTEE",
        "status": "VERIFIED",
        "location": "Ankara",
        "capacity_current": 0,
        "capacity_max": 0,
        "interests": ["Backend", "Interview Prep"],
        "bio": "I am preparing for internship interviews and want structured help with backend fundamentals and practice questions.",
        "is_banned": True,
    },
    {
        "id": 6,
        "first_name": "Ayse",
        "last_name": "Kaya",
        "role": "MENTEE",
        "status": "UNVERIFIED",
        "location": "Izmir",
        "capacity_current": 0,
        "capacity_max": 0,
        "interests": ["Frontend", "Career Switch"],
        "bio": "I am exploring a career switch into software and want guidance on where to focus first.",
        "is_banned": False,
    },
    {
        "id": 7,
        "first_name": "Mert",
        "last_name": "Aydin",
        "role": "MENTEE",
        "status": "VERIFIED",
        "location": "Kadikoy, Istanbul",
        "capacity_current": 0,
        "capacity_max": 0,
        "interests": ["Frontend", "React", "Portfolio Review"],
        "bio": "I am a final-year computer engineering student looking to improve my portfolio and get ready for my first job.",
        "is_banned": False,
    },
    {
        "id": 8,
        "first_name": "Selin",
        "last_name": "Ozturk",
        "role": "MENTEE",
        "status": "VERIFIED",
        "location": "Besiktas, Istanbul",
        "capacity_current": 0,
        "capacity_max": 0,
        "interests": ["Machine Learning", "Python", "Data Science"],
        "bio": "I am preparing for a master's program in artificial intelligence and need project ideas and a better study roadmap.",
        "is_banned": False,
    },
    {
        "id": 9,
        "first_name": "Bora",
        "last_name": "Cetin",
        "role": "MENTOR",
        "status": "VERIFIED",
        "location": "Izmir",
        "capacity_current": 1,
        "capacity_max": 4,
        "interests": ["Product Management", "Startups", "Entrepreneurship"],
        "bio": "I work on product strategy and early-stage startup growth. I help students clarify their goals and present ideas well.",
        "is_banned": False,
    },
    {
        "id": 10,
        "first_name": "Hale",
        "last_name": "Demir",
        "role": "MENTOR",
        "status": "VERIFIED",
        "location": "Ankara",
        "capacity_current": 2,
        "capacity_max": 5,
        "interests": ["Digital Marketing", "Brand Strategy", "Content Marketing"],
        "bio": "I have over ten years of experience in digital marketing and brand strategy. I support mentees with practical, measurable growth plans.",
        "is_banned": False,
    },
    {
        "id": 11,
        "first_name": "Kaan",
        "last_name": "Yildirim",
        "role": "MENTEE",
        "status": "VERIFIED",
        "location": "Sisli, Istanbul",
        "capacity_current": 0,
        "capacity_max": 0,
        "interests": ["Backend", "Node.js", "System Design"],
        "bio": "I want to improve my backend design skills and learn how to build more scalable APIs.",
        "is_banned": False,
    },
    {
        "id": 12,
        "first_name": "Derya",
        "last_name": "Karaca",
        "role": "MENTEE",
        "status": "VERIFIED",
        "location": "Bursa",
        "capacity_current": 0,
        "capacity_max": 0,
        "interests": ["UI/UX", "Frontend", "Design Systems"],
        "bio": "I am moving from graphic design into product design and want feedback on user experience and portfolio quality.",
        "is_banned": False,
    },
    {
        "id": 13,
        "first_name": "Emre",
        "last_name": "Aksoy",
        "role": "MENTOR",
        "status": "VERIFIED",
        "location": "Antalya",
        "capacity_current": 1,
        "capacity_max": 3,
        "interests": ["Backend", "Cloud", "DevOps"],
        "bio": "I work on backend architecture and cloud infrastructure and help junior developers build practical project habits.",
        "is_banned": False,
    },
    {
        "id": 14,
        "first_name": "Nil",
        "last_name": "Gunes",
        "role": "MENTEE",
        "status": "VERIFIED",
        "location": "Kadikoy, Istanbul",
        "capacity_current": 0,
        "capacity_max": 0,
        "interests": ["Interview Prep", "Career Switch", "Business Analysis"],
        "bio": "I am preparing to move into a business analyst role and want help with interview questions and a focused study plan.",
        "is_banned": False,
    },
    {
        "id": 15,
        "first_name": "Oguz",
        "last_name": "Sahin",
        "role": "MENTOR",
        "status": "VERIFIED",
        "location": "Bursa",
        "capacity_current": 4,
        "capacity_max": 6,
        "interests": ["Java", "Spring Boot", "Backend"],
        "bio": "I have years of experience in enterprise software development and I mentor students who want to build production-ready backend skills.",
        "is_banned": False,
    },
    {
        "id": 16,
        "first_name": "Ipek",
        "last_name": "Acar",
        "role": "MENTEE",
        "status": "UNVERIFIED",
        "location": "Izmir",
        "capacity_current": 0,
        "capacity_max": 0,
        "interests": ["Digital Marketing", "Content Marketing", "SEO"],
        "bio": "I am transitioning into marketing and want to learn how to build a practical content and SEO strategy.",
        "is_banned": False,
    },
    {
        "id": 17,
        "first_name": "Serdar",
        "last_name": "Koc",
        "role": "MENTOR",
        "status": "VERIFIED",
        "location": "Konya",
        "capacity_current": 2,
        "capacity_max": 4,
        "interests": ["Entrepreneurship", "Finance", "Pitch Deck"],
        "bio": "I mentor founders on startup finance, business models, and how to shape a clear pitch deck for early-stage investors.",
        "is_banned": False,
    },
    {
        "id": 18,
        "first_name": "Sude",
        "last_name": "Ergin",
        "role": "MENTEE",
        "status": "VERIFIED",
        "location": "Besiktas, Istanbul",
        "capacity_current": 0,
        "capacity_max": 0,
        "interests": ["Machine Learning", "Data Science", "Python"],
        "bio": "I have data analysis coursework but need more hands-on project experience and realistic examples from industry.",
        "is_banned": False,
    },
    {
        "id": 19,
        "first_name": "Fikret",
        "last_name": "Ersoy",
        "role": "MENTOR",
        "status": "VERIFIED",
        "location": "Ankara",
        "capacity_current": 1,
        "capacity_max": 2,
        "interests": ["Academic Career", "Research", "Machine Learning"],
        "bio": "I help students prepare for graduate study, research planning, and academic career decisions.",
        "is_banned": False,
    },
    {
        "id": 20,
        "first_name": "Gamze",
        "last_name": "Ates",
        "role": "MENTEE",
        "status": "VERIFIED",
        "location": "Antalya",
        "capacity_current": 0,
        "capacity_max": 0,
        "interests": ["Sales", "Communication Skills", "Career Switch"],
        "bio": "I am moving from tourism into technology and want to understand which roles fit my communication strengths best.",
        "is_banned": False,
    },
    {
        "id": 21,
        "first_name": "Tolga",
        "last_name": "Ekinci",
        "role": "MENTOR",
        "status": "VERIFIED",
        "location": "Sisli, Istanbul",
        "capacity_current": 0,
        "capacity_max": 3,
        "interests": ["Interview Prep", "LeetCode", "Backend"],
        "bio": "I mentor students on technical interview preparation, algorithm practice, and confident communication in hiring processes.",
        "is_banned": False,
    },
    {
        "id": 22,
        "first_name": "Merve",
        "last_name": "Ural",
        "role": "MENTEE",
        "status": "VERIFIED",
        "location": "Ankara",
        "capacity_current": 0,
        "capacity_max": 0,
        "interests": ["Frontend", "Vue", "UI/UX"],
        "bio": "I am learning frontend development and want to build a small but realistic product while improving my design sense.",
        "is_banned": False,
    },
    {
        "id": 23,
        "first_name": "Yusuf",
        "last_name": "Balci",
        "role": "MENTOR",
        "status": "VERIFIED",
        "location": "Kadikoy, Istanbul",
        "capacity_current": 3,
        "capacity_max": 5,
        "interests": ["Frontend", "React", "Design Systems"],
        "bio": "I work on frontend architecture and design systems and help younger developers focus on maintainable, readable code.",
        "is_banned": False,
    },
    {
        "id": 24,
        "first_name": "Aylin",
        "last_name": "Sonmez",
        "role": "MENTEE",
        "status": "UNVERIFIED",
        "location": "Bursa",
        "capacity_current": 0,
        "capacity_max": 0,
        "interests": ["Project Management", "Communication Skills", "Career Switch"],
        "bio": "I want to move into project coordination and would value guidance from someone with practical corporate experience.",
        "is_banned": False,
    },
    {
        "id": 25,
        "first_name": "Hakan",
        "last_name": "Polat",
        "role": "MENTOR",
        "status": "VERIFIED",
        "location": "Izmir",
        "capacity_current": 2,
        "capacity_max": 4,
        "interests": ["Career Development", "Interview Prep", "Leadership"],
        "bio": "I have worked in talent management and I support mentees with CV feedback, interview prep, and career planning.",
        "is_banned": False,
    },
    {
        "id": 26,
        "first_name": "Ece",
        "last_name": "Tan",
        "role": "MENTEE",
        "status": "VERIFIED",
        "location": "Sisli, Istanbul",
        "capacity_current": 0,
        "capacity_max": 0,
        "interests": ["Data Science", "Statistics", "Python"],
        "bio": "I recently graduated from statistics and want help choosing the right tools and projects for data science.",
        "is_banned": False,
    },
    {
        "id": 27,
        "first_name": "Baris",
        "last_name": "Korkmaz",
        "role": "MENTOR",
        "status": "VERIFIED",
        "location": "Antalya",
        "capacity_current": 1,
        "capacity_max": 3,
        "interests": ["Digital Marketing", "SEO", "Analytics"],
        "bio": "I work on performance marketing and measurement, and I help young professionals build a data-driven mindset.",
        "is_banned": False,
    },
    {
        "id": 28,
        "first_name": "Buse",
        "last_name": "Kurt",
        "role": "MENTEE",
        "status": "VERIFIED",
        "location": "Besiktas, Istanbul",
        "capacity_current": 0,
        "capacity_max": 0,
        "interests": ["Backend", "Database", "Spring Boot"],
        "bio": "I am a software engineering student looking for mentorship on database design and backend development.",
        "is_banned": False,
    },
    {
        "id": 29,
        "first_name": "Cem",
        "last_name": "Turan",
        "role": "MENTOR",
        "status": "VERIFIED",
        "location": "Konya",
        "capacity_current": 5,
        "capacity_max": 5,
        "interests": ["Cloud", "DevOps", "System Design"],
        "bio": "I focus on scalable systems, Docker, and cloud services, and I enjoy explaining infrastructure topics in simple terms.",
        "is_banned": False,
    },
    {
        "id": 30,
        "first_name": "Naz",
        "last_name": "Kilicoglu",
        "role": "MENTEE",
        "status": "VERIFIED",
        "location": "Istanbul",
        "capacity_current": 0,
        "capacity_max": 0,
        "interests": ["Product Management", "Entrepreneurship", "User Research"],
        "bio": "I have a new digital product idea and want guidance on user research, roadmap planning, and product thinking.",
        "is_banned": False,
    },
    {
        "id": 31,
        "first_name": "Levent",
        "last_name": "Sezer",
        "role": "MENTOR",
        "status": "VERIFIED",
        "location": "Bursa",
        "capacity_current": 2,
        "capacity_max": 3,
        "interests": ["Finance", "Accounting", "Startup Operations"],
        "bio": "I have worked in finance and operations for many years and help early-stage teams build strong internal structure.",
        "is_banned": False,
    },
    {
        "id": 32,
        "first_name": "Sule",
        "last_name": "Dogan",
        "role": "MENTEE",
        "status": "VERIFIED",
        "location": "Ankara",
        "capacity_current": 0,
        "capacity_max": 0,
        "interests": ["Academic Career", "Research", "Study Abroad"],
        "bio": "I am exploring graduate study and research-oriented career paths and want to understand my options more clearly.",
        "is_banned": False,
    },
    {
        "id": 33,
        "first_name": "Pelin",
        "last_name": "Basaran",
        "role": "MENTOR",
        "status": "VERIFIED",
        "location": "Istanbul",
        "capacity_current": 1,
        "capacity_max": 4,
        "interests": ["UI/UX", "Product Design", "Design Systems"],
        "bio": "I work in product design and user research, and I help junior designers improve their portfolios and case studies.",
        "is_banned": False,
    },
    {
        "id": 34,
        "first_name": "Onur",
        "last_name": "Kilic",
        "role": "MENTEE",
        "status": "UNVERIFIED",
        "location": "Kadikoy, Istanbul",
        "capacity_current": 0,
        "capacity_max": 0,
        "interests": ["iOS", "Swift", "Mobile Development"],
        "bio": "I am a beginner in mobile development and want a clearer path for learning iOS development step by step.",
        "is_banned": False,
    },
    {
        "id": 35,
        "first_name": "Seda",
        "last_name": "Yalcin",
        "role": "MENTOR",
        "status": "VERIFIED",
        "location": "Izmir",
        "capacity_current": 0,
        "capacity_max": 2,
        "interests": ["Career Development", "Leadership", "Communication Skills"],
        "bio": "I support personal development, leadership growth, and healthy work habits, especially for recent graduates.",
        "is_banned": False,
    },
    {
        "id": 36,
        "first_name": "Riza",
        "last_name": "Uysal",
        "role": "MENTEE",
        "status": "VERIFIED",
        "location": "Antalya",
        "capacity_current": 0,
        "capacity_max": 0,
        "interests": ["Entrepreneurship", "Finance", "Pitch Deck"],
        "bio": "I want to move from a small tourism business into a more tech-oriented venture and need help with business modelling.",
        "is_banned": False,
    },
    {
        "id": 37,
        "first_name": "Mina",
        "last_name": "Eren",
        "role": "MENTEE",
        "status": "VERIFIED",
        "location": "Sisli, Istanbul",
        "capacity_current": 0,
        "capacity_max": 0,
        "interests": ["Frontend", "TypeScript", "React"],
        "bio": "I am continuing my learning after a bootcamp and want to read real project code and write cleaner frontend applications.",
        "is_banned": False,
    },
    {
        "id": 38,
        "first_name": "Haluk",
        "last_name": "Bayer",
        "role": "MENTOR",
        "status": "VERIFIED",
        "location": "Ankara",
        "capacity_current": 3,
        "capacity_max": 6,
        "interests": ["Academic Career", "Research", "Interview Prep"],
        "bio": "I am a retired professor who helps students with graduate planning, research discipline, and public speaking.",
        "is_banned": False,
    },
    {
        "id": 39,
        "first_name": "Asli",
        "last_name": "Kara",
        "role": "MENTEE",
        "status": "VERIFIED",
        "location": "Bursa",
        "capacity_current": 0,
        "capacity_max": 0,
        "interests": ["Digital Marketing", "Content Marketing", "SEO"],
        "bio": "I am learning digital marketing for my small brand and want guidance on content planning and visibility growth.",
        "is_banned": False,
    },
    {
        "id": 40,
        "first_name": "Nihat",
        "last_name": "Erkmen",
        "role": "MENTOR",
        "status": "VERIFIED",
        "location": "Kadikoy, Istanbul",
        "capacity_current": 2,
        "capacity_max": 2,
        "interests": ["Backend", "Database", "Cloud"],
        "bio": "I have spent many years in enterprise software projects and I mentor on API design, data modelling, and code quality.",
        "is_banned": False,
    },
    {
        "id": 41,
        "first_name": "Gul",
        "last_name": "Tufekci",
        "role": "MENTEE",
        "status": "VERIFIED",
        "location": "Istanbul",
        "capacity_current": 0,
        "capacity_max": 0,
        "interests": ["Machine Learning", "Data Science", "Research"],
        "bio": "I am in the thesis stage and want to clarify my experimental plan and find examples that match my topic.",
        "is_banned": False,
    },
    {
        "id": 42,
        "first_name": "Selcuk",
        "last_name": "Mert",
        "role": "MENTOR",
        "status": "VERIFIED",
        "location": "Sisli, Istanbul",
        "capacity_current": 1,
        "capacity_max": 3,
        "interests": ["Startups", "Entrepreneurship", "Product Management"],
        "bio": "I work on product validation and business model development and enjoy helping people turn ideas into structured plans.",
        "is_banned": False,
    },
    {
        "id": 43,
        "first_name": "Damla",
        "last_name": "Ersoy",
        "role": "MENTEE",
        "status": "VERIFIED",
        "location": "Ankara",
        "capacity_current": 0,
        "capacity_max": 0,
        "interests": ["Interview Prep", "Communication Skills", "Career Switch"],
        "bio": "I am preparing to move into a new sector and want to practice expressing myself more clearly in interviews.",
        "is_banned": False,
    },
    {
        "id": 44,
        "first_name": "Kerem",
        "last_name": "Bozkurt",
        "role": "MENTOR",
        "status": "VERIFIED",
        "location": "Antalya",
        "capacity_current": 4,
        "capacity_max": 4,
        "interests": ["Frontend", "Accessibility", "React"],
        "bio": "I mentor on user interface development and accessibility, with a strong focus on clean user experience.",
        "is_banned": False,
    },
    {
        "id": 45,
        "first_name": "Pinar",
        "last_name": "Gok",
        "role": "MENTEE",
        "status": "UNVERIFIED",
        "location": "Besiktas, Istanbul",
        "capacity_current": 0,
        "capacity_max": 0,
        "interests": ["Backend", "API Design", "Spring Boot"],
        "bio": "I want to build a backend career and need support with API design and software architecture basics.",
        "is_banned": False,
    },
    {
        "id": 46,
        "first_name": "Tayfun",
        "last_name": "Aksu",
        "role": "MENTOR",
        "status": "VERIFIED",
        "location": "Izmir",
        "capacity_current": 2,
        "capacity_max": 5,
        "interests": ["Business Analysis", "Product Management", "Leadership"],
        "bio": "I help connect business needs with product teams and support people transitioning into corporate roles.",
        "is_banned": False,
    },
    {
        "id": 47,
        "first_name": "Ceren",
        "last_name": "Sari",
        "role": "MENTEE",
        "status": "VERIFIED",
        "location": "Bursa",
        "capacity_current": 0,
        "capacity_max": 0,
        "interests": ["UI/UX", "Figma", "Portfolio Review"],
        "bio": "I want to strengthen my design portfolio and learn how to focus on real user problems.",
        "is_banned": False,
    },
    {
        "id": 48,
        "first_name": "Murat",
        "last_name": "Tekin",
        "role": "MENTOR",
        "status": "VERIFIED",
        "location": "Ankara",
        "capacity_current": 1,
        "capacity_max": 2,
        "interests": ["Finance", "Accounting", "Career Development"],
        "bio": "I have worked in finance teams for years and enjoy helping people understand how numbers shape career decisions.",
        "is_banned": False,
    },
    {
        "id": 49,
        "first_name": "Neslihan",
        "last_name": "Colak",
        "role": "MENTEE",
        "status": "VERIFIED",
        "location": "Kadikoy, Istanbul",
        "capacity_current": 0,
        "capacity_max": 0,
        "interests": ["Digital Marketing", "Brand Strategy", "Content Marketing"],
        "bio": "I work in social media but want to better understand brand strategy, positioning, and practical feedback from a mentor.",
        "is_banned": False,
    },
    {
        "id": 50,
        "first_name": "Ali",
        "last_name": "Bicakci",
        "role": "MENTOR",
        "status": "VERIFIED",
        "location": "Istanbul",
        "capacity_current": 3,
        "capacity_max": 7,
        "interests": ["Machine Learning", "Data Science", "Career Development"],
        "bio": "I have worked on data products and analytics teams for years, and I help students shape both their technical and career goals.",
        "is_banned": False,
    },
]


TURKISH_FIRST_NAMES = [
    "Ali", "Ayse", "Can", "Ceren", "Deniz", "Derya", "Ece", "Emre", "Eren", "Ezgi",
    "Fikret", "Gamze", "Gul", "Hakan", "Hale", "Haluk", "Ipek", "Kaan", "Kerem", "Levent",
    "Merve", "Mert", "Mina", "Murat", "Naz", "Neslihan", "Nihat", "Nil", "Oguz", "Onur",
    "Pelin", "Pinar", "Riza", "Selcuk", "Selin", "Serdar", "Seda", "Sule", "Sude", "Tayfun",
    "Tolga", "Yusuf", "Zeynep", "Bora", "Baris", "Buse", "Asli", "Damla", "Burak", "Yagmur",
]

TURKISH_LAST_NAMES = [
    "Acar", "Aksu", "Aksoy", "Arslan", "Aydin", "Basaran", "Balci", "Bayer", "Bicakci", "Bozkurt",
    "Cetin", "Colak", "Demir", "Dogan", "Ekinci", "Eren", "Erkmen", "Ersoy", "Gok", "Gunes",
    "Karaca", "Kara", "Kilic", "Kilicoglu", "Korkmaz", "Kurt", "Mert", "Ozturk", "Polat", "Sahin",
    "Sezer", "Sari", "Sonmez", "Tan", "Tekin", "Turan", "Tufekci", "Ural", "Uysal", "Yalcin",
]

LOCATIONS = [
    "Kadikoy, Istanbul", "Sisli, Istanbul", "Besiktas, Istanbul", "Uskudar, Istanbul", "Bakirkoy, Istanbul",
    "Maltepe, Istanbul", "Beyoglu, Istanbul", "Ankara", "Cankaya, Ankara", "Izmir", "Karşıyaka, Izmir",
    "Bursa", "Antalya", "Konya", "Eskişehir", "Adana", "Gaziantep", "Kayseri", "Trabzon", "Mersin",
]

MENTEE_INTEREST_SETS = [
    ["Frontend Development", "React", "Portfolio Review"],
    ["Backend Development", "System Design", "Interview Prep"],
    ["Machine Learning", "Data Science", "Python"],
    ["Digital Marketing", "Content Strategy", "SEO"],
    ["Career Switch", "Communication Skills", "Business Analysis"],
    ["UI/UX Design", "Figma", "Design Systems"],
    ["iOS Development", "Swift", "Mobile Development"],
    ["Entrepreneurship", "Finance", "Pitch Deck"],
    ["Research", "Academic Career", "Study Abroad"],
    ["Product Management", "User Research", "Leadership"],
    ["Cyber Security", "Ethical Hacking", "Internship Hunt"],
    ["Game Development", "Unity", "3D Modeling"],
    ["Blockchain", "Smart Contracts", "Web3"],
    ["DevOps", "Docker", "Cloud Fundamentals"],
    ["QA Automation", "Selenium", "Test Strategy"],
    ["Data Engineering", "ETL", "SQL"],
    ["Cloud Engineering", "AWS", "Certification Prep"],
    ["Mobile App Development", "Flutter", "Startup Ideas"],
    ["Algorithms", "Competitive Programming", "Problem Solving"],
    ["Public Speaking", "Leadership", "Confidence"],
    ["Time Management", "Study Habits", "Focus"],
    ["English Writing", "Technical Blogging", "Personal Branding"],
    ["Machine Learning Operations", "Model Deployment", "Cloud"],
    ["Computer Vision", "OpenCV", "Research Papers"],
    ["Full Stack Development", "Node.js", "MongoDB"],
    ["FinTech", "Risk Analysis", "Data Visualization"],
    ["Game Design", "Level Design", "Storytelling"],
    ["AI Ethics", "Prompting", "Responsible AI"],
    ["Digital Product Thinking", "Roadmapping", "Stakeholder Communication"],
    ["Study Abroad", "GRE Prep", "Campus Networking"],
    ["Bootcamp Graduate", "First Job", "Portfolio Building"],
]

MENTOR_INTEREST_SETS = [
    ["Frontend Architecture", "React", "Accessibility"],
    ["Backend Engineering", "Java", "Spring Boot"],
    ["Machine Learning", "Data Science", "Python"],
    ["Digital Marketing", "Brand Strategy", "Analytics"],
    ["Career Development", "Interview Prep", "Leadership"],
    ["UI/UX Strategy", "Product Design", "Figma"],
    ["iOS Engineering", "Swift", "Mobile Development"],
    ["Entrepreneurship", "Finance", "Startup Operations"],
    ["Research Leadership", "Academic Career", "Public Speaking"],
    ["Cloud Architecture", "DevOps", "System Design"],
    ["Engineering Management", "Team Growth", "Hiring"],
    ["Angel Investing", "Startup Strategy", "Mentorship"],
    ["Career Transition", "Coaching", "Personal Growth"],
    ["Retirement Planning", "Financial Literacy", "Life Design"],
    ["Security Architecture", "Threat Modeling", "Zero Trust"],
    ["Data Platform Design", "Streaming", "Governance"],
    ["Agile Leadership", "Scrum", "Cross-Functional Collaboration"],
    ["MLOps", "Model Deployment", "Operational Excellence"],
    ["Platform Engineering", "Observability", "Reliability"],
    ["Startup Founding", "Fundraising", "Go-to-Market"],
    ["Technical Writing", "Documentation", "Developer Experience"],
    ["Open Source", "Community Building", "Code Review"],
    ["FinTech", "Payments", "Regulatory Compliance"],
    ["HealthTech", "Privacy", "Clinical Workflows"],
    ["EdTech", "Curriculum Design", "Learning Science"],
    ["Game Industry", "Production", "Live Operations"],
    ["Embedded Systems", "Firmware", "Hardware Debugging"],
    ["AI Governance", "Model Risk", "Responsible Innovation"],
    ["Customer Success", "B2B Growth", "Retention"],
    ["Sales Engineering", "Discovery Calls", "Solution Design"],
    ["Mentoring First-Gen Talent", "Inclusive Hiring", "Career Growth"],
]

BIO_TEMPLATES = {
    "MENTEE": [
        "Seeking practical guidance in {interest1} and steady progress through small, well-defined projects.",
        "Transitioning toward {interest1} and looking for a realistic roadmap with clear milestones.",
        "Open to feedback on a {interest1} portfolio, with a strong focus on interview preparation and professional polish.",
        "Working to connect a current background with {interest1} and would value a structured mentorship plan.",
        "Building confidence in {interest1} while strengthening {interest2} through consistent practice and support.",
        "Early in a tech journey and focused on learning {interest1} alongside stronger {interest2} habits.",
        "Preparing for an internship and aiming to grow in {interest1} while becoming more effective at {interest2}.",
        "Recently completed a bootcamp and now looking to apply {interest1} in real-world projects with stronger {interest2} skills.",
        "Exploring a career shift into {interest1} and seeking practical advice on {interest2} and next steps.",
        "Aiming to improve both {interest1} and {interest2} with mentorship that is direct, supportive, and actionable.",
        "Eager to build a stronger foundation in {interest1} and learn how professionals approach {interest2}.",
        "Balancing studies and personal projects while developing {interest1} and better {interest2} habits.",
        "Focused on turning curiosity about {interest1} into a serious path, with extra attention on {interest2}.",
        "Looking for guidance that can turn practice in {interest1} into measurable progress and stronger {interest2}.",
        "Preparing for study abroad and building skills in {interest1} while improving {interest2} for collaboration.",
        "Working toward a first role in tech and aiming to sharpen {interest1} as well as {interest2}.",
        "Interested in {interest1} and eager to become more disciplined in {interest2} through mentorship.",
        "Developing a portfolio around {interest1} and hoping to present work more confidently through {interest2}.",
        "Learning {interest1} from scratch while building the communication and planning habits needed for {interest2}.",
        "Committed to long-term growth in {interest1} and seeking a mentor who can help refine {interest2}.",
    ],
    "MENTOR": [
        "Bringing years of hands-on experience in {interest1} and {interest2}, with a focus on practical, measurable growth.",
        "Supporting mentees who want to translate theory in {interest1} into real-world results and stronger {interest2} habits.",
        "Guiding professionals through career growth in {interest1}, with additional emphasis on communication and {interest2}.",
        "Working across {interest1} and {interest2}, and helping people build confidence through clear milestones.",
        "Focused on helping ambitious learners develop strong foundations in {interest1} while improving {interest2}.",
        "Offering mentorship shaped by experience in {interest1}, leadership, and practical decision-making around {interest2}.",
        "Actively mentoring people who are preparing for their next step in {interest1} and want to become stronger in {interest2}.",
        "Providing thoughtful feedback to mentees exploring {interest1} and building professional habits around {interest2}.",
        "Helping people navigate growth in {interest1} with realistic planning, accountability, and better {interest2}.",
        "Bringing a startup and product perspective to conversations about {interest1} and {interest2}.",
        "Sharing lessons from senior roles in {interest1}, with an emphasis on clarity, systems thinking, and {interest2}.",
        "Mentoring with a balance of empathy and structure for people building skills in {interest1} and {interest2}.",
        "Supporting learners who are moving from early exploration to confident execution in {interest1} and {interest2}.",
        "Drawing from experience in architecture, delivery, and team growth to mentor around {interest1} and {interest2}.",
        "Helping mentees connect technical depth in {interest1} with the professional habits needed for {interest2}.",
        "Offering practical mentorship for those pursuing growth in {interest1}, especially when paired with stronger {interest2}.",
        "Guiding the next generation of builders in {interest1} with a strong focus on feedback, iteration, and {interest2}.",
        "Bringing cross-industry experience to mentorship in {interest1} and {interest2}, especially for people navigating change.",
        "Mentoring people who want to grow beyond the basics of {interest1} and develop more confident {interest2} skills.",
        "Sharing a long-term perspective on careers in {interest1} and {interest2}, with advice that is calm, practical, and clear.",
    ],
}


def pick(items, index: int):
    return items[index % len(items)]


def professional_bio(persona: dict) -> str:
    interests = persona.get("interests") or []
    interest1 = interests[0] if len(interests) > 0 else "professional growth"
    interest2 = interests[1] if len(interests) > 1 else "collaboration"
    template_pool = BIO_TEMPLATES.get(persona.get("role"), BIO_TEMPLATES["MENTEE"])
    template = pick(template_pool, persona.get("id", 0))
    return template.format(interest1=interest1, interest2=interest2)


def normalize_persona(persona: dict) -> dict:
    normalized = dict(persona)

    if not normalized.get("first_name"):
        normalized["first_name"] = "Persona"
    if not normalized.get("last_name"):
        normalized["last_name"] = str(normalized.get("id", "User"))
    if not normalized.get("location"):
        normalized["location"] = "Istanbul"
    if not normalized.get("role"):
        normalized["role"] = "MENTEE"
    if not normalized.get("status"):
        normalized["status"] = "VERIFIED"
    if not normalized.get("interests"):
        normalized["interests"] = ["Professional Growth", "Communication"]
    if normalized.get("capacity_max") is None:
        normalized["capacity_max"] = 0
    if normalized.get("capacity_current") is None:
        normalized["capacity_current"] = 0

    bio = (normalized.get("bio") or "").strip()
    if not bio or bio.lower().startswith("i am"):
        normalized["bio"] = professional_bio(normalized)

    return normalized


def generate_bulk_personas(start_id: int = 51, total: int = 80):
    bulk = []
    for offset in range(total):
        persona_id = start_id + offset
        is_mentor = offset % 3 != 0
        first_name = pick(TURKISH_FIRST_NAMES, offset)
        last_name = pick(TURKISH_LAST_NAMES, offset * 2 + 3)
        location = pick(LOCATIONS, offset)

        if is_mentor:
            interests = pick(MENTOR_INTEREST_SETS, offset)
            capacity_max = [2, 3, 4, 5][offset % 4]
            capacity_current = capacity_max if offset % 8 == 0 else max(0, capacity_max - (offset % 3))
            bio_template = pick(BIO_TEMPLATES["MENTOR"], offset)
            status = "VERIFIED" if offset % 10 != 7 else "UNVERIFIED"
            role = "MENTOR"
            if capacity_current > capacity_max:
                capacity_current = capacity_max
        else:
            interests = pick(MENTEE_INTEREST_SETS, offset)
            capacity_max = 0
            capacity_current = 0
            bio_template = pick(BIO_TEMPLATES["MENTEE"], offset)
            status = "VERIFIED" if offset % 11 != 5 else "UNVERIFIED"
            role = "MENTEE"

        bulk.append({
            "id": persona_id,
            "first_name": first_name,
            "last_name": last_name,
            "role": role,
            "status": status,
            "location": location,
            "capacity_current": capacity_current,
            "capacity_max": capacity_max,
            "interests": interests,
            "bio": bio_template.format(interest1=interests[0], interest2=interests[1]),
            "is_banned": offset in {13, 29, 47},
        })

    return bulk


PERSONAS.extend(generate_bulk_personas())

PERSONAS.append(
    {
        "id": 131,
        "first_name": "Deniz",
        "last_name": "Admin",
        "role": "ADMIN",
        "status": "VERIFIED",
        "location": "Istanbul",
        "capacity_current": 0,
        "capacity_max": 0,
        "interests": ["Platform Governance", "Community Safety"],
        "bio": "I manage the platform, review moderation workflows, and support safe community operations.",
        "is_banned": False,
    }
)

PERSONAS = [normalize_persona(persona) for persona in PERSONAS]


MENTORSHIP_EDGE_CASES = [
    {"mentee_id": 1, "mentor_id": 2, "duration": 3, "message": "I would like guidance on data science foundations and a practical roadmap."},
    {"mentee_id": 7, "mentor_id": 2, "duration": 3, "message": "I need help planning my data science portfolio and first project."},
    {"mentee_id": 8, "mentor_id": 3, "duration": 3, "message": "I want mentorship on finance and startup strategy."},
    {"mentee_id": 11, "mentor_id": 3, "duration": 3, "message": "I am exploring startup thinking and product-first finance basics."},
    {"mentee_id": 12, "mentor_id": 3, "duration": 3, "message": "I want to understand startup decision-making and early-stage budgeting."},
    {"mentee_id": 30, "mentor_id": 17, "duration": 6, "message": "I want help validating a product idea and structuring a pitch deck."},
    {"mentee_id": 32, "mentor_id": 19, "duration": 3, "message": "I am preparing for graduate study and research planning."},
    {"mentee_id": 37, "mentor_id": 23, "duration": 3, "message": "I need portfolio feedback and frontend architecture advice."},
    {"mentee_id": 41, "mentor_id": 42, "duration": 3, "message": "I want guidance on research-focused career choices and roadmap planning."},
    {"mentee_id": 47, "mentor_id": 44, "duration": 1, "message": "I want product design feedback and a practical UI/UX review."},
]


def slugify(value: str) -> str:
    value = value.lower().strip()
    value = re.sub(r"[^a-z0-9]+", ".", value)
    return value.strip(".") or "user"


def build_email(persona: dict) -> str:
    return f"{slugify(persona['first_name'])}.{slugify(persona['last_name'])}.{persona['id']}@seed.test"


def rate_limit_delay(payload: dict, default_delay: int = 12) -> int:
    message = " ".join(str(payload.get(key, "")) for key in ("message", "error", "raw"))
    match = re.search(r"(\d+)\s*seconds?", message)
    if match:
        return max(1, int(match.group(1)))
    return default_delay


def request_json(req: urllib.request.Request, max_retries: int = 6):
    for attempt in range(max_retries + 1):
        try:
            with urllib.request.urlopen(req) as r:
                return r.status, parse_json_response(r.read())
        except urllib.error.HTTPError as e:
            payload = parse_json_response(e.read())
            if e.code == 429 and attempt < max_retries:
                delay = rate_limit_delay(payload, int(e.headers.get("Retry-After", "12") or "12"))
                time.sleep(delay)
                continue
            return e.code, payload
        except urllib.error.URLError as e:
            return 0, {"error": str(e)}
    return 0, {"error": "request retry limit exceeded"}


def http_post(url: str, data: dict, token: Optional[str] = None):
    body = json.dumps(data).encode("utf-8")
    headers = {"Content-Type": "application/json"}
    if token:
        headers["Authorization"] = f"Bearer {token}"
    req = urllib.request.Request(url, data=body, headers=headers, method="POST")
    return request_json(req)


def http_get(url: str, token: Optional[str] = None):
    headers = {}
    if token:
        headers["Authorization"] = f"Bearer {token}"
    req = urllib.request.Request(url, headers=headers)
    return request_json(req)


def http_patch(url: str, data: dict, token: str):
    body = json.dumps(data).encode("utf-8")
    headers = {"Content-Type": "application/json", "Authorization": f"Bearer {token}"}
    req = urllib.request.Request(url, data=body, headers=headers, method="PATCH")
    return request_json(req)


def http_put(url: str, data: dict, token: str):
    body = json.dumps(data).encode("utf-8")
    headers = {"Content-Type": "application/json", "Authorization": f"Bearer {token}"}
    req = urllib.request.Request(url, data=body, headers=headers, method="PUT")
    return request_json(req)


def parse_json_response(payload: bytes):
    if not payload:
        return {}

    text = payload.decode("utf-8", errors="replace").strip()
    if not text:
        return {}

    try:
        return json.loads(text)
    except json.JSONDecodeError:
        return {"raw": text}


def get_verification_token(email: str):
    for _ in range(8):
        _, data = http_get(f"{MAILHOG}/messages?limit=200")
        for msg in data.get("items", []):
            to = msg["Content"]["Headers"].get("To", [""])[0]
            if email.lower() not in to.lower():
                continue
            body = msg["Content"]["Body"]
            match = re.search(r"token=([A-Za-z0-9\-]+)", body)
            if match:
                return match.group(1)
        time.sleep(0.5)
    return None


def export_personas() -> None:
    OUTPUT_PATH.write_text(json.dumps(PERSONAS, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


def mentor_profile_payload(persona: dict) -> dict:
    interests = persona.get("interests", [])
    return {
        "firstName": persona["first_name"],
        "lastName": persona["last_name"],
        "bio": persona["bio"],
        "field": interests[0] if interests else "General",
        "expertise": interests[0] if interests else "General",
        "affiliation": persona["location"],
        "interests": interests,
        "maxMenteeCapacity": persona["capacity_max"],
        "preferredMenteeSkills": interests[:3],
        "preferredMenteeMajor": interests[0] if interests else "General",
        "mentoringGoals": persona["bio"],
        "mentorshipDuration": 3,
    }


def mentee_profile_payload(persona: dict) -> dict:
    interests = persona.get("interests", [])
    return {
        "firstName": persona["first_name"],
        "lastName": persona["last_name"],
        "profileVisibility": True,
        "goals": persona["bio"],
        "major": interests[0] if interests else "General",
        "interests": interests,
        "careerInterest": interests[0] if interests else "General",
        "skills": interests[:3],
        "meetingFreqPref": "Weekly",
        "backgroundInfo": persona["bio"],
    }


def register_persona(persona: dict):
    status, resp = http_post(
        f"{BASE}/auth/register",
        {
            "firstName": persona["first_name"],
            "lastName": persona["last_name"],
            "email": build_email(persona),
            "password": SEED_PASSWORD,
            "isMentor": persona["role"] == "MENTOR",
        },
    )
    if status not in (200, 201):
        return status, resp, None
    return status, resp, resp.get("id")


def login_persona(persona: dict):
    email = build_email(persona)
    status, resp = http_post(f"{BASE}/auth/login", {"email": email, "password": SEED_PASSWORD})
    if status != 200 or "sessionToken" not in resp or "userId" not in resp:
        return status, resp, None
    return status, resp, {"token": resp["sessionToken"], "user_id": resp["userId"]}


def update_profile(persona: dict, token: str):
    if persona["role"] == "MENTOR":
        return http_patch(f"{BASE}/users/me/mentor", mentor_profile_payload(persona), token)
    return http_patch(f"{BASE}/users/me/mentee", mentee_profile_payload(persona), token)


def create_mentorship_request(mentee_id: int, mentor_id: int, token: str, message: str):
    return http_post(
        f"{BASE}/mentorship-requests",
        {"mentorId": mentor_id, "message": message},
        token,
    )


def accept_mentorship_request(request_id: int, duration: int, token: str):
    return http_put(
        f"{BASE}/mentorship-requests/{request_id}/accept",
        {"duration": duration},
        token,
    )


def send_mentorship_message(mentorship_id: int, token: str, content: str):
    return http_post(
        f"{BASE}/mentorships/{mentorship_id}/messages",
        {"content": content},
        token,
    )


def create_task(mentorship_id: int, token: str, title: str, description: str, due_date: datetime):
    return http_post(
        f"{BASE}/mentorships/{mentorship_id}/tasks",
        {
            "title": title,
            "description": description,
            "dueDate": due_date.isoformat(),
        },
        token,
    )


def create_meeting(mentorship_id: int, token: str, title: str, description: str, start_time: datetime, end_time: datetime):
    return http_post(
        f"{BASE}/mentorships/{mentorship_id}/meetings",
        {
            "title": title,
            "description": description,
            "startTime": start_time.isoformat(),
            "endTime": end_time.isoformat(),
            "meetingType": "ONLINE",
            "meetingLink": "https://meet.google.com/seed-demo-session",
            "recurring": False,
            "recurrenceRule": None,
        },
        token,
    )


def request_meeting_reschedule(meeting_id: int, token: str, proposed_start: datetime, proposed_end: datetime, reason: str):
    return http_post(
        f"{BASE}/meetings/{meeting_id}/reschedule-requests",
        {
            "proposedStart": proposed_start.isoformat(),
            "proposedEnd": proposed_end.isoformat(),
            "reason": reason,
        },
        token,
    )


def create_feed_post(token: str, content: str, hashtags):
    return http_post(
        f"{BASE}/posts",
        {
            "content": content,
            "hashtags": hashtags,
        },
        token,
    )


def process_persona(persona: dict, index: int) -> Optional[dict]:
    email = build_email(persona)
    print(f"[{index + 1:2}/{len(PERSONAS)}] {persona['first_name']} {persona['last_name']} ({email})", end=" ... ", flush=True)

    if persona["role"] == "ADMIN":
        print("admin fixture exported; backend bootstrap handles ADMIN creation")
        return None

    status, resp, auth = login_persona(persona)
    registered_id = None
    if status == 200 and auth:
        print("already exists, continuing")
    else:
        status, resp, registered_id = register_persona(persona)
        if status not in (200, 201):
            if "already" in str(resp).lower() or status == 409:
                print("already exists, continuing")
            else:
                print(f"REGISTER FAILED ({status}): {resp}")
                return False

        if persona["status"] != "VERIFIED":
            print("registered as unverified fixture")
            return None

        status, resp, auth = login_persona(persona)
        if status != 200 or not auth:
            if status == 401 and "not verified" in str(resp).lower():
                token = get_verification_token(email)
                if token:
                    http_get(f"{BASE}/auth/verify-email?token={token}")
                    status, resp, auth = login_persona(persona)
            if status != 200 or not auth:
                print(f"LOGIN FAILED ({status}): {resp}")
                return False

    status, resp = update_profile(persona, auth["token"])
    if status != 200:
        print(f"PROFILE UPDATE FAILED ({status}): {resp}")
        return None

    if persona["is_banned"]:
        print("seeded; ban flag not applied because no public ban endpoint is exposed", end="")

    print(f"OK (id={resp.get('id')})")
    user_id = resp.get("id") or registered_id or auth["user_id"]
    return {"token": auth["token"], "user_id": user_id, "role": persona["role"]}


def seed_mentorship_edges(persona_tokens: dict):
    ok = 0
    created_mentorships = {}
    for edge in MENTORSHIP_EDGE_CASES:
        mentee = persona_tokens.get(edge["mentee_id"])
        mentor = persona_tokens.get(edge["mentor_id"])
        if not mentee or not mentor:
            print(f"[MENTORSHIP] skipping {edge['mentee_id']} -> {edge['mentor_id']} because a token is missing")
            continue

        status, resp = create_mentorship_request(mentee["user_id"], mentor["user_id"], mentee["token"], edge["message"])
        if status not in (200, 201):
            print(f"[MENTORSHIP] request failed ({status}): {resp}")
            continue

        request_id = resp.get("id")
        status, resp = accept_mentorship_request(request_id, edge["duration"], mentor["token"])
        if status not in (200, 201):
            print(f"[MENTORSHIP] accept failed ({status}): {resp}")
            continue

        ok += 1
        mentorship_id = resp.get("id")
        created_mentorships[(edge["mentee_id"], edge["mentor_id"])] = {
            "mentorship_id": mentorship_id,
            "request_id": request_id,
            "mentee": mentee,
            "mentor": mentor,
        }
        print(f"[MENTORSHIP] created {mentee['user_id']} -> {mentor['user_id']} (request {request_id})")

    print(f"Mentorship edges seeded: {ok}/{len(MENTORSHIP_EDGE_CASES)}")
    return created_mentorships


def seed_demo_ecosystem(persona_tokens: dict, mentorships: dict):
    elif_ahmet = mentorships.get((1, 2))
    mert_ahmet = mentorships.get((7, 2))

    if elif_ahmet:
        mentoring_thread = [
            (1, "I have been revisiting the roadmap you gave me and I now have a stronger sense of which topics matter first."),
            (2, "That is great progress. Next, I want you to focus on one small project that combines data cleaning and visualization."),
            (1, "I started the project and I will share the first draft of my notebook after I test the preprocessing steps."),
            (2, "Perfect. Keep it simple, document your decisions, and we will review it together in our next session."),
        ]
        for sender_id, content in mentoring_thread:
            sender = persona_tokens.get(sender_id)
            if not sender:
                continue
            status, resp = send_mentorship_message(elif_ahmet["mentorship_id"], sender["token"], content)
            if status not in (200, 201):
                print(f"[DEMO][messages] failed ({status}): {resp}")
            else:
                print(f"[DEMO][messages] seeded message {resp.get('id')} for mentorship {elif_ahmet['mentorship_id']}")

        due_date = datetime.now().astimezone() - timedelta(days=1)
        status, resp = create_task(
            elif_ahmet["mentorship_id"],
            persona_tokens[2]["token"],
            "Finish the data cleaning checklist",
            "Prepare a cleaned dataset, add column notes, and document the assumptions you made.",
            due_date,
        )
        if status not in (200, 201):
            fallback_due_date = datetime.now().astimezone() + timedelta(days=2)
            fallback_status, fallback_resp = create_task(
                elif_ahmet["mentorship_id"],
                persona_tokens[2]["token"],
                "Finish the data cleaning checklist",
                "Prepare a cleaned dataset, add column notes, and document the assumptions you made.",
                fallback_due_date,
            )
            print(f"[DEMO][task] yesterday due date rejected ({status}); fallback used ({fallback_status})")
            if fallback_status not in (200, 201):
                print(f"[DEMO][task] fallback failed ({fallback_status}): {fallback_resp}")
            else:
                print(f"[DEMO][task] seeded task {fallback_resp.get('id')} for mentorship {elif_ahmet['mentorship_id']}")
        else:
            print(f"[DEMO][task] seeded overdue-style task {resp.get('id')} for mentorship {elif_ahmet['mentorship_id']}")

    if mert_ahmet:
        meeting_start = datetime.now().astimezone() + timedelta(days=2, hours=1)
        meeting_end = meeting_start + timedelta(hours=1)
        status, resp = create_meeting(
            mert_ahmet["mentorship_id"],
            persona_tokens[2]["token"],
            "Portfolio review sync",
            "Review the latest portfolio draft and agree on one follow-up improvement.",
            meeting_start,
            meeting_end,
        )
        if status not in (200, 201):
            print(f"[DEMO][meeting] creation failed ({status}): {resp}")
        else:
            meetings = resp.get("meetings", [])
            meeting_id = meetings[0]["id"] if meetings else None
            if meeting_id:
                reschedule_start = meeting_start + timedelta(days=1)
                reschedule_end = meeting_end + timedelta(days=1)
                rs_status, rs_resp = request_meeting_reschedule(
                    meeting_id,
                    persona_tokens[7]["token"],
                    reschedule_start,
                    reschedule_end,
                    "I have a class conflict and need to move the review by one day.",
                )
                if rs_status not in (200, 201):
                    print(f"[DEMO][meeting] reschedule request failed ({rs_status}): {rs_resp}")
                else:
                    print(f"[DEMO][meeting] created pending reschedule request {rs_resp.get('id')} for meeting {meeting_id}")
    else:
        print("[DEMO][meeting] skipped because no Dr. Ahmet mentorship exists for another mentee")

    elif_post_status, elif_post_resp = create_feed_post(
        persona_tokens[1]["token"],
        "Working on a new data cleaning routine and comparing feature choices with my mentor today. #DataScience #MachineLearning",
        ["DataScience", "MachineLearning"],
    )
    if elif_post_status not in (200, 201):
        print(f"[DEMO][feed] post creation skipped/failed ({elif_post_status}): {elif_post_resp}")
    else:
        print(f"[DEMO][feed] seeded post {elif_post_resp.get('id')} for Elif")

    mentor_posts = [
        (
            persona_tokens[2]["token"],
            "A good mentorship plan starts with one small experiment and a clear review loop. #DataScience #MachineLearning",
            ["DataScience", "MachineLearning"],
        ),
        (
            persona_tokens[2]["token"],
            "Strong growth comes from consistent practice, feedback, and a focused portfolio story. #CareerGrowth #Mentorship",
            ["CareerGrowth", "Mentorship"],
        ),
    ]
    for token, content, hashtags in mentor_posts:
        status, resp = create_feed_post(token, content, hashtags)
        if status not in (200, 201):
            print(f"[DEMO][feed] mentor post skipped/failed ({status}): {resp}")
        else:
            print(f"[DEMO][feed] seeded post {resp.get('id')}")


def main() -> None:
    export_personas()
    print(f"Exported {len(PERSONAS)} personas to {OUTPUT_PATH}")

    persona_tokens = {}
    for index, persona in enumerate(PERSONAS):
        auth = process_persona(persona, index)
        if not auth:
            continue

        persona_tokens[persona["id"]] = auth

    mentorships = seed_mentorship_edges(persona_tokens)
    seed_demo_ecosystem(persona_tokens, mentorships or {})


if __name__ == "__main__":
    main()