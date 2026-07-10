package vn.edu.hcmus.securechat.common.protocol;

public enum Role {
    USER,
    MODERATOR,
    ADMIN;

    public static Role fromString(String role) {
        try {
            return Role.valueOf(role.toUpperCase());
        } catch (Exception e) {
            return USER;
        }
    }
}
