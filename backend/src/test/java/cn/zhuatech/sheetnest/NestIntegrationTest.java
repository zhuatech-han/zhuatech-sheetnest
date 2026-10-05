// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.sheetnest;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.*;
import org.springframework.test.web.servlet.*;
import tools.jackson.databind.*;
import tools.jackson.databind.json.JsonMapper;

/** HTTP/JPA验证整数尺寸、岗位、冻结、幂等、并发和人工结果闭环。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
@SpringBootTest
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class NestIntegrationTest {
  static final String PASSWORD = "Aa9" + UUID.randomUUID();

  @DynamicPropertySource
  static void props(DynamicPropertyRegistry r) {
    r.add("sheetnest.admin-password", () -> PASSWORD);
  }

  @Autowired MockMvc mvc;
  @Autowired JdbcTemplate sql;
  final JsonMapper json = JsonMapper.builder().findAndAddModules().build();
  MockHttpSession admin, review, operator, outside, viewer, second;
  long reviewerId, operatorId, secondId, operatorRole;

  String key() {
    return UUID.randomUUID().toString();
  }

  Map<String, Object> m(Object... args) {
    var m = new LinkedHashMap<String, Object>();
    for (int i = 0; i < args.length; i += 2) m.put((String) args[i], args[i + 1]);
    return m;
  }

  MvcResult req(MockHttpSession who, String method, String path, Object value) throws Exception {
    var b =
        switch (method) {
          case "POST" -> post("/api" + path);
          case "PUT" -> put("/api" + path);
          case "DELETE" -> delete("/api" + path);
          default -> get("/api" + path);
        };
    if (who != null) b.session(who);
    b.with(csrf());
    if (value != null) b.contentType("application/json").content(json.writeValueAsString(value));
    return mvc.perform(b).andReturn();
  }

  JsonNode ok(MockHttpSession who, String method, String path, Object v) throws Exception {
    var r = req(who, method, path, v);
    assertEquals(
        200, r.getResponse().getStatus(), path + " " + r.getResponse().getContentAsString());
    return json.readTree(r.getResponse().getContentAsString());
  }

  void fail(MockHttpSession who, String method, String path, Object v, int status, String code)
      throws Exception {
    var r = req(who, method, path, v);
    assertEquals(
        status, r.getResponse().getStatus(), path + " " + r.getResponse().getContentAsString());
    assertEquals(code, json.readTree(r.getResponse().getContentAsString()).path("code").asString());
  }

  MockHttpSession login(String name) throws Exception {
    var r = req(null, "POST", "/auth/login", Map.of("username", name, "password", PASSWORD));
    assertEquals(200, r.getResponse().getStatus());
    return (MockHttpSession) r.getRequest().getSession(false);
  }

  long role(String scope, Set<String> ps) throws Exception {
    return ok(
            admin,
            "POST",
            "/admin/roles",
            m("name", "TEST" + key(), "scope", scope, "permissions", ps))
        .path("id")
        .asLong();
  }

  JsonNode user(long role, long dept) throws Exception {
    return ok(
        admin,
        "POST",
        "/admin/users",
        m(
            "username",
            "u" + key().substring(0, 8),
            "displayName",
            "TEST岗位",
            "roleId",
            role,
            "departmentId",
            dept,
            "enabled",
            true,
            "password",
            PASSWORD));
  }

  @BeforeAll
  void setup() throws Exception {
    admin = login("admin");
    var r = user(role("ALL", Set.of("job.read", "job.review", "dashboard", "export")), 1);
    reviewerId = r.path("id").asLong();
    review = login(r.path("username").asString());
    operatorRole = role("ALL", Set.of("job.read", "cut.write", "dashboard", "export"));
    var o = user(operatorRole, 1);
    operatorId = o.path("id").asLong();
    operator = login(o.path("username").asString());
    long d =
        ok(admin, "POST", "/admin/departments", m("name", "TEST外部" + key())).path("id").asLong();
    var x = user(role("DEPARTMENT", Set.of("job.read", "job.write", "dashboard", "export")), d);
    outside = login(x.path("username").asString());
    var v = user(role("SELF", Set.of("job.read", "dashboard", "export")), 1);
    viewer = login(v.path("username").asString());
    var s = user(1, 1);
    secondId = s.path("id").asLong();
    second = login(s.path("username").asString());
  }

  Map<String, Object> jobInput() {
    return m(
        "requestKey",
        key(),
        "reference",
        "SN" + key(),
        "name",
        "TEST排样",
        "departmentId",
        1,
        "category",
        "WOOD",
        "material",
        "TEST木板",
        "width",
        1200,
        "height",
        800,
        "margin",
        10,
        "kerf",
        3,
        "maxSheets",
        5,
        "instructions",
        "TEST核对方向和人工实际数量",
        "reviewerId",
        reviewerId,
        "operatorId",
        operatorId);
  }

  JsonNode current(JsonNode j) throws Exception {
    return ok(admin, "GET", "/jobs/" + j.path("id").asLong(), null);
  }

  Map<String, Object> partInput(JsonNode j, String code, int quantity) throws Exception {
    return m(
        "requestKey",
        key(),
        "version",
        current(j).path("version").asLong(),
        "jobId",
        j.path("id").asLong(),
        "code",
        code,
        "name",
        "TEST零件",
        "width",
        250,
        "height",
        200,
        "quantity",
        quantity,
        "rotation",
        false);
  }

  JsonNode draft() throws Exception {
    var j = ok(admin, "POST", "/jobs", jobInput());
    ok(admin, "POST", "/parts", partInput(j, "A", 3));
    ok(admin, "POST", "/parts", partInput(j, "B", 2));
    return current(j);
  }

  Map<String, Object> cmd(JsonNode j) {
    return m("requestKey", key(), "version", j.path("version").asLong(), "note", "TEST核对事实");
  }

  JsonNode command(MockHttpSession a, JsonNode j, String action) throws Exception {
    return ok(a, "POST", "/jobs/" + j.path("id").asLong() + "/commands/" + action, cmd(j));
  }

  JsonNode running() throws Exception {
    var j = draft();
    j = command(admin, j, "calculate");
    j = command(admin, j, "submit");
    j = command(review, j, "approve");
    return command(admin, j, "start");
  }

  JsonNode recordAll(JsonNode j, int scrap, int notCut) throws Exception {
    var parts = current(j).path("parts");
    for (var p : parts) {
      int q = p.path("quantity").asInt();
      j =
          ok(
                  operator,
                  "POST",
                  "/results",
                  m(
                      "requestKey",
                      key(),
                      "version",
                      current(j).path("version").asLong(),
                      "jobId",
                      j.path("id").asLong(),
                      "partId",
                      p.path("id").asLong(),
                      "good",
                      q - scrap - notCut,
                      "scrap",
                      scrap,
                      "notCut",
                      notCut,
                      "note",
                      "TEST实际核对"))
              .path("job");
    }
    return current(j);
  }

  JsonNode submitReport(JsonNode j, String outcome) throws Exception {
    var body = cmd(j);
    body.put("outcome", outcome);
    return ok(operator, "POST", "/jobs/" + j.path("id").asLong() + "/commands/submit-report", body);
  }

  @Test
  void completeCycleAndSeal() throws Exception {
    var j = running();
    assertEquals(64, j.path("approvedHash").asString().length());
    j = command(operator, j, "acknowledge");
    j = recordAll(j, 0, 0);
    j = submitReport(j, "FINISHED");
    j = command(review, j, "close");
    assertEquals("CLOSED", j.path("status").asString());
    assertEquals("COMPLETED", j.path("outcome").asString());
    assertEquals(64, j.path("closedHash").asString().length());
    fail(
        admin,
        "POST",
        "/jobs/" + j.path("id").asLong() + "/commands/calculate",
        cmd(j),
        409,
        "FROZEN");
  }

  @Test
  void scrapIsExplicitShortfall() throws Exception {
    var j = command(operator, running(), "acknowledge");
    j = recordAll(j, 1, 0);
    j = submitReport(j, "FINISHED");
    j = command(review, j, "close");
    assertEquals("SHORTFALL", j.path("outcome").asString());
  }

  @Test
  void stoppedAccountsForEveryUncutPiece() throws Exception {
    var j = command(operator, running(), "acknowledge");
    j = recordAll(j, 0, 1);
    j = submitReport(j, "STOPPED");
    j = command(review, j, "close");
    assertEquals("STOPPED", j.path("outcome").asString());
  }

  @Test
  void incompleteResultCannotBecomeReport() throws Exception {
    var j = command(operator, running(), "acknowledge");
    var b = cmd(j);
    b.put("outcome", "FINISHED");
    fail(
        operator,
        "POST",
        "/jobs/" + j.path("id").asLong() + "/commands/submit-report",
        b,
        409,
        "INCOMPLETE_RESULT");
    assertEquals(0, current(j).path("results").size());
  }

  @Test
  void exactMillimetersRejectTruncationAndKeepZeroKerf() throws Exception {
    var b = jobInput();
    b.put("width", 12.34);
    fail(admin, "POST", "/jobs", b, 400, "INVALID_DIMENSION");
    b = jobInput();
    b.put("width", 12.3);
    b.put("height", 20);
    b.put("margin", 0);
    b.put("kerf", 0);
    var j = ok(admin, "POST", "/jobs", b);
    assertEquals(12.3, j.path("width").asDouble());
    assertEquals(0, j.path("kerf").asInt());
  }

  @Test
  void unknownClientStatusRejected() throws Exception {
    var b = jobInput();
    b.put("status", "CLOSED");
    fail(admin, "POST", "/jobs", b, 400, "INVALID_INPUT");
  }

  @Test
  void unplacedPartsPreventPlanSubmission() throws Exception {
    var j = draft();
    var b = partInput(j, "HUGE", 1);
    b.put("width", 4000);
    ok(admin, "POST", "/parts", b);
    j = command(admin, current(j), "calculate");
    var d = current(j);
    assertEquals("PARTIAL", d.path("layout").path("status").asString());
    assertEquals(1, d.path("layout").path("unplaced").size());
    fail(
        admin,
        "POST",
        "/jobs/" + j.path("id").asLong() + "/commands/submit",
        cmd(j),
        409,
        "UNPLACED_PARTS");
  }

  @Test
  void editableChangeInvalidatesButPreservesHistoricalRevision() throws Exception {
    var j = command(admin, draft(), "calculate");
    var d = current(j);
    long rid = d.path("activeRevisionId").asLong();
    ok(admin, "POST", "/parts", partInput(j, "C", 1));
    d = current(j);
    assertTrue(d.path("layout").isNull());
    assertEquals("DRAFT", d.path("status").asString());
    assertEquals(1, d.path("revisions").size());
    assertEquals(
        5, ok(admin, "GET", "/revisions/" + rid, null).path("layout").path("requested").asInt());
  }

  @Test
  void independentPlanReturnAndResultReturn() throws Exception {
    var j = command(admin, draft(), "calculate");
    j = command(admin, j, "submit");
    j = command(review, j, "return-plan");
    assertEquals("DRAFT", j.path("status").asString());
    j = command(admin, j, "calculate");
    j = command(admin, j, "submit");
    j = command(review, j, "approve");
    j = command(admin, j, "start");
    j = command(operator, j, "acknowledge");
    j = recordAll(j, 0, 0);
    j = submitReport(j, "FINISHED");
    j = command(review, j, "return-result");
    assertEquals("RUNNING", j.path("status").asString());
    assertTrue(j.path("reportHash").isNull());
    j = submitReport(current(j), "FINISHED");
    assertEquals("CLOSED", command(review, j, "close").path("status").asString());
  }

  @Test
  void assignedOperatorEvenAllScopeCannotReadOtherJob() throws Exception {
    var other = user(operatorRole, 1);
    var b = jobInput();
    b.put("operatorId", other.path("id").asLong());
    var j = ok(admin, "POST", "/jobs", b);
    fail(operator, "GET", "/jobs/" + j.path("id").asLong(), null, 403, "OUT_OF_SCOPE");
    fail(
        operator,
        "GET",
        "/jobs/" + j.path("id").asLong() + "/parts.csv",
        null,
        403,
        "OUT_OF_SCOPE");
  }

  @Test
  void departmentAndSelfScopeApplyToDetailRevisionAndExport() throws Exception {
    var j = command(admin, draft(), "calculate");
    long id = j.path("id").asLong(), rid = j.path("activeRevisionId").asLong();
    for (var a : List.of(outside, viewer)) {
      fail(a, "GET", "/jobs/" + id, null, 403, "OUT_OF_SCOPE");
      fail(a, "GET", "/revisions/" + rid, null, 403, "OUT_OF_SCOPE");
      fail(a, "GET", "/jobs/" + id + "/report.json", null, 403, "OUT_OF_SCOPE");
    }
  }

  @Test
  void liveRevocationPrecedesCachedCommand() throws Exception {
    var j = command(admin, draft(), "calculate");
    j = command(admin, j, "submit");
    var b = cmd(j);
    String path = "/jobs/" + j.path("id").asLong() + "/commands/approve";
    var first = ok(review, "POST", path, b);
    long rr = sql.queryForObject("select role_id from account where id=?", Long.class, reviewerId);
    sql.update("delete from role_permission where role_id=? and permission_code='job.review'", rr);
    try {
      fail(review, "POST", path, b, 403, "FORBIDDEN");
    } finally {
      sql.update(
          "insert into role_permission(role_id,permission_code) values (?,?)", rr, "job.review");
    }
    assertEquals(first, ok(review, "POST", path, b));
  }

  @Test
  void repeatedCreationSameResponseAndChangedKeyFails() throws Exception {
    var b = jobInput();
    var a = ok(admin, "POST", "/jobs", b);
    assertEquals(a, ok(admin, "POST", "/jobs", b));
    b.put("name", "changed");
    fail(admin, "POST", "/jobs", b, 409, "REQUEST_KEY_REUSED");
  }

  @Test
  void deleteReplayChecksParentAndRoleBeforeCache() throws Exception {
    var j = draft();
    long pid = j.path("parts").get(0).path("id").asLong();
    var b = cmd(j);
    b.put("jobId", j.path("id").asLong());
    String path = "/parts/" + pid + "/delete";
    var first = ok(admin, "POST", path, b);
    assertEquals(first, ok(admin, "POST", path, b));
    fail(viewer, "POST", path, b, 403, "OUT_OF_SCOPE");
  }

  @Test
  void staleVersionCannotOverwriteOrDoubleCalculate() throws Exception {
    var j = draft();
    var b = cmd(j);
    command(admin, j, "calculate");
    fail(
        admin,
        "POST",
        "/jobs/" + j.path("id").asLong() + "/commands/calculate",
        b,
        409,
        "STALE_VERSION");
  }

  @Test
  void parallelCalculationCommitsOnlyOneExpectedVersion() throws Exception {
    var j = draft();
    long id = j.path("id").asLong();
    var b1 = cmd(j);
    var b2 = cmd(j);
    try (var pool = Executors.newFixedThreadPool(2)) {
      var f1 =
          pool.submit(
              () ->
                  req(admin, "POST", "/jobs/" + id + "/commands/calculate", b1)
                      .getResponse()
                      .getStatus());
      var f2 =
          pool.submit(
              () ->
                  req(admin, "POST", "/jobs/" + id + "/commands/calculate", b2)
                      .getResponse()
                      .getStatus());
      assertEquals(
          List.of(200, 409), java.util.stream.Stream.of(f1.get(), f2.get()).sorted().toList());
    }
    assertEquals(1, current(j).path("revisions").size());
  }

  @Test
  void pastEditorCannotBecomeIndependentReviewer() throws Exception {
    var j = draft();
    var b = partInput(j, "SECOND", 1);
    ok(second, "POST", "/parts", b);
    var input = jobInput();
    input.put("reference", j.path("reference").asString());
    input.put("reviewerId", secondId);
    input.put("version", current(j).path("version").asLong());
    fail(admin, "PUT", "/jobs/" + j.path("id").asLong(), input, 409, "INDEPENDENT_REVIEW_REQUIRED");
  }

  @Test
  void unassignedAdminCannotApproveOrEnterActualCounts() throws Exception {
    var j = command(admin, draft(), "calculate");
    j = command(admin, j, "submit");
    fail(
        admin,
        "POST",
        "/jobs/" + j.path("id").asLong() + "/commands/approve",
        cmd(j),
        403,
        "INDEPENDENT_REVIEW_REQUIRED");
    j = command(review, j, "approve");
    j = command(admin, j, "start");
    fail(
        admin,
        "POST",
        "/jobs/" + j.path("id").asLong() + "/commands/acknowledge",
        cmd(j),
        403,
        "ASSIGNED_OPERATOR_ONLY");
  }

  @Test
  void approvedInputsFreezeAndStartingAgainFails() throws Exception {
    var j = running();
    fail(admin, "POST", "/parts", partInput(j, "NEW", 1), 409, "FROZEN");
    fail(
        admin,
        "POST",
        "/jobs/" + j.path("id").asLong() + "/commands/start",
        cmd(j),
        409,
        "INVALID_STATE");
  }

  @Test
  void actualCountsRejectOverflowFractionalAndMissingValues() throws Exception {
    var j = command(operator, running(), "acknowledge");
    var p = current(j).path("parts").get(0);
    long id = j.path("id").asLong();
    var b =
        m(
            "requestKey",
            key(),
            "version",
            j.path("version").asLong(),
            "jobId",
            id,
            "partId",
            p.path("id").asLong(),
            "good",
            4,
            "scrap",
            0,
            "notCut",
            0,
            "note",
            "");
    fail(operator, "POST", "/results", b, 400, "INVALID_COUNTS");
    b.put("good", 1.2);
    fail(operator, "POST", "/results", b, 400, "INVALID_INPUT");
    b.put("good", 0);
    b.remove("scrap");
    fail(operator, "POST", "/results", b, 400, "INVALID_COUNTS");
  }

  @Test
  void duplicateCodesAndCrossParentPartRejected() throws Exception {
    var j = draft();
    fail(admin, "POST", "/parts", partInput(j, "A", 1), 409, "CONFLICT");
    var k = draft();
    long pid = j.path("parts").get(0).path("id").asLong();
    fail(admin, "PUT", "/parts/" + pid, partInput(k, "OTHER", 1), 409, "PARENT_MISMATCH");
  }

  @Test
  void tamperedApprovedPlanBlocksActualActions() throws Exception {
    var j = running();
    long rid = j.path("activeRevisionId").asLong();
    String old =
        sql.queryForObject("select plan_json from nest_revision where id=?", String.class, rid);
    sql.update(
        "update nest_revision set plan_json=? where id=?",
        old.replace("\"placed\":5", "\"placed\":4"),
        rid);
    try {
      fail(
          operator,
          "POST",
          "/jobs/" + j.path("id").asLong() + "/commands/acknowledge",
          cmd(j),
          409,
          "SNAPSHOT_CHANGED");
    } finally {
      sql.update("update nest_revision set plan_json=? where id=?", old, rid);
    }
  }

  @Test
  void csvNeutralizesFormulaAndUnreportedCountsRemainBlank() throws Exception {
    var j = draft();
    var b = partInput(j, "=SUM(A1)", 1);
    b.put("name", "=SUM(\nA1)");
    ok(admin, "POST", "/parts", b);
    var r = req(admin, "GET", "/jobs/" + j.path("id").asLong() + "/parts.csv", null);
    assertEquals(200, r.getResponse().getStatus());
    String csv = r.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
    assertTrue(csv.startsWith("\uFEFF"));
    assertTrue(csv.contains("'="));
    assertTrue(csv.contains("'=SUM(\nA1)"));
    assertTrue(csv.contains("\"\",\"\",\"\",\"\""));
    assertFalse(csv.contains("zhuatech"));
  }

  @Test
  void csrfAndLastAdminProtection() throws Exception {
    assertEquals(
        403,
        mvc.perform(post("/api/jobs").session(admin).contentType("application/json").content("{}"))
            .andReturn()
            .getResponse()
            .getStatus());
    fail(viewer, "GET", "/admin/users", null, 403, "FORBIDDEN");
    assertFalse(ok(admin, "GET", "/admin/users", null).toString().contains("passwordHash"));
  }
}
