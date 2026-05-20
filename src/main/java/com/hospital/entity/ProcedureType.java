package com.hospital.entity;

/**
 * Тип медицинской процедуры, назначаемой медсестрой.
 *
 * Используется в NurseAssignment.procedureType (EnumType.STRING).
 * Русские ярлыки для UI определены в NurseServiceImpl.PROCEDURE_LABELS.
 */
public enum ProcedureType {
    INJECTION,  // внутримышечные, внутривенные, подкожные уколы
    PILL,       // приём таблеток / капсул / сиропов
    DRESSING,   // перевязка раны
    PROCEDURE,  // любая иная медицинская манипуляция (капельница, ингаляция и т.п.)
    OTHER       // прочее — для случаев, не укладывающихся в классификацию выше
}
