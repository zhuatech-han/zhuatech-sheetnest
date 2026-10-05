// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.sheetnest;

import java.util.*;
import org.springframework.http.*;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

/** 范围、岗位和状态验证均由业务事务执行。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
@RestController
@RequestMapping("/api")
public class ApiController {
  final NestService nest;
  final AdminService admin;
  final AccessService access;
  final Store db;

  public ApiController(NestService nest, AdminService admin, AccessService access, Store db) {
    this.nest = nest;
    this.admin = admin;
    this.access = access;
    this.db = db;
  }

  /** 安全表单目录。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @GetMapping("/options")
  public Object options() {
    return nest.options();
  }

  /** 范围内分页和排序。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @GetMapping("/jobs")
  public Object list(
      @RequestParam(defaultValue = "") String search,
      @RequestParam(defaultValue = "") String status,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size,
      @RequestParam(defaultValue = "newest") String sort) {
    return nest.list(search, status, page, size, sort);
  }

  /** 授权方案详情。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @GetMapping("/jobs/{id}")
  public Object detail(@PathVariable Long id) {
    return nest.detail(id);
  }

  /** 不可变历史版本。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @GetMapping("/revisions/{id}")
  public Object revision(@PathVariable Long id) {
    return nest.revision(id);
  }

  /** 新建方案。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @PostMapping("/jobs")
  public Object create(@RequestBody NestService.JobInput v) {
    return nest.saveJob(null, v);
  }

  /** 编辑草稿方案。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @PutMapping("/jobs/{id}")
  public Object edit(@PathVariable Long id, @RequestBody NestService.JobInput v) {
    return nest.saveJob(id, v);
  }

  /** 新建零件需求。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @PostMapping("/parts")
  public Object part(@RequestBody NestService.PartInput v) {
    return nest.savePart(null, v);
  }

  /** 编辑零件需求。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @PutMapping("/parts/{id}")
  public Object editPart(@PathVariable Long id, @RequestBody NestService.PartInput v) {
    return nest.savePart(id, v);
  }

  /** 删除草稿需求。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @PostMapping("/parts/{id}/delete")
  public Object deletePart(@PathVariable Long id, @RequestBody NestService.Command v) {
    return nest.deletePart(id, v);
  }

  /** 登记人工实际结果。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @PostMapping("/results")
  public Object result(@RequestBody NestService.ResultInput v) {
    return nest.saveResult(v);
  }

  /** 版本化业务命令。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @PostMapping("/jobs/{id}/commands/{action}")
  public Object command(
      @PathVariable Long id, @PathVariable String action, @RequestBody NestService.Command v) {
    return nest.command(id, action, v);
  }

  /** 授权统计。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @GetMapping("/dashboard")
  public Object dashboard() {
    return nest.dashboard();
  }

  /** 完整JSON报告。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @GetMapping("/jobs/{id}/report.json")
  public ResponseEntity<Object> report(@PathVariable Long id) {
    access.require("export");
    return ResponseEntity.ok()
        .contentType(MediaType.APPLICATION_JSON)
        .header(HttpHeaders.CACHE_CONTROL, "no-store")
        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=sheetnest-" + id + ".json")
        .body(nest.detail(id));
  }

  /** 尺寸与人工实际结果CSV。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @GetMapping("/jobs/{id}/parts.csv")
  public ResponseEntity<String> csv(@PathVariable Long id) {
    return ResponseEntity.ok()
        .contentType(MediaType.parseMediaType("text/csv;charset=UTF-8"))
        .header(HttpHeaders.CACHE_CONTROL, "no-store")
        .header(
            HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=sheetnest-" + id + "-parts.csv")
        .body(nest.csv(id));
  }

  /** 审计目录限制到授权部门及本人范围。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @GetMapping("/audit")
  @Transactional(readOnly = true)
  public Object audit() {
    access.require("audit");
    return db
        .jpql(
            AuditEvent.class,
            "from AuditEvent where (?1=true or departmentId=?2) and (?3=false or actor=?4) order by id desc")
        .setParameter(1, access.role().scope.equals("ALL"))
        .setParameter(2, access.current().departmentId)
        .setParameter(3, access.role().scope.equals("SELF"))
        .setParameter(4, access.current().username)
        .setMaxResults(500)
        .getResultList()
        .stream()
        .filter(
            e ->
                access.visible(e.departmentId)
                    && (!access.role().scope.equals("SELF")
                        || e.actor.equals(access.current().username)))
        .sorted(Comparator.comparing((AuditEvent e) -> e.id).reversed())
        .toList();
  }

  /** 管理资源真实读取。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @GetMapping("/admin/{type}")
  public Object adminList(@PathVariable String type) {
    return admin.list(type);
  }

  /** 管理资源创建。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @PostMapping("/admin/{type}")
  public Object adminCreate(@PathVariable String type, @RequestBody AdminService.Input v) {
    return admin.save(type, null, v);
  }

  /** 管理资源编辑。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @PutMapping("/admin/{type}/{id}")
  public Object adminEdit(
      @PathVariable String type, @PathVariable Long id, @RequestBody AdminService.Input v) {
    return admin.save(type, id, v);
  }

  /** 未引用的管理资源删除，外键保护业务历史。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @DeleteMapping("/admin/{type}/{id}")
  public Object adminDelete(@PathVariable String type, @PathVariable Long id) {
    admin.delete(type, id);
    return Map.of("ok", true);
  }
}
