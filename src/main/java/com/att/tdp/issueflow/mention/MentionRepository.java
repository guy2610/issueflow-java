package com.att.tdp.issueflow.mention;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MentionRepository extends JpaRepository<Mention, Long> {

    List<Mention> findByCommentId(Long commentId);

    List<Mention> findByMentionedUserIdOrderByCommentCreatedAtDesc(Long userId);

    void deleteByCommentId(Long commentId);
}