package lms;

import jakarta.servlet.http.*;
import java.util.*;

/** Student: browse/enroll, access materials, submit assignments, track progress and grades. */
public class StudentServlet extends BaseServlet {
    @Override protected String requiredRole() { return "STUDENT"; }

    @Override
    protected void handle(HttpServletRequest q, HttpServletResponse r, Map<String, Object> u, String p) throws Exception {
        switch (p) {
            case "/student" -> dashboard(q, r, u);
            case "/student/browse" -> browse(q, r, u);
            case "/student/course" -> coursePage(q, r, u);
            case "/student/download" -> download(q, r, u);
            case "/student/do/enroll" -> enroll(q, r, u);
            case "/student/do/submit" -> submit(q, r, u);
            default -> r.sendRedirect("/student");
        }
    }

    private boolean enrolled(int cid, Map<String, Object> u) throws LmsException {
        return Db.one("SELECT 1 x FROM enrollments WHERE student_id=? AND course_id=?", uid(u), cid) != null;
    }

    private void dashboard(HttpServletRequest q, HttpServletResponse r, Map<String, Object> u) throws Exception {
        List<String[]> t = new ArrayList<>();
        long sum = 0, graded = 0;
        for (Map<String, Object> m : Db.rows(PERF + "WHERE e.student_id=? ORDER BY c.title", uid(u))) {
            if (m.get("avg") != null) { sum += Math.round(Db.dbl(m.get("avg"))); graded++; }
            t.add(new String[]{h(m.get("title")), m.get("done") + " / " + m.get("total"), bar(progress(m)), avgText(m), "<a class='btn sm' href='/student/course?id=" + m.get("cid") + "'>Open</a>"});
        }
        String overall = graded == 0 ? "—" : (sum / graded) + "%";
        page(q, r, u, "My Learning", "<h1>Welcome, " + h(u.get("name")) + "</h1><div class='stats'>" + stat(t.size(), "Enrolled courses") + stat(overall, "Overall grade") + "</div>"
            + card("My courses &amp; progress", table(new String[]{"Course", "Submitted", "Progress", "Avg grade", ""}, t)));
    }

    private void browse(HttpServletRequest q, HttpServletResponse r, Map<String, Object> u) throws Exception {
        List<String[]> t = new ArrayList<>();
        for (Map<String, Object> m : Db.rows("SELECT c.id, c.title, c.description, c.syllabus, i.name inst, "
            + "(SELECT COUNT(*) FROM enrollments e WHERE e.course_id=c.id AND e.student_id=?) mine FROM courses c JOIN users i ON i.id=c.instructor_id ORDER BY c.title", uid(u))) {
            String act = Db.num(m.get("mine")) > 0 ? "<span class='tag ok'>Enrolled</span>" : miniForm("/student/do/enroll", hid("id", m.get("id")), "Enroll", "");
            t.add(new String[]{"<b>" + h(m.get("title")) + "</b><br><span class='muted'>" + h(m.get("description")) + "</span>", h(m.get("syllabus")), h(m.get("inst")), act});
        }
        page(q, r, u, "Browse Courses", "<h1>Browse Courses</h1>" + card("Available courses", table(new String[]{"Course", "Syllabus", "Instructor", ""}, t)));
    }

    private void enroll(HttpServletRequest q, HttpServletResponse r, Map<String, Object> u) throws Exception {
        int cid = num(q, "id");
        Map<String, Object> c = Db.one("SELECT title FROM courses WHERE id=?", cid);
        if (c == null) throw new LmsException("Course not found.");
        if (enrolled(cid, u)) throw new LmsException("You are already enrolled in this course.");
        Db.tx(con -> {
            Db.execTx(con, "INSERT INTO enrollments(student_id,course_id) VALUES(?,?)", uid(u), cid);
            Db.execTx(con, "INSERT INTO activity(msg) VALUES(?)", u.get("name") + " enrolled in '" + c.get("title") + "'");
        });
        ok(q, "Enrolled in " + c.get("title") + ".");
        r.sendRedirect("/student/course?id=" + cid);
    }

    private void coursePage(HttpServletRequest q, HttpServletResponse r, Map<String, Object> u) throws Exception {
        int cid = num(q, "id");
        if (!enrolled(cid, u)) throw new LmsException("Please enroll in the course first.");
        Map<String, Object> c = Db.one("SELECT c.title, c.description, c.syllabus, i.name inst FROM courses c JOIN users i ON i.id=c.instructor_id WHERE c.id=?", cid);
        StringBuilder b = new StringBuilder("<p><a href='/student'>&larr; My learning</a></p><h1>" + h(c.get("title")) + "</h1>");
        b.append(card("About", "<p>" + h(c.get("description")) + "</p><p><b>Syllabus:</b> " + h(c.get("syllabus")) + "</p><p class='muted'>Instructor: " + h(c.get("inst")) + "</p>"));
        List<String[]> mt = new ArrayList<>();
        for (Map<String, Object> m : Db.rows("SELECT id, title, content FROM materials WHERE course_id=? ORDER BY id", cid))
            mt.add(new String[]{h(m.get("title")), h(m.get("content")), "<a class='btn sm ghost' href='/student/download?id=" + m.get("id") + "'>Download</a>"});
        b.append(card("Course materials", table(new String[]{"Material", "Content", ""}, mt)));
        StringBuilder as = new StringBuilder();
        for (Map<String, Object> m : Db.rows("SELECT a.id, a.title, a.max_marks, s.content, s.grade, s.feedback FROM assignments a "
            + "LEFT JOIN submissions s ON s.assignment_id=a.id AND s.student_id=? WHERE a.course_id=? ORDER BY a.id", uid(u), cid)) {
            boolean done = m.get("content") != null, graded = m.get("grade") != null;
            as.append("<div class='assign'><h3>").append(h(m.get("title"))).append(" <small class='muted'>(").append(m.get("max_marks")).append(" marks)</small></h3>");
            if (graded) as.append("<p><span class='tag ok'>Grade: ").append(m.get("grade")).append(" / ").append(m.get("max_marks")).append("</span> ").append(h(m.get("feedback"))).append("</p>");
            else as.append("<p>").append(done ? "<span class='tag'>Submitted - awaiting grade</span>" : "<span class='tag warn'>Not submitted</span>").append("</p>");
            if (!graded) as.append(form("/student/do/submit", hid("aid", m.get("id")) + hid("cid", cid) + area(done ? "Update your answer" : "Your answer", "content", m.get("content")), done ? "Resubmit" : "Submit"));
            else as.append("<p class='muted'>Your answer: ").append(h(m.get("content"))).append("</p>");
            as.append("</div>");
        }
        b.append(card("Assignments, grades &amp; feedback", as.length() == 0 ? "<p class='muted'>No assignments yet.</p>" : as.toString()));
        page(q, r, u, "Course", b.toString());
    }

    private void submit(HttpServletRequest q, HttpServletResponse r, Map<String, Object> u) throws Exception {
        int aid = num(q, "aid"), cid = num(q, "cid");
        String content = req(q, "content");
        Map<String, Object> a = Db.one("SELECT 1 x FROM assignments WHERE id=? AND course_id=?", aid, cid);
        if (a == null || !enrolled(cid, u)) throw new LmsException("You cannot submit to this assignment.");
        Map<String, Object> old = Db.one("SELECT id, grade FROM submissions WHERE assignment_id=? AND student_id=?", aid, uid(u));
        if (old == null) Db.exec("INSERT INTO submissions(assignment_id,student_id,content) VALUES(?,?,?)", aid, uid(u), content);
        else if (old.get("grade") != null) throw new LmsException("This assignment is already graded and cannot be changed.");
        else Db.exec("UPDATE submissions SET content=? WHERE id=?", content, old.get("id"));
        ActivityLog.log(u.get("name") + " submitted assignment #" + aid);
        ok(q, "Submission saved.");
        r.sendRedirect("/student/course?id=" + cid);
    }

    private void download(HttpServletRequest q, HttpServletResponse r, Map<String, Object> u) throws Exception {
        Map<String, Object> m = Db.one("SELECT m.title, m.content FROM materials m JOIN enrollments e ON e.course_id=m.course_id WHERE m.id=? AND e.student_id=?", num(q, "id"), uid(u));
        if (m == null) throw new LmsException("Material not available.");
        r.setContentType("text/plain;charset=UTF-8");
        r.setHeader("Content-Disposition", "attachment; filename=\"" + String.valueOf(m.get("title")).replaceAll("[^A-Za-z0-9_-]", "_") + ".txt\"");
        r.getWriter().write(m.get("title") + "\n\n" + m.get("content"));
    }
}
