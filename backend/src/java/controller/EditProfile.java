package controller;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import entity.User;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import javax.servlet.ServletException;
import javax.servlet.annotation.MultipartConfig;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.Part;
import model.HibernateUtil;
import org.hibernate.Session;
import org.hibernate.Transaction;

@WebServlet(name = "EditProfile", urlPatterns = {"/EditProfile"})
@MultipartConfig(
        fileSizeThreshold = 1024 * 1024 * 2,
        maxFileSize = 1024 * 1024 * 5,
        maxRequestSize = 1024 * 1024 * 10
)
public class EditProfile extends HttpServlet {

    private static final String AVATAR_FOLDER = "AvaterImages";
    private static final long MAX_AVATAR_BYTES = 2L * 1024 * 1024;

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        resp.setContentType("application/json");
        resp.setCharacterEncoding("UTF-8");

        JsonObject jsonResponse = new JsonObject();
        Session session = null;

        try {
            String uidStr = req.getParameter("user_id");
            if (uidStr == null || uidStr.trim().isEmpty()) {
                jsonResponse.addProperty("success", false);
                jsonResponse.addProperty("message", "user_id is required");
                jsonResponse.add("data", null);
                resp.getWriter().write(jsonResponse.toString());
                return;
            }
            int userId = Integer.parseInt(uidStr.trim());

            session = HibernateUtil.getSessionFactory().openSession();

            User user = (User) session.get(User.class, userId);
            if (user == null) {
                jsonResponse.addProperty("success", false);
                jsonResponse.addProperty("message", "User not found");
                jsonResponse.add("data", null);
                session.close();
                resp.getWriter().write(jsonResponse.toString());
                return;
            }

            JsonObject data = new JsonObject();
            data.addProperty("id", user.getId());
            data.addProperty("mobile", user.getMobile());
            data.addProperty("first_name", user.getFirst_name());
            data.addProperty("last_name", user.getLast_name());

            String letters = "";
            String fn = user.getFirst_name();
            String ln = user.getLast_name();
            if (fn != null && !fn.isEmpty() && ln != null && !ln.isEmpty()) {
                letters = fn.substring(0, 1).toUpperCase() + ln.substring(0, 1).toUpperCase();
            } else if (fn != null && !fn.isEmpty()) {
                letters = fn.substring(0, 1).toUpperCase();
            }
            data.addProperty("avatar_letters", letters);

            boolean avatarFound = false;
            try {
                String avatarPath = req.getServletContext().getRealPath("/" + AVATAR_FOLDER + "/" + user.getMobile() + ".png");
                File f = new File(avatarPath);
                avatarFound = f.isFile() && f.length() > 0;
            } catch (Exception ignored) {}
            data.addProperty("avatar_image_found", avatarFound);

            jsonResponse.addProperty("success", true);
            jsonResponse.addProperty("message", "Profile loaded");
            jsonResponse.add("data", data);
            session.close();

        } catch (Exception e) {
            e.printStackTrace();
            if (session != null && session.isOpen()) {
                try { session.close(); } catch (Exception ignored) {}
            }
            jsonResponse = new JsonObject();
            jsonResponse.addProperty("success", false);
            jsonResponse.addProperty("message", "Failed to load profile");
            jsonResponse.add("data", null);
        }

        resp.getWriter().write(jsonResponse.toString());
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        resp.setContentType("application/json");
        resp.setCharacterEncoding("UTF-8");

        JsonObject jsonResponse = new JsonObject();
        Session session = null;
        Transaction tx = null;

        try {
            String userIdStr = req.getParameter("user_id");
            String firstName = req.getParameter("firstName");
            String lastName = req.getParameter("lastName");
            String removeAvatar = req.getParameter("removeAvatar");
            Part avatarPart = null;
            try {
                avatarPart = req.getPart("avatarImage");
            } catch (Exception ignored) {
            }

            if (userIdStr == null || userIdStr.trim().isEmpty()) {
                jsonResponse.addProperty("success", false);
                jsonResponse.addProperty("message", "user_id is required");
                resp.getWriter().write(jsonResponse.toString());
                return;
            }
            int userId = Integer.parseInt(userIdStr.trim());

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

            session = HibernateUtil.getSessionFactory().openSession();

            User user = (User) session.get(User.class, userId);
            if (user == null) {
                jsonResponse.addProperty("success", false);
                jsonResponse.addProperty("message", "User not found");
                session.close();
                resp.getWriter().write(jsonResponse.toString());
                return;
            }

            user.setFirst_name(firstName.trim());
            user.setLast_name(lastName.trim());

            tx = session.beginTransaction();
            session.update(user);
            tx.commit();

            String mobile = user.getMobile();
            boolean avatarFound = false;

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
                avatarFound = true;
            } else if ("1".equals(removeAvatar)) {
                deleteAvatar(req, mobile);
                avatarFound = false;
            } else {
                try {
                    String avatarPath = req.getServletContext().getRealPath("/" + AVATAR_FOLDER + "/" + mobile + ".png");
                    File f = new File(avatarPath);
                    avatarFound = f.isFile() && f.length() > 0;
                } catch (Exception ignored) {}
            }

            JsonObject data = new JsonObject();
            data.addProperty("id", user.getId());
            data.addProperty("first_name", user.getFirst_name());
            data.addProperty("last_name", user.getLast_name());

            String letters = "";
            String fn = user.getFirst_name();
            String ln = user.getLast_name();
            if (fn != null && !fn.isEmpty() && ln != null && !ln.isEmpty()) {
                letters = fn.substring(0, 1).toUpperCase() + ln.substring(0, 1).toUpperCase();
            } else if (fn != null && !fn.isEmpty()) {
                letters = fn.substring(0, 1).toUpperCase();
            }
            data.addProperty("avatar_letters", letters);
            data.addProperty("avatar_image_found", avatarFound);

            jsonResponse.addProperty("success", true);
            jsonResponse.addProperty("message", "Profile updated successfully");
            jsonResponse.add("data", data);
            session.close();

        } catch (Exception e) {
            e.printStackTrace();
            if (tx != null && tx.isActive()) {
                try { tx.rollback(); } catch (Exception ignored) {}
            }
            if (session != null && session.isOpen()) {
                try { session.close(); } catch (Exception ignored) {}
            }
            jsonResponse = new JsonObject();
            jsonResponse.addProperty("success", false);
            jsonResponse.addProperty("message", "Failed to update profile");
            jsonResponse.add("data", null);
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

    private void deleteAvatar(HttpServletRequest req, String mobile) {
        String fileName = mobile + ".png";

        try {
            String realPath = req.getServletContext().getRealPath("/");
            File runtimeDir = new File(realPath);
            File runtimeTarget = new File(new File(runtimeDir, AVATAR_FOLDER), fileName);
            Files.deleteIfExists(runtimeTarget.toPath());
        } catch (Exception ignored) {}

        try {
            String realPath = req.getServletContext().getRealPath("/");
            File runtimeDir = new File(realPath);
            File projectWebDir = runtimeDir.getParentFile().getParentFile();
            File sourceTarget = new File(new File(projectWebDir, "web\\" + AVATAR_FOLDER), fileName);
            Files.deleteIfExists(sourceTarget.toPath());
        } catch (Exception ignored) {}
    }
}
