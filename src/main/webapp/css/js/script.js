// ========================================
// LMS FRONTEND JAVASCRIPT
// ========================================

document.addEventListener("DOMContentLoaded", function () {

    console.log("LMS frontend loaded successfully");


    // ========================================
    // 1. SHOW / HIDE PASSWORD
    // ========================================

    const toggleButtons =
        document.querySelectorAll(".toggle-password");

    toggleButtons.forEach(function (button) {

        button.addEventListener("click", function () {

            const targetId =
                button.getAttribute("data-target") ||
                button.getAttribute("aria-controls");

            const passwordInput =
                document.getElementById(targetId);

            if (!passwordInput) {
                return;
            }

            const isHidden =
                passwordInput.type === "password";

            passwordInput.type =
                isHidden ? "text" : "password";

            button.textContent =
                isHidden ? "Hide" : "Show";

            button.setAttribute(
                "aria-pressed",
                String(isHidden)
            );

        });

    });


    // ========================================
    // 2. CONFIRM BEFORE FORM SUBMISSION
    // ========================================

    const confirmForms =
        document.querySelectorAll("[data-confirm]");

    confirmForms.forEach(function (form) {

        form.addEventListener("submit", function (event) {

            const message =
                form.getAttribute("data-confirm");

            if (message && !confirm(message)) {
                event.preventDefault();
            }

        });

    });


    // ========================================
    // 3. PREVENT DOUBLE SUBMISSIONS
    // ========================================

    const forms = document.querySelectorAll("form");

    forms.forEach(function (form) {

        form.addEventListener("submit", function (event) {

            // Let the browser validate the form first.
            if (!form.checkValidity()) {
                return;
            }

            // Stop if a confirmation handler cancelled submission.
            if (event.defaultPrevented) {
                return;
            }

            const submitButton = form.querySelector(
                'button[type="submit"], input[type="submit"]'
            );

            if (
                submitButton &&
                !form.hasAttribute("data-confirm")
            ) {

                submitButton.dataset.originalText =
                    submitButton.textContent;

                submitButton.textContent =
                    "Please wait...";

                submitButton.disabled = true;

            }

        });

    });


    // ========================================
    // 4. AUTO-HIDE FLASH MESSAGES
    // ========================================

    const flashMessages =
        document.querySelectorAll(
            ".flash, .flash-success, .flash-error"
        );

    flashMessages.forEach(function (message) {

        setTimeout(function () {

            message.style.transition = "opacity 0.5s";

            message.style.opacity = "0";

            setTimeout(function () {

                message.style.display = "none";

            }, 500);

        }, 5000);

    });

});