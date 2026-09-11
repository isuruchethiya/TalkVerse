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
 * Records that ownerUser deleted their view of the entire conversation
 * with otherUser as of hiddenAt. One-sided: otherUser's view is untouched.
 *
 * When loading history/home-list for ownerUser, any chat message with
 * date_time <= hiddenAt (between ownerUser and otherUser) should be
 * excluded. A message that arrives AFTER hiddenAt naturally reappears —
 * same behavior as WhatsApp's "Delete chat".
 */
@Entity
@Table(
    name = "chat_hidden",
    uniqueConstraints = @UniqueConstraint(
        name = "uniq_owner_other",
        columnNames = {"owner_user_id", "other_user_id"}
    )
)
public class ChatHidden {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_user_id", nullable = false)
    private User ownerUser;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "other_user_id", nullable = false)
    private User otherUser;

    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "hidden_at", nullable = false)
    private Date hiddenAt;

    // Cutoff by chat.id rather than timestamp — immune to same-second
    // timestamp ties that a plain DATETIME comparison can't distinguish.
    // Any message with id > this value is visible again.
    @Column(name = "hidden_before_chat_id", nullable = false)
    private Integer hiddenBeforeChatId;

    public ChatHidden() {
    }

    public ChatHidden(User ownerUser, User otherUser, Date hiddenAt) {
        this.ownerUser = ownerUser;
        this.otherUser = otherUser;
        this.hiddenAt = hiddenAt;
    }

    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }

    public User getOwnerUser() { return ownerUser; }
    public void setOwnerUser(User ownerUser) { this.ownerUser = ownerUser; }

    public User getOtherUser() { return otherUser; }
    public void setOtherUser(User otherUser) { this.otherUser = otherUser; }

    public Date getHiddenAt() { return hiddenAt; }
    public void setHiddenAt(Date hiddenAt) { this.hiddenAt = hiddenAt; }

    public Integer getHiddenBeforeChatId() { return hiddenBeforeChatId; }
    public void setHiddenBeforeChatId(Integer hiddenBeforeChatId) { this.hiddenBeforeChatId = hiddenBeforeChatId; }
}
