package controller;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import entity.Channel;
import entity.ChannelMember;
import entity.ChannelMessage;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
 * Returns only the channels the requesting user is already a member of
 * (invite-only model - no public "discover all channels" list).
 *
 *   GET /ListChannels?user_id=123
 */
@WebServlet(name = "ListChannels", urlPatterns = {"/ListChannels"})
public class ListChannels extends HttpServlet {

    private static final SimpleDateFormat TIME_FMT = new SimpleDateFormat("h:mm a");
    private static final SimpleDateFormat DATE_FMT = new SimpleDateFormat("MMM d");

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        resp.setContentType("application/json");
        resp.setCharacterEncoding("UTF-8");

        JsonObject jsonResponse = new JsonObject();
        Session session = null;

        try {
            String userIdRaw = req.getParameter("user_id");
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

            session = HibernateUtil.getSessionFactory().openSession();

            // Channels this user belongs to, joined against Channel for active status.
            Criteria membershipCrit = session.createCriteria(ChannelMember.class);
            membershipCrit.add(Restrictions.eq("user.id", userId));
            @SuppressWarnings("unchecked")
            List<ChannelMember> myMemberships = membershipCrit.list();

            Map<Integer, Boolean> myAdminMap = new HashMap<Integer, Boolean>();
            for (ChannelMember cm : myMemberships) {
                if (cm.getChannel() != null && cm.getChannel().getChannel_status() != null
                        && cm.getChannel().getChannel_status().getId() == 1) {
                    myAdminMap.put(cm.getChannel().getId(), cm.isIs_admin());
                }
            }

            JsonArray dataArray = new JsonArray();

            for (ChannelMember cm : myMemberships) {
                Channel ch = cm.getChannel();
                if (ch == null || ch.getChannel_status() == null || ch.getChannel_status().getId() != 1) {
                    continue; // skip inactive/archived channels
                }
                int channelId = ch.getId();

                Criteria countCrit = session.createCriteria(ChannelMember.class);
                countCrit.add(Restrictions.eq("channel.id", channelId));
                int memberCount = countCrit.list().size();

                Criteria msgCrit = session.createCriteria(ChannelMessage.class);
                msgCrit.add(Restrictions.eq("channel.id", channelId));
                msgCrit.addOrder(Order.desc("date_time"));
                msgCrit.setMaxResults(1);
                @SuppressWarnings("unchecked")
                List<ChannelMessage> msgList = msgCrit.list();

                // Unread = messages from OTHER users, sent after this user's
                // last_read_date_time. NULL last_read means never opened ->
                // everything from others is unread.
                Date lastRead = cm.getLast_read_date_time();
                Criteria unreadCrit = session.createCriteria(ChannelMessage.class);
                unreadCrit.add(Restrictions.eq("channel.id", channelId));
                unreadCrit.add(Restrictions.ne("from_user.id", userId));
                if (lastRead != null) {
                    unreadCrit.add(Restrictions.gt("date_time", lastRead));
                }
                int unreadCount = unreadCrit.list().size();

                JsonObject row = new JsonObject();
                row.addProperty("channel_id", ch.getId());
                row.addProperty("channel_name", ch.getName());
                row.addProperty("description", ch.getDescription() == null ? "" : ch.getDescription());
                row.addProperty("logo", ch.getLogo() == null ? "" : ch.getLogo());
                row.addProperty("member_count", memberCount);
                row.addProperty("is_admin", myAdminMap.containsKey(channelId) ? myAdminMap.get(channelId) : false);
                row.addProperty("unread_count", unreadCount);

                if (!msgList.isEmpty()) {
                    ChannelMessage last = msgList.get(0);
                    row.addProperty("last_message", last.getMessage());
                    row.addProperty("last_message_time", formatShort(last.getDate_time()));
                    String lastSenderName = "";
                    if (last.getFrom_user() != null) {
                        String fn = last.getFrom_user().getFirst_name();
                        String ln = last.getFrom_user().getLast_name();
                        lastSenderName = ((fn == null ? "" : fn) + " " + (ln == null ? "" : ln)).trim();
                        if (lastSenderName.isEmpty()) {
                            lastSenderName = last.getFrom_user().getMobile();
                        }
                    }
                    row.addProperty("last_message_sender_name", lastSenderName);
                    row.addProperty("last_message_sender_id",
                            last.getFrom_user() != null ? last.getFrom_user().getId() : -1);
                } else {
                    row.addProperty("last_message", "");
                    row.addProperty("last_message_time", "");
                    row.addProperty("last_message_sender_name", "");
                    row.addProperty("last_message_sender_id", -1);
                }

                dataArray.add(row);
            }

            jsonResponse.addProperty("success", true);
            jsonResponse.addProperty("message", "Channels loaded");
            jsonResponse.add("data", dataArray);
            session.close();

        } catch (Exception e) {
            e.printStackTrace();
            if (session != null && session.isOpen()) {
                try { session.close(); } catch (Exception ignored) {}
            }
            jsonResponse = new JsonObject();
            jsonResponse.addProperty("success", false);
            jsonResponse.addProperty("message", "Failed to load channels");
            jsonResponse.add("data", new JsonArray());
        }

        resp.getWriter().write(jsonResponse.toString());
    }

    private String formatShort(Date d) {
        if (d == null) return "";
        Calendar today = Calendar.getInstance();
        Calendar c = Calendar.getInstance();
        c.setTime(d);
        boolean sameDay = today.get(Calendar.YEAR) == c.get(Calendar.YEAR)
                && today.get(Calendar.MONTH) == c.get(Calendar.MONTH)
                && today.get(Calendar.DAY_OF_MONTH) == c.get(Calendar.DAY_OF_MONTH);
        try {
            return sameDay ? TIME_FMT.format(d) : DATE_FMT.format(d);
        } catch (Exception e) {
            return "";
        }
    }
}
