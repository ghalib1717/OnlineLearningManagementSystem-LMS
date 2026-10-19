package lms;

import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.servlet.*;
import org.eclipse.jetty.server.handler.ResourceHandler;
import org.eclipse.jetty.server.handler.HandlerList;
import org.eclipse.jetty.util.resource.Resource;

public class App {

    public static void main(String[] args) throws Exception {

        int port = args.length > 0
                ? Integer.parseInt(args[0])
                : 8080;

        Db.init();

        ActivityLog.start();
        ActivityLog.log("System started");

        // Configure static frontend files
        ResourceHandler staticHandler = new ResourceHandler();

        staticHandler.setBaseResource(
                Resource.newClassPathResource("/webapp/")
        );

        staticHandler.setWelcomeFiles(new String[]{"index.html"});

        // Configure application routes
        ServletContextHandler ctx =
                new ServletContextHandler(ServletContextHandler.SESSIONS);

        ctx.setContextPath("/");

        ctx.addServlet(
                new ServletHolder(new StyleServlet()),
                "/style.css"
        );

        ctx.addServlet(
                new ServletHolder(new AdminServlet()),
                "/admin/*"
        );

        ctx.addServlet(
                new ServletHolder(new InstructorServlet()),
                "/instructor/*"
        );

        ctx.addServlet(
                new ServletHolder(new StudentServlet()),
                "/student/*"
        );

        ctx.addServlet(
                new ServletHolder(new AuthServlet()),
                "/"
        );

        // Serve static files before application routes
        Server server = new Server(port);

        HandlerList handlers = new HandlerList();
        handlers.addHandler(staticHandler);
        handlers.addHandler(ctx);

        server.setHandler(handlers);

        server.start();

        System.out.println(
                "LMS running at http://localhost:" + port
        );

        server.join();
    }
}