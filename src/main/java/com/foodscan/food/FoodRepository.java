package com.foodscan.food;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FoodRepository extends JpaRepository<Food, Long> {

    @EntityGraph(attributePaths = "portions")
    Optional<Food> findWithPortionsById(Long id);

    @EntityGraph(attributePaths = "portions")
    List<Food> findBySourceAndSourceRefIn(String source, Collection<String> sourceRefs);

    /**
     * Ranked search combining Postgres full-text search (whole words, with
     * stemming and prefixes) and trigram similarity (tolerates typos and word
     * order). Query words are OR'ed, so foods matching more of them rank
     * higher while partial matches still show up. Native because JPQL has no
     * equivalent for tsvector or pg_trgm.
     */
    @Query(nativeQuery = true, value = """
            WITH q AS (
                SELECT lower(:q) AS text,
                       CAST((SELECT string_agg(quote_literal(lexeme) || ':*', ' | ')
                               FROM unnest(tsvector_to_array(to_tsvector('english', :q))) AS lexeme)
                            AS tsquery) AS tsq
            )
            SELECT f.id            AS "id",
                   f.name          AS "name",
                   f.data_type     AS "dataType",
                   f.category      AS "category",
                   f.kcal_100g     AS "kcal100g",
                   f.protein_100g  AS "protein100g",
                   f.carbs_100g    AS "carbs100g",
                   f.fat_100g      AS "fat100g",
                   coalesce(ts_rank(f.name_tsv, q.tsq, 1), 0)
                     + 0.5 * word_similarity(q.text, lower(f.name))
                     + CASE f.data_type
                           WHEN 'Survey (FNDDS)' THEN 0.05
                           WHEN 'Foundation'     THEN 0.03
                           ELSE 0
                       END         AS "score"
              FROM foods f, q
             WHERE (f.name_tsv @@ q.tsq OR q.text <% lower(f.name))
               AND f.owner_user_id IS NULL
             ORDER BY "score" DESC, length(f.name)
             LIMIT :limit
            """)
    List<SearchRow> search(@Param("q") String query, @Param("limit") int limit);

    /** Projection for {@link #search}; property names match the column aliases. */
    interface SearchRow {
        Long getId();
        String getName();
        String getDataType();
        String getCategory();
        BigDecimal getKcal100g();
        BigDecimal getProtein100g();
        BigDecimal getCarbs100g();
        BigDecimal getFat100g();
        Double getScore();
    }
}
