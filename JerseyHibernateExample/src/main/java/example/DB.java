package example;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Properties;
import java.util.Random;
import java.util.stream.Collectors;

import javax.mail.Authenticator;
import javax.mail.Message;
import javax.mail.PasswordAuthentication;
import javax.mail.Transport;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;
import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.Root;
import javax.ws.rs.core.Cookie;

import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.hibernate.cfg.Configuration;

import example.db.DBBlog;
import example.db.DBBlog.FlatBlog;
import example.db.DBComment;
import example.db.DBComment.FlatComment;
import example.db.DBLoginSession;
import example.db.DBPost;
import example.db.DBPost.FlatPost;
import example.db.DBReport;
import example.db.DBSiteSetting;
import example.db.DBUser;
import example.db.DBUser.FlatUser;

public class DB {
	
	private static SessionFactory sessionFactory;
	
	/**
	 * Start the database connection
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
		configuration.addAnnotatedClass(DBComment.class);
		configuration.addAnnotatedClass(DBLoginSession.class);
		configuration.addAnnotatedClass(DBBlog.class);
		configuration.addAnnotatedClass(DBReport.class);
		configuration.addAnnotatedClass(DBSiteSetting.class);

		StandardServiceRegistryBuilder builder = new StandardServiceRegistryBuilder().applySettings(configuration.getProperties()); // apply default settings 
		return configuration.buildSessionFactory(builder.build()); // build the SessionFactory
	}
	
	/**
	 * @param token token from user
	 * @return FlatUser containing user, or null if user does not exist ** IMPORTANT, DOES NOT THROW EXCEPTION **
	 */
	public static FlatUser getUserByToken(Cookie token) {
		Session session = sessionFactory.openSession(); // open connection to database
		DBUser user = getUserByToken(session, token);
		if (user == null) return null;
		FlatUser flatUser = user.flatten();
		session.close();
		return flatUser;
	}
	
	/**
	 * 
	 * @param token token from user
	 * @param session database session
	 * @return FlatUser containing user, or null if user does not exist ** IMPORTANT, DOES NOT THROW EXCEPTION **
	 */
	private static DBUser getUserByToken(Session session, Cookie token) {
		if (token == null) return null;
		if (token.getValue() == null) return null;
		if (token.getValue().trim().length() == 0) return null;
		
		List<DBLoginSession> list = session.createQuery("from DBLoginSession ls where ls.token='" + token.getValue() + "'",DBLoginSession.class).list(); 
		if (list.size() == 0) return null;
		DBUser user = list.get(0).getUser(); 
		try {
			session.beginTransaction();
			list.get(0).setLastActivity(System.currentTimeMillis());
			session.merge(list.get(0));
			session.getTransaction().commit();
		} catch (Exception e) {
			session.getTransaction().rollback();
			//throw new DBRollbackException(); // don't throw a rollback exception here, just because it's not that important, and doing so would make code everywhere else ugly
		}
		return user;
	}
	
	/**
	 * Creates a new login session
	 * @param email email address of user
	 * @param password password of user, to be tested for correctness
	 * @return String containing new login token
	 * @throws DBNotFoundException  if the email address does not exist in the database
	 * @throws DBIncorrectPasswordException if the provided password is incorrect
	 * @throws DBRollbackException if the database was rolled back (should never happend) 
	 */
	public static String createLoginSession(String email, String password) throws DBNotFoundException, DBIncorrectPasswordException, DBRollbackException {
		Session session = sessionFactory.openSession();
		
		DBUser user = getUserByEmail(session, email);
		if (user == null) throw new DBNotFoundException(); // check user exists
		if (!user.getPassword().equals(password)) throw new DBIncorrectPasswordException(); // check password
		
		DBLoginSession login = createLoginSession(session, user);
		String token = login.getToken();
		
		session.close();
		return token;
	}
	
	/**
	 * Deletes a login session (ie logs the user out)
	 * @param token token from user, to be deleted
	 */
	public static void deleteLoginSession(Cookie token) {
		Session session = sessionFactory.openSession();
		
		List<DBLoginSession> list = session.createQuery("from DBLoginSession ls where ls.token='" + token.getValue() + "'",DBLoginSession.class).list(); 
		try {
			session.beginTransaction();
			for (DBLoginSession login : list) session.delete(login);
			session.getTransaction().commit();
		} catch (Exception e) {
			session.getTransaction().rollback();
		}
		
		session.close();
	}
	
	/**
	 * Creates a new login session for the user
	 * @param session database session
	 * @param user user to log in
	 * @return DBLoginSession containing login token
	 * @throws DBRollbackException if the database was rolled back (should never happend) 
	 */
	private static DBLoginSession createLoginSession(Session session, DBUser user) throws DBRollbackException {
		DBLoginSession login = new DBLoginSession();
		login.setLastActivity(System.currentTimeMillis());
		login.setToken(generateLoginToken(session));
		login.setUser(user);
		try {
			session.beginTransaction();
			session.save(login);
			session.getTransaction().commit();
		} catch (Exception e) {
			session.getTransaction().rollback();
			throw new DBRollbackException();
		}
		return login;
	}

	/**
	 * Get a user from the database by email address
	 * @param session database session
	 * @param emailAddress address of user
	 * @return DBUser, or null if email not in database
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
	 * Get a user from the user table by email
	 * @param email user's email address
	 * @return FlatUser of user
	 * @throws DBNotFoundException if email address not in database
	 */
	public static FlatUser getUserByEmail(String emailAddress) throws DBNotFoundException {
		Session session = sessionFactory.openSession();
		
		DBUser dbUser = getUserByEmail(session, emailAddress);
		
		if (dbUser == null) throw new DBNotFoundException();
		
		FlatUser user = dbUser.flatten(); // get the user from the result list (call flatten() before closing session)
		session.close(); // remember to close the session!
		return user;
	}
		
	/**
	 * Add a new user to the database
	 * @param emailAddress new user's email address
	 * @param password new user's password
	 * @param displayName new user's dislpay name
	 * @return newly-created login token
	 * @throws DBRollbackException when email already exists
	 */
	public static String addUser(String emailAddress, String password, String displayName) throws DBRollbackException {
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
			throw new DBRollbackException();
		}
		
		DBLoginSession login = DB.createLoginSession(session, user);
		
		return login.getToken();
		
		// the user is now saved in the database
	}
	
	/**
	 * Get all posts posted to the website, in order from newest to oldest
	 * @return list of posts
	 */
	public static List<FlatPost> getAllBlogPosts() {
		Session session = sessionFactory.openSession();
		
		CriteriaBuilder cb = session.getCriteriaBuilder(); 
		
		CriteriaQuery<DBPost> query = cb.createQuery(DBPost.class); 
		Root<DBPost> root = query.from(DBPost.class); 
		query.select(root).orderBy(cb.desc(root.get("date"))); // with no where clause, the query will select the entire table (dangerous in production, fine for an example)
				
		List<DBPost> result = session.createQuery(query).list();
		
		List<FlatPost> list = result.stream().map(post->post.flatten()).collect(Collectors.toList()); // map DBPosts to FlatPosts, again, make sure to call flatten() before closing the session
		session.close(); // remember to close the session!
		return list;
	}
	
	public static class BlogPostWithComments {
		public final FlatPost post;
		public final List<FlatComment> comments;
		public BlogPostWithComments(FlatPost post, List<FlatComment> comments) {
			this.post = post;
			this.comments = comments;
		}
	}
	
	/**
	 * Get a blog post by its id, plus comments on it
	 * @param id id of blog post
	 * @return BlogPostWithComments containing FlatPost and List<FlatComments>
	 * @throws DBNotFoundException if id does not exist in the database
	 */
	public static BlogPostWithComments getPostById(long id) throws DBNotFoundException {
		Session session = sessionFactory.openSession();
		
		DBPost result = session.get(DBPost.class, id);
		if (result == null) throw new DBNotFoundException();
		FlatPost post = result.flatten();
		
		List<FlatComment> comments = session.createQuery("from DBComment comment where comment.post.id=" + id, DBComment.class)
				.stream().map(comment->comment.flatten()).collect(Collectors.toList());
		
		session.close();
		
		return new BlogPostWithComments(post, comments) ;
	}
	
	/**
	 * Get a blog by its id
	 * @param id id of blog
	 * @return FlatBlog 
	 * @throws DBNotFoundException if id does not exist in database
	 */
	public static FlatBlog getBlogById(long id) throws DBNotFoundException {
		Session session = sessionFactory.openSession();
		
		DBBlog result = session.get(DBBlog.class, id);
		
		if (result == null) throw new DBNotFoundException();
		
		FlatBlog blog = new FlatBlog(result, session);
		
		session.close();
		
		return blog;
	}
	
	/**
	 * Create a new blog for the user
	 * @param token token from user
	 * @param title title of new blog
	 * @return FlatBlog representation of new blog
	 * @throws DBNotFoundException if user token does not exist in database (user not logged in)
	 * @throws DBRollbackException if database was rolled back (should never happen)
	 */
	public static FlatBlog addBlog(Cookie token, String title) throws DBNotFoundException, DBRollbackException {
		Session session = sessionFactory.openSession();
		
		DBUser user = getUserByToken(session, token);
		if (user == null) throw new DBNotFoundException();
		
		DBBlog blog = new DBBlog();
		blog.setAuthor(user);
		blog.setDate(System.currentTimeMillis());
		blog.setTitle(title);
		
		session.beginTransaction();
		long id;
		try {
			id = (long) session.save(blog);
			session.getTransaction().commit();
		} catch (Exception e) {
			session.getTransaction().rollback();
			throw new DBRollbackException();
		}
		
		blog.setId(id);
		
		
		FlatBlog ret = blog.flatten();
		
		session.close();
		
		return ret;
	}
	
	/**
	 * Add a new blog post
	 * @param token token from user
	 * @param blogid id of blog to add to
	 * @param title title of new post
	 * @param body body of new post
	 * @return FlatPost of new post
	 * @throws DBNotFoundException if user token or blogid does not exist in database
	 * @throws DBRollbackException if database was rolled back
	 */
	public static FlatPost addPost(Cookie token, long blogid, String title, String body) throws DBNotFoundException, DBRollbackException {
		Session session = sessionFactory.openSession();
		
		DBUser user = getUserByToken(session, token);
		if (user == null) throw new DBNotFoundException();
		
		DBBlog blog = session.get(DBBlog.class, blogid);
		if (blog == null) throw new DBNotFoundException();
		
		if (blog.getAuthor().getId() != user.getId()) throw new DBNotFoundException();
		
		DBPost post = new DBPost();
		post.setBlog(blog);
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
		
		
		FlatPost ret = post.flatten();
		
		session.close();
		
		return ret;
	}
	
	public static class UserProfileResult {
		public final FlatUser user;
		public final List<FlatBlog> blogs;
		public final List<FlatPost> posts;
		public UserProfileResult(FlatUser user, List<FlatBlog> blogs, List<FlatPost> posts) {
			this.user = user;
			this.blogs = blogs;
			this.posts = posts;
		}
	}
	
	/**
	 * Get a user profile by user id
	 * @param id id of user to get profile for
	 * @return UserProfileResult containing a FlatUser of the user, list of blogs, and list of posts
	 * @throws DBNotFoundException
	 */
	public static UserProfileResult getUserProfile(long id) throws DBNotFoundException {
		Session session = sessionFactory.openSession();
		
		DBUser user = session.get(DBUser.class, id); 
		if (user == null) throw new DBNotFoundException();

		List<FlatPost> posts = session.createQuery("from DBPost post where post.blog.author.id="+id, DBPost.class)
				.stream().map(post->post.flatten()).collect(Collectors.toList());
		
		List<FlatBlog> blogs = session.createQuery("from DBBlog blog where blog.author.id="+id, DBBlog.class)
				.stream().map(post->post.flatten()).collect(Collectors.toList()); 
		
		session.close();
		return new UserProfileResult(user.flatten(), blogs, posts);
	}
	
	/**
	 * Set the background color for a user
	 * @param token token from user
	 * @param color new color (ARGB)
	 * @return FlatUser of user
	 * @throws DBRollbackException should never happen
	 * @throws DBNotFoundException token does not exist in database (not logged in)
	 */
	public static FlatUser setBgColor(Cookie token, int color) throws DBRollbackException, DBNotFoundException {
		Session session = sessionFactory.openSession();
		DBUser user = DB.getUserByToken(session, token);
		
		if (user == null) throw new DBNotFoundException();
		
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
		
		return user.flatten();
	}
	
	/**
	 * Add a comment on a blog post
	 * @param token token from user
	 * @param body comment body
	 * @param postID id of post being commented on
	 * @return FlatComment of comment
	 * @throws DBNotFoundException if token or postID does not exist in database
	 * @throws DBRollbackException should never happen
	 */
	public static FlatComment addComment(Cookie token, String body, long postID) throws DBNotFoundException, DBRollbackException {
		Session session = sessionFactory.openSession();
		
		DBUser user = getUserByToken(session, token);
		if (user==null) throw new DBNotFoundException();
				
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
	
	/**
	 * Search blog posts for a search query
	 * @param search search query
	 * @return list of FlatPosts which match search query
	 */
	public static List<FlatPost> searchPosts(String search) {
		Session session = sessionFactory.openSession();
		
		search = "%" + search.toLowerCase().replace("\\s+", "%") + "%";
		
		String hql = "from DBPost post where lower(post.body) like :search OR lower(post.title) like :search";
		List<FlatPost> list = session.createQuery(hql, DBPost.class).setParameter("search", search).stream()
				.map(post->post.flatten()).collect(Collectors.toList());
				
		session.close();
		
		return list;
	}
	
	/**
	 * Submit a user report
	 * @param token token from user
	 * @param suggestion body of user report
	 * @throws DBNotFoundException if user not found
	 * @throws DBRollbackException should not happen
	 */
	public static FlatUser submitReport(Cookie token, String suggestion) throws DBNotFoundException, DBRollbackException {
		Session session = sessionFactory.openSession();
		
		DBUser user = DB.getUserByToken(session, token);
		if (user == null) throw new DB.DBNotFoundException();
		
		DBReport report = new DBReport();
		report.setAuthor(user);
		report.setBody(suggestion);
		report.setDate(System.currentTimeMillis());
		
		try {
			session.beginTransaction();
			session.save(report);
			session.getTransaction().commit();
		} catch (Exception e) {
			session.getTransaction().rollback();
			//System.err.println(e);
			e.printStackTrace();
			throw new DB.DBRollbackException();
		}
		FlatUser result = user.flatten();
		
		session.close();
		
		return result;
	}
	
	/**
	 * (attempt to) change a user's password
	 * @param token 
	 * @param password current password, to be checked
	 * @param password1 new password
	 * @param password2 confirmation of new password
	 * @return FlatUser 
	 * @throws DBNotFoundException if token does not connect to a user
	 * @throws DBIncorrectPasswordException if password does not match the one in the database
	 * @throws DBPasswordMismatchException if password1 != password2
	 * @throws DBRollbackException rollback 
	 */
	public static FlatUser changeUserPassword(Cookie token, String password, String password1, String password2) throws DBNotFoundException, DBIncorrectPasswordException, DBPasswordMismatchException, DBRollbackException {
		Session session = sessionFactory.openSession();
		DBUser user = DB.getUserByToken(session, token);
		if (user == null) throw new DBNotFoundException();
		
		if (!password.equals(user.getPassword())) throw new DBIncorrectPasswordException(user.flatten());
		if (!password1.equals(password2)) throw new DBPasswordMismatchException(user.flatten());
		
		session.beginTransaction();
		try {
			user.setPassword(password1);
			session.merge(user);
			session.getTransaction().commit();
		} catch (Exception e) {
			session.getTransaction().rollback();
			throw new DBRollbackException();
		}
		
		FlatUser fuser = user.flatten();
		session.close(); 
		return fuser;
	}
	
    /**
     * Send an email
     * @param toAddress
     * @param subject
     * @param body
     * @return whether it sent successfully or not
     */
	public static boolean sendEmail(String toAddress, String subject, String body, Session session) {
		String email = session.get(DBSiteSetting.class, "SMTP_EMAIL").getValue();
		String password = session.get(DBSiteSetting.class, "SMTP_PASSWORD").getValue();
		String server = session.get(DBSiteSetting.class, "SMTP_SERVER").getValue();
		
		if (email==null || password == null || server == null) return false;
		
		Properties props = new Properties();
		props.put("mail.smtp.host", server);
		props.put("mail.smtp.port", "587");
		props.put("mail.smtp.auth", "true");
		props.put("mail.smtp.starttls.enable", "true");
		Authenticator auth = new Authenticator() {
			protected PasswordAuthentication getPasswordAuthentication() {
				return new PasswordAuthentication(email, password);
			}
		};
		try {
			MimeMessage msg = new MimeMessage(javax.mail.Session.getInstance(props, auth));
			msg.addHeader("Content-type", "text/HTML; charset=UTF-8");
			msg.addHeader("format", "flowed");
			msg.addHeader("Content-Transfer-Encoding", "8bit");
			msg.setFrom(new InternetAddress(email, "Blog Project"));
			msg.setReplyTo(InternetAddress.parse(email, false));
			msg.setSubject(subject, "UTF-8");
			msg.setText(body, "UTF-8", "html");
			msg.setSentDate(new Date());
			msg.setRecipients(Message.RecipientType.TO, InternetAddress.parse(toAddress, false));
			Transport.send(msg);
		} catch (Exception e) {
			return false;
		}
		return true;
	}
	
	/*private static void emailSetup() {
		DB.startDatabaseConnection();
		Session session = DB.sessionFactory.openSession();
		
		DBSiteSetting email = new DBSiteSetting();
		email.setKey("SMTP_EMAIL");
		email.setValue("your.email.address@example.com");
		
		DBSiteSetting password = new DBSiteSetting();
		password.setKey("SMTP_PASSWORD");
		password.setValue("ThIsIsYoUrEmAiLpAsSwOrD");
		
		DBSiteSetting server = new DBSiteSetting();
		server.setKey("SMTP_SERVER");
		server.setValue("smtp.example.com");
		
		try {
			session.beginTransaction();
			session.save(email);
			session.save(password);
			session.save(server);
			session.getTransaction().commit();
		} catch (Exception e) {
			session.getTransaction().rollback();
			System.err.println("Failed to set email settings");
		}
		
		session.close();
		System.exit(0);
	}*/
	
	/**
	 * Generate a unique login token
	 * @param session database session
	 * @return new login token
	 */
	private static String generateLoginToken(Session session) {
		List<Character> list = new ArrayList<>();
		Random r = new Random();
		for (char c='a'; c<='z'; ++c) list.add(c);
		for (char c='A'; c<='Z'; ++c) list.add(c);
		for (char c='0'; c<='9'; ++c) list.add(c);
		while (true) {
			StringBuilder sb = new StringBuilder();
			for (int i=0; i<200; ++i) sb.append(list.get(r.nextInt(list.size())));
			if (session.createQuery("from DBLoginSession where token='" + sb.toString() + "'").list().size() == 0) return sb.toString();
		}
	}
	
	/******************************* Exceptions *******************************/
	
	public static class DBNotFoundException extends Exception {
		private static final long serialVersionUID = -3413135035628577683L;

		public DBNotFoundException() {
			super("not found in database");
		}
	}
	
	public static class DBPasswordMismatchException extends Exception {
		private static final long serialVersionUID = -3413135035628577683L;
		public final FlatUser user;

		public DBPasswordMismatchException(FlatUser user) {
			super("password mismatch");
			this.user = user;
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
		public final FlatUser user;
		public DBIncorrectPasswordException() {
			super("password incorrect, no changes made");
			user = null;
		}
		
		public DBIncorrectPasswordException(FlatUser user) {
			super("password incorrect, no changes made");
			this.user = user;
		}
	}
}