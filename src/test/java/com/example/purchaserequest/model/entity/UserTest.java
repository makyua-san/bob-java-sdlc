package com.example.purchaserequest.model.entity;

import com.example.purchaserequest.model.Role;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class UserTest {

    @Test
    @DisplayName("ROLE_APPROVERを持つユーザーは承認権限があること")
    void 承認者ロールを持つ場合承認権限がある() {
        User user = User.builder()
            .id(1L)
            .username("approver")
            .password("password")
            .name("承認者")
            .department("管理部")
            .email("approver@example.com")
            .roles(Set.of(Role.ROLE_USER, Role.ROLE_APPROVER))
            .createdAt(LocalDateTime.now())
            .updatedAt(LocalDateTime.now())
            .build();

        assertThat(user.hasApprovalAuthority()).isTrue();
    }

    @Test
    @DisplayName("ROLE_USERのみのユーザーは承認権限がないこと")
    void 一般ユーザーロールのみの場合承認権限がない() {
        User user = User.builder()
            .id(2L)
            .username("user")
            .password("password")
            .name("一般ユーザー")
            .department("開発部")
            .email("user@example.com")
            .roles(Set.of(Role.ROLE_USER))
            .createdAt(LocalDateTime.now())
            .updatedAt(LocalDateTime.now())
            .build();

        assertThat(user.hasApprovalAuthority()).isFalse();
    }
}
