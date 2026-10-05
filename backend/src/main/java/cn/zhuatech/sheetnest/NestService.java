// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.sheetnest;

import java.math.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.*;
import java.util.*;
import java.util.function.Supplier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import tools.jackson.databind.ObjectMapper;

/** 排样、历史版本、独立审签和人工结果的事务边界。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
@Service
@Transactional(isolation = Isolation.READ_COMMITTED)
public class NestService {
  final Store db;
  final AccessService access;
  final Clock clock;
  final ObjectMapper json;

  public NestService(Store db, AccessService access, Clock clock, ObjectMapper json) {
    this.db = db;
    this.access = access;
    this.clock = clock;
    this.json = json;
  }

  /** 接口以毫米接受有限方案字段，不接受状态、散列或系统时间。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public record JobInput(
      String requestKey,
      Long version,
      String reference,
      String name,
      Long departmentId,
      String category,
      String material,
      BigDecimal width,
      BigDecimal height,
      BigDecimal margin,
      BigDecimal kerf,
      Integer maxSheets,
      String instructions,
      Long reviewerId,
      Long operatorId) {}

  /** 零件需求使用方案版本防止并发覆盖，旋转必须显式选择。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public record PartInput(
      String requestKey,
      Long version,
      Long jobId,
      String code,
      String name,
      BigDecimal width,
      BigDecimal height,
      Integer quantity,
      Boolean rotation) {}

  /** 人工实际数量可分次登记，送审时三类数量必须等于需求。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public record ResultInput(
      String requestKey,
      Long version,
      Long jobId,
      Long partId,
      Integer good,
      Integer scrap,
      Integer notCut,
      String note) {}

  /** 明确状态命令及停止／完成申报，不接受任意实体。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public record Command(String requestKey, Long version, Long jobId, String note, String outcome) {}

  private static final Set<String> EDITABLE = Set.of("DRAFT", "CALCULATED");

  private Long who() {
    return access.current().id;
  }

  private Instant now() {
    return BusinessTime.now(clock);
  }

  private void check(boolean ok, String code) {
    if (!ok) throw new Problem(409, code);
  }

  private String text(String s, int n) {
    return AdminService.text(s, n);
  }

  private String optional(String s, int n) {
    if (s == null) return "";
    if (s.length() > n) throw new Problem(400, "INVALID_INPUT");
    return s.trim();
  }

  private void lock() {
    db.lock(Department.class, 1L);
    var a = access.current();
    db.refresh(a);
    db.refresh(db.get(AccessRole.class, a.roleId));
    access.current();
  }

  private void version(SheetJob j, Long expected) {
    check(expected != null && expected == j.version, "STALE_VERSION");
  }

  private int ticks(BigDecimal mm, int min, int max) {
    if (mm == null || mm.stripTrailingZeros().scale() > 1)
      throw new Problem(400, "INVALID_DIMENSION");
    try {
      int n = mm.movePointRight(1).intValueExact();
      if (n < min || n > max) throw new Problem(400, "INVALID_DIMENSION");
      return n;
    } catch (ArithmeticException e) {
      throw new Problem(400, "INVALID_DIMENSION");
    }
  }

  private BigDecimal mm(int ticks) {
    return BigDecimal.valueOf(ticks, 1);
  }

  private List<NestPart> parts(Long id) {
    return db.query(NestPart.class, "from NestPart where jobId=?1 order by id", id);
  }

  private List<CutResult> results(Long id) {
    return db.query(CutResult.class, "from CutResult where jobId=?1 order by partId", id);
  }

  private boolean editor(SheetJob j, Long actor) {
    return !db.query(JobEditor.class, "from JobEditor where jobId=?1 and actorId=?2", j.id, actor)
        .isEmpty();
  }

  private boolean operatorOnly() {
    var ps = access.role().permissions;
    return ps.contains("cut.write")
        && !ps.contains("job.write")
        && !ps.contains("job.review")
        && !ps.contains("admin");
  }

  private boolean scope(SheetJob j) {
    if (!access.visible(j.departmentId)) return false;
    if (operatorOnly()) return Objects.equals(j.operatorId, who());
    return !access.role().scope.equals("SELF")
        || Objects.equals(j.createdBy, who())
        || Objects.equals(j.operatorId, who())
        || Objects.equals(j.reviewerId, who())
        || editor(j, who());
  }

  private SheetJob read(Long id) {
    access.require("job.read");
    var j = db.get(SheetJob.class, id);
    if (!scope(j)) throw new Problem(403, "OUT_OF_SCOPE");
    return j;
  }

  private void writer(SheetJob j) {
    read(j.id);
    access.require("job.write");
  }

  private void reviewer(SheetJob j) {
    read(j.id);
    access.require("job.review");
    if (!Objects.equals(j.reviewerId, who())
        || Objects.equals(j.operatorId, who())
        || editor(j, who())) throw new Problem(403, "INDEPENDENT_REVIEW_REQUIRED");
  }

  private void operator(SheetJob j) {
    read(j.id);
    access.require("cut.write");
    if (!Objects.equals(j.operatorId, who()) || editor(j, who()))
      throw new Problem(403, "ASSIGNED_OPERATOR_ONLY");
  }

  private void markEditor(SheetJob j) {
    if (!editor(j, who())) {
      var e = new JobEditor();
      e.jobId = j.id;
      e.actorId = who();
      db.save(e);
    }
  }

  private void invalidate(SheetJob j) {
    j.status = "DRAFT";
    j.activeRevisionId = null;
    j.approvedHash = null;
  }

  private Map<String, Object> map(Object... values) {
    var m = new LinkedHashMap<String, Object>();
    for (int i = 0; i < values.length; i += 2) m.put((String) values[i], values[i + 1]);
    return m;
  }

  private String encode(Object v) {
    return json.writeValueAsString(v);
  }

  private Object decode(String s) {
    return json.readValue(s, Object.class);
  }

  private String hash(Object v) {
    try {
      return HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256")
                  .digest(encode(v).getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }

  private Object memo(String key, Object payload, Supplier<Object> work) {
    if (key == null
        || !key.matches(
            "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}"))
      throw new Problem(400, "INVALID_REQUEST_KEY");
    String fingerprint = hash(List.of(who(), payload));
    var rows = db.query(CommandRecord.class, "from CommandRecord where requestKey=?1", key);
    if (!rows.isEmpty()) {
      check(rows.getFirst().fingerprint.equals(fingerprint), "REQUEST_KEY_REUSED");
      return decode(rows.getFirst().responseJson);
    }
    check(db.all(CommandRecord.class).size() < 10000, "COMMAND_LIMIT");
    var response = work.get();
    db.flush();
    var c = new CommandRecord();
    c.requestKey = key;
    c.fingerprint = fingerprint;
    c.responseJson = encode(response);
    db.save(c);
    return response;
  }

  private void event(SheetJob j, String action, String note) {
    var e = new BusinessEvent();
    e.objectType = "JOB";
    e.objectId = j.id;
    e.actorId = who();
    e.action = action;
    e.note = optional(note, 1000);
    e.snapshot =
        encode(
            map(
                "status",
                j.status,
                "version",
                j.version,
                "revisionId",
                j.activeRevisionId,
                "approvedHash",
                j.approvedHash,
                "reportHash",
                j.reportHash,
                "outcome",
                j.outcome));
    e.createdAt = now();
    db.save(e);
    access.audit(action, j.id, j.departmentId);
  }

  private boolean eligible(Account a, String permission, Long department) {
    return a.enabled
        && Objects.equals(a.departmentId, department)
        && db.get(AccessRole.class, a.roleId)
            .permissions
            .containsAll(Set.of("job.read", permission));
  }

  private void ready(SheetJob j) {
    check(
        !Objects.equals(j.reviewerId, j.operatorId)
            && !editor(j, j.reviewerId)
            && !editor(j, j.operatorId),
        "INDEPENDENT_REVIEW_REQUIRED");
    check(
        eligible(db.get(Account.class, j.reviewerId), "job.review", j.departmentId)
            && eligible(db.get(Account.class, j.operatorId), "cut.write", j.departmentId),
        "ASSIGNED_ACCOUNT_UNAVAILABLE");
  }

  private Object inputSnapshot(SheetJob j) {
    return map(
        "reference",
        j.reference,
        "name",
        j.name,
        "departmentId",
        j.departmentId,
        "category",
        j.category,
        "material",
        j.material,
        "width",
        j.widthTicks,
        "height",
        j.heightTicks,
        "margin",
        j.marginTicks,
        "kerf",
        j.kerfTicks,
        "maxSheets",
        j.maxSheets,
        "instructions",
        j.instructions,
        "reviewerId",
        j.reviewerId,
        "operatorId",
        j.operatorId,
        "parts",
        parts(j.id).stream()
            .map(
                p ->
                    map(
                        "id",
                        p.id,
                        "code",
                        p.code,
                        "name",
                        p.name,
                        "width",
                        p.widthTicks,
                        "height",
                        p.heightTicks,
                        "quantity",
                        p.quantity,
                        "rotation",
                        p.rotation))
            .toList());
  }

  private NestRevision active(SheetJob j) {
    check(j.activeRevisionId != null, "CALCULATION_REQUIRED");
    var r = db.get(NestRevision.class, j.activeRevisionId);
    check(
        Objects.equals(r.jobId, j.id)
            && r.inputHash.equals(hash(inputSnapshot(j)))
            && r.planHash.equals(hash(decode(r.planJson))),
        "SNAPSHOT_CHANGED");
    return r;
  }

  private String planSeal(SheetJob j) {
    var r = active(j);
    return hash(List.of(r.inputHash, r.planHash));
  }

  private String reportSeal(SheetJob j) {
    return hash(map("outcome", j.requestedOutcome, "results", results(j.id)));
  }

  private Map<String, Object> partDto(NestPart p) {
    return map(
        "id",
        p.id,
        "jobId",
        p.jobId,
        "code",
        p.code,
        "name",
        p.name,
        "width",
        mm(p.widthTicks),
        "height",
        mm(p.heightTicks),
        "quantity",
        p.quantity,
        "rotation",
        p.rotation);
  }

  private Map<String, Object> jobDto(SheetJob j) {
    return map(
        "id",
        j.id,
        "reference",
        j.reference,
        "name",
        j.name,
        "departmentId",
        j.departmentId,
        "category",
        j.category,
        "material",
        j.material,
        "width",
        mm(j.widthTicks),
        "height",
        mm(j.heightTicks),
        "margin",
        mm(j.marginTicks),
        "kerf",
        mm(j.kerfTicks),
        "maxSheets",
        j.maxSheets,
        "instructions",
        j.instructions,
        "reviewerId",
        j.reviewerId,
        "operatorId",
        j.operatorId,
        "createdBy",
        j.createdBy,
        "status",
        j.status,
        "version",
        j.version,
        "activeRevisionId",
        j.activeRevisionId,
        "approvedHash",
        j.approvedHash,
        "reportHash",
        j.reportHash,
        "closedHash",
        j.closedHash,
        "requestedOutcome",
        j.requestedOutcome,
        "outcome",
        j.outcome,
        "acknowledgedAt",
        j.acknowledgedAt,
        "approvedAt",
        j.approvedAt,
        "closedAt",
        j.closedAt,
        "createdAt",
        j.createdAt);
  }

  /** 仅返回范围内表单目录和安全账号信息。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @Transactional(readOnly = true)
  public Object options() {
    access.require("job.read");
    return map(
        "departments",
        db.all(Department.class).stream().filter(d -> access.visible(d.id)).toList(),
        "categories",
        db.all(DictionaryEntry.class).stream().filter(d -> d.type.equals("category")).toList(),
        "accounts",
        db.all(Account.class).stream()
            .filter(a -> a.enabled && access.visible(a.departmentId))
            .map(
                a ->
                    map(
                        "id",
                        a.id,
                        "displayName",
                        a.displayName,
                        "departmentId",
                        a.departmentId,
                        "permissions",
                        db.get(AccessRole.class, a.roleId).permissions))
            .toList(),
        "companyName",
        db.query(SystemSetting.class, "from SystemSetting where code='companyName'")
            .getFirst()
            .value);
  }

  /** 授权后搜索、分页和排序，最大50条；查询范围不由客户端指定。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @Transactional(readOnly = true)
  public Object list(String search, String status, int page, int size, String sort) {
    access.require("job.read");
    if (search == null
        || search.length() > 160
        || page < 0
        || page > 1000
        || size < 1
        || size > 50
        || !Set.of("newest", "oldest", "reference").contains(sort))
      throw new Problem(400, "INVALID_INPUT");
    var all =
        db.all(SheetJob.class).stream()
            .filter(this::scope)
            .filter(j -> status.isEmpty() || j.status.equals(status))
            .filter(
                j ->
                    (j.reference + " " + j.name + " " + j.material)
                        .toLowerCase(Locale.ROOT)
                        .contains(search.toLowerCase(Locale.ROOT)))
            .toList();
    var comparator =
        sort.equals("reference")
            ? Comparator.comparing((SheetJob j) -> j.reference)
            : sort.equals("oldest")
                ? Comparator.comparing((SheetJob j) -> j.id)
                : Comparator.comparing((SheetJob j) -> j.id).reversed();
    return map(
        "total",
        all.size(),
        "rows",
        all.stream()
            .sorted(comparator)
            .skip((long) page * size)
            .limit(size)
            .map(this::jobDto)
            .toList());
  }

  /** 详情包含当前布局、历史摘要与人工结果，所有内容遵循父方案权限。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @Transactional(readOnly = true)
  public Object detail(Long id) {
    var j = read(id);
    var m = jobDto(j);
    m.put("parts", parts(id).stream().map(this::partDto).toList());
    m.put("results", results(id));
    m.put(
        "layout",
        j.activeRevisionId == null
            ? null
            : decode(db.get(NestRevision.class, j.activeRevisionId).planJson));
    m.put(
        "revisions",
        db.query(NestRevision.class, "from NestRevision where jobId=?1 order by id desc", id));
    m.put(
        "history",
        db.jpql(
                BusinessEvent.class,
                "from BusinessEvent where objectType='JOB' and objectId=?1 order by id desc")
            .setParameter(1, id)
            .setMaxResults(200)
            .getResultList());
    var ps = access.role().permissions;
    m.put("canWrite", ps.contains("job.write"));
    m.put(
        "canReview",
        ps.contains("job.review")
            && Objects.equals(j.reviewerId, who())
            && !editor(j, who())
            && !Objects.equals(j.operatorId, who()));
    m.put(
        "canOperate",
        ps.contains("cut.write") && Objects.equals(j.operatorId, who()) && !editor(j, who()));
    return m;
  }

  /** 单个不可变历史版本按父方案权限读取，便于复算。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @Transactional(readOnly = true)
  public Object revision(Long id) {
    var r = db.get(NestRevision.class, id);
    read(r.jobId);
    return map("revision", r, "input", decode(r.inputJson), "layout", decode(r.planJson));
  }

  /** 方案创建或草稿编辑；输入变化使旧布局失效，历史仍保留。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public Object saveJob(Long id, JobInput v) {
    if (v == null) throw new Problem(400, "INVALID_INPUT");
    lock();
    access.require("job.read");
    access.require("job.write");
    access.department(v.departmentId);
    SheetJob existing = id == null ? null : read(id);
    if (existing != null) writer(existing);
    // A creation retry checks the created record's current range before revealing its cached
    // response.
    var prior =
        db.query(CommandRecord.class, "from CommandRecord where requestKey=?1", v.requestKey);
    if (id == null && !prior.isEmpty()) {
      check(
          prior.getFirst().fingerprint.equals(hash(List.of(who(), List.of("job", "new", v)))),
          "REQUEST_KEY_REUSED");
      var cached = json.readTree(prior.getFirst().responseJson);
      read(cached.get("id").asLong());
    }
    return memo(
        v.requestKey,
        List.of("job", id == null ? "new" : id, v),
        () -> {
          SheetJob j = existing == null ? new SheetJob() : existing;
          if (existing != null) {
            version(j, v.version);
            check(EDITABLE.contains(j.status), "FROZEN");
            check(
                Objects.equals(j.departmentId, v.departmentId) && j.reference.equals(v.reference),
                "IDENTITY_IMMUTABLE");
          } else {
            int maximum =
                Integer.parseInt(
                    db.query(SystemSetting.class, "from SystemSetting where code='maxRecords'")
                        .getFirst()
                        .value);
            check(db.all(SheetJob.class).size() < maximum, "RECORD_LIMIT");
            j.reference = text(v.reference, 60);
            j.departmentId = db.get(Department.class, v.departmentId).id;
            j.createdBy = who();
            j.createdAt = now();
            j.version = 0;
          }
          j.name = text(v.name, 160);
          j.category = text(v.category, 60);
          j.material = text(v.material, 160);
          if (db.query(
                  DictionaryEntry.class,
                  "from DictionaryEntry where type='category' and code=?1",
                  j.category)
              .isEmpty()) throw new Problem(400, "INVALID_CATEGORY");
          j.widthTicks = ticks(v.width, 10, 500000);
          j.heightTicks = ticks(v.height, 10, 500000);
          j.marginTicks = ticks(v.margin, 0, 20000);
          j.kerfTicks = ticks(v.kerf, 0, 1000);
          if (2 * j.marginTicks >= j.widthTicks
              || 2 * j.marginTicks >= j.heightTicks
              || v.maxSheets == null
              || v.maxSheets < 1
              || v.maxSheets > 30) throw new Problem(400, "INVALID_DESIGN");
          j.maxSheets = v.maxSheets;
          j.instructions = text(v.instructions, 1000);
          j.reviewerId = db.get(Account.class, v.reviewerId).id;
          j.operatorId = db.get(Account.class, v.operatorId).id;
          check(
              !Objects.equals(j.reviewerId, j.operatorId)
                  && !Objects.equals(j.reviewerId, who())
                  && !Objects.equals(j.operatorId, who()),
              "INDEPENDENT_REVIEW_REQUIRED");
          check(
              eligible(db.get(Account.class, j.reviewerId), "job.review", j.departmentId)
                  && eligible(db.get(Account.class, j.operatorId), "cut.write", j.departmentId),
              "ASSIGNED_ACCOUNT_UNAVAILABLE");
          invalidate(j);
          if (existing == null) db.save(j);
          markEditor(j);
          ready(j);
          j.version++;
          event(j, existing == null ? "CREATE_JOB" : "UPDATE_JOB", j.instructions);
          return jobDto(j);
        });
  }

  /** 编辑零件需求，维护总数量上限并立即作废活动计算。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public Object savePart(Long id, PartInput v) {
    if (v == null) throw new Problem(400, "INVALID_INPUT");
    lock();
    var j = read(v.jobId);
    writer(j);
    return memo(
        v.requestKey,
        List.of("part", id == null ? "new" : id, v),
        () -> {
          version(j, v.version);
          check(EDITABLE.contains(j.status), "FROZEN");
          var rows = parts(j.id);
          check(id != null || rows.size() < 30, "PART_LIMIT");
          var p = id == null ? new NestPart() : db.get(NestPart.class, id);
          check(id == null || Objects.equals(p.jobId, j.id), "PARENT_MISMATCH");
          if (v.quantity == null || v.quantity < 1 || v.quantity > 300 || v.rotation == null)
            throw new Problem(400, "INVALID_INPUT");
          int count =
              rows.stream().filter(x -> !Objects.equals(x.id, id)).mapToInt(x -> x.quantity).sum()
                  + v.quantity;
          check(count <= 300, "PART_LIMIT");
          p.jobId = j.id;
          p.code = text(v.code, 60);
          p.name = text(v.name, 160);
          p.widthTicks = ticks(v.width, 10, 500000);
          p.heightTicks = ticks(v.height, 10, 500000);
          p.quantity = v.quantity;
          p.rotation = v.rotation;
          if (id == null) db.save(p);
          markEditor(j);
          invalidate(j);
          j.version++;
          event(j, id == null ? "CREATE_PART" : "UPDATE_PART", p.code);
          return map("part", partDto(p), "job", jobDto(j));
        });
  }

  /** 删除仅限草稿零件，重试先检查父方案授权，再返回原响应。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public Object deletePart(Long id, Command v) {
    if (v == null) throw new Problem(400, "INVALID_INPUT");
    lock();
    var j = read(v.jobId);
    writer(j);
    return memo(
        v.requestKey,
        List.of("delete-part", id, v),
        () -> {
          version(j, v.version);
          check(EDITABLE.contains(j.status), "FROZEN");
          var p = db.get(NestPart.class, id);
          check(Objects.equals(p.jobId, j.id), "PARENT_MISMATCH");
          var note = text(v.note, 1000);
          db.delete(p);
          markEditor(j);
          invalidate(j);
          j.version++;
          event(j, "DELETE_PART", note);
          return jobDto(j);
        });
  }

  /** 仅指定执行人可登记，三类计数不得超过需求，也不推断缺失实物。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public Object saveResult(ResultInput v) {
    if (v == null) throw new Problem(400, "INVALID_INPUT");
    lock();
    var j = read(v.jobId);
    operator(j);
    return memo(
        v.requestKey,
        List.of("result", v),
        () -> {
          version(j, v.version);
          check(j.status.equals("RUNNING") && j.acknowledgedAt != null, "ACKNOWLEDGEMENT_REQUIRED");
          check(j.approvedHash.equals(planSeal(j)), "SNAPSHOT_CHANGED");
          var p = db.get(NestPart.class, v.partId);
          check(Objects.equals(p.jobId, j.id), "PARENT_MISMATCH");
          if (v.good == null
              || v.scrap == null
              || v.notCut == null
              || v.good < 0
              || v.scrap < 0
              || v.notCut < 0
              || v.good > 300
              || v.scrap > 300
              || v.notCut > 300
              || v.good + v.scrap + v.notCut > p.quantity) throw new Problem(400, "INVALID_COUNTS");
          var found =
              db.query(CutResult.class, "from CutResult where jobId=?1 and partId=?2", j.id, p.id);
          var r = found.isEmpty() ? new CutResult() : found.getFirst();
          r.jobId = j.id;
          r.partId = p.id;
          r.good = v.good;
          r.scrap = v.scrap;
          r.notCut = v.notCut;
          r.note = v.scrap + v.notCut > 0 ? text(v.note, 1000) : optional(v.note, 1000);
          r.actorId = who();
          r.recordedAt = now();
          if (found.isEmpty()) db.save(r);
          j.version++;
          event(j, "SAVE_RESULT", r.note);
          return map("result", r, "job", jobDto(j));
        });
  }

  /** 完整计算、指定独立审签、登记开启、实际报告和最终封存均为版本化命令。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public Object command(Long id, String action, Command v) {
    if (v == null) throw new Problem(400, "INVALID_INPUT");
    lock();
    var j = read(id);
    switch (action) {
      case "calculate", "submit", "start", "cancel" -> writer(j);
      case "approve", "return-plan", "return-result", "close" -> reviewer(j);
      case "acknowledge", "submit-report" -> operator(j);
      default -> throw new Problem(404, "NOT_FOUND");
    }
    return memo(
        v.requestKey,
        List.of("command", id, action, v),
        () -> {
          version(j, v.version);
          var note = text(v.note, 1000);
          switch (action) {
            case "calculate" -> {
              check(EDITABLE.contains(j.status), "FROZEN");
              check(
                  db.query(NestRevision.class, "from NestRevision where jobId=?1", j.id).size()
                      < 50,
                  "REVISION_LIMIT");
              var layout =
                  NestingEngine.calculate(
                      j.widthTicks,
                      j.heightTicks,
                      j.marginTicks,
                      j.kerfTicks,
                      j.maxSheets,
                      parts(j.id).stream()
                          .map(
                              p ->
                                  new NestingEngine.Item(
                                      p.id, p.widthTicks, p.heightTicks, p.quantity, p.rotation))
                          .toList());
              markEditor(j);
              ready(j);
              var r = new NestRevision();
              r.jobId = j.id;
              r.algorithm = NestingEngine.VERSION;
              r.inputJson = encode(inputSnapshot(j));
              r.planJson = encode(layout);
              r.inputHash = hash(decode(r.inputJson));
              r.planHash = hash(decode(r.planJson));
              r.createdBy = who();
              r.createdAt = now();
              db.save(r);
              j.activeRevisionId = r.id;
              j.status = "CALCULATED";
            }
            case "submit" -> {
              check(j.status.equals("CALCULATED"), "INVALID_STATE");
              ready(j);
              check(
                  json.readTree(active(j).planJson).get("status").asText().equals("COMPLETE"),
                  "UNPLACED_PARTS");
              j.status = "SUBMITTED";
            }
            case "return-plan" -> {
              check(j.status.equals("SUBMITTED"), "INVALID_STATE");
              invalidate(j);
            }
            case "approve" -> {
              check(j.status.equals("SUBMITTED"), "INVALID_STATE");
              ready(j);
              j.approvedHash = planSeal(j);
              j.approvedAt = now();
              j.status = "APPROVED";
            }
            case "start" -> {
              check(j.status.equals("APPROVED"), "INVALID_STATE");
              ready(j);
              check(j.approvedHash.equals(planSeal(j)), "SNAPSHOT_CHANGED");
              j.status = "RUNNING";
            }
            case "acknowledge" -> {
              check(j.status.equals("RUNNING") && j.acknowledgedAt == null, "INVALID_STATE");
              check(j.approvedHash.equals(planSeal(j)), "SNAPSHOT_CHANGED");
              j.acknowledgedAt = now();
            }
            case "submit-report" -> {
              check(
                  j.status.equals("RUNNING") && j.acknowledgedAt != null,
                  "ACKNOWLEDGEMENT_REQUIRED");
              if (v.outcome == null || !Set.of("FINISHED", "STOPPED").contains(v.outcome))
                throw new Problem(400, "INVALID_OUTCOME");
              var actual = results(j.id);
              for (var p : parts(j.id)) {
                var found = actual.stream().filter(r -> r.partId.equals(p.id)).findFirst();
                check(
                    found.isPresent()
                        && found.get().good + found.get().scrap + found.get().notCut == p.quantity,
                    "INCOMPLETE_RESULT");
              }
              check(j.approvedHash.equals(planSeal(j)), "SNAPSHOT_CHANGED");
              j.requestedOutcome = v.outcome;
              j.reportHash = reportSeal(j);
              j.status = "REVIEW";
            }
            case "return-result" -> {
              check(j.status.equals("REVIEW"), "INVALID_STATE");
              j.reportHash = null;
              j.requestedOutcome = null;
              j.status = "RUNNING";
            }
            case "close" -> {
              check(j.status.equals("REVIEW"), "INVALID_STATE");
              check(
                  j.approvedHash.equals(planSeal(j)) && j.reportHash.equals(reportSeal(j)),
                  "SNAPSHOT_CHANGED");
              j.outcome =
                  j.requestedOutcome.equals("STOPPED")
                      ? "STOPPED"
                      : results(j.id).stream().allMatch(r -> r.scrap == 0 && r.notCut == 0)
                          ? "COMPLETED"
                          : "SHORTFALL";
              j.closedHash = hash(List.of(j.approvedHash, j.reportHash, j.outcome));
              j.closedAt = now();
              j.status = "CLOSED";
            }
            case "cancel" -> {
              check(
                  Set.of("DRAFT", "CALCULATED", "SUBMITTED", "APPROVED").contains(j.status),
                  "INVALID_STATE");
              j.status = "CANCELLED";
            }
            default -> throw new Problem(404, "NOT_FOUND");
          }
          j.version++;
          event(j, action.toUpperCase(Locale.ROOT).replace('-', '_'), note);
          return jobDto(j);
        });
  }

  /** 范围内实际统计，部分排样与未上报实物单独保留。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @Transactional(readOnly = true)
  public Object dashboard() {
    access.require("dashboard");
    access.require("job.read");
    var jobs = db.all(SheetJob.class).stream().filter(this::scope).toList();
    var statuses = new TreeMap<String, Long>();
    var outcomes = new TreeMap<String, Long>();
    long used = 0, requested = 0, placed = 0, good = 0, scrap = 0, notCut = 0;
    for (var j : jobs) {
      statuses.merge(j.status, 1L, Long::sum);
      if (j.outcome != null) outcomes.merge(j.outcome, 1L, Long::sum);
      if (j.activeRevisionId != null) {
        var p = json.readTree(db.get(NestRevision.class, j.activeRevisionId).planJson);
        used += p.get("usedSheets").asLong();
        requested += p.get("requested").asLong();
        placed += p.get("placed").asLong();
      }
      for (var r : results(j.id)) {
        good += r.good;
        scrap += r.scrap;
        notCut += r.notCut;
      }
    }
    return map(
        "jobs",
        jobs.size(),
        "statuses",
        statuses,
        "outcomes",
        outcomes,
        "usedSheets",
        used,
        "requested",
        requested,
        "placed",
        placed,
        "good",
        good,
        "scrap",
        scrap,
        "notCut",
        notCut);
  }

  private String cell(Object v) {
    String s = v == null ? "" : v.toString();
    String leading = s.stripLeading();
    if ((!leading.isEmpty() && "=+@-".indexOf(leading.charAt(0)) >= 0)
        || s.startsWith("\t")
        || s.startsWith("\r")) s = "'" + s;
    return "\"" + s.replace("\"", "\"\"") + "\"";
  }

  /** 导出毫米尺寸及已登记实际结果，空白表示尚未登记；不添加宣传内容。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @Transactional(readOnly = true)
  public String csv(Long id) {
    access.require("export");
    var j = read(id);
    var actual = results(j.id);
    var out =
        new StringBuilder(
            "\uFEFFcode,name,width_mm,height_mm,quantity,rotation,good,scrap,not_cut,note\r\n");
    for (var p : parts(id)) {
      var r = actual.stream().filter(x -> x.partId.equals(p.id)).findFirst().orElse(null);
      Object[] values = {
        p.code,
        p.name,
        mm(p.widthTicks),
        mm(p.heightTicks),
        p.quantity,
        p.rotation,
        r == null ? null : r.good,
        r == null ? null : r.scrap,
        r == null ? null : r.notCut,
        r == null ? null : r.note
      };
      out.append(String.join(",", Arrays.stream(values).map(this::cell).toList())).append("\r\n");
    }
    return out.toString();
  }
}
