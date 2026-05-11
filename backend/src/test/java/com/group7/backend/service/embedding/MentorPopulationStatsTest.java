package com.group7.backend.service.embedding;

import com.group7.backend.entity.Mentor;
import com.group7.backend.repository.MentorRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.offset;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MentorPopulationStatsTest {

    private MentorRepository mentorRepository;
    private SemanticSimilarityService similarity;
    private MentorPopulationStats stats;

    @BeforeEach
    void setUp() {
        mentorRepository = mock(MentorRepository.class);
        similarity = mock(SemanticSimilarityService.class);
        stats = new MentorPopulationStats(mentorRepository, similarity);
    }

    private static Mentor mentor(long id, String goals, String expertise, String field) {
        Mentor m = new Mentor();
        m.setId(id);
        m.setMentoringGoals(goals);
        m.setExpertise(expertise);
        m.setField(field);
        return m;
    }

    @Test
    void centroid_emptyBeforeFirstRefresh() {
        assertThat(stats.centroid()).isEmpty();
    }

    @Test
    void refresh_emptyMentorPool_keepsCentroidEmpty() {
        when(mentorRepository.findAll()).thenReturn(List.of());
        stats.refresh();
        assertThat(stats.centroid()).isEmpty();
    }

    @Test
    void refresh_computesMeanOfNonEmptyEmbeddings() {
        when(mentorRepository.findAll()).thenReturn(List.of(
                mentor(1, "g", "e", "f"),
                mentor(2, "g", "e", "f"),
                mentor(3, "g", "e", "f")));
        // Three mentors → three vectors: [1,0], [0,1], [2,2]. Mean = [1, 1].
        when(similarity.embed(anyString())).thenReturn(
                new float[]{1, 0},
                new float[]{0, 1},
                new float[]{2, 2});

        stats.refresh();
        Optional<float[]> centroid = stats.centroid();
        assertThat(centroid).isPresent();
        assertThat(centroid.get().length).isEqualTo(2);
        assertThat((double) centroid.get()[0]).isCloseTo(1.0, offset(1e-6));
        assertThat((double) centroid.get()[1]).isCloseTo(1.0, offset(1e-6));
    }

    @Test
    void refresh_skipsEmptyEmbeddings() {
        when(mentorRepository.findAll()).thenReturn(List.of(
                mentor(1, "g", "e", "f"),
                mentor(2, null, null, null),   // empty profile → empty embedding
                mentor(3, "g", "e", "f")));
        when(similarity.embed(anyString())).thenReturn(
                new float[]{4, 0},
                new float[0],                  // skipped
                new float[]{0, 4});

        stats.refresh();
        // Centroid of [4,0] and [0,4] → [2, 2]; the empty vector doesn't drag it.
        Optional<float[]> centroid = stats.centroid();
        assertThat(centroid).isPresent();
        assertThat((double) centroid.get()[0]).isCloseTo(2.0, offset(1e-6));
        assertThat((double) centroid.get()[1]).isCloseTo(2.0, offset(1e-6));
    }

    @Test
    void refresh_allEmpty_keepsPreviousCentroid() {
        // Seed an initial centroid.
        when(mentorRepository.findAll()).thenReturn(List.of(mentor(1, "g", "e", "f")));
        when(similarity.embed(anyString())).thenReturn(new float[]{1, 2, 3});
        stats.refresh();
        float[] firstCentroid = stats.centroid().orElseThrow();

        // Next refresh — every mentor has an empty profile. Centroid stays.
        when(mentorRepository.findAll()).thenReturn(List.of(mentor(2, null, null, null)));
        when(similarity.embed(anyString())).thenReturn(new float[0]);
        stats.refresh();

        assertThat(stats.centroid()).isPresent();
        assertThat(stats.centroid().get()).isEqualTo(firstCentroid);
    }

    @Test
    void refresh_repositoryThrows_doesNotPropagate() {
        when(mentorRepository.findAll()).thenThrow(new RuntimeException("DB down"));
        stats.refresh();
        // Doesn't crash; centroid stays at whatever it was before.
        assertThat(stats.centroid()).isEmpty();
    }

    @Test
    void setCentroidForTest_overridesValueDirectly() {
        stats.setCentroidForTest(new float[]{9, 9, 9});
        assertThat(stats.centroid()).isPresent();
        assertThat(stats.centroid().get()).containsExactly(9f, 9f, 9f);
    }
}
