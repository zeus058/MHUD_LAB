package vn.edu.hcmus.securechat.chat.service;

import vn.edu.hcmus.securechat.common.exception.ProtocolException;
import vn.edu.hcmus.securechat.common.protocol.Role;

/**
 * AccessControlService — Kiểm tra quyền truy cập dựa trên Role được nhúng trong ST.
 *
 * Nguyên tắc: Fail-closed (mặc định từ chối nếu không đủ quyền).
 *
 * Quy tắc phân quyền:
 *   - Mọi user: gửi/nhận tin, upload/download file, xem danh sách users
 *   - Chỉ ADMIN: revoke cert của user KHÁC, xem audit log bảo mật
 */
public final class AccessControlService {

    private AccessControlService() {}

    /**
     * Kiểm tra quyền revoke cert của user khác.
     * User luôn được tự revoke cert của mình; chỉ ADMIN mới revoke cert của người khác.
     *
     * @param requesterId clientId của người gửi request
     * @param targetId    clientId bị revoke
     * @param role        role của requester từ ST
     * @throws ProtocolException nếu USER cố revoke cert người khác
     */
    public static void requireAdminForRemoteRevoke(String requesterId, String targetId, Role role)
            throws ProtocolException {
        if (requesterId == null || targetId == null) {
            throw new ProtocolException("ACCESS_DENIED: requesterId and targetId must not be null");
        }
        if (!requesterId.equals(targetId) && role != Role.ADMIN) {
            throw new ProtocolException(
                "ACCESS_DENIED: Only ADMIN can revoke other users' certificates. " +
                "requesterId=" + requesterId + " targetId=" + targetId + " role=" + role);
        }
    }

    /**
     * Kiểm tra quyền xem audit log.
     * Chỉ ADMIN được xem secure_audit.log.
     *
     * @param role role của requester từ ST
     * @throws ProtocolException nếu không phải ADMIN
     */
    public static void requireAdminForAuditLog(Role role) throws ProtocolException {
        if (role != Role.ADMIN) {
            throw new ProtocolException(
                "ACCESS_DENIED: Only ADMIN can access audit logs. Current role=" + role);
        }
    }

    /**
     * Kiểm tra quyền chung theo role và action.
     *
     * @param requiredRole role tối thiểu cần có
     * @param actualRole   role thực tế của user
     * @param action       tên action (để log lỗi)
     * @throws ProtocolException nếu không đủ quyền
     */
    public static void requireRole(Role requiredRole, Role actualRole, String action)
            throws ProtocolException {
        if (actualRole == null) {
            throw new ProtocolException("ACCESS_DENIED: No role assigned for action=" + action);
        }
        // ADMIN >= MODERATOR >= USER
        int required = roleOrdinal(requiredRole);
        int actual   = roleOrdinal(actualRole);
        if (actual < required) {
            throw new ProtocolException(
                "ACCESS_DENIED: action=" + action +
                " requires role=" + requiredRole + " but current role=" + actualRole);
        }
    }

    private static int roleOrdinal(Role role) {
        return switch (role) {
            case USER      -> 0;
            case MODERATOR -> 1;
            case ADMIN     -> 2;
        };
    }
}
