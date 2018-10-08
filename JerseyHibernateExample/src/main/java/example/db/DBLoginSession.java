package example.db;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.ManyToOne;
import javax.persistence.Table;

@Entity // marks this class as a database table (database tables are also called entities)
@Table(name="loginsession") // sets the name of the table in the database
public class DBLoginSession {
	@Id 
	@GeneratedValue(strategy=GenerationType.AUTO) 
	private long id;
	
	@Column(unique=true)
	private String token;
	
	@ManyToOne
	private DBUser user;
	
	private long lastActivity;
	
	public DBLoginSession() {}

	public long getId() {
		return id;
	}

	public void setId(long id) {
		this.id = id;
	}

	public String getToken() {
		return token;
	}

	public void setToken(String token) {
		this.token = token;
	}

	public DBUser getUser() {
		return user;
	}

	public void setUser(DBUser user) {
		this.user = user;
	}

	public long getLastActivity() {
		return lastActivity;
	}

	public void setLastActivity(long lastActivity) {
		this.lastActivity = lastActivity;
	}
	
	
}
