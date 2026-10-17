package lms;

import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.servlet.*;

/** Entry point: initialises the database, starts the activity thread and the embedded Jetty server. */
public class App {
    public static void main(String[] args) throws Exception {
        int port = args.length > 0 ? Integer.parseInt(args[0]) : 8080;
        Db.init();
        ActivityLog.start();
        ActivityLog.log("System started");

        ServletContextHandler ctx = new ServletContextHandler(ServletContextHandler.SESSIONS);
        ctx.setContextPath("/");
        ctx.addServlet(new ServletHolder(new StyleServlet()), "/style.css");
        ctx.addServlet(new ServletHolder(new AdminServlet()), "/admin/*");
        ctx.addServlet(new ServletHolder(new InstructorServlet()), "/instructor/*");
        ctx.addServlet(new ServletHolder(new StudentServlet()), "/student/*");
        ctx.addServlet(new ServletHolder(new AuthServlet()), "/");

        Server server = new Server(port);
        server.setHandler(ctx);
        server.start();
        System.out.println("LMS running at http://localhost:" + port);
        server.join();
    }
}
