package lms;

import jakarta.servlet.http.*;
import java.io.IOException;
import java.util.*;

/** Parent of all servlets: session/role checks, error handling, page layout and HTML helpers. */
public abstract class BaseServlet extends HttpServlet {
    /** Role required to access this servlet, or null if it is public. */
    protected abstract String requiredRole();

    protected abstract void handle(HttpServletRequest q, HttpServletResponse r, Map<String, Object> u, String path) throws Exception;

    @Override
    @SuppressWarnings("unchecked")
    protected void service(HttpServletRequest q, HttpServletResponse r) throws IOException {
        q.setCharacterEncoding("UTF-8");
        HttpSession s = q.getSession();
        Map<String, Object> u = (Map<String, Object>) s.getAttribute("user");
        String role = requiredRole();
        if (role != null && (u == null || !role.equals(u.get("user_role")))) { r.sendRedirect("/login"); return; }
        String path = q.getServletPath() + (q.getPathInfo() == null ? "" : q.getPathInfo());
        if (path.length() > 1 && path.endsWith("/")) path = path.substring(0, path.length() - 1);
        if (path.contains("/do/") && !"POST".equals(q.getMethod())) { r.sendRedirect("/"); return; }
        try {
            handle(q, r, u, path);
        } catch (LmsException e) {
            s.setAttribute("flash", "✖ " + e.getMessage());
            String ref = q.getHeader("Referer");
            r.sendRedirect(ref != null ? ref : "/");
        } catch (Exception e) {
            e.printStackTrace();
            if (!r.isCommitted()) {
                r.setStatus(500);
                r.setContentType("text/html;charset=UTF-8");
                r.getWriter().write("<h3>Something went wrong.</h3><p>" + h(e.getMessage()) + "</p><a href='/'>Home</a>");
            }
        }
    }

    // ---------- layout ----------
    protected void page(HttpServletRequest q, HttpServletResponse r, Map<String, Object> u, String title, String body) throws Exception {
        r.setContentType("text/html;charset=UTF-8");
        HttpSession s = q.getSession();
        String site = Db.setting("site_name", "EduLearn LMS");
        String cur = q.getServletPath() + (q.getPathInfo() == null ? "" : q.getPathInfo());
        StringBuilder b = new StringBuilder("<!DOCTYPE html><html lang='en'><head><meta charset='UTF-8'>")
            .append("<meta name='viewport' content='width=device-width,initial-scale=1'><title>").append(h(title)).append(" | ").append(h(site))
            .append("</title><link rel='stylesheet' href='/style.css'></head><body>");
        if (u != null) {
            String role = (String) u.get("user_role");
            String base = "/" + role.toLowerCase();
            String[][] nav = switch (role) {
                case "ADMIN" -> new String[][]{{base, "Dashboard"}, {base + "/users", "Users"}, {base + "/courses", "Courses"}, {base + "/settings", "Settings"}};
                case "INSTRUCTOR" -> new String[][]{{base, "My Courses"}};
                default -> new String[][]{{base, "My Learning"}, {base + "/browse", "Browse Courses"}};
            };
            b.append("<header class='top'><div class='brand'>🎓 ").append(h(site)).append("</div><nav>");
            for (String[] n : nav) b.append("<a href='").append(n[0]).append("'").append(cur.equals(n[0]) ? " class='active'" : "").append(">").append(n[1]).append("</a>");
            b.append("</nav><div class='who'>").append(h(u.get("name"))).append(" <span class='tag'>").append(role).append("</span> <a class='btn sm ghost' href='/logout'>Logout</a></div></header><main>");
        } else {
            b.append("<main class='auth'><div class='logo'>🎓</div><h1 class='site'>").append(h(site)).append("</h1>");
        }
        Object flash = s.getAttribute("flash");
        if (flash != null) {
            s.removeAttribute("flash");
            b.append("<div class='flash ").append(flash.toString().startsWith("✔") ? "ok" : "err").append("'>").append(h(flash)).append("</div>");
        }
        b.append(body).append("</main><footer>").append(h(site)).append(" &middot; Java Servlets + JDBC + H2 &middot; Galgotias University</footer></body></html>");
        r.getWriter().write(b.toString());
    }

    protected static void ok(HttpServletRequest q, String msg) { q.getSession().setAttribute("flash", "✔ " + msg); }

    // ---------- request helpers ----------
    protected static String str(HttpServletRequest q, String k) { String v = q.getParameter(k); return v == null ? "" : v.trim(); }

    protected static String req(HttpServletRequest q, String k) throws LmsException {
        String v = str(q, k);
        if (v.isEmpty()) throw new LmsException("Please fill in the '" + k.replace('_', ' ') + "' field.");
        return v;
    }

    protected static int num(HttpServletRequest q, String k) throws LmsException {
        try { return Integer.parseInt(str(q, k)); } catch (NumberFormatException e) { throw new LmsException("Invalid or missing value for '" + k + "'."); }
    }

    protected static int uid(Map<String, Object> u) { return ((Number) u.get("id")).intValue(); }

    // ---------- HTML helpers ----------
    protected static String h(Object o) {
        if (o == null) return "";
        return o.toString().replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&#39;");
    }

    protected static String card(String title, String inner) { return "<section class='card'><h2>" + title + "</h2>" + inner + "</section>"; }

    protected static String stat(Object value, String label) { return "<div class='stat'><b>" + value + "</b><span>" + label + "</span></div>"; }

    protected static String bar(long pct) {
        pct = Math.max(0, Math.min(100, pct));
        return "<div class='bar'><span style='width:" + pct + "%'></span><b>" + pct + "%</b></div>";
    }

    protected static String hid(String k, Object v) { return "<input type='hidden' name='" + k + "' value='" + h(v) + "'>"; }

    protected static String input(String label, String name, String type, Object val, boolean required) {
        return "<label>" + label + "<input type='" + type + "' name='" + name + "' value='" + h(val) + "'" + (required ? " required" : "") + "></label>";
    }

    protected static String area(String label, String name, Object val) {
        return "<label>" + label + "<textarea name='" + name + "' rows='4'>" + h(val) + "</textarea></label>";
    }

    protected static String select(String label, String name, List<String[]> options, Object selected) {
        StringBuilder b = new StringBuilder("<label>" + label + "<select name='" + name + "'>");
        for (String[] o : options) b.append("<option value='").append(h(o[0])).append("'").append(o[0].equals(String.valueOf(selected)) ? " selected" : "").append(">").append(h(o[1])).append("</option>");
        return b.append("</select></label>").toString();
    }

    protected static String form(String action, String inner, String button) {
        return "<form method='post' action='" + action + "'>" + inner + "<button class='btn'>" + button + "</button></form>";
    }

    protected static String miniForm(String action, String hidden, String label, String cls) {
        return "<form method='post' action='" + action + "' class='inline'>" + hidden + "<button class='btn sm " + cls + "'>" + label + "</button></form>";
    }

    protected static String table(String[] heads, List<String[]> rows) {
        if (rows.isEmpty()) return "<p class='muted'>Nothing to show yet.</p>";
        StringBuilder b = new StringBuilder("<div class='scroll'><table><tr>");
        for (String x : heads) b.append("<th>").append(x).append("</th>");
        b.append("</tr>");
        for (String[] row : rows) {
            b.append("<tr>");
            for (String c : row) b.append("<td>").append(c).append("</td>");
            b.append("</tr>");
        }
        return b.append("</table></div>").toString();
    }

    /** Shared progress/performance query: progress = submitted/total assignments, avg = mean graded percentage. */
    protected static final String PERF =
        "SELECT st.name student, c.id cid, c.title title, "
        + "(SELECT COUNT(*) FROM assignments a WHERE a.course_id=c.id) total, "
        + "(SELECT COUNT(*) FROM submissions s JOIN assignments a ON a.id=s.assignment_id WHERE a.course_id=c.id AND s.student_id=e.student_id) done, "
        + "(SELECT AVG(s.grade*100.0/a.max_marks) FROM submissions s JOIN assignments a ON a.id=s.assignment_id WHERE a.course_id=c.id AND s.student_id=e.student_id AND s.grade IS NOT NULL) avg "
        + "FROM enrollments e JOIN users st ON st.id=e.student_id JOIN courses c ON c.id=e.course_id ";

    protected static long progress(Map<String, Object> m) {
        long total = Db.num(m.get("total"));
        return total == 0 ? 0 : Db.num(m.get("done")) * 100 / total;
    }

    protected static String avgText(Map<String, Object> m) { return m.get("avg") == null ? "—" : Math.round(Db.dbl(m.get("avg"))) + "%"; }

    protected static List<String[]> options(List<Map<String, Object>> rows) {
        List<String[]> o = new ArrayList<>();
        for (Map<String, Object> m : rows) o.add(new String[]{String.valueOf(m.get("id")), String.valueOf(m.get("name"))});
        return o;
    }
}
