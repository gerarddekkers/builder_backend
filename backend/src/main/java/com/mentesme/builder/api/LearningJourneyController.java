package com.mentesme.builder.api;

import com.mentesme.builder.model.LearningJourneyDetail;
import com.mentesme.builder.model.LearningJourneyListItem;
import com.mentesme.builder.service.LearningJourneyLookupRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/learning-journeys")
@CrossOrigin(origins = {"http://localhost:5173", "https://builder.mentes.me", "https://builder-prod.mentes.me"})
public class LearningJourneyController {

    private final LearningJourneyLookupRepository lookupRepository;

    public LearningJourneyController(LearningJourneyLookupRepository lookupRepository) {
        this.lookupRepository = lookupRepository;
    }

    @GetMapping
    public List<LearningJourneyListItem> list() {
        return lookupRepository.findAll();
    }

    @GetMapping("/{id}")
    public ResponseEntity<LearningJourneyDetail> getById(@PathVariable long id) {
        return lookupRepository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
}
