package controller;

import com.google.gson.JsonObject;
import entity.User;
import entity.UserStatus;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Date;
import javax.servlet.ServletException;
import javax.servlet.annotation.MultipartConfig;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.Part;
import model.HibernateUtil;
import org.hibernate.Criteria;
import org.hibernate.Session;
import org.hibernate.Transaction;
import org.hibernate.criterion.Restrictions;

@WebServlet(name = "SignUp", urlPatterns = {"/SignUp"})
@MultipartConfig(
        fileSizeThreshold = 1024 * 1024 * 2,
        maxFileSize = 1024 * 1024 * 5,
        maxRequestSize = 1024 * 1024 * 10
)
public class SignUp extends HttpServlet {

    private static final String AVATAR_FOLDER = "AvaterImages";
    private static final long MAX_AVATAR_BYTES = 2L * 1024 * 1024;

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        resp.setContentType("application/json");
        resp.setCharacterEncoding("UTF-8");

        JsonObject jsonResponse = new JsonObject();
        Session session = null;
        Transaction tx = null;

        try {
            String mobile = req.getParameter("mobile");
            String firstName = req.getParameter("firstName");
            String lastName = req.getParameter("lastName");
            String password = req.getParameter("password");
            Part avatarPart = null;
            try {
                avatarPart = req.getPart("avatarImage");
            } catch (Exception ignored) {
            }

            if (mobile == null || mobile.trim().isEmpty()) {
                jsonResponse.addProperty("success", false);
                jsonResponse.addProperty("message", "Mobile number is required");
                resp.getWriter().write(jsonResponse.toString());
                return;
            }
            mobile = mobile.trim();
            if (mobile.length() != 10) {
                jsonResponse.addProperty("success", false);
                jsonResponse.addProperty("message", "Mobile number must be 10 digits");
                resp.getWriter().write(jsonResponse.toString());
                return;
            }

            if (firstName == null || firstName.trim().isEmpty()) {
                jsonResponse.addProperty("success", false);
                jsonResponse.addProperty("message", "First name is required");
                resp.getWriter().write(jsonResponse.toString());
                return;
            }

            if (lastName == null || lastName.trim().isEmpty()) {
                jsonResponse.addProperty("success", false);
                jsonResponse.addProperty("message", "Last name is required");
                resp.getWriter().write(jsonResponse.toString());
                return;
            }

            if (password == null || password.trim().isEmpty() || password.length() < 4) {
                jsonResponse.addProperty("success", false);
                jsonResponse.addProperty("message", "Password must be at least 4 characters");
                resp.getWriter().write(jsonResponse.toString());
                return;
            }

            session = HibernateUtil.getSessionFactory().openSession();

            Criteria dupCheck = session.createCriteria(User.class);
            dupCheck.add(Restrictions.eq("mobile", mobile));
            Object existing = dupCheck.uniqueResult();
            if (existing != null) {
                jsonResponse.addProperty("success", false);
                jsonResponse.addProperty("message", "Mobile number already registered");
                session.close();
                resp.getWriter().write(jsonResponse.toString());
                return;
            }

            UserStatus activeStatus = (UserStatus) session.get(UserStatus.class, 1);
            if (activeStatus == null) {
                jsonResponse.addProperty("success", false);
                jsonResponse.addProperty("message", "Server configuration error");
                session.close();
                resp.getWriter().write(jsonResponse.toString());
                return;
            }

            User newUser = new User();
            newUser.setMobile(mobile);
            newUser.setFirst_name(firstName.trim());
            newUser.setLast_name(lastName.trim());
            newUser.setPassword(password.trim());
            newUser.setRegistered_date_time(new Date());
            newUser.setUser_status(activeStatus);

            tx = session.beginTransaction();
            session.save(newUser);
            tx.commit();

            if (avatarPart != null && avatarPart.getSize() > 0) {
                if (avatarPart.getSize() > MAX_AVATAR_BYTES) {
                    session.close();
                    jsonResponse.addProperty("success", false);
                    jsonResponse.addProperty("message", "Avatar image is too large (max 2MB)");
                    resp.getWriter().write(jsonResponse.toString());
                    return;
                }
                String ct = avatarPart.getContentType();
                if (ct != null && !ct.startsWith("image/")) {
                    session.close();
                    jsonResponse.addProperty("success", false);
                    jsonResponse.addProperty("message", "Avatar must be an image file");
                    resp.getWriter().write(jsonResponse.toString());
                    return;
                }
                saveAvatar(req, mobile, avatarPart);
            }

            jsonResponse.addProperty("success", true);
            jsonResponse.addProperty("message", "Registration complete. Please sign in.");
            session.close();

        } catch (Exception e) {
            e.printStackTrace();
            if (tx != null && tx.isActive()) {
                try {
                    tx.rollback();
                } catch (Exception ignored) {
                }
            }
            if (session != null && session.isOpen()) {
                try {
                    session.close();
                } catch (Exception ignored) {
                }
            }
            jsonResponse = new JsonObject();
            jsonResponse.addProperty("success", false);
            jsonResponse.addProperty("message", "Registration failed. Please try again.");
        }

        resp.getWriter().write(jsonResponse.toString());
    }

    private void saveAvatar(HttpServletRequest req, String mobile, Part avatarPart) throws IOException {
        String fileName = mobile + ".png";

        String realPath = req.getServletContext().getRealPath("/");
        File runtimeDir = new File(realPath);

        File runtimeAvatarDir = new File(runtimeDir, AVATAR_FOLDER);
        if (!runtimeAvatarDir.exists()) {
            runtimeAvatarDir.mkdirs();
        }
        File runtimeTarget = new File(runtimeAvatarDir, fileName);
        try (InputStream in = avatarPart.getInputStream()) {
            Files.copy(in, runtimeTarget.toPath(), StandardCopyOption.REPLACE_EXISTING);
        }

        File projectWebDir = runtimeDir.getParentFile().getParentFile();
        File sourceAvatarDir = new File(projectWebDir, "web\\" + AVATAR_FOLDER);
        if (!sourceAvatarDir.exists()) {
            sourceAvatarDir.mkdirs();
        }
        File sourceTarget = new File(sourceAvatarDir, fileName);
        try (InputStream in2 = avatarPart.getInputStream()) {
            Files.copy(in2, sourceTarget.toPath(), StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
