package io.kalypso.scheduler.api.v1alpha1;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.kalypso.scheduler.api.v1alpha1.spec.RepositoryReference;
import io.kalypso.scheduler.api.v1alpha1.spec.WorkloadRegistrationSpec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifies that {@link WorkloadRegistration} CRD model classes serialize to and from JSON
 * with the field names expected by the Kubernetes API server.
 */
class WorkloadRegistrationSerializationTest {

    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
    }

    @Test
    void testWorkloadRegistrationSpecRoundTripWithAllFields() throws Exception {
        RepositoryReference ref = new RepositoryReference();
        ref.setRepo("https://github.com/org/workloads");
        ref.setBranch("main");
        ref.setPath("./apps/myapp");

        WorkloadRegistrationSpec spec = new WorkloadRegistrationSpec();
        spec.setWorkload(ref);
        spec.setWorkspace("team-alpha");

        String json = objectMapper.writeValueAsString(spec);

        assertTrue(json.contains("\"workload\""), "workload must be serialized");
        assertTrue(json.contains("\"workspace\":\"team-alpha\""), "workspace must be serialized");
        assertTrue(json.contains("\"repo\":\"https://github.com/org/workloads\""), "repo must be serialized");

        WorkloadRegistrationSpec deserialized = objectMapper.readValue(json, WorkloadRegistrationSpec.class);
        assertNotNull(deserialized.getWorkload());
        assertEquals("https://github.com/org/workloads", deserialized.getWorkload().getRepo());
        assertEquals("main", deserialized.getWorkload().getBranch());
        assertEquals("./apps/myapp", deserialized.getWorkload().getPath());
        assertEquals("team-alpha", deserialized.getWorkspace());
    }

    @Test
    void testWorkloadRegistrationSpecNullFieldsOmittedFromJson() throws Exception {
        WorkloadRegistrationSpec spec = new WorkloadRegistrationSpec();
        spec.setWorkspace("team-beta");

        String json = objectMapper.writeValueAsString(spec);

        assertFalse(json.contains("\"workload\""), "null workload must be omitted");
    }

    @Test
    void testWorkloadRegistrationSpecDeserializesPartialJson() throws Exception {
        String json = "{\"workspace\":\"team-gamma\"}";
        WorkloadRegistrationSpec spec = objectMapper.readValue(json, WorkloadRegistrationSpec.class);

        assertEquals("team-gamma", spec.getWorkspace());
        assertNull(spec.getWorkload(), "absent workload must remain null");
    }

    @Test
    void testWorkloadRegistrationSpecRoundTripWithoutWorkspace() throws Exception {
        RepositoryReference ref = new RepositoryReference();
        ref.setRepo("https://github.com/org/workloads");
        ref.setBranch("feature/new-service");

        WorkloadRegistrationSpec spec = new WorkloadRegistrationSpec();
        spec.setWorkload(ref);

        String json = objectMapper.writeValueAsString(spec);
        WorkloadRegistrationSpec deserialized = objectMapper.readValue(json, WorkloadRegistrationSpec.class);

        assertNotNull(deserialized.getWorkload());
        assertEquals("https://github.com/org/workloads", deserialized.getWorkload().getRepo());
        assertNull(deserialized.getWorkspace(), "absent workspace must remain null");
    }

    @Test
    void testWorkloadRegistrationSpecDeserializesWorkloadBranchAndPath() throws Exception {
        String json = "{\"workload\":{\"repo\":\"https://github.com/org/wl\",\"branch\":\"v2\",\"path\":\"./svc\"},\"workspace\":\"ws1\"}";
        WorkloadRegistrationSpec spec = objectMapper.readValue(json, WorkloadRegistrationSpec.class);

        assertEquals("https://github.com/org/wl", spec.getWorkload().getRepo());
        assertEquals("v2", spec.getWorkload().getBranch());
        assertEquals("./svc", spec.getWorkload().getPath());
        assertEquals("ws1", spec.getWorkspace());
    }
}
