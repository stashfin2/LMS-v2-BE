/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.fineract.portfolio.savings.custom.data;

import lombok.Data;
import java.util.List;
import io.swagger.v3.oas.annotations.media.Schema;

@Data
public class FullCreateSavingsRequest {

    @Schema(example = "1")
    private Long productId;

    @Schema(example = "25 November 2025")
    private String submittedOnDate;

    @Schema(example = "1234567890")
    private String externalId;

    @Schema(example = "1000")
    private Long overdraftLimit;

    private List<ChargeData> charges;

    @Schema(example = "dd MMMM yyyy")
    private String dateFormat;

    @Schema(example = "en")
    private String locale;

    @Schema(example = "7")
    private Long clientId;
}
