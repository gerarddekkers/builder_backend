package com.mentesme.builder.repository;

import com.mentesme.builder.entity.QuestionnaireItem;
import com.mentesme.builder.entity.QuestionnaireItemId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface QuestionnaireItemRepository extends JpaRepository<QuestionnaireItem, QuestionnaireItemId> {

    List<QuestionnaireItem> findByIdQuestionnaireIdOrderBySortOrder(Long questionnaireId);

    List<QuestionnaireItem> findByIdItemId(Long itemId);
}
