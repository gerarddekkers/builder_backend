# Assessment Builder - Project Context

## Wat is dit?
MentesMe Assessment Builder: een full-stack web app voor het bouwen van competentie-assessments en learning journeys. Tweetalig (NL/EN). Wordt opgeslagen in de Metro MySQL database en gebruikt voor 360-graden feedback, zelfevaluaties en coaching.

## Technische Documentatie
De volledige technische documentatie staat in `docs/`. Raadpleeg deze EERST bij vragen over architectuur, API's, datamodel of deployment:

| Document | Beschrijving |
|----------|-------------|
| [docs/01-architecture-overview.md](docs/01-architecture-overview.md) | Systeemarchitectuur, componenten, request flows, package structuur |
| [docs/02-data-model.md](docs/02-data-model.md) | Database schema (Metro + Builder tabellen), ER diagrammen, trigger optimalisatie |
| [docs/03-api-reference.md](docs/03-api-reference.md) | Alle ~35 REST endpoints met request/response voorbeelden |
| [docs/04-authentication.md](docs/04-authentication.md) | Login flow, JWT tokens, rollen, access flags, gebruikersbeheer |
| [docs/05-assessment-builder.md](docs/05-assessment-builder.md) | 4-stappen wizard, competentie-matrix, publish flow, XML generatie |
| [docs/06-learning-journey-builder.md](docs/06-learning-journey-builder.md) | Step editor, preview panel specs, publish flow, validatie |
| [docs/07-deployment.md](docs/07-deployment.md) | AWS infra, EB environments, deploy scripts, WAR packaging |
| [docs/08-configuration.md](docs/08-configuration.md) | Alle environment variables, Spring config, lokaal ontwikkelen |

## Stack
- **Frontend**: React 18 + Vite + TypeScript (port 5173)
- **Backend**: Spring Boot 3.3.5 + Java 17 + JPA (port 8080)
- **Database**: Metro MySQL (AWS RDS) - read + write
- **Packaging**: WAR (ROOT.war) voor Tomcat op Elastic Beanstalk
- **Taal in code**: Nederlands primair, Engels als vertaling

## Twee Builders, Eén Backend
Het platform heeft twee builders die dezelfde backend delen:
1. **Assessment Builder** — competentie-assessments met 5-punts Likert schaal
2. **Learning Journey Builder** — interactieve leerpaden met stappen en content blocks

Na login kiest de gebruiker een van 4 omgevingen via de EnvironmentSelector:
- assessment-test, assessment-prod, journeys-test, journeys-prod

## Projectstructuur
```
frontend/src/
  main.tsx                    # Entry point: auth → omgeving kiezen → builder
  App.tsx                     # Assessment Builder (~3000 regels)
  Login.tsx, auth.ts, api.ts  # Auth + API integratie
  EnvironmentSelector.tsx     # Omgevingskeuze (4 kaarten)
  learning-journey/           # Learning Journey Builder (aparte componenten)
    LearningJourneyBuilderPage.tsx, StepEditor.tsx, preview/

backend/src/main/java/com/mentesme/builder/
  api/          # 11 REST controllers
  service/      # 16 business logic services
  entity/       # JPA entities (BuilderUser, Item, etc.)
  model/        # ~48 Request/Response DTOs
  config/       # Auth, datasource, S3, web config
```

## Deployment
```bash
./deploy-assessment.sh test   # Assessment Builder → Metro-builder-env
./deploy-assessment.sh prod   # Assessment Builder → metro-builder-prod
./deploy-journeys.sh test     # Learning Journeys → journeys-test
./deploy-journeys.sh prod     # Learning Journeys → journeys-prod
```

AWS account: 643502197318, region: eu-west-1
Deploy bucket: elasticbeanstalk-eu-west-1-643502197318
Productie domein: builder.mentes.me

## Authenticatie
- Custom JWT (HMAC-SHA256), 24 uur geldig
- Gebruikers in `builder_users` tabel met BCrypt wachtwoorden
- Rollen: ADMIN (alles), BUILDER (bouwen + publiceren)
- 4 access flags per user: assessmentTest/Prod, journeysTest/Prod
- Admin seeding: `support@mentes.me` met wachtwoord uit `BUILDER_AUTH_PASSWORD`
- Zie [docs/04-authentication.md](docs/04-authentication.md) voor volledige flow

## Bekende Technische Schuld
- App.tsx ~3000 regels — moet opgesplitst in componenten
- SQL injection risico in MetroIntegrationService (string concatenation)
- i18n onvolledig: alleen NL UI-strings, geen EN vertaalbestand
- Geen frontend tests (backend heeft 25 tests)

## Visie / Roadmap
Platform groeit van builder naar **ontwikkelplatform**:
1. Assessment bouwen (huidige builder) ✅
2. Learning Journey bouwen ✅
3. Assessment afnemen (bestaand in Metro)
4. **Interactief rapport** (i.p.v. statische XML/PDF)
5. **AI-begeleide coaching** via iGROW of STARRT
6. Actieplannen + follow-up
7. Hermeting en voortgang

### Coaching methodieken
- **iGROW**: Insight → Goal → Reality → Options → Will
- **STARRT**: Situatie → Taak → Actie → Resultaat → Reflectie → Transfer
- Partners kunnen eigen methodieken hanteren

### SCS (Social Communication Styles)
Bestaande XML-gebaseerde rapporten die geleidelijk gemigreerd worden naar interactieve structuur.

## Mockups
- `mockup-learning-journey-builder.html` — Goedgekeurde LJ builder mockup
- `.claude/mockup-restyling.html` — Assessment Builder redesign mockup
- `.claude/analyse-restyling-frontend.md` — Redesign analyse + implementatieplan
- `mockup-inline-en.html` — Inline Engelse mockup

## Conventies
- Taal in UI: Nederlands primair
- Alle teksten tweetalig opslaan (NL + EN)
- Metro database is de bron van waarheid
- Raw SQL voor Metro writes, JPA alleen voor builder_users
- Trigger bypass bij bulk inserts (20× performance)

## Commando's
```bash
# Frontend development
cd frontend && npm run dev

# Backend development
cd backend && mvn spring-boot:run

# Frontend build (output naar backend/src/main/webapp/)
cd frontend && npm run build

# Backend build (WAR)
cd backend && mvn clean package

# Tests
cd backend && mvn test
```
