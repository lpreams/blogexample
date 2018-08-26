package example;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.Date;
import java.util.List;
import java.util.Scanner;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.core.Response;

import example.DB.DBNotFoundException;
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
		
		return Response.ok(page
				.replace("$BLOGPOSTTITLE", post.title)
				.replace("$BLOGPOSTBODY", post.body)
				.replace("$BLOGPOSTUSERID", Long.toString(post.author.id))
				.replace("$BLOGPOSTUSERNAME", post.author.name)
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
		
		return Response.ok(page
				.replace("$BLOGUSERNAME", user.name)
				.replace("$BLOGPOSTS", sb.toString())
			).build();
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
