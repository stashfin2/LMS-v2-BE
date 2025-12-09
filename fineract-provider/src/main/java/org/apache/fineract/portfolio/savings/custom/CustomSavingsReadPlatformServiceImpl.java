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

import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.apache.fineract.infrastructure.security.service.PlatformSecurityContext;
import org.apache.fineract.portfolio.savings.SavingsAccountTransactionType;
import org.apache.fineract.portfolio.savings.custom.data.CustomSavingsTransactionData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CustomSavingsReadPlatformServiceImpl implements CustomSavingsReadPlatformService {

    private static final Logger LOG = LoggerFactory.getLogger(CustomSavingsReadPlatformServiceImpl.class);

    private static final String TRANSACTION_QUERY = """
            SELECT
                t.id AS transaction_id,
                t.transaction_date,
                t.amount,
                t.running_balance_derived,
                t.transaction_type_enum,
                n.note AS note,
                c.name AS charge_name
            FROM m_savings_account_transaction t
            LEFT JOIN m_note n ON n.savings_account_transaction_id = t.id
            LEFT JOIN m_savings_account_charge_paid_by cp ON cp.savings_account_transaction_id = t.id
            LEFT JOIN m_savings_account_charge sc ON sc.id = cp.savings_account_charge_id
            LEFT JOIN m_charge c ON c.id = sc.charge_id
            WHERE t.savings_account_id = ? AND t.is_reversed = 0
            ORDER BY t.transaction_date, t.id
            """;

    private final PlatformSecurityContext context;
    private final JdbcTemplate jdbcTemplate;

    @Override
    @Transactional(readOnly = true)
    public List<CustomSavingsTransactionData> retrieveTransactions(Long savingsAccountId) {
        context.authenticatedUser();
        LOG.info("Fetching custom savings transactions for savingsAccountId={}", savingsAccountId);
        try {
            return jdbcTemplate.query(TRANSACTION_QUERY, transactionRowMapper(), savingsAccountId);
        } catch (DataAccessException ex) {
            LOG.error("Failed to fetch custom savings transactions for savingsAccountId={}. Error: {}",
                    savingsAccountId, ex.getMessage(),
                    ex);
            throw ex;
        }
    }

    private RowMapper<CustomSavingsTransactionData> transactionRowMapper() {
        return new RowMapper<>() {
            @Override
            public CustomSavingsTransactionData mapRow(ResultSet rs, int rowNum) throws SQLException {
                Date sqlDate = rs.getDate("transaction_date");
                LocalDate transactionDate = sqlDate != null ? sqlDate.toLocalDate() : null;
                Integer typeEnum = (Integer) rs.getObject("transaction_type_enum");
                String note = rs.getString("note");
                String chargeName = rs.getString("charge_name");
                return CustomSavingsTransactionData.builder()
                        .transactionId(rs.getLong("transaction_id"))
                        .transactionDate(transactionDate)
                        .amount(rs.getBigDecimal("amount"))
                        .runningBalanceDerived(rs.getBigDecimal("running_balance_derived"))
                        .transactionTypeEnum(typeEnum)
                        .transactionTypeLabel(mapTransactionLabel(typeEnum, note, chargeName))
                        .note(note)
                        .chargeName(chargeName)
                        .build();
            }
        };
    }

    private String mapTransactionLabel(Integer typeEnum, String note, String chargeName) {
        if (typeEnum == null) {
            return "UNKNOWN";
        }
        SavingsAccountTransactionType type = SavingsAccountTransactionType.fromInt(typeEnum);
        return switch (type) {
            case WITHDRAWAL_FEE -> withOptional("WITHDRAWAL_FEE", chargeName);
            case PAY_CHARGE -> withOptional("CHARGE", chargeName);
            case DEPOSIT -> withOptional("DEPOSIT", note);
            case WITHDRAWAL -> "WITHDRAWAL";
            case INTEREST_POSTING -> "INTEREST_POSTING";
            case ANNUAL_FEE -> "ANNUAL_FEE";
            case WAIVE_CHARGES -> "WAIVE_CHARGES";
            case APPROVE_TRANSFER -> "APPROVE_TRANSFER";
            case WITHDRAW_TRANSFER -> "WITHDRAW_TRANSFER";
            case REJECT_TRANSFER -> "DECLINE_TRANSFER";
            case DIVIDEND_PAYOUT -> "DIVIDEND_POSTING";
            case OVERDRAFT_INTEREST -> "OVERDRAFT_INTEREST";
            case WITHHOLD_TAX -> "WITHHOLD_TAX";
            case ESCHEAT -> "ESCHEAT";
            case INITIATE_TRANSFER -> "INITIATE_TRANSFER";
            case ACCRUAL -> "ACCRUAL";
            case WRITTEN_OFF -> "WRITTEN_OFF";
            case AMOUNT_HOLD -> "AMOUNT_HOLD";
            case AMOUNT_RELEASE -> "AMOUNT_RELEASE";
            default -> "UNKNOWN";
        };
    }

    private String withOptional(String base, String suffix) {
        if (suffix == null || suffix.isBlank()) {
            return base;
        }
        return base + " - " + suffix;
    }
}
