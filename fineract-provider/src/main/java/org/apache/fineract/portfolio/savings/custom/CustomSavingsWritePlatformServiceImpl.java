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
package org.apache.fineract.portfolio.savings.custom;

import com.google.gson.JsonElement;
import lombok.RequiredArgsConstructor;
import org.apache.fineract.infrastructure.core.api.JsonCommand;
import org.apache.fineract.infrastructure.core.serialization.FromJsonHelper;
import org.apache.fineract.portfolio.savings.custom.data.FullCreateSavingsRequest;
import org.apache.fineract.portfolio.savings.custom.data.FullCreateSavingsUnifiedResponse;
import org.apache.fineract.portfolio.savings.custom.exception.FullCreateSavingsException;
import org.apache.fineract.portfolio.savings.service.SavingsAccountWritePlatformService;
import org.apache.fineract.portfolio.savings.service.SavingsApplicationProcessWritePlatformService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CustomSavingsWritePlatformServiceImpl implements CustomSavingsWritePlatformService {

    private final FromJsonHelper fromJsonHelper;
    private final SavingsApplicationProcessWritePlatformService savingsApplicationProcessWritePlatformService;
    private final SavingsAccountWritePlatformService savingsAccountWritePlatformService;

    @Override
    @Transactional
    public FullCreateSavingsUnifiedResponse createFullSavings(FullCreateSavingsRequest r) {
        Long savingsId = null;
        try {
            // CREATE
            var createCmd = fromApiJson(buildCreateJson(r));
            var createResult = savingsApplicationProcessWritePlatformService.submitApplication(createCmd);
            savingsId = createResult.getResourceId();

            // APPROVE
            try {
                var approveCmd = fromApiJson(buildApproveJson(r));
                savingsApplicationProcessWritePlatformService.approveApplication(savingsId, approveCmd);
            } catch (Exception ex) {
                throw new FullCreateSavingsException("approve", ex.getMessage());
            }

            // ACTIVATE
            try {
                var activateCmd = fromApiJson(buildActivateJson(r));
                savingsAccountWritePlatformService.activate(savingsId, activateCmd);
            } catch (Exception ex) {
                throw new FullCreateSavingsException("activate", ex.getMessage());
            }

            // SUCCESS RESPONSE
            return FullCreateSavingsUnifiedResponse.builder()
                    .status("success")
                    .savingsAccountId(savingsId)
                    .creationStatus("created")
                    .approvalStatus("approved")
                    .activationStatus("activated")
                    .build();

        } catch (FullCreateSavingsException ex) {
            throw ex; // Let API layer convert to 200/400 JSON
        } catch (Exception ex) {
            throw new FullCreateSavingsException("create", ex.getMessage());
        }
    }

    private JsonCommand fromApiJson(String json) {
        final JsonElement element = fromJsonHelper.parse(json);
        return JsonCommand.from(json, element, fromJsonHelper, null, null, null, null,
                null, null, null, null, null, null, null, null, null, null);
    }

    private String buildCreateJson(FullCreateSavingsRequest r) {
        // Build the JSON string matching Fineract /savingsaccounts input.
        String submittedOnDate = r.getSubmittedOnDate() != null ? r.getSubmittedOnDate() : todayString();
        String dateFormat = r.getDateFormat() != null ? r.getDateFormat() : "dd MMMM yyyy";
        String locale = r.getLocale() != null ? r.getLocale() : "en";

        StringBuilder json = new StringBuilder("{");
        json.append("\"clientId\": ").append(r.getClientId()).append(",");
        json.append("\"productId\": ").append(r.getProductId()).append(",");
        json.append("\"submittedOnDate\":\"").append(submittedOnDate).append("\",");
        json.append("\"savingsAccountDetails\":{");
        json.append("\"externalId\":\"").append(r.getExternalId()).append("\",");
        if (r.getOverdraftLimit() != null) {
            json.append("\"allowOverdraft\": true,");
            json.append("\"overdraftLimit\": ").append(r.getOverdraftLimit()).append(",");
        }
        json.append("\"dateFormat\":\"").append(dateFormat).append("\",");
        json.append("\"locale\":\"").append(locale).append("\"");
        json.append("}");
        json.append("}");
        return json.toString();
    }

    private String buildApproveJson(FullCreateSavingsRequest r) {
        String dateFormat = r.getDateFormat() != null ? r.getDateFormat() : "dd MMMM yyyy";
        return "{ \"approvedOnDate\": \"" + todayString() + "\", \"note\": \"Auto-approved\", \"dateFormat\":\""
                + dateFormat + "\", \"locale\":\"" + (r.getLocale() != null ? r.getLocale() : "en") + "\" }";
    }

    private String buildActivateJson(FullCreateSavingsRequest r) {
        String dateFormat = r.getDateFormat() != null ? r.getDateFormat() : "dd MMMM yyyy";
        return "{ \"activatedOnDate\": \"" + todayString() + "\", \"dateFormat\":\"" + dateFormat + "\", \"locale\":\""
                + (r.getLocale() != null ? r.getLocale() : "en") + "\" }";
    }

    private String todayString() {
        // produce "25 November 2025" style or configured format; use DateTimeFormatter
        return java.time.LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("dd MMMM yyyy"));
    }
}
