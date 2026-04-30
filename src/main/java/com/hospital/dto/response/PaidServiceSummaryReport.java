package com.hospital.dto.response;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * DTO сводного отчёта по платным услугам в разрезе пациентов.
 *
 * <p>Возвращается эндпоинтом GET /api/reports/paid-services-summary.
 * Данные агрегируются в сервисном слое: для каждого пациента
 * суммируются стоимости всех назначенных услуг.
 *
 * <p>Аннотация {@code @Builder} генерирует Builder-паттерн для удобного
 * создания объекта в сервисе без многострочной инициализации полей.
 *
 * <p>Отчёт имеет двухуровневую структуру:
 * <ul>
 *   <li>Верхний уровень: список {@code byPatient} и итоговая сумма {@code grandTotal}.</li>
 *   <li>Вложенный уровень: {@code PatientSummary} — данные одного пациента
 *       (имя, общая сумма, количество услуг).</li>
 * </ul>
 * Такая структура позволяет отобразить как детализацию по каждому пациенту,
 * так и общий итог на одной странице.
 */
@Data
@Builder
public class PaidServiceSummaryReport {

    /**
     * Список сводок по каждому пациенту, которому назначались услуги.
     * Отсортирован в сервисе (например, по убыванию суммы).
     */
    private List<PatientSummary> byPatient;

    /**
     * Общая сумма всех платных услуг по всем пациентам.
     * Равна сумме {@code total} по всем элементам {@code byPatient}.
     * {@code BigDecimal} используется для точных денежных вычислений.
     */
    private BigDecimal grandTotal;

    /**
     * Сводная строка по одному пациенту в отчёте.
     *
     * <p>Статический вложенный класс — используется только внутри
     * {@code PaidServiceSummaryReport} и не имеет самостоятельного смысла.
     * Аннотация {@code @Builder} позволяет создавать экземпляры через Builder.
     */
    @Data
    @Builder
    public static class PatientSummary {

        /** Идентификатор пациента. */
        private Long patientId;

        /**
         * Полное имя пациента.
         * Берётся из Entity {@code Patient.fullName} при формировании отчёта.
         */
        private String patientName;

        /**
         * Суммарная стоимость всех услуг данного пациента.
         * Вычисляется через {@code SUM(price)} в запросе к БД или
         * редукцией в Java Stream API.
         */
        private BigDecimal total;

        /**
         * Количество назначенных услуг пациенту.
         * Позволяет рассчитать среднюю стоимость услуги:
         * {@code averagePrice = total / serviceCount}.
         */
        private int serviceCount;
    }
}
