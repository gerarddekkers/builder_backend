package com.mentesme.builder.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "item_translations")
public class ItemTranslation {

    @EmbeddedId
    private ItemTranslationId id;

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("itemId")
    @JoinColumn(name = "item_id")
    private Item item;

    @Column(name = "text", nullable = false, columnDefinition = "TEXT")
    private String text;

    public ItemTranslation() {
    }

    public ItemTranslation(Item item, String languageCode, String text) {
        this.id = new ItemTranslationId(item.getId(), languageCode);
        this.item = item;
        this.text = text;
    }

    public ItemTranslationId getId() {
        return id;
    }

    public void setId(ItemTranslationId id) {
        this.id = id;
    }

    public Item getItem() {
        return item;
    }

    public void setItem(Item item) {
        this.item = item;
    }

    public String getLanguageCode() {
        return id != null ? id.getLanguageCode() : null;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }
}
