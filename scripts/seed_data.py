"""Shared mock-data constants for the local seed scripts.

Extracted from `seed_personas.py` so both that script and the lightweight
`seed_local.py` (direct-DB seed for `docker compose` users) can share the same
pools without copy-paste drift.
"""

from __future__ import annotations


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
