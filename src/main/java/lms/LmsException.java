package lms;

/** Checked exception carrying a user-friendly message (validation, authorization, DB errors). */
public class LmsException extends Exception {
    public LmsException(String message) { super(message); }
}
