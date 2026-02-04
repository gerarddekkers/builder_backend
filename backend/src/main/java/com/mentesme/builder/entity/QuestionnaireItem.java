package com.mentesme.builder.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "questionnaire_items")
public class QuestionnaireItem {

    @EmbeddedId
    private QuestionnaireItemId id;

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("itemId")
    @JoinColumn(name = "item_id")
    private Item item;

    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder;

    public QuestionnaireItem() {
    }

    public QuestionnaireItem(Long questionnaireId, Item item, Integer sortOrder) {
        this.id = new QuestionnaireItemId(questionnaireId, item.getId());
        this.item = item;
        this.sortOrder = sortOrder;
    }

    public QuestionnaireItemId getId() {
        return id;
    }

    public void setId(QuestionnaireItemId id) {
        this.id = id;
    }

    public Long getQuestionnaireId() {
        return id != null ? id.getQuestionnaireId() : null;
    }

    public Item getItem() {
        return item;
    }

    public void setItem(Item item) {
        this.item = item;
    }

    public Integer getSortOrder() {
        return sortOrder;
    }

    public void setSortOrder(Integer sortOrder) {
        this.sortOrder = sortOrder;
    }
}
