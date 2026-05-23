package com.hospital.search;

import java.util.List;

public interface SearchService {
    void indexPatient(PatientDocument doc);
    void indexDoctor(DoctorDocument doc);
    void deletePatient(String id);
    void deleteDoctor(String id);
    List<PatientDocument> searchPatients(String query);
    List<DoctorDocument> searchDoctors(String query);
}
