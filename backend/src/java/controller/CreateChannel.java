package controller;

import com.google.gson.JsonObject;
import entity.Channel;
import entity.ChannelMember;
import entity.ChannelStatus;
import entity.User;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Date;
import java.util.UUID;
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

/**
 * Creates a channel. Now accepts multipart/form-data instead of a raw JSON
 * body so a logo file can be sent in the same request:
 *
 *   name                 (text, required)
 *   description          (text, optional)
 *   created_by_user_id   (text, required)
 *   logo                 (file, optional)
 *
 * Uploaded logos are written under the webapp's own /ChannelLogos folder
 * (same pattern as /AvaterImages for user avatars in SyncContacts.java —
 * resolved via getServletContext().getRealPath so it's always inside the
 * deployed app and therefore reachable at CHANNEL_LOGO_BASE_PATH in api.js).
 */
@WebServlet(name = "CreateChannel", urlPatterns = {"/CreateChannel"})
@MultipartConfig(
        maxFileSize = 5 * 1024 * 1024,       // 5 MB per file
        maxRequestSize = 6 * 1024 * 1024      // 6 MB total request
)
public class CreateChannel extends HttpServlet {

    private static final long MAX_LOGO_BYTES = 5 * 1024 * 1024;

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        resp.setContentType("application/json");
        resp.setCharacterEncoding("UTF-8");

        JsonObject jsonResponse = new JsonObject();
        Session session = null;
        Transaction tx = null;
        String savedLogoFilename = null;

        try {
            String name = getFormField(req, "name");
            String description = getFormField(req, "description");
            String creatorIdRaw = getFormField(req, "created_by_user_id");

            int creatorId = -1;
            try {
                creatorId = creatorIdRaw == null ? -1 : Integer.parseInt(creatorIdRaw.trim());
            } catch (NumberFormatException ignored) {
            }

            if (name == null || name.trim().isEmpty()) {
                jsonResponse.addProperty("success", false);
                jsonResponse.addProperty("message", "Channel name is required");
                resp.getWriter().write(jsonResponse.toString());
                return;
            }
            if (name.trim().length() > 45) {
                jsonResponse.addProperty("success", false);
                jsonResponse.addProperty("message", "Channel name too long (max 45 chars)");
                resp.getWriter().write(jsonResponse.toString());
                return;
            }
            if (creatorId < 1) {
                jsonResponse.addProperty("success", false);
                jsonResponse.addProperty("message", "Invalid creator user");
                resp.getWriter().write(jsonResponse.toString());
                return;
            }

            // Handle optional logo part before touching the DB, so a bad file
            // fails fast without opening a session.
            Part logoPart = req.getPart("logo");
            if (logoPart != null && logoPart.getSize() > 0) {
                if (logoPart.getSize() > MAX_LOGO_BYTES) {
                    jsonResponse.addProperty("success", false);
                    jsonResponse.addProperty("message", "Logo file too large (max 5MB)");
                    resp.getWriter().write(jsonResponse.toString());
                    return;
                }
                String contentType = logoPart.getContentType();
                if (contentType == null || !contentType.startsWith("image/")) {
                    jsonResponse.addProperty("success", false);
                    jsonResponse.addProperty("message", "Logo must be an image file");
                    resp.getWriter().write(jsonResponse.toString());
                    return;
                }
                String ext = extensionFor(contentType);
                savedLogoFilename = UUID.randomUUID().toString() + ext;

                Path uploadDirPath = Paths.get(resolveLogoDir(req));
                Files.createDirectories(uploadDirPath);
                Path target = uploadDirPath.resolve(savedLogoFilename);

                try (InputStream in = logoPart.getInputStream()) {
                    Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
                }
            }

            session = HibernateUtil.getSessionFactory().openSession();

            User creator = (User) session.get(User.class, creatorId);
            if (creator == null) {
                jsonResponse.addProperty("success", false);
                jsonResponse.addProperty("message", "Creator user not found");
                session.close();
                deleteSavedLogoIfAny(req, savedLogoFilename);
                resp.getWriter().write(jsonResponse.toString());
                return;
            }

            ChannelStatus activeStatus = (ChannelStatus) session.get(ChannelStatus.class, 1);
            if (activeStatus == null) {
                jsonResponse.addProperty("success", false);
                jsonResponse.addProperty("message", "Channel status Active not seeded (id=1 missing)");
                session.close();
                deleteSavedLogoIfAny(req, savedLogoFilename);
                resp.getWriter().write(jsonResponse.toString());
                return;
            }

            Channel channel = new Channel();
            channel.setName(name.trim());
            channel.setDescription(description == null ? null : description.trim());
            channel.setLogo(savedLogoFilename);
            channel.setCreated_by_user(creator);
            channel.setCreated_date_time(new Date());
            channel.setChannel_status(activeStatus);

            tx = session.beginTransaction();
            int channelId = (Integer) session.save(channel);

            ChannelMember member = new ChannelMember();
            member.setChannel(channel);
            member.setUser(creator);
            member.setJoined_date_time(new Date());
            member.setIs_admin(true);
            session.save(member);

            tx.commit();
            session.close();

            JsonObject data = new JsonObject();
            data.addProperty("channel_id", channelId);
            data.addProperty("logo", savedLogoFilename);

            jsonResponse.addProperty("success", true);
            jsonResponse.addProperty("message", "Channel created");
            jsonResponse.add("data", data);

        } catch (Exception e) {
            e.printStackTrace();
            if (tx != null && tx.isActive()) {
                try { tx.rollback(); } catch (Exception ignored) {}
            }
            if (session != null && session.isOpen()) {
                try { session.close(); } catch (Exception ignored) {}
            }
            deleteSavedLogoIfAny(req, savedLogoFilename);
            jsonResponse = new JsonObject();
            jsonResponse.addProperty("success", false);
            jsonResponse.addProperty("message", "Failed to create channel");
        }

        resp.getWriter().write(jsonResponse.toString());
    }

    private String getFormField(HttpServletRequest req, String fieldName) throws IOException, ServletException {
        Part part = req.getPart(fieldName);
        if (part == null) {
            return null;
        }
        try (InputStream is = part.getInputStream()) {
            return new String(is.readAllBytes(), "UTF-8");
        }
    }

    private String extensionFor(String contentType) {
        switch (contentType) {
            case "image/png": return ".png";
            case "image/jpeg": return ".jpg";
            case "image/webp": return ".webp";
            case "image/gif": return ".gif";
            default: return "";
        }
    }

    private void deleteSavedLogoIfAny(HttpServletRequest req, String filename) {
        if (filename == null) {
            return;
        }
        try {
            Files.deleteIfExists(Paths.get(resolveLogoDir(req), filename));
        } catch (IOException ignored) {
        }
    }

    // Resolves to <deployed-webapp>/ChannelLogos, same pattern as
    // /AvaterImages in SyncContacts.java, so CHANNEL_LOGO_BASE_PATH in
    // api.js can actually reach these files as static content.
    private String resolveLogoDir(HttpServletRequest req) {
        String realPath = req.getServletContext().getRealPath("/ChannelLogos");
        File dir = new File(realPath);
        if (!dir.exists()) {
            dir.mkdirs();
        }
        return realPath;
    }
}
