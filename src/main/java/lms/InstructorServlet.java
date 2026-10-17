package lms;

import jakarta.servlet.http.*;
import java.util.*;

/** Instructor: create/manage courses, content, assignments, grading and student performance. */
public class InstructorServlet extends BaseServlet {
    @Override protected String requiredRole() { return "INSTRUCTOR"; }

    @Override
    protected void handle(HttpServletRequest q, HttpServletResponse r, Map<String, Object> u, String p) throws Exception {
        switch (p) {
            case "/instructor" -> dashboard(q, r, u);
            case "/instructor/course" -> coursePage(q, r, u);
            case "/instructor/do/saveCourse" -> saveCourse(q, r, u);
            case "/instructor/do/material" -> addMaterial(q, r, u);
            case "/instructor/do/assignment" -> addAssignment(q, r, u);
            case "/instructor/do/grade" -> grade(q, r, u);
            default -> r.sendRedirect("/instructor");
        }
    }

    private Map<String, Object> owned(int cid, Map<String, Object> u) throws LmsException {
        Map<String, Object> c = Db.one("SELECT * FROM courses WHERE id=? AND instructor_id=?", cid, uid(u));
        if (c == null) throw new LmsException("Course not found or you are not its instructor.");
        return c;
    }

    private String courseForm(Map<String, Object> c) {
        return form("/instructor/do/saveCourse", (c == null ? "" : hid("id", c.get("id"))) + input("Title", "title", "text", c == null ? "" : c.get("title"), true)
            + area("Description", "description", c == null ? "" : c.get("description")) + area("Syllabus", "syllabus", c == null ? "" : c.get("syllabus")),
            c == null ? "Create course" : "Save changes");
    }

    private void dashboard(HttpServletRequest q, HttpServletResponse r, Map<String, Object> u) throws Exception {
        List<Map<String, Object>> rows = Db.rows("SELECT c.id, c.title, (SELECT COUNT(*) FROM enrollments e WHERE e.course_id=c.id) n, "
            + "(SELECT COUNT(*) FROM assignments a WHERE a.course_id=c.id) a, "
            + "(SELECT COUNT(*) FROM submissions s JOIN assignments a ON a.id=s.assignment_id WHERE a.course_id=c.id AND s.grade IS NULL) pending "
            + "FROM courses c WHERE c.instructor_id=? ORDER BY c.id", uid(u));
        long max = 1;
        for (Map<String, Object> m : rows) max = Math.max(max, Db.num(m.get("n")));
        List<String[]> t = new ArrayList<>();
        for (Map<String, Object> m : rows) {
            long n = Db.num(m.get("n"));
            t.add(new String[]{h(m.get("title")), n + "<div class='bar'><span style='width:" + (n * 100 / max) + "%'></span></div>", String.valueOf(m.get("a")),
                String.valueOf(m.get("pending")), "<a class='btn sm' href='/instructor/course?id=" + m.get("id") + "'>Manage</a>"});
        }
        page(q, r, u, "My Courses", "<h1>Instructor Dashboard</h1>" + card("My courses &amp; enrollment stats", table(new String[]{"Course", "Enrolled", "Assignments", "To grade", ""}, t))
            + card("Create a new course", courseForm(null)));
    }

    private void coursePage(HttpServletRequest q, HttpServletResponse r, Map<String, Object> u) throws Exception {
        int cid = num(q, "id");
        Map<String, Object> c = owned(cid, u);
        StringBuilder b = new StringBuilder("<p><a href='/instructor'>&larr; Back to my courses</a></p><h1>" + h(c.get("title")) + "</h1>");
        b.append(card("Course details", courseForm(c)));

        List<String[]> mt = new ArrayList<>();
        for (Map<String, Object> m : Db.rows("SELECT title, content FROM materials WHERE course_id=? ORDER BY id", cid)) mt.add(new String[]{h(m.get("title")), h(m.get("content"))});
        b.append(card("Course content", table(new String[]{"Material", "Content"}, mt)
            + form("/instructor/do/material", hid("cid", cid) + input("Title", "title", "text", "", true) + area("Content", "content", ""), "Add material")));

        List<String[]> at = new ArrayList<>();
        for (Map<String, Object> m : Db.rows("SELECT title, max_marks FROM assignments WHERE course_id=? ORDER BY id", cid)) at.add(new String[]{h(m.get("title")), String.valueOf(m.get("max_marks"))});
        b.append(card("Assignments", table(new String[]{"Assignment", "Max marks"}, at)
            + form("/instructor/do/assignment", hid("cid", cid) + input("Title", "title", "text", "", true) + input("Max marks", "max_marks", "number", 100, true), "Add assignment")));

        List<String[]> pt = new ArrayList<>();
        for (Map<String, Object> m : Db.rows(PERF + "WHERE e.course_id=? ORDER BY st.name", cid))
            pt.add(new String[]{h(m.get("student")), m.get("done") + " / " + m.get("total"), bar(progress(m)), avgText(m)});
        b.append(card("Student performance", table(new String[]{"Student", "Submitted", "Progress", "Avg grade"}, pt)));

        List<String[]> st = new ArrayList<>();
        for (Map<String, Object> m : Db.rows("SELECT s.id, st.name, a.title, a.max_marks, s.content, s.grade, s.feedback FROM submissions s "
            + "JOIN assignments a ON a.id=s.assignment_id JOIN users st ON st.id=s.student_id WHERE a.course_id=? ORDER BY s.id DESC", cid)) {
            String f = "<form method='post' action='/instructor/do/grade' class='row'>" + hid("cid", cid) + hid("sid", m.get("id"))
                + "<input type='number' name='grade' min='0' max='" + m.get("max_marks") + "' value='" + h(m.get("grade")) + "' placeholder='/" + m.get("max_marks") + "' required>"
                + "<input type='text' name='feedback' value='" + h(m.get("feedback")) + "' placeholder='Feedback'><button class='btn sm'>Save</button></form>";
            st.add(new String[]{h(m.get("name")), h(m.get("title")), h(m.get("content")), f});
        }
        b.append(card("Submissions &amp; feedback", table(new String[]{"Student", "Assignment", "Submission", "Grade / feedback"}, st)));
        page(q, r, u, "Manage course", b.toString());
    }

    private void saveCourse(HttpServletRequest q, HttpServletResponse r, Map<String, Object> u) throws Exception {
        String title = req(q, "title");
        if (str(q, "id").isEmpty()) {
            Db.exec("INSERT INTO courses(title,description,syllabus,instructor_id) VALUES(?,?,?,?)", title, str(q, "description"), str(q, "syllabus"), uid(u));
            ok(q, "Course created.");
            r.sendRedirect("/instructor");
        } else {
            int cid = num(q, "id");
            owned(cid, u);
            Db.exec("UPDATE courses SET title=?, description=?, syllabus=? WHERE id=?", title, str(q, "description"), str(q, "syllabus"), cid);
            ok(q, "Course updated.");
            r.sendRedirect("/instructor/course?id=" + cid);
        }
        ActivityLog.log("Instructor " + u.get("name") + " saved course '" + title + "'");
    }

    private void addMaterial(HttpServletRequest q, HttpServletResponse r, Map<String, Object> u) throws Exception {
        int cid = num(q, "cid");
        owned(cid, u);
        Db.exec("INSERT INTO materials(course_id,title,content) VALUES(?,?,?)", cid, req(q, "title"), str(q, "content"));
        ActivityLog.log("Instructor " + u.get("name") + " added material to course #" + cid);
        ok(q, "Material added.");
        r.sendRedirect("/instructor/course?id=" + cid);
    }

    private void addAssignment(HttpServletRequest q, HttpServletResponse r, Map<String, Object> u) throws Exception {
        int cid = num(q, "cid"), max = num(q, "max_marks");
        owned(cid, u);
        if (max < 1 || max > 1000) throw new LmsException("Max marks must be between 1 and 1000.");
        Db.exec("INSERT INTO assignments(course_id,title,max_marks) VALUES(?,?,?)", cid, req(q, "title"), max);
        ActivityLog.log("Instructor " + u.get("name") + " added assignment to course #" + cid);
        ok(q, "Assignment added.");
        r.sendRedirect("/instructor/course?id=" + cid);
    }

    private void grade(HttpServletRequest q, HttpServletResponse r, Map<String, Object> u) throws Exception {
        int cid = num(q, "cid"), sid = num(q, "sid"), g = num(q, "grade");
        Map<String, Object> s = Db.one("SELECT a.max_marks FROM submissions s JOIN assignments a ON a.id=s.assignment_id JOIN courses c ON c.id=a.course_id "
            + "WHERE s.id=? AND c.instructor_id=?", sid, uid(u));
        if (s == null) throw new LmsException("Submission not found.");
        if (g < 0 || g > Db.num(s.get("max_marks"))) throw new LmsException("Grade must be between 0 and " + s.get("max_marks") + ".");
        Db.exec("UPDATE submissions SET grade=?, feedback=? WHERE id=?", g, str(q, "feedback"), sid);
        ActivityLog.log("Instructor " + u.get("name") + " graded submission #" + sid);
        ok(q, "Grade saved.");
        r.sendRedirect("/instructor/course?id=" + cid);
    }
}
