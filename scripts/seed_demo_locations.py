"""City -> (lat, lon) map for seed users. Applied after Phase A so the
mentor location signal can emit `nearby:Xkm` factors instead of `location-unset`.
"""
from __future__ import annotations

CITY_COORDS: dict[str, tuple[float, float]] = {
    # Istanbul districts
    "Kadikoy, Istanbul":   (40.9905, 29.0303),
    "Sisli, Istanbul":     (41.0602, 28.9871),
    "Besiktas, Istanbul":  (41.0428, 29.0061),
    "Uskudar, Istanbul":   (41.0226, 29.0156),
    "Bakirkoy, Istanbul":  (40.9776, 28.8770),
    "Maltepe, Istanbul":   (40.9354, 29.1297),
    "Beyoglu, Istanbul":   (41.0369, 28.9774),
    "Atasehir, Istanbul":  (40.9846, 29.1276),
    "Sariyer, Istanbul":   (41.1714, 29.0568),
    "Pendik, Istanbul":    (40.8782, 29.2536),
    "Umraniye, Istanbul":  (41.0264, 29.1226),
    "Kartal, Istanbul":    (40.9056, 29.1908),
    "Esenyurt, Istanbul":  (41.0345, 28.6800),
    "Avcilar, Istanbul":   (40.9789, 28.7209),
    "Beylikduzu, Istanbul":(40.9938, 28.6411),
    "Cihangir, Istanbul":  (41.0322, 28.9817),
    # Ankara
    "Cankaya, Ankara":     (39.9111, 32.8526),
    "Kecioren, Ankara":    (39.9803, 32.8540),
    "Yenimahalle, Ankara": (39.9686, 32.7882),
    "Etimesgut, Ankara":   (39.9650, 32.6900),
    "Mamak, Ankara":       (39.9325, 32.9180),
    # Izmir
    "Konak, Izmir":        (38.4189, 27.1287),
    "Karsiyaka, Izmir":    (38.4598, 27.1099),
    "Bornova, Izmir":      (38.4690, 27.2200),
    "Buca, Izmir":         (38.3911, 27.1797),
    # Bursa
    "Nilufer, Bursa":      (40.2143, 28.9593),
    "Osmangazi, Bursa":    (40.1828, 29.0660),
    # Antalya
    "Muratpasa, Antalya":  (36.8849, 30.7136),
    "Kepez, Antalya":      (36.9221, 30.6899),
    # Aegean
    "Manisa":              (38.6191, 27.4289),
    "Aydin":               (37.8466, 27.8456),
    "Denizli":             (37.7765, 29.0864),
    # Mediterranean
    "Adana":               (37.0000, 35.3213),
    "Mersin":              (36.8121, 34.6415),
    "Hatay":               (36.2023, 36.1613),
    # Marmara
    "Kocaeli":             (40.8533, 29.8815),
    "Sakarya":             (40.7831, 30.4036),
    "Tekirdag":            (40.9833, 27.5167),
    # Black Sea
    "Trabzon":             (41.0027, 39.7168),
    "Samsun":              (41.2867, 36.3300),
    "Ordu":                (40.9839, 37.8764),
    # Central Anatolia
    "Konya":               (37.8746, 32.4932),
    "Kayseri":             (38.7330, 35.4853),
    "Eskisehir":           (39.7767, 30.5206),
    # Southeast
    "Gaziantep":           (37.0660, 37.3833),
    "Diyarbakir":          (37.9144, 40.2306),
    # East
    "Erzurum":             (39.9043, 41.2670),
    "Van":                 (38.4942, 43.4090),
    # College towns
    "Edirne":              (41.6764, 26.5557),
    "Canakkale":           (40.1467, 26.4086),
}


def apply_coordinates(cur) -> int:
    """Update lat/lon for every seed user based on their city string.

    Idempotent — re-runs are no-ops for already-matching rows.
    Returns the number of rows updated.
    """
    cur.execute(
        "SELECT id, city FROM users "
        "WHERE email LIKE %s OR email LIKE %s",
        ("%@seed.test", "%@seed.local"),
    )
    rows = cur.fetchall()
    n = 0
    for uid, city in rows:
        coords = CITY_COORDS.get(city)
        if not coords:
            continue
        lat, lon = coords
        cur.execute(
            "UPDATE users SET latitude=%s, longitude=%s WHERE id=%s",
            (lat, lon, uid),
        )
        n += 1
    return n
