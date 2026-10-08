# IVC Historical Meetings — Migration Report

Prepared 2026-10-08 for [backload-ivc-meetings.md](backload-ivc-meetings.md).
The approved plan and Nathan's decisions are in
[ivc-historical-meetings-manifest.md](ivc-historical-meetings-manifest.md).
The file-to-UUID mapping is in
[ivc-historical-meetings-files.tsv](ivc-historical-meetings-files.tsv).

**Status: locally verified and ready for Nathan's local review.** Nothing has
been done in production.

## 1. Counts

| | Count |
| --- | ---: |
| Meetings created: 25 monthly, 3 Spanish, 2 Bordeaux | 30 |
| Agenda items | 143 |
| Presentations attached, each once | 57 |
| Size exceptions over the 25 MiB upload cap (approved) | 2 |
| Topic links: 15 to Emerging Standards topics, 16 to Building Bridges topics | 31 |
| Staged bytes | 346,632,565 |
| Gaps: meetings with no deck | 0 |

### Decisions applied (2026-10-08)

| # | Decision |
| --- | --- |
| Q1 | The June and July 2025 decks are attached unchanged as size exceptions. |
| Q2 | The original January 2025 deck is used. |
| Q3 | The Bordeaux opening deck is attached unchanged. |
| Q4 | The Bordeaux training deck is confirmed. |
| Q5 | The 24 September 2025 Spanish meeting is omitted. |
| Q6 | The two same-day Spanish meetings are at 09:00 ET. |
| Q7 | The topic links were approved, using Emerging Standards NUVA (117). |
| Q8 | The exclusions were approved. |
| Q9 | Presenter names are included in item descriptions. |

These rules from earlier decisions also apply:

- Monthly meetings use the series title.
- No summaries are imported.
- All other meetings start at a nominal 10:00 America/New_York.

## 2. Meeting map

All 30 meetings belong to the existing IVC series: `es_topic_meeting`
*Immunization Vocabularies Collaboration (IVC) Monthly Meeting*, under topic
*Immunization Vocabularies Collaboration (IVC)* in Emerging Standards. They
were resolved by name in SQL, not by ID.

**Meeting IDs are local.** Production will assign its own IDs, so the URLs
below are for local review only.

Public series landing page:
`https://informatics.immregistries.org/hub/es/meeting-series?seriesId=1`.
This is the stable link the website should use.

| ID | Date | Title | Items | Decks | Local URL |
| --- | --- | --- | ---: | ---: | --- |
| 92 | 2023-06-14 10:00 | Immunization Vocabularies Collaboration (IVC) Monthly Meeting | 4 | 2 | http://localhost:8080/hub/es/agenda?meetingId=92 |
| 93 | 2023-07-12 10:00 | Immunization Vocabularies Collaboration (IVC) Monthly Meeting | 5 | 2 | http://localhost:8080/hub/es/agenda?meetingId=93 |
| 94 | 2023-08-09 10:00 | Immunization Vocabularies Collaboration (IVC) Monthly Meeting | 4 | 1 | http://localhost:8080/hub/es/agenda?meetingId=94 |
| 95 | 2023-09-20 10:00 | Immunization Vocabularies Collaboration (IVC) Monthly Meeting | 5 | 2 | http://localhost:8080/hub/es/agenda?meetingId=95 |
| 96 | 2023-10-11 10:00 | Immunization Vocabularies Collaboration (IVC) Monthly Meeting | 4 | 1 | http://localhost:8080/hub/es/agenda?meetingId=96 |
| 97 | 2023-12-13 10:00 | Immunization Vocabularies Collaboration (IVC) Monthly Meeting | 4 | 1 | http://localhost:8080/hub/es/agenda?meetingId=97 |
| 98 | 2024-03-20 10:00 | Immunization Vocabularies Collaboration (IVC) Monthly Meeting | 4 | 1 | http://localhost:8080/hub/es/agenda?meetingId=98 |
| 99 | 2024-04-17 10:00 | Immunization Vocabularies Collaboration (IVC) Monthly Meeting | 5 | 2 | http://localhost:8080/hub/es/agenda?meetingId=99 |
| 100 | 2024-06-12 10:00 | Immunization Vocabularies Collaboration (IVC) Monthly Meeting | 4 | 2 | http://localhost:8080/hub/es/agenda?meetingId=100 |
| 101 | 2024-07-31 10:00 | Immunization Vocabularies Collaboration (IVC) Monthly Meeting | 5 | 1 | http://localhost:8080/hub/es/agenda?meetingId=101 |
| 102 | 2024-10-09 10:00 | Immunization Vocabularies Collaboration (IVC) Monthly Meeting | 6 | 1 | http://localhost:8080/hub/es/agenda?meetingId=102 |
| 103 | 2024-11-27 10:00 | Immunization Vocabularies Collaboration (IVC) Monthly Meeting | 4 | 1 | http://localhost:8080/hub/es/agenda?meetingId=103 |
| 104 | 2025-01-08 10:00 | Immunization Vocabularies Collaboration (IVC) Monthly Meeting | 5 | 1 | http://localhost:8080/hub/es/agenda?meetingId=104 |
| 105 | 2025-03-12 09:00 | IVC en español — Introducción a los códigos de vacunas | 5 | 2 | http://localhost:8080/hub/es/agenda?meetingId=105 |
| 106 | 2025-03-12 10:00 | Immunization Vocabularies Collaboration (IVC) Monthly Meeting | 4 | 1 | http://localhost:8080/hub/es/agenda?meetingId=106 |
| 107 | 2025-04-09 09:00 | IVC en español — IVC y NUVA | 4 | 1 | http://localhost:8080/hub/es/agenda?meetingId=107 |
| 108 | 2025-04-09 10:00 | Immunization Vocabularies Collaboration (IVC) Monthly Meeting | 5 | 1 | http://localhost:8080/hub/es/agenda?meetingId=108 |
| 109 | 2025-05-08 10:00 | IVC Vaccine Code Training — Bordeaux | 1 | 1 | http://localhost:8080/hub/es/agenda?meetingId=109 |
| 110 | 2025-05-09 10:00 | International Summit on Vaccine Coding & Standards — Bordeaux | 15 | 12 | http://localhost:8080/hub/es/agenda?meetingId=110 |
| 111 | 2025-06-11 10:00 | Immunization Vocabularies Collaboration (IVC) Monthly Meeting | 3 | 1 | http://localhost:8080/hub/es/agenda?meetingId=111 |
| 112 | 2025-07-09 10:00 | Immunization Vocabularies Collaboration (IVC) Monthly Meeting | 3 | 1 | http://localhost:8080/hub/es/agenda?meetingId=112 |
| 113 | 2025-07-23 10:00 | IVC en español — Resumen de la Cumbre de Burdeos | 5 | 1 | http://localhost:8080/hub/es/agenda?meetingId=113 |
| 114 | 2025-09-10 10:00 | Immunization Vocabularies Collaboration (IVC) Monthly Meeting | 4 | 1 | http://localhost:8080/hub/es/agenda?meetingId=114 |
| 115 | 2025-10-08 10:00 | Immunization Vocabularies Collaboration (IVC) Monthly Meeting | 4 | 3 | http://localhost:8080/hub/es/agenda?meetingId=115 |
| 116 | 2025-11-12 10:00 | Immunization Vocabularies Collaboration (IVC) Monthly Meeting | 4 | 2 | http://localhost:8080/hub/es/agenda?meetingId=116 |
| 117 | 2025-12-10 10:00 | Immunization Vocabularies Collaboration (IVC) Monthly Meeting | 5 | 4 | http://localhost:8080/hub/es/agenda?meetingId=117 |
| 118 | 2026-01-14 10:00 | Immunization Vocabularies Collaboration (IVC) Monthly Meeting | 6 | 2 | http://localhost:8080/hub/es/agenda?meetingId=118 |
| 119 | 2026-02-11 10:00 | Immunization Vocabularies Collaboration (IVC) Monthly Meeting | 6 | 3 | http://localhost:8080/hub/es/agenda?meetingId=119 |
| 120 | 2026-03-11 10:00 | Immunization Vocabularies Collaboration (IVC) Monthly Meeting | 5 | 2 | http://localhost:8080/hub/es/agenda?meetingId=120 |
| 121 | 2026-05-13 10:00 | Immunization Vocabularies Collaboration (IVC) Monthly Meeting | 5 | 1 | http://localhost:8080/hub/es/agenda?meetingId=121 |

## 3. Agenda map

The 31 topic links follow the strict-evidence rule. The reasoning for each
link is in the manifest, under Q7 and the 🔗 entries. In summary:

- **Named organization or country:** a link where the session is about that
  organization or country's own work, such as WHODrug/UMC, SNOMED
  International, HL7, the United States (CVX processes), Luxembourg, Canada,
  France (the MenB campaign), the EU/HaDEA strategy, and industry.
- **NUVA (Emerging Standards 117):** a link where the item is about NUVA itself.
- **Building Bridges (Emerging Standards 120):** used for country-interview
  items, as current IVC agendas do.

Everything else is intentionally left unlinked.

| Meeting | Ord | Item ID | Item | Topic (ID, space) |
| --- | ---: | ---: | --- | --- |
| 92 | 10 | 228 | Welcome and Introductions | none |
| 92 | 11 | 229 | Roundtable Updates | none |
| 92 | 12 | 230 | Gaps Analysis | none |
| 92 | 13 | 231 | Wrap Up | none |
| 93 | 10 | 232 | Welcome and Introductions | none |
| 93 | 11 | 233 | Roundtable Updates | none |
| 93 | 12 | 234 | Gap Analysis Review | none |
| 93 | 13 | 235 | SMART Health Cards | Digital Vaccine Cards (SMART Health Cards) (19, emerging-standards) |
| 93 | 14 | 236 | Wrap Up | none |
| 94 | 10 | 237 | Welcome and Introductions | none |
| 94 | 11 | 238 | Roundtable Updates | none |
| 94 | 12 | 239 | Vaccine Code Set Metrics | none |
| 94 | 13 | 240 | Wrap Up | none |
| 95 | 10 | 241 | Welcome and Introductions | none |
| 95 | 11 | 242 | WHODrug | Uppsala Monitoring Centre and WHODrug (154, building-bridges) |
| 95 | 12 | 243 | Roundtable Updates | none |
| 95 | 13 | 244 | Vaccine Code Set Metrics | none |
| 95 | 14 | 245 | Wrap Up | none |
| 96 | 10 | 246 | Welcome and Introductions | none |
| 96 | 11 | 247 | Roundtable Updates | none |
| 96 | 12 | 248 | Vaccine Code Set Metrics | none |
| 96 | 13 | 249 | Wrap Up | none |
| 97 | 10 | 250 | Welcome and Introductions | none |
| 97 | 11 | 251 | Roundtable Updates | none |
| 97 | 12 | 252 | CVX Update Management | United States (168, building-bridges) |
| 97 | 13 | 253 | Wrap Up | none |
| 98 | 10 | 254 | Welcome and Introductions | none |
| 98 | 11 | 255 | Roundtable Updates | none |
| 98 | 12 | 256 | CVX Metrics | none |
| 98 | 13 | 257 | Wrap Up | none |
| 99 | 10 | 258 | Welcome and Introductions | none |
| 99 | 11 | 259 | Roundtable Updates | none |
| 99 | 12 | 260 | NUVA | Unified Nomenclature of Vaccines (NUVA) (117, emerging-standards) |
| 99 | 13 | 261 | Metrics | none |
| 99 | 14 | 262 | Wrap Up | none |
| 100 | 10 | 263 | Welcome and Introductions | none |
| 100 | 11 | 264 | Roundtable Updates | none |
| 100 | 12 | 265 | Next Year Planning | none |
| 100 | 13 | 266 | Wrap Up | none |
| 101 | 10 | 267 | Welcome and Introductions | none |
| 101 | 11 | 268 | Roundtable Updates | none |
| 101 | 12 | 269 | Vision & Goals | none |
| 101 | 13 | 270 | Metrics | none |
| 101 | 14 | 271 | Wrap Up | none |
| 102 | 10 | 272 | Welcome and Introductions | none |
| 102 | 11 | 273 | Roundtable Updates | none |
| 102 | 12 | 274 | HL7 WGM Update | HL7 International (155, building-bridges) |
| 102 | 13 | 275 | AI Discussions | none |
| 102 | 14 | 276 | Country and Project Interviews | Building Bridges (120, emerging-standards) |
| 102 | 15 | 277 | Wrap Up | none |
| 103 | 10 | 278 | Welcome and Introductions | none |
| 103 | 11 | 279 | Roundtable Updates | none |
| 103 | 12 | 280 | Country and Project Interviews | Building Bridges (120, emerging-standards) |
| 103 | 13 | 281 | Wrap Up | none |
| 104 | 10 | 282 | Welcome and Introductions | none |
| 104 | 11 | 283 | Roundtable Updates | none |
| 104 | 12 | 284 | Country and Project Outreach | Building Bridges (120, emerging-standards) |
| 104 | 13 | 285 | Bordeaux 2025 | none |
| 104 | 14 | 286 | Wrap Up | none |
| 105 | 10 | 287 | Bienvenida | none |
| 105 | 11 | 288 | Introducción a los Conjuntos de Códigos de Vacunas | none |
| 105 | 12 | 289 | Conjuntos de Códigos de Medicamentos | Uppsala Monitoring Centre and WHODrug (154, building-bridges) |
| 105 | 13 | 290 | Discusión sobre la Iniciativa de Codificación de Vacunas (IVC) | none |
| 105 | 14 | 291 | Cierre y Próximos Pasos | none |
| 106 | 10 | 292 | Welcome and Introductions | none |
| 106 | 11 | 293 | Roundtable Updates | none |
| 106 | 12 | 294 | Bordeaux 2025 | none |
| 106 | 13 | 295 | Wrap Up | none |
| 107 | 10 | 296 | Bienvenida | none |
| 107 | 11 | 297 | Introducción y Herramientas Clave de Codificación de Vacunas (IVC) | none |
| 107 | 12 | 298 | Conversación Abierta con el Grupo | none |
| 107 | 13 | 299 | Cierre y Próximos Pasos | none |
| 108 | 10 | 300 | Welcome and Introductions | none |
| 108 | 11 | 301 | Roundtable Updates | none |
| 108 | 12 | 302 | Country Interviews | Building Bridges (120, emerging-standards) |
| 108 | 13 | 303 | Bordeaux 2025 | none |
| 108 | 14 | 304 | Wrap Up | none |
| 109 | 10 | 305 | IVC Vaccine Code Training | none |
| 110 | 10 | 306 | Goals of the Meeting and Importance of Standardized Vaccine Coding | none |
| 110 | 11 | 307 | NUVA: What It Is and Why It Matters | Unified Nomenclature of Vaccines (NUVA) (117, emerging-standards) |
| 110 | 12 | 308 | How NUVA Uses Valences | Unified Nomenclature of Vaccines (NUVA) (117, emerging-standards) |
| 110 | 13 | 309 | NUVA Extension to SNOMED CT | SNOMED International (152, building-bridges) |
| 110 | 14 | 310 | Industry View: Vaccine Codification and Access to Resources | Vaccine Manufacturers and Pharmaceutical Industry (177, building-bridges) |
| 110 | 15 | 311 | EU Strategy for Cross-Border Vaccination Records | European Commission, HaDEA, and EU4Health (163, building-bridges) |
| 110 | 16 | 312 | WHODrug and IDMP for Vaccines | Uppsala Monitoring Centre and WHODrug (154, building-bridges) |
| 110 | 17 | 313 | Luxembourg Experience | Luxembourg (170, building-bridges) |
| 110 | 18 | 314 | EUVABECO Electronic Vaccination Card Project | European Vaccination Card (EVC) (157, building-bridges) |
| 110 | 19 | 315 | United States Vaccine-Coding Experience | United States (168, building-bridges) |
| 110 | 20 | 316 | Canadian Vaccine-Coding Experience | Canada (105, building-bridges) |
| 110 | 21 | 317 | Mapping Across Code Systems | none |
| 110 | 22 | 318 | Metrics for Code Systems | none |
| 110 | 23 | 319 | Long-Term Goals and Next Actions | none |
| 110 | 24 | 320 | Final Takeaways | none |
| 111 | 10 | 321 | Welcome and Introductions | none |
| 111 | 11 | 322 | Summit Report | none |
| 111 | 12 | 323 | Wrap Up | none |
| 112 | 10 | 324 | Welcome and Introductions | none |
| 112 | 11 | 325 | Summit Report, Part 2 | none |
| 112 | 12 | 326 | Wrap Up | none |
| 113 | 10 | 327 | Bienvenida | none |
| 113 | 11 | 328 | Progreso de 2025 | none |
| 113 | 12 | 329 | Resumen de la Cumbre de Burdeos | none |
| 113 | 13 | 330 | Discusión y Comentarios | none |
| 113 | 14 | 331 | Cierre y Próximos Pasos | none |
| 114 | 10 | 332 | Welcome and Introductions | none |
| 114 | 11 | 333 | Flu and COVID Coding | none |
| 114 | 12 | 334 | Year Ahead Brainstorming | none |
| 114 | 13 | 335 | Wrap Up | none |
| 115 | 10 | 336 | Welcome and Introductions | none |
| 115 | 11 | 337 | Vaccine Genie | none |
| 115 | 12 | 338 | Automatic Recognition of Paper Records | none |
| 115 | 13 | 339 | Wrap Up | none |
| 116 | 10 | 340 | Welcome and Introductions | none |
| 116 | 11 | 341 | Update on SNOMED CT Expo 2025 | SNOMED International (152, building-bridges) |
| 116 | 12 | 342 | NUVA Training | Unified Nomenclature of Vaccines (NUVA) (117, emerging-standards) |
| 116 | 13 | 343 | Wrap Up | none |
| 117 | 10 | 344 | Welcome and Introductions | none |
| 117 | 11 | 345 | Vaccination Information Sheets | none |
| 117 | 12 | 346 | Country Interview Process | Building Bridges (120, emerging-standards) |
| 117 | 13 | 347 | Publications of Mappings | Unified Nomenclature of Vaccines (NUVA) (117, emerging-standards) |
| 117 | 14 | 348 | Wrap Up | none |
| 118 | 10 | 349 | Welcome and Introductions | none |
| 118 | 11 | 350 | NUVA Use Cases: France and Switzerland | Unified Nomenclature of Vaccines (NUVA) (117, emerging-standards) |
| 118 | 12 | 351 | SNOMED Discussion | SNOMED International (152, building-bridges) |
| 118 | 13 | 352 | United States Update | United States (168, building-bridges) |
| 118 | 14 | 353 | NUVA Search App | Unified Nomenclature of Vaccines (NUVA) (117, emerging-standards) |
| 118 | 15 | 354 | Wrap Up | none |
| 119 | 10 | 355 | Welcome and Introductions | none |
| 119 | 11 | 356 | Country Updates | none |
| 119 | 12 | 357 | Meningococcal Introduction | none |
| 119 | 13 | 358 | Meningococcal Implementation | none |
| 119 | 14 | 359 | Target Disease | Unified Nomenclature of Vaccines (NUVA) (117, emerging-standards) |
| 119 | 15 | 360 | Wrap Up | none |
| 120 | 10 | 361 | Welcome and Introductions | none |
| 120 | 11 | 362 | Country Updates | none |
| 120 | 12 | 363 | Meningococcal Discussion | France (107, building-bridges) |
| 120 | 13 | 364 | Other Coded Values | none |
| 120 | 14 | 365 | Wrap Up | none |
| 121 | 10 | 366 | Welcome and Introductions | none |
| 121 | 11 | 367 | Updates | Building Bridges (120, emerging-standards) |
| 121 | 12 | 368 | Scope and Name Discussion | none |
| 121 | 13 | 369 | MMR/MMRV | none |
| 121 | 14 | 370 | Wrap Up | none |

## 4. Presentation manifest

- **Source root:** `C:/Users/NathanBunker/AIRA Dropbox/Nathan Bunker/Emerging Standards/IVC`
- **Staged path:** `C:/dev/immregistries/InteropHub-artifacts/<storage_key>`
- **Local download URL:** `http://localhost:8080/hub/files/<public_id>`

Every original filename is preserved in `original_filename`.

| Meeting | Item ID | File ID | Attach ID | Source (under `Emerging Standards/IVC`) | Bytes | Size exception | public_id / storage_key | SHA-256 |
| ---: | ---: | ---: | ---: | --- | ---: | --- | --- | --- |
| 92 | 228 | 1 | 1 | `Presentation/2023/6 June/International Vaccine Codes 2023-06-14.pptx` | 4,525,555 |  | `ef1e8b6d-f977-4760-8236-1b9638cd5632` / `9b792021-a501-4e35-94d1-8bca1009ef20` | `ea7e6d7a89a32b0ee90d1a63a006970e9617fb0a6e8d3de66030b172d8dd6cd7` |
| 92 | 230 | 2 | 2 | `Presentation/2023/6 June/230614-AIRA  codes meeting.pptx` | 435,310 |  | `fd5747ed-fe79-46c8-a1fe-f68bb8211156` / `2538d063-b386-4847-86d0-241f3a0f1194` | `89952d565a3d191472de9fd08a88601190dff3856504435ab8b2e4abc1126cb2` |
| 93 | 232 | 3 | 3 | `Presentation/2023/7 July/International Vaccine Codes 2023-07-12.pptx` | 6,118,680 |  | `fd26a179-7367-4010-ab8b-6e365886873c` / `3b9bd7e1-34d4-4f04-9612-55e56ff9b1ae` | `9424bf2c600a4a139464e15873d454f0891a88988d2e11bf248280201ff23554` |
| 93 | 235 | 4 | 4 | `Presentation/2023/7 July/2023-07-12 SHC Terminology Approach.pdf` | 2,524,173 |  | `d573a657-faf6-491e-8071-d52a7edbdbbd` / `2d7d047d-9881-4328-945b-f4354b82dc93` | `d1e996a8c7501be44650db7eab999518500c36fa5e3a363cbf25eeb5970beab2` |
| 94 | 237 | 5 | 5 | `Presentation/2023/8 August/International Vaccine Codes 2023-08-09.pptx` | 21,972,047 |  | `28a1e6d3-e4eb-4c38-94ee-95ee869e5b59` / `b5bc2540-07d9-4707-b83b-6a6212b5d649` | `6ab2b96003928520701acc64d036b1d82e1c29c6463814b65227420c245033f2` |
| 95 | 241 | 6 | 6 | `Presentation/2023/9 September/International Vaccine Codes 2023-09-20.pptx` | 5,678,298 |  | `9ec2dcad-fa0c-48be-a9e3-073b50ac642e` / `d3c84395-261c-4b8b-8373-4325f2d24432` | `cb0bc1b6776e671a77c0236c40ffaa9bbdf4b869fc042f5da785d9a2a31890c0` |
| 95 | 242 | 7 | 7 | `Presentation/2023/9 September/2023_09_20_WHODrug Global_ENG.pdf` | 3,424,988 |  | `6a6e2b95-f323-464b-9671-2194d7242b92` / `66d06705-dac2-47c7-9c97-e8d82cebdff5` | `f1b9ee4c98a5444ece567fec20d3e661db062ebd8ed1d2035bff7db42ed56842` |
| 96 | 246 | 8 | 8 | `Presentation/2023/10 October/International Vaccine Codes 2023.10.11.pptx` | 22,382,311 |  | `b40f059a-9862-4519-9435-4eb682b414de` / `3e772c7b-5033-439d-bd7b-6e51552d0b9b` | `e14b526ffb871a9b8c3c831dae81be7fabf236531bd17c091578bc1c5ad880c6` |
| 97 | 250 | 9 | 9 | `Presentation/2023/12 December/International Vaccine Codes 2023.12.13.pptx` | 5,663,040 |  | `0d542e20-40d5-4c9f-81d4-23f565cf7693` / `ee01c464-745a-4eee-9365-bea6d764dd45` | `2dfc0115415f04819f11aefe0ebc652c1ceebe63c2d126d12b92ff93ce323658` |
| 98 | 254 | 10 | 10 | `Presentation/2024/3 March/International Vaccine Codes 2024.03.20.pptx` | 3,980,451 |  | `4f400323-3ce5-4590-a92c-e9f40d141de9` / `2b65ef0c-418b-4d6a-9913-5b3e65e647b3` | `a2f87bf0d2b7a8e7513e36cc5a48e5f3a8bb8c77f782ffabf6e93104035cef3a` |
| 99 | 258 | 11 | 11 | `Presentation/2024/4 April/International Vaccine Codes 2024.04.17.pptx` | 5,251,139 |  | `af9d68b7-7657-485e-9889-fc1f09449d1e` / `cd22c501-bd9e-4e90-9f2d-f0360290099e` | `0ef2e1367b58136ebd4a764dbaa718f18b8ea5747698e3d45e48e35e08f74d94` |
| 99 | 261 | 12 | 12 | `Presentation/2024/4 April/240320-IVCI-Metrics- FINAL.pptx` | 316,264 |  | `073a804c-fe33-4b63-9faf-c62375173fbb` / `3f6ca736-1560-44d4-a2a1-ca142ee3c2db` | `1d90ac3ae75abb310ec7c544329cccf42e80d2476865903861f35f7cfb19bf50` |
| 100 | 263 | 13 | 13 | `Presentation/2024/6 June/International Vaccine Codes 2024.06.12.pptx` | 7,514,281 |  | `3fac4989-a984-4ccb-b081-04ff5a49154a` / `3abe0b9f-2f72-45a2-8d1f-001464a5a61c` | `c004b6c86c356d6a1c38133a67172bcb57311984839013d6dc52d66752787122` |
| 100 | 265 | 14 | 14 | `Presentation/2024/6 June/240612-IVCI.pptx` | 269,595 |  | `769bdc87-66e3-4acd-92f6-cafd08302f08` / `9b5cd91c-9116-4bad-bda6-2a2fc0a0a568` | `1bddb03b58368a3c88f3b44d966579dacb5704bf96a49808ef161e06ffe68ac4` |
| 101 | 267 | 15 | 15 | `Presentation/2024/7 July/International Vaccine Codes 2024.07.31.pptx` | 6,649,560 |  | `ff10ca07-ce40-4eed-929a-a53b10b5b3b8` / `5b9bd241-d31b-44a1-9cc9-984b5c8d6d23` | `a696870653d2ad26f44f70e8ee9282a19cdb3bc8af4944da3e2aa73e937a937a` |
| 102 | 272 | 16 | 16 | `Presentation/2024/10 October/International Vaccine Codes 2024.10.09.pptx` | 6,584,085 |  | `927a8049-93e7-4c91-adb3-6b9bf280de0f` / `acb48cae-5282-498a-91a1-57928294c1fd` | `6630b1128ca880c12adccbd01a4f2ec6de4e4ea2f4d7359307a3fd61f6931848` |
| 103 | 278 | 17 | 17 | `Presentation/2024/11 November/International Vaccine Codes 2024.11.27.pptx` | 5,536,956 |  | `97542530-5fa3-491c-ad9a-88fe55de922c` / `7e5fa7a5-c3a9-4e64-a648-a35a91f34c29` | `4120a96b0278e50bdcb6b0ed38a99df1e9bb0de0a54914241a7e3ba0437b5289` |
| 104 | 282 | 18 | 18 | `Presentation/2025/1 January/International Vaccine Codes 2025.01.08.pptx` | 4,540,773 |  | `5a420935-3f94-4d7d-b3c4-66ea1bfa7648` / `98f647a7-120c-499e-996e-15b0c26c4676` | `454d69dedd8412c4bc4a45464d6f4e40c1017b923456052a48f8bde1ba75db0d` |
| 106 | 292 | 21 | 21 | `Presentation/2025/3 March/International Vaccine Codes 2025.03.12.pptx` | 3,166,686 |  | `13f827f1-19e8-4e67-ba81-51c72d4299dc` / `c88ff187-07ee-437f-8103-d2d891a07430` | `98e6c024277c4fb15e5f3718640ee7be8fc3dfc377b7c9eb3fcbe2bcd75780ae` |
| 105 | 287 | 19 | 19 | `IVC en espanol/Presentation/IVC en español 2025-03_meeting review version.pptx` | 9,877,242 |  | `86fc0392-9b0f-470f-b7a6-a06a452d5371` / `684cfc72-af32-436b-b980-2d2309b36ebb` | `5bc97460eafd3e305ad21de95e1ee4c597055420482c7aa23c3f3fc5a3de3ff3` |
| 105 | 289 | 20 | 20 | `IVC en espanol/Presentation/2025_03_12_USA_AIRA_SPA_IVC en español_WHODrug Global.pdf` | 4,195,844 |  | `97604564-c91a-479b-9d82-ff557abaace7` / `9573439b-5c76-46ad-b49f-0d7d8b92eee7` | `b973922e51c707157f536561ec98afd4d26f5b1fb4d739e4685ad11a0a486747` |
| 108 | 300 | 23 | 23 | `Presentation/2025/4 April/International Vaccine Codes 2025.04.09.pptx` | 3,145,901 |  | `3bb9af1d-94b9-4313-985e-7952ad9e3941` / `47741fce-7bbd-47e8-922c-3f17b927325d` | `19a61ba6bc110e9e633de652f513bb97895e33185871c73986d41ec3ab42848f` |
| 107 | 296 | 22 | 22 | `IVC en espanol/Presentation/IVC en español 2025-04.pptx` | 8,054,013 |  | `74b88ed7-22a8-4efa-b1e4-2d1de5b6cc11` / `b7125759-da3e-4d24-84f0-ad70a1786fdc` | `0286668d7ef4fd81cb52688e81291af856bdc1e99b28e2d930e69f4b2ebf9e64` |
| 109 | 305 | 24 | 24 | `Bordeaux 2025/Presentations/IVC Training 2025-05-08-Nathan.pptx` | 16,322,148 |  | `f5f83daa-f515-48ed-9e84-605a7867d96b` / `523da3f4-5059-4ac5-ad10-4341421bb3d1` | `690b9fa1b01c18b5896a5f37264f01aa17a797c05fc8eb19c49e48011b6a777e` |
| 110 | 306 | 25 | 25 | `Bordeaux 2025/A01-FK-Welcome and goals of the meeting.pptx` | 2,010,485 |  | `81d8a650-4ece-4239-a0f0-1079f0921868` / `95d43404-8dc9-4e5d-847c-ca07a0e2aeac` | `991371dd17564419ca3240430431c0eacb1cd4409fd8fded9d6e006dab3520db` |
| 110 | 307 | 26 | 26 | `Bordeaux 2025/Presentations/A03-FK-NUVA- Why it matters.pdf` | 845,906 |  | `1a983f34-9e63-4a58-b7f8-1a4611ae1943` / `2b3212f9-8feb-401f-889d-65e81d32c8ec` | `33dfab41076d0f5aadca2098efe986d5d3ef1eee31ae22882eeb28c7e07d02c9` |
| 110 | 308 | 27 | 27 | `Bordeaux 2025/Presentations/a04-jlk-_valence_concept.pdf` | 641,594 |  | `ee7067e8-7628-46cf-8fca-a6ee636f7825` / `461922ed-aab1-4153-b7ed-b7a0437d122e` | `a7843a721469d6bc6cbc5f29752ff1218791d2cc12dde9868b9b7990e408844c` |
| 110 | 309 | 28 | 28 | `Bordeaux 2025/Presentations/A05_NUVA Extension to SNOMED CT.pdf` | 3,849,368 |  | `bccc2cc6-168c-47ae-9817-6a4146ca4872` / `3824018d-0780-45fc-b7ed-3ad3968016ba` | `5909f67e5989e3ec8e66aec456dcd61719ce12655a43ae2bd2c8c686efdef850` |
| 110 | 310 | 29 | 29 | `Bordeaux 2025/Presentations/B01-IW-View from the industry.pdf` | 1,936,414 |  | `920b504a-59b2-4caa-927f-bdca04f20c4a` / `808939ff-8a14-41b0-bff5-9c0e158df50a` | `b9d5fc2992e3139668852e9b5a51809ecca0b83765e789e790594fab3ba395f9` |
| 110 | 312 | 30 | 30 | `Bordeaux 2025/Presentations/B04-MF-WHODrug and IDMP for vaccines.pdf` | 1,883,984 |  | `a8afc867-8430-4a58-8b54-6598777b7a77` / `1197c519-ad94-4ef5-bd74-c7180e5da1d1` | `ae9c1ebb79c7b9b4d138352cf1f7ff4a612d36c5e1b53edeff599e92a45ec2d6` |
| 110 | 313 | 31 | 31 | `Bordeaux 2025/Presentations/c01-md-luxembourg_experience.pdf` | 1,475,396 |  | `bb631c76-278a-4bb7-933a-534d1a71f438` / `0f9cf0fc-fa1f-4982-bfda-2c9d1314d285` | `ebaec19d292f565e270898f8fcededd73a8ebddc054be8c19de5761a5e254a86` |
| 110 | 314 | 32 | 32 | `Bordeaux 2025/Presentations/C02-AC-The EUVABECO EVC project.pdf` | 1,104,906 |  | `fe9a5ef0-c26c-45f2-aa4e-852895a48e33` / `f85be11b-ad1a-414e-bbaf-c58733e1dfc1` | `0b179eeaff8940f5d0da650fa9117da4d82581c29ed9b1d689e70ebf0ddc748a` |
| 110 | 315 | 33 | 33 | `Bordeaux 2025/Presentations/D02-SC-US - Vaccine Coding.pdf` | 3,600,199 |  | `85a08544-db55-4fe2-9304-ec70c662e6eb` / `e8d3cafa-7224-4558-8fb2-83f4e0e0b8d5` | `657965fcf641830582b572e0b3a8ba3dd7e4e0dd03295716dbc5ddc02e8952fa` |
| 110 | 316 | 34 | 34 | `Bordeaux 2025/Presentations/D03-MT-Canada_Experiences_in_Vaccine_Coding.pdf` | 1,120,770 |  | `61253c43-b3be-49e6-8137-2360799bd73c` / `ead609c6-f6e3-4807-b350-ce9d6531554a` | `ece61324f98c2f63da951fc4e39124d91dcfb9707dea740e9b6b97c3f09ce02f` |
| 110 | 317 | 35 | 35 | `Bordeaux 2025/Presentations/E01-TD-Transcoding and aligning.pdf` | 854,339 |  | `c31f73ec-9095-47ad-ae77-a3878922fb43` / `17954148-34bf-478c-b4e7-7f0d72ade25b` | `439ce915b76f8c3ad7a3f34ae04fbc809a2decddcab5789918bff2384ec13586` |
| 110 | 318 | 36 | 36 | `Bordeaux 2025/Presentations/E02-FK-Metrics.pdf` | 668,256 |  | `370cefb5-a8a4-4175-a012-8114be04b5af` / `37283452-cd58-4734-8007-81f4549e6a94` | `6d36a0cff7692a862dbf8201e05ab703a406f4c110698f32e6fc85863cc04bd3` |
| 111 | 321 | 37 | 37 | `Presentation/2025/6 June/International Vaccine Codes 2025.06.11.pptx` | 60,078,075 | **yes** | `de5870b8-089d-469b-beef-0b85682ee7fe` / `86ee6e14-47a0-4cf6-93c1-70c74d654731` | `684bfeca90c4a66fb9debf5e02e77394fb8635a0c032b7b8dbff5ea76d544e4f` |
| 112 | 324 | 38 | 38 | `Presentation/2025/7 July/International Vaccine Codes 2025.07.09.pptx` | 26,738,884 | **yes** | `23154db6-eb06-4de2-984f-700570d37e20` / `570d89ad-b96c-49ac-9ab9-f6ae52a7126f` | `8453157bbf0c4c00b48651c31cc69e67aa0c7d6ca9bf49c9e68e867584531ba9` |
| 113 | 327 | 39 | 39 | `IVC en espanol/Presentation/International Vaccine Codes 2025.07.23 Esp.pptx` | 5,821,232 |  | `c4f2e791-046d-4325-821e-7844eccde5a1` / `054177c9-f49d-4c8c-bd0d-b3f5e90c3282` | `958ae668ff2e959aaab3b683e17b01037f254545d858e8dc3890dbbba5a245e9` |
| 114 | 332 | 40 | 40 | `Presentation/2025/9 Sep/International Vaccine Codes 2025.09.10.pptx` | 2,825,992 |  | `ceb10568-13c6-45fa-87f9-ef32b5e298b8` / `08b77bea-7985-4b4d-993c-2fcf7db9ef2e` | `8e48d7a478159a84003858646f1d24bcba2ae63cd50ce1011dfc9b71cc9f275e` |
| 115 | 336 | 41 | 41 | `Presentation/2025/10 Oct/International Vaccine Codes 2025.10.08.pptx` | 3,165,283 |  | `599dd208-2bb2-46ac-95ea-29f78a78285a` / `dae6787a-e556-4447-b9c6-412c590ff1ad` | `0bc8366a1ae7a636f9e8fc163b14d90fb363b614f2f6f78eda98ced4c20b8cc7` |
| 115 | 337 | 42 | 42 | `Presentation/2025/10 Oct/AIRA_15min (2).pptx` | 6,346,905 |  | `35ee0f3e-8eb0-4c40-8e57-6dd3b70bb55e` / `e38a1b82-1040-4405-bb0d-ccc9cd377223` | `0143b23a5a8cb9adcf7c66fd92f3a6734478708c1a6f95d6b8cee2d64d42ff4f` |
| 115 | 338 | 43 | 43 | `Presentation/2025/10 Oct/Syadem Presentation.pdf` | 9,805,025 |  | `3b6e4506-f655-4f84-86d9-50779a41dddb` / `2123cace-6ff4-4a87-b068-49befc58eda2` | `df30efed684afef2dbcbc2bc51357c4f07e66287db8fb6bb125763d8820b549b` |
| 116 | 340 | 44 | 44 | `Presentation/2025/11 Nov/International Vaccine Codes 2025.11.12.pptx` | 2,244,724 |  | `5daac571-61fa-4216-bb29-4a1ccf70b69a` / `2034ccf8-8853-4c89-b833-8218d042b589` | `b190d233c76a6e0080e1a2c2bb343e143289b3472bce6efa9d3e77dbc9bac610` |
| 116 | 342 | 45 | 45 | `Presentation/2025/11 Nov/251112-NUVA presentation for IVCI.pptx` | 4,806,532 |  | `d64fe397-923d-4301-bb2d-622b5054aa80` / `21bd3776-4278-4e32-a2d1-c8cfd0d8c583` | `de50eeaed29de7a2298a757f83bda68e8735a3fd301eb4c7fcbc20f40cdb1bc3` |
| 117 | 344 | 46 | 46 | `Presentation/2025/12 Dec/International Vaccine Codes 2025.12.10.pptx` | 9,960,233 |  | `55e47dd4-c213-4680-bb55-30f7fe6c476b` / `960e016d-3df4-469f-a55e-be01ddeef366` | `87640fb00f6c27a0cf8d5a3cd016c97ad951f1b4b5a3b95cda316f2971f94aa0` |
| 117 | 345 | 47 | 47 | `Presentation/2025/12 Dec/NUVAccess.pdf` | 517,251 |  | `2b6dffba-dc86-4b2f-8b5a-2bdda354fab5` / `6a934910-c2be-4e6e-8293-854f11729216` | `870eab5a5392fe54818284fddbe79cfec071646bafda6ebb555816dd0ea2f31b` |
| 117 | 346 | 48 | 48 | `Presentation/2025/12 Dec/Country Interview Review with IVC.pptx` | 8,619,611 |  | `8c860970-c5ca-4669-b947-5a1976d25cdf` / `533d3bd7-6f47-477f-8ba8-b987c6378298` | `018fd04a4b2fc2a77747f75141b0b8667962838930ef8a403a1759803477c8f2` |
| 117 | 347 | 49 | 49 | `Presentation/2025/12 Dec/251210-NUVA alignment files.pptx` | 2,615,064 |  | `e6470415-dbcc-483e-9d53-6d361056d2d2` / `4a4e16ad-ee87-4a6a-a52d-7fe12fa29d6d` | `e9859c36d1d0fc40d2f0b1454f209a6aa932b292da89f390fe1720550aab03d6` |
| 118 | 349 | 50 | 50 | `Presentation/2026/01 Jan/International Vaccine Codes 2026.01.14.pptx` | 2,490,720 |  | `aa8061b1-0c8b-4e50-97ed-82f4f71d43da` / `397e5856-22a7-4126-8533-a41501b5eecd` | `5bfd76cb49f16df663aa37a255d32230804d7786e115afb480e4872af75a5053` |
| 118 | 350 | 51 | 51 | `Presentation/2026/01 Jan/260114-NUVA use cases in France and Switzerland.pptx` | 2,583,827 |  | `ec4f46b6-2830-41ad-b803-9ba127b9da1b` / `677f6d60-125d-4837-b767-742093c469e7` | `71b9c4e848d8b3de44675af3afb96992276f1bc506ffd624d0a990fa4bc3f829` |
| 119 | 355 | 52 | 52 | `Presentation/2026/02 Feb/International Vaccine Codes 2026.02.11.pptx` | 2,647,119 |  | `9b598401-b4d7-4dd5-b8b9-5327a592b6d5` / `fb7f575f-3e55-4aa7-a874-6f615d5fffee` | `2b06795ca497755624c183424502231ea8df74980197d051d9bf4aefacbcac7e` |
| 119 | 357 | 53 | 53 | `Presentation/2026/02 Feb/20260210 Neisseria meningitidis.pptx` | 1,486,066 |  | `54c62a5b-c80f-44fa-b6c7-5bf83d415f79` / `2cd998dd-ed40-4478-9983-d3eed27bd4ce` | `73d6335ae43c9d69d16d1ce61035dfb36aaeb037f094b07d2e5d24574b6fa93a` |
| 119 | 359 | 54 | 54 | `Presentation/2026/02 Feb/260211-Concept of disease in NUVA.pptx` | 2,617,666 |  | `e28fcd99-12b1-4138-9b27-a3117dac10be` / `4b595958-5e74-4f3b-9489-32019b48096d` | `7e8616dec113672e81c251aa1aca1fdb6406908b928abee7902f73e1d8e59013` |
| 120 | 361 | 55 | 55 | `Presentation/2026/03 Mar/International Vaccine Codes 2026.03.11.pptx` | 2,732,052 |  | `eb6955e5-7cfa-4829-9e69-8c9306368a6d` / `e4bc85a9-d1f8-4684-b487-e669cef74cf4` | `ec4aec5737306e2b13625c5ece50474ac9f9160331789e966c39cb633888200c` |
| 120 | 363 | 56 | 56 | `Presentation/2026/03 Mar/MenB Rennes 2025_v3.pptx` | 2,611,266 |  | `fb7e7b04-bbf8-4c36-9f71-0a11c1b0eb1d` / `2f6d7dbd-cd51-403d-8bfe-428cd071e838` | `a063b2bbdf4ebff7ce549dec9f9532cd5acf0bf028368ef1b70ccaa174da3db7` |
| 121 | 366 | 57 | 57 | `Presentation/2026/05 May/International Vaccine Codes 2026.05.13.pptx` | 7,798,081 |  | `3e5a754d-08e8-429e-b350-23e2c3441adf` / `be44b8df-c3d8-44c0-9af2-bc0564c5b8fd` | `41899ccea51c64628e4f716d24cbb8eff70f280d5f94c657b3f54553fae54cd1` |

**Content type and disposition:**

- **PPTX:** stored as
  `application/vnd.openxmlformats-officedocument.presentationml.presentation`.
- **PDF:** stored as `application/pdf`.
- **All files:** `download_only = 1` (the meeting-upload policy), so PDFs also
  download rather than open inline.

**Validation:** every file passed InteropHub's own upload checks
(`StoredFileValidation.validate`) and the meeting-attachment type allowlist.
For the two size exceptions, every check except size was run, including the
Office package and macro checks.

## 5. Exclusions and gaps

- **Excluded file types and duplicates:**
  - agenda documents, notes, minutes, recordings, transcripts, chat;
  - attendance, registrations, contacts, invitations;
  - follow-up, planning, promotion, speaker photos, and event photos;
  - Mentimeter decks and results, and internal reports;
  - ZIP bundles, PDF exports of selected PPTX decks, drafts, and conflicted
    copies;
  - spreadsheets, screenshots, and undated general decks.
- **File-specific exclusions (Q8):**
  - `Vaccine Recommendations.pptx`: not on the January 2026 agenda.
  - The RIVM deck: its slides are already in the July 2023 main deck.
  - `Gap analysis v1_0.pdf`: a written report, not a presentation.
  - `Training NUVA.pptx`: embedded in the training deck.
- **Omitted meetings:**
  - 2025-09-24 Spanish meeting: no evidence it took place (Q5).
  - 2026-04-08: never held.
- **Source problems handled:**
  - Four agenda files were wrong or unfilled (listed in the manifest). Decks
    and DokuWiki were used instead.
  - The Spanish July deck's title slide says "11 June 2025". It was kept
    unchanged under 23 July, per the Spanish review.
- **Possible summaries for a later pass:**
  - **Bordeaux summit:** the reconciled event record, with outcomes
    language already reviewed.
  - **2024-03-20, 2024-04-17, 2025-09-10:** minutes exist and would need a
    privacy review.
  - **June/July 2025:** summit-review meetings.
- **Display limitation (not a migration defect):** topic pages
  (`/es/topic/{id}`) show the next upcoming meeting plus only the **3 most
  recent** past meetings (`EsTopicDetailServlet.buildTopicMeetingRows`).
  Historical links are stored and appear on agenda pages. On topics with newer
  meetings, such as NUVA, Building Bridges, and IVC itself, the older ones
  aren't listed on the topic page. A complete history would need a separate
  UI change.

## 6. SQL

- **Location:** the block at the end of `db/unapplied_updates.sql`, headed
  `-- IVC historical meeting backload (June 2023 - May 2026).`
- **How it runs:**
  1. Temporary tables hold the approved meetings, items (with topics named by
     `space_code` + `topic_name`), and files (with literal `public_id` and
     `storage_key` UUIDs).
  2. The `backload_ivc_historical_meetings()` procedure resolves the space,
     series, and user (`nbunker@immregistries.org`) by stable fields.
  3. It checks preconditions.
  4. It inserts everything in a single transaction.
  5. It verifies the counts (30 / 143 / 57 / 57) and rolls back on any
     failure.
- **Meeting rows:**
  - `status = CLOSED`, `close_method = MANUAL`.
  - `completed_at = closed_at =` the migration time, and `close_due_at` is 7
    days later (the app's manual-close path).
  - `created_by`, `completed_by`, and `closed_by` are Nathan's user.
  - No chair, scribe, start, attendance, or RSVP data.
  - Monthly meetings take the series description; special events have none.
- **Status history:** one `NULL → CLOSED` row per meeting (`USER`). No
  FINALIZED step was invented.
- **Agenda items:** `ACCEPTED` with `accepted_at` set. `time_minutes` is NULL.
- **Repeatability:**
  - If all 57 `public_id`s already exist and all 30 meetings match, the block
    does nothing.
  - If only some of the files exist, it fails.
  - If any IVC meeting before 2026-06-01 already exists, it fails.
  - If a topic link resolves to zero or more than one active public topic, it
    fails.
- **Production preconditions:**
  - The earlier task 1a/1b blocks in the same file (`hub_stored_file`,
    `es_meeting_agenda_attachment`) must apply first. They come first in the
    file.
  - The 57 staged files must already be in production's artifact directory.
  - `nbunker@immregistries.org` must exist.
  - No IVC meetings may exist before June 2026.

## 7. Verification

| Check | Result |
| --- | --- |
| SQL block applied to the current local DB, then rerun | Applied; the rerun was a no-op |
| `restore_interophub_db_from_latest_local.py` (backup of 2026-10-08 05:30) | Succeeded |
| DB counts after restore: meetings / items / topic links / files / attachments / status rows | 30 / 143 / 31 / 57 / 57 / 30 |
| Item and attachment mapping (non-Welcome decks on the intended items) | All 27 non-Welcome decks on the intended items |
| Stored files: `LOCAL`, `download_only`, unique keys, staged file present with matching size | 57/57 |
| Source SHA-256 = staged SHA-256 | 57/57 |
| Anonymous download: HTTP 200, SHA-256 = source, content type, `attachment`, UTF-8 filename | 57/57 |
| Anonymous agenda page per meeting: HTTP 200 and expected attachment-link count | 30/30 |
| Series page lists all 30, in order after meeting 6; same-day pairs ordered by time | Yes |
| Topic pages for all 13 linked topics plus IVC: HTTP 200 | Yes (3-meeting display limit noted in §5) |
| Signed-in (admin) agenda, workspace, series pages | HTTP 200, no errors |
| **Second restore** | Identical: meeting IDs 92–121, 143 items, 31 links, the same 57 public_ids; 60 MB deck downloads with the matching hash |

No Tomcat restart or redeploy was needed. This task made no application code
changes.

## 8. Nathan's production checklist (not performed)

1. Back up the production database **and** the production artifact directory
   together.
2. Copy only the 57 files listed in `ivc-historical-meetings-files.tsv`
   (column `storage_key`) from `C:\dev\immregistries\InteropHub-artifacts` into
   production's `INTEROPHUB_ARTIFACTS_LOCAL_DIRECTORY`. Keep the bare UUID
   names, with no extensions. The folder also contains your earlier local test
   uploads, which you plan to clean up separately. Optionally verify SHA-256
   against the `sha256` column.
3. Run `db/unapplied_updates.sql` against production, as part of the normal
   release.
4. Deploy the new WAR. The backload itself needs no code from it.
5. Post-deployment checks:
   - The series page shows the historical meetings.
   - Open the Bordeaux summit and the 2025-03-12 Spanish meeting.
   - Download one PDF, one PPTX, and the 60 MB 2025-06-11 deck. Compare their
     SHA-256 values with these:
     - 2025-06-11 deck (`public_id` `de5870b8-089d-469b-beef-0b85682ee7fe`):
       `684bfeca90c4a66fb9debf5e02e77394fb8635a0c032b7b8dbff5ea76d544e4f`
     - Bordeaux A03 PDF (`1a983f34-9e63-4a58-b7f8-1a4611ae1943`):
       `33dfab41076d0f5aadca2098efe986d5d3ef1eee31ae22882eeb28c7e07d02c9`
     - March Spanish deck (`86fc0392-9b0f-470f-b7a6-a06a452d5371`):
       `5bc97460eafd3e305ad21de95e1ee4c597055420482c7aa23c3f3fc5a3de3ff3`
6. **Recovery:**
   - The block runs in a transaction, so a failure leaves no partial rows.
   - To undo after success, restore from the step 1 backup. The staged files
     are harmless if left unreferenced.
   - A rerun is safe: it does nothing once the backload is complete.

## 9. Website handoff

- **Stable landing page:**
  `https://informatics.immregistries.org/hub/es/meeting-series?seriesId=1`.
  It covers June 2023 onward, with English, Spanish, and Bordeaux meetings in
  one chronological list. The website should normally link here.
- **Direct Bordeaux links:** these exist only after production deployment,
  because production assigns its own IDs. Find them on the series page:
  - *IVC Vaccine Code Training — Bordeaux* (2025-05-08)
  - *International Summit on Vaccine Coding & Standards — Bordeaux*
    (2025-05-09)

  The URL shape is `…/hub/es/agenda?meetingId=<id>`.
- **Caveats:**
  - Start times are nominal (10:00 ET, or 09:00 for the two same-day Spanish
    meetings) and don't reflect actual historical times.
  - The decks are dated historical artifacts. Several use the former IVCI
    name and contain time-bound claims.
  - The planned WHO and PAHO summit slots are intentionally absent.
  - The EU strategy session has no slides.
  - No meeting summaries are published.
- **Don't duplicate:** the website should not duplicate agendas or the deck
  archive.

## 10. Changed files

**Repository files changed:**
- `db/unapplied_updates.sql`: the appended backload block.
- `docs/tasks/backload-ivc-meetings.md`: the task revisions.
- `docs/tasks/ivc-historical-meetings-manifest.md`: the plan and decisions.
- `docs/tasks/ivc-historical-meetings-files.tsv`: the file and UUID mapping.
- This report.
- `db/schema.sql`: regenerated by the restore script, not hand-edited.

**Outside the repository:**
- 57 new files in `C:\dev\immregistries\InteropHub-artifacts`.
- Source files in Dropbox: not modified.

**Commits:** none. The changes are uncommitted for Nathan's review. There was
no unrelated dirty work; the tree was clean at the start apart from the
regenerated `schema.sql`.
