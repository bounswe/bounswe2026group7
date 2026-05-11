package com.group7.backend.service.ranking.feed;

import com.group7.backend.entity.FeedPost;
import com.group7.backend.entity.FeedPostHashtag;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashSet;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DiversityFloorEnforcerTest {

    private final DiversityFloorEnforcer enforcer = new DiversityFloorEnforcer();

    @Test
    void allInTopHashtags_noOutsiderInPool_returnsOriginal() {
        // Pool is single-topic. Page is sliced from it. No outsider exists,
        // so the floor must be a no-op rather than failing the request.
        List<FeedPost> pool = List.of(
                post(1L, List.of("ai")),
                post(2L, List.of("ai")),
                post(3L, List.of("ai")));
        List<DiversityFloorEnforcer.Placed> page = List.of(
                placed(pool.get(0), 90),
                placed(pool.get(1), 80));

        List<DiversityFloorEnforcer.Placed> out = enforcer.enforce(page, pool, 3);

        assertThat(out).isEqualTo(page);
    }

    @Test
    void pageAlreadyContainsOutsider_noSwap() {
        List<FeedPost> pool = List.of(
                post(1L, List.of("ai")),
                post(2L, List.of("ai")),
                post(3L, List.of("cooking")));
        List<DiversityFloorEnforcer.Placed> page = List.of(
                placed(pool.get(0), 90),
                placed(pool.get(2), 75)); // cooking is outsider already in page

        List<DiversityFloorEnforcer.Placed> out = enforcer.enforce(page, pool, 1);

        assertThat(out).isEqualTo(page);
    }

    @Test
    void pageMonopolizedByTopHashtags_swapsInOutsider() {
        // Pool: 5 ai, 5 ml, 5 cooking, 1 photography. Top-3 by frequency
        // = {ai, ml, cooking}. Page is ai+ai+ml; photography is the
        // outsider waiting in the pool.
        FeedPost photo = post(99L, List.of("photography"));
        List<FeedPost> pool = new java.util.ArrayList<>();
        for (int i = 0; i < 5; i++) pool.add(post(i, List.of("ai")));
        for (int i = 5; i < 10; i++) pool.add(post(i, List.of("ml")));
        for (int i = 10; i < 15; i++) pool.add(post(i, List.of("cooking")));
        pool.add(photo);

        List<DiversityFloorEnforcer.Placed> page = List.of(
                placed(pool.get(0), 95),
                placed(pool.get(1), 90),
                placed(pool.get(5), 85));

        List<DiversityFloorEnforcer.Placed> out = enforcer.enforce(page, pool, 3);

        // The lowest-scoring placed slot was the ml post at score 85;
        // it's swapped out for the photography post.
        assertThat(out).hasSize(3);
        assertThat(out.get(0)).isEqualTo(page.get(0));
        assertThat(out.get(1)).isEqualTo(page.get(1));
        assertThat(out.get(2).post().getId()).isEqualTo(photo.getId());
        assertThat(out.get(2).factors()).containsExactly("feed:outside-primary-goal");
    }

    @Test
    void postWithNoHashtagsTreatedAsOutsider() {
        // Pool: 3 ai posts + 1 tag-less post. Page is all ai. The tag-less
        // post is the outsider because it can't be in any topic bubble.
        FeedPost untagged = post(99L, List.of());
        List<FeedPost> pool = List.of(
                post(1L, List.of("ai")),
                post(2L, List.of("ai")),
                post(3L, List.of("ai")),
                untagged);
        List<DiversityFloorEnforcer.Placed> page = List.of(
                placed(pool.get(0), 90),
                placed(pool.get(1), 80),
                placed(pool.get(2), 70));

        List<DiversityFloorEnforcer.Placed> out = enforcer.enforce(page, pool, 1);

        assertThat(out.get(2).post().getId()).isEqualTo(untagged.getId());
        assertThat(out.get(2).factors()).containsExactly("feed:outside-primary-goal");
    }

    @Test
    void emptyPageOrPool_returnedUnchanged() {
        FeedPost p = post(1L, List.of("ai"));
        assertThat(enforcer.enforce(List.of(), List.of(p), 3)).isEmpty();
        assertThat(enforcer.enforce(List.of(placed(p, 1)), List.of(), 3)).hasSize(1);
    }

    @Test
    void zeroTopHashtagsCount_noOp() {
        FeedPost p = post(1L, List.of("ai"));
        List<DiversityFloorEnforcer.Placed> page = List.of(placed(p, 1));
        assertThat(enforcer.enforce(page, List.of(p), 0)).isEqualTo(page);
    }

    @Test
    void factorsAreImmutable() {
        // Sanity: Placed.withExtraFactor returns a new instance with the
        // extra factor; the caller's original list is unchanged.
        FeedPost p = post(1L, List.of("ai"));
        DiversityFloorEnforcer.Placed original = placed(p, 50);
        DiversityFloorEnforcer.Placed extended = original.withExtraFactor("feed:new");

        assertThat(original.factors()).doesNotContain("feed:new");
        assertThat(extended.factors()).contains("feed:new");
    }

    // ── Fixtures ───────────────────────────────────────────────────────────

    private static FeedPost post(long id, List<String> tags) {
        FeedPost p = new FeedPost(99L, "body");
        p.setId(id);
        LinkedHashSet<FeedPostHashtag> hashtags = new LinkedHashSet<>();
        for (String t : tags) {
            hashtags.add(new FeedPostHashtag(p, t));
        }
        p.setHashtags(hashtags);
        return p;
    }

    private static DiversityFloorEnforcer.Placed placed(FeedPost post, int score) {
        return new DiversityFloorEnforcer.Placed(post, score, List.of());
    }
}
