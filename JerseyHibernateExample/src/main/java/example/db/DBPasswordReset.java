package example.db;

import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.ManyToOne;
import javax.persistence.Table;

@Entity 
@Table(name="passwordreset")
public class DBPasswordReset {
	@Id 
	@GeneratedValue(strategy=GenerationType.AUTO) 
	private long id;
	
	private String token;
	
	@ManyToOne
	private DBUser user;
	
	private long date;
	
	private String newHashedPassword;
	
	public DBPasswordReset() {}

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

	public long getDate() {
		return date;
	}

	public void setDate(long date) {
		this.date = date;
	}

	public String getNewHashedPassword() {
		return newHashedPassword;
	}

	public void setNewHashedPassword(String newHashedPassword) {
		this.newHashedPassword = newHashedPassword;
	}
}
