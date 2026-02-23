# Assessment Builder - Project Context

## Wat is dit?
Assessment Builder van MentesMe: een full-stack web app voor het bouwen van competentie-assessments. Tweetalig (NL/EN). Assessments worden opgeslagen in de Metro database en gebruikt voor 360-graden feedback, zelfevaluaties en coaching.

## Stack
- **Frontend**: React 18 + Vite + TypeScript (port 5173)
- **Backend**: Spring Boot 3.3.5 + Java 17 + JPA (port 8080)
- **Database**: Metro MySQL (AWS RDS) - zowel read (lookups) als write (assessment opslag)
- **Packaging**: WAR voor Tomcat deployment
- **Taal in code**: Nederlands primair, Engels als vertaling

## Projectstructuur
```
frontend/src/
  App.tsx              # Hoofdcomponent (~1700 regels, moet opgesplitst)
  types.ts             # Form state types (UI) + API payload types
  api.ts               # Alle API calls
  auth.ts              # JWT auth utilities
  Login.tsx            # Login pagina
  AssessmentView.tsx   # Assessment viewer
  i18n/nl.ts           # Nederlandse UI-vertalingen
  styles.css           # Styling

backend/src/main/java/com/mentesme/builder/
  api/
    BuilderController.java    # Hoofd REST endpoints
    AuthController.java       # Login/token endpoints
    HealthController.java     # Health check
  service/
    MetroIntegrationService.java   # SQL generatie + opslaan naar Metro
    MetroLookupRepository.java     # Read-only queries (competenties, categorieën)
    GoogleTranslationService.java  # Google Cloud Translation
    AiTranslationService.java     # OpenAI/Claude vertaling (fallback)
    TokenService.java             # JWT tokens
  entity/                         # JPA entities (Item, ItemTranslation, etc.)
  repository/                     # Spring Data repositories
  model/                          # Request/Response DTOs
  config/                         # Auth, datasource configuratie

backend/src/main/resources/
  application.yml       # Configuratie (LET OP: bevat hardcoded credentials)
  schema-items.sql      # DDL voor items/competence_items/questionnaire_items
```

## API Endpoints
- `GET  /api/health` - Health check
- `POST /api/auth/login` - Inloggen
- `GET  /api/auth/status` - Auth status
- `GET  /api/categories/search?q=` - Categorieën zoeken in Metro
- `GET  /api/competences/search?q=` - Competenties zoeken in Metro
- `POST /api/translate` - Vertaling (Google of AI)
- `GET  /api/questionnaires/check?name=` - Duplicate check
- `POST /api/assessments/build` - Assessment aanmaken in Metro

## Huidige functionaliteit
1. **Wizard stap 1**: Assessment naam/beschrijving + competentie-matrix (categorieën + competenties zoeken/aanmaken)
2. **Wizard stap 2**: Vragen maken (links/rechts polariteiten voor 5-puntsschaal items)
3. **Vertaling**: NL→EN automatisch via Google/AI
4. **Opslaan**: Naar Metro DB via SQL generatie (questionnaires, categories, competences, items)
5. **Lokaal projectbeheer**: localStorage voor tussentijds opslaan
6. **Authenticatie**: JWT login

## Bekende issues
- Frontend wordt niet meegebuild in WAR (deployment naar builder.mentes.me serveert geen frontend)
- App.tsx is te groot (~1700 regels) - moet opgesplitst in componenten
- Database credentials hardcoded in application.yml (moeten naar env vars)
- SQL generatie gebruikt string concatenation i.p.v. parameterized queries
- Geen tests aanwezig
- i18n onvolledig: geen EN vertaalbestand, geen taalswitch in UI

## Visie / Roadmap
Het platform groeit van "assessment builder" naar een **ontwikkelplatform**:
1. Assessment bouwen (huidige builder)
2. Assessment afnemen (bestaand in Metro)
3. **Interactief rapport** (i.p.v. statische XML/PDF rapporten)
4. **AI-begeleide coaching** via iGROW of STARRT methodiek
5. Actieplannen + follow-up
6. Hermeting en voortgang

### Coaching methodieken (multi-methodology support)
- **iGROW**: Insight → Goal → Reality → Options → Will
- **STARRT**: Situatie → Taak → Actie → Resultaat → Reflectie → Transfer
- Partners kunnen eigen methodieken hanteren

### Rapport-gebruikers
- **Mentee**: bekijkt eigen resultaten, doorloopt coaching
- **Coach/mentor/psycholoog**: bereidt sessie voor, begeleidt mentee, ziet sessie-historie

### SCS (Social Communication Styles)
Bestaande XML-gebaseerde rapporten die geleidelijk gemigreerd moeten worden naar de nieuwe interactieve structuur. Parallel laten draaien tijdens migratie.

## Conventies
- Taal in UI: Nederlands primair
- Alle teksten tweetalig opslaan (NL + EN)
- Metro database is de bron van waarheid voor competenties, categorieën en assessments
- Assessment Builder schrijft naar Metro; leest ook uit Metro voor lookups

## Commando's
```bash
# Frontend development
cd frontend && npm run dev

# Backend development
cd backend && mvn spring-boot:run

# Frontend build
cd frontend && npm run build

# Backend build (WAR)
cd backend && mvn clean package
```
