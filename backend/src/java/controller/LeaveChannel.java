package controller;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import entity.Channel;
import entity.ChannelMember;
import java.io.BufferedReader;
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
 * Lets a member remove THEMSELF from a channel — no admin check, since
 * anyone can leave a channel they're in (unlike RemoveChannelMember, which
 * is for an admin removing someone else).
 *
 * The channel creator can NOT leave through this endpoint — a channel can't
 * be left ownerless. The creator should use DeleteChannel instead if they no
 * longer want the channel.
 *
 * JSON body POST (same convention as AddChannelMember / DeleteChannel):
 *   POST /LeaveChannel
 *   { "channel_id": 5, "user_id": 12 }
 */
@WebServlet(name = "LeaveChannel", urlPatterns = {"/LeaveChannel"})
public class LeaveChannel extends HttpServlet {

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        resp.setContentType("application/json");
        resp.setCharacterEncoding("UTF-8");

        JsonObject jsonResponse = new JsonObject();
        Session session = null;
        Transaction tx = null;

        try {
            JsonObject body = readJsonBody(req);
            int channelId = intField(body, "channel_id");
            int userId = intField(body, "user_id");

            if (channelId < 1 || userId < 1) {
                jsonResponse.addProperty("success", false);
                jsonResponse.addProperty("message", "channel_id and user_id are required");
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

            Integer creatorId = channel.getCreated_by_user() == null ? null : channel.getCreated_by_user().getId();
            if (creatorId != null && creatorId == userId) {
                jsonResponse.addProperty("success", false);
                jsonResponse.addProperty("message", "As the creator you can't leave this channel — delete it instead if you no longer want it");
                session.close();
                resp.getWriter().write(jsonResponse.toString());
                return;
            }

            Criteria memberCrit = session.createCriteria(ChannelMember.class);
            memberCrit.add(Restrictions.eq("channel.id", channelId));
            memberCrit.add(Restrictions.eq("user.id", userId));
            @SuppressWarnings("unchecked")
            List<ChannelMember> match = memberCrit.list();
            if (match.isEmpty()) {
                jsonResponse.addProperty("success", false);
                jsonResponse.addProperty("message", "You are not a member of this channel");
                session.close();
                resp.getWriter().write(jsonResponse.toString());
                return;
            }

            tx = session.beginTransaction();
            session.delete(match.get(0));
            tx.commit();
            session.close();

            jsonResponse.addProperty("success", true);
            jsonResponse.addProperty("message", "Left channel");

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
            jsonResponse.addProperty("message", "Failed to leave channel");
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
