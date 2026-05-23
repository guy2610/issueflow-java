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
    @ResponseStatus(HttpStatus.CREATED)
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
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteComment(@PathVariable Long commentId) {
        commentService.deleteComment(commentId);
    }
}