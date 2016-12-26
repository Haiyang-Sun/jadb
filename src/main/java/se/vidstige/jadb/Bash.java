package se.vidstige.jadb;

public class Bash {
    public static String quote(String s) {
        // TODO: Should also check other whitespace
        if (!s.contains(" ")) {
            return s;
        }
        return "'" + s.replace("'", "'\\''") + "'";
    }
}
