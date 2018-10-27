package web.servlet;

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

@WebServlet(name = "SubmitCommentServlet", urlPatterns = "/submit")
public class SubmitCommentServlet extends HttpServlet {
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        request.setCharacterEncoding("UTF-8");
        String isRobot = request.getParameter("isRobot");
        Comment comment = null;
        PrintWriter out = null;
        String resp = null;
        if (isRobot != null && isRobot.equals("false")) {
            comment = new Comment();
            comment.setContent(request.getParameter("content"));
            comment.setEmail(request.getParameter("email"));
            comment.setPid(request.getParameter("pid"));
            comment.setTime(request.getParameter("time"));
            comment.setUser(request.getParameter("user"));
            CommentServiceImpl commentService = new CommentServiceImpl();
            try {
                Long id = commentService.submitComment(comment);
                comment.setId(String.valueOf(id));
                resp = new ObjectMapper().writeValueAsString(comment);
                HeaderUtil.decorateResponse(response);
                out = response.getWriter();
                out.write(resp);
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                out.close();
            }
        } else
            response.setStatus(500);
    }

    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        doPost(request, response);
    }
}
