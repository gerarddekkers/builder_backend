package com.mentesme.builder.api;

import com.mentesme.builder.service.BuilderProjectRepository;
import com.mentesme.builder.service.BuilderProjectRepository.ProjectListItem;
import com.mentesme.builder.service.BuilderProjectRepository.ProjectRow;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/projects")
@CrossOrigin(origins = {"http://localhost:5173", "https://builder.mentes.me", "https://builder-prod.mentes.me"})
public class ProjectController {

    private final BuilderProjectRepository repository;

    public ProjectController(BuilderProjectRepository repository) {
        this.repository = repository;
    }

    @GetMapping
    public List<ProjectListItem> listProjects() {
        return repository.listAll();
    }

    @GetMapping("/{id}")
    public ResponseEntity<ProjectRow> getProject(@PathVariable String id) {
        return repository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PutMapping("/{id}")
    public ResponseEntity<Map<String, String>> saveProject(
            @PathVariable String id,
            @RequestBody SaveProjectRequest request,
            HttpServletRequest httpRequest) {
        String username = (String) httpRequest.getAttribute("userName");
        if (username == null) username = "unknown";
        repository.save(id, request.name(), request.projectData(), request.currentStep(), username);
        return ResponseEntity.ok(Map.of("status", "saved", "id", id));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteProject(@PathVariable String id) {
        boolean deleted = repository.deleteById(id);
        return deleted ? ResponseEntity.noContent().build() : ResponseEntity.notFound().build();
    }

    public record SaveProjectRequest(String name, String projectData, int currentStep) {}
}
