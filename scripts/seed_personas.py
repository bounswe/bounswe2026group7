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


PERSONAS = [   {   'id': 1,
        'first_name': 'Elif',
        'last_name': 'Yilmaz',
        'role': 'MENTEE',
        'status': 'VERIFIED',
        'location': 'Istanbul',
        'capacity_current': 0,
        'capacity_max': 0,
        'interests': ['Data Science', 'Machine Learning', 'Music', 'Sociology', 'Photography'],
        'is_banned': False,
        'background': 'Computer science undergrad with a part-time interest in qualitative '
                      'research methods; plays classical guitar and shoots film photography on the side.',
        'goals': 'Transitioning into data science with a long-term interest in computational '
                 'social science. Looking for roadmap advice, project ideas, and a realistic '
                 'study plan that leaves room for non-technical hobbies.'},
    {   'id': 2,
        'first_name': 'Dr. Ahmet',
        'last_name': 'Bulut',
        'role': 'MENTOR',
        'status': 'VERIFIED',
        'location': 'Besiktas, Istanbul',
        'capacity_current': 2,
        'capacity_max': 3,
        'interests': ['Data Science', 'Academic Career'],
        'bio': 'Supports students who want to build a strong foundation in data science and pursue an academic career. '
               'currently actively mentoring Elif.',
        'is_banned': False,
        'mentoring_goals': 'Aims to support and guide mentees in Data Science.'},
    {   'id': 3,
        'first_name': 'Mehmet',
        'last_name': 'Yilmaz',
        'role': 'MENTOR',
        'status': 'VERIFIED',
        'location': 'Sisli, Istanbul',
        'capacity_current': 3,
        'capacity_max': 3,
        'interests': ['Finance', 'Startups'],
        'bio': 'Mentors founders on finance basics, startup strategy, and how to communicate early-stage ideas '
               'clearly.',
        'is_banned': False,
        'mentoring_goals': 'Aims to support and guide mentees in Finance.'},
    {   'id': 4,
        'first_name': 'Zeynep',
        'last_name': 'Arslan',
        'role': 'MENTOR',
        'status': 'VERIFIED',
        'location': 'Kadikoy, Istanbul',
        'capacity_current': 0,
        'capacity_max': 2,
        'interests': ['iOS', 'Swift'],
        'bio': 'Mentors aspiring iOS developers on Swift fundamentals, architecture, testing, and career entry steps.',
        'is_banned': False,
        'mentoring_goals': 'Aims to support and guide mentees in iOS.'},
    {   'id': 5,
        'first_name': 'Eren',
        'last_name': 'Kilic',
        'role': 'MENTEE',
        'status': 'VERIFIED',
        'location': 'Ankara',
        'capacity_current': 0,
        'capacity_max': 0,
        'interests': ['Backend', 'Interview Prep'],
        'is_banned': True,
        'background': 'Academic or professional background in Backend.',
        'goals': 'Preparing for internship interviews and want structured help with backend fundamentals and practice '
                 'questions.'},
    {   'id': 6,
        'first_name': 'Ayse',
        'last_name': 'Kaya',
        'role': 'MENTEE',
        'status': 'UNVERIFIED',
        'location': 'Izmir',
        'capacity_current': 0,
        'capacity_max': 0,
        'interests': ['Frontend', 'Career Switch'],
        'is_banned': False,
        'background': 'Academic or professional background in Frontend.',
        'goals': 'Exploring a career switch into software and want guidance on where to focus first.'},
    {   'id': 7,
        'first_name': 'Mert',
        'last_name': 'Aydin',
        'role': 'MENTEE',
        'status': 'VERIFIED',
        'location': 'Kadikoy, Istanbul',
        'capacity_current': 0,
        'capacity_max': 0,
        'interests': ['Frontend', 'React', 'Portfolio Review'],
        'is_banned': False,
        'background': 'Academic or professional background in Frontend.',
        'goals': 'Final-year computer engineering student looking to improve my portfolio and get ready for my first '
                 'job.'},
    {   'id': 8,
        'first_name': 'Selin',
        'last_name': 'Ozturk',
        'role': 'MENTEE',
        'status': 'VERIFIED',
        'location': 'Besiktas, Istanbul',
        'capacity_current': 0,
        'capacity_max': 0,
        'interests': ['Machine Learning', 'Python', 'Data Science'],
        'is_banned': False,
        'background': 'Academic or professional background in Machine Learning.',
        'goals': "Preparing for a master's program in artificial intelligence and need project ideas and a better "
                 'study roadmap.'},
    {   'id': 9,
        'first_name': 'Bora',
        'last_name': 'Cetin',
        'role': 'MENTOR',
        'status': 'VERIFIED',
        'location': 'Izmir',
        'capacity_current': 1,
        'capacity_max': 4,
        'interests': ['Product Management', 'Startups', 'Entrepreneurship'],
        'bio': 'Works on product strategy and early-stage startup growth. helps students clarify their goals and '
               'present ideas well.',
        'is_banned': False,
        'mentoring_goals': 'Aims to support and guide mentees in Product Management.'},
    {   'id': 10,
        'first_name': 'Hale',
        'last_name': 'Demir',
        'role': 'MENTOR',
        'status': 'VERIFIED',
        'location': 'Ankara',
        'capacity_current': 2,
        'capacity_max': 5,
        'interests': ['Digital Marketing', 'Brand Strategy', 'Content Marketing'],
        'bio': 'Has over ten years of experience in digital marketing and brand strategy. supports mentees with '
               'practical, measurable growth plans.',
        'is_banned': False,
        'mentoring_goals': 'Aims to support and guide mentees in Digital Marketing.'},
    {   'id': 11,
        'first_name': 'Kaan',
        'last_name': 'Yildirim',
        'role': 'MENTEE',
        'status': 'VERIFIED',
        'location': 'Sisli, Istanbul',
        'capacity_current': 0,
        'capacity_max': 0,
        'interests': ['Backend', 'Node.js', 'System Design'],
        'is_banned': False,
        'background': 'Academic or professional background in Backend.',
        'goals': 'Aims to improve my backend design skills and learn how to build more scalable APIs.'},
    {   'id': 12,
        'first_name': 'Derya',
        'last_name': 'Karaca',
        'role': 'MENTEE',
        'status': 'VERIFIED',
        'location': 'Bursa',
        'capacity_current': 0,
        'capacity_max': 0,
        'interests': ['UI/UX', 'Frontend', 'Design Systems'],
        'is_banned': False,
        'background': 'Academic or professional background in UI/UX.',
        'goals': 'Moving from graphic design into product design and want feedback on user experience and portfolio '
                 'quality.'},
    {   'id': 13,
        'first_name': 'Emre',
        'last_name': 'Aksoy',
        'role': 'MENTOR',
        'status': 'VERIFIED',
        'location': 'Antalya',
        'capacity_current': 1,
        'capacity_max': 3,
        'interests': ['Backend', 'Cloud', 'DevOps'],
        'bio': 'Works on backend architecture and cloud infrastructure and help junior developers build practical '
               'project habits.',
        'is_banned': False,
        'mentoring_goals': 'Aims to support and guide mentees in Backend.'},
    {   'id': 14,
        'first_name': 'Nil',
        'last_name': 'Gunes',
        'role': 'MENTEE',
        'status': 'VERIFIED',
        'location': 'Kadikoy, Istanbul',
        'capacity_current': 0,
        'capacity_max': 0,
        'interests': ['Interview Prep', 'Career Switch', 'Business Analysis'],
        'is_banned': False,
        'background': 'Academic or professional background in Interview Prep.',
        'goals': 'Preparing to move into a business analyst role and want help with interview questions and a focused '
                 'study plan.'},
    {   'id': 15,
        'first_name': 'Oguz',
        'last_name': 'Sahin',
        'role': 'MENTOR',
        'status': 'VERIFIED',
        'location': 'Bursa',
        'capacity_current': 4,
        'capacity_max': 6,
        'interests': ['Java', 'Spring Boot', 'Backend'],
        'bio': 'Has years of experience in enterprise software development and mentors students who want to build '
               'production-ready backend skills.',
        'is_banned': False,
        'mentoring_goals': 'Aims to support and guide mentees in Java.'},
    {   'id': 16,
        'first_name': 'Ipek',
        'last_name': 'Acar',
        'role': 'MENTEE',
        'status': 'UNVERIFIED',
        'location': 'Izmir',
        'capacity_current': 0,
        'capacity_max': 0,
        'interests': ['Digital Marketing', 'Content Marketing', 'SEO'],
        'is_banned': False,
        'background': 'Academic or professional background in Digital Marketing.',
        'goals': 'Transitioning into marketing and want to learn how to build a practical content and SEO strategy.'},
    {   'id': 17,
        'first_name': 'Serdar',
        'last_name': 'Koc',
        'role': 'MENTOR',
        'status': 'VERIFIED',
        'location': 'Konya',
        'capacity_current': 2,
        'capacity_max': 4,
        'interests': ['Entrepreneurship', 'Finance', 'Pitch Deck'],
        'bio': 'Mentors founders on startup finance, business models, and how to shape a clear pitch deck for '
               'early-stage investors.',
        'is_banned': False,
        'mentoring_goals': 'Aims to support and guide mentees in Entrepreneurship.'},
    {   'id': 18,
        'first_name': 'Sude',
        'last_name': 'Ergin',
        'role': 'MENTEE',
        'status': 'VERIFIED',
        'location': 'Besiktas, Istanbul',
        'capacity_current': 0,
        'capacity_max': 0,
        'interests': ['Machine Learning', 'Data Science', 'Python'],
        'is_banned': False,
        'background': 'Academic or professional background in Machine Learning.',
        'goals': 'Has data analysis coursework but need more hands-on project experience and realistic examples from '
                 'industry.'},
    {   'id': 19,
        'first_name': 'Fikret',
        'last_name': 'Ersoy',
        'role': 'MENTOR',
        'status': 'VERIFIED',
        'location': 'Ankara',
        'capacity_current': 1,
        'capacity_max': 2,
        'interests': ['Academic Career', 'Research', 'Machine Learning'],
        'bio': 'Helps students prepare for graduate study, research planning, and academic career decisions.',
        'is_banned': False,
        'mentoring_goals': 'Aims to support and guide mentees in Academic Career.'},
    {   'id': 20,
        'first_name': 'Gamze',
        'last_name': 'Ates',
        'role': 'MENTEE',
        'status': 'VERIFIED',
        'location': 'Antalya',
        'capacity_current': 0,
        'capacity_max': 0,
        'interests': ['Sales', 'Communication Skills', 'Career Switch'],
        'is_banned': False,
        'background': 'Academic or professional background in Sales.',
        'goals': 'Moving from tourism into technology and want to understand which roles fit my communication '
                 'strengths best.'},
    {   'id': 21,
        'first_name': 'Tolga',
        'last_name': 'Ekinci',
        'role': 'MENTOR',
        'status': 'VERIFIED',
        'location': 'Sisli, Istanbul',
        'capacity_current': 0,
        'capacity_max': 3,
        'interests': ['Interview Prep', 'LeetCode', 'Backend'],
        'bio': 'Mentors students on technical interview preparation, algorithm practice, and confident communication '
               'in hiring processes.',
        'is_banned': False,
        'mentoring_goals': 'Aims to support and guide mentees in Interview Prep.'},
    {   'id': 22,
        'first_name': 'Merve',
        'last_name': 'Ural',
        'role': 'MENTEE',
        'status': 'VERIFIED',
        'location': 'Ankara',
        'capacity_current': 0,
        'capacity_max': 0,
        'interests': ['Frontend', 'Vue', 'UI/UX'],
        'is_banned': False,
        'background': 'Academic or professional background in Frontend.',
        'goals': 'Learning frontend development and want to build a small but realistic product while improving my '
                 'design sense.'},
    {   'id': 23,
        'first_name': 'Yusuf',
        'last_name': 'Balci',
        'role': 'MENTOR',
        'status': 'VERIFIED',
        'location': 'Kadikoy, Istanbul',
        'capacity_current': 3,
        'capacity_max': 5,
        'interests': ['Frontend', 'React', 'Design Systems'],
        'bio': 'Works on frontend architecture and design systems and help younger developers focus on maintainable, '
               'readable code.',
        'is_banned': False,
        'mentoring_goals': 'Aims to support and guide mentees in Frontend.'},
    {   'id': 24,
        'first_name': 'Aylin',
        'last_name': 'Sonmez',
        'role': 'MENTEE',
        'status': 'UNVERIFIED',
        'location': 'Bursa',
        'capacity_current': 0,
        'capacity_max': 0,
        'interests': ['Project Management', 'Communication Skills', 'Career Switch'],
        'is_banned': False,
        'background': 'Academic or professional background in Project Management.',
        'goals': 'Aims to move into project coordination and would value guidance from someone with practical '
                 'corporate experience.'},
    {   'id': 25,
        'first_name': 'Hakan',
        'last_name': 'Polat',
        'role': 'MENTOR',
        'status': 'VERIFIED',
        'location': 'Izmir',
        'capacity_current': 2,
        'capacity_max': 4,
        'interests': ['Career Development', 'Interview Prep', 'Leadership'],
        'bio': 'Has worked in talent management and supports mentees with CV feedback, interview prep, and career '
               'planning.',
        'is_banned': False,
        'mentoring_goals': 'Aims to support and guide mentees in Career Development.'},
    {   'id': 26,
        'first_name': 'Ece',
        'last_name': 'Tan',
        'role': 'MENTEE',
        'status': 'VERIFIED',
        'location': 'Sisli, Istanbul',
        'capacity_current': 0,
        'capacity_max': 0,
        'interests': ['Data Science', 'Statistics', 'Python'],
        'is_banned': False,
        'background': 'Academic or professional background in Data Science.',
        'goals': 'Recently graduated from statistics and want help choosing the right tools and projects for data '
                 'science.'},
    {   'id': 27,
        'first_name': 'Baris',
        'last_name': 'Korkmaz',
        'role': 'MENTOR',
        'status': 'VERIFIED',
        'location': 'Antalya',
        'capacity_current': 1,
        'capacity_max': 3,
        'interests': ['Digital Marketing', 'SEO', 'Analytics'],
        'bio': 'Works on performance marketing and measurement, and helps young professionals build a data-driven '
               'mindset.',
        'is_banned': False,
        'mentoring_goals': 'Aims to support and guide mentees in Digital Marketing.'},
    {   'id': 28,
        'first_name': 'Buse',
        'last_name': 'Kurt',
        'role': 'MENTEE',
        'status': 'VERIFIED',
        'location': 'Besiktas, Istanbul',
        'capacity_current': 0,
        'capacity_max': 0,
        'interests': ['Backend', 'Database', 'Spring Boot'],
        'is_banned': False,
        'background': 'Academic or professional background in Backend.',
        'goals': 'Software engineering student looking for mentorship on database design and backend development.'},
    {   'id': 29,
        'first_name': 'Cem',
        'last_name': 'Turan',
        'role': 'MENTOR',
        'status': 'VERIFIED',
        'location': 'Konya',
        'capacity_current': 5,
        'capacity_max': 5,
        'interests': ['Cloud', 'DevOps', 'System Design'],
        'bio': 'Focuses on scalable systems, Docker, and cloud services, and enjoys explaining infrastructure topics '
               'in simple terms.',
        'is_banned': False,
        'mentoring_goals': 'Aims to support and guide mentees in Cloud.'},
    {   'id': 30,
        'first_name': 'Naz',
        'last_name': 'Kilicoglu',
        'role': 'MENTEE',
        'status': 'VERIFIED',
        'location': 'Istanbul',
        'capacity_current': 0,
        'capacity_max': 0,
        'interests': ['Product Management', 'Entrepreneurship', 'User Research'],
        'is_banned': False,
        'background': 'Academic or professional background in Product Management.',
        'goals': 'Has a new digital product idea and want guidance on user research, roadmap planning, and product '
                 'thinking.'},
    {   'id': 31,
        'first_name': 'Levent',
        'last_name': 'Sezer',
        'role': 'MENTOR',
        'status': 'VERIFIED',
        'location': 'Bursa',
        'capacity_current': 2,
        'capacity_max': 3,
        'interests': ['Finance', 'Accounting', 'Startup Operations'],
        'bio': 'Has worked in finance and operations for many years and help early-stage teams build strong internal '
               'structure.',
        'is_banned': False,
        'mentoring_goals': 'Aims to support and guide mentees in Finance.'},
    {   'id': 32,
        'first_name': 'Sule',
        'last_name': 'Dogan',
        'role': 'MENTEE',
        'status': 'VERIFIED',
        'location': 'Ankara',
        'capacity_current': 0,
        'capacity_max': 0,
        'interests': ['Academic Career', 'Research', 'Study Abroad'],
        'is_banned': False,
        'background': 'Academic or professional background in Academic Career.',
        'goals': 'Exploring graduate study and research-oriented career paths and want to understand my options more '
                 'clearly.'},
    {   'id': 33,
        'first_name': 'Pelin',
        'last_name': 'Basaran',
        'role': 'MENTOR',
        'status': 'VERIFIED',
        'location': 'Istanbul',
        'capacity_current': 1,
        'capacity_max': 4,
        'interests': ['UI/UX', 'Product Design', 'Design Systems'],
        'bio': 'Works in product design and user research, and helps junior designers improve their portfolios and '
               'case studies.',
        'is_banned': False,
        'mentoring_goals': 'Aims to support and guide mentees in UI/UX.'},
    {   'id': 34,
        'first_name': 'Onur',
        'last_name': 'Kilic',
        'role': 'MENTEE',
        'status': 'UNVERIFIED',
        'location': 'Kadikoy, Istanbul',
        'capacity_current': 0,
        'capacity_max': 0,
        'interests': ['iOS', 'Swift', 'Mobile Development'],
        'is_banned': False,
        'background': 'Academic or professional background in iOS.',
        'goals': 'Beginner in mobile development and want a clearer path for learning iOS development step by step.'},
    {   'id': 35,
        'first_name': 'Seda',
        'last_name': 'Yalcin',
        'role': 'MENTOR',
        'status': 'VERIFIED',
        'location': 'Izmir',
        'capacity_current': 0,
        'capacity_max': 2,
        'interests': ['Career Development', 'Leadership', 'Communication Skills'],
        'bio': 'Supports personal development, leadership growth, and healthy work habits, especially for recent '
               'graduates.',
        'is_banned': False,
        'mentoring_goals': 'Aims to support and guide mentees in Career Development.'},
    {   'id': 36,
        'first_name': 'Riza',
        'last_name': 'Uysal',
        'role': 'MENTEE',
        'status': 'VERIFIED',
        'location': 'Antalya',
        'capacity_current': 0,
        'capacity_max': 0,
        'interests': ['Entrepreneurship', 'Finance', 'Pitch Deck'],
        'is_banned': False,
        'background': 'Academic or professional background in Entrepreneurship.',
        'goals': 'Aims to move from a small tourism business into a more tech-oriented venture and need help with '
                 'business modelling.'},
    {   'id': 37,
        'first_name': 'Mina',
        'last_name': 'Eren',
        'role': 'MENTEE',
        'status': 'VERIFIED',
        'location': 'Sisli, Istanbul',
        'capacity_current': 0,
        'capacity_max': 0,
        'interests': ['Frontend', 'TypeScript', 'React'],
        'is_banned': False,
        'background': 'Academic or professional background in Frontend.',
        'goals': 'Continuing my learning after a bootcamp and want to read real project code and write cleaner '
                 'frontend applications.'},
    {   'id': 38,
        'first_name': 'Haluk',
        'last_name': 'Bayer',
        'role': 'MENTOR',
        'status': 'VERIFIED',
        'location': 'Ankara',
        'capacity_current': 3,
        'capacity_max': 6,
        'interests': ['Academic Career', 'Research', 'Interview Prep'],
        'bio': 'Retired professor who helps students with graduate planning, research discipline, and public speaking.',
        'is_banned': False,
        'mentoring_goals': 'Aims to support and guide mentees in Academic Career.'},
    {   'id': 39,
        'first_name': 'Asli',
        'last_name': 'Kara',
        'role': 'MENTEE',
        'status': 'VERIFIED',
        'location': 'Bursa',
        'capacity_current': 0,
        'capacity_max': 0,
        'interests': ['Digital Marketing', 'Content Marketing', 'SEO'],
        'is_banned': False,
        'background': 'Academic or professional background in Digital Marketing.',
        'goals': 'Learning digital marketing for my small brand and want guidance on content planning and visibility '
                 'growth.'},
    {   'id': 40,
        'first_name': 'Nihat',
        'last_name': 'Erkmen',
        'role': 'MENTOR',
        'status': 'VERIFIED',
        'location': 'Kadikoy, Istanbul',
        'capacity_current': 2,
        'capacity_max': 2,
        'interests': ['Backend', 'Database', 'Cloud'],
        'bio': 'Has spent many years in enterprise software projects and mentors on API design, data modelling, and '
               'code quality.',
        'is_banned': False,
        'mentoring_goals': 'Aims to support and guide mentees in Backend.'},
    {   'id': 41,
        'first_name': 'Gul',
        'last_name': 'Tufekci',
        'role': 'MENTEE',
        'status': 'VERIFIED',
        'location': 'Istanbul',
        'capacity_current': 0,
        'capacity_max': 0,
        'interests': ['Machine Learning', 'Data Science', 'Research'],
        'is_banned': False,
        'background': 'Academic or professional background in Machine Learning.',
        'goals': 'In the thesis stage and want to clarify my experimental plan and find examples that match my topic.'},
    {   'id': 42,
        'first_name': 'Selcuk',
        'last_name': 'Mert',
        'role': 'MENTOR',
        'status': 'VERIFIED',
        'location': 'Sisli, Istanbul',
        'capacity_current': 1,
        'capacity_max': 3,
        'interests': ['Startups', 'Entrepreneurship', 'Product Management'],
        'bio': 'Works on product validation and business model development and enjoy helping people turn ideas into '
               'structured plans.',
        'is_banned': False,
        'mentoring_goals': 'Aims to support and guide mentees in Startups.'},
    {   'id': 43,
        'first_name': 'Damla',
        'last_name': 'Ersoy',
        'role': 'MENTEE',
        'status': 'VERIFIED',
        'location': 'Ankara',
        'capacity_current': 0,
        'capacity_max': 0,
        'interests': ['Interview Prep', 'Communication Skills', 'Career Switch'],
        'is_banned': False,
        'background': 'Academic or professional background in Interview Prep.',
        'goals': 'Preparing to move into a new sector and want to practice expressing myself more clearly in '
                 'interviews.'},
    {   'id': 44,
        'first_name': 'Kerem',
        'last_name': 'Bozkurt',
        'role': 'MENTOR',
        'status': 'VERIFIED',
        'location': 'Antalya',
        'capacity_current': 4,
        'capacity_max': 4,
        'interests': ['Frontend', 'Accessibility', 'React'],
        'bio': 'Mentors on user interface development and accessibility, with a strong focus on clean user experience.',
        'is_banned': False,
        'mentoring_goals': 'Aims to support and guide mentees in Frontend.'},
    {   'id': 45,
        'first_name': 'Pinar',
        'last_name': 'Gok',
        'role': 'MENTEE',
        'status': 'UNVERIFIED',
        'location': 'Besiktas, Istanbul',
        'capacity_current': 0,
        'capacity_max': 0,
        'interests': ['Backend', 'API Design', 'Spring Boot'],
        'is_banned': False,
        'background': 'Academic or professional background in Backend.',
        'goals': 'Aims to build a backend career and need support with API design and software architecture basics.'},
    {   'id': 46,
        'first_name': 'Tayfun',
        'last_name': 'Aksu',
        'role': 'MENTOR',
        'status': 'VERIFIED',
        'location': 'Izmir',
        'capacity_current': 2,
        'capacity_max': 5,
        'interests': ['Business Analysis', 'Product Management', 'Leadership'],
        'bio': 'Helps connect business needs with product teams and support people transitioning into corporate roles.',
        'is_banned': False,
        'mentoring_goals': 'Aims to support and guide mentees in Business Analysis.'},
    {   'id': 47,
        'first_name': 'Ceren',
        'last_name': 'Sari',
        'role': 'MENTEE',
        'status': 'VERIFIED',
        'location': 'Bursa',
        'capacity_current': 0,
        'capacity_max': 0,
        'interests': ['UI/UX', 'Figma', 'Portfolio Review'],
        'is_banned': False,
        'background': 'Academic or professional background in UI/UX.',
        'goals': 'Aims to strengthen my design portfolio and learn how to focus on real user problems.'},
    {   'id': 48,
        'first_name': 'Murat',
        'last_name': 'Tekin',
        'role': 'MENTOR',
        'status': 'VERIFIED',
        'location': 'Ankara',
        'capacity_current': 1,
        'capacity_max': 2,
        'interests': ['Finance', 'Accounting', 'Career Development'],
        'bio': 'Has worked in finance teams for years and enjoy helping people understand how numbers shape career '
               'decisions.',
        'is_banned': False,
        'mentoring_goals': 'Aims to support and guide mentees in Finance.'},
    {   'id': 49,
        'first_name': 'Neslihan',
        'last_name': 'Colak',
        'role': 'MENTEE',
        'status': 'VERIFIED',
        'location': 'Kadikoy, Istanbul',
        'capacity_current': 0,
        'capacity_max': 0,
        'interests': ['Digital Marketing', 'Brand Strategy', 'Content Marketing'],
        'is_banned': False,
        'background': 'Academic or professional background in Digital Marketing.',
        'goals': 'Works in social media but want to better understand brand strategy, positioning, and practical '
                 'feedback from a mentor.'},
    {   'id': 50,
        'first_name': 'Ali',
        'last_name': 'Bicakci',
        'role': 'MENTOR',
        'status': 'VERIFIED',
        'location': 'Istanbul',
        'capacity_current': 3,
        'capacity_max': 7,
        'interests': ['Machine Learning', 'Data Science', 'Career Development'],
        'bio': 'Has worked on data products and analytics teams for years, and helps students shape both their '
               'technical and career goals.',
        'is_banned': False,
        'mentoring_goals': 'Aims to support and guide mentees in Machine Learning.'}]



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

MENTEE_BACKGROUND_TEMPLATES = [
    "I am currently studying computer engineering and trying to apply my knowledge to real-world problems.",
    "I have been learning about {interest1} through online courses and bootcamps.",
    "My background is in a different field, but I have recently started exploring tech and {interest2}.",
    "I work as a junior professional and want to dive deeper into {interest1}.",
    "I have some academic experience in {interest1} but I lack practical industry experience.",
    "I recently graduated and am actively looking for opportunities to use my skills in {interest1}.",
    "I have been a hobbyist programmer for a few years, mostly focusing on {interest2}.",
    "I am transitioning from a non-technical role and have completed several projects in {interest1}.",
    "I am an undergraduate student with a strong passion for {interest1} and {interest2}.",
    "I have been self-taught in {interest1} for the past year and want to take it to the next level.",
    "Currently exploring different career paths in technology, with a particular focus on {interest1}.",
    "I have built a few small projects using {interest2} and want to learn enterprise standards.",
]

MENTOR_GOALS_TEMPLATES = [
    "Aims to share industry experience and guide mentees in {interest1} and {interest2}.",
    "Focuses on providing practical insights and career guidance for those interested in {interest1}.",
    "Passionate about helping juniors navigate the complexities of {interest1} and build confidence.",
    "Dedicated to mentoring aspiring professionals in {interest1}, helping them achieve their career milestones.",
    "Wants to support mentees by sharing real-world lessons in {interest1} and {interest2}.",
    "Committed to fostering growth in {interest1} through structured feedback and regular check-ins.",
    "Looks forward to helping mentees overcome obstacles in {interest1} and develop strong foundations.",
    "Interested in guiding early-career talents through the challenges of {interest1}.",
    "Aims to bridge the gap between academic knowledge and practical application in {interest1}.",
    "Enthusiastic about empowering the next generation of leaders in {interest1} and {interest2}.",
    "Focuses on career strategy, portfolio building, and technical growth in {interest1}.",
    "Dedicated to offering actionable advice and support for mentees navigating {interest1}.",
]


def pick(items, index: int):
    return items[index % len(items)]


def professional_bio(persona: dict) -> str:
    interests = persona.get("interests") or []
    interest1 = interests[0] if len(interests) > 0 else "professional growth"
    interest2 = interests[1] if len(interests) > 1 else "collaboration"
    template_pool = BIO_TEMPLATES.get(persona.get("role"), BIO_TEMPLATES["MENTEE"])
    template = pick(template_pool, persona.get("id", 0))
    return template.format(interest1=interest1, interest2=interest2)


def professional_background(persona: dict) -> str:
    interests = persona.get("interests") or []
    interest1 = interests[0] if len(interests) > 0 else "technology"
    interest2 = interests[1] if len(interests) > 1 else "collaboration"
    template = pick(MENTEE_BACKGROUND_TEMPLATES, persona.get("id", 0))
    return template.format(interest1=interest1, interest2=interest2)


def professional_mentoring_goals(persona: dict) -> str:
    interests = persona.get("interests") or []
    interest1 = interests[0] if len(interests) > 0 else "professional development"
    interest2 = interests[1] if len(interests) > 1 else "career growth"
    template = pick(MENTOR_GOALS_TEMPLATES, persona.get("id", 0))
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

    if normalized["role"] == "MENTEE":
        bio = normalized.pop("bio", "").strip()
        if not bio or bio.lower().startswith("i am"):
            normalized["goals"] = professional_bio(normalized)
        else:
            normalized["goals"] = bio
        normalized["background"] = professional_background(normalized)
    else:
        bio = (normalized.get("bio") or "").strip()
        if not bio or bio.lower().startswith("i am"):
            normalized["bio"] = professional_bio(normalized)
            
        if not normalized.get("mentoring_goals") or normalized["mentoring_goals"].startswith("Aims to support and guide mentees in"):
            normalized["mentoring_goals"] = professional_mentoring_goals(normalized)

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

# Non-tech / cross-domain personas — explicit named mentors and mentees in
# fields the bulk-generator pool doesn't surface clearly (Music, Sports,
# Public Speaking, Management). They give the showcase user (Elif) a
# realistic graph of varied interests to follow and engage with.
PERSONAS.extend([
    {
        "id": 132,
        "first_name": "Defne",
        "last_name": "Aksu",
        "role": "MENTOR",
        "status": "VERIFIED",
        "location": "Beyoglu, Istanbul",
        "capacity_current": 1,
        "capacity_max": 3,
        "interests": ["Music Production", "Songwriting", "Audio Engineering"],
        "bio": "Helps emerging musicians develop a release-ready workflow, from arrangement and "
               "production to mixing and distribution. Background in classical training plus a decade in indie studios.",
        "is_banned": False,
        "mentoring_goals": "Aims to support mentees in turning unfinished demos into shipped tracks.",
    },
    {
        "id": 133,
        "first_name": "Cem",
        "last_name": "Demir",
        "role": "MENTOR",
        "status": "VERIFIED",
        "location": "Ankara",
        "capacity_current": 0,
        "capacity_max": 4,
        "interests": ["Sports Performance", "Strength & Conditioning", "Sports Psychology"],
        "bio": "Strength coach for amateur and student athletes. Builds programs that balance "
               "academic schedules, training load, and recovery so people don't burn out mid-season.",
        "is_banned": False,
        "mentoring_goals": "Aims to help mentees build sustainable athletic habits alongside study and work.",
    },
    {
        "id": 134,
        "first_name": "Yasemin",
        "last_name": "Kara",
        "role": "MENTOR",
        "status": "VERIFIED",
        "location": "Kadikoy, Istanbul",
        "capacity_current": 2,
        "capacity_max": 4,
        "interests": ["Public Speaking", "Storytelling", "Executive Presence"],
        "bio": "Speaker coach for engineers and researchers preparing conference talks, interview "
               "panels, and academic defenses. Focus on narrative structure over slide polish.",
        "is_banned": False,
        "mentoring_goals": "Aims to help mentees deliver a clear, confident technical story in 10 minutes or less.",
    },
    {
        "id": 135,
        "first_name": "Tolga",
        "last_name": "Erdem",
        "role": "MENTOR",
        "status": "VERIFIED",
        "location": "Sisli, Istanbul",
        "capacity_current": 1,
        "capacity_max": 3,
        "interests": ["Management", "Team Leadership", "1:1 Coaching"],
        "bio": "Engineering manager turned coach. Helps individual contributors transitioning "
               "into their first management role build a steady cadence of 1:1s, feedback, and review.",
        "is_banned": False,
        "mentoring_goals": "Aims to help mentees grow into managers without losing their craft.",
    },
    {
        "id": 136,
        "first_name": "Mira",
        "last_name": "Yalcin",
        "role": "MENTEE",
        "status": "VERIFIED",
        "location": "Beyoglu, Istanbul",
        "capacity_current": 0,
        "capacity_max": 0,
        "interests": ["Music Production", "Songwriting", "Photography"],
        "is_banned": False,
        "background": "Self-taught producer balancing a day job and weekend recording sessions.",
        "goals": "Wants help finishing the first EP and developing a consistent release rhythm.",
    },
    {
        "id": 137,
        "first_name": "Burak",
        "last_name": "Ozdemir",
        "role": "MENTEE",
        "status": "VERIFIED",
        "location": "Bursa",
        "capacity_current": 0,
        "capacity_max": 0,
        "interests": ["Sports Performance", "Public Speaking", "Time Management"],
        "is_banned": False,
        "background": "University student-athlete training competitively while studying engineering.",
        "goals": "Wants a training program that respects exam crunch periods and improves "
                 "post-game interview confidence.",
    },
])

PERSONAS = [normalize_persona(persona) for persona in PERSONAS]


MENTORSHIP_EDGE_CASES = [
    {"mentee_id": 1, "mentor_id": 2, "duration": 3, "message": "I would like guidance on data science foundations and a practical roadmap."},
    # Elif's past mentor — completed gracefully in seed_demo_ecosystem so the
    # mentee dashboard surfaces a populated "previous mentorships" timeline,
    # not just a single active row.
    {"mentee_id": 1, "mentor_id": 19, "duration": 3, "message": "I am preparing to apply for graduate study with a research focus; would like roadmap help."},
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
        "mentoringGoals": persona.get("mentoring_goals", persona.get("bio", "")),
        "mentorshipDuration": 3,
    }


def mentee_profile_payload(persona: dict) -> dict:
    interests = persona.get("interests", [])
    return {
        "firstName": persona["first_name"],
        "lastName": persona["last_name"],
        "profileVisibility": True,
        "goals": persona.get("goals", ""),
        "major": interests[0] if interests else "General",
        "interests": interests,
        "careerInterest": interests[0] if interests else "General",
        "skills": interests[:3],
        "meetingFreqPref": "Weekly",
        "backgroundInfo": persona.get("background", ""),
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


def like_post(post_id: int, token: str):
    return http_post(f"{BASE}/feed/posts/{post_id}/like", {}, token)


def comment_on_post(post_id: int, token: str, body: str):
    return http_post(f"{BASE}/feed/posts/{post_id}/comments", {"body": body}, token)


def follow_user(user_id: int, token: str):
    return http_post(f"{BASE}/users/{user_id}/follow", {}, token)


def end_mentorship_gracefully(mentorship_id: int, token: str, reason: str = ""):
    return http_patch(
        f"{BASE}/mentorships/{mentorship_id}/end",
        {"reason": reason},
        token,
    )


def login_admin(email: str, password: str):
    """Lightweight admin liveness check — used by main() to confirm the
    AdminBootstrapper actually ran. Returns (status, body)."""
    return http_post(f"{BASE}/auth/login", {"email": email, "password": password})


def send_admin_dm(target_user_id: int, token: str, content: str):
    return http_post(
        f"{BASE}/admin/messages/direct/{target_user_id}",
        {"content": content},
        token,
    )


def send_admin_broadcast(token: str, content: str):
    return http_post(
        f"{BASE}/admin/messages/broadcast",
        {"content": content},
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


def seed_demo_ecosystem(persona_tokens: dict, mentorships: dict, admin_token: Optional[str] = None):
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

    # ── Past mentor for Elif: seed a short chat history with Fikret, then
    # end the (1, 19) mentorship gracefully so the mentee dashboard surfaces
    # a "previous mentorships" timeline with both messages and a COMPLETED
    # status row, not just the active Ahmet one.
    elif_fikret = mentorships.get((1, 19))
    if elif_fikret:
        past_thread = [
            (1, "Thanks for taking me on. I want to apply for graduate study within 2 years and would love a realistic roadmap."),
            (19, "Good. Let's narrow your research interest first — pick one paper this week and write a 1-page summary, including the dataset and the limitation you spot."),
            (1, "Sent the summary draft. The limitation I keep coming back to is the lack of held-out evaluation."),
            (19, "Solid catch. That's the kind of observation reviewers reward. Next: read the paper that cites this one most often and compare assumptions."),
            (1, "Wrapping up the comparison this weekend; my next step is the technical depth side with Dr. Ahmet. Thanks for the early planning push."),
        ]
        for sender_id, content in past_thread:
            sender = persona_tokens.get(sender_id)
            if not sender:
                continue
            status, resp = send_mentorship_message(elif_fikret["mentorship_id"], sender["token"], content)
            if status not in (200, 201):
                print(f"[DEMO][messages] past-mentor message failed ({status}): {resp}")
            else:
                print(f"[DEMO][messages] seeded past-mentor message {resp.get('id')}")

        end_status, end_resp = end_mentorship_gracefully(
            elif_fikret["mentorship_id"],
            persona_tokens[19]["token"],
            "Roadmap planning wrapped — Elif starting CS coursework. Continue with Dr. Ahmet for data science depth.",
        )
        if end_status not in (200, 201):
            print(f"[DEMO][mentorship] past-mentor close failed ({end_status}): {end_resp}")
        else:
            print(f"[DEMO][mentorship] closed past mentorship {elif_fikret['mentorship_id']} (Elif ↔ Fikret)")
    else:
        print("[DEMO][mentorship] past-mentor edge (1, 19) missing; skipping graceful close")

    # ── Feed posts: track ids so we can wire engagement around them ─────────
    feed_post_ids = {}

    feed_post_seed = [
        # (key, author_persona_id, content, hashtags)
        ("elif_data", 1,
         "Working on a new data cleaning routine and comparing feature choices "
         "with my mentor today. #DataScience #MachineLearning",
         ["DataScience", "MachineLearning"]),
        ("elif_music", 1,
         "Took a break to learn a new piece on classical guitar — and noticed "
         "the same iterative practice mindset transfers to debugging notebooks. "
         "#Music #LearningInPublic",
         ["Music", "LearningInPublic"]),
        ("elif_sociology", 1,
         "Started reading on computational social science. Quantitative methods "
         "+ qualitative grounding feel underrated as a combo. #Sociology #DataScience",
         ["Sociology", "DataScience"]),
        ("ahmet_plan", 2,
         "A good mentorship plan starts with one small experiment and a clear "
         "review loop. #DataScience #MachineLearning",
         ["DataScience", "MachineLearning"]),
        ("ahmet_growth", 2,
         "Strong growth comes from consistent practice, feedback, and a focused "
         "portfolio story. #CareerGrowth #Mentorship",
         ["CareerGrowth", "Mentorship"]),
        ("fikret_research", 19,
         "Research-track careers reward depth on a narrow question early on; "
         "breadth comes from collaborators, not your own reading list. #Research #AcademicCareer",
         ["Research", "AcademicCareer"]),
        ("mert_portfolio", 7,
         "Finishing a portfolio rewrite — focusing on three projects with clear "
         "before/after impact instead of ten weak ones. #Frontend #PortfolioReview",
         ["Frontend", "PortfolioReview"]),
    ]
    for key, author_id, content, hashtags in feed_post_seed:
        author = persona_tokens.get(author_id)
        if not author:
            continue
        status, resp = create_feed_post(author["token"], content, hashtags)
        if status not in (200, 201):
            print(f"[DEMO][feed] post '{key}' skipped/failed ({status}): {resp}")
            continue
        post_id = resp.get("id")
        feed_post_ids[key] = post_id
        print(f"[DEMO][feed] seeded post {post_id} ({key}) by persona {author_id}")

    # ── Likes: Elif likes the posts that align with her interests, and a
    # handful of other personas like Elif's posts so her engagement graph is
    # populated for the dashboard surfaces (recent likers, like counts).
    engagement_likes = [
        # (post_key, liker_persona_id)
        ("ahmet_plan", 1), ("ahmet_plan", 7), ("ahmet_plan", 8),
        ("ahmet_growth", 1), ("ahmet_growth", 7),
        ("fikret_research", 1), ("fikret_research", 8),
        ("mert_portfolio", 1), ("mert_portfolio", 12),
        ("elif_data", 2), ("elif_data", 7), ("elif_data", 8), ("elif_data", 19),
        ("elif_music", 2), ("elif_music", 7),
        ("elif_sociology", 2), ("elif_sociology", 19), ("elif_sociology", 8),
    ]
    for post_key, liker_id in engagement_likes:
        post_id = feed_post_ids.get(post_key)
        liker = persona_tokens.get(liker_id)
        if not post_id or not liker:
            continue
        status, _ = like_post(post_id, liker["token"])
        if status in (200, 201):
            print(f"[DEMO][feed] persona {liker_id} liked post {post_id} ({post_key})")

    # ── Comments: Elif comments on others' posts (showing engagement), and
    # other personas comment on Elif's posts (so her detail page renders a
    # real thread).
    engagement_comments = [
        # (post_key, commenter_persona_id, body)
        ("ahmet_plan", 1, "This is exactly what I have been trying to set up with you — small experiments first."),
        ("ahmet_plan", 7, "Saving this. The review-loop framing fixes 80% of where I get stuck."),
        ("fikret_research", 1, "Thanks again for the early roadmap — I am applying the depth-over-breadth advice now."),
        ("mert_portfolio", 1, "Cutting from ten to three was the hardest part for me too — but the signal got so much stronger."),
        ("elif_data", 2, "Great progress, Elif. Note the assumptions in the README so future-you can rerun cleanly."),
        ("elif_data", 7, "Curious which features you ended up dropping after the correlation pass."),
        ("elif_music", 2, "Cross-domain practice transfer is real — keep both habits."),
        ("elif_sociology", 19, "Welcome to the rabbit hole. Start with Salganik's Bit by Bit if you have not yet."),
    ]
    for post_key, commenter_id, body in engagement_comments:
        post_id = feed_post_ids.get(post_key)
        commenter = persona_tokens.get(commenter_id)
        if not post_id or not commenter:
            continue
        status, resp = comment_on_post(post_id, commenter["token"], body)
        if status in (200, 201):
            print(f"[DEMO][feed] persona {commenter_id} commented on post {post_id} ({post_key}) -> {resp.get('id')}")

    # ── Follow graph centred on Elif: she follows her active and past mentors
    # plus a few peers across her interest mix (technical, music, sociology,
    # photography), and a handful of personas follow her back so the network
    # surfaces (followers/following counts, Following feed) aren't empty for
    # the showcase mentee.
    follow_edges = [
        # (follower_persona_id, target_persona_id)
        (1, 2),    # Elif → Dr. Ahmet (active mentor)
        (1, 19),   # Elif → Fikret (past mentor)
        (1, 50),   # Elif → Ali Bicakci (additional ML mentor)
        (1, 7),    # Elif → Mert (peer mentee)
        (1, 8),    # Elif → Selin (peer mentee, ML/Data)
        (1, 12),   # Elif → Derya (peer mentee, UI/UX — cross-interest)
        (1, 132),  # Elif → Defne (music mentor — matches Elif's Music interest)
        (1, 133),  # Elif → Cem (sports mentor — wellness/life-balance interest)
        (1, 134),  # Elif → Yasemin (public speaking — research-presentation prep)
        (1, 136),  # Elif → Mira (music mentee — peer cross-interest)
        (1, 137),  # Elif → Burak (sports mentee — cross-interest)
        (2, 1),    # Dr. Ahmet → Elif (mentor follows mentee back)
        (19, 1),   # Fikret → Elif
        (7, 1),    # Mert → Elif
        (8, 1),    # Selin → Elif
        (132, 1),  # Defne → Elif (cross-domain peer engagement)
        (136, 1),  # Mira → Elif
        # A few edges that don't involve Elif so the broader graph isn't a star.
        (136, 132), # Mira → Defne (mentee in music follows music mentor)
        (137, 133), # Burak → Cem (sports mentee follows sports coach)
        (137, 134), # Burak → Yasemin (sports mentee follows public-speaking coach)
        (7, 135),   # Mert → Tolga (frontend mentee follows management mentor)
    ]
    for follower_id, target_id in follow_edges:
        follower = persona_tokens.get(follower_id)
        target = persona_tokens.get(target_id)
        if not follower or not target:
            continue
        status, _ = follow_user(target["user_id"], follower["token"])
        if status in (200, 201, 204):
            print(f"[DEMO][follow] persona {follower_id} → persona {target_id}")

    # ── Cross-domain feed posts from the new mentors + likes from Elif to
    # surface non-tech content on her dashboard.
    cross_domain_posts = [
        (132, "Mixing tip of the week: leave the kick at -6 dB and carve a 3 kHz dip in the bass for clarity. "
              "Small EQ moves outrank fancy plugins. #MusicProduction #Audio",
         ["MusicProduction", "Audio"]),
        (133, "Block your training into 6-week cycles aligned with your school term. Deload during exam week — "
              "the recovery shows up two weeks later, not the next session. #Sports #Training",
         ["Sports", "Training"]),
        (134, "Your slides aren't the story. Open with the question your work answers, close with what to do "
              "differently on Monday. Everything in between is supporting evidence. #PublicSpeaking",
         ["PublicSpeaking"]),
        (135, "First-time managers: protect your 1:1 calendar like prod traffic. Cancelled 1:1s compound silently "
              "into surprise resignations 4 months later. #Management #Leadership",
         ["Management", "Leadership"]),
    ]
    for author_id, content, hashtags in cross_domain_posts:
        author = persona_tokens.get(author_id)
        if not author:
            continue
        status, resp = create_feed_post(author["token"], content, hashtags)
        if status not in (200, 201):
            print(f"[DEMO][feed] cross-domain post by persona {author_id} skipped ({status}): {resp}")
            continue
        post_id = resp.get("id")
        print(f"[DEMO][feed] seeded cross-domain post {post_id} by persona {author_id}")

        # Elif likes + comments on each cross-domain post so the showcase
        # account has a populated cross-interest engagement history.
        elif_token = persona_tokens.get(1)
        if elif_token:
            like_post(post_id, elif_token["token"])
            elif_comments = {
                132: "This is exactly the kind of practical mixing note I was looking for — saving for the weekend session.",
                133: "Calibrating my training around exam blocks has been my biggest unsolved problem; trying this.",
                134: "Reframing slides as supporting evidence makes the prep so much less stressful.",
                135: "Reading this as a soon-to-be tech lead — the 1:1 cadence point is the one I keep underrating.",
            }
            comment_body = elif_comments.get(author_id)
            if comment_body:
                comment_on_post(post_id, elif_token["token"], comment_body)

    # ── Admin messaging surfaces (#280, #410, #561). One DM to the showcase
    # mentee Elif, one DM to the banned-fixture mentee Eren (so the
    # admin → user DM-after-ban surface has real content), and one broadcast
    # so every admin's broadcast inbox isn't empty on first log-in.
    if admin_token:
        admin_messages = [
            (1,  "Welcome to the platform — let us know via the report flow if anything looks off in your mentor matches."),
            (5,  "Your account is currently restricted following a community-guidelines review. Reach out here with any questions."),
            (7,  "Quick check: please confirm your portfolio review link in your profile is still the latest. Thanks!"),
        ]
        for target_persona_id, content in admin_messages:
            target = persona_tokens.get(target_persona_id)
            if not target:
                continue
            status, resp = send_admin_dm(target["user_id"], admin_token, content)
            if status in (200, 201):
                print(f"[DEMO][admin-dm] admin → persona {target_persona_id} (msg {resp.get('id')})")
            else:
                print(f"[DEMO][admin-dm] admin → persona {target_persona_id} failed ({status}): {resp}")

        admin_broadcasts = [
            "Reminder: this week's moderation queue closes at EOD Friday. Please review pending reports before then.",
            "Heads-up: the new admin-direct read endpoints are live — recipients can now see and reply to your DMs through the standard inbox.",
        ]
        for content in admin_broadcasts:
            status, resp = send_admin_broadcast(admin_token, content)
            if status in (200, 201):
                print(f"[DEMO][admin-broadcast] seeded broadcast msg {resp.get('id')}")
            else:
                print(f"[DEMO][admin-broadcast] failed ({status}): {resp}")
    else:
        print("[DEMO][admin-dm] skipped — no admin token (verify .env.example admin bootstrap)")


def verify_admin_exists() -> Optional[str]:
    """Verify the AdminBootstrapper created the default admin and return a
    session token for it. If the expected credentials don't log in, print a
    clear warning with remediation instructions and return None — the rest
    of the seed can still run, only admin-specific surfaces (DMs, broadcast)
    are skipped."""
    status, resp = login_admin("admin@group7.com", "Admin1234!")
    if status == 200 and "sessionToken" in resp:
        print("[ADMIN] verified: admin@group7.com is logged-in-able")
        return resp["sessionToken"]
    print(f"[ADMIN][WARN] could not log in as admin@group7.com (status={status}). "
          "Confirm the backend started with APP_ADMIN_BOOTSTRAP_ENABLED=true "
          "and a matching APP_ADMIN_BOOTSTRAP_PASSWORD (see .env.example). The "
          "rest of the seed will continue; persona accounts are unaffected; "
          "admin DMs and the broadcast thread will not be seeded.")
    return None


def main() -> None:
    export_personas()
    print(f"Exported {len(PERSONAS)} personas to {OUTPUT_PATH}")

    admin_token = verify_admin_exists()

    persona_tokens = {}
    for index, persona in enumerate(PERSONAS):
        auth = process_persona(persona, index)
        if not auth:
            continue

        persona_tokens[persona["id"]] = auth

    mentorships = seed_mentorship_edges(persona_tokens)
    seed_demo_ecosystem(persona_tokens, mentorships or {}, admin_token=admin_token)


if __name__ == "__main__":
    main()