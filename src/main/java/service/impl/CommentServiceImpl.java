package service.impl;

import dao.impl.CommentDaoImpl;
import domain.Comment;
import service.CommentService;
import utils.EmailUtil;

import java.sql.SQLException;
import java.util.List;

public class CommentServiceImpl implements CommentService {
    @Override
    public Long submitComment(Comment comment) throws Exception {
        CommentDaoImpl commentDao = new CommentDaoImpl();
        Long id = commentDao.submitComment(comment);
        Comment parentComment = commentDao.findParentComment(comment.getPid());
        if(id!=null&&parentComment!=null){
            EmailUtil.senEmailto(comment,parentComment);
        }
        return id;
    }

    @Override
    public List<Comment> pullComment() throws SQLException {
        CommentDaoImpl commentDao = new CommentDaoImpl();
        return commentDao.pullComment();
    }
}
