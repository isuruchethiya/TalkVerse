package controller;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import entity.Contact;
import entity.ChannelMember;
import entity.User;
import java.io.File;
import java.io.IOException;
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
import org.hibernate.criterion.Restrictions;

/**
 * Lists a user's saved contacts (entity.Contact rows, populated by
 * SyncContacts.java) — used to power "pick a contact to add" pickers, like
 * the channel-member-add flow, instead of typing a raw user ID.
 *
 *   GET /ListContacts?user_id=12
 *   GET /ListContacts?user_id=12&exclude_channel_id=5   (also drops users
 *        already in that channel, so the picker only shows people who can
 *        actually be added)
 */
@WebServlet(name = "ListContacts", urlPatterns = {"/ListContacts"})
public class ListContacts extends HttpServlet {

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        resp.setContentType("application/json");
        resp.setCharacterEncoding("UTF-8");

        JsonObject jsonResponse = new JsonObject();
        Session session = null;

        try {
            String userIdRaw = req.getParameter("user_id");
            String excludeChannelIdRaw = req.getParameter("exclude_channel_id");

            int userId = -1;
            try {
                userId = userIdRaw == null ? -1 : Integer.parseInt(userIdRaw.trim());
            } catch (NumberFormatException ignored) {
            }

            if (userId < 1) {
                jsonResponse.addProperty("success", false);
                jsonResponse.addProperty("message", "user_id is required");
                jsonResponse.add("data", new JsonArray());
                resp.getWriter().write(jsonResponse.toString());
                return;
            }

            Integer excludeChannelId = null;
            try {
                if (excludeChannelIdRaw != null && !excludeChannelIdRaw.trim().isEmpty()) {
                    excludeChannelId = Integer.parseInt(excludeChannelIdRaw.trim());
                }
            } catch (NumberFormatException ignored) {
            }

            session = HibernateUtil.getSessionFactory().openSession();

            Criteria contactCrit = session.createCriteria(Contact.class);
            contactCrit.add(Restrictions.eq("ownerUser.id", userId));
            @SuppressWarnings("unchecked")
            List<Contact> myContacts = contactCrit.list();

            // Already-members-of-this-channel, so the picker doesn't offer people twice.
            Set<Integer> alreadyInChannel = new HashSet<>();
            if (excludeChannelId != null) {
                Criteria memberCrit = session.createCriteria(ChannelMember.class);
                memberCrit.add(Restrictions.eq("channel.id", excludeChannelId));
                @SuppressWarnings("unchecked")
                List<ChannelMember> existingMembers = memberCrit.list();
                for (ChannelMember cm : existingMembers) {
                    if (cm.getUser() != null) {
                        alreadyInChannel.add(cm.getUser().getId());
                    }
                }
            }

            JsonArray dataArray = new JsonArray();
            for (Contact c : myContacts) {
                User cu = c.getContactUser();
                if (cu == null) continue;
                if (alreadyInChannel.contains(cu.getId())) continue;

                JsonObject row = new JsonObject();
                row.addProperty("user_id", cu.getId());
                row.addProperty("mobile", safeMobile(cu));

                String displayName = c.getSavedName();
                if (displayName == null || displayName.trim().isEmpty()) {
                    displayName = displayName(cu);
                }
                row.addProperty("name", displayName);

                String letters = initials(cu);
                row.addProperty("avatar_letters", letters);

                boolean avatarFound = false;
                try {
                    String avatarPath = req.getServletContext()
                            .getRealPath("/AvaterImages/" + cu.getMobile() + ".png");
                    File f = new File(avatarPath);
                    avatarFound = f.isFile() && f.length() > 0;
                } catch (Exception ignored) {
                }
                row.addProperty("avatar_image_found", avatarFound);

                dataArray.add(row);
            }

            jsonResponse.addProperty("success", true);
            jsonResponse.addProperty("message", "Contacts loaded");
            jsonResponse.add("data", dataArray);
            session.close();

        } catch (Exception e) {
            e.printStackTrace();
            if (session != null && session.isOpen()) {
                try { session.close(); } catch (Exception ignored) {}
            }
            jsonResponse = new JsonObject();
            jsonResponse.addProperty("success", false);
            jsonResponse.addProperty("message", "Failed to load contacts");
            jsonResponse.add("data", new JsonArray());
        }

        resp.getWriter().write(jsonResponse.toString());
    }

    // ⚠️ Same getter-name caveat as ListChannelMembers.java — adjust if your
    // User entity's getters aren't getFirst_name()/getLast_name()/getMobile().
    private String displayName(User u) {
        try {
            String first = u.getFirst_name();
            String last = u.getLast_name();
            String full = ((first == null ? "" : first) + " " + (last == null ? "" : last)).trim();
            return full.isEmpty() ? ("User #" + u.getId()) : full;
        } catch (Exception e) {
            return "User #" + u.getId();
        }
    }

    private String initials(User u) {
        try {
            String fn = u.getFirst_name() == null ? "" : u.getFirst_name().trim();
            String ln = u.getLast_name() == null ? "" : u.getLast_name().trim();
            if (!fn.isEmpty() && !ln.isEmpty()) {
                return fn.substring(0, 1).toUpperCase() + ln.substring(0, 1).toUpperCase();
            } else if (!fn.isEmpty()) {
                return fn.substring(0, 1).toUpperCase();
            }
            return "?";
        } catch (Exception e) {
            return "?";
        }
    }

    private String safeMobile(User u) {
        try {
            String mobile = u.getMobile();
            return mobile == null ? "" : mobile;
        } catch (Exception e) {
            return "";
        }
    }
}
