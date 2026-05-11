package com.group7.backend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A taxonomy entry: a free-text label paired with an optional canonical URI
 * from a controlled vocabulary (ESCO, Wikidata, or ISCED-F).
 *
 * <p>Used as the element type of the four profile element-collections
 * (mentor interests, mentor preferred mentee skills, mentee interests,
 * mentee skills). Hibernate identifies element-collection rows by value
 * equality, so {@code @EqualsAndHashCode} is mandatory: without it every
 * profile save would delete-then-insert the entire collection on each
 * update.
 */
@Embeddable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class TaggedTerm {

    @Column(nullable = false)
    private String label;

    @Column(name = "identifier_uri")
    private String identifierUri;
}
