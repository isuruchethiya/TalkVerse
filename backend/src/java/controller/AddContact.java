package controller;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import entity.Contact;
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
 * Adds (or confirms) a single contact relationship instantly.
 *
 * This exists alongside SyncContacts because SyncContacts reads and posts
 * the ENTIRE phone contact list (needed so it can also remove stale
 * contacts) — that's fine for a periodic background sync, but too slow to
 * run synchronously right after the in-app "Save" button, especially on
 * phones with large address books.
 *
 * AddContact only touches the one row being added, so it's near-instant.
 * It does NOT remove anything, so it's safe to call any time without
 * affecting other contacts. A full SyncContacts can still run later
 * (app open / periodic) to catch removals.
 *
 * POST body:
 * {
 *   "user_id": 1,
 *   "mobile": "0741555382"
 * }
 *
 * Response:
 * {
 *   "success": true,
 *   "other_user_id": 2,
 *   "other_user_name": "Sunil Chethiya"
 * }
 * or, if the mobile isn't a registered TalkVerse user:
 * {
 *   "success": false,
 *   "message": "No TalkVerse user with that number"
 * }
 */
@WebServlet(name = "AddContact", urlPatterns = {"/AddContact"})
public class AddContact extends HttpServlet {

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {

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
            while ((line = reader.readLine()) != null) sb.append(line);

            JsonObject body = gson.fromJson(sb.toString(), JsonObject.class);
            if (body == null || !body.has("user_id") || !body.has("mobile")) {
                jsonResponse.addProperty("success", false);
                jsonResponse.addProperty("message", "Missing user_id or mobile");
                resp.getWriter().write(jsonResponse.toString());
                return;
            }

            int loggedUserId = body.get("user_id").getAsInt();
            String mobile = body.get("mobile").getAsString().trim();

            session = HibernateUtil.getSessionFactory().openSession();

            User owner = (User) session.get(User.class, loggedUserId);
            if (owner == null) {
                session.close();
                jsonResponse.addProperty("success", false);
                jsonResponse.addProperty("message", "User not found");
                resp.getWriter().write(jsonResponse.toString());
                return;
            }

            // Find the registered user with this mobile number
            Criteria matchCrit = session.createCriteria(User.class);
            matchCrit.add(Restrictions.eq("mobile", mobile));
            matchCrit.add(Restrictions.ne("id", loggedUserId));
            User matchedUser = (User) matchCrit.uniqueResult();

            if (matchedUser == null) {
                session.close();
                jsonResponse.addProperty("success", false);
                jsonResponse.addProperty("message", "No TalkVerse user with that number");
                resp.getWriter().write(jsonResponse.toString());
                return;
            }

            // Already saved? Then this is just a confirmation, nothing to insert.
            Criteria existingCrit = session.createCriteria(Contact.class);
            existingCrit.add(Restrictions.eq("ownerUser.id", loggedUserId));
            existingCrit.add(Restrictions.eq("contactUser.id", matchedUser.getId()));
            Contact existing = (Contact) existingCrit.uniqueResult();

            if (existing == null) {
                tx = session.beginTransaction();
                Contact c = new Contact(owner, matchedUser, null);
                session.save(c);
                tx.commit();
            }

            session.close();

            String fn = matchedUser.getFirst_name() == null ? "" : matchedUser.getFirst_name().trim();
            String ln = matchedUser.getLast_name() == null ? "" : matchedUser.getLast_name().trim();

            jsonResponse.addProperty("success", true);
            jsonResponse.addProperty("other_user_id", matchedUser.getId());
            jsonResponse.addProperty("other_user_name", (fn + " " + ln).trim());

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
            jsonResponse.addProperty("message", "Server error");
        }

        resp.getWriter().write(jsonResponse.toString());
    }
}
