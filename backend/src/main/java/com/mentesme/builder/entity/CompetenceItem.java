package com.mentesme.builder.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "competence_items")
public class CompetenceItem {

    @EmbeddedId
    private CompetenceItemId id;

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("itemId")
    @JoinColumn(name = "item_id")
    private Item item;

    public CompetenceItem() {
    }

    public CompetenceItem(Long competenceId, Item item) {
        this.id = new CompetenceItemId(competenceId, item.getId());
        this.item = item;
    }

    public CompetenceItemId getId() {
        return id;
    }

    public void setId(CompetenceItemId id) {
        this.id = id;
    }

    public Long getCompetenceId() {
        return id != null ? id.getCompetenceId() : null;
    }

    public Item getItem() {
        return item;
    }

    public void setItem(Item item) {
        this.item = item;
    }
}
