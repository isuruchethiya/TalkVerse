package controller;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import entity.Channel;
import entity.ChannelMember;
import entity.User;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Date;
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
 * Replaces the old self-join JoinChannel servlet.
 * Channels are invite-only: only an existing admin of the channel can add a
 * new member. Request body:
 * {
 *   "requested_by_user_id": <admin's user id>,
 *   "channel_id": <channel id>,
 *   "user_id": <user id to add>
 * }
 */
@WebServlet(name = "AddChannelMember", urlPatterns = {"/AddChannelMember"})
public class AddChannelMember extends HttpServlet {

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

            session = HibernateUtil.getSessionFactory().openSession();

            Channel channel = (Channel) session.get(Channel.class, channelId);
            if (channel == null) {
                jsonResponse.addProperty("success", false);
                jsonResponse.addProperty("message", "Channel not found");
                session.close();
                resp.getWriter().write(jsonResponse.toString());
                return;
            }

            // requester must be an admin member of this channel
            Criteria adminCrit = session.createCriteria(ChannelMember.class);
            adminCrit.add(Restrictions.eq("channel.id", channelId));
            adminCrit.add(Restrictions.eq("user.id", requestedByUserId));
            adminCrit.add(Restrictions.eq("is_admin", true));
            @SuppressWarnings("unchecked")
            List<ChannelMember> adminMatch = adminCrit.list();
            if (adminMatch.isEmpty()) {
                jsonResponse.addProperty("success", false);
                jsonResponse.addProperty("message", "Only a channel admin can add members");
                session.close();
                resp.getWriter().write(jsonResponse.toString());
                return;
            }

            User user = (User) session.get(User.class, userId);
            if (user == null) {
                jsonResponse.addProperty("success", false);
                jsonResponse.addProperty("message", "User not found");
                session.close();
                resp.getWriter().write(jsonResponse.toString());
                return;
            }

            Criteria dupCrit = session.createCriteria(ChannelMember.class);
            dupCrit.add(Restrictions.eq("channel.id", channelId));
            dupCrit.add(Restrictions.eq("user.id", userId));
            @SuppressWarnings("unchecked")
            List<ChannelMember> existing = dupCrit.list();
            if (!existing.isEmpty()) {
                jsonResponse.addProperty("success", true);
                jsonResponse.addProperty("message", "Already a member");
                session.close();
                resp.getWriter().write(jsonResponse.toString());
                return;
            }

            ChannelMember cm = new ChannelMember();
            cm.setChannel(channel);
            cm.setUser(user);
            cm.setJoined_date_time(new Date());
            cm.setIs_admin(false);

            tx = session.beginTransaction();
            session.save(cm);
            tx.commit();
            session.close();

            jsonResponse.addProperty("success", true);
            jsonResponse.addProperty("message", "Member added");

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
            jsonResponse.addProperty("message", "Failed to add member");
        }

        resp.getWriter().write(jsonResponse.toString());
    }
}
