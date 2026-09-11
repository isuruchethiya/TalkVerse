package entity;

import java.io.Serializable;
import java.util.Date;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.Table;
import javax.persistence.Temporal;
import javax.persistence.TemporalType;

@Entity
@Table(name = "channel")
public class Channel implements Serializable {

    @Id
    @Column(name = "id")
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private int id;

    @Column(name = "name", length = 45, nullable = false)
    private String name;

    @Column(name = "description", length = 255, nullable = true)
    private String description;

    @Column(name = "logo", length = 255, nullable = true)
    private String logo;

    @ManyToOne
    @JoinColumn(name = "created_by_user_id", nullable = false)
    private User created_by_user;

    @Column(name = "created_date_time", nullable = false)
    @Temporal(TemporalType.TIMESTAMP)
    private Date created_date_time;

    @ManyToOne
    @JoinColumn(name = "channel_status_id", nullable = false)
    private ChannelStatus channel_status;

    public Channel() {
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getLogo() {
        return logo;
    }

    public void setLogo(String logo) {
        this.logo = logo;
    }

    public User getCreated_by_user() {
        return created_by_user;
    }

    public void setCreated_by_user(User created_by_user) {
        this.created_by_user = created_by_user;
    }

    public Date getCreated_date_time() {
        return created_date_time;
    }

    public void setCreated_date_time(Date created_date_time) {
        this.created_date_time = created_date_time;
    }

    public ChannelStatus getChannel_status() {
        return channel_status;
    }

    public void setChannel_status(ChannelStatus channel_status) {
        this.channel_status = channel_status;
    }

}
