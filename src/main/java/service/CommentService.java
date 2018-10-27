package service;

import domain.Comment;

import java.sql.SQLException;
import java.util.List;

public interface CommentService {
    Long submitComment(Comment comment) throws Exception;
    List<Comment> pullComment() throws SQLException;
}
