package com.example.purchaserequest.controller;

import com.example.purchaserequest.model.RequestStatus;
import com.example.purchaserequest.model.dto.DeletedPurchaseRequestDto;
import com.example.purchaserequest.model.dto.PurchaseRequestDto;
import com.example.purchaserequest.model.dto.UserSummaryDto;
import com.example.purchaserequest.model.entity.User;
import com.example.purchaserequest.model.Role;
import com.example.purchaserequest.repository.UserRepository;
import com.example.purchaserequest.service.PurchaseRequestService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import com.example.purchaserequest.config.SecurityConfig;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.Set;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(PurchaseRequestController.class)
@Import(SecurityConfig.class)
class PurchaseRequestControllerTest {

    @TestConfiguration
    static class MockConfig {
        @Bean
        public PurchaseRequestService purchaseRequestService() {
            return mock(PurchaseRequestService.class);
        }

        @Bean
        public UserRepository userRepository() {
            return mock(UserRepository.class);
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private PurchaseRequestService purchaseRequestService;

    @Autowired
    private UserRepository userRepository;

    private User createTestUser() {
        return User.builder()
            .id(1L)
            .username("yamada")
            .password("password")
            .name("山田太郎")
            .department("開発部")
            .email("yamada@example.com")
            .roles(Set.of(Role.ROLE_USER))
            .createdAt(LocalDateTime.now())
            .updatedAt(LocalDateTime.now())
            .build();
    }

    private PurchaseRequestDto createTestDto() {
        return PurchaseRequestDto.builder()
            .id(1L)
            .itemName("ノートPC")
            .quantity(1)
            .unitPrice(new BigDecimal("150000"))
            .totalAmount(new BigDecimal("150000"))
            .purchaseReason("開発業務用")
            .status(RequestStatus.DRAFT)
            .highAmount(true)
            .requester(UserSummaryDto.builder().id(1L).name("山田太郎").department("開発部").build())
            .createdAt(LocalDateTime.now())
            .updatedAt(LocalDateTime.now())
            .build();
    }

    @Test
    @DisplayName("申請登録APIが正常に動作すること")
    @WithMockUser(username = "yamada", roles = {"USER"})
    void 申請登録APIが正常に動作すること() throws Exception {
        when(userRepository.findByUsername("yamada")).thenReturn(Optional.of(createTestUser()));
        when(purchaseRequestService.createDraft(any(), eq(1L))).thenReturn(createTestDto());

        String requestBody = """
            {
                "itemName": "ノートPC",
                "quantity": 1,
                "unitPrice": 150000,
                "purchaseReason": "開発業務用"
            }
            """;

        mockMvc.perform(post("/api/v1/requests")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.status").value("success"))
            .andExpect(jsonPath("$.data.itemName").value("ノートPC"))
            .andExpect(jsonPath("$.message").value("申請を下書き保存しました"));
    }

    @Test
    @DisplayName("バリデーションエラー時に400が返ること")
    @WithMockUser(username = "yamada", roles = {"USER"})
    void バリデーションエラー時に400が返ること() throws Exception {
        String requestBody = """
            {
                "itemName": "",
                "quantity": 0,
                "purchaseReason": ""
            }
            """;

        mockMvc.perform(post("/api/v1/requests")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.status").value("error"))
            .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    @Test
    @DisplayName("認証なしでアクセスすると401が返ること")
    void 認証なしでアクセスすると401が返ること() throws Exception {
        mockMvc.perform(get("/api/v1/requests"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("下書き削除APIが正常に動作すること")
    @WithMockUser(username = "yamada", roles = {"USER"})
    void 下書き削除APIが正常に動作すること() throws Exception {
        when(userRepository.findByUsername("yamada")).thenReturn(Optional.of(createTestUser()));
        DeletedPurchaseRequestDto deletedDto = DeletedPurchaseRequestDto.builder()
            .id(1L)
            .deleted(true)
            .deletedAt(LocalDateTime.now())
            .build();
        when(purchaseRequestService.deleteDraftRequest(eq(1L), eq("yamada"))).thenReturn(deletedDto);

        mockMvc.perform(delete("/api/v1/requests/1")
                .with(csrf()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("success"))
            .andExpect(jsonPath("$.data.id").value(1))
            .andExpect(jsonPath("$.data.deleted").value(true))
            .andExpect(jsonPath("$.message").value("下書きを削除しました"));
    }

    @Test
    @DisplayName("承認APIにUSERロールでアクセスすると403が返ること")
    @WithMockUser(username = "yamada", roles = {"USER"})
    void 承認APIにUSERでアクセスすると403が返ること() throws Exception {
        mockMvc.perform(patch("/api/v1/requests/1/approve")
                .with(csrf()))
            .andExpect(status().isForbidden());
    }
}
