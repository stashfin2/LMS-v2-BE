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
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import jakarta.persistence.PersistenceException;

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
            try {
                LOG.info("Step 1: Creating savings account application for clientId: {}, productId: {}",
                        r.getClientId(), r.getProductId());
                LOG.debug("Building create JSON command for savings account");
                var createCmd = fromApiJson(buildCreateJson(r));
                LOG.debug("Create command JSON: {}", createCmd.json());

                LOG.info("Calling downstream service: submitApplication for savings account creation");
                var createResult = savingsApplicationProcessWritePlatformService.submitApplication(createCmd);

                if (createResult == null) {
                    LOG.error(
                            "Downstream service submitApplication returned null result for clientId: {}, productId: {}",
                            r.getClientId(), r.getProductId());
                    throw new FullCreateSavingsException("create", "Savings account creation returned null result");
                }

                savingsId = createResult.getResourceId();
                if (savingsId == null) {
                    LOG.error(
                            "Downstream service submitApplication returned null savingsAccountId for clientId: {}, productId: {}",
                            r.getClientId(), r.getProductId());
                    throw new FullCreateSavingsException("create",
                            "Savings account creation returned null savingsAccountId");
                }

                LOG.info(
                        "Savings account application created successfully. SavingsAccountId: {}, ClientId: {}, ProductId: {}",
                        savingsId, r.getClientId(), r.getProductId());
            } catch (FullCreateSavingsException ex) {
                LOG.error("Failed to create savings account application. ClientId: {}, ProductId: {}, Error: {}",
                        r.getClientId(), r.getProductId(), ex.getMessage(), ex);
                throw ex;
            } catch (DataAccessException | PersistenceException ex) {
                String errorDetails = extractDataIntegrityErrorDetails(ex);
                LOG.error(
                        "Data integrity violation while creating savings account. ClientId: {}, ProductId: {}, ExternalId: {}, Error: {}, ErrorDetails: {}, ExceptionType: {}",
                        r.getClientId(), r.getProductId(), r.getExternalId(), ex.getMessage(), errorDetails,
                        ex.getClass().getName(), ex);
                throw new FullCreateSavingsException("create", "Data integrity issue: " + errorDetails);
            } catch (Exception ex) {
                LOG.error(
                        "Failed to create savings account application. ClientId: {}, ProductId: {}, Error: {}, ExceptionType: {}",
                        r.getClientId(), r.getProductId(), ex.getMessage(), ex.getClass().getName(), ex);
                throw new FullCreateSavingsException("create", "Failed to create savings account: " + ex.getMessage());
            }

            // APPROVE
            try {
                LOG.info("Step 2: Approving savings account. SavingsAccountId: {}, ClientId: {}", savingsId,
                        r.getClientId());
                LOG.debug("Building approve JSON command for savingsAccountId: {}", savingsId);
                var approveCmd = fromApiJson(buildApproveJson(r));
                LOG.debug("Approve command JSON: {}", approveCmd.json());

                LOG.info("Calling downstream service: approveApplication for savingsAccountId: {}", savingsId);
                var approveResult = savingsApplicationProcessWritePlatformService.approveApplication(savingsId,
                        approveCmd);

                if (approveResult == null) {
                    LOG.warn("Downstream service approveApplication returned null result for savingsAccountId: {}",
                            savingsId);
                } else {
                    LOG.debug("Approve result: ResourceId={}, OfficeId={}, ClientId={}",
                            approveResult.getResourceId(), approveResult.getOfficeId(), approveResult.getClientId());
                }

                LOG.info("Savings account approved successfully. SavingsAccountId: {}, ClientId: {}", savingsId,
                        r.getClientId());
            } catch (FullCreateSavingsException ex) {
                LOG.error("Failed to approve savings account. SavingsAccountId: {}, ClientId: {}, Error: {}",
                        savingsId, r.getClientId(), ex.getMessage(), ex);
                throw ex;
            } catch (DataAccessException | PersistenceException ex) {
                String errorDetails = extractDataIntegrityErrorDetails(ex);
                LOG.error(
                        "Data integrity violation while approving savings account. SavingsAccountId: {}, ClientId: {}, Error: {}, ErrorDetails: {}, ExceptionType: {}",
                        savingsId, r.getClientId(), ex.getMessage(), errorDetails, ex.getClass().getName(), ex);
                throw new FullCreateSavingsException("approve",
                        "Data integrity issue during approval: " + errorDetails);
            } catch (Exception ex) {
                LOG.error(
                        "Failed to approve savings account. SavingsAccountId: {}, ClientId: {}, Error: {}, ExceptionType: {}",
                        savingsId, r.getClientId(), ex.getMessage(), ex.getClass().getName(), ex);
                throw new FullCreateSavingsException("approve",
                        "Failed to approve savings account: " + ex.getMessage());
            }

            // ACTIVATE
            try {
                LOG.info("Step 3: Activating savings account. SavingsAccountId: {}, ClientId: {}", savingsId,
                        r.getClientId());
                LOG.debug("Building activate JSON command for savingsAccountId: {}", savingsId);
                var activateCmd = fromApiJson(buildActivateJson(r));
                LOG.debug("Activate command JSON: {}", activateCmd.json());

                LOG.info("Calling downstream service: activate for savingsAccountId: {}", savingsId);
                var activateResult = savingsAccountWritePlatformService.activate(savingsId, activateCmd);

                if (activateResult == null) {
                    LOG.warn("Downstream service activate returned null result for savingsAccountId: {}", savingsId);
                } else {
                    LOG.debug("Activate result: ResourceId={}, OfficeId={}, ClientId={}",
                            activateResult.getResourceId(), activateResult.getOfficeId(), activateResult.getClientId());
                }

                LOG.info("Savings account activated successfully. SavingsAccountId: {}, ClientId: {}", savingsId,
                        r.getClientId());
            } catch (FullCreateSavingsException ex) {
                LOG.error("Failed to activate savings account. SavingsAccountId: {}, ClientId: {}, Error: {}",
                        savingsId, r.getClientId(), ex.getMessage(), ex);
                throw ex;
            } catch (DataAccessException | PersistenceException ex) {
                String errorDetails = extractDataIntegrityErrorDetails(ex);
                LOG.error(
                        "Data integrity violation while activating savings account. SavingsAccountId: {}, ClientId: {}, Error: {}, ErrorDetails: {}, ExceptionType: {}",
                        savingsId, r.getClientId(), ex.getMessage(), errorDetails, ex.getClass().getName(), ex);
                throw new FullCreateSavingsException("activate",
                        "Data integrity issue during activation: " + errorDetails);
            } catch (Exception ex) {
                LOG.error(
                        "Failed to activate savings account. SavingsAccountId: {}, ClientId: {}, Error: {}, ExceptionType: {}",
                        savingsId, r.getClientId(), ex.getMessage(), ex.getClass().getName(), ex);
                throw new FullCreateSavingsException("activate",
                        "Failed to activate savings account: " + ex.getMessage());
            }

            // SUCCESS RESPONSE
            LOG.info(
                    "Full-create savings account process completed successfully. SavingsAccountId: {}, ClientId: {}, ProductId: {}",
                    savingsId, r.getClientId(), r.getProductId());
            return FullCreateSavingsUnifiedResponse.builder()
                    .status("success")
                    .savingsAccountId(savingsId)
                    .creationStatus("created")
                    .approvalStatus("approved")
                    .activationStatus("activated")
                    .build();

        } catch (FullCreateSavingsException ex) {
            LOG.error(
                    "Full-create savings account failed at step: {}. SavingsAccountId: {}, ClientId: {}, ProductId: {}, Error: {}",
                    ex.getResponse().getStep(), savingsId, r.getClientId(), r.getProductId(), ex.getMessage(), ex);
            throw ex; // Let API layer convert to 200/400 JSON
        } catch (DataAccessException | PersistenceException ex) {
            String errorDetails = extractDataIntegrityErrorDetails(ex);
            LOG.error(
                    "Data integrity violation during full-create savings account. SavingsAccountId: {}, ClientId: {}, ProductId: {}, ExternalId: {}, Error: {}, ErrorDetails: {}, ExceptionType: {}",
                    savingsId, r.getClientId(), r.getProductId(), r.getExternalId(), ex.getMessage(), errorDetails,
                    ex.getClass().getName(), ex);
            throw new FullCreateSavingsException("create", "Data integrity issue: " + errorDetails);
        } catch (Exception ex) {
            LOG.error(
                    "Full-create savings account failed with unexpected error. SavingsAccountId: {}, ClientId: {}, ProductId: {}, Error: {}, ExceptionType: {}",
                    savingsId, r.getClientId(), r.getProductId(), ex.getMessage(), ex.getClass().getName(), ex);
            throw new FullCreateSavingsException("create",
                    "Unexpected error during full-create savings account: " + ex.getMessage());
        }
    }

    /**
     * Extracts detailed error information from data integrity exceptions.
     * This helps identify specific constraint violations like duplicate externalId,
     * accountNo, etc.
     */
    private String extractDataIntegrityErrorDetails(Throwable ex) {
        try {
            StringBuilder details = new StringBuilder();
            Throwable rootCause = ex;
            int depth = 0;
            final int maxDepth = 10;

            // Navigate to root cause
            while (rootCause.getCause() != null && rootCause.getCause() != rootCause && depth < maxDepth) {
                rootCause = rootCause.getCause();
                depth++;
            }

            String rootMessage = rootCause != null ? rootCause.getMessage() : "Unknown";
            details.append("RootCause: ").append(rootMessage);

            // Check for common constraint violations
            String message = rootMessage != null ? rootMessage.toLowerCase() : "";
            if (message.contains("sa_external_id_unique")
                    || (message.contains("external_id") && message.contains("unique"))) {
                details.append(" | Constraint: Duplicate externalId");
            } else if (message.contains("sa_account_no_unique")
                    || (message.contains("account_no") && message.contains("unique"))) {
                details.append(" | Constraint: Duplicate accountNo");
            } else if (message.contains("foreign key") || message.contains("fk_")) {
                details.append(" | Constraint: Foreign key violation");
            } else if (message.contains("unique constraint") || message.contains("unique index")) {
                details.append(" | Constraint: Unique constraint violation");
            } else if (message.contains("not null")) {
                details.append(" | Constraint: Not null violation");
            } else if (message.contains("check constraint")) {
                details.append(" | Constraint: Check constraint violation");
            }

            // Log full exception chain for debugging
            if (LOG.isDebugEnabled()) {
                details.append(" | ExceptionChain: ");
                Throwable current = ex;
                int chainDepth = 0;
                while (current != null && chainDepth < maxDepth) {
                    if (chainDepth > 0)
                        details.append(" -> ");
                    details.append(current.getClass().getSimpleName());
                    if (current.getMessage() != null && !current.getMessage().equals(rootMessage)) {
                        details.append("(").append(current.getMessage()).append(")");
                    }
                    current = current.getCause();
                    chainDepth++;
                }
            }

            return details.toString();
        } catch (Exception e) {
            LOG.warn("Failed to extract data integrity error details", e);
            return "Error extracting details: " + ex.getMessage();
        }
    }

    private JsonCommand fromApiJson(String json) {
        try {
            LOG.debug("Parsing JSON string to JsonElement");
            final JsonElement element = fromJsonHelper.parse(json);
            if (element == null) {
                LOG.error("Failed to parse JSON: fromJsonHelper.parse returned null");
                throw new FullCreateSavingsException("create", "Failed to parse JSON: returned null element");
            }
            LOG.debug("Creating JsonCommand from parsed JSON element");
            JsonCommand command = JsonCommand.from(json, element, fromJsonHelper, null, null, null, null,
                    null, null, null, null, null, null, null, null, null, null);
            if (command == null) {
                LOG.error("Failed to create JsonCommand: JsonCommand.from returned null");
                throw new FullCreateSavingsException("create", "Failed to create JsonCommand: returned null");
            }
            LOG.debug("Successfully created JsonCommand");
            return command;
        } catch (FullCreateSavingsException ex) {
            LOG.error("Failed to parse JSON to JsonCommand. Error: {}", ex.getMessage(), ex);
            throw ex;
        } catch (Exception ex) {
            LOG.error("Failed to parse JSON to JsonCommand. JSON length: {}, Error: {}, ExceptionType: {}",
                    json != null ? json.length() : 0, ex.getMessage(), ex.getClass().getName(), ex);
            throw new FullCreateSavingsException("create", "Failed to parse JSON: " + ex.getMessage());
        }
    }

    private String buildCreateJson(FullCreateSavingsRequest r) {
        try {
            LOG.debug("Building create JSON for clientId: {}, productId: {}", r.getClientId(), r.getProductId());
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
                LOG.debug("Adding {} charges to create JSON", r.getCharges().size());
                json.append("\"charges\":[");
                for (int i = 0; i < r.getCharges().size(); i++) {
                    ChargeData charge = r.getCharges().get(i);
                    if (i > 0)
                        json.append(",");
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
            String result = json.toString();
            LOG.debug("Successfully built create JSON with length: {}", result.length());
            return result;
        } catch (Exception ex) {
            LOG.error("Failed to build create JSON for clientId: {}, productId: {}, Error: {}, ExceptionType: {}",
                    r.getClientId(), r.getProductId(), ex.getMessage(), ex.getClass().getName(), ex);
            throw new FullCreateSavingsException("create", "Failed to build create JSON: " + ex.getMessage());
        }
    }

    private String buildApproveJson(FullCreateSavingsRequest r) {
        try {
            LOG.debug("Building approve JSON");
            String dateFormat = r.getDateFormat() != null ? r.getDateFormat() : "dd MMMM yyyy";
            String result = "{ \"approvedOnDate\": \"" + todayString()
                    + "\", \"note\": \"Auto-approved\", \"dateFormat\":\""
                    + dateFormat + "\", \"locale\":\"" + (r.getLocale() != null ? r.getLocale() : "en") + "\" }";
            LOG.debug("Successfully built approve JSON");
            return result;
        } catch (Exception ex) {
            LOG.error("Failed to build approve JSON. Error: {}, ExceptionType: {}", ex.getMessage(),
                    ex.getClass().getName(), ex);
            throw new FullCreateSavingsException("approve", "Failed to build approve JSON: " + ex.getMessage());
        }
    }

    private String buildActivateJson(FullCreateSavingsRequest r) {
        try {
            LOG.debug("Building activate JSON");
            String dateFormat = r.getDateFormat() != null ? r.getDateFormat() : "dd MMMM yyyy";
            String result = "{ \"activatedOnDate\": \"" + todayString() + "\", \"dateFormat\":\"" + dateFormat
                    + "\", \"locale\":\""
                    + (r.getLocale() != null ? r.getLocale() : "en") + "\" }";
            LOG.debug("Successfully built activate JSON");
            return result;
        } catch (Exception ex) {
            LOG.error("Failed to build activate JSON. Error: {}, ExceptionType: {}", ex.getMessage(),
                    ex.getClass().getName(), ex);
            throw new FullCreateSavingsException("activate", "Failed to build activate JSON: " + ex.getMessage());
        }
    }

    private String todayString() {
        try {
            // produce "25 November 2025" style or configured format; use DateTimeFormatter
            String result = java.time.LocalDate.now()
                    .format(java.time.format.DateTimeFormatter.ofPattern("dd MMMM yyyy"));
            LOG.debug("Generated today's date string: {}", result);
            return result;
        } catch (Exception ex) {
            LOG.error("Failed to generate today's date string. Error: {}, ExceptionType: {}",
                    ex.getMessage(), ex.getClass().getName(), ex);
            throw new FullCreateSavingsException("create", "Failed to generate date string: " + ex.getMessage());
        }
    }
}
