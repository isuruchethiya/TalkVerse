package controller;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import entity.Channel;
import entity.ChannelMember;
import entity.ChannelMessage;
import entity.User;
import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
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

@WebServlet(name = "LoadChannelMessages", urlPatterns = {"/LoadChannelMessages"})
public class LoadChannelMessages extends HttpServlet {

    private static final SimpleDateFormat DT_FMT = new SimpleDateFormat("MMM d, h:mm a");

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        resp.setContentType("application/json");
        resp.setCharacterEncoding("UTF-8");

        JsonObject jsonResponse = new JsonObject();
        Session session = null;

        try {
            String cidStr = req.getParameter("channel_id");
            String uidStr = req.getParameter("user_id");
            if (cidStr == null || cidStr.trim().isEmpty()
                    || uidStr == null || uidStr.trim().isEmpty()) {
                jsonResponse.addProperty("success", false);
                jsonResponse.addProperty("message", "channel_id and user_id are required");
                jsonResponse.add("data", new JsonArray());
                resp.getWriter().write(jsonResponse.toString());
                return;
            }
            int channelId = Integer.parseInt(cidStr.trim());
            int userId = Integer.parseInt(uidStr.trim());

            session = HibernateUtil.getSessionFactory().openSession();

            Channel channel = (Channel) session.get(Channel.class, channelId);
            if (channel == null) {
                jsonResponse.addProperty("success", false);
                jsonResponse.addProperty("message", "Channel not found");
                jsonResponse.add("data", new JsonArray());
                session.close();
                resp.getWriter().write(jsonResponse.toString());
                return;
            }

            User user = (User) session.get(User.class, userId);
            if (user == null) {
                jsonResponse.addProperty("success", false);
                jsonResponse.addProperty("message", "User not found");
                jsonResponse.add("data", new JsonArray());
                session.close();
                resp.getWriter().write(jsonResponse.toString());
                return;
            }

            Criteria memberCrit = session.createCriteria(ChannelMember.class);
            memberCrit.add(Restrictions.eq("channel.id", channelId));
            memberCrit.add(Restrictions.eq("user.id", userId));
            if (memberCrit.list().isEmpty()) {
                jsonResponse.addProperty("success", false);
                jsonResponse.addProperty("message", "Not a member of this channel");
                jsonResponse.add("data", new JsonArray());
                session.close();
                resp.getWriter().write(jsonResponse.toString());
                return;
            }

            Criteria msgCrit = session.createCriteria(ChannelMessage.class);
            msgCrit.add(Restrictions.eq("channel.id", channelId));
            msgCrit.addOrder(Order.asc("date_time"));
            @SuppressWarnings("unchecked")
            List<ChannelMessage> messages = msgCrit.list();

            JsonArray dataArray = new JsonArray();
            for (ChannelMessage cm : messages) {
                JsonObject row = new JsonObject();
                User sender = cm.getFrom_user();
                row.addProperty("sender_id", sender.getId());
                String senderName = (sender.getFirst_name() == null ? "" : sender.getFirst_name())
                        + " "
                        + (sender.getLast_name() == null ? "" : sender.getLast_name());
                row.addProperty("sender_name", senderName.trim().isEmpty() ? sender.getMobile() : senderName.trim());
                row.addProperty("sender_mobile", sender.getMobile() == null ? "" : sender.getMobile());

                // Same avatar lookup as ListContacts.java / ListChannelMembers.java —
                // real profile photo when it exists (/AvaterImages/{mobile}.png),
                // initials fallback on the client otherwise.
                boolean senderAvatarFound = false;
                try {
                    String avatarPath = req.getServletContext()
                            .getRealPath("/AvaterImages/" + sender.getMobile() + ".png");
                    File f = new File(avatarPath);
                    senderAvatarFound = f.isFile() && f.length() > 0;
                } catch (Exception ignored) {
                }
                row.addProperty("sender_avatar_found", senderAvatarFound);

                row.addProperty("message", cm.getMessage());
                row.addProperty("datetime",
                        cm.getDate_time() != null ? DT_FMT.format(cm.getDate_time()) : "");
                row.addProperty("is_own_message", sender.getId() == userId);
                row.addProperty("status",
                        cm.getChannel_message_status() != null
                        ? cm.getChannel_message_status().getId() : 1);
                dataArray.add(new Gson().fromJson(new Gson().toJson(row), JsonObject.class));
            }

            jsonResponse.addProperty("success", true);
            jsonResponse.addProperty("message", "Messages loaded");
            jsonResponse.add("data", dataArray);
            session.close();

        } catch (Exception e) {
            e.printStackTrace();
            if (session != null && session.isOpen()) {
                try { session.close(); } catch (Exception ignored) {}
            }
            jsonResponse = new JsonObject();
            jsonResponse.addProperty("success", false);
            jsonResponse.addProperty("message", "Failed to load channel messages");
            jsonResponse.add("data", new JsonArray());
        }

        resp.getWriter().write(jsonResponse.toString());
    }
}
