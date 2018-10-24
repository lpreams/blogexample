package example.db;

import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.ManyToOne;
import javax.persistence.Table;

import example.db.DBUser.FlatUser;


@Entity
@Table(name="report")

public class DBReport {
	
		
		/***************************** database columns ***************************/
		
		@Id 
		@GeneratedValue(strategy=GenerationType.AUTO) 
		private long id; // unique id
		
		@ManyToOne // one blog can own many comments
					// (in the database this means that the user's id will be stored in a column called author_id, but Hibernate will automatically convert that into a full DBUser object for us)
		private DBUser author; // user who wrote this post
		private String suggestion; // suggestion body
		private long date; // when you post the comment
		
		
		
		
		public static class FlatReport {
			public final long id;
			public final String suggestion;
			public final long date;
			public final FlatUser author;
			public FlatReport(DBReport user) {
				this.id = user.id;
				this.suggestion = user.suggestion;
				this.date = user.date;
				this.author = new FlatUser(user.getAuthor());
			}
		}
		public FlatReport flatten() {
			return new FlatReport(this);
		}
		
		
		
		
		public DBReport() {} // required empty constructor

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

		public String getSuggestion() {
			return suggestion;
		}

		public void setBody(String suggestion) {
			this.suggestion = suggestion;
		}

		public long getDate() {
			return date;
		}

		public void setDate(long date) {
			this.date = date;
		}
		
		
		


	
		
	

}
