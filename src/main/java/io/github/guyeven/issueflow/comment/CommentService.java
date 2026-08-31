package io.github.guyeven.issueflow.comment;

import io.github.guyeven.issueflow.common.error.NotFoundException;
import io.github.guyeven.issueflow.ticket.Ticket;
import io.github.guyeven.issueflow.ticket.TicketService;
import io.github.guyeven.issueflow.user.User;
import io.github.guyeven.issueflow.common.security.CurrentUserService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import io.github.guyeven.issueflow.audit.AuditAction;
import io.github.guyeven.issueflow.audit.AuditEntityType;
import io.github.guyeven.issueflow.audit.AuditLogService;
import io.github.guyeven.issueflow.mention.MentionService;

import java.util.List;

@Service
public class CommentService {

    private final CommentRepository commentRepository;
    private final TicketService ticketService;
    private final CurrentUserService currentUserService;
    private final AuditLogService auditLogService;
    private final MentionService mentionService;

    public CommentService(
            CommentRepository commentRepository,
            TicketService ticketService,
            CurrentUserService currentUserService,
            AuditLogService auditLogService,
            MentionService mentionService
    ) {
        this.commentRepository = commentRepository;
        this.ticketService = ticketService;
        this.currentUserService = currentUserService;
        this.auditLogService = auditLogService;
        this.mentionService = mentionService;
    }

    @Transactional
    public CommentResponse addComment(Long ticketId, CreateCommentRequest request) {
        Ticket ticket = ticketService.findActiveTicketEntity(ticketId);
        User author = currentUserService.requireCurrentUser();

        Comment comment = new Comment();
        comment.setTicket(ticket);
        comment.setAuthor(author);
        comment.setContent(request.content());
        Comment saved = commentRepository.save(comment);
        mentionService.reevaluateMentions(saved);

        auditLogService.recordCurrentUserAction(
                AuditAction.CREATE,
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

        auditLogService.recordCurrentUserAction(
                AuditAction.UPDATE,
                AuditEntityType.COMMENT,
                saved.getId(),
                "Comment updated"
        );

        mentionService.reevaluateMentions(saved);
        return CommentResponse.from(saved, mentionService.getMentionedUsers(saved.getId()));
    }

    @Transactional
    public CommentResponse updateComment(Long ticketId, Long commentId, UpdateCommentRequest request) {
        Comment comment = findCommentForTicket(ticketId, commentId);
        comment.setContent(request.content());
        Comment saved = commentRepository.saveAndFlush(comment);
        auditLogService.recordCurrentUserAction(AuditAction.UPDATE, AuditEntityType.COMMENT,
                saved.getId(), "Comment updated");
        mentionService.reevaluateMentions(saved);
        return CommentResponse.from(saved, mentionService.getMentionedUsers(saved.getId()));
    }

    @Transactional
    public void deleteComment(Long commentId) {
        Comment comment = findCommentEntity(commentId);
        mentionService.deleteMentionsForComment(comment.getId());
        commentRepository.delete(comment);
        auditLogService.recordCurrentUserAction(
                AuditAction.DELETE,
                AuditEntityType.COMMENT,
                comment.getId(),
                "Comment deleted"
        );
    }

    @Transactional
    public void deleteComment(Long ticketId, Long commentId) {
        Comment comment = findCommentForTicket(ticketId, commentId);
        mentionService.deleteMentionsForComment(comment.getId());
        commentRepository.delete(comment);
        auditLogService.recordCurrentUserAction(AuditAction.DELETE, AuditEntityType.COMMENT,
                comment.getId(), "Comment deleted");
    }

    private Comment findCommentForTicket(Long ticketId, Long commentId) {
        ticketService.findActiveTicketEntity(ticketId);
        Comment comment = findCommentEntity(commentId);
        if (!comment.getTicket().getId().equals(ticketId)) {
            throw new NotFoundException("Comment not found for ticket: " + commentId);
        }
        return comment;
    }

    public Comment findCommentEntity(Long commentId) {
        return commentRepository.findById(commentId)
                .orElseThrow(() -> new NotFoundException("Comment not found: " + commentId));
    }
}
