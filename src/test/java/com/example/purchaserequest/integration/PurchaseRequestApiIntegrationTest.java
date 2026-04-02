package com.example.purchaserequest.integration;

import com.example.purchaserequest.model.RequestStatus;
import com.example.purchaserequest.model.Role;
import com.example.purchaserequest.model.entity.PurchaseRequest;
import com.example.purchaserequest.model.entity.User;
import com.example.purchaserequest.repository.PurchaseRequestRepository;
import com.example.purchaserequest.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class PurchaseRequestApiIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PurchaseRequestRepository purchaseRequestRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private User normalUser;
    private User approverUser;

    @BeforeEach
    void setUp() {
        purchaseRequestRepository.deleteAll();
        userRepository.deleteAll();

        normalUser = userRepository.save(User.builder()
            .username("yamada")
            .password(passwordEncoder.encode("password"))
            .name("山田太郎")
            .department("開発部")
            .email("yamada@example.com")
            .roles(Set.of(Role.ROLE_USER))
            .createdAt(LocalDateTime.now())
            .updatedAt(LocalDateTime.now())
            .build());

        approverUser = userRepository.save(User.builder()
            .username("sato")
            .password(passwordEncoder.encode("password"))
            .name("佐藤花子")
            .department("管理部")
            .email("sato@example.com")
            .roles(Set.of(Role.ROLE_USER, Role.ROLE_APPROVER))
            .createdAt(LocalDateTime.now())
            .updatedAt(LocalDateTime.now())
            .build());
    }

    @Test
    @DisplayName("申請登録→提出→承認フローが正常に動作すること")
    void 申請登録から承認までの正常フロー() throws Exception {
        // 1. 申請登録（下書き）
        String createBody = """
            {
                "itemName": "ノートPC",
                "quantity": 1,
                "unitPrice": 150000,
                "purchaseReason": "開発業務用"
            }
            """;

        String createResponse = mockMvc.perform(post("/api/v1/requests")
                .with(httpBasic("yamada", "password"))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(createBody))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.status").value("success"))
            .andExpect(jsonPath("$.data.status").value("DRAFT"))
            .andReturn().getResponse().getContentAsString();

        Long requestId = objectMapper.readTree(createResponse).path("data").path("id").asLong();

        // 2. 申請提出
        mockMvc.perform(post("/api/v1/requests/" + requestId + "/submit")
                .with(httpBasic("yamada", "password"))
                .with(csrf()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("SUBMITTED"));

        // 3. 承認
        mockMvc.perform(patch("/api/v1/requests/" + requestId + "/approve")
                .with(httpBasic("sato", "password"))
                .with(csrf()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("APPROVED"));

        // DB確認
        PurchaseRequest saved = purchaseRequestRepository.findById(requestId).orElseThrow();
        assertThat(saved.getStatus()).isEqualTo(RequestStatus.APPROVED);
        assertThat(saved.getApproverId()).isEqualTo(approverUser.getId());
    }

    @Test
    @DisplayName("申請登録→提出→却下フローが正常に動作すること")
    void 申請登録から却下までの正常フロー() throws Exception {
        // 1. 申請登録
        String createBody = """
            {
                "itemName": "モニター",
                "quantity": 2,
                "unitPrice": 30000,
                "purchaseReason": "デュアルモニター用"
            }
            """;

        String createResponse = mockMvc.perform(post("/api/v1/requests")
                .with(httpBasic("yamada", "password"))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(createBody))
            .andExpect(status().isCreated())
            .andReturn().getResponse().getContentAsString();

        Long requestId = objectMapper.readTree(createResponse).path("data").path("id").asLong();

        // 2. 提出
        mockMvc.perform(post("/api/v1/requests/" + requestId + "/submit")
                .with(httpBasic("yamada", "password"))
                .with(csrf()))
            .andExpect(status().isOk());

        // 3. 却下
        String rejectBody = """
            {
                "rejectionReason": "予算超過のため"
            }
            """;

        mockMvc.perform(patch("/api/v1/requests/" + requestId + "/reject")
                .with(httpBasic("sato", "password"))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(rejectBody))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("REJECTED"))
            .andExpect(jsonPath("$.data.rejectionReason").value("予算超過のため"));

        // DB確認
        PurchaseRequest saved = purchaseRequestRepository.findById(requestId).orElseThrow();
        assertThat(saved.getStatus()).isEqualTo(RequestStatus.REJECTED);
        assertThat(saved.getRejectionReason()).isEqualTo("予算超過のため");
    }

    @Test
    @DisplayName("一般ユーザーが承認APIにアクセスすると403が返ること")
    void 権限なしユーザーによる承認アクセス拒否() throws Exception {
        mockMvc.perform(patch("/api/v1/requests/1/approve")
                .with(httpBasic("yamada", "password"))
                .with(csrf()))
            .andExpect(status().isForbidden());
    }
}
