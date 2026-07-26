#!/usr/bin/env python3
"""Synthetic Karnataka Police FIR dataset generator.

Emits scripts/fir-seed.sql — INSERTs for units (police stations), courts,
employees (IOs), cases, complainants, victims, accused, arrests, chargesheets
and act-section associations, matching sql-models/fir-schema.sql.

Load once (never in the app boot path):
    psql postgresql://crime_ai:crime_ai@localhost:5432/crime_ai -f scripts/fir-seed.sql

Deterministic (seeded) so re-generation is reproducible. Designed signal for the
demo: seasonality, district-clustered hotspots, repeat offenders (shared
person_uid across cases) and gang clusters (co-accused groups).
"""

import random
from datetime import date, datetime, timedelta
from pathlib import Path

random.seed(42)

OUT = Path(__file__).resolve().parent / "fir-seed.sql"

N_CASES = 18000
START = date(2019, 1, 1)
END = date(2026, 6, 30)

# district_id -> (name, HQ lat, HQ lng, population weight, n stations)
DISTRICTS = {
    1:  ("Bengaluru City", 12.9716, 77.5946, 30, 24),
    2:  ("Bengaluru Rural", 13.2846, 77.5700, 4, 6),
    3:  ("Mysuru", 12.2958, 76.6394, 8, 10),
    4:  ("Mangaluru City", 12.9141, 74.8560, 6, 8),
    5:  ("Belagavi", 15.8497, 74.4977, 6, 8),
    6:  ("Hubballi-Dharwad", 15.3647, 75.1240, 6, 8),
    7:  ("Kalaburagi", 17.3297, 76.8343, 5, 7),
    8:  ("Ballari", 15.1394, 76.9214, 4, 6),
    9:  ("Tumakuru", 13.3392, 77.1140, 4, 6),
    10: ("Shivamogga", 13.9299, 75.5681, 4, 6),
    11: ("Davanagere", 14.4644, 75.9218, 4, 5),
    12: ("Vijayapura", 16.8302, 75.7100, 3, 5),
    13: ("Raichur", 16.2076, 77.3463, 3, 5),
    14: ("Hassan", 13.0068, 76.1003, 3, 5),
    15: ("Mandya", 12.5218, 76.8951, 3, 5),
    16: ("Chitradurga", 14.2296, 76.3985, 2, 4),
    17: ("Kolar", 13.1360, 78.1298, 2, 4),
    18: ("Bidar", 17.9104, 77.5199, 2, 4),
    19: ("Bagalkot", 16.1691, 75.6615, 2, 4),
    20: ("Gadag", 15.4315, 75.6355, 2, 3),
    21: ("Haveri", 14.7935, 75.4045, 2, 3),
    22: ("Koppal", 15.3459, 76.1548, 2, 3),
    23: ("Yadgir", 16.7625, 77.1376, 2, 3),
    24: ("Chikkamagaluru", 13.3161, 75.7720, 2, 4),
    25: ("Chikkaballapur", 13.4355, 77.7315, 2, 3),
    26: ("Ramanagara", 12.7111, 77.2800, 2, 3),
    27: ("Chamarajanagar", 11.9236, 76.9456, 2, 3),
    28: ("Kodagu", 12.4218, 75.7400, 1, 3),
    29: ("Udupi", 13.3409, 74.7421, 2, 4),
    30: ("Uttara Kannada", 14.7937, 74.6869, 2, 4),
    31: ("Dakshina Kannada", 12.8438, 75.2479, 2, 4),
}

BLR_AREAS = ["Jayanagar", "Koramangala", "Indiranagar", "Whitefield", "Yelahanka",
             "Malleshwaram", "Rajajinagar", "Basavanagudi", "HSR Layout", "BTM Layout",
             "KR Puram", "Marathahalli", "Banashankari", "Vijayanagar", "Hebbal",
             "Electronic City", "Bommanahalli", "Peenya", "Yeshwanthpur", "Shivajinagar",
             "Ulsoor", "Frazer Town", "Kengeri", "Jalahalli"]

AREA_WORDS = ["Town", "Rural", "Extension", "Market", "Camp", "Gandhi Nagar", "New Town",
              "Fort", "East", "West", "North", "South", "Traffic"]

FIRST_M = ["Ravi", "Suresh", "Manjunath", "Prakash", "Kiran", "Santosh", "Raghavendra",
           "Venkatesh", "Mahesh", "Girish", "Harish", "Nagaraj", "Shivakumar", "Basavaraj",
           "Umesh", "Lokesh", "Anand", "Chandra", "Dinesh", "Ganesh", "Krishna", "Mohan",
           "Naveen", "Praveen", "Ramesh", "Srinivas", "Sunil", "Vijay", "Arun", "Deepak",
           "Imran", "Abdul", "Salman", "Farhan", "Joseph", "Anthony", "David", "Peter"]
FIRST_F = ["Lakshmi", "Savitha", "Manjula", "Rekha", "Sunitha", "Geetha", "Shobha",
           "Padma", "Kavitha", "Asha", "Radha", "Uma", "Vani", "Meena", "Anitha",
           "Bhavya", "Chaitra", "Divya", "Gowri", "Jyothi", "Ayesha", "Fatima", "Mary"]
LAST = ["Gowda", "Reddy", "Shetty", "Rao", "Kumar", "Naik", "Patil", "Hegde", "Poojari",
        "Achar", "Bhat", "Kulkarni", "Desai", "Shastri", "Murthy", "Swamy", "Khan",
        "Sheikh", "Dsouza", "Fernandes", "Nayak", "Angadi", "Biradar", "Chavan"]

# sub_head_id -> (crime_head_id, weight, gravity(1 heinous/2 non),
#                 [(act, section), ...], seasonal peak months, brief-facts template)
SUB_HEADS = {
    1:  (1, 2.0, 1, [("IPC", "302")], [], "The deceased {v} was found murdered near {place}. Accused {a} had a prior dispute with the deceased over {motive}."),
    2:  (1, 2.5, 1, [("IPC", "307")], [], "Accused {a} attacked {v} with a deadly weapon near {place} following a quarrel over {motive}."),
    3:  (1, 8.0, 2, [("IPC", "323"), ("IPC", "324"), ("IPC", "504"), ("IPC", "506")], [4, 5], "Complainant reported that accused {a} assaulted {v} near {place} over {motive}."),
    4:  (1, 2.0, 1, [("IPC", "363")], [], "Complainant reported that {v} was kidnapped from {place} by accused {a}."),
    5:  (2, 1.5, 1, [("IPC", "376")], [], "Complainant reported sexual assault on the victim by accused {a} at {place}."),
    6:  (2, 3.5, 2, [("IPC", "498A"), ("DP", "3"), ("DP", "4")], [], "Complainant {v} reported harassment by husband {a} and in-laws demanding additional dowry."),
    7:  (2, 3.0, 2, [("IPC", "354")], [8, 9], "Complainant {v} reported that accused {a} outraged her modesty near {place}."),
    8:  (3, 12.0, 2, [("IPC", "379")], [10, 11, 12], "Complainant reported theft of {property} from {place}. Unknown accused decamped with the property."),
    9:  (3, 9.0, 2, [("IPC", "379")], [10, 11, 12], "Complainant reported theft of a {vehicle} bearing registration KA-{reg} parked near {place}."),
    10: (3, 5.0, 2, [("IPC", "457"), ("IPC", "380")], [11, 12, 1], "During the night, unknown accused broke into the house of the complainant at {place} and committed theft of {property}."),
    11: (3, 3.0, 1, [("IPC", "392")], [], "Accused {a} waylaid {v} near {place} and robbed {property} at knifepoint."),
    12: (3, 1.0, 1, [("IPC", "395")], [], "A gang of accused persons committed dacoity at {place}, decamping with {property}."),
    13: (4, 6.0, 2, [("IPC", "420")], [], "Accused {a} cheated the complainant of Rs. {amount} on the pretext of {scheme}."),
    14: (4, 2.0, 2, [("IPC", "406")], [], "Accused {a}, entrusted with {property}, committed criminal breach of trust of Rs. {amount}."),
    15: (5, 6.0, 2, [("IT", "66D"), ("IPC", "420")], [], "Complainant reported online fraud of Rs. {amount}; the fraudster posed as {scheme} and induced transfer via UPI."),
    16: (5, 2.0, 2, [("IT", "66C")], [], "Complainant reported identity theft; accused misused complainant's documents and OTP to operate accounts."),
    17: (5, 1.5, 2, [("IT", "66"), ("IPC", "506")], [], "Complainant reported harassment and threats through social media by accused {a}."),
    18: (6, 3.0, 2, [("NDPS", "20")], [], "On credible information, police intercepted accused {a} near {place} and seized {contraband}."),
    19: (6, 1.5, 1, [("NDPS", "21"), ("NDPS", "22")], [], "Raid at {place} led to seizure of {contraband}; accused {a} was trafficking the contraband."),
    20: (7, 1.5, 1, [("POCSO", "4"), ("POCSO", "8")], [], "Complainant reported sexual offence against a minor by accused {a}; case registered under POCSO Act."),
    21: (8, 2.5, 2, [("IPC", "143"), ("IPC", "147")], [], "A group of persons formed an unlawful assembly at {place} and rioted over {motive}."),
    22: (8, 2.0, 2, [("GAMB", "79"), ("GAMB", "80")], [], "Raid on a gaming house at {place}; accused {a} and others were gambling with playing cards and cash."),
    23: (9, 6.0, 2, [("MV", "279"), ("IPC", "304A")], [], "Accused {a} drove a {vehicle} in a rash and negligent manner near {place}, injuring {v}."),
    24: (9, 3.0, 1, [("MV", "279"), ("IPC", "304A")], [], "A {vehicle} driven at high speed near {place} fatally knocked down {v}. Driver {a} fled the spot."),
    25: (10, 2.0, 2, [("EXCISE", "32")], [], "Raid at {place}; accused {a} was selling illicit liquor without licence. Contraband seized."),
    26: (10, 1.0, 1, [("ARMS", "25")], [], "Accused {a} was found in possession of an unlicensed country-made pistol near {place}."),
    27: (10, 1.0, 1, [("SCST", "3"), ("IPC", "323")], [], "Complainant {v} reported caste-based abuse and assault by accused {a} at {place}."),
    28: (10, 2.0, 2, [("KPA", "92")], [], "Accused {a} caused public nuisance at {place} in an inebriated condition."),
}

PROPERTY = ["gold ornaments worth Rs. 2,40,000", "a laptop and mobile phone",
            "cash of Rs. 85,000", "household articles", "copper wire and machinery parts",
            "silver articles worth Rs. 60,000"]
VEHICLES = ["motorcycle", "autorickshaw", "car", "goods tempo", "scooter"]
SCHEMES = ["a fake job offer", "an online trading scheme", "matrimonial fraud",
           "a fake loan app", "lottery prize money", "an OLX vehicle sale"]
CONTRABAND = ["2.5 kg of ganja", "48 grams of MDMA", "120 grams of charas",
              "psychotropic tablets", "5 kg of ganja concealed in a bag"]
MOTIVES = ["a money dispute", "a land dispute", "old enmity", "a drunken quarrel",
           "a family dispute", "a business rivalry"]

# Complainant occupation weighting by crime-head — a uniform randint(1,13) made Area 4's
# occupation-based socio-economic analysis spurious by construction (no signal to find). Weights
# are per occupation_id 1..13 (Farmer, Government Employee, Private Employee, Business, Daily Wage
# Labourer, Student, Homemaker, Driver, Unemployed, Retired, IT Professional, Shop Owner, Not Stated).
DEFAULT_OCC_WEIGHTS = [10, 8, 20, 12, 10, 8, 12, 5, 5, 5, 8, 5, 2]
OCC_WEIGHTS_BY_HEAD = {
    4: [3, 5, 10, 25, 3, 4, 4, 2, 2, 3, 20, 15, 2],   # Economic Offences: business/shop-owner/IT skew
    5: [2, 5, 15, 8, 2, 12, 3, 1, 2, 2, 35, 6, 2],    # Cyber Crimes: IT professional/student skew
    6: [8, 4, 15, 5, 20, 10, 3, 8, 15, 2, 4, 3, 3],   # Narcotics: daily-wage/unemployed skew
}


def occupation_weights(crime_head_id):
    return OCC_WEIGHTS_BY_HEAD.get(crime_head_id, DEFAULT_OCC_WEIGHTS)


ENTRY_METHODS = ["forcing open the rear window", "breaking the front door lock",
                 "cutting through the compound grill", "scaling the compound wall",
                 "removing a ventilator grill"]

# Month-of-year intensity multiplier so raw monthly case VOLUME varies (not just which sub-head is
# drawn) — previously rand_date() had no month term at all, so Holt-Winters had nothing seasonal to
# fit. Mild dip in the monsoon (Jun-Aug), uptick around the Oct-Dec festival/year-end period.
MONTH_INTENSITY = {1: 0.95, 2: 0.90, 3: 0.95, 4: 1.00, 5: 1.00, 6: 0.85,
                   7: 0.80, 8: 0.85, 9: 0.95, 10: 1.15, 11: 1.20, 12: 1.15}
_MAX_MONTH_INTENSITY = max(MONTH_INTENSITY.values())

# Time-of-day (hour-of-day, 0-23) weighting per crime sub-head — real MO signal derivable straight
# from the official IncidentFromDate column, no new field needed. Burglary/theft-in-dwelling cluster
# at night; street robbery/assault skew evening; cyber/economic offences skew business hours.
NIGHT_HEAVY_SUBHEADS = {10}                      # house break-in
EVENING_HEAVY_SUBHEADS = {2, 3, 7, 11, 21}        # attempt-to-murder, assault, outraging modesty, robbery, rioting
DAYTIME_HEAVY_SUBHEADS = {13, 14, 15, 16, 17}     # cheating/fraud, breach of trust, cyber offences


def esc(s):
    return s.replace("'", "''")


def name(gender=None):
    g = gender or (1 if random.random() < 0.82 else 2)
    first = random.choice(FIRST_M if g == 1 else FIRST_F)
    return f"{first} {random.choice(LAST)}", g


def rand_date():
    """Date weighted mildly upward over the years (crime reporting grows) AND by month-of-year
    intensity (rejection sampling), so total monthly volume has a real seasonal signal to fit."""
    span = (END - START).days
    while True:
        d = START + timedelta(days=int(span * (random.random() ** 0.85)))
        if random.random() < MONTH_INTENSITY[d.month] / _MAX_MONTH_INTENSITY:
            return d


def hour_for_subhead(sub):
    """Draw an incident hour-of-day correlated with the crime sub-head's typical MO."""
    if sub in NIGHT_HEAVY_SUBHEADS:
        return random.choices(range(24), weights=[3 if 0 <= h < 5 or h >= 22 else 1 for h in range(24)])[0]
    if sub in EVENING_HEAVY_SUBHEADS:
        return random.choices(range(24), weights=[3 if 18 <= h < 23 else 1 for h in range(24)])[0]
    if sub in DAYTIME_HEAVY_SUBHEADS:
        return random.choices(range(24), weights=[3 if 9 <= h < 18 else 1 for h in range(24)])[0]
    return random.randint(0, 23)


def pick_subhead(month):
    weights = []
    keys = list(SUB_HEADS)
    for k in keys:
        _, w, _, _, peaks, _ = SUB_HEADS[k]
        weights.append(w * (1.8 if month in peaks else 1.0))
    return random.choices(keys, weights=weights)[0]


rows = {t: [] for t in ["unit", "court", "employee", "case_master", "complainant_details",
                        "victim", "accused", "arrest_surrender", "act_section_association",
                        "chargesheet_details"]}

# ---------------- units, courts, employees ----------------
unit_id = 1000
court_id = 100
emp_id = 5000
district_units = {}
district_courts = {}
unit_names = {}
unit_ios = {}
# Each station gets its own coordinate, spread across the district rather than every station (and
# every case) sharing one district-HQ point — sub-district hotspots and map pins that actually
# match the station named in brief_facts require this.
station_coords = {}

for did, (dname, lat, lng, w, n_st) in DISTRICTS.items():
    court_id += 1
    district_courts[did] = court_id
    rows["court"].append(f"({court_id}, '{esc(dname)} District & Sessions Court', {did}, 1, TRUE)")
    stations = []
    for i in range(n_st):
        unit_id += 1
        if did == 1:
            area = BLR_AREAS[i % len(BLR_AREAS)]
        else:
            area = f"{dname.split()[0]} {AREA_WORDS[i % len(AREA_WORDS)]}"
        uname = f"{area} Police Station"
        rows["unit"].append(f"({unit_id}, '{esc(uname)}', {1 if did in (1, 4) else 2}, NULL, 1, 1, {did}, TRUE)")
        stations.append(unit_id)
        unit_names[unit_id] = uname
        # Station spread within the district (wider than the per-case jitter around it below).
        station_sd = 0.045 if did == 1 else 0.10
        station_coords[unit_id] = (round(random.gauss(lat, station_sd), 6),
                                    round(random.gauss(lng, station_sd), 6))
        ios = []
        for _ in range(3):
            emp_id += 1
            ename, g = name(1 if random.random() < 0.85 else 2)
            rank = random.choice([4, 4, 5])  # PSI, PSI, PI
            dob = date(random.randint(1975, 1995), random.randint(1, 12), random.randint(1, 28))
            appt = dob + timedelta(days=365 * random.randint(22, 30))
            rows["employee"].append(
                f"({emp_id}, {did}, {unit_id}, {rank}, 1, 'KGID{emp_id}', '{esc(ename)}', "
                f"'{dob}', {g}, {random.randint(1, 8)}, FALSE, '{appt}')")
            ios.append(emp_id)
        unit_ios[unit_id] = ios
    district_units[did] = stations

# ---------------- repeat offenders & gangs ----------------
offender_pool = {}   # district -> list of (uid, name, gender, byear)
uid_counter = 0
for did in DISTRICTS:
    pool = []
    for _ in range(30 + DISTRICTS[did][3] * 4):
        uid_counter += 1
        # Repeat-offender pool skews male (consistent with real crime demographics) but is not
        # all-male — forcing gender=1 here made every one of the ~1,300 repeat offenders male,
        # leaving accused-gender analysis degenerate on one dimension.
        nm, g = name(1 if random.random() < 0.88 else 2)
        pool.append((f"P{uid_counter:06d}", nm, g, random.randint(1970, 2004)))
    offender_pool[did] = pool

gangs = []  # (district, [members], preferred sub-heads)
for _ in range(40):
    did = random.choices(list(DISTRICTS), weights=[d[3] for d in DISTRICTS.values()])[0]
    members = random.sample(offender_pool[did], k=random.randint(3, 6))
    gangs.append((did, members, random.choice([[8, 9, 10], [11, 12], [18, 19], [13, 15]])))

# ---------------- cases ----------------
case_id = 0
comp_id = 0
vict_id = 0
acc_id = 0
arrest_id = 0
cs_id = 0
serials = {}

district_ids = list(DISTRICTS)
district_weights = [d[3] for d in DISTRICTS.values()]

for _ in range(N_CASES):
    case_id += 1
    d = rand_date()
    did = random.choices(district_ids, weights=district_weights)[0]
    dname, dlat, dlng, _, _ = DISTRICTS[did]

    gang = None
    if random.random() < 0.06:
        for g in random.sample(gangs, len(gangs)):
            if g[0] == did:
                gang = g
                break
    sub = random.choice(gang[2]) if gang else pick_subhead(d.month)
    head, _, gravity, sections, _, template = SUB_HEADS[sub]

    station = random.choice(district_units[did])
    io = random.choice(unit_ios[station])
    category = random.choices([1, 2, 3, 4], weights=[88, 6, 3, 3])[0]

    year = d.year
    key = (station, category, year)
    serials[key] = serials.get(key, 0) + 1
    serial = serials[key]
    crime_no = f"{category}{did:04d}{station:04d}{year}{serial:05d}"
    case_no = f"{year}{serial:05d}"

    # Incident location: tight gaussian jitter around the CASE'S OWN STATION (not the district HQ),
    # so sub-district hotspots exist and map pins land near the station named in brief_facts.
    slat, slng = station_coords[station]
    lat = round(random.gauss(slat, 0.006), 6)
    lng = round(random.gauss(slng, 0.006), 6)

    # Incident hour-of-day correlated with the sub-head's typical MO (see hour_for_subhead) — a
    # real, derivable signal straight off the official IncidentFromDate column.
    hour = hour_for_subhead(sub)
    inc_from = datetime.combine(d - timedelta(days=random.randint(0, 3)),
                                datetime.min.time()) + timedelta(hours=hour, minutes=random.randint(0, 59))
    inc_to = inc_from + timedelta(minutes=random.randint(10, 360))
    info = inc_to + timedelta(minutes=random.randint(30, 2880))

    # ---- parties ----
    vname, vg = name()
    accused_list = []
    if gang:
        members = random.sample(gang[1], k=random.randint(2, min(4, len(gang[1]))))
        for uid, nm, g, byear in members:
            accused_list.append((uid, nm, g, d.year - byear))
    else:
        n_acc = random.choices([0, 1, 2, 3], weights=[18, 60, 16, 6])[0]
        for _ in range(n_acc):
            if random.random() < 0.25:
                uid, nm, g, byear = random.choice(offender_pool[did])
                accused_list.append((uid, nm, g, d.year - byear))
            else:
                nm, g = name(1 if random.random() < 0.85 else 2)
                accused_list.append((None, nm, g, random.randint(18, 60)))

    place = unit_names[station].replace(" Police Station", "")
    # Named locals (not inline random.choice() calls inside .format()'s kwargs) so the exact MO
    # vocabulary picked is available beyond the formatted string if a later pass wants it, and so
    # it's obvious which draws are actually used by any given template.
    motive_word = random.choice(MOTIVES)
    property_word = random.choice(PROPERTY)
    vehicle_word = random.choice(VEHICLES)
    reg_word = f"{random.randint(1, 60):02d}-{random.choice('ABCDEFGH')}-{random.randint(1000, 9999)}"
    amount_word = f"{random.randint(20, 900) * 1000:,}"
    scheme_word = random.choice(SCHEMES)
    contraband_word = random.choice(CONTRABAND)
    facts = template.format(
        v=vname, a=accused_list[0][1] if accused_list else "an unknown person",
        place=f"{place}, {dname}", property=property_word,
        vehicle=vehicle_word, reg=reg_word,
        amount=amount_word, scheme=scheme_word,
        contraband=contraband_word, motive=motive_word)
    if sub == 10:
        facts += f" Entry was made by {random.choice(ENTRY_METHODS)}."

    # ---- outcome ----
    # Previously a single flat random.random() branch independent of crime type, district or
    # whether anyone was identified — conviction rate was ~11% uniformly everywhere, making any
    # outcome-comparison feature show flat noise. Now: cases with an identified accused resolve far
    # more often than "unknown accused" property crimes, districts have a deterministic clearance-
    # quality factor, and heinous cases skew toward a longer pending-trial/appeal tail.
    age_days = (END - d).days
    identified = len(accused_list) > 0
    district_clearance = 0.7 + 0.5 * ((did * 7) % 11) / 10   # deterministic per-district, 0.7-1.2
    chargesheet_p = min((0.62 if identified else 0.18) * district_clearance, 0.85)
    false_case_p = 0.05
    undetected_p = 0.12 if identified else 0.55
    if age_days < 90:
        status, cstype = 1, None
    else:
        r = random.random()
        if r < chargesheet_p:
            if gravity == 1:
                status = random.choices([2, 3, 4, 5], weights=[20, 35, 25, 20])[0]
            else:
                status = random.choices([2, 3, 4, 5], weights=[15, 20, 45, 20])[0]
            cstype = 'A'
        elif r < chargesheet_p + false_case_p:
            status, cstype = 6, 'B'
        elif r < chargesheet_p + false_case_p + undetected_p:
            status, cstype = 7, 'C'
        else:
            status, cstype = 1, None

    rows["case_master"].append(
        f"({case_id}, '{crime_no}', '{case_no}', '{d}', {io}, {station}, {category}, {gravity}, "
        f"{head}, {sub}, {status}, {district_courts[did]}, '{inc_from}', '{inc_to}', '{info}', "
        f"{lat}, {lng}, '{esc(facts)}')")

    comp_id += 1
    cname, cg = name()
    occ_id = random.choices(range(1, 14), weights=occupation_weights(head))[0]
    rows["complainant_details"].append(
        f"({comp_id}, {case_id}, '{esc(cname)}', {random.randint(18, 70)}, "
        f"{occ_id}, {random.choices([1, 2, 3, 4, 5, 6, 7], weights=[62, 18, 6, 3, 2, 2, 7])[0]}, "
        f"{random.choices([1, 2, 3, 4, 5], weights=[30, 35, 18, 9, 8])[0]}, {cg})")

    if sub not in (22, 25, 28):  # victimless offences
        vict_id += 1
        rows["victim"].append(
            f"({vict_id}, {case_id}, '{esc(vname)}', {random.randint(12, 75)}, {vg}, '0')")

    for idx, (uid, nm, g, age) in enumerate(accused_list, start=1):
        acc_id += 1
        uid_sql = f"'{uid}'" if uid else "NULL"
        rows["accused"].append(
            f"({acc_id}, {case_id}, '{esc(nm)}', {max(18, min(age, 75))}, {g}, 'A{idx}', {uid_sql})")
        if cstype == 'A' or (cstype is None and random.random() < 0.5 and status != 1) or \
           (status in (2, 3, 4, 5)) or (accused_list and random.random() < 0.55):
            arrest_id += 1
            adate = d + timedelta(days=random.randint(1, 120))
            if adate <= END:
                rows["arrest_surrender"].append(
                    f"({arrest_id}, {case_id}, {1 if random.random() < 0.9 else 2}, '{adate}', 1, {did}, "
                    f"{station}, {io}, {district_courts[did]}, {acc_id}, TRUE, FALSE)")

    for a_ord, (act, sec) in enumerate(sections, start=1):
        rows["act_section_association"].append(
            f"({case_id}, '{act}', '{sec}', {a_ord}, {a_ord})")

    if cstype:
        cs_id += 1
        csdate = datetime.combine(d + timedelta(days=random.randint(30, 300)), datetime.min.time())
        rows["chargesheet_details"].append(
            f"({cs_id}, {case_id}, '{csdate}', '{cstype}', {io})")

# ---------------- emit SQL ----------------
COLS = {
    "unit": "(unit_id, unit_name, type_id, parent_unit, nationality_id, state_id, district_id, active)",
    "court": "(court_id, court_name, district_id, state_id, active)",
    "employee": "(employee_id, district_id, unit_id, rank_id, designation_id, kgid, first_name, employee_dob, gender_id, blood_group_id, physically_challenged, appointment_date)",
    "case_master": "(case_master_id, crime_no, case_no, crime_registered_date, police_person_id, police_station_id, case_category_id, gravity_offence_id, crime_major_head_id, crime_minor_head_id, case_status_id, court_id, incident_from_date, incident_to_date, info_received_ps_date, latitude, longitude, brief_facts)",
    "complainant_details": "(complainant_id, case_master_id, complainant_name, age_year, occupation_id, religion_id, caste_id, gender_id)",
    "victim": "(victim_master_id, case_master_id, victim_name, age_year, gender_id, victim_police)",
    "accused": "(accused_master_id, case_master_id, accused_name, age_year, gender_id, person_id, person_uid)",
    "arrest_surrender": "(arrest_surrender_id, case_master_id, arrest_surrender_type_id, arrest_surrender_date, arrest_surrender_state_id, arrest_surrender_district_id, police_station_id, io_id, court_id, accused_master_id, is_accused, is_complainant_accused)",
    "act_section_association": "(case_master_id, act_code, section_code, act_order_id, section_order_id)",
    "chargesheet_details": "(cs_id, case_master_id, cs_date, cs_type, police_person_id)",
}
CONFLICT = {
    "unit": " ON CONFLICT (unit_id) DO NOTHING",
    "court": " ON CONFLICT (court_id) DO NOTHING",
    "employee": " ON CONFLICT (employee_id) DO NOTHING",
    "case_master": " ON CONFLICT (case_master_id) DO NOTHING",
    "complainant_details": " ON CONFLICT (complainant_id) DO NOTHING",
    "victim": " ON CONFLICT (victim_master_id) DO NOTHING",
    "accused": " ON CONFLICT (accused_master_id) DO NOTHING",
    "arrest_surrender": " ON CONFLICT (arrest_surrender_id) DO NOTHING",
    "act_section_association": "",
    "chargesheet_details": " ON CONFLICT (cs_id) DO NOTHING",
}

with OUT.open("w") as f:
    f.write("-- Generated by scripts/generate_fir_data.py — load once with psql -f\n")
    f.write("BEGIN;\n")
    for table, values in rows.items():
        f.write(f"\n-- {table}: {len(values)} rows\n")
        for i in range(0, len(values), 500):
            batch = ",\n".join(values[i:i + 500])
            f.write(f"INSERT INTO {table} {COLS[table]} VALUES\n{batch}{CONFLICT[table]};\n")
    f.write("COMMIT;\nANALYZE;\n")

print(f"Wrote {OUT}")
for table, values in rows.items():
    print(f"  {table:26s} {len(values):>7,} rows")
