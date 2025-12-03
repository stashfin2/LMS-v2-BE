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
import org.apache.fineract.portfolio.savings.custom.data.ChargeData;
import org.apache.fineract.portfolio.savings.custom.data.FullCreateSavingsRequest;
import org.apache.fineract.portfolio.savings.custom.data.FullCreateSavingsUnifiedResponse;
import org.apache.fineract.portfolio.savings.custom.exception.FullCreateSavingsException;
import org.apache.fineract.portfolio.savings.service.SavingsAccountWritePlatformService;
import org.apache.fineract.portfolio.savings.service.SavingsApplicationProcessWritePlatformService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CustomSavingsWritePlatformServiceImpl implements CustomSavingsWritePlatformService {

    private static final Logger LOG = LoggerFactory.getLogger(CustomSavingsWritePlatformServiceImpl.class);

    private final FromJsonHelper fromJsonHelper;
    private final SavingsApplicationProcessWritePlatformService savingsApplicationProcessWritePlatformService;
    private final SavingsAccountWritePlatformService savingsAccountWritePlatformService;

    @Override
    @Transactional
    public FullCreateSavingsUnifiedResponse createFullSavings(FullCreateSavingsRequest r) {
        LOG.info("Starting full-create savings account process for clientId: {}, productId: {}, externalId: {}",
                r.getClientId(), r.getProductId(), r.getExternalId());

        Long savingsId = null;
        try {
            // CREATE
            LOG.info("Step 1: Creating savings account application");
            var createCmd = fromApiJson(buildCreateJson(r));
            LOG.info("Create command: {}", createCmd.json());
            var createResult = savingsApplicationProcessWritePlatformService.submitApplication(createCmd);
            
            savingsId = createResult.getResourceId();
            LOG.info("Savings account application created successfully. SavingsAccountId: {}", savingsId);

            // APPROVE
            try {
                LOG.debug("Step 2: Approving savings account. SavingsAccountId: {}", savingsId);
                var approveCmd = fromApiJson(buildApproveJson(r));
                savingsApplicationProcessWritePlatformService.approveApplication(savingsId, approveCmd);
                LOG.info("Savings account approved successfully. SavingsAccountId: {}", savingsId);
            } catch (Exception ex) {
                LOG.error("Failed to approve savings account. SavingsAccountId: {}, Error: {}",
                        savingsId, ex.getMessage(), ex);
                throw new FullCreateSavingsException("approve", ex.getMessage());
            }

            // ACTIVATE
            try {
                LOG.debug("Step 3: Activating savings account. SavingsAccountId: {}", savingsId);
                var activateCmd = fromApiJson(buildActivateJson(r));
                savingsAccountWritePlatformService.activate(savingsId, activateCmd);
                LOG.info("Savings account activated successfully. SavingsAccountId: {}", savingsId);
            } catch (Exception ex) {
                LOG.error("Failed to activate savings account. SavingsAccountId: {}, Error: {}",
                        savingsId, ex.getMessage(), ex);
                throw new FullCreateSavingsException("activate", ex.getMessage());
            }

            // SUCCESS RESPONSE
            LOG.info("Full-create savings account process completed successfully. SavingsAccountId: {}", savingsId);
            return FullCreateSavingsUnifiedResponse.builder()
                    .status("success")
                    .savingsAccountId(savingsId)
                    .creationStatus("created")
                    .approvalStatus("approved")
                    .activationStatus("activated")
                    .build();

        } catch (FullCreateSavingsException ex) {
            LOG.error("Full-create savings account failed at step: {}. SavingsAccountId: {}, Error: {}",
                    ex.getResponse().getStep(), savingsId, ex.getMessage(), ex);
            throw ex; // Let API layer convert to 200/400 JSON
        } catch (Exception ex) {
            LOG.error("Full-create savings account failed with unexpected error. SavingsAccountId: {}, Error: {}",
                    savingsId, ex.getMessage(), ex);
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
        if (r.getExternalId() != null) {
            json.append("\"externalId\":\"").append(r.getExternalId()).append("\",");
        }
        if (r.getOverdraftLimit() != null) {
            json.append("\"allowOverdraft\": true,");
            json.append("\"overdraftLimit\": ").append(r.getOverdraftLimit()).append(",");
        }
        if (r.getCharges() != null && !r.getCharges().isEmpty()) {
            json.append("\"charges\":[");
            for (int i = 0; i < r.getCharges().size(); i++) {
                ChargeData charge = r.getCharges().get(i);
                if (i > 0) json.append(",");
                json.append("{");
                json.append("\"chargeId\":").append(charge.getChargeId());
                if (charge.getAmount() != null) {
                    json.append(",\"amount\":").append(charge.getAmount());
                }
                if (charge.getDueDate() != null) {
                    json.append(",\"dueDate\":\"").append(charge.getDueDate()).append("\"");
                }
                json.append("}");
            }
            json.append("],");
        }
        json.append("\"dateFormat\":\"").append(dateFormat).append("\",");
        json.append("\"locale\":\"").append(locale).append("\"");
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
