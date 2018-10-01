package example;

import java.io.File;
import java.io.FileNotFoundException;
import java.net.URI;
import java.util.Date;
import java.util.List;
import java.util.Scanner;

import javax.ws.rs.FormParam;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.core.Response;

import example.DB.DBCollisionException;
import example.DB.DBIncorrectPasswordException;
import example.DB.DBNotFoundException;
import example.DB.DBRollbackException;
import example.db.DBComment.FlatComment;
import example.db.DBPost.FlatPost;
import example.db.DBUser.FlatUser;

@Path("/")
public class API {
	
	@GET
	@Path("/")
	public static Response homepage() {
		String page = textFileToString("index.html");
		List<FlatPost> posts = DB.getAllBlogPosts();
		StringBuilder sb = new StringBuilder();
		for (FlatPost post : posts) {
			sb.append("<p><h3><a href=/getpost/" + post.id + ">" + post.title + "</a></h3></p>"+System.lineSeparator());
			sb.append("<p>Posted by <a href=/getuser/" + post.author.id + ">" + post.author.name + "</a> on "+ new Date(post.date) +"</p>"+System.lineSeparator());
			sb.append("<br/>" + System.lineSeparator());
		}
		return Response.ok(page.replace("$BLOGPOSTS", sb)).build();
	}
	
	@GET
	@Path("/getpost/{id}")
	public static Response getPost(@PathParam("id") long id) {
		FlatPost post;
		String page = textFileToString("blogpost.html");
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

		
		return Response.ok(page
				.replace("$BLOGPOSTTITLE", post.title)
				.replace("$BLOGPOSTBODY", post.body)
				.replace("$BLOGPOSTUSERID", Long.toString(post.author.id))
				.replace("$BLOGPOSTUSERNAME", post.author.name)
				.replace("$BLOGCOMMENTS", sb.toString())
				.replace("$POSTID", Long.toString(id))
				.replace("$BLOGPOSTDATE", new Date(post.date).toString())
			).build();
	}
	
	@GET
	@Path("/getuser/{id}")
	public static Response getUser(@PathParam("id") long id) {
		String page = textFileToString("userpage.html");
		FlatUser user;
		try {
			user = DB.getUserById(id);
		} catch (DBNotFoundException e) {
			return Response.ok("Not found: " + id).build();
		}
		
		List<FlatPost> posts = DB.getPostsByUserId(id);
		
		StringBuilder sb = new StringBuilder();
		for (FlatPost post : posts) {
			sb.append("<p><h3><a href=/getpost/" + post.id + ">" + post.title + "</a></h3></p>"+System.lineSeparator());
			sb.append("<p>Posted on "+ new Date(post.date) +"</p>"+System.lineSeparator());
			sb.append("<br/>" + System.lineSeparator());
		}
		
		StringBuilder bg = new StringBuilder();
		bg.append(Integer.toString(user.bgColor, 16));
		while (bg.length() < 6) bg.insert(0, "0");
		
		return Response.ok(page
				.replace("$BLOGUSERNAME", user.name)
				.replace("$BGCOLOR" , bg.toString())
				.replace("$BLOGPOSTS", sb.toString())
			).build();
	}
	
	@GET
	@Path("/createaccount")
	public static Response createAccountGet() {
		return Response.ok(textFileToString("createaccount.html")).build();
	}
	
	@POST
	@Path("/createaccount") 
	public static Response createAccountPost(@FormParam("email") String email, @FormParam("name") String name, @FormParam("password1") String password1, @FormParam("password2") String password2) {
		if (password1.compareTo(password2) != 0) return Response.ok("passwords do not match").build();
		try {
			DB.addUser(email, password1, name);
		} catch (DBCollisionException e) {
			return Response.ok("email address already in use").build(); 
		}
		return Response.seeOther(URI.create("/")).build(); // redirect to homepage on success
	}
	
	@POST
	@Path("/changebgcolor") 
	public static Response changebgColor(@FormParam("email") String email, @FormParam("password") String password, @FormParam("bgcolor") String bgColor) {
		int intColor;
		try {
			intColor = Integer.parseInt(bgColor, 16);
		} catch (NumberFormatException e) {
			return Response.ok("Not a hex number: " + bgColor).build();
		}
		
		long userID;
		
		try {
			userID = DB.setBgColor(email, password, intColor);
		} catch (DBIncorrectPasswordException e) {
			return Response.ok("Incorrect password").build();
		} catch (DBRollbackException e) {
			return Response.ok("Rollback exception (should never happen)").build();
		} catch (DBNotFoundException e) {
			return Response.ok("Email address not found: " + email).build();
		}
		
		return Response.seeOther(URI.create("/getuser/" + userID)).build(); // redirect to homepage on success
	}
	
	
	@GET
	@Path("/createpost")
	public static Response createPostGet() {
		return Response.ok(textFileToString("createpost.html")).build();
	}
	
	@POST
	@Path("/createpost") 
	public static Response createPostPost(@FormParam("title") String title, @FormParam("body") String body, @FormParam("email") String email, @FormParam("password") String password) {
		FlatPost post;
		try {
			post = DB.addPost(title, body, email, password);
		} catch (DBNotFoundException e) {
			return Response.ok("Incorrect email or password").build();
		} catch (DBRollbackException e) {
			return Response.ok(e.getMessage()).build();
		}
		
		return Response.seeOther(URI.create("/getpost/" + post.id)).build(); // redirect to the new post
	}
	
	@POST
	@Path("/addcomment/{id}")
	public static Response addComment(@PathParam("id") long id, @FormParam("email") String email, @FormParam("password") String password, @FormParam("body") String body) {
		try {
			DB.addComment(body, email, password, id);
		} catch (DBNotFoundException e) {
			return Response.ok("Email not found").build();
		} catch (DBRollbackException e) {
			return Response.ok("Rollback (should never happen)").build();
		} catch (DBIncorrectPasswordException e) {
			return Response.ok("Incorrect password").build();
		}
		return Response.seeOther(URI.create("/getpost/"+id)).build();
	}
	
	
	/**
	 * Convert a text file in src/main/resources to a String
	 * @param filename
	 * @return
	 */
	private static String textFileToString(String filename) {
		StringBuilder sb = new StringBuilder();
		
		ClassLoader classLoader = API.class.getClassLoader();
		File file = new File(classLoader.getResource(filename).getFile());

		try (Scanner scan = new Scanner(file)) {
			while (scan.hasNextLine()) sb.append(scan.nextLine() + System.lineSeparator());
		} catch (FileNotFoundException e) {
			throw new RuntimeException("File not found: " + filename); // fail-fast
		}
		return sb.toString();
	}
}
