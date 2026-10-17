package lms;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.*;
import java.util.*;

/** JDBC helper: PreparedStatement queries, transactions, schema creation and seed data. */
public final class Db {
    private static final String URL = "jdbc:h2:./data/lms";

    private Db() { }

    public interface TxWork { void run(Connection c) throws SQLException; }

    public static Connection conn() throws SQLException { return DriverManager.getConnection(URL, "sa", ""); }

    private static void bind(PreparedStatement ps, Object[] p) throws SQLException {
        for (int i = 0; i < p.length; i++) ps.setObject(i + 1, p[i]);
    }

    public static List<Map<String, Object>> rows(String sql, Object... p) throws LmsException {
        try (Connection c = conn(); PreparedStatement ps = c.prepareStatement(sql)) {
            bind(ps, p);
            try (ResultSet rs = ps.executeQuery()) {
                ResultSetMetaData md = rs.getMetaData();
                List<Map<String, Object>> out = new ArrayList<>();
                while (rs.next()) {
                    Map<String, Object> m = new LinkedHashMap<>();
                    for (int i = 1; i <= md.getColumnCount(); i++) m.put(md.getColumnLabel(i).toLowerCase(), rs.getObject(i));
                    out.add(m);
                }
                return out;
            }
        } catch (SQLException e) { throw new LmsException("Database error: " + e.getMessage()); }
    }

    public static Map<String, Object> one(String sql, Object... p) throws LmsException {
        List<Map<String, Object>> r = rows(sql, p);
        return r.isEmpty() ? null : r.get(0);
    }

    public static int exec(String sql, Object... p) throws LmsException {
        try (Connection c = conn(); PreparedStatement ps = c.prepareStatement(sql)) {
            bind(ps, p);
            return ps.executeUpdate();
        } catch (SQLException e) {
            throw new LmsException(e.getErrorCode() == 23505 ? "That value already exists (duplicate entry)." : "Database error: " + e.getMessage());
        }
    }

    public static int execTx(Connection c, String sql, Object... p) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(sql)) { bind(ps, p); return ps.executeUpdate(); }
    }

    /** Runs all statements atomically: commit on success, rollback on any failure. */
    public static void tx(TxWork work) throws LmsException {
        try (Connection c = conn()) {
            c.setAutoCommit(false);
            try { work.run(c); c.commit(); } catch (SQLException e) { c.rollback(); throw e; }
        } catch (SQLException e) {
            throw new LmsException(e.getErrorCode() == 23505 ? "Already exists (duplicate entry)." : "Transaction failed: " + e.getMessage());
        }
    }

    public static long num(Object o) { return o == null ? 0 : ((Number) o).longValue(); }
    public static double dbl(Object o) { return o == null ? 0 : ((Number) o).doubleValue(); }

    public static String hash(String s) {
        try {
            byte[] d = MessageDigest.getInstance("SHA-256").digest(("lms-salt:" + s).getBytes(StandardCharsets.UTF_8));
            StringBuilder b = new StringBuilder();
            for (byte x : d) b.append(String.format("%02x", x));
            return b.toString();
        } catch (Exception e) { throw new IllegalStateException(e); }
    }

    public static String setting(String key, String def) throws LmsException {
        Map<String, Object> m = one("SELECT v FROM settings WHERE k=?", key);
        return m == null ? def : String.valueOf(m.get("v"));
    }

    public static void init() throws LmsException {
        exec("CREATE TABLE IF NOT EXISTS users(id INT AUTO_INCREMENT PRIMARY KEY, name VARCHAR(100) NOT NULL, email VARCHAR(120) NOT NULL UNIQUE, password VARCHAR(64) NOT NULL, user_role VARCHAR(12) NOT NULL)");
        exec("CREATE TABLE IF NOT EXISTS courses(id INT AUTO_INCREMENT PRIMARY KEY, title VARCHAR(150) NOT NULL, description VARCHAR(2000), syllabus VARCHAR(4000), instructor_id INT NOT NULL REFERENCES users(id) ON DELETE CASCADE)");
        exec("CREATE TABLE IF NOT EXISTS enrollments(student_id INT NOT NULL REFERENCES users(id) ON DELETE CASCADE, course_id INT NOT NULL REFERENCES courses(id) ON DELETE CASCADE, PRIMARY KEY(student_id, course_id))");
        exec("CREATE TABLE IF NOT EXISTS materials(id INT AUTO_INCREMENT PRIMARY KEY, course_id INT NOT NULL REFERENCES courses(id) ON DELETE CASCADE, title VARCHAR(150) NOT NULL, content VARCHAR(8000))");
        exec("CREATE TABLE IF NOT EXISTS assignments(id INT AUTO_INCREMENT PRIMARY KEY, course_id INT NOT NULL REFERENCES courses(id) ON DELETE CASCADE, title VARCHAR(150) NOT NULL, max_marks INT NOT NULL)");
        exec("CREATE TABLE IF NOT EXISTS submissions(id INT AUTO_INCREMENT PRIMARY KEY, assignment_id INT NOT NULL REFERENCES assignments(id) ON DELETE CASCADE, student_id INT NOT NULL REFERENCES users(id) ON DELETE CASCADE, content VARCHAR(8000), grade INT, feedback VARCHAR(1000), UNIQUE(assignment_id, student_id))");
        exec("CREATE TABLE IF NOT EXISTS settings(k VARCHAR(50) PRIMARY KEY, v VARCHAR(200))");
        exec("CREATE TABLE IF NOT EXISTS activity(id INT AUTO_INCREMENT PRIMARY KEY, ts TIMESTAMP DEFAULT CURRENT_TIMESTAMP, msg VARCHAR(300))");
        if (num(one("SELECT COUNT(*) c FROM users").get("c")) == 0) seed();
    }

    private static void seed() throws LmsException {
        exec("INSERT INTO users(name,email,password,user_role) VALUES(?,?,?,?)", "System Admin", "admin@lms.com", hash("admin123"), "ADMIN");
        exec("INSERT INTO users(name,email,password,user_role) VALUES(?,?,?,?)", "Dr. Priya Sharma", "instructor@lms.com", hash("teach123"), "INSTRUCTOR");
        exec("INSERT INTO users(name,email,password,user_role) VALUES(?,?,?,?)", "Rahul Verma", "student@lms.com", hash("learn123"), "STUDENT");
        int ins = (int) num(one("SELECT id FROM users WHERE user_role='INSTRUCTOR'").get("id"));
        int stu = (int) num(one("SELECT id FROM users WHERE user_role='STUDENT'").get("id"));
        exec("INSERT INTO courses(title,description,syllabus,instructor_id) VALUES(?,?,?,?)", "Java Programming",
             "Core Java, OOP, collections, JDBC and servlets.", "Unit 1: OOP | Unit 2: Collections | Unit 3: Threads | Unit 4: JDBC | Unit 5: Servlets", ins);
        int cid = (int) num(one("SELECT id FROM courses").get("id"));
        exec("INSERT INTO materials(course_id,title,content) VALUES(?,?,?)", cid, "Welcome & Setup Guide", "Install JDK 17+, an IDE and Maven. Read chapter 1 before the first class.");
        exec("INSERT INTO assignments(course_id,title,max_marks) VALUES(?,?,?)", cid, "Assignment 1 - OOP Basics", 20);
        exec("INSERT INTO enrollments(student_id,course_id) VALUES(?,?)", stu, cid);
        exec("MERGE INTO settings(k,v) KEY(k) VALUES('site_name','EduLearn LMS')");
        exec("MERGE INTO settings(k,v) KEY(k) VALUES('allow_registration','true')");
    }
}
