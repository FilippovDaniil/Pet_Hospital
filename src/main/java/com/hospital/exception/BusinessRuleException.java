package com.hospital.exception;

/**
 * Исключение «Нарушение бизнес-правила» — пробрасывается при попытке выполнить
 * операцию, которая противоречит бизнес-логике системы.
 *
 * Отличие от ResourceNotFoundException:
 *   - ResourceNotFoundException: объект вообще не существует (404)
 *   - BusinessRuleException: объект существует, но операция запрещена правилом (409)
 *
 * Примеры нарушений бизнес-правил:
 *   - Попытка создать пациента с уже существующим СНИЛС
 *   - Назначение врача, у которого уже 20 активных пациентов
 *   - Госпитализация в палату без свободных мест
 *   - Пациент уже находится в палате
 *
 * GlobalExceptionHandler конвертирует это исключение в HTTP 409 Conflict.
 */
public class BusinessRuleException extends RuntimeException {

    public BusinessRuleException(String message) {
        super(message);
    }
}
