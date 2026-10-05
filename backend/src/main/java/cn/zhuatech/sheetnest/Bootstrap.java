// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.sheetnest;

import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.*;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** 空库初始化排样岗位，不预置虚构板材或加工记录。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
@Component
public class Bootstrap implements ApplicationRunner {
  final Store db;
  final BCryptPasswordEncoder encoder;
  final String password;

  public Bootstrap(
      Store db,
      BCryptPasswordEncoder encoder,
      @Value("${sheetnest.admin-password}") String password) {
    this.db = db;
    this.encoder = encoder;
    this.password = password;
  }

  /** 首次初始化身份目录、菜单、字典和随机口令管理员。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @Override
  @Transactional
  public void run(ApplicationArguments args) {
    if (!db.all(Account.class).isEmpty()) return;
    AdminService.validatePassword(password);
    var d = new Department();
    d.name = "总部";
    db.save(d);
    String[][] permissions = {
      {"job.read", "查看授权排样方案"},
      {"job.write", "设计与推进方案"},
      {"job.review", "指定独立方案与结果复核"},
      {"cut.write", "指定人工结果登记"},
      {"dashboard", "范围内统计"},
      {"export", "范围内导出"},
      {"audit", "操作审计"},
      {"admin", "系统管理"}
    };
    var all = new HashSet<String>();
    for (var row : permissions) {
      var p = new Permission();
      p.code = row[0];
      p.name = row[1];
      db.save(p);
      all.add(p.code);
    }
    var role = role("管理员", "ALL", all);
    role("方案设计", "DEPARTMENT", Set.of("job.read", "job.write", "dashboard", "export", "audit"));
    role("独立复核", "DEPARTMENT", Set.of("job.read", "job.review", "dashboard", "export", "audit"));
    role("实物登记", "SELF", Set.of("job.read", "cut.write", "dashboard", "export"));
    role("部门查阅", "DEPARTMENT", Set.of("job.read", "dashboard", "export"));
    var a = new Account();
    a.username = "admin";
    a.displayName = "管理员";
    a.roleId = role.id;
    a.departmentId = d.id;
    a.enabled = true;
    a.passwordHash = encoder.encode(password);
    db.save(a);
    String[][] menus = {
      {"jobs", "排样方案", "Nesting jobs", "job.read"},
      {"dashboard", "排样统计", "Statistics", "dashboard"},
      {"audit", "操作审计", "Audit", "audit"},
      {"users", "账号管理", "Accounts", "admin"},
      {"roles", "角色与权限", "Roles", "admin"},
      {"departments", "部门管理", "Departments", "admin"},
      {"menus", "导航管理", "Navigation", "admin"},
      {"permissions", "权限目录", "Permissions", "admin"},
      {"dictionaries", "材料类型", "Material types", "admin"},
      {"settings", "系统参数", "Settings", "admin"}
    };
    for (int i = 0; i < menus.length; i++) {
      var m = new NavMenu();
      m.code = menus[i][0];
      m.name = menus[i][1];
      m.nameEn = menus[i][2];
      m.permissionCode = menus[i][3];
      m.position = i;
      m.enabled = true;
      db.save(m);
    }
    Map.of("timezone", "Asia/Shanghai", "companyName", "SheetNest 板材排样", "maxRecords", "1000")
        .forEach(
            (k, v) -> {
              var s = new SystemSetting();
              s.code = k;
              s.value = v;
              db.save(s);
            });
    String[][] categories = {
      {"WOOD", "木质板材", "Wood panels"}, {"METAL", "金属板材", "Metal sheets"},
      {"PLASTIC", "塑料板材", "Plastic sheets"}, {"OTHER", "其他", "Other"}
    };
    for (var row : categories) {
      var v = new DictionaryEntry();
      v.type = "category";
      v.code = row[0];
      v.name = row[1];
      v.nameEn = row[2];
      db.save(v);
    }
  }

  private AccessRole role(String name, String scope, Set<String> ps) {
    var r = new AccessRole();
    r.name = name;
    r.scope = scope;
    r.permissions = new HashSet<>(ps);
    return db.save(r);
  }
}
