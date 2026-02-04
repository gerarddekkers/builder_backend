package com.mentesme.builder.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.util.Objects;

@Embeddable
public class QuestionnaireItemId implements Serializable {

    @Column(name = "questionnaire_id")
    private Long questionnaireId;

    @Column(name = "item_id")
    private Long itemId;

    public QuestionnaireItemId() {
    }

    public QuestionnaireItemId(Long questionnaireId, Long itemId) {
        this.questionnaireId = questionnaireId;
        this.itemId = itemId;
    }

    public Long getQuestionnaireId() {
        return questionnaireId;
    }

    public void setQuestionnaireId(Long questionnaireId) {
        this.questionnaireId = questionnaireId;
    }

    public Long getItemId() {
        return itemId;
    }

    public void setItemId(Long itemId) {
        this.itemId = itemId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        QuestionnaireItemId that = (QuestionnaireItemId) o;
        return Objects.equals(questionnaireId, that.questionnaireId) && Objects.equals(itemId, that.itemId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(questionnaireId, itemId);
    }
}
