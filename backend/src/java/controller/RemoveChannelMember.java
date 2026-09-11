package controller;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import entity.ChannelMember;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
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
 * Admin-only member removal. Request body:
 * {
 *   "requested_by_user_id": <admin's user id>,
 *   "channel_id": <channel id>,
 *   "user_id": <user id to remove>
 * }
 * An admin cannot remove themself through this endpoint (avoids leaving a
 * channel with zero admins by accident) - they'd need to promote someone
 * else first, or a separate "leave channel" flow.
 */
@WebServlet(name = "RemoveChannelMember", urlPatterns = {"/RemoveChannelMember"})
public class RemoveChannelMember extends HttpServlet {

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        resp.setContentType("application/json");
        resp.setCharacterEncoding("UTF-8");

        JsonObject jsonResponse = new JsonObject();
        Gson gson = new Gson();
        Session session = null;
        Transaction tx = null;

        try {
            StringBuilder sb = new StringBuilder();
            BufferedReader reader = new BufferedReader(new InputStreamReader(req.getInputStream()));
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
            JsonObject body = gson.fromJson(sb.toString(), JsonObject.class);

            int requestedByUserId = body.has("requested_by_user_id") ? body.get("requested_by_user_id").getAsInt() : -1;
            int channelId = body.has("channel_id") ? body.get("channel_id").getAsInt() : -1;
            int userId = body.has("user_id") ? body.get("user_id").getAsInt() : -1;

            if (requestedByUserId < 1 || channelId < 1 || userId < 1) {
                jsonResponse.addProperty("success", false);
                jsonResponse.addProperty("message", "Invalid requested_by_user_id, channel_id or user_id");
                resp.getWriter().write(jsonResponse.toString());
                return;
            }

            if (requestedByUserId == userId) {
                jsonResponse.addProperty("success", false);
                jsonResponse.addProperty("message", "Admins cannot remove themselves via this endpoint");
                resp.getWriter().write(jsonResponse.toString());
                return;
            }

            session = HibernateUtil.getSessionFactory().openSession();

            Criteria adminCrit = session.createCriteria(ChannelMember.class);
            adminCrit.add(Restrictions.eq("channel.id", channelId));
            adminCrit.add(Restrictions.eq("user.id", requestedByUserId));
            adminCrit.add(Restrictions.eq("is_admin", true));
            @SuppressWarnings("unchecked")
            List<ChannelMember> adminMatch = adminCrit.list();
            if (adminMatch.isEmpty()) {
                jsonResponse.addProperty("success", false);
                jsonResponse.addProperty("message", "Only a channel admin can remove members");
                session.close();
                resp.getWriter().write(jsonResponse.toString());
                return;
            }

            Criteria targetCrit = session.createCriteria(ChannelMember.class);
            targetCrit.add(Restrictions.eq("channel.id", channelId));
            targetCrit.add(Restrictions.eq("user.id", userId));
            @SuppressWarnings("unchecked")
            List<ChannelMember> targetMatch = targetCrit.list();
            if (targetMatch.isEmpty()) {
                jsonResponse.addProperty("success", false);
                jsonResponse.addProperty("message", "User is not a member of this channel");
                session.close();
                resp.getWriter().write(jsonResponse.toString());
                return;
            }

            tx = session.beginTransaction();
            session.delete(targetMatch.get(0));
            tx.commit();
            session.close();

            jsonResponse.addProperty("success", true);
            jsonResponse.addProperty("message", "Member removed");

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
            jsonResponse.addProperty("message", "Failed to remove member");
        }

        resp.getWriter().write(jsonResponse.toString());
    }
}
