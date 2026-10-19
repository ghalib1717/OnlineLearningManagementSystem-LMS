package lms;

import jakarta.servlet.http.*;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class AdminServlet extends BaseServlet {

    @Override
    protected String requiredRole() {
        return "ADMIN";
    }

    @Override
    protected void handle(
            HttpServletRequest q,
            HttpServletResponse r,
            Map<String, Object> u,
            String p
    ) throws Exception {

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

    // ---------- HTML template helper ----------

    private String readTemplate(String file) throws IOException {
        try (InputStream in = getClass()
                .getClassLoader()
                .getResourceAsStream("webapp/" + file)) {

            if (in == null) {
                throw new IOException(
                        "Cannot find webapp/" + file
                        + ". Check src/main/webapp and rebuild."
                );
            }

            return new String(
                    in.readAllBytes(),
                    StandardCharsets.UTF_8
            );
        }
    }

    // ---------- Admin dashboard ----------

    private void dashboard(
            HttpServletRequest q,
            HttpServletResponse r,
            Map<String, Object> u
    ) throws Exception {

        Map<String, Object> c = Db.one(
                "SELECT "
                + "(SELECT COUNT(*) FROM users WHERE user_role='STUDENT') s, "
                + "(SELECT COUNT(*) FROM users WHERE user_role='INSTRUCTOR') i, "
                + "(SELECT COUNT(*) FROM courses) c, "
                + "(SELECT COUNT(*) FROM enrollments) e, "
                + "(SELECT COUNT(*) FROM submissions) sub"
        );

        List<Map<String, Object>> rows = Db.rows(
                "SELECT c.title, COUNT(DISTINCT e.student_id) n, "
                + "(SELECT AVG(s.grade*100.0/a.max_marks) "
                + "FROM submissions s "
                + "JOIN assignments a ON a.id=s.assignment_id "
                + "WHERE a.course_id=c.id AND s.grade IS NOT NULL) g "
                + "FROM courses c "
                + "LEFT JOIN enrollments e ON e.course_id=c.id "
                + "GROUP BY c.id, c.title ORDER BY n DESC"
        );

        long max = 1;

        for (Map<String, Object> m : rows) {
            max = Math.max(max, Db.num(m.get("n")));
        }

        List<String[]> performance = new ArrayList<>();

        for (Map<String, Object> m : rows) {
            long n = Db.num(m.get("n"));

            String enrollmentBar =
                    n + "<div class='bar'><span style='width:"
                    + (n * 100 / max)
                    + "%'></span></div>";

            String grade = m.get("g") == null
                    ? "—"
                    : Math.round(Db.dbl(m.get("g"))) + "%";

            performance.add(new String[]{
                h(m.get("title")),
                enrollmentBar,
                grade
            });
        }

        String performanceTable = table(
                new String[]{
                    "Course",
                    "Enrolled students",
                    "Avg grade"
                },
                performance
        );

        List<String[]> logs = new ArrayList<>();

        for (Map<String, Object> m : Db.rows(
                "SELECT ts, msg FROM activity ORDER BY id DESC LIMIT 15")) {

            String timestamp = String.valueOf(m.get("ts"));

            if (timestamp.length() > 19) {
                timestamp = timestamp.substring(0, 19);
            }

            logs.add(new String[]{
                h(timestamp),
                h(m.get("msg"))
            });
        }

        String activityTable = table(
                new String[]{"Time", "Event"},
                logs
        );

        String html = readTemplate("admin-dashboard.html")
                .replace("{{ADMIN_NAME}}", h(u.get("name")))
                .replace("{{STUDENT_COUNT}}", h(c.get("s")))
                .replace("{{INSTRUCTOR_COUNT}}", h(c.get("i")))
                .replace("{{COURSE_COUNT}}", h(c.get("c")))
                .replace("{{ENROLLMENT_COUNT}}", h(c.get("e")))
                .replace("{{SUBMISSION_COUNT}}", h(c.get("sub")))
                .replace("{{PERFORMANCE_TABLE}}", performanceTable)
                .replace("{{ACTIVITY_TABLE}}", activityTable);

        r.setContentType("text/html;charset=UTF-8");
        r.getWriter().write(html);
    }

    // ---------- User management ----------

    private void users(
            HttpServletRequest q,
            HttpServletResponse r,
            Map<String, Object> u
    ) throws Exception {

        Map<String, Object> e = q.getParameter("edit") == null
                ? null
                : Db.one(
                        "SELECT * FROM users WHERE id=?",
                        num(q, "edit")
                );

        String name = e == null
                ? ""
                : String.valueOf(e.get("name"));

        String email = e == null
                ? ""
                : String.valueOf(e.get("email"));

        String role = e == null
                ? "STUDENT"
                : String.valueOf(e.get("user_role"));

        String html = readTemplate("admin-users.html")
                .replace("{{ADMIN_NAME}}", h(u.get("name")))
                .replace(
                        "{{FORM_TITLE}}",
                        e == null ? "Add user" : "Edit user"
                )
                .replace(
                        "{{HIDDEN_ID}}",
                        e == null ? "" : hid("id", e.get("id"))
                )
                .replace("{{USER_NAME}}", h(name))
                .replace("{{USER_EMAIL}}", h(email))
                .replace(
                        "{{PASSWORD_LABEL}}",
                        e == null
                                ? "Password (min 6)"
                                : "New password (leave blank to keep)"
                )
                .replace(
                        "{{PASSWORD_REQUIRED}}",
                        e == null ? "required" : ""
                )
                .replace(
                        "{{STUDENT_SELECTED}}",
                        role.equals("STUDENT") ? "selected" : ""
                )
                .replace(
                        "{{INSTRUCTOR_SELECTED}}",
                        role.equals("INSTRUCTOR") ? "selected" : ""
                )
                .replace(
                        "{{ADMIN_SELECTED}}",
                        role.equals("ADMIN") ? "selected" : ""
                )
                .replace(
                        "{{FORM_BUTTON}}",
                        e == null ? "Create user" : "Save changes"
                )
                .replace("{{USERS_TABLE}}", buildUsersTable(u));

        r.setContentType("text/html;charset=UTF-8");
        r.getWriter().write(html);
    }

    private String buildUsersTable(
            Map<String, Object> u
    ) throws Exception {

        List<String[]> rows = new ArrayList<>();

        for (Map<String, Object> m : Db.rows(
                "SELECT * FROM users ORDER BY id")) {

            String deleteButton = Db.num(m.get("id")) == uid(u)
                    ? ""
                    : miniForm(
                            "/admin/do/deleteUser",
                            hid("id", m.get("id")),
                            "Delete",
                            "danger"
                    );

            rows.add(new String[]{
                String.valueOf(m.get("id")),
                h(m.get("name")),
                h(m.get("email")),
                "<span class='tag'>"
                        + h(m.get("user_role"))
                        + "</span>",
                "<a class='btn sm ghost' href='/admin/users?edit="
                        + m.get("id")
                        + "'>Edit</a> "
                        + deleteButton
            });
        }

        return table(
                new String[]{
                    "ID", "Name", "Email", "Role", "Actions"
                },
                rows
        );
    }

    private void saveUser(
            HttpServletRequest q,
            HttpServletResponse r,
            Map<String, Object> u
    ) throws Exception {

        String id = str(q, "id");
        String name = req(q, "name");
        String email = req(q, "email").toLowerCase();
        String pw = str(q, "password");
        String role = str(q, "role");

        if (!List.of("ADMIN", "INSTRUCTOR", "STUDENT").contains(role)) {
            throw new LmsException("Invalid role.");
        }

        if (!email.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")) {
            throw new LmsException(
                    "Please enter a valid email address."
            );
        }

        if (!pw.isEmpty() && pw.length() < 6) {
            throw new LmsException(
                    "Password must be at least 6 characters."
            );
        }

        if (id.isEmpty()) {
            if (pw.isEmpty()) {
                throw new LmsException(
                        "Password is required for new users."
                );
            }

            Db.exec(
                    "INSERT INTO users(name,email,password,user_role) "
                    + "VALUES(?,?,?,?)",
                    name, email, Db.hash(pw), role
            );

        } else if (pw.isEmpty()) {
            Db.exec(
                    "UPDATE users SET name=?, email=?, user_role=? WHERE id=?",
                    name, email, role, num(q, "id")
            );

        } else {
            Db.exec(
                    "UPDATE users SET name=?, email=?, user_role=?, password=? "
                    + "WHERE id=?",
                    name, email, role, Db.hash(pw), num(q, "id")
            );
        }

        ActivityLog.log(
                "Admin " + u.get("name") + " saved user " + email
        );

        ok(q, "User saved.");
        r.sendRedirect("/admin/users");
    }

    private void deleteUser(
            HttpServletRequest q,
            HttpServletResponse r,
            Map<String, Object> u
    ) throws Exception {

        int id = num(q, "id");

        if (id == uid(u)) {
            throw new LmsException(
                    "You cannot delete your own account."
            );
        }

        Db.tx(c -> {
            Db.execTx(
                    c,
                    "DELETE FROM submissions WHERE student_id=?",
                    id
            );

            Db.execTx(
                    c,
                    "DELETE FROM enrollments WHERE student_id=?",
                    id
            );

            Db.execTx(
                    c,
                    "DELETE FROM users WHERE id=?",
                    id
            );
        });

        ActivityLog.log(
                "Admin " + u.get("name") + " deleted user #" + id
        );

        ok(q, "User deleted.");
        r.sendRedirect("/admin/users");
    }

    // ---------- Course management ----------

    private void courses(
            HttpServletRequest q,
            HttpServletResponse r,
            Map<String, Object> u
    ) throws Exception {

        Map<String, Object> e = q.getParameter("edit") == null
                ? null
                : Db.one(
                        "SELECT * FROM courses WHERE id=?",
                        num(q, "edit")
                );

        List<Map<String, Object>> instructors = Db.rows(
                "SELECT id, name FROM users "
                + "WHERE user_role='INSTRUCTOR' ORDER BY name"
        );

        if (instructors.isEmpty()) {
            page(
                    q,
                    r,
                    u,
                    "Courses",
                    "<h1>Course Management</h1>"
                    + card(
                            "No instructors",
                            "<p>Create an instructor account first.</p>"
                    )
            );
            return;
        }

        StringBuilder instructorOptions = new StringBuilder();

        for (Map<String, Object> instructor : instructors) {
            String instructorId =
                    String.valueOf(instructor.get("id"));

            boolean selected = e != null
                    && instructorId.equals(
                            String.valueOf(e.get("instructor_id"))
                    );

            instructorOptions.append("<option value='")
                    .append(h(instructorId))
                    .append("'")
                    .append(selected ? " selected" : "")
                    .append(">")
                    .append(h(instructor.get("name")))
                    .append("</option>");
        }

        List<String[]> rows = new ArrayList<>();

        for (Map<String, Object> m : Db.rows(
                "SELECT c.id, c.title, u.name inst, "
                + "(SELECT COUNT(*) FROM enrollments e "
                + "WHERE e.course_id=c.id) n "
                + "FROM courses c JOIN users u ON u.id=c.instructor_id "
                + "ORDER BY c.id")) {

            rows.add(new String[]{
                String.valueOf(m.get("id")),
                h(m.get("title")),
                h(m.get("inst")),
                String.valueOf(m.get("n")),
                "<a class='btn sm ghost' href='/admin/courses?edit="
                        + m.get("id")
                        + "'>Edit</a> "
                        + miniForm(
                                "/admin/do/deleteCourse",
                                hid("id", m.get("id")),
                                "Delete",
                                "danger"
                        )
            });
        }

        String html = readTemplate("admin-courses.html")
                .replace("{{ADMIN_NAME}}", h(u.get("name")))
                .replace(
                        "{{FORM_TITLE}}",
                        e == null ? "Create course" : "Edit course"
                )
                .replace(
                        "{{HIDDEN_ID}}",
                        e == null ? "" : hid("id", e.get("id"))
                )
                .replace(
                        "{{COURSE_TITLE}}",
                        e == null ? "" : h(e.get("title"))
                )
                .replace(
                        "{{COURSE_DESCRIPTION}}",
                        e == null ? "" : h(e.get("description"))
                )
                .replace(
                        "{{COURSE_SYLLABUS}}",
                        e == null ? "" : h(e.get("syllabus"))
                )
                .replace(
                        "{{INSTRUCTOR_OPTIONS}}",
                        instructorOptions.toString()
                )
                .replace(
                        "{{FORM_BUTTON}}",
                        e == null ? "Create course" : "Save changes"
                )
                .replace(
                        "{{COURSES_TABLE}}",
                        table(
                                new String[]{
                                    "ID", "Title", "Instructor",
                                    "Students", "Actions"
                                },
                                rows
                        )
                );

        r.setContentType("text/html;charset=UTF-8");
        r.getWriter().write(html);
    }

    private void saveCourse(
            HttpServletRequest q,
            HttpServletResponse r,
            Map<String, Object> u
    ) throws Exception {

        String title = req(q, "title");

        if (str(q, "id").isEmpty()) {
            Db.exec(
                    "INSERT INTO courses(title,description,syllabus,instructor_id) "
                    + "VALUES(?,?,?,?)",
                    title,
                    str(q, "description"),
                    str(q, "syllabus"),
                    num(q, "instructor_id")
            );

        } else {
            Db.exec(
                    "UPDATE courses SET title=?, description=?, syllabus=?, "
                    + "instructor_id=? WHERE id=?",
                    title,
                    str(q, "description"),
                    str(q, "syllabus"),
                    num(q, "instructor_id"),
                    num(q, "id")
            );
        }

        ActivityLog.log(
                "Admin " + u.get("name")
                + " saved course '" + title + "'"
        );

        ok(q, "Course saved.");
        r.sendRedirect("/admin/courses");
    }

    private void deleteCourse(
            HttpServletRequest q,
            HttpServletResponse r,
            Map<String, Object> u
    ) throws Exception {

        Db.exec(
                "DELETE FROM courses WHERE id=?",
                num(q, "id")
        );

        ActivityLog.log(
                "Admin " + u.get("name")
                + " deleted course #" + str(q, "id")
        );

        ok(q, "Course deleted.");
        r.sendRedirect("/admin/courses");
    }

    // ---------- System settings ----------

    private void settings(
            HttpServletRequest q,
            HttpServletResponse r,
            Map<String, Object> u
    ) throws Exception {

        String siteName = Db.setting("site_name", "EduLearn LMS");

        boolean allow = "true".equals(
                Db.setting("allow_registration", "true")
        );

        String html = readTemplate("admin-settings.html")
                .replace("{{ADMIN_NAME}}", h(u.get("name")))
                .replace("{{SITE_NAME}}", h(siteName))
                .replace(
                        "{{REGISTRATION_OPEN}}",
                        allow ? "selected" : ""
                )
                .replace(
                        "{{REGISTRATION_CLOSED}}",
                        allow ? "" : "selected"
                );

        r.setContentType("text/html;charset=UTF-8");
        r.getWriter().write(html);
    }

    private void saveSettings(
            HttpServletRequest q,
            HttpServletResponse r,
            Map<String, Object> u
    ) throws Exception {

        String name = req(q, "site_name");

        Db.exec(
                "MERGE INTO settings(k,v) KEY(k) VALUES('site_name',?)",
                name
        );

        Db.exec(
                "MERGE INTO settings(k,v) KEY(k) VALUES('allow_registration',?)",
                "true".equals(str(q, "allow_registration"))
                        ? "true" : "false"
        );

        ActivityLog.log(
                "Admin " + u.get("name") + " updated system settings"
        );

        ok(q, "Settings updated.");
        r.sendRedirect("/admin/settings");
    }
}