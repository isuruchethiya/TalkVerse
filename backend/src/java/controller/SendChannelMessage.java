package controller;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import entity.Channel;
import entity.ChannelMember;
import entity.ChannelMessage;
import entity.ChannelMessageStatus;
import entity.User;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
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

@WebServlet(name = "SendChannelMessage", urlPatterns = {"/SendChannelMessage"})
public class SendChannelMessage extends HttpServlet {

    private static final SimpleDateFormat DT_FMT = new SimpleDateFormat("MMM d, h:mm a");

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

            int channelId = body.has("channel_id") ? body.get("channel_id").getAsInt() : -1;
            int fromUserId = body.has("from_user_id") ? body.get("from_user_id").getAsInt() : -1;
            String message = body.has("message") ? body.get("message").getAsString() : "";

            if (message == null || message.trim().isEmpty()) {
                jsonResponse.addProperty("success", false);
                jsonResponse.addProperty("message", "Message cannot be empty");
                resp.getWriter().write(jsonResponse.toString());
                return;
            }
            if (channelId < 1 || fromUserId < 1) {
                jsonResponse.addProperty("success", false);
                jsonResponse.addProperty("message", "Invalid channel_id or from_user_id");
                resp.getWriter().write(jsonResponse.toString());
                return;
            }

            session = HibernateUtil.getSessionFactory().openSession();

            Channel channel = (Channel) session.get(Channel.class, channelId);
            if (channel == null) {
                jsonResponse.addProperty("success", false);
                jsonResponse.addProperty("message", "Channel not found");
                session.close();
                resp.getWriter().write(jsonResponse.toString());
                return;
            }

            User fromUser = (User) session.get(User.class, fromUserId);
            if (fromUser == null) {
                jsonResponse.addProperty("success", false);
                jsonResponse.addProperty("message", "Sender user not found");
                session.close();
                resp.getWriter().write(jsonResponse.toString());
                return;
            }

            Criteria memberCrit = session.createCriteria(ChannelMember.class);
            memberCrit.add(Restrictions.eq("channel.id", channelId));
            memberCrit.add(Restrictions.eq("user.id", fromUserId));
            @SuppressWarnings("unchecked")
            List<ChannelMember> membership = memberCrit.list();
            if (membership.isEmpty()) {
                jsonResponse.addProperty("success", false);
                jsonResponse.addProperty("message", "Not a member of this channel");
                session.close();
                resp.getWriter().write(jsonResponse.toString());
                return;
            }

            ChannelMessageStatus sentStatus = (ChannelMessageStatus) session.get(ChannelMessageStatus.class, 1);
            if (sentStatus == null) {
                jsonResponse.addProperty("success", false);
                jsonResponse.addProperty("message", "Channel message status Sent not seeded (id=1 missing)");
                session.close();
                resp.getWriter().write(jsonResponse.toString());
                return;
            }

            ChannelMessage cm = new ChannelMessage();
            cm.setChannel(channel);
            cm.setFrom_user(fromUser);
            cm.setMessage(message.trim());
            cm.setDate_time(new Date());
            cm.setChannel_message_status(sentStatus);

            tx = session.beginTransaction();
            session.save(cm);
            tx.commit();

            try {
                Criteria allMembersCrit = session.createCriteria(ChannelMember.class);
                allMembersCrit.add(Restrictions.eq("channel.id", channelId));
                @SuppressWarnings("unchecked")
                List<ChannelMember> allMembers = allMembersCrit.list();
                ArrayList<Integer> memberIds = new ArrayList<Integer>();
                for (ChannelMember mem : allMembers) {
                    if (mem.getUser() != null) {
                        memberIds.add(mem.getUser().getId());
                    }
                }

                String senderName = (fromUser.getFirst_name() == null ? "" : fromUser.getFirst_name())
                        + " "
                        + (fromUser.getLast_name() == null ? "" : fromUser.getLast_name());
                senderName = senderName.trim().isEmpty() ? fromUser.getMobile() : senderName.trim();

                for (Integer viewerIdObj : memberIds) {
                    int viewerId = viewerIdObj.intValue();
                    boolean isOwn = viewerId == fromUserId;

                    JsonObject pushRow = new JsonObject();
                    pushRow.addProperty("sender_id", fromUserId);
                    pushRow.addProperty("sender_name", senderName);
                    pushRow.addProperty("message", cm.getMessage());
                    pushRow.addProperty("datetime",
                            cm.getDate_time() != null ? DT_FMT.format(cm.getDate_time()) : "");
                    pushRow.addProperty("is_own_message", isOwn);
                    pushRow.addProperty("status",
                            cm.getChannel_message_status() != null
                            ? cm.getChannel_message_status().getId() : 1);

                    JsonObject pushEnvelope = new JsonObject();
                    pushEnvelope.addProperty("type", "channel_message");
                    pushEnvelope.addProperty("channel_id", channelId);
                    pushEnvelope.add("message", pushRow);
                    ChatSocketEndpoint.pushToUser(viewerId, new Gson().toJson(pushEnvelope));
                }
            } catch (Exception ignored) {
            }

            session.close();

            jsonResponse.addProperty("success", true);
            jsonResponse.addProperty("message", "Message sent");

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
            jsonResponse.addProperty("message", "Failed to send channel message");
        }

        resp.getWriter().write(jsonResponse.toString());
    }
}
