package org.apache.fineract.portfolio.savings.custom.data;

import lombok.Data;
import java.util.List;

@Data
public class FullCreateSavingsRequest {

    private Long productId;
    private String submittedOnDate;

    private String externalId;
    private Long overdraftLimit;

    private List<ChargeData> charges;

    private String dateFormat;
    private String locale;

    private Long clientId;
}
