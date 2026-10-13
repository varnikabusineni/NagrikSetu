package com.nagriksetu.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nagriksetu.evidence.LocalEvidenceStorageService;
import com.nagriksetu.reference.DepartmentRepository;
import com.nagriksetu.reference.Ward;
import com.nagriksetu.reference.WardRepository;
import com.nagriksetu.security.JwtService;
import com.nagriksetu.user.RoleCode;
import com.nagriksetu.user.RoleRepository;
import com.nagriksetu.user.User;
import com.nagriksetu.user.UserRepository;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.stream.Stream;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

/**
 * Base for integration tests: full Spring context against a real PostgreSQL database
 * (NAGRIK_TEST_DB_URL, default jdbc:postgresql://localhost:5432/nagrik_setu_test) and a temporary
 * local evidence directory. Each test starts from a clean set of fixtures.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public abstract class IntegrationTest {

    // Computed once: BCrypt at strength 12 is deliberately slow.
    private static String passwordHash;
    public static final String PASSWORD = "Passw0rd!";

    /** A point inside {@link #wardA}'s boundary (Madhapur area). */
    public static final String LAT_IN_A = "17.448300";
    public static final String LNG_IN_A = "78.391500";
    /** A point inside India but outside every configured boundary. */
    public static final String LAT_OUTSIDE = "17.536000";
    public static final String LNG_OUTSIDE = "78.485000";

    @Autowired protected MockMvc mvc;
    @Autowired protected ObjectMapper json;
    @Autowired protected JdbcTemplate jdbc;
    @Autowired protected UserRepository users;
    @Autowired protected RoleRepository roles;
    @Autowired protected WardRepository wards;
    @Autowired protected DepartmentRepository departments;
    @Autowired protected JwtService jwtService;
    @Autowired protected LocalEvidenceStorageService storage;
    @Autowired protected org.springframework.security.crypto.password.PasswordEncoder passwordEncoder;

    protected Ward wardA;
    protected Ward wardB;
    protected User citizen;
    protected User otherCitizen;
    protected User wardHeadA;
    protected User wardHeadB;
    protected User roadsHead;
    protected User roadsOfficer;
    protected User sanitationOfficer;
    protected User admin;

    @BeforeEach
    void resetDatabaseAndStorage() throws IOException {
        jdbc.execute("""
                TRUNCATE notifications, feedback, upvotes, complaint_followers, duplicate_flags, evidence, resolutions,
                         complaint_status_history, assignments, complaints, audit_events, users, wards RESTART IDENTITY CASCADE
                """);
        clearDirectory(storage.root());
        if (passwordHash == null) {
            passwordHash = passwordEncoder.encode(PASSWORD);
        }
        wardA = wards.save(new Ward("TEST-A", "Test Ward A", "Hyderabad",
                "[[17.4383,78.3795],[17.4383,78.4035],[17.4583,78.4035],[17.4583,78.3795]]", "test"));
        wardB = wards.save(new Ward("TEST-B", "Test Ward B", "Hyderabad",
                "[[17.3516,78.4627],[17.3516,78.4867],[17.3716,78.4867],[17.3716,78.4627]]", "test"));
        citizen = user("citizen@test.in", "Asha Citizen", RoleCode.CITIZEN, null, null);
        otherCitizen = user("other@test.in", "Ravi Neighbour", RoleCode.CITIZEN, null, null);
        wardHeadA = user("wardhead.a@test.in", "Ward Head A", RoleCode.WARD_HEAD, wardA, null);
        wardHeadB = user("wardhead.b@test.in", "Ward Head B", RoleCode.WARD_HEAD, wardB, null);
        roadsHead = user("roads.head@test.in", "Roads Head", RoleCode.DEPARTMENT_HEAD, null, "ROADS");
        roadsOfficer = user("roads.officer@test.in", "Roads Officer", RoleCode.OFFICER, null, "ROADS");
        sanitationOfficer = user("san.officer@test.in", "Sanitation Officer", RoleCode.OFFICER, null, "SANITATION");
        admin = user("admin@test.in", "Admin", RoleCode.ADMIN, null, null);
    }

    protected User user(String email, String name, RoleCode role, Ward ward, String deptCode) {
        User u = new User();
        u.setEmail(email);
        u.setFullName(name);
        u.setPhone("+919876543210");
        u.setPasswordHash(passwordHash);
        u.setRole(roles.findByCode(role).orElseThrow());
        u.setWard(ward);
        u.setDepartment(deptCode == null ? null : departments.findByCode(deptCode).orElseThrow());
        u.setLocality("Madhapur");
        return users.save(u);
    }

    protected String bearer(User u) {
        return "Bearer " + jwtService.issue(u.getId(), u.roleCode().name()).token();
    }

    protected Long departmentId(String code) {
        return departments.findByCode(code).orElseThrow().getId();
    }

    // ------------------------------------------------------------------ images

    public static byte[] pngBytes(Color colour) {
        return image(colour, "png");
    }

    public static byte[] jpegBytes(Color colour) {
        return image(colour, "jpg");
    }

    private static byte[] image(Color colour, String format) {
        BufferedImage img = new BufferedImage(64, 48, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setColor(colour);
        g.fillRect(0, 0, 64, 48);
        g.dispose();
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ImageIO.write(img, format, out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    public static MockMultipartFile photo(String name, byte[] bytes, String contentType) {
        return new MockMultipartFile("photos", name, contentType, bytes);
    }

    // ------------------------------------------------------------------ complaint helpers

    protected MockMultipartHttpServletRequestBuilder complaintForm(String lat, String lng, MockMultipartFile... photos) {
        MockMultipartHttpServletRequestBuilder req = MockMvcRequestBuilders.multipart("/api/complaints");
        for (MockMultipartFile p : photos) {
            req.file(p);
        }
        req.param("title", "Large pothole outside the school gate")
                .param("description", "A deep pothole has formed right outside the school gate and is filling with water.")
                .param("categoryCode", "POTHOLE")
                .param("locality", "Madhapur")
                .param("address", "Road No. 36, near the school gate")
                .param("latitude", lat)
                .param("longitude", lng);
        return req;
    }

    /** Creates a public complaint with one photo in ward A as {@link #citizen}; returns the response body. */
    protected JsonNode createComplaint(User reporter, boolean isPublic) throws Exception {
        MvcResult result = mvc.perform(complaintForm(LAT_IN_A, LNG_IN_A, photo("pothole.png", pngBytes(Color.GRAY), "image/png"))
                        .param("isPublic", Boolean.toString(isPublic))
                        .header("Authorization", bearer(reporter)))
                .andReturn();
        if (result.getResponse().getStatus() != 201) {
            throw new AssertionError("Complaint creation failed: " + result.getResponse().getContentAsString());
        }
        return json.readTree(result.getResponse().getContentAsString());
    }

    protected JsonNode read(MvcResult result) throws Exception {
        return json.readTree(result.getResponse().getContentAsString());
    }

    private static void clearDirectory(Path root) throws IOException {
        if (!Files.exists(root)) {
            return;
        }
        try (Stream<Path> walk = Files.walk(root)) {
            walk.sorted(Comparator.reverseOrder()).filter(p -> !p.equals(root)).forEach(p -> p.toFile().delete());
        }
    }
}
