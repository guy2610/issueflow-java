package com.att.tdp.issueflow.comment;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
public class CommentController {

    private final CommentService commentService;

    public CommentController(CommentService commentService) {
        this.commentService = commentService;
    }

    @PostMapping("/tickets/{ticketId}/comments")
    @ResponseStatus(HttpStatus.OK)
    public CommentResponse addComment(
            @PathVariable Long ticketId,
            @Valid @RequestBody CreateCommentRequest request
    ) {
        return commentService.addComment(ticketId, request);
    }

    @GetMapping("/tickets/{ticketId}/comments")
    public List<CommentResponse> getCommentsForTicket(@PathVariable Long ticketId) {
        return commentService.getCommentsForTicket(ticketId);
    }

    @PostMapping("/comments/update/{commentId}")
    public CommentResponse updateComment(
            @PathVariable Long commentId,
            @Valid @RequestBody UpdateCommentRequest request
    ) {
        return commentService.updateComment(commentId, request);
    }

    @DeleteMapping("/comments/{commentId}")
    @ResponseStatus(HttpStatus.OK)
    public void deleteComment(@PathVariable Long commentId) {
        commentService.deleteComment(commentId);
    }

    @PatchMapping("/tickets/{ticketId}/comments/{commentId}")
    public CommentResponse patchComment(
            @PathVariable Long ticketId,
            @PathVariable Long commentId,
            @Valid @RequestBody UpdateCommentRequest request
    ) {
        return commentService.updateComment(commentId, request);
    }
    @DeleteMapping("/tickets/{ticketId}/comments/{commentId}")
    @ResponseStatus(HttpStatus.OK)
    public void deleteCommentByTicketPath(
            @PathVariable Long ticketId,
            @PathVariable Long commentId
    ) {
        commentService.deleteComment(commentId);
    }
}