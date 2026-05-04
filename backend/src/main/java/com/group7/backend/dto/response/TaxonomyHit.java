package com.group7.backend.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "A taxonomy entry: a display label paired with its canonical URI")
public record TaxonomyHit(
        @Schema(description = "Display label", example = "Machine learning")
        String label,
        @Schema(description = "Canonical URI from the source taxonomy",
                example = "http://data.europa.eu/esco/skill/ccd0a1d9-afda-43d9-b901-96344886e14d")
        String identifierUri
) {}
