package com.hospital.service;

import com.hospital.entity.Patient;
import com.hospital.entity.PatientStatus;
import com.hospital.service.strategy.*;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit-тесты паттерна «Стратегия» для выписки пациентов.
 *
 * ПОЧЕМУ ЭТИ ТЕСТЫ ЦЕННЫ:
 *   Стратегии — это чистая бизнес-логика без зависимостей на репозитории или сервисы.
 *   Поэтому здесь НЕ нужен Mockito (@ExtendWith(MockitoExtension.class) отсутствует):
 *   мы просто создаём объекты через new и вызываем методы напрямую.
 *   Тест максимально прост и быстр.
 *
 * ЧТО ТЕСТИРУЕМ:
 *   1. Каждая стратегия правильно меняет статус пациента.
 *   2. Каждая стратегия очищает текущего врача пациента.
 *   3. Каждая стратегия знает свой тип (getType()).
 *   4. Фабрика правильно сопоставляет тип → стратегию.
 *   5. Фабрика бросает исключение для незарегистрированного типа.
 */
class DischargeStrategyTest {

    /**
     * Вспомогательный метод: создаёт пациента в состоянии «на лечении».
     * Вынесен в метод (не в @BeforeEach), потому что каждый тест работает
     * с независимым объектом — изменения одной стратегии не влияют на другой тест.
     */
    private Patient buildPatient() {
        return Patient.builder()
                .id(1L)
                .fullName("Test Patient")
                .status(PatientStatus.TREATMENT) // начальный статус
                .active(true)
                .build();
    }

    @Test
    void normalDischarge_setsStatusDischargedAndClearsDoctor() {
        Patient patient = buildPatient();

        // new NormalDischargeStrategy() — без Spring, без IoC, просто объект.
        // Стратегия ТОЛЬКО МУТИРУЕТ объект, не сохраняет его — это ответственность сервиса.
        new NormalDischargeStrategy().discharge(patient);

        // DISCHARGED — плановая выписка домой
        assertThat(patient.getStatus()).isEqualTo(PatientStatus.DISCHARGED);
        // После выписки врач больше не ведёт пациента — связь разрывается
        assertThat(patient.getCurrentDoctor()).isNull();
    }

    @Test
    void forcedDischarge_setsStatusDischargedAndClearsDoctor() {
        Patient patient = buildPatient();
        new ForcedDischargeStrategy().discharge(patient);

        // Принудительная выписка даёт тот же статус DISCHARGED, что и обычная.
        // Отличие — только в логировании (WARN) и потенциальных доп. действиях.
        assertThat(patient.getStatus()).isEqualTo(PatientStatus.DISCHARGED);
        assertThat(patient.getCurrentDoctor()).isNull();
    }

    @Test
    void transferDischarge_setsStatusTransferredAndClearsDoctor() {
        Patient patient = buildPatient();
        new TransferDischargeStrategy().discharge(patient);

        // TRANSFERRED отличается от DISCHARGED: пациент не домой, а в другое учреждение.
        // Это важно для отчётности — администратор видит разницу между «выписан» и «переведён».
        assertThat(patient.getStatus()).isEqualTo(PatientStatus.TRANSFERRED);
        assertThat(patient.getCurrentDoctor()).isNull();
    }

    // ---- Тесты метода getType() — каждая стратегия знает свой тип ----

    @Test
    void normalStrategy_returnsTypeNormal() {
        // getType() используется фабрикой при построении Map<DischargeType, DischargeStrategy>
        assertThat(new NormalDischargeStrategy().getType()).isEqualTo(DischargeType.NORMAL);
    }

    @Test
    void forcedStrategy_returnsTypeForced() {
        assertThat(new ForcedDischargeStrategy().getType()).isEqualTo(DischargeType.FORCED);
    }

    @Test
    void transferStrategy_returnsTypeTransfer() {
        assertThat(new TransferDischargeStrategy().getType()).isEqualTo(DischargeType.TRANSFER);
    }

    // ---- Тесты DischargeStrategyFactory ----

    @Test
    void factory_returnsCorrectStrategyForEachType() {
        // В production Spring сам собирает List<DischargeStrategy> через DI.
        // В тесте создаём список вручную — без Spring-контекста.
        DischargeStrategyFactory factory = new DischargeStrategyFactory(List.of(
                new NormalDischargeStrategy(),
                new ForcedDischargeStrategy(),
                new TransferDischargeStrategy()
        ));

        // isInstanceOf() — проверяем конкретный класс, не только интерфейс
        assertThat(factory.getStrategy(DischargeType.NORMAL)).isInstanceOf(NormalDischargeStrategy.class);
        assertThat(factory.getStrategy(DischargeType.FORCED)).isInstanceOf(ForcedDischargeStrategy.class);
        assertThat(factory.getStrategy(DischargeType.TRANSFER)).isInstanceOf(TransferDischargeStrategy.class);
    }

    @Test
    void factory_whenUnknownType_throwsIllegalArgumentException() {
        // Если передали тип, для которого нет стратегии — это программная ошибка,
        // а не ошибка пользователя. Поэтому IllegalArgumentException, а не BusinessRuleException.
        // Воспроизводим ситуацию: создаём фабрику с ПУСТЫМ списком стратегий.
        DischargeStrategyFactory factory = new DischargeStrategyFactory(List.of());

        assertThatThrownBy(() -> factory.getStrategy(DischargeType.NORMAL))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("No discharge strategy found for type");
    }
}
