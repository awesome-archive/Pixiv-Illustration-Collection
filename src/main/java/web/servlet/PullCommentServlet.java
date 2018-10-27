package web.servlet;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import domain.Comment;
import service.impl.CommentServiceImpl;
import utils.HeaderUtil;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;
import java.sql.SQLException;
import java.util.List;

@WebServlet(name = "PullCommentServlet", urlPatterns = "/pull")
public class PullCommentServlet extends HttpServlet {
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        doGet(request, response);
    }

    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        request.setCharacterEncoding("utf-8");
        List<Comment> commentsList = null;
        PrintWriter out = null;
        String comments = null;
        try {
            commentsList = new CommentServiceImpl().pullComment();
            comments = new ObjectMapper().setSerializationInclusion(JsonInclude.Include.NON_NULL).writeValueAsString(commentsList);
            HeaderUtil.decorateResponse(response);
            out = response.getWriter();
            out.write(comments);
        } catch (SQLException e) {
            e.printStackTrace();
        } finally {
            out.close();
        }
    }
}
