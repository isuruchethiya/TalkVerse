package controller;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import entity.Chat;
import entity.ChatStatus;
import entity.User;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.text.SimpleDateFormat;
import java.util.Date;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import model.HibernateUtil;
import org.hibernate.Session;
import org.hibernate.Transaction;

@WebServlet(name = "SendChat", urlPatterns = {"/SendChat"})
public class SendChat extends HttpServlet {

    private static final SimpleDateFormat DT_FMT = new SimpleDateFormat("h:mm a");

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

            int loggedId = body.has("logged_user_id") ? body.get("logged_user_id").getAsInt() : -1;
            int otherId = body.has("other_user_id") ? body.get("other_user_id").getAsInt() : -1;
            String message = body.has("message") ? body.get("message").getAsString() : "";

            if (message == null || message.trim().isEmpty()) {
                jsonResponse.addProperty("success", false);
                jsonResponse.addProperty("message", "Message cannot be empty");
                resp.getWriter().write(jsonResponse.toString());
                return;
            }
            if (loggedId < 0 || otherId < 0 || loggedId == otherId) {
                jsonResponse.addProperty("success", false);
                jsonResponse.addProperty("message", "Invalid user");
                resp.getWriter().write(jsonResponse.toString());
                return;
            }

            session = HibernateUtil.getSessionFactory().openSession();

            User fromUser = (User) session.get(User.class, loggedId);
            User toUser = (User) session.get(User.class, otherId);
            if (fromUser == null || toUser == null) {
                jsonResponse.addProperty("success", false);
                jsonResponse.addProperty("message", "User not found");
                session.close();
                resp.getWriter().write(jsonResponse.toString());
                return;
            }

            ChatStatus sentStatus = (ChatStatus) session.get(ChatStatus.class, 2);
            if (sentStatus == null) {
                sentStatus = (ChatStatus) session.get(ChatStatus.class, 1);
            }

            Chat chat = new Chat();
            chat.setFrom_user(fromUser);
            chat.setTo_user(toUser);
            chat.setMessage(message.trim());
            chat.setDate_time(new Date());
            chat.setChat_status(sentStatus);

            tx = session.beginTransaction();
            session.save(chat);
            tx.commit();

            try {
                JsonObject pushRow = new JsonObject();
                pushRow.addProperty("side", "left");
                pushRow.addProperty("message", chat.getMessage());
                pushRow.addProperty("datetime",
                        chat.getDate_time() != null ? DT_FMT.format(chat.getDate_time()) : "");
                pushRow.addProperty("status",
                        chat.getChat_status() != null ? chat.getChat_status().getId() : 2);

                JsonObject pushEnvelope = new JsonObject();
                pushEnvelope.addProperty("type", "chat_message");
                pushEnvelope.addProperty("from_user_id", loggedId);
                pushEnvelope.addProperty("to_user_id", otherId);
                pushEnvelope.add("message", pushRow);
                ChatSocketEndpoint.pushToUser(otherId, new Gson().toJson(pushEnvelope));
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
            jsonResponse.addProperty("message", "Failed to send message");
        }

        resp.getWriter().write(jsonResponse.toString());
    }
}
