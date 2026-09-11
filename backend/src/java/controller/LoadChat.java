package controller;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import entity.Chat;
import entity.ChatStatus;
import java.io.IOException;
import java.text.SimpleDateFormat;
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
import org.hibernate.SQLQuery;
import org.hibernate.Session;
import org.hibernate.Transaction;
import org.hibernate.criterion.Disjunction;
import org.hibernate.criterion.Order;
import org.hibernate.criterion.Restrictions;

@WebServlet(name = "LoadChat", urlPatterns = {"/LoadChat"})
public class LoadChat extends HttpServlet {

    private static final SimpleDateFormat DT_FMT = new SimpleDateFormat("h:mm a");

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        resp.setContentType("application/json");
        resp.setCharacterEncoding("UTF-8");

        JsonArray result = new JsonArray();
        Session session = null;
        Transaction tx = null;

        try {
            String loggedStr = req.getParameter("logged_user_id");
            String otherStr = req.getParameter("other_user_id");
            if (loggedStr == null || loggedStr.trim().isEmpty()
                    || otherStr == null || otherStr.trim().isEmpty()) {
                resp.getWriter().write(new Gson().toJson(result));
                return;
            }
            int loggedId = Integer.parseInt(loggedStr.trim());
            int otherId = Integer.parseInt(otherStr.trim());

            session = HibernateUtil.getSessionFactory().openSession();

            // --- Bug B fix: chat-level "Delete Chat" cutoff ---
            // Each delete inserts a NEW chat_hidden row for this (owner, other) pair,
            // so we must take the MAX hidden_before_chat_id, not just any row.
            Object maxHiddenObj = session.createSQLQuery(
                    "SELECT COALESCE(MAX(hidden_before_chat_id), 0) "
                            + "FROM chat_hidden WHERE owner_user_id = :owner AND other_user_id = :other")
                    .setParameter("owner", loggedId)
                    .setParameter("other", otherId)
                    .uniqueResult();
            int hiddenBeforeChatId = maxHiddenObj != null ? ((Number) maxHiddenObj).intValue() : 0;

            // --- Message-level "Delete for me" filter ---
            // Individual messages this user deleted, regardless of chat-level hide state.
            @SuppressWarnings("unchecked")
            List<Number> deletedRows = session.createSQLQuery(
                    "SELECT chat_id FROM chat_deleted_for_user WHERE user_id = :uid")
                    .setParameter("uid", loggedId)
                    .list();
            Set<Long> deletedForUser = new HashSet<>();
            for (Number n : deletedRows) {
                deletedForUser.add(n.longValue());
            }

            Criteria c = session.createCriteria(Chat.class);
            Disjunction pair = Restrictions.disjunction();
            pair.add(Restrictions.and(
                    Restrictions.eq("from_user.id", loggedId),
                    Restrictions.eq("to_user.id", otherId)));
            pair.add(Restrictions.and(
                    Restrictions.eq("from_user.id", otherId),
                    Restrictions.eq("to_user.id", loggedId)));
            c.add(pair);
            if (hiddenBeforeChatId > 0) {
                c.add(Restrictions.gt("id", hiddenBeforeChatId));
            }
            c.addOrder(Order.asc("date_time"));

            @SuppressWarnings("unchecked")
            List<Chat> chatList = c.list();

            ChatStatus seenStatus = null;
            if (!chatList.isEmpty()) {
                seenStatus = (ChatStatus) session.get(ChatStatus.class, 1);
            }
            boolean anyUpdate = false;
            tx = session.beginTransaction();

            for (Chat ch : chatList) {
                if (deletedForUser.contains((long) ch.getId())) {
                    continue;
                }

                JsonObject row = new JsonObject();
                boolean isRightSide = ch.getFrom_user().getId() == loggedId;
                row.addProperty("chat_id", ch.getId());
                row.addProperty("side", isRightSide ? "right" : "left");
                row.addProperty("message", ch.getMessage());
                row.addProperty("datetime",
                        ch.getDate_time() != null ? DT_FMT.format(ch.getDate_time()) : "");
                int statusId = ch.getChat_status() != null ? ch.getChat_status().getId() : 0;
                row.addProperty("status", statusId);

                if (!isRightSide && statusId != 1 && seenStatus != null) {
                    ch.setChat_status(seenStatus);
                    session.update(ch);
                    anyUpdate = true;
                    row.remove("status");
                    row.addProperty("status", 1);
                }

                result.add(row);
            }

            if (anyUpdate) {
                tx.commit();
            } else {
                tx.rollback();
            }
            session.close();

        } catch (Exception e) {
            e.printStackTrace();
            if (tx != null && tx.isActive()) {
                try { tx.rollback(); } catch (Exception ignored) {}
            }
            if (session != null && session.isOpen()) {
                try { session.close(); } catch (Exception ignored) {}
            }
            result = new JsonArray();
        }

        resp.getWriter().write(new Gson().toJson(result));
    }
}
