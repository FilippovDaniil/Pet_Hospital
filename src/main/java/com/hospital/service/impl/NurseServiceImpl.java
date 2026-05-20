package com.hospital.service.impl;

import com.hospital.dto.request.AdjustSupplyRequest;
import com.hospital.dto.request.CreateAssignmentRequest;
import com.hospital.dto.request.CreateSupplyRequest;
import com.hospital.dto.response.MedicalSupplyResponse;
import com.hospital.dto.response.NurseAssignmentResponse;
import com.hospital.entity.*;
import com.hospital.exception.BusinessRuleException;
import com.hospital.exception.ResourceNotFoundException;
import com.hospital.repository.MedicalSupplyRepository;
import com.hospital.repository.NurseAssignmentRepository;
import com.hospital.repository.UserRepository;
import com.hospital.service.NurseService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class NurseServiceImpl implements NurseService {

    private static final Map<String, String> CATEGORY_LABELS = Map.of(
            "MEDICINE",   "Медикамент",
            "CONSUMABLE", "Расходник",
            "EQUIPMENT",  "Оборудование"
    );

    private static final Map<String, String> PROCEDURE_LABELS = Map.of(
            "INJECTION", "Укол",
            "PILL",      "Приём таблеток",
            "DRESSING",  "Перевязка",
            "PROCEDURE", "Процедура",
            "OTHER",     "Прочее"
    );

    private final MedicalSupplyRepository supplyRepository;
    private final NurseAssignmentRepository assignmentRepository;
    private final UserRepository userRepository;

    @Override
    public List<MedicalSupplyResponse> getAllSupplies() {
        return supplyRepository.findAllByOrderByCategoryAscNameAsc().stream()
                .map(this::toSupplyResponse)
                .toList();
    }

    @Override
    @Transactional
    public MedicalSupplyResponse createSupply(CreateSupplyRequest request) {
        MedicalSupply supply = MedicalSupply.builder()
                .name(request.getName())
                .category(SupplyCategory.valueOf(request.getCategory()))
                .quantity(request.getQuantity())
                .unit(request.getUnit())
                .description(request.getDescription())
                .minQuantity(request.getMinQuantity())
                .build();
        return toSupplyResponse(supplyRepository.save(supply));
    }

    @Override
    @Transactional
    public MedicalSupplyResponse updateSupply(Long id, CreateSupplyRequest request) {
        MedicalSupply supply = supplyRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("MedicalSupply", id));
        supply.setName(request.getName());
        supply.setCategory(SupplyCategory.valueOf(request.getCategory()));
        supply.setQuantity(request.getQuantity());
        supply.setUnit(request.getUnit());
        supply.setDescription(request.getDescription());
        supply.setMinQuantity(request.getMinQuantity());
        return toSupplyResponse(supplyRepository.save(supply));
    }

    @Override
    @Transactional
    public MedicalSupplyResponse adjustSupply(Long id, AdjustSupplyRequest request) {
        MedicalSupply supply = supplyRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("MedicalSupply", id));
        int newQty = supply.getQuantity() + request.getDelta();
        if (newQty < 0) throw new BusinessRuleException("Недостаточно остатка на складе");
        supply.setQuantity(newQty);
        return toSupplyResponse(supplyRepository.save(supply));
    }

    @Override
    @Transactional
    public void deleteSupply(Long id) {
        supplyRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("MedicalSupply", id));
        supplyRepository.deleteById(id);
    }

    @Override
    public List<NurseAssignmentResponse> getAllAssignments(String status) {
        if (status != null && !status.isBlank()) {
            return assignmentRepository.findByStatusWithUsers(AssignmentStatus.valueOf(status)).stream()
                    .map(this::toAssignmentResponse).toList();
        }
        return assignmentRepository.findAllWithUsers().stream()
                .map(this::toAssignmentResponse).toList();
    }

    @Override
    @Transactional
    public NurseAssignmentResponse createAssignment(String nurseUsername, CreateAssignmentRequest request) {
        User nurse = userRepository.findByUsername(nurseUsername)
                .orElseThrow(() -> new ResourceNotFoundException("Пользователь не найден"));
        User client = userRepository.findById(request.getClientUserId())
                .filter(u -> u.getRole() == Role.ROLE_CLIENT)
                .orElseThrow(() -> new ResourceNotFoundException("Клиент", request.getClientUserId()));

        NurseAssignment assignment = NurseAssignment.builder()
                .clientUser(client)
                .nurseUser(nurse)
                .procedureType(ProcedureType.valueOf(request.getProcedureType()))
                .title(request.getTitle())
                .description(request.getDescription())
                .dosage(request.getDosage())
                .scheduledDate(request.getScheduledDate())
                .scheduledTime(request.getScheduledTime())
                .status(AssignmentStatus.ACTIVE)
                .build();
        return toAssignmentResponse(assignmentRepository.save(assignment));
    }

    @Override
    @Transactional
    public NurseAssignmentResponse updateAssignmentStatus(Long id, String status) {
        NurseAssignment assignment = assignmentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("NurseAssignment", id));
        assignment.setStatus(AssignmentStatus.valueOf(status));
        return toAssignmentResponse(assignmentRepository.save(assignment));
    }

    @Override
    @Transactional
    public void deleteAssignment(Long id) {
        assignmentRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("NurseAssignment", id));
        assignmentRepository.deleteById(id);
    }

    @Override
    public List<NurseAssignmentResponse> getClientAssignments(Long clientUserId) {
        return assignmentRepository.findByClientUserId(clientUserId).stream()
                .map(this::toAssignmentResponse).toList();
    }

    private MedicalSupplyResponse toSupplyResponse(MedicalSupply s) {
        return MedicalSupplyResponse.builder()
                .id(s.getId())
                .name(s.getName())
                .category(s.getCategory().name())
                .categoryLabel(CATEGORY_LABELS.getOrDefault(s.getCategory().name(), s.getCategory().name()))
                .quantity(s.getQuantity())
                .unit(s.getUnit())
                .description(s.getDescription())
                .minQuantity(s.getMinQuantity())
                .lowStock(s.getQuantity() <= s.getMinQuantity())
                .build();
    }

    private NurseAssignmentResponse toAssignmentResponse(NurseAssignment a) {
        return NurseAssignmentResponse.builder()
                .id(a.getId())
                .clientUserId(a.getClientUser().getId())
                .clientName(a.getClientUser().getFullName())
                .nurseUserId(a.getNurseUser().getId())
                .nurseName(a.getNurseUser().getFullName())
                .procedureType(a.getProcedureType().name())
                .procedureTypeLabel(PROCEDURE_LABELS.getOrDefault(a.getProcedureType().name(), a.getProcedureType().name()))
                .title(a.getTitle())
                .description(a.getDescription())
                .dosage(a.getDosage())
                .scheduledDate(a.getScheduledDate())
                .scheduledTime(a.getScheduledTime())
                .status(a.getStatus().name())
                .createdAt(a.getCreatedAt())
                .build();
    }
}
