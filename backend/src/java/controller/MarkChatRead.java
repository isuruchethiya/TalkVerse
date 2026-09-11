package controller;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import entity.Chat;
import entity.ChatStatus;
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
 * POST body: { "user_id": 1, "other_user_id": 2 }
 *
 * Marks every message FROM other_user_id TO user_id as read. Call this
 * when a chat screen opens (or comes to the foreground) so the unread
 * badge on the home screen clears in real time on the next LoadHomeData
 * poll, instead of staying stuck at the old count.
 */
@WebServlet(name = "MarkChatRead", urlPatterns = {"/MarkChatRead"})
public class MarkChatRead extends HttpServlet {

    // chat_status: id 1 = "Seen" (read), id 2 = "Sent" (unread),
    // id 3 = "Delivered" (unused). Confirmed via chat_status table.
    private static final int READ_STATUS_ID = 1;

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {

        resp.setContentType("application/json");
        resp.setCharacterEncoding("UTF-8");
        JsonObject jsonResponse = new JsonObject();
        Session session = null;
        Transaction tx = null;

        try {
            StringBuilder sb = new StringBuilder();
            BufferedReader reader = new BufferedReader(new InputStreamReader(req.getInputStream()));
            String line;
            while ((line = reader.readLine()) != null) sb.append(line);

            JsonObject body = new Gson().fromJson(sb.toString(), JsonObject.class);
            if (body == null || !body.has("user_id") || !body.has("other_user_id")) {
                jsonResponse.addProperty("success", false);
                jsonResponse.addProperty("message", "Missing user_id or other_user_id");
                resp.getWriter().write(jsonResponse.toString());
                return;
            }

            int userId = body.get("user_id").getAsInt();
            int otherUserId = body.get("other_user_id").getAsInt();

            session = HibernateUtil.getSessionFactory().openSession();

            ChatStatus readStatus = (ChatStatus) session.get(ChatStatus.class, READ_STATUS_ID);
            if (readStatus == null) {
                session.close();
                jsonResponse.addProperty("success", false);
                jsonResponse.addProperty("message", "READ_STATUS_ID not found in chat_status table");
                resp.getWriter().write(jsonResponse.toString());
                return;
            }

            tx = session.beginTransaction();

            Criteria crit = session.createCriteria(Chat.class);
            crit.add(Restrictions.eq("from_user.id", otherUserId));
            crit.add(Restrictions.eq("to_user.id", userId));
            @SuppressWarnings("unchecked")
            List<Chat> unreadChats = crit.list();

            int updated = 0;
            for (Chat c : unreadChats) {
                if (c.getChat_status() == null || c.getChat_status().getId() != READ_STATUS_ID) {
                    c.setChat_status(readStatus);
                    session.update(c);
                    updated++;
                }
            }

            tx.commit();
            session.close();

            jsonResponse.addProperty("success", true);
            jsonResponse.addProperty("updated_count", updated);

        } catch (Exception e) {
            e.printStackTrace();
            if (tx != null && tx.isActive()) { try { tx.rollback(); } catch (Exception ignored) {} }
            if (session != null && session.isOpen()) { try { session.close(); } catch (Exception ignored) {} }
            jsonResponse = new JsonObject();
            jsonResponse.addProperty("success", false);
            jsonResponse.addProperty("message", "Server error");
        }

        resp.getWriter().write(jsonResponse.toString());
    }
}
