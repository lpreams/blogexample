package example;

import java.util.List;
import java.util.stream.Collectors;

import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.Root;

import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.hibernate.cfg.Configuration;

import example.db.DBComment;
import example.db.DBComment.FlatComment;
import example.db.DBPost;
import example.db.DBPost.FlatPost;
import example.db.DBUser;
import example.db.DBUser.FlatUser;

public class DB {
	
	private static SessionFactory sessionFactory;
	
	/**
	 * Star the database connection
	 */
	public static void startDatabaseConnection() {
		sessionFactory = newSessionFactory();
	}
	
	/**
	 * Set up a new SessionFactory to manage connections to the database
	 * @return newly created SessionFactory instance
	 */
	private static SessionFactory newSessionFactory() {
		Configuration configuration = new Configuration();


		configuration.setProperty("hibernate.connection.driver_class", "org.h2.Driver"); // use H2 driver
		configuration.setProperty("hibernate.dialect", "org.hibernate.dialect.H2Dialect"); // use H2 dialect
		configuration.setProperty("hibernate.connection.url", "jdbc:h2:./exampledb"); // create database called "exampledb" in project directory (aka working directory)

		configuration.setProperty("hibernate.connection.username", ""); // empty username
		configuration.setProperty("hibernate.connection.password", ""); // empty password
		configuration.setProperty("hibernate.show_sql", "true"); // print SQL statements to stdout
		configuration.setProperty("hibernate.hbm2ddl.auto", "update"); // use update mode
						
		// tell Hibernate which classes represent database tables
		configuration.addAnnotatedClass(DBUser.class);
		configuration.addAnnotatedClass(DBPost.class);

		StandardServiceRegistryBuilder builder = new StandardServiceRegistryBuilder().applySettings(configuration.getProperties()); // apply default settings 
		return configuration.buildSessionFactory(builder.build()); // build the SessionFactory
	}
	
	/** 
	 * Get a DBUser from the user table by id
	 * @param id
	 * @return
	 * @throws DBNotFoundException 
	 */
	public static FlatUser getUserById(long id) throws DBNotFoundException {
		Session session = sessionFactory.openSession(); // open connection to database
		DBUser user = session.get(DBUser.class, id); // this is the important line, get the DBUser using the provided id
		if (user == null) throw new DBNotFoundException();
		FlatUser flatUser = user.flatten(); // make sure to call this *before* closing the session
		session.close(); // NEVER FORGET TO CLOSE THE SESSION!!!
		return flatUser; 
	}
	

	/**
	 * 
	 * @param session
	 * @param emailAddress
	 * @return null if not found
	 */
	private static DBUser getUserByEmail(Session session, String emailAddress) {
		
		CriteriaBuilder cb = session.getCriteriaBuilder(); // CriteriaBuilder is used to build criteria which will be used to query the database
		
		CriteriaQuery<DBUser> query = cb.createQuery(DBUser.class); // create a new query which will return results of type DBUser
		Root<DBUser> root = query.from(DBUser.class); // specify that we are querying the DBUser table (some complex queries require multiple roots)
		query.select(root).where(cb.equal(root.get("email"), emailAddress)); // select rows from DBUser table where "email" column is equal to emailAddress
		
		List<DBUser> result = session.createQuery(query).list(); // get the results of the query as a list
		
		if (result.size() == 0) return null;
		else return result.get(0); 
	}
	
	/** 
	 * Get a DBUser from the user table by email (use this code to query against non-primary-key columns
	 * @param email
	 * @return
	 * @throws DBNotFoundException 
	 */
	public static FlatUser getUserByEmail(String emailAddress) throws DBNotFoundException {
		Session session = sessionFactory.openSession();
		
		DBUser dbUser = getUserByEmail(session, emailAddress);
		
		if (dbUser == null) throw new DBNotFoundException();
		
		FlatUser user = dbUser.flatten(); // get the user from the result list (call flatten() before closing session)
		session.close(); // remember to close the session!
		return user;
	}
		
	public static void addUser(String emailAddress, String password, String displayName) throws DBCollisionException {
		Session session = sessionFactory.openSession();
		DBUser user = new DBUser(); // create DBUser object from scratch
		
		// set all the stuff (remember not to call setId(), as the Hibernate will generate that for us
		user.setEmail(emailAddress);
		user.setPassword(password);
		user.setName(displayName);
		user.setBgColor(0xFFFFFF);
		user.setTimestamp(System.currentTimeMillis());
		
		session.beginTransaction(); // you must begin a transaction when modifying the database
		session.save(user); // specifies that user should be saved to the database as a new row
		try {
			session.getTransaction().commit(); // commit the transaction to the database
		} catch (Exception e) {
			// ALWAYS put commit() in a try block and call rollback() if anything goes wrong. Hibernate will bitch at you if you a transaction is left open (open = neither committed nor rolled back)
			session.getTransaction().rollback(); 
			throw new DBCollisionException();
		}
		
		// the user is now saved in the database
	}
	
	public static List<FlatPost> getAllBlogPosts() {
		Session session = sessionFactory.openSession();
		
		CriteriaBuilder cb = session.getCriteriaBuilder(); 
		
		CriteriaQuery<DBPost> query = cb.createQuery(DBPost.class); 
		Root<DBPost> root = query.from(DBPost.class); 
		query.select(root); // with no where clause, the query will select the entire table (dangerous in production, fine for an example)
				
		List<DBPost> result = session.createQuery(query).list();
		
		List<FlatPost> list = result.stream().map(post->post.flatten()).collect(Collectors.toList()); // map DBPosts to FlatPosts, again, make sure to call flatten() before closing the session
		session.close(); // remember to close the session!
		return list;
	}
	
	//Retrieve post based on user ID
	public static FlatPost getPostById(long id) throws DBNotFoundException {
		Session session = sessionFactory.openSession();
		
		DBPost result = session.get(DBPost.class, id);
		
		if (result == null) throw new DBNotFoundException();
		
		FlatPost post = result.flatten();
		
		session.close();
		
		return post;
	}
	
	//add a post with a Title, Body, Message
	public static FlatPost addPost(String title, String body, String email, String password) throws DBNotFoundException, DBRollbackException {
		Session session = sessionFactory.openSession();
		
		DBUser user = session.createQuery("from DBUser user where user.email='" + email + "'", DBUser.class).uniqueResult();
		if (user==null) throw new DBNotFoundException();
		
		DBPost post = new DBPost();
		post.setAuthor(user);
		post.setBody(body);
		post.setDate(System.currentTimeMillis());
		post.setTitle(title);
		
		session.beginTransaction();
		long id;
		try {
			id = (long) session.save(post);
			session.getTransaction().commit();
		} catch (Exception e) {
			session.getTransaction().rollback();
			throw new DBRollbackException();
		}
		
		post.setId(id);
		
		FlatPost ret = new FlatPost(post);
		
		session.close();
		
		return ret;
	}
	
	//retrieve Posts based on the athor of the post
	public static List<FlatPost> getPostsByUserId(long id) {
		Session session = sessionFactory.openSession();
		
		List<DBPost> result = session.createQuery("from DBPost post where post.author.id="+id, DBPost.class).list(); // this is HQL, Hibernate Query Language. It's like SQL but simpler, specific to Hibernate, and works with any Hibernate-supported database
		
		List<FlatPost> list = result.stream().map(post->post.flatten()).collect(Collectors.toList());
		session.close(); 
		return list;
	}
	
	/**
	 * 
	 * @param email
	 * @param password
	 * @param color
	 * @return user's ID 
	 * @throws DBIncorrectPasswordException
	 * @throws DBRollbackException
	 * @throws DBNotFoundException
	 */
	public static long setBgColor(String email, String password, int color) throws DBIncorrectPasswordException, DBRollbackException, DBNotFoundException {
		Session session = sessionFactory.openSession();
		DBUser user = DB.getUserByEmail(session, email);
		
		if (user == null) throw new DBNotFoundException();
		
		if (!user.getPassword().equals(password)) throw new DBIncorrectPasswordException();
		
		long userID = user.getId();
		
		user.setBgColor(color);
		
		session.beginTransaction();
		session.merge(user);
		try {
			session.getTransaction().commit();
		} catch (Exception e) {
			session.getTransaction().rollback();
			throw new DBRollbackException();
		} finally {
			session.close();
		}
		
		return userID;
	}
	
	public static  List<FlatComment> getAllComments() {
		Session session = sessionFactory.openSession();
		
		CriteriaBuilder cb = session.getCriteriaBuilder(); 
		
		CriteriaQuery<DBComment> query = cb.createQuery(DBComment.class); 
		Root<DBComment> root = query.from(DBComment.class); 
		query.select(root); 
		List<DBComment> result = session.createQuery(query).list();
		
		List<FlatComment> list = result.stream().map(comment->comment.flatten()).collect(Collectors.toList()); // map DBComment to FlatComment, again, make sure to call flatten() before closing the session
		session.close(); 
		return list;
	}
	
	
	
	
	public static FlatComment getCommentById(long id) throws DBNotFoundException {
		Session session = sessionFactory.openSession();
		
		DBComment result = session.get(DBComment.class, id);
		
		if (result == null) throw new DBNotFoundException();
		
		FlatComment comment = result.flatten();
		
		session.close();
		
		return comment;
	}
	
	
	
	
	
	
	public static FlatComment addComment(String body, String email, String password, long postID) throws DBNotFoundException, DBRollbackException, DBIncorrectPasswordException{
		Session session = sessionFactory.openSession();
		
		DBUser user = session.createQuery("from DBUser user where user.email='" + email + "'", DBUser.class).uniqueResult();
		if (user==null) throw new DBNotFoundException();
		
		if (!user.getPassword().equals(password)) throw new DBIncorrectPasswordException();
		
		DBPost post = session.createQuery("from DBPost post where post.id="+postID, DBPost.class).uniqueResult();
		
		DBComment comment = new DBComment();
		comment.setAuthor(user);
		comment.setBody(body);
		comment.setDate(System.currentTimeMillis());
		comment.setPost(post);
		
		session.beginTransaction();
		long id;
		try {
			id = (long) session.save(comment);
			session.getTransaction().commit();
		} catch (Exception e) {
			session.getTransaction().rollback();
			throw new DBRollbackException();
		}
		
		comment.setId(id);
		
		FlatComment ret = new FlatComment(comment);
		
		session.close();
		
		return ret;
	}
	
	
	
	
	public static List<FlatComment> getCommentsByUserId(long id) {
		Session session = sessionFactory.openSession();
		
		List<DBComment> result = session.createQuery("from DBComment comment where comment.author.id="+id, DBComment.class).list(); 
		
		List<FlatComment> list = result.stream().map(comment->comment.flatten()).collect(Collectors.toList());
		session.close(); 
		return list;
	}
	
	/**
	 * Returns all comments on post with id
	 * @param postID id of the post for which to get comments
	 * @return
	 */
	public static List<FlatComment> getCommentsOnPost(long postID) {
		Session session = sessionFactory.openSession();
		List<DBComment> list = session.createQuery("from DBComment comment where comment.post.id=" + postID, DBComment.class).list();
		return list.stream().map(FlatComment::new).collect(Collectors.toList());
	}
	
	
	/******************************* Exceptions *******************************/
	
	public static class DBNotFoundException extends Exception {
		private static final long serialVersionUID = -3413135035628577683L;

		public DBNotFoundException() {
			super("not found in database");
		}
	}
	
	public static class DBCollisionException extends Exception {
		private static final long serialVersionUID = -3413135035628577683L;

		public DBCollisionException() {
			super("db collision detected");
		}
	}
	
	public static class DBRollbackException extends Exception {
		private static final long serialVersionUID = -3413135035628577683L;

		public DBRollbackException() {
			super("db commit failed, was rolled back");
		}
	}
	
	public static class DBIncorrectPasswordException extends Exception {
		private static final long serialVersionUID = -3413135035628577683L;

		public DBIncorrectPasswordException() {
			super("password incorrect, no changes made");
		}
	}
}