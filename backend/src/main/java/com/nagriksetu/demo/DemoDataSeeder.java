package com.nagriksetu.demo;

import com.nagriksetu.community.CommunityService;
import com.nagriksetu.complaint.ComplaintDtos;
import com.nagriksetu.complaint.ComplaintService;
import com.nagriksetu.complaint.CreateComplaintRequest;
import com.nagriksetu.config.NagrikProperties;
import com.nagriksetu.feedback.FeedbackService;
import com.nagriksetu.official.OfficialService;
import com.nagriksetu.reference.Department;
import com.nagriksetu.reference.DepartmentRepository;
import com.nagriksetu.reference.Ward;
import com.nagriksetu.reference.WardRepository;
import com.nagriksetu.security.UserPrincipal;
import com.nagriksetu.user.RoleCode;
import com.nagriksetu.user.RoleRepository;
import com.nagriksetu.user.User;
import com.nagriksetu.user.UserRepository;
import java.awt.Color;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

/**
 * DEVELOPMENT ONLY. Seeds clearly labelled Hyderabad demonstration wards, accounts and complaints the
 * first time the dev profile starts with {@code nagrik.demo.enabled=true}. Everything goes through the
 * regular services (including the photo upload pipeline), so the data behaves exactly like real data.
 * Ward boundaries are approximate rectangles for demonstration — not official GHMC boundaries.
 */
@Component
@Profile("dev")
public class DemoDataSeeder {

    private static final Logger log = LoggerFactory.getLogger(DemoDataSeeder.class);
    static final String DOMAIN = "@demo.nagriksetu.in";
    private static final String BOUNDARY_SOURCE = "DEMO: approximate rectangle for demonstration, not an official GHMC boundary";

    private record DemoWard(String code, String name, double lat, double lng) {
    }

    private static final List<DemoWard> WARDS = List.of(
            new DemoWard("DEMO-MDP", "Madhapur (Demo)", 17.4483, 78.3915),
            new DemoWard("DEMO-GCB", "Gachibowli (Demo)", 17.4401, 78.3489),
            new DemoWard("DEMO-KKP", "Kukatpally (Demo)", 17.4948, 78.3996),
            new DemoWard("DEMO-AMP", "Ameerpet (Demo)", 17.4375, 78.4482),
            new DemoWard("DEMO-BJH", "Banjara Hills (Demo)", 17.4156, 78.4347),
            new DemoWard("DEMO-SEC", "Secunderabad (Demo)", 17.4399, 78.4983),
            new DemoWard("DEMO-CHM", "Charminar (Demo)", 17.3616, 78.4747));

    private final NagrikProperties properties;
    private final WardRepository wards;
    private final DepartmentRepository departments;
    private final RoleRepository roles;
    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final ComplaintService complaintService;
    private final OfficialService officialService;
    private final CommunityService communityService;
    private final FeedbackService feedbackService;
    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;

    private final Map<String, User> accounts = new HashMap<>();

    public DemoDataSeeder(NagrikProperties properties, WardRepository wards, DepartmentRepository departments,
                          RoleRepository roles, UserRepository users, PasswordEncoder passwordEncoder,
                          ComplaintService complaintService, OfficialService officialService,
                          CommunityService communityService, FeedbackService feedbackService, JdbcTemplate jdbc,
                          TransactionTemplate tx) {
        this.properties = properties;
        this.wards = wards;
        this.departments = departments;
        this.roles = roles;
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.complaintService = complaintService;
        this.officialService = officialService;
        this.communityService = communityService;
        this.feedbackService = feedbackService;
        this.jdbc = jdbc;
        this.tx = tx;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void seed() {
        NagrikProperties.Demo demo = properties.demo();
        if (demo == null || !demo.enabled()) {
            return;
        }
        if (users.existsByEmailIgnoreCase("admin" + DOMAIN)) {
            log.info("Demo data already present; skipping seeding");
            return;
        }
        if (demo.password() == null || demo.password().length() < 8) {
            log.warn("NAGRIK_DEMO_PASSWORD is not set (min 8 chars); demo data not seeded");
            return;
        }
        log.info("Seeding Nagrik Setu demonstration data (Hyderabad sample wards, accounts and complaints)...");
        tx.executeWithoutResult(s -> {
            seedWards();
            seedAccounts(demo.password());
        });
        seedComplaints();
        log.info("Demo data ready. Sign in with any *{} account, password from NAGRIK_DEMO_PASSWORD", DOMAIN);
    }

    private void seedWards() {
        for (DemoWard w : WARDS) {
            if (wards.findByCode(w.code()).isEmpty()) {
                double dLat = 0.010;
                double dLng = 0.012;
                String boundary = String.format(Locale.ROOT, "[[%.4f,%.4f],[%.4f,%.4f],[%.4f,%.4f],[%.4f,%.4f]]",
                        w.lat() - dLat, w.lng() - dLng, w.lat() - dLat, w.lng() + dLng,
                        w.lat() + dLat, w.lng() + dLng, w.lat() + dLat, w.lng() - dLng);
                wards.save(new Ward(w.code(), w.name(), "Hyderabad", boundary, BOUNDARY_SOURCE));
            }
        }
        if (wards.findByCode("DEMO-UPL").isEmpty()) {
            wards.save(new Ward("DEMO-UPL", "Uppal (Demo, no boundary configured)", "Hyderabad", null,
                    "DEMO: boundary intentionally missing to demonstrate manual ward verification"));
        }
    }

    private void seedAccounts(String password) {
        String hash = passwordEncoder.encode(password);
        account("admin", "Control Room Admin (Demo)", RoleCode.ADMIN, null, null, hash, "Head Office");
        account("priya.sharma", "Priya Sharma (Demo Citizen)", RoleCode.CITIZEN, null, null, hash, "Madhapur");
        account("arjun.reddy", "Arjun Reddy (Demo Citizen)", RoleCode.CITIZEN, null, null, hash, "Kukatpally");
        account("fatima.khan", "Fatima Khan (Demo Citizen)", RoleCode.CITIZEN, null, null, hash, "Charminar");
        for (DemoWard w : WARDS) {
            String slug = w.code().substring(5).toLowerCase(Locale.ROOT);
            account("wardhead." + slug, "Ward Head, " + w.name(), RoleCode.WARD_HEAD,
                    wards.findByCode(w.code()).orElseThrow(), null, hash, w.name());
        }
        account("wardhead.upl", "Ward Head, Uppal (Demo)", RoleCode.WARD_HEAD, wards.findByCode("DEMO-UPL").orElseThrow(), null, hash, "Uppal");
        for (Department d : departments.findAll()) {
            String slug = d.getCode().toLowerCase(Locale.ROOT).replace("_", "");
            account("head." + slug, d.getName() + " Head (Demo)", RoleCode.DEPARTMENT_HEAD, null, d, hash, null);
            account("officer." + slug, d.getName() + " Officer (Demo)", RoleCode.OFFICER, null, d, hash, null);
        }
        account("officer.roads2", "Roads & Transport Officer 2 (Demo)", RoleCode.OFFICER, null,
                departments.findByCode("ROADS").orElseThrow(), hash, null);
    }

    private void account(String local, String name, RoleCode role, Ward ward, Department dept, String hash, String locality) {
        User u = new User();
        u.setEmail(local + DOMAIN);
        u.setFullName(name);
        u.setPhone("+919" + String.format("%09d", Math.abs(local.hashCode()) % 1_000_000_000));
        u.setPasswordHash(hash);
        u.setRole(roles.findByCode(role).orElseThrow());
        u.setWard(ward);
        u.setDepartment(dept);
        u.setLocality(locality);
        accounts.put(local, users.save(u));
    }

    private UserPrincipal p(String local) {
        return tx.execute(s -> UserPrincipal.from(users.findById(accounts.get(local).getId()).orElseThrow()));
    }

    private Long dept(String code) {
        return departments.findByCode(code).orElseThrow().getId();
    }

    private Long officer(String local) {
        return accounts.get(local).getId();
    }

    private ComplaintDtos.Detail report(String citizen, String title, String description, String category,
                                        String locality, String address, double lat, double lng, boolean isPublic,
                                        Color colour, int photos) {
        List<MultipartFile> files = new ArrayList<>();
        for (int i = 1; i <= photos; i++) {
            files.add(new InMemoryMultipartFile("demo-" + category.toLowerCase(Locale.ROOT) + "-" + i + ".png", "image/png",
                    DemoImageGenerator.png(title.length() > 44 ? title.substring(0, 44) + "..." : title,
                            locality + " • photo " + i + " of " + photos, colour, title.hashCode() + i)));
        }
        return complaintService.create(p(citizen), new CreateComplaintRequest(title, description, category, locality, address,
                BigDecimal.valueOf(lat), BigDecimal.valueOf(lng), isPublic, null), files).complaint();
    }

    private List<MultipartFile> resolutionPhoto(String label, Color colour) {
        return List.of(new InMemoryMultipartFile("demo-resolution.png", "image/png",
                DemoImageGenerator.png("Work completed", label, colour, label.hashCode())));
    }

    private void seedComplaints() {
        Color road = new Color(96, 96, 104);
        Color garbage = new Color(120, 98, 52);
        Color water = new Color(36, 104, 160);
        Color drain = new Color(70, 88, 70);
        Color light = new Color(150, 120, 30);
        Color infra = new Color(120, 70, 70);
        Color done = new Color(22, 101, 52);

        // 1. Resolved pothole with resolution photo and feedback.
        var c1 = report("priya.sharma", "Deep pothole near Cyber Towers junction",
                "A large pothole about 1 m wide has formed on the left lane near the Cyber Towers signal. Two-wheelers swerve suddenly to avoid it, especially at night.",
                "POTHOLE", "Madhapur", "Hitech City Main Road, near Cyber Towers signal", 17.4504, 78.3812, true, road, 2);
        officialService.route(p("wardhead.mdp"), c1.id(), dept("ROADS"), "Arterial road, high traffic");
        officialService.assignOfficer(p("head.roads"), c1.id(), officer("officer.roads"), "Please patch within 48 hours");
        officialService.startWork(p("officer.roads"), c1.id(), "Barricades placed; patching scheduled tonight");
        officialService.resolve(p("officer.roads"), c1.id(),
                "Pothole filled with bituminous mix and compacted. Lane reopened to traffic.", resolutionPhoto("Pothole patched, Madhapur", done));
        feedbackService.create(p("priya.sharma"), c1.id(), new FeedbackService.FeedbackRequest(4, "Fixed quickly. The patch is slightly uneven but much safer now."));
        backdate(c1.id(), 12);

        // 2. Garbage in progress.
        var c2 = report("arjun.reddy", "Garbage not collected for a week at KPHB Phase 3",
                "The community bin near the KPHB Phase 3 bus stop is overflowing and garbage has spread onto the road. Stray animals are scattering it further.",
                "GARBAGE", "Kukatpally", "KPHB Colony Phase 3, near the bus stop", 17.4925, 78.3990, true, garbage, 2);
        officialService.route(p("wardhead.kkp"), c2.id(), dept("SANITATION"), null);
        officialService.assignOfficer(p("head.sanitation"), c2.id(), officer("officer.sanitation"), null);
        officialService.startWork(p("officer.sanitation"), c2.id(), "Collection vehicle dispatched");
        backdate(c2.id(), 5);

        // 3. Streetlight assigned.
        var c3 = report("priya.sharma", "Three streetlights not working on Gachibowli flyover approach",
                "Three consecutive streetlights on the approach road to the Gachibowli flyover have been off for four nights, making the stretch very dark.",
                "STREETLIGHT", "Gachibowli", "Approach road to Gachibowli flyover, near the stadium", 17.4425, 78.3501, true, light, 1);
        officialService.route(p("wardhead.gcb"), c3.id(), dept("ELECTRICAL"), null);
        officialService.assignOfficer(p("head.electrical"), c3.id(), officer("officer.electrical"), null);
        backdate(c3.id(), 3);

        // 4. Drainage under review (routed, awaiting officer).
        var c4 = report("arjun.reddy", "Sewage overflowing onto the road at Ameerpet",
                "Sewage water is overflowing from a manhole near the Ameerpet metro station exit and pooling on the road. The smell is very strong.",
                "DRAINAGE", "Ameerpet", "Near Ameerpet metro station, exit B", 17.4370, 78.4475, true, drain, 2);
        officialService.route(p("wardhead.amp"), c4.id(), dept("DRAINAGE"), "Health hazard: please prioritise");
        officialService.setPriority(p("wardhead.amp"), c4.id(), com.nagriksetu.complaint.Priority.HIGH);
        backdate(c4.id(), 2);

        // 5. Water leakage just submitted.
        var c5 = report("fatima.khan", "Water pipeline leaking on Road No. 12",
                "Clean drinking water has been gushing from a cracked pipeline on Road No. 12 since this morning. The road is flooded.",
                "WATER_LEAKAGE", "Banjara Hills", "Road No. 12, Banjara Hills, opposite the park", 17.4140, 78.4360, true, water, 1);
        backdate(c5.id(), 1);

        // 6. Garbage near Charminar, submitted, community upvotes.
        var c6 = report("fatima.khan", "Garbage dumped beside Laad Bazaar lane",
                "Shop waste is being dumped every evening at the corner of the Laad Bazaar lane. It blocks pedestrians and attracts flies.",
                "GARBAGE", "Charminar", "Laad Bazaar lane corner, near Charminar", 17.3612, 78.4738, true, garbage, 1);
        communityService.upvote(p("priya.sharma"), c6.id());
        communityService.upvote(p("arjun.reddy"), c6.id());
        communityService.follow(p("priya.sharma"), c6.id());

        // 7. Rejected with official reason.
        var c7 = report("arjun.reddy", "Broken road surface near Paradise circle",
                "The road surface is broken and uneven for about 50 metres near Paradise circle.",
                "POTHOLE", "Secunderabad", "Near Paradise circle, SD Road", 17.4425, 78.4950, true, road, 1);
        officialService.reject(p("wardhead.sec"), c7.id(),
                "This stretch is maintained by the Secunderabad Cantonment Board, not this municipal ward. The complaint has been forwarded to the Board.");
        backdate(c7.id(), 8);

        // 8. Outside all demo ward boundaries: requires manual ward verification by the admin.
        report("priya.sharma", "Open drain without cover near Kompally market",
                "A storm water drain next to the Kompally vegetable market has no cover slab for about 10 metres. Children walk past it daily.",
                "DRAINAGE", "Kompally", "Kompally vegetable market, main road side", 17.5360, 78.4850, true, drain, 1);

        // 9. Water supply resolved then reopened.
        var c9 = report("arjun.reddy", "No water supply in Vivekananda Nagar for 3 days",
                "Our lane in Vivekananda Nagar Colony has had no municipal water supply for three days. Other lanes are receiving water.",
                "WATER_SUPPLY", "Kukatpally", "Vivekananda Nagar Colony, Lane 4", 17.4980, 78.4020, true, water, 0);
        officialService.route(p("wardhead.kkp"), c9.id(), dept("WATER"), null);
        officialService.assignOfficer(p("head.water"), c9.id(), officer("officer.water"), null);
        officialService.startWork(p("officer.water"), c9.id(), null);
        officialService.resolve(p("officer.water"), c9.id(), "Valve on the lane feeder line was found closed and has been reopened.", List.of());
        officialService.reopen(p("wardhead.kkp"), c9.id(), "Residents report supply stopped again the next day; the feeder line needs inspection.");
        backdate(c9.id(), 6);

        // 10. Private complaint (not shown in the community feed).
        report("priya.sharma", "Damaged bus shelter roof near Durgam Cheruvu",
                "Part of the roof sheet of the bus shelter has come loose and hangs dangerously over the seating area.",
                "INFRASTRUCTURE", "Madhapur", "Bus shelter on Durgam Cheruvu road", 17.4440, 78.3885, false, infra, 1);

        // 11. Possible duplicate of complaint 2, flagged by a citizen.
        var c11 = report("fatima.khan", "Overflowing dustbin at KPHB Phase 3 bus stop",
                "The garbage bin at the Phase 3 bus stop is overflowing and smells terrible.",
                "GARBAGE", "Kukatpally", "KPHB Phase 3 bus stop", 17.4927, 78.3992, true, garbage, 1);
        communityService.flagDuplicate(p("arjun.reddy"), c11.id(),
                new CommunityService.FlagRequest(c2.referenceNo(), "Same bin as my earlier complaint"));

        // 12. Streetlight in progress with an information request and citizen response.
        var c12 = report("fatima.khan", "Streetlight pole leaning dangerously near Mecca Masjid",
                "A streetlight pole is leaning at an angle after a truck hit it. It could fall onto the footpath.",
                "STREETLIGHT", "Charminar", "Footpath near Mecca Masjid entrance", 17.3604, 78.4733, true, light, 1);
        officialService.route(p("wardhead.chm"), c12.id(), dept("ELECTRICAL"), null);
        officialService.assignOfficer(p("head.electrical"), c12.id(), officer("officer.electrical"), null);
        officialService.requestInfo(p("officer.electrical"), c12.id(), "Is the light still connected and glowing at night? A closer photo of the base would help.");
        complaintService.respond(p("fatima.khan"), c12.id(), "Yes, the light still glows at night. Adding a photo of the base.",
                List.of(new InMemoryMultipartFile("demo-pole-base.png", "image/png",
                        DemoImageGenerator.png("Pole base (closer view)", "Charminar • additional photo", light, 77))));
        officialService.startWork(p("officer.electrical"), c12.id(), "Crane booked to straighten and re-anchor the pole");
        backdate(c12.id(), 4);

        communityService.upvote(p("fatima.khan"), c1.id());
        communityService.upvote(p("priya.sharma"), c4.id());
        communityService.upvote(p("fatima.khan"), c4.id());
        communityService.follow(p("arjun.reddy"), c3.id());
    }

    /** Shifts every timestamp of a demo complaint into the past, preserving the order of events. */
    private void backdate(Long complaintId, int days) {
        String interval = days + " days";
        tx.executeWithoutResult(s -> {
            jdbc.update("update complaints set created_at = created_at - cast(? as interval), updated_at = updated_at - cast(? as interval), "
                    + "resolved_at = resolved_at - cast(? as interval) where id = ?", interval, interval, interval, complaintId);
            jdbc.update("update feedback set updated_at = updated_at - cast(? as interval) where complaint_id = ?", interval, complaintId);
            for (String table : List.of("complaint_status_history", "evidence", "resolutions", "notifications", "feedback")) {
                jdbc.update("update " + table + " set created_at = created_at - cast(? as interval) where complaint_id = ?", interval, complaintId);
            }
            jdbc.update("update assignments set created_at = created_at - cast(? as interval), ended_at = ended_at - cast(? as interval) "
                    + "where complaint_id = ?", interval, interval, complaintId);
        });
    }
}
