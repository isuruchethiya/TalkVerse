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
import javax.persistence.UniqueConstraint;

@Entity
@Table(name = "channel_member", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"channel_id", "user_id"})
})
public class ChannelMember implements Serializable {

    @Id
    @Column(name = "id")
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private int id;

    @ManyToOne
    @JoinColumn(name = "channel_id", nullable = false)
    private Channel channel;

    @ManyToOne
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "joined_date_time", nullable = false)
    @Temporal(TemporalType.TIMESTAMP)
    private Date joined_date_time;

    @Column(name = "is_admin", nullable = false)
    private boolean is_admin;

    // Null means "never opened this channel" -> everything is unread.
    @Column(name = "last_read_date_time", nullable = true)
    @Temporal(TemporalType.TIMESTAMP)
    private Date last_read_date_time;

    public ChannelMember() {
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public Channel getChannel() {
        return channel;
    }

    public void setChannel(Channel channel) {
        this.channel = channel;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public Date getJoined_date_time() {
        return joined_date_time;
    }

    public void setJoined_date_time(Date joined_date_time) {
        this.joined_date_time = joined_date_time;
    }

    public boolean isIs_admin() {
        return is_admin;
    }

    public void setIs_admin(boolean is_admin) {
        this.is_admin = is_admin;
    }

    public Date getLast_read_date_time() {
        return last_read_date_time;
    }

    public void setLast_read_date_time(Date last_read_date_time) {
        this.last_read_date_time = last_read_date_time;
    }

}
