package com.mentesme.builder.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record DocumentInput(
        @NotBlank String label,
        @NotBlank String fileName,
        String url,
        @NotBlank @Pattern(regexp = "^(nl|en)$", message = "Lang must be 'nl' or 'en'") String lang
) {
}
