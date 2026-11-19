# Application Architecture — EduLearn LMS

## High-level request flow

```text
Browser (HTML / CSS / JavaScript)
              | HTTP request
              v
Embedded Jetty server (App.java)
              |
              +--> Static ResourceHandler --> HTML / CSS / JavaScript
              |
              +--> AuthServlet       (/ and authentication routes)
              +--> AdminServlet     (/admin/*)
              +--> InstructorServlet(/instructor/*)
              +--> StudentServlet   (/student/*)
                         |
                         v
                 BaseServlet
       session checks, role checks, shared helpers
                         |
                         v
                       Db.java
       JDBC + PreparedStatement + transactions
                         |
                         v
                   H2 database
```

## Component responsibilities

| Component | Responsibility |
|---|---|
| `App` | Initializes the database and activity logger, configures Jetty, registers servlet routes and serves static resources. |
| `BaseServlet` | Shared servlet request handling, session lookup, role authorization, error handling, HTML escaping and reusable page helpers. |
| `AuthServlet` | Login, registration, logout and role-based redirect after authentication. |
| `AdminServlet` | Administrative dashboard, users, courses and settings. |
| `InstructorServlet` | Instructor dashboard, course content and assignment operations. |
| `StudentServlet` | Student dashboard, course browsing, enrollment and assignment submission. |
| `Db` | H2 connection management, parameterized SQL helpers, transactions, schema initialization and demo seed data. |
| `ActivityLog` | Records significant application actions. |
| `StyleServlet` | Serves the shared stylesheet from the classpath. |

## Frontend structure

The `src/main/webapp/` directory contains standalone HTML templates and static frontend assets. Dynamic data is supplied by the Java servlet layer where required. `css/style.css` styles static pages; the shared `/style.css` route serves the stylesheet used by servlet-rendered pages; `js/script.js` contains browser-side interactions. The stylesheet contains media queries for smaller screens.

## Authorization model

The application stores the authenticated user in the HTTP session. Each protected servlet declares its required role through `requiredRole()`. `BaseServlet` checks the session user and redirects unauthenticated or unauthorized requests to the login route. The admin, instructor and student servlets therefore have separate access boundaries.

## Build and runtime

Maven compiles Java sources, copies resources and packages an executable shaded JAR. `App.main()` starts embedded Jetty on port `8080` by default; an optional first command-line argument overrides the port.
