package com.hospital.exception;

/**
 * Исключение «Ресурс не найден» — пробрасывается при отсутствии запрошенного объекта в БД.
 *
 * Extends RuntimeException (unchecked exception) — не требует объявления в throws и
 * может пробрасываться через любые слои без перехвата, пока не достигнет GlobalExceptionHandler.
 * Там оно конвертируется в HTTP 404 Not Found.
 *
 * Использование:
 *   patientRepository.findById(id)
 *     .orElseThrow(() -> new ResourceNotFoundException("Patient", id));
 *   // → "Patient not found with id: 5"
 */
public class ResourceNotFoundException extends RuntimeException {

    /**
     * Конструктор с именем ресурса и ID.
     * Формирует сообщение: "Patient not found with id: 5".
     *
     * @param resource имя сущности ("Patient", "Doctor", "Ward" и т.д.)
     * @param id       идентификатор, который не удалось найти
     */
    public ResourceNotFoundException(String resource, Long id) {
        super(resource + " not found with id: " + id);
    }

    /**
     * Конструктор с произвольным сообщением — для случаев без числового ID.
     * Например: new ResourceNotFoundException("Patient with SNILS not found")
     */
    public ResourceNotFoundException(String message) {
        super(message);
    }
}
