# EduLearn – Online Learning Management System (Java Web Project)

**Course:** Java Programming (Project-Based Evaluation) · Galgotias University × GUVI · Batch 2029
**Topic:** #1 – Online Learning Management System (LMS) · **Rubric:** B (Web-Based)

## Tech stack
Java 17 · Jakarta Servlets (embedded Jetty 11) · JDBC + H2 embedded database · server-rendered HTML + CSS · Maven

## Run
```bash
mvn clean package
java -jar target/lms.jar          # http://localhost:8080  (optional: java -jar target/lms.jar 9090)
```
Data is stored in `./data/lms.mv.db` (auto-created with demo data).

| Role | Email | Password |
|---|---|---|
| Admin | admin@lms.com | admin123 |
| Instructor | instructor@lms.com | teach123 |
| Student | student@lms.com | learn123 |

## Features
- **Admin:** user CRUD + roles, course CRUD, performance analytics (bars/tables), system settings, live activity monitor
- **Instructor:** create/edit courses, add materials & assignments, grade with feedback, student performance, enrollment stats
- **Student:** browse & enroll, view/download materials, submit assignments, progress tracking, grades & feedback

## Rubric mapping
| Rubric item | Where |
|---|---|
| Problem understanding & design | 3 roles, 8-table schema (`Db.init`), role dashboards as per topic doc |
| Core Java (OOP, Collections, Exceptions, Threads) | `BaseServlet` (abstraction/inheritance/polymorphism), `LmsException`, `List/Map` result sets, `ActivityLog` (background thread + `BlockingQueue`) |
| JDBC | `Db.java` – PreparedStatements, CRUD, transactions (`Db.tx`) |
| Servlets & web integration | `*Servlet.java`, `HttpSession` login, request/response handling, role guards |

## Structure
`src/main/java/lms/` – `App`, `Db`, `ActivityLog`, `LmsException`, `BaseServlet`, `AuthServlet`, `AdminServlet`, `InstructorServlet`, `StudentServlet`, `StyleServlet` · `src/main/resources/style.css`

## Team
Team name: ______ · Members: ______ (fill in)
