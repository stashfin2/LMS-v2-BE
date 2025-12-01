package org.apache.fineract.portfolio.savings.custom.data;

import lombok.Data;

@Data
public class ChargeData {
    private Long chargeId;
    private Long amount;
    private String dueDate;
}
