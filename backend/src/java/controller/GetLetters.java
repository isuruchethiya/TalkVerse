package controller;

import com.google.gson.JsonObject;
import entity.User;
import java.io.File;
import java.io.IOException;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import model.HibernateUtil;
import org.hibernate.Criteria;
import org.hibernate.Session;
import org.hibernate.criterion.Restrictions;

@WebServlet(name = "GetLetters", urlPatterns = {"/GetLetters"})
public class GetLetters extends HttpServlet {

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        resp.setContentType("application/json");
        resp.setCharacterEncoding("UTF-8");

        String mobile = req.getParameter("mobile");
        JsonObject jsonResponse = new JsonObject();

        try {
            String letters = "";
            boolean avatarFound = false;

            String avatarPath = getServletContext().getRealPath("/AvaterImages/" + mobile + ".png");
            File avatarFile = new File(avatarPath);
            avatarFound = avatarFile.isFile() && avatarFile.length() > 0;

            if (mobile != null && mobile.length() == 10) {
                Session session = HibernateUtil.getSessionFactory().openSession();
                Criteria criteria = session.createCriteria(User.class);
                criteria.add(Restrictions.eq("mobile", mobile));
                User user = (User) criteria.uniqueResult();

                if (user != null) {
                    String fn = user.getFirst_name();
                    String ln = user.getLast_name();
                    if (fn != null && !fn.isEmpty() && ln != null && !ln.isEmpty()) {
                        letters = fn.substring(0, 1).toUpperCase() + ln.substring(0, 1).toUpperCase();
                    } else if (fn != null && !fn.isEmpty()) {
                        letters = fn.substring(0, 1).toUpperCase();
                    }
                }
                session.close();
            }

            jsonResponse.addProperty("letters", letters);
            jsonResponse.addProperty("avatar_image_found", avatarFound);

        } catch (Exception e) {
            e.printStackTrace();
            jsonResponse.addProperty("letters", "");
            jsonResponse.addProperty("avatar_image_found", false);
        }

        resp.getWriter().write(jsonResponse.toString());
    }
}
