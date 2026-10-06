package com.scalegrams.nutrition;

import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import java.util.StringJoiner;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import com.scalegrams.catalog.FoodCategory;
import com.scalegrams.catalog.FoodPreparation;

@Repository
public class FoodVectorRepository {
    private final JdbcTemplate jdbc;

    public FoodVectorRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<FoodVectorMatch> findSimilar(float[] embedding, String model, FoodCategory category,
            FoodPreparation preparation, int limit, double minimumSimilarity) {
        String vector = toPgVector(embedding);
        StringBuilder sql = new StringBuilder("""
                select e.food_id, 1 - (e.embedding <=> cast(? as vector)) as similarity
                from food_embedding e
                join food f on f.id = e.food_id
                where e.model_name = ?
                  and f.deleted_at is null
                  and f.moderation_status = 'APPROVED'
                  and (e.embedding <=> cast(? as vector)) <= ?
                """);
        List<Object> args = new ArrayList<>(List.of(vector, model, vector, 1.0 - minimumSimilarity));
        if (category != null) {
            sql.append(" and f.category = ?");
            args.add(category.name());
        }
        if (preparation != null && preparation != FoodPreparation.UNSPECIFIED) {
            sql.append(" and (f.preparation = ? or f.preparation = 'UNSPECIFIED')");
            args.add(preparation.name());
        }
        sql.append(" order by e.embedding <=> cast(? as vector), f.id limit ?");
        args.add(vector);
        args.add(limit);
        return jdbc.query(sql.toString(), FoodVectorMatch.ROW_MAPPER, args.toArray());
    }

    public void save(long foodId, float[] embedding, String model, String sourceText) {
        jdbc.update("""
                insert into food_embedding (food_id, embedding, model_name, source_text, indexed_at)
                values (?, cast(? as vector), ?, ?, now())
                on conflict (food_id) do update
                   set embedding = excluded.embedding,
                       model_name = excluded.model_name,
                       source_text = excluded.source_text,
                       indexed_at = now()
                """, foodId, toPgVector(embedding), model, sourceText);
    }

    public List<FoodEmbeddingSource> findMissingEmbeddings(String model, int limit) {
        return jdbc.query("""
                select f.id, f.name, f.brand, f.category, f.preparation, f.search_tags
                from food f
                left join food_embedding e on e.food_id = f.id and e.model_name = ?
                where f.deleted_at is null
                  and f.moderation_status = 'APPROVED'
                  and e.food_id is null
                order by f.id
                limit ?
                """, FoodEmbeddingSource.ROW_MAPPER, model, limit);
    }

    public void delete(long foodId) {
        jdbc.update("delete from food_embedding where food_id = ?", foodId);
    }

    private static String toPgVector(float[] values) {
        StringJoiner vector = new StringJoiner(",", "[", "]");
        for (float value : values) vector.add(Float.toString(value));
        return vector.toString();
    }

    public record FoodVectorMatch(long foodId, double similarity) {
        private static final RowMapper<FoodVectorMatch> ROW_MAPPER = (ResultSet rs, int row) ->
                new FoodVectorMatch(rs.getLong("food_id"), rs.getDouble("similarity"));
    }

    public record FoodEmbeddingSource(long id, String name, String brand, String category,
            String preparation, String tags) {
        private static final RowMapper<FoodEmbeddingSource> ROW_MAPPER = (ResultSet rs, int row) ->
                new FoodEmbeddingSource(rs.getLong("id"), rs.getString("name"), rs.getString("brand"),
                        rs.getString("category"), rs.getString("preparation"), rs.getString("search_tags"));
    }
}
