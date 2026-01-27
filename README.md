# Assessment Builder

Nieuw project met React frontend en Spring Boot backend voor de eerste stap van de Assessment Builder.

## Frontend (React + Vite)

```bash
cd frontend
npm install
npm run dev
```

De app draait standaard op http://localhost:5173.

## Backend (Spring Boot)

```bash
cd backend
mvn spring-boot:run
```

De API draait op http://localhost:8080/api.

### API endpoints

- `GET /api/health`
- `GET /api/competences?query=`
- `POST /api/competences/lookup`
- `POST /api/integration/preview`
- `POST /api/assessments/build`

## Config

- Frontend verwacht `VITE_API_BASE` (default `http://localhost:8080/api`).
- Voor Metro read-only lookup:
	- `BUILDER_METRO_ENABLED=true`
	- `BUILDER_METRO_DATASOURCE_URL=jdbc:mysql://<host>:3306/metro?sslMode=REQUIRED`
	- `BUILDER_METRO_DATASOURCE_USERNAME=<user>`
	- `BUILDER_METRO_DATASOURCE_PASSWORD=<password>`

## Opmerking

Node.js (18+) en Java 17 zijn vereist om het project lokaal te draaien.
