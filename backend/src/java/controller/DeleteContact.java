package controller;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import entity.Contact;
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
 * POST body: { "user_id": 1, "other_user_id": 2 }
 *
 * Deletes ONLY the Contact row (owner_user_id=1, contact_user_id=2).
 *  - Does NOT delete the User account of either party.
 *  - Does NOT touch the reverse direction (if user 2 has user 1 saved,
 *    that Contact row is untouched).
 *  - Does NOT delete any chat messages — if they still have message
 *    history, LoadHomeData's chat-table union means the conversation can
 *    still show up (with the mobile number instead of a name), same as
 *    any other never-saved sender.
 */
@WebServlet(name = "DeleteContact", urlPatterns = {"/DeleteContact"})
public class DeleteContact extends HttpServlet {

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
            tx = session.beginTransaction();

            Criteria crit = session.createCriteria(Contact.class);
            crit.add(Restrictions.eq("ownerUser.id", userId));
            crit.add(Restrictions.eq("contactUser.id", otherUserId));
            Contact existing = (Contact) crit.uniqueResult();

            if (existing != null) {
                session.delete(existing);
            }

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
