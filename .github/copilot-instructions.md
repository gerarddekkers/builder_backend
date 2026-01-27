<!-- Use this file to provide workspace-specific custom instructions to Copilot. For more details, visit https://code.visualstudio.com/docs/copilot/copilot-customization#_use-a-githubcopilotinstructionsmd-file -->

	<!-- Ask for project type, language, and frameworks if not specified. Skip if already provided. -->

	<!--
	Ensure that the previous step has been marked as completed.
	Call project setup tool with projectType parameter.
	Run scaffolding command to create project files and folders.
	Use '.' as the working directory.
	If no appropriate projectType is available, search documentation using available tools.
	Otherwise, create the project structure manually using available file creation tools.
	-->

	<!--
	Verify that all previous steps have been completed successfully and you have marked the step as completed.
	Develop a plan to modify codebase according to user requirements.
	Apply modifications using appropriate tools and user-provided references.
	Skip this step for "Hello World" projects.
	-->

	<!-- ONLY install extensions provided mentioned in the get_project_setup_info. Skip this step otherwise and mark as completed. -->

	<!--
	Verify that all previous steps have been completed.
	Install any missing dependencies.
	Run diagnostics and resolve any issues.
	Check for markdown files in project folder for relevant instructions on how to do this.
	-->

	<!--
	Verify that all previous steps have been completed.
	Check https://code.visualstudio.com/docs/debugtest/tasks to determine if the project needs a task. If so, use the create_and_run_task to create and launch a task based on package.json, README.md, and project structure.
	Skip this step otherwise.
	 -->

	<!--
	Verify that all previous steps have been completed.
	Prompt user for debug mode, launch only if confirmed.
	 -->

	<!--
	Verify that all previous steps have been completed.
	Verify that README.md and the copilot-instructions.md file in the .github directory exists and contains current project information.
	Clean up the copilot-instructions.md file in the .github directory by removing all HTML comments.
	 -->

<!--
## Execution Guidelines
PROGRESS TRACKING:

COMMUNICATION RULES:

DEVELOPMENT RULES:

TASK COMPLETION RULES:
  - Project is successfully scaffolded and compiled without errors
  - copilot-instructions.md file in the .github directory exists in the project
  - README.md file exists and is up to date
  - User is provided with clear instructions to debug/launch the project

Before starting a new task in the above plan, update progress in the plan.

# Assessment Builder: AI Agent Coding Guidelines

## Architecture Overview
- **Monorepo**: React (Vite) frontend and Spring Boot backend (Java 17, Maven).
- **Frontend**: `frontend/` (TypeScript, React, Vite). Talks to backend via REST API.
- **Backend**: `backend/` (Spring Boot, Maven). Exposes `/api` endpoints, optionally integrates with Metro MySQL DB for lookups.
- **Metro Integration**: Controlled by `BUILDER_METRO_ENABLED` and related datasource env vars. See `backend/src/main/java/com/mentesme/builder/config/MetroDataSourceConfig.java`.

## Developer Workflows
- **Start all**: Use VS Code task `Dev: Backend + Frontend` (runs both servers in parallel).
- **Frontend**: 
  - `cd frontend && npm install && npm run dev` (default: http://localhost:5173)
  - Config: expects `VITE_API_BASE` (default: http://localhost:8080/api)
- **Backend**:
  - `cd backend && mvn spring-boot:run` (default: http://localhost:8080/api)
  - Or: `bash backend/scripts/start-backend.sh` (sets Metro env vars, port 8087)
  - Requires Java 17+, Node.js 18+
- **API endpoints**: See `README.md` for up-to-date list. Example: `/api/assessments/build`, `/api/xml/preview`, `/api/translate`.

## Key Patterns & Conventions
- **Type/Model Alignment**: TypeScript types in `frontend/src/types.ts` mirror Java records in `backend/src/main/java/com/mentesme/builder/model/`.
- **Metro DB**: If `BUILDER_METRO_ENABLED=true`, backend uses a separate MySQL connection for lookups (see `MetroDataSourceConfig.java`).
- **AI Translation**: `/api/translate` endpoint uses OpenAI API if `builder.ai.apiKey` is set in backend config.
- **XML/SQL Generation**: `/api/xml/preview` and `/api/integration/preview` endpoints generate XML/SQL for assessments, using logic in `XmlGenerationService.java` and `MetroIntegrationService.java`.
- **Frontend-Backend Contract**: All payloads and responses should be kept in sync between `frontend/src/types.ts` and backend model classes.
- **Port/Env Conventions**: Default ports: frontend 5173, backend 8080 (or 8087 if using script). Env vars for Metro and AI are required for full feature set.

## Integration Points
- **Metro DB**: Read-only lookup, not required for local dev. Controlled by env vars and `application.yml`.
- **OpenAI**: For AI translation, set `builder.ai.apiKey` in backend config or env.

## File References
- **Frontend entry**: `frontend/src/App.tsx`, `frontend/src/types.ts`
- **Backend entry**: `backend/src/main/java/com/mentesme/builder/AssessmentBuilderApplication.java`
- **API**: `backend/src/main/java/com/mentesme/builder/api/BuilderController.java`
- **Metro config**: `backend/src/main/java/com/mentesme/builder/config/MetroDataSourceConfig.java`
- **XML/SQL logic**: `backend/src/main/java/com/mentesme/builder/service/XmlGenerationService.java`, `MetroIntegrationService.java`

## Project-Specific Advice
- **When adding new API endpoints**, update both backend controller and frontend types.
- **When changing assessment/questionnaire structure**, update both XML/SQL generation logic and TypeScript types.
- **For Metro DB changes**, ensure all Metro-related beans are conditional on `builder.metro.enabled`.
- **Keep README.md up to date** with new endpoints, config, and workflow changes.
