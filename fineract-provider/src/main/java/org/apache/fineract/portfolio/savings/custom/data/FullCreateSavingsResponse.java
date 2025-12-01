package org.apache.fineract.portfolio.savings.custom.data;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class FullCreateSavingsResponse {
    private String status;
    private Long savingsAccountId;
    private String creationStatus;
    private String approvalStatus;
    private String activationStatus;
    private String step;    
    private String message;  
}
