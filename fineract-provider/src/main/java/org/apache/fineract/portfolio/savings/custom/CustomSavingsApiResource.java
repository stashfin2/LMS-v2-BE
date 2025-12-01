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
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Tag(name = "Custom Savings", description = "Custom API for creating, approving, and activating savings accounts in a single operation.")
@Path("/custom/savings")
@Component
@RequiredArgsConstructor
public class CustomSavingsApiResource {

    private final CustomSavingsWritePlatformService customSavingsWritePlatformService;
    private final FromJsonHelper fromJsonHelper;

    @POST
    @Path("/full-create")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Transactional
    @Operation(summary = "Create, Approve, Activate Savings Account", description = "This API creates a savings account, approves it, and activates it in a single request.", responses = {
            @ApiResponse(responseCode = "200", description = "Savings account created and activated successfully", content = @Content(mediaType = "application/json", schema = @Schema(implementation = FullCreateSavingsUnifiedResponse.class))),
            @ApiResponse(responseCode = "400", description = "Validation/Error while creating savings account", content = @Content(mediaType = "application/json", schema = @Schema(implementation = FullCreateSavingsUnifiedResponse.class)))
    })
    public Response fullCreateSavings(final String apiRequestBodyAsJson) {

        // Basic null/empty check
        if (apiRequestBodyAsJson == null || apiRequestBodyAsJson.trim().isEmpty()) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(ApiGlobalErrorResponse.badClientRequest("error.msg.request.body.required",
                            "Request body is required"))
                    .build();
        }

        try {
            // Deserialize JSON to FullCreateSavingsRequest
            FullCreateSavingsRequest req = fromJsonHelper.fromJson(apiRequestBodyAsJson,
                    FullCreateSavingsRequest.class);

            FullCreateSavingsUnifiedResponse resp = customSavingsWritePlatformService.createFullSavings(req);
            return Response.ok(resp).build();

        } catch (FullCreateSavingsException ex) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(ex.getResponse())
                    .build();
        } catch (Exception ex) {
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(ApiGlobalErrorResponse.serverSideError("error.msg.unexpected.error",
                            "An unexpected error occurred: " + ex.getMessage()))
                    .build();
        }
    }
}
