package com.att.tdp.issueflow.comment;

import com.att.tdp.issueflow.common.error.NotFoundException;
import com.att.tdp.issueflow.ticket.Ticket;
import com.att.tdp.issueflow.ticket.TicketService;
import com.att.tdp.issueflow.user.User;
import com.att.tdp.issueflow.user.UserService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.att.tdp.issueflow.audit.AuditAction;
import com.att.tdp.issueflow.audit.AuditEntityType;
import com.att.tdp.issueflow.audit.AuditLogService;
import com.att.tdp.issueflow.mention.MentionService;

import java.util.List;

@Service
public class CommentService {

    private final CommentRepository commentRepository;
    private final TicketService ticketService;
    private final UserService userService;
    private final AuditLogService auditLogService;
    private final MentionService mentionService;

    public CommentService(
            CommentRepository commentRepository,
            TicketService ticketService,
            UserService userService,
            AuditLogService auditLogService,
            MentionService mentionService
    ) {
        this.commentRepository = commentRepository;
        this.ticketService = ticketService;
        this.userService = userService;
        this.auditLogService = auditLogService;
        this.mentionService = mentionService;
    }

    @Transactional
    public CommentResponse addComment(Long ticketId, CreateCommentRequest request) {
        Ticket ticket = ticketService.findActiveTicketEntity(ticketId);
        User author = userService.findUserEntity(request.authorId());

        Comment comment = new Comment();
        comment.setTicket(ticket);
        comment.setAuthor(author);
        comment.setContent(request.content());
        Comment saved = commentRepository.save(comment);
        mentionService.reevaluateMentions(saved);

        auditLogService.recordUserAction(
                AuditAction.CREATE,
                author.getId(),
                AuditEntityType.COMMENT,
                saved.getId(),
                "Comment created"
        );
        return CommentResponse.from(saved, mentionService.getMentionedUsers(saved.getId()));
    }

    @Transactional(readOnly = true)
    public List<CommentResponse> getCommentsForTicket(Long ticketId) {
        ticketService.findActiveTicketEntity(ticketId);

        return commentRepository.findByTicketIdOrderByCreatedAtAsc(ticketId)
                .stream()
                .map(comment -> CommentResponse.from(
                        comment,
                        mentionService.getMentionedUsers(comment.getId())
                ))
                .toList();
    }

    @Transactional
    public CommentResponse updateComment(Long commentId, UpdateCommentRequest request) {
        Comment comment = findCommentEntity(commentId);
        comment.setContent(request.content());

        Comment saved = commentRepository.saveAndFlush(comment);

        auditLogService.recordUserAction(
                AuditAction.UPDATE,
                comment.getAuthor().getId(),
                AuditEntityType.COMMENT,
                saved.getId(),
                "Comment updated"
        );

        mentionService.reevaluateMentions(saved);
        return CommentResponse.from(saved, mentionService.getMentionedUsers(saved.getId()));
    }

    @Transactional
    public void deleteComment(Long commentId) {
        Comment comment = findCommentEntity(commentId);
        mentionService.deleteMentionsForComment(comment.getId());
        commentRepository.delete(comment);
        auditLogService.recordUserAction(
                AuditAction.DELETE,
                comment.getAuthor().getId(),
                AuditEntityType.COMMENT,
                comment.getId(),
                "Comment deleted"
        );
    }

    public Comment findCommentEntity(Long commentId) {
        return commentRepository.findById(commentId)
                .orElseThrow(() -> new NotFoundException("Comment not found: " + commentId));
    }
}