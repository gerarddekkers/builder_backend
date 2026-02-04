package com.mentesme.builder.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.util.Objects;

@Embeddable
public class ItemTranslationId implements Serializable {

    @Column(name = "item_id")
    private Long itemId;

    @Column(name = "language_code", length = 5)
    private String languageCode;

    public ItemTranslationId() {
    }

    public ItemTranslationId(Long itemId, String languageCode) {
        this.itemId = itemId;
        this.languageCode = languageCode;
    }

    public Long getItemId() {
        return itemId;
    }

    public void setItemId(Long itemId) {
        this.itemId = itemId;
    }

    public String getLanguageCode() {
        return languageCode;
    }

    public void setLanguageCode(String languageCode) {
        this.languageCode = languageCode;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ItemTranslationId that = (ItemTranslationId) o;
        return Objects.equals(itemId, that.itemId) && Objects.equals(languageCode, that.languageCode);
    }

    @Override
    public int hashCode() {
        return Objects.hash(itemId, languageCode);
    }
}
