package com.att.tdp.issueflow.mention;

import com.att.tdp.issueflow.audit.AuditAction;
import com.att.tdp.issueflow.audit.AuditEntityType;
import com.att.tdp.issueflow.audit.AuditLogService;
import com.att.tdp.issueflow.comment.Comment;
import com.att.tdp.issueflow.comment.CommentResponse;
import com.att.tdp.issueflow.user.User;
import com.att.tdp.issueflow.user.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

@Service
public class MentionService {

    private static final Pattern MENTION_PATTERN = Pattern.compile("@([A-Za-z0-9_.-]+)");
    private final MentionRepository mentionRepository;
    private final UserRepository userRepository;
    private final AuditLogService auditLogService;

    public MentionService(
            MentionRepository mentionRepository,
            UserRepository userRepository,
            AuditLogService auditLogService
    ) {
        this.mentionRepository = mentionRepository;
        this.userRepository = userRepository;
        this.auditLogService = auditLogService;
    }

    @Transactional
    public void reevaluateMentions(Comment comment) {
        mentionRepository.deleteByCommentId(comment.getId());

        Set<String> usernames = extractUsernames(comment.getContent());
        if (usernames.isEmpty()) {
            return;
        }

        List<User> users = userRepository.findByUsernameIgnoreCaseIn(usernames.stream().toList());

        for (User user : users) {
            Mention mention = new Mention();
            mention.setComment(comment);
            mention.setMentionedUser(user);

            Mention saved = mentionRepository.save(mention);

            auditLogService.recordCurrentUserAction(
                    AuditAction.CREATE,
                    AuditEntityType.MENTION,
                    saved.getId(),
                    "User " + user.getId() + " mentioned in comment " + comment.getId()
            );
        }
    }

    @Transactional(readOnly = true)
    public List<MentionedUserResponse> getMentionedUsers(Long commentId) {
        return mentionRepository.findByCommentId(commentId)
                .stream()
                .map(Mention::getMentionedUser)
                .map(MentionedUserResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<CommentResponse> getCommentsMentioningUser(Long userId) {
        return mentionRepository.findByMentionedUserIdOrderByCommentCreatedAtDesc(userId)
                .stream()
                .map(Mention::getComment)
                .map(comment -> CommentResponse.from(comment, getMentionedUsers(comment.getId())))
                .toList();
    }

    private Set<String> extractUsernames(String content) {
        Set<String> usernames = new LinkedHashSet<>();
        var matcher = MENTION_PATTERN.matcher(content);

        while (matcher.find()) {
            usernames.add(matcher.group(1));
        }

        return usernames;
    }
    @Transactional
    public void deleteMentionsForComment(Long commentId) {
        mentionRepository.deleteByCommentId(commentId);
    }
}