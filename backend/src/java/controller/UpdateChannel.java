package controller;

import com.google.gson.JsonObject;
import entity.Channel;
import entity.ChannelMember;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.UUID;
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

/**
 * Updates a channel's name / description / logo. Admin-only — checked via
 * ChannelMember.is_admin, same as SetChannelAdmin / RemoveChannelMember.
 *
 * multipart/form-data, mirrors CreateChannel.java exactly:
 *   POST /UpdateChannel
 *     channel_id              (text, required)
 *     requested_by_user_id    (text, required)
 *     name                    (text, optional — only replaces if non-blank)
 *     description             (text, optional — sent even if blank, to allow clearing it)
 *     logo                    (file, optional — only replaces if present)
 *
 * Same logo folder, same field-reading approach (Part-based, not
 * req.getParameter — matches CreateChannel.java's getFormField), same logo
 * size/content-type validation and extension mapping.
 *
 * Logos are written under the webapp's own /ChannelLogos folder (same
 * pattern as /AvaterImages for user avatars in SyncContacts.java, and same
 * fix now applied to CreateChannel.java) — resolved via
 * getServletContext().getRealPath so it's always inside the deployed app
 * and therefore reachable at CHANNEL_LOGO_BASE_PATH in api.js.
 */
@WebServlet(name = "UpdateChannel", urlPatterns = {"/UpdateChannel"})
@MultipartConfig(
        maxFileSize = 5 * 1024 * 1024,       // 5 MB per file — same as CreateChannel.java
        maxRequestSize = 6 * 1024 * 1024      // 6 MB total request — same as CreateChannel.java
)
public class UpdateChannel extends HttpServlet {

    private static final long MAX_LOGO_BYTES = 5 * 1024 * 1024;

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        resp.setContentType("application/json");
        resp.setCharacterEncoding("UTF-8");

        JsonObject jsonResponse = new JsonObject();
        Session session = null;
        Transaction tx = null;
        String savedLogoFilename = null;
        String oldLogoFilenameToDelete = null;

        try {
            String channelIdRaw = getFormField(req, "channel_id");
            String requestedByRaw = getFormField(req, "requested_by_user_id");
            String newName = getFormField(req, "name");
            String newDescription = getFormField(req, "description");

            int channelId = -1;
            int requestedByUserId = -1;
            try {
                channelId = channelIdRaw == null ? -1 : Integer.parseInt(channelIdRaw.trim());
                requestedByUserId = requestedByRaw == null ? -1 : Integer.parseInt(requestedByRaw.trim());
            } catch (NumberFormatException ignored) {
            }

            if (channelId < 1 || requestedByUserId < 1) {
                jsonResponse.addProperty("success", false);
                jsonResponse.addProperty("message", "channel_id and requested_by_user_id are required");
                resp.getWriter().write(jsonResponse.toString());
                return;
            }
            if (newName != null && newName.trim().length() > 45) {
                jsonResponse.addProperty("success", false);
                jsonResponse.addProperty("message", "Channel name too long (max 45 chars)");
                resp.getWriter().write(jsonResponse.toString());
                return;
            }

            // Handle optional logo part before touching the DB, same as CreateChannel.java —
            // fail fast on a bad file without opening a session or deleting the old logo yet.
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

            // Requester must be an admin of this channel.
            Criteria adminCrit = session.createCriteria(ChannelMember.class);
            adminCrit.add(Restrictions.eq("channel.id", channelId));
            adminCrit.add(Restrictions.eq("user.id", requestedByUserId));
            adminCrit.add(Restrictions.eq("is_admin", true));
            @SuppressWarnings("unchecked")
            List<ChannelMember> adminMatch = adminCrit.list();
            if (adminMatch.isEmpty()) {
                jsonResponse.addProperty("success", false);
                jsonResponse.addProperty("message", "Only channel admins can update channel details");
                session.close();
                deleteSavedLogoIfAny(req, savedLogoFilename);
                resp.getWriter().write(jsonResponse.toString());
                return;
            }

            Channel channel = (Channel) session.get(Channel.class, channelId);
            if (channel == null) {
                jsonResponse.addProperty("success", false);
                jsonResponse.addProperty("message", "Channel not found");
                session.close();
                deleteSavedLogoIfAny(req, savedLogoFilename);
                resp.getWriter().write(jsonResponse.toString());
                return;
            }

            if (newName != null && !newName.trim().isEmpty()) {
                channel.setName(newName.trim());
            }
            if (newDescription != null) {
                // allow clearing the description with an empty string
                channel.setDescription(newDescription.trim());
            }
            if (savedLogoFilename != null) {
                oldLogoFilenameToDelete = channel.getLogo();
                channel.setLogo(savedLogoFilename);
            }

            tx = session.beginTransaction();
            session.update(channel);
            tx.commit();
            session.close();

            // Only remove the old logo file after the DB update committed successfully.
            deleteSavedLogoIfAny(req, oldLogoFilenameToDelete);

            JsonObject data = new JsonObject();
            data.addProperty("channel_id", channel.getId());
            data.addProperty("name", channel.getName());
            data.addProperty("description", channel.getDescription());
            data.addProperty("logo", channel.getLogo());

            jsonResponse.addProperty("success", true);
            jsonResponse.addProperty("message", "Channel updated");
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
            jsonResponse.addProperty("message", "Failed to update channel");
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
    // /AvaterImages in SyncContacts.java.
    private String resolveLogoDir(HttpServletRequest req) {
        String realPath = req.getServletContext().getRealPath("/ChannelLogos");
        File dir = new File(realPath);
        if (!dir.exists()) {
            dir.mkdirs();
        }
        return realPath;
    }
}
