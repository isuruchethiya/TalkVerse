package controller;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import entity.ChannelMember;
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
 * Called when a user opens a channel chat screen. Stamps
 * ChannelMember.last_read_date_time = now, so ListChannels can compute an
 * accurate unread_count (messages from other members sent after this time).
 *
 *   POST /MarkChannelRead   body: { "channel_id": 5, "user_id": 12 }
 */
@WebServlet(name = "MarkChannelRead", urlPatterns = {"/MarkChannelRead"})
public class MarkChannelRead extends HttpServlet {

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

            int channelId = body != null && body.has("channel_id") ? body.get("channel_id").getAsInt() : -1;
            int userId = body != null && body.has("user_id") ? body.get("user_id").getAsInt() : -1;

            if (channelId < 1 || userId < 1) {
                jsonResponse.addProperty("success", false);
                jsonResponse.addProperty("message", "channel_id and user_id are required");
                resp.getWriter().write(jsonResponse.toString());
                return;
            }

            session = HibernateUtil.getSessionFactory().openSession();

            Criteria memberCrit = session.createCriteria(ChannelMember.class);
            memberCrit.add(Restrictions.eq("channel.id", channelId));
            memberCrit.add(Restrictions.eq("user.id", userId));
            @SuppressWarnings("unchecked")
            List<ChannelMember> membership = memberCrit.list();

            if (membership.isEmpty()) {
                session.close();
                jsonResponse.addProperty("success", false);
                jsonResponse.addProperty("message", "Not a member of this channel");
                resp.getWriter().write(jsonResponse.toString());
                return;
            }

            ChannelMember cm = membership.get(0);
            cm.setLast_read_date_time(new Date());

            tx = session.beginTransaction();
            session.update(cm);
            tx.commit();
            session.close();

            jsonResponse.addProperty("success", true);
            jsonResponse.addProperty("message", "Channel marked as read");

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
            jsonResponse.addProperty("message", "Failed to mark channel as read");
        }

        resp.getWriter().write(jsonResponse.toString());
    }
}
