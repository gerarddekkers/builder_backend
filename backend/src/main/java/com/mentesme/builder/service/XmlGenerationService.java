package com.mentesme.builder.service;

import com.mentesme.builder.model.AssessmentBuildRequest;
import com.mentesme.builder.model.CompetenceInput;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;

@Service
public class XmlGenerationService {

    public String generateQuestionnaireXml(AssessmentBuildRequest request, String language, List<String> warnings) {
        String title = select(language, request.assessmentName(), request.assessmentNameEn());
        String instruction = select(language, request.assessmentInstruction(), request.assessmentInstructionEn());

        LinkedHashMap<String, CategoryBucket> categories = buildCategoryBuckets(request.competences());

        StringBuilder sb = new StringBuilder();
        sb.append("<?xml version=\"1.0\" encoding=\"utf-8\"?>\n");
        sb.append("<questionnaire");
        sb.append(attribute("title", title));
        sb.append("\n\tinstruction=\"\"");
        sb.append("\n\tvaluators=\"7\"");
        sb.append("\n\tdescription=\"\"");
        sb.append(">\n");

        boolean firstSection = true;
        int categoryIndex = 1;
        for (CategoryBucket bucket : categories.values()) {
            String sectionTitle = bucket.name;
            // Each section gets its category description as instruction.
            // Fallback: first section gets the assessment instruction if no category description.
            String categoryDesc = select(language, bucket.descriptionNl, bucket.descriptionEn);
            String sectionInstruction;
            if (!categoryDesc.isBlank()) {
                sectionInstruction = categoryDesc;
            } else if (firstSection) {
                sectionInstruction = instruction;
            } else {
                sectionInstruction = "";
            }
            firstSection = false;
            sb.append("\t<section");
            sb.append(attribute("title", sectionTitle));
            sb.append(attribute("instruction", sectionInstruction));
            sb.append(">\n");

            int competenceIndex = 1;
            for (CompetenceInput competence : bucket.competences) {
                String questionId = questionId(categoryIndex, competenceIndex);
                String left = select(language, competence.questionLeft(), competence.questionLeftEn());
                String right = select(language, competence.questionRight(), competence.questionRightEn());

                if (left.isBlank() || right.isBlank()) {
                    warnings.add("Vraagtekst ontbreekt voor competence: " + competence.name());
                }

                sb.append("\t\t<rangeQuestion");
                sb.append(attribute("id", questionId));
                sb.append(attribute("left", left));
                sb.append(attribute("right", right));
                sb.append(" />\n");
                competenceIndex++;
            }

            sb.append("\t</section>\n");
            categoryIndex++;
        }

        sb.append("</questionnaire>");
        return sb.toString();
    }

    public String generateReportXml(AssessmentBuildRequest request, String language, List<String> warnings) {
        String assessmentName = select(language, request.assessmentName(), request.assessmentNameEn());
        String introText = select(language, request.assessmentDescription(), request.assessmentDescriptionEn());

        LinkedHashMap<String, CategoryBucket> categories = buildCategoryBuckets(request.competences());
        LinkedHashMap<String, ReportSection> reportSections = buildReportSections(request.competences(), categories, language);

        StringBuilder sb = new StringBuilder();
        sb.append("<?xml version=\"1.0\" encoding=\"utf-8\"?>\n");
        sb.append("<report");
        sb.append(attribute("title", assessmentName));
        sb.append(">\n");

        sb.append("\t<section").append(attribute("title", title(language, "Inleiding", "Introduction"))).append(">\n");
        if (!introText.isBlank()) {
            sb.append("\t\t<p>").append(escapeText(introText)).append("</p>\n");
        }
        sb.append("\t</section>\n");

        // Build category-level entries for overview graphs (e.g., "1.|2.|3." with category names)
        List<QuestionEntry> categoryEntries = buildCategoryEntries(categories, language);
        String categoryQuestions = joinQuestions(categoryEntries);
        String categoryLabels = joinLabels(categoryEntries);

        sb.append("\t<section").append(attribute("title", title(language, "Overzicht van de scores", "Score overview"))).append(">\n");
        sb.append("\t\t<graph").append(attribute("type", "bar"))
                .append(attribute("questions", categoryQuestions))
                .append(attribute("labels", categoryLabels)).append(" />\n");
        sb.append("\t\t<graph").append(attribute("type", "spider"))
                .append(attribute("title", title(language, "Alle gebieden op een rijtje", "All areas at a glance")))
                .append(attribute("questions", categoryQuestions))
                .append(attribute("min", "6"))
                .append(attribute("max", "8"))
                .append(attribute("labels", categoryLabels)).append(" />\n");
        sb.append("\t\t<graph").append(attribute("type", "bar"))
                .append(attribute("title", title(language, "Mijn score versus wat anderen vinden", "My score versus others")))
                .append(attribute("questions", categoryQuestions))
                .append(attribute("labels", categoryLabels))
                .append(attribute("groupBy", "0"))
                .append(attribute("groups", "1|2|3|4"))
                .append(attribute("groupLabels", groupLabels(language))).append(" />\n");
        sb.append("\t\t<graph").append(attribute("type", "table"))
                .append(attribute("title", title(language, "Mijn score versus wat anderen vinden", "My score versus others")))
                .append(attribute("questions", categoryQuestions))
                .append(attribute("labels", categoryLabels))
                .append(attribute("groupBy", "0"))
                .append(attribute("groups", "1|2|3|4"))
                .append(attribute("groupLabels", groupLabels(language))).append(" />\n");
        sb.append("\t</section>\n");

        for (ReportSection section : reportSections.values()) {
            sb.append("\t<section").append(attribute("title", section.title.toUpperCase(Locale.ROOT))).append(">\n");
            if (!section.description.isBlank()) {
                sb.append("\t\t<p>").append(escapeText(section.description)).append("</p>\n");
            }
            if (!section.questions.isEmpty()) {
                sb.append("\t\t<list>\n");
                for (QuestionEntry entry : section.questions) {
                    String displayId = entry.id.endsWith(".") ? entry.id.substring(0, entry.id.length() - 1) : entry.id;
                    sb.append("\t\t\t<p>")
                            .append(escapeText(displayId + " " + entry.label))
                            .append("</p>\n");
                }
                sb.append("\t\t</list>\n");

                String sectionQuestions = joinQuestions(section.questions);
                sb.append("\t\t<graph").append(attribute("type", "bar"))
                        .append(attribute("title", title(language, "Gemiddelde score per vraag", "Average score per question")))
                        .append(attribute("questions", sectionQuestions))
                        .append(" />\n");
                sb.append("\t\t<graph").append(attribute("type", "bar"))
                        .append(attribute("title", title(language, "Gemiddelde score per vraag per respondentengroep", "Average score per group")))
                        .append(attribute("questions", sectionQuestions))
                        .append(attribute("groupBy", "0"))
                        .append(attribute("groups", "1|2|3|4"))
                        .append(attribute("groupLabels", groupLabels(language)))
                        .append(" />\n");
            }
            sb.append("\t</section>\n");
        }

        sb.append("</report>");
        return sb.toString();
    }

    private LinkedHashMap<String, CategoryBucket> buildCategoryBuckets(List<CompetenceInput> competences) {
        LinkedHashMap<String, CategoryBucket> categories = new LinkedHashMap<>();
        for (CompetenceInput competence : competences) {
            String category = safe(competence.category());
            CategoryBucket bucket = categories.computeIfAbsent(category.toLowerCase(Locale.ROOT), key -> new CategoryBucket(category));
            if (bucket.descriptionNl.isBlank() && competence.categoryDescription() != null) {
                bucket.descriptionNl = competence.categoryDescription();
            }
            if (bucket.descriptionEn.isBlank() && competence.categoryDescriptionEn() != null) {
                bucket.descriptionEn = competence.categoryDescriptionEn();
            }
            bucket.competences.add(competence);
        }
        return categories;
    }

    private List<QuestionEntry> buildCategoryEntries(LinkedHashMap<String, CategoryBucket> categories, String language) {
        List<QuestionEntry> entries = new ArrayList<>();
        int categoryIndex = 1;
        for (CategoryBucket bucket : categories.values()) {
            String id = categoryIndex + ".";
            String label = bucket.name;
            entries.add(new QuestionEntry(id, label));
            categoryIndex++;
        }
        return entries;
    }

    private List<QuestionEntry> buildQuestionEntries(LinkedHashMap<String, CategoryBucket> categories, String language) {
        List<QuestionEntry> entries = new ArrayList<>();
        int categoryIndex = 1;
        for (CategoryBucket bucket : categories.values()) {
            int competenceIndex = 1;
            for (CompetenceInput competence : bucket.competences) {
                String id = questionId(categoryIndex, competenceIndex);
                String label = select(language, competence.name(), competence.nameEn());
                entries.add(new QuestionEntry(id, label));
                competenceIndex++;
            }
            categoryIndex++;
        }
        return entries;
    }

    private LinkedHashMap<String, ReportSection> buildReportSections(List<CompetenceInput> competences,
                                                                     LinkedHashMap<String, CategoryBucket> categories,
                                                                     String language) {
        LinkedHashMap<String, ReportSection> sections = new LinkedHashMap<>();
        int categoryIndex = 1;
        for (CategoryBucket bucket : categories.values()) {
            int competenceIndex = 1;
            for (CompetenceInput competence : bucket.competences) {
                String id = questionId(categoryIndex, competenceIndex);
                String label = select(language, competence.questionRight(), competence.questionRightEn());

                String sectionKey = safe(competence.subcategory()).isBlank() ? bucket.name : competence.subcategory();
                ReportSection section = sections.computeIfAbsent(sectionKey.toLowerCase(Locale.ROOT),
                        key -> new ReportSection(sectionKey));

                if (section.description.isBlank()) {
                    String description = select(language,
                            safe(competence.subcategory()).isBlank() ? competence.categoryDescription() : competence.subcategoryDescription(),
                            safe(competence.subcategory()).isBlank() ? competence.categoryDescriptionEn() : competence.subcategoryDescriptionEn());
                    section.description = description;
                }

                section.questions.add(new QuestionEntry(id, label));
                competenceIndex++;
            }
            categoryIndex++;
        }
        return sections;
    }

    private String questionId(int categoryIndex, int competenceIndex) {
        return categoryIndex + "." + competenceIndex + ".";
    }

    private String joinQuestions(List<QuestionEntry> entries) {
        if (entries.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < entries.size(); i++) {
            if (i > 0) {
                sb.append("|");
            }
            sb.append(entries.get(i).id);
        }
        return sb.toString();
    }

    private String joinLabels(List<QuestionEntry> entries) {
        if (entries.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < entries.size(); i++) {
            if (i > 0) {
                sb.append("|");
            }
            sb.append(escapeAttribute(entries.get(i).label));
        }
        return sb.toString();
    }

    private String select(String language, String nlValue, String enValue) {
        String value = "en".equalsIgnoreCase(language) ? safe(enValue) : safe(nlValue);
        if (value.isBlank()) {
            value = safe(nlValue);
        }
        return value;
    }

    private String title(String language, String nl, String en) {
        return "en".equalsIgnoreCase(language) ? en : nl;
    }

    private String groupLabels(String language) {
        if ("en".equalsIgnoreCase(language)) {
            return "Self|Colleagues|Parents|Managers";
        }
        return "Zelf|Collega's|Ouders|Leiding";
    }

    private String attribute(String name, String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        return " " + name + "=\"" + escapeAttribute(value) + "\"";
    }

    private String escapeAttribute(String value) {
        return escapeText(value).replace("\"", "&quot;");
    }

    private String escapeText(String value) {
        if (value == null) {
            return "";
        }
        String normalized = value.replace("\r\n", " ").replace("\n", " ").replace("\r", " ");
        return normalized
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }

    private static class CategoryBucket {
        private final String name;
        private String descriptionNl = "";
        private String descriptionEn = "";
        private final List<CompetenceInput> competences = new ArrayList<>();

        private CategoryBucket(String name) {
            this.name = name;
        }
    }

    private static class QuestionEntry {
        private final String id;
        private final String label;

        private QuestionEntry(String id, String label) {
            this.id = id;
            this.label = label;
        }
    }

    private static class ReportSection {
        private final String title;
        private String description = "";
        private final List<QuestionEntry> questions = new ArrayList<>();

        private ReportSection(String title) {
            this.title = title;
        }
    }
}
