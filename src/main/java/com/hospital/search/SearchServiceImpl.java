package com.hospital.search;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.opensearch._types.query_dsl.Query;
import org.opensearch.client.opensearch.core.IndexRequest;
import org.opensearch.client.opensearch.core.SearchRequest;
import org.opensearch.client.opensearch.core.SearchResponse;
import org.opensearch.client.opensearch.indices.CreateIndexRequest;
import org.opensearch.client.opensearch.indices.ExistsRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

@Slf4j
@Service
public class SearchServiceImpl implements SearchService {

    static final String INDEX_PATIENTS = "patients";
    static final String INDEX_DOCTORS  = "doctors";

    // required=false: в тестовом профиле OpenSearch отключён — бин не создаётся
    @Autowired(required = false)
    private OpenSearchClient client;

    @PostConstruct
    public void ensureIndexes() {
        if (client == null) return;
        createIndexIfAbsent(INDEX_PATIENTS);
        createIndexIfAbsent(INDEX_DOCTORS);
    }

    private void createIndexIfAbsent(String index) {
        try {
            boolean exists = client.indices()
                    .exists(ExistsRequest.of(r -> r.index(index)))
                    .value();
            if (!exists) {
                client.indices().create(CreateIndexRequest.of(r -> r.index(index)));
                log.info("OpenSearch: создан индекс '{}'", index);
            }
        } catch (IOException e) {
            log.warn("OpenSearch: не удалось проверить/создать индекс '{}': {}", index, e.getMessage());
        }
    }

    @Override
    public void indexPatient(PatientDocument doc) {
        if (client == null) return;
        try {
            client.index(IndexRequest.of(r -> r
                    .index(INDEX_PATIENTS)
                    .id(doc.getId())
                    .document(doc)));
        } catch (IOException e) {
            log.warn("OpenSearch: ошибка индексации пациента {}: {}", doc.getId(), e.getMessage());
        }
    }

    @Override
    public void indexDoctor(DoctorDocument doc) {
        if (client == null) return;
        try {
            client.index(IndexRequest.of(r -> r
                    .index(INDEX_DOCTORS)
                    .id(doc.getId())
                    .document(doc)));
        } catch (IOException e) {
            log.warn("OpenSearch: ошибка индексации врача {}: {}", doc.getId(), e.getMessage());
        }
    }

    @Override
    public void deletePatient(String id) {
        if (client == null) return;
        try {
            client.delete(r -> r.index(INDEX_PATIENTS).id(id));
        } catch (IOException e) {
            log.warn("OpenSearch: ошибка удаления пациента {}: {}", id, e.getMessage());
        }
    }

    @Override
    public void deleteDoctor(String id) {
        if (client == null) return;
        try {
            client.delete(r -> r.index(INDEX_DOCTORS).id(id));
        } catch (IOException e) {
            log.warn("OpenSearch: ошибка удаления врача {}: {}", id, e.getMessage());
        }
    }

    @Override
    public List<PatientDocument> searchPatients(String query) {
        if (client == null) return Collections.emptyList();
        return doSearch(query, INDEX_PATIENTS, PatientDocument.class);
    }

    @Override
    public List<DoctorDocument> searchDoctors(String query) {
        if (client == null) return Collections.emptyList();
        return doSearch(query, INDEX_DOCTORS, DoctorDocument.class);
    }

    private <T> List<T> doSearch(String query, String index, Class<T> clazz) {
        try {
            Query multiMatch = Query.of(q -> q.multiMatch(m -> m
                    .query(query)
                    .fields("fullName^3", "diagnosis", "specialization", "department", "ward")));

            SearchResponse<T> response = client.search(
                    SearchRequest.of(r -> r.index(index).query(multiMatch).size(50)),
                    clazz);

            return response.hits().hits().stream()
                    .map(hit -> hit.source())
                    .filter(src -> src != null)
                    .toList();
        } catch (IOException e) {
            log.warn("OpenSearch: ошибка поиска в '{}': {}", index, e.getMessage());
            return Collections.emptyList();
        }
    }
}
