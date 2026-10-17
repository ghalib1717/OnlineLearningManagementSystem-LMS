package lms;

import jakarta.servlet.http.*;
import java.util.*;

/** Login, registration, logout and role-based home redirect. */
public class AuthServlet extends BaseServlet {
    @Override protected String requiredRole() { return null; }

    @Override
    protected void handle(HttpServletRequest q, HttpServletResponse r, Map<String, Object> u, String p) throws Exception {
        switch (p) {
            case "/login" -> {
                if (u != null) { r.sendRedirect("/"); return; }
                if ("POST".equals(q.getMethod())) login(q, r); else loginPage(q, r);
            }
            case "/register" -> {
                if (u != null) { r.sendRedirect("/"); return; }
                if ("POST".equals(q.getMethod())) register(q, r); else registerPage(q, r);
            }
            case "/logout" -> {
                if (u != null) ActivityLog.log(u.get("name") + " logged out");
                q.getSession().invalidate();
                r.sendRedirect("/login");
            }
            default -> r.sendRedirect(u == null ? "/login" : "/" + String.valueOf(u.get("user_role")).toLowerCase());
        }
    }

    private void loginPage(HttpServletRequest q, HttpServletResponse r) throws Exception {
        String f = input("Email", "email", "email", "", true) + input("Password", "password", "password", "", true);
        page(q, r, null, "Login", "<section class='card narrow'><h2>Sign in</h2>" + form("/login", f, "Login")
            + "<p class='muted'>New here? <a href='/register'>Create an account</a></p>"
            + "<p class='hint'><b>Demo:</b> admin@lms.com / admin123 &middot; instructor@lms.com / teach123 &middot; student@lms.com / learn123</p></section>");
    }

    private void login(HttpServletRequest q, HttpServletResponse r) throws Exception {
        Map<String, Object> user = Db.one("SELECT * FROM users WHERE email=? AND password=?", req(q, "email").toLowerCase(), Db.hash(req(q, "password")));
        if (user == null) throw new LmsException("Invalid email or password.");
        HttpSession old = q.getSession();
        old.invalidate();
        q.getSession(true).setAttribute("user", user);
        ActivityLog.log(user.get("name") + " (" + user.get("user_role") + ") logged in");
        r.sendRedirect("/" + String.valueOf(user.get("user_role")).toLowerCase());
    }

    private void registerPage(HttpServletRequest q, HttpServletResponse r) throws Exception {
        if (!"true".equals(Db.setting("allow_registration", "true"))) {
            page(q, r, null, "Register", "<section class='card narrow'><h2>Registration closed</h2><p>Self-registration is disabled by the administrator.</p><a class='btn' href='/login'>Back to login</a></section>");
            return;
        }
        String f = input("Full name", "name", "text", "", true) + input("Email", "email", "email", "", true)
            + input("Password (min 6 chars)", "password", "password", "", true)
            + select("I am a", "role", List.of(new String[]{"STUDENT", "Student"}, new String[]{"INSTRUCTOR", "Instructor"}), "STUDENT");
        page(q, r, null, "Register", "<section class='card narrow'><h2>Create account</h2>" + form("/register", f, "Register")
            + "<p class='muted'>Already registered? <a href='/login'>Sign in</a></p></section>");
    }

    private void register(HttpServletRequest q, HttpServletResponse r) throws Exception {
        if (!"true".equals(Db.setting("allow_registration", "true"))) throw new LmsException("Registration is closed.");
        String name = req(q, "name"), email = req(q, "email").toLowerCase(), pw = req(q, "password"), role = str(q, "role");
        if (!email.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")) throw new LmsException("Please enter a valid email address.");
        if (pw.length() < 6) throw new LmsException("Password must be at least 6 characters.");
        if (!role.equals("STUDENT") && !role.equals("INSTRUCTOR")) throw new LmsException("Invalid role.");
        Db.exec("INSERT INTO users(name,email,password,user_role) VALUES(?,?,?,?)", name, email, Db.hash(pw), role);
        ActivityLog.log("New " + role + " registered: " + email);
        ok(q, "Account created. Please sign in.");
        r.sendRedirect("/login");
    }
}
