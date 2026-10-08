# IVC Historical Meetings — Migration Manifest (draft for review)

Prepared 2026-10-08 for [backload-ivc-meetings.md](backload-ivc-meetings.md),
step 1 (reconcile) and step 2 (review with Nathan). **Nothing has been written
to SQL or staged yet.** Decisions marked **Q** need Nathan's answer first.

Source root: `C:\Users\NathanBunker\AIRA Dropbox\Nathan Bunker\Emerging Standards\IVC`
(paths below are relative to it). Evidence used: `Agenda`, `Presentation`,
`IVC en espanol`, `Bordeaux 2025`, the existence of files in `Notes` and
`Recordings` (their contents were not used), the IVC-Website inventories, and the
DokuWiki [meetings](https://ivci.org/doku/doku.php?id=ivci:meetings) and
[presentations](https://ivci.org/doku/doku.php?id=ivci:presentations) indexes.

## Summary

| | Count |
| --- | ---: |
| Monthly meetings (2023-06-14 … 2026-05-13) | 25 |
| IVC en español meetings | 3 |
| Bordeaux meetings (training + summit) | 2 |
| **Meetings to create** | **30** |
| Presentations selected | 57 |
| — blocked by the 25 MiB limit (Q1) | 2 |
| — pending a version or attribution decision (Q2–Q4) | 3 |
| Proposed topic links | 31 |

**Monthly series:** the 25 dates match the DokuWiki meeting index exactly,
and each has a recording folder. There is no IVC meeting in InteropHub on or
before 2026-05-13 (the earliest is 2026-06-10, meeting 6), so nothing
duplicates. 2026-04-08 was announced as "next meeting" in March 2026, but it
has no agenda, deck, recording, or DokuWiki entry, so it is treated as not held.

**Conventions applied to every meeting:**

- **Time:** 10:00 America/New_York (see Q6 for the two same-day pairs).
- **Status:** CLOSED.
- **Title:** monthly meetings use
  `Immunization Vocabularies Collaboration (IVC) Monthly Meeting`.
- **Opening item:** `Welcome and Introductions` is first, at order 10. Monthly
  decks attach here.
- **Closing item:** `Wrap Up` is last.
- **Item order:** items between them are numbered 11, 12, ….
- **Item descriptions:** markdown comes from the published agenda bullets.
  Presenter names appear as printed on the public agendas (see Q9).
- **Topic links:** 🔗 marks a proposed link, by topic ID and space.
  ES = Emerging Standards, BB = Building Bridges.

## Questions for Nathan

**Q1. Two decks exceed the 25 MiB attachment limit.** No PDF exists for either.

| Deck | Size |
| --- | --- |
| `Presentation/2025/6 June/International Vaccine Codes 2025.06.11.pptx` (106 slides) | 60,078,075 bytes |
| `Presentation/2025/7 July/International Vaccine Codes 2025.07.09.pptx` (46 slides) | 26,738,884 bytes |

Both are Bordeaux summit review decks, and both largely re-show the speaker
slides. The task forbids me converting or compressing them. Options:

- (a) You save a "Compress Pictures" copy or PDF export as a new canonical
  file, and I stage that. **Recommended.**
- (b) Attach nothing to those two meetings and record the gap. The agenda
  items still exist.
- (c) Raise the limit. That's a code change, so it's out of scope for this task.

**Q2. January 2025 has two versions of the deck.**
`International Vaccine Codes 2025.01.08.pptx` (24 slides, saved on the meeting
date) and `… 2025.01.08 Updates.pptx` (27 slides, saved 2025-02-04). The
Updates version adds Point of Contact, Liaison, and Co-chair slides after the
meeting, and DokuWiki linked to it. **Recommend** the original, because it's
what was presented and the added roles are the ones the Spanish review calls
obsolete.

**Q3. The Bordeaux opening deck**
(`Bordeaux 2025/A01-FK-Welcome and goals of the meeting.pptx`, 12 slides,
2,010,485 bytes) was flagged in the Bordeaux register as "confirm speaker
attribution before publishing." **Recommend** attaching it unchanged to the
opening item, because it's preserved as a historical artifact. The other choice
is to omit it.

**Q4. The Bordeaux training deck.** Confirm
`Bordeaux 2025/Presentations/IVC Training 2025-05-08-Nathan.pptx` (77 slides,
16,322,148 bytes) is the delivered deck. It's byte-identical to the root copy.
The 63- and 89-slide versions are drafts, and the Quiz deck dates from
October 2025. `Training NUVA.pptx` is already embedded in this deck as its
"Practical use of the NUVA" section, so I'm excluding it as a duplicate.

**Q5. 24 September 2025 Spanish meeting?** There's an agenda file,
`Agenda/2025/2025.09.24_Agenda_ Iniciativa de Codificación….docx`, but it's an
unfilled template with "Item" placeholders. There's no deck, no recording, it
isn't in the Spanish review, and the 2025-07-23 agenda file is also an
un-updated copy of April's. **Recommend** not creating it unless you know it
took place.

**Q6. Same-day meetings.** On 2025-03-12 and 2025-04-09, both an English
monthly meeting (10:00 ET) and a Spanish meeting (9:00 ET per its agenda) took
place. At "10:00 for everything," they would have identical timestamps and an
unpredictable order. **Recommend** 9:00 ET for those two Spanish meetings only.

**Q7. Topic links.** Please review the 🔗 proposals below. Notes:

- **NUVA:** there are two NUVA topics, ES 117 *Unified Nomenclature of
  Vaccines (NUVA)* and BB 151 *NUVA*. I used ES 117, which current IVC
  agendas already use.
- **Country interviews:** these items use ES 120 *Building Bridges*, as current
  IVC agendas do. BB links are used where the subject is a specific country or
  organization's own work.
- **Considered but left unlinked:**
  - Syadem paper-record recognition (Oct 2025): speaker-org only.
  - Vaccine Genie: no topic.
  - Gaps Analysis (Jun 2023): broad.
  - Mapping across code systems and Metrics (Bordeaux): broad.
  - Flu/COVID coding: no matching topic.
  - Meningococcal items: no topic.
  - Other Coded Values / schedule authority: unclear fit.
  - Spanish April NUVA segment: it's part of a broader item.

**Q8. Companion files I'm excluding.** Please confirm.

| File | Reason |
| --- | --- |
| `Presentation/2026/01 Jan/Vaccine Recommendations.pptx` | A US CDS-assessment/SISC deck that isn't on the January agenda |
| `Presentation/2023/7 July/Vaccin Codes presentation M Philippi 2023-07-11.pptx` | Its 3 slides are already inside the July 2023 main deck |
| `Presentation/2023/6 June/Gap analysis v1_0.pdf` | A written report rather than a deck; it was discussed under "Gaps Analysis" |

**Q9. Presenter names in item descriptions.** **Recommend** including
presenter names exactly as they appeared on the public agendas. The
alternative is titles and topics only.

## Source problems found (handled)

| Source file | Problem | Evidence used instead |
| --- | --- | --- |
| `Agenda/2025/2025.04.09 … Agenda.docx` | Overwritten with June 2025 content | April PDF |
| `Agenda/2023/8 August/…docx` | Copy of the July agenda | August deck's agenda slide and DokuWiki |
| `Agenda/2023/10 October/…docx`, `Agenda/2024/3 March/…docx` | Unfilled templates | Decks and DokuWiki |
| Spanish `Agenda/2025/2025.07.23 …docx` | Copy of April's agenda | July deck and Spanish review |

`Presentation/2025/7 July/International Vaccine Codes 2025.07.23 Esp.pptx`
(60 MB) is not the Spanish July deck. It's a renamed copy of the June
106-slide deck. The selected Spanish July deck is the 16-slide file in
`IVC en espanol/Presentation`.

## Meetings

Format: **order. Item title** — description · 📎 attachment (bytes) · 🔗 topic

### 2023-06-14 — Monthly
10. **Welcome and Introductions** — Purpose of call · 📎 `Presentation/2023/6 June/International Vaccine Codes 2023-06-14.pptx` (4,525,555)
11. **Roundtable Updates** — Updates from AIRA and Mes Vaccins
12. **Gaps Analysis** — Comparative analysis of existing vaccine code systems (François Kaag, Mes Vaccins) · 📎 `Presentation/2023/6 June/230614-AIRA  codes meeting.pptx` (435,310)
13. **Wrap Up**

Excluded: the PDF export of the main deck (in the July folder), the gap
analysis report, `Gestion des nomenclatures 0_5.pdf` (a 2021 Syadem technical
document), `NUVA overview.docx`, and the SPARQL text file.

### 2023-07-12 — Monthly
10. **Welcome and Introductions** — Purpose, scope, introductions · 📎 `Presentation/2023/7 July/International Vaccine Codes 2023-07-12.pptx` (6,118,680)
11. **Roundtable Updates** — Washington IIS, NUVA, Netherlands (RIVM), AIRA
12. **Gap Analysis Review** — Collect feedback on the gap analysis
13. **SMART Health Cards** — Terminology approach (MITRE) · 📎 `Presentation/2023/7 July/2023-07-12 SHC Terminology Approach.pdf` (2,524,173) · 🔗 ES 19 Digital Vaccine Cards (SMART Health Cards)
14. **Wrap Up**

### 2023-08-09 — Monthly
10. **Welcome and Introductions** — Introductions, scope of discussion · 📎 `Presentation/2023/8 August/International Vaccine Codes 2023-08-09.pptx` (21,972,047)
11. **Roundtable Updates**
12. **Vaccine Code Set Metrics**
13. **Wrap Up**

Excluded: 8 metric PNGs and `Excerpt of mapping on EVC.docx`.

### 2023-09-20 — Monthly
10. **Welcome and Introductions** · 📎 `Presentation/2023/9 September/International Vaccine Codes 2023-09-20.pptx` (5,678,298)
11. **WHODrug** — Introduction to the WHODrug dictionary (Salvador Alvarado, UMC) · 📎 `Presentation/2023/9 September/2023_09_20_WHODrug Global_ENG.pdf` (3,424,988) · 🔗 BB 154 Uppsala Monitoring Centre and WHODrug
12. **Roundtable Updates**
13. **Vaccine Code Set Metrics** — Wiki introduction
14. **Wrap Up**

Excluded: the PDF export of the main deck.

### 2023-10-11 — Monthly
10. **Welcome and Introductions** · 📎 `Presentation/2023/10 October/International Vaccine Codes 2023.10.11.pptx` (22,382,311)
11. **Roundtable Updates**
12. **Vaccine Code Set Metrics**
13. **Wrap Up**

### 2023-12-13 — Monthly
10. **Welcome and Introductions** · 📎 `Presentation/2023/12 December/International Vaccine Codes 2023.12.13.pptx` (5,663,040)
11. **Roundtable Updates**
12. **CVX Update Management** — AIRA, NIST, and other CVX update processes · 🔗 BB 168 United States
13. **Wrap Up**

Excluded: `2023.12.13 IVC Notes.docx`.

### 2024-03-20 — Monthly
10. **Welcome and Introductions** · 📎 `Presentation/2024/3 March/International Vaccine Codes 2024.03.20.pptx` (3,980,451)
11. **Roundtable Updates**
12. **CVX Metrics**
13. **Wrap Up**

### 2024-04-17 — Monthly
10. **Welcome and Introductions** · 📎 `Presentation/2024/4 April/International Vaccine Codes 2024.04.17.pptx` (5,251,139)
11. **Roundtable Updates**
12. **NUVA** — One-page introduction · 🔗 ES 117 NUVA
13. **Metrics** — Automatically generating metrics of code systems (François Kaag) · 📎 `Presentation/2024/4 April/240320-IVCI-Metrics- FINAL.pptx` (316,264)
14. **Wrap Up**

Excluded: `International Vaccine Codes - Introduction.pptx` (its 3 slides are in the main deck).

### 2024-06-12 — Monthly
10. **Welcome and Introductions** · 📎 `Presentation/2024/6 June/International Vaccine Codes 2024.06.12.pptx` (7,514,281)
11. **Roundtable Updates**
12. **Next Year Planning** — IVC and NUVA launch event · 📎 `Presentation/2024/6 June/240612-IVCI.pptx` (269,595)
13. **Wrap Up**

### 2024-07-31 — Monthly
10. **Welcome and Introductions** · 📎 `Presentation/2024/7 July/International Vaccine Codes 2024.07.31.pptx` (6,649,560)
11. **Roundtable Updates**
12. **Vision & Goals** — Website vision and goals; SME and WHO interviews
13. **Metrics** — Assessing CVX against abstract NUVA codes
14. **Wrap Up**

### 2024-10-09 — Monthly
10. **Welcome and Introductions** · 📎 `Presentation/2024/10 October/International Vaccine Codes 2024.10.09.pptx` (6,584,085)
11. **Roundtable Updates**
12. **HL7 WGM Update** — IPS, vaccine codes and connectathon, SMART Health Links · 🔗 BB 155 HL7 International
13. **AI Discussions** — Could AI help organize vaccine codes?
14. **Country and Project Interviews** — Malawi, Tanzania, Ghana, Ireland · 🔗 ES 120 Building Bridges
15. **Wrap Up** — IVC en Español, Bordeaux 2025, SNOMED & WHO

Excluded: the PDF export of the main deck.

### 2024-11-27 — Monthly
10. **Welcome and Introductions** · 📎 `Presentation/2024/11 November/International Vaccine Codes 2024.11.27.pptx` (5,536,956)
11. **Roundtable Updates**
12. **Country and Project Interviews** — Report on interviews · 🔗 ES 120 Building Bridges
13. **Wrap Up**

### 2025-01-08 — Monthly
10. **Welcome and Introductions** · 📎 `Presentation/2025/1 January/International Vaccine Codes 2025.01.08.pptx` (4,540,773) **(Q2)**
11. **Roundtable Updates**
12. **Country and Project Outreach** — Outreach plan 2025 · 🔗 ES 120 Building Bridges
13. **Bordeaux 2025** — Agenda planning
14. **Wrap Up** — IVC en Español

### 2025-03-12 — Monthly
10. **Welcome and Introductions** · 📎 `Presentation/2025/3 March/International Vaccine Codes 2025.03.12.pptx` (3,166,686)
11. **Roundtable Updates**
12. **Bordeaux 2025** — Agenda planning
13. **Wrap Up**

### 2025-03-12 — `IVC en español — Introducción a los códigos de vacunas` (Q6: 9:00 ET)
10. **Bienvenida** — Dinámica de apertura · 📎 `IVC en espanol/Presentation/IVC en español 2025-03_meeting review version.pptx` (9,877,242)
11. **Introducción a los Conjuntos de Códigos de Vacunas** (Alejandra Arias, CT WiZ IIS; Nathan Bunker, AIRA)
12. **Conjuntos de Códigos de Medicamentos** — UMC, WHODrug Global (Salvador Alvarado López, UMC) · 📎 `IVC en espanol/Presentation/2025_03_12_USA_AIRA_SPA_IVC en español_WHODrug Global.pdf` (4,195,844) · 🔗 BB 154 Uppsala Monitoring Centre and WHODrug
13. **Discusión sobre la Iniciativa de Codificación de Vacunas (IVC)**
14. **Cierre y Próximos Pasos**

The welcome and closing items use the Spanish titles from the agenda (as with
the April and July meetings). Excluded: the PDF export, 2 conflicted copies,
the Archive drafts, the Bordeaux promo slide, follow-up, registration,
recording, chat, and photos.

### 2025-04-09 — Monthly
10. **Welcome and Introductions** · 📎 `Presentation/2025/4 April/International Vaccine Codes 2025.04.09.pptx` (3,145,901)
11. **Roundtable Updates**
12. **Country Interviews** — Update on recent conversations · 🔗 ES 120 Building Bridges
13. **Bordeaux 2025** — Agenda planning (François Kaag, Syadem)
14. **Wrap Up**

### 2025-04-09 — `IVC en español — IVC y NUVA` (Q6: 9:00 ET)
10. **Bienvenida** · 📎 `IVC en espanol/Presentation/IVC en español 2025-04.pptx` (8,054,013)
11. **Introducción y Herramientas Clave de Codificación de Vacunas (IVC)** — Includes NUVA as an interoperability tool
12. **Conversación Abierta con el Grupo**
13. **Cierre y Próximos Pasos**

### 2025-05-08 — `IVC Vaccine Code Training — Bordeaux` (title proposed)
10. **IVC Vaccine Code Training** — Vocabulary in health data exchange, vaccine code systems, NUVA, and practical use of NUVA tools · 📎 `Bordeaux 2025/Presentations/IVC Training 2025-05-08-Nathan.pptx` (16,322,148) **(Q4)**

This is a single-session event, so it has no Welcome or Wrap Up item. Tell
me if you want them added.

### 2025-05-09 — `International Summit on Vaccine Coding & Standards — Bordeaux` (title proposed)
Only sessions evidenced as delivered are included (per the Bordeaux
reconciliation). The planned WHO and PAHO slots are omitted.

10. **Goals of the Meeting and Importance of Standardized Vaccine Coding** (François Kaag, Nathan Bunker) · 📎 `Bordeaux 2025/A01-FK-Welcome and goals of the meeting.pptx` (2,010,485) **(Q3)**
11. **NUVA: What It Is and Why It Matters** (François Kaag) · 📎 `Bordeaux 2025/Presentations/A03-FK-NUVA- Why it matters.pdf` (845,906) · 🔗 ES 117 NUVA
12. **How NUVA Uses Valences** (Jean-Louis Koeck) · 📎 `…/Presentations/a04-jlk-_valence_concept.pdf` (641,594) · 🔗 ES 117 NUVA
13. **NUVA Extension to SNOMED CT** (Suzy Roy, Peter Williams) · 📎 `…/Presentations/A05_NUVA Extension to SNOMED CT.pdf` (3,849,368) · 🔗 BB 152 SNOMED International
14. **Industry View: Vaccine Codification and Access to Resources** (Ingrid Weindorfer) · 📎 `…/Presentations/B01-IW-View from the industry.pdf` (1,936,414) · 🔗 BB 177 Vaccine Manufacturers and Pharmaceutical Industry
15. **EU Strategy for Cross-Border Vaccination Records** (Georgios Margetidis, HaDEA; virtual, no slides) · 🔗 BB 163 European Commission, HaDEA, and EU4Health
16. **WHODrug and IDMP for Vaccines** (Malin Fladvad, UMC) · 📎 `…/Presentations/B04-MF-WHODrug and IDMP for vaccines.pdf` (1,883,984) · 🔗 BB 154 Uppsala Monitoring Centre and WHODrug
17. **Luxembourg Experience** (Maud Delporte) · 📎 `…/Presentations/c01-md-luxembourg_experience.pdf` (1,475,396) · 🔗 BB 170 Luxembourg
18. **EUVABECO Electronic Vaccination Card Project** (Alain Cimino) · 📎 `…/Presentations/C02-AC-The EUVABECO EVC project.pdf` (1,104,906) · 🔗 BB 157 European Vaccination Card (EVC)
19. **United States Vaccine-Coding Experience** (Shannon Coleman, STCHealth) · 📎 `…/Presentations/D02-SC-US - Vaccine Coding.pdf` (3,600,199) · 🔗 BB 168 United States
20. **Canadian Vaccine-Coding Experience** (Myriam Talantikit, Canada Health Infoway) · 📎 `…/Presentations/D03-MT-Canada_Experiences_in_Vaccine_Coding.pdf` (1,120,770) · 🔗 BB 105 Canada
21. **Mapping Across Code Systems** (Timothée Doulut, Syadem) · 📎 `…/Presentations/E01-TD-Transcoding and aligning.pdf` (854,339)
22. **Metrics for Code Systems** (François Kaag) · 📎 `…/Presentations/E02-FK-Metrics.pdf` (668,256)
23. **Long-Term Goals and Next Actions** (Nathan Bunker) — discussion
24. **Final Takeaways** (Nathan Bunker)

Excluded: the root-level Canada PDF (near-duplicate), the ZIP bundle, the
Mentimeter decks and results, the reports, agendas, invitations, logistics,
promotion, and photos.

### 2025-06-11 — Monthly
10. **Welcome and Introductions** · 📎 `Presentation/2025/6 June/International Vaccine Codes 2025.06.11.pptx` (60,078,075) **BLOCKED (Q1)**
11. **Summit Report** — Review of presentations and key discussions from the Bordeaux 2025 summit (Nathan Bunker; François Kaag, Syadem)
12. **Wrap Up** — Next meetings planned (English & Spanish)

### 2025-07-09 — Monthly
10. **Welcome and Introductions** · 📎 `Presentation/2025/7 July/International Vaccine Codes 2025.07.09.pptx` (26,738,884) **BLOCKED (Q1)**
11. **Summit Report, Part 2** — Afternoon sessions and strategy session
12. **Wrap Up**

Excluded: the 2-slide "temp" deck and the 60 MB misnamed "07.23 Esp" copies.

### 2025-07-23 — `IVC en español — Resumen de la Cumbre de Burdeos`
10. **Bienvenida** · 📎 `IVC en espanol/Presentation/International Vaccine Codes 2025.07.23 Esp.pptx` (5,821,232)
11. **Progreso de 2025**
12. **Resumen de la Cumbre de Burdeos**
13. **Discusión y Comentarios**
14. **Cierre y Próximos Pasos** — Próximas reuniones

The title slide says "11 June 2025" while the filename and context support
23 July. I'll record this discrepancy and leave the file unchanged. Item titles
are the Spanish review's agenda, rendered in Spanish. Tell me if you want
different wording. Excluded: the 106-slide "Esp extra" deck.

### 2025-09-10 — Monthly
10. **Welcome and Introductions** · 📎 `Presentation/2025/9 Sep/International Vaccine Codes 2025.09.10.pptx` (2,825,992)
11. **Flu and COVID Coding** — NUVA mappings for flu and COVID; how members are coding this season
12. **Year Ahead Brainstorming** — IVC en Español, Costa Rica liaison, NHS England engagement, country interview process, co-chair recruitment
13. **Wrap Up** — Testimonials about IVC

Excluded: two spreadsheets and the internal `2025.09.10 Meeting Plan.docx`.

### 2025-10-08 — Monthly
10. **Welcome and Introductions** · 📎 `Presentation/2025/10 Oct/International Vaccine Codes 2025.10.08.pptx` (3,165,283)
11. **Vaccine Genie** — International vaccine coding and translation (Evelyn Fang, Sean Bennick) · 📎 `Presentation/2025/10 Oct/AIRA_15min (2).pptx` (6,346,905)
12. **Automatic Recognition of Paper Records** (Mathieu Laporte, Syadem) · 📎 `Presentation/2025/10 Oct/Syadem Presentation.pdf` (9,805,025)
13. **Wrap Up** — Next topic: NUVA training

Excluded: 3 screenshots.

### 2025-11-12 — Monthly
10. **Welcome and Introductions** · 📎 `Presentation/2025/11 Nov/International Vaccine Codes 2025.11.12.pptx` (2,244,724)
11. **Update on SNOMED CT Expo 2025** · 🔗 BB 152 SNOMED International
12. **NUVA Training** (François Kaag, Syadem) · 📎 `Presentation/2025/11 Nov/251112-NUVA presentation for IVCI.pptx` (4,806,532) · 🔗 ES 117 NUVA
13. **Wrap Up**

### 2025-12-10 — Monthly
10. **Welcome and Introductions** · 📎 `Presentation/2025/12 Dec/International Vaccine Codes 2025.12.10.pptx` (9,960,233)
11. **Vaccination Information Sheets** — Nancy student project demonstration · 📎 `Presentation/2025/12 Dec/NUVAccess.pdf` (517,251)
12. **Country Interview Process** · 📎 `Presentation/2025/12 Dec/Country Interview Review with IVC.pptx` (8,619,611) · 🔗 ES 120 Building Bridges
13. **Publications of Mappings** — Pre-built pivot tables (François Kaag) · 📎 `Presentation/2025/12 Dec/251210-NUVA alignment files.pptx` (2,615,064) · 🔗 ES 117 NUVA
14. **Wrap Up**

### 2026-01-14 — Monthly
10. **Welcome and Introductions** · 📎 `Presentation/2026/01 Jan/International Vaccine Codes 2026.01.14.pptx` (2,490,720)
11. **NUVA Use Cases: France and Switzerland** — Interpreting and transcoding vaccine histories · 📎 `Presentation/2026/01 Jan/260114-NUVA use cases in France and Switzerland.pptx` (2,583,827) · 🔗 ES 117 NUVA
12. **SNOMED Discussion** · 🔗 BB 152 SNOMED International
13. **United States Update** · 🔗 BB 168 United States
14. **NUVA Search App** — Student app demonstration · 🔗 ES 117 NUVA
15. **Wrap Up**

Excluded: `Vaccine Recommendations.pptx` (Q8).

### 2026-02-11 — Monthly
10. **Welcome and Introductions** · 📎 `Presentation/2026/02 Feb/International Vaccine Codes 2026.02.11.pptx` (2,647,119)
11. **Country Updates**
12. **Meningococcal Introduction** (Dr Jean-Louis Koeck, Syadem) · 📎 `Presentation/2026/02 Feb/20260210 Neisseria meningitidis.pptx` (1,486,066)
13. **Meningococcal Implementation** — Roundtable
14. **Target Disease** — Disease vs. pathogen (François Kaag) · 📎 `Presentation/2026/02 Feb/260211-Concept of disease in NUVA.pptx` (2,617,666) · 🔗 ES 117 NUVA
15. **Wrap Up**

Excluded: `Meningococcal vaccines.xlsx`.

### 2026-03-11 — Monthly
10. **Welcome and Introductions** · 📎 `Presentation/2026/03 Mar/International Vaccine Codes 2026.03.11.pptx` (2,732,052)
11. **Country Updates**
12. **Meningococcal Discussion** — Experience in France (MenB campaign, Rennes) · 📎 `Presentation/2026/03 Mar/MenB Rennes 2025_v3.pptx` (2,611,266) · 🔗 BB 107 France
13. **Other Coded Values** — Schedule authority and other coded concepts
14. **Wrap Up**

### 2026-05-13 — Monthly
10. **Welcome and Introductions** · 📎 `Presentation/2026/05 May/International Vaccine Codes 2026.05.13.pptx` (7,798,081)
11. **Updates** — Country updates and country interviews · 🔗 ES 120 Building Bridges
12. **Scope and Name Discussion** — IVC and interoperability standards; the term "International"
13. **MMR/MMRV** — Introducing the topic for next time
14. **Wrap Up**

## Not imported (all meetings)

- Agenda files: used only as evidence.
- `Notes/` and `Recordings/`: these include minutes, transcripts, chat, and
  video.
- `Attendance/`, registrations, contacts, invitations, follow-up emails,
  speaker photos, and planning and promotion material.
- The undated general decks in `Presentation/` (Geneva prep, Strategy,
  Roadshow, and similar).
- Public summaries: none written (decision 2026-10-08).
