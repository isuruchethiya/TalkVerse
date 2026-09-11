package entity;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.Table;
import javax.persistence.Temporal;
import javax.persistence.TemporalType;
import javax.persistence.UniqueConstraint;
import java.util.Date;

/**
 * Records that `user` deleted a single `chat` message from their own view
 * only. The message row itself is never removed — the other participant
 * still sees it. When loading chat history for `user`, any Chat whose id
 * has a matching row here should be excluded.
 */
@Entity
@Table(
    name = "chat_deleted_for_user",
    uniqueConstraints = @UniqueConstraint(
        name = "uniq_chat_user",
        columnNames = {"chat_id", "user_id"}
    )
)
public class ChatDeletedForUser {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "chat_id", nullable = false)
    private Chat chat;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "deleted_at")
    private Date deletedAt;

    public ChatDeletedForUser() {
    }

    public ChatDeletedForUser(Chat chat, User user) {
        this.chat = chat;
        this.user = user;
        this.deletedAt = new Date();
    }

    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }

    public Chat getChat() { return chat; }
    public void setChat(Chat chat) { this.chat = chat; }

    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }

    public Date getDeletedAt() { return deletedAt; }
    public void setDeletedAt(Date deletedAt) { this.deletedAt = deletedAt; }
}
