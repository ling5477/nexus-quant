package com.guidinglight.nexusquant.validationreview.application;

import com.guidinglight.nexusquant.validationreview.domain.ValidationReviewException;
import com.guidinglight.nexusquant.validationreview.domain.ValidationReviewTransitionCommand;
import com.guidinglight.nexusquant.validationreview.domain.ValidationReviewTransitionResult;
import com.guidinglight.nexusquant.validationreview.domain.port.ValidationReviewRepository;

import java.util.Objects;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * GateV-2 可复用的 durable review transition application boundary。
 *
 * <p>本 service 不暴露 HTTP，不自动创建 case；只把可信 tenant/owner scope 交给 repository，
 * 并确保 accepted case update 与 event append 共享事务。
 */
@Service
public class ValidationReviewTransitionService {

    private final ValidationReviewRepository repository;

    /**
     * @param repository durable local review repository；不得具有外部交易或 credential 副作用
     */
    public ValidationReviewTransitionService(ValidationReviewRepository repository) {
        this.repository = Objects.requireNonNull(repository, "repository must not be null");
    }

    /**
     * 执行 OPERATOR owner-scoped transition。
     *
     * <p>actor 必须等于 owner；不匹配时 fail-closed，且不访问 repository。
     *
     * @param command 服务端构造的 transition command
     * @return accepted 或幂等 replay 结果
     */
    @Transactional
    public ValidationReviewTransitionResult transitionOwned(ValidationReviewTransitionCommand command) {
        Objects.requireNonNull(command, "command must not be null");
        if (command.actorId() != command.ownerId()) {
            throw new ValidationReviewException(
                    "REVIEW_ACTION_FORBIDDEN",
                    "operator actor must match review case owner",
                    command.reviewCaseId(),
                    null,
                    command.targetState()
            );
        }
        return repository.transitionOwned(command);
    }

    /**
     * 执行 ADMIN same-tenant transition。
     *
     * <p>ADMIN 鉴权由后续可信 adapter 完成；repository 仍强制 tenant scope，不能跨 tenant。
     *
     * @param command 服务端构造且已完成 ADMIN 鉴权的 command
     * @return accepted 或幂等 replay 结果
     */
    @Transactional
    public ValidationReviewTransitionResult transitionAsAdmin(ValidationReviewTransitionCommand command) {
        Objects.requireNonNull(command, "command must not be null");
        return repository.transitionInTenant(command);
    }
}
