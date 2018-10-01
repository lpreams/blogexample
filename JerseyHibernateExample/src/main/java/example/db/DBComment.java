package example.db;

import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.ManyToOne;
import javax.persistence.Table;

import example.db.DBUser.FlatUser;


@Entity
@Table(name="comment")
public class DBComment {
	
	/***************************** database columns ***************************/
	
	@Id 
	@GeneratedValue(strategy=GenerationType.AUTO) 
	private long id; // unique id
	
	@ManyToOne // one blog can own many comments
				// (in the database this means that the user's id will be stored in a column called author_id, but Hibernate will automatically convert that into a full DBUser object for us)
	private DBUser author; // user who wrote this post
	
	@ManyToOne
	private DBPost post;
	
	private String body; // comment body
	
	private long date; // when you post the comment
	
	
	
	
	public static class FlatComment {
		public final long id;
		public final String body;
		public final long date;
		public final FlatUser author;
		public FlatComment(DBComment user) {
			this.id = user.id;
			this.body = user.body;
			this.date = user.date;
			this.author = new FlatUser(user.getAuthor());
		}
	}
	public FlatComment flatten() {
		return new FlatComment(this);
	}
	
	
	
	
	public DBComment() {} // required empty constructor

	public long getId() {
		return id;
	}

	public void setId(long id) {
		this.id = id;
	}

	public DBUser getAuthor() {
		return author;
	}

	public void setAuthor(DBUser author) {
		this.author = author;
	}

	public String getBody() {
		return body;
	}

	public void setBody(String body) {
		this.body = body;
	}

	public long getDate() {
		return date;
	}

	public void setDate(long date) {
		this.date = date;
	}




	public DBPost getPost() {
		return post;
	}




	public void setPost(DBPost post) {
		this.post = post;
	}
	
	
}
