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
 * Represents a "phone-contact sync" relationship: owner_user has
 * contact_user saved in their phone contacts and it matched a
 * registered TalkVerse account.
 *
 * One-directional, like WhatsApp: if A has B saved but B does not
 * have A saved, only A sees B in their Contacts/Chats — not the
 * other way around.
 *
 * NOTE: If your project uses XML (.hbm.xml) mappings instead of
 * annotations (check how entity.User is mapped), mirror that style
 * here instead — the column/table names below are what matters.
 */
@Entity
@Table(
    name = "contact",
    uniqueConstraints = @UniqueConstraint(
        name = "uniq_owner_contact",
        columnNames = {"owner_user_id", "contact_user_id"}
    )
)
public class Contact {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_user_id", nullable = false)
    private User ownerUser;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "contact_user_id", nullable = false)
    private User contactUser;

    @Column(name = "saved_name")
    private String savedName;

    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "created_at")
    private Date createdAt;

    public Contact() {
    }

    public Contact(User ownerUser, User contactUser, String savedName) {
        this.ownerUser = ownerUser;
        this.contactUser = contactUser;
        this.savedName = savedName;
        this.createdAt = new Date();
    }

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public User getOwnerUser() {
        return ownerUser;
    }

    public void setOwnerUser(User ownerUser) {
        this.ownerUser = ownerUser;
    }

    public User getContactUser() {
        return contactUser;
    }

    public void setContactUser(User contactUser) {
        this.contactUser = contactUser;
    }

    public String getSavedName() {
        return savedName;
    }

    public void setSavedName(String savedName) {
        this.savedName = savedName;
    }

    public Date getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Date createdAt) {
        this.createdAt = createdAt;
    }
}
