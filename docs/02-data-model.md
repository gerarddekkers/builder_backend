# Datamodel

## Overzicht

Er zijn twee database-contexten:

1. **Metro database** (AWS RDS MySQL) — de bron van waarheid voor assessments, learning journeys, competenties, categorieën. Wordt gelezen en geschreven door de builder, en gelezen door het Metro platform (afname, rapportage).
2. **Builder tabellen** (in dezelfde Metro DB) — applicatie-specifiek: gebruikers, projecten. Worden alleen door de builder gebruikt.

De builder schrijft naar Metro via **raw SQL** (geen JPA mappings voor Metro tabellen). Alleen `builder_users` en item-gerelateerde tabellen hebben JPA entities.

## Metro Database — Assessment Tabellen

```mermaid
erDiagram
    questionnaires ||--o{ questionnaire_translations : "vertaling"
    questionnaires ||--o{ questionnaire_items : "bevat"
    questionnaires ||--o{ group_questionnaires : "toegewezen aan"
    categories ||--o{ competences : "bevat"
    competences ||--o{ competence_items : "gekoppeld aan"
    competences ||--o{ goals : "heeft"
    items ||--o{ item_translations : "vertaling"
    items ||--o{ competence_items : "gekoppeld aan"
    items ||--o{ questionnaire_items : "in vragenlijst"
    items ||--o{ competence_questions : "scoring"
    groups ||--o{ group_questionnaires : "toegewezen aan"

    questionnaires {
        bigint id PK
        varchar name
        varchar description
        varchar instruction
        timestamp created_at
    }

    questionnaire_translations {
        bigint questionnaire_id FK
        varchar language "nl of en"
        varchar name
        text description
        text instruction
        varchar questions_url "S3 XML URL"
        varchar report_url "S3 XML URL"
    }

    categories {
        bigint id PK
        varchar name
    }

    competences {
        bigint id PK
        bigint category_id FK
        varchar name
    }

    goals {
        bigint id PK
        bigint competence_id FK
        varchar name
    }

    items {
        bigint id PK
        varchar name
        tinyint invertOrder "0 of 1"
    }

    item_translations {
        bigint item_id FK
        varchar language "nl of en"
        text leftText "linkerpolariteit"
        text rightText "rechterpolariteit"
    }

    competence_items {
        bigint competence_id FK
        bigint item_id FK
    }

    questionnaire_items {
        bigint questionnaire_id FK
        bigint item_id FK
        int sort_order
    }

    competence_questions {
        bigint competence_id FK
        bigint item_id FK
        bigint questionnaire_id FK
    }

    groups {
        bigint id PK
        varchar name
    }

    group_questionnaires {
        bigint group_id FK
        bigint questionnaire_id FK
    }
```

### Toelichting Assessment tabellen

- **questionnaires**: Hoofdtabel. Elke assessment is een questionnaire.
- **questionnaire_translations**: NL en EN naam, beschrijving, instructie + S3 URLs naar de XML bestanden (questionnaire XML + report XML).
- **categories**: Competentiecategorieën (bijv. "Leiderschap", "Communicatie").
- **competences**: Competenties binnen een categorie (bijv. "Motiveren", "Delegeren").
- **goals**: Doelen per competentie (1-op-1 met competentie in huidige implementatie).
- **items**: Vraagitems met een 5-punts schaal (links/rechts polariteiten).
- **item_translations**: Meertalige tekst per item (leftText = linkerpool, rightText = rechterpool).
- **competence_items**: Koppeling item ↔ competentie.
- **questionnaire_items**: Koppeling item ↔ questionnaire met sorteervolgorde.
- **competence_questions**: Scoring-koppeling. **Let op triggers hieronder.**
- **group_questionnaires**: Welke groepen toegang hebben tot welke questionnaire.

### Trigger Optimalisatie (competence_questions)

De `competence_questions` tabel heeft 3 triggers die bij elke INSERT/UPDATE/DELETE de stored procedure `metro.calculate_user_competence_scores_for_all_assessments()` aanroepen. Deze procedure duurt ~10 seconden per aanroep.

**Probleem**: Bij het publiceren van een assessment worden tientallen rijen ge-INSERT. Zonder optimalisatie: 50 inserts × 10s = ~500s.

**Oplossing** (`MetroLookupRepository.executeSqlStatements()`):
1. `DROP TRIGGER IF EXISTS recalculate_user_competence_scores_on_insert_2`
2. `DROP TRIGGER IF EXISTS recalculate_user_competence_scores_on_update_2`
3. `DROP TRIGGER IF EXISTS recalculate_user_competence_scores_on_delete_2`
4. Alle SQL statements uitvoeren
5. Triggers recreëren (maar de stored procedure wordt **niet** aangeroepen na recreatie)

**Resultaat**: 52 statements in 313ms (was 21.5 seconden), 20× sneller.

**Bestand**: `backend/src/main/java/com/mentesme/builder/service/MetroLookupRepository.java`

## Metro Database — Learning Journey Tabellen

```mermaid
erDiagram
    learning_journeys ||--o{ steps : "bevat"
    learning_journeys ||--o{ learning_journey_documents : "bijlagen"
    learning_journeys ||--o{ group_learning_journey : "toegewezen aan"
    steps ||--o{ step_question : "vragen"
    labels ||--o{ steps : "titel/tekst via identifier"

    learning_journeys {
        bigint id PK
        varchar name
        varchar name_en
        varchar lj_key "unieke slug"
        text description
        text description_en
        tinyint ai_coach_enabled "optioneel kolom"
    }

    steps {
        bigint id PK
        bigint learning_journey_id FK
        int position
        varchar title "label identifier"
        text text_content "label identifier"
        varchar type "substap|hoofdstap|afsluiting"
        varchar colour "hex kleurcode"
        varchar size "large|medium"
        varchar role
        text conversation
        text documents "JSON"
    }

    step_question {
        bigint id PK
        bigint step_id FK
        int question_order
        varchar type "open|menteeValuation|mentorValuation"
        varchar question "label identifier"
    }

    labels {
        varchar identifier PK
        varchar lang "nl of en"
        text text
    }

    learning_journey_documents {
        bigint id PK
        varchar identifier "LJ_{ljId}_{docOrder}"
        varchar label
        varchar url "S3 URL"
        varchar lang "nl of en"
    }

    group_learning_journey {
        bigint group_id FK
        bigint learning_journey_id FK
    }
```

### Toelichting Learning Journey tabellen

- **learning_journeys**: Hoofdtabel met naam (NL+EN), beschrijving, unieke key, optioneel AI coach flag.
- **steps**: Stappen binnen een journey. Het `type` bepaalt de structuur:
  - `hoofdstap`: genummerde hoofdstap (large, blauw/oranje/paars)
  - `substap`: geneste substap (medium)
  - `afsluiting`: afsluitende stap (laatste)
- **step_question**: Vragen per stap (max 5 per substap). Het `question` veld verwijst naar een `labels.identifier`.
- **labels**: Meertalig label-systeem. Identifier koppelt een NL en EN tekst aan steps/vragen.
- **learning_journey_documents**: Bijlagen per journey (PDF, Word etc.), per taal.
- **group_learning_journey**: Groepstoewijzing.

### Structuurafleiding (colour/size → type)

De Metro database slaat step type af als `colour` + `size` combinatie. De builder leidt het structurele type af:

| colour | size | → structuralType |
|--------|------|-----------------|
| blauw/oranje/paars | large | hoofdstap |
| * | medium | substap |
| grijs | * | afsluiting |

**Bestand**: `backend/src/main/java/com/mentesme/builder/service/LearningJourneyLookupRepository.java`

## Builder Tabellen

```mermaid
erDiagram
    builder_users {
        bigint id PK "AUTO_INCREMENT"
        varchar username "UNIQUE, max 100"
        varchar display_name "max 255"
        varchar password_hash "BCrypt"
        enum role "ADMIN of BUILDER"
        tinyint active "default 1"
        tinyint access_assessment_test "default 0"
        tinyint access_assessment_prod "default 0"
        tinyint access_journeys_test "default 0"
        tinyint access_journeys_prod "default 0"
        timestamp created_at
        timestamp updated_at
    }
```

### Toelichting

- **builder_users**: Authenticatie en autorisatie. Elke gebruiker heeft een rol (ADMIN/BUILDER) en 4 access flags die bepalen welke omgevingen zichtbaar/bruikbaar zijn.
- Tabel wordt automatisch aangemaakt bij opstarten (`UserService.ensureTableExists()`).
- Admin user wordt automatisch geseeded als er geen users bestaan (username: `support@mentes.me`, wachtwoord uit `BUILDER_AUTH_PASSWORD` env var).
- Migratie: nieuwe kolommen worden via `ALTER TABLE ADD COLUMN` toegevoegd als ze ontbreken.

**Bestand**: `backend/src/main/java/com/mentesme/builder/service/UserService.java`

## JPA vs Raw SQL

| Tabel(len) | Methode | Reden |
|------------|---------|-------|
| builder_users | JPA (BuilderUser entity) | Standaard CRUD, past goed bij JPA |
| items, item_translations, competence_items, questionnaire_items | JPA entities | Historisch; worden nu via raw SQL geschreven |
| questionnaires, categories, competences, goals, etc. | Raw SQL (JDBC) | Complex Metro schema; sequentie-generatie via `MAX(id)+1` |
| learning_journeys, steps, labels, etc. | Raw SQL (JDBC) | Bulk inserts met labels-systeem |
| Alle lookups (search, list) | Raw SQL (JDBC) | Complexe joins, performance |

### ID-generatie

Metro gebruikt geen AUTO_INCREMENT voor alle tabellen. De builder genereert IDs via:
```sql
SELECT MAX(id) FROM questionnaires  -- +1 voor nieuwe questionnaire
SELECT MAX(id) FROM categories      -- +1 voor nieuwe category
-- etc.
```
Dit wordt in één query opgehaald via `MetroLookupRepository.getAllMaxIds()`.

**Risico**: Bij gelijktijdige inserts kan een race condition optreden. In de praktijk is dit acceptabel omdat de builder single-user per assessment werkt.
