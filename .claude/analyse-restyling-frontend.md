# Analyse & Mockup: Restyling Frontend Assessment Builder

## 1. HUIDIGE SITUATIE

### 1.1 Architectuur
- **App.tsx**: 1807 regels monoliet - 30+ useState calls, alle logica in 1 component
- **styles.css**: 1270 regels, goed georganiseerd maar veel legacy
- **Geen component-decomposition**: alles in App, inclusief autocomplete, wizard, modals
- **Geen UI framework**: raw React + custom CSS

### 1.2 Huidige UX Flow
```
Login -> Wizard Step 1 (Assessment + Competenties) -> Wizard Step 2 (Vragen) -> Publish
```

### 1.3 Pijnpunten Huidige UI

| Probleem | Impact | Waar |
|----------|--------|------|
| NL/EN velden naast elkaar | Visuele ruis, moeilijk focussen | Overal |
| Veel scrollen in Stap 1 | Assessment info + alle categorien/competenties op 1 pagina | Stap 1 |
| Autocomplete 3x gedupliceerd | Inconsistente UX, code duplicatie | Category, Competence, Group |
| Geen visueel overzicht | Geen samenvatting/preview van assessment structuur | Hele app |
| Project menu als dropdown | Niet schaalbaar, onhandig | Header |
| Wizard slechts 2 stappen | Stap 1 is overbelast | Wizard |
| Geen mobile layout | Grid breekt op smalle schermen | Overal |
| Geen live preview | Gebruiker ziet niet hoe eindresultaat eruit ziet | Stap 2 |

---

## 2. VOORSTEL: NIEUWE STRUCTUUR

### 2.1 Wizard uitbreiden naar 4 stappen

```
Stap 1: Assessment Info    (naam, beschrijving, instructie, groepen)
Stap 2: Competenties       (categorien + competenties matrix)
Stap 3: Vragen             (niet-vraag / wel-vraag per competentie)
Stap 4: Review & Publish   (overzicht, preview, publish knoppen)
```

**Waarom?** Stap 1 is nu overbelast. Door assessment-info en competentie-matrix te scheiden wordt elke stap behapbaar.

### 2.2 NL/EN Toggle i.p.v. Side-by-Side

In plaats van twee kolommen (NL | EN) voor elk veld:
- **Primaire taal** (NL) altijd zichtbaar
- **EN tab/toggle** om vertalingen te zien/bewerken
- Auto-vertaling indicator op NL veld ("EN: vertaald")

### 2.3 Component Decompositie

```
src/
  components/
    WizardNav.tsx           # Stap-navigatie (herbruikbaar)
    AssessmentForm.tsx      # Stap 1: assessment info
    CompetenceMatrix.tsx    # Stap 2: categorien + competenties
    QuestionEditor.tsx      # Stap 3: vragen per competentie
    ReviewPublish.tsx       # Stap 4: overzicht + publish
    AutocompleteField.tsx   # Generieke autocomplete (1x)
    CategoryBox.tsx         # Category card met competenties
    CompetenceItem.tsx      # Individuele competentie row
    QuestionCard.tsx        # Vraag-card (niet/wel)
    ProjectSidebar.tsx      # Project beheer (sidebar i.p.v. dropdown)
    LanguageToggle.tsx      # NL/EN switch
    Modal.tsx               # Generieke modal
  hooks/
    useDebounce.ts          # Bestaand
    useAutoComplete.ts      # Generieke autocomplete logica
    useProjects.ts          # Project CRUD logica
    useTranslation.ts       # Vertaal logica
  App.tsx                   # Alleen routing/state orchestratie (~200 regels)
```

---

## 3. MOCKUPS

### 3.1 Header & Wizard Nav (nieuw)

```
+------------------------------------------------------------------------+
|  Assessment Builder                    [Projecten] [Opslaan] [Uitloggen]|
|  MentesMe                                                              |
+------------------------------------------------------------------------+
|                                                                        |
|  (1) Assessment  -->  (2) Competenties  -->  (3) Vragen  -->  (4) Review|
|      [====]              [ ]                   [ ]              [ ]    |
|                                                                        |
+------------------------------------------------------------------------+
```

**Details:**
- Wizard nav met progress bar tussen stappen
- Stappen zijn klikbaar als vorige stap valide is
- Huidige stap blauw, voltooide stappen groen met vinkje
- Compactere header, project-sidebar wordt apart paneel

### 3.2 Stap 1: Assessment Info (gesplitst)

```
+------------------------------------------------------------------------+
|  Stap 1: Assessment Informatie                          [NL] [EN]      |
+------------------------------------------------------------------------+
|                                                                        |
|  Naam *                                                                |
|  +------------------------------------------------------------------+  |
|  | Leiderschap 2026                                                 |  |
|  +------------------------------------------------------------------+  |
|  [!] Assessment met deze naam bestaat al (ID: 42)                      |
|                                                                        |
|  Beschrijving (rapport introductie)                                    |
|  +------------------------------------------------------------------+  |
|  | Dit assessment is bedoeld om inzicht te krijgen in...            |  |
|  |                                                                  |  |
|  +------------------------------------------------------------------+  |
|                                                                        |
|  Instructie (bovenaan vragenlijst)                                     |
|  +------------------------------------------------------------------+  |
|  | Deze vragenlijst is bedoeld om uw competenties te bepalen...     |  |
|  +------------------------------------------------------------------+  |
|                                                                        |
|  Groep(en) *                                                           |
|  +------------------------------------------------------------------+  |
|  | Zoek een groep...                                                |  |
|  +------------------------------------------------------------------+  |
|  [x MentesMe Intern (ID: 5)] [x School ABC (ID: 12)]                  |
|                                                                        |
|                                         [Volgende: Competenties -->]   |
+------------------------------------------------------------------------+
```

**Verbeteringen:**
- Full-width velden (geen NL|EN grid meer)
- NL/EN toggle rechtsboven: wissel de hele view
- Cleaner layout, meer ademruimte
- Duidelijke verplichte veld-indicatoren

### 3.3 Stap 2: Competenties Matrix

```
+------------------------------------------------------------------------+
|  Stap 2: Competenties                                   [NL] [EN]     |
+------------------------------------------------------------------------+
|                                                                        |
|  +------------------------------------------------------------------+  |
|  | LEIDERSCHAP                         [Bestaand] [Bewerk] [x]     |  |
|  |------------------------------------------------------------------|  |
|  | Beschrijving: Competenties rond leidinggeven en...               |  |
|  |------------------------------------------------------------------|  |
|  |                                                                  |  |
|  |  +------------------------------------------------------------+  |  |
|  |  | Besluitvaardigheid          [Bestaand ID: 23]    [Verwijder]|  |  |
|  |  | Beschrijving: Neemt tijdig en onderbouwd...                 |  |  |
|  |  +------------------------------------------------------------+  |  |
|  |                                                                  |  |
|  |  +------------------------------------------------------------+  |  |
|  |  | Delegeren                   [+ Nieuw]            [Verwijder]|  |  |
|  |  | Beschrijving: Taken en verantwoordelijkheden...             |  |  |
|  |  +------------------------------------------------------------+  |  |
|  |                                                                  |  |
|  |  [+ Competentie toevoegen aan Leiderschap]                       |  |
|  +------------------------------------------------------------------+  |
|                                                                        |
|  +------------------------------------------------------------------+  |
|  | SAMENWERKING                        [Nieuw] [Bewerk] [x]        |  |
|  |------------------------------------------------------------------|  |
|  | ...                                                              |  |
|  +------------------------------------------------------------------+  |
|                                                                        |
|  [+ Nieuwe categorie toevoegen]                                        |
|                                                                        |
|  Samenvatting: 2 categorien, 5 competenties (3 bestaand, 2 nieuw)     |
|                                                                        |
|               [<-- Terug]                    [Volgende: Vragen -->]    |
+------------------------------------------------------------------------+
```

**Verbeteringen:**
- Competenties in compacte, duidelijke kaarten
- Per-veld status badges (Bestaand/Nieuw)
- Samenvatting onderaan voor overzicht
- Category beschrijving zichtbaar maar inklapbaar

### 3.4 Stap 3: Vragen Editor

```
+------------------------------------------------------------------------+
|  Stap 3: Vragen                                         [NL] [EN]     |
|  Vul voor elke competentie een Niet-vraag en Wel-vraag in              |
+------------------------------------------------------------------------+
|                                                                        |
|  LEIDERSCHAP (2 van 2 ingevuld)                                        |
|  ================================================================      |
|                                                                        |
|  Besluitvaardigheid                                  [Volledig]        |
|  +------------------------------------------------------------------+  |
|  |  X  Niet-vraag                                                   |  |
|  |  +--------------------------------------------------------------+|  |
|  |  | Neemt geen beslissingen wanneer dat nodig is                  ||  |
|  |  +--------------------------------------------------------------+|  |
|  |                                                                  |  |
|  |  V  Wel-vraag                                                    |  |
|  |  +--------------------------------------------------------------+|  |
|  |  | Neemt tijdig en onderbouwd beslissingen                       ||  |
|  |  +--------------------------------------------------------------+|  |
|  +------------------------------------------------------------------+  |
|                                                                        |
|  Delegeren                                           [Nog invullen]   |
|  +------------------------------------------------------------------+  |
|  |  X  Niet-vraag                                                   |  |
|  |  +--------------------------------------------------------------+|  |
|  |  |                                                              ||  |
|  |  +--------------------------------------------------------------+|  |
|  |                                                                  |  |
|  |  V  Wel-vraag                                                    |  |
|  |  +--------------------------------------------------------------+|  |
|  |  |                                                              ||  |
|  |  +--------------------------------------------------------------+|  |
|  +------------------------------------------------------------------+  |
|                                                                        |
|               [<-- Terug]                    [Volgende: Review -->]    |
+------------------------------------------------------------------------+
```

**Verbeteringen:**
- Full-width vraagvelden (geen NL|EN naast elkaar)
- EN versie via toggle, niet altijd zichtbaar
- Duidelijke X/V iconen voor niet/wel
- Progress per categorie ("2 van 3 ingevuld")
- Minder visuele ruis door 1 taal tegelijk

### 3.5 Stap 4: Review & Publish (NIEUW)

```
+------------------------------------------------------------------------+
|  Stap 4: Review & Publiceren                                           |
+------------------------------------------------------------------------+
|                                                                        |
|  ASSESSMENT OVERZICHT                                                  |
|  +------------------------------------------------------------------+  |
|  | Naam:        Leiderschap 2026                                    |  |
|  | Groepen:     MentesMe Intern, School ABC                        |  |
|  | Categorien:  2  |  Competenties: 5  |  Vragen: 10               |  |
|  | Vertalingen: Alle velden vertaald (NL + EN)                     |  |
|  +------------------------------------------------------------------+  |
|                                                                        |
|  STRUCTUUR                                                             |
|  +------------------------------------------------------------------+  |
|  | Leiderschap                                                      |  |
|  |   1.1 Besluitvaardigheid  [V] NL [V] EN                        |  |
|  |   1.2 Delegeren           [V] NL [V] EN                        |  |
|  | Samenwerking                                                     |  |
|  |   2.1 Teamwork            [V] NL [V] EN                        |  |
|  |   2.2 Communicatie        [V] NL [!] EN ontbreekt               |  |
|  |   2.3 Conflicthantering   [V] NL [V] EN                        |  |
|  +------------------------------------------------------------------+  |
|                                                                        |
|  +------------------------------------------------------------------+  |
|  |  [XML Preview]                                                   |  |
|  |  Bekijk de gegenereerde XML voor vragenlijst en rapport          |  |
|  +------------------------------------------------------------------+  |
|                                                                        |
|  +------------------------------------------------------------------+  |
|  |  [Publish to TEST]                  [Publish to PRODUCTION]      |  |
|  |                                     (alleen na TEST publicatie)  |  |
|  +------------------------------------------------------------------+  |
|                                                                        |
|               [<-- Terug naar Vragen]                                  |
+------------------------------------------------------------------------+
```

**Verbeteringen:**
- Compleet overzicht voor je publiceert
- Checklist: welke vertalingen ontbreken
- Structuur-boom met ID-nummering (1.1, 1.2, etc.)
- Duidelijke scheiding TEST vs PRODUCTION
- XML Preview knop prominent

---

## 4. DESIGN PRINCIPES

### 4.1 Kleurenpalet (behouden/verfijnen)
```
Primair:     #2563eb (blauw)      - knoppen, actieve stap
Succes:      #16a34a (groen)      - bestaande items, voltooid
Waarschuwing:#d97706 (amber)      - nieuwe items, onvolledig
Gevaar:      #dc2626 (rood)       - verwijder, production
Achtergrond: #f5f6fa (licht grijs)- app achtergrond
Paneel:      #ffffff (wit)        - content cards
Tekst:       #1f2933 (donker)     - primaire tekst
Subtekst:    #64748b (grijs)      - labels, hints
```

### 4.2 Typografie (behouden)
- Inter font family (al in gebruik)
- 28px titel, 20px h2, 15px body, 13px labels
- Font-weight 600/700 voor headers

### 4.3 Spacing & Layout
- Max-width 1100px (behouden)
- Panel border-radius 16px (behouden)
- Meer verticale ruimte tussen secties
- Full-width velden i.p.v. 2-kolom grid voor content

### 4.4 Responsive Breakpoints
```
Desktop:  > 768px   - huidige layout
Tablet:   768px     - single column, compactere cards
Mobiel:   < 480px   - stacked layout, hamburger menu
```

---

## 5. IMPLEMENTATIE-AANPAK

### Fase 1: Component Decompositie (geen visuele wijzigingen)
- Split App.tsx in losse componenten
- Extract hooks (useAutoComplete, useProjects, useTranslation)
- Extract generieke AutocompleteField
- Behoud exacte huidige CSS/UX

### Fase 2: Wizard Uitbreiden naar 4 Stappen
- Assessment Info naar eigen stap
- Nieuwe Review & Publish stap
- Update wizard nav component
- Validatie per stap

### Fase 3: NL/EN Toggle
- LanguageToggle component
- Refactor alle NL|EN grid naar single-column + toggle
- Vertaal-indicator op primaire veld
- Context voor active taal

### Fase 4: Visuele Verfijning
- Verbeterde card layouts
- Progress indicators per categorie
- Betere autocomplete UX
- Responsive breakpoints
- Animaties/transities

### Fase 5: Project Sidebar (optioneel)
- Sidebar i.p.v. dropdown
- Recent projects
- Auto-save indicator

---

## 6. SCOPE AFBAKENING

### In scope (deze restyling)
- Component decompositie van App.tsx
- 4-stappen wizard
- NL/EN toggle
- Verbeterde layout en spacing
- Review stap met overzicht
- Responsive basis

### Buiten scope (later)
- XML questionnaire rendering/styling (Metro platform)
- Dark mode
- Drag-and-drop reordering van competenties
- AI-assisted vraag-suggesties
- Bulk import van competenties
- Rapport preview (PDF-achtig)

---

## 7. RISICO'S

| Risico | Mitigatie |
|--------|----------|
| Regressie bij component split | Fase 1 is puur refactor, geen UX wijziging |
| State management complexiteit | Context API voor gedeelde state, of prop drilling |
| Verlies van huidige functionaliteit | Per fase testen op test.mentes.me |
| Build/deploy issues | Frontend build apart testen voor WAR integratie |
