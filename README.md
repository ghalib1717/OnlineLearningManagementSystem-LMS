# EduLearn LMS — Learning Management System

A role-based web application built with **Java 17, Jakarta Servlets, Jetty, JDBC, H2, HTML5, CSS3 and JavaScript**. It provides separate workflows for administrators, instructors and students.

## Review 2 focus

This repository documents the application structure, relational database design, JDBC connectivity and responsive user interface. The application uses server-side Java Servlets for request handling and JDBC for database operations; HTML templates and static CSS/JavaScript provide the presentation layer.

## Features

- **Authentication:** sign in, student/instructor registration, logout and role-based routing.
- **Administrator:** dashboard statistics, user management, course management and site settings.
- **Instructor:** course overview, course materials and assignment management.
- **Student:** course browsing, enrollment, learning materials and assignment submissions.
- **Database:** H2 relational database with foreign keys, unique constraints and parameterized JDBC statements.
- **Activity logging:** records important application events.

## Technology stack

| Technology | Version | Purpose |
|---|---:|---|
| Java | 17 | Backend language |
| Jakarta Servlet API | Jetty 11.0.24 | HTTP request and response handling |
| Jetty | 11.0.24 | Embedded web server |
| H2 Database | 2.2.224 | Relational database |
| JDBC | JDK | Database connectivity |
| Maven | Project build | Dependency management and packaging |
| HTML5 / CSS3 / JavaScript | — | Frontend and responsive interface |

## Project structure

```text
lms-project/
├── docs/
│   ├── architecture.md
│   └── database-design.md
├── src/
│   └── main/
│       ├── java/lms/
│       │   ├── App.java                # Embedded Jetty startup and route registration
│       │   ├── BaseServlet.java        # Shared authentication, role checks and HTML helpers
│       │   ├── AuthServlet.java        # Login, registration and logout
│       │   ├── AdminServlet.java       # Admin operations
│       │   ├── InstructorServlet.java  # Instructor operations
│       │   ├── StudentServlet.java     # Student operations
│       │   ├── Db.java                 # JDBC connection, SQL helpers and schema initialization
│       │   ├── ActivityLog.java        # Activity logging
│       │   ├── StyleServlet.java       # Serves the shared stylesheet
│       │   └── LmsException.java       # Application-level exception
│       ├── resources/style.css         # Shared stylesheet used by servlet-rendered pages
│       └── webapp/                     # Static HTML templates and frontend assets
│           ├── css/style.css
│           ├── js/script.js
│           └── *.html
├── pom.xml
└── README.md
```

## Database design

The database schema is initialized by `Db.init()` when the application starts. The database design, table descriptions, primary keys, foreign keys and relationships are documented in [`docs/database-design.md`](docs/database-design.md). The application connects through JDBC to the local H2 database URL `jdbc:h2:./data/lms`.

### Main relationships

- One user with the `INSTRUCTOR` role can own multiple courses.
- Students and courses have a many-to-many relationship through `enrollments`.
- A course can contain multiple materials and assignments.
- A student can submit once per assignment; the database enforces this with a unique constraint on `(assignment_id, student_id)`.

## Application architecture

See [`docs/architecture.md`](docs/architecture.md) for the request flow and responsibilities of the frontend, servlets, shared base class and database helper.

## Prerequisites

1. JDK 17 or later.
2. Apache Maven 3.8 or later.
3. Git (optional, for cloning the repository).

Check installations:

```powershell
java -version
mvn -version
git --version
```

## Run locally

1. Clone the repository and open the project folder:

   ```powershell
   git clone https://github.com/ghalib1717/OnlineLearningManagementSystem-LMS.git
   cd OnlineLearningManagementSystem-LMS
   ```

2. Build the executable JAR:

   ```powershell
   mvn clean package
   ```

3. Start the application:

   ```powershell
   java -jar target/lms.jar
   ```

4. Open `http://localhost:8080` in a browser. A different port can be supplied as the first argument, for example `java -jar target/lms.jar 9090`.

The H2 database is created in the `data/` directory on first startup. The `data/` directory is intentionally excluded from Git so local database files are not committed.

## Demo accounts

The first startup seeds demo accounts when the users table is empty:

| Role | Email | Password |
|---|---|---|
| Administrator | `admin@lms.com` | `admin123` |
| Instructor | `instructor@lms.com` | `teach123` |
| Student | `student@lms.com` | `learn123` |

Change or remove demo credentials before deploying the application beyond a classroom demonstration.

## Frontend assets

- Shared servlet stylesheet: `/style.css`
- Static page stylesheet: `/css/style.css`
- JavaScript: `/js/script.js`

The CSS includes responsive breakpoints for tablet/mobile widths.

## Testing checklist for Review 2

- [ ] Build completes with `mvn clean package`.
- [ ] Application starts and the home page opens.
- [ ] Login works for each demo role and each role reaches the correct dashboard.
- [ ] Student registration works when registration is enabled.
- [ ] Admin can create/update users and courses.
- [ ] Instructor can manage course content and assignments.
- [ ] Student can browse/enroll in courses and submit assignment work.
- [ ] Refresh the browser at desktop and mobile widths; navigation and forms remain usable.
- [ ] Confirm database records persist after restarting the application.

## Team collaboration

Use GitHub commits and pull requests to show each member's contribution. Assign clear ownership for frontend/UI, backend/servlets, database/JDBC and testing/documentation. The repository's commit history should reflect the actual work completed by each team member.

## Notes

This is an academic demonstration project. The included SHA-256 password digest with a fixed application salt is not suitable for production authentication. Production deployment should use a dedicated password-hashing algorithm (such as Argon2id, bcrypt or PBKDF2), CSRF protection, secure cookie settings, input validation and automated tests.
