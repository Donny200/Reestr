package adliya.uz.task1.service;

import adliya.uz.task1.config.security.SystemRole;
import adliya.uz.task1.entity.User;
import adliya.uz.task1.repository.UserRepository;

/** Keeps at least one enabled SUPER_ADMIN: such an account must not be deactivated or given another role. */
final class SuperAdmins {

    private SuperAdmins() {
    }

    static boolean isLastEnabled(User user, UserRepository users) {
        return Boolean.TRUE.equals(user.getEnabled()) && SystemRole.SUPER_ADMIN.isHeldBy(user)
                && users.countByRole_NameAndEnabledTrue(SystemRole.SUPER_ADMIN.authority()) <= 1;
    }
}
