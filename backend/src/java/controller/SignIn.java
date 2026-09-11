package controller;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import entity.User;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.text.SimpleDateFormat;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import model.HibernateUtil;
import org.hibernate.Criteria;
import org.hibernate.Session;
import org.hibernate.criterion.Restrictions;

@WebServlet(name = "SignIn", urlPatterns = {"/SignIn"})
public class SignIn extends HttpServlet {

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        resp.setContentType("application/json");
        resp.setCharacterEncoding("UTF-8");

        JsonObject jsonResponse = new JsonObject();
        Gson gson = new Gson();

        try {
            StringBuilder sb = new StringBuilder();
            BufferedReader reader = new BufferedReader(new InputStreamReader(req.getInputStream()));
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
            JsonObject requestBody = gson.fromJson(sb.toString(), JsonObject.class);

            String mobile = requestBody.has("mobile") ? requestBody.get("mobile").getAsString() : "";
            String password = requestBody.has("password") ? requestBody.get("password").getAsString() : "";

            if (mobile == null || mobile.trim().isEmpty()) {
                jsonResponse.addProperty("success", false);
                jsonResponse.addProperty("message", "Please enter your Mobile Number");
                resp.getWriter().write(jsonResponse.toString());
                return;
            }

            if (mobile.length() != 10) {
                jsonResponse.addProperty("success", false);
                jsonResponse.addProperty("message", "Mobile number must be 10 digits");
                resp.getWriter().write(jsonResponse.toString());
                return;
            }

            if (password == null || password.trim().isEmpty()) {
                jsonResponse.addProperty("success", false);
                jsonResponse.addProperty("message", "Please enter your Password");
                resp.getWriter().write(jsonResponse.toString());
                return;
            }

            Session session = HibernateUtil.getSessionFactory().openSession();
            Criteria criteria = session.createCriteria(User.class);
            criteria.add(Restrictions.eq("mobile", mobile));
            User user = (User) criteria.uniqueResult();

            if (user == null) {
                jsonResponse.addProperty("success", false);
                jsonResponse.addProperty("message", "Invalid Details!");
                session.close();
                resp.getWriter().write(jsonResponse.toString());
                return;
            }

            // TODO: hash passwords
            if (!password.equals(user.getPassword())) {
                jsonResponse.addProperty("success", false);
                jsonResponse.addProperty("message", "Invalid Details!");
                session.close();
                resp.getWriter().write(jsonResponse.toString());
                return;
            }

            if (user.getUser_status() == null || user.getUser_status().getId() != 1) {
                jsonResponse.addProperty("success", false);
                jsonResponse.addProperty("message", "Account is not active");
                session.close();
                resp.getWriter().write(jsonResponse.toString());
                return;
            }

            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");
            String registeredDt = user.getRegistered_date_time() != null
                    ? sdf.format(user.getRegistered_date_time()) : "";

            JsonObject userObj = new JsonObject();
            userObj.addProperty("id", user.getId());
            userObj.addProperty("mobile", user.getMobile());
            userObj.addProperty("first_name", user.getFirst_name());
            userObj.addProperty("last_name", user.getLast_name());
            userObj.addProperty("user_status_id",
                    user.getUser_status() != null ? user.getUser_status().getId() : 0);
            userObj.addProperty("registered_date_time", registeredDt);

            jsonResponse.addProperty("success", true);
            jsonResponse.addProperty("message", "Sign in success");
            jsonResponse.add("user", userObj);

            session.close();

        } catch (Exception e) {
            e.printStackTrace();
            jsonResponse = new JsonObject();
            jsonResponse.addProperty("success", false);
            jsonResponse.addProperty("message", "Server error. Please try again later.");
        }

        resp.getWriter().write(jsonResponse.toString());
    }
}
