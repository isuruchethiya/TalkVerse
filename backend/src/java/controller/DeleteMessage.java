package controller;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import entity.Chat;
import entity.ChatDeletedForUser;
import entity.User;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
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
 * POST body: { "user_id": 1, "chat_id": 57 }
 *
 * Deletes ONE message from user 1's view only.
 *  - The Chat row itself is never removed — the other participant still
 *    sees it normally.
 *  - Validates that user 1 was actually a participant (from_user or
 *    to_user) in that chat before allowing the delete, so one user can't
 *    hide messages from a conversation they're not part of.
 *  - Idempotent — deleting an already-deleted-for-me message is a no-op.
 */
@WebServlet(name = "DeleteMessage", urlPatterns = {"/DeleteMessage"})
public class DeleteMessage extends HttpServlet {

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
            if (body == null || !body.has("user_id") || !body.has("chat_id")) {
                jsonResponse.addProperty("success", false);
                jsonResponse.addProperty("message", "Missing user_id or chat_id");
                resp.getWriter().write(jsonResponse.toString());
                return;
            }

            int userId = body.get("user_id").getAsInt();
            int chatId = body.get("chat_id").getAsInt();

            session = HibernateUtil.getSessionFactory().openSession();

            Chat chat = (Chat) session.get(Chat.class, chatId);
            if (chat == null) {
                session.close();
                jsonResponse.addProperty("success", false);
                jsonResponse.addProperty("message", "Message not found");
                resp.getWriter().write(jsonResponse.toString());
                return;
            }

            boolean isParticipant = chat.getFrom_user().getId() == userId
                    || chat.getTo_user().getId() == userId;
            if (!isParticipant) {
                session.close();
                jsonResponse.addProperty("success", false);
                jsonResponse.addProperty("message", "Not a participant in this conversation");
                resp.getWriter().write(jsonResponse.toString());
                return;
            }

            User user = (User) session.get(User.class, userId);

            Criteria existingCrit = session.createCriteria(ChatDeletedForUser.class);
            existingCrit.add(Restrictions.eq("chat.id", chatId));
            existingCrit.add(Restrictions.eq("user.id", userId));
            ChatDeletedForUser existing = (ChatDeletedForUser) existingCrit.uniqueResult();

            if (existing == null) {
                tx = session.beginTransaction();
                session.save(new ChatDeletedForUser(chat, user));
                tx.commit();
            }

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
