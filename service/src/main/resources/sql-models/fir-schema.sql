-- ============================================================================
-- Karnataka Police FIR schema (translated from the SQL Server ER diagram).
-- DDL + small enumerable master data only — the bulk synthetic dataset
-- (units, employees, courts, cases, parties) is loaded once via
-- scripts/fir-seed.sql, NOT on boot.
-- Type mapping: NVARCHAR(MAX)->TEXT, BIT->BOOLEAN, DATETIME->TIMESTAMP.
-- ============================================================================

-- ---------- Geography / organisation ----------

CREATE TABLE IF NOT EXISTS state (
    state_id       INT PRIMARY KEY,
    state_name     VARCHAR(100) NOT NULL,
    nationality_id INT,
    active         BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE TABLE IF NOT EXISTS district (
    district_id   INT PRIMARY KEY,
    district_name VARCHAR(100) NOT NULL,
    state_id      INT REFERENCES state (state_id),
    active        BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE TABLE IF NOT EXISTS unit_type (
    unit_type_id   INT PRIMARY KEY,
    unit_type_name VARCHAR(100) NOT NULL,
    city_dist_state VARCHAR(20),
    hierarchy      INT,
    active         BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE TABLE IF NOT EXISTS unit (
    unit_id        INT PRIMARY KEY,
    unit_name      VARCHAR(200) NOT NULL,
    type_id        INT REFERENCES unit_type (unit_type_id),
    parent_unit    INT,
    nationality_id INT,
    state_id       INT REFERENCES state (state_id),
    district_id    INT REFERENCES district (district_id),
    active         BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE TABLE IF NOT EXISTS court (
    court_id    INT PRIMARY KEY,
    court_name  VARCHAR(200) NOT NULL,
    district_id INT REFERENCES district (district_id),
    state_id    INT REFERENCES state (state_id),
    active      BOOLEAN NOT NULL DEFAULT TRUE
);

-- ---------- Police personnel ----------

CREATE TABLE IF NOT EXISTS rank (
    rank_id   INT PRIMARY KEY,
    rank_name VARCHAR(100) NOT NULL,
    hierarchy INT,
    active    BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE TABLE IF NOT EXISTS designation (
    designation_id   INT PRIMARY KEY,
    designation_name VARCHAR(100) NOT NULL,
    active           BOOLEAN NOT NULL DEFAULT TRUE,
    sort_order       INT
);

CREATE TABLE IF NOT EXISTS employee (
    employee_id           INT PRIMARY KEY,
    district_id           INT REFERENCES district (district_id),
    unit_id               INT REFERENCES unit (unit_id),
    rank_id               INT REFERENCES rank (rank_id),
    designation_id        INT REFERENCES designation (designation_id),
    kgid                  VARCHAR(30),
    first_name            VARCHAR(150),
    employee_dob          DATE,
    gender_id             INT,
    blood_group_id        INT,
    physically_challenged BOOLEAN NOT NULL DEFAULT FALSE,
    appointment_date      DATE
);

-- ---------- Legal classification ----------

CREATE TABLE IF NOT EXISTS act (
    act_code        VARCHAR(30) PRIMARY KEY,
    act_description VARCHAR(300),
    short_name      VARCHAR(60),
    active          BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE TABLE IF NOT EXISTS section (
    act_code            VARCHAR(30) NOT NULL REFERENCES act (act_code),
    section_code        VARCHAR(30) NOT NULL,
    section_description VARCHAR(400),
    active              BOOLEAN NOT NULL DEFAULT TRUE,
    PRIMARY KEY (act_code, section_code)
);

CREATE TABLE IF NOT EXISTS crime_head (
    crime_head_id    INT PRIMARY KEY,
    crime_group_name VARCHAR(150) NOT NULL,
    active           BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE TABLE IF NOT EXISTS crime_sub_head (
    crime_sub_head_id INT PRIMARY KEY,
    crime_head_id     INT REFERENCES crime_head (crime_head_id),
    crime_head_name   VARCHAR(150) NOT NULL,
    seq_id            INT
);

CREATE TABLE IF NOT EXISTS crime_head_act_section (
    crime_head_id INT REFERENCES crime_head (crime_head_id),
    act_code      VARCHAR(30),
    section_code  VARCHAR(30)
);

-- ---------- Person lookup masters ----------

CREATE TABLE IF NOT EXISTS gender_master (
    gender_id   INT PRIMARY KEY,
    gender_name VARCHAR(30) NOT NULL
);

CREATE TABLE IF NOT EXISTS caste_master (
    caste_master_id   INT PRIMARY KEY,
    caste_master_name VARCHAR(100) NOT NULL
);

CREATE TABLE IF NOT EXISTS religion_master (
    religion_id   INT PRIMARY KEY,
    religion_name VARCHAR(100) NOT NULL
);

CREATE TABLE IF NOT EXISTS occupation_master (
    occupation_id   INT PRIMARY KEY,
    occupation_name VARCHAR(120) NOT NULL
);

CREATE TABLE IF NOT EXISTS case_status_master (
    case_status_id   INT PRIMARY KEY,
    case_status_name VARCHAR(100) NOT NULL
);

CREATE TABLE IF NOT EXISTS case_category (
    case_category_id INT PRIMARY KEY,
    lookup_value     VARCHAR(50) NOT NULL
);

CREATE TABLE IF NOT EXISTS gravity_offence (
    gravity_offence_id INT PRIMARY KEY,
    lookup_value       VARCHAR(50) NOT NULL
);

-- ---------- Core case tables ----------

CREATE TABLE IF NOT EXISTS case_master (
    case_master_id        INT PRIMARY KEY,
    crime_no              VARCHAR(30) NOT NULL,
    case_no               VARCHAR(20),
    crime_registered_date DATE,
    police_person_id      INT REFERENCES employee (employee_id),
    police_station_id     INT REFERENCES unit (unit_id),
    case_category_id      INT REFERENCES case_category (case_category_id),
    gravity_offence_id    INT REFERENCES gravity_offence (gravity_offence_id),
    crime_major_head_id   INT REFERENCES crime_head (crime_head_id),
    crime_minor_head_id   INT REFERENCES crime_sub_head (crime_sub_head_id),
    case_status_id        INT REFERENCES case_status_master (case_status_id),
    court_id              INT REFERENCES court (court_id),
    incident_from_date    TIMESTAMP,
    incident_to_date      TIMESTAMP,
    info_received_ps_date TIMESTAMP,
    latitude              NUMERIC(9, 6),
    longitude             NUMERIC(9, 6),
    brief_facts           TEXT
);

CREATE INDEX IF NOT EXISTS idx_case_master_station_date ON case_master (police_station_id, crime_registered_date);
CREATE INDEX IF NOT EXISTS idx_case_master_head_date ON case_master (crime_major_head_id, crime_registered_date);
CREATE INDEX IF NOT EXISTS idx_case_master_crime_no ON case_master (crime_no);

CREATE TABLE IF NOT EXISTS complainant_details (
    complainant_id   INT PRIMARY KEY,
    case_master_id   INT REFERENCES case_master (case_master_id),
    complainant_name VARCHAR(200),
    age_year         INT,
    occupation_id    INT REFERENCES occupation_master (occupation_id),
    religion_id      INT REFERENCES religion_master (religion_id),
    caste_id         INT REFERENCES caste_master (caste_master_id),
    gender_id        INT
);

CREATE TABLE IF NOT EXISTS victim (
    victim_master_id INT PRIMARY KEY,
    case_master_id   INT REFERENCES case_master (case_master_id),
    victim_name      VARCHAR(200),
    age_year         INT,
    gender_id        INT,
    victim_police    VARCHAR(5)
);

CREATE INDEX IF NOT EXISTS idx_victim_case ON victim (case_master_id);

CREATE TABLE IF NOT EXISTS accused (
    accused_master_id INT PRIMARY KEY,
    case_master_id    INT REFERENCES case_master (case_master_id),
    accused_name      VARCHAR(200),
    age_year          INT,
    gender_id         INT,
    person_id         VARCHAR(10),
    -- Synthetic cross-case identity: the same real-world person carries the same
    -- person_uid in every case they appear in (repeat offenders, gang networks).
    person_uid        VARCHAR(40)
);

CREATE INDEX IF NOT EXISTS idx_accused_case ON accused (case_master_id);
CREATE INDEX IF NOT EXISTS idx_accused_person_uid ON accused (person_uid);

CREATE TABLE IF NOT EXISTS arrest_surrender (
    arrest_surrender_id       INT PRIMARY KEY,
    case_master_id            INT REFERENCES case_master (case_master_id),
    arrest_surrender_type_id  INT,
    arrest_surrender_date     DATE,
    arrest_surrender_state_id INT REFERENCES state (state_id),
    arrest_surrender_district_id INT REFERENCES district (district_id),
    police_station_id         INT REFERENCES unit (unit_id),
    io_id                     INT REFERENCES employee (employee_id),
    court_id                  INT REFERENCES court (court_id),
    -- Kept for the single-accused-per-arrest-row case the seed generator produces. The official ER
    -- diagram's relationship matrix ALSO documents inv_arrestsurrenderaccused as a junction ("one
    -- arrest event can link multiple accused") — use that table, not this column, when an arrest
    -- covers more than one accused (e.g. a multi-person raid recorded as one event).
    accused_master_id         INT REFERENCES accused (accused_master_id),
    is_accused                BOOLEAN,
    is_complainant_accused    BOOLEAN
);

-- Official junction table (named in the ER diagram's relationship matrix; no column-level
-- definition was provided there, only its existence and cardinality — see
-- documents/SCHEMA_FIDELITY.md). Lets one arrest/surrender event link multiple accused, which a
-- direct arrest_surrender.accused_master_id FK cannot express.
CREATE TABLE IF NOT EXISTS inv_arrestsurrenderaccused (
    inv_arrestsurrenderaccused_id INT PRIMARY KEY,
    arrest_surrender_id           INT REFERENCES arrest_surrender (arrest_surrender_id),
    accused_master_id             INT REFERENCES accused (accused_master_id),
    is_primary_accused            BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE INDEX IF NOT EXISTS idx_isaa_arrest ON inv_arrestsurrenderaccused (arrest_surrender_id);
CREATE INDEX IF NOT EXISTS idx_isaa_accused ON inv_arrestsurrenderaccused (accused_master_id);

-- Official 1:1 with CaseMaster (ER diagram relationship matrix: "One FIR has one occurrence
-- time/location record"). No column-level definition was provided in the source document beyond
-- this table's name and cardinality — occurance_time/place_of_occurance/is_public_place below are
-- a best-effort reconstruction from the table's stated purpose, not a literal transcription; see
-- documents/SCHEMA_FIDELITY.md.
CREATE TABLE IF NOT EXISTS inv_occurance_time (
    case_master_id     INT PRIMARY KEY REFERENCES case_master (case_master_id),
    occurance_time     TIME,
    place_of_occurance VARCHAR(300),
    is_public_place    BOOLEAN
);

CREATE TABLE IF NOT EXISTS act_section_association (
    case_master_id   INT REFERENCES case_master (case_master_id),
    act_code         VARCHAR(30),
    section_code     VARCHAR(30),
    act_order_id     INT,
    section_order_id INT
);

CREATE INDEX IF NOT EXISTS idx_asa_case ON act_section_association (case_master_id);

CREATE TABLE IF NOT EXISTS chargesheet_details (
    cs_id            INT PRIMARY KEY,
    case_master_id   INT REFERENCES case_master (case_master_id),
    cs_date          TIMESTAMP,
    -- A -> Chargesheet, B -> False Case, C -> Undetected
    cs_type          CHAR(1),
    police_person_id INT REFERENCES employee (employee_id)
);

-- ---------- Offender risk scoring (Req: criminology-based profiling) ----------
-- Defined in entity-resolution.sql (which runs immediately after this file in
-- ServiceConfig.crimeDataSourceInitializer), not here. It used to be a CREATE OR REPLACE VIEW in
-- THIS file, sourced from accused.person_uid directly — Phase 1.1 repointed it at accused_identity
-- (the derived cross-case entity, since accused.person_uid is a synthetic ground-truth-only
-- column with no real-data equivalent) and added an identity_confidence column. Postgres's
-- CREATE OR REPLACE VIEW cannot drop/reorder a view's existing columns — since this file runs on
-- EVERY boot (spring.sql.init.mode: always) and entity-resolution.sql's version has one more
-- column than this file's old version did, leaving both definitions around meant every SECOND
-- boot (once the view already had the extra column) failed with "cannot drop columns from view".
-- One authoritative definition, not two, is required for this specific reason: DROP VIEW IF
-- EXISTS + CREATE VIEW (entity-resolution.sql's approach) tolerates a shape change; CREATE OR
-- REPLACE VIEW does not.

-- ============================================================================
-- Master data (idempotent)
-- ============================================================================

INSERT INTO state (state_id, state_name, nationality_id) VALUES
    (1, 'Karnataka', 1),
    (2, 'Maharashtra', 1),
    (3, 'Tamil Nadu', 1),
    (4, 'Andhra Pradesh', 1),
    (5, 'Telangana', 1),
    (6, 'Kerala', 1),
    (7, 'Goa', 1)
ON CONFLICT (state_id) DO NOTHING;

INSERT INTO district (district_id, district_name, state_id) VALUES
    (1,  'Bengaluru City', 1),
    (2,  'Bengaluru Rural', 1),
    (3,  'Mysuru', 1),
    (4,  'Mangaluru City', 1),
    (5,  'Belagavi', 1),
    (6,  'Hubballi-Dharwad', 1),
    (7,  'Kalaburagi', 1),
    (8,  'Ballari', 1),
    (9,  'Tumakuru', 1),
    (10, 'Shivamogga', 1),
    (11, 'Davanagere', 1),
    (12, 'Vijayapura', 1),
    (13, 'Raichur', 1),
    (14, 'Hassan', 1),
    (15, 'Mandya', 1),
    (16, 'Chitradurga', 1),
    (17, 'Kolar', 1),
    (18, 'Bidar', 1),
    (19, 'Bagalkot', 1),
    (20, 'Gadag', 1),
    (21, 'Haveri', 1),
    (22, 'Koppal', 1),
    (23, 'Yadgir', 1),
    (24, 'Chikkamagaluru', 1),
    (25, 'Chikkaballapur', 1),
    (26, 'Ramanagara', 1),
    (27, 'Chamarajanagar', 1),
    (28, 'Kodagu', 1),
    (29, 'Udupi', 1),
    (30, 'Uttara Kannada', 1),
    (31, 'Dakshina Kannada', 1)
ON CONFLICT (district_id) DO NOTHING;

-- Added-beyond-spec (see documents/SCHEMA_FIDELITY.md): the official schema has no Kannada name
-- column for District, but district names are the one place free-text Kannada chat input has to
-- match a fixed catalog value (e.g. a user typing "ಬೆಂಗಳೂರು" for "Bengaluru"), and there are only
-- 31 of them — a small hardcoded transliteration table, not a general i18n mechanism.
ALTER TABLE district ADD COLUMN IF NOT EXISTS district_name_kn VARCHAR(100);
UPDATE district SET district_name_kn = v.name_kn FROM (VALUES
    (1,  'ಬೆಂಗಳೂರು ನಗರ'),
    (2,  'ಬೆಂಗಳೂರು ಗ್ರಾಮಾಂತರ'),
    (3,  'ಮೈಸೂರು'),
    (4,  'ಮಂಗಳೂರು'),
    (5,  'ಬೆಳಗಾವಿ'),
    (6,  'ಹುಬ್ಬಳ್ಳಿ-ಧಾರವಾಡ'),
    (7,  'ಕಲಬುರಗಿ'),
    (8,  'ಬಳ್ಳಾರಿ'),
    (9,  'ತುಮಕೂರು'),
    (10, 'ಶಿವಮೊಗ್ಗ'),
    (11, 'ದಾವಣಗೆರೆ'),
    (12, 'ವಿಜಯಪುರ'),
    (13, 'ರಾಯಚೂರು'),
    (14, 'ಹಾಸನ'),
    (15, 'ಮಂಡ್ಯ'),
    (16, 'ಚಿತ್ರದುರ್ಗ'),
    (17, 'ಕೋಲಾರ'),
    (18, 'ಬೀದರ್'),
    (19, 'ಬಾಗಲಕೋಟೆ'),
    (20, 'ಗದಗ'),
    (21, 'ಹಾವೇರಿ'),
    (22, 'ಕೊಪ್ಪಳ'),
    (23, 'ಯಾದಗಿರಿ'),
    (24, 'ಚಿಕ್ಕಮಗಳೂರು'),
    (25, 'ಚಿಕ್ಕಬಳ್ಳಾಪುರ'),
    (26, 'ರಾಮನಗರ'),
    (27, 'ಚಾಮರಾಜನಗರ'),
    (28, 'ಕೊಡಗು'),
    (29, 'ಉಡುಪಿ'),
    (30, 'ಉತ್ತರ ಕನ್ನಡ'),
    (31, 'ದಕ್ಷಿಣ ಕನ್ನಡ')
) AS v(district_id, name_kn) WHERE district.district_id = v.district_id;

INSERT INTO unit_type (unit_type_id, unit_type_name, city_dist_state, hierarchy) VALUES
    (1, 'Police Station', 'City', 5),
    (2, 'Police Station', 'District', 5),
    (3, 'Circle Office', 'District', 4),
    (4, 'Sub-Division Police Office', 'District', 3),
    (5, 'District Police Office', 'District', 2),
    (6, 'City Commissionerate', 'City', 1)
ON CONFLICT (unit_type_id) DO NOTHING;

INSERT INTO rank (rank_id, rank_name, hierarchy) VALUES
    (1,  'Police Constable', 11),
    (2,  'Head Constable', 10),
    (3,  'Assistant Sub-Inspector', 9),
    (4,  'Police Sub-Inspector', 8),
    (5,  'Police Inspector', 7),
    (6,  'Deputy Superintendent of Police', 6),
    (7,  'Superintendent of Police', 5),
    (8,  'Deputy Inspector General', 4),
    (9,  'Inspector General', 3),
    (10, 'Additional Director General', 2),
    (11, 'Director General of Police', 1)
ON CONFLICT (rank_id) DO NOTHING;

INSERT INTO designation (designation_id, designation_name, sort_order) VALUES
    (1, 'Investigating Officer', 1),
    (2, 'Station House Officer', 2),
    (3, 'Circle Inspector', 3),
    (4, 'Sub-Divisional Police Officer', 4),
    (5, 'Superintendent of Police', 5)
ON CONFLICT (designation_id) DO NOTHING;

INSERT INTO gender_master (gender_id, gender_name) VALUES
    (1, 'Male'), (2, 'Female'), (3, 'Transgender')
ON CONFLICT (gender_id) DO NOTHING;

INSERT INTO caste_master (caste_master_id, caste_master_name) VALUES
    (1, 'General'), (2, 'OBC'), (3, 'SC'), (4, 'ST'), (5, 'Not Stated')
ON CONFLICT (caste_master_id) DO NOTHING;

INSERT INTO religion_master (religion_id, religion_name) VALUES
    (1, 'Hindu'), (2, 'Muslim'), (3, 'Christian'), (4, 'Jain'),
    (5, 'Buddhist'), (6, 'Sikh'), (7, 'Not Stated')
ON CONFLICT (religion_id) DO NOTHING;

INSERT INTO occupation_master (occupation_id, occupation_name) VALUES
    (1, 'Farmer'), (2, 'Government Employee'), (3, 'Private Employee'),
    (4, 'Business'), (5, 'Daily Wage Labourer'), (6, 'Student'),
    (7, 'Homemaker'), (8, 'Driver'), (9, 'Unemployed'), (10, 'Retired'),
    (11, 'IT Professional'), (12, 'Shop Owner'), (13, 'Not Stated')
ON CONFLICT (occupation_id) DO NOTHING;

INSERT INTO case_status_master (case_status_id, case_status_name) VALUES
    (1, 'Under Investigation'),
    (2, 'Charge Sheeted'),
    (3, 'Pending Trial'),
    (4, 'Convicted'),
    (5, 'Acquitted'),
    (6, 'Closed - False Case'),
    (7, 'Closed - Undetected')
ON CONFLICT (case_status_id) DO NOTHING;

INSERT INTO case_category (case_category_id, lookup_value) VALUES
    (1, 'FIR'), (2, 'UDR'), (3, 'Zero FIR'), (4, 'PAR')
ON CONFLICT (case_category_id) DO NOTHING;

INSERT INTO gravity_offence (gravity_offence_id, lookup_value) VALUES
    (1, 'Heinous'), (2, 'Non-Heinous')
ON CONFLICT (gravity_offence_id) DO NOTHING;

INSERT INTO act (act_code, act_description, short_name) VALUES
    ('IPC',    'Indian Penal Code, 1860', 'IPC'),
    ('BNS',    'Bharatiya Nyaya Sanhita, 2023', 'BNS'),
    ('NDPS',   'Narcotic Drugs and Psychotropic Substances Act, 1985', 'NDPS Act'),
    ('ARMS',   'Arms Act, 1959', 'Arms Act'),
    ('POCSO',  'Protection of Children from Sexual Offences Act, 2012', 'POCSO'),
    ('IT',     'Information Technology Act, 2000', 'IT Act'),
    ('KPA',    'Karnataka Police Act, 1963', 'KP Act'),
    ('MV',     'Motor Vehicles Act, 1988', 'MV Act'),
    ('EXCISE', 'Karnataka Excise Act, 1965', 'Excise Act'),
    ('DP',     'Dowry Prohibition Act, 1961', 'DP Act'),
    ('SCST',   'SC/ST (Prevention of Atrocities) Act, 1989', 'SC/ST Act'),
    ('GAMB',   'Karnataka Prevention of Gambling Act, 1963', 'Gambling Act')
ON CONFLICT (act_code) DO NOTHING;

INSERT INTO section (act_code, section_code, section_description) VALUES
    ('IPC', '302',  'Murder'),
    ('IPC', '307',  'Attempt to murder'),
    ('IPC', '304A', 'Causing death by negligence'),
    ('IPC', '323',  'Voluntarily causing hurt'),
    ('IPC', '324',  'Voluntarily causing hurt by dangerous weapons'),
    ('IPC', '326',  'Voluntarily causing grievous hurt by dangerous weapons'),
    ('IPC', '341',  'Wrongful restraint'),
    ('IPC', '354',  'Assault on woman with intent to outrage modesty'),
    ('IPC', '363',  'Kidnapping'),
    ('IPC', '376',  'Rape'),
    ('IPC', '379',  'Theft'),
    ('IPC', '380',  'Theft in dwelling house'),
    ('IPC', '392',  'Robbery'),
    ('IPC', '395',  'Dacoity'),
    ('IPC', '406',  'Criminal breach of trust'),
    ('IPC', '420',  'Cheating and dishonestly inducing delivery of property'),
    ('IPC', '447',  'Criminal trespass'),
    ('IPC', '457',  'Lurking house-trespass by night'),
    ('IPC', '498A', 'Cruelty by husband or relatives of husband'),
    ('IPC', '504',  'Intentional insult to provoke breach of peace'),
    ('IPC', '506',  'Criminal intimidation'),
    ('IPC', '143',  'Unlawful assembly'),
    ('IPC', '147',  'Rioting'),
    ('NDPS', '20',  'Contravention in relation to cannabis'),
    ('NDPS', '21',  'Contravention in relation to manufactured drugs'),
    ('NDPS', '22',  'Contravention in relation to psychotropic substances'),
    ('ARMS', '25',  'Possession of arms without licence'),
    ('POCSO', '4',  'Penetrative sexual assault'),
    ('POCSO', '6',  'Aggravated penetrative sexual assault'),
    ('POCSO', '8',  'Sexual assault'),
    ('IT', '66',    'Computer related offences'),
    ('IT', '66C',   'Identity theft'),
    ('IT', '66D',   'Cheating by personation using computer resource'),
    ('MV', '279',   'Rash driving on a public way'),
    ('MV', '184',   'Dangerous driving'),
    ('DP', '3',     'Penalty for giving or taking dowry'),
    ('DP', '4',     'Penalty for demanding dowry'),
    ('SCST', '3',   'Punishments for offences of atrocities'),
    ('GAMB', '79',  'Keeping common gaming house'),
    ('GAMB', '80',  'Gaming in common gaming house'),
    ('EXCISE', '32','Sale of liquor without licence'),
    ('KPA', '92',   'Public nuisance in street or public place')
ON CONFLICT (act_code, section_code) DO NOTHING;

INSERT INTO crime_head (crime_head_id, crime_group_name) VALUES
    (1,  'Crimes Against Body'),
    (2,  'Crimes Against Women'),
    (3,  'Crimes Against Property'),
    (4,  'Economic Offences'),
    (5,  'Cyber Crimes'),
    (6,  'Narcotics'),
    (7,  'Crimes Against Children'),
    (8,  'Public Order'),
    (9,  'Traffic Offences'),
    (10, 'Other Offences')
ON CONFLICT (crime_head_id) DO NOTHING;

INSERT INTO crime_sub_head (crime_sub_head_id, crime_head_id, crime_head_name, seq_id) VALUES
    (1,  1, 'Murder', 1),
    (2,  1, 'Attempt to Murder', 2),
    (3,  1, 'Grievous Hurt / Assault', 3),
    (4,  1, 'Kidnapping', 4),
    (5,  2, 'Rape', 1),
    (6,  2, 'Dowry Harassment', 2),
    (7,  2, 'Outraging Modesty', 3),
    (8,  3, 'Theft', 1),
    (9,  3, 'Vehicle Theft', 2),
    (10, 3, 'House Break-in', 3),
    (11, 3, 'Robbery', 4),
    (12, 3, 'Dacoity', 5),
    (13, 4, 'Cheating / Fraud', 1),
    (14, 4, 'Criminal Breach of Trust', 2),
    (15, 5, 'Online Financial Fraud', 1),
    (16, 5, 'Identity Theft', 2),
    (17, 5, 'Social Media Offences', 3),
    (18, 6, 'Drug Possession', 1),
    (19, 6, 'Drug Trafficking', 2),
    (20, 7, 'POCSO Offences', 1),
    (21, 8, 'Rioting / Unlawful Assembly', 1),
    (22, 8, 'Gambling', 2),
    (23, 9, 'Rash & Negligent Driving', 1),
    (24, 9, 'Fatal Road Accident', 2),
    (25, 10, 'Illicit Liquor', 1),
    (26, 10, 'Arms Act Violations', 2),
    (27, 10, 'Atrocities (SC/ST)', 3),
    (28, 10, 'Public Nuisance', 4)
ON CONFLICT (crime_sub_head_id) DO NOTHING;

INSERT INTO crime_head_act_section (crime_head_id, act_code, section_code)
SELECT v.crime_head_id, v.act_code, v.section_code
FROM (VALUES
    (1, 'IPC', '302'), (1, 'IPC', '307'), (1, 'IPC', '323'), (1, 'IPC', '324'),
    (1, 'IPC', '326'), (1, 'IPC', '363'),
    (2, 'IPC', '376'), (2, 'IPC', '354'), (2, 'IPC', '498A'), (2, 'DP', '3'), (2, 'DP', '4'),
    (3, 'IPC', '379'), (3, 'IPC', '380'), (3, 'IPC', '392'), (3, 'IPC', '395'), (3, 'IPC', '457'),
    (4, 'IPC', '420'), (4, 'IPC', '406'),
    (5, 'IT', '66'), (5, 'IT', '66C'), (5, 'IT', '66D'),
    (6, 'NDPS', '20'), (6, 'NDPS', '21'), (6, 'NDPS', '22'),
    (7, 'POCSO', '4'), (7, 'POCSO', '6'), (7, 'POCSO', '8'),
    (8, 'IPC', '143'), (8, 'IPC', '147'), (8, 'GAMB', '79'), (8, 'GAMB', '80'),
    (9, 'MV', '279'), (9, 'MV', '184'), (9, 'IPC', '304A'),
    (10, 'EXCISE', '32'), (10, 'ARMS', '25'), (10, 'SCST', '3'), (10, 'KPA', '92')
) AS v(crime_head_id, act_code, section_code)
WHERE NOT EXISTS (
    SELECT 1 FROM crime_head_act_section c
    WHERE c.crime_head_id = v.crime_head_id
      AND c.act_code = v.act_code AND c.section_code = v.section_code
);
