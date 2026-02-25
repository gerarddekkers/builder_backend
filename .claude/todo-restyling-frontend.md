# TODO: Restyling Frontend Vragenlijst (XML)

## Context
De Assessment Builder genereert XML vragenlijsten die door het Metro platform worden gebruikt voor 360-graden feedback assessments. De huidige look and feel van de XML vragenlijst in Metro is basic en moet mooier ontworpen worden.

## Wat bekijken
1. **Huidige XML output**: `XmlGenerationService.java` genereert questionnaire.xml en report.xml
2. **Metro platform rendering**: Hoe Metro de XML rendert (test via https://test.mentes.me)
3. **Bestaande voorbeelden**: S3 bucket `metro-platform` bevat werkende XML files onder `test/nl/` en `test/en/`
4. **Builder frontend**: De wizard in `frontend/src/App.tsx` (~1700 regels, moet sowieso opgesplitst)

## Mogelijke scope
- Styling/CSS binnen de XML questionnaire templates
- Layout van vragen (links/rechts polariteiten, 5-puntsschaal)
- Rapport XML styling (competentie scores, grafieken, feedback)
- Responsive design voor mobiel gebruik
- Consistente branding (MentesMe huisstijl)

## Relevante files
- `backend/src/main/java/com/mentesme/builder/service/XmlGenerationService.java`
- `frontend/src/App.tsx` - de builder wizard zelf
- `frontend/src/styles.css` - huidige styling
- S3: `s3://metro-platform/test/nl/` en `s3://metro-platform/test/en/` voor referentie-XMLs

## Recente wijzigingen (session 2026-02-17)
- Multi-group selectie toegevoegd (groupIds i.p.v. single groupId)
- competence_questions tabel wordt nu gevuld door builder
- S3 URL format gefixt (zonder region)
- Deployed als version 26-9 op Elastic Beanstalk
- DB is schoon (alle test questionnaires opgeruimd)
