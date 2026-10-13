package com.nagriksetu.community;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/** Upvotes and follows. One row per (complaint, user) is guaranteed by the tables' primary keys. */
@Repository
public class CommunityRepository {

    private final NamedParameterJdbcTemplate jdbc;

    public CommunityRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** @return true if a new upvote was recorded, false if this user had already upvoted. */
    public boolean addUpvote(long complaintId, long userId) {
        return jdbc.update("insert into upvotes (complaint_id, user_id) values (:c, :u) on conflict do nothing",
                Map.of("c", complaintId, "u", userId)) == 1;
    }

    public boolean removeUpvote(long complaintId, long userId) {
        return jdbc.update("delete from upvotes where complaint_id = :c and user_id = :u",
                Map.of("c", complaintId, "u", userId)) == 1;
    }

    public boolean addFollow(long complaintId, long userId) {
        return jdbc.update("insert into complaint_followers (complaint_id, user_id) values (:c, :u) on conflict do nothing",
                Map.of("c", complaintId, "u", userId)) == 1;
    }

    public boolean removeFollow(long complaintId, long userId) {
        return jdbc.update("delete from complaint_followers where complaint_id = :c and user_id = :u",
                Map.of("c", complaintId, "u", userId)) == 1;
    }

    public Set<Long> upvotedAmong(long userId, Collection<Long> complaintIds) {
        return idsAmong("upvotes", userId, complaintIds);
    }

    public Set<Long> followedAmong(long userId, Collection<Long> complaintIds) {
        return idsAmong("complaint_followers", userId, complaintIds);
    }

    public List<Long> followerIds(long complaintId) {
        return jdbc.queryForList("select user_id from complaint_followers where complaint_id = :c",
                Map.of("c", complaintId), Long.class);
    }

    public int countUpvotes(long complaintId) {
        Integer n = jdbc.queryForObject("select count(*) from upvotes where complaint_id = :c", Map.of("c", complaintId), Integer.class);
        return n == null ? 0 : n;
    }

    private Set<Long> idsAmong(String table, long userId, Collection<Long> complaintIds) {
        if (complaintIds.isEmpty()) {
            return Set.of();
        }
        return new HashSet<>(jdbc.queryForList(
                "select complaint_id from " + table + " where user_id = :u and complaint_id in (:ids)",
                Map.of("u", userId, "ids", complaintIds), Long.class));
    }
}
