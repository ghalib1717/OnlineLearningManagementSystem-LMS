package lms;

import jakarta.servlet.http.*;
import java.util.*;

/** Admin: user & course management, analytics, system settings and activity monitoring. */
public class AdminServlet extends BaseServlet {
    @Override protected String requiredRole() { return "ADMIN"; }

    @Override
    protected void handle(HttpServletRequest q, HttpServletResponse r, Map<String, Object> u, String p) throws Exception {
        switch (p) {
            case "/admin" -> dashboard(q, r, u);
            case "/admin/users" -> users(q, r, u);
            case "/admin/courses" -> courses(q, r, u);
            case "/admin/settings" -> settings(q, r, u);
            case "/admin/do/saveUser" -> saveUser(q, r, u);
            case "/admin/do/deleteUser" -> deleteUser(q, r, u);
            case "/admin/do/saveCourse" -> saveCourse(q, r, u);
            case "/admin/do/deleteCourse" -> deleteCourse(q, r, u);
            case "/admin/do/saveSettings" -> saveSettings(q, r, u);
            default -> r.sendRedirect("/admin");
        }
    }

    private void dashboard(HttpServletRequest q, HttpServletResponse r, Map<String, Object> u) throws Exception {
        Map<String, Object> c = Db.one("SELECT (SELECT COUNT(*) FROM users WHERE user_role='STUDENT') s, (SELECT COUNT(*) FROM users WHERE user_role='INSTRUCTOR') i, "
            + "(SELECT COUNT(*) FROM courses) c, (SELECT COUNT(*) FROM enrollments) e, (SELECT COUNT(*) FROM submissions) sub");
        StringBuilder b = new StringBuilder("<meta http-equiv='refresh' content='15'><h1>Admin Dashboard</h1><div class='stats'>")
            .append(stat(c.get("s"), "Students")).append(stat(c.get("i"), "Instructors")).append(stat(c.get("c"), "Courses"))
            .append(stat(c.get("e"), "Enrollments")).append(stat(c.get("sub"), "Submissions")).append("</div>");
        List<Map<String, Object>> rows = Db.rows("SELECT c.title, COUNT(DISTINCT e.student_id) n, "
            + "(SELECT AVG(s.grade*100.0/a.max_marks) FROM submissions s JOIN assignments a ON a.id=s.assignment_id WHERE a.course_id=c.id AND s.grade IS NOT NULL) g "
            + "FROM courses c LEFT JOIN enrollments e ON e.course_id=c.id GROUP BY c.id, c.title ORDER BY n DESC");
        long max = 1;
        for (Map<String, Object> m : rows) max = Math.max(max, Db.num(m.get("n")));
        List<String[]> t = new ArrayList<>();
        for (Map<String, Object> m : rows) {
            long n = Db.num(m.get("n"));
            t.add(new String[]{h(m.get("title")), n + "<div class='bar'><span style='width:" + (n * 100 / max) + "%'></span></div>", m.get("g") == null ? "—" : Math.round(Db.dbl(m.get("g"))) + "%"});
        }
        b.append(card("Performance Analytics", table(new String[]{"Course", "Enrolled students", "Avg grade"}, t)));
        List<String[]> logs = new ArrayList<>();
        for (Map<String, Object> m : Db.rows("SELECT ts, msg FROM activity ORDER BY id DESC LIMIT 15"))
            logs.add(new String[]{h(String.valueOf(m.get("ts")).substring(0, 19)), h(m.get("msg"))});
        b.append(card("System Activity Monitor <small class='muted'>(auto-refreshes)</small>", table(new String[]{"Time", "Event"}, logs)));
        page(q, r, u, "Admin Dashboard", b.toString());
    }

    private void users(HttpServletRequest q, HttpServletResponse r, Map<String, Object> u) throws Exception {
        Map<String, Object> e = q.getParameter("edit") == null ? null : Db.one("SELECT * FROM users WHERE id=?", num(q, "edit"));
        List<String[]> roles = List.of(new String[]{"STUDENT", "Student"}, new String[]{"INSTRUCTOR", "Instructor"}, new String[]{"ADMIN", "Admin"});
        String f = (e == null ? "" : hid("id", e.get("id"))) + input("Name", "name", "text", e == null ? "" : e.get("name"), true)
            + input("Email", "email", "email", e == null ? "" : e.get("email"), true)
            + input(e == null ? "Password (min 6)" : "New password (leave blank to keep)", "password", "password", "", e == null)
            + select("Role", "role", roles, e == null ? "STUDENT" : e.get("user_role"));
        List<String[]> t = new ArrayList<>();
        for (Map<String, Object> m : Db.rows("SELECT * FROM users ORDER BY id")) {
            String del = Db.num(m.get("id")) == uid(u) ? "" : miniForm("/admin/do/deleteUser", hid("id", m.get("id")), "Delete", "danger");
            t.add(new String[]{String.valueOf(m.get("id")), h(m.get("name")), h(m.get("email")), "<span class='tag'>" + m.get("user_role") + "</span>",
                "<a class='btn sm ghost' href='/admin/users?edit=" + m.get("id") + "'>Edit</a> " + del});
        }
        page(q, r, u, "Users", "<h1>User Management</h1>" + card(e == null ? "Add user" : "Edit user", form("/admin/do/saveUser", f, e == null ? "Create user" : "Save changes"))
            + card("All users", table(new String[]{"ID", "Name", "Email", "Role", "Actions"}, t)));
    }

    private void saveUser(HttpServletRequest q, HttpServletResponse r, Map<String, Object> u) throws Exception {
        String id = str(q, "id"), name = req(q, "name"), email = req(q, "email").toLowerCase(), pw = str(q, "password"), role = str(q, "role");
        if (!List.of("ADMIN", "INSTRUCTOR", "STUDENT").contains(role)) throw new LmsException("Invalid role.");
        if (!email.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")) throw new LmsException("Please enter a valid email address.");
        if (!pw.isEmpty() && pw.length() < 6) throw new LmsException("Password must be at least 6 characters.");
        if (id.isEmpty()) {
            if (pw.isEmpty()) throw new LmsException("Password is required for new users.");
            Db.exec("INSERT INTO users(name,email,password,user_role) VALUES(?,?,?,?)", name, email, Db.hash(pw), role);
        } else if (pw.isEmpty()) {
            Db.exec("UPDATE users SET name=?, email=?, user_role=? WHERE id=?", name, email, role, num(q, "id"));
        } else {
            Db.exec("UPDATE users SET name=?, email=?, user_role=?, password=? WHERE id=?", name, email, role, Db.hash(pw), num(q, "id"));
        }
        ActivityLog.log("Admin " + u.get("name") + " saved user " + email);
        ok(q, "User saved.");
        r.sendRedirect("/admin/users");
    }

    private void deleteUser(HttpServletRequest q, HttpServletResponse r, Map<String, Object> u) throws Exception {
        int id = num(q, "id");
        if (id == uid(u)) throw new LmsException("You cannot delete your own account.");
        Db.tx(c -> {
            Db.execTx(c, "DELETE FROM submissions WHERE student_id=?", id);
            Db.execTx(c, "DELETE FROM enrollments WHERE student_id=?", id);
            Db.execTx(c, "DELETE FROM users WHERE id=?", id);
        });
        ActivityLog.log("Admin " + u.get("name") + " deleted user #" + id);
        ok(q, "User deleted.");
        r.sendRedirect("/admin/users");
    }

    private void courses(HttpServletRequest q, HttpServletResponse r, Map<String, Object> u) throws Exception {
        Map<String, Object> e = q.getParameter("edit") == null ? null : Db.one("SELECT * FROM courses WHERE id=?", num(q, "edit"));
        List<String[]> ins = options(Db.rows("SELECT id, name FROM users WHERE user_role='INSTRUCTOR' ORDER BY name"));
        if (ins.isEmpty()) { page(q, r, u, "Courses", "<h1>Course Management</h1>" + card("No instructors", "<p>Create an instructor account first.</p>")); return; }
        String f = (e == null ? "" : hid("id", e.get("id"))) + input("Title", "title", "text", e == null ? "" : e.get("title"), true)
            + area("Description", "description", e == null ? "" : e.get("description")) + area("Syllabus", "syllabus", e == null ? "" : e.get("syllabus"))
            + select("Instructor", "instructor_id", ins, e == null ? "" : e.get("instructor_id"));
        List<String[]> t = new ArrayList<>();
        for (Map<String, Object> m : Db.rows("SELECT c.id, c.title, u.name inst, (SELECT COUNT(*) FROM enrollments e WHERE e.course_id=c.id) n FROM courses c JOIN users u ON u.id=c.instructor_id ORDER BY c.id"))
            t.add(new String[]{String.valueOf(m.get("id")), h(m.get("title")), h(m.get("inst")), String.valueOf(m.get("n")),
                "<a class='btn sm ghost' href='/admin/courses?edit=" + m.get("id") + "'>Edit</a> " + miniForm("/admin/do/deleteCourse", hid("id", m.get("id")), "Delete", "danger")});
        page(q, r, u, "Courses", "<h1>Course Management</h1>" + card(e == null ? "Create course" : "Edit course", form("/admin/do/saveCourse", f, e == null ? "Create course" : "Save changes"))
            + card("All courses", table(new String[]{"ID", "Title", "Instructor", "Students", "Actions"}, t)));
    }

    private void saveCourse(HttpServletRequest q, HttpServletResponse r, Map<String, Object> u) throws Exception {
        String title = req(q, "title");
        if (str(q, "id").isEmpty())
            Db.exec("INSERT INTO courses(title,description,syllabus,instructor_id) VALUES(?,?,?,?)", title, str(q, "description"), str(q, "syllabus"), num(q, "instructor_id"));
        else
            Db.exec("UPDATE courses SET title=?, description=?, syllabus=?, instructor_id=? WHERE id=?", title, str(q, "description"), str(q, "syllabus"), num(q, "instructor_id"), num(q, "id"));
        ActivityLog.log("Admin " + u.get("name") + " saved course '" + title + "'");
        ok(q, "Course saved.");
        r.sendRedirect("/admin/courses");
    }

    private void deleteCourse(HttpServletRequest q, HttpServletResponse r, Map<String, Object> u) throws Exception {
        Db.exec("DELETE FROM courses WHERE id=?", num(q, "id"));
        ActivityLog.log("Admin " + u.get("name") + " deleted course #" + str(q, "id"));
        ok(q, "Course deleted.");
        r.sendRedirect("/admin/courses");
    }

    private void settings(HttpServletRequest q, HttpServletResponse r, Map<String, Object> u) throws Exception {
        boolean allow = "true".equals(Db.setting("allow_registration", "true"));
        String f = input("Site name", "site_name", "text", Db.setting("site_name", "EduLearn LMS"), true)
            + select("Self-registration", "allow_registration", List.of(new String[]{"true", "Open"}, new String[]{"false", "Closed"}), allow);
        page(q, r, u, "Settings", "<h1>System Settings</h1>" + card("Configuration", form("/admin/do/saveSettings", f, "Update settings")));
    }

    private void saveSettings(HttpServletRequest q, HttpServletResponse r, Map<String, Object> u) throws Exception {
        String name = req(q, "site_name");
        Db.exec("MERGE INTO settings(k,v) KEY(k) VALUES('site_name',?)", name);
        Db.exec("MERGE INTO settings(k,v) KEY(k) VALUES('allow_registration',?)", "true".equals(str(q, "allow_registration")) ? "true" : "false");
        ActivityLog.log("Admin " + u.get("name") + " updated system settings");
        ok(q, "Settings updated.");
        r.sendRedirect("/admin/settings");
    }
}
