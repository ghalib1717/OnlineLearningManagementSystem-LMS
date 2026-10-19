package lms;

import jakarta.servlet.http.*;
import java.util.*;

public class AuthServlet extends BaseServlet {

    @Override
    protected String requiredRole() {
        return null;
    }

    @Override
    protected void handle(
            HttpServletRequest q,
            HttpServletResponse r,
            Map<String, Object> u,
            String p
    ) throws Exception {

        switch (p) {

            case "/login" -> {
                if ("POST".equals(q.getMethod())) {
                    login(q, r);
                } else {
                    loginPage(r);
                }
            }

            case "/register" -> {
                if ("POST".equals(q.getMethod())) {
                    register(q, r);
                } else {
                    registerPage(q, r);
                }
            }

            case "/logout" -> {
                if (u != null) {
                    ActivityLog.log(
                            u.get("name") + " logged out"
                    );
                }

                HttpSession session = q.getSession(false);

                if (session != null) {
                    session.invalidate();
                }

                r.sendRedirect("/login.html");
            }

            default -> {
                if (u == null) {
                    r.sendRedirect("/login.html");
                } else {
                    String role = String.valueOf(
                            u.get("user_role")
                    ).toLowerCase();

                    r.sendRedirect("/" + role);
                }
            }
        }
    }

    // ---------- Login page ----------

    private void loginPage(HttpServletResponse r)
            throws Exception {

        r.sendRedirect("/login.html");
    }

    // ---------- Login backend ----------

    private void login(
            HttpServletRequest q,
            HttpServletResponse r
    ) throws Exception {

        String email = req(q, "email").toLowerCase();
        String password = req(q, "password");

        Map<String, Object> user = Db.one(
                "SELECT * FROM users WHERE email=? AND password=?",
                email,
                Db.hash(password)
        );

        if (user == null) {
            throw new LmsException(
                    "Invalid email or password."
            );
        }

        // Invalidate the previous session after authentication.
        HttpSession oldSession = q.getSession(false);

        if (oldSession != null) {
            oldSession.invalidate();
        }

        // Create a new authenticated session.
        HttpSession newSession = q.getSession(true);
        newSession.setAttribute("user", user);

        ActivityLog.log(
                user.get("name") + " ("
                + user.get("user_role") + ") logged in"
        );

        String role = String.valueOf(
                user.get("user_role")
        ).toLowerCase();

        r.sendRedirect("/" + role);
    }

    // ---------- Registration page ----------

    private void registerPage(
            HttpServletRequest q,
            HttpServletResponse r
    ) throws Exception {

        if (!"true".equals(
                Db.setting("allow_registration", "true"))) {

            page(
                    q,
                    r,
                    null,
                    "Registration Closed",
                    "<section class='card narrow'>"
                    + "<h2>Registration closed</h2>"
                    + "<p>Self-registration is disabled "
                    + "by the administrator.</p>"
                    + "<a class='btn' href='/login.html'>"
                    + "Back to login</a>"
                    + "</section>"
            );

            return;
        }

        // Use the existing HTML registration form.
        r.sendRedirect("/register.html");
    }

    // ---------- Registration backend ----------

    private void register(
            HttpServletRequest q,
            HttpServletResponse r
    ) throws Exception {

        if (!"true".equals(
                Db.setting("allow_registration", "true"))) {

            throw new LmsException(
                    "Registration is closed."
            );
        }

        String name = req(q, "name");
        String email = req(q, "email").toLowerCase();
        String password = req(q, "password");
        String role = str(q, "role");

        if (!email.matches(
                "^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")) {

            throw new LmsException(
                    "Please enter a valid email address."
            );
        }

        if (password.length() < 6) {
            throw new LmsException(
                    "Password must be at least 6 characters."
            );
        }

        if (!role.equals("STUDENT")
                && !role.equals("INSTRUCTOR")) {

            throw new LmsException("Invalid role.");
        }

        Db.exec(
                "INSERT INTO users(name,email,password,user_role) "
                + "VALUES(?,?,?,?)",
                name,
                email,
                Db.hash(password),
                role
        );

        ActivityLog.log(
                "New " + role + " registered: " + email
        );

        ok(q, "Account created. Please sign in.");

        r.sendRedirect("/login.html");
    }
}