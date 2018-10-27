package dao;

import domain.Comment;

import java.sql.SQLException;
import java.util.List;

public interface CommentDao {
    Long submitComment(Comment comment) throws SQLException;
    List<Comment> pullComment() throws SQLException;
    Comment findParentComment(String id) throws SQLException;
}
