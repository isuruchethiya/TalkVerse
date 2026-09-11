package controller;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import entity.Chat;
import entity.ChatHidden;
import entity.Contact;
import entity.User;
import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
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
import org.hibernate.criterion.Disjunction;
import org.hibernate.criterion.Order;
import org.hibernate.criterion.Restrictions;

@WebServlet(name = "LoadHomeData", urlPatterns = {"/LoadHomeData"})
public class LoadHomeData extends HttpServlet {

    private static final SimpleDateFormat TIME_FMT = new SimpleDateFormat("h:mm a");
    private static final SimpleDateFormat DATE_FMT = new SimpleDateFormat("MMM d");

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        resp.setContentType("application/json");
        resp.setCharacterEncoding("UTF-8");

        JsonObject jsonResponse = new JsonObject();
        JsonArray chatArray = new JsonArray();
        Session session = null;

        try {
            String idStr = req.getParameter("id");
            if (idStr == null || idStr.trim().isEmpty()) {
                jsonResponse.addProperty("success", false);
                jsonResponse.add("jsonChatArray", chatArray);
                resp.getWriter().write(jsonResponse.toString());
                return;
            }
            int userId = Integer.parseInt(idStr.trim());

            session = HibernateUtil.getSessionFactory().openSession();

            User me = (User) session.get(User.class, userId);
            if (me == null) {
                jsonResponse.addProperty("success", false);
                jsonResponse.add("jsonChatArray", chatArray);
                session.close();
                resp.getWriter().write(jsonResponse.toString());
                return;
            }

            // Two sources feed the chat list, unioned together:
            //  1) Contact table — people this user has synced from their phone
            //     (shows up even with no messages yet, as "Start a conversation")
            //  2) Chat table — anyone with actual message history with this user,
            //     REGARDLESS of whether this user has them saved as a contact.
            //     This matters because contact-sync is one-directional: if sunil
            //     messages isuru but isuru hasn't saved sunil's number, isuru
            //     must still see the chat — exactly like WhatsApp shows messages
            //     from unsaved numbers.
            Map<Integer, Chat> latestChatPerUser = new HashMap<Integer, Chat>();
            Criteria chatCrit = session.createCriteria(Chat.class);
            Disjunction or = Restrictions.disjunction();
            or.add(Restrictions.eq("from_user.id", userId));
            or.add(Restrictions.eq("to_user.id", userId));
            chatCrit.add(or);
            chatCrit.addOrder(Order.desc("date_time"));
            @SuppressWarnings("unchecked")
            List<Chat> allMyChats = chatCrit.list();

            Criteria contactCrit = session.createCriteria(Contact.class);
            contactCrit.add(Restrictions.eq("ownerUser.id", userId));
            @SuppressWarnings("unchecked")
            List<Contact> myContacts = contactCrit.list();

            // "Delete chat" markers — conversations I hid. hiddenBeforeChatId
            // is the id-based cutoff: any message with a HIGHER id was sent
            // after the delete and stays visible. (hiddenAt is kept only for
            // the contact-suppression check below, which compares against
            // Contact.createdAt — a much lower-frequency event where a
            // same-second tie is not a practical concern.)
            Criteria hiddenCrit = session.createCriteria(ChatHidden.class);
            hiddenCrit.add(Restrictions.eq("ownerUser.id", userId));
            @SuppressWarnings("unchecked")
            List<ChatHidden> myHiddenChats = hiddenCrit.list();
            // A pair can have MULTIPLE ChatHidden rows (each "Delete Chat"
            // tap inserts a new row rather than reliably updating one — see
            // chat_hidden table having duplicate rows for the same pair).
            // We must take the MAX hidden_before_chat_id per otherUserId,
            // exactly like LoadChat.java's SQL MAX() does — otherwise this
            // loop picks whichever row happens to come last in an
            // unordered result set, which can be a stale/smaller cutoff
            // and cause the chat list preview to disagree with the chat
            // screen about which messages are hidden.
            Map<Integer, Date> hiddenAtByOtherUserId = new HashMap<Integer, Date>();
            Map<Integer, Integer> hiddenBeforeChatIdByOtherUserId = new HashMap<Integer, Integer>();
            for (ChatHidden h : myHiddenChats) {
                int otherUid = h.getOtherUser().getId();
                int cutoff = h.getHiddenBeforeChatId() == null ? 0 : h.getHiddenBeforeChatId();
                Integer existingCutoff = hiddenBeforeChatIdByOtherUserId.get(otherUid);
                if (existingCutoff == null || cutoff > existingCutoff) {
                    hiddenBeforeChatIdByOtherUserId.put(otherUid, cutoff);
                    hiddenAtByOtherUserId.put(otherUid, h.getHiddenAt());
                }
            }

            Map<Integer, User> otherUsersById = new HashMap<Integer, User>();
            java.util.Set<Integer> savedContactIds = new java.util.HashSet<Integer>();

            for (Contact c : myContacts) {
                User cu = c.getContactUser();
                savedContactIds.add(cu.getId()); // always tracked, for name-display logic

                // If I deleted this chat AND the contact was saved before
                // that deletion, don't let the bare "saved contact"
                // placeholder bring the conversation back into the Chats
                // tab. Re-saving/re-syncing the contact AFTER the delete
                // (created_at after hiddenAt) legitimately brings it back.
                Date hiddenAt = hiddenAtByOtherUserId.get(cu.getId());
                boolean suppressed = hiddenAt != null
                        && c.getCreatedAt() != null
                        && !c.getCreatedAt().after(hiddenAt);
                if (!suppressed) {
                    otherUsersById.put(cu.getId(), cu);
                }
            }

            for (Chat c : allMyChats) {
                User other = (c.getFrom_user().getId() == userId) ? c.getTo_user() : c.getFrom_user();
                int otherId = other.getId();

                // Skip messages at/before this user's "delete chat" cutoff id.
                Integer cutoffId = hiddenBeforeChatIdByOtherUserId.get(otherId);
                if (cutoffId != null && c.getId() <= cutoffId) {
                    continue;
                }

                otherUsersById.put(otherId, other); // union — no-op if already added via contacts
                if (!latestChatPerUser.containsKey(otherId)) {
                    latestChatPerUser.put(otherId, c);
                }
            }

            // chat_status: id 1 = "Seen" (read), id 2 = "Sent" (unread),
            // id 3 = "Delivered" (unused). Confirmed via chat_status table.
            final int UNREAD_STATUS_ID = 2;
            Map<Integer, Integer> unreadCountByOtherUserId = new HashMap<Integer, Integer>();
            for (Chat c : allMyChats) {
                if (c.getTo_user().getId() != userId) continue; // only messages sent TO me count as unread
                int otherId = c.getFrom_user().getId();
                Integer cutoffId = hiddenBeforeChatIdByOtherUserId.get(otherId);
                if (cutoffId != null && c.getId() <= cutoffId) continue;
                boolean unread = c.getChat_status() != null && c.getChat_status().getId() == UNREAD_STATUS_ID;
                if (unread) {
                    Integer prev = unreadCountByOtherUserId.get(otherId);
                    unreadCountByOtherUserId.put(otherId, (prev == null ? 0 : prev) + 1);
                }
            }

            List<User> allOtherUsers = new ArrayList<User>(otherUsersById.values());

            List<JsonObject> rowsWithChat = new ArrayList<JsonObject>();
            List<JsonObject> rowsWithoutChat = new ArrayList<JsonObject>();

            for (User other : allOtherUsers) {
                boolean isSaved = savedContactIds.contains(other.getId());
                JsonObject row = buildRow(req, other, isSaved);
                Integer unread = unreadCountByOtherUserId.get(other.getId());
                row.addProperty("unread_count", unread == null ? 0 : unread);
                Chat last = latestChatPerUser.get(other.getId());
                if (last != null) {
                    row.addProperty("message", last.getMessage());
                    row.addProperty("chat_status_id",
                            last.getChat_status() != null ? last.getChat_status().getId() : 0);
                    row.addProperty("dateTime", formatDateTimeShort(last.getDate_time()));
                    rowsWithChat.add(row);
                    row.addProperty("__sort_ts", last.getDate_time().getTime());
                } else {
                    row.addProperty("message", "Start a conversation");
                    row.addProperty("chat_status_id", 0);
                    row.addProperty("dateTime", "");
                    rowsWithoutChat.add(row);
                }
            }

            Collections.sort(rowsWithChat, new Comparator<JsonObject>() {
                @Override
                public int compare(JsonObject a, JsonObject b) {
                    long ta = a.has("__sort_ts") ? a.get("__sort_ts").getAsLong() : 0;
                    long tb = b.has("__sort_ts") ? b.get("__sort_ts").getAsLong() : 0;
                    return Long.compare(tb, ta);
                }
            });

            Collections.sort(rowsWithoutChat, new Comparator<JsonObject>() {
                @Override
                public int compare(JsonObject a, JsonObject b) {
                    String na = a.has("other_user_name") ? a.get("other_user_name").getAsString() : "";
                    String nb = b.has("other_user_name") ? b.get("other_user_name").getAsString() : "";
                    return na.compareToIgnoreCase(nb);
                }
            });

            for (JsonObject r : rowsWithChat) {
                r.remove("__sort_ts");
                chatArray.add(new Gson().fromJson(new Gson().toJson(r), JsonObject.class));
            }
            for (JsonObject r : rowsWithoutChat) {
                r.remove("__sort_ts");
                chatArray.add(new Gson().fromJson(new Gson().toJson(r), JsonObject.class));
            }

            jsonResponse.addProperty("success", true);
            jsonResponse.add("jsonChatArray", chatArray);
            session.close();

        } catch (Exception e) {
            e.printStackTrace();
            if (session != null && session.isOpen()) {
                try { session.close(); } catch (Exception ignored) {}
            }
            jsonResponse = new JsonObject();
            jsonResponse.addProperty("success", false);
            jsonResponse.add("jsonChatArray", new JsonArray());
        }

        resp.getWriter().write(jsonResponse.toString());
    }

    private JsonObject buildRow(HttpServletRequest req, User other, boolean isSavedContact) {
        JsonObject o = new JsonObject();
        o.addProperty("other_user_id", other.getId());
        o.addProperty("other_user_mobile", other.getMobile());
        o.addProperty("is_saved_contact", isSavedContact);

        String fn = other.getFirst_name();
        String ln = other.getLast_name();

        if (isSavedContact) {
            // Saved contact — show their registered name.
            String name = (fn == null ? "" : fn) + " " + (ln == null ? "" : ln);
            o.addProperty("other_user_name", name.trim());

            String letters = "";
            if (fn != null && !fn.isEmpty() && ln != null && !ln.isEmpty()) {
                letters = fn.substring(0, 1).toUpperCase() + ln.substring(0, 1).toUpperCase();
            } else if (fn != null && !fn.isEmpty()) {
                letters = fn.substring(0, 1).toUpperCase();
            }
            o.addProperty("other_user_avatar_letters", letters);
        } else {
            // Not saved in this user's contacts — only reached this list via
            // chat history. Show the mobile number instead of the name, same
            // as WhatsApp/Telegram do for messages from unsaved numbers. The
            // client can offer a "Save Contact" action using is_saved_contact.
            o.addProperty("other_user_name", other.getMobile());
            o.addProperty("other_user_avatar_letters", "#");
        }

        o.addProperty("other_user_status",
                other.getUser_status() != null ? other.getUser_status().getId() : 0);

        boolean avatarFound = false;
        try {
            String avatarPath = req.getServletContext().getRealPath("/AvaterImages/" + other.getMobile() + ".png");
            File f = new File(avatarPath);
            avatarFound = f.isFile() && f.length() > 0;
        } catch (Exception ignored) {}
        o.addProperty("avatar_image_found", avatarFound);

        return o;
    }

    private String formatDateTimeShort(Date d) {
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
