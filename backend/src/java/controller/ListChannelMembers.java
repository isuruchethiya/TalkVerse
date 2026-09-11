package controller;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import entity.Channel;
import entity.ChannelMember;
import entity.User;
import java.io.File;
import java.io.IOException;
import java.util.List;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import model.HibernateUtil;
import org.hibernate.Criteria;
import org.hibernate.Session;
import org.hibernate.criterion.Order;
import org.hibernate.criterion.Restrictions;

/**
 * Lists the members of a channel (any current member can call this - not
 * admin-only, since everyone in the channel should be able to see who else
 * is in it).
 *
 *   GET /ListChannelMembers?channel_id=5&requested_by_user_id=12
 */
@WebServlet(name = "ListChannelMembers", urlPatterns = {"/ListChannelMembers"})
public class ListChannelMembers extends HttpServlet {

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        resp.setContentType("application/json");
        resp.setCharacterEncoding("UTF-8");

        JsonObject jsonResponse = new JsonObject();
        Session session = null;

        try {
            String channelIdRaw = req.getParameter("channel_id");
            String requestedByRaw = req.getParameter("requested_by_user_id");

            int channelId = -1;
            int requestedByUserId = -1;
            try {
                channelId = channelIdRaw == null ? -1 : Integer.parseInt(channelIdRaw.trim());
                requestedByUserId = requestedByRaw == null ? -1 : Integer.parseInt(requestedByRaw.trim());
            } catch (NumberFormatException ignored) {
            }

            if (channelId < 1 || requestedByUserId < 1) {
                jsonResponse.addProperty("success", false);
                jsonResponse.addProperty("message", "channel_id and requested_by_user_id are required");
                jsonResponse.add("data", new JsonArray());
                resp.getWriter().write(jsonResponse.toString());
                return;
            }

            session = HibernateUtil.getSessionFactory().openSession();

            // requester must themself be a member to see the member list
            Criteria requesterCrit = session.createCriteria(ChannelMember.class);
            requesterCrit.add(Restrictions.eq("channel.id", channelId));
            requesterCrit.add(Restrictions.eq("user.id", requestedByUserId));
            @SuppressWarnings("unchecked")
            List<ChannelMember> requesterMatch = requesterCrit.list();
            if (requesterMatch.isEmpty()) {
                jsonResponse.addProperty("success", false);
                jsonResponse.addProperty("message", "You are not a member of this channel");
                jsonResponse.add("data", new JsonArray());
                session.close();
                resp.getWriter().write(jsonResponse.toString());
                return;
            }

            Criteria memberCrit = session.createCriteria(ChannelMember.class);
            memberCrit.add(Restrictions.eq("channel.id", channelId));
            memberCrit.addOrder(Order.desc("is_admin"));
            memberCrit.addOrder(Order.asc("joined_date_time"));
            @SuppressWarnings("unchecked")
            List<ChannelMember> members = memberCrit.list();

            // Channel creator — separate from ChannelMember.is_admin, since
            // appointed admins must NOT be allowed to delete the channel,
            // only the original creator can.
            Channel channel = (Channel) session.get(Channel.class, channelId);
            Integer creatorId = (channel != null && channel.getCreated_by_user() != null)
                    ? channel.getCreated_by_user().getId() : null;

            JsonArray dataArray = new JsonArray();
            for (ChannelMember cm : members) {
                User u = cm.getUser();
                if (u == null) continue;
                JsonObject row = new JsonObject();
                row.addProperty("user_id", u.getId());
                row.addProperty("name", displayName(u));
                row.addProperty("mobile", safeMobile(u));
                row.addProperty("is_admin", cm.isIs_admin());
                row.addProperty("is_self", u.getId() == requestedByUserId);
                row.addProperty("is_creator", creatorId != null && creatorId == u.getId());

                // Same avatar lookup as ListContacts.java — real profile
                // photo when it exists (/AvaterImages/{mobile}.png), initials
                // fallback on the client otherwise.
                boolean avatarFound = false;
                try {
                    String avatarPath = req.getServletContext()
                            .getRealPath("/AvaterImages/" + u.getMobile() + ".png");
                    File f = new File(avatarPath);
                    avatarFound = f.isFile() && f.length() > 0;
                } catch (Exception ignored) {
                }
                row.addProperty("avatar_image_found", avatarFound);

                dataArray.add(row);
            }

            jsonResponse.addProperty("success", true);
            jsonResponse.addProperty("message", "Members loaded");
            jsonResponse.add("data", dataArray);
            session.close();

        } catch (Exception e) {
            e.printStackTrace();
            if (session != null && session.isOpen()) {
                try { session.close(); } catch (Exception ignored) {}
            }
            jsonResponse = new JsonObject();
            jsonResponse.addProperty("success", false);
            jsonResponse.addProperty("message", "Failed to load members");
            jsonResponse.add("data", new JsonArray());
        }

        resp.getWriter().write(jsonResponse.toString());
    }

    // ⚠️ Adjust these two helpers if your User entity's getters are named differently
    // (e.g. if it's getFirstName()/getLastName() instead of getFirst_name()/getLast_name()).
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

    private String safeMobile(User u) {
        try {
            String mobile = u.getMobile();
            return mobile == null ? "" : mobile;
        } catch (Exception e) {
            return "";
        }
    }
}
