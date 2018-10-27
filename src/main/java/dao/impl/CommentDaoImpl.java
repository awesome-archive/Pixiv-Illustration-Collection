package dao.impl;

import dao.CommentDao;
import domain.Comment;
import org.apache.commons.dbutils.QueryRunner;
import org.apache.commons.dbutils.handlers.BeanHandler;
import org.apache.commons.dbutils.handlers.BeanListHandler;
import org.apache.commons.dbutils.handlers.ScalarHandler;
import utils.DBPoolUtil;

import java.sql.SQLException;
import java.util.List;

public class CommentDaoImpl implements CommentDao {
    @Override
    public Long submitComment(Comment comment) throws SQLException {
        QueryRunner queryRunner = new QueryRunner(DBPoolUtil.getInstance().getDruidDataSource());
        String sql = "insert into comments (pid,user,email,content,time) values(?,?,?,?,?)";
        Long id = queryRunner.insert(sql,new ScalarHandler<>(), comment.getPid(),  comment.getUser(), comment.getEmail(), comment.getContent(), comment.getTime());
        return id;
    }

    @Override
    public List<Comment> pullComment() throws SQLException {
        QueryRunner queryRunner = new QueryRunner(DBPoolUtil.getInstance().getDruidDataSource());
        String sql = "select pid,id,user,content,time from comments where id != 0 order by id desc";
        List<Comment> comments = queryRunner.query(sql, new BeanListHandler<>(Comment.class));
        return comments;
    }

    @Override
    public Comment findParentComment(String id) throws SQLException {
        QueryRunner queryRunner = new QueryRunner(DBPoolUtil.getInstance().getDruidDataSource());
        String sql = "select * from comments where id=?";
        Comment comment = queryRunner.query(sql, id, new BeanHandler<Comment>(Comment.class));
        return comment;
    }
}
