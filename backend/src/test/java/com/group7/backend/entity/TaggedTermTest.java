package com.group7.backend.entity;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TaggedTermTest {

    @Test
    void equalsAndHashCodeUseBothLabelAndUri() {
        // Hibernate identifies element-collection rows by value equality.
        // Without @EqualsAndHashCode, every profile save would delete-then-
        // insert the entire collection because object identity differs.
        TaggedTerm a = new TaggedTerm("Machine learning",
                "http://data.europa.eu/esco/skill/abc");
        TaggedTerm b = new TaggedTerm("Machine learning",
                "http://data.europa.eu/esco/skill/abc");

        assertThat(a).isEqualTo(b);
        assertThat(a.hashCode()).isEqualTo(b.hashCode());
    }

    @Test
    void differentUrisYieldDifferentEntries() {
        TaggedTerm withUri = new TaggedTerm("Java",
                "http://data.europa.eu/esco/skill/x");
        TaggedTerm withoutUri = new TaggedTerm("Java", null);

        assertThat(withUri).isNotEqualTo(withoutUri);
    }

    @Test
    void differentLabelsAreNotEqualEvenWithSameUri() {
        TaggedTerm a = new TaggedTerm("Machine learning", "http://example.org/x");
        TaggedTerm b = new TaggedTerm("Deep learning", "http://example.org/x");

        assertThat(a).isNotEqualTo(b);
    }

    @Test
    void noArgsConstructorYieldsNullsForHibernate() {
        TaggedTerm empty = new TaggedTerm();

        assertThat(empty.getLabel()).isNull();
        assertThat(empty.getIdentifierUri()).isNull();
    }
}
