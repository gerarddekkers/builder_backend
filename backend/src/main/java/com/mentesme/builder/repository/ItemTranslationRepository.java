package com.mentesme.builder.repository;

import com.mentesme.builder.entity.ItemTranslation;
import com.mentesme.builder.entity.ItemTranslationId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ItemTranslationRepository extends JpaRepository<ItemTranslation, ItemTranslationId> {

    List<ItemTranslation> findByIdItemId(Long itemId);

    List<ItemTranslation> findByIdLanguageCode(String languageCode);
}
