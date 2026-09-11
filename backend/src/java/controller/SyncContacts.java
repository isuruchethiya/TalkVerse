package controller;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import entity.Contact;
import entity.User;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
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

@WebServlet(name = "SyncContacts", urlPatterns = {"/SyncContacts"})
public class SyncContacts extends HttpServlet {

    /**
     * POST body:
     * {
     *   "user_id": 1,
     *   "mobiles": ["0774733386", "0741555382", ...]
     * }
     *
     * Behavior:
     *  - Finds registered users whose mobile is in the posted list (excluding self).
     *  - Upserts a Contact row (owner_user_id = user_id, contact_user_id = match)
     *    for every match that doesn't already exist.
     *  - Removes Contact rows for this owner that are no longer present in the
     *    posted mobiles list (number deleted / unsaved from phone).
     *  - Returns the resulting contact list.
     *
     * Response:
     * {
     *   "success": true,
     *   "contacts": [
     *     {
     *       "other_user_id": 2,
     *       "other_user_mobile": "0741555382",
     *       "other_user_name": "Sunil Perera",
     *       "other_user_avatar_letters": "SP",
     *       "other_user_status": 1,
     *       "avatar_image_found": false
     *     }, ...
     *   ]
     * }
     */
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
            // Read request body
            StringBuilder sb = new StringBuilder();
            BufferedReader reader = new BufferedReader(new InputStreamReader(req.getInputStream()));
            String line;
            while ((line = reader.readLine()) != null) sb.append(line);

            JsonObject body = gson.fromJson(sb.toString(), JsonObject.class);
            if (body == null) {
                jsonResponse.addProperty("success", false);
                jsonResponse.addProperty("message", "Invalid request");
                resp.getWriter().write(jsonResponse.toString());
                return;
            }

            int loggedUserId = body.has("user_id") ? body.get("user_id").getAsInt() : -1;
            JsonArray mobilesArray = body.has("mobiles") ? body.getAsJsonArray("mobiles") : new JsonArray();

            if (loggedUserId < 0 || mobilesArray.size() == 0) {
                jsonResponse.addProperty("success", false);
                jsonResponse.addProperty("message", "Missing user_id or mobiles");
                resp.getWriter().write(jsonResponse.toString());
                return;
            }

            // Build list of mobile strings to query
            List<String> mobileList = new ArrayList<>();
            for (JsonElement el : mobilesArray) {
                String m = el.getAsString().trim();
                if (!m.isEmpty()) mobileList.add(m);
            }

            session = HibernateUtil.getSessionFactory().openSession();

            User owner = (User) session.get(User.class, loggedUserId);
            if (owner == null) {
                session.close();
                jsonResponse.addProperty("success", false);
                jsonResponse.addProperty("message", "User not found");
                resp.getWriter().write(jsonResponse.toString());
                return;
            }

            // Find all registered users whose mobile is in the provided list, excluding self
            Criteria criteria = session.createCriteria(User.class);
            criteria.add(Restrictions.in("mobile", mobileList));
            criteria.add(Restrictions.ne("id", loggedUserId));

            @SuppressWarnings("unchecked")
            List<User> matchedUsers = criteria.list();

            // Existing contact rows for this owner
            Criteria existingCrit = session.createCriteria(Contact.class);
            existingCrit.add(Restrictions.eq("ownerUser.id", loggedUserId));
            @SuppressWarnings("unchecked")
            List<Contact> existingContacts = existingCrit.list();

            Set<Integer> existingContactUserIds = new HashSet<>();
            for (Contact c : existingContacts) {
                existingContactUserIds.add(c.getContactUser().getId());
            }

            Set<Integer> matchedUserIds = new HashSet<>();
            for (User u : matchedUsers) {
                matchedUserIds.add(u.getId());
            }

            tx = session.beginTransaction();

            // Insert new matches that aren't already saved as contacts
            for (User u : matchedUsers) {
                if (!existingContactUserIds.contains(u.getId())) {
                    Contact c = new Contact(owner, u, null);
                    session.save(c);
                }
            }

            // Remove contacts that are no longer present in the phone's mobile list
            // (the previously-matched user's number was deleted/unsaved on-device)
            for (Contact c : existingContacts) {
                if (!matchedUserIds.contains(c.getContactUser().getId())) {
                    session.delete(c);
                }
            }

            tx.commit();

            JsonArray contactsArray = new JsonArray();
            for (User u : matchedUsers) {
                JsonObject row = new JsonObject();
                row.addProperty("other_user_id", u.getId());
                row.addProperty("other_user_mobile", u.getMobile());

                String fn = u.getFirst_name() == null ? "" : u.getFirst_name().trim();
                String ln = u.getLast_name() == null ? "" : u.getLast_name().trim();
                String fullName = (fn + " " + ln).trim();
                row.addProperty("other_user_name", fullName);

                // Avatar initials
                String letters = "";
                if (!fn.isEmpty() && !ln.isEmpty()) {
                    letters = fn.substring(0, 1).toUpperCase() + ln.substring(0, 1).toUpperCase();
                } else if (!fn.isEmpty()) {
                    letters = fn.substring(0, 1).toUpperCase();
                }
                row.addProperty("other_user_avatar_letters", letters);

                row.addProperty("other_user_status",
                        u.getUser_status() != null ? u.getUser_status().getId() : 0);

                // Check if avatar image exists on server
                boolean avatarFound = false;
                try {
                    String avatarPath = req.getServletContext()
                            .getRealPath("/AvaterImages/" + u.getMobile() + ".png");
                    File f = new File(avatarPath);
                    avatarFound = f.isFile() && f.length() > 0;
                } catch (Exception ignored) {}
                row.addProperty("avatar_image_found", avatarFound);

                contactsArray.add(row);
            }

            session.close();

            jsonResponse.addProperty("success", true);
            jsonResponse.add("contacts", contactsArray);

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
