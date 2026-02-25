# API Reference

Base URL: `/api` (relatief aan de deployment)

## Authenticatie

Endpoints die auth vereisen verwachten een `Authorization: Bearer <token>` header. Zie [Authenticatie](04-authentication.md) voor details over het token formaat.

De `AuthFilter` skipt: `OPTIONS`, `/api/auth/*`, `/api/health`, `/api/db-*`.

## Endpoint Overzicht

| Method | Path | Auth | Access Check | Controller |
|--------|------|------|-------------|------------|
| **Auth** | | | | |
| POST | `/api/auth/login` | Nee | — | AuthController |
| GET | `/api/auth/status` | Nee | — | AuthController |
| **Zoeken & Lookups** | | | | |
| GET | `/api/categories/search?q=` | Ja | — | BuilderController |
| GET | `/api/competences/search?q=` | Ja | — | BuilderController |
| GET | `/api/groups/search?q=` | Ja | — | BuilderController |
| GET | `/api/questionnaires/check?name=` | Ja | — | BuilderController |
| **Vertaling** | | | | |
| POST | `/api/translate` | Ja | — | BuilderController |
| **Assessment Publicatie** | | | | |
| POST | `/api/assessments/xml-preview` | Ja | — | BuilderController |
| POST | `/api/questionnaires/publish` | Ja | assessmentTest | QuestionnairePublishController |
| POST | `/api/questionnaires/publish-test` | Ja | assessmentTest | QuestionnairePublishController |
| POST | `/api/questionnaires/publish-production` | Ja | assessmentProd | QuestionnairePublishController |
| **Learning Journey Publicatie** | | | | |
| POST | `/api/learning-journeys/publish` | Ja | journeysTest | LearningJourneyPublishController |
| POST | `/api/learning-journeys/publish-test` | Ja | journeysTest | LearningJourneyPublishController |
| POST | `/api/learning-journeys/publish-production` | Ja | journeysProd | LearningJourneyPublishController |
| DELETE | `/api/learning-journeys/{id}` | Ja | journeysTest | LearningJourneyPublishController |
| **Learning Journey Lookups** | | | | |
| GET | `/api/learning-journeys` | Ja | — | LearningJourneyController |
| GET | `/api/learning-journeys/{id}` | Ja | — | LearningJourneyController |
| **Assessment Definities** | | | | |
| GET | `/api/assessment-definitions?q=` | Ja | — | AssessmentDefinitionController |
| GET | `/api/assessment-definitions/{id}` | Ja | — | AssessmentDefinitionController |
| GET | `/api/assessment-definitions/{id}/groups` | Ja | — | AssessmentDefinitionController |
| POST | `/api/assessment-definitions/compose` | Ja | — | AssessmentDefinitionController |
| **Gebruikersbeheer** | | | | |
| GET | `/api/admin/users` | Ja | ADMIN rol | UserAdminController |
| POST | `/api/admin/users` | Ja | ADMIN rol | UserAdminController |
| PUT | `/api/admin/users/{id}` | Ja | ADMIN rol | UserAdminController |
| PUT | `/api/admin/users/{id}/password` | Ja | ADMIN rol | UserAdminController |
| DELETE | `/api/admin/users/{id}` | Ja | ADMIN rol | UserAdminController |
| **Projecten** | | | | |
| GET | `/api/projects` | Ja | — | ProjectController |
| GET | `/api/projects/{id}` | Ja | — | ProjectController |
| PUT | `/api/projects/{id}` | Ja | — | ProjectController |
| DELETE | `/api/projects/{id}` | Ja | — | ProjectController |
| **Bestandsuploads** | | | | |
| POST | `/api/documents/upload` | Ja | — | DocumentUploadController |
| POST | `/api/images/upload` | Ja | — | ImageUploadController |
| **Diagnostiek (dev)** | | | | |
| GET | `/api/health` | Nee | — | HealthController |
| DELETE | `/api/db-cleanup?fromId=&toId=` | Ja | — | HealthController |
| GET | `/api/db-questionnaires` | Ja | — | HealthController |
| GET | `/api/db-translations` | Ja | — | HealthController |
| GET | `/api/db-perf-test` | Ja | — | HealthController |

---

## Auth Endpoints

### POST `/api/auth/login`

Login en ontvang een JWT token.

**Request:**
```json
{
  "username": "pepijn@mentes.me",
  "password": "welkom123"
}
```

**Response (200):**
```json
{
  "token": "MTpzdXBwb3J0QG1lbnRlcy5tZTpBRE1JTjo...",
  "role": "ADMIN",
  "displayName": "MentesMe Support",
  "userId": 1,
  "access": {
    "assessmentTest": true,
    "assessmentProd": true,
    "journeysTest": true,
    "journeysProd": true
  }
}
```

**Response (401):**
```json
{ "error": "Onjuiste inloggegevens" }
```

### GET `/api/auth/status`

Check of authenticatie ingeschakeld is.

**Response:**
```json
{ "authEnabled": true }
```

---

## Zoeken & Lookups

### GET `/api/categories/search?q={zoekterm}`

Zoek competentiecategorieën in Metro DB. Zoekt in zowel de categorie naam als vertalingen.

**Response:**
```json
[
  { "id": 42, "nameNl": "Leiderschap", "nameEn": "Leadership" }
]
```

### GET `/api/competences/search?q={zoekterm}`

Zoek competenties. Zoekt in competentie naam + vertalingen.

**Response:**
```json
[
  { "id": 105, "nameNl": "Motiveren", "nameEn": "Motivating" }
]
```

### GET `/api/groups/search?q={zoekterm}`

Zoek groepen (organisaties/teams die assessments afnemen).

**Response:**
```json
[
  { "id": 7, "name": "MentesMe Intern" }
]
```

### GET `/api/questionnaires/check?name={naam}`

Controleer of een assessment naam al bestaat.

**Response:**
```json
{ "exists": true, "questionnaireId": 456 }
```

---

## Vertaling

### POST `/api/translate`

Vertaal teksten via Google Cloud Translation API (fallback: AI vertaling).

**Request:**
```json
{
  "sourceLanguage": "nl",
  "targetLanguage": "en",
  "texts": ["Motiveren van medewerkers", "Delegeren van taken"]
}
```

**Response:**
```json
{
  "translations": ["Motivating employees", "Delegating tasks"],
  "warning": null
}
```

Als Google niet geconfigureerd is, bevat `warning` een melding en wordt AI vertaling gebruikt.

---

## Assessment Publicatie

### POST `/api/questionnaires/publish-test`

Publiceer een assessment naar de Metro test database. Vereist `assessmentTest` access flag.

**Request:**
```json
{
  "assessmentName": "Persoonlijk Leiderschap",
  "assessmentNameEn": "Personal Leadership",
  "assessmentDescription": "Assessment voor leiderschapscompetenties",
  "assessmentDescriptionEn": "Leadership competency assessment",
  "assessmentInstruction": "Beoordeel uzelf op onderstaande stellingen",
  "assessmentInstructionEn": "Rate yourself on the following statements",
  "groupIds": [7, 12],
  "competences": [
    {
      "category": "Leiderschap",
      "categoryEn": "Leadership",
      "categoryDescription": "",
      "categoryDescriptionEn": "",
      "subcategory": "",
      "subcategoryEn": "",
      "subcategoryDescription": "",
      "subcategoryDescriptionEn": "",
      "name": "Motiveren",
      "nameEn": "Motivating",
      "description": "Het vermogen om anderen te inspireren",
      "descriptionEn": "The ability to inspire others",
      "questionLeft": "Kan anderen niet motiveren",
      "questionLeftEn": "Cannot motivate others",
      "questionRight": "Motiveert anderen uitstekend",
      "questionRightEn": "Excels at motivating others",
      "isNew": true,
      "existingId": null
    }
  ],
  "editQuestionnaireId": null
}
```

**Response (201):**
```json
{
  "questionnaireId": 789,
  "success": true,
  "timings": {
    "generatePreview_ms": 45,
    "executeSql_ms": 312,
    "xmlAndS3Upload_ms": 890,
    "total_ms": 1247,
    "slowestStatements": [
      { "index": 12, "elapsed_ms": 45, "sql": "INSERT INTO questionnaire_items..." }
    ]
  }
}
```

### POST `/api/questionnaires/publish-production`

Identiek aan publish-test maar schrijft naar de productie Metro DB. Vereist `assessmentProd` access flag.

### POST `/api/assessments/xml-preview`

Genereer XML preview zonder te publiceren. Zelfde request body als publish.

**Response:**
```json
{
  "questionnaireNl": "<?xml version='1.0'...>",
  "questionnaireEn": "<?xml version='1.0'...>",
  "reportNl": "<?xml version='1.0'...>",
  "reportEn": "<?xml version='1.0'...>",
  "warnings": []
}
```

---

## Learning Journey Publicatie

### POST `/api/learning-journeys/publish-test`

Publiceer een learning journey naar Metro test. Vereist `journeysTest` access flag.

**Request:**
```json
{
  "name": "Onboarding Programma",
  "nameEn": "Onboarding Program",
  "description": "Introductie voor nieuwe medewerkers",
  "descriptionEn": "Introduction for new employees",
  "groupIds": [7],
  "aiCoachEnabled": false,
  "steps": [
    {
      "structuralType": "hoofdstap",
      "title": { "nl": "Welkom", "en": "Welcome" },
      "textContent": { "nl": "Welkom bij MentesMe", "en": "Welcome to MentesMe" },
      "blocks": [],
      "questions": [],
      "documents": []
    },
    {
      "structuralType": "substap",
      "title": { "nl": "Over ons", "en": "About us" },
      "textContent": { "nl": "MentesMe is...", "en": "MentesMe is..." },
      "blocks": [],
      "questions": [
        { "type": "open", "question": { "nl": "Wat vind je interessant?", "en": "What do you find interesting?" } }
      ],
      "documents": [
        { "fileName": "welkomstgids.pdf", "lang": "nl", "url": "https://metro-platform.s3.amazonaws.com/docs/welkomstgids.pdf" }
      ]
    },
    {
      "structuralType": "afsluiting",
      "title": { "nl": "Afsluiting", "en": "Conclusion" },
      "textContent": { "nl": "Bedankt voor het doorlopen", "en": "Thank you for completing" },
      "blocks": [],
      "questions": [],
      "documents": []
    }
  ],
  "editLearningJourneyId": null
}
```

**Response (201):**
```json
{
  "learningJourneyId": 45,
  "success": true
}
```

### Validatieregels

- Naam verplicht, max 50 tekens
- Minimaal 1 groep
- Minimaal 2 hoofdstappen
- Laatste stap moet type `afsluiting` zijn
- Max 5 vragen per substap
- Document bestandsnamen: `^[a-zA-Z0-9][a-zA-Z0-9._-]*$`
- Talen: alleen `nl` en `en`

---

## Gebruikersbeheer (ADMIN only)

### GET `/api/admin/users`

Lijst van alle gebruikers.

**Response:**
```json
[
  {
    "id": 1,
    "username": "support@mentes.me",
    "displayName": "MentesMe Support",
    "role": "ADMIN",
    "active": true,
    "accessAssessmentTest": true,
    "accessAssessmentProd": true,
    "accessJourneysTest": true,
    "accessJourneysProd": true,
    "createdAt": "2026-02-18T10:30"
  }
]
```

### POST `/api/admin/users`

Maak een nieuwe gebruiker.

**Request:**
```json
{
  "username": "pepijn@mentes.me",
  "displayName": "Pepijn",
  "password": "welkom123",
  "role": "BUILDER",
  "accessAssessmentTest": true,
  "accessAssessmentProd": true,
  "accessJourneysTest": true,
  "accessJourneysProd": true
}
```

### PUT `/api/admin/users/{id}`

Update gebruiker (alle velden optioneel).

**Request:**
```json
{
  "displayName": "Pepijn de Vries",
  "role": "ADMIN",
  "active": true,
  "accessAssessmentTest": true,
  "accessAssessmentProd": false,
  "accessJourneysTest": true,
  "accessJourneysProd": false
}
```

### PUT `/api/admin/users/{id}/password`

Wijzig wachtwoord.

**Request:**
```json
{ "password": "nieuw-wachtwoord" }
```

### DELETE `/api/admin/users/{id}`

Deactiveer gebruiker (soft delete: `active = false`).

---

## Bestandsuploads

### POST `/api/documents/upload`

Upload een document naar S3. Max 25MB. Multipart form data.

**Toegestane MIME types:** PDF, Word (.doc/.docx), Excel (.xls/.xlsx), PowerPoint (.ppt/.pptx), afbeeldingen (JPEG, PNG, GIF).

**Response:**
```json
{ "url": "https://metro-platform.s3.amazonaws.com/documents/welkomstgids.pdf" }
```

### POST `/api/images/upload`

Upload een afbeelding naar S3. Max 10MB. Multipart form data.

**Toegestane MIME types:** JPEG, PNG, GIF, WebP, SVG.

**Response:**
```json
{ "url": "https://metro-platform.s3.amazonaws.com/images/foto.jpg" }
```

---

## Error Responses

Alle endpoints volgen hetzelfde error formaat:

**401 Unauthorized:**
```json
{ "error": "Missing or invalid Authorization header" }
```

**403 Forbidden:**
```json
{ "error": "Geen toegang tot deze omgeving" }
```

**400 Bad Request (validatie):**
```json
{
  "error": "Validation failed",
  "details": { "name": "Naam is verplicht" }
}
```

**409 Conflict:**
```json
{ "error": "Gebruikersnaam bestaat al" }
```

**500 Internal Server Error:**
```json
{ "error": "Publicatie mislukt", "details": "..." }
```
