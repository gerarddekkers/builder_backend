# MentesMe Assessment Builder Platform — Technische Documentatie

Het MentesMe Assessment Builder platform is een full-stack webapplicatie voor het bouwen en publiceren van competentie-assessments en learning journeys. Het platform schrijft naar de Metro database (MySQL op AWS RDS), die ook de basis vormt voor het afnemen van assessments en het genereren van rapporten. Alle content wordt tweetalig opgeslagen (NL + EN).

## Tech Stack

| Laag | Technologie |
|------|-------------|
| Frontend | React 18 + TypeScript + Vite |
| Backend | Spring Boot 3.3.5 + Java 17 |
| Database | MySQL (Metro) op AWS RDS |
| Opslag | AWS S3 (XML bestanden, documenten, afbeeldingen) |
| Hosting | AWS Elastic Beanstalk (4 omgevingen) |
| Vertaling | Google Cloud Translation API (fallback: AI) |
| Auth | Custom JWT (HMAC-SHA256) + BCrypt wachtwoorden |

## Documentatie

| # | Document | Beschrijving |
|---|----------|-------------|
| 1 | [Architectuuroverzicht](01-architecture-overview.md) | Systeemarchitectuur, componenten, request flow |
| 2 | [Datamodel](02-data-model.md) | Database schema, entity relaties, triggers |
| 3 | [API Reference](03-api-reference.md) | Alle REST endpoints met request/response |
| 4 | [Authenticatie & Autorisatie](04-authentication.md) | Login flow, JWT tokens, rollen, access flags |
| 5 | [Assessment Builder](05-assessment-builder.md) | Wizard flow, competentie-matrix, publish naar Metro |
| 6 | [Learning Journey Builder](06-learning-journey-builder.md) | Step editor, preview panel, publish flow |
| 7 | [Deployment](07-deployment.md) | AWS infrastructuur, deploy scripts, omgevingen |
| 8 | [Configuratie](08-configuration.md) | Environment variables, Spring config, lokaal ontwikkelen |

## Quick Start

### Lokaal ontwikkelen

```bash
# Backend starten (port 8080)
cd backend
export DB_HOST=test-metro-db.cyi4arp1bouk.eu-west-1.rds.amazonaws.com
export DB_PASSWORD="<metro-wachtwoord>"
export BUILDER_AUTH_PASSWORD="<admin-wachtwoord>"
mvn spring-boot:run

# Frontend starten (port 5173, proxy naar backend)
cd frontend
npm install
npm run dev
```

### Deployen

```bash
# Assessment Builder naar test
./deploy-assessment.sh test

# Assessment Builder naar productie
./deploy-assessment.sh prod

# Learning Journey Builder naar test
./deploy-journeys.sh test
```

## Projectstructuur

```
Assessmentbuilder/
├── backend/
│   ├── src/main/java/com/mentesme/builder/
│   │   ├── api/           # REST controllers (11 bestanden)
│   │   ├── service/       # Business logic (16 bestanden)
│   │   ├── entity/        # JPA entities (8 bestanden)
│   │   ├── repository/    # Spring Data repositories (5 bestanden)
│   │   ├── model/         # Request/Response DTOs (~48 bestanden)
│   │   └── config/        # Auth, datasource, S3, web config (7 bestanden)
│   ├── src/main/resources/
│   │   └── application.yml
│   ├── .platform/nginx/   # EB Nginx configuratie
│   └── pom.xml            # Maven build (WAR packaging)
├── frontend/              # Git submodule (apart repository)
│   ├── src/
│   │   ├── App.tsx                 # Assessment Builder (~3000 regels)
│   │   ├── learning-journey/       # Learning Journey Builder
│   │   ├── auth.ts, api.ts         # Auth + API integratie
│   │   ├── Login.tsx               # Login pagina
│   │   ├── EnvironmentSelector.tsx # Omgeving kiezen
│   │   └── i18n/                   # Vertalingen (NL)
│   └── vite.config.ts
├── docs/                  # Deze documentatie
├── deploy-assessment.sh   # Deploy script Assessment Builder
├── deploy-journeys.sh     # Deploy script Learning Journey Builder
└── CLAUDE.md              # AI-assistentie context
```
