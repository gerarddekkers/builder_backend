package com.mentesme.builder.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.util.Objects;

@Embeddable
public class CompetenceItemId implements Serializable {

    @Column(name = "competence_id")
    private Long competenceId;

    @Column(name = "item_id")
    private Long itemId;

    public CompetenceItemId() {
    }

    public CompetenceItemId(Long competenceId, Long itemId) {
        this.competenceId = competenceId;
        this.itemId = itemId;
    }

    public Long getCompetenceId() {
        return competenceId;
    }

    public void setCompetenceId(Long competenceId) {
        this.competenceId = competenceId;
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
        CompetenceItemId that = (CompetenceItemId) o;
        return Objects.equals(competenceId, that.competenceId) && Objects.equals(itemId, that.itemId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(competenceId, itemId);
    }
}
