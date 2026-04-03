package com.example.purchaserequest.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DeletedPurchaseRequestDto {

    private Long id;

    private Boolean deleted;

    private LocalDateTime deletedAt;
}
