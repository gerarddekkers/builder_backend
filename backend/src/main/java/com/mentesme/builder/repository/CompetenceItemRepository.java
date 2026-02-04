package com.mentesme.builder.repository;

import com.mentesme.builder.entity.CompetenceItem;
import com.mentesme.builder.entity.CompetenceItemId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CompetenceItemRepository extends JpaRepository<CompetenceItem, CompetenceItemId> {

    List<CompetenceItem> findByIdCompetenceId(Long competenceId);

    List<CompetenceItem> findByIdItemId(Long itemId);
}
