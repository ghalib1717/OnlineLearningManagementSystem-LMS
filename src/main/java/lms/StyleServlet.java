package lms;

import jakarta.servlet.http.*;
import java.io.*;

/** Serves the stylesheet from the classpath. */
public class StyleServlet extends HttpServlet {
    @Override
    protected void doGet(HttpServletRequest q, HttpServletResponse r) throws IOException {
        r.setContentType("text/css;charset=UTF-8");
        r.setHeader("Cache-Control", "max-age=300");
        try (InputStream in = getClass().getResourceAsStream("/style.css")) {
            if (in == null) { r.sendError(404); return; }
            in.transferTo(r.getOutputStream());
        }
    }
}
