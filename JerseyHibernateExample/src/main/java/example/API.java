package example;

import java.net.URI;
import java.util.Date;
import java.util.List;
import java.util.Scanner;

import javax.ws.rs.CookieParam;
import javax.ws.rs.FormParam;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Cookie;
import javax.ws.rs.core.NewCookie;
import javax.ws.rs.core.Response;

import org.apache.commons.text.StringEscapeUtils;

import example.DB.DBIncorrectPasswordException;
import example.DB.DBNotFoundException;
import example.DB.DBPasswordMismatchException;
import example.DB.DBRollbackException;
import example.db.DBBlog.FlatBlog;
import example.db.DBComment.FlatComment;
import example.db.DBPost.FlatPost;
import example.db.DBUser.FlatUser;

@Path("/")
public class API {
	
	@GET
	@Path("/")
	public static Response homepage(@CookieParam("blogtoken") Cookie token) {
		FlatUser user = DB.getUserByToken(token);
		String page = textFileToString("index.html", user);
		List<FlatPost> posts = DB.getAllBlogPosts();
		StringBuilder sb = new StringBuilder();
		for (FlatPost post : posts) {
			sb.append("<p><h3><a href=/getpost/" + post.id + ">" + post.title + "</a></h3></p>"+System.lineSeparator());
			sb.append("<p>Posted by <a href=/getuser/" + post.blog.author.id + ">" + post.blog.author.name + "</a> on "+ new Date(post.date) +"</p>"+System.lineSeparator());
			sb.append("<br/>" + System.lineSeparator());
		}
		
		String createBlogButton="<p><a href=/createblog>Create a new blog</a></p><hr/>\n";
		if (user == null) createBlogButton = "";
		
		return Response.ok(page
				.replace("$CREATEBLOGPOSTBUTTON", createBlogButton)
				.replace("$BLOGPOSTS", sb)).build();
	}
	
	@GET
	@Path("/login")
	public static Response loginForm(@CookieParam("blogtoken") Cookie token) {
		if (DB.getUserByToken(token) != null) return Response.ok("Already logged in").build();
		
		return Response.ok(textFileToString("loginpage.html", null).replace("$MESSAGE", "")).build();
	}
	

	
	
	
	@POST
	@Path("/loginpost")
	public static Response loginPost(@CookieParam("blogtoken") Cookie token, @FormParam("email") String email, @FormParam("password") String password) {
		if (DB.getUserByToken(token) != null) return Response.ok("Already logged in").build();
		
		String newToken;
		try {
			newToken = DB.createLoginSession(email, password);
		} catch (DBIncorrectPasswordException | DBNotFoundException e) {
			return Response.ok(textFileToString("loginpage.html", null).replace("$MESSAGE","Incorrect email or password")).build();
		} catch (DBRollbackException e) {
			return Response.ok("Rollback exception (should never happen)").build();
		}
		NewCookie cookie = new NewCookie("blogtoken", newToken);
		
		return Response.seeOther(URI.create("/")).cookie(cookie).build(); // redirect to homepage on success
	}
	
	@GET
	@Path("/logout")
	public static Response logout(@CookieParam("blogtoken") Cookie token) {
		DB.deleteLoginSession(token);
		return Response.seeOther(URI.create("/")).build(); // redirect to homepage either way
	}
	
	@GET
	@Path("/getblog/{id}")
	public static Response getBlog(@CookieParam("blogtoken") Cookie token, @PathParam("id") long id) {
		FlatBlog blog;
		try {
			blog = DB.getBlogById(id);
		} catch (DBNotFoundException e) {
			return Response.ok("Not found: " + id).build();
		}
		FlatUser user = DB.getUserByToken(token);
		String page = textFileToString("blogpage.html", user);
		StringBuilder sb = new StringBuilder();
		if (user.id == blog.author.id) {
			sb.append("<p><a href=/createpost/" + id + ">Create new blog post</a></p><hr/>\n");
		}
		for (FlatPost post : blog.posts) {
			sb.append("<h2><a href=/getpost/" + post.id + ">" + escape(post.title) + "</a></h2>\n");
			sb.append("<p>Posted on "+new Date(post.date)+"</p>\n");
			sb.append("<p>" + escape(post.body) + "</p><hr/>\n");
		}
		
		StringBuilder bg = new StringBuilder();
		bg.append(Integer.toString(blog.author.bgColor, 16));
		while (bg.length() < 6) bg.insert(0, "0");
		return Response.ok(page.replace("$BLOGPOSTUSERID", Long.toString(blog.author.id))
			.replace("$BGCOLOR",bg.toString())
			.replace("$BLOGPOSTTITLE",escape(blog.title))
			.replace("$BLOGPOSTUSERNAME",escape(blog.author.name))
			.replace("$BLOGPOSTDATE", new Date(blog.date).toString())
			.replace("$BLOGPOSTBODY", sb.toString())).build();
	}
	
	@GET
	@Path("/getpost/{id}")
	public static Response getPost(@CookieParam("blogtoken") Cookie token, @PathParam("id") long id) {
		FlatPost post;
		String page = textFileToString("blogpost.html", DB.getUserByToken(token));
		try {
			post = DB.getPostById(id);
		} catch (DBNotFoundException e) {
			return Response.ok("Not found: " + id).build();
		}
		
		StringBuilder sb = new StringBuilder();
		List<FlatComment> list = DB.getCommentsOnPost(id);
		
		for (FlatComment com : list) {
			sb.append("<p>" + com.body + "<br/><br/>\n");
			sb.append("Posted by " + com.author.name + " on " + new Date(com.date) + "\n");
		}
		
		StringBuilder bg = new StringBuilder();
		bg.append(Integer.toString(post.blog.author.bgColor, 16));
		while (bg.length() < 6) bg.insert(0, "0");
		
		return Response.ok(page
				.replace("$BLOGPOSTTITLE", post.title)
				.replace("$BLOGPOSTBODY", post.body)
				.replace("$BLOGPOSTUSERID", Long.toString(post.blog.author.id))
				.replace("$BLOGPOSTUSERNAME", post.blog.author.name)
				.replace("$BLOGCOMMENTS", sb.toString())
				.replace("$POSTID", Long.toString(id))
				.replace("$BGCOLOR" , bg.toString())
				.replace("$BLOGPOSTDATE", new Date(post.date).toString())
			).build();
	}
	
	@GET
	@Path("/getuser/{id}")
	public static Response getUser(@CookieParam("blogtoken") Cookie token, @PathParam("id") long id) {
		FlatUser viewer = DB.getUserByToken(token);
		
		String page = textFileToString("userpage.html", DB.getUserByToken(token));
		FlatUser pageOwner;
		try {
			pageOwner = DB.getUserById(id);
		} catch (DBNotFoundException e) {
			return Response.ok("Not found: " + id).build();
		}
		
		List<FlatBlog> blogs = DB.getBlogsByUserId(id);
		List<FlatPost> posts = DB.getPostsByUserId(id);
		
		StringBuilder sb = new StringBuilder();
		sb.append("<h2>Blogs</h2>\n");
		for (FlatBlog blog : blogs) {
			sb.append("<p><h3><a href=/getblog/" + blog.id + ">" + blog.title + "</a></h3>"+System.lineSeparator());
			sb.append("<br />Created on "+ new Date(blog.date) +"</p>"+System.lineSeparator());
		}
		
		
		
		sb.append("<hr/><h2>Posts</h2>\n");
		for (FlatPost post : posts) {
			sb.append("<p><h3><a href=/getpost/" + post.id + ">" + post.title + "</a></h3></p>"+System.lineSeparator());
			sb.append("<p>Posted on "+ new Date(post.date) +"</p>"+System.lineSeparator());
			sb.append("<br/>" + System.lineSeparator());
		}
		
		StringBuilder bg = new StringBuilder();
		bg.append(Integer.toString(pageOwner.bgColor, 16));
		while (bg.length() < 6) bg.insert(0, "0");
		
		String colorChangeForm = "<form action= \"/changebgcolor\" method=\"post\">\n" + 
				"	<p>Background Color: <input type=\"color\" name=\"bgcolor\" value=\"#FFFFFF\" /> <!--<input type= \"text\" name= \"bgcolor\" size=\"100\" maxlength=\"255\" />--></p>\n" + 
				"	<input type= \"submit\" value= \"Submit\"/>\n" + 
				"</form>";
		if (viewer == null) colorChangeForm = ""; 
		else if (viewer.id != pageOwner.id) colorChangeForm = ""; 
		
		return Response.ok(page
				.replace("$COLORCHANGEFORM",colorChangeForm)
				.replace("$BLOGUSERNAME", pageOwner.name)
				.replace("$BGCOLOR" , bg.toString())
				.replace("$BLOGPOSTS", sb.toString())
			).build();
	}
	
	@GET
	@Path("/createaccount")
	public static Response createAccountGet(@CookieParam("blogtoken") Cookie token) {
		if (DB.getUserByToken(token) != null) return Response.ok("Already logged in").build();
		
		return Response.ok(textFileToString("createaccount.html", null)).build();
	}
	
	@POST
	@Path("/createaccount") 
	public static Response createAccountPost(@CookieParam("blogtoken") Cookie token, @FormParam("email") String email, @FormParam("name") String name, @FormParam("password1") String password1, @FormParam("password2") String password2) {
		if (DB.getUserByToken(token) != null) return Response.ok("You are already logged in to an account").build();
		if (password1.compareTo(password2) != 0) return Response.ok("passwords do not match").build();
		String newToken;
		try {
			newToken = DB.addUser(email, password1, name);
		} catch (DBRollbackException e) {
			return Response.ok("email address already in use").build(); 
		}
		
		NewCookie cookie = new NewCookie("blogtoken", newToken);
		
		return Response.seeOther(URI.create("/")).cookie(cookie).build(); // redirect to homepage on success
	}
	
	@POST
	@Path("/changebgcolor") 
	public static Response changebgColor(@CookieParam("blogtoken") Cookie token, @FormParam("bgcolor") String bgColor) {
		if (bgColor.startsWith("#")) bgColor = bgColor.substring(1, bgColor.length());
		int intColor;
		try {
			intColor = Integer.parseInt(bgColor, 16);
		} catch (NumberFormatException e) {
			return Response.ok("Not a hex number: " + bgColor).build();
		}
		
		FlatUser user;
		
		try {
			
			user = DB.setBgColor(token, intColor);
		} catch (DBRollbackException e) {
			return Response.ok("Rollback exception (should never happen)").build();
		} catch (DBNotFoundException e) {
			return Response.ok("You must be logged in to do that").build();
		}
		
		return Response.seeOther(URI.create("/getuser/" + user.id)).build(); // redirect to homepage on success
	}
	
	@GET
	@Path("/createblog")
	public static Response createBlogGet(@CookieParam("blogtoken") Cookie token) {
		FlatUser user = DB.getUserByToken(token);
		if (user == null) return Response.ok("You must be logged in to do that").build();
		return Response.ok(textFileToString("createblog.html", user)).build();
	}
	
	@POST
	@Path("/createblog") 
	public static Response createBlogPost(@CookieParam("blogtoken") Cookie token, @FormParam("title") String title) {
		FlatBlog blog;
		try {
			blog = DB.addBlog(token, title);
		} catch (DBNotFoundException | DBRollbackException e) {
			return Response.ok("You must be logged in to do that").build();
		} 
		
		return Response.seeOther(URI.create("/getblog/" + blog.id)).build(); // redirect to the new post
	}
	
	
	@GET
	@Path("/createpost/{blogid}")
	public static Response createPostGet(@CookieParam("blogtoken") Cookie token, @PathParam("blogid") long blogid) {
		FlatUser user = DB.getUserByToken(token);
		FlatBlog blog;
		try {
			blog = DB.getBlogById(blogid);
		} catch (DBNotFoundException e) {
			return Response.ok("Not found: " + blogid).build();
		}
		
		
		
		if (user == null) return Response.ok("You must be logged in to do that").build();
		return Response.ok(textFileToString("createpost.html", user)
				.replace("$BLOGTITLE", escape(blog.title))
				.replace("$BLOGID", Long.toString(blog.id))
			).build();
	}
	
	@POST
	@Path("/createpost/{blogid}") 
	public static Response createPostPost(@CookieParam("blogtoken") Cookie token, @PathParam("blogid") long blogid, @FormParam("title") String title, @FormParam("body") String body) {
		FlatPost post;
		try {
			post = DB.addPost(token, blogid, title, body);
		} catch (DBNotFoundException e) {
			return Response.ok("You must be logged in to do that").build();
		} catch (DBRollbackException e) {
			return Response.ok(e.getMessage()).build();
		}
		
		return Response.seeOther(URI.create("/getpost/" + post.id)).build(); // redirect to the new post
	}
	
	@POST
	@Path("/addcomment/{id}")
	public static Response addComment(@CookieParam("blogtoken") Cookie token, @PathParam("id") long id, @FormParam("body") String body) {
		try {
			DB.addComment(token, body, id);
		} catch (DBNotFoundException e) {
			return Response.ok("You must be logged in to do that").build();
		} catch (DBRollbackException e) {
			return Response.ok("Rollback (should never happen)").build();
		}
		return Response.seeOther(URI.create("/getpost/"+id)).build();
	}
	
	@GET
	@Path("/search")
	public static Response search(@CookieParam("blogtoken") Cookie token, @QueryParam("q") String query) {
		FlatUser user = DB.getUserByToken(token);
		if (query==null || query.length() == 0) return Response.ok(textFileToString("search.html", user).replace("$SEARCHRESULTS", "")).build();
		
		List<FlatPost> result = DB.searchPosts(query);
		StringBuilder sb = new StringBuilder();
		
		for (FlatPost post : result) {
			sb.append("<p><h3><a href=/getpost/" + post.id + ">" + post.title + "</a></h3></p>"+System.lineSeparator());
			sb.append("<p>Posted on "+ new Date(post.date) +"</p>"+System.lineSeparator());
			sb.append("<br/>" + System.lineSeparator());
		}
		
		return Response.ok(textFileToString("search.html", user).replace("$SEARCHRESULTS", sb.toString())).build();
	}
	
	@GET
	@Path("/report")
	public static Response reportform(@CookieParam("blogtoken") Cookie token) {
		FlatUser user = DB.getUserByToken(token);
		if (user == null) return Response.ok("You must be logged in to do that").build();
		
		return Response.ok(textFileToString("report.html", user).replace("$MESSAGE","")).build();
	}
	
	@POST
	@Path("/report")
	public static Response submitreport(@CookieParam("blogtoken") Cookie token, @FormParam("phone") String phone, @FormParam("comments") String suggestion) {
		
		try {
			FlatUser user = DB.submitReport(token, suggestion);
			return Response.ok(textFileToString("report.html", user).replace("$MESSAGE","Report submitted successfully")).build();
		} catch (DBNotFoundException e) {
			return Response.ok("You must be logged in to do that").build();
		} catch (DBRollbackException e) {
			return Response.ok("Rollback exception (should never happen)").build();
		}
	}
		
	@Path("/useraccount")
	public static Response useraccountform(@CookieParam("blogtoken") Cookie token) {
		FlatUser user = DB.getUserByToken(token);
		if (user==null) return Response.ok("You must be logged in to do that").build();
		
		return Response.ok(API.textFileToString("useraccountform.html", user).replace("$MESSAGE", "")).build();
	}
	
	@POST
	@Path("/changepassword")
	public static Response changepassword(@CookieParam("blogtoken") Cookie token, @FormParam("password") String password, @FormParam("password1") String password1, @FormParam("password2") String password2) {
		//FlatUser user = DB.getUserByToken(token);
		//if (user==null) return Response.ok("You must be logged in to do that").build();
		
		try {
			FlatUser user = DB.changeUserPassword(token, password, password1, password2);
			return Response.ok(API.textFileToString("useraccountform.html", user).replace("$MESSAGE", "Password successfully updated!")).build();
		} catch (DBNotFoundException e) {
			return Response.ok("You must be logged in to do that").build();
		} catch (DBIncorrectPasswordException e) {
			return Response.ok(API.textFileToString("useraccountform.html", e.user).replace("$MESSAGE", "Incorrect current password, password NOT changed.")).build();
		} catch (DBPasswordMismatchException e) {
			return Response.ok(API.textFileToString("useraccountform.html", e.user).replace("$MESSAGE", "Passwords did not match, password NOT changed")).build();
		} catch (DBRollbackException e) {
			return Response.ok("Rollback exception (should never happen)").build();
		}
	}
	
	/**
	 * Convert a text file in src/main/resources to a String
	 * @param filename file to retrieve
	 * @param user FlatUser so the loginbox can be inserted
	 * @return
	 */
	private static String textFileToString(String filename, FlatUser user) {
		StringBuilder sb = new StringBuilder();

		try (Scanner scan = new Scanner(Thread.currentThread().getContextClassLoader().getResourceAsStream(filename))) {
			while (scan.hasNextLine()) sb.append(scan.nextLine() + System.lineSeparator());
		} 
		
		StringBuilder loginBox = new StringBuilder();
		loginBox.append("<div style=\"outline: 1px solid; float: right; text-align: center; margin: 2px; padding: 2px; max-width: 40%;\">\n");
		loginBox.append("<h2><a href=/>Blog Project</a></h2>\n");
		if (user == null) loginBox.append("<p><a href=/login>Log in</a></p><p><a href=/createaccount>Create account</a></p>");
		else loginBox.append("<p>Logged in as <a href=/useraccount>" + escape(user.name) + "</a></p><p><a href=/logout>Log out</a></p>\n");
		loginBox.append("</div>");
		return sb.toString().replace("$LOGINBOX", loginBox);
	}
	
	/**
	 * Escape a string so it can be safely inserted into HTML
	 * @param text text to escape
	 * @return HTML-escaped text
	 */
	private static String escape(String string) {
		return StringEscapeUtils.escapeHtml4(string);
	}
}
