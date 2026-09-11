package controller;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import entity.Channel;
import entity.ChannelMember;
import entity.ChannelMessage; // ⚠️ adjust this class name if your channel-messages entity is named differently
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.util.List;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import model.HibernateUtil;
import org.hibernate.Criteria;
import org.hibernate.Session;
import org.hibernate.Transaction;
import org.hibernate.criterion.Restrictions;

/**
 * Deletes a channel entirely — CREATOR ONLY. Appointed admins (ChannelMember
 * .is_admin = true) can edit the channel, add/remove members, etc, but they
 * cannot delete it — only the user in Channel.created_by_user can.
 *
 * JSON body POST (same calling convention as AddChannelMember /
 * RemoveChannelMember / SendChannelMessage — fetch() with
 * Content-Type: application/json):
 *
 *   POST /DeleteChannel
 *   { "channel_id": 5, "requested_by_user_id": 12 }
 */
@WebServlet(name = "DeleteChannel", urlPatterns = {"/DeleteChannel"})
public class DeleteChannel extends HttpServlet {

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        resp.setContentType("application/json");
        resp.setCharacterEncoding("UTF-8");

        JsonObject jsonResponse = new JsonObject();
        Session session = null;
        Transaction tx = null;
        String logoFilenameToDelete = null;

        try {
            JsonObject body = readJsonBody(req);
            int channelId = intField(body, "channel_id");
            int requestedByUserId = intField(body, "requested_by_user_id");

            if (channelId < 1 || requestedByUserId < 1) {
                jsonResponse.addProperty("success", false);
                jsonResponse.addProperty("message", "channel_id and requested_by_user_id are required");
                resp.getWriter().write(jsonResponse.toString());
                return;
            }

            session = HibernateUtil.getSessionFactory().openSession();

            Channel channel = (Channel) session.get(Channel.class, channelId);
            if (channel == null) {
                jsonResponse.addProperty("success", false);
                jsonResponse.addProperty("message", "Channel not found");
                session.close();
                resp.getWriter().write(jsonResponse.toString());
                return;
            }

            // Creator-only check — being an appointed admin is NOT enough here.
            Integer creatorId = channel.getCreated_by_user() == null ? null : channel.getCreated_by_user().getId();
            if (creatorId == null || creatorId != requestedByUserId) {
                jsonResponse.addProperty("success", false);
                jsonResponse.addProperty("message", "Only the channel creator can delete this channel");
                session.close();
                resp.getWriter().write(jsonResponse.toString());
                return;
            }

            logoFilenameToDelete = channel.getLogo();

            tx = session.beginTransaction();

            // Delete dependent rows first so the FK constraints don't block
            // deleting the channel itself.
            Criteria messageCrit = session.createCriteria(ChannelMessage.class);
            messageCrit.add(Restrictions.eq("channel.id", channelId));
            @SuppressWarnings("unchecked")
            List<ChannelMessage> messages = messageCrit.list();
            for (ChannelMessage m : messages) {
                session.delete(m);
            }

            Criteria memberCrit = session.createCriteria(ChannelMember.class);
            memberCrit.add(Restrictions.eq("channel.id", channelId));
            @SuppressWarnings("unchecked")
            List<ChannelMember> members = memberCrit.list();
            for (ChannelMember cm : members) {
                session.delete(cm);
            }

            session.delete(channel);
            tx.commit();
            session.close();

            // Best-effort cleanup of the logo file — channel row is already
            // gone at this point, so a failure here shouldn't fail the request.
            if (logoFilenameToDelete != null) {
                try {
                    String realPath = req.getServletContext().getRealPath("/ChannelLogos/" + logoFilenameToDelete);
                    if (realPath != null) {
                        new File(realPath).delete();
                    }
                } catch (Exception ignored) {
                }
            }

            jsonResponse.addProperty("success", true);
            jsonResponse.addProperty("message", "Channel deleted");

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
            jsonResponse.addProperty("message", "Failed to delete channel");
        }

        resp.getWriter().write(jsonResponse.toString());
    }

    private JsonObject readJsonBody(HttpServletRequest req) throws IOException {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = req.getReader()) {
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
        }
        try {
            JsonObject parsed = new Gson().fromJson(sb.toString(), JsonObject.class);
            return parsed != null ? parsed : new JsonObject();
        } catch (Exception e) {
            return new JsonObject();
        }
    }

    private int intField(JsonObject body, String key) {
        try {
            if (body == null || !body.has(key) || body.get(key).isJsonNull()) return -1;
            return body.get(key).getAsInt();
        } catch (Exception e) {
            return -1;
        }
    }
}
