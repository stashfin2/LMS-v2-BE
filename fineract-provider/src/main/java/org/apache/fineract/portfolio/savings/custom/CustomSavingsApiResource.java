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

import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import lombok.RequiredArgsConstructor;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import org.apache.fineract.infrastructure.core.data.ApiGlobalErrorResponse;
import org.apache.fineract.infrastructure.core.serialization.FromJsonHelper;
import org.apache.fineract.portfolio.savings.custom.data.FullCreateSavingsRequest;
import org.apache.fineract.portfolio.savings.custom.data.FullCreateSavingsUnifiedResponse;
import org.apache.fineract.portfolio.savings.custom.exception.FullCreateSavingsException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Component;
import jakarta.persistence.PersistenceException;

@Tag(name = "Custom Savings", description = "Custom API for creating, approving, and activating savings accounts in a single operation.")
@Path("/v1/custom/savings")
@Component
@RequiredArgsConstructor
public class CustomSavingsApiResource {

        private static final Logger LOG = LoggerFactory.getLogger(CustomSavingsApiResource.class);

        private final CustomSavingsWritePlatformService customSavingsWritePlatformService;
        private final FromJsonHelper fromJsonHelper;

        @POST
        @Path("/full-create")
        @Consumes(MediaType.APPLICATION_JSON)
        @Produces(MediaType.APPLICATION_JSON)
        @Operation(summary = "Create, Approve, Activate Savings Account", description = "This API creates a savings account, approves it, and activates it in a single request.", responses = {
                        @ApiResponse(responseCode = "200", description = "Savings account created and activated successfully", content = @Content(mediaType = "application/json", schema = @Schema(implementation = FullCreateSavingsUnifiedResponse.class))),
                        @ApiResponse(responseCode = "400", description = "Validation/Error while creating savings account", content = @Content(mediaType = "application/json", schema = @Schema(implementation = FullCreateSavingsUnifiedResponse.class)))
        })
        public Response fullCreateSavings(final String apiRequestBodyAsJson) {
                LOG.info("Received full-create savings account request. Request body length: {}",
                                apiRequestBodyAsJson != null ? apiRequestBodyAsJson.length() : 0);

                // Basic null/empty check
                if (apiRequestBodyAsJson == null || apiRequestBodyAsJson.trim().isEmpty()) {
                        LOG.warn("Full-create savings account request rejected: empty request body");
                        return Response.status(Response.Status.BAD_REQUEST)
                                        .entity(ApiGlobalErrorResponse.badClientRequest(
                                                        "error.msg.request.body.required",
                                                        "Request body is required"))
                                        .build();
                }

                try {
                        LOG.debug("Deserializing full-create savings account request. JSON length: {}",
                                        apiRequestBodyAsJson.length());
                        // Deserialize JSON to FullCreateSavingsRequest
                        FullCreateSavingsRequest req = null;
                        try {
                                req = fromJsonHelper.fromJson(apiRequestBodyAsJson,
                                                FullCreateSavingsRequest.class);
                                if (req == null) {
                                        LOG.error("Failed to deserialize request: fromJsonHelper.fromJson returned null");
                                        return Response.status(Response.Status.BAD_REQUEST)
                                                        .entity(ApiGlobalErrorResponse.badClientRequest(
                                                                        "error.msg.invalid.request.body",
                                                                        "Failed to deserialize request body"))
                                                        .build();
                                }
                                LOG.debug("Successfully deserialized request. ClientId: {}, ProductId: {}, ExternalId: {}",
                                                req.getClientId(), req.getProductId(), req.getExternalId());
                        } catch (Exception ex) {
                                LOG.error("Failed to deserialize request body. Error: {}, ExceptionType: {}",
                                                ex.getMessage(), ex.getClass().getName(), ex);
                                return Response.status(Response.Status.BAD_REQUEST)
                                                .entity(ApiGlobalErrorResponse.badClientRequest(
                                                                "error.msg.invalid.request.body",
                                                                "Failed to deserialize request body: "
                                                                                + ex.getMessage()))
                                                .build();
                        }

                        LOG.info("Processing full-create savings account for clientId: {}, productId: {}, externalId: {}",
                                        req.getClientId(), req.getProductId(), req.getExternalId());

                        LOG.debug("Calling downstream service: createFullSavings");
                        FullCreateSavingsUnifiedResponse resp = null;
                        try {
                                resp = customSavingsWritePlatformService.createFullSavings(req);
                                if (resp == null) {
                                        LOG.error("Downstream service createFullSavings returned null response for clientId: {}, productId: {}",
                                                        req.getClientId(), req.getProductId());
                                        return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                                                        .entity(ApiGlobalErrorResponse.serverSideError(
                                                                        "error.msg.null.response",
                                                                        "Service returned null response"))
                                                        .build();
                                }
                                LOG.debug("Downstream service returned response. Status: {}, SavingsAccountId: {}",
                                                resp.getStatus(), resp.getSavingsAccountId());
                        } catch (FullCreateSavingsException ex) {
                                LOG.error("Downstream service createFullSavings failed with business error. Step: {}, Message: {}",
                                                ex.getResponse().getStep(), ex.getMessage(), ex);
                                throw ex;
                        } catch (DataAccessException | PersistenceException ex) {
                                String errorDetails = extractDataIntegrityErrorDetails(ex);
                                LOG.error("Data integrity violation in downstream service. ClientId: {}, ProductId: {}, ExternalId: {}, Error: {}, ErrorDetails: {}, ExceptionType: {}",
                                                req.getClientId(), req.getProductId(), req.getExternalId(),
                                                ex.getMessage(), errorDetails, ex.getClass().getName(), ex);
                                // Convert to FullCreateSavingsException to maintain consistent error handling
                                throw new FullCreateSavingsException("create", "Data integrity issue: " + errorDetails);
                        } catch (Exception ex) {
                                LOG.error("Downstream service createFullSavings failed with unexpected error. ClientId: {}, ProductId: {}, Error: {}, ExceptionType: {}",
                                                req.getClientId(), req.getProductId(), ex.getMessage(),
                                                ex.getClass().getName(), ex);
                                throw ex;
                        }

                        LOG.info("Full-create savings account completed successfully. SavingsAccountId: {}, Status: {}",
                                        resp.getSavingsAccountId(), resp.getStatus());

                        return Response.ok(resp).build();

                } catch (FullCreateSavingsException ex) {
                        LOG.error("Full-create savings account failed with business error. Step: {}, Message: {}",
                                        ex.getResponse().getStep(), ex.getMessage(), ex);
                        return Response.status(Response.Status.BAD_REQUEST)
                                        .entity(ex.getResponse())
                                        .build();
                } catch (DataAccessException | PersistenceException ex) {
                        String errorDetails = extractDataIntegrityErrorDetails(ex);
                        LOG.error("Data integrity violation in full-create savings account. Error: {}, ErrorDetails: {}, ExceptionType: {}",
                                        ex.getMessage(), errorDetails, ex.getClass().getName(), ex);
                        return Response.status(Response.Status.BAD_REQUEST)
                                        .entity(ApiGlobalErrorResponse.badClientRequest(
                                                        "error.msg.data.integrity.issue",
                                                        "Data integrity issue: " + errorDetails))
                                        .build();
                } catch (Exception ex) {
                        LOG.error("Full-create savings account failed with unexpected error. Error: {}, ExceptionType: {}",
                                        ex.getMessage(), ex.getClass().getName(), ex);
                        return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                                        .entity(ApiGlobalErrorResponse.serverSideError("error.msg.unexpected.error",
                                                        "An unexpected error occurred: " + ex.getMessage()))
                                        .build();
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
}
