package com.hospital.dto.response;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * DTO отчёта о заполняемости палат по отделению.
 *
 * <p>Возвращается специализированным эндпоинтом (GET /api/reports/ward-occupancy).
 * Это не простой маппинг Entity → DTO: данные агрегируются в сервисном слое
 * из нескольких сущностей ({@code Department}, {@code Ward}, {@code Patient}).
 *
 * <p>Аннотация {@code @Builder} (Lombok) позволяет создавать объект через
 * паттерн «Строитель» (Builder pattern):
 * <pre>{@code
 * WardOccupancyReport report = WardOccupancyReport.builder()
 *     .departmentId(1L)
 *     .departmentName("Кардиология")
 *     .totalCapacity(30)
 *     .build();
 * }</pre>
 * Это удобнее конструктора при большом числе полей.
 *
 * <p>Вложенный статический класс {@code WardOccupancyItem} описывает данные
 * одной палаты. Хранение его внутри родительского класса подчёркивает
 * семантическую связь: этот класс используется только в контексте данного отчёта.
 */
@Data
@Builder
public class WardOccupancyReport {

    /** Идентификатор отделения, по которому строится отчёт. */
    private Long departmentId;

    /** Название отделения. */
    private String departmentName;

    /**
     * Список данных по каждой палате отделения.
     * Каждый элемент — объект {@code WardOccupancyItem}.
     */
    private List<WardOccupancyItem> wards;

    /**
     * Суммарная вместимость всех палат отделения.
     * Вычисляется как сумма {@code capacity} по всем палатам.
     */
    private int totalCapacity;

    /**
     * Суммарное число занятых мест по всем палатам отделения.
     * Вычисляется как сумма {@code occupied} по всем палатам.
     */
    private int totalOccupied;

    /**
     * Суммарное число свободных мест: {@code totalCapacity - totalOccupied}.
     * Позволяет быстро оценить доступность мест без итерации по {@code wards}.
     */
    private int totalFree;

    /**
     * Вложенный класс с данными о заполняемости одной конкретной палаты.
     *
     * <p>Статический класс (не inner class) — не хранит неявную ссылку
     * на экземпляр внешнего класса, что экономит память и упрощает
     * сериализацию/десериализацию Jackson.
     *
     * <p>Аннотация {@code @Builder} добавляет отдельный Builder для
     * {@code WardOccupancyItem} — его экземпляры создаются в сервисе
     * при формировании отчёта.
     */
    @Data
    @Builder
    public static class WardOccupancyItem {

        /** Идентификатор палаты. */
        private Long wardId;

        /** Номер палаты (например, «101»). */
        private String wardNumber;

        /** Максимальная вместимость палаты. */
        private int capacity;

        /** Текущее число занятых мест в палате. */
        private int occupied;

        /** Число свободных мест: {@code capacity - occupied}. */
        private int free;
    }
}
