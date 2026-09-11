package controller;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import entity.ChatHidden;
import entity.User;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Date;
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
 * Deletes conversation (1 <-> 2) from user 1's view ONLY, as of now.
 *  - User 2's copy of the conversation is completely untouched.
 *  - No Chat rows are deleted — just a "hidden as of this timestamp"
 *    marker for user 1. LoadHomeData / LoadChat must both filter out
 *    messages with date_time <= hiddenAt for this pair when serving
 *    user 1.
 *  - If user 2 later sends a NEW message, it has date_time > hiddenAt,
 *    so it's visible again and the conversation naturally reappears in
 *    user 1's chat list — same behavior as WhatsApp's "Delete chat".
 *
 * Idempotent: calling again just moves hiddenAt forward to now. Uses an
 * atomic MySQL upsert (INSERT ... ON DUPLICATE KEY UPDATE) rather than a
 * select-then-insert/update pattern, so two rapid taps (or any concurrent
 * requests) can never race into a duplicate-key error — MySQL serializes
 * the upsert itself.
 */
@WebServlet(name = "DeleteChat", urlPatterns = {"/DeleteChat"})
public class DeleteChat extends HttpServlet {

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

            User owner = (User) session.get(User.class, userId);
            User other = (User) session.get(User.class, otherUserId);
            if (owner == null || other == null) {
                session.close();
                jsonResponse.addProperty("success", false);
                jsonResponse.addProperty("message", "User not found");
                resp.getWriter().write(jsonResponse.toString());
                return;
            }

            tx = session.beginTransaction();

            // MAX(id) among this pair's existing messages becomes the
            // cutoff — anything with a HIGHER id is a message sent after
            // this delete action and stays visible. Using the chat table's
            // auto-increment id instead of a timestamp avoids any
            // same-second tie ambiguity between hidden_at and a message's
            // date_time (which only has second-level precision).
            session.createSQLQuery(
                "INSERT INTO chat_hidden (owner_user_id, other_user_id, hidden_at, hidden_before_chat_id) " +
                "SELECT :ownerId, :otherId, NOW(), COALESCE(MAX(id), 0) " +
                "FROM chat WHERE (from_user_id = :ownerId AND to_user_id = :otherId) " +
                "   OR (from_user_id = :otherId AND to_user_id = :ownerId) " +
                "ON DUPLICATE KEY UPDATE hidden_at = NOW(), hidden_before_chat_id = VALUES(hidden_before_chat_id)"
            )
            .setParameter("ownerId", userId)
            .setParameter("otherId", otherUserId)
            .executeUpdate();

            tx.commit();
            session.close();

            jsonResponse.addProperty("success", true);

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
